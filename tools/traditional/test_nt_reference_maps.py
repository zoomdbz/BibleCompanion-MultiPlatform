"""Regression coverage for reviewed native NT merges, never verse fragments."""

import json
from pathlib import Path
import re
import unittest

from reference_maps import reference_map_for_edition
from test_edition_reference_exceptions import expand


ROOT = Path(__file__).resolve().parents[2]
BOOKS = ROOT / "shared/assets/books"
TRAILING = re.compile(r"\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\s*\.?\s*$")


def scripture_units(language, edition, book):
    if edition is None:
        payload = json.loads((BOOKS / "new_testament" / language / f"{book}.json").read_text(encoding="utf-8"))
        result = {}
        for story in payload["stories"]:
            for bullet in story["summaryBullets"]:
                match = TRAILING.search(bullet)
                if match is None:
                    raise AssertionError(f"Missing trailing reference: {language}/{book}")
                chapter, start, end = int(match[1]), int(match[2]), int(match[3] or match[2])
                for verse in range(start, end + 1):
                    result[chapter, verse] = bullet[:match.start()]
        return result
    payload = json.loads((BOOKS / "editions" / language / edition / "new_testament" / f"{book}.json").read_text(encoding="utf-8"))
    return {(verse["chapter"], number): verse["text"] for chapter in payload["chapters"]
            for verse in chapter["verses"] for number in range(verse["verse"], verse.get("verseEnd", verse["verse"]) + 1)}


class NtReferenceMapTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.maps = json.loads(Path(__file__).with_name("nt_reference_maps.json").read_text(encoding="utf-8"))["maps"]

    def test_every_explicit_coordinate_exists_in_both_editions(self):
        for edition in self.maps:
            for book in edition["books"]:
                with self.subTest(language=edition["language"], book=book["bookId"]):
                    source = scripture_units(edition["language"], None, book["bookId"])
                    target = scripture_units(edition["language"], edition["editionId"], book["bookId"])
                    mappings = expand(book["mappings"])
                    self.assertLessEqual(mappings.keys(), source.keys())
                    self.assertLessEqual(set().union(*mappings.values()), target.keys())

    def test_reviewed_map_is_packaged_once_without_losing_other_books(self):
        for edition in self.maps:
            combined = reference_map_for_edition(ROOT, edition["language"], edition["editionId"])
            books = {book["bookId"]: book for book in combined["books"]}
            self.assertEqual(len(books), len(combined["books"]))
            for book in edition["books"]:
                # A newer full-edition map may supersede this partial evidence
                # artifact. Every reviewed row must remain semantically present,
                # but the packaged book may contain additional proven rows or a
                # different compact representation of the same mappings.
                expected = expand(book["mappings"])
                actual = expand(books[book["bookId"]]["mappings"])
                for source, targets in expected.items():
                    self.assertEqual(targets, actual.get(source))
            manifest = json.loads((BOOKS / "editions" / edition["language"] / edition["editionId"] / "_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(edition["provenance"]["sourceArchiveSha256"].lower(), manifest["source"]["archiveSha256"].lower())

    def test_german_greetings_and_blessing_are_not_interchanged(self):
        source = scripture_units("de", None, "2_corinthians")
        target = scripture_units("de", "luther1912", "2_corinthians")
        self.assertIn("Es grüßen euch alle Heiligen", source[13, 12])
        self.assertIn("Es grüßen euch alle Heiligen", target[13, 13])
        self.assertIn("Die Gnade", source[13, 13])
        self.assertIn("Die Gnade", target[13, 14])

    def test_spanish_combined_greetings_and_assembly_dismissal(self):
        source = scripture_units("es", None, "2_corinthians")
        target = scripture_units("es", "rv1909", "2_corinthians")
        self.assertIn("Todos los creyentes", source[13, 13])
        self.assertIn("Todos los santos", target[13, 12])
        self.assertIn("Que la gracia", source[13, 14])
        self.assertIn("La gracia", target[13, 13])
        self.assertIn("despidió la asamblea", scripture_units("es", None, "acts")[19, 41])
        self.assertIn("despidió la concurrencia", scripture_units("es", "rv1909", "acts")[19, 40])

    def test_chinese_scripts_have_independent_boundary_evidence(self):
        source = scripture_units("zh-Hans", None, "john")
        target = scripture_units("zh-Hans", "cuv", "john")
        self.assertIn("都回家去了", source[7, 53])
        self.assertIn("橄榄山", source[8, 1])
        self.assertNotIn((7, 53), target)
        self.assertIn("都回家去了", target[8, 1])
        self.assertIn("橄榄山", target[8, 1])

    def test_english_merged_alias_and_source_variant_remain_whole(self):
        source = scripture_units("en", None, "3_john")
        self.assertEqual(source[1, 14], source[1, 15])
        self.assertIn("Greet the friends by name", scripture_units("en", "kjv1769", "3_john")[1, 14])
        source = scripture_units("en", None, "revelation")
        target = scripture_units("en", "kjv1769", "revelation")
        self.assertEqual(source[12, 17], source[12, 18])
        self.assertIn("the dragon stood", source[12, 18])
        self.assertIn("I stood", target[13, 1])
        self.assertNotIn((12, 18), target)


if __name__ == "__main__":
    unittest.main()
