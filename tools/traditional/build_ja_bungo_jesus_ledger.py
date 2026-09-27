#!/usr/bin/env python3
"""Audit the Bungo site's native red spans against pinned KJV speech semantics.

The site supplies Japanese text boundaries, not the set of verses the app
colors. Only pinned KJV-wj verses may become ledger rows. The HTML cache is
ignored and refetchable; page SHA-256 values bind the review evidence.
"""

from __future__ import annotations

import argparse
from collections import Counter
import difflib
import hashlib
import json
from pathlib import Path
import re
import unicodedata

from import_traditional_editions import BOOKS, base_jesus_ranges
from jesus_word_spans import sha256_text


ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / ".scripture-structure-cache/bungo-red-letter"
TARGET = ROOT / "shared/assets/books/editions/ja/bungo/new_testament"
KJV = ROOT / "shared/assets/books/editions/en/kjv1769/new_testament"
LEDGER_PATH = ROOT / "tools/traditional/jesus_word_spans/ja_bungo.json"
REPORT_PATH = ROOT / "tools/reports/ja_bungo_red_letter_evidence.json"
TAG = re.compile(r"\[/?(?:J|DN|ADD)\]")
VERSE_ID = re.compile(r"b(\d{2})c(\d{3})v(\d{3})$")
VARIATION_SELECTOR = re.compile(r"[\uFE00-\uFE0F\U000E0100-\U000E01EF]")
PARATEXT = re.compile(r"\[(?:\*|△)[^\]]*\]|〘\d+ページ〙", re.DOTALL)
SITE_BASE = "https://bungo.iinaa.net"
BOOKS_NT = tuple(book_id for _, collection, book_id in BOOKS if collection == "new_testament")
MANUAL_BOUNDARIES = {
    "matthew:10:5": ("first_quote_to_end", "The website has three spaces before Jesus' opening quote; the overlay has none."),
    "matthew:16:4": ("start_to_first_close", "The spoken sentence ends at the closing quote; the following changed character begins narration."),
    "matthew:26:56": ("start_to_first_close", "The spoken sentence ends at the closing quote; the following changed character begins narration."),
    "luke:7:31": ("whole_verse", "The entire target verse is Jesus' speech; the initial kanji and comma differ."),
    "luke:24:36": ("first_quote", "The site uses a non-scriptural bracket around the direct quotation; only the inner quote is Jesus' speech."),
    "john:8:11": ("last_quote", "The closing bracket after Jesus' quote is editorial, not part of the speech."),
    "john:12:28": ("start_to_first_close", "Jesus' prayer ends at the first closing quote; the changed word starts the heavenly reply narrative."),
    "john:12:36": ("start_to_first_close", "The spoken sentence ends at the quote; the site has three following layout spaces."),
    "john:13:26": ("first_quote", "The changed character after the quote begins narration of the offered morsel."),
    "2_corinthians:12:9": ("first_quote", "Christ's speech is the quotation; the changed character after it begins Paul's response."),
}


def normalized_chars(text: str) -> str:
    return unicodedata.normalize("NFKC", VARIATION_SELECTOR.sub("", text))


def source_characters(node) -> list[tuple[str, bool]]:
    """Read visible characters with red state; discard verse links and ruby rt."""
    from bs4 import NavigableString, Tag
    result: list[tuple[str, bool]] = []

    def walk(current, red: bool) -> None:
        if isinstance(current, NavigableString):
            for character in str(current):
                if not VARIATION_SELECTOR.fullmatch(character):
                    for normalized in unicodedata.normalize("NFKC", character):
                        result.append((normalized, red))
        elif isinstance(current, Tag):
            if current.name in {"a", "rt", "script", "style"}:
                return
            marked = red or "red" in current.get("class", ())
            for child in current.children:
                walk(child, marked)

    walk(node, False)
    # The website inserts note markers, variant readings, and printed-page
    # labels inside verse spans. These are not part of the pinned Bungo text.
    visible = "".join(char for char, _ in result)
    remove = {index for match in PARATEXT.finditer(visible)
              for index in range(match.start(), match.end())}
    result = [value for index, value in enumerate(result) if index not in remove]
    while result and result[0][0].isspace():
        result.pop(0)
    while result and result[-1][0].isspace():
        result.pop()
    return result


def red_runs(chars: list[tuple[str, bool]]) -> list[tuple[int, int]]:
    runs = []
    start = None
    for index, (_, red) in enumerate(chars):
        if red and start is None:
            start = index
        elif not red and start is not None:
            runs.append((start, index))
            start = None
    if start is not None:
        runs.append((start, len(chars)))
    return runs


