"""Read-only chapter/verse existence checks for localized study assets.

Uses the app's own localized book-alias tables and bundled edition numbering.
This is not a replacement for the Kotlin linker's tests or a semantic review of
the cited passage. A valid address can still cite the wrong passage.

Run: python -X utf8 tools/prophecy/audit_note_references.py --language en
Omit --language to check every supported language.
"""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi", "sw")
PAGES = ("revelation_timeline.md", "messianic_prophecy.md", "daniels_timeline.md", "second_coming_rapture.md")
SINGLE_CHAPTER_BOOKS = {
    "obadiah", "philemon", "2_john", "3_john", "jude",
    "letter_of_jeremiah", "prayer_of_manasseh", "psalm_151",
}
VERSE_MARKER = re.compile(r"\((\d+):(\d+)(?:[-\u2013](\d+))?\)[.\s]*$")
TAIL = (
    r"\s*(?P<chapter>\d{1,3})(?!\d)"
    r"(?:\s*(?::\s*|,)(?P<verse>\d{1,3})(?!\d|[,.\s]\d{3}\b))?"
    r"(?:\s*[-\u2013]\s*(?:(?P<end_chapter>\d{1,3})(?::\s*|,))?"
    r"(?P<end_number>\d{1,3})(?!\d))?"
)


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def normalize(text: str) -> str:
    # Only a validation scan copy; never change the reader's visible text.
    return unicodedata.normalize("NFKC", text).replace("\u060c", ",")


@dataclass(frozen=True)
class Citation:
    book: str
    collection: str
    chapter: int
    verse: int | None
    end_chapter: int
    end_verse: int | None
    display: str


@lru_cache(maxsize=None)
def alias_pattern(language: str):
    english = read_json(ASSETS / "refs/en/book_aliases.json")
    localized = read_json(ASSETS / f"refs/{language}/book_aliases.json")
    if len(english) != len(localized):
        raise ValueError(f"Alias rows do not align for {language}")
    books = {}
    for collection in ("old_testament", "new_testament", "deuterocanonical",
                       "apocrypha", "pseudepigrapha"):
        for book_id, title in read_json(ASSETS / f"books/{collection}/en/_index.json"):
            if not book_id:
                continue
            books[title.casefold()] = (book_id, collection)
            # "Esther" is the canonical book, while "Esther (Greek)" is a
            # separate deuterocanonical book. A shortened title must not
            # replace an earlier exact canonical title.
            books.setdefault(title.split(" (")[0].casefold(), (book_id, collection))
    owners = {}
    verse_only = set()
    for en, local in zip(english, localized):
        book = next((books[name.casefold()] for name in (en["canon"], *en["aliases"])
                     if name.casefold() in books), None)
        if book is None:
            continue
        for alias in (local["canon"], *local["aliases"], en["canon"], *en["aliases"]):
            # These English abbreviations are ordinary Swahili conjunctions
            # and prepositions, so they cannot safely start a book citation.
            if language == "sw" and alias.casefold() in {"na", "la", "je"}:
                continue
            # Short aliases can be ordinary words, but they also include French
            # canonical Luc and Japanese ルカ/使徒. Require chapter AND verse for
            # these names instead of silently missing their explicit citations.
            key = normalize(alias).casefold()
            owners.setdefault(key, book)
            if language in ("ja", "ko", "zh-Hans", "zh-Hant") and " " in alias:
                owners.setdefault(normalize(alias.replace(" ", "")).casefold(), book)
            if (re.fullmatch(r"[A-Za-z.]{1,3}", alias)
                    or (alias not in (local["canon"], en["canon"]) and len(alias) < 3)):
                verse_only.add(key)
    # Commas denote chapter/verse in German. Elsewhere a short book alias next
    # to a comma can instead be prose followed by a thousands-formatted number.
    separator = r"(?::\s*|,)" if language == "de" else r":\s*"
    explicit_verse = r"(?=\s*[1-9]\d{0,2}\s*" + separator + r"[1-9]\d{0,2}(?!\d))"
    alternatives = "|".join(
        re.escape(key) + (explicit_verse if key in verse_only else "")
        for key in sorted(owners, key=len, reverse=True)
    )
    # In Swahili, a comma after a verse range separates more verses (for
    # example 26:1-4,51); it is not a chapter/verse separator.
    tail = TAIL.replace(r"(?::\s*|,)", r":\s*") if language == "sw" else TAIL
    pattern = re.compile(r"(?<![A-Za-z0-9_])(?P<book>" + alternatives + ")" + tail, re.I)
    return owners, pattern


