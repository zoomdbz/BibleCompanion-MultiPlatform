#!/usr/bin/env python3
"""Audit or sync paired native units from a rendered browser snapshot.

The tool pairs existing app bullets with ordered Bible.com native units. It
never splits a bullet. Apply mode updates the numeric body of a trailing marker,
for example ``(1:4).`` to ``(1:4-5).``. When that same native unit also has a
wording or semantic-span difference, it can replace the prefix only after the
rendered J/ADD/DN evidence independently reconstructs the exact tagged source
text. Both edits are assembled in memory and written atomically. Publisher text
remains in memory; output contains hashes and counts only.
"""

from __future__ import annotations

import argparse
from copy import deepcopy
from dataclasses import dataclass
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from typing import Any, TextIO
from urllib.parse import unquote, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_base_heading_tables import TRAILING_MARKER_RE, parse_trailing_marker  # noqa: E402
from compare_modern_editions import BOOKS, DIVINE_TAG, EDITIONS  # noqa: E402
from compare_modern_editions_browser import (  # noqa: E402
    BrowserAuditError,
    _align_untagged_divine_names,
    _dn_difference,
    _runtime_covers_untagged_dn,
    _space_separator_fold,
    extract_browser_records,
)
from compare_biblecom import parse_semantic_text  # noqa: E402
from sync_modern_scripture_browser import (  # noqa: E402
    ScriptureSyncError,
    _apply_raw_replacements,
    _array_item_spans,
    _confirmed_tagged_replacement,
    _decoded_offsets,
    _object_fields,
    _parse_tagged,
    _raw_replacements,
    _semantic_spans,
    _source_semantics,
    _story_start,
)

ROOT = Path(__file__).resolve().parents[2]
BOOK_INDEX = {code: (collection, book_id) for code, collection, book_id in BOOKS}
# Root JSON ids predate the current underscore-based asset filenames. These
# eight spellings are the only established exceptions in the canonical corpus;
# every other canonical book uses its filename stem verbatim. Keep this closed
# map independent of the payload being opened so a copied wrong-book payload
# cannot nominate its own story id.
_BOOK_ROOT_ID_OVERRIDES = {
    "1_samuel": "1-samuel",
    "2_samuel": "2-samuel",
    "1_kings": "1-kings",
    "2_kings": "2-kings",
    "1_chronicles": "1chronicles",
    "2_chronicles": "2chronicles",
    "song_of_songs": "song-of-songs",
    "1_corinthians": "1-corinthians",
}
BOOK_ROOT_IDS = {
    book_id: _BOOK_ROOT_ID_OVERRIDES.get(book_id, book_id)
    for _code, _collection, book_id in BOOKS
}
JADD_TAG = re.compile(r"\[/?(?:J|ADD)\]")
RANGE_CORE = re.compile(
    r"(?P<chapter>\d+)(?P<colon>\s*[:：]\s*)(?P<start>\d+)"
    r"(?:(?P<separator>\s*[-‐‑‒–—―−－]\s*)(?P<end>\d+))?"
)
SINGLE_CORE = re.compile(
    r"(?P<start>\d+)(?:(?P<separator>\s*[-‐‑‒–—―−－]\s*)(?P<end>\d+))?"
)
MAX_SNAPSHOT = 8_000_000
MAX_RECORDS = 2_000

