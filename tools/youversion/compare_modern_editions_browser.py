#!/usr/bin/env python3
"""Read-only Bible.com comparison through an already-running Chromium CDP port.

Start Chromium yourself with --remote-debugging-port=9224. This command opens
one temporary tab, reads public chapter DOM, and closes that tab. It never
persists publisher text. A report contains only references, counts, and hashes.
The public page may expose different text from the YouVersion licensed API;
this tool claims parity only with the page actually rendered in the browser.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import re
import sys
import time
import unicodedata
from typing import Any, TextIO
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen

from bs4 import BeautifulSoup, NavigableString, Tag

sys.path.insert(0, str(Path(__file__).resolve().parent))
from compare_modern_editions import (  # noqa: E402
    BOOKS, DIVINE_TAG, EDITIONS, REFERENCE, ValidationError, _fingerprint,
    load_local_book, parse_semantic_text, sha256_text, utc_now,
)
from compare_biblecom import BLOCK_BOUNDARY, INLINE_BOUNDARY, normalize_boundary_whitespace  # noqa: E402
from repair_traditional_nt_readings import (  # noqa: E402
    BOOK_LABEL, SPANISH_RVR1960, variant_matches,
)
from reviewed_arabic_repairs import (  # noqa: E402
    REVIEWED_ARABIC_CHAPTERS, chapter_dn_rows, digest_json, digest_text,
)

ROOT = Path(__file__).resolve().parents[2]
USFM = re.compile(r"^([1-3]?[A-Z]{2,3})\.(\d+)\.(\d+)(?:-(?:(?:[1-3]?[A-Z]{2,3})\.(\d+)\.)?(\d+))?$")
SKIP_CLASSES = frozenset({
    "label", "verse-number", "verse-num", "footnote", "footnotes", "note",
    "notes", "crossreference", "cross-reference", "crossref", "xref",
    "audio", "chapter-number", "chapter-label", "yv-vlbl", "yv-clbl",
})
HEADING_CLASSES = frozenset({"heading", "section-heading", "s1", "s2", "s3", "ms1", "ms2", "d"})
MAX_HTML = 6_000_000
MAX_SNAPSHOT_RECORDS = 2_000
NVI_SOURCE_OMISSIONS = frozenset({
    ("MAT", 17, 21), ("MAT", 18, 11), ("MAT", 23, 14),
    ("MRK", 7, 16), ("MRK", 9, 44), ("MRK", 9, 46),
    ("MRK", 11, 26), ("MRK", 15, 28),
    ("LUK", 17, 36), ("LUK", 23, 17),
    ("JHN", 5, 4),
    ("ACT", 8, 37), ("ACT", 15, 34), ("ACT", 24, 7), ("ACT", 28, 29),
    ("ROM", 16, 24),
})
SAB_LUK7_DEFECT = {
    "recordsSha256": "c0a0efe580365e4a4a70e3bb2f31730c4e68ee872491b585750df91f772e124b",
    "emptyRecordsSha256": "2b395bc266df4ff474b5d4e64bb23d91c00bac6134d608fcc445ed774e7f9b27",
    "localStorySha256": "d2854df21f8f5303dfecd6d2945f1e28871e8fab7b9057eee8d17f490d6ddf3d",
    "localVersePrefixSha256": "71dd0d1d776d6b9c87d421c37b2bddaf5049f33eb108442f6825e03e1b8e9f6a",
    "sourceRangesSha256": "d981eb16490ee6414564f9ab7362f87e9c5b73fc623bf70c12368a52169ab182",
    "sourceTextSha256": "37b076498af5a6ff193e3416c0dc0d60e5812ba20506b350c15e33a3b02c6372",
    "sourceHeadingsSha256": "be81536c7c2da55669f66d0988b521a37c11c51ee3f732565c2c6bcbed4dc059",
}
SAB_LUK7_DEFECT_CODE = "locked_sab_luk_7_15_empty_native_range"
SAB_LUK7_FALLBACK_SOURCE = {
    "publisher": "International Sharif Bible Society",
    "url": "https://www.kitabsharif.org/sites/www.kitabsharif.org/files/bshart%20lwqa_0.pdf",
    "reference": "LUK.7.15",
    "verseTextSha256": SAB_LUK7_DEFECT["localVersePrefixSha256"],
}


class BrowserAuditError(RuntimeError):
    """Browser connection, page identity, or DOM structure is not auditable."""


def _canonical_json_sha256(value: object) -> str:
    try:
        rendered = json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError) as exc:
        raise BrowserAuditError("browser evidence cannot be canonically hashed") from exc
    return hashlib.sha256(rendered.encode("utf-8")).hexdigest()


def _blocked_result(exc: BaseException) -> dict[str, str]:
    result = {"status": "blocked", "blocker": type(exc).__name__}
    if type(exc) is BrowserAuditError:
        code = re.sub(r"[^a-z0-9]+", "_", str(exc).lower()).strip("_")
        if code:
            result["errorCode"] = code[:120]
    return result


@dataclass(frozen=True)
class BrowserChapter:
    ranges: dict[tuple[int, int], str]
    headings: tuple[tuple[int, str], ...]
    headings_available: bool
    divine_markup: dict[tuple[int, int], str] = field(default_factory=dict)
    fragment_counts: dict[tuple[int, int], int] = field(default_factory=dict)


def _classes(tag: Tag) -> set[str]:
    return {str(part).lower() for part in tag.get("class", ())}


def _is_skipped(tag: Tag) -> bool:
    classes = _classes(tag)
    return bool(classes & SKIP_CLASSES or any("__note" in part or "__label" in part for part in classes)
                or tag.name in {"script", "style", "button", "svg", "sup"}
                or tag.get("aria-hidden") == "true" or tag.has_attr("hidden"))


def _node_text(node: Tag, *, normalize: bool = True, divine_markup: bool = False) -> str:
    """Preserve literal intra-text whitespace; normalize only markup boundaries."""
    pieces: list[str] = []

    def walk(item: Tag | NavigableString) -> None:
        if isinstance(item, NavigableString):
            pieces.append(str(item))
            return
        if _is_skipped(item):
            return
        if item.name == "br":
            pieces.append(BLOCK_BOUNDARY)
            return
        pieces.append(INLINE_BOUNDARY)
        divine = divine_markup and any("__nd" in part for part in _classes(item))
        if divine:
            pieces.append("[DN]")
        for child in item.children:
            if isinstance(child, (Tag, NavigableString)):
                walk(child)
        if divine:
            pieces.append("[/DN]")
        pieces.append(INLINE_BOUNDARY)

    walk(node)
    raw = "".join(pieces)
    return normalize_boundary_whitespace(raw) if normalize else raw


def _narrow_divine_markup_outer_punctuation(value: str) -> str:
    """Keep publisher ``nd`` proof on its lexical core, not wrapper punctuation."""
    output: list[str] = []
    position = 0
    matches = list(DIVINE_TAG.finditer(value))
    if len(matches) % 2:
        raise BrowserAuditError("unbalanced rendered divine-name markup")
    for index in range(0, len(matches), 2):
        opening, closing = matches[index], matches[index + 1]
        if opening.group() != "[DN]" or closing.group() != "[/DN]":
            raise BrowserAuditError("crossed rendered divine-name markup")
        output.append(value[position:opening.start()])
        inner = value[opening.end():closing.start()]

        def outer(character: str) -> bool:
            return character.isspace() or unicodedata.category(character)[0] in {"P", "Z"}

        start, end = 0, len(inner)
        while start < end and outer(inner[start]):
            start += 1
        while end > start and outer(inner[end - 1]):
            end -= 1
        if start == end:
            raise BrowserAuditError("empty rendered divine-name lexeme")
        output.extend((inner[:start], "[DN]", inner[start:end], "[/DN]", inner[end:]))
        position = closing.end()
    output.append(value[position:])
    return "".join(output)


def _range_from_usfm(value: str, book: str, chapter: int) -> tuple[int, int] | None:
    if "+" in value:
        parts = value.upper().split("+")
        keys = [_range_from_usfm(part, book, chapter) for part in parts]
        if any(key is None or key[0] != key[1] for key in keys):
            raise BrowserAuditError("invalid plus-joined native range")
        numbers = [key[0] for key in keys if key is not None]
        if numbers != list(range(numbers[0], numbers[-1] + 1)):
            raise BrowserAuditError("nonconsecutive plus-joined native range")
        return numbers[0], numbers[-1]
    normalized = value.upper()
    match = USFM.fullmatch(normalized)
    if match is None:
        # Verse-part suffixes such as 1a/1b are source-native units that this
        # app must never silently collapse or discard. Ignore unrelated DOM
        # references, but fail closed when an unparseable value targets the
        # chapter currently being audited.
        if normalized.startswith(f"{book}.{chapter}."):
            raise BrowserAuditError("unparseable same-chapter native range")
        return None
    if match[1] != book or int(match[2]) != chapter:
        return None
    start = int(match[3])
    end = int(match[5] or start)
    if match[4] and int(match[4]) != chapter:
        raise BrowserAuditError("cross-chapter native range")
    if start < 1 or end < start:
        raise BrowserAuditError("invalid native range")
    return start, end


def _is_heading(tag: Tag) -> bool:
    classes = _classes(tag)
    return bool(classes & HEADING_CLASSES or any("__heading" in part for part in classes))


def _is_verse(tag: Tag) -> bool:
    classes = _classes(tag)
    return bool("verse" in classes or any("__verse" in part for part in classes))


def _has_verse_label(node: Tag) -> bool:
    return any("label" in _classes(child) or any("__label" in part for part in _classes(child))
               for child in node.find_all(True))


def extract_browser_records(records: list[dict[str, str]], book: str, chapter: int) -> BrowserChapter:
    """Parse an ordered in-memory DOM snapshot from CDP or browser automation."""
    fragments: dict[tuple[int, int], list[str]] = {}
    divine_fragments: dict[tuple[int, int], list[str]] = {}
    headings: list[tuple[int, str]] = []
    pending: list[str] = []
    last_end = 0
    last_key: tuple[int, int] | None = None
    saw_heading_record = False
    for record in records:
        kind = record.get("kind")
        html = record.get("html")
        if not isinstance(html, str) or len(html) > MAX_HTML:
            raise BrowserAuditError("invalid browser snapshot fragment")
        node = BeautifulSoup(html, "html.parser").find(True)
        if node is None:
            raise BrowserAuditError("empty browser snapshot fragment")
        if kind == "heading":
            # Bible.com also emits heading-styled layout fragments containing
            # only whitespace or punctuation. These are not section titles.
            # Trim only edge spacing; retain all interior typography verbatim.
            label = _node_text(node).strip()
            if any(unicodedata.category(character)[0] in {"L", "N"} for character in label):
                saw_heading_record = True
                pending.append(label)
            continue
        if kind != "verse":
            raise BrowserAuditError("unknown browser snapshot record kind")
        key = _range_from_usfm(record.get("usfm", ""), book, chapter)
        if key is None:
            continue
        if key != last_key and key[0] <= last_end:
            raise BrowserAuditError("duplicate, overlapping, or unordered native ranges")
        if key != last_key:
            last_end = key[1]
            fragments[key] = []
            divine_fragments[key] = []
        if pending and _has_verse_label(node):
            headings.extend((key[0], label) for label in pending)
            pending.clear()
        # Walk the full verse. Divine-name wrappers can sit between direct
        # content siblings; the skip set removes labels/notes without dropping
        # nested Scripture inside wrappers such as __nd.
        fragments[key].append(_node_text(node, normalize=False))
        divine_fragments[key].append(_node_text(node, normalize=False, divine_markup=True))
        last_key = key
    if not fragments:
        raise BrowserAuditError("no matching data-usfm verse nodes")
    # A rendered chapter can expose the next section title after its final
    # labelled verse. It has no anchor in this chapter, so exclude it rather
    # than attaching it to the preceding Scripture or blocking the audit.
    pending.clear()
    ranges = {key: normalize_boundary_whitespace(BLOCK_BOUNDARY.join(parts)) for key, parts in fragments.items()}
    divine = {
        key: _narrow_divine_markup_outer_punctuation(
            normalize_boundary_whitespace(BLOCK_BOUNDARY.join(parts))
        )
        for key, parts in divine_fragments.items()
    }
    if any(not text for text in ranges.values()):
        raise BrowserAuditError("empty native verse range")
    return BrowserChapter(
        ranges,
        tuple(headings),
        saw_heading_record,
        divine,
        {key: len(parts) for key, parts in fragments.items()},
    )


def _empty_snapshot_ranges(records: list[dict[str, str]], book: str,
                           chapter: int) -> tuple[tuple[int, int], ...]:
    """Classify empty source units without weakening the shared extractor."""
    fragments: dict[tuple[int, int], list[str]] = {}
    last_end = 0
    last_key: tuple[int, int] | None = None
    for record in records:
        kind = record.get("kind")
        html = record.get("html")
        if not isinstance(html, str) or len(html) > MAX_HTML:
            raise BrowserAuditError("invalid browser snapshot fragment")
        node = BeautifulSoup(html, "html.parser").find(True)
        if node is None:
            raise BrowserAuditError("empty browser snapshot fragment")
        if kind == "heading":
            continue
        if kind != "verse":
            raise BrowserAuditError("unknown browser snapshot record kind")
        key = _range_from_usfm(record.get("usfm", ""), book, chapter)
        if key is None:
            continue
        if key != last_key and key[0] <= last_end:
            raise BrowserAuditError("duplicate, overlapping, or unordered native ranges")
        if key != last_key:
            last_end = key[1]
            fragments[key] = []
        fragments[key].append(_node_text(node, normalize=False))
        last_key = key
    return tuple(sorted(
        key for key, parts in fragments.items()
        if not normalize_boundary_whitespace(BLOCK_BOUNDARY.join(parts))
    ))


def _validate_nvi_omission_policy(book: str, chapter: int,
                                  empty_ranges: tuple[tuple[int, int], ...],
                                  books_root: Path) -> None:
    lookup = {code: (collection, name) for code, collection, name in BOOKS}
    collection, name = lookup[book]
    if collection != "new_testament" or any(
        start != end or (book, chapter, start) not in NVI_SOURCE_OMISSIONS
        for start, end in empty_ranges
    ):
        raise BrowserAuditError("unapproved NVI empty native verse range")

    path = books_root / collection / "es" / f"{name}.json"
    local_chapter = load_local_book(path).get(chapter)
    if local_chapter is None:
        raise BrowserAuditError("NVI omission local chapter is missing")
    for verse, _end in empty_ranges:
        if any(start <= verse <= end for start, end in local_chapter):
            raise BrowserAuditError("NVI omission local body coverage mismatch")

    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    stories = [story for story in payload.get("stories", ())
               if isinstance(story, dict) and story.get("id") == f"{name}-{chapter}"]
    if len(stories) != 1 or not isinstance(stories[0].get("manuscriptVariants"), list):
        raise BrowserAuditError("NVI omission manuscript variant structure mismatch")
    variants = stories[0]["manuscriptVariants"]
    for verse, _end in empty_ranges:
        expected = {
            "ref": f"RVR1960 {BOOK_LABEL[name]} {chapter}:{verse}",
            "text": SPANISH_RVR1960[(name, chapter, verse)],
        }
        matching_reference = [
            value for value in variants
            if isinstance(value, dict) and variant_matches(value, chapter, verse)
        ]
        if matching_reference != [expected]:
            raise BrowserAuditError("NVI omission manuscript variant mismatch")


def _classify_locked_sab_luke7_defect(
        language: str, book: str, chapter: int,
        records: list[dict[str, str]], books_root: Path,
        empty_ranges: tuple[tuple[int, int], ...],
        ) -> dict[str, Any] | None:
    """Classify one exact publisher defect without claiming source parity."""
    if (language, book, chapter) != ("ar", "LUK", 7):
        return None
    if empty_ranges != ((15, 15),):
        raise BrowserAuditError("locked SAB Luke 7 empty-range identity changed")
    empty_records = [
        record for record in records
        if record.get("kind") == "verse"
        and _range_from_usfm(record.get("usfm", ""), book, chapter) == (15, 15)
    ]
    if (_canonical_json_sha256(records) != SAB_LUK7_DEFECT["recordsSha256"]
            or _canonical_json_sha256(empty_records) != SAB_LUK7_DEFECT["emptyRecordsSha256"]):
        raise BrowserAuditError("locked SAB Luke 7 rendered-source hash changed")

    collection, name = next(
        (candidate_collection, candidate_name)
        for candidate_code, candidate_collection, candidate_name in BOOKS
        if candidate_code == book
    )
    path = books_root / collection / language / f"{name}.json"
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    root_id = payload.get("id") if isinstance(payload, dict) else None
    stories = payload.get("stories") if isinstance(payload, dict) else None
    if not isinstance(root_id, str) or not isinstance(stories, list):
        raise BrowserAuditError("locked SAB Luke 7 local story is unavailable")
    matches = [
        story for story in stories
        if isinstance(story, dict) and story.get("id") == f"{root_id}-{chapter}"
    ]
    if (len(matches) != 1
            or _canonical_json_sha256(matches[0]) != SAB_LUK7_DEFECT["localStorySha256"]):
        raise BrowserAuditError("locked SAB Luke 7 local story hash changed")
    verse_prefixes: list[str] = []
    for bullet in matches[0].get("summaryBullets", ()):
        if not isinstance(bullet, str):
            raise BrowserAuditError("locked SAB Luke 7 local Scripture structure changed")
        marker = REFERENCE.search(bullet)
        if marker is not None and tuple(map(int, (marker[1], marker[2], marker[3] or marker[2]))) == (7, 15, 15):
            verse_prefixes.append(bullet[:marker.start()])
    if (len(verse_prefixes) != 1
            or sha256_text(verse_prefixes[0]).lower() != SAB_LUK7_DEFECT["localVersePrefixSha256"]):
        raise BrowserAuditError("locked SAB Luke 7 local verse 15 changed")
    if sha256_text(verse_prefixes[0]).lower() != SAB_LUK7_FALLBACK_SOURCE["verseTextSha256"]:
        raise BrowserAuditError("locked SAB Luke 7 publisher verification changed")

    filtered = [record for record in records if record not in empty_records]
    source = extract_browser_records(filtered, book, chapter)
    source_headings = [
        {"beforeVerse": anchor, "text": text}
        for anchor, text in normalize_source_headings(source.headings)
    ]
    if (_canonical_json_sha256(list(source.ranges)) != SAB_LUK7_DEFECT["sourceRangesSha256"]
            or _canonical_json_sha256(list(source.ranges.items())) != SAB_LUK7_DEFECT["sourceTextSha256"]
            or _canonical_json_sha256(source_headings) != SAB_LUK7_DEFECT["sourceHeadingsSha256"]):
        raise BrowserAuditError("locked SAB Luke 7 extracted-source hash changed")
    compared = compare_source_chapter(language, book, chapter, books_root, source)
    expected_findings = {
        ("local_range_only", "LUK.7.15-15"),
        ("heading_text_mismatch", "LUK.7.1#1"),
        ("heading_text_mismatch", "LUK.7.11#2"),
        ("heading_text_mismatch", "LUK.7.18#3"),
        ("heading_text_mismatch", "LUK.7.36#4"),
    }
    actual_findings = {
        (finding.get("kind"), finding.get("reference"))
        for finding in compared.get("findings", ())
        if isinstance(finding, dict)
    }
    if (compared.get("status") != "mismatch"
            or actual_findings != expected_findings
            or len(compared.get("findings", ())) != len(expected_findings)):
        raise BrowserAuditError("locked SAB Luke 7 nondefect comparison changed")
    return {
        **compared,
        "status": "source_defect",
        "sourceParityClaimed": False,
        "sourceDefectCode": SAB_LUK7_DEFECT_CODE,
        "sourceDefectRanges": ["LUK.7.15"],
        "localSourceDefectVersePreserved": True,
        "fallbackSourceVerified": True,
        "fallbackSource": SAB_LUK7_FALLBACK_SOURCE,
        "sourceOmissions": 0,
        "emptyRanges": 1,
    }


def _filter_read_only_source_omissions(language: str, book: str, chapter: int,
                                       records: list[dict[str, str]], books_root: Path
                                       ) -> tuple[list[dict[str, str]], int, int]:
    empty_ranges = _empty_snapshot_ranges(records, book, chapter)
    if not empty_ranges:
        return records, 0, 0
    edition = EDITIONS[language]
    if language != "es" or edition.bible_id != 128 or edition.abbreviation != "NVI":
        raise BrowserAuditError("unresolved malformed-source empty native verse range")
    _validate_nvi_omission_policy(book, chapter, empty_ranges, books_root)
    excluded = set(empty_ranges)
    filtered: list[dict[str, str]] = []
    for record in records:
        if record.get("kind") == "verse":
            key = _range_from_usfm(record.get("usfm", ""), book, chapter)
            if key in excluded:
                continue
        filtered.append(record)
    return filtered, len(empty_ranges), len(empty_ranges)


def extract_browser_chapter(page_html: str, book: str, chapter: int) -> BrowserChapter:
    """Extract only native data-usfm verse units from the rendered chapter DOM.

    Reject duplicate/overlapping ranges. Never split bridged or a/b verses.
    Headings are reported only when the page identifies them structurally.
    """
    if len(page_html) > MAX_HTML:
        raise BrowserAuditError("rendered page exceeds safety limit")
    soup = BeautifulSoup(page_html, "html.parser")
    candidates = [node for node in soup.find_all(True) if _is_heading(node) or node.has_attr("data-usfm") and _is_verse(node)]
    verse_nodes = [node for node in candidates if node.has_attr("data-usfm") and _is_verse(node)]
    # A wrapper and child may repeat one reference. Keep the innermost verse
    # element; disjoint fragments with the same reference remain separate.
    records: list[dict[str, str]] = []
    for node in candidates:
        if node.has_attr("data-usfm") and _is_verse(node):
            if any(node in child.parents and node.get("data-usfm") == child.get("data-usfm") for child in verse_nodes if child is not node):
                continue
            records.append({"kind": "verse", "usfm": str(node["data-usfm"]), "html": str(node)})
        elif _is_heading(node):
            if any(_is_heading(parent) for parent in node.parents if isinstance(parent, Tag)):
                continue
            records.append({"kind": "heading", "html": str(node)})
    return extract_browser_records(records, book, chapter)


def local_headings(path: Path, chapter: int) -> tuple[tuple[int, str], ...]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    result: list[tuple[int, str]] = []
    for story in payload["stories"]:
        bullets = story.get("summaryBullets", [])
        if not bullets:
            continue
        matches = [REFERENCE.search(item) for item in bullets]
        if not all(matches) or int(matches[0][1]) != chapter:
            continue
        for item in story.get("headings", []):
            text = normalize_boundary_whitespace(DIVINE_TAG.sub("", item["text"]))
            result.append((int(item["beforeVerse"]), text))
    return tuple(result)


def local_superscription(path: Path, chapter: int) -> tuple[str, str] | None:
    """Return one chapter's separately stored superscription as plain/DN text."""
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    first_story_start: int | None = None
    values: list[tuple[int, object]] = []
    for story in payload["stories"]:
        bullets = story.get("summaryBullets", [])
        if not bullets:
            continue
        references = [REFERENCE.search(item) for item in bullets]
        if not all(references) or int(references[0][1]) != chapter:
            continue
        start = int(references[0][2])
        first_story_start = start if first_story_start is None else min(first_story_start, start)
        if "superscription" in story:
            values.append((start, story["superscription"]))
    if first_story_start is None:
        raise ValidationError(f"local chapter story identity mismatch: {path} chapter {chapter}")
    if not values:
        return None
    if len(values) != 1 or values[0][0] != first_story_start:
        raise ValidationError(f"local superscription story identity mismatch: {path} chapter {chapter}")
    value = values[0][1]
    if not isinstance(value, str) or not value.strip():
        raise ValidationError(f"invalid local superscription: {path} chapter {chapter}")
    marked = parse_semantic_text(value, f"local {path.name} {chapter} superscription").plain
    # parse_semantic_text intentionally preserves [DN] for the marker-aware
    # comparison below while removing [J]/[ADD]. Validate the [DN] structure
    # before exposing either representation.
    _dn_segments(marked)
    plain = normalize_boundary_whitespace(DIVINE_TAG.sub("", marked))
    return plain, marked


