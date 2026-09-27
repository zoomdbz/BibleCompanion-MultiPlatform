"""Offline source-pinned tests for Russian Synodal Jesus-word review."""

from __future__ import annotations

import json
import unittest

from build_ru_jesus_ledger import OUTPUT, build_ledger, load_evidence, load_overrides
from jesus_word_spans import sha256_text


def find(rows: list[dict], book: str, chapter: int, verse: int) -> dict:
    return next(row for row in rows if (row["bookId"], row["chapter"], row["verse"]) == (book, chapter, verse))


class RussianJesusLedgerTests(unittest.TestCase):
    def test_pinned_evidence_and_ledger_rebuild_exactly(self) -> None:
        evidence = load_evidence()
        self.assertEqual(626, evidence["candidateCount"])
        self.assertEqual(1402, evidence["fullAuthorityCount"])
        self.assertEqual({"full-red": 1396, "partial-red": 6}, evidence["fullRedStatus"])
        self.assertEqual(19, evidence["textStatus"]["different-words"])
        self.assertEqual(2, evidence["sourceRedStatus"]["no-red"])
        self.assertEqual(
            "16DF67D751069FA23B481BA6FCFDF29422526E47727D21D912C71F7052ED3B4A",
            evidence["source"]["archiveSha256"],
        )
        for source in evidence["rows"] + evidence["fullExceptions"]:
            self.assertEqual(sha256_text(source["targetText"]), source["sourceTextSha256"])
        generated = build_ledger()
        saved = json.loads(OUTPUT.read_text(encoding="utf-8"))
        self.assertEqual(generated, saved)
        self.assertEqual(632, len(saved["rows"]))
        self.assertEqual(35, len(load_overrides()["rows"]))
        self.assertEqual(6, sum(row.get("overrideInherited") is True for row in saved["rows"]))

    def test_source_conflicts_glosses_and_relocation_stay_bounded(self) -> None:
        rows = build_ledger()["rows"]
        self.assertEqual([], find(rows, "mark", 11, 22)["spans"])
        self.assertEqual(
            {"chapter": 11, "verse": 23,
             "targetTextSha256": "03EC9CE727145A969B6C9F5DE7192EF058177798ADC33E3E5CCA4DB0E75AA3B0",
             "exactText": "имейте веру Божию", "fulfillment": "inherited"},
            find(rows, "mark", 11, 22)["speechRelocatedTo"],
        )
        self.assertEqual(
            [{"exactText": "ты - Симон, сын Ионин; ты наречешься Кифа,"}],
            find(rows, "john", 1, 42)["spans"],
        )
        self.assertEqual(
            [{"exactText": "Я есмь Альфа и Омега, начало и конец,"}],
            find(rows, "revelation", 1, 8)["spans"],
        )
        for book, chapter, verse in (
            ("matthew", 27, 46), ("mark", 5, 41),
            ("mark", 7, 34), ("mark", 15, 34),
        ):
            self.assertEqual(1, len(find(rows, book, chapter, verse)["spans"]))
        self.assertEqual(2, len(find(rows, "luke", 8, 45)["spans"]))
        self.assertEqual(
            "но ждите обещанного от Отца, о чем вы слышали от Меня,",
            find(rows, "acts", 1, 4)["spans"][0]["exactText"],
        )
        for book, chapter, verse in (
            ("matthew", 19, 5), ("matthew", 25, 30), ("mark", 12, 37),
            ("luke", 7, 41), ("luke", 8, 15), ("luke", 10, 22),
        ):
            self.assertTrue(find(rows, book, chapter, verse)["overrideInherited"])
        self.assertEqual(2, len(find(rows, "matthew", 25, 30)["spans"]))
        self.assertEqual(2, len(find(rows, "luke", 8, 15)["spans"]))


if __name__ == "__main__":
    unittest.main()
