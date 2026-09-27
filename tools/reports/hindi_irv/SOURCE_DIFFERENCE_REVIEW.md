# Hindi IRV source-difference review

This review compares every Hindi app verse with the pinned official eBible
`hin2017` USFM archive. The audit itself is read-only; the one approved
source-backed lexical repair is recorded below.

- Source: <https://ebible.org/Scriptures/hin2017_usfm.zip>
- SHA-256: `4B284BEE6D52D8DAE3425D540E6743836E614694EEFF9C55D2395071A76BF07F`
- License: [Creative Commons Attribution-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/)
- Rights: <https://ebible.org/hin2017/copyright.htm>
- Verse units: 31,104 app and 31,104 source
- Strict exact matches: 28,448
- Strict differences reviewed: 2,656
- Presentation-only differences: 2,656
- Repaired lexical differences: 1
- Unresolved lexical differences: 0

## Decision

The current corpus has 2,656 strict differences
after the one lexical repair, which remains protected by exact old-text and
pinned-source guards. Every current difference is presentation-only:
2,502 contain only reviewed inline
cross-reference apparatus, 137 contain only
source-export whitespace at `\it` presentation-marker boundaries, and
17 contain both.
The comparator removes
only the exact apparatus spans recorded in the JSON. It permits whitespace
equivalence only after current app text, parsed source text, raw USFM, and
the reviewed spans all match byte-for-byte. It never ignores words or
punctuation.

Every app-only apparatus span is bound to exact offsets and a raw-USFM
`\bdit` marker. Source-only apparatus and the lexical repair also carry
verified official eBible HTML evidence in the JSON.

## Jesus-word boundary audit

The boundary pass checked all 31,104 verse units:
28,448 strict-text matches and
2,656 reviewed presentation differences.
The initial pass found 229
presentation-unit speech-mask mismatches; it found
2,427 already correct.
The repair now leaves 0 mismatches.
All 2,519 exact app-only apparatus
spans are excluded from source alignment and forced to narration. The transfer
preserves every non-`[J]` character and fails closed on offset drift, wording
drift, or any other app display tag.

## Applied lexical repair

### revelation 12:18

- Old app text: और अजगर समुद्र के किनारे की रेत पर खड़ा हो गया।
- Repaired text: और वह समुद्र के रेत पर जा खड़ा हुआ।
- Pinned source archive: <https://ebible.org/Scriptures/hin2017_usfm.zip>
- Official HTML: <https://ebible.org/hin2017/REV12.htm>
- Current linked edition: <https://www.bible.com/bible/1980/REV.12.IRVHin>
- Guard: the repair runs only when both the old app text and pinned
  source text match their exact reviewed strings. The already-fixed
  string is an idempotent no-op.

## Unresolved lexical differences

None.

## Counts by book

