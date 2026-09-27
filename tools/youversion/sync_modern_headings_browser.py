#!/usr/bin/env python3
"""Audit or narrowly apply headings from one rendered Bible.com chapter snapshot.

Input is one JSON object per line on stdin or POST /snapshot on loopback, with
language, book (USFM code), chapter, pageUrl, pageTitle, and ordered DOM
records. The legacy bibleId/sourceUrl pair also works. Source text is never
written to a report. Without --apply this command never changes an asset.
The caller must establish that the DOM snapshot came from the declared URL;
snapshot metadata alone cannot authenticate an external web page.
"""

from __future__ import annotations

import argparse
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
from audit_base_heading_tables import parse_trailing_marker  # noqa: E402
from compare_modern_editions import BOOKS, EDITIONS  # noqa: E402
from compare_modern_editions_browser import BrowserAuditError, extract_browser_records  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
BOOK_INDEX = {code: (collection, book_id) for code, collection, book_id in BOOKS}
# Canonical root ids are not always filename stems. These are the exact legacy
# spellings present across every supported language; no payload-defined alias
# is accepted because it could make a copied wrong-book file self-consistent.
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
APP_TAG = re.compile(r"\[/?(?:J|ADD|DN)\]")
MAX_SNAPSHOT = 8_000_000
MAX_HEADINGS = 100
MAX_RECORDS = 2_000
DECODER = json.JSONDecoder()


class HeadingSyncError(ValueError):
    """The snapshot or local asset cannot support a safe heading update."""


def _positive(value: object) -> bool:
    return type(value) is int and value > 0


def _digest(value: object) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")).hexdigest()


def _identity(snapshot: dict[str, Any]) -> tuple[str, int, str, str, str, Path]:
    language, code, chapter = snapshot.get("language"), snapshot.get("book"), snapshot.get("chapter")
    if language not in EDITIONS or code not in BOOK_INDEX or not _positive(chapter):
        raise HeadingSyncError("unknown language, book, or chapter")
    edition = EDITIONS[language]
    if "bibleId" in snapshot and (type(snapshot["bibleId"]) is not int or snapshot["bibleId"] != edition.bible_id):
        raise HeadingSyncError("Bible edition identity mismatch")
    page_url = snapshot.get("pageUrl")
    source_url = snapshot.get("sourceUrl")
    if page_url is not None and source_url is not None and page_url != source_url:
        raise HeadingSyncError("page and source URLs disagree")
    url = page_url if page_url is not None else source_url
    if not isinstance(url, str):
        raise HeadingSyncError("missing source URL")
    if page_url is not None:
        title = snapshot.get("pageTitle")
        if not isinstance(title, str) or not title.strip() or re.search(r"client challenge|captcha|verify you are human", title, re.I):
            raise HeadingSyncError("browser page title is missing or challenged")
    parsed = urlsplit(url)
    # Require the passage URL, not a catalog or generic chapter URL. Query
    # parameters may vary, but they cannot alter the declared passage path.
    passage_paths = {
        f"/bible/{edition.bible_id}/{code}.{chapter}.{abbreviation}".lower()
        for abbreviation in (edition.abbreviation, *edition.published_abbreviation_aliases)
    }
    path_parts = unquote(parsed.path).lower().rstrip("/").split("/")
    # Bible.com can prepend a locale to a passage path, for example /de/bible/.
    if len(path_parts) == 5 and re.fullmatch(r"[a-z]{2}(?:-[a-z]{2})?", path_parts[1]):
        path_parts.pop(1)
    normalized_path = "/".join(path_parts)
    if (parsed.scheme != "https" or parsed.hostname not in {"www.bible.com", "bible.com"}
            or parsed.username or parsed.password or parsed.port not in {None, 443}
            or normalized_path not in passage_paths):
        raise HeadingSyncError("source URL does not identify the declared passage")
    collection, book_id = BOOK_INDEX[code]
    path = ROOT / "shared" / "assets" / "books" / collection / language / f"{book_id}.json"
    return language, chapter, code, collection, book_id, path


