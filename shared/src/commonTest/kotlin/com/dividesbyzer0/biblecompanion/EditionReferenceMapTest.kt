package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditionReferenceMapTest {
  private val psalm = EditionBookReferenceMap("psalms", complete = true, mappings = listOf(
    EditionReferenceRule(8, 4, targetChapter = 8, targetVerse = 3),
    EditionReferenceRule(8, 5, targetChapter = 8, targetVerse = 4),
    EditionReferenceRule(8, 6, targetChapter = 8, targetVerse = 5)
  ))

  @Test
  fun mapsThePassageInBothDirections() {
    assertEquals(VerseAnchor(8, 4), mapEditionReference(psalm, VerseAnchor(8, 5)))
    assertEquals(VerseAnchor(8, 5), mapEditionReference(psalm, VerseAnchor(8, 4), reverse = true))
    assertEquals(VerseAnchor(8, 3, 5), mapEditionReference(psalm, VerseAnchor(8, 4, 6)))
  }

  @Test
  fun completeMapDoesNotGuessAnUnmappedVerse() {
    assertNull(mapEditionReference(psalm, VerseAnchor(8, 99)))
  }

  @Test
  fun partialMapDoesNotAssumeUnreviewedIdentity() {
    assertNull(mapEditionReference(psalm.copy(complete = false), VerseAnchor(8, 99)))
    assertNull(mapEditionReference(psalm.copy(complete = false), VerseAnchor(8, 99), reverse = true))
    val reviewedIdentity = EditionBookReferenceMap("example", mappings = listOf(
      EditionReferenceRule(1, 1, 10, 1, 1, 10)
    ))
    assertEquals(VerseAnchor(1, 5), mapEditionReference(reviewedIdentity, VerseAnchor(1, 5)))
  }

  @Test
  fun preservesNativeMergedUnitsAndTheirReverseRange() {
    val merged = EditionBookReferenceMap("example", complete = true, mappings = listOf(
      EditionReferenceRule(1, 1, sourceVerseEnd = 2, targetChapter = 1, targetVerse = 1)
    ))
    assertEquals(VerseAnchor(1, 1), mapEditionReference(merged, VerseAnchor(1, 2)))
    assertEquals(VerseAnchor(1, 1, 2), mapEditionReference(merged, VerseAnchor(1, 1), reverse = true))
  }

  @Test
  fun refusesDisjointOrCrossChapterHighlightRanges() {
    val split = EditionBookReferenceMap("example", complete = true, mappings = listOf(
      EditionReferenceRule(1, 1, targetChapter = 1, targetVerse = 2),
      EditionReferenceRule(1, 2, targetChapter = 2, targetVerse = 1)
    ))
    assertNull(mapEditionReference(split, VerseAnchor(1, 1, 2)))
  }

  @Test
  fun compactRowsMapPositionallyNotToTheEntireChapter() {
    val compact = EditionBookReferenceMap("psalms", true, listOf(
      EditionReferenceRule(51, 4, 21, 51, 2, 19)
    ))
    assertEquals(VerseAnchor(51, 10), mapEditionReference(compact, VerseAnchor(51, 12)))
    assertEquals(VerseAnchor(51, 12), mapEditionReference(compact, VerseAnchor(51, 10), reverse = true))
    assertEquals(VerseAnchor(51, 10, 12), mapEditionReference(compact, VerseAnchor(51, 12, 14)))
  }

  @Test
  fun oneSourceVerseCanMapToTwoNativeVerses() {
    val split = EditionBookReferenceMap("psalms", true, listOf(
      EditionReferenceRule(13, 6, targetChapter = 13, targetVerse = 5, targetVerseEnd = 6)
    ))
    assertEquals(VerseAnchor(13, 5, 6), mapEditionReference(split, VerseAnchor(13, 6)))
    assertEquals(VerseAnchor(13, 6), mapEditionReference(split, VerseAnchor(13, 6), reverse = true))
  }

  @Test
  fun japaneseSecondCorinthiansClosingSplitMapsBothDirections() {
    val closing = EditionBookReferenceMap("2_corinthians", mappings = listOf(
      EditionReferenceRule(13, 1, 11, 13, 1, 11),
      EditionReferenceRule(13, 12, targetChapter = 13, targetVerse = 12, targetVerseEnd = 13),
      EditionReferenceRule(13, 13, targetChapter = 13, targetVerse = 14)
    ))
    assertEquals(VerseAnchor(13, 1, 11), mapEditionReference(closing, VerseAnchor(13, 1, 11)))
    assertEquals(VerseAnchor(13, 1, 11), mapEditionReference(closing, VerseAnchor(13, 1, 11), reverse = true))
    assertEquals(VerseAnchor(13, 12, 13), mapEditionReference(closing, VerseAnchor(13, 12)))
    assertEquals(VerseAnchor(13, 14), mapEditionReference(closing, VerseAnchor(13, 13)))
    assertEquals(VerseAnchor(13, 12), mapEditionReference(closing, VerseAnchor(13, 12), reverse = true))
    assertEquals(VerseAnchor(13, 12), mapEditionReference(closing, VerseAnchor(13, 13), reverse = true))
    assertEquals(VerseAnchor(13, 13), mapEditionReference(closing, VerseAnchor(13, 14), reverse = true))
    assertEquals(VerseAnchor(13, 11, 14), mapEditionReference(closing, VerseAnchor(13, 11, 13)))
    assertEquals(VerseAnchor(13, 12, 13), mapEditionReference(closing, VerseAnchor(13, 12, 14), reverse = true))
  }

  @Test
  fun overlappingSemanticBoundaryRowsPreserveBothSpeakers() {
    val followingVerse = EditionBookReferenceMap("matthew", mappings = listOf(
      EditionReferenceRule(20, 32, targetChapter = 20, targetVerse = 32, targetVerseEnd = 33),
      EditionReferenceRule(20, 33, targetChapter = 20, targetVerse = 33)
    ))
    assertEquals(VerseAnchor(20, 32, 33), mapEditionReference(followingVerse, VerseAnchor(20, 32)))
    assertEquals(VerseAnchor(20, 32), mapEditionReference(followingVerse, VerseAnchor(20, 32), reverse = true))
    assertEquals(VerseAnchor(20, 32, 33), mapEditionReference(followingVerse, VerseAnchor(20, 33), reverse = true))

    val precedingVerse = EditionBookReferenceMap("john", mappings = listOf(
      EditionReferenceRule(8, 40, targetChapter = 8, targetVerse = 40),
      EditionReferenceRule(8, 41, targetChapter = 8, targetVerse = 40, targetVerseEnd = 41)
    ))
    assertEquals(VerseAnchor(8, 40, 41), mapEditionReference(precedingVerse, VerseAnchor(8, 41)))
    assertEquals(VerseAnchor(8, 40, 41), mapEditionReference(precedingVerse, VerseAnchor(8, 40), reverse = true))
    assertEquals(VerseAnchor(8, 41), mapEditionReference(precedingVerse, VerseAnchor(8, 41), reverse = true))
  }

  @Test
  fun sourceMergedVerseCannotLoseItsCrossChapterClause() {
    val chapterBoundary = EditionBookReferenceMap("revelation", mappings = listOf(
      EditionReferenceRule(12, 17, targetChapter = 12, targetVerse = 17),
      EditionReferenceRule(12, 18, targetChapter = 13, targetVerse = 1),
      EditionReferenceRule(13, 1, targetChapter = 13, targetVerse = 1)
    ))
    val native = fullNativeVerseAnchor(VerseAnchor(12, 17), listOf(VerseAnchor(12, 17, 18)))
    assertEquals(VerseAnchor(12, 17, 18), native)
    assertNull(mapEditionReference(chapterBoundary, native!!))
    assertNull(mapEditionReference(chapterBoundary, VerseAnchor(13, 1), reverse = true))
  }

  @Test
  fun nativeRangeExpansionPreservesOmissionsAndSingleVerses() {
    val units = listOf(VerseAnchor(1, 1, 2), VerseAnchor(1, 4))
    assertEquals(VerseAnchor(1, 1, 2), fullNativeVerseAnchor(VerseAnchor(1, 2), units))
    assertEquals(VerseAnchor(1, 4), fullNativeVerseAnchor(VerseAnchor(1, 4), units))
    assertNull(fullNativeVerseAnchor(VerseAnchor(1, 2, 4), units))
    assertNull(fullNativeVerseAnchor(VerseAnchor(1, 3), units))
  }

  @Test
  fun chapterHeaderUsesTheActiveEditionsLastNativeVerse() {
    val chapter = EditionChapter(number = 8, verses = listOf(
      EditionVerse(8, 1, text = "first"), EditionVerse(8, 9, text = "last")
    ))
    assertEquals(listOf("Psalms 8:1-9"), editionChapterReferences(listOf("Psalms 8:1-10"), chapter))
    val mergedEnd = chapter.copy(verses = listOf(EditionVerse(8, 1, 9, "combined")))
    assertEquals(listOf("Psalms 8:1-9"), editionChapterReferences(listOf("Psalms 8:1-10"), mergedEnd))
    assertEquals(listOf("Bel and the Dragon 1:1-42"), editionChapterReferences(
      listOf("Bel and the Dragon 0-42"),
      EditionChapter(number = 1, verses = listOf(EditionVerse(1, 42, text = "last")))
    ))
  }
}
