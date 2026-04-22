package com.dividesbyzer0.biblecompanion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.platformCurrentDate
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeastCalendarScreen(prefs: PrefsState, repo: PrefsRepo, onBack: () -> Unit) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()
  val (todayY, todayM, todayD) = remember { platformCurrentDate() }

  var displayYear by remember { mutableStateOf(todayY) }
  var displayMonth by remember { mutableStateOf(todayM) }

  var notesExpanded by remember { mutableStateOf(prefs.feastNotesExpanded) }
  var ordainedExpanded by remember { mutableStateOf(prefs.ordainedFeastsExpanded) }
  var notes by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(prefs.appLanguage) {
    val lang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
    notes = readAssetText(ctx, "notes/$lang/feast_calendar_notes.md")
      ?: readAssetText(ctx, "notes/en/feast_calendar_notes.md")
  }

  val daysInMonth = remember(displayYear, displayMonth) {
    CalendarUtils.daysInGregorianMonth(displayYear, displayMonth)
  }
  val firstDow = remember(displayYear, displayMonth) {
    CalendarUtils.firstDayOfWeekInMonth(displayYear, displayMonth)
  }
  val feastMap = remember(displayYear, displayMonth) {
    CalendarUtils.buildFeastMap(displayYear, displayMonth)
  }
  val hebrewFirst = remember(displayYear, displayMonth) {
    HebrewCalendar.gregorianToHebrew(displayYear, displayMonth, 1)
  }
  val hebrewLast = remember(displayYear, displayMonth) {
    HebrewCalendar.gregorianToHebrew(displayYear, displayMonth, daysInMonth)
  }

  val lang = remember(prefs.appLanguage) { LocaleUtils.effectiveAssetTag(prefs.appLanguage) }

  val monthTitle = "${CalendarUtils.localizedMonthName(displayMonth, lang)} $displayYear"
  val hebrewSubtitle = run {
    val m1 = hebrewFirst.monthName
    val m2 = hebrewLast.monthName
    val y = hebrewFirst.year
    if (m1 == m2) "$m1 $y" else "$m1 \u2013 $m2 $y"
  }

  fun advanceMonth(delta: Int) {
    var m = displayMonth + delta
    var y = displayYear
    if (m > 12) { m = 1; y++ }
    if (m < 1) { m = 12; y-- }
    displayMonth = m; displayYear = y
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(Res.string.feast_calendar)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
          }
        }
      )
    }
  ) { pad ->
    LazyColumn(
      modifier = Modifier.padding(pad),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Explanation notes
      notes?.let { text ->
        if (text.isNotBlank()) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              )
            ) {
              Column(Modifier.padding(12.dp)) {
                Row(
                  Modifier.fillMaxWidth().clickable {
                    notesExpanded = !notesExpanded
                    scope.launch { repo.setFeastNotesExpanded(notesExpanded) }
                  },
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    stringResource(Res.string.feast_about_heading),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                  )
                  Icon(
                    if (notesExpanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                  )
                }
                AnimatedVisibility(visible = notesExpanded) {
                  Column(Modifier.padding(top = 8.dp)) {
                    RenderNotesMarkdown(body = text, prefs = prefs)
                  }
                }
              }
            }
          }
        }
      }

      // Ordained Feasts section
      item {
        OrdainedFeastsCard(
          expanded = ordainedExpanded,
          onToggle = {
            ordainedExpanded = !ordainedExpanded
            scope.launch { repo.setOrdainedFeastsExpanded(ordainedExpanded) }
          },
          prefs = prefs
        )
      }

      // Month navigation
      item {
        Column(Modifier.fillMaxWidth()) {
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = { advanceMonth(-1) }) {
              Icon(Icons.Filled.ChevronLeft, contentDescription = null)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(monthTitle, style = MaterialTheme.typography.titleMedium)
              Text(
                hebrewSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            IconButton(onClick = { advanceMonth(1) }) {
              Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
          }

          if (displayYear != todayY || displayMonth != todayM) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
              TextButton(onClick = { displayYear = todayY; displayMonth = todayM }) {
                Text(stringResource(Res.string.feast_today))
              }
            }
          }
        }
      }

      // Calendar grid
      item {
        CalendarGrid(
          year = displayYear,
          month = displayMonth,
          daysInMonth = daysInMonth,
          firstDow = firstDow,
          todayDay = if (displayYear == todayY && displayMonth == todayM) todayD else -1,
          feastMap = feastMap,
          lang = lang
        )
      }

      // Feasts this month
      item {
        MonthFeastList(displayYear, displayMonth, feastMap, prefs, lang)
      }

      // Typology section
      item {
        FeastTypologyCard(prefs)
      }

      item { Spacer(Modifier.height(32.dp)) }
    }
  }
}

