# Localized Scripture structural auditor

`audit_localized_scripture.py` performs a read-only structural comparison of
the app's 66-book localized Bible files against explicitly pinned
BibleGateway editions. It does not import, stage, rewrite, or apply Scripture.

The auditor checks:

- the exact BibleGateway version code, catalog label, language group,
  passage-version root, canonical URL, and publisher attribution;
- every local book/chapter boundary and one source boundary past each book;
- verse inventory, empty omitted markers, and bridged verse grouping without
  splitting a source line;
- heading placement, including editions that number Psalm superscriptions as
  verse 1;
- the presence of Words-of-Jesus and translator-addition markup only when the
  selected BibleGateway edition exposes that semantic markup; and
- the 16 traditional New Testament verses against a configured native
  fallback edition when the selected modern edition omits them.

The tool downloads HTML only to evaluate the current response. It discards the
HTML before any file write. Its Git-ignored cache contains sanitized marker
records, heading positions and hashes, page hashes, version attribution, and
optional one-way hashes for at most 24 explicitly requested short spot checks.
It never caches raw HTML or source verse text.

From the repository root:

```powershell
python -B tools/youversion/audit_localized_scripture.py --self-test
python -B tools/youversion/audit_localized_scripture.py --languages de
python -B tools/youversion/audit_localized_scripture.py --languages de,es --spot-checks de:EPH.6.10,es:MAT.17.20
```

Exit code `0` means the selected audit is clean, `1` means the audit completed
with findings, and `2` means a source, cache, parser, or local invariant blocked
the audit. The manifest path is printed at the end. There is no `--apply`
option.

## Source-backed traditional-reading repair

`repair_traditional_nt_readings.py` is a separate, intentionally narrow repair
utility for the 16 disputed New Testament addresses. It encodes the final
edition policy established from the pinned import snapshots and the cited
Bible.com publisher pages:

- exact selected-edition body text remains in the body;
- readings omitted by the selected edition become one exact, version-labelled
  `manuscriptVariants` entry;
- native ranges such as John 5:3-4, Acts 24:6-8, Mark 9:43-44, and Mark
  9:45-46 remain unsplit; and
- generated quotations, duplicate variants, and artificial `a`/`b` verse
  fragments are rejected.

Use `--emit-patch LANGUAGE BOOK` for a read-only idempotency check of one target
file. Running the script without arguments applies the complete 72-file repair.
See `SOURCE_AUDIT_2026-09-25.md` for the source matrix, exact policy counts, and
audit limits.

## Primary source matrix

| App language | BibleGateway edition | Bible.com numbering/link parity |
| --- | --- | --- |
| `de` | Schlachter 2000 (`SCH2000`) | 157 / `SCH2000`, same edition |
| `es` | Nueva Version Internacional (`NVI`) | 128 / `NVI`, same edition |
| `fr` | Segond 21 (`SG21`) structural comparator | 104 / `NBS`, link and numbering authority |
| `it` | Nuova Riveduta 2006 (`NR2006`) | 122 / `NR06`, same edition |
| `ru` | New Russian Translation (`NRT`) | 143 / `NRT`, same edition |
| `pt` | Nova Versao Transformadora (`NVT`) | 1930 / `NVT`, same edition |
| `ja` | Japanese Living Bible (`JLB`) | 83 / `JCB`, different app-link edition |
| `ko` | Korean Living Bible (`KLB`) structural comparator | 142 / `RNKSV`, link and numbering authority |
| `zh-Hans` | Chinese Contemporary Bible, Simplified (`CCB`) structural comparator | 36 / `CCB`, same name but different platform text |
| `zh-Hant` | RCUV 2017 Shen edition (`RCU17TS`) structural comparator | 139 / `RCUV`, link and numbering authority |
| `ar` | Ketab El Hayat (`NAV`) structural comparator | 153 / `SAB`, link and numbering authority |
| `hi` | Hindi Easy-to-Read Version (`ERV-HI`) structural comparator | 1980 / `IRVHIN`, link and numbering authority |

Bible.com text is not treated as a bulk-extractable source when its browser
challenge blocks deterministic requests. The structural auditor records
Bible.com IDs and codes for numbering and link review; it does not claim a
full-corpus text comparison. Targeted, cited publisher-page checks support the
traditional-reading repair. For French, Korean, Simplified Chinese,
Traditional Chinese, Arabic, and Hindi, BibleGateway does not carry the exact
Bible.com parity text. Findings from those six BibleGateway editions are
secondary structural comparisons and cannot prove corpus-wide wording or
edition-specific verse inventory.

## Traditional-verse source limits

Configured native BibleGateway fallback editions are RVR1960, LSG, LND, RUSV,
ARC, JERV, CUVS, CUV, and SHB. A fallback must contain a substantive marker for
the exact verse. Existing local wording never proves a fallback and never gets
silently accepted.

When BibleGateway lacks edition parity, the final repair uses the pinned import
or an exact cited Bible.com publisher note. It never invents Scripture or
borrows wording from another language.