def local_divine_markup(path: Path, chapter: int) -> dict[tuple[int, int], str]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    result: dict[tuple[int, int], str] = {}
    for story in payload["stories"]:
        for bullet in story.get("summaryBullets", []):
            match = REFERENCE.search(bullet)
            if match is None or int(match[1]) != chapter:
                continue
            key = (int(match[2]), int(match[3] or match[2]))
            result[key] = parse_semantic_text(bullet[: match.start()], f"local {path.name} {chapter}:{key[0]}").plain
    return result


def _dn_segments(markup: str) -> tuple[tuple[bool, str], ...]:
    """Split validated DN markup into exact outside/inside spans."""
    pieces: list[tuple[bool, str]] = []
    current: list[str] = []
    inside = False
    previous = 0
    for match in DIVINE_TAG.finditer(markup):
        current.append(markup[previous : match.start()])
        closing = match.group() == "[/DN]"
        if closing != inside:
            raise BrowserAuditError("unbalanced divine-name markup")
        pieces.append((inside, "".join(current)))
        current = []
        inside = not inside
        previous = match.end()
    if inside:
        raise BrowserAuditError("unclosed divine-name markup")
    current.append(markup[previous:])
    pieces.append((False, "".join(current)))
    return tuple(pieces)


def _dn_difference(local_markup: str, source_markup: str) -> str | None:
    """Return boundary, wording, or presentation difference, if any."""
    local = _dn_segments(local_markup)
    source = _dn_segments(source_markup)
    if tuple(kind for kind, _ in local) != tuple(kind for kind, _ in source):
        return "divine_name_boundary_mismatch"
    local_offsets: list[int] = []
    source_offsets: list[int] = []
    for spans, offsets in ((local, local_offsets), (source, source_offsets)):
        position = 0
        for inside, content in spans:
            if inside:
                offsets.append(position)
            # Case folding may expand one code point (for example, German ß).
            # Compare marker positions in folded text, then compare literal
            # outside text below without relaxing any wording.
            position += len(content.casefold())
            if inside:
                offsets.append(position)
    if local_offsets != source_offsets:
        return "divine_name_boundary_mismatch"
    presentation = False
    for (inside, local_text), (_source_inside, source_text) in zip(local, source):
        if not inside and local_text != source_text:
            return "text_mismatch"
        if inside:
            if local_text.casefold() != source_text.casefold():
                return "text_mismatch"
            if local_text != source_text:
                presentation = True
    return "divine_name_presentation_difference" if presentation else None


