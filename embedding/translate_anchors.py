#!/usr/bin/env python3
"""
Translate CANONICAL_NAMES and VERSE_ANCHORS to all target languages
using facebook/nllb-200-distilled-1.3B running locally on GPU.

First run downloads ~2.6 GB model. Typical runtime: ~1 min/language on RTX 3090.

Usage:
  python translate_anchors.py              # all 12 languages
  python translate_anchors.py de es fr     # specific languages
  python translate_anchors.py --model 600m # smaller/faster model
  python translate_anchors.py --force      # overwrite existing

Output: output/anchors/{lang}.json
"""

import argparse
import json
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
OUTPUT_DIR = SCRIPT_DIR / "output" / "anchors"

MODELS = {
    "600m": "facebook/nllb-200-distilled-600M",
    "1.3b": "facebook/nllb-200-distilled-1.3B",
    "3.3b": "facebook/nllb-200-3.3B",
}

LANG_CODES = {
    "ar": "arb_Arab",
    "de": "deu_Latn",
    "es": "spa_Latn",
    "fr": "fra_Latn",
    "hi": "hin_Deva",
    "it": "ita_Latn",
    "ja": "jpn_Jpan",
    "ko": "kor_Hang",
    "pt": "por_Latn",
    "ru": "rus_Cyrl",
    "zh-Hans": "zho_Hans",
    "zh-Hant": "zho_Hant",
}


def load_source_dicts():
    """Import CANONICAL_NAMES and VERSE_ANCHORS from generate_embeddings.py
    without importing torch (which may not be available on all hosts)."""
    gen_path = SCRIPT_DIR / "generate_embeddings.py"
    source = gen_path.read_text(encoding="utf-8")

    cn = _extract_dict(source, "CANONICAL_NAMES")
    va = _extract_dict(source, "VERSE_ANCHORS")
    return cn, va


def _extract_dict(source: str, var_name: str) -> dict:
    """Extract a top-level dict assignment from Python source."""
    import ast
    start = source.find(f"{var_name} = {{")
    if start == -1:
        raise ValueError(f"Could not find {var_name} in source")
    depth = 0
    end = start
    for i in range(start, len(source)):
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    dict_source = source[start:end]
    assign_source = dict_source.replace(f"{var_name} = ", "", 1)
    lines = []
    for line in assign_source.split("\n"):
        comment_idx = line.find("#")
        if comment_idx >= 0:
            in_string = False
            quote_char = None
            for j, ch in enumerate(line):
                if ch in ('"', "'") and not in_string:
                    in_string = True
                    quote_char = ch
                elif ch == quote_char and in_string:
                    in_string = False
                elif ch == "#" and not in_string:
                    line = line[:j]
                    break
        lines.append(line)
    clean_source = "\n".join(lines)
    return ast.literal_eval(clean_source)


def translate_batch(model, tokenizer, texts, tgt_lang_id, batch_size=64, max_new_tokens=200):
    """Translate a flat list of texts. Returns list of translated strings."""
    import torch

    results = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]
        inputs = tokenizer(
            batch, return_tensors="pt", padding=True, truncation=True, max_length=256
        )
        inputs = {k: v.to(model.device) for k, v in inputs.items()}
        with torch.no_grad():
            generated = model.generate(
                **inputs, forced_bos_token_id=tgt_lang_id, max_new_tokens=max_new_tokens
            )
        decoded = tokenizer.batch_decode(generated, skip_special_tokens=True)
        results.extend(decoded)
    return results


def translate_segmented(model, tokenizer, source_dict, tgt_lang_id, sep_in, sep_out, batch_size):
    """Split each value by sep_in, translate segments individually, rejoin with sep_out."""
    keys = list(source_dict.keys())

    all_segments = []
    segment_counts = []
    for key in keys:
        parts = [s.strip() for s in source_dict[key].split(sep_in) if s.strip()]
        segment_counts.append(len(parts))
        all_segments.extend(parts)

    translated = translate_batch(model, tokenizer, all_segments, tgt_lang_id, batch_size=batch_size)

    result = {}
    offset = 0
    for i, key in enumerate(keys):
        n = segment_counts[i]
        segs = translated[offset : offset + n]
        result[key] = sep_out.join(segs)
        offset += n

    return result


