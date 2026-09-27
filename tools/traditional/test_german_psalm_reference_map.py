#!/usr/bin/env python3
"""Validate the SCH2000 -> Luther 1912 Psalm reference crosswalk.

Pass --upstream PATH to also reproduce the crosswalk from the pinned STEPBible
TVTMS source. Pass --emit-upstream PATH to print the reproduced compact rows.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unittest
from collections import defaultdict
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = Path(__file__).with_name("german_psalm_reference_map.json")
BASE_PATH = REPO_ROOT / "shared/assets/books/old_testament/de/psalms.json"
OVERLAY_PATH = (
    REPO_ROOT
    / "shared/assets/books/editions/de/luther1912/old_testament/psalms.json"
)
REF_RE = re.compile(r"^Psalms (\d+):1-(\d+)$")
TVTMS_REF_RE = re.compile(r"Psa\.(\d+):(Title|\d+(?:-\d+)?)", re.IGNORECASE)
TVTMS_ACTIONS = {
    "OneToOne",
    "SubdividedVerse",
    "MergedPrevVerse",
    "MergedFollVerse",
    "MergedVerse",
}
CROSSWIRE_REF_RE = re.compile(r"^Ps\.(\d+)\.(\d+)(?:![a-z])?$", re.IGNORECASE)


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def source_last_verses() -> dict[int, int]:
    base = load_json(BASE_PATH)
    result: dict[int, int] = {}
    for story in base["stories"]:
        if len(story["refs"]) != 1:
            raise AssertionError(f"unexpected Psalm refs in {story['id']}: {story['refs']}")
        match = REF_RE.fullmatch(story["refs"][0])
        if not match:
            raise AssertionError(f"unexpected Psalm ref syntax: {story['refs'][0]}")
        chapter, last_verse = map(int, match.groups())
        result[chapter] = last_verse
    return result


def target_verses() -> set[tuple[int, int]]:
    overlay = load_json(OVERLAY_PATH)
    return {
        (verse["chapter"], verse["verse"])
        for chapter in overlay["chapters"]
        for verse in chapter["verses"]
    }


def row_source_verses(row: dict) -> range:
    return range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1)


def row_target_verses(row: dict) -> range:
    return range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1)


def expand_rows(rows: list[dict]) -> dict[tuple[int, int], tuple[int, tuple[int, ...]]]:
    expanded: dict[tuple[int, int], tuple[int, tuple[int, ...]]] = {}
    for row in rows:
        source = list(row_source_verses(row))
        target = list(row_target_verses(row))
        if len(source) > 1 and len(target) > 1 and len(source) != len(target):
            raise AssertionError(f"ambiguous unequal multi-range row: {row}")
        for index, source_verse in enumerate(source):
            if len(source) == len(target):
                mapped_target = (target[index],)
            elif len(target) == 1:
                mapped_target = (target[0],)
            elif len(source) == 1:
                mapped_target = tuple(target)
            else:
                raise AssertionError(f"unsupported mapping row: {row}")
            key = (row["sourceChapter"], source_verse)
            if key in expanded:
                raise AssertionError(f"duplicate source mapping: {key}")
            expanded[key] = (row["targetChapter"], mapped_target)
    return expanded


def parse_tvtms_refs(raw: str, *, aliases_when_absent: bool) -> list[tuple[int, int]]:
    text = raw.strip()
    absent = bool(re.match(r"^(Absent|NoVerse|NotExist|Missing|Empty)\b", text, re.I))
    candidates: list[str]
    if absent:
        if not aliases_when_absent:
            return []
        candidates = re.findall(r"\[=([^\]]+)\]", text)
    else:
        candidates = [re.sub(r"\[[^\]]*\]", "", text)]

    refs: list[tuple[int, int]] = []
    for candidate in candidates:
        for match in TVTMS_REF_RE.finditer(candidate):
            chapter = int(match.group(1))
            verse_text = match.group(2)
            if verse_text.lower() == "title":
                refs.append((chapter, 0))
                continue
            if "-" in verse_text:
                first, last = map(int, verse_text.split("-", 1))
                refs.extend((chapter, verse) for verse in range(first, last + 1))
            else:
                refs.append((chapter, int(verse_text)))
    return refs


def build_from_tvtms(path: Path) -> dict[tuple[int, int], tuple[int, tuple[int, ...]]]:
    """Reproduce MT-numbered SCH2000 refs to merged-title KJV refs.

    TVTMS aligns content units. Its Hebrew column supplies the Masoretic source
    numbering used by SCH2000. Its English KJV column supplies the normalized
    numbering used by the eBible DELUT51 source. DELUT51 folds each unnumbered
    English Psalm title into verse 1, so English ``Title`` becomes target 1.
    """

    counts = source_last_verses()
    result: dict[tuple[int, int], set[tuple[int, int]]] = {
        (chapter, verse): {(chapter, verse)}
        for chapter, last_verse in counts.items()
        for verse in range(1, last_verse + 1)
    }
    touched: set[tuple[int, int]] = set()
    layout: dict[str, int] = {}
    in_psalms = False
    in_condensed = False

    with path.open(encoding="utf-8-sig") as handle:
        for raw_line in handle:
            line = raw_line.rstrip("\r\n")
            if line.startswith("#DataStart(Condensed)"):
                in_condensed = True
                continue
            if line.startswith("#DataStart(Expanded)"):
                break
            if not in_condensed:
                continue
            cells = line.split("\t")
            first = cells[0].strip()
            if first.startswith("$"):
                in_psalms = first.lower().startswith("$psa.")
                layout = {}
                if in_psalms:
                    for index, label in enumerate(cells):
                        label = label.strip()
                        if label in {"English KJV", "Hebrew"}:
                            layout[label] = index
                continue
            if not in_psalms:
                continue
            if first == "BIBLES":
                layout = {
                    label.strip(): index
                    for index, label in enumerate(cells)
                    if label.strip() in {"English KJV", "Hebrew"}
                }
                continue
            if first not in TVTMS_ACTIONS or not {"English KJV", "Hebrew"} <= layout.keys():
                continue

            english_cell = cells[layout["English KJV"]] if layout["English KJV"] < len(cells) else ""
            hebrew_cell = cells[layout["Hebrew"]] if layout["Hebrew"] < len(cells) else ""
            english = parse_tvtms_refs(english_cell, aliases_when_absent=False)
            hebrew = parse_tvtms_refs(hebrew_cell, aliases_when_absent=True)
            if not english or not hebrew:
                continue

            pairs: list[tuple[tuple[int, int], tuple[int, int]]]
            if len(english) == len(hebrew):
                pairs = list(zip(hebrew, english))
            elif len(hebrew) == 1:
                pairs = [(hebrew[0], target) for target in english]
            elif len(english) == 1:
                pairs = [(source, english[0]) for source in hebrew]
            else:
                raise AssertionError(
                    f"unsupported TVTMS Psalm alignment {first}: {english_cell!r} / {hebrew_cell!r}"
                )

            for source, english_target in pairs:
                if source not in result:
                    raise AssertionError(f"TVTMS source outside SCH2000 coverage: {source}")
                target = (english_target[0], max(1, english_target[1]))
                if source not in touched:
                    result[source] = set()
                    touched.add(source)
                result[source].add(target)

    return {
        source: (next(iter(targets))[0], tuple(sorted(target[1] for target in targets)))
        for source, targets in result.items()
        if len({target[0] for target in targets}) == 1
    }


def parse_crosswire_span(raw: str) -> list[tuple[int, int]]:
    endpoints = raw.strip().split("-", 1)
    parsed: list[tuple[int, int]] = []
    for endpoint in endpoints:
        match = CROSSWIRE_REF_RE.fullmatch(endpoint)
        if not match:
            raise AssertionError(f"unexpected CrossWire Psalm reference: {raw}")
        parsed.append(tuple(map(int, match.groups())))
    if len(parsed) == 1:
        return parsed
    first, last = parsed
    if first[0] != last[0] or first[1] > last[1]:
        raise AssertionError(f"unsupported CrossWire Psalm range: {raw}")
    return [(first[0], verse) for verse in range(first[1], last[1] + 1)]


def build_from_crosswire(path: Path) -> dict[tuple[int, int], tuple[int, tuple[int, ...]]]:
    """Reproduce the map from CrossWire JSword's Luther-to-KJV table."""

    counts = source_last_verses()
    result: dict[tuple[int, int], set[tuple[int, int]]] = {
        (chapter, verse): {(chapter, verse)}
        for chapter, last_verse in counts.items()
        for verse in range(1, last_verse + 1)
    }
    touched: set[tuple[int, int]] = set()
    with path.open(encoding="utf-8-sig") as handle:
        for raw_line in handle:
            line = raw_line.strip()
            if not line.startswith("Ps.") or "=" not in line:
                continue
            source_text, target_text = line.split("=", 1)
            sources = parse_crosswire_span(source_text)
            targets = parse_crosswire_span(target_text)
            if len(sources) == len(targets):
                pairs = list(zip(sources, targets))
            elif len(sources) == 1:
                pairs = [(sources[0], target) for target in targets]
            elif len(targets) == 1:
                pairs = [(source, targets[0]) for source in sources]
            else:
                raise AssertionError(f"unsupported CrossWire alignment: {line}")
            for source, target in pairs:
                if source not in result:
                    raise AssertionError(f"CrossWire source outside SCH2000 coverage: {source}")
                folded_target = (target[0], max(1, target[1]))
                if source not in touched:
                    result[source] = set()
                    touched.add(source)
                result[source].add(folded_target)
    return {
        source: (next(iter(targets))[0], tuple(sorted(target[1] for target in targets)))
        for source, targets in result.items()
        if len({target[0] for target in targets}) == 1
    }


