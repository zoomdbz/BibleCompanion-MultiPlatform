package com.dividesbyzer0.biblecompanion

/** Every verse covered by a trailing bullet marker, including range interiors. */
internal fun versePickerNumbers(bullets: List<String>, chapter: Int): List<Int> =
  bullets.asSequence()
    .mapNotNull(::verseAnchorFromText)
    .filter { it.chapter == chapter && it.verseStart <= it.verseEnd }
    .flatMap { (it.verseStart..it.verseEnd).asSequence() }
    .distinct()
    .sorted()
    .toList()

/** Exact, compact chapter:verse segments for selected bullets. */
internal fun compactSelectedVerseTail(anchors: List<VerseAnchor>): String? {
  if (anchors.isEmpty()) return null
  return anchors.groupBy { it.chapter }
    .toList()
    .sortedBy { it.first }
    .joinToString(",") { (chapter, chapterAnchors) ->
      val ranges = chapterAnchors
        .map { minOf(it.verseStart, it.verseEnd)..maxOf(it.verseStart, it.verseEnd) }
        .sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
      val merged = mutableListOf<IntRange>()
      for (range in ranges) {
        val previous = merged.lastOrNull()
        if (previous != null && range.first <= previous.last + 1) {
          merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
        } else {
          merged += range
        }
      }
      "$chapter:" + merged.joinToString(",") { span ->
        if (span.first == span.last) "${span.first}" else "${span.first}-${span.last}"
      }
    }
}
