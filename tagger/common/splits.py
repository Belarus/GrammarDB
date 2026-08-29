"""Deterministic train/val/test split by lemma.

IMPLEMENTATION.md p.7 step 5 requires the held-out evaluation to be done
*by word* (here: by lemma) so that different inflected forms of the same
lexeme never leak across train/val/test -- otherwise the model could simply
memorize a lexeme's paradigm instead of generalizing.

The split is a pure function of the lemma string (stable md5 hash), so:
- it is 100% reproducible without needing to store anything, but
- we still persist it to ``artifacts/split.json`` so that baseline, BiLSTM
  and the comparison script are guaranteed to agree on it (and so a human
  can inspect/audit exactly which lemmas ended up in which bucket).
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Dict, Literal

Split = Literal["train", "val", "test"]

# Percent boundaries out of 100 (deterministic hash bucket of the lemma).
TRAIN_PCT = 80
VAL_PCT = 10
# remaining 10% -> test


def split_for_lemma(lemma: str, seed: str = "grammardb-tagger") -> Split:
    """Deterministically assign a lemma to train/val/test.

    Uses ``md5(seed + lemma) % 100`` so the split is stable across runs and
    across processes without any shared state, while still being an
    (approximately) uniform, reproducible partition.
    """
    digest = hashlib.md5(f"{seed}\0{lemma}".encode("utf-8")).hexdigest()
    bucket = int(digest[:8], 16) % 100
    if bucket < TRAIN_PCT:
        return "train"
    if bucket < TRAIN_PCT + VAL_PCT:
        return "val"
    return "test"


def build_split_table(lemmas: Dict[str, None]) -> Dict[str, Split]:
    """Build lemma -> split mapping for a collection of (unique) lemmas."""
    return {lemma: split_for_lemma(lemma) for lemma in lemmas}


def save_split_table(table: Dict[str, Split], path: str | Path) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(table, f, ensure_ascii=False)


def load_split_table(path: str | Path) -> Dict[str, Split]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _main() -> None:
    """CLI: scan forms.tsv, write artifacts/split.json, print bucket sizes.

    Not required for training/eval (which call ``split_for_lemma`` directly
    and need no precomputed file), but useful to audit the split and to give
    all scripts one canonical file to point at.
    """
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--forms", default="forms.tsv")
    parser.add_argument("--out", default="artifacts/split.json")
    args = parser.parse_args()

    lemmas: Dict[str, None] = {}
    with open(args.forms, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3:
                continue
            lemmas[parts[1]] = None

    table = build_split_table(lemmas)
    save_split_table(table, args.out)

    counts: Dict[str, int] = {"train": 0, "val": 0, "test": 0}
    for split in table.values():
        counts[split] += 1
    print(f"lemmas: {len(table)}  ->  {counts}")
    print(f"saved: {args.out}")


if __name__ == "__main__":
    _main()
