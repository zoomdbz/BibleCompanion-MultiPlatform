#!/usr/bin/env python3
"""One-shot, fail-closed rebase of reviewed traditional heading exceptions.

The modern heading inventory was verified against rendered publishers before this
review. Only three rewritten titles still need a native-edition relocation.
All other unbound exceptions, including Arabic headings proven absent from SAB,
refer to removed/replaced titles or now point at their own current anchor, so
retaining them would fail exact-text validation.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import tempfile

from audit_heading_map_rebase import audit


ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = ROOT / "tools/traditional/edition_heading_maps.json"
LANGUAGES = {"ar", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant"}
KEEP_REWRITTEN = {
    ("de", "luther1912", "ISA", 8, 23): (
        "Verheißung des kommenden Friedefürsten", 8, 23, 9, 1,
    ),
    ("fr", "lsg1910", "PHP", 3, 1): (
        "La vraie manière d'être juste aux yeux de Dieu", 3, 1, 3, 2,
    ),
    ("zh-Hant", "cuv", "1CH", 8, 29): (
        "掃羅王的家族\n（代上9‧35－38）", 8, 29, 8, 33,
    ),
}


def main() -> int:
    review = audit(ROOT, MAP_PATH)
    original = MAP_PATH.read_bytes()
    if hashlib.sha256(original).hexdigest() != review["mapSha256"]:
        raise RuntimeError("Heading map changed during review")
    data = json.loads(original)
    rows = {
        (row["language"], row["editionId"], row["bookCode"], row["relocationIndex"]): row
        for row in review["rows"]
    }
    seen_special: set[tuple[str, str, str, int, int]] = set()
    removed = 0
    retained = 0
    editions = []
    for entry in data["editions"]:
        language = entry["language"]
        if language not in LANGUAGES:
            editions.append(entry)
            continue
        kept = []
        for index, relocation in enumerate(entry["relocations"]):
            key = (language, entry["editionId"], entry["bookCode"])
            row = rows[key + (index,)]
            old = (relocation["sourceChapter"], relocation["sourceBeforeVerse"])
            special = key + old
            if row["status"] == "bound":
                if special in KEEP_REWRITTEN:
                    title, _, _, target_chapter, target_verse = KEEP_REWRITTEN[special]
                    if relocation["sourceText"] != title or len(relocation["targets"]) != 1:
                        raise RuntimeError(f"Reviewed exception drifted: {special}")
                    target = relocation["targets"][0]
                    if (target["chapter"], target["beforeVerse"], target["text"]) != (
                        target_chapter, target_verse, title,
                    ):
                        raise RuntimeError(f"Reviewed native target drifted: {special}")
                    seen_special.add(special)
                kept.append(relocation)
                retained += 1
            elif special in KEEP_REWRITTEN:
                title, source_chapter, source_verse, target_chapter, target_verse = KEEP_REWRITTEN[special]
                base = ROOT / row["baseBook"]
                book = json.loads(base.read_text(encoding="utf-8"))
                candidates = [
                    heading for story in book["stories"]
                    if int(story["id"].rsplit("-", 1)[-1]) == source_chapter
                    for heading in story.get("headings", [])
                    if heading["beforeVerse"] == source_verse and heading["text"] == title
                ]
                if len(candidates) != 1 or len(relocation["targets"]) != 1:
                    raise RuntimeError(f"Rewritten title is not unique: {special}")
                target = relocation["targets"][0]
                if (target["chapter"], target["beforeVerse"]) != (target_chapter, target_verse):
                    raise RuntimeError(f"Reviewed native target changed: {special}")
                relocation["sourceText"] = title
                target["text"] = title
                kept.append(relocation)
                retained += 1
                seen_special.add(special)
            elif row["status"] == "exact_text_unique_relocation_review_required":
                matches = row["currentExactTextAnchors"]
                if len(matches) != 1 or len(relocation["targets"]) != 1:
                    raise RuntimeError(f"Unreviewed exact relocation: {key} {old}")
                target = relocation["targets"][0]
                if matches[0] != {"chapter": target["chapter"], "beforeVerse": target["beforeVerse"]}:
                    raise RuntimeError(f"Exact title still needs relocation: {key} {old}")
                removed += 1
            elif row["status"] in {"source_heading_removed_or_rewritten", "old_anchor_text_changed_or_replaced"}:
                removed += 1
            else:
                raise RuntimeError(f"Unreviewed audit status: {key} {old} {row['status']}")
        if kept:
            entry["relocations"] = kept
            editions.append(entry)
    if seen_special != KEEP_REWRITTEN.keys():
        raise RuntimeError(f"Missing reviewed exceptions: {KEEP_REWRITTEN.keys() - seen_special}")
    data["editions"] = editions
    output = (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if MAP_PATH.read_bytes() != original:
        raise RuntimeError("Heading map changed before write")
    with tempfile.NamedTemporaryFile(dir=MAP_PATH.parent, prefix=".heading-map-", suffix=".tmp", delete=False) as temporary:
        temporary.write(output)
        temporary_path = Path(temporary.name)
    try:
        os.replace(temporary_path, MAP_PATH)
    finally:
        temporary_path.unlink(missing_ok=True)
    print(f"Rebased heading map: removed {removed} obsolete rows, retained {retained} reviewed rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
