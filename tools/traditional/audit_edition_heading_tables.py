#!/usr/bin/env python3
"""Audit explicit alternate-edition heading lookup tables."""

from __future__ import annotations

from collections import Counter
import json
from pathlib import Path

from heading_maps import EditionHeadingMap, load_heading_maps


ROOT = Path(__file__).resolve().parents[2]
EDITIONS = (
    ("en", "kjv1769"),
    ("de", "luther1912"),
    ("es", "rv1909"),
    ("fr", "lsg1910"),
    ("it", "diodati1885"),
    ("pt", "almeida1911"),
    ("ru", "synodal1876"),
    ("ja", "bungo"),
    ("ko", "korrv"),
    ("zh-Hans", "cuv"),
    ("zh-Hant", "cuv"),
    ("ar", "van_dyck"),
)


class HeadingAuditError(AssertionError):
    pass


def require(value: bool, message: str) -> None:
    if not value:
        raise HeadingAuditError(message)


def base_stories(path: Path) -> dict[int, dict]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    stories = raw.get("stories", [])
    if len(stories) == 1:
        # Match ChapterLocator: every singleton book displays as chapter 1,
        # even when its retained base story id has a historical suffix such
        # as song_of_three-3.
        result = {1: stories[0]}
    else:
        result = {
            int(str(story["id"]).rsplit("-", 1)[-1]): story
            for story in stories
            if str(story.get("id", "")).rsplit("-", 1)[-1].isdigit()
        }
    require(bool(result), f"No numeric base chapters: {path}")
    return result


def heading_lines(rows) -> Counter[str]:
    return Counter(
        line
        for row in rows
        for line in row["text"].splitlines()
    )


def expected_heading_lines(
    chapters: dict[int, dict],
    reviewed: EditionHeadingMap | None,
    label: str,
) -> Counter[str]:
    """Expand reviewed composite splits before comparing localized text."""
    relocations = {
        (row.source_chapter, row.source_before_verse): row
        for row in reviewed.relocations
    } if reviewed else {}
    used: set[tuple[int, int]] = set()
    expected: Counter[str] = Counter()
    for chapter_number in sorted(chapters):
        for heading in chapters[chapter_number].get("headings", []):
            anchor = (chapter_number, heading.get("beforeVerse"))
            relocation = relocations.get(anchor)
            if relocation is None:
                expected.update(heading["text"].splitlines())
                continue
            require(
                heading.get("text") == relocation.source_text,
                f"Reviewed heading source text differs: {label} "
                f"{chapter_number}:{heading.get('beforeVerse')}",
            )
            used.add(anchor)
            for target in relocation.targets:
                expected.update(target.text.splitlines())
    require(
        used == set(relocations),
        f"Reviewed heading source anchor is absent: {label}",
    )
    return expected


def require_reviewed_targets(
    chapters: dict[int, dict],
    reviewed: EditionHeadingMap | None,
    label: str,
) -> None:
    """Prove each reviewed fragment occurs at its exact native verse anchor.

    The importer joins independent titles at one anchor with newlines. Count
    lines at each anchor rather than comparing a whole joined string, so a
    reviewed title can coexist with an unrelated title there.
    """
    if reviewed is None:
        return
    required: Counter[tuple[int, int, str]] = Counter(
        (target.chapter, target.before_verse, line)
        for relocation in reviewed.relocations
        for target in relocation.targets
        for line in target.text.splitlines()
    )
    actual: Counter[tuple[int, int, str]] = Counter(
        (chapter_number, heading["beforeVerse"], line)
        for chapter_number, chapter in chapters.items()
        for heading in chapter["headings"]
        for line in heading["text"].splitlines()
    )
    missing = required - actual
    require(
        not missing,
        f"Reviewed heading target missing or misplaced: {label} {list(missing.items())[:3]}",
    )


def audit_edition(language: str, edition: str) -> dict[str, int | str]:
    directory = ROOT / "shared/assets/books/editions" / language / edition
    manifest_path = directory / "_manifest.json"
    require(manifest_path.is_file(), f"Missing manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    heading_proof = manifest.get("headings", {})
    require(
        heading_proof.get("placement", "").startswith("Explicit per-chapter beforeVerse lookup"),
        f"Missing heading provenance: {manifest_path}",
    )
    manifest_books = {
        directory / row["output"]: row
        for row in manifest.get("books", [])
        if row.get("coverage") != "fallback"
    }
    expected_outputs = set(manifest_books)
    actual_outputs = {
        path for path in directory.glob("*/*.json")
        if not path.name.startswith("_")
    }
    require(actual_outputs == expected_outputs, f"Overlay inventory differs: {language}/{edition}")

    reviewed_maps = load_heading_maps(ROOT / "tools/traditional/edition_heading_maps.json")
    chapters = entries = lines = 0
    for path in sorted(actual_outputs):
        overlay = json.loads(path.read_text(encoding="utf-8"))
        collection = overlay["collection"]
        book_id = overlay["bookId"]
        base_path = ROOT / "shared/assets/books" / collection / language / f"{book_id}.json"
        base = base_stories(base_path)
        alternate = {row["number"]: row for row in overlay["chapters"]}
        require(set(alternate) == set(base), f"Chapter inventory differs: {path}")
        alternate_rows = []
        for chapter_number in sorted(alternate):
            chapter = alternate[chapter_number]
            require("headings" in chapter, f"Missing heading table: {path} {chapter_number}")
            headings = chapter["headings"]
            require(isinstance(headings, list), f"Invalid heading table: {path} {chapter_number}")
            anchors = [row.get("beforeVerse") for row in headings]
            starts = {row["verse"] for row in chapter["verses"]}
            require(
                anchors == sorted(anchors) and len(anchors) == len(set(anchors)),
                f"Unsorted or duplicate heading anchors: {path} {chapter_number}",
            )
            require(
                all(type(anchor) is int and anchor in starts for anchor in anchors),
                f"Heading is not bound to a native verse-unit start: {path} {chapter_number}",
            )
            require(
                all(isinstance(row.get("text"), str) and row["text"].strip() for row in headings),
                f"Blank heading text: {path} {chapter_number}",
            )
            alternate_rows.extend(headings)
            chapters += 1
            entries += len(headings)
        book_code = manifest_books[path].get("sourceBookCode")
        require(isinstance(book_code, str), f"Missing source book code: {path}")
        reviewed = reviewed_maps.get((language, edition, book_code))
        if reviewed is not None:
            require(
                (reviewed.collection, reviewed.book_id) == (collection, book_id),
                f"Reviewed heading map points to the wrong book: {path}",
            )
        require(
            heading_lines(alternate_rows) == expected_heading_lines(
                base, reviewed, f"{language}/{edition}/{book_id}"
            ),
            f"Localized heading text was lost or changed: {path}",
        )
        require_reviewed_targets(
            alternate, reviewed, f"{language}/{edition}/{book_id}"
        )
        lines += sum(len(row["text"].splitlines()) for row in alternate_rows)
    return {
        "language": language,
        "editionId": edition,
        "books": len(actual_outputs),
        "chapters": chapters,
        "headingEntries": entries,
        "headingLines": lines,
    }


def audit_all() -> list[dict[str, int | str]]:
    return [audit_edition(language, edition) for language, edition in EDITIONS]


def main() -> int:
    print(json.dumps(audit_all(), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
