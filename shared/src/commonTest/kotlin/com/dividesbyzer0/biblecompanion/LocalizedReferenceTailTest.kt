package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizedReferenceTailTest {

  @Test
  fun normalizesChineseChapterAndVerseGrammar() {
    assertEquals("53", ScriptureRefs.normalizeCjkTail("第53章"))
    assertEquals("53:1", ScriptureRefs.normalizeCjkTail("第53章第1节"))
    assertEquals("28:12-19", ScriptureRefs.normalizeCjkTail("第28章12-19节"))
  }

  @Test
  fun normalizesJapaneseAndKoreanChapterAndVerseGrammar() {
    assertEquals("3:16", ScriptureRefs.normalizeCjkTail("3章16節"))
    assertEquals("3:16", ScriptureRefs.normalizeCjkTail("3장16절"))
    assertEquals("3", ScriptureRefs.normalizeCjkTail("제3장"))
  }
}
