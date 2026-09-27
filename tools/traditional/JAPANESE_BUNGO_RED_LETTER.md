# Japanese Bungo Jesus-word review

`jesus_word_spans/ja_bungo.json` binds word-level color spans to exact native
Bungo verse text. It does not change Scripture wording. The pinned KJV1769
CrossWire `wj` source decides which verses belong to Jesus' speech; the
[Bungo native red-letter site](https://bungo.iinaa.net/b40.html) supplies
Japanese start and stop boundaries. The 27 source pages are b40.html through
b66.html. Their SHA-256 values and every source/target difference are recorded
in `tools/reports/ja_bungo_red_letter_evidence.json`.

The native site has 2,060 raw red-letter coordinates. Five lie only in
variant-reading footnotes; after removing footnotes and page labels, 2,055
remain. The pinned KJV authority has 1,402 full-speech and 626 mixed-speech
coordinates. The cleaned site agrees on 1,396 full and 623 mixed coordinates.
Its 36 additional editorial red coordinates are not imported. Eight KJV-full
verses are Bungo `[なし]` omissions; eight `overrideInherited` rows prevent those
omission markers from being colored. In Matthew 18:11 and 23:14 the site colors
the omission marker itself, but that marker contains no spoken words. Three KJV-mixed units have no speech in
the target: Luke 9:55, 9:56, and 20:23. The ledger records explicit
`noTargetSpeech` reviews for them.

Of the 626 mixed target units, 304 match the native source text exactly after
removing ruby readings and variation selectors and applying Unicode NFKC.
Another 309 have orthographic, spacing, or punctuation differences but speech
boundaries anchor in equal text on both sides. Ten edits touch a speech
boundary; each has an explicit quote/speaker review. The evidence report lists
all 319 differences, not just a sample. The ledger hashes each uncolored
target verse and checks repeated occurrences, source `[DN]` and `[ADD]` tags,
and unchanged visible text through `ReviewedJesusSpans`.

To reproduce against the source pages:

```text
python tools/traditional/build_ja_bungo_jesus_ledger.py --fetch
python tools/traditional/build_ja_bungo_jesus_ledger.py --write
python -m unittest discover -s tools/traditional -p test_build_ja_bungo_jesus_ledger.py
```

The HTML cache stays under ignored `.scripture-structure-cache/bungo-red-letter`.
The checked-in report preserves the exact page hashes for offline review.
The shared importer must opt `ja/bungo` into reviewed speech annotations before
the ledger affects packaged app verses; this builder does not edit the importer.
