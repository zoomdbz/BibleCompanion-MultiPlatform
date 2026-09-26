package com.dividesbyzer0.biblecompanion

/**
 * Maps chapter numbers in the app's canonical (English/MT) navigation data to
 * the chapter ids used by a localized edition. Inputs to these functions must
 * be canonical ids, never chapter numbers already read from localized assets.
 */
internal fun canonicalChaptersToNative(bookId: String, chapter: Int, appLanguage: String): List<Int> {
  val language = LocaleUtils.effectiveAssetTag(appLanguage)
  if (bookId == "malachi" && language in setOf("de", "fr") && chapter == 4) return listOf(3)
  if (bookId != "psalms" || language != "ru") return listOf(chapter)
  return when (chapter) {
    in 1..9 -> listOf(chapter)
    10 -> listOf(9)
    in 11..113 -> listOf(chapter - 1)
    114, 115 -> listOf(113)
    116 -> listOf(114, 115)
    in 117..146 -> listOf(chapter - 1)
    147 -> listOf(146, 147)
    in 148..150 -> listOf(chapter)
    else -> listOf(chapter)
  }
}

/** Convert a canonical story id used by static search pins into native story ids. */
internal fun canonicalStoryIdsToNative(storyId: String, appLanguage: String): List<String> {
  val bookId = storyId.substringBeforeLast('-', missingDelimiterValue = "")
  if (bookId !in setOf("psalms", "malachi")) return listOf(storyId)
  val chapter = storyId.substringAfterLast('-').toIntOrNull() ?: return listOf(storyId)
  return canonicalChaptersToNative(bookId, chapter, appLanguage).map { "$bookId-$it" }
}

/**
 * Resolve a stored/shared chapter through canonical English/MT numbering.
 * A missing source tag means a pre-migration canonical ID. Same-language
 * navigation preserves the exact native chapter, including split Psalms.
 * Cross-language chapter-only links choose the first corresponding chapter;
 * they cannot identify which verse of a merged or split chapter was intended.
 */
internal fun resolveStoryIdAcrossLanguages(
  storyId: String?,
  sourceLanguage: String?,
  targetLanguage: String
): String? {
  if (storyId.isNullOrBlank()) return null
  val source = sourceLanguage?.let(LocaleUtils::effectiveAssetTag) ?: "en"
  val target = LocaleUtils.effectiveAssetTag(targetLanguage)
  if (source == target) return storyId
  val bookId = storyId.substringBeforeLast('-', missingDelimiterValue = "")
  if (bookId !in setOf("psalms", "malachi")) return storyId
  val chapter = storyId.substringAfterLast('-').toIntOrNull() ?: return null
  val canonical = when {
    bookId == "psalms" && source == "ru" ->
      (1..150).firstOrNull { chapter in canonicalChaptersToNative(bookId, it, source) }
    bookId == "malachi" && source in setOf("de", "fr") ->
      chapter.takeIf { it in 1..3 }
    else -> chapter.takeIf { it in 1..(if (bookId == "psalms") 150 else 4) }
  } ?: return null
  return canonicalChaptersToNative(bookId, canonical, target).firstOrNull()?.let { "$bookId-$it" }
}

/** Keep the chronology's displayed range in the same numbering as its click targets. */
internal fun canonicalRangeToNative(
  bookId: String,
  range: String,
  appLanguage: String,
  excludedCanonicalChapters: Set<Int> = emptySet()
): String {
  val language = LocaleUtils.effectiveAssetTag(appLanguage)
  if (!(bookId == "psalms" && language == "ru") &&
      !(bookId == "malachi" && language in setOf("de", "fr"))) return range
  val chapters = parseChronologyChapterRange(range)
    .filterNot { it in excludedCanonicalChapters }
    .flatMap { canonicalChaptersToNative(bookId, it, language) }
    .distinct()
    .sorted()
  if (chapters.isEmpty()) return range
  val spans = mutableListOf<IntRange>()
  var start = chapters.first()
  var end = start
  for (chapter in chapters.drop(1)) {
    if (chapter == end + 1) {
      end = chapter
    } else {
      spans += start..end
      start = chapter
      end = chapter
    }
  }
  spans += start..end
  return spans.joinToString(", ") { span ->
    if (span.first == span.last) "${span.first}" else "${span.first}-${span.last}"
  }
}
