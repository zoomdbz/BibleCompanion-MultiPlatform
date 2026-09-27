package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderEntryTest {
    private fun story(id: String) = Story(id, id, emptyList(), emptyList())

    @Test
    fun continueIntoMarkStartsAtChapterOne() {
        val mark = Book("mark", "Mark", (1..16).map { story("mark-$it") })
        assertEquals("mark-1", firstReaderChapterId(mark))
    }

    @Test
    fun continueSkipsNamedFrontMatter() {
        val sirach = Book("sirach", "Sirach", listOf(
            story("sirach-prologue"), story("sirach-1"), story("sirach-2")
        ))
        assertEquals("sirach-1", firstReaderChapterId(sirach))
    }

    @Test
    fun firstChapterRetainsTheActualNativeStoryId() {
        val song = Book("song_of_three", "Song of the Three", listOf(story("song_of_three-3")))
        assertEquals("song_of_three-3", firstReaderChapterId(song))
    }

    @Test
    fun emptyBookHasNoInventedChapter() {
        assertNull(firstReaderChapterId(Book("empty", "Empty", emptyList())))
    }
}
