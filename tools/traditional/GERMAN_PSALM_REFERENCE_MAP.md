# German Psalm reference map provenance

`german_psalm_reference_map.json` is a complete forward crosswalk from the
numbered Psalm references used by SCH2000 to the verse units in the repository's
eBible Luther 1912 overlay. It covers all 2,527 source coordinates in all 150
Psalms and all 2,461 target coordinates. It does not alter Scripture text.

## Primary mapping source

The alignment comes from STEPBible's TVTMS (Translators Versification
Traditions with Methodology for Standardisation) dataset, not from a comparison
of chapter counts.

- Repository: <https://github.com/STEPBible/STEPBible-Data>
- Pinned commit: `b99716b0cddb648ddb95cc786a197180f2f97d48`
- Commit date: `2026-09-18T09:31:36Z`
- Pinned raw file: <https://raw.githubusercontent.com/STEPBible/STEPBible-Data/b99716b0cddb648ddb95cc786a197180f2f97d48/Versification/TVTMS%20-%20Translators%20Versification%20Traditions%20with%20Methodology%20for%20Standardisation%20for%20Eng%2BHeb%2BLat%2BGrk%2BOthers%20-%20STEPBible.org%20CC%20BY.txt>
- SHA-256: `63058e0f20201af4bdaa7d830da5be8f493455d947c5f147d84840b33db9ddf8`
- License: [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)
- Attribution: Data created by STEPBible.org based on work at Tyndale House
  Cambridge.

TVTMS defines its Hebrew column as Masoretic Text (MT) numbering and its
English column as the English standard, which it documents as virtually
identical to King James Version (KJV) numbering in the Old Testament. Its rows
align content units, including Psalm titles, subdivided verses, and merged
verses. This distinction matters in Psalm 13 and Psalm 51; chapter-length
offsets alone produce wrong mappings there.

## Edition checks and transformation

The checked-in modern German Psalm references match the TVTMS Hebrew/MT
coordinate counts. Direct checks against the publisher-hosted SCH2000 edition
confirm the distinctive boundaries, including [Psalm 13](https://www.bible.com/de/bible/157/PSA.13.SCH2000)
and [Psalm 51](https://www.bible.com/de/bible/157/PSA.51.SCH2000).

The target is the repository's pinned eBible source:

- Archive: <https://ebible.org/Scriptures/deu1912_usfm.zip>
- Archive SHA-256:
  `650A8192134A8F0057286C469754EDCFAEE4FBB18800621AEE4563F3055BB39B`
- Edition identity: Lutherbibel 1912, also exposed as DELUT51 by
  [YouVersion](https://www.bible.com/de/bible/51/PSA.8.DELUT)

The eBible Psalm file uses normalized English/KJV verse numbers and folds an
unnumbered Psalm title into target verse 1. The transformation therefore uses
TVTMS Hebrew as the source column and TVTMS English KJV as the target column,
then maps an English `Title` marker to target verse 1. No `a`/`b` subverse was
invented. Adjacent equivalent rows were compressed only after the complete
verse-level alignment was built.

Critical examples:

- SCH2000 Psalm 8:5 maps to Luther 1912 Psalm 8:4.
- SCH2000 Psalm 19:2 maps to Luther 1912 Psalm 19:1.
- SCH2000 Psalm 42:2 maps to Luther 1912 Psalm 42:1.
- SCH2000 Psalm 51:1-3 maps to Luther 1912 Psalm 51:1; source verses 1-2
  are the numbered title and source verse 3 begins the prayer.
- SCH2000 Psalm 13:1-2 maps to Luther 1912 Psalm 13:1; source 13:3-5 maps
  to target 13:2-4; source 13:6 maps to target 13:5-6. This is a real
  merge/split boundary recorded by TVTMS, not a count-derived offset.

## Row semantics

Each row is inclusive.

- Equal source and target span lengths map positionally.
- A source span with one target verse maps every source verse to that target.
- One source verse with a target span maps that source verse to the complete
  target span.

These rules preserve the many-to-one title folds and the one-to-many Psalm
13:6 boundary in both forward and reverse conversion.

## Verification

Run the repository-only checks:

```text
python tools/traditional/test_german_psalm_reference_map.py
```

To reproduce the rows from a separately downloaded copy of the pinned TVTMS
file and compare them with the checked-in map:

```text
python tools/traditional/test_german_psalm_reference_map.py --upstream PATH_TO_TVTMS
```

The tests reject missing or duplicate source coordinates, ambiguous range
shapes, nonexistent target verses, incomplete target coverage, metadata drift,
and changes to the known Psalm 8, 13, 19, 42, and 51 boundaries.

The JSON is a transformed subset of CC BY 4.0 data. The modifications are
documented above and in the JSON `provenance` object.