def parse_page(raw: bytes, number: int) -> dict[tuple[int, int], dict]:
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(raw, "html.parser")
    verses = {}
    for node in soup.select("span[id]"):
        match = VERSE_ID.fullmatch(node.get("id", ""))
        if not match or int(match[1]) != number:
            continue
        key = (int(match[2]), int(match[3]))
        if key in verses:
            raise ValueError(f"Repeated Bungo site verse: {number} {key}")
        chars = source_characters(node)
        verses[key] = {"text": "".join(char for char, _ in chars), "runs": red_runs(chars)}
    if not verses:
        raise ValueError(f"Bungo site has no native verses: b{number}")
    return verses


def target_normalized_map(text: str) -> tuple[str, list[int]]:
    visible = TAG.sub("", text)
    mapped = []
    chars = []
    for index, character in enumerate(visible):
        if VARIATION_SELECTOR.fullmatch(character):
            continue
        for normalized in unicodedata.normalize("NFKC", character):
            chars.append(normalized)
            mapped.append(index)
    return "".join(chars), mapped


def reviewed_target_runs(source: str, target: str, runs: list[tuple[int, int]], reference: str):
    """Relocate only source-red boundaries anchored in equal text or reviewed quotes."""
    if not runs:
        return [], "reviewed_source_omission", []
    if source == target:
        return runs, "exact_target_text", []
    matcher = difflib.SequenceMatcher(None, source, target, autojunk=False)
    edits = [(kind, source[a:b], target[x:y], a, b, x, y)
             for kind, a, b, x, y in matcher.get_opcodes() if kind != "equal"]
    if reference in MANUAL_BOUNDARIES:
        if len(runs) != 1:
            raise ValueError(f"Manual speech count changed: {reference}")
        mode = MANUAL_BOUNDARIES[reference][0]
        first_open = target.find("『")
        first_close = target.find("』") + 1
        last_open = target.rfind("『")
        last_close = target.rfind("』") + 1
        if mode in {"first_quote", "last_quote"} and first_open < 0:
            raise ValueError(f"Reviewed opening quote changed: {reference}")
        if mode in {"first_quote", "last_quote", "start_to_first_close"} and first_close <= 0:
            raise ValueError(f"Reviewed quote delimiter changed: {reference}")
        selected = {
            "first_quote_to_end": (first_open, len(target)),
            "start_to_first_close": (0, first_close),
            "whole_verse": (0, len(target)),
            "first_quote": (first_open, first_close),
            "last_quote": (last_open, last_close),
        }[mode]
        if not 0 <= selected[0] < selected[1] <= len(target):
            raise ValueError(f"Reviewed speech boundary drift: {reference}")
        return [selected], "manual_quote_boundary", edits

    def matched_boundary(position: int) -> int:
        if any(a <= position <= b for _, _, _, a, b, _, _ in edits):
            raise ValueError(f"Unreviewed source/target edit touches Jesus speech boundary: {reference}")
        positions = [x + position - a for kind, a, b, x, _ in matcher.get_opcodes()
                     if kind == "equal" and a <= position <= b]
        if len(set(positions)) != 1:
            raise ValueError(f"Unanchored Jesus speech boundary: {reference} {position}")
        return positions[0]

    mapped = [(matched_boundary(start), matched_boundary(end)) for start, end in runs]
    if any(not 0 <= start < end <= len(target) for start, end in mapped):
        raise ValueError(f"Invalid target speech interval: {reference}")
    return mapped, "equal_boundary_with_reviewed_text_differences", edits


def fetch_pages() -> None:
    import requests
    CACHE.mkdir(parents=True, exist_ok=True)
    for number in range(40, 67):
        url = f"{SITE_BASE}/b{number}.html"
        response = requests.get(url, timeout=30)
        response.raise_for_status()
        page = response.content
        parse_page(page, number)
        (CACHE / f"b{number}.html").write_bytes(page)
        print(f"b{number}: {len(page)} bytes, {hashlib.sha256(page).hexdigest()}")


def inventory() -> dict:
    from bs4 import BeautifulSoup
    site = {}
    page_proofs = []
    for index, book_id in enumerate(BOOKS_NT):
        number = index + 40
        path = CACHE / f"b{number}.html"
        if not path.is_file():
            raise FileNotFoundError(f"Fetch the exact source page first: {path}")
        raw = path.read_bytes()
        source = parse_page(raw, number)
        soup = BeautifulSoup(raw, "html.parser")
        raw_red = sum(
            node.select_one(".red") is not None
            for node in soup.select("span[id]")
            if (match := VERSE_ID.fullmatch(node.get("id", ""))) and int(match[1]) == number
        )
        site[book_id] = source
        page_proofs.append({"bookId": book_id, "url": f"{SITE_BASE}/b{number}.html",
                            "htmlSha256": hashlib.sha256(raw).hexdigest(),
                            "nativeVerses": len(source),
                            "rawRedVerses": raw_red,
                            "redVerses": sum(bool(v["runs"]) for v in source.values())})
    return {"site": site, "pages": page_proofs}


