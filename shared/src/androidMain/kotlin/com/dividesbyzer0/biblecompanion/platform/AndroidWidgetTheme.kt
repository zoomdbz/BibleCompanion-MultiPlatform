package com.dividesbyzer0.biblecompanion.platform

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.dividesbyzer0.biblecompanion.PrefsState
import com.dividesbyzer0.biblecompanion.ThemePreset
import com.dividesbyzer0.biblecompanion.colorSchemeFor
import com.dividesbyzer0.biblecompanion.comfortableDynamicColorScheme

/** Light and dark color schemes for the home-screen widgets. */
class WidgetColorSchemes(val light: ColorScheme, val dark: ColorScheme)

/**
 * Resolves the color scheme the app itself shows for [prefs]: the chosen preset,
 * the custom accents, or wallpaper-derived dynamic color on Android 12+. When the
 * app forces light or dark, both schemes are that one so a widget does not follow
 * the device night mode on its own. A null [prefs] yields the default Parchment
 * theme following the device night mode.
 */
fun widgetColorSchemes(context: Context, prefs: PrefsState?): WidgetColorSchemes {
  val preset = ThemePreset.fromKey(prefs?.themePreset)

  fun scheme(dark: Boolean): ColorScheme {
    if (preset == ThemePreset.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val wallpaper = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      return comfortableDynamicColorScheme(wallpaper, dark)
    }
    if (prefs == null) return colorSchemeFor(preset, dark)
    return colorSchemeFor(
      preset,
      dark,
      prefs.customThemeHue,
      prefs.customThemeSaturation,
      prefs.customThemeLightness,
      prefs.customThemeSecondary,
      prefs.customThemeTertiary
    )
  }

  return when (prefs?.theme?.lowercase()) {
    "dark" -> scheme(true).let { WidgetColorSchemes(it, it) }
    "light" -> scheme(false).let { WidgetColorSchemes(it, it) }
    else -> WidgetColorSchemes(scheme(false), scheme(true))
  }
}
