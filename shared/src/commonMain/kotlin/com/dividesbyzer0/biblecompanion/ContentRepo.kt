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
        appLang: String
    ): Book? = runCatching {
        val tag = LocaleUtils.effectiveAssetTag(appLang)
        val candidates = listOf(
            "books/$collection/$tag/$bookId.json",
            "books/$collection/en/$bookId.json"
        )
        val path = candidates.firstOrNull { p -> assetExists(context, p) } ?: return@runCatching null
        val txt = readAssetText(context, path) ?: return@runCatching null
        json.decodeFromString<Book>(txt)
    }.getOrNull()
}
