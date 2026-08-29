#!/usr/bin/env python3
"""Train the unified BiLSTM tagger (IMPLEMENTATION.md p.7 step 4).

Single network (see ``common/bilstm_model.py`` docstring for the full
architecture rationale): one shared word+lemma BiLSTM encoder computed once
per example, one shared GRU decoder looping over tag positions, one shared
masked-softmax output head over the whole tag-letter alphabet. Trained with
teacher forcing (one pass per example covers every suffix cut implicitly).

Usage
-----
    /home/alex/.venv/bin/python3 train_bilstm.py \\
        --forms forms.tsv --tag-tree tag_tree.json \\
        --out artifacts/bilstm.pt \\
        [--limit-train N] [--limit-eval N] [--epochs E] [--batch-size B]

Run with small ``--limit-train``/``--epochs`` for a quick smoke test.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Dict, Iterable, Iterator, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import torch
import torch.nn as nn

from common.bilstm_model import BOS, TagCodec, TaggerModel, masked_cross_entropy
from common.dataset import FormRow, iter_split
from common.tag_tree import TagTree
from common.vocab import Vocab

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {DEVICE}", file=sys.stderr)


def build_char_vocab(forms_path: str, scan_limit: int) -> Vocab:
    """Scan a prefix of the train split to build the char alphabet. The
    Belarusian alphabet saturates within a few thousand rows, so a capped
    scan is enough even when training on the full dataset."""
    texts: List[str] = []
    for row in iter_split(forms_path, "train", limit=scan_limit or None):
        texts.append(row.word)
        texts.append(row.lemma)
    return Vocab.build_from_texts(texts)


def batched(iterable: Iterable, batch_size: int) -> Iterator[list]:
    batch: list = []
    for item in iterable:
        batch.append(item)
        if len(batch) >= batch_size:
            yield batch
            batch = []
    if batch:
        yield batch


def pad_sequences(seqs: List[List[int]], pad_value: int = 0) -> Tuple[torch.Tensor, torch.Tensor]:
    lengths = torch.tensor([max(1, len(s)) for s in seqs], dtype=torch.long)
    max_len = int(lengths.max().item())
    out = torch.full((len(seqs), max_len), pad_value, dtype=torch.long)
    for i, s in enumerate(seqs):
        if s:
            out[i, : len(s)] = torch.tensor(s, dtype=torch.long)
    return out, lengths


class BatchEncoder:
    """Turns a list of ``FormRow`` into the tensors ``TaggerModel`` expects."""

    def __init__(self, char_vocab: Vocab, tree: TagTree):
        self.char_vocab = char_vocab
        self.tree = tree
        self.codec = TagCodec(tree)
        self.idx_to_tag_letter = self.codec.idx_to_tag_letter
        self.tag_letter_to_idx = self.codec.tag_letter_to_idx
        self.tag_vocab = self.codec.tag_vocab

    @property
    def num_tag_letters(self) -> int:
        return self.codec.num_tag_letters

    def encode_batch(self, rows: List[FormRow]):
        word_idx, word_len = pad_sequences([self.char_vocab.encode(r.word) for r in rows])
        lemma_idx, lemma_len = pad_sequences([self.char_vocab.encode(r.lemma) for r in rows])

        tag_lengths = [len(r.tag) for r in rows]
        T = max(tag_lengths)
        B = len(rows)
        V = self.num_tag_letters

        prev_letter_idx = torch.zeros((B, T), dtype=torch.long)
        target = torch.full((B, T), -100, dtype=torch.long)
        allowed_mask = torch.zeros((B, T, V), dtype=torch.bool)

        bos_idx = self.tag_vocab.encode_token(BOS)
        for i, row in enumerate(rows):
            positions = self.tree.walk(row.tag)
            for t, pos in enumerate(positions):
                prev_letter_idx[i, t] = (
                    bos_idx if t == 0 else self.tag_vocab.encode_token(row.tag[t - 1])
                )
                target[i, t] = self.tag_letter_to_idx[pos.letter]
                for letter in pos.allowed:
                    allowed_mask[i, t, self.tag_letter_to_idx[letter]] = True
            # Positions beyond this example's real tag length: leave target
            # at -100 (ignored by the loss) but keep the mask fully open so
            # log_softmax never sees an all -inf row (which would be NaN).
            for t in range(len(positions), T):
                allowed_mask[i, t, :] = True

        return (
            word_idx.to(DEVICE),
            word_len,
            lemma_idx.to(DEVICE),
            lemma_len,
            prev_letter_idx.to(DEVICE),
            allowed_mask.to(DEVICE),
            target.to(DEVICE),
        )


def evaluate(
    model: TaggerModel, encoder: BatchEncoder, rows: List[FormRow], tree: TagTree
) -> Dict[str, dict]:
    model.eval()
    per_group_correct: Dict[str, int] = defaultdict(int)
    per_group_total: Dict[str, int] = defaultdict(int)
    with torch.no_grad():
        for row in rows:
            word_idx, word_len = pad_sequences([encoder.char_vocab.encode(row.word)])
            lemma_idx, lemma_len = pad_sequences([encoder.char_vocab.encode(row.lemma)])
            predicted = model.decode(
                word_idx.to(DEVICE),
                word_len,
                lemma_idx.to(DEVICE),
                lemma_len,
                known_prefix="",
                tree=tree,
                tag_letter_to_idx=encoder.tag_letter_to_idx,
                idx_to_tag_letter=encoder.idx_to_tag_letter,
                tag_vocab=encoder.tag_vocab,
                max_steps=tree.max_depth(),
            )
            positions = tree.walk(row.tag)
            for i, pos in enumerate(positions):
                per_group_total[pos.group] += 1
                if i < len(predicted) and predicted[i] == pos.letter:
                    per_group_correct[pos.group] += 1
    model.train()

    report: Dict[str, dict] = {}
    total_correct = total_count = 0
    for group in sorted(per_group_total):
        correct, total = per_group_correct[group], per_group_total[group]
        report[group] = {"accuracy": correct / total, "n": total}
        total_correct += correct
        total_count += total
    report["__overall__"] = {
        "accuracy": total_correct / total_count if total_count else 0.0,
        "n": total_count,
    }
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--forms", default="forms.tsv")
    parser.add_argument("--tag-tree", default="tag_tree.json")
    parser.add_argument("--out", default="artifacts/bilstm.pt")
    parser.add_argument("--limit-train", type=int, default=0)
    parser.add_argument("--limit-eval", type=int, default=0)
    parser.add_argument("--vocab-scan-limit", type=int, default=200_000)
    parser.add_argument("--epochs", type=int, default=1)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--log-every", type=int, default=200)
    parser.add_argument("--char-embed-dim", type=int, default=32)
    parser.add_argument("--word-hidden-dim", type=int, default=96)
    parser.add_argument("--lemma-hidden-dim", type=int, default=64)
    parser.add_argument("--tag-embed-dim", type=int, default=24)
    parser.add_argument("--decoder-hidden-dim", type=int, default=128)
    args = parser.parse_args()

    tree = TagTree.load(args.tag_tree)
    char_vocab = build_char_vocab(args.forms, args.vocab_scan_limit)
    encoder = BatchEncoder(char_vocab, tree)

    hparams = dict(
        char_embed_dim=args.char_embed_dim,
        word_hidden_dim=args.word_hidden_dim,
        lemma_hidden_dim=args.lemma_hidden_dim,
        tag_embed_dim=args.tag_embed_dim,
        decoder_hidden_dim=args.decoder_hidden_dim,
    )
    model = TaggerModel(
        char_vocab=char_vocab,
        tag_vocab=encoder.tag_vocab,
        num_tag_letters=encoder.num_tag_letters,
        **hparams,
    ).to(DEVICE)
    optimizer = torch.optim.Adam(model.parameters(), lr=args.lr)

    t0 = time.time()
    step = 0
    for epoch in range(args.epochs):
        train_rows = iter_split(args.forms, "train", limit=args.limit_train or None)
        for batch_rows in batched(train_rows, args.batch_size):
            (
                word_idx,
                word_len,
                lemma_idx,
                lemma_len,
                prev_letter_idx,
                allowed_mask,
                target,
            ) = encoder.encode_batch(batch_rows)

            logits = model(word_idx, word_len, lemma_idx, lemma_len, prev_letter_idx, allowed_mask)
            loss = masked_cross_entropy(logits, target)

            optimizer.zero_grad()
            loss.backward()
            optimizer.step()

            step += 1
            if step % args.log_every == 0:
                print(
                    f"epoch={epoch} step={step} loss={loss.item():.4f} "
                    f"elapsed={time.time() - t0:.1f}s",
                    file=sys.stderr,
                )
        print(f"epoch {epoch} done, elapsed={time.time() - t0:.1f}s", file=sys.stderr)

    val_rows = list(iter_split(args.forms, "val", limit=args.limit_eval or None))
    test_rows = list(iter_split(args.forms, "test", limit=args.limit_eval or None))
    val_report = evaluate(model, encoder, val_rows, tree) if val_rows else {}
    test_report = evaluate(model, encoder, test_rows, tree) if test_rows else {}

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(
        {
            "model_state": model.state_dict(),
            "char_vocab": char_vocab.itos[2:],
            "idx_to_tag_letter": encoder.idx_to_tag_letter,
            "hparams": hparams,
        },
        out_path,
    )
    metrics_path = out_path.parent / "bilstm_metrics.json"
    with open(metrics_path, "w", encoding="utf-8") as f:
        json.dump(
            {"val": val_report, "test": test_report, "elapsed_seconds": time.time() - t0},
            f,
            ensure_ascii=False,
            indent=2,
        )

    print(f"saved model -> {out_path}")
    print(f"saved metrics -> {metrics_path}")
    if val_report:
        print(f"val overall accuracy: {val_report['__overall__']['accuracy']:.4f}")
    if test_report:
        print(f"test overall accuracy: {test_report['__overall__']['accuracy']:.4f}")


if __name__ == "__main__":
    main()
