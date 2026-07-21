package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import kotlinx.coroutines.flow.Flow

// Cross-platform prefs using a simple in-memory + platform persistence approach
expect class PrefsRepo(context: PlatformContext) {
    val flow: Flow<PrefsState>
    fun initialSnapshot(): PrefsState
    suspend fun setTheme(theme: String)
    suspend fun setVersion(version: String)
    suspend fun setReaderMode(mode: String)
    suspend fun setDeutero(show: Boolean)
    suspend fun setApoc(show: Boolean)
    suspend fun setPseudepigrapha(show: Boolean)
    suspend fun setAppLanguage(tag: String)
    suspend fun setJesusWordsColor(colorKey: String)
    suspend fun setFontMode(mode: String)
    suspend fun setTextSizeScale(scale: Float)
    suspend fun setLastRead(collection: String, bookId: String, bookTitle: String, storyId: String?)
    suspend fun setOnboardingComplete(complete: Boolean)
    suspend fun setStudyPinned(pinned: Boolean)
    suspend fun setThemePreset(preset: String)
    suspend fun setDivineName(mode: String)
    suspend fun setDivineNameColor(colorKey: String)
    suspend fun setFeastNotesExpanded(expanded: Boolean)
    suspend fun setOrdainedFeastsExpanded(expanded: Boolean)
    suspend fun setHapticEnabled(enabled: Boolean)
    suspend fun setCustomThemeHue(hue: Float)
    suspend fun setExpandNotesDefault(expand: Boolean)
    suspend fun setCrossBookTts(enabled: Boolean)
    suspend fun setCollapsedStories(json: String)
    suspend fun setAutoContinueTts(enabled: Boolean)
    suspend fun setTtsReadIntros(enabled: Boolean)
    suspend fun setNotesExpandedSections(json: String)
    suspend fun setVotdDismissedDate(date: String)
    suspend fun setAiSearch(enabled: Boolean)

    val bookmarksFlow: Flow<List<Bookmark>>
    val savedVersesFlow: Flow<List<SavedVerse>>
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun removeBookmark(collection: String, bookId: String, storyId: String)
    suspend fun reorderBookmarks(bookmarks: List<Bookmark>)
    suspend fun addSavedVerse(verse: SavedVerse)
    suspend fun removeSavedVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int)
    suspend fun updateVerseHighlight(collection: String, bookId: String, storyId: String, bulletIndex: Int, color: String?)
    suspend fun reorderSavedVerses(verses: List<SavedVerse>)

    val labelsFlow: Flow<List<Label>>
    suspend fun addLabel(label: Label)
    suspend fun removeLabel(id: String)
    suspend fun updateLabel(id: String, name: String, color: String)
    suspend fun addLabelToVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int, labelId: String)
    suspend fun removeLabelFromVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int, labelId: String)

    suspend fun exportBackup(): String
    suspend fun importBackup(jsonData: String): Boolean
}
