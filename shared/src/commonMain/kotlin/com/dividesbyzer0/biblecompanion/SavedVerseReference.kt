package com.dividesbyzer0.biblecompanion

/**
 * Renders a saved verse's exact native anchor while retaining the book prefix
 * supplied by the story reference. The prefix is not parsed or translated;
 * this only recognizes the saved anchor's known ` chapter:` boundary.
 */
internal fun savedVerseReference(storyReference: String, anchor: VerseAnchor?): String {
  if (anchor == null || storyReference.isBlank()) return storyReference
  val chapterBoundary = " ${anchor.chapter}:"
  val boundary = storyReference.lastIndexOf(chapterBoundary)
  if (boundary <= 0) return storyReference

  val bookPrefix = storyReference.substring(0, boundary)
  val verseRange = buildString {
    append(anchor.chapter).append(':').append(anchor.verseStart)
    if (anchor.verseEnd != anchor.verseStart) append('-').append(anchor.verseEnd)
  }
  return "$bookPrefix $verseRange"
}

/** Repairs legacy broad chapter labels when the saved text or fields prove an exact anchor. */
internal fun SavedVerse.displayReference(): String = savedVerseReference(ref, stableAnchor())
