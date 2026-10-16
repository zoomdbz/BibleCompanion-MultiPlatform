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

/** Keep source speech intact, completing only quotes cut off by excerpt boundaries. */
internal fun wrapVotdQuotes(text: String, language: String = "en"): String {
  val source = text.trim()
  if (source.isEmpty()) return source
  val tag = LocaleUtils.effectiveAssetTag(language)
  val german = tag == "de"
  val lowDouble = german && '\u201e' in source
  val lowSingle = german && '\u201a' in source
  val stack = mutableListOf<Pair<Int, Char>>()
  val marks = mutableMapOf<Int, VotdQuoteMark>()
  val insertions = mutableMapOf<Int, MutableList<VotdQuoteMark>>()
  val prefix = mutableListOf<VotdQuoteMark>()
  for ((index, ch) in source.withIndex()) {
    val kind = when (ch) {
      '"', '\uff02', '\u201c', '\u201d', '\u201e', '\u201f', '\u00ab', '\u00bb',
      '\u300c', '\u300d', '\u300e', '\u300f' -> '"'
      '\u2018', '\u2019', '\u201a', '\u201b', '\u2039', '\u203a' -> '\''
      else -> continue
    }
    // ASCII apostrophes never enter the speech stack. Curly apostrophes inside
    // Latin words or possessives (don't, father's, Jesus', brothers') do not either.
    if (ch == '\u2019' && votdWordApostrophe(source, index, stack.any { it.second == '\'' })) continue
    val step = when (ch) {
      '"', '\uff02' -> if (stack.any { it.second == '"' }) -1 else 1
      '\u201c' -> if (lowDouble) -1 else 1
      '\u2018' -> if (lowSingle) -1 else 1
      '\u201e', '\u201f', '\u201a', '\u201b', '\u300c', '\u300e' -> 1
      '\u201d', '\u300d', '\u300f' -> -1
      '\u2019' -> -1
      '\u00ab', '\u2039' -> if (german) -1 else 1
      '\u203a' -> if (german) 1 else -1
      '\u00bb' -> if (german || tag == "es" && stripScriptureInlineTags(source.take(index)).isBlank()) 1 else -1
      else -> continue
    }
    val mark = VotdQuoteMark(kind, step > 0)
    marks[index] = mark
    if (mark.opening) {
      stack += index to kind
    } else {
      val matching = stack.indexOfLast { it.second == kind }
      if (matching >= 0) {
        // An excerpt can omit the inner close while retaining an outer close.
        // Finish the inner speech before closing its enclosing quotation.
        while (stack.lastIndex > matching) {
          insertions.getOrPut(index) { mutableListOf() } += VotdQuoteMark(stack.removeAt(stack.lastIndex).second, false)
        }
        stack.removeAt(stack.lastIndex)
      } else {
        val opening = VotdQuoteMark(kind, true)
        val ancestor = stack.lastOrNull()?.first
        if (ancestor == null) prefix.add(0, opening)
        else insertions.getOrPut(ancestor + 1) { mutableListOf() }.add(0, opening)
      }
    }
  }
  val completed = StringBuilder(source.length + 8)
  val doubleEvents = mutableListOf<Pair<Int, Int>>()
  fun appendMark(mark: VotdQuoteMark) {
    if (mark.kind == '"') doubleEvents += completed.length to if (mark.opening) 1 else -1
    completed.append(mark.kind)
  }
  prefix.forEach(::appendMark)
  for ((index, ch) in source.withIndex()) {
    insertions[index]?.forEach(::appendMark)
    val mark = marks[index]
    if (mark == null) completed.append(asciiVotdQuote(ch)) else appendMark(mark)
  }
  stack.asReversed().forEach { appendMark(VotdQuoteMark(it.second, false)) }
  val result = completed.toString()
  // Display tags must not hide a quote at the beginning or end of the excerpt.
  val visible = stripScriptureInlineTags(result)
  // Reuse a source pair only when it encloses every word, not merely because a
  // speech quote happens to end the verse (for example, Psalm 46:10).
  var enclosingDepth = 0
  val closesBeforeEnd = doubleEvents.any { (index, step) ->
    enclosingDepth += step
    enclosingDepth == 0 && stripScriptureInlineTags(result.substring(index + 1)).isNotBlank()
  }
  val enclosed = visible.startsWith('"') && visible.endsWith('"') && !closesBeforeEnd
  return if (enclosed) result else "\"$result\""
}

private data class VotdQuoteMark(val kind: Char, val opening: Boolean)

private fun votdWordApostrophe(text: String, index: Int, insideSingleSpeech: Boolean): Boolean {
  fun latin(ch: Char?): Boolean = ch != null && ch.isLetter() &&
    (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00c0'..'\u024f')
  val previous = text.getOrNull(index - 1)
  val next = text.getOrNull(index + 1)
  if (latin(previous) && latin(next)) return true
  if ((previous == 's' || previous == 'S') && !latin(next)) {
    if (!insideSingleSpeech || next?.isWhitespace() == true && latin(text.drop(index + 1).firstOrNull { !it.isWhitespace() })) return true
  }
  return !insideSingleSpeech && (previous == null || previous.isWhitespace()) && latin(next)
}

private fun asciiVotdQuote(ch: Char): Char = when (ch) {
    '\u201c', '\u201d', '\u201e', '\u201f', '\u00ab', '\u00bb',
    '\u300c', '\u300d', '\u300e', '\u300f', '\uff02' -> '"'
    '\u2018', '\u2019', '\u201a', '\u201b', '\u2039', '\u203a', '\uff07' -> '\''
    else -> ch
}

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

    val selected = selectCalendarVerse(year, month, day, loadFeasts(context, tag)) {
      loadDaily(context, tag)
    }
    return resolveInternalEdition(context, tag, internalBibleVersion, selected)
  }

  internal fun selectCalendarVerse(
    year: Int,
    month: Int,
    day: Int,
    feasts: FeastVersesFile,
    dailyBank: () -> DailyVersesFile
  ): DailyVerse {
    checkFeastOverride(year, month, day, feasts)?.let { return it }
    val daily = dailyBank()
    if (daily.verses.isEmpty()) return DailyVerse("", "")

    val dayOfYear = dayOfYear(year, month, day)
    // The bank is sorted by bible.com's day-of-year calendar (verses[0] = day 1, ...).
    // Index by day-of-year so the app shows the same verse bible.com shows that day.
    val size = daily.verses.size
    val index = ((dayOfYear - 1) % size + size) % size
    val entry = daily.verses[index]
    return DailyVerse(entry.text, entry.ref)
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
        // A feast has one configured verse. Use it once on the opening day,
        // then resume the dated daily bank without shifting its calendar.
        // Continue checking so an overlapping feast can use its own verse.
        if (marker.totalDays > 1 && marker.dayOfFeast != 1) continue
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
