"""The single unified BiLSTM tagger model (IMPLEMENTATION.md p.3/p.7 step 4).

Architecture (confirmed with the user: **one** network, not a set of
independent per-group models):

1. Shared encoder, computed **once** per example:
   char-embedding -> BiLSTM over the word (+ a second BiLSTM over the
   lemma) -> concatenation of the final forward/backward hidden states of
   each (NOT mean-pooling -- see IMPLEMENTATION.md p.4: this preserves the
   LSTM's natural recency bias towards the end of the word, where
   Belarusian grammatical information concentrates). The two encoders'
   final states are concatenated into a single context vector ``v`` that is
   reused, unchanged, for every subsequent decoding step of this example.
2. Shared decoder loop over tag positions: a single ``GRUCell`` conditioned
   on ``v`` (fed at every step) and the embedding of the previous
   letter (known or already predicted). Its hidden state carries the
   "current tree node" information implicitly.
3. Single shared output head: one ``Linear`` layer mapping the decoder
   hidden state to logits over the *entire* tag-letter alphabet -- not one
   head per group. The tree-constrained mask (which letters are valid at
   the current node) is applied to these logits before the softmax/loss,
   exactly as described in p.3 ("softmax, masked by the allowed children of
   the current TagLetter node").

Training uses teacher forcing: for a batch of (word, lemma, full tag)
triples, the decoder is unrolled once over the *entire* real tag with the
true previous letter fed back at each step, and a masked cross-entropy loss
is computed at every position simultaneously -- this implicitly covers
every possible suffix-masking cut in a single forward/backward pass (no
need to materialize separate masked copies per position, unlike the
tabular baseline).

Inference (``decode``) runs the same loop autoregressively: at each step it
takes the arg-max of the masked distribution, feeds it back as the "known"
previous letter, and steps the real ``TagTree`` node forward -- stopping
exactly when the tree node has no children (a terminal node), with no need
to know the target tag's length ahead of time (see p.3).
"""
from __future__ import annotations

from typing import Dict, List, Optional, Tuple

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.nn.utils.rnn import pack_padded_sequence

from common.tag_tree import TagTree
from common.vocab import Vocab

BOS = "<bos>"  # fed as the "previous letter" at decoding step 0


class TagCodec:
    """Bundles the tag-letter <-> index mappings shared by training and
    inference: ``idx_to_tag_letter``/``tag_letter_to_idx`` (decoder output
    classes, no BOS/PAD/UNK) and ``tag_vocab`` (decoder *input* embedding
    vocabulary: tag letters + the special ``BOS`` token fed at step 0)."""

    def __init__(self, tree: TagTree):
        self.idx_to_tag_letter: List[str] = sorted(tree.alphabet())
        self.tag_letter_to_idx: Dict[str, int] = {
            letter: i for i, letter in enumerate(self.idx_to_tag_letter)
        }
        self.tag_vocab = Vocab(self.idx_to_tag_letter + [BOS])

    @property
    def num_tag_letters(self) -> int:
        return len(self.idx_to_tag_letter)


