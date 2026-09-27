package com.dividesbyzer0.biblecompanion

internal enum class LibrarySection {
  TORAH,
  HISTORICAL_BOOKS,
  WISDOM_AND_POETRY,
  PROPHETS,
  GOSPELS,
  CHURCH_HISTORY,
  LETTERS,
  PAULINE_EPISTLES,
  GENERAL_EPISTLES,
  REVELATION
}

internal data class LibraryBookEntry(val id: String, val title: String)

internal data class LibraryBookSection(
  val section: LibrarySection?,
  val suppliedHeading: String? = null,
  val books: List<LibraryBookEntry>
)

private val oldTestamentSections = mapOf(
  "genesis" to LibrarySection.TORAH,
  "exodus" to LibrarySection.TORAH,
  "leviticus" to LibrarySection.TORAH,
  "numbers" to LibrarySection.TORAH,
  "deuteronomy" to LibrarySection.TORAH,
  "joshua" to LibrarySection.HISTORICAL_BOOKS,
  "judges" to LibrarySection.HISTORICAL_BOOKS,
  "ruth" to LibrarySection.HISTORICAL_BOOKS,
  "1_samuel" to LibrarySection.HISTORICAL_BOOKS,
  "2_samuel" to LibrarySection.HISTORICAL_BOOKS,
  "1_kings" to LibrarySection.HISTORICAL_BOOKS,
  "2_kings" to LibrarySection.HISTORICAL_BOOKS,
  "1_chronicles" to LibrarySection.HISTORICAL_BOOKS,
  "2_chronicles" to LibrarySection.HISTORICAL_BOOKS,
  "ezra" to LibrarySection.HISTORICAL_BOOKS,
  "nehemiah" to LibrarySection.HISTORICAL_BOOKS,
  "esther" to LibrarySection.HISTORICAL_BOOKS,
  "job" to LibrarySection.WISDOM_AND_POETRY,
  "psalms" to LibrarySection.WISDOM_AND_POETRY,
  "proverbs" to LibrarySection.WISDOM_AND_POETRY,
  "ecclesiastes" to LibrarySection.WISDOM_AND_POETRY,
  "song_of_songs" to LibrarySection.WISDOM_AND_POETRY,
  "isaiah" to LibrarySection.PROPHETS,
  "jeremiah" to LibrarySection.PROPHETS,
  "lamentations" to LibrarySection.PROPHETS,
  "ezekiel" to LibrarySection.PROPHETS,
  "daniel" to LibrarySection.PROPHETS,
  "hosea" to LibrarySection.PROPHETS,
  "joel" to LibrarySection.PROPHETS,
  "amos" to LibrarySection.PROPHETS,
  "obadiah" to LibrarySection.PROPHETS,
  "jonah" to LibrarySection.PROPHETS,
  "micah" to LibrarySection.PROPHETS,
  "nahum" to LibrarySection.PROPHETS,
  "habakkuk" to LibrarySection.PROPHETS,
  "zephaniah" to LibrarySection.PROPHETS,
  "haggai" to LibrarySection.PROPHETS,
  "zechariah" to LibrarySection.PROPHETS,
  "malachi" to LibrarySection.PROPHETS
)

private val newTestamentSections = buildMap {
  listOf("matthew", "mark", "luke", "john").forEach {
    put(it, LibrarySection.GOSPELS)
  }
  put("acts", LibrarySection.CHURCH_HISTORY)
  listOf(
    "romans", "1_corinthians", "2_corinthians", "galatians", "ephesians",
    "philippians", "colossians", "1_thessalonians", "2_thessalonians",
    "1_timothy", "2_timothy", "titus", "philemon"
  ).forEach { put(it, LibrarySection.PAULINE_EPISTLES) }
  listOf(
    "hebrews", "james", "1_peter", "2_peter", "1_john", "2_john", "3_john", "jude"
  ).forEach { put(it, LibrarySection.GENERAL_EPISTLES) }
  put("revelation", LibrarySection.REVELATION)
}

internal fun buildLibrarySections(
  collection: String,
  indexedEntries: List<Pair<String, String>>
): List<LibraryBookSection> {
  val canonicalMap = when (collection) {
    "old_testament" -> oldTestamentSections
    "new_testament" -> newTestamentSections
    else -> null
  }

  if (canonicalMap != null) {
    val grouped = linkedMapOf<LibrarySection, MutableList<LibraryBookEntry>>()
    indexedEntries.forEach { (id, title) ->
      if (id.isBlank()) return@forEach
      val section = canonicalMap[id] ?: LibrarySection.GENERAL_EPISTLES
      grouped.getOrPut(section) { mutableListOf() }.add(LibraryBookEntry(id, title))
    }
    return grouped.flatMap { (section, books) ->
      buildList {
        if (section == LibrarySection.PAULINE_EPISTLES) {
          add(LibraryBookSection(section = LibrarySection.LETTERS, books = emptyList()))
        }
        add(LibraryBookSection(section = section, books = books))
      }
    }
  }

  // Non-canonical indexes can contain localized disclaimer or section rows.
  // Keep them in their original position and never reinterpret their content.
  val result = mutableListOf<LibraryBookSection>()
  var heading: String? = null
  var books = mutableListOf<LibraryBookEntry>()

  fun flush() {
    if (heading != null || books.isNotEmpty()) {
      result += LibraryBookSection(section = null, suppliedHeading = heading, books = books.toList())
    }
    heading = null
    books = mutableListOf()
  }

  indexedEntries.forEach { (id, title) ->
    if (id.isBlank()) {
      flush()
      heading = title
    } else {
      books += LibraryBookEntry(id, title)
    }
  }
  flush()
  return result
}
