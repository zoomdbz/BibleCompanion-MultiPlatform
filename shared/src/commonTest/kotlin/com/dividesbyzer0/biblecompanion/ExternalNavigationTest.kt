package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalNavigationTest {
  @Test
  fun retiredSearchRouteFocusesHomeSearch() {
    assertEquals(ExternalNavigationTarget.FocusSearch, externalNavigationTarget("search"))
  }

  @Test
  fun activeStaticRoutesCanNavigate() {
    assertEquals(
      ExternalNavigationTarget.Navigate(Dest.SavedItems.route),
      externalNavigationTarget(Dest.SavedItems.route)
    )
    assertEquals(
      ExternalNavigationTarget.Navigate(Dest.FeastCalendar.route),
      externalNavigationTarget(Dest.FeastCalendar.route)
    )
  }

  @Test
  fun bookRouteKeepsVerseLanguageAndEditionArguments() {
    val route = Dest.BookView.route(
      "new_testament",
      "matthew",
      "matthew-5",
      verse = 3,
      verseEnd = 7,
      sourceLang = "zh-Hant",
      sourceEdition = "rcuv"
    )
    assertEquals(ExternalNavigationTarget.Navigate(route), externalNavigationTarget(route))
  }

  @Test
  fun notificationBookRouteRemainsValid() {
    val route = Dest.BookView.route(
      "old_testament",
      "psalms",
      "psalms-23",
      verse = 1,
      sourceLang = "en",
      sourceEdition = "bsb"
    )
    assertEquals(ExternalNavigationTarget.Navigate(route), externalNavigationTarget(route))
  }

  @Test
  fun readerRoutesAcceptBothBooleanTtsValues() {
    listOf("true", "false").forEach { value ->
      val route = "book/new_testament/matthew?storyId=matthew-5&autoStartTts=$value"
      assertEquals(ExternalNavigationTarget.Navigate(route), externalNavigationTarget(route))
    }
    assertNull(externalNavigationTarget("book/new_testament/matthew?autoStartTts=invalid"))
  }

  @Test
  fun unknownOrMalformedRoutesAreRejected() {
    listOf(
      null,
      "",
      "unknown",
      "search/extra",
      "book/unknown/matthew",
      "book/new_testament",
      "book/new_testament/matthew/5",
      "book/new_testament/matthew?unknown=value",
      "book/new_testament/matthew?verse=0",
      "book/new_testament/matthew?verse=4&verseEnd=3",
      "book/new_testament/matthew?verseEnd=4",
      "book/new_testament/matthew?sourceLang=en&sourceLang=de",
      "book/new_testament/matthew?storyId=bad value",
      "book/new_testament/matthew?storyId=bad%2"
    ).forEach { route -> assertNull(externalNavigationTarget(route), route) }
  }
}
