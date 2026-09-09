package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedVerseIdentityTest {

  @Test
  fun legacyAndEditionAwareSavesUseTheirVerseAnchor() {
    val legacy = SavedVerse(
      collection = "new_testament",
      bookId = "john",
      storyId = "john-5",
      bulletIndex = 3,
      text = "Legacy text (5:4).",
      ref = "John 5"
    )
    val kjv = SavedVerse(
      collection = "new_testament",
      bookId = "john",
      storyId = "john-5",
      bulletIndex = 4,
      chapter = 5,
      verseStart = 4,
      verseEnd = 4,
      editionId = BibleEditions.KJV_1769,
      text = "KJV text (5:4).",
      ref = "John 5"
    )
    val nextVerse = kjv.copy(verseStart = 5, verseEnd = 5, text = "KJV text (5:5).")

    assertTrue(legacy.sameScriptureLocation(kjv))
    assertFalse(legacy.sameScriptureLocation(nextVerse))
  }

  @Test
  fun identicalBulletIndexesDoNotMergeDifferentVerses() {
    val verse16 = SavedVerse(
      collection = "new_testament",
      bookId = "mark",
      storyId = "mark-16",
      bulletIndex = 8,
      chapter = 16,
      verseStart = 16,
      text = "BSB text (16:16).",
      ref = "Mark 16"
    )
    val verse17 = verse16.copy(
      verseStart = 17,
      verseEnd = 17,
      editionId = BibleEditions.KJV_1769,
      text = "KJV text (16:17)."
    )

    assertFalse(verse16.sameScriptureLocation(verse17))
  }
}
