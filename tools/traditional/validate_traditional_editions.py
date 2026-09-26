#!/usr/bin/env python3
"""Validate committed traditional-language Scripture overlays."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from import_traditional_editions import (
    BOOKS,
    EDITIONS,
    KRV_MERGED_MARKER,
    KRV_OMITTED_MARKER,
    SCHEMA_VERSION,
)


class ValidationError(AssertionError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def validate_tag_pair(text: str, opening: str, closing: str, reference: str) -> None:
    depth = 0
    for marker in re.findall(f"{re.escape(opening)}|{re.escape(closing)}", text):
        depth += 1 if marker == opening else -1
        require(depth in {0, 1}, f"Unbalanced {opening} at {reference}")
    require(depth == 0, f"Unclosed {opening} at {reference}")


REFERENCE_METADATA = re.compile(r"\[(?:\d+:\d+|\d+[a-z])\]")


def normalized_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def validate_edition(repo_root: Path, config) -> dict:
    edition_dir = (
        repo_root / "shared" / "assets" / "books" / "editions" /
        config.language / config.edition_id
    )
    manifest_path = edition_dir / "_manifest.json"
    require(manifest_path.is_file(), f"Missing manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(manifest["schemaVersion"] == SCHEMA_VERSION, f"Schema mismatch: {manifest_path}")
    require(manifest["editionId"] == config.edition_id, f"Edition mismatch: {manifest_path}")
    require(manifest["language"] == config.language, f"Language mismatch: {manifest_path}")
    source_hash_key = "sourceFileSha256" if config.source_format == "krv-json" else "archiveSha256"
    require(
        manifest["source"][source_hash_key] == config.archive_sha256,
        f"Pinned source hash mismatch: {manifest_path}",
    )
    if config.auxiliary_sha256 is not None:
        require(
            manifest["source"]["auxiliary"]["sourceFileSha256"] == config.auxiliary_sha256,
            f"Pinned auxiliary hash mismatch: {manifest_path}",
        )
    require(
        manifest_path.read_text(encoding="utf-8") == normalized_json(manifest),
        f"Non-canonical JSON formatting: {manifest_path}",
    )

    expected_outputs = {
        edition_dir / collection / f"{book_id}.json"
        for _code, collection, book_id in BOOKS
    } | {manifest_path}
    actual_outputs = set(edition_dir.rglob("*.json"))
    require(actual_outputs == expected_outputs, f"Output inventory mismatch: {edition_dir}")

    manifest_by_book = {
        (row["collection"], row["bookId"]): row for row in manifest["books"]
    }
    dc_index = json.loads((
        repo_root / "shared" / "assets" / "books" / "deuterocanonical" /
        config.language / "_index.json"
    ).read_text(encoding="utf-8"))
    expected_manifest_keys = {
        (collection, book_id) for _code, collection, book_id in BOOKS
    } | {("deuterocanonical", row[0]) for row in dc_index}
    require(
        set(manifest_by_book) == expected_manifest_keys,
        f"Manifest inventory mismatch: {manifest_path}",
    )

    totals = {
        "overlayBooks": 0,
        "fallbackBooks": len(dc_index),
        "chapters": 0,
        "verseUnits": 0,
        "coveredVerseNumbers": 0,
        "jesusWordSpans": 0,
        "inheritedJesusVerseSpans": sum(
            row.get("inheritedJesusVerseSpans", 0)
            for row in manifest["books"]
        ),
        "omittedMixedJesusVerseSpans": sum(
            row.get("omittedMixedJesusVerseSpans", 0)
            for row in manifest["books"]
        ),
        "translatorAdditionSpans": 0,
        "superscriptions": 0,
        "promotedFootnoteVerses": 0,
        "sourcePlaceholderVerses": 0,
    }

    for code, collection, book_id in BOOKS:
        path = edition_dir / collection / f"{book_id}.json"
        overlay = json.loads(path.read_text(encoding="utf-8"))
        require(path.read_text(encoding="utf-8") == normalized_json(overlay), f"Non-canonical JSON: {path}")
        require(overlay["schemaVersion"] == SCHEMA_VERSION, f"Schema mismatch: {path}")
        require(overlay["editionId"] == config.edition_id, f"Edition mismatch: {path}")
        require(overlay["language"] == config.language, f"Language mismatch: {path}")
        require(overlay["collection"] == collection, f"Collection mismatch: {path}")
        require(overlay["bookId"] == book_id, f"Book mismatch: {path}")
        require(overlay["sourceBookCode"] == code, f"Source code mismatch: {path}")
        require(overlay["coverage"] == "full", f"Coverage mismatch: {path}")

        base = json.loads((
            repo_root / "shared" / "assets" / "books" / collection /
            config.language / f"{book_id}.json"
        ).read_text(encoding="utf-8"))
        base_chapters = sum(
            story.get("id", "").rsplit("-", 1)[-1].isdigit()
            for story in base.get("stories", [])
        )
        require(len(overlay["chapters"]) == base_chapters, f"Chapter count mismatch: {path}")

        base_stories = base.get("stories", [])
        if len(base_stories) == 1:
            story_by_chapter = {1: base_stories[0]}
        else:
            story_by_chapter = {
                int(story["id"].rsplit("-", 1)[-1]): story
                for story in base_stories
                if story.get("id", "").rsplit("-", 1)[-1].isdigit()
            }
        overlay_by_chapter = {chapter["number"]: chapter for chapter in overlay["chapters"]}
        require(
            set(story_by_chapter) == set(overlay_by_chapter),
            f"Base/overlay chapter inventory mismatch: {path}",
        )
        for chapter_number, story in story_by_chapter.items():
            chapter = overlay_by_chapter[chapter_number]
            effective_headings = chapter.get("headings", story.get("headings", []))
            anchors: list[int] = []
            for heading in effective_headings:
                before_verse = heading.get("beforeVerse")
                require(
                    isinstance(before_verse, int),
                    f"Invalid heading anchor at {path} {chapter_number}:{before_verse}",
                )
                require(
                    isinstance(heading.get("text"), str) and heading["text"].strip(),
                    f"Empty heading at {path} {chapter_number}:{before_verse}",
                )
                require(
                    any(
                        verse["verse"] == before_verse
                        for verse in chapter["verses"]
                    ),
                    f"Heading falls outside selected edition at {path} "
                    f"{chapter_number}:{before_verse}",
                )
                anchors.append(before_verse)
            require(
                anchors == sorted(anchors) and len(anchors) == len(set(anchors)),
                f"Duplicate or unsorted headings at {path} chapter {chapter_number}",
            )

        expected_chapter = 1
        for chapter in overlay["chapters"]:
            number = chapter["number"]
            require(number == expected_chapter, f"Non-contiguous chapter in {path}: {number}")
            require(
                chapter.get("verseUnitCount") == len(chapter["verses"]),
                f"Verse-unit metadata mismatch: {path} chapter {number}",
            )
            expected_verse = 1
            for verse in chapter["verses"]:
                start = verse["verse"]
                end = verse.get("verseEnd", start)
                reference = f"{config.language}/{book_id} {number}:{start}-{end}"
                require(verse["chapter"] == number, f"Chapter mismatch at {reference}")
                require(start == expected_verse and end >= start, f"Verse gap at {reference}")
                text = verse["text"]
                require(isinstance(text, str) and text.strip(), f"Empty text at {reference}")
                require("\\" not in text, f"Leaked source marker at {reference}")
                require(
                    REFERENCE_METADATA.search(text) is None,
                    f"Leaked reference metadata at {reference}",
                )
                validate_tag_pair(text, "[J]", "[/J]", reference)
                validate_tag_pair(text, "[ADD]", "[/ADD]", reference)
                if verse.get("sourcePlaceholder"):
                    require(
                        config.language == "ko" and (
                            text == KRV_OMITTED_MARKER or
                            KRV_MERGED_MARKER.fullmatch(text) is not None
                        ),
                        f"Unexpected source placeholder at {reference}",
                    )
                expected_verse = end + 1

                totals["verseUnits"] += 1
                totals["coveredVerseNumbers"] += end - start + 1
                totals["jesusWordSpans"] += text.count("[J]")
                totals["translatorAdditionSpans"] += text.count("[ADD]")
                totals["promotedFootnoteVerses"] += bool(verse.get("sourceFootnotePromotion"))
                totals["sourcePlaceholderVerses"] += bool(verse.get("sourcePlaceholder"))
            require(expected_verse > 1, f"Empty chapter: {path} chapter {number}")
            require(
                chapter.get("lastVerse") == expected_verse - 1,
                f"Last-verse metadata mismatch: {path} chapter {number}",
            )
            if chapter.get("superscription"):
                totals["superscriptions"] += 1
            expected_chapter += 1

        totals["overlayBooks"] += 1
        totals["chapters"] += len(overlay["chapters"])
        row = manifest_by_book[(collection, book_id)]
        require(row["coverage"] == "full", f"Manifest coverage mismatch: {book_id}")
        require(row["chapters"] == len(overlay["chapters"]), f"Manifest chapter mismatch: {book_id}")

    for row in dc_index:
        item = manifest_by_book[("deuterocanonical", row[0])]
        require(item["coverage"] == "fallback", f"DC fallback missing: {config.language}/{row[0]}")
        require(item["fallbackEditionId"] == "current", f"DC fallback target mismatch: {config.language}/{row[0]}")

    require(totals == manifest["totals"], f"Manifest totals mismatch: {config.language}")
    return {"language": config.language, "editionId": config.edition_id, **totals}


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    try:
        reports = [validate_edition(repo_root, config) for config in EDITIONS]
    except (OSError, KeyError, TypeError, ValueError, ValidationError) as exc:
        print(f"Traditional-edition validation failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
