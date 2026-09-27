package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderViewportAnchorTest {

  @Test
  fun selectsTheVerseIntersectingViewportTopBeforeLaterVerses() {
    assertTrue(
      isBetterReaderViewportAnchor(
        rootY = 80f, rootBottomY = 130f, viewportTopY = 100f, viewportBottomY = 300f,
        currentRootY = Float.POSITIVE_INFINITY, currentRootBottomY = Float.NEGATIVE_INFINITY
      )
    )
    assertFalse(
      isBetterReaderViewportAnchor(
        rootY = 140f, rootBottomY = 180f, viewportTopY = 100f, viewportBottomY = 300f,
        currentRootY = 80f, currentRootBottomY = 130f
      )
    )
  }

  @Test
  fun rejectsVerseOutsideViewportAndPrefersEarliestFullyVisibleVerse() {
    assertFalse(
      isBetterReaderViewportAnchor(
        rootY = 320f, rootBottomY = 350f, viewportTopY = 100f, viewportBottomY = 300f,
        currentRootY = Float.POSITIVE_INFINITY, currentRootBottomY = Float.NEGATIVE_INFINITY
      )
    )
    assertTrue(
      isBetterReaderViewportAnchor(
        rootY = 112f, rootBottomY = 150f, viewportTopY = 100f, viewportBottomY = 300f,
        currentRootY = 140f, currentRootBottomY = 180f
      )
    )
  }

  @Test
  fun selectionNeverAcceptsTheFirstMapEntryUnlessItIntersects() {
    val measurements = linkedMapOf(
      "chapter/0" to ReaderViewportMeasurement(20f, 80f, generation = 4),
      "chapter/1" to ReaderViewportMeasurement(110f, 150f, generation = 4)
    )

    assertEquals(
      "chapter/1",
      selectReaderViewportAnchor(
        measurements = measurements,
        generation = 4,
        viewportTopY = 100f,
        viewportBottomY = 300f
      )?.first
    )
  }

  @Test
  fun selectionRejectsSettledCollectionsWithNoVisibleVerse() {
    val measurements = linkedMapOf(
      "above/0" to ReaderViewportMeasurement(20f, 80f, generation = 2),
      "below/0" to ReaderViewportMeasurement(310f, 350f, generation = 2),
      "stale/0" to ReaderViewportMeasurement(120f, 160f, generation = 1)
    )

    assertNull(
      selectReaderViewportAnchor(
        measurements = measurements,
        generation = 2,
        viewportTopY = 100f,
        viewportBottomY = 300f
      )
    )
  }
}