def _space_separator_fold(value: str) -> str:
    """Map each Unicode Zs character to one ASCII space, without collapsing."""
    return "".join(" " if unicodedata.category(character) == "Zs" else character for character in value)


def _align_untagged_divine_names(local_text: str, source_markup: str) -> tuple[tuple[int, int, str, str], ...] | None:
    """Align untagged local text to source DN spans using exact outside text."""
    segments = _dn_segments(source_markup)
    if not any(inside for inside, _ in segments):
        return () if local_text == source_markup else None
    cursor = 0
    spans: list[tuple[int, int, str, str]] = []
    for index, (inside, text) in enumerate(segments):
        if not inside:
            if not local_text.startswith(text, cursor):
                return None
            cursor += len(text)
            continue
        next_outside = segments[index + 1][1]
        candidates = [
            end for end in range(cursor + 1, min(len(local_text), cursor + max(8, len(text) * 3)) + 1)
            if local_text[cursor:end].casefold() == text.casefold()
            and local_text.startswith(next_outside, end)
        ]
        if len(candidates) != 1:
            return None
        end = candidates[0]
        spans.append((cursor, end, local_text[cursor:end], text))
        cursor = end
    return tuple(spans) if cursor == len(local_text) else None


# Conservative literal branches of ScriptureRefs.kt highlightTraditionalSegment.
# Context-dependent titles not listed here produce an explicit coverage finding.
RUNTIME_DN_FORMS: dict[str, frozenset[str]] = {
    "en": frozenset({"LORD", "GOD", "JEHOVAH", "JAH"}),
    "de": frozenset({"HERR", "HERRN", "Jahwe", "Jehova"}),
    "es": frozenset({"SEÑOR", "Jehová", "Yahveh", "Yahvé"}),
    "fr": frozenset({"ÉTERNEL", "Éternel", "SEIGNEUR", "Yahvé", "Yahveh"}),
    "it": frozenset({"SIGNORE", "Geova", "Signore"}),
    "pt": frozenset({"SENHOR", "Javé", "Jeová", "Senhor"}),
    "ru": frozenset({"ГОСПОДЬ", "ГОСПОДА", "ГОСПОДУ", "ГОСПОДОМ", "Яхве", "Иегова"}),
    "hi": frozenset({"यहोवा", "याहवे"}),
    "ja": frozenset({"ヤハウェ", "ヱホバ", "エホバ"}),
    "ko": frozenset({"여호와", "야훼"}),
    "zh-Hans": frozenset({"耶和华", "雅威"}),
    "zh-Hant": frozenset({"耶和華", "雅威"}),
}
LATIN_EXPLICIT_DN = frozenset({"yahweh", "yhwh", "yhvh", "yahuah", "yahveh", "jehovah", "jah"})


