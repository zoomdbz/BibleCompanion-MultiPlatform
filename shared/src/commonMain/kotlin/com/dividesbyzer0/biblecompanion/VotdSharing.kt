package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext

private val votdShareReferencePattern = Regex("^(.+?)\\s+(\\d+):(\\d+)(?:-(\\d+))?$")

private data class VotdShareReference(val bookName: String, val tail: String) {
  val canonical: String get() = "$bookName $tail"
}

private fun votdShareReference(reference: String): VotdShareReference? {
  val match = votdShareReferencePattern.matchEntire(reference.trim()) ?: return null
  val chapter = match.groupValues[2].toIntOrNull() ?: return null
  val first = match.groupValues[3].toIntOrNull() ?: return null
  val endText = match.groupValues[4]
  val last = if (endText.isEmpty()) first else endText.toIntOrNull() ?: return null
  if (chapter < 1 || first < 1 || last < first) return null
  val name = match.groupValues[1].trim().let {
    if (it.equals("Acts of the Apostles", ignoreCase = true)) "Acts" else it
  }
  val tail = "$chapter:$first" + if (last == first) "" else "-$last"
  return VotdShareReference(name, tail)
}

private data class VotdShareBook(val collection: String, val id: String, val displayTitle: String)

/** Share the displayed daily verse using the same provider policy as reader sharing. */
internal fun buildVotdShareText(context: PlatformContext, verse: DailyVerse, prefs: PrefsState): String {
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val reference = votdShareReference(verse.ref)
  val book = reference?.let { ref ->
    listOf("old_testament", "new_testament", "deuterocanonical").firstNotNullOfOrNull { collection ->
      ContentRepo.listBooksLocalized(context, collection, "en")
        .firstOrNull { (_, title) -> title.equals(ref.bookName, ignoreCase = true) }
        ?.let { (id, title) ->
          val displayTitle = ContentRepo.listBooksLocalized(context, collection, language)
            .firstOrNull { it.first == id }?.second ?: title
          VotdShareBook(collection, id, displayTitle)
        }
    }
  }
  val plainVerse = verse.copy(text = notificationPlainText(
    verse.text, prefs.divineName, language, book?.collection ?: "old_testament"
  ))
  return buildVotdShareText(plainVerse, prefs,
    resolveBookId = { book?.id },
    linksForReference = { ref, bookId, edition ->
      readerShareLinks(context, ref, bookId, prefs, edition)
    },
    localizeReference = { ref ->
      if (book != null && reference != null) "${book.displayTitle} ${reference.tail}" else ref
    })
}

/** Keep the selected text and source edition intact; never invent an unknown book link. */
internal fun buildVotdShareText(
  verse: DailyVerse,
  prefs: PrefsState,
  resolveBookId: (String) -> String?,
  linksForReference: (canonicalRef: String, bookId: String, sourceEdition: String) -> List<String>,
  localizeReference: (String) -> String = ScriptureRefs::localizeRef
): String {
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val edition = verse.editionId ?: BibleEditions.defaultForLanguage(language)
  val reference = votdShareReference(verse.ref)
  val bookId = reference?.let { resolveBookId(it.bookName) }
  val links = if (reference != null && bookId != null) {
    linksForReference(reference.canonical, bookId, edition).distinct()
  } else emptyList()
  return buildString {
    append(wrapVotdQuotes(stripScriptureInlineTags(verse.text), language))
    append('\n')
    append(localizeReference(reference?.canonical ?: verse.ref))
    append(' ').append(readerShareEditionLabel(language, edition))
    if (links.isNotEmpty()) append("\n\n").append(links.joinToString("\n"))
  }
}
