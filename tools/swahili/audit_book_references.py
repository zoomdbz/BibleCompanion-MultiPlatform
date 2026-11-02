#!/usr/bin/env python3
"""Resolve Swahili book cross-references and citations inside study notes.

This validates addresses and coverage, not the prose meaning of a citation.
The source books may still need bilingual editorial review.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
sys.path.insert(0, str(ROOT / "tools/prophecy"))
from audit_note_references import citations, validate  # noqa: E402

COLLECTIONS = ("old_testament", "new_testament", "deuterocanonical",
               "apocrypha", "pseudepigrapha")
DESCRIPTION_START = re.compile(r":\s+(?!\d)")


def main() -> int:
    failures = []
    checked = 0
    note_checked = 0
    for collection in COLLECTIONS:
        count = 0
        native_dir = ASSETS / "books" / collection / "sw"
        for path in sorted(native_dir.glob("*.json")):
            if path.stem == "_index":
                continue
            document = json.loads(path.read_text(encoding="utf-8"))
            for story in document.get("stories", []):
                for index, text in enumerate(story.get("crossRefs", [])):
                    label = f"{collection}/{path.stem}/{story['id']}/crossRefs[{index}]"
                    description = DESCRIPTION_START.search(text)
                    address = text[:description.start()] if description else text
                    refs = citations("sw", address)
                    if not refs:
                        failures.append(f"{label}: no linked book reference")
                    for ref in refs:
                        count += 1
                        for problem in validate("sw", ref):
                            failures.append(f"{label}: {ref.display}: {problem}")
                for index, note in enumerate(story.get("translationNotes", [])):
                    label = f"{collection}/{path.stem}/{story['id']}/translationNotes[{index}]"
                    for ref in citations("sw", note.get("note", "")):
                        note_checked += 1
                        for problem in validate("sw", ref):
                            failures.append(f"{label}: {ref.display}: {problem}")
        checked += count
        print(f"{collection}: {count} resolved citation endpoints")
    print(f"Checked {checked} Swahili cross-reference and {note_checked} study-note "
          f"citation endpoints; failures: {len(failures)}")
    for failure in failures[:50]:
        print(failure)
    if len(failures) > 50:
        print(f"... {len(failures) - 50} more failures")
    return int(bool(failures))


if __name__ == "__main__":
    raise SystemExit(main())
