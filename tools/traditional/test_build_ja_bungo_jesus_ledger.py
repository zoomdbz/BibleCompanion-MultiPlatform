"""Offline guards for source-bound Japanese Bungo Jesus-word review."""

from __future__ import annotations

import hashlib
import json
import unittest

from build_ja_bungo_jesus_ledger import (
    BOOKS_NT, CACHE, LEDGER_PATH, REPORT_PATH, ROOT, TARGET, audit, build_documents,
)
from import_traditional_editions import base_jesus_ranges, overlaps
from jesus_word_spans import ReviewedJesusSpans


class BungoJesusLedgerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ledger = json.loads(LEDGER_PATH.read_text(encoding="utf-8"))
        cls.report = json.loads(REPORT_PATH.read_text(encoding="utf-8"))

    def test_source_proofs_and_semantic_inventory(self):
        self.assertEqual(634, len(self.ledger["rows"]))
        self.assertEqual({"kjvFull": 1402, "kjvMixed": 626},
                         {key: self.report["counts"][key] for key in ("kjvFull", "kjvMixed")})
        self.assertEqual(2060, self.report["counts"]["rawSiteRed"])
        self.assertEqual(2055, self.report["counts"]["siteRed"])
        self.assertEqual(27, len(self.report["pages"]))
        self.assertEqual(36, len(self.report["siteOnlyEditorialRed"]))
        self.assertEqual(319, len(self.report["targetTextDifferences"]))
        self.assertEqual(
            self.ledger["externalEvidence"]["reportSha256"],
            hashlib.sha256(REPORT_PATH.read_bytes()).hexdigest().upper(),
        )
        self.assertEqual(10, len(self.report["manualBoundaryReviews"]))

    def test_all_mixed_native_units_validate_without_changing_visible_text(self):
        reviewer = ReviewedJesusSpans.load(ROOT, "ja", "bungo")
        expected = set()
        expected_full = set()
        colored = 0
        for book_id in BOOKS_NT:
            target = json.loads((TARGET / f"{book_id}.json").read_text(encoding="utf-8"))
            full, mixed = base_jesus_ranges(ROOT, "new_testament", book_id)
            for chapter in target["chapters"]:
                chapter_number = chapter["number"]
                for verse in chapter["verses"]:
                    first, last = verse["verse"], verse.get("verseEnd", verse["verse"])
                    if not overlaps(mixed.get(chapter_number, []), first, last):
                        if overlaps(full.get(chapter_number, []), first, last):
                            key = ("new_testament", book_id, chapter_number, first)
                            expected_full.add(key)
                            if key in reviewer.rows:
                                self.assertTrue(reviewer.rows[key].get("overrideInherited"))
                                raw = verse["text"].replace("[J]", "").replace("[/J]", "")
                                self.assertEqual(raw, reviewer.apply(*key, raw, last))
                        continue
                    key = ("new_testament", book_id, chapter_number, first)
                    expected.add(key)
                    raw = verse["text"].replace("[J]", "").replace("[/J]", "")
                    rendered = reviewer.apply(*key, raw, last)
                    self.assertEqual(raw, rendered.replace("[J]", "").replace("[/J]", ""))
                    colored += rendered != raw
        reviewer.validate_coverage(expected, expected_full)
        self.assertEqual(626, len(expected))
        self.assertEqual(623, colored)
        self.assertEqual(8, sum(row.get("overrideInherited") is True for row in self.ledger["rows"]))
        for chapter, verse in ((18, 11), (23, 14)):
            row = reviewer.rows[("new_testament", "matthew", chapter, verse)]
            self.assertEqual([[0, 4]], row["review"]["evidence"]["nativeRedRuns"])
            self.assertTrue(row["noTargetSpeech"])
        omitted = {("luke", 9, 55), ("luke", 9, 56), ("luke", 20, 23)}
        self.assertEqual(omitted, {(row["bookId"], row["chapter"], row["verse"])
                                   for row in self.ledger["rows"]
                                   if row.get("noTargetSpeech") and not row.get("overrideInherited")})

    def test_cached_source_regenerates_exact_documents_when_present(self):
        if not all((CACHE / f"b{number}.html").is_file() for number in range(40, 67)):
            self.skipTest("ignored native-source HTML cache is not packaged in CI")
        ledger, report = build_documents(audit())
        self.assertEqual(self.ledger, ledger)
        self.assertEqual(self.report, report)


if __name__ == "__main__":
    unittest.main()
