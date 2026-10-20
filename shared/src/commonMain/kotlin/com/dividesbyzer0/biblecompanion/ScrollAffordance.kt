package com.dividesbyzer0.biblecompanion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A vertically scrolling column whose edges fade while more content lies beyond
 * the viewport, with a chevron under the bottom fade. A menu that fits on a
 * tall phone but overflows on a short one otherwise gives no hint that it
 * scrolls, so its last entry is never found.
 */
@Composable
fun FadingScrollColumn(
  modifier: Modifier = Modifier,
  scrollState: ScrollState = rememberScrollState(),
  contentPadding: PaddingValues = PaddingValues(16.dp),
  verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  fadeHeight: Dp = 64.dp,
  content: @Composable ColumnScope.() -> Unit
) {
  val surface = MaterialTheme.colorScheme.background
  val bottomAlpha by animateFloatAsState(
    targetValue = if (scrollState.canScrollForward) 1f else 0f,
    label = "scrollFadeBottom"
  )
  val topAlpha by animateFloatAsState(
    targetValue = if (scrollState.canScrollBackward) 1f else 0f,
    label = "scrollFadeTop"
  )
  Box(modifier) {
    Column(
      Modifier.fillMaxSize().verticalScroll(scrollState).padding(contentPadding),
      verticalArrangement = verticalArrangement,
      horizontalAlignment = horizontalAlignment,
      content = content
    )
    // Plain boxes carry no pointer input, so scroll gestures reach the column.
    Box(
      Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .height(fadeHeight / 2)
        .alpha(topAlpha)
        .background(Brush.verticalGradient(listOf(surface, Color.Transparent)))
    )
    Box(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(fadeHeight)
        .alpha(bottomAlpha)
        .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
      contentAlignment = Alignment.BottomCenter
    ) {
      Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        modifier = Modifier.padding(bottom = 4.dp).size(22.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
