package com.dividesbyzer0.biblecompanion

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomThemeColorsTest {
  @Test
  fun independentRoleChoicesRetainExactRgbInBothModes() {
    for (dark in listOf(false, true)) {
      val scheme = colorSchemeFor(
        ThemePreset.Custom, dark, 0f, 1f, .5f,
        customSecondary = CustomThemeColor(120f, 1f, .5f),
        customTertiary = CustomThemeColor(240f, 1f, .5f)
      )
      assertEquals(Color(0xFFFF0000), scheme.primaryContainer)
      assertEquals(Color(0xFF00FF00), scheme.secondaryContainer)
      assertEquals(Color(0xFF0000FF), scheme.tertiaryContainer)
    }
  }

  @Test
  fun defaultsKeepPrimaryHueInsteadOfRotatingToYellowOrPink() {
    for (hue in listOf(0f, 240f)) {
      val prefs = PrefsState(customThemeHue = hue)
      assertEquals(hue, prefs.themeColor(CustomThemeRole.Secondary).hue)
      assertEquals(hue, prefs.themeColor(CustomThemeRole.Tertiary).hue)
      assertTrue(prefs.themeColor(CustomThemeRole.Tertiary).saturation < prefs.customThemeSaturation)
    }
  }

  @Test
  fun changingPrimaryDoesNotReplaceExplicitSupportingColors() {
    val second = CustomThemeColor(120f, 1f, .5f)
    val third = CustomThemeColor(240f, 1f, .5f)
    val prefs = PrefsState(customThemeSecondary = second, customThemeTertiary = third)
      .copy(customThemeHue = 30f)
    assertEquals(second, prefs.themeColor(CustomThemeRole.Secondary))
    assertEquals(third, prefs.themeColor(CustomThemeRole.Tertiary))
  }

  @Test
  fun colorPersistenceAndBackupRoundTripIndependentRoles() {
    val second = CustomThemeColor(120f, .8f, .3f)
    val third = CustomThemeColor(240f, 1f, .5f)
    assertEquals(second, readCustomThemeColor(Json.encodeToString(second)))
    val backup = AppBackup(timestamp = 1L, customThemeHue = 0f,
      customThemeSecondary = second, customThemeTertiary = third)
    val imported = Json.decodeFromString<AppBackup>(Json.encodeToString(backup))
    assertEquals(second, imported.customThemeSecondary)
    assertEquals(third, imported.customThemeTertiary)
    val legacy = Json.decodeFromString<AppBackup>("""{"timestamp":1,"customThemeHue":42.0}""")
    assertNull(legacy.customThemeSecondary)
    assertNull(legacy.customThemeTertiary)
  }

  @Test
  fun malformedStoredColorFallsBackAndNumericValuesNormalize() {
    assertNull(readCustomThemeColor(null))
    assertNull(readCustomThemeColor("broken"))
    assertEquals(CustomThemeColor(330f, 1f, 0f), CustomThemeColor(-30f, 2f, -1f).normalized())
    assertEquals(CustomThemeColor(210f, 1f, .5f),
      CustomThemeColor(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).normalized())
  }
}
