# Swahili assets and audit

The app's default Swahili Bible is Biblica Open Kiswahili Contemporary Version
(Neno) 2015. The alternate canonical edition is the supplied Swahili Union
Version XML. Both are packaged as JSON; the source ZIP and XML are not packaged.
The 18 Swahili deuterocanonical books and other noncanonical books are app
study translations. The reader labels the deuterocanonical text separately;
it does not attribute that text to either canonical Bible edition.

## Sources and rights

- ONEN: `swhonen_usfm.zip`, SHA-256
  `9ad97f89077ba8a07c94b907c70f2b1980a00a1f43f411752bb57d106c9fc0c6`.
  Biblica permits redistribution and reasonable adaptation under Creative
  Commons Attribution-ShareAlike 4.0 with attribution and change notices:
  <https://ebible.org/swhonen/copyright.htm>. The app's source manifest and
  About screen record its attribution and conversion changes.
- SUV: `SwahiliSUV1952Bible.xml`, SHA-256
  `a506e7eee5a1198e159f2d202f52783a86b8be7e247eb5a05cd03633b9e12b20`.
  This supplied file has no redistribution license. Digital Bible Society
  lists its Swahili Union Version metadata as `RESERVED`:
  <https://arc.dbs.org/data/scripture/SWHSUV/>. Permission to distribute this
  exact XML-derived text remains unverified.
- The existing German Tobit text matches Einheitsuebersetzung 2016 in the
  audited passage; its publisher marks that edition all rights reserved:
  <https://www.bibleserver.com/EU/Tobit6>. Repository permission for the
  existing German deuterocanonical corpus remains unverified.

The converter pins both source hashes and refuses to overwrite an existing
asset. It builds canonical Scripture and edition overlays, not editorial prose.

## Display marker placement

The SUV overlay has 2,067 Jesus-speech spans in 2,052 verses. Their character
positions live in `suv_jesus_spans.json`, based on the ONEN archive's native
`wj` markers and independently reviewed against SUV wording in all 27 New
Testament books. Three Matthew references use adjacent verse numbers between
the editions; the speech text itself was matched. Removing display tags leaves
the SUV XML text unchanged.

The ONEN New Testament leaves some Old Testament quotations' `Bwana` unmarked.
`sw_quote_divine_names.json` records 46 explicit quotations where the cited
ONEN Old Testament verse has a native `nd` span. The importer adds 48 editorial
divine-name spans to each New Testament edition at those checked positions.
Ambiguous allusions and ordinary Lord titles remain unmarked. This editorial
rule is separate from the publisher's native markup; the ledger records the
source passage and the exact word occurrence for each edition.

Swahili study JSON and Markdown notes also mark uppercase divine-name forms
so color and replacement settings apply to study text and sharing. The marker
audit checks balance and finds no unmarked uppercase `BWANA` in these assets.

## Asset map

- Base canonical text: `shared/assets/books/{old_testament,new_testament}/sw/`
- Alternate SUV overlay: `shared/assets/books/editions/sw/suv1952/`
- App translations: `shared/assets/books/{deuterocanonical,apocrypha,pseudepigrapha}/sw/`
- Study notes, daily verses, and book aliases: `shared/assets/notes/sw/`,
  `shared/assets/daily_verses/sw/`, `shared/assets/refs/sw/`
- Compose, Android, and iOS user interface strings: their `values-sw`,
  `values-sw`, and `sw.lproj` resource directories respectively.

The canonical source comparison covers 66 books, 1,189 chapters, 31,103 ONEN
verse units, and 31,102 SUV verse units. The Swahili study content covers all
23 Markdown notes and all 36 noncanonical books. Ten English master notes
describe the wrong chapters: five in Proverbs and five in 2 Chronicles. Their
Swahili copies were omitted with exact, source-checked exceptions in
`audit_assets.py`; the English and older localized copies remain unchanged.

## Recheck

Run from the repository root:

```sh
python3 tools/swahili/audit_assets.py \
  --onen /path/to/swhonen_usfm.zip \
  --suv /path/to/SwahiliSUV1952Bible.xml
python3 tools/swahili/audit_deuterocanon.py \
  --language de --language ko --language hi --language sw
python3 tools/swahili/audit_book_references.py
python3 tools/swahili/audit_localization_labels.py
python3 tools/swahili/audit_daily_verses.py
python3 tools/swahili/audit_common_distortions.py
python3 tools/swahili/audit_display_markup.py
python3 tools/swahili/audit_semantic_index.py
python3 tools/swahili/audit_platform.py
python3 tools/prophecy/audit_note_references.py --language sw --all-notes
git diff --check
```

These checks validate source identity, data structure, reference addresses,
display labels, and direct Common Distortions quotations. Bilingual review of
the German, Korean, Hindi, and Swahili deuterocanonical books sampled all 18
books per language; it was not a line-by-line certification. The Swahili
semantic search currently routes an existing multilingual model's English
embedding index to the corresponding Swahili stories. Static route coverage
passes, but ranking quality and native Android/iOS behavior need device tests.