def main():
    import torch
    from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

    parser = argparse.ArgumentParser(description="Translate embedding anchors to target languages")
    parser.add_argument("langs", nargs="*", help="Target languages (default: all 12)")
    parser.add_argument("--model", choices=MODELS.keys(), default="1.3b", help="NLLB model size (default: 1.3b)")
    parser.add_argument("--force", action="store_true", help="Overwrite existing translations")
    parser.add_argument("--batch-size", type=int, default=64, help="Translation batch size (default: 64)")
    args = parser.parse_args()

    langs = args.langs if args.langs else list(LANG_CODES.keys())
    langs = [l for l in langs if l in LANG_CODES]

    if not langs:
        print("No valid languages specified. Available:", ", ".join(LANG_CODES.keys()))
        sys.exit(1)

    if not args.force:
        skip = [l for l in langs if (OUTPUT_DIR / f"{l}.json").exists()]
        langs = [l for l in langs if l not in skip]
        if skip:
            print(f"Skipping (already exist): {', '.join(skip)}  (use --force to overwrite)")
        if not langs:
            print("All requested languages already translated.")
            return

    print("Loading source dicts from generate_embeddings.py...", flush=True)
    canonical_names, verse_anchors = load_source_dicts()

    cn_segs = sum(len([s for s in v.split(",") if s.strip()]) for v in canonical_names.values())
    va_segs = sum(len([s for s in v.split(";") if s.strip()]) for v in verse_anchors.values())

    print(f"Languages: {', '.join(langs)}")
    print(f"Canonical names: {len(canonical_names)} entries ({cn_segs} segments)")
    print(f"Verse anchors: {len(verse_anchors)} entries ({va_segs} segments)")
    print(f"Total segments per language: {cn_segs + va_segs}")
    print()

    model_name = MODELS[args.model]
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Loading {model_name} on {device}...", flush=True)
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSeq2SeqLM.from_pretrained(model_name, use_safetensors=True).to(device)
    if device == "cuda":
        print(f"GPU: {torch.cuda.get_device_name(0)}", flush=True)
    print("Model loaded.", flush=True)
    print()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    total_t0 = time.time()

    for lang in langs:
        tgt_code = LANG_CODES[lang]
        tgt_lang_id = tokenizer.convert_tokens_to_ids(tgt_code)
        print(f"--- {lang} ({tgt_code}) ---", flush=True)
        t0 = time.time()

        print(f"  Canonical names ({cn_segs} segments)...", flush=True)
        cn_translated = translate_segmented(
            model, tokenizer, canonical_names, tgt_lang_id,
            sep_in=",", sep_out=", ", batch_size=args.batch_size,
        )

        print(f"  Verse anchors ({va_segs} segments)...", flush=True)
        va_translated = translate_segmented(
            model, tokenizer, verse_anchors, tgt_lang_id,
            sep_in=";", sep_out="; ", batch_size=args.batch_size,
        )

        output = {
            "canonical_names": cn_translated,
            "verse_anchors": va_translated,
        }

        outpath = OUTPUT_DIR / f"{lang}.json"
        with open(outpath, "w", encoding="utf-8") as f:
            json.dump(output, f, ensure_ascii=False, indent=2)

        elapsed = time.time() - t0
        size_kb = outpath.stat().st_size / 1024
        print(f"  Saved {outpath.name} ({size_kb:.0f} KB) in {elapsed:.1f}s", flush=True)
        print()

    total = time.time() - total_t0
    print(f"Done. {len(langs)} language(s) in {total:.0f}s. Output: {OUTPUT_DIR}/")


if __name__ == "__main__":
    main()
