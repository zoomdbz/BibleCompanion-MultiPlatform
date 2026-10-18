package com.dividesbyzer0.biblecompanion

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dividesbyzer0.biblecompanion.platform.WidgetColorSchemes
import com.dividesbyzer0.biblecompanion.platform.widgetColorSchemes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.first

/** Saved preferences and the resolved color schemes that shape the home-screen widgets. */
internal class WidgetAppearance(val prefs: PrefsState?, val schemes: WidgetColorSchemes) {
  /** Mirrors the app's serif reading mode in widget titles and feast names. */
  val fontFamily: FontFamily? = if (prefs?.fontMode == "serif") FontFamily.Serif else null
}

internal suspend fun loadWidgetAppearance(context: Context): WidgetAppearance {
  val prefs = runCatching { PrefsRepo(context).flow.first() }.getOrNull()
  return WidgetAppearance(prefs, widgetColorSchemes(context, prefs))
}

/** Applies the in-app theme (preset, custom accents, dynamic color, forced light or dark) to Glance. */
@Composable
internal fun BibleCompanionWidgetTheme(
  appearance: WidgetAppearance,
  content: @Composable () -> Unit
) {
  GlanceTheme(
    colors = ColorProviders(appearance.schemes.light, appearance.schemes.dark),
    content = content
  )
}

// Same season colors as the in-app feast calendar: spring gold, fall indigo.
private val SpringColor = Color(0xFFD4A017)
private val FallColor = Color(0xFF5B6BBF)

internal fun seasonColor(spring: Boolean, alpha: Float = 1f): ColorProvider =
  ColorProvider((if (spring) SpringColor else FallColor).copy(alpha = alpha))

/** The app's softened tertiary container for days where Essene and Hebrew or Karaite feasts coincide. */
internal fun mixedFeastCellColor(schemes: WidgetColorSchemes): ColorProvider =
  androidx.glance.color.ColorProvider(
    day = schemes.light.tertiaryContainer.copy(alpha = 0.6f),
    night = schemes.dark.tertiaryContainer.copy(alpha = 0.6f)
  )

/** Marker color per calendar, matching the in-app legend. */
@Composable
internal fun calendarMarkerColor(calendar: FeastCalendarType, spring: Boolean): ColorProvider =
  when (calendar) {
    FeastCalendarType.HEBREW -> seasonColor(spring)
    FeastCalendarType.ESSENE -> GlanceTheme.colors.tertiary
    FeastCalendarType.KARAITE -> GlanceTheme.colors.secondary
  }

/** Same glyphs on both platforms: Star of David, sun, moon. */
internal fun calendarGlyph(calendar: FeastCalendarType): String = when (calendar) {
  FeastCalendarType.HEBREW -> "✡"
  FeastCalendarType.ESSENE -> "☉"
  FeastCalendarType.KARAITE -> "☾"
}

internal fun calendarTypeFor(calType: String): FeastCalendarType? = when (calType) {
  "H" -> FeastCalendarType.HEBREW
  "E" -> FeastCalendarType.ESSENE
  "K" -> FeastCalendarType.KARAITE
  else -> null
}

internal fun calendarLabel(calendar: FeastCalendarType): String = when (calendar) {
  FeastCalendarType.HEBREW -> WidgetStrings.calHebrew()
  FeastCalendarType.ESSENE -> WidgetStrings.calEssene()
  FeastCalendarType.KARAITE -> WidgetStrings.calKaraite()
}

internal fun filterGlyphs(calType: String): String =
  calendarTypeFor(calType)?.let { calendarGlyph(it) } ?: "✡☉☾"

internal fun filterLabel(calType: String): String =
  calendarTypeFor(calType)?.let { calendarLabel(it) } ?: WidgetStrings.calAll()

/** Month title such as "October 2026" or "2026年10月" in the device locale, like the iOS widget. */
internal fun widgetMonthTitle(year: Int, month: Int): String {
  val locale = Locale.getDefault()
  val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
  val calendar = Calendar.getInstance(locale).apply {
    clear()
    set(year, month - 1, 1)
  }
  return SimpleDateFormat(pattern, locale).format(calendar.time)
}

/** A tappable pill in the theme's secondary container, the same control style as the app's chips. */
@Composable
internal fun WidgetChip(
  text: String,
  fontSize: TextUnit,
  action: Action,
  modifier: GlanceModifier = GlanceModifier,
  horizontalPadding: Dp = 8.dp,
  verticalPadding: Dp = 4.dp
) {
  Box(
    modifier = modifier
      .background(GlanceTheme.colors.secondaryContainer)
      .cornerRadius(12.dp)
      .padding(horizontal = horizontalPadding, vertical = verticalPadding)
      .clickable(action),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      style = TextStyle(
        color = GlanceTheme.colors.onSecondaryContainer,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold
      ),
      maxLines = 1
    )
  }
}

/** Legend entry: the calendar glyph in its marker color, the label in the muted text color, as in the app. */
@Composable
internal fun WidgetLegendEntry(calendar: FeastCalendarType, spring: Boolean, fontSize: TextUnit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = calendarGlyph(calendar),
      style = TextStyle(fontSize = fontSize, color = calendarMarkerColor(calendar, spring)),
      maxLines = 1
    )
    Spacer(GlanceModifier.width(3.dp))
    Text(
      text = calendarLabel(calendar),
      style = TextStyle(fontSize = fontSize, color = GlanceTheme.colors.onSurfaceVariant),
      maxLines = 1
    )
  }
}

/** Re-renders every placed widget; called when theme or font preferences change. */
internal suspend fun refreshHomeScreenWidgets(context: Context) {
  runCatching { FeastWidget().updateAll(context) }
  runCatching { CalendarGridWidget().updateAll(context) }
}
