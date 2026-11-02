#!/usr/bin/env python3
"""Convert the two supplied Swahili Bibles to Bible Companion assets.

The 2015 ONEN USFM archive supplies the base 66-book reader text and its
publisher headings. The SUV XML supplies a separate schema-2 edition overlay.
Study prose is intentionally absent: this importer does not claim to translate
introductions, takeaways, cross-references, or translation notes.

The script refuses to overwrite an existing destination. Run it from anywhere:

  python3 tools/swahili/import_sources.py \
    --onen /path/to/swhonen_usfm.zip \
    --suv /path/to/SwahiliSUV1952Bible.xml \
    --out shared/assets
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "traditional"))
from import_traditional_editions import (  # noqa: E402
    BOOKS, ImportErrorDetail, parse_inline, parse_usfm_bytes, source_code,
)

ONEN_SHA256 = "9ad97f89077ba8a07c94b907c70f2b1980a00a1f43f411752bb57d106c9fc0c6"
SUV_SHA256 = "a506e7eee5a1198e159f2d202f52783a86b8be7e247eb5a05cd03633b9e12b20"
ONEN_SOURCE = "https://ebible.org/swhonen/"
ONEN_LICENSE = "https://creativecommons.org/licenses/by-sa/4.0/"


def check_hash(path: Path, expected: str) -> str:
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise ValueError(f"Source changed: {path}: expected {expected}, got {actual}")
    return actual


def clean(text: str) -> str:
    return " ".join(text.split())


SUV_DIVINE_NAME = re.compile(r"(?<![\w-])(?:BWANA|MUNGU|YEHOVA)(?![\w-])")
SUV_OLD_TESTAMENT_CODES = frozenset(code for code, collection, _ in BOOKS
                                    if collection == "old_testament")
SUV_PSALM_MUNGU_NAME = {(8, 1), (8, 9), (109, 21), (140, 7), (141, 8)}
SUV_LORD_GOD = re.compile(r"(?<!\w)Bwana,?\s+$")
SUV_JESUS_SPANS = Path(__file__).with_name("suv_jesus_spans.json")
SW_QUOTE_DIVINE_NAMES = Path(__file__).with_name("sw_quote_divine_names.json")
SW_BWANA = re.compile(r"(?<![\w-])Bwana(?![\w-])")


def load_suv_jesus_spans() -> dict[str, dict[str, list[list[int]]]]:
    """Load the reviewed speech positions against the pinned SUV source."""
    ledger = json.loads(SUV_JESUS_SPANS.read_text(encoding="utf-8"))
    expected = {code for code, collection, _ in BOOKS if collection == "new_testament"}
    if (ledger.get("schemaVersion") != 1 or ledger.get("editionId") != "suv1952" or
            ledger.get("sourceSha256") != SUV_SHA256 or
            set(ledger.get("books", {})) != expected):
        raise ValueError("SUV Jesus-speech ledger identity or book inventory changed")
    return ledger["books"]


def mark_suv_jesus_words(text: str, spans: list[list[int]], reference: str) -> str:
    """Apply speech spans at Python character offsets in the exact XML text."""
    result = []
    previous = 0
    for span in spans:
        if (not isinstance(span, list) or len(span) != 2 or
                not all(isinstance(offset, int) for offset in span)):
            raise ValueError(f"Invalid SUV Jesus-speech span: {reference}: {span}")
        start, end = span
        if start < previous or start >= end or end > len(text):
            raise ValueError(f"Out-of-range SUV Jesus-speech span: {reference}: {span}")
        result.extend((text[previous:start], "[J]", text[start:end], "[/J]"))
        previous = end
    result.append(text[previous:])
    return "".join(result)


def load_quote_divine_names() -> tuple[dict[str, dict], dict[str, dict]]:
    """Explicit OT quotation identities; these are editorial, not native NT nd."""
    ledger = json.loads(SW_QUOTE_DIVINE_NAMES.read_text(encoding="utf-8"))
    entries = ledger.get("entries", [])
    if (ledger.get("schemaVersion") != 1 or
            ledger.get("onenSourceSha256") != ONEN_SHA256 or
            ledger.get("suvSourceSha256") != SUV_SHA256 or len(entries) != 46):
        raise ValueError("Swahili quotation divine-name ledger identity changed")
    onen, suv = {}, {}
    nt_codes = {code for code, collection, _ in BOOKS if collection == "new_testament"}
    for entry in entries:
        for ref_key, target in (("onenRef", onen), ("suvRef", suv)):
            reference = entry[ref_key]
            if reference.split(" ", 1)[0] not in nt_codes or reference in target:
                raise ValueError(f"Duplicate or invalid quotation reference: {reference}")
            target[reference] = entry
    return onen, suv


def mark_quote_divine_names(text: str, expected: int, selected: list[int], reference: str) -> str:
    matches = list(SW_BWANA.finditer(text))
    if (len(matches) != expected or not selected or
            len(selected) != len(set(selected)) or
            any(not isinstance(index, int) or index < 1 or index > expected for index in selected)):
        raise ValueError(f"Bwana quotation placement changed: {reference}")
    result = text
    for index in sorted(selected, reverse=True):
        match = matches[index - 1]
        prior = text[:match.start()]
        if prior.rfind("[DN]") > prior.rfind("[/DN]"):
            raise ValueError(f"Quotation already marked: {reference}")
        result = result[:match.start()] + "[DN]Bwana[/DN]" + result[match.end():]
    return result


def mark_suv_divine_names(text: str, code: str, chapter: int, verse: int) -> str:
    """Use the SUV's own name typography, not ordinary Bwana/Mungu titles."""
    if code not in SUV_OLD_TESTAMENT_CODES:
        return text

    def mark(match: re.Match[str]) -> str:
        word = match.group()
        if word == "BWANA" and (code, chapter, verse) == ("HAB", 3, 19):
            return word  # "YEHOVA, aliye BWANA": BWANA is the accompanying title.
        if word == "MUNGU" and not (
            SUV_LORD_GOD.search(text[:match.start()]) or
            (code == "PSA" and (chapter, verse) in SUV_PSALM_MUNGU_NAME)
        ):
            return word  # Most isolated poetic MUNGU forms mean God, not YHWH.
        return f"[DN]{word}[/DN]"

    return SUV_DIVINE_NAME.sub(mark, text)


