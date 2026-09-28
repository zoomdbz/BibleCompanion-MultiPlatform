package com.dividesbyzer0.biblecompanion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString

/** Keyboard copy bypasses TextToolbar, so observe the clipboard write as well. */
internal class NotesSelectionClipboard(
  private val delegate: ClipboardManager,
  private val onCopied: () -> Unit
) : ClipboardManager by delegate {
  override fun setText(annotatedString: AnnotatedString) {
    delegate.setText(annotatedString)
    onCopied()
  }
}

/** Observe actual selection actions, not long presses on arbitrary page content. */
internal class NotesSelectionToolbar(
  private val delegate: TextToolbar,
  private val onVisibilityChanged: (Boolean) -> Unit,
  private val onSelectWholeNote: () -> Unit,
  private val platformFinishesActionCallback: Boolean = false
) : TextToolbar {
  private var actionCallbackDepth = 0
  private var actionFinishedToolbar = false

  override val status: TextToolbarStatus
    get() = if (platformFinishesActionCallback && actionFinishedToolbar) {
      TextToolbarStatus.Hidden
    } else {
      delegate.status
    }

  private fun guardAction(action: (() -> Unit)?): (() -> Unit)? {
    if (!platformFinishesActionCallback || action == null) return action
    return {
      actionCallbackDepth++
      var completedNormally = false
      try {
        action()
        completedNormally = true
      } finally {
        actionCallbackDepth--
        if (actionCallbackDepth == 0) {
          actionFinishedToolbar = true
          onVisibilityChanged(false)
          // AndroidX finishes the ActionMode immediately after a successful callback.
          // If the callback throws, that platform finish is skipped, so close it here.
          if (!completedNormally) delegate.hide()
        }
      }
    }
  }

  override fun showMenu(
    rect: Rect,
    onCopyRequested: (() -> Unit)?,
    onPasteRequested: (() -> Unit)?,
    onCutRequested: (() -> Unit)?,
    onSelectAllRequested: (() -> Unit)?
  ) {
    actionFinishedToolbar = false
    delegate.showMenu(
      rect = rect,
      onCopyRequested = guardAction(onCopyRequested),
      onPasteRequested = guardAction(onPasteRequested),
      onCutRequested = guardAction(onCutRequested),
      // A section's SelectionContainer cannot select collapsed/offscreen text.
      // Use the complete-note dialog for the native Select all action too.
      onSelectAllRequested = if (platformFinishesActionCallback) {
        guardAction(onSelectWholeNote)
      } else {
        {
          try {
            onSelectWholeNote()
          } finally {
            // UIKit does not finish this action for us. Release its edit menu
            // and temporary first responder before the full-note dialog takes over.
            hide()
          }
        }
      }
    )
    onVisibilityChanged(onCopyRequested != null)
  }

  override fun hide() {
    actionFinishedToolbar = true
    // AndroidX calls ActionMode.finish() after its action callback returns. Calling the
    // delegate from inside that callback destroys the same floating mode twice on Android.
    if (!platformFinishesActionCallback || actionCallbackDepth == 0) delegate.hide()
    onVisibilityChanged(false)
  }
}
