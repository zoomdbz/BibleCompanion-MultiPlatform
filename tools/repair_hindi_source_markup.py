#!/usr/bin/env python3
"""Restore source-backed IRVHin red-letter spans without changing wording.

Complete plain-text matches qualify directly. Reviewed presentation differences
qualify only through their pinned exact apparatus offsets and whitespace proof.
Psalm 119's source acrostic labels move out of the preceding verse into their
own stanza headings. Other text differences remain audit findings.
Run without --apply for a read-only report. Source text is CC BY-SA 4.0; see
shared/assets/scripture_sources/irvhin.json for attribution and source details.
"""

from __future__ import annotations

import argparse
from collections import Counter
import io
import json
from pathlib import Path
import re
import unicodedata
import zipfile

from audit_ebible_source import download_archive, normalize, parse_app, parse_usfm
from audit_scripture_sources import APP_TAG, BOOKS, TRAILING_REFERENCE, j_mask


SOURCE_URL = "https://ebible.org/Scriptures/hin2017_usfm.zip"
SOURCE_SHA256 = "4B284BEE6D52D8DAE3425D540E6743836E614694EEFF9C55D2395071A76BF07F"
J_TAG = re.compile(r"\[/?J\]")
DISPLAY_TAG = re.compile(r"\[/?[A-Z][A-Z0-9_-]*\]")
REVIEW_RELATIVE_PATH = Path("tools/reports/hindi_irv/source_difference_review.json")
INITIAL_REVIEWED_PRESENTATION_SPEECH_MISMATCHES = 229
REVIEWED_LEXICAL_REPAIRS = {
    ("REV", 12, 18, 18): {
        "old": "और अजगर समुद्र के किनारे की रेत पर खड़ा हो गया।",
        "official": "और वह समुद्र के रेत पर जा खड़ा हुआ।",
        "source": "https://ebible.org/Scriptures/hin2017_usfm.zip",
        "sourceHtml": "https://ebible.org/hin2017/REV12.htm",
        "linkedEdition": "https://www.bible.com/bible/1980/REV.12.IRVHin",
    }
}


def psalm119_headings(raw: bytes) -> dict[int, tuple[str, str]]:
    chapter = None
    title = label = ""
    result = {}
    for line in raw.decode("utf-8-sig").splitlines():
        if match := re.match(r"^\\c\s+(\d+)\b", line):
            chapter = int(match[1])
        elif chapter == 119:
            if match := re.match(r"^\\s1\s+(.*)", line):
                title = match[1].strip()
            elif match := re.match(r"^\\d\s+(.*)", line):
                label = match[1].strip()
            elif (match := re.match(r"^\\v\s+(\d+)\s", line)) and label:
                if not title:
                    raise ValueError("Acrostic label without a stanza title")
                result[int(match[1])] = (title, label)
                title = label = ""
    return result


def repair_acrostic(story: dict, source: dict, headings: dict) -> tuple[int, int]:
    """Relocate only the exact source label incorrectly appended to verse N-1."""
    moved = restored = 0
    by_anchor = {h["beforeVerse"]: h for h in story["headings"]}
    for anchor, (title, label) in headings.items():
        heading = by_anchor.get(anchor)
        corrected = title + "\n" + label
        if heading is None or heading["text"] not in (title, corrected):
            raise ValueError(f"Unexpected Psalm 119 heading at verse {anchor}")
        if heading["text"] != corrected:
            heading["text"] = corrected
            restored += 1
        if anchor == 1:
            continue
        prior = anchor - 1
        for index, bullet in enumerate(story["summaryBullets"]):
            match = TRAILING_REFERENCE.search(bullet)
            if match is None or (int(match[1]), int(match[2])) != (119, prior):
                continue
            scripture = bullet[:match.start()]
            wanted = source[(119, prior, prior)][0]
            if normalize(APP_TAG.sub("", scripture)) == wanted:
                break  # Already repaired.
            suffix = " " + label
            if not scripture.endswith(suffix):
                raise ValueError(f"Missing expected acrostic suffix at Psalm 119:{prior}")
            corrected_text = scripture[:-len(suffix)]
            if normalize(APP_TAG.sub("", corrected_text)) != wanted:
                raise ValueError(f"Other wording differences at Psalm 119:{prior}")
            story["summaryBullets"][index] = corrected_text + bullet[match.start():]
            moved += 1
            break
        else:
            raise ValueError(f"Missing Psalm 119:{prior}")
    return moved, restored