def _runtime_covers_untagged_dn(language: str, text: str, start: int, end: int, collection: str) -> bool:
    token = text[start:end]
    def word_edge() -> bool:
        before = text[start - 1] if start else ""
        after = text[end] if end < len(text) else ""
        return all(not character or not (character.isalpha() or unicodedata.category(character) == "Mn")
                   for character in (before, after))

    if token.casefold() in LATIN_EXPLICIT_DN and word_edge():
        return True
    if token in RUNTIME_DN_FORMS.get(language, ()):
        if token in {"Signore", "Senhor"} and collection != "old_testament":
            return False
        if language == "en" and token in {"LORD", "GOD"} and collection != "old_testament":
            return False
        if language in {"en", "de", "es", "fr", "it", "pt", "ru", "hi"} and not word_edge():
            return False
        return True
    if language in {"zh-Hans", "zh-Hant"} and token == "上主":
        return collection == "old_testament" and bool(re.match(r"(?:的|之)(?:天使|使者)", text[end:]))
    if language == "ru" and collection == "old_testament" and word_edge():
        return bool(re.fullmatch(r"Господ(?:ь|а|у|ом|е|ень|нее|него|нему|ним|нем|них|няя|нюю|ней|ня|ню|не|ни)", token))
    if language == "ar" and word_edge():
        base = "".join(character for character in token if unicodedata.category(character) != "Mn")
        return base == "يهوه" or collection == "old_testament" and base == "الرب"
    if language == "ja" and token == "主" and collection == "old_testament":
        disallowed_prefixes = ("持ち", "救い", "ご", "領", "君", "家", "店", "船", "地", "当", "雇い", "造り", "創造", "所有")
        if any(text[:start].endswith(prefix) for prefix in disallowed_prefixes):
            return False
        return bool(re.match(r"(?:は|が|を|に|へ|の|と|よ|から|より|も|こそ|で|だ|です|なる|[、。，．！!？?\s]|$)", text[end:]))
    if language == "ko" and token == "주님":
        return start == 0 or not ('가' <= text[start - 1] <= '힣')
    if language == "ko" and token == "주" and collection == "old_testament":
        if start and '가' <= text[start - 1] <= '힣':
            return False
        if not re.match(r"(?:께서|께|가|를|와|여|에게|앞|의(?=\s|[,.!?;:，。！？；：]|$)|는|\s|[,.!?;:，。！？；：]|$)", text[end:]):
            return False
        if text[end:].startswith("는"):
            before = text[:start].rstrip()
            previous = re.search(r"[가-힣]+$", before)
            previous_word = previous.group() if previous else ""
            following = text[end + 1:].lstrip()
            return previous_word in {"나", "그러나"} or not previous_word and following.startswith(("자기", "복도"))
        return True
    if language == "fr" and token == "Seigneur" and collection == "old_testament":
        return bool(re.search(r"anges?\s+du\s+$", text[:start], re.I))
    if language == "es" and token == "Señor" and collection == "old_testament":
        return bool(re.search(r"ángel(?:es)?\s+del\s+$", text[:start], re.I))
    if language == "de" and token == "Herrn" and collection == "old_testament":
        return bool(re.search(r"Engel(?:n|s)?\s+des\s+$", text[:start], re.I))
    return False


