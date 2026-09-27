#!/usr/bin/env python3
"""Audit traditional heading relocations without changing files.

Only a unique, byte-exact heading in the same verified base book can become a
review-only rebase proposal. Changed, removed, or duplicate headings need
human source review. Every proposal is bound to input SHA-256 hashes and
exact old-value tests. This program never applies it.
"""

from __future__ import annotations

from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re

from heading_maps import HeadingMapError, load_heading_maps
from import_traditional_editions import BOOKS


ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = ROOT / "tools/traditional/edition_heading_maps.json"
SAFE_SEGMENT = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*\Z")
# A few legacy JSON ids differ from their file stems. Keep this explicit;
# never infer a book identity from a similarly named filename.
BASE_JSON_IDS = {
    "1_chronicles": "1chronicles",
    "2_chronicles": "2chronicles",
    "1_corinthians": "1-corinthians",
    "1_samuel": "1-samuel",
    "2_kings": "2-kings",
    "2_samuel": "2-samuel",
}


class RebaseAuditError(ValueError):
    """An input is malformed, so no rebase assessment can be trusted."""


def _digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _read_json(path: Path) -> tuple[dict, str]:
    try:
        raw = path.read_bytes()
        document = json.loads(raw.decode("utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise RebaseAuditError(f"Cannot read JSON {path}: {exc}") from exc
    if not isinstance(document, dict):
        raise RebaseAuditError(f"JSON root must be an object: {path}")
    return document, _digest(raw)


def _safe_segment(value: str, label: str) -> str:
    if not isinstance(value, str) or SAFE_SEGMENT.fullmatch(value) is None:
        raise RebaseAuditError(f"Unsafe {label}: {value!r}")
    return value


def _base_headings(path: Path, book_id: str) -> tuple[dict[str, list[tuple[int, int]]], dict[tuple[int, int], list[str]], str]:
    document, digest = _read_json(path)
    json_id = BASE_JSON_IDS.get(book_id, book_id)
    if document.get("id") != json_id:
        raise RebaseAuditError(f"Base book identity mismatch: {path}")
    stories = document.get("stories")
    if not isinstance(stories, list) or not stories:
        raise RebaseAuditError(f"Base book has no stories: {path}")

    by_text: dict[str, list[tuple[int, int]]] = defaultdict(list)
    by_anchor: dict[tuple[int, int], list[str]] = defaultdict(list)
    seen_chapters: set[int] = set()
    for story in stories:
        if not isinstance(story, dict):
            raise RebaseAuditError(f"Malformed base story: {path}")
        story_id = story.get("id")
        prefix = f"{json_id}-"
        if not isinstance(story_id, str) or not story_id.startswith(prefix):
            raise RebaseAuditError(f"Base story identity mismatch: {path} {story_id!r}")
        suffix = story_id[len(prefix):]
        if not suffix.isdecimal() or int(suffix) < 1:
            raise RebaseAuditError(f"Malformed base chapter: {path} {story_id!r}")
        chapter = int(suffix)
        if chapter in seen_chapters:
            raise RebaseAuditError(f"Duplicate base chapter: {path} {chapter}")
        seen_chapters.add(chapter)
        headings = story.get("headings", [])
        if not isinstance(headings, list):
            raise RebaseAuditError(f"Malformed base headings: {path} {chapter}")
        for heading in headings:
            if not isinstance(heading, dict):
                raise RebaseAuditError(f"Malformed base heading: {path} {chapter}")
            text = heading.get("text")
            verse = heading.get("beforeVerse")
            if not isinstance(text, str) or not text or text != text.strip():
                raise RebaseAuditError(f"Malformed base heading text: {path} {chapter}")
            if type(verse) is not int or verse < 1:
                raise RebaseAuditError(f"Malformed base heading anchor: {path} {chapter}")
            anchor = (chapter, verse)
            by_text[text].append(anchor)
            by_anchor[anchor].append(text)
    if seen_chapters != set(range(1, len(stories) + 1)):
        raise RebaseAuditError(f"Base chapters are not contiguous: {path}")
    for anchors in by_text.values():
        anchors.sort()
    return by_text, by_anchor, digest


def _anchor(chapter: int, verse: int) -> dict[str, int]:
    return {"chapter": chapter, "beforeVerse": verse}


def audit(root: Path = ROOT, map_path: Path | None = None) -> dict:
    """Return a deterministic audit and optional review-only JSON Patch."""
    root = root.resolve()
    map_path = map_path or root / "tools/traditional/edition_heading_maps.json"
    document, map_digest = _read_json(map_path)
    try:
        validated = load_heading_maps(map_path)
    except HeadingMapError as exc:
        raise RebaseAuditError(str(exc)) from exc
    if _read_json(map_path)[1] != map_digest:
        raise RebaseAuditError("Heading map changed while validating it")
    books_root = (root / "shared/assets/books").resolve()
    canonical_books = {code: (collection, book_id) for code, collection, book_id in BOOKS}
    rows: list[dict] = []
    proposed: list[dict] = []
    book_digests: dict[str, str] = {}

    for edition_index, edition in enumerate(document["editions"]):
        key = (edition["language"], edition["editionId"], edition["bookCode"])
        reviewed = validated[key]
        if canonical_books.get(reviewed.book_code) != (reviewed.collection, reviewed.book_id):
            raise RebaseAuditError(
                f"Heading map book code/identity mismatch: "
                f"{reviewed.book_code} -> {reviewed.collection}/{reviewed.book_id}"
            )
        language = _safe_segment(reviewed.language, "language")
        collection = _safe_segment(reviewed.collection, "collection")
        book_id = _safe_segment(reviewed.book_id, "bookId")
        base_path = (books_root / collection / language / f"{book_id}.json").resolve()
        if not base_path.is_relative_to(books_root):
            raise RebaseAuditError(f"Base book escapes assets root: {base_path}")
        by_text, by_anchor, base_digest = _base_headings(base_path, book_id)
        relative_base = base_path.relative_to(root).as_posix()
        book_digests[relative_base] = base_digest
        occupied_map_anchors = {
            (relocation.source_chapter, relocation.source_before_verse)
            for relocation in reviewed.relocations
        }
        candidate_destinations: Counter[tuple[int, int]] = Counter()
        preliminary: list[tuple[dict, tuple[int, int] | None, int]] = []

        for relocation_index, relocation in enumerate(reviewed.relocations):
            old = (relocation.source_chapter, relocation.source_before_verse)
            current = by_text.get(relocation.source_text, [])
            at_old = by_anchor.get(old, [])
            candidate: tuple[int, int] | None = None
            if at_old == [relocation.source_text]:
                status = "bound"
            elif len(at_old) > 1:
                status = "ambiguous_old_anchor"
            elif len(current) > 1:
                status = "ambiguous_duplicate_text"
            elif len(current) == 1:
                candidate = current[0]
                candidate_destinations[candidate] += 1
                status = "exact_text_unique_relocation_review_required"
            elif at_old:
                status = "old_anchor_text_changed_or_replaced"
            else:
                status = "source_heading_removed_or_rewritten"

            row = {
                "language": reviewed.language,
                "editionId": reviewed.edition_id,
                "bookCode": reviewed.book_code,
                "collection": reviewed.collection,
                "bookId": reviewed.book_id,
                "baseBook": relative_base,
                "relocationIndex": relocation_index,
                "oldSourceAnchor": _anchor(*old),
                "oldAnchorHeadingCount": len(at_old),
                "oldAnchorTextSha256": [_digest(value.encode("utf-8")) for value in at_old],
                "currentExactTextAnchors": [_anchor(*anchor) for anchor in current],
                "sourceTextSha256": _digest(relocation.source_text.encode("utf-8")),
                "status": status,
            }
            preliminary.append((row, candidate, relocation_index))

        for row, candidate, relocation_index in preliminary:
            if candidate is not None:
                if candidate in occupied_map_anchors or candidate_destinations[candidate] != 1:
                    row["status"] = "ambiguous_relocation_collision"
                else:
                    base = f"/editions/{edition_index}/relocations/{relocation_index}"
                    old = row["oldSourceAnchor"]
                    proposed.extend([
                        {"op": "test", "path": base + "/sourceChapter", "value": old["chapter"]},
                        {"op": "test", "path": base + "/sourceBeforeVerse", "value": old["beforeVerse"]},
                        {"op": "test", "path": base + "/sourceText", "value": edition["relocations"][relocation_index]["sourceText"]},
                        {"op": "replace", "path": base + "/sourceChapter", "value": candidate[0]},
                        {"op": "replace", "path": base + "/sourceBeforeVerse", "value": candidate[1]},
                    ])
            rows.append(row)

    counts = Counter(row["status"] for row in rows)
    return {
        "readOnly": True,
        "proposalRequiresSourceReview": True,
        "map": map_path.relative_to(root).as_posix(),
        "mapSha256": map_digest,
        "baseBookSha256": dict(sorted(book_digests.items())),
        "summary": dict(sorted(counts.items())),
        "proposedJsonPatch": proposed,
        "rows": rows,
    }


def main() -> int:
    try:
        # Keep CLI output portable on Windows consoles whose legacy code page
        # cannot encode every localized heading. JSON escapes remain lossless.
        print(json.dumps(audit(), ensure_ascii=True, indent=2))
    except RebaseAuditError as exc:
        print(f"Heading-map rebase audit failed closed: {exc}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
