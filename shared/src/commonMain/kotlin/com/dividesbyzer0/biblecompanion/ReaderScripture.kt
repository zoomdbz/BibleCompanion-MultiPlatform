package com.dividesbyzer0.biblecompanion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal data class ReaderVersePresentation(
  val bulletIndex: Int,
  val anchor: VerseAnchor?,
  val label: String?,
  val text: String
)

internal data class ReaderScriptureBlock(
  val heading: String?,
  val verses: List<ReaderVersePresentation>
)

internal fun readerNativeReferenceTail(anchor: VerseAnchor): String =
  if (anchor.verseEnd == anchor.verseStart) {
    "${anchor.chapter}:${anchor.verseStart}"
  } else {
    "${anchor.chapter}:${anchor.verseStart}-${anchor.verseEnd}"
  }

internal fun readerTextWithoutTrailingAnchor(
  raw: String,
  parsed: ParsedTrailingVerseAnchor?
): String {
  if (parsed == null) return raw
  val before = raw.substring(0, parsed.rawStart).trimEnd()
  val trailingPunctuation = parsed.trailingPunctuation.trim()
  if (trailingPunctuation.isEmpty()) return before

  val punctuationProbe = stripScriptureInlineTags(before)
    .trimEnd()
    .trimEnd('"', '\'', '\u2019', '\u201D', '\u00BB')
  val alreadyTerminated = punctuationProbe.lastOrNull()?.let { char ->
    !char.isLetterOrDigit() && char !in listOf(')', ']', '}')
  } ?: false
  return if (alreadyTerminated) before else before + trailingPunctuation
}

internal fun readerVersePresentations(story: Story): List<ReaderVersePresentation> =
  story.summaryBullets.mapIndexed { index, raw ->
    val parsed = parseTrailingVerseAnchor(raw, storyId = story.id)
    val anchor = parsed?.anchor
    val text = readerTextWithoutTrailingAnchor(raw, parsed)
    val label = anchor?.let {
      if (it.verseEnd == it.verseStart) it.verseStart.toString()
      else "${it.verseStart}-${it.verseEnd}"
    }
    ReaderVersePresentation(index, anchor, label, text)
  }

internal fun readerScriptureBlocks(story: Story, versePerLine: Boolean): List<ReaderScriptureBlock> {
  val headingsByVerse = story.headings.associate { it.beforeVerse to it.text }
  val verses = readerVersePresentations(story)
  if (versePerLine) {
    return verses.map { verse ->
      ReaderScriptureBlock(
        heading = verse.anchor?.verseStart?.let(headingsByVerse::get),
        verses = listOf(verse)
      )
    }
  }

  val blocks = mutableListOf<ReaderScriptureBlock>()
  var heading: String? = null
  val pending = mutableListOf<ReaderVersePresentation>()

  fun flush() {
    if (pending.isNotEmpty()) {
      blocks += ReaderScriptureBlock(heading, pending.toList())
      pending.clear()
      heading = null
    }
  }

  verses.forEach { verse ->
    val verseHeading = verse.anchor?.verseStart?.let(headingsByVerse::get)
    if (verseHeading != null) {
      flush()
      heading = verseHeading
    }
    pending += verse
    if ('\n' in verse.text || '\r' in verse.text) flush()
  }
  flush()
  return blocks
}

internal fun readerHighlightBgColor(colorKey: String?, isDark: Boolean): Color {
  val alpha = if (isDark) 0.30f else 0.18f
  return when (colorKey) {
    "yellow" -> (if (isDark) Color(0xFFFFD54F) else Color(0xFFFFEB3B)).copy(alpha = alpha)
    "green" -> (if (isDark) Color(0xFF81C784) else Color(0xFF66BB6A)).copy(alpha = alpha)
    "blue" -> (if (isDark) Color(0xFF64B5F6) else Color(0xFF42A5F5)).copy(alpha = alpha)
    "pink" -> (if (isDark) Color(0xFFF06292) else Color(0xFFEC407A)).copy(alpha = alpha)
    else -> Color.Transparent
  }
}

