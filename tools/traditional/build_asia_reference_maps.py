#!/usr/bin/env python3
"""Build the reviewed Simplified Chinese CCB-to-CUV reference map.

This uses cached publisher CCB marker spans only for verse boundaries. It does
not compare or copy CCB wording, and it refuses a coordinate absent from the
pinned CUV overlay or checked-in app corpus.
"""

from __future__ import annotations

import json
import hashlib
import argparse
from pathlib import Path

from audit_reference_coverage import ASSETS_ROOT, base_units, overlay_units


ROOT = Path(__file__).resolve().parents[2]
CACHE_ROOT = ROOT / ".scripture-structure-cache" / "structure"
CACHE = CACHE_ROOT / "CCB"
OUTPUT = Path(__file__).with_name("asia_reference_maps.json")
COLLECTIONS = ("old_testament", "new_testament")


def spans(book_id: str) -> list[tuple[int, int, int]]:
    result = []
    for path in sorted((CACHE / book_id).glob("*.json"), key=lambda item: int(item.stem)):
        payload = json.loads(path.read_text(encoding="utf-8"))
        if payload.get("schemaVersion") != 3 or payload.get("edition") != "CCB":
            raise ValueError(f"Unexpected CCB cache document: {path}")
        chapter = payload.get("chapter")
        if not isinstance(chapter, int) or chapter < 1:
            raise ValueError(f"Invalid CCB chapter: {path}")
        for marker in payload.get("markers", []):
            if marker.get("empty"):
                continue
            start, end = marker.get("start"), marker.get("end")
            if not isinstance(start, int) or not isinstance(end, int) or start < 1 or end < start:
                raise ValueError(f"Invalid CCB marker: {path}")
            result.append((chapter, start, end))
    if not result:
        raise ValueError(f"Missing CCB cache markers: {book_id}")
    return result


def mapping(chapter: int, start: int, end: int) -> dict[str, int]:
    row = {"sourceChapter": chapter, "sourceVerse": start, "targetChapter": chapter, "targetVerse": start}
    if end != start:
        row["sourceVerseEnd"] = end
        row["targetVerseEnd"] = end
    return row


def compact(rows: list[tuple[int, int, int]]) -> list[tuple[int, int, int]]:
    """Compress only adjacent single-verse CCB units; keep native bridges whole."""
    result: list[tuple[int, int, int]] = []
    run: tuple[int, int, int] | None = None

    def flush() -> None:
        nonlocal run
        if run is not None:
            result.append(run)
            run = None

    for chapter, start, end in rows:
        if start != end:
            flush()
            result.append((chapter, start, end))
        elif run is not None and run[0] == chapter and run[2] + 1 == start:
            run = (chapter, run[1], end)
        else:
            flush()
            run = (chapter, start, end)
    flush()
    return result


def cache_sha256() -> str:
    """Hash sorted relative paths and raw JSON bytes without retaining the cache."""
    digest = hashlib.sha256()
    for path in sorted(CACHE.rglob("*.json"), key=lambda item: item.relative_to(CACHE_ROOT).as_posix()):
        digest.update(path.relative_to(CACHE_ROOT).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def build() -> dict:
    books = []
    for collection in COLLECTIONS:
        for base_path in sorted((ASSETS_ROOT / collection / "zh-Hans").glob("*.json")):
            if base_path.name.startswith("_"):
                continue
            book_id = base_path.stem
            source_units, source_index = base_units(base_path)
            _, target_index = overlay_units(
                ASSETS_ROOT / "editions" / "zh-Hans" / "cuv" / collection / base_path.name,
                "zh-Hans", "cuv", collection, book_id,
            )
            rows = []
            source_native_units = {(unit.chapter, unit.start, unit.end) for unit in source_units}
            direct_spans = [span for span in spans(book_id) if span in source_native_units]
            for chapter, start, end in compact(direct_spans):
                coordinates = {(chapter, verse) for verse in range(start, end + 1)}
                if not coordinates <= set(source_index):
                    raise ValueError(f"CCB cache span absent from app corpus: {book_id} {chapter}:{start}-{end}")
                if not coordinates <= set(target_index):
                    raise ValueError(f"CCB cache span absent from CUV: {book_id} {chapter}:{start}-{end}")
                rows.append(mapping(chapter, start, end))
            # The checked-in CCB corpus has a separately reviewed John 7:53
            # unit absent from the BibleGateway cache. CUV combines its clause
            # with 8:1, so it cannot be inferred from the cache and stays explicit.
            if book_id == "john":
                rows.append({"sourceChapter": 7, "sourceVerse": 53, "targetChapter": 8, "targetVerse": 1})
            books.append({"bookId": book_id, "complete": False, "mappings": rows})
    return {
        "schemaVersion": 1,
        "maps": [{
            "schemaVersion": 1,
            "language": "zh-Hans",
            "baseEditionId": "ccb",
            "editionId": "cuv",
            "books": books,
            "provenance": {
                "mappingSource": "Direct comparison of cached BibleGateway CCB publisher marker spans with the checked-in CCB corpus and pinned Simplified CUV overlay. A generated identity row exists only where the CCB cache and app corpus have the identical native marker range and that complete coordinate range exists in CUV. This establishes reference boundaries only, not CCB wording parity.",
                "ccbCache": ".scripture-structure-cache/structure/CCB (1189 canonical chapter documents with canonical URLs, version metadata, page SHA-256 values, and native marker spans)",
                "ccbCacheAuditDate": "2026-09-26",
                "ccbCacheAggregateSha256": cache_sha256(),
                "ccbCacheHashMethod": "SHA-256 over sorted structure-root-relative POSIX path, NUL, exact file bytes, NUL for each JSON",
                "ccbPublisherVersionLabel": "Chinese Contemporary Bible (Simplified) (CCB)",
                "ccbPublisherCopyright": "Chinese Contemporary Bible Copyright © 1979, 2005, 2007, 2011 by Biblica® Used by permission. All rights reserved worldwide.",
                "ccbPublisherDetailsUrl": "https://www.biblegateway.com/versions/Chinese-Contemporary-Bible-CCB/",
                "ccbCanonicalVersion": "BibleGateway CCB; source structure only, not proof of the different Bible.com CCB revision's wording parity.",
                "sourceUrl": "https://ebible.org/Scriptures/cmn-cu89s_usfm.zip",
                "sourceArchiveSha256": "68df122e9195e071dc286f19ef53e530fcaadb3a16a7dc34b8430b7062f70598",
                "sourceLicense": "Public Domain",
                "coveragePolicy": "Rows derive only from cached publisher marker spans with identical local CCB native ranges and present CUV coordinates. The separately reviewed John 7:53 to 8:1 merge is explicit. Cache/app grouping mismatches and coordinates absent from either side remain unmapped; no a/b subdivisions or count-based inference."
            }
        }]
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-packaged", action="store_true", help="Synchronize only the generated CUV reference-map artifact and manifest pointer")
    args = parser.parse_args()
    OUTPUT.write_text(json.dumps(build(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.write_packaged:
        from reference_maps import reference_map_for_edition

        packaged = reference_map_for_edition(ROOT, "zh-Hans", "cuv")
        if packaged is None:
            raise ValueError("Generated CCB-to-CUV map was not packageable")
        destination = ASSETS_ROOT / "editions" / "zh-Hans" / "cuv"
        (destination / "_reference_map.json").write_text(
            json.dumps(packaged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        manifest_path = destination / "_manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["referenceMap"] = {
            "path": "_reference_map.json",
            "books": [book["bookId"] for book in packaged["books"]],
            "provenance": packaged["provenance"],
        }
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
