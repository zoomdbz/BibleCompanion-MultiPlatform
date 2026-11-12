package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext

private data class ProviderNumberingKey(
  val language: String,
  val readerMode: String,
  val providerCode: String,
  val bookId: String? = null
)

/*
 * These rows identify an audited reference-numbering target. They do not claim
 * that similarly named provider editions have identical wording. In
 * particular, a Linker code alias only selects a
 * provider catalog entry; an alias is not evidence of edition identity.
 */
private val providerNumberingEditions = mapOf(
  ProviderNumberingKey("de", "biblegateway", "SCH2000") to "sch2000",
  ProviderNumberingKey("it", "biblegateway", "NR2006") to "nr06",
  ProviderNumberingKey("ru", "biblegateway", "NRT") to "nrt_nrp",

  // Existing KJV source audits establish one reference-numbering target for
  // these provider catalog entries. The codes do not assert byte-identical text.
  ProviderNumberingKey("en", "biblecom", "KJV") to BibleEditions.KJV_1769,
  ProviderNumberingKey("en", "biblecom", "KJVAE") to BibleEditions.KJV_1769,
  ProviderNumberingKey("en", "biblecom", "KJVAAE") to BibleEditions.KJV_1769,
  ProviderNumberingKey("en", "biblegateway", "KJV") to BibleEditions.KJV_1769,
  ProviderNumberingKey("sw", "biblecom", "SUV") to BibleEditions.SUV_1952,

  // YouVersion DELUT 51 is documented against the overlay's Psalm numbering.
  // Other books and traditional-looking provider labels remain unverified.
  ProviderNumberingKey("de", "biblecom", "DELUT", "psalms") to BibleEditions.LUTHER_1912
)

internal fun providerNumberingEdition(
  language: String,
  readerMode: String,
  providerCode: String,
  bookId: String
): String? {
  val key = ProviderNumberingKey(
    LocaleUtils.effectiveAssetTag(language),
    readerMode.trim().lowercase(),
    providerCode.trim().uppercase(),
    bookId.trim().lowercase()
  )
  // The Linker default is the declared Bible.com numbering authority for the
  // localized base corpus. Require the exact selected code, not an alias or a
  // similar provider label.
  if (key.language != "sw" && key.readerMode == "biblecom" &&
    key.providerCode == Linker.defaultVersionForLanguage(key.language).uppercase()
  ) return BibleEditions.defaultForLanguage(key.language)
  return providerNumberingEditions[key] ?: providerNumberingEditions[key.copy(bookId = null)]
}

internal fun providerFallbackKeepsNumbering(
  language: String,
  readerMode: String,
  selectedCode: String,
  resolvedCode: String,
  bookId: String
): Boolean {
  if (selectedCode.equals(resolvedCode, ignoreCase = true)) return true
  val selectedTarget = providerNumberingEdition(language, readerMode, selectedCode, bookId)
    ?: return false
  return providerNumberingEdition(language, readerMode, resolvedCode, bookId) == selectedTarget
}

private data class CanonicalExternalReference(
  val book: String,
  val chapter: Int,
  val verseStart: Int?,
  val verseEnd: Int?
) {
  fun render(anchor: VerseAnchor? = null): String {
    val resolvedChapter = anchor?.chapter ?: chapter
    val resolvedStart = anchor?.verseStart ?: verseStart
    val resolvedEnd = anchor?.verseEnd ?: verseEnd
    if (resolvedStart == null) return "$book $resolvedChapter"
    val range = if (resolvedEnd != null && resolvedEnd != resolvedStart) "-$resolvedEnd" else ""
    return "$book $resolvedChapter:$resolvedStart$range"
  }
}

private val canonicalExternalReferencePattern = Regex(
  """^\s*(.+?)\s+(\d+)(?::(\d+)(?:\s*[-\u2013]\s*(\d+))?)?\s*$"""
)

