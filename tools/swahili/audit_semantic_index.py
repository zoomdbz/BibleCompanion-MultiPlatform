#!/usr/bin/env python3
"""Check the cross-language semantic index route for Swahili story search.

This proves asset and story-identity compatibility. Search relevance still
requires an on-device model run, which this offline check cannot perform.
"""

from __future__ import annotations

import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "embedding-assets/src/main/assets/embedding"
BOOKS = ROOT / "shared/assets/books"
ALIASES = {
    "1-corinthians": "1_corinthians", "1-kings": "1_kings",
    "1-samuel": "1_samuel", "1chronicles": "1_chronicles",
    "2-kings": "2_kings", "2-samuel": "2_samuel",
    "2chronicles": "2_chronicles", "song-of-songs": "song_of_songs",
}


def main() -> int:
    failures = []
    binary = ASSETS / "embeddings_en.bin"
    with binary.open("rb") as handle:
        header = handle.read(24)
    if len(header) != 24 or header[:4] != b"BCEF":
        failures.append("English embedding header is invalid")
        count = dim = 0
    else:
        count, dim = struct.unpack_from("<ii", header, 8)
        if count <= 0 or dim != 384 or binary.stat().st_size != 24 + count * dim:
            failures.append("English embedding dimensions or size are invalid")
    metadata = json.loads((ASSETS / "metadata_en.json").read_text(encoding="utf-8"))
    if len(metadata) != count:
        failures.append(f"metadata entries {len(metadata)} != embedding rows {count}")
    if not (ASSETS / "model_quantized.onnx").is_file() or not (ASSETS / "vocab.bin").is_file():
        failures.append("bundled multilingual model or tokenizer is absent")

    sw_stories = set()
    for collection in ("old_testament", "new_testament"):
        for path in (BOOKS / collection / "sw").glob("*.json"):
            if path.stem == "_index":
                continue
            book = json.loads(path.read_text(encoding="utf-8"))
            sw_stories.update((collection, path.stem, story["id"])
                              for story in book.get("stories", []))
    indexed_stories = {(row["c"], ALIASES.get(row["b"], row["b"]), row["s"])
                       for row in metadata}
    missing = indexed_stories - sw_stories
    if missing:
        failures.append(f"{len(missing)} indexed stories have no Swahili target: {sorted(missing)[:5]}")
    if len(sw_stories) != 1189 or indexed_stories != sw_stories:
        failures.append(f"story coverage differs: index={len(indexed_stories)}, Swahili={len(sw_stories)}")
    print(f"Checked {count} English index rows and {len(indexed_stories)} canonical "
          f"story routes into Swahili; failures: {len(failures)}")
    for failure in failures:
        print(failure)
    return int(bool(failures))


if __name__ == "__main__":
    raise SystemExit(main())
