package com.dividesbyzer0.biblecompanion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotesSelectionToolbarTest {
  @Test
  fun keyboardCopyClearsSelectionWithoutInvokingToolbar() {
    var text: AnnotatedString? = null
    var cleared = false
    val native = object : ClipboardManager {
      override fun setText(annotatedString: AnnotatedString) { text = annotatedString }
      override fun getText(): AnnotatedString? = text
    }
    val clipboard = NotesSelectionClipboard(native) {
      assertEquals("Selected note text", text?.text)
      cleared = true
    }
    clipboard.setText(AnnotatedString("Selected note text"))
    assertTrue(cleared)
    assertTrue(clipboard.hasText())
    assertEquals(text, clipboard.getText())
  }

  @Test
  fun actualSelectionShowsDismissAndCopyClearsItAfterCopying() {
    val native = FakeToolbar()
    var visible = false
    val actions = mutableListOf<String>()
    val clipboard = NotesSelectionClipboard(object : ClipboardManager {
      override fun setText(annotatedString: AnnotatedString) { actions += "copy" }
      override fun getText(): AnnotatedString? = null
    }) {
      actions += "clear"
      visible = false
      native.hide()
    }
    val toolbar = NotesSelectionToolbar(native, { visible = it }, {})

    assertFalse(visible)
    toolbar.showMenu(Rect.Zero, { clipboard.setText(AnnotatedString("note")) }, null, null, null)
    assertTrue(visible)
    assertEquals(TextToolbarStatus.Shown, toolbar.status)
    assertNotNull(native.copy).invoke()
    assertEquals(listOf("copy", "clear"), actions)
    assertFalse(visible)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun hidingSelectionAlsoHidesDismissControl() {
    val native = FakeToolbar()
    var visible = false
    val toolbar = NotesSelectionToolbar(native, { visible = it }, {})
    toolbar.showMenu(Rect.Zero, {}, null, null, null)
    toolbar.hide()
    assertFalse(visible)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
    toolbar.showMenu(Rect.Zero, {}, null, null, null)
    assertTrue(visible)
  }

  @Test
  fun selectAllUsesWholeNoteRatherThanOnlyComposedSection() {
    val native = FakeToolbar()
    var sectionSelected = false
    var wholeNoteSelected = false
    val toolbar = NotesSelectionToolbar(native, {}, { wholeNoteSelected = true })
    toolbar.showMenu(Rect.Zero, {}, null, null, { sectionSelected = true })
    assertNotNull(native.selectAll).invoke()
    assertTrue(wholeNoteSelected)
    assertFalse(sectionSelected)
  }

  @Test
  fun menuWithoutCopyDoesNotShowDismiss() {
    var visible = false
    val toolbar = NotesSelectionToolbar(FakeToolbar(), { visible = it }, {})
    toolbar.showMenu(Rect.Zero, null, null, null, null)
    assertFalse(visible)
  }

  private class FakeToolbar : TextToolbar {
    override var status = TextToolbarStatus.Hidden
    var copy: (() -> Unit)? = null
    var selectAll: (() -> Unit)? = null
    override fun showMenu(
      rect: Rect,
      onCopyRequested: (() -> Unit)?,
      onPasteRequested: (() -> Unit)?,
      onCutRequested: (() -> Unit)?,
      onSelectAllRequested: (() -> Unit)?
    ) {
      copy = onCopyRequested
      selectAll = onSelectAllRequested
      status = TextToolbarStatus.Shown
    }
    override fun hide() { status = TextToolbarStatus.Hidden }
  }
}
