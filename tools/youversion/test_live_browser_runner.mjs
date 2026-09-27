import assert from "node:assert/strict";
import fs from "node:fs/promises";
import test from "node:test";

import {
  CANONICAL_BOOKS,
  EDITIONS,
  buildEvidenceEnvelope,
  canAttemptMissingRangeInsertion,
  canonicalSha256,
  classifyMutationPreflight,
  expectedChapterCount,
  hasExactNativeRangeInventory,
  loadBookSpecs,
  normalizePublisherPresentationOrder,
  validateLanguageInventory,
  validatedNviOmissionSnapshot,
} from "./live_browser_runner.mjs";

test("CCB Isaiah 38 display-order exception moves only complete 21 and 22 groups", () => {
  const record = (verse, fragment = 1) => ({
    kind: "verse", usfm: `ISA.38.${verse}`, html: `${verse}:${fragment}`,
  });
  const heading = { kind: "heading", html: "The illness" };
  const source = [
    heading,
    ...Array.from({ length: 6 }, (_, index) => record(index + 1)),
    record(21), record(21, 2), record(22),
    ...Array.from({ length: 14 }, (_, index) => record(index + 7)),
  ];
  const result = normalizePublisherPresentationOrder(source, "zh-Hans", 36, "ISA", 38);
  assert.equal(result.sourcePresentationNormalization, "CCB.ISA.38.21-22.after-20");
  assert.deepEqual(result.records, [
    heading,
    ...source.slice(1, 7),
    ...source.slice(10),
    ...source.slice(7, 10),
  ]);
  assert.deepEqual(normalizePublisherPresentationOrder(source, "zh-Hant", 139, "ISA", 38), { records: source });
  assert.deepEqual(normalizePublisherPresentationOrder(result.records, "zh-Hans", 36, "ISA", 38), { records: result.records });
  assert.throws(
    () => normalizePublisherPresentationOrder([...source, record(22, 3)], "zh-Hans", 36, "ISA", 38),
    /publisher presentation order changed/,
  );
  assert.throws(
    () => normalizePublisherPresentationOrder(source.filter((item) => item.usfm !== "ISA.38.8"), "zh-Hans", 36, "ISA", 38),
    /publisher presentation order changed/,
  );
  assert.throws(
    () => normalizePublisherPresentationOrder([heading, ...source.slice(1, 7), heading, ...source.slice(7)], "zh-Hans", 36, "ISA", 38),
    /publisher presentation order changed/,
  );
  assert.throws(
    () => normalizePublisherPresentationOrder(source, "zh-Hans", 139, "ISA", 38),
    /edition identity changed/,
  );
});

test("evidence hashes are canonical and bind one snapshot to one current story", () => {
  assert.equal(
    canonicalSha256({ nested: { z: 2, a: 1 }, text: "same" }),
    canonicalSha256({ text: "same", nested: { a: 1, z: 2 } }),
  );
  const snapshot = {
    language: "en",
    book: "GEN",
    chapter: 1,
    pageUrl: "https://www.bible.com/bible/3034/GEN.1.BSB",
    pageTitle: "Genesis 1",
    records: [{ kind: "verse", usfm: "GEN.1.1", html: "In the beginning" }],
  };
  const localStory = {
    id: "genesis-1",
    title: "Genesis 1",
    summaryBullets: ["In the beginning (1:1)"],
    headings: [{ beforeVerse: 1, text: "The Creation" }],
  };
  const envelope = buildEvidenceEnvelope({
    language: "en",
    bibleId: 3034,
    reference: "GEN.1",
    snapshot,
    localStory,
  });
  assert.deepEqual(envelope, {
    evidenceSchemaVersion: 1,
    language: "en",
    bibleId: 3034,
    reference: "GEN.1",
    snapshotSha256: canonicalSha256(snapshot),
    localStorySha256: canonicalSha256(localStory),
  });
  assert.match(envelope.snapshotSha256, /^[0-9a-f]{64}$/);
  assert.notEqual(
    envelope.localStorySha256,
    canonicalSha256({ ...localStory, title: "Changed" }),
  );
  assert.throws(() => buildEvidenceEnvelope({
    language: "en",
    bibleId: 3034,
    reference: "GEN.0",
    snapshot,
    localStory,
  }), /invalid evidence envelope input/);
});

