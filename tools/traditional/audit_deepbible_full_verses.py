#!/usr/bin/env python3
"""Audit KJV whole-verse J inheritance against an exact red-letter target witness."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from fetch_deepbible_jesus_evidence import (
    APP_MARKER, BOOK_CODE, clean_source, fetch_chapter,
    presentation_equivalent, source_speech_ranges,
)


def build_report(repo_root: Path, language: str, edition_id: str, source: str) -> dict:
    authorities = []
    target_root = repo_root / "shared/assets/books/editions" / language / edition_id / "new_testament"
    kjv_root = repo_root / "shared/assets/books/editions/en/kjv1769/new_testament"
    for book_id in BOOK_CODE:
        kjv = json.loads((kjv_root / f"{book_id}.json").read_text(encoding="utf-8"))
        target = json.loads((target_root / f"{book_id}.json").read_text(encoding="utf-8"))
        target_units = {(ch["number"], v["verse"]): v for ch in target["chapters"] for v in ch["verses"]}
        for chapter in kjv["chapters"]:
            for verse in chapter["verses"]:
                text = verse["text"]
                if "[J]" not in text:
                    continue
                outside = APP_MARKER.sub("", text)
                # Removing J-tagged runs leaves only real outside-J visible text.
                import re
                outside = re.sub(r"\[J\].*?\[/J\]", "", text)
                if APP_MARKER.sub("", outside).strip():
                    continue
                key = (chapter["number"], verse["verse"])
                target_verse = target_units.get(key)
                if target_verse is None:
                    matches = [v for (c, _), v in target_units.items()
                               if c == chapter["number"] and v["verse"] <= verse["verse"] <= v.get("verseEnd", v["verse"])]
                    target_verse = matches[0] if len(matches) == 1 else None
                authorities.append({"bookId": book_id, "chapter": chapter["number"], "verse": verse["verse"],
                                    "target": target_verse})
    chapters = sorted({(row["bookId"], row["chapter"]) for row in authorities})
    fetched = {}
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = {executor.submit(fetch_chapter, book, chapter, source): (book, chapter) for book, chapter in chapters}
        for future in as_completed(futures):
            fetched[futures[future]] = future.result()
    rows = []
    for authority in authorities:
        book_id, chapter, verse = authority["bookId"], authority["chapter"], authority["verse"]
        target = authority["target"]
        base = {"bookId": book_id, "chapter": chapter, "verse": verse,
                "sourceChapterSha256": fetched[(book_id, chapter)]["sourceSha256"]}
        if target is None:
            rows.append({**base, "status": "target_missing"})
            continue
        target_plain = APP_MARKER.sub("", target["text"])
        base.update({"targetVerse": target["verse"], "targetVerseEnd": target.get("verseEnd", target["verse"]),
                     "targetText": target_plain})
        source_tagged = fetched[(book_id, chapter)]["verses"].get(verse)
        if source_tagged is None:
            rows.append({**base, "status": "source_missing"})
            continue
        cleaned = clean_source(source_tagged)
        try:
            source_plain, ranges = source_speech_ranges(cleaned)
        except ValueError as exc:
            rows.append({**base, "status": "malformed_source_j", "sourceText": cleaned,
                         "sourceMarkupError": str(exc)})
            continue
        base["sourceText"] = source_plain
        if source_plain == target_plain:
            text_status = "exact"
        elif presentation_equivalent(source_plain).rstrip(".") == presentation_equivalent(target_plain).rstrip("."):
            text_status = "presentation"
        else:
            text_status = "mismatch"
        full_source_j = bool(ranges) and not source_plain[:ranges[0][0]].strip() and not source_plain[ranges[-1][1]:].strip()
        full_source_j = full_source_j and all(not source_plain[end:start].strip() for (_, end), (start, _) in zip(ranges, ranges[1:]))
        rows.append({**base, "status": f"{text_status}_{'full_j' if full_source_j else 'partial_j' if ranges else 'no_j'}",
                     "sourceSpeechSpans": [source_plain[start:end] for start, end in ranges]})
    return {"schemaVersion": 1, "language": language, "editionId": edition_id,
            "source": source, "authorityCount": len(authorities), "chapterCount": len(chapters),
            "statusCounts": dict(Counter(row["status"] for row in rows)), "rows": rows}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", required=True)
    parser.add_argument("--edition-id", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    report = build_report(args.repo_root, args.language, args.edition_id, args.source)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "rows"}, indent=2))


if __name__ == "__main__":
    main()
