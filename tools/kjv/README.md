# KJV 1769 edition assets

`import_kjv_usfm.py` structurally imports the pinned eBible `eng-kjv` USFM
snapshot. It removes Strong's attributes and source footnote apparatus from
display text while retaining their audit counts. It does not read or modify the
existing English BSB/custom Deuterocanon JSON.

Run from the repository root:

```powershell
python -B tools/kjv/import_kjv_usfm.py --source "G:\Bible Companion App\eng-kjv_usfm"
python -B tools/kjv/validate_kjv_overlays.py --source "G:\Bible Companion App\eng-kjv_usfm"
```

If the original ZIP is present, pass `--archive` to both commands. The scripts
then require SHA-256
`1165788907A8BBE93C3299D89EB5134D942038E5DCA2C0832BEB13E8F72441D0`.

## Overlay schema

Each file under `shared/assets/books/editions/en/kjv1769/<collection>/` has:

```json
{
  "schemaVersion": 1,
  "editionId": "kjv1769",
  "language": "en",
  "collection": "new_testament",
  "bookId": "matthew",
  "sourceBookCode": "MAT",
  "coverage": "full",
  "chapters": [
    {
      "number": 1,
      "superscription": "Only present when the source supplies one",
      "verses": [
        {"chapter": 1, "verse": 1, "text": "Exact display text"}
      ]
    }
  ]
}
```

Verse `text` may contain balanced `[J]...[/J]` and `[ADD]...[/ADD]` markers.
Removing those four markers yields the source verse text after removal of
presentation-only paragraph marks and normalization of USFM layout whitespace.
`[J]` comes only from `wj`; `[ADD]` comes only from `add`.

Verses with excluded source apparatus contain `sourceFootnoteCount`. Mapped
books add `sourceMapping`; mapped verses include `sourceChapter` and
`sourceVerse`. Baruch 6:1 is recorded as the Letter of Jeremiah source heading,
and Baruch 6:2-73 maps to Letter of Jeremiah 1:1-72.

`_manifest.json` pins the archive hash, source date, individual source-file
hashes, coverage, mappings, output counts, and four explicit fallback books.
The importer writes through a staging directory and replaces only the generated
edition directory after every source file parses successfully.

## Official Bible.com comparison

`compare_biblecom.py` performs a separate, read-only comparison against the
official YouVersion Platform API. It fetches one HTML passage per chapter and
uses YouVersion's `yv-v` milestones for verse boundaries. The comparison does
not scrape the public website and never modifies edition or Scripture assets.

The API requires a YouVersion Platform App Key and content licenses for both
Bible ID 1 (`KJV`) and Bible ID 546 (`KJVAAE`). Put the key only in the current
process environment, then run the validator from the repository root:

```powershell
$env:YVP_APP_KEY = "your-platform-app-key"
python -B tools/kjv/compare_biblecom.py
Remove-Item Env:YVP_APP_KEY
```

Successful chapter responses and the default `report.json` go under
`.kjv-biblecom-cache/`, which Git ignores because the cache contains licensed
Bible text. The cache makes interrupted comparisons resumable. Use `--refresh`
to replace it, or `--report` to select another report path. Never place an App
Key in a command argument, source file, report, or committed environment file.

The validator compares all 66 canonical books with KJV Bible ID 1. It compares
only reviewed KJVAAE mappings with Bible ID 546: Tobit, Judith, Wisdom, Sirach,
Baruch, Letter of Jeremiah through its explicit Baruch 6 source mapping, Song
of the Three, Bel and the Dragon, 1 Maccabees, and 2 Maccabees. The JSON report
lists every excluded book and the reason. Susanna remains excluded because
YouVersion exposes an anomalous chapter identifier; Greek Esther, 1 Esdras, 2
Esdras, Prayer of Manasseh, Psalm 151, 3 Maccabees, and 4 Maccabees also lack a
reviewed one-to-one mapping in this source/API pair.

The report records exact normalized text for every differing reference, markup
span placement differences, API failures, and separate assertions for all 16
traditional KJV New Testament verses. Psalm superscriptions are counted and
listed as an explicit content exclusion: the API call disables headings, and no
reviewed one-to-one map exists between YouVersion headings and the overlay's
separate superscription field. Normalization is deliberately narrow: HTML
entities, line endings, Unicode NFC, supported presentation wrappers, and
whitespace at element boundaries. It does not fold punctuation, case, spelling,
or brackets. Unknown red-letter semantics, bridged verse milestones, a missing
key, or a missing content license blocks the comparison instead of producing a
partial pass.

Parser and mapping checks require no key and make no network requests:

```powershell
python -B tools/kjv/compare_biblecom.py --self-test
```

API reference: <https://developers.youversion.com/api-usage> and
<https://developers.youversion.com/api/bibles>.
