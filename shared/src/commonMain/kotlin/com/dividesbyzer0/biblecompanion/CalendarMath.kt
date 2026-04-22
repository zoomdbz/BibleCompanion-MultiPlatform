package com.dividesbyzer0.biblecompanion

data class HebrewDate(val year: Int, val monthIndex: Int, val day: Int) {
  val monthName: String get() = HebrewCalendar.monthNames(year)[monthIndex]
}

data class EsseneDate(val year: Int, val month: Int, val day: Int, val intercalary: Boolean = false)

data class FeastMarker(
  val id: String,
  val displayName: String,
  val isSpring: Boolean,
  val calendar: FeastCalendarType,
  val dayOfFeast: Int = 0,
  val totalDays: Int = 1
)

enum class FeastCalendarType { HEBREW, ESSENE }

object HebrewCalendar {

  private const val EPOCH = 347996L

  fun isLeapYear(year: Int): Boolean = (7L * year + 1) % 19 < 7

  fun monthsInYear(year: Int): Int = if (isLeapYear(year)) 13 else 12

  private fun elapsedDays(year: Int): Long {
    val y = year.toLong()
    val cycles = (y - 1) / 19
    val rem = (y - 1) % 19
    val monthsElapsed = 235L * cycles + 12L * rem + (7L * rem + 1) / 19
    val partsElapsed = 204L + 793L * (monthsElapsed % 1080L)
    val hoursElapsed =
      5L + 12L * monthsElapsed + 793L * (monthsElapsed / 1080L) + partsElapsed / 1080L
    val conjDay = 1L + 29L * monthsElapsed + hoursElapsed / 24L
    val conjParts = 1080L * (hoursElapsed % 24L) + partsElapsed % 1080L

    var d = conjDay
    if (conjParts >= 19440L) d++
    if (d % 7L == 2L && conjParts >= 9924L && !isLeapYear(year)) d++
    if (d % 7L == 1L && conjParts >= 16789L && isLeapYear(year - 1)) d++
    val dow = d % 7L
    if (dow == 0L || dow == 3L || dow == 5L) d++
    return d
  }

  fun newYearJDN(year: Int): Long = EPOCH + elapsedDays(year)

  fun yearLength(year: Int): Int = (newYearJDN(year + 1) - newYearJDN(year)).toInt()

  private val COMMON_MONTHS = listOf(
    "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar",
    "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul"
  )
  private val LEAP_MONTHS = listOf(
    "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar I", "Adar II",
    "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul"
  )

  fun monthNames(year: Int): List<String> =
    if (isLeapYear(year)) LEAP_MONTHS else COMMON_MONTHS

  fun daysInMonth(year: Int, monthIndex: Int): Int {
    val name = monthNames(year)[monthIndex]
    val yl = yearLength(year)
    return when (name) {
      "Tishrei" -> 30
      "Cheshvan" -> if (yl % 10 == 5) 30 else 29
      "Kislev" -> if (yl % 10 == 3) 29 else 30
      "Tevet" -> 29
      "Shevat" -> 30
      "Adar" -> 29
      "Adar I" -> 30
      "Adar II" -> 29
      "Nisan" -> 30
      "Iyar" -> 29
      "Sivan" -> 30
      "Tammuz" -> 29
      "Av" -> 30
      "Elul" -> 29
      else -> 30
    }
  }

  fun gregorianToJDN(year: Int, month: Int, day: Int): Long {
    val a = (14 - month) / 12
    val y = year.toLong() + 4800L - a
    val m = month + 12 * a - 3
    return day.toLong() + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045L
  }

  fun jdnToGregorian(jdn: Long): Triple<Int, Int, Int> {
    val a = jdn + 32044
    val b = (4 * a + 3) / 146097
    val c = a - 146097 * b / 4
    val d = (4 * c + 3) / 1461
    val e = c - 1461 * d / 4
    val m = (5 * e + 2) / 153
    val day = (e - (153 * m + 2) / 5 + 1).toInt()
    val month = (m + 3 - 12 * (m / 10)).toInt()
    val year = (100 * b + d - 4800 + m / 10).toInt()
    return Triple(year, month, day)
  }

