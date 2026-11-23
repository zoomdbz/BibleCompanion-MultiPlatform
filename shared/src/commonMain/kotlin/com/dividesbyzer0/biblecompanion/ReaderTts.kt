package com.dividesbyzer0.biblecompanion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.TtsPlatformEvent
import com.dividesbyzer0.biblecompanion.platform.platformTtsPause
import com.dividesbyzer0.biblecompanion.platform.platformTtsResume
import com.dividesbyzer0.biblecompanion.platform.platformTtsSetEventSink
import com.dividesbyzer0.biblecompanion.platform.platformTtsSpeakQueue
import com.dividesbyzer0.biblecompanion.platform.platformTtsStop
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/** One spoken unit. [id] is the verse's bullet index, or -1 for a chapter title. */
internal class TtsSegment(val id: Int, val text: String)

/** What is being read, so each button can tell whether playback is its own. */
internal sealed interface TtsUnit {
  data class Chapter(val storyId: String) : TtsUnit
  data class Section(val storyId: String, val kind: String) : TtsUnit
  data object Intro : TtsUnit
  data object Votd : TtsUnit
}

/** [scope] names the screen instance that started playback. */
internal data class TtsOwner(val scope: String, val unit: TtsUnit)

internal enum class TtsStatus { Playing, Paused }

internal data class TtsPlayback(
  val requestId: Long,
  val owner: TtsOwner,
  val segmentIds: List<Int>,
  val status: TtsStatus = TtsStatus.Playing,
  /**
   * Segment id being read. Set to the first segment as soon as playback is
   * requested, so a skip moves the highlight at once; the engine confirms it.
   */
  val activeSegmentId: Int? = null,
  /** True once the engine reports speech for this request (watchdog input). */
  val started: Boolean = false
)

internal interface TtsDriver<C> {
  fun speak(ctx: C, requestId: Long, segments: List<String>, languageTag: String)
  fun pause(ctx: C): Boolean
  fun resume(ctx: C)
  fun stop(ctx: C)
}

/**
 * The single owner of text-to-speech playback. Screens ask it to play, pause,
 * resume, or stop; platforms report progress back through [onPlatformEvent].
 *
 * Every request gets a fresh id, and events carrying any other id are dropped
 * here, after main-thread dispatch. A callback still in flight from a stopped
 * or replaced queue therefore cannot finish, fail, or highlight the next one.
 * Completion comes only from the platform's Finished event, never from polling
 * isSpeaking, so a resume can no longer look like the end of a chapter.
 */
internal class TtsSessionCore<C>(private val driver: TtsDriver<C>) {
  var playback: TtsPlayback? by mutableStateOf(null)
    private set

  private var nextRequestId = 1L
  private var onComplete: (() -> Unit)? = null
  private var onFailure: (() -> Unit)? = null

  /**
   * Replaces anything playing with [segments], read from the top. [onComplete]
   * runs only when the last segment finishes naturally; [onFailure] runs when
   * the engine cannot speak. Neither runs after [stop] or a replacing [play].
   */
  fun play(
    ctx: C,
    owner: TtsOwner,
    segments: List<TtsSegment>,
    languageTag: String,
    onComplete: () -> Unit = {},
    onFailure: () -> Unit = {}
  ) {
    val spoken = segments.filter { it.text.isNotBlank() }
    if (spoken.isEmpty()) {
      stop(ctx)
      onComplete()
      return
    }
    clearCallbacks()
    val requestId = nextRequestId++
    playback = TtsPlayback(
      requestId, owner, spoken.map { it.id }, activeSegmentId = spoken.first().id
    )
    this.onComplete = onComplete
    this.onFailure = onFailure
    driver.speak(ctx, requestId, spoken.map { it.text }, languageTag)
  }

  fun pause(ctx: C) {
    val current = playback ?: return
    if (current.status != TtsStatus.Playing) return
    if (driver.pause(ctx)) playback = current.copy(status = TtsStatus.Paused)
  }

  fun resume(ctx: C) {
    val current = playback ?: return
    if (current.status != TtsStatus.Paused) return
    playback = current.copy(status = TtsStatus.Playing)
    driver.resume(ctx)
  }

  /** Ends playback and forgets its position; the next play starts at the top. */
  fun stop(ctx: C) {
    if (playback == null) return
    playback = null
    clearCallbacks()
    driver.stop(ctx)
  }

