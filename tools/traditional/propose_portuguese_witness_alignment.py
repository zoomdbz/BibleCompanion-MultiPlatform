#!/usr/bin/env python3
"""Read-only exact boundary alignment of two Almeida witnesses to 1911 text."""

from __future__ import annotations

import argparse
import difflib
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path


MARKER = re.compile(r"\[/?(?:DN|ADD|J)\]")


def normalize(text: str) -> str:
    characters = []
    for char in text:
        base = "".join(c for c in unicodedata.normalize("NFD", char) if not unicodedata.combining(c))
        lower = base.casefold().replace("’", "'").replace("‘", "'")
        characters.append(lower if len(lower) == 1 else char.lower())
    return "".join(characters)


def align(witness: dict, target: str) -> dict:
    if witness.get("status") != "parsed" or not witness.get("spans"):
        return {"status": "unavailable"}
    source = witness["plainText"]
    cursor = 0
    ranges = []
    for span in witness["spans"]:
        start = source.find(span, cursor)
        if start < 0:
            return {"status": "ambiguous_source_span"}
        ranges.append((start, start + len(span)))
        cursor = start + len(span)
    matcher = difflib.SequenceMatcher(None, normalize(source), normalize(target), autojunk=False)
    ratio = matcher.ratio()
    mapping = {}
    for block in matcher.get_matching_blocks():
        for offset in range(block.size + 1):
            mapping[block.a + offset] = block.b + offset
    boundaries = {point for pair in ranges for point in pair}
    if ratio < 0.70 or not boundaries <= mapping.keys():
        return {"status": "boundary_unmapped", "ratio": round(ratio, 5)}
    target_ranges = [(mapping[start], mapping[end]) for start, end in ranges]
    if any(start >= end for start, end in target_ranges):
        return {"status": "empty_mapped_span", "ratio": round(ratio, 5)}
    if any(
        (start > 0 and start < len(target) and target[start - 1].isalpha() and target[start].isalpha()) or
        (end > 0 and end < len(target) and target[end - 1].isalpha() and target[end].isalpha())
        for start, end in target_ranges
    ):
        return {"status": "boundary_inside_target_word", "ratio": round(ratio, 5)}
    return {"status": "mapped", "ratio": round(ratio, 5),
            "spans": [target[start:end] for start, end in target_ranges],
            "ranges": target_ranges}


def build_report(evidence_path: Path) -> dict:
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    rows = []
    for source in evidence["rows"]:
        target = MARKER.sub("", source["targetText"])
        aligned = {name: align(source["witnesses"][name], target) for name in ("AA", "ARC")}
        both_mapped = all(item["status"] == "mapped" for item in aligned.values())
        same_ranges = both_mapped and aligned["AA"]["ranges"] == aligned["ARC"]["ranges"]
        status = "dual_exact_boundary" if same_ranges and source["status"] == "both_shape_concordant" else "review"
        rows.append({"collection": source["collection"], "bookId": source["bookId"],
                     "chapter": source["chapter"], "verse": source["verse"],
                     "sourceTextSha256": source["sourceTextSha256"],
                     "kjvTextSha256": source["kjvTextSha256"],
                     "kjvSpeechShape": source["kjvSpeechShape"],
                     "targetText": source["targetText"],
                     "witnessShapeStatus": source["status"],
                     "aligned": aligned, "status": status,
                     "spans": aligned["ARC"]["spans"] if status == "dual_exact_boundary" else []})
    return {"schemaVersion": 1, "language": "pt", "editionId": "almeida1911",
            "sourceReport": evidence_path.as_posix(), "candidateCount": len(rows),
            "statusCounts": dict(Counter(row["status"] for row in rows)), "rows": rows}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = build_report(args.evidence)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "rows"}, indent=2))


if __name__ == "__main__":
    main()
