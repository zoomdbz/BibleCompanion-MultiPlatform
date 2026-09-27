#!/usr/bin/env python3
"""Validate committed traditional-language Scripture overlays."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import import_traditional_editions as importer
import import_synodal_deuterocanon as synodal
from audit_edition_heading_tables import (
    HeadingAuditError,
    expected_heading_lines,
    heading_lines,
    require_reviewed_targets,
)
from heading_maps import EditionHeadingMap, HeadingMapError, load_heading_maps

from import_traditional_editions import (
    BOOKS,
    EDITIONS,
    KRV_MERGED_MARKER,
    KRV_OMITTED_MARKER,
    SCHEMA_VERSION,
    reference_map_for,
)
from import_synodal_deuterocanon import (
    ARCHIVE_SHA256 as SYNODAL_DC_SHA256,
    SUPPORTED_SOURCES as SYNODAL_DC_BOOKS,
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


def verify_pin(path: Path, expected_sha256: str) -> None:
    require(path.is_file(), f"Missing pinned source: {path}")
    actual = importer.sha256(path)
    require(actual == expected_sha256, f"Pinned source SHA-256 mismatch: {path}; expected {expected_sha256}, got {actual}")


def validate_localized_headings(
    base_chapters: dict[int, dict],
    edition_chapters: dict[int, dict],
    reviewed: EditionHeadingMap | None,
    label: str,
) -> None:
    """Preserve every localized title and enforce reviewed edition anchors."""
    expected = expected_heading_lines(base_chapters, reviewed, label)
    actual = heading_lines(
        heading
        for chapter_number in sorted(edition_chapters)
        for heading in edition_chapters[chapter_number]["headings"]
    )
    require(actual == expected, f"Edition heading text differs from localized source of truth: {label}")
    require_reviewed_targets(edition_chapters, reviewed, label)


def compare_native_chapters(expected: list[dict], actual: list[dict], reference: str) -> None:
    """Compare derived source units, not counts or manifest assertions."""
    require(len(expected) == len(actual), f"Source chapter count differs: {reference}")
    for expected_chapter, actual_chapter in zip(expected, actual, strict=True):
        chapter = expected_chapter["number"]
        require(actual_chapter.get("number") == chapter, f"Source chapter coordinate differs: {reference} {chapter}")
        require(
            actual_chapter.get("superscription") == expected_chapter.get("superscription"),
            f"Source superscription differs: {reference} {chapter}",
        )
        if "headings" in expected_chapter:
            require(
                actual_chapter.get("headings") == expected_chapter["headings"],
                f"Edition heading table differs: {reference} {chapter}",
            )
        derived = expected_chapter["verses"]
        packaged = actual_chapter.get("verses", [])
        require(len(derived) == len(packaged), f"Source native-unit count differs: {reference} {chapter}")
        for source_verse, packaged_verse in zip(derived, packaged, strict=True):
            verse = source_verse["verse"]
            label = f"{reference} {chapter}:{verse}"
            source_coordinate = (
                source_verse["chapter"], verse, source_verse.get("verseEnd", verse)
            )
            packaged_coordinate = (
                packaged_verse.get("chapter"), packaged_verse.get("verse"),
                packaged_verse.get("verseEnd", packaged_verse.get("verse")),
            )
            require(source_coordinate == packaged_coordinate, f"Source native coordinate differs: {label}")
            require(source_verse == packaged_verse, f"Source text/markup differs: {label}")


def verify_canonical_source(
    repo_root: Path, config, source_root: Path, pysword_path: Path | None,
    edition_dir: Path, manifest: dict,
) -> None:
    archive = source_root / config.archive_name
    verify_pin(archive, config.archive_sha256)
    auxiliary = None
    if config.auxiliary_sha256 is not None:
        require(config.auxiliary_name is not None, f"Incomplete auxiliary source pin: {config.language}")
        auxiliary = source_root / config.auxiliary_name
        verify_pin(auxiliary, config.auxiliary_sha256)
    if config.source_format == "sword":
        parsed = importer.parse_sword(config, archive, pysword_path)
        extracted_hash = None
    elif config.source_format == "krv-json":
        require(auxiliary is not None, f"Missing KRV auxiliary source: {config.language}")
        parsed = importer.parse_krv_json(config, archive, auxiliary)
        extracted_hash = None
    else:
        parsed, extracted_hash = importer.parse_usfm_archive(archive)
    if extracted_hash is not None:
        require(
            manifest["source"].get("extractedScriptureFileSetSha256") == extracted_hash,
            f"Extracted source file-set hash differs: {config.language}",
        )
    source_has_jesus_markup = any(
        verse.source_jesus_spans
        for book in parsed.values()
        for chapter in book.chapters
        for verse in chapter.verses
    )
    reviewed_jesus = (
        importer.ReviewedJesusSpans.load(repo_root, config.language, config.edition_id)
        if (config.language, config.edition_id) in importer.REVIEWED_JESUS_EDITIONS
        else None
    )
    mixed_candidates = set()
    full_candidates = set()
    manifest_books = {(row["collection"], row["bookId"]): row for row in manifest["books"]}
    for code, collection, book_id in BOOKS:
        require(code in parsed, f"Pinned source lacks {config.language}/{book_id}")
        native, mapping = importer.apply_display_versification(
            config, code, parsed[code],
            importer.base_verse_units(repo_root, config.language, collection, book_id),
        )
        inherited, mixed = importer.base_jesus_ranges(repo_root, collection, book_id)
        if reviewed_jesus is not None:
            full_candidates.update(
                importer.verse_key(collection, book_id, chapter_number, number)
                for chapter_number, ranges in inherited.items()
                for start, end in ranges for number in range(start, end + 1)
            )
        expected = [
            importer.chapter_to_json(
                chapter, inherited, mixed,
                allow_inherited_jesus=not source_has_jesus_markup,
            )[0]
            for chapter in native.chapters
        ]
        if reviewed_jesus is not None:
            for chapter in expected:
                for verse_row in chapter["verses"]:
                    if verse_row.get("sourcePlaceholder"):
                        continue
                    key = importer.verse_key(collection, book_id, chapter["number"], verse_row["verse"])
                    ledger_row = reviewed_jesus.rows.get(key)
                    is_mixed = importer.overlaps(
                        mixed.get(chapter["number"], []),
                        verse_row["verse"], verse_row.get("verseEnd", verse_row["verse"]),
                    )
                    is_supplemental = ledger_row is not None and ledger_row.get("supplementalFor") is not None
                    is_full_override = ledger_row is not None and ledger_row.get("overrideInherited") is True
                    if not is_mixed and not is_supplemental and not is_full_override:
                        continue
                    require("[J]" not in verse_row["text"] or is_full_override, f"Inherited J overlaps reviewed target: {key}")
                    if is_mixed:
                        mixed_candidates.add(key)
                    raw = verse_row["text"].replace("[J]", "").replace("[/J]", "") if is_full_override else verse_row["text"]
                    verse_row["text"] = reviewed_jesus.apply(
                        collection, book_id, chapter["number"], verse_row["verse"],
                        raw, verse_row.get("verseEnd", verse_row["verse"]),
                    )
            reviewed_jesus.verify_relocations(collection, book_id, expected)
        expected_headings = importer.heading_overrides(
            repo_root, config, code, collection, book_id, native,
        )
        for chapter in expected:
            chapter["headings"] = expected_headings[chapter["number"]]
        overlay = json.loads((edition_dir / collection / f"{book_id}.json").read_text(encoding="utf-8"))
        reference = f"{config.language}/{book_id}"
        compare_native_chapters(expected, overlay["chapters"], reference)
        require(overlay.get("sourceMapping") == mapping, f"Source display mapping differs: {reference}")
        require(
            manifest_books[(collection, book_id)]["sourceFileSha256"] == native.source_sha256,
            f"Source file pin differs: {reference}",
        )
        require(
            manifest_books[(collection, book_id)]["sourceFile"] == native.source_name,
            f"Source file name differs: {reference}",
        )
    if reviewed_jesus is not None:
        reviewed_jesus.validate_coverage(mixed_candidates, full_candidates)


def expected_synodal_dc_chapters(bible, source_book, spec, repo_root: Path, book_id: str) -> tuple[list[dict], list[int]]:
    """Derive DC chapter rows directly from the pinned SWORD module in memory."""
    base = synodal._base_units(repo_root, book_id)
    base_headings = synodal._base_headings(repo_root, book_id)
    mapping = synodal._chapter_map(spec, source_book)
    require(set(base) == {display for _, display in mapping}, f"DC source chapter inventory differs: ru/{book_id}")
    chapters: list[dict] = []
    for source_number, display_number in mapping:
        require(1 <= source_number <= len(source_book.chapter_lengths), f"DC source chapter absent: ru/{book_id} {source_number}")
        count = int(source_book.chapter_lengths[source_number - 1])
        require(base[display_number] == [(n, n) for n in range(1, count + 1)],
                f"DC source verse slots differ: ru/{book_id} {display_number}")
        verses = []
        superscription = None
        for number in range(1, count + 1):
            raw = str(bible.get(books=source_book.name, chapters=source_number, verses=number, clean=False))
            title = synodal._source_title(
                raw,
                permitted=(spec.allow_canonical_psalm_title and source_number == spec.source_chapters[0]
                           and display_number == 1 and number == 1),
                source_reference=f"{source_book.osis_name} {source_number}:{number}",
            )
            if title is not None:
                require(superscription is None, f"Multiple DC superscriptions: ru/{book_id}")
                superscription = title
            text = synodal._normalize_text(str(bible.get(
                books=source_book.name, chapters=source_number, verses=number, clean=True,
            )))
            require(text and not synodal.XML_TAG.search(text) and "\\" not in text,
                    f"Invalid pinned DC source text: ru/{book_id} {display_number}:{number}")
            require(not any(marker in text for marker in ("[J]", "[/J]", "[DN]", "[/DN]")),
                    f"Unexpected display marker in pinned DC source: ru/{book_id} {display_number}:{number}")
            require(title is None or title not in text,
                    f"DC canonical title remains in verse text: ru/{book_id} {display_number}:{number}")
            verses.append({"chapter": display_number, "verse": number, "text": text})
        chapter = {
            "number": display_number,
            "lastVerse": count,
            "verseUnitCount": count,
            "headings": base_headings.get(display_number, []),
            "verses": verses,
        }
        if superscription is not None:
            chapter["superscription"] = superscription
        chapters.append(chapter)
    return chapters, [source for source, _ in mapping]


def verify_synodal_dc_source(
    repo_root: Path, source_root: Path, pysword_path: Path | None,
    edition_dir: Path, manifest: dict,
) -> None:
    archive = source_root / "RusSynodal.zip"
    verify_pin(archive, SYNODAL_DC_SHA256)
    bible, _metadata, source_books = synodal._open_sword(archive, pysword_path)
    manifest_books = {(row["collection"], row["bookId"]): row for row in manifest["books"]}
    for book_id, spec in SYNODAL_DC_BOOKS.items():
        require(spec.module_book in source_books, f"Pinned DC source lacks ru/{book_id}")
        source_book = source_books[spec.module_book]
        expected, source_chapters = expected_synodal_dc_chapters(bible, source_book, spec, repo_root, book_id)
        overlay = json.loads((edition_dir / "deuterocanonical" / f"{book_id}.json").read_text(encoding="utf-8"))
        compare_native_chapters(expected, overlay["chapters"], f"ru/{book_id}")
        proof = synodal._proof(archive, source_book, source_chapters)
        require(overlay.get("sourceProof") == proof, f"Pinned DC source proof differs: ru/{book_id}")
        require(manifest_books[("deuterocanonical", book_id)].get("sourceProof") == proof,
                f"Manifest DC source proof differs: ru/{book_id}")
        require(manifest_books[("deuterocanonical", book_id)].get("sourceFileSha256") == SYNODAL_DC_SHA256,
                f"Manifest DC source pin differs: ru/{book_id}")


def validate_edition(
    repo_root: Path, config, source_root: Path | None = None,
    pysword_path: Path | None = None,
) -> dict:
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
    require(
        manifest.get("headings", {}).get("placement") ==
        "Explicit per-chapter beforeVerse lookup in the alternate edition's displayed coordinates.",
        f"Missing heading-table provenance: {manifest_path}",
    )
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

    manifest_by_book = {
        (row["collection"], row["bookId"]): row for row in manifest["books"]
    }
    require(len(manifest_by_book) == len(manifest["books"]), f"Duplicate manifest book: {manifest_path}")
    dc_index = [row for row in json.loads((
        repo_root / "shared" / "assets" / "books" / "deuterocanonical" /
        config.language / "_index.json"
    ).read_text(encoding="utf-8")) if row[0]]
    expected_manifest_keys = {
        (collection, book_id) for _code, collection, book_id in BOOKS
    } | {("deuterocanonical", row[0]) for row in dc_index}
    require(
        set(manifest_by_book) == expected_manifest_keys,
        f"Manifest inventory mismatch: {manifest_path}",
    )
    dc_overlays = set(SYNODAL_DC_BOOKS) if config.language == "ru" else set()
    books_to_validate = list(BOOKS) + [
        (manifest_by_book[("deuterocanonical", book_id)]["sourceBookCode"], "deuterocanonical", book_id)
        for book_id in sorted(dc_overlays)
    ]
    expected_outputs = {
        edition_dir / collection / f"{book_id}.json"
        for _code, collection, book_id in books_to_validate
    } | {manifest_path}
    reference_map = reference_map_for(config, repo_root)
    reviewed_maps = load_heading_maps(
        repo_root / "tools" / "traditional" / "edition_heading_maps.json"
    )
    if reference_map is not None:
        map_path = edition_dir / "_reference_map.json"
        expected_outputs.add(map_path)
        require(map_path.is_file(), f"Missing edition reference map: {map_path}")
        require(json.loads(map_path.read_text(encoding="utf-8")) == reference_map,
                f"Edition reference map differs from reviewed input: {map_path}")
        require(manifest.get("referenceMap") == {
            "path": "_reference_map.json",
            "books": [book["bookId"] for book in reference_map["books"]],
            "provenance": reference_map["provenance"],
        }, f"Missing reference map attribution: {manifest_path}")
    actual_outputs = set(edition_dir.rglob("*.json"))
    require(actual_outputs == expected_outputs, f"Output inventory mismatch: {edition_dir}")

    totals = {
        "overlayBooks": 0,
        "fallbackBooks": len(dc_index) - len(dc_overlays),
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
        "reviewedMixedJesusVerseSpans": sum(
            row.get("reviewedMixedJesusVerseSpans", 0)
            for row in manifest["books"]
        ),
        "reviewedOmittedJesusVerseUnits": sum(
            row.get("reviewedOmittedJesusVerseUnits", 0)
            for row in manifest["books"]
        ),
        "reviewedRelocatedJesusVerseUnits": sum(
            row.get("reviewedRelocatedJesusVerseUnits", 0)
            for row in manifest["books"]
        ),
        "supplementalJesusVerseUnits": sum(
            row.get("supplementalJesusVerseUnits", 0)
            for row in manifest["books"]
        ),
        "reviewedFullJesusVerseUnits": sum(
            row.get("reviewedFullJesusVerseUnits", 0)
            for row in manifest["books"]
        ),
        "reviewedFullOmittedJesusVerseUnits": sum(
            row.get("reviewedFullOmittedJesusVerseUnits", 0)
            for row in manifest["books"]
        ),
        "translatorAdditionSpans": 0,
        "superscriptions": 0,
        "promotedFootnoteVerses": 0,
        "sourcePlaceholderVerses": 0,
    }

    for code, collection, book_id in books_to_validate:
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
        if collection == "deuterocanonical":
            proof = overlay.get("sourceProof", {})
            require(proof.get("archiveSha256") == SYNODAL_DC_SHA256, f"DC source hash mismatch: {path}")
            require(proof.get("license") == "Public Domain" and proof.get("module") == "RusSynodal",
                    f"DC source metadata mismatch: {path}")
            require(manifest_by_book[(collection, book_id)].get("sourceProof") == proof,
                    f"DC manifest/source mismatch: {path}")

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
            require("headings" in chapter, f"Missing explicit heading table: {path} chapter {chapter_number}")
            effective_headings = chapter["headings"]
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
        reviewed = reviewed_maps.get((config.language, config.edition_id, code))
        if reviewed is not None:
            require(
                (reviewed.collection, reviewed.book_id) == (collection, book_id),
                f"Reviewed heading map points to wrong book: {path}",
            )
        validate_localized_headings(story_by_chapter, overlay_by_chapter, reviewed, str(path))

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
        if row[0] in dc_overlays:
            require(item["coverage"] == "full", f"DC overlay missing: {config.language}/{row[0]}")
            continue
        require(item["coverage"] == "fallback", f"DC fallback missing: {config.language}/{row[0]}")
        require(item["fallbackEditionId"] == "current", f"DC fallback target mismatch: {config.language}/{row[0]}")

    require(totals == manifest["totals"], f"Manifest totals mismatch: {config.language}")
    if source_root is not None:
        verify_canonical_source(repo_root, config, source_root, pysword_path, edition_dir, manifest)
        if config.language == "ru":
            verify_synodal_dc_source(repo_root, source_root, pysword_path, edition_dir, manifest)
    return {
        "language": config.language,
        "editionId": config.edition_id,
        "verification": "pinned-source-text-and-structure" if source_root is not None else "structure-only",
        **totals,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, help="Pinned archive directory; enables word-for-word source verification")
    parser.add_argument("--pysword-path", type=Path, help="pysword 0.2.8 install directory for SWORD source checks")
    parser.add_argument("--languages", nargs="*", choices=[config.language for config in EDITIONS],
                        help="Validate only these language tags; the default validates all editions")
    args = parser.parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    selected = set(args.languages or [config.language for config in EDITIONS])
    try:
        reports = [
            validate_edition(repo_root, config, args.source_root, args.pysword_path)
            for config in EDITIONS if config.language in selected
        ]
    except (OSError, KeyError, TypeError, ValueError, ValidationError,
            HeadingAuditError, HeadingMapError,
            importer.ImportErrorDetail, synodal.SynodalImportError) as exc:
        print(f"Traditional-edition validation failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
