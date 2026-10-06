#!/usr/bin/env python3
"""Check or emit a patch for the Japanese/Russian daily passage calendars.

The English bank supplies the day order, not translated Scripture. Every
localized entry comes from a whole native unit in the existing book assets.
This tool never writes files; --patch emits an apply_patch-compatible patch.
"""

from __future__ import annotations

import argparse
import difflib
import functools
import json
from pathlib import Path
import re
import sys
import unicodedata

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
MAP_PATH = Path(__file__).with_name("daily_calendar_reference_map.json")
LANGUAGES = ("ja", "ru")
REFERENCE = re.compile(r"^(.+?) (\d+):(\d+)(?:-(\d+))?$")
ANCHOR = re.compile(r"\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\s*\.?\s*$")
TAG = re.compile(r"\[/?(?:J|DN|ADD)\]")


def read_json(path: Path) -> dict | list:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def normalized_text(text: str) -> str:
    return " ".join(unicodedata.normalize("NFC", TAG.sub("", text)).split())


def parse_reference(reference: str) -> tuple[str, int, int, int]:
    match = REFERENCE.fullmatch(reference)
    if not match:
        raise ValueError(f"Invalid daily reference: {reference}")
    name, chapter, start, end = match.groups()
    if name not in book_catalog():
        raise ValueError(f"Invalid daily book: {name}")
    chapter, start, end = int(chapter), int(start), int(end or start)
    if chapter < 1 or start < 1 or end < start:
        raise ValueError(f"Invalid daily range: {reference}")
    return name, chapter, start, end


@functools.lru_cache(None)
def book_catalog() -> dict[str, tuple[str, str]]:
    result = {}
    for collection in ("old_testament", "new_testament"):
        for book_id, title in read_json(ASSETS / "books" / collection / "en/_index.json"):
            result[title] = (collection, book_id)
    result["Acts of the Apostles"] = result["Acts"]
    return result


@functools.lru_cache(None)
def book_document(language: str, collection: str, book_id: str) -> dict:
    return read_json(ASSETS / "books" / collection / language / (book_id + ".json"))


def native_entry(language: str, reference: str) -> dict[str, str]:
    name, chapter, start, end = parse_reference(reference)
    collection, book_id = book_catalog()[name]
    document = book_document(language, collection, book_id)
    story = next((s for s in document["stories"]
                  if s["id"].rsplit("-", 1)[-1] == str(chapter)), None)
    if story is None and chapter == 1 and len(document["stories"]) == 1:
        story = document["stories"][0]
    if story is None:
        raise ValueError(f"Missing native chapter: {language}/{reference}")
    units = []
    for bullet in story["summaryBullets"]:
        marker = ANCHOR.search(bullet)
        if not marker or int(marker[1]) != chapter:
            continue
        first, last = int(marker[2]), int(marker[3] or marker[2])
        if first <= end and last >= start:
            units.append((first, last, TAG.sub("", bullet[:marker.start()].rstrip())))
    if not units or any(not any(a <= number <= b for a, b, _ in units)
                        for number in range(start, end + 1)):
        raise ValueError(f"Missing native verses: {language}/{reference}")
    if any(current[0] != previous[1] + 1
           for previous, current in zip(units, units[1:])):
        raise ValueError(f"Noncontiguous native units: {language}/{reference}")
    first, last = units[0][0], units[-1][1]
    tail = f"{chapter}:{first}" + (f"-{last}" if last != first else "")
    text = " ".join(unit[2] for unit in units)
    if not text.strip() or "\ufffd" in text:
        raise ValueError(f"Empty or damaged native text: {language}/{reference}")
    return {"text": text, "ref": f"{name} {tail}"}


def calendar_reference(language: str, reference: str, reference_map: dict) -> str:
    if language == "ru" and parse_reference(reference)[0] == "Psalms":
        # Every scheduled Psalm must have an explicit reviewed crosswalk.
        rows = {row["canonicalRef"]: row["nativeRef"] for row in reference_map["ru"]}
        if reference not in rows:
            raise ValueError(f"Unreviewed Russian Psalm: {reference}")
        return rows[reference]
    return reference


def build_bank(language: str) -> dict:
    if language not in LANGUAGES:
        raise ValueError(f"Unsupported repair language: {language}")
    english = read_json(ASSETS / "daily_verses/en/daily.json")["verses"]
    if len(english) != 366:
        raise ValueError("The reference calendar must contain 366 days")
    reference_map = read_json(MAP_PATH)
    previous = read_json(ASSETS / "daily_verses" / language / "daily.json")["verses"]
    by_reference = {}
    for entry in previous:
        by_reference.setdefault(entry["ref"], []).append(entry)
    result = []
    for scheduled in english:
        reference = calendar_reference(language, scheduled["ref"], reference_map)
        expected = native_entry(language, reference)
        # Keep already-correct daily wording/spacing exactly as shipped.
        equivalent = next((entry for entry in by_reference.get(expected["ref"], [])
                           if normalized_text(entry["text"]) == normalized_text(expected["text"])), None)
        result.append(dict(equivalent or expected))
    return {"verses": result}


def emit_patch(language: str, expected: dict) -> str:
    relative = f"shared/assets/daily_verses/{language}/daily.json"
    existing = (ROOT / relative).read_text(encoding="utf-8-sig")
    repaired = json.dumps(expected, ensure_ascii=False, indent=2) + "\n"
    diff = list(difflib.unified_diff(existing.splitlines(), repaired.splitlines(), lineterm=""))
    if not diff:
        return ""
    lines = [f"*** Update File: {relative}"]
    lines.extend("@@" if line.startswith("@@") else line for line in diff[2:])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--patch", action="store_true", help="Print a patch; never write files")
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    patches = []
    failed = False
    for language in LANGUAGES:
        expected = build_bank(language)
        existing = read_json(ASSETS / "daily_verses" / language / "daily.json")
        if args.patch:
            patches.append(emit_patch(language, expected))
        else:
            matches = existing == expected
            print(f"{language}: {len(existing['verses'])}/366 entries; calendar/source match={matches}")
            failed |= not matches
    if args.patch and any(patches):
        print("*** Begin Patch\n" + "".join(patches) + "*** End Patch")
    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())
