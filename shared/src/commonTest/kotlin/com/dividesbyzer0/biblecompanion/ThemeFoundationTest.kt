package com.dividesbyzer0.biblecompanion

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.math.roundToInt

class ThemeFoundationTest {
    @Test
    fun staticPresetsRetainLeadingColorsAndRelateTertiary() {
        val expectedLight = mapOf(
            ThemePreset.Parchment to (Color(0xFF7D4A3E) to Color(0xFF76584A)),
            ThemePreset.Sage to (Color(0xFF4C6B4D) to Color(0xFF54634E)),
            ThemePreset.Indigo to (Color(0xFF3A5BA0) to Color(0xFF575E71)),
            ThemePreset.Ink to (Color(0xFFAF3434) to Color(0xFF775653))
        )
        val expectedDark = mapOf(
            ThemePreset.Parchment to (Color(0xFFF6B8A6) to Color(0xFFE8BEA9)),
            ThemePreset.Sage to (Color(0xFFB4D2B1) to Color(0xFFBACCB4)),
            ThemePreset.Indigo to (Color(0xFFB3C5FF) to Color(0xFFBFC6DC)),
            ThemePreset.Ink to (Color(0xFFFFB4AA) to Color(0xFFE7BDB9))
        )

        expectedLight.forEach { (preset, expected) ->
            assertSemanticColors(colorSchemeFor(preset, dark = false), expected)
        }
        expectedDark.forEach { (preset, expected) ->
            assertSemanticColors(colorSchemeFor(preset, dark = true), expected)
        }
    }

    @Test
    fun everyPresetHasOrderedSurfaceRamps() {
        ThemePreset.entries.forEach { preset ->
            val light = colorSchemeFor(preset, dark = false, customHue = 212f)
            assertEquals(light.background, light.surface, "$preset light background")
            assertNonIncreasing(
                "$preset light",
                listOf(
                light.surfaceContainerLowest,
                light.surfaceBright,
                light.surface,
                light.surfaceContainerLow,
                light.surfaceContainer,
                light.surfaceContainerHigh,
                light.surfaceContainerHighest,
                light.surfaceDim)
            )

            val dark = colorSchemeFor(preset, dark = true, customHue = 212f)
            assertEquals(dark.background, dark.surface, "$preset dark background")
            assertNonDecreasing(
                "$preset dark",
                listOf(
                dark.surfaceContainerLowest,
                dark.surfaceDim,
                dark.surface,
                dark.surfaceContainerLow,
                dark.surfaceContainer,
                dark.surfaceContainerHigh,
                dark.surfaceContainerHighest,
                dark.surfaceBright)
            )
        }
    }

    @Test
    fun customSurfaceRampTracksSelectedHue() {
        val warm = colorSchemeFor(ThemePreset.Custom, dark = false, customHue = 30f)
        val cool = colorSchemeFor(ThemePreset.Custom, dark = false, customHue = 210f)

        assertTrue(warm.surfaceContainer != cool.surfaceContainer)
        assertTrue(warm.surfaceContainerHighest != cool.surfaceContainerHighest)
    }

    @Test
    fun customSeedUsesExactHslRgbAndNormalizesHue() {
        assertEquals(Color(0xFFFF0000), customThemeSeedColor(0f, 1f, .5f))
        assertEquals(Color(0xFF00FF00), customThemeSeedColor(120f, 1f, .5f))
        assertEquals(Color(0xFF0000FF), customThemeSeedColor(240f, 1f, .5f))
        assertEquals(
            customThemeSeedColor(0f, 1f, .5f),
            customThemeSeedColor(360f, 1f, .5f)
        )
        assertEquals(Color(0xFF996633), customThemeSeedColor(30f, .5f, .4f))

        val gray = customThemeSeedColor(217f, 0f, .37f)
        assertEquals(gray.red, gray.green)
        assertEquals(gray.green, gray.blue)
    }

    @Test
    fun customThemeKeepsExactSeedContainersAndReadablePrimaryRolesAtEndpoints() {
        val hues = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
        val saturations = listOf(0f, .25f, 1f)
        val lightnesses = listOf(0f, .5f, 1f)

        for (dark in listOf(false, true)) {
            for (hue in hues) for (saturation in saturations) for (lightness in lightnesses) {
                val scheme = colorSchemeFor(
                    ThemePreset.Custom,
                    dark,
                    customHue = hue,
                    customSaturation = saturation,
                    customLightness = lightness
                )
                val label = "custom h=$hue s=$saturation l=$lightness dark=$dark"
                assertEquals(customThemeSeedColor(hue, saturation, lightness), scheme.primaryContainer, label)
                assertContentRoleContrast(label, scheme)
                assertContrast("$label primary on surface", scheme.primary, scheme.surface, 4.5f)
            }
        }
    }

