#!/usr/bin/env python3
"""Validate committed KJV 1769 overlays and their pinned USFM source."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

from import_kjv_usfm import (
    ARCHIVE_SHA256,
    BOOKS,
    EDITION_ID,
    FALLBACKS,
    SCHEMA_VERSION,
    TRADITIONAL_NT_VERSES,
    find_source_files,
    parse_usfm,
    select_book_chapters,
    sha256,
    source_file_set_sha256,
)


class ValidationError(AssertionError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def strip_markup(text: str) -> str:
    return (
        text.replace("[J]", "")
        .replace("[/J]", "")
        .replace("[ADD]", "")
        .replace("[/ADD]", "")
    )


def validate_tag_pair(text: str, opening: str, closing: str, reference: str) -> None:
    depth = 0
    pattern = re.compile(f"({re.escape(opening)}|{re.escape(closing)})")
    for match in pattern.finditer(text):
        if match.group(0) == opening:
            depth += 1
            require(depth == 1, f"Nested {opening} at {reference}")
        else:
            depth -= 1
            require(depth >= 0, f"Unmatched {closing} at {reference}")
    require(depth == 0, f"Unclosed {opening} at {reference}")


def flattened(chapters: list[dict]) -> dict[tuple[int, int], dict]:
    result = {}
    last_chapter = 0
    for chapter in chapters:
        number = chapter["number"]
        require(isinstance(number, int) and number > last_chapter, "Chapters must be strictly increasing")
        last_chapter = number
        last_verse = 0
        for verse in chapter["verses"]:
            require(verse["chapter"] == number, f"Chapter mismatch at {number}:{verse.get('verse')}")
            verse_number = verse["verse"]
            require(isinstance(verse_number, int) and verse_number > last_verse, f"Verses not increasing in chapter {number}")
            last_verse = verse_number
            key = number, verse_number
            require(key not in result, f"Duplicate verse {number}:{verse_number}")
            require(strip_markup(verse["text"]).strip(), f"Empty verse {number}:{verse_number}")
            validate_tag_pair(verse["text"], "[J]", "[/J]", f"{number}:{verse_number}")
            validate_tag_pair(verse["text"], "[ADD]", "[/ADD]", f"{number}:{verse_number}")
            require("\\" not in verse["text"], f"Leaked USFM marker at {number}:{verse_number}")
            require("|strong=" not in verse["text"], f"Leaked Strong's data at {number}:{verse_number}")
            result[key] = verse
        if "superscription" in chapter:
            text = chapter["superscription"]
            validate_tag_pair(text, "[J]", "[/J]", f"superscription {number}")
            validate_tag_pair(text, "[ADD]", "[/ADD]", f"superscription {number}")
            require("\\" not in text, f"Leaked USFM marker in superscription {number}")
    return result


def normalized_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def validate(source_dir: Path, output_dir: Path, archive: Path | None) -> dict:
    manifest_path = output_dir / "_manifest.json"
    require(manifest_path.is_file(), f"Missing manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(manifest["schemaVersion"] == SCHEMA_VERSION, "Manifest schema version mismatch")
    require(manifest["editionId"] == EDITION_ID, "Manifest edition mismatch")
    require(manifest["source"]["archiveSha256"] == ARCHIVE_SHA256, "Pinned archive hash mismatch in manifest")
    if archive is not None:
        require(archive.is_file(), f"Archive not found: {archive}")
        require(sha256(archive) == ARCHIVE_SHA256, "Original archive SHA-256 mismatch")

    source_files = find_source_files(source_dir)
    required_codes = {code for code, _collection, _book_id in BOOKS} | {"ESG"}
    require(set(source_files) == required_codes, "Pinned source file inventory mismatch")
    require(
        manifest["source"]["extractedScriptureFileSetSha256"]
        == source_file_set_sha256(source_files, required_codes),
        "Extracted source file-set SHA-256 mismatch",
    )
    expected_outputs = {(collection, book_id): code for code, collection, book_id in BOOKS}
    manifest_by_book = {(item["collection"], item["bookId"]): item for item in manifest["books"]}
    expected_all = set(expected_outputs) | {("deuterocanonical", book_id) for book_id, _ in FALLBACKS}
    require(set(manifest_by_book) == expected_all, "Manifest book inventory mismatch")
    expected_paths = {
        output_dir / collection / f"{book_id}.json"
        for collection, book_id in expected_outputs
    } | {manifest_path}
    actual_paths = set(output_dir.rglob("*.json"))
    require(actual_paths == expected_paths, "Generated JSON file inventory mismatch")

    report = {
        "editionId": EDITION_ID,
        "overlayBooks": 0,
        "fallbackBooks": 0,
        "chapters": 0,
        "verses": 0,
        "sourceFootnotes": 0,
        "jesusWordSpans": 0,
        "translatorAdditionSpans": 0,
        "superscriptions": 0,
        "traditionalNtVerses": 0,
        "errors": [],
    }
    verses_by_book: dict[str, dict[tuple[int, int], dict]] = {}

    for (collection, book_id), code in expected_outputs.items():
        path = output_dir / collection / f"{book_id}.json"
        require(path.is_file(), f"Missing overlay: {path}")
        overlay = json.loads(path.read_text(encoding="utf-8"))
        require(overlay["schemaVersion"] == SCHEMA_VERSION, f"Schema mismatch: {path}")
        require(overlay["editionId"] == EDITION_ID, f"Edition mismatch: {path}")
        require(overlay["language"] == "en", f"Language mismatch: {path}")
        require(overlay["collection"] == collection, f"Collection mismatch: {path}")
        require(overlay["bookId"] == book_id, f"Book ID mismatch: {path}")
        require(overlay["sourceBookCode"] == code, f"Source code mismatch: {path}")

        source = parse_usfm(source_files[code])
        expected_chapters, expected_mapping = select_book_chapters(book_id, source)
        require(overlay["chapters"] == expected_chapters, f"Overlay differs from parsed source: {path}")
        require(overlay.get("sourceMapping") == expected_mapping, f"Source mapping mismatch: {path}")
        require(path.read_text(encoding="utf-8") == normalized_json(overlay), f"Non-canonical JSON formatting: {path}")
        source_manifest = manifest_by_book[(collection, book_id)]
        require(source_manifest["sourceFileSha256"] == sha256(source_files[code]), f"Source file hash mismatch: {book_id}")

        flat = flattened(overlay["chapters"])
        verses_by_book[book_id] = flat
        report["overlayBooks"] += 1
        report["chapters"] += len(overlay["chapters"])
        report["verses"] += len(flat)
        report["sourceFootnotes"] += sum(v.get("sourceFootnoteCount", 0) for v in flat.values())
        report["jesusWordSpans"] += sum(v["text"].count("[J]") for v in flat.values())
        report["translatorAdditionSpans"] += sum(v["text"].count("[ADD]") for v in flat.values())
        superscriptions = [c["superscription"] for c in overlay["chapters"] if "superscription" in c]
        report["jesusWordSpans"] += sum(text.count("[J]") for text in superscriptions)
        report["translatorAdditionSpans"] += sum(text.count("[ADD]") for text in superscriptions)
        report["superscriptions"] += len(superscriptions)

    for book_id, _reason in FALLBACKS:
        item = manifest_by_book[("deuterocanonical", book_id)]
        require(item["coverage"] == "fallback", f"Fallback status missing: {book_id}")
        require(item["fallbackEditionId"] == "current", f"Fallback edition missing: {book_id}")
        require(not (output_dir / "deuterocanonical" / f"{book_id}.json").exists(), f"Fallback unexpectedly has overlay: {book_id}")
        report["fallbackBooks"] += 1

    for book_id, chapter, verse in TRADITIONAL_NT_VERSES:
        require((chapter, verse) in verses_by_book[book_id], f"Traditional KJV verse missing: {book_id} {chapter}:{verse}")
        report["traditionalNtVerses"] += 1

    song = verses_by_book["song_of_three"]
    require(set(song) == {(1, verse) for verse in range(1, 69)}, "Song of Three must contain exactly 1:1-68")
    letter = verses_by_book["letter_of_jeremiah"]
    require(set(letter) == {(1, verse) for verse in range(1, 73)}, "Letter of Jeremiah must contain exactly 1:1-72")
    baruch = verses_by_book["baruch"]
    require(all(chapter <= 5 for chapter, _verse in baruch), "Baruch overlay must exclude source chapter 6")

    old_items = [item for item in manifest["books"] if item["collection"] == "old_testament"]
    new_items = [item for item in manifest["books"] if item["collection"] == "new_testament"]
    dc_items = [item for item in manifest["books"] if item["collection"] == "deuterocanonical" and item["coverage"] != "fallback"]
    require((len(old_items), sum(i["chapters"] for i in old_items), sum(i["verses"] for i in old_items)) == (39, 929, 23145), "KJV Old Testament totals mismatch")
    require((len(new_items), sum(i["chapters"] for i in new_items), sum(i["verses"] for i in new_items)) == (27, 260, 7957), "KJV New Testament totals mismatch")
    require((len(dc_items), sum(i["chapters"] for i in dc_items), sum(i["verses"] for i in dc_items)) == (14, 166, 5614), "KJVA overlay totals mismatch")
    esg = parse_usfm(source_files["ESG"])
    require([chapter.number for chapter in esg.chapters] == list(range(10, 17)), "ESG fallback source must contain chapters 10-16")
    require(sum(len(chapter.verses) for chapter in esg.chapters) == 105, "ESG fallback source verse total mismatch")

    require(report["overlayBooks"] == 80, "Expected 80 overlay books")
    require(report["fallbackBooks"] == 4, "Expected 4 fallback books")
    require(report["traditionalNtVerses"] == 16, "Expected all 16 traditional NT verses")
    expected_report = {
        "editionId": EDITION_ID,
        **manifest["totals"],
        "traditionalNtVerses": 16,
        "errors": [],
    }
    require(report == expected_report, f"Manifest totals mismatch: actual={report}, expected={expected_report}")
    return report


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Extracted eng-kjv USFM directory")
    parser.add_argument(
        "--output",
        type=Path,
        default=repo_root / "shared/assets/books/editions/en/kjv1769",
        help="Committed edition overlay directory",
    )
    parser.add_argument("--archive", type=Path, help="Optional original ZIP to hash-check")
    parser.add_argument("--report", type=Path, help="Optional JSON report path")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        report = validate(args.source.resolve(), args.output.resolve(), args.archive.resolve() if args.archive else None)
    except (OSError, KeyError, ValueError, ValidationError) as exc:
        print(f"KJV overlay validation failed: {exc}", file=sys.stderr)
        return 1
    rendered = normalized_json(report)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered, encoding="utf-8", newline="\n")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