# Bible.com JCB omits rendered ``wj`` markup on these exact combined native
# units even though each unit is Jesus speaking (currently confirmed at Luke
# 4:18-19, 11:50-51, 20:37-38, and 20:42-43). The local manuscript already wraps
# each complete unit in one [J] span. These story and source-inventory hashes
# pin the exception to the independently reviewed pre-repair state; any text,
# range, tag, or source-structure change fails closed. The generic rendered
# semantic guard remains authoritative for every other range.
REVIEWED_FULL_RANGE_J_SOURCE_REPLACEMENTS: dict[
    tuple[str, str, int, tuple[int, int]], tuple[str, str]
] = {
    ("ja", "LUK", 4, (18, 19)): (
        "87f71b4bdd3d7980eed7e3c9136ffa18b5283c41288b067d662919b1947247b9",
        "593432939778b6352e5963897ad1b0ad74b0efd89bc8c09bade3f267ec80771a",
    ),
    ("ja", "LUK", 11, (50, 51)): (
        "3aa282ea3469fa180a676fd32436ec67014fa1f3225b786fce9f4f83a02f0c18",
        "c2e24dfed8519af93931cb2cf14b878b5de1b2c623941eb387c1ae37c3e78fa2",
    ),
    ("ja", "LUK", 20, (37, 38)): (
        "40f1dabfc1efd31c6beeb8a9d5a61d86a54a5d3dcf2ec98f049f8b722538f20c",
        "ef229e050ffaf0668329aea598105d1c943768c42c0b9158c9ffc1ae77f53052",
    ),
    ("ja", "LUK", 20, (42, 43)): (
        "40f1dabfc1efd31c6beeb8a9d5a61d86a54a5d3dcf2ec98f049f8b722538f20c",
        "ef229e050ffaf0668329aea598105d1c943768c42c0b9158c9ffc1ae77f53052",
    ),
}


class RangeSyncError(ValueError):
    """The snapshot or asset cannot support a marker-only range update."""


@dataclass(frozen=True)
class RawEdit:
    start: int
    end: int
    value: str


def _positive(value: object) -> bool:
    return type(value) is int and value > 0


def _sha_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _sha_json(value: object) -> str:
    return _sha_bytes(json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))


def _identity(snapshot: dict[str, Any], root: Path) -> tuple[str, int, str, str, str, Path]:
    language, code, chapter = snapshot.get("language"), snapshot.get("book"), snapshot.get("chapter")
    if language not in EDITIONS or code not in BOOK_INDEX or not _positive(chapter):
        raise RangeSyncError("unknown language book or chapter")
    edition = EDITIONS[language]
    supplied_id = snapshot.get("bibleId")
    if supplied_id is not None and (type(supplied_id) is not int or supplied_id != edition.bible_id):
        raise RangeSyncError("bible edition identity mismatch")
    url, title = snapshot.get("pageUrl"), snapshot.get("pageTitle")
    if not isinstance(url, str):
        raise RangeSyncError("missing rendered browser page url")
    if (not isinstance(title, str) or not title.strip()
            or re.search(r"client challenge|captcha|verify you are human", title, re.I)):
        raise RangeSyncError("browser page title is missing or challenged")
    parsed = urlsplit(url)
    valid_paths = {
        f"/bible/{edition.bible_id}/{code}.{chapter}.{abbreviation}".lower()
        for abbreviation in (edition.abbreviation, *edition.published_abbreviation_aliases)
    }
    parts = unquote(parsed.path).lower().rstrip("/").split("/")
    if len(parts) == 5 and re.fullmatch(r"[a-z]{2}(?:-[a-z]{2})?", parts[1]):
        parts.pop(1)
    normalized_path = "/".join(parts)
    if (parsed.scheme != "https" or parsed.hostname not in {"www.bible.com", "bible.com"}
            or parsed.username or parsed.password or parsed.port not in {None, 443}
            or normalized_path not in valid_paths):
        raise RangeSyncError("source url does not identify declared passage and edition")
    collection, book_id = BOOK_INDEX[code]
    path = root / "shared" / "assets" / "books" / collection / language / f"{book_id}.json"
    return language, chapter, code, collection, book_id, path


