package com.dividesbyzer0.biblecompanion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotesSelectionToolbarTest {
  @Test
  fun clipboardWriteClearsSelectionAndHidesToolbarOutsideActionCallback() {
    var cleared = false
    val nativeClipboard = FakeClipboard()
    val nativeToolbar = FakeToolbar()
    lateinit var toolbar: NotesSelectionToolbar
    val clipboard = NotesSelectionClipboard(nativeClipboard) {
      assertEquals(1, nativeClipboard.writeCalls)
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

    // Keyboard writes bypass the toolbar. A null entry tests the common API without
    // constructing a native ClipData/UIPasteboard object in a host test.
    val write = startSuspend { clipboard.setClipEntry(null) }
    assertNotNull(write.result).getOrThrow()
    assertTrue(cleared)
    val read = startSuspend { assertEquals(null, clipboard.getClipEntry()) }
    assertNotNull(read.result).getOrThrow()
    assertEquals(1, nativeClipboard.readCalls)
    assertEquals(1, nativeToolbar.hideCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun actualSelectionShowsDismissAndCopyClearsItAfterCopying() {
    val native = FakeToolbar()
    var visible = false
    val actions = mutableListOf<String>()
    lateinit var toolbar: NotesSelectionToolbar
    val clipboard = NotesSelectionClipboard(FakeClipboard(onWrite = { actions += "copy" })) {
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
    toolbar.showMenu(Rect.Zero, {
      val write = startSuspend { clipboard.setClipEntry(null) }
      assertNotNull(write.result).getOrThrow()
    }, null, null, null)
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
  fun suspendedAndroidWriteDoesNotFinishThePlatformToolbarTwice() {
    val gate = CompletableDeferred<Unit>()
    val nativeClipboard = FakeClipboard(beforeWrite = { gate.await() })
    val native = FakeToolbar()
    var visible = false
    var cleared = false
    lateinit var toolbar: NotesSelectionToolbar
    val clipboard = NotesSelectionClipboard(nativeClipboard) {
      cleared = true
      toolbar.hide()
    }
    toolbar = NotesSelectionToolbar(native, { visible = it }, {}, true)
    var write: SuspendResult? = null
    toolbar.showMenu(Rect.Zero, {
      write = startSuspend { clipboard.setClipEntry(null) }
    }, null, null, null)

    native.invokeCopyAsPlatform()
    assertEquals(null, assertNotNull(write).result)
    assertFalse(cleared)
    assertFalse(visible)
    assertEquals(1, native.platformFinishCalls)
    gate.complete(Unit)

    assertNotNull(assertNotNull(write).result).getOrThrow()
    assertTrue(cleared)
    assertEquals(1, nativeClipboard.writeCalls)
    assertEquals(0, native.hideCalls)
    assertEquals(1, native.platformFinishCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)

    // A later selection must restore ordinary external-hide behavior.
    toolbar.showMenu(Rect.Zero, {}, null, null, null)
    assertTrue(visible)
    toolbar.hide()
    assertEquals(1, native.hideCalls)
  }

  @Test
  fun suspendedNonAndroidWriteHidesTheToolbarAfterItCompletes() {
    val gate = CompletableDeferred<Unit>()
    val native = FakeToolbar()
    var visible = false
    var cleared = false
    lateinit var toolbar: NotesSelectionToolbar
    val clipboard = NotesSelectionClipboard(FakeClipboard(beforeWrite = { gate.await() })) {
      cleared = true
      toolbar.hide()
    }
    toolbar = NotesSelectionToolbar(native, { visible = it }, {})
    var write: SuspendResult? = null
    toolbar.showMenu(Rect.Zero, {
      write = startSuspend { clipboard.setClipEntry(null) }
    }, null, null, null)

    assertNotNull(native.copy).invoke()
    assertEquals(null, assertNotNull(write).result)
    assertFalse(cleared)
    assertTrue(visible)
    assertEquals(0, native.hideCalls)
    gate.complete(Unit)

    assertNotNull(assertNotNull(write).result).getOrThrow()
    assertTrue(cleared)
    assertFalse(visible)
    assertEquals(1, native.hideCalls)
    assertEquals(0, native.platformFinishCalls)
    assertEquals(TextToolbarStatus.Hidden, toolbar.status)
  }

  @Test
  fun failedClipboardWriteDoesNotReportCopyOrClearSelection() {
    val gate = CompletableDeferred<Unit>()
    val failure = IllegalStateException("Clipboard write failed")
    val native = FakeClipboard(beforeWrite = { gate.await(); throw failure })
    var cleared = false
    val clipboard = NotesSelectionClipboard(native) { cleared = true }
    val write = startSuspend { clipboard.setClipEntry(null) }

    assertEquals(null, write.result)
    assertFalse(cleared)
    gate.complete(Unit)
    val observed = assertNotNull(assertNotNull(write.result).exceptionOrNull())
    assertTrue(observed is IllegalStateException)
    assertEquals(failure.message, observed.message)
    assertFalse(cleared)
    assertEquals(0, native.writeCalls)
  }

  @Test
  fun cancelledClipboardWriteDoesNotReportCopyOrClearSelection() {
    val gate = CompletableDeferred<Unit>()
    val native = FakeClipboard(beforeWrite = { gate.await() })
    var cleared = false
    val clipboard = NotesSelectionClipboard(native) { cleared = true }
    val write = startSuspend { clipboard.setClipEntry(null) }

    gate.cancel(CancellationException("Note screen closed"))

    assertTrue(assertNotNull(write.result).exceptionOrNull() is CancellationException)
    assertFalse(cleared)
    assertEquals(0, native.writeCalls)
  }

  @Test
  fun delayedWriteDoesNotClearANewerSelectionOrDisposedPage() {
    val gate = CompletableDeferred<Unit>()
    val native = FakeClipboard(beforeWrite = { gate.await() })
    var generation = 1L
    var cleared = false
    val clipboard = NotesSelectionClipboard(native, selectionGeneration = { generation }) {
      cleared = true
    }
    val write = startSuspend { clipboard.setClipEntry(null) }

    // A new menu, explicit dismissal, or page disposal invalidates the old cleanup.
    generation++
    gate.complete(Unit)

    assertNotNull(write.result).getOrThrow()
    assertEquals(1, native.writeCalls)
    assertFalse(cleared)
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

  private class SuspendResult {
    var result: Result<Unit>? = null
  }

  private fun startSuspend(block: suspend () -> Unit): SuspendResult {
    val probe = SuspendResult()
    block.startCoroutine(object : Continuation<Unit> {
      override val context = EmptyCoroutineContext
      override fun resumeWith(result: Result<Unit>) {
        probe.result = result
      }
    })
    return probe
  }

  private class FakeClipboard(
    private val beforeWrite: suspend () -> Unit = {},
    private val onWrite: () -> Unit = {}
  ) : Clipboard {
    var writeCalls = 0
    var readCalls = 0
    private var entry: ClipEntry? = null
    override val nativeClipboard: NativeClipboard
      get() = error("Native clipboard access is not needed for these tests")

    override suspend fun getClipEntry(): ClipEntry? {
      readCalls++
      return entry
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
      beforeWrite()
      entry = clipEntry
      writeCalls++
      onWrite()
    }
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
