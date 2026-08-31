#!/usr/bin/env python3
"""Compare baseline (LightGBM) vs BiLSTM on the same held-out split
(IMPLEMENTATION.md p.7 step 5: "held-out sample split by word, to avoid
leakage of forms of the same word between train/test").

Both models are evaluated identically, in two complementary ways:

1. **Next-letter accuracy** (per tree group + overall): for a random
   suffix cut of the real tag, predict just the very next letter given the
   fully-known prefix. This matches exactly what each model was trained to
   do (masking scheme from ``common.masking``) and is the fairest
   apples-to-apples metric.
2. **Full-suffix decode accuracy**: from the same random cut, both models
   autoregressively decode *all* remaining letters (substituting each
   prediction back in before predicting the next one, per p.3) and we
   check whether the whole reconstructed tag exactly matches the gold tag.
   This mirrors the real end-user scenario from p.6/p.7 step 6: word (+
   lemma) + a partially-X tag in, full predicted tag out.

Usage
-----
    /home/alex/.venv/bin/python3 evaluate_compare.py \\
        --forms forms.tsv --tag-tree tag_tree.json \\
        --baseline-dir artifacts/baseline --bilstm-checkpoint artifacts/bilstm.pt \\
        --split test [--limit N] [--max-positions-per-tag K]
"""
from __future__ import annotations

import argparse
import json
import random
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, List

sys.path.insert(0, str(Path(__file__).resolve().parent))

import torch

from common.baseline_model import BaselineModel
from common.bilstm_model import load_checkpoint
from common.dataset import FormRow, iter_split
from common.masking import sample_masked_examples
from common.tag_tree import TagTree


def baseline_decode(model: BaselineModel, word: str, lemma: str, known_prefix: str, tree: TagTree) -> str:
    """Autoregressive full-suffix decode with the baseline (p.3 loop, using
    the baseline's per-group classifiers instead of a neural softmax
    head)."""
    node = tree.node_after(known_prefix)
    predicted = known_prefix
    while not tree.is_terminal(node):
        allowed = tree.allowed_letters(node)
        group = tree.group_at(node)
        letter = model.predict_letter(group, word, lemma, predicted, allowed)
        predicted += letter
        node = tree.step(node, letter)
    return predicted


def bilstm_decode(model, char_vocab, codec, word: str, lemma: str, known_prefix: str, tree: TagTree) -> str:
    word_idx = torch.tensor([char_vocab.encode(word)], dtype=torch.long)
    word_len = torch.tensor([max(1, len(word))], dtype=torch.long)
    lemma_idx = torch.tensor([char_vocab.encode(lemma)], dtype=torch.long)
    lemma_len = torch.tensor([max(1, len(lemma))], dtype=torch.long)
    return model.decode(
        word_idx,
        word_len,
        lemma_idx,
        lemma_len,
        known_prefix=known_prefix,
        tree=tree,
        tag_letter_to_idx=codec.tag_letter_to_idx,
        idx_to_tag_letter=codec.idx_to_tag_letter,
        tag_vocab=codec.tag_vocab,
        max_steps=tree.max_depth(),
    )


def accumulate(report: Dict[str, Dict[str, int]], group: str, correct: bool) -> None:
    report[group]["total"] += 1
    if correct:
        report[group]["correct"] += 1