def _local_story(payload: Any, book_id: str,
                 chapter: int) -> tuple[int, dict[str, Any], list[tuple[int, int]]]:
    expected_root_id = BOOK_ROOT_IDS.get(book_id)
    if (expected_root_id is None or not isinstance(payload, dict)
            or payload.get("id") != expected_root_id
            or not isinstance(payload.get("stories"), list)):
        raise RangeSyncError("local book identity or structure mismatch")
    story_id = f"{expected_root_id}-{chapter}"
    matches: list[tuple[int, dict[str, Any], list[tuple[int, int]]]] = []
    for story_index, story in enumerate(payload["stories"]):
        if not isinstance(story, dict) or not isinstance(story.get("summaryBullets"), list):
            raise RangeSyncError("invalid local story")
        if story.get("id") != story_id:
            continue
        units: list[tuple[int, int]] = []
        for bullet_index, bullet in enumerate(story["summaryBullets"]):
            marker = parse_trailing_marker(bullet, bullet_index)
            if marker.unit is None or marker.unit.chapter != chapter:
                raise RangeSyncError("target story has nonnative or cross chapter marker")
            units.append((marker.unit.start, marker.unit.end))
        if not units or units != sorted(units):
            raise RangeSyncError("target story has missing or unordered native units")
        if any(start < 1 or end < start for start, end in units):
            raise RangeSyncError("target story has invalid native unit")
        if any(units[index][0] <= units[index - 1][1] for index in range(1, len(units))):
            raise RangeSyncError("target story has overlapping or ambiguous native units")
        matches.append((story_index, story, units))
    if len(matches) != 1:
        raise RangeSyncError("target chapter must have exactly one matching story")
    return matches[0]


def _heading_anchors(story: dict[str, Any]) -> tuple[int, ...]:
    headings = story.get("headings", [])
    if not isinstance(headings, list):
        raise RangeSyncError("target story headings are invalid")
    anchors: list[int] = []
    for row in headings:
        if (not isinstance(row, dict) or type(row.get("beforeVerse")) is not int
                or row["beforeVerse"] < 1 or not isinstance(row.get("text"), str)):
            raise RangeSyncError("target story heading row is invalid")
        anchors.append(row["beforeVerse"])
    if anchors != sorted(set(anchors)):
        raise RangeSyncError("target story heading anchors are ambiguous")
    return tuple(anchors)


def _local_semantics(prefix: str, reference: str) -> tuple[str, str]:
    try:
        _parse_tagged(prefix)
        # Removing J/ADD keeps literal DN markers for the comparator's exact
        # marker-aware logic. parse_semantic_text supplies its normal NFC,
        # entity, line-ending, and markup-boundary normalization.
        local_marked = parse_semantic_text(JADD_TAG.sub("", prefix), reference).plain
    except (ScriptureSyncError, ValueError) as exc:
        raise RangeSyncError("local scripture tags are malformed") from exc
    return DIVINE_TAG.sub("", local_marked), local_marked


def _semantic_text_equal(prefix: str, source_plain: str, source_marked: str,
                         language: str, collection: str, reference: str) -> bool:
    local_plain, local_marked = _local_semantics(prefix, reference)
    if not DIVINE_TAG.search(local_marked) and DIVINE_TAG.search(source_marked):
        aligned = _align_untagged_divine_names(local_plain, source_marked)
        if aligned is None:
            aligned = _align_untagged_divine_names(
                _space_separator_fold(local_plain), _space_separator_fold(source_marked)
            )
        if aligned is None:
            return False
        return all(
            _runtime_covers_untagged_dn(language, local_plain, start, end, collection)
            for start, end, _local, _source in aligned
        )
    difference = _dn_difference(local_marked, source_marked)
    if difference in {None, "divine_name_presentation_difference"}:
        return True
    if difference == "text_mismatch":
        folded = _dn_difference(_space_separator_fold(local_marked), _space_separator_fold(source_marked))
        return folded in {None, "divine_name_presentation_difference"}
    return False


