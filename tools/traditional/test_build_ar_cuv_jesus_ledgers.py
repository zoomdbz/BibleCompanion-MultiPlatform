"""Focused source-bound checks for Arabic Van Dyck and both CUV ledgers."""

from __future__ import annotations

import json
import unittest

from build_ar_cuv_jesus_ledgers import ROOT, build_ledger, candidate_rows
from jesus_word_spans import ReviewedJesusSpans


class ArabicChineseJesusLedgerTests(unittest.TestCase):
    def test_ledgers_cover_exact_kjv_mixed_native_units(self) -> None:
        for language, edition_id in (("ar", "van_dyck"), ("zh-Hans", "cuv"), ("zh-Hant", "cuv")):
            with self.subTest(language=language):
                expected = build_ledger(ROOT, language, edition_id)
                path = ROOT / "tools/traditional/jesus_word_spans" / f"{language}_{edition_id}.json"
                committed = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(expected, committed)
                self.assertEqual(626, len(committed["rows"]))
                self.assertEqual(len(committed["rows"]), len({
                    (row["collection"], row["bookId"], row["chapter"], row["verse"])
                    for row in committed["rows"]
                }))
                reviewed = ReviewedJesusSpans.load(ROOT, language, edition_id)
                candidates = candidate_rows(ROOT, language, edition_id)
                for candidate in candidates:
                    key = candidate["key"]
                    output = reviewed.apply(*key, candidate["raw"], candidate["verseEnd"])
                    self.assertEqual(candidate["raw"], output.replace("[J]", "").replace("[/J]", ""))
                    if candidate["noTargetSpeech"]:
                        self.assertNotIn("[J]", output)
                    else:
                        self.assertIn("[J]", output)
                reviewed.validate_coverage({candidate["key"] for candidate in candidates})

    def test_reviewed_translation_recasts_and_omission(self) -> None:
        arabic = build_ledger(ROOT, "ar", "van_dyck")
        simplified = build_ledger(ROOT, "zh-Hans", "cuv")
        traditional = build_ledger(ROOT, "zh-Hant", "cuv")

        def row(ledger: dict, book: str, chapter: int, verse: int) -> dict:
            return next(item for item in ledger["rows"]
                        if (item["bookId"], item["chapter"], item["verse"]) == (book, chapter, verse))

        self.assertEqual(2, row(arabic, "luke", 8, 45)["spans"][1]["occurrence"])
        self.assertEqual(30, row(simplified, "luke", 21, 29)["verseEnd"])
        self.assertEqual(30, row(traditional, "luke", 21, 29)["verseEnd"])
        for ledger in (simplified, traditional):
            self.assertTrue(row(ledger, "luke", 20, 23)["noTargetSpeech"])
            self.assertEqual([], row(ledger, "luke", 20, 23)["spans"])
            self.assertEqual("「我是阿拉法，我是俄梅戛，",
                             row(ledger, "revelation", 1, 8)["spans"][0]["exactText"])
            self.assertTrue(row(ledger, "acts", 20, 35)["spans"][0]["exactText"].startswith("『施比受更"))
        self.assertNotEqual(
            row(simplified, "matthew", 9, 28)["sourceTextSha256"],
            row(traditional, "matthew", 9, 28)["sourceTextSha256"],
        )


if __name__ == "__main__":
    unittest.main()
