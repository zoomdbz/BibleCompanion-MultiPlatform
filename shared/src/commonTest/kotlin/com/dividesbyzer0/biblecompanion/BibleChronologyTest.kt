package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleChronologyTest {

  private fun epoch(id: ChronologyEpochId) = BibleChronologyData.epochs.first { it.id == id }

  @Test
  fun readingOrderPlacesJobBetweenTheTwoGenesisSectionsWithoutClaimingAnExactDate() {
    val entries = BibleChronologyData.epochs.flatMap { it.readingEntries(false) }
    assertEquals(listOf("genesis", "job", "genesis"), entries.take(3).map { it.bookId })
    assertEquals(listOf("1-11", "1-42", "12-50"), entries.take(3).map { it.chapterRange })
    assertEquals(ChronologyBasis.TRADITIONAL_SETTING, entries[1].basis)
  }

  @Test
  fun conquestReadingOrderKeepsJoshuaJudgesAndRuthTogether() {
    val entries = epoch(ChronologyEpochId.CONQUEST_JUDGES).readingEntries(false)
    assertEquals(listOf("joshua", "judges", "ruth"), entries.map { it.bookId })
    assertTrue(entries.all { it.lane == ChronologyLane.HISTORICAL_FLOW })
    assertEquals(ChronologyBasis.PARALLEL_ACCOUNT, entries.last().basis)
  }

  @Test
  fun solomonWisdomReadingsComeBeforeHisClosingKingsChapter() {
    val entries = epoch(ChronologyEpochId.UNITED_KINGDOM).readingEntries(false)
    val beginning = entries.indexOfFirst { it.bookId == "1_kings" && it.chapterRange == "1-10" }
    val ending = entries.indexOfFirst { it.bookId == "1_kings" && it.chapterRange == "11" }
    assertTrue(beginning >= 0 && ending > beginning)
    for (book in listOf("proverbs", "song_of_songs", "ecclesiastes")) {
      assertTrue(entries.indexOfFirst { it.bookId == book } in (beginning + 1) until ending)
    }
    assertEquals(
      ChronologyBasis.SPANS_MULTIPLE_PERIODS,
      entries.first { it.bookId == "proverbs" }.basis
    )
  }

  @Test
  fun restorationPlacesProphetsAndEstherBetweenEzrasReturns() {
    val entries = epoch(ChronologyEpochId.RETURN_RESTORATION).readingEntries(false)
    assertEquals(
      listOf("ezra", "haggai", "zechariah", "esther", "ezra", "nehemiah", "malachi"),
      entries.take(7).map { it.bookId }
    )
    assertEquals("1-6", entries[0].chapterRange)
    assertEquals("7-10", entries[4].chapterRange)
    assertEquals(7, entries[4].openingChapter)
  }

  @Test
  fun readingStepNumbersFollowVisibleEntriesWithoutRegroupingLanes() {
    for (includeDeuterocanon in listOf(false, true)) {
      var expectedStep = 1
      for (epoch in BibleChronologyData.epochs) {
        val expectedEntries = epoch.entries.filter {
          includeDeuterocanon || it.collection != "deuterocanonical"
        }
        assertEquals(expectedEntries, epoch.readingEntries(includeDeuterocanon))
        assertEquals(expectedStep, BibleChronologyData.readingStepStart(epoch.id, includeDeuterocanon))
        expectedStep += expectedEntries.size
      }
    }
  }

  @Test
  fun splitNarrativesRetainEveryChapterExactlyOnce() {
    val entries = BibleChronologyData.epochs.flatMap { it.entries }
    for ((book, count) in mapOf(
      "genesis" to 50, "1_chronicles" to 29, "2_chronicles" to 36,
      "1_kings" to 22, "2_kings" to 25, "ezra" to 10,
      "isaiah" to 66, "jeremiah" to 52
    )) {
      val chapters = entries.filter { it.bookId == book }
        .flatMap { parseChronologyChapterRange(it.chapterRange) }
      assertEquals((1..count).toList(), chapters.sorted(), book)
    }
    assertTrue(entries.all { it.openingChapter in parseChronologyChapterRange(it.chapterRange) })
  }

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
