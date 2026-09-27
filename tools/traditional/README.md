# Traditional in-app Bible editions

## Edition coverage policy

[`edition_coverage_policy.json`](edition_coverage_policy.json) is the
machine-readable source of truth for traditional-edition identity and OT, NT,
and Deuterocanon coverage. Its offline audit checks the policy against every
manifest and packaged overlay:

```powershell
python -X utf8 -B tools/traditional/audit_edition_coverage.py
```

All 12 traditional editions have source-backed 39-book OT and 27-book NT
overlays. English has 14 source-backed Deuterocanon books, including two
reviewed `mapped` books, plus four explicit fallbacks. Russian has 13
source-backed Deuterocanon books plus five explicit fallbacks. The other ten
traditional editions use named 66-book sources, so all 18 Deuterocanon books
remain explicit base-corpus fallbacks. Hindi has no traditional edition. The
audit fails on a changed source identity, URL, or hash; a mislabeled manifest
row; a missing or unexpected overlay; an incorrect fallback target; or an
unapproved edition directory. A fallback cannot claim source proof or receive
edition-reference rules. It makes no network requests.

Reviewed alternates are not drop-in text. Spanish `SpaPlatense` 1948, French
`FreCrampon` 1923 and `FreVulgGlaire`, and Chinese `ChiSB`/Studium Biblicum are
redistributable sources with distinct edition identities. They would require
separate app editions and cannot fill Reina-Valera 1909, Louis Segond 1910, or
Chinese Union Version gaps. Portuguese `PorCap` is also a distinct edition,
and its license permits noncommercial distribution through CrossWire only,
not other redistribution. The German, Italian, Japanese, Korean, and Arabic
reviews found no redistributable Deuterocanon companion for the named edition.
HINOVBSI requires publisher authorization. The policy pins the reviewed
source hashes and dispositions so none can silently enter an existing overlay.

`import_traditional_editions.py` builds the non-English traditional-edition
overlays. It never changes the current localized books. Scripture text and
Psalm superscriptions come from the pinned source; introductions, section-
heading text, summaries, key takeaways, notes, and cross-references remain in
the localized base corpus. When an edition puts the same passage at a different
reference boundary, an audited overlay moves the existing heading with that
passage. It never invents or translates a new heading.

The importer requires the exact source files and SHA-256 values declared in
`EDITIONS` and parses USFM bytes directly from those verified archives. It does
not trust previously extracted copies. Most sources are public-domain eBible USFM archives or official
CrossWire SWORD modules. The Almeida module is distributed by CrossWire under
the GNU General Public License; its manifest records that license and the
public-domain 1911 source edition. The primary sources contain the 66-book
Protestant canon. Russian Synodal also uses a pinned public-domain CrossWire
`RusSynodal` 1.9.1 supplement for 13 Deuterocanon books (4,975 source verse units).
It includes complete native Daniel 13/14 as Susanna/Bel and Psalms 151 as its
standalone book. Every imported verse has an exact source slot. Five Russian
books still use the existing corpus: Prayer of Manasseh, 2 Esdras, 4 Maccabees,
Greek Esther, and Song of Three. Their manifests explain missing material or
unresolved numbering. Other traditional editions retain the localized fallback;
the importer does not label another translation as part of their canon.

Korean uses a pinned, reviewed 31,102-record KRV 1961 JSON transcription for
wording plus a second pinned KRV transcription only for native merged-verse
and omitted-verse markers. This avoids the alternate-versification shifts in
generic SWORD readers and preserves Bible.com's KRV convention, including
`(없음)` and `(25절에 포함되어 있음)`, without duplicating Scripture text.
Psalm 72:19-20 receives the same native merged treatment explicitly. The
Korean Bible Society states that the edition's economic rights expired on
2011-12-31; its attribution and integrity rights remain. The app identifies it
as the Korean Bible Society's 1961 KRV and does not paraphrase it.

Run the importer from the repository root after placing the archives in one
source directory. SWORD imports require `pysword==0.2.8` in a separate tool
directory. Russian also requires the official
`https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/RusSynodal.zip`
archive named `RusSynodal.zip`; the supplementary importer pins its hash:

```powershell
python -m pip install --target C:\temp\traditional-pysword pysword==0.2.8
python -B tools/traditional/import_traditional_editions.py `
  --source-root C:\path\to\traditional-sources `
  --pysword-path C:\temp\traditional-pysword
python -B tools/traditional/validate_traditional_editions.py
python -B tools/traditional/validate_traditional_editions.py `
  --source-root C:\path\to\traditional-sources `
  --pysword-path C:\temp\traditional-pysword
```