def transplant_jesus_tags(original: str, source: str) -> str:
    """Copy source character-level speech state; preserve every original byte of text."""
    plain = J_TAG.sub("", original)
    if APP_TAG.search(plain):
        raise ValueError("Other display tags require separate reviewed offset handling")
    source_plain = APP_TAG.sub("", source)
    original_letters = "".join(c for c in plain if not c.isspace())
    source_letters = "".join(c for c in source_plain if not c.isspace())
    if original_letters != source_letters:
        raise ValueError("Exact code-point identity required to transfer speech spans")
    mask = j_mask(source)
    if len(mask) != len(original_letters):
        raise ValueError("Unicode normalization changes offsets; needs manual review")
    out: list[str] = []
    active = False
    index = 0
    for character in plain:
        if not character.isspace():
            wanted = mask[index]
            if wanted != active:
                out.append("[J]" if wanted else "[/J]")
                active = wanted
            index += 1
        out.append(character)
    if active:
        out.append("[/J]")
    result = "".join(out)
    if J_TAG.sub("", result) != plain or j_mask(result) != mask:
        raise ValueError("Speech-tag transfer violated text/markup invariants")
    return result


def _validated_span_mask(value: str, spans: list[dict], label: str) -> tuple[bool, ...]:
    """Lock reviewed apparatus to exact, nonoverlapping code-point offsets."""

    result = [False] * len(value)
    prior_end = 0
    for span in spans:
        start, end, text = span.get("start"), span.get("end"), span.get("text")
        if not isinstance(start, int) or not isinstance(end, int) or not isinstance(text, str):
            raise ValueError(f"Malformed reviewed {label} apparatus span")
        if start < prior_end or start < 0 or end <= start or end > len(value):
            raise ValueError(f"Overlapping or invalid reviewed {label} apparatus span")
        if value[start:end] != text:
            raise ValueError(f"Reviewed {label} apparatus text drift")
        result[start:end] = [True] * (end - start)
        prior_end = end
    return tuple(result)


def load_reviewed_presentation_adjudications(repo: Path) -> dict[tuple[str, int, int, int], dict]:
    """Load only the exhaustive, source-pinned presentation adjudications."""

    path = repo / REVIEW_RELATIVE_PATH
    report = json.loads(path.read_text(encoding="utf-8"))
    if report.get("sourceUrl") != SOURCE_URL or report.get("sourceArchiveSha256") != SOURCE_SHA256:
        raise ValueError("Hindi presentation review is not pinned to the repair source")
    summary = report.get("summary", {})
    rows = report.get("adjudications", [])
    if (
        summary.get("appVerseUnits") != 31104
        or summary.get("sourceVerseUnits") != 31104
        or summary.get("strictDifferences") != len(rows)
        or summary.get("presentationOnly") != len(rows)
        or summary.get("unresolvedLexical") != 0
    ):
        raise ValueError("Hindi presentation review is incomplete")
    result: dict[tuple[str, int, int, int], dict] = {}
    for row in rows:
        if row.get("strictEquivalent") is not True:
            raise ValueError("Non-equivalent row cannot authorize speech transfer")
        key = (
            row["sourceBookCode"],
            int(row["chapter"]),
            int(row["verseStart"]),
            int(row["verseEnd"]),
        )
        if key in result:
            raise ValueError(f"Duplicate Hindi presentation adjudication: {key}")
        _validated_span_mask(row["appText"], row["approvedAppApparatus"], "app")
        _validated_span_mask(row["sourceText"], row["approvedSourceApparatus"], "source")
        result[key] = row
    return result


