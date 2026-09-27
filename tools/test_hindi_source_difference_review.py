from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import sys
import unittest


TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

from hindi_source_difference_review import (  # noqa: E402
    REVIEW_PATH,
    ReviewError,
    classify_difference,
    raw_visible_with_it_boundaries,
    remove_reviewed_spans,
    whitespace_gap_differences,
)


class HindiSourceDifferenceReviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.review = json.loads(REVIEW_PATH.read_text(encoding="utf-8"))
        cls.rows = cls.review["adjudications"]

    def test_complete_inventory_and_classification_counts(self) -> None:
        self.assertEqual(1, self.review["schemaVersion"])
        self.assertEqual(31_104, self.review["summary"]["appVerseUnits"])
        self.assertEqual(31_104, self.review["summary"]["sourceVerseUnits"])
        self.assertEqual(28_448, self.review["summary"]["strictExact"])
        self.assertEqual(2_656, len(self.rows))
        self.assertEqual(2_656, len({row["reference"] for row in self.rows}))
        self.assertEqual(Counter({
            "cross_reference_apparatus": 2_502,
            "source_export_whitespace": 137,
            "cross_reference_apparatus_and_source_export_whitespace": 17,
        }), Counter(row["classification"] for row in self.rows))
        speech = self.review["jesusWordBoundaryAudit"]
        self.assertEqual(31_104, speech["verseUnitsChecked"])
        self.assertEqual(28_448, speech["exactTextUnitsChecked"])
        self.assertEqual(2_656, speech["reviewedPresentationUnitsChecked"])
        self.assertEqual(229, speech["initialReviewedPresentationMismatches"])
        self.assertEqual(2_427, speech["initialReviewedPresentationMatches"])
        self.assertEqual(2_519, speech["appOnlyApparatusSpansForcedNarration"])
        self.assertEqual(0, speech["remainingMismatches"])
        self.assertEqual([], speech["remainingMismatchReferences"])

    def test_every_apparatus_span_has_exact_source_evidence(self) -> None:
        app_spans = 0
        source_spans = 0
        for row in self.rows:
            raw = row["sourceRawUsfm"]
            for span in row["approvedAppApparatus"]:
                app_spans += 1
                self.assertEqual("bdit", span["sourceUsfmMarker"])
                self.assertIn(span["text"], raw)
            for span in row["approvedSourceApparatus"]:
                source_spans += 1
                self.assertIn(span["sourceUsfmMarker"], {"it", "plain"})
                self.assertIn(span["text"], raw)
                self.assertTrue(row["sourceHtmlVerified"])
        self.assertEqual(2_519, app_spans)
        self.assertEqual(0, source_spans)

    def test_every_whitespace_exception_is_tied_to_source_it_markup(self) -> None:
        rows = [
            row for row in self.rows
            if row["classification"] in {
                "source_export_whitespace",
                "cross_reference_apparatus_and_source_export_whitespace",
            }
        ]
        self.assertEqual(154, len(rows))
        for row in rows:
            self.assertIn("it", row["sourcePresentationMarkers"])
            self.assertIn("\\it", row["sourceRawUsfm"])
            app = remove_reviewed_spans(row["appText"], row["approvedAppApparatus"])
            source = remove_reviewed_spans(row["sourceText"], row["approvedSourceApparatus"])
            gaps = whitespace_gap_differences(app, source)
            _visible, boundaries = raw_visible_with_it_boundaries(row["sourceRawUsfm"])
            self.assertEqual(row["approvedWhitespaceGaps"], gaps)
            self.assertLessEqual(set(gaps), boundaries)

    def test_only_lexical_difference_is_recorded_as_repaired_revelation_12_18(self) -> None:
        lexical = [row for row in self.rows if row["classification"] == "unresolved_lexical"]
        self.assertEqual([], lexical)
        self.assertEqual(1, len(self.review["reviewedRepairs"]))
        row = self.review["reviewedRepairs"][0]
        self.assertEqual("revelation 12:18", row["reference"])
        self.assertEqual(
            "\u0914\u0930 \u0905\u091c\u0917\u0930 \u0938\u092e\u0941\u0926\u094d\u0930 \u0915\u0947 \u0915\u093f\u0928\u093e\u0930\u0947 \u0915\u0940 \u0930\u0947\u0924 \u092a\u0930 \u0916\u0921\u093c\u093e \u0939\u094b \u0917\u092f\u093e\u0964",
            row["oldText"],
        )
        self.assertEqual(
            "\u0914\u0930 \u0935\u0939 \u0938\u092e\u0941\u0926\u094d\u0930 \u0915\u0947 \u0930\u0947\u0924 \u092a\u0930 \u091c\u093e \u0916\u0921\u093c\u093e \u0939\u0941\u0906\u0964",
            row["repairedText"],
        )
        self.assertTrue(row["sourceHtmlVerified"])
        self.assertEqual("https://ebible.org/Scriptures/hin2017_usfm.zip", row["sourceArchiveUrl"])

    def test_exact_span_removal_rejects_stale_text(self) -> None:
        row = next(row for row in self.rows if row["approvedAppApparatus"])
        spans = row["approvedAppApparatus"]
        remove_reviewed_spans(row["appText"], spans)
        changed = row["appText"].replace(spans[0]["text"], "(changed)", 1)
        with self.assertRaises(ReviewError):
            remove_reviewed_spans(changed, spans)

    def test_word_or_punctuation_change_cannot_become_presentation_only(self) -> None:
        word = classify_difference(
            code="REV",
            book_id="revelation",
            source_file="test.usfm",
            key=(12, 18, 18),
            app_text="alpha dragon.",
            source_text="alpha he.",
            raw_usfm="alpha he.",
        )
        punctuation = classify_difference(
            code="REV",
            book_id="revelation",
            source_file="test.usfm",
            key=(12, 18, 18),
            app_text="same!",
            source_text="same.",
            raw_usfm="same.",
        )
        self.assertEqual("unresolved_lexical", word["classification"])
        self.assertEqual("unresolved_lexical", punctuation["classification"])


if __name__ == "__main__":
    unittest.main()