    @Test
    fun customThemeClampsNonFiniteAndOutOfRangeInputs() {
        val values = listOf(Float.NEGATIVE_INFINITY, -1f, 0f, .5f, 1f, 2f, Float.POSITIVE_INFINITY, Float.NaN)
        for (hue in values) for (saturation in values) for (lightness in values) {
            val seed = customThemeSeedColor(hue, saturation, lightness)
            val scheme = colorSchemeFor(
                ThemePreset.Custom,
                dark = false,
                customHue = hue,
                customSaturation = saturation,
                customLightness = lightness
            )
            assertTrue(seed.red.isFinite() && seed.green.isFinite() && seed.blue.isFinite())
            assertTrue(scheme.primary.red.isFinite() && scheme.primary.green.isFinite() && scheme.primary.blue.isFinite())
        }
    }

    @Test
    fun exactColorParserAcceptsHexAndRgbAndRoundTripsRenderedRgb() {
        val cases = listOf(
            "#FF0000" to "#FF0000",
            "00ff00" to "#00FF00",
            "  #00FFFF" to "#00FFFF",
            "  0, 0, 255  " to "#0000FF",
            "153, 102, 51" to "#996633"
        )

        cases.forEach { (input, expected) ->
            val parsed = assertNotNull(parseExactColor(input), input)
            assertEquals(expected, formatExactColor(parsed.first, parsed.second, parsed.third), input)
            assertEquals(
                expected,
                colorToHex(customThemeSeedColor(parsed.first, parsed.second, parsed.third)),
                "$input rendered seed"
            )
            listOf(false, true).forEach { dark ->
                val scheme = colorSchemeFor(
                    ThemePreset.Custom,
                    dark,
                    parsed.first,
                    parsed.second,
                    parsed.third
                )
                assertEquals(expected, colorToHex(scheme.primaryContainer), "$input $dark primary container")
            }
        }
    }

    @Test
    fun exactColorParserRejectsMalformedAndOutOfRangeRgb() {
        listOf("", "#12345", "#GG0000", "rgb(255,0,0)", "256,0,0", "-1,0,0", "1,2", "1,2,3,4")
            .forEach { input -> assertEquals(null, parseExactColor(input), input) }
    }

    @Test
    fun exactColorParserHandlesNeutralBlackWhiteAndGray() {
        listOf("#000000", "#FFFFFF", "#808080").forEach { expected ->
            val parsed = assertNotNull(parseExactColor(expected), expected)
            assertEquals(expected, formatExactColor(parsed.first, parsed.second, parsed.third))
            assertEquals(expected, colorToHex(customThemeSeedColor(parsed.first, parsed.second, parsed.third)))
        }
    }

    @Test
    fun legacyThemeBackupUsesHistoricalDefaultsAndMissingThemeDoesNothing() {
        val legacy = AppBackup(timestamp = 1L, customThemeHue = 42f)
        assertEquals(ImportedCustomTheme(42f, 1f, .5f), legacy.importedCustomThemeOrNull())

        val complete = AppBackup(
            timestamp = 1L,
            customThemeHue = 42f,
            customThemeSaturation = .25f,
            customThemeLightness = .75f
        )
        assertEquals(ImportedCustomTheme(42f, .25f, .75f), complete.importedCustomThemeOrNull())
        assertEquals(null, AppBackup(timestamp = 1L).importedCustomThemeOrNull())
    }

    @Test
    fun semanticContentRolesMeetBodyTextContrast() {
        val schemes = buildList {
            ThemePreset.entries
                .filter { it != ThemePreset.Custom }
                .forEach { preset ->
                    add("$preset light" to colorSchemeFor(preset, dark = false))
                    add("$preset dark" to colorSchemeFor(preset, dark = true))
                }
            (0..360 step 15).forEach { hue ->
                add("Custom $hue light" to colorSchemeFor(ThemePreset.Custom, dark = false, customHue = hue.toFloat()))
                add("Custom $hue dark" to colorSchemeFor(ThemePreset.Custom, dark = true, customHue = hue.toFloat()))
            }
        }

        schemes.forEach { (label, scheme) -> assertContentRoleContrast(label, scheme) }
    }

    @Test
    fun darkSurfacesHaveVisibleHierarchyAndBoundaries() {
        ThemePreset.entries.forEach { preset ->
            val scheme = colorSchemeFor(preset, dark = true, customHue = 212f)
            assertContrast("$preset highest surface", scheme.surfaceContainerHighest, scheme.background, 1.5f)
            assertContrast("$preset outline variant", scheme.outlineVariant, scheme.background, 3.0f)
        }
    }

