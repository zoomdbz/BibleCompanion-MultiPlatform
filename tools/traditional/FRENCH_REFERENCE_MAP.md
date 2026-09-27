# French NBS to LSG 1910 reference-map provenance

`french_reference_map.json` contains the reviewed non-identity reference
boundaries between the repository's French Nouvelle Bible Segond (NBS) base
and its eBible Louis Segond 1910 (LSG 1910) overlay. It does not alter Scripture
text.

The map is intentionally partial. Every listed row has direct source-text
evidence. Unlisted coordinates fail closed at runtime; an equal verse number is
not treated as proof that two editions divide the text identically.

## Sources

The primary versification authority is STEPBible TVTMS (Translators
Versification Traditions with Methodology for Standardisation):

- Repository: <https://github.com/STEPBible/STEPBible-Data>
- Pinned commit: `b99716b0cddb648ddb95cc786a197180f2f97d48`
- Pinned raw file: <https://raw.githubusercontent.com/STEPBible/STEPBible-Data/b99716b0cddb648ddb95cc786a197180f2f97d48/Versification/TVTMS%20-%20Translators%20Versification%20Traditions%20with%20Methodology%20for%20Standardisation%20for%20Eng%2BHeb%2BLat%2BGrk%2BOthers%20-%20STEPBible.org%20CC%20BY.txt>
- SHA-256: `63058e0f20201af4bdaa7d830da5be8f493455d947c5f147d84840b33db9ddf8`
- License: [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)
- Attribution: Data created by STEPBible.org based on work at Tyndale House
  Cambridge.

The independent standardized-table check is CrossWire JSword's Segond to
English KJV map:

- Repository: <https://github.com/crosswire/jsword>
- Pinned commit: `a2c51f3c3a55416f3c991a67ef3d635c7ee55330`
- Pinned raw file: <https://raw.githubusercontent.com/crosswire/jsword/a2c51f3c3a55416f3c991a67ef3d635c7ee55330/src/main/resources/org/crosswire/jsword/versification/Segond.properties>
- SHA-256: `47dfe8f7d6a43fbe0d8f16af972db1e420907e540ab32d09a16313719a489b6f`
- License: [LGPL-2.1-or-later](https://github.com/crosswire/jsword/blob/a2c51f3c3a55416f3c991a67ef3d635c7ee55330/LICENSE)

The target edition source is the public-domain eBible archive:

- Details: <https://ebible.org/details.php?id=fraLSG>
- Archive: <https://ebible.org/Scriptures/fraLSG_usfm.zip>
- SHA-256: `3a0615e992ffd412b1afcaed50d146bba5ec8ae2378f04ca71459a4cd2d7cc33`
- License: Public Domain

## Method

The audit compared all 39 Old Testament and 27 New Testament books in the
checked-in NBS base with all 66 source-derived LSG 1910 overlays. A monotonic
text alignment identified candidate 1:1, 1:2, and 2:1 boundaries. Each emitted
row was then checked against the actual French wording and against existing
source and target coordinates. Chapter and verse counts served only as a
completeness check; they did not generate offsets.

TVTMS directly identifies the French NEG Job 38:39 through 41:34 shifts.
CrossWire independently confirms the LSG Job, Ecclesiastes, and Mark boundary
families. The actual NBS and LSG texts establish the remaining rows. Job
34:36-37 is one important edition-specific merge absent from both standardized
tables.

CrossWire maps Segond to English KJV, not NBS to LSG. Its rows therefore cannot
be copied wholesale. For example, CrossWire records KJV-relative differences
in Exodus 7-8, Isaiah, Ezekiel, and several minor prophets, while the actual NBS
base and eBible LSG source use the same French coordinates in those passages.
Adding those KJV-relative offsets here would corrupt valid French references.

## Proven non-identity boundaries

The compact JSON rows cover these boundaries:

- Genesis 32 begins at LSG Genesis 31:55.
- Exodus 21:37 begins LSG Exodus 22; the rest of NBS Exodus 22 is shifted by
  one verse.
- Numbers 17 is divided between LSG Numbers 16:36-50 and 17:1-13. NBS Numbers
  25:19 and 26:1 jointly form LSG Numbers 26:1.
- Deuteronomy 13, 23, and 29 begin one verse earlier in LSG; NBS 28:69 becomes
  LSG 29:1.
- 1 Samuel 21 begins at LSG 20:43.
- 2 Samuel 19 begins at LSG 18:33.
- 1 Kings 5:1-14 closes LSG chapter 4; the remainder becomes LSG 5:1-18.
- 2 Kings 12 begins at LSG 11:21.
- 1 Chronicles 5:27-41 becomes LSG 6:1-15; NBS chapter 6 follows at LSG 6:16.
  NBS 12:4-5 is one LSG verse.
- 2 Chronicles 1:18 becomes LSG 2:1.
- Nehemiah 3:33-38 opens LSG chapter 4; NBS chapter 4 continues at LSG 4:7.
  NBS 7:68 is split across LSG 7:68-69. NBS 10:1 becomes LSG 9:38.
- Job 34:36-37 is one LSG verse. NBS 38:39 through 41:26 follows the reviewed
  LSG 39:1 through 41:25 sequence, including the chapter-boundary shifts.
- NBS Psalm 13 omits the numbered LSG title at 13:1. NBS 13:1-4 becomes LSG
  13:2-5, while NBS 13:5-6 is combined at LSG 13:6.
- Ecclesiastes 11:9-10 becomes LSG 12:1-2; NBS 12:1-14 follows at LSG 12:3-16.
- Jeremiah 8:23 becomes LSG 9:1.
- Daniel 3:31-33 becomes LSG 4:1-3; NBS Daniel 4 follows at LSG 4:4. NBS 6:1
  becomes LSG 5:31.
- Hosea 14:1 becomes LSG 13:16.
- Zechariah 2:1-4 becomes LSG 1:18-21.
- NBS Mark 9:50 is split at LSG 9:50-51; NBS Mark 10:52 is split at LSG
  10:52-53.

Isaiah and Ezekiel require no NBS-to-LSG exception. The minor prophets require
no exception beyond Hosea and Zechariah. Joel and Malachi already use the
French-native chapter layout produced by the importer. The actual French text
audit also found no non-identity boundary in the other unlisted books.

## Target-only holes

In the audited app base, LSG contains these numbered coordinates with no
corresponding NBS app coordinate. They remain unmapped instead of being
attached to a neighboring verse:

- Psalms 13:1
- John 5:4

The Numbers 25:19 plus 26:1 merge crosses an NBS chapter boundary. Forward
mapping either source fragment to the complete LSG 26:1 unit is safe. Reverse
mapping cannot express the two NBS chapters as one contiguous reference, so it
must fail closed.

## Verification

Repository-only checks:

```text
python -B tools/traditional/test_french_reference_map.py
```

Pinned-source reproduction:

```text
python -B tools/traditional/test_french_reference_map.py --tvtms PATH_TO_TVTMS --crosswire PATH_TO_SEGOND_PROPERTIES --lsg-archive PATH_TO_FRA_LSG_ZIP
```

The tests reject missing coordinates, duplicate source coverage, unsupported
range shapes, weak text correspondence, undocumented target-only coordinates,
archive drift, and source-derived overlay inventories that diverge from the
pinned eBible archive.
