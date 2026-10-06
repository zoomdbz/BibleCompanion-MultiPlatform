package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerseOfTheDayTest {
  private val dailyBank = DailyVersesFile((1..366).map { day ->
    VerseEntry("Daily text $day", "Daily reference $day")
  })
  private val feastBank = FeastVersesFile(listOf(
    "passover", "unleavened", "firstfruits", "pentecost", "trumpets",
    "atonement", "tabernacles", "assembly"
  ).associateWith { id -> VerseEntry("Feast text $id", "Feast reference $id") })

  private fun selected(jdn: Long, feasts: FeastVersesFile = feastBank): DailyVerse {
    val (year, month, day) = HebrewCalendar.jdnToGregorian(jdn)
    return VerseOfTheDay.selectCalendarVerse(year, month, day, feasts) { dailyBank }
  }

  @Test fun multiDayFeastsDoNotRepeatTheirOpeningVerse() {
    for (year in 5784..5794) {
      val markers = HebrewCalendar.hebrewFeastsForYear(year)
      for (id in listOf("unleavened", "trumpets", "tabernacles")) {
        val days = markers.filter { it.second.id == id }
        val verses = days.map { (jdn, _) -> selected(jdn) }

        assertEquals(feastBank.feastVerses.getValue(id).ref, verses.first().ref, "$year $id opening")
        assertTrue(verses.first().isFeastOverride)
        assertEquals(days.size, verses.map { it.ref }.toSet().size, "$year $id daily variety")
        assertEquals(1, verses.count { it.ref == feastBank.feastVerses.getValue(id).ref }, "$year $id repeats")
        for ((index, verse) in verses.withIndex()) {
          if (index == 0 || id == "unleavened" && index == 1) continue
          val (gYear, month, day) = HebrewCalendar.jdnToGregorian(days[index].first)
          val regular = VerseOfTheDay.selectCalendarVerse(gYear, month, day, FeastVersesFile()) { dailyBank }
          assertEquals(regular, verse, "$year $id day ${index + 1} keeps the daily calendar")
        }
      }
    }
  }

  @Test fun firstfruitsIsNotHiddenByTheSecondDayOfUnleavenedBread() {
    for (year in 5784..5794) {
      val firstfruits = HebrewCalendar.hebrewFeastsForYear(year).first { it.second.id == "firstfruits" }
      val verse = selected(firstfruits.first)
      assertEquals(feastBank.feastVerses.getValue("firstfruits").ref, verse.ref)
      assertTrue(verse.isFeastOverride)
    }
  }

  @Test fun singleDayFeastsKeepTheirConfiguredVerses() {
    for ((jdn, marker) in HebrewCalendar.hebrewFeastsForYear(5787).filter { it.second.totalDays == 1 }) {
      val expected = feastBank.feastVerses[marker.id] ?: continue
      val verse = selected(jdn)
      assertEquals(expected.text, verse.text)
      assertEquals(expected.ref, verse.ref)
      assertTrue(verse.isFeastOverride)
    }
  }

  @Test fun feastSelectionPreservesTheLocalizedEntry() {
    val feasts = FeastVersesFile(mapOf("tabernacles" to VerseEntry("Localized feast text", "John 7:37")))
    val opening = HebrewCalendar.hebrewFeastsForYear(5787).first { it.second.id == "tabernacles" }
    assertEquals(DailyVerse("Localized feast text", "John 7:37", true), selected(opening.first, feasts))
    assertFalse(selected(opening.first + 1, feasts).isFeastOverride)
  }

  @Test fun regularCalendarKeepsLeapAndNonLeapDayIndexes() {
    fun ref(year: Int, month: Int, day: Int) =
      VerseOfTheDay.selectCalendarVerse(year, month, day, FeastVersesFile()) { dailyBank }.ref

    assertEquals("Daily reference 1", ref(2026, 1, 1))
    assertEquals("Daily reference 59", ref(2024, 2, 28))
    assertEquals("Daily reference 60", ref(2024, 2, 29))
    assertEquals("Daily reference 61", ref(2024, 3, 1))
    assertEquals("Daily reference 60", ref(2026, 3, 1))
    assertEquals("Daily reference 366", ref(2024, 12, 31))
    assertEquals("Daily reference 365", ref(2026, 12, 31))
  }

  @Test fun missingFeastEntryUsesTheRegularCalendar() {
    val opening = HebrewCalendar.hebrewFeastsForYear(5787).first { it.second.id == "tabernacles" }
    val verse = selected(opening.first, FeastVersesFile())
    assertFalse(verse.isFeastOverride)
    assertTrue(verse.ref.startsWith("Daily reference"))
  }

  @Test fun feastOverrideDoesNotLoadTheDailyBank() {
    val opening = HebrewCalendar.hebrewFeastsForYear(5787).first { it.second.id == "tabernacles" }
    val (year, month, day) = HebrewCalendar.jdnToGregorian(opening.first)
    val verse = VerseOfTheDay.selectCalendarVerse(year, month, day, feastBank) {
      error("The daily bank should not load when a feast entry applies")
    }
    assertTrue(verse.isFeastOverride)
  }

  @Test fun emptyBanksKeepTheExistingEmptyResult() {
    assertEquals(DailyVerse("", ""), VerseOfTheDay.selectCalendarVerse(
      2026, 1, 1, FeastVersesFile()
    ) { DailyVersesFile() })
  }
}
