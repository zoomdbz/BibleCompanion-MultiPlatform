#!/usr/bin/env python3
"""Fetch and compare public red-letter markup for a traditional edition.

This creates a review report, never a reviewed importer ledger. Every source
chapter response receives a SHA-256 pin. No mismatch is silently normalized.
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import html
import json
import re
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

from propose_jesus_word_spans import build_queue, speech_shape


API = "https://api.bible.placki.cloud/rpc/fetch_verses_by_address"
BOOK_CODE = {
    "matthew": "Mt", "mark": "Mk", "luke": "Lk", "john": "Joh",
    "acts": "Apg", "1_corinthians": "1Kor", "2_corinthians": "2Kor",
    "revelation": "Offb",
}
BOOK_NUMBER = {
    "matthew": 470, "mark": 480, "luke": 490, "john": 500,
    "acts": 510, "1_corinthians": 530, "2_corinthians": 540,
    "revelation": 730,
}
J_TAG = re.compile(r"<J>(.*?)</J>", re.DOTALL)
OTHER_TAG = re.compile(r"<(?!/?J>)[^>]+>")
APP_MARKER = re.compile(r"\[/?(?:J|ADD|DN)\]")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def fetch_chapter(book_id: str, chapter: int, source: str) -> dict:
    address = f"{BOOK_CODE[book_id]} {chapter}"
    response = requests.post(
        API,
        headers={"Accept-Profile": "api", "Content-Profile": "api"},
        json={"p_address": address, "p_source": source}, timeout=30,
    )
    response.raise_for_status()
    payload = response.json()
    if not isinstance(payload, list) or any(row.get("source") != source for row in payload):
        raise ValueError(f"Unexpected DeepBible source response for {address}/{source}")
    verses: dict[int, str] = {}
    for row in payload:
        if row.get("chapter") != chapter or row.get("book_number") != BOOK_NUMBER[book_id]:
            raise ValueError(f"Unexpected DeepBible reference in {address}/{source}: {row.get('address')}")
        verse = row.get("verse")
        if not isinstance(verse, int) or verse in verses:
            raise ValueError(f"Duplicate or invalid DeepBible verse in {address}/{source}")
        verses[verse] = row["text"]
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {"bookId": book_id, "chapter": chapter, "sourceSha256": sha256_bytes(canonical), "verses": verses}


def clean_source(text: str) -> str:
    return html.unescape(OTHER_TAG.sub("", text))


def presentation_equivalent(text: str) -> str:
    # German LUTD uses French-style quotation glyphs; the pinned USFM app
    # text uses English curly glyphs for the same printed quotation.
    return (text.replace("»", "“").replace("«", "”")
            .replace("’", "'").replace("‘", "'"))


def source_speech_ranges(tagged: str) -> tuple[str, list[tuple[int, int]]]:
    plain_parts = []
    ranges = []
    position = 0
    start = None
    for part in re.split(r"(</?J>)", tagged):
        if part == "<J>":
            if start is not None:
                raise ValueError("Nested external J markup")
            start = position
        elif part == "</J>":
            if start is None:
                raise ValueError("Unmatched external J close")
            ranges.append((start, position))
            start = None
        else:
            plain_parts.append(part)
            position += len(part)
    if start is not None:
        raise ValueError("Unclosed external J markup")
    return "".join(plain_parts), ranges


def compare(source_text: str | None, target: dict, chapter_hash: str) -> dict:
    base = {
        key: target[key]
        for key in ("collection", "bookId", "chapter", "verse", "sourceTextSha256", "kjvTextSha256", "kjvSpeechShape")
    }
    base["sourceChapterSha256"] = chapter_hash
    if source_text is None:
        return {**base, "status": "missing_source"}
    cleaned = clean_source(source_text)
    try:
        source_plain, source_ranges = source_speech_ranges(cleaned)
    except ValueError as exc:
        return {**base, "status": "malformed_source_j", "sourceText": cleaned,
                "sourceMarkupError": str(exc), "targetText": APP_MARKER.sub("", target["targetText"])}
    target_plain = APP_MARKER.sub("", target["targetText"])
    if source_plain == target_plain:
        status = "exact_text"
    elif presentation_equivalent(source_plain) == presentation_equivalent(target_plain):
        status = "presentation_only"
    elif presentation_equivalent(source_plain).rstrip(".") == presentation_equivalent(target_plain).rstrip("."):
        status = "presentation_only"
    else:
        status = "text_mismatch"
    alignment = None
    if status == "text_mismatch":
        # Align exact boundaries only inside common target/source runs. Edits
        # elsewhere in a verse are permitted; a changed boundary fails closed.
        matcher = difflib.SequenceMatcher(None, source_plain.casefold(), target_plain.casefold(), autojunk=False)
        boundary_map = {}
        for block in matcher.get_matching_blocks():
            for offset in range(block.size + 1):
                boundary_map[block.a + offset] = block.b + offset
        boundaries = {point for pair in source_ranges for point in pair}
        if matcher.ratio() >= 0.95 and boundaries <= boundary_map.keys():
            alignment = [tuple(boundary_map[point] for point in pair) for pair in source_ranges]
            status = "aligned_text"
        else:
            return {
                **base, "status": "text_mismatch", "externalPlainTextSha256": sha256_bytes(source_plain.encode("utf-8")),
                "targetText": target_plain, "sourceText": source_plain,
                "alignmentRatio": round(matcher.ratio(), 5),
            }
    spans = []
    for start, end in (alignment if alignment is not None else source_ranges):
        if start >= len(target_plain):
            status = "ambiguous_span"
            continue
        target_end = min(end, len(target_plain))
        if alignment is None and end == len(source_plain) and len(target_plain) > len(source_plain):
            target_end = len(target_plain)
        spans.append(target_plain[start:target_end])
    source_shape = speech_shape(cleaned.replace("<J>", "[J]").replace("</J>", "[/J]"))
    if not spans:
        status = "no_source_j"
    elif source_shape != target["kjvSpeechShape"]:
        status = "speech_shape_conflict"
    elif any(not span or target_plain.count(span) != 1 for span in spans):
        status = "ambiguous_span"
    return {
        **base,
        "status": status,
        "sourceSpeechShape": source_shape,
        "externalPlainTextSha256": sha256_bytes(source_plain.encode("utf-8")),
        "spans": spans,
        "targetText": target_plain,
        "sourceText": source_plain,
    }


def build_report(repo_root: Path, language: str, edition_id: str, source: str, workers: int = 8) -> dict:
    queue = build_queue(repo_root, language, edition_id)
    chapters = sorted({(row["bookId"], row["chapter"]) for row in queue["rows"]})
    fetched = {}
    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = {executor.submit(fetch_chapter, book, chapter, source): (book, chapter) for book, chapter in chapters}
        for future in as_completed(futures):
            book, chapter = futures[future]
            fetched[(book, chapter)] = future.result()
    rows = []
    for row in queue["rows"]:
        chapter = fetched[(row["bookId"], row["chapter"])]
        source_text = chapter["verses"].get(row["verse"])
        rows.append(compare(source_text, row, chapter["sourceSha256"]))
    return {
        "schemaVersion": 1,
        "endpoint": API,
        "source": source,
        "language": language,
        "editionId": edition_id,
        "candidateCount": queue["candidateCount"],
        "chapterCount": len(chapters),
        "statusCounts": dict(Counter(row["status"] for row in rows)),
        "rows": rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", required=True)
    parser.add_argument("--edition-id", required=True)
    parser.add_argument("--source", required=True, help="Exact DeepBible source code, e.g. LUTD")
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = build_report(args.repo_root.resolve(), args.language, args.edition_id, args.source)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "rows"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
