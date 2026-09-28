#!/usr/bin/env python3
"""Build source-bound same-number maps for unambiguous native coordinates.

Equal verse counts or one shared coordinate are not proof. A whole chapter
qualifies only when both editions expose the same verse coordinates and no
earlier reviewed exception rule touches either native unit. Native grouping
may vary; an identity map never splits a combined verse line.
"""

from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import unicodedata

from audit_reference_coverage import ASSETS_ROOT, EDITIONS, MARKER, base_units, overlay_units
from reference_maps import reference_map_for_edition


TAG = re.compile(r"\[/?(?:J|DN|ADD)\]")
ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / ".scripture-structure-cache/live-browser-parity-2026-09-26"
OUTPUT = Path(__file__).with_name("concordant_identity_reference_maps.json")
REVIEWED_SEMANTIC_MAPS = Path(__file__).with_name("reviewed_semantic_reference_maps.json")
LANGUAGES = tuple(EDITIONS)
ARABIC_LUKE7_STORY_SHA256 = "d2854df21f8f5303dfecd6d2945f1e28871e8fab7b9057eee8d17f490d6ddf3d"
ARABIC_LUKE715_PUBLISHER_SHA256 = "71dd0d1d776d6b9c87d421c37b2bddaf5049f33eb108442f6825e03e1b8e9f6a"
ARABIC_LUKE7_SNAPSHOT_SHA256 = "acc0527230c68ca4667fec16e6d66d67f7be26540a82dada0eca8b3d35639c23"


def normalized_bigrams(text: str) -> set[str]:
    text = unicodedata.normalize("NFKC", TAG.sub("", text)).casefold()
    chars = "".join(ch for ch in text if ch.isalnum())
    return {chars[index:index + 2] for index in range(max(0, len(chars) - 1))}


def similarity(left: str, right: str) -> float:
    a, b = normalized_bigrams(left), normalized_bigrams(right)
    return len(a & b) / len(a | b) if a and b else 0.0


def base_chapters(path: Path) -> dict[int, list[tuple[tuple[int, int], str]]]:
    book = json.loads(path.read_text(encoding="utf-8"))
    chapters: dict[int, list[tuple[tuple[int, int], str]]] = {}
    for story in book["stories"]:
        chapter = int(story["id"].rsplit("-", 1)[-1])
        units = []
        for bullet in story["summaryBullets"]:
            marker = MARKER.search(bullet)
            if marker is None or int(marker[1]) != chapter:
                raise ValueError(f"Malformed native base marker: {path} {chapter}")
            units.append(((int(marker[2]), int(marker[3] or marker[2])), bullet[:marker.start()]))
        chapters[chapter] = units
    return chapters


def alternate_chapters(path: Path) -> dict[int, list[tuple[tuple[int, int], str]]]:
    book = json.loads(path.read_text(encoding="utf-8"))
    return {
        chapter["number"]: [
            ((verse["verse"], verse.get("verseEnd", verse["verse"])), verse["text"])
            for verse in chapter["verses"]
        ]
        for chapter in book["chapters"]
    }


def coordinates(units: list[tuple[tuple[int, int], str]]) -> tuple[int, ...]:
    """Expand native spans, rejecting overlaps and out-of-order lines."""
    result: list[int] = []
    for (first, last), _ in units:
        if first < 1 or last < first or (result and first <= result[-1]):
            return ()
        result.extend(range(first, last + 1))
    if not result or result[0] != 1:
        return ()
    return tuple(result)


def identity_ranges(source, target, prior):
    """Return only spans containing entire untouched source and target units."""
    source_coords, target_coords = coordinates(source), coordinates(target)
    if not source_coords or not target_coords:
        return []
    source_index = {n: span for span, _ in source for n in range(span[0], span[1] + 1)}
    target_index = {n: span for span, _ in target for n in range(span[0], span[1] + 1)}
    source_occupied = {n for row in prior for n in range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1)}
    target_occupied = {n for row in prior for n in range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1)}
    eligible = (set(source_coords) & set(target_coords)) - source_occupied - target_occupied
    # A combined native unit is indivisible in either direction. Remove any
    # candidate whose complete source or target unit is not also eligible.
    while True:
        reduced = {n for n in eligible
                   if all(x in eligible for span in (source_index[n], target_index[n])
                          for x in range(span[0], span[1] + 1))}
        if reduced == eligible:
            break
        eligible = reduced
    ranges = []
    for n in sorted(eligible):
        if ranges and ranges[-1][1] + 1 == n:
            ranges[-1] = (ranges[-1][0], n)
        else:
            ranges.append((n, n))
    return ranges


