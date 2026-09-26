package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerseSelectionTest {
  @Test
  fun pickerIncludesEveryVerseInARangeAndInteriorVerseFindsItsBullet() {
    val bullets = listOf(
      "First sentence. (1:74-75).",
      "Second sentence. (1:76).",
      "Other chapter. (2:1)."
    )
    assertEquals(listOf(74, 75, 76), versePickerNumbers(bullets, 1))
    assertEquals(setOf(0), findBulletsForVerseRange(bullets, 75, 75))
  }

  @Test
  fun disjointSelectionKeepsExactCompactVersesAndSafeBibleComFallback() {
    val story = Story(
      id = "john-3",
      title = "John 3",
      refs = listOf("John 3:1-36"),
      summaryBullets = listOf(
        "First selected verse. (3:16).",
        "Not selected. (3:17).",
        "Second selected verse. (3:18)."
      )
    )
    val book = Book("john", "John", listOf(story))
    val content = buildSelectedContent(book, setOf("john-3" to 0, "john-3" to 2))

    assertEquals("John 3:16,18", content.primaryRef)
    assertTrue(content.text.lineSequence().first().endsWith("3:16,18"))
    assertTrue("Not selected" !in content.text)
    assertEquals(
      "https://www.bible.com/bible/1/JHN.3.KJV",
      Linker.buildBibleComUrl(content.primaryRef!!, "KJV")
    )
  }

  @Test
  fun selectedRangesMergeOnlyCoveredAdjacentVerses() {
    val anchors = listOf(
      VerseAnchor(3, 16), VerseAnchor(3, 18, 19), VerseAnchor(3, 17), VerseAnchor(4, 2)
    )
    assertEquals("3:16-19,4:2", compactSelectedVerseTail(anchors))
  }

  @Test
  fun chapterSpeechUsesTheDisplayedDivineNameAndRemovesInlineTags() {
    val bullet = "The LORD God spoke and [J]Jesus answered[/J]. (2:4)."
    val spoken = ttsCleanBullet(bullet, "yahweh", "en", false, "old_testament")
    assertTrue("Yahweh God" in spoken)
    assertTrue("LORD" !in spoken && "[J]" !in spoken && "(2:4)" !in spoken)
    assertEquals(
      "The LORD God spoke and Jesus answered.",
      ttsCleanBullet(bullet, "traditional", "en", false, "old_testament")
    )
  }
}
