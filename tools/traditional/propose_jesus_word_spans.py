#!/usr/bin/env python3
"""Read-only review queue for KJV-wj mixed verses in one target edition.

Suggestions are grammar/punctuation leads, not evidence of completed review.
Never feed this output directly to the reviewed ledger or importer.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path

from import_traditional_editions import BOOKS, base_jesus_ranges, overlaps
from jesus_word_spans import sha256_text


INTRODUCER = {
    "de": re.compile(r"\b(?:sprach|sagte|antwortete|rief|spricht|sagen|sagte er|redete)\b", re.IGNORECASE),
    "es": re.compile(r"\b(?:dijo|dice|respondió|respondiendo|clamó|habló|diciendo)\b", re.IGNORECASE),
    "it": re.compile(r"\b(?:disse|dice|rispose|rispondendo|gridò|dicendo)\b", re.IGNORECASE),
    "pt": re.compile(r"\b(?:disse|diz|respondendo|clamou|dizendo|falou)\b", re.IGNORECASE),
    "ru": re.compile(r"\b(?:сказал|говорит|сказав|воззвал|отвечал|говоря|сказала)\b", re.IGNORECASE),
}
TRAILING_REF = re.compile(r"\((\d+):(\d+)(?:[-–](\d+))?\)\.?\s*$")
J_SPAN = re.compile(r"\[J\](.*?)\[/J\]", re.DOTALL)


def remove_jesus_tags(text: str) -> str:
    return text.replace("[J]", "").replace("[/J]", "")


def speech_shape(text: str) -> str:
    spans = list(J_SPAN.finditer(text))
    if not spans:
        return "none"
    outside = J_SPAN.sub("", text)
    outside = re.sub(r"\[/?(?:DN|ADD)\]", "", outside)
    if not outside.strip():
        return "full"
    if len(spans) > 1:
        return "multiple"
    if text.rstrip().endswith("[/J]"):
        return "suffix"
    if text.lstrip().startswith("[J]"):
        return "prefix"
    return "middle"


def modern_index(repo_root: Path, language: str, book_id: str) -> dict[tuple[int, int], dict]:
    path = repo_root / "shared/assets/books/new_testament" / language / f"{book_id}.json"
    if not path.is_file():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    found: dict[tuple[int, int], dict] = {}
    for story in payload.get("stories", []):
        for bullet in story.get("summaryBullets", []):
            match = TRAILING_REF.search(bullet)
            if not match:
                continue
            chapter, start = int(match[1]), int(match[2])
            end = int(match[3] or match[2])
            scripture = bullet[:match.start()].strip()
            for verse in range(start, end + 1):
                # A range bullet is not an independent per-verse boundary.
                if (chapter, verse) not in found:
                    found[(chapter, verse)] = {
                        "shape": speech_shape(scripture) if start == end else "range",
                        "textSha256": sha256_text(scripture),
                    }
    return found


def propose(raw: str, kjv_shape: str, language: str, modern_shape: str) -> tuple[list[str], str, str]:
    plain = re.sub(r"\[/?(?:DN|ADD)\]", "", raw)
    if kjv_shape == "suffix" and ":" in plain:
        left, right = plain.split(":", 1)
        speech = right.lstrip()
        if speech and INTRODUCER.get(language, re.compile(r"$^")).search(left):
            confidence = "high" if modern_shape in {"suffix", "full"} else "medium"
            return [speech], confidence, "First target colon follows a language-specific speech introducer; inspect any later narrator clauses."
        if speech:
            return [speech], "low", "Colon exists, but no target-language speech introducer was detected."
    if kjv_shape == "prefix":
        match = re.search(r"[.!?](?:\s|$)", plain)
        if match:
            return [plain[:match.end()].rstrip()], "low", "KJV speech leads the verse; inspect the first target sentence boundary."
    return [], "low", "Multiple or internal speech intervals require direct language-specific review."


def build_queue(repo_root: Path, language: str, edition_id: str) -> dict:
    rows: list[dict] = []
    for _, collection, book_id in BOOKS:
        if collection != "new_testament":
            continue
        target_path = repo_root / "shared/assets/books/editions" / language / edition_id / collection / f"{book_id}.json"
        kjv_path = repo_root / "shared/assets/books/editions/en/kjv1769" / collection / f"{book_id}.json"
        target = json.loads(target_path.read_text(encoding="utf-8"))
        kjv = json.loads(kjv_path.read_text(encoding="utf-8"))
        _, mixed = base_jesus_ranges(repo_root, collection, book_id)
        modern = modern_index(repo_root, language, book_id)
        kjv_verses = {(c["number"], v["verse"]): v for c in kjv["chapters"] for v in c["verses"]}
        target_verses = {(c["number"], v["verse"]): v for c in target["chapters"] for v in c["verses"]}
        emitted: set[tuple[int, int]] = set()
        for chapter, ranges in mixed.items():
            for verse, end in ranges:
                if end != verse:
                    raise ValueError(f"Combined KJV speech unit: {book_id} {chapter}:{verse}-{end}")
                matches = [
                    row for (target_chapter, _), row in target_verses.items()
                    if target_chapter == chapter and row["verse"] <= verse <= row.get("verseEnd", row["verse"])
                ]
                if len(matches) != 1 or matches[0].get("sourcePlaceholder"):
                    raise ValueError(f"Missing, duplicate, or placeholder target speech unit: {book_id} {chapter}:{verse}")
                target_row = matches[0]
                target_start = target_row["verse"]
                target_end = target_row.get("verseEnd", target_start)
                if (chapter, target_start) in emitted:
                    continue
                emitted.add((chapter, target_start))
                covered_kjv = []
                for covered_verse in range(target_start, target_end + 1):
                    source = kjv_verses.get((chapter, covered_verse))
                    if source is None:
                        raise ValueError(f"Combined target contains absent KJV unit: {book_id} {chapter}:{covered_verse}")
                    covered_kjv.append(source)
                kjv_row = kjv_verses[(chapter, verse)]
                raw = remove_jesus_tags(target_row["text"])
                kshape = speech_shape(kjv_row["text"])
                modern_row = modern.get((chapter, target_start), {"shape": "unknown", "textSha256": None})
                if target_end != target_start:
                    suggestions, confidence, reason = [], "low", "Combined native target unit requires review across every covered KJV source verse."
                else:
                    suggestions, confidence, reason = propose(raw, kshape, language, modern_row["shape"])
                rows.append({
                    "collection": collection,
                    "bookId": book_id,
                    "chapter": chapter,
                    "verse": target_start,
                    **({"verseEnd": target_end} if target_end != target_start else {}),
                    "sourceTextSha256": sha256_text(raw),
                    "kjvTextSha256": sha256_text(kjv_row["text"]),
                    "coveredKjv": [
                        {"verse": item["verse"], "textSha256": sha256_text(item["text"]), "speechShape": speech_shape(item["text"])}
                        for item in covered_kjv
                    ],
                    "kjvSpeechShape": kshape,
                    "modernSpeechShape": modern_row["shape"],
                    "modernTextSha256": modern_row["textSha256"],
                    "targetText": raw,
                    "proposedSpans": suggestions,
                    "confidence": confidence,
                    "reason": reason,
                    "reviewStatus": "unreviewed",
                })
    rows.sort(key=lambda row: (next(i for i, (_, _, book) in enumerate(BOOKS) if book == row["bookId"]), row["chapter"], row["verse"]))
    return {
        "schemaVersion": 1,
        "language": language,
        "editionId": edition_id,
        "semanticAuthority": "en/kjv1769 pinned source wj",
        "candidateCount": len(rows),
        "confidenceCounts": dict(Counter(row["confidence"] for row in rows)),
        "rows": rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", required=True)
    parser.add_argument("--edition-id", required=True)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, help="Optional review-queue JSON file; never an importer ledger")
    args = parser.parse_args()
    queue = build_queue(args.repo_root.resolve(), args.language, args.edition_id)
    if args.output:
        args.output.write_text(json.dumps(queue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in queue.items() if k != "rows"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