def compact_rows(
    expanded: dict[tuple[int, int], tuple[int, tuple[int, ...]]]
) -> list[dict]:
    rows: list[dict] = []
    counts = source_last_verses()
    for chapter in range(1, 151):
        verse = 1
        last_verse = counts[chapter]
        while verse <= last_verse:
            target_chapter, target_values = expanded[(chapter, verse)]
            source_end = verse
            target_start = target_values[0]
            target_end = target_values[-1]

            if len(target_values) > 1:
                pass
            else:
                mode: str | None = None
                while source_end < last_verse:
                    next_chapter, next_targets = expanded[(chapter, source_end + 1)]
                    if next_chapter != target_chapter or len(next_targets) != 1:
                        break
                    if mode is None:
                        if next_targets[0] == target_end:
                            mode = "many-to-one"
                        elif next_targets[0] == target_end + 1:
                            mode = "linear"
                        else:
                            break
                    if mode == "many-to-one" and next_targets[0] == target_start:
                        source_end += 1
                        continue
                    if mode == "linear" and next_targets[0] == target_end + 1:
                        source_end += 1
                        target_end = next_targets[0]
                        continue
                    break

            row = {
                "sourceChapter": chapter,
                "sourceVerse": verse,
                "targetChapter": target_chapter,
                "targetVerse": target_start,
            }
            if source_end != verse:
                row["sourceVerseEnd"] = source_end
            if target_end != target_start:
                row["targetVerseEnd"] = target_end
            rows.append(row)
            verse = source_end + 1
    return rows


class GermanPsalmReferenceMapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.document = load_json(MAP_PATH)
        cls.book = cls.document["books"][0]
        cls.expanded = expand_rows(cls.book["mappings"])

    def test_metadata_and_book_scope(self) -> None:
        self.assertEqual(self.document["schemaVersion"], 1)
        self.assertEqual(self.document["language"], "de")
        self.assertEqual(self.document["baseEditionId"], "sch2000")
        self.assertEqual(self.document["editionId"], "luther1912")
        self.assertEqual(len(self.document["books"]), 1)
        self.assertEqual(self.book["bookId"], "psalms")
        self.assertIs(self.book["complete"], True)

    def test_every_declared_sch2000_verse_is_mapped_once(self) -> None:
        expected = {
            (chapter, verse)
            for chapter, last_verse in source_last_verses().items()
            for verse in range(1, last_verse + 1)
        }
        self.assertEqual(set(self.expanded), expected)

    def test_every_luther_target_exists_and_is_covered(self) -> None:
        actual_targets = target_verses()
        mapped_targets = {
            (target_chapter, target_verse)
            for target_chapter, target_verses in self.expanded.values()
            for target_verse in target_verses
        }
        self.assertEqual(mapped_targets, actual_targets)

    def test_known_non_identity_boundaries(self) -> None:
        expected = {
            (8, 5): (8, (4,)),
            (13, 1): (13, (1,)),
            (13, 2): (13, (1,)),
            (13, 3): (13, (2,)),
            (13, 6): (13, (5, 6)),
            (19, 2): (19, (1,)),
            (42, 2): (42, (1,)),
            (51, 1): (51, (1,)),
            (51, 2): (51, (1,)),
            (51, 3): (51, (1,)),
            (51, 21): (51, (19,)),
        }
        for source, target in expected.items():
            self.assertEqual(self.expanded[source], target, source)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path)
    parser.add_argument("--crosswire", type=Path)
    parser.add_argument("--emit-upstream", type=Path)
    args, unittest_args = parser.parse_known_args()
    if args.emit_upstream:
        generated = build_from_tvtms(args.emit_upstream)
        json.dump(compact_rows(generated), sys.stdout, indent=2)
        print()
        return 0
    if args.upstream:
        generated = build_from_tvtms(args.upstream)
        checked_in = expand_rows(load_json(MAP_PATH)["books"][0]["mappings"])
        if generated != checked_in:
            raise AssertionError("checked-in mappings differ from the pinned TVTMS source")
    if args.crosswire:
        generated = build_from_crosswire(args.crosswire)
        checked_in = expand_rows(load_json(MAP_PATH)["books"][0]["mappings"])
        if generated != checked_in:
            raise AssertionError("checked-in mappings differ from CrossWire Luther.properties")
    unittest.main(argv=[sys.argv[0], *unittest_args], exit=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
