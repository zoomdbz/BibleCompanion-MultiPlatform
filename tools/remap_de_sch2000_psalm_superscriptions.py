#!/usr/bin/env python3
"""Remap German Psalm superscriptions to SCH2000 verse numbering.

This is deliberately a structural migration. It moves the existing German
superscription text into verse 1, increments the existing body markers and
heading targets, and updates German references. It never downloads or rewrites
Scripture wording.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PSALMS = ROOT / "shared/assets/books/old_testament/de/psalms.json"
GERMAN_ASSETS = ROOT / "shared/assets"

# In SCH2000 these superscriptions are numbered as verse 1. Four of these
# chapters (51, 52, 54, 60) also have an unrelated missing body verse; this
# migration leaves those gaps for the licensed Scripture importer.
CHAPTERS = {
    3, 4, 5, 6, 7, 8, 9, 12, 18, 19, 20, 21, 22, 30, 31, 34, 36, 38, 39,
    40, 41, 42, 44, 45, 46, 47, 48, 49, 51, 52, 53, 54, 55, 56, 57, 58, 59,
    60, 61, 62, 63, 64, 65, 67, 68, 69, 70, 75, 76, 77, 80, 81, 83, 84, 85,
    88, 89, 92, 102, 108, 140, 142,
}

MARKER = re.compile(r"\((\d+):(\d+)\)\.?$")
FULL_REFERENCE = re.compile(
    r"(?i)\b(Psalmen?|Psalms|Ps)(\s+)(\d{1,3})(\s*[:,]\s*)"
    r"(\d{1,3})(?:(\s*[-–]\s*)(\d{1,3}))?"
)
TITLE_CONTEXT = re.compile(
    r"(?i)Überschrift|überschrieben|Vorsänger|Miktam|Maskil|Saitenspiel|"
    r"Ein Psalm Davids|Von den Söhnen Korahs"
)
BARE_VERSE = re.compile(r"(?i)\b(Vers(?:e|en)?|V\.)(\s+)(\d{1,3})")


def remap_marker(text: str, chapter: int) -> str:
    match = MARKER.search(text)
    if not match or int(match.group(1)) != chapter:
        raise RuntimeError(f"unexpected Psalm {chapter} marker: {text!r}")
    verse = int(match.group(2)) + 1
    return text[: match.start()] + f"({chapter}:{verse})."


def reference_context(text: str, start: int, end: int) -> str:
    return text[max(0, start - 180) : min(len(text), end + 220)]


def remap_full_references(text: str) -> str:
    def replace(match: re.Match[str]) -> str:
        chapter = int(match.group(3))
        if chapter not in CHAPTERS:
            return match.group(0)
        # "Psalmen 39, 62 und 77" is a chapter list, not Psalm 39:62.
        if "," in match.group(4) and re.search(r"\s$", match.group(4)):
            return match.group(0)
        first = int(match.group(5))
        last = int(match.group(7)) if match.group(7) else None
        context = reference_context(text, match.start(), match.end())
        title_reference = first == 1 and bool(TITLE_CONTEXT.search(context))
        if not title_reference:
            first += 1
        if last is not None:
            last += 1
        suffix = "" if last is None else f"{match.group(6)}{last}"
        return (
            f"{match.group(1)}{match.group(2)}{chapter}{match.group(4)}"
            f"{first}{suffix}"
        )

    return FULL_REFERENCE.sub(replace, text)


def remap_story_metadata(value, chapter: int):
    if isinstance(value, str):
        value = remap_full_references(value)
        self_reference = re.compile(
            rf"(?<!\d)({chapter})(\s*[:,]\s*)(\d{{1,3}})",
        )

        def replace_self_reference(match: re.Match[str]) -> str:
            prefix = value[max(0, match.start() - 12) : match.start()]
            if re.search(r"(?i)(?:Psalmen?|Psalms|Ps)\s*$", prefix):
                return match.group(0)
            return f"{match.group(1)}{match.group(2)}{int(match.group(3)) + 1}"

        value = self_reference.sub(replace_self_reference, value)
        value = BARE_VERSE.sub(
            lambda m: f"{m.group(1)}{m.group(2)}{int(m.group(3)) + 1}", value
        )
        return value
    if isinstance(value, list):
        return [remap_story_metadata(item, chapter) for item in value]
    if isinstance(value, dict):
        return {key: remap_story_metadata(item, chapter) for key, item in value.items()}
    return value


def migrate_psalms() -> tuple[int, int]:
    data = json.loads(PSALMS.read_text(encoding="utf-8"))
    moved = 0
    shifted = 0
    found: set[int] = set()
    for story in data["stories"]:
        match = re.fullmatch(r"psalms-(\d+)", story["id"])
        if not match:
            continue
        chapter = int(match.group(1))
        if chapter not in CHAPTERS:
            continue
        found.add(chapter)
        superscription = story.get("superscription")
        if not superscription:
            raise RuntimeError(f"Psalm {chapter} has no superscription to migrate")

        old_bullets = story["summaryBullets"]
        new_bullets = [f"{superscription} ({chapter}:1)."]
        new_bullets.extend(remap_marker(bullet, chapter) for bullet in old_bullets)
        story["summaryBullets"] = new_bullets
        moved += 1
        shifted += len(old_bullets)
        del story["superscription"]

        for heading in story.get("headings", []):
            heading["beforeVerse"] += 1

        # Update same-Psalm references in notes and prose. Scripture bullets,
        # headings, and the canonical range are handled separately.
        for key in list(story):
            if key in {"summaryBullets", "headings", "refs", "id", "title"}:
                continue
            story[key] = remap_story_metadata(story[key], chapter)

        old_last = max(int(MARKER.search(bullet).group(2)) for bullet in old_bullets)
        story["refs"] = [f"Psalms {chapter}:1-{old_last + 1}"]

    if found != CHAPTERS:
        raise RuntimeError(f"missing Psalms: {sorted(CHAPTERS - found)}")
    PSALMS.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return moved, shifted


def migrate_other_german_references() -> tuple[int, int]:
    changed_files = 0
    changed_refs = 0
    for path in sorted(GERMAN_ASSETS.glob("**/de/*")):
        if not path.is_file() or path == PSALMS:
            continue
        original = path.read_text(encoding="utf-8")
        updated = remap_full_references(original)
        if updated == original:
            continue
        changed_refs += sum(
            1
            for before, after in zip(
                FULL_REFERENCE.findall(original), FULL_REFERENCE.findall(updated)
            )
            if before != after
        )
        path.write_text(updated, encoding="utf-8")
        changed_files += 1
    return changed_files, changed_refs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if not args.apply:
        parser.error("this migration requires --apply")
    moved, shifted = migrate_psalms()
    files, refs = migrate_other_german_references()
    print(
        f"moved_superscriptions={moved} shifted_body_markers={shifted} "
        f"reference_files={files} changed_reference_matches={refs}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
