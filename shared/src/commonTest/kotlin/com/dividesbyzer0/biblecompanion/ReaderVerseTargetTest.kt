package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderVerseTargetTest {
    @Test
    fun inlineReferencesCannotHijackTheSelectedVerse() {
        val bullets = listOf("See John 3:16 in the note. (1:1).", "The next verse. (1:16).")
        assertEquals(setOf(1), findBulletsForVerseRange(bullets, 16, 16))
    }

    @Test
    fun fullWidthMarkersAndRangesKeepTheirSourceVerseUnit() {
        val bullets = listOf("Text (1:1).", "Text \uFF081:2\u20133\uFF09\u3002")
        assertEquals(setOf(1), findBulletsForVerseRange(bullets, 3, 3))
    }

    @Test
    fun singleChapterMarkersRequireKnownBookContext() {
        val bullets = listOf("First (1).", "Second (2).")
        assertEquals(emptySet(), findBulletsForVerseRange(bullets, 2, 2))
        assertEquals(setOf(1), findBulletsForVerseRange(bullets, 2, 2, "susanna-1", "susanna"))
    }
}
