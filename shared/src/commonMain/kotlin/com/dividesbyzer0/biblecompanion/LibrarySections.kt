package com.dividesbyzer0.biblecompanion

internal enum class LibrarySection {
  LAW,
  HISTORY,
  WISDOM,
  MAJOR_PROPHETS,
  MINOR_PROPHETS,
  GOSPELS,
  PAUL,
  GENERAL
}

internal data class LibraryBookEntry(val id: String, val title: String)

internal data class LibraryBookSection(
  val section: LibrarySection?,
  val suppliedHeading: String? = null,
  val books: List<LibraryBookEntry>
)

private val oldTestamentSections = mapOf(
  "genesis" to LibrarySection.LAW,
  "exodus" to LibrarySection.LAW,
  "leviticus" to LibrarySection.LAW,
  "numbers" to LibrarySection.LAW,
  "deuteronomy" to LibrarySection.LAW,
  "joshua" to LibrarySection.HISTORY,
  "judges" to LibrarySection.HISTORY,
  "ruth" to LibrarySection.HISTORY,
  "1_samuel" to LibrarySection.HISTORY,
  "2_samuel" to LibrarySection.HISTORY,
  "1_kings" to LibrarySection.HISTORY,
  "2_kings" to LibrarySection.HISTORY,
  "1_chronicles" to LibrarySection.HISTORY,
  "2_chronicles" to LibrarySection.HISTORY,
  "ezra" to LibrarySection.HISTORY,
  "nehemiah" to LibrarySection.HISTORY,
  "esther" to LibrarySection.HISTORY,
  "job" to LibrarySection.WISDOM,
  "psalms" to LibrarySection.WISDOM,
  "proverbs" to LibrarySection.WISDOM,
  "ecclesiastes" to LibrarySection.WISDOM,
  "song_of_songs" to LibrarySection.WISDOM,
  "isaiah" to LibrarySection.MAJOR_PROPHETS,
  "jeremiah" to LibrarySection.MAJOR_PROPHETS,
  "lamentations" to LibrarySection.MAJOR_PROPHETS,
  "ezekiel" to LibrarySection.MAJOR_PROPHETS,
  "daniel" to LibrarySection.MAJOR_PROPHETS,
  "hosea" to LibrarySection.MINOR_PROPHETS,
  "joel" to LibrarySection.MINOR_PROPHETS,
  "amos" to LibrarySection.MINOR_PROPHETS,
  "obadiah" to LibrarySection.MINOR_PROPHETS,
  "jonah" to LibrarySection.MINOR_PROPHETS,
  "micah" to LibrarySection.MINOR_PROPHETS,
  "nahum" to LibrarySection.MINOR_PROPHETS,
  "habakkuk" to LibrarySection.MINOR_PROPHETS,
  "zephaniah" to LibrarySection.MINOR_PROPHETS,
  "haggai" to LibrarySection.MINOR_PROPHETS,
  "zechariah" to LibrarySection.MINOR_PROPHETS,
  "malachi" to LibrarySection.MINOR_PROPHETS
)

private val newTestamentSections = buildMap {
  listOf("matthew", "mark", "luke", "john", "acts").forEach {
    put(it, LibrarySection.GOSPELS)
  }
  listOf(
    "romans", "1_corinthians", "2_corinthians", "galatians", "ephesians",
    "philippians", "colossians", "1_thessalonians", "2_thessalonians",
    "1_timothy", "2_timothy", "titus", "philemon"
  ).forEach { put(it, LibrarySection.PAUL) }
  listOf(
    "hebrews", "james", "1_peter", "2_peter", "1_john", "2_john",
    "3_john", "jude", "revelation"
  ).forEach { put(it, LibrarySection.GENERAL) }
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
      val section = canonicalMap[id] ?: LibrarySection.GENERAL
      grouped.getOrPut(section) { mutableListOf() }.add(LibraryBookEntry(id, title))
    }
    return grouped.map { (section, books) -> LibraryBookSection(section = section, books = books) }
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