The default validator checks packaged structure, tags, maps, and manifest
consistency. Source mode also verifies the code-pinned archives and compares
every packaged native verse with the deterministic source transformation,
including all 13 Russian Deuterocanon books. It checks text and markup, not
just counts or manifest claims. It preserves the documented source apparatus,
native versification, and inherited whole-verse speech-markup rules. It writes
no corpus files; reports distinguish `structure-only` from
`pinned-source-text-and-structure` checks.

The importer fails on unknown markers, source hash changes, missing books,
chapter gaps, verse gaps, empty text, leaked reference metadata, headings that
do not land on a selected-edition verse, and unexplained SWORD verse slots.
Every schema-2 chapter records its final covered verse and verse-unit count;
both the audit tool and runtime loader reject incomplete or internally
inconsistent overlays. Existing English KJV schema-1 overlays remain supported
and receive the same contiguous-reference validation at runtime.

Every generated alternate-edition chapter contains an explicit `headings`
lookup, including `[]` when the chapter has none. That lookup is the runtime
source of truth: `beforeVerse` identifies the selected edition's native verse-
unit start, and the reader never infers placement from paragraph order or from
another edition's verse numbers. `edition_heading_maps.json` records reviewed
publisher-boundary exceptions separately from general passage equivalence. It
pins the source artifact, matches the exact stored localized heading text, and
records the native target anchor. A reviewed row overrides the general passage
map because a section boundary can differ even when verse identity does not.
Composite headings may split only when their declared space or newline join
reconstructs the original text byte-for-byte. Stale text, duplicate sources,
unknown books, bad provenance, lossy splits, and targets inside a combined
native unit all fail closed. The current ledger contains 168 reviewed
relocations across 109 edition/book tables for Arabic, German, French, Italian,
Japanese, Russian, Simplified Chinese, and Traditional Chinese.

Luther 1912 historical reference labels embedded by eBible are stripped from
display text and used for the app's localized navigation where supported.
Psalms keeps the eBible/Bible.com DELUT 51 numbering, not the heading-inclusive
LU12 numbering used by the German Bible Society; links between those schemes
require an explicit passage map.
The importer packages reviewed maps in `_reference_map.json` with source
attribution. German Psalms/Isaiah, Italian Job, Russian Romans, nineteen
[French books](FRENCH_REFERENCE_MAP.md), and Traditional Chinese John have
explicit mappings. Additional New Testament boundaries use
[`nt_reference_maps.json`](NT_REFERENCE_MAPS.md), including independently
reviewed Simplified Chinese John. The reader checks actual source and
destination verse inventories and permits only explicit reviewed rows,
including identity rows. Missing maps, unlisted passages, and disjoint targets
keep the reference in its source edition. The reader labels the actual edition
under its book title. It never treats equal verse counts as proof of identity.
The [native identity supplement](CONCORDANT_REFERENCE_MAPS.md) adds
native-unit rows only after shared verse-coordinate agreement outside the
specific units touched by reviewed exception rules, plus a hash-bound modern
source checkpoint. The coverage report
lists every native range that still lacks a proved conversion.
The heading importer uses the dedicated heading ledger first, then a reviewed
passage map where no heading-only row exists. It keeps source heading order
when distinct titles converge on one native unit and rejects ambiguous or
absent targets. Unlisted heading anchors remain at their source coordinate or
move only through an explicit passage map.
Source-native combined verses remain one text unit with an explicit
`verseEnd`; the UI does not split them into invented halves. Chinese CUV verses
preserved in numbered source footnotes are promoted with an audit flag. Korean
KRV native merged and omitted verse markers remain explicit placeholders
rather than being silently dropped, duplicated, or filled from another Bible.

An edition with source `wj` markers keeps those source-native red-letter spans.
For editions without `wj`, the pinned KJV 1769 CrossWire `wj` overlay supplies
semantic authority: 1,402 whole-speech verses and 626 mixed narration/speech
verses. Whole verses inherit `[J]` only when no reviewed override corrects a
target omission or narrator clause. Every mixed authority must resolve through
a hash-bound, edition-specific reviewed row before its edition enters the strict
gate. Relocations, combined native verses, repeated speech, omissions, and
whole-verse exceptions have explicit reviewed row forms. Import and validation
fail on missing authority coverage, target text drift, changed `[DN]` or
`[ADD]` inventories, or any source text change after stripping `[J]` tags.
Portuguese evidence and exception review are documented in
[PORTUGUESE_RED_LETTER.md](PORTUGUESE_RED_LETTER.md).

Hindi intentionally remains single-edition. The expected Hindi Old Version
(HINOVBSI) is copyrighted by the Bible Society of India, and no complete,
redistributable traditional Hindi digital source passed this audit. Do not
scrape and relabel it, or generate a synthetic translation, to make the option
count look complete.
