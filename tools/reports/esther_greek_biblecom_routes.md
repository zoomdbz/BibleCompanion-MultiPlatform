# Greek Esther Bible.com route audit

Checked against the 21 English `esther_greek.json` story boundaries and live Bible.com pages on 2026-09-25. The local first and last verse numbers follow NRSVUE exactly. BFC uses six lettered additions (A-F) and continuous verse numbers across splits. DHH94I embeds the additions as lettered subverses inside ten numbered chapters. A Bible.com HTTP 200 alone does not prove a route exists: absent routes such as BFC/DHH94I `ESG.18` return an empty "Bible - Bible App" page.

| Local segment | Local verses | NRSVUE | BFC route and matching verses | DHH94I |
| --- | --- | --- | --- | --- |
| 1 | 2-12, Mordecai's dream | [1](https://www.bible.com/bible/3523/ESG.1.NRSVUE) | [1_1](https://www.bible.com/bible/63/ESG.1_1.BFC), A 1-11 | [1](https://www.bible.com/bible/52/ESG.1.DHH94I) |
| 2 | 1-6, plot against king | [2](https://www.bible.com/bible/3523/ESG.2.NRSVUE) | [1_1](https://www.bible.com/bible/63/ESG.1_1.BFC), A 12-17 | [1](https://www.bible.com/bible/52/ESG.1.DHH94I) |
| 3 | 1-22, Vashti | [3](https://www.bible.com/bible/3523/ESG.3.NRSVUE) | [1_2](https://www.bible.com/bible/63/ESG.1_2.BFC), 1:18-39 | [1](https://www.bible.com/bible/52/ESG.1.DHH94I) |
| 4 | 1-23, Esther becomes queen | [4](https://www.bible.com/bible/3523/ESG.4.NRSVUE) | [2](https://www.bible.com/bible/63/ESG.2.BFC), 2:1-23 | [2](https://www.bible.com/bible/52/ESG.2.DHH94I) |
| 5 | 1-13, Haman's decree | [5](https://www.bible.com/bible/3523/ESG.5.NRSVUE) | [3](https://www.bible.com/bible/63/ESG.3.BFC), 3:1-13 | [3](https://www.bible.com/bible/52/ESG.3.DHH94I) |
| 6 | 1-7, decree text | [6](https://www.bible.com/bible/3523/ESG.6.NRSVUE) | [3_1](https://www.bible.com/bible/63/ESG.3_1.BFC), B 14-20 | [3](https://www.bible.com/bible/52/ESG.3.DHH94I) |
| 7 | 14-15, decree posted | [7](https://www.bible.com/bible/3523/ESG.7.NRSVUE) | [3_2](https://www.bible.com/bible/63/ESG.3_2.BFC), 3:21-22 | [3](https://www.bible.com/bible/52/ESG.3.DHH94I) |
| 8 | 1-17, Mordecai and Esther | [8](https://www.bible.com/bible/3523/ESG.8.NRSVUE) | [4](https://www.bible.com/bible/63/ESG.4.BFC), 4:1-17 | [4](https://www.bible.com/bible/52/ESG.4.DHH94I) |
| 9 | 8-18, Mordecai's prayer | [9](https://www.bible.com/bible/3523/ESG.9.NRSVUE) | [4_1](https://www.bible.com/bible/63/ESG.4_1.BFC), C 18-28 | [4](https://www.bible.com/bible/52/ESG.4.DHH94I) |
| 10 | 1-19, Esther's prayer | [10](https://www.bible.com/bible/3523/ESG.10.NRSVUE) | [4_1](https://www.bible.com/bible/63/ESG.4_1.BFC), C 29-47 | [4](https://www.bible.com/bible/52/ESG.4.DHH94I) |
| 11 | 1-16, Esther enters court | [11](https://www.bible.com/bible/3523/ESG.11.NRSVUE) | [5_1](https://www.bible.com/bible/63/ESG.5_1.BFC), D 1-16 | [5](https://www.bible.com/bible/52/ESG.5.DHH94I) |
| 12 | 3-14, Esther and Haman | [12](https://www.bible.com/bible/3523/ESG.12.NRSVUE) | [5_2](https://www.bible.com/bible/63/ESG.5_2.BFC), 5:17-28 | [5](https://www.bible.com/bible/52/ESG.5.DHH94I) |
| 13 | 1-14, king's sleepless night | [13](https://www.bible.com/bible/3523/ESG.13.NRSVUE) | [6](https://www.bible.com/bible/63/ESG.6.BFC), 6:1-14 | [6](https://www.bible.com/bible/52/ESG.6.DHH94I) |
| 14 | 1-10, Haman falls | [14](https://www.bible.com/bible/3523/ESG.14.NRSVUE) | [7](https://www.bible.com/bible/63/ESG.7.BFC), 7:1-10 | [7](https://www.bible.com/bible/52/ESG.7.DHH94I) |
| 15 | 1-12, new decree ordered | [15](https://www.bible.com/bible/3523/ESG.15.NRSVUE) | [8](https://www.bible.com/bible/63/ESG.8.BFC), 8:1-12 | [8](https://www.bible.com/bible/52/ESG.8.DHH94I) |
| 16 | 1-24, new decree text | [16](https://www.bible.com/bible/3523/ESG.16.NRSVUE) | [8_1](https://www.bible.com/bible/63/ESG.8_1.BFC), E 13-36 | [8](https://www.bible.com/bible/52/ESG.8.DHH94I) |
| 17 | 13-17, new decree posted | [17](https://www.bible.com/bible/3523/ESG.17.NRSVUE) | [8_2](https://www.bible.com/bible/63/ESG.8_2.BFC), 8:37-41 | [8](https://www.bible.com/bible/52/ESG.8.DHH94I) |
| 18 | 1-32, Purim | [18](https://www.bible.com/bible/3523/ESG.18.NRSVUE) | [9](https://www.bible.com/bible/63/ESG.9.BFC), 9:1-32 | [9](https://www.bible.com/bible/52/ESG.9.DHH94I) |
| 19 | 1-3, Mordecai honored | [19](https://www.bible.com/bible/3523/ESG.19.NRSVUE) | [10](https://www.bible.com/bible/63/ESG.10.BFC), 10:1-3 | [10](https://www.bible.com/bible/52/ESG.10.DHH94I) |
| 20 | 4-13, dream explained | [20](https://www.bible.com/bible/3523/ESG.20.NRSVUE) | [10_1](https://www.bible.com/bible/63/ESG.10_1.BFC), F 4-13 | [10](https://www.bible.com/bible/52/ESG.10.DHH94I) |
| 21 | 1, closing colophon | [21](https://www.bible.com/bible/3523/ESG.21.NRSVUE) | [10_1](https://www.bible.com/bible/63/ESG.10_1.BFC), F 14 | [10](https://www.bible.com/bible/52/ESG.10.DHH94I) |

NRSVUE verse links keep local verse numbers. BFC verse links apply the reviewed per-segment offsets in `Linker.kt`; the first BFC A verse combines local 1:2-3. DHH94I verse links are retained only where the canonical numeric verse matches. Its lettered additions and the mixed first chapter use chapter-only links, preventing an invented numeric anchor from selecting unrelated text. Other configured editions are not assumed to share these maps and retain the existing fallback behavior.
