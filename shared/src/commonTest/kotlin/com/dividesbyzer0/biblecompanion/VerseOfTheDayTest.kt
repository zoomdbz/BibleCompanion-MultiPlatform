package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerseOfTheDayTest {
  @Test fun quotedSpeechAtTheEndCannotReplaceTheOuterClosingQuote() {
    val source = "He says, \u201cBe still, and know that I am God; I will be exalted among the nations, I will be exalted in the earth.\u201d"
    assertEquals(
      "\"He says, \"Be still, and know that I am God; I will be exalted among the nations, I will be exalted in the earth.\"\"",
      wrapVotdQuotes(source)
    )
    assertEquals(4, wrapVotdQuotes(source).count { it == '"' })
  }

  @Test fun excerptsCompleteSourceSpeechWithoutDroppingTheSpeechQuote() {
    assertEquals("\"Forget the former things.\"", wrapVotdQuotes("\u201cForget the former things."))
    assertEquals("\"You also must be ready.\"", wrapVotdQuotes("You also must be ready.\u201d"))
    assertEquals("\"Then he said, \"Follow me.\"\"", wrapVotdQuotes("Then he said, \u201cFollow me."))
    assertEquals("\"He said, \"Go.\" Then he left.\"", wrapVotdQuotes("He said, \u201cGo.\u201d Then he left."))
  }

  @Test fun entireQuotedExcerptsReuseOnlyTheirEnclosingPair() {
    assertEquals("\"Follow me.\"", wrapVotdQuotes("\u201cFollow me.\u201d"))
    assertEquals("[J]\"Follow me.\"[/J]", wrapVotdQuotes("[J]\u201cFollow me.\u201d[/J]"))
    assertEquals("\"\"First.\" And \"second.\"\"", wrapVotdQuotes("\u201cFirst.\u201d And \u201csecond.\u201d"))
    assertEquals("", wrapVotdQuotes("   "))
  }

  @Test fun apostrophesAndNestedSingleSpeechBecomeAsciiWithoutInventedPairs() {
    assertEquals("\"Don't forget your father's words or Jesus' disciples.\"",
      wrapVotdQuotes("Don\u2019t forget your father\u2019s words or Jesus\u2019 disciples."))
    assertEquals("\"He said, 'Love your neighbor.'\"",
      wrapVotdQuotes("He said, \u2018Love your neighbor.\u2019"))
    assertEquals("\"Jesus' disciples heard 'Come.'\"",
      wrapVotdQuotes("Jesus\u2019 disciples heard \u2018Come.\u2019"))
    assertEquals("\"'Jesus' disciples'\"", wrapVotdQuotes("\u2018Jesus\u2019 disciples"))
    assertEquals("\"brothers' love and l'amour\"", wrapVotdQuotes("brothers' love and l'amour"))
    assertEquals("\"brothers' love and l'amour\"", wrapVotdQuotes("brothers\u2019 love and l\u2019amour"))
  }

  @Test fun nestedExcerptClosuresFollowTheirOpeningOrder() {
    assertEquals("\"'Words.'\"", wrapVotdQuotes("\u201c\u2018Words."))
    assertEquals("\"'Words.'\"", wrapVotdQuotes("\u201c\u2018Words.\u201d"))
    assertEquals("\"He said, \"'Come.'\"\"", wrapVotdQuotes("He said, \u201c\u2018Come."))
    assertEquals("\"'Words.'\"", wrapVotdQuotes("Words.\u2019\u201d"))
    assertEquals("\"'Words.' More.\"", wrapVotdQuotes("\u201cWords.\u2019 More.\u201d"))
  }

  @Test fun localizedDailyExcerptsCompleteExplicitSingleSpeech() {
    // Hindi Matthew 6:11 and Traditional Chinese Ezekiel 36:26 both omit
    // speech endings because their selected verses end before the speech does.
    val hindi = "\u0939\u092e\u093e\u0930\u0940 \u0926\u093f\u0928 \u092d\u0930 \u0915\u0940 \u0930\u094b\u091f\u0940 \u0906\u091c \u0939\u092e\u0947\u0902 \u0926\u0947\u0964"
    assertEquals("\"'$hindi'\"", wrapVotdQuotes("\u2018$hindi", "hi"))
    val chinese = "\u6211\u5fc5\u628a\u65b0\u5fc3\u8cdc\u7d66\u4f60\u5011\uff0c\u628a\u65b0\u9748\u653e\u5728\u4f60\u5011\u88e1\u9762\uff1b\u6211\u5fc5\u5f9e\u4f60\u5011\u7684\u8089\u9ad4\u4e2d\u9664\u53bb\u77f3\u5fc3\uff0c\u628a\u8089\u5fc3\u8cdc\u7d66\u4f60\u5011\u3002"
    assertEquals("\"'$chinese'\"", wrapVotdQuotes("\"\u2018$chinese", "zh-Hant"))
    assertEquals("\"'\u4e0d\u8981\u5bb3\u6015\u3002'\"", wrapVotdQuotes("\u4e0d\u8981\u5bb3\u6015\u3002\u2019", "zh-Hans"))
  }

  @Test fun localizedQuotesPreserveEveryWordAndDisplayTag() {
    assertEquals("\"Er sagte: \"Komm.\"\"", wrapVotdQuotes("Er sagte: \u00bbKomm.\u00ab", "de"))
    assertEquals("\"Komm.\"", wrapVotdQuotes("\u201eKomm.\u201c", "de"))
    assertEquals("\"El sabado.\"", wrapVotdQuotes("\u00bbEl sabado.", "es"))
    assertEquals("[J]\"\u4f86\u3002\"[/J]", wrapVotdQuotes("[J]\u300c\u4f86\u3002\u300d[/J]", "zh-Hant"))
    assertEquals("\"\"\u3053\u3068\u3070\" \u8a9e\u308a \"\u7d9a\u304d\"\"",
      wrapVotdQuotes("\u3053\u3068\u3070\u300d \u8a9e\u308a \u300c\u7d9a\u304d", "ja"))
  }

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