@Composable
private fun CalendarGrid(
  year: Int,
  month: Int,
  daysInMonth: Int,
  firstDow: Int,
  todayDay: Int,
  feastMap: Map<Int, List<FeastMarker>>,
  lang: String
) {
  val dayHeaders = remember(lang) { CalendarUtils.localizedDayHeaders(lang) }

  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth()) {
      dayHeaders.forEach { d ->
        Text(
          d,
          modifier = Modifier.weight(1f),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    Spacer(Modifier.height(4.dp))

    val totalSlots = firstDow + daysInMonth
    val rows = (totalSlots + 6) / 7

    for (row in 0 until rows) {
      Row(Modifier.fillMaxWidth()) {
        for (col in 0..6) {
          val slotIndex = row * 7 + col
          val day = slotIndex - firstDow + 1
          Box(
            modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp),
            contentAlignment = Alignment.Center
          ) {
            if (day in 1..daysInMonth) {
              val feasts = feastMap[day]
              val isToday = day == todayDay
              val hebrew = HebrewCalendar.gregorianToHebrew(year, month, day)

              DayCell(
                day = day,
                hebrewDay = hebrew.day,
                hebrewMonth = hebrew.monthName,
                isToday = isToday,
                feasts = feasts
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DayCell(
  day: Int,
  hebrewDay: Int,
  hebrewMonth: String,
  isToday: Boolean,
  feasts: List<FeastMarker>?
) {
  val hasHebrew = feasts?.any { it.calendar == FeastCalendarType.HEBREW } == true
  val hasEssene = feasts?.any { it.calendar == FeastCalendarType.ESSENE } == true
  val springFeast = feasts?.any { it.isSpring } == true

  val bgColor = when {
    hasHebrew && hasEssene -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    hasHebrew -> feastSpringColor(springFeast).copy(alpha = 0.25f)
    hasEssene -> feastSpringColor(springFeast).copy(alpha = 0.15f)
    else -> Color.Transparent
  }

  val borderMod = if (isToday) {
    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
  } else Modifier

  Box(
    modifier = Modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(6.dp))
      .background(bgColor)
      .then(borderMod)
      .padding(2.dp),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        "$day",
        style = MaterialTheme.typography.bodySmall.copy(
          fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        ),
        color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
      )
      Text(
        "$hebrewDay",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
      )
      if (feasts != null) {
        Row(horizontalArrangement = Arrangement.Center) {
          if (hasHebrew) {
            Box(
              Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(feastSpringColor(springFeast))
            )
          }
          if (hasHebrew && hasEssene) Spacer(Modifier.width(3.dp))
          if (hasEssene) {
            Box(
              Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun feastSpringColor(spring: Boolean): Color =
  if (spring) Color(0xFFD4A017) else Color(0xFF5B6BBF)

@Composable
private fun MonthFeastList(
  year: Int,
  month: Int,
  feastMap: Map<Int, List<FeastMarker>>,
  prefs: PrefsState,
  lang: String
) {
  val entries = feastMap.entries.sortedBy { it.key }
  if (entries.isEmpty()) return

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )
  ) {
    Column(Modifier.padding(12.dp)) {
      Text(
        stringResource(Res.string.feast_this_month),
        style = MaterialTheme.typography.titleSmall
      )
      Spacer(Modifier.height(8.dp))

      val shown = mutableSetOf<String>()
      for ((day, markers) in entries) {
        for (m in markers) {
          val key = "${m.id}_${m.calendar.name}"
          if (key in shown) continue
          shown.add(key)

          val rangeEnd = entries
            .filter { (_, ms) -> ms.any { it.id == m.id && it.calendar == m.calendar } }
            .maxOf { it.key }
          val dateStr = if (day == rangeEnd) "$day" else "$day\u2013$rangeEnd"

          val calLabel = if (m.calendar == FeastCalendarType.HEBREW) "H" else "E"
          val hebrew = HebrewCalendar.gregorianToHebrew(year, month, day)

          Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                  if (m.calendar == FeastCalendarType.HEBREW) feastSpringColor(m.isSpring)
                  else MaterialTheme.colorScheme.tertiary
                )
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
              val feastLabel = localizedFeastDisplayName(m.id)
              Text(
                "$feastLabel [$calLabel]",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
              )
              val localizedHebMonth = localizedHebrewMonthName(hebrew.monthName)
              Text(
                "${CalendarUtils.localizedMonthName(month, lang)} $dateStr \u2022 $localizedHebMonth ${hebrew.day}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FeastTypologyCard(prefs: PrefsState) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    )
  ) {
    Column(Modifier.padding(12.dp)) {
      Text(
        stringResource(Res.string.feast_typology_title),
        style = MaterialTheme.typography.titleSmall
      )
      Spacer(Modifier.height(8.dp))

      Text(
        stringResource(Res.string.feast_spring_heading),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFFD4A017)
      )
      TypologyRow(stringResource(Res.string.feast_typology_passover_name), stringResource(Res.string.feast_typology_passover_meaning), "1 Corinthians 5:7", prefs)
      TypologyRow(stringResource(Res.string.feast_typology_unleavened_name), stringResource(Res.string.feast_typology_unleavened_meaning), "1 Peter 2:22", prefs)
      TypologyRow(stringResource(Res.string.feast_typology_firstfruits_name), stringResource(Res.string.feast_typology_firstfruits_meaning), "1 Corinthians 15:20", prefs)
      TypologyRow(stringResource(Res.string.feast_typology_weeks_name), stringResource(Res.string.feast_typology_weeks_meaning), "Acts 2:1\u20134", prefs)

      Spacer(Modifier.height(8.dp))
      Text(
        stringResource(Res.string.feast_fall_heading),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF5B6BBF)
      )
      TypologyRow(stringResource(Res.string.feast_typology_trumpets_name), stringResource(Res.string.feast_typology_trumpets_meaning), "1 Thessalonians 4:16", prefs)
      TypologyRow(stringResource(Res.string.feast_typology_atonement_name), stringResource(Res.string.feast_typology_atonement_meaning), "Romans 11:26\u201327", prefs)
      TypologyRow(stringResource(Res.string.feast_typology_tabernacles_name), stringResource(Res.string.feast_typology_tabernacles_meaning), "Revelation 21:3", prefs)

      Spacer(Modifier.height(8.dp))
      HorizontalDivider()
      Spacer(Modifier.height(6.dp))
      Text(
        stringResource(Res.string.feast_idiom_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun OrdainedFeastsCard(expanded: Boolean, onToggle: () -> Unit, prefs: PrefsState) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
  ) {
    Column(Modifier.padding(12.dp)) {
      Row(
        Modifier.fillMaxWidth().clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          stringResource(Res.string.ordained_feasts_heading),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f)
        )
        Icon(
          if (expanded) Icons.Filled.KeyboardArrowUp
          else Icons.Filled.KeyboardArrowDown,
          contentDescription = null
        )
      }
      AnimatedVisibility(visible = expanded) {
        Column(Modifier.padding(top = 8.dp)) {
          Text(
            stringResource(Res.string.ordained_commanded_heading),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD4A017)
          )
          Spacer(Modifier.height(4.dp))
          OrdainedFeastRow(stringResource(Res.string.feast_passover), stringResource(Res.string.feast_passover_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_unleavened), stringResource(Res.string.feast_unleavened_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_firstfruits), stringResource(Res.string.feast_firstfruits_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_second_passover), stringResource(Res.string.feast_second_passover_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_weeks), stringResource(Res.string.feast_weeks_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_trumpets), stringResource(Res.string.feast_trumpets_ref), prefs)
          OrdainedFeastRow(
            stringResource(Res.string.feast_atonement) + " " + stringResource(Res.string.feast_atonement_note),
            stringResource(Res.string.feast_atonement_ref),
            prefs
          )
          OrdainedFeastRow(stringResource(Res.string.feast_tabernacles), stringResource(Res.string.feast_tabernacles_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_shemini_atzeret), stringResource(Res.string.feast_shemini_atzeret_ref), prefs)

          Spacer(Modifier.height(12.dp))
          HorizontalDivider()
          Spacer(Modifier.height(8.dp))

          Text(
            stringResource(Res.string.ordained_tradition_heading),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(Modifier.height(4.dp))
          OrdainedFeastRow(stringResource(Res.string.feast_purim), stringResource(Res.string.feast_purim_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_hanukkah), stringResource(Res.string.feast_hanukkah_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_fast_tammuz), stringResource(Res.string.feast_fast_tammuz_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_tisha_bav), stringResource(Res.string.feast_tisha_bav_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_fast_gedaliah), stringResource(Res.string.feast_fast_gedaliah_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_fast_tevet), stringResource(Res.string.feast_fast_tevet_ref), prefs)
          OrdainedFeastRow(stringResource(Res.string.feast_fast_esther), stringResource(Res.string.feast_fast_esther_ref), prefs)
        }
      }
    }
  }
}

@Composable
private fun OrdainedFeastRow(name: String, ref: String, prefs: PrefsState) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      name,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.weight(1f)
    )
    ScriptureRefs.ClickableRefsText(
      text = ref,
      collection = "old_testament",
      prefs = prefs,
      textStyle = MaterialTheme.typography.labelSmall
    )
  }
}

@Composable
private fun TypologyRow(feast: String, meaning: String, ref: String, prefs: PrefsState) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Text(
      feast,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.weight(1f)
    )
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
      Text(meaning, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
      ScriptureRefs.ClickableRefsText(
        text = ref,
        collection = "new_testament",
        prefs = prefs,
        textStyle = MaterialTheme.typography.labelSmall
      )
    }
  }
}

@Composable
internal fun localizedFeastDisplayName(id: String): String = when (id) {
  "passover" -> stringResource(Res.string.feast_passover)
  "unleavened" -> stringResource(Res.string.feast_unleavened)
  "firstfruits" -> stringResource(Res.string.feast_firstfruits)
  "second_passover" -> stringResource(Res.string.feast_second_passover)
  "pentecost" -> stringResource(Res.string.feast_weeks)
  "trumpets" -> stringResource(Res.string.feast_trumpets)
  "atonement" -> stringResource(Res.string.feast_atonement)
  "tabernacles" -> stringResource(Res.string.feast_tabernacles)
  "assembly" -> stringResource(Res.string.feast_shemini_atzeret)
  "fast_tevet" -> stringResource(Res.string.feast_fast_tevet)
  "fast_esther" -> stringResource(Res.string.feast_fast_esther)
  "purim" -> stringResource(Res.string.feast_purim)
  "fast_tammuz" -> stringResource(Res.string.feast_fast_tammuz)
  "tisha_bav" -> stringResource(Res.string.feast_tisha_bav)
  "fast_gedaliah" -> stringResource(Res.string.feast_fast_gedaliah)
  "hanukkah" -> stringResource(Res.string.feast_hanukkah)
  else -> id
}

@Composable
internal fun localizedHebrewMonthName(englishName: String): String = when (englishName) {
  "Nisan" -> stringResource(Res.string.hebrew_month_nisan)
  "Iyar" -> stringResource(Res.string.hebrew_month_iyar)
  "Sivan" -> stringResource(Res.string.hebrew_month_sivan)
  "Tammuz" -> stringResource(Res.string.hebrew_month_tammuz)
  "Av" -> stringResource(Res.string.hebrew_month_av)
  "Elul" -> stringResource(Res.string.hebrew_month_elul)
  "Tishrei" -> stringResource(Res.string.hebrew_month_tishrei)
  "Cheshvan" -> stringResource(Res.string.hebrew_month_cheshvan)
  "Kislev" -> stringResource(Res.string.hebrew_month_kislev)
  "Tevet" -> stringResource(Res.string.hebrew_month_tevet)
  "Shevat" -> stringResource(Res.string.hebrew_month_shevat)
  "Adar" -> stringResource(Res.string.hebrew_month_adar)
  "Adar I" -> stringResource(Res.string.hebrew_month_adar_i)
  "Adar II" -> stringResource(Res.string.hebrew_month_adar_ii)
  else -> englishName
}
