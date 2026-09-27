# Concordant cross-edition reference maps

`concordant_identity_reference_maps.json` extends the reviewed crosswalk at
native-verse boundaries. A coordinate range qualifies only when:

1. Both packaged editions contain the same verse coordinates for every mapped
   unit. Native units may group adjacent coordinates differently; the map
   never splits a combined line in either direction.
2. An earlier reviewed non-identity or special passage map does not touch its
   source or target native unit. Untouched units in the same chapter may map;
   shifted and cross-chapter passages keep their explicit rules.
3. For non-English base editions, the local story hash matches a fresh rendered
   Bible.com parity checkpoint whose status is `match`. The checkpoint binds the
   source chapter and edition; an absent or changed checkpoint fails closed.
4. The traditional overlay has already passed its pinned-source text and
   structure validator. The extension changes references only, not Scripture.

The generator is `audit_reference_identity_candidates.py`. Its checked-in
supplement records a proof-manifest SHA-256 and skipped-chapter counts. Its
text-concordance function is diagnostic, never a gate on exact native passage
identity. The exact still-unresolved native ranges and reason codes appear in
`tools/reports/edition_reference_coverage.json`. A missing map means the app
keeps the reference's source edition. It never assumes that two editions use
the same passage merely because a number appears in both.

This is deliberately partial where native units cannot map cleanly. Japanese
JCB versus Bungo uses many combined JCB units; matching ranges map as whole
units. Coordinates absent on either side or entangled in shifted passages
still need a native passage crosswalk. Existing French, German, Italian,
Russian, Spanish, Portuguese, and Chinese exception chapters remain governed
by their explicit maps. Arabic Luke 7 uses a narrowly pinned publisher
fallback for its verse 15 because Bible.com renders that one SAB verse empty.
The local verse hash matches the International Sharif Bible Society PDF and
Bilughatain, the other 49 verses match the rendered page, and the whole local
story and defective page snapshot are hash-bound. All 50 native coordinates
map to the corresponding Van Dyck passage in both directions.

External Bible.com or BibleGateway links require a recognized provider-native
numbering target. Unknown target numbering now keeps the reference in-app;
known same-edition verse references also pass native-unit validation before an
external link can open. Chapter-only references cannot be converted between
numbering schemes.
