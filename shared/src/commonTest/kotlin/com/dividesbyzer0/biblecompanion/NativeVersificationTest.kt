package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeVersificationTest {

  @Test
  fun russianPsalmMergesUseTheSameNativeChapter() {
    assertEquals(listOf(9), canonicalChaptersToNative("psalms", 9, "ru"))
    assertEquals(listOf(9), canonicalChaptersToNative("psalms", 10, "ru"))
    assertEquals(listOf(113), canonicalChaptersToNative("psalms", 114, "ru"))
    assertEquals(listOf(113), canonicalChaptersToNative("psalms", 115, "ru"))
    assertEquals("9-10", canonicalRangeToNative("psalms", "9-11", "ru"))
  }

  @Test
  fun russianPsalmSplitsRetainBothNativeChapters() {
    assertEquals(listOf(114, 115), canonicalChaptersToNative("psalms", 116, "ru"))
    assertEquals(listOf(146, 147), canonicalChaptersToNative("psalms", 147, "ru"))
    assertEquals(listOf("psalms-114", "psalms-115"), canonicalStoryIdsToNative("psalms-116", "ru"))
    assertEquals(listOf("psalms-146", "psalms-147"), canonicalStoryIdsToNative("psalms-147", "ru"))
    assertEquals("9, 114-115, 146-147", canonicalRangeToNative("psalms", "9-10, 116, 147", "ru"))
    assertEquals("113-116", canonicalRangeToNative("psalms", "114-117", "ru"))
  }

  @Test
  fun russianPsalmBoundariesAndOtherLanguagesStayStable() {
    assertEquals(listOf(8), canonicalChaptersToNative("psalms", 8, "ru"))
    assertEquals(listOf(10), canonicalChaptersToNative("psalms", 11, "ru"))
    assertEquals(listOf(112), canonicalChaptersToNative("psalms", 113, "ru"))
    assertEquals(listOf(116), canonicalChaptersToNative("psalms", 117, "ru"))
    assertEquals(listOf(145), canonicalChaptersToNative("psalms", 146, "ru"))
    assertEquals(listOf(148), canonicalChaptersToNative("psalms", 148, "ru"))
    assertEquals(listOf(116), canonicalChaptersToNative("psalms", 116, "en"))
    assertEquals("114-117", canonicalRangeToNative("psalms", "114-117", "de"))
  }

  @Test
  fun germanAndFrenchMalachiChapterFourMapsToThree() {
    for (language in listOf("de", "fr")) {
      assertEquals(listOf(3), canonicalChaptersToNative("malachi", 4, language))
      assertEquals(listOf("malachi-3"), canonicalStoryIdsToNative("malachi-4", language))
      assertEquals("1-3", canonicalRangeToNative("malachi", "1-4", language))
    }
    assertEquals(listOf(4), canonicalChaptersToNative("malachi", 4, "en"))
    assertEquals("1-4", canonicalRangeToNative("malachi", "1-4", "en"))
  }

  @Test
  fun storedAndSharedStoryIdsRespectTheirSourceLanguage() {
    assertEquals("psalms-9", resolveStoryIdAcrossLanguages("psalms-9", "ru", "ru"))
    assertEquals("psalms-9", resolveStoryIdAcrossLanguages("psalms-9", "ru", "en"))
    assertEquals("psalms-116", resolveStoryIdAcrossLanguages("psalms-115", "ru", "en"))
    assertEquals("psalms-114", resolveStoryIdAcrossLanguages("psalms-116", null, "ru"))
    assertEquals("psalms-146", resolveStoryIdAcrossLanguages("psalms-147", null, "ru"))
    assertEquals("psalms-10", resolveStoryIdAcrossLanguages("psalms-11", "en", "ru"))
    assertEquals("malachi-3", resolveStoryIdAcrossLanguages("malachi-4", null, "de"))
    assertEquals("malachi-3", resolveStoryIdAcrossLanguages("malachi-3", "de", "en"))
    assertEquals("malachi-3", resolveStoryIdAcrossLanguages("malachi-3", "fr", "de"))
    assertEquals(null, resolveStoryIdAcrossLanguages("psalms-151", null, "ru"))
    assertEquals("genesis-1", resolveStoryIdAcrossLanguages("genesis-1", "ru", "en"))
  }

  @Test
  fun chronologyRangeAndOpeningChapterUseTheSameConversion() {
    val splitPsalm = ChronologyEntry(
      "old_testament", "psalms", "116", 116, ChronologyLane.VOICES_FROM_PERIOD
    )
    assertEquals("114-115", splitPsalm.rangeForLanguage("ru"))
    assertEquals(114, splitPsalm.openingChapterForLanguage("ru"))

    val malachi = ChronologyEntry(
      "old_testament", "malachi", "1-4", 4, ChronologyLane.VOICES_FROM_PERIOD
    )
    for (language in listOf("de", "fr")) {
      assertEquals("1-3", malachi.rangeForLanguage(language))
      assertEquals(3, malachi.openingChapterForLanguage(language))
    }

    val unnamedPsalms = ChronologyEntry(
      "old_testament", "psalms", "1-2, 10, 33", 1,
      ChronologyLane.VOICES_FROM_PERIOD,
      psalmAttribution = PsalmAttribution.UNNAMED
    )
    assertEquals("1-2, 32", unnamedPsalms.rangeForLanguage("ru"))
  }
}
