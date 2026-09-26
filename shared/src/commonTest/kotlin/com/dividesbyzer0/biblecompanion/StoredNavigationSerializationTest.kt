package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredNavigationSerializationTest {
  @Test
  fun oldBookmarksAndSavedVersesRemainCanonical() {
    val bookmark = Json.decodeFromString<Bookmark>(
      """{"collection":"old_testament","bookId":"psalms","bookTitle":"Psalms","storyId":"psalms-116","storyTitle":"Psalm 116","timestamp":1}"""
    )
    val saved = Json.decodeFromString<SavedVerse>(
      """{"collection":"old_testament","bookId":"psalms","storyId":"psalms-116","bulletIndex":0,"text":"Text (116:1).","ref":"Psalms 116","timestamp":1}"""
    )
    assertNull(bookmark.sourceLanguage)
    assertNull(saved.sourceLanguage)
    assertEquals("psalms-114", resolveStoryIdAcrossLanguages(bookmark.storyId, bookmark.sourceLanguage, "ru"))
    assertEquals("psalms-114", resolveStoryIdAcrossLanguages(saved.storyId, saved.sourceLanguage, "ru"))
  }

  @Test
  fun newRecordsRoundTripWithNativeSource() {
    val bookmark = Bookmark("old_testament", "psalms", "Psalms", "psalms-115", "Psalm 115",
      timestamp = 2, sourceLanguage = "ru")
    val saved = SavedVerse("old_testament", "psalms", "psalms-115", 0,
      text = "Text (115:1).", ref = "Psalms 115", timestamp = 2, sourceLanguage = "ru")
    assertEquals(bookmark, Json.decodeFromString<Bookmark>(Json.encodeToString(bookmark)))
    assertEquals(saved, Json.decodeFromString<SavedVerse>(Json.encodeToString(saved)))
    assertEquals("psalms-115", resolveStoryIdAcrossLanguages(saved.storyId, saved.sourceLanguage, "ru"))
  }
}
