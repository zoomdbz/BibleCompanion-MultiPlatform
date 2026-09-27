package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySectionsTest {

  @Test
  fun oldTestamentPreservesAllBookOrderAcrossCanonicalSections() {
    val ids = listOf(
      "genesis", "exodus", "leviticus", "numbers", "deuteronomy",
      "joshua", "judges", "ruth", "1_samuel", "2_samuel", "1_kings", "2_kings",
      "1_chronicles", "2_chronicles", "ezra", "nehemiah", "esther",
      "job", "psalms", "proverbs", "ecclesiastes", "song_of_songs",
      "isaiah", "jeremiah", "lamentations", "ezekiel", "daniel",
      "hosea", "joel", "amos", "obadiah", "jonah", "micah", "nahum",
      "habakkuk", "zephaniah", "haggai", "zechariah", "malachi"
    )

    val result = buildLibrarySections("old_testament", ids.map { it to "title:$it" })

    assertEquals(
      listOf(
        LibrarySection.TORAH,
        LibrarySection.HISTORICAL_BOOKS,
        LibrarySection.WISDOM_AND_POETRY,
        LibrarySection.PROPHETS
      ),
      result.map { it.section }
    )
    assertEquals(listOf(5, 12, 5, 17), result.map { it.books.size })
    assertEquals(ids, result.flatMap { section -> section.books.map { it.id } })
  }

  @Test
  fun newTestamentPreservesAllBookOrderAcrossCanonicalSections() {
    val ids = listOf(
      "matthew", "mark", "luke", "john", "acts",
      "romans", "1_corinthians", "2_corinthians", "galatians", "ephesians",
      "philippians", "colossians", "1_thessalonians", "2_thessalonians",
      "1_timothy", "2_timothy", "titus", "philemon",
      "hebrews", "james", "1_peter", "2_peter", "1_john", "2_john",
      "3_john", "jude", "revelation"
    )

    val result = buildLibrarySections("new_testament", ids.map { it to "title:$it" })

    assertEquals(
      listOf(
        LibrarySection.GOSPELS,
        LibrarySection.CHURCH_HISTORY,
        LibrarySection.LETTERS,
        LibrarySection.PAULINE_EPISTLES,
        LibrarySection.GENERAL_EPISTLES,
        LibrarySection.REVELATION
      ),
      result.map { it.section }
    )
    assertEquals(listOf(4, 1, 0, 13, 8, 1), result.map { it.books.size })
    assertEquals(ids, result.flatMap { section -> section.books.map { it.id } })
  }

  @Test
  fun hebrewsIsGeneralEpistleAndRevelationStaysSeparate() {
    val result = buildLibrarySections(
      "new_testament",
      listOf("hebrews", "james", "revelation").map { it to it }
    )

    assertEquals(
      listOf(LibrarySection.GENERAL_EPISTLES, LibrarySection.REVELATION),
      result.map { it.section }
    )
    assertEquals(listOf("hebrews", "james"), result[0].books.map { it.id })
  }

  @Test
  fun supplementalCollectionsKeepLocalizedNotesAndBookOrder() {
    val input = listOf(
      "" to "Localized opening note",
      "first" to "First",
      "second" to "Second",
      "" to "Localized second note",
      "third" to "Third"
    )

    val result = buildLibrarySections("deuterocanonical", input)

    assertEquals(listOf("Localized opening note", "Localized second note"), result.map { it.suppliedHeading })
    assertEquals(listOf("first", "second", "third"), result.flatMap { section -> section.books.map { it.id } })
  }
}
