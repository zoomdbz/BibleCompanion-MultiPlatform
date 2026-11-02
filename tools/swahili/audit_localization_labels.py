#!/usr/bin/env python3
"""Find English labels and explanatory glosses still displayed in Swahili.

This catches high-confidence English descriptors. It does not judge translation
quality or treat proper names and transliterations as English prose.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
sys.path.insert(0, str(ROOT / "tools/prophecy"))
from audit_note_references import alias_pattern  # noqa: E402

COLLECTIONS = ("old_testament", "new_testament", "deuterocanonical",
               "apocrypha", "pseudepigrapha")
ENGLISH_ORIGINAL_LABEL = re.compile(r"(?<!\w)(?:Hebrew|Greek|Aramaic|Latin|Syriac)(?:\s*\([^:]*\))?:")
ENGLISH_ORIGINAL_GLOSS = re.compile(
    r"Kigiriki \(LXX\)|\(var\.\)|\bfrom Hebrew\b|"
    r"\b(?:literal(?:ly)?|plural|singular|sense|means?|meaning|rendered|"
    r"variant|word|phrase|name|title|root|compare|related|the opposite of|"
    r"to pluck out|son of|daughter of|of God|God is|I have asked)\b|"
    r"\b(?:and|or|the|of|to)\s+(?:the|a|God|Hebrew|Greek|Latin|Aramaic|"
    r"king|Lord|people|heaven|earth|word|name|heart|father|mother|house|"
    r"light|life|death|peace)\b", re.I)
DESCRIPTION_START = re.compile(r":\s+(?!\d)")


def main() -> int:
    titles = {}
    for collection in COLLECTIONS:
        for book_id, title in json.loads((ASSETS / "books" / collection / "sw/_index.json").read_text()):
            if book_id:
                titles[(collection, book_id)] = title
    owners, pattern = alias_pattern("sw")
    bad_labels = []
    bad_originals = []
    bad_glosses = []
    checked = 0
    for collection in COLLECTIONS:
        for path in sorted((ASSETS / "books" / collection / "sw").glob("*.json")):
            if path.stem == "_index":
                continue
            document = json.loads(path.read_text(encoding="utf-8"))
            for story in document.get("stories", []):
                for index, value in enumerate(story.get("crossRefs", [])):
                    checked += 1
                    descriptor = DESCRIPTION_START.search(value)
                    prefix = value[:descriptor.start()] if descriptor else value
                    for match in pattern.finditer(prefix):
                        book_id, target_collection = owners[match["book"].casefold()]
                        native = titles[(target_collection, book_id)]
                        if match["book"].casefold() != native.casefold():
                            bad_labels.append((collection, path.stem, story["id"], index,
                                               match["book"], native))
                for index, note in enumerate(story.get("translationNotes", [])):
                    original = note.get("original", "")
                    match = ENGLISH_ORIGINAL_LABEL.search(original)
                    if match:
                        bad_originals.append((collection, path.stem, story["id"], index, match[0]))
                    match = ENGLISH_ORIGINAL_GLOSS.search(original)
                    if match:
                        bad_glosses.append((collection, path.stem, story["id"], index, match[0]))
    print(f"Checked {checked} Swahili cross-reference labels; "
          f"noncanonical display names: {len(bad_labels)}; "
          f"English original-field labels: {len(bad_originals)}; "
          f"English glosses: {len(bad_glosses)}")
    for row in bad_labels[:30]:
        print("BOOK LABEL", *row, sep=" | ")
    for row in bad_originals[:30]:
        print("ORIGINAL LABEL", *row, sep=" | ")
    for row in bad_glosses[:30]:
        print("ORIGINAL GLOSS", *row, sep=" | ")
    return int(bool(bad_labels or bad_originals or bad_glosses))


if __name__ == "__main__":
    raise SystemExit(main())
