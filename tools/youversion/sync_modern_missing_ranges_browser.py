#!/usr/bin/env python3
"""Audit or insert whole, absent Bible.com native units into one local chapter.

Input is a rendered browser snapshot on JSON Lines, stdin, or loopback POST.
Publisher text stays in memory. Apply mode changes only the target story's
summaryBullets array by inserting source-native units; it never splits units.
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
from compare_modern_editions_browser import BrowserAuditError, _range_from_usfm, extract_browser_records  # noqa: E402
from sync_modern_ranges_browser import (  # noqa: E402
    MAX_RECORDS, MAX_SNAPSHOT, ROOT, RANGE_CORE, SINGLE_CORE, RangeSyncError,
    _heading_anchors, _identity, _local_story, _sha_bytes, _sha_json,
)
from sync_modern_scripture_browser import (  # noqa: E402
    ScriptureSyncError, _array_item_spans, _object_fields, _parse_tagged,
    _semantic_spans, _source_semantics, _story_start,
)


class MissingRangeSyncError(ValueError):
    """The requested insertion is not proven safe."""


def _marker_style(bullets: list[str], chapter: int) -> tuple[str, str, str, str, str, str, str]:
    """Infer one marker envelope and one range separator from existing bullets."""
    styles: set[tuple[str, str, str, str, str, str]] = set()
    separators: set[str] = set()
    for bullet in bullets:
        marker = TRAILING_MARKER_RE.search(bullet)
        if marker is None:
            raise MissingRangeSyncError("target bullet lacks trailing native marker")
        body = marker.group("body")
        match = RANGE_CORE.fullmatch(body) or SINGLE_CORE.fullmatch(body)
        if match is None:
            raise MissingRangeSyncError("marker style is not safely reproducible")
        if "chapter" in match.groupdict() and int(match.group("chapter")) != chapter:
            raise MissingRangeSyncError("cross chapter marker")
        lead = marker.group(0)[:marker.start("body") - marker.start()]
        tail = marker.group(0)[marker.end("body") - marker.start():]
        pre = body[:match.start("start")]
        post = body[match.end("end") if match.group("end") is not None else match.end("start"):]
        # A single-chapter marker and a chapter:verse marker are distinct
        # grammars; _local_story already validates which one applies.
        styles.add((lead, pre, post, tail, "chapter" if "chapter" in match.groupdict() else "single", match.group("colon") if "colon" in match.groupdict() else ""))
        if match.group("separator") is not None:
            separators.add(match.group("separator"))
    if len(styles) != 1 or len(separators) > 1:
        raise MissingRangeSyncError("ambiguous trailing marker style")
    lead, pre, post, tail, grammar, colon = next(iter(styles))
    return lead, pre, post, tail, grammar, colon, next(iter(separators), "-")


def _new_marker(style: tuple[str, str, str, str, str, str, str], chapter: int,
                key: tuple[int, int]) -> str:
    lead, pre, post, tail, grammar, _colon, separator = style
    # pre includes chapter and colon in the existing style. Replace its
    # chapter digits rather than assuming ASCII punctuation or spacing.
    if grammar == "chapter":
        pre = re.sub(r"^\d+", str(chapter), pre, count=1)
    start, end = key
    body = pre + str(start) + (separator + str(end) if end != start else "") + post
    marker = lead + body + tail
    if TRAILING_MARKER_RE.fullmatch(marker) is None:
        raise MissingRangeSyncError("inferred marker is invalid")
    return marker


def _source_prefix(plain: str, semantics: dict[str, Any]) -> str:
    if re.search(r"\[/?(?:J|ADD|DN)\]", plain):
        raise MissingRangeSyncError("source text contains app control tag")
    spans: list[tuple[int, int, str]] = []
    for kind in ("J", "ADD", "DN"):
        spans.extend((start, end, kind) for start, end in _semantic_spans(semantics[kind], kind))
    starts: dict[int, list[tuple[int, str]]] = {}
    ends: dict[int, list[tuple[int, str]]] = {}
    for start, end, kind in spans:
        if not 0 <= start < end <= len(plain):
            raise MissingRangeSyncError("source semantic span is invalid")
        starts.setdefault(start, []).append((end, kind))
        ends.setdefault(end, []).append((start, kind))
    priority = {"J": 0, "ADD": 1, "DN": 2}
    output: list[str] = []
    stack: list[str] = []
    for offset in range(len(plain) + 1):
        for _start, kind in sorted(ends.get(offset, []), key=lambda item: (-item[0], -priority[item[1]])):
            if not stack or stack[-1] != kind:
                raise MissingRangeSyncError("crossing source semantic spans")
            output.append(f"[/{kind}]")
            stack.pop()
        for _end, kind in sorted(starts.get(offset, []), key=lambda item: (-item[0], priority[item[1]])):
            output.append(f"[{kind}]")
            stack.append(kind)
        if offset < len(plain):
            output.append(plain[offset])
    if stack:
        raise MissingRangeSyncError("unclosed source semantic span")
    result = "".join(output)
    if _parse_tagged(result).plain != plain:
        raise MissingRangeSyncError("source semantic reconstruction changed text")
    return result


def _insert_raw(raw: bytes, story_index: int, old_bullets: list[str],
                inserted: dict[int, list[str]]) -> tuple[bytes, str]:
    text = raw.decode("utf-8")
    try:
        fields = _object_fields(text, _story_start(text, story_index))
        array_start, array_end = fields["summaryBullets"]
        spans = _array_item_spans(text, array_start)
    except (ScriptureSyncError, KeyError, IndexError, ValueError) as exc:
        raise MissingRangeSyncError("raw target array is invalid") from exc
    if len(spans) != len(old_bullets) or not spans:
        raise MissingRangeSyncError("raw and parsed bullet counts differ")
    for (start, end), bullet in zip(spans, old_bullets):
        if json.loads(text[start:end]) != bullet:
            raise MissingRangeSyncError("raw bullet differs from parsed asset")
    # Reuse the exact whitespace before an existing item as separator. At the
    # end, reuse the last item's whitespace; only new bytes enter the array.
    first_ws = text[array_start + 1:spans[0][0]]
    last_comma = text.rfind(",", spans[-2][1] if len(spans) > 1 else array_start, spans[-1][0])
    end_ws = text[last_comma + 1:spans[-1][0]] if last_comma >= 0 else first_ws
    edits: list[tuple[int, str]] = []
    for index, bullets in sorted(inserted.items()):
        if not bullets or not 0 <= index <= len(spans):
            raise MissingRangeSyncError("invalid insertion slot")
        ws = (first_ws if index == 0 else text[text.rfind(",", spans[index - 1][1], spans[index][0]) + 1:spans[index][0]]) if index < len(spans) else end_ws
        if index > 0 and index < len(spans) and text.rfind(",", spans[index - 1][1], spans[index][0]) < 0:
            raise MissingRangeSyncError("raw array separator is invalid")
        encoded = [json.dumps(value, ensure_ascii=False) for value in bullets]
        if index < len(spans):
            edits.append((spans[index][0], ("," + ws).join(encoded) + "," + ws))
        else:
            edits.append((spans[-1][1], "," + ws + ("," + ws).join(encoded)))
    pieces: list[str] = []
    untouched: list[str] = []
    cursor = 0
    for position, addition in edits:
        if position < cursor or position > array_end:
            raise MissingRangeSyncError("raw insertion escaped target array")
        segment = text[cursor:position]
        pieces.extend((segment, addition))
        untouched.append(segment)
        cursor = position
    untouched.append(text[cursor:])
    pieces.append(text[cursor:])
    return "".join(pieces).encode("utf-8"), _sha_json([_sha_bytes(part.encode("utf-8")) for part in untouched])


def sync_snapshot(snapshot: dict[str, Any], *, root: Path = ROOT, apply: bool = False) -> dict[str, Any]:
    language, chapter, code, collection, book_id, path = _identity(snapshot, root)
    records = snapshot.get("records")
    if (not isinstance(records, list) or len(records) > MAX_RECORDS
            or not all(isinstance(record, dict) for record in records)):
        raise MissingRangeSyncError("snapshot record limit or structure invalid")
    # The generic extractor ignores unrelated/malformed references. A native
    # a/b or other suffix in this chapter cannot establish an absent unit.
    for record in records:
        if record.get("kind") != "verse":
            continue
        usfm = record.get("usfm")
        if isinstance(usfm, str) and usfm.upper().startswith(f"{code}.{chapter}."):
            if _range_from_usfm(usfm, code, chapter) is None:
                raise MissingRangeSyncError("source has suffix or unsupported native reference")
    raw = path.read_bytes()
    payload = json.loads(raw.decode("utf-8-sig"))
    story_index, story, local_units = _local_story(payload, book_id, chapter)
    source = extract_browser_records(records, code, chapter)
    source_units = list(source.ranges)
    source_set = set(source_units)
    if any(unit not in source_set for unit in local_units):
        raise MissingRangeSyncError("local native ranges are not an exact source subset")
    if [unit for unit in source_units if unit in set(local_units)] != local_units:
        raise MissingRangeSyncError("local source unit ordering differs")
    missing = [unit for unit in source_units if unit not in set(local_units)]
    headings = _heading_anchors(story)
    for start, end in missing:
        if any(start <= anchor <= end for anchor in headings):
            raise MissingRangeSyncError("local heading anchor falls in absent range")
        if any(start <= anchor <= end for anchor, _text in source.headings):
            raise MissingRangeSyncError("source heading belongs to absent range")
    style = _marker_style(story["summaryBullets"], chapter)
    semantics = _source_semantics(records, code, chapter, source.ranges) if missing else None
    inserted: dict[int, list[str]] = {}
    local_set = set(local_units)
    slot = 0
    for key in source_units:
        if key in local_set:
            slot += 1
            continue
        if semantics is None:
            raise MissingRangeSyncError("missing source semantic evidence")
        prefix = _source_prefix(source.ranges[key], {kind: semantics[kind][key] for kind in semantics})
        inserted.setdefault(slot, []).append(prefix + _new_marker(style, chapter, key))
    updated, untouched_proof = _insert_raw(raw, story_index, story["summaryBullets"], inserted) if inserted else (raw, _sha_json([_sha_bytes(raw)]))
    expected = deepcopy(payload)
    expected_bullets = expected["stories"][story_index]["summaryBullets"]
    for index in sorted(inserted, reverse=True):
        expected_bullets[index:index] = inserted[index]
    if json.loads(updated.decode("utf-8-sig")) != expected:
        raise MissingRangeSyncError("raw insertion changed non-target json data")
    result = {
        "status": "changed" if inserted else "match", "mode": "apply" if apply else "audit",
        "language": language, "bibleId": EDITIONS[language].bible_id,
        "book": code, "chapter": chapter, "collection": collection,
        "localNativeUnitCount": len(local_units), "sourceNativeUnitCount": len(source_units),
        "insertedNativeUnitCount": len(missing),
        "beforeSha256": _sha_bytes(raw), "afterSha256": _sha_bytes(updated),
        "localRangesSha256": _sha_json(local_units), "sourceRangesSha256": _sha_json(source_units),
        "untouchedRawSegmentsSha256": untouched_proof,
    }
    if apply and inserted:
        if path.read_bytes() != raw:
            raise MissingRangeSyncError("local asset changed during audit")
        temporary: str | None = None
        try:
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".missing-ranges-", suffix=".tmp", delete=False) as handle:
                temporary = handle.name
                handle.write(updated)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)
    return result


SAFE_EXCEPTIONS = (MissingRangeSyncError, RangeSyncError, ScriptureSyncError, BrowserAuditError,
                   OSError, UnicodeError, ValueError, KeyError, IndexError, TypeError)


def _blocked_result(exc: BaseException) -> dict[str, str]:
    result = {"status": "blocked", "errorType": type(exc).__name__}
    if type(exc) in {MissingRangeSyncError, RangeSyncError, ScriptureSyncError}:
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
                raise MissingRangeSyncError("snapshot exceeds size limit")
            snapshot = json.loads(line)
            if not isinstance(snapshot, dict):
                raise MissingRangeSyncError("snapshot must be json object")
            result = sync_snapshot(snapshot, root=root, apply=apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
            status = 2
        sink.write(json.dumps(result, ensure_ascii=False, sort_keys=True) + "\n")
        sink.flush()
    return status


def create_snapshot_server(port: int, *, root: Path = ROOT, apply: bool = False) -> HTTPServer:
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
                length = self.headers.get("Content-Length", "")
                if not length.isdecimal() or not 1 <= int(length) <= MAX_SNAPSHOT:
                    raise MissingRangeSyncError("invalid request length")
                if self.headers.get_content_type() != "application/json":
                    raise MissingRangeSyncError("invalid content type")
                raw = self.rfile.read(int(length))
                if len(raw) != int(length):
                    raise MissingRangeSyncError("incomplete request body")
                snapshot = json.loads(raw.decode("utf-8"))
                if not isinstance(snapshot, dict):
                    raise MissingRangeSyncError("snapshot must be json object")
                result = sync_snapshot(snapshot, root=root, apply=apply)
            except SAFE_EXCEPTIONS as exc:
                result = _blocked_result(exc)
            self._respond(200, result)

    return HTTPServer(("127.0.0.1", port), Handler)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="insert verified missing whole native units")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--snapshot-server", action="store_true")
    parser.add_argument("--snapshot-stdin", action="store_true")
    parser.add_argument("--port", type=int, default=9229)
    args = parser.parse_args()
    if args.snapshot_server:
        if args.snapshot_stdin or not 1 <= args.port <= 65535:
            parser.error("--snapshot-server requires valid --port and cannot use --snapshot-stdin")
        server = create_snapshot_server(args.port, root=args.root, apply=args.apply)
        try:
            print(f"Missing-range snapshot server listening on 127.0.0.1:{server.server_port}", flush=True)
            server.serve_forever(poll_interval=0.2)
        finally:
            server.server_close()
        return 0
    if args.snapshot_stdin:
        try:
            snapshot = json.load(sys.stdin)
            if not isinstance(snapshot, dict):
                raise MissingRangeSyncError("snapshot must be json object")
            result = sync_snapshot(snapshot, root=args.root, apply=args.apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 2 if result["status"] == "blocked" else 0
    return stream_snapshots(sys.stdin, sys.stdout, root=args.root, apply=args.apply)


if __name__ == "__main__":
    raise SystemExit(main())
