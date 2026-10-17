package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderSharingTest {
  @Test
  fun internalBsbVerseSharesQuotedTextAndAPublicLinkWithoutHiddenEsvOrAppLinks() {
    val story = story("1-corinthians-10", "1 Corinthians 10:1-33", listOf(
      "We should not test Christ, as some of them did, and were killed by snakes. (10:9)."
    ))
    val prefs = PrefsState(appLanguage = "en")
    val content = payload(Book("1_corinthians", "1 Corinthians", listOf(story)),
      setOf(story.id to 0), prefs, BibleEditions.BSB)

    assertEquals(
      "\"We should not test Christ, as some of them did, and were killed by snakes.\"\n" +
        "1 Corinthians 10:9 BSB\n\nhttps://www.bible.com/bible/3034/1CO.10.9.BSB",
      content.text
    )
    assertEquals("1 Corinthians 10:9", content.primaryRef)
    assertFalse("biblecompanion://" in content.text)
    assertFalse("ESV" in content.text)
  }

  @Test
  fun mergedNativeRangeKeepsAllTextClosesSpeechAndKeepsScriptureParentheses() {
    val story = story("john-3", "John 3:1-36", listOf(
      "He said, \u201cRemember this (the promise) (3:16-17).",
      "and keep it.\u201d (3:18)."
    ))
    val content = payload(Book("john", "John", listOf(story)),
      setOf(story.id to 0, story.id to 1), PrefsState(appLanguage = "en"), BibleEditions.BSB)

    assertEquals(
      "\"He said, \"Remember this (the promise). and keep it.\"\"\n" +
        "John 3:16-18 BSB\n\nhttps://www.bible.com/bible/3034/JHN.3.16-18.BSB",
      content.text
    )
    assertFalse("(3:" in content.text)
  }

  @Test
  fun anExcerptEndingInsideSpeechGetsBalancedAsciiQuotes() {
    val story = story("john-3", "John 3:1-36", listOf("He said, \u201cDo not fear. (3:16)."))
    val content = payload(Book("john", "John", listOf(story)), setOf(story.id to 0),
      PrefsState(appLanguage = "en"), BibleEditions.BSB)

    assertEquals("\"He said, \"Do not fear.\"\"", content.text.lineSequence().first())
  }

  @Test
  fun disjointSelectionsKeepSourceOrderAndSeparateExactPublicLinks() {
    val story = story("john-3", "John 3:1-36", listOf(
      "First selected verse. (3:16).",
      "Not selected. (3:17).",
      "Second selected verse. (3:18)."
    ))
    val prefs = PrefsState(appLanguage = "en")
    val content = payload(Book("john", "John", listOf(story)),
      linkedSetOf(story.id to 2, story.id to 0), prefs, BibleEditions.BSB)
    val links = content.text.lineSequence().filter { "://" in it }.toList()

    assertTrue(content.text.startsWith("\"First selected verse. Second selected verse.\"\nJohn 3:16,18 BSB"))
    assertFalse("Not selected" in content.text)
    assertFalse("16-18" in content.text)
    assertEquals(listOf(
      "https://www.bible.com/bible/3034/JHN.3.16.BSB",
      "https://www.bible.com/bible/3034/JHN.3.18.BSB"
    ), links)
  }

  @Test
  fun multipleChaptersBecomeSeparateBlocksInBookOrderWithTheirOwnVerseLinks() {
    val first = story("john-3", "John 3:1-36", listOf("Earlier chapter. (3:36)."))
    val second = story("john-4", "John 4:1-54", listOf("Later chapter. (4:1)."))
    val prefs = PrefsState(appLanguage = "en")
    val content = payload(Book("john", "John", listOf(first, second)),
      linkedSetOf(second.id to 0, first.id to 0), prefs, "bsb")

    assertEquals("John 3:36", content.primaryRef)
    assertTrue(content.text.indexOf("Earlier chapter.") < content.text.indexOf("Later chapter."))
    assertTrue("\n\n\"Later chapter.\"\nJohn 4:1 BSB" in content.text)
    assertTrue("https://www.bible.com/bible/3034/JHN.3.36.BSB" in content.text)
    assertTrue("https://www.bible.com/bible/3034/JHN.4.1.BSB" in content.text)
    assertFalse("biblecompanion://" in content.text)
  }

  @Test
  fun internalReaderUsesItsSavedWebsiteAndExternalReadersOverrideThatChoice() {
    val internal = PrefsState(appLanguage = "en", verseShareProvider = "biblegateway")
    assertEquals(listOf(Linker.buildBibleGatewayUrl("John 3:16", "NIV")),
      fixtureLinks("John 3:16", "john", internal, "bsb"))
    assertEquals(listOf("https://www.bible.com/bible/3034/JHN.3.16.BSB"),
      fixtureLinks("John 3:16", "john", internal.copy(readerMode = "biblecom"), "bsb"))
    assertEquals(listOf(Linker.buildBibleGatewayUrl("John 3:16", "ESV")),
      fixtureLinks("John 3:16", "john", internal.copy(readerMode = "biblegateway", verseShareProvider = "biblecom"), "bsb"))
  }

  @Test
  fun shareProviderDefaultsAndUnrecognizedValuesNormalizeToBibleCom() {
    assertEquals("biblecom", PrefsState().verseShareProvider)
    for (value in listOf(null, "", "  ", "internal", "other-site", " BIBLECOM ")) {
      assertEquals("biblecom", normalizeVerseShareProvider(value), value)
    }
    assertEquals("biblegateway", normalizeVerseShareProvider("  BIBLEGATEWAY  "))
    assertEquals(listOf("https://www.bible.com/bible/3034/JHN.3.16.BSB"), fixtureLinks(
      "John 3:16", "john", PrefsState(appLanguage = "en", verseShareProvider = "unsupported"), "bsb"
    ))
  }

  @Test
  fun internalSharingOnlyResolvesPublicReadersAndRejectsCustomSchemeResults() {
    val attempts = mutableListOf<Pair<String, String>>()
    val prefs = PrefsState(appLanguage = "en", readerMode = "internal", translation = "ESV")
    val links = readerShareLinks("John 3:16", "john", prefs, "bsb",
      resolveExternal = { _, _, candidate ->
        attempts += candidate.readerMode to candidate.translation
        "BSB" to "biblecompanion://open?book=john"
      },
      resolveSourceAnchor = { _, _, _, _ -> error("Bible.com must keep its audited resolver") })

    assertEquals(listOf("biblecom" to "BSB"), attempts)
    assertTrue(links.isEmpty())
  }

  @Test
  fun internalDefaultsHaveAnAuditedPublicTargetInAllThirteenLanguages() {
    val expected = mapOf(
      "en" to ("BSB" to 3034), "de" to ("SCH2000" to 157), "es" to ("NVI" to 128),
      "fr" to ("NBS" to 104), "it" to ("NR06" to 122), "pt" to ("NVT" to 1930),
      "ru" to ("NRT" to 143), "ja" to ("JCB" to 83), "ko" to ("RNKSV" to 142),
      "zh-Hans" to ("CCB" to 36), "zh-Hant" to ("RCUV" to 139),
      "ar" to ("SAB" to 153), "hi" to ("IRVHIN" to 1980)
    )
    for ((language, provider) in expected) {
      val prefs = PrefsState(appLanguage = language, readerMode = "internal", translation = "ESV")
      val source = BibleEditions.defaultForLanguage(language)
      assertEquals(listOf("https://www.bible.com/bible/${provider.second}/JHN.3.16.${provider.first}"),
        fixtureLinks("John 3:16", "john", prefs, source), language)
      assertEquals(provider.first, readerShareEditionLabel(language, source), language)
    }
  }

  @Test
  fun gatewayDefaultsEncodeReferenceLookupsInAllThirteenLanguages() {
    val expected = mapOf(
      "en" to "NIV", "de" to "SCH2000", "es" to "NVI", "fr" to "SG21", "it" to "NR2006",
      "pt" to "NVT", "ru" to "NRT", "ja" to "JLB", "ko" to "KLB", "zh-Hans" to "CCB",
      "zh-Hant" to "RCU17TS", "ar" to "NAV", "hi" to "ERV-HI"
    )
    for ((language, version) in expected) {
      val prefs = PrefsState(appLanguage = language, verseShareProvider = "biblegateway", translation = "ESV")
      val links = fixtureLinks("John 3:16", "john", prefs, BibleEditions.defaultForLanguage(language))
      // These catalog entries are reference lookup destinations; this assertion
      // does not establish provider passage identity or text equivalence.
      assertEquals(listOf(Linker.buildBibleGatewayUrl("John 3:16", version)), links, language)
      assertTrue(links.single().startsWith("https://www.biblegateway.com/passage/?search=John"), language)
      assertTrue(links.single().endsWith("&version=$version"), language)
    }
  }

  @Test
  fun gatewayLookupUsesItsOwnCatalogAliasesAndDeuterocanonicalFallbacks() {
    val italian = PrefsState(appLanguage = "it", readerMode = "biblegateway", translation = "NR06")
    assertEquals(listOf(Linker.buildBibleGatewayUrl("John 3:16", "NR2006")),
      fixtureLinks("John 3:16", "john", italian, "nr06"))
    val chinese = PrefsState(appLanguage = "zh-Hant", readerMode = "biblegateway", translation = "CCB_T")
    assertEquals(listOf(Linker.buildBibleGatewayUrl("John 3:16", "CCBT")),
      fixtureLinks("John 3:16", "john", chinese, "rcuv"))
    val english = PrefsState(appLanguage = "en", verseShareProvider = "biblegateway")
    assertEquals(listOf(Linker.buildBibleGatewayUrl("Tobit 1:1", "NRSVUE")),
      fixtureLinks("Tobit 1:1", "tobit", english, "bsb"))
  }

  @Test
  fun aSafelyMappedSelectedExternalProviderIsRespected() {
    val prefs = PrefsState(appLanguage = "de", readerMode = "biblegateway", translation = "SCH2000")
    val links = fixtureLinks("Psalms 8:4", "psalms", prefs, BibleEditions.LUTHER_1912) { _, from, to, anchor ->
      assertEquals(BibleEditions.LUTHER_1912, from)
      assertEquals("sch2000", to)
      assertEquals(VerseAnchor(8, 4), anchor)
      VerseAnchor(8, 5)
    }

    assertEquals(listOf(Linker.buildBibleGatewayUrl("Psalms 8:5", "SCH2000")), links)
  }

  @Test
  fun gatewayLookupMapsAlternateSourceCoordinatesBeforeQueryingAnUnauditedCatalogEntry() {
    val prefs = PrefsState(appLanguage = "de", readerMode = "biblegateway", translation = "HOF")
    val links = fixtureLinks("Psalms 8:4-5", "psalms", prefs, BibleEditions.LUTHER_1912) { _, from, to, anchor ->
      assertEquals(BibleEditions.LUTHER_1912, from)
      assertEquals("sch2000", to)
      assertEquals(VerseAnchor(8, 4, 5), anchor)
      VerseAnchor(8, 5, 6)
    }

    assertEquals(listOf(Linker.buildBibleGatewayUrl("Psalms 8:5-6", "HOF")), links)
  }

  @Test
  fun gatewayCannotBypassAFailedAuditedCrosswalkWithABaseLookup() {
    val prefs = PrefsState(appLanguage = "en", readerMode = "biblegateway", translation = "KJV")
    var attempts = 0
    val links = fixtureLinks("Revelation 12:17-18", "revelation", prefs, "bsb") { _, from, to, anchor ->
      attempts += 1
      assertEquals("bsb", from)
      assertEquals(BibleEditions.KJV_1769, to)
      assertEquals(VerseAnchor(12, 17, 18), anchor)
      null // The merged BSB unit crosses the KJV chapter boundary.
    }

    assertTrue(links.isEmpty())
    assertEquals(1, attempts)
  }

  @Test
  fun gatewayInvalidNativeUnitsAndUnmappedAlternateLookupsStayUnlinked() {
    val base = PrefsState(appLanguage = "en", verseShareProvider = "biblegateway")
    assertTrue(fixtureLinks("John 3:99", "john", base, "bsb") { _, _, _, _ -> null }.isEmpty())
    val alternate = PrefsState(appLanguage = "ja", verseShareProvider = "biblegateway")
    assertTrue(fixtureLinks("Psalms 8:4", "psalms", alternate, BibleEditions.BUNGO) { _, _, _, _ -> null }.isEmpty())
    val audited = PrefsState(appLanguage = "de", verseShareProvider = "biblegateway")
    assertTrue(fixtureLinks("John 3:99", "john", audited, "sch2000") { _, _, _, _ -> null }.isEmpty())
  }

  @Test
  fun unsupportedExternalEditionFallsBackThroughTheAuditedNumberingResolver() {
    val prefs = PrefsState(appLanguage = "de", readerMode = "biblecom", translation = "DELUT")
    val links = fixtureLinks("Isaiah 1:2", "isaiah", prefs, BibleEditions.LUTHER_1912) { _, from, to, anchor ->
      assertEquals(BibleEditions.LUTHER_1912, from)
      assertEquals("sch2000", to)
      assertEquals(VerseAnchor(1, 2), anchor)
      VerseAnchor(1, 3)
    }

    assertEquals(listOf("https://www.bible.com/bible/157/ISA.1.3.SCH2000"), links)
  }

  @Test
  fun unknownAlternateNumberingFailsClosedWithoutAnUnmappedRawFallback() {
    val prefs = PrefsState(appLanguage = "it", readerMode = "biblecom", translation = "DB1885")
    assertTrue(fixtureLinks("Job 3:10-12", "job", prefs, BibleEditions.DIODATI_1885) { _, _, _, _ ->
      null
    }.isEmpty())
    assertTrue(fixtureLinks("Job 3", "job", prefs, BibleEditions.DIODATI_1885) { _, _, _, _ ->
      error("a chapter-only alternate reference must not reach an anchor resolver")
    }.isEmpty())
  }

  @Test
  fun englishKjvKeepsItsSourceEditionAndEntireRangeEvenWithHiddenEsv() {
    val story = story("john-3", "John 3:1-36", listOf("For God so loved the world. (3:16-17)."))
    val prefs = PrefsState(appLanguage = "en", internalBibleVersion = BibleEditions.KJV_1769)
    val content = payload(Book("john", "John", listOf(story)), setOf(story.id to 0), prefs, BibleEditions.KJV_1769)

    assertTrue("John 3:16-17 KJV" in content.text)
    assertTrue("https://www.bible.com/bible/1/JHN.3.16-17.KJV" in content.text)
    assertFalse("biblecompanion://" in content.text)
    assertFalse("BSB" in content.text)
  }

  @Test
  fun referenceLocalizationAndLabelFollowTheSourceRatherThanThePublicTarget() {
    val story = story("psalms-8", "Psalms 8:1-10", listOf("Traditional source text. (8:4)."))
    val prefs = PrefsState(appLanguage = "de")
    val content = buildReaderShareText("old_testament", Book("psalms", "Psalms", listOf(story)),
      setOf(story.id to 0), prefs, BibleEditions.LUTHER_1912,
      linksForReference = { _ -> listOf("https://www.bible.com/bible/157/PSA.8.5.SCH2000") },
      localizeReference = { it.replace("Psalms", "Psalmen") })

    assertTrue("Psalmen 8:4 Luther 1912" in content.text)
    assertTrue("PSA.8.5.SCH2000" in content.text)
    assertFalse("Psalmen 8:4 SCH2000" in content.text)
  }

  @Test
  fun auditedSingleChapterAssetUsesItsExactSelectedVerseAndPublicLink() {
    val story = story("susanna-1", "Susanna 1-64", listOf(
      "In Babylon wohnte ein Mann mit Namen Jojakim. (1)."
    ))
    val prefs = PrefsState(appLanguage = "de")
    val content = buildReaderShareText("deuterocanonical", Book("susanna", "Susanna", listOf(story)),
      setOf(story.id to 0), prefs, "sch2000",
      linksForReference = { ref ->
        fixtureLinks(ref, "susanna", prefs, "sch2000") { bookId, from, to, anchor ->
          assertEquals("sch2000", from)
          assertEquals(from, to)
          fullNativeVerseAnchor(anchor, story.summaryBullets.mapNotNull {
            parseTrailingVerseAnchor(it, story.id, bookId)?.anchor
          })
        }
      },
      localizeReference = { it })

    assertEquals(
      "\"In Babylon wohnte ein Mann mit Namen Jojakim.\"\nSusanna 1:1 SCH2000\n\n" +
        "https://www.bible.com/bible/3523/SUS.1.1.NRSVUE",
      content.text
    )
    assertEquals("Susanna 1:1", content.primaryRef)
  }

  @Test
  fun anUnknownBareMarkerCannotInventASingleChapterVerseIdentity() {
    val story = story("unknown-1", "Susanna 1-64", listOf("Source text (an actual parenthesis) (18)."))
    val content = buildReaderShareText("deuterocanonical", Book("susanna", "Susanna", listOf(story)),
      setOf(story.id to 0), PrefsState(appLanguage = "en"), "bsb",
      linksForReference = { _ -> error("unknown bare markers must not request a verse link") },
      localizeReference = { it })

    assertEquals("\"Source text (an actual parenthesis) (18).\"\nSusanna 1-64 BSB", content.text)
  }

  @Test
  fun authoritativeRouteBookIdKeepsLocalizedBelVerseAndLinksWithoutMutatingTheAsset() {
    val story = story("bel-1", "Bel und der Drache 1-42", listOf(
      "Der K\u00F6nig Astyages ging zu seinen V\u00E4tern heim und sein Reich \u00FCbernahm der Perser Kyrus. (1)."
    ))
    val book = Book("bel", "Bel und der Drache", listOf(story))
    val authoritativeBookId = "bel_and_the_dragon"
    val prefs = PrefsState(appLanguage = "de")
    val content = buildReaderShareText("deuterocanonical", book, setOf(story.id to 0), prefs, "sch2000",
      linksForReference = { ref ->
        fixtureLinks(ref, authoritativeBookId, prefs, "sch2000") { bookId, _, _, anchor ->
          fullNativeVerseAnchor(anchor, story.summaryBullets.mapNotNull {
            parseTrailingVerseAnchor(it, story.id, bookId)?.anchor
          })
        }
      },
      localizeReference = { it },
      bookId = authoritativeBookId)

    assertEquals("Bel and the Dragon 1:1", content.primaryRef)
    assertTrue("Bel and the Dragon 1:1 SCH2000" in content.text)
    assertTrue("https://www.bible.com/bible/3523/BEL.1.1.NRSVUE" in content.text)
    assertFalse("biblecompanion://" in content.text)
    assertFalse("(1)." in content.text)
    assertEquals("bel", book.id)
  }

  private fun story(id: String, reference: String, bullets: List<String>) =
    Story(id = id, title = reference, refs = listOf(reference), summaryBullets = bullets)

  private fun payload(
    book: Book,
    selected: Set<Pair<String, Int>>,
    prefs: PrefsState,
    sourceEdition: String
  ): SelectedContent = buildReaderShareText("new_testament", book, selected, prefs, sourceEdition,
    linksForReference = { ref -> fixtureLinks(ref, book.id, prefs, sourceEdition) },
    localizeReference = { it })

  // Native assets are platform-backed. This fixture supplies only their anchor
  // crosswalk; provider catalogs, audited targets, reference parsing and public
  // URL generation all use the production helpers.
  private fun fixtureLinks(
    reference: String,
    bookId: String,
    prefs: PrefsState,
    sourceEdition: String,
    resolveAnchor: (String, String, String, VerseAnchor) -> VerseAnchor? = { _, _, _, anchor -> anchor }
  ): List<String> = readerShareLinks(reference, bookId, prefs, sourceEdition,
    resolveExternal = { ref, targetBook, candidate ->
      val language = candidate.appLanguage
      val version = Linker.selectedVersionForReader(candidate.translation, candidate.readerMode, language)
      val target = version?.let { providerNumberingEdition(language, candidate.readerMode, it, targetBook) }
      val mapped = editionAwareCanonicalReference(ref, targetBook, BibleEditions.defaultForLanguage(language),
        sourceEdition, target, resolveAnchor)
      mapped?.let { Linker.linkForReader(it, candidate.translation, candidate.readerMode, language) }
    },
    resolveSourceAnchor = resolveAnchor)
}
