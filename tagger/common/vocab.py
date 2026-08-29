"""Minimal character-level vocabulary with PAD/UNK, shared by the BiLSTM
word/lemma encoders and the tag-letter decoder.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, Iterable, List

PAD = "<pad>"
UNK = "<unk>"


class Vocab:
    def __init__(self, tokens: List[str]):
        # tokens must NOT already include PAD/UNK; they are prepended here
        # so PAD always == 0 and UNK always == 1 (needed by callers that
        # hard-code padding_idx=0).
        self.itos: List[str] = [PAD, UNK] + list(tokens)
        self.stoi: Dict[str, int] = {t: i for i, t in enumerate(self.itos)}

    def __len__(self) -> int:
        return len(self.itos)

    def encode(self, text: Iterable[str]) -> List[int]:
        unk = self.stoi[UNK]
        return [self.stoi.get(ch, unk) for ch in text]

    def encode_token(self, token: str) -> int:
        return self.stoi.get(token, self.stoi[UNK])

    @classmethod
    def build_from_texts(cls, texts: Iterable[str]) -> "Vocab":
        chars = set()
        for text in texts:
            chars.update(text)
        return cls(sorted(chars))

    def save(self, path: str | Path) -> None:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.itos[2:], f, ensure_ascii=False)  # skip PAD/UNK

    @classmethod
    def load(cls, path: str | Path) -> "Vocab":
        with open(path, "r", encoding="utf-8") as f:
            tokens = json.load(f)
        return cls(tokens)
