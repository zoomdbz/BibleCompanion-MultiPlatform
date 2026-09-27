"""Focused reproducibility and exact-boundary tests for the KRV ledger."""

from __future__ import annotations

import json
import unittest

from build_ko_jesus_ledger import MANUAL, ROOT, automatic, build_ledger, key
from jesus_word_spans import ReviewedJesusSpans, sha256_text
from propose_jesus_word_spans import build_queue


LEDGER = ROOT / "tools/traditional/jesus_word_spans/ko_korrv.json"
OMISSIONS = {
    ("mark", 9, 31),
    ("luke", 9, 55),
    ("luke", 9, 56),
    ("luke", 20, 23),
}


def find(rows: list[dict], book: str, chapter: int, verse: int) -> dict:
    return next(row for row in rows if (row["bookId"], row["chapter"], row["verse"]) == (book, chapter, verse))


class KoreanJesusLedgerTests(unittest.TestCase):
    def test_all_626_candidate_units_rebuild_and_apply_without_text_drift(self) -> None:
        generated = build_ledger(ROOT)
        saved = json.loads(LEDGER.read_text(encoding="utf-8"))
        self.assertEqual(generated, saved)
        self.assertEqual(627, len(saved["rows"]))
        self.assertEqual(74, len(MANUAL))
        queue = build_queue(ROOT, "ko", "korrv")
        self.assertEqual(626, queue["candidateCount"])
        checker = ReviewedJesusSpans.load(ROOT, "ko", "korrv")
        keys = set()
        for candidate in queue["rows"]:
            native_key = (candidate["collection"], candidate["bookId"], candidate["chapter"], candidate["verse"])
            self.assertNotIn(native_key, keys)
            keys.add(native_key)
            row = find(saved["rows"], candidate["bookId"], candidate["chapter"], candidate["verse"])
            self.assertEqual(sha256_text(candidate["targetText"]), row["sourceTextSha256"])
            self.assertEqual(candidate["kjvTextSha256"], row["kjvWjVerseSha256"])
            rendered = checker.apply(*native_key, candidate["targetText"], candidate.get("verseEnd", candidate["verse"]))
            self.assertEqual(candidate["targetText"], rendered.replace("[J]", "").replace("[/J]", ""))
            if row.get("noTargetSpeech") or row.get("speechRelocatedTo"):
                self.assertNotIn("[J]", rendered)
            else:
                self.assertIn("[J]", rendered)
            if key(candidate) not in MANUAL:
                self.assertIsNotNone(automatic(candidate))
        moved_source = find(saved["rows"], "matthew", 20, 32)
        moved_target = find(saved["rows"], "matthew", 20, 33)
        self.assertEqual("supplemental", moved_source["speechRelocatedTo"]["fulfillment"])
        self.assertEqual("너희에게 무엇을 하여주기를 원하느냐",
                         moved_source["speechRelocatedTo"]["exactText"])
        self.assertEqual([{
            "collection": "new_testament", "bookId": "matthew", "chapter": 20, "verse": 32
        }], moved_target["supplementalFor"])
        target_book = json.loads(
            (ROOT / "shared/assets/books/editions/ko/korrv/new_testament/matthew.json")
            .read_text(encoding="utf-8")
        )
        target_verse = next(
            verse for chapter in target_book["chapters"] if chapter["number"] == 20
            for verse in chapter["verses"] if verse["verse"] == 33
        )
        raw_target = target_verse["text"].replace("[J]", "").replace("[/J]", "")
        rendered_target = checker.apply("new_testament", "matthew", 20, 33, raw_target, 33)
        self.assertIn("[J]너희에게 무엇을 하여주기를 원하느냐[/J]", rendered_target)
        self.assertEqual(raw_target, rendered_target.replace("[J]", "").replace("[/J]", ""))
        checker.verify_relocations(
            "new_testament", "matthew",
            [{"number": 20, "verses": [{"verse": 33, "text": rendered_target}]}],
        )
        checker.validate_coverage(keys)

    def test_reviewed_omissions_and_recast_boundaries(self) -> None:
        rows = build_ledger(ROOT)["rows"]
        self.assertEqual(OMISSIONS, {
            (row["bookId"], row["chapter"], row["verse"])
            for row in rows if row.get("noTargetSpeech")
        })
        self.assertEqual(622, sum(bool(row["spans"]) for row in rows))
        self.assertEqual(
            "데나리온 하나를 내게 보이라 뉘 화상과 글이 여기 있느냐",
            find(rows, "luke", 20, 24)["spans"][0]["exactText"],
        )
        self.assertEqual(
            [{"exactText": "나는 알파와 오메가라"}],
            find(rows, "revelation", 1, 8)["spans"],
        )
        self.assertEqual(
            [{"exactText": "주는 것이 받는 것보다 복이 있다"}],
            find(rows, "acts", 20, 35)["spans"],
        )
        self.assertEqual(2, len(find(rows, "john", 21, 15)["spans"]))
        self.assertEqual(2, len(find(rows, "john", 21, 16)["spans"]))
        self.assertEqual(2, len(find(rows, "john", 21, 17)["spans"]))
        self.assertEqual(
            [{"exactText": "내게 손을 댄 자가 누구냐"}],
            find(rows, "luke", 8, 45)["spans"],
        )
        self.assertEqual(
            [{"exactText": "달리다굼"}],
            find(rows, "mark", 5, 41)["spans"],
        )
        self.assertEqual(
            [{"exactText": "에바다"}],
            find(rows, "mark", 7, 34)["spans"],
        )


if __name__ == "__main__":
    unittest.main()
