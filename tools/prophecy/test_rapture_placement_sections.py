"""Regression contracts for the rapture-placement dropdown hierarchy.

Run: python -m unittest tools.prophecy.test_rapture_placement_sections
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NOTES = ROOT / "shared/assets/notes"
LANGUAGES = (
    "en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko",
    "zh-Hans", "zh-Hant", "ar", "hi",
)
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
NUMBERED_PLACEMENT = re.compile(r"^([1-4])\.\s+\S")


def headings(lines: list[str]) -> list[tuple[int, int, str]]:
    result = []
    for line_number, line in enumerate(lines):
        match = HEADING.match(line)
        if match:
            result.append((line_number, len(match[1]), match[2]))
    return result


def has_prose(lines: list[str]) -> bool:
    return any(line.strip() and not HEADING.match(line) for line in lines)


class RapturePlacementSectionTests(unittest.TestCase):
    def test_placement_section_has_four_nested_dropdowns(self):
        for language in LANGUAGES:
            with self.subTest(language=language):
                path = NOTES / language / "revelation_timeline.md"
                lines = path.read_text(encoding="utf-8-sig").splitlines()
                outline = headings(lines)
                numbered = [
                    (index, line_number, depth, title, NUMBERED_PLACEMENT.match(title))
                    for index, (line_number, depth, title) in enumerate(outline)
                    if NUMBERED_PLACEMENT.match(title)
                ]

                self.assertEqual(
                    ["1", "2", "3", "4"],
                    [match[1] for _, _, _, _, match in numbered],
                    "Expected exactly four numbered placement headings in order",
                )
                self.assertEqual(
                    [3, 3, 3, 3],
                    [depth for _, _, depth, _, _ in numbered],
                    "Numbered placements must be H3 children, never H2 siblings",
                )

                first_outline_index = numbered[0][0]
                parent_line, parent_depth, _ = outline[first_outline_index - 1]
                self.assertEqual(2, parent_depth, "Placement children must follow an H2 parent")
                self.assertTrue(
                    has_prose(lines[parent_line + 1:numbered[0][1]]),
                    "Placement parent must retain its introduction before child 1",
                )

                following_h2_indexes = [
                    index
                    for index in range(first_outline_index, len(outline))
                    if outline[index][1] == 2
                ]
                self.assertTrue(
                    following_h2_indexes,
                    "The placement children must be followed by the H2 comparison section",
                )
                following_h2_index = following_h2_indexes[0]
                section_outline = outline[first_outline_index:following_h2_index]
                self.assertEqual(
                    [title for _, depth, title in section_outline if depth == 3],
                    [title for _, _, _, title, _ in numbered],
                    "The parent must contain only the four numbered H3 children",
                )
                self.assertEqual(
                    2,
                    outline[following_h2_index][1],
                    "The comparison section following the placements must remain H2",
                )
                comparison_line = outline[following_h2_index][0]
                next_h2_line = next(
                    (line_number for line_number, depth, _ in outline[following_h2_index + 1:]
                     if depth == 2),
                    len(lines),
                )
                self.assertTrue(
                    any(line.lstrip().startswith("|---") for line in lines[comparison_line:next_h2_line]),
                    "The following H2 must remain the placement-comparison table",
                )

                support_counts = []
                for position, (_, child_line, _, _, _) in enumerate(numbered):
                    next_line = (
                        numbered[position + 1][1]
                        if position + 1 < len(numbered)
                        else outline[following_h2_index][0]
                    )
                    child_outline = [
                        item for item in section_outline
                        if child_line < item[0] < next_line
                    ]
                    first_nested_line = child_outline[0][0] if child_outline else next_line
                    self.assertTrue(
                        has_prose(lines[child_line + 1:first_nested_line]),
                        f"Placement child {position + 1} must have nonempty content",
                    )
                    self.assertTrue(
                        all(depth == 4 for _, depth, _ in child_outline),
                        "Supporting headings must be H4 descendants",
                    )
                    support_counts.append(len(child_outline))

                self.assertEqual([3, 2, 2, 2], support_counts)


if __name__ == "__main__":
    unittest.main()
