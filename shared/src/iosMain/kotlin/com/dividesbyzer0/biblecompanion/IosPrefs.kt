package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual class PrefsRepo actual constructor(context: PlatformContext) {

    private val defaults = NSUserDefaults.standardUserDefaults

    private fun getString(key: String): String? = defaults.stringForKey(key)
    private fun getBool(key: String, default: Boolean): Boolean {
        return if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else default
    }
    private fun getFloat(key: String, default: Float): Float {
        return if (defaults.objectForKey(key) != null) defaults.floatForKey(key) else default
    }

    private fun buildState(): PrefsState {
        val effectiveLang = LocaleUtils.effectiveAssetTag(getString("app_language") ?: "system")
        val stored = getString("translation")
        val langDefault = Linker.defaultVersionForLanguage(effectiveLang)
        val resolvedVersion = when {
            stored != null -> stored
            effectiveLang != "en" -> langDefault
            else -> "ESV"
        }
        return PrefsState(
            theme = getString("theme") ?: "System",
            translation = resolvedVersion,
            readerMode = getString("reader_mode") ?: "biblecom",
            showDeutero = getBool("show_deutero", true),
            showApoc = getBool("show_apoc", true),
            showPseudepigrapha = getBool("show_pseudepigrapha", true),
            appLanguage = getString("app_language") ?: "system",
            jesusWordsColor = getString("jesus_color") ?: "default",
            fontMode = getString("font_mode") ?: "sans",
            textSizeScale = getFloat("text_size_scale", 1.0f),
            lastReadCollection = getString("last_read_collection"),
            lastReadBookId = getString("last_read_book_id"),
            lastReadBookTitle = getString("last_read_book_title"),
            lastReadStoryId = getString("last_read_story_id"),
            onboardingComplete = getBool("onboarding_complete", false),
            studyPinned = getBool("study_pinned", false),
            themePreset = getString("theme_preset") ?: "parchment",
            divineName = getString("divine_name") ?: "traditional",
            divineNameColor = getString("divine_name_color") ?: "default",
            feastNotesExpanded = getBool("feast_notes_expanded", true),
            ordainedFeastsExpanded = getBool("ordained_feasts_expanded", false),
            hapticEnabled = getBool("haptic_enabled", true),
            customThemeHue = getFloat("custom_theme_hue", 210f),
            expandNotesDefault = getBool("expand_notes_default", false),
            collapsedStoriesJson = getString("collapsed_stories_json") ?: "{}",
            autoContinueTts = getBool("auto_continue_tts", true),
            crossBookTts = getBool("cross_book_tts", false),
            notesExpandedSectionsJson = getString("notes_expanded_sections_json") ?: "{}",
            votdDismissedDate = getString("votd_dismissed_date") ?: "",
            screenshotExpandLanguage = getBool("ss_expand_language", false),
            aiSearch = getBool("ai_search", true)
        )
    }

    private val _state = MutableStateFlow(buildState())

    actual val flow: Flow<PrefsState> = _state

    actual fun initialSnapshot(): PrefsState = buildState()

    private fun refresh() { _state.value = buildState() }

    actual suspend fun setTheme(theme: String) {
        defaults.setObject(theme, forKey = "theme"); refresh()
    }
    actual suspend fun setVersion(version: String) {
        defaults.setObject(version, forKey = "translation"); refresh()
    }
    actual suspend fun setReaderMode(mode: String) {
        defaults.setObject(mode, forKey = "reader_mode"); refresh()
    }
    actual suspend fun setDeutero(show: Boolean) {
        defaults.setBool(show, forKey = "show_deutero"); refresh()
    }
    actual suspend fun setApoc(show: Boolean) {
        defaults.setBool(show, forKey = "show_apoc"); refresh()
    }
    actual suspend fun setPseudepigrapha(show: Boolean) {
        defaults.setBool(show, forKey = "show_pseudepigrapha"); refresh()
    }
    actual suspend fun setAppLanguage(tag: String) {
        defaults.setObject(tag, forKey = "app_language"); refresh()
    }
    actual suspend fun setJesusWordsColor(colorKey: String) {
        defaults.setObject(colorKey, forKey = "jesus_color"); refresh()
    }
    actual suspend fun setFontMode(mode: String) {
        defaults.setObject(mode, forKey = "font_mode"); refresh()
    }
    actual suspend fun setTextSizeScale(scale: Float) {
        defaults.setFloat(scale, forKey = "text_size_scale"); refresh()
    }
    actual suspend fun setLastRead(collection: String, bookId: String, bookTitle: String, storyId: String?) {
        defaults.setObject(collection, forKey = "last_read_collection")
        defaults.setObject(bookId, forKey = "last_read_book_id")
        defaults.setObject(bookTitle, forKey = "last_read_book_title")
        if (storyId != null) defaults.setObject(storyId, forKey = "last_read_story_id")
        else defaults.removeObjectForKey("last_read_story_id")
        refresh()
    }
    actual suspend fun setOnboardingComplete(complete: Boolean) {
        defaults.setBool(complete, forKey = "onboarding_complete"); refresh()
    }
    actual suspend fun setStudyPinned(pinned: Boolean) {
        defaults.setBool(pinned, forKey = "study_pinned"); refresh()
    }
    actual suspend fun setThemePreset(preset: String) {
        defaults.setObject(preset, forKey = "theme_preset"); refresh()
    }
    actual suspend fun setDivineName(mode: String) {
        defaults.setObject(mode, forKey = "divine_name"); refresh()
    }
    actual suspend fun setDivineNameColor(colorKey: String) {
        defaults.setObject(colorKey, forKey = "divine_name_color"); refresh()
    }
    actual suspend fun setFeastNotesExpanded(expanded: Boolean) {
        defaults.setBool(expanded, forKey = "feast_notes_expanded"); refresh()
    }
    actual suspend fun setOrdainedFeastsExpanded(expanded: Boolean) {
        defaults.setBool(expanded, forKey = "ordained_feasts_expanded"); refresh()
    }
    actual suspend fun setHapticEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = "haptic_enabled"); refresh()
    }
    actual suspend fun setCustomThemeHue(hue: Float) {
        defaults.setFloat(hue, forKey = "custom_theme_hue"); refresh()
    }
    actual suspend fun setExpandNotesDefault(expand: Boolean) {
        defaults.setBool(expand, forKey = "expand_notes_default"); refresh()
    }
    actual suspend fun setCrossBookTts(enabled: Boolean) {
        defaults.setBool(enabled, forKey = "cross_book_tts"); refresh()
    }
    actual suspend fun setCollapsedStories(json: String) {
        defaults.setObject(json, forKey = "collapsed_stories_json"); refresh()
    }
    actual suspend fun setAutoContinueTts(enabled: Boolean) {
        defaults.setBool(enabled, forKey = "auto_continue_tts"); refresh()
    }
    actual suspend fun setNotesExpandedSections(json: String) {
        defaults.setObject(json, forKey = "notes_expanded_sections_json"); refresh()
    }
    actual suspend fun setVotdDismissedDate(date: String) {
        defaults.setObject(date, forKey = "votd_dismissed_date"); refresh()
    }
    actual suspend fun setAiSearch(enabled: Boolean) {
        defaults.setBool(enabled, forKey = "ai_search"); refresh()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _bookmarks = MutableStateFlow(loadBookmarks())
    private val _savedVerses = MutableStateFlow(loadSavedVerses())

    actual val bookmarksFlow: Flow<List<Bookmark>> = _bookmarks
    actual val savedVersesFlow: Flow<List<SavedVerse>> = _savedVerses

    private fun loadBookmarks(): List<Bookmark> {
        val raw = getString("bookmarks_json") ?: return emptyList()
        return runCatching { json.decodeFromString<List<Bookmark>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadSavedVerses(): List<SavedVerse> {
        val raw = getString("saved_verses_json") ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedVerse>>(raw) }.getOrDefault(emptyList())
    }

    private fun persistBookmarks(list: List<Bookmark>) {
        defaults.setObject(json.encodeToString(list), forKey = "bookmarks_json")
        _bookmarks.value = list
    }

    private fun persistSavedVerses(list: List<SavedVerse>) {
        defaults.setObject(json.encodeToString(list), forKey = "saved_verses_json")
        _savedVerses.value = list
    }

    actual suspend fun addBookmark(bookmark: Bookmark) {
        val list = loadBookmarks().filter {
            !(it.collection == bookmark.collection && it.bookId == bookmark.bookId && it.storyId == bookmark.storyId)
        }
        persistBookmarks(list + bookmark)
    }

    actual suspend fun removeBookmark(collection: String, bookId: String, storyId: String) {
        persistBookmarks(loadBookmarks().filter {
            !(it.collection == collection && it.bookId == bookId && it.storyId == storyId)
        })
    }

    actual suspend fun addSavedVerse(verse: SavedVerse) {
        val list = loadSavedVerses().filter {
            !(it.collection == verse.collection && it.bookId == verse.bookId &&
              it.storyId == verse.storyId && it.bulletIndex == verse.bulletIndex)
        }
        persistSavedVerses(list + verse)
    }

    actual suspend fun removeSavedVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int) {
        persistSavedVerses(loadSavedVerses().filter {
            !(it.collection == collection && it.bookId == bookId &&
              it.storyId == storyId && it.bulletIndex == bulletIndex)
        })
    }

    actual suspend fun updateVerseHighlight(collection: String, bookId: String, storyId: String, bulletIndex: Int, color: String?) {
        persistSavedVerses(loadSavedVerses().map {
            if (it.collection == collection && it.bookId == bookId &&
                it.storyId == storyId && it.bulletIndex == bulletIndex
            ) it.copy(highlightColor = color) else it
        })
    }

    private val _labels = MutableStateFlow(loadLabels())
    actual val labelsFlow: Flow<List<Label>> = _labels

    private fun loadLabels(): List<Label> {
        val raw = getString("labels_json") ?: return emptyList()
        return runCatching { json.decodeFromString<List<Label>>(raw) }.getOrDefault(emptyList())
    }

    private fun persistLabels(list: List<Label>) {
        defaults.setObject(json.encodeToString(list), forKey = "labels_json")
        _labels.value = list
    }

    actual suspend fun addLabel(label: Label) {
        val list = loadLabels().filter { it.id != label.id }
        persistLabels(list + label)
    }

    actual suspend fun removeLabel(id: String) {
        persistLabels(loadLabels().filter { it.id != id })
        persistSavedVerses(loadSavedVerses().map {
            it.copy(labels = it.labels.filter { l -> l != id })
        })
    }

    actual suspend fun updateLabel(id: String, name: String, color: String) {
        persistLabels(loadLabels().map {
            if (it.id == id) it.copy(name = name, color = color) else it
        })
    }

    actual suspend fun addLabelToVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int, labelId: String) {
        persistSavedVerses(loadSavedVerses().map {
            if (it.collection == collection && it.bookId == bookId &&
                it.storyId == storyId && it.bulletIndex == bulletIndex &&
                labelId !in it.labels
            ) it.copy(labels = it.labels + labelId) else it
        })
    }

    actual suspend fun removeLabelFromVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int, labelId: String) {
        persistSavedVerses(loadSavedVerses().map {
            if (it.collection == collection && it.bookId == bookId &&
                it.storyId == storyId && it.bulletIndex == bulletIndex
            ) it.copy(labels = it.labels.filter { l -> l != labelId }) else it
        })
    }

    actual suspend fun reorderSavedVerses(verses: List<SavedVerse>) {
        persistSavedVerses(verses)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun exportBackup(): String {
        val backup = AppBackup(
            timestamp = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong(),
            bookmarks = loadBookmarks(),
            savedVerses = loadSavedVerses(),
            labels = loadLabels()
        )
        return json.encodeToString(backup)
    }

    actual suspend fun importBackup(jsonData: String): Boolean {
        return runCatching {
            val backup = json.decodeFromString<AppBackup>(jsonData)
            persistBookmarks(backup.bookmarks)
            persistSavedVerses(backup.savedVerses)
            persistLabels(backup.labels)
            refresh()
            true
        }.getOrDefault(false)
    }
}
