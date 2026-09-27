"""Focused tests for the edition-specific heading source-of-truth table."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import heading_maps
import import_traditional_editions as importer


class HeadingMapLoaderTests(unittest.TestCase):
    def _document(self, relocations: list[dict]) -> dict:
        return {
            "schemaVersion": 1,
            "description": "Fixture.",
            "editions": [{
                "language": "xx",
                "editionId": "historical",
                "bookCode": "GEN",
                "collection": "old_testament",
                "bookId": "genesis",
                "pinnedSource": {
                    "title": "Pinned source",
                    "url": "https://example.invalid/pinned.zip",
                    "artifactSha256": "A" * 64,
                    "sourceDate": "2026-09-26",
                },
                "relocations": relocations,
            }],
        }

    def _load(self, document: dict):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "headings.json"
        path.write_text(json.dumps(document), encoding="utf-8")
        return heading_maps.load_heading_maps(path)

    @staticmethod
    def _row(text: str = "First\nSecond", join_with: str = "\n") -> dict:
        return {
            "sourceChapter": 1,
            "sourceBeforeVerse": 2,
            "sourceText": text,
            "joinWith": join_with,
            "targets": [
                {"chapter": 1, "beforeVerse": 3, "text": "First"},
                {"chapter": 2, "beforeVerse": 1, "text": "Second"},
            ],
        }

    def test_packaged_table_retains_only_live_cross_chapter_exceptions(self):
        path = Path(__file__).with_name("edition_heading_maps.json")
        tables = heading_maps.load_heading_maps(path)
        expected = {
            ("de", "luther1912", "ISA"): (8, 23, 9, 1),
            ("it", "diodati1885", "JOB"): (38, 39, 39, 1),
            ("ru", "synodal1876", "ROM"): (16, 25, 14, 24),
            ("zh-Hant", "cuv", "JHN"): (7, 53, 8, 1),
        }
        self.assertTrue(expected.keys() <= tables.keys())
        # The localized Bungo frog title now starts at Exodus 8:1 itself.
        # The old 7:25 -> 8:1 exception would refer to a removed title.
        self.assertNotIn(("ja", "bungo", "EXO"), tables)
        for key, coordinates in expected.items():
            relocation = tables[key].relocations[0]
            target = relocation.targets[0]
            self.assertEqual(
                (
                    relocation.source_chapter,
                    relocation.source_before_verse,
                    target.chapter,
                    target.before_verse,
                ),
                coordinates,
            )
            self.assertEqual(target.text, relocation.source_text)

    def test_composite_heading_can_split_without_changing_text(self):
        tables = self._load(self._document([self._row()]))
        relocation = tables[("xx", "historical", "GEN")].relocations[0]
        self.assertEqual([target.text for target in relocation.targets], ["First", "Second"])

    def test_space_separated_composite_heading_can_split_losslessly(self):
        tables = self._load(self._document([self._row("First Second", " ")]))
        relocation = tables[("xx", "historical", "GEN")].relocations[0]
        self.assertEqual(relocation.join_with, " ")

    def test_reversed_targets_fail_even_when_text_joins_losslessly(self):
        row = self._row("First Second", " ")
        row["targets"] = [
            {"chapter": 1, "beforeVerse": 2, "text": "First"},
            {"chapter": 1, "beforeVerse": 1, "text": "Second"},
        ]
        with self.assertRaisesRegex(heading_maps.HeadingMapError, "ascending Scripture order"):
            self._load(self._document([row]))

    def test_lossy_composite_split_fails_closed(self):
        row = self._row()
        row["targets"][1]["text"] = "Changed"
        with self.assertRaisesRegex(heading_maps.HeadingMapError, "do not preserve source text"):
            self._load(self._document([row]))

    def test_duplicate_source_coordinate_fails_closed(self):
        with self.assertRaisesRegex(heading_maps.HeadingMapError, "Duplicate heading source key"):
            self._load(self._document([self._row(), self._row()]))

    def test_invalid_target_coordinate_fails_closed(self):
        row = self._row()
        row["targets"][0]["beforeVerse"] = 0
        with self.assertRaisesRegex(heading_maps.HeadingMapError, "positive integer"):
            self._load(self._document([row]))

    def test_unapproved_fragment_separator_fails_closed(self):
        row = self._row()
        row["joinWith"] = " / "
        with self.assertRaisesRegex(heading_maps.HeadingMapError, "joinWith"):
            self._load(self._document([row]))


class HeadingMapImporterTests(unittest.TestCase):
    def setUp(self):
        self.config = next(config for config in importer.EDITIONS if config.language == "de")

    def _fixture(self, source_text: str, targets: list[dict], join_with: str = "\n"):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        base_path = root / "shared/assets/books/old_testament/de/psalms.json"
        base_path.parent.mkdir(parents=True)
        base_path.write_text(json.dumps({"stories": [{
            "id": "psalms-1",
            "headings": [{"beforeVerse": 2, "text": "First\nSecond"}],
            "summaryBullets": ["Base (1:1).", "Base (1:2)."],
        }, {
            "id": "psalms-2",
            "headings": [],
            "summaryBullets": ["Base (2:1)."],
        }]}), encoding="utf-8")
        document = {
            "schemaVersion": 1,
            "description": "Fixture.",
            "editions": [{
                "language": self.config.language,
                "editionId": self.config.edition_id,
                "bookCode": "PSA",
                "collection": "old_testament",
                "bookId": "psalms",
                "pinnedSource": {
                    "title": self.config.source_title,
                    "url": self.config.source_url,
                    "artifactSha256": self.config.archive_sha256,
                    "sourceDate": importer.SOURCE_DATE,
                },
                "relocations": [{
                    "sourceChapter": 1,
                    "sourceBeforeVerse": 2,
                    "sourceText": source_text,
                    "joinWith": join_with,
                    "targets": targets,
                }],
            }],
        }
        map_path = root / "headings.json"
        map_path.write_text(json.dumps(document), encoding="utf-8")
        source = importer.SourceBook("PSA", "fixture", "fixture", [
            importer.SourceChapter(1, verses=[
                importer.SourceVerse(1, 1, 1, parts=["Text."]),
                importer.SourceVerse(1, 2, 2, parts=["Text."]),
            ]),
            importer.SourceChapter(2, verses=[
                importer.SourceVerse(2, 1, 1, parts=["Text."]),
            ]),
        ])
        return root, map_path, source

    def test_exact_composite_text_splits_into_two_target_rows(self):
        root, map_path, source = self._fixture("First\nSecond", [
            {"chapter": 1, "beforeVerse": 2, "text": "First"},
            {"chapter": 2, "beforeVerse": 1, "text": "Second"},
        ])
        with patch.object(importer, "HEADING_MAP_PATH", map_path):
            headings = importer.heading_overrides(
                root, self.config, "PSA", "old_testament", "psalms", source
            )
        self.assertEqual(headings[1], [{"beforeVerse": 2, "text": "First"}])
        self.assertEqual(headings[2], [{"beforeVerse": 1, "text": "Second"}])

    def test_stale_base_heading_text_fails_closed(self):
        root, map_path, source = self._fixture("Stale\nText", [
            {"chapter": 1, "beforeVerse": 2, "text": "Stale"},
            {"chapter": 2, "beforeVerse": 1, "text": "Text"},
        ])
        with patch.object(importer, "HEADING_MAP_PATH", map_path):
            with self.assertRaisesRegex(importer.ImportErrorDetail, "source text mismatch"):
                importer.heading_overrides(
                    root, self.config, "PSA", "old_testament", "psalms", source
                )

    def test_target_must_be_a_native_unit_start(self):
        source_targets = [{"chapter": 1, "beforeVerse": 2, "text": "First\nSecond"}]
        root, map_path, source = self._fixture("First\nSecond", source_targets)
        source.chapters[0].verses = [
            importer.SourceVerse(1, 1, 2, parts=["Merged text."]),
        ]
        with patch.object(importer, "HEADING_MAP_PATH", map_path):
            with self.assertRaisesRegex(importer.ImportErrorDetail, "native verse-unit start"):
                importer.heading_overrides(
                    root, self.config, "PSA", "old_testament", "psalms", source
                )

    def test_heading_table_overrides_different_general_passage_anchor(self):
        root, map_path, source = self._fixture("First\nSecond", [
            {"chapter": 2, "beforeVerse": 1, "text": "First\nSecond"},
        ])
        passage_map = {"books": [{"bookId": "psalms", "mappings": [{
            "sourceChapter": 1,
            "sourceVerse": 2,
            "targetChapter": 1,
            "targetVerse": 1,
        }]}]}
        with (
            patch.object(importer, "HEADING_MAP_PATH", map_path),
            patch.object(importer, "reference_map_for", return_value=passage_map),
        ):
            headings = importer.heading_overrides(
                root, self.config, "PSA", "old_testament", "psalms", source
            )
        self.assertFalse(any(row["text"] == "First\nSecond" for row in headings[1]))
        self.assertEqual(headings[2], [{"beforeVerse": 1, "text": "First\nSecond"}])

    def test_pinned_source_mismatch_fails_closed(self):
        root, map_path, source = self._fixture("First\nSecond", [
            {"chapter": 1, "beforeVerse": 2, "text": "First\nSecond"},
        ])
        document = json.loads(map_path.read_text(encoding="utf-8"))
        document["editions"][0]["pinnedSource"]["artifactSha256"] = "B" * 64
        map_path.write_text(json.dumps(document), encoding="utf-8")
        with patch.object(importer, "HEADING_MAP_PATH", map_path):
            with self.assertRaisesRegex(importer.ImportErrorDetail, "provenance mismatch"):
                importer.heading_overrides(
                    root, self.config, "PSA", "old_testament", "psalms", source
                )


if __name__ == "__main__":
    unittest.main()