def preserve_divine_name(raw: bytes) -> bytes:
    """Keep source-proven USFM nd spans in the app's [DN] markup."""
    raw = re.sub(rb"\\\+?nd[ \t]+", b"[DN]", raw)
    return re.sub(rb"\\\+?nd\*", b"[/DN]", raw)


def preserve_continuation_text(text: str) -> str:
    """Pass source table and paragraph continuations to the shared parser."""
    aliases = {"qr", "qc", "pm", "pmr", "pmo", "pmc", "tc1", "tcr2"}
    return "\n".join(
        re.sub(r"^\\(?:qr|qc|pm|pmr|pmo|pmc|tc1|tcr2)(?=\s)", r"\\p", line)
        if re.match(r"^\\([A-Za-z0-9]+)", line) and
        re.match(r"^\\([A-Za-z0-9]+)", line).group(1) in aliases else line
        for line in text.splitlines()
    ) + "\n"


def repair_neh_8_boundary(text: str, code: str) -> str:
    """Move the source's unnumbered Nehemiah 8:1 preface into verse 8:1.

    This archive puts the heading and the first words of 8:1 before its
    chapter marker. The generic line parser otherwise adds them to 7:73.
    The source archive hash and these exact checks pin this one repair.
    """
    if code != "NEH":
        return text
    lines = text.splitlines()
    heading = r"\s1 Ezra Asoma Sheria"
    positions = [i for i, line in enumerate(lines) if line.strip() == heading]
    if len(positions) != 1:
        raise ValueError("Expected one Nehemiah 8 boundary heading")
    index = positions[0]
    if (not lines[index + 1].startswith(r"\p Ilipotimia miezi saba,") or
            not lines[index + 2].startswith(r"\c 8")):
        raise ValueError("Nehemiah 8 source boundary changed")
    preface = lines.pop(index + 1).removeprefix(r"\p ").strip()
    lines.pop(index)
    chapter_at = next(i for i, line in enumerate(lines) if line.startswith(r"\c 8"))
    verse_at = next(i for i in range(chapter_at + 1, len(lines)) if lines[i].startswith(r"\v 1 "))
    lines.insert(verse_at, heading)
    lines[verse_at + 1] = lines[verse_at + 1].replace(r"\v 1 ", rf"\v 1 {preface} ", 1)
    return "\n".join(lines) + "\n"


