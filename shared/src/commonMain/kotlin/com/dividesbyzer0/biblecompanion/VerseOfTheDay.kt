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

  fun todayVerse(context: PlatformContext, appLang: String): DailyVerse {
    val tag = LocaleUtils.effectiveAssetTag(appLang)
    val (year, month, day) = platformCurrentDate()

    val feasts = loadFeasts(context, tag)
    val feastOverride = checkFeastOverride(year, month, day, feasts)
    if (feastOverride != null) return feastOverride

    val daily = loadDaily(context, tag)
    if (daily.verses.isEmpty()) return DailyVerse("", "")

    val dayOfYear = dayOfYear(year, month, day)
    val hash = (year * 31L + dayOfYear)
    val size = daily.verses.size
    val index = ((hash % size) + size).toInt() % size
    val entry = daily.verses[index]
    return DailyVerse(entry.text, entry.ref)
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
