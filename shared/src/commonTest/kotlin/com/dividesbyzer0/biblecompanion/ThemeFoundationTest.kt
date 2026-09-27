package com.dividesbyzer0.biblecompanion

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeFoundationTest {
    @Test
    fun staticPresetsRetainSemanticColors() {
        val expectedLight = mapOf(
            ThemePreset.Parchment to Triple(Color(0xFF7D4A3E), Color(0xFF76584A), Color(0xFF6E5F31)),
            ThemePreset.Sage to Triple(Color(0xFF4C6B4D), Color(0xFF54634E), Color(0xFF38656A)),
            ThemePreset.Indigo to Triple(Color(0xFF3A5BA0), Color(0xFF575E71), Color(0xFF735471)),
            ThemePreset.Ink to Triple(Color(0xFFAF3434), Color(0xFF775653), Color(0xFF735A2F))
        )
        val expectedDark = mapOf(
            ThemePreset.Parchment to Triple(Color(0xFFF6B8A6), Color(0xFFE8BEA9), Color(0xFFDDC78F)),
            ThemePreset.Sage to Triple(Color(0xFFB4D2B1), Color(0xFFBACCB4), Color(0xFFA2CED3)),
            ThemePreset.Indigo to Triple(Color(0xFFB3C5FF), Color(0xFFBFC6DC), Color(0xFFE2BBDD)),
            ThemePreset.Ink to Triple(Color(0xFFFFB4AA), Color(0xFFE7BDB9), Color(0xFFE3C18E))
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
                light.surfaceContainerLowest,
                light.surfaceBright,
                light.surface,
                light.surfaceContainerLow,
                light.surfaceContainer,
                light.surfaceContainerHigh,
                light.surfaceContainerHighest,
                light.surfaceDim
            )

            val dark = colorSchemeFor(preset, dark = true, customHue = 212f)
            assertEquals(dark.background, dark.surface, "$preset dark background")
            assertNonDecreasing(
                "$preset dark",
                dark.surfaceContainerLowest,
                dark.surfaceDim,
                dark.surface,
                dark.surfaceContainerLow,
                dark.surfaceContainer,
                dark.surfaceContainerHigh,
                dark.surfaceContainerHighest,
                dark.surfaceBright
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
    fun dynamicComfortPassPreservesWallpaperAccentRoles() {
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
        assertEquals(source.tertiary, result.tertiary)
        assertEquals(source.onTertiary, result.onTertiary)
        assertEquals(source.tertiaryContainer, result.tertiaryContainer)
        assertEquals(source.onTertiaryContainer, result.onTertiaryContainer)
        assertTrue(result.surface.luminance() > source.surface.luminance())
        assertTrue(result.surfaceContainerHighest.luminance() > result.surfaceContainer.luminance())
        assertNonDecreasing(
            "dynamic dark",
            result.surfaceContainerLowest,
            result.surfaceDim,
            result.surface,
            result.surfaceContainerLow,
            result.surfaceContainer,
            result.surfaceContainerHigh,
            result.surfaceContainerHighest,
            result.surfaceBright
        )
        assertContentRoleContrast("dynamic dark", result)
        assertContrast("dynamic outline variant", result.outlineVariant, result.background, 3.0f)
    }

    private fun assertSemanticColors(scheme: ColorScheme, expected: Triple<Color, Color, Color>) {
        assertEquals(expected.first, scheme.primary)
        assertEquals(expected.second, scheme.secondary)
        assertEquals(expected.third, scheme.tertiary)
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

    private fun assertNonIncreasing(label: String, vararg colors: Color) {
        colors.asList().zipWithNext().forEachIndexed { index, (first, second) ->
            assertTrue(
                first.luminance() >= second.luminance(),
                "$label surface luminance rises at step $index"
            )
        }
    }

    private fun assertNonDecreasing(label: String, vararg colors: Color) {
        colors.asList().zipWithNext().forEachIndexed { index, (first, second) ->
            assertTrue(
                first.luminance() <= second.luminance(),
                "$label surface luminance falls at step $index"
            )
        }
    }
}