def _local_story(payload: Any, book_id: str, chapter: int) -> tuple[int, dict[str, Any], list[tuple[int, int]]]:
    expected_root_id = BOOK_ROOT_IDS.get(book_id)
    if (expected_root_id is None or not isinstance(payload, dict)
            or payload.get("id") != expected_root_id
            or not isinstance(payload.get("stories"), list)):
        raise HeadingSyncError("local book identity or structure mismatch")
    matches: list[tuple[int, dict[str, Any], list[tuple[int, int]]]] = []
    for index, story in enumerate(payload["stories"]):
        if not isinstance(story, dict) or not isinstance(story.get("summaryBullets"), list):
            raise HeadingSyncError("invalid local story")
        if story.get("id") != f"{expected_root_id}-{chapter}":
            continue
        units = []
        for bullet_index, bullet in enumerate(story["summaryBullets"]):
            marker = parse_trailing_marker(bullet, bullet_index)
            if marker.unit is None or marker.unit.chapter != chapter:
                raise HeadingSyncError("target story has nonnative or cross-chapter verse marker")
            units.append((marker.unit.start, marker.unit.end))
        if not units or any(start < 1 or end < start for start, end in units):
            raise HeadingSyncError("target story has invalid native units")
        if units != sorted(units) or any(units[i][0] <= units[i - 1][1] for i in range(1, len(units))):
            raise HeadingSyncError("target story has unordered or overlapping native units")
        matches.append((index, story, units))
    if len(matches) != 1:
        raise HeadingSyncError("target chapter must have exactly one matching story")
    return matches[0]


def _headings(source: tuple[tuple[int, str], ...], starts: set[int]) -> list[dict[str, Any]]:
    if len(source) > MAX_HEADINGS:
        raise HeadingSyncError("too many source heading lines")
    merged: list[dict[str, Any]] = []
    for anchor, label in source:
        if type(anchor) is not int or anchor not in starts or not isinstance(label, str) or not label.strip():
            raise HeadingSyncError("source heading lacks a valid native anchor")
        if label != label.strip() or APP_TAG.search(label) or any(ord(char) < 32 and char != "\n" for char in label):
            raise HeadingSyncError("unsafe source heading text")
        if merged and anchor < merged[-1]["beforeVerse"]:
            raise HeadingSyncError("source headings are out of order")
        if merged and anchor == merged[-1]["beforeVerse"]:
            merged[-1]["text"] += "\n" + label
        else:
            merged.append({"beforeVerse": anchor, "text": label})
    return merged


def _check_existing(headings: object, starts: set[int]) -> None:
    if not isinstance(headings, list):
        raise HeadingSyncError("target story lacks a headings array")
    last = 0
    for row in headings:
        if not isinstance(row, dict) or set(row) != {"beforeVerse", "text"}:
            raise HeadingSyncError("unexpected local heading structure")
        anchor, label = row["beforeVerse"], row["text"]
        if type(anchor) is not int or anchor not in starts or anchor <= last or not isinstance(label, str) or not label.strip():
            raise HeadingSyncError("invalid local heading or duplicate anchor")
        if APP_TAG.search(label):
            raise HeadingSyncError("tagged local heading requires manual review")
        last = anchor


def _skip_ws(text: str, pos: int) -> int:
    while pos < len(text) and text[pos] in " \t\r\n":
        pos += 1
    return pos


def _object_fields(text: str, start: int) -> dict[str, tuple[int, int]]:
    if text[start] != "{":
        raise HeadingSyncError("expected JSON object")
    pos = start + 1
    fields: dict[str, tuple[int, int]] = {}
    while True:
        pos = _skip_ws(text, pos)
        if text[pos] == "}":
            return fields
        key, pos = DECODER.raw_decode(text, pos)
        if not isinstance(key, str) or key in fields:
            raise HeadingSyncError("invalid or duplicate JSON key")
        pos = _skip_ws(text, pos)
        if text[pos] != ":":
            raise HeadingSyncError("invalid JSON field separator")
        start_value = _skip_ws(text, pos + 1)
        _, end_value = DECODER.raw_decode(text, start_value)
        fields[key] = (start_value, end_value)
        pos = _skip_ws(text, end_value)
        if text[pos] == "}":
            return fields
        if text[pos] != ",":
            raise HeadingSyncError("invalid JSON object separator")
        pos += 1


def _array_item_start(text: str, start: int, index: int) -> int:
    if text[start] != "[":
        raise HeadingSyncError("expected stories array")
    pos = start + 1
    for item_index in range(index + 1):
        pos = _skip_ws(text, pos)
        if text[pos] == "]":
            raise HeadingSyncError("story index is out of range")
        item_start = pos
        _, pos = DECODER.raw_decode(text, pos)
        if item_index == index:
            return item_start
        pos = _skip_ws(text, pos)
        if text[pos] != ",":
            raise HeadingSyncError("invalid stories array separator")
        pos += 1
    raise HeadingSyncError("story index is out of range")


