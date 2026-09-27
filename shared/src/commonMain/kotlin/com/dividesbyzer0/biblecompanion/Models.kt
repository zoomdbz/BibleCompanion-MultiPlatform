package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.Serializable

@Serializable
data class TransNote(
  val term: String,
  val original: String? = null,
  val note: String
)

@Serializable
data class ManuscriptVariant(
  val ref: String,
  val text: String
)

@Serializable
data class Heading(
  // Native verse-unit start in the active edition. Every edition supplies its
  // own explicit lookup table; the renderer never infers placement from list
  // order or borrows another edition's verse coordinates.
  val beforeVerse: Int,
  val text: String
)

@Serializable
data class Story(
  val id: String,
  val title: String,
  val refs: List<String>,
  val summaryBullets: List<String>,
  val keyTakeaway: String = "",
  val crossRefs: List<String> = emptyList(),
  val manuscriptVariants: List<ManuscriptVariant> = emptyList(),
  val translationNotes: List<TransNote> = emptyList(),
  val headings: List<Heading> = emptyList(),
  // Psalm superscription (e.g. "To the choirmaster. A Psalm of David."). Part
  // of the inspired text, but a heading-style preface, not a numbered verse, so
  // it is stored separately and rendered as an italic superscription above the
  // verses instead of bleeding into verse 1. Empty for books without one.
  val superscription: String = ""
)

@Serializable
data class Book(
  val id: String,
  val title: String,
  val stories: List<Story>,
  // Multi-paragraph book introduction. Paragraphs separated by "\n\n".
  // Empty means no Intro entry is shown in the chapter picker.
  val intro: String = ""
)

const val DEFAULT_CHRONOLOGY_EXPANDED_EPOCHS = "CREATION_EARLY_HISTORY"

data class PrefsState(
  val theme: String = "System",
  val translation: String = "ESV",
  val readerMode: String = "internal",
  // Bundled in-app edition selector. The first edition for each language is
  // modern and remains the default; source-backed traditional overlays are
  // selectable where they ship with the app.
  val internalBibleVersion: String = "bsb",
  // Chronology owns this toggle so changing it does not unexpectedly alter
  // the app-wide Collections preference.
  val chronologyIncludeDeutero: Boolean = true,
  // Comma-separated ChronologyEpochId names. An empty value means all closed.
  val chronologyExpandedEpochs: String = DEFAULT_CHRONOLOGY_EXPANDED_EPOCHS,
  val showDeutero: Boolean = true,
  val showApoc: Boolean = true,
  val showPseudepigrapha: Boolean = true,
  val appLanguage: String = "system",
  val jesusWordsColor: String = "default",
  val fontMode: String = "sans",
  val textSizeScale: Float = 1.0f,
  val readingLineSpacing: Float = 1.65f,
  val versePerLine: Boolean = true,
  val lastReadCollection: String? = null,
  val lastReadBookId: String? = null,
  val lastReadBookTitle: String? = null,
  val lastReadStoryId: String? = null,
  // Null marks a pre-native-numbering canonical story ID.
  val lastReadSourceLanguage: String? = null,
  val lastReadSourceEdition: String? = null,
  val onboardingComplete: Boolean = false,
  val studyPinned: Boolean = false,
  val themePreset: String = "parchment",
  val divineName: String = "traditional",
  val divineNameColor: String = "default",
  val feastNotesExpanded: Boolean = true,
  val ordainedFeastsExpanded: Boolean = false,
  val hapticEnabled: Boolean = true,
  val customThemeHue: Float = 210f,
  val expandNotesDefault: Boolean = false,
  val collapsedStoriesJson: String = "{}",
  val autoContinueTts: Boolean = true,
  val crossBookTts: Boolean = false,
  // When false (default), book intros are skipped by TTS. The IntroCard's
  // play button hides and auto-continue TTS won't speak intro paragraphs.
  val ttsReadIntros: Boolean = false,
  // JSON object: { "<assetFileName>": ["<sectionHeader>", ...], ... }
  val notesExpandedSectionsJson: String = "{}",
  // Local date ("YYYY-MM-DD") on which VOTD was dismissed; empty = not dismissed
  val votdDismissedDate: String = "",
  // Device-local opt-in; notification permission is requested only from Settings.
  val dailyVerseNotifications: Boolean = false,
  val dailyVerseNotificationMinuteOfDay: Int = 9 * 60,
  // Screenshot-mode hint: when true, settings opens with the language picker pre-expanded.
  val screenshotExpandLanguage: Boolean = false,
  val aiSearch: Boolean = true
)

enum class SearchHitType { STORY, NOTE, BOOK }

data class SearchHit(
  val title: String,
  val snippet: String,
  val collection: String,
  val bookId: String,
  val storyId: String,
  val score: Int = 0,
  val type: SearchHitType = SearchHitType.STORY,
  val verse: Int? = null,
  val verseEnd: Int? = null,
  val semantic: Boolean = false
)

// Bookmarks & saved verses
@Serializable
data class Bookmark(
  val collection: String,
  val bookId: String,
  val bookTitle: String,
  val storyId: String,
  val storyTitle: String,
  val snippet: String = "",
  val timestamp: Long,
  // 0 = not manually ordered (sorts to top by timestamp); positive = explicit user position.
  val sortOrder: Int = 0,
  val sourceLanguage: String? = null
)

@Serializable
data class SavedVerse(
  val collection: String,
  val bookId: String,
  val storyId: String,
  val bulletIndex: Int,
  // Stable verse identity. bulletIndex remains for backward-compatible
  // backups, but cannot identify a verse across BSB/KJV versification changes.
  val chapter: Int? = null,
  val verseStart: Int? = null,
  val verseEnd: Int? = null,
  val editionId: String = "default",
  val text: String,
  val ref: String,
  val highlightColor: String? = null,
  val labels: List<String> = emptyList(),
  val timestamp: Long,
  // 0 = not manually ordered (sorts to top by timestamp); positive = explicit user position.
  val sortOrder: Int = 0,
  val sourceLanguage: String? = null
)