def _reviewed_full_range_j_replacement(
        *, language: str, code: str, chapter: int, source_unit: tuple[int, int],
        local_prefix: str, source_plain: str, source_kinds: dict[str, Any],
        collection: str, story_sha: str, source_ranges_sha: str,
        reviewed: dict[tuple[str, str, int, tuple[int, int]], tuple[str, str]] | None = None,
        ) -> tuple[str, int, int] | None:
    """Preserve one hash-pinned whole-unit Jesus-word span absent upstream."""
    rows = REVIEWED_FULL_RANGE_J_SOURCE_REPLACEMENTS if reviewed is None else reviewed
    expected = rows.get((language, code, chapter, source_unit))
    if expected is None:
        return None
    if expected != (story_sha, source_ranges_sha):
        raise RangeSyncError("reviewed full-range J replacement hash changed")
    local = _parse_tagged(local_prefix)
    if (not local.plain
            or _semantic_spans(local, "J") != ((0, len(local.plain)),)
            or any(boundary.kind != "J" for boundary in local.boundaries)):
        raise RangeSyncError("reviewed full-range J replacement local span changed")
    rendered_j = source_kinds.get("J")
    if rendered_j is None or rendered_j.plain != source_plain:
        raise RangeSyncError("reviewed full-range J replacement source text changed")
    if _semantic_spans(rendered_j, "J"):
        raise RangeSyncError("reviewed full-range J replacement gained rendered J evidence")
    adjusted = dict(source_kinds)
    adjusted["J"] = _parse_tagged(f"[J]{source_plain}[/J]")
    try:
        return _confirmed_tagged_replacement(
            local_prefix, source_plain, adjusted,
            language=language, collection=collection,
        )
    except ScriptureSyncError as exc:
        raise RangeSyncError(
            "reviewed full-range J replacement failed semantic reconstruction"
        ) from exc


def _updated_marker(bullet: str, source_end: int) -> str:
    marker = TRAILING_MARKER_RE.search(bullet)
    if marker is None:
        raise RangeSyncError("target bullet lacks trailing native marker")
    raw_body = marker.group("body")
    leading = raw_body[:len(raw_body) - len(raw_body.lstrip())]
    trailing = raw_body[len(raw_body.rstrip()):]
    core = raw_body.strip()
    match = RANGE_CORE.fullmatch(core) or SINGLE_CORE.fullmatch(core)
    if match is None:
        raise RangeSyncError("target marker style cannot be preserved safely")
    start = int(match.group("start"))
    old_end = int(match.group("end") or start)
    if source_end < start:
        raise RangeSyncError("source native range ends before its start")
    if source_end == old_end:
        return bullet
    if match.group("end") is not None:
        if source_end == start:
            new_core = core[:match.start("separator")]
        else:
            new_core = core[:match.start("end")] + str(source_end) + core[match.end("end"):]
    elif source_end > start:
        # A newly bridged end always uses the canonical ASCII hyphen. All
        # existing parentheses, colon, spaces, and trailing punctuation stay.
        new_core = core + f"-{source_end}"
    else:
        new_core = core
    new_body = leading + new_core + trailing
    return bullet[:marker.start("body")] + new_body + bullet[marker.end("body"):]


def _common_change(old: str, new: str) -> tuple[int, int, int]:
    prefix = 0
    while prefix < min(len(old), len(new)) and old[prefix] == new[prefix]:
        prefix += 1
    suffix = 0
    while (suffix < len(old) - prefix and suffix < len(new) - prefix
           and old[len(old) - 1 - suffix] == new[len(new) - 1 - suffix]):
        suffix += 1
    return prefix, len(old) - suffix, len(new) - suffix


