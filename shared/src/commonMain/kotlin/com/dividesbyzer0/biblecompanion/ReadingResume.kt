package com.dividesbyzer0.biblecompanion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext

internal data class ReadingResume(
  val title: String,
  val storyId: String?,
  val chapter: Int?,
  val chapterCount: Int
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
    val targetEdition = BibleEditions.forNavigation(
      appLanguage = prefs.appLanguage,
      current = prefs.internalBibleVersion,
      sourceLanguage = sourceLanguage,
      sourceEdition = prefs.lastReadSourceEdition
    )
    val targetBook = ContentRepo.loadBookOrNull(
      context = context,
      collection = collection,
      bookId = bookId,
      appLang = prefs.appLanguage,
      internalBibleVersion = targetEdition
    ) ?: return@remember null

    readingResumeForBook(
      book = targetBook,
      storedStoryId = prefs.lastReadStoryId,
      sourceLanguage = sourceLanguage,
      targetLanguage = prefs.appLanguage
    )
  }
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
