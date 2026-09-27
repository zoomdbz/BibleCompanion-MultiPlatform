#!/usr/bin/env python3
"""Audit or merge whole local bullets into verified Bible.com native ranges.

One source range may cover multiple adjacent local bullets. Every actual merge
reconstructs or verifies rendered J/ADD/DN semantics, and a merged unit may
receive confirmed source text. A duplicated native range may instead collapse
to its already-complete terminal bullet, but only after exact text and semantic
proof. The tool never splits a bullet or persists publisher snapshot text.
"""

from __future__ import annotations

import argparse
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from typing import Any, TextIO

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_base_heading_tables import TRAILING_MARKER_RE  # noqa: E402
from compare_modern_editions import EDITIONS  # noqa: E402
from compare_modern_editions_browser import (  # noqa: E402
    BrowserAuditError, _runtime_covers_untagged_dn, extract_browser_records,
)
from reviewed_arabic_repairs import (  # noqa: E402
    REVIEWED_ARABIC_CHAPTERS, chapter_dn_rows, digest_json, digest_text,
)
from sync_modern_headings_browser import (  # noqa: E402
    HeadingSyncError, _headings as _source_headings,
    _replace_heading_value,
)
from sync_modern_ranges_browser import (  # noqa: E402
    MAX_RECORDS, MAX_SNAPSHOT, ROOT, RangeSyncError, RawEdit, _apply_raw_edits,
    _heading_anchors, _identity, _local_story, _semantic_text_equal, _sha_bytes,
    _sha_json, _updated_marker,
)
from sync_modern_scripture_browser import (  # noqa: E402
    ScriptureSyncError, _array_item_spans, _object_fields, _parse_tagged,
    _story_start, _source_semantics, _confirmed_tagged_replacement,
)


class RangeMergeError(ValueError):
    """A source-native range cannot be assembled from intact local bullets."""


MAX_JOIN_CANDIDATES = 1024
MAX_ENUMERATED_JOIN_BOUNDARIES = 8

# These exact Arabic chapter states were independently reviewed as duplicated
# native ranges: stale partial local bullets precede one complete terminal
# bullet for each rendered bridge. While either hash matches, every multi-unit
# group must take the terminal-collapse path below. Falling through to the
# generic join/source-sync path would hide a changed premise.
REVIEWED_TERMINAL_DUPLICATE_CHAPTERS: dict[
    tuple[str, str, int], tuple[str, str]
] = {
    ("ar", "1CH", 2): (
        "eba06ad27f057a787b86789848aadacff2134ab8c10c9c60553d544932c2a0e3",
        "0a201d35114ffdbe365a4aa2d8f502d6b2a30e383cb75f8d6729c531e2feb0fa",
    ),
    ("ar", "1CH", 24): (
        "d8b36840f26ccbf40dc3e68954511fc701f68a2ac2f78b2a91fdf8f418ff8d18",
        "9196c18fa42fccc070492cc7687c18a490798ef83d11fb5983de36a03b4140a4",
    ),
    ("ar", "1CH", 25): (
        "86e39f8d692c9147eaeaa08d3b21bc2898cb91ff128f1a20c0dbb4d93b46f958",
        "55bad9eda9f77e3894e0e114fdd21e8298fc420470f21a5aeb62c464f12efeda",
    ),
    ("ar", "EZR", 10): (
        "cabff8b005a512316e8a75577f73f2456134318f3f95c9d435eca784f1599917",
        "a4cbf8fcd060c219a9a2ee8c984aac2ef20750c356f24a5673aeeb80d711964e",
    ),
    ("ar", "EZR", 2): (
        "063c40a06979043ce3d271fc0f7d45504b1ab5f0dd7091fb820ae986c9fec12c",
        "058a82320118501a5c2a9c370a0c296ad65d94069b876af0b615ea154f40a25b",
    ),
    ("ar", "NEH", 10): (
        "22f436b08ced91d758f33679556cd79683fe93fa9259d96baa16efd8f0312671",
        "5bdd68cc28f2e678847fde24322e417796101c378669eea2c06ba11144d95134",
    ),
    ("ar", "NUM", 1): (
        "e396bb247121abe87bcc9803e9353d38de012e021a9caff1a118ffa622276e22",
        "3b1c9ae0ff139551079ec3b3aa80c14baa6c2af9c1b5a05a34ba9423095e347e",
    ),
}

