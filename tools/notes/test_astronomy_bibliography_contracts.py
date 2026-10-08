#!/usr/bin/env python3
"""Preserve the exact astronomy-source move across every shipped locale."""
from __future__ import annotations

from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
BASELINE = "a684cd8e4bd9de368fcbab523cb7a66a613ae1ac"
LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")
REQUIRED_SOURCES = (
    "https://penelope.uchicago.edu/josephus/ant-17.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE-0099-0000.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE0001-0100.html",
    "https://www.nature.com/articles/306743a0",
    "https://articles.adsabs.harvard.edu/pdf/1990QJRAS..31...53S",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE1401-1500.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE1901-2000.html",
    "https://eclipse.gsfc.nasa.gov/SEcat5/SE2001-2100.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE2001-2100.html",
    "https://www.hebcal.com/home/1663/zmanim-halachic-times-api",
    "https://www.hebcal.com/home/219/hebrew-date-converter-rest-api",
)


def normalized(text: str) -> str:
    return text.removeprefix("\ufeff").replace("\r\n", "\n")


def baseline_text(path: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{BASELINE}:{path}"],
        cwd=ROOT,
        check=True,
        capture_output=True,
    )
    return normalized(result.stdout.decode("utf-8-sig"))


def current_text(path: str) -> str:
    return normalized((ROOT / path).read_text(encoding="utf-8-sig"))


def source_paragraph(text: str) -> str:
    matches = [line for line in text.splitlines() if all(source in line for source in REQUIRED_SOURCES)]
    if len(matches) != 1:
        raise AssertionError(f"expected one source paragraph, found {len(matches)}")
    return matches[0]


class AstronomyBibliographyContracts(unittest.TestCase):
    def test_only_exact_source_paragraph_moved_to_locale_titled_bibliography_section(self) -> None:
        for language in LANGUAGES:
            with self.subTest(language=language):
                astronomy_path = f"shared/assets/notes/{language}/astronomical_signs.md"
                bibliography_path = f"shared/assets/notes/{language}/bibliography.md"
                original_astronomy = baseline_text(astronomy_path)
                original_bibliography = baseline_text(bibliography_path)
                paragraph = source_paragraph(original_astronomy)
                title = next(
                    (line[2:] for line in original_astronomy.splitlines() if line.startswith("# ")),
                    None,
                )
                self.assertIsNotNone(title)
                removal = f"{paragraph}\n"
                self.assertEqual(original_astronomy.count(removal), 1)
                expected_astronomy = original_astronomy.replace(removal, "", 1)
                expected_bibliography = original_bibliography.rstrip("\n")
                expected_bibliography += f"\n\n## {title}\n\n{paragraph}\n"
                self.assertEqual(
                    current_text(astronomy_path).rstrip("\n"),
                    expected_astronomy.rstrip("\n"),
                    "astronomical note changed beyond removing its source paragraph",
                )
                self.assertEqual(
                    current_text(bibliography_path).rstrip("\n"),
                    expected_bibliography.rstrip("\n"),
                    "bibliography must preserve its old text and append the exact moved paragraph",
                )


if __name__ == "__main__":
    unittest.main()
