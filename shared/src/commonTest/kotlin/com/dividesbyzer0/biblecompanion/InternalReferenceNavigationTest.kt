package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class InternalReferenceNavigationTest {

  @Test
  fun singleChapterReferenceUsesChapterOneAndTargetsTheVerse() {
    val target = ScriptureRefs.parseInternalRefTail("jude", "3")

    assertEquals("1", target.chapter)
    assertEquals(3, target.verse)
    assertEquals(null, target.verseEnd)
  }

  @Test
  fun ordinaryColonlessReferenceStillMeansChapter() {
    val target = ScriptureRefs.parseInternalRefTail("genesis", "3")

    assertEquals("3", target.chapter)
    assertEquals(null, target.verse)
  }
}
