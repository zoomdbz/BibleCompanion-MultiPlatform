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
        assertEquals("mark-16", lastReaderChapterId(mark))
    }

    @Test
    fun continueSkipsNamedFrontMatter() {
        val sirach = Book("sirach", "Sirach", listOf(
            story("sirach-prologue"), story("sirach-1"), story("sirach-2")
        ))
        assertEquals("sirach-1", firstReaderChapterId(sirach))
        assertEquals("sirach-2", lastReaderChapterId(sirach))
    }

    @Test
    fun firstChapterRetainsTheActualNativeStoryId() {
        val song = Book("song_of_three", "Song of the Three", listOf(story("song_of_three-3")))
        assertEquals("song_of_three-3", firstReaderChapterId(song))
    }

    @Test
    fun emptyBookHasNoInventedChapter() {
        assertNull(firstReaderChapterId(Book("empty", "Empty", emptyList())))
        assertNull(lastReaderChapterId(Book("empty", "Empty", emptyList())))
    }

    @Test
    fun chapterArrowsCrossBookAndTestamentBoundaries() {
        val old = listOf("genesis" to "Genesis", "malachi" to "Malachi")
        val new = listOf("matthew" to "Matthew", "mark" to "Mark")
        assertEquals(
            ReaderBookNeighbor("old_testament", "malachi", "Malachi"),
            readerBookNeighbor("new_testament", "matthew", new, old, forward = false)
        )
        assertEquals(
            ReaderBookNeighbor("new_testament", "matthew", "Matthew"),
            readerBookNeighbor("old_testament", "malachi", old, new, forward = true)
        )
        assertEquals(
            ReaderBookNeighbor("new_testament", "mark", "Mark"),
            readerBookNeighbor("new_testament", "matthew", new, old, forward = true)
        )
        assertNull(readerBookNeighbor("old_testament", "genesis", old, new, forward = false))
        assertNull(readerBookNeighbor("new_testament", "mark", new, old, forward = true))
        assertNull(readerBookNeighbor("old_testament", "zechariah",
            listOf("zechariah" to "Zechariah"), new, forward = true))
    }
}
