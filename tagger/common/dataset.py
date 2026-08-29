"""Streaming access to ``forms.tsv`` with train/val/test filtering.

``forms.tsv`` is a large (~240 MB, ~4.5M rows) TSV without a header, one row
per ``Form``: ``word\\tlemma\\ttag`` (see IMPLEMENTATION.md p.7 step 1 /
``ExportToTagger.java``). Word and lemma are already stripped of stress
marks by the exporter, but we still run them through
``common.text.strip_stress`` defensively (idempotent, cheap).
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterator, Optional

from common.splits import Split, split_for_lemma
from common.text import strip_stress


@dataclass(frozen=True)
class FormRow:
    word: str
    lemma: str
    tag: str


def iter_forms(path: str | Path, limit: Optional[int] = None) -> Iterator[FormRow]:
    """Yield every row of ``forms.tsv`` as a ``FormRow``, in file order.

    ``limit`` (if given) stops after that many rows -- handy for smoke tests
    on the full 4.5M-row file without waiting for a full pass.
    """
    count = 0
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 3:
                continue
            word, lemma, tag = parts
            yield FormRow(strip_stress(word), strip_stress(lemma), tag)
            count += 1
            if limit is not None and count >= limit:
                return


def iter_split(
    path: str | Path,
    split: Split,
    limit: Optional[int] = None,
    seed: str = "grammardb-tagger",
) -> Iterator[FormRow]:
    """Yield rows of ``forms.tsv`` belonging to the requested split.

    The split is a pure function of ``row.lemma`` (see ``common.splits``),
    so this needs no precomputed table and is guaranteed consistent across
    the baseline, BiLSTM and comparison scripts as long as they use the same
    ``seed``.
    """
    yielded = 0
    for row in iter_forms(path):
        if split_for_lemma(row.lemma, seed=seed) != split:
            continue
        yield row
        yielded += 1
        if limit is not None and yielded >= limit:
            return
