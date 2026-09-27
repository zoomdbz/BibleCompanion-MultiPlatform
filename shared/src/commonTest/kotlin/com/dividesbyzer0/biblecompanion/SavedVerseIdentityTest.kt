package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedVerseIdentityTest {

  @Test
  fun legacySavesBelongToTheDefaultEditionNotEveryEdition() {
    val legacy = SavedVerse(
      collection = "new_testament",
      bookId = "john",
      storyId = "john-5",
      bulletIndex = 3,
      text = "Legacy text (5:4).",
      ref = "John 5",
      timestamp = 0
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
      ref = "John 5",
      timestamp = 0
    )
    val nextVerse = kjv.copy(verseStart = 5, verseEnd = 5, text = "KJV text (5:5).")

    assertTrue(legacy.sameScriptureLocation(kjv.copy(editionId = BibleEditions.BSB)))
    assertFalse(legacy.sameScriptureLocation(kjv))
    assertFalse(legacy.sameScriptureLocation(nextVerse))
    assertFalse(legacy.sameScriptureLocation(kjv.copy(sourceLanguage = "ru")))
  }

  @Test
  fun equalNumbersInDifferentEditionsCannotOverwriteOrHighlightEachOther() {
    val modern = SavedVerse(
      collection = "old_testament", bookId = "psalms", storyId = "psalms-8",
      bulletIndex = 4, chapter = 8, verseStart = 5, editionId = "sch2000",
      sourceLanguage = "de", text = "Modern passage (8:5).", ref = "Psalms 8:5", timestamp = 0
    )
    val traditional = modern.copy(editionId = BibleEditions.LUTHER_1912, text = "Another passage (8:5).")
    assertFalse(modern.sameScriptureLocation(traditional))
    assertTrue(modern.belongsToEdition("de", "sch2000"))
    assertFalse(modern.belongsToEdition("de", BibleEditions.LUTHER_1912))
    assertFalse(modern.belongsToEdition("en", "sch2000"))
  }

  @Test
  fun languageAliasesAndLegacyKjvIdPreserveIdentity() {
    val saved = SavedVerse(
      collection = "new_testament", bookId = "john", storyId = "john-1",
      bulletIndex = 0, chapter = 1, verseStart = 1, editionId = "kjv",
      sourceLanguage = "en-US", text = "Text (1:1).", ref = "John 1:1", timestamp = 0
    )
    assertTrue(saved.sameScriptureLocation(saved.copy(sourceLanguage = "en", editionId = BibleEditions.KJV_1769)))
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
      ref = "Mark 16",
      timestamp = 0
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
