package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class BibleEditionOptionsTest {
  @Test
  fun everySupportedLanguageDefaultsToItsModernEdition() {
    val defaults = mapOf(
      "en" to BibleEditions.BSB,
      "de" to "sch2000",
      "es" to "nvi",
      "fr" to "nbs",
      "it" to "nr06",
      "pt" to "nvt",
      "ru" to "nrt_nrp",
      "ja" to "jcb",
      "ko" to "rnksv",
      "zh-Hans" to "ccb",
      "zh-Hant" to "rcuv",
      "ar" to "sab",
      "hi" to "irvhin"
    )
    defaults.forEach { (language, edition) ->
      assertEquals(edition, BibleEditions.defaultForLanguage(language))
      assertEquals(edition, BibleEditions.selectedForLanguage(language, "bsb"))
      assertEquals(edition, BibleEditions.effective(language, "bsb"))
    }
  }

  @Test
  fun englishRetainsItsAlternativeAndOtherLanguagesDoNotOfferIt() {
    assertEquals(listOf(BibleEditions.BSB, BibleEditions.KJV_1769), BibleEditions.available("en"))
    assertEquals(BibleEditions.KJV_1769, BibleEditions.selectedForLanguage("en", "kjv"))
    assertEquals("nvi", BibleEditions.selectedForLanguage("es", BibleEditions.KJV_1769))
    assertEquals(listOf("nvi"), BibleEditions.available("es"))
  }
}
