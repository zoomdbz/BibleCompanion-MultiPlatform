package com.dividesbyzer0.biblecompanion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

actual class PrefsRepo actual constructor(private val context: PlatformContext) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val VERSION = stringPreferencesKey("translation")
        val READER_MODE = stringPreferencesKey("reader_mode")
        val SHOW_DEUTERO = booleanPreferencesKey("show_deutero")
        val SHOW_APOC = booleanPreferencesKey("show_apoc")
        val SHOW_PSEUDEPIGRAPHA = booleanPreferencesKey("show_pseudepigrapha")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val JESUS_COLOR = stringPreferencesKey("jesus_color")
        val FONT_MODE = stringPreferencesKey("font_mode")
        val TEXT_SIZE_SCALE = floatPreferencesKey("text_size_scale")
        val LAST_READ_COLLECTION = stringPreferencesKey("last_read_collection")
        val LAST_READ_BOOK_ID = stringPreferencesKey("last_read_book_id")
        val LAST_READ_BOOK_TITLE = stringPreferencesKey("last_read_book_title")
        val LAST_READ_STORY_ID = stringPreferencesKey("last_read_story_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val STUDY_PINNED = booleanPreferencesKey("study_pinned")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val DIVINE_NAME = stringPreferencesKey("divine_name")
        val DIVINE_NAME_COLOR = stringPreferencesKey("divine_name_color")
        val FEAST_NOTES_EXPANDED = booleanPreferencesKey("feast_notes_expanded")
        val ORDAINED_FEASTS_EXPANDED = booleanPreferencesKey("ordained_feasts_expanded")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val CUSTOM_THEME_HUE = floatPreferencesKey("custom_theme_hue")
        val SHOW_KEY_TAKEAWAY = booleanPreferencesKey("show_key_takeaway")
        val SHOW_CROSS_REFS = booleanPreferencesKey("show_cross_refs")
        val SHOW_MANUSCRIPT_VARIANTS = booleanPreferencesKey("show_manuscript_variants")
        val SHOW_TRANSLATION_NOTES = booleanPreferencesKey("show_translation_notes")
        val COLLAPSED_STORIES_JSON = stringPreferencesKey("collapsed_stories_json")
        val AUTO_CONTINUE_TTS = booleanPreferencesKey("auto_continue_tts")
        val NOTES_EXPANDED_SECTIONS_JSON = stringPreferencesKey("notes_expanded_sections_json")
        val VOTD_DISMISSED_DATE = stringPreferencesKey("votd_dismissed_date")
        val BOOKMARKS_JSON = stringPreferencesKey("bookmarks_json")
        val SAVED_VERSES_JSON = stringPreferencesKey("saved_verses_json")
        val LABELS_JSON = stringPreferencesKey("labels_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    actual val flow: Flow<PrefsState> = context.dataStore.data.map { p ->
        val effectiveLang = LocaleUtils.effectiveAssetTag(p[Keys.APP_LANGUAGE] ?: "system")
        val stored = p[Keys.VERSION]
        val langDefault = Linker.defaultVersionForLanguage(effectiveLang)
        val resolvedVersion = when {
            stored != null -> stored
            effectiveLang != "en" -> langDefault
            else -> "ESV"
        }

        PrefsState(
            theme = p[Keys.THEME] ?: "System",
            translation = resolvedVersion,
            readerMode = p[Keys.READER_MODE] ?: "biblecom",
            showDeutero = p[Keys.SHOW_DEUTERO] ?: true,
            showApoc = p[Keys.SHOW_APOC] ?: true,
            showPseudepigrapha = p[Keys.SHOW_PSEUDEPIGRAPHA] ?: true,
            appLanguage = p[Keys.APP_LANGUAGE] ?: "system",
            jesusWordsColor = p[Keys.JESUS_COLOR] ?: "default",
            fontMode = p[Keys.FONT_MODE] ?: "sans",
            textSizeScale = p[Keys.TEXT_SIZE_SCALE] ?: 1.0f,
            lastReadCollection = p[Keys.LAST_READ_COLLECTION],
            lastReadBookId = p[Keys.LAST_READ_BOOK_ID],
            lastReadBookTitle = p[Keys.LAST_READ_BOOK_TITLE],
            lastReadStoryId = p[Keys.LAST_READ_STORY_ID],
            onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
            studyPinned = p[Keys.STUDY_PINNED] ?: false,
            themePreset = p[Keys.THEME_PRESET] ?: "parchment",
            divineName = p[Keys.DIVINE_NAME] ?: "traditional",
            divineNameColor = p[Keys.DIVINE_NAME_COLOR] ?: "default",
            feastNotesExpanded = p[Keys.FEAST_NOTES_EXPANDED] ?: true,
            ordainedFeastsExpanded = p[Keys.ORDAINED_FEASTS_EXPANDED] ?: false,
            hapticEnabled = p[Keys.HAPTIC_ENABLED] ?: true,
            customThemeHue = p[Keys.CUSTOM_THEME_HUE] ?: 210f,
            showKeyTakeaway = p[Keys.SHOW_KEY_TAKEAWAY] ?: false,
            showCrossRefs = p[Keys.SHOW_CROSS_REFS] ?: false,
            showManuscriptVariants = p[Keys.SHOW_MANUSCRIPT_VARIANTS] ?: false,
            showTranslationNotes = p[Keys.SHOW_TRANSLATION_NOTES] ?: false,
            collapsedStoriesJson = p[Keys.COLLAPSED_STORIES_JSON] ?: "{}",
            autoContinueTts = p[Keys.AUTO_CONTINUE_TTS] ?: true,
            notesExpandedSectionsJson = p[Keys.NOTES_EXPANDED_SECTIONS_JSON] ?: "{}",
            votdDismissedDate = p[Keys.VOTD_DISMISSED_DATE] ?: ""
        )
    }

    actual fun initialSnapshot(): PrefsState = runBlocking { flow.first() }

    actual suspend fun setTheme(theme: String) =
        context.dataStore.edit { it[Keys.THEME] = theme }.let { Unit }

    actual suspend fun setVersion(version: String) =
        context.dataStore.edit { it[Keys.VERSION] = version }.let { Unit }

    actual suspend fun setReaderMode(mode: String) =
        context.dataStore.edit { it[Keys.READER_MODE] = mode }.let { Unit }

    actual suspend fun setDeutero(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_DEUTERO] = show }.let { Unit }

    actual suspend fun setApoc(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_APOC] = show }.let { Unit }

    actual suspend fun setPseudepigrapha(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_PSEUDEPIGRAPHA] = show }.let { Unit }

    actual suspend fun setAppLanguage(tag: String) =
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = tag }.let { Unit }

    actual suspend fun setJesusWordsColor(colorKey: String) =
        context.dataStore.edit { it[Keys.JESUS_COLOR] = colorKey }.let { Unit }

    actual suspend fun setFontMode(mode: String) =
        context.dataStore.edit { it[Keys.FONT_MODE] = mode }.let { Unit }

    actual suspend fun setTextSizeScale(scale: Float) =
        context.dataStore.edit { it[Keys.TEXT_SIZE_SCALE] = scale }.let { Unit }

    actual suspend fun setLastRead(collection: String, bookId: String, bookTitle: String, storyId: String?) =
        context.dataStore.edit {
            it[Keys.LAST_READ_COLLECTION] = collection
            it[Keys.LAST_READ_BOOK_ID] = bookId
            it[Keys.LAST_READ_BOOK_TITLE] = bookTitle
            if (storyId != null) it[Keys.LAST_READ_STORY_ID] = storyId
            else it.remove(Keys.LAST_READ_STORY_ID)
        }.let { Unit }

    actual suspend fun setOnboardingComplete(complete: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }.let { Unit }

    actual suspend fun setStudyPinned(pinned: Boolean) =
        context.dataStore.edit { it[Keys.STUDY_PINNED] = pinned }.let { Unit }

    actual suspend fun setThemePreset(preset: String) =
        context.dataStore.edit { it[Keys.THEME_PRESET] = preset }.let { Unit }

    actual suspend fun setDivineName(mode: String) =
        context.dataStore.edit { it[Keys.DIVINE_NAME] = mode }.let { Unit }

    actual suspend fun setDivineNameColor(colorKey: String) =
        context.dataStore.edit { it[Keys.DIVINE_NAME_COLOR] = colorKey }.let { Unit }

    actual suspend fun setFeastNotesExpanded(expanded: Boolean) =
        context.dataStore.edit { it[Keys.FEAST_NOTES_EXPANDED] = expanded }.let { Unit }

    actual suspend fun setOrdainedFeastsExpanded(expanded: Boolean) =
        context.dataStore.edit { it[Keys.ORDAINED_FEASTS_EXPANDED] = expanded }.let { Unit }

    actual suspend fun setHapticEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }.let { Unit }

    actual suspend fun setCustomThemeHue(hue: Float) =
        context.dataStore.edit { it[Keys.CUSTOM_THEME_HUE] = hue }.let { Unit }

    actual suspend fun setShowKeyTakeaway(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_KEY_TAKEAWAY] = show }.let { Unit }

    actual suspend fun setShowCrossRefs(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_CROSS_REFS] = show }.let { Unit }

    actual suspend fun setShowManuscriptVariants(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_MANUSCRIPT_VARIANTS] = show }.let { Unit }

    actual suspend fun setShowTranslationNotes(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_TRANSLATION_NOTES] = show }.let { Unit }

    actual suspend fun setCollapsedStories(json: String) =
        context.dataStore.edit { it[Keys.COLLAPSED_STORIES_JSON] = json }.let { Unit }

    actual suspend fun setAutoContinueTts(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_CONTINUE_TTS] = enabled }.let { Unit }

    actual suspend fun setNotesExpandedSections(json: String) =
        context.dataStore.edit { it[Keys.NOTES_EXPANDED_SECTIONS_JSON] = json }.let { Unit }

    actual suspend fun setVotdDismissedDate(date: String) =
        context.dataStore.edit { it[Keys.VOTD_DISMISSED_DATE] = date }.let { Unit }

    actual val bookmarksFlow: Flow<List<Bookmark>> = context.dataStore.data.map { p ->
        val raw = p[Keys.BOOKMARKS_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Bookmark>>(raw) }.getOrDefault(emptyList())
    }

    actual val savedVersesFlow: Flow<List<SavedVerse>> = context.dataStore.data.map { p ->
        val raw = p[Keys.SAVED_VERSES_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<SavedVerse>>(raw) }.getOrDefault(emptyList())
    }

    actual suspend fun addBookmark(bookmark: Bookmark) {
        context.dataStore.edit { p ->
            val list = p[Keys.BOOKMARKS_JSON]?.let {
                runCatching { json.decodeFromString<List<Bookmark>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val filtered = list.filter {
                !(it.collection == bookmark.collection && it.bookId == bookmark.bookId && it.storyId == bookmark.storyId)
            }
            p[Keys.BOOKMARKS_JSON] = json.encodeToString(filtered + bookmark)
        }
    }

    actual suspend fun removeBookmark(collection: String, bookId: String, storyId: String) {
        context.dataStore.edit { p ->
            val list = p[Keys.BOOKMARKS_JSON]?.let {
                runCatching { json.decodeFromString<List<Bookmark>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.BOOKMARKS_JSON] = json.encodeToString(
                list.filter { !(it.collection == collection && it.bookId == bookId && it.storyId == storyId) }
            )
        }
    }

    actual suspend fun addSavedVerse(verse: SavedVerse) {
        context.dataStore.edit { p ->
            val list = p[Keys.SAVED_VERSES_JSON]?.let {
                runCatching { json.decodeFromString<List<SavedVerse>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val filtered = list.filter {
                !(it.collection == verse.collection && it.bookId == verse.bookId &&
                  it.storyId == verse.storyId && it.bulletIndex == verse.bulletIndex)
            }
            p[Keys.SAVED_VERSES_JSON] = json.encodeToString(filtered + verse)
        }
    }

    actual suspend fun removeSavedVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int) {
        context.dataStore.edit { p ->
            val list = p[Keys.SAVED_VERSES_JSON]?.let {
                runCatching { json.decodeFromString<List<SavedVerse>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.SAVED_VERSES_JSON] = json.encodeToString(
                list.filter { !(it.collection == collection && it.bookId == bookId &&
                    it.storyId == storyId && it.bulletIndex == bulletIndex) }
            )
        }
    }

    actual suspend fun updateVerseHighlight(collection: String, bookId: String, storyId: String, bulletIndex: Int, color: String?) {
        context.dataStore.edit { p ->
            val list = p[Keys.SAVED_VERSES_JSON]?.let {
                runCatching { json.decodeFromString<List<SavedVerse>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.SAVED_VERSES_JSON] = json.encodeToString(
                list.map {
                    if (it.collection == collection && it.bookId == bookId &&
                        it.storyId == storyId && it.bulletIndex == bulletIndex
                    ) it.copy(highlightColor = color) else it
                }
            )
        }
    }

    actual suspend fun reorderSavedVerses(verses: List<SavedVerse>) {
        context.dataStore.edit { p ->
            p[Keys.SAVED_VERSES_JSON] = json.encodeToString(verses)
        }
    }

    actual val labelsFlow: Flow<List<Label>> = context.dataStore.data.map { p ->
        val raw = p[Keys.LABELS_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Label>>(raw) }.getOrDefault(emptyList())
    }

    actual suspend fun addLabel(label: Label) {
        context.dataStore.edit { p ->
            val list = p[Keys.LABELS_JSON]?.let {
                runCatching { json.decodeFromString<List<Label>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val filtered = list.filter { it.id != label.id }
            p[Keys.LABELS_JSON] = json.encodeToString(filtered + label)
        }
    }

    actual suspend fun removeLabel(id: String) {
        context.dataStore.edit { p ->
            val list = p[Keys.LABELS_JSON]?.let {
                runCatching { json.decodeFromString<List<Label>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.LABELS_JSON] = json.encodeToString(list.filter { it.id != id })
            val verses = p[Keys.SAVED_VERSES_JSON]?.let {
                runCatching { json.decodeFromString<List<SavedVerse>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.SAVED_VERSES_JSON] = json.encodeToString(
                verses.map { it.copy(labels = it.labels.filter { l -> l != id }) }
            )
        }
    }

    actual suspend fun updateLabel(id: String, name: String, color: String) {
        context.dataStore.edit { p ->
            val list = p[Keys.LABELS_JSON]?.let {
                runCatching { json.decodeFromString<List<Label>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.LABELS_JSON] = json.encodeToString(
                list.map { if (it.id == id) it.copy(name = name, color = color) else it }
            )
        }
    }

    actual suspend fun addLabelToVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int, labelId: String) {
        context.dataStore.edit { p ->
            val list = p[Keys.SAVED_VERSES_JSON]?.let {
                runCatching { json.decodeFromString<List<SavedVerse>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.SAVED_VERSES_JSON] = json.encodeToString(
                list.map {
                    if (it.collection == collection && it.bookId == bookId &&
                        it.storyId == storyId && it.bulletIndex == bulletIndex &&
                        labelId !in it.labels
                    ) it.copy(labels = it.labels + labelId) else it
                }
            )
        }
    }

    actual suspend fun removeLabelFromVerse(collection: String, bookId: String, storyId: String, bulletIndex: Int, labelId: String) {
        context.dataStore.edit { p ->
            val list = p[Keys.SAVED_VERSES_JSON]?.let {
                runCatching { json.decodeFromString<List<SavedVerse>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            p[Keys.SAVED_VERSES_JSON] = json.encodeToString(
                list.map {
                    if (it.collection == collection && it.bookId == bookId &&
                        it.storyId == storyId && it.bulletIndex == bulletIndex
                    ) it.copy(labels = it.labels.filter { l -> l != labelId }) else it
                }
            )
        }
    }

    actual suspend fun exportBackup(): String {
        val p = context.dataStore.data.first()
        val bookmarks = p[Keys.BOOKMARKS_JSON]?.let {
            runCatching { json.decodeFromString<List<Bookmark>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        val verses = p[Keys.SAVED_VERSES_JSON]?.let {
            runCatching { json.decodeFromString<List<SavedVerse>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        val labels = p[Keys.LABELS_JSON]?.let {
            runCatching { json.decodeFromString<List<Label>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        val backup = AppBackup(
            timestamp = System.currentTimeMillis(),
            bookmarks = bookmarks,
            savedVerses = verses,
            labels = labels
        )
        return json.encodeToString(backup)
    }

    actual suspend fun importBackup(jsonData: String): Boolean {
        return runCatching {
            val backup = json.decodeFromString<AppBackup>(jsonData)
            context.dataStore.edit { p ->
                p[Keys.BOOKMARKS_JSON] = json.encodeToString(backup.bookmarks)
                p[Keys.SAVED_VERSES_JSON] = json.encodeToString(backup.savedVerses)
                p[Keys.LABELS_JSON] = json.encodeToString(backup.labels)
            }
            true
        }.getOrDefault(false)
    }
}