def source_headings(text: str, parsed_book) -> dict[int, list[dict]]:
    """Anchor source section headings to the next native verse unit."""
    result: dict[int, list[dict]] = {}
    chapter = 0
    last_verse = 0
    pending: list[str] = []
    anchored: dict[int, dict[int, list[str]]] = defaultdict(lambda: defaultdict(list))
    for line in text.splitlines():
        match = re.match(r"^\\c\s+(\d+)\s*$", line)
        if match:
            if pending:
                # Song of Songs 6:13 changes speaker inside its final verse.
                if chapter != 6 or parsed_book.code != "SNG" or pending != ["Mpenzi"]:
                    raise ValueError(f"Unanchored heading after chapter {chapter}: {pending}")
                anchored[chapter][last_verse].extend(pending)
                pending.clear()
            chapter = int(match.group(1))
            last_verse = 0
            continue
        match = re.match(r"^\\(?:s[1-4]?|ms[1-4]?|sp)\s+(.+)$", line)
        if match and chapter:
            heading, _, _, _ = parse_inline(match.group(1))
            if heading:
                pending.append(heading)
            continue
        match = re.match(r"^\\v\s+(\d+)(?:-\d+)?\s+", line)
        if match:
            last_verse = int(match.group(1))
            if pending:
                anchored[chapter][last_verse].extend(pending)
                pending.clear()
    if pending:
        raise ValueError(f"Unanchored heading after final chapter {chapter}: {pending}")
    for source_chapter in parsed_book.chapters:
        units = {verse.verse for verse in source_chapter.verses}
        headings = []
        for verse, texts in sorted(anchored[source_chapter.number].items()):
            if verse not in units:
                raise ValueError(f"Heading anchor missing: {parsed_book.code} {source_chapter.number}:{verse}")
            headings.append({"beforeVerse": verse, "text": " / ".join(texts)})
        result[source_chapter.number] = headings
    return result


def parse_onen(path: Path):
    books = {}
    names = {}
    headings = {}
    file_hashes = {}
    with zipfile.ZipFile(path) as source:
        members = [entry for entry in source.infolist() if entry.filename.endswith(".usfm")]
        for entry in members:
            original = source.read(entry)
            text = original.decode("utf-8-sig")
            code = source_code(text, Path(entry.filename))
            if code in books:
                raise ValueError(f"Duplicate USFM book: {code}")
            match = re.search(r"(?m)^\\h\s+(.+)$", text)
            if match is None:
                raise ValueError(f"Missing Swahili book name: {code}")
            name = clean(match.group(1))
            text = repair_neh_8_boundary(text, code)
            marked = preserve_divine_name(preserve_continuation_text(text).encode("utf-8"))
            book = parse_usfm_bytes(marked, Path(entry.filename))
            book.source_sha256 = hashlib.sha256(original).hexdigest()
            books[code] = book
            names[code] = name
            headings[code] = source_headings(text, book)
            file_hashes[code] = book.source_sha256
    expected = {code for code, _, _ in BOOKS}
    if set(books) != expected:
        raise ValueError(f"ONEN book inventory: missing={sorted(expected-set(books))}, extra={sorted(set(books)-expected)}")
    return books, names, headings, file_hashes


