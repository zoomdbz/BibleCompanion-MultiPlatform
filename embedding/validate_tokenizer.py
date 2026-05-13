#!/usr/bin/env python3
"""Validate that our binary vocab format + Unigram Viterbi produces the same
token IDs as the HuggingFace tokenizer.

This script reimplements the Kotlin EmbeddingSearch.tokenize() logic in Python
and compares against AutoTokenizer. Any mismatches indicate a bug in the
Kotlin tokenizer that would produce wrong query embeddings.

Run from the embedding/ directory with the venv active:
  python validate_tokenizer.py
"""

import json
import struct
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
MODEL_DIR = SCRIPT_DIR / "output" / "model"
VOCAB_PATH = SCRIPT_DIR / "output" / "vocab.bin"

BOS_ID = 0
EOS_ID = 2
UNK_ID = 3
MAX_TOKEN_LEN = 16
METASPACE = "▁"


def load_vocab_bin(path):
    """Load vocab.bin in the same way the Kotlin code would."""
    with open(path, "rb") as f:
        magic = f.read(4)
        assert magic == b"SPVK", f"Bad magic: {magic}"
        version = struct.unpack("<I", f.read(4))[0]
        count = struct.unpack("<I", f.read(4))[0]
        scores = []
        tokens = []
        for i in range(count):
            score = struct.unpack("<f", f.read(4))[0]
            tlen = struct.unpack("<H", f.read(2))[0]
            token = f.read(tlen).decode("utf-8")
            scores.append(score)
            tokens.append(token)
    special_ids = {0, 1, 2, 3, count - 1}
    lookup = {}
    for i, tok in enumerate(tokens):
        if i not in special_ids:
            lookup[tok] = i
    return scores, lookup


def tokenize_our(text, scores, lookup):
    """Reimplement the Kotlin Unigram Viterbi tokenizer."""
    import unicodedata

    normalized = unicodedata.normalize("NFKC", text)
    processed = METASPACE + normalized.replace(" ", METASPACE)

    pieces = []
    start = 0
    for i in range(1, len(processed)):
        if processed[i] == METASPACE:
            if start < i:
                pieces.append(processed[start:i])
            start = i
    if start < len(processed):
        pieces.append(processed[start:])

    all_ids = [BOS_ID]
    for piece in pieces:
        ids = unigram_viterbi(piece, scores, lookup)
        all_ids.extend(ids)
    all_ids.append(EOS_ID)
    return all_ids


def unigram_viterbi(text, scores, lookup):
    n = len(text)
    if n == 0:
        return []
    best_score = [0.0] + [float("-inf")] * n
    best_id = [-1] * (n + 1)
    best_len = [0] * (n + 1)

    for i in range(n):
        if best_score[i] == float("-inf"):
            continue
        found = False
        for length in range(1, min(MAX_TOKEN_LEN, n - i) + 1):
            sub = text[i : i + length]
            tid = lookup.get(sub)
            if tid is None:
                continue
            found = True
            s = best_score[i] + scores[tid]
            if s > best_score[i + length]:
                best_score[i + length] = s
                best_id[i + length] = tid
                best_len[i + length] = length
        if not found:
            s = best_score[i] - 100.0
            if s > best_score[i + 1]:
                best_score[i + 1] = s
                best_id[i + 1] = UNK_ID
                best_len[i + 1] = 1

    ids = []
    pos = n
    while pos > 0 and best_len[pos] > 0:
        ids.append(best_id[pos])
        pos -= best_len[pos]
    ids.reverse()
    return ids


def main():
    from transformers import AutoTokenizer

    print("Loading HuggingFace tokenizer...")
    hf_tok = AutoTokenizer.from_pretrained(str(MODEL_DIR))

    print("Loading vocab.bin...")
    scores, lookup = load_vocab_bin(VOCAB_PATH)
    print(f"  {len(scores)} tokens, {len(lookup)} in lookup")

    queries = [
        "query: walking on water",
        "query: who is the suffering servant",
        "query: salvation by grace",
        "query: marriage in the Bible",
        "query: armor of God",
        "query: what happens after death",
        "query: parable of the sower",
        "query: Daniel's vision of four beasts",
        "query: love your enemies",
        "query: fruit of the Spirit",
        "query: baptism",
        "query: resurrection of Jesus",
        "query: Ten Commandments",
        "query: Sermon on the Mount",
        "query: prodigal son",
        # Non-English queries
        "query: marcher sur l'eau",
        "query: la armadura de Dios",
        "query: die Auferstehung Jesu",
        "query: camminare sull'acqua",
        "query: 十字架",
        "query: 復活",
        "query: 바다 위를 걷다",
        "query: ходить по воде",
        "query: المشي على الماء",
        "query: पानी पर चलना",
    ]

    passed = 0
    failed = 0
    for q in queries:
        hf_ids = hf_tok.encode(q)
        our_ids = tokenize_our(q, scores, lookup)
        match = hf_ids == our_ids
        status = "PASS" if match else "FAIL"
        if match:
            passed += 1
        else:
            failed += 1
            print(f"  {status}: {q}")
            print(f"    HF:  {hf_ids}")
            print(f"    Ours: {our_ids}")
        if match:
            print(f"  {status}: {q} ({len(hf_ids)} tokens)")

    print(f"\n{passed}/{passed+failed} passed, {failed} failed")
    if failed > 0:
        print("TOKENIZER MISMATCH: Kotlin implementation will produce wrong embeddings!")
        return 1
    else:
        print("All queries match. Kotlin tokenizer is correct.")
        return 0


if __name__ == "__main__":
    exit(main())