def audit() -> dict:
    evidence = inventory()
    site = evidence["site"]
    counts = Counter()
    counts["rawSiteRed"] = sum(page["rawRedVerses"] for page in evidence["pages"])
    mismatches = []
    kjv_only = []
    site_only = []
    mixed_rows = []
    for book_id in BOOKS_NT:
        target_doc = json.loads((TARGET / f"{book_id}.json").read_text(encoding="utf-8"))
        kjv_doc = json.loads((KJV / f"{book_id}.json").read_text(encoding="utf-8"))
        kjv_verses = {(chapter["number"], verse["verse"]): verse["text"]
                      for chapter in kjv_doc["chapters"] for verse in chapter["verses"]}
        full, mixed = base_jesus_ranges(ROOT, "new_testament", book_id)
        full_coords = {(ch, n) for ch, spans in full.items() for first, last in spans for n in range(first, last + 1)}
        mixed_coords = {(ch, n) for ch, spans in mixed.items() for first, last in spans for n in range(first, last + 1)}
        site_red = {key for key, value in site[book_id].items() if value["runs"]}
        counts["kjvFull"] += len(full_coords)
        counts["kjvMixed"] += len(mixed_coords)
        counts["siteRed"] += len(site_red)
        counts["fullIntersection"] += len(full_coords & site_red)
        counts["mixedIntersection"] += len(mixed_coords & site_red)
        kjv_only.extend({"bookId": book_id, "chapter": ch, "verse": v, "kind": "full" if (ch, v) in full_coords else "mixed"}
                        for ch, v in sorted((full_coords | mixed_coords) - site_red))
        site_only.extend({"bookId": book_id, "chapter": ch, "verse": v}
                         for ch, v in sorted(site_red - full_coords - mixed_coords))
        for chapter in target_doc["chapters"]:
            ch = chapter["number"]
            for verse in chapter["verses"]:
                first, last = verse["verse"], verse.get("verseEnd", verse["verse"])
                covered_mixed = [n for n in range(first, last + 1) if (ch, n) in mixed_coords]
                if not covered_mixed:
                    continue
                counts["targetMixedUnits"] += 1
                if last != first:
                    counts["combinedMixedUnits"] += 1
                raw = verse["text"].replace("[J]", "").replace("[/J]", "")
                target_text, index_map = target_normalized_map(raw)
                matching = [site[book_id].get((ch, n)) for n in range(first, last + 1)]
                if any(item is None for item in matching):
                    mismatches.append({"bookId": book_id, "chapter": ch, "verse": first,
                                       "verseEnd": last, "kind": "missingSiteVerse"})
                    continue
                source_text = "".join(item["text"] for item in matching)
                runs = []
                offset = 0
                for n, item in zip(range(first, last + 1), matching):
                    if (ch, n) in mixed_coords:
                        runs.extend((offset + a, offset + b) for a, b in item["runs"])
                    offset += len(item["text"])
                reference = f"{book_id}:{ch}:{first}"
                mapped_runs, method, edits = reviewed_target_runs(source_text, target_text, runs, reference)
                if edits:
                    mismatches.append({"bookId": book_id, "chapter": ch, "verse": first,
                                       "verseEnd": last, "kind": "textDifference",
                                       "method": method,
                                       "siteSha256": sha256_text(source_text),
                                       "targetSha256": sha256_text(target_text),
                                       "differences": [{"site": src, "target": dest} for _, src, dest, *_ in edits]})
                visible = TAG.sub("", raw)
                spans = []
                span_starts = []
                for start, end in mapped_runs:
                    if not 0 <= start < end <= len(index_map):
                        raise ValueError(f"Invalid source red run: {book_id} {ch}:{first}")
                    exact = visible[index_map[start]:index_map[end - 1] + 1]
                    if not exact:
                        raise ValueError(f"Empty target speech: {book_id} {ch}:{first}")
                    spans.append(exact)
                    span_starts.append(index_map[start])
                mixed_rows.append({"bookId": book_id, "chapter": ch, "verse": first,
                                   "verseEnd": last, "raw": raw, "spans": spans,
                                   "spanStarts": span_starts,
                                   "siteTextSha256": sha256_text(source_text),
                                   "siteRedRunCount": len(runs), "method": method,
                                   "kjvTextSha256": sha256_text(kjv_verses[(ch, first)]),
                                   "manualReason": MANUAL_BOUNDARIES.get(reference, (None, None))[1],
                                   "differences": [{"site": src, "target": dest} for _, src, dest, *_ in edits]})
    return {"pages": evidence["pages"], "site": site, "counts": dict(counts),
            "kjvOnly": kjv_only, "siteOnly": site_only,
            "mismatches": mismatches, "mixedRows": mixed_rows}


