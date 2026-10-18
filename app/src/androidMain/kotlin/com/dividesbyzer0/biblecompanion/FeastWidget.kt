package com.dividesbyzer0.biblecompanion

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
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
import androidx.glance.text.FontFamily
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
    private const val SHOWN_FEASTS = 10
    val CAL_TYPE_KEY = stringPreferencesKey("feast_calendar_type")
  }

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)

    // Enough candidates that a single-calendar filter still fills the list.
    val feasts = CalendarUtils.upcomingFeasts(year, month, day, SHOWN_FEASTS * 6)
    val appearance = loadWidgetAppearance(context)

    provideContent {
      val prefs = currentState<Preferences>()
      val calType = prefs[CAL_TYPE_KEY] ?: "B"
      BibleCompanionWidgetTheme(appearance) {
        WidgetContent(feasts, calType, appearance.fontFamily)
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
    calType: String,
    family: FontFamily?
  ) {
    val size = LocalSize.current
    val tier = sizeTier(size)

    // Filter first, then cut to the shown count, so a single calendar fills the list.
    val selected = calendarTypeFor(calType)
    val shownFeasts = (if (selected == null) feasts else feasts.filter { it.calendarType == selected })
      .take(SHOWN_FEASTS)
    val showMarker = selected == null

    if (tier == SizeTier.COMPACT) {
      CompactContent(shownFeasts.firstOrNull(), family)
      return
    }

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
        .cornerRadius(24.dp)
        .background(GlanceTheme.colors.background)
        .padding(12.dp)
        .clickable(calendarWidgetOpenAction()),
      verticalAlignment = if (shownFeasts.size <= 1) Alignment.CenterVertically else Alignment.Top
    ) {
      // Header row with title and calendar filter chip
      Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "✡ " + WidgetStrings.feastsHeader(),
          style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = headerFontSize,
            color = GlanceTheme.colors.onSurfaceVariant,
            fontFamily = family
          ),
          maxLines = 1,
          modifier = GlanceModifier.defaultWeight()
        )
        Spacer(GlanceModifier.width(6.dp))
        WidgetChip(
          text = filterGlyphs(calType) + " " + filterLabel(calType),
          fontSize = toggleFontSize,
          action = actionRunCallback<ToggleFeastCalTypeAction>()
        )
      }
      Spacer(GlanceModifier.height(itemSpacing))

      if (shownFeasts.isEmpty()) {
        Text(
          text = WidgetStrings.noFeasts(),
          style = TextStyle(
            fontSize = nameFontSize,
            color = GlanceTheme.colors.onSurfaceVariant
          )
        )
      } else {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
          itemsIndexed(shownFeasts) { idx, feast ->
            Column(
              modifier = GlanceModifier.fillMaxWidth().clickable(calendarWidgetOpenAction())
            ) {
              if (idx == 0) {
                HeroRow(
                  feast = feast,
                  showMarker = showMarker,
                  nameFontSize = nameFontSizeFirst,
                  dateFontSize = dateFontSize,
                  family = family
                )
              } else {
                if (idx > 1) {
                  Spacer(GlanceModifier.height(itemSpacing))
                  Box(
                    modifier = GlanceModifier.fillMaxWidth().height(1.dp)
                      .background(GlanceTheme.colors.surfaceVariant)
                  ) {}
                }
                Spacer(GlanceModifier.height(itemSpacing))
                FeastRow(
                  feast = feast,
                  showMarker = showMarker,
                  nameFontSize = nameFontSize,
                  dateFontSize = dateFontSize,
                  countdownFontSize = countdownFontSize,
                  family = family
                )
              }
            }
          }
        }
      }
    }
  }

  /** The next feast, raised on a primary container card like the app's highlight cards. */
  @Composable
  private fun HeroRow(
    feast: CalendarUtils.UpcomingFeast,
    showMarker: Boolean,
    nameFontSize: TextUnit,
    dateFontSize: TextUnit,
    family: FontFamily?
  ) {
    Column(
      modifier = GlanceModifier
        .fillMaxWidth()
        .background(GlanceTheme.colors.primaryContainer)
        .cornerRadius(12.dp)
        .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
      Text(
        text = feastLabel(feast, showMarker),
        style = TextStyle(
          fontWeight = FontWeight.Bold,
          fontSize = nameFontSize,
          color = GlanceTheme.colors.onPrimaryContainer,
          fontFamily = family
        ),
        maxLines = 1
      )
      Spacer(GlanceModifier.height(2.dp))
      Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = formatDate(feast.gregorianMonth, feast.gregorianDay),
          style = TextStyle(
            fontSize = dateFontSize,
            color = GlanceTheme.colors.onPrimaryContainer
          ),
          maxLines = 1,
          modifier = GlanceModifier.defaultWeight()
        )
        Text(
          text = countdownLabel(feast.daysUntil),
          style = TextStyle(
            fontSize = dateFontSize,
            fontWeight = FontWeight.Bold,
            color = GlanceTheme.colors.onPrimaryContainer
          ),
          maxLines = 1
        )
      }
    }
  }

  @Composable
  private fun FeastRow(
    feast: CalendarUtils.UpcomingFeast,
    showMarker: Boolean,
    nameFontSize: TextUnit,
    dateFontSize: TextUnit,
    countdownFontSize: TextUnit,
    family: FontFamily?
  ) {
    val soon = feast.daysUntil <= 1
    Row(
      modifier = GlanceModifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
          text = feastLabel(feast, showMarker),
          style = TextStyle(
            fontSize = nameFontSize,
            color = GlanceTheme.colors.onSurface,
            fontFamily = family
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
          text = countdownLabel(feast.daysUntil),
          style = TextStyle(
            fontSize = countdownFontSize,
            fontWeight = if (soon) FontWeight.Bold else FontWeight.Normal,
            color = if (soon) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
          ),
          maxLines = 1
        )
      }
    }
  }

  @Composable
  private fun CompactContent(feast: CalendarUtils.UpcomingFeast?, family: FontFamily?) {
    Row(
      modifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(16.dp)
        .background(GlanceTheme.colors.primaryContainer)
        .padding(horizontal = 10.dp, vertical = 4.dp)
        .clickable(calendarWidgetOpenAction()),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (feast != null) {
        Text(
          text = feastLabel(feast, showMarker = false),
          style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = GlanceTheme.colors.onPrimaryContainer,
            fontFamily = family
          ),
          maxLines = 1,
          modifier = GlanceModifier.defaultWeight()
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
          text = when (feast.daysUntil) {
            0 -> WidgetStrings.today()
            1 -> WidgetStrings.tomorrow()
            else -> "${formatDate(feast.gregorianMonth, feast.gregorianDay)} • " + WidgetStrings.daysShort(feast.daysUntil)
          },
          style = TextStyle(
            fontSize = 10.sp,
            fontWeight = if (feast.daysUntil <= 1) FontWeight.Bold else FontWeight.Normal,
            color = GlanceTheme.colors.onPrimaryContainer
          ),
          maxLines = 1
        )
      } else {
        Text(
          text = WidgetStrings.noFeasts(),
          style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onPrimaryContainer)
        )
      }
    }
  }

  private fun feastLabel(feast: CalendarUtils.UpcomingFeast, showMarker: Boolean): String {
    val baseName = WidgetStrings.feastName(feast.id, feast.name)
    val label = if (feast.totalDays > 1) "$baseName (${WidgetStrings.dayOfFeast(feast.dayOfFeast)})" else baseName
    return if (showMarker) "$label ${calendarGlyph(feast.calendarType)}" else label
  }

  private fun countdownLabel(daysUntil: Int): String = when (daysUntil) {
    0 -> WidgetStrings.today()
    1 -> WidgetStrings.tomorrow()
    else -> WidgetStrings.daysShort(daysUntil)
  }

  private fun formatDate(month: Int, day: Int): String {
    val symbols = java.text.DateFormatSymbols(java.util.Locale.getDefault()).shortMonths
    val monthName = if (month in 1..symbols.size) symbols[month - 1] else ""
    return "$monthName $day"
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
    FeastWidget().update(context, glanceId)
  }
}