  /** Screen teardown: stop only playback this screen instance started. */
  fun stopIfOwnedBy(ctx: C, scope: String) {
    if (playback?.owner?.scope == scope) stop(ctx)
  }

  fun onPlatformEvent(event: TtsPlatformEvent) {
    val current = playback ?: return
    if (event.requestId != current.requestId) return
    when (event) {
      is TtsPlatformEvent.SegmentStarted -> {
        val id = current.segmentIds.getOrNull(event.segmentIndex) ?: return
        if (id != current.activeSegmentId || !current.started) {
          playback = current.copy(activeSegmentId = id, started = true)
        }
      }
      // A natural finish of the current request wins even while a pause is
      // pending: iOS pauses at the next word boundary, and the final utterance
      // can end first. Staying "paused" would strand controls with nothing left.
      is TtsPlatformEvent.Finished -> finish(onComplete)
      is TtsPlatformEvent.Failed -> finish(onFailure)
    }
  }

  /** Watchdog: [requestId] produced no speech in time. Fails; never advances. */
  fun onStartTimeout(ctx: C, requestId: Long) {
    val current = playback ?: return
    if (current.requestId != requestId || current.started || current.status != TtsStatus.Playing) return
    val callback = onFailure
    playback = null
    clearCallbacks()
    driver.stop(ctx)
    callback?.invoke()
  }

  private fun finish(callback: (() -> Unit)?) {
    playback = null
    clearCallbacks()
    callback?.invoke()
  }

  private fun clearCallbacks() {
    onComplete = null
    onFailure = null
  }
}

private object PlatformTtsDriver : TtsDriver<PlatformContext> {
  override fun speak(ctx: PlatformContext, requestId: Long, segments: List<String>, languageTag: String) =
    platformTtsSpeakQueue(ctx, requestId, segments, languageTag)
  override fun pause(ctx: PlatformContext): Boolean = platformTtsPause(ctx)
  override fun resume(ctx: PlatformContext) = platformTtsResume(ctx)
  override fun stop(ctx: PlatformContext) = platformTtsStop(ctx)
}

/** One synthesizer per process, so one playback session above every screen. */
internal val AppTts: TtsSessionCore<PlatformContext> by lazy {
  TtsSessionCore(PlatformTtsDriver).also { session ->
    platformTtsSetEventSink { event -> session.onPlatformEvent(event) }
  }
}

private var ttsScopeCounter = 0

/** A fresh owner scope for one screen instance, e.g. "reader:3". */
internal fun newTtsScope(prefix: String): String {
  ttsScopeCounter += 1
  return "$prefix:$ttsScopeCounter"
}

private val TTS_START_TIMEOUT = 10.seconds

/**
 * Fails a request that never starts speaking (engine bind failure, missing
 * voice data). Lives at the app root so it outlasts the screen that asked.
 */
@Composable
internal fun TtsStartWatchdog() {
  val ctx = LocalPlatformContext.current
  val current = AppTts.playback
  val waitingId = current?.takeIf { !it.started && it.status == TtsStatus.Playing }?.requestId
  LaunchedEffect(waitingId) {
    val id = waitingId ?: return@LaunchedEffect
    delay(TTS_START_TIMEOUT)
    AppTts.onStartTimeout(ctx, id)
  }
}

/**
 * Play/Pause plus a square Stop to its left. Stop shows only while this unit is
 * playing or paused. Play/Pause continues where it left off; only Stop resets.
 */
@Composable
internal fun TtsControls(
  playing: Boolean,
  paused: Boolean,
  onPlayPause: () -> Unit,
  onStop: () -> Unit,
  idleTint: Color,
  activeTint: Color,
  iconModifier: Modifier = Modifier
) {
  val active = playing || paused
  if (active) {
    IconButton(onClick = onStop) {
      Icon(
        Icons.Filled.Stop,
        contentDescription = stringResource(Res.string.cd_tts_stop),
        modifier = iconModifier,
        tint = activeTint
      )
    }
  }
  IconButton(onClick = onPlayPause) {
    Icon(
      if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
      contentDescription = stringResource(
        when {
          playing -> Res.string.cd_tts_pause
          paused -> Res.string.cd_tts_resume
          else -> Res.string.cd_tts_play
        }
      ),
      modifier = iconModifier,
      tint = if (active) activeTint else idleTint
    )
  }
}

