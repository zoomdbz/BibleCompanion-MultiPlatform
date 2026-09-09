package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleChronologyTest {

  @Test
  fun chronologyContainsEveryRequestedBookAcrossTwelveEpochs() {
    assertEquals(12, BibleChronologyData.epochs.size)
    val errors = BibleChronologyData.validationErrors()
    assertTrue(errors.isEmpty(), errors.joinToString("\n"))
  }

  @Test
  fun requestedChapterWinsAndMissingChapterFallsBackToFirstStory() {
    val book = Book(
      id = "sample",
      title = "Sample",
      stories = listOf(
        Story("sample-1", "One", emptyList(), emptyList()),
        Story("sample-12", "Twelve", emptyList(), emptyList())
      )
    )

    assertEquals("sample-12", chronologyOpeningStoryId(book, 12))
    assertEquals("sample-1", chronologyOpeningStoryId(book, 99))
  }
}