# SAB Numbers 1:17-19 is the sole reviewed terminal-duplicate bridge whose
# complete local terminal unit uses the app's runtime divine-name presentation
# while the rendered publisher text uses Allah. The rendered page exposes no
# ``nd`` wrapper, so this exception is pinned to the exact pre-repair story,
# source inventory, local terminal prefix, and source prefix. It must never
# widen Allah into a global/runtime divine-name form.
REVIEWED_TERMINAL_DN_PRESENTATION: dict[
    tuple[str, str, int, tuple[int, int]], dict[str, str]
] = {
    ("ar", "NUM", 1, (17, 19)): {
        "expectedStorySha256": "e396bb247121abe87bcc9803e9353d38de012e021a9caff1a118ffa622276e22",
        "expectedSourceRangesSha256": "3b1c9ae0ff139551079ec3b3aa80c14baa6c2af9c1b5a05a34ba9423095e347e",
        "expectedLocalPrefixSha256": "3fff1c15877472f24011fec143e704bc244ae814f49d056961f9446145eaa663",
        "expectedSourcePrefixSha256": "952582aef1f5b3454080c3fdf0f7ccbe4df3120e6bacb66473dc423cf173c4de",
        "localToken": "يَهوَهْ",
        "sourceToken": "اللهُ",
    },
}


def _prefix(bullet: str) -> str:
    marker = TRAILING_MARKER_RE.search(bullet)
    if marker is None:
        raise RangeMergeError("target bullet lacks trailing native marker")
    prefix = bullet[:marker.start()]
    try:
        _parse_tagged(prefix)
    except ScriptureSyncError as exc:
        raise RangeMergeError("local scripture tags are malformed") from exc
    return prefix


def _merged_prefix_candidates(bullets: list[str]) -> tuple[str, ...]:
    """Try only zero or one structural space at each whole-bullet boundary."""
    prefixes = [_prefix(bullet) for bullet in bullets]
    candidates = {prefixes[0]}
    for following in prefixes[1:]:
        if not following or any(not candidate for candidate in candidates):
            raise RangeMergeError("empty local scripture segment")
        next_candidates = {
            candidate.rstrip() + separator + following.lstrip()
            for candidate in candidates for separator in ("", " ")
        }
        if len(next_candidates) > MAX_JOIN_CANDIDATES:
            raise RangeMergeError("too many possible native joins")
        candidates = next_candidates
    valid: list[str] = []
    for candidate in sorted(candidates):
        try:
            _parse_tagged(candidate)
        except ScriptureSyncError:
            continue
        valid.append(candidate)
    if not valid:
        raise RangeMergeError("merged scripture tags are incompatible")
    return tuple(valid)


def _single_merged_prefix(bullets: list[str]) -> str:
    """Use one structural-space join for large, source-confirmed groups."""
    prefixes = [_prefix(bullet) for bullet in bullets]
    if any(not prefix for prefix in prefixes):
        raise RangeMergeError("empty local scripture segment")
    merged = prefixes[0]
    for following in prefixes[1:]:
        merged = merged.rstrip() + " " + following.lstrip()
    try:
        _parse_tagged(merged)
    except ScriptureSyncError as exc:
        raise RangeMergeError("merged scripture tags are incompatible") from exc
    return merged


def _verified_merged_prefix(bullets: list[str], source_plain: str, source_marked: str,
                            language: str, collection: str, reference: str) -> str | None:
    matches = [
        prefix for prefix in _merged_prefix_candidates(bullets)
        if _semantic_text_equal(prefix, source_plain, source_marked, language, collection, reference)
    ]
    if len(matches) != 1:
        if matches:
            raise RangeMergeError("ambiguous native join matches source text")
        return None
    return matches[0]


def _confirmed_source_prefix(bullets: list[str], source_plain: str,
                             semantics: dict[str, Any], language: str,
                             collection: str, *, large_group: bool = False) -> tuple[str, int, int]:
    """Confirm every retained app boundary for a whole-unit source rewrite."""
    confirmed: set[tuple[str, int, int]] = set()
    candidates = (_single_merged_prefix(bullets),) if large_group else _merged_prefix_candidates(bullets)
    for candidate in candidates:
        try:
            replacement = _confirmed_tagged_replacement(
                candidate, source_plain, semantics, language=language, collection=collection
            )
        except (ScriptureSyncError, RangeSyncError):
            continue
        confirmed.add(replacement)
        if len(confirmed) > 1:
            raise RangeMergeError("ambiguous confirmed native source replacement")
    if not confirmed:
        raise RangeMergeError("source semantic boundaries do not confirm merged scripture")
    return confirmed.pop()


