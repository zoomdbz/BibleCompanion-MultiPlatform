package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import com.dividesbyzer0.biblecompanion.platform.assetExists
import kotlinx.serialization.json.Json

object ContentRepo {
    private val json = Json { ignoreUnknownKeys = true }

    private fun readIndexPairsOrEmpty(
        context: PlatformContext,
        candidatePaths: List<String>
    ): List<Pair<String, String>> {
        for (p in candidatePaths) {
            val txt = readAssetText(context, p) ?: continue
            val pairs = runCatching {
                val arr = json.decodeFromString<List<List<String>>>(txt)
                arr.map { it[0] to it[1] }
            }.getOrNull()
            if (pairs != null) return pairs
        }
        return emptyList()
    }

    fun listBooksLocalized(
        context: PlatformContext,
        collection: String,
        appLang: String
    ): List<Pair<String, String>> {
        val tag = LocaleUtils.effectiveAssetTag(appLang)
        return readIndexPairsOrEmpty(
            context,
            listOf(
                "books/$collection/$tag/_index.json",
                "books/$collection/en/_index.json"
            )
        )
    }

    fun listApocryphaSectionsLocalized(
        context: PlatformContext,
        appLang: String
    ): Pair<List<Pair<String, String>>, List<Pair<String, String>>> {
        val tag = LocaleUtils.effectiveAssetTag(appLang)
        val regular = readIndexPairsOrEmpty(
            context,
            listOf(
                "books/apocrypha/$tag/_index.json",
                "books/apocrypha/en/_index.json"
            )
        )
        return regular to emptyList()
    }

    fun loadBookOrNull(
        context: PlatformContext,
        collection: String,
        bookId: String,
        appLang: String,
        internalBibleVersion: String = BibleEditions.BSB
    ): Book? = loadBookWithEdition(
        context = context,
        collection = collection,
        bookId = bookId,
        appLang = appLang,
        internalBibleVersion = internalBibleVersion
    )?.book

    fun loadBookWithEdition(
        context: PlatformContext,
        collection: String,
        bookId: String,
        appLang: String,
        internalBibleVersion: String = BibleEditions.BSB
    ): LoadedBook? = runCatching {
        val tag = LocaleUtils.effectiveAssetTag(appLang)
        val candidates = listOf(
            "books/$collection/$tag/$bookId.json",
            "books/$collection/en/$bookId.json"
        )
        val path = candidates.firstOrNull { p -> assetExists(context, p) } ?: return@runCatching null
        val txt = readAssetText(context, path) ?: return@runCatching null
        val base = json.decodeFromString<Book>(txt)
        val requested = BibleEditions.effective(appLang, internalBibleVersion)
        val editionApplies = tag == "en" && requested == BibleEditions.KJV_1769 &&
            collection in setOf("old_testament", "new_testament", "deuterocanonical")
        if (!editionApplies) {
            return@runCatching LoadedBook(
                book = base,
                requestedEdition = requested,
                effectiveEdition = BibleEditions.BSB,
                coverage = EditionCoverage.BASE
            )
        }

        val overlayPath = "books/editions/en/${BibleEditions.KJV_1769}/$collection/$bookId.json"
        val overlayText = readAssetText(context, overlayPath)
        val fallback = LoadedBook(
            book = base,
            requestedEdition = requested,
            effectiveEdition = BibleEditions.BSB,
            coverage = EditionCoverage.FALLBACK
        )
        if (overlayText == null) return@runCatching fallback

        runCatching {
            val overlay = json.decodeFromString<EditionBookOverlay>(overlayText)
            require(overlay.editionId == BibleEditions.KJV_1769)
            require(overlay.collection == collection && overlay.bookId == bookId)

            val storyByChapter = ChapterLocator.build(base).byChapter
            val chapterByStory = storyByChapter.entries.associate { (chapter, storyId) -> storyId to chapter }
            val overlayByChapter = overlay.chapters.associateBy { it.number }
            val mergedStories = base.stories.map { story ->
                val chapterNumber = chapterByStory[story.id]
                    ?: story.id.substringAfterLast('-').toIntOrNull()
                val chapter = chapterNumber?.let { overlayByChapter[it] }
                if (chapter == null) story else story.copy(
                    summaryBullets = chapter.verses.map { verse ->
                        "${verse.text.trim()} (${verse.chapter}:${verse.verse})."
                    },
                    superscription = chapter.superscription
                )
            }
            LoadedBook(
                book = base.copy(stories = mergedStories),
                requestedEdition = requested,
                effectiveEdition = BibleEditions.KJV_1769,
                coverage = EditionCoverage.FULL
            )
        }.getOrElse { fallback }
    }.getOrNull()
}
