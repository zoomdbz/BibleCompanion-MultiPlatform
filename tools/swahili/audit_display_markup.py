#!/usr/bin/env python3
"""Check Swahili display tags and study divine-name coverage."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "shared/assets"
MARKER = re.compile(r"\[(/?)(DN|J|ADD)\]")
DIVINE_SPAN = re.compile(r"\[DN\].*?\[/DN\]", re.S)
UNMARKED_STUDY_NAME = re.compile(r"(?<![\w-])BWANA(?![\w-])|Bwana,?\s+MUNGU\b")


def strings(value, location: str):
    if isinstance(value, str):
        yield location, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from strings(item, f"{location}/{index}")
    elif isinstance(value, dict):
        for key, item in value.items():
            yield from strings(item, f"{location}/{key}")


def main() -> int:
    base_books = sorted((ROOT / "books").glob("*/sw/*.json"))
    overlays = sorted((ROOT / "books/editions/sw").rglob("*.json"))
    notes = sorted((ROOT / "notes/sw").glob("*.md"))
    daily = sorted((ROOT / "daily_verses/sw").glob("*.json"))
    paths = base_books + overlays + notes + daily + [ROOT / "notifications/sw.json"]
    errors = []
    counts = {"DN": 0, "J": 0, "ADD": 0}
    for path in paths:
        content = path.read_text(encoding="utf-8-sig")
        value = json.loads(content) if path.suffix == ".json" else content
        for location, text in strings(value, str(path.relative_to(ROOT))):
            stack = []
            for marker in MARKER.finditer(text):
                kind = marker.group(2)
                if marker.group(1):
                    if not stack or stack[-1] != kind:
                        errors.append(f"Misnested {marker.group()} at {location}")
                        break
                    stack.pop()
                else:
                    counts[kind] += 1
                    stack.append(kind)
            if stack:
                errors.append(f"Unclosed {stack} at {location}")
            if path in base_books or path in notes:
                outside = DIVINE_SPAN.sub("", text)
                if UNMARKED_STUDY_NAME.search(outside):
                    errors.append(f"Unmarked Swahili divine-name typography at {location}")
    print(f"Checked {len(paths)} Swahili asset files; tag opens {counts}; failures: {len(errors)}")
    for error in errors[:25]:
        print(error)
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
