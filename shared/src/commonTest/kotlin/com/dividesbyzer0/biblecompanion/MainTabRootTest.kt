package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainTabRootTest {
  @Test
  fun homeButtonTargetsHome() {
    assertEquals(Dest.Home.route, mainTabRootRoute("home"))
  }

  @Test
  fun readButtonTargetsLibraryInsteadOfLastBook() {
    assertEquals(Dest.Read.route, mainTabRootRoute("tab_read"))
  }

  @Test
  fun studyButtonTargetsHubInsteadOfLastNote() {
    assertEquals(Dest.Study.route, mainTabRootRoute("tab_study"))
  }

  @Test
  fun calendarButtonTargetsCalendarInsteadOfLinkedReader() {
    assertEquals(Dest.FeastCalendar.route, mainTabRootRoute("tab_calendar"))
  }

  @Test
  fun leafAndUnknownRoutesAreNotMainButtons() {
    assertNull(mainTabRootRoute(Dest.JesusIdentity.route))
    assertNull(mainTabRootRoute("book/new_testament/john"))
    assertNull(mainTabRootRoute("unknown"))
  }
}
