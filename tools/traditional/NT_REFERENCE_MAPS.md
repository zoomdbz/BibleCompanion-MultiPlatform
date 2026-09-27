# Native New Testament passage boundaries

`nt_reference_maps.json` records eight reviewed book maps in three edition pairs.
The packaged files identify the source and target editions. These maps change
navigation, not Scripture wording, numbering, headings, or notes.

| Pair | Reviewed difference |
|---|---|
| English BSB to KJV | The app's combined 3 John 1:14-15 maps to KJV 1:14. The shoreline clause in the app's combined Revelation 12:17-18 belongs to KJV 13:1. |
| German SCH2000 to Luther 1912 | 2 Corinthians 13:12 contains both greetings, which Luther splits into 12 and 13; the blessing moves from 13 to 14. 3 John 14-15 maps to 14. Revelation 12:18 is part of 13:1. |
| Spanish NVI to RV1909 | 2 Corinthians 13:12-13 maps to 12 and the blessing at 14 maps to 13. 3 John 14-15 maps to 14. Acts 19:40-41 maps to 40. |

The public-domain target archives and exact SHA-256 pins appear in the JSON
and edition manifests. Direct comparison of the checked-in source verses and
those pinned target texts establishes the clause boundaries. The supplied BSB
USFM and [Bible.com BSB 3 John](https://www.bible.com/bible/3034/3JN.1.BSB)
print the entire closing as verse 14 and document the alternate verse 15;
the app retains its existing whole-unit 14-15 alias. The
[SCH2000 chapter](https://www.bible.com/bible/157/2CO.13.SCH2000)
independently confirms the German greetings/blessing boundary.

Revelation's shoreline sentence has a textual variant as well as a numbering
difference: BSB names the dragon, while KJV says "I". The map never harmonizes
those words. It identifies the corresponding passage.

## Safety contract

The resolver accepts only explicit reviewed mappings, including identity
rows. Equal verse counts never authorize conversion. Before mapping, it expands
an anchor to its whole native source unit. If that unit would require a
disjoint or cross-chapter target highlight, navigation retains the source
edition. The reader shows the actual edition under its book title.

Consequently, BSB Revelation 12:17-18 cannot silently lose its final clause by
opening KJV 12:17 alone. The reverse CUV John 8:1 merge cannot choose only one
of the two modern chapters. Neither case invents an a/b verse subdivision.
Simplified Chinese CCB-to-CUV coverage now lives in `asia_reference_maps.json`;
it has a separate cached publisher-marker evidence set for the canonical books.

`reference_maps.py` packages the reviewed input for both the traditional and
English KJV importers. Both validators check the packaged map and provenance.

```powershell
python -X utf8 -B -m unittest discover -s tools/traditional -p 'test_nt_reference_maps.py' -v
```

These checks prove the listed map coordinates and reviewed clause boundaries.
They do not establish corpus-wide publisher text parity or complete crosswalk
coverage for every edition. Missing mappings deliberately retain the actual
source edition, with its visible label.