def _replace_heading_value(raw: bytes, story_index: int, headings: list[dict[str, Any]], expected: Any) -> bytes:
    text = raw.decode("utf-8")
    root_start = _skip_ws(text, 1 if text.startswith("\ufeff") else 0)
    root_fields = _object_fields(text, root_start)
    stories_start = root_fields["stories"][0]
    story_start = _array_item_start(text, stories_start, story_index)
    fields = _object_fields(text, story_start)
    newline = "\r\n" if "\r\n" in text else "\n"
    if "headings" in fields:
        begin, end = fields["headings"]
        if json.loads(text[begin:end]) != expected:
            raise HeadingSyncError("heading field span does not match parsed asset")
        line_start = text.rfind("\n", 0, begin) + 1
        indent = re.match(r"[ \t]*", text[line_start:begin]).group()
        serialized = json.dumps(headings, ensure_ascii=False, indent=2).replace("\n", newline + indent)
        updated = text[:begin] + serialized + text[end:]
        untouched = text[:begin] == updated[:begin] and text[end:] == updated[begin + len(serialized):]
    else:
        if expected is not None or not headings or not fields:
            raise HeadingSyncError("heading insertion state is invalid")
        # Insert after the last existing field, leaving every existing byte
        # before and after the new field untouched, including story whitespace.
        begin = max(end for _, end in fields.values())
        _, story_end = DECODER.raw_decode(text, story_start)
        if text[begin:story_end - 1].strip():
            raise HeadingSyncError("unexpected content after last story field")
        multiline = "\n" in text[story_start:story_end]
        last_line = text.rfind("\n", story_start, begin) + 1
        indent = re.match(r"[ \t]*", text[last_line:begin]).group() if multiline else ""
        if multiline:
            value = json.dumps(headings, ensure_ascii=False, indent=2).replace("\n", newline + indent)
            inserted = "," + newline + indent + '"headings": ' + value
        else:
            value = json.dumps(headings, ensure_ascii=False, separators=(",", ":"))
            inserted = ',"headings":' + value
        updated = text[:begin] + inserted + text[begin:]
        untouched = text[:begin] == updated[:begin] and text[begin:] == updated[begin + len(inserted):]
    checked = json.loads(updated.lstrip("\ufeff"))
    original = json.loads(text.lstrip("\ufeff"))
    original["stories"][story_index]["headings"] = headings
    if checked != original or not untouched:
        raise HeadingSyncError("replacement touched a non-heading field")
    return updated.encode("utf-8")


def sync_snapshot(snapshot: dict[str, Any], *, root: Path = ROOT, apply: bool = False) -> dict[str, Any]:
    language, chapter, code, collection, book_id, default_path = _identity(snapshot)
    path = root / default_path.relative_to(ROOT)
    raw = path.read_bytes()
    payload = json.loads(raw.decode("utf-8-sig"))
    story_index, story, local_ranges = _local_story(payload, book_id, chapter)
    # Long poetry chapters can legitimately contain more than 500 rendered
    # fragments (Psalm 119 currently reaches 559). The byte cap and strict
    # per-record parser remain the primary bounds.
    if (not isinstance(snapshot.get("records"), list) or len(snapshot["records"]) > MAX_RECORDS
            or not all(isinstance(record, dict) for record in snapshot["records"])):
        raise HeadingSyncError("snapshot record limit or structure invalid")
    source = extract_browser_records(snapshot["records"], code, chapter)
    if list(source.ranges) != local_ranges:
        raise HeadingSyncError("source and local native range inventories differ")
    starts = {start for start, _ in local_ranges}
    has_heading_field = "headings" in story
    existing = story["headings"] if has_heading_field else []
    _check_existing(existing, starts)
    if not source.headings_available and existing:
        raise HeadingSyncError("source page has no structural heading evidence; refusing deletion")
    replacement = _headings(source.headings, starts)
    if not replacement and existing:
        raise HeadingSyncError("source heading snapshot is empty; refusing deletion")
    updated = (raw if replacement == existing else
               _replace_heading_value(raw, story_index, replacement, existing if has_heading_field else None))
    changed = updated != raw
    result = {
        "status": "changed" if changed else "match", "mode": "apply" if apply else "audit",
        "language": language, "bibleId": EDITIONS[language].bible_id, "book": code, "chapter": chapter,
        "collection": collection, "localHeadingCount": len(existing), "sourceHeadingCount": len(source.headings),
        "targetHeadingCount": len(replacement), "nativeRangeCount": len(local_ranges),
        "beforeSha256": hashlib.sha256(raw).hexdigest(), "afterSha256": hashlib.sha256(updated).hexdigest(),
        "localHeadingsSha256": _digest(existing), "sourceHeadingsSha256": _digest(replacement),
    }
    if apply and changed:
        # Re-read immediately before replacement so concurrent edits fail closed.
        if path.read_bytes() != raw:
            raise HeadingSyncError("local asset changed during audit")
        temporary: str | None = None
        try:
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".headings-", suffix=".tmp", delete=False) as handle:
                temporary = handle.name
                handle.write(updated)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)
    return result


