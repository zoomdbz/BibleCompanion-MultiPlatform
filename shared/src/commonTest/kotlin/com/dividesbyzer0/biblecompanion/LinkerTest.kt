package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkerTest {

  @Test
  fun internalReaderDoesNotCreateAnExternalLink() {
    assertNull(Linker.linkForReader("John 3:16", "KJV", "internal", "en"))
  }

  @Test
  fun bibleGatewayKeepsTheRequestedVersionForCanonicalReferences() {
    val result = Linker.linkForReader("John 3:16", "KJV", "biblegateway", "en")
    assertEquals("KJV", result?.first)
    assertTrue(result?.second?.endsWith("&version=KJV") == true)
  }

  @Test
  fun bibleGatewayUsesVerifiedExpandedFallbackForUnsupportedDcBook() {
    val result = Linker.linkForReader("Tobit 1:1", "KJV", "biblegateway", "en")
    assertEquals("NRSVUE", result?.first)
    assertTrue(result?.second?.endsWith("&version=NRSVUE") == true)
  }

  @Test
  fun bibleGatewayKeepsNrsVueForExpandedDcBooks() {
    val result = Linker.linkForReader("4 Maccabees 1:1", "NRSVUE", "biblegateway", "en")
    assertEquals("NRSVUE", result?.first)
    assertTrue(result?.second?.startsWith("https://www.biblegateway.com/passage/?search=") == true)
    assertTrue(result?.second?.endsWith("&version=NRSVUE") == true)
  }

  @Test
  fun editionSpecificIntegratedBooksUseVerifiedStandaloneFallbacks() {
    val bibleCom = Linker.linkForReader(
      "Letter of Jeremiah 1:1", "DRC1752", "biblecom", "en"
    )
    assertEquals("NRSVUE", bibleCom?.first)
    assertEquals(
      "https://www.bible.com/bible/3523/LJE.1.1.NRSVUE",
      bibleCom?.second
    )

    val bibleGateway = Linker.linkForReader(
      "Susanna 1:1", "DRA", "biblegateway", "en"
    )
    assertEquals("NRSVUE", bibleGateway?.first)
    assertTrue(bibleGateway?.second?.endsWith("&version=NRSVUE") == true)
  }

  @Test
  fun catholicEditionsKeepTheirDirectStandaloneBooks() {
    val result = Linker.linkForReader("Tobit 1:1", "DRC1752", "biblecom", "en")
    assertEquals("DRC1752", result?.first)
    assertEquals("https://www.bible.com/bible/55/TOB.1.1.DRC1752", result?.second)
  }

  @Test
  fun bibleComUsesCanonicalKjvForCanonicalReferences() {
    val result = Linker.linkForReader("John 3:16", "KJV", "biblecom", "en")
    assertEquals("KJV", result?.first)
    assertEquals("https://www.bible.com/bible/1/JHN.3.16.KJV", result?.second)
  }

  @Test
  fun bibleComPrefersKjvaaForSupportedKjvDeuterocanonReferences() {
    val result = Linker.linkForReader("Tobit 1:1", "KJV", "biblecom", "en")
    assertEquals("KJVAAE", result?.first)
    assertEquals("https://www.bible.com/bible/546/TOB.1.1.KJVAAE", result?.second)
  }

  @Test
  fun bibleComNeverRoutesBooksMissingFromKjvaaToId546() {
    val absent = listOf(
      "1 Esdras 1:1",
      "2 Esdras 1:1",
      "Prayer of Manasseh 1:1",
      "Psalm 151:1",
      "3 Maccabees 1:1",
      "4 Maccabees 1:1"
    )
    absent.forEach { ref ->
      assertFalse(Linker.supportsDc("biblecom", "KJVAAE", "en", ref), ref)
      val result = Linker.linkForReader(ref, "KJV", "biblecom", "en")
      assertEquals("NRSVUE", result?.first, ref)
      assertFalse(result?.second?.contains("/546/") == true, ref)
    }
  }

  @Test
  fun kjvaaUsesVerifiedLetterAndSusannaRoutes() {
    assertEquals(
      "https://www.bible.com/bible/546/BAR.6.73.KJVAAE",
      Linker.buildBibleComUrl("Letter of Jeremiah 1:72", "KJVAAE")
    )
    assertEquals(
      "https://www.bible.com/bible/546/SUS.1_1.64.KJVAAE",
      Linker.buildBibleComUrl("Susanna 1:64", "KJVAAE")
    )
    assertNull(Linker.buildYouVersionDeepLink("Letter of Jeremiah 1:1", "KJVAAE"))
    assertNull(Linker.buildYouVersionDeepLink("Susanna 1:1", "KJVAAE"))
  }

  @Test
  fun kjvaaMapsAllIntegratedGreekEstherChapters() {
    val expectedRoutes = listOf(
      "ESG.2_1", "ESG.3_1", "EST.1", "EST.2", "EST.3", "ESG.4_1", "EST.3",
      "EST.4", "ESG.4_1", "ESG.5_1", "ESG.6_1", "EST.5", "EST.6", "EST.7",
      "EST.8", "ESG.7_1", "EST.8", "EST.9", "EST.10", "ESG.1_1", "ESG.2_1"
    )
    expectedRoutes.forEachIndexed { index, route ->
      val expectedSuffix = if (route.startsWith("EST.")) route + ".1" else route
      assertEquals(
        "https://www.bible.com/bible/546/" + expectedSuffix + ".KJVAAE",
        Linker.buildBibleComUrl("Esther (Greek) " + (index + 1) + ":1", "KJVAAE"),
        "integrated Greek Esther chapter " + (index + 1)
      )
    }
    assertNull(Linker.buildYouVersionDeepLink("Esther (Greek) 1:1", "KJVAAE"))
  }

  @Test
  fun kjvaaMapsStandaloneEstherAdditionNumbering() {
    assertEquals(
      "https://www.bible.com/bible/546/ESG.1_1.KJVAAE",
      Linker.buildBibleComUrl("Additions to Esther 10:1", "KJVAAE")
    )
    assertEquals(
      "https://www.bible.com/bible/546/ESG.7_1.KJVAAE",
      Linker.buildBibleComUrl("Additions to Esther 16:24", "KJVAAE")
    )
  }

  @Test
  fun nrsvueRoutesTheFullExpandedSetOnBibleCom() {
    assertTrue(Linker.supportsDc("biblecom", "NRSVUE", "en", "Psalm 151:1"))
    assertEquals(
      "https://www.bible.com/bible/3523/PS2.1.1.NRSVUE",
      Linker.buildBibleComUrl("Psalm 151:1", "NRSVUE")
    )
  }
}