def normalize_source_headings(lines: tuple[tuple[int, str], ...]) -> tuple[tuple[int, str], ...]:
    """Mirror app storage: consecutive same-anchor source lines join with LF."""
    objects: list[tuple[int, str]] = []
    for anchor, label in lines:
        if objects and objects[-1][0] == anchor:
            objects[-1] = (anchor, objects[-1][1] + "\n" + label)
        else:
            objects.append((anchor, label))
    return tuple(objects)


def _reviewed_arabic_chapter_context(
        language: str, code: str, chapter: int, path: Path,
        source: BrowserChapter) -> dict[str, Any] | None:
    """Prove the exact post-repair SAB chapter and rendered source state."""
    row = REVIEWED_ARABIC_CHAPTERS.get((language, code, chapter))
    if row is None or not source.headings_available:
        return None
    try:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
        root_id = payload.get("id")
        stories = payload.get("stories")
        if not isinstance(root_id, str) or not isinstance(stories, list):
            return None
        matches = [
            story for story in stories
            if isinstance(story, dict) and story.get("id") == f"{root_id}-{chapter}"
        ]
        if len(matches) != 1 or digest_json(matches[0]) != row["postStorySha256"]:
            return None
        ranges = list(source.ranges)
        if (digest_json(ranges) != row["sourceRangesSha256"]
                or digest_json(list(source.ranges.items())) != row["sourceTextSha256"]):
            return None
        headings = [
            {"beforeVerse": anchor, "text": text}
            for anchor, text in normalize_source_headings(source.headings)
        ]
        if digest_json(headings) != row["sourceHeadingsSha256"]:
            return None
        # The reviewed SAB pages expose no publisher nd wrappers for these
        # words.  If that changes, the ordinary publisher-markup path must
        # review the new evidence instead of using this exception.
        if any(source.divine_markup.get(unit, plain) != plain
               for unit, plain in source.ranges.items()):
            return None
        return row
    except (OSError, UnicodeError, ValueError, TypeError, KeyError):
        return None


