"""Guard conservative, source-backed cross-edition identity extensions."""

from __future__ import annotations

import json
from pathlib import Path
import unittest

from audit_reference_identity_candidates import (
    ASSETS_ROOT, EVIDENCE, OUTPUT, REVIEWED_SEMANTIC_MAPS, ROOT,
    alternate_chapters, base_chapters, coordinates, evidence_for, generate, reviewed_semantic_maps,
)
from audit_reference_coverage import Anchor, Rule, _index, _rules_by_verse, audit, map_unit
from reference_maps import reference_map_for_edition


class ConcordantIdentityReferenceMapTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.document = json.loads(OUTPUT.read_text(encoding="utf-8"))

    def test_every_row_has_untouched_whole_native_units(self):
        self.assertEqual(1, self.document["schemaVersion"])
        for entry in self.document["maps"]:
            if entry["provenance"].get("replacesPreviouslyMappedIdentityUnits") is True:
                continue
            language, edition = entry["language"], entry["editionId"]
            existing = reference_map_for_edition(ROOT, language, edition, include_concordant=False)
            claimed = {book["bookId"]: book for book in existing["books"]} if existing else {}
            for book in entry["books"]:
                book_id = book["bookId"]
                prior = claimed.get(book_id, {}).get("mappings", [])
                used_source = {(row["sourceChapter"], n) for row in prior
                               for n in range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1)}
                used_target = {(row["targetChapter"], n) for row in prior
                               for n in range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1)}
                collection = "old_testament" if (ASSETS_ROOT / "old_testament" / language / f"{book_id}.json").is_file() else "new_testament"
                base = ASSETS_ROOT / collection / language / f"{book_id}.json"
                target = ASSETS_ROOT / "editions" / language / edition / collection / f"{book_id}.json"
                source_chapters = base_chapters(base)
                target_chapters = alternate_chapters(target)
                seen = set()
                for row in book["mappings"]:
                    chapter = row["sourceChapter"]
                    interval = {(chapter, n) for n in range(row["sourceVerse"], row["sourceVerseEnd"] + 1)}
                    self.assertFalse(interval & seen)
                    self.assertFalse(interval & used_source)
                    self.assertFalse(interval & used_target)
                    seen.update(interval)
                    self.assertEqual((chapter, row["sourceVerse"], row["sourceVerseEnd"]),
                                     (row["targetChapter"], row["targetVerse"], row["targetVerseEnd"]))
                    source_units = source_chapters[chapter]
                    target_units = target_chapters[chapter]
                    for units in (source_units, target_units):
                        self.assertTrue(interval <= {(chapter, n) for n in coordinates(units)})
                        for (first, last), _ in units:
                            native = {(chapter, n) for n in range(first, last + 1)}
                            self.assertFalse(native & interval and native - interval)
                if book["complete"]:
                    all_source = {(chapter, n) for chapter, units in source_chapters.items() for n in coordinates(units)}
                    self.assertFalse(prior)
                    self.assertEqual(seen, all_source)

    def test_fresh_browser_evidence_binds_every_non_english_mapped_story_when_present(self):
        if not EVIDENCE.is_dir():
            self.skipTest("ignored browser evidence is not packaged in CI")
        for entry in self.document["maps"]:
            if entry["language"] == "en":
                continue
            language, edition = entry["language"], entry["editionId"]
            for book in entry["books"]:
                book_id = book["bookId"]
                collection = "old_testament" if (ASSETS_ROOT / "old_testament" / language / f"{book_id}.json").is_file() else "new_testament"
                base = json.loads((ASSETS_ROOT / collection / language / f"{book_id}.json").read_text(encoding="utf-8"))
                alternate = json.loads((ASSETS_ROOT / "editions" / language / edition / collection / f"{book_id}.json").read_text(encoding="utf-8"))
                code = alternate["sourceBookCode"]
                for row in book["mappings"]:
                    chapter = row["sourceChapter"]
                    self.assertIsNotNone(evidence_for(language, code, chapter, base["stories"][chapter - 1]))

    def test_packaged_maps_and_manifests_match_combined_source(self):
        for entry in self.document["maps"]:
            language, edition = entry["language"], entry["editionId"]
            combined = reference_map_for_edition(ROOT, language, edition)
            directory = ASSETS_ROOT / "editions" / language / edition
            packaged = json.loads((directory / "_reference_map.json").read_text(encoding="utf-8"))
            manifest = json.loads((directory / "_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(packaged, combined)
            self.assertEqual(manifest["referenceMap"], {
                "path": "_reference_map.json",
                "books": [book["bookId"] for book in combined["books"]],
                "provenance": combined["provenance"],
            })

    def test_regeneration_is_deterministic_when_browser_evidence_exists(self):
        if not EVIDENCE.is_dir():
            self.skipTest("ignored browser evidence is not packaged in CI")
        self.assertEqual(generate(), self.document)

    def test_japanese_second_corinthians_closing_boundary_maps_both_directions(self):
        source = json.loads(REVIEWED_SEMANTIC_MAPS.read_text(encoding="utf-8"))
        self.assertEqual(source["maps"], reviewed_semantic_maps())
        self.assertEqual(self.document["maps"][-len(source["maps"]):], source["maps"])
        document = reference_map_for_edition(ROOT, "ja", "bungo")
        book = next(item for item in document["books"] if item["bookId"] == "2_corinthians")
        rules = [Rule(Anchor(row["sourceChapter"], row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"])),
                      Anchor(row["targetChapter"], row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"])))
                 for row in book["mappings"]]
        base = ASSETS_ROOT / "new_testament/ja/2_corinthians.json"
        target = ASSETS_ROOT / "editions/ja/bungo/new_testament/2_corinthians.json"
        source_index = _index([Anchor(ch, first, last) for ch, units in base_chapters(base).items()
                               for (first, last), _ in units], "JCB 2 Corinthians")
        target_index = _index([Anchor(ch, first, last) for ch, units in alternate_chapters(target).items()
                               for (first, last), _ in units], "Bungo 2 Corinthians")
        forward = _rules_by_verse(rules, False)
        reverse = _rules_by_verse(rules, True)
        self.assertEqual(map_unit(Anchor(13, 12, 12), forward, target_index),
                         (Anchor(13, 12, 13), "resolved"))
        self.assertEqual(map_unit(Anchor(13, 13, 13), forward, target_index),
                         (Anchor(13, 14, 14), "resolved"))
        self.assertEqual(map_unit(Anchor(13, 12, 12), reverse, source_index, True),
                         (Anchor(13, 12, 12), "resolved"))
        self.assertEqual(map_unit(Anchor(13, 13, 13), reverse, source_index, True),
                         (Anchor(13, 12, 12), "resolved"))
        self.assertEqual(map_unit(Anchor(13, 14, 14), reverse, source_index, True),
                         (Anchor(13, 13, 13), "resolved"))
        self.assertEqual(map_unit(Anchor(13, 11, 13), forward, target_index),
                         (Anchor(13, 11, 14), "resolved"))
        self.assertEqual(map_unit(Anchor(13, 12, 14), reverse, source_index, True),
                         (Anchor(13, 12, 13), "resolved"))

    def test_reviewed_semantic_maps_respect_the_language_scope(self):
        self.assertEqual([], reviewed_semantic_maps(("ko",)))
        self.assertEqual(["ja"], [entry["language"] for entry in reviewed_semantic_maps(("ja",))])

    def test_japanese_combined_line_maps_whole_native_range(self):
        japanese = next(entry for entry in self.document["maps"] if entry["language"] == "ja")
        genesis = next(book for book in japanese["books"] if book["bookId"] == "genesis")
        self.assertIn(1, [row["sourceChapter"] for row in genesis["mappings"]])
        source = base_chapters(ASSETS_ROOT / "old_testament/ja/genesis.json")[1]
        target = alternate_chapters(ASSETS_ROOT / "editions/ja/bungo/old_testament/genesis.json")[1]
        self.assertIn(((4, 5), source[3][1]), source)
        target_index = _index([Anchor(1, first, last) for (first, last), _ in target], "Bungo Genesis 1")
        rule = Rule(Anchor(1, 1, 31), Anchor(1, 1, 31))
        resolved, reason = map_unit(Anchor(1, 4, 5), _rules_by_verse([rule], False), target_index)
        self.assertEqual((resolved, reason), (Anchor(1, 4, 5), "resolved"))

    def test_arabic_luke7_publisher_fallback_restores_both_directions(self):
        base = json.loads((ASSETS_ROOT / "new_testament/ar/luke.json").read_text(encoding="utf-8"))
        story = next(story for story in base["stories"] if story["id"] == "luke-7")
        self.assertTrue(evidence_for("ar", "LUK", 7, story).startswith("publisher-fallback-sab-luke7:"))
        arabic = next(entry for entry in self.document["maps"] if entry["language"] == "ar")
        luke = next(book for book in arabic["books"] if book["bookId"] == "luke")
        self.assertIn(15, [n for row in luke["mappings"] if row["sourceChapter"] == 7
                           for n in range(row["sourceVerse"], row["sourceVerseEnd"] + 1)])
        entry = next(item for item in audit()["books"] if item["language"] == "ar"
                     and item["book"] == "new_testament/luke")
        self.assertEqual(0, entry["baseToAlternate"]["retainSourceEditionCoordinates"])
        self.assertEqual(0, entry["alternateToBase"]["retainSourceEditionCoordinates"])

    def test_korean_matthew20_question_keeps_cross_verse_boundary(self):
        map_doc = reference_map_for_edition(ROOT, "ko", "korrv")
        matthew = next(book for book in map_doc["books"] if book["bookId"] == "matthew")
        rules = [Rule(Anchor(row["sourceChapter"], row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"])),
                      Anchor(row["targetChapter"], row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"])))
                 for row in matthew["mappings"]]
        base = ASSETS_ROOT / "new_testament/ko/matthew.json"
        target = ASSETS_ROOT / "editions/ko/korrv/new_testament/matthew.json"
        source_index = _index([Anchor(ch, first, last) for ch, units in base_chapters(base).items()
                               for (first, last), _ in units], "RNKSV Matthew")
        target_index = _index([Anchor(ch, first, last) for ch, units in alternate_chapters(target).items()
                               for (first, last), _ in units], "KRV Matthew")
        self.assertEqual(map_unit(Anchor(20, 32, 32), _rules_by_verse(rules, False), target_index),
                         (Anchor(20, 32, 33), "resolved"))
        self.assertEqual(map_unit(Anchor(20, 32, 32), _rules_by_verse(rules, True), source_index, True),
                         (Anchor(20, 32, 32), "resolved"))
        self.assertEqual(map_unit(Anchor(20, 33, 33), _rules_by_verse(rules, True), source_index, True),
                         (Anchor(20, 32, 33), "resolved"))

    def test_italian_speech_relocations_override_broad_identity(self):
        document = reference_map_for_edition(ROOT, "it", "diodati1885")
        for book_id, chapter, source_verse, forward, reverse_verse, backward in (
            ("matthew", 9, 30, Anchor(9, 30, 31), 31, Anchor(9, 30, 31)),
            ("john", 8, 41, Anchor(8, 40, 41), 40, Anchor(8, 40, 41)),
        ):
            with self.subTest(book=book_id):
                book = next(item for item in document["books"] if item["bookId"] == book_id)
                rules = [Rule(Anchor(row["sourceChapter"], row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"])),
                              Anchor(row["targetChapter"], row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"])))
                         for row in book["mappings"]]
                base = ASSETS_ROOT / f"new_testament/it/{book_id}.json"
                target = ASSETS_ROOT / f"editions/it/diodati1885/new_testament/{book_id}.json"
                source_index = _index([Anchor(ch, first, last) for ch, units in base_chapters(base).items()
                                       for (first, last), _ in units], f"NR06 {book_id}")
                target_index = _index([Anchor(ch, first, last) for ch, units in alternate_chapters(target).items()
                                       for (first, last), _ in units], f"Diodati {book_id}")
                self.assertEqual(map_unit(Anchor(chapter, source_verse, source_verse),
                                          _rules_by_verse(rules, False), target_index), (forward, "resolved"))
                self.assertEqual(map_unit(Anchor(chapter, reverse_verse, reverse_verse),
                                          _rules_by_verse(rules, True), source_index, True), (backward, "resolved"))


if __name__ == "__main__":
    unittest.main()
