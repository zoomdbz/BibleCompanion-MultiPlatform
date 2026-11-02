#!/usr/bin/env python3
"""Normalize Swahili in-book reference titles and original-language labels.

Default mode previews affected files. --write changes only `crossRefs` book
names in their leading citation prefix and `translationNotes.original` English
language labels. Original Hebrew/Greek glyphs and romanized forms are not
translated by this mechanical pass; review residual English glosses by hand.
"""

from __future__ import annotations

import argparse
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
DESCRIPTION_START = re.compile(r":\s+(?!\d)")
LANGUAGE_LABELS = {
    "Hebrew": "Kiebrania", "Greek": "Kigiriki", "Aramaic": "Kiaramu",
    "Latin": "Kilatini", "Syriac": "Kisiria",
}
LABEL_PATTERN = re.compile(r"(?<!\w)(Hebrew|Greek|Aramaic|Latin|Syriac)(?=\s*(?:\([^:]*\))?:)")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="write the reviewed changes to Swahili book JSON files")
    parser.add_argument("--collection", choices=COLLECTIONS, action="append",
                        help="limit changes to one or more book collections")
    args = parser.parse_args()
    selected = args.collection or COLLECTIONS
    titles = {}
    for collection in COLLECTIONS:
        index = json.loads((ASSETS / "books" / collection / "sw/_index.json").read_text(encoding="utf-8"))
        titles.update({(collection, book_id): title for book_id, title in index if book_id})
    owners, pattern = alias_pattern("sw")
    files_changed = book_labels = source_labels = 0
    for collection in selected:
        for path in sorted((ASSETS / "books" / collection / "sw").glob("*.json")):
            if path.stem == "_index":
                continue
            document = json.loads(path.read_text(encoding="utf-8"))
            changed = False
            for story in document.get("stories", []):
                for index, value in enumerate(story.get("crossRefs", [])):
                    descriptor = DESCRIPTION_START.search(value)
                    prefix = value[:descriptor.start()] if descriptor else value
                    matches = list(pattern.finditer(prefix))
                    for match in reversed(matches):
                        target_id, target_collection = owners[match["book"].casefold()]
                        title = titles[(target_collection, target_id)]
                        if value[match.start("book"):match.end("book")] != title:
                            value = value[:match.start("book")] + title + value[match.end("book"):]
                            book_labels += 1
                            changed = True
                    story["crossRefs"][index] = value
                for note in story.get("translationNotes", []):
                    original = note.get("original", "")
                    localized, count = LABEL_PATTERN.subn(lambda m: LANGUAGE_LABELS[m[1]], original)
                    localized, lxx_count = re.subn(r"Kigiriki \(LXX\)", "Kigiriki (Septuajinti)", localized)
                    localized, variant_count = re.subn(r"\(var\.\)", "(umbo tofauti)", localized)
                    count += lxx_count + variant_count
                    if count:
                        note["original"] = localized
                        source_labels += count
                        changed = True
            if changed:
                files_changed += 1
                if args.write:
                    path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{'Updated' if args.write else 'Would update'} {files_changed} Swahili book files: "
          f"{book_labels} citation book labels and {source_labels} original-language labels")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
