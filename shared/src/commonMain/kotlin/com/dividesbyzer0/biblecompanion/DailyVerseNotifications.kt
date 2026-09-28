package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface DailyVerseNotificationPermissionResult {
  fun complete(granted: Boolean)
}

interface DailyVerseNotificationHost {
  fun requestPermission(result: DailyVerseNotificationPermissionResult)
  fun checkPermission(result: DailyVerseNotificationPermissionResult)
  fun synchronize(prefs: PrefsState)
  fun openSettings()
}

/** Native hosts install permission/scheduling operations before creating AppRoot. */
object DailyVerseNotificationBridge {
  private var host: DailyVerseNotificationHost? = null
  private val _refresh = MutableStateFlow(0L)
  val refresh: StateFlow<Long> = _refresh
  private val _permissionAllowed = MutableStateFlow<Boolean?>(null)
  val permissionAllowed: StateFlow<Boolean?> = _permissionAllowed
  private val _permissionRequestDenied = MutableStateFlow(false)
  val permissionRequestDenied: StateFlow<Boolean> = _permissionRequestDenied

  fun clearPermissionRequestFeedback() { _permissionRequestDenied.value = false }
  fun permissionRequestFinished(granted: Boolean) {
    _permissionAllowed.value = granted
    _permissionRequestDenied.value = !granted
  }

  fun install(host: DailyVerseNotificationHost) {
    this.host = host
    refreshAfterResume()
  }

  fun requestPermission(result: (Boolean) -> Unit) {
    clearPermissionRequestFeedback()
    val native = host
    if (native == null) {
      permissionRequestFinished(false)
      result(false)
    } else native.requestPermission(object : DailyVerseNotificationPermissionResult {
      override fun complete(granted: Boolean) {
        permissionRequestFinished(granted)
        result(granted)
      }
    })
  }

  fun synchronize(prefs: PrefsState) {
    host?.checkPermission(object : DailyVerseNotificationPermissionResult {
      override fun complete(granted: Boolean) { _permissionAllowed.value = granted }
    })
    host?.synchronize(prefs)
  }
  fun openSettings() { host?.openSettings() }
  fun refreshAfterResume() { _refresh.value += 1L }
}

@Serializable
data class DailyVerseNotificationLabels(
  val title: String,
  // Avoid NSObject.copy() in Swift while preserving the bundled JSON keys.
  @SerialName("copy") val copyActionTitle: String,
  @SerialName("share") val shareActionTitle: String,
  val copied: String
)

@Serializable
data class DailyVerseNotificationContent(
  val title: String,
  val text: String,
  val reference: String,
  val route: String,
  val language: String,
  val edition: String,
  val copyLabel: String,
  val shareLabel: String,
  val copiedLabel: String
) {
  val shareText: String get() = "$text\n$reference"
}

object DailyVerseNotifications {
  private val json = Json { ignoreUnknownKeys = true }

  fun labels(context: PlatformContext, language: String): DailyVerseNotificationLabels {
    val tag = LocaleUtils.effectiveAssetTag(language)
    val raw = readAssetText(context, "notifications/$tag.json")
      ?: readAssetText(context, "notifications/en.json")
      ?: error("Missing daily notification labels")
    return json.decodeFromString(raw)
  }

  /** Returns null rather than inventing a destination for an unrecognized verse. */
  fun forDate(
    context: PlatformContext, prefs: PrefsState, year: Int, month: Int, day: Int
  ): DailyVerseNotificationContent? {
    val language = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
    val verse = VerseOfTheDay.forDate(context, language, prefs.internalBibleVersion, year, month, day)
    if (verse.text.isBlank()) return null
    val match = Regex("^(.+?)\\s+(\\d+):(\\d+)(?:-(\\d+))?$").matchEntire(verse.ref) ?: return null
    val name = match.groupValues[1].let { if (it.equals("Acts of the Apostles", true)) "Acts" else it }
    val chapter = match.groupValues[2].toIntOrNull() ?: return null
    val first = match.groupValues[3].toIntOrNull() ?: return null
    val last = match.groupValues[4].toIntOrNull() ?: first
    if (chapter < 1 || first < 1 || last < first) return null
    val edition = verse.editionId ?: BibleEditions.defaultForLanguage(language)
    val labels = labels(context, language)
    for (collection in listOf("old_testament", "new_testament", "deuterocanonical")) {
      val bookId = ContentRepo.listBooksLocalized(context, collection, "en")
        .firstOrNull { (_, title) -> title.equals(name, ignoreCase = true) }?.first ?: continue
      val loaded = ContentRepo.loadBookWithEdition(context, collection, bookId, language, edition) ?: continue
      if (loaded.effectiveEdition != edition) continue
      val book = loaded.book
      val story = ChapterLocator.build(book).byChapter[chapter] ?: continue
      val target = book.stories.firstOrNull { it.id == story } ?: continue
      if (!notificationAnchorExists(target.summaryBullets, chapter, first, last)) continue
      val title = ContentRepo.listBooksLocalized(context, collection, language)
        .firstOrNull { it.first == bookId }?.second ?: book.title
      val reference = "$title $chapter:$first" + if (last == first) "" else "-$last"
      val text = notificationPlainText(verse.text, prefs.divineName, language, collection)
      return DailyVerseNotificationContent(
        labels.title, text, reference,
        Dest.BookView.route(collection, bookId, story, verse = first, verseEnd = last,
          sourceLang = language, sourceEdition = edition),
        language, edition, labels.copyActionTitle, labels.shareActionTitle, labels.copied
      )
    }
    return null
  }

  // A JSON boundary keeps Swift independent of Kotlin collection bridging.
  fun jsonForDate(context: PlatformContext, prefs: PrefsState, year: Int, month: Int, day: Int): String? =
    forDate(context, prefs, year, month, day)?.let { json.encodeToString(it) }
}

internal fun notificationPlainText(text: String, divineName: String, language: String, collection: String): String =
  stripScriptureInlineTags(applyDivineName(text, divineName, language, false, collection)).trim()

internal fun notificationAnchorExists(bullets: List<String>, chapter: Int, first: Int, last: Int): Boolean {
  if (chapter < 1 || first < 1 || last < first) return false
  val units = bullets.mapNotNull(::verseAnchorFromText).filter { it.chapter == chapter }
  return (first..last).all { number -> units.any { number in it.verseStart..it.verseEnd } }
}