def _reviewed_arabic_dn_presentation(
        *, language: str, code: str, chapter: int, unit: tuple[int, int],
        local_marked: str, source_plain: str, source_marked: str,
        chapter_context: dict[str, Any] | None,
        ) -> bool:
    """Accept one exact stored DN boundary absent from rendered SAB markup."""
    if chapter_context is None or source_marked != source_plain:
        return False
    row = chapter_dn_rows(language, code, chapter).get(unit)
    if row is None:
        return False
    if (digest_text(local_marked) != row["taggedPrefixSha256"]
            or digest_text(source_plain) != row["sourcePrefixSha256"]):
        return False
    try:
        local_segments = _dn_segments(local_marked)
    except BrowserAuditError:
        return False
    if "".join(text for _inside, text in local_segments) != source_plain:
        return False
    actual = tuple(text for inside, text in local_segments if inside)
    expected = tuple(lexeme for _start, _end, lexeme in row["spans"])
    return actual == expected


def validate_page_identity(page_url: str, page_title: str, bible_id: int, book: str, chapter: int) -> None:
    if not isinstance(page_url, str) or not isinstance(page_title, str) or not page_title.strip():
        raise BrowserAuditError("missing browser page identity")
    if re.search(r"client challenge|captcha|verify you are human", page_title, re.I):
        raise BrowserAuditError("browser received client challenge")
    parsed = urlsplit(page_url)
    if parsed.scheme != "https" or parsed.hostname not in {"www.bible.com", "bible.com"} or parsed.username or parsed.password:
        raise BrowserAuditError("browser page host is not Bible.com HTTPS")
    if parsed.port not in {None, 443}:
        raise BrowserAuditError("browser page uses unexpected port")
    parts = [part for part in parsed.path.split("/") if part]
    if len(parts) == 4 and re.fullmatch(r"[A-Za-z]{2}(?:-[A-Za-z]{2,4})?", parts[0]):
        parts = parts[1:]
    if len(parts) != 3 or parts[0] != "bible" or not parts[1].isdecimal() or int(parts[1]) != bible_id:
        raise BrowserAuditError("browser page Bible ID does not match selected edition")
    passage = re.fullmatch(r"([1-3]?[A-Za-z]{2,3})\.(\d+)(?:\.[A-Za-z0-9_-]+)?", parts[2])
    if not passage or passage[1].upper() != book or int(passage[2]) != chapter:
        raise BrowserAuditError("browser page passage does not match requested chapter")


