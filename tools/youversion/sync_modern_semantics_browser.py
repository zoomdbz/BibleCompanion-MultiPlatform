#!/usr/bin/env python3
"""Audit or sync exact Bible.com text and independently proven semantic spans.

This fallback handles single native ranges whose local text/tags differ from
the rendered source. No publisher text leaves the process. Apply is explicit.
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
from audit_base_heading_tables import TRAILING_MARKER_RE, parse_trailing_marker  # noqa: E402
from compare_modern_editions import EDITIONS  # noqa: E402
from compare_modern_editions_browser import BrowserAuditError, extract_browser_records  # noqa: E402
from compare_biblecom import BLOCK_BOUNDARY, normalize_boundary_whitespace  # noqa: E402
from sync_modern_scripture_browser import (  # noqa: E402
    ROOT, MAX_RECORDS, MAX_SNAPSHOT, Boundary, DivineNameCoverageError,
    ScriptureSyncError, _apply_raw_replacements, _identity, _local_story,
    TaggedText, _confirmed_tagged_replacement,
    _parse_tagged, _semantic_spans,
    _raw_replacements,
    _sha_bytes, _sha_json, _source_semantics,
)


class SemanticSyncError(ScriptureSyncError):
    """A semantic reconstruction has insufficient proof."""


KINDS = ("J", "ADD", "DN")


def _tagged_source(local_prefix: str, source_plain: str, semantics: dict[str, Any],
                   *, language: str, collection: str) -> tuple[str, int, int]:
    return _confirmed_tagged_replacement(
        local_prefix,
        source_plain,
        semantics,
        language=language,
        collection=collection,
    )


def _slice_semantics_after_superscription(
    semantics: dict[str, Any], source_plain: str, body_start: int,
) -> dict[str, TaggedText]:
    """Retain every rendered body span while excluding the separate title."""
    body_plain = source_plain[body_start:]
    result: dict[str, TaggedText] = {}
    for kind in KINDS:
        tagged = semantics.get(kind)
        if not isinstance(tagged, TaggedText) or tagged.plain != source_plain:
            raise SemanticSyncError("semantic source extraction changed Scripture text")
        shifted: list[tuple[int, int]] = []
        for start, end in _semantic_spans(tagged, kind):
            if end <= body_start:
                # This source span belongs wholly to the separately rendered
                # superscription, not to the Scripture bullet.
                continue
            if start < body_start:
                raise SemanticSyncError("source semantic span crosses superscription boundary")
            shifted.append((start - body_start, end - body_start))
        boundaries: list[Boundary] = []
        for start, end in shifted:
            boundaries.append(Boundary(kind, True, start, len(boundaries), f"[{kind}]"))
            boundaries.append(Boundary(kind, False, end, len(boundaries), f"[/{kind}]"))
        sliced = TaggedText(body_plain, tuple(boundaries))
        if _semantic_spans(sliced, kind) != tuple(shifted):
            raise SemanticSyncError("body semantic reconstruction changed source spans")
        result[kind] = sliced
    return result


def _body_source_for_separate_superscription(
    story: dict[str, Any], key: tuple[int, int], first_range: tuple[int, int],
    fragment_count: int, local_prefix: str, source_plain: str,
    semantics: dict[str, Any],
) -> tuple[str, dict[str, Any]]:
    """Prove and remove a rendered superscription from the first source unit."""
    if key != first_range or "superscription" not in story:
        return source_plain, semantics
    local_body_plain = normalize_boundary_whitespace(_parse_tagged(local_prefix).plain)
    if source_plain == local_body_plain:
        return source_plain, semantics
    value = story["superscription"]
    if not isinstance(value, str) or not value.strip():
        raise SemanticSyncError("local superscription is empty or invalid")
    superscription_plain = normalize_boundary_whitespace(_parse_tagged(value).plain)
    combined = normalize_boundary_whitespace(
        superscription_plain + BLOCK_BOUNDARY + local_body_plain
    )
    if fragment_count <= 1 or combined != source_plain:
        raise SemanticSyncError("source superscription lacks exact split-text proof")
    body_start = len(superscription_plain) + 1
    if (source_plain[:body_start - 1] != superscription_plain
            or source_plain[body_start:] != local_body_plain):
        raise SemanticSyncError("source superscription boundary is not exact")
    return local_body_plain, _slice_semantics_after_superscription(
        semantics, source_plain, body_start
    )


def sync_snapshot(snapshot: dict[str, Any], *, root: Path = ROOT, apply: bool = False) -> dict[str, Any]:
    language, chapter, code, collection, book_id, path = _identity(snapshot, root)
    records = snapshot.get("records")
    if (not isinstance(records, list) or len(records) > MAX_RECORDS
            or not all(isinstance(record, dict) for record in records)):
        raise SemanticSyncError("snapshot record limit or structure invalid")
    raw = path.read_bytes()
    payload = json.loads(raw.decode("utf-8-sig"))
    story_index, story, local_ranges = _local_story(payload, book_id, chapter)
    source = extract_browser_records(records, code, chapter)
    if list(source.ranges) != local_ranges:
        raise SemanticSyncError("source and local native range inventories differ")
    semantics = _source_semantics(records, code, chapter, source.ranges)
    bullets = story["summaryBullets"]
    prefixes: list[str] = []
    local_hashes: list[str] = []
    source_hashes: list[str] = []
    local_boundary_count = runtime_count = changed_ranges = 0
    for bullet, key in zip(bullets, local_ranges):
        marker = TRAILING_MARKER_RE.search(bullet)
        if marker is None:
            raise SemanticSyncError("target bullet lacks trailing native marker")
        local_prefix = bullet[:marker.start()]
        source_plain = source.ranges[key]
        source_kinds = {kind: semantics[kind][key] for kind in KINDS}
        source_plain, source_kinds = _body_source_for_separate_superscription(
            story, key, local_ranges[0], source.fragment_counts.get(key, 0),
            local_prefix, source_plain, source_kinds,
        )
        prefix, boundaries, runtime = _tagged_source(
            local_prefix, source_plain, source_kinds, language=language, collection=collection
        )
        prefixes.append(prefix)
        local_hashes.append(_sha_json(local_prefix))
        source_hashes.append(_sha_json(source_plain))
        local_boundary_count += boundaries
        runtime_count += runtime
        changed_ranges += prefix != local_prefix
    replacements = _raw_replacements(raw, story_index, bullets, prefixes)
    updated, untouched_hash, marker_hash = _apply_raw_replacements(raw, replacements)
    if len(replacements) != changed_ranges:
        raise SemanticSyncError("raw and parsed change counts differ")
    expected = deepcopy(payload)
    expected_bullets = expected["stories"][story_index]["summaryBullets"]
    for index, (bullet, prefix) in enumerate(zip(bullets, prefixes)):
        marker = TRAILING_MARKER_RE.search(bullet)
        expected_bullets[index] = prefix + bullet[marker.start():]
    if json.loads(updated.decode("utf-8-sig")) != expected:
        raise SemanticSyncError("raw replacement changed non-target JSON value")
    for old, new in zip(bullets, expected_bullets):
        old_marker = TRAILING_MARKER_RE.search(old)
        if old_marker is None:
            raise SemanticSyncError("trailing marker changed")
        # Publisher text can end in whitespace.  A second regex search would
        # absorb that new whitespace into the marker's leading ``\s*`` even
        # though the raw original marker suffix remains byte-identical.
        old_suffix = old[old_marker.start():]
        if not new.endswith(old_suffix):
            raise SemanticSyncError("trailing marker changed")
        if parse_trailing_marker(old, 0) != parse_trailing_marker(new, 0):
            raise SemanticSyncError("trailing marker changed")
    result = {
        "status": "changed" if updated != raw else "match", "mode": "apply" if apply else "audit",
        "language": language, "bibleId": EDITIONS[language].bible_id, "book": code,
        "chapter": chapter, "collection": collection, "nativeRangeCount": len(local_ranges),
        "changedRangeCount": changed_ranges, "localTagBoundaryCount": local_boundary_count,
        "preservedRuntimeDivineNameSpanCount": runtime_count,
        "beforeSha256": _sha_bytes(raw), "afterSha256": _sha_bytes(updated),
        "localPrefixSetSha256": _sha_json(local_hashes), "sourcePrefixSetSha256": _sha_json(source_hashes),
        "untouchedRawSegmentsSha256": untouched_hash, "preservedRawMarkersSha256": marker_hash,
    }
    if apply and updated != raw:
        if path.read_bytes() != raw:
            raise SemanticSyncError("local asset changed during audit")
        temporary: str | None = None
        try:
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".semantics-", suffix=".tmp", delete=False) as handle:
                temporary = handle.name
                handle.write(updated)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)
    return result


SAFE_EXCEPTIONS = (ScriptureSyncError, BrowserAuditError, OSError, UnicodeError,
                   ValueError, KeyError, IndexError, TypeError)


def _blocked_result(exc: BaseException) -> dict[str, str]:
    result = {"status": "blocked", "errorType": type(exc).__name__}
    if type(exc) in {SemanticSyncError, ScriptureSyncError, DivineNameCoverageError}:
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
                raise SemanticSyncError("snapshot exceeds size limit")
            snapshot = json.loads(line)
            if not isinstance(snapshot, dict):
                raise SemanticSyncError("snapshot must be a JSON object")
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
                length_text = self.headers.get("Content-Length", "")
                if not length_text.isdecimal() or not 1 <= int(length_text) <= MAX_SNAPSHOT:
                    raise SemanticSyncError("invalid request length")
                if self.headers.get_content_type() != "application/json":
                    raise SemanticSyncError("invalid content type")
                body = self.rfile.read(int(length_text))
                if len(body) != int(length_text):
                    raise SemanticSyncError("incomplete request body")
                snapshot = json.loads(body.decode("utf-8"))
                if not isinstance(snapshot, dict):
                    raise SemanticSyncError("snapshot must be a JSON object")
                result = sync_snapshot(snapshot, root=root, apply=apply)
            except SAFE_EXCEPTIONS as exc:
                result = _blocked_result(exc)
            self._respond(200, result)

    return HTTPServer(("127.0.0.1", port), Handler)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="write verified Scripture prefixes")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--snapshot-server", action="store_true")
    parser.add_argument("--snapshot-stdin", action="store_true")
    parser.add_argument("--port", type=int, default=9230)
    args = parser.parse_args()
    if args.snapshot_server:
        if args.snapshot_stdin or not 1 <= args.port <= 65535:
            parser.error("--snapshot-server requires a valid port and excludes --snapshot-stdin")
        try:
            server = create_snapshot_server(args.port, root=args.root, apply=args.apply)
            try:
                print(f"Semantic snapshot server listening on 127.0.0.1:{server.server_port}", flush=True)
                server.serve_forever(poll_interval=0.2)
            finally:
                server.server_close()
        except OSError:
            print("Semantic snapshot server could not bind loopback port", file=sys.stderr)
            return 2
        return 0
    if args.snapshot_stdin:
        try:
            snapshot = json.load(sys.stdin)
            if not isinstance(snapshot, dict):
                raise SemanticSyncError("snapshot must be a JSON object")
            result = sync_snapshot(snapshot, root=args.root, apply=args.apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 2 if result["status"] == "blocked" else 0
    return stream_snapshots(sys.stdin, sys.stdout, root=args.root, apply=args.apply)


if __name__ == "__main__":
    raise SystemExit(main())