private fun parseCanonicalExternalReference(reference: String): CanonicalExternalReference? {
  val match = canonicalExternalReferencePattern.matchEntire(reference) ?: return null
  val book = match.groupValues[1].trim().takeIf { it.isNotEmpty() } ?: return null
  // The book segment may contain an ordinal digit, but never reference
  // punctuation. This prevents "John 3:16; Luke 2:1" from being reinterpreted
  // as a book named "John 3:16; Luke" followed by one final coordinate.
  val bookWithoutOrdinal = book.replaceFirst(Regex("^[1-4]\\s+"), "")
  if (bookWithoutOrdinal.any { it.isDigit() } ||
    book.any { it == ':' || it == ';' || it == ',' || it == '/' || it == '\\' }
  ) return null
  val chapter = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
  val start = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
  val end = match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
  if (start != null && (start < 1 || end == null || end < start)) return null
  return CanonicalExternalReference(book, chapter, start, end)
}

/**
 * Rewrite one simple, contiguous reference without guessing passage identity.
 * Complex/disjoint references fail as a whole instead of being truncated to
 * their first verse.
 */
internal fun editionAwareCanonicalReference(
  canonicalRef: String,
  bookId: String,
  baseEdition: String,
  sourceEdition: String,
  providerTargetEdition: String?,
  resolveAnchor: (bookId: String, fromEdition: String, toEdition: String, anchor: VerseAnchor) -> VerseAnchor?
): String? {
  val parsed = parseCanonicalExternalReference(canonicalRef) ?: return null
  val source = sourceEdition.trim().lowercase()
  val target = providerTargetEdition?.trim()?.lowercase()

  // An unknown provider version has no proved numbering scheme, even when
  // the in-app source is the default edition. Keep that reference in-app.
  if (target == null) return null
  if (source == target) {
    val start = parsed.verseStart ?: return parsed.render()
    val native = resolveAnchor(
      bookId, source, target,
      VerseAnchor(parsed.chapter, start, parsed.verseEnd ?: start)
    ) ?: return null
    return parsed.render(native)
  }

  // A chapter-only link cannot express a verse-level crosswalk safely.
  val start = parsed.verseStart ?: return null
  val mapped = resolveAnchor(
    bookId,
    source,
    target,
    VerseAnchor(parsed.chapter, start, parsed.verseEnd ?: start)
  ) ?: return null
  return parsed.render(mapped)
}

/**
 * Build an external reader link whose anchor follows the source in-app
 * edition. Unknown alternate-to-provider numbering fails closed; the caller
 * can offer an edition-preserving in-app link instead.
 */
internal fun editionAwareExternalLink(
  context: PlatformContext,
  canonicalRef: String,
  bookId: String,
  prefs: PrefsState,
  sourceEdition: String
): Pair<String, String>? {
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val baseEdition = BibleEditions.defaultForLanguage(language)
  val normalizedSource = when (sourceEdition.trim().lowercase()) {
    "default" -> baseEdition
    "kjv" -> if (language == "en") BibleEditions.KJV_1769 else return null
    else -> sourceEdition.trim().lowercase()
  }
  if (normalizedSource !in BibleEditions.available(language)) return null

  val selectedProviderVersion = Linker.selectedVersionForReader(
    prefs.translation,
    prefs.readerMode,
    language
  ) ?: return null
  val providerTarget = providerNumberingEdition(
    language,
    prefs.readerMode,
    selectedProviderVersion,
    bookId
  )
  val resolvedReference = editionAwareCanonicalReference(
    canonicalRef = canonicalRef,
    bookId = bookId,
    baseEdition = baseEdition,
    sourceEdition = normalizedSource,
    providerTargetEdition = providerTarget
  ) { resolvedBookId, fromEdition, toEdition, anchor ->
    EditionReferenceMaps.resolve(
      context = context,
      language = language,
      bookId = resolvedBookId,
      fromEdition = fromEdition,
      toEdition = toEdition,
      anchor = anchor
    )
  } ?: return null

  val result = Linker.linkForReader(
    resolvedReference,
    prefs.translation,
    prefs.readerMode,
    language
  ) ?: return null

  // A deuterocanonical fallback may select a different provider edition after
  // the anchor was mapped. That edition needs its own audited target scheme.
  val crossEditionMappingApplied = providerTarget != null && normalizedSource != providerTarget
  if ((normalizedSource != baseEdition || crossEditionMappingApplied) &&
    !providerFallbackKeepsNumbering(
      language,
      prefs.readerMode,
      selectedProviderVersion,
      result.first,
      bookId
    )
  ) return null
  return result
}

