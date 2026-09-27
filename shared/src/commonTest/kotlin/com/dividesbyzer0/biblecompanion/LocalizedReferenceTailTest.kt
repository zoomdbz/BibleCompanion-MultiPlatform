package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizedReferenceTailTest {

  @Test
  fun normalizesChineseChapterAndVerseGrammar() {
    assertEquals("53", ScriptureRefs.normalizeCjkTail("第53章"))
    assertEquals("53:1", ScriptureRefs.normalizeCjkTail("第53章第1节"))
    assertEquals("28:12-19", ScriptureRefs.normalizeCjkTail("第28章12-19节"))
  }

  @Test
  fun normalizesJapaneseAndKoreanChapterAndVerseGrammar() {
    assertEquals("3:16", ScriptureRefs.normalizeCjkTail("3章16節"))
    assertEquals("3:16", ScriptureRefs.normalizeCjkTail("3장16절"))
    assertEquals("3", ScriptureRefs.normalizeCjkTail("제3장"))
  }

  @Test
  fun arabicCommaNormalizationKeepsEveryDisplayOffsetAligned() {
    val display = "12:6، 14"
    val scan = ScriptureRefs.normalizeReferenceScanText(display)

    assertEquals("12:6, 14", scan)
    assertEquals(display.length, scan.length)
    assertEquals(scan.length, ScriptureRefs.scanRefTail(scan, 0))
    val fullDisplay = "الرؤيا $display"
    val annotations = ScriptureRefs.referenceLinkSegments(fullDisplay, "الرؤيا ".length, scan)
    assertEquals(2, annotations.size)
    assertEquals("الرؤيا 12:6", fullDisplay.substring(annotations[0].start, annotations[0].endExclusive))
    assertEquals("12:6", annotations[0].tail)
    assertEquals("14", fullDisplay.substring(annotations[1].start, annotations[1].endExclusive))
    assertEquals("12:14", annotations[1].tail)
    assertEquals(
      ScriptureRefs.InternalRefTarget("12", 6, null),
      ScriptureRefs.parseInternalRefTail("revelation", annotations[0].tail)
    )
    assertEquals(
      ScriptureRefs.InternalRefTarget("12", 14, null),
      ScriptureRefs.parseInternalRefTail("revelation", annotations[1].tail)
    )
  }

  @Test
  fun germanPeriodVerseListsAreConsumedOnlyAfterContinentalChapterGrammar() {
    val list = "53,4-6.10-12"
    assertEquals(list.length, ScriptureRefs.scanRefTail(list, 0))
    val display = "Jesaja $list"
    val annotations = ScriptureRefs.referenceLinkSegments(display, "Jesaja ".length, list)
    assertEquals(2, annotations.size)
    assertEquals("Jesaja 53,4-6", display.substring(annotations[0].start, annotations[0].endExclusive))
    assertEquals("53:4-6", annotations[0].tail)
    assertEquals("10-12", display.substring(annotations[1].start, annotations[1].endExclusive))
    assertEquals("53:10-12", annotations[1].tail)
    assertEquals(
      ScriptureRefs.InternalRefTarget("53", 4, 6),
      ScriptureRefs.parseInternalRefTail("isaiah", annotations[0].tail)
    )
    assertEquals(
      ScriptureRefs.InternalRefTarget("53", 10, 12),
      ScriptureRefs.parseInternalRefTail("isaiah", annotations[1].tail)
    )

    // A colon reference does not opt into German list-period grammar. This
    // keeps an ordinary following period out of the reference annotation.
    assertEquals("12:6".length, ScriptureRefs.scanRefTail("12:6.14", 0))
    assertEquals(
      "The period lasted 1.260 days.",
      ScriptureRefs.normalizeReferenceScanText("The period lasted 1.260 days.")
    )
  }

  @Test
  fun crossChapterRangeProducesTwoExactNavigableEndpointAnnotations() {
    val display = "Revelation 19:11-20:10"
    val segments = ScriptureRefs.referenceLinkSegments(
      display = display,
      displayTailStart = "Revelation ".length,
      normalizedTail = "19:11-20:10"
    )

    assertEquals(2, segments.size)
    assertEquals("Revelation 19:11", display.substring(segments[0].start, segments[0].endExclusive))
    assertEquals("19:11", segments[0].tail)
    assertEquals("20:10", display.substring(segments[1].start, segments[1].endExclusive))
    assertEquals("20:10", segments[1].tail)
    assertEquals("-", display.substring(segments[0].endExclusive, segments[1].start))
    assertEquals(
      ScriptureRefs.InternalRefTarget("19", 11, null),
      ScriptureRefs.parseInternalRefTail("revelation", segments[0].tail)
    )
    assertEquals(
      ScriptureRefs.InternalRefTarget("20", 10, null),
      ScriptureRefs.parseInternalRefTail("revelation", segments[1].tail)
    )
  }

  @Test
  fun germanCrossChapterRangeKeepsDisplayPunctuationButUsesColonDestinations() {
    val display = "Jesaja 8,23-9,1"
    val segments = ScriptureRefs.referenceLinkSegments(
      display = display,
      displayTailStart = "Jesaja ".length,
      normalizedTail = "8,23-9,1"
    )

    assertEquals(2, segments.size)
    assertEquals("Jesaja 8,23", display.substring(segments[0].start, segments[0].endExclusive))
    assertEquals("8:23", segments[0].tail)
    assertEquals("9,1", display.substring(segments[1].start, segments[1].endExclusive))
    assertEquals("9:1", segments[1].tail)
    assertEquals(
      ScriptureRefs.InternalRefTarget("8", 23, null),
      ScriptureRefs.parseInternalRefTail("isaiah", segments[0].tail)
    )
    assertEquals(
      ScriptureRefs.InternalRefTarget("9", 1, null),
      ScriptureRefs.parseInternalRefTail("isaiah", segments[1].tail)
    )
  }

  @Test
  fun fullwidthCrossChapterSeparatorsUseNormalizedScanOffsets() {
    val display = "Revelation \uFF11\uFF19\uFF1A\uFF11\uFF11\uFF0D\uFF12\uFF10\uFF1A\uFF11\uFF10"
    val segments = ScriptureRefs.referenceLinkSegments(
      display = display,
      displayTailStart = "Revelation ".length,
      normalizedTail = "19:11-20:10"
    )

    assertEquals(2, segments.size)
    assertEquals(
      "Revelation \uFF11\uFF19\uFF1A\uFF11\uFF11",
      display.substring(segments[0].start, segments[0].endExclusive)
    )
    assertEquals("19:11", segments[0].tail)
    assertEquals(
      "\uFF12\uFF10\uFF1A\uFF11\uFF10",
      display.substring(segments[1].start, segments[1].endExclusive)
    )
    assertEquals("20:10", segments[1].tail)
  }

  @Test
  fun everyNoncontiguousListComponentGetsItsOwnDestination() {
    val display = "Revelation 22:7, 12, 16-17, 20"
    val segments = ScriptureRefs.referenceLinkSegments(display, 11, "22:7, 12, 16-17, 20")

    assertEquals(listOf("22:7", "22:12", "22:16-17", "22:20"), segments.map { it.tail })
    assertEquals(
      listOf("Revelation 22:7", "12", "16-17", "20"),
      segments.map { display.substring(it.start, it.endExclusive) }
    )
    assertEquals(
      listOf(
        ScriptureRefs.InternalRefTarget("22", 7, null),
        ScriptureRefs.InternalRefTarget("22", 12, null),
        ScriptureRefs.InternalRefTarget("22", 16, 17),
        ScriptureRefs.InternalRefTarget("22", 20, null)
      ),
      segments.map { ScriptureRefs.parseInternalRefTail("revelation", it.tail) }
    )
  }

  @Test
  fun ordinaryRangesAndProseDecimalsDoNotSplitIntoEndpointAnnotations() {
    val ordinary = ScriptureRefs.referenceLinkSegments("Matthew 24:29-31", 8, "24:29-31")
    assertEquals(1, ordinary.size)
    assertEquals("24:29-31", ordinary.single().tail)

    val prose = ScriptureRefs.referenceLinkSegments("1.260 days", 0, "1.260")
    assertEquals(1, prose.size)
    assertTrue(prose.single().start == 0 && prose.single().endExclusive == "1.260 days".length)
  }
}
