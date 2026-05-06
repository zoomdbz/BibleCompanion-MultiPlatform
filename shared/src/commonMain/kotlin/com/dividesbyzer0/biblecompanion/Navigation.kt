package com.dividesbyzer0.biblecompanion

import androidx.compose.runtime.staticCompositionLocalOf

val LocalInternalNavigate = staticCompositionLocalOf<(collection: String, bookId: String, storyId: String?, verse: Int?, verseEnd: Int?) -> Unit> {
    { _, _, _, _, _ -> }
}

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Settings : Dest("settings")
    data object About : Dest("about")
    data object TranslationNotes : Dest("translation_notes")
    data object HistoricalAwareness : Dest("historical_awareness")
    data object BibleCanon : Dest("bible_canon")
    data object FalseDoctrine : Dest("false_doctrine")
    data object CommonDistortions : Dest("common_distortions")
    data object Genealogy : Dest("genealogy")
    data object JesusDivinity : Dest("jesus_divinity")
    data object Grace : Dest("grace")
    data object ChristianSymbolism : Dest("christian_symbolism")
    data object Christophanies : Dest("christophanies")
    data object FAQs : Dest("faqs")
    data object UnseenWar : Dest("unseen_war")
    data object FeastCalendar : Dest("feast_calendar")
    data object Prophecy : Dest("prophecy")
    data object MessianicProphecy : Dest("messianic_prophecy")
    data object DanielsTimeline : Dest("daniels_timeline")
    data object AstronomicalSigns : Dest("astronomical_signs")
    data object RevelationOverview : Dest("revelation_overview")
    data object SavedItems : Dest("saved_items")
    data class Books(val col: String) : Dest("books/{col}") {
        companion object { fun route(col: String) = "books/$col" }
    }
    data class BookView(val col: String, val bookId: String) :
        Dest("book/{col}/{bookId}?storyId={storyId}&verse={verse}&verseEnd={verseEnd}&autoStartTts={autoStartTts}") {
        companion object {
            fun route(
                col: String,
                bookId: String,
                storyId: String? = null,
                verse: Int? = null,
                verseEnd: Int? = null,
                autoStartTts: Boolean = false
            ): String {
                val base = "book/$col/$bookId"
                val params = buildList {
                    if (!storyId.isNullOrBlank()) add("storyId=$storyId")
                    if (verse != null) add("verse=$verse")
                    if (verseEnd != null && verseEnd != verse) add("verseEnd=$verseEnd")
                    if (autoStartTts) add("autoStartTts=true")
                }
                return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
            }
        }
    }
}
