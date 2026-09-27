#!/usr/bin/env python3
"""Pin two Portuguese Almeida red-letter witnesses for 1911 boundary review."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sqlite3
import zipfile
from collections import Counter
from pathlib import Path

import requests

from propose_jesus_word_spans import build_queue, speech_shape


ARCHIVES = {
    "AA": "97821E78214BA9E989DD2362A156E843D3E18FB5FE121A548486DE030851F90B",
    "ARC": "1DE56663CCF0A9C4F40AB163DF963F3C12EECEF7AF93ACB4CBD0301DBCC87FD0",
}
BOOK_NUMBER = {
    "matthew": 470, "mark": 480, "luke": 490, "john": 500,
    "acts": 510, "1_corinthians": 530, "2_corinthians": 540, "revelation": 730,
}
TAG = re.compile(r"<(?!/?J>)[^>]+>")
J_SPAN = re.compile(r"<J>(.*?)</J>", re.DOTALL)


def load_module(source: str) -> dict[tuple[int, int, int], str]:
    url = f"https://www.ph4.org/_dl.php?back=bbl&a={source}&b=mybible&c"
    content = requests.get(url, timeout=45).content
    digest = hashlib.sha256(content).hexdigest().upper()
    if digest != ARCHIVES[source]:
        raise ValueError(f"{source} archive drift or unavailable: {digest}")
    archive = zipfile.ZipFile(io.BytesIO(content))
    if archive.namelist() != [f"{source}.SQLite3"]:
        raise ValueError(f"Unexpected {source} archive layout")
    connection = sqlite3.connect(":memory:")
    connection.deserialize(archive.read(archive.namelist()[0]))
    rows = connection.execute("SELECT book_number, chapter, verse, text FROM verses").fetchall()
    connection.close()
    return {(int(book), int(chapter), int(verse)): text for book, chapter, verse, text in rows}


def witness(raw: str | None) -> dict:
    if raw is None:
        return {"status": "missing"}
    cleaned = TAG.sub("", raw)
    spans = [match.group(1) for match in J_SPAN.finditer(cleaned)]
    if cleaned.count("<J>") != len(spans) or cleaned.count("</J>") != len(spans):
        return {"status": "malformed", "taggedText": cleaned}
    plain = cleaned.replace("<J>", "").replace("</J>", "")
    return {"status": "parsed", "plainText": plain, "spans": spans,
            "speechShape": speech_shape(cleaned.replace("<J>", "[J]").replace("</J>", "[/J]"))}


def build_report(repo_root: Path) -> dict:
    queue = build_queue(repo_root, "pt", "almeida1911")
    modules = {source: load_module(source) for source in ARCHIVES}
    output = []
    for candidate in queue["rows"]:
        key = (BOOK_NUMBER[candidate["bookId"]], candidate["chapter"], candidate["verse"])
        witnesses = {source: witness(module.get(key)) for source, module in modules.items()}
        shapes = [item.get("speechShape") for item in witnesses.values()]
        concordant = len(set(shapes)) == 1 and shapes[0] == candidate["kjvSpeechShape"]
        output.append({
            "collection": candidate["collection"], "bookId": candidate["bookId"],
            "chapter": candidate["chapter"], "verse": candidate["verse"],
            "sourceTextSha256": candidate["sourceTextSha256"],
            "kjvTextSha256": candidate["kjvTextSha256"],
            "kjvSpeechShape": candidate["kjvSpeechShape"],
            "targetText": candidate["targetText"],
            "proposal": candidate["proposedSpans"],
            "proposalConfidence": candidate["confidence"],
            "witnesses": witnesses,
            "status": "both_shape_concordant" if concordant else "needs_review",
        })
    return {
        "schemaVersion": 1, "language": "pt", "editionId": "almeida1911",
        "semanticAuthority": "pinned en/kjv1769 WJ", "source": "ph4 MyBible AA and ARC",
        "archiveSha256": ARCHIVES, "candidateCount": len(output),
        "statusCounts": dict(Counter(row["status"] for row in output)),
        "rows": output,
    }


def build_full_report(repo_root: Path) -> dict:
    """Compare every KJV full-J authority with both red-letter witnesses."""
    modules = {source: load_module(source) for source in ARCHIVES}
    kjv_root = repo_root / "shared/assets/books/editions/en/kjv1769/new_testament"
    target_root = repo_root / "shared/assets/books/editions/pt/almeida1911/new_testament"
    rows = []
    for book_id, number in BOOK_NUMBER.items():
        kjv = json.loads((kjv_root / f"{book_id}.json").read_text(encoding="utf-8"))
        target = json.loads((target_root / f"{book_id}.json").read_text(encoding="utf-8"))
        targets = {(c["number"], v["verse"]): v for c in target["chapters"] for v in c["verses"]}
        for chapter in kjv["chapters"]:
            for verse in chapter["verses"]:
                tagged = verse["text"]
                if "[J]" not in tagged:
                    continue
                outside = re.sub(r"\[J\].*?\[/J\]", "", tagged)
                if re.sub(r"\[/?(?:DN|ADD)\]", "", outside).strip():
                    continue
                reference = (chapter["number"], verse["verse"])
                target_verse = targets.get(reference)
                target_text = None if target_verse is None else target_verse["text"].replace("[J]", "").replace("[/J]", "")
                witnesses = {source: witness(module.get((number, *reference))) for source, module in modules.items()}
                rows.append({
                    "bookId": book_id, "chapter": reference[0], "verse": reference[1],
                    "targetVerseEnd": None if target_verse is None else target_verse.get("verseEnd", reference[1]),
                    "targetText": target_text,
                    "targetTextSha256": None if target_text is None else hashlib.sha256(target_text.encode()).hexdigest().upper(),
                    "witnesses": witnesses,
                    "status": "both_full" if all(item.get("speechShape") == "full" for item in witnesses.values())
                    else "review",
                })
    return {"schemaVersion": 1, "language": "pt", "editionId": "almeida1911",
            "semanticAuthority": "pinned en/kjv1769 WJ full verses",
            "archiveSha256": ARCHIVES, "authorityCount": len(rows),
            "statusCounts": dict(Counter(row["status"] for row in rows)), "rows": rows}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--full-output", type=Path)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    report = build_report(args.repo_root)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "rows"}, indent=2))
    if args.full_output is not None:
        full_report = build_full_report(args.repo_root)
        args.full_output.write_text(json.dumps(full_report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({key: value for key, value in full_report.items() if key != "rows"}, indent=2))


if __name__ == "__main__":
    main()
