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
  val headings: List<Heading> = emptyList()
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

data class PrefsState(
  val theme: String = "System",
  val translation: String = "ESV",
  val readerMode: String = "biblecom",
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
  val timestamp: Long
)

@Serializable
data class SavedVerse(
  val collection: String,
  val bookId: String,
  val storyId: String,
  val bulletIndex: Int,
  val text: String,
  val ref: String,
  val highlightColor: String? = null,
  val labels: List<String> = emptyList(),
  val timestamp: Long,
  // 0 = not manually ordered (sorts to top by timestamp); positive = explicit user position.
  val sortOrder: Int = 0
)

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
  val version: Int = 1,
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
