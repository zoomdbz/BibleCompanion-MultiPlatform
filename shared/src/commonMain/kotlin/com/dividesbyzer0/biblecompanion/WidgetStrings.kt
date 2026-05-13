package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleLanguage
import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleScript
import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleCountry
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
 *
 * Fallbacks are localized via [localizedFallback] keyed by system locale so
 * that resource-lookup failures still respect the user's language.
 */
@OptIn(ExperimentalResourceApi::class)
object WidgetStrings {

  private fun localeKey(): String {
    val lang = runCatching { platformGetDefaultLocaleLanguage() }.getOrDefault("en")
    if (lang == "zh") {
      val script = runCatching { platformGetDefaultLocaleScript() }.getOrDefault("")
      val country = runCatching { platformGetDefaultLocaleCountry() }.getOrDefault("")
      return if (script.contains("hant", ignoreCase = true) || country in setOf("TW", "HK", "MO"))
        "zh-Hant" else "zh-Hans"
    }
    return when (lang) {
      "es", "fr", "de", "it", "pt", "ru", "ar", "hi", "ja", "ko" -> lang
      else -> "en"
    }
  }

  // Localized fallbacks used when Compose Resources lookup throws on Kotlin/Native.
  // Keep keys stable; entries must cover every fallback string used below.
  private val FALLBACKS: Map<String, Map<String, String>> = mapOf(
    "today" to mapOf(
      "en" to "Today", "es" to "Hoy", "fr" to "Aujourd’hui", "de" to "Heute",
      "it" to "Oggi", "pt" to "Hoje", "ru" to "Сегодня", "ar" to "اليوم",
      "hi" to "आज", "ja" to "今日", "ko" to "오늘",
      "zh-Hans" to "今天", "zh-Hant" to "今天"
    ),
    "tomorrow" to mapOf(
      "en" to "Tomorrow", "es" to "Mañana", "fr" to "Demain", "de" to "Morgen",
      "it" to "Domani", "pt" to "Amanhã", "ru" to "Завтра", "ar" to "غدًا",
      "hi" to "कल", "ja" to "明日", "ko" to "내일",
      "zh-Hans" to "明天", "zh-Hant" to "明天"
    ),
    "feasts" to mapOf(
      "en" to "Feasts", "es" to "Fiestas", "fr" to "Fêtes", "de" to "Feste",
      "it" to "Feste", "pt" to "Festas", "ru" to "Праздники", "ar" to "الأعياد",
      "hi" to "पर्व", "ja" to "祭り", "ko" to "절기",
      "zh-Hans" to "节期", "zh-Hant" to "節期"
    ),
    "hebrew" to mapOf(
      "en" to "Hebrew", "es" to "Hebreo", "fr" to "Hébreu", "de" to "Hebräisch",
      "it" to "Ebraico", "pt" to "Hebraico", "ru" to "Иврит", "ar" to "العبرية",
      "hi" to "इब्रानी", "ja" to "ヘブライ", "ko" to "히브리어",
      "zh-Hans" to "希伯来", "zh-Hant" to "希伯來"
    ),
    "essene" to mapOf(
      "en" to "Essene", "es" to "Esenio", "fr" to "Essénien", "de" to "Essener",
      "it" to "Esseno", "pt" to "Essênio", "ru" to "Ессеи", "ar" to "إسيني",
      "hi" to "एस्सेन", "ja" to "エッセネ派", "ko" to "에세네",
      "zh-Hans" to "艾赛尼", "zh-Hant" to "艾賽尼"
    ),
    "karaite" to mapOf(
      "en" to "Karaite", "es" to "Caraíta", "fr" to "Caraïte", "de" to "Karäer",
      "it" to "Caraita", "pt" to "Caraíta", "ru" to "Караимы", "ar" to "قرائيم",
      "hi" to "कराइट", "ja" to "カライ派", "ko" to "카라이",
      "zh-Hans" to "卡拉派", "zh-Hant" to "卡拉派"
    ),
    "both" to mapOf(
      "en" to "Both", "es" to "Ambos", "fr" to "Les deux", "de" to "Beide",
      "it" to "Entrambi", "pt" to "Ambos", "ru" to "Оба", "ar" to "كلاهما",
      "hi" to "दोनों", "ja" to "両方", "ko" to "둘 다",
      "zh-Hans" to "两者", "zh-Hant" to "兩者"
    ),
    "all" to mapOf(
      "en" to "All", "es" to "Todos", "fr" to "Tous", "de" to "Alle",
      "it" to "Tutti", "pt" to "Todos", "ru" to "Все", "ar" to "الكل",
      "hi" to "सभी", "ja" to "すべて", "ko" to "전체",
      "zh-Hans" to "全部", "zh-Hant" to "全部"
    ),
    "no_feasts" to mapOf(
      "en" to "No feasts", "es" to "Sin fiestas", "fr" to "Aucune fête", "de" to "Keine Feste",
      "it" to "Nessuna festa", "pt" to "Sem festas", "ru" to "Нет праздников", "ar" to "لا أعياد",
      "hi" to "कोई पर्व नहीं", "ja" to "祭りなし", "ko" to "절기 없음",
      "zh-Hans" to "无节期", "zh-Hant" to "無節期"
    ),
    "feast_calendar" to mapOf(
      "en" to "Feast Calendar", "es" to "Calendario de fiestas", "fr" to "Calendrier des fêtes",
      "de" to "Festkalender", "it" to "Calendario delle feste", "pt" to "Calendário das festas",
      "ru" to "Календарь праздников", "ar" to "تقويم الأعياد", "hi" to "पर्व कैलेंडर",
      "ja" to "祭りカレンダー", "ko" to "절기 달력",
      "zh-Hans" to "节期日历", "zh-Hant" to "節期日曆"
    ),
    "upcoming_feasts" to mapOf(
      "en" to "Upcoming biblical feasts",
      "es" to "Próximas fiestas bíblicas", "fr" to "Prochaines fêtes bibliques",
      "de" to "Kommende biblische Feste", "it" to "Prossime feste bibliche",
      "pt" to "Próximas festas bíblicas", "ru" to "Ближайшие библейские праздники",
      "ar" to "الأعياد الكتابية القادمة", "hi" to "आगामी बाइबिल पर्व",
      "ja" to "今後の聖書の祭り", "ko" to "다가오는 성경 절기",
      "zh-Hans" to "即将到来的圣经节期", "zh-Hant" to "即將到來的聖經節期"
    ),
    "feast_grid" to mapOf(
      "en" to "Feast Grid", "es" to "Rejilla de fiestas", "fr" to "Grille des fêtes",
      "de" to "Festraster", "it" to "Griglia delle feste", "pt" to "Grade de festas",
      "ru" to "Сетка праздников", "ar" to "شبكة الأعياد", "hi" to "पर्व ग्रिड",
      "ja" to "祭りグリッド", "ko" to "절기 격자",
      "zh-Hans" to "节期网格", "zh-Hant" to "節期網格"
    ),
    "biblical_feast_grid" to mapOf(
      "en" to "Biblical feast grid", "es" to "Rejilla de fiestas bíblicas",
      "fr" to "Grille des fêtes bibliques", "de" to "Biblisches Festraster",
      "it" to "Griglia delle feste bibliche", "pt" to "Grade de festas bíblicas",
      "ru" to "Сетка библейских праздников", "ar" to "شبكة الأعياد الكتابية",
      "hi" to "बाइबिल पर्व ग्रिड", "ja" to "聖書の祭りグリッド",
      "ko" to "성경 절기 격자",
      "zh-Hans" to "圣经节期网格", "zh-Hant" to "聖經節期網格"
    ),
    "search" to mapOf(
      "en" to "Search", "es" to "Buscar", "fr" to "Rechercher", "de" to "Suche",
      "it" to "Cerca", "pt" to "Buscar", "ru" to "Поиск", "ar" to "بحث",
      "hi" to "खोज", "ja" to "検索", "ko" to "검색",
      "zh-Hans" to "搜索", "zh-Hant" to "搜尋"
    ),
    "bookmarks" to mapOf(
      "en" to "Bookmarks", "es" to "Marcadores", "fr" to "Signets", "de" to "Lesezeichen",
      "it" to "Segnalibri", "pt" to "Marcadores", "ru" to "Закладки", "ar" to "إشارات مرجعية",
      "hi" to "बुकमार्क", "ja" to "ブックマーク", "ko" to "북마크",
      "zh-Hans" to "书签", "zh-Hant" to "書籤"
    ),
    "continue_reading" to mapOf(
      "en" to "Continue Reading", "es" to "Seguir leyendo", "fr" to "Continuer la lecture",
      "de" to "Weiterlesen", "it" to "Continua a leggere", "pt" to "Continuar a leitura",
      "ru" to "Продолжить чтение", "ar" to "متابعة القراءة", "hi" to "पढ़ना जारी रखें",
      "ja" to "続きを読む", "ko" to "이어 읽기",
      "zh-Hans" to "继续阅读", "zh-Hant" to "繼續閱讀"
    ),
    "app_name" to mapOf(
      "en" to "Bible Companion", "es" to "Bible Companion", "fr" to "Bible Companion",
      "de" to "Bible Companion", "it" to "Bible Companion", "pt" to "Bible Companion",
      "ru" to "Bible Companion", "ar" to "Bible Companion", "hi" to "Bible Companion",
      "ja" to "Bible Companion", "ko" to "Bible Companion",
      "zh-Hans" to "Bible Companion", "zh-Hant" to "Bible Companion"
    )
  )

