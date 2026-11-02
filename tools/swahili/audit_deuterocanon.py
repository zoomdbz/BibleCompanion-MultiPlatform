#!/usr/bin/env python3
"""Check the German, Korean, Hindi, and Swahili deuterocanon assets.

This checks coverage, book/story structure, verse markers, original-language
glyphs, and whether each cross-reference resolves in that language's bundled
Bible. It cannot certify the meaning or naturalness of a translation; those
need a separate bilingual editorial review.
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
sys.path.insert(0, str(ROOT / "tools/prophecy"))
from audit_note_references import citations, validate  # noqa: E402

LANGUAGES = ("de", "ko", "hi", "sw")
MARKER = re.compile(r"\((?:(\d+):)?(\d+)(?:\s*[-\u2013]\s*(\d+))?\)[.\u3002]*$")
ORIGINAL_SCRIPT = re.compile(r"[\u0370-\u03ff\u1f00-\u1fff\u0590-\u05ff]+")


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def audit_language(language: str) -> dict:
    source_dir = ASSETS / "books/deuterocanonical/en"
    native_dir = ASSETS / "books/deuterocanonical" / language
    errors: list[str] = []
    review: list[str] = []
    books = stories = bullets = editorial = citations_checked = 0
    source_files = sorted(p for p in source_dir.glob("*.json") if p.stem != "_index")
    expected_names = {p.name for p in source_files} | {"_index.json"}
    actual_names = {p.name for p in native_dir.glob("*.json")}
    for missing in sorted(expected_names - actual_names):
        errors.append(f"missing book: {missing}")
    for extra in sorted(actual_names - expected_names):
        review.append(f"extra book: {extra}")

    if (native_dir / "_index.json").exists():
        english_index = load(source_dir / "_index.json")
        native_index = load(native_dir / "_index.json")
        if [row[0] for row in english_index] != [row[0] for row in native_index]:
            errors.append("book index IDs/order differ from English app inventory")
        if any(not isinstance(row[1], str) or not row[1].strip() for row in native_index):
            errors.append("book index has a blank title")

    def translated(en: str, native: str, label: str) -> None:
        nonlocal editorial
        if not en:
            return
        editorial += 1
        if not isinstance(native, str) or not native.strip():
            errors.append(f"blank text: {label}")
        elif native == en and len(en) >= 40:
            errors.append(f"English text unchanged: {label}")
        elif "\ufffd" in native or "\x00" in native:
            errors.append(f"invalid character in text: {label}")

    for source_path in source_files:
        native_path = native_dir / source_path.name
        if not native_path.exists():
            continue
        source, native = load(source_path), load(native_path)
        books += 1
        book = source_path.stem
        if native.get("id") != source.get("id"):
            errors.append(f"book ID differs: {book}")
        translated(source.get("intro", ""), native.get("intro", ""), f"{book}/intro")
        source_stories, native_stories = source.get("stories", []), native.get("stories", [])
        if len(source_stories) != len(native_stories):
            errors.append(f"story count differs: {book}: {len(source_stories)} vs {len(native_stories)}")
        if book == "sirach" and len(native_stories) > 1:
            prologue = " ".join(native_stories[0].get("summaryBullets", []))
            chapter_one = native_stories[1].get("summaryBullets", [])
            first_verses = " ".join(chapter_one[:8])
            # Earlier German and Korean imports copied the prologue into 1:1,
            # and Korean then shifted every chapter-one verse by eight places.
            if chapter_one and len(chapter_one[0]) > 500:
                errors.append("Sirach 1:1 contains an oversized prologue-like paragraph")
            if difflib.SequenceMatcher(None, prologue, first_verses).ratio() > 0.30:
                errors.append("Sirach prologue is repeated in chapter one")
        for source_story, native_story in zip(source_stories, native_stories):
            stories += 1
            story = source_story["id"]
            if native_story.get("id") != story:
                errors.append(f"story ID differs: {book}/{story}")
            if len(native_story.get("refs", [])) != len(source_story.get("refs", [])):
                errors.append(f"reference count differs: {book}/{story}")
            translated(source_story.get("keyTakeaway", ""), native_story.get("keyTakeaway", ""),
                       f"{book}/{story}/keyTakeaway")

            native_bullets = native_story.get("summaryBullets", [])
            if not native_bullets and source_story.get("summaryBullets"):
                errors.append(f"no Scripture bullets: {book}/{story}")
            if language == "sw" and len(native_bullets) != len(source_story.get("summaryBullets", [])):
                errors.append(f"Swahili bullet count differs: {book}/{story}")
            previous_end = 0
            marked_chapter = None
            for index, bullet in enumerate(native_bullets):
                bullets += 1
                label = f"{book}/{story}/summaryBullets[{index}]"
                if not isinstance(bullet, str) or not bullet.strip():
                    errors.append(f"blank Scripture bullet: {label}")
                    continue
                if story.endswith("prologue"):
                    if not re.search(r"\([^)]+\)[.\u3002]*$", bullet):
                        errors.append(f"missing prologue marker: {label}")
                    continue
                marker = MARKER.search(bullet.strip())
                if marker is None:
                    errors.append(f"missing verse marker: {label}")
                    continue
                chapter, first, end = marker.groups()
                first = int(first)
                end = int(end) if end else first
                # Greek Esther has an app story grid that deliberately differs
                # from its printed chapter numbers. Check consistency within
                # the story, not equality with the story ID suffix.
                if chapter is not None:
                    if marked_chapter is None:
                        marked_chapter = int(chapter)
                        # Greek Esther is the sole intentional exception: its
                        # source additions use a 21-story app grid with
                        # different printed chapter labels.
                        story_number = story.rsplit("-", 1)[-1]
                        if (book != "esther_greek" and story_number.isdigit()
                                and marked_chapter != int(story_number)):
                            errors.append(f"verse chapter cannot load in story grid: {book}/{story}")
                    elif int(chapter) != marked_chapter:
                        errors.append(f"mixed printed chapter markers: {label}")
                if first <= previous_end or end < first:
                    errors.append(f"duplicate or out-of-order verse marker: {label}")
                previous_end = end

            for field in ("crossRefs", "translationNotes", "manuscriptVariants"):
                source_items = source_story.get(field, [])
                native_items = native_story.get(field, [])
                if len(source_items) != len(native_items):
                    errors.append(f"{field} count differs: {book}/{story}")
                for index, (source_item, native_item) in enumerate(zip(source_items, native_items)):
                    label = f"{book}/{story}/{field}[{index}]"
                    if field == "crossRefs":
                        translated(source_item, native_item, label)
                        if not isinstance(native_item, str):
                            continue
                        refs = citations(language, native_item)
                        if len(refs) != 1:
                            errors.append(f"expected one linked citation, found {len(refs)}: {label}")
                        for ref in refs:
                            citations_checked += 1
                            for problem in validate(language, ref):
                                errors.append(f"citation {ref.display}: {problem}: {label}")
                    elif not isinstance(source_item, dict) or not isinstance(native_item, dict):
                        errors.append(f"note type differs: {label}")
                    elif field == "translationNotes":
                        translated(source_item.get("note", ""), native_item.get("note", ""), f"{label}/note")
                        if set(source_item) != set(native_item):
                            errors.append(f"note schema differs: {label}")
                        if source_item.get("term") and not native_item.get("term"):
                            errors.append(f"missing note term: {label}")
                        if ORIGINAL_SCRIPT.findall(source_item.get("original", "")) != ORIGINAL_SCRIPT.findall(native_item.get("original", "")):
                            errors.append(f"original-script glyphs differ: {label}")
                    else:
                        if source_item.get("ref") != native_item.get("ref"):
                            errors.append(f"variant reference differs: {label}")
                        translated(source_item.get("text", ""), native_item.get("text", ""), f"{label}/text")

            if language == "sw":
                for index, (en, native_bullet) in enumerate(zip(source_story.get("summaryBullets", []), native_bullets)):
                    translated(en, native_bullet, f"{book}/{story}/summaryBullets[{index}]")
                    en_marker = re.search(r"\([^)]+\)[.\u3002]*$", en)
                    sw_marker = re.search(r"\([^)]+\)[.\u3002]*$", native_bullet)
                    if (en_marker.group(0) if en_marker else None) != (sw_marker.group(0) if sw_marker else None):
                        errors.append(f"Swahili verse marker differs from source: {book}/{story}/summaryBullets[{index}]")
    return {"language": language, "books": books, "stories": stories, "scriptureBullets": bullets,
            "editorialSlotsChecked": editorial, "crossReferencesResolved": citations_checked,
            "errors": errors, "reviewFlags": review}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", choices=LANGUAGES, action="append")
    args = parser.parse_args()
    results = [audit_language(language) for language in args.language or LANGUAGES]
    for result in results:
        print(f"{result['language']}: {result['books']} books, {result['stories']} stories, "
              f"{result['scriptureBullets']} bullets, {result['editorialSlotsChecked']} editorial slots, "
              f"{result['crossReferencesResolved']} linked citations; "
              f"{len(result['errors'])} errors, {len(result['reviewFlags'])} review flags")
        for issue in result["errors"][:25]:
            print(f"  ERROR {issue}")
        if len(result["errors"]) > 25:
            print(f"  ... {len(result['errors']) - 25} more errors")
        for issue in result["reviewFlags"][:25]:
            print(f"  REVIEW {issue}")
    return int(any(result["errors"] for result in results))


if __name__ == "__main__":
    raise SystemExit(main())
