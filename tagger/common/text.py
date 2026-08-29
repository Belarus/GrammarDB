"""Text cleanup shared by dataset loading and the training/inference scripts.

See IMPLEMENTATION.md p.2/p.5: word forms and lemmas must have stress marks
(``+``) stripped before being fed to any model -- the mark carries no
grammatical information relevant to the task and only inflates the alphabet
size / sequence length for no benefit.
"""
from __future__ import annotations

STRESS_MARK = "+"


def strip_stress(text: str) -> str:
    """Remove stress marks (``+``) from a word form or lemma."""
    return text.replace(STRESS_MARK, "")