def _verified_terminal_duplicate_prefix(
        bullets: list[str], source_plain: str, source_marked: str,
        semantics: dict[str, Any], language: str, collection: str,
        reference: str) -> tuple[str, int, int] | None:
    """Prove that the last local unit already is the complete native range.

    This path never joins local units and never rewrites wording or tags. The
    ordinary semantic comparator first proves publisher text parity; the
    reconstruction then proves that retaining the terminal prefix preserves
    the exact J/ADD/DN boundary set.
    """
    if len(bullets) < 2:
        return None
    terminal = _prefix(bullets[-1])
    if not _semantic_text_equal(
            terminal, source_plain, source_marked, language, collection,
            f"{reference}#terminal"):
        return None
    try:
        confirmed = _confirmed_tagged_replacement(
            terminal, source_plain, semantics,
            language=language, collection=collection,
        )
    except (ScriptureSyncError, RangeSyncError):
        return None
    # A spacing, wording, presentation, or tag reconstruction belongs to the
    # normal source-sync path. Duplicate collapse retains only an exact final
    # prefix and therefore cannot silently accept a lexical change.
    return confirmed if confirmed[0] == terminal else None


def _reviewed_terminal_dn_presentation_prefix(
        *, language: str, code: str, chapter: int, collection: str,
        source_unit: tuple[int, int], terminal: str, source_plain: str,
        semantics: dict[str, Any], story_sha: str, source_ranges_sha: str,
        reviewed: dict[tuple[str, str, int, tuple[int, int]], dict[str, str]] | None = None,
        ) -> tuple[str, int, int] | None:
    """Validate one hash-pinned terminal divine-name presentation exception."""
    rows = REVIEWED_TERMINAL_DN_PRESENTATION if reviewed is None else reviewed
    row = rows.get((language, code, chapter, source_unit))
    if row is None or collection != "old_testament":
        return None
    required = {
        "expectedStorySha256", "expectedSourceRangesSha256",
        "expectedLocalPrefixSha256", "expectedSourcePrefixSha256",
        "localToken", "sourceToken",
    }
    if set(row) != required:
        raise RangeMergeError("reviewed terminal presentation row is invalid")
    if (story_sha != row["expectedStorySha256"]
            or source_ranges_sha != row["expectedSourceRangesSha256"]
            or _sha_bytes(terminal.encode("utf-8")) != row["expectedLocalPrefixSha256"]
            or _sha_bytes(source_plain.encode("utf-8")) != row["expectedSourcePrefixSha256"]):
        return None

    local_token = row["localToken"]
    source_token = row["sourceToken"]
    if (terminal.count(local_token) != 1 or terminal.count(source_token) != 0
            or source_plain.count(source_token) != 1 or source_plain.count(local_token) != 0
            or terminal.replace(local_token, source_token) != source_plain):
        return None
    local = _parse_tagged(terminal)
    if local.boundaries:
        return None
    if any(
        tagged.plain != source_plain or tagged.boundaries
        for tagged in semantics.values()
    ):
        return None
    start = terminal.index(local_token)
    end = start + len(local_token)
    if not _runtime_covers_untagged_dn(language, terminal, start, end, collection):
        return None
    source_start = source_plain.index(source_token)
    source_end = source_start + len(source_token)
    if _runtime_covers_untagged_dn(
            language, source_plain, source_start, source_end, collection):
        raise RangeMergeError("reviewed source token unexpectedly became a runtime divine name")
    return terminal, 0, 1


def _is_reviewed_terminal_duplicate_chapter(
        language: str, code: str, chapter: int, story_sha: str,
        source_ranges_sha: str,
        reviewed: dict[tuple[str, str, int], tuple[str, str]] | None = None,
        ) -> bool:
    rows = REVIEWED_TERMINAL_DUPLICATE_CHAPTERS if reviewed is None else reviewed
    expected = rows.get((language, code, chapter))
    return expected == (story_sha, source_ranges_sha)


