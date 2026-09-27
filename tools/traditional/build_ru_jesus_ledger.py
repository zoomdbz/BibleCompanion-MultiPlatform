#!/usr/bin/env python3
"""Compile pinned RST+ red-letter evidence into exact Russian Synodal J spans."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re

from import_traditional_editions import BOOKS, base_jesus_ranges
from jesus_word_spans import ReviewedJesusSpans, sha256_text
from fetch_rst_plus_jesus_evidence import J_SPAN, ROOT, WORD, OUTPUT as EVIDENCE


OVERRIDES = ROOT / "tools/traditional/jesus_word_spans/ru_synodal1876_overrides.json"
OUTPUT = ROOT / "tools/traditional/jesus_word_spans/ru_synodal1876.json"
EVIDENCE_SHA256 = "1CF2579193D7194446BB477F930B3BC817C8C82BEEE34B8C61E2A9E829C92B3B"
SOURCE_TAG = re.compile(r"\[/?(?:DN|ADD|J)\]")
SEGMENT = re.compile(r"(</?J>)")
NT_BOOKS = [book for _, collection, book in BOOKS if collection == "new_testament"]
BOOK_ORDER = {book: index for index, book in enumerate(NT_BOOKS)}
SPECIAL_SOURCE_CONFLICTS = {"john 1:42"}


def ref(row: dict) -> str:
    return f"{row['bookId']} {row['chapter']}:{row['verse']}"


def source_ranges(markup: str) -> tuple[str, list[tuple[int, int]]]:
    plain_parts = []
    ranges = []
    position = 0
    opened = None
    for part in SEGMENT.split(markup):
        if part == "<J>":
            if opened is not None:
                raise ValueError("Nested RST+ J markup")
            opened = position
        elif part == "</J>":
            if opened is None:
                raise ValueError("Unmatched RST+ J close")
            ranges.append((opened, position))
            opened = None
        else:
            plain_parts.append(part)
            position += len(part)
    if opened is not None:
        raise ValueError("Unclosed RST+ J markup")
    return "".join(plain_parts), ranges


def token_projection(row: dict) -> list[dict]:
    """Project source J only when every source and target word matches in order.

    This is not semantic inference: source and target must have identical word
    inventories, and the KJV WJ segment count must agree with source J. Any
    lexical difference or editorial disagreement requires an explicit review.
    """
    source_plain, source_red = source_ranges(row["rstMarkup"])
    target_plain = SOURCE_TAG.sub("", row["targetText"])
    source_words = list(WORD.finditer(source_plain))
    target_words = list(WORD.finditer(target_plain))
    if [word.group().casefold() for word in source_words] != [
        word.group().casefold() for word in target_words
    ]:
        raise ValueError(f"RST+ target word inventory differs: {ref(row)}")
    expected_segments = 2 if row["kjvSpeechShape"] == "multiple" else 1
    if len(source_red) != expected_segments:
        raise ValueError(f"RST+ red/KJV WJ segment disagreement: {ref(row)}")
    spans = []
    for start, end in source_red:
        covered = [
            index for index, word in enumerate(source_words)
            if word.start() < end and word.end() > start
        ]
        if not covered:
            raise ValueError(f"RST+ empty red word span: {ref(row)}")
        first, last = covered[0], covered[-1]
        left = target_words[first].start()
        right = target_words[last].end()
        source_trailing = source_plain[source_words[last].end():end]
        if re.search(r"[^\s]", source_trailing):
            next_word = target_words[last + 1].start() if last + 1 < len(target_words) else len(target_plain)
            target_trailing = target_plain[right:next_word]
            # Keep punctuation attached to the spoken final word. A spaced
            # dash can introduce narration (Matt 9:6; Luke 5:24).
            right += len(re.match(r"[^\w\s]*", target_trailing).group())
        source_leading = source_plain[start:source_words[first].start()]
        if re.search(r"[«“\"]", source_leading):
            previous = target_words[first - 1].end() if first else 0
            target_leading = target_plain[previous:left]
            quote = max(target_leading.rfind("«"), target_leading.rfind("“"), target_leading.rfind('"'))
            if quote >= 0:
                left = previous + quote
        spans.append((left, right))
    # External markup can split one continuous saying at a formatting mark.
    merged = []
    for start, end in spans:
        if merged and not WORD.search(target_plain[merged[-1][1]:start]):
            merged[-1] = (merged[-1][0], end)
        else:
            merged.append((start, end))
    output = []
    for start, end in merged:
        exact = target_plain[start:end]
        if not exact:
            raise ValueError(f"Projected empty target span: {ref(row)}")
        count = target_plain.count(exact)
        span = {"exactText": exact}
        if count > 1:
            span["occurrence"] = target_plain[:start].count(exact) + 1
        output.append(span)
    return output


def load_evidence() -> dict:
    data = EVIDENCE.read_bytes()
    if hashlib.sha256(data).hexdigest().upper() != EVIDENCE_SHA256:
        raise ValueError("Pinned RST+ evidence JSON changed")
    evidence = json.loads(data)
    if (evidence["language"], evidence["editionId"], evidence["candidateCount"],
            evidence["fullAuthorityCount"]) != ("ru", "synodal1876", 626, 1402):
        raise ValueError("RST+ evidence identity or authority inventory changed")
    return evidence


def load_overrides() -> dict:
    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    if (overrides["schemaVersion"], overrides["language"], overrides["editionId"]) != (
        1, "ru", "synodal1876"
    ):
        raise ValueError("Russian override identity changed")
    return overrides


def build_ledger(root: Path = ROOT) -> dict:
    if root != ROOT:
        raise ValueError("Russian evidence pins the current repository root")
    evidence = load_evidence()
    overrides = load_overrides()
    manual = {row["reference"]: row for row in overrides["rows"]}
    if len(manual) != len(overrides["rows"]):
        raise ValueError("Duplicate Russian mixed override")
    full_manual = {row["reference"]: row for row in overrides["fullOverrides"]}
    if len(full_manual) != len(overrides["fullOverrides"]):
        raise ValueError("Duplicate Russian full-verse override")
    required_manual = {
        ref(row) for row in evidence["rows"]
        if row["textStatus"] == "different-words"
        or len(row["rstRedSpans"]) != (2 if row["kjvSpeechShape"] == "multiple" else 1)
    } | SPECIAL_SOURCE_CONFLICTS
    if set(manual) != required_manual:
        raise ValueError(f"Russian manual review set changed: missing={sorted(required_manual - set(manual))}; extra={sorted(set(manual) - required_manual)}")
    required_full = {ref(row) for row in evidence["fullExceptions"]}
    if set(full_manual) != required_full:
        raise ValueError(f"Russian full override set changed: missing={sorted(required_full - set(full_manual))}; extra={sorted(set(full_manual) - required_full)}")
    output = []
    for row in evidence["rows"]:
        reference = ref(row)
        spec = manual.get(reference)
        spans = (
            [part if isinstance(part, dict) else {"exactText": part} for part in spec.get("spans", [])]
            if spec else token_projection(row)
        )
        ledger_row = {
            "collection": "new_testament",
            "bookId": row["bookId"], "chapter": row["chapter"], "verse": row["verse"],
            "sourceTextSha256": row["sourceTextSha256"],
            "kjvWjVerseSha256": row["kjvTextSha256"],
            **({"speechRelocatedTo": spec["speechRelocatedTo"]} if spec and spec.get("speechRelocatedTo") else {}),
            **({"noTargetSpeech": True, "reason": spec["reason"]} if spec and spec.get("noTargetSpeech") else {}),
            "spans": spans,
            "review": {
                "status": "reviewed",
                "evidence": {
                    "source": "ph4 RST+ MyBible red-letter module",
                    "archiveSha256": evidence["source"]["archiveSha256"],
                    "sourceVerseSha256": row["rstRawSha256"],
                    "textStatus": row["textStatus"],
                    "method": "manual-KJV-WJ-and-RST+-review" if spec else "exact-word-aligned-RST+-red-spans-and-KJV-WJ",
                    "reason": spec["reason"] if spec else "Pinned source red span and KJV WJ speaker/segment count agree; exact source and target words align.",
                },
            },
        }
        output.append(ledger_row)
    full_index = {ref(row): row for row in evidence["fullExceptions"]}
    for reference, spec in full_manual.items():
        source = full_index[reference]
        raw = source["targetText"]
        if sha256_text(raw) != spec["targetTextSha256"]:
            raise ValueError(f"Russian full override target drift: {reference}")
        output.append({
            "collection": "new_testament",
            "bookId": source["bookId"], "chapter": source["chapter"], "verse": source["verse"],
            "sourceTextSha256": spec["targetTextSha256"],
            "kjvWjVerseSha256": source["kjvTextSha256"],
            "overrideInherited": True,
            "spans": [part if isinstance(part, dict) else {"exactText": part} for part in spec["spans"]],
            "review": {
                "status": "reviewed",
                "evidence": {
                    "source": "ph4 RST+ MyBible red-letter module",
                    "archiveSha256": evidence["source"]["archiveSha256"],
                    "sourceVerseSha256": source["rstRawSha256"],
                    "method": "manual-KJV-full-source-disagreement",
                    "reason": spec["reason"],
                },
            },
        })
    output.sort(key=lambda row: (BOOK_ORDER[row["bookId"]], row["chapter"], row["verse"]))
    kjv_manifest = json.loads(
        (root / "shared/assets/books/editions/en/kjv1769/_manifest.json")
        .read_text(encoding="utf-8")
    )
    target_manifest = json.loads(
        (root / "shared/assets/books/editions/ru/synodal1876/_manifest.json")
        .read_text(encoding="utf-8")
    )
    ledger = {
        "schemaVersion": 1,
        "language": "ru",
        "editionId": "synodal1876",
        "semanticAuthority": {
            "edition": "en/kjv1769",
            "archiveSha256": kjv_manifest["source"]["archiveSha256"],
        },
        "targetSource": {
            "edition": "ru/synodal1876",
            "url": target_manifest["source"]["url"],
            "archiveSha256": target_manifest["source"]["archiveSha256"],
        },
        "externalEvidence": {
            "source": "ph4 RST+ MyBible",
            "archiveSha256": evidence["source"]["archiveSha256"],
            "databaseSha256": evidence["source"]["databaseSha256"],
            "report": "tools/traditional/jesus_word_spans/ru_synodal1876_rstplus_evidence.json",
            "reportSha256": EVIDENCE_SHA256,
        },
        "rows": output,
    }
    if len(output) != 632:
        raise ValueError(f"Expected 626 mixed and six full rows, found {len(output)}")
    checker = ReviewedJesusSpans(
        "ru", "synodal1876", Path("<in-memory Russian ledger>"),
        {(row["collection"], row["bookId"], row["chapter"], row["verse"]): row for row in output},
        set(), set(),
    )
    expected = set()
    for candidate in evidence["rows"]:
        key = ("new_testament", candidate["bookId"], candidate["chapter"], candidate["verse"])
        expected.add(key)
        result = checker.apply(*key, candidate["targetText"], candidate["verse"])
        if result.replace("[J]", "").replace("[/J]", "") != candidate["targetText"]:
            raise ValueError(f"Russian mixed review changed Scripture: {ref(candidate)}")
    expected_full = set()
    for correction in evidence["fullExceptions"]:
        key = ("new_testament", correction["bookId"], correction["chapter"], correction["verse"])
        expected_full.add(key)
        result = checker.apply(*key, correction["targetText"], correction["verse"])
        if result.replace("[J]", "").replace("[/J]", "") != correction["targetText"]:
            raise ValueError(f"Russian full override changed Scripture: {ref(correction)}")
    mark = json.loads(
        (root / "shared/assets/books/editions/ru/synodal1876/new_testament/mark.json")
        .read_text(encoding="utf-8")
    )
    mark_11_23 = next(
        verse for chapter in mark["chapters"] if chapter["number"] == 11
        for verse in chapter["verses"] if verse["verse"] == 23
    )
    checker.verify_relocations(
        "new_testament", "mark",
        [{"number": 11, "verses": [{"verse": 23, "text": mark_11_23["text"]}]}],
    )
    checker.validate_coverage(expected, expected_full)
    return ledger


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="write ledger only if absent")
    parser.add_argument("--unresolved", action="store_true", help="print manual refs required by evidence")
    args = parser.parse_args()
    evidence = load_evidence()
    required = [
        ref(row) for row in evidence["rows"]
        if row["textStatus"] == "different-words"
        or len(row["rstRedSpans"]) != (2 if row["kjvSpeechShape"] == "multiple" else 1)
    ]
    required = sorted(set(required) | SPECIAL_SOURCE_CONFLICTS)
    if args.unresolved:
        print(json.dumps({"manualMixed": required, "manualFull": [ref(row) for row in evidence["fullExceptions"]]}, ensure_ascii=False, indent=2))
        return
    ledger = build_ledger()
    if args.apply:
        if OUTPUT.exists():
            raise ValueError(f"Refusing to overwrite existing Russian ledger: {OUTPUT}")
        OUTPUT.write_text(json.dumps(ledger, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "mixedAuthorities": 626,
        "fullOverrides": 6,
        "manualMixed": len(required),
        "rows": len(ledger["rows"]),
        "written": args.apply,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
