"""Feature engineering + per-group LightGBM models for the baseline
(IMPLEMENTATION.md p.7 step 3: "gradient boosting - a separate model/head
for each group of the tree").

Design
------
For every category ("group", e.g. "Род", "Склон") that appears anywhere in
the tag tree, we train one independent ``LGBMClassifier`` whose classes are
*every letter ever used for that group anywhere in the tree* (a superset of
what is actually reachable from any single position). At inference time we
take the classifier's predicted probabilities and mask them down to the
letters that are actually valid *at this specific tree position*
(``allowed_letters``, computed dynamically from ``TagTree`` given the
letters resolved so far) before picking the arg-max -- mirroring the same
"softmax masked by the current tree node" idea used by the BiLSTM, just
implemented as a post-hoc probability mask over a discrete classifier
instead of a differentiable masked softmax.

Features per (word, lemma, known_prefix) example:
- last ``WORD_SUFFIX_LEN`` characters of the word (one categorical column
  per position from the end -- grammatical information concentrates in the
  ending, see IMPLEMENTATION.md p.4).
- last ``LEMMA_SUFFIX_LEN`` characters of the lemma (same idea; useful when
  the missing value belongs to a Paradigm/Variant-level category that
  describes the lexeme as a whole rather than this specific word form).
- one categorical column per tree group name, holding the letter already
  resolved for that group within ``known_prefix`` (or a sentinel "missing"
  value if that group has not been decided yet / does not apply on this
  path).
"""
from __future__ import annotations

import json
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Union

import pandas as pd

from common.masking import MaskedExample
from common.tag_tree import TagTree

WORD_SUFFIX_LEN = 5
LEMMA_SUFFIX_LEN = 3
MISSING = "\0"  # sentinel: this group is not yet resolved in known_prefix


def _suffix_columns(text: str, length: int, prefix: str) -> Dict[str, str]:
    cols: Dict[str, str] = {}
    for k in range(1, length + 1):
        cols[f"{prefix}_suf_{k}"] = text[-k] if len(text) >= k else MISSING
    return cols


def known_groups_from_prefix(known_prefix: str, tree: TagTree) -> Dict[str, str]:
    """Walk ``known_prefix`` through the tree, returning {group: letter}."""
    resolved: Dict[str, str] = {}
    for pos in tree.walk(known_prefix):
        resolved[pos.group] = pos.letter
    return resolved


def build_features(
    word: str,
    lemma: str,
    known_prefix: str,
    tree: TagTree,
    all_groups: List[str],
) -> Dict[str, str]:
    """Build one feature dict (all string/categorical) for a single example."""
    features: Dict[str, str] = {}
    features.update(_suffix_columns(word, WORD_SUFFIX_LEN, "word"))
    features.update(_suffix_columns(lemma, LEMMA_SUFFIX_LEN, "lemma"))
    resolved = known_groups_from_prefix(known_prefix, tree)
    for group in all_groups:
        features[f"known__{group}"] = resolved.get(group, MISSING)
    return features


def features_frame(rows: List[Dict[str, str]], all_groups: List[str]) -> pd.DataFrame:
    """Turn a list of feature dicts into a categorical-dtype DataFrame ready
    for LightGBM (which handles ``category`` dtype columns natively)."""
    df = pd.DataFrame(rows)
    for col in df.columns:
        df[col] = df[col].astype("category")
    return df


def feature_columns(all_groups: List[str]) -> List[str]:
    cols = [f"word_suf_{k}" for k in range(1, WORD_SUFFIX_LEN + 1)]
    cols += [f"lemma_suf_{k}" for k in range(1, LEMMA_SUFFIX_LEN + 1)]
    cols += [f"known__{g}" for g in all_groups]
    return cols


