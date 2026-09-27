# Asia edition-reference map evidence boundary

The supported edition conversion rows for Japanese, Korean, Simplified Chinese,
and Traditional Chinese are deliberately incomplete. This document records why;
it is not permission to infer mappings from matching numbers.

| Pair | Base units / coordinates | Traditional units / coordinates | Equal-inventory books | Explicit rows |
| --- | ---: | ---: | ---: | ---: |
| JCB to Bungo | 28,201 / 28,398 | 31,099 / 31,102 | 5 | 0 |
| RNKSV to KRV | 31,087 / 31,088 | 31,102 / 31,102 | 56 | 0 |
| CCB to simplified CUV | 30,974 / 31,104 | 31,032 / 31,103 | 27 | 1,407 across all 66 books |
| RCUV to traditional CUV | 31,082 / 31,088 | 31,032 / 31,103 | 36 | 2, John only |

The base corpus files are app Scripture units with native anchors. They are not
pinned, complete publisher exports for JCB, RNKSV, CCB, or RCUV. The local
traditional overlays are pinned, but no local authoritative table maps any of
these modern edition pairs across the full canon. Equal coordinate inventory
does not prove that a source unit and target unit contain the same passage.

Simplified Chinese has 1,407 explicit rows across all 66 canonical books. The
rows come only from native CCB marker spans that exactly match a checked-in app
unit and occupy the same complete coordinate span in pinned CUV. The ignored
CCB cache has 1,189 canonical chapter documents, publisher/version metadata,
canonical URLs, page hashes, and a deterministic aggregate pin. It is a
BibleGateway CCB response, not proof that the current Bible.com CCB wording is
identical. Forty cache native spans lack an identical local unit, 26 chapters
have grouping mismatches, and one local coordinate is extra; those residuals
stay unmapped except for the pre-existing reviewed John merge.

The offline coverage audit resolves 30,928 of 30,974 CCB source units and
30,979 of 31,032 reverse CUV units. It retains the other 46 and 53 units,
respectively. The source map is intentionally `complete: false` for every
book, including books whose coordinate inventory happens to match.

The independently reviewed Chinese John rows remain: Simplified CCB John 7:53
and 8:1 both point to CUV John 8:1; Traditional RCUV has the same 7:53/8:1
merge. That target unit contains both clauses. Reverse conversion remains
ambiguous and must retain the source edition. Traditional CUV John 5:4 has no
RCUV base coordinate and remains unresolved.

The pinned traditional source URLs and hashes, exact totals, and missing
evidence requirements are machine-readable in `asia_reference_map_limits.json`.
Before adding an identity row, supply either a hash-pinned complete modern
edition corpus plus direct boundary comparison, or an authoritative,
version-specific versification table. Preserve native ranges; do not introduce
verse `a`/`b` fragments.

Run the guard:

```powershell
python -X utf8 -B -m unittest tools/traditional/test_asia_reference_map_limits.py -v
```
