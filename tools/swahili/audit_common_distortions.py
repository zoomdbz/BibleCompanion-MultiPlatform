#!/usr/bin/env python3
"""Compare displayed Common Distortions quotations with each bundled Bible."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
sys.path.insert(0, str(ROOT / "tools/prophecy"))
from audit_note_references import citations, validate  # noqa: E402

LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko",
             "zh-Hans", "zh-Hant", "ar", "hi", "sw")
QUOTE_LINE = re.compile(r"^\s*(?:- )?\(([^)]+)\)\s*[:;]?\s+(.+)$")
VERSE_MARKER = re.compile(r"\((\d+):(\d+)(?:[-\u2013](\d+))?\)[.\s]*$")
INLINE_TAG = re.compile(r"\[/?(?:J|DN|ADD)\]")
HEADING = re.compile(r"^(#{1,6}) ", re.M)
OUTER_QUOTES = '\"\u201c\u201d\u201e\u00ab\u00bb\u300c\u300d'
REVIEWED_EXCERPTS = {
    ("ja", "\u30ed\u30fc\u30de 6:1\u20132"),
    ("ja", "\u30de\u30eb\u30b3 7:15"),
    ("ja", "\u30da\u30c8\u30ed\u306e\u624b\u7d19\u4e8c 1:21"),
}


def visible(text: str) -> str:
    return INLINE_TAG.sub("", text).strip().strip(OUTER_QUOTES + " ")


def source_quote(language: str, reference, books: dict) -> str:
    key = (language, reference.collection, reference.book)
    if key not in books:
        path = ASSETS / "books" / reference.collection / language / f"{reference.book}.json"
        books[key] = json.loads(path.read_text(encoding="utf-8-sig"))
    parts = []
    for chapter in range(reference.chapter, reference.end_chapter + 1):
        story = next(item for item in books[key]["stories"]
                     if item["id"].endswith(f"-{chapter}"))
        first = reference.verse if chapter == reference.chapter else 1
        last = reference.end_verse if chapter == reference.end_chapter else 999
        if first is None or last is None:
            raise ValueError(f"Quote lacks verse: {language}/{reference.display}")
        for bullet in story["summaryBullets"]:
            marker = VERSE_MARKER.search(bullet)
            if marker and int(marker[2]) <= last and int(marker[3] or marker[2]) >= first:
                parts.append(INLINE_TAG.sub("", bullet[:marker.start()]).strip())
    return visible(" ".join(parts))


def main() -> int:
    failures = []
    checked = 0
    citations_checked = 0
    books = {}
    expected_outline = None
    for language in LANGUAGES:
        path = ASSETS / "notes" / language / "common_distortions.md"
        body = path.read_text(encoding="utf-8-sig")
        for reference in citations(language, body):
            citations_checked += 1
            for problem in validate(language, reference):
                failures.append(f"{language}: {reference.display}: {problem}")
        outline = [len(level) for level in HEADING.findall(body)]
        if expected_outline is None:
            expected_outline = outline
        elif outline != expected_outline:
            failures.append(f"{language}: section outline differs")
        rows = 0
        for line in body.splitlines():
            match = QUOTE_LINE.match(line)
            if not match:
                continue
            rows += 1
            parsed = citations(language, match[1])
            if len(parsed) != 1:
                failures.append(f"{language}: cannot resolve {match[1]}")
                continue
            reference = parsed[0]
            if validate(language, reference):
                failures.append(f"{language}: invalid quote reference {match[1]}")
                continue
            expected = source_quote(language, reference, books)
            actual = visible(match[2])
            excerpt = (language, match[1]) in REVIEWED_EXCERPTS
            if not expected or (expected not in actual and
                                not (excerpt and len(actual) > 25 and actual in expected)):
                failures.append(f"{language}: quote differs from {match[1]}")
            checked += 1
        if rows != 82:
            failures.append(f"{language}: expected 82 direct quote lines; found {rows}")
    print(f"Checked {checked} Common Distortions quotations and {citations_checked} "
          f"citation endpoints in {len(LANGUAGES)} languages; "
          f"failures: {len(failures)}")
    for failure in failures:
        print(failure)
    return int(bool(failures))


if __name__ == "__main__":
    raise SystemExit(main())
