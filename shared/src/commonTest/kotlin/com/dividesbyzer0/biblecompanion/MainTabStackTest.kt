package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTabStackTest {
  @Test
  fun calendarSelectionPeelsReadAndRevealsCalendar() {
    val result = peel(
      target = "tab_calendar",
      entries = listOf(
        entry("read-book", "tab_read", "book/gospel_thomas/gospel_thomas"),
        entry("calendar", "tab_calendar", Dest.FeastCalendar.route)
      )
    )

    assertEquals(listOf("read-book" to true), result.calls)
    assertEquals("calendar", result.currentId)
  }

  @Test
  fun deeperMixedStackPeelsEachForeignGraphInOrder() {
    val result = peel(
      target = "tab_study",
      entries = listOf(
        entry("read-book", "tab_read", "book/new_testament/john"),
        entry("calendar", "tab_calendar", Dest.FeastCalendar.route),
        entry("study-note", "tab_study", Dest.Gospel.route)
      )
    )

    assertEquals(listOf("read-book" to true, "calendar" to true), result.calls)
    assertEquals("study-note", result.currentId)
  }

  @Test
  fun homeSelectionSplitsEveryOwnedGraph() {
    val result = peel(
      target = Dest.Home.route,
      entries = listOf(
        entry("read-book", "tab_read", "book/old_testament/genesis"),
        entry("calendar", "tab_calendar", Dest.FeastCalendar.route),
        entry("study-note", "tab_study", Dest.Gospel.route),
        entry("home", Dest.Home.route, Dest.Home.route)
      )
    )

    assertEquals(
      listOf("read-book" to true, "calendar" to true, "study-note" to true),
      result.calls
    )
    assertEquals("home", result.currentId)
  }

  @Test
  fun targetAlreadyCurrentDoesNothing() {
    val result = peel(
      target = "tab_calendar",
      entries = listOf(entry("calendar", "tab_calendar", Dest.FeastCalendar.route))
    )

    assertEquals(emptyList(), result.calls)
    assertEquals("calendar", result.currentId)
  }

  @Test
  fun unownedAndMissingEntriesDoNothing() {
    val unknown = peel(
      target = "tab_calendar",
      entries = listOf(entry("settings", null, Dest.Settings.route))
    )
    val missing = peel(target = "tab_calendar", entries = emptyList())

    assertEquals(emptyList(), unknown.calls)
    assertEquals("settings", unknown.currentId)
    assertEquals(emptyList(), missing.calls)
    assertEquals(null, missing.currentId)
  }

  @Test
  fun duplicateGraphsSaveOnlyTheLatestOccurrence() {
    val result = peel(
      target = "tab_calendar",
      entries = listOf(
        entry("read-new", "tab_read", "book/new_testament/john"),
        entry("read-old", "tab_read", Dest.Read.route),
        entry("calendar", "tab_calendar", Dest.FeastCalendar.route)
      )
    )

    assertEquals(listOf("read-new" to true, "read-old" to false), result.calls)
    assertEquals("calendar", result.currentId)
  }

  @Test
  fun sharedSavedTabsKeepsHistoricalRestorePassFromReplacingNewerSnapshot() {
    val savedTabs = mutableSetOf<String>()
    val first = peel(
      target = Dest.Home.route,
      entries = listOf(
        entry("read-live", "tab_read", "book/new_testament/john"),
        entry("home", Dest.Home.route, Dest.Home.route)
      ),
      savedTabs = savedTabs
    )
    val restored = peel(
      target = "tab_calendar",
      entries = listOf(
        entry("read-historical", "tab_read", "book/new_testament/matthew"),
        entry("calendar", "tab_calendar", Dest.FeastCalendar.route)
      ),
      savedTabs = savedTabs
    )

    assertEquals(listOf("read-live" to true), first.calls)
    assertEquals(listOf("read-historical" to false), restored.calls)
    assertEquals(setOf("tab_read"), savedTabs)
  }

  @Test
  fun nonProgressingCallbackStopsAfterTheSameEntryRepeats() {
    val stuck = entry("read-book", "tab_read", "book/new_testament/john")
    val calls = mutableListOf<Pair<String, Boolean>>()

    saveForeignMainTabStacks(
      targetTab = "tab_calendar",
      currentEntry = { stuck },
      saveAndPop = { current, save -> calls += current.id to save }
    )

    assertEquals(listOf("read-book" to true), calls)
  }

  private fun peel(
    target: String,
    entries: List<MainTabStackEntry>,
    savedTabs: MutableSet<String> = mutableSetOf()
  ): PeelResult {
    var index = 0
    val calls = mutableListOf<Pair<String, Boolean>>()
    saveForeignMainTabStacks(
      targetTab = target,
      currentEntry = { entries.getOrNull(index) },
      saveAndPop = { current, save ->
        calls += current.id to save
        index++
      },
      savedTabs = savedTabs
    )
    return PeelResult(calls, entries.getOrNull(index)?.id)
  }

  private fun entry(id: String, tabRoute: String?, destinationRoute: String) =
    MainTabStackEntry(id, tabRoute, destinationRoute)

  private data class PeelResult(
    val calls: List<Pair<String, Boolean>>,
    val currentId: String?
  )
}