def _reviewed_arabic_dn_prefix(
        *, language: str, code: str, chapter: int, source_unit: tuple[int, int],
        local_prefix: str, source_plain: str, semantics: dict[str, Any],
        ) -> str:
    """Build one exact reviewed SAB prefix with source-proven DN wrappers.

    The source wording remains byte-for-byte identical after app tags are
    removed.  Offsets, lexemes, and all three prefix hashes must match the
    review ledger; there is no language-wide Allah or Mawla recognition.
    """
    row = chapter_dn_rows(language, code, chapter).get(source_unit)
    if row is None:
        raise RangeMergeError("reviewed Arabic divine-name range is absent")
    required = {
        "localPrefixSha256", "sourcePrefixSha256", "taggedPrefixSha256", "spans",
    }
    if set(row) != required:
        raise RangeMergeError("reviewed Arabic divine-name row is invalid")
    if (digest_text(local_prefix) != row["localPrefixSha256"]
            or digest_text(source_plain) != row["sourcePrefixSha256"]):
        raise RangeMergeError("reviewed Arabic divine-name prefix hash changed")
    for kind in ("J", "ADD", "DN"):
        tagged = semantics.get(kind)
        if tagged is None or tagged.plain != source_plain or tagged.boundaries:
            raise RangeMergeError("reviewed Arabic source semantics changed")

    spans = row["spans"]
    if (not isinstance(spans, tuple) or not spans
            or any(not isinstance(span, tuple) or len(span) != 3 for span in spans)):
        raise RangeMergeError("reviewed Arabic divine-name spans are invalid")
    cursor = 0
    pieces: list[str] = []
    for start, end, lexeme in spans:
        if (type(start) is not int or type(end) is not int
                or not isinstance(lexeme, str) or start < cursor or end <= start
                or end > len(source_plain) or source_plain[start:end] != lexeme):
            raise RangeMergeError("reviewed Arabic divine-name offset or lexeme changed")
        pieces.extend((source_plain[cursor:start], "[DN]", lexeme, "[/DN]"))
        cursor = end
    pieces.append(source_plain[cursor:])
    tagged_prefix = "".join(pieces)
    if digest_text(tagged_prefix) != row["taggedPrefixSha256"]:
        raise RangeMergeError("reviewed Arabic tagged prefix hash changed")
    parsed = _parse_tagged(tagged_prefix)
    if (parsed.plain != source_plain
            or len(parsed.boundaries) != len(spans) * 2
            or any(boundary.kind != "DN" for boundary in parsed.boundaries)):
        raise RangeMergeError("reviewed Arabic tagging changed source Scripture")
    return tagged_prefix


def _reviewed_arabic_state(
        *, language: str, code: str, chapter: int, story: dict[str, Any],
        source: Any, source_units: list[tuple[int, int]],
        semantics: dict[str, dict[tuple[int, int], Any]],
        ) -> tuple[str | None, list[dict[str, Any]] | None]:
    """Return a fully proven pre/post state for a reviewed SAB chapter."""
    row = REVIEWED_ARABIC_CHAPTERS.get((language, code, chapter))
    if row is None:
        return None, None
    required = {
        "preStorySha256", "postStorySha256", "sourceRangesSha256",
        "sourceTextSha256", "localHeadingsSha256", "sourceHeadingsSha256",
        "postDivineNameSpanCount",
    }
    if set(row) != required:
        raise RangeMergeError("reviewed Arabic chapter row is invalid")
    story_sha = digest_json(story)
    if story_sha == row["preStorySha256"]:
        state = "pre"
    elif story_sha == row["postStorySha256"]:
        state = "post"
    else:
        raise RangeMergeError("reviewed Arabic chapter story hash changed")
    if (digest_json(source_units) != row["sourceRangesSha256"]
            or digest_json(list(source.ranges.items())) != row["sourceTextSha256"]):
        raise RangeMergeError("reviewed Arabic rendered source hash changed")
    if not source.headings_available:
        raise RangeMergeError("reviewed Arabic source headings are unavailable")
    try:
        headings = _source_headings(source.headings, {start for start, _end in source_units})
    except HeadingSyncError as exc:
        raise RangeMergeError("reviewed Arabic source headings are invalid") from exc
    if digest_json(headings) != row["sourceHeadingsSha256"]:
        raise RangeMergeError("reviewed Arabic source heading hash changed")
    existing = story.get("headings") if "headings" in story else None
    if state == "pre" and digest_json(existing) != row["localHeadingsSha256"]:
        raise RangeMergeError("reviewed Arabic local heading hash changed")
    if state == "post" and existing != headings:
        raise RangeMergeError("reviewed Arabic post-repair headings changed")
    expected_units = set(source_units)
    for kind in ("J", "ADD", "DN"):
        values = semantics.get(kind)
        if values is None or set(values) != expected_units:
            raise RangeMergeError("reviewed Arabic semantic inventory changed")
        for unit, tagged in values.items():
            if tagged.plain != source.ranges[unit] or tagged.boundaries:
                raise RangeMergeError("reviewed Arabic publisher semantics changed")
    return state, headings


