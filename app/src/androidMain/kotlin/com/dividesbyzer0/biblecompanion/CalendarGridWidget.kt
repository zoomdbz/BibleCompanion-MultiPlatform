package com.dividesbyzer0.biblecompanion

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Locale-aware single-letter day-of-week labels, ordered Sunday → Saturday
 * to match the Gregorian grid layout used by [CalendarGridWidget].
 * Uses the "EEEEE" narrow weekday pattern, which yields one character for
 * most Latin and CJK locales and remains short for the rest.
 */
private fun narrowWeekdaySymbols(): List<String> {
  val formatter = SimpleDateFormat("EEEEE", Locale.getDefault())
  val cal = Calendar.getInstance()
  return (1..7).map { dow ->
    cal.set(Calendar.DAY_OF_WEEK, dow)
    formatter.format(cal.time)
  }
}

private data class MonthData(
  val year: Int,
  val month: Int,
  val todayDay: Int,
  val daysInMonth: Int,
  val firstDow: Int,
  val feastMap: Map<Int, List<FeastMarker>>,
  val monthName: String
)

class CalendarGridWidget : GlanceAppWidget() {

  override val sizeMode = SizeMode.Exact

  companion object {
    val CAL_TYPE_KEY = stringPreferencesKey("calendar_type")
    val MONTH_OFFSET_KEY = intPreferencesKey("month_offset")
  }

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      val prefs = currentState<Preferences>()
      val calType = prefs[CAL_TYPE_KEY] ?: "B"
      val monthOffset = prefs[MONTH_OFFSET_KEY] ?: 0

      val cal = Calendar.getInstance()
      var year = cal.get(Calendar.YEAR)
      var month = cal.get(Calendar.MONTH) + 1
      val todayDay = cal.get(Calendar.DAY_OF_MONTH)
      val todayYear = year
      val todayMonth = month

      var off = monthOffset
      while (off > 0) { month++; if (month > 12) { month = 1; year++ }; off-- }
      while (off < 0) { month--; if (month < 1) { month = 12; year-- }; off++ }

      val isCurrentMonth = year == todayYear && month == todayMonth
      val data = MonthData(
        year = year, month = month,
        todayDay = if (isCurrentMonth) todayDay else 0,
        daysInMonth = CalendarUtils.daysInGregorianMonth(year, month),
        firstDow = CalendarUtils.firstDayOfWeekInMonth(year, month),
        feastMap = CalendarUtils.buildFeastMap(year, month),
        monthName = CalendarUtils.localizedMonthName(month, java.util.Locale.getDefault().language)
      )

