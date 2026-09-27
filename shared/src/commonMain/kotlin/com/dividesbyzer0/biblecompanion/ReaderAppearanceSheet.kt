package com.dividesbyzer0.biblecompanion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private data class ReaderPreview(
  val collection: String,
  val bookId: String,
  val title: String,
  val reference: String,
  val story: Story
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderAppearanceSheet(
  prefs: PrefsState,
  repo: PrefsRepo,
  onDismiss: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val context = LocalPlatformContext.current
  var textScale by remember(prefs.textSizeScale) { mutableStateOf(prefs.textSizeScale) }
  var lineSpacing by remember(prefs.readingLineSpacing) { mutableStateOf(prefs.readingLineSpacing) }
  var versePerLine by remember(prefs.versePerLine) { mutableStateOf(prefs.versePerLine) }
  val preview = remember(
    context,
    prefs.appLanguage,
    prefs.internalBibleVersion,
    prefs.lastReadCollection,
    prefs.lastReadBookId,
    prefs.lastReadStoryId
  ) {
    loadReaderPreview(context, prefs)
  }
  val previewPrefs = prefs.copy(
    textSizeScale = textScale,
    readingLineSpacing = lineSpacing,
    versePerLine = versePerLine
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(Res.string.ui_reading_appearance),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
        }
      }

      if (preview != null) {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              preview.title,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            if (preview.reference.isNotBlank()) {
              Text(
                preview.reference,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            // Keep the controls in a stable position while the live preview
            // reflows for a larger font or looser line spacing.
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .verticalScroll(rememberScrollState())
            ) {
              ReaderScripture(
                story = preview.story,
                col = preview.collection,
                prefs = previewPrefs,
                defaultBook = preview.bookId,
                selectedBullets = emptySet(),
                savedVerseColors = emptyMap(),
                goldFadeBulletIdxs = emptySet(),
                onToggleBullet = null,
                onCopyBullet = null,
                listState = null,
                viewportTopY = 0f,
                viewportHeightPx = 0
              )
            }
          }
        }
      }

      Text(stringResource(Res.string.font_label), style = MaterialTheme.typography.titleSmall)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = prefs.fontMode != "serif",
          onClick = { scope.launch { repo.setFontMode("sans") } },
          label = { Text(stringResource(Res.string.font_sans)) }
        )
        FilterChip(
          selected = prefs.fontMode == "serif",
          onClick = { scope.launch { repo.setFontMode("serif") } },
          label = { Text(stringResource(Res.string.font_serif)) }
        )
      }

      Text(stringResource(Res.string.text_size), style = MaterialTheme.typography.titleSmall)
      Slider(
        value = textScale,
        onValueChange = { value ->
          val snapped = kotlin.math.round(value * 20f) / 20f
          textScale = snapped
          scope.launch { repo.setTextSizeScale(snapped) }
        },
        valueRange = 0.8f..1.6f,
        modifier = Modifier.fillMaxWidth()
      )

      Text(stringResource(Res.string.ui_line_spacing), style = MaterialTheme.typography.titleSmall)
      Slider(
        value = lineSpacing,
        onValueChange = { value ->
          val snapped = kotlin.math.round(value * 20f) / 20f
          lineSpacing = snapped
          scope.launch { repo.setReadingLineSpacing(snapped) }
        },
        valueRange = 1.3f..2.1f,
        modifier = Modifier.fillMaxWidth()
      )

      Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          stringResource(Res.string.ui_verse_per_line),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f)
        )
        Switch(
          checked = versePerLine,
          onCheckedChange = { enabled ->
            versePerLine = enabled
            scope.launch { repo.setVersePerLine(enabled) }
          }
        )
      }
      Spacer(Modifier.height(4.dp))
    }
  }
}

private fun loadReaderPreview(
  context: com.dividesbyzer0.biblecompanion.platform.PlatformContext,
  prefs: PrefsState
): ReaderPreview? {
  val preferredCollection = prefs.lastReadCollection
    ?.takeIf { it in setOf("old_testament", "new_testament", "deuterocanonical") }
  val collections = listOfNotNull(preferredCollection, "old_testament", "new_testament").distinct()

  for (collection in collections) {
    val localizedBooks = ContentRepo.listBooksLocalized(context, collection, prefs.appLanguage)
    val preferredBook = prefs.lastReadBookId
      ?.takeIf { id -> collection == preferredCollection && localizedBooks.any { it.first == id } }
    val bookId = preferredBook ?: localizedBooks.firstOrNull()?.first ?: continue
    val loaded = ContentRepo.loadBookWithEdition(
      context = context,
      collection = collection,
      bookId = bookId,
      appLang = prefs.appLanguage,
      internalBibleVersion = prefs.internalBibleVersion
    ) ?: continue
    val story = loaded.book.stories.firstOrNull { it.id == prefs.lastReadStoryId }
      ?: loaded.book.stories.firstOrNull { it.summaryBullets.isNotEmpty() }
      ?: continue
    val previewBullets = story.summaryBullets.take(2)
    if (previewBullets.isEmpty()) continue
    val previewAnchors = previewBullets.mapNotNull(::verseAnchorFromText)
    val previewStory = story.copy(
      summaryBullets = previewBullets,
      headings = story.headings.filter { heading ->
        previewAnchors.any { heading.beforeVerse in it.verseStart..it.verseEnd }
      }
    )
    return ReaderPreview(
      collection = collection,
      bookId = loaded.book.id,
      title = loaded.book.title,
      reference = story.refs.firstOrNull().orEmpty(),
      story = previewStory
    )
  }
  return null
}
