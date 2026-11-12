package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BibleGatewayAppLinkTest {
  @Test
  fun wholeChapterReferencesOpenTheAppRoute() {
    assertEquals("biblegateway://bible/John.3/NIV", Linker.buildBibleGatewayAppLink("John 3", "NIV"))
    assertEquals("biblegateway://bible/Jas.4/NIV", Linker.buildBibleGatewayAppLink("James 4", "NIV"))
    assertEquals("biblegateway://bible/1John.4/ESV", Linker.buildBibleGatewayAppLink("1 John 4", "esv"))
    assertEquals("biblegateway://bible/Ps.119/KJV", Linker.buildBibleGatewayAppLink("Psalms 119", "KJV"))
    assertEquals("biblegateway://bible/Song.2/NKJV", Linker.buildBibleGatewayAppLink("Song of Solomon 2", "NKJV"))
    assertEquals("biblegateway://bible/Matt.5/NVI-PT", Linker.buildBibleGatewayAppLink("Matthew 5", "NVI-PT"))
  }

  @Test
  fun verseReferencesKeepTheExactWebsitePage() {
    // The app's link handler accepts Book.Chapter only and cannot land on a verse.
    assertNull(Linker.buildBibleGatewayAppLink("James 4:17", "NIV"))
    assertNull(Linker.buildBibleGatewayAppLink("James 4.17", "NIV"))
    assertNull(Linker.buildBibleGatewayAppLink("John 3:16-18", "NIV"))
    assertNull(Linker.buildBibleGatewayAppLink("Psalms 119:105", "ESV"))
    assertNull(Linker.buildBibleGatewayAppLink("Genesis 1-2", "NIV"))
  }

  @Test
  fun oneChapterBooksOpenTheAppOnlyForTheWholeChapter() {
    assertEquals("biblegateway://bible/Jude.1/NIV", Linker.buildBibleGatewayAppLink("Jude 1", "NIV"))
    assertNull(Linker.buildBibleGatewayAppLink("Jude 3", "NIV"))
    assertNull(Linker.buildBibleGatewayAppLink("Obadiah 1:15", "NIV"))
  }

  @Test
  fun everyProtocanonBookHasAnAppCode() {
    val books = listOf(
      "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
      "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra",
      "Nehemiah", "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Songs",
      "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
      "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
      "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians",
      "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians",
      "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon",
      "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation"
    )
    val links = books.map { Linker.buildBibleGatewayAppLink("$it 1", "NIV") }
    assertEquals(66, links.filterNotNull().distinct().size, books.zip(links).toString())
  }

  @Test
  fun deuterocanonAndPsalm151StayOnTheWebsite() {
    // An unknown or translation-missing book silently opens the app's first book.
    assertNull(Linker.buildBibleGatewayAppLink("Tobit 1", "NRSVUE"))
    assertNull(Linker.buildBibleGatewayAppLink("Greek Esther 3", "NRSVUE"))
    assertNull(Linker.buildBibleGatewayAppLink("Psalm 151", "NRSVUE"))
    assertNull(Linker.buildBibleGatewayAppLink("Enoch 1", "NIV"))
    assertNull(Linker.buildBibleGatewayAppLink("John 3", "N IV"))
  }

  @Test
  fun derivesTheAppLinkFromEitherPlatformsGatewayUrlEncoding() {
    assertEquals(
      "biblegateway://bible/John.3/NIV",
      Linker.bibleGatewayAppLinkForWebUrl(Linker.buildBibleGatewayUrl("John 3", "NIV"))
    )
    // Android URLEncoder form and iOS strict percent-encoding form.
    assertEquals(
      "biblegateway://bible/Rom.8/ESV",
      Linker.bibleGatewayAppLinkForWebUrl("https://www.biblegateway.com/passage/?search=Romans+8&version=ESV")
    )
    assertEquals(
      "biblegateway://bible/1Cor.13/NKJV",
      Linker.bibleGatewayAppLinkForWebUrl("https://www.biblegateway.com/passage/?search=1%20Corinthians%2013&version=NKJV")
    )
  }

  @Test
  fun verseAndNonGatewayUrlsHaveNoAppLink() {
    assertNull(Linker.bibleGatewayAppLinkForWebUrl(Linker.buildBibleGatewayUrl("James 4:17", "NIV")))
    assertNull(Linker.bibleGatewayAppLinkForWebUrl("https://www.biblegateway.com/passage/?search=1%20Corinthians%2013%3A4&version=NKJV"))
    assertNull(Linker.bibleGatewayAppLinkForWebUrl("https://www.bible.com/bible/111/JHN.3.16.NIV"))
    assertNull(Linker.bibleGatewayAppLinkForWebUrl("https://www.biblegateway.com/passage/?search=John+3"))
    assertNull(Linker.bibleGatewayAppLinkForWebUrl(Linker.buildBibleGatewayUrl("Tobit 1", "NRSVUE")))
  }
}
