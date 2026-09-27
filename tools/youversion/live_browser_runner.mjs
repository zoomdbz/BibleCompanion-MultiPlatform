import { Buffer } from "node:buffer";
import { createHash } from "node:crypto";
import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.resolve(HERE, "..", "..");

export const EDITIONS = {
  en: { id: 3034, abbr: "BSB" },
  de: { id: 157, abbr: "SCH2000" },
  es: { id: 128, abbr: "NVI" },
  fr: { id: 104, abbr: "NBS" },
  it: { id: 122, abbr: "NR06" },
  pt: { id: 1930, abbr: "NVT" },
  ru: { id: 143, abbr: "NRT" },
  ja: { id: 83, abbr: "JCB" },
  ko: { id: 142, abbr: "RNKSV" },
  "zh-Hans": { id: 36, abbr: "CCB" },
  "zh-Hant": { id: 139, abbr: "RCUV" },
  ar: { id: 153, abbr: "SAB" },
  hi: { id: 1980, abbr: "IRVHIN" },
};

// Keep the production defaults stable while allowing an audit session to
// isolate freshly restarted mutators from an older local process that still
// owns the default port.
export let SCRIPTURE_PORT = 9319;
export let SEMANTIC_PORT = 9322;
export let HEADING_PORT = 9318;

export function configureAuditPorts({
  scripturePort = 9319,
  semanticPort = 9322,
  headingPort = 9318,
} = {}) {
  for (const port of [scripturePort, semanticPort, headingPort]) {
    if (!Number.isInteger(port) || port < 1024 || port > 65535) {
      throw new Error("invalid local audit port");
    }
  }
  SCRIPTURE_PORT = scripturePort;
  SEMANTIC_PORT = semanticPort;
  HEADING_PORT = headingPort;
}

// Authoritative Protestant 66-book inventory. SCH2000 and NBS use the
// four-chapter Hebrew layout for Joel and combine English Malachi 3-4 into
// three chapters; the override below preserves their native chapter counts.
export const CANONICAL_BOOKS = Object.freeze([
  ["GEN", "old_testament", "genesis", 50],
  ["EXO", "old_testament", "exodus", 40],
  ["LEV", "old_testament", "leviticus", 27],
  ["NUM", "old_testament", "numbers", 36],
  ["DEU", "old_testament", "deuteronomy", 34],
  ["JOS", "old_testament", "joshua", 24],
  ["JDG", "old_testament", "judges", 21],
  ["RUT", "old_testament", "ruth", 4],
  ["1SA", "old_testament", "1_samuel", 31],
  ["2SA", "old_testament", "2_samuel", 24],
  ["1KI", "old_testament", "1_kings", 22],
  ["2KI", "old_testament", "2_kings", 25],
  ["1CH", "old_testament", "1_chronicles", 29],
  ["2CH", "old_testament", "2_chronicles", 36],
  ["EZR", "old_testament", "ezra", 10],
  ["NEH", "old_testament", "nehemiah", 13],
  ["EST", "old_testament", "esther", 10],
  ["JOB", "old_testament", "job", 42],
  ["PSA", "old_testament", "psalms", 150],
  ["PRO", "old_testament", "proverbs", 31],
  ["ECC", "old_testament", "ecclesiastes", 12],
  ["SNG", "old_testament", "song_of_songs", 8],
  ["ISA", "old_testament", "isaiah", 66],
  ["JER", "old_testament", "jeremiah", 52],
  ["LAM", "old_testament", "lamentations", 5],
  ["EZK", "old_testament", "ezekiel", 48],
  ["DAN", "old_testament", "daniel", 12],
  ["HOS", "old_testament", "hosea", 14],
  ["JOL", "old_testament", "joel", 3],
  ["AMO", "old_testament", "amos", 9],
  ["OBA", "old_testament", "obadiah", 1],
  ["JON", "old_testament", "jonah", 4],
  ["MIC", "old_testament", "micah", 7],
  ["NAM", "old_testament", "nahum", 3],
  ["HAB", "old_testament", "habakkuk", 3],
  ["ZEP", "old_testament", "zephaniah", 3],
  ["HAG", "old_testament", "haggai", 2],
  ["ZEC", "old_testament", "zechariah", 14],
  ["MAL", "old_testament", "malachi", 4],
  ["MAT", "new_testament", "matthew", 28],
  ["MRK", "new_testament", "mark", 16],
  ["LUK", "new_testament", "luke", 24],
  ["JHN", "new_testament", "john", 21],
  ["ACT", "new_testament", "acts", 28],
  ["ROM", "new_testament", "romans", 16],
  ["1CO", "new_testament", "1_corinthians", 16],
  ["2CO", "new_testament", "2_corinthians", 13],
  ["GAL", "new_testament", "galatians", 6],
  ["EPH", "new_testament", "ephesians", 6],
  ["PHP", "new_testament", "philippians", 4],
  ["COL", "new_testament", "colossians", 4],
  ["1TH", "new_testament", "1_thessalonians", 5],
  ["2TH", "new_testament", "2_thessalonians", 3],
  ["1TI", "new_testament", "1_timothy", 6],
  ["2TI", "new_testament", "2_timothy", 4],
  ["TIT", "new_testament", "titus", 3],
  ["PHM", "new_testament", "philemon", 1],
  ["HEB", "new_testament", "hebrews", 13],
  ["JAS", "new_testament", "james", 5],
  ["1PE", "new_testament", "1_peter", 5],
  ["2PE", "new_testament", "2_peter", 3],
  ["1JN", "new_testament", "1_john", 5],
  ["2JN", "new_testament", "2_john", 1],
  ["3JN", "new_testament", "3_john", 1],
  ["JUD", "new_testament", "jude", 1],
  ["REV", "new_testament", "revelation", 22],
].map(([code, collection, book, chapters]) => Object.freeze({ code, collection, book, chapters })));

