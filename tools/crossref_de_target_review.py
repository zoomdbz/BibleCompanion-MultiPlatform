"""Inspect and apply the audited SCH2000 target corrections.

The difference inventory is the immutable source for row identity, original
German description, and original citation. The review file records one verdict
per row. Apply changes only citation prefixes and preserves all other bytes.
"""

import argparse
from collections import defaultdict
import json
import re
from pathlib import Path

import requests
from bs4 import BeautifulSoup


ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / "tools/reports/crossref_audit/german_target_differences.json"
REVIEW = ROOT / "tools/reports/crossref_audit/german_target_review.json"
BOOK_CODES = {
    "Psalmen": "PSA", "Psalms": "PSA",
    "Hoheslied": "SNG", "Song of Songs": "SNG",
    "Epheser": "EPH", "Ephesians": "EPH",
}


def inventory():
    return json.loads(INVENTORY.read_text(encoding="utf-8"))


def target_parts(target):
    match = re.fullmatch(r"(.+?) (\d+):(\d+)(?:-(\d+))?", target)
    if not match or match.group(1) not in BOOK_CODES:
        raise ValueError(f"unsupported German target: {target}")
    book, chapter, first, last = match.groups()
    return BOOK_CODES[book], int(chapter), int(first), int(last or first)


def bible_url(target):
    book, chapter, first, last = target_parts(target)
    verses = f".{first}" if first == last else f".{first}-{last}"
    return f"https://www.bible.com/de/bible/157/{book}.{chapter}{verses}.SCH2000"


def live_verses(book, chapter):
    url = f"https://www.bible.com/de/bible/157/{book}.{chapter}.SCH2000"
    response = requests.get(url, timeout=25)
    response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    result = {}
    for element in soup.select("span[data-usfm]"):
        if element.attrs is None:
            continue
        usfm = element.get("data-usfm", "")
        if not any("__verse" in cls for cls in element.get("class", [])):
            continue
        match = re.fullmatch(rf"{book}\.{chapter}\.(\d+)", usfm)
        if not match:
            continue
        for note in element.select('[class*="__note"]'):
            note.decompose()
        number = int(match.group(1))
        fragment = element.get_text(" ", strip=True)
        fragment = re.sub(rf"^{number}\s+", "", fragment)
        if fragment:
            result[number] = (result.get(number, "") + " " + fragment).strip()
    if not result:
        raise ValueError(f"no SCH2000 verses found: {url}")
    return result


def inspect():
    cache = {}
    for index, row in enumerate(inventory()):
        old = row["local_target"]
        proposed = row["expected_native_shift_target"]
        for target in (old, proposed):
            book, chapter, _, _ = target_parts(target)
            if (book, chapter) not in cache:
                cache[(book, chapter)] = live_verses(book, chapter)
        book, chapter, first, last = target_parts(old)
        _, _, proposed_first, proposed_last = target_parts(proposed)
        verses = cache[(book, chapter)]
        old_first = verses.get(first, "MISSING")
        new_last = verses.get(proposed_last, "MISSING")
        print(f"{index:02d} {old} -> {proposed}")
        print(f"  old first {first}: {old_first[:220]}")
        if proposed_first != first:
            print(f"  new first {proposed_first}: {verses.get(proposed_first, 'MISSING')[:220]}")
        print(f"  new last {proposed_last}: {new_last[:220]}")


def reviewed_rows():
    original = inventory()
    report = json.loads(REVIEW.read_text(encoding="utf-8"))
    reviews = report["rows"]
    if len(original) != 40 or len(reviews) != 40:
        raise ValueError("review must cover all 40 inventory rows")
    indexed = {entry["index"]: entry for entry in reviews}
    if set(indexed) != set(range(40)):
        raise ValueError("review indices are missing or duplicated")
    result = []
    for index, base in enumerate(original):
        entry = indexed[index]
        target = entry["verified_target"]
        if entry["evidence_url"] != bible_url(target):
            raise ValueError(f"nonmatching evidence URL: {index}")
        correct = entry["decision"].startswith("correct_")
        if correct != (target == base["local_target"]):
            raise ValueError(f"decision/target disagreement: {index}")
        result.append((base, entry))
    return result


