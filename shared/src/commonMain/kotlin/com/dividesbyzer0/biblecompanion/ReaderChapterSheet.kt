package com.dividesbyzer0.biblecompanion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

/** The loaded edition supplies every chapter and verse, including native verse bridges. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderChapterSheet(
    book: Book,
    currentStoryId: String?,
    onDismiss: () -> Unit,
    onChooseBook: () -> Unit,
    onIntro: () -> Unit,
    onOpenStory: (String, Int?) -> Unit
) {
    val index = remember(book) { ChapterLocator.build(book) }
    val chapters = remember(index) { index.byChapter.keys.sorted() }
    var verseTab by rememberSaveable { mutableStateOf(false) }
    var chapter by rememberSaveable(book.id) {
        mutableStateOf(index.byChapter.entries.firstOrNull { it.value == currentStoryId }?.key
            ?: chapters.firstOrNull() ?: 1)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val story = book.stories.firstOrNull { it.id == index.byChapter[chapter] }
    val verses = remember(story, chapter) {
        versePickerNumbers(story?.summaryBullets.orEmpty(), chapter, story?.id, book.id)
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(book.title, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onChooseBook) { Text(stringResource(Res.string.ui_choose_book)) }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !verseTab, onClick = { verseTab = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(Res.string.ui_chapters))
                }
                SegmentedButton(selected = verseTab, onClick = { verseTab = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(Res.string.ui_verses))
                }
            }
            Spacer(Modifier.height(12.dp))
            if (verseTab) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }) {
                            Text(story?.title ?: chapter.toString())
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            chapters.forEach { number ->
                                DropdownMenuItem(text = { Text(number.toString()) }, onClick = {
                                    chapter = number
                                    menuOpen = false
                                })
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = { index.byChapter[chapter]?.let { onOpenStory(it, null) } }
                    ) {
                        Text(stringResource(Res.string.ui_open_chapter))
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (book.intro.isNotBlank()) {
                        OutlinedButton(onClick = onIntro) { Text(stringResource(Res.string.intro_section_header)) }
                    }
                    index.specials.forEach { special ->
                        val title = book.stories.firstOrNull { it.id == special.storyId }?.title ?: special.label
                        OutlinedButton(onClick = { onOpenStory(special.storyId, null) }) { Text(title) }
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(if (verseTab) verses else chapters, key = { it }) { number ->
                    val isSelected = !verseTab && number == chapter
                    val numberDescription = if (verseTab) {
                        "${stringResource(Res.string.verse_label)} $number"
                    } else {
                        book.stories.firstOrNull { it.id == index.byChapter[number] }?.title ?: number.toString()
                    }
                    FilledTonalButton(
                        onClick = {
                            if (verseTab) {
                                index.byChapter[chapter]?.let { onOpenStory(it, number) }
                            } else {
                                // Selecting a chapter is deliberately non-navigating: it keeps the
                                // sheet open and takes the reader straight to that chapter's verses.
                                chapter = number
                                verseTab = true
                            }
                        },
                        modifier = Modifier.heightIn(min = 52.dp).semantics {
                            contentDescription = numberDescription
                            selected = isSelected
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface)
                    ) { Text(number.toString()) }
                }
            }
        }
    }
}
