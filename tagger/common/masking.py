"""Self-supervised masking generator (IMPLEMENTATION.md p.7 step 2).

Why *suffix* masking (not arbitrary scattered positions)
---------------------------------------------------------
The tag tree is order-dependent (p.1): the set of categories/values that are
even possible at position *i* depends on the concrete letters chosen at
positions *0..i-1*. So the only masking scheme that is structurally
consistent with the tree is to know a full, contiguous **prefix** of the
tag and treat everything from some cut point onward as unknown (``X``) --
this is exactly the auto-regressive decoding loop described in p.3
("predict the first X position -> substitute -> move to the next tree node
-> repeat"). Masking an isolated middle letter while keeping a later letter
"known" would be meaningless: you cannot even name the category of that
later position without first resolving the letter(s) before it.

This module is used by:
- ``train_baseline.py``: gradient boosting is a tabular model, so it needs
  one training *row* per (known prefix -> single target letter) example.
- ``evaluate_compare.py``: to simulate "partially known" tags at various
  masking depths and evaluate both models under identical conditions.

The BiLSTM (``train_bilstm.py``) does *not* need pre-generated masked
copies: its decoder is trained with teacher forcing in a single pass over
the real, fully-known tag, which covers every possible suffix cut
implicitly and far more efficiently (see train_bilstm.py docstring).
"""
from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Iterator, List, Optional

from common.tag_tree import TagTree

MASK = "X"


@dataclass(frozen=True)
class MaskedExample:
    word: str
    lemma: str
    known_prefix: str  # tag[:position], fully resolved, no X
    position: int  # index of the target letter within the full tag
    group: str  # category name at `position` (e.g. "Род", "Склон")
    target_letter: str  # the real (gold) letter at `position`
    allowed_letters: List[str]  # every valid letter at `position` given known_prefix


def iter_masked_examples(
    word: str,
    lemma: str,
    tag: str,
    tree: TagTree,
) -> Iterator[MaskedExample]:
    """Yield one ``MaskedExample`` per position of ``tag`` (suffix-masking).

    For a tag of length *n* this yields exactly *n* examples: position 0
    (nothing known yet) through position *n-1* (everything but the last
    letter known). Each example's ``known_prefix`` is always a fully
    resolved prefix of the real tag (never contains ``X``), matching the
    only tree-consistent masking scheme (see module docstring).
    """
    node = tree.root
    for i, letter in enumerate(tag):
        children = tree.children_of(node)
        if letter not in children:
            raise ValueError(
                f"Invalid tag {tag!r} at position {i} ({letter!r}); "
                f"allowed: {sorted(children)}"
            )
        entry = children[letter]
        yield MaskedExample(
            word=word,
            lemma=lemma,
            known_prefix=tag[:i],
            position=i,
            group=entry["group"],  # type: ignore[index]
            target_letter=letter,
            allowed_letters=list(children.keys()),
        )
        node = entry


def sample_masked_examples(
    word: str,
    lemma: str,
    tag: str,
    tree: TagTree,
    rng: random.Random,
    max_positions: Optional[int] = None,
) -> List[MaskedExample]:
    """Like ``iter_masked_examples`` but subsample down to ``max_positions``.

    Useful to cap the number of (prefix, target) rows generated per tag when
    building the baseline's training table (a tag of length 9 would
    otherwise contribute 9 rows, skewing the dataset towards long-tag POS).
    """
    examples = list(iter_masked_examples(word, lemma, tag, tree))
    if max_positions is not None and len(examples) > max_positions:
        examples = rng.sample(examples, max_positions)
    return examples


def render_partial_tag(tag: str, first_masked_position: int) -> str:
    """Render ``tag`` with everything from ``first_masked_position`` on
    replaced by the ``X`` mask token -- the human/model-facing "known
    context" string described in IMPLEMENTATION.md p.2 ("known letters of
    the full tag ... with X as a special mask token")."""
    return tag[:first_masked_position] + MASK * (len(tag) - first_masked_position)
