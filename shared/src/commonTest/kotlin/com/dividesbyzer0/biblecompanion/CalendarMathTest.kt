package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarMathTest {
  @Test fun hebrewMonthCountsAndLengthsAgreeForCommonAndLeapYears() {
    for (year in 5770..5810) {
      assertEquals(if (HebrewCalendar.isLeapYear(year)) 13 else 12, HebrewCalendar.monthsInYear(year))
      assertEquals(HebrewCalendar.monthsInYear(year), HebrewCalendar.monthNames(year).size)
      assertEquals(HebrewCalendar.yearLength(year),
        (0 until HebrewCalendar.monthsInYear(year)).sumOf { HebrewCalendar.daysInMonth(year, it) })
    }
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
