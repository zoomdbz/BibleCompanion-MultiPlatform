#!/usr/bin/env python3
"""Repair reviewed trailing-reference metadata without changing source wording.

The affected Jubilees translations retain the same one-bullet-per-verse layout
as the English source asset, but a small set of translated bullets lost only
their trailing address marker. Korean 2 Esdras kept its markers inside ``[J]``
instead of after the closing tag. This tool validates those exact states and
performs minimal string substitutions in the existing JSON files.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
TRAILING_MARKER = re.compile(r"\s*\(\d+:\d+(?:-\d+)?\)\.?$")

# Each tuple is language, chapter, and the verses whose translated bullets
# lost only their address suffix. Bullet index equals verse minus one in this
# reviewed Jubilees corpus.
JUBILEES_REPAIRS = (
    ("ar", 12, (2, 3, 4, 5)),
    ("ar", 15, (5, 6, 7, 8)),
    ("ar", 22, (16,)),
    ("de", 22, (16,)),
    ("hi", 12, (2, 3, 4, 5)),
    ("hi", 15, (5, 6, 7, 8)),
    ("hi", 19, (25,)),
    ("hi", 22, (11, 12, 13, 14, 15)),
    ("hi", 31, (15, 16, 17)),
    ("ja", 12, (2, 3, 4, 5)),
    ("ja", 15, (5, 6, 7, 8)),
    ("ko", 12, (2, 3, 4, 5)),
    ("ko", 15, (5, 6, 7, 8)),
    ("ko", 22, (12, 15)),
)


class RepairError(RuntimeError):
    pass


def read_document(path: Path) -> tuple[str, dict]:
    raw = path.read_bytes().decode("utf-8")
    document = json.loads(raw.lstrip("\ufeff"))
    if not isinstance(document, dict):
        raise RepairError(f"JSON root is not an object: {path}")
    return raw, document


def story(document: dict, story_id: str, path: Path) -> dict:
    matches = [row for row in document.get("stories", []) if row.get("id") == story_id]
    if len(matches) != 1:
        raise RepairError(f"Expected one story {story_id} in {path}; found {len(matches)}")
    return matches[0]


def replace_json_string(raw: str, old: str, new: str, label: str) -> str:
    old_json = json.dumps(old, ensure_ascii=False)
    new_json = json.dumps(new, ensure_ascii=False)
    count = raw.count(old_json)
    if count != 1:
        raise RepairError(f"Expected one exact JSON string for {label}; found {count}")
    return raw.replace(old_json, new_json, 1)


def repair_jubilees(repo_root: Path, apply: bool) -> int:
    books = repo_root / "shared/assets/books/pseudepigrapha"
    english_path = books / "en/jubilees.json"
    _, english = read_document(english_path)
    edits = 0
    by_language: dict[str, list[tuple[int, tuple[int, ...]]]] = {}
    for language, chapter, verses in JUBILEES_REPAIRS:
        by_language.setdefault(language, []).append((chapter, verses))

    for language, chapter_rows in sorted(by_language.items()):
        path = books / language / "jubilees.json"
        raw, document = read_document(path)
        original = raw
        for chapter, verses in chapter_rows:
            story_id = f"jubilees-{chapter}"
            english_story = story(english, story_id, english_path)
            translated_story = story(document, story_id, path)
            english_bullets = english_story.get("summaryBullets")
            translated_bullets = translated_story.get("summaryBullets")
            if not isinstance(english_bullets, list) or not isinstance(translated_bullets, list):
                raise RepairError(f"Missing summaryBullets: {path} {story_id}")
            if len(english_bullets) != len(translated_bullets):
                raise RepairError(f"Bullet inventory differs: {path} {story_id}")
            for verse in verses:
                index = verse - 1
                expected = f"({chapter}:{verse})."
                english_text = english_bullets[index]
                translated_text = translated_bullets[index]
                if not isinstance(english_text, str) or not english_text.endswith(expected):
                    raise RepairError(
                        f"English control marker differs: {english_path} {story_id} bullet {index}"
                    )
                if not isinstance(translated_text, str):
                    raise RepairError(f"Non-string bullet: {path} {story_id} bullet {index}")
                if translated_text.endswith(expected):
                    continue
                if TRAILING_MARKER.search(translated_text):
                    raise RepairError(
                        f"Unexpected existing marker: {path} {story_id} bullet {index}"
                    )
                separator = "" if language == "ja" else " "
                repaired = f"{translated_text}{separator}{expected}"
                raw = replace_json_string(
                    raw, translated_text, repaired, f"{language} {story_id} bullet {index}"
                )
                translated_bullets[index] = repaired
                edits += 1
        if apply and raw != original:
            # Parse before replacing the original bytes. This also proves the
            # substitutions changed JSON string contents only.
            json.loads(raw.lstrip("\ufeff"))
            path.write_bytes(raw.encode("utf-8"))
    return edits


def repair_korean_2_esdras(repo_root: Path, apply: bool) -> int:
    path = repo_root / "shared/assets/books/deuterocanonical/ko/2_esdras.json"
    raw, document = read_document(path)
    original = raw
    chapter = story(document, "2_esdras-7", path)
    bullets = chapter.get("summaryBullets")
    if not isinstance(bullets, list):
        raise RepairError(f"Missing summaryBullets: {path} 2_esdras-7")
    edits = 0
    for verse in (28, 29):
        index = verse - 1
        text = bullets[index]
        expected = f"(7:{verse})."
        if not isinstance(text, str):
            raise RepairError(f"Non-string bullet: {path} 2_esdras-7 bullet {index}")
        if text.endswith(expected):
            continue
        old_suffix = f" {expected}[/J]"
        if not text.endswith(old_suffix):
            raise RepairError(f"Unexpected Jesus-tag marker state: {path} 2_esdras-7 bullet {index}")
        repaired = f"{text[:-len(old_suffix)]}[/J] {expected}"
        raw = replace_json_string(raw, text, repaired, f"ko 2_esdras-7 bullet {index}")
        bullets[index] = repaired
        edits += 1
    if apply and raw != original:
        json.loads(raw.lstrip("\ufeff"))
        path.write_bytes(raw.encode("utf-8"))
    return edits


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="write the reviewed metadata repairs")
    args = parser.parse_args()
    edits = repair_jubilees(ROOT, args.apply) + repair_korean_2_esdras(ROOT, args.apply)
    action = "Applied" if args.apply else "Proposed"
    print(f"{action} {edits} trailing-reference metadata repair(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
