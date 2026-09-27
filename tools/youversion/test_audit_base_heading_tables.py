from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

import audit_base_heading_tables as audit


class BaseHeadingAuditTests(unittest.TestCase):
    def write_book(self, root: Path, payload: dict, language: str = "en") -> Path:
        path = root / "shared/assets/books/old_testament" / language / "sample.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        return path

    @staticmethod
    def book(bullets: list[str], headings: list[dict] | None = None) -> dict:
        story = {
            "id": "sample-1",
            "title": "Sample 1",
            "refs": ["Sample 1:1-4"],
            "summaryBullets": bullets,
            "crossRefs": [],
            "translationNotes": [],
            "keyTakeaway": "Sample",
        }
        if headings is not None:
            story["headings"] = headings
        return {"id": "sample", "title": "Sample", "stories": [story], "intro": "Sample"}

    def test_native_ranges_and_heading_starts_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_book(
                root,
                self.book(
                    ["First unit (1:1-2).", "Second unit (1:3).", "Third unit (1:4)."],
                    [
                        {"beforeVerse": 1, "text": "First"},
                        {"beforeVerse": 3, "text": "Second"},
                    ],
                ),
            )
            result = audit.audit_base_books(root)
            self.assertTrue(result.ok, result.errors)
            self.assertEqual(result.counts.native_units, 3)
            self.assertEqual(result.counts.headings, 2)

    def test_full_width_japanese_marker_parses(self) -> None:
        parsed = audit.parse_trailing_marker("本文（3：4－6）。", 7)
        self.assertEqual(parsed.kind, "chapter_verse")
        self.assertEqual(parsed.unit, audit.NativeUnit(3, 4, 6, 7))

    def test_source_preface_verse_zero_is_a_nonoverlapping_native_unit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_book(root, self.book(["Preface (6:0).", "First verse (6:1)."]))
            result = audit.audit_base_books(root)
            self.assertTrue(result.ok, result.errors)

    def test_overlap_unsorted_duplicate_blank_and_interior_heading_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_book(
                root,
                self.book(
                    ["First unit (1:1-3).", "Overlapping unit (1:3-4)."],
                    [
                        {"beforeVerse": 2, "text": " "},
                        {"beforeVerse": 1, "text": "First"},
                        {"beforeVerse": 1, "text": "Duplicate"},
                    ],
                ),
            )
            errors = "\n".join(audit.audit_base_books(root).errors)
            self.assertIn("overlapping native units", errors)
            self.assertIn("heading anchors are not sorted", errors)
            self.assertIn("duplicate heading anchors", errors)
            self.assertIn("text must be nonblank", errors)
            self.assertIn("beforeVerse 2 is not a native-unit start", errors)

    def test_addressable_story_rejects_one_missing_trailing_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_book(root, self.book(["First (1:1).", "Missing marker"]))
            errors = "\n".join(audit.audit_base_books(root).errors)
            self.assertIn("no trailing marker in an addressable story", errors)

    def test_ledger_rejects_unapplied_old_state(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_book(
                root,
                self.book(
                    ["Combined source text (1:1).", "Second (1:2)."],
                    [{"beforeVerse": 2, "text": "Reviewed heading"}],
                ),
                language="ja",
            )
            ledger = {
                "schemaVersion": 1,
                "language": "ja",
                "sourceEdition": {"code": "JCB", "bibleComVersionId": 83},
                "reviewedRepairs": [
                    {
                        "id": "sample",
                        "asset": "old_testament/ja/sample.json",
                        "storyId": "sample-1",
                        "sourceUrls": ["https://www.bible.com/bible/83/GEN.1.JCB"],
                        "nativeUnits": [
                            {
                                "old": {"chapter": 1, "start": 1, "end": 1},
                                "new": {"chapter": 1, "start": 1, "end": 2},
                            }
                        ],
                        "headingMoves": [
                            {
                                "text": "Reviewed heading",
                                "old": {"storyId": "sample-1", "beforeVerse": 2},
                                "new": {"storyId": "sample-1", "beforeVerse": 1},
                            }
                        ],
                    }
                ],
            }
            ledger_path = root / "ledger.json"
            ledger_path.write_text(json.dumps(ledger, ensure_ascii=False), encoding="utf-8")
            result = audit.AuditResult()
            audit.audit_jcb_repair_ledger(root, ledger_path, result)
            errors = "\n".join(result.errors)
            self.assertIn("still has reviewed old anchor", errors)
            self.assertIn("obsolete native unit", errors)
            self.assertIn("expected heading", errors)

    def test_ledger_accepts_heading_only_repair(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_book(
                root,
                self.book(
                    ["Combined source text (1:1-2)."],
                    [{"beforeVerse": 1, "text": "Reviewed heading"}],
                ),
                language="ja",
            )
            ledger = {
                "schemaVersion": 1,
                "language": "ja",
                "sourceEdition": {"code": "JCB", "bibleComVersionId": 83},
                "reviewedRepairs": [
                    {
                        "id": "sample",
                        "asset": "old_testament/ja/sample.json",
                        "storyId": "sample-1",
                        "sourceUrls": ["https://www.bible.com/bible/83/GEN.1.JCB"],
                        "nativeUnits": [],
                        "headingMoves": [
                            {
                                "text": "Reviewed heading",
                                "old": {"storyId": "sample-1", "beforeVerse": 2},
                                "new": {"storyId": "sample-1", "beforeVerse": 1},
                            }
                        ],
                    }
                ],
            }
            ledger_path = root / "ledger.json"
            ledger_path.write_text(json.dumps(ledger, ensure_ascii=False), encoding="utf-8")
            result = audit.AuditResult()
            audit.audit_jcb_repair_ledger(root, ledger_path, result)
            self.assertTrue(result.ok, result.errors)
            self.assertEqual(result.counts.jcb_repairs, 0)
            self.assertEqual(result.counts.jcb_heading_moves, 1)
            self.assertEqual(result.counts.jcb_chapter_records, 1)

    def test_ledger_rejects_empty_repair(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_book(root, self.book(["Source text (1:1)."]), language="ja")
            ledger = {
                "schemaVersion": 1,
                "language": "ja",
                "sourceEdition": {"code": "JCB", "bibleComVersionId": 83},
                "reviewedRepairs": [
                    {
                        "id": "sample",
                        "asset": "old_testament/ja/sample.json",
                        "storyId": "sample-1",
                        "sourceUrls": ["https://www.bible.com/bible/83/GEN.1.JCB"],
                        "nativeUnits": [],
                        "headingMoves": [],
                    }
                ],
            }
            ledger_path = root / "ledger.json"
            ledger_path.write_text(json.dumps(ledger), encoding="utf-8")
            result = audit.AuditResult()
            audit.audit_jcb_repair_ledger(root, ledger_path, result)
            self.assertIn(
                "at least one nativeUnits or headingMoves item is required",
                "\n".join(result.errors),
            )

    def test_checked_in_jcb_ledger_matches_current_assets(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        result = audit.AuditResult()
        audit.audit_jcb_repair_ledger(repo_root, audit.default_ledger_path(), result)
        self.assertTrue(result.ok, result.errors)
        self.assertEqual(result.counts.jcb_repairs, 17)
        self.assertEqual(result.counts.jcb_heading_moves, 10)
        self.assertEqual(result.counts.jcb_chapter_records, 12)


if __name__ == "__main__":
    unittest.main()
