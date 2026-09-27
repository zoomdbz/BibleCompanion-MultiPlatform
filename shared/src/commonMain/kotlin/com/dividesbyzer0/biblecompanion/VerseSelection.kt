package com.dividesbyzer0.biblecompanion

/** A link can highlight only one contiguous, same-chapter selection. */
internal fun contiguousSelectionAnchor(anchors: List<VerseAnchor>): VerseAnchor? {
  val sorted = anchors.sortedWith(compareBy({ it.chapter }, { it.verseStart }))
  val first = sorted.firstOrNull() ?: return null
  if (first.chapter < 1 || first.verseStart < 1 || first.verseEnd < first.verseStart) return null
  var end = first.verseEnd
  for (anchor in sorted.drop(1)) {
    if (anchor.chapter != first.chapter || anchor.verseStart > end + 1 ||
      anchor.verseStart < 1 || anchor.verseEnd < anchor.verseStart) return null
    end = maxOf(end, anchor.verseEnd)
  }
  return VerseAnchor(first.chapter, first.verseStart, end)
}

/** Keep a source's combined verse intact when a link names only part of it. */
internal fun fullNativeVerseAnchor(anchor: VerseAnchor, units: List<VerseAnchor>): VerseAnchor? {
  if (anchor.chapter < 1 || anchor.verseStart < 1 || anchor.verseEnd < anchor.verseStart ||
    anchor.verseEnd - anchor.verseStart > 1000) return null
  val chapterUnits = units.filter {
    it.chapter == anchor.chapter && it.verseStart > 0 && it.verseEnd >= it.verseStart &&
      it.verseEnd - it.verseStart <= 1000
  }
  if (!(anchor.verseStart..anchor.verseEnd).all { verse ->
      chapterUnits.any { verse in it.verseStart..it.verseEnd }
    }) return null
  val touching = chapterUnits.filter {
    it.verseStart <= anchor.verseEnd && it.verseEnd >= anchor.verseStart
  }
  return VerseAnchor(anchor.chapter, touching.minOf { it.verseStart }, touching.maxOf { it.verseEnd })
}

/** Every verse covered by a trailing bullet marker, including range interiors. */
internal fun versePickerNumbers(bullets: List<String>, chapter: Int): List<Int> =
  versePickerNumbers(bullets, chapter, storyId = null, bookId = null)

/** Single-chapter source markers are accepted only with their audited story context. */
internal fun versePickerNumbers(
  bullets: List<String>,
  chapter: Int,
  storyId: String?,
  bookId: String? = null
): List<Int> =
  bullets.asSequence()
    .mapNotNull { parseTrailingVerseAnchor(it, storyId, bookId)?.anchor }
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
