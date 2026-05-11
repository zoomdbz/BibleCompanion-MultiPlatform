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

APP_ASSETS = Path(__file__).parent.parent / "BibleCompanion-MultiPlatform" / "shared" / "assets" / "books"

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

    book_id = data.get("id", filepath.stem)
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
    print()

    for lang in langs:
        entries = parse_language(lang)
        bullets = sum(1 for e in entries if e["type"] == "bullet")
        takeaways = sum(1 for e in entries if e["type"] == "takeaway")
        crossrefs = sum(1 for e in entries if e["type"] == "crossref")
        outpath = write_jsonl(entries, lang)
        print(f"  {lang}: {len(entries)} entries ({bullets} bullets, {takeaways} takeaways, {crossrefs} crossrefs) -> {outpath.name}")

    print()
    print("Done. Corpus files in output/")


if __name__ == "__main__":
    main()
