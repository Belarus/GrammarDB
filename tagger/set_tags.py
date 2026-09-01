#!/usr/bin/env python3
"""Fill missing tag positions ('X') in fixtag.tsv using Baseline and BiLSTM models.

Reads /home/alex/gits/GrammarDB/fixtag.tsv, predicts the missing letter for
the category specified in 'група_X' using both LightGBM (baseline) and BiLSTM
models, resolves the descriptive name of the predicted value, and writes the
augmented table to /home/alex/gits/GrammarDB/fixtag-fixed.tsv.

Output columns:
1. група_X
2. tag
3. pdgId+variantId
4. variant_lemma
5. форма
6. tag_baseline
7. desc_baseline
8. tag_bilstm
9. desc_bilstm

Usage:
    /home/alex/.venv/bin/python3 set_tags.py \
        [--input /home/alex/gits/GrammarDB/fixtag.tsv] \
        [--output /home/alex/gits/GrammarDB/fixtag-fixed.tsv] \
        [--tag-tree tag_tree.json] \
        [--baseline-dir artifacts/baseline] \
        [--bilstm-checkpoint artifacts/bilstm.pt] \
        [--batch-size 1000]
"""
from __future__ import annotations

import argparse
import csv
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import torch
from torch.nn.utils.rnn import pad_sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

from common.baseline_model import BaselineModel, build_features, features_frame
from common.bilstm_model import BOS, load_checkpoint
from common.tag_tree import TagTree
from common.text import strip_stress


def clean_text(s: str) -> str:
    """Strip stress characters (+ and combining acute accent U+0301)."""
    return strip_stress(s).replace("\u0301", "")


def find_target_info(
    tag: str, group: str, tree: TagTree
) -> Optional[Tuple[int, str, List[str], Dict[str, str]]]:
    """Find the target 'X' position corresponding to `group`.

    Returns:
        (target_idx, prefix, allowed_letters, letter_to_desc) or None
    """
    for i, ch in enumerate(tag):
        if ch == "X":
            prefix = tag[:i]
            try:
                node = tree.node_after(prefix)
                children = tree.children_of(node)
                group_children = {
                    letter: entry["desc"]  # type: ignore[index]
                    for letter, entry in children.items()
                    if entry.get("group") == group  # type: ignore[index]
                }
                if group_children:
                    allowed = list(group_children.keys())
                    return i, prefix, allowed, group_children
            except Exception:
                pass
    return None


def predict_batch_baseline(
    baseline: BaselineModel,
    tree: TagTree,
    batch_items: List[dict],
) -> List[Tuple[str, str]]:
    """Predict letters and descriptions using the baseline LightGBM models."""
    by_group = defaultdict(list)
    for idx, item in enumerate(batch_items):
        by_group[item["group"]].append((idx, item["features"], item["allowed"], item["desc_map"]))

    predictions: List[Optional[Tuple[str, str]]] = [None] * len(batch_items)
    for grp, entries in by_group.items():
        indices = [e[0] for e in entries]
        feat_list = [e[1] for e in entries]
        allowed_list = [e[2] for e in entries]
        desc_list = [e[3] for e in entries]

        model = baseline.models.get(grp)
        if model is None or not hasattr(model, "predict_proba"):
            constant_letter = model if (model is not None and model in allowed_list[0]) else allowed_list[0][0]
            for i, idx in enumerate(indices):
                d = desc_list[i].get(constant_letter, "")
                predictions[idx] = (constant_letter, d)
        else:
            df = features_frame(feat_list, baseline.all_groups)
            probs = model.predict_proba(df)
            classes = list(model.classes_)
            for i, idx in enumerate(indices):
                allowed = allowed_list[i]
                desc_map = desc_list[i]
                proba = probs[i]
                best_letter, best_p = None, -1.0
                for letter in allowed:
                    p = proba[classes.index(letter)] if letter in classes else 0.0
                    if p > best_p:
                        best_letter, best_p = letter, p
                chosen = best_letter if best_letter is not None else allowed[0]
                predictions[idx] = (chosen, desc_map.get(chosen, ""))

    return [p if p is not None else ("", "") for p in predictions]