class BaselineModel:
    """Collection of one LightGBM classifier per tag-tree group."""

    def __init__(
        self,
        tree: TagTree,
        n_estimators: int = 200,
        learning_rate: float = 0.1,
        early_stopping_rounds: int = 15,
    ):
        self.tree = tree
        self.all_groups: List[str] = tree.all_group_names()
        self.models: Dict[str, "object"] = {}
        self.n_estimators = n_estimators
        self.learning_rate = learning_rate
        self.early_stopping_rounds = early_stopping_rounds

    def feature_row(self, word: str, lemma: str, known_prefix: str) -> Dict[str, str]:
        return build_features(word, lemma, known_prefix, self.tree, self.all_groups)

    def fit_group(
        self,
        group: str,
        examples: List[MaskedExample],
        val_examples: Optional[List] = None,
        n_estimators: Optional[int] = None,
        learning_rate: Optional[float] = None,
        early_stopping_rounds: Optional[int] = None,
    ) -> Optional[dict]:
        """Train the classifier for one group; returns train-set metrics or
        ``None`` if there is only one class (nothing to learn / classifier
        would be trivial and LightGBM would refuse to fit)."""
        import lightgbm as lgb
        from lightgbm import LGBMClassifier

        n_est = n_estimators if n_estimators is not None else self.n_estimators
        lr = learning_rate if learning_rate is not None else self.learning_rate
        patience = (
            early_stopping_rounds
            if early_stopping_rounds is not None
            else self.early_stopping_rounds
        )

        rows = [
            self.feature_row(ex.word, ex.lemma, ex.known_prefix) for ex in examples
        ]
        y = [ex.target_letter for ex in examples]
        X = features_frame(rows, self.all_groups)
        classes = sorted(list(set(y)))
        if len(classes) < 2:
            self.models[group] = (y[0] if y else None)  # constant "model"
            return {
                "n_examples": len(y),
                "n_classes": len(classes),
                "constant": True,
                "best_iteration": 0,
                "total_iterations": 0,
                "train_accuracy": 1.0,
                "early_stopped": False,
                "best_train_loss": None,
                "best_val_loss": None,
                "train_losses": [],
                "val_losses": [],
            }

        clf = LGBMClassifier(
            n_estimators=n_est,
            num_leaves=31,
            learning_rate=lr,
            min_child_samples=5,
            verbosity=-1,
        )

        eval_set = [(X, y)]
        eval_names = ["train"]

        val_filtered = (
            [ex for ex in val_examples if ex.target_letter in set(y)]
            if val_examples
            else []
        )
        if val_filtered:
            val_rows = [
                self.feature_row(ex.word, ex.lemma, ex.known_prefix) for ex in val_filtered
            ]
            X_val = features_frame(val_rows, self.all_groups)
            y_val = [ex.target_letter for ex in val_filtered]
            eval_set.append((X_val, y_val))
            eval_names.append("val")

        callbacks = []
        if hasattr(lgb, "log_evaluation"):
            callbacks.append(lgb.log_evaluation(period=0))
        if val_filtered and patience > 0 and hasattr(lgb, "early_stopping"):
            callbacks.append(lgb.early_stopping(stopping_rounds=patience, verbose=False))

        clf.fit(X, y, eval_set=eval_set, eval_names=eval_names, callbacks=callbacks)
        self.models[group] = clf

        evals_result = getattr(clf, "evals_result_", {})
        train_metric_key = next(iter(evals_result.get("train", {}).keys()), None)
        train_losses = (
            [float(v) for v in evals_result["train"][train_metric_key]]
            if train_metric_key
            else []
        )

        val_metric_key = next(iter(evals_result.get("val", {}).keys()), None)
        val_losses = (
            [float(v) for v in evals_result["val"][val_metric_key]]
            if val_metric_key
            else []
        )

        total_iterations = len(train_losses) if train_losses else n_est
        best_iteration = getattr(clf, "best_iteration_", None) or total_iterations
        if best_iteration <= 0:
            best_iteration = total_iterations

        best_train_loss = (
            train_losses[best_iteration - 1]
            if 0 < best_iteration <= len(train_losses)
            else None
        )
        best_val_loss = (
            val_losses[best_iteration - 1]
            if 0 < best_iteration <= len(val_losses)
            else None
        )

        train_acc = float((clf.predict(X) == pd.Series(y)).mean())
        early_stopped = bool(val_filtered and patience > 0 and total_iterations < n_est)

        return {
            "n_examples": len(y),
            "n_classes": len(classes),
            "constant": False,
            "train_accuracy": train_acc,
            "best_iteration": int(best_iteration),
            "total_iterations": int(total_iterations),
            "best_train_loss": best_train_loss,
            "best_val_loss": best_val_loss,
            "early_stopped": early_stopped,
            "train_losses": train_losses,
            "val_losses": val_losses,
        }

    def predict_letter(
        self,
        group: str,
        word: str,
        lemma: str,
        known_prefix: str,
        allowed_letters: List[str],
    ) -> str:
        """Predict the most likely letter for ``group``, restricted to
        ``allowed_letters`` (the letters actually valid at this tree
        position). Falls back to the first allowed letter if the model has
        no signal at all for any allowed class."""
        model = self.models.get(group)
        if model is None:
            return allowed_letters[0]
        if not hasattr(model, "predict_proba"):
            # constant "model": a bare letter string, learned during fit_group
            return model if model in allowed_letters else allowed_letters[0]

        row = self.feature_row(word, lemma, known_prefix)
        X = features_frame([row], self.all_groups)
        proba = model.predict_proba(X)[0]
        classes = list(model.classes_)
        best_letter, best_p = None, -1.0
        for letter in allowed_letters:
            if letter in classes:
                p = proba[classes.index(letter)]
            else:
                p = 0.0
            if p > best_p:
                best_letter, best_p = letter, p
        return best_letter if best_letter is not None else allowed_letters[0]

    def save(self, target: Union[str, Path]) -> None:
        target = Path(target)
        if not str(target).endswith(".zip"):
            target = target.with_suffix(".zip")
        target.parent.mkdir(parents=True, exist_ok=True)
        manifest: Dict[str, str] = {}
        with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for group, model in self.models.items():
                safe_name = group.replace("/", "_")
                if hasattr(model, "booster_"):
                    filename = f"{safe_name}.txt"
                    num_iter = getattr(model, "best_iteration_", None)
                    if num_iter and num_iter > 0:
                        model_str = model.booster_.model_to_string(num_iteration=num_iter)
                    else:
                        model_str = model.booster_.model_to_string()
                    zf.writestr(filename, model_str)
                    manifest[group] = json.dumps(
                        {"kind": "lgbm", "file": filename, "classes": list(model.classes_)},
                        ensure_ascii=False,
                    )
                else:
                    manifest[group] = json.dumps(
                        {"kind": "constant", "letter": model}, ensure_ascii=False
                    )
            zf.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))

    @classmethod
    def load(cls, path: Union[str, Path], tree: TagTree) -> "BaselineModel":
        import lightgbm as lgb

        path = Path(path)
        if not path.exists() and path.with_suffix(".zip").exists():
            path = path.with_suffix(".zip")

        if path.is_file() and zipfile.is_zipfile(path):
            with zipfile.ZipFile(path, "r") as zf:
                manifest = json.loads(zf.read("manifest.json").decode("utf-8"))
                instance = cls(tree)
                for group, raw in manifest.items():
                    info = json.loads(raw)
                    if info["kind"] == "constant":
                        instance.models[group] = info["letter"]
                    else:
                        model_str = zf.read(info["file"]).decode("utf-8")
                        booster = lgb.Booster(model_str=model_str)
                        wrapper = _BoosterClassifierWrapper(booster, info["classes"])
                        instance.models[group] = wrapper
                return instance
        else:
            with open(path / "manifest.json", "r", encoding="utf-8") as f:
                manifest = json.load(f)
            instance = cls(tree)
            for group, raw in manifest.items():
                info = json.loads(raw)
                if info["kind"] == "constant":
                    instance.models[group] = info["letter"]
                else:
                    booster = lgb.Booster(model_file=str(path / info["file"]))
                    wrapper = _BoosterClassifierWrapper(booster, info["classes"])
                    instance.models[group] = wrapper
            return instance


class _BoosterClassifierWrapper:
    """Thin wrapper so a loaded ``lgb.Booster`` supports ``predict_proba``
    and ``classes_`` the same way a fitted ``LGBMClassifier`` does."""

    def __init__(self, booster, classes: List[str]):
        self.booster_ = booster
        self.classes_ = classes

    def predict_proba(self, X: pd.DataFrame):
        import numpy as np

        raw = self.booster_.predict(X)
        raw = np.asarray(raw)
        if raw.ndim == 1:
            # LightGBM's binary objective returns P(class==1) as a flat
            # array instead of a 2-column matrix; expand it so callers can
            # always index [n_samples, n_classes] uniformly.
            raw = np.stack([1.0 - raw, raw], axis=1)
        return raw

    def predict(self, X: pd.DataFrame):
        import numpy as np

        proba = self.predict_proba(X)
        idx = np.argmax(proba, axis=1)
        return [self.classes_[i] for i in idx]