private val ttsSentenceEnds = charArrayOf('.', '!', '?', '。', '！', '？', '\n')
private val ttsClosingMarks = charArrayOf('"', '\'', '”', '’', '»', ')', ']')

private fun isTtsUnspacedEnd(c: Char): Boolean =
  c == '。' || c == '！' || c == '？' || c == '\n'

/**
 * Splits [text], starting at [from], into sentence-sized pieces no longer than
 * [maxLen], each paired with its start offset inside [text]. Android queues each
 * piece as its own utterance, so a resume without word positions repeats at most
 * one sentence, and a word offset reported inside a piece maps back to [text].
 */
internal fun ttsSpeechPieces(text: String, from: Int = 0, maxLen: Int): List<Pair<Int, String>> {
  val limit = maxLen.coerceAtLeast(1)
  val out = ArrayList<Pair<Int, String>>()

  fun addPiece(rangeStart: Int, rangeEnd: Int) {
    var a = rangeStart
    var b = rangeEnd
    while (a < b && text[a].isWhitespace()) a++
    while (b > a && text[b - 1].isWhitespace()) b--
    while (b - a > limit) {
      val windowEnd = a + limit
      val space = text.lastIndexOf(' ', windowEnd - 1).takeIf { it > a + limit / 2 }
      val cut = space ?: windowEnd
      var pieceEnd = cut
      while (pieceEnd > a && text[pieceEnd - 1].isWhitespace()) pieceEnd--
      if (pieceEnd > a) out += a to text.substring(a, pieceEnd)
      a = cut
      while (a < b && text[a].isWhitespace()) a++
    }
    if (a < b) out += a to text.substring(a, b)
  }

  var start = from.coerceIn(0, text.length)
  var i = start
  while (i < text.length) {
    val c = text[i]
    if (c in ttsSentenceEnds) {
      var end = i + 1
      while (end < text.length && text[end] in ttsClosingMarks) end++
      if (end >= text.length || isTtsUnspacedEnd(c) || text[end].isWhitespace()) {
        addPiece(start, end)
        start = end
        i = end
        continue
      }
    }
    i++
  }
  addPiece(start, text.length)
  return out
}

/** After the reader drags, follow-along waits this long before scrolling again. */
internal const val TTS_FOLLOW_HOLD_MS = 4_000L

/** Steady gold for the verse being read; the same hue as the navigation fade. */
internal val TtsReadingGold = Color(0xFFFFB300)

// Player bar geometry, taken from Material 3's floating toolbar tokens
// (androidx.compose.material3 1.4.0 FloatingToolbarTokens): 64dp container,
// 16dp from the screen edge, full (pill) corners, 8dp leading and trailing
// space, 4dp between items. Capped in width so it stays a bar on tablets.
internal val TTS_BAR_HEIGHT = 64.dp
internal val TTS_BAR_SCREEN_OFFSET = 16.dp
private val TTS_BAR_MAX_WIDTH = 560.dp

/** What the reader's player bar shows; kept through its exit animation. */
internal data class TtsBarState(
  val title: String,
  val subtitle: String?,
  val playing: Boolean,
  val canPrevious: Boolean,
  val canNext: Boolean
)

/**
 * Floating now-playing bar for the reader. Text on the leading side (tap it to
 * jump back to what is being read), then the transport group: previous verse,
 * square Stop, Play/Pause as the one filled button, next verse. Previous and
 * next sit on the outside with Play/Pause between them, the arrangement media
 * players share. Only shown while something is playing or paused, so Play here
 * always means Resume.
 *
 * Media controls are not mirrored for right-to-left languages (Material
 * bidirectionality guidance): they describe playback direction, not reading
 * direction. The text side still follows the layout direction.
 */