def parse_suv(path: Path):
    root = ET.parse(path).getroot()
    if root.tag != "bible" or "Swahili SUV" not in root.attrib.get("translation", ""):
        raise ValueError("Unexpected SUV XML identity")
    source_books = root.findall(".//book")
    if len(source_books) != len(BOOKS):
        raise ValueError(f"Expected {len(BOOKS)} SUV books, got {len(source_books)}")
    result = {}
    jesus_spans = load_suv_jesus_spans()
    _, suv_quotes = load_quote_divine_names()
    seen_suv_quotes = set()
    seen_jesus_refs = {code: set() for code in jesus_spans}
    for position, ((code, _, _), source_book) in enumerate(zip(BOOKS, source_books), 1):
        if int(source_book.attrib.get("number", "0")) != position:
            raise ValueError(f"SUV book position mismatch at {position}")
        chapters = []
        for chapter_number, source_chapter in enumerate(source_book.findall("chapter"), 1):
            if int(source_chapter.attrib.get("number", "0")) != chapter_number:
                raise ValueError(f"SUV chapter gap: {code} {chapter_number}")
            verses = []
            for verse_number, element in enumerate(source_chapter.findall("verse"), 1):
                if int(element.attrib.get("number", "0")) != verse_number:
                    raise ValueError(f"SUV verse gap: {code} {chapter_number}:{verse_number}")
                value = clean("".join(element.itertext()))
                if not value:
                    raise ValueError(f"Empty SUV verse: {code} {chapter_number}:{verse_number}")
                value = mark_suv_divine_names(value, code, chapter_number, verse_number)
                if code in jesus_spans:
                    reference = f"{chapter_number}:{verse_number}"
                    if reference in jesus_spans[code]:
                        value = mark_suv_jesus_words(
                            value, jesus_spans[code][reference], f"{code} {reference}")
                        seen_jesus_refs[code].add(reference)
                quote_reference = f"{code} {chapter_number}:{verse_number}"
                if quote_reference in suv_quotes:
                    quote = suv_quotes[quote_reference]
                    value = mark_quote_divine_names(
                        value, quote["suvBwanaCount"], quote["suvSelect"], quote_reference)
                    seen_suv_quotes.add(quote_reference)
                verses.append({"chapter": chapter_number, "verse": verse_number, "text": value})
            if not verses:
                raise ValueError(f"Empty SUV chapter: {code} {chapter_number}")
            chapters.append({
                "number": chapter_number,
                "lastVerse": len(verses),
                "verseUnitCount": len(verses),
                "verses": verses,
                "headings": [],
            })
        result[code] = chapters
    for code, references in seen_jesus_refs.items():
        missing = set(jesus_spans[code]) - references
        if missing:
            raise ValueError(f"SUV Jesus-speech references not found: {code} {sorted(missing)}")
    if set(suv_quotes) != seen_suv_quotes:
        raise ValueError(f"SUV quotation references not found: {sorted(set(suv_quotes)-seen_suv_quotes)}")
    return result


