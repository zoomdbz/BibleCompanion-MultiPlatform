# Publisher-structure reference maps

`publisher_structure_reference_maps.json` adds whole-native-unit identity
crosswalks for German SCH2000 to Luther 1912, Italian NR2006 to Diodati 1885,
and Russian NRT to Synodal 1876. It contains no Scripture wording. Its only
purpose is edition-aware reference navigation.

| Pair | New books | New chapters | New native units | Forward coverage after packaging | Reverse coverage |
|---|---:|---:|---:|---:|---:|
| SCH2000 / Luther 1912 | 61 | 861 | 24,595 | 27,161 / 31,171 | 27,094 / 31,104 |
| NR2006 / Diodati 1885 | 65 | 1,112 | 29,071 | 29,162 / 31,104 | 29,162 / 31,095 |
| NRT / Synodal 1876 | 65 | 998 | 27,524 | 27,957 / 31,163 | 27,957 / 31,169 |

The new rows exclude every book already governed by the reviewed German
Psalms, German Isaiah, Italian Job, Russian Romans, or New Testament exception
maps. They preserve those maps unchanged. For each new chapter, the builder
requires **the exact ordered native marker sequence** to agree among four
structures: the publisher's cached canonical page, the checked-in modern
base, the hash-pinned traditional USFM source, and the packaged traditional
overlay. A count or last-verse match cannot pass. Empty publisher markers and
any chapter touched by a CrossWire exception rule stay unmapped. A matched
range maps by native verse numbers, never by invented `a`/`b` subdivisions.
The source and target must each contain the same complete native units.

The publisher structure cache came from BibleGateway audits on 2026-09-25.
Every cached chapter carries the canonical URL and version, page SHA-256,
native marker spans, marker text hashes, and version/copyright metadata.
The local `.scripture-structure-cache` directory is ignored; it is **not**
committed. The JSON provenance pins the deterministic aggregate SHA-256 over
sorted structure-root-relative paths, NUL, exact file bytes, NUL:

| Publisher cache | Aggregate SHA-256 |
|---|---|
| SCH2000 | `DC97C47D0FF3D50D1D5FD2453F57A9962170068AD75D2D5C916CBCB6ABB1B8A1` |
| NR2006 | `402D5927149F8148CA07B0DF85C5E7A8EC5006A538814D9E1AC48E73A8C3952B` |
| NRT | `2203B340713936EB2957F62C9463F13F8F1E9B1EFEDB8EA5EC735295D157F634` |

The traditional archives are the importer-pinned public-domain eBible USFM
sources. German and Russian additionally quarantine chapters named in the
pinned CrossWire Luther and Synodal versification tables. For Italian, the
already-reviewed Job shift remains in its separate exception map; the new
rows require four-way marker identity elsewhere. These structural checks
establish a reference correspondence, not publisher word-for-word text parity.

The three edition `_reference_map.json` files and their manifest
`referenceMap` fields are packaged by the map-only `--package` option. That
option does not touch Scripture text or overlay book files. The pinned-source
validator passed after packaging.

French NBS and Arabic SAB have no complete local publisher structure cache.
No new identity rows were inferred for them. French retains its independently
reviewed 19-book partial map (723 / 31,154 forward units); Arabic remains
unmapped (0 / 31,104). More map work requires source structures or another
independent verse-unit authority, not an equal-count comparison.

Reproduce with the original pinned local inputs, if available:

```powershell
python -X utf8 -B tools/traditional/build_publisher_structure_maps.py --source-root 'C:\Users\Dominic\AppData\Local\Temp\bible-traditional-sources' --table-root 'C:\Users\Dominic\AppData\Local\Temp'
python -X utf8 -B -m unittest discover -s tools/traditional -p 'test_publisher_structure_reference_maps.py' -v
python -X utf8 -B tools/traditional/validate_traditional_editions.py
```

The source-reproduction test skips only if the ignored cache or original
archives are unavailable. Repository-only schema, coordinate, package, and
provenance tests still run.
