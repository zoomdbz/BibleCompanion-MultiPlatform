package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditionNavigationTest {
  @Test
  fun sameLanguageLinkKeepsItsExactEdition() {
    assertEquals(BibleEditions.LUTHER_1912, BibleEditions.forNavigation(
      "de", "sch2000", "de-DE", BibleEditions.LUTHER_1912
    ))
    assertEquals("sch2000", BibleEditions.forNavigation(
      "de", BibleEditions.LUTHER_1912, "de", "sch2000"
    ))
    assertEquals(BibleEditions.KJV_1769, BibleEditions.forNavigation("en", "bsb", "en-US", "KJV"))
    assertEquals(BibleEditions.KJV_1769, BibleEditions.canonicalId("en", "KJV"))
  }

  @Test
  fun unknownOrOtherLanguageEditionCannotOverrideReader() {
    assertEquals("sch2000", BibleEditions.forNavigation("de", "sch2000", "de", "unknown"))
    assertEquals("sch2000", BibleEditions.forNavigation("de", BibleEditions.LUTHER_1912, "de", "unknown"))
    assertEquals("sch2000", BibleEditions.forNavigation("de", "sch2000", "en", BibleEditions.KJV_1769))
    assertEquals("rcuv", BibleEditions.forNavigation("zh-Hant", "rcuv", "zh-Hans", BibleEditions.CUV))
  }

  @Test
  fun legacyLinksKeepTheSelectedEdition() {
    assertEquals(BibleEditions.KJV_1769, BibleEditions.forNavigation("en", "kjv", null, null))
  }

  @Test
  fun crossLanguageLinksDoNotReuseUnmappedVerseNumbers() {
    assertTrue(BibleEditions.canKeepLinkedVerse("de", "de-DE", false))
    assertFalse(BibleEditions.canKeepLinkedVerse("ru", "en", false))
    assertFalse(BibleEditions.canKeepLinkedVerse("de", "de", true))
    assertFalse(BibleEditions.canKeepLinkedVerse("ru", null, false))
  }

  @Test
  fun knownEditionWithoutThisBooksOverlayCannotReuseVerseNumbers() {
    assertFalse(BibleEditions.linkedEditionMatchesLoaded("en", "kjv1769", "bsb"))
    assertFalse(BibleEditions.linkedEditionMatchesLoaded("de", "luther1912", "sch2000"))
    assertFalse(BibleEditions.linkedEditionMatchesLoaded("en", "unknown", "bsb"))
    assertTrue(BibleEditions.linkedEditionMatchesLoaded("en", "KJV", "kjv1769"))
    assertTrue(BibleEditions.linkedEditionMatchesLoaded("de", "sch2000", "sch2000"))
    assertTrue(BibleEditions.linkedEditionMatchesLoaded("en", null, "bsb"))
  }

  @Test
  fun sharedPassageCarriesItsEditionAndExactRange() {
    val link = appPassageLink("old_testament", "psalms", "psalms-8", "de-DE", "luther1912", VerseAnchor(8, 4, 6))
    assertTrue(link.startsWith("biblecompanion://open?"))
    assertTrue("sourceLang=de" in link)
    assertTrue("sourceEdition=luther1912" in link)
    assertTrue("verse=4&verseEnd=6" in link)
  }
}
