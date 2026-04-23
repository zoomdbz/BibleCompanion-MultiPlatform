package com.dividesbyzer0.biblecompanion

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
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
import androidx.glance.text.TextStyle
import java.util.Calendar

class FeastWidget : GlanceAppWidget() {

  override val sizeMode = SizeMode.Responsive(
    setOf(COMPACT, SMALL, MEDIUM, LARGE)
  )

  companion object {
    private val COMPACT = DpSize(80.dp, 40.dp)
    private val SMALL = DpSize(110.dp, 50.dp)
    private val MEDIUM = DpSize(180.dp, 80.dp)
    private val LARGE = DpSize(220.dp, 150.dp)
    val CAL_TYPE_KEY = stringPreferencesKey("feast_calendar_type")
  }

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)

    val feasts = CalendarUtils.upcomingFeasts(year, month, day, 10)

    provideContent {
      val prefs = currentState<Preferences>()
      val calType = prefs[CAL_TYPE_KEY] ?: "B"
      GlanceTheme {
        WidgetContent(feasts, calType)
      }
    }
  }

  /** Determine a size tier from actual widget dimensions. */
  private enum class SizeTier { COMPACT, SMALL, MEDIUM, LARGE }

  private fun sizeTier(size: DpSize): SizeTier = when {
    size.width >= 220.dp && size.height >= 150.dp -> SizeTier.LARGE
    size.width >= 180.dp && size.height >= 80.dp -> SizeTier.MEDIUM
    size.width >= 110.dp && size.height >= 50.dp -> SizeTier.SMALL
    else -> SizeTier.COMPACT
  }

  @Composable
  private fun WidgetContent(
    feasts: List<CalendarUtils.UpcomingFeast>,
    calType: String
  ) {
    val size = LocalSize.current
    val tier = sizeTier(size)

    // Filter feasts by calendar type
    val filteredFeasts = when (calType) {
      "H" -> feasts.filter { it.calendarType == FeastCalendarType.HEBREW }
      "E" -> feasts.filter { it.calendarType == FeastCalendarType.ESSENE }
      "K" -> feasts.filter { it.calendarType == FeastCalendarType.KARAITE }
      else -> feasts
    }

    // Compact: single row, just next feast + countdown
    if (tier == SizeTier.COMPACT) {
      CompactContent(filteredFeasts.firstOrNull(), calType)
      return
    }

    val showItems = filteredFeasts

    // Responsive font sizes
    val headerFontSize = when (tier) {
      SizeTier.COMPACT -> 9.sp
      SizeTier.SMALL -> 10.sp
      SizeTier.MEDIUM -> 11.sp
      SizeTier.LARGE -> 13.sp
    }
    val nameFontSizeFirst = when (tier) {
      SizeTier.COMPACT -> 10.sp
      SizeTier.SMALL -> 12.sp
      SizeTier.MEDIUM -> 14.sp
      SizeTier.LARGE -> 15.sp
    }
    val nameFontSize = when (tier) {
      SizeTier.COMPACT -> 9.sp
      SizeTier.SMALL -> 11.sp
      SizeTier.MEDIUM -> 12.sp
      SizeTier.LARGE -> 13.sp
    }
    val dateFontSize = when (tier) {
      SizeTier.COMPACT -> 9.sp
      SizeTier.SMALL -> 10.sp
      SizeTier.MEDIUM -> 11.sp
      SizeTier.LARGE -> 12.sp
    }
    val countdownFontSize = when (tier) {
      SizeTier.COMPACT -> 8.sp
      SizeTier.SMALL -> 9.sp
      SizeTier.MEDIUM -> 10.sp
      SizeTier.LARGE -> 11.sp
    }
    val toggleFontSize = when (tier) {
      SizeTier.COMPACT -> 8.sp
      SizeTier.SMALL -> 9.sp
      SizeTier.MEDIUM -> 10.sp
      SizeTier.LARGE -> 11.sp
    }
    val itemSpacing = when (tier) {
      SizeTier.COMPACT -> 2.dp
      SizeTier.SMALL -> 3.dp
      SizeTier.MEDIUM -> 5.dp
      SizeTier.LARGE -> 6.dp
    }

    Column(
      modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(16.dp)
        .background(GlanceTheme.colors.widgetBackground)
        .padding(12.dp)
        .clickable(actionRunCallback<OpenFeastCalendarAction>()),
      verticalAlignment = if (showItems.size <= 1) Alignment.CenterVertically else Alignment.Top
    ) {
      // Header row with title and calendar type toggle
      Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "\u2721 " + WidgetStrings.feastsHeader(),
          style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = headerFontSize,
            color = GlanceTheme.colors.onSurfaceVariant
          ),
          maxLines = 1,
          modifier = GlanceModifier.defaultWeight()
        )
        Box(
          modifier = GlanceModifier
            .clickable(actionRunCallback<ToggleFeastCalTypeAction>())
            .padding(horizontal = 14.dp, vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = when (calType) {
              "H" -> "\u2721 " + WidgetStrings.calHebrew()
              "E" -> "\u2609 " + WidgetStrings.calEssene()
              "K" -> "\u263E " + WidgetStrings.calKaraite()
              else -> "\u2721\u2609\u263E " + WidgetStrings.calAll()
            },
            style = TextStyle(
              fontSize = toggleFontSize,
              color = GlanceTheme.colors.primary
            )
          )
        }
      }
      Spacer(GlanceModifier.height(itemSpacing))

      if (showItems.isEmpty()) {
        Text(
          text = WidgetStrings.noFeasts(),
          style = TextStyle(
            fontSize = nameFontSize,
            color = GlanceTheme.colors.onSurfaceVariant
          )
        )
      } else {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
          itemsIndexed(showItems) { idx, feast ->
            Column {
              if (idx > 0) {
                Spacer(GlanceModifier.height(itemSpacing))
                Box(
                  modifier = GlanceModifier.fillMaxWidth().height(1.dp)
                    .background(GlanceTheme.colors.onSurfaceVariant)
                ) {}
                Spacer(GlanceModifier.height(itemSpacing))
              }
              FeastRow(
                feast = feast,
                isFirst = idx == 0,
                calType = calType,
                nameFontSizeFirst = nameFontSizeFirst,
                nameFontSize = nameFontSize,
                dateFontSize = dateFontSize,
                countdownFontSize = countdownFontSize
              )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun FeastRow(
    feast: CalendarUtils.UpcomingFeast,
    isFirst: Boolean,
    calType: String,
    nameFontSizeFirst: androidx.compose.ui.unit.TextUnit,
    nameFontSize: androidx.compose.ui.unit.TextUnit,
    dateFontSize: androidx.compose.ui.unit.TextUnit,
    countdownFontSize: androidx.compose.ui.unit.TextUnit
  ) {
    // Add [H] or [E] marker when showing both calendars
    val marker = if (calType == "B") {
      when (feast.calendarType) {
        FeastCalendarType.HEBREW -> " \u2721"
        FeastCalendarType.ESSENE -> " \u2609"
        FeastCalendarType.KARAITE -> " \u263E"
      }
    } else ""

    val baseName = WidgetStrings.feastName(feast.id, feast.name)
    val dayLabel = WidgetStrings.dayOfFeast(feast.dayOfFeast)
    val displayLabel = if (feast.totalDays > 1) "$baseName ($dayLabel)" else baseName

    Row(
      modifier = GlanceModifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
          text = "$displayLabel$marker",
          style = TextStyle(
            fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isFirst) nameFontSizeFirst else nameFontSize,
            color = if (isFirst) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface
          ),
          maxLines = 1
        )
      }
      Spacer(GlanceModifier.width(8.dp))
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = formatDate(feast.gregorianMonth, feast.gregorianDay),
          style = TextStyle(
            fontSize = dateFontSize,
            color = GlanceTheme.colors.onSurfaceVariant
          ),
          maxLines = 1
        )
        Text(
          text = when (feast.daysUntil) {
            0 -> WidgetStrings.today()
            1 -> WidgetStrings.tomorrow()
            else -> WidgetStrings.daysShort(feast.daysUntil)
          },
          style = TextStyle(
            fontSize = countdownFontSize,
            fontWeight = if (feast.daysUntil <= 1) FontWeight.Bold else FontWeight.Normal,
            color = if (feast.daysUntil <= 1) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
          ),
          maxLines = 1
        )
      }
    }
  }

  @Composable
  private fun CompactContent(feast: CalendarUtils.UpcomingFeast?, calType: String) {
    Row(
      modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(12.dp)
        .background(GlanceTheme.colors.widgetBackground)
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .clickable(actionRunCallback<OpenFeastCalendarAction>()),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (feast != null) {
        val cbaseName = WidgetStrings.feastName(feast.id, feast.name)
        val cdayLabel = WidgetStrings.dayOfFeast(feast.dayOfFeast)
        val cdisplayLabel = if (feast.totalDays > 1) "$cbaseName ($cdayLabel)" else cbaseName
        Text(
          text = cdisplayLabel,
          style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = GlanceTheme.colors.primary
          ),
          maxLines = 1,
          modifier = GlanceModifier.defaultWeight()
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
          text = when (feast.daysUntil) {
            0 -> WidgetStrings.today()
            1 -> WidgetStrings.tomorrow()
            else -> "${formatDate(feast.gregorianMonth, feast.gregorianDay)} \u2022 " + WidgetStrings.daysShort(feast.daysUntil)
          },
          style = TextStyle(
            fontSize = 10.sp,
            fontWeight = if (feast.daysUntil <= 1) FontWeight.Bold else FontWeight.Normal,
            color = if (feast.daysUntil <= 1) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
          ),
          maxLines = 1
        )
      } else {
        Text(
          text = WidgetStrings.noFeasts(),
          style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
        )
      }
    }
  }

  private fun formatDate(month: Int, day: Int): String {
    val symbols = java.text.DateFormatSymbols(java.util.Locale.getDefault()).shortMonths
    val monthName = if (month in 1..symbols.size) symbols[month - 1] else ""
    return "$monthName $day"
  }
}

class OpenFeastCalendarAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val intent = Intent("com.dividesbyzer0.biblecompanion.FEAST_CALENDAR").apply {
      setClassName("com.dividesbyzer0.biblecompanion", "com.dividesbyzer0.biblecompanion.MainActivity")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    context.startActivity(intent)
  }
}

class ToggleFeastCalTypeAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    updateAppWidgetState(context, glanceId) { prefs ->
      val current = prefs[FeastWidget.CAL_TYPE_KEY] ?: "B"
      prefs[FeastWidget.CAL_TYPE_KEY] = when (current) {
        "B" -> "H"
        "H" -> "E"
        "E" -> "K"
        else -> "B"
      }
    }
    FeastWidget().updateAll(context)
  }
}