def finalize(report: Dict[str, Dict[str, int]]) -> Dict[str, dict]:
    out: Dict[str, dict] = {}
    total_correct = total_count = 0
    for group in sorted(report):
        c, t = report[group]["correct"], report[group]["total"]
        out[group] = {"accuracy": c / t if t else 0.0, "n": t}
        total_correct += c
        total_count += t
    out["__overall__"] = {
        "accuracy": total_correct / total_count if total_count else 0.0,
        "n": total_count,
    }
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--forms", default="forms.tsv")
    parser.add_argument("--tag-tree", default="tag_tree.json")
    parser.add_argument("--baseline-dir", default="artifacts/baseline")
    parser.add_argument("--bilstm-checkpoint", default="artifacts/bilstm.pt")
    parser.add_argument("--split", default="test", choices=["train", "val", "test"])
    parser.add_argument("--limit", type=int, default=0, help="cap on forms.tsv rows read (0 = all)")
    parser.add_argument(
        "--max-positions-per-tag",
        type=int,
        default=2,
        help="how many random suffix cuts to evaluate per tag",
    )
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--out", default="artifacts/comparison.json")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    tree = TagTree.load(args.tag_tree)

    baseline = BaselineModel.load(args.baseline_dir, tree)
    bilstm, char_vocab, codec = load_checkpoint(args.bilstm_checkpoint, tree)

    next_letter_report = {
        "baseline": defaultdict(lambda: {"correct": 0, "total": 0}),
        "bilstm": defaultdict(lambda: {"correct": 0, "total": 0}),
    }
    full_suffix_report = {
        "baseline": {"correct": 0, "total": 0},
        "bilstm": {"correct": 0, "total": 0},
    }

    n_rows = 0
    for row in iter_split(args.forms, args.split, limit=args.limit or None):
        n_rows += 1
        if n_rows % 100 == 0:
            print(f"[{args.split}] rows={n_rows}", file=sys.stderr)
        examples = sample_masked_examples(
            row.word, row.lemma, row.tag, tree, rng, args.max_positions_per_tag
        )
        for ex in examples:
            baseline_letter = baseline.predict_letter(
                ex.group, ex.word, ex.lemma, ex.known_prefix, ex.allowed_letters
            )
            accumulate(
                next_letter_report["baseline"], ex.group, baseline_letter == ex.target_letter
            )

            bilstm_full = bilstm_decode(
                bilstm, char_vocab, codec, ex.word, ex.lemma, ex.known_prefix, tree
            )
            bilstm_letter = (
                bilstm_full[ex.position] if ex.position < len(bilstm_full) else None
            )
            accumulate(
                next_letter_report["bilstm"], ex.group, bilstm_letter == ex.target_letter
            )

            baseline_full = baseline_decode(baseline, ex.word, ex.lemma, ex.known_prefix, tree)
            full_suffix_report["baseline"]["total"] += 1
            if baseline_full == row.tag:
                full_suffix_report["baseline"]["correct"] += 1

            full_suffix_report["bilstm"]["total"] += 1
            if bilstm_full == row.tag:
                full_suffix_report["bilstm"]["correct"] += 1

    print(f"[{args.split}] rows={n_rows}", file=sys.stderr)

    result = {
        "split": args.split,
        "n_rows": n_rows,
        "next_letter_accuracy": {
            "baseline": finalize(next_letter_report["baseline"]),
            "bilstm": finalize(next_letter_report["bilstm"]),
        },
        "full_suffix_exact_match": {
            model_name: {
                "accuracy": (r["correct"] / r["total"]) if r["total"] else 0.0,
                "n": r["total"],
            }
            for model_name, r in full_suffix_report.items()
        },
    }

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"saved comparison -> {out_path}")
    print()
    print("=== Next-letter accuracy (overall) ===")
    for model_name in ("baseline", "bilstm"):
        acc = result["next_letter_accuracy"][model_name]["__overall__"]
        print(f"  {model_name:10s}: {acc['accuracy']:.4f}  (n={acc['n']})")

    print()
    print("=== Next-letter accuracy by group ===")
    groups = sorted(
        g
        for g in result["next_letter_accuracy"]["baseline"]
        if g != "__overall__"
    )
    for group in groups:
        b = result["next_letter_accuracy"]["baseline"].get(group, {"accuracy": 0.0, "n": 0})
        n = result["next_letter_accuracy"]["bilstm"].get(group, {"accuracy": 0.0, "n": 0})
        print(f"  {group:20s} baseline={b['accuracy']:.4f} (n={b['n']:5d})   bilstm={n['accuracy']:.4f} (n={n['n']:5d})")

    print()
    print("=== Full-suffix exact-match accuracy ===")
    for model_name in ("baseline", "bilstm"):
        r = result["full_suffix_exact_match"][model_name]
        print(f"  {model_name:10s}: {r['accuracy']:.4f}  (n={r['n']})")


if __name__ == "__main__":
    main()
