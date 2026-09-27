# Russian Synodal Jesus-word review

The speaker authority is the pinned English KJV1769 WJ overlay. The exact
target wording is the current `ru/synodal1876` edition. The independent
red-letter witness is ph4's MyBible `RST+` module:
`https://www.ph4.org/_dl.php?back=bbl&a=RST_plus_&b=mybible&c`.
The download SHA-256 is
`16DF67D751069FA23B481BA6FCFDF29422526E47727D21D912C71F7052ED3B4A`;
the `RST+.SQLite3` SHA-256 is
`48F161475699F571E41E3760AEBF7769F6B7D5E6921353C926181676C29E5366`.
The deterministic fetch strips Strong numbers and formatting tags while
retaining RST+ `<J>` boundaries. It never changes app Scripture.

There are 626 mixed KJV semantic authorities. RST+ has red markup on 624
and none on two. Source and target words match in order for 607; 19
wording variants need explicit review. The ledger uses 591 exact-word
projections with matching KJV WJ segment counts and 35 manually reviewed
rows. It includes no reviewed omissions. Matthew 26:56 uses KJV WJ where
RST+ omits red markup. Mark 11:22 moves the `Have faith in God` saying
to Russian Mark 11:23, a separately inherited full-red verse; the
relocation pins the target raw hash and exact Russian phrase.

Manual boundaries exclude the narrator's translation gloss after the
original-language cries in Matthew 27:46, Mark 5:41, Mark 7:34, and Mark
15:34. John 1:42 excludes `что значит камень Петр`; KJV WJ stops at
Cephas even though RST+ colors the gloss. Luke 8:45 marks both the first
question and Peter's verbatim repetition, following KJV WJ. Acts 1:4
starts at the KJV WJ `but wait` clause, excluding the preceding reported
departure command. Revelation 1:8 stops after Alpha/Omega and beginning/end,
despite RST+ coloring the later title; Revelation 1:11 marks only the
retained command because Russian omits KJV's first clause.

The KJV overlay has 1,402 full-red semantic units. RST+ fully colors
1,396 and supplies six partial-red corrections. Exact hash-bound
`overrideInherited` rows keep these narrator clauses uncolored:

- Matthew 19:5: `И сказал:`
- Matthew 25:30: narration between two Jesus turns
- Mark 12:37: the crowd's response
- Luke 7:41: `Иисус сказал:`
- Luke 8:15: narration between two Jesus turns
- Luke 10:22: the speech introduction

Reproduction and offline validation:

```powershell
python -X utf8 tools/traditional/fetch_rst_plus_jesus_evidence.py
python -X utf8 tools/traditional/build_ru_jesus_ledger.py
python -m unittest discover -s tools/traditional -p 'test_build_ru_jesus_ledger.py' -v
```

The fetch script checks the archive and database pins before producing
the report. The ledger builder checks the report hash, KJV candidate
coverage, every target raw-text hash, exact substring, overlap, relocation,
and full-override coverage in memory through `ReviewedJesusSpans`.
