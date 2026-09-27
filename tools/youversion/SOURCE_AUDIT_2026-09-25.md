# Scripture source lineage and exactness audit

Date: 2026-09-25 through 2026-09-26

Scope: the current working tree on top of `a8049020`, including the source-proven repairs described below. The audit tools are read-only by default; repair utilities are narrow, reproducible, and idempotent.

## Final verdict

The canonical Old and New Testament audit is complete.

- English BSB covers all 66 books, 1,189 chapters, and 31,086 native verse units. It has zero unresolved differences against the supplied BSB USFM after exact review of export artifacts, native ranges, live Bible.com readings, and red-letter markup. All 3,117 headings match their source anchors.
- Eleven non-English modern editions match their rendered Bible.com editions in all 1,189 canonical chapters. The audit checks native units, wording, source-supported semantic spans, and heading anchors without splitting combined verses.
- Arabic SAB matches in 1,188 of 1,189 chapters. Bible.com renders Luke 7:15 as an empty native verse. The app's complete verse matches the publisher's independent Luke PDF; the audit pins its exact text and the defective Bible.com page separately. It makes no Bible.com parity claim for that chapter.
- The rendered-browser evidence covers 14,268 non-English chapters. Repairs were replayed against the same rendered source, and every repair tool finishes with a zero-edit dry run.
- The English KJV overlay matches the pinned eBible KJV-with-Apocrypha source across all 80 source-backed books. Four deuterocanonical books remain explicit base-edition fallbacks.
- Twelve traditional editions have source-backed canonical Old and New Testaments. Hindi remains modern-only because no complete redistributable traditional Hindi source passed source and rights review.
- The deuterocanon remains a documented hybrid outside the source-backed KJV and Russian Synodal books. It is complete in the app, but it is not one publisher edition in every language.
- English 1 Enoch, 2 Enoch, and Jubilees derive from old public-domain translations that were mechanically modernized. Their foreign-language files are custom translations of the app text, not named modern publisher editions.

## Canonical Old and New Testament source matrix