  fun jdnToHebrew(jdn: Long): HebrewDate {
    var year = ((jdn - EPOCH) * 19 / 6940 + 1).toInt()
    while (newYearJDN(year + 1) <= jdn) year++
    while (newYearJDN(year) > jdn) year--

    var dayInYear = (jdn - newYearJDN(year)).toInt()
    val months = monthNames(year)
    var mi = 0
    while (mi < months.size - 1) {
      val dm = daysInMonth(year, mi)
      if (dayInYear < dm) break
      dayInYear -= dm
      mi++
    }
    return HebrewDate(year, mi, dayInYear + 1)
  }

  fun hebrewToJDN(year: Int, monthIndex: Int, day: Int): Long {
    var jdn = newYearJDN(year)
    for (m in 0 until monthIndex) jdn += daysInMonth(year, m)
    return jdn + day - 1
  }

  fun gregorianToHebrew(gYear: Int, gMonth: Int, gDay: Int): HebrewDate =
    jdnToHebrew(gregorianToJDN(gYear, gMonth, gDay))

  fun hebrewToGregorian(hYear: Int, monthIndex: Int, day: Int): Triple<Int, Int, Int> =
    jdnToGregorian(hebrewToJDN(hYear, monthIndex, day))

  // 0=Sunday, 1=Monday, ..., 6=Saturday
  fun dayOfWeekFromJDN(jdn: Long): Int = ((jdn + 1) % 7).toInt()

  fun monthIndexByName(year: Int, name: String): Int = monthNames(year).indexOf(name)

  fun hebrewFeastsForYear(year: Int): List<Pair<Long, FeastMarker>> {
    val result = mutableListOf<Pair<Long, FeastMarker>>()
    fun add(monthName: String, day: Int, id: String, display: String, spring: Boolean,
            dayOfFeast: Int = 0, totalDays: Int = 1) {
      val mi = monthIndexByName(year, monthName)
      if (mi >= 0) {
        val jdn = hebrewToJDN(year, mi, day)
        result.add(jdn to FeastMarker(id, display, spring, FeastCalendarType.HEBREW, dayOfFeast, totalDays))
      }
    }

    add("Nisan", 14, "passover", "Passover", true)
    for ((i, d) in (15..21).withIndex()) add("Nisan", d, "unleavened", "Unleavened Bread", true, dayOfFeast = i + 1, totalDays = 7)
    add("Nisan", 16, "firstfruits", "Firstfruits", true)
    // Pesach Sheni — Second Passover for those unclean or on a journey at the first
    // (Numbers 9:9-14). Observed one month after Passover, Iyar 14.
    add("Iyar", 14, "second_passover", "Second Passover", true)
    add("Sivan", 6, "pentecost", "Pentecost", true)
    add("Tishrei", 1, "trumpets", "Trumpets", false, dayOfFeast = 1, totalDays = 2)
    add("Tishrei", 2, "trumpets", "Trumpets", false, dayOfFeast = 2, totalDays = 2)
    add("Tishrei", 10, "atonement", "Day of Atonement", false)
    for ((i, d) in (15..21).withIndex()) add("Tishrei", d, "tabernacles", "Tabernacles", false, dayOfFeast = i + 1, totalDays = 7)
    add("Tishrei", 22, "assembly", "Shemini Atzeret", false)

    return result
  }

  fun hebrewTraditionForYear(year: Int): List<Pair<Long, FeastMarker>> {
    val result = mutableListOf<Pair<Long, FeastMarker>>()
    fun add(monthName: String, day: Int, id: String, display: String,
            dayOfFeast: Int = 0, totalDays: Int = 1) {
      val mi = monthIndexByName(year, monthName)
      if (mi >= 0) {
        val jdn = hebrewToJDN(year, mi, day)
        result.add(jdn to FeastMarker(id, display, false, FeastCalendarType.HEBREW, dayOfFeast, totalDays))
      }
    }

    val adarName = if (isLeapYear(year)) "Adar II" else "Adar"
    add("Tevet", 10, "fast_tevet", "10th of Tevet (fast)")
    add(adarName, 13, "fast_esther", "Fast of Esther")
    add(adarName, 14, "purim", "Purim")
    add(adarName, 15, "purim", "Shushan Purim")
    add("Tammuz", 17, "fast_tammuz", "17th of Tammuz (fast)")
    add("Av", 9, "tisha_bav", "Tisha B\u2019Av (fast)")
    add("Tishrei", 3, "fast_gedaliah", "Fast of Gedaliah")
    add("Kislev", 25, "hanukkah", "Hanukkah", dayOfFeast = 1, totalDays = 8)
    for ((i, d) in (26..30).withIndex()) add("Kislev", d, "hanukkah", "Hanukkah", dayOfFeast = i + 2, totalDays = 8)
    add("Tevet", 1, "hanukkah", "Hanukkah", dayOfFeast = 7, totalDays = 8)
    add("Tevet", 2, "hanukkah", "Hanukkah", dayOfFeast = 8, totalDays = 8)

    return result
  }
}

