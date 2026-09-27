package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditionOverlayValidationTest {
  private fun overlay(
    schemaVersion: Int = 2,
    lastVerse: Int? = 3,
    verseUnitCount: Int? = 2,
    verses: List<EditionVerse> = listOf(
      EditionVerse(chapter = 1, verse = 1, text = "First"),
      EditionVerse(chapter = 1, verse = 2, verseEnd = 3, text = "Second and third")
    ),
    headings: List<Heading>? = listOf(Heading(beforeVerse = 2, text = "Heading"))
  ) = EditionBookOverlay(
    schemaVersion = schemaVersion,
    editionId = "traditional",
    language = "de",
    collection = "old_testament",
    bookId = "genesis",
    sourceBookCode = "GEN",
    coverage = "full",
    chapters = listOf(
      EditionChapter(
        number = 1,
        headings = headings,
        lastVerse = lastVerse,
        verseUnitCount = verseUnitCount,
        verses = verses
      )
    )
  )

  private fun EditionBookOverlay.valid(): Boolean = isStructurallyValid(
    expectedEditionId = "traditional",
    expectedLanguage = "de",
    expectedCollection = "old_testament",
    expectedBookId = "genesis",
    expectedChapterNumbers = setOf(1)
  )

  @Test
  fun schemaTwoAcceptsContiguousVerseRangesAndUnitStartHeadings() {
    assertTrue(overlay().valid())
  }

  @Test
  fun schemaTwoRejectsIncorrectIntegrityMetadata() {
    assertFalse(overlay(lastVerse = 2).valid())
    assertFalse(overlay(verseUnitCount = 3).valid())
  }

  @Test
  fun overlayRejectsGapsAndHeadingsInsideCombinedRanges() {
    assertFalse(overlay(verses = listOf(
      EditionVerse(chapter = 1, verse = 1, text = "First"),
      EditionVerse(chapter = 1, verse = 3, text = "Third")
    )).valid())
    assertFalse(overlay(headings = listOf(Heading(beforeVerse = 3, text = "Inside range"))).valid())
  }

  @Test
  fun overlayRejectsAMissingEditionHeadingTable() {
    assertFalse(overlay(headings = null).valid())
  }

  @Test
  fun legacySchemaOneDoesNotRequireSchemaTwoMetadata() {
    assertTrue(overlay(schemaVersion = 1, lastVerse = null, verseUnitCount = null).valid())
  }
}