def _groups(local_units: list[tuple[int, int]], source_units: list[tuple[int, int]]) -> list[tuple[int, int]]:
    if not source_units or not local_units or len(source_units) > len(local_units):
        raise RangeMergeError("source native unit count exceeds local units")
    if any(start <= previous[1] for previous, (start, _end) in
           zip(local_units, local_units[1:])):
        raise RangeMergeError("local native units overlap or regress")
    if any(start <= previous[1] for previous, (start, _end) in
           zip(source_units, source_units[1:])):
        raise RangeMergeError("source native units overlap or regress")
    groups: list[tuple[int, int]] = []
    cursor = 0
    for source_start, source_end in source_units:
        first = cursor
        if cursor >= len(local_units) or local_units[cursor][0] != source_start:
            raise RangeMergeError("source and local native starts differ")
        while cursor < len(local_units) and local_units[cursor][1] < source_end:
            if cursor + 1 >= len(local_units) or local_units[cursor + 1][0] != local_units[cursor][1] + 1:
                raise RangeMergeError("local gap falls inside a source native range")
            cursor += 1
        if cursor >= len(local_units) or local_units[cursor][1] != source_end:
            raise RangeMergeError("local units do not exactly cover source range")
        cursor += 1
        groups.append((first, cursor))
    if cursor != len(local_units):
        raise RangeMergeError("source range coverage is incomplete")
    return groups


def _raw_merge_edits(raw: bytes, story_index: int, old_bullets: list[str],
                     new_bullets: list[str], groups: list[tuple[int, int]]) -> list[RawEdit]:
    text = raw.decode("utf-8")
    try:
        fields = _object_fields(text, _story_start(text, story_index))
        spans = _array_item_spans(text, fields["summaryBullets"][0])
    except (ScriptureSyncError, KeyError, ValueError, IndexError) as exc:
        raise RangeMergeError("raw target story structure is invalid") from exc
    if len(spans) != len(old_bullets) or len(groups) != len(new_bullets):
        raise RangeMergeError("raw and parsed bullet counts differ")
    for (start, end), bullet in zip(spans, old_bullets):
        if json.loads(text[start:end]) != bullet:
            raise RangeMergeError("raw bullet span does not match parsed asset")
    edits: list[RawEdit] = []
    for (first, last), merged in zip(groups, new_bullets):
        if last - first == 1:
            if merged == old_bullets[first]:
                continue
            start, end = spans[first]
            replacement = json.dumps(merged, ensure_ascii=False)
            if json.loads(replacement) != merged:
                raise RangeMergeError("unmerged bullet encoding mismatch")
            edits.append(RawEdit(start, end, replacement))
            continue
        start, end = spans[first][0], spans[last - 1][1]
        replacement = json.dumps(merged, ensure_ascii=False)
        if json.loads(replacement) != merged:
            raise RangeMergeError("merged bullet encoding mismatch")
        edits.append(RawEdit(start, end, replacement))
    return edits


