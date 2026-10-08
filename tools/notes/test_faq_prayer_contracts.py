"""Preservation and citation contracts for the localized answer to FAQ 5.

These source checks protect the other questions and existing quotations. They
check the new references against bundled Scripture; they do not replace human
review of meaning or native device rendering.
"""

from __future__ import annotations

import re
import subprocess
import sys
import unittest
from functools import lru_cache
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/prophecy"))
from audit_note_references import citations, validate  # noqa: E402


LANGUAGES = (
    "en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko",
    "zh-Hans", "zh-Hant", "ar", "hi",
)
BASELINE = "0d6328bf355be426bdb8d5a4acf9af8bbc81f356"
QUESTION = re.compile(r"(?m)^### \*\*([56])\.[^\n]*\n")
QUOTATIONS = re.compile(
    r'"[^"\n]+"|\u201e[^\u201c\n]+\u201c|\u201c[^\u201d\n]+\u201d'
    r'|\u00ab[^\u00bb\n]+\u00bb|\u300c[^\u300d\n]+\u300d'
    r'|\u300e[^\u300f\n]+\u300f'
)
BULLET_LABEL = re.compile(r"(?m)^- \*\*([^*\n]+)\*\*")
NUMBERED_HEADING = re.compile(r"(?m)^\*\*[1-5][)\uff09][^\n]+\*\*\s*$")
ARABIC_NUMERIC_JOHN = "1 \u064a\u0648\u062d\u0646\u0627"
ARABIC_FIRST_JOHN = "\u064a\u0648\u062d\u0646\u0627 \u0627\u0644\u0623\u0648\u0644\u0649"


def split_question(text: str) -> tuple[str, str, str]:
    boundaries = list(QUESTION.finditer(text))
    if [match[1] for match in boundaries] != ["5", "6"]:
        raise ValueError("Expected FAQ questions 5 and 6")
    start, end = boundaries[0].end(), boundaries[1].start()
    return text[:start], text[start:end], text[end:]


def read(language: str) -> str:
    return (ROOT / f"shared/assets/notes/{language}/faqs.md").read_text(
        encoding="utf-8-sig"
    )


@lru_cache(maxsize=None)
def original(language: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{BASELINE}:shared/assets/notes/{language}/faqs.md"],
        cwd=ROOT, capture_output=True, check=True,
    )
    return result.stdout.decode("utf-8-sig").replace("\r\n", "\n")


def added_references(language: str, section: str):
    # The sixteen original bullet labels retain their native edition numbering.
    # Exclude those labels before checking the newly added citations for parity.
    return citations(language, BULLET_LABEL.sub("- ", section))


def address(reference) -> tuple:
    return (
        reference.book, reference.chapter, reference.verse,
        reference.end_chapter, reference.end_verse,
    )


class FaqPrayerContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.has_baseline = subprocess.run(
            ["git", "cat-file", "-e", BASELINE + "^{commit}"],
            cwd=ROOT, capture_output=True,
        ).returncode == 0

    def test_question_title_and_all_other_questions_remain_unchanged(self):
        if not self.has_baseline:
            self.skipTest("Preservation baseline absent in shallow checkout")
        for language in LANGUAGES:
            with self.subTest(language=language):
                before, _, after = split_question(original(language))
                actual_before, _, actual_after = split_question(read(language))
                self.assertEqual(before, actual_before)
                # apply_patch can add a final newline to a file that lacked it.
                # Ignore only EOF newlines, not whitespace inside another FAQ.
                self.assertEqual(after.rstrip("\n"), actual_after.rstrip("\n"))

    def test_existing_scripture_quotes_and_native_reference_labels_are_preserved(self):
        if not self.has_baseline:
            self.skipTest("Preservation baseline absent in shallow checkout")
        for language in LANGUAGES:
            with self.subTest(language=language):
                _, before, _ = split_question(original(language))
                _, after, _ = split_question(read(language))
                self.assertGreaterEqual(len(QUOTATIONS.findall(before)), 15)
                self.assertEqual(QUOTATIONS.findall(before), QUOTATIONS.findall(after))
                expected_labels = BULLET_LABEL.findall(before)
                if language == "ar":
                    # The old numeric label can match John's Gospel in the app.
                    # Use the registered epistle alias; keep its verse numbers.
                    expected_labels = [
                        label.replace(ARABIC_NUMERIC_JOHN, ARABIC_FIRST_JOHN, 1)
                        for label in expected_labels
                    ]
                self.assertEqual(expected_labels, BULLET_LABEL.findall(after))
                self.assertEqual(
                    NUMBERED_HEADING.findall(before), NUMBERED_HEADING.findall(after)
                )

    def test_each_locale_keeps_five_support_groups_and_adds_practical_prayer(self):
        for language in LANGUAGES:
            with self.subTest(language=language):
                _, section, _ = split_question(read(language))
                self.assertEqual(5, len(NUMBERED_HEADING.findall(section)))
                self.assertEqual(16, len(BULLET_LABEL.findall(section)))
                standalone = re.findall(r"(?m)^\*\*[^\n]+\*\*\s*$", section)
                self.assertEqual(6, len(standalone))
                self.assertFalse(re.search(r"(?m)^#{1,6} ", section))

    def test_arabic_first_john_uses_the_epistle_alias(self):
        _, section, _ = split_question(read("ar"))
        label = BULLET_LABEL.findall(section)[3]
        self.assertEqual(
            [("1_john", 5, 14, 5, 15)],
            [address(item) for item in citations("ar", label)],
        )

    def test_new_citations_match_english_in_order_and_exist_in_bundled_scripture(self):
        _, english, _ = split_question(read("en"))
        expected = [address(item) for item in added_references("en", english)]
        self.assertEqual(19, len(expected))
        self.assertIn(("john", 11, 33, 11, 35), expected)
        self.assertIn(("romans", 8, 28, 8, 29), expected)
        self.assertIn(("matthew", 6, 9, 6, 13), expected)
        for language in LANGUAGES:
            with self.subTest(language=language):
                _, section, _ = split_question(read(language))
                actual = added_references(language, section)
                self.assertEqual(expected, [address(item) for item in actual])
                for reference in actual:
                    self.assertEqual([], validate(language, reference), reference.display)


if __name__ == "__main__":
    unittest.main()
