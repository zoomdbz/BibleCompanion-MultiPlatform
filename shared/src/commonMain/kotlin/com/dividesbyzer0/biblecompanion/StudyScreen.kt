package com.dividesbyzer0.biblecompanion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.platformCurrentDate
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class StudyDestination(
  val label: StringResource,
  val icon: ImageVector,
  val destination: Dest,
  val calendarSummary: Boolean = false
)

internal enum class StudyCalendarDetail { ABOUT, APPOINTED }

private data class StudyGroup(
  val heading: StringResource,
  val entries: List<StudyDestination>
)

private val studyGroups = listOf(
  StudyGroup(
    Res.string.ui_foundations,
    listOf(
      StudyDestination(Res.string.gospel, Icons.Filled.AutoAwesome, Dest.Gospel),
      StudyDestination(Res.string.grace, Icons.Filled.Bookmark, Dest.Grace),
      StudyDestination(Res.string.jesus_divinity, Icons.Filled.Lightbulb, Dest.JesusDivinity),
      StudyDestination(Res.string.jesus_identity, Icons.Filled.Person, Dest.JesusIdentity),
      StudyDestination(Res.string.christophanies, Icons.Filled.AutoAwesome, Dest.Christophanies)
    )
  ),
  StudyGroup(
    Res.string.ui_torah_feasts,
    listOf(
      StudyDestination(Res.string.feast_calendar, Icons.Filled.CalendarMonth, Dest.FeastCalendar, calendarSummary = true),
      StudyDestination(
        Res.string.feast_about_heading,
        Icons.Filled.Info,
        destination = Dest.AboutCalendars
      ),
      StudyDestination(
        Res.string.ordained_feasts_heading,
        Icons.Filled.EventNote,
        destination = Dest.OrdainedFeasts
      ),
      StudyDestination(Res.string.torah_feasts_and_gentiles, Icons.AutoMirrored.Filled.MenuBook, Dest.TorahFeastsAndGentiles)
    )
  ),
  StudyGroup(
    Res.string.prophecy,
    listOf(
      StudyDestination(Res.string.prophecy, Icons.Filled.Timeline, Dest.Prophecy),
      StudyDestination(Res.string.prophecy_messianic, Icons.Filled.AutoAwesome, Dest.MessianicProphecy),
      StudyDestination(Res.string.prophecy_daniel, Icons.Filled.Timeline, Dest.DanielsTimeline),
      StudyDestination(Res.string.prophecy_astronomical, Icons.Filled.Lightbulb, Dest.AstronomicalSigns),
      StudyDestination(Res.string.prophecy_revelation, Icons.AutoMirrored.Filled.MenuBook, Dest.RevelationOverview),
      StudyDestination(Res.string.prophecy_revelation_timeline, Icons.Filled.Timeline, Dest.RevelationTimeline),
      StudyDestination(Res.string.prophecy_second_coming_rapture, Icons.Filled.EventNote, Dest.SecondComingRapture)
    )
  ),
  StudyGroup(
    Res.string.ui_discernment,
    listOf(
      StudyDestination(Res.string.false_doctrine, Icons.Filled.Warning, Dest.FalseDoctrine),
      StudyDestination(Res.string.common_distortions, Icons.Filled.Gavel, Dest.CommonDistortions),
      StudyDestination(Res.string.unseen_war, Icons.Filled.Shield, Dest.UnseenWar),
      StudyDestination(Res.string.historical_awareness, Icons.Filled.HistoryEdu, Dest.HistoricalAwareness),
      StudyDestination(Res.string.christian_symbolism, Icons.Filled.AutoAwesome, Dest.ChristianSymbolism)
    )
  ),
  StudyGroup(
    Res.string.ui_reference,
    listOf(
      StudyDestination(Res.string.bible_chronology, Icons.Filled.Timeline, Dest.BibleChronology),
      StudyDestination(Res.string.genealogy, Icons.Filled.FamilyRestroom, Dest.Genealogy),
      StudyDestination(Res.string.translation_notes, Icons.Filled.Translate, Dest.TranslationNotes),
      StudyDestination(Res.string.bible_canon, Icons.AutoMirrored.Filled.MenuBook, Dest.BibleCanon),
      StudyDestination(Res.string.bibliography, Icons.Filled.Bookmark, Dest.Bibliography),
      StudyDestination(Res.string.faqs, Icons.Filled.QuestionAnswer, Dest.FAQs)
    )
  )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StudyScreen(
  prefs: PrefsState,
  onNavigate: (Dest) -> Unit,
  onBack: (() -> Unit)? = null
) {
  val (year, month, day) = remember(prefs.appLanguage) { platformCurrentDate() }
  val nextFeast = remember(year, month, day) { CalendarUtils.nextFeast(year, month, day) }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(Res.string.ui_nav_study)) },
        navigationIcon = {
          if (onBack != null) {
            IconButton(onClick = onBack) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.back)
              )
            }
          }
        }
      )
    }
  ) { pad ->
    LazyColumn(
      modifier = Modifier.padding(pad).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
      studyGroups.forEachIndexed { groupIndex, group ->
        stickyHeader("study-group-$groupIndex") {
          Surface(color = MaterialTheme.colorScheme.surface) {
            Text(
              stringResource(group.heading),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)
            )
          }
        }
        group.entries.forEachIndexed { entryIndex, entry ->
          item("study-${entry.destination.route}-$groupIndex-$entryIndex") {
            val summary = if (entry.calendarSummary && nextFeast != null) {
              val basis = calendarTypeLabel(nextFeast.calendarType)
              "${localizedFeastDisplayName(nextFeast.id)} - $basis"
            } else null
            StudyDestinationRow(
              label = stringResource(entry.label),
              summary = summary,
              icon = entry.icon,
              onClick = { onNavigate(entry.destination) }
            )
          }
        }
      }
      item { Spacer(Modifier.height(24.dp)) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyCalendarDetailScreen(
  detail: StudyCalendarDetail,
  prefs: PrefsState,
  onBack: () -> Unit
) {
  val ctx = LocalPlatformContext.current
  var notes by remember(detail, prefs.appLanguage) { mutableStateOf<String?>(null) }
  var ordainedExpanded by rememberSaveable { mutableStateOf(true) }
  LaunchedEffect(detail, prefs.appLanguage) {
    if (detail == StudyCalendarDetail.ABOUT) {
      val lang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
      notes = readAssetText(ctx, "notes/$lang/feast_calendar_notes.md")
        ?: readAssetText(ctx, "notes/en/feast_calendar_notes.md")
    }
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            stringResource(
              if (detail == StudyCalendarDetail.ABOUT) Res.string.feast_about_heading
              else Res.string.ordained_feasts_heading
            )
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(Res.string.back)
            )
          }
        }
      )
    }
  ) { pad ->
    LazyColumn(
      modifier = Modifier.padding(pad).fillMaxSize(),
      contentPadding = PaddingValues(16.dp)
    ) {
      item {
        if (detail == StudyCalendarDetail.ABOUT) {
          notes?.let { RenderNotesMarkdown(body = it, prefs = prefs) }
        } else {
          OrdainedFeastsCard(
            expanded = ordainedExpanded,
            onToggle = { ordainedExpanded = !ordainedExpanded },
            prefs = prefs
          )
        }
      }
    }
  }
}

