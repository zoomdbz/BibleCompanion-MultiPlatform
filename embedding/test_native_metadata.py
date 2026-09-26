"""No model inference required: vector/metadata navigation integrity checks."""

from __future__ import annotations

import json
import re
import unittest

from native_versification import native_anchor_ids, russian_psalm_verse
from remap_native_metadata import OUTPUT, PACKAGED, source_refs, validate_vector_count

class NativeMetadataTests(unittest.TestCase):
    def test_russian_rule_matches_scripture_migration_boundaries(self):
        cases = {
            (1, 1): (1, 1), (3, 1): (3, 2),
            (9, 1): (9, 2), (10, 1): (9, 22), (11, 1): (10, 1),
            (51, 1): (50, 3), (114, 1): (113, 1),
            (115, 1): (113, 9), (116, 9): (114, 9),
            (116, 10): (115, 1), (117, 1): (116, 1),
            (147, 11): (146, 11), (147, 12): (147, 1),
            (148, 1): (148, 1), (150, 6): (150, 6),
        }
        for old, native in cases.items():
            self.assertEqual(russian_psalm_verse(*old), native)

    def test_chapter_boundaries(self):
        self.assertEqual(native_anchor_ids("psalms-23", "ru"), ("psalms-22",))
        self.assertEqual(native_anchor_ids("psalms-116", "ru"), ("psalms-114", "psalms-115"))
        self.assertEqual(native_anchor_ids("psalms-147", "ru"), ("psalms-146", "psalms-147"))
        self.assertEqual(native_anchor_ids("malachi-4", "de"), ("malachi-3",))

    def test_packaged_metadata_and_vectors_keep_exact_row_alignment(self):
        for lang in ("ru", "de"):
            with self.subTest(lang=lang):
                filename = f"metadata_{lang}.json"
                self.assertEqual((OUTPUT / filename).read_bytes(), (PACKAGED / filename).read_bytes())
                rows = json.loads((OUTPUT / filename).read_text(encoding="utf-8"))
                validate_vector_count(lang, len(rows))

    def test_russian_psalm_rows_are_native_or_disabled(self):
        rows = json.loads((OUTPUT / "metadata_ru.json").read_text(encoding="utf-8"))
        refs = source_refs("ru", "psalms")
        disabled = []
        for row in rows:
            if row["b"] != "psalms":
                continue
            self.assertEqual(row.get("n"), 1)
            if not row["s"]:
                disabled.append(row)
                self.assertIn(row["t"], ("story", "takeaway"))
                self.assertEqual(row["r"], [])
                continue
            self.assertIn(row["s"], refs)
            self.assertEqual(row["r"], refs[row["s"]])
            if row["v"]:
                chapter, verse = map(int, row["v"].split(":"))
                self.assertEqual(row["s"], f"psalms-{chapter}")
                match = re.fullmatch(r"Psalms (\d+):(\d+)-(\d+)", row["r"][0])
                self.assertIsNotNone(match)
                self.assertLessEqual(int(match[2]), verse)
                self.assertLessEqual(verse, int(match[3]))
        self.assertEqual(len(disabled), 4)

    def test_german_malachi_never_targets_missing_chapter(self):
        rows = json.loads((OUTPUT / "metadata_de.json").read_text(encoding="utf-8"))
        refs = source_refs("de", "malachi")
        for row in rows:
            if row["b"] == "malachi":
                self.assertIn(row["s"], refs)
                self.assertTrue(all(ref.startswith("Malachi " + row["s"].split("-")[-1] + ":") for ref in row["r"]))


if __name__ == "__main__":
    unittest.main()
