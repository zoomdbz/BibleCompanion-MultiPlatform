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
  // The bullet whose trailing verse marker matches this number will be preceded
  // by the heading text in the renderer. Bullet count is unaffected; only the
  // visual insertion changes.
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
  // English-only bundled edition selector. Other languages have one custom
  // in-app translation and deliberately hide the edition control.
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
  val lastReadCollection: String? = null,
  val lastReadBookId: String? = null,
  val lastReadBookTitle: String? = null,
  val lastReadStoryId: String? = null,
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
  val sortOrder: Int = 0
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
  val sortOrder: Int = 0
)

internal fun SavedVerse.sameScriptureLocation(other: SavedVerse): Boolean {
  if (collection != other.collection || bookId != other.bookId) return false
  val thisAnchor = stableAnchor()
  val otherAnchor = other.stableAnchor()
  return if (thisAnchor != null && otherAnchor != null) {
    thisAnchor == otherAnchor
  } else {
    storyId == other.storyId && bulletIndex == other.bulletIndex
  }
}

private val savedVerseAnchorPattern = Regex(
  """\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\s*\.?\s*$"""
)

internal data class VerseAnchor(
  val chapter: Int,
  val verseStart: Int,
  val verseEnd: Int = verseStart
)

internal fun verseAnchorFromText(text: String): VerseAnchor? {
  val match = savedVerseAnchorPattern.find(text) ?: return null
  val chapter = match.groupValues[1].toIntOrNull() ?: return null
  val start = match.groupValues[2].toIntOrNull() ?: return null
  val end = match.groupValues[3].toIntOrNull() ?: start
  return VerseAnchor(chapter, start, end)
}

internal fun SavedVerse.stableAnchor(): VerseAnchor? {
  val chapterNumber = chapter
  val start = verseStart
  if (chapterNumber != null && start != null) {
    return VerseAnchor(chapterNumber, start, verseEnd ?: start)
  }
  return verseAnchorFromText(text)
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
