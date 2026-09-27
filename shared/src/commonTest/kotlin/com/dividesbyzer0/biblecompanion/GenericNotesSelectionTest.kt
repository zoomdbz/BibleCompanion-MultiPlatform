package com.dividesbyzer0.biblecompanion

import kotlin.test.Test
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

    val plainText = genericNotesPlainText(source)

    assertTrue("Visible preamble." in plainText)
    assertTrue("Text hidden by the top-level collapse state." in plainText)
    assertTrue("Text hidden by the nested collapse state." in plainText)
    assertTrue("Text that may be outside the lazy viewport." in plainText)
    assertFalse("##" in plainText)
  }
}
