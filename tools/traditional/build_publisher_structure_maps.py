#!/usr/bin/env python3
"""Build conservative whole-chapter identity maps from pinned source structures.

The ignored BibleGateway caches carry publisher version metadata, page hashes,
and native marker spans for SCH2000, NR2006, and NRT. The target markers come
from SHA-pinned eBible USFM archives. A chapter maps only when its publisher,
checked-in base, raw USFM, and packaged overlay all expose exactly the same
nonempty native markers. Known CrossWire exception chapters are excluded.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from audit_reference_coverage import base_units, overlay_units
from import_traditional_editions import EDITIONS, write_json
from reference_maps import reference_map_for_edition


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets/books"
OUT = Path(__file__).with_name("publisher_structure_reference_maps.json")
PAIRS = {
    "de": ("SCH2000", "sch2000", "luther1912", "CrossWire-Luther-a2c51f3.properties"),
    "it": ("NR2006", "nr06", "diodati1885", None),
    "ru": ("NRT", "nrt_nrp", "synodal1876", "CrossWire-Synodal-a2c51f3.properties"),
}
CACHE_RUNS = {
    "de": "20260925-222448Z-56028",
    "it": "20260925-221510Z-18048",
    "ru": "20260925-215606Z-53896",
}
CACHE = ROOT / ".scripture-structure-cache/structure"
MARKER = re.compile(r"^\\v\s+(\d+)(?:-(\d+))?\b")
CHAPTER = re.compile(r"^\\c\s+(\d+)\b")
REFERENCE = re.compile(r"([1-3]?[A-Za-z]+)\.(\d+)\.(\d+)")
HEX64 = re.compile(r"^[0-9a-fA-F]{64}$")
CROSSWIRE_CODES = {
    "Gen": "GEN", "Exod": "EXO", "Lev": "LEV", "Num": "NUM", "Deut": "DEU",
    "Josh": "JOS", "1Sam": "1SA", "2Sam": "2SA", "1Kgs": "1KI", "2Kgs": "2KI",
    "1Chr": "1CH", "2Chr": "2CH", "Neh": "NEH", "Job": "JOB", "Ps": "PSA",
    "Eccl": "ECC", "Song": "SNG", "Isa": "ISA", "Jer": "JER", "Ezek": "EZK",
    "Dan": "DAN", "Hos": "HOS", "Joel": "JOL", "Jonah": "JON", "Mic": "MIC",
    "Nah": "NAM", "Zech": "ZEC", "Mal": "MAL", "Acts": "ACT", "Rom": "ROM",
    "2Cor": "2CO", "3John": "3JN", "Rev": "REV",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cache_digest(directory: Path) -> str:
    value = hashlib.sha256()
    for path in sorted(directory.rglob("*.json"), key=lambda p: p.relative_to(CACHE).as_posix()):
        value.update(path.relative_to(CACHE).as_posix().encode("utf-8"))
        value.update(b"\0")
        value.update(path.read_bytes())
        value.update(b"\0")
    return value.hexdigest()


def existing_books(language: str) -> set[str]:
    names = ["edition_reference_exceptions.json", "nt_reference_maps.json"]
    if language == "de":
        names.append("german_psalm_reference_map.json")
    claimed: set[str] = set()
    for name in names:
        doc = json.loads(OUT.with_name(name).read_text(encoding="utf-8"))
        candidates = doc.get("maps", [doc])
        for item in candidates:
            if item["language"] == language:
                claimed.update(book["bookId"] for book in item["books"])
    return claimed


def exception_chapters(path: Path) -> dict[str, set[int]]:
    """Conservatively quarantine every chapter named in a nonidentity rule."""
    result: dict[str, set[int]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line[0] in "#!" or "=" not in line:
            continue
        left, right = line.split("=", 1)
        left_refs = REFERENCE.findall(left)
        right_refs = REFERENCE.findall(right)
        if not left_refs or not right_refs:
            continue
        for side in (left_refs, right_refs):
            book = CROSSWIRE_CODES[side[0][0]]
            chapters = [int(ref[1]) for ref in side]
            result.setdefault(book, set()).update(range(min(chapters), max(chapters) + 1))
    return result


def usfm_books(archive: Path, expected_sha: str) -> dict[str, dict[int, list[tuple[int, int]]]]:
    if digest(archive).lower() != expected_sha.lower():
        raise ValueError(f"Source archive SHA-256 mismatch: {archive}")
    books = {}
    with zipfile.ZipFile(archive) as source:
        for name in source.namelist():
            if not name.lower().endswith(".usfm"):
                continue
            text = source.read(name).decode("utf-8-sig")
            id_match = re.search(r"(?m)^\\id\s+(\S+)", text)
            if not id_match:
                continue
            code = id_match[1]
            chapters: dict[int, list[tuple[int, int]]] = {}
            active: int | None = None
            for line in text.splitlines():
                chapter = CHAPTER.match(line)
                if chapter:
                    active = int(chapter[1])
                    chapters.setdefault(active, [])
                verse = MARKER.match(line)
                if verse and active is not None:
                    start = int(verse[1])
                    chapters[active].append((start, int(verse[2] or start)))
            books[code] = chapters
    return books


def cache_markers(path: Path, edition: str, book: str, chapter: int) -> tuple[list[tuple[int, int]], dict[str, str]]:
    row = json.loads(path.read_text(encoding="utf-8"))
    if (row.get("schemaVersion"), row.get("edition"), row.get("book"), row.get("chapter")) != (3, edition, book, chapter):
        raise ValueError(f"Publisher cache identity drift: {path}")
    if f"version={edition}" not in row.get("canonicalUrl", ""):
        raise ValueError(f"Publisher URL edition drift: {path}")
    if not HEX64.fullmatch(row.get("pageSha256", "")):
        raise ValueError(f"Publisher page hash missing: {path}")
    metadata = row["metadata"]
    if not all(metadata.get(key) for key in ("versionLabel", "copyright", "detailsUrl")):
        raise ValueError(f"Publisher metadata missing: {path}")
    markers = row["markers"]
    if any(m.get("empty") or not HEX64.fullmatch(m.get("textSha256") or "") or (m.get("textLength") or 0) < 1 for m in markers):
        return [], metadata
    return [(m["start"], m["end"]) for m in markers], metadata


def by_chapter(units: list) -> dict[int, list[tuple[int, int]]]:
    result: dict[int, list[tuple[int, int]]] = {}
    for unit in units:
        result.setdefault(unit.chapter, []).append((unit.start, unit.end))
    return result


def generate(source_root: Path, temp_root: Path) -> dict:
    configs = {(item.language, item.edition_id): item for item in EDITIONS}
    maps = []
    for language, (publisher, base, alternate, exception_name) in PAIRS.items():
        config = configs[(language, alternate)]
        archive = source_root / config.archive_name
        original = usfm_books(archive, config.archive_sha256)
        blocked = exception_chapters(temp_root / exception_name) if exception_name else {}
        claimed = existing_books(language)
        books = []
        metadata: dict[str, str] | None = None
        chapter_count = 0
        unit_count = 0
        for collection in ("old_testament", "new_testament"):
            for source_path in sorted((ASSETS / collection / language).glob("*.json")):
                book_id = source_path.stem
                if book_id.startswith("_") or book_id in claimed:
                    continue
                target_path = ASSETS / "editions" / language / alternate / collection / source_path.name
                if not target_path.is_file():
                    continue
                target_doc = json.loads(target_path.read_text(encoding="utf-8"))
                code = target_doc["sourceBookCode"]
                cache_dir = CACHE / publisher / book_id
                if not cache_dir.is_dir():
                    continue
                source_chapters = by_chapter(base_units(source_path)[0])
                target_chapters = by_chapter(overlay_units(target_path, language, alternate, collection, book_id)[0])
                rows = []
                for chapter, source_markers in sorted(source_chapters.items()):
                    cache_path = cache_dir / f"{chapter}.json"
                    if not cache_path.is_file() or chapter in blocked.get(code, set()):
                        continue
                    publisher_markers, current_metadata = cache_markers(cache_path, publisher, book_id, chapter)
                    if metadata is None:
                        metadata = current_metadata
                    elif metadata != current_metadata:
                        raise ValueError(f"Inconsistent publisher metadata: {cache_path}")
                    if not publisher_markers or not (source_markers == publisher_markers == original.get(code, {}).get(chapter) == target_chapters.get(chapter)):
                        continue
                    # Exact marker sequence, not just a last-verse or count check.
                    for start, end in publisher_markers:
                        if rows and rows[-1]["sourceChapter"] == chapter and rows[-1].get("sourceVerseEnd", rows[-1]["sourceVerse"]) + 1 == start:
                            rows[-1]["sourceVerseEnd"] = end
                            rows[-1]["targetVerseEnd"] = end
                        else:
                            row = {"sourceChapter": chapter, "sourceVerse": start,
                                   "targetChapter": chapter, "targetVerse": start}
                            if end != start:
                                row["sourceVerseEnd"] = end
                                row["targetVerseEnd"] = end
                            rows.append(row)
                    chapter_count += 1
                    unit_count += len(publisher_markers)
                if rows:
                    books.append({"bookId": book_id, "complete": False, "mappings": rows})
        if metadata is None:
            raise ValueError(f"No publisher cache metadata for {language}")
        maps.append({"schemaVersion": 1, "language": language, "baseEditionId": base,
                     "editionId": alternate, "books": books,
                     "provenance": {"auditDate": "2026-09-26",
                                    "publisherCacheAuditDate": "2026-09-25",
                                    "publisherCacheRunId": CACHE_RUNS[language],
                                    "coveragePolicy": "Whole native marker units in four independent structures must match exactly. Existing reviewed books and CrossWire exception chapters are excluded. Unlisted coordinates retain source edition; no a/b split.",
                                    "publisherEdition": publisher,
                                    "publisherVersionLabel": metadata["versionLabel"],
                                    "publisherCopyright": metadata["copyright"],
                                    "publisherDetailsUrl": metadata["detailsUrl"],
                                    "publisherCacheAggregateSha256": cache_digest(CACHE / publisher),
                                    "publisherCacheHashMethod": "SHA-256 over sorted structure-root-relative POSIX path, NUL, exact file bytes, NUL for each JSON",
                                    "alternateSourceUrl": config.source_url,
                                    "alternateSourceArchiveSha256": config.archive_sha256.lower(),
                                    "crossWireFile": exception_name,
                                    "crossWireSha256": digest(temp_root / exception_name) if exception_name else None,
                                    "mappedChapters": chapter_count,
                                    "mappedNativeUnits": unit_count}})
    return {"schemaVersion": 1, "maps": maps}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--table-root", type=Path, required=True)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--package", action="store_true", help="Update only the three packaged maps and manifest referenceMap fields; requires --write")
    args = parser.parse_args()
    if args.package and not args.write:
        parser.error("--package requires --write")
    doc = generate(args.source_root, args.table_root)
    for item in doc["maps"]:
        proof = item["provenance"]
        print(f"{item['language']}: {len(item['books'])} books, {proof['mappedChapters']} chapters, {proof['mappedNativeUnits']} native units")
    if args.write:
        OUT.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.package:
        for language, (_publisher, _base, alternate, _table) in PAIRS.items():
            packaged = reference_map_for_edition(ROOT, language, alternate)
            if packaged is None:
                raise ValueError(f"No combined map for {language}/{alternate}")
            directory = ASSETS / "editions" / language / alternate
            manifest_path = directory / "_manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["referenceMap"] = {
                "path": "_reference_map.json",
                "books": [book["bookId"] for book in packaged["books"]],
                "provenance": packaged["provenance"],
            }
            write_json(directory / "_reference_map.json", packaged)
            write_json(manifest_path, manifest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
