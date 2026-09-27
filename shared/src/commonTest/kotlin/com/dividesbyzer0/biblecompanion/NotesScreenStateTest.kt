package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NotesScreenStateTest {
  @Test
  fun stateIsIndependentPerLanguage() {
    val english = NotesScreenState(
      expandedHeaders = setOf("English section"),
      firstVisibleItem = 3,
      firstVisibleOffset = 17
    )
    val arabic = NotesScreenState(
      expandedHeaders = setOf("Arabic section"),
      firstVisibleItem = 8,
      firstVisibleOffset = 29
    )

    val withEnglish = mergeNotesScreenState(
      "{}", "en", "prophecy/revelation.md", Json.encodeToString(english)
    )
    val withBoth = mergeNotesScreenState(
      withEnglish, "ar", "prophecy/revelation.md", Json.encodeToString(arabic)
    )

    assertEquals(english, readNotesScreenState(withBoth, "en", "prophecy/revelation.md"))
    assertEquals(arabic, readNotesScreenState(withBoth, "ar", "prophecy/revelation.md"))
    assertEquals(NotesScreenState(), readNotesScreenState(withBoth, "hi", "prophecy/revelation.md"))
  }

  @Test
  fun liveStateBeatsStalePrefsWithoutCrossingLanguages() {
    val assetFileName = "live-cache-stale-pref-test.md"
    val staleEnglish = NotesScreenState(
      expandedHeaders = setOf("Persisted English section"),
      firstVisibleItem = 1,
      firstVisibleOffset = 12
    )
    val persistedArabic = NotesScreenState(
      expandedHeaders = setOf("Persisted Arabic section"),
      firstVisibleItem = 4,
      firstVisibleOffset = 27
    )
    val withEnglish = mergeNotesScreenState(
      "{}", "en", assetFileName, Json.encodeToString(staleEnglish)
    )
    val stalePrefs = mergeNotesScreenState(
      withEnglish, "ar", assetFileName, Json.encodeToString(persistedArabic)
    )
    val latestEnglish = NotesScreenState(
      expandedHeaders = setOf("Latest English section"),
      expandedPaths = setOf(notesHeaderPath("Latest English section", "Child")),
      firstVisibleItem = 9,
      firstVisibleOffset = 41
    )

    LiveNotesScreenStates.record("en", assetFileName, latestEnglish)

    assertEquals(latestEnglish, LiveNotesScreenStates.read(stalePrefs, "en", assetFileName))
    assertEquals(persistedArabic, LiveNotesScreenStates.read(stalePrefs, "ar", assetFileName))
  }

  @Test
  fun nestedHeaderPathsCannotCollideOnSeparatorsOrParents() {
    val separatorInParent = notesHeaderPath("A/B", "C")
    val separatorInChild = notesHeaderPath("A", "B/C")
    val differentParent = notesHeaderPath("Other", "C")

    assertNotEquals(separatorInParent, separatorInChild)
    assertNotEquals(separatorInParent, differentParent)
    assertEquals(separatorInParent, notesHeaderPath("A/B", "C"))
  }

  @Test
  fun legacyBareFilenameMigratesH2HeadersOnRead() {
    val raw = """{"revelation_timeline.md":["The Seven Seals","The Seven Trumpets"]}"""

    val state = readNotesScreenState(raw, "en", "revelation_timeline.md")

    assertEquals(setOf("The Seven Seals", "The Seven Trumpets"), state.expandedHeaders)
    assertTrue(state.expandedPaths.isEmpty())
    assertEquals(0, state.firstVisibleItem)
    assertEquals(0, state.firstVisibleOffset)
  }

  @Test
  fun explicitEmptyStatePreventsLegacyHeadersFromResurrecting() {
    val legacy = """{"revelation_timeline.md":["The Seven Seals"]}"""
    val merged = mergeNotesScreenState(
      legacy,
      "en",
      "revelation_timeline.md",
      Json.encodeToString(NotesScreenState())
    )

    assertEquals(NotesScreenState(), readNotesScreenState(merged, "en", "revelation_timeline.md"))
    assertTrue(Json.parseToJsonElement(merged).jsonObject.containsKey("revelation_timeline.md"))
    assertTrue(Json.parseToJsonElement(merged).jsonObject.containsKey("en/revelation_timeline.md"))
  }

  @Test
  fun jsonRoundTripPreservesExpansionAndScroll() {
    val expected = NotesScreenState(
      expandedHeaders = setOf("First", "Second"),
      expandedPaths = setOf(
        notesHeaderPath("First", "Child"),
        notesHeaderPath("First", "Child", "Grandchild")
      ),
      firstVisibleItem = 14,
      firstVisibleOffset = 237
    )

    val raw = mergeNotesScreenState(
      "{}", "ko", "nested/path/notes.md", Json.encodeToString(expected)
    )

    assertEquals(expected, readNotesScreenState(raw, "ko", "nested/path/notes.md"))
  }

  @Test
  fun corruptInputFallsBackWithoutThrowing() {
    assertEquals(NotesScreenState(), readNotesScreenState("not json", "en", "notes.md"))
    assertEquals(
      NotesScreenState(),
      readNotesScreenState("""{"en/notes.md":{"expandedHeaders":42}}""", "en", "notes.md")
    )

    val validRaw = """{"other.md":["Keep me"]}"""
    assertEquals(validRaw, mergeNotesScreenState(validRaw, "en", "notes.md", "not json"))

    val recoveredState = NotesScreenState(firstVisibleItem = 2, firstVisibleOffset = 19)
    val recoveredRaw = mergeNotesScreenState(
      "corrupt root", "en", "notes.md", Json.encodeToString(recoveredState)
    )
    assertEquals(recoveredState, readNotesScreenState(recoveredRaw, "en", "notes.md"))
  }

  @Test
  fun mergePreservesOtherScreensAndLegacyEntries() {
    val otherState = NotesScreenState(
      expandedHeaders = setOf("Other screen"),
      firstVisibleItem = 5,
      firstVisibleOffset = 11
    )
    val original = mergeNotesScreenState(
      """{"legacy.md":["Legacy H2"],"unrelated":{"value":"keep"}}""",
      "ru",
      "other.md",
      Json.encodeToString(otherState)
    )
    val targetState = NotesScreenState(
      expandedPaths = setOf(notesHeaderPath("Parent", "Child")),
      firstVisibleItem = 9,
      firstVisibleOffset = 41
    )

    val merged = mergeNotesScreenState(
      original, "en", "target.md", Json.encodeToString(targetState)
    )
    val root = Json.parseToJsonElement(merged).jsonObject

    assertEquals(otherState, readNotesScreenState(merged, "ru", "other.md"))
    assertEquals(targetState, readNotesScreenState(merged, "en", "target.md"))
    assertTrue(root.containsKey("legacy.md"))
    assertTrue(root.containsKey("unrelated"))
  }
}
