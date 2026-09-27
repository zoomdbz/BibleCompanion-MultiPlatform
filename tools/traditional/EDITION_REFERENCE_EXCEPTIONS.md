# Edition reference exception provenance

`edition_reference_exceptions.json` contains the non-Psalm reference mappings
between the repository's modern base editions and their traditional overlays.
It does not alter Scripture text. The top-level `maps` array contains one
version-1 map per language and edition pair; each item otherwise has the same
shape as `german_psalm_reference_map.json`.

A book with `complete: false` lists only reviewed ranges; unlisted source
coordinates retain their source edition. Identity mappings also require an
explicit reviewed row. A book with `complete: true` must map every source
coordinate explicitly. Equal-length ranges map positionally.
One source verse may map to a target range, and several source verses may map
to one target verse. The data does not invent `a`/`b` subdivisions.

## Shared primary mapping data

The German and Italian alignments use STEPBible TVTMS (Translators
Versification Traditions with Methodology for Standardisation):

- Repository: <https://github.com/STEPBible/STEPBible-Data>
- Commit: `b99716b0cddb648ddb95cc786a197180f2f97d48`
- Raw file: <https://raw.githubusercontent.com/STEPBible/STEPBible-Data/b99716b0cddb648ddb95cc786a197180f2f97d48/Versification/TVTMS%20-%20Translators%20Versification%20Traditions%20with%20Methodology%20for%20Standardisation%20for%20Eng%2BHeb%2BLat%2BGrk%2BOthers%20-%20STEPBible.org%20CC%20BY.txt>
- SHA-256: `63058e0f20201af4bdaa7d830da5be8f493455d947c5f147d84840b33db9ddf8`
- License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- Attribution: Data created by STEPBible.org based on work at Tyndale House
  Cambridge.

The German and Russian checks also use CrossWire JSword's primary
versification tables at commit
`a2c51f3c3a55416f3c991a67ef3d635c7ee55330`:

- [Luther.properties](https://raw.githubusercontent.com/crosswire/jsword/a2c51f3c3a55416f3c991a67ef3d635c7ee55330/src/main/resources/org/crosswire/jsword/versification/Luther.properties),
  SHA-256 `5a07e22efdf46bc1dfa4a1ff644cc6d11d5b7799d5cd9617196f38388251e1c3`
- [Synodal.properties](https://raw.githubusercontent.com/crosswire/jsword/a2c51f3c3a55416f3c991a67ef3d635c7ee55330/src/main/resources/org/crosswire/jsword/versification/Synodal.properties),
  SHA-256 `76385bbd47834482c651e509e898d704cf27b5d4037ecff26ec7baf45f1edba6`
- License: [LGPL 2.1 or later](https://github.com/crosswire/jsword/blob/a2c51f3c3a55416f3c991a67ef3d635c7ee55330/LICENSE)

## German: SCH2000 to Luther 1912, Isaiah

The checked-in SCH2000 text and pinned eBible Luther text establish these
actual content boundaries:

- SCH2000 8:23 maps to Luther target 9:1. The raw target marks it `[8:23]`.
- SCH2000 9:1-20 maps positionally to target 9:2-21.
- SCH2000 64:1 contains the content split across target 64:1-2.
- SCH2000 64:2-11 maps positionally to target 64:3-12.

TVTMS records English KJV 9:1 as Hebrew 8:23 and English 9:2-21 as Hebrew
9:1-20. Its Isaiah 63:19-64:12 block records the constituent KJV boundary.
CrossWire independently records `Isa.8.23-Isa.9.20=Isa.9.1-Isa.9.21`,
`Isa.63.19=Isa.63.19-Isa.64.1`, and
`Isa.64.1-Isa.64.11=Isa.64.2-Isa.64.12`. Direct comparison of the actual
SCH2000 grouping, rather than chapter counts, determines the two source 64
rows. Unlisted passages stay in their source edition.

Target source:

- Edition details: <https://ebible.org/details.php?id=deu1912>
- USFM archive: <https://ebible.org/Scriptures/deu1912_usfm.zip>
- Archive SHA-256: `650a8192134a8f0057286c469754edcfaee4fbb18800621aee4563f3055bb39b`
- Book: `24-ISAdeu1912.usfm`, SHA-256
  `daca8923266d6b8c4a62576e4396fdde1ced00fb10fa8045d96f26ff6d88f6c1`
- License: Public Domain

## Italian: NR06 to Diodati 1885, Job

TVTMS aligns Hebrew-numbered Job to the Spanish RV1909 column as follows:

- 38:39-41 to 39:1-3
- 39:1-30 to 39:4-33
- 40:1-5 to 39:34-38
- 40:6-24 to 40:1-19
- 40:25-32 to 41:1-8
- 41:1 to 41:9
- 41:2-26 to 41:10-34

The table does not identify Diodati. The mapping therefore does not assume
that Diodati follows Spanish versification. A verse-by-verse comparison of the
actual NR06 base text with the pinned Diodati Job USFM confirms that Diodati
uses those same boundaries and preserves the content sequence across every
listed range. The map remains partial because only the non-identity range is
listed. Unlisted passages stay in their source edition.

Target source:

- Edition details: <https://ebible.org/details.php?id=ita1885>
- USFM archive: <https://ebible.org/Scriptures/ita1885_usfm.zip>
- Archive SHA-256: `459884735df5d5ae7f3381980bc5ea846f0034cea305fa77b5d72e09727e051c`
- Book: `19-JOBita1885.usfm`, SHA-256
  `3b0a9d08a8758c9bc69b720ee22eca8ae4a790468acf7cb5ce8d44ff5648886d`
- License: Public Domain

## Russian: NRT/NRP to Synodal 1876, Romans

CrossWire's Synodal table contains
`Rom.14.24-Rom.14.26=Rom.16.25-Rom.16.27!a`. Its left side is Synodal and its
right side is English KJV. The app converts in the opposite direction, so
modern Romans 16:25-27 maps positionally to Synodal 14:24-26. Direct comparison
of both checked-in texts and the raw Synodal USFM confirms the doxology.

Romans is `complete: true`. Chapters 1-13, 14:1-23, chapter 15, and 16:1-24
have explicit identity rows; 16:25-27 has the explicit moved row. This covers
all 433 coordinates in both editions.

Target source:

- Edition details: <https://ebible.org/details.php?id=russyn>
- USFM archive: <https://ebible.org/Scriptures/russyn_usfm.zip>
- Archive SHA-256: `acd5d80c0d28ca72d17cb12a2bb9f561d957439b03bddfaa433beb51cf7b0363`
- Book: `75-ROMrussyn.usfm`, SHA-256
  `fea367b62c049a0ef784f1e708d3f2e3c8463c09f34b52bc21c7d24a14855872`
- License: Public Domain

## Traditional Chinese: RCUV to CUV, John

The modern RCUV base gives the return-home clause at 7:53 and the Mount of
Olives clause at 8:1. The pinned traditional CUV source has no 7:53; its 8:1
contains both clauses. The map therefore has two source rows targeting CUV
8:1. Reverse conversion of CUV 8:1 is intentionally ambiguous and must fail
closed instead of choosing half of the merged verse.

The source comparison also found a target-only coordinate outside the requested
merge: CUV has John 5:4, while RCUV omits it. The schema cannot express a verse
with no source coordinate. `unresolvedTargetOnlyCoordinates` records `John
5:4`; reverse navigation must validate the resulting base coordinate and remain
in the source edition when RCUV 5:4 does not exist. No phantom verse or adjacent
verse mapping was added.

Target source:

- Edition details: <https://ebible.org/details.php?id=cmn-cu89t>
- USFM archive: <https://ebible.org/Scriptures/cmn-cu89t_usfm.zip>
- Archive SHA-256: `01e919ec0f2ea9e22adaa5097340fc4ad18a7976a32f850b2e6434d7884f3e81`
- Book: `73-JHNcmn-cu89t.usfm`, SHA-256
  `d692d0509087472f18a46d4e3ce7d1000eb85aa2ca1978edf947cffecba8057b`
- License: Public Domain

## Verification

Repository-only checks:

```text
python tools/traditional/test_edition_reference_exceptions.py
```

Full reproduction against separately downloaded pinned inputs:

```text
python tools/traditional/test_edition_reference_exceptions.py \
  --tvtms PATH_TO_TVTMS \
  --crosswire-luther PATH_TO_LUTHER_PROPERTIES \
  --crosswire-synodal PATH_TO_SYNODAL_PROPERTIES \
  --de-source PATH_TO_DEU1912_USFM_ZIP \
  --it-source PATH_TO_ITA1885_USFM_ZIP \
  --ru-source PATH_TO_RUSSYN_USFM_ZIP \
  --zh-source PATH_TO_CMN_CU89T_USFM_ZIP
```

The tests reject schema drift, wrong edition IDs, duplicate or nonexistent
source coordinates, unsupported range shapes, missing target coordinates,
unreported target-only coordinates, changed exception rows, mapping-table hash
drift, archive hash drift, and changes to the text at each boundary.