const CANONICAL_BY_CODE = new Map(CANONICAL_BOOKS.map((spec) => [spec.code, spec]));
const inventoryValidation = new Map();
const ASSET_ID_OVERRIDES = Object.freeze({
  "1_samuel": "1-samuel",
  "2_samuel": "2-samuel",
  "1_kings": "1-kings",
  "2_kings": "2-kings",
  "1_chronicles": "1chronicles",
  "2_chronicles": "2chronicles",
  song_of_songs: "song-of-songs",
  "1_corinthians": "1-corinthians",
});

export function expectedChapterCount(language, code) {
  if (!Object.hasOwn(EDITIONS, language)) throw new Error("unsupported local language inventory");
  const canonical = CANONICAL_BY_CODE.get(code);
  if (!canonical) throw new Error("unsupported canonical book inventory");
  if ((language === "de" || language === "fr") && code === "JOL") return 4;
  if ((language === "de" || language === "fr") && code === "MAL") return 3;
  return canonical.chapters;
}

export const PARITY_ROOT = path.join(ROOT, ".scripture-structure-cache", "live-browser-parity-2026-09-26");
export const HEADING_ROOT = path.join(ROOT, ".scripture-structure-cache", "live-browser-headings-2026-09-26");
export const SCRIPTURE_ROOT = path.join(ROOT, ".scripture-structure-cache", "live-browser-scripture-2026-09-26");
export const RANGE_ROOT = path.join(ROOT, ".scripture-structure-cache", "live-browser-ranges-2026-09-26");
export const MERGE_ROOT = path.join(ROOT, ".scripture-structure-cache", "live-browser-range-merges-2026-09-26");
export const SEMANTIC_ROOT = path.join(ROOT, ".scripture-structure-cache", "live-browser-semantics-2026-09-26");
export const MISSING_RANGE_ROOT = path.join(ROOT, ".scripture-structure-cache", "live-browser-missing-ranges-2026-09-26");

const MARKER = /\s*\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\.?\s*$/;