def predict_batch_bilstm(
    bilstm,
    char_vocab,
    codec,
    device: torch.device,
    batch_items: List[dict],
) -> List[Tuple[str, str]]:
    """Predict letters and descriptions using the BiLSTM model."""
    if not batch_items:
        return []

    clean_words = [item["clean_form"] for item in batch_items]
    clean_lemmas = [item["clean_lemma"] for item in batch_items]

    w_tensors = [torch.tensor(char_vocab.encode(w), dtype=torch.long) for w in clean_words]
    w_lens = torch.tensor([max(1, len(w)) for w in clean_words], dtype=torch.long, device=device)
    w_padded = pad_sequence(w_tensors, batch_first=True, padding_value=0).to(device)

    l_tensors = [torch.tensor(char_vocab.encode(l), dtype=torch.long) for l in clean_lemmas]
    l_lens = torch.tensor([max(1, len(l)) for l in clean_lemmas], dtype=torch.long, device=device)
    l_padded = pad_sequence(l_tensors, batch_first=True, padding_value=0).to(device)

    with torch.no_grad():
        context = bilstm.encode(w_padded, w_lens, l_padded, l_lens)
        hidden = torch.tanh(bilstm.context_to_hidden(context))

    predictions: List[Tuple[str, str]] = []
    with torch.no_grad():
        for i, item in enumerate(batch_items):
            ctx_i = context[i : i + 1]
            hid_i = hidden[i : i + 1]
            prefix = item["prefix"]
            allowed = item["allowed"]
            desc_map = item["desc_map"]

            prev_token = BOS
            for p_ch in prefix:
                prev_emb = bilstm.tag_embedding(
                    torch.tensor([codec.tag_vocab.encode_token(prev_token)], device=device)
                )
                step_input = torch.cat([prev_emb, ctx_i], dim=-1)
                hid_i = bilstm.decoder_cell(step_input, hid_i)
                prev_token = p_ch

            prev_emb = bilstm.tag_embedding(
                torch.tensor([codec.tag_vocab.encode_token(prev_token)], device=device)
            )
            step_input = torch.cat([prev_emb, ctx_i], dim=-1)
            hid_i = bilstm.decoder_cell(step_input, hid_i)
            logits = bilstm.output_layer(hid_i)[0]

            best_letter, best_logit = None, float("-inf")
            for letter in allowed:
                idx = codec.tag_letter_to_idx[letter]
                val = logits[idx].item()
                if val > best_logit:
                    best_letter, best_logit = letter, val
            chosen = best_letter if best_letter is not None else allowed[0]
            predictions.append((chosen, desc_map.get(chosen, "")))

    return predictions