    @Test
    fun dynamicComfortPassPreservesLeadingWallpaperRolesAndRelatesTertiary() {
        val source = darkColorScheme()
        val result = comfortableDynamicColorScheme(source, dark = true)

        assertEquals(source.primary, result.primary)
        assertEquals(source.onPrimary, result.onPrimary)
        assertEquals(source.primaryContainer, result.primaryContainer)
        assertEquals(source.onPrimaryContainer, result.onPrimaryContainer)
        assertEquals(source.secondary, result.secondary)
        assertEquals(source.onSecondary, result.onSecondary)
        assertEquals(source.secondaryContainer, result.secondaryContainer)
        assertEquals(source.onSecondaryContainer, result.onSecondaryContainer)
        assertEquals(lerp(source.secondary, source.primary, .2f), result.tertiary)
        assertEquals(lerp(source.secondaryContainer, source.primaryContainer, .2f), result.tertiaryContainer)
        assertTrue(result.surface.luminance() > source.surface.luminance())
        assertTrue(result.surfaceContainerHighest.luminance() > result.surfaceContainer.luminance())
        assertNonDecreasing(
            "dynamic dark",
            listOf(
            result.surfaceContainerLowest,
            result.surfaceDim,
            result.surface,
            result.surfaceContainerLow,
            result.surfaceContainer,
            result.surfaceContainerHigh,
            result.surfaceContainerHighest,
            result.surfaceBright)
        )
        assertContentRoleContrast("dynamic dark", result)
        assertContrast("dynamic outline variant", result.outlineVariant, result.background, 3.0f)
    }

    @Test
    fun independentlyChosenAccentsRetainReadableRolesAtColorExtremes() {
        val choices = listOf(
            CustomThemeColor(0f, 1f, .5f), CustomThemeColor(60f, 1f, .5f),
            CustomThemeColor(120f, 1f, .5f), CustomThemeColor(240f, 1f, .5f),
            CustomThemeColor(300f, 1f, .5f), CustomThemeColor(0f, 0f, 0f),
            CustomThemeColor(0f, 0f, 1f), CustomThemeColor(210f, .25f, .5f)
        )
        for (dark in listOf(false, true)) for (second in choices) for (third in choices) {
            val scheme = colorSchemeFor(ThemePreset.Custom, dark,
                customSecondary = second, customTertiary = third)
            val label = "Custom independent $second $third dark=$dark"
            assertContentRoleContrast(label, scheme)
            assertContrast("$label secondary text", scheme.secondary, scheme.surface, 4.5f)
            assertContrast("$label tertiary text", scheme.tertiary, scheme.surface, 4.5f)
        }
    }

    private fun assertSemanticColors(scheme: ColorScheme, expected: Pair<Color, Color>) {
        assertEquals(expected.first, scheme.primary)
        assertEquals(expected.second, scheme.secondary)
        assertEquals(lerp(expected.second, expected.first, .2f), scheme.tertiary)
    }

    private fun assertContentRoleContrast(label: String, scheme: ColorScheme) {
        assertContrast("$label primary", scheme.primary, scheme.onPrimary, 4.5f)
        assertContrast("$label primary container", scheme.primaryContainer, scheme.onPrimaryContainer, 4.5f)
        assertContrast("$label secondary", scheme.secondary, scheme.onSecondary, 4.5f)
        assertContrast("$label secondary container", scheme.secondaryContainer, scheme.onSecondaryContainer, 4.5f)
        assertContrast("$label tertiary", scheme.tertiary, scheme.onTertiary, 4.5f)
        assertContrast("$label tertiary container", scheme.tertiaryContainer, scheme.onTertiaryContainer, 4.5f)
        assertContrast("$label background", scheme.background, scheme.onBackground, 4.5f)
        assertContrast("$label surface", scheme.surface, scheme.onSurface, 4.5f)
        assertContrast("$label surface variant", scheme.surfaceVariant, scheme.onSurfaceVariant, 4.5f)
        assertContrast("$label error", scheme.error, scheme.onError, 4.5f)
        assertContrast("$label error container", scheme.errorContainer, scheme.onErrorContainer, 4.5f)
    }

    private fun assertContrast(label: String, first: Color, second: Color, minimum: Float) {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue(ratio >= minimum, "$label contrast $ratio is below $minimum")
    }

    private fun colorToHex(color: Color): String {
        val red = (color.red * 255f).roundToInt().coerceIn(0, 255)
        val green = (color.green * 255f).roundToInt().coerceIn(0, 255)
        val blue = (color.blue * 255f).roundToInt().coerceIn(0, 255)
        return "#" + listOf(red, green, blue).joinToString("") {
            it.toString(16).uppercase().padStart(2, '0')
        }
    }

    private fun assertNonIncreasing(label: String, colors: List<Color>) {
        colors.zipWithNext().forEachIndexed { index, (first, second) ->
            assertTrue(
                first.luminance() >= second.luminance(),
                "$label surface luminance rises at step $index"
            )
        }
    }

    private fun assertNonDecreasing(label: String, colors: List<Color>) {
        colors.zipWithNext().forEachIndexed { index, (first, second) ->
            assertTrue(
                first.luminance() <= second.luminance(),
                "$label surface luminance falls at step $index"
            )
        }
    }
}
