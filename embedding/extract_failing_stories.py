#!/usr/bin/env python3
"""Extract story titles and takeaway text for failing benchmark story_ids."""

import json
import re
import os

CORPUS_DIR = "/mnt/Shared/Bible Companion App/embedding-project/output"
LANGS = ["ar", "de", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh-Hans", "zh-Hant"]
STORY_IDS = ["luke-15", "luke-10", "exodus-3", "ephesians-6", "john-3", "genesis-7"]

def strip_emoji(s):
    """Strip leading emoji and whitespace."""
    # Remove common emoji patterns at the start
    return re.sub(r'^[\U0001F300-\U0001FAFF\U00002702-\U000027B0\U0000FE00-\U0000FE0F\U0000200D]+\s*', '', s).strip()

results = {}  # {lang: {story_id: {"title": ..., "takeaway": ...}}}

for lang in LANGS:
    path = os.path.join(CORPUS_DIR, f"corpus_{lang}.jsonl")
    if not os.path.exists(path):
        print(f"MISSING: {path}")
        continue
    results[lang] = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            obj = json.loads(line)
            sid = obj.get("story_id", "")
            if sid not in STORY_IDS:
                continue
            if sid not in results[lang]:
                results[lang][sid] = {"title": None, "takeaway": None}
            # Grab the title from any record for this story
            if results[lang][sid]["title"] is None:
                results[lang][sid]["title"] = strip_emoji(obj.get("story_title", ""))
            # Grab takeaway text
            if obj.get("type") == "takeaway":
                results[lang][sid]["takeaway"] = obj.get("text", "")

# Print results as a table
print(f"{'Lang':<8} {'Story ID':<16} {'Story Title':<50} {'Takeaway (first 100 chars)'}")
print("=" * 180)

for lang in LANGS:
    if lang not in results:
        continue
    for sid in STORY_IDS:
        entry = results[lang].get(sid)
        if entry is None:
            print(f"{lang:<8} {sid:<16} {'*** MISSING ***':<50} {'*** MISSING ***'}")
        else:
            title = entry["title"] or "*** NO TITLE ***"
            takeaway = entry["takeaway"] or "*** NO TAKEAWAY ***"
            takeaway_short = takeaway[:100] + ("..." if len(takeaway) > 100 else "")
            print(f"{lang:<8} {sid:<16} {title:<50} {takeaway_short}")
    print("-" * 180)
