package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleChronologyTest {

  @Test
  fun chronologyContainsEveryRequestedBookAcrossAllEpochs() {
    assertEquals(ChronologyEpochId.entries.size, BibleChronologyData.epochs.size)
    val errors = BibleChronologyData.validationErrors()
    assertTrue(errors.isEmpty(), errors.joinToString("\n"))
  }

  @Test
  fun psalmAttributionGroupsPrecedeSecondTempleAndCoverEveryPsalmOnce() {
    val psalmIndex = BibleChronologyData.epochs.indexOfFirst {
      it.id == ChronologyEpochId.PSALMS_COLLECTION
    }
    assertTrue(psalmIndex >= 0)
    assertEquals(ChronologyEpochId.SECOND_TEMPLE, BibleChronologyData.epochs[psalmIndex + 1].id)

    val entries = BibleChronologyData.epochs[psalmIndex].entries
    assertEquals(PsalmAttribution.entries.toSet(), entries.mapNotNull { it.psalmAttribution }.toSet())
    val psalms = entries.flatMap { parseChronologyChapterRange(it.chapterRange) }
    assertEquals(150, psalms.size)
    assertEquals((1..150).toList(), psalms.sorted())
  }

  @Test
  fun chronologyExpandedEpochPreferenceRoundTripsAndIgnoresUnknownIds() {
    val selected = setOf(
      ChronologyEpochId.CREATION_EARLY_HISTORY,
      ChronologyEpochId.PSALMS_COLLECTION
    )
    val encoded = encodeChronologyExpandedEpochs(selected)
    assertEquals("CREATION_EARLY_HISTORY,PSALMS_COLLECTION", encoded)
    assertEquals(selected, decodeChronologyExpandedEpochs("$encoded,REMOVED_EPOCH"))
    assertTrue(decodeChronologyExpandedEpochs("").isEmpty())
    assertEquals(
      setOf(ChronologyEpochId.CREATION_EARLY_HISTORY),
      decodeChronologyExpandedEpochs(DEFAULT_CHRONOLOGY_EXPANDED_EPOCHS)
    )
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
