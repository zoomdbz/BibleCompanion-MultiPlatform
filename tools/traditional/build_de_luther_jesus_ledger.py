#!/usr/bin/env python3
"""Compile the pinned LUTD/KJV review into the German importer ledger."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from jesus_word_spans import ReviewedJesusSpans, sha256_text
from build_deepbible_jesus_ledger import select_span


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
EVIDENCE = ROOT / "tools/reports/de_luther1912_deepbible_evidence.json"
OVERRIDES = HERE / "jesus_word_spans/de_luther1912_overrides.json"
OUTPUT = HERE / "jesus_word_spans/de_luther1912.json"
PINNED_EVIDENCE_SHA256 = "7E595E01D34C24BEDDF2182FE65E2E76E5FEEB8C57F1D5CE9ABA8A9538EDEA31"
FULL_EVIDENCE = ROOT / "tools/reports/de_luther1912_deepbible_full_evidence.json"
PINNED_FULL_EVIDENCE_SHA256 = "A9AC172F5E007E2A1E1B0B0CA757464B028BC7340EE7BE0F327F3D5B33FA1724"
REQUIRED_FULL_OVERRIDES = {"matthew 19:5", "mark 12:37"}
SPECIAL_SOURCE_REVIEW = {"mark 5:39"}


def reference(row: dict) -> str:
    return f"{row['bookId']} {row['chapter']}:{row['verse']}"


def build() -> dict:
    evidence_bytes = EVIDENCE.read_bytes()
    digest = hashlib.sha256(evidence_bytes).hexdigest().upper()
    if digest != PINNED_EVIDENCE_SHA256:
        raise ValueError(f"German DeepBible evidence drift: {digest}")
    evidence = json.loads(evidence_bytes)
    full_bytes = FULL_EVIDENCE.read_bytes()
    full_digest = hashlib.sha256(full_bytes).hexdigest().upper()
    if full_digest != PINNED_FULL_EVIDENCE_SHA256:
        raise ValueError(f"German full-verse DeepBible evidence drift: {full_digest}")
    full_evidence = json.loads(full_bytes)
    if full_evidence["authorityCount"] != 1402:
        raise ValueError("German full-verse KJV authority audit incomplete")
    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    if (evidence["language"], evidence["editionId"], evidence["source"], evidence["candidateCount"]) != (
        "de", "luther1912", "LUTD", 626,
    ):
        raise ValueError("German DeepBible evidence identity or candidate count drift")
    if (overrides["language"], overrides["editionId"]) != ("de", "luther1912"):
        raise ValueError("German Jesus-word override identity drift")
    review_by_ref = {row["reference"]: row for row in overrides["rows"]}
    if len(review_by_ref) != len(overrides["rows"]):
        raise ValueError("Duplicate German manual speech review")
    required = {
        reference(row) for row in evidence["rows"]
        if row["status"] not in {"exact_text", "presentation_only"}
    } | SPECIAL_SOURCE_REVIEW
    if set(review_by_ref) != required:
        raise ValueError(f"German manual review set drift: missing={required - set(review_by_ref)}, extra={set(review_by_ref) - required}")
    full_overrides = {row["reference"]: row for row in overrides.get("fullOverrides", [])}
    if len(full_overrides) != len(overrides.get("fullOverrides", [])) or set(full_overrides) != REQUIRED_FULL_OVERRIDES:
        raise ValueError("German full-verse override set drift")
    full_by_ref = {reference(row): row for row in full_evidence["rows"]}
    output_rows = []
    for row in evidence["rows"]:
        ref = reference(row)
        override = review_by_ref.get(ref)
        if override:
            spans = [part if isinstance(part, dict) else {"exactText": part} for part in override["spans"]]
            method = "manual-language-review"
            reason = override["reason"]
        else:
            spans = [{"exactText": text} for text in row["spans"]]
            method = "exact-target-text-KJV-shape-and-LUTD-span-concordance"
            reason = "Exact target wording and KJV speech shape agree with the LUTD source's own red-letter spans."
        if not spans:
            raise ValueError(f"Missing reviewed German speech: {ref}")
        output_rows.append({
            "collection": row["collection"],
            "bookId": row["bookId"],
            "chapter": row["chapter"],
            "verse": row["verse"],
            "sourceTextSha256": row["sourceTextSha256"],
            "spans": spans,
            "review": {
                "status": "reviewed",
                "method": method,
                "evidence": {
                    "kjvTextSha256": row["kjvTextSha256"],
                    "kjvSpeechShape": row["kjvSpeechShape"],
                    "externalSource": "DeepBible LUTD",
                    "externalChapterSha256": row["sourceChapterSha256"],
                    "externalStatus": row["status"],
                    "reason": reason,
                },
            },
        })
    for ref, spec in full_overrides.items():
        source = full_by_ref.get(ref)
        if source is None:
            raise ValueError(f"German full override lacks KJV authority: {ref}")
        book_id, location = ref.split(" ")
        chapter_number, verse_number = map(int, location.split(":"))
        path = ROOT / "shared/assets/books/editions/de/luther1912/new_testament" / f"{book_id}.json"
        book = json.loads(path.read_text(encoding="utf-8"))
        target = next(v for ch in book["chapters"] if ch["number"] == chapter_number for v in ch["verses"] if v["verse"] == verse_number)
        raw = target["text"].replace("[J]", "").replace("[/J]", "")
        if sha256_text(raw) != spec["targetTextSha256"]:
            raise ValueError(f"German full override text drift: {ref}")
        visible = raw.replace("[ADD]", "").replace("[/ADD]", "").replace("[DN]", "").replace("[/DN]", "")
        output_rows.append({
            "collection": "new_testament", "bookId": book_id, "chapter": chapter_number, "verse": verse_number,
            "sourceTextSha256": spec["targetTextSha256"], "overrideInherited": True,
            "spans": [select_span(visible, part) for part in spec["spans"]],
            "review": {"status": "reviewed", "method": "manual-language-review-KJV-full-correction",
                       "evidence": {"externalSource": "DeepBible LUTD", "externalChapterSha256": source["sourceChapterSha256"],
                                    "externalStatus": source["status"], "fullEvidenceSha256": full_digest,
                                    "reason": spec["reason"]}},
        })
    ledger = {
        "schemaVersion": 1,
        "language": "de",
        "editionId": "luther1912",
        "semanticAuthority": "Pinned en/kjv1769 CrossWire wj source",
        "externalEvidence": {
            "source": "DeepBible LUTD (ph4 module)",
            "endpoint": evidence["endpoint"],
            "report": "tools/reports/de_luther1912_deepbible_evidence.json",
            "reportSha256": PINNED_EVIDENCE_SHA256,
            "fullReport": "tools/reports/de_luther1912_deepbible_full_evidence.json",
            "fullReportSha256": PINNED_FULL_EVIDENCE_SHA256,
        },
        "rows": output_rows,
    }
    if len(output_rows) != 626 + len(REQUIRED_FULL_OVERRIDES):
        raise ValueError(f"Expected 626 German mixed candidates and full corrections, got {len(output_rows)}")
    return ledger


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify existing ledger bytes without writing")
    args = parser.parse_args()
    ledger = build()
    serialized = (json.dumps(ledger, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if args.check:
        if OUTPUT.read_bytes() != serialized:
            raise ValueError("German Jesus-word ledger does not match pinned evidence")
    else:
        OUTPUT.write_bytes(serialized)
    reviewed = ReviewedJesusSpans.load(ROOT, "de", "luther1912")
    target = ROOT / "shared/assets/books/editions/de/luther1912/new_testament"
    expected = set()
    expected_full = set()
    for path in target.glob("*.json"):
        book = json.loads(path.read_text(encoding="utf-8"))
        for chapter in book["chapters"]:
            for verse in chapter["verses"]:
                key = ("new_testament", path.stem, chapter["number"], verse["verse"])
                if key not in reviewed.rows:
                    continue
                (expected_full if reviewed.rows[key].get("overrideInherited") else expected).add(key)
                raw = verse["text"].replace("[J]", "").replace("[/J]", "")
                reviewed.apply(*key, raw, verse.get("verseEnd", verse["verse"]))
    reviewed.validate_coverage(expected, expected_full)
    print(json.dumps({"reviewed": len(reviewed.used), "ledgerSha256": sha256_text(serialized.decode("utf-8"))}))


if __name__ == "__main__":
    main()
