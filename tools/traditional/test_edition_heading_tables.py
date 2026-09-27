#!/usr/bin/env python3
"""Regression test for explicit alternate-edition heading tables."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from audit_edition_heading_tables import (
    EDITIONS, HeadingAuditError, audit_all, base_stories, require_reviewed_targets,
)
from heading_maps import EditionHeadingMap, HeadingRelocation, HeadingTarget
from validate_traditional_editions import validate_localized_headings


class EditionHeadingTableTests(unittest.TestCase):
    @staticmethod
    def reviewed(source: str, first: str, second: str) -> EditionHeadingMap:
        return EditionHeadingMap(
            "fr", "lsg1910", "1TH", "new_testament", "1_thessalonians",
            "Pinned source", "https://example.invalid", "A" * 64, "2026-09-26",
            (HeadingRelocation(
                1, 1, source, " ",
                (HeadingTarget(1, 1, first), HeadingTarget(1, 2, second)),
            ),),
        )

    def test_space_composites_preserve_french_and_chinese_text(self) -> None:
        for source, first, second in (
            ("Adresse et salutation Succès de l’Évangile à Thessalonique, et fidélité de son Église",
             "Adresse et salutation", "Succès de l’Évangile à Thessalonique, et fidélité de son Église"),
            ("问候 帖撒罗尼迦信徒的信心和榜样", "问候", "帖撒罗尼迦信徒的信心和榜样"),
            ("問候 帖撒羅尼迦信徒的信心和榜樣", "問候", "帖撒羅尼迦信徒的信心和榜樣"),
        ):
            with self.subTest(source=source):
                reviewed = self.reviewed(source, first, second)
                base = {1: {"headings": [{"beforeVerse": 1, "text": source}]}}
                edition = {1: {"headings": [
                    {"beforeVerse": 1, "text": first},
                    {"beforeVerse": 2, "text": second},
                ]}}
                validate_localized_headings(base, edition, reviewed, "synthetic")

    def test_reviewed_target_can_share_anchor_with_another_heading(self) -> None:
        reviewed = self.reviewed("Greeting Faith", "Greeting", "Faith")
        base = {1: {"headings": [
            {"beforeVerse": 1, "text": "Greeting Faith"},
            {"beforeVerse": 2, "text": "Other"},
        ]}}
        edition = {1: {"headings": [
            {"beforeVerse": 1, "text": "Greeting"},
            {"beforeVerse": 2, "text": "Faith\nOther"},
        ]}}
        validate_localized_headings(base, edition, reviewed, "synthetic")

    def test_wrong_but_valid_native_anchor_fails(self) -> None:
        reviewed = self.reviewed("Greeting Faith", "Greeting", "Faith")
        base = {1: {"headings": [{"beforeVerse": 1, "text": "Greeting Faith"}]}}
        edition = {1: {"headings": [
            {"beforeVerse": 1, "text": "Greeting"},
            {"beforeVerse": 3, "text": "Faith"},
        ]}}
        with self.assertRaisesRegex(HeadingAuditError, "target missing or misplaced"):
            validate_localized_headings(base, edition, reviewed, "synthetic")
        with self.assertRaisesRegex(HeadingAuditError, "target missing or misplaced"):
            require_reviewed_targets(edition, reviewed, "synthetic")

    def test_duplicate_reviewed_target_fails_text_preservation(self) -> None:
        reviewed = self.reviewed("Greeting Faith", "Greeting", "Faith")
        base = {1: {"headings": [{"beforeVerse": 1, "text": "Greeting Faith"}]}}
        edition = {1: {"headings": [
            {"beforeVerse": 1, "text": "Greeting"},
            {"beforeVerse": 2, "text": "Faith\nFaith"},
        ]}}
        with self.assertRaisesRegex(AssertionError, "Edition heading text differs"):
            validate_localized_headings(base, edition, reviewed, "synthetic")

    def test_single_story_historical_suffix_is_display_chapter_one(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "song_of_three.json"
            path.write_text(
                json.dumps({"stories": [{"id": "song_of_three-3", "verses": []}]}),
                encoding="utf-8",
            )
            chapters = base_stories(path)
        self.assertEqual(set(chapters), {1})
        self.assertEqual(chapters[1]["id"], "song_of_three-3")

    def test_every_alternate_edition_has_complete_native_heading_tables(self) -> None:
        report = audit_all()
        self.assertEqual(len(report), len(EDITIONS))
        self.assertEqual(sum(int(row["books"]) for row in report), 819)
        self.assertTrue(all(int(row["chapters"]) > 0 for row in report))
        self.assertTrue(all(int(row["headingLines"]) > 0 for row in report))


if __name__ == "__main__":
    unittest.main()
