package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext

/** External readers share their website; the internal reader uses its saved share provider. */
internal fun readerShareLinks(
  context: PlatformContext,
  canonicalRef: String,
  bookId: String,
  prefs: PrefsState,
  sourceEdition: String
): List<String> = readerShareLinks(canonicalRef, bookId, prefs, sourceEdition,
  resolveExternal = { ref, targetBookId, candidate ->
    editionAwareExternalLink(context, ref, targetBookId, candidate, sourceEdition)
  },
  resolveSourceAnchor = { targetBookId, fromEdition, toEdition, anchor ->
    EditionReferenceMaps.resolve(context, LocaleUtils.effectiveAssetTag(prefs.appLanguage),
      targetBookId, fromEdition, toEdition, anchor)
  })

/** Inject the checked public-link resolver so payload tests need no platform assets. */
internal fun readerShareLinks(
  canonicalRef: String,
  bookId: String,
  prefs: PrefsState,
  sourceEdition: String,
  resolveExternal: (canonicalRef: String, bookId: String, providerPrefs: PrefsState) -> Pair<String, String>?,
  resolveSourceAnchor: (bookId: String, fromEdition: String, toEdition: String, anchor: VerseAnchor) -> VerseAnchor?
): List<String> {
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val source = readerShareSourceEdition(language, sourceEdition)
  if (source !in BibleEditions.available(language)) return emptyList()
  val externalMode = prefs.readerMode.trim().lowercase()
    .takeIf { it == "biblecom" || it == "biblegateway" }
  val provider = externalMode ?: normalizeVerseShareProvider(prefs.verseShareProvider)
  val defaultVersion = when {
    language == "en" && source == BibleEditions.KJV_1769 -> "KJV"
    provider == "biblegateway" -> Linker.defaultGatewayVersionForLanguage(language)
    else -> Linker.defaultVersionForLanguage(language)
  }
  val candidate = prefs.copy(appLanguage = language, readerMode = provider,
    translation = if (externalMode != null) prefs.translation else defaultVersion)
  fun publicUrl(result: Pair<String, String>?): String? = result?.second?.takeIf {
    val host = if (provider == "biblegateway") "www.biblegateway.com" else "www.bible.com"
    it.startsWith("https://$host/")
  }

  publicUrl(resolveExternal(canonicalRef, bookId, candidate))?.let { return listOf(it) }
  if (provider == "biblecom") {
    // Hidden external preferences cannot change the internal corpus's declared
    // numbering authority. Keep the fallback on the selected website.
    if (!candidate.translation.equals(defaultVersion, ignoreCase = true)) {
      publicUrl(resolveExternal(canonicalRef, bookId, candidate.copy(translation = defaultVersion)))
        ?.let { return listOf(it) }
    }
    return emptyList()
  }

  // Failure for an audited provider target is authoritative. A generic lookup
  // must not reinterpret failed KJV crosswalks or invalid native verse units.
  val selectedVersion = Linker.selectedVersionForReader(candidate.translation, provider, language)
  if (selectedVersion != null && providerNumberingEdition(language, provider, selectedVersion, bookId) != null) {
    return emptyList()
  }

  // Gateway's localized catalog includes structural comparators rather than
  // audited edition identities. A share-only reference lookup first maps the
  // source passage to the base corpus; this does not claim identical wording
  // or provider numbering. Unmapped alternate passages remain unlinked.
  val base = BibleEditions.defaultForLanguage(language)
  val lookupReference = editionAwareCanonicalReference(canonicalRef, bookId, base, source, base,
    resolveSourceAnchor) ?: return emptyList()
  return listOfNotNull(publicUrl(Linker.linkForReader(
    lookupReference, candidate.translation, "biblegateway", language
  )))
}

internal fun buildReaderShareText(
  context: PlatformContext,
  collection: String,
  book: Book,
  selected: Set<Pair<String, Int>>,
  prefs: PrefsState,
  sourceEdition: String,
  bookId: String = book.id
): SelectedContent = buildReaderShareText(collection, book, selected, prefs, sourceEdition,
  linksForReference = { ref ->
    readerShareLinks(context, ref, bookId, prefs, sourceEdition)
  },
  bookId = bookId)

private data class ReaderShareUnit(
  val storyTitle: String,
  val storyReference: String?,
  val bookName: String?,
  val anchor: VerseAnchor?,
  val text: String
)

private data class ReaderShareBlockKey(
  val bookName: String?,
  val chapter: Int?,
  val fallbackStoryId: String?
)

