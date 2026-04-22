package com.dividesbyzer0.biblecompanion

object ChapterLocator {

    data class Index(
        val byChapter: Map<Int, String>
    )

    fun build(book: Book): Index {
        val targetBookKey = key(book.title)
        val claim = linkedMapOf<Int, String>()

        for (story in book.stories) {
            for (refLine in story.refs) {
                val chapters = extractChapters(refLine, targetBookKey)
                for (chap in chapters) {
                    if (chap !in claim) claim[chap] = story.id
                }
            }
        }

        return Index(byChapter = claim)
    }

    private fun key(name: String): String {
        val letters = name.lowercase().replace(Regex("[^a-z]"), "")
        return if (letters.length >= 3) letters.substring(0, 3) else letters
    }

    private fun extractChapters(line: String, targetBookKey: String): Set<Int> {
        val cleaned = line
            .replace('\u2014', '-')
            .replace('\u2013', '-')
            .replace(Regex("\\(.*?\\)"), " ")
            .trim()

        val segments = cleaned.split(Regex("\\s*(?:;|,|\\band\\b|&|\\|)\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val out = linkedSetOf<Int>()
        var carryBookKey: String? = null

        for (seg in segments) {
            val m = Regex("^([1-3]?\\s*\\p{L}[\\p{L}\\s]+?)\\s+(.+)$").find(seg)
            val (segBookKey, restRaw) = if (m != null) {
                key(m.groupValues[1]) to m.groupValues[2].trim()
            } else {
                null to seg
            }

            val segBook = segBookKey ?: carryBookKey ?: targetBookKey
            if (segBookKey != null) carryBookKey = segBookKey
            if (segBook != targetBookKey) continue

            val rest = restRaw.replace(" ", "")

            var mm = Regex("^(\\d+):(\\d+)-(\\d+):(\\d+)$").matchEntire(rest)
            if (mm != null) {
                val c1 = mm.groupValues[1].toInt()
                val c2 = mm.groupValues[3].toInt()
                for (c in minOf(c1, c2)..maxOf(c1, c2)) out.add(c)
                continue
            }

            mm = Regex("^(\\d+):(\\d+)-(\\d+)$").matchEntire(rest)
            if (mm != null) { out.add(mm.groupValues[1].toInt()); continue }

            mm = Regex("^(\\d+):(\\d+)$").matchEntire(rest)
            if (mm != null) { out.add(mm.groupValues[1].toInt()); continue }

            mm = Regex("^(\\d+)-(\\d+)$").matchEntire(rest)
            if (mm != null) {
                val c1 = mm.groupValues[1].toInt()
                val c2 = mm.groupValues[2].toInt()
                for (c in minOf(c1, c2)..maxOf(c1, c2)) out.add(c)
                continue
            }

            mm = Regex("^(\\d+)$").matchEntire(rest)
            if (mm != null) { out.add(mm.groupValues[1].toInt()); continue }
        }

        return out
    }
}
