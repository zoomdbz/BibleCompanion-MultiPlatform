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
  private val onSelectWholeNote: () -> Unit
) : TextToolbar {
  override val status: TextToolbarStatus get() = delegate.status

  override fun showMenu(
    rect: Rect,
    onCopyRequested: (() -> Unit)?,
    onPasteRequested: (() -> Unit)?,
    onCutRequested: (() -> Unit)?,
    onSelectAllRequested: (() -> Unit)?
  ) {
    delegate.showMenu(
      rect = rect,
      onCopyRequested = onCopyRequested,
      onPasteRequested = onPasteRequested,
      onCutRequested = onCutRequested,
      // A section's SelectionContainer cannot select collapsed/offscreen text.
      // Use the complete-note dialog for the native Select all action too.
      onSelectAllRequested = onSelectWholeNote
    )
    onVisibilityChanged(onCopyRequested != null)
  }

  override fun hide() {
    delegate.hide()
    onVisibilityChanged(false)
  }
}
