package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.platformCurrentDate
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class DailyVerse(
  val text: String,
  val ref: String,
  val isFeastOverride: Boolean = false,
  val editionId: String? = null
)

@Serializable
data class VerseEntry(val text: String, val ref: String)

@Serializable
data class DailyVersesFile(val verses: List<VerseEntry> = emptyList())

@Serializable
data class FeastVersesFile(val feastVerses: Map<String, VerseEntry> = emptyMap())

object VerseOfTheDay {

  private val json = Json { ignoreUnknownKeys = true }
  // The home screen and Android's notification worker can read concurrently.
  private val dailyCache = MutableStateFlow<Map<String, DailyVersesFile>>(emptyMap())
  private val feastCache = MutableStateFlow<Map<String, FeastVersesFile>>(emptyMap())

  fun todayVerse(
    context: PlatformContext,
    appLang: String,
    internalBibleVersion: String = BibleEditions.BSB
  ): DailyVerse {
    val (year, month, day) = platformCurrentDate()
    return forDate(context, appLang, internalBibleVersion, year, month, day)
  }

  /** Uses the same calendar, feast overrides, and edition mapping for reminders. */
  fun forDate(
    context: PlatformContext,
    appLang: String,
    internalBibleVersion: String,
    year: Int,
    month: Int,
    day: Int
  ): DailyVerse {
    val tag = LocaleUtils.effectiveAssetTag(appLang)

    val feasts = loadFeasts(context, tag)
    val feastOverride = checkFeastOverride(year, month, day, feasts)
    if (feastOverride != null) {
      return resolveInternalEdition(context, tag, internalBibleVersion, feastOverride)
    }

    val daily = loadDaily(context, tag)
    if (daily.verses.isEmpty()) return DailyVerse("", "")

    val dayOfYear = dayOfYear(year, month, day)
    // The bank is sorted by bible.com's day-of-year calendar (verses[0] = day 1, ...).
    // Index by day-of-year so the app shows the same verse bible.com shows that day.
    val size = daily.verses.size
    val index = ((dayOfYear - 1) % size + size) % size
    val entry = daily.verses[index]
    return resolveInternalEdition(
      context,
      tag,
      internalBibleVersion,
      DailyVerse(entry.text, entry.ref)
    )
  }

  /**
   * Daily banks define the calendar and localized reference. When an alternate
   * in-app edition is selected, resolve that reference from its overlay so the
   * home card changes with the rest of the reader.
   */
  private fun resolveInternalEdition(
    context: PlatformContext,
    effectiveLanguage: String,
    internalBibleVersion: String,
    dailyVerse: DailyVerse
  ): DailyVerse {
    val requestedEdition = BibleEditions.effective(effectiveLanguage, internalBibleVersion)
    val baseEdition = BibleEditions.defaultForLanguage(effectiveLanguage)
    val original = dailyVerse.copy(editionId = baseEdition)
    if (requestedEdition == baseEdition) return original

    val match = Regex("^(.+?)\\s+(\\d+):(\\d+)(?:-(\\d+))?$").matchEntire(dailyVerse.ref) ?: return original
    val rawBookName = match.groupValues[1].trim()
    val bookName = when (rawBookName.lowercase()) {
      "acts of the apostles" -> "Acts"
      else -> rawBookName
    }
    val chapter = match.groupValues[2].toIntOrNull() ?: return original
    val verse = match.groupValues[3].toIntOrNull() ?: return original
    val verseEnd = match.groupValues[4].toIntOrNull() ?: verse

    for (collection in listOf("old_testament", "new_testament", "deuterocanonical")) {
      // Daily-reference anchors intentionally use stable English book names in
      // every language; the displayed reference itself is localized elsewhere.
      val bookId = ContentRepo.listBooksLocalized(context, collection, "en")
        .firstOrNull { (_, title) -> title.equals(bookName, ignoreCase = true) }
        ?.first ?: continue
      val mapped = EditionReferenceMaps.resolve(
        context, effectiveLanguage, bookId, baseEdition, requestedEdition,
        VerseAnchor(chapter, verse, verseEnd)
      ) ?: return original
      val raw = readAssetText(
        context,
        "books/editions/$effectiveLanguage/$requestedEdition/$collection/$bookId.json"
      ) ?: return original
      val overlay = runCatching { json.decodeFromString<EditionBookOverlay>(raw) }.getOrNull()
        ?: return original
      if (!overlay.isStructurallyValid(
          expectedEditionId = requestedEdition,
          expectedLanguage = effectiveLanguage,
          expectedCollection = collection,
          expectedBookId = bookId
        )) return original
      val units = overlay.chapters.firstOrNull { it.number == mapped.chapter }
        ?.verses?.filter { it.verse <= mapped.verseEnd && (it.verseEnd ?: it.verse) >= mapped.verseStart }
        ?.takeIf { it.isNotEmpty() } ?: return original
      if (!(mapped.verseStart..mapped.verseEnd).all { number ->
          units.any { number in it.verse..(it.verseEnd ?: it.verse) }
        }) return original
      val first = units.first().verse
      val last = units.last().let { it.verseEnd ?: it.verse }
      val tail = "${mapped.chapter}:$first" + if (last != first) "-$last" else ""
      return dailyVerse.copy(
        text = units.joinToString(" ") { it.text },
        ref = "$rawBookName $tail",
        editionId = requestedEdition
      )
    }
    return original
  }

  private fun loadDaily(context: PlatformContext, lang: String): DailyVersesFile {
    dailyCache.value[lang]?.let { return it }
    val loaded = readAssetText(context, "daily_verses/$lang/daily.json")
      ?.let { runCatching { json.decodeFromString<DailyVersesFile>(it) }.getOrNull() }
      ?: readAssetText(context, "daily_verses/en/daily.json")
        ?.let { runCatching { json.decodeFromString<DailyVersesFile>(it) }.getOrNull() }
      ?: DailyVersesFile(emptyList())
    dailyCache.update { it + (lang to loaded) }
    return loaded
  }

  private fun loadFeasts(context: PlatformContext, lang: String): FeastVersesFile {
    feastCache.value[lang]?.let { return it }
    val loaded = readAssetText(context, "daily_verses/$lang/feasts.json")
      ?.let { runCatching { json.decodeFromString<FeastVersesFile>(it) }.getOrNull() }
      ?: readAssetText(context, "daily_verses/en/feasts.json")
        ?.let { runCatching { json.decodeFromString<FeastVersesFile>(it) }.getOrNull() }
      ?: FeastVersesFile(emptyMap())
    feastCache.update { it + (lang to loaded) }
    return loaded
  }

  private fun checkFeastOverride(
    year: Int, month: Int, day: Int, feasts: FeastVersesFile
  ): DailyVerse? {
    if (feasts.feastVerses.isEmpty()) return null
    val jdn = HebrewCalendar.gregorianToJDN(year, month, day)
    val hDate = HebrewCalendar.jdnToHebrew(jdn)
    val hebrewFeasts = HebrewCalendar.hebrewFeastsForYear(hDate.year)
    for ((feastJdn, marker) in hebrewFeasts) {
      if (feastJdn == jdn) {
        val v = feasts.feastVerses[marker.id] ?: continue
        return DailyVerse(v.text, v.ref, isFeastOverride = true)
      }
    }
    return null
  }

  private fun dayOfYear(year: Int, month: Int, day: Int): Int {
    val dim = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) dim[2] = 29
    var doy = day
    for (m in 1 until month) doy += dim[m]
    return doy
  }
}