class CdpTab:
    def __init__(self, endpoint: str, timeout: float) -> None:
        parsed = urlsplit(endpoint)
        if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
            raise BrowserAuditError("CDP endpoint must be loopback HTTP")
        self.endpoint = endpoint.rstrip("/")
        self.timeout = timeout
        self.ws: Any = None
        self.target_id: str | None = None
        self.next_id = 0

    def __enter__(self) -> "CdpTab":
        try:
            import websocket
            request = Request(self.endpoint + "/json/new?about:blank", method="PUT")
            with urlopen(request, timeout=self.timeout) as response:
                target = json.load(response)
            self.target_id = target["id"]
            self.ws = websocket.create_connection(target["webSocketDebuggerUrl"], timeout=self.timeout, suppress_origin=True)
            self.call("Page.enable")
            self.call("Runtime.enable")
            return self
        except Exception as exc:
            self.__exit__(None, None, None)
            raise BrowserAuditError("could not attach to local Chromium CDP endpoint") from exc

    def __exit__(self, *_unused: object) -> None:
        if self.ws is not None:
            self.ws.close()
            self.ws = None
        if self.target_id:
            try:
                with urlopen(Request(self.endpoint + "/json/close/" + quote(self.target_id), method="GET"), timeout=self.timeout):
                    pass
            except Exception:
                pass
            self.target_id = None

    def call(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        self.next_id += 1
        request_id = self.next_id
        self.ws.send(json.dumps({"id": request_id, "method": method, "params": params or {}}))
        while True:
            message = json.loads(self.ws.recv())
            if message.get("id") != request_id:
                continue
            if "error" in message:
                raise BrowserAuditError("CDP command failed")
            return message.get("result", {})

    def chapter_html(self, url: str, bible_id: int, book: str, chapter: int) -> str:
        self.call("Page.navigate", {"url": url})
        deadline = time.monotonic() + self.timeout
        needle = f"{book}.{chapter}."
        while time.monotonic() < deadline:
            response = self.call("Runtime.evaluate", {
                "expression": "JSON.stringify({title:document.title,url:location.href,html:document.body?document.body.outerHTML:''})",
                "returnByValue": True,
            })
            value = response.get("result", {}).get("value")
            if isinstance(value, str):
                page = json.loads(value)
                title = str(page.get("title", ""))
                html = str(page.get("html", ""))
                current = str(page.get("url", ""))
                if re.search(r"client challenge|captcha|verify you are human", title, re.I):
                    raise BrowserAuditError("browser received client challenge")
                if len(html) > MAX_HTML:
                    raise BrowserAuditError("rendered page exceeds safety limit")
                if needle in html.upper():
                    try:
                        validate_page_identity(current, title, bible_id, book, chapter)
                    except BrowserAuditError:
                        pass  # Navigation may still be replacing the previous page.
                    else:
                        return html
            time.sleep(0.4)
        raise BrowserAuditError("chapter DOM did not expose requested verse markers")


def compare_source_chapter(language: str, book_code: str, chapter: int, books_root: Path,
                           source: BrowserChapter) -> dict[str, Any]:
    edition = EDITIONS[language]
    lookup = {code: (collection, name) for code, collection, name in BOOKS}
    collection, name = lookup[book_code]
    path = books_root / collection / language / f"{name}.json"
    local = load_local_book(path).get(chapter)
    if local is None:
        raise ValidationError("requested local chapter is missing")
    local_dn = local_divine_markup(path, chapter)
    reviewed_arabic_context = _reviewed_arabic_chapter_context(
        language, book_code, chapter, path, source,
    )
    superscription = local_superscription(path, chapter)
    first_local_range = min(local)
    first_source_range = min(source.ranges)
    ref = f"{book_code}.{chapter}"
    findings: list[dict[str, Any]] = []
    presentation_differences = 0
    typography_differences = 0
    for key in sorted(set(local) - set(source.ranges)):
        findings.append({"kind": "local_range_only", "reference": f"{ref}.{key[0]}-{key[1]}"})
    for key in sorted(set(source.ranges) - set(local)):
        findings.append({"kind": "source_range_only", "reference": f"{ref}.{key[0]}-{key[1]}"})
    for key in sorted(set(local) & set(source.ranges)):
        local_text, source_text = local[key].plain, source.ranges[key]
        reference = f"{ref}.{key[0]}" + (f"-{key[1]}" if key[0] != key[1] else "")
        local_marked = local_dn.get(key, local_text)
        source_marked = source.divine_markup.get(key, source_text)
        # Some publisher Psalm pages assign both the superscription fragment
        # and the verse body fragment to the first native data-usfm value. The
        # app stores those two pieces separately. Recognize that representation
        # only when the rendered source actually supplied repeated fragments
        # for the first range and their combined plain text is an exact match.
        # This affects comparison only; never copy the superscription into the
        # verse bullet, where the reader would render it twice.
        if (
            superscription is not None
            and key == first_local_range == first_source_range
            and source.fragment_counts.get(key, 0) > 1
        ):
            superscription_plain, superscription_marked = superscription
            combined_text = normalize_boundary_whitespace(
                superscription_plain + BLOCK_BOUNDARY + local_text
            )
            if combined_text == source_text:
                local_text = combined_text
                local_marked = normalize_boundary_whitespace(
                    superscription_marked + BLOCK_BOUNDARY + local_marked
                )
        if not DIVINE_TAG.search(local_marked) and DIVINE_TAG.search(source_marked):
            aligned = _align_untagged_divine_names(local_text, source_marked)
            if aligned is None:
                folded = _align_untagged_divine_names(
                    _space_separator_fold(local_text), _space_separator_fold(source_marked)
                )
                if folded is None:
                    difference = "text_mismatch"
                elif not all(_runtime_covers_untagged_dn(language, local_text, start, end, collection)
                             for start, end, _local_part, _source_part in folded):
                    difference = "divine_name_runtime_coverage_gap"
                else:
                    difference = "typography_spacing_difference"
            elif not all(_runtime_covers_untagged_dn(language, local_text, start, end, collection)
                         for start, end, _local_part, _source_part in aligned):
                difference = "divine_name_runtime_coverage_gap"
            elif any(local_part != source_part for _start, _end, local_part, source_part in aligned):
                difference = "divine_name_presentation_difference"
            else:
                difference = None
        else:
            difference = _dn_difference(local_marked, source_marked)
            if difference == "text_mismatch" and (
                _dn_difference(_space_separator_fold(local_marked), _space_separator_fold(source_marked))
                in {None, "divine_name_presentation_difference"}
            ):
                difference = "typography_spacing_difference"
        if (difference == "divine_name_boundary_mismatch"
                and _reviewed_arabic_dn_presentation(
                    language=language, code=book_code, chapter=chapter, unit=key,
                    local_marked=local_marked, source_plain=source_text,
                    source_marked=source_marked,
                    chapter_context=reviewed_arabic_context,
                )):
            difference = "divine_name_presentation_difference"
        if difference == "divine_name_presentation_difference":
            presentation_differences += 1
        elif difference is not None:
            if difference == "typography_spacing_difference":
                typography_differences += 1
            findings.append(_fingerprint(reference, difference, local_marked, source_marked))
        elif local_text != source_text:
            # Defensive check: marker-aware equality may not obscure plain text.
            findings.append(_fingerprint(reference, "text_mismatch", local_text, source_text))
    if source.headings_available:
        expected = local_headings(path, chapter)
        source_headings = normalize_source_headings(source.headings)
        if tuple(position for position, _ in expected) != tuple(position for position, _ in source_headings):
            findings.append({"kind": "heading_placement_mismatch", "reference": ref,
                             "localAnchors": [position for position, _ in expected],
                             "sourceAnchors": [position for position, _ in source_headings]})
        else:
            for index, ((position, local_text), (_, source_text)) in enumerate(zip(expected, source_headings)):
                if local_text != source_text:
                    findings.append({"kind": "heading_text_mismatch", "reference": f"{ref}.{position}#{index + 1}",
                                     "localSha256": sha256_text(local_text), "sourceSha256": sha256_text(source_text),
                                     "localLength": len(local_text), "sourceLength": len(source_text)})
    return {"language": language, "bibleId": edition.bible_id, "reference": ref,
            "sourceUrl": f"https://www.bible.com/bible/{edition.bible_id}/{book_code}.{chapter}.{edition.abbreviation}",
            "status": "mismatch" if findings else "match",
            "localRanges": len(local), "sourceRanges": len(source.ranges),
            "rangesCompared": len(set(local) & set(source.ranges)),
            "divineNamePresentationDifferences": presentation_differences,
            "typographySpacingDifferences": typography_differences,
            "headingComparisonAvailable": source.headings_available,
            "localHeadings": len(local_headings(path, chapter)),
            "sourceHeadingLines": len(source.headings),
            "sourceHeadings": len(normalize_source_headings(source.headings)),
            "findings": findings}


def compare_chapter(tab: CdpTab, language: str, book_code: str, chapter: int, books_root: Path) -> dict[str, Any]:
    edition = EDITIONS[language]
    url = f"https://www.bible.com/bible/{edition.bible_id}/{book_code}.{chapter}.{edition.abbreviation}"
    source = extract_browser_chapter(tab.chapter_html(url, edition.bible_id, book_code, chapter), book_code, chapter)
    return compare_source_chapter(language, book_code, chapter, books_root, source)


def compare_stdin_snapshot(payload: dict[str, Any], books_root: Path) -> dict[str, Any]:
    """Compare a CUA/Playwright snapshot piped through stdin, never a raw file."""
    language = payload.get("language")
    book = payload.get("book")
    chapter = payload.get("chapter")
    records = payload.get("records")
    if language not in EDITIONS or not isinstance(book, str) or book not in {code for code, _, _ in BOOKS}:
        raise BrowserAuditError("invalid snapshot edition or book")
    if not isinstance(chapter, int) or chapter < 1 or not isinstance(records, list):
        raise BrowserAuditError("invalid snapshot chapter or records")
    # Psalm 119 can legitimately exceed 500 DOM records because Bible.com may
    # emit several disjoint content fragments for each of its 176 verses. The
    # transport remains bounded by MAX_HTML; retain a separate generous count
    # ceiling for direct in-memory callers and malformed snapshots.
    if len(records) > MAX_SNAPSHOT_RECORDS or not all(isinstance(item, dict) for item in records):
        raise BrowserAuditError("snapshot record limit or structure invalid")
    validate_page_identity(payload.get("pageUrl"), payload.get("pageTitle"), EDITIONS[language].bible_id, book, chapter)
    empty_snapshot_ranges = _empty_snapshot_ranges(records, book, chapter)
    source_defect = _classify_locked_sab_luke7_defect(
        language, book, chapter, records, books_root, empty_snapshot_ranges,
    )
    if source_defect is not None:
        return source_defect
    filtered, source_omissions, empty_ranges = _filter_read_only_source_omissions(
        language, book, chapter, records, books_root
    )
    source = extract_browser_records(filtered, book, chapter)
    result = compare_source_chapter(language, book, chapter, books_root, source)
    result["sourceOmissions"] = source_omissions
    result["emptyRanges"] = empty_ranges
    return result


def stream_snapshots(source: TextIO, sink: TextIO, books_root: Path) -> int:
    """Process JSON Lines in memory; flush one sanitized result per snapshot."""
    status = 0
    for line in source:
        if not line.strip():
            continue
        try:
            if len(line) > MAX_HTML:
                raise BrowserAuditError("snapshot line exceeds safety limit")
            payload = json.loads(line)
            if not isinstance(payload, dict):
                raise BrowserAuditError("snapshot is not an object")
            result = compare_stdin_snapshot(payload, books_root)
        except (BrowserAuditError, ValidationError, OSError, UnicodeError, ValueError, KeyError, TypeError) as exc:
            result = _blocked_result(exc)
        sink.write(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n")
        sink.flush()
        status = max(status, {
            "match": 0, "mismatch": 1, "source_defect": 1, "blocked": 2,
        }[result["status"]])
    return status


def create_snapshot_server(port: int, books_root: Path) -> HTTPServer:
    """Create a loopback-only, sequential in-memory snapshot comparison server."""

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format: str, *_args: object) -> None:
            # Default BaseHTTPRequestHandler logs request paths and messages.
            # Keep this endpoint silent because publisher text arrives in POST.
            return

        def _respond(self, code: int, payload: dict[str, Any]) -> None:
            body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self) -> None:
            if self.path == "/health":
                self._respond(200, {"status": "ok"})
            else:
                self._respond(404, {"status": "not_found"})

        def do_POST(self) -> None:
            if self.path != "/snapshot":
                self._respond(404, {"status": "not_found"})
                return
            try:
                length = int(self.headers.get("Content-Length", ""))
                if length < 1 or length > MAX_HTML:
                    raise BrowserAuditError("invalid request length")
                if self.headers.get_content_type() != "application/json":
                    raise BrowserAuditError("invalid content type")
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise BrowserAuditError("incomplete request body")
                payload = json.loads(raw.decode("utf-8"))
                if not isinstance(payload, dict):
                    raise BrowserAuditError("snapshot is not an object")
                result = compare_stdin_snapshot(payload, books_root)
            except (BrowserAuditError, ValidationError, OSError, UnicodeError, ValueError, KeyError, TypeError) as exc:
                result = _blocked_result(exc)
            self._respond(200, result)

    return HTTPServer(("127.0.0.1", port), Handler)