def sync_snapshot(snapshot: dict[str, Any], *, root: Path = ROOT, apply: bool = False) -> dict[str, Any]:
    try:
        language, chapter, code, collection, book_id, path = _identity(snapshot, root)
        records = snapshot.get("records")
        if (not isinstance(records, list) or len(records) > MAX_RECORDS
                or not all(isinstance(record, dict) for record in records)):
            raise RangeMergeError("snapshot record limit or structure invalid")
        raw = path.read_bytes()
        payload = json.loads(raw.decode("utf-8-sig"))
        story_index, story, local_units = _local_story(payload, book_id, chapter)
        source = extract_browser_records(records, code, chapter)
        source_units = list(source.ranges)
        groups = _groups(local_units, source_units)
        anchors = set(_heading_anchors(story)) | {anchor for anchor, _text in source.headings}
        semantics: dict[str, dict[tuple[int, int], Any]] | None = None
        story_sha = _sha_json(story)
        source_ranges_sha = _sha_json(source_units)
        reviewed_arabic_state: str | None = None
        reviewed_arabic_headings: list[dict[str, Any]] | None = None
        if (language, code, chapter) in REVIEWED_ARABIC_CHAPTERS:
            semantics = _source_semantics(records, code, chapter, source.ranges)
            reviewed_arabic_state, reviewed_arabic_headings = _reviewed_arabic_state(
                language=language, code=code, chapter=chapter, story=story,
                source=source, source_units=source_units, semantics=semantics,
            )
        reviewed_arabic_atomic = reviewed_arabic_state == "pre"
        reviewed_arabic_dn_rows = (
            chapter_dn_rows(language, code, chapter) if reviewed_arabic_atomic else {}
        )
        reviewed_terminal_duplicate_chapter = _is_reviewed_terminal_duplicate_chapter(
            language, code, chapter, story_sha, source_ranges_sha,
        )

        old_bullets = story["summaryBullets"]
        new_bullets: list[str] = []
        source_synced_groups = 0
        semantic_verified_groups = 0
        preserved_boundaries = 0
        preserved_runtime_dn_spans = 0
        terminal_collapsed_groups = 0
        reviewed_terminal_dn_presentation_groups = 0
        reviewed_arabic_dn_groups = 0
        reviewed_arabic_dn_units_seen: set[tuple[int, int]] = set()
        for source_index, ((first, last), source_unit) in enumerate(zip(groups, source_units)):
            source_start, source_end = source_unit
            if (not reviewed_arabic_atomic and last - first > 1
                    and any(source_start < anchor <= source_end for anchor in anchors)):
                raise RangeMergeError("heading anchor falls inside merged native range")
            selected = old_bullets[first:last]
            reference = f"{code}.{chapter}.{source_start}-{source_end}#{source_index}"
            reviewed_arabic_dn = reviewed_arabic_dn_rows.get(source_unit)
            if reviewed_arabic_dn is not None:
                if semantics is None:  # pragma: no cover - review state initializes it
                    raise RangeMergeError("reviewed Arabic semantic proof is unavailable")
                source_kinds = {
                    kind: by_range[source_unit] for kind, by_range in semantics.items()
                }
                prefix = _reviewed_arabic_dn_prefix(
                    language=language, code=code, chapter=chapter,
                    source_unit=source_unit, local_prefix=_prefix(selected[-1]),
                    source_plain=source.ranges[source_unit], semantics=source_kinds,
                )
                reviewed_arabic_dn_units_seen.add(source_unit)
                reviewed_arabic_dn_groups += 1
                source_synced_groups += prefix != _prefix(selected[-1])
                semantic_verified_groups += 1
                preserved_boundaries += len(_parse_tagged(prefix).boundaries)
                if len(selected) > 1:
                    terminal_collapsed_groups += 1
                marked = selected[0] if len(selected) == 1 else _updated_marker(selected[0], source_end)
                marker = TRAILING_MARKER_RE.search(marked)
                if marker is None:
                    raise RangeMergeError("reviewed Arabic bullet lost native marker")
                new_bullets.append(prefix + marked[marker.start():])
                continue
            if len(selected) == 1:
                prefix = _verified_merged_prefix(
                    selected, source.ranges[source_unit], source.divine_markup[source_unit],
                    language, collection, reference,
                )
                if prefix is None:
                    if reviewed_arabic_atomic:
                        raise RangeMergeError(
                            "reviewed Arabic singleton differs outside divine-name ledger"
                        )
                    if semantics is None:
                        semantics = _source_semantics(records, code, chapter, source.ranges)
                    source_kinds = {
                        kind: by_range[source_unit] for kind, by_range in semantics.items()
                    }
                    try:
                        prefix, boundary_count, runtime_dn_count = _confirmed_tagged_replacement(
                            _prefix(selected[0]), source.ranges[source_unit], source_kinds,
                            language=language, collection=collection,
                        )
                    except ScriptureSyncError as exc:
                        raise RangeMergeError(
                            "source semantic boundaries do not confirm unmerged scripture"
                        ) from exc
                    source_synced_groups += prefix != _prefix(selected[0])
                    semantic_verified_groups += 1
                    preserved_boundaries += boundary_count
                    preserved_runtime_dn_spans += runtime_dn_count
                    marker = TRAILING_MARKER_RE.search(selected[0])
                    if marker is None:
                        raise RangeMergeError("target bullet lacks trailing native marker")
                    new_bullets.append(prefix + selected[0][marker.start():])
                else:
                    new_bullets.append(selected[0])
            else:
                if semantics is None:
                    semantics = _source_semantics(records, code, chapter, source.ranges)
                source_kinds = {
                    kind: by_range[source_unit] for kind, by_range in semantics.items()
                }
                terminal_proof = _verified_terminal_duplicate_prefix(
                    selected, source.ranges[source_unit], source.divine_markup[source_unit],
                    source_kinds, language, collection, reference,
                )
                reviewed_terminal_proof = None
                if terminal_proof is None:
                    reviewed_terminal_proof = _reviewed_terminal_dn_presentation_prefix(
                        language=language, code=code, chapter=chapter,
                        collection=collection, source_unit=source_unit,
                        terminal=_prefix(selected[-1]),
                        source_plain=source.ranges[source_unit],
                        semantics=source_kinds, story_sha=story_sha,
                        source_ranges_sha=source_ranges_sha,
                    )
                if terminal_proof is not None or reviewed_terminal_proof is not None:
                    proof = terminal_proof or reviewed_terminal_proof
                    if proof is None:  # pragma: no cover - narrowed above for type checking
                        raise RangeMergeError("terminal duplicate proof vanished")
                    prefix, boundary_count, runtime_dn_count = proof
                    terminal_collapsed_groups += 1
                    reviewed_terminal_dn_presentation_groups += reviewed_terminal_proof is not None
                else:
                    if reviewed_terminal_duplicate_chapter:
                        raise RangeMergeError(
                            "reviewed terminal duplicate group differs from rendered source"
                        )
                    large_group = len(selected) - 1 > MAX_ENUMERATED_JOIN_BOUNDARIES
                    prefix = None if large_group else _verified_merged_prefix(
                        selected, source.ranges[source_unit], source.divine_markup[source_unit],
                        language, collection, reference,
                    )
                    if prefix is None:
                        prefix, boundary_count, runtime_dn_count = _confirmed_source_prefix(
                            selected, source.ranges[source_unit], source_kinds,
                            language, collection, large_group=large_group,
                        )
                        source_synced_groups += 1
                    else:
                        original_prefix = prefix
                        try:
                            prefix, boundary_count, runtime_dn_count = _confirmed_tagged_replacement(
                                prefix, source.ranges[source_unit], source_kinds,
                                language=language, collection=collection,
                            )
                        except ScriptureSyncError as exc:
                            raise RangeMergeError(
                                "source semantic boundaries do not confirm merged scripture"
                            ) from exc
                        source_synced_groups += prefix != original_prefix
                semantic_verified_groups += 1
                preserved_boundaries += boundary_count
                preserved_runtime_dn_spans += runtime_dn_count
                marker = TRAILING_MARKER_RE.search(selected[0])
                if marker is None:
                    raise RangeMergeError("target bullet lacks trailing native marker")
                # First marker supplies the existing punctuation and typography;
                # its numeric end comes from the verified native source range.
                marked = _updated_marker(selected[0], source_end)
                updated_marker = TRAILING_MARKER_RE.search(marked)
                if updated_marker is None:
                    raise RangeMergeError("merged bullet lost native marker")
                new_bullets.append(prefix + marked[updated_marker.start():])

        if reviewed_arabic_atomic:
            if reviewed_arabic_dn_units_seen != set(reviewed_arabic_dn_rows):
                raise RangeMergeError("reviewed Arabic divine-name range coverage changed")
            expected_collapses = sum(last - first > 1 for first, last in groups)
            if terminal_collapsed_groups != expected_collapses:
                raise RangeMergeError("reviewed Arabic bridge did not use terminal collapse")
            for source_unit, bullet in zip(source_units, new_bullets):
                marker = TRAILING_MARKER_RE.search(bullet)
                if marker is None or _parse_tagged(bullet[:marker.start()]).plain != source.ranges[source_unit]:
                    raise RangeMergeError("reviewed Arabic output differs from SAB source text")

        edits = _raw_merge_edits(raw, story_index, old_bullets, new_bullets, groups)
        updated_raw, untouched_proof = _apply_raw_edits(raw, edits)
        existing_headings = story.get("headings") if "headings" in story else None
        if reviewed_arabic_atomic:
            if reviewed_arabic_headings is None:  # pragma: no cover - state proves it
                raise RangeMergeError("reviewed Arabic heading proof vanished")
            if existing_headings != reviewed_arabic_headings:
                try:
                    updated_raw = _replace_heading_value(
                        updated_raw, story_index, reviewed_arabic_headings, existing_headings,
                    )
                except HeadingSyncError as exc:
                    raise RangeMergeError("reviewed Arabic atomic heading update failed") from exc
        expected = deepcopy(payload)
        expected["stories"][story_index]["summaryBullets"] = new_bullets
        if reviewed_arabic_atomic:
            expected["stories"][story_index]["headings"] = reviewed_arabic_headings
            review_row = REVIEWED_ARABIC_CHAPTERS[(language, code, chapter)]
            expected_story = expected["stories"][story_index]
            if digest_json(expected_story) != review_row["postStorySha256"]:
                raise RangeMergeError("reviewed Arabic post-repair story hash changed")
            serialized_story = json.dumps(expected_story, ensure_ascii=False, separators=(",", ":"))
            if (serialized_story.count("[J]") or serialized_story.count("[/J]")
                    or serialized_story.count("[ADD]") or serialized_story.count("[/ADD]")
                    or serialized_story.count("[DN]") != review_row["postDivineNameSpanCount"]
                    or serialized_story.count("[/DN]") != review_row["postDivineNameSpanCount"]):
                raise RangeMergeError("reviewed Arabic post-repair tag counts changed")
        if json.loads(updated_raw.decode("utf-8-sig")) != expected:
            raise RangeMergeError("raw update changed non target json data")
        changed = updated_raw != raw
        result = {
            "status": "changed" if changed else "match",
            "mode": "apply" if apply else "audit",
            "language": language,
            "bibleId": EDITIONS[language].bible_id,
            "book": code,
            "chapter": chapter,
            "collection": collection,
            "localUnitCount": len(local_units),
            "sourceUnitCount": len(source_units),
            "mergedGroupCount": len(edits),
            "sourceSyncedGroupCount": source_synced_groups,
            "terminalCollapsedGroupCount": terminal_collapsed_groups,
            "reviewedTerminalDivineNamePresentationGroupCount": reviewed_terminal_dn_presentation_groups,
            "reviewedTerminalDuplicateChapter": reviewed_terminal_duplicate_chapter,
            "reviewedArabicAtomicState": reviewed_arabic_state,
            "reviewedArabicDivineNameGroupCount": reviewed_arabic_dn_groups,
            "semanticVerifiedGroupCount": semantic_verified_groups,
            "preservedTagBoundaryCount": preserved_boundaries,
            "preservedRuntimeDivineNameSpanCount": preserved_runtime_dn_spans,
            "removedBulletCount": len(local_units) - len(source_units),
            "beforeSha256": _sha_bytes(raw),
            "afterSha256": _sha_bytes(updated_raw),
            "localRangesSha256": _sha_json(local_units),
            "sourceRangesSha256": source_ranges_sha,
            "untouchedRawSegmentsSha256": untouched_proof,
        }
        if apply and changed:
            if path.read_bytes() != raw:
                raise RangeMergeError("local asset changed during audit")
            temporary: str | None = None
            try:
                with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".range-merges-", suffix=".tmp", delete=False) as handle:
                    temporary = handle.name
                    handle.write(updated_raw)
                    handle.flush()
                    os.fsync(handle.fileno())
                os.replace(temporary, path)
            finally:
                if temporary and os.path.exists(temporary):
                    os.unlink(temporary)
        return result
    except RangeSyncError as exc:
        raise RangeMergeError(str(exc)) from exc


