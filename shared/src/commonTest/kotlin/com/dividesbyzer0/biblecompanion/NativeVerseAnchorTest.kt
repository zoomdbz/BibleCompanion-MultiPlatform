package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeVerseAnchorTest {
  @Test
  fun parsesLocalizedChapterVerseMarkersWithoutChangingSource() {
    val japanese = "\u5F7C\u3089\u306F\u30A8\u30EB\u30B5\u30EC\u30E0\u306B\u4E0A\u3063\u305F\uFF081:1\uFF09\u3002"
    val parsedJapanese = parseTrailingVerseAnchor(japanese)
    assertEquals(VerseAnchor(1, 1), parsedJapanese?.anchor)
    assertEquals(
      "\uFF081:1\uFF09\u3002",
      parsedJapanese?.rawMarkerRange?.let { range -> japanese.substring(range) }
    )
    assertEquals("\u3002", parsedJapanese?.trailingPunctuation)
    assertEquals(japanese.indexOf('\uFF08'), parsedJapanese?.rawStart)
    assertEquals(parsedJapanese?.anchor, verseAnchorFromText(japanese))

    val hindi = "\u092A\u094D\u0930\u092D\u0941 \u0928\u0947 \u0909\u0928\u094D\u0939\u0947\u0902 \u092C\u0941\u0932\u093E\u092F\u093E\u0964 (2:1)\u0964"
    assertEquals(VerseAnchor(2, 1), parseTrailingVerseAnchor(hindi)?.anchor)

    val chineseRange = "\u7ECF\u6587\uFF081\uFF1A2\u20133\uFF09\u3002"
    assertEquals(VerseAnchor(1, 2, 3), parseTrailingVerseAnchor(chineseRange)?.anchor)
  }

  @Test
  fun preservesOrdinaryAsciiMarkerMetadata() {
    val source = "Jesus wept. (11:35)."
    val parsed = parseTrailingVerseAnchor(source)
    assertEquals(VerseAnchor(11, 35), parsed?.anchor)
    assertEquals("(11:35).", parsed?.rawMarkerRange?.let { range -> source.substring(range) })
    assertEquals(".", parsed?.trailingPunctuation)
    assertEquals(verseAnchorFromText(source), parsed?.anchor)
  }

  @Test
  fun singleChapterMarkersRequireAuditedStoryContext() {
    val susanna = "In Babylon there lived a man named Joakim. (1)."
    assertNull(parseTrailingVerseAnchor(susanna))
    assertEquals(
      VerseAnchor(1, 1),
      parseTrailingVerseAnchor(susanna, storyId = "susanna-1", bookId = "susanna")?.anchor
    )
    assertNull(
      parseTrailingVerseAnchor(susanna, storyId = "susanna-1", bookId = "daniel")
    )

    assertEquals(
      listOf(1, 2, 3),
      versePickerNumbers(
        listOf("First. (1).", "Bridge. (2-3)."),
        chapter = 1,
        storyId = "bel-1",
        bookId = "bel_and_the_dragon"
      )
    )
  }

  @Test
  fun rejectsProseParenthesesFootnotesAndNonTrailingMarkers() {
    assertNull(parseTrailingVerseAnchor("A numbered observation (12)."))
    assertNull(parseTrailingVerseAnchor("See footnote (3) for manuscript evidence."))
    assertNull(parseTrailingVerseAnchor("The event (1:2) continues after the citation."))
    assertNull(parseTrailingVerseAnchor("Malformed (1:)."))
  }
}
