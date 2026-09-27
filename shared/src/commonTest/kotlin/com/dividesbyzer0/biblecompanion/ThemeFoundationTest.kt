package com.dividesbyzer0.biblecompanion

import androidx.compose.material3.ColorScheme
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

    private fun assertSemanticColors(scheme: ColorScheme, expected: Triple<Color, Color, Color>) {
        assertEquals(expected.first, scheme.primary)
        assertEquals(expected.second, scheme.secondary)
        assertEquals(expected.third, scheme.tertiary)
    }

    private fun assertNonIncreasing(label: String, vararg colors: Color) {
        colors.zipWithNext().forEachIndexed { index, (first, second) ->
            assertTrue(
                first.luminance() >= second.luminance(),
                "$label surface luminance rises at step $index"
            )
        }
    }

    private fun assertNonDecreasing(label: String, vararg colors: Color) {
        colors.zipWithNext().forEachIndexed { index, (first, second) ->
            assertTrue(
                first.luminance() <= second.luminance(),
                "$label surface luminance falls at step $index"
            )
        }
    }
}
