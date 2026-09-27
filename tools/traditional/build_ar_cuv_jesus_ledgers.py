#!/usr/bin/env python3
"""Build exact Arabic Van Dyck and CUV mixed-verse review ledgers.

The KJV overlay supplies the Jesus-word attribution. Target-edition quotation
marks locate only boundaries. A row without a unique attributable boundary is
reported for manual review; it is never silently promoted into a ledger.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

from import_traditional_editions import BOOKS, base_jesus_ranges, overlaps
from jesus_word_spans import ReviewedJesusSpans, sha256_text


ROOT = Path(__file__).resolve().parents[2]
SOURCE_TAGS = re.compile(r"\[/?(?:DN|ADD|J)\]")
J_SPAN = re.compile(r"\[J\].*?\[/J\]", re.DOTALL)
TARGETS = {("ar", "van_dyck"), ("zh-Hans", "cuv"), ("zh-Hant", "cuv")}
# These dialogue turns were checked against the pinned KJV-wj speaker order.
# The first target quotation is Jesus' speech except John 18:5, where the
# search party speaks first. Additional CUV keys mark narrated translations
# of Jesus' utterance, not a second utterance (Matt 27:46; Mark 5:41, 7:34).
MANUAL_FIRST = frozenset("""
matthew:9:28 matthew:13:51 matthew:15:34 matthew:20:21
matthew:20:22 matthew:22:42 matthew:27:46
mark:5:9 mark:5:41 mark:6:37 mark:6:38 mark:7:34
mark:8:5 mark:8:29 mark:9:21 mark:10:51 mark:12:16
luke:7:40 luke:8:25 luke:8:30 luke:9:13 luke:9:20
luke:9:59 luke:18:41 luke:22:35 luke:24:19
john:1:38 john:9:7 john:11:34 john:11:39 john:18:7
john:20:15 john:20:16 john:21:5 john:21:12 acts:9:10
""".split())
MANUAL_MULTIPLE = {
    "matthew:9:6": (0, 1),
    "matthew:21:31": (0, 2),
    "luke:5:24": (0, 1),
    "luke:8:8": (0, 1),
    "john:21:15": (0, 2),
    "john:21:16": (0, 2),
    "john:21:17": (0, 2),  # Arabic has three dialogue runs.
}
ARABIC_UNQUOTED_SPEECH = frozenset("""
acts:11:16 acts:22:7 acts:22:8 acts:22:10 acts:22:18
acts:26:14 acts:26:15
""".split())


def plain(text: str) -> str:
    return SOURCE_TAGS.sub("", text)


def quote_runs(text: str, language: str) -> list[str]:
    """Return outer dialogue runs, including cross-verse open/close runs."""
    pairs = {"«": "»"} if language == "ar" else {"「": "」", "『": "』"}
    runs: list[str] = []
    start: int | None = None
    stack: list[str] = []
    for index, character in enumerate(text):
        if character in pairs:
            if not stack:
                start = index
            stack.append(pairs[character])
        elif character in pairs.values():
            if not stack:
                runs.append(text[:index + 1])
            elif stack[-1] == character:
                stack.pop()
                if not stack:
                    assert start is not None
                    runs.append(text[start:index + 1])
                    start = None
    if stack:
        assert start is not None
        runs.append(text[start:])
    return runs


def kjv_shape(text: str) -> str:
    spans = list(J_SPAN.finditer(text))
    if len(spans) > 1:
        return "multiple"
    if not spans:
        return "none"
    if not plain(text[:spans[0].start()]).strip():
        return "prefix"
    if not plain(text[spans[0].end():]).strip():
        return "suffix"
    return "middle"


def candidate_rows(root: Path, language: str, edition_id: str) -> list[dict]:
    if (language, edition_id) not in TARGETS:
        raise ValueError(f"Unowned target edition: {language}/{edition_id}")
    rows: list[dict] = []
    for _, collection, book_id in BOOKS:
        if collection != "new_testament":
            continue
        kjv = json.loads((root / "shared/assets/books/editions/en/kjv1769" /
                          collection / f"{book_id}.json").read_text(encoding="utf-8"))
        target = json.loads((root / "shared/assets/books/editions" / language /
                            edition_id / collection / f"{book_id}.json").read_text(encoding="utf-8"))
        _, mixed = base_jesus_ranges(root, collection, book_id)
        kjv_units = {
            (chapter["number"], verse["verse"]): verse
            for chapter in kjv["chapters"] for verse in chapter["verses"]
        }
        for chapter in target["chapters"]:
            number = chapter["number"]
            for verse in chapter["verses"]:
                first = verse["verse"]
                last = verse.get("verseEnd", first)
                if not overlaps(mixed.get(number, []), first, last):
                    continue
                if verse.get("sourcePlaceholder"):
                    raise ValueError(f"Placeholder mixed unit: {book_id} {number}:{first}-{last}")
                matching = [kjv_units[(number, value)] for value in range(first, last + 1)
                            if any(start <= value <= end for start, end in mixed[number])]
                if len(matching) != 1:
                    raise ValueError(f"Ambiguous KJV mixed units: {book_id} {number}:{first}-{last}")
                raw = verse["text"].replace("[J]", "").replace("[/J]", "")
                kjv_text = matching[0]["text"]
                runs = quote_runs(plain(raw), language)
                shape = kjv_shape(kjv_text)
                ref = f"{book_id}:{number}:{first}"
                reason = None
                no_target_speech = False
                if language != "ar" and ref == "luke:20:23":
                    selected = []
                    no_target_speech = True
                    method = "CUV omits KJV-wj 'Why tempt ye me?'; only a speech introducer remains"
                elif ref in {"acts:20:35", "acts:22:21"}:
                    if language == "ar":
                        selected = [plain(raw).rsplit(":", 1)[1].lstrip()]
                    else:
                        selected = [next(run for run in runs if run.startswith("『") and run.endswith("』"))]
                    method = "reviewed nested quotation of Jesus inside another speaker's ongoing turn"
                elif language != "ar" and ref == "revelation:1:8":
                    selected = [plain(raw).split("，", 2)[0] + "，" + plain(raw).split("，", 2)[1] + "，"]
                    selected = [selected[0][selected[0].index("「"):]]
                    method = "KJV-wj ends after Alpha/Omega; CUV recasts the remaining title inside the quotation"
                elif len(runs) == 1 and shape != "multiple":
                    selected = runs
                    method = "unique target-edition dialogue run"
                elif ref in MANUAL_FIRST and len(runs) >= 2 and shape == "middle":
                    selected = [runs[0]]
                    method = "reviewed Jesus turn before a second speaker or narrated gloss"
                elif ref == "john:18:5" and len(runs) == 2 and shape == "middle":
                    selected = [runs[1]]
                    method = "reviewed Jesus reply after the search party's answer"
                elif ref in MANUAL_MULTIPLE and shape == "multiple":
                    indexes = MANUAL_MULTIPLE[ref]
                    if ref == "john:21:17" and language != "ar":
                        indexes = (0, 3)
                    selected = [runs[index] for index in indexes]
                    method = "reviewed separated Jesus turns around narrator or another speaker"
                elif ref == "luke:8:45" and shape == "multiple" and len(runs) == 2:
                    selected = [runs[0]]
                    if language == "ar":
                        repeated = "مَنِ ٱلَّذِي لَمَسَنِي؟"
                        if plain(raw).count(repeated) != 2:
                            raise ValueError("Arabic Luke 8:45 repeated Jesus quotation changed")
                        selected.append(repeated)
                    method = "KJV-wj marks Jesus' question and, in Arabic, Peter's verbatim quotation of it"
                elif ref == "acts:1:4" and shape == "multiple" and len(runs) == 1:
                    selected = runs
                    method = "KJV splits Jesus speech around 'saith he'; target quotes the same speech continuously"
                elif ref == "revelation:1:11" and shape == "multiple" and len(runs) == 1:
                    selected = runs
                    method = ("KJV-wj second command is quoted; CUV omits its first clause"
                              if language != "ar" else
                              "Arabic quote carries both KJV-wj clauses without an intervening narrator")
                elif language == "ar" and ref in ARABIC_UNQUOTED_SPEECH and shape == "suffix" and not runs:
                    selected = [plain(raw).rsplit(":", 1)[1].lstrip()]
                    method = "reviewed speech after the final Arabic narrator colon"
                elif language != "ar" and ref == "luke:6:20" and shape == "suffix" and not runs:
                    selected = [plain(raw).split("：", 1)[1].lstrip()]
                    method = "reviewed unquoted CUV speech after Jesus' introducer"
                elif len(runs) > 1 and shape in {"prefix", "suffix"}:
                    selected = [runs[0] if shape == "prefix" else runs[-1]]
                    method = f"{shape} KJV-wj speech aligned to ordered target-edition dialogue"
                else:
                    selected = []
                    reason = f"{shape} KJV-wj speech; {len(runs)} target dialogue runs"
                    method = ""
                key = (collection, book_id, number, first)
                rows.append({
                    "key": key,
                    "verseEnd": last,
                    "raw": raw,
                    "kjv": kjv_text,
                    "kjvShape": shape,
                    "runs": runs,
                    "spans": selected,
                    "method": method,
                    "unresolved": reason,
                    "noTargetSpeech": no_target_speech,
                })
    return rows


def build_ledger(root: Path, language: str, edition_id: str) -> dict:
    candidates = candidate_rows(root, language, edition_id)
    if len(candidates) != 626:
        raise ValueError(f"KJV-wj mixed candidate inventory changed: {len(candidates)}")
    target_manifest = json.loads((root / "shared/assets/books/editions" / language /
                                  edition_id / "_manifest.json").read_text(encoding="utf-8"))
    kjv_manifest = json.loads((root / "shared/assets/books/editions/en/kjv1769/_manifest.json")
                              .read_text(encoding="utf-8"))
    output: list[dict] = []
    for candidate in candidates:
        if candidate["unresolved"]:
            raise ValueError(f"Unresolved speech boundary {candidate['key']}: {candidate['unresolved']}")
        collection, book_id, chapter, verse = candidate["key"]
        row: dict = {
            "collection": collection,
            "bookId": book_id,
            "chapter": chapter,
            "verse": verse,
            "sourceTextSha256": sha256_text(candidate["raw"]),
            "kjvWjVerseSha256": sha256_text(candidate["kjv"]),
        }
        if candidate["verseEnd"] != verse:
            row["verseEnd"] = candidate["verseEnd"]
        if candidate["noTargetSpeech"]:
            row["noTargetSpeech"] = True
            row["reason"] = candidate["method"]
            row["spans"] = []
        else:
            spans = []
            target_plain = plain(candidate["raw"])
            for speech in candidate["spans"]:
                if not speech or speech not in target_plain:
                    raise ValueError(f"Speech text absent in {candidate['key']}: {speech!r}")
                span = {"exactText": speech}
                if target_plain.count(speech) > 1:
                    if language == "ar" and (book_id, chapter, verse) == ("luke", 8, 45) and speech == "مَنِ ٱلَّذِي لَمَسَنِي؟":
                        span["occurrence"] = 2
                    else:
                        raise ValueError(f"Repeated target speech needs review: {candidate['key']}: {speech!r}")
                spans.append(span)
            row["spans"] = spans
        row["review"] = {
            "status": "reviewed",
            "evidence": (
                f"Pinned KJV1769 wj at {book_id} {chapter}:{verse}; "
                f"{language}/{edition_id} exact text and dialogue grammar: {candidate['method']}."
            ),
        }
        output.append(row)
    ledger = {
        "schemaVersion": 1,
        "language": language,
        "editionId": edition_id,
        "semanticAuthority": {
            "edition": "en/kjv1769",
            "sourceArchiveSha256": kjv_manifest["source"]["archiveSha256"],
        },
        "targetSource": {
            "url": target_manifest["source"]["url"],
            "archiveSha256": target_manifest["source"]["archiveSha256"],
        },
        "rows": output,
    }
    # Apply the entire ledger to current raw edition text in memory. This
    # checks every row's hash, exact substring, occurrence, and marker safety.
    reviewed = ReviewedJesusSpans(
        language, edition_id, Path("<in-memory ledger>"),
        {(r["collection"], r["bookId"], r["chapter"], r["verse"]): r for r in output}, set(), set(),
    )
    for candidate in candidates:
        collection, book_id, chapter, verse = candidate["key"]
        reviewed.apply(collection, book_id, chapter, verse,
                       candidate["raw"], candidate["verseEnd"])
    reviewed.validate_coverage({candidate["key"] for candidate in candidates})
    return ledger


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("language", choices=("ar", "zh-Hans", "zh-Hant"))
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--apply", action="store_true", help="write the validated reviewed ledger")
    args = parser.parse_args()
    edition_id = "van_dyck" if args.language == "ar" else "cuv"
    ledger = build_ledger(args.root, args.language, edition_id)
    rows = ledger["rows"]
    if args.apply:
        path = args.root / "tools/traditional/jesus_word_spans" / f"{args.language}_{edition_id}.json"
        if path.exists():
            raise ValueError(f"Refusing to overwrite existing reviewed ledger: {path}")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(ledger, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "language": args.language,
        "candidates": len(rows),
        "reviewedSpeechRows": sum(not row.get("noTargetSpeech", False) for row in rows),
        "reviewedOmissions": sum(bool(row.get("noTargetSpeech")) for row in rows),
        "written": args.apply,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