def stream_snapshots(source: TextIO, sink: TextIO, *, root: Path = ROOT, apply: bool = False) -> int:
    status = 0
    for line in source:
        try:
            if len(line) > MAX_SNAPSHOT:
                raise HeadingSyncError("snapshot exceeds size limit")
            snapshot = json.loads(line)
            if not isinstance(snapshot, dict):
                raise HeadingSyncError("snapshot must be a JSON object")
            result = sync_snapshot(snapshot, root=root, apply=apply)
        except (HeadingSyncError, BrowserAuditError, OSError, UnicodeError, ValueError, KeyError, IndexError, TypeError) as exc:
            # Never echo exception messages: a malformed snapshot could embed
            # publisher text in them. Only the error class leaves this process.
            result = {"status": "blocked", "errorType": type(exc).__name__}
            if isinstance(exc, (HeadingSyncError, BrowserAuditError)):
                result["errorCode"] = re.sub(r"[^a-z0-9]+", "_", str(exc).lower()).strip("_")
            status = 2
        sink.write(json.dumps(result, ensure_ascii=False, sort_keys=True) + "\n")
        sink.flush()
    return status


def create_snapshot_server(port: int, *, root: Path = ROOT, apply: bool = False) -> HTTPServer:
    """Accept one ordered DOM snapshot at a time over loopback, without storage."""

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format: str, *_args: object) -> None:
            # Request paths and source snippets must never reach access logs.
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
                    raise HeadingSyncError("invalid request length")
                if self.headers.get_content_type() != "application/json":
                    raise HeadingSyncError("invalid content type")
                length = int(length_text)
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise HeadingSyncError("incomplete request body")
                snapshot = json.loads(raw.decode("utf-8"))
                if not isinstance(snapshot, dict):
                    raise HeadingSyncError("snapshot must be a JSON object")
                result = sync_snapshot(snapshot, root=root, apply=apply)
            except (HeadingSyncError, BrowserAuditError, OSError, UnicodeError, ValueError, KeyError, IndexError, TypeError) as exc:
                result = {"status": "blocked", "errorType": type(exc).__name__}
                if isinstance(exc, (HeadingSyncError, BrowserAuditError)):
                    result["errorCode"] = re.sub(r"[^a-z0-9]+", "_", str(exc).lower()).strip("_")
            self._respond(200, result)

    return HTTPServer(("127.0.0.1", port), Handler)


def serve_snapshots(port: int, *, root: Path = ROOT, apply: bool = False) -> None:
    server = create_snapshot_server(port, root=root, apply=apply)
    try:
        print(f"Heading snapshot server listening on 127.0.0.1:{server.server_port}", flush=True)
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="update only a verified story headings value")
    parser.add_argument("--root", type=Path, default=ROOT, help="repository root")
    parser.add_argument("--snapshot-server", action="store_true", help="accept in-memory chapter snapshots over loopback POST /snapshot")
    parser.add_argument("--port", type=int, default=9226, help="loopback server port; default 9226")
    args = parser.parse_args()
    if args.snapshot_server:
        if not 1 <= args.port <= 65535:
            parser.error("--port must be between 1 and 65535")
        try:
            serve_snapshots(args.port, root=args.root, apply=args.apply)
        except OSError:
            print("Heading snapshot server could not bind loopback port", file=sys.stderr)
            return 2
        return 0
    return stream_snapshots(sys.stdin, sys.stdout, root=args.root, apply=args.apply)


if __name__ == "__main__":
    raise SystemExit(main())