      GlanceTheme {
        val size = LocalSize.current
        CalendarContent(size, data, calType, monthOffset)
      }
    }
  }

  private fun shortFeastName(feasts: List<FeastMarker>): String {
    val first = feasts.first()
    val localized = WidgetStrings.feastName(first.id, first.displayName)
    return localized.take(5)
  }

  private fun filterFeasts(feasts: List<FeastMarker>?, calType: String): List<FeastMarker>? {
    if (feasts == null) return null
    val filtered = when (calType) {
      "H" -> feasts.filter { it.calendar == FeastCalendarType.HEBREW }
      "E" -> feasts.filter { it.calendar == FeastCalendarType.ESSENE }
      "K" -> feasts.filter { it.calendar == FeastCalendarType.KARAITE }
      else -> feasts
    }
    return filtered.ifEmpty { null }
  }

  @Composable
  private fun CalendarContent(size: DpSize, data: MonthData, calType: String, monthOffset: Int) {
    val totalSlots = data.firstDow + data.daysInMonth
    val rows = (totalSlots + 6) / 7

    val isSmall = size.height < 160.dp
    val isLarge = size.height >= 240.dp

    val dayFs = if (isLarge) 13.sp else if (isSmall) 10.sp else 11.sp
    val headerFs = if (isLarge) 12.sp else if (isSmall) 9.sp else 10.sp
    val hebrewFs = if (isLarge) 8.sp else 7.sp
    val markerFs = if (isLarge) 8.sp else 7.sp
    val feastNameFs = if (isLarge) 7.sp else 6.sp
    val titleFs = if (isLarge) 15.sp else if (isSmall) 11.sp else 13.sp
    val arrowFs = if (isLarge) 18.sp else if (isSmall) 14.sp else 16.sp
    val toggleFs = if (isLarge) 11.sp else if (isSmall) 8.sp else 9.sp
    val legendFs = if (isLarge) 10.sp else if (isSmall) 7.sp else 8.sp
    val showHebrew = !isSmall
    val showName = !isSmall

    val titleH = if (isLarge) 24.dp else if (isSmall) 18.dp else 20.dp
    val headerH = if (isLarge) 18.dp else if (isSmall) 14.dp else 16.dp
    val legendH = if (!isSmall) 16.dp else 10.dp
    val fixedH = titleH + headerH + legendH + 8.dp + 16.dp
    val gridH = size.height - fixedH
    val rowH = if (rows > 0 && gridH > 20.dp) gridH / rows else 16.dp

    Column(
      modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(16.dp)
        .background(GlanceTheme.colors.widgetBackground)
        .padding(8.dp)
        .clickable(actionRunCallback<OpenCalendarAction>())
    ) {
      // Title: [H/E/B] ... < April 2026 > ...
      Row(
        modifier = GlanceModifier.fillMaxWidth().height(titleH),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (!isSmall) {
          Box(
            modifier = GlanceModifier
              .height(titleH)
              .clickable(actionRunCallback<ToggleCalendarTypeAction>())
              .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              when (calType) {
                "H" -> "\u2721"
                "E" -> "\u2609"
                "K" -> "\u263E"
                else -> "\u2721\u2609\u263E"
              },
              style = TextStyle(fontSize = toggleFs, color = GlanceTheme.colors.primary)
            )
          }
        }
        Spacer(GlanceModifier.defaultWeight())
        Box(
          modifier = GlanceModifier.height(titleH).padding(horizontal = 6.dp)
            .clickable(actionRunCallback<PrevMonthAction>()),
          contentAlignment = Alignment.Center
        ) {
          Text("\u25C0", style = TextStyle(fontSize = arrowFs, color = GlanceTheme.colors.primary))
        }
        Text(
          "${data.monthName} ${data.year}",
          style = TextStyle(fontWeight = FontWeight.Bold, fontSize = titleFs, color = GlanceTheme.colors.onSurface)
        )
        Box(
          modifier = GlanceModifier.height(titleH).padding(horizontal = 6.dp)
            .clickable(actionRunCallback<NextMonthAction>()),
          contentAlignment = Alignment.Center
        ) {
          Text("\u25B6", style = TextStyle(fontSize = arrowFs, color = GlanceTheme.colors.primary))
        }
        Spacer(GlanceModifier.defaultWeight())
      }

      Spacer(GlanceModifier.height(2.dp))

      // S M T W T F S header (localized via narrow weekday symbols, Sun → Sat)
      Row(modifier = GlanceModifier.fillMaxWidth().height(headerH)) {
        for (h in narrowWeekdaySymbols()) {
          Text(
            text = h,
            style = TextStyle(fontSize = headerFs, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.defaultWeight()
          )
        }
      }

      Spacer(GlanceModifier.height(2.dp))

      // Day grid rows \u2014 wrapped in a single inner Column so the outer Column
      // stays under Glance's 10-child cap. Without this nesting, 5\u20136 weeks
      // plus title/header/legend pushed children to 11+ and Glance silently
      // truncated, blanking the widget.
      Column(modifier = GlanceModifier.fillMaxWidth()) {
        for (row in 0 until rows) {
          Row(modifier = GlanceModifier.fillMaxWidth().height(rowH)) {
            for (col in 0..6) {
              val slotIndex = row * 7 + col
              val day = slotIndex - data.firstDow + 1
              val inMonth = day in 1..data.daysInMonth
              val feasts = if (inMonth) data.feastMap[day] else null
              val filteredFeasts = filterFeasts(feasts, calType)
              val isToday = inMonth && day == data.todayDay
              val hasFeast = filteredFeasts != null

              val bgColor = when {
                !inMonth -> GlanceTheme.colors.surfaceVariant
                isToday -> GlanceTheme.colors.primaryContainer
                hasFeast -> GlanceTheme.colors.tertiaryContainer
                else -> GlanceTheme.colors.widgetBackground
              }

              Box(
                modifier = GlanceModifier
                  .defaultWeight()
                  .padding(1.dp)
                  .background(bgColor)
                  .cornerRadius(4.dp),
                contentAlignment = Alignment.TopCenter
              ) {
                if (inMonth) {
                  Column(
                    modifier = GlanceModifier.padding(1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      "$day",
                      style = TextStyle(
                        fontSize = dayFs,
                        fontWeight = if (isToday || hasFeast) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
                        textAlign = TextAlign.Center
                      )
                    )
                    if (showHebrew) {
                      val heb = HebrewCalendar.gregorianToHebrew(data.year, data.month, day)
                      Text("${heb.day}", style = TextStyle(fontSize = hebrewFs, color = GlanceTheme.colors.onSurfaceVariant))
                    }
                    if (hasFeast) {
                      val hasH = filteredFeasts!!.any { it.calendar == FeastCalendarType.HEBREW }
                      val hasE = filteredFeasts.any { it.calendar == FeastCalendarType.ESSENE }
                      val hasK = filteredFeasts.any { it.calendar == FeastCalendarType.KARAITE }
                      Row {
                        if (hasH) Text("\u2721", style = TextStyle(fontSize = markerFs, color = GlanceTheme.colors.error))
                        if (hasE) Text("\u2609", style = TextStyle(fontSize = markerFs, color = GlanceTheme.colors.primary))
                      }
                      if (showName) {
                        Text(shortFeastName(filteredFeasts), style = TextStyle(fontSize = feastNameFs, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Legend + Today button
      Spacer(GlanceModifier.height(2.dp))
      Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (calType != "E") Text("\u2721 " + WidgetStrings.calHebrew(), style = TextStyle(fontSize = legendFs, color = GlanceTheme.colors.error))
        if (calType == "B") Spacer(GlanceModifier.width(8.dp))
        if (calType != "H") Text("\u2609 " + WidgetStrings.calEssene(), style = TextStyle(fontSize = legendFs, color = GlanceTheme.colors.primary))
        Spacer(GlanceModifier.defaultWeight())
        if (monthOffset != 0) {
          Box(
            modifier = GlanceModifier.clickable(actionRunCallback<ResetMonthAction>()).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(WidgetStrings.todayLabel(), style = TextStyle(fontSize = legendFs, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary))
          }
        }
      }
    }
  }
}

class OpenCalendarAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val intent = Intent("com.dividesbyzer0.biblecompanion.FEAST_CALENDAR").apply {
      setClassName("com.dividesbyzer0.biblecompanion", "com.dividesbyzer0.biblecompanion.MainActivity")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    context.startActivity(intent)
  }
}

class ToggleCalendarTypeAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val current = prefs[CalendarGridWidget.CAL_TYPE_KEY] ?: "B"
      prefs[CalendarGridWidget.CAL_TYPE_KEY] = when (current) {
        "B" -> "H"
        "H" -> "E"
        "E" -> "K"
        else -> "B"
      }
    }
    CalendarGridWidget().updateAll(context)
  }
}

class PrevMonthAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val current = prefs[CalendarGridWidget.MONTH_OFFSET_KEY] ?: 0
      prefs[CalendarGridWidget.MONTH_OFFSET_KEY] = current - 1
    }
    CalendarGridWidget().updateAll(context)
  }
}

class NextMonthAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val current = prefs[CalendarGridWidget.MONTH_OFFSET_KEY] ?: 0
      prefs[CalendarGridWidget.MONTH_OFFSET_KEY] = current + 1
    }
    CalendarGridWidget().updateAll(context)
  }
}

class ResetMonthAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      prefs[CalendarGridWidget.MONTH_OFFSET_KEY] = 0
    }
    CalendarGridWidget().updateAll(context)
  }
}