def process_fixtag(
    input_path: Path,
    output_path: Path,
    tree: TagTree,
    baseline: BaselineModel,
    bilstm,
    char_vocab,
    codec,
    device: torch.device,
    batch_size: int = 1000,
) -> None:
    # First count total rows
    with open(input_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        header = next(reader)
        total_rows = sum(1 for _ in reader)

    new_header = header + ["tag_baseline", "desc_baseline", "tag_bilstm", "desc_bilstm"]
    output_path.parent.mkdir(parents=True, exist_ok=True)

    t0 = time.time()
    processed_count = 0

    with open(input_path, "r", encoding="utf-8") as in_f, open(
        output_path, "w", encoding="utf-8", newline=""
    ) as out_f:
        reader = csv.reader(in_f, delimiter="\t")
        writer = csv.writer(out_f, delimiter="\t")

        next(reader)  # skip header
        writer.writerow(new_header)

        batch_raw: List[List[str]] = []
        batch_items: List[dict] = []

        def flush_batch() -> None:
            nonlocal processed_count
            if not batch_raw:
                return

            base_preds = predict_batch_baseline(baseline, tree, batch_items)
            bi_preds = predict_batch_bilstm(bilstm, char_vocab, codec, device, batch_items)

            for i, raw_row in enumerate(batch_raw):
                target_idx = batch_items[i]["target_idx"]
                orig_tag = raw_row[1]

                base_letter, base_desc = base_preds[i]
                bi_letter, bi_desc = bi_preds[i]

                if target_idx is not None:
                    tag_base = orig_tag[:target_idx] + base_letter + orig_tag[target_idx + 1 :]
                    tag_bi = orig_tag[:target_idx] + bi_letter + orig_tag[target_idx + 1 :]
                else:
                    tag_base = orig_tag
                    tag_bi = orig_tag

                out_row = raw_row + [tag_base, base_desc, tag_bi, bi_desc]
                writer.writerow(out_row)

            processed_count += len(batch_raw)
            if processed_count % 1000 == 0 or processed_count == total_rows:
                pct = (processed_count / total_rows) * 100
                elapsed = time.time() - t0
                print(
                    f"Processed {processed_count}/{total_rows} rows ({pct:.1f}%) [elapsed: {elapsed:.1f}s]",
                    flush=True,
                )

            batch_raw.clear()
            batch_items.clear()

        for row in reader:
            if not row:
                continue
            grp, tag, pdg_var, lemma, form = row
            c_form = clean_text(form)
            c_lemma = clean_text(lemma)

            info = find_target_info(tag, grp, tree)
            if info is not None:
                t_idx, prefix, allowed, desc_map = info
            else:
                t_idx, prefix, allowed, desc_map = None, "", [], {}

            features = build_features(c_form, c_lemma, prefix, tree, baseline.all_groups)
            batch_raw.append(row)
            batch_items.append(
                {
                    "group": grp,
                    "target_idx": t_idx,
                    "prefix": prefix,
                    "allowed": allowed,
                    "desc_map": desc_map,
                    "clean_form": c_form,
                    "clean_lemma": c_lemma,
                    "features": features,
                }
            )

            if len(batch_raw) >= batch_size:
                flush_batch()

        flush_batch()

    total_time = time.time() - t0
    print(f"Finished processing {processed_count} rows in {total_time:.2f}s -> {output_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        default="/home/alex/gits/GrammarDB/fixtag.tsv",
        help="Input TSV file path",
    )
    parser.add_argument(
        "--output",
        default="/home/alex/gits/GrammarDB/fixtag-fixed.tsv",
        help="Output TSV file path",
    )
    parser.add_argument("--tag-tree", default="tag_tree.json")
    parser.add_argument("--baseline-dir", default="artifacts/baseline")
    parser.add_argument("--bilstm-checkpoint", default="artifacts/bilstm.pt")
    parser.add_argument("--batch-size", type=int, default=1000)
    parser.add_argument("--device", default=None)
    args = parser.parse_args()

    input_path = Path(args.input)
    output_path = Path(args.output)

    if not input_path.exists():
        print(f"Error: input file {input_path} does not exist", file=sys.stderr)
        sys.exit(1)

    device = (
        torch.device(args.device)
        if args.device
        else torch.device("cuda" if torch.cuda.is_available() else "cpu")
    )
    print(f"Loading TagTree from {args.tag_tree}...")
    tree = TagTree.load(args.tag_tree)

    print(f"Loading Baseline model from {args.baseline_dir}...")
    baseline = BaselineModel.load(args.baseline_dir, tree)

    print(f"Loading BiLSTM model from {args.bilstm_checkpoint} (device={device})...")
    bilstm, char_vocab, codec = load_checkpoint(args.bilstm_checkpoint, tree, device=device)

    print(f"Starting processing: {input_path} -> {output_path}")
    process_fixtag(
        input_path=input_path,
        output_path=output_path,
        tree=tree,
        baseline=baseline,
        bilstm=bilstm,
        char_vocab=char_vocab,
        codec=codec,
        device=device,
        batch_size=args.batch_size,
    )


if __name__ == "__main__":
    main()