def transplant_reviewed_presentation_jesus_tags(
    original: str,
    source: str,
    adjudication: dict,
) -> str:
    """Transfer speech flags across one exact, reviewed presentation difference.

    Reviewed apparatus is excluded from alignment. App-only apparatus is forced
    to narration so a nearby Jesus span cannot color a cross-reference.
    """

    plain = J_TAG.sub("", original)
    if DISPLAY_TAG.search(plain):
        raise ValueError("Other display tags require separate reviewed offset handling")
    source_plain = APP_TAG.sub("", source)
    app_review = adjudication["appText"]
    source_review = adjudication["sourceText"]
    if normalize(plain) != app_review or normalize(source_plain) != source_review:
        raise ValueError("Reviewed presentation text drift")

    app_apparatus = _validated_span_mask(
        app_review, adjudication["approvedAppApparatus"], "app"
    )
    source_apparatus = _validated_span_mask(
        source_review, adjudication["approvedSourceApparatus"], "source"
    )
    app_nonspace = [(c, app_apparatus[i]) for i, c in enumerate(app_review) if not c.isspace()]
    source_mask = j_mask(source)
    source_characters = [(i, c) for i, c in enumerate(source_review) if not c.isspace()]
    if len(source_mask) != len(source_characters):
        raise ValueError("Source speech mask does not match reviewed source text")
    source_nonspace = [
        (c, source_apparatus[i], source_mask[index])
        for index, (i, c) in enumerate(source_characters)
    ]

    app_letters = "".join(c for c, excluded in app_nonspace if not excluded)
    source_letters = "".join(c for c, excluded, _speech in source_nonspace if not excluded)
    if app_letters != source_letters:
        raise ValueError("Reviewed apparatus removal does not yield exact code-point identity")

    source_speech = iter(speech for _c, excluded, speech in source_nonspace if not excluded)
    wanted_review_mask = tuple(False if excluded else next(source_speech) for _c, excluded in app_nonspace)
    try:
        next(source_speech)
    except StopIteration:
        pass
    else:
        raise ValueError("Unconsumed source speech flags after reviewed alignment")

    # This strict identity lets us render against the original whitespace while
    # preserving every non-J character exactly, without Unicode normalization.
    original_nonspace = "".join(c for c in plain if not c.isspace())
    review_nonspace = "".join(c for c in app_review if not c.isspace())
    if original_nonspace != review_nonspace:
        raise ValueError("Original text cannot be byte-preservingly aligned to review")

    out: list[str] = []
    active = False
    index = 0
    for character in plain:
        if not character.isspace():
            wanted = wanted_review_mask[index]
            if wanted != active:
                out.append("[J]" if wanted else "[/J]")
                active = wanted
            index += 1
        out.append(character)
    if active:
        out.append("[/J]")
    result = "".join(out)
    if J_TAG.sub("", result) != plain or j_mask(result) != wanted_review_mask:
        raise ValueError("Reviewed speech-tag transfer violated text/markup invariants")
    return result


