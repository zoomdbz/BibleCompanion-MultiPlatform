"""Offline fixtures for the licensed modern-edition comparator."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import compare_modern_editions as modern


class FakeClient:
    def __init__(self, content: str, *, licensed: bool = True, chapters: tuple[int, ...] = (1,)) -> None:
        self.content = content
        self.licensed = licensed
        self.chapters = chapters
        self.calls: list[str] = []

    def get_json(self, path: str, params: object = ()) -> dict:
        self.calls.append(path)
        if path == "/bibles":
            return {"data": [{"id": 3034, "abbreviation": "BSB"}] if self.licensed else []}
        if path == "/bibles/3034/books":
            assert not params, "Book collection does not accept pagination parameters"
            return {"data": [{"id": "GEN"}]}
        if path == "/bibles/3034/books/GEN/chapters":
            assert not params, "Chapter collection does not accept pagination parameters"
            return {"data": [{"id": str(number), "passage_id": f"GEN.{number}"} for number in self.chapters]}
        if path == "/bibles/3034/passages/GEN.1":
            return {"id": "GEN.1", "content": self.content, "reference": "Genesis 1"}
        raise AssertionError(f"unexpected fixture API path: {path}")


class ModernParityTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.books = Path(self.temp.name)
        path = self.books / "old_testament" / "en" / "genesis.json"
        path.parent.mkdir(parents=True)
        path.write_text(
            json.dumps({"stories": [{"id": "genesis-1", "summaryBullets": [
                "[J]He spoke.[/J] (1:1).", "A native bridge. (1:2-3).",
            ]}]}), encoding="utf-8"
        )
        self.edition = modern.EDITIONS["en"]
        self.source = (
            '<span class="yv-v" v="1"></span><span class="wj">He spoke.</span>'
            '<span class="yv-v" v="2" ev="3"></span>A native bridge.'
        )
        self.book_patch = patch.object(modern, "BOOKS", (("GEN", "old_testament", "genesis"),))
        self.book_patch.start()
        self.addCleanup(self.book_patch.stop)

    def test_native_range_and_jesus_boundary_match(self) -> None:
        result = modern.audit_edition(FakeClient(self.source), self.edition, self.books)
        self.assertEqual(result["status"], "match")
        self.assertEqual(result["rangesCompared"], 2)
        self.assertEqual(result["jesusMarkupCompared"], 2)
        self.assertEqual(result["findings"], [])

    def test_local_reference_does_not_require_an_english_space(self) -> None:
        path = self.books / "japanese.json"
        path.write_text(json.dumps({"stories": [{"summaryBullets": [
            "日本語の文。(1:1)", "続き。(1:2\u20133).",
        ]}]}), encoding="utf-8")
        verses = modern.load_local_book(path)[1]
        self.assertEqual(set(verses), {(1, 1), (2, 3)})
        self.assertEqual(verses[1, 1].plain, "日本語の文。")

    def test_wrong_source_chapter_does_not_pass(self) -> None:
        class WrongPassage(FakeClient):
            def get_json(self, path, params=()):
                payload = super().get_json(path, params)
                if "/passages/" in path:
                    payload["id"] = "GEN.2"
                return payload
        with self.assertRaises(modern.ValidationError):
            modern.audit_edition(WrongPassage(self.source), self.edition, self.books)

    def test_text_mismatch_report_contains_hashes_not_source_text(self) -> None:
        source = self.source.replace("A native bridge.", "Publisher wording.")
        result = modern.audit_edition(FakeClient(source), self.edition, self.books)
        self.assertEqual(result["status"], "mismatch")
        finding = next(item for item in result["findings"] if item["kind"] == "text_mismatch")
        self.assertEqual(finding["reference"], "GEN.1.2-3")
        self.assertEqual(len(finding["sourceSha256"]), 64)
        self.assertNotIn("Publisher wording", json.dumps(result))
        self.assertNotIn("A native bridge", json.dumps(result))

    def test_native_segmentation_is_not_treated_as_exact_text(self) -> None:
        source = (
            '<span class="yv-v" v="1"></span>He spoke.'
            '<span class="yv-v" v="2"></span>A native'
            '<span class="yv-v" v="3"></span>bridge.'
        )
        result = modern.audit_edition(FakeClient(source), self.edition, self.books)
        self.assertEqual(result["status"], "mismatch")
        kinds = [item["kind"] for item in result["findings"]]
        self.assertIn("local_range_only", kinds)
        self.assertIn("source_range_only", kinds)
        self.assertEqual(result["rangesCompared"], 1)

    def test_source_chapter_gap_is_a_mismatch_not_a_success(self) -> None:
        result = modern.audit_edition(FakeClient(self.source, chapters=(1, 2)), self.edition, self.books)
        self.assertEqual(result["status"], "mismatch")
        self.assertIn({"kind": "local_chapter_missing", "reference": "GEN.2"}, result["findings"])

    def test_license_absent_prevents_content_requests(self) -> None:
        client = FakeClient(self.source, licensed=False)
        result = modern.audit_edition(client, self.edition, self.books)
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(client.calls, ["/bibles"])

    def test_russian_published_localized_abbreviation_is_recognized(self) -> None:
        class RussianMetadata(FakeClient):
            def get_json(self, path, params=()):
                return {"data": [{"id": 143, "abbreviation": "\u041d\u0420\u041f"}]}
        self.assertTrue(modern._licensed(RussianMetadata(""), modern.EDITIONS["ru"]))

    def test_same_id_with_unexpected_edition_label_is_rejected(self) -> None:
        class WrongEdition(FakeClient):
            def get_json(self, path, params=()):
                return {"data": [{"id": 3034, "abbreviation": "KJV"}]}
        with self.assertRaises(modern.ValidationError):
            modern._licensed(WrongEdition(""), self.edition)

    def test_no_key_is_not_audited(self) -> None:
        result = modern.audit(["en", "hi"], self.books, None, 1.0, 0)
        self.assertEqual([item["status"] for item in result["languages"]], ["not_audited", "not_audited"])

    def test_challenge_is_blocked_without_source_leak(self) -> None:
        challenge = '<html><p>Verify you are human, secret challenge text.</p></html>'
        with patch.object(modern, "YouVersionClient", return_value=FakeClient(challenge)):
            result = modern.audit(["en"], self.books, "fixture-app-key", 1.0, 0)
        self.assertEqual(result["languages"][0]["status"], "blocked")
        self.assertIn("challenge", result["languages"][0]["blocker"])
        self.assertNotIn("secret", json.dumps(result))
        self.assertNotIn("fixture-app-key", json.dumps(result))

    def test_overlapping_native_range_is_rejected(self) -> None:
        source = (
            '<span class="yv-v" v="1" ev="2"></span>First.'
            '<span class="yv-v" v="2"></span>Second.'
        )
        parser = modern.RangedChapterParser("GEN.1")
        with self.assertRaises(modern.UnsupportedMarkup):
            parser.feed(source)
            parser.finish()

    def test_http_403_is_blocked_not_mismatch(self) -> None:
        class ForbiddenClient(FakeClient):
            def get_json(self, path: str, params: object = ()) -> dict:
                if path.endswith("/passages/GEN.1"):
                    raise modern.ApiError(path, 403, "publisher text denied")
                return super().get_json(path, params)

        with patch.object(modern, "YouVersionClient", return_value=ForbiddenClient(self.source)):
            result = modern.audit(["en"], self.books, "fixture-app-key", 1.0, 0)
        self.assertEqual(result["languages"][0]["status"], "blocked")
        self.assertIn("403", result["languages"][0]["blocker"])
        self.assertNotIn("publisher text denied", json.dumps(result))


if __name__ == "__main__":
    unittest.main()