/** Pure payload assembly; each requested reference names only selected units. */
internal fun buildReaderShareText(
  collection: String,
  book: Book,
  selected: Set<Pair<String, Int>>,
  prefs: PrefsState,
  sourceEdition: String,
  linksForReference: (canonicalRef: String) -> List<String>,
  localizeReference: (String) -> String = ScriptureRefs::localizeRef,
  bookId: String = book.id
): SelectedContent {
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val edition = readerShareSourceEdition(language, sourceEdition)
  val editionLabel = readerShareEditionLabel(language, edition)
  val blocks = linkedMapOf<ReaderShareBlockKey, MutableList<ReaderShareUnit>>()
  val chapterTail = Regex("\\s+\\d+(?::.*)?$")

  for (story in book.stories) {
    val storyReference = story.refs.firstOrNull()?.let(ScriptureRefs::canonicalizeRef)
    val chapterBookName = storyReference?.let { reference ->
      chapterTail.find(reference)?.let { reference.substring(0, it.range.first).trim() }
    }?.takeIf { it.isNotEmpty() }
    for ((index, raw) in story.summaryBullets.withIndex()) {
      if ((story.id to index) !in selected) continue
      val parsed = parseTrailingVerseAnchor(raw, story.id, bookId)
      // Audited single-chapter stories use refs such as "Susanna 1-64"
      // and bare source markers such as "(18).". A successful bare-marker
      // parse has already verified both story and book identity. Recover
      // the canonical book from that authority, not from an ambiguous range.
      val bookName = chapterBookName ?: parsed?.takeIf {
        raw.substring(it.rawStart).none { char -> char == ':' || char == '\uFF1A' }
      }?.let {
        Linker.referenceForDeuterocanonBook(bookId)?.let { reference ->
          chapterTail.find(reference)?.let { reference.substring(0, it.range.first).trim() }
        }
      }
      val anchor = parsed?.anchor?.takeIf {
        it.chapter > 0 && it.verseStart > 0 && it.verseEnd >= it.verseStart
      }
      val key = if (bookName != null && anchor != null) {
        ReaderShareBlockKey(bookName, anchor.chapter, null)
      } else {
        ReaderShareBlockKey(bookName, null, story.id)
      }
      val text = stripScriptureInlineTags(readerTextWithoutTrailingAnchor(raw, parsed)).trim()
      blocks.getOrPut(key) { mutableListOf() } += ReaderShareUnit(
        story.title, storyReference, bookName, anchor, text
      )
    }
  }

  var primaryRef: String? = null
  val rendered = blocks.values.map { units ->
    val first = units.first()
    val anchors = units.mapNotNull { it.anchor }
    val exact = first.bookName != null && anchors.size == units.size
    val reference = if (exact) {
      "${first.bookName} ${compactSelectedVerseTail(anchors)}"
    } else {
      first.storyReference
    }
    if (primaryRef == null) primaryRef = reference

    val links = if (exact) {
      readerShareContiguousSpans(anchors).flatMap { span ->
        val ref = "${first.bookName} ${readerNativeReferenceTail(span)}"
        linksForReference(ref)
      }.distinct()
    } else {
      // Without source coordinates there is no proved selected verse target.
      emptyList()
    }

    buildString {
      append(wrapVotdQuotes(units.joinToString(" ") { it.text }, language))
      append('\n')
      append(reference?.let(localizeReference) ?: first.storyTitle)
      append(' ').append(editionLabel)
      if (links.isNotEmpty()) append("\n\n").append(links.joinToString("\n"))
    }
  }
  return SelectedContent(rendered.joinToString("\n\n"), primaryRef)
}

/** Split gaps before asking a provider or app link to highlight a range. */
internal fun readerShareContiguousSpans(anchors: List<VerseAnchor>): List<VerseAnchor> {
  val spans = mutableListOf<VerseAnchor>()
  for (anchor in anchors.sortedWith(compareBy({ it.chapter }, { it.verseStart }))) {
    val previous = spans.lastOrNull()
    if (previous != null && previous.chapter == anchor.chapter &&
      anchor.verseStart <= previous.verseEnd + 1
    ) {
      spans[spans.lastIndex] = previous.copy(verseEnd = maxOf(previous.verseEnd, anchor.verseEnd))
    } else spans += anchor
  }
  return spans
}

private fun readerShareSourceEdition(language: String, sourceEdition: String): String =
  if (sourceEdition.trim().equals("default", ignoreCase = true)) {
    BibleEditions.defaultForLanguage(language)
  } else {
    BibleEditions.canonicalId(language, sourceEdition.trim()) ?: sourceEdition.trim().lowercase()
  }

internal fun readerShareEditionLabel(language: String, sourceEdition: String): String =
  when (val edition = readerShareSourceEdition(language, sourceEdition)) {
    BibleEditions.KJV_1769 -> "KJV"
    BibleEditions.LUTHER_1912 -> "Luther 1912"
    BibleEditions.RV_1909 -> "Reina-Valera 1909"
    BibleEditions.LSG_1910 -> "Louis Segond 1910"
    BibleEditions.DIODATI_1885 -> "Diodati 1885"
    BibleEditions.ALMEIDA_1911 -> "Almeida 1911"
    BibleEditions.SYNODAL_1876 -> "Synodal 1876"
    BibleEditions.BUNGO -> "Bungo"
    BibleEditions.KOR_RV -> "KRV"
    BibleEditions.CUV -> "CUV"
    BibleEditions.VAN_DYCK -> "Van Dyck"
    BibleEditions.defaultForLanguage(language) -> Linker.defaultVersionForLanguage(language)
    else -> edition
  }
