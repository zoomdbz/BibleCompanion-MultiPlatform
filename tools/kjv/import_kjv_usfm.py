#!/usr/bin/env python3
"""Build deterministic KJV 1769 verse overlays from the pinned eBible USFM.

The importer never edits the app's existing BSB/custom-translation books. It
emits edition overlays whose inline markup is deliberately small:

  [J]...[/J]       words marked by USFM ``wj``
  [ADD]...[/ADD]   words marked by USFM ``add`` (translator supplied)

USFM Strong's data and source footnote apparatus do not enter verse text.
Their presence is counted in each output book and in the edition manifest.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


EDITION_ID = "kjv1769"
SCHEMA_VERSION = 1
ARCHIVE_SHA256 = "1165788907A8BBE93C3299D89EB5134D942038E5DCA2C0832BEB13E8F72441D0"
SOURCE_DATE = "2026-08-19"
SOURCE_URL = "https://ebible.org/eng-kjv/"


# source code, collection, app book id
BOOKS: tuple[tuple[str, str, str], ...] = (
    ("GEN", "old_testament", "genesis"),
    ("EXO", "old_testament", "exodus"),
    ("LEV", "old_testament", "leviticus"),
    ("NUM", "old_testament", "numbers"),
    ("DEU", "old_testament", "deuteronomy"),
    ("JOS", "old_testament", "joshua"),
    ("JDG", "old_testament", "judges"),
    ("RUT", "old_testament", "ruth"),
    ("1SA", "old_testament", "1_samuel"),
    ("2SA", "old_testament", "2_samuel"),
    ("1KI", "old_testament", "1_kings"),
    ("2KI", "old_testament", "2_kings"),
    ("1CH", "old_testament", "1_chronicles"),
    ("2CH", "old_testament", "2_chronicles"),
    ("EZR", "old_testament", "ezra"),
    ("NEH", "old_testament", "nehemiah"),
    ("EST", "old_testament", "esther"),
    ("JOB", "old_testament", "job"),
    ("PSA", "old_testament", "psalms"),
    ("PRO", "old_testament", "proverbs"),
    ("ECC", "old_testament", "ecclesiastes"),
    ("SNG", "old_testament", "song_of_songs"),
    ("ISA", "old_testament", "isaiah"),
    ("JER", "old_testament", "jeremiah"),
    ("LAM", "old_testament", "lamentations"),
    ("EZK", "old_testament", "ezekiel"),
    ("DAN", "old_testament", "daniel"),
    ("HOS", "old_testament", "hosea"),
    ("JOL", "old_testament", "joel"),
    ("AMO", "old_testament", "amos"),
    ("OBA", "old_testament", "obadiah"),
    ("JON", "old_testament", "jonah"),
    ("MIC", "old_testament", "micah"),
    ("NAM", "old_testament", "nahum"),
    ("HAB", "old_testament", "habakkuk"),
    ("ZEP", "old_testament", "zephaniah"),
    ("HAG", "old_testament", "haggai"),
    ("ZEC", "old_testament", "zechariah"),
    ("MAL", "old_testament", "malachi"),
    ("MAT", "new_testament", "matthew"),
    ("MRK", "new_testament", "mark"),
    ("LUK", "new_testament", "luke"),
    ("JHN", "new_testament", "john"),
    ("ACT", "new_testament", "acts"),
    ("ROM", "new_testament", "romans"),
    ("1CO", "new_testament", "1_corinthians"),
    ("2CO", "new_testament", "2_corinthians"),
    ("GAL", "new_testament", "galatians"),
    ("EPH", "new_testament", "ephesians"),
    ("PHP", "new_testament", "philippians"),
    ("COL", "new_testament", "colossians"),
    ("1TH", "new_testament", "1_thessalonians"),
    ("2TH", "new_testament", "2_thessalonians"),
    ("1TI", "new_testament", "1_timothy"),
    ("2TI", "new_testament", "2_timothy"),
    ("TIT", "new_testament", "titus"),
    ("PHM", "new_testament", "philemon"),
    ("HEB", "new_testament", "hebrews"),
    ("JAS", "new_testament", "james"),
    ("1PE", "new_testament", "1_peter"),
    ("2PE", "new_testament", "2_peter"),
    ("1JN", "new_testament", "1_john"),
    ("2JN", "new_testament", "2_john"),
    ("3JN", "new_testament", "3_john"),
    ("JUD", "new_testament", "jude"),
    ("REV", "new_testament", "revelation"),
    ("TOB", "deuterocanonical", "tobit"),
    ("JDT", "deuterocanonical", "judith"),
    ("WIS", "deuterocanonical", "wisdom"),
    ("SIR", "deuterocanonical", "sirach"),
    ("BAR", "deuterocanonical", "baruch"),
    ("BAR", "deuterocanonical", "letter_of_jeremiah"),
    ("MAN", "deuterocanonical", "prayer_of_manasseh"),
    ("1MA", "deuterocanonical", "1_maccabees"),
    ("2MA", "deuterocanonical", "2_maccabees"),
    ("1ES", "deuterocanonical", "1_esdras"),
    ("2ES", "deuterocanonical", "2_esdras"),
    ("S3Y", "deuterocanonical", "song_of_three"),
    ("SUS", "deuterocanonical", "susanna"),
    ("BEL", "deuterocanonical", "bel_and_the_dragon"),
)

FALLBACKS: tuple[tuple[str, str], ...] = (
    ("esther_greek", "The source ESG is only the KJV additions in chapters 10-16 and cannot replace the app's integrated Greek Esther without a reviewed versification map."),
    ("psalm_151", "The pinned KJV/KJVA source contains no Psalm 151."),
    ("3_maccabees", "The pinned KJV/KJVA source contains no 3 Maccabees."),
    ("4_maccabees", "The pinned KJV/KJVA source contains no 4 Maccabees."),
)

TRADITIONAL_NT_VERSES: tuple[tuple[str, int, int], ...] = (
    ("matthew", 17, 21), ("matthew", 18, 11), ("matthew", 23, 14),
    ("mark", 7, 16), ("mark", 9, 44), ("mark", 9, 46),
    ("mark", 11, 26), ("mark", 15, 28), ("luke", 17, 36),
    ("luke", 23, 17), ("john", 5, 4), ("acts", 8, 37),
    ("acts", 15, 34), ("acts", 24, 7), ("acts", 28, 29),
    ("romans", 16, 24),
)


@dataclass(frozen=True)
class SourceVerse:
    chapter: int
    verse: int
    text: str
    source_footnotes: int


@dataclass(frozen=True)
class SourceChapter:
    number: int
    verses: tuple[SourceVerse, ...]
    superscription: str | None


@dataclass(frozen=True)
class SourceBook:
    code: str
    source_file: Path
    chapters: tuple[SourceChapter, ...]
    footnote_count: int
    jesus_span_count: int
    addition_span_count: int


class UsfmError(ValueError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def marker_at(text: str, offset: int) -> tuple[str, bool, int] | None:
    """Return (marker, closing, next offset) for the marker at offset."""
    if offset >= len(text) or text[offset] != "\\":
        return None
    match = re.match(r"\\(\+?[A-Za-z0-9-]+)(\*)?", text[offset:])
    if not match:
        return None
    return match.group(1), bool(match.group(2)), offset + match.end()


def skip_marker_spacing(text: str, offset: int) -> int:
    while offset < len(text) and text[offset] in " \t":
        offset += 1
    return offset


def find_closing_marker(text: str, offset: int, marker: str) -> tuple[int, int]:
    token = "\\" + marker + "*"
    end = text.find(token, offset)
    if end < 0:
        raise UsfmError(f"Unclosed inline marker {marker!r}: {text[offset:offset + 120]!r}")
    return end, end + len(token)


def parse_inline(raw: str) -> tuple[str, int, int, int]:
    """Parse inline USFM and return text plus f/wj/add occurrence counts."""
    output: list[str] = []
    stacks: list[str] = []
    footnotes = 0
    jesus_spans = 0
    addition_spans = 0
    i = 0
    while i < len(raw):
        parsed = marker_at(raw, i)
        if parsed is None:
            output.append(raw[i])
            i += 1
            continue
        marker, closing, after = parsed
        base = marker.removeprefix("+")

        if base == "f":
            if closing:
                raise UsfmError(f"Unexpected footnote close in {raw!r}")
            _, i = find_closing_marker(raw, after, marker)
            footnotes += 1
            continue

        if base == "w":
            if closing:
                raise UsfmError(f"Unexpected word close in {raw!r}")
            content_end, i = find_closing_marker(raw, after, marker)
            content = raw[skip_marker_spacing(raw, after):content_end]
            visible, separator, _attributes = content.partition("|")
            if not separator:
                raise UsfmError(f"Word marker has no attribute separator: {content!r}")
            nested_text, nested_f, nested_j, nested_add = parse_inline(visible)
            output.append(nested_text)
            footnotes += nested_f
            jesus_spans += nested_j
            addition_spans += nested_add
            continue

        if base in {"wj", "add", "nd", "tl"}:
            canonical = base
            if closing:
                if not stacks or stacks[-1] != canonical:
                    raise UsfmError(f"Mismatched close {marker!r}; stack={stacks!r}")
                stacks.pop()
                if canonical == "wj":
                    output.append("[/J]")
                elif canonical == "add":
                    output.append("[/ADD]")
                i = after
                continue
            stacks.append(canonical)
            if canonical == "wj":
                output.append("[J]")
                jesus_spans += 1
            elif canonical == "add":
                output.append("[ADD]")
                addition_spans += 1
            i = skip_marker_spacing(raw, after)
            continue

        raise UsfmError(f"Unsupported inline marker {marker!r} in {raw!r}")

    if stacks:
        raise UsfmError(f"Unclosed inline markers {stacks!r} in {raw!r}")

    rendered = "".join(output).replace("\u00b6", " ")
    rendered = re.sub(r"\s+", " ", rendered).strip()
    return rendered, footnotes, jesus_spans, addition_spans


def source_code(text: str, path: Path) -> str:
    match = re.search(r"(?m)^\\id\s+(\S+)", text)
    if not match:
        raise UsfmError(f"Missing id marker in {path}")
    return match.group(1)


def parse_usfm(path: Path) -> SourceBook:
    text = path.read_text(encoding="utf-8-sig")
    code = source_code(text, path)
    chapters: list[SourceChapter] = []
    current_number: int | None = None
    current_verses: list[SourceVerse] = []
    current_superscription: str | None = None
    footnote_count = 0
    jesus_span_count = 0
    addition_span_count = 0

    def finish_chapter() -> None:
        nonlocal current_number, current_verses, current_superscription
        if current_number is None:
            return
        chapters.append(SourceChapter(current_number, tuple(current_verses), current_superscription))
        current_number = None
        current_verses = []
        current_superscription = None

    for line_number, original_line in enumerate(text.splitlines(), start=1):
        line = original_line.rstrip()
        chapter_match = re.match(r"^\\c\s+(\d+)\s*$", line)
        if chapter_match:
            finish_chapter()
            current_number = int(chapter_match.group(1))
            continue

        verse_match = re.match(r"^\\v\s+(\d+)\s+(.*)$", line)
        if verse_match:
            if current_number is None:
                raise UsfmError(f"Verse before chapter in {path}:{line_number}")
            verse_number = int(verse_match.group(1))
            parsed, notes, jesus, additions = parse_inline(verse_match.group(2))
            if not parsed:
                raise UsfmError(f"Empty verse in {path}:{line_number}")
            if current_verses and verse_number <= current_verses[-1].verse:
                raise UsfmError(f"Non-increasing verse number in {path}:{line_number}")
            current_verses.append(SourceVerse(current_number, verse_number, parsed, notes))
            footnote_count += notes
            jesus_span_count += jesus
            addition_span_count += additions
            continue

        superscription_match = re.match(r"^\\d\s+(.*)$", line)
        if superscription_match:
            if current_number is None:
                raise UsfmError(f"Superscription before chapter in {path}:{line_number}")
            parsed, notes, jesus, additions = parse_inline(superscription_match.group(1))
            if current_superscription:
                current_superscription += " " + parsed
            else:
                current_superscription = parsed
            footnote_count += notes
            jesus_span_count += jesus
            addition_span_count += additions

    finish_chapter()
    if not chapters:
        raise UsfmError(f"No chapters parsed from {path}")
    return SourceBook(
        code=code,
        source_file=path,
        chapters=tuple(chapters),
        footnote_count=footnote_count,
        jesus_span_count=jesus_span_count,
        addition_span_count=addition_span_count,
    )


def find_source_files(source_dir: Path) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for path in sorted(source_dir.glob("*.usfm")):
        code = source_code(path.read_text(encoding="utf-8-sig"), path)
        if code == "FRT":
            continue
        if code in result:
            raise UsfmError(f"Duplicate source code {code}: {result[code]} and {path}")
        result[code] = path
    return result


def source_file_set_sha256(source_files: dict[str, Path], codes: Iterable[str]) -> str:
    digest = hashlib.sha256()
    for code in sorted(codes):
        path = source_files[code]
        digest.update(path.name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(bytes.fromhex(sha256(path)))
    return digest.hexdigest().upper()


def chapter_to_json(chapter: SourceChapter, transform=None) -> dict:
    verses = []
    for source_verse in chapter.verses:
        target_chapter, target_verse = (
            transform(source_verse) if transform else (source_verse.chapter, source_verse.verse)
        )
        item = {
            "chapter": target_chapter,
            "verse": target_verse,
            "text": source_verse.text,
        }
        if transform:
            item["sourceChapter"] = source_verse.chapter
            item["sourceVerse"] = source_verse.verse
        if source_verse.source_footnotes:
            item["sourceFootnoteCount"] = source_verse.source_footnotes
        verses.append(item)
    item = {"number": verses[0]["chapter"] if verses else chapter.number}
    if chapter.superscription:
        item["superscription"] = chapter.superscription
    item["verses"] = verses
    return item


def select_book_chapters(book_id: str, source: SourceBook) -> tuple[list[dict], dict | None]:
    if book_id == "baruch":
        selected = [chapter_to_json(c) for c in source.chapters if 1 <= c.number <= 5]
        return selected, {
            "kind": "chapterSubset",
            "sourceRange": "BAR 1:1-5:9",
            "targetRange": "Baruch 1:1-5:9",
        }
    if book_id == "letter_of_jeremiah":
        chapter = next((c for c in source.chapters if c.number == 6), None)
        if chapter is None:
            raise UsfmError("BAR chapter 6 is missing")
        heading = next((v for v in chapter.verses if v.verse == 1), None)
        mapped = tuple(v for v in chapter.verses if 2 <= v.verse <= 73)
        if heading is None or len(mapped) != 72:
            raise UsfmError("Expected BAR 6:1 heading followed by BAR 6:2-73")
        mapped_chapter = SourceChapter(1, mapped, None)
        transform = lambda verse: (1, verse.verse - 1)
        return [chapter_to_json(mapped_chapter, transform)], {
            "kind": "verseOffset",
            "sourceRange": "BAR 6:2-73",
            "targetRange": "Letter of Jeremiah 1:1-72",
            "sourceHeading": {
                "sourceChapter": 6,
                "sourceVerse": 1,
                "text": heading.text,
            },
            "reason": "BAR 6:1 is the epistle heading sentence in this source; the app's 72-verse Greek-style numbering begins with source BAR 6:2.",
        }
    return [chapter_to_json(c) for c in source.chapters], None


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    path.write_text(text, encoding="utf-8", newline="\n")


def count_tag(text: str, opening: str) -> int:
    return text.count(opening)


def book_metrics(chapters: Iterable[dict]) -> dict[str, int]:
    chapter_list = list(chapters)
    texts = [verse["text"] for chapter in chapter_list for verse in chapter["verses"]]
    superscriptions = [chapter["superscription"] for chapter in chapter_list if "superscription" in chapter]
    return {
        "chapters": len(chapter_list),
        "verses": len(texts),
        "sourceFootnotes": sum(
            verse.get("sourceFootnoteCount", 0)
            for chapter in chapter_list
            for verse in chapter["verses"]
        ),
        "jesusWordSpans": sum(count_tag(text, "[J]") for text in texts + superscriptions),
        "translatorAdditionSpans": sum(count_tag(text, "[ADD]") for text in texts + superscriptions),
        "superscriptions": len(superscriptions),
    }


def build(source_dir: Path, output_dir: Path, archive: Path | None) -> dict:
    if not source_dir.is_dir():
        raise UsfmError(f"USFM source directory does not exist: {source_dir}")
    if archive is not None:
        if not archive.is_file():
            raise UsfmError(f"Archive does not exist: {archive}")
        actual_archive_hash = sha256(archive)
        if actual_archive_hash != ARCHIVE_SHA256:
            raise UsfmError(
                f"Archive SHA-256 mismatch: expected {ARCHIVE_SHA256}, got {actual_archive_hash}"
            )

    if output_dir.name != EDITION_ID or output_dir.parent.name != "en" or output_dir.parent.parent.name != "editions":
        raise UsfmError(
            "Refusing unsafe output path; it must end with editions/en/kjv1769"
        )

    source_files = find_source_files(source_dir)
    required_codes = {code for code, _collection, _book_id in BOOKS}
    required_codes.add("ESG")
    missing_codes = sorted(required_codes - source_files.keys())
    unexpected_codes = sorted(source_files.keys() - required_codes)
    if missing_codes or unexpected_codes:
        raise UsfmError(f"Source inventory mismatch; missing={missing_codes}, unexpected={unexpected_codes}")

    parsed = {code: parse_usfm(source_files[code]) for code in sorted(required_codes)}

    staging = output_dir.parent / f".{output_dir.name}.staging"
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)

    manifest_books = []
    for code, collection, book_id in BOOKS:
        source = parsed[code]
        chapters, mapping = select_book_chapters(book_id, source)
        status = "mapped" if mapping else "full"
        overlay = {
            "schemaVersion": SCHEMA_VERSION,
            "editionId": EDITION_ID,
            "language": "en",
            "collection": collection,
            "bookId": book_id,
            "sourceBookCode": code,
            "coverage": status,
            "chapters": chapters,
        }
        if mapping:
            overlay["sourceMapping"] = mapping
        relative_output = Path(collection) / f"{book_id}.json"
        write_json(staging / relative_output, overlay)
        metrics = book_metrics(chapters)
        manifest_item = {
            "collection": collection,
            "bookId": book_id,
            "sourceBookCode": code,
            "sourceFile": source.source_file.name,
            "sourceFileSha256": sha256(source.source_file),
            "coverage": status,
            "output": relative_output.as_posix(),
            **metrics,
        }
        if mapping:
            manifest_item["sourceMapping"] = mapping
        manifest_books.append(manifest_item)

    for book_id, reason in FALLBACKS:
        manifest_books.append({
            "collection": "deuterocanonical",
            "bookId": book_id,
            "sourceBookCode": "ESG" if book_id == "esther_greek" else None,
            "sourceFile": source_files["ESG"].name if book_id == "esther_greek" else None,
            "sourceFileSha256": sha256(source_files["ESG"]) if book_id == "esther_greek" else None,
            "coverage": "fallback",
            "fallbackEditionId": "current",
            "reason": reason,
        })

    totals = {
        "overlayBooks": sum(item["coverage"] != "fallback" for item in manifest_books),
        "fallbackBooks": sum(item["coverage"] == "fallback" for item in manifest_books),
        "chapters": sum(item.get("chapters", 0) for item in manifest_books),
        "verses": sum(item.get("verses", 0) for item in manifest_books),
        "sourceFootnotes": sum(item.get("sourceFootnotes", 0) for item in manifest_books),
        "jesusWordSpans": sum(item.get("jesusWordSpans", 0) for item in manifest_books),
        "translatorAdditionSpans": sum(item.get("translatorAdditionSpans", 0) for item in manifest_books),
        "superscriptions": sum(item.get("superscriptions", 0) for item in manifest_books),
    }
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "editionId": EDITION_ID,
        "displayName": "King James Version",
        "language": "en",
        "source": {
            "title": "King James Version + Apocrypha",
            "description": "Standardized 1769 text with Apocrypha/Deuterocanon",
            "publisher": "eBible.org; source text courtesy of CrossWire Bible Society",
            "url": SOURCE_URL,
            "sourceDate": SOURCE_DATE,
            "archiveSha256": ARCHIVE_SHA256,
            "extractedScriptureFileSetSha256": source_file_set_sha256(source_files, required_codes),
            "rights": "Public domain outside the United Kingdom; UK distribution requires a separate rights determination.",
        },
        "markup": {
            "jesusWords": {"open": "[J]", "close": "[/J]", "sourceMarker": "wj"},
            "translatorAdditions": {"open": "[ADD]", "close": "[/ADD]", "sourceMarker": "add"},
            "wordMetadata": "USFM w/+w Strong's attributes are intentionally excluded from display text.",
            "footnotes": "USFM f apparatus is intentionally excluded from display text; counts are retained for audit.",
            "paragraphMarks": "USFM paragraph structure and printed pilcrow glyphs are presentation data and are intentionally excluded from verse text.",
        },
        "totals": totals,
        "books": manifest_books,
    }
    write_json(staging / "_manifest.json", manifest)

    if output_dir.exists():
        shutil.rmtree(output_dir)
    staging.rename(output_dir)
    return manifest


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Extracted eng-kjv USFM directory")
    parser.add_argument(
        "--output",
        type=Path,
        default=repo_root / "shared/assets/books/editions/en/kjv1769",
        help="Edition output directory",
    )
    parser.add_argument("--archive", type=Path, help="Optional original ZIP; verifies the pinned SHA-256")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        manifest = build(args.source.resolve(), args.output.resolve(), args.archive.resolve() if args.archive else None)
    except (OSError, UsfmError, UnicodeError) as exc:
        print(f"KJV import failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({"output": str(args.output.resolve()), **manifest["totals"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