@Composable
private fun StudyDestinationRow(
  label: String,
  summary: String?,
  icon: ImageVector,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clickable(onClick = onClick)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      Modifier
        .size(36.dp)
        .clip(CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer
      ) {}
      Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      if (summary != null) {
        Text(
          summary,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
    Icon(
      Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@OptIn(
  ExperimentalMaterial3Api::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun ReadLibraryScreen(
  prefs: PrefsState,
  onOpenCollection: (String) -> Unit,
  onContinue: () -> Unit
) {
  val lastCollection = prefs.lastReadCollection
  val lastBook = prefs.lastReadBookId
  val readingResume = rememberReadingResume(prefs)

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(title = { Text(stringResource(Res.string.ui_nav_read)) })
    }
  ) { pad ->
    LazyColumn(
      modifier = Modifier.padding(pad).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (lastCollection != null && lastBook != null) {
        item("continue") {
          ElevatedCard(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer
            )
          ) {
            Row(
              Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
              )
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(
                  stringResource(Res.string.ui_reading_now),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Text(
                  readingResume?.title ?: lastBook,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                readingResume?.let { resume ->
                  resume.chapter?.let { chapter ->
                    Text(
                      "${stringResource(Res.string.chapters_label)} " + stringResource(
                        Res.string.ui_chapter_position, chapter, resume.chapterCount
                      ),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                  }
                }
              }
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
          }
        }
      }

      item("collections") {
        val collections = buildList {
          add("old_testament" to Res.string.old_testament)
          add("new_testament" to Res.string.new_testament)
          if (prefs.showPseudepigrapha) add("pseudepigrapha" to Res.string.pseudepigrapha)
          if (prefs.showDeutero) add("deuterocanonical" to Res.string.deuterocanonical)
          if (prefs.showApoc) add("apocrypha" to Res.string.apocrypha)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
          val columns = if (maxWidth >= 720.dp) 3 else 2
          val gap = 12.dp
          val buttonWidth = (maxWidth - gap * (columns - 1)) / columns
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap)
          ) {
            collections.forEach { (collection, label) ->
              Button(
                onClick = { onOpenCollection(collection) },
                modifier = Modifier.width(buttonWidth).heightIn(min = 56.dp),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
              ) {
                Text(
                  stringResource(label),
                  maxLines = 2
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun calendarTypeLabel(type: FeastCalendarType): String = when (type) {
  FeastCalendarType.HEBREW -> stringResource(Res.string.widget_cal_hebrew)
  FeastCalendarType.ESSENE -> stringResource(Res.string.widget_cal_essene)
  FeastCalendarType.KARAITE -> stringResource(Res.string.widget_cal_karaite)
}
