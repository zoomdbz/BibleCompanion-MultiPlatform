#!/usr/bin/env python3
"""Build reviewed-coordinate reference rules for the two bundled Swahili Bibles.

The rules assert only that a verse with the same book, chapter, and verse
number exists in both source files. No text equivalence is implied. A verse
present in just one source gets no cross-edition rule.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets/books"
OUT = ASSETS / "editions/sw/suv1952/_reference_map.json"
TRAILING = re.compile(r"\((\d+):(\d+)(?:-(\d+))?\)\.?$")


def native_numbers(story: dict) -> set[int]:
    chapter = int(story["id"].rsplit("-", 1)[1])
    numbers: set[int] = set()
    for bullet in story["summaryBullets"]:
        match = TRAILING.search(bullet)
        if match is None or int(match[1]) != chapter:
            raise ValueError(f"Missing native verse marker: {story['id']}: {bullet[:80]}")
        numbers.update(range(int(match[2]), int(match[3] or match[2]) + 1))
    return numbers


def overlay_numbers(chapter: dict) -> set[int]:
    numbers: set[int] = set()
    for verse in chapter["verses"]:
        numbers.update(range(verse["verse"], verse.get("verseEnd", verse["verse"]) + 1))
    return numbers


def consecutive_ranges(numbers: set[int]) -> list[tuple[int, int]]:
    ranges: list[tuple[int, int]] = []
    for number in sorted(numbers):
        if ranges and number == ranges[-1][1] + 1:
            ranges[-1] = (ranges[-1][0], number)
        else:
            ranges.append((number, number))
    return ranges


def main() -> None:
    books: list[dict] = []
    differences: list[tuple[str, int, list[int], list[int]]] = []
    for collection in ("old_testament", "new_testament"):
        index = json.loads((ASSETS / collection / "sw/_index.json").read_text())
        for book_id, _ in index:
            base = json.loads((ASSETS / collection / "sw" / f"{book_id}.json").read_text())
            overlay = json.loads((ASSETS / "editions/sw/suv1952" / collection / f"{book_id}.json").read_text())
            chapters = overlay["chapters"]
            if len(base["stories"]) != len(chapters):
                raise ValueError(f"Chapter count mismatch: {book_id}")
            mappings: list[dict] = []
            complete = True
            for story, chapter in zip(base["stories"], chapters):
                number = int(story["id"].rsplit("-", 1)[1])
                if chapter["number"] != number:
                    raise ValueError(f"Chapter identity mismatch: {book_id} {number}")
                source = native_numbers(story)
                target = overlay_numbers(chapter)
                if source != target:
                    differences.append((book_id, number, sorted(source - target), sorted(target - source)))
                    complete = False
                for start, end in consecutive_ranges(source & target):
                    rule = {
                        "sourceChapter": number,
                        "sourceVerse": start,
                        "targetChapter": number,
                        "targetVerse": start,
                    }
                    if end > start:
                        rule["sourceVerseEnd"] = end
                        rule["targetVerseEnd"] = end
                    mappings.append(rule)
            books.append({"bookId": book_id, "complete": complete, "mappings": mappings})
    if differences != [("john", 7, [53], [])]:
        raise ValueError(f"Unexpected source reference differences: {differences}")
    content = {
        "schemaVersion": 1,
        "language": "sw",
        "baseEditionId": "onen2015",
        "editionId": "suv1952",
        "books": books,
    }
    rendered = json.dumps(content, ensure_ascii=False, indent=2) + "\n"
    if not OUT.exists() or OUT.read_text() != rendered:
        OUT.write_text(rendered)
    print(f"Mapped {len(books)} books; {sum(len(x['mappings']) for x in books)} coordinate ranges")
    print(f"Unmapped source-only/target-only coordinates: {differences}")


if __name__ == "__main__":
    main()
