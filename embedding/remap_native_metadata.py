#!/usr/bin/env python3
"""Patch navigation metadata only; never touch the precomputed vector rows.

The two metadata copies must be byte-identical before this script writes them.
The output retains every row in the same order, so each vector still describes
its original text. Ambiguous chapter-wide rows for split Russian Psalms are
disabled, not assigned to a guessed chapter.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from native_versification import russian_psalm_chapters, russian_psalm_verse

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "embedding" / "output"
PACKAGED = ROOT / "embedding-assets" / "src" / "main" / "assets" / "embedding"
BOOKS = ROOT / "shared" / "assets" / "books" / "old_testament"
DIM = 384
VERSE = re.compile(r"^(\d+):(\d+)$")
REF_RANGE = re.compile(r"^Psalms (\d+):(\d+)-(\d+)$")


def source_refs(lang: str, book: str) -> dict[str, list[str]]:
    data = json.loads((BOOKS / lang / f"{book}.json").read_text(encoding="utf-8"))
    return {story["id"]: story["refs"] for story in data["stories"]}


def remap_russian_row(row: dict, native_refs: dict[str, list[str]]) -> dict:
    if row.get("b") != "psalms":
        return row.copy()
    if row.get("n") == 1:
        return row.copy()
    old_id = row["s"]
    old_chapter = int(old_id.removeprefix("psalms-"))
    if old_id != f"psalms-{old_chapter}":
        raise ValueError(f"Unexpected Russian Psalm story ID: {old_id}")
    out = row.copy()
    out["n"] = 1  # Explicit native-numbering marker checked by the reader.
    verse_ref = row.get("v")
    if verse_ref:
        match = VERSE.fullmatch(verse_ref)
        if not match or int(match[1]) != old_chapter:
            raise ValueError(f"Unexpected Russian Psalm verse reference: {row}")
        chapter, verse = russian_psalm_verse(old_chapter, int(match[2]))
        out["v"] = f"{chapter}:{verse}"
    else:
        chapters = russian_psalm_chapters(old_chapter)
        if len(chapters) != 1:
            # A whole-story embedding for MT Psalm 116/147 describes two NRT
            # chapters. There is no safe single-chapter navigation target.
            out["s"] = ""
            out["v"] = None
            out["r"] = []
            return out
        chapter = chapters[0]
        verse = None
    story_id = f"psalms-{chapter}"
    refs = native_refs.get(story_id)
    if not refs or len(refs) != 1:
        raise ValueError(f"Missing native Russian Psalm reference: {story_id}")
    if verse is not None:
        match = REF_RANGE.fullmatch(refs[0])
        if not match or int(match[1]) != chapter or not int(match[2]) <= verse <= int(match[3]):
            raise ValueError(f"Mapped verse outside native Psalm range: {out['v']} / {refs}")
    out["s"] = story_id
    out["r"] = refs
    return out


def remap_german_row(row: dict, native_refs: dict[str, list[str]]) -> dict:
    out = row.copy()
    if row.get("b") == "malachi" and row.get("s") == "malachi-4":
        out["s"] = "malachi-3"
        out["r"] = native_refs["malachi-3"]
    return out


def validate_vector_count(lang: str, row_count: int) -> None:
    for directory in (OUTPUT, PACKAGED):
        data = (directory / f"embeddings_{lang}.bin").read_bytes()
        if data[:4] != b"BCEF" or len(data) < 24:
            raise ValueError(f"Invalid vector header: {directory} / {lang}")
        count = int.from_bytes(data[8:12], "little")
        dim = int.from_bytes(data[12:16], "little")
        if count != row_count or dim != DIM or len(data) != 24 + count * dim:
            raise ValueError(f"Vector/metadata shape mismatch: {directory} / {lang}")
    if (OUTPUT / f"embeddings_{lang}.bin").read_bytes() != (
        PACKAGED / f"embeddings_{lang}.bin"
    ).read_bytes():
        raise ValueError(f"Packaged and source vectors differ: {lang}")


def process(lang: str) -> tuple[list[Path], bytes | None, int]:
    paths = [directory / f"metadata_{lang}.json" for directory in (OUTPUT, PACKAGED)]
    before = [path.read_bytes() for path in paths]
    if before[0] != before[1]:
        raise ValueError(f"Packaged and source metadata differ: {lang}")
    rows = json.loads(before[0])
    validate_vector_count(lang, len(rows))
    if lang == "ru":
        refs = source_refs(lang, "psalms")
        marked = [row.get("n") == 1 for row in rows if row.get("b") == "psalms"]
        if any(marked) and not all(marked):
            raise ValueError("Partially remapped Russian Psalm metadata")
        out = [remap_russian_row(row, refs) for row in rows]
        if not all(row.get("n") == 1 for row in out if row.get("b") == "psalms"):
            raise ValueError("Russian Psalm metadata lacks native marker")
        disabled = sum(row.get("b") == "psalms" and not row.get("s") for row in out)
        if disabled != 4:
            raise ValueError(f"Expected four ambiguous Russian split-Psalm rows, got {disabled}")
    elif lang == "de":
        refs = source_refs(lang, "malachi")
        out = [remap_german_row(row, refs) for row in rows]
        if any(row.get("b") == "malachi" and row.get("s") == "malachi-4" for row in out):
            raise ValueError("German Malachi 4 still present")
    else:
        raise ValueError(lang)
    if len(out) != len(rows):
        raise ValueError("Metadata row count changed")
    changed = sum(a != b for a, b in zip(rows, out))
    print(f"{lang}: {len(rows)} vector rows, {changed} metadata rows changed")
    encoded = json.dumps(out, ensure_ascii=False, separators=(",", ":")).encode("utf-8") if changed else None
    return paths, encoded, changed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    # Validate both complete outputs before writing either copy.
    plans = [process(lang) for lang in ("ru", "de")]
    if not args.apply and any(changed for _, _, changed in plans):
        raise ValueError("Metadata needs --apply")
    if args.apply:
        for paths, encoded, _ in plans:
            if encoded is not None:
                for path in paths:
                    path.write_bytes(encoded)


if __name__ == "__main__":
    main()
