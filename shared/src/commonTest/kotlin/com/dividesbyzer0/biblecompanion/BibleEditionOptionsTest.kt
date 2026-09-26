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
  fun sourceBackedTraditionalEditionsAreAvailableWithoutChangingDefaults() {
    assertEquals(listOf(BibleEditions.BSB, BibleEditions.KJV_1769), BibleEditions.available("en"))
    assertEquals(BibleEditions.KJV_1769, BibleEditions.selectedForLanguage("en", "kjv"))
    assertEquals(listOf("sch2000", BibleEditions.LUTHER_1912), BibleEditions.available("de"))
    assertEquals(listOf("nvi", BibleEditions.RV_1909), BibleEditions.available("es"))
    assertEquals(listOf("nbs", BibleEditions.LSG_1910), BibleEditions.available("fr"))
    assertEquals(listOf("nr06", BibleEditions.DIODATI_1885), BibleEditions.available("it"))
    assertEquals(listOf("nvt", BibleEditions.ALMEIDA_1911), BibleEditions.available("pt"))
    assertEquals(listOf("nrt_nrp", BibleEditions.SYNODAL_1876), BibleEditions.available("ru"))
    assertEquals(listOf("jcb", BibleEditions.BUNGO), BibleEditions.available("ja"))
    assertEquals(listOf("rnksv", BibleEditions.KOR_RV), BibleEditions.available("ko"))
    assertEquals(listOf("ccb", BibleEditions.CUV), BibleEditions.available("zh-Hans"))
    assertEquals(listOf("rcuv", BibleEditions.CUV), BibleEditions.available("zh-Hant"))
    assertEquals(listOf("sab", BibleEditions.VAN_DYCK), BibleEditions.available("ar"))
    assertEquals(listOf("irvhin"), BibleEditions.available("hi"))
    assertEquals("nvi", BibleEditions.selectedForLanguage("es", BibleEditions.KJV_1769))
    assertEquals(BibleEditions.RV_1909, BibleEditions.selectedForLanguage("es", BibleEditions.RV_1909))
    assertEquals(true, BibleEditions.isAlternate("es", BibleEditions.RV_1909))
    assertEquals(false, BibleEditions.isAlternate("es", "nvi"))
  }
}
