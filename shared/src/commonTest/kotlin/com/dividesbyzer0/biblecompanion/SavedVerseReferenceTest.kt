package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class SavedVerseReferenceTest {
  @Test
  fun formatsTheExactSelectedNativeVerseInsteadOfTheChapterRange() {
    assertEquals(
      "Matthew 5:3",
      savedVerseReference("Matthew 5:1-48", VerseAnchor(5, 3))
    )
    assertEquals(
      "Matthew 5:3-5",
      savedVerseReference("Matthew 5:1-48", VerseAnchor(5, 3, 5))
    )
  }

  @Test
  fun retainsTheProvidedLocalizedBookPrefixWithoutParsingIt() {
    assertEquals(
      "Matthäus 5:3",
      savedVerseReference("Matthäus 5:1-48", VerseAnchor(5, 3))
    )
    assertEquals(
      "马太福音 5:3",
      savedVerseReference("马太福音 5:1-48", VerseAnchor(5, 3))
    )
    assertEquals(
      "1 Corinthians 13:4",
      savedVerseReference("1 Corinthians 13:1-13", VerseAnchor(13, 4))
    )
    assertEquals(
      "Malachi 3:24",
      savedVerseReference("Malachi 3:1-24", VerseAnchor(3, 24))
    )
  }

  @Test
  fun legacySavedVerseRepairsItsBroadLabelFromTheProvenTextAnchor() {
    val legacy = SavedVerse(
      collection = "new_testament",
      bookId = "matthew",
      storyId = "matthew-5",
      bulletIndex = 2,
      text = "Blessed are the poor in spirit. (5:3).",
      ref = "Matthew 5:1-48",
      timestamp = 0L
    )

    assertEquals("Matthew 5:3", legacy.displayReference())
  }

  @Test
  fun keepsTheOriginalReferenceWhenNoKnownAnchorBoundaryExists() {
    assertEquals(
      "Matthew five, verses one through forty-eight",
      savedVerseReference(
        "Matthew five, verses one through forty-eight",
        VerseAnchor(5, 3)
      )
    )
  }
}
