# Licensed modern-edition parity audit

`compare_modern_editions.py` compares the app's 66 canonical Old/New Testament books with the intended YouVersion edition in each of 13 languages. It uses only the documented YouVersion Platform API. It never changes Scripture, grants a license, accepts terms, follows browser challenges, or caches API responses. Its optional JSON report contains references, SHA-256 hashes, lengths, counts, and findings; it contains no verse text or App Key.

```powershell
python -B -m unittest discover -s tools/youversion -p 'test_compare_modern_editions.py' -v
python -B tools/youversion/compare_modern_editions.py --languages en,hi
$env:YVP_APP_KEY = '<existing authorized app key>'
python -B tools/youversion/compare_modern_editions.py --languages en,hi --report .scripture-structure-cache/modern-parity.json
```

The second command makes no API requests when `YVP_APP_KEY` is absent and reports `not_audited`. Before using a key, register the app and arrange the relevant publisher licenses in the YouVersion Platform portal. The tool checks the licensed `/bibles` collection before requesting chapters. A target absent from that collection, HTTP 403, a client challenge, unsupported markup, or an incomplete API response yields `blocked`, never `match`. Exit codes: `0` complete match, `1` complete comparison with findings, `2` blocked or not audited. Do not treat public version metadata as content authorization.

The pinned targets are en/3034 BSB, de/157 SCH2000, es/128 NVI, fr/104 NBS, it/122 NR06, pt/1930 NVT, ru/143 NRT, ja/83 JCB, ko/142 RNKSV, zh-Hans/36 CCB, zh-Hant/139 RCUV, ar/153 SAB, and hi/1980 IRVHin. The tool enumerates actual API books and chapters, compares native verse ranges without splitting bridges, and compares exact text after HTML entity, line-ending, Unicode NFC, and markup-boundary whitespace normalization. It reports Jesus-word boundary differences separately only when the source chapter exposes `wj` markup; other chapters count markup as unavailable, not matched.

Official access references: [authentication](https://developers.youversion.com/authentication), [API usage](https://developers.youversion.com/api-usage), [licenses](https://developers.youversion.com/api/licenses), and [403/license behavior](https://developers.youversion.com/error-codes). Review each publisher agreement before deciding whether to retain or redistribute source responses; this tool retains none.

The fixtures follow the [documented response schemas](https://developers.youversion.com/api/bibles): collection responses use `data`, while a passage response exposes `id` and `content` at the root. The comparator checks the returned passage ID. Local range parsing supports Japanese references without a preceding space and native en-dash ranges. Russian ID 143 accepts its [published localized abbreviation](https://www.bible.com/ru/versions/143?returnTo=PRO.1) as well as the app's English NRT label; other unexpected edition labels stop the audit.

## Offline sanitized-cache check

`audit_cached_modern_editions.py` rechecks the current app against complete,
sanitized BibleGateway cache records for SCH2000, NVI, NR2006, NVT, NRT, and
BibleGateway's CCB revision. It uses only publisher metadata, native range
markers, one-way text hashes, and page hashes. It performs no network request
and exposes no source verse text.

```powershell
python -B -m unittest discover -s tools/youversion -p 'test_audit_cached_modern_editions.py' -v
python -B tools/youversion/audit_cached_modern_editions.py
```

The default report is language-level; `--details` adds per-book counts. A hash
match proves equality to the cached BibleGateway response. A mismatch is not
automatically an app error because BibleGateway and Bible.com can expose
different revisions under similar labels; the CCB comparison is explicitly
structural-only. The report pins each sanitized cache with a deterministic
aggregate SHA-256 over sorted relative paths and exact cache bytes.
