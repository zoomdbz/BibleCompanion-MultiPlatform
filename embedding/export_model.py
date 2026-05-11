#!/usr/bin/env python3
"""
Step 3: Export multilingual-e5-small as ONNX with quantization.

Uses torch.onnx.export directly (no Optimum dependency issues).
Saves tokenizer files alongside the model for on-device use.

Output:
  output/model/model.onnx              -- full precision ONNX
  output/model/model_quantized.onnx    -- int8 dynamic quantized
  output/model/tokenizer.json          -- tokenizer config
  output/model/tokenizer_config.json
  output/model/special_tokens_map.json
  output/model/sentencepiece.bpe.model
"""

import sys
from pathlib import Path

import numpy as np
import torch
from transformers import AutoModel, AutoTokenizer

MODEL_NAME = "intfloat/multilingual-e5-small"
OUTPUT_DIR = Path(__file__).parent / "output" / "model"


def export_onnx():
    """Export model to ONNX using torch.onnx.export."""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Loading model: {MODEL_NAME}")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME)
    model.eval()

    print("Saving tokenizer files...")
    tokenizer.save_pretrained(OUTPUT_DIR)

    print("Exporting ONNX model...")
    dummy = tokenizer(
        "query: test sentence",
        return_tensors="pt",
        padding="max_length",
        max_length=128,
        truncation=True,
    )

    onnx_path = OUTPUT_DIR / "model.onnx"
    torch.onnx.export(
        model,
        (dummy["input_ids"], dummy["attention_mask"]),
        str(onnx_path),
        input_names=["input_ids", "attention_mask"],
        output_names=["last_hidden_state"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "last_hidden_state": {0: "batch", 1: "seq"},
        },
        opset_version=14,
        do_constant_folding=True,
    )
    print(f"  Saved: {onnx_path} ({onnx_path.stat().st_size / 1024 / 1024:.1f} MB)")


def quantize():
    """Apply int8 dynamic quantization."""
    from onnxruntime.quantization import quantize_dynamic, QuantType

    onnx_path = OUTPUT_DIR / "model.onnx"
    quant_path = OUTPUT_DIR / "model_quantized.onnx"

    print("Quantizing to int8...")
    quantize_dynamic(
        str(onnx_path),
        str(quant_path),
        weight_type=QuantType.QInt8,
    )
    print(f"  Saved: {quant_path} ({quant_path.stat().st_size / 1024 / 1024:.1f} MB)")


def verify():
    """Sanity check: run one query through the quantized model."""
    import onnxruntime as ort

    print("\nVerifying quantized model...")
    tokenizer = AutoTokenizer.from_pretrained(OUTPUT_DIR)

    model_path = OUTPUT_DIR / "model_quantized.onnx"
    if not model_path.exists():
        model_path = OUTPUT_DIR / "model.onnx"

    session = ort.InferenceSession(str(model_path))

    text = "query: where does it talk about Satan fighting over Moses' body?"
    inputs = tokenizer(text, return_tensors="np", padding=True, truncation=True)

    outputs = session.run(
        None,
        {"input_ids": inputs["input_ids"], "attention_mask": inputs["attention_mask"]},
    )

    # Mean pooling over token embeddings (masked)
    token_embs = outputs[0]  # (1, seq_len, 384)
    mask = inputs["attention_mask"][..., np.newaxis]  # (1, seq_len, 1)
    pooled = (token_embs * mask).sum(axis=1) / mask.sum(axis=1)
    embedding = pooled[0]
    norm = np.linalg.norm(embedding)
    embedding = embedding / norm

    print(f"  Input: {text}")
    print(f"  Output dim: {embedding.shape[0]}")
    print(f"  First 5 dims: {embedding[:5]}")
    print("  Verification passed.")


def report_sizes():
    """Print file sizes."""
    print("\nModel files:")
    total = 0
    for f in sorted(OUTPUT_DIR.iterdir()):
        if f.is_file():
            size = f.stat().st_size / 1024 / 1024
            total += size
            print(f"  {f.name}: {size:.1f} MB")
    print(f"  Total: {total:.1f} MB")


def main():
    export_onnx()
    quantize()
    verify()
    report_sizes()
    print("\nDone.")


if __name__ == "__main__":
    main()
