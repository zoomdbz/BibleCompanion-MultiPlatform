#!/usr/bin/env python3
"""Adjudicate strict Hindi IRV differences against the pinned official USFM.

The comparator is deliberately narrow. It accepts only exact, reviewed
cross-reference spans and source-export whitespace at inline presentation
marker boundaries. Current app text, parsed source text, and raw USFM are
locked in the review JSON, so a later word or punctuation change cannot pass
under a broad normalization rule.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import html
import io
import json
from pathlib import Path
import re
import sys
import unicodedata
import zipfile

import requests

from audit_ebible_source import (
    AuditError,
    FLOW_LINE,
    ID,
    MARKER,
    SKIP_PAIRED,
    STRUCTURAL_LINE,
    VISIBLE_PAIRED,
    download_archive,
    normalize,
    parse_app,
    parse_usfm,
    remove_paired,
)
from audit_scripture_sources import BOOKS, j_mask
from repair_hindi_source_markup import (
    INITIAL_REVIEWED_PRESENTATION_SPEECH_MISMATCHES,
    REVIEWED_LEXICAL_REPAIRS,
    transplant_reviewed_presentation_jesus_tags,
)


SOURCE_URL = "https://ebible.org/Scriptures/hin2017_usfm.zip"
SOURCE_SHA256 = "4B284BEE6D52D8DAE3425D540E6743836E614694EEFF9C55D2395071A76BF07F"
REPORT_DIR = Path(__file__).resolve().parent / "reports/hindi_irv"
REVIEW_PATH = REPORT_DIR / "source_difference_review.json"
SUMMARY_PATH = REPORT_DIR / "SOURCE_DIFFERENCE_REVIEW.md"
REFERENCE_PAREN = re.compile(r"\([^()\n]*\d+\s*:\s*\d+[^()\n]*\)")
INLINE_MARKER = re.compile(r"\\(\+?[A-Za-z0-9-]+)(\*)?")
PRESENTATION_MARKERS = {
    "add", "bd", "bdit", "bk", "dc", "em", "it", "k", "nd", "ord",
    "pn", "png", "pro", "qs", "qt", "rq", "sc", "sig", "sls", "sup",
    "tl", "wj", "w", "wa", "wg", "wh",
}
PRESENTATION_CLASSES = {
    "cross_reference_apparatus",
    "source_export_whitespace",
    "cross_reference_apparatus_and_source_export_whitespace",
}


class ReviewError(RuntimeError):
    pass


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest().upper()


def without_whitespace(value: str) -> str:
    return "".join(character for character in value if not character.isspace())


def whitespace_gaps(value: str) -> tuple[str, list[bool]]:
    """Return non-whitespace text and whitespace presence at every text gap."""

    characters: list[str] = []
    gaps: list[bool] = []
    pending = False
    for character in value:
        if character.isspace():
            pending = True
        else:
            gaps.append(pending)
            pending = False
            characters.append(character)
    gaps.append(pending)
    return "".join(characters), gaps


def whitespace_gap_differences(left: str, right: str) -> list[int]:
    left_text, left_gaps = whitespace_gaps(left)
    right_text, right_gaps = whitespace_gaps(right)
    if left_text != right_text or len(left_gaps) != len(right_gaps):
        raise ReviewError("non-whitespace text differs")
    return [index for index, pair in enumerate(zip(left_gaps, right_gaps)) if pair[0] != pair[1]]


def raw_visible_with_it_boundaries(raw: str) -> tuple[str, set[int]]:
    """Render visible raw text and record each italic-marker boundary gap."""

    output: list[str] = []
    boundaries: set[int] = set()

    def nonspace_length() -> int:
        return sum(not character.isspace() for part in output for character in part)

    def scan(fragment: str) -> None:
        index = 0
        while index < len(fragment):
            match = MARKER.search(fragment, index)
            if match is None:
                output.append(fragment[index:])
                break
            output.append(fragment[index:match.start()])
            name = match.group(1)
            base = name.removeprefix("+")
            if match.group(2):
                raise ReviewError(f"unexpected close marker in raw review text: {name}")
            content, next_index = remove_paired(fragment, match.end(), name)
            if base in SKIP_PAIRED or base in {"va", "vp"}:
                index = next_index
                continue
            if base not in VISIBLE_PAIRED:
                raise ReviewError(f"unsupported raw inline marker in review: {name}")
            if base == "it":
                boundaries.add(nonspace_length())
            if base in {"w", "wa", "wg", "wh", "k"}:
                content = content.split("|", 1)[0]
            scan(content)
            if base == "it":
                boundaries.add(nonspace_length())
            index = next_index

    scan(raw)
    return normalize("".join(output).replace("\u00b6", " ")), boundaries


def raw_usfm_verses(raw: bytes, label: str) -> dict[tuple[int, int, int], str]:
    """Return the exact inline USFM collected by the canonical parser."""

    text = raw.decode("utf-8-sig")
    chapter: int | None = None
    active: tuple[int, int, int] | None = None
    parts: list[str] = []
    result: dict[tuple[int, int, int], str] = {}

    def finish() -> None:
        nonlocal active, parts
        if active is not None:
            if active in result:
                raise ReviewError(f"duplicate raw verse in {label}: {active}")
            result[active] = " ".join(parts)
        active = None
        parts = []

    for line_number, original in enumerate(text.splitlines(), start=1):
        line = original.strip()
        if match := re.match(r"^\\c\s+(\d+)\b", line):
            finish()
            chapter = int(match[1])
            continue
        if match := re.match(r"^\\v\s+(\d+)(?:-(\d+))?\s+(.*)$", line):
            finish()
            if chapter is None:
                raise ReviewError(f"verse before chapter in {label}:{line_number}")
            start = int(match[1])
            active = (chapter, start, int(match[2] or start))
            parts = [match[3]]
            continue
        if active is not None and line:
            flow = FLOW_LINE.match(line)
            if flow:
                if flow.group(1):
                    parts.append(flow.group(1))
            elif not STRUCTURAL_LINE.match(line):
                parts.append(line)
    finish()
    return result


def load_source_archive(archive_path: Path | None) -> tuple[bytes, str]:
    if archive_path is None:
        return download_archive(SOURCE_URL, SOURCE_SHA256)
    body = archive_path.read_bytes()
    digest = sha256(body)
    if digest != SOURCE_SHA256:
        raise ReviewError(
            f"archive SHA-256 mismatch: expected {SOURCE_SHA256}, got {digest}"
        )
    return body, digest


def source_inventory(body: bytes) -> dict[str, dict[str, object]]:
    result: dict[str, dict[str, object]] = {}
    with zipfile.ZipFile(io.BytesIO(body)) as archive:
        for name in archive.namelist():
            if not name.lower().endswith(".usfm"):
                continue
            raw = archive.read(name)
            code, verses = parse_usfm(raw, name)
            if code in result:
                raise ReviewError(f"duplicate source book: {code}")
            result[code] = {
                "file": name,
                "verses": verses,
                "rawVerses": raw_usfm_verses(raw, name),
                "sha256": sha256(raw),
            }
    expected = {code for code, _collection, _book_id in BOOKS}
    if set(result) != expected:
        raise ReviewError("source archive does not contain exactly the canonical 66 books")
    return result


def extra_reference_spans(text: str, other: str) -> list[dict[str, object]]:
    """Return only unmatched reference-like parentheses, preserving positions."""

    matches = list(REFERENCE_PAREN.finditer(text))
    excess = Counter(match.group(0) for match in matches) - Counter(REFERENCE_PAREN.findall(other))
    result: list[dict[str, object]] = []
    used: Counter[str] = Counter()
    for match in matches:
        value = match.group(0)
        if used[value] >= excess[value]:
            continue
        used[value] += 1
        result.append({"start": match.start(), "end": match.end(), "text": value})
    return result


def remove_reviewed_spans(text: str, spans: list[dict[str, object]]) -> str:
    """Remove exact non-overlapping spans; reject stale offsets or text."""

    previous_end = -1
    for span in sorted(spans, key=lambda row: int(row["start"])):
        start, end, expected = int(span["start"]), int(span["end"]), str(span["text"])
        if start < previous_end or start < 0 or end < start or text[start:end] != expected:
            raise ReviewError(f"stale or overlapping reviewed apparatus span: {span}")
        previous_end = end
    result = text
    for span in sorted(spans, key=lambda row: int(row["start"]), reverse=True):
        result = result[:int(span["start"])] + result[int(span["end"]):]
    return normalize(result)


def marker_for_literal(raw_usfm: str, literal: str) -> str:
    """Identify the paired USFM marker enclosing an exact apparatus literal."""

    offset = raw_usfm.find(literal)
    if offset < 0:
        raise ReviewError(f"reviewed apparatus not present in raw USFM: {literal!r}")
    for marker in ("bdit", "it", "x"):
        for opening in re.finditer(r"\\" + marker + r"(?:\s|\+|-)", raw_usfm):
            closing = raw_usfm.find("\\" + marker + "*", opening.end())
            if opening.end() <= offset < closing:
                return marker
    return "plain"


def visible_markers(raw_usfm: str) -> list[str]:
    return sorted({
        match.group(1).removeprefix("+")
        for match in INLINE_MARKER.finditer(raw_usfm)
        if not match.group(2) and match.group(1).removeprefix("+") in PRESENTATION_MARKERS
    })


def make_reference(book_id: str, key: tuple[int, int, int]) -> str:
    chapter, start, end = key
    return f"{book_id} {chapter}:{start}" + (f"-{end}" if end != start else "")


def classify_difference(
    *,
    code: str,
    book_id: str,
    source_file: str,
    key: tuple[int, int, int],
    app_text: str,
    source_text: str,
    raw_usfm: str,
) -> dict[str, object]:
    app_spans = extra_reference_spans(app_text, source_text)
    source_spans = extra_reference_spans(source_text, app_text)
    for span in app_spans + source_spans:
        span["sourceUsfmMarker"] = marker_for_literal(raw_usfm, str(span["text"]))

    app_residual = remove_reviewed_spans(app_text, app_spans)
    source_residual = remove_reviewed_spans(source_text, source_spans)
    whitespace_only = app_residual != source_residual and (
        without_whitespace(app_residual) == without_whitespace(source_residual)
    )
    markers = visible_markers(raw_usfm)
    approved_whitespace_gaps: list[int] = []
    if whitespace_only and "it" not in markers:
        raise ReviewError(
            f"unproved whitespace difference without source inline marker: {book_id} {key}"
        )
    if whitespace_only:
        approved_whitespace_gaps = whitespace_gap_differences(app_residual, source_residual)
        raw_visible, raw_it_boundaries = raw_visible_with_it_boundaries(raw_usfm)
        if without_whitespace(raw_visible) != without_whitespace(app_residual):
            raise ReviewError(f"raw visible text does not support whitespace review: {book_id} {key}")
        if not set(approved_whitespace_gaps) <= raw_it_boundaries:
            raise ReviewError(f"whitespace difference is not at an it-marker boundary: {book_id} {key}")

    if app_residual == source_residual and (app_spans or source_spans):
        classification = "cross_reference_apparatus"
    elif whitespace_only and (app_spans or source_spans):
        classification = "cross_reference_apparatus_and_source_export_whitespace"
    elif whitespace_only:
        classification = "source_export_whitespace"
    else:
        classification = "unresolved_lexical"

    return {
        "reference": make_reference(book_id, key),
        "sourceBookCode": code,
        "sourceBookFile": source_file,
        "bookId": book_id,
        "chapter": key[0],
        "verseStart": key[1],
        "verseEnd": key[2],
        "classification": classification,
        "strictEquivalent": classification in PRESENTATION_CLASSES,
        "appText": app_text,
        "sourceText": source_text,
        "approvedAppApparatus": app_spans,
        "approvedSourceApparatus": source_spans,
        "sourcePresentationMarkers": markers if whitespace_only else [],
        "approvedWhitespaceGaps": approved_whitespace_gaps,
        "sourceRawUsfm": raw_usfm,
        "sourceHtmlUrl": f"https://ebible.org/hin2017/{code}{key[0]:02d}.htm",
        "sourceHtmlVerified": False,
    }


def build_review(repo: Path, body: bytes, digest: str) -> dict[str, object]:
    sources = source_inventory(body)
    adjudications: list[dict[str, object]] = []
    app_by_code: dict[str, dict[tuple[int, int, int], tuple[str, str]]] = {}
    book_id_by_code = {code: book_id for code, _collection, book_id in BOOKS}
    exact = 0
    app_lines = 0
    source_lines = 0
    for code, collection, book_id in BOOKS:
        app_path = repo / "shared/assets/books" / collection / "hi" / f"{book_id}.json"
        app = parse_app(app_path)
        app_by_code[code] = app
        source = sources[code]["verses"]
        raw_verses = sources[code]["rawVerses"]
        if not isinstance(source, dict) or not isinstance(raw_verses, dict):
            raise ReviewError(f"invalid source inventory for {code}")
        if set(app) != set(source) or set(source) != set(raw_verses):
            raise ReviewError(f"verse inventory mismatch for {book_id}")
        app_lines += len(app)
        source_lines += len(source)
        for key in sorted(app):
            app_text = app[key][0]
            source_text = source[key][0]
            if app_text == source_text:
                exact += 1
                continue
            adjudications.append(classify_difference(
                code=code,
                book_id=book_id,
                source_file=str(sources[code]["file"]),
                key=key,
                app_text=app_text,
                source_text=source_text,
                raw_usfm=raw_verses[key],
            ))

    repairs: list[dict[str, object]] = []
    for (code, chapter, start, end), repair in sorted(REVIEWED_LEXICAL_REPAIRS.items()):
        key = (chapter, start, end)
        source_text = sources[code]["verses"][key][0]
        app_text = app_by_code[code][key][0]
        if source_text != repair["official"]:
            raise ReviewError(f"reviewed repair source drift: {code} {key}")
        if app_text != repair["official"]:
            raise ReviewError(f"reviewed repair is not applied exactly: {code} {key}")
        repairs.append({
            "reference": make_reference(book_id_by_code[code], key),
            "sourceBookCode": code,
            "bookId": book_id_by_code[code],
            "chapter": chapter,
            "verseStart": start,
            "verseEnd": end,
            "oldText": repair["old"],
            "repairedText": repair["official"],
            "sourceArchiveUrl": repair["source"],
            "sourceHtmlUrl": repair["sourceHtml"],
            "linkedEditionUrl": repair["linkedEdition"],
            "sourceHtmlVerified": False,
        })

    counts = Counter(str(row["classification"]) for row in adjudications)
    adjudication_by_key = {
        (
            str(row["sourceBookCode"]),
            int(row["chapter"]),
            int(row["verseStart"]),
            int(row["verseEnd"]),
        ): row
        for row in adjudications
    }
    speech_mismatches: list[str] = []
    for code, _collection, book_id in BOOKS:
        source = sources[code]["verses"]
        for key, (_app_plain, app_marked) in app_by_code[code].items():
            source_plain, source_marked = source[key]
            if _app_plain == source_plain:
                wanted = j_mask(source_marked)
            else:
                row = adjudication_by_key.get((code, *key))
                if row is None or row["strictEquivalent"] is not True:
                    raise ReviewError(f"speech audit lacks presentation proof: {book_id} {key}")
                wanted = j_mask(
                    transplant_reviewed_presentation_jesus_tags(app_marked, source_marked, row)
                )
            if j_mask(app_marked) != wanted:
                speech_mismatches.append(make_reference(book_id, key))
    app_apparatus_spans = sum(len(row["approvedAppApparatus"]) for row in adjudications)
    return {
        "schemaVersion": 1,
        "language": "hi",
        "edition": "Indian Revised Version Hindi 2019",
        "sourceUrl": SOURCE_URL,
        "sourceArchiveSha256": digest,
        "sourceLicense": "Creative Commons Attribution-ShareAlike 4.0 International",
        "sourceLicenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
        "sourceRightsUrl": "https://ebible.org/hin2017/copyright.htm",
        "comparison": (
            "NFC and ordinary whitespace normalization first; then only exact reviewed "
            "cross-reference spans and source-export whitespace proven by raw inline USFM "
            "presentation markers. No word, punctuation, or broad lexical normalization."
        ),
        "jesusWordBoundaryAudit": {
            "verseUnitsChecked": app_lines,
            "exactTextUnitsChecked": exact,
            "reviewedPresentationUnitsChecked": len(adjudications),
            "initialReviewedPresentationMismatches": (
                INITIAL_REVIEWED_PRESENTATION_SPEECH_MISMATCHES
            ),
            "initialReviewedPresentationMatches": (
                len(adjudications) - INITIAL_REVIEWED_PRESENTATION_SPEECH_MISMATCHES
            ),
            "appOnlyApparatusSpansForcedNarration": app_apparatus_spans,
            "remainingMismatches": len(speech_mismatches),
            "remainingMismatchReferences": speech_mismatches,
            "method": (
                "Exact source speech flags for strict matches; exact reviewed apparatus "
                "removal plus non-whitespace code-point alignment for presentation-only "
                "differences. App-only apparatus is forced to narration."
            ),
        },
        "summary": {
            "appVerseUnits": app_lines,
            "sourceVerseUnits": source_lines,
            "strictExact": exact,
            "strictDifferences": len(adjudications),
            "presentationOnly": sum(counts[name] for name in PRESENTATION_CLASSES),
            "unresolvedLexical": counts["unresolved_lexical"],
            "repairedLexical": len(repairs),
            "crossReferenceApparatus": counts["cross_reference_apparatus"],
            "sourceExportWhitespace": counts["source_export_whitespace"],
            "crossReferenceAndWhitespace": counts[
                "cross_reference_apparatus_and_source_export_whitespace"
            ],
        },
        "reviewedRepairs": repairs,
        "adjudications": adjudications,
    }


def html_text(url: str) -> str:
    response = requests.get(url, timeout=120)
    response.raise_for_status()
    decoded = response.content.decode("utf-8-sig")
    return normalize(html.unescape(re.sub(r"<[^>]+>", " ", decoded)))


def verify_selected_html(review: dict[str, object]) -> None:
    cache: dict[str, str] = {}
    for row in review["adjudications"]:
        if not isinstance(row, dict):
            raise ReviewError("invalid adjudication row")
        source_spans = row["approvedSourceApparatus"]
        needs_html = bool(source_spans) or row["classification"] == "unresolved_lexical"
        if not needs_html:
            continue
        url = str(row["sourceHtmlUrl"])
        if url not in cache:
            cache[url] = html_text(url)
        required = [str(span["text"]) for span in source_spans]
        if row["classification"] == "unresolved_lexical":
            required.append(str(row["sourceText"]))
        if not all(normalize(value) in cache[url] for value in required):
            raise ReviewError(f"official source HTML does not contain reviewed evidence: {row['reference']}")
        row["sourceHtmlVerified"] = True
    for repair in review["reviewedRepairs"]:
        url = str(repair["sourceHtmlUrl"])
        if url not in cache:
            cache[url] = html_text(url)
        if normalize(str(repair["repairedText"])) not in cache[url]:
            raise ReviewError(f"official source HTML does not contain repaired wording: {repair['reference']}")
        repair["sourceHtmlVerified"] = True


def check_review(current: dict[str, object], reviewed: dict[str, object]) -> dict[str, int]:
    if reviewed.get("schemaVersion") != 1:
        raise ReviewError("unsupported review schema")
    if reviewed.get("sourceArchiveSha256") != SOURCE_SHA256:
        raise ReviewError("review source hash is not the pinned archive hash")
    if current.get("jesusWordBoundaryAudit") != reviewed.get("jesusWordBoundaryAudit"):
        raise ReviewError("Jesus-word boundary audit record drift")
    if current.get("reviewedRepairs") != reviewed.get("reviewedRepairs"):
        # Verification flags exist only in the checked-in review. Compare the
        # immutable repair evidence separately from that flag.
        current_repairs = [{k: v for k, v in row.items() if k != "sourceHtmlVerified"}
                           for row in current.get("reviewedRepairs", [])]
        reviewed_repairs = [{k: v for k, v in row.items() if k != "sourceHtmlVerified"}
                            for row in reviewed.get("reviewedRepairs", [])]
        if current_repairs != reviewed_repairs:
            raise ReviewError("reviewed lexical repair record drift")
    for repair in reviewed.get("reviewedRepairs", []):
        if repair.get("sourceHtmlVerified") is not True:
            raise ReviewError(f"repair HTML evidence was not verified: {repair.get('reference')}")
    current_rows = {row["reference"]: row for row in current["adjudications"]}
    reviewed_rows = {row["reference"]: row for row in reviewed["adjudications"]}
    if len(reviewed_rows) != len(reviewed["adjudications"]):
        raise ReviewError("duplicate reference in checked-in review")
    if set(current_rows) != set(reviewed_rows):
        missing = sorted(set(reviewed_rows) - set(current_rows))
        added = sorted(set(current_rows) - set(reviewed_rows))
        raise ReviewError(f"strict difference inventory drift; removed={missing}, added={added}")

    counts: Counter[str] = Counter()
    for reference in sorted(current_rows):
        now = current_rows[reference]
        old = reviewed_rows[reference]
        locked_fields = (
            "sourceBookCode", "sourceBookFile", "bookId", "chapter", "verseStart",
            "verseEnd", "classification", "strictEquivalent", "appText", "sourceText",
            "approvedAppApparatus", "approvedSourceApparatus", "sourcePresentationMarkers",
            "approvedWhitespaceGaps", "sourceRawUsfm", "sourceHtmlUrl",
        )
        for field in locked_fields:
            if now[field] != old[field]:
                raise ReviewError(f"review drift at {reference}: {field}")
        for span in now["approvedAppApparatus"]:
            if span["sourceUsfmMarker"] != "bdit":
                raise ReviewError(f"app apparatus lacks bdit source proof at {reference}")
        for span in now["approvedSourceApparatus"]:
            if span["sourceUsfmMarker"] not in {"it", "plain"}:
                raise ReviewError(f"unexpected source apparatus marker at {reference}")
        if (now["approvedSourceApparatus"] or now["classification"] == "unresolved_lexical") and \
                old.get("sourceHtmlVerified") is not True:
            raise ReviewError(f"official HTML evidence was not verified at {reference}")
        app_residual = remove_reviewed_spans(
            str(now["appText"]), list(now["approvedAppApparatus"])
        )
        source_residual = remove_reviewed_spans(
            str(now["sourceText"]), list(now["approvedSourceApparatus"])
        )
        classification = str(now["classification"])
        if classification == "cross_reference_apparatus":
            if app_residual != source_residual:
                raise ReviewError(f"apparatus adjudication no longer reconciles {reference}")
        elif classification in {
            "source_export_whitespace",
            "cross_reference_apparatus_and_source_export_whitespace",
        }:
            if app_residual == source_residual or without_whitespace(app_residual) != without_whitespace(source_residual):
                raise ReviewError(f"whitespace adjudication no longer reconciles {reference}")
            if "it" not in now["sourcePresentationMarkers"]:
                raise ReviewError(f"whitespace proof lost its source marker at {reference}")
            gaps = whitespace_gap_differences(app_residual, source_residual)
            raw_visible, boundaries = raw_visible_with_it_boundaries(str(now["sourceRawUsfm"]))
            if gaps != now["approvedWhitespaceGaps"] or not set(gaps) <= boundaries or \
                    without_whitespace(raw_visible) != without_whitespace(app_residual):
                raise ReviewError(f"whitespace boundary proof drifted at {reference}")
        elif classification == "unresolved_lexical":
            if without_whitespace(app_residual) == without_whitespace(source_residual):
                raise ReviewError(f"lexical adjudication became presentation-only at {reference}")
        else:
            raise ReviewError(f"unknown classification at {reference}: {classification}")
        counts[classification] += 1
    return dict(counts)


def markdown_report(review: dict[str, object]) -> str:
    summary = review["summary"]
    speech = review["jesusWordBoundaryAudit"]
    adjudications = review["adjudications"]
    by_book: dict[str, Counter[str]] = defaultdict(Counter)
    for row in adjudications:
        by_book[str(row["bookId"])][str(row["classification"])] += 1
    lexical = [row for row in adjudications if row["classification"] == "unresolved_lexical"]
    lines = [
        "# Hindi IRV source-difference review",
        "",
        "This review compares every Hindi app verse with the pinned official eBible",
        "`hin2017` USFM archive. The audit itself is read-only; the one approved",
        "source-backed lexical repair is recorded below.",
        "",
        f"- Source: <{review['sourceUrl']}>",
        f"- SHA-256: `{review['sourceArchiveSha256']}`",
        f"- License: [{review['sourceLicense']}]({review['sourceLicenseUrl']})",
        f"- Rights: <{review['sourceRightsUrl']}>",
        f"- Verse units: {summary['appVerseUnits']:,} app and {summary['sourceVerseUnits']:,} source",
        f"- Strict exact matches: {summary['strictExact']:,}",
        f"- Strict differences reviewed: {summary['strictDifferences']:,}",
        f"- Presentation-only differences: {summary['presentationOnly']:,}",
        f"- Repaired lexical differences: {summary['repairedLexical']:,}",
        f"- Unresolved lexical differences: {summary['unresolvedLexical']:,}",
        "",
        "## Decision",
        "",
        f"The current corpus has {summary['strictDifferences']:,} strict differences",
        "after the one lexical repair, which remains protected by exact old-text and",
        "pinned-source guards. Every current difference is presentation-only:",
        f"{summary['crossReferenceApparatus']:,} contain only reviewed inline",
        f"cross-reference apparatus, {summary['sourceExportWhitespace']:,} contain only",
        "source-export whitespace at `\\it` presentation-marker boundaries, and",
        f"{summary['crossReferenceAndWhitespace']:,} contain both.",
        "The comparator removes",
        "only the exact apparatus spans recorded in the JSON. It permits whitespace",
        "equivalence only after current app text, parsed source text, raw USFM, and",
        "the reviewed spans all match byte-for-byte. It never ignores words or",
        "punctuation.",
        "",
        "Every app-only apparatus span is bound to exact offsets and a raw-USFM",
        "`\\bdit` marker. Source-only apparatus and the lexical repair also carry",
        "verified official eBible HTML evidence in the JSON.",
        "",
        "## Jesus-word boundary audit",
        "",
        f"The boundary pass checked all {speech['verseUnitsChecked']:,} verse units:",
        f"{speech['exactTextUnitsChecked']:,} strict-text matches and",
        f"{speech['reviewedPresentationUnitsChecked']:,} reviewed presentation differences.",
        f"The initial pass found {speech['initialReviewedPresentationMismatches']:,}",
        "presentation-unit speech-mask mismatches; it found",
        f"{speech['initialReviewedPresentationMatches']:,} already correct.",
        f"The repair now leaves {speech['remainingMismatches']:,} mismatches.",
        f"All {speech['appOnlyApparatusSpansForcedNarration']:,} exact app-only apparatus",
        "spans are excluded from source alignment and forced to narration. The transfer",
        "preserves every non-`[J]` character and fails closed on offset drift, wording",
        "drift, or any other app display tag.",
        "",
        "## Applied lexical repair",
        "",
    ]
    for repair in review["reviewedRepairs"]:
        lines.extend([
            f"### {repair['reference']}",
            "",
            f"- Old app text: {repair['oldText']}",
            f"- Repaired text: {repair['repairedText']}",
            f"- Pinned source archive: <{repair['sourceArchiveUrl']}>",
            f"- Official HTML: <{repair['sourceHtmlUrl']}>",
            f"- Current linked edition: <{repair['linkedEditionUrl']}>",
            "- Guard: the repair runs only when both the old app text and pinned",
            "  source text match their exact reviewed strings. The already-fixed",
            "  string is an idempotent no-op.",
            "",
        ])
    lines.extend([
        "## Unresolved lexical differences",
        "",
    ])
    if not lexical:
        lines.extend(["None.", ""])
    for row in lexical:
        lines.extend([
            f"### {row['reference']}",
            "",
            f"- App: {row['appText']}",
            f"- Pinned source: {row['sourceText']}",
            f"- Official HTML: <{row['sourceHtmlUrl']}>",
            "- Current linked IRVHin edition: <https://www.bible.com/bible/1980/REV.12.IRVHin>",
            "- Adjudication: substantive wording difference. Do not remove it as",
            "  apparatus or whitespace; Scripture repair requires a separate source",
            "  decision.",
            "",
        ])
    lines.extend([
        "## Counts by book",
        "",
        "| Book | Apparatus | Whitespace | Both | Lexical | Total |",
        "|---|---:|---:|---:|---:|---:|",
    ])
    for book_id in sorted(by_book):
        counts = by_book[book_id]
        values = [
            counts["cross_reference_apparatus"],
            counts["source_export_whitespace"],
            counts["cross_reference_apparatus_and_source_export_whitespace"],
            counts["unresolved_lexical"],
        ]
        lines.append(f"| {book_id} | {values[0]} | {values[1]} | {values[2]} | {values[3]} | {sum(values)} |")
    lines.extend([
        "",
        "## Reproduction",
        "",
        "```text",
        "python -B tools/hindi_source_difference_review.py --check",
        "python -B -m unittest tools/test_hindi_source_difference_review.py -v",
        "```",
        "",
        "Use `--archive PATH` to check a local copy of the pinned archive. `--write-review`",
        "regenerates only this report directory and requires `--verify-html` so the",
        "source-only apparatus and reviewed repair source wording are checked",
        "against the official rendered chapters.",
        "",
        "The complete 364-reference adjudication, exact texts, reviewed removal spans,",
        "raw USFM evidence, and HTML verification flags are in",
        "`source_difference_review.json`.",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, help="local pinned hin2017 USFM archive")
    parser.add_argument("--check", action="store_true", help="check the current corpus against the reviewed JSON")
    parser.add_argument("--write-review", action="store_true", help="regenerate the review JSON and Markdown")
    parser.add_argument("--verify-html", action="store_true", help="verify selected evidence against official eBible HTML")
    args = parser.parse_args()
    if args.check and args.write_review:
        parser.error("choose --check or --write-review")
    if args.write_review and not args.verify_html:
        parser.error("--write-review requires --verify-html")
    if not args.check and not args.write_review:
        args.check = True

    repo = Path(__file__).resolve().parents[1]
    try:
        body, digest = load_source_archive(args.archive)
        current = build_review(repo, body, digest)
        if args.write_review:
            verify_selected_html(current)
            REPORT_DIR.mkdir(parents=True, exist_ok=True)
            REVIEW_PATH.write_text(
                json.dumps(current, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8", newline="\n",
            )
            SUMMARY_PATH.write_text(markdown_report(current), encoding="utf-8", newline="\n")
            result = {"mode": "write-review", **current["summary"]}
        else:
            reviewed = json.loads(REVIEW_PATH.read_text(encoding="utf-8"))
            counts = check_review(current, reviewed)
            result = {"mode": "check", **current["summary"], "classifications": counts}
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (
        AuditError, ReviewError, OSError, UnicodeError, ValueError, KeyError,
        requests.RequestException, zipfile.BadZipFile,
    ) as exc:
        print(f"Hindi source-difference review failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