@Composable
internal fun TtsPlayerBar(
  state: TtsBarState,
  onPlayPause: () -> Unit,
  onStop: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .widthIn(max = TTS_BAR_MAX_WIDTH)
      .fillMaxWidth()
      .height(TTS_BAR_HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 3.dp,
    shadowElevation = 3.dp
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Column(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(CircleShape)
          .clickable(onClick = onOpen)
          .padding(start = 16.dp, end = 4.dp),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          state.title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        state.subtitle?.let { subtitle ->
          Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          IconButton(onClick = onPrevious, enabled = state.canPrevious) {
            Icon(
              Icons.Filled.SkipPrevious,
              contentDescription = stringResource(Res.string.cd_tts_previous_verse)
            )
          }
          IconButton(onClick = onStop) {
            Icon(
              Icons.Filled.Stop,
              contentDescription = stringResource(Res.string.cd_tts_stop),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          FilledIconButton(onClick = onPlayPause) {
            Icon(
              if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = stringResource(
                if (state.playing) Res.string.cd_tts_pause else Res.string.cd_tts_resume
              )
            )
          }
          IconButton(onClick = onNext, enabled = state.canNext) {
            Icon(
              Icons.Filled.SkipNext,
              contentDescription = stringResource(Res.string.cd_tts_next_verse)
            )
          }
        }
      }
    }
  }
}

/** Where reading is inside one book: an intro paragraph or a chapter segment. */
internal sealed interface TtsSpot {
  data class Intro(val paragraph: Int) : TtsSpot
  /** [segment] is a bullet index, or -1 for the chapter title. */
  data class Chapter(val storyIndex: Int, val segment: Int) : TtsSpot
}

internal sealed interface TtsSkip {
  data class To(val spot: TtsSpot) : TtsSkip
  /** Last verse of the previous book's last chapter. */
  data object PreviousBookEnd : TtsSkip
  /** Start of the next book: its intro when intros are read, else chapter one. */
  data object NextBookStart : TtsSkip
  /** Genesis 1:1 going back, the last verse of Revelation going forward. */
  data object None : TtsSkip
}

/**
 * One verse back or forward, crossing chapters and books.
 *
 * Forward: the next verse; past the last verse, the next chapter from its
 * title; past the last chapter, the next book. From the intro, the next
 * paragraph, then chapter one.
 *
 * Back: the previous verse; before the first verse (or from the title), the
 * last verse of the previous chapter; before chapter one, the intro's last
 * paragraph when intros are read, otherwise the previous book's last verse.
 *
 * [chapterIds] lists the spoken segment ids of a chapter (title -1 first).
 * [introIds] lists spoken intro paragraph ids, empty when intros are skipped.
 * Book neighbors never wrap, so Genesis has no previous book and Revelation no
 * next one; the caller passes that in.
 */
internal fun ttsSkipTarget(
  from: TtsSpot,
  forward: Boolean,
  storyCount: Int,
  chapterIds: (Int) -> List<Int>,
  introIds: List<Int>,
  hasPreviousBook: Boolean,
  hasNextBook: Boolean
): TtsSkip {
  fun chapterStart(storyIndex: Int) = TtsSpot.Chapter(storyIndex, chapterIds(storyIndex).firstOrNull() ?: -1)
  fun chapterEnd(storyIndex: Int): TtsSpot.Chapter {
    val ids = chapterIds(storyIndex)
    return TtsSpot.Chapter(storyIndex, ids.lastOrNull { it >= 0 } ?: ids.lastOrNull() ?: -1)
  }
  val nextBook = if (hasNextBook) TtsSkip.NextBookStart else TtsSkip.None
  val previousBook = if (hasPreviousBook) TtsSkip.PreviousBookEnd else TtsSkip.None

  return when (from) {
    is TtsSpot.Intro -> if (forward) {
      introIds.firstOrNull { it > from.paragraph }?.let { TtsSkip.To(TtsSpot.Intro(it)) }
        ?: if (storyCount > 0) TtsSkip.To(chapterStart(0)) else nextBook
    } else {
      introIds.lastOrNull { it < from.paragraph }?.let { TtsSkip.To(TtsSpot.Intro(it)) }
        ?: previousBook
    }
    is TtsSpot.Chapter -> if (forward) {
      chapterIds(from.storyIndex).firstOrNull { it > from.segment }
        ?.let { TtsSkip.To(TtsSpot.Chapter(from.storyIndex, it)) }
        ?: if (from.storyIndex + 1 < storyCount) TtsSkip.To(chapterStart(from.storyIndex + 1)) else nextBook
    } else {
      // Going back skips the title: before verse one is the previous chapter.
      chapterIds(from.storyIndex).lastOrNull { it >= 0 && it < from.segment }
        ?.let { TtsSkip.To(TtsSpot.Chapter(from.storyIndex, it)) }
        ?: when {
          from.storyIndex > 0 -> TtsSkip.To(chapterEnd(from.storyIndex - 1))
          introIds.isNotEmpty() -> TtsSkip.To(TtsSpot.Intro(introIds.last()))
          else -> previousBook
        }
    }
  }
}
