#!/usr/bin/env python3
"""Validate complete Spanish and Portuguese edition-reference maps."""

from __future__ import annotations

import json
from pathlib import Path
import unittest

from build_iberian_reference_maps import CACHE, EDITIONS, build, cache_hash
from reference_maps import reference_map_for_edition


ROOT = Path(__file__).resolve().parents[2]
BOOKS = ROOT / "shared/assets/books"
DIRECTORY = Path(__file__).resolve().parent
EXPECTED = {
    "es": {
        "file": "es_rv1909_reference_map.json",
        "edition": "rv1909",
        "cache": "NVI",
        "cacheHash": "4D57E1F9E58E3310CB5E47153E82E456F673FB6E8B9A3FD9FA0DD8A17FC05C3C",
        "sourceUnits": 31087,
        "targetOnly": 16,
    },
    "pt": {
        "file": "pt_almeida1911_reference_map.json",
        "edition": "almeida1911",
        "cache": "NVT",
        "cacheHash": "46040F835A8EB27D889874119E90055345B0480727352F647E16718C511F73B1",
        "sourceUnits": 31104,
        "targetOnly": 0,
    },
}


def expand(rows: list[dict]) -> dict[tuple[int, int], tuple[int, int]]:
    result: dict[tuple[int, int], tuple[int, int]] = {}
    for row in rows:
        source = list(range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1))
        target = list(range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1))
        if len(source) > 1 and len(target) > 1 and len(source) != len(target):
            raise AssertionError(f"Ambiguous unequal multi-range: {row}")
        for index, source_verse in enumerate(source):
            key = (row["sourceChapter"], source_verse)
            if key in result:
                raise AssertionError(f"Duplicate source coordinate: {key}")
            target_verse = target[index] if len(source) == len(target) else target[0]
            result[key] = (row["targetChapter"], target_verse)
    return result