test("canonical inventory is exact and complete for every supported language", async () => {
  assert.equal(CANONICAL_BOOKS.length, 66);
  assert.equal(new Set(CANONICAL_BOOKS.map((spec) => spec.code)).size, 66);
  assert.equal(new Set(CANONICAL_BOOKS.map((spec) => `${spec.collection}/${spec.book}`)).size, 66);
  assert.equal(CANONICAL_BOOKS.reduce((sum, spec) => sum + spec.chapters, 0), 1189);
  assert.equal(expectedChapterCount("de", "JOL"), 4);
  assert.equal(expectedChapterCount("de", "MAL"), 3);
  assert.equal(expectedChapterCount("fr", "JOL"), 4);
  assert.equal(expectedChapterCount("fr", "MAL"), 3);
  assert.equal(expectedChapterCount("en", "JOL"), 3);
  assert.equal(expectedChapterCount("en", "MAL"), 4);

  const parsed = await loadBookSpecs();
  assert.deepEqual(
    parsed,
    CANONICAL_BOOKS.map(({ code, collection, book }) => ({ code, collection, book })),
  );
  for (const language of Object.keys(EDITIONS)) {
    assert.equal(await validateLanguageInventory(language), true);
  }
});

test("repair gates fail closed around native-range structure", () => {
  const sourceOnly = {
    status: "mismatch",
    localRanges: 2,
    sourceRanges: 3,
    findings: [{ kind: "source_range_only" }],
  };
  assert.equal(canAttemptMissingRangeInsertion(sourceOnly), true);
  assert.equal(canAttemptMissingRangeInsertion({
    ...sourceOnly,
    findings: [{ kind: "source_range_only" }, { kind: "local_range_only" }],
  }), false);
  assert.equal(canAttemptMissingRangeInsertion({ ...sourceOnly, sourceRanges: 2 }), false);

  for (const status of ["match", "mismatch"]) {
    assert.equal(hasExactNativeRangeInventory({
      status,
      localRanges: 4,
      sourceRanges: 4,
      findings: status === "match" ? [] : [{ kind: "text_mismatch" }],
    }), true);
  }
  assert.equal(hasExactNativeRangeInventory({
    status: "mismatch",
    localRanges: 4,
    sourceRanges: 4,
    findings: [{ kind: "source_range_only" }],
  }), false);
  assert.equal(hasExactNativeRangeInventory({
    status: "blocked",
    localRanges: 4,
    sourceRanges: 4,
    findings: [],
  }), false);
});

test("empty source ranges are comparator-gated away from text and range mutators", () => {
  assert.deepEqual(classifyMutationPreflight({
    status: "source_defect",
    sourceParityClaimed: false,
    sourceDefectCode: "locked_sab_luk_7_15_empty_native_range",
    sourceDefectRanges: ["LUK.7.15"],
    localSourceDefectVersePreserved: true,
    fallbackSourceVerified: true,
    fallbackSource: {
      url: "https://www.kitabsharif.org/sites/www.kitabsharif.org/files/bshart%20lwqa_0.pdf",
      verseTextSha256: "71dd0d1d776d6b9c87d421c37b2bddaf5049f33eb108442f6825e03e1b8e9f6a",
    },
    sourceOmissions: 0,
    emptyRanges: 1,
  }), { canMutate: false, reason: "validated_source_defect" });
  assert.deepEqual(classifyMutationPreflight({
    status: "source_defect",
    sourceParityClaimed: true,
    sourceDefectCode: "locked_sab_luk_7_15_empty_native_range",
    sourceDefectRanges: ["LUK.7.15"],
    localSourceDefectVersePreserved: true,
    sourceOmissions: 0,
    emptyRanges: 1,
  }), { canMutate: false, reason: "invalid_source_defect_report" });
  assert.deepEqual(classifyMutationPreflight({
    status: "match",
    sourceOmissions: 1,
    emptyRanges: 1,
  }), { canMutate: false, reason: "validated_source_omission" });
  assert.deepEqual(classifyMutationPreflight({
    status: "mismatch",
    sourceOmissions: 2,
    emptyRanges: 2,
  }), { canMutate: false, reason: "validated_source_omission" });
  assert.deepEqual(classifyMutationPreflight({
    status: "match",
    sourceOmissions: 0,
    emptyRanges: 0,
  }), { canMutate: true, reason: null });
  assert.deepEqual(classifyMutationPreflight({ status: "match" }), {
    canMutate: true,
    reason: null,
  });
  for (const result of [
    { status: "blocked" },
    { status: "match", sourceOmissions: 0, emptyRanges: 1 },
    { status: "match", sourceOmissions: 1, emptyRanges: 0 },
    { status: "match", sourceOmissions: 1, emptyRanges: 2 },
    { status: "match", sourceOmissions: -1, emptyRanges: -1 },
    { status: "match", sourceOmissions: "1", emptyRanges: 1 },
  ]) {
    assert.equal(classifyMutationPreflight(result).canMutate, false);
  }
});

