#!/usr/bin/env python3
"""Audit Swahili Scripture assets and report unfinished study localization.

This is a structural and source-text audit, not a claim of translation quality.
Pass --onen and --suv to compare packaged Scripture with the pinned sources.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from import_sources import (  # noqa: E402
    BOOKS, build_files, check_hash, parse_onen, parse_suv, ONEN_SHA256, SUV_SHA256,
)
sys.path.insert(0, str(ROOT / "tools/prophecy"))
from audit_note_references import citations  # noqa: E402

COLLECTIONS = ("old_testament", "new_testament", "deuterocanonical", "apocrypha", "pseudepigrapha")
SCRIPTURE_FIELDS = ("id", "title", "refs", "summaryBullets", "headings", "superscription")
STUDY_LISTS = ("crossRefs", "translationNotes", "manuscriptVariants")
VERSE_MARKER = re.compile(r"\((\d+):(\d+)(?:[-\u2013](\d+))?\)[.\s]*$")
# These English Proverbs notes duplicate material in the cited chapters and
# describe verses outside the chapter where the source placed them. The Swahili
# reader omits only these five source mistakes, while keeping every other note.
PROVERBS_SOURCE_NOTE_OMISSIONS = {
    "proverbs-10": {0: "11:1"},
    "proverbs-16": {3: "18:10"},
    "proverbs-24": {0: "22:28", 1: "23:6-8", 2: "23:2"},
}
CHRONICLES_SOURCE_NOTE_OMISSIONS = {
    "2chronicles-17": {2: "Steadfast love", 3: "Valley of Beracah"},
    "2chronicles-18": {0: "*fear of God*", 2: "Steadfast love", 3: "Valley of Beracah"},
}


def same_variant_ref(english: str, swahili: str) -> bool:
    """Allow translated book labels while keeping variant verse identity."""
    if english == swahili:
        return True
    source = citations("en", english)
    native = citations("sw", swahili)
    def identity(ref):
        return (ref.collection, ref.book, ref.chapter, ref.verse,
                ref.end_chapter, ref.end_verse)
    return bool(source) and [identity(ref) for ref in source] == [identity(ref) for ref in native]


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def noncanon_slot_count(source: dict) -> int:
    count = int(bool(source.get("intro")))
    for story in source.get("stories", []):
        count += int(bool(story.get("keyTakeaway")))
        count += len(story.get("summaryBullets", []))
        count += len(story.get("crossRefs", []))
        count += sum(bool(item.get("note")) for item in story.get("translationNotes", []))
        count += sum(bool(item.get("text")) for item in story.get("manuscriptVariants", []))
    return count


def audit_noncanon_book(source: dict, native: dict, label: str) -> tuple[list[str], int, int]:
    """Check translated prose and structural parity without judging its meaning."""
    errors: list[str] = []
    translated = required = 0

    def prose(english: str, swahili: str, field: str) -> None:
        nonlocal translated, required
        if not english:
            return
        required += 1
        if isinstance(swahili, str) and swahili.strip() and swahili != english:
            translated += 1
        else:
            errors.append(f"Missing/untranslated {label}/{field}")

    if source.get("id") != native.get("id"):
        errors.append(f"Book id mismatch: {label}")
    prose(source.get("intro", ""), native.get("intro", ""), "intro")
    en_stories = source.get("stories", [])
    sw_stories = native.get("stories", [])
    if len(en_stories) != len(sw_stories):
        errors.append(f"Story count mismatch: {label}")
    for english, swahili in zip(en_stories, sw_stories):
        story_label = f"{label}/{english.get('id', '?')}"
        if english.get("id") != swahili.get("id"):
            errors.append(f"Story id mismatch: {story_label}")
        if len(english.get("refs", [])) != len(swahili.get("refs", [])):
            errors.append(f"Reference count mismatch: {story_label}")
        prose(english.get("keyTakeaway", ""), swahili.get("keyTakeaway", ""), "keyTakeaway")
        en_bullets = english.get("summaryBullets", [])
        sw_bullets = swahili.get("summaryBullets", [])
        if len(en_bullets) != len(sw_bullets):
            errors.append(f"Scripture bullet count mismatch: {story_label}")
        for index, (en_bullet, sw_bullet) in enumerate(zip(en_bullets, sw_bullets)):
            prose(en_bullet, sw_bullet, f"summaryBullets[{index}]")
            en_marker = VERSE_MARKER.search(en_bullet)
            sw_marker = VERSE_MARKER.search(sw_bullet)
            if (en_marker.group(0) if en_marker else None) != (sw_marker.group(0) if sw_marker else None):
                errors.append(f"Verse marker mismatch: {story_label}/summaryBullets[{index}]")
        for field in STUDY_LISTS:
            source_list = english.get(field, [])
            native_list = swahili.get(field, [])
            if len(source_list) != len(native_list):
                errors.append(f"{field} count mismatch: {story_label}")
            for index, (en_item, sw_item) in enumerate(zip(source_list, native_list)):
                item_label = f"{field}[{index}]"
                if isinstance(en_item, str):
                    prose(en_item, sw_item, item_label)
                elif isinstance(en_item, dict) and isinstance(sw_item, dict):
                    if set(en_item) != set(sw_item):
                        errors.append(f"{item_label} schema mismatch: {story_label}")
                    if field == "translationNotes":
                        prose(en_item.get("note", ""), sw_item.get("note", ""), f"{item_label}/note")
                        for key in ("term", "original"):
                            if en_item.get(key) and not sw_item.get(key):
                                errors.append(f"Missing {story_label}/{item_label}/{key}")
                    elif field == "manuscriptVariants":
                        if not same_variant_ref(en_item.get("ref", ""), sw_item.get("ref", "")):
                            errors.append(f"Variant ref mismatch: {story_label}/{item_label}")
                        prose(en_item.get("text", ""), sw_item.get("text", ""), f"{item_label}/text")
                else:
                    errors.append(f"{item_label} type mismatch: {story_label}")
    return errors, translated, required


def audit(assets: Path, onen: Path | None, suv: Path | None) -> tuple[list[str], dict]:
    errors: list[str] = []
    metrics = {"canonBooks": 0, "canonChapters": 0, "baseVerseUnits": 0,
               "suvVerseUnits": 0, "translatedStudySlots": 0, "requiredStudySlots": 0,
               "translatedNotesFiles": 0, "requiredNotesFiles": 0,
               "presentNoncanonBooks": 0, "completeNoncanonBooks": 0,
               "requiredNoncanonBooks": 0, "translatedNoncanonSlots": 0,
               "requiredNoncanonSlots": 0}
    expected_files = None
    if onen is not None and suv is not None:
        onen_hash = check_hash(onen, ONEN_SHA256)
        suv_hash = check_hash(suv, SUV_SHA256)
        parsed_onen, names, headings, hashes = parse_onen(onen)
        parsed_suv = parse_suv(suv)
        expected_files, _ = build_files(parsed_onen, names, headings, hashes,
                                        parsed_suv, onen_hash, suv_hash)

    for collection in ("old_testament", "new_testament"):
        source_index = read_json(assets / "books" / collection / "en/_index.json")
        native_index_path = assets / "books" / collection / "sw/_index.json"
        if not native_index_path.exists():
            errors.append(f"Missing {native_index_path}")
            continue
        native_index = read_json(native_index_path)
        if [row[0] for row in native_index] != [row[0] for row in source_index]:
            errors.append(f"Book index differs: {collection}")
        for book_id, _ in source_index:
            en_path = assets / "books" / collection / "en" / f"{book_id}.json"
            sw_path = assets / "books" / collection / "sw" / f"{book_id}.json"
            alt_path = assets / "books/editions/sw/suv1952" / collection / f"{book_id}.json"
            if not sw_path.exists() or not alt_path.exists():
                errors.append(f"Missing Swahili JSON: {collection}/{book_id}")
                continue
            en, sw, alt = read_json(en_path), read_json(sw_path), read_json(alt_path)
            metrics["canonBooks"] += 1
            if sw.get("id") != book_id or len(sw.get("stories", [])) != len(en["stories"]):
                errors.append(f"Book/story inventory mismatch: {collection}/{book_id}")
                continue
            if (alt.get("schemaVersion"), alt.get("editionId"), alt.get("language"),
                alt.get("collection"), alt.get("bookId"), alt.get("coverage")) != (
                    2, "suv1952", "sw", collection, book_id, "full"):
                errors.append(f"SUV overlay identity mismatch: {collection}/{book_id}")
            if len(alt.get("chapters", [])) != len(sw["stories"]):
                errors.append(f"SUV chapter count mismatch: {collection}/{book_id}")
            if expected_files is not None:
                relative = sw_path.relative_to(assets)
                expected = json.loads(expected_files[relative])
                for key in ("id", "title"):
                    if sw.get(key) != expected[key]:
                        errors.append(f"Source field changed: {collection}/{book_id}/{key}")
                for actual_story, source_story in zip(sw["stories"], expected["stories"]):
                    for key in SCRIPTURE_FIELDS:
                        if actual_story.get(key, "") != source_story.get(key, ""):
                            errors.append(f"Source Scripture field changed: {actual_story.get('id')}/{key}")
                alt_relative = alt_path.relative_to(assets)
                if alt != json.loads(expected_files[alt_relative]):
                    errors.append(f"SUV source text changed: {collection}/{book_id}")
            for english_story, native_story in zip(en["stories"], sw["stories"]):
                metrics["canonChapters"] += 1
                metrics["baseVerseUnits"] += len(native_story.get("summaryBullets", []))
                if native_story.get("id") != english_story["id"]:
                    errors.append(f"Story id mismatch: {collection}/{book_id}")
                for field in ("keyTakeaway",):
                    if english_story.get(field):
                        metrics["requiredStudySlots"] += 1
                        value = native_story.get(field, "")
                        if value and value != english_story[field]:
                            metrics["translatedStudySlots"] += 1
                        else:
                            errors.append(f"Missing/untranslated {field}: {native_story['id']}")
                for field in STUDY_LISTS:
                    source_list = english_story.get(field, [])
                    if field == "translationNotes" and collection == "old_testament" and book_id == "proverbs":
                        omitted = PROVERBS_SOURCE_NOTE_OMISSIONS.get(english_story["id"], {})
                        for index, wrong_chapter_ref in omitted.items():
                            if index >= len(source_list) or wrong_chapter_ref not in source_list[index].get("note", ""):
                                errors.append(f"Proverbs source note exception changed: {english_story['id']}/{index}")
                        source_list = [item for index, item in enumerate(source_list) if index not in omitted]
                    if field == "translationNotes" and collection == "old_testament" and book_id == "2_chronicles":
                        omitted = CHRONICLES_SOURCE_NOTE_OMISSIONS.get(english_story["id"], {})
                        chapter_twenty_notes = en["stories"][19].get("translationNotes", [])
                        for index, term in omitted.items():
                            if (index >= len(source_list) or source_list[index].get("term") != term or
                                    source_list[index] not in chapter_twenty_notes):
                                errors.append(f"Chronicles source note exception changed: {english_story['id']}/{index}")
                        source_list = [item for index, item in enumerate(source_list) if index not in omitted]
                    metrics["requiredStudySlots"] += len(source_list)
                    native_list = native_story.get(field, [])
                    if len(native_list) != len(source_list):
                        errors.append(f"{field} count mismatch: {native_story['id']}")
                    for source_item, native_item in zip(source_list, native_list):
                        if isinstance(source_item, str):
                            translated = isinstance(native_item, str) and bool(native_item.strip()) and native_item != source_item
                        elif isinstance(source_item, dict) and isinstance(native_item, dict):
                            if field == "translationNotes":
                                translated = bool(native_item.get("term")) and (
                                    not source_item.get("original") or bool(native_item.get("original"))) and (
                                    bool(native_item.get("note")) and native_item.get("note") != source_item.get("note"))
                            else:
                                translated = (same_variant_ref(source_item.get("ref", ""), native_item.get("ref", "")) and
                                              bool(native_item.get("text")) and native_item.get("text") != source_item.get("text"))
                        else:
                            translated = False
                        if translated:
                            metrics["translatedStudySlots"] += 1
                        else:
                            errors.append(f"Untranslated {field} item: {native_story['id']}")
                if native_story.get("headings") is None:
                    errors.append(f"Missing heading table: {native_story['id']}")
            if en.get("intro"):
                metrics["requiredStudySlots"] += 1
                if sw.get("intro") and sw["intro"] != en["intro"]:
                    metrics["translatedStudySlots"] += 1
                else:
                    errors.append(f"Missing/untranslated intro: {collection}/{book_id}")
            for chapter in alt.get("chapters", []):
                if chapter.get("number") != alt["chapters"].index(chapter) + 1:
                    errors.append(f"SUV chapter gap: {collection}/{book_id}")
                verses = chapter.get("verses", [])
                metrics["suvVerseUnits"] += len(verses)
                if (chapter.get("lastVerse") != (verses[-1]["verse"] if verses else 0) or
                        chapter.get("verseUnitCount") != len(verses) or
                        chapter.get("headings") is None):
                    errors.append(f"SUV chapter metadata mismatch: {collection}/{book_id}/{chapter.get('number')}")

    for collection in COLLECTIONS[2:]:
        source_dir = assets / "books" / collection / "en"
        native_dir = assets / "books" / collection / "sw"
        for path in source_dir.glob("*.json"):
            metrics["requiredNoncanonBooks"] += 1
            source = read_json(path)
            if path.name != "_index.json":
                metrics["requiredNoncanonSlots"] += noncanon_slot_count(source)
            native_path = native_dir / path.name
            if not native_path.exists():
                errors.append(f"Missing noncanon translation: {collection}/{path.name}")
                continue
            metrics["presentNoncanonBooks"] += 1
            if path.name == "_index.json":
                source_index, native_index = read_json(path), read_json(native_path)
                if [row[0] for row in source_index] != [row[0] for row in native_index]:
                    errors.append(f"Noncanon index id mismatch: {collection}")
                elif any(not isinstance(row[1], str) or not row[1].strip() for row in native_index):
                    errors.append(f"Noncanon index has empty title: {collection}")
                else:
                    metrics["completeNoncanonBooks"] += 1
                continue
            book_errors, translated, required = audit_noncanon_book(
                source, read_json(native_path), f"{collection}/{path.stem}")
            metrics["translatedNoncanonSlots"] += translated
            errors.extend(book_errors)
            if not book_errors:
                metrics["completeNoncanonBooks"] += 1
    for path in (assets / "notes/en").glob("*.md"):
        metrics["requiredNotesFiles"] += 1
        native = assets / "notes/sw" / path.name
        if native.exists() and native.stat().st_size > 0:
            metrics["translatedNotesFiles"] += 1
        else:
            errors.append(f"Missing study note: {path.name}")
    for relative in ("refs/sw/book_aliases.json", "daily_verses/sw/daily.json",
                     "daily_verses/sw/feasts.json", "notifications/sw.json"):
        if not (assets / relative).exists():
            errors.append(f"Missing localization asset: {relative}")
    return errors, metrics


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, default=ROOT / "shared/assets")
    parser.add_argument("--onen", type=Path)
    parser.add_argument("--suv", type=Path)
    args = parser.parse_args()
    if (args.onen is None) != (args.suv is None):
        parser.error("--onen and --suv must be supplied together")
    errors, metrics = audit(args.assets, args.onen, args.suv)
    print(json.dumps({"metrics": metrics, "errorCount": len(errors), "firstErrors": errors[:25]},
                     indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
