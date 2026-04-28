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
 *
 * Every call is wrapped in runCatching because on Kotlin/Native,
 * runBlocking { getString() } can dispatch through Dispatchers.Default
 * (GCD utility-qos). An exception on that queue triggers __cxa_throw
 * and abort() before structured concurrency can propagate it back.
 */
@OptIn(ExperimentalResourceApi::class)
object WidgetStrings {

  private inline fun safe(fallback: String, block: () -> String): String =
    try { block() } catch (_: Throwable) { fallback }

  fun today(): String = safe("Today") { runBlocking { getString(Res.string.widget_today) } }
  fun tomorrow(): String = safe("Tomorrow") { runBlocking { getString(Res.string.widget_tomorrow) } }
  fun daysShort(days: Int): String = safe("$days days") { runBlocking { getString(Res.string.widget_days_short, days) } }
  fun feastsHeader(): String = safe("Feasts") { runBlocking { getString(Res.string.widget_feasts) } }
  fun calHebrew(): String = safe("Hebrew") { runBlocking { getString(Res.string.widget_cal_hebrew) } }
  fun calEssene(): String = safe("Essene") { runBlocking { getString(Res.string.widget_cal_essene) } }
  fun calKaraite(): String = safe("Karaite") { runBlocking { getString(Res.string.widget_cal_karaite) } }
  fun calBoth(): String = safe("Both") { runBlocking { getString(Res.string.widget_cal_both) } }
  fun calAll(): String = safe("All") { runBlocking { getString(Res.string.widget_cal_all) } }
  fun noFeasts(): String = safe("No feasts") { runBlocking { getString(Res.string.widget_no_feasts) } }
  fun dayOfFeast(day: Int): String = safe("Day $day") { runBlocking { getString(Res.string.widget_day_of_feast, day) } }
  fun listDisplayName(): String = safe("Feast Calendar") { runBlocking { getString(Res.string.widget_list_display_name) } }
  fun listDescription(): String = safe("Upcoming biblical feasts") { runBlocking { getString(Res.string.widget_list_description) } }
  fun gridDisplayName(): String = safe("Feast Grid") { runBlocking { getString(Res.string.widget_grid_display_name) } }
  fun gridDescription(): String = safe("Biblical feast grid") { runBlocking { getString(Res.string.widget_grid_description) } }

  fun todayLabel(): String = safe("Today") { runBlocking { getString(Res.string.nav_today) } }

  fun shortcutSearch(): String = safe("Search") { runBlocking { getString(Res.string.action_search) } }
  fun shortcutBookmarks(): String = safe("Bookmarks") { runBlocking { getString(Res.string.bookmarks_tab) } }
  fun shortcutContinue(): String = safe("Continue Reading") { runBlocking { getString(Res.string.continue_reading) } }
  fun shortcutFeastCalendar(): String = safe("Feast Calendar") { runBlocking { getString(Res.string.widget_list_display_name) } }
  fun appName(): String = safe("Bible Companion") { runBlocking { getString(Res.string.app_name) } }

  fun feastName(id: String, englishFallback: String): String = safe(englishFallback) {
    runBlocking {
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
}