SAFE_EXCEPTIONS = (
    RangeMergeError, BrowserAuditError, ScriptureSyncError, OSError, UnicodeError,
    ValueError, KeyError, IndexError, TypeError,
)


def _blocked_result(exc: BaseException) -> dict[str, str]:
    result = {"status": "blocked", "errorType": type(exc).__name__}
    if type(exc) is RangeMergeError:
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
                raise RangeMergeError("snapshot exceeds size limit")
            snapshot = json.loads(line)
            if not isinstance(snapshot, dict):
                raise RangeMergeError("snapshot must be json object")
            result = sync_snapshot(snapshot, root=root, apply=apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
            status = 2
        sink.write(json.dumps(result, ensure_ascii=False, sort_keys=True) + "\n")
        sink.flush()
    return status


def create_snapshot_server(port: int, *, root: Path = ROOT, apply: bool = False) -> HTTPServer:
    """Accept sequential merge snapshots over loopback without storage."""

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
                    raise RangeMergeError("invalid request length")
                if self.headers.get_content_type() != "application/json":
                    raise RangeMergeError("invalid content type")
                length = int(length_text)
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise RangeMergeError("incomplete request body")
                snapshot = json.loads(raw.decode("utf-8"))
                if not isinstance(snapshot, dict):
                    raise RangeMergeError("snapshot must be json object")
                result = sync_snapshot(snapshot, root=root, apply=apply)
            except SAFE_EXCEPTIONS as exc:
                result = _blocked_result(exc)
            self._respond(200, result)

    return HTTPServer(("127.0.0.1", port), Handler)


def serve_snapshots(port: int, *, root: Path = ROOT, apply: bool = False) -> None:
    server = create_snapshot_server(port, root=root, apply=apply)
    try:
        print(f"Range merge snapshot server listening on 127.0.0.1:{server.server_port}", flush=True)
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="merge only fully verified native ranges")
    parser.add_argument("--root", type=Path, default=ROOT, help="repository root")
    parser.add_argument("--snapshot-server", action="store_true", help="accept snapshots over loopback POST /snapshot")
    parser.add_argument("--snapshot-stdin", action="store_true", help="read one snapshot JSON object")
    parser.add_argument("--port", type=int, default=9229, help="loopback server port; default 9229")
    args = parser.parse_args()
    if args.snapshot_server:
        if args.snapshot_stdin or not 1 <= args.port <= 65535:
            parser.error("--snapshot-server requires valid --port and cannot use --snapshot-stdin")
        try:
            serve_snapshots(args.port, root=args.root, apply=args.apply)
        except OSError:
            print("Range merge snapshot server could not bind loopback port", file=sys.stderr)
            return 2
        return 0
    if args.snapshot_stdin:
        try:
            snapshot = json.load(sys.stdin)
            if not isinstance(snapshot, dict):
                raise RangeMergeError("snapshot must be json object")
            result = sync_snapshot(snapshot, root=args.root, apply=args.apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 2 if result["status"] == "blocked" else 0
    return stream_snapshots(sys.stdin, sys.stdout, root=args.root, apply=args.apply)


if __name__ == "__main__":
    raise SystemExit(main())
