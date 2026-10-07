package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarMathTest {
  @Test fun hebrewMonthCountsAndLengthsAgreeForCommonAndLeapYears() {
    for (year in 5770..5810) {
      assertTrue(HebrewCalendar.yearLength(year) in if (HebrewCalendar.isLeapYear(year)) 383..385 else 353..355,
        "Invalid Hebrew year length for $year")
      assertEquals(if (HebrewCalendar.isLeapYear(year)) 13 else 12, HebrewCalendar.monthsInYear(year))
      assertEquals(HebrewCalendar.monthsInYear(year), HebrewCalendar.monthNames(year).size)
      assertEquals(HebrewCalendar.yearLength(year),
        (0 until HebrewCalendar.monthsInYear(year)).sumOf { HebrewCalendar.daysInMonth(year, it) })
    }
  }

  @Test fun hebrewDatesMatchIndependentDaytimeConversions() {
    // Hebcal date converter, h2g=1 and strict=1; these are daytime dates.
    // https://www.hebcal.com/home/219/hebrew-date-converter-rest-api
    for ((year, gregorian) in listOf(
      5785 to Triple(2024, 10, 3),
      5786 to Triple(2025, 9, 23),
      5787 to Triple(2026, 9, 12)
    )) {
      assertEquals(gregorian, HebrewCalendar.hebrewToGregorian(year, 0, 1))
      assertEquals(HebrewDate(year, 0, 1), HebrewCalendar.gregorianToHebrew(
        gregorian.first, gregorian.second, gregorian.third))
    }
    assertEquals(Triple(2026, 4, 2), HebrewCalendar.hebrewToGregorian(
      5786, HebrewCalendar.monthIndexByName(5786, "Nisan"), 15))
  }

  @Test fun gregorianAndHebrewDatesRoundTripThroughoutTheCatalogYears() {
    for (year in 2024..2033) for (month in 1..12) {
      for (day in 1..CalendarUtils.daysInGregorianMonth(year, month)) {
        val hebrew = HebrewCalendar.gregorianToHebrew(year, month, day)
        assertEquals(Triple(year, month, day),
          HebrewCalendar.hebrewToGregorian(hebrew.year, hebrew.monthIndex, hebrew.day))
      }
    }
  }

  @Test fun feastDisplayLabelPreservesSingleAndMultipleDayBehavior() {
    val single = CalendarUtils.UpcomingFeast("Passover", 2026, 4, 1, 0)
    val multi = single.copy(name = "Tabernacles", dayOfFeast = 3, totalDays = 7)
    assertEquals("Passover", single.displayLabel)
    assertEquals("Tabernacles (Day 3)", multi.displayLabel)
  }
}
