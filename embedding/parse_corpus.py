#!/usr/bin/env python3
"""
Step 1: Parse Bible Companion JSON book files into corpus JSONL.

Reads shared/assets/books/{collection}/{lang}/*.json and extracts
summaryBullets with metadata (book, story, chapter:verse refs).
Also extracts keyTakeaway and crossRefs as additional searchable text.

Output: output/corpus_{lang}.jsonl
"""

import json
import os
import re
import sys
from pathlib import Path

# Resolve the books directory for both layouts:
#   embedding/ inside the project (current canonical layout)
#   embedding/ next to a separate BibleCompanion-MultiPlatform/ clone (legacy)
def _resolve_app_assets() -> Path:
    here = Path(__file__).parent
    # Layout A: embedding/ is inside the project root.
    inside = here.parent / "shared" / "assets" / "books"
    if inside.is_dir():
        return inside
    # Layout B: embedding/ is a sibling of BibleCompanion-MultiPlatform/.
    sibling = here.parent / "BibleCompanion-MultiPlatform" / "shared" / "assets" / "books"
    if sibling.is_dir():
        return sibling
    # Fall back to the in-project layout so the error message is informative.
    return inside

APP_ASSETS = _resolve_app_assets()

COLLECTIONS = ["old_testament", "new_testament", "apocrypha", "deuterocanonical", "pseudepigrapha"]

LANGUAGES = ["en", "ar", "de", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh-Hans", "zh-Hant"]

OUTPUT_DIR = Path(__file__).parent / "output"


def extract_verse_ref(bullet_text: str) -> str | None:
    """Pull the (chapter:verse) annotation from the end of a bullet."""
    m = re.search(r'\((\d+:\d+(?:-\d+(?::\d+)?)?)\)\s*\.?\s*$', bullet_text)
    return m.group(1) if m else None


def clean_bullet(text: str) -> str:
    """Strip the trailing verse ref annotation for embedding purposes."""
    cleaned = re.sub(r'\s*\(\d+:\d+(?:-\d+(?::\d+)?)?\)\s*\.?\s*$', '', text)
    return cleaned.strip()


def parse_book(filepath: Path, collection: str, lang: str) -> list[dict]:
    """Parse a single book JSON file into corpus entries."""
    with open(filepath, encoding="utf-8") as f:
        data = json.load(f)

    if isinstance(data, list):
        return []

    # Use the file stem as the book id, not the JSON's internal `id` field.
    # Runtime navigation loads books by filename (`books/<col>/<lang>/<stem>.json`),
    # so embedding metadata must carry the stem or semantic-only hits route to
    # missing books. Some legacy JSONs have ids like "1-corinthians" while the
    # file is `1_corinthians.json` — those mismatches break navigation.
    book_id = filepath.stem
    internal_id = data.get("id", book_id)
    if internal_id != book_id:
        print(f"  WARN: {filepath.name} internal id={internal_id!r} != stem; using stem", file=sys.stderr)
    book_title = data.get("title", book_id)
    entries = []

    for story in data.get("stories", []):
        story_id = story.get("id", "")
        story_title = story.get("title", "")
        refs = story.get("refs", [])

        for i, bullet in enumerate(story.get("summaryBullets", [])):
            if not bullet or not bullet.strip():
                continue
            verse_ref = extract_verse_ref(bullet)
            cleaned = clean_bullet(bullet)
            if not cleaned:
                continue
            entries.append({
                "type": "bullet",
                "collection": collection,
                "book_id": book_id,
                "book_title": book_title,
                "story_id": story_id,
                "story_title": story_title,
                "bullet_index": i,
                "text": cleaned,
                "verse_ref": verse_ref,
                "refs": refs,
                "lang": lang,
            })

        takeaway = story.get("keyTakeaway", "")
        if takeaway and takeaway.strip():
            entries.append({
                "type": "takeaway",
                "collection": collection,
                "book_id": book_id,
                "book_title": book_title,
                "story_id": story_id,
                "story_title": story_title,
                "bullet_index": -1,
                "text": takeaway.strip(),
                "verse_ref": None,
                "refs": refs,
                "lang": lang,
            })

        for cr in story.get("crossRefs", []):
            if not cr or not cr.strip():
                continue
            entries.append({
                "type": "crossref",
                "collection": collection,
                "book_id": book_id,
                "book_title": book_title,
                "story_id": story_id,
                "story_title": story_title,
                "bullet_index": -1,
                "text": cr.strip(),
                "verse_ref": None,
                "refs": refs,
                "lang": lang,
            })

    return entries


def parse_language(lang: str) -> list[dict]:
    """Parse all books for a single language."""
    all_entries = []
    for col in COLLECTIONS:
        lang_dir = APP_ASSETS / col / lang
        if not lang_dir.is_dir():
            continue
        for book_file in sorted(lang_dir.glob("*.json")):
            if book_file.name.startswith("_"):
                continue
            entries = parse_book(book_file, col, lang)
            all_entries.extend(entries)
    return all_entries


def write_jsonl(entries: list[dict], lang: str):
    """Write entries to output/corpus_{lang}.jsonl."""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    outpath = OUTPUT_DIR / f"corpus_{lang}.jsonl"
    with open(outpath, "w", encoding="utf-8") as f:
        for entry in entries:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")
    return outpath


def main():
    langs = sys.argv[1:] if len(sys.argv) > 1 else LANGUAGES
    print(f"Parsing {len(langs)} language(s): {', '.join(langs)}")
    print(f"Source: {APP_ASSETS}")
    if not APP_ASSETS.is_dir():
        print(f"  ERROR: source directory does not exist: {APP_ASSETS}", file=sys.stderr)
        sys.exit(1)
    print()

    failed: list[str] = []
    for lang in langs:
        entries = parse_language(lang)
        bullets = sum(1 for e in entries if e["type"] == "bullet")
        takeaways = sum(1 for e in entries if e["type"] == "takeaway")
        crossrefs = sum(1 for e in entries if e["type"] == "crossref")
        outpath = write_jsonl(entries, lang)
        print(f"  {lang}: {len(entries)} entries ({bullets} bullets, {takeaways} takeaways, {crossrefs} crossrefs) -> {outpath.name}")
        if len(entries) == 0:
            failed.append(lang)

    print()
    if failed:
        print(f"ERROR: 0-entry corpora for: {', '.join(failed)}", file=sys.stderr)
        print("Refusing to leave empty corpus files — fix the source path or assets and rerun.", file=sys.stderr)
        sys.exit(2)
    print("Done. Corpus files in output/")


if __name__ == "__main__":
    main()
