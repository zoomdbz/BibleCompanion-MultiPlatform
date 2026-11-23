package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.TtsPlatformEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderTtsTest {

  private class FakeDriver : TtsDriver<Unit> {
    val calls = mutableListOf<String>()
    val spokenRequests = mutableListOf<Pair<Long, List<String>>>()
    var pauseAccepted = true

    override fun speak(ctx: Unit, requestId: Long, segments: List<String>, languageTag: String) {
      calls += "speak"
      spokenRequests += requestId to segments
    }
    override fun pause(ctx: Unit): Boolean {
      calls += "pause"
      return pauseAccepted
    }
    override fun resume(ctx: Unit) { calls += "resume" }
    override fun stop(ctx: Unit) { calls += "stop" }
  }

  private val chapter = TtsOwner("reader:1", TtsUnit.Chapter("gen-1"))
  private val segments = listOf(
    TtsSegment(-1, "Genesis Chapter 1."),
    TtsSegment(0, "In the beginning God created the heaven and the earth."),
    TtsSegment(2, "And God said, Let there be light: and there was light.")
  )

  private fun session(driver: FakeDriver = FakeDriver()) = TtsSessionCore(driver) to driver

  @Test
  fun pauseThenPlayResumesInsteadOfRestarting() {
    val (tts, driver) = session()
    tts.play(Unit, chapter, segments, "en")
    tts.pause(Unit)
    assertEquals(TtsStatus.Paused, tts.playback?.status)
    tts.resume(Unit)
    assertEquals(TtsStatus.Playing, tts.playback?.status)
    assertEquals(listOf("speak", "pause", "resume"), driver.calls)
    assertEquals(1, driver.spokenRequests.size)
  }

  @Test
  fun stopThenPlayStartsFromTheTop() {
    val (tts, driver) = session()
    tts.play(Unit, chapter, segments, "en")
    tts.onPlatformEvent(TtsPlatformEvent.SegmentStarted(driver.spokenRequests[0].first, 2))
    tts.stop(Unit)
    assertNull(tts.playback)
    tts.play(Unit, chapter, segments, "en")
    assertEquals(listOf("speak", "stop", "speak"), driver.calls)
    assertEquals(segments.map { it.text }, driver.spokenRequests[1].second)
    assertEquals(-1, tts.playback?.activeSegmentId)
    assertFalse(tts.playback?.started ?: true)
  }

  @Test
  fun segmentStartMapsQueueIndexToBulletIndex() {
    val (tts, driver) = session()
    tts.play(Unit, chapter, segments, "en")
    val id = driver.spokenRequests.single().first
    tts.onPlatformEvent(TtsPlatformEvent.SegmentStarted(id, 2))
    assertEquals(2, tts.playback?.activeSegmentId)
    tts.onPlatformEvent(TtsPlatformEvent.SegmentStarted(id, 0))
    assertEquals(-1, tts.playback?.activeSegmentId)
  }

  @Test
  fun blankSegmentsAreNotSpokenAndDoNotShiftIds() {
    val (tts, driver) = session()
    tts.play(Unit, chapter, listOf(TtsSegment(0, "First."), TtsSegment(1, "  "), TtsSegment(2, "Third.")), "en")
    assertEquals(listOf("First.", "Third."), driver.spokenRequests.single().second)
    tts.onPlatformEvent(TtsPlatformEvent.SegmentStarted(driver.spokenRequests.single().first, 1))
    assertEquals(2, tts.playback?.activeSegmentId)
  }

  @Test
  fun finishedRunsCompletionOnce() {
    val (tts, driver) = session()
    var completed = 0
    tts.play(Unit, chapter, segments, "en", onComplete = { completed++ })
    val id = driver.spokenRequests.single().first
    tts.onPlatformEvent(TtsPlatformEvent.Finished(id))
    tts.onPlatformEvent(TtsPlatformEvent.Finished(id))
    assertEquals(1, completed)
    assertNull(tts.playback)
  }

  @Test
  fun finishedWhilePausePendingStillFinishes() {
    val (tts, driver) = session()
    var completed = false
    tts.play(Unit, chapter, segments, "en", onComplete = { completed = true })
    tts.pause(Unit)
    tts.onPlatformEvent(TtsPlatformEvent.Finished(driver.spokenRequests.single().first))
    assertTrue(completed)
    assertNull(tts.playback)
  }

  @Test
  fun staleEventsFromAReplacedRequestAreIgnored() {
    val (tts, driver) = session()
    var oldCompleted = false
    tts.play(Unit, chapter, segments, "en", onComplete = { oldCompleted = true })
    val oldId = driver.spokenRequests[0].first
    tts.play(Unit, TtsOwner("reader:1", TtsUnit.Chapter("gen-2")), segments, "en")
    tts.onPlatformEvent(TtsPlatformEvent.Finished(oldId))
    tts.onPlatformEvent(TtsPlatformEvent.SegmentStarted(oldId, 2))
    assertFalse(oldCompleted)
    assertEquals(TtsUnit.Chapter("gen-2"), tts.playback?.owner?.unit)
    assertEquals(-1, tts.playback?.activeSegmentId)
  }

  @Test
  fun failureNeverRunsCompletion() {
    val (tts, driver) = session()
    var completed = false
    var failed = false
    tts.play(Unit, chapter, segments, "en", onComplete = { completed = true }, onFailure = { failed = true })
    tts.onPlatformEvent(TtsPlatformEvent.Failed(driver.spokenRequests.single().first))
    assertFalse(completed)
    assertTrue(failed)
    assertNull(tts.playback)
  }

  @Test
  fun startTimeoutFailsOnlyARequestThatNeverStarted() {
    val (tts, driver) = session()
    var completed = false
    tts.play(Unit, chapter, segments, "en", onComplete = { completed = true })
    val id = driver.spokenRequests.single().first
    tts.onStartTimeout(Unit, id + 1)
    assertEquals(id, tts.playback?.requestId)
    tts.onStartTimeout(Unit, id)
    assertNull(tts.playback)
    assertFalse(completed)
    assertEquals(listOf("speak", "stop"), driver.calls)

    tts.play(Unit, chapter, segments, "en")
    val started = driver.spokenRequests.last().first
    tts.onPlatformEvent(TtsPlatformEvent.SegmentStarted(started, 0))
    tts.onStartTimeout(Unit, started)
    assertEquals(started, tts.playback?.requestId)
  }

  @Test
  fun rejectedPauseKeepsPlaying() {
    val (tts, driver) = session()
    driver.pauseAccepted = false
    tts.play(Unit, chapter, segments, "en")
    tts.pause(Unit)
    assertEquals(TtsStatus.Playing, tts.playback?.status)
  }

  @Test
  fun teardownStopsOnlyItsOwnPlayback() {
    val (tts, driver) = session()
    tts.play(Unit, TtsOwner("home", TtsUnit.Votd), listOf(TtsSegment(0, "Verse.")), "en")
    tts.stopIfOwnedBy(Unit, "reader:1")
    assertEquals("home", tts.playback?.owner?.scope)
    tts.stopIfOwnedBy(Unit, "home")
    assertNull(tts.playback)
    assertEquals(listOf("speak", "stop"), driver.calls)
  }

  @Test
  fun startingAnotherUnitReplacesTheCurrentOne() {
    val (tts, driver) = session()
    var chapterCompleted = false
    tts.play(Unit, chapter, segments, "en", onComplete = { chapterCompleted = true })
    val chapterId = driver.spokenRequests[0].first
    tts.play(Unit, TtsOwner("reader:1", TtsUnit.Intro), listOf(TtsSegment(0, "Intro.")), "en")
    val introId = driver.spokenRequests[1].first
    tts.onPlatformEvent(TtsPlatformEvent.Finished(introId))
    tts.onPlatformEvent(TtsPlatformEvent.Finished(chapterId))
    assertFalse(chapterCompleted)
    assertNull(tts.playback)
  }

  @Test
  fun emptyPlayCompletesWithoutSpeaking() {
    val (tts, driver) = session()
    var completed = false
    tts.play(Unit, chapter, listOf(TtsSegment(0, " ")), "en", onComplete = { completed = true })
    assertTrue(completed)
    assertTrue(driver.spokenRequests.isEmpty())
    assertNull(tts.playback)
  }

  @Test
  fun playHighlightsTheFirstSegmentBeforeTheEngineStarts() {
    val (tts, _) = session()
    tts.play(Unit, chapter, segments.drop(2), "en")
    assertEquals(2, tts.playback?.activeSegmentId)
    assertFalse(tts.playback?.started ?: true)
  }

  // Chapter 0: title, verses 0-2. Chapter 1: title, verses 0-1.
  private val ids = mapOf(0 to listOf(-1, 0, 1, 2), 1 to listOf(-1, 0, 1))

  private fun skip(
    from: TtsSpot,
    forward: Boolean,
    introIds: List<Int> = emptyList(),
    hasPrevious: Boolean = true,
    hasNext: Boolean = true,
    chapters: Map<Int, List<Int>> = ids
  ) = ttsSkipTarget(from, forward, chapters.size, { chapters[it].orEmpty() }, introIds, hasPrevious, hasNext)

  @Test
  fun skipForwardStepsVerseByVerseThenIntoTheNextChapterTitle() {
    assertEquals(TtsSkip.To(TtsSpot.Chapter(0, 0)), skip(TtsSpot.Chapter(0, -1), forward = true))
    assertEquals(TtsSkip.To(TtsSpot.Chapter(0, 1)), skip(TtsSpot.Chapter(0, 0), forward = true))
    assertEquals(TtsSkip.To(TtsSpot.Chapter(1, -1)), skip(TtsSpot.Chapter(0, 2), forward = true))
  }

  @Test
  fun skipForwardFromTheLastVerseOfABookGoesToTheNextBookButNeverWraps() {
    assertEquals(TtsSkip.NextBookStart, skip(TtsSpot.Chapter(1, 1), forward = true))
    // End of Revelation: no next book, so nothing happens.
    assertEquals(TtsSkip.None, skip(TtsSpot.Chapter(1, 1), forward = true, hasNext = false))
  }

  @Test
  fun skipBackGoesToThePreviousVerseThenThePreviousChaptersLastVerse() {
    assertEquals(TtsSkip.To(TtsSpot.Chapter(1, 0)), skip(TtsSpot.Chapter(1, 1), forward = false))
    assertEquals(TtsSkip.To(TtsSpot.Chapter(0, 2)), skip(TtsSpot.Chapter(1, 0), forward = false))
    // From a chapter title, back is also the previous chapter's last verse.
    assertEquals(TtsSkip.To(TtsSpot.Chapter(0, 2)), skip(TtsSpot.Chapter(1, -1), forward = false))
  }

  @Test
  fun skipBackFromABooksFirstVerseGoesToThePreviousBookButNotPastGenesis() {
    assertEquals(TtsSkip.PreviousBookEnd, skip(TtsSpot.Chapter(0, 0), forward = false))
    assertEquals(TtsSkip.PreviousBookEnd, skip(TtsSpot.Chapter(0, -1), forward = false))
    // Genesis 1:1: no previous book, so nothing happens.
    assertEquals(TtsSkip.None, skip(TtsSpot.Chapter(0, 0), forward = false, hasPrevious = false))
  }

  @Test
  fun skipStepsOverVersesThatAreNotSpoken() {
    val sparse = mapOf(0 to listOf(-1, 0, 2))
    assertEquals(TtsSkip.To(TtsSpot.Chapter(0, 2)), skip(TtsSpot.Chapter(0, 0), forward = true, chapters = sparse))
    assertEquals(TtsSkip.To(TtsSpot.Chapter(0, 0)), skip(TtsSpot.Chapter(0, 2), forward = false, chapters = sparse))
  }

  @Test
  fun skipIncludesTheIntroWhenIntrosAreRead() {
    val intro = listOf(0, 2)
    assertEquals(TtsSkip.To(TtsSpot.Intro(2)), skip(TtsSpot.Chapter(0, 0), forward = false, introIds = intro))
    assertEquals(TtsSkip.To(TtsSpot.Intro(0)), skip(TtsSpot.Intro(2), forward = false, introIds = intro))
    assertEquals(TtsSkip.PreviousBookEnd, skip(TtsSpot.Intro(0), forward = false, introIds = intro))
    assertEquals(TtsSkip.None, skip(TtsSpot.Intro(0), forward = false, introIds = intro, hasPrevious = false))
    assertEquals(TtsSkip.To(TtsSpot.Intro(2)), skip(TtsSpot.Intro(0), forward = true, introIds = intro))
    assertEquals(TtsSkip.To(TtsSpot.Chapter(0, -1)), skip(TtsSpot.Intro(2), forward = true, introIds = intro))
  }

  @Test
  fun introSegmentsSplitByParagraphAndKeepParagraphIndices() {
    val segments = ttsIntroSegments("First paragraph.\n\n  \n\nThird paragraph.", "traditional", "en", false, "old_testament")
    assertEquals(listOf(0, 2), segments.map { it.id })
    assertEquals(listOf("First paragraph.", "Third paragraph."), segments.map { it.text })
  }

  @Test
  fun speechPiecesSplitAtSentencesWithOffsets() {
    val text = "And God said, Let there be light. And there was light! Was it good? Yes."
    val pieces = ttsSpeechPieces(text, maxLen = 4000)
    assertEquals(
      listOf("And God said, Let there be light.", "And there was light!", "Was it good?", "Yes."),
      pieces.map { it.second }
    )
    pieces.forEach { (offset, piece) -> assertEquals(piece, text.substring(offset, offset + piece.length)) }
  }

  @Test
  fun speechPiecesResumeFromAWordOffset() {
    val text = "In the beginning God created the heaven and the earth."
    val from = text.indexOf("created")
    val pieces = ttsSpeechPieces(text, from = from, maxLen = 4000)
    assertEquals(listOf(from to "created the heaven and the earth."), pieces)
  }

  @Test
  fun speechPiecesKeepClosingQuotesAndSkipDecimals() {
    val text = "He said, \"Go.\" Then 3.5 cubits remained."
    val pieces = ttsSpeechPieces(text, maxLen = 4000).map { it.second }
    assertEquals(listOf("He said, \"Go.\"", "Then 3.5 cubits remained."), pieces)
  }

  @Test
  fun speechPiecesSplitUnspacedScriptAfterFullStops() {
    val text = "起初神创造天地。地是空虚混沌。"
    assertEquals(listOf("起初神创造天地。", "地是空虚混沌。"), ttsSpeechPieces(text, maxLen = 4000).map { it.second })
  }

  @Test
  fun speechPiecesHardSplitOverTheEngineLimit() {
    val words = List(40) { "word$it" }.joinToString(" ")
    val pieces = ttsSpeechPieces(words, maxLen = 50)
    assertTrue(pieces.all { it.second.length <= 50 })
    assertEquals(words.split(' '), pieces.flatMap { it.second.split(' ') })
    pieces.forEach { (offset, piece) -> assertEquals(piece, words.substring(offset, offset + piece.length)) }
  }

  @Test
  fun chapterSegmentsKeyEveryVerseByBulletIndex() {
    val story = Story(
      id = "genesis-1",
      title = "Genesis 1",
      refs = listOf("Genesis 1"),
      summaryBullets = listOf(
        "In the beginning God created the heaven and the earth. (1:1)",
        "",
        "And the earth was without form, and void. (1:2)"
      )
    )
    val segments = ttsChapterSegments(story, "en", "traditional", false, "old_testament")
    assertEquals(listOf(-1, 0, 2), segments.map { it.id })
    assertEquals("Genesis Chapter 1.", segments[0].text)
    assertEquals(
      ttsCleanBullet(story.summaryBullets[0], "traditional", "en", false, "old_testament"),
      segments[1].text
    )
    assertFalse(segments[1].text.contains("1:1"))
  }
}