function canonicalJson(value) {
  if (value === null || typeof value === "string" || typeof value === "boolean") {
    return JSON.stringify(value);
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw new Error("evidence contains a non-finite number");
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => canonicalJson(item)).join(",")}]`;
  }
  if (value && typeof value === "object") {
    const keys = Object.keys(value).sort();
    return `{${keys.map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(",")}}`;
  }
  throw new Error("evidence contains a non-JSON value");
}

export function canonicalSha256(value) {
  return createHash("sha256").update(canonicalJson(value), "utf8").digest("hex");
}

export function buildEvidenceEnvelope({ language, bibleId, reference, snapshot, localStory }) {
  if (!Object.hasOwn(EDITIONS, language)
      || EDITIONS[language].id !== bibleId
      || typeof reference !== "string"
      || !/^[A-Z0-9]{3}\.[1-9]\d*$/.test(reference)
      || !snapshot || typeof snapshot !== "object" || Array.isArray(snapshot)
      || !localStory || typeof localStory !== "object" || Array.isArray(localStory)) {
    throw new Error("invalid evidence envelope input");
  }
  return {
    evidenceSchemaVersion: 1,
    language,
    bibleId,
    reference,
    snapshotSha256: canonicalSha256(snapshot),
    localStorySha256: canonicalSha256(localStory),
  };
}

async function evidenceForChapter(language, edition, spec, chapter, snapshot) {
  const canonical = CANONICAL_BY_CODE.get(spec.code);
  if (!canonical || spec.collection !== canonical.collection || spec.book !== canonical.book
      || chapter < 1 || chapter > expectedChapterCount(language, spec.code)) {
    throw new Error("noncanonical evidence chapter");
  }
  const file = path.join(ROOT, "shared", "assets", "books", spec.collection, language, `${spec.book}.json`);
  const payload = JSON.parse((await fs.readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const assetId = ASSET_ID_OVERRIDES[spec.book] || spec.book;
  if (!payload || payload.id !== assetId || !Array.isArray(payload.stories)) {
    throw new Error("local evidence book identity mismatch");
  }
  const matches = payload.stories.filter((story) => story && story.id === `${assetId}-${chapter}`);
  if (matches.length !== 1) throw new Error("local evidence chapter identity mismatch");
  return buildEvidenceEnvelope({
    language,
    bibleId: edition.id,
    reference: `${spec.code}.${chapter}`,
    snapshot,
    localStory: matches[0],
  });
}

export async function loadBook(language, spec) {
  const canonical = CANONICAL_BY_CODE.get(spec.code);
  if (!canonical || spec.collection !== canonical.collection || spec.book !== canonical.book) {
    throw new Error("noncanonical book specification");
  }
  const file = path.join(ROOT, "shared", "assets", "books", spec.collection, language, `${spec.book}.json`);
  const payload = JSON.parse((await fs.readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const assetId = ASSET_ID_OVERRIDES[spec.book] || spec.book;
  if (!payload || payload.id !== assetId) throw new Error("local book identity mismatch");
  if (!Array.isArray(payload.stories)) throw new Error("local book lacks stories inventory");
  const chapters = new Set();
  for (const story of payload.stories) {
    if (!story || !Array.isArray(story.summaryBullets) || story.summaryBullets.length === 0) {
      throw new Error("local chapter lacks Scripture units");
    }
    let chapter = null;
    for (const bullet of story.summaryBullets) {
      if (typeof bullet !== "string") throw new Error("local Scripture unit is not text");
      const match = bullet.match(MARKER);
      if (!match) throw new Error("local marker parse failed");
      const current = Number(match[1]);
      if (chapter === null) chapter = current;
      if (chapter !== current) throw new Error("mixed local chapter");
    }
    if (chapter === null || chapters.has(chapter)) throw new Error("duplicate or empty local chapter");
    if (story.id !== `${assetId}-${chapter}`) throw new Error("local chapter identity mismatch");
    chapters.add(chapter);
  }
  const actual = [...chapters].sort((a, b) => a - b);
  const expected = Array.from({ length: expectedChapterCount(language, spec.code) }, (_, index) => index + 1);
  if (actual.length !== expected.length || actual.some((chapter, index) => chapter !== expected[index])) {
    throw new Error("local chapter inventory is incomplete or noncanonical");
  }
  return actual;
}

export async function loadBookSpecs() {
  const source = await fs.readFile(path.join(ROOT, "tools", "audit_scripture_sources.py"), "utf8");
  const matches = [...source.matchAll(/\("([A-Z0-9]{3})",\s*"([^"]+)",\s*"([^"]+)"\)/g)];
  const specs = matches.map((match) => ({ code: match[1], collection: match[2], book: match[3] }));
  if (specs.length !== CANONICAL_BOOKS.length) throw new Error("canonical book inventory mismatch");
  const codes = new Set(specs.map((spec) => spec.code));
  const paths = new Set(specs.map((spec) => `${spec.collection}/${spec.book}`));
  if (codes.size !== CANONICAL_BOOKS.length || paths.size !== CANONICAL_BOOKS.length) {
    throw new Error("canonical book inventory contains a duplicate");
  }
  for (let index = 0; index < CANONICAL_BOOKS.length; index += 1) {
    const actual = specs[index];
    const expected = CANONICAL_BOOKS[index];
    if (actual.code !== expected.code || actual.collection !== expected.collection || actual.book !== expected.book) {
      throw new Error("canonical book specification inventory differs");
    }
  }
  return specs;
}

export async function validateLanguageInventory(language) {
  if (!inventoryValidation.has(language)) {
    inventoryValidation.set(language, (async () => {
      const specs = await loadBookSpecs();
      const inventories = await Promise.all(specs.map((spec) => loadBook(language, spec)));
      const total = inventories.reduce((sum, chapters) => sum + chapters.length, 0);
      if (total !== 1189) throw new Error("canonical language chapter total mismatch");
      return true;
    })());
  }
  return inventoryValidation.get(language);
}

export function normalizePublisherPresentationOrder(records, language, editionId, code, chapter) {
  if (language !== "zh-Hans" || code !== "ISA" || chapter !== 38) {
    return { records };
  }
  if (editionId !== 36) throw new Error("CCB Isaiah 38 edition identity changed");
  // CCB renders Isaiah 38:21-22 immediately after verse 6, before 7-20.
  // The numbered units are complete; only the publisher's display order is
  // noncanonical. Move the two whole rendered verse groups for the audit,
  // without changing their text, markup, identifiers, or app verse markers.
  const verses = records.filter((record) => record.kind === "verse");
  const order = verses.map((record) => record.usfm).filter((value, index, all) => index === 0 || value !== all[index - 1]);
  const canonical = Array.from({ length: 22 }, (_, index) => `ISA.38.${index + 1}`);
  if (JSON.stringify(order) === JSON.stringify(canonical)) return { records };
  const observed = [
    ...canonical.slice(0, 6), "ISA.38.21", "ISA.38.22", ...canonical.slice(6, 20),
  ];
  const firstVerse = records.findIndex((record) => record.kind === "verse");
  if (JSON.stringify(order) !== JSON.stringify(observed)
      || firstVerse !== 1
      || records[0].kind !== "heading"
      || records.slice(firstVerse).some((record) => record.kind !== "verse")) {
    throw new Error("CCB Isaiah 38 publisher presentation order changed");
  }
  const moved = records.filter((record) => record.usfm === "ISA.38.21" || record.usfm === "ISA.38.22");
  const retained = records.filter((record) => record.usfm !== "ISA.38.21" && record.usfm !== "ISA.38.22");
  return {
    records: [...retained, ...moved],
    sourcePresentationNormalization: "CCB.ISA.38.21-22.after-20",
  };
}

export async function snapshotFor(target, language, code, chapter) {
  const capturedRecords = await target.playwright
    .locator('span[class*="__heading"], span[class*="__verse"][data-usfm]')
    .evaluateAll((elements) => elements.map((element) => {
      const heading = String(element.className).includes("__heading");
      return {
        kind: heading ? "heading" : "verse",
        ...(heading ? {} : { usfm: element.getAttribute("data-usfm") || "" }),
        html: element.outerHTML,
      };
    }));
  const normalized = normalizePublisherPresentationOrder(
    capturedRecords, language, EDITIONS[language]?.id, code, chapter,
  );
  return {
    language,
    book: code,
    chapter,
    pageUrl: await target.url(),
    pageTitle: await target.title(),
    ...normalized,
  };
}

export async function postSnapshot(port, snapshot) {
  const body = JSON.stringify(snapshot);
  return await new Promise((resolve, reject) => {
    const request = http.request({
      hostname: "127.0.0.1",
      port,
      path: "/snapshot",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(body),
      },
    }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => {
        try {
          resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
        } catch (error) {
          reject(error);
        }
      });
    });
    request.on("error", reject);
    request.end(body);
  });
}

async function readCheckpoint(file) {
  try {
    return JSON.parse(await fs.readFile(file, "utf8"));
  } catch (error) {
    if (error && error.code === "ENOENT") return null;
    throw error;
  }
}

async function writeCheckpoint(file, value) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function checkpointPaths(language, code, chapter) {
  return {
    parity: path.join(PARITY_ROOT, language, `${code}.${chapter}.json`),
    heading: path.join(HEADING_ROOT, language, `${code}.${chapter}.json`),
    scripture: path.join(SCRIPTURE_ROOT, language, `${code}.${chapter}.json`),
    range: path.join(RANGE_ROOT, language, `${code}.${chapter}.json`),
    merge: path.join(MERGE_ROOT, language, `${code}.${chapter}.json`),
    semantic: path.join(SEMANTIC_ROOT, language, `${code}.${chapter}.json`),
    missingRange: path.join(MISSING_RANGE_ROOT, language, `${code}.${chapter}.json`),
  };
}

function rangeFindingKinds(value) {
  return new Set((value.findings || [])
    .map((finding) => String(finding.kind || ""))
    .filter((kind) => kind === "local_range_only" || kind === "source_range_only"));
}

function hasRangeFinding(value) {
  return value.status === "mismatch" && rangeFindingKinds(value).size > 0;
}

export function canAttemptMissingRangeInsertion(value) {
  if (value.status !== "mismatch") return false;
  const kinds = rangeFindingKinds(value);
  return Number(value.sourceRanges) > Number(value.localRanges)
    && kinds.has("source_range_only")
    && !kinds.has("local_range_only");
}

export function hasExactNativeRangeInventory(value) {
  const localRanges = Number(value.localRanges);
  const sourceRanges = Number(value.sourceRanges);
  return (value.status === "match" || value.status === "mismatch")
    && Number.isInteger(localRanges)
    && localRanges > 0
    && localRanges === sourceRanges
    && rangeFindingKinds(value).size === 0;
}

export function classifyMutationPreflight(value) {
  if (value?.status === "source_defect") {
    const exact = value.sourceParityClaimed === false
      && value.sourceDefectCode === "locked_sab_luk_7_15_empty_native_range"
      && JSON.stringify(value.sourceDefectRanges) === JSON.stringify(["LUK.7.15"])
      && value.localSourceDefectVersePreserved === true
      && value.fallbackSourceVerified === true
      && value.fallbackSource?.url === "https://www.kitabsharif.org/sites/www.kitabsharif.org/files/bshart%20lwqa_0.pdf"
      && value.fallbackSource?.verseTextSha256 === "71dd0d1d776d6b9c87d421c37b2bddaf5049f33eb108442f6825e03e1b8e9f6a"
      && value.sourceOmissions === 0
      && value.emptyRanges === 1;
    return {
      canMutate: false,
      reason: exact ? "validated_source_defect" : "invalid_source_defect_report",
    };
  }
  if (!value || !["match", "mismatch"].includes(value.status)) {
    return { canMutate: false, reason: "comparator_blocked" };
  }
  const sourceOmissions = value.sourceOmissions ?? 0;
  const emptyRanges = value.emptyRanges ?? 0;
  if (!Number.isInteger(sourceOmissions)
      || !Number.isInteger(emptyRanges)
      || sourceOmissions < 0
      || emptyRanges < 0) {
    return { canMutate: false, reason: "invalid_empty_range_report" };
  }
  if (sourceOmissions === 0 && emptyRanges === 0) {
    return { canMutate: true, reason: null };
  }
  if (sourceOmissions > 0 && sourceOmissions === emptyRanges) {
    return { canMutate: false, reason: "validated_source_omission" };
  }
  return { canMutate: false, reason: "unvalidated_empty_range_report" };
}

// The read-only comparator independently checks each empty NVI source verse
// against the locked manuscript-variant note in the local chapter. Only these
// exact, publisher-validated omissions may be removed from a *derived* DOM
// snapshot used for heading and semantic checks. The original snapshot stays
// authoritative for parity and its evidence envelope.
const NVI_LOCKED_OMISSION_VERSES = Object.freeze({
  "MAT.17": [21], "MAT.18": [11], "MAT.23": [14],
  "MRK.7": [16], "MRK.9": [44, 46], "MRK.11": [26], "MRK.15": [28],
  "LUK.17": [36], "LUK.23": [17], "JHN.5": [4],
  "ACT.8": [37], "ACT.15": [34], "ACT.24": [7], "ACT.28": [29],
  "ROM.16": [24],
});

export function validatedNviOmissionSnapshot(parity, snapshot) {
  const reference = `${snapshot?.book}.${snapshot?.chapter}`;
  const omissions = NVI_LOCKED_OMISSION_VERSES[reference];
  if (!omissions || snapshot?.language !== "es"
      || parity?.language !== "es" || parity?.bibleId !== 128
      || parity?.reference !== reference || parity?.sourceUrl !== snapshot?.pageUrl
      || !["match", "mismatch"].includes(parity?.status)
      || parity?.sourceOmissions !== omissions.length
      || parity?.emptyRanges !== omissions.length
      || !Number.isInteger(parity?.localRanges)
      || parity.localRanges !== parity.sourceRanges
      || !Array.isArray(snapshot?.records)) {
    throw new Error("NVI omission comparator proof is incomplete");
  }
  const excluded = new Set(omissions.map((verse) => `${reference}.${verse}`));
  const removed = new Set();
  const records = snapshot.records.filter((record) => {
    if (record?.kind !== "verse" || !excluded.has(record.usfm)) return true;
    removed.add(record.usfm);
    return false;
  });
  if (removed.size !== excluded.size || records.length === snapshot.records.length) {
    throw new Error("NVI omission source records do not match locked proof");
  }
  return { ...snapshot, records };
}

function notNeeded(language, spec, chapter) {
  return { status: "not_needed", mode: "apply", language, book: spec.code, chapter };
}

function gatedResult(language, spec, chapter, gate) {
  if (gate.reason === "validated_source_omission") {
    return { ...notNeeded(language, spec, chapter), reason: gate.reason };
  }
  return {
    status: "blocked",
    mode: "apply",
    language,
    book: spec.code,
    chapter,
    blocker: gate.reason,
  };
}

async function processChapter(target, language, spec, chapter) {
  const edition = EDITIONS[language];
  let evidence = null;
  let heading = null;
  let merge = null;
  let missingRange = null;
  let range = null;
  let scripture = null;
  let semantic = null;
  let parity = null;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const url = `https://www.bible.com/bible/${edition.id}/${spec.code}.${chapter}.${edition.abbr}`;
      if ((await target.url()) !== url) await target.goto(url);
      else if (attempt > 0) await target.reload();
      // Accessibility refreshes can become unavailable after a long browser
      // audit even while the rendered page and Playwright bridge remain
      // healthy. Wait on the rendered Scripture DOM instead; this remains
      // fail closed when the requested chapter never produces a native verse.
      await target.playwright
        .locator('span[class*="__verse"][data-usfm]')
        .first()
        .waitFor({ state: "attached", timeoutMs: 10_000 });
      const snapshot = await snapshotFor(target, language, spec.code, chapter);
      // The read-only comparator is the sole authority for empty native
      // ranges. Exact NVI omissions are validated there against the locked
      // local manuscript note. Never send a snapshot containing an empty
      // source range to a heading, structure, text, or semantic mutator.
      let before = await postSnapshot(9317, snapshot);
      const gate = classifyMutationPreflight(before);
      if (gate.reason === "validated_source_defect") {
        const result = {
          ...notNeeded(language, spec, chapter),
          reason: before.sourceDefectCode,
        };
        heading = result;
        merge = result;
        missingRange = result;
        range = result;
        scripture = result;
        semantic = result;
        parity = before;
        evidence = await evidenceForChapter(language, edition, spec, chapter, snapshot);
        break;
      }
      if (gate.reason === "validated_source_omission") {
        const verified = validatedNviOmissionSnapshot(before, snapshot);
        // Never send the derived snapshot to text, range, merge, or missing-
        // range writers. Those endpoints must see the complete rendered page.
        merge = notNeeded(language, spec, chapter);
        missingRange = notNeeded(language, spec, chapter);
        range = notNeeded(language, spec, chapter);
        scripture = notNeeded(language, spec, chapter);
        heading = await postSnapshot(HEADING_PORT, verified);
        parity = await postSnapshot(9317, snapshot);
        if (heading.status !== "blocked" && parity.status === "match"
            && hasExactNativeRangeInventory(parity)) {
          semantic = await postSnapshot(SEMANTIC_PORT, verified);
          parity = await postSnapshot(9317, snapshot);
        } else {
          semantic = notNeeded(language, spec, chapter);
        }
        if (parity.status !== "blocked") {
          evidence = await evidenceForChapter(language, edition, spec, chapter, snapshot);
          break;
        }
        continue;
      }
      if (!gate.canMutate) {
        const result = gatedResult(language, spec, chapter, gate);
        heading = result;
        merge = result;
        missingRange = result;
        range = result;
        scripture = result;
        semantic = result;
        parity = gate.reason === "validated_source_omission"
          ? before
          : before.status === "blocked"
            ? before
            : {
              language,
              reference: `${spec.code}.${chapter}`,
              status: "blocked",
              blocker: gate.reason,
            };
        if (parity.status !== "blocked") {
          evidence = await evidenceForChapter(language, edition, spec, chapter, snapshot);
          break;
        }
        continue;
      }
      heading = await postSnapshot(HEADING_PORT, snapshot);
      before = await postSnapshot(9317, snapshot);
      // The insertion endpoint independently proves exact ordered-subset
      // membership before it can write. The comparator gate ensures that we
      // only ask it to evaluate source-only native units.
      if (canAttemptMissingRangeInsertion(before)) {
        missingRange = await postSnapshot(9323, snapshot);
        before = await postSnapshot(9317, snapshot);
      } else {
        missingRange = notNeeded(language, spec, chapter);
      }
      const needsMerge = hasRangeFinding(before)
        && Number(before.localRanges) > Number(before.sourceRanges);
      if (needsMerge) {
        merge = await postSnapshot(9321, snapshot);
        before = await postSnapshot(9317, snapshot);
      } else {
        merge = notNeeded(language, spec, chapter);
      }
      const needsRange = before.status === "mismatch"
        && hasRangeFinding(before)
        && Number(before.localRanges) === Number(before.sourceRanges);
      if (needsRange) {
        range = await postSnapshot(9320, snapshot);
        before = await postSnapshot(9317, snapshot);
      } else {
        range = notNeeded(language, spec, chapter);
      }
      const needsScripture = before.status === "mismatch"
        && (before.findings || []).some((finding) => !String(finding.kind || "").startsWith("heading_"));
      if (needsScripture) {
        scripture = await postSnapshot(SCRIPTURE_PORT, snapshot);
        parity = await postSnapshot(9317, snapshot);
      } else {
        scripture = notNeeded(language, spec, chapter);
        parity = before;
      }
      // Audit semantic spans even when plain-text parity already matches.
      // The endpoint independently proves ordered range equality before any
      // write, so an equal-count comparator result is only a coarse gate.
      if (hasExactNativeRangeInventory(parity)) {
        semantic = await postSnapshot(SEMANTIC_PORT, snapshot);
        parity = await postSnapshot(9317, snapshot);
      } else {
        semantic = notNeeded(language, spec, chapter);
      }
      if (parity.status !== "blocked") {
        evidence = await evidenceForChapter(language, edition, spec, chapter, snapshot);
        break;
      }
    } catch (error) {
      evidence = null;
      const errorType = error && error.name ? error.name : "BrowserAuditError";
      heading = { status: "blocked", errorType };
      merge = { status: "blocked", errorType };
      missingRange = { status: "blocked", errorType };
      range = { status: "blocked", errorType };
      scripture = { status: "blocked", errorType };
      semantic = { status: "blocked", errorType };
      parity = { language, reference: `${spec.code}.${chapter}`, status: "blocked", blocker: errorType };
    }
  }
  if (evidence !== null) {
    heading = { ...heading, ...evidence };
    parity = { ...parity, ...evidence };
  }
  const files = checkpointPaths(language, spec.code, chapter);
  await writeCheckpoint(files.heading, heading);
  await writeCheckpoint(files.merge, merge);
  await writeCheckpoint(files.missingRange, missingRange);
  await writeCheckpoint(files.range, range);
  await writeCheckpoint(files.scripture, scripture);
  await writeCheckpoint(files.semantic, semantic);
  await writeCheckpoint(files.parity, parity);
  return { heading, merge, missingRange, range, scripture, semantic, parity };
}