def _raw_marker_edits(raw: bytes, story_index: int, old_bullets: list[str],
                      new_bullets: list[str]) -> list[RawEdit]:
    text = raw.decode("utf-8")
    try:
        fields = _object_fields(text, _story_start(text, story_index))
        spans = _array_item_spans(text, fields["summaryBullets"][0])
    except (ScriptureSyncError, KeyError, ValueError, IndexError) as exc:
        raise RangeSyncError("raw target story structure is invalid") from exc
    if len(spans) != len(old_bullets) or len(new_bullets) != len(old_bullets):
        raise RangeSyncError("raw and parsed bullet counts differ")
    edits: list[RawEdit] = []
    for (item_start, item_end), old, new in zip(spans, old_bullets, new_bullets):
        token = text[item_start:item_end]
        if json.loads(token) != old:
            raise RangeSyncError("raw bullet span does not match parsed asset")
        if old == new:
            continue
        old_marker, new_marker = TRAILING_MARKER_RE.search(old), TRAILING_MARKER_RE.search(new)
        if old_marker is None or new_marker is None:
            raise RangeSyncError("range update lost trailing marker")
        if old[:old_marker.start()] != new[:new_marker.start()]:
            raise RangeSyncError("range update changed scripture prefix")
        prefix, old_change_end, new_change_end = _common_change(old, new)
        if (not old_marker.start("body") <= prefix <= old_change_end <= old_marker.end("body")
                or not new_marker.start("body") <= prefix <= new_change_end <= new_marker.end("body")):
            raise RangeSyncError("range update escaped marker body")
        try:
            offsets = _decoded_offsets(token, old)
        except ScriptureSyncError as exc:
            raise RangeSyncError("raw marker offset mapping failed") from exc
        replacement_fragment = json.dumps(new[prefix:new_change_end], ensure_ascii=False)[1:-1]
        edits.append(RawEdit(item_start + offsets[prefix], item_start + offsets[old_change_end], replacement_fragment))
    return edits


def _apply_raw_edits(raw: bytes, edits: list[RawEdit]) -> tuple[bytes, str]:
    text = raw.decode("utf-8")
    pieces: list[str] = []
    untouched: list[str] = []
    cursor = 0
    for edit in sorted(edits, key=lambda item: item.start):
        if edit.start < cursor or edit.end > len(text):
            raise RangeSyncError("raw marker edits overlap or exceed asset")
        segment = text[cursor:edit.start]
        pieces.extend((segment, edit.value))
        untouched.append(segment)
        cursor = edit.end
    tail = text[cursor:]
    pieces.append(tail)
    untouched.append(tail)
    proof = _sha_json([_sha_bytes(item.encode("utf-8")) for item in untouched])
    return "".join(pieces).encode("utf-8"), proof


