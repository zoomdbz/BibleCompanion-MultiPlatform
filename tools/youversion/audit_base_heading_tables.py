#!/usr/bin/env python3
"""Read-only integrity audit for the app's base book assets.

The alternate-edition overlay audit lives under ``tools/traditional``.  This
tool covers the base assets that supply summaries, headings, and (for the
default edition) Scripture text.  It deliberately does not rewrite a book.

Canonical and verse-addressable stories must use one trailing native marker on
every bullet.  Literary summaries that have no numeric verse address (some
apocrypha and pseudepigrapha) are still parsed as JSON and counted, but they
cannot carry verse-bound headings.  A story may not mix addressable and
unaddressed bullets.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import json
from pathlib import Path
import re
import sys
from typing import Any, Iterable


BASE_COLLECTIONS = (
    "old_testament",
    "new_testament",
    "deuterocanonical",
    "apocrypha",
    "pseudepigrapha",
)

# Accept the punctuation actually present in localized assets.  The marker
# payload stays strict: chapter:verse[-verse] or a verse[-verse] number for a
# single-chapter work.  Full-width Japanese delimiters are native source
# punctuation, not a different reference system.
TRAILING_MARKER_RE = re.compile(
    r"\s*[\(\uff08](?P<body>[^()\uff08\uff09]+)[\)\uff09]"
    r"(?P<punct>[.\u3002\u0964\u06d4]?)\s*$"
)
CHAPTER_VERSE_RE = re.compile(
    r"^(?P<chapter>\d+)\s*[:\uff1a]\s*(?P<start>\d+)"
    r"(?:\s*[-\u2010-\u2015\u2212\uff0d]\s*(?P<end>\d+))?$"
)
SINGLE_CHAPTER_VERSE_RE = re.compile(
    r"^(?P<start>\d+)(?:\s*[-\u2010-\u2015\u2212\uff0d]\s*(?P<end>\d+))?$"
)


@dataclass(frozen=True)
class NativeUnit:
    chapter: int
    start: int
    end: int
    bullet_index: int


@dataclass(frozen=True)
class ParsedMarker:
    kind: str
    raw: str | None = None
    unit: NativeUnit | None = None


@dataclass
class AuditCounts:
    json_files: int = 0
    book_files: int = 0
    index_files: int = 0
    stories: int = 0
    bullets: int = 0
    native_units: int = 0
    opaque_markers: int = 0
    unaddressed_bullets: int = 0
    headings: int = 0
    jcb_repairs: int = 0
    jcb_heading_moves: int = 0
    jcb_chapter_records: int = 0


@dataclass
class AuditResult:
    counts: AuditCounts = field(default_factory=AuditCounts)
    errors: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


def _load_json(path: Path, errors: list[str]) -> Any | None:
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        errors.append(f"{path}: cannot parse JSON: {exc}")
        return None


def parse_trailing_marker(text: object, bullet_index: int) -> ParsedMarker:
    if not isinstance(text, str):
        return ParsedMarker("invalid")
    suffix = TRAILING_MARKER_RE.search(text)
    if suffix is None:
        return ParsedMarker("none")
    body = suffix.group("body").strip()
    match = CHAPTER_VERSE_RE.fullmatch(body)
    if match is not None:
        chapter = int(match.group("chapter"))
        start = int(match.group("start"))
        end = int(match.group("end") or start)
        return ParsedMarker("chapter_verse", body, NativeUnit(chapter, start, end, bullet_index))
    match = SINGLE_CHAPTER_VERSE_RE.fullmatch(body)
    if match is not None:
        start = int(match.group("start"))
        end = int(match.group("end") or start)
        return ParsedMarker("single_chapter_verse", body, NativeUnit(1, start, end, bullet_index))
    return ParsedMarker("opaque", body)


def _story_ref(path: Path, story: dict[str, Any], story_index: int) -> str:
    story_id = story.get("id")
    return f"{path}:{story_id if isinstance(story_id, str) and story_id else story_index}"


def _audit_headings(
    story: dict[str, Any],
    units: list[NativeUnit],
    reference: str,
    result: AuditResult,
) -> None:
    headings = story.get("headings", [])
    if headings is None:
        headings = []
    if not isinstance(headings, list):
        result.errors.append(f"{reference}: headings is not an array")
        return
    result.counts.headings += len(headings)
    anchors: list[int] = []
    for index, heading in enumerate(headings):
        heading_ref = f"{reference}:heading[{index}]"
        if not isinstance(heading, dict):
            result.errors.append(f"{heading_ref}: row is not an object")
            continue
        before = heading.get("beforeVerse")
        text = heading.get("text")
        if isinstance(before, bool) or not isinstance(before, int) or before < 1:
            result.errors.append(f"{heading_ref}: beforeVerse must be a positive integer")
            continue
        anchors.append(before)
        if not isinstance(text, str) or not text.strip():
            result.errors.append(f"{heading_ref}: text must be nonblank")

    if anchors != sorted(anchors):
        result.errors.append(f"{reference}: heading anchors are not sorted: {anchors}")
    duplicates = sorted({anchor for anchor in anchors if anchors.count(anchor) > 1})
    if duplicates:
        result.errors.append(f"{reference}: duplicate heading anchors: {duplicates}")
    if headings and not units:
        result.errors.append(f"{reference}: headings exist without numeric native units")
        return
    starts = {unit.start for unit in units}
    for anchor in anchors:
        if anchor not in starts:
            result.errors.append(
                f"{reference}: heading beforeVerse {anchor} is not a native-unit start; "
                f"starts={sorted(starts)}"
            )


def _audit_units(units: list[NativeUnit], reference: str, result: AuditResult) -> None:
    by_chapter: dict[int, list[NativeUnit]] = {}
    for unit in units:
        # A few source traditions number a prefatory address as verse 0 (for
        # example Arabic Letter of Jeremiah/Baruch 6:0).  It is a real native
        # source unit even though UI headings remain restricted to verse 1+.
        if unit.chapter < 1 or unit.start < 0 or unit.end < unit.start:
            result.errors.append(
                f"{reference}: invalid native unit {unit.chapter}:{unit.start}-{unit.end} "
                f"at bullet {unit.bullet_index}"
            )
            continue
        by_chapter.setdefault(unit.chapter, []).append(unit)
    if len(by_chapter) > 1:
        result.errors.append(
            f"{reference}: one story spans multiple native chapters: {sorted(by_chapter)}"
        )
    for chapter, chapter_units in sorted(by_chapter.items()):
        ordered = sorted(chapter_units, key=lambda unit: (unit.start, unit.end, unit.bullet_index))
        previous: NativeUnit | None = None
        for unit in ordered:
            if previous is not None and unit.start <= previous.end:
                result.errors.append(
                    f"{reference}: overlapping native units "
                    f"{chapter}:{previous.start}-{previous.end} (bullet {previous.bullet_index}) and "
                    f"{chapter}:{unit.start}-{unit.end} (bullet {unit.bullet_index})"
                )
            if previous is None or unit.end > previous.end:
                previous = unit


def _audit_story(
    path: Path,
    story: object,
    story_index: int,
    collection: str,
    result: AuditResult,
) -> None:
    result.counts.stories += 1
    if not isinstance(story, dict):
        result.errors.append(f"{path}:story[{story_index}]: story is not an object")
        return
    reference = _story_ref(path, story, story_index)
    bullets = story.get("summaryBullets")
    if not isinstance(bullets, list):
        result.errors.append(f"{reference}: summaryBullets is not an array")
        return
    result.counts.bullets += len(bullets)
    markers = [parse_trailing_marker(bullet, index) for index, bullet in enumerate(bullets)]
    units = [marker.unit for marker in markers if marker.unit is not None]
    result.counts.native_units += len(units)
    result.counts.opaque_markers += sum(marker.kind == "opaque" for marker in markers)
    result.counts.unaddressed_bullets += sum(marker.kind == "none" for marker in markers)

    numeric_kinds = {marker.kind for marker in markers if marker.unit is not None}
    nonnumeric = [index for index, marker in enumerate(markers) if marker.unit is None]
    if numeric_kinds:
        if len(numeric_kinds) > 1:
            result.errors.append(
                f"{reference}: mixes chapter:verse and single-chapter native markers"
            )
        for index in nonnumeric:
            marker = markers[index]
            detail = f"opaque marker {marker.raw!r}" if marker.kind == "opaque" else "no trailing marker"
            result.errors.append(f"{reference}:bullet[{index}]: {detail} in an addressable story")
    elif collection in {"old_testament", "new_testament"} and bullets:
        result.errors.append(f"{reference}: canonical story has no numeric native units")

    _audit_units(units, reference, result)
    _audit_headings(story, units, reference, result)


def _book_assets(repo_root: Path) -> Iterable[tuple[str, Path]]:
    books_root = repo_root / "shared" / "assets" / "books"
    for collection in BASE_COLLECTIONS:
        collection_root = books_root / collection
        if not collection_root.is_dir():
            continue
        for path in sorted(collection_root.glob("*/*.json")):
            yield collection, path


def audit_base_books(repo_root: Path) -> AuditResult:
    result = AuditResult()
    for collection, path in _book_assets(repo_root):
        result.counts.json_files += 1
        payload = _load_json(path, result.errors)
        if payload is None:
            continue
        if path.name == "_index.json":
            result.counts.index_files += 1
            if not isinstance(payload, list):
                result.errors.append(f"{path}: index root is not an array")
            continue
        result.counts.book_files += 1
        if not isinstance(payload, dict):
            result.errors.append(f"{path}: book root is not an object")
            continue
        stories = payload.get("stories")
        if not isinstance(stories, list):
            result.errors.append(f"{path}: stories is not an array")
            continue
        for story_index, story in enumerate(stories):
            _audit_story(path, story, story_index, collection, result)
    return result


def _positive_int(value: object) -> int | None:
    return value if isinstance(value, int) and not isinstance(value, bool) and value > 0 else None


def _ledger_anchor(value: object, reference: str, errors: list[str]) -> tuple[int, int, int] | None:
    if not isinstance(value, dict):
        errors.append(f"{reference}: anchor is not an object")
        return None
    chapter = _positive_int(value.get("chapter"))
    start = _positive_int(value.get("start"))
    end = _positive_int(value.get("end"))
    if chapter is None or start is None or end is None or end < start:
        errors.append(f"{reference}: invalid chapter/start/end")
        return None
    return chapter, start, end


def _heading_location(value: object, reference: str, errors: list[str]) -> tuple[str, int] | None:
    if not isinstance(value, dict):
        errors.append(f"{reference}: heading location is not an object")
        return None
    story_id = value.get("storyId")
    before = _positive_int(value.get("beforeVerse"))
    if not isinstance(story_id, str) or not story_id or before is None:
        errors.append(f"{reference}: invalid storyId/beforeVerse")
        return None
    return story_id, before


def audit_jcb_repair_ledger(repo_root: Path, ledger_path: Path, result: AuditResult) -> None:
    ledger = _load_json(ledger_path, result.errors)
    if not isinstance(ledger, dict):
        if ledger is not None:
            result.errors.append(f"{ledger_path}: ledger root is not an object")
        return
    if ledger.get("schemaVersion") != 1 or ledger.get("language") != "ja":
        result.errors.append(f"{ledger_path}: expected schemaVersion 1 and language ja")
        return
    source = ledger.get("sourceEdition")
    if not isinstance(source, dict) or source.get("code") != "JCB" or source.get("bibleComVersionId") != 83:
        result.errors.append(f"{ledger_path}: source edition must be Bible.com 83/JCB")
    repairs = ledger.get("reviewedRepairs")
    if not isinstance(repairs, list) or not repairs:
        result.errors.append(f"{ledger_path}: reviewedRepairs must be a nonempty array")
        return
    seen_ids: set[str] = set()
    books_root = (repo_root / "shared" / "assets" / "books").resolve()
    for repair_index, repair in enumerate(repairs):
        ref = f"{ledger_path}:reviewedRepairs[{repair_index}]"
        if not isinstance(repair, dict):
            result.errors.append(f"{ref}: repair is not an object")
            continue
        repair_id = repair.get("id")
        if not isinstance(repair_id, str) or not repair_id or repair_id in seen_ids:
            result.errors.append(f"{ref}: id is missing or duplicated")
            continue
        seen_ids.add(repair_id)
        ref = f"{ledger_path}:{repair_id}"
        asset = repair.get("asset")
        story_id = repair.get("storyId")
        urls = repair.get("sourceUrls")
        if not isinstance(asset, str) or not asset or Path(asset).is_absolute() or ".." in Path(asset).parts:
            result.errors.append(f"{ref}: invalid relative asset path")
            continue
        if "/ja/" not in asset.replace("\\", "/"):
            result.errors.append(f"{ref}: asset is not a Japanese base asset")
            continue
        if not isinstance(story_id, str) or not story_id:
            result.errors.append(f"{ref}: storyId is missing")
            continue
        if not isinstance(urls, list) or not urls or any(
            not isinstance(url, str) or not url.startswith("https://www.bible.com/bible/83/")
            for url in urls
        ):
            result.errors.append(f"{ref}: sourceUrls must point to Bible.com version 83")
        asset_path = (books_root / asset).resolve()
        try:
            asset_path.relative_to(books_root)
        except ValueError:
            result.errors.append(f"{ref}: asset escapes the base books root")
            continue
        payload = _load_json(asset_path, result.errors)
        if not isinstance(payload, dict) or not isinstance(payload.get("stories"), list):
            result.errors.append(f"{ref}: asset is not a base book object")
            continue
        stories = [story for story in payload["stories"] if isinstance(story, dict)]
        matching_stories = [story for story in stories if story.get("id") == story_id]
        if len(matching_stories) != 1:
            result.errors.append(f"{ref}: expected exactly one story {story_id!r}")
            continue
        story = matching_stories[0]
        parsed_units = [
            marker.unit
            for index, bullet in enumerate(story.get("summaryBullets", []))
            if (marker := parse_trailing_marker(bullet, index)).unit is not None
        ]
        native_repairs = repair.get("nativeUnits")
        if not isinstance(native_repairs, list):
            result.errors.append(f"{ref}: nativeUnits must be an array")
            native_repairs = []
        heading_repairs = repair.get("headingMoves")
        if not isinstance(heading_repairs, list):
            result.errors.append(f"{ref}: headingMoves must be an array")
            heading_repairs = []
        if not native_repairs and not heading_repairs:
            result.errors.append(f"{ref}: at least one nativeUnits or headingMoves item is required")
            continue
        result.counts.jcb_chapter_records += 1
        for unit_index, unit_repair in enumerate(native_repairs):
            unit_ref = f"{ref}:nativeUnits[{unit_index}]"
            if not isinstance(unit_repair, dict):
                result.errors.append(f"{unit_ref}: row is not an object")
                continue
            old = _ledger_anchor(unit_repair.get("old"), f"{unit_ref}:old", result.errors)
            new = _ledger_anchor(unit_repair.get("new"), f"{unit_ref}:new", result.errors)
            if old is None or new is None:
                continue
            if old == new:
                result.errors.append(f"{unit_ref}: old and new anchors are identical")
                continue
            current = [(unit.chapter, unit.start, unit.end) for unit in parsed_units]
            if current.count(new) != 1:
                state = "still has reviewed old anchor" if old in current else f"has {current.count(new)} new matches"
                result.errors.append(f"{unit_ref}: expected native unit {new}; {state}")
            if old in current:
                result.errors.append(f"{unit_ref}: obsolete native unit {old} remains")
            result.counts.jcb_repairs += 1

        for heading_index, heading_repair in enumerate(heading_repairs):
            heading_ref = f"{ref}:headingMoves[{heading_index}]"
            if not isinstance(heading_repair, dict):
                result.errors.append(f"{heading_ref}: row is not an object")
                continue
            text = heading_repair.get("text")
            old = _heading_location(heading_repair.get("old"), f"{heading_ref}:old", result.errors)
            new = _heading_location(heading_repair.get("new"), f"{heading_ref}:new", result.errors)
            if not isinstance(text, str) or not text.strip() or old is None or new is None:
                if not isinstance(text, str) or not text.strip():
                    result.errors.append(f"{heading_ref}: text must be nonblank")
                continue
            if old == new:
                result.errors.append(f"{heading_ref}: old and new heading locations are identical")
                continue
            locations: list[tuple[str, int]] = []
            for candidate_story in stories:
                candidate_id = candidate_story.get("id")
                for heading in candidate_story.get("headings", []):
                    if isinstance(heading, dict) and heading.get("text") == text:
                        before = heading.get("beforeVerse")
                        if isinstance(candidate_id, str) and isinstance(before, int):
                            locations.append((candidate_id, before))
            if locations.count(new) != 1 or old in locations:
                result.errors.append(
                    f"{heading_ref}: expected heading {text!r} at {new} and not at obsolete {old}; "
                    f"found {locations}"
                )
            result.counts.jcb_heading_moves += 1


def default_repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_ledger_path() -> Path:
    return Path(__file__).with_name("jcb_native_unit_repairs.json")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=default_repo_root())
    parser.add_argument("--ledger", type=Path, default=default_ledger_path())
    parser.add_argument("--skip-jcb-ledger", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    repo_root = args.repo_root.resolve()
    result = audit_base_books(repo_root)
    if not args.skip_jcb_ledger:
        audit_jcb_repair_ledger(repo_root, args.ledger.resolve(), result)
    counts = result.counts
    print(
        "Base heading audit: "
        f"{counts.json_files} JSON files, {counts.book_files} books, {counts.index_files} indexes, "
        f"{counts.stories} stories, {counts.bullets} bullets, {counts.native_units} native units, "
        f"{counts.headings} headings, {counts.jcb_repairs} reviewed JCB unit repairs, "
        f"{counts.jcb_heading_moves} heading moves across {counts.jcb_chapter_records} chapter records"
    )
    if result.errors:
        print(f"FAILED: {len(result.errors)} error(s)", file=sys.stderr)
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("PASS: all addressable headings anchor native-unit starts; no native units overlap")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
