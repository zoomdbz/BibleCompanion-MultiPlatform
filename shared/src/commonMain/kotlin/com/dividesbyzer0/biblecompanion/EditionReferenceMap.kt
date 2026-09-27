package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Serializable
internal data class EditionReferenceRule(
  val sourceChapter: Int,
  val sourceVerse: Int,
  val sourceVerseEnd: Int? = null,
  val targetChapter: Int,
  val targetVerse: Int,
  val targetVerseEnd: Int? = null
) {
  fun source() = VerseAnchor(sourceChapter, sourceVerse, sourceVerseEnd ?: sourceVerse)
  fun target() = VerseAnchor(targetChapter, targetVerse, targetVerseEnd ?: targetVerse)
}

@Serializable
internal data class EditionBookReferenceMap(
  val bookId: String,
  val complete: Boolean = false,
  val mappings: List<EditionReferenceRule>
)

@Serializable
internal data class EditionReferenceMap(
  val schemaVersion: Int,
  val language: String,
  val baseEditionId: String,
  val editionId: String,
  val books: List<EditionBookReferenceMap>
)

/** Resolve explicit passage equivalences, never infer equivalence from counts. */
internal fun mapEditionReference(
  bookMap: EditionBookReferenceMap,
  anchor: VerseAnchor,
  reverse: Boolean = false
): VerseAnchor? {
  if (anchor.chapter < 1 || anchor.verseStart < 1 || anchor.verseEnd < anchor.verseStart ||
    anchor.verseEnd - anchor.verseStart > 1000) return null
  val targets = mutableListOf<VerseAnchor>()
  for (verse in anchor.verseStart..anchor.verseEnd) {
    val rules = bookMap.mappings.filter { rule ->
      val from = if (reverse) rule.target() else rule.source()
      from.chapter == anchor.chapter && verse in from.verseStart..from.verseEnd
    }
    if (rules.isEmpty()) {
      // A partial table is not evidence for any unlisted identity mapping.
      // Keep the actual source edition until a reviewed row covers this verse.
      return null
    } else {
      for (rule in rules) {
        val from = if (reverse) rule.target() else rule.source()
        val to = if (reverse) rule.source() else rule.target()
        val fromLength = from.verseEnd - from.verseStart + 1
        val toLength = to.verseEnd - to.verseStart + 1
        when {
          fromLength == toLength -> targets += VerseAnchor(to.chapter, to.verseStart + verse - from.verseStart)
          fromLength == 1 || toLength == 1 -> targets += to
          // Unequal multi-verse spans do not identify individual passages.
          else -> return null
        }
      }
    }
  }
  val ordered = targets.distinct().sortedWith(compareBy({ it.chapter }, { it.verseStart }))
  val first = ordered.firstOrNull() ?: return null
  var end = first.verseEnd
  for (target in ordered.drop(1)) {
    // The internal reader accepts one contiguous range in one chapter. Do not
    // widen disjoint passages or invent a span across different chapters.
    if (target.chapter != first.chapter || target.verseStart > end + 1) return null
    end = maxOf(end, target.verseEnd)
  }
  return VerseAnchor(first.chapter, first.verseStart, end)
}

internal object EditionReferenceMaps {
  private val json = Json { ignoreUnknownKeys = true }
  private val cache = MutableStateFlow<Map<String, EditionReferenceMap?>>(emptyMap())

  fun resolve(
    context: PlatformContext,
    language: String,
    bookId: String,
    fromEdition: String,
    toEdition: String,
    anchor: VerseAnchor
  ): VerseAnchor? {
    if (anchor.chapter < 1 || anchor.verseStart < 1 || anchor.verseEnd < anchor.verseStart ||
      anchor.verseEnd - anchor.verseStart > 1000) return null
    val tag = LocaleUtils.effectiveAssetTag(language)
    if (fromEdition == toEdition) return nativeAnchor(context, tag, bookId, fromEdition, anchor)
    val base = BibleEditions.defaultForLanguage(tag)
    val alternate = when {
      fromEdition == base && toEdition in BibleEditions.available(tag) -> toEdition
      toEdition == base && fromEdition in BibleEditions.available(tag) -> fromEdition
      else -> return null
    }
    // A merged source unit cannot be divided into invented a/b fragments.
    // In particular, BSB Revelation 12:17-18 crosses the KJV chapter boundary.
    val original = nativeAnchor(context, tag, bookId, fromEdition, anchor) ?: return null
    val key = "$tag/$alternate"
    val snapshot = cache.value
    val map = if (snapshot.containsKey(key)) snapshot[key] else {
      val raw = readAssetText(context, "books/editions/$key/_reference_map.json")
      val loaded = raw?.let { runCatching { json.decodeFromString<EditionReferenceMap>(it) }.getOrNull() }
        ?.takeIf { it.schemaVersion == 1 && it.language == tag && it.editionId == alternate && it.baseEditionId == base }
      cache.update { it + (key to loaded) }
      loaded
    }
    // Equal chapter counts or equal sets of numbers do not establish passage
    // identity. Missing/corrupt maps retain the actual source edition.
    val bookMap = map?.books?.firstOrNull { it.bookId == bookId } ?: return null
    val mapped = mapEditionReference(bookMap, original, reverse = fromEdition == alternate) ?: return null
    return checkedTarget(context, tag, bookId, fromEdition, toEdition, original, mapped)
  }

  private fun nativeAnchor(
    context: PlatformContext, language: String, bookId: String, edition: String, anchor: VerseAnchor
  ): VerseAnchor? {
    for (collection in listOf("old_testament", "new_testament", "deuterocanonical", "apocrypha", "pseudepigrapha")) {
      val loaded = ContentRepo.loadBookWithEdition(context, collection, bookId, language, edition) ?: continue
      if (loaded.effectiveEdition != edition) return null
      return fullNativeVerseAnchor(anchor, loaded.book.stories.flatMap { story ->
        story.summaryBullets.mapNotNull(::verseAnchorFromText)
      })
    }
    return null
  }

  private fun checkedTarget(
    context: PlatformContext,
    language: String,
    bookId: String,
    fromEdition: String,
    toEdition: String,
    original: VerseAnchor,
    mapped: VerseAnchor
  ): VerseAnchor? {
    for (collection in listOf("old_testament", "new_testament", "deuterocanonical", "apocrypha", "pseudepigrapha")) {
      val target = ContentRepo.loadBookWithEdition(context, collection, bookId, language, toEdition) ?: continue
      if (target.effectiveEdition != toEdition) return null
      fun chapterNumbers(book: Book, chapter: Int): Set<Int> = book.stories
        .flatMap { versePickerNumbers(it.summaryBullets, chapter) }.toSet()
      val targetNumbers = chapterNumbers(target.book, mapped.chapter)
      if (!(mapped.verseStart..mapped.verseEnd).all { it in targetNumbers }) return null
      val source = ContentRepo.loadBookWithEdition(context, collection, bookId, language, fromEdition) ?: return null
      if (source.effectiveEdition != fromEdition) return null
      val sourceNumbers = chapterNumbers(source.book, original.chapter)
      if (!(original.verseStart..original.verseEnd).all { it in sourceNumbers }) return null
      return fullNativeVerseAnchor(mapped, target.book.stories.flatMap { story ->
        story.summaryBullets.mapNotNull(::verseAnchorFromText)
      })
    }
    return null
  }
}
