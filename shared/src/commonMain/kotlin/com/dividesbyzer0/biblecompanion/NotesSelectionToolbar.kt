package com.dividesbyzer0.biblecompanion

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/** Keyboard copy bypasses TextToolbar. Observe completed writes, including clipboard clears. */
internal class NotesSelectionClipboard(
  private val delegate: Clipboard,
  private val selectionGeneration: () -> Long = { 0L },
  private val onCopied: () -> Unit
) : Clipboard by delegate {
  override suspend fun setClipEntry(clipEntry: ClipEntry?) {
    val copiedSelection = selectionGeneration()
    delegate.setClipEntry(clipEntry)
    // A delayed write must not dismiss a newer selection or a different page.
    if (copiedSelection == selectionGeneration()) onCopied()
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
  private var platformOwnsActionFinish = false

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
          platformOwnsActionFinish = completedNormally
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
    platformOwnsActionFinish = false
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
    // delegate inside that callback or after a suspended copy completes would finish it twice.
    if (!platformFinishesActionCallback ||
      (actionCallbackDepth == 0 && !platformOwnsActionFinish)) delegate.hide()
    onVisibilityChanged(false)
  }
}
