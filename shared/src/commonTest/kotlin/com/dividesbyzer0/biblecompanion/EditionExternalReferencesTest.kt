package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditionExternalReferencesTest {
  @Test
  fun ordinaryReviewedIdentityExternalizesToTheSameNativeReference() {
    val result = editionAwareCanonicalReference(
      canonicalRef = "Genesis 1:7",
      bookId = "genesis",
      baseEdition = "rnksv",
      sourceEdition = BibleEditions.KOR_RV,
      providerTargetEdition = "rnksv"
    ) { bookId, from, to, anchor ->
      assertEquals("genesis", bookId)
      assertEquals(BibleEditions.KOR_RV, from)
      assertEquals("rnksv", to)
      assertEquals(VerseAnchor(1, 7), anchor)
      VerseAnchor(1, 7)
    }
    assertEquals("Genesis 1:7", result)
  }

  @Test
  fun mapsLutherPsalmAnchorToVerifiedModernProviderNumbering() {
    var request: List<Any>? = null
    val result = editionAwareCanonicalReference(
      canonicalRef = "Psalms 8:4",
      bookId = "psalms",
      baseEdition = "sch2000",
      sourceEdition = BibleEditions.LUTHER_1912,
      providerTargetEdition = "sch2000"
    ) { bookId, from, to, anchor ->
      request = listOf(bookId, from, to, anchor)
      VerseAnchor(8, 5)
    }

    assertEquals("Psalms 8:5", result)
    assertEquals(
      listOf("psalms", BibleEditions.LUTHER_1912, "sch2000", VerseAnchor(8, 4)),
      request
    )
  }

  @Test
  fun preservesTheEntireMappedRange() {
    val result = editionAwareCanonicalReference(
      canonicalRef = "Job 3:10-12",
      bookId = "job",
      baseEdition = "nr06",
      sourceEdition = BibleEditions.DIODATI_1885,
      providerTargetEdition = "nr06"
    ) { _, _, _, anchor ->
      assertEquals(VerseAnchor(3, 10, 12), anchor)
      VerseAnchor(3, 11, 13)
    }

    assertEquals("Job 3:11-13", result)
  }

  @Test
  fun rejectsDisjointAndCrossChapterReferencesWithoutTruncating() {
    val unused: (String, String, String, VerseAnchor) -> VerseAnchor? = { _, _, _, _ ->
      error("invalid references must not reach the resolver")
    }
    assertNull(editionAwareCanonicalReference(
      "John 3:16,18", "john", "rcuv", BibleEditions.CUV, "rcuv", unused
    ))
    assertNull(editionAwareCanonicalReference(
      "John 3:16-4:2", "john", "rcuv", BibleEditions.CUV, "rcuv", unused
    ))
    assertNull(editionAwareCanonicalReference(
      "John 3:16; Luke 2:1", "john", "rcuv", BibleEditions.CUV, "rcuv", unused
    ))
    assertNull(editionAwareCanonicalReference(
      "John 3.16 Luke 2:1", "john", "rcuv", BibleEditions.CUV, "rcuv", unused
    ))
  }

  @Test
  fun unknownAlternateProviderNumberingFailsClosed() {
    assertNull(editionAwareCanonicalReference(
      canonicalRef = "Romans 3:25",
      bookId = "romans",
      baseEdition = "nrt_nrp",
      sourceEdition = BibleEditions.SYNODAL_1876,
      providerTargetEdition = null
    ) { _, _, _, _ -> error("unknown targets must not reach the resolver") })
  }

  @Test
  fun baseOriginAlsoRejectsUnknownProviderNumbering() {
    assertNull(editionAwareCanonicalReference(
      canonicalRef = "Romans 3:25",
      bookId = "romans",
      baseEdition = "nrt_nrp",
      sourceEdition = "nrt_nrp",
      providerTargetEdition = null
    ) { _, _, _, _ -> error("unknown targets must not reach the resolver") })
  }

  @Test
  fun sameEditionExternalReferenceMustExistAsANativeUnit() {
    assertEquals("Psalms 13:1-2", editionAwareCanonicalReference(
      canonicalRef = "Psalms 13:2",
      bookId = "psalms",
      baseEdition = "bsb",
      sourceEdition = "bsb",
      providerTargetEdition = "bsb"
    ) { _, from, to, anchor ->
      assertEquals("bsb", from)
      assertEquals("bsb", to)
      assertEquals(VerseAnchor(13, 2), anchor)
      VerseAnchor(13, 1, 2)
    })
    assertNull(editionAwareCanonicalReference(
      canonicalRef = "Psalms 13:99",
      bookId = "psalms",
      baseEdition = "bsb",
      sourceEdition = "bsb",
      providerTargetEdition = "bsb"
    ) { _, _, _, _ -> null })
  }

  @Test
  fun chapterOnlyCrossEditionReferenceFailsClosed() {
    assertNull(editionAwareCanonicalReference(
      canonicalRef = "Job 3",
      bookId = "job",
      baseEdition = "nr06",
      sourceEdition = BibleEditions.DIODATI_1885,
      providerTargetEdition = "nr06"
    ) { _, _, _, _ -> error("chapter-only references must not reach the resolver") })
  }

  @Test
  fun providerTargetsUseAuditedEditionMappingsNotNameGuessing() {
    assertEquals("sch2000", providerNumberingEdition("de-DE", "biblecom", "SCH2000", "psalms"))
    assertEquals("nr06", providerNumberingEdition("it", "biblegateway", "NR2006", "job"))
    assertEquals("nrt_nrp", providerNumberingEdition("ru", "biblecom", "NRT", "romans"))
    assertEquals("rcuv", providerNumberingEdition("zh-TW", "biblecom", "RCUV", "john"))
    assertEquals(
      BibleEditions.LUTHER_1912,
      providerNumberingEdition("de", "biblecom", "DELUT", "psalms")
    )
    assertNull(providerNumberingEdition("de", "biblecom", "DELUT", "isaiah"))

    // RCU17TS is a structural comparator, not an alias proving RCUV identity.
    assertNull(providerNumberingEdition("zh-Hant", "biblegateway", "RCU17TS", "john"))
    assertNull(providerNumberingEdition("it", "biblecom", "DB1885", "job"))
    assertNull(providerNumberingEdition("ru", "biblecom", "SYNO", "romans"))
  }

  @Test
  fun exactBibleComDefaultsRouteToEveryLocalizedBaseEdition() {
    val expected = mapOf(
      "en" to "bsb", "de" to "sch2000", "es" to "nvi", "fr" to "nbs",
      "it" to "nr06", "pt" to "nvt", "ru" to "nrt_nrp", "ja" to "jcb",
      "ko" to "rnksv", "zh-Hans" to "ccb", "zh-Hant" to "rcuv",
      "ar" to "sab", "hi" to "irvhin"
    )
    expected.forEach { (language, edition) ->
      assertEquals(
        edition,
        providerNumberingEdition(
          language,
          "biblecom",
          Linker.defaultVersionForLanguage(language),
          "john"
        )
      )
    }
    assertNull(providerNumberingEdition("es", "biblecom", "RVR1960", "john"))
  }

  @Test
  fun auditedKjvProviderCodesShareKjvNumbering() {
    listOf("KJV", "KJVAE", "KJVAAE").forEach { code ->
      assertEquals(
        BibleEditions.KJV_1769,
        providerNumberingEdition("en", "biblecom", code, "tobit")
      )
    }
    assertEquals(
      BibleEditions.KJV_1769,
      providerNumberingEdition("en", "biblegateway", "KJV", "john")
    )
    assertEquals(
      true,
      providerFallbackKeepsNumbering("en", "biblecom", "KJV", "KJVAAE", "tobit")
    )
    assertEquals(
      false,
      providerFallbackKeepsNumbering("en", "biblecom", "BSB", "KJVAAE", "tobit")
    )
  }
}
