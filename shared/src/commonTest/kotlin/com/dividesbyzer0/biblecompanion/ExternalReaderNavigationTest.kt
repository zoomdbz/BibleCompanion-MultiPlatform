package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalReaderNavigationTest {
  private val identity: (String, String, String, VerseAnchor) -> VerseAnchor? =
    { _, _, _, anchor -> anchor }

  private fun prefs(language: String, mode: String, version: String) =
    PrefsState(appLanguage = language, readerMode = mode, translation = version)

  @Test
  fun gatewayDefaultVersionOpensTheWebsiteInsteadOfTheInAppReader() {
    val result = externalReaderNavigationLink(
      "John 3:16", "john", prefs("en", "biblegateway", "NIV"), BibleEditions.BSB, identity
    )
    assertEquals("NIV", result?.first)
    val url = result?.second.orEmpty()
    assertTrue(url.startsWith("https://www.biblegateway.com/passage/?search="), url)
    assertTrue(url.endsWith("&version=NIV"), url)
  }

  @Test
  fun unauditedBibleComVersionKeepsTheSelectedVersion() {
    assertEquals(
      "ESV" to "https://www.bible.com/bible/59/JHN.3.16.ESV",
      externalReaderNavigationLink(
        "John 3:16", "john", prefs("en", "biblecom", "ESV"), BibleEditions.BSB, identity
      )
    )
  }

  @Test
  fun chapterOnlyReferenceOpensTheChapter() {
    assertEquals(
      "NIV" to "https://www.bible.com/bible/111/PSA.23.NIV",
      externalReaderNavigationLink(
        "Psalms 23", "psalms", prefs("en", "biblecom", "NIV"), BibleEditions.BSB, identity
      )
    )
  }

  @Test
  fun alternateSourceVerseIsMappedToBaseNumbering() {
    var request: List<Any>? = null
    val result = externalReaderNavigationLink(
      "Revelation 12:18", "revelation", prefs("en", "biblecom", "NIV"), BibleEditions.KJV_1769
    ) { bookId, from, to, anchor ->
      request = listOf(bookId, from, to, anchor)
      VerseAnchor(13, 1)
    }
    assertEquals(
      listOf("revelation", BibleEditions.KJV_1769, BibleEditions.BSB, VerseAnchor(12, 18)),
      request
    )
    assertEquals("NIV" to "https://www.bible.com/bible/111/REV.13.1.NIV", result)
  }

  @Test
  fun alternateSourceChapterIsPlacedByItsFirstVerse() {
    var request: VerseAnchor? = null
    val result = externalReaderNavigationLink(
      "Psalms 9", "psalms", prefs("en", "biblecom", "NIV"), BibleEditions.KJV_1769
    ) { _, _, _, anchor ->
      request = anchor
      VerseAnchor(10, 1)
    }
    assertEquals(VerseAnchor(9, 1), request)
    assertEquals("NIV" to "https://www.bible.com/bible/111/PSA.10.NIV", result)
  }

  @Test
  fun unmappableReferenceStillOpensAsDisplayed() {
    assertEquals(
      "NIV" to "https://www.bible.com/bible/111/JHN.3.16.NIV",
      externalReaderNavigationLink(
        "John 3:16", "john", prefs("en", "biblecom", "NIV"), BibleEditions.KJV_1769
      ) { _, _, _, _ -> null }
    )
  }

  @Test
  fun auditedProviderTargetKeepsItsCrosswalk() {
    val result = externalReaderNavigationLink(
      "Psalms 8:4", "psalms", prefs("de", "biblecom", "SCH2000"), BibleEditions.LUTHER_1912
    ) { _, from, to, anchor ->
      assertEquals(BibleEditions.LUTHER_1912, from)
      assertEquals("sch2000", to)
      assertEquals(VerseAnchor(8, 4), anchor)
      VerseAnchor(8, 5)
    }
    assertEquals("SCH2000" to "https://www.bible.com/bible/157/PSA.8.5.SCH2000", result)
  }

  @Test
  fun deuterocanonFallsBackToACarryingEditionForTheSwapDialog() {
    assertEquals(
      "NRSVUE" to "https://www.bible.com/bible/3523/TOB.1.1.NRSVUE",
      externalReaderNavigationLink(
        "Tobit 1:1", "tobit", prefs("en", "biblecom", "NIV"), BibleEditions.BSB, identity
      )
    )
  }

  @Test
  fun internalReaderAndUnsupportedProviderReturnNull() {
    assertNull(externalReaderNavigationLink(
      "John 3:16", "john", prefs("en", "internal", "NIV"), BibleEditions.BSB, identity
    ))
    assertNull(externalReaderNavigationLink(
      "John 3:16", "john", prefs("sw", "biblegateway", "NIV"), BibleEditions.ONEN_2015, identity
    ))
  }
}