def build_documents(result: dict) -> tuple[dict, dict]:
    counts = result["counts"]
    if (counts["kjvFull"], counts["kjvMixed"], counts["targetMixedUnits"],
        counts["fullIntersection"], counts["mixedIntersection"]) != (1402, 626, 626, 1396, 623):
        raise ValueError(f"Pinned KJV/Bungo coverage changed: {counts}")
    if len(result["siteOnly"]) != 36 or len(result["mismatches"]) != 319:
        raise ValueError("Bungo source-only or text-difference inventory changed")
    source_uncolored_omissions = {
        ("matthew", 17, 21), ("mark", 7, 16), ("mark", 9, 44),
        ("mark", 9, 46), ("mark", 11, 26), ("luke", 17, 36),
    }
    source_colored_omission_markers = {
        ("matthew", 18, 11), ("matthew", 23, 14),
    }
    actual_full_omissions = {(row["bookId"], row["chapter"], row["verse"])
                             for row in result["kjvOnly"] if row["kind"] == "full"}
    if actual_full_omissions != source_uncolored_omissions:
        raise ValueError(f"Bungo omitted full-speech inventory changed: {actual_full_omissions}")
    report = {
        "schemaVersion": 1,
        "source": "Bungo native red-letter edition",
        "sourceBase": SITE_BASE,
        "sourceScope": "b40.html through b66.html, fetched 2026-09-26",
        "semanticAuthority": "Pinned en/kjv1769 CrossWire wj source",
        "normalization": "Strip ruby rt, Unicode variation selectors, site footnote/page paratext; compare NFKC visible text; never rewrite target Scripture.",
        "pages": result["pages"],
        "counts": counts,
        "kjvOnly": result["kjvOnly"],
        "siteOnlyEditorialRed": result["siteOnly"],
        "targetTextDifferences": result["mismatches"],
        "manualBoundaryReviews": [
            {"reference": reference, "method": method, "reason": reason}
            for reference, (method, reason) in MANUAL_BOUNDARIES.items()
        ],
        "reviewPolicy": "Site-only red coordinates are not imported. All 626 KJV-mixed native target units receive exact hashed rows; three omitted target speeches use explicit noTargetSpeech reviews. Eight KJV-full verses are Bungo [なし] omissions and override inherited coloring; the website colors the omission marker itself in two of them, but the marker is not speech.",
    }
    report_bytes = (json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    report_hash = hashlib.sha256(report_bytes).hexdigest().upper()
    page_hashes = {page["bookId"]: page["htmlSha256"] for page in result["pages"]}
    rows = []
    for candidate in result["mixedRows"]:
        book_id, chapter, verse = candidate["bookId"], candidate["chapter"], candidate["verse"]
        raw = candidate["raw"]
        row = {
            "collection": "new_testament", "bookId": book_id,
            "chapter": chapter, "verse": verse,
            "sourceTextSha256": sha256_text(raw),
        }
        if candidate["verseEnd"] != verse:
            row["verseEnd"] = candidate["verseEnd"]
        if not candidate["spans"]:
            row["noTargetSpeech"] = True
            row["reason"] = "The pinned KJV has Jesus speech here, but Bungo's corresponding native verse contains narration only; the site's red text, if any, belongs to a discarded variant footnote."
        else:
            visible = TAG.sub("", raw)
            selected = []
            for exact, start in zip(candidate["spans"], candidate["spanStarts"]):
                span = {"exactText": exact}
                if visible.count(exact) > 1:
                    positions = []
                    offset = -1
                    while (offset := visible.find(exact, offset + 1)) >= 0:
                        positions.append(offset)
                    if start not in positions:
                        raise ValueError(f"Repeated target span lost its position: {book_id} {chapter}:{verse}")
                    span["occurrence"] = positions.index(start) + 1
                selected.append(span)
            row["spans"] = selected
        row["review"] = {
            "status": "reviewed",
            "method": candidate["method"],
            "evidence": {
                "semanticAuthority": "Pinned KJV1769 CrossWire wj",
                "kjvTextSha256": candidate["kjvTextSha256"],
                "nativeRedSource": f"{SITE_BASE}/b{BOOKS_NT.index(book_id) + 40}.html",
                "nativePageSha256": page_hashes[book_id],
                "nativeVerseTextSha256": candidate["siteTextSha256"],
                "nativeRedRuns": candidate["siteRedRunCount"],
                "targetDifferenceReview": candidate["differences"],
                "reason": candidate["manualReason"] or (
                    "The native red boundaries anchor in unchanged target text; listed orthographic and punctuation differences do not cross a speech boundary."
                    if candidate["differences"] else
                    "The native source and target text match exactly after prescribed ruby/variation normalization."
                ),
            },
        }
        rows.append(row)
    all_full_omissions = source_uncolored_omissions | source_colored_omission_markers
    for book_id, chapter, verse in sorted(all_full_omissions, key=lambda key: (BOOKS_NT.index(key[0]), key[1], key[2])):
        target = json.loads((TARGET / f"{book_id}.json").read_text(encoding="utf-8"))
        kjv = json.loads((KJV / f"{book_id}.json").read_text(encoding="utf-8"))
        target_verse = next(item for item in target["chapters"][chapter - 1]["verses"] if item["verse"] == verse)
        kjv_verse = next(item for item in kjv["chapters"][chapter - 1]["verses"] if item["verse"] == verse)
        native_site = result["site"][book_id][(chapter, verse)]
        raw = target_verse["text"].replace("[J]", "").replace("[/J]", "")
        expected_runs = [(0, len(raw))] if (book_id, chapter, verse) in source_colored_omission_markers else []
        if raw != "[なし]" or native_site["text"] != raw or native_site["runs"] != expected_runs:
            raise ValueError(f"Bungo omitted verse changed: {book_id} {chapter}:{verse}")
        rows.append({
            "collection": "new_testament", "bookId": book_id,
            "chapter": chapter, "verse": verse,
            "sourceTextSha256": sha256_text(raw),
            "overrideInherited": True,
            "noTargetSpeech": True,
            "reason": "Bungo has only the native [なし] omission marker; the KJV full-speech verse has no target words to color, even when the website colors the marker itself.",
            "review": {
                "status": "reviewed", "method": "native_omission_overrides_kjv_full_inheritance",
                "evidence": {
                    "semanticAuthority": "Pinned KJV1769 CrossWire wj",
                    "kjvTextSha256": sha256_text(kjv_verse["text"]),
                    "nativeRedSource": f"{SITE_BASE}/b{BOOKS_NT.index(book_id) + 40}.html",
                    "nativePageSha256": page_hashes[book_id],
                    "nativeVerseTextSha256": sha256_text(native_site["text"]),
                    "nativeRedRuns": [list(run) for run in native_site["runs"]],
                    "reason": "The source and target both show [なし]; inherited [J] on the non-Scripture omission marker would miscolor it. The website's red marker, if present, is not spoken text.",
                },
            },
        })
    rows.sort(key=lambda row: (BOOKS_NT.index(row["bookId"]), row["chapter"], row["verse"]))
    ledger = {
        "schemaVersion": 1,
        "language": "ja", "editionId": "bungo",
        "semanticAuthority": "Pinned en/kjv1769 CrossWire wj source",
        "externalEvidence": {
            "source": "Bungo native red-letter edition",
            "pageRange": f"{SITE_BASE}/b40.html through {SITE_BASE}/b66.html",
            "report": "tools/reports/ja_bungo_red_letter_evidence.json",
            "reportSha256": report_hash,
        },
        "rows": rows,
    }
    return ledger, report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fetch", action="store_true")
    parser.add_argument("--audit", action="store_true")
    parser.add_argument("--write", action="store_true", help="Write reviewed ledger and source evidence")
    args = parser.parse_args()
    if args.fetch:
        fetch_pages()
    if args.audit or args.write:
        result = audit()
        if args.audit:
            print(json.dumps({key: value for key, value in result.items() if key not in {"mixedRows", "site"}},
                             ensure_ascii=True, indent=2))
        if args.write:
            ledger, report = build_documents(result)
            REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
            REPORT_PATH.write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
            LEDGER_PATH.parent.mkdir(parents=True, exist_ok=True)
            LEDGER_PATH.write_bytes((json.dumps(ledger, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
            print(f"Wrote {len(ledger['rows'])} reviewed native-unit rows and {len(report['targetTextDifferences'])} explicit text-difference reviews")


if __name__ == "__main__":
    main()
