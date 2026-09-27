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
        Dest("book/{col}/{bookId}?storyId={storyId}&verse={verse}&verseEnd={verseEnd}&autoStartTts={autoStartTts}&sourceLang={sourceLang}&sourceEdition={sourceEdition}") {
        companion object {
            fun route(
                col: String,
                bookId: String,
                storyId: String? = null,
                verse: Int? = null,
                verseEnd: Int? = null,
                autoStartTts: Boolean = false,
                sourceLang: String? = null,
                sourceEdition: String? = null
            ): String {
                val base = "book/${encPath(col)}/${encPath(bookId)}"
                val params = buildList {
                    if (!storyId.isNullOrBlank()) add("storyId=${encPath(storyId)}")
                    if (verse != null) add("verse=$verse")
                    if (verseEnd != null && verseEnd != verse) add("verseEnd=$verseEnd")
                    if (autoStartTts) add("autoStartTts=true")
                    if (!sourceLang.isNullOrBlank()) add("sourceLang=${encPath(sourceLang)}")
                    if (!sourceEdition.isNullOrBlank()) add("sourceEdition=${encPath(sourceEdition)}")
                }
                return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
            }
        }
    }
}
