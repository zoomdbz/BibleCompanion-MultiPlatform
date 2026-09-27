#!/usr/bin/env python3
"""Verify that every localized astronomy note contains the pinned catalog."""

from __future__ import annotations

import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
CATALOG = Path(__file__).with_name("eclipse_catalog_2024_2033.json")
NOTES = ROOT / "shared/assets/notes"
LANGUAGES = (
    "en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko",
    "zh-Hans", "zh-Hant", "ar", "hi",
)
MARKER = re.compile(
    r"<!--\s*eclipse:(\d{4}-\d{2}-\d{2}):(solar|lunar)-"
    r"(total|annular|partial|hybrid|penumbral)\s*-->"
)
BEGIN = "<!-- ECLIPSE_CATALOG_2024_2033_BEGIN -->"
END = "<!-- ECLIPSE_CATALOG_2024_2033_END -->"


def expected_events() -> list[tuple[str, str, str]]:
    document = json.loads(CATALOG.read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1:
        raise ValueError("Unsupported eclipse catalog schema")
    events = [(row["date"], row["body"], row["type"]) for row in document["events"]]
    if len(events) != len(set(events)):
        raise ValueError("Duplicate canonical eclipse event")
    return events


def audit() -> list[str]:
    expected = expected_events()
    failures: list[str] = []
    for language in LANGUAGES:
        path = NOTES / language / "astronomical_signs.md"
        if not path.is_file():
            failures.append(f"{language}: missing astronomical_signs.md")
            continue
        text = path.read_text(encoding="utf-8")
        if text.count(BEGIN) != 1 or text.count(END) != 1:
            failures.append(f"{language}: missing or duplicate catalog boundary")
            continue
        section = text.split(BEGIN, 1)[1].split(END, 1)[0]
        actual = MARKER.findall(section)
        if sorted(actual) != sorted(expected):
            missing = [event for event in expected if event not in actual]
            extra = [event for event in actual if event not in expected]
            failures.append(
                f"{language}: catalog content mismatch; missing={missing}, extra={extra}"
            )
        if any(source not in section for source in json.loads(CATALOG.read_text(encoding="utf-8"))["sources"]):
            failures.append(f"{language}: NASA source links incomplete")
    return failures


def main() -> int:
    failures = audit()
    if failures:
        print("\n".join(failures))
        return 1
    print(f"Eclipse catalog complete in {len(LANGUAGES)} languages: {len(expected_events())} events each")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
