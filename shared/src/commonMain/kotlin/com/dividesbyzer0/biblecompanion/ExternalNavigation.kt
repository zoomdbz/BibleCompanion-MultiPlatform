package com.dividesbyzer0.biblecompanion

internal sealed interface ExternalNavigationTarget {
  data object FocusSearch : ExternalNavigationTarget
  data class Navigate(val route: String) : ExternalNavigationTarget
}

private val externalStaticRoutes = setOf(
  Dest.Home.route,
  Dest.Read.route,
  Dest.Study.route,
  Dest.AboutCalendars.route,
  Dest.OrdainedFeasts.route,
  Dest.Settings.route,
  Dest.About.route,
  Dest.TranslationNotes.route,
  Dest.HistoricalAwareness.route,
  Dest.BibleCanon.route,
  Dest.FalseDoctrine.route,
  Dest.CommonDistortions.route,
  Dest.BibleChronology.route,
  Dest.Genealogy.route,
  Dest.JesusDivinity.route,
  Dest.JesusIdentity.route,
  Dest.Gospel.route,
  Dest.Grace.route,
  Dest.ChristianSymbolism.route,
  Dest.Christophanies.route,
  Dest.FAQs.route,
  Dest.Bibliography.route,
  Dest.UnseenWar.route,
  Dest.FeastCalendar.route,
  Dest.TorahFeastsAndGentiles.route,
  Dest.Prophecy.route,
  Dest.MessianicProphecy.route,
  Dest.DanielsTimeline.route,
  Dest.AstronomicalSigns.route,
  Dest.RevelationOverview.route,
  Dest.RevelationTimeline.route,
  Dest.SecondComingRapture.route,
  Dest.SavedItems.route,
  "tab_read",
  "tab_study",
  "tab_calendar"
)

private val externalBookCollections = setOf(
  "old_testament",
  "new_testament",
  "deuterocanonical",
  "apocrypha",
  "pseudepigrapha"
)

private val externalBookQueryKeys = setOf(
  "storyId",
  "verse",
  "verseEnd",
  "autoStartTts",
  "sourceLang",
  "sourceEdition"
)

/** Maps a caller-controlled route only when it matches an active navigation destination. */
internal fun externalNavigationTarget(route: String?): ExternalNavigationTarget? {
  if (route == null || route.isEmpty()) return null
  if (route == "search") return ExternalNavigationTarget.FocusSearch
  if (route in externalStaticRoutes) return ExternalNavigationTarget.Navigate(route)
  if (isValidBooksRoute(route) || isValidBookRoute(route)) {
    return ExternalNavigationTarget.Navigate(route)
  }
  return null
}

private fun isValidBooksRoute(route: String): Boolean {
  if ('?' in route || '#' in route) return false
  val parts = route.split('/')
  return parts.size == 2 && parts[0] == "books" && parts[1] in externalBookCollections
}

private fun isValidBookRoute(route: String): Boolean {
  if ('#' in route || route.count { it == '?' } > 1) return false
  val path = route.substringBefore('?')
  val parts = path.split('/')
  if (parts.size != 3 || parts[0] != "book" || parts[1] !in externalBookCollections ||
    !isValidRouteComponent(parts[2])) return false

  if ('?' !in route) return true
  val rawQuery = route.substringAfter('?')
  if (rawQuery.isEmpty()) return false
  val params = LinkedHashMap<String, String>()
  for (rawParam in rawQuery.split('&')) {
    val pair = rawParam.split('=', limit = 2)
    if (pair.size != 2 || pair[0] !in externalBookQueryKeys || pair[0] in params ||
      !isValidRouteComponent(pair[1])) return false
    params[pair[0]] = pair[1]
  }

  val verse = params["verse"]?.toPositiveIntOrNull()
  if ("verse" in params && verse == null) return false
  val verseEnd = params["verseEnd"]?.toPositiveIntOrNull()
  if ("verseEnd" in params && (verseEnd == null || verse == null || verseEnd < verse)) return false
  if (params["autoStartTts"]?.let { it != "true" && it != "false" } == true) return false
  return true
}

private fun String.toPositiveIntOrNull(): Int? = toIntOrNull()?.takeIf { it > 0 }

private fun isValidRouteComponent(value: String): Boolean {
  if (value.isEmpty()) return false
  var index = 0
  while (index < value.length) {
    val char = value[index]
    if (char <= ' ' || char in "/?#&=") return false
    if (char == '%') {
      if (index + 2 >= value.length || !value[index + 1].isHexDigit() ||
        !value[index + 2].isHexDigit()) return false
      index += 3
    } else {
      index++
    }
  }
  return true
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
