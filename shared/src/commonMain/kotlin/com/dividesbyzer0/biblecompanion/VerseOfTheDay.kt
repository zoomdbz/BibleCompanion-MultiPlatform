package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.platformCurrentDate
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DailyVerse(
  val text: String,
  val ref: String,
  val isFeastOverride: Boolean = false
)

@Serializable
data class VerseEntry(val text: String, val ref: String)

@Serializable
data class DailyVersesFile(val verses: List<VerseEntry> = emptyList())

@Serializable
data class FeastVersesFile(val feastVerses: Map<String, VerseEntry> = emptyMap())

object VerseOfTheDay {

  private val json = Json { ignoreUnknownKeys = true }
  private val dailyCache = mutableMapOf<String, DailyVersesFile>()
  private val feastCache = mutableMapOf<String, FeastVersesFile>()

  fun todayVerse(
    context: PlatformContext,
    appLang: String,
    internalBibleVersion: String = BibleEditions.BSB
  ): DailyVerse {
    val tag = LocaleUtils.effectiveAssetTag(appLang)
    val (year, month, day) = platformCurrentDate()

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
   * Daily banks define the calendar and localized reference. When English KJV
   * is selected, resolve that reference from the KJV overlay so the home card
   * changes with the rest of the in-app Bible.
   */
  private fun resolveInternalEdition(
    context: PlatformContext,
    effectiveLanguage: String,
    internalBibleVersion: String,
    dailyVerse: DailyVerse
  ): DailyVerse {
    if (!BibleEditions.isKjv(effectiveLanguage, internalBibleVersion)) return dailyVerse

    val match = Regex("^(.+?)\\s+(\\d+):(\\d+)").find(dailyVerse.ref) ?: return dailyVerse
    val rawBookName = match.groupValues[1].trim()
    val bookName = when (rawBookName.lowercase()) {
      "acts of the apostles" -> "Acts"
      else -> rawBookName
    }
    val chapter = match.groupValues[2].toIntOrNull() ?: return dailyVerse
    val verse = match.groupValues[3].toIntOrNull() ?: return dailyVerse

    for (collection in listOf("old_testament", "new_testament", "deuterocanonical")) {
      val bookId = ContentRepo.listBooksLocalized(context, collection, "en")
        .firstOrNull { (_, title) -> title.equals(bookName, ignoreCase = true) }
        ?.first ?: continue
      val raw = readAssetText(
        context,
        "books/editions/en/${BibleEditions.KJV_1769}/$collection/$bookId.json"
      ) ?: return dailyVerse
      val overlay = runCatching { json.decodeFromString<EditionBookOverlay>(raw) }.getOrNull()
        ?: return dailyVerse
      val text = overlay.chapters.firstOrNull { it.number == chapter }
        ?.verses?.firstOrNull { it.verse == verse }
        ?.text ?: return dailyVerse
      return dailyVerse.copy(text = text)
    }
    return dailyVerse
  }

  private fun loadDaily(context: PlatformContext, lang: String): DailyVersesFile {
    dailyCache[lang]?.let { return it }
    val loaded = readAssetText(context, "daily_verses/$lang/daily.json")
      ?.let { runCatching { json.decodeFromString<DailyVersesFile>(it) }.getOrNull() }
      ?: readAssetText(context, "daily_verses/en/daily.json")
        ?.let { runCatching { json.decodeFromString<DailyVersesFile>(it) }.getOrNull() }
      ?: DailyVersesFile(emptyList())
    dailyCache[lang] = loaded
    return loaded
  }

  private fun loadFeasts(context: PlatformContext, lang: String): FeastVersesFile {
    feastCache[lang]?.let { return it }
    val loaded = readAssetText(context, "daily_verses/$lang/feasts.json")
      ?.let { runCatching { json.decodeFromString<FeastVersesFile>(it) }.getOrNull() }
      ?: readAssetText(context, "daily_verses/en/feasts.json")
        ?.let { runCatching { json.decodeFromString<FeastVersesFile>(it) }.getOrNull() }
      ?: FeastVersesFile(emptyMap())
    feastCache[lang] = loaded
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
