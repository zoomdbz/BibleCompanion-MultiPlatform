import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_reference_coverage import (
    Anchor,
    AuditError,
    MARKER,
    Rule,
    _index,
    _rules_by_verse,
    audit,
    base_units,
    map_unit,
)


class ReferenceCoverageTests(unittest.TestCase):
    def test_malformed_base_reference_cannot_disappear_from_counts(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "book.json"
            path.write_text(json.dumps({"stories": [{"summaryBullets": [
                "One verse (1:1).", "A verse without its marker.",
            ]}]}), encoding="utf-8")
            with self.assertRaisesRegex(AuditError, "lacks a trailing native reference"):
                base_units(path)

    def test_trailing_marker_accepts_no_whitespace_and_en_dash(self):
        match = MARKER.search("Japanese text(4:2\u20133).")
        self.assertIsNotNone(match)
        self.assertEqual(match.groups(), ("4", "2", "3"))

    def test_compact_ranges_map_positionally_in_both_directions(self):
        rule = Rule(Anchor(51, 4, 21), Anchor(51, 2, 19))
        target = _index([Anchor(51, number, number) for number in range(2, 20)], "target")
        source = _index([Anchor(51, number, number) for number in range(4, 22)], "source")
        self.assertEqual(
            map_unit(Anchor(51, 12, 12), _rules_by_verse([rule], False), target),
            (Anchor(51, 10, 10), "resolved"),
        )
        self.assertEqual(
            map_unit(Anchor(51, 10, 10), _rules_by_verse([rule], True), source, reverse=True),
            (Anchor(51, 12, 12), "resolved"),
        )

    def test_native_merge_must_have_every_explicit_coordinate(self):
        target = _index([Anchor(1, 1, 3)], "target")
        partial = [Rule(Anchor(1, 2, 2), Anchor(1, 2, 2))]
        self.assertEqual(
            map_unit(Anchor(1, 2, 3), _rules_by_verse(partial, False), target),
            (None, "unreviewed"),
        )
        complete = [Rule(Anchor(1, 2, 3), Anchor(1, 2, 3))]
        self.assertEqual(
            map_unit(Anchor(1, 2, 3), _rules_by_verse(complete, False), target),
            (Anchor(1, 1, 3), "resolved"),
        )

    def test_merged_source_crossing_destination_chapters_is_retained(self):
        rules = [
            Rule(Anchor(12, 17, 17), Anchor(12, 17, 17)),
            Rule(Anchor(12, 18, 18), Anchor(13, 1, 1)),
        ]
        target = _index([Anchor(12, 17, 17), Anchor(13, 1, 1)], "target")
        self.assertEqual(
            map_unit(Anchor(12, 17, 18), _rules_by_verse(rules, False), target),
            (None, "cross_chapter"),
        )

    def test_disjoint_destinations_are_retained(self):
        rules = [
            Rule(Anchor(1, 1, 1), Anchor(1, 1, 1)),
            Rule(Anchor(1, 2, 2), Anchor(1, 3, 3)),
        ]
        target = _index([Anchor(1, 1, 1), Anchor(1, 3, 3)], "target")
        self.assertEqual(
            map_unit(Anchor(1, 1, 2), _rules_by_verse(rules, False), target),
            (None, "disjoint"),
        )

    def test_one_to_many_and_many_to_one_use_whole_native_units(self):
        one_to_many = Rule(Anchor(1, 2, 2), Anchor(1, 2, 3))
        target = _index([Anchor(1, 1, 3)], "target")
        self.assertEqual(
            map_unit(Anchor(1, 2, 2), _rules_by_verse([one_to_many], False), target),
            (Anchor(1, 1, 3), "resolved"),
        )
        many_to_one = Rule(Anchor(1, 2, 3), Anchor(1, 2, 2))
        self.assertEqual(
            map_unit(Anchor(1, 2, 3), _rules_by_verse([many_to_one], False), target),
            (Anchor(1, 1, 3), "resolved"),
        )


class PackagedFixtureTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.editions = {"xx": ("modern", "traditional")}
        self._write("old_testament/xx/example.json", {
            "stories": [{"summaryBullets": ["one(1:1).", "two and three (1:2\u20133)."]}]
        })
        self._write("editions/xx/traditional/_manifest.json", {
            "language": "xx", "editionId": "traditional",
            "books": [{"collection": "old_testament", "bookId": "example", "coverage": "full", "output": "old_testament/example.json"}],
        })
        self._write("editions/xx/traditional/old_testament/example.json", {
            "language": "xx", "editionId": "traditional", "collection": "old_testament", "bookId": "example",
            "chapters": [{"number": 1, "verses": [
                {"chapter": 1, "verse": n, "text": str(n)} for n in range(1, 4)
            ]}],
        })

    def _write(self, relative, value):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding="utf-8")

    def _map(self, rows, complete=False):
        self._write("editions/xx/traditional/_reference_map.json", {
            "schemaVersion": 1, "language": "xx", "baseEditionId": "modern", "editionId": "traditional",
            "books": [{"bookId": "example", "complete": complete, "mappings": rows}],
        })

    def test_missing_map_retains_all_units_even_with_equal_inventory(self):
        row = audit(self.root, self.editions)["books"][0]
        self.assertIsNone(row["map"])
        self.assertEqual(row["baseToAlternate"]["reviewedResolvable"], 0)
        self.assertEqual(row["baseToAlternate"]["retainSourceEdition"], 2)
        self.assertEqual(row["alternateToBase"]["retainSourceEdition"], 3)
        self.assertEqual(row["baseToAlternate"]["retainSourceEditionCoordinates"], 3)
        self.assertEqual(row["baseToAlternate"]["unresolvedRanges"], [
            {"chapter": 1, "start": 1, "end": 3, "reason": "unreviewed"},
        ])

    def test_partial_map_counts_only_explicit_native_unit(self):
        self._map([{"sourceChapter": 1, "sourceVerse": 1, "targetChapter": 1, "targetVerse": 1}])
        row = audit(self.root, self.editions)["books"][0]
        self.assertEqual(row["baseToAlternate"]["reviewedResolvable"], 1)
        self.assertEqual(row["baseToAlternate"]["retainSourceEdition"], 1)
        self.assertEqual(row["alternateToBase"]["reviewedResolvable"], 1)
        self.assertEqual(row["alternateToBase"]["retainSourceEdition"], 2)
        self.assertEqual(row["baseToAlternate"]["retainSourceEditionCoordinates"], 2)
        self.assertEqual(row["baseToAlternate"]["unresolvedRanges"], [
            {"chapter": 1, "start": 2, "end": 3, "reason": "unreviewed"},
        ])

    def test_complete_flag_does_not_infer_identity(self):
        self._map([{"sourceChapter": 1, "sourceVerse": 1, "targetChapter": 1, "targetVerse": 1}], complete=True)
        row = audit(self.root, self.editions)["books"][0]
        self.assertEqual(row["baseToAlternate"]["retainSourceEdition"], 1)

    def test_invalid_source_or_target_coordinate_fails(self):
        for row in (
            {"sourceChapter": 1, "sourceVerse": 4, "targetChapter": 1, "targetVerse": 1},
            {"sourceChapter": 1, "sourceVerse": 1, "targetChapter": 1, "targetVerse": 4},
        ):
            with self.subTest(row=row):
                self._map([row])
                with self.assertRaisesRegex(AuditError, "coordinate 1:4 is absent"):
                    audit(self.root, self.editions)

    def test_malformed_map_fails(self):
        self._map([{"sourceChapter": 1, "sourceVerse": 3, "sourceVerseEnd": 2, "targetChapter": 1, "targetVerse": 1}])
        with self.assertRaisesRegex(AuditError, "invalid verse range"):
            audit(self.root, self.editions)


if __name__ == "__main__":
    unittest.main()