def serve_snapshots(port: int, books_root: Path) -> None:
    server = create_snapshot_server(port, books_root)
    try:
        print(f"Browser parity snapshot server listening on 127.0.0.1:{server.server_port}", flush=True)
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cdp", default="http://127.0.0.1:9224", help="Existing local Chromium CDP endpoint")
    parser.add_argument("--languages", default="en", help="Comma-separated supported language codes")
    parser.add_argument("--chapters", default="GEN.1", help="Comma-separated USFM book.chapter references")
    parser.add_argument("--timeout", type=float, default=25.0)
    parser.add_argument("--report", type=Path, help="Optional sanitized JSON report")
    parser.add_argument("--snapshot-stdin", action="store_true", help="Read one ordered DOM snapshot as JSON from stdin; do not use CDP")
    parser.add_argument("--snapshot-jsonl", action="store_true", help="Stream ordered DOM snapshots from stdin; emit sanitized JSON Lines on stdout")
    parser.add_argument("--snapshot-server", action="store_true", help="Accept in-memory chapter snapshots over loopback POST /snapshot")
    parser.add_argument("--port", type=int, default=9225, help="Loopback snapshot server port; default 9225")
    args = parser.parse_args()
    if args.snapshot_server:
        if args.snapshot_stdin or args.snapshot_jsonl or args.report or not (1 <= args.port <= 65535):
            parser.error("--snapshot-server requires a valid --port and no other snapshot/report mode")
        try:
            serve_snapshots(args.port, ROOT / "shared/assets/books")
        except OSError:
            print("Browser parity snapshot server could not bind loopback port", file=sys.stderr)
            return 2
        return 0
    if args.snapshot_jsonl:
        if args.snapshot_stdin or args.report:
            parser.error("--snapshot-jsonl cannot be combined with --snapshot-stdin or --report")
        return stream_snapshots(sys.stdin, sys.stdout, ROOT / "shared/assets/books")
    if args.snapshot_stdin:
        try:
            payload = json.load(sys.stdin)
            result = compare_stdin_snapshot(payload, ROOT / "shared/assets/books")
        except (BrowserAuditError, ValidationError, OSError, UnicodeError, ValueError, KeyError, TypeError) as exc:
            result = _blocked_result(exc)
        output = {"schemaVersion": 1, "generatedAt": utc_now(),
                  "mode": "read-only public Bible.com in-memory browser snapshot parity; no source text retained",
                  "chapters": [result], "status": result["status"]}
        rendered = json.dumps(output, ensure_ascii=False, indent=2) + "\n"
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(rendered, encoding="utf-8", newline="\n")
        print(rendered, end="")
        return {"match": 0, "mismatch": 1, "source_defect": 1, "blocked": 2}[output["status"]]
    languages = [item.strip() for item in args.languages.split(",")]
    if not languages or any(item not in EDITIONS for item in languages):
        parser.error("unsupported --languages entry")
    valid_books = {code for code, _collection, _name in BOOKS}
    chapters: list[tuple[str, int]] = []
    for raw in args.chapters.split(","):
        match = re.fullmatch(r"([1-3]?[A-Z]{2,3})\.(\d+)", raw.strip().upper())
        if not match or match[1] not in valid_books or int(match[2]) < 1:
            parser.error("--chapters must contain valid USFM book.chapter references")
        chapters.append((match[1], int(match[2])))
    if args.timeout <= 0:
        parser.error("--timeout must be positive")
    output: dict[str, Any] = {"schemaVersion": 1, "generatedAt": utc_now(),
        "mode": "read-only public Bible.com browser DOM parity; no source text retained",
        "normalization": "HTML entities, Unicode NFC, line endings, markup-boundary whitespace only",
        "chapters": []}
    try:
        with CdpTab(args.cdp, args.timeout) as tab:
            for language in languages:
                for book, chapter in chapters:
                    try:
                        result = compare_chapter(tab, language, book, chapter, ROOT / "shared/assets/books")
                    except (BrowserAuditError, ValidationError, OSError, UnicodeError, ValueError) as exc:
                        result = {"language": language, "reference": f"{book}.{chapter}",
                                  "status": "blocked", "blocker": type(exc).__name__}
                    output["chapters"].append(result)
    except BrowserAuditError as exc:
        output["status"] = "blocked"
        output["blocker"] = type(exc).__name__
    else:
        output["status"] = "blocked" if any(item["status"] == "blocked" for item in output["chapters"]) else (
            "mismatch" if any(item["status"] == "mismatch" for item in output["chapters"]) else (
                "source_defect" if any(item["status"] == "source_defect" for item in output["chapters"])
                else "match"
            ))
    rendered = json.dumps(output, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered, encoding="utf-8", newline="\n")
    print(rendered, end="")
    return {"match": 0, "mismatch": 1, "source_defect": 1, "blocked": 2}[output["status"]]


if __name__ == "__main__":
    raise SystemExit(main())
