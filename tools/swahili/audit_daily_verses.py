#!/usr/bin/env python3
"""Check every Swahili daily and feast quote against the bundled ONEN text."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
sys.path.insert(0, str(ROOT / "tools/prophecy"))
from audit_note_references import citations, validate  # noqa: E402

VERSE_MARKER = re.compile(r"\((\d+):(\d+)(?:-(\d+))?\)[.\s]*$")
INLINE_TAG = re.compile(r"\[/?(?:J|DN|ADD)\]")


def native_quote(collection: str, book_id: str, chapter: int,
                 first: int, last: int) -> str:
    path = ASSETS / "books" / collection / "sw" / f"{book_id}.json"
    book = json.loads(path.read_text(encoding="utf-8-sig"))
    story = next(item for item in book["stories"]
                 if item["id"].endswith(f"-{chapter}"))
    text = []
    for bullet in story["summaryBullets"]:
        marker = VERSE_MARKER.search(bullet)
        if marker and first <= int(marker[2]) <= last:
            text.append(INLINE_TAG.sub("", bullet[:marker.start()]).strip())
    return " ".join(text)


def main() -> int:
    daily = json.loads((ASSETS / "daily_verses/sw/daily.json").read_text(encoding="utf-8"))
    feasts = json.loads((ASSETS / "daily_verses/sw/feasts.json").read_text(encoding="utf-8"))
    rows = daily["verses"] + list(feasts["feastVerses"].values())
    failures = []
    for index, row in enumerate(rows):
        parsed = citations("sw", row["ref"])
        if len(parsed) != 1 or validate("sw", parsed[0]):
            failures.append(f"{index}: invalid reference {row['ref']}")
            continue
        ref = parsed[0]
        if ref.chapter != ref.end_chapter or ref.verse is None or ref.end_verse is None:
            failures.append(f"{index}: unsupported reference range {row['ref']}")
            continue
        expected = native_quote(ref.collection, ref.book, ref.chapter,
                                ref.verse, ref.end_verse)
        if row["text"] != expected:
            failures.append(f"{index}: quote differs from ONEN {row['ref']}")
    print(f"Checked {len(rows)} Swahili daily and feast quotes; failures: {len(failures)}")
    for failure in failures:
        print(failure)
    return int(bool(failures))


if __name__ == "__main__":
    raise SystemExit(main())