def sync_snapshot(snapshot: dict[str, Any], *, root: Path = ROOT, apply: bool = False) -> dict[str, Any]:
    language, chapter, code, collection, book_id, path = _identity(snapshot, root)
    records = snapshot.get("records")
    if (not isinstance(records, list) or len(records) > MAX_RECORDS
            or not all(isinstance(record, dict) for record in records)):
        raise RangeSyncError("snapshot record limit or structure invalid")
    raw = path.read_bytes()
    payload = json.loads(raw.decode("utf-8-sig"))
    story_index, story, local_units = _local_story(payload, book_id, chapter)
    source = extract_browser_records(records, code, chapter)
    source_units = list(source.ranges)
    if len(source_units) != len(local_units):
        raise RangeSyncError("local and source native unit counts differ")
    headings = _heading_anchors(story)

    replacement_prefixes: list[str] = []
    intermediate_bullets: list[str] = []
    new_bullets: list[str] = []
    changed_markers = 0
    source_synced_ranges = 0
    preserved_boundaries = 0
    preserved_runtime_dn_spans = 0
    semantics: dict[str, dict[tuple[int, int], Any]] | None = None
    story_sha = _sha_json(story)
    source_ranges_sha = _sha_json(source_units)
    for index, (bullet, local_unit, source_unit) in enumerate(zip(story["summaryBullets"], local_units, source_units)):
        local_start, local_end = local_unit
        source_start, source_end = source_unit
        if local_start != source_start:
            raise RangeSyncError("paired local and source native starts differ")
        if source_end < source_start:
            raise RangeSyncError("source native range is invalid")
        if source_end > local_end and any(local_start < anchor <= source_end for anchor in headings):
            raise RangeSyncError("local heading anchor falls inside new bridged range")
        marker = TRAILING_MARKER_RE.search(bullet)
        if marker is None:
            raise RangeSyncError("target bullet lacks trailing native marker")
        prefix = bullet[:marker.start()]
        if _semantic_text_equal(
            prefix, source.ranges[source_unit], source.divine_markup[source_unit],
            language, collection, f"{code}.{chapter}.{source_start}-{source_end}#{index}",
        ):
            replacement = prefix
        else:
            if semantics is None:
                semantics = _source_semantics(records, code, chapter, source.ranges)
            source_kinds = {kind: by_range[source_unit] for kind, by_range in semantics.items()}
            try:
                replacement, boundary_count, runtime_dn_count = _confirmed_tagged_replacement(
                    prefix,
                    source.ranges[source_unit],
                    source_kinds,
                    language=language,
                    collection=collection,
                )
            except ScriptureSyncError as exc:
                reviewed_replacement = _reviewed_full_range_j_replacement(
                    language=language, code=code, chapter=chapter,
                    source_unit=source_unit, local_prefix=prefix,
                    source_plain=source.ranges[source_unit], source_kinds=source_kinds,
                    collection=collection, story_sha=story_sha,
                    source_ranges_sha=source_ranges_sha,
                )
                if reviewed_replacement is None:
                    raise RangeSyncError(
                        "paired source semantic boundaries do not confirm scripture replacement "
                        f"at {code}.{chapter}.{source_start}-{source_end}: {exc}"
                    ) from exc
                replacement, boundary_count, runtime_dn_count = reviewed_replacement
            source_synced_ranges += replacement != prefix
            preserved_boundaries += boundary_count
            preserved_runtime_dn_spans += runtime_dn_count
        intermediate = replacement + bullet[marker.start():]
        updated = _updated_marker(intermediate, source_end)
        replacement_prefixes.append(replacement)
        intermediate_bullets.append(intermediate)
        new_bullets.append(updated)
        changed_markers += updated != intermediate

    prefix_edits = _raw_replacements(
        raw, story_index, story["summaryBullets"], replacement_prefixes
    )
    prefix_raw, prefix_untouched_proof, marker_suffix_proof = _apply_raw_replacements(
        raw, prefix_edits
    )
    marker_edits = _raw_marker_edits(
        prefix_raw, story_index, intermediate_bullets, new_bullets
    )
    if len(marker_edits) != sum(old != new for old, new in zip(intermediate_bullets, new_bullets)):
        raise RangeSyncError("parsed and raw marker edit counts differ")
    updated_raw, marker_untouched_proof = _apply_raw_edits(prefix_raw, marker_edits)
    expected = deepcopy(payload)
    expected["stories"][story_index]["summaryBullets"] = new_bullets
    if json.loads(updated_raw.decode("utf-8-sig")) != expected:
        raise RangeSyncError("raw update changed non target json data")
    for expected_unit, new in zip(source_units, new_bullets):
        parsed = parse_trailing_marker(new, 0)
        if parsed.unit is None or (parsed.unit.start, parsed.unit.end) != expected_unit:
            raise RangeSyncError("raw update changed native marker unexpectedly")

    changed = updated_raw != raw
    result = {
        "status": "changed" if changed else "match",
        "mode": "apply" if apply else "audit",
        "language": language,
        "bibleId": EDITIONS[language].bible_id,
        "book": code,
        "chapter": chapter,
        "collection": collection,
        "nativeUnitCount": len(local_units),
        "changedMarkerCount": changed_markers,
        "sourceSyncedRangeCount": source_synced_ranges,
        "preservedTagBoundaryCount": preserved_boundaries,
        "preservedRuntimeDivineNameSpanCount": preserved_runtime_dn_spans,
        "beforeSha256": _sha_bytes(raw),
        "afterSha256": _sha_bytes(updated_raw),
        "localRangesSha256": _sha_json(local_units),
        "sourceRangesSha256": _sha_json(source_units),
        "untouchedRawSegmentsSha256": _sha_json(
            [prefix_untouched_proof, marker_untouched_proof]
        ),
        "originalMarkerSuffixesSha256": marker_suffix_proof,
    }
    if apply and changed:
        if path.read_bytes() != raw:
            raise RangeSyncError("local asset changed during audit")
        temporary: str | None = None
        try:
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".ranges-", suffix=".tmp", delete=False) as handle:
                temporary = handle.name
                handle.write(updated_raw)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)
    return result


