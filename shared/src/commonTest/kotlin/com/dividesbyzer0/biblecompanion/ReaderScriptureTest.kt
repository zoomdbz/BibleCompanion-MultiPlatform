package com.dividesbyzer0.biblecompanion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderScriptureTest {
  @Test
  fun versePresentationMovesOnlyTheTrailingAnchor() {
    val story = story(
      bullets = listOf(
        "[J]Jesus answered[/J]; see John 3:16 and https://example.test. (3:4-5).",
        "Raw editorial line with (not an anchor)."
      )
    )

    val verses = readerVersePresentations(story)

    assertEquals("4-5", verses[0].label)
    assertEquals(
      "[J]Jesus answered[/J]; see John 3:16 and https://example.test.",
      verses[0].text
    )
    assertNull(verses[1].label)
    assertEquals("Raw editorial line with (not an anchor).", verses[1].text)
  }

  @Test
  fun markerPeriodCompletesUnpunctuatedTextWithoutDuplicatingExistingPunctuation() {
    val story = story(
      bullets = listOf(
        "He went to Mount Seir (4:42).",
        "In the beginning God created. (1:1).",
        "The LORD reigns! (16:31).",
        "A hanging clause\u2014 (16:15).",
        "[J]Yes.[/J] (1:6)."
      )
    )

    assertEquals(
      listOf(
        "He went to Mount Seir.",
        "In the beginning God created.",
        "The LORD reigns!",
        "A hanging clause\u2014",
        "[J]Yes.[/J]"
      ),
      readerVersePresentations(story).map { it.text }
    )
  }

  @Test
  fun enDashRangeStaysOneVerseUnit() {
    val story = story(bullets = listOf("One source verse unit. (8:3\u20134)."))

    val verse = readerVersePresentations(story).single()

    assertEquals(VerseAnchor(8, 3, 4), verse.anchor)
    assertEquals("3-4", verse.label)
    assertEquals("One source verse unit.", verse.text)
    assertEquals("8:3-4", readerNativeReferenceTail(verse.anchor!!))
  }

  @Test
  fun continuousModeBreaksOnlyAtHeadingsAndExplicitNewlines() {
    val story = story(
      bullets = listOf(
        "First. (1:1).",
        "Second. (1:2).",
        "Third with an explicit\nline break. (1:3).",
        "Fourth. (1:4).",
        "Fifth. (1:5)."
      ),
      headings = listOf(Heading(beforeVerse = 4, text = "A real heading"))
    )

    val blocks = readerScriptureBlocks(story, versePerLine = false)

    assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), blocks.map { block ->
      block.verses.map { it.bulletIndex }
    })
    assertNull(blocks[0].heading)
    assertEquals("A real heading", blocks[1].heading)
  }

  @Test
  fun versePerLineCreatesOneBlockPerSourceVerseUnit() {
    val story = story(
      bullets = listOf("First. (2:1).", "Combined range. (2:2-3).", "Raw line."),
      headings = listOf(Heading(beforeVerse = 2, text = "Heading"))
    )

    val blocks = readerScriptureBlocks(story, versePerLine = true)

    assertEquals(3, blocks.size)
    assertEquals(listOf("1", "2-3", null), blocks.map { it.verses.single().label })
    assertEquals("Heading", blocks[1].heading)
    assertEquals("Raw line.", blocks[2].verses.single().text)
  }

  @Test
  fun markedPresentationRetainsInlineTagsAndRawText() {
    val marked = readerPresentationText(
      listOf(
        ReaderTextVerse(7, "10", "[DN]LORD[/DN] spoke."),
        ReaderTextVerse(8, null, "Raw line.")
      ),
      separator = " "
    )

    assertTrue("[DN]LORD[/DN] spoke." in marked)
    assertTrue("Raw line." in marked)
    assertFalse("(1:10)" in marked)
  }

  @Test
  fun finalUrlStopsBeforeTheReaderClosingMarker() {
    val url = "https://example.test/final"
    val marked = readerPresentationText(
      listOf(ReaderTextVerse(0, "1", url)),
      separator = " "
    )
    val start = marked.indexOf(url)
    val end = scriptureUrlTokenEnd(marked, start)

    assertEquals(url, marked.substring(start, end))
  }

  @Test
  fun accessibilityVersesKeepLinksScopedToTheirSourceVerse() {
    val parsed = buildAnnotatedString {
      append("1 First link")
      addStringAnnotation("READER_VERSE", "0", 0, length)
      addStringAnnotation("BIBLE_REF", "first", 8, 12)
      val secondStart = length
      append(" 2 Second URL")
      addStringAnnotation("READER_VERSE", "1", secondStart, length)
      addStringAnnotation("URL", "https://example.test", length - 3, length)
    }

    val verses = readerAccessibleVerses(
      parsed,
      mapOf(0 to "1:1", 1 to "1:2")
    )

    assertEquals(listOf("1", "2"), verses.map { it.label })
    assertEquals(1, verses[0].links.size)
    assertEquals(1, verses[1].links.size)
    assertEquals("link", verses[0].links.single().label)
    assertEquals("URL", verses[1].links.single().label)
  }

  @Test
  fun accessibilityTextKeepsAuthoredLeadingDigitForAnchorlessVerse() {
    val parsed = buildAnnotatedString {
      append("1 authored point remains intact")
      addStringAnnotation("READER_VERSE", "0", 0, length)
    }

    val verse = readerAccessibleVerses(parsed, emptyMap()).single()

    assertEquals("1 authored point remains intact", verse.text)
  }

  @Test
  fun contextualSingleChapterAnchorRendersAsOneVerseUnit() {
    val verse = readerVersePresentations(
      story(bullets = listOf("Daniel answered. (3)."), id = "bel-1")
    ).single()

    assertEquals(VerseAnchor(1, 3, 3), verse.anchor)
    assertEquals("3", verse.label)
    assertEquals("Daniel answered.", verse.text)
  }

  @Test
  fun savedHighlightPaletteKeepsUnknownColorsTransparent() {
    assertEquals(Color.Transparent, readerHighlightBgColor("unknown", isDark = false))
    assertTrue(readerHighlightBgColor("yellow", isDark = false).alpha > 0f)
    assertTrue(readerHighlightBgColor("yellow", isDark = true).alpha >
      readerHighlightBgColor("yellow", isDark = false).alpha)
  }

  @Test
  fun dynamicVerseStylesLayerOntoParsedAnnotationsWithoutChangingText() {
    val parsed = buildAnnotatedString {
      append("1 In the beginning")
      addStringAnnotation("READER_VERSE", "4", 0, length)
      addStringAnnotation("BIBLE_REF", "native-reference", 0, 1)
    }

    val styled = applyReaderVerseStyles(
      parsed,
      mapOf(4 to SpanStyle(background = Color.Red))
    )

    assertEquals(parsed.text, styled.text)
    assertEquals(
      parsed.getStringAnnotations("READER_VERSE", 0, parsed.length),
      styled.getStringAnnotations("READER_VERSE", 0, styled.length)
    )
    assertEquals(
      parsed.getStringAnnotations("BIBLE_REF", 0, 1),
      styled.getStringAnnotations("BIBLE_REF", 0, 1)
    )
    assertTrue(styled.spanStyles.any { range ->
      range.start == 0 && range.end == styled.length && range.item.background == Color.Red
    })
  }

  private fun story(
    bullets: List<String>,
    headings: List<Heading> = emptyList(),
    id: String = "test-1"
  ) = Story(
    id = id,
    title = "Test",
    refs = listOf("Test 1"),
    summaryBullets = bullets,
    headings = headings
  )
}
