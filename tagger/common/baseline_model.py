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
from pathlib import Path
from typing import Dict, List, Optional

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

    def __init__(self, tree: TagTree):
        self.tree = tree
        self.all_groups: List[str] = tree.all_group_names()
        self.models: Dict[str, "object"] = {}

    def feature_row(self, word: str, lemma: str, known_prefix: str) -> Dict[str, str]:
        return build_features(word, lemma, known_prefix, self.tree, self.all_groups)

    def fit_group(self, group: str, examples: List[MaskedExample]) -> Optional[dict]:
        """Train the classifier for one group; returns train-set metrics or
        ``None`` if there is only one class (nothing to learn / classifier
        would be trivial and LightGBM would refuse to fit)."""
        from lightgbm import LGBMClassifier

        rows = [
            self.feature_row(ex.word, ex.lemma, ex.known_prefix) for ex in examples
        ]
        y = [ex.target_letter for ex in examples]
        X = features_frame(rows, self.all_groups)
        if len(set(y)) < 2:
            self.models[group] = (y[0] if y else None)  # constant "model"
            return {"n_examples": len(y), "n_classes": len(set(y)), "constant": True}

        clf = LGBMClassifier(
            n_estimators=200,
            num_leaves=31,
            learning_rate=0.1,
            min_child_samples=5,
            verbosity=-1,
        )
        clf.fit(X, y)
        self.models[group] = clf
        train_acc = float((clf.predict(X) == pd.Series(y)).mean())
        return {
            "n_examples": len(y),
            "n_classes": len(set(y)),
            "constant": False,
            "train_accuracy": train_acc,
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

    def save(self, directory: str | Path) -> None:
        directory = Path(directory)
        directory.mkdir(parents=True, exist_ok=True)
        manifest: Dict[str, str] = {}
        for group, model in self.models.items():
            safe_name = group.replace("/", "_")
            if hasattr(model, "booster_"):
                path = directory / f"{safe_name}.txt"
                model.booster_.save_model(str(path))
                manifest[group] = json.dumps(
                    {"kind": "lgbm", "file": path.name, "classes": list(model.classes_)},
                    ensure_ascii=False,
                )
            else:
                manifest[group] = json.dumps(
                    {"kind": "constant", "letter": model}, ensure_ascii=False
                )
        with open(directory / "manifest.json", "w", encoding="utf-8") as f:
            json.dump(manifest, f, ensure_ascii=False, indent=2)

    @classmethod
    def load(cls, directory: str | Path, tree: TagTree) -> "BaselineModel":
        import lightgbm as lgb

        directory = Path(directory)
        with open(directory / "manifest.json", "r", encoding="utf-8") as f:
            manifest = json.load(f)
        instance = cls(tree)
        for group, raw in manifest.items():
            info = json.loads(raw)
            if info["kind"] == "constant":
                instance.models[group] = info["letter"]
            else:
                booster = lgb.Booster(model_file=str(directory / info["file"]))
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
