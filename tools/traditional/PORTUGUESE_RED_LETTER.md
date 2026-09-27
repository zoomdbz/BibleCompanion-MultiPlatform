# Almeida 1911 Jesus-word boundary audit

The eBible/CrossWire PorAlmeida1911 source has no `wj` runs. The pinned
`en/kjv1769` CrossWire overlay supplies semantic attribution, not character
offsets. Its audit has 626 mixed Jesus/narrator authorities and 1,402
whole-speech authorities. Portuguese words come only from the Almeida 1911
target source; neither witness replaces its text.

Two independent Portuguese red-letter MyBible modules from
[ph4.org](https://www.ph4.org/b4_1.php?l=pt) supplied boundary evidence:

| Witness | Archive bytes | SHA-256 |
| --- | ---: | --- |
| AA | 1,891,645 | `97821E78214BA9E989DD2362A156E843D3E18FB5FE121A548486DE030851F90B` |
| ARC | 1,967,250 | `1DE56663CCF0A9C4F40AB163DF963F3C12EECEF7AF93ACB4CBD0301DBCC87FD0` |

`fetch_portuguese_red_evidence.py` checks both archive hashes before reading
their SQLite verse tables. The checked-in mixed and full evidence reports keep
the witness plain text and red runs. `propose_portuguese_witness_alignment.py`
maps witness boundaries to exact target characters through deterministic
case/accent alignment. It does not alter or normalize target Scripture.

The mixed report has 467 dual-witness exact boundary matches. The remaining
159 rows were individually inspected against KJV `wj`, both witness spans,
Almeida 1911 punctuation, and the local speaker/narrator grammar. The pinned
decision file accepts ARC except for 18 explicit AA selections and 22 manual
selector corrections. Examples include excluding Peter's repetition from
Luke 8:45, stopping at the Aramaic utterance before narrator translations in
Matthew 27:46 and Mark 15:34, and retaining the speech on both sides of
Luke 5:14's `disse` intrusion. The generated ledger stores each target
verse's SHA-256, selected exact text, KJV authority hash, review method,
and evidence report hash. Selector indices cannot float because the entire
alignment report is hash-pinned.

The full-verse report audits all 1,402 authorities. Both witnesses mark 1,396
fully. Six differ between the witnesses or from KJV; target grammar and KJV
attribution require two `overrideInherited` rows: Matthew 19:5 excludes the
introductory `E disse`, and Mark 12:37 excludes the crowd's response.
Matthew 17:21, Mark 11:26, Luke 10:22, and Luke 17:36 retain full-verse
inheritance after review. No Portuguese mixed authority requires a relocated
target row or true omission.

Regenerate the checked-in ledger without network access:

```powershell
python -X utf8 -B tools/traditional/build_portuguese_jesus_ledger.py --check
```

Regenerate Portuguese from pinned raw sources with
`import_traditional_editions.py --languages pt`, then validate with
`validate_traditional_editions.py --languages pt`. The importer and validator
require all 626 mixed authorities, both reviewed full overrides, exact source
text identity after removing `[J]`, and unchanged `[DN]`/`[ADD]` inventories.
The final manifest reports 626 reviewed mixed speech units, zero omitted
mixed units, two full overrides, and 2,038 `[J]` spans.