def json_bytes(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def build_files(onen, names, headings, file_hashes, suv, onen_hash, suv_hash):
    files: dict[Path, bytes] = {}
    totals = {"books": 0, "chapters": 0, "onenVerseUnits": 0, "suvVerseUnits": 0}
    indices = {"old_testament": [], "new_testament": []}
    manifest_books = []
    onen_quotes, _ = load_quote_divine_names()
    seen_onen_quotes = set()
    for quote in onen_quotes.values():
        match = re.fullmatch(r"([0-9A-Z]+) (\d+):(\d+)(?:-(\d+))?", quote["sourceOtRef"])
        if match is None or match[1] not in SUV_OLD_TESTAMENT_CODES:
            raise ValueError(f"Invalid quotation source: {quote['sourceOtRef']}")
        chapter, first = int(match[2]), int(match[3])
        last = int(match[4] or match[3])
        source_chapter = onen[match[1]].chapters[chapter - 1]
        source_spans = sum(
            verse.text.count("[DN]Bwana[/DN]") for verse in source_chapter.verses
            if first <= verse.verse <= last
        )
        if source_spans < len(quote["onenSelect"]):
            raise ValueError(f"Quotation lacks native OT divine-name support: {quote['onenRef']}")
    for code, collection, book_id in BOOKS:
        source_book = onen[code]
        english_book = json.loads((ROOT / "shared/assets/books" / collection / "en" /
                                   f"{book_id}.json").read_text(encoding="utf-8-sig"))
        suv_chapters = suv[code]
        if len(source_book.chapters) != len(suv_chapters):
            raise ValueError(f"Chapter count differs between editions: {code}")
        if len(source_book.chapters) != len(english_book["stories"]):
            raise ValueError(f"Chapter count differs from app story IDs: {book_id}")
        stories = []
        for chapter in source_book.chapters:
            english_story_id = english_book["stories"][chapter.number - 1]["id"]
            if not english_story_id.endswith(f"-{chapter.number}"):
                raise ValueError(f"English story ID/chapter mismatch: {book_id} {chapter.number}")
            last = chapter.verses[-1].verse_end
            bullets = []
            for verse in chapter.verses:
                marker = f"{chapter.number}:{verse.verse}"
                if verse.verse_end != verse.verse:
                    marker += f"-{verse.verse_end}"
                verse_text = verse.text
                quote_reference = f"{code} {chapter.number}:{verse.verse}"
                if quote_reference in onen_quotes:
                    if verse.verse_end != verse.verse:
                        raise ValueError(f"Merged ONEN quotation unit: {quote_reference}")
                    quote = onen_quotes[quote_reference]
                    verse_text = mark_quote_divine_names(
                        verse_text, quote["onenBwanaCount"], quote["onenSelect"], quote_reference)
                    seen_onen_quotes.add(quote_reference)
                bullets.append(f"{verse_text} ({marker}).")
            story = {
                "id": english_story_id,
                "title": f"{names[code]} {chapter.number}",
                "refs": [f"{names[code]} {chapter.number}:1-{last}"],
                "summaryBullets": bullets,
                "headings": headings[code][chapter.number],
            }
            if chapter.superscription:
                story["superscription"] = chapter.superscription
            stories.append(story)
        files[Path("books") / collection / "sw" / f"{book_id}.json"] = json_bytes({
            "id": book_id, "title": names[code], "stories": stories,
        })
        overlay = {
            "schemaVersion": 2,
            "editionId": "suv1952",
            "language": "sw",
            "collection": collection,
            "bookId": book_id,
            "sourceBookCode": code,
            "coverage": "full",
            "chapters": suv_chapters,
        }
        files[Path("books/editions/sw/suv1952") / collection / f"{book_id}.json"] = json_bytes(overlay)
        indices[collection].append([book_id, names[code]])
        source_units = sum(len(c.verses) for c in source_book.chapters)
        suv_units = sum(len(c["verses"]) for c in suv_chapters)
        totals["books"] += 1
        totals["chapters"] += len(source_book.chapters)
        totals["onenVerseUnits"] += source_units
        totals["suvVerseUnits"] += suv_units
        manifest_books.append({
            "collection": collection, "bookId": book_id, "sourceBookCode": code,
            "coverage": "full", "chapters": len(suv_chapters), "verseUnits": suv_units,
        })
    for collection, index in indices.items():
        files[Path("books") / collection / "sw/_index.json"] = json_bytes(index)
    files[Path("scripture_sources/swhonen.json")] = json_bytes({
        "editionId": "onen2015",
        "displayName": "Biblica Open Kiswahili Contemporary Version 2015",
        "language": "sw",
        "source": ONEN_SOURCE,
        "copyright": "Copyright (c) 1984, 1989, 2009, 2015 Biblica, Inc.",
        "license": "Creative Commons Attribution-ShareAlike 4.0 International",
        "licenseUrl": ONEN_LICENSE,
        "archiveSha256": onen_hash,
            "changes": "Converted USFM to app JSON; publisher footnotes and cross-references excluded; native wj and nd spans represented by app display tags. Source table and paragraph continuation text was preserved. The source's unnumbered Nehemiah 8:1 preface was moved from before its chapter marker into verse 8:1. An editorial ledger marks 48 divine-name occurrences in 46 explicit New Testament quotations where the cited ONEN Old Testament passage has native nd markup.",
        "sourceFileSha256": file_hashes,
        "totals": totals,
    })
    files[Path("books/editions/sw/suv1952/_manifest.json")] = json_bytes({
        "schemaVersion": 2,
        "editionId": "suv1952",
        "displayName": "Swahili Union Version 1952",
        "language": "sw",
        "source": {
            "title": "Swahili SUV (Maandiko Matakatifu ya Mungu Yaitwayo Biblia)",
            "file": "SwahiliSUV1952Bible.xml",
            "format": "xml",
            "fileSha256": suv_hash,
            "sourceNotice": "Swahili Union Version (SUV): Bible Society of Tanzania and Bible Society of Kenya, 1952.",
            "redistributionPermission": "Not established by the pinned XML. An earlier inspection of this pathname showed a rights-reserved notice; the current pinned file does not contain a license.",
        },
        "totals": totals,
        "books": manifest_books,
    })
    if set(onen_quotes) != seen_onen_quotes:
        raise ValueError(f"ONEN quotation references not found: {sorted(set(onen_quotes)-seen_onen_quotes)}")
    return files, totals


def write_new(files: dict[Path, bytes], out: Path) -> None:
    collisions = [out / path for path in files if (out / path).exists()]
    if collisions:
        raise FileExistsError(f"Refusing to overwrite {len(collisions)} existing files, first: {collisions[0]}")
    for relative, data in files.items():
        target = out / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--onen", type=Path, required=True)
    parser.add_argument("--suv", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=ROOT / "shared" / "assets")
    args = parser.parse_args()
    onen_hash = check_hash(args.onen, ONEN_SHA256)
    suv_hash = check_hash(args.suv, SUV_SHA256)
    onen, names, headings, file_hashes = parse_onen(args.onen)
    suv = parse_suv(args.suv)
    files, totals = build_files(onen, names, headings, file_hashes, suv, onen_hash, suv_hash)
    write_new(files, args.out)
    print(f"Wrote {len(files)} new app assets: {totals}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
