package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderViewportAnchorTest {

  @Test
  fun visibleItemSkipsPriorItemEndingAtViewportStart() {
    val items = listOf(
      ReaderVisibleItemMeasurement(index = 4, offset = -240, size = 240),
      ReaderVisibleItemMeasurement(index = 5, offset = 24, size = 300)
    )

    assertEquals(5, firstReaderVisibleItemIndex(items, viewportStartOffset = 0))
  }

  @Test
  fun visibleItemKeepsPriorItemWithActualPixelsInViewport() {
    val items = listOf(
      ReaderVisibleItemMeasurement(index = 4, offset = -240, size = 241),
      ReaderVisibleItemMeasurement(index = 5, offset = 25, size = 300)
    )

    assertEquals(4, firstReaderVisibleItemIndex(items, viewportStartOffset = 0))
  }

  @Test
  fun prefetchedNextChapterCannotReplaceGenuineVisibleAnchor() {
    val measurements = linkedMapOf(
      "mark-1/8" to ReaderViewportMeasurement(-31f, 143f, generation = 5),
      // Exact false candidate from the device trace. Its text had not received
      // real LayoutCoordinates, so the old default root placed it at zero.
      "mark-2/13" to ReaderViewportMeasurement(0f, 174f, generation = 5)
    )

    assertEquals(
      "mark-1/8",
      selectReaderViewportAnchor(
        measurements = measurements,
        generation = 5,
        viewportTopY = 98f,
        viewportBottomY = 950f,
        visibleStoryIds = setOf("mark-1")
      )?.first
    )
  }

  @Test
  fun restoreDeltaPreservesTheSavedViewportRelativeOffset() {
    assertEquals(
      430f,
      readerViewportScrollDelta(
        measurementRootY = 600f,
        viewportTopY = 120f,
        savedViewportOffset = 50f
      )
    )
    assertEquals(
      -35f,
      readerViewportScrollDelta(
        measurementRootY = 75f,
        viewportTopY = 100f,
        savedViewportOffset = 10f
      )
    )
  }

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
