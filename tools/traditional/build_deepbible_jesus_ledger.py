#!/usr/bin/env python3
"""Compile exact DeepBible J spans plus language-reviewed exceptions into a ledger."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from jesus_word_spans import ReviewedJesusSpans, sha256_text


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CONFIG = {
    ("it", "diodati1885"): {
        "source": "GDB",
        "evidenceSha256": "F566CDFDE4120230F5879BAE1CD76FF6AEB7E9AA10515A10E6247FA3C0DF7CBE",
        "fullEvidenceSha256": "F704446D11C16E517D30E541AF38B6E4CC9C4A05374D2A7BA755CAB11AF6A8C7",
        "additionalReview": set(),
        "requiredFullOverrides": {"matthew 19:5", "matthew 24:15", "mark 2:11", "mark 12:37", "mark 13:14", "luke 19:25"},
    },
    ("es", "rv1909"): {
        "source": "RVR",
        "evidenceSha256": "D26621ABEED001243C5AC20369E50F2C809B00F6B7248E60E3966F9BED30CDB1",
        "fullEvidenceSha256": "53EB0AC845AADEED2D61C25DB1EAFF60484DEAE9AE2470D3971FB2693D78F483",
        "additionalReview": set(),
        "requiredFullOverrides": {"matthew 19:5", "matthew 24:15", "mark 12:37", "mark 13:14", "luke 19:25"},
    },
}
VISIBLE_MARKER = re.compile(r"\[/?(?:J|DN|ADD)\]")


def select_span(text: str, spec: dict) -> dict:
    if "exactText" in spec:
        return spec
    after = spec.get("after", "")
    before = spec.get("before", "")
    if after and text.count(after) != 1:
        raise ValueError(f"Ambiguous language-specific after-anchor: {after!r}")
    start = text.index(after) + len(after) if after else 0
    if before:
        if text.count(before, start) != 1:
            raise ValueError(f"Ambiguous language-specific before-anchor: {before!r}")
        end = text.index(before, start)
    else:
        end = len(text)
    exact = text[start:end].strip()
    if not exact:
        raise ValueError(f"Empty reviewed speech between {after!r} and {before!r}")
    return {"exactText": exact}


def build(language: str, edition_id: str) -> dict:
    policy = CONFIG[(language, edition_id)]
    evidence_path = ROOT / "tools/reports" / f"{language}_{edition_id}_deepbible_evidence.json"
    evidence_bytes = evidence_path.read_bytes()
    digest = hashlib.sha256(evidence_bytes).hexdigest().upper()
    if digest != policy["evidenceSha256"]:
        raise ValueError(f"DeepBible evidence drift: {digest}")
    evidence = json.loads(evidence_bytes)
    full_evidence_path = ROOT / "tools/reports" / f"{language}_{edition_id}_deepbible_full_evidence.json"
    full_evidence_bytes = full_evidence_path.read_bytes()
    full_digest = hashlib.sha256(full_evidence_bytes).hexdigest().upper()
    if full_digest != policy["fullEvidenceSha256"]:
        raise ValueError(f"DeepBible full-verse evidence drift: {full_digest}")
    full_evidence = json.loads(full_evidence_bytes)
    overrides_path = HERE / "jesus_word_spans" / f"{language}_{edition_id}_overrides.json"
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    if (evidence["language"], evidence["editionId"], evidence["source"], evidence["candidateCount"]) != (
        language, edition_id, policy["source"], 626,
    ):
        raise ValueError("DeepBible evidence identity or candidate count drift")
    if (overrides["language"], overrides["editionId"]) != (language, edition_id):
        raise ValueError("Override identity drift")
    by_ref = {row["reference"]: row for row in overrides["rows"]}
    if len(by_ref) != len(overrides["rows"]):
        raise ValueError("Duplicate manual speech review")
    required = {
        f"{row['bookId']} {row['chapter']}:{row['verse']}"
        for row in evidence["rows"]
        if row["status"] not in {"exact_text", "presentation_only", "aligned_text"}
    } | policy["additionalReview"]
    if set(by_ref) != required:
        raise ValueError(f"Manual review set drift: missing={required - set(by_ref)}, extra={set(by_ref) - required}")
    full_overrides = {row["reference"]: row for row in overrides.get("fullOverrides", [])}
    if len(full_overrides) != len(overrides.get("fullOverrides", [])) or set(full_overrides) != policy["requiredFullOverrides"]:
        raise ValueError("Full-verse manual review set drift")
    full_by_ref = {f"{row['bookId']} {row['chapter']}:{row['verse']}": row for row in full_evidence["rows"]}
    if full_evidence["authorityCount"] != 1402 or len(full_by_ref) != 1402:
        raise ValueError("Full-verse KJV authority audit incomplete")
    output_rows = []
    evidence_by_ref = {f"{row['bookId']} {row['chapter']}:{row['verse']}": row for row in evidence["rows"]}
    for row in evidence["rows"]:
        ref = f"{row['bookId']} {row['chapter']}:{row['verse']}"
        override = by_ref.get(ref)
        if override:
            no_speech = override.get("noTargetSpeech") is True
            relocated = override.get("speechRelocatedTo")
            visible = VISIBLE_MARKER.sub("", row["targetText"])
            spans = [select_span(visible, spec) for spec in override.get("spans", [])]
            reason = override["reason"]
            method = "manual-language-review"
        else:
            no_speech = False
            relocated = None
            spans = [{"exactText": text} for text in row["spans"]]
            reason = "External source J spans agree with KJV wj shape on exact target wording or reviewed presentation-equivalent text."
            method = "target-text-KJV-shape-and-source-span-concordance"
        if not spans and not no_speech and relocated is None:
            raise ValueError(f"Missing reviewed speech at {ref}")
        result = {
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
                    "externalSource": policy["source"],
                    "externalChapterSha256": row["sourceChapterSha256"],
                    "externalStatus": row["status"],
                    "reason": reason,
                },
            },
        }
        if no_speech:
            result["noTargetSpeech"] = True
            result["reason"] = reason
        if relocated is not None:
            result["speechRelocatedTo"] = relocated
        output_rows.append(result)
    if len(output_rows) != 626:
        raise ValueError("Compiled ledger does not cover 626 source-mixed coordinates")
    for spec in overrides.get("supplementalRows", []):
        reference = spec["reference"]
        authority = spec["supplementalFor"]
        source = evidence_by_ref.get(authority)
        if source is None:
            raise ValueError(f"Supplemental authority is not a KJV mixed candidate: {authority}")
        book_id, location = reference.split(" ")
        chapter, verse = map(int, location.split(":"))
        target_path = ROOT / "shared/assets/books/editions" / language / edition_id / "new_testament" / f"{book_id}.json"
        target_book = json.loads(target_path.read_text(encoding="utf-8"))
        target = next(v for c in target_book["chapters"] if c["number"] == chapter for v in c["verses"] if v["verse"] == verse)
        raw = target["text"].replace("[J]", "").replace("[/J]", "")
        if sha256_text(raw) != spec["targetTextSha256"]:
            raise ValueError(f"Supplemental target text drift: {reference}")
        spans = [select_span(VISIBLE_MARKER.sub("", raw), selector) for selector in spec["spans"]]
        output_rows.append({
            "collection": "new_testament", "bookId": book_id, "chapter": chapter, "verse": verse,
            "sourceTextSha256": spec["targetTextSha256"], "spans": spans,
            "supplementalFor": [{"collection": "new_testament", "bookId": source["bookId"],
                                 "chapter": source["chapter"], "verse": source["verse"]}],
            "review": {"status": "reviewed", "method": "manual-language-review-relocated-KJV-authority",
                       "evidence": {"kjvTextSha256": source["kjvTextSha256"],
                                    "externalSource": policy["source"],
                                    "externalChapterSha256": source["sourceChapterSha256"],
                                    "reason": spec["reason"]}},
        })
    for reference, spec in full_overrides.items():
        source = full_by_ref.get(reference)
        if source is None:
            raise ValueError(f"Full override lacks KJV full authority: {reference}")
        book_id, location = reference.split(" ")
        chapter, verse = map(int, location.split(":"))
        target_path = ROOT / "shared/assets/books/editions" / language / edition_id / "new_testament" / f"{book_id}.json"
        target_book = json.loads(target_path.read_text(encoding="utf-8"))
        target = next(v for c in target_book["chapters"] if c["number"] == chapter for v in c["verses"] if v["verse"] == verse)
        raw = target["text"].replace("[J]", "").replace("[/J]", "")
        if sha256_text(raw) != spec["targetTextSha256"]:
            raise ValueError(f"Full override target text drift: {reference}")
        spans = [select_span(VISIBLE_MARKER.sub("", raw), selector) for selector in spec.get("spans", [])]
        row = {
            "collection": "new_testament", "bookId": book_id, "chapter": chapter, "verse": verse,
            "sourceTextSha256": spec["targetTextSha256"], "overrideInherited": True,
            "spans": spans,
            "review": {"status": "reviewed", "method": "manual-language-review-KJV-full-correction",
                       "evidence": {"externalSource": policy["source"],
                                    "externalChapterSha256": source["sourceChapterSha256"],
                                    "externalStatus": source["status"], "fullEvidenceSha256": full_digest,
                                    "reason": spec["reason"]}},
        }
        if spec.get("noTargetSpeech") is True:
            row["noTargetSpeech"] = True
            row["reason"] = spec["reason"]
        output_rows.append(row)
    return {
        "schemaVersion": 1,
        "language": language,
        "editionId": edition_id,
        "semanticAuthority": "Pinned en/kjv1769 CrossWire wj source",
        "externalEvidence": {
            "source": policy["source"],
            "endpoint": evidence["endpoint"],
            "report": evidence_path.relative_to(ROOT).as_posix(),
            "reportSha256": digest,
            "fullReport": full_evidence_path.relative_to(ROOT).as_posix(),
            "fullReportSha256": full_digest,
        },
        "rows": output_rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", required=True)
    parser.add_argument("--edition-id", required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    ledger = build(args.language, args.edition_id)
    path = HERE / "jesus_word_spans" / f"{args.language}_{args.edition_id}.json"
    data = (json.dumps(ledger, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if args.check:
        if path.read_bytes() != data:
            raise ValueError("Compiled ledger differs from pinned evidence")
    else:
        path.write_bytes(data)
    reviewed = ReviewedJesusSpans.load(ROOT, args.language, args.edition_id)
    target_root = ROOT / "shared/assets/books/editions" / args.language / args.edition_id / "new_testament"
    for book_path in target_root.glob("*.json"):
        book = json.loads(book_path.read_text(encoding="utf-8"))
        for chapter in book["chapters"]:
            for verse in chapter["verses"]:
                key = ("new_testament", book_path.stem, chapter["number"], verse["verse"])
                if key in reviewed.rows:
                    raw = verse["text"].replace("[J]", "").replace("[/J]", "")
                    verse["text"] = reviewed.apply(*key, raw, verse.get("verseEnd", verse["verse"]))
        reviewed.verify_relocations("new_testament", book_path.stem, book["chapters"])
    reviewed.validate_coverage(
        {key for key, row in reviewed.rows.items() if row.get("supplementalFor") is None and not row.get("overrideInherited")},
        {key for key, row in reviewed.rows.items() if row.get("overrideInherited")},
    )
    print(json.dumps({"reviewed": len(reviewed.used), "ledgerSha256": hashlib.sha256(data).hexdigest().upper()}))


if __name__ == "__main__":
    main()
