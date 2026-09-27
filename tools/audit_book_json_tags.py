#!/usr/bin/env python3
"""Parse every packaged book JSON file and validate semantic-tag nesting."""

from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1] / "shared" / "assets" / "books"
TAG = re.compile(r"\[(/?)(J|DN|ADD)\]")


def strings(value: object, location: str):
    if isinstance(value, str):
        yield location, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from strings(item, f"{location}[{index}]")
    elif isinstance(value, dict):
        for key, item in value.items():
            yield from strings(item, f"{location}.{key}")


def main() -> int:
    files = 0
    string_leaves = 0
    tagged_strings = 0
    tag_counts: Counter[str] = Counter()
    errors: list[str] = []

    # Underscore-prefixed JSON files are indexes, manifests, and reference-map
    # metadata. Some manifests intentionally name literal tag delimiters in
    # separate fields, so they are not Scripture strings and must not enter a
    # per-string nesting audit.
    paths = (path for path in ROOT.rglob("*.json") if not path.name.startswith("_"))
    for path in sorted(paths):
        files += 1
        try:
            document = json.loads(path.read_text(encoding="utf-8-sig"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            errors.append(f"{path}: invalid JSON: {exc}")
            continue
        for location, text in strings(document, str(path)):
            string_leaves += 1
            matches = list(TAG.finditer(text))
            if not matches:
                continue
            tagged_strings += 1
            stack: list[str] = []
            for match in matches:
                closing, name = match.groups()
                tag_counts[("close:" if closing else "open:") + name] += 1
                if not closing:
                    stack.append(name)
                elif not stack or stack[-1] != name:
                    errors.append(f"{location}: crossing or orphan {match.group(0)}")
                    break
                else:
                    stack.pop()
            if stack:
                errors.append(f"{location}: unclosed semantic tag(s): {', '.join(stack)}")

    for name in ("J", "DN", "ADD"):
        if tag_counts[f"open:{name}"] != tag_counts[f"close:{name}"]:
            errors.append(
                f"global {name} count mismatch: "
                f"{tag_counts[f'open:{name}']} open / {tag_counts[f'close:{name}']} close"
            )

    print(json.dumps({
        "jsonFiles": files,
        "stringLeaves": string_leaves,
        "taggedStrings": tagged_strings,
        "tagCounts": dict(sorted(tag_counts.items())),
        "errors": errors,
    }, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
