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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dividesbyzer0.biblecompanion.platform.ColorHsl
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private data class CuratedThemeColor(
  val hue: Float,
  val name: StringResource,
  val saturation: Float = 1f,
  val lightness: Float = 0.5f
)

private val curatedThemeColors = listOf(
  CuratedThemeColor(0f, Res.string.ui_color_red),
  CuratedThemeColor(30f, Res.string.ui_color_orange),
  CuratedThemeColor(60f, Res.string.ui_color_yellow),
  CuratedThemeColor(90f, Res.string.ui_color_lime),
  CuratedThemeColor(120f, Res.string.ui_color_green),
  CuratedThemeColor(150f, Res.string.ui_color_emerald),
  CuratedThemeColor(180f, Res.string.ui_color_cyan),
  CuratedThemeColor(210f, Res.string.ui_color_sky_blue),
  CuratedThemeColor(240f, Res.string.ui_color_blue),
  CuratedThemeColor(270f, Res.string.ui_color_violet),
  CuratedThemeColor(300f, Res.string.ui_color_magenta),
  CuratedThemeColor(330f, Res.string.ui_color_rose)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomThemePalettePicker(
  prefs: PrefsState,
  dark: Boolean,
  onColorSelected: (CustomThemeRole, CustomThemeColor) -> Unit,
  onMatchAccents: () -> Unit
) {
  var roleName by rememberSaveable { mutableStateOf(CustomThemeRole.Primary.name) }
  val role = CustomThemeRole.valueOf(roleName)
  val color = prefs.themeColor(role)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(Res.string.ui_custom_palette_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      CustomThemeRole.entries.forEach { option ->
        val optionColor = prefs.themeColor(option)
        FilterChip(
          selected = role == option,
          onClick = { roleName = option.name },
          label = {
            Text(stringResource(when (option) {
              CustomThemeRole.Primary -> Res.string.ui_primary_color
              CustomThemeRole.Secondary -> Res.string.ui_secondary_color
              CustomThemeRole.Tertiary -> Res.string.ui_tertiary_color
            }))
          },
          leadingIcon = {
            Box(Modifier.size(18.dp).clip(CircleShape).background(
              customThemeSeedColor(optionColor.hue, optionColor.saturation, optionColor.lightness)
            ))
          }
        )
      }
    }
    key(role) {
      CustomThemePicker(
        hue = color.hue,
        saturation = color.saturation,
        lightness = color.lightness,
        dark = dark,
        onColorSelected = { h, s, l -> onColorSelected(role, CustomThemeColor(h, s, l)) },
        palettePreview = { draft ->
          val primary = if (role == CustomThemeRole.Primary) draft else prefs.primaryThemeColor()
          colorSchemeFor(
            ThemePreset.Custom, dark, primary.hue, primary.saturation, primary.lightness,
            customSecondary = if (role == CustomThemeRole.Secondary) draft else prefs.customThemeSecondary,
            customTertiary = if (role == CustomThemeRole.Tertiary) draft else prefs.customThemeTertiary
          )
        }
      )
    }
    OutlinedButton(onClick = onMatchAccents, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(Res.string.ui_match_accent_colors))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CustomThemePicker(
  hue: Float,
  saturation: Float,
  lightness: Float,
  dark: Boolean,
  onColorSelected: (Float, Float, Float) -> Unit,
  palettePreview: ((CustomThemeColor) -> ColorScheme)? = null
) {
  var showFineTuning by rememberSaveable { mutableStateOf(false) }
  var draftHue by remember { mutableFloatStateOf(hue.coerceIn(0f, 360f)) }
  var draftSaturation by remember { mutableFloatStateOf(saturation.coerceIn(0f, 1f)) }
  var draftLightness by remember { mutableFloatStateOf(lightness.coerceIn(0f, 1f)) }
  var sliderActive by remember { mutableStateOf(false) }
  var exactColorText by remember {
    mutableStateOf(formatExactColor(draftHue, draftSaturation, draftLightness))
  }
  var exactColorError by remember { mutableStateOf(false) }

  val selectedDescription = stringResource(Res.string.ui_color_selected)
  val saturationDescription = stringResource(Res.string.ui_saturation)
  val hueDescription = stringResource(Res.string.ui_hue)
  val lightnessDescription = stringResource(Res.string.ui_lightness)
  val previewScheme = remember(dark, draftHue, draftSaturation, draftLightness, palettePreview) {
    palettePreview?.invoke(CustomThemeColor(draftHue, draftSaturation, draftLightness)) ?: colorSchemeFor(
      ThemePreset.Custom,
      dark,
      draftHue,
      draftSaturation,
      draftLightness
    )
  }
  val seedColor = remember(draftHue, draftSaturation, draftLightness) {
    customThemeSeedColor(draftHue, draftSaturation, draftLightness)
  }

  LaunchedEffect(hue, saturation, lightness) {
    if (!sliderActive) {
      draftHue = hue.coerceIn(0f, 360f)
      draftSaturation = saturation.coerceIn(0f, 1f)
      draftLightness = lightness.coerceIn(0f, 1f)
      exactColorText = formatExactColor(draftHue, draftSaturation, draftLightness)
      exactColorError = false
    }
  }

  fun persistDraft() {
    sliderActive = false
    exactColorText = formatExactColor(draftHue, draftSaturation, draftLightness)
    exactColorError = false
    onColorSelected(draftHue, draftSaturation, draftLightness)
  }

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
    Text(
      stringResource(Res.string.ui_custom_color_readability_note),
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
        val selected = colorsMatch(
          draftHue,
          draftSaturation,
          draftLightness,
          option.hue,
          option.saturation,
          option.lightness
        )
        val optionScheme = remember(dark, option) {
          colorSchemeFor(
            ThemePreset.Custom,
            dark,
            option.hue,
            option.saturation,
            option.lightness
          )
        }
        val rawColor = remember(option) {
          customThemeSeedColor(option.hue, option.saturation, option.lightness)
        }
        Surface(
          modifier = Modifier
            .widthIn(min = 88.dp, max = 140.dp)
            .heightIn(min = 88.dp)
            .semantics {
              contentDescription = name
              this.selected = selected
              if (selected) stateDescription = selectedDescription
            }
            .selectable(
              selected = selected,
              role = Role.RadioButton,
              onClick = {
                sliderActive = false
                draftHue = option.hue
                draftSaturation = option.saturation
                draftLightness = option.lightness
                exactColorText = formatExactColor(draftHue, draftSaturation, draftLightness)
                exactColorError = false
                onColorSelected(draftHue, draftSaturation, draftLightness)
              }
            ),
          shape = RoundedCornerShape(14.dp),
          color = if (selected) optionScheme.primaryContainer else optionScheme.surfaceContainerLow,
          contentColor = if (selected) optionScheme.onPrimaryContainer else optionScheme.onSurface,
          border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) optionScheme.primary else optionScheme.outline
          )
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
          ) {
            Box(
              modifier = Modifier.size(36.dp).clip(CircleShape).background(rawColor),
              contentAlignment = Alignment.Center
            ) {
              if (selected) {
                Icon(
                  Icons.Filled.Check,
                  contentDescription = null,
                  tint = rawColor.contrastingContentColor(),
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            Text(
              text = name,
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }

    ColorSliderLabel(
      label = stringResource(Res.string.ui_saturation),
      value = "${(draftSaturation * 100f).roundToInt()}%"
    )
    ColorGradient(
      colors = listOf(
        customThemeSeedColor(draftHue, 0f, draftLightness),
        customThemeSeedColor(draftHue, 1f, draftLightness)
      )
    )
    Slider(
      value = draftSaturation,
      onValueChange = { value ->
        sliderActive = true
        draftSaturation = value
      },
      onValueChangeFinished = ::persistDraft,
      valueRange = 0f..1f,
      modifier = Modifier.fillMaxWidth().semantics { contentDescription = saturationDescription }
    )

    ThemeColorPreview(
      hue = draftHue,
      saturation = draftSaturation,
      lightness = draftLightness,
      seedColor = seedColor,
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
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorSliderLabel(
          label = stringResource(Res.string.ui_hue),
          value = draftHue.roundToInt().toString()
        )
        ColorGradient(
          colors = (0..360 step 15).map { sampleHue ->
            customThemeSeedColor(sampleHue.toFloat(), draftSaturation, draftLightness)
          }
        )
        Slider(
          value = draftHue,
          onValueChange = { value ->
            sliderActive = true
            draftHue = value
          },
          onValueChangeFinished = ::persistDraft,
          valueRange = 0f..360f,
          modifier = Modifier.fillMaxWidth().semantics { contentDescription = hueDescription }
        )

        ColorSliderLabel(
          label = stringResource(Res.string.ui_lightness),
          value = "${(draftLightness * 100f).roundToInt()}%"
        )
        ColorGradient(
          colors = (0..10).map { step ->
            customThemeSeedColor(draftHue, draftSaturation, step / 10f)
          }
        )
        Slider(
          value = draftLightness,
          onValueChange = { value ->
            sliderActive = true
            draftLightness = value
          },
          onValueChangeFinished = ::persistDraft,
          valueRange = 0f..1f,
          modifier = Modifier.fillMaxWidth().semantics { contentDescription = lightnessDescription }
        )

        OutlinedTextField(
          value = exactColorText,
          onValueChange = { value ->
            exactColorText = value
            exactColorError = false
          },
          modifier = Modifier.fillMaxWidth(),
          label = { Text(stringResource(Res.string.ui_exact_color)) },
          placeholder = { Text(stringResource(Res.string.ui_exact_color_hint)) },
          supportingText = {
            Text(
              if (exactColorError) {
                stringResource(Res.string.ui_invalid_exact_color)
              } else {
                stringResource(Res.string.ui_exact_color_hint)
              }
            )
          },
          isError = exactColorError,
          singleLine = true
        )
        Button(
          onClick = {
            val parsed = parseExactColor(exactColorText)
            if (parsed == null) {
              exactColorError = true
            } else {
              sliderActive = false
              draftHue = parsed.first
              draftSaturation = parsed.second
              draftLightness = parsed.third
              persistDraft()
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(Res.string.ui_apply_color))
        }
      }
    }
  }
}

@Composable
private fun ColorSliderLabel(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(value, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun ColorGradient(colors: List<Color>) {
  Box(
    Modifier
      .fillMaxWidth()
      .height(20.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(Brush.horizontalGradient(colors))
  )
}

@Composable
private fun ThemeColorPreview(
  hue: Float,
  saturation: Float,
  lightness: Float,
  seedColor: Color,
  scheme: androidx.compose.material3.ColorScheme
) {
  val selectedName = curatedThemeColors
    .firstOrNull {
      colorsMatch(hue, saturation, lightness, it.hue, it.saturation, it.lightness)
    }
    ?.name
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
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(seedColor))
        Column {
          Text(stringResource(Res.string.ui_theme_preview), style = MaterialTheme.typography.titleSmall)
          Text(
            selectedName?.let { stringResource(it) }
              ?: stringResource(Res.string.ui_color_fine_tuned),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
          )
        }
      }
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

private fun colorsMatch(
  firstHue: Float,
  firstSaturation: Float,
  firstLightness: Float,
  secondHue: Float,
  secondSaturation: Float,
  secondLightness: Float
): Boolean =
  hueDistance(firstHue, secondHue) < 0.5f &&
    abs(firstSaturation - secondSaturation) < 0.005f &&
    abs(firstLightness - secondLightness) < 0.005f

private fun hueDistance(first: Float, second: Float): Float {
  val direct = abs(first - second) % 360f
  return minOf(direct, 360f - direct)
}

internal fun formatExactColor(hue: Float, saturation: Float, lightness: Float): String {
  val normalizedHue = ((hue % 360f) + 360f) % 360f
  val argb = ColorHsl.hslToColor(floatArrayOf(normalizedHue, saturation, lightness))
  val rgb = argb and 0xFFFFFF
  return "#${rgb.toUInt().toString(16).uppercase().padStart(6, '0')}"
}

internal fun parseExactColor(input: String): Triple<Float, Float, Float>? {
  val value = input.trim()
  val rgb = when {
    value.matches(Regex("^#?[0-9a-fA-F]{6}$")) -> value.removePrefix("#").toIntOrNull(16)
    else -> {
      val parts = value.split(',').map { it.trim().toIntOrNull() }
      if (parts.size != 3 || parts.any { it == null || it !in 0..255 }) return null
      (parts[0]!! shl 16) or (parts[1]!! shl 8) or parts[2]!!
    }
  } ?: return null

  val hsl = FloatArray(3)
  ColorHsl.colorToHSL((0xFF shl 24) or rgb, hsl)
  return Triple(hsl[0], hsl[1], hsl[2])
}

private fun Color.contrastingContentColor(): Color =
  if (luminance() > 0.179f) Color.Black else Color.White
