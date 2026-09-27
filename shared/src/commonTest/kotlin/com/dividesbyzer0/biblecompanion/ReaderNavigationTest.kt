package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