class IberianReferenceMapTests(unittest.TestCase):
    def document(self, language: str) -> dict:
        return json.loads((DIRECTORY / EXPECTED[language]["file"]).read_text(encoding="utf-8"))

    def book_map(self, language: str, book_id: str) -> dict[tuple[int, int], tuple[int, int]]:
        book = next(item for item in self.document(language)["books"] if item["bookId"] == book_id)
        return expand(book["mappings"])

    def book_rules(self, language: str, book_id: str) -> list[dict]:
        book = next(item for item in self.document(language)["books"] if item["bookId"] == book_id)
        return book["mappings"]

    def test_schema_provenance_and_complete_forward_coverage(self) -> None:
        for language, expected in EXPECTED.items():
            with self.subTest(language=language):
                document = self.document(language)
                self.assertEqual(document["schemaVersion"], 1)
                self.assertEqual(document["language"], language)
                self.assertEqual(document["editionId"], expected["edition"])
                self.assertEqual(len(document["books"]), 66)
                self.assertTrue(all(book["complete"] for book in document["books"]))
                self.assertEqual(
                    sum(len(expand(book["mappings"])) for book in document["books"]),
                    expected["sourceUnits"],
                )
                proof = document["provenance"]
                self.assertEqual(proof["modernStructure"]["aggregateSha256"], expected["cacheHash"])
                self.assertEqual(proof["unresolvedSourceOnlyCoordinates"], {})
                self.assertEqual(len(proof["unresolvedTargetOnlyCoordinates"]), expected["targetOnly"])
                self.assertIn("no a/b subdivisions", proof["coveragePolicy"])

    def test_reviewed_span_changes_remain_whole(self) -> None:
        chronicles = self.book_map("es", "1_chronicles")
        self.assertEqual(chronicles[1, 30], (1, 30))
        self.assertEqual(chronicles[1, 31], (1, 30))
        self.assertEqual(chronicles[21, 16], (21, 17))
        self.assertEqual(chronicles[21, 29], (21, 30))
        self.assertEqual(chronicles[21, 30], (21, 30))
        self.assertIn({
            "sourceChapter": 1, "sourceVerse": 32,
            "targetChapter": 1, "targetVerse": 31, "targetVerseEnd": 32,
        }, self.book_rules("es", "1_chronicles"))
        self.assertIn({
            "sourceChapter": 21, "sourceVerse": 15,
            "targetChapter": 21, "targetVerse": 15, "targetVerseEnd": 16,
        }, self.book_rules("es", "1_chronicles"))
        for language in ("es", "pt"):
            kings = self.book_map(language, "1_kings")
            self.assertEqual(kings[22, 44], (22, 45))
            self.assertEqual(kings[22, 52], (22, 53))
            self.assertEqual(kings[22, 53], (22, 53))
            self.assertIn({
                "sourceChapter": 22, "sourceVerse": 43,
                "targetChapter": 22, "targetVerse": 43, "targetVerseEnd": 44,
            }, self.book_rules(language, "1_kings"))
        second_chronicles = self.book_map("es", "2_chronicles")
        self.assertEqual(second_chronicles[33, 10], (33, 10))
        self.assertEqual(second_chronicles[33, 11], (33, 10))
        self.assertEqual(second_chronicles[33, 12], (33, 11))
        self.assertEqual(second_chronicles[33, 25], (33, 24))
        judges = self.book_map("es", "judges")
        self.assertEqual(judges[14, 19], (14, 20))
        self.assertEqual(judges[14, 20], (14, 20))
        self.assertIn({
            "sourceChapter": 14, "sourceVerse": 18,
            "targetChapter": 14, "targetVerse": 18, "targetVerseEnd": 19,
        }, self.book_rules("es", "judges"))
        philippians = self.book_map("es", "philippians")
        self.assertEqual(philippians[1, 16], (1, 17))
        self.assertEqual(philippians[1, 17], (1, 16))
        numbers = self.book_map("es", "numbers")
        self.assertEqual(numbers[13, 32], (13, 33))
        self.assertEqual(numbers[13, 33], (13, 33))
        self.assertEqual(numbers[30, 15], (30, 16))
        self.assertEqual(numbers[30, 16], (30, 16))
        hosea = self.book_map("es", "hosea")
        self.assertEqual(hosea[12, 13], (12, 14))
        self.assertEqual(hosea[12, 14], (12, 14))
        jonah = self.book_map("es", "jonah")
        self.assertEqual(jonah[2, 9], (2, 10))
        self.assertEqual(jonah[2, 10], (2, 10))
        job = self.book_map("es", "job")
        self.assertEqual(job[38, 39], (39, 1))
        self.assertEqual(job[40, 1], (39, 30))
        self.assertEqual(job[40, 6], (40, 1))
        portuguese = self.book_map("pt", "2_corinthians")
        self.assertEqual(portuguese[13, 12], (13, 12))
        self.assertEqual(portuguese[13, 13], (13, 13))
        revelation = self.book_map("pt", "revelation")
        self.assertEqual(revelation[12, 18], (13, 1))
        self.assertEqual(revelation[13, 1], (13, 1))

    def test_portuguese_reviewed_source_to_native_ranges_remain_whole(self) -> None:
        expected = {
            "1_kings": [
                (18, 33, 18, 33, 34),
                (22, 43, 22, 43, 44),
            ],
            "matthew": [(9, 2, 9, 1, 2)],
            "luke": [
                (4, 18, 4, 18, 19),
                (7, 18, 7, 18, 19),
            ],
            "acts": [
                (3, 20, 3, 19, 20),
                (10, 30, 10, 30, 31),
                (13, 33, 13, 32, 33),
                (24, 2, 24, 2, 3),
            ],
            "1_corinthians": [(6, 9, 6, 9, 10)],
            "2_corinthians": [(2, 11, 2, 10, 11)],
            "philippians": [(3, 13, 3, 13, 14)],
            "1_thessalonians": [(2, 7, 2, 6, 7)],
            "hebrews": [(11, 19, 11, 18, 19)],
            "revelation": [(2, 28, 2, 27, 28)],
        }
        for book_id, spans in expected.items():
            rules = self.book_rules("pt", book_id)
            for source_chapter, source_verse, target_chapter, target_verse, target_verse_end in spans:
                with self.subTest(book=book_id, chapter=source_chapter, verse=source_verse):
                    self.assertIn({
                        "sourceChapter": source_chapter,
                        "sourceVerse": source_verse,
                        "targetChapter": target_chapter,
                        "targetVerse": target_verse,
                        "targetVerseEnd": target_verse_end,
                    }, rules)

    def test_packaged_maps_and_manifest_match_loader(self) -> None:
        for language, expected in EXPECTED.items():
            with self.subTest(language=language):
                packaged = reference_map_for_edition(ROOT, language, expected["edition"])
                directory = BOOKS / "editions" / language / expected["edition"]
                self.assertEqual(
                    json.loads((directory / "_reference_map.json").read_text(encoding="utf-8")),
                    packaged,
                )
                manifest = json.loads((directory / "_manifest.json").read_text(encoding="utf-8"))
                self.assertEqual(manifest["referenceMap"], {
                    "path": "_reference_map.json",
                    "books": [book["bookId"] for book in packaged["books"]],
                    "provenance": packaged["provenance"],
                })

    def test_optional_cached_source_reproduction(self) -> None:
        if not all((CACHE / expected["cache"]).is_dir() for expected in EXPECTED.values()):
            self.skipTest("Ignored NVI/NVT structure caches are unavailable")
        for language, expected in EXPECTED.items():
            self.assertEqual(cache_hash(expected["cache"]), expected["cacheHash"])
            self.assertEqual(build(language), self.document(language))


if __name__ == "__main__":
    unittest.main()