def repair_reviewed_lexical_text(
    code: str,
    key: tuple[int, int, int],
    current: str,
    pinned_source: str,
) -> tuple[str, bool]:
    """Apply only an exact, individually reviewed old-to-source replacement."""

    repair = REVIEWED_LEXICAL_REPAIRS.get((code, *key))
    if repair is None:
        return current, False
    official = repair["official"]
    if pinned_source != official:
        raise ValueError(f"Pinned reviewed source drift at {code} {key}")
    if current == official:
        return current, False
    if current != repair["old"]:
        raise ValueError(f"Reviewed current text drift at {code} {key}")
    return official, True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument(
        "--apply-acrostic-only",
        action="store_true",
        help="apply only the source-pinned Psalm 119 label relocation before regenerating review evidence",
    )
    args = parser.parse_args()
    if args.apply and args.apply_acrostic_only:
        parser.error("choose --apply or --apply-acrostic-only")
    repo = Path(__file__).resolve().parents[1]
    body, _digest = download_archive(SOURCE_URL, SOURCE_SHA256)
    sources = {}
    acrostic_headings = {}
    with zipfile.ZipFile(io.BytesIO(body)) as archive:
        for entry in archive.infolist():
            if not entry.filename.lower().endswith(".usfm"):
                continue
            raw = archive.read(entry)
            code, verses = parse_usfm(raw, entry.filename)
            if code in sources:
                raise ValueError(f"Duplicate source book: {code}")
            sources[code] = verses
            if code == "PSA":
                acrostic_headings = psalm119_headings(raw)
    expected = {code for code, _, _ in BOOKS}
    if set(sources) != expected:
        raise ValueError("Source canon inventory does not match the 66 canonical books")
    if sorted(acrostic_headings) != list(range(1, 177, 8)):
        raise ValueError("Psalm 119 must contain exactly 22 source acrostic headings")

    if args.apply_acrostic_only:
        path = repo / "shared/assets/books/old_testament/hi/psalms.json"
        if set(parse_app(path)) != set(sources["PSA"]):
            raise ValueError("Verse inventory mismatch in psalms; no files changed")
        payload = json.loads(path.read_text(encoding="utf-8"))
        matches = [story for story in payload["stories"] if story["id"] == "psalms-119"]
        if len(matches) != 1:
            raise ValueError("Expected exactly one Psalm 119 story")
        moved, restored = repair_acrostic(matches[0], sources["PSA"], acrostic_headings)
        if moved or restored:
            path.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
                newline="\n",
            )
        print(json.dumps({
            "mode": "apply-acrostic-only",
            "archiveSha256": SOURCE_SHA256,
            "relocatedAcrosticLabels": moved,
            "restoredAcrosticHeadingLabels": restored,
        }, ensure_ascii=False, indent=2))
        return 0

    reviewed_presentation = load_reviewed_presentation_adjudications(repo)
    seen_reviewed_presentation: set[tuple[str, int, int, int]] = set()

    totals: Counter = Counter()
    prepared = []
    changes = []
    for code, collection, book_id in BOOKS:
        path = repo / "shared/assets/books" / collection / "hi" / f"{book_id}.json"
        app = parse_app(path)
        source = sources[code]
        if set(app) != set(source):
            raise ValueError(f"Verse inventory mismatch in {book_id}; no files changed")
        payload = json.loads(path.read_text(encoding="utf-8"))
        modified = False
        for story in payload["stories"]:
            if code == "PSA" and story["id"] == "psalms-119":
                moved, restored = repair_acrostic(story, source, acrostic_headings)
                totals["relocatedAcrosticLabels"] += moved
                totals["restoredAcrosticHeadingLabels"] += restored
                modified |= bool(moved or restored)
            bullets = story.get("summaryBullets", [])
            for index, bullet in enumerate(bullets):
                reference = TRAILING_REFERENCE.search(bullet)
                if reference is None:
                    raise ValueError(f"Missing trailing reference in {book_id}")
                chapter, verse = int(reference[1]), int(reference[2])
                key = chapter, verse, int(reference[3] or verse)
                totals["verseUnits"] += 1
                before = bullet[:reference.start()]
                repaired_text, lexical_changed = repair_reviewed_lexical_text(
                    code, key, before, source[key][0]
                )
                if lexical_changed:
                    bullets[index] = repaired_text + bullet[reference.start():]
                    bullet = bullets[index]
                    before = repaired_text
                    modified = True
                    totals["repairedReviewedLexical"] += 1
                    changes.append(f"{book_id} {chapter}:{verse} wording")
                elif (code, *key) in REVIEWED_LEXICAL_REPAIRS:
                    totals["exactReviewedLexical"] += 1
                current = normalize(before)
                if normalize(APP_TAG.sub("", current)) != source[key][0]:
                    review_key = (code, *key)
                    adjudication = reviewed_presentation.get(review_key)
                    if adjudication is None:
                        totals["unchangedTextDifferences"] += 1
                        continue
                    seen_reviewed_presentation.add(review_key)
                    try:
                        after = transplant_reviewed_presentation_jesus_tags(
                            before, source[key][1], adjudication
                        )
                    except ValueError as exc:
                        raise ValueError(
                            f"{book_id} {chapter}:{verse} reviewed presentation repair failed: {exc}"
                        ) from exc
                    totals["reviewedPresentationUnits"] += 1
                    if j_mask(before) == j_mask(after):
                        totals["exactReviewedPresentationMarkup"] += 1
                        continue
                    bullets[index] = after + bullet[reference.start():]
                    totals["repairedReviewedPresentationMarkup"] += 1
                    modified = True
                    changes.append(f"{book_id} {chapter}:{verse} reviewed presentation markup")
                    continue
                if j_mask(current) == j_mask(source[key][1]):
                    totals["exactMarkup"] += 1
                    continue
                after = transplant_jesus_tags(before, source[key][1])
                if normalize(APP_TAG.sub("", after)) != source[key][0]:
                    raise ValueError(f"Source identity changed in {book_id} {key}")
                bullets[index] = after + bullet[reference.start():]
                totals["repairedMarkup"] += 1
                modified = True
                changes.append(f"{book_id} {chapter}:{verse}")
        if modified:
            prepared.append((path, payload))

    if seen_reviewed_presentation != set(reviewed_presentation):
        missing = sorted(set(reviewed_presentation) - seen_reviewed_presentation)
        raise ValueError(f"Not all reviewed presentation units were checked: {missing[:5]}")

    # Compute and check every proposed replacement before writing any file.
    if args.apply:
        for path, payload in prepared:
            path.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8", newline="\n",
            )
    print(json.dumps({
        "mode": "apply" if args.apply else "read-only",
        "archiveSha256": SOURCE_SHA256,
        **totals,
        "changedBooks": len(prepared),
        "references": changes,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
