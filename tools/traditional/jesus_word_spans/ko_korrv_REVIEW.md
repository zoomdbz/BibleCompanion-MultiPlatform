# KRV 1961 Jesus-word span review

This ledger uses the pinned English KJV1769 WJ overlay for speaker
attribution, not Korean punctuation or a conjectured Korean red-letter
edition. The exact Korean Revised Version 1961 (KRV) text comes from the
current `ko/korrv` overlay. Bible.com, BiblePortal, WordProject, CrossWire,
and ph4 KRV witnesses corroborate wording but do not provide usable digital
word-level red-letter boundaries. The Korean Bible Society publishes physical
red-letter KRV editions; no machine-readable span data was available for this
review.

The KJV source archive SHA-256 is
`1BAB5D4D030439831FC0B39D7F11001DD8527FC6277405E7F6512273C200C3A4`.
The pinned primary KRV source-file SHA-256 is
`65C6D99F80A5A47B9C0D144F313AC533F482454F336D3A82FC1DCEDB020612E2`.
Each ledger row also pins its exact KRV native-unit text and matching KJV
WJ verse text by SHA-256.

The inventory has 626 KJV mixed semantic candidates and 627 ledger rows.
Korean reporting formulas give exact boundaries in 552 straightforward units.
Another 74 authority units have explicit, source-exact boundaries, relocation,
or reviewed omissions in
`build_ko_jesus_ledger.py`; these cover multiple speakers, nested
quotation, remembered Jesus words, original-language utterances versus
translator glosses, narration after speech, and Revelation's recast titles.
No a/b splits or invented target wording occur.

Four KJV WJ mixed units have no target direct-speech words to mark:

- Mark 9:31: KRV recasts the saying as indirect narration.
- Luke 9:55-56: KRV omits both KJV saying clauses.
- Luke 20:23: KRV retains only the speech introducer.

Matthew 20:32 moves Jesus' question into KRV 20:33. The authority row
pins that destination's raw hash and exact speech, while a supplemental
20:33 row colors only the question before the blind men's answer.
KRV Luke 20:24 has the separate coin request, matching KJV 20:24;
it is not a relocation of Luke 20:23's omitted question.

KRV Luke 8:45 contains Jesus' first question but omits Peter's later
verbatim repetition, so the row marks only the first question. Acts 20:35
marks only Paul's quotation of Jesus' maxim, not Paul's surrounding speech.
Revelation 1:8 marks only `나는 알파와 오메가라`; the remaining KRV title
clause lies outside the pinned KJV WJ boundary.

Validation:

```powershell
python -X utf8 tools/traditional/build_ko_jesus_ledger.py
python -m unittest discover -s tools/traditional -p 'test_build_ko_jesus_ledger.py' -v
```

The builder applies every proposed span in memory through
`ReviewedJesusSpans`, verifies all 626 candidate keys and the one
supplemental target, and rejects source
drift, absent or repeated substrings, overlap, and changed DN/ADD/Scripture
bytes.