  private fun localizedFallback(key: String): String {
    val map = FALLBACKS[key] ?: return ""
    val lk = localeKey()
    return map[lk] ?: map["en"] ?: ""
  }

  private inline fun safe(fallback: String, block: () -> String): String =
    try { block() } catch (_: Throwable) { fallback }

  fun today(): String = safe(localizedFallback("today")) { runBlocking { getString(Res.string.widget_today) } }
  fun tomorrow(): String = safe(localizedFallback("tomorrow")) { runBlocking { getString(Res.string.widget_tomorrow) } }
  fun daysShort(days: Int): String = safe("$days") { runBlocking { getString(Res.string.widget_days_short, days) } }
  fun feastsHeader(): String = safe(localizedFallback("feasts")) { runBlocking { getString(Res.string.widget_feasts) } }
  fun calHebrew(): String = safe(localizedFallback("hebrew")) { runBlocking { getString(Res.string.widget_cal_hebrew) } }
  fun calEssene(): String = safe(localizedFallback("essene")) { runBlocking { getString(Res.string.widget_cal_essene) } }
  fun calKaraite(): String = safe(localizedFallback("karaite")) { runBlocking { getString(Res.string.widget_cal_karaite) } }
  fun calBoth(): String = safe(localizedFallback("both")) { runBlocking { getString(Res.string.widget_cal_both) } }
  fun calAll(): String = safe(localizedFallback("all")) { runBlocking { getString(Res.string.widget_cal_all) } }
  fun noFeasts(): String = safe(localizedFallback("no_feasts")) { runBlocking { getString(Res.string.widget_no_feasts) } }
  fun dayOfFeast(day: Int): String = safe("$day") { runBlocking { getString(Res.string.widget_day_of_feast, day) } }
  fun listDisplayName(): String = safe(localizedFallback("feast_calendar")) { runBlocking { getString(Res.string.widget_list_display_name) } }
  fun listDescription(): String = safe(localizedFallback("upcoming_feasts")) { runBlocking { getString(Res.string.widget_list_description) } }
  fun gridDisplayName(): String = safe(localizedFallback("feast_grid")) { runBlocking { getString(Res.string.widget_grid_display_name) } }
  fun gridDescription(): String = safe(localizedFallback("biblical_feast_grid")) { runBlocking { getString(Res.string.widget_grid_description) } }

  fun todayLabel(): String = safe(localizedFallback("today")) { runBlocking { getString(Res.string.nav_today) } }

  fun shortcutSearch(): String = safe(localizedFallback("search")) { runBlocking { getString(Res.string.action_search) } }
  fun shortcutBookmarks(): String = safe(localizedFallback("bookmarks")) { runBlocking { getString(Res.string.bookmarks_tab) } }
  fun shortcutContinue(): String = safe(localizedFallback("continue_reading")) { runBlocking { getString(Res.string.continue_reading) } }
  fun shortcutFeastCalendar(): String = safe(localizedFallback("feast_calendar")) { runBlocking { getString(Res.string.widget_list_display_name) } }
  fun appName(): String = safe(localizedFallback("app_name")) { runBlocking { getString(Res.string.app_name) } }

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
