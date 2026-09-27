#!/usr/bin/env python3
"""Build complete Iberian reference maps from reviewed native structures.

The optional BibleGateway structure cache is evidence for the named modern
edition's reference markers only.  It is not a source of application wording.
This script never downloads it and pins its complete byte inventory before
using it.  The checked-in map remains usable without the cache.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from import_traditional_editions import write_json
from reference_maps import reference_map_for_edition

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets/books"
CACHE = ROOT / ".scripture-structure-cache/structure"
MARKER = re.compile(r"\((\d+):(\d+)(?:-(\d+))?\)\.?\s*$")
EDITIONS = {
    "es": ("nvi", "rv1909", "NVI", "spaRV1909.zip", "B5BFAC87199A561FCBACB5E32BE5D8D280934B1C6830088D9EB8C68FFBFBE711", "4D57E1F9E58E3310CB5E47153E82E456F673FB6E8B9A3FD9FA0DD8A17FC05C3C"),
    "pt": ("nvt", "almeida1911", "NVT", "porAlmeida1911.zip", "C67FCF72F22A7916B695034F2DA48C10E38432463AA6C60F6CB48F992ACE5FDA", "46040F835A8EB27D889874119E90055345B0480727352F647E16718C511F73B1"),
}
# These are whole passage units manually checked against the two local texts.
# All other rows are matching, named-edition reference markers.
SPECIAL = {
 "es": {
  "1_chronicles": [((1,30),(1,30)), ((1,31),(1,30))] + [((21,v),(21,v+1)) for v in range(16,30)],
  "1_kings": [((22,v),(22,v+1)) for v in range(44,53)],
  "1_samuel": [((23,29),(24,1))] + [((24,v),(24,v+1)) for v in range(1,22)],
  "2_samuel": [((20,26),(20,25))],
  "2_chronicles": [((33,10),(33,10)), ((33,11),(33,10))] + [((33,v),(33,v-1)) for v in range(12,26)],
  "numbers": [((12,16),(13,1))] + [((13,v),(13,v+1)) for v in range(1,33)] + [((13,33),(13,33))] + [((29,40),(30,1))] + [((30,v),(30,v+1)) for v in range(1,16)] + [((30,16),(30,16))],
  "hosea": [((11,12),(12,1))] + [((12,v),(12,v+1)) for v in range(1,14)] + [((12,14),(12,14))],
  "jonah": [((1,17),(2,1))] + [((2,v),(2,v+1)) for v in range(1,10)] + [((2,10),(2,10))],
  "job": [((35,16),(35,15))] + [((38,v),(39,v-38)) for v in range(39,42)] + [((39,v),(39,v+3)) for v in range(1,26)] + [((39,26),(39,29))] + [((39,v),(39,30)) for v in range(27,31)] + [((40,v),(39,30)) for v in range(1,6)] + [((40,v),(40,v-5)) for v in range(6,25)],
  "judges": [((14,19),(14,20)), ((14,20),(14,20))],
  "2_corinthians": [((13,12),(13,12)), ((13,13),(13,12)), ((13,14),(13,13))],
  "3_john": [((1,14),(1,14)), ((1,15),(1,14))],
  "acts": [((19,40),(19,40)), ((19,41),(19,40))],
  "philippians": [((1,16),(1,17)), ((1,17),(1,16))],
 },
 "pt": {
  "1_kings": [((22,v),(22,v+1)) for v in range(44,53)],
  "2_corinthians": [((13,12),(13,12)), ((13,13),(13,13)), ((13,14),(13,14))],
  "3_john": [((1,14),(1,14)), ((1,15),(1,14))],
  "revelation": [((12,18),(13,1)), ((13,1),(13,1))],
 },
}
# One modern verse can span more than one traditional numbered verse. Keep the
# complete native range explicit; never invent a/b subdivisions or discard the
# second target verse. Heading placement uses the first target verse.
SPECIAL_SPANS = {
 "es": {
  "1_chronicles": [
   {"sourceChapter":1,"sourceVerse":32,"targetChapter":1,"targetVerse":31,"targetVerseEnd":32},
   {"sourceChapter":21,"sourceVerse":15,"targetChapter":21,"targetVerse":15,"targetVerseEnd":16},
  ],
  "1_kings": [{"sourceChapter":22,"sourceVerse":43,"targetChapter":22,"targetVerse":43,"targetVerseEnd":44}],
  "judges": [{"sourceChapter":14,"sourceVerse":18,"targetChapter":14,"targetVerse":18,"targetVerseEnd":19}],
 },
 "pt": {
  "1_kings": [
   {"sourceChapter":18,"sourceVerse":33,"targetChapter":18,"targetVerse":33,"targetVerseEnd":34},
   {"sourceChapter":22,"sourceVerse":43,"targetChapter":22,"targetVerse":43,"targetVerseEnd":44},
  ],
  "matthew": [{"sourceChapter":9,"sourceVerse":2,"targetChapter":9,"targetVerse":1,"targetVerseEnd":2}],
  "luke": [
   {"sourceChapter":4,"sourceVerse":18,"targetChapter":4,"targetVerse":18,"targetVerseEnd":19},
   {"sourceChapter":7,"sourceVerse":18,"targetChapter":7,"targetVerse":18,"targetVerseEnd":19},
  ],
  "acts": [
   {"sourceChapter":3,"sourceVerse":20,"targetChapter":3,"targetVerse":19,"targetVerseEnd":20},
   {"sourceChapter":10,"sourceVerse":30,"targetChapter":10,"targetVerse":30,"targetVerseEnd":31},
   {"sourceChapter":13,"sourceVerse":33,"targetChapter":13,"targetVerse":32,"targetVerseEnd":33},
   {"sourceChapter":24,"sourceVerse":2,"targetChapter":24,"targetVerse":2,"targetVerseEnd":3},
  ],
  "1_corinthians": [{"sourceChapter":6,"sourceVerse":9,"targetChapter":6,"targetVerse":9,"targetVerseEnd":10}],
  "2_corinthians": [{"sourceChapter":2,"sourceVerse":11,"targetChapter":2,"targetVerse":10,"targetVerseEnd":11}],
  "philippians": [{"sourceChapter":3,"sourceVerse":13,"targetChapter":3,"targetVerse":13,"targetVerseEnd":14}],
  "1_thessalonians": [{"sourceChapter":2,"sourceVerse":7,"targetChapter":2,"targetVerse":6,"targetVerseEnd":7}],
  "hebrews": [{"sourceChapter":11,"sourceVerse":19,"targetChapter":11,"targetVerse":18,"targetVerseEnd":19}],
  "revelation": [{"sourceChapter":2,"sourceVerse":28,"targetChapter":2,"targetVerse":27,"targetVerseEnd":28}],
 },
}
UNRESOLVED_TARGET_ONLY = {"es": ["Matthew 17:21", "Matthew 18:11", "Matthew 23:14", "Mark 7:16", "Mark 9:44", "Mark 9:46", "Mark 11:26", "Mark 15:28", "Luke 17:36", "Luke 23:17", "John 5:4", "Acts 8:37", "Acts 15:34", "Acts 24:7", "Acts 28:29", "Romans 16:24"], "pt": []}
UNRESOLVED_SOURCE_ONLY = {"es": {}, "pt": {}}

def base_coords(language, collection, book):
    result=set()
    for story in json.loads((ASSETS/collection/language/f"{book}.json").read_text(encoding="utf-8"))["stories"]:
        for line in story["summaryBullets"]:
            m=MARKER.search(line)
            if not m: raise ValueError(f"missing base marker: {language}/{book}")
            result.update((int(m[1]), n) for n in range(int(m[2]), int(m[3] or m[2])+1))
    return result

def target_coords(language, edition, collection, book):
    data=json.loads((ASSETS/"editions"/language/edition/collection/f"{book}.json").read_text(encoding="utf-8"))
    return {(v["chapter"], n) for c in data["chapters"] for v in c["verses"] for n in range(v["verse"],v.get("verseEnd",v["verse"])+1)}

def cache_coords(code, book):
    result=set()
    for path in sorted((CACHE/code/book).glob("*.json"), key=lambda p:int(p.stem)):
        row=json.loads(path.read_text(encoding="utf-8"))
        result.update((row["chapter"], n) for marker in row["markers"] for n in range(marker["start"], marker["end"]+1))
    return result

def cache_hash(code):
    digest=hashlib.sha256()
    for path in sorted((CACHE/code).rglob("*.json")):
        digest.update(path.relative_to(CACHE).as_posix().encode()+b"\0"+path.read_bytes()+b"\0")
    return digest.hexdigest().upper()

def compact(mapping):
    rows=[]
    by_chapter={}
    for source,target in mapping.items(): by_chapter.setdefault(source[0],[]).append((source,target))
    for chapter, values in sorted(by_chapter.items()):
        values.sort(); index=0
        while index<len(values):
            source,target=values[index]; end=index
            mode=None
            while end+1<len(values):
                ns,nt=values[end+1]; ps,pt=values[end]
                if ns[0]!=chapter or ns[1]!=ps[1]+1 or nt[0]!=target[0]: break
                candidate="same" if nt[1]==pt[1] else "linear" if nt[1]==pt[1]+1 else None
                if candidate is None or (mode and mode!=candidate): break
                mode=candidate; end+=1
            last_source,last_target=values[end]
            row={"sourceChapter":source[0],"sourceVerse":source[1],"targetChapter":target[0],"targetVerse":target[1]}
            if last_source[1]!=source[1]: row["sourceVerseEnd"]=last_source[1]
            if mode=="linear" and last_target[1]!=target[1]: row["targetVerseEnd"]=last_target[1]
            rows.append(row); index=end+1
    return rows

def build(language):
    base, alternate, code, archive, archive_hash, expected_cache_hash = EDITIONS[language]
    actual_cache_hash = cache_hash(code)
    if actual_cache_hash != expected_cache_hash:
        raise ValueError(
            f"Publisher structure cache SHA-256 mismatch for {language}/{code}: "
            f"expected {expected_cache_hash}, got {actual_cache_hash}"
        )
    manifest = json.loads(
        (ASSETS / "editions" / language / alternate / "_manifest.json").read_text(encoding="utf-8")
    )
    if manifest.get("source", {}).get("archiveSha256", "").upper() != archive_hash:
        raise ValueError(f"Traditional source archive pin mismatch for {language}/{alternate}")
    books=[]
    for collection in ("old_testament","new_testament"):
        for path in sorted((ASSETS/collection/language).glob("*.json")):
            if path.name.startswith("_"): continue
            book=path.stem; source=base_coords(language,collection,book); modern=cache_coords(code,book); target=target_coords(language,alternate,collection,book)
            if not source <= modern: raise ValueError(f"app coordinates absent from {code}: {book}")
            mapping={coord:coord for coord in source & target}
            for coord in UNRESOLVED_SOURCE_ONLY[language].get(book, []): mapping.pop(coord, None)
            span_rules=[]
            span_sources=set()
            for row in SPECIAL_SPANS[language].get(book, []):
                source_start=row["sourceVerse"]; source_end=row.get("sourceVerseEnd",source_start)
                target_start=row["targetVerse"]; target_end=row.get("targetVerseEnd",target_start)
                source_rows={(row["sourceChapter"],verse) for verse in range(source_start,source_end+1)}
                target_rows={(row["targetChapter"],verse) for verse in range(target_start,target_end+1)}
                if not source_rows <= source or not target_rows <= target:
                    raise ValueError(f"bad reviewed span: {language}/{book} {row}")
                if span_sources & source_rows:
                    raise ValueError(f"overlapping reviewed span: {language}/{book} {row}")
                span_sources.update(source_rows)
                for source_coord in source_rows: mapping.pop(source_coord, None)
                span_rules.append(row)
            for source_coord,target_coord in SPECIAL[language].get(book,[]):
                if source_coord not in source or target_coord not in target: raise ValueError(f"bad reviewed boundary: {language}/{book} {source_coord}->{target_coord}")
                if source_coord in span_sources: raise ValueError(f"duplicate reviewed boundary: {language}/{book} {source_coord}")
                mapping[source_coord]=target_coord
            residual=set(UNRESOLVED_SOURCE_ONLY[language].get(book, []))
            if set(mapping) | span_sources | residual != source: raise ValueError(f"unresolved source coordinates: {language}/{book}: {sorted(source-set(mapping)-span_sources-residual)}")
            rules=compact(mapping)+span_rules
            rules.sort(key=lambda row:(row["sourceChapter"],row["sourceVerse"],row["targetChapter"],row["targetVerse"]))
            books.append({"bookId":book,"complete":not residual,"mappings":rules})
    return {"schemaVersion":1,"language":language,"baseEditionId":base,"editionId":alternate,"books":books,"provenance":{"mappingSource":"Matching named-edition BibleGateway reference markers establish identity rows; reviewed local passage continuity establishes each SPECIAL row. Cached hashes establish modern source structure only, not parity with checked-in wording.","modernStructure":{"provider":"BibleGateway","edition":code,"aggregateSha256":actual_cache_hash,"algorithm":"SHA-256 over sorted relative UTF-8 JSON paths, a NUL byte, and exact file bytes","auditDate":"2026-09-25"},"alternateSource":{"archive":archive,"archiveSha256":archive_hash},"unresolvedTargetOnlyCoordinates":UNRESOLVED_TARGET_ONLY[language],"unresolvedSourceOnlyCoordinates":{book:[f"{chapter}:{verse}" for chapter,verse in coords] for book,coords in UNRESOLVED_SOURCE_ONLY[language].items()},"coveragePolicy":"Every non-residual app-base coordinate has an explicit mapping. Residual source-only and target-only coordinates remain unresolved and fail closed; no a/b subdivisions or inferred adjacent mappings."}}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--package",
        action="store_true",
        help="Also update only the Spanish and Portuguese packaged maps and manifest referenceMap fields",
    )
    args = parser.parse_args()
    for language, (_,edition,*_) in EDITIONS.items():
        output=ROOT/"tools/traditional"/f"{language}_{edition}_reference_map.json"
        output.write_text(json.dumps(build(language),ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
        if args.package:
            packaged = reference_map_for_edition(ROOT, language, edition)
            if packaged is None:
                raise ValueError(f"No combined map for {language}/{edition}")
            directory = ASSETS / "editions" / language / edition
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