SAFE_EXCEPTIONS = (
    RangeSyncError, BrowserAuditError, ScriptureSyncError, OSError, UnicodeError,
    ValueError, KeyError, IndexError, TypeError,
)


def _blocked_result(exc: BaseException) -> dict[str, str]:
    result = {"status": "blocked", "errorType": type(exc).__name__}
    if type(exc) is RangeSyncError:
        code = re.sub(r"[^a-z0-9]+", "_", str(exc).lower()).strip("_")
        if code:
            result["errorCode"] = code[:120]
    return result


def stream_snapshots(source: TextIO, sink: TextIO, *, root: Path = ROOT, apply: bool = False) -> int:
    status = 0
    for line in source:
        if not line.strip():
            continue
        try:
            if len(line) > MAX_SNAPSHOT:
                raise RangeSyncError("snapshot exceeds size limit")
            snapshot = json.loads(line)
            if not isinstance(snapshot, dict):
                raise RangeSyncError("snapshot must be json object")
            result = sync_snapshot(snapshot, root=root, apply=apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
            status = 2
        sink.write(json.dumps(result, ensure_ascii=False, sort_keys=True) + "\n")
        sink.flush()
    return status


def create_snapshot_server(port: int, *, root: Path = ROOT, apply: bool = False) -> HTTPServer:
    """Accept sequential paired-unit snapshots over loopback without storage."""

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format: str, *_args: object) -> None:
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
            self._respond(200, {"status": "ok"}) if self.path == "/health" else self._respond(404, {"status": "not_found"})

        def do_POST(self) -> None:
            if self.path != "/snapshot":
                self._respond(404, {"status": "not_found"})
                return
            try:
                length_text = self.headers.get("Content-Length", "")
                if not length_text.isdecimal() or not 1 <= int(length_text) <= MAX_SNAPSHOT:
                    raise RangeSyncError("invalid request length")
                if self.headers.get_content_type() != "application/json":
                    raise RangeSyncError("invalid content type")
                length = int(length_text)
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise RangeSyncError("incomplete request body")
                snapshot = json.loads(raw.decode("utf-8"))
                if not isinstance(snapshot, dict):
                    raise RangeSyncError("snapshot must be json object")
                result = sync_snapshot(snapshot, root=root, apply=apply)
            except SAFE_EXCEPTIONS as exc:
                result = _blocked_result(exc)
            self._respond(200, result)

    return HTTPServer(("127.0.0.1", port), Handler)


def serve_snapshots(port: int, *, root: Path = ROOT, apply: bool = False) -> None:
    server = create_snapshot_server(port, root=root, apply=apply)
    try:
        print(f"Range snapshot server listening on 127.0.0.1:{server.server_port}", flush=True)
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="update verified paired native units atomically")
    parser.add_argument("--root", type=Path, default=ROOT, help="repository root")
    parser.add_argument("--snapshot-server", action="store_true", help="accept snapshots over loopback POST /snapshot")
    parser.add_argument("--snapshot-stdin", action="store_true", help="read one snapshot JSON object")
    parser.add_argument("--port", type=int, default=9228, help="loopback server port; default 9228")
    args = parser.parse_args()
    if args.snapshot_server:
        if args.snapshot_stdin or not 1 <= args.port <= 65535:
            parser.error("--snapshot-server requires valid --port and cannot use --snapshot-stdin")
        try:
            serve_snapshots(args.port, root=args.root, apply=args.apply)
        except OSError:
            print("Range snapshot server could not bind loopback port", file=sys.stderr)
            return 2
        return 0
    if args.snapshot_stdin:
        try:
            snapshot = json.load(sys.stdin)
            if not isinstance(snapshot, dict):
                raise RangeSyncError("snapshot must be json object")
            result = sync_snapshot(snapshot, root=args.root, apply=args.apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 2 if result["status"] == "blocked" else 0
    return stream_snapshots(sys.stdin, sys.stdout, root=args.root, apply=args.apply)


if __name__ == "__main__":
    raise SystemExit(main())
