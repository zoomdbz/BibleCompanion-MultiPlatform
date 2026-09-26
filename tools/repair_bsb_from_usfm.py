#!/usr/bin/env python3
"""Apply narrowly reviewed BSB text and red-letter repairs from supplied USFM.

The default mode is read-only. ``--apply`` changes only English canonical
summary bullets. It preserves app-specific ``[DN]`` and ``[ADD]`` spans while
rebuilding ``[J]`` spans from the BSB ``\\wj`` markers.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
import unicodedata

from audit_scripture_sources import (
    BOOKS,
    TRAILING_REFERENCE,
    j_mask,
    parse_app_book,
    parse_bsb_usfm,
)


INLINE_TAG = re.compile(r"\[(/?)(J|DN|ADD)\]")
TEXT_FIXES = {
    ("acts", 26, 14),
    ("revelation", 1, 11),
}
SOURCE_TEXT_REPLACEMENTS = {
    # The supplied export drops the display space occupied by a removed
    # footnote marker. Bible.com renders the quotation with this space.
    ("revelation", 1, 11): ("saying,“Write", "saying, “Write"),
}
RANGE_MERGES = {
    ("3_john", 1, 14, 15),
    ("revelation", 12, 17, 18),
}


class RepairError(RuntimeError):
    pass


def normalize(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).split())


def semantic_masks(
    marked: str,
    *,
    allow_open_j: bool = False,
) -> tuple[str, dict[str, tuple[bool, ...]]]:
    states = {"J": False, "DN": False, "ADD": False}
    masks: dict[str, list[bool]] = {key: [] for key in states}
    plain: list[str] = []
    cursor = 0
    for match in INLINE_TAG.finditer(unicodedata.normalize("NFC", marked)):
        segment = marked[cursor:match.start()]
        plain.append(segment)
        for character in segment:
            if not character.isspace():
                for key, state in states.items():
                    masks[key].append(state)
        states[match.group(2)] = not bool(match.group(1))
        cursor = match.end()
    segment = marked[cursor:]
    plain.append(segment)
    for character in segment:
        if not character.isspace():
            for key, state in states.items():
                masks[key].append(state)
    if states["DN"] or states["ADD"] or (states["J"] and not allow_open_j):
        raise RepairError(f"unclosed inline tag in {marked!r}")
    return normalize("".join(plain)), {key: tuple(value) for key, value in masks.items()}


def render_with_masks(plain: str, masks: dict[str, tuple[bool, ...]]) -> str:
    tag_order = ("J", "DN", "ADD")
    expected = sum(not character.isspace() for character in plain)
    if any(len(mask) != expected for mask in masks.values()):
        raise RepairError("semantic mask length does not match plain text")
    active: list[str] = []
    output: list[str] = []
    character_index = 0
    for character in plain:
        if not character.isspace():
            desired = [tag for tag in tag_order if masks[tag][character_index]]
            common = 0
            while common < min(len(active), len(desired)) and active[common] == desired[common]:
                common += 1
            for tag in reversed(active[common:]):
                output.append(f"[/{tag}]")
            for tag in desired[common:]:
                output.append(f"[{tag}]")
            active = desired
            character_index += 1
        output.append(character)
    for tag in reversed(active):
        output.append(f"[/{tag}]")
    return "".join(output)


def merge_source_j(app_marked: str, source_marked: str, source_plain: str) -> str:
    app_plain, app_masks = semantic_masks(app_marked)
    # USFM \wj spans may cross verse boundaries. The source parser records
    # that continuing state without manufacturing a closing tag per verse.
    parsed_source_plain, source_masks = semantic_masks(source_marked, allow_open_j=True)
    if parsed_source_plain != source_plain:
        raise RepairError("source marked/plain representations disagree")
    if sum(not c.isspace() for c in app_plain) != sum(not c.isspace() for c in source_plain):
        raise RepairError("reviewed text fix changes non-whitespace characters")
    masks = {
        "J": source_masks["J"],
        "DN": app_masks["DN"],
        "ADD": app_masks["ADD"],
    }
    return render_with_masks(source_plain, masks)


def bullet_parts(raw: str) -> tuple[str, tuple[int, int, int]]:
    match = TRAILING_REFERENCE.search(raw)
    if match is None:
        raise RepairError(f"Scripture bullet lacks a trailing reference: {raw[:100]!r}")
    start = int(match.group(2))
    end = int(match.group(3) or start)
    return normalize(raw[:match.start()]), (int(match.group(1)), start, end)


def load_payload(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(value, dict):
        raise RepairError(f"expected an object in {path}")
    return value


def find_bullet(payload: dict, chapter: int, start: int, end: int) -> tuple[list[str], int, str]:
    matches: list[tuple[list[str], int, str]] = []
    for story in payload.get("stories", []):
        bullets = story.get("summaryBullets", [])
        for index, raw in enumerate(bullets):
            _marked, ref = bullet_parts(raw)
            if ref == (chapter, start, end):
                matches.append((bullets, index, raw))
    if len(matches) != 1:
        raise RepairError(f"expected one bullet for {chapter}:{start}-{end}, found {len(matches)}")
    return matches[0]


def repair_book(
    app_path: Path,
    source_path: Path,
    book: str,
    *,
    write: bool,
) -> list[str]:
    payload = load_payload(app_path)
    app = parse_app_book(app_path)
    source = parse_bsb_usfm(source_path)
    changes: list[str] = []

    for key in sorted(set(app).intersection(source)):
        app_verse, source_verse = app[key], source[key]
        ref = (book, key[0], key[1])
        text_fix = ref in TEXT_FIXES
        source_marked = source_verse.marked
        source_plain = source_verse.plain
        replacement = SOURCE_TEXT_REPLACEMENTS.get(ref)
        if replacement is not None:
            old, new = replacement
            source_marked = source_marked.replace(old, new)
            source_plain = source_plain.replace(old, new)
        if app_verse.plain != source_plain and not text_fix:
            continue
        if app_verse.plain == source_plain and j_mask(app_verse.marked) == j_mask(source_marked):
            continue
        bullets, index, raw = find_bullet(payload, key[0], key[1], key[1])
        app_marked, _ = bullet_parts(raw)
        repaired = merge_source_j(app_marked, source_marked, source_plain)
        bullets[index] = f"{repaired} ({key[0]}:{key[1]})."
        changes.append(f"{book} {key[0]}:{key[1]}: " + ("text/J" if text_fix else "J"))

    for merge_book, chapter, start, end in sorted(RANGE_MERGES):
        if merge_book != book:
            continue
        first_bullets, first_index, first_raw = find_bullet(payload, chapter, start, start)
        second_bullets, second_index, second_raw = find_bullet(payload, chapter, end, end)
        if first_bullets is not second_bullets or second_index != first_index + 1:
            raise RepairError(f"{book} {chapter}:{start}-{end} bullets are not adjacent")
        first_marked, _ = bullet_parts(first_raw)
        second_marked, _ = bullet_parts(second_raw)
        combined_app = normalize(f"{first_marked} {second_marked}")
        source_verse = source[(chapter, start)]
        combined = merge_source_j(combined_app, source_verse.marked, source_verse.plain)
        first_bullets[first_index] = f"{combined} ({chapter}:{start}-{end})."
        del first_bullets[second_index]
        changes.append(f"{book} {chapter}:{start}-{end}: source range")

    if changes and write:
        app_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
    return changes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bsb-source", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    planned: list[tuple[Path, Path, str]] = []
    for code, collection, book in BOOKS:
        app_path = repo / "shared" / "assets" / "books" / collection / "en" / f"{book}.json"
        source_path = args.bsb_source / f"{code}.usfm"
        app = parse_app_book(app_path)
        source = parse_bsb_usfm(source_path)
        has_j_change = any(
            app[key].plain == source[key].plain and j_mask(app[key].marked) != j_mask(source[key].marked)
            for key in set(app).intersection(source)
        )
        has_text_fix = False
        for fix_book, chapter, verse in TEXT_FIXES:
            if fix_book != book or (chapter, verse) not in source or (chapter, verse) not in app:
                continue
            expected = source[(chapter, verse)].plain
            replacement = SOURCE_TEXT_REPLACEMENTS.get((book, chapter, verse))
            if replacement is not None:
                expected = expected.replace(*replacement)
            if app[(chapter, verse)].plain != expected:
                has_text_fix = True
                break
        has_merge = any(
            merge_book == book and
            (chapter, start) in app and
            (chapter, start) in source and
            app[(chapter, start)].plain != source[(chapter, start)].plain
            for merge_book, chapter, start, _end in RANGE_MERGES
        )
        if has_j_change or has_text_fix or has_merge:
            planned.append((app_path, source_path, book))
    changes: list[str] = []
    for app_path, source_path, book in planned:
        changes.extend(repair_book(app_path, source_path, book, write=args.apply))
    print(json.dumps({
        "mode": "applied" if args.apply else "read-only",
        "books": [item[2] for item in planned],
        "changes": changes,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RepairError, OSError, UnicodeError, ValueError, KeyError) as exc:
        print(f"BSB repair failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
