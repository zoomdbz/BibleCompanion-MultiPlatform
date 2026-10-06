package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderEditionSelectionTest {
  private fun book(id: String, chapters: Map<Int, List<VerseAnchor>>): Book = Book(
    id = id,
    title = id,
    stories = chapters.map { (chapter, anchors) ->
      Story("$id-$chapter", "Chapter $chapter", emptyList(), anchors.map {
        "Text. (${it.chapter}:${it.verseStart}" +
          (if (it.verseEnd != it.verseStart) "-${it.verseEnd}" else "") + ")."
      })
    }
  )

  @Test
  fun savedReaderCannotOverrideANewEditionInAnyLanguage() {
    for (language in listOf("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")) {
      val editions = BibleEditions.available(language)
      for (source in editions) for (target in editions) {
        val prefs = PrefsState(
          appLanguage = language, internalBibleVersion = target,
          lastReadSourceLanguage = language, lastReadSourceEdition = source
        )
        assertEquals(source == target, readingResumeCanRestoreSavedEdition(prefs), "$language: $source -> $target")
      }
    }
  }

  @Test
  fun savedReaderRespectsLanguageAliasesButNeverConflatesChineseScripts() {
    assertTrue(readingResumeCanRestoreSavedEdition(PrefsState(
      appLanguage = "en-US", internalBibleVersion = "kjv1769",
      lastReadSourceLanguage = "en-GB", lastReadSourceEdition = "KJV"
    )))
    assertFalse(readingResumeCanRestoreSavedEdition(PrefsState(
      appLanguage = "zh-Hant", internalBibleVersion = "cuv",
      lastReadSourceLanguage = "zh-Hans", lastReadSourceEdition = "cuv"
    )))
  }

  @Test
  fun legacyProgressCanRestoreOnlyItsDefaultEdition() {
    assertTrue(readingResumeCanRestoreSavedEdition(PrefsState(appLanguage = "en")))
    assertFalse(readingResumeCanRestoreSavedEdition(PrefsState(appLanguage = "en", internalBibleVersion = "kjv1769")))
    assertFalse(readingResumeCanRestoreSavedEdition(PrefsState(appLanguage = "en", lastReadSourceEdition = "unknown")))
  }

  @Test
  fun editionSwitchPreservesAnExactVerseWhenTheCrosswalkSupportsIt() {
    val target = book("matthew", mapOf(1 to listOf(VerseAnchor(1, 1)), 5 to listOf(VerseAnchor(5, 16))))
    var requested: VerseAnchor? = null
    val position = readerPositionForEdition(
      target, "matthew-5", "en", "en", "bsb", "kjv1769", VerseAnchor(5, 16)
    ) { requested = it; it }
    assertEquals(VerseAnchor(5, 16), requested)
    assertEquals("matthew-5", position.storyId)
    assertEquals(5, position.chapter)
    assertEquals(16, position.verse)
    assertEquals(16, position.verseEnd)
  }

  @Test
  fun italianJobCanMoveToTheCorrespondingTraditionalChapter() {
    val target = book("job", mapOf(39 to listOf(VerseAnchor(39, 34)), 40 to listOf(VerseAnchor(40, 1))))
    val position = readerPositionForEdition(target, "job-40", "it", "it", "nr06", "diodati1885") {
      assertEquals(VerseAnchor(40, 1), it)
      VerseAnchor(39, 34)
    }
    assertEquals("job-39", position.storyId)
    assertEquals(39, position.chapter)
    assertEquals(34, position.verse)
  }

  @Test
  fun sameEditionRetainsWholeMergedSourceUnitsWithoutCallingTheCrosswalk() {
    val target = book("proverbs", mapOf(1 to listOf(VerseAnchor(1, 1)), 23 to listOf(VerseAnchor(23, 24, 25))))
    val position = readerPositionForEdition(
      target, "proverbs-23", "ja", "ja", "jcb", "jcb", VerseAnchor(23, 24)
    ) { error("A same-edition position needs no crosswalk") }
    assertEquals(24, position.verse)
    assertEquals(25, position.verseEnd)
  }

  @Test
  fun unrepresentableOrMissingMappingsStillUseTheNewEditionWithoutAFabricatedVerse() {
    val target = book("revelation", mapOf(12 to listOf(VerseAnchor(12, 17)), 13 to listOf(VerseAnchor(13, 1))))
    val position = readerPositionForEdition(
      target, "revelation-12", "en", "en", "bsb", "kjv1769", VerseAnchor(12, 17, 18)
    ) { null }
    assertEquals("revelation-12", position.storyId)
    assertNull(position.verse)
    assertNull(position.verseEnd)
  }

  @Test
  fun invalidMappingCannotHighlightMissingTargetText() {
    val target = book("genesis", mapOf(1 to listOf(VerseAnchor(1, 1)), 2 to listOf(VerseAnchor(2, 1))))
    val position = readerPositionForEdition(target, "genesis-2", "en", "en", "bsb", "kjv1769") {
      VerseAnchor(2, 99)
    }
    assertEquals("genesis-2", position.storyId)
    assertNull(position.verse)
  }

  @Test
  fun unknownSourceEditionCannotGuessAnEquivalence() {
    val target = book("genesis", mapOf(1 to listOf(VerseAnchor(1, 1)), 2 to listOf(VerseAnchor(2, 1))))
    val position = readerPositionForEdition(target, "genesis-2", "en", "en", "unknown", "kjv1769") {
      error("Unknown source editions have no verified crosswalk")
    }
    assertEquals("genesis-2", position.storyId)
    assertNull(position.verse)
  }

  @Test
  fun languageChangeKeepsNativeChapterConversionButDropsUnmappedVerses() {
    val target = book("malachi", (1..3).associateWith { listOf(VerseAnchor(it, 1)) })
    val position = readerPositionForEdition(
      target, "malachi-4", "en", "de", "kjv1769", "sch2000", VerseAnchor(4, 1)
    ) { error("Different languages cannot reuse a same-language edition crosswalk") }
    assertEquals("malachi-3", position.storyId)
    assertEquals(3, position.chapter)
    assertNull(position.verse)
  }

  @Test
  fun unavailableChapterKeepsNearbyContextRatherThanJumpingToTheBooksStart() {
    val target = book("malachi", (1..3).associateWith { listOf(VerseAnchor(it, 1)) })
    val position = readerPositionForEdition(target, "malachi-4", "de", "de", "sch2000", "luther1912")
    assertEquals("malachi-3", position.storyId)
    assertNull(position.verse)
  }

  @Test
  fun introAndNamedPrologueRemainDistinctFromChapterOne() {
    val chapter = Story("sirach-1", "Chapter 1", emptyList(), listOf("Text. (1:1)."))
    val target = Book("sirach", "Sirach", listOf(Story("sirach-prologue", "Prologue", emptyList(), emptyList()), chapter))
    val intro = readerPositionForEdition(target, null, "en", "en", "bsb", "kjv1769")
    val prologue = readerPositionForEdition(target, "sirach-prologue", "en", "en", "bsb", "kjv1769")
    assertNull(intro.storyId)
    assertNull(intro.verse)
    assertEquals("sirach-prologue", prologue.storyId)
    assertNull(prologue.verse)
  }
}