object EsseneCalendar {

  val MONTH_LENGTHS = intArrayOf(30, 30, 31, 30, 30, 31, 30, 30, 31, 30, 30, 31)

  fun yearStartJDN(gregorianYear: Int): Long {
    val equinoxJDN = HebrewCalendar.gregorianToJDN(gregorianYear, 3, 20)
    val dow = HebrewCalendar.dayOfWeekFromJDN(equinoxJDN)
    val offset = (3 - dow + 7) % 7 // next Wednesday
    return equinoxJDN + offset
  }

  fun jdnToEssene(jdn: Long): EsseneDate {
    val (gYear, _, _) = HebrewCalendar.jdnToGregorian(jdn)

    var start = yearStartJDN(gYear)
    var esseneYear = gYear
    if (jdn < start) {
      esseneYear = gYear - 1
      start = yearStartJDN(gYear - 1)
    }
    val dayOffset = (jdn - start).toInt()
    if (dayOffset >= 364) {
      return EsseneDate(esseneYear, 0, 0, intercalary = true)
    }

    var remaining = dayOffset
    for (m in MONTH_LENGTHS.indices) {
      if (remaining < MONTH_LENGTHS[m]) return EsseneDate(esseneYear, m + 1, remaining + 1)
      remaining -= MONTH_LENGTHS[m]
    }
    return EsseneDate(esseneYear, 12, remaining + 1)
  }

  fun esseneToJDN(gregorianYear: Int, month: Int, day: Int): Long {
    val start = yearStartJDN(gregorianYear)
    var offset = 0
    for (m in 0 until (month - 1)) offset += MONTH_LENGTHS[m]
    return start + offset + day - 1
  }

  fun esseneFeastsForYear(gregorianYear: Int): List<Pair<Long, FeastMarker>> {
    val result = mutableListOf<Pair<Long, FeastMarker>>()
    fun add(month: Int, day: Int, id: String, display: String, spring: Boolean,
            dayOfFeast: Int = 0, totalDays: Int = 1) {
      result.add(esseneToJDN(gregorianYear, month, day) to
        FeastMarker(id, display, spring, FeastCalendarType.ESSENE, dayOfFeast, totalDays))
    }

    add(1, 14, "passover", "Passover", true)
    for ((i, d) in (15..21).withIndex()) add(1, d, "unleavened", "Unleavened Bread", true, dayOfFeast = i + 1, totalDays = 7)
    add(1, 26, "firstfruits", "Firstfruits", true)
    // Pesach Sheni on the Qumran 364-day calendar falls one month after the first
    // Passover (II.14), matching the Numbers 9 commandment pattern.
    add(2, 14, "second_passover", "Second Passover", true)
    add(3, 15, "pentecost", "Pentecost", true)
    add(7, 1, "trumpets", "Trumpets", false)
    add(7, 10, "atonement", "Day of Atonement", false)
    for ((i, d) in (15..21).withIndex()) add(7, d, "tabernacles", "Tabernacles", false, dayOfFeast = i + 1, totalDays = 7)
    add(7, 22, "assembly", "Shemini Atzeret", false)

    return result
  }

  val MONTH_NAMES = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
}

object CalendarUtils {
  fun daysInGregorianMonth(year: Int, month: Int): Int = when (month) {
    1 -> 31; 2 -> if (isGregorianLeap(year)) 29 else 28; 3 -> 31
    4 -> 30; 5 -> 31; 6 -> 30; 7 -> 31; 8 -> 31; 9 -> 30
    10 -> 31; 11 -> 30; 12 -> 31; else -> 30
  }

