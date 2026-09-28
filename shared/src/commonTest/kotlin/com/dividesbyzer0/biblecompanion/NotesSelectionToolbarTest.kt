package com.dividesbyzer0.biblecompanion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotesSelectionToolbarTest {
  @Test
  fun keyboardCopyClearsSelectionAndHidesToolbarOutsideActionCallback() {
    var text: AnnotatedString? = null
    var cleared = false
    val nativeClipboard = object : ClipboardManager {
      override fun setText(annotatedString: AnnotatedString) { text = annotatedString }
      override fun getText(): AnnotatedString? = text
    }
    val nativeToolbar = FakeToolbar()
    lateinit var toolbar: NotesSelectionToolbar
    val clipboard = NotesSelectionClipboard(nativeClipboard) {
      assertEquals("Selected note text", text?.text)
      cleared = true
      toolbar.hide()
    }
    toolbar = NotesSelectionToolbar(
      nativeToolbar,
      {},
      {},
      platformFinishesActionCallback = true
    )
    toolbar.showMenu(Rect.Zero, {}, null, null, null)

    // A hardware-keyboard copy writes to the clipboard without entering a toolbar callback.
    clipboard.setText(AnnotatedString("Selected note text"))
    assertTrue(cleared)
    assertTrue(clipboard.hasText())
    assertEquals(text, clipboard.getText())
    assertEquals(1, nativeToolbar.hideCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun actualSelectionShowsDismissAndCopyClearsItAfterCopying() {
    val native = FakeToolbar()
    var visible = false
    val actions = mutableListOf<String>()
    lateinit var toolbar: NotesSelectionToolbar
    val clipboard = NotesSelectionClipboard(object : ClipboardManager {
      override fun setText(annotatedString: AnnotatedString) { actions += "copy" }
      override fun getText(): AnnotatedString? = null
    }) {
      actions += "clear"
      toolbar.hide()
    }
    toolbar = NotesSelectionToolbar(
      native,
      { visible = it },
      {},
      platformFinishesActionCallback = true
    )

    assertFalse(visible)
    toolbar.showMenu(Rect.Zero, { clipboard.setText(AnnotatedString("note")) }, null, null, null)
    assertTrue(visible)
    assertEquals(TextToolbarStatus.Shown, toolbar.status)
    native.invokeCopyAsPlatform()
    assertEquals(listOf("copy", "clear"), actions)
    assertFalse(visible)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
    assertEquals(0, native.hideCalls)
    assertEquals(1, native.platformFinishCalls)
  }

  @Test
  fun hidingSelectionAlsoHidesDismissControl() {
    val native = FakeToolbar()
    var visible = false
    val toolbar = NotesSelectionToolbar(
      native,
      { visible = it },
      {},
      platformFinishesActionCallback = true
    )
    toolbar.showMenu(Rect.Zero, {}, null, null, null)
    toolbar.hide()
    assertFalse(visible)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
    assertEquals(1, native.hideCalls)
    toolbar.showMenu(Rect.Zero, {}, null, null, null)
    assertTrue(visible)
  }

  @Test
  fun selectAllUsesWholeNoteRatherThanOnlyComposedSection() {
    val native = FakeToolbar()
    var sectionSelected = false
    var wholeNoteSelected = false
    lateinit var toolbar: NotesSelectionToolbar
    toolbar = NotesSelectionToolbar(
      native,
      {},
      {
        wholeNoteSelected = true
        toolbar.hide()
      },
      platformFinishesActionCallback = true
    )
    toolbar.showMenu(Rect.Zero, {}, null, null, { sectionSelected = true })
    native.invokeSelectAllAsPlatform()
    assertTrue(wholeNoteSelected)
    assertFalse(sectionSelected)
    assertEquals(0, native.hideCalls)
    assertEquals(1, native.platformFinishCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun nonAndroidSelectAllOwnsCleanupWithoutCallerHidingToolbar() {
    val native = FakeToolbar()
    var wholeNoteSelected = false
    val toolbar = NotesSelectionToolbar(native, {}, { wholeNoteSelected = true })
    toolbar.showMenu(Rect.Zero, {}, null, null, null)

    assertNotNull(native.selectAll).invoke()
    assertTrue(wholeNoteSelected)
    assertEquals(1, native.hideCalls)
    assertEquals(0, native.platformFinishCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun nonAndroidSelectAllFailureStillReleasesToolbar() {
    val native = FakeToolbar()
    val toolbar = NotesSelectionToolbar(native, {}, { error("callback failed") })
    toolbar.showMenu(Rect.Zero, {}, null, null, null)

    assertFailsWith<IllegalStateException> { assertNotNull(native.selectAll).invoke() }
    assertEquals(1, native.hideCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun callbackExceptionRestoresGuardAndClosesDelegate() {
    val native = FakeToolbar()
    lateinit var toolbar: NotesSelectionToolbar
    toolbar = NotesSelectionToolbar(
      native,
      {},
      {
        toolbar.hide()
        error("callback failed")
      },
      platformFinishesActionCallback = true
    )
    toolbar.showMenu(Rect.Zero, {}, null, null, null)

    assertFailsWith<IllegalStateException> { native.invokeSelectAllAsPlatform() }
    assertEquals(1, native.hideCalls)
    assertEquals(0, native.platformFinishCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)

    // A later external hide must reach the delegate, proving the callback guard reset.
    toolbar.hide()
    assertEquals(2, native.hideCalls)
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
    var hideCalls = 0
    var platformFinishCalls = 0
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
    override fun hide() {
      hideCalls++
      status = TextToolbarStatus.Hidden
    }

    fun invokeCopyAsPlatform() {
      assertNotNull(copy).invoke()
      platformFinish()
    }

    fun invokeSelectAllAsPlatform() {
      assertNotNull(selectAll).invoke()
      platformFinish()
    }

    private fun platformFinish() {
      platformFinishCalls++
      status = TextToolbarStatus.Hidden
    }
  }
}