def citations(language: str, text: str) -> list[Citation]:
    owners, pattern = alias_pattern(language)
    scan = normalize(text)
    result = []
    for match in pattern.finditer(scan):
        book, collection = owners[match["book"].casefold()]
        chapter = int(match["chapter"])
        verse = int(match["verse"]) if match["verse"] else None
        end = int(match["end_number"]) if match["end_number"] else None
        end_chapter = int(match["end_chapter"]) if match["end_chapter"] else chapter
        end_verse = verse
        if end is not None:
            if verse is None:
                end_chapter, end_verse = end, None
            else:
                end_verse = end
        if book in SINGLE_CHAPTER_BOOKS and verse is None:
            verse, end_verse = chapter, end_chapter
            chapter = end_chapter = 1
        # "Daniel 360-day years" refers to a calculation, not chapter 360.
        if verse is None and end is None and re.match(r"-\D", scan[match.end():]):
            continue
        result.append(Citation(book, collection, chapter, verse, end_chapter, end_verse, match[0]))
        if language == "sw" and verse is not None and book not in SINGLE_CHAPTER_BOOKS:
            rest = scan[match.end():]
            while extra := re.match(r"\s*,\s*(\d{1,3})(?:\s*[-\u2013]\s*(\d{1,3}))?", rest):
                first_extra = int(extra[1])
                last_extra = int(extra[2]) if extra[2] else first_extra
                result.append(Citation(book, collection, chapter, first_extra, chapter,
                                       last_extra, f"{match['book']} {chapter}:{extra[0].strip(' ,')}"))
                rest = rest[extra.end():]
    return result


@lru_cache(maxsize=None)
def verse_inventory(language: str, collection: str, book: str):
    document = read_json(ASSETS / f"books/{collection}/{language}/{book}.json")
    chapters: dict[int, set[int]] = {}
    for story in document["stories"]:
        chapter_match = re.search(r"-(\d+)$", story["id"])
        if chapter_match is None:
            continue
        chapter = int(chapter_match[1])
        verses = chapters.setdefault(chapter, set())
        for bullet in story.get("summaryBullets", []):
            marker = VERSE_MARKER.search(bullet)
            if marker is None:
                continue
            marked_chapter, first = int(marker[1]), int(marker[2])
            last = int(marker[3]) if marker[3] else first
            if marked_chapter != chapter:
                raise ValueError(f"Mismatched chapter in {language}/{book}: {bullet[-60:]}")
            verses.update(range(first, last + 1))
    return chapters


def validate(language: str, citation: Citation) -> list[str]:
    inventory = verse_inventory(language, citation.collection, citation.book)
    problems = []
    first = (citation.chapter, citation.verse or 0)
    last = (citation.end_chapter, citation.end_verse or 0)
    if last < first:
        problems.append("range ends before it starts")
    for chapter, verse in ((citation.chapter, citation.verse),
                           (citation.end_chapter, citation.end_verse)):
        if chapter not in inventory:
            problems.append(f"chapter {chapter} absent")
        elif verse is not None and verse not in inventory[chapter]:
            problems.append(f"verse {chapter}:{verse} absent")
    return sorted(set(problems))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", choices=LANGUAGES, action="append")
    parser.add_argument("--all-notes", action="store_true",
                        help="Check every localized Markdown study note")
    args = parser.parse_args()
    failures = 0
    for language in args.language or LANGUAGES:
        count = 0
        pages = (sorted(path.name for path in (ASSETS / "notes" / language).glob("*.md"))
                 if args.all_notes else PAGES)
        for page in pages:
            path = ASSETS / f"notes/{language}/{page}"
            refs = citations(language, path.read_text(encoding="utf-8-sig"))
            count += len(refs)
            for ref in refs:
                try:
                    problems = validate(language, ref)
                except FileNotFoundError:
                    problems = ["translated book asset absent"]
                if problems:
                    failures += 1
                    print(f"{language}/{page}: {ref.display}: {'; '.join(problems)}")
        print(f"{language}: checked {count} explicit book/chapter references")
    print(f"Invalid citation endpoints: {failures}")
    return int(failures > 0)


if __name__ == "__main__":
    raise SystemExit(main())
