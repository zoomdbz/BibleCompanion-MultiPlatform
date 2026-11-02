package com.dividesbyzer0.biblecompanion

object ChapterLocator {

    // Greek Esther keeps legacy story ids for saved reading state. Five ids
    // differ from the printed chapter numbers in every localized asset.
    private val greekEstherChapters = mapOf(
        "esther_greek-11" to 7,
        "esther_greek-12" to 11,
        "esther_greek-13" to 12,
        "esther_greek-14" to 13,
        "esther_greek-7" to 14
    )

    data class Special(val storyId: String, val label: String)

    data class Index(
        val byChapter: Map<Int, String>,
        val specials: List<Special> = emptyList()
    )

    // Chapters normally come from story ids ("genesis-3", "psalm-151-1",
    // "2_enoch-68"). Greek Esther has five legacy id exceptions. The refs lines are
    // localized prose ("Bel and the Dragon 1:1-42", "Bel und der Drache 1-42",
    // "Вил и Дракон 0-42") and parsing them yielded an empty map for English
    // Bel (the word "and" read as a list separator), chapter 1511 for
    // Psalm 151, and 42 phantom chapters for German Bel, so refs are no
    // longer consulted at all.
    fun build(book: Book): Index {
        // A single-story book is chapter 1 regardless of its id tail: nine
        // languages ship song_of_three with id "...-3" while its verse
        // markers say (1:x), and the verse grid keys off the markers.
        book.stories.singleOrNull()?.let { only ->
            return Index(byChapter = mapOf(1 to only.id))
        }

        val claim = linkedMapOf<Int, String>()
        val specials = mutableListOf<Special>()
        for (story in book.stories) {
            val tail = story.id.substringAfterLast('-')
            val chap = if (book.id == "esther_greek") {
                greekEstherChapters[story.id] ?: tail.toIntOrNull()
            } else {
                tail.toIntOrNull()
            }
            if (chap != null) {
                if (chap !in claim) claim[chap] = story.id
            } else {
                // Non-numeric tails are named front matter, e.g. the English
                // Sirach translator's prologue ("sirach-prologue"). Surfaced
                // in the chapter picker as a lettered button before chapter 1.
                specials += Special(story.id, tail)
            }
        }
        return Index(byChapter = claim, specials = specials)
    }
}
