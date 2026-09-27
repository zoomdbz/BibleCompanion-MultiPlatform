"""Offline regression checks for the reviewed Almeida 1911 Jesus-word ledger."""

from __future__ import annotations

import hashlib
import json
import unittest

from build_portuguese_jesus_ledger import HERE, ROOT, build
from jesus_word_spans import ReviewedJesusSpans


class PortugueseJesusLedgerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = HERE / "jesus_word_spans/pt_almeida1911.json"
        cls.ledger = json.loads(cls.path.read_text(encoding="utf-8"))

    def test_pinned_evidence_rebuilds_same_ledger(self):
        rebuilt = build()
        self.assertEqual(self.ledger, rebuilt)
        self.assertEqual(628, len(rebuilt["rows"]))
        self.assertEqual(626, sum(not row.get("overrideInherited") for row in rebuilt["rows"]))
        self.assertEqual(2, sum(row.get("overrideInherited") is True for row in rebuilt["rows"]))
        self.assertFalse(any(row.get("noTargetSpeech") or row.get("speechRelocatedTo")
                             for row in rebuilt["rows"]))
        for key in ("alignmentReport", "fullReport"):
            path = ROOT / rebuilt["externalEvidence"][key]
            self.assertEqual(rebuilt["externalEvidence"][key + "Sha256"],
                             hashlib.sha256(path.read_bytes()).hexdigest().upper())

    def test_complicated_speech_excludes_narrators_and_preserves_text(self):
        reviewer = ReviewedJesusSpans.load(ROOT, "pt", "almeida1911")
        target_root = ROOT / "shared/assets/books/editions/pt/almeida1911/new_testament"
        cases = (("matthew", 27, 46), ("mark", 6, 37), ("luke", 8, 45),
                 ("luke", 5, 14), ("revelation", 1, 8),
                 ("matthew", 19, 5), ("mark", 12, 37))
        rendered = {}
        for book_id, chapter_number, verse_number in cases:
            book = json.loads((target_root / f"{book_id}.json").read_text(encoding="utf-8"))
            verse = next(v for c in book["chapters"] if c["number"] == chapter_number
                         for v in c["verses"] if v["verse"] == verse_number)
            raw = verse["text"].replace("[J]", "").replace("[/J]", "")
            output = reviewer.apply("new_testament", book_id, chapter_number, verse_number, raw)
            self.assertEqual(raw, output.replace("[J]", "").replace("[/J]", ""))
            rendered[(book_id, chapter_number, verse_number)] = output
        self.assertEqual(2, rendered[("luke", 8, 45)].count("[J]"))
        self.assertEqual(2, rendered[("luke", 5, 14)].count("[J]"))
        self.assertIn("[/J] isto", rendered[("matthew", 27, 46)])
        self.assertIn("[/J] E elles disseram-lhe", rendered[("mark", 6, 37)])
        self.assertIn("[/J] diz o Senhor", rendered[("revelation", 1, 8)])
        self.assertTrue(rendered[("matthew", 19, 5)].startswith("E disse: [J]"))
        self.assertIn("[/J] E a grande multid", rendered[("mark", 12, 37)])


if __name__ == "__main__":
    unittest.main()
