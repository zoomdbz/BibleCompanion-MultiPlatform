#!/usr/bin/env python3
"""Read-only BSB heading comparison against the supplied official USFM.

Reports source section labels independently of the verse-text audit. Source
labels that interrupt a verse (notably Song of Songs speakers) need human
review because the app's beforeVerse model cannot represent them precisely.
"""

from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re
import sys

from audit_scripture_sources import BOOKS


MARKER = re.compile(r"\\(?P<name>c|v|s1|s2|ms|qa)\s+(?P<value>[^\\\r\n]*)")
INLINE_SPEAKER = re.compile(r"\\q[12]\s+(?!\\v\b)\S")
EXPECTED_INLINE_SPEAKERS = [
    ("song_of_songs", 1, 4, "The Friends"),
    ("song_of_songs", 1, 4, "The Bride"),
    ("song_of_songs", 5, 1, "The Friends"),
    ("song_of_songs", 6, 13, "The Bridegroom"),
    ("song_of_songs", 7, 9, "The Bride"),
    ("song_of_songs", 8, 5, "The Bride"),
]


def source_headings(path: Path) -> tuple[list[tuple[int, int, str]], list[tuple[int, int, str]]]:
    raw = path.read_text(encoding="utf-8-sig")
    markers = list(MARKER.finditer(raw))
    chapter = verse = 0
    pending: list[tuple[str, str]] = []
    headings: list[tuple[int, int, str]] = []
    inline: list[tuple[int, int, str]] = []
    for marker in markers:
        name, value = marker.group("name"), marker.group("value").strip()
        if name == "c":
            chapter = int(value)
            verse = 0
        elif name in {"s1", "s2", "ms", "qa"}:
            following = raw[marker.end():]
            next_verse = re.search(r"\\v\s+\d+", following)
            intervening = following[:next_verse.start()] if next_verse else following
            if name == "s2" and INLINE_SPEAKER.search(intervening):
                inline.append((chapter, verse, value))
            else:
                pending.append((name, value))
        elif name == "v":
            verse = int(value.split()[0].split("-")[0])
            i = 0
            while i < len(pending):
                pending_name, text = pending[i]
                if pending_name == "qa":
                    if i + 1 >= len(pending) or pending[i + 1][0] != "qa":
                        raise ValueError(f"unpaired acrostic label: {path}: {chapter}:{verse}")
                    text += " " + pending[i + 1][1]
                    i += 1
                headings.append((chapter, verse, text))
                i += 1
            pending.clear()
    if pending:
        raise ValueError(f"section headings without a following verse: {path}: {pending}")
    return headings, inline


def app_headings(path: Path) -> list[tuple[int, int, str]]:
    book = json.loads(path.read_text(encoding="utf-8"))
    rows = []
    for chapter, story in enumerate(book["stories"], 1):
        for heading in story.get("headings", []):
            rows.extend((chapter, heading["beforeVerse"], text)
                        for text in heading["text"].split("\n"))
    return rows


def compare(repo: Path, source: Path) -> dict:
    details = []
    source_count = app_count = exact = 0
    inline_all = []
    for code, collection, book in BOOKS:
        source_rows, inline = source_headings(source / f"{code}.usfm")
        app_rows = app_headings(repo / "shared/assets/books" / collection / "en" / f"{book}.json")
        source_count += len(source_rows)
        app_count += len(app_rows)
        inline_all.extend((book, chapter, verse, text) for chapter, verse, text in inline)
        source_counter, app_counter = Counter(source_rows), Counter(app_rows)
        exact += sum((source_counter & app_counter).values())
        if source_counter != app_counter:
            details.append({"book": book, "missing": list((source_counter - app_counter).elements()),
                            "extra": list((app_counter - source_counter).elements())})
    return {"sourceHeadings": source_count, "appHeadings": app_count,
            "exactAnchoredHeadings": exact, "unrepresentableInlineSpeakers": inline_all,
            "expectedInlineSpeakers": inline_all == EXPECTED_INLINE_SPEAKERS,
            "differentBooks": len(details), "differences": details}


if __name__ == "__main__":
    repo = Path(__file__).resolve().parents[1]
    result = compare(repo, Path(sys.argv[1]))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(bool(result["differences"]) or not result["expectedInlineSpeakers"])
