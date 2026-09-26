# Scripture source lineage and exactness audit

Date: 2026-09-25 through 2026-09-26

Scope: the current working tree on top of `cf552051`, including the source-proven repairs described below. The audit tools are read-only by default; repair utilities are narrow, reproducible, and idempotent.

## Verdict

The app still does not contain thirteen independently proven, word-for-word publisher editions. It now has a much cleaner evidence boundary:

- English BSB has zero unresolved differences against the supplied BSB USFM after separately adjudicating known export artifacts, live Bible.com differences, verse ranges, and red-letter markup.
- The English KJV overlay matches a freshly generated copy of the pinned current eBible KJV-with-Apocrypha source across all 80 source-backed books. Four books remain explicit fallbacks because that source cannot safely supply them.
- The 16 disputed traditional New Testament readings were repaired in all 12 non-English corpora. Published body text remains body text; omitted readings now remain exact, version-labelled manuscript notes. Native ranges were not split.
- All 2,707 surviving known JA1955/ERV insertions were removed from the Japanese JCB corpus. The repaired set now has zero known contamination, but a complete licensed live comparison of every JCB line was not available.
- Several large differences are legitimate native versification, punctuation, heading movement, divine-name presentation, or source apparatus. Counts alone do not prove corruption.
- The deuterocanon remains a set of hybrid corpora. Some books came from named published editions; other passages and entire language sets were translated from English to fill gaps.
- English 1 Enoch, 2 Enoch, and Jubilees derive from old public-domain translations that were mechanically modernized. They are not verbatim modern publisher editions. Their foreign-language files are custom translations of the English-derived app text.

## Canonical Old and New Testament source matrix

| App language | Intended source lineage | Modern? | Word-for-word source verdict |
| --- | --- | --- | --- |
| en | [Berean Standard Bible, Bible.com 3034](https://www.bible.com/versions/3034-bsb-berean-standard-bible) | Yes | Source-adjudicated against the supplied BSB USFM: complete coverage, zero unresolved text differences, and zero red-letter differences on exact text. Raw source-export artifacts are documented below. |
| de | [Schlachter 2000, Bible.com 157](https://www.bible.com/de/versions/157) | Modern revision with traditional/formal style | Corpus-wide live identity is not proven. The 16 traditional readings now exactly match the pinned SCH2000 import. |
| es | [Nueva Version Internacional, Bible.com 128](https://www.bible.com/es/versions/128) | Yes | NVI body no longer contains generated traditional-verse additions. All 16 omitted readings are exact, version-labelled RVR1960 manuscript notes. Two plain-text lines differ from the import snapshot. |
| fr | [Nouvelle Bible Segond, Bible.com 104](https://www.bible.com/fr/versions/104) | Yes | Corpus-wide live identity is not proven. NBS body omits the 16 disputed readings; one exact LSG note preserves each reading with native LSG wording. |
| it | [Nuova Riveduta 2006, Bible.com 122](https://www.bible.com/it/versions/122) | Modern revision with traditional/formal style | Corpus-wide live identity is not proven. The 16 traditional readings now exactly match the pinned NR06 import. |
| pt | [Nova Versao Transformadora, Bible.com 1930](https://www.bible.com/versions/1930) | Yes | All source-import text still exists; one extra current range reflects later structure. The 16 traditional readings now exactly match the pinned NVT import. BibleGateway exposes a platform text that differs in 214 strict ranges and is not the correction authority. |
| ru | [New Russian Translation, Bible.com 143](https://www.bible.com/ru/versions/143) | Yes | Corpus-wide live identity is not proven. The confirmed Psalm 89:16 lexical error and all 16 disputed readings are repaired to NRT. Native Psalm numbering accounts for much of the line-count difference. |
| ja | [Japanese Contemporary Bible, Bible.com 83](https://www.bible.com/ja/versions/83) | Yes | All 2,707 surviving known JA1955/ERV insertions were removed. Verified JCB wording and native ranges were restored at the identified failures; only exact JA1955 manuscript notes remain at Mark 11:26 and Acts 15:34. Full live corpus identity is not claimed. |
| ko | [Revised New Korean Standard Version, Bible.com 142](https://www.bible.com/ko/versions/142) | Yes, 2001 revision | RNKSV body keeps Mark 7:16 and bracketed John 5:3-4; the other disputed readings are exact publisher notes, including unsplit Acts 24:6-8. Corpus-wide live identity is not proven. |
| zh-Hans | [Chinese Contemporary Bible, Bible.com 36](https://www.bible.com/versions/36) | Yes | The 16 disputed readings now match the pinned CCB import, including native Mark 9:43-44 and 9:45-46 ranges. BibleGateway exposes a different CCB platform revision, so it is not the correction authority. |
| zh-Hant | [Revised Chinese Union Version, Bible.com 139](https://www.bible.com/zh-TW/versions/139) | Yes | RCUV body omits all 16 disputed readings. Exact publisher notes preserve them, including unsplit John 5:3-4 and Acts 24:6-8. Corpus-wide live identity is not proven. |
| ar | [Sharif Arabic Bible, Bible.com 153](https://www.bible.com/versions/153-sab-sharif-arabic-bible) | Yes | SAB-derived with deliberate divine-name presentation. The 16 disputed readings now match the pinned import; two other source lines still differ from that snapshot. |
| hi | [Indian Revised Version Hindi 2019, Bible.com 1980](https://www.bible.com/versions/1980-irvhin-irv-2019) | Yes | All source-import text still exists; one extra current range reflects later structure. The 16 disputed readings now match the pinned IRVHin import. The independent eBible comparison still records apparatus and presentation differences described below. |

"Modern" describes the intended publisher edition. It does not prove that the app reproduces that edition exactly.

## Exact-comparison evidence

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
| hi | 31,103 | 31,104 | 31,103 | 0 | 0 |

Some remaining lines moved to different native verse references. Editions that publish traditional readings keep them in body text; editions that omit them keep exact readings in manuscript notes, which this table does not count. These figures prove post-import change, not that every changed line is wrong.

The Japanese Git snapshot already contained the earlier JA1955/ERV insertions. Its 2,860 unmatched snapshot lines therefore include the 2,707 known removals and cannot be read as missing JCB verses.

Independent reference checks:

- English BSB versus the supplied USFM: 31,086 app units and 31,086 source units; 31,022 are strict raw matches. All 64 raw differences are adjudicated: 59 literal broken footnote-export tokens, three stray `vvv` tokens, and two live Bible.com/source-export differences. There are zero missing units, zero unresolved text differences, and zero red-letter differences on exact text. Native 3 John and Revelation ranges are handled without splitting verses.
- Hindi IRVHin versus the official eBible USFM: 31,104 lines on both sides, 30,704 exact, 400 different, no missing line on either side, and 560 red-letter differences on otherwise exact text. Cross-reference apparatus accounts for much of the strict difference count.
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

The complete review of 78 removed worktree text units found that the other alarming removals were duplicate removal, native versification changes, or text resegmentation. This clears the reviewed removal/replacement set; it does not create a corpus-wide live-publisher parity claim.

## Audit limits

Bible.com currently returns a client challenge to deterministic bulk Python requests. YouVersion's supported API requires an app key and the relevant publisher content licenses. BibleGateway can independently test editions it carries, but it cannot substitute for NBS, JCB, RNKSV, RCUV, SAB, or IRVHin, and its CCB text is not identical to Bible.com 36.

Accordingly, this report does not label any unproved corpus "exact." The safe repair process is to pin licensed source files, rebuild verse text deterministically, preserve notes/summaries separately, and review every intentional departure before changing Scripture.
