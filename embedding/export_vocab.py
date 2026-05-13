#!/usr/bin/env python3
"""Export tokenizer vocabulary from tokenizer.json to compact binary format.

Binary format (vocab.bin):
  Magic: "SPVK" (4 bytes)
  Version: 1 (uint32 LE)
  Count: N (uint32 LE)
  For each token (indexed by ID, 0..N-1):
    Score: float32 LE
    TokenLen: uint16 LE
    TokenBytes: byte[TokenLen] (UTF-8)

Usage:
  python export_vocab.py
  -> reads output/model/tokenizer.json
  -> writes output/vocab.bin
"""

import json
import struct
import sys
from pathlib import Path


def export_vocab(model_dir: Path, output_path: Path):
    tokenizer_path = model_dir / "tokenizer.json"
    if not tokenizer_path.exists():
        print(f"ERROR: {tokenizer_path} not found", file=sys.stderr)
        sys.exit(1)

    with open(tokenizer_path, encoding="utf-8") as f:
        tok = json.load(f)

    vocab = tok["model"]["vocab"]
    print(f"Vocab size: {len(vocab)}")

    with open(output_path, "wb") as f:
        f.write(b"SPVK")
        f.write(struct.pack("<I", 1))
        f.write(struct.pack("<I", len(vocab)))
        for token, score in vocab:
            token_bytes = token.encode("utf-8")
            f.write(struct.pack("<f", float(score)))
            f.write(struct.pack("<H", len(token_bytes)))
            f.write(token_bytes)

    size_mb = output_path.stat().st_size / 1024 / 1024
    print(f"Wrote {output_path} ({size_mb:.1f} MB, {len(vocab)} tokens)")


if __name__ == "__main__":
    script_dir = Path(__file__).parent
    model_dir = script_dir / "output" / "model"
    output_path = script_dir / "output" / "vocab.bin"
    export_vocab(model_dir, output_path)
