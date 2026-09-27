#!/usr/bin/env python3
"""Tests for explicit per-edition KJV heading lookup tables."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from import_kjv_usfm import UsfmError, resolved_heading_table


class KjvHeadingTableTests(unittest.TestCase):
    def fixture(self, headings: dict[int, list[dict]], chapters: list[dict]) -> Path:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        path = root / "shared/assets/books/new_testament/en/revelation.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps({
            "stories": [
                {"id": f"revelation-{chapter}", "headings": rows}
                for chapter, rows in sorted(headings.items())
            ]
        }), encoding="utf-8")
        return root

    def test_cross_chapter_mapping_stacks_at_native_verse(self) -> None:
        chapters = [
            {"number": 12, "verses": [{"chapter": 12, "verse": 17, "text": "a"}]},
            {"number": 13, "verses": [{"chapter": 13, "verse": 1, "text": "b"}]},
        ]
        root = self.fixture({
            12: [{"beforeVerse": 18, "text": "Moved"}],
            13: [{"beforeVerse": 1, "text": "Already there"}],
        }, chapters)
        reference_map = {"books": [{"bookId": "revelation", "mappings": [
            {"sourceChapter": 12, "sourceVerse": 18, "targetChapter": 13, "targetVerse": 1},
            {"sourceChapter": 13, "sourceVerse": 1, "targetChapter": 13, "targetVerse": 1},
        ]}]}
        table = resolved_heading_table(
            root, "new_testament", "revelation", chapters, reference_map,
        )
        self.assertEqual(table[12], [])
        self.assertEqual(table[13], [
            {"beforeVerse": 1, "text": "Moved\nAlready there"},
        ])

    def test_heading_must_land_on_a_native_unit_start(self) -> None:
        chapters = [{"number": 12, "verses": [{"chapter": 12, "verse": 17, "text": "a"}]}]
        root = self.fixture({12: [{"beforeVerse": 18, "text": "Missing"}]}, chapters)
        with self.assertRaisesRegex(UsfmError, "outside displayed KJV"):
            resolved_heading_table(
                root, "new_testament", "revelation", chapters, {"books": []},
            )


if __name__ == "__main__":
    unittest.main()
