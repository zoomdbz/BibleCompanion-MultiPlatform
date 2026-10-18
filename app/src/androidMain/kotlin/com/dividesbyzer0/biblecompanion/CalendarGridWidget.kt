package com.dividesbyzer0.biblecompanion

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.unit.ColorProvider
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
  val title: String
)

private val LEGEND_CALENDARS = listOf(
  FeastCalendarType.HEBREW, FeastCalendarType.ESSENE, FeastCalendarType.KARAITE
)

class CalendarGridWidget : GlanceAppWidget() {

  override val sizeMode = SizeMode.Exact

  companion object {
    val CAL_TYPE_KEY = stringPreferencesKey("calendar_type")
    val MONTH_OFFSET_KEY = intPreferencesKey("month_offset")
  }

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val appearance = loadWidgetAppearance(context)
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
        title = widgetMonthTitle(year, month)
      )

      BibleCompanionWidgetTheme(appearance) {
        val size = LocalSize.current
        CalendarContent(size, data, calType, monthOffset, appearance)
      }
    }
  }

  private fun shortFeastName(feasts: List<FeastMarker>): String {
    val first = feasts.first()
    val localized = WidgetStrings.feastName(first.id, first.displayName)
    return localized.take(5)
  }

  private fun filterFeasts(feasts: List<FeastMarker>?, selected: FeastCalendarType?): List<FeastMarker>? {
    if (feasts == null) return null
    val filtered = if (selected == null) feasts else feasts.filter { it.calendar == selected }
    return filtered.ifEmpty { null }
  }

  /** Same tinting rule as the in-app calendar day cells. */
  private fun feastCellColor(feasts: List<FeastMarker>, appearance: WidgetAppearance): ColorProvider {
    val hasHebrew = feasts.any { it.calendar == FeastCalendarType.HEBREW }
    val hasEssene = feasts.any { it.calendar == FeastCalendarType.ESSENE }
    val hasKaraite = feasts.any { it.calendar == FeastCalendarType.KARAITE }
    val spring = feasts.any { it.isSpring }
    return when {
      (hasHebrew || hasKaraite) && hasEssene -> mixedFeastCellColor(appearance.schemes)
      hasHebrew -> seasonColor(spring, 0.25f)
      hasKaraite -> seasonColor(spring, 0.22f)
      else -> seasonColor(spring, 0.15f)
    }
  }

  /** The Hebrew legend swatch follows the displayed month: gold in spring, indigo in fall. */
  private fun hebrewLegendIsSpring(feastMap: Map<Int, List<FeastMarker>>): Boolean {
    val hebrew = feastMap.values.flatten().filter { it.calendar == FeastCalendarType.HEBREW }
    return hebrew.isEmpty() || hebrew.any { it.isSpring }
  }

  @Composable
  private fun CalendarContent(
    size: DpSize,
    data: MonthData,
    calType: String,
    monthOffset: Int,
    appearance: WidgetAppearance
  ) {
    val family = appearance.fontFamily
    val totalSlots = data.firstDow + data.daysInMonth
    val rows = (totalSlots + 6) / 7
    val selected = calendarTypeFor(calType)

    val isSmall = size.height < 160.dp
    val isLarge = size.height >= 240.dp

    val dayFs = if (isLarge) 13.sp else if (isSmall) 10.sp else 11.sp
    val headerFs = if (isLarge) 12.sp else if (isSmall) 9.sp else 10.sp
    val hebrewFs = if (isLarge) 8.sp else 7.sp
    val markerFs = if (isLarge) 8.sp else 7.sp
    val feastNameFs = if (isLarge) 7.sp else 6.sp
    val titleFs = if (isLarge) 15.sp else if (isSmall) 11.sp else 13.sp
    val arrowFs = if (isLarge) 14.sp else if (isSmall) 11.sp else 12.sp
    val toggleFs = if (isLarge) 11.sp else if (isSmall) 8.sp else 9.sp
    val legendFs = if (isLarge) 10.sp else if (isSmall) 7.sp else 8.sp

    val titleH = if (isLarge) 26.dp else if (isSmall) 20.dp else 22.dp
    val headerH = if (isLarge) 18.dp else if (isSmall) 14.dp else 16.dp
    val legendH = if (isLarge) 20.dp else if (isSmall) 14.dp else 18.dp
    val fixedH = titleH + headerH + legendH + 8.dp + 16.dp
    val gridH = size.height - fixedH
    val rowH = if (rows > 0 && gridH > 20.dp) gridH / rows else 16.dp

    // Rows with a fixed height clip their children, so each extra line of cell
    // content is gated on the room the row actually has.
    val showMarkers = rowH >= 22.dp
    val showHebrew = !isSmall && rowH >= 30.dp
    val showName = !isSmall && rowH >= 44.dp
    val hebrewSpring = hebrewLegendIsSpring(data.feastMap)

    Column(
      modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(24.dp)
        .background(GlanceTheme.colors.background)
        .padding(8.dp)
        .clickable(calendarWidgetOpenAction())
    ) {
      // Title: [filter chip] ... [<] April 2026 [>] ...
      Row(
        modifier = GlanceModifier.fillMaxWidth().height(titleH),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (!isSmall) {
          WidgetChip(
            text = filterGlyphs(calType),
            fontSize = toggleFs,
            action = actionRunCallback<ToggleCalendarTypeAction>(),
            horizontalPadding = 10.dp,
            verticalPadding = 3.dp
          )
        }
        Spacer(GlanceModifier.defaultWeight())
        WidgetChip(
          text = "◀",
          fontSize = arrowFs,
          action = actionRunCallback<PrevMonthAction>(),
          horizontalPadding = 10.dp,
          verticalPadding = 3.dp
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
          data.title,
          style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = titleFs,
            color = GlanceTheme.colors.primary,
            fontFamily = family
          ),
          maxLines = 1
        )
        Spacer(GlanceModifier.width(8.dp))
        WidgetChip(
          text = "▶",
          fontSize = arrowFs,
          action = actionRunCallback<NextMonthAction>(),
          horizontalPadding = 10.dp,
          verticalPadding = 3.dp
        )
        Spacer(GlanceModifier.defaultWeight())
      }

      Spacer(GlanceModifier.height(2.dp))

      // S M T W T F S header (localized via narrow weekday symbols, Sun → Sat)
      Row(modifier = GlanceModifier.fillMaxWidth().height(headerH)) {
        for (h in narrowWeekdaySymbols()) {
          Text(
            text = h,
            style = TextStyle(
              fontSize = headerFs,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              color = GlanceTheme.colors.onSurfaceVariant
            ),
            modifier = GlanceModifier.defaultWeight()
          )
        }
      }

      Spacer(GlanceModifier.height(2.dp))

      // Day grid rows — wrapped in a single inner Column so the outer Column
      // stays under Glance's 10-child cap. Without this nesting, 5–6 weeks
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
              val filteredFeasts = filterFeasts(feasts, selected)
              val isToday = inMonth && day == data.todayDay
              val hasFeast = filteredFeasts != null

              val bgColor: ColorProvider = when {
                !inMonth -> GlanceTheme.colors.surfaceVariant
                isToday -> GlanceTheme.colors.primaryContainer
                hasFeast -> feastCellColor(filteredFeasts, appearance)
                else -> GlanceTheme.colors.background
              }

              Box(
                modifier = GlanceModifier
                  .defaultWeight()
                  .padding(1.dp)
                  .background(bgColor)
                  .cornerRadius(6.dp),
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
                        color = if (isToday) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface,
                        textAlign = TextAlign.Center
                      )
                    )
                    if (showHebrew) {
                      val heb = HebrewCalendar.gregorianToHebrew(data.year, data.month, day)
                      Text("${heb.day}", style = TextStyle(fontSize = hebrewFs, color = GlanceTheme.colors.onSurfaceVariant))
                    }
                    if (hasFeast && showMarkers) {
                      val spring = filteredFeasts.any { it.isSpring }
                      Row {
                        for (calendar in LEGEND_CALENDARS) {
                          if (filteredFeasts.any { it.calendar == calendar }) {
                            Text(
                              calendarGlyph(calendar),
                              style = TextStyle(fontSize = markerFs, color = calendarMarkerColor(calendar, spring))
                            )
                          }
                        }
                      }
                      if (showName) {
                        Text(
                          shortFeastName(filteredFeasts),
                          style = TextStyle(fontSize = feastNameFs, color = GlanceTheme.colors.onSurfaceVariant),
                          maxLines = 1
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Legend + Today chip
      Spacer(GlanceModifier.height(2.dp))
      Row(
        modifier = GlanceModifier.fillMaxWidth().height(legendH),
        verticalAlignment = Alignment.CenterVertically
      ) {
        for (calendar in LEGEND_CALENDARS) {
          if (selected == null || selected == calendar) {
            WidgetLegendEntry(
              calendar = calendar,
              spring = calendar != FeastCalendarType.HEBREW || hebrewSpring,
              fontSize = legendFs
            )
            Spacer(GlanceModifier.width(8.dp))
          }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (monthOffset != 0) {
          WidgetChip(
            text = WidgetStrings.todayLabel(),
            fontSize = legendFs,
            action = actionRunCallback<ResetMonthAction>(),
            verticalPadding = 2.dp
          )
        }
      }
    }
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
    CalendarGridWidget().update(context, glanceId)
  }
}

class PrevMonthAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val current = prefs[CalendarGridWidget.MONTH_OFFSET_KEY] ?: 0
      prefs[CalendarGridWidget.MONTH_OFFSET_KEY] = current - 1
    }
    CalendarGridWidget().update(context, glanceId)
  }
}

class NextMonthAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val current = prefs[CalendarGridWidget.MONTH_OFFSET_KEY] ?: 0
      prefs[CalendarGridWidget.MONTH_OFFSET_KEY] = current + 1
    }
    CalendarGridWidget().update(context, glanceId)
  }
}

class ResetMonthAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      prefs[CalendarGridWidget.MONTH_OFFSET_KEY] = 0
    }
    CalendarGridWidget().update(context, glanceId)
  }
}