export async function auditChapterSet(target, language, spec, requested, force = false) {
  // Retain the positional API for existing browser sessions, but never trust
  // an old checkpoint as proof of the current asset/tool/edition state.
  void force;
  await validateLanguageInventory(language);
  const available = await loadBook(language, spec);
  if (!Array.isArray(requested)
      || requested.some((chapter) => !Number.isInteger(chapter) || !available.includes(chapter))
      || new Set(requested).size !== requested.length) {
    throw new Error("requested chapter inventory is invalid");
  }
  const wanted = new Set(requested);
  const chapters = available.filter((chapter) => wanted.has(chapter));
  const stats = {
    language,
    code: spec.code,
    chapters: chapters.length,
    processed: 0,
    skipped: 0,
    scripture: { not_needed: 0, match: 0, changed: 0, blocked: 0 },
    semantics: { not_needed: 0, match: 0, changed: 0, blocked: 0 },
    missingRanges: { not_needed: 0, match: 0, changed: 0, blocked: 0 },
    merges: { not_needed: 0, match: 0, changed: 0, blocked: 0 },
    ranges: { not_needed: 0, match: 0, changed: 0, blocked: 0 },
    parity: { match: 0, mismatch: 0, source_defect: 0, blocked: 0, findings: {} },
    headings: { match: 0, changed: 0, blocked: 0 },
  };
  for (const chapter of chapters) {
    const result = await processChapter(target, language, spec, chapter);
    stats.processed += 1;
    stats.scripture[result.scripture.status] = (stats.scripture[result.scripture.status] || 0) + 1;
    stats.semantics[result.semantic.status] = (stats.semantics[result.semantic.status] || 0) + 1;
    stats.missingRanges[result.missingRange.status] = (stats.missingRanges[result.missingRange.status] || 0) + 1;
    stats.merges[result.merge.status] = (stats.merges[result.merge.status] || 0) + 1;
    stats.ranges[result.range.status] = (stats.ranges[result.range.status] || 0) + 1;
    stats.headings[result.heading.status] = (stats.headings[result.heading.status] || 0) + 1;
    stats.parity[result.parity.status] = (stats.parity[result.parity.status] || 0) + 1;
    for (const finding of result.parity.findings || []) {
      stats.parity.findings[finding.kind] = (stats.parity.findings[finding.kind] || 0) + 1;
    }
  }
  return stats;
}

