package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTypographyTest {
    @Test
    fun scriptureSizeStartsAtSeventeenSpAndUsesPreferenceScale() {
        assertEquals(17f, scriptureFontSize(scale = 1f).value)
        assertEquals(21.25f, scriptureFontSize(scale = 1.25f).value)
    }

    @Test
    fun scriptureLineHeightCombinesSizeScaleAndReadingSpacing() {
        assertEquals(28.05f, scriptureLineHeight(scale = 1f, lineSpacing = 1.65f).value, 0.0001f)
        assertEquals(33.66f, scriptureLineHeight(scale = 1.2f, lineSpacing = 1.65f).value, 0.0001f)
    }
}
