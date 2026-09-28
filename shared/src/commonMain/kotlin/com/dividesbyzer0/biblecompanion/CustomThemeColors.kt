package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class CustomThemeRole { Primary, Secondary, Tertiary }

@Serializable
data class CustomThemeColor(val hue: Float, val saturation: Float, val lightness: Float) {
  fun normalized(): CustomThemeColor = CustomThemeColor(
    hue = if (hue.isFinite()) ((hue % 360f) + 360f) % 360f else 210f,
    saturation = if (saturation.isFinite()) saturation.coerceIn(0f, 1f) else 1f,
    lightness = if (lightness.isFinite()) lightness.coerceIn(0f, 1f) else .5f
  )
}

internal fun readCustomThemeColor(raw: String?): CustomThemeColor? =
  raw?.let { runCatching { Json.decodeFromString<CustomThemeColor>(it).normalized() }.getOrNull() }

internal fun PrefsState.primaryThemeColor() =
  CustomThemeColor(customThemeHue, customThemeSaturation, customThemeLightness).normalized()

/** Related tones by default; explicit choices never inherit another role's hue. */
internal fun matchingThemeAccent(primary: CustomThemeColor, role: CustomThemeRole): CustomThemeColor {
  val color = primary.normalized()
  return when (role) {
    CustomThemeRole.Primary -> color
    CustomThemeRole.Secondary -> color.copy(saturation = color.saturation * .55f, lightness = .46f)
    CustomThemeRole.Tertiary -> color.copy(saturation = color.saturation * .30f, lightness = .40f)
  }
}

internal fun PrefsState.themeColor(role: CustomThemeRole): CustomThemeColor = when (role) {
  CustomThemeRole.Primary -> primaryThemeColor()
  CustomThemeRole.Secondary -> customThemeSecondary?.normalized()
    ?: matchingThemeAccent(primaryThemeColor(), role)
  CustomThemeRole.Tertiary -> customThemeTertiary?.normalized()
    ?: matchingThemeAccent(primaryThemeColor(), role)
}