export async function auditBook(target, language, spec, force = false) {
  return auditChapterSet(target, language, spec, await loadBook(language, spec), force);
}

export async function auditBooks(target, language, specs, force = false) {
  const output = [];
  for (const spec of specs) output.push(await auditBook(target, language, spec, force));
  return output;
}

export async function repairBook(target, language, spec) {
  const chapters = [];
  for (const chapter of await loadBook(language, spec)) {
    const prior = await readCheckpoint(checkpointPaths(language, spec.code, chapter).parity);
    if (prior && prior.status === "mismatch") chapters.push(chapter);
  }
  return auditChapterSet(target, language, spec, chapters, true);
}

export async function repairRangeBook(target, language, spec) {
  const chapters = [];
  for (const chapter of await loadBook(language, spec)) {
    const prior = await readCheckpoint(checkpointPaths(language, spec.code, chapter).parity);
    const hasRangeFinding = prior && prior.status === "mismatch"
      && (prior.findings || []).some((finding) => ["local_range_only", "source_range_only"].includes(String(finding.kind || "")));
    if (hasRangeFinding) chapters.push(chapter);
  }
  return auditChapterSet(target, language, spec, chapters, true);
}

export async function repairBlockedBook(target, language, spec) {
  const chapters = [];
  for (const chapter of await loadBook(language, spec)) {
    const prior = await readCheckpoint(checkpointPaths(language, spec.code, chapter).parity);
    if (prior && prior.status === "blocked") chapters.push(chapter);
  }
  return auditChapterSet(target, language, spec, chapters, true);
}
