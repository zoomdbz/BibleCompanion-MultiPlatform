package com.dividesbyzer0.biblecompanion

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString

/**
 * Synchronous accessors for the widget's UI strings, backed by the shared
 * Compose Resources string catalog (shared/src/commonMain/composeResources).
 *
 * Called from the iOS FeastWidgetExtension target via Kotlin/Native interop:
 * `WidgetStrings.shared.today()` in Swift. Strings.xml is the single source
 * of truth; the widget reads from the same catalog as the in-app UI.
 */
@OptIn(ExperimentalResourceApi::class)
object WidgetStrings {

  fun today(): String = runBlocking { getString(Res.string.widget_today) }
  fun tomorrow(): String = runBlocking { getString(Res.string.widget_tomorrow) }
  fun daysShort(days: Int): String = runBlocking { getString(Res.string.widget_days_short, days) }
  fun feastsHeader(): String = runBlocking { getString(Res.string.widget_feasts) }
  fun calHebrew(): String = runBlocking { getString(Res.string.widget_cal_hebrew) }
  fun calEssene(): String = runBlocking { getString(Res.string.widget_cal_essene) }
  fun calKaraite(): String = runBlocking { getString(Res.string.widget_cal_karaite) }
  fun calBoth(): String = runBlocking { getString(Res.string.widget_cal_both) }
  fun calAll(): String = runBlocking { getString(Res.string.widget_cal_all) }
  fun noFeasts(): String = runBlocking { getString(Res.string.widget_no_feasts) }
  fun dayOfFeast(day: Int): String = runBlocking { getString(Res.string.widget_day_of_feast, day) }
  fun listDisplayName(): String = runBlocking { getString(Res.string.widget_list_display_name) }
  fun listDescription(): String = runBlocking { getString(Res.string.widget_list_description) }
  fun gridDisplayName(): String = runBlocking { getString(Res.string.widget_grid_display_name) }
  fun gridDescription(): String = runBlocking { getString(Res.string.widget_grid_description) }

  fun todayLabel(): String = runBlocking { getString(Res.string.nav_today) }

  fun shortcutSearch(): String = runBlocking { getString(Res.string.action_search) }
  fun shortcutBookmarks(): String = runBlocking { getString(Res.string.bookmarks_tab) }
  fun shortcutContinue(): String = runBlocking { getString(Res.string.continue_reading) }
  fun shortcutFeastCalendar(): String = runBlocking { getString(Res.string.widget_list_display_name) }
  fun appName(): String = runBlocking { getString(Res.string.app_name) }

  /**
   * Resolves a FeastMarker id (e.g. "passover", "tabernacles") to the localized
   * display name used in the calendar list. Falls back to the passed English
   * name if the id is unknown.
   */
  fun feastName(id: String, englishFallback: String): String = runBlocking {
    when (id) {
      "passover" -> getString(Res.string.feast_passover)
      "unleavened" -> getString(Res.string.feast_unleavened)
      "firstfruits" -> getString(Res.string.feast_firstfruits)
      "second_passover" -> getString(Res.string.feast_second_passover)
      "pentecost" -> getString(Res.string.feast_weeks)
      "trumpets" -> getString(Res.string.feast_trumpets)
      "atonement" -> getString(Res.string.feast_atonement)
      "tabernacles" -> getString(Res.string.feast_tabernacles)
      "assembly" -> getString(Res.string.feast_shemini_atzeret)
      "fast_tevet" -> getString(Res.string.feast_fast_tevet)
      "fast_esther" -> getString(Res.string.feast_fast_esther)
      "purim" -> getString(Res.string.feast_purim)
      "fast_tammuz" -> getString(Res.string.feast_fast_tammuz)
      "tisha_bav" -> getString(Res.string.feast_tisha_bav)
      "fast_gedaliah" -> getString(Res.string.feast_fast_gedaliah)
      "hanukkah" -> getString(Res.string.feast_hanukkah)
      else -> englishFallback
    }
  }
}
