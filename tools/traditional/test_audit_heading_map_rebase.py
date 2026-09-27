"""Fail-closed tests for traditional heading-map rebase proposals."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

import audit_heading_map_rebase as auditor


class HeadingMapRebaseAuditTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.map_path = self.root / "tools/traditional/edition_heading_maps.json"
        self.book_path = self.root / "shared/assets/books/old_testament/xx/genesis.json"
        self.map_path.parent.mkdir(parents=True)
        self.book_path.parent.mkdir(parents=True)
        self._write_map()
        self._write_book([{"text": "Other", "beforeVerse": 2}, {"text": "Heading", "beforeVerse": 3}])

    def _write_map(self, *, source_text: str = "Heading", old_verse: int = 2) -> None:
        self.map_path.write_text(json.dumps({
            "schemaVersion": 1,
            "description": "Fixture.",
            "editions": [{
                "language": "xx",
                "editionId": "historical",
                "bookCode": "GEN",
                "collection": "old_testament",
                "bookId": "genesis",
                "pinnedSource": {
                    "title": "Pinned source",
                    "url": "https://example.invalid/source.zip",
                    "artifactSha256": "A" * 64,
                    "sourceDate": "2026-09-26",
                },
                "relocations": [{
                    "sourceChapter": 1,
                    "sourceBeforeVerse": old_verse,
                    "sourceText": source_text,
                    "joinWith": " ",
                    "targets": [{"chapter": 1, "beforeVerse": 4, "text": source_text}],
                }],
            }],
        }), encoding="utf-8")

    def _write_book(self, headings: list[dict], *, book_id: str = "genesis", story_id: str = "genesis-1") -> None:
        self.book_path.write_text(json.dumps({
            "id": book_id,
            "stories": [{"id": story_id, "headings": headings}],
        }), encoding="utf-8")

    def _audit(self) -> dict:
        return auditor.audit(self.root, self.map_path)

    def test_exact_unique_relocation_proposes_review_only_patch_with_preconditions(self):
        result = self._audit()
        self.assertTrue(result["readOnly"])
        self.assertTrue(result["proposalRequiresSourceReview"])
        self.assertEqual(result["rows"][0]["status"], "exact_text_unique_relocation_review_required")
        self.assertEqual([op["op"] for op in result["proposedJsonPatch"]],
                         ["test", "test", "test", "replace", "replace"])
        self.assertEqual(result["proposedJsonPatch"][-1]["value"], 3)
        self.assertEqual(len(result["mapSha256"]), 64)
        self.assertEqual(len(next(iter(result["baseBookSha256"].values()))), 64)
        self.assertEqual(self._audit(), result)

    def test_bound_exact_anchor_needs_no_patch(self):
        self._write_book([{"text": "Heading", "beforeVerse": 2}])
        result = self._audit()
        self.assertEqual(result["rows"][0]["status"], "bound")
        self.assertEqual(result["proposedJsonPatch"], [])

    def test_duplicate_exact_text_is_ambiguous(self):
        self._write_book([{"text": "Heading", "beforeVerse": 3},
                          {"text": "Heading", "beforeVerse": 4}])
        result = self._audit()
        self.assertEqual(result["rows"][0]["status"], "ambiguous_duplicate_text")
        self.assertEqual(result["proposedJsonPatch"], [])

    def test_multiple_headings_at_old_anchor_is_ambiguous(self):
        self._write_book([{"text": "Heading", "beforeVerse": 2},
                          {"text": "Other", "beforeVerse": 2}])
        self.assertEqual(self._audit()["rows"][0]["status"], "ambiguous_old_anchor")

    def test_changed_text_at_old_anchor_is_not_guessed(self):
        self._write_book([{"text": "Replacement", "beforeVerse": 2}])
        result = self._audit()
        self.assertEqual(result["rows"][0]["status"], "old_anchor_text_changed_or_replaced")
        self.assertEqual(result["proposedJsonPatch"], [])

    def test_removed_or_rewritten_heading_is_not_guessed(self):
        self._write_book([])
        result = self._audit()
        self.assertEqual(result["rows"][0]["status"], "source_heading_removed_or_rewritten")
        self.assertEqual(result["proposedJsonPatch"], [])

    def test_book_identity_mismatch_fails_closed(self):
        self._write_book([], book_id="exodus")
        with self.assertRaisesRegex(auditor.RebaseAuditError, "identity mismatch"):
            self._audit()

    def test_story_identity_mismatch_fails_closed(self):
        self._write_book([], story_id="exodus-1")
        with self.assertRaisesRegex(auditor.RebaseAuditError, "story identity mismatch"):
            self._audit()

    def test_malformed_map_fails_closed(self):
        self._write_map(source_text="Heading ")
        with self.assertRaisesRegex(auditor.RebaseAuditError, "trimmed string"):
            self._audit()

    def test_book_code_mismatch_fails_closed(self):
        document = json.loads(self.map_path.read_text(encoding="utf-8"))
        document["editions"][0]["bookCode"] = "EXO"
        self.map_path.write_text(json.dumps(document), encoding="utf-8")
        with self.assertRaisesRegex(auditor.RebaseAuditError, "book code/identity mismatch"):
            self._audit()

    def test_packaged_corpus_audit_does_not_mutate_inputs(self):
        map_before = auditor.MAP_PATH.read_bytes()
        result = auditor.audit()
        self.assertEqual(auditor.MAP_PATH.read_bytes(), map_before)
        self.assertEqual(sum(result["summary"].values()), len(result["rows"]))


if __name__ == "__main__":
    unittest.main()
