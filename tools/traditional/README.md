# Traditional in-app Bible editions

`import_traditional_editions.py` builds the non-English traditional-edition
overlays. It never changes the current localized books. Scripture text and
Psalm superscriptions come from the pinned source; introductions, section-
heading text, summaries, key takeaways, notes, and cross-references remain in
the localized base corpus. When an edition puts the same passage at a different
reference boundary, an audited overlay moves the existing heading with that
passage. It never invents or translates a new heading.

The importer requires the exact source files and SHA-256 values declared in
`EDITIONS`. Most sources are public-domain eBible USFM archives or official
CrossWire SWORD modules. The Almeida module is distributed by CrossWire under
the GNU General Public License; its manifest records that license and the
public-domain 1911 source edition. The app falls back to its current localized
Deuterocanon because these sources contain the 66-book Protestant canon.

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
directory:

```powershell
python -m pip install --target C:\temp\traditional-pysword pysword==0.2.8
python -B tools/traditional/import_traditional_editions.py `
  --source-root C:\path\to\traditional-sources `
  --pysword-path C:\temp\traditional-pysword
python -B tools/traditional/validate_traditional_editions.py
```

The importer fails on unknown markers, source hash changes, missing books,
chapter gaps, verse gaps, empty text, leaked reference metadata, headings that
do not land on a selected-edition verse, and unexplained SWORD verse slots.
Every schema-2 chapter records its final covered verse and verse-unit count;
both the audit tool and runtime loader reject incomplete or internally
inconsistent overlays. Existing English KJV schema-1 overlays remain supported
and receive the same contiguous-reference validation at runtime.
Luther 1912 native reference labels embedded by eBible are stripped from the
display text and used to restore the edition's own chapter and verse numbering.
Source-native combined verses remain one text unit with an explicit
`verseEnd`; the UI does not split them into invented halves. Chinese CUV verses
preserved in numbered source footnotes are promoted with an audit flag. Korean
KRV native merged and omitted verse markers remain explicit placeholders
rather than being silently dropped, duplicated, or filled from another Bible.

Exact source `wj` spans remain exact red-letter spans. When a source does not
include `wj`, only an entire verse reviewed as Jesus' speech in the English BSB
metadata inherits a whole-verse `[J]` span. A verse mixing narration and speech
stays uncolored; this avoids falsely coloring narration. The manifest counts
both inherited full-verse spans and deliberately omitted mixed spans.

Hindi intentionally remains single-edition. The expected Hindi Old Version
(HINOVBSI) is copyrighted by the Bible Society of India, and no complete,
redistributable traditional Hindi digital source passed this audit. Do not
scrape and relabel it, or generate a synthetic translation, to make the option
count look complete.