def score(source: list[tuple[tuple[int, int], str]], target: list[tuple[tuple[int, int], str]]) -> tuple[float, float, int]:
    if [u[0] for u in source] != [u[0] for u in target]:
        return 0.0, 0.0, 0
    if len(source) < 3:
        return 0.0, 0.0, len(source)
    diagonal = [similarity(a[1], b[1]) for a, b in zip(source, target)]
    shifted = [max(similarity(source[i][1], target[i - 1][1]),
                   similarity(source[i][1], target[i + 1][1]))
               for i in range(1, len(source) - 1)]
    return sum(diagonal) / len(diagonal), sum(shifted) / len(shifted), len(source)


def audit() -> dict:
    result = {}
    for language, (_, edition) in EDITIONS.items():
        counts = Counter()
        examples: list[dict] = []
        for collection in ("old_testament", "new_testament"):
            for base_path in sorted((ASSETS_ROOT / collection / language).glob("*.json")):
                if base_path.name.startswith("_"):
                    continue
                target_path = ASSETS_ROOT / "editions" / language / edition / collection / base_path.name
                source = base_chapters(base_path)
                target = alternate_chapters(target_path)
                for chapter, source_units in source.items():
                    target_units = target.get(chapter)
                    if target_units is None or not coordinates(source_units) or coordinates(source_units) != coordinates(target_units):
                        counts["structureDivergentChapters"] += 1
                        continue
                    counts["structureIdenticalChapters"] += 1
                    diagonal, shifted, length = score(source_units, target_units)
                    if length < 3:
                        counts["shortChapters"] += 1
                    elif diagonal >= 0.20 and diagonal >= shifted * 1.5:
                        counts["strongConcordanceChapters"] += 1
                        counts["strongConcordanceUnits"] += length
                    else:
                        counts["weakConcordanceChapters"] += 1
                        if len(examples) < 12:
                            examples.append({"book": base_path.stem, "chapter": chapter,
                                             "units": length, "diagonal": round(diagonal, 3),
                                             "shifted": round(shifted, 3)})
        result[language] = {"counts": dict(sorted(counts.items())), "weakExamples": examples}
    return result


def canonical_digest(value: object) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def verse_payload_digest(verses: list[dict]) -> str:
    """Bind traditional text and native markers, independent of display tags."""
    return canonical_digest([{**verse, "text": TAG.sub("", verse["text"])} for verse in verses])


