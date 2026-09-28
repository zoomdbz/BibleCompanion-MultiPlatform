package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyTabNavigationTest {
  @Test
  fun studyReselectionReturnsFromNotesAndNestedDetailPages() {
    listOf(
      Dest.JesusIdentity,
      Dest.TranslationNotes,
      Dest.FAQs,
      Dest.Prophecy,
      Dest.DanielsTimeline,
      Dest.AstronomicalSigns,
      Dest.AboutCalendars,
      Dest.OrdainedFeasts,
      Dest.BibleChronology
    ).forEach { destination ->
      assertTrue(shouldReturnToStudyRoot("tab_study", "tab_study", destination.route))
    }
  }

  @Test
  fun directHomeNoteEntryDoesNotRequireStudyHubInBackStack() {
    // The destination's graph identifies Study even if Home opened the note directly.
    assertTrue(shouldReturnToStudyRoot("tab_study", "tab_study", Dest.Gospel.route))
  }

  @Test
  fun studyHubReselectionDoesNotAddAnotherHub() {
    assertFalse(shouldReturnToStudyRoot("tab_study", "tab_study", Dest.Study.route))
  }

  @Test
  fun enteringStudyFromOtherTabsKeepsTheirNormalStateSaving() {
    assertFalse(shouldReturnToStudyRoot("tab_study", "tab_read", "book/new_testament/john"))
    assertFalse(shouldReturnToStudyRoot("tab_study", Dest.Home.route, Dest.Home.route))
    assertFalse(shouldReturnToStudyRoot("tab_study", "tab_calendar", Dest.FeastCalendar.route))
  }

  @Test
  fun otherButtonsAndUninitializedNavigationDoNotResetStudy() {
    assertFalse(shouldReturnToStudyRoot(Dest.Home.route, "tab_study", Dest.JesusIdentity.route))
    assertFalse(shouldReturnToStudyRoot("tab_read", "tab_study", Dest.JesusIdentity.route))
    assertFalse(shouldReturnToStudyRoot("tab_calendar", "tab_study", Dest.JesusIdentity.route))
    assertFalse(shouldReturnToStudyRoot("tab_study", null, null))
    assertFalse(shouldReturnToStudyRoot("tab_study", "tab_study", null))
  }
}
