package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkerTest {

  @Test
  fun modernBibleComDefaultsUseTheCatalogIdAndExactVerseForEveryLanguage() {
    val expected = mapOf(
      "en" to ("BSB" to 3034), "de" to ("SCH2000" to 157),
      "es" to ("NVI" to 128), "fr" to ("NBS" to 104),
      "it" to ("NR06" to 122), "pt" to ("NVT" to 1930),
      "ru" to ("NRT" to 143), "ja" to ("JCB" to 83),
      "ko" to ("RNKSV" to 142), "zh-Hans" to ("CCB" to 36),
      "zh-Hant" to ("RCUV" to 139), "ar" to ("SAB" to 153),
      "hi" to ("IRVHIN" to 1980)
    )
    expected.forEach { (lang, choice) ->
      val (code, id) = choice
      assertEquals(code, Linker.defaultVersionForLanguage(lang), lang)
      assertEquals(
        code to "https://www.bible.com/bible/$id/GEN.1.7.$code",
        Linker.linkForReader("Genesis 1:7", "", "biblecom", lang),
        lang
      )
      assertEquals(
        "youversion://bible?reference=GEN.1.7&version_id=$id",
        Linker.buildYouVersionDeepLink("Genesis 1:7", code),
        lang
      )
    }
  }

  @Test
  fun bibleGatewayDefaultsAreFromItsCatalogNotBibleComIds() {
    val expected = mapOf(
      "en" to "NIV", "de" to "SCH2000", "es" to "NVI",
      "fr" to "SG21", "it" to "NR2006", "pt" to "NVT",
      "ru" to "NRT", "ja" to "JLB", "ko" to "KLB",
      "zh-Hans" to "CCB", "zh-Hant" to "RCU17TS",
      "ar" to "NAV", "hi" to "ERV-HI"
    )
    expected.forEach { (lang, code) ->
      assertEquals(code, Linker.defaultGatewayVersionForLanguage(lang), lang)
      val result = Linker.linkForReader("Genesis 1:7", "", "biblegateway", lang)
      assertEquals(code, result?.first, lang)
      assertTrue(result?.second?.contains("search=Genesis") == true, lang)
      assertTrue(result?.second?.endsWith("&version=$code") == true, lang)
    }
  }

  @Test
  fun selectedExternalEditionMatchesTheProviderThatWillOpen() {
    assertEquals("NR06", Linker.selectedVersionForReader("NR2006", "biblecom", "it"))
    assertEquals("NR2006", Linker.selectedVersionForReader("NR06", "biblegateway", "it"))
    assertEquals("NIV", Linker.selectedVersionForReader("BSB", "biblegateway", "en"))
    assertNull(Linker.selectedVersionForReader("BSB", "internal", "en"))
  }

  @Test
  fun gatewayMapsProviderSpecificCodesWithoutInventingEquivalentText() {
    val cases = listOf(
      Triple("it", "NR06", "NR2006"),
      Triple("it", "NR94", "NR1994"),
      Triple("zh-Hant", "RCUV", "RCU17TS"),
      Triple("zh-Hans", "RCUVSS", "RCU17SS"),
      Triple("zh-Hant", "CCB_T", "CCBT"),
      Triple("fr", "NBS", "SG21"),
      Triple("ja", "JCB", "JLB"),
      Triple("ko", "RNKSV", "KLB"),
      Triple("ar", "SAB", "NAV"),
      Triple("hi", "IRVHIN", "ERV-HI"),
      Triple("en", "BSB", "NIV")
    )
    cases.forEach { (lang, bibleComCode, gatewayCode) ->
      val result = Linker.linkForReader("Genesis 1:7", bibleComCode, "biblegateway", lang)
      assertEquals(gatewayCode, result?.first, "$lang $bibleComCode")
      assertTrue(result?.second?.endsWith("&version=$gatewayCode") == true, "$lang $bibleComCode")
    }
  }

  @Test
  fun bibleComNeverSendsAnUnknownGatewayOnlyCodeToGatewayAsItsVersion() {
    val result = Linker.linkForReader("Genesis 1:7", "JLB", "biblecom", "ja")
    assertEquals("JCB", result?.first)
    assertEquals("https://www.bible.com/bible/83/GEN.1.7.JCB", result?.second)
  }

  @Test
  fun italianYouVersionAliasesUseBibleComsCanonicalPathCode() {
    assertEquals(
      "https://www.bible.com/bible/122/GEN.1.7.NR06",
      Linker.buildBibleComUrl("Genesis 1:7", "NR2006")
    )
    assertEquals(
      "https://www.bible.com/bible/123/GEN.1.7.NR94",
      Linker.buildBibleComUrl("Genesis 1:7", "NR1994")
    )
  }

  @Test
  fun bibleGatewayUsesItsNativeNamesForExpandedBooks() {
    val greekEsther = Linker.buildBibleGatewayUrl("Esther (Greek) 18:1", "NRSVUE")
    assertTrue(greekEsther.contains("search=Greek"))
    assertFalse(greekEsther.contains("Esther%20%28Greek%29"))

    val songOfThree = Linker.buildBibleGatewayUrl("Song of the Three 1:1", "NRSVUE")
    assertTrue(songOfThree.contains("search=Prayer"))
    assertTrue(songOfThree.endsWith("&version=NRSVUE"))
  }

  @Test
  fun bibleGatewayMapsAllIntegratedGreekEstherSegmentsWithoutFalseVerseAnchors() {
    val chapters = listOf(
      1, 1, 1, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 7, 8, 8, 8, 9, 10, 10, 10
    )
    chapters.forEachIndexed { index, gatewayChapter ->
      val ref = "Esther (Greek) ${index + 1}:1"
      val result = Linker.linkForReader(ref, "NRSVUE", "biblegateway", "en")
      assertEquals("NRSVUE", result?.first, ref)
      assertEquals(
        Linker.buildBibleGatewayUrl("Greek Esther $gatewayChapter", "NRSVUE"),
        result?.second,
        ref
      )
    }
  }

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
  fun bibleComSkipsTheSirachPrologueForBfcAndDhh94i() {
    val french = Linker.linkForReader("Sirach 1:1", "BFC", "biblecom", "fr")
    assertEquals("BFC", french?.first)
    assertEquals("https://www.bible.com/bible/63/SIR.1_1.1.BFC", french?.second)

    val spanish = Linker.linkForReader("Sirach 1:1", "DHH94I", "biblecom", "es")
    assertEquals("DHH94I", spanish?.first)
    assertEquals("https://www.bible.com/bible/52/SIR.1_1.1.DHH94I", spanish?.second)

    assertEquals(
      "https://www.bible.com/bible/63/SIR.2.1.BFC",
      Linker.buildBibleComUrl("Sirach 2:1", "BFC")
    )
    assertEquals(
      "https://www.bible.com/bible/52/SIR.2.1.DHH94I",
      Linker.buildBibleComUrl("Sirach 2:1", "DHH94I")
    )
    assertNull(Linker.buildYouVersionDeepLink("Sirach 1:1", "BFC"))
    assertNull(Linker.buildYouVersionDeepLink("Sirach 1:1", "DHH94I"))
    assertEquals(
      "youversion://bible?reference=SIR.2.1&version_id=63",
      Linker.buildYouVersionDeepLink("Sirach 2:1", "BFC")
    )
  }

  @Test
  fun combinedSongOfThreeNameUsesTheStandaloneBibleComRoute() {
    val ref = "Prayer of Azariah and Song of the Three 1:1-67"
    assertTrue(Linker.isDeuterocanonReference(ref))

    val result = Linker.linkForReader(ref, "BFC", "biblecom", "fr")
    assertEquals("NRSVUE", result?.first)
    assertEquals(
      "https://www.bible.com/bible/3523/S3Y.1.1-67.NRSVUE",
      result?.second
    )
  }

  @Test
  fun arabicGnAdc25KeepsItsVerifiedDirectDeuterocanonBooks() {
    val result = Linker.linkForReader("Tobit 1:1", "GNADC25", "biblecom", "ar")
    assertEquals("GNADC25", result?.first)
    assertEquals("https://www.bible.com/bible/1665/TOB.1.1.GNADC25", result?.second)
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
  fun nrsvueMatchesEveryIntegratedGreekEstherSegment() {
    val firstVerses = listOf(2, 1, 1, 1, 1, 1, 14, 1, 8, 1, 1, 3, 1, 1, 1, 1, 13, 1, 1, 4, 1)
    val lastVerses = listOf(12, 6, 22, 23, 13, 7, 15, 17, 18, 19, 16, 14, 14, 10, 12, 24, 17, 32, 3, 13, 1)
    for (index in 0 until 21) {
      val chapter = index + 1
      assertEquals(
        "https://www.bible.com/bible/3523/ESG.$chapter.NRSVUE",
        Linker.buildBibleComUrl("Esther (Greek) $chapter", "NRSVUE"),
        "NRSVUE Greek Esther chapter $chapter"
      )
      for (verse in listOf(firstVerses[index], lastVerses[index]).distinct()) {
        assertEquals(
          "https://www.bible.com/bible/3523/ESG.$chapter.$verse.NRSVUE",
          Linker.buildBibleComUrl("Esther (Greek) $chapter:$verse", "NRSVUE"),
          "NRSVUE Greek Esther $chapter:$verse"
        )
      }
    }
  }

  @Test
  fun bfcMapsEveryIntegratedGreekEstherSegmentAndVerseBoundary() {
    val routes = listOf(
      "1_1" to -1, "1_1" to 11, "1_2" to 17, "2" to 0, "3" to 0,
      "3_1" to 13, "3_2" to 7, "4" to 0, "4_1" to 10, "4_1" to 28,
      "5_1" to 0, "5_2" to 14, "6" to 0, "7" to 0, "8" to 0,
      "8_1" to 12, "8_2" to 24, "9" to 0, "10" to 0,
      "10_1" to 0, "10_1" to 13
    )
    val firstVerses = listOf(2, 1, 1, 1, 1, 1, 14, 1, 8, 1, 1, 3, 1, 1, 1, 1, 13, 1, 1, 4, 1)
    val lastVerses = listOf(12, 6, 22, 23, 13, 7, 15, 17, 18, 19, 16, 14, 14, 10, 12, 24, 17, 32, 3, 13, 1)
    for (index in routes.indices) {
      val chapter = index + 1
      val (route, offset) = routes[index]
      assertEquals(
        "https://www.bible.com/bible/63/ESG.$route.BFC",
        Linker.buildBibleComUrl("Esther (Greek) $chapter", "BFC"),
        "BFC Greek Esther chapter $chapter"
      )
      for (verse in listOf(firstVerses[index], lastVerses[index]).distinct()) {
        assertEquals(
          "https://www.bible.com/bible/63/ESG.$route.${verse + offset}.BFC",
          Linker.buildBibleComUrl("Esther (Greek) $chapter:$verse", "BFC"),
          "BFC Greek Esther $chapter:$verse"
        )
      }
    }
    assertNull(Linker.buildYouVersionDeepLink("Esther (Greek) 8:1", "BFC"))
  }

  @Test
  fun dhh94iMapsEveryIntegratedGreekEstherSegmentWithoutFalseAdditionVerses() {
    val routes = listOf(1, 1, 1, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 7, 8, 8, 8, 9, 10, 10, 10)
    val verseAnchored = setOf(4, 5, 7, 8, 12, 13, 14, 15, 17, 18, 19)
    val firstVerses = listOf(2, 1, 1, 1, 1, 1, 14, 1, 8, 1, 1, 3, 1, 1, 1, 1, 13, 1, 1, 4, 1)
    for (index in routes.indices) {
      val chapter = index + 1
      val base = "https://www.bible.com/bible/52/ESG.${routes[index]}"
      assertEquals(
        "$base.DHH94I",
        Linker.buildBibleComUrl("Esther (Greek) $chapter", "DHH94I"),
        "DHH94I Greek Esther chapter $chapter"
      )
      val suffix = if (chapter in verseAnchored) ".${firstVerses[index]}" else ""
      assertEquals(
        "$base$suffix.DHH94I",
        Linker.buildBibleComUrl("Esther (Greek) $chapter:${firstVerses[index]}", "DHH94I"),
        "DHH94I Greek Esther $chapter:${firstVerses[index]}"
      )
    }
    assertNull(Linker.buildYouVersionDeepLink("Esther (Greek) 18:1", "DHH94I"))
  }

  @Test
  fun greekEstherUsesVerifiedDirectEditionsAndFallsBackElsewhere() {
    assertEquals("BFC", Linker.linkForReader("Esther (Greek) 18:1", "BFC", "biblecom", "fr")?.first)
    assertEquals("DHH94I", Linker.linkForReader("Esther (Greek) 18:1", "DHH94I", "biblecom", "es")?.first)
    assertEquals("DHH94I", Linker.linkForReader("Esther (Greek) 18:1", "BDO1573", "biblecom", "es")?.first)
    assertEquals("NRSVUE", Linker.linkForReader("Esther (Greek) 18:1", "LUT", "biblecom", "de")?.first)
    assertFalse(Linker.supportsDc("biblecom", "BFC", "fr", "Additions to Esther 10:1"))
    assertNull(Linker.buildBibleComUrl("Esther (Greek) 22:1", "BFC"))
    assertNull(Linker.buildBibleComUrl("Esther (Greek) 22:1", "DHH94I"))
    assertEquals(
      "https://www.bible.com/bible/3523/ESG.21.1.NRSVUE",
      Linker.buildBibleComUrl("Esther (Greek) 21:1", "NRSVUE")
    )
    assertNull(Linker.buildBibleComUrl("Esther (Greek) 22:1", "NRSVUE"))
    assertFalse(Linker.supportsDc("biblecom", "NRSVUE", "en", "Esther (Greek) 22:1"))
    assertNull(Linker.linkForReader("Esther (Greek) 22:1", "BFC", "biblecom", "fr"))
    assertNull(Linker.linkForReader("Esther (Greek) 22:1", "NRSVUE", "biblecom", "en"))
    assertEquals(
      "https://www.bible.com/bible/3523/ESG.21.1.NRSVUE",
      Linker.linkForReader("Esther (Greek) 21:1", "NRSVUE", "biblecom", "en")?.second
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
