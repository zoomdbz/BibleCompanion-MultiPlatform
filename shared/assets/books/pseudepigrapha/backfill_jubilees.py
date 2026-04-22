#!/usr/bin/env python3
"""
Backfill missing Jubilees chapters (26-50 equivalent) into all 12 translated
jubilees.json files.  English has 50 chapters; each translation has only 25.

For every missing chapter the script copies:
  - id        : same as English
  - title     : "📜 {localised book title} {chapter#}"  (matches existing pattern)
  - refs      : copied verbatim from English
  - summaryBullets : copied verbatim from English (English text kept as placeholder)

Files are written with ensure_ascii=False, indent=2 and a trailing newline.
"""

import json
import pathlib

BASE = pathlib.Path(__file__).resolve().parent          # .../pseudepigrapha
LANGUAGES = ["ar", "de", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh-Hans", "zh-Hant"]


def chapter_num(story_id: str) -> int:
    """Extract the integer chapter number from an id like 'jubilees-4'."""
    return int(story_id.split("-")[1])


def main():
    # 1. Load English source
    en_path = BASE / "en" / "jubilees.json"
    with open(en_path, "r", encoding="utf-8") as f:
        en_data = json.load(f)
    en_stories = {s["id"]: s for s in en_data["stories"]}
    en_ids = set(en_stories.keys())

    # 2. Process each language
    for lang in LANGUAGES:
        lang_path = BASE / lang / "jubilees.json"
        with open(lang_path, "r", encoding="utf-8") as f:
            lang_data = json.load(f)

        existing_ids = {s["id"] for s in lang_data["stories"]}
        missing_ids = en_ids - existing_ids

        if not missing_ids:
            print(f"  {lang}: already has all 50 chapters -- skipped")
            continue

        # Derive the localised book title from the top-level title field
        book_title = lang_data["title"]  # e.g. "Jubileos", "ヨベル書", etc.

        # Build new story entries for every missing chapter
        for mid in missing_ids:
            en_story = en_stories[mid]
            num = chapter_num(mid)
            new_story = {
                "id": en_story["id"],
                "title": f"\U0001f4dc {book_title} {num}",   # 📜 + localised title + number
                "refs": en_story["refs"],
                "summaryBullets": en_story["summaryBullets"],
            }
            lang_data["stories"].append(new_story)

        # Sort all stories by chapter number
        lang_data["stories"].sort(key=lambda s: chapter_num(s["id"]))

        # Write back
        with open(lang_path, "w", encoding="utf-8") as f:
            json.dump(lang_data, f, ensure_ascii=False, indent=2)
            f.write("\n")

        print(f"  {lang}: added {len(missing_ids)} chapters -> {len(lang_data['stories'])} total")

    # 3. Verify every file now has exactly 50 stories
    print("\n--- Verification ---")
    all_ok = True
    for lang in ["en"] + LANGUAGES:
        lang_path = BASE / lang / "jubilees.json"
        with open(lang_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        n = len(data["stories"])
        ids_sorted = [chapter_num(s["id"]) for s in data["stories"]]
        is_sorted = ids_sorted == sorted(ids_sorted)
        status = "OK" if (n == 50 and is_sorted) else "FAIL"
        if status == "FAIL":
            all_ok = False
        print(f"  {lang}: {n} stories, sorted={is_sorted}  [{status}]")

    if all_ok:
        print("\nAll 13 files verified: 50 chapters each, correctly sorted.")
    else:
        print("\nSome files FAILED verification -- check above.")


if __name__ == "__main__":
    main()
