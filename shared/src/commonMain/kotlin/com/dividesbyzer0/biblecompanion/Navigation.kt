package com.dividesbyzer0.biblecompanion

import androidx.compose.runtime.staticCompositionLocalOf
import com.dividesbyzer0.biblecompanion.platform.urlEncode

val LocalInternalNavigate = staticCompositionLocalOf<(collection: String, bookId: String, storyId: String?, verse: Int?, verseEnd: Int?) -> Unit> {
    { _, _, _, _, _ -> }
}

internal data class EditionDestination(
    val collection: String,
    val bookId: String,
    val storyId: String?,
    val verse: Int?,
    val verseEnd: Int?,
    val language: String,
    val editionId: String
)

internal val LocalEditionNavigate = staticCompositionLocalOf<(EditionDestination) -> Unit> { {} }

// A cross-book Continue action prefers the first numbered chapter over named
// front matter. A book without numbered chapters falls back to its first section.
// Always use the loaded book's own IDs, never the previous reader's scroll index.
internal fun firstReaderChapterId(book: Book): String? =
    ChapterLocator.build(book).byChapter.minByOrNull { it.key }?.value
        ?: book.stories.firstOrNull()?.id

// Encode a route path/query component. Current ids are ASCII-safe but future
// localized or punctuation-containing ids would otherwise corrupt navigation.
private fun encPath(s: String): String = urlEncode(s).replace("+", "%20")

internal fun appPassageLink(
    collection: String,
    bookId: String,
    storyId: String?,
    language: String,
    editionId: String,
    anchor: VerseAnchor? = null
): String {
    val params = buildList {
        add("col=${encPath(collection)}")
        add("book=${encPath(bookId)}")
        if (storyId != null) add("story=${encPath(storyId)}")
        add("sourceLang=${encPath(LocaleUtils.effectiveAssetTag(language))}")
        add("sourceEdition=${encPath(editionId)}")
        if (anchor != null) {
            add("verse=${anchor.verseStart}")
            if (anchor.verseEnd != anchor.verseStart) add("verseEnd=${anchor.verseEnd}")
        }
    }
    return "biblecompanion://open?${params.joinToString("&")}"
}

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Read : Dest("read")
    data object Study : Dest("study")
    data object AboutCalendars : Dest("about_calendars")
    data object OrdainedFeasts : Dest("ordained_feasts")
    data object Settings : Dest("settings")
    data object About : Dest("about")
    data object TranslationNotes : Dest("translation_notes")
    data object HistoricalAwareness : Dest("historical_awareness")
    data object BibleCanon : Dest("bible_canon")
    data object FalseDoctrine : Dest("false_doctrine")
    data object CommonDistortions : Dest("common_distortions")
    data object BibleChronology : Dest("bible_chronology")
    data object Genealogy : Dest("genealogy")
    data object JesusDivinity : Dest("jesus_divinity")
    data object JesusIdentity : Dest("jesus_identity")
    data object Gospel : Dest("gospel")
    data object Grace : Dest("grace")
    data object ChristianSymbolism : Dest("christian_symbolism")
    data object Christophanies : Dest("christophanies")
    data object FAQs : Dest("faqs")
    data object Bibliography : Dest("bibliography")
    data object UnseenWar : Dest("unseen_war")
    data object FeastCalendar : Dest("feast_calendar")
    data object TorahFeastsAndGentiles : Dest("torah_feasts_and_gentiles")
    data object Prophecy : Dest("prophecy")
    data object MessianicProphecy : Dest("messianic_prophecy")
    data object DanielsTimeline : Dest("daniels_timeline")
    data object AstronomicalSigns : Dest("astronomical_signs")
    data object RevelationOverview : Dest("revelation_overview")
    data object RevelationTimeline : Dest("revelation_timeline")
    data object SecondComingRapture : Dest("second_coming_rapture")
    data object SavedItems : Dest("saved_items")
    data class Books(val col: String) : Dest("books/{col}") {
        companion object { fun route(col: String) = "books/${encPath(col)}" }
    }
    data class BookView(val col: String, val bookId: String) :
        Dest("book/{col}/{bookId}?storyId={storyId}&verse={verse}&verseEnd={verseEnd}&autoStartTts={autoStartTts}&sourceLang={sourceLang}&sourceEdition={sourceEdition}&requestId={requestId}") {
        companion object {
            fun route(
                col: String,
                bookId: String,
                storyId: String? = null,
                verse: Int? = null,
                verseEnd: Int? = null,
                autoStartTts: Boolean = false,
                sourceLang: String? = null,
                sourceEdition: String? = null,
                requestId: Long? = null
            ): String {
                val base = "book/${encPath(col)}/${encPath(bookId)}"
                val params = buildList {
                    if (!storyId.isNullOrBlank()) add("storyId=${encPath(storyId)}")
                    if (verse != null) add("verse=$verse")
                    if (verseEnd != null && verseEnd != verse) add("verseEnd=$verseEnd")
                    if (autoStartTts) add("autoStartTts=true")
                    if (!sourceLang.isNullOrBlank()) add("sourceLang=${encPath(sourceLang)}")
                    if (!sourceEdition.isNullOrBlank()) add("sourceEdition=${encPath(sourceEdition)}")
                    if (requestId != null) add("requestId=$requestId")
                }
                return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
            }
        }
    }
}

/** Adds an internal event identity after a caller-controlled route has passed validation. */
internal fun withExternalReaderRequestId(route: String, requestId: Long): String {
    val pathParts = route.substringBefore('?').split('/')
    if (pathParts.size != 3 || pathParts[0] != "book") return route
    return "$route${if ('?' in route) '&' else '?'}requestId=$requestId"
}

/** Stable value stored after a one-shot reader route request has been handled. */
internal fun readerRequestIdentity(requestId: Long?, vararg routeParts: Any?): String = buildString {
    val values = arrayOf<Any?>(requestId, *routeParts)
    values.forEach { value ->
        if (value == null) {
            append("null;")
        } else {
            val text = value.toString()
            append(text.length).append(':').append(text).append(';')
        }
    }
}

internal fun followingInternalReaderRequestId(issuedRequestId: Long): Long =
    if (issuedRequestId == Long.MIN_VALUE) -1L else issuedRequestId - 1L

/**
 * Identifies the reader state that may be restored after leaving it for Home.
 * Include the current reader preference as well as the stored passage: changing
 * language or edition while Home is visible must use the normal route fallback.
 */
internal fun readingNowRestoreIdentity(
    collection: String?,
    bookId: String?,
    storyId: String?,
    sourceLanguage: String?,
    sourceEdition: String?,
    currentLanguage: String,
    currentEdition: String
): String? {
    if (collection == null || bookId == null) return null
    return readerRequestIdentity(
        null,
        collection,
        bookId,
        storyId,
        sourceLanguage,
        sourceEdition,
        currentLanguage,
        currentEdition
    )
}

/** A restored Read graph is useful only when its leaf is the expected reader. */
internal fun restoredReadingNowReaderMatches(
    destinationRoute: String?,
    restoredCollection: String?,
    restoredBookId: String?,
    expectedCollection: String,
    expectedBookId: String
): Boolean = destinationRoute?.startsWith("book/") == true &&
    restoredCollection == expectedCollection && restoredBookId == expectedBookId

/** Explicit Read/library or passage navigation supersedes a saved reader viewport. */
internal fun invalidatesSavedReadingNowState(route: String): Boolean =
    route == "tab_read" || route == Dest.Read.route || route.startsWith("book/")