def apply():
    grouped = defaultdict(list)
    for base, entry in reviewed_rows():
        grouped[base["file"]].append((base, entry))
    changed = 0
    for file, selected in grouped.items():
        path = ROOT / file
        original = path.read_text(encoding="utf-8")
        data = json.loads(original)
        stories = {story["id"]: story for story in data["stories"]}
        for base, entry in selected:
            value = stories[base["story_id"]]["crossRefs"][base["crossref_index_zero_based"]]
            old = base["local_target"] + ": " + base["current_localized_description"]
            new = entry["verified_target"] + ": " + base["current_localized_description"]
            if value not in (old, new):
                raise ValueError(f"crossRef changed outside review: {file} {base['story_id']}#{base['crossref_index_zero_based']}")
        replacements = {
            (base["story_id"], base["crossref_index_zero_based"]): (base, entry)
            for base, entry in selected
        }
        lines = original.splitlines(keepends=True)
        story_id = None
        in_crossrefs = False
        crossref_index = 0
        seen = set()
        for line_number, line in enumerate(lines):
            if not in_crossrefs:
                match = re.match(r'^\s*"id":\s*"([^"]+)"\s*,?\s*$', line)
                if match:
                    story_id = match.group(1)
                if re.match(r'^\s*"crossRefs":\s*\[\s*$', line):
                    in_crossrefs = True
                    crossref_index = 0
                continue
            if re.match(r'^\s*\]', line):
                in_crossrefs = False
                continue
            match = re.match(r'^(\s*)("(?:[^"\\]|\\.)*")(,?)(\r?\n?)$', line)
            if not match:
                raise ValueError(f"unexpected crossRef format: {file}:{line_number+1}")
            key = story_id, crossref_index
            if key in replacements:
                base, entry = replacements[key]
                old = base["local_target"] + ": " + base["current_localized_description"]
                new = entry["verified_target"] + ": " + base["current_localized_description"]
                literal = match.group(2)
                value = json.loads(literal)
                if value not in (old, new):
                    raise ValueError(f"line provenance changed: {file}:{line_number+1}")
                if old != new and value == old:
                    old_prefix = '"' + base["local_target"] + ': '
                    new_prefix = '"' + entry["verified_target"] + ': '
                    if not literal.startswith(old_prefix):
                        raise ValueError(f"unexpected encoded prefix: {file}:{line_number+1}")
                    literal = new_prefix + literal[len(old_prefix):]
                    if json.loads(literal) != new:
                        raise ValueError(f"replacement did not preserve description: {file}:{line_number+1}")
                    lines[line_number] = match.group(1) + literal + match.group(3) + match.group(4)
                    changed += 1
                seen.add(key)
            crossref_index += 1
        if seen != set(replacements):
            raise ValueError(f"unmatched reviewed rows in {file}")
        updated = "".join(lines)
        if len(json.loads(updated)["stories"]) != len(data["stories"]):
            raise ValueError(f"story count changed: {file}")
        if updated != original:
            path.write_text(updated, encoding="utf-8", newline="")
    print(f"Applied {changed} target-only corrections")


def verify():
    selected = reviewed_rows()
    grouped = defaultdict(list)
    for base, entry in selected:
        grouped[base["file"]].append((base, entry))
    checked = 0
    corrected = 0
    for file, items in grouped.items():
        german = json.loads((ROOT / file).read_text(encoding="utf-8"))
        english_path = file.replace("/de/", "/en/")
        english = json.loads((ROOT / english_path).read_text(encoding="utf-8"))
        german_stories = {story["id"]: story for story in german["stories"]}
        english_stories = {story["id"]: story for story in english["stories"]}
        if len(german_stories) != len(english_stories):
            raise ValueError(f"story count mismatch: {file}")
        for story_id, story in german_stories.items():
            other = english_stories[story_id]
            if len(story.get("crossRefs", [])) != len(other.get("crossRefs", [])):
                raise ValueError(f"crossRef count mismatch: {file} {story_id}")
        for base, entry in items:
            value = german_stories[base["story_id"]]["crossRefs"][base["crossref_index_zero_based"]]
            expected = entry["verified_target"] + ": " + base["current_localized_description"]
            if value != expected:
                raise ValueError(f"target or description mismatch: {file} {base['story_id']}#{base['crossref_index_zero_based']}")
            checked += 1
            corrected += entry["verified_target"] != base["local_target"]
    alias_rows = json.loads((ROOT / "shared/assets/refs/de/book_aliases.json").read_text(encoding="utf-8"))
    aliases = {row["canon"]: row["aliases"] for row in alias_rows}
    for german, english in (("Psalmen", "Psalms"), ("Hoheslied", "Song of Songs"), ("Epheser", "Ephesians")):
        if english not in aliases[german]:
            raise ValueError(f"Linker alias missing: {german} -> {english}")
    cache = {}
    for _, entry in selected:
        target = entry["verified_target"]
        book, chapter, first, last = target_parts(target)
        if (book, chapter) not in cache:
            cache[(book, chapter)] = live_verses(book, chapter)
        if any(verse not in cache[(book, chapter)] for verse in range(first, last + 1)):
            raise ValueError(f"SCH2000 verse absent: {target}")
    print(f"Reviewed {checked}/40; corrected {corrected}; retained {checked-corrected}; English count parity OK; Linker aliases and all SCH2000 verse ranges resolve")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("inspect", "apply", "verify"))
    args = parser.parse_args()
    if args.command == "inspect":
        inspect()
    elif args.command == "apply":
        apply()
    else:
        verify()