class CharBiLSTMEncoder(nn.Module):
    """Char-embedding + BiLSTM; returns the concatenated final
    forward/backward hidden states (see module docstring point 1)."""

    def __init__(self, vocab_size: int, embed_dim: int, hidden_dim: int, num_layers: int = 1):
        super().__init__()
        self.embedding = nn.Embedding(vocab_size, embed_dim, padding_idx=0)
        self.lstm = nn.LSTM(
            embed_dim,
            hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            bidirectional=True,
        )
        self.output_dim = hidden_dim * 2

    def forward(self, idx: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        # idx: [B, L] (0 = PAD), lengths: [B] (>=1)
        embedded = self.embedding(idx)
        packed = pack_padded_sequence(
            embedded, lengths.cpu(), batch_first=True, enforce_sorted=False
        )
        _, (h_n, _) = self.lstm(packed)
        # h_n: [num_layers*2, B, hidden_dim]; last layer's fwd/bwd = last two entries
        forward_final = h_n[-2]
        backward_final = h_n[-1]
        return torch.cat([forward_final, backward_final], dim=-1)  # [B, 2*hidden_dim]


class TaggerModel(nn.Module):
    def __init__(
        self,
        char_vocab: Vocab,
        tag_vocab: Vocab,  # tag letters + BOS, used as decoder *input* embedding
        num_tag_letters: int,  # decoder *output* size == len(tree.alphabet())
        char_embed_dim: int = 32,
        word_hidden_dim: int = 96,
        lemma_hidden_dim: int = 64,
        tag_embed_dim: int = 24,
        decoder_hidden_dim: int = 128,
    ):
        super().__init__()
        self.word_encoder = CharBiLSTMEncoder(len(char_vocab), char_embed_dim, word_hidden_dim)
        self.lemma_encoder = CharBiLSTMEncoder(len(char_vocab), char_embed_dim, lemma_hidden_dim)
        context_dim = self.word_encoder.output_dim + self.lemma_encoder.output_dim

        self.tag_embedding = nn.Embedding(len(tag_vocab), tag_embed_dim, padding_idx=0)
        self.context_to_hidden = nn.Linear(context_dim, decoder_hidden_dim)
        self.decoder_cell = nn.GRUCell(tag_embed_dim + context_dim, decoder_hidden_dim)
        self.output_layer = nn.Linear(decoder_hidden_dim, num_tag_letters)

        self.context_dim = context_dim
        self.decoder_hidden_dim = decoder_hidden_dim

    def encode(
        self,
        word_idx: torch.Tensor,
        word_len: torch.Tensor,
        lemma_idx: torch.Tensor,
        lemma_len: torch.Tensor,
    ) -> torch.Tensor:
        word_ctx = self.word_encoder(word_idx, word_len)
        lemma_ctx = self.lemma_encoder(lemma_idx, lemma_len)
        return torch.cat([word_ctx, lemma_ctx], dim=-1)  # [B, context_dim]

    def forward(
        self,
        word_idx: torch.Tensor,
        word_len: torch.Tensor,
        lemma_idx: torch.Tensor,
        lemma_len: torch.Tensor,
        prev_letter_idx: torch.Tensor,  # [B, T] teacher-forced previous letters (BOS at t=0)
        allowed_mask: torch.Tensor,  # [B, T, V] bool, True = allowed at this position
    ) -> torch.Tensor:
        """Teacher-forced training pass. Returns masked logits [B, T, V]."""
        context = self.encode(word_idx, word_len, lemma_idx, lemma_len)  # [B, C] -- computed ONCE
        hidden = torch.tanh(self.context_to_hidden(context))  # [B, H]

        B, T = prev_letter_idx.shape
        all_logits = []
        for t in range(T):
            prev_emb = self.tag_embedding(prev_letter_idx[:, t])  # [B, E]
            step_input = torch.cat([prev_emb, context], dim=-1)  # [B, E+C]
            hidden = self.decoder_cell(step_input, hidden)
            logits_t = self.output_layer(hidden)  # [B, V]
            logits_t = logits_t.masked_fill(~allowed_mask[:, t, :], float("-inf"))
            all_logits.append(logits_t)
        return torch.stack(all_logits, dim=1)  # [B, T, V]

    @torch.no_grad()
    def decode(
        self,
        word_idx: torch.Tensor,  # [1, L]
        word_len: torch.Tensor,  # [1]
        lemma_idx: torch.Tensor,  # [1, L]
        lemma_len: torch.Tensor,  # [1]
        known_prefix: str,
        tree: TagTree,
        tag_letter_to_idx: dict,
        idx_to_tag_letter: List[str],
        tag_vocab: Vocab,
        max_steps: int = 32,
    ) -> str:
        """Autoregressive decode (p.3): predict the first X position, step
        the tree, repeat until the tree node is terminal. Starts from
        ``known_prefix`` (already-resolved letters) and returns the full
        predicted tag string (known_prefix + predicted suffix)."""
        context = self.encode(word_idx, word_len, lemma_idx, lemma_len)
        hidden = torch.tanh(self.context_to_hidden(context))

        node = tree.node_after(known_prefix)
        # Replay the known prefix through the decoder so its hidden state
        # reflects everything already resolved, teacher-forced with the
        # real known letters.
        prev_token = BOS
        for ch in known_prefix:
            prev_emb = self.tag_embedding(
                torch.tensor([tag_vocab.encode_token(prev_token)])
            )
            step_input = torch.cat([prev_emb, context], dim=-1)
            hidden = self.decoder_cell(step_input, hidden)
            prev_token = ch

        predicted = known_prefix
        for _ in range(max_steps):
            if tree.is_terminal(node):
                break
            allowed = tree.allowed_letters(node)
            prev_emb = self.tag_embedding(
                torch.tensor([tag_vocab.encode_token(prev_token)])
            )
            step_input = torch.cat([prev_emb, context], dim=-1)
            hidden = self.decoder_cell(step_input, hidden)
            logits = self.output_layer(hidden)[0]  # [V]
            mask = torch.full_like(logits, float("-inf"))
            for letter in allowed:
                mask[tag_letter_to_idx[letter]] = 0.0
            letter_idx = int(torch.argmax(logits + mask).item())
            letter = idx_to_tag_letter[letter_idx]
            predicted += letter
            node = tree.step(node, letter)
            prev_token = letter
        return predicted


def masked_cross_entropy(
    logits: torch.Tensor, target: torch.Tensor, ignore_index: int = -100
) -> torch.Tensor:
    """``logits``: [B, T, V] (already masked with -inf for disallowed
    classes); ``target``: [B, T] with ``ignore_index`` for padding beyond
    each example's real tag length."""
    B, T, V = logits.shape
    return F.cross_entropy(
        logits.reshape(B * T, V), target.reshape(B * T), ignore_index=ignore_index
    )


def load_checkpoint(
    path: str, tree: TagTree, device: Optional[torch.device] = None
) -> Tuple[TaggerModel, Vocab, TagCodec]:
    """Reconstruct a trained ``TaggerModel`` (+ its char vocab and tag
    codec) from a checkpoint saved by ``train_bilstm.py``."""
    device = device or torch.device("cpu")
    checkpoint = torch.load(path, map_location=device)
    char_vocab = Vocab(checkpoint["char_vocab"])
    codec = TagCodec(tree)
    assert codec.idx_to_tag_letter == checkpoint["idx_to_tag_letter"], (
        "tag_tree.json alphabet does not match the one this checkpoint was "
        "trained with -- did tag_tree.json change since training?"
    )
    model = TaggerModel(
        char_vocab=char_vocab,
        tag_vocab=codec.tag_vocab,
        num_tag_letters=codec.num_tag_letters,
        **checkpoint["hparams"],
    ).to(device)
    model.load_state_dict(checkpoint["model_state"])
    model.eval()
    return model, char_vocab, codec