test("only exact comparator-proven NVI omissions can reach heading and semantic checks", () => {
  const pageUrl = "https://www.bible.com/bible/128/MRK.9.NVI";
  const snapshot = {
    language: "es", book: "MRK", chapter: 9, pageUrl, pageTitle: "Marcos 9",
    records: [
      { kind: "heading", html: "<span>Heading</span>" },
      { kind: "verse", usfm: "MRK.9.43", html: "<span>43</span>" },
      { kind: "verse", usfm: "MRK.9.44", html: "<span>44</span>" },
      { kind: "verse", usfm: "MRK.9.45", html: "<span>45</span>" },
      { kind: "verse", usfm: "MRK.9.46", html: "<span>46</span>" },
      { kind: "verse", usfm: "MRK.9.47", html: "<span>47</span>" },
    ],
  };
  const parity = {
    language: "es", bibleId: 128, reference: "MRK.9", sourceUrl: pageUrl,
    status: "match", localRanges: 48, sourceRanges: 48,
    sourceOmissions: 2, emptyRanges: 2,
  };
  const verified = validatedNviOmissionSnapshot(parity, snapshot);
  assert.deepEqual(verified.records.map((record) => record.usfm || "heading"), [
    "heading", "MRK.9.43", "MRK.9.45", "MRK.9.47",
  ]);
  assert.equal(snapshot.records.length, 6);
  for (const invalid of [
    { ...parity, sourceOmissions: 1 },
    { ...parity, sourceUrl: "https://www.bible.com/bible/128/MRK.8.NVI" },
    { ...parity, bibleId: 999 },
    { ...parity, localRanges: 47 },
    { ...parity, status: "blocked" },
  ]) {
    assert.throws(() => validatedNviOmissionSnapshot(invalid, snapshot));
  }
  assert.throws(() => validatedNviOmissionSnapshot(parity, {
    ...snapshot,
    records: snapshot.records.filter((record) => record.usfm !== "MRK.9.46"),
  }));
  assert.throws(() => validatedNviOmissionSnapshot(parity, {
    ...snapshot, language: "ko",
  }));
});

test("pipeline orders structural repairs before text and mandatory semantics", async () => {
  const source = await fs.readFile(new URL("./live_browser_runner.mjs", import.meta.url), "utf8");
  const preflight = source.indexOf("let before = await postSnapshot(9317, snapshot)");
  const omissionGate = source.indexOf("const gate = classifyMutationPreflight(before)");
  const headings = source.indexOf("heading = await postSnapshot(HEADING_PORT, snapshot)");
  assert.ok(preflight >= 0 && omissionGate > preflight && headings > omissionGate);
  const stages = ["9323", "9321", "9320", "SCRIPTURE_PORT", "SEMANTIC_PORT"]
    .map((port) => source.indexOf(`postSnapshot(${port}, snapshot)`));
  assert.ok(stages.every((offset) => offset >= 0));
  assert.deepEqual([...stages].sort((left, right) => left - right), stages);
  assert.match(source, /if \(hasExactNativeRangeInventory\(parity\)\)/);
  assert.match(source, /heading = \{ \.\.\.heading, \.\.\.evidence \}/);
  assert.match(source, /parity = \{ \.\.\.parity, \.\.\.evidence \}/);
  assert.doesNotMatch(source, /oldParity\s*&&\s*oldHeading/);
  assert.match(source, /postSnapshot\(HEADING_PORT, verified\)/);
  assert.match(source, /postSnapshot\(SEMANTIC_PORT, verified\)/);
  assert.doesNotMatch(source, /postSnapshot\((?:9320|9321|9323|SCRIPTURE_PORT), verified\)/);
  assert.match(source, /gate\.reason === "validated_source_defect"/);
});
