#!/usr/bin/env python3
"""Move 62 verified NBS Psalm superscriptions into their numbered verse slots.

The linked Bible.com NBS edition numbers these title lines as verse 1, or as
verses 1-2 in Psalms 51, 52, 54, and 60. This script does not touch any other
Psalm, nor any existing body verse. It downloads pages only for validation;
neither remote HTML nor remote Scripture text is written to disk.

Run with --apply to make the one asset change; run with --verify afterwards.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import time
import unicodedata

import requests
from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "shared/assets/books/old_testament/fr/psalms.json"
NBS_URL = "https://www.bible.com/fr/bible/104/PSA.{chapter}.NBS"
AFFECTED = (
    3, 4, 5, 6, 7, 8, 9, 12, 18, 19, 20, 21, 22, 30, 31, 34,
    36, 38, 39, 40, 41, 42, 44, 45, 46, 47, 48, 49, 51, 52, 53,
    54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 67, 68, 69,
    70, 75, 76, 77, 80, 81, 83, 84, 85, 88, 89, 92, 102, 108,
    140, 142,
)
TWO_VERSE_TITLES = frozenset({51, 52, 54, 60})
REFERENCE = re.compile(r"\((\d+):(\d+)(?:-(\d+))?\)\.?$")


def normalized(value: str) -> str:
    text = " ".join(unicodedata.normalize("NFC", value).split())
    return re.sub(r"\s+([.,;!?])", r"\1", text).casefold()


def reference_range(value: str, chapter: int) -> range:
    match = REFERENCE.search(value)
    if match is None or int(match.group(1)) != chapter:
        raise ValueError(f"Psalm {chapter}: invalid trailing verse reference: {value[-80:]!r}")
    start, end = int(match.group(2)), int(match.group(3) or match.group(2))
    if start < 1 or end < start:
        raise ValueError(f"Psalm {chapter}: invalid verse range {start}-{end}")
    return range(start, end + 1)


def local_verses(story: dict, chapter: int) -> set[int]:
    found: set[int] = set()
    for bullet in story["summaryBullets"]:
        covered = set(reference_range(bullet, chapter))
        if found & covered:
            raise ValueError(f"Psalm {chapter}: duplicate local verse markers {sorted(found & covered)}")
        found.update(covered)
    return found


def source_verses(session: requests.Session, chapter: int) -> dict[int, str]:
    url = NBS_URL.format(chapter=chapter)
    response = session.get(url, timeout=30)
    response.raise_for_status()
    if f"/104/PSA.{chapter}.NBS" not in response.url:
        raise ValueError(f"Psalm {chapter}: unexpected Bible.com URL {response.url}")
    soup = BeautifulSoup(response.text, "html.parser")
    del response
    source: dict[int, list[str]] = {}
    for verse in soup.select("[data-usfm]"):
        match = re.fullmatch(rf"PSA\.{chapter}\.(\d+)", verse.get("data-usfm", ""))
        if match is None:
            continue
        number = int(match.group(1))
        source.setdefault(number, [])
        for child in verse.find_all(recursive=False):
            classes = " ".join(child.get("class", []))
            if "__label" in classes or "__note" in classes:
                continue
            content = " ".join(child.get_text(" ", strip=True).split())
            if content:
                source[number].append(content)
    if not source or set(source) != set(range(1, max(source) + 1)):
        raise ValueError(f"Psalm {chapter}: incomplete or noncontiguous NBS source markers")
    return {number: " ".join(parts) for number, parts in source.items()}


def validate_source(story: dict, chapter: int, source: dict[int, str], applied: bool) -> list[str]:
    title_count = 2 if chapter in TWO_VERSE_TITLES else 1
    title_numbers = set(range(1, title_count + 1))
    actual = local_verses(story, chapter)
    if applied:
        if actual != set(source) or story.get("superscription", "").strip():
            raise ValueError(f"Psalm {chapter}: title verses not applied exactly once")
        local_titles = story["summaryBullets"][:title_count]
        for number, bullet in enumerate(local_titles, start=1):
            if set(reference_range(bullet, chapter)) != {number}:
                raise ValueError(f"Psalm {chapter}: title bullet {number} has wrong marker")
            text = REFERENCE.sub("", bullet).strip()
            if normalized(text) != normalized(source[number]):
                raise ValueError(f"Psalm {chapter}: title bullet {number} differs from NBS")
        return local_titles
    if actual != set(source) - title_numbers:
        raise ValueError(f"Psalm {chapter}: local/source verse inventory differs beyond numbered titles")
    if min(actual) != title_count + 1:
        raise ValueError(f"Psalm {chapter}: unexpected first body verse")
    superscription = story.get("superscription", "")
    if not superscription or normalized(superscription) != normalized(" ".join(source[n] for n in sorted(title_numbers))):
        raise ValueError(f"Psalm {chapter}: superscription does not match exact NBS title verses")
    if title_count == 1:
        return [f"{superscription} ({chapter}:1)."]
    first_title = " ".join(source[1].split())
    if not superscription.startswith(first_title + " "):
        raise ValueError(f"Psalm {chapter}: verse 1/2 title boundary cannot be preserved")
    second_title = superscription[len(first_title) + 1 :]
    if normalized(second_title) != normalized(source[2]):
        raise ValueError(f"Psalm {chapter}: title verse 2 does not match NBS")
    return [f"{first_title} ({chapter}:1).", f"{second_title} ({chapter}:2)."]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--apply", action="store_true")
    action.add_argument("--verify", action="store_true")
    args = parser.parse_args()

    original = ASSET.read_text(encoding="utf-8")
    payload = json.loads(original)
    if json.dumps(payload, ensure_ascii=False, indent=2) + "\n" != original:
        raise ValueError("Psalm JSON formatting differs from the expected lossless serializer")
    stories = {int(story["id"].rsplit("-", 1)[-1]): story for story in payload["stories"]}
    if len(stories) != 150 or len(AFFECTED) != 62 or len(set(AFFECTED)) != 62:
        raise ValueError("Psalm chapter or audited target inventory changed")
    before_bodies = {
        chapter: tuple(
            stories[chapter]["summaryBullets"][2 if chapter in TWO_VERSE_TITLES else 1 :]
            if args.verify else stories[chapter]["summaryBullets"]
        )
        for chapter in AFFECTED
    }
    session = requests.Session()
    session.headers.update({"User-Agent": "BibleCompanion-Structural-Auditor/1.0"})
    source_counts = 0
    for chapter in AFFECTED:
        story = stories[chapter]
        source = source_verses(session, chapter)
        title_bullets = validate_source(story, chapter, source, applied=args.verify)
        source_counts += len(title_bullets)
        if args.apply:
            story["summaryBullets"] = title_bullets + story["summaryBullets"]
            story["superscription"] = ""
        time.sleep(0.1)
    if args.apply:
        heading = stories[42].get("headings")
        if heading != [{"beforeVerse": 2, "text": "LIVRE DEUXIÈME"}]:
            raise ValueError("Psalm 42 book heading is not at its audited original anchor")
        heading[0]["beforeVerse"] = 1
        for chapter in AFFECTED:
            title_count = 2 if chapter in TWO_VERSE_TITLES else 1
            if tuple(stories[chapter]["summaryBullets"][title_count:]) != before_bodies[chapter]:
                raise ValueError(f"Psalm {chapter}: existing body verse changed")
        output = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        ASSET.write_text(output, encoding="utf-8", newline="")
    else:
        if stories[42].get("headings") != [{"beforeVerse": 1, "text": "LIVRE DEUXIÈME"}]:
            raise ValueError("Psalm 42 book heading is not before NBS verse 1")
    digest = hashlib.sha256("\n".join(bullet for bullets in before_bodies.values() for bullet in bullets).encode()).hexdigest()
    print(
        f"French NBS Psalm {'repair applied' if args.apply else 'repair verified'}: "
        f"{len(AFFECTED)} chapters, {source_counts} numbered title verses, "
        f"body SHA-256 {digest}; no remote HTML or text cached."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, requests.RequestException) as exc:
        print(f"French NBS Psalm repair blocked: {exc}", file=sys.stderr)
        raise SystemExit(2)