| Book | Apparatus | Whitespace | Both | Lexical | Total |
|---|---:|---:|---:|---:|---:|
| 1_chronicles | 16 | 0 | 0 | 0 | 16 |
| 1_corinthians | 50 | 3 | 0 | 0 | 53 |
| 1_john | 8 | 1 | 0 | 0 | 9 |
| 1_kings | 26 | 0 | 0 | 0 | 26 |
| 1_peter | 26 | 0 | 0 | 0 | 26 |
| 1_samuel | 22 | 0 | 0 | 0 | 22 |
| 1_thessalonians | 9 | 0 | 0 | 0 | 9 |
| 1_timothy | 14 | 1 | 0 | 0 | 15 |
| 2_chronicles | 22 | 0 | 0 | 0 | 22 |
| 2_corinthians | 26 | 3 | 0 | 0 | 29 |
| 2_john | 1 | 0 | 0 | 0 | 1 |
| 2_kings | 14 | 0 | 0 | 0 | 14 |
| 2_peter | 14 | 0 | 0 | 0 | 14 |
| 2_samuel | 16 | 0 | 0 | 0 | 16 |
| 2_thessalonians | 8 | 0 | 0 | 0 | 8 |
| 2_timothy | 6 | 1 | 0 | 0 | 7 |
| 3_john | 1 | 0 | 0 | 0 | 1 |
| acts | 127 | 1 | 1 | 0 | 129 |
| amos | 10 | 0 | 0 | 0 | 10 |
| colossians | 6 | 1 | 0 | 0 | 7 |
| daniel | 43 | 0 | 0 | 0 | 43 |
| deuteronomy | 109 | 0 | 0 | 0 | 109 |
| ecclesiastes | 5 | 0 | 0 | 0 | 5 |
| ephesians | 25 | 0 | 0 | 0 | 25 |
| esther | 2 | 0 | 0 | 0 | 2 |
| exodus | 30 | 0 | 0 | 0 | 30 |
| ezekiel | 84 | 0 | 0 | 0 | 84 |
| ezra | 5 | 0 | 0 | 0 | 5 |
| galatians | 20 | 1 | 0 | 0 | 21 |
| genesis | 95 | 0 | 0 | 0 | 95 |
| habakkuk | 4 | 0 | 0 | 0 | 4 |
| haggai | 3 | 0 | 0 | 0 | 3 |
| hebrews | 110 | 2 | 0 | 0 | 112 |
| hosea | 16 | 0 | 0 | 0 | 16 |
| isaiah | 241 | 0 | 0 | 0 | 241 |
| james | 20 | 0 | 0 | 0 | 20 |
| jeremiah | 93 | 0 | 0 | 0 | 93 |
| job | 47 | 0 | 0 | 0 | 47 |
| joel | 13 | 0 | 0 | 0 | 13 |
| john | 73 | 29 | 0 | 0 | 102 |
| jonah | 2 | 0 | 0 | 0 | 2 |
| joshua | 15 | 0 | 0 | 0 | 15 |
| jude | 6 | 0 | 0 | 0 | 6 |
| judges | 32 | 0 | 0 | 0 | 32 |
| lamentations | 4 | 0 | 0 | 0 | 4 |
| leviticus | 64 | 0 | 0 | 0 | 64 |
| luke | 128 | 31 | 7 | 0 | 166 |
| malachi | 12 | 0 | 0 | 0 | 12 |
| mark | 52 | 12 | 3 | 0 | 67 |
| matthew | 49 | 44 | 3 | 0 | 96 |
| micah | 8 | 0 | 0 | 0 | 8 |
| nahum | 2 | 0 | 0 | 0 | 2 |
| nehemiah | 4 | 0 | 0 | 0 | 4 |
| numbers | 61 | 0 | 0 | 0 | 61 |
| obadiah | 3 | 0 | 0 | 0 | 3 |
| philippians | 7 | 2 | 0 | 0 | 9 |
| proverbs | 42 | 0 | 0 | 0 | 42 |
| psalms | 245 | 0 | 0 | 0 | 245 |
| revelation | 164 | 4 | 3 | 0 | 171 |
| romans | 86 | 1 | 0 | 0 | 87 |
| ruth | 4 | 0 | 0 | 0 | 4 |
| song_of_songs | 14 | 0 | 0 | 0 | 14 |
| titus | 2 | 0 | 0 | 0 | 2 |
| zechariah | 32 | 0 | 0 | 0 | 32 |
| zephaniah | 4 | 0 | 0 | 0 | 4 |

## Reproduction

```text
python -B tools/hindi_source_difference_review.py --check
python -B -m unittest tools/test_hindi_source_difference_review.py -v
```

Use `--archive PATH` to check a local copy of the pinned archive. `--write-review`
regenerates only this report directory and requires `--verify-html` so the
source-only apparatus and reviewed repair source wording are checked
against the official rendered chapters.

The complete 364-reference adjudication, exact texts, reviewed removal spans,
raw USFM evidence, and HTML verification flags are in
`source_difference_review.json`.
