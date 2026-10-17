package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VotdSharingTest {
  @Test
  fun internalReaderSharesThroughItsSelectedPublicWebsite() {
    val verse = DailyVerse("For God so loved the world.", "John 3:16", editionId = "bsb")
    val prefs = PrefsState(appLanguage = "en", readerMode = "internal")

    assertEquals(
      "\"For God so loved the world.\"\nJohn 3:16 BSB\n\n" +
        "https://www.bible.com/bible/3034/JHN.3.16.BSB",
      payload(verse, prefs.copy(verseShareProvider = "biblecom"))
    )
    assertEquals(
      "\"For God so loved the world.\"\nJohn 3:16 BSB\n\n" +
        Linker.buildBibleGatewayUrl("John 3:16", "NIV"),
      payload(verse, prefs.copy(verseShareProvider = "biblegateway"))
    )
  }

  @Test
  fun externalScriptureDestinationOverridesTheHiddenInternalShareChoice() {
    val verse = DailyVerse("For God so loved the world.", "John 3:16", editionId = "bsb")
    val bibleCom = payload(verse, PrefsState(appLanguage = "en", readerMode = "biblecom",
      translation = "KJV", verseShareProvider = "biblegateway"))
    val gateway = payload(verse, PrefsState(appLanguage = "en", readerMode = "biblegateway",
      translation = "ESV", verseShareProvider = "biblecom"))

    assertTrue(bibleCom.endsWith("https://www.bible.com/bible/1/JHN.3.16.KJV"))
    assertTrue(gateway.endsWith(Linker.buildBibleGatewayUrl("John 3:16", "ESV")))
    assertTrue("\nJohn 3:16 BSB\n" in bibleCom)
    assertTrue("\nJohn 3:16 BSB\n" in gateway)
    assertFalse("biblecompanion://" in bibleCom + gateway)
  }

  @Test
  fun fallbackDailyVerseKeepsItsActualBaseEditionDespiteAKjvReaderPreference() {
    val prefs = PrefsState(appLanguage = "en", internalBibleVersion = BibleEditions.KJV_1769)
    // A failed alternate-edition selection can return a base-corpus verse.
    // Legacy entries without an edition also belong to that base corpus.
    for (edition in listOf("bsb", null)) {
      val result = payload(DailyVerse("The displayed base text.", "John 3:16", editionId = edition), prefs)

      assertTrue("\nJohn 3:16 BSB\n" in result, edition ?: "legacy base")
      assertTrue(result.endsWith("https://www.bible.com/bible/3034/JHN.3.16.BSB"))
      assertFalse("KJV" in result)
    }
  }

  @Test
  fun actualKjvDailyVerseKeepsItsSourceEditionAndPublicTarget() {
    val verse = DailyVerse("For God so loved the world.", "John 3:16", editionId = BibleEditions.KJV_1769)
    val prefs = PrefsState(appLanguage = "en", internalBibleVersion = "bsb")

    assertEquals(
      "\"For God so loved the world.\"\nJohn 3:16 KJV\n\n" +
        "https://www.bible.com/bible/1/JHN.3.16.KJV",
      payload(verse, prefs)
    )
  }

  @Test
  fun verseRangesStripInlineTagsAndFinishSourceSpeechWithAsciiQuotes() {
    val verse = DailyVerse("[J]He said, \u201cBe still.[/J]", "Psalms 46:10-11", editionId = "bsb")
    val result = payload(verse, PrefsState(appLanguage = "en"))

    assertEquals(
      "\"He said, \"Be still.\"\"\nPsalms 46:10-11 BSB\n\n" +
        "https://www.bible.com/bible/3034/PSA.46.10-11.BSB",
      result
    )
    assertEquals(4, result.count { it == '"' })
    assertFalse("[J]" in result || "[/J]" in result || '\u201c' in result)
  }

  @Test
  fun localizedCaptionKeepsSourceCoordinatesWhenThePublicTargetIsMapped() {
    val verse = DailyVerse("Traditional source text.", "Psalms 8:4", editionId = BibleEditions.LUTHER_1912)
    val prefs = PrefsState(appLanguage = "de")
    val result = buildVotdShareText(verse, prefs,
      resolveBookId = ::bookId,
      linksForReference = { reference, book, source ->
        fixtureLinks(reference, book, prefs, source) { actualBook, from, to, anchor ->
          assertEquals("psalms", actualBook)
          assertEquals(BibleEditions.LUTHER_1912, from)
          assertEquals("sch2000", to)
          assertEquals(VerseAnchor(8, 4), anchor)
          VerseAnchor(8, 5)
        }
      },
      localizeReference = { it.replace("Psalms", "Psalmen") })

    assertEquals(
      "\"Traditional source text.\"\nPsalmen 8:4 Luther 1912\n\n" +
        "https://www.bible.com/bible/157/PSA.8.5.SCH2000",
      result
    )
  }

  @Test
  fun actsOfTheApostlesAliasResolvesTheCanonicalBookAndPublicVerse() {
    val verse = DailyVerse("You will be My witnesses.", "Acts of the Apostles 1:8", editionId = "bsb")

    assertEquals(
      "\"You will be My witnesses.\"\nActs 1:8 BSB\n\n" +
        "https://www.bible.com/bible/3034/ACT.1.8.BSB",
      payload(verse, PrefsState(appLanguage = "en"))
    )
  }

  @Test
  fun sharingAFeastOverrideUsesTheDisplayedVerseRatherThanSelectingAnotherDailyVerse() {
    val verse = DailyVerse("The displayed feast text.", "Luke 2:14", isFeastOverride = true, editionId = "bsb")
    val prefs = PrefsState(appLanguage = "en", internalBibleVersion = BibleEditions.KJV_1769,
      verseShareProvider = "biblegateway")

    assertEquals(
      "\"The displayed feast text.\"\nLuke 2:14 BSB\n\n" +
        Linker.buildBibleGatewayUrl("Luke 2:14", "NIV"),
      payload(verse, prefs)
    )
  }

  @Test
  fun unknownBooksAndComplexReferencesCannotInventVerseLinks() {
    for (reference in listOf("Unknown Book 3:16", "John 3:16,18", "John 3:16; 4:1", "John 3")) {
      val result = buildVotdShareText(DailyVerse("Keep the displayed text.", reference, editionId = "bsb"),
        PrefsState(appLanguage = "en"), resolveBookId = ::bookId,
        linksForReference = { _, _, _ -> error("Unsupported reference must not request a verse link") },
        localizeReference = { it })

      assertEquals("\"Keep the displayed text.\"\n$reference BSB", result)
      assertFalse("://" in result)
    }
  }

  private fun payload(verse: DailyVerse, prefs: PrefsState): String =
    buildVotdShareText(verse, prefs, resolveBookId = ::bookId,
      linksForReference = { reference, book, source -> fixtureLinks(reference, book, prefs, source) },
      localizeReference = { it })

  private fun bookId(name: String): String? = mapOf(
    "John" to "john", "Psalms" to "psalms", "Acts" to "acts", "Luke" to "luke"
  )[name]

  // Only the asset-backed verse crosswalk is injected. Public provider choices,
  // catalog aliases, numbering decisions, and URL construction remain real.
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