  private fun isGregorianLeap(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

  fun firstDayOfWeekInMonth(year: Int, month: Int): Int =
    HebrewCalendar.dayOfWeekFromJDN(HebrewCalendar.gregorianToJDN(year, month, 1))

  fun buildFeastMap(
    gYear: Int,
    gMonth: Int
  ): Map<Int, List<FeastMarker>> {
    val firstJDN = HebrewCalendar.gregorianToJDN(gYear, gMonth, 1)
    val daysInMonth = daysInGregorianMonth(gYear, gMonth)
    val lastJDN = firstJDN + daysInMonth - 1

    val hDate = HebrewCalendar.jdnToHebrew(firstJDN)
    val hebrewFeasts = HebrewCalendar.hebrewFeastsForYear(hDate.year)
    val hebrewFeastsNext = if (hDate.monthName == "Elul")
      HebrewCalendar.hebrewFeastsForYear(hDate.year + 1) else emptyList()

    val hebrewTradition = HebrewCalendar.hebrewTraditionForYear(hDate.year)
    val hebrewTraditionNext = if (hDate.monthName == "Elul")
      HebrewCalendar.hebrewTraditionForYear(hDate.year + 1) else emptyList()

    val esseneFeasts = EsseneCalendar.esseneFeastsForYear(gYear)
    val esseneFeastsPrev = EsseneCalendar.esseneFeastsForYear(gYear - 1)

    val allFeasts = hebrewFeasts + hebrewFeastsNext + hebrewTradition + hebrewTraditionNext + esseneFeasts + esseneFeastsPrev

    val map = mutableMapOf<Int, MutableList<FeastMarker>>()
    for ((jdn, marker) in allFeasts) {
      if (jdn in firstJDN..lastJDN) {
        val dayOfMonth = (jdn - firstJDN + 1).toInt()
        map.getOrPut(dayOfMonth) { mutableListOf() }.add(marker)
      }
    }
    return map
  }

  val GREGORIAN_MONTH_NAMES = listOf(
    "", "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  )

  fun localizedDayHeaders(lang: String): List<String> = when (lang.lowercase()) {
    "ja" -> listOf("\u65e5", "\u6708", "\u706b", "\u6c34", "\u6728", "\u91d1", "\u571f")
    "ko" -> listOf("\uc77c", "\uc6d4", "\ud654", "\uc218", "\ubaa9", "\uae08", "\ud1a0")
    "zh-hans", "zh-hant" -> listOf("\u65e5", "\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d")
    "ar" -> listOf("\u0623\u062d", "\u0625\u062b", "\u062b\u0644", "\u0623\u0631", "\u062e\u0645", "\u062c\u0645", "\u0633\u0628")
    "ru" -> listOf("\u0412\u0441", "\u041f\u043d", "\u0412\u0442", "\u0421\u0440", "\u0427\u0442", "\u041f\u0442", "\u0421\u0431")
    "de" -> listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
    "es" -> listOf("Dom", "Lun", "Mar", "Mi\u00e9", "Jue", "Vie", "S\u00e1b")
    "fr" -> listOf("Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam")
    "pt" -> listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "S\u00e1b")
    "it" -> listOf("Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab")
    "hi" -> listOf("\u0930\u0935\u093f", "\u0938\u094b\u092e", "\u092e\u0902", "\u092c\u0941\u0927", "\u0917\u0941\u0930\u0941", "\u0936\u0941\u0915\u094d\u0930", "\u0936\u0928\u093f")
    else -> listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
  }

  data class UpcomingFeast(
    val name: String,
    val gregorianYear: Int,
    val gregorianMonth: Int,
    val gregorianDay: Int,
    val daysUntil: Int,
    val calendarType: FeastCalendarType = FeastCalendarType.HEBREW,
    val dayOfFeast: Int = 0,
    val totalDays: Int = 1,
    val id: String = ""
  ) {
    val displayLabel: String get() =
      if (totalDays > 1) "$name (Day $dayOfFeast)" else name
  }

  fun nextFeast(gYear: Int, gMonth: Int, gDay: Int): UpcomingFeast? =
    upcomingFeasts(gYear, gMonth, gDay, 1).firstOrNull()

  fun upcomingFeasts(gYear: Int, gMonth: Int, gDay: Int, count: Int = 5): List<UpcomingFeast> {
    val todayJDN = HebrewCalendar.gregorianToJDN(gYear, gMonth, gDay)
    val hDate = HebrewCalendar.jdnToHebrew(todayJDN)

    val candidates = mutableListOf<Pair<Long, FeastMarker>>()
    candidates.addAll(HebrewCalendar.hebrewFeastsForYear(hDate.year))
    candidates.addAll(HebrewCalendar.hebrewFeastsForYear(hDate.year + 1))
    candidates.addAll(EsseneCalendar.esseneFeastsForYear(gYear))
    candidates.addAll(EsseneCalendar.esseneFeastsForYear(gYear + 1))

    val seen = mutableSetOf<String>()
    val result = mutableListOf<UpcomingFeast>()
    val future = candidates
      .filter { it.first >= todayJDN }
      .sortedBy { it.first }

    for ((jdn, marker) in future) {
      if (result.size >= count) break
      val key = "${marker.id}_${marker.calendar.name}_$jdn"
      if (key in seen) continue
      seen.add(key)
      val (y, m, d) = HebrewCalendar.jdnToGregorian(jdn)
      val daysUntil = (jdn - todayJDN).toInt()
      result.add(UpcomingFeast(marker.displayName, y, m, d, daysUntil, marker.calendar, marker.dayOfFeast, marker.totalDays, marker.id))
    }
    return result
  }

  fun localizedMonthName(month: Int, lang: String): String {
    val names = when (lang.lowercase()) {
      "es" -> listOf("", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
      "fr" -> listOf("", "Janvier", "F\u00e9vrier", "Mars", "Avril", "Mai", "Juin", "Juillet", "Ao\u00fbt", "Septembre", "Octobre", "Novembre", "D\u00e9cembre")
      "de" -> listOf("", "Januar", "Februar", "M\u00e4rz", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
      "it" -> listOf("", "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")
      "pt" -> listOf("", "Janeiro", "Fevereiro", "Mar\u00e7o", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
      "ru" -> listOf("", "\u042f\u043d\u0432\u0430\u0440\u044c", "\u0424\u0435\u0432\u0440\u0430\u043b\u044c", "\u041c\u0430\u0440\u0442", "\u0410\u043f\u0440\u0435\u043b\u044c", "\u041c\u0430\u0439", "\u0418\u044e\u043d\u044c", "\u0418\u044e\u043b\u044c", "\u0410\u0432\u0433\u0443\u0441\u0442", "\u0421\u0435\u043d\u0442\u044f\u0431\u0440\u044c", "\u041e\u043a\u0442\u044f\u0431\u0440\u044c", "\u041d\u043e\u044f\u0431\u0440\u044c", "\u0414\u0435\u043a\u0430\u0431\u0440\u044c")
      "ja" -> listOf("", "1\u6708", "2\u6708", "3\u6708", "4\u6708", "5\u6708", "6\u6708", "7\u6708", "8\u6708", "9\u6708", "10\u6708", "11\u6708", "12\u6708")
      "ko" -> listOf("", "1\uc6d4", "2\uc6d4", "3\uc6d4", "4\uc6d4", "5\uc6d4", "6\uc6d4", "7\uc6d4", "8\uc6d4", "9\uc6d4", "10\uc6d4", "11\uc6d4", "12\uc6d4")
      "zh-hans", "zh-hant" -> listOf("", "1\u6708", "2\u6708", "3\u6708", "4\u6708", "5\u6708", "6\u6708", "7\u6708", "8\u6708", "9\u6708", "10\u6708", "11\u6708", "12\u6708")
      "ar" -> listOf("", "\u064a\u0646\u0627\u064a\u0631", "\u0641\u0628\u0631\u0627\u064a\u0631", "\u0645\u0627\u0631\u0633", "\u0623\u0628\u0631\u064a\u0644", "\u0645\u0627\u064a\u0648", "\u064a\u0648\u0646\u064a\u0648", "\u064a\u0648\u0644\u064a\u0648", "\u0623\u063a\u0633\u0637\u0633", "\u0633\u0628\u062a\u0645\u0628\u0631", "\u0623\u0643\u062a\u0648\u0628\u0631", "\u0646\u0648\u0641\u0645\u0628\u0631", "\u062f\u064a\u0633\u0645\u0628\u0631")
      "hi" -> listOf("", "\u091c\u0928\u0935\u0930\u0940", "\u092b\u093c\u0930\u0935\u0930\u0940", "\u092e\u093e\u0930\u094d\u091a", "\u0905\u092a\u094d\u0930\u0948\u0932", "\u092e\u0908", "\u091c\u0942\u0928", "\u091c\u0941\u0932\u093e\u0908", "\u0905\u0917\u0938\u094d\u0924", "\u0938\u093f\u0924\u0902\u092c\u0930", "\u0905\u0915\u094d\u0924\u0942\u092c\u0930", "\u0928\u0935\u0902\u092c\u0930", "\u0926\u093f\u0938\u0902\u092c\u0930")
      else -> GREGORIAN_MONTH_NAMES
    }
    return names.getOrElse(month) { GREGORIAN_MONTH_NAMES.getOrElse(month) { "" } }
  }
}