@Composable
private fun ReaderScriptureTextBlock(
  block: ReaderScriptureBlock,
  col: String,
  prefs: PrefsState,
  defaultBook: String?,
  selectedBullets: Set<Int>,
  savedVerseColors: Map<Int, String?>,
  goldFadeBulletIdxs: Set<Int>,
  onToggleBullet: ((Int) -> Unit)?,
  onCopyBullet: ((Int) -> Unit)?,
  verseRootY: MutableMap<Int, Float>,
  isDark: Boolean,
  scriptureStyle: androidx.compose.ui.text.TextStyle,
  editionId: String,
  goldAlpha: () -> Float
) {
  val blockGoldAlpha = if (block.verses.any { it.bulletIndex in goldFadeBulletIdxs }) {
    goldAlpha()
  } else {
    0f
  }
  val verseStyles = buildMap {
    block.verses.forEach { verse ->
      val background = when {
        verse.bulletIndex in selectedBullets -> MaterialTheme.colorScheme.primaryContainer
        verse.bulletIndex in goldFadeBulletIdxs && blockGoldAlpha > 0f ->
          Color(0xFFFFB300).copy(alpha = blockGoldAlpha * 0.38f)
        else -> readerHighlightBgColor(savedVerseColors[verse.bulletIndex], isDark)
      }
      if (background != Color.Transparent) {
        put(verse.bulletIndex, SpanStyle(background = background))
      }
    }
  }
  val markedText = remember(block) {
    readerPresentationText(
      verses = block.verses.map { verse ->
        ReaderTextVerse(verse.bulletIndex, verse.label, verse.text)
      },
      separator = " "
    )
  }
  val nativeReferenceTails = remember(block) {
    block.verses.mapNotNull { verse ->
      verse.anchor?.let { anchor ->
        verse.bulletIndex to readerNativeReferenceTail(anchor)
      }
    }.toMap()
  }

  ScriptureRefs.ClickableRefsText(
    text = markedText,
    collection = col,
    prefs = prefs,
    defaultBook = defaultBook,
    allowRelativeInParensOnly = true,
    referenceEditionId = editionId,
    textStyle = scriptureStyle,
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
    readerOptions = ReaderTextOptions(
      verseStyles = verseStyles,
      nativeReferenceTails = nativeReferenceTails,
      onVerseClick = onToggleBullet,
      onVerseLongClick = onCopyBullet,
      onVersePositioned = { index, rootY -> verseRootY[index] = rootY },
      useVerseDialogAccessibility = block.verses.size > 1
    )
  )
}

/**
 * Shared Scripture renderer for reader chapters and appearance previews.
 * Verse markers are presentation-only; the source bullets remain unchanged.
 */
@Composable
fun ReaderScripture(
  story: Story,
  col: String,
  prefs: PrefsState,
  defaultBook: String?,
  selectedBullets: Set<Int>,
  savedVerseColors: Map<Int, String?>,
  goldFadeBulletIdxs: Set<Int>,
  onToggleBullet: ((Int) -> Unit)?,
  onCopyBullet: ((Int) -> Unit)?,
  listState: LazyListState?,
  viewportTopY: Float,
  viewportHeightPx: Int
) {
  val blocks = remember(story.summaryBullets, story.headings, prefs.versePerLine) {
    readerScriptureBlocks(story, prefs.versePerLine)
  }
  val verseRootY = remember(story.id) { mutableStateMapOf<Int, Float>() }
  val goldAlpha = remember(story.id) { Animatable(0f) }
  val firstGoldIndex = goldFadeBulletIdxs.minOrNull()
  val viewportTopState = rememberUpdatedState(viewportTopY)
  val viewportHeightState = rememberUpdatedState(viewportHeightPx)

  LaunchedEffect(goldFadeBulletIdxs, listState) {
    if (goldFadeBulletIdxs.isEmpty()) {
      goldAlpha.snapTo(0f)
      return@LaunchedEffect
    }

    if (firstGoldIndex != null && listState != null) {
      // Match the reader's expand animation before measuring. A verse can be
      // composed early while its root position is still moving.
      delay(160)
      var targetRootY: Float? = verseRootY[firstGoldIndex]
      var attempts = 0
      while ((targetRootY == null || viewportHeightState.value <= 0) && attempts < 12) {
        delay(16)
        targetRootY = verseRootY[firstGoldIndex]
        attempts++
      }
      targetRootY?.takeIf { viewportHeightState.value > 0 }?.let { rootY ->
        val verseInViewport = rootY - viewportTopState.value
        val targetInViewport = viewportHeightState.value * 0.22f
        val delta = verseInViewport - targetInViewport
        if (kotlin.math.abs(delta) > 2f) runCatching { listState.scrollBy(delta) }
      }
    }

    delay(80)
    goldAlpha.snapTo(1f)
    goldAlpha.animateTo(
      targetValue = 0f,
      animationSpec = tween(durationMillis = 2200, easing = LinearEasing)
    )
  }

  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.25f
  val scriptureStyle = scriptureTextStyle(prefs)
  val editionId = BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(if (prefs.versePerLine) 4.dp else 10.dp)
  ) {
    if (story.superscription.isNotBlank()) {
      ScriptureRefs.ClickableRefsText(
        text = story.superscription,
        collection = col,
        prefs = prefs,
        defaultBook = defaultBook,
        allowRelativeInParensOnly = true,
        referenceEditionId = editionId,
        textStyle = scriptureStyle.copy(fontStyle = FontStyle.Italic),
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
      )
    }

    blocks.forEach { block ->
      block.heading?.let { heading ->
        ScriptureRefs.ClickableRefsText(
          text = heading,
          collection = col,
          prefs = prefs,
          defaultBook = defaultBook,
          allowRelativeInParensOnly = true,
          referenceEditionId = editionId,
          textStyle = displayTitleTextStyle(
            prefs,
            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
          ),
          modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
        )
      }

      ReaderScriptureTextBlock(
        block = block,
        col = col,
        prefs = prefs,
        defaultBook = defaultBook,
        selectedBullets = selectedBullets,
        savedVerseColors = savedVerseColors,
        goldFadeBulletIdxs = goldFadeBulletIdxs,
        onToggleBullet = onToggleBullet,
        onCopyBullet = onCopyBullet,
        verseRootY = verseRootY,
        isDark = isDark,
        scriptureStyle = scriptureStyle,
        editionId = editionId,
        goldAlpha = { goldAlpha.value }
      )
    }
  }
}
