package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenericNotesSelectionTest {

  @Test
  fun fullNoteTextIncludesCollapsedNestedAndOffscreenSections() {
    val source = """
      # Note title

      Visible preamble.

      ## Closed section
      Text hidden by the top-level collapse state.

      ### Closed nested section
      Text hidden by the nested collapse state.

      ## Later section
      Text that may be outside the lazy viewport.
    """.trimIndent()

    val plainText = genericNotesPlainText(
      body = source,
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = false
    )

    assertTrue("Visible preamble." in plainText)
    assertTrue("Text hidden by the top-level collapse state." in plainText)
    assertTrue("Text hidden by the nested collapse state." in plainText)
    assertTrue("Text that may be outside the lazy viewport." in plainText)
    assertFalse("##" in plainText)
  }

  @Test
  fun fullNoteTextUsesTraditionalDivineNameWhenHighlightingIsActive() {
    val plainText = genericNotesPlainText(
      body = "kept the appointed feasts of YHWH (**Leviticus 23**)",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = true
    )

    assertEquals("kept the appointed feasts of the LORD (Leviticus 23)", plainText)
    assertFalse("[DN]" in plainText)
  }

  @Test
  fun fullNoteTextPreservesTraditionalModeWithoutHighlighting() {
    val plainText = genericNotesPlainText(
      body = "YHWH",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = false
    )

    assertEquals("YHWH", plainText)
  }

  @Test
  fun fullNoteTextUsesSelectedExplicitDivineNameAndStripsMarkers() {
    val plainText = genericNotesPlainText(
      body = "[J]Jesus[/J] names YHWH and [DN]the LORD[/DN]",
      divineName = "yahweh",
      appLanguage = "en",
      divineNameColorActive = true
    )

    assertEquals("Jesus names Yahweh and Yahweh", plainText)
    assertFalse("[J]" in plainText)
    assertFalse("[DN]" in plainText)
  }

  @Test
  fun fullNoteTextUsesYhwhMode() {
    val plainText = genericNotesPlainText(
      body = "Yahweh",
      divineName = "yhwh",
      appLanguage = "en",
      divineNameColorActive = false
    )

    assertEquals("YHWH", plainText)
  }

  @Test
  fun fullNoteTextUsesRenderedLocaleForTraditionalName() {
    val plainText = genericNotesPlainText(
      body = "YHWH",
      divineName = "traditional",
      appLanguage = "de",
      divineNameColorActive = true
    )

    assertEquals("HERR", plainText)
  }

  @Test
  fun fullNoteTextPreservesCurrentHeadingRendering() {
    val plainText = genericNotesPlainText(
      body = "# YHWH\n\nYHWH",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = true
    )

    assertEquals("YHWH\n\nthe LORD", plainText)
  }

  @Test
  fun fullNoteTextJoinsWrappedParagraphLines() {
    val plainText = genericNotesPlainText(
      body = "first physical line\nsecond physical line",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = false
    )

    assertEquals("first physical line second physical line", plainText)
  }

  @Test
  fun fullNoteTextPreservesMarkdownHardBreaks() {
    val plainText = genericNotesPlainText(
      body = "first display line  \nsecond display line",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = false
    )

    assertEquals("first display line\nsecond display line", plainText)
  }

  @Test
  fun fullNoteTextTreatsIndentedHashLineAsRenderedBodyText() {
    val plainText = genericNotesPlainText(
      body = " # YHWH",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = true
    )

    assertEquals("# the LORD", plainText)
  }

  @Test
  fun fullNoteTextTreatsLevelFiveHeadingAsRenderedBodyText() {
    val plainText = genericNotesPlainText(
      body = "##### YHWH",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = true
    )

    assertEquals("##### the LORD", plainText)
  }

  @Test
  fun fullNoteTextTransformsContextSensitiveNameAfterJoiningSourceLines() {
    val plainText = genericNotesPlainText(
      body = "The angel of\nthe LORD spoke.",
      divineName = "yahweh",
      appLanguage = "en",
      divineNameColorActive = false
    )

    assertEquals("The angel of Yahweh spoke.", plainText)
  }

  @Test
  fun fullNoteTextKeepsSectionHeadingAsBlockBoundaryWithoutBlankLine() {
    val plainText = genericNotesPlainText(
      body = "intro\n## YHWH\nbody",
      divineName = "traditional",
      appLanguage = "en",
      divineNameColorActive = true
    )

    assertEquals("intro\nYHWH\nbody", plainText)
  }
}
