#!/usr/bin/env python3
"""Reproduce pinned RST+ MyBible red-letter evidence for Russian Synodal."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import html
import io
import json
from pathlib import Path
import re
import sqlite3
import zipfile

import requests

from import_traditional_editions import BOOKS, base_jesus_ranges
from jesus_word_spans import sha256_text
from propose_jesus_word_spans import build_queue


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "tools/traditional/jesus_word_spans/ru_synodal1876_rstplus_evidence.json"
URL = "https://www.ph4.org/_dl.php?back=bbl&a=RST_plus_&b=mybible&c"
ARCHIVE_SHA256 = "16DF67D751069FA23B481BA6FCFDF29422526E47727D21D912C71F7052ED3B4A"
DB_SHA256 = "48F161475699F571E41E3760AEBF7769F6B7D5E6921353C926181676C29E5366"
STRONG = re.compile(r"<S>.*?</S>", re.DOTALL)
OTHER_TAG = re.compile(r"<(?!/?J>)[^>]+>")
J_SPAN = re.compile(r"<J>(.*?)</J>", re.DOTALL)
APP_TAG = re.compile(r"\[/?(?:DN|ADD|J)\]")
WORD = re.compile(r"[А-Яа-яЁёA-Za-z0-9]+")
NT_BOOKS = [book for _, collection, book in BOOKS if collection == "new_testament"]
BOOK_NUMBER = {book: 470 + 10 * index for index, book in enumerate(NT_BOOKS)}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def fetch_database() -> sqlite3.Connection:
    response = requests.get(URL, timeout=60)
    response.raise_for_status()
    if digest(response.content) != ARCHIVE_SHA256:
        raise ValueError("RST+ ph4 archive SHA-256 changed")
    with zipfile.ZipFile(io.BytesIO(response.content)) as archive:
        if "RST+.SQLite3" not in archive.namelist():
            raise ValueError("RST+ archive lacks the pinned Bible database")
        data = archive.read("RST+.SQLite3")
    if digest(data) != DB_SHA256:
        raise ValueError("RST+ SQLite database SHA-256 changed")
    database = sqlite3.connect(":memory:")
    database.deserialize(data)
    return database


def source_markup(raw: str) -> str:
    """Discard Strong numbers and layout tags while retaining visible words and J."""
    return html.unescape(OTHER_TAG.sub("", STRONG.sub("", raw)))


def source_status(markup: str) -> str:
    red = "".join(J_SPAN.findall(markup))
    if not WORD.search(red):
        return "no-red"
    outside = J_SPAN.sub("", markup)
    return "partial-red" if WORD.search(outside) else "full-red"


def word_sequence(value: str) -> list[str]:
    return [match.group().casefold() for match in WORD.finditer(value)]


def database_verse(database: sqlite3.Connection, book: str, chapter: int, verse: int) -> str:
    rows = database.execute(
        "SELECT text FROM verses WHERE book_number=? AND chapter=? AND verse=?",
        (BOOK_NUMBER[book], chapter, verse),
    ).fetchall()
    if len(rows) != 1:
        raise ValueError(f"RST+ source verse missing or duplicated: {book} {chapter}:{verse}")
    return rows[0][0]


def kjv_index(root: Path, book: str) -> dict[tuple[int, int], str]:
    payload = json.loads(
        (root / "shared/assets/books/editions/en/kjv1769/new_testament" / f"{book}.json")
        .read_text(encoding="utf-8")
    )
    return {
        (chapter["number"], verse["verse"]): verse["text"]
        for chapter in payload["chapters"] for verse in chapter["verses"]
    }


def target_index(root: Path, book: str) -> dict[tuple[int, int], str]:
    payload = json.loads(
        (root / "shared/assets/books/editions/ru/synodal1876/new_testament" / f"{book}.json")
        .read_text(encoding="utf-8")
    )
    return {
        (chapter["number"], verse["verse"]): verse["text"].replace("[J]", "").replace("[/J]", "")
        for chapter in payload["chapters"] for verse in chapter["verses"]
    }


def build_evidence(root: Path = ROOT) -> dict:
    database = fetch_database()
    candidates = build_queue(root, "ru", "synodal1876")
    if candidates["candidateCount"] != 626:
        raise ValueError("Pinned KJV mixed inventory changed")
    rows = []
    text_status = Counter()
    red_status = Counter()
    for target in candidates["rows"]:
        book, chapter, verse = target["bookId"], target["chapter"], target["verse"]
        source_raw = database_verse(database, book, chapter, verse)
        markup = source_markup(source_raw)
        plain = J_SPAN.sub(lambda match: match.group(1), markup)
        app_plain = APP_TAG.sub("", target["targetText"])
        status = "exact" if plain == app_plain else (
            "space-only" if " ".join(plain.split()) == " ".join(app_plain.split())
            else "same-words" if word_sequence(plain) == word_sequence(app_plain)
            else "different-words"
        )
        text_status[status] += 1
        red_status[source_status(markup)] += 1
        rows.append({
            "collection": "new_testament",
            "bookId": book, "chapter": chapter, "verse": verse,
            "sourceTextSha256": sha256_text(target["targetText"]),
            "kjvTextSha256": target["kjvTextSha256"],
            "kjvSpeechShape": target["kjvSpeechShape"],
            "rstRawSha256": sha256_text(source_raw),
            "rstMarkup": markup,
            "rstRedSpans": J_SPAN.findall(markup),
            "targetText": target["targetText"],
            "textStatus": status,
        })
    full_counts = Counter()
    full_exceptions = []
    full_count = 0
    for book in NT_BOOKS:
        kjv = kjv_index(root, book)
        target = target_index(root, book)
        full, _mixed = base_jesus_ranges(root, "new_testament", book)
        for chapter, ranges in full.items():
            for start, stop in ranges:
                for verse in range(start, stop + 1):
                    full_count += 1
                    raw = database_verse(database, book, chapter, verse)
                    markup = source_markup(raw)
                    status = source_status(markup)
                    full_counts[status] += 1
                    if status != "full-red":
                        full_exceptions.append({
                            "collection": "new_testament",
                            "bookId": book, "chapter": chapter, "verse": verse,
                            "sourceTextSha256": sha256_text(target[(chapter, verse)]),
                            "kjvTextSha256": sha256_text(kjv[(chapter, verse)]),
                            "rstRawSha256": sha256_text(raw),
                            "rstMarkup": markup,
                            "targetText": target[(chapter, verse)],
                            "redStatus": status,
                        })
    if full_count != 1402 or full_counts != {"full-red": 1396, "partial-red": 6}:
        raise ValueError(f"RST+ KJV-full inventory drift: {full_count}, {full_counts}")
    return {
        "schemaVersion": 1,
        "language": "ru",
        "editionId": "synodal1876",
        "source": {
            "name": "ph4 RST+ MyBible",
            "url": URL,
            "archiveSha256": ARCHIVE_SHA256,
            "database": "RST+.SQLite3",
            "databaseSha256": DB_SHA256,
        },
        "semanticAuthority": "pinned en/kjv1769 WJ",
        "candidateCount": len(rows),
        "textStatus": dict(text_status),
        "sourceRedStatus": dict(red_status),
        "rows": rows,
        "fullAuthorityCount": full_count,
        "fullRedStatus": dict(full_counts),
        "fullExceptions": full_exceptions,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--apply", action="store_true", help="write the pinned evidence if absent")
    args = parser.parse_args()
    evidence = build_evidence(args.root)
    if args.apply:
        path = args.root / "tools/traditional/jesus_word_spans/ru_synodal1876_rstplus_evidence.json"
        if path.exists():
            raise ValueError(f"Refusing to overwrite existing evidence: {path}")
        path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        key: evidence[key] for key in (
            "candidateCount", "textStatus", "sourceRedStatus",
            "fullAuthorityCount", "fullRedStatus"
        )
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
