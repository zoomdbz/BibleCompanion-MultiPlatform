package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReaderNavigationTest {
  @Test
  fun chapterPickerSeparatesNamedFrontMatterFromNumberedChapters() {
    val book = Book(
      id = "sirach",
      title = "Sirach",
      stories = listOf(
        Story("sirach-prologue", "Prologue", emptyList(), emptyList()),
        Story("sirach-1", "Chapter 1", emptyList(), listOf("Text. (1:1).")),
        Story("sirach-2", "Chapter 2", emptyList(), listOf("Text. (2:1)."))
      )
    )

    val index = ChapterLocator.build(book)

    assertEquals(linkedMapOf(1 to "sirach-1", 2 to "sirach-2"), index.byChapter)
    assertEquals(listOf(ChapterLocator.Special("sirach-prologue", "prologue")), index.specials)
  }

  @Test
  fun singleStoryBookUsesChapterOneAndItsNativeVerseRange() {
    val story = Story(
      id = "song_of_three-3",
      title = "The Song of the Three",
      refs = emptyList(),
      summaryBullets = listOf("Text. (1:1-2).")
    )
    val index = ChapterLocator.build(Book("song_of_three", story.title, listOf(story)))

    assertEquals(mapOf(1 to story.id), index.byChapter)
    assertEquals(listOf(1, 2), versePickerNumbers(story.summaryBullets, chapter = 1))
  }

  @Test
  fun readingResumeUsesTheTargetLanguagesNativeChapterAndCount() {
    val germanMalachi = Book(
      id = "malachi",
      title = "Maleachi",
      stories = (1..3).map { chapter ->
        Story(
          id = "malachi-$chapter",
          title = "Maleachi $chapter",
          refs = emptyList(),
          summaryBullets = listOf("Text. ($chapter:1).")
        )
      }
    )

    val resume = readingResumeForBook(
      book = germanMalachi,
      storedStoryId = "malachi-4",
      sourceLanguage = "en",
      targetLanguage = "de"
    )

    assertEquals("Maleachi", resume.title)
    assertEquals("malachi-3", resume.storyId)
    assertEquals(3, resume.chapter)
    assertEquals(3, resume.chapterCount)
  }

  @Test
  fun externalReaderRequestIdentityDistinguishesRepeatedIdenticalLinks() {
    val route = Dest.BookView.route(
      "new_testament",
      "matthew",
      "matthew-28",
      verse = 20,
      sourceLang = "en",
      sourceEdition = "bsb"
    )

    assertEquals("$route&requestId=41", withExternalReaderRequestId(route, 41L))
    assertEquals("$route&requestId=42", withExternalReaderRequestId(route, 42L))
  }

  @Test
  fun externalRequestIdentityOnlyChangesReaderRoutes() {
    assertEquals(
      "book/new_testament/matthew?requestId=7",
      withExternalReaderRequestId("book/new_testament/matthew", 7L)
    )
    assertEquals(Dest.SavedItems.route, withExternalReaderRequestId(Dest.SavedItems.route, 7L))
  }

  @Test
  fun consumedReaderIdentitySurvivesRecreationButNotANewIdenticalRequest() {
    val routeParts = arrayOf<Any?>(
      "new_testament", "matthew", "matthew-28", "en", "en", "bsb", "bsb", 20, 20
    )
    val consumed = readerRequestIdentity(41L, *routeParts)

    assertEquals(consumed, readerRequestIdentity(41L, *routeParts))
    assertNotEquals(consumed, readerRequestIdentity(42L, *routeParts))
  }

  @Test
  fun readerIdentitySeparatesEveryNavigationTargetDimension() {
    fun identity(
      collection: String = "new_testament",
      book: String = "matthew",
      story: String = "matthew-1",
      sourceLanguage: String = "en",
      effectiveLanguage: String = "en",
      sourceEdition: String = "bsb",
      activeEdition: String = "bsb",
      verse: Int? = 1
    ) = readerRequestIdentity(
      null,
      collection,
      book,
      story,
      sourceLanguage,
      effectiveLanguage,
      sourceEdition,
      activeEdition,
      verse,
      verse
    )

    val base = identity()
    assertNotEquals(base, identity(collection = "old_testament"))
    assertNotEquals(base, identity(book = "mark"))
    assertNotEquals(base, identity(story = "matthew-2"))
    assertNotEquals(base, identity(verse = 2))
    assertNotEquals(base, identity(sourceLanguage = "de"))
    assertNotEquals(base, identity(effectiveLanguage = "de"))
    assertNotEquals(base, identity(sourceEdition = "kjv"))
    assertNotEquals(base, identity(activeEdition = "kjv"))
  }

  @Test
  fun freshInternalContinueDoesNotMatchAnOlderExternalReaderRequest() {
    val oldExternalMatthew = readerRequestIdentity(
      41L, "new_testament", "matthew", "matthew-28", "en", "en", "bsb", "bsb", 20, 20
    )
    val internalContinueToMatthew = readerRequestIdentity(
      -1L, "new_testament", "matthew", "matthew-1", "en", "en", "kjv", "kjv", null, null
    )

    assertNotEquals(oldExternalMatthew, internalContinueToMatthew)
  }

  @Test
  fun readerIdentityEncodingSeparatesNullLiteralsAndDelimiterBoundaries() {
    assertNotEquals(
      readerRequestIdentity(null, null),
      readerRequestIdentity(null, "null")
    )
    assertNotEquals(
      readerRequestIdentity(null, "a", "bc"),
      readerRequestIdentity(null, "ab", "c")
    )
    assertNotEquals(
      readerRequestIdentity(null, "a;1:b"),
      readerRequestIdentity(null, "a", "1:b")
    )
  }

  @Test
  fun internalTargetStampPreservesLanguageAndEdition() {
    assertEquals(
      "book/new_testament/matthew?storyId=matthew-1&verse=1&sourceLang=en&sourceEdition=kjv&requestId=-7",
      Dest.BookView.route(
        col = "new_testament",
        bookId = "matthew",
        storyId = "matthew-1",
        verse = 1,
        sourceLang = "en",
        sourceEdition = "kjv",
        requestId = -7L
      )
    )
    assertEquals(
      "book/new_testament/matthew?storyId=matthew-1&verse=1&sourceLang=en&sourceEdition=bsb&requestId=-8",
      Dest.BookView.route(
        col = "new_testament",
        bookId = "matthew",
        storyId = "matthew-1",
        verse = 1,
        sourceLang = "en",
        sourceEdition = "bsb",
        requestId = -8L
      )
    )
  }

  @Test
  fun repeatedInternalTargetStampsProduceDistinctConsumedIdentities() {
    val targetParts = arrayOf<Any?>(
      "new_testament", "mark", "mark-1", "en", "en", "kjv", "kjv", null, null
    )

    assertNotEquals(
      readerRequestIdentity(-1L, *targetParts),
      readerRequestIdentity(-2L, *targetParts)
    )
    assertEquals(-2L, followingInternalReaderRequestId(-1L))
  }

  @Test
  fun readingNowRestoreIdentityRejectsChangedPassageLanguageOrEdition() {
    fun identity(
      story: String = "matthew-14",
      language: String = "en",
      edition: String = "kjv1769"
    ) = readingNowRestoreIdentity(
      collection = "new_testament",
      bookId = "matthew",
      storyId = story,
      sourceLanguage = "en",
      sourceEdition = "kjv1769",
      currentLanguage = language,
      currentEdition = edition
    )

    val saved = identity()
    assertEquals(saved, identity())
    assertNotEquals(saved, identity(story = "matthew-15"))
    assertNotEquals(saved, identity(language = "de"))
    assertNotEquals(saved, identity(edition = "bsb"))
    assertEquals(
      null,
      readingNowRestoreIdentity(null, "matthew", null, null, null, "en", "bsb")
    )
  }

  @Test
  fun readingNowAcceptsOnlyTheExpectedRestoredReaderLeaf() {
    val readerRoute = Dest.BookView("new_testament", "matthew").route

    assertEquals(
      true,
      restoredReadingNowReaderMatches(
        readerRoute, "new_testament", "matthew", "new_testament", "matthew"
      )
    )
    assertEquals(
      false,
      restoredReadingNowReaderMatches(
        Dest.Read.route, null, null, "new_testament", "matthew"
      )
    )
    assertEquals(
      false,
      restoredReadingNowReaderMatches(
        readerRoute, "new_testament", "mark", "new_testament", "matthew"
      )
    )
  }

  @Test
  fun explicitReadNavigationExpiresSavedViewportButOtherTabsDoNot() {
    val firstRequest = Dest.BookView.route(
      "new_testament", "matthew", "matthew-14", verse = 22, requestId = 41L
    )
    val newerSameChapterRequest = Dest.BookView.route(
      "new_testament", "matthew", "matthew-14", verse = 27, requestId = 42L
    )

    assertEquals(true, invalidatesSavedReadingNowState(firstRequest))
    assertEquals(true, invalidatesSavedReadingNowState(newerSameChapterRequest))
    assertEquals(true, invalidatesSavedReadingNowState(Dest.Read.route))
    assertEquals(true, invalidatesSavedReadingNowState("tab_read"))
    assertEquals(false, invalidatesSavedReadingNowState(Dest.Home.route))
    assertEquals(false, invalidatesSavedReadingNowState("tab_study"))
    assertEquals(false, invalidatesSavedReadingNowState("tab_calendar"))
  }
}
