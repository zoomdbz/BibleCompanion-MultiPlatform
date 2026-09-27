package com.dividesbyzer0.biblecompanion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private data class CuratedThemeColor(
  val hue: Float,
  val name: StringResource
)

private val curatedThemeColors = listOf(
  CuratedThemeColor(350f, Res.string.ui_color_crimson),
  CuratedThemeColor(15f, Res.string.ui_color_coral),
  CuratedThemeColor(38f, Res.string.ui_color_amber),
  CuratedThemeColor(52f, Res.string.ui_color_gold),
  CuratedThemeColor(80f, Res.string.ui_color_olive),
  CuratedThemeColor(125f, Res.string.ui_color_green),
  CuratedThemeColor(155f, Res.string.ui_color_emerald),
  CuratedThemeColor(180f, Res.string.ui_color_teal),
  CuratedThemeColor(210f, Res.string.ui_color_sky_blue),
  CuratedThemeColor(225f, Res.string.ui_color_blue),
  CuratedThemeColor(250f, Res.string.ui_color_indigo),
  CuratedThemeColor(275f, Res.string.ui_color_violet),
  CuratedThemeColor(310f, Res.string.ui_color_magenta),
  CuratedThemeColor(335f, Res.string.ui_color_rose)
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CustomThemePicker(
  hue: Float,
  dark: Boolean,
  onHueSelected: (Float) -> Unit
) {
  var showFineTuning by rememberSaveable { mutableStateOf(false) }
  var sliderHue by remember(hue) { mutableFloatStateOf(hue.coerceIn(0f, 360f)) }
  val selectedDescription = stringResource(Res.string.ui_color_selected)
  val fineTuneDescription = stringResource(Res.string.ui_fine_tune_color)
  val previewScheme = colorSchemeFor(ThemePreset.Custom, dark, hue)
  val selectedOption = curatedThemeColors.minBy { hueDistance(hue, it.hue) }

  Column(
    modifier = Modifier.padding(top = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(stringResource(Res.string.ui_choose_custom_color), style = MaterialTheme.typography.titleSmall)
    Text(
      stringResource(Res.string.ui_choose_custom_color_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    FlowRow(
      modifier = Modifier.fillMaxWidth().selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      curatedThemeColors.forEach { option ->
        val name = stringResource(option.name)
        val selected = option == selectedOption
        val scheme = colorSchemeFor(ThemePreset.Custom, dark, option.hue)
        Surface(
          modifier = Modifier
            .width(78.dp)
            .heightIn(min = 82.dp)
            .semantics {
              contentDescription = name
              this.selected = selected
              if (selected) stateDescription = selectedDescription
            }
            .selectable(
              selected = selected,
              role = Role.RadioButton,
              onClick = { onHueSelected(option.hue) }
            ),
          shape = RoundedCornerShape(14.dp),
          color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
          contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
          border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) scheme.primary else scheme.outlineVariant
          )
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Box(
              modifier = Modifier.size(36.dp).clip(CircleShape).background(scheme.primary),
              contentAlignment = Alignment.Center
            ) {
              if (selected) {
                Icon(
                  Icons.Filled.Check,
                  contentDescription = null,
                  tint = scheme.onPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            Text(
              text = name,
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }

    ThemeColorPreview(
      hue = hue,
      scheme = previewScheme
    )

    OutlinedButton(
      onClick = { showFineTuning = !showFineTuning },
      modifier = Modifier.fillMaxWidth()
    ) {
      Icon(
        if (showFineTuning) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
        contentDescription = null
      )
      Spacer(Modifier.width(8.dp))
      Text(
        if (showFineTuning) {
          stringResource(Res.string.ui_hide_fine_color_control)
        } else {
          stringResource(Res.string.ui_fine_tune_color)
        }
      )
    }

    AnimatedVisibility(visible = showFineTuning) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
          Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
              Brush.horizontalGradient(
                (0..360 step 15).map { sampleHue ->
                  colorSchemeFor(ThemePreset.Custom, dark, sampleHue.toFloat()).primary
                }
              )
            )
        )
        Slider(
          value = sliderHue,
          onValueChange = { value ->
            sliderHue = value
            onHueSelected(value)
          },
          valueRange = 0f..360f,
          modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = fineTuneDescription }
        )
      }
    }
  }
}

@Composable
private fun ThemeColorPreview(hue: Float, scheme: androidx.compose.material3.ColorScheme) {
  val nearestName = curatedThemeColors.minBy { hueDistance(hue, it.hue) }.name
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = scheme.surfaceContainer,
    contentColor = scheme.onSurface,
    border = BorderStroke(1.dp, scheme.outlineVariant)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(stringResource(Res.string.ui_theme_preview), style = MaterialTheme.typography.titleSmall)
      Text(
        stringResource(nearestName),
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PreviewColor(scheme.primaryContainer, scheme.onPrimaryContainer)
        PreviewColor(scheme.secondaryContainer, scheme.onSecondaryContainer)
        PreviewColor(scheme.tertiaryContainer, scheme.onTertiaryContainer)
      }
    }
  }
}

@Composable
private fun PreviewColor(container: Color, content: Color) {
  Surface(
    modifier = Modifier.height(30.dp).width(54.dp),
    shape = RoundedCornerShape(9.dp),
    color = container,
    contentColor = content
  ) {
    Box(contentAlignment = Alignment.Center) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(content))
    }
  }
}

private fun hueDistance(first: Float, second: Float): Float {
  val direct = abs(first - second) % 360f
  return minOf(direct, 360f - direct)
}
