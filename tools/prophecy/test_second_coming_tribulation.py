"""Contracts for the localized tribulation, wrath, and escape explanation.

These check asset preservation, citation parity, and dropdown structure.
They do not replace review of the theological argument or device rendering.
"""

from __future__ import annotations

import re
import subprocess
import unittest
from pathlib import Path

from audit_note_references import citations


ROOT = Path(__file__).resolve().parents[2]
NOTES = ROOT / "shared/assets/notes"
LANGUAGES = (
    "en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko",
    "zh-Hans", "zh-Hant", "ar", "hi",
)
PAGE = "second_coming_rapture.md"
BASELINE = "0d6328bf355be426bdb8d5a4acf9af8bbc81f356"


def split_target(text: str) -> tuple[str, str, str]:
    """The fourth H2 is the existing tribulation-versus-wrath section."""
    headings = list(re.finditer(r"(?m)^## [^\n]+\n", text))
    if len(headings) < 5:
        raise ValueError("Expected the existing Second Coming study sections")
    start, end = headings[3].end(), headings[4].start()
    return text[:start], text[start:end], text[end:]


def read(language: str) -> str:
    return (NOTES / language / PAGE).read_text(encoding="utf-8-sig")


def addresses(language: str, text: str) -> list[tuple]:
    return [
        (item.book, item.chapter, item.verse, item.end_chapter, item.end_verse)
        for item in citations(language, text)
    ]


class SecondComingTribulationTests(unittest.TestCase):
    def test_every_locale_has_four_nonempty_child_dropdowns(self):
        for language in LANGUAGES:
            with self.subTest(language=language):
                _, section, _ = split_target(read(language))
                children = re.split(r"(?m)^### [^\n]+\n", section)
                self.assertEqual(5, len(children))
                self.assertTrue(all(child.strip() for child in children))
                self.assertFalse(re.search(r"(?m)^#{4,6} ", section))
                self.assertEqual(4, len(re.findall(r"(?m)^- ", children[3])))
                self.assertEqual(["1", "2", "3"], re.findall(r"(?m)^([1-3])\. ", children[4]))

    def test_localized_citations_match_english_in_order(self):
        _, english, _ = split_target(read("en"))
        expected = addresses("en", english)
        self.assertGreater(len(expected), 30)
        for language in LANGUAGES:
            with self.subTest(language=language):
                _, section, _ = split_target(read(language))
                self.assertEqual(expected, addresses(language, section))

    def test_escape_birth_pains_and_flight_use_distinct_passages(self):
        for language in LANGUAGES:
            with self.subTest(language=language):
                _, section, _ = split_target(read(language))
                promise_section = re.split(r"(?m)^### [^\n]+\n", section)[3]
                bullets = re.split(r"(?m)^- ", promise_section)[1:]
                self.assertEqual(4, len(bullets))
                expected_starts = (
                    [("revelation", 3, 10, 3, 10)],
                    [("luke", 21, 34, 21, 36)],
                    [("matthew", 24, 4, 24, 8), ("mark", 13, 5, 13, 8)],
                    [("matthew", 24, 15, 24, 22), ("mark", 13, 14, 13, 20),
                     ("luke", 21, 20, 21, 24)],
                )
                for bullet, expected in zip(bullets, expected_starts):
                    self.assertEqual(expected, addresses(language, bullet)[:len(expected)])
                self.assertIn(("matthew", 24, 20, 24, 20), addresses(language, bullets[3]))
                self.assertIn(("mark", 13, 18, 13, 18), addresses(language, bullets[3]))

    def test_other_study_sections_and_scripture_quotes_remain_unchanged(self):
        available = subprocess.run(
            ["git", "cat-file", "-e", BASELINE + "^{commit}"],
            cwd=ROOT, capture_output=True,
        ).returncode == 0
        if not available:
            self.skipTest("Preservation baseline absent in shallow checkout")
        for language in LANGUAGES:
            with self.subTest(language=language):
                relative = f"shared/assets/notes/{language}/{PAGE}"
                original = subprocess.run(
                    ["git", "show", BASELINE + ":" + relative],
                    cwd=ROOT, capture_output=True, check=True,
                ).stdout.decode("utf-8-sig").replace("\r\n", "\n")
                updated = read(language)
                original_before, _, original_after = split_target(original)
                updated_before, _, updated_after = split_target(updated)
                self.assertEqual(original_before, updated_before)
                self.assertEqual(original_after, updated_after)
                self.assertEqual(
                    re.findall(r"(?m)^>.*$", original),
                    re.findall(r"(?m)^>.*$", updated),
                )


if __name__ == "__main__":
    unittest.main()
