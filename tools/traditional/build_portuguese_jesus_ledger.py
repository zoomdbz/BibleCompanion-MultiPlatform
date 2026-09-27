#!/usr/bin/env python3
"""Compile reviewed Almeida 1911 J spans from pinned AA/ARC and KJV evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from build_deepbible_jesus_ledger import select_span
from jesus_word_spans import ReviewedJesusSpans, sha256_text


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
MARKER = re.compile(r"\[/?(?:J|DN|ADD)\]")
FULL_OVERRIDES = {"matthew 19:5", "mark 12:37"}


def pinned_json(path: Path, expected_sha256: str) -> dict:
    content = path.read_bytes()
    digest = hashlib.sha256(content).hexdigest().upper()
    if digest != expected_sha256:
        raise ValueError(f"Pinned Portuguese evidence drift at {path}: {digest}")
    return json.loads(content)


def reference(row: dict) -> str:
    return f"{row['bookId']} {row['chapter']}:{row['verse']}"


def build() -> dict:
    override_path = HERE / "jesus_word_spans/pt_almeida1911_overrides.json"
    overrides = json.loads(override_path.read_text(encoding="utf-8"))
    if (overrides["language"], overrides["editionId"]) != ("pt", "almeida1911"):
        raise ValueError("Portuguese review identity drift")
    alignment_path = ROOT / "tools/reports/pt_almeida1911_red_alignment.json"
    full_path = ROOT / "tools/reports/pt_almeida1911_red_full_evidence.json"
    alignment = pinned_json(alignment_path, overrides["alignmentReportSha256"])
    full = pinned_json(full_path, overrides["fullReportSha256"])
    if alignment["candidateCount"] != 626 or full["authorityCount"] != 1402:
        raise ValueError("KJV semantic authority coverage drift")
    if (alignment["language"], alignment["editionId"], full["language"], full["editionId"]) != (
        "pt", "almeida1911", "pt", "almeida1911"
    ):
        raise ValueError("Portuguese evidence identity drift")
    review_rows = [row for row in alignment["rows"] if row["status"] == "review"]
    if len(review_rows) != overrides["reviewedExceptionCount"]:
        raise ValueError("Portuguese exception-review coverage drift")
    aa_indices = set(overrides["acceptAA"])
    manual = {int(index): specs for index, specs in overrides["manual"].items()}
    all_indices = set(range(len(review_rows)))
    if not (aa_indices | set(manual)) <= all_indices or aa_indices & set(manual):
        raise ValueError("Unbound or duplicate Portuguese review decision")
    rows = []
    seen = set()
    review_index = 0
    for candidate in alignment["rows"]:
        ref = reference(candidate)
        if ref in seen:
            raise ValueError(f"Duplicate Portuguese mixed authority: {ref}")
        seen.add(ref)
        visible = MARKER.sub("", candidate["targetText"])
        if sha256_text(candidate["targetText"]) != candidate["sourceTextSha256"]:
            raise ValueError(f"Portuguese target context hash drift: {ref}")
        if candidate["status"] == "dual_exact_boundary":
            specs = [{"exactText": span} for span in candidate["spans"]]
            method = "two-red-letter-witnesses-exact-boundary"
            reason = "AA and ARC preserve the same exact Almeida1911 boundaries and the pinned KJV speech shape."
        elif candidate["status"] == "review":
            index = review_index
            review_index += 1
            if index in manual:
                specs = manual[index]
                method = "Portuguese-KJV-semantic-manual-boundary"
                reason = "The target grammar and KJV WJ require a correction to the Portuguese red-letter witnesses."
            else:
                selected = "AA" if index in aa_indices else "ARC"
                witness = candidate["aligned"][selected]
                if witness["status"] != "mapped":
                    raise ValueError(f"Reviewed {selected} boundary unavailable at {ref}")
                specs = [{"exactText": span} for span in witness["spans"]]
                method = f"reviewed-{selected}-red-letter-boundary"
                reason = "Reviewer checked the selected red-letter run against target Portuguese grammar and KJV WJ."
        else:
            raise ValueError(f"Unreviewed Portuguese candidate: {ref}")
        try:
            spans = [select_span(visible, spec) for spec in specs]
        except ValueError as exc:
            raise ValueError(f"Portuguese reviewed selector {ref}: {exc}") from exc
        if not spans:
            raise ValueError(f"No Portuguese Jesus speech selected at {ref}")
        rows.append({
            "collection": candidate["collection"], "bookId": candidate["bookId"],
            "chapter": candidate["chapter"], "verse": candidate["verse"],
            "sourceTextSha256": candidate["sourceTextSha256"], "spans": spans,
            "review": {"status": "reviewed", "method": method,
                       "evidence": {"kjvTextSha256": candidate["kjvTextSha256"],
                                    "kjvSpeechShape": candidate["kjvSpeechShape"],
                                    "alignmentReportSha256": overrides["alignmentReportSha256"],
                                    "reference": ref, "reason": reason}},
        })
    if len(rows) != 626 or review_index != 159:
        raise ValueError("Incomplete Portuguese KJV mixed coverage")
    full_by_ref = {reference(row): row for row in full["rows"]}
    if len(full_by_ref) != 1402:
        raise ValueError("Duplicate KJV full authorities")
    full_overrides = {row["reference"]: row for row in overrides["fullOverrides"]}
    if len(full_overrides) != len(overrides["fullOverrides"]) or set(full_overrides) != FULL_OVERRIDES:
        raise ValueError("Portuguese full-verse override review drift")
    for ref, spec in full_overrides.items():
        source = full_by_ref[ref]
        visible = MARKER.sub("", source["targetText"])
        spans = [select_span(visible, selector) for selector in spec["spans"]]
        rows.append({
            "collection": "new_testament", "bookId": source["bookId"],
            "chapter": source["chapter"], "verse": source["verse"],
            "sourceTextSha256": source["targetTextSha256"],
            "overrideInherited": True, "spans": spans,
            "review": {"status": "reviewed", "method": "Portuguese-KJV-full-narrator-correction",
                       "evidence": {"fullReportSha256": overrides["fullReportSha256"],
                                    "AA": source["witnesses"]["AA"]["speechShape"],
                                    "ARC": source["witnesses"]["ARC"]["speechShape"],
                                    "reason": spec["reason"]}},
        })
    return {"schemaVersion": 1, "language": "pt", "editionId": "almeida1911",
            "semanticAuthority": "Pinned en/kjv1769 CrossWire wj source",
            "externalEvidence": {"source": "ph4 MyBible AA and ARC",
                                 "archiveSha256": full["archiveSha256"],
                                 "alignmentReport": alignment_path.relative_to(ROOT).as_posix(),
                                 "alignmentReportSha256": overrides["alignmentReportSha256"],
                                 "fullReport": full_path.relative_to(ROOT).as_posix(),
                                 "fullReportSha256": overrides["fullReportSha256"]},
            "rows": rows}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    ledger = build()
    path = HERE / "jesus_word_spans/pt_almeida1911.json"
    data = (json.dumps(ledger, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if args.check:
        if path.read_bytes() != data:
            raise ValueError("Compiled Portuguese ledger differs from pinned reviewed evidence")
    else:
        path.write_bytes(data)
    reviewed = ReviewedJesusSpans.load(ROOT, "pt", "almeida1911")
    target_root = ROOT / "shared/assets/books/editions/pt/almeida1911/new_testament"
    for book_path in target_root.glob("*.json"):
        book = json.loads(book_path.read_text(encoding="utf-8"))
        for chapter in book["chapters"]:
            for verse in chapter["verses"]:
                key = ("new_testament", book_path.stem, chapter["number"], verse["verse"])
                if key in reviewed.rows:
                    raw = verse["text"].replace("[J]", "").replace("[/J]", "")
                    reviewed.apply(*key, raw, verse.get("verseEnd", verse["verse"]))
    reviewed.validate_coverage(
        {key for key, row in reviewed.rows.items() if not row.get("overrideInherited")},
        {key for key, row in reviewed.rows.items() if row.get("overrideInherited")},
    )
    print(json.dumps({"mixedAuthorities": 626, "fullOverrides": 2,
                      "reviewed": len(reviewed.used),
                      "ledgerSha256": hashlib.sha256(data).hexdigest().upper()}))


if __name__ == "__main__":
    main()
