package com.dividesbyzer0.biblecompanion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.PlatformContext

internal data class ReadingResume(
  val title: String,
  val storyId: String?,
  val chapter: Int?,
  val chapterCount: Int,
  val verse: Int? = null,
  val verseEnd: Int? = null
)

@Composable
internal fun rememberReadingResume(prefs: PrefsState): ReadingResume? {
  val context = LocalPlatformContext.current
  val collection = prefs.lastReadCollection
  val bookId = prefs.lastReadBookId
  val sourceLanguage = prefs.lastReadSourceLanguage ?: "en"

  return remember(
    context,
    collection,
    bookId,
    prefs.lastReadStoryId,
    sourceLanguage,
    prefs.lastReadSourceEdition,
    prefs.appLanguage,
    prefs.internalBibleVersion
  ) {
    if (collection == null || bookId == null) return@remember null
    loadReadingResume(context, prefs)
  }
}

/** Resume follows the current setting; saved passage links keep their own edition. */
internal fun loadReadingResume(context: PlatformContext, prefs: PrefsState): ReadingResume? {
  val collection = prefs.lastReadCollection ?: return null
  val bookId = prefs.lastReadBookId ?: return null
  val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val sourceLanguage = prefs.lastReadSourceLanguage ?: "en"
  val sourceEdition = BibleEditions.canonicalId(
    sourceLanguage, prefs.lastReadSourceEdition ?: BibleEditions.defaultForLanguage(sourceLanguage)
  )
  val targetBook = ContentRepo.loadBookWithEdition(
    context, collection, bookId, language,
    BibleEditions.effective(language, prefs.internalBibleVersion)
  ) ?: return null

  return readerPositionForEdition(
    book = targetBook.book,
    storedStoryId = prefs.lastReadStoryId,
    sourceLanguage = sourceLanguage,
    targetLanguage = language,
    sourceEdition = sourceEdition,
    targetEdition = targetBook.effectiveEdition,
    resolveAnchor = { anchor ->
      sourceEdition?.let {
        EditionReferenceMaps.resolve(context, language, bookId, it, targetBook.effectiveEdition, anchor)
      }
    }
  )
}

/** Do not restore an old reader graph just because its book and chapter match. */
internal fun readingResumeCanRestoreSavedEdition(prefs: PrefsState): Boolean {
  val sourceLanguage = prefs.lastReadSourceLanguage ?: "en"
  if (LocaleUtils.effectiveAssetTag(sourceLanguage) != LocaleUtils.effectiveAssetTag(prefs.appLanguage)) {
    return false
  }
  val sourceEdition = BibleEditions.canonicalId(
    sourceLanguage, prefs.lastReadSourceEdition ?: BibleEditions.defaultForLanguage(sourceLanguage)
  )
  return sourceEdition == BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)
}

internal fun readingResumeForBook(
  book: Book,
  storedStoryId: String?,
  sourceLanguage: String,
  targetLanguage: String
): ReadingResume {
  val index = ChapterLocator.build(book)
  val storyIds = book.stories.mapTo(mutableSetOf()) { it.id }
  val wantedStoryId = resolveStoryIdAcrossLanguages(
    storedStoryId,
    sourceLanguage,
    targetLanguage
  )
  val resolvedStoryId = when {
    wantedStoryId.isNullOrBlank() || wantedStoryId in storyIds -> wantedStoryId
    else -> {
      val chapter = wantedStoryId.substringAfterLast('-')
      "${book.id}-$chapter".takeIf { it in storyIds }
        ?: chapter.toIntOrNull()?.let(index.byChapter::get)
    }
  }
  val chapter = index.byChapter.entries
    .firstOrNull { it.value == resolvedStoryId }
    ?.key

  return ReadingResume(
    title = book.title,
    storyId = resolvedStoryId,
    chapter = chapter,
    chapterCount = index.byChapter.size
  )
}

/** Use reviewed edition crosswalks, never carry a presentation-local bullet index. */
internal fun readerPositionForEdition(
  book: Book,
  storedStoryId: String?,
  sourceLanguage: String,
  targetLanguage: String,
  sourceEdition: String?,
  targetEdition: String,
  sourceAnchor: VerseAnchor? = null,
  resolveAnchor: (VerseAnchor) -> VerseAnchor? = { null }
): ReadingResume {
  val index = ChapterLocator.build(book)
  var fallback = readingResumeForBook(book, storedStoryId, sourceLanguage, targetLanguage)
  if (storedStoryId != null && fallback.storyId == null) {
    // An unrepresented chapter can occur at an edition boundary. Keep nearby
    // chapter context; no verse highlight claims an unverified equivalence.
    val wantedChapter = resolveStoryIdAcrossLanguages(storedStoryId, sourceLanguage, targetLanguage)
      ?.substringAfterLast('-')?.toIntOrNull()
    val chapter = wantedChapter?.let { wanted -> index.byChapter.keys.filter { it <= wanted }.maxOrNull() }
      ?: index.byChapter.keys.minOrNull()
    fallback = readingResumeForBook(book, chapter?.let(index.byChapter::get), targetLanguage, targetLanguage)
  }
  if (LocaleUtils.effectiveAssetTag(sourceLanguage) != LocaleUtils.effectiveAssetTag(targetLanguage)) {
    return fallback
  }
  val canonicalSourceEdition = sourceEdition?.let { BibleEditions.canonicalId(sourceLanguage, it) }
    ?: return fallback
  val sameEdition = canonicalSourceEdition == targetEdition
  val mapped = if (sameEdition) sourceAnchor else {
    val anchor = sourceAnchor ?: storedStoryId?.substringAfterLast('-')?.toIntOrNull()
      ?.takeIf { it > 0 }?.let { VerseAnchor(it, 1) }
    anchor?.let(resolveAnchor)
  } ?: return fallback
  val storyId = index.byChapter[mapped.chapter] ?: return fallback
  val story = book.stories.firstOrNull { it.id == storyId } ?: return fallback
  val native = fullNativeVerseAnchor(mapped, story.summaryBullets.mapNotNull {
    parseTrailingVerseAnchor(it, story.id)?.anchor
  }) ?: return fallback
  return fallback.copy(
    storyId = storyId,
    chapter = native.chapter,
    verse = native.verseStart,
    verseEnd = native.verseEnd
  )
}
