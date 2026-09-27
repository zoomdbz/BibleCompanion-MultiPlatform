package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class StorySearchMemoryTest {
  @Test
  fun scoringWordsPreserveRepeatedTermsAndPhraseOrder() {
    assertEquals(
      listOf("faith", "faith", "hope", "faith"),
      storySearchVerseWords("faith faith hope faith")
    )
  }

  @Test
  fun postingsDeduplicateOnlyWithinOneVerse() {
    assertEquals(
      listOf("faith", "hope"),
      storySearchPostingWords("faith faith hope faith hope")
    )
    // A second verse still contributes its own posting for each word.
    assertEquals(listOf("faith", "hope"), storySearchPostingWords("faith hope"))
  }

  @Test
  fun postingsKeepFirstOccurrenceOrderAndExistingLengthFilter() {
    assertEquals(
      listOf("am", "that"),
      storySearchPostingWords("i am am that i am")
    )
  }

  @Test
  fun cjkTokenBoundariesRemainSpaceDriven() {
    assertEquals(
      listOf("起初", "神", "創造", "天地"),
      storySearchVerseWords("起初 神 創造 天地")
    )
    assertEquals(listOf("天地創造"), storySearchVerseWords("天地創造"))
    assertEquals(
      listOf("起初", "創造", "天地"),
      storySearchPostingWords("起初 神 創造 天地 起初")
    )
  }

  @Test
  fun emptySegmentsRemainIgnoredForScoringParity() {
    assertEquals(
      listOf("many", "matches", "remain"),
      storySearchVerseWords("many  matches   remain")
    )
  }
}