def reviewed_semantic_maps(languages: tuple[str, ...] | None = None) -> list[dict]:
    """Load reviewed non-identity rows that package after generated identities."""
    document = json.loads(REVIEWED_SEMANTIC_MAPS.read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1 or not isinstance(document.get("maps"), list):
        raise ValueError(f"Unsupported reviewed semantic map schema: {REVIEWED_SEMANTIC_MAPS}")
    seen = set()
    for entry in document["maps"]:
        language = entry.get("language")
        expected = EDITIONS.get(language)
        if (entry.get("schemaVersion") != 1 or expected is None
            or (entry.get("baseEditionId"), entry.get("editionId")) != expected
            or entry.get("provenance", {}).get("replacesPreviouslyMappedIdentityUnits") is not True):
            raise ValueError(f"Invalid reviewed semantic map identity: {language}")
        for book in entry.get("books", []):
            if book.get("complete") is not False or not book.get("mappings"):
                raise ValueError(f"Invalid reviewed semantic map book: {language}/{book.get('bookId')}")
            for row in book["mappings"]:
                values = [row.get(key) for key in ("sourceChapter", "sourceVerse", "targetChapter", "targetVerse")]
                if any(not isinstance(value, int) or value < 1 for value in values):
                    raise ValueError(f"Invalid reviewed semantic map row: {language}/{book['bookId']}")
                source_end = row.get("sourceVerseEnd", row["sourceVerse"])
                target_end = row.get("targetVerseEnd", row["targetVerse"])
                if source_end < row["sourceVerse"] or target_end < row["targetVerse"]:
                    raise ValueError(f"Invalid reviewed semantic map range: {language}/{book['bookId']}")
                identity = (language, entry["editionId"], book["bookId"], row["sourceChapter"],
                            row["sourceVerse"], source_end, row["targetChapter"], row["targetVerse"], target_end)
                if identity in seen:
                    raise ValueError(f"Duplicate reviewed semantic map row: {identity}")
                seen.add(identity)
    return [entry for entry in document["maps"]
            if languages is None or entry["language"] in languages]


def evidence_for(language: str, book_code: str, chapter: int, story: dict) -> str | None:
    """Require fresh exact-source browser parity bound to this local story."""
    if language == "en":
        # The English BSB corpus is the checked-in app source. KJV source text
        # was independently pinned and checked by the traditional validator.
        return "checked-in-bsb-source"
    path = EVIDENCE / language / f"{book_code}.{chapter}.json"
    if not path.is_file():
        return None
    evidence = json.loads(path.read_text(encoding="utf-8"))
    if (language, book_code, chapter) == ("ar", "LUK", 7):
        # Bible.com has an empty SAB 7:15. The app's complete verse is pinned
        # to the International Sharif Bible Society Luke PDF and corroborated
        # by Bilughatain; all 49 other native verses match the rendered page.
        verse15 = [bullet[:match.start()] for bullet in story["summaryBullets"]
                   if (match := MARKER.search(bullet)) and (int(match[1]), int(match[2]), int(match[3] or match[2])) == (7, 15, 15)]
        if (canonical_digest(story) == ARABIC_LUKE7_STORY_SHA256
            and len(verse15) == 1
            and hashlib.sha256(verse15[0].strip().encode("utf-8")).hexdigest() == ARABIC_LUKE715_PUBLISHER_SHA256
            and evidence.get("status") == "source_defect"
            and evidence.get("sourceDefectCode") == "locked_sab_luk_7_15_empty_native_range"
            and evidence.get("sourceDefectRanges") == ["LUK.7.15"]
            and evidence.get("rangesCompared") == 49
            and evidence.get("snapshotSha256") == ARABIC_LUKE7_SNAPSHOT_SHA256):
            return "publisher-fallback-sab-luke7:" + ARABIC_LUKE715_PUBLISHER_SHA256
        return None
    if (evidence.get("status") != "match" or evidence.get("evidenceSchemaVersion") != 1
        or evidence.get("localStorySha256") != canonical_digest(story)
        or not re.fullmatch(r"[0-9a-f]{64}", evidence.get("snapshotSha256", ""))):
        return None
    return evidence["snapshotSha256"]


def generate() -> dict:
    """Generate source-backed identities only outside previously mapped chapters."""
    maps = []
    for language, (base_edition, alternate) in EDITIONS.items():
        if language not in LANGUAGES:
            continue
        existing = reference_map_for_edition(ROOT, language, alternate, include_concordant=False)
        existing_books = {book["bookId"]: book for book in existing["books"]} if existing else {}
        books = []
        evidence_rows = []
        skipped = Counter()
        for collection in ("old_testament", "new_testament"):
            for base_path in sorted((ASSETS_ROOT / collection / language).glob("*.json")):
                if base_path.name.startswith("_"):
                    continue
                target_path = ASSETS_ROOT / "editions" / language / alternate / collection / base_path.name
                base_doc = json.loads(base_path.read_text(encoding="utf-8"))
                target_doc = json.loads(target_path.read_text(encoding="utf-8"))
                book_code = target_doc["sourceBookCode"]
                source = base_chapters(base_path)
                target = alternate_chapters(target_path)
                previous = existing_books.get(base_path.stem, {}).get("mappings", [])
                rows = []
                for story in base_doc["stories"]:
                    chapter = int(story["id"].rsplit("-", 1)[-1])
                    source_units, target_units = source[chapter], target.get(chapter)
                    if target_units is None:
                        skipped["structureDivergent"] += 1
                        continue
                    local_prior = [row for row in previous
                                   if row["sourceChapter"] == chapter or row["targetChapter"] == chapter]
                    source_prior = [row for row in local_prior if row["sourceChapter"] == chapter]
                    target_prior = [row for row in local_prior if row["targetChapter"] == chapter]
                    source_occupied = {n for row in source_prior for n in range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1)}
                    target_occupied = {n for row in target_prior for n in range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1)}
                    # Feed each side's occupied coordinates independently;
                    # a bridge can touch a different chapter on the other side.
                    occupied = ([{"sourceVerse": n, "targetVerse": n} for n in sorted(source_occupied | target_occupied)])
                    ranges = identity_ranges(source_units, target_units, occupied)
                    if not ranges:
                        skipped["noUntouchedNativeUnits"] += 1
                        continue
                    proof = evidence_for(language, book_code, chapter, story)
                    if proof is None:
                        skipped["sourceParityNotBound"] += 1
                        continue
                    for first, last in ranges:
                        rows.append({"sourceChapter": chapter, "sourceVerse": first,
                                     "sourceVerseEnd": last, "targetChapter": chapter,
                                     "targetVerse": first, "targetVerseEnd": last})
                    evidence_rows.append((language, collection, base_path.stem, chapter,
                                          proof, canonical_digest(story),
                                          verse_payload_digest(target_doc["chapters"][chapter - 1]["verses"])))
                if rows:
                    all_source = {(chapter, n) for chapter, units in source.items() for n in coordinates(units)}
                    all_target = {(chapter, n) for chapter, units in target.items() for n in coordinates(units)}
                    covered = {(row["sourceChapter"], n) for row in rows
                               for n in range(row["sourceVerse"], row["sourceVerseEnd"] + 1)}
                    books.append({"bookId": base_path.stem,
                                  "complete": not previous and covered == all_source == all_target,
                                  "mappings": rows})
        if not books:
            continue
        maps.append({
            "schemaVersion": 1,
            "language": language,
            "baseEditionId": base_edition,
            "editionId": alternate,
            "books": books,
            "provenance": {
                "auditDate": "2026-09-26",
                "method": "Same-coordinate identity only for complete native units untouched by existing exception rules; combined lines remain whole. Non-English base stories require fresh hash-bound rendered Bible.com parity. Text similarity is diagnostic, not a gate.",
                "baseAuthority": "Checked-in BSB source" if language == "en" else "Bible.com rendered chapter parity evidence",
                "targetAuthority": "SHA-pinned traditional source archive and validated overlay",
                "avoidsPreviouslyMappedChapters": True,
                "evidenceManifestSha256": canonical_digest(evidence_rows),
                "mappedChapters": len(evidence_rows),
                "skippedChapters": dict(sorted(skipped.items())),
            },
        })
    maps.extend(reviewed_semantic_maps(LANGUAGES))
    return {"schemaVersion": 1, "maps": maps}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Write reviewed identity candidates into the supplement")
    parser.add_argument("--package", action="store_true", help="Package combined maps and manifest metadata; requires --write")
    parser.add_argument("--package-languages", nargs="*", help="Limit packaged languages while still writing the full supplement")
    args = parser.parse_args()
    if args.package and not args.write:
        parser.error("--package requires --write")
    result = generate()
    for entry in result["maps"]:
        print(entry["language"], len(entry["books"]),
              entry["provenance"].get("mappedChapters", "reviewed-boundary"),
              entry["provenance"].get("skippedChapters", {}))
    if args.write:
        OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.package:
        for entry in result["maps"]:
            language, alternate = entry["language"], entry["editionId"]
            if args.package_languages is not None and language not in args.package_languages:
                continue
            combined = reference_map_for_edition(ROOT, language, alternate)
            if combined is None:
                raise ValueError(f"No combined reference map: {language}/{alternate}")
            directory = ASSETS_ROOT / "editions" / language / alternate
            manifest_path = directory / "_manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            if (manifest.get("language"), manifest.get("editionId")) != (language, alternate):
                raise ValueError(f"Manifest identity mismatch: {manifest_path}")
            manifest["referenceMap"] = {
                "path": "_reference_map.json",
                "books": [book["bookId"] for book in combined["books"]],
                "provenance": combined["provenance"],
            }
            (directory / "_reference_map.json").write_text(
                json.dumps(combined, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            manifest_path.write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