/**
 * Tap navigation for a selected external reader. Bible.com and BibleGateway
 * both accept chapter, verse, and same-chapter range links, so a tap honors the
 * selected site and version instead of silently opening in-app. An audited
 * provider target keeps its exact crosswalk; any other provider edition gets
 * the reference in the base corpus numbering it is displayed in. Returns null
 * only for the internal reader or when the provider cannot address the book.
 * Share links keep the stricter [editionAwareExternalLink] contract.
 */
internal fun externalReaderNavigationLink(
  context: PlatformContext,
  canonicalRef: String,
  bookId: String,
  prefs: PrefsState,
  sourceEdition: String
): Pair<String, String>? {
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  return externalReaderNavigationLink(canonicalRef, bookId, prefs, sourceEdition) {
      resolvedBookId, fromEdition, toEdition, anchor ->
    EditionReferenceMaps.resolve(
      context = context,
      language = language,
      bookId = resolvedBookId,
      fromEdition = fromEdition,
      toEdition = toEdition,
      anchor = anchor
    )
  }
}

/** Inject the edition resolver so navigation tests need no platform assets. */
internal fun externalReaderNavigationLink(
  canonicalRef: String,
  bookId: String,
  prefs: PrefsState,
  sourceEdition: String,
  resolveAnchor: (bookId: String, fromEdition: String, toEdition: String, anchor: VerseAnchor) -> VerseAnchor?
): Pair<String, String>? {
  val mode = prefs.readerMode.trim().lowercase()
  if (mode != "biblecom" && mode != "biblegateway") return null
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val baseEdition = BibleEditions.defaultForLanguage(language)
  val source = when (val raw = sourceEdition.trim().lowercase()) {
    "default" -> baseEdition
    "kjv" -> if (language == "en") BibleEditions.KJV_1769 else baseEdition
    else -> raw
  }.takeIf { it in BibleEditions.available(language) } ?: baseEdition
  val selectedVersion = Linker.selectedVersionForReader(prefs.translation, mode, language)
    ?: return null
  val target = providerNumberingEdition(language, mode, selectedVersion, bookId) ?: baseEdition

  val lookupReference = editionAwareCanonicalReference(
    canonicalRef = canonicalRef,
    bookId = bookId,
    baseEdition = baseEdition,
    sourceEdition = source,
    providerTargetEdition = target,
    resolveAnchor = resolveAnchor
  ) ?: chapterOnlyNavigationReference(canonicalRef, bookId, source, target, resolveAnchor)
    ?: canonicalRef

  return Linker.linkForReader(lookupReference, prefs.translation, mode, language)
}

/**
 * A chapter-only reference cannot carry a verse crosswalk, but the audited map
 * still places the chapter's first verse. That chapter is the honest target
 * (for example, Synodal LXX Psalm numbering into a modern edition).
 */
private fun chapterOnlyNavigationReference(
  canonicalRef: String,
  bookId: String,
  source: String,
  target: String,
  resolveAnchor: (bookId: String, fromEdition: String, toEdition: String, anchor: VerseAnchor) -> VerseAnchor?
): String? {
  if (source == target) return null
  val parsed = parseCanonicalExternalReference(canonicalRef) ?: return null
  if (parsed.verseStart != null) return null
  val mapped = resolveAnchor(bookId, source, target, VerseAnchor(parsed.chapter, 1)) ?: return null
  return "${parsed.book} ${mapped.chapter}"
}