internal fun SavedVerse.sameScriptureLocation(other: SavedVerse): Boolean {
  if (collection != other.collection || bookId != other.bookId) return false
  if (scriptureLanguage() != other.scriptureLanguage()) return false
  // Equal numbers in different editions need not identify the same passage.
  // Keep their saved text, highlights and labels independent until an audited
  // passage crosswalk explicitly establishes equivalence.
  if (scriptureEdition() != other.scriptureEdition()) return false
  val thisAnchor = stableAnchor()
  val otherAnchor = other.stableAnchor()
  return if (thisAnchor != null && otherAnchor != null) {
    thisAnchor == otherAnchor
  } else {
    storyId == other.storyId && bulletIndex == other.bulletIndex
  }
}

internal fun SavedVerse.scriptureLanguage(): String =
  LocaleUtils.effectiveAssetTag(sourceLanguage ?: "en")

internal fun SavedVerse.scriptureEdition(): String = when {
  editionId == "default" -> BibleEditions.defaultForLanguage(scriptureLanguage())
  scriptureLanguage() == "en" && editionId.equals("kjv", ignoreCase = true) -> BibleEditions.KJV_1769
  else -> editionId.lowercase()
}

internal fun SavedVerse.belongsToEdition(language: String, edition: String): Boolean =
  scriptureLanguage() == LocaleUtils.effectiveAssetTag(language) && scriptureEdition() == edition.lowercase()

private const val VERSE_ANCHOR_DASHES = "-\u2010\u2011\u2013\u2014\uFF0D"

private val chapterVerseAnchorPattern = Regex(
  """[\(\uFF08]\s*(\d+)\s*[:\uFF1A]\s*(\d+)(?:\s*[$VERSE_ANCHOR_DASHES]\s*(\d+))?\s*[\)\uFF09]\s*([.\u3002\u0964]?)\s*$"""
)

private val singleChapterVerseAnchorPattern = Regex(
  """[\(\uFF08]\s*(\d+)(?:\s*[$VERSE_ANCHOR_DASHES]\s*(\d+))?\s*[\)\uFF09]\s*([.\u3002\u0964]?)\s*$"""
)

private val knownSingleChapterVerseStories = mapOf(
  "bel-1" to "bel_and_the_dragon",
  "letter_of_jeremiah-1" to "letter_of_jeremiah",
  "susanna-1" to "susanna"
)

internal data class VerseAnchor(
  val chapter: Int,
  val verseStart: Int,
  val verseEnd: Int = verseStart
)

/**
 * A parsed source marker. [rawMarkerRange] retains its exact source extent,
 * including terminal punctuation and whitespace, so presentation code can
 * splice around it without rewriting Scripture.
 */
internal data class ParsedTrailingVerseAnchor(
  val anchor: VerseAnchor,
  val rawMarkerRange: IntRange,
  val trailingPunctuation: String
) {
  val rawStart: Int get() = rawMarkerRange.first
}

/**
 * Parses only an explicit marker at the end of a source unit. Localized
 * punctuation changes metadata recognition, never the source string itself.
 * Bare `(verse)` markers require one of the audited single-chapter story IDs.
 */
internal fun parseTrailingVerseAnchor(
  text: String,
  storyId: String? = null,
  bookId: String? = null
): ParsedTrailingVerseAnchor? {
  chapterVerseAnchorPattern.find(text)?.let { match ->
    val chapter = match.groupValues[1].toIntOrNull() ?: return null
    val start = match.groupValues[2].toIntOrNull() ?: return null
    val end = match.groupValues[3].toIntOrNull() ?: start
    return ParsedTrailingVerseAnchor(
      anchor = VerseAnchor(chapter, start, end),
      rawMarkerRange = match.range,
      trailingPunctuation = match.groupValues[4]
    )
  }

  val expectedBookId = knownSingleChapterVerseStories[storyId] ?: return null
  if (bookId != null && bookId != expectedBookId) return null
  val match = singleChapterVerseAnchorPattern.find(text) ?: return null
  val start = match.groupValues[1].toIntOrNull() ?: return null
  val end = match.groupValues[2].toIntOrNull() ?: start
  return ParsedTrailingVerseAnchor(
    anchor = VerseAnchor(chapter = 1, verseStart = start, verseEnd = end),
    rawMarkerRange = match.range,
    trailingPunctuation = match.groupValues[3]
  )
}

internal fun verseAnchorFromText(text: String): VerseAnchor? =
  parseTrailingVerseAnchor(text)?.anchor

internal fun SavedVerse.stableAnchor(): VerseAnchor? {
  val chapterNumber = chapter
  val start = verseStart
  if (chapterNumber != null && start != null) {
    return VerseAnchor(chapterNumber, start, verseEnd ?: start)
  }
  return parseTrailingVerseAnchor(text, storyId = storyId, bookId = bookId)?.anchor
}

@Serializable
data class Label(
  val id: String,
  val name: String,
  val color: String = "blue",
  val timestamp: Long
)

// Export / backup
@Serializable
data class AppBackup(
  val version: Int = 2,
  val timestamp: Long,
  val bookmarks: List<Bookmark> = emptyList(),
  val savedVerses: List<SavedVerse> = emptyList(),
  val labels: List<Label> = emptyList()
)

// Genealogy models
data class GeneNode(val name: String, val refs: List<String>)
data class GeneRow(
  val center: GeneNode? = null,
  val left: GeneNode? = null,
  val right: GeneNode? = null
)
