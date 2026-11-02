package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StorySearchExplicitRefTest {
  @Test
  fun swahiliBookAliasesResolveExplicitChapterAndVerseToCanonicalBook() {
    val leviticus = Triple("old_testament", "leviticus", "Mambo Ya Walawi")
    val acts = Triple("new_testament", "acts", "Matendo Ya Mitume")
    val actsOfPeter = Triple("apocrypha", "acts_of_peter", "Matendo ya Petro")
    val books = listOf(leviticus, acts, actsOfPeter)
    val aliases = StorySearch.buildExactBookAliasLookup(
      """[
        {"canon":"Mambo Ya Walawi","aliases":["Mambo Ya Walawi","Walawi"]},
        {"canon":"Matendo Ya Mitume","aliases":["Matendo Ya Mitume","Matendo","Mitume"]},
        {"canon":"Matendo ya Petro","aliases":["Matendo ya Petro"]}
      ]""",
      books
    )
    val indexedTitles = mapOf(
      ("mamboyawalawi" to null) to listOf(leviticus),
      ("matendoyamitume" to null) to listOf(acts),
      ("matendoyapetro" to null) to listOf(actsOfPeter)
    )

    for ((query, expectedBook) in listOf(
      "Walawi 1:1" to leviticus,
      "Matendo 1:1" to acts,
      "Mitume 1:1" to acts
    )) {
      val ref = assertNotNull(StorySearch.parseExplicitRef(query))
      assertEquals(1, ref.chapter, query)
      assertEquals(1, ref.verse, query)
      assertEquals(
        listOf(expectedBook),
        StorySearch.resolveExplicitBookCandidates(ref.family, ref.number, aliases, indexedTitles),
        query
      )
    }
  }
}