| App language | Intended source lineage | Modern? | Word-for-word source verdict |
| --- | --- | --- | --- |
| en | [Berean Standard Bible, Bible.com 3034](https://www.bible.com/versions/3034-bsb-berean-standard-bible) | Yes | Source-adjudicated against the supplied BSB USFM: complete coverage, zero unresolved text differences, and zero red-letter differences on exact text. Raw source-export artifacts are documented below. |
| de | [Schlachter 2000, Bible.com 157](https://www.bible.com/de/versions/157) | Modern revision with traditional/formal style | Rendered source audit: 1,189 of 1,189 chapters match. |
| es | [Nueva Version Internacional, Bible.com 128](https://www.bible.com/es/versions/128) | Yes | Rendered source audit: 1,189 of 1,189 chapters match. Omitted readings remain exact, labelled manuscript notes and never become invented body verses. |
| fr | [Nouvelle Bible Segond, Bible.com 104](https://www.bible.com/fr/versions/104) | Yes | Rendered source audit: 1,189 of 1,189 chapters match. Omitted readings remain exact, labelled manuscript notes. |
| it | [Nuova Riveduta 2006, Bible.com 122](https://www.bible.com/it/versions/122) | Modern revision with traditional/formal style | Rendered source audit: 1,189 of 1,189 chapters match. |
| pt | [Nova Versao Transformadora, Bible.com 1930](https://www.bible.com/versions/1930) | Yes | Rendered source audit: 1,189 of 1,189 chapters match. BibleGateway carries a different platform revision and is not the correction authority. |
| ru | [New Russian Translation, Bible.com 143](https://www.bible.com/ru/versions/143) | Yes | Rendered source audit: 1,189 of 1,189 chapters match, including native numbering. |
| ja | [Japanese Contemporary Bible, Bible.com 83](https://www.bible.com/ja/versions/83) | Yes | Rendered source audit: 1,189 of 1,189 chapters match. Native combined ranges remain intact; four exact source-absent red-letter exceptions are hash-pinned. |
| ko | [Revised New Korean Standard Version, Bible.com 142](https://www.bible.com/ko/versions/142) | Yes, 2001 revision | Rendered source audit: 1,189 of 1,189 chapters match, including native ranges and publisher notes. |
| zh-Hans | [Chinese Contemporary Bible, Bible.com 36](https://www.bible.com/versions/36) | Yes | Rendered source audit: 1,189 of 1,189 chapters match. The Isaiah 38 page-order defect is normalized only in the audit after exact inventory checks. |
| zh-Hant | [Revised Chinese Union Version, Bible.com 139](https://www.bible.com/zh-TW/versions/139) | Yes | Rendered source audit: 1,189 of 1,189 chapters match, including native 2 Corinthians 13 numbering. |
| ar | [Sharif Arabic Bible, Bible.com 153](https://www.bible.com/versions/153-sab-sharif-arabic-bible) | Yes | Rendered source audit: 1,188 of 1,189 chapters match. Luke 7 is the sole locked source-rendering defect; the app keeps the complete local verse 7:15. |
| hi | [Indian Revised Version Hindi 2019, Bible.com 1980](https://www.bible.com/versions/1980-irvhin-irv-2019) | Yes | Source-adjudicated against pinned official eBible USFM: 31,104 units, zero missing units, zero unresolved wording differences, and zero speech-boundary mismatches. The 2,656 remaining strict differences have exact apparatus/spacing evidence, not broad normalization exceptions. |

"Modern" describes the intended publisher edition. The verdict column states the evidence actually completed for that edition.

## Rendered-browser comparison evidence

The audit loads each linked Bible.com chapter in Chromium and waits for the native rendered Scripture DOM. It records chapter identity, native unit order, text, headings, and source-supported semantic spans. It rejects redirects, wrong editions, wrong chapters, duplicate units, missing inventories, empty source ranges, and unexpected page order.

The final non-English run covers 12 languages and 14,268 canonical chapters. Eleven languages pass 1,189 of 1,189 chapters. Arabic passes 1,188 chapters and records Luke 7 as the single locked source defect. The repair tools replay changed chapters against fresh rendered pages; second dry runs propose no further edits.

## Historical Git-import comparison evidence

The Git source-import baseline audit strips app tags and compares current plain verse text with the last commit identified as the source import. This is provenance evidence, not independent publisher proof. The final 2026-09-26 run produced:

| Language | Import lines | Current lines | Exact, same reference | Same text, moved reference | Import text with no exact current match |
| --- | ---: | ---: | ---: | ---: | ---: |
| en | 31,087 | 31,086 | 30,904 | 1 | 182 |
| de | 30,977 | 31,171 | 30,853 | 24 | 100 |
| es | 31,087 | 31,087 | 31,085 | 0 | 2 |
| fr | 30,975 | 31,154 | 30,863 | 24 | 88 |
| it | 31,075 | 31,104 | 29,728 | 1,227 | 120 |
| ru | 30,324 | 31,163 | 30,276 | 30 | 18 |
| pt | 31,103 | 31,104 | 31,103 | 0 | 0 |
| ja | 30,763 | 28,201 | 27,735 | 168 | 2,860 |
| ko | 31,103 | 31,087 | 31,068 | 0 | 35 |
| zh-Hans | 30,960 | 30,974 | 30,826 | 120 | 14 |
| zh-Hant | 31,087 | 31,082 | 31,063 | 6 | 18 |
| ar | 31,103 | 31,104 | 31,101 | 0 | 2 |
| hi | 31,103 | 31,104 | 31,082 | 0 | 21 |

Some remaining lines moved to different native verse references. Editions that publish traditional readings keep them in body text; editions that omit them keep exact readings in manuscript notes, which this table does not count. These figures prove post-import change, not that every changed line is wrong.

The Japanese Git snapshot already contained the earlier JA1955/ERV insertions. Its 2,860 unmatched snapshot lines therefore include the 2,707 known removals and cannot be read as missing JCB verses.

The Hindi snapshot comparison counts 21 Psalm 119 acrostic labels moved from verse endings into their proper headings. It also records the later extra Revelation 12:18 unit, whose wording now matches the independent publisher source. Snapshot differences do not override the complete official-source audit below.

Independent reference checks:

- English BSB versus the supplied USFM: 31,086 app units and 31,086 source units; 31,022 are strict raw matches. All 64 raw differences are adjudicated: 59 literal broken footnote-export tokens, three stray `vvv` tokens, and two live Bible.com/source-export differences. There are zero missing units, zero unresolved text differences, and zero red-letter differences on exact text. Native 3 John and Revelation ranges are handled without splitting verses.
- Hindi IRVHin follow-up on 2026-09-26: 31,104 verse units on both sides, 28,448 strict exact, 2,656 reviewed presentation-only differences, no missing units, and zero unresolved wording differences. One lexical correction at Revelation 12:18 now matches official USFM, eBible HTML, and Bible.com. The review pins exact evidence for 2,502 cross-reference-apparatus units, 137 source-export-whitespace units, and 17 units containing both. Repairs corrected 229 reviewed presentation speech-boundary mismatches; all 31,104 units now pass. Psalm 119 acrostic labels occupy their source heading positions. See the [complete evidence review](../reports/hindi_irv/SOURCE_DIFFERENCE_REVIEW.md).
- The base-heading integrity audit now parses 1,352 JSON files, 1,287 books, 65 indexes, 21,333 stories, 525,587 bullets, 521,793 native units, and 32,067 headings. Every heading is nonblank, sorted, unique within its chapter, and anchored to a native verse-unit start; no native units overlap. A separate JCB ledger pins 17 native-unit repairs and 10 heading moves across 12 chapter records to the app's Bible.com 83/JCB authority. Mark 11:26-28 was already a combined unit and is recorded only as a heading move. This is structural evidence, not a claim of complete licensed JCB wording parity.
- The same audit exposed 47 older trailing-reference metadata defects. Forty-five translated Jubilees bullets retained their wording but had lost only the final address suffix; each restored coordinate matches the unchanged English control at the same one-verse bullet. Korean 2 Esdras 7:28-29 already contained their correct markers inside `[J]`; those markers now sit after `[/J]` so address parsing and Jesus-word coloring do not consume metadata. An independent before/after comparison found exactly 47 changed string leaves, no non-string changes, and unchanged `[J]`, `[DN]`, and `[ADD]` token inventories.
- Portuguese NVT: 214 strict verse-range mismatches in 93 chapters.
- Italian NR2006: 1,652 strict verse-range mismatches in 718 chapters.
- German SCH2000: 6,284 strict verse-range mismatches in 903 chapters; many are case/divine-name or punctuation presentation differences. BibleGateway fails to expose Hosea 14:1, so that range is recorded as unavailable rather than missing.
- Spanish NVI: 11,192 strict range mismatches in 1,136 chapters against BibleGateway. Genesis 1:7 matches Bible.com 128 while BibleGateway supplies different wording, so these differences cannot be treated as app errors against the app's Bible.com link authority.
- Russian NRT: 13,784 strict range mismatches in 1,181 chapters; this count is heavily inflated by punctuation, dash, case, native numbering, and divine-name presentation. The confirmed Psalm 89:16 lexical divergence is repaired.
- Simplified Chinese CCB: BibleGateway and Bible.com expose different same-named CCB texts and verse inventories. The BibleGateway mismatch count cannot be used as a correction source for Bible.com 36.

## Traditional New Testament readings

The repair covers Matthew 17:21, 18:11, and 23:14; Mark 7:16, 9:44, 9:46, 11:26, and 15:28; Luke 17:36 and 23:17; John 5:4; Acts 8:37, 15:34, 24:7, and 28:29; and Romans 16:24.

| Languages | Target addresses covered by published body | Exact publisher or older-edition notes |
| --- | ---: | ---: |
| ar, de, hi, it, pt, ru, zh-Hans | 16 | 0 |
| es, fr | 0 | 16 |
| ja | 16 | 2 |
| ko | 2 | 15 |
| zh-Hant | 0 | 16 |

- Spanish and French keep clean NVI/NBS body text and exact RVR1960/LSG notes.
- Japanese preserves JCB native ranges; only Mark 11:26 and Acts 15:34 have exact JA1955 notes.
- Korean keeps RNKSV Mark 7:16 and bracketed John 5:3-4 in body text. Acts 24:6-8 remains one unsplit publisher note.
- Traditional Chinese keeps all 16 out of RCUV body text. John 5:3-4 and Acts 24:6-8 remain unsplit publisher notes.
- Simplified Chinese preserves CCB Mark 9:43-44 and 9:45-46 as native ranges. No language uses artificial `a` or `b` verse fragments.
- All 72 target files pass idempotency, JSON parsing, tag balance, non-overlapping coverage, and exact policy-count checks.

## Traditional-edition Jesus-word markup

- English KJV and French Louis Segond retain native source word-level markup.
- Arabic Van Dyck, German Luther 1912, Spanish Reina-Valera 1909, Italian
  Diodati 1885, Portuguese Almeida 1911, Russian Synodal 1876, Japanese Bungo,
  Korean Revised Version 1961, and both Chinese Union Version scripts each
  have an exact reviewed ledger for all 626 mixed narration/speech authority
  units. Import fails on stale target text, incomplete authority coverage,
  ambiguous spans, or visible-text drift.
- Portuguese has 628 reviewed rows: 626 mixed authorities plus full-verse
  narrator corrections at Matthew 19:5 and Mark 12:37. It has no omissions or
  speech relocations and produces 2,038 Jesus-word spans.
- Japanese has 634 reviewed rows. Eight source-omission units retain the exact
  visible `[なし]` placeholder without Jesus-word color: Matthew 17:21, 18:11,
  and 23:14; Mark 7:16, 9:44, 9:46, and 11:26; and Luke 17:36. The packaged
  Bungo corpus contains zero `[J][なし][/J]` strings.
- The full traditional validator reproduces every source-backed overlay from
  pinned source bytes. The traditional Python suite passes 144 tests; six
  optional command-line source-reproduction tests skip during ordinary
  discovery because the full validator supplies those artifacts separately.

## English KJV overlay

The English KJV overlay has a cleaner provenance than the localized base corpora:

- Source: [current eBible standardized KJV 1769 with Apocrypha archive](https://ebible.org/Scriptures/eng-kjv_usfm.zip), SHA-256 `1BAB5D4D030439831FC0B39D7F11001DD8527FC6277405E7F6512273C200C3A4`; audited 2026-09-25.
- Manifest: 80 source-backed books, 1,355 chapters, and 36,716 verses. The deterministic extracted 80-file set SHA-256 is `07378DB0B11E8B882841729544F55B7BC6D543BAF855D14CC0FF6B120ABB061F`.
- Every overlay file matches a fresh importer run. Validation found zero missing chapters or verses, 2,038 balanced Jesus-word spans, 21,539 balanced translator-addition spans, 116 superscriptions, and all 16 traditional New Testament readings.
- The current source corrected two 1 Maccabees lines relative to the prior pin: `many spoils` at 10:87 and the final period at 15:31. Bible.com KJVAAE confirms both.
- Four books are explicit fallbacks to the existing app text, not KJV: Greek Esther, Psalm 151, 3 Maccabees, and 4 Maccabees.
- The Bible.com comparison tool passes its self-test, but a full Bible.com text comparison was not run because YouVersion's supported API requires an app key and content licenses.

## Deuterocanon lineage

No non-KJV deuterocanon language is a clean, corpus-wide publisher edition.

This lineage table describes the base corpus. The 2026-09-26 Russian Synodal
traditional overlay adds 13 complete source-backed books from CrossWire
RusSynodal 1.9.1; its remaining five Deuterocanon books use explicit base
fallbacks. It does not make the modern Russian base corpus a Synodal edition.

| Language | Primary lineage | Verdict |
| --- | --- | --- |
| en | Mainly NRSVUE import, followed by gap and structural repairs | Published-source derived; not externally proven verbatim |
| de | English-derived custom translations | No named German publisher edition |
| es | DHH94I where available, plus English-derived fills/replacements | Hybrid |
| fr | BFC where available, plus English-derived fills/replacements | Hybrid |
| it | ICL00D where available, plus English-derived fills/replacements | Hybrid |
| pt | BPT09DC where available, plus English-derived fills | Hybrid |
| ru | Russian Synodal base plus English-derived fills | Hybrid; base wording is old, not modern NRT |
| ja | Bible.com 1819 Shinkyoudoyaku plus English-derived fills | Hybrid |
| ko | English-derived custom translations | No named Korean publisher edition |
| zh-Hans | Bible.com 1889 Traditional Chinese text converted to Simplified, plus English-derived fills | Derivative hybrid |
| zh-Hant | Bible.com 1889 Hong Kong Bible Society deuterocanon plus English-derived fills | Hybrid |
| ar | Arabic Van Dyck-derived import plus English-derived fills; the old `AR1665` shorthand is not a verified edition identifier | Hybrid; base is not modern SAB |
| hi | English-derived custom translations | No named Hindi publisher edition |

## Enoch, Jubilees, and other noncanonical material

- English 1 Enoch: 108 chapters and 1,034 bullets, derived from R. H. Charles 1917.
- English 2 Enoch: 68 chapters and 321 bullets, derived from W. R. Morfill 1896 as edited by Charles. This is the shorter 68-chapter recension.
- English Jubilees: 50 chapters and 1,305 bullets, derived from Charles 1902.
- The three are structurally complete for the declared recensions, but later commits filled gaps and mechanically modernized wording. They are not verbatim editions.
- Their non-English files follow the same app structure and are custom agent translations, not named foreign publisher editions.
- Gospel of Thomas is a curated 120-bullet digest with omissions, duplicates, variants, and summaries. It is not a continuous published translation.
- Most other apocrypha/pseudepigrapha entries are authored summaries rather than full source texts.

## Repaired material errors

1. Japanese Acts 15:34 now follows JCB 15:33-35: Judas and Silas return; Paul and Barnabas stay. The later "Silas stayed" reading remains only as an exact JA1955 note.
2. Japanese Acts 24:7-8 now preserves JCB's native range and the clause that the commander ordered a trial under Roman law.
3. Japanese Mark 11:26-28 now follows the JCB range beginning the return to Jerusalem. The traditional forgiveness warning remains only as an exact JA1955 note.
4. Russian Psalm 89:16 now uses the published NRT word `velichie`, not `slava`.
5. The Japanese cleanup removed all 2,707 surviving known JA1955/ERV insertions from 57 files and restored the verified JCB ranges. A fresh provenance multiset audit found zero known remnants.
6. Hindi Revelation 12:18 now matches its official source rather than an English-derived rendering. The repair changes no other wording and rejects drift in either the old text or the pinned source.

The complete review of 78 removed worktree text units found that the other alarming removals were duplicate removal, native versification changes, or text resegmentation. This clears the reviewed removal/replacement set; it does not create a corpus-wide live-publisher parity claim.

## Audit limits

- Bible.com renders Arabic SAB Luke 7:15 as empty. The [International Sharif Bible Society's Luke PDF](https://www.kitabsharif.org/sites/www.kitabsharif.org/files/bshart%20lwqa_0.pdf), page 10 of the PDF (printed page 901), supplies the complete SAB text; [Bilughatain's Luke 7](https://ar-en.bilughatain.org/index.php/en/luke/7) independently reproduces it. Both give `فَجَلَسَ الْمَيِّتُ وَأَخَذَ يَتَكَلَّمُ، فَأَعْطَاهُ عِيسَى لِأُمِّهِ.` The previous local text used a period and `ثُمَّ أَعْطَاهُ`; that single verse now follows the publisher. The local verse prefix SHA-256 is `71dd0d1d776d6b9c87d421c37b2bddaf5049f33eb108442f6825e03e1b8e9f6a`. This verifies the app verse against a fallback publisher source, not against Bible.com's empty rendering, and does not establish whole-chapter Bible.com parity.
- Six Song of Songs speaker changes occur inside English BSB verses. The app heading model anchors headings before native verse units, so it cannot represent those six inline labels without changing Scripture text. No such change was made.
- The modern live audit covers the canonical Old and New Testaments. The deuterocanon and noncanonical collections keep the provenance limits documented above.
- Traditional overlays use exact pinned Scripture text and explicit native heading tables. The ten editions without complete native word-level speech markup now use exact hash-pinned reviews for every mixed KJV authority unit. These ledgers prove the app's selected boundaries; they do not convert the target editions into publisher-issued red-letter editions.
- A small set of cross-edition native units cannot become one same-chapter app range. Those links retain their source edition. Almeida Revelation 13:1 is one explicit case because its native clause boundary touches modern Revelation 12:18 and 13:1; the app does not fabricate a split or one-verse mapping.
- Browser evidence stays in the ignored local audit cache. The committed tools contain fail-closed rules, hashes, tests, and source manifests; they do not commit publisher page content.
- No local Gradle, Android, or iOS build ran. CI and device tests still need to verify platform integration and rotation behavior.
