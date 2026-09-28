package com.dividesbyzer0.biblecompanion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BookmarkBorder
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import com.dividesbyzer0.biblecompanion.platform.platformAppBuild
import com.dividesbyzer0.biblecompanion.platform.platformAppVersion
import com.dividesbyzer0.biblecompanion.platform.platformOpenUrl
import com.dividesbyzer0.biblecompanion.platform.platformCopyToClipboard
import com.dividesbyzer0.biblecompanion.platform.platformShareText
import com.dividesbyzer0.biblecompanion.platform.isApplePlatform
import com.dividesbyzer0.biblecompanion.platform.platformOnnxInit
import com.dividesbyzer0.biblecompanion.platform.platformOnnxIsReady
import com.dividesbyzer0.biblecompanion.platform.currentTimeMillis
import com.dividesbyzer0.biblecompanion.platform.platformCurrentDate
import com.dividesbyzer0.biblecompanion.platform.platformTtsInit
import com.dividesbyzer0.biblecompanion.platform.platformTtsSpeak
import com.dividesbyzer0.biblecompanion.platform.platformTtsStop
import com.dividesbyzer0.biblecompanion.platform.platformTtsIsSpeaking
import com.dividesbyzer0.biblecompanion.platform.platformTtsSetOnDone
import com.dividesbyzer0.biblecompanion.platform.platformTtsPause
import com.dividesbyzer0.biblecompanion.platform.platformTtsResume
import com.dividesbyzer0.biblecompanion.platform.platformTtsIsPaused
import com.dividesbyzer0.biblecompanion.platform.platformSetAppLocale
import com.dividesbyzer0.biblecompanion.platform.platformDynamicColorScheme
import com.dividesbyzer0.biblecompanion.platform.platformSupportsDynamicColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// --------------- App Root ----------------

@Composable
fun AppRoot(
  shortcutAction: String? = null,
  deepLinkRoute: String? = null,
  shortcutEventId: Long = 0L,
  deepLinkEventId: Long = 0L
) {
  val ctx = LocalPlatformContext.current
  val repo = remember { PrefsRepo(ctx) }
  val initialPrefs = remember { repo.initialSnapshot() }
  val prefs by repo.flow.collectAsState(initialPrefs)

  val notificationRefresh by DailyVerseNotificationBridge.refresh.collectAsState()
  LaunchedEffect(notificationRefresh, prefs.dailyVerseNotifications,
    prefs.dailyVerseNotificationMinuteOfDay, prefs.appLanguage,
    prefs.internalBibleVersion, prefs.divineName) {
    DailyVerseNotificationBridge.synchronize(prefs)
  }

  LaunchedEffect(prefs.appLanguage) {
    // Sync platform locale on every change so iOS (no-op MainActivity hook)
    // and Android both pick up the user's saved language without depending on
    // the language-picker code path. Both platforms guard against redundant
    // sets, so calling here on launch is safe.
    runCatching { platformSetAppLocale(prefs.appLanguage) }
    withContext(Dispatchers.Default) {
      runCatching { ScriptureRefs.primeBooks(ctx, prefs.appLanguage) }
    }
  }

  val dark = when (prefs.theme.lowercase()) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }

  val scale = prefs.textSizeScale
  val preset = ThemePreset.fromKey(prefs.themePreset)
  val dynamicScheme = if (preset == ThemePreset.Dynamic) platformDynamicColorScheme(dark) else null
  val resolvedScheme = dynamicScheme ?: colorSchemeFor(
    preset,
    dark,
    prefs.customThemeHue,
    prefs.customThemeSaturation,
    prefs.customThemeLightness,
    prefs.customThemeSecondary,
    prefs.customThemeTertiary
  )

  MaterialTheme(
    colorScheme = resolvedScheme,
    typography = if (prefs.fontMode == "serif")
      buildSerifTypography(prefs.appLanguage, scale)
    else if (scale != 1.0f)
      buildScaledTypography(scale)
    else
      Typography()
  ) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val currentEntry by nav.currentBackStackEntryAsState()
    val tabRoutes = listOf(Dest.Home.route, "tab_read", "tab_study", "tab_calendar")
    val currentTab = currentEntry?.destination?.hierarchy
      ?.firstOrNull { it.route in tabRoutes }?.route
    var savedReadingNowIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var savedReadingNowRestoreRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val currentReadingNowIdentity = readingNowRestoreIdentity(
      collection = prefs.lastReadCollection,
      bookId = prefs.lastReadBookId,
      storyId = prefs.lastReadStoryId,
      sourceLanguage = prefs.lastReadSourceLanguage,
      sourceEdition = prefs.lastReadSourceEdition,
      currentLanguage = LocaleUtils.effectiveAssetTag(prefs.appLanguage),
      currentEdition = BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)
    )
    fun expireSavedReadingNowState() {
      savedReadingNowIdentity = null
      savedReadingNowRestoreRoute = null
      // Navigation 2.8.0-alpha10 keeps the first saved state for a destination
      // ID. Clear the Read graph so an older reader cannot block a newer snapshot.
      // A reader popped to Home is also indexed by its parent Read graph, while
      // Study and Calendar states use their own graph IDs and remain untouched.
      nav.clearBackStack("tab_read")
    }
    val selectTab: (String) -> Unit = select@{ route ->
      val rootRoute = mainTabRootRoute(route) ?: return@select
      fun liveEntry(): MainTabStackEntry? = nav.currentBackStackEntry?.let { entry ->
        MainTabStackEntry(
          entry.id,
          entry.destination.hierarchy.firstOrNull { it.route in tabRoutes }?.route,
          entry.destination.route
        )
      }
      if (liveEntry()?.destinationRoute == rootRoute) return@select

      // Scripture links can leave Calendar/Study below a reader so Back still
      // returns to the origin. A tab click saves those graphs independently:
      // popping everything above Home would save the reader AS Calendar/Study.
      val savedTabs = mutableSetOf<String>()
      fun saveForeignStacks() {
        saveForeignMainTabStacks(route, ::liveEntry, { entry, saveState ->
          val graphRoute = entry.tabRoute ?: return@saveForeignMainTabStacks
          if (saveState) {
            // Navigation keeps the first snapshot for an ID. Replace it before
            // saving this graph; never clear an alias after saving fresh state.
            nav.clearBackStack(graphRoute)
            if (graphRoute == "tab_read") {
              val leavingReader = entry.destinationRoute?.startsWith("book/") == true
              savedReadingNowIdentity = currentReadingNowIdentity.takeIf { leavingReader }
              savedReadingNowRestoreRoute = "tab_read".takeIf { leavingReader }
            }
          }
          nav.popBackStack(graphRoute, inclusive = true, saveState = saveState)
        }, savedTabs)
      }
      saveForeignStacks()

      if (route == "tab_read") {
        // Read explicitly opens the library; Reading Now resumes a passage.
        expireSavedReadingNowState()
      }
      if (route == Dest.Home.route) {
        if (nav.currentDestination?.route != Dest.Home.route) {
          nav.navigate(Dest.Home.route) {
            popUpTo(Dest.Home.route) { inclusive = false }
            launchSingleTop = true
          }
        }
      } else {
        if (liveEntry()?.tabRoute != route) {
          nav.navigate(route) {
            launchSingleTop = true
            restoreState = route != "tab_read"
          }
          // Repair a mixed snapshot saved by an older build. Peeling its foreign
          // graphs preserves the reader viewport instead of discarding it.
          saveForeignStacks()
          if (liveEntry()?.tabRoute != route) {
            nav.navigate(route) { launchSingleTop = true }
          }
        }
        // Each main button leads to its hub, including Study entered from a
        // Scripture link. Reuse a live hub to retain its list/calendar state.
        if (nav.currentDestination?.route != rootRoute) {
          if (!nav.popBackStack(rootRoute, inclusive = false, saveState = false)) {
            nav.navigate(rootRoute) {
              popUpTo(route) { inclusive = false }
              launchSingleTop = true
            }
          }
        }
      }
    }
    val continueReading: () -> Unit = {
      val col = prefs.lastReadCollection
      val bookId = prefs.lastReadBookId
      if (col != null && bookId != null) {
        val expectedIdentity = readingNowRestoreIdentity(
          collection = col,
          bookId = bookId,
          storyId = prefs.lastReadStoryId,
          sourceLanguage = prefs.lastReadSourceLanguage,
          sourceEdition = prefs.lastReadSourceEdition,
          currentLanguage = LocaleUtils.effectiveAssetTag(prefs.appLanguage),
          currentEdition = BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)
        )
        var restoredReader = false
        val restoreRoute = savedReadingNowRestoreRoute
        if (
          savedReadingNowIdentity != null &&
          savedReadingNowIdentity == expectedIdentity &&
          restoreRoute != null
        ) {
          // Home saves the Read graph. Restoring it retains BookScreen's
          // saveable LazyList and semantic viewport anchor for this session.
          // Consume our marker before navigating; a later Home visit records
          // a fresh snapshot. If Navigation cannot restore the saved graph,
          // the destination check below takes the durable chapter fallback.
          savedReadingNowIdentity = null
          savedReadingNowRestoreRoute = null
          nav.navigate(restoreRoute) {
            launchSingleTop = true
            restoreState = true
          }
          val restored = nav.currentBackStackEntry
          restoredReader = restoredReadingNowReaderMatches(
            destinationRoute = restored?.destination?.route,
            restoredCollection = restored?.arguments?.getString("col"),
            restoredBookId = restored?.arguments?.getString("bookId"),
            expectedCollection = col,
            expectedBookId = bookId
          )
        }
        if (!restoredReader) {
          // A failed restore can leave a saved Read alias behind. Expire it
          // before creating the durable chapter-level fallback reader.
          expireSavedReadingNowState()
          // Cold start, changed language/edition, or an unrelated saved Read
          // destination: retain the established chapter-level resume route.
          nav.navigate(Dest.BookView.route(col, bookId, prefs.lastReadStoryId,
            sourceLang = prefs.lastReadSourceLanguage ?: "en",
            sourceEdition = prefs.lastReadSourceEdition)) { launchSingleTop = true }
        }
      }
    }
    val navBack: () -> Unit = {
      if (!nav.popBackStack()) nav.navigate(Dest.Home.route) {
        popUpTo(Dest.Home.route) { inclusive = true }
      }
    }
    // Explicit passage targets must remain events even when launchSingleTop
    // reuses a reader entry holding the same route and saveable state. Negative
    // ids keep this stream disjoint from platform-supplied external event ids.
    var nextInternalReaderRequestId by rememberSaveable { mutableStateOf(-1L) }
    val freshInternalReaderRequestId: () -> Long = {
      val requestId = nextInternalReaderRequestId
      nextInternalReaderRequestId = followingInternalReaderRequestId(requestId)
      requestId
    }

    var pendingSearchFocus by remember { mutableStateOf(false) }
    val focusHomeSearch: () -> Unit = {
      if (nav.currentDestination?.route != Dest.Home.route) {
        nav.navigate(Dest.Home.route) {
          popUpTo(Dest.Home.route) { inclusive = true }
          launchSingleTop = true
        }
      }
      pendingSearchFocus = true
    }
    // Android re-delivers its launch intent after rotation, while iOS keeps
    // shortcuts and deep links in independent event streams. Consume each
    // stream once so rotation cannot replay it and a later event cannot replay
    // the stale value from the other stream.
    var shortcutNavConsumed by rememberSaveable(shortcutAction, shortcutEventId) {
      mutableStateOf(false)
    }
    LaunchedEffect(shortcutAction, shortcutEventId) {
      if (shortcutAction == null || shortcutNavConsumed) return@LaunchedEffect
      shortcutNavConsumed = true
      when (shortcutAction) {
        "search" -> focusHomeSearch()
        "bookmarks" -> nav.navigate(Dest.SavedItems.route) { launchSingleTop = true }
        "feast_calendar" -> nav.navigate(Dest.FeastCalendar.route) { launchSingleTop = true }
        "continue" -> continueReading()
      }
    }

    var deepLinkNavConsumed by rememberSaveable(deepLinkRoute, deepLinkEventId) {
      mutableStateOf(false)
    }
    LaunchedEffect(deepLinkRoute, deepLinkEventId) {
      if (deepLinkRoute == null || deepLinkNavConsumed) return@LaunchedEffect
      deepLinkNavConsumed = true
      when (val target = externalNavigationTarget(deepLinkRoute)) {
        ExternalNavigationTarget.FocusSearch -> focusHomeSearch()
        is ExternalNavigationTarget.Navigate -> {
          if (invalidatesSavedReadingNowState(target.route)) expireSavedReadingNowState()
          nav.navigate(withExternalReaderRequestId(target.route, deepLinkEventId)) {
            launchSingleTop = true
          }
        }
        null -> Unit
      }
    }

    val internalNavigate: (String, String, String?, Int?, Int?) -> Unit = { col, bookId, storyId, verse, verseEnd ->
      expireSavedReadingNowState()
      nav.navigate(Dest.BookView.route(col, bookId, storyId, verse, verseEnd,
        sourceLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage),
        requestId = freshInternalReaderRequestId())) { launchSingleTop = true }
    }

    val editionNavigate: (EditionDestination) -> Unit = { target ->
      expireSavedReadingNowState()
      nav.navigate(Dest.BookView.route(
        target.collection, target.bookId, target.storyId, target.verse, target.verseEnd,
        sourceLang = target.language, sourceEdition = target.editionId,
        requestId = freshInternalReaderRequestId()
      )) { launchSingleTop = true }
    }
    androidx.compose.runtime.CompositionLocalProvider(
      LocalInternalNavigate provides internalNavigate,
      LocalEditionNavigate provides editionNavigate
    ) {
    BoxWithConstraints(
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      val useNavigationRail = maxWidth >= 840.dp
      Row(Modifier.fillMaxSize()) {
      if (useNavigationRail && currentTab != null) {
        AppMainNavigation(selectedRoute = currentTab, onSelect = selectTab, rail = true)
      }
      Scaffold(
        modifier = Modifier.weight(1f),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
          if (!useNavigationRail && currentTab != null) {
            AppMainNavigation(selectedRoute = currentTab, onSelect = selectTab, rail = false)
          }
        }
      ) { rootPadding ->
      NavHost(navController = nav, startDestination = Dest.Home.route,
        modifier = Modifier.padding(rootPadding)) {
        composable(Dest.Home.route) {
          HomeScreen(
            prefs = prefs,
            repo = repo,
            onOpen = { col -> nav.navigate(Dest.Books.route(col)) { launchSingleTop = true } },
            onOpenBook = { col, bookId, storyId, verse, verseEnd ->
              expireSavedReadingNowState()
              nav.navigate(Dest.BookView.route(col, bookId, storyId, verse, verseEnd,
                sourceLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage),
                requestId = if (storyId != null || verse != null) freshInternalReaderRequestId() else null
              )) { launchSingleTop = true }
            },
            onNavigateRoute = { route ->
              if (invalidatesSavedReadingNowState(route)) expireSavedReadingNowState()
              nav.navigate(route) { launchSingleTop = true }
            },
            onContinueReading = continueReading,
            onSettings = { nav.navigate(Dest.Settings.route) { launchSingleTop = true } },
            onBibleChronology = { nav.navigate(Dest.BibleChronology.route) { launchSingleTop = true } },
            onGenealogy = { nav.navigate(Dest.Genealogy.route) { launchSingleTop = true } },
            onJesusDivinity = { nav.navigate(Dest.JesusDivinity.route) { launchSingleTop = true } },
            onJesusIdentity = { nav.navigate(Dest.JesusIdentity.route) { launchSingleTop = true } },
            onGospel = { nav.navigate(Dest.Gospel.route) { launchSingleTop = true } },
            onGrace = { nav.navigate(Dest.Grace.route) { launchSingleTop = true } },
            onChristianSymbolism = { nav.navigate(Dest.ChristianSymbolism.route) { launchSingleTop = true } },
            onUnseenWar = { nav.navigate(Dest.UnseenWar.route) { launchSingleTop = true } },
            onFalseDoctrine = { nav.navigate(Dest.FalseDoctrine.route) { launchSingleTop = true } },
            onCommonDistortions = { nav.navigate(Dest.CommonDistortions.route) { launchSingleTop = true } },
            onChristophanies = { nav.navigate(Dest.Christophanies.route) { launchSingleTop = true } },
            onTranslationNotes = { nav.navigate(Dest.TranslationNotes.route) { launchSingleTop = true } },
            onHistoricalAwareness = { nav.navigate(Dest.HistoricalAwareness.route) { launchSingleTop = true } },
            onBibleCanon = { nav.navigate(Dest.BibleCanon.route) { launchSingleTop = true } },
            onFaqs = { nav.navigate(Dest.FAQs.route) { launchSingleTop = true } },
            onBibliography = { nav.navigate(Dest.Bibliography.route) { launchSingleTop = true } },
            onFeastCalendar = { nav.navigate(Dest.FeastCalendar.route) { launchSingleTop = true } },
            onTorahFeastsAndGentiles = { nav.navigate(Dest.TorahFeastsAndGentiles.route) { launchSingleTop = true } },
            onProphecy = { nav.navigate(Dest.Prophecy.route) { launchSingleTop = true } },
            onAbout = { nav.navigate(Dest.About.route) { launchSingleTop = true } },
            onSavedItems = { nav.navigate(Dest.SavedItems.route) { launchSingleTop = true } },
            requestSearchFocus = pendingSearchFocus
          )
          LaunchedEffect(pendingSearchFocus) {
            if (pendingSearchFocus) {
              delay(500)
              pendingSearchFocus = false
            }
          }
        }
        composable(Dest.Settings.route) {
          SettingsScreen(prefs = prefs, repo = repo) { navBack() }
        }
        composable(Dest.About.route) {
          AboutScreen { navBack() }
        }
        composable(Dest.SavedItems.route) {
          SavedItemsScreen(
            prefs = prefs,
            repo = repo,
            onBack = { navBack() },
            onOpenBook = { col, bookId, storyId ->
              expireSavedReadingNowState()
              nav.navigate(Dest.BookView.route(
                col, bookId, storyId, requestId = freshInternalReaderRequestId()
              )) { launchSingleTop = true }
            },
            onOpenSavedVerse = { saved ->
              expireSavedReadingNowState()
              val sameLanguage = saved.scriptureLanguage() == LocaleUtils.effectiveAssetTag(prefs.appLanguage)
              val anchor = saved.stableAnchor().takeIf { sameLanguage }
              nav.navigate(Dest.BookView.route(
                saved.collection, saved.bookId, saved.storyId,
                verse = anchor?.verseStart, verseEnd = anchor?.verseEnd,
                sourceLang = saved.scriptureLanguage(), sourceEdition = saved.scriptureEdition(),
                requestId = freshInternalReaderRequestId()
              )) { launchSingleTop = true }
            }
          )
        }
        navigation(startDestination = Dest.Study.route, route = "tab_study") {
        composable(Dest.Study.route) {
          StudyScreen(prefs = prefs, onNavigate = { destination ->
            nav.navigate(destination.route) { launchSingleTop = true }
          })
        }
        composable(Dest.AboutCalendars.route) {
          StudyCalendarDetailScreen(StudyCalendarDetail.ABOUT, prefs, onBack = navBack)
        }
        composable(Dest.OrdainedFeasts.route) {
          StudyCalendarDetailScreen(StudyCalendarDetail.APPOINTED, prefs, onBack = navBack)
        }
        composable(Dest.TranslationNotes.route) {
          GenericNotesScreen(Res.string.translation_notes, "translation_notes.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.HistoricalAwareness.route) {
          GenericNotesScreen(Res.string.historical_awareness, "historical_awareness.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.BibleCanon.route) {
          GenericNotesScreen(Res.string.bible_canon, "bible_canon.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.JesusDivinity.route) {
          GenericNotesScreen(Res.string.jesus_divinity, "jesus_divinity.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.JesusIdentity.route) {
          GenericNotesScreen(Res.string.jesus_identity, "jesus_identity.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.Gospel.route) {
          GenericNotesScreen(Res.string.gospel, "gospel.md", prefs, repo) { navBack() }
        }
        composable(Dest.Grace.route) {
          GenericNotesScreen(Res.string.grace, "grace.md", prefs, repo) { navBack() }
        }
        composable(Dest.ChristianSymbolism.route) {
          GenericNotesScreen(Res.string.christian_symbolism, "christian_symbolism.md", prefs, repo) { navBack() }
        }
        composable(Dest.UnseenWar.route) {
          GenericNotesScreen(Res.string.unseen_war, "unseen_war.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.FalseDoctrine.route) {
          GenericNotesScreen(Res.string.false_doctrine, "false_doctrine.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.CommonDistortions.route) {
          GenericNotesScreen(Res.string.common_distortions, "common_distortions.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.Christophanies.route) {
          GenericNotesScreen(Res.string.christophanies, "christophanies.md", prefs, repo) { navBack() }
        }
        composable(Dest.FAQs.route) {
          GenericNotesScreen(
            Res.string.faqs, "faqs.md", prefs, repo,
            collapsible = true, headingPrefix = "### "
          ) { navBack() }
        }
        composable(Dest.Bibliography.route) {
          GenericNotesScreen(Res.string.bibliography, "bibliography.md", prefs, repo, collapsible = true) { navBack() }
        }
        composable(Dest.Genealogy.route) {
          GenealogyScreen(prefs = prefs, onBack = { navBack() })
        }
        composable(Dest.BibleChronology.route) {
          BibleChronologyScreen(
            appLanguage = prefs.appLanguage,
            includeDeuterocanon = prefs.chronologyIncludeDeutero,
            expandedEpochs = prefs.chronologyExpandedEpochs,
            onIncludeDeuterocanonChange = { show ->
              scope.launch { repo.setChronologyIncludeDeutero(show) }
            },
            onExpandedEpochsChange = { value ->
              scope.launch { repo.setChronologyExpandedEpochs(value) }
            },
            onBack = { navBack() },
            onOpenChapterRange = { collection, bookId, openingChapter ->
              val selectedBook = ContentRepo.loadBookOrNull(
                context = ctx,
                collection = collection,
                bookId = bookId,
                appLang = prefs.appLanguage,
                internalBibleVersion = prefs.internalBibleVersion
              )
              val storyId = selectedBook?.let { chronologyOpeningStoryId(it, openingChapter) }
              expireSavedReadingNowState()
              nav.navigate(Dest.BookView.route(
                collection, bookId, storyId, requestId = freshInternalReaderRequestId()
              )) {
                launchSingleTop = true
              }
            }
          )
        }
        composable(Dest.TorahFeastsAndGentiles.route) {
          GenericNotesScreen(
            Res.string.torah_feasts_and_gentiles,
            "torah_feasts_and_gentiles.md",
            prefs,
            repo,
            collapsible = true
          ) { navBack() }
        }
        composable(Dest.Prophecy.route) {
          ProphecyMenuScreen(
            onBack = { navBack() },
            onMessianic = { nav.navigate(Dest.MessianicProphecy.route) { launchSingleTop = true } },
            onDaniel = { nav.navigate(Dest.DanielsTimeline.route) { launchSingleTop = true } },
            onAstronomical = { nav.navigate(Dest.AstronomicalSigns.route) { launchSingleTop = true } },
            onRevelation = { nav.navigate(Dest.RevelationOverview.route) { launchSingleTop = true } },
            onRevelationTimeline = { nav.navigate(Dest.RevelationTimeline.route) { launchSingleTop = true } },
            onSecondComingRapture = { nav.navigate(Dest.SecondComingRapture.route) { launchSingleTop = true } }
          )
        }
        composable(Dest.MessianicProphecy.route) {
          GenericNotesScreen(
            Res.string.prophecy_messianic,
            "messianic_prophecy.md",
            prefs,
            repo,
            collapsible = true
          ) { navBack() }
        }
        composable(Dest.DanielsTimeline.route) {
          GenericNotesScreen(
            Res.string.prophecy_daniel,
            "daniels_timeline.md",
            prefs,
            repo,
            collapsible = true
          ) { navBack() }
        }
        composable(Dest.AstronomicalSigns.route) {
          GenericNotesScreen(Res.string.prophecy_astronomical, "astronomical_signs.md", prefs, repo) { navBack() }
        }
        composable(Dest.RevelationOverview.route) {
          GenericNotesScreen(Res.string.prophecy_revelation, "revelation_overview.md", prefs, repo) { navBack() }
        }
        composable(Dest.RevelationTimeline.route) {
          GenericNotesScreen(
            Res.string.prophecy_revelation_timeline,
            "revelation_timeline.md",
            prefs,
            repo,
            collapsible = true
          ) { navBack() }
        }
        composable(Dest.SecondComingRapture.route) {
          GenericNotesScreen(
            Res.string.prophecy_second_coming_rapture,
            "second_coming_rapture.md",
            prefs,
            repo,
            collapsible = true
          ) { navBack() }
        }
        }
        navigation(startDestination = Dest.FeastCalendar.route, route = "tab_calendar") {
          composable(Dest.FeastCalendar.route) {
            FeastCalendarScreen(prefs = prefs, repo = repo, onBack = { navBack() })
          }
        }
        navigation(startDestination = Dest.Read.route, route = "tab_read") {
        composable(Dest.Read.route) {
          ReadLibraryScreen(prefs = prefs, repo = repo,
            onOpenCollection = { col -> nav.navigate(Dest.Books.route(col)) { launchSingleTop = true } },
            onSavedItems = { nav.navigate(Dest.SavedItems.route) { launchSingleTop = true } },
            onContinue = continueReading)
        }
        composable("books/{col}") { back ->
          val col = back.arguments?.getString("col") ?: "old_testament"
          BooksScreen(
            col = col,
            appLanguage = prefs.appLanguage,
            currentBookId = prefs.lastReadBookId.takeIf { prefs.lastReadCollection == col },
            internalBibleVersion = prefs.internalBibleVersion,
            onBack = { navBack() },
            onOpenBook = { bookId ->
              expireSavedReadingNowState()
              nav.navigate(Dest.BookView.route(col, bookId)) { launchSingleTop = true }
            }
          )
        }
        composable(
          route = "book/{col}/{bookId}?storyId={storyId}&verse={verse}&verseEnd={verseEnd}&autoStartTts={autoStartTts}&sourceLang={sourceLang}&sourceEdition={sourceEdition}&requestId={requestId}",
          arguments = listOf(
            navArgument("storyId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("verse") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("verseEnd") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("autoStartTts") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("sourceLang") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("sourceEdition") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("requestId") { type = NavType.StringType; nullable = true; defaultValue = null }
          )
        ) { back ->
          val col = back.arguments?.getString("col") ?: return@composable
          val bookId = back.arguments?.getString("bookId") ?: return@composable
          val storyIdArg = back.arguments?.getString("storyId")
          val verseArg = back.arguments?.getString("verse")?.toIntOrNull()?.takeIf { it > 0 }
          val verseEndArg = back.arguments?.getString("verseEnd")?.toIntOrNull()
            ?.takeIf { verseArg != null && it >= verseArg }
          val autoStartTtsArg = back.arguments?.getString("autoStartTts") == "true"
          val sourceLangArg = back.arguments?.getString("sourceLang")
          val sourceEditionArg = back.arguments?.getString("sourceEdition")
          val requestIdArg = back.arguments?.getString("requestId")?.toLongOrNull()
          val readerPrefs = prefs.copy(internalBibleVersion = BibleEditions.forNavigation(
            prefs.appLanguage, prefs.internalBibleVersion, sourceLangArg, sourceEditionArg
          ))
          val linkedEditionUnavailable = sourceEditionArg != null &&
            sourceLangArg?.let(LocaleUtils::effectiveAssetTag) == LocaleUtils.effectiveAssetTag(prefs.appLanguage) &&
            BibleEditions.canonicalId(prefs.appLanguage, sourceEditionArg) == null
          val keepLinkedVerse = BibleEditions.canKeepLinkedVerse(
            prefs.appLanguage, sourceLangArg, linkedEditionUnavailable
          )
          val readerNavigate: (String, String, String?, Int?, Int?) -> Unit = { nextCol, nextBook, nextStory, nextVerse, nextEnd ->
            nav.navigate(Dest.BookView.route(
              nextCol, nextBook, nextStory, nextVerse, nextEnd,
              sourceLang = LocaleUtils.effectiveAssetTag(readerPrefs.appLanguage),
              sourceEdition = readerPrefs.internalBibleVersion,
              requestId = freshInternalReaderRequestId()
            )) { launchSingleTop = true }
          }
          // launchSingleTop can replace a book's route arguments in the same
          // entry. Keep scroll, sheet and animation state scoped to that book.
          key(col, bookId) {
          androidx.compose.runtime.CompositionLocalProvider(LocalInternalNavigate provides readerNavigate) {
          BookScreen(
            col = col,
            bookId = bookId,
            prefs = readerPrefs,
            repo = repo,
            initialStoryId = storyIdArg,
            initialSourceLanguage = sourceLangArg,
            initialSourceEdition = sourceEditionArg,
            initialVerse = verseArg.takeIf { keepLinkedVerse },
            initialVerseEnd = verseEndArg.takeIf { keepLinkedVerse },
            initialRequestId = requestIdArg,
            linkedEditionUnavailable = linkedEditionUnavailable,
            autoStartTts = autoStartTtsArg,
            onChooseBook = { nav.navigate(Dest.Books.route(col)) { launchSingleTop = true } },
            onNavigateToBook = { nextCol, nextBookId, startTts ->
              val nextLoadedBook = ContentRepo.loadBookWithEdition(
                context = ctx,
                collection = nextCol,
                bookId = nextBookId,
                appLang = readerPrefs.appLanguage,
                internalBibleVersion = readerPrefs.internalBibleVersion
              )
              nav.navigate(Dest.BookView.route(
                nextCol, nextBookId,
                storyId = nextLoadedBook?.book?.let(::firstReaderChapterId),
                autoStartTts = startTts,
                sourceLang = LocaleUtils.effectiveAssetTag(readerPrefs.appLanguage),
                sourceEdition = nextLoadedBook?.effectiveEdition ?: readerPrefs.internalBibleVersion,
                requestId = freshInternalReaderRequestId()
              )) {
                launchSingleTop = true
              }
            }
          ) { navBack() }
          }
          }
        }
        }
      }
      }
    }
    }
    }
  }
}

// --------------- Onboarding ----------------

@Composable
private fun OnboardingOverlay(onComplete: () -> Unit) {
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { 3 })

  val titles = listOf(
    stringResource(Res.string.onboarding_1_title),
    stringResource(Res.string.onboarding_2_title),
    stringResource(Res.string.onboarding_3_title)
  )
  val bodies = listOf(
    stringResource(Res.string.onboarding_1_text),
    stringResource(Res.string.onboarding_2_text),
    stringResource(Res.string.onboarding_3_text)
  )
  val icons = listOf(
    Icons.Filled.Search,
    Icons.AutoMirrored.Filled.MenuBook,
    Icons.Filled.Settings
  )

  Surface(
    Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
  ) {
    Column(
      Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f)
      ) { page ->
        Column(
          Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = icons[page],
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(Modifier.height(24.dp))
          Text(
            titles[page],
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(12.dp))
          Text(
            bodies[page],
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Page indicators
      Row(
        Modifier.padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        repeat(3) { idx ->
          Box(
            Modifier
              .size(if (pagerState.currentPage == idx) 10.dp else 8.dp)
              .clip(CircleShape)
              .background(
                if (pagerState.currentPage == idx) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
              )
          )
        }
      }

      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        TextButton(onClick = onComplete) {
          Text(stringResource(Res.string.skip))
        }
        if (pagerState.currentPage < 2) {
          Button(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
            Text(stringResource(Res.string.next_button))
          }
        } else {
          Button(onClick = onComplete) {
            Text(stringResource(Res.string.get_started))
          }
        }
      }
    }
  }
}

// --------------- Screens ----------------

@Composable
private fun HomeWideButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
  Button(onClick = onClick, enabled = enabled, modifier = modifier.height(56.dp), shape = RoundedCornerShape(24.dp)) {
    AutoSizeOneLineText(text = text, maxFontSizeSp = 16f, minFontSizeSp = 11f)
  }
}

@Composable
private fun HomePill(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
  FilledTonalButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(44.dp),
    shape = RoundedCornerShape(28.dp),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
  ) { AutoSizeOneLineText(text = text, maxFontSizeSp = 14f, minFontSizeSp = 11f) }
}

@Composable
private fun AutoSizeOneLineText(
  modifier: Modifier = Modifier,
  text: String,
  maxFontSizeSp: Float,
  minFontSizeSp: Float = 11f,
  stepSp: Float = 0.5f,
  baseStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge
) {
  var size by remember(text, maxFontSizeSp) { mutableFloatStateOf(maxFontSizeSp) }
  Text(
    text = text,
    maxLines = 1,
    softWrap = false,
    overflow = TextOverflow.Clip,
    textAlign = TextAlign.Center,
    style = baseStyle.copy(fontSize = size.sp),
    modifier = modifier.fillMaxWidth(),
    onTextLayout = { result ->
      if (result.didOverflowWidth && size > minFontSizeSp) {
        size = (size - stepSp).coerceAtLeast(minFontSizeSp)
      }
    }
  )
}

@OptIn(
  ExperimentalMaterial3Api::class,
  androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun HomeScreen(
  prefs: PrefsState,
  repo: PrefsRepo,
  onOpen: (String) -> Unit,
  onOpenBook: (String, String, String?, Int?, Int?) -> Unit,
  onNavigateRoute: (String) -> Unit,
  onContinueReading: () -> Unit,
  onSettings: () -> Unit,
  onBibleChronology: () -> Unit,
  onGenealogy: () -> Unit,
  onJesusDivinity: () -> Unit,
  onJesusIdentity: () -> Unit,
  onGospel: () -> Unit,
  onGrace: () -> Unit,
  onChristianSymbolism: () -> Unit,
  onUnseenWar: () -> Unit,
  onFalseDoctrine: () -> Unit,
  onCommonDistortions: () -> Unit,
  onChristophanies: () -> Unit,
  onTranslationNotes: () -> Unit,
  onHistoricalAwareness: () -> Unit,
  onBibleCanon: () -> Unit,
  onFaqs: () -> Unit,
  onBibliography: () -> Unit,
  onFeastCalendar: () -> Unit,
  onTorahFeastsAndGentiles: () -> Unit,
  onProphecy: () -> Unit,
  onAbout: () -> Unit,
  onSavedItems: () -> Unit = {},
  requestSearchFocus: Boolean = false
) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()
  val searchFocusRequester = remember { FocusRequester() }

  // Onboarding
  var showOnboarding by remember { mutableStateOf(!prefs.onboardingComplete) }

  var navBusy by remember { mutableStateOf(false) }
  var studyExpanded by rememberSaveable(prefs.studyPinned) { mutableStateOf(prefs.studyPinned) }
  fun safeNav(action: () -> Unit) {
    if (navBusy) return
    navBusy = true
    action()
    scope.launch { delay(350); navBusy = false }
  }

  var query by remember { mutableStateOf("") }
  var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
  var showSheet by remember { mutableStateOf(false) }
  var searchJob by remember { mutableStateOf<Job?>(null) }
  var searchInFlight by remember { mutableStateOf(false) }
  var searchGen by remember { mutableStateOf(0) }
  var indexReady by remember { mutableStateOf(true) }
  val embLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)

  LaunchedEffect(requestSearchFocus) {
    if (requestSearchFocus) {
      delay(120)
      runCatching { searchFocusRequester.requestFocus() }
    }
  }

  // Prewarm search indexes/ONNX in the background when the user signals intent
  // to search (focuses the field). This trades a tiny extra preload for hiding
  // the 2-3 second freeze observed when the ONNX session initializes mid-typing.
  // If the user never focuses search, we never load.
  var searchPrewarmed by remember { mutableStateOf(false) }
  fun prewarmSearch() {
    if (searchPrewarmed) return
    searchPrewarmed = true
    scope.launch(Dispatchers.Default) {
      runCatching { StorySearch.ensureBuilt(ctx, prefs.appLanguage, prefs.internalBibleVersion) }
      if (prefs.aiSearch) {
        runCatching { platformOnnxInit(ctx) }
        runCatching { EmbeddingSearch.ensureBuilt(ctx, embLang) }
      }
    }
  }
  // If the language preference changes, allow re-prewarm so the new locale's
  // index loads next time the user touches the search field.
  LaunchedEffect(prefs.appLanguage, prefs.internalBibleVersion) {
    searchPrewarmed = false
  }

  Box(Modifier.fillMaxSize()) {
    Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
          title = { Text(stringResource(Res.string.app_name)) },
          actions = {
            IconButton(onClick = { safeNav { onSettings() } }, enabled = !navBusy) {
              Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.settings))
            }
          }
        )
      }
    ) { pad ->
      // State hoisted above LazyColumn so it isn't recreated when items
      // scroll in/out of viewport. Identical lifetime to the previous
      // Column-wrapped declarations.
      val votd = remember(prefs.appLanguage, prefs.internalBibleVersion) {
        VerseOfTheDay.todayVerse(ctx, prefs.appLanguage, prefs.internalBibleVersion)
      }
      val votdLocalRef = remember(votd.ref) { ScriptureRefs.localizeRef(votd.ref) }
      val votdCollection = ScriptureRefs.collectionOf(ScriptureRefs.canonBookOfRef(votd.ref))
        ?: "old_testament"
      var ttsPlaying by remember { mutableStateOf(false) }
      DisposableEffect(Unit) {
        platformTtsInit(ctx)
        platformTtsSetOnDone { ttsPlaying = false }
        onDispose { platformTtsSetOnDone(null) }
      }
      LaunchedEffect(ttsPlaying) {
        if (ttsPlaying) {
          // Wait for TTS engine to start speaking (init can be slow)
          var started = false
          for (i in 0..39) {
            delay(250)
            if (!ttsPlaying) return@LaunchedEffect
            if (platformTtsIsSpeaking(ctx)) { started = true; break }
          }
          if (started) {
            while (platformTtsIsSpeaking(ctx)) { delay(500) }
          }
          ttsPlaying = false
        }
      }
      // VOTD dismissal is persisted per local calendar day. We compare the
      // saved YYYY-MM-DD to today's local date; at midnight the card reappears
      // naturally without any special scheduling. Matches YouVersion/M3 banner
      // convention (see project research notes).
      val todayDate = remember(prefs.appLanguage) {
        val (y, m, d) = platformCurrentDate()
        val mm = m.toString().padStart(2, '0')
        val dd = d.toString().padStart(2, '0')
        "$y-$mm-$dd"
      }
      val votdDismissed = prefs.votdDismissedDate == todayDate
      val lastCol = prefs.lastReadCollection
      val lastBook = prefs.lastReadBookId
      val readingResume = rememberReadingResume(prefs)
      val bookmarks by repo.bookmarksFlow.collectAsState(initial = emptyList())
      val savedVerses by repo.savedVersesFlow.collectAsState(initial = emptyList())

      // LazyColumn defers off-screen item composition. The Study & Reference
      // section retains its existing expand/collapse + pin behavior — only
      // the outer scroll container changes.
      LazyColumn(
        Modifier
          .padding(pad)
          .fillMaxSize()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item("search") {
        // Search
        OutlinedTextField(
          value = query,
          onValueChange = { q ->
            query = q
            showSheet = q.length >= 2
            searchJob?.cancel()
            if (q.length >= 2) {
              searchInFlight = true
              val gen = ++searchGen
              searchJob = scope.launch {
                // Per-query timing breakdown for logcat makes slow stages visible.
                val tStart = currentTimeMillis()
                var kwMs = 0L
                var onnxMs = 0L
                var encodeMs = 0L
                var mergeMs = 0L
                var cacheHit = false
                var hadSemantic = false
                var isRefFlag = false
                try {
                  // Show keyword results quickly after a short debounce, then
                  // hold longer before doing the expensive semantic encode.
                  // ONNX firing mid-typing caused blocking-GC stalls;
                  // running semantic search idle-only
                  // keeps the keyword path snappy and the semantic merge
                  // only kicks in when the user pauses.
                  delay(220)
                  if (!StorySearch.isReady(prefs.appLanguage, prefs.internalBibleVersion)) {
                    indexReady = false
                    withContext(Dispatchers.Default) {
                      runCatching {
                        StorySearch.ensureBuilt(ctx, prefs.appLanguage, prefs.internalBibleVersion)
                      }
                    }
                    indexReady = StorySearch.isReady(prefs.appLanguage, prefs.internalBibleVersion)
                  }
                  val tKw0 = currentTimeMillis()
                  val kwHits = withContext(Dispatchers.Default) {
                    StorySearch.search(q)
                  }
                  kwMs = currentTimeMillis() - tKw0
                  if (gen == searchGen) results = kwHits

                  // Gate semantic search: requires AI enabled, idle pause,
                  // and a substantive query. Skip when:
                  //   - query is < 3 chars (too few semantic signals)
                  //   - query parses as an explicit Bible reference (e.g.,
                  //     "John 3:16", "1 Cor 13") — the keyword path already
                  //     produces the canonical hit; semantic adds no signal.
                  // Strong keyword hits without an explicit reference still
                  // get the semantic merge so related-passage discovery works.
                  val tooShort = q.trim().length < 3
                  isRefFlag = runCatching { StorySearch.isExplicitReference(q) }.getOrDefault(false)
                  // Packaged semantic indexes were built for each language's
                  // default corpus. Alternate editions use their freshly rebuilt
                  // lexical index; merging default-edition vectors would return
                  // mismatched snippets and rankings.
                  val alternateEditionSelected = BibleEditions.isAlternate(
                    prefs.appLanguage, prefs.internalBibleVersion
                  )
                  val skipSemantic = !prefs.aiSearch || tooShort || isRefFlag || alternateEditionSelected
                  if (!skipSemantic) {
                    hadSemantic = true
                    // Idle gate: wait additional time after keyword shows.
                    // If user types again, gen advances and we exit early.
                    delay(450)
                    if (gen != searchGen) return@launch
                    if (!platformOnnxIsReady()) {
                      val tOnnx0 = currentTimeMillis()
                      withContext(Dispatchers.Default) { runCatching { platformOnnxInit(ctx) } }
                      onnxMs = currentTimeMillis() - tOnnx0
                    }
                    if (gen != searchGen) return@launch
                    if (!EmbeddingSearch.isReady(embLang)) {
                      withContext(Dispatchers.Default) { runCatching { EmbeddingSearch.ensureBuilt(ctx, embLang) } }
                    }
                    if (gen == searchGen && EmbeddingSearch.isReady(embLang)) {
                      val semQuery = if (embLang == "en") StorySearch.correctQuery(q) else q
                      val cached = SemanticCache.get(embLang, semQuery)
                      val semHits = if (cached != null) {
                        cacheHit = true
                        cached
                      } else {
                        val tEnc0 = currentTimeMillis()
                        val r = withContext(Dispatchers.Default) {
                          runCatching { EmbeddingSearch.encodeAndSearch(semQuery, embLang) }.getOrNull()
                        }
                        encodeMs = currentTimeMillis() - tEnc0
                        if (r != null) SemanticCache.put(embLang, semQuery, r)
                        r
                      }
                      if (gen == searchGen) {
                        val tMerge0 = currentTimeMillis()
                        results = EmbeddingSearch.merge(kwHits, semHits)
                        mergeMs = currentTimeMillis() - tMerge0
                      }
                    }
                  }
                } finally {
                  if (gen == searchGen) {
                    searchInFlight = false
                    val totalMs = currentTimeMillis() - tStart
                    val qSafe = q.take(60).replace('"', '\'')
                    println(
                      "SEARCH q=\"$qSafe\" lang=$embLang ai=${prefs.aiSearch} " +
                      "ref=$isRefFlag sem=$hadSemantic kw_ms=$kwMs " +
                      "onnx_ms=$onnxMs enc_ms=$encodeMs merge_ms=$mergeMs " +
                      "total_ms=$totalMs cache=${if (cacheHit) "hit" else "miss"} " +
                      "results=${results.size}"
                    )
                  }
                }
              }
            } else {
              searchInFlight = false
              searchGen++
              results = emptyList()
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(searchFocusRequester)
            .onFocusChanged { if (it.isFocused) prewarmSearch() },
          placeholder = { Text(stringResource(Res.string.search_placeholder)) },
          singleLine = true,
          shape = RoundedCornerShape(28.dp),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color.Transparent
          ),
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = {
                searchJob?.cancel()
                searchInFlight = false
                query = ""
                results = emptyList()
                showSheet = false
              }) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        )
        }

        // Search results with highlighted keywords
        if (showSheet && results.isEmpty()) {
          item("search-empty") {
          Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            if (searchInFlight || !indexReady) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(
                  stringResource(Res.string.search_searching),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            } else {
              Text(
                stringResource(Res.string.search_no_results).replace("%1\$s", query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
              )
            }
          }
          }
        }
        if (showSheet && results.isNotEmpty()) {
          item("search-results") {
          Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
              results.forEach { hit ->
                ListItem(
                  headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(hit.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                      if (hit.semantic) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                          Icons.Filled.AutoAwesome,
                          contentDescription = null,
                          modifier = Modifier.size(14.dp),
                          tint = MaterialTheme.colorScheme.tertiary
                        )
                      }
                    }
                  },
                  supportingContent = {
                    when (hit.type) {
                      SearchHitType.BOOK -> Text(hit.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis)
                      else -> Text(
                        highlightSearchSnippet(hit.snippet, query, prefs, hit.collection),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  },
                  modifier = Modifier.clickable(enabled = !navBusy) {
                    safeNav {
                      when (hit.type) {
                        SearchHitType.BOOK -> onOpenBook(hit.collection, hit.bookId, null, null, null)
                        SearchHitType.NOTE -> onNavigateRoute(hit.bookId)
                        SearchHitType.STORY -> onOpenBook(hit.collection, hit.bookId, hit.storyId, hit.verse, hit.verseEnd)
                      }
                    }
                    showSheet = false
                  }
                )
                HorizontalDivider()
              }
            }
          }
          }
        }

        // Verse of the Day — state hoisted above LazyColumn (see top of body)
        if (!votdDismissed) {
          item("votd") {
        // Dynamic (Material You) dark palettes give tertiaryContainer a vivid
        // accent that makes the persistent VOTD card strain the eye. Under the
        // Dynamic preset, back it with the calmer secondaryContainer so it sits
        // in the same family as the other home cards. Hand-tuned presets keep
        // their intended tertiary accent.
        val votdDynamic = ThemePreset.fromKey(prefs.themePreset) == ThemePreset.Dynamic
        val votdContainer = if (votdDynamic) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer
        val votdOnContainer = if (votdDynamic) MaterialTheme.colorScheme.onSecondaryContainer
                              else MaterialTheme.colorScheme.onTertiaryContainer
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = votdContainer
          )
        ) {
          Column(Modifier.padding(16.dp)) {
            Row(
              Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                if (votd.isFeastOverride) stringResource(Res.string.feast_verse_label)
                else stringResource(Res.string.verse_of_the_day),
                style = MaterialTheme.typography.labelMedium,
                color = votdOnContainer.copy(alpha = 0.7f)
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = {
                    platformShareText(
                      ctx,
                      votdLocalRef,
                      "${wrapVotdQuotes(stripScriptureInlineTags(votd.text))}\n\u2014 $votdLocalRef"
                    )
                  }
                ) {
                  Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(Res.string.share),
                    tint = votdOnContainer,
                    modifier = Modifier.size(18.dp)
                  )
                }
                IconButton(
                  onClick = {
                    if (ttsPlaying) {
                      platformTtsStop(ctx)
                      ttsPlaying = false
                    } else {
                      val lang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
                      platformTtsSpeak(ctx, "${stripScriptureInlineTags(votd.text)} $votdLocalRef", lang)
                      ttsPlaying = true
                    }
                  },
                ) {
                  Icon(
                    if (ttsPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (ttsPlaying) stringResource(Res.string.cd_tts_stop) else stringResource(Res.string.cd_tts_play),
                    tint = votdOnContainer,
                    modifier = Modifier.size(18.dp)
                  )
                }
                IconButton(
                  onClick = {
                    if (ttsPlaying) { platformTtsStop(ctx); ttsPlaying = false }
                    scope.launch { repo.setVotdDismissedDate(todayDate) }
                  },
                ) {
                  Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.votd_dismiss_today),
                    tint = votdOnContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
            Spacer(Modifier.height(4.dp))
            Text(
              highlightSearchSnippet(wrapVotdQuotes(votd.text), "", prefs, votdCollection),
              style = MaterialTheme.typography.bodyMedium,
              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
              color = votdOnContainer
            )
            ScriptureRefs.ClickableRefsTextSmart(
              text = "\u2014 $votdLocalRef",
              prefs = prefs.copy(internalBibleVersion = votd.editionId ?: BibleEditions.defaultForLanguage(prefs.appLanguage)),
              referenceEditionId = votd.editionId,
              modifier = Modifier.padding(top = 4.dp),
              textStyle = MaterialTheme.typography.labelSmall.copy(
                color = votdOnContainer.copy(alpha = 0.7f)
              ),
              linkColor = votdOnContainer
            )
            if (votd.editionId != null &&
              votd.editionId != BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)) {
              Text(
                text = when (votd.editionId) {
                  BibleEditions.BSB -> stringResource(Res.string.version_bsb)
                  BibleEditions.KJV_1769 -> stringResource(Res.string.version_kjv)
                  BibleEditions.defaultForLanguage(prefs.appLanguage) -> stringResource(Res.string.version_local_modern)
                  else -> stringResource(Res.string.version_local_traditional)
                },
                style = MaterialTheme.typography.labelSmall,
                color = votdOnContainer.copy(alpha = 0.7f)
              )
            }
          }
        }
          }
        }

        // Continue Reading card (lastCol/lastBook hoisted above LazyColumn)
        if (lastBook != null && lastCol != null) {
          item("continue") {
          ReadingNowCard(lastBook = lastBook, resume = readingResume) {
            if (!navBusy) safeNav {
              onContinueReading()
            }
          }
          }
        }

        // Bookmarks & Saved Verses (bookmarks/savedVerses hoisted above LazyColumn)
        item("saved") {
          SavedItemsCard(bookmarkCount = bookmarks.size, savedVerseCount = savedVerses.size) {
            if (!navBusy) safeNav { onSavedItems() }
          }
        }

        item("collections") {
          CollectionButtons(prefs = prefs, enabled = !navBusy, onOpenCollection = { collection ->
            safeNav { onOpen(collection) }
          })
        }

        item("divider") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }

        // Study & Reference uses the same horizontal destination cards as Study.
        item("study") {
        Surface(
          shape = RoundedCornerShape(16.dp),
          tonalElevation = 1.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column {
            Row(
              Modifier
                .fillMaxWidth()
                .clickable { studyExpanded = !studyExpanded }
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                stringResource(Res.string.study_reference),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
              )
              IconButton(onClick = {
                val next = !prefs.studyPinned
                if (next) studyExpanded = true
                scope.launch { repo.setStudyPinned(next) }
              }) {
                Icon(
                  Icons.Filled.PushPin,
                  contentDescription = stringResource(if (prefs.studyPinned) Res.string.study_unpin else Res.string.study_pin),
                  tint = if (prefs.studyPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
              }
              Icon(
                if (studyExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
              )
            }
            AnimatedVisibility(visible = studyExpanded) {
              Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                HomeStudyGroup(
                  title = stringResource(Res.string.ui_foundations),
                  enabled = !navBusy,
                  entries = listOf(
                    HomeStudyTile(stringResource(Res.string.gospel), Icons.Filled.AutoAwesome, StudyIconTone.Primary) { safeNav { onGospel() } },
                    HomeStudyTile(stringResource(Res.string.grace), Icons.Filled.Bookmark, StudyIconTone.Secondary) { safeNav { onGrace() } },
                    HomeStudyTile(stringResource(Res.string.jesus_divinity), Icons.Filled.Lightbulb, StudyIconTone.Tertiary) { safeNav { onJesusDivinity() } },
                    HomeStudyTile(stringResource(Res.string.jesus_identity), Icons.Filled.Person, StudyIconTone.Primary) { safeNav { onJesusIdentity() } },
                    HomeStudyTile(stringResource(Res.string.christophanies), Icons.Filled.AutoAwesome, StudyIconTone.Tertiary) { safeNav { onChristophanies() } },
                    HomeStudyTile(stringResource(Res.string.genealogy), Icons.Filled.FamilyRestroom, StudyIconTone.Secondary) { safeNav { onGenealogy() } }
                  )
                )
                HomeStudyGroup(
                  title = stringResource(Res.string.ui_torah_feasts),
                  enabled = !navBusy,
                  entries = listOf(
                    HomeStudyTile(stringResource(Res.string.feast_calendar), Icons.Filled.CalendarMonth, StudyIconTone.Tertiary) { safeNav { onFeastCalendar() } },
                    HomeStudyTile(stringResource(Res.string.feast_about_heading), Icons.Filled.Info, StudyIconTone.Secondary) { safeNav { onNavigateRoute(Dest.AboutCalendars.route) } },
                    HomeStudyTile(stringResource(Res.string.ordained_feasts_heading), Icons.Filled.EventNote, StudyIconTone.Primary) { safeNav { onNavigateRoute(Dest.OrdainedFeasts.route) } },
                    HomeStudyTile(stringResource(Res.string.torah_feasts_and_gentiles), Icons.AutoMirrored.Filled.MenuBook, StudyIconTone.Primary) { safeNav { onTorahFeastsAndGentiles() } }
                  )
                )
                HomeStudyGroup(
                  title = stringResource(Res.string.prophecy),
                  enabled = !navBusy,
                  entries = listOf(
                    HomeStudyTile(stringResource(Res.string.prophecy), Icons.Filled.Timeline, StudyIconTone.Secondary) { safeNav { onProphecy() } }
                  )
                )
                HomeStudyGroup(
                  title = stringResource(Res.string.ui_discernment),
                  enabled = !navBusy,
                  entries = listOf(
                    HomeStudyTile(stringResource(Res.string.false_doctrine), Icons.Filled.Warning, StudyIconTone.Primary) { safeNav { onFalseDoctrine() } },
                    HomeStudyTile(stringResource(Res.string.common_distortions), Icons.Filled.Gavel, StudyIconTone.Tertiary) { safeNav { onCommonDistortions() } },
                    HomeStudyTile(stringResource(Res.string.unseen_war), Icons.Filled.Shield, StudyIconTone.Secondary) { safeNav { onUnseenWar() } },
                    HomeStudyTile(stringResource(Res.string.historical_awareness), Icons.Filled.HistoryEdu, StudyIconTone.Primary) { safeNav { onHistoricalAwareness() } },
                    HomeStudyTile(stringResource(Res.string.christian_symbolism), Icons.Filled.AutoAwesome, StudyIconTone.Tertiary) { safeNav { onChristianSymbolism() } }
                  )
                )
                HomeStudyGroup(
                  title = stringResource(Res.string.ui_reference),
                  enabled = !navBusy,
                  entries = listOf(
                    HomeStudyTile(stringResource(Res.string.bible_chronology), Icons.Filled.Timeline, StudyIconTone.Primary) { safeNav { onBibleChronology() } },
                    HomeStudyTile(stringResource(Res.string.translation_notes), Icons.Filled.Translate, StudyIconTone.Secondary) { safeNav { onTranslationNotes() } },
                    HomeStudyTile(stringResource(Res.string.bible_canon), Icons.AutoMirrored.Filled.MenuBook, StudyIconTone.Tertiary) { safeNav { onBibleCanon() } },
                    HomeStudyTile(stringResource(Res.string.bibliography), Icons.Filled.Bookmark, StudyIconTone.Primary) { safeNav { onBibliography() } },
                    HomeStudyTile(stringResource(Res.string.faqs), Icons.Filled.QuestionAnswer, StudyIconTone.Secondary) { safeNav { onFaqs() } }
                  )
                )
              }
            }
          }
        }
        }

      }
    }

    // Onboarding overlay
    if (showOnboarding) {
      OnboardingOverlay(
        onComplete = {
          showOnboarding = false
          scope.launch { repo.setOnboardingComplete(true) }
        }
      )
    }
  }
}

@Composable
private fun SearchSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
  Row(
    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
  }
}

private data class HomeStudyTile(
  val text: String,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val tone: StudyIconTone,
  val onClick: () -> Unit
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HomeStudyGroup(
  title: String,
  enabled: Boolean,
  entries: List<HomeStudyTile>
) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.tertiary,
    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
  )
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    entries.forEach { entry ->
      StudyItem(
        text = entry.text,
        icon = entry.icon,
        tone = entry.tone,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        onClick = entry.onClick
      )
    }
  }
}

@Composable
private fun StudyItem(
  text: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tone: StudyIconTone,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Surface(
    modifier = modifier
      .heightIn(min = 64.dp)
      .clickable(enabled = enabled, onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val (containerColor, contentColor) = studyIconColors(tone)
      Box(
        Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(containerColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          icon,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = contentColor
        )
      }
      Spacer(Modifier.width(12.dp))
      Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
      Icon(
        Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/** Annotated string that bolds query matches and preserves Jesus/divine-name colors in search snippets. */
@Composable
private fun highlightSearchSnippet(
  snippet: String,
  query: String,
  prefs: PrefsState? = null,
  collection: String = "old_testament"
): AnnotatedString {
  val jesusColor = prefs?.let { ScriptureRefs.jesusColor(it) }
  val dnColor = prefs?.let { ScriptureRefs.divineNameColor(it) }
  val prepared = prefs?.let {
    applyDivineName(
      snippet,
      it.divineName,
      LocaleUtils.effectiveAssetTag(it.appLanguage),
      it.divineNameColor != "default",
      collection
    )
  } ?: snippet

  return buildAnnotatedString {
    // Strip semantic color markers while recording spans in cleaned-text coordinates.
    val jRanges = mutableListOf<IntRange>()
    val dnRanges = mutableListOf<IntRange>()
    val addRanges = mutableListOf<IntRange>()
    val step1 = prepared.replace("[[", "").replace("]]", "")
    val buf = StringBuilder(step1.length)
    var si = 0
    var jStart = -1
    var dnStart = -1
    var addStart = -1

    fun markerAt(marker: String): Boolean =
      step1.regionMatches(si, marker, 0, marker.length, ignoreCase = true)

    fun closeRange(start: Int, ranges: MutableList<IntRange>): Int {
      if (start >= 0 && buf.length > start) ranges += start until buf.length
      return -1
    }

    while (si < step1.length) {
      when {
        markerAt("[J]") -> { if (jStart < 0) jStart = buf.length; si += 3 }
        markerAt("[/J]") -> { jStart = closeRange(jStart, jRanges); si += 4 }
        markerAt("[DN]") -> { if (dnStart < 0) dnStart = buf.length; si += 4 }
        markerAt("[/DN]") -> { dnStart = closeRange(dnStart, dnRanges); si += 5 }
        markerAt("[ADD]") -> { if (addStart < 0) addStart = buf.length; si += 5 }
        markerAt("[/ADD]") -> { addStart = closeRange(addStart, addRanges); si += 6 }
        else -> { buf.append(step1[si]); si++ }
      }
    }
    closeRange(jStart, jRanges)
    closeRange(dnStart, dnRanges)
    closeRange(addStart, addRanges)
    val cleaned = buf.toString()

    append(cleaned)

    // Layer 1: translator-supplied words. Layer 2: Jesus words. Layer 3:
    // divine names, which must win if semantic spans overlap.
    for (r in addRanges) {
      addStyle(
        SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        r.first,
        r.last + 1
      )
    }
    if (jesusColor != null) {
      for (r in jRanges) addStyle(SpanStyle(color = jesusColor), r.first, r.last + 1)
    }
    if (dnColor != null) {
      for (r in dnRanges) addStyle(SpanStyle(color = dnColor), r.first, r.last + 1)
    }

    // Final layer: keyword highlights preserve semantic color while adding weight.
    val lcCleaned = cleaned.lowercase()
    val lcQuery = query.lowercase().trim()
    if (lcQuery.length >= 2) {
      val tokens = lcQuery.split(Regex("\\s+")).filter { it.length >= 2 }
      val raw = mutableListOf<IntRange>()
      for (tok in tokens) {
        var idx = lcCleaned.indexOf(tok)
        while (idx >= 0) { raw += idx until (idx + tok.length); idx = lcCleaned.indexOf(tok, idx + 1) }
      }
      raw.sortBy { it.first }
      val merged = mutableListOf<IntRange>()
      for (r in raw) {
        if (merged.isEmpty() || r.first > merged.last().last + 1) merged += r
        else merged[merged.lastIndex] = merged.last().first..maxOf(merged.last().last, r.last)
      }
      for (r in merged) {
        val inJ = jesusColor != null && jRanges.any { it.first <= r.first && r.last <= it.last }
        val inDn = dnColor != null && dnRanges.any { it.first <= r.first && r.last <= it.last }
        val color = when {
          inDn -> dnColor!!
          inJ -> jesusColor!!
          else -> MaterialTheme.colorScheme.primary
        }
        addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), r.first, r.last + 1)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BooksScreen(
  col: String,
  appLanguage: String,
  currentBookId: String? = null,
  internalBibleVersion: String = BibleEditions.BSB,
  onBack: () -> Unit,
  onOpenBook: (String) -> Unit
) {
  val ctx = LocalPlatformContext.current

  val (regularList, gnosticList) = if (col == "apocrypha") {
    remember(col, appLanguage) { ContentRepo.listApocryphaSectionsLocalized(ctx, appLanguage) }
  } else {
    remember(col, appLanguage) { ContentRepo.listBooksLocalized(ctx, col, appLanguage) } to emptyList()
  }
  val sections = remember(col, regularList) { buildLibrarySections(col, regularList) }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            when (col) {
              "old_testament" -> stringResource(Res.string.old_testament)
              "new_testament" -> stringResource(Res.string.new_testament)
              "deuterocanonical" -> stringResource(Res.string.deuterocanonical)
              "apocrypha" -> stringResource(Res.string.apocrypha)
              "pseudepigrapha" -> stringResource(Res.string.pseudepigrapha)
              else -> stringResource(Res.string.books_heading_generic)
            }
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
          }
        }
      )
    }
  ) { pad ->
    if (regularList.isEmpty() && gnosticList.isEmpty()) {
      // Friendly empty state
      Column(
        Modifier.padding(pad).fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          Icons.AutoMirrored.Filled.MenuBook,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
          stringResource(Res.string.no_books_found),
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
        val columns = if (maxWidth >= 720.dp) 2 else 1
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 24.dp)
        ) {
          sections.forEachIndexed { sectionIndex, section ->
            section.suppliedHeading?.takeIf { it.isNotBlank() }?.let { heading ->
              item("supplied-$sectionIndex") {
                Text(
                  heading,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                )
              }
            }
            section.section?.let { sectionType ->
              stickyHeader("section-${sectionType.name}") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                  Text(
                    librarySectionTitle(sectionType),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                  )
                }
              }
            }
            items(
              items = section.books.chunked(columns),
              key = { row -> row.joinToString("|") { it.id } }
            ) { rowBooks ->
              Row(Modifier.fillMaxWidth()) {
                rowBooks.forEach { book ->
                  LibraryBookRow(
                    collection = col,
                    book = book,
                    appLanguage = appLanguage,
                    internalBibleVersion = internalBibleVersion,
                    isCurrent = book.id == currentBookId,
                    onOpenBook = onOpenBook,
                    modifier = Modifier.weight(1f)
                  )
                }
                repeat(columns - rowBooks.size) { Spacer(Modifier.weight(1f)) }
              }
              HorizontalDivider()
            }
          }

          if (gnosticList.isNotEmpty()) {
            stickyHeader("gnostic") {
              Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                  stringResource(Res.string.gnostic_heading),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                )
              }
            }
            items(
              items = gnosticList.filter { it.first.isNotBlank() }.chunked(columns),
              key = { row -> row.joinToString("|") { it.first } }
            ) { rowBooks ->
              Row(Modifier.fillMaxWidth()) {
                rowBooks.forEach { (id, title) ->
                  LibraryBookRow(
                    collection = col,
                    book = LibraryBookEntry(id, title),
                    appLanguage = appLanguage,
                    internalBibleVersion = internalBibleVersion,
                    isCurrent = id == currentBookId,
                    onOpenBook = onOpenBook,
                    modifier = Modifier.weight(1f)
                  )
                }
                repeat(columns - rowBooks.size) { Spacer(Modifier.weight(1f)) }
              }
              HorizontalDivider()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun librarySectionTitle(section: LibrarySection): String = when (section) {
  LibrarySection.TORAH -> stringResource(Res.string.ui_library_torah)
  LibrarySection.HISTORICAL_BOOKS -> stringResource(Res.string.ui_library_historical_books)
  LibrarySection.WISDOM_AND_POETRY -> stringResource(Res.string.ui_library_wisdom_poetry)
  LibrarySection.PROPHETS -> stringResource(Res.string.ui_library_prophets)
  LibrarySection.GOSPELS -> stringResource(Res.string.ui_library_gospels)
  LibrarySection.CHURCH_HISTORY -> stringResource(Res.string.ui_library_church_history)
  LibrarySection.LETTERS -> stringResource(Res.string.ui_library_letters)
  LibrarySection.PAULINE_EPISTLES -> stringResource(Res.string.ui_library_pauline_epistles)
  LibrarySection.GENERAL_EPISTLES -> stringResource(Res.string.ui_library_general_epistles)
  LibrarySection.REVELATION -> stringResource(Res.string.ui_library_revelation)
}

@Composable
private fun LibraryBookRow(
  collection: String,
  book: LibraryBookEntry,
  appLanguage: String,
  internalBibleVersion: String,
  isCurrent: Boolean,
  onOpenBook: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val ctx = LocalPlatformContext.current
  val chapterCount = remember(collection, book.id, appLanguage, internalBibleVersion) {
    ContentRepo.loadBookOrNull(
      context = ctx,
      collection = collection,
      bookId = book.id,
      appLang = appLanguage,
      internalBibleVersion = internalBibleVersion
    )?.let(ChapterLocator::build)?.byChapter?.size
  }
  ListItem(
    headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    supportingContent = {
      if (chapterCount != null) {
        Text(
          stringResource(Res.string.chronology_chapters, chapterCount.toString()),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    trailingContent = if (isCurrent) {
      {
        Text(
          stringResource(Res.string.ui_reading_now),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary
        )
      }
    } else null,
    modifier = modifier.clickable { onOpenBook(book.id) }
  )
}

private data class ReaderViewportLayoutSnapshot(
  val epoch: Int,
  val measurements: Map<String, ReaderViewportMeasurement>,
  val firstVisibleItemIndex: Int,
  val firstVisibleItemScrollOffset: Int,
  val scrolling: Boolean,
  val restoringRequestId: Int?,
  val restorePending: Boolean,
  val viewportTopY: Float,
  val viewportHeightPx: Int
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BookScreen(
  col: String,
  bookId: String,
  prefs: PrefsState,
  repo: PrefsRepo,
  initialStoryId: String?,
  initialSourceLanguage: String? = null,
  initialSourceEdition: String? = null,
  initialVerse: Int? = null,
  initialVerseEnd: Int? = null,
  initialRequestId: Long? = null,
  linkedEditionUnavailable: Boolean = false,
  autoStartTts: Boolean = false,
  onChooseBook: () -> Unit = {},
  onNavigateToBook: ((col: String, bookId: String, autoStartTts: Boolean) -> Unit)? = null,
  onBack: () -> Unit
) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  val doHaptic = { if (prefs.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

  val loadedBook = remember(col, bookId, prefs.appLanguage, prefs.internalBibleVersion) {
    ContentRepo.loadBookWithEdition(
      context = ctx,
      collection = col,
      bookId = bookId,
      appLang = prefs.appLanguage,
      internalBibleVersion = prefs.internalBibleVersion
    )
  }
  val book = loadedBook?.book
  val effectiveLanguage = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val loadedEditionId = loadedBook?.effectiveEdition
  val activeEditionId = loadedEditionId
    ?: BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)
  // Keep the last edition that actually loaded while a replacement is pending.
  // A transient null must not move saveable anchor state into the default
  // edition's scope and strand the position restored after recreation.
  var readerAnchorEditionId by rememberSaveable(col, bookId) {
    mutableStateOf(loadedEditionId)
  }
  LaunchedEffect(loadedEditionId) {
    if (loadedEditionId != null) readerAnchorEditionId = loadedEditionId
  }
  // A bullet index is presentation-local. Do not carry it between editions or
  // localized assets, where one edition can split or combine source verses.
  val readerAnchorEditionScope = loadedEditionId ?: readerAnchorEditionId ?: "pending"
  val readerAnchorScope = "$effectiveLanguage/$readerAnchorEditionScope"
  val keepLoadedLinkedVerse = BibleEditions.canKeepLinkedVerse(
    prefs.appLanguage, initialSourceLanguage, linkedEditionUnavailable
  ) && BibleEditions.linkedEditionMatchesLoaded(
    prefs.appLanguage, initialSourceEdition, activeEditionId
  )

  val index = remember(book) { book?.let { ChapterLocator.build(it) } }
  val storyIndex = remember(book) {
    val introOffset = if (book?.intro?.isNotBlank() == true) 1 else 0
    book?.stories?.mapIndexed { i, s -> s.id to (i + introOffset) }?.toMap().orEmpty()
  }

  // A tapped cross-reference names the asset id, but story ids are numbered from
  // the book's OWN id field, and the two are not always the same string:
  // bel_and_the_dragon.json declares id "bel" and numbers its story "bel-1",
  // 1_samuel.json declares "1-samuel", gospel_thomas.json declares
  // "gospel_of_thomas". Seventeen books differ that way, which cost 935 links.
  //
  // "<book.id>-<chapter>" reproduces every story id in all 99 books with no
  // exceptions, so rebuild from the book that actually loaded and keep
  // ChapterLocator as a backstop. The ids themselves stay put: bookmarks and
  // highlighted verses resolve by stored story id, and renaming them would
  // strand every one already on a reader's device.
  val resolvedStoryId = remember(initialStoryId, initialSourceLanguage, effectiveLanguage, storyIndex, book, index) {
    val want = resolveStoryIdAcrossLanguages(
      initialStoryId, initialSourceLanguage ?: effectiveLanguage, effectiveLanguage
    )
    if (want.isNullOrBlank() || want in storyIndex) {
      want
    } else {
      val chapter = want.substringAfterLast('-')
      book?.id?.let { "$it-$chapter" }?.takeIf { it in storyIndex }
        ?: chapter.toIntOrNull()?.let { index?.byChapter?.get(it) }
    }
  }

  // Keep the reader's item index and scroll offset across Activity recreation
  // while still resetting when launchSingleTop reuses this screen for another book.
  val listState = rememberSaveable(
    col,
    bookId,
    saver = LazyListState.Saver
  ) { LazyListState() }

  // Route targets are navigation events, not standing scroll commands. Store
  // the identity that was handled instead of a Boolean: a reused navigation
  // entry can restore an old saveable value before changed inputs invalidate it.
  val initialTargetIdentity = readerRequestIdentity(
    initialRequestId,
    col,
    bookId,
    initialStoryId,
    initialSourceLanguage,
    effectiveLanguage,
    initialSourceEdition,
    activeEditionId,
    initialVerse,
    initialVerseEnd
  )
  var consumedInitialTargetIdentity by rememberSaveable(col, bookId) {
    mutableStateOf<String?>(null)
  }
  val autoStartTtsIdentity = readerRequestIdentity(
    initialRequestId,
    col,
    bookId,
    autoStartTts
  )
  var consumedAutoStartTtsIdentity by rememberSaveable(col, bookId) {
    mutableStateOf<String?>(null)
  }
  // Kept before the progress observer so restoration cannot write a transient
  // chapter while a reflow is moving the semantic anchor back into place. This
  // is an in-flight coroutine marker, not durable reader state; restoring it
  // after process death could suppress anchor capture forever.
  var viewportRestoringRequestId by remember(col, bookId, readerAnchorScope) {
    mutableStateOf<Int?>(null)
  }
  // Semantic reader position survives only inside the same localized edition.
  var viewportAnchorStoryId by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf<String?>(null) }
  var viewportAnchorBullet by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf(-1) }
  var viewportAnchorOffset by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf(0f) }
  var viewportHasVisibleVerse by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf(false) }
  var viewportRestorePending by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf(false) }
  var viewportRestoreRequestId by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf(0) }
  var lastReaderWidth by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf(0) }
  var lastReaderLayoutKey by rememberSaveable(col, bookId, readerAnchorScope) { mutableStateOf("") }
  val positionedVerseRoots = remember(readerAnchorScope) { mutableStateMapOf<String, ReaderViewportMeasurement>() }
  var viewportMeasurementEpoch by remember { mutableStateOf(0) }
  var viewportTopY by remember { mutableFloatStateOf(0f) }
  var viewportHeightPx by remember { mutableStateOf(0) }
  val readerLayoutKey = "${prefs.fontMode}/${prefs.textSizeScale}/${prefs.readingLineSpacing}/${prefs.versePerLine}"
  val layoutKeyChanged = lastReaderLayoutKey.isNotEmpty() && lastReaderLayoutKey != readerLayoutKey
  val readerVisibleItemIndex by remember(listState) {
    derivedStateOf {
      val layoutInfo = listState.layoutInfo
      firstReaderVisibleItemIndex(
        items = layoutInfo.visibleItemsInfo.map { item ->
          ReaderVisibleItemMeasurement(item.index, item.offset, item.size)
        },
        // LazyList can retain a preceding item solely inside negative content
        // padding. It is not the chapter occupying the reading viewport.
        viewportStartOffset = maxOf(0, layoutInfo.viewportStartOffset)
      ) ?: listState.firstVisibleItemIndex
    }
  }

  fun requestViewportRestore() {
    if (viewportHasVisibleVerse && viewportAnchorStoryId != null) {
      viewportRestoreRequestId += 1
      viewportRestorePending = true
    }
  }

  fun cancelViewportRestoreForNavigation() {
    viewportRestoreRequestId += 1
    viewportRestorePending = false
    viewportRestoringRequestId = null
    viewportHasVisibleVerse = false
    viewportAnchorStoryId = null
    viewportAnchorBullet = -1
    viewportAnchorOffset = 0f
    viewportMeasurementEpoch += 1
    positionedVerseRoots.clear()
  }

  fun refreshViewportAnchorFromMeasurements(epoch: Int) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo.map { item ->
      ReaderVisibleItemMeasurement(item.index, item.offset, item.size)
    }
    val visibleStoryIds = storyIndex.entries.mapNotNull { (storyId, itemIndex) ->
      storyId.takeIf {
        isReaderItemVisible(
          itemIndex = itemIndex,
          items = visibleItems,
          viewportStartOffset = maxOf(0, layoutInfo.viewportStartOffset),
          viewportEndOffset = layoutInfo.viewportEndOffset
        )
      }
    }.toSet()
    val candidate = selectReaderViewportAnchor(
      measurements = positionedVerseRoots,
      generation = epoch,
      viewportTopY = viewportTopY,
      viewportBottomY = viewportTopY + viewportHeightPx,
      visibleStoryIds = visibleStoryIds
    )
    if (candidate == null) {
      viewportHasVisibleVerse = false
      viewportAnchorStoryId = null
      viewportAnchorBullet = -1
      viewportAnchorOffset = 0f
      return
    }
    val (key, measurement) = candidate
    val separator = key.lastIndexOf('/')
    val bullet = key.substring(separator + 1).toIntOrNull()
    if (separator <= 0 || bullet == null) return
    viewportHasVisibleVerse = true
    viewportAnchorStoryId = key.substring(0, separator)
    viewportAnchorBullet = bullet
    viewportAnchorOffset = measurement.rootY - viewportTopY
  }

  // Tab switching and rotation retain the reader's open study sections.
  val sectionOverrides = rememberSaveable(col, bookId,
    saver = androidx.compose.runtime.saveable.mapSaver(
      save = { state: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean> -> state.toMap() },
      restore = { saved -> mutableStateMapOf<String, Boolean>().apply {
        saved.forEach { (key, value) -> this[key] = value as Boolean }
      } }
    )
  ) { mutableStateMapOf<String, Boolean>() }

  val nextBook = remember(col, bookId, prefs.appLanguage) {
    val books = ContentRepo.listBooksLocalized(ctx, col, prefs.appLanguage)
    val idx = books.indexOfFirst { it.first == bookId }
    if (idx >= 0 && idx + 1 < books.size) {
      Triple(col, books[idx + 1].first, books[idx + 1].second)
    } else if (col == "old_testament") {
      val nt = ContentRepo.listBooksLocalized(ctx, "new_testament", prefs.appLanguage)
      if (nt.isNotEmpty()) Triple("new_testament", nt[0].first, nt[0].second) else null
    } else null
  }

  // Verse selection state: Set of (storyId, bulletIndex)
  var selectedBullets by remember(col, bookId, activeEditionId) {
    mutableStateOf(setOf<Pair<String, Int>>())
  }

  val bookKey = "$effectiveLanguage/$col/$bookId"
  val legacyBookKey = "$col/$bookId"
  var expandedStoryIds by remember(book, prefs.collapsedStoriesJson, effectiveLanguage) {
    val allIds = book?.stories?.map { it.id }?.toSet() ?: emptySet()
    val collapsed = runCatching {
      val root = Json.parseToJsonElement(prefs.collapsedStoriesJson).jsonObject
      val legacySafe = !(bookId == "psalms" && effectiveLanguage == "ru") &&
        !(bookId == "malachi" && effectiveLanguage in setOf("de", "fr"))
      (root[bookKey] ?: if (legacySafe) root[legacyBookKey] else null)
        ?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
    }.getOrDefault(emptySet())
    mutableStateOf(allIds - collapsed)
  }

  // Gold fade: highlight the bullets covering the target verse range after navigating from a scripture ref
  var goldFadeStoryId by remember { mutableStateOf<String?>(null) }
  var goldFadeBulletIdxs by remember { mutableStateOf<Set<Int>>(emptySet()) }

  // Chapter TTS state (story.id as identity; avoids LazyColumn index timing issues)
  var chapterTtsPlaying by remember { mutableStateOf(false) }
  var chapterTtsStoryId by remember { mutableStateOf<String?>(null) }
  var chapterTtsPaused by remember { mutableStateOf(false) }
  var ttsToggleInFlight by remember { mutableStateOf(false) }

  // Section TTS state: one-shot playback of a key takeaway / cross refs /
  // translation notes block. Never auto-continues. Key is "<storyId>:<kind>".
  var sectionTtsKey by remember { mutableStateOf<String?>(null) }
  var sectionTtsPaused by remember { mutableStateOf(false) }
  var sectionTtsToggleInFlight by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }
  val undoActionLabel = stringResource(Res.string.snack_undo)
  val highlightClearedMsg = stringResource(Res.string.snack_highlight_cleared)
  val verseRemovedMsg = stringResource(Res.string.snack_verse_removed)
  val bookmarkRemovedMsg = stringResource(Res.string.snack_bookmark_removed)

  suspend fun showUndo(message: String, actionLabel: String, onUndo: suspend () -> Unit) {
    val result = snackbarHostState.showSnackbar(
      message = message,
      actionLabel = actionLabel,
      duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
  }

  DisposableEffect(Unit) {
    platformTtsInit(ctx)
    onDispose {
      platformTtsStop(ctx)
    }
  }

  LaunchedEffect(autoStartTtsIdentity, book?.id) {
    if (
      autoStartTts &&
      consumedAutoStartTtsIdentity != autoStartTtsIdentity &&
      book != null &&
      book.stories.isNotEmpty()
    ) {
      consumedAutoStartTtsIdentity = autoStartTtsIdentity
      delay(400)
      chapterTtsStoryId = resolvedStoryId?.takeIf { target -> book.stories.any { it.id == target } }
        ?: firstReaderChapterId(book)
      chapterTtsPlaying = true
    }
  }

  LaunchedEffect(chapterTtsPlaying, chapterTtsStoryId) {
    val currentId = chapterTtsStoryId
    if (!chapterTtsPlaying || currentId == null || book == null) return@LaunchedEffect

    val stories = book.stories
    val storyIdx = stories.indexOfFirst { it.id == currentId }
    if (storyIdx < 0) {
      chapterTtsPlaying = false
      chapterTtsStoryId = null
      chapterTtsPaused = false
      return@LaunchedEffect
    }

    val story = stories[storyIdx]

    // Expand the story if collapsed so user can follow along
    if (story.id !in expandedStoryIds) {
      expandedStoryIds = expandedStoryIds + story.id
    }

    // Scroll to the chapter being read. Add intro offset so LazyColumn index matches.
    val introOffset = if (book.intro.isNotBlank()) 1 else 0
    cancelViewportRestoreForNavigation()
    listState.animateScrollToItem(storyIdx + introOffset)

    val text = ttsBuildChapterText(
      story, prefs.appLanguage, prefs.divineName, prefs.divineNameColor != "default", col
    )

    val lang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
    platformTtsStop(ctx)
    delay(150)
    platformTtsSpeak(ctx, text, lang)

    // Wait for speech to start (up to 10s)
    var started = false
    for (i in 0..39) {
      delay(250)
      if (!chapterTtsPlaying) return@LaunchedEffect
      if (platformTtsIsSpeaking(ctx)) { started = true; break }
    }

    // Wait for speech to finish. Treat "paused" as still-in-progress so the
    // auto-continue advance doesn't trigger while the user is paused.
    if (started) {
      while (chapterTtsPlaying &&
        (platformTtsIsSpeaking(ctx) || platformTtsIsPaused(ctx))
      ) { delay(500) }
    }

    // Auto-advance only if still playing on the same story and user wants auto-continue
    if (chapterTtsPlaying && currentId == chapterTtsStoryId) {
      if (prefs.autoContinueTts) {
        val nextIdx = storyIdx + 1
        if (nextIdx < stories.size) {
          chapterTtsStoryId = stories[nextIdx].id
          chapterTtsPaused = false
        } else if (prefs.crossBookTts && nextBook != null && onNavigateToBook != null) {
          chapterTtsPlaying = false
          chapterTtsStoryId = null
          chapterTtsPaused = false
          onNavigateToBook(nextBook.first, nextBook.second, true)
        } else {
          chapterTtsPlaying = false
          chapterTtsStoryId = null
          chapterTtsPaused = false
        }
      } else {
        chapterTtsPlaying = false
        chapterTtsStoryId = null
        chapterTtsPaused = false
      }
    }
  }

  // Section TTS: one-shot playback of key takeaway / cross refs / translation
  // notes. Does not auto-continue; clears sectionTtsKey when speech finishes.
  LaunchedEffect(sectionTtsKey) {
    val key = sectionTtsKey ?: return@LaunchedEffect
    val parts = key.split(':', limit = 2)
    if (parts.size != 2 || book == null) {
      sectionTtsKey = null
      return@LaunchedEffect
    }
    val storyId = parts[0]
    val kind = parts[1]
    val story = book.stories.firstOrNull { it.id == storyId } ?: run {
      sectionTtsKey = null
      return@LaunchedEffect
    }
    val text = when (kind) {
      "key_takeaway" -> ttsBuildKeyTakeawayText(story, prefs.appLanguage)
      "cross_refs" -> ttsBuildCrossRefsText(story)
      "manuscript_variants" -> ttsBuildManuscriptVariantsText(story)
      "translation_notes" -> ttsBuildTranslationNotesText(story)
      else -> ""
    }
    if (text.isBlank()) {
      sectionTtsKey = null
      return@LaunchedEffect
    }

    val lang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
    platformTtsStop(ctx)
    delay(150)
    if (sectionTtsKey != key) return@LaunchedEffect
    platformTtsSpeak(ctx, text, lang)

    var started = false
    for (i in 0..39) {
      delay(250)
      if (sectionTtsKey != key) return@LaunchedEffect
      if (platformTtsIsSpeaking(ctx)) { started = true; break }
    }

    if (started) {
      while (sectionTtsKey == key &&
        (platformTtsIsSpeaking(ctx) || platformTtsIsPaused(ctx))
      ) { delay(500) }
    }

    if (sectionTtsKey == key) {
      sectionTtsKey = null
      sectionTtsPaused = false
    }
  }

  LaunchedEffect(
    initialTargetIdentity,
    keepLoadedLinkedVerse,
    storyIndex,
    book
  ) {
    if (
      consumedInitialTargetIdentity != initialTargetIdentity &&
      !resolvedStoryId.isNullOrBlank() &&
      book != null
    ) {
      consumedInitialTargetIdentity = initialTargetIdentity
      cancelViewportRestoreForNavigation()
      if (resolvedStoryId !in expandedStoryIds) {
        expandedStoryIds = expandedStoryIds + resolvedStoryId
      }
      val storyIdx = storyIndex[resolvedStoryId]
      if (initialVerse != null && keepLoadedLinkedVerse) {
        val story = book.stories.find { it.id == resolvedStoryId }
        if (story != null) {
          val end = initialVerseEnd?.coerceAtLeast(initialVerse) ?: initialVerse
          val bullets = findBulletsForVerseRange(story.summaryBullets, initialVerse, end, story.id, bookId)
          if (bullets.isNotEmpty()) {
            goldFadeStoryId = resolvedStoryId
            goldFadeBulletIdxs = bullets
            val firstBullet = bullets.min()
            val approxOffset = firstBullet * 200 + 150
            if (storyIdx != null) listState.scrollToItem(storyIdx, approxOffset)
          } else if (storyIdx != null) {
            listState.scrollToItem(storyIdx)
          }
        } else if (storyIdx != null) {
          listState.scrollToItem(storyIdx)
        }
      } else if (storyIdx != null) {
        val story = book.stories.find { it.id == resolvedStoryId }
        if (keepLoadedLinkedVerse && story != null && story.summaryBullets.isNotEmpty()) {
          goldFadeStoryId = resolvedStoryId
          goldFadeBulletIdxs = setOf(0)
        }
        listState.scrollToItem(storyIdx)
      }
    }
  }

  // Auto-clear gold fade state slightly after the fade animation completes so
  // the bullet isn't "stuck" as a highlight target and can be re-triggered later.
  LaunchedEffect(goldFadeStoryId, goldFadeBulletIdxs) {
    if (goldFadeStoryId != null && goldFadeBulletIdxs.isNotEmpty()) {
      delay(8000)
      goldFadeStoryId = null
      goldFadeBulletIdxs = emptySet()
    }
  }

  // The restored list position is the authority for reading progress. Keeping
  // this in one writer avoids clearing lastReadStoryId during rotation before
  // the list observer has emitted its restored chapter.
  LaunchedEffect(book, listState, effectiveLanguage, activeEditionId) {
    if (book == null) return@LaunchedEffect
    val titlesMap = ContentRepo.listBooksLocalized(ctx, col, prefs.appLanguage).toMap()
    val title = titlesMap[bookId] ?: book.title
    val introOffset = if (book.intro.isNotBlank()) 1 else 0
    snapshotFlow { readerVisibleItemIndex }
      .distinctUntilChanged()
      .collectLatest { idx ->
        if (viewportRestoringRequestId != null) return@collectLatest
        delay(500)
        if (viewportRestoringRequestId != null) return@collectLatest
        val storyId = book.stories.getOrNull(idx - introOffset)?.id
        repo.setLastRead(col, bookId, title, storyId, effectiveLanguage, activeEditionId)
      }
  }

  // Bookmarks & saved verses
  val bookmarks by repo.bookmarksFlow.collectAsState(initial = emptyList())
  val savedVerses by repo.savedVersesFlow.collectAsState(initial = emptyList())
  val labels by repo.labelsFlow.collectAsState(initial = emptyList())
  val bookmarkedStoryIds = remember(bookmarks, col, bookId, effectiveLanguage) {
    bookmarks
      .filter { it.collection == col && it.bookId == bookId }
      .flatMap { bookmark ->
        val sourceLanguage = bookmark.sourceLanguage?.let(LocaleUtils::effectiveAssetTag)
        when {
          sourceLanguage == null -> canonicalStoryIdsToNative(bookmark.storyId, effectiveLanguage)
          sourceLanguage == effectiveLanguage -> listOf(bookmark.storyId)
          else -> emptyList()
        }
      }
      .toSet()
  }
  // Saved verses use chapter/verse anchors when available. Two editions can
  // assign different bullet indexes when one includes a verse that the other
  // places in a footnote, so bulletIndex alone is not a stable identity.
  val savedVerseRecords = remember(savedVerses, col, bookId, book, effectiveLanguage, activeEditionId) {
    if (book == null) emptyMap()
    else mapSavedVersesToCurrentBook(book, savedVerses, col, bookId, effectiveLanguage, activeEditionId)
  }
  val savedVerseMap = remember(savedVerseRecords) {
    savedVerseRecords.mapValues { (_, byIndex) ->
      byIndex.mapValues { (_, saved) -> saved.highlightColor }
    }
  }

  var showChapters by rememberSaveable { mutableStateOf(false) }
  var showAppearance by rememberSaveable { mutableStateOf(false) }
  val visibleStoryIndex by remember(book, listState) {
    derivedStateOf {
      (readerVisibleItemIndex - if (book?.intro?.isNotBlank() == true) 1 else 0)
        .coerceAtMost(book?.stories?.lastIndex ?: -1)
    }
  }
  val currentStory = book?.stories?.getOrNull(visibleStoryIndex)
  val currentChapter = index?.byChapter?.entries?.firstOrNull { it.value == currentStory?.id }?.key
  val readerBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
  LaunchedEffect(readerLayoutKey) {
    if (layoutKeyChanged) requestViewportRestore()
    lastReaderLayoutKey = readerLayoutKey
  }
  LaunchedEffect(readerAnchorScope) {
    // A new localized edition owns a new callback collection even when its
    // visible text happens to match the prior edition byte-for-byte.
    viewportMeasurementEpoch += 1
    positionedVerseRoots.clear()
  }
  var hasObservedReaderPosition by remember(readerAnchorScope) { mutableStateOf(false) }
  LaunchedEffect(listState, readerAnchorScope) {
    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
      .collect {
        // Preserve the saved semantic anchor through the first restored-state
        // emission. Later position changes begin a fresh viewport measurement.
        if (hasObservedReaderPosition && viewportRestoringRequestId == null) {
          viewportMeasurementEpoch += 1
          positionedVerseRoots.clear()
        }
        hasObservedReaderPosition = true
      }
  }
  LaunchedEffect(listState, readerAnchorScope) {
    snapshotFlow {
      ReaderViewportLayoutSnapshot(
        epoch = viewportMeasurementEpoch,
        measurements = positionedVerseRoots.toMap(),
        firstVisibleItemIndex = listState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        scrolling = listState.isScrollInProgress,
        restoringRequestId = viewportRestoringRequestId,
        restorePending = viewportRestorePending,
        viewportTopY = viewportTopY,
        viewportHeightPx = viewportHeightPx
      )
    }.collectLatest { observed ->
      if (
        observed.scrolling ||
        observed.restoringRequestId != null ||
        observed.restorePending ||
        observed.viewportHeightPx <= 0 ||
        observed.measurements.values.none { it.generation == observed.epoch }
      ) {
        return@collectLatest
      }

      // Position callbacks from each scripture block can land in adjacent
      // frames. collectLatest restarts this gate for every callback, so only a
      // settled current-generation collection may replace or clear the anchor.
      withFrameNanos { }
      withFrameNanos { }
      if (
        viewportMeasurementEpoch != observed.epoch ||
        listState.isScrollInProgress ||
        viewportRestoringRequestId != null ||
        viewportRestorePending ||
        listState.firstVisibleItemIndex != observed.firstVisibleItemIndex ||
        listState.firstVisibleItemScrollOffset != observed.firstVisibleItemScrollOffset ||
        viewportTopY != observed.viewportTopY ||
        viewportHeightPx != observed.viewportHeightPx ||
        positionedVerseRoots.toMap() != observed.measurements
      ) {
        return@collectLatest
      }
      refreshViewportAnchorFromMeasurements(observed.epoch)
    }
  }
  LaunchedEffect(
    viewportRestoreRequestId,
    viewportRestorePending,
    viewportAnchorStoryId,
    viewportAnchorBullet,
    lastReaderWidth,
    viewportTopY,
    viewportHeightPx,
    book
  ) {
    val storyId = viewportAnchorStoryId ?: return@LaunchedEffect
    if (!viewportRestorePending || viewportHeightPx <= 0 || book == null) return@LaunchedEffect
    val item = storyIndex[storyId] ?: run { viewportRestorePending = false; return@LaunchedEffect }
    val requestId = viewportRestoreRequestId
    val key = "$storyId/$viewportAnchorBullet"
    viewportRestoringRequestId = requestId
    var restored = false
    var completed = false
    try {
      // Establish a deterministic item origin before accepting coordinates for
      // this transaction. If the generation advances first, a callback can
      // publish the pre-rotation position under the new generation while
      // scrollToItem is still moving the chapter.
      listState.scrollToItem(item)
      positionedVerseRoots.clear()
      val measurementEpoch = viewportMeasurementEpoch + 1
      viewportMeasurementEpoch = measurementEpoch
      val measurement = withTimeoutOrNull(2_000) {
        snapshotFlow { positionedVerseRoots[key] }
          .first { it?.generation == measurementEpoch }
      }
      if (measurement != null && viewportRestoreRequestId == requestId && viewportRestorePending) {
        val requestedDelta = readerViewportScrollDelta(
          measurementRootY = measurement.rootY,
          viewportTopY = viewportTopY,
          savedViewportOffset = viewportAnchorOffset
        )
        listState.scrollBy(requestedDelta)
        restored = true
      }
      // On timeout, remain at the semantic chapter start. A raw item offset
      // saved at another width is not a valid fallback: applying it after
      // reflow can place the pixels in the next chapter while LazyList still
      // reports the prior item as first visible.
      completed = true
    } finally {
      // A LaunchedEffect key change cancels the old transaction. Keep the
      // request pending in that case so the replacement effect can finish it.
      if (completed && viewportRestoreRequestId == requestId) {
        viewportRestorePending = false
        if (restored) viewportHasVisibleVerse = true
      }
      if (viewportRestoringRequestId == requestId) {
        viewportRestoringRequestId = null
      }
    }
  }
  fun openReaderStory(sid: String, verse: Int? = null) {
    val story = book?.stories?.firstOrNull { it.id == sid } ?: return
    cancelViewportRestoreForNavigation()
    expandedStoryIds = expandedStoryIds + sid
    val targets = verse?.let { findBulletsForVerseRange(story.summaryBullets, it, it, story.id, bookId) }.orEmpty()
    goldFadeStoryId = sid.takeIf { targets.isNotEmpty() }
    goldFadeBulletIdxs = targets
    showChapters = false
    storyIndex[sid]?.let { item ->
      scope.launch { listState.scrollToItem(item, (targets.minOrNull()?.times(200)?.plus(150)) ?: 0) }
    }
  }
  if (showAppearance) {
    ReaderAppearanceSheet(prefs = prefs, repo = repo, onDismiss = { showAppearance = false })
  }
  if (showChapters && book != null) {
    ReaderChapterSheet(book = book, currentStoryId = currentStory?.id,
      onDismiss = { showChapters = false },
      onChooseBook = { showChapters = false; onChooseBook() },
      onIntro = {
        showChapters = false
        cancelViewportRestoreForNavigation()
        scope.launch { listState.scrollToItem(0) }
      },
      onOpenStory = { sid, verse -> openReaderStory(sid, verse) })
  }

  Scaffold(
    modifier = Modifier.nestedScroll(readerBarScroll.nestedScrollConnection),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (book != null && selectedBullets.isEmpty()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
          Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = visibleStoryIndex > 0, onClick = {
              book.stories.getOrNull(visibleStoryIndex - 1)?.let { openReaderStory(it.id) }
            }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.ui_previous_chapter))
            }
            TextButton(onClick = { showChapters = true }, modifier = Modifier.weight(1f)) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentStory?.title ?: stringResource(Res.string.intro_section_header),
                  maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (currentChapter != null) Text(
                  stringResource(Res.string.ui_chapter_position, currentChapter, index?.byChapter?.size ?: 0),
                  style = MaterialTheme.typography.labelSmall)
              }
            }
            IconButton(enabled = visibleStoryIndex < book.stories.lastIndex, onClick = {
              book.stories.getOrNull((visibleStoryIndex + 1).coerceAtLeast(0))?.let { openReaderStory(it.id) }
            }) {
              Icon(Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(Res.string.ui_next_chapter))
            }
          }
        }
      }
    },
    topBar = {
      CenterAlignedTopAppBar(
        scrollBehavior = readerBarScroll,
        title = {
          val titlesMap = remember(col, prefs.appLanguage) {
            ContentRepo.listBooksLocalized(ctx, col, prefs.appLanguage).toMap()
          }
          val titleText = titlesMap[bookId] ?: book?.title
            ?: stringResource(Res.string.books_heading_generic)

          val canToggle = book != null && index != null
          val pickerLabel = stringResource(Res.string.ui_chapters)

          val pulse = remember { Animatable(1f) }
          var didPulse by remember { mutableStateOf(false) }

          LaunchedEffect(canToggle) {
            if (!canToggle) return@LaunchedEffect
            repeat(2) {
              pulse.animateTo(0.6f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
              pulse.animateTo(1f,  animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
            }
            didPulse = true
          }

          val caretRotation = if (showChapters) 180f else 0f

          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
          ) {
            Row(
              Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = canToggle, role = Role.Button, onClickLabel = pickerLabel) {
                  showChapters = !showChapters
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .alpha(if (didPulse) 1f else pulse.value),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(Modifier.weight(1f, fill = false)) {
                Text(
                  titleText,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (loadedBook != null && col in setOf("old_testament", "new_testament", "deuterocanonical")) {
                  // A source-reference link can deliberately open a different
                  // edition from the saved preference. Identify the actual text.
                  val editionLabel = when (activeEditionId) {
                    BibleEditions.BSB -> stringResource(Res.string.version_bsb)
                    BibleEditions.KJV_1769 -> stringResource(Res.string.version_kjv)
                    BibleEditions.defaultForLanguage(effectiveLanguage) -> stringResource(Res.string.version_local_modern)
                    else -> stringResource(Res.string.version_local_traditional)
                  }
                  Text(
                    editionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
              Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                  .padding(start = 4.dp)
                  .rotate(caretRotation)
              )
            }
          }
        },
        actions = {
          val appearanceLabel = stringResource(Res.string.ui_reading_appearance)
          TextButton(onClick = { showAppearance = true },
            modifier = Modifier.semantics { contentDescription = appearanceLabel }) {
            Text("Aa", style = MaterialTheme.typography.titleMedium)
          }
        },
        navigationIcon = {
          IconButton(onClick = {
            if (selectedBullets.isNotEmpty()) {
              selectedBullets = emptySet()
            } else {
              if (chapterTtsPlaying) { platformTtsStop(ctx); chapterTtsPlaying = false; chapterTtsStoryId = null; chapterTtsPaused = false }
              onBack()
            }
          }) {
            Icon(
              if (selectedBullets.isNotEmpty()) Icons.Filled.Close
              else Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(Res.string.back)
            )
          }
        }
      )
    }
  ) { pad ->
    when {
      book == null -> {
        // Friendly empty state
        Column(
          Modifier.padding(pad).fillMaxSize().padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
          )
          Spacer(Modifier.height(16.dp))
          Text(
            stringResource(Res.string.no_books_found),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      else -> {
        val effLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
        // Only matters when a tap leaves the app for bible.com or BibleGateway,
        // where the selected translation may not carry the deuterocanon. The
        // internal reader serves the bundled text, so there is nothing to swap.
        val dcReference = remember(bookId) { Linker.referenceForDeuterocanonBook(bookId) }
        val needsDcWarning = col == "deuterocanonical" &&
                prefs.readerMode != "internal" &&
                dcReference != null &&
                !Linker.supportsDc(
                  prefs.readerMode,
                  prefs.translation,
                  effLang,
                  dcReference
                )
        val dcCandidates = remember(prefs.readerMode, effLang, dcReference) {
          dcReference?.let { Linker.dcCandidates(prefs.readerMode, effLang, it) }.orEmpty()
        }
        var dcBannerDismissed by remember(col, bookId) { mutableStateOf(false) }

        Box(Modifier.padding(pad)) {
          Column(Modifier.fillMaxSize()) {
            if (needsDcWarning && !dcBannerDismissed && dcCandidates.isNotEmpty()) {
              DcBookBanner(
                current = prefs.translation,
                candidates = dcCandidates,
                onPick = { v ->
                  dcBannerDismissed = true
                  scope.launch { repo.setVersion(v) }
                },
                onDismiss = { dcBannerDismissed = true }
              )
            }

            if (loadedBook?.coverage == EditionCoverage.FALLBACK || linkedEditionUnavailable) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
              ) {
                Text(
                  text = stringResource(Res.string.edition_fallback),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
              }
            }

            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp,
                bottom = if (selectedBullets.isNotEmpty()) 80.dp else 16.dp
              ),
              verticalArrangement = Arrangement.spacedBy(24.dp),
              modifier = Modifier
                .weight(1f)
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                // Parent size dispatches before child placement. Freeze anchor
                // capture here so reflowed verse callbacks cannot replace the
                // semantic position saved under the previous width.
                .onSizeChanged { size ->
                  val width = size.width
                  if (lastReaderWidth != 0 && lastReaderWidth != width) {
                    requestViewportRestore()
                  }
                  lastReaderWidth = width
                }
                .onGloballyPositioned { coords ->
                  viewportTopY = coords.positionInRoot().y
                  viewportHeightPx = coords.size.height
                }
            ) {
              if (book?.intro?.isNotBlank() == true) {
                item("intro") {
                  var introTtsPlaying by remember { mutableStateOf(false) }
                  IntroCard(
                    bookTitle = book.title,
                    intro = book.intro,
                    collection = col,
                    prefs = prefs,
                    isTtsPlaying = introTtsPlaying,
                    onPlayTts = {
                      if (introTtsPlaying) {
                        platformTtsStop(ctx)
                        introTtsPlaying = false
                      } else {
                        val lang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
                        val spokenIntro = stripScriptureInlineTags(
                          applyDivineName(
                            book.intro,
                            prefs.divineName,
                            lang,
                            prefs.divineNameColor != "default",
                            col
                          )
                        )
                        platformTtsSpeak(ctx, spokenIntro, lang)
                        introTtsPlaying = true
                      }
                    }
                  )
                }
              }
              items(items = book.stories, key = { it.id }) { story ->
                val storySelected = remember(selectedBullets, story.id) {
                  selectedBullets.filter { it.first == story.id }.map { it.second }.toSet()
                }
                val isTtsActive = chapterTtsPlaying && chapterTtsStoryId == story.id
                val isTtsPausedHere = isTtsActive && chapterTtsPaused
                val verseLbl = stringResource(Res.string.verse_label)
                StoryCard(
                  col = col,
                  bookId = bookId,
                  listState = listState,
                  viewportTopY = viewportTopY,
                  viewportHeightPx = viewportHeightPx,
                  measurementEpoch = viewportMeasurementEpoch,
                  onVersePositioned = { storyId, bulletIndex, _, rootY, rootBottomY, epoch ->
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo.map { item ->
                      ReaderVisibleItemMeasurement(item.index, item.offset, item.size)
                    }
                    val storyItemIndex = storyIndex[storyId]
                    if (
                      storyItemIndex != null &&
                      isReaderItemVisible(
                        itemIndex = storyItemIndex,
                        items = visibleItems,
                        viewportStartOffset = maxOf(0, layoutInfo.viewportStartOffset),
                        viewportEndOffset = layoutInfo.viewportEndOffset
                      )
                    ) {
                      positionedVerseRoots["$storyId/$bulletIndex"] = ReaderViewportMeasurement(
                        rootY = rootY,
                        rootBottomY = rootBottomY,
                        generation = epoch
                      )
                    }
                  },
                  story = story,
                  prefs = prefs.copy(internalBibleVersion = activeEditionId),
                  isTtsPlaying = isTtsActive && !chapterTtsPaused,
                  isTtsPaused = isTtsPausedHere,
                  onPlayTts = {
                    if (!ttsToggleInFlight) {
                      ttsToggleInFlight = true
                      scope.launch {
                        try {
                          when {
                            isTtsActive && chapterTtsPaused -> {
                              platformTtsResume(ctx)
                              chapterTtsPaused = false
                            }
                            isTtsActive -> {
                              platformTtsPause(ctx)
                              chapterTtsPaused = true
                            }
                            else -> {
                              platformTtsStop(ctx)
                              chapterTtsPaused = false
                              delay(120)
                              chapterTtsStoryId = story.id
                              chapterTtsPlaying = true
                            }
                          }
                        } finally {
                          ttsToggleInFlight = false
                        }
                      }
                    }
                  },
                  selectedBullets = storySelected,
                  onToggleBullet = { idx ->
                    doHaptic()
                    val key = story.id to idx
                    selectedBullets = if (key in selectedBullets) selectedBullets - key
                    else selectedBullets + key
                  },
                  inSelectionMode = selectedBullets.isNotEmpty(),
                  isBookmarked = story.id in bookmarkedStoryIds,
                  isExpanded = story.id in expandedStoryIds,
                  onToggleExpand = {
                    val newExpanded = if (story.id in expandedStoryIds)
                      expandedStoryIds - story.id else expandedStoryIds + story.id
                    expandedStoryIds = newExpanded
                    scope.launch {
                      val allIds = book.stories.map { it.id }.toSet()
                      val collapsed = allIds - newExpanded
                      val root = runCatching {
                        Json.parseToJsonElement(prefs.collapsedStoriesJson).jsonObject.toMutableMap()
                      }.getOrDefault(mutableMapOf())
                      // Keep an explicit empty array so an old unscoped value
                      // cannot reappear when every chapter is expanded.
                      root[bookKey] = JsonArray(collapsed.map { JsonPrimitive(it) })
                      repo.setCollapsedStories(JsonObject(root).toString())
                    }
                  },
                  onToggleBookmark = {
                    doHaptic()
                    scope.launch {
                      if (story.id in bookmarkedStoryIds) {
                        val prior = bookmarks.filter {
                          if (it.collection != col || it.bookId != bookId) return@filter false
                          val sourceLanguage = it.sourceLanguage?.let(LocaleUtils::effectiveAssetTag)
                          when {
                            sourceLanguage == null ->
                              story.id in canonicalStoryIdsToNative(it.storyId, effectiveLanguage)
                            sourceLanguage == effectiveLanguage -> it.storyId == story.id
                            else -> false
                          }
                        }
                        prior.forEach {
                          repo.removeBookmark(it.collection, it.bookId, it.storyId, it.sourceLanguage)
                        }
                        if (prior.isNotEmpty()) {
                          scope.launch {
                            showUndo(bookmarkRemovedMsg, undoActionLabel) {
                              prior.forEach { repo.addBookmark(it) }
                            }
                          }
                        }
                      } else {
                        repo.addBookmark(Bookmark(
                          collection = col,
                          bookId = bookId,
                          bookTitle = book.title,
                          storyId = story.id,
                          storyTitle = story.title,
                          snippet = story.summaryBullets.firstOrNull()
                            ?.let(::stripScriptureInlineTags)
                            ?.take(80) ?: "",
                          timestamp = currentTimeMillis(),
                          sourceLanguage = effectiveLanguage
                        ))
                      }
                    }
                  },
                  savedVerseColors = savedVerseMap[story.id] ?: emptyMap(),
                  goldFadeBulletIdxs = if (goldFadeStoryId == story.id) goldFadeBulletIdxs else emptySet(),
                  showKeyTakeaway = sectionOverrides["${story.id}:key_takeaway"] ?: prefs.expandNotesDefault,
                  showCrossRefs = sectionOverrides["${story.id}:cross_refs"] ?: prefs.expandNotesDefault,
                  showManuscriptVariants = sectionOverrides["${story.id}:manuscript_variants"] ?: prefs.expandNotesDefault,
                  showTranslationNotes = sectionOverrides["${story.id}:translation_notes"] ?: prefs.expandNotesDefault,
                  onToggleKeyTakeaway = {
                    val k = "${story.id}:key_takeaway"
                    sectionOverrides[k] = !(sectionOverrides[k] ?: prefs.expandNotesDefault)
                  },
                  onToggleCrossRefs = {
                    val k = "${story.id}:cross_refs"
                    sectionOverrides[k] = !(sectionOverrides[k] ?: prefs.expandNotesDefault)
                  },
                  onToggleManuscriptVariants = {
                    val k = "${story.id}:manuscript_variants"
                    sectionOverrides[k] = !(sectionOverrides[k] ?: prefs.expandNotesDefault)
                  },
                  onToggleTranslationNotes = {
                    val k = "${story.id}:translation_notes"
                    sectionOverrides[k] = !(sectionOverrides[k] ?: prefs.expandNotesDefault)
                  },
                  onCopyBullet = { idx ->
                    doHaptic()
                    val content = buildSelectedContent(book, setOf(story.id to idx))
                    val url = content.primaryRef?.let {
                      editionAwareExternalLink(
                        ctx, ScriptureRefs.canonicalizeRef(it), bookId, prefs, activeEditionId
                      )?.second
                    }
                    val appLink = appPassageLink(col, bookId, story.id, effectiveLanguage, activeEditionId,
                      parseTrailingVerseAnchor(story.summaryBullets[idx], story.id, bookId)?.anchor)
                    val shareText = content.text + "\n\n" + listOfNotNull(url, appLink).joinToString("\n")
                    platformCopyToClipboard(ctx, content.primaryRef?.let { ScriptureRefs.localizeRef(it) } ?: verseLbl, shareText)
                  },
                  activeSectionTts = sectionTtsKey
                    ?.takeIf { it.startsWith("${story.id}:") }
                    ?.substringAfter(':'),
                  sectionTtsPaused = sectionTtsPaused,
                  onPlaySectionTts = { kind ->
                    if (!sectionTtsToggleInFlight) {
                      sectionTtsToggleInFlight = true
                      scope.launch {
                        try {
                          val key = "${story.id}:$kind"
                          val sameActive = sectionTtsKey == key
                          when {
                            sameActive && !sectionTtsPaused -> {
                              platformTtsPause(ctx)
                              sectionTtsPaused = true
                            }
                            sameActive && sectionTtsPaused -> {
                              platformTtsResume(ctx)
                              sectionTtsPaused = false
                            }
                            else -> {
                              chapterTtsPlaying = false
                              chapterTtsStoryId = null
                              chapterTtsPaused = false
                              platformTtsStop(ctx)
                              sectionOverrides["${story.id}:$kind"] = true
                              delay(120)
                              sectionTtsPaused = false
                              sectionTtsKey = key
                            }
                          }
                        } finally {
                          sectionTtsToggleInFlight = false
                        }
                      }
                    }
                  }
                )
              }

              if (nextBook != null && onNavigateToBook != null) {
                item(key = "next_book") {
                  FilledTonalButton(
                    onClick = { onNavigateToBook(nextBook.first, nextBook.second, false) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                  ) {
                    Text(stringResource(Res.string.continue_to_book, nextBook.third))
                  }
                }
              }
            }
          }

          // Bottom action bar for selected verses
          AnimatedVisibility(
            visible = selectedBullets.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter)
          ) {
            val versesLbl = stringResource(Res.string.verses_label)
            var showColors by remember { mutableStateOf(false) }
            var showLabelPicker by remember { mutableStateOf(false) }
            Surface(
              tonalElevation = 8.dp,
              shadowElevation = 8.dp,
              shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
              Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Row(
                  Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceEvenly,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  // Copy
                  IconButton(onClick = {
                    doHaptic()
                    val content = buildSelectedContent(book, selectedBullets)
                    platformCopyToClipboard(ctx, content.primaryRef?.let { ScriptureRefs.localizeRef(it) } ?: versesLbl, content.text)
                    selectedBullets = emptySet()
                  }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(Res.string.cd_copy))
                  }
                  // Share
                  IconButton(onClick = {
                    doHaptic()
                    val content = buildSelectedContent(book, selectedBullets)
                    val url = content.primaryRef?.let {
                      editionAwareExternalLink(
                        ctx, ScriptureRefs.canonicalizeRef(it), bookId, prefs, activeEditionId
                      )?.second
                    }
                    val firstStory = book.stories.firstOrNull { story -> selectedBullets.any { it.first == story.id } }
                    val anchors = book.stories.flatMap { story ->
                      story.summaryBullets.mapIndexedNotNull { index, bullet ->
                        parseTrailingVerseAnchor(bullet, story.id, bookId)?.anchor
                          .takeIf { (story.id to index) in selectedBullets }
                      }
                    }
                    val appLink = appPassageLink(col, bookId, firstStory?.id, effectiveLanguage, activeEditionId,
                      contiguousSelectionAnchor(anchors))
                    val shareText = content.text + "\n\n" + listOfNotNull(url, appLink).joinToString("\n")
                    platformShareText(ctx, content.primaryRef?.let { ScriptureRefs.localizeRef(it) } ?: versesLbl, shareText)
                    selectedBullets = emptySet()
                  }) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
                  }
                  // Save: toggles saved state. If all selected verses are already saved,
                  // unsave them all. Otherwise save any that aren't saved (no color change).
                  val allSelectedSaved = selectedBullets.all { (sid, idx) ->
                    savedVerseMap[sid]?.containsKey(idx) == true
                  }
                  IconButton(onClick = {
                    doHaptic()
                    scope.launch {
                      if (allSelectedSaved) {
                        val prior = selectedBullets.mapNotNull { (sid, idx) ->
                          savedVerseRecords[sid]?.get(idx)
                        }
                        for (saved in prior) {
                          repo.removeSavedVerse(saved)
                        }
                        if (prior.isNotEmpty()) {
                          scope.launch {
                            showUndo(verseRemovedMsg, undoActionLabel) {
                              for (sv in prior) repo.addSavedVerse(sv)
                            }
                          }
                        }
                      } else {
                        for ((sid, idx) in selectedBullets) {
                          if (savedVerseMap[sid]?.containsKey(idx) == true) continue
                          val story = book.stories.find { it.id == sid } ?: continue
                          val bulletText = story.summaryBullets.getOrNull(idx) ?: continue
                          repo.addSavedVerse(
                            makeSavedVerse(
                              collection = col,
                              bookId = bookId,
                              story = story,
                              bulletIndex = idx,
                              bulletText = bulletText,
                              editionId = activeEditionId,
                              sourceLanguage = effectiveLanguage
                            )
                          )
                        }
                      }
                      selectedBullets = emptySet()
                    }
                  }) {
                    Icon(
                      if (allSelectedSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                      contentDescription = stringResource(Res.string.cd_bookmark),
                      tint = if (allSelectedSaved) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  // Highlight
                  IconButton(onClick = { showColors = !showColors; showLabelPicker = false }) {
                    Icon(Icons.Filled.FormatColorFill, contentDescription = stringResource(Res.string.cd_highlight))
                  }
                  // Label
                  IconButton(onClick = { showLabelPicker = !showLabelPicker; showColors = false }) {
                    Icon(Icons.Filled.Star, contentDescription = stringResource(Res.string.cd_label))
                  }
                  // Deselect
                  TextButton(onClick = { selectedBullets = emptySet() }) {
                    Text("${selectedBullets.size} \u2715")
                  }
                }
                // Color picker row
                AnimatedVisibility(visible = showColors) {
                  Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                  ) {
                    val highlightOptions = listOf("yellow", "green", "blue", "pink")
                    for (hlKey in highlightOptions) {
                      val color = labelColor(hlKey)
                      Surface(
                        onClick = {
                          doHaptic()
                          scope.launch {
                            for ((sid, idx) in selectedBullets) {
                              val existing = savedVerseRecords[sid]?.get(idx)
                              if (existing != null) {
                                repo.updateVerseHighlight(existing, hlKey)
                              } else {
                                val story = book.stories.find { it.id == sid } ?: continue
                                val bulletText = story.summaryBullets.getOrNull(idx) ?: continue
                                repo.addSavedVerse(
                                  makeSavedVerse(
                                    collection = col,
                                    bookId = bookId,
                                    story = story,
                                    bulletIndex = idx,
                                    bulletText = bulletText,
                                    editionId = activeEditionId,
                                    sourceLanguage = effectiveLanguage,
                                    highlightColor = hlKey
                                  )
                                )
                              }
                            }
                            selectedBullets = emptySet()
                            showColors = false
                          }
                        },
                        shape = CircleShape,
                        color = color,
                        modifier = Modifier.size(48.dp)
                      ) {}
                    }
                    // Clear highlight (keeps verse saved; only clears the color)
                    Surface(
                      onClick = {
                        scope.launch {
                          val prior = selectedBullets.mapNotNull { (sid, idx) ->
                            val saved = savedVerseRecords[sid]?.get(idx)
                            val color = saved?.highlightColor
                            if (saved != null && color != null) saved to color else null
                          }
                          for ((sid, idx) in selectedBullets) {
                            val saved = savedVerseRecords[sid]?.get(idx) ?: continue
                            repo.updateVerseHighlight(saved, null)
                          }
                          selectedBullets = emptySet()
                          showColors = false
                          if (prior.isNotEmpty()) {
                            scope.launch {
                              showUndo(highlightClearedMsg, undoActionLabel) {
                                for ((saved, color) in prior) {
                                  repo.updateVerseHighlight(saved, color)
                                }
                              }
                            }
                          }
                        }
                      },
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surfaceVariant,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                      modifier = Modifier.size(48.dp)
                    ) {}
                  }
                }
                // Label picker row
                AnimatedVisibility(visible = showLabelPicker) {
                  var newLabelName by remember { mutableStateOf("") }
                  Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    FlowRow(
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      for (lbl in labels) {
                        val lblColor = labelColor(lbl.color)
                        FilterChip(
                          selected = false,
                          onClick = {
                            doHaptic()
                            scope.launch {
                              for ((sid, idx) in selectedBullets) {
                                val story = book.stories.find { it.id == sid } ?: continue
                                val bulletText = story.summaryBullets.getOrNull(idx) ?: continue
                                val existing = savedVerseRecords[sid]?.get(idx)
                                val saved = existing ?: makeSavedVerse(
                                  collection = col,
                                  bookId = bookId,
                                  story = story,
                                  bulletIndex = idx,
                                  bulletText = bulletText,
                                  editionId = activeEditionId,
                                  sourceLanguage = effectiveLanguage
                                ).also { repo.addSavedVerse(it) }
                                repo.addLabelToVerse(saved, lbl.id)
                              }
                              selectedBullets = emptySet()
                              showLabelPicker = false
                            }
                          },
                          label = {
                            Text(lbl.name, style = MaterialTheme.typography.labelSmall)
                          },
                          leadingIcon = {
                            Box(Modifier.size(8.dp).background(lblColor, CircleShape))
                          }
                        )
                      }
                    }
                    Row(
                      Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp, end = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      OutlinedTextField(
                        value = newLabelName,
                        onValueChange = { newLabelName = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        placeholder = { Text(stringResource(Res.string.new_label), style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall
                      )
                      Spacer(Modifier.width(8.dp))
                      FilledTonalButton(
                        onClick = {
                          if (newLabelName.isNotBlank()) {
                            scope.launch {
                              val id = newLabelName.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + currentTimeMillis()
                              repo.addLabel(Label(id = id, name = newLabelName.trim(), timestamp = currentTimeMillis()))
                              newLabelName = ""
                            }
                          }
                        },
                        enabled = newLabelName.isNotBlank()
                      ) { Text("+") }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DcBookBanner(
  current: String,
  candidates: List<String>,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  Card(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
      contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    )
  ) {
    Column(Modifier.padding(16.dp)) {
      Text(
        stringResource(Res.string.dc_book_banner_title).replace("%1\$s", current),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(Modifier.height(4.dp))
      Text(
        stringResource(Res.string.dc_book_banner_body),
        style = MaterialTheme.typography.bodyMedium
      )
      Spacer(Modifier.height(12.dp))
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              stringResource(Res.string.dc_banner_pick),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
          ) {
            candidates.forEach { v ->
              DropdownMenuItem(
                text = { Text(v) },
                onClick = {
                  expanded = false
                  onPick(v)
                }
              )
            }
          }
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(Res.string.dc_banner_dismiss), maxLines = 1)
        }
      }
    }
  }
}

// -------------------------------------- Book intro -------------------------------------
@Composable
private fun IntroCard(
  bookTitle: String,
  intro: String,
  collection: String,
  prefs: PrefsState,
  onPlayTts: () -> Unit,
  isTtsPlaying: Boolean
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          bookTitle,
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        if (prefs.ttsReadIntros) {
          IconButton(onClick = onPlayTts) {
            Icon(
              if (isTtsPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
              contentDescription = if (isTtsPlaying)
                stringResource(Res.string.cd_tts_stop)
              else stringResource(Res.string.cd_tts_play),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
      Text(
        stringResource(Res.string.intro_section_header),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          intro.split("\n\n").forEach { paragraph ->
            if (paragraph.isNotBlank()) {
              ScriptureRefs.ClickableRefsText(
                text = paragraph.trim(),
                collection = collection,
                prefs = prefs,
                textStyle = MaterialTheme.typography.bodyLarge,
                selectionCompatible = true
              )
            }
          }
        }
      }
    }
  }
}

// -------------------------------------- Story cards ------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StoryCard(
  col: String,
  bookId: String,
  story: Story,
  prefs: PrefsState,
  modifier: Modifier = Modifier,
  listState: LazyListState? = null,
  viewportTopY: Float = 0f,
  viewportHeightPx: Int = 0,
  isTtsPlaying: Boolean = false,
  isTtsPaused: Boolean = false,
  onPlayTts: (() -> Unit)? = null,
  selectedBullets: Set<Int> = emptySet(),
  onToggleBullet: ((Int) -> Unit)? = null,
  inSelectionMode: Boolean = false,
  isBookmarked: Boolean = false,
  isExpanded: Boolean = false,
  onToggleExpand: (() -> Unit)? = null,
  onToggleBookmark: (() -> Unit)? = null,
  savedVerseColors: Map<Int, String?> = emptyMap(),
  goldFadeBulletIdxs: Set<Int> = emptySet(),
  showKeyTakeaway: Boolean = false,
  showCrossRefs: Boolean = false,
  showManuscriptVariants: Boolean = false,
  showTranslationNotes: Boolean = false,
  onToggleKeyTakeaway: (() -> Unit)? = null,
  onToggleCrossRefs: (() -> Unit)? = null,
  onToggleManuscriptVariants: (() -> Unit)? = null,
  onToggleTranslationNotes: (() -> Unit)? = null,
  activeSectionTts: String? = null,
  sectionTtsPaused: Boolean = false,
  onPlaySectionTts: ((kind: String) -> Unit)? = null,
  onCopyBullet: ((Int) -> Unit)? = null,
  measurementEpoch: Int = 0,
  onVersePositioned: ((storyId: String, bulletIndex: Int, anchor: VerseAnchor?, rootY: Float, rootBottomY: Float, epoch: Int) -> Unit)? = null
) {
  val ctx = LocalPlatformContext.current

  val defaultBook: String? = remember(story.refs) {
    story.refs.firstOrNull()?.let { ScriptureRefs.canonBookOfRef(it) }
  }

  val ktLabel = stringResource(Res.string.key_takeaway)
  val crLabel = stringResource(Res.string.cross_references)
  val canonicalShareRef = story.refs.firstOrNull()?.let { ScriptureRefs.canonicalizeRef(it) }
  val sharePlain = remember(
    story,
    col,
    bookId,
    canonicalShareRef,
    prefs.translation,
    prefs.readerMode,
    prefs.appLanguage,
    prefs.internalBibleVersion,
    ktLabel,
    crLabel
  ) {
    val md = buildStoryMarkdown(story, ktLabel, crLabel)
    val plain = markdownToPlainText(md)
    val url = canonicalShareRef?.let { ref ->
      editionAwareExternalLink(
        ctx, ref, bookId, prefs,
        BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)
      )?.second
    }
    val deepLink = appPassageLink(col, bookId, story.id, prefs.appLanguage,
      BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion))
    val links = listOfNotNull(url, deepLink).joinToString("\n")
    "$plain\n\n$links"
  }

  val expanded = isExpanded
  val hasCollapsibleContent = story.summaryBullets.isNotEmpty() ||
      story.keyTakeaway.isNotBlank() ||
      story.crossRefs.isNotEmpty() ||
      story.manuscriptVariants.isNotEmpty() ||
      story.translationNotes.isNotEmpty()

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
    border = if (isTtsPlaying || isTtsPaused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
  ) {
    Column(Modifier.animateContentSize()) {
      // Header: title + bookmark + share
      Row(
        Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
      ) {
        SelectionContainer(Modifier.weight(1f)) {
          Text(
            story.title,
            style = displayTitleTextStyle(prefs, MaterialTheme.typography.headlineSmall)
          )
        }
        if (onToggleBookmark != null) {
          val bmTint = if (isBookmarked) {
            if (col == "old_testament" || col == "pseudepigrapha") MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error
          } else MaterialTheme.colorScheme.onSurfaceVariant
          IconButton(onClick = onToggleBookmark) {
            Icon(
              if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
              contentDescription = stringResource(Res.string.cd_toggle_bookmark),
              modifier = Modifier.size(20.dp),
              tint = bmTint
            )
          }
        }
        if (onPlayTts != null) {
          val isActive = isTtsPlaying || isTtsPaused
          IconButton(onClick = onPlayTts) {
            Icon(
              if (isTtsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = when {
                isTtsPlaying -> stringResource(Res.string.cd_tts_pause)
                isTtsPaused -> stringResource(Res.string.cd_tts_resume)
                else -> stringResource(Res.string.cd_tts_play)
              },
              modifier = Modifier.size(20.dp),
              tint = if (isActive) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(onClick = { platformShareText(ctx, story.title, sharePlain) }) {
          Icon(
            Icons.Filled.Share,
            contentDescription = stringResource(Res.string.share),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Column(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        // References - always visible
        if (story.refs.isNotEmpty()) {
          val refsJoined = remember(story.refs) { story.refs.joinToString("\n") }
          SelectionContainer {
            ScriptureRefs.ClickableRefsText(
              text = refsJoined,
              collection = col,
              prefs = prefs,
              referenceEditionId = BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion),
              selectionCompatible = true
            )
          }
        }

        // Collapsible content
        if (hasCollapsibleContent) {
          AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              ReaderScripture(
                story = story,
                col = col,
                prefs = prefs,
                defaultBook = defaultBook,
                selectedBullets = selectedBullets,
                savedVerseColors = savedVerseColors,
                goldFadeBulletIdxs = goldFadeBulletIdxs,
                onToggleBullet = onToggleBullet,
                onCopyBullet = onCopyBullet,
                listState = listState,
                viewportTopY = viewportTopY,
                viewportHeightPx = viewportHeightPx,
                measurementEpoch = measurementEpoch,
                onVersePositioned = { bulletIndex, anchor, rootY, rootBottomY, epoch ->
                  onVersePositioned?.invoke(story.id, bulletIndex, anchor, rootY, rootBottomY, epoch)
                }
              )
              if (story.keyTakeaway.isNotBlank() || story.crossRefs.isNotEmpty() ||
                story.manuscriptVariants.isNotEmpty() || story.translationNotes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                  stringResource(Res.string.ui_nav_study),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              // Key takeaway (collapsible + TTS)
              if (story.keyTakeaway.isNotBlank()) {
                HorizontalDivider()
                val ktPlaying = activeSectionTts == "key_takeaway" && !sectionTtsPaused
                val ktPaused = activeSectionTts == "key_takeaway" && sectionTtsPaused
                Row(
                  Modifier.fillMaxWidth().clickable { onToggleKeyTakeaway?.invoke() },
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    stringResource(Res.string.key_takeaway),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                  )
                  if (onPlaySectionTts != null) {
                    IconButton(
                      onClick = { onPlaySectionTts("key_takeaway") }
                    ) {
                      Icon(
                        if (ktPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = when {
                          ktPlaying -> stringResource(Res.string.cd_tts_pause)
                          ktPaused -> stringResource(Res.string.cd_tts_resume)
                          else -> stringResource(Res.string.cd_tts_play)
                        },
                        modifier = Modifier.size(18.dp),
                        tint = if (ktPlaying || ktPaused) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  Icon(
                    if (showKeyTakeaway) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
                AnimatedVisibility(visible = showKeyTakeaway) {
                  SelectionContainer {
                    ScriptureRefs.ClickableRefsText(
                      text = story.keyTakeaway,
                      collection = col,
                      prefs = prefs,
                      defaultBook = defaultBook,
                      allowRelativeInParensOnly = true,
                      selectionCompatible = true
                    )
                  }
                }
              }

              // Cross references (collapsible + TTS)
              if (story.crossRefs.isNotEmpty()) {
                HorizontalDivider()
                val crPlaying = activeSectionTts == "cross_refs" && !sectionTtsPaused
                val crPaused = activeSectionTts == "cross_refs" && sectionTtsPaused
                Row(
                  Modifier.fillMaxWidth().clickable { onToggleCrossRefs?.invoke() },
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    stringResource(Res.string.cross_references),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                  )
                  if (onPlaySectionTts != null) {
                    IconButton(
                      onClick = { onPlaySectionTts("cross_refs") }
                    ) {
                      Icon(
                        if (crPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = when {
                          crPlaying -> stringResource(Res.string.cd_tts_pause)
                          crPaused -> stringResource(Res.string.cd_tts_resume)
                          else -> stringResource(Res.string.cd_tts_play)
                        },
                        modifier = Modifier.size(18.dp),
                        tint = if (crPlaying || crPaused) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  Icon(
                    if (showCrossRefs) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
                AnimatedVisibility(visible = showCrossRefs) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    story.crossRefs.forEach { x ->
                      SelectionContainer {
                        ScriptureRefs.ClickableRefsText(
                          text = x,
                          collection = col,
                          prefs = prefs,
                          defaultBook = defaultBook,
                          allowRelativeInParensOnly = true,
                          selectionCompatible = true
                        )
                      }
                    }
                  }
                }
              }

              // Manuscript variants (collapsible + TTS). Shown above translation
              // notes so readers see textual-variant footnotes before nuance notes.
              if (story.manuscriptVariants.isNotEmpty()) {
                HorizontalDivider()
                val mvPlaying = activeSectionTts == "manuscript_variants" && !sectionTtsPaused
                val mvPaused = activeSectionTts == "manuscript_variants" && sectionTtsPaused
                Row(
                  Modifier.fillMaxWidth().clickable { onToggleManuscriptVariants?.invoke() },
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    stringResource(Res.string.manuscript_variants_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                  )
                  if (onPlaySectionTts != null) {
                    IconButton(
                      onClick = { onPlaySectionTts("manuscript_variants") }
                    ) {
                      Icon(
                        if (mvPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = when {
                          mvPlaying -> stringResource(Res.string.cd_tts_pause)
                          mvPaused -> stringResource(Res.string.cd_tts_resume)
                          else -> stringResource(Res.string.cd_tts_play)
                        },
                        modifier = Modifier.size(18.dp),
                        tint = if (mvPlaying || mvPaused) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  Icon(
                    if (showManuscriptVariants) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
                AnimatedVisibility(visible = showManuscriptVariants) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    story.manuscriptVariants.forEach { mv ->
                      SelectionContainer {
                        ScriptureRefs.ClickableRefsText(
                          text = "(${mv.ref})",
                          collection = col,
                          prefs = prefs,
                          defaultBook = defaultBook,
                          allowRelativeInParensOnly = true,
                          textStyle = MaterialTheme.typography.titleSmall,
                          selectionCompatible = true
                        )
                      }
                      SelectionContainer {
                        ScriptureRefs.ClickableRefsText(
                          text = mv.text,
                          collection = col,
                          prefs = prefs,
                          defaultBook = defaultBook,
                          allowRelativeInParensOnly = true,
                          textStyle = MaterialTheme.typography.bodyMedium,
                          selectionCompatible = true
                        )
                      }
                    }
                  }
                }
              }

              // Translation notes (collapsible + TTS)
              if (story.translationNotes.isNotEmpty()) {
                HorizontalDivider()
                val tnPlaying = activeSectionTts == "translation_notes" && !sectionTtsPaused
                val tnPaused = activeSectionTts == "translation_notes" && sectionTtsPaused
                Row(
                  Modifier.fillMaxWidth().clickable { onToggleTranslationNotes?.invoke() },
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    stringResource(Res.string.translation_notes_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                  )
                  if (onPlaySectionTts != null) {
                    IconButton(
                      onClick = { onPlaySectionTts("translation_notes") }
                    ) {
                      Icon(
                        if (tnPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = when {
                          tnPlaying -> stringResource(Res.string.cd_tts_pause)
                          tnPaused -> stringResource(Res.string.cd_tts_resume)
                          else -> stringResource(Res.string.cd_tts_play)
                        },
                        modifier = Modifier.size(18.dp),
                        tint = if (tnPlaying || tnPaused) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  Icon(
                    if (showTranslationNotes) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
                AnimatedVisibility(visible = showTranslationNotes) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    story.translationNotes.forEach { tn ->
                      SelectionContainer {
                        ScriptureRefs.ClickableRefsText(
                          text = tn.term,
                          collection = col,
                          prefs = prefs,
                          allowRelativeInParensOnly = true,
                          textStyle = MaterialTheme.typography.titleSmall,
                          selectionCompatible = true
                        )
                      }
                      tn.original?.takeIf { it.isNotBlank() }?.let { orig ->
                        SelectionContainer {
                          Text(
                            orig,
                            style = MaterialTheme.typography.bodySmall.copy(
                              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                        }
                      }
                      SelectionContainer {
                        ScriptureRefs.ClickableRefsText(
                          text = tn.note,
                          collection = col,
                          prefs = prefs,
                          defaultBook = defaultBook,
                          allowRelativeInParensOnly = true,
                          selectionCompatible = true
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // Show More / Show Less toggle
          TextButton(
            onClick = { onToggleExpand?.invoke() },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
          ) {
            Icon(
              if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
              if (expanded) stringResource(Res.string.show_less) else stringResource(Res.string.show_more),
              style = MaterialTheme.typography.labelMedium
            )
          }
        }
      }
    }
  }
}


private fun mapSavedVersesToCurrentBook(
  book: Book,
  savedVerses: List<SavedVerse>,
  collection: String,
  bookId: String,
  currentLanguage: String,
  currentEdition: String
): Map<String, Map<Int, SavedVerse>> {
  val stories = book.stories.associateBy { it.id }
  return savedVerses
    .filter {
      if (it.collection != collection || it.bookId != bookId) return@filter false
      it.belongsToEdition(currentLanguage, currentEdition)
    }
    .groupBy { it.storyId }
    .mapValues { (storyId, savedForStory) ->
      val story = stories[storyId]
      val currentAnchors = story?.summaryBullets.orEmpty().mapIndexedNotNull { index, bullet ->
        parseTrailingVerseAnchor(bullet, storyId, bookId)?.anchor?.let { index to it }
      }
      buildMap {
        for (saved in savedForStory) {
          val anchor = saved.stableAnchor()
          val currentIndex = if (anchor != null) {
            currentAnchors.firstOrNull { (_, current) ->
              current.chapter == anchor.chapter &&
                current.verseStart <= anchor.verseStart &&
                current.verseEnd >= anchor.verseEnd
            }?.first
          } else {
            saved.bulletIndex.takeIf { it in story?.summaryBullets.orEmpty().indices }
          }
          if (currentIndex != null) put(currentIndex, saved)
        }
      }
    }
}

private fun makeSavedVerse(
  collection: String,
  bookId: String,
  story: Story,
  bulletIndex: Int,
  bulletText: String,
  editionId: String,
  sourceLanguage: String,
  highlightColor: String? = null
): SavedVerse {
  val anchor = parseTrailingVerseAnchor(bulletText, story.id, bookId)?.anchor
  return SavedVerse(
    collection = collection,
    bookId = bookId,
    storyId = story.id,
    bulletIndex = bulletIndex,
    chapter = anchor?.chapter,
    verseStart = anchor?.verseStart,
    verseEnd = anchor?.verseEnd,
    editionId = editionId,
    sourceLanguage = sourceLanguage,
    text = bulletText,
    ref = savedVerseReference(story.refs.firstOrNull().orEmpty(), anchor),
    highlightColor = highlightColor,
    timestamp = currentTimeMillis()
  )
}

private fun savedVerseUiKey(saved: SavedVerse): String = buildString {
  append(saved.collection).append('/')
  append(saved.bookId).append('/')
  append(saved.sourceLanguage ?: "en").append('/')
  append(saved.storyId).append('/')
  append(saved.chapter ?: "legacy").append('/')
  append(saved.verseStart ?: saved.bulletIndex).append('/')
  append(saved.verseEnd ?: saved.verseStart ?: saved.bulletIndex).append('/')
  append(saved.editionId)
}

// Wrap verse text in curly quotation marks for VOTD display/share, but skip
// quotes that already exist on either edge of the source text. Bible verses
// often begin or end with quoted speech (~21 / 366 in the EN bank); without
// this guard those days render as ""..."" in the card.
//
// Edge handling: a verse may end with the close-curly-quote followed by
// sentence punctuation (e.g. `... will see.”` followed by `.`), so detect
// the close-curly after trimming trailing punctuation. Same on the lead
// side for occasional leading whitespace bytes.
private fun wrapVotdQuotes(text: String): String {
  val t = text.trim()
  if (t.isEmpty()) return t
  val leadProbe = t.trimStart(' ', ' ', ' ', ' ')
  val trailProbe = t.trimEnd('.', '!', '?', ',', ';', ':', ' ')
  val hasLead = leadProbe.startsWith('“')
  val hasTrail = trailProbe.endsWith('”')
  return when {
    hasLead && hasTrail -> t
    hasLead -> "$t”"
    hasTrail -> "“$t"
    else -> "“$t”"
  }
}

internal fun findBulletsForVerseRange(
  bullets: List<String>, startVerse: Int, endVerse: Int,
  storyId: String? = null, bookId: String? = null
): Set<Int> {
  val out = linkedSetOf<Int>()
  for ((idx, bullet) in bullets.withIndex()) {
    // Inline cross-references are not the identity of this verse.
    val anchor = parseTrailingVerseAnchor(bullet, storyId, bookId)?.anchor ?: continue
    if (anchor.verseStart <= endVerse && anchor.verseEnd >= startVerse) {
      out += idx
    }
  }
  return out
}

internal data class SelectedContent(val text: String, val primaryRef: String?)

// Build the text + ref that represent ONLY the selected bullets.
//  - The header on each story's block is the exact selected verse reference
//    (e.g. "John 3:16,18"), not an enclosing span that adds unselected verses.
//  - primaryRef is the first story's exact reference; providers that cannot
//    encode verse lists fall back to a chapter URL.
//  - Falls back to the chapter reference / story title if the selected
//    bullets contain no parseable verse numbers.
internal fun buildSelectedContent(book: Book, selected: Set<Pair<String, Int>>): SelectedContent {
  val grouped = selected.groupBy({ it.first }, { it.second }).mapValues { it.value.sorted() }
  val sb = StringBuilder()
  var primaryRef: String? = null
  val chapterRefTail = Regex("\\s+\\d+(?::.*)?$")

  for ((storyId, indices) in grouped) {
    val story = book.stories.find { it.id == storyId } ?: continue

    val anchors = indices.mapNotNull { idx ->
      story.summaryBullets.getOrNull(idx)?.let { parseTrailingVerseAnchor(it, story.id, book.id)?.anchor }
    }
    val chapterRef = story.refs.firstOrNull()
    val bookName = chapterRef?.replace(chapterRefTail, "")
    val exactTail = if (anchors.size == indices.size) compactSelectedVerseTail(anchors) else null
    val specificRef = if (!bookName.isNullOrBlank() && bookName != chapterRef && exactTail != null) {
      "$bookName $exactTail"
    } else chapterRef

    if (primaryRef == null) primaryRef = specificRef

    if (sb.isNotEmpty()) sb.appendLine()
    when {
      specificRef != null -> sb.appendLine(ScriptureRefs.localizeRef(specificRef))
      else -> sb.appendLine(story.title)
    }
    for (idx in indices) {
      story.summaryBullets.getOrNull(idx)?.let {
        sb.appendLine(stripScriptureInlineTags(it))
      }
    }
  }

  return SelectedContent(sb.toString().trimEnd(), primaryRef)
}

@Composable
private fun labelColor(key: String): Color {
  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.25f
  return when (key) {
    "red" -> if (isDark) Color(0xFFEF5350) else Color(0xFFE53935)
    "orange" -> if (isDark) Color(0xFFFFB74D) else Color(0xFFFB8C00)
    "yellow" -> if (isDark) Color(0xFFFFD54F) else Color(0xFFFFB300)
    "green" -> if (isDark) Color(0xFF81C784) else Color(0xFF43A047)
    "blue" -> if (isDark) Color(0xFF64B5F6) else Color(0xFF1E88E5)
    "indigo" -> if (isDark) Color(0xFF7986CB) else Color(0xFF3949AB)
    "purple" -> if (isDark) Color(0xFFBA68C8) else Color(0xFF8E24AA)
    "pink" -> if (isDark) Color(0xFFF06292) else Color(0xFFD81B60)
    else -> if (isDark) Color(0xFF64B5F6) else Color(0xFF1E88E5)
  }
}

@Composable
private fun highlightBgColor(colorKey: String?): Color {
  val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.25f
  val alpha = if (isDark) 0.30f else 0.18f
  return when (colorKey) {
    "yellow" -> (if (isDark) Color(0xFFFFD54F) else Color(0xFFFFEB3B)).copy(alpha = alpha)
    "green" -> (if (isDark) Color(0xFF81C784) else Color(0xFF66BB6A)).copy(alpha = alpha)
    "blue" -> (if (isDark) Color(0xFF64B5F6) else Color(0xFF42A5F5)).copy(alpha = alpha)
    "pink" -> (if (isDark) Color(0xFFF06292) else Color(0xFFEC407A)).copy(alpha = alpha)
    else -> Color.Transparent
  }
}

// -------------------------------------- Saved Items Screen ------------------------------------
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SavedItemsScreen(
  prefs: PrefsState,
  repo: PrefsRepo,
  onBack: () -> Unit,
  onOpenBook: (col: String, bookId: String, storyId: String?) -> Unit,
  onOpenSavedVerse: (SavedVerse) -> Unit
) {
  val scope = rememberCoroutineScope()
  val bookmarks by repo.bookmarksFlow.collectAsState(initial = emptyList())
  val savedVerses by repo.savedVersesFlow.collectAsState(initial = emptyList())
  val labels by repo.labelsFlow.collectAsState(initial = emptyList())
  var selectedTab by remember { mutableStateOf(0) }
  var confirmDeleteLabel by remember { mutableStateOf<Label?>(null) }
  var skipDeleteConfirm by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(Res.string.saved_items)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
          }
        }
      )
    }
  ) { pad ->
    Column(Modifier.padding(pad)) {
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SegmentedButton(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
        ) { Text(stringResource(Res.string.bookmarks_tab), maxLines = 1) }
        SegmentedButton(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
        ) { Text(stringResource(Res.string.saved_verses_tab), maxLines = 1) }
        SegmentedButton(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
        ) { Text(stringResource(Res.string.labels_tab), maxLines = 1) }
      }

      when (selectedTab) {
        0 -> {
          if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  Icons.Outlined.BookmarkBorder,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                  stringResource(Res.string.bookmarks_tab),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            // Sort: sortOrder=0 items first by timestamp desc; user-ordered items (sortOrder>0) by sortOrder asc.
            // Same SnapshotStateList-inside-remember pattern as saved verses below; see that comment for why.
            val displayBookmarks = remember(bookmarks) {
              bookmarks.sortedWith(compareBy<Bookmark> { it.sortOrder }.thenByDescending { it.timestamp })
                .toMutableStateList()
            }
            val displayBookmarksRef = rememberUpdatedState(displayBookmarks)
            var needsPersist by remember { mutableStateOf(false) }
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
              val list = displayBookmarksRef.value
              val moved = list.removeAt(from.index)
              list.add(to.index, moved)
              needsPersist = true
            }
            LaunchedEffect(reorderState.isAnyItemDragging) {
              if (!reorderState.isAnyItemDragging && needsPersist) {
                needsPersist = false
                val reordered = displayBookmarks.mapIndexed { idx, bm -> bm.copy(sortOrder = idx + 1) }
                repo.reorderBookmarks(reordered)
              }
            }
            LazyColumn(
              state = lazyListState,
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(
                items = displayBookmarks,
                key = { "${it.collection}/${it.bookId}/${it.sourceLanguage ?: "en"}/${it.storyId}" }
              ) { bm ->
                ReorderableItem(reorderState, key = "${bm.collection}/${bm.bookId}/${bm.sourceLanguage ?: "en"}/${bm.storyId}") { isDragging ->
                  val bmColor = if (bm.collection == "old_testament" || bm.collection == "pseudepigrapha")
                    MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                  val elevation = if (isDragging) 8.dp else 0.dp
                  Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                      onOpenBook(bm.collection, bm.bookId,
                        resolveStoryIdAcrossLanguages(bm.storyId, bm.sourceLanguage, prefs.appLanguage))
                    },
                    elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                  ) {
                    Row(
                      Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                      verticalAlignment = Alignment.Top
                    ) {
                      Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = bmColor,
                        modifier = Modifier.size(24.dp).padding(top = 2.dp)
                      )
                      Spacer(Modifier.width(12.dp))
                      Column(Modifier.weight(1f)) {
                        Text(bm.storyTitle, style = MaterialTheme.typography.titleSmall)
                        Text(
                          bm.bookTitle,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bm.snippet.isNotBlank()) {
                          Text(
                            bm.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                          )
                        }
                      }
                      // Drag handle on trailing edge; long-press or touch to drag
                      IconButton(
                        modifier = Modifier.draggableHandle(),
                        onClick = {}
                      ) {
                        Icon(
                          Icons.Filled.DragHandle,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                      }
                      IconButton(onClick = {
                        scope.launch { repo.removeBookmark(bm.collection, bm.bookId, bm.storyId, bm.sourceLanguage) }
                      }) {
                        Icon(
                          Icons.Filled.Close,
                          contentDescription = stringResource(Res.string.cd_remove_bookmark),
                          modifier = Modifier.size(16.dp),
                          tint = MaterialTheme.colorScheme.error
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
        1 -> {
          if (savedVerses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  Icons.Filled.Star,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                  stringResource(Res.string.saved_verses_tab),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            // Sort: sortOrder=0 items first by timestamp desc; user-ordered items (sortOrder>0) by sortOrder asc.
            // The SnapshotStateList must be created INSIDE remember so it persists across recompositions;
            // creating it outside causes drags to snap back because mutations land on a list that gets
            // discarded on the next recomposition.
            val displayVerses = remember(savedVerses) {
              savedVerses.sortedWith(compareBy<SavedVerse> { it.sortOrder }.thenByDescending { it.timestamp })
                .toMutableStateList()
            }
            // Reorder lambda captures displayVerses at creation time. When savedVerses updates after a
            // persist, displayVerses gets a new instance — keep the lambda pointing at the live one.
            val displayVersesRef = rememberUpdatedState(displayVerses)
            var needsPersist by remember { mutableStateOf(false) }
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
              val list = displayVersesRef.value
              val moved = list.removeAt(from.index)
              list.add(to.index, moved)
              needsPersist = true
            }
            LaunchedEffect(reorderState.isAnyItemDragging) {
              if (!reorderState.isAnyItemDragging && needsPersist) {
                needsPersist = false
                val reordered = displayVerses.mapIndexed { idx, sv -> sv.copy(sortOrder = idx + 1) }
                repo.reorderSavedVerses(reordered)
              }
            }
            LazyColumn(
              state = lazyListState,
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(
                items = displayVerses,
                key = { savedVerseUiKey(it) }
              ) { sv ->
                ReorderableItem(reorderState, key = savedVerseUiKey(sv)) { isDragging ->
                  val hlColor = highlightBgColor(sv.highlightColor)
                  val verseLabels = labels.filter { it.id in sv.labels }
                  val barColor = when (sv.highlightColor) {
                    "yellow" -> Color(0xFFFBC02D)
                    "green" -> Color(0xFF66BB6A)
                    "blue" -> Color(0xFF42A5F5)
                    "pink" -> Color(0xFFEC407A)
                    else -> MaterialTheme.colorScheme.primary
                  }
                  val elevation = if (isDragging) 8.dp else 0.dp
                  Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                      onOpenSavedVerse(sv)
                    },
                    elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                  ) {
                    Row(
                      Modifier
                        .background(hlColor)
                        .height(IntrinsicSize.Min)
                        .padding(end = 4.dp),
                      verticalAlignment = Alignment.Top
                    ) {
                      Box(
                        Modifier
                          .width(6.dp)
                          .fillMaxHeight()
                          .background(barColor)
                      )
                      Column(
                        Modifier
                          .weight(1f)
                          .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                      ) {
                        ScriptureRefs.ClickableRefsText(
                          text = sv.text,
                          collection = sv.collection,
                          prefs = prefs,
                          referenceEditionId = sv.scriptureEdition(),
                          referenceLanguage = sv.scriptureLanguage(),
                          defaultBook = sv.bookId,
                          allowRelativeInParensOnly = true,
                          textStyle = MaterialTheme.typography.bodyMedium,
                          onNonLinkClick = { onOpenSavedVerse(sv) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                          ScriptureRefs.localizeRef(sv.displayReference()),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (verseLabels.isNotEmpty()) {
                          FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                          ) {
                            for (lbl in verseLabels) {
                              Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = labelColor(lbl.color).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, labelColor(lbl.color).copy(alpha = 0.4f))
                              ) {
                                Text(
                                  lbl.name,
                                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                  style = MaterialTheme.typography.labelSmall,
                                  color = labelColor(lbl.color)
                                )
                              }
                            }
                          }
                        }
                      }
                      // Drag handle — visible on trailing edge; long-press or touch to drag
                      IconButton(
                        modifier = Modifier.draggableHandle(),
                        onClick = {}
                      ) {
                        Icon(
                          Icons.Filled.DragHandle,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                      }
                      IconButton(onClick = {
                        scope.launch { repo.removeSavedVerse(sv) }
                      }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_remove_saved_verse), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                  }
                }
              }
            }
          }
        }
        2 -> {
          var editingLabel by remember { mutableStateOf<Label?>(null) }
          var newLabelName by remember { mutableStateOf("") }
          var newLabelColor by remember { mutableStateOf("blue") }
          val colorOptions = listOf("red", "orange", "yellow", "green", "blue", "indigo", "purple", "pink")

          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(items = labels, key = { it.id }) { lbl ->
              val versesWithLabel = savedVerses.filter { lbl.id in it.labels }
              Card(
                modifier = Modifier.fillMaxWidth().clickable {
                  if (editingLabel?.id == lbl.id) editingLabel = null else editingLabel = lbl
                }
              ) {
                Column(Modifier.padding(12.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(labelColor(lbl.color), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(lbl.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                      "${versesWithLabel.size}",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = {
                      if (versesWithLabel.isEmpty() || skipDeleteConfirm) {
                        scope.launch { repo.removeLabel(lbl.id) }
                      } else {
                        confirmDeleteLabel = lbl
                      }
                    }) {
                      Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_remove_label), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                  }
                  AnimatedVisibility(visible = editingLabel?.id == lbl.id) {
                    Column(Modifier.padding(top = 8.dp)) {
                      if (versesWithLabel.isEmpty()) {
                        Text(
                          stringResource(Res.string.no_labels_yet),
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      } else {
                        for (sv in versesWithLabel.take(10)) {
                          Row(
                            Modifier
                              .fillMaxWidth()
                              .clickable { onOpenSavedVerse(sv) }
                              .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Column(Modifier.weight(1f)) {
                              Text(
                                stripScriptureInlineTags(sv.text),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                              )
                              Text(ScriptureRefs.localizeRef(sv.displayReference()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                              scope.launch { repo.removeLabelFromVerse(sv, lbl.id) }
                            }) {
                              Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cd_remove_verse_label), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                          }
                        }
                        if (versesWithLabel.size > 10) {
                          Text(
                            "+${versesWithLabel.size - 10} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
            item {
              Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                  Text(stringResource(Res.string.new_label), style = MaterialTheme.typography.titleSmall)
                  Spacer(Modifier.height(8.dp))
                  OutlinedTextField(
                    value = newLabelName,
                    onValueChange = { newLabelName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.label_name)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                  )
                  Spacer(Modifier.height(8.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in colorOptions) {
                      Surface(
                        onClick = { newLabelColor = c },
                        shape = CircleShape,
                        color = labelColor(c),
                        border = if (c == newLabelColor) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                        modifier = Modifier.size(48.dp)
                      ) {}
                    }
                  }
                  Spacer(Modifier.height(8.dp))
                  FilledTonalButton(
                    onClick = {
                      if (newLabelName.isNotBlank()) {
                        scope.launch {
                          val id = newLabelName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + currentTimeMillis()
                          repo.addLabel(Label(id = id, name = newLabelName.trim(), color = newLabelColor, timestamp = currentTimeMillis()))
                          newLabelName = ""
                        }
                      }
                    },
                    enabled = newLabelName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                  ) { Text(stringResource(Res.string.add_label)) }
                }
              }
            }
          }
        }
      }
    }
  }

  confirmDeleteLabel?.let { lbl ->
    val count = savedVerses.count { lbl.id in it.labels }
    var dontAskAgain by remember { mutableStateOf(false) }
    AlertDialog(
      onDismissRequest = { confirmDeleteLabel = null },
      title = { Text(stringResource(Res.string.delete_label)) },
      text = {
        Column {
          Text(
            stringResource(Res.string.delete_label_confirm).replace("%1\$s", lbl.name).replace("%1\$d", count.toString())
          )
          Spacer(Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = dontAskAgain, onCheckedChange = { dontAskAgain = it })
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.dont_ask_again), style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        Button(onClick = {
          if (dontAskAgain) skipDeleteConfirm = true
          scope.launch { repo.removeLabel(lbl.id) }
          confirmDeleteLabel = null
        }) { Text(stringResource(Res.string.delete_label)) }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteLabel = null }) {
          Text(stringResource(Res.string.back))
        }
      }
    )
  }
}

/** Markdown that NotesMarkdown can flatten to clean text for sharing. */
private fun buildStoryMarkdown(story: Story, keyTakeawayLabel: String, crossRefsLabel: String): String = buildString {
  appendLine("# ${story.title}")
  if (story.refs.isNotEmpty()) {
    appendLine()
    story.refs.forEach { appendLine(ScriptureRefs.localizeRef(it)) }
  }
  if (story.summaryBullets.isNotEmpty()) {
    appendLine()
    story.summaryBullets.forEach { appendLine("- ${stripScriptureInlineTags(it)}") }
  }
  if (story.keyTakeaway.isNotBlank()) {
    appendLine()
    appendLine("**$keyTakeawayLabel:** ${story.keyTakeaway}")
  }
  if (story.crossRefs.isNotEmpty()) {
    appendLine()
    appendLine("**$crossRefsLabel**")
    story.crossRefs.forEach { appendLine("- $it") }
  }
}.trimEnd()

// ---------------- TTS text cleaning ----------------

private val trailingVerseRefPattern = Regex(
  """\s*\(\s*\d+\s*:\s*\d+(?:\s*[-–]\s*\d+)?(?:\s*,\s*\d+\s*:\s*\d+(?:\s*[-–]\s*\d+)?)*\s*\)\s*\.?\s*$"""
)

internal fun ttsCleanBullet(
  bullet: String,
  divineNameMode: String,
  appLanguageTag: String,
  divineNameColorActive: Boolean,
  collection: String
): String {
  val prepared = applyDivineName(
    bullet, divineNameMode, LocaleUtils.effectiveAssetTag(appLanguageTag),
    divineNameColorActive, collection
  )
  val stripped = stripScriptureInlineTags(prepared).replace(trailingVerseRefPattern, "")
  val core = stripped.trimEnd(',', ';', ' ', '.', '—', '–', '-', '\t', ' ')
  return when {
    core.isEmpty() -> ""
    core.endsWith('?') || core.endsWith('!') -> core
    else -> "$core."
  }
}

private fun ttsOrdinalWord(n: Int, lang: String): String = when (lang.lowercase()) {
  "en" -> when (n) { 1 -> "First"; 2 -> "Second"; 3 -> "Third"; else -> n.toString() }
  "es" -> when (n) { 1 -> "Primera"; 2 -> "Segunda"; 3 -> "Tercera"; else -> n.toString() }
  "fr" -> when (n) { 1 -> "Première"; 2 -> "Deuxième"; 3 -> "Troisième"; else -> n.toString() }
  "de" -> when (n) { 1 -> "Erster"; 2 -> "Zweiter"; 3 -> "Dritter"; else -> n.toString() }
  "it" -> when (n) { 1 -> "Prima"; 2 -> "Seconda"; 3 -> "Terza"; else -> n.toString() }
  "pt" -> when (n) { 1 -> "Primeira"; 2 -> "Segunda"; 3 -> "Terceira"; else -> n.toString() }
  "ru" -> when (n) { 1 -> "Первое"; 2 -> "Второе"; 3 -> "Третье"; else -> n.toString() }
  "ar" -> when (n) { 1 -> "الأول"; 2 -> "الثاني"; 3 -> "الثالث"; else -> n.toString() }
  "hi" -> when (n) { 1 -> "पहला"; 2 -> "दूसरा"; 3 -> "तीसरा"; else -> n.toString() }
  "ja" -> when (n) { 1 -> "第一"; 2 -> "第二"; 3 -> "第三"; else -> n.toString() }
  "ko" -> when (n) { 1 -> "첫째"; 2 -> "둘째"; 3 -> "셋째"; else -> n.toString() }
  "zh-hans", "zh-hant" -> when (n) { 1 -> "第一"; 2 -> "第二"; 3 -> "第三"; else -> n.toString() }
  else -> n.toString()
}

private fun ttsChapterWord(lang: String): String? = when (lang.lowercase()) {
  "en" -> "Chapter"
  "es" -> "Capítulo"
  "fr" -> "Chapitre"
  "de" -> "Kapitel"
  "it" -> "Capitolo"
  "pt" -> "Capítulo"
  "ru" -> "Глава"
  "ar" -> "الفصل"
  "hi" -> "अध्याय"
  "ja" -> "章"
  "ko" -> "장"
  "zh-hans", "zh-hant" -> "章"
  else -> null
}

private fun ttsTransformTitle(rawTitle: String, appLanguageTag: String): String {
  val stripped = rawTitle.replace(Regex("""^[^\p{L}\p{N}]+"""), "").trim()
  if (stripped.isEmpty()) return stripped
  val lang = LocaleUtils.effectiveAssetTag(appLanguageTag).lowercase()
  val chapterWord = ttsChapterWord(lang)

  val ordMatch = Regex("""^(\d+)\.?\s+(.+?)\s+(\d+)$""").find(stripped)
  if (ordMatch != null) {
    val ord = ordMatch.groupValues[1].toIntOrNull() ?: return stripped
    val book = ordMatch.groupValues[2].trim()
    val chap = ordMatch.groupValues[3]
    val ordWord = ttsOrdinalWord(ord, lang)
    return if (chapterWord != null) "$ordWord $book $chapterWord $chap" else "$ordWord $book $chap"
  }

  val plain = Regex("""^(.+?)\s+(\d+)$""").find(stripped)
  if (plain != null && chapterWord != null) {
    val book = plain.groupValues[1].trim()
    val chap = plain.groupValues[2]
    return "$book $chapterWord $chap"
  }

  return stripped
}

private fun ttsBuildChapterText(
  story: Story,
  appLanguageTag: String,
  divineNameMode: String,
  divineNameColorActive: Boolean,
  collection: String
): String = buildString {
  append(ttsTransformTitle(story.title, appLanguageTag))
  append(". ")
  story.summaryBullets.forEach { bullet ->
    val cleaned = ttsCleanBullet(
      bullet, divineNameMode, appLanguageTag, divineNameColorActive, collection
    )
    if (cleaned.isNotBlank()) {
      append(cleaned)
      append(' ')
    }
  }
}.trim()

private fun ttsBuildKeyTakeawayText(story: Story, appLanguageTag: String): String {
  val title = ttsTransformTitle(story.title, appLanguageTag)
  val body = story.keyTakeaway.trim()
  if (body.isBlank()) return ""
  return "$title. $body"
}

private fun ttsBuildCrossRefsText(story: Story): String = buildString {
  story.crossRefs.forEach { line ->
    val t = line.trim()
    if (t.isNotEmpty()) {
      append(t)
      if (!t.endsWith('.') && !t.endsWith('!') && !t.endsWith('?')) append('.')
      append(' ')
    }
  }
}.trim()

private fun ttsBuildTranslationNotesText(story: Story): String = buildString {
  story.translationNotes.forEach { tn ->
    val term = tn.term.trim()
    val note = tn.note.trim()
    if (term.isNotEmpty()) {
      append(term)
      if (!term.endsWith('.') && !term.endsWith(':')) append('.')
      append(' ')
    }
    if (note.isNotEmpty()) {
      append(note)
      if (!note.endsWith('.') && !note.endsWith('!') && !note.endsWith('?')) append('.')
      append(' ')
    }
  }
}.trim()

private fun ttsBuildManuscriptVariantsText(story: Story): String = buildString {
  story.manuscriptVariants.forEach { mv ->
    val text = mv.text.trim()
    if (text.isNotEmpty()) {
      append(text)
      if (!text.endsWith('.') && !text.endsWith('!') && !text.endsWith('?')) append('.')
      append(' ')
    }
  }
}.trim()


// ---------------- Settings & About ----------------

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: PrefsState, repo: PrefsRepo, onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val ctx = LocalPlatformContext.current
  val notificationPermissionDenied by DailyVerseNotificationBridge.permissionRequestDenied.collectAsState()
  var notificationPermissionPending by remember { mutableStateOf(false) }
  var showNotificationTime by rememberSaveable { mutableStateOf(false) }
  val notificationAllowed by DailyVerseNotificationBridge.permissionAllowed.collectAsState()
  if (showNotificationTime) {
    val minutes = prefs.dailyVerseNotificationMinuteOfDay.coerceIn(0, 1439)
    val time = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    AlertDialog(
      onDismissRequest = { showNotificationTime = false },
      title = { Text(stringResource(Res.string.daily_notification_time)) },
      text = { TimeInput(state = time) },
      confirmButton = {
        TextButton(onClick = {
          scope.launch { repo.setDailyVerseNotificationTime(time.hour * 60 + time.minute) }
          showNotificationTime = false
        }) { Text(stringResource(Res.string.daily_notification_save)) }
      },
      dismissButton = {
        TextButton(onClick = { showNotificationTime = false }) { Text(stringResource(Res.string.cancel)) }
      }
    )
  }

  // Bible.com (YouVersion) catalog — every code here must be mapped in
  // Linker.youVersionIdByCode. Ordered roughly by popularity per language.
  val versionsByLang = mapOf(
    "en" to listOf(
      "NIV","ESV","NRSVUE","KJV","NKJV","NASB","NASB1995","NASB2020","NLT","CSB","HCSB",
      "NIVUK","NIRV","AMP","AMPC","RSV","NET","MSG","GNT","GNTD","ICB","NCV","TPT","CEB",
      "CEV","CEVUK","CEVDCI","CJB","DARBY","DRC1752","EASY","BSB","EHV","FNVNT","FBV",
      "GW","JUB","LEB","LSB","LSV","MEV","NABRE","NMV","NLTCE","NRSV-CI","RSVCI","TLV",
      "WEB","WEBBE","WMB","WMBBE","WYC","YLT","AFV","ASV","CPDV","CSBA","GNV","KJVAAE","KJVAE"
    ),
    "es" to listOf(
      "RVR1960","NVI","LBLA","NBLA","NTV","RVR1995","RVR09","NBV","RVA-2015","RVA","RVC","JBS",
      "BDO1573","DHH","DHH94PC","DHH23ST","DHHDK","DHHS94","GLOSSSP","BHTI","PDT","BLPH","NVIS","TLA","TLAI"
    ),
    "fr" to listOf(
      "LSG","SG21","BDS","NEG1979",
      "BFC","PDV2017","NFC","BCC1923","JND","BEX2004","FMAR","NBS","NEG79",
      "NVS78P","OST","THU","TFM","NEG","SACY"
    ),
    "it" to listOf(
      "NR06","NR94","IRB20","DB1885","ICL00P","ICL00D","RDV24"
    ),
    "ru" to listOf(
      "RST","SYNO","NRT",
      "DROT","CSLAV","BTI","CARS","CARSA","CARST","CASS70","RSP","CAROS","ROT","RU167"
    ),
    "pt" to listOf(
      "NVI-PT","ARA","ARC","NVT","NAA","NTLH","A21","BLT","VFL","NBV-P","MZNVI","TB","BPT09DC","AVM"
    ),
    "de" to listOf(
      "LUT","ELB","SCH2000","GANTP","BIBELHEUTE","SCH1951","ELB71","ELBBK",
      "HFA","LUTHEUTE","DELUT","NGU2011","TKW"
    ),
    "zh-Hans" to listOf(
      "CUVS","RCUVSS","CCB","CUNPSS","CSBS","CNVS"
    ),
    "zh-Hant" to listOf(
      "CUNP","RCUV","CCB_T","TCV2019T","CSBT","CCCBST","CNV","ZHDC1889"
    ),
    "ja" to listOf(
      "JCB","JA1955","ERV","JA1819"
    ),
    "ko" to listOf(
      "KRV","RNKSV","KLB","KOERV","NLTNK"
    ),
    "hi" to listOf(
      "HERV","HSS","IRVHIN","HSB","HINCLBSI","HINOVBSI","HHBD"
    ),
    "ar" to listOf(
      "SAB","QNAV","AVDDV","FAOV","GOV","GNADC25"
    )
  )

  // BibleGateway catalog — codes from https://www.biblegateway.com/versions/ (scraped).
  // Only put codes here that BibleGateway actually serves, otherwise the search page
  // returns HTTP 200 with an empty body and users see a blank page.
  val bgVersionsByLang = mapOf(
    "en" to listOf(
      "NIV","ESV","NRSVUE","KJV","NKJV","NASB","NLT","CSB","NASB1995","NIRV","NIVUK",
      "NRSVA","NRSVACE","NRSVCE","ESVUK","CSBA","AMP","AMPC","AKJV","KJ21","ASV",
      "CEB","CEV","CJB","DARBY","DLNT","DRA","EASY","ERV","EHV","EXB","GW","GNT","GNV",
      "HCSB","ICB","ISV","JUB","LSB","LEB","TLB","MEV","MSG","MOUNCE","NABRE","NCB",
      "NCV","NET","NLV","NMB","NOG","NTFE","OJB","PHILLIPS","RGT","RSV","RSVCE","TLV",
      "VOICE","WEB","WE","WYC","YLT","BRG"
    ),
    "es" to listOf(
      "NVI","RVR1960","RVR1995","LBLA","NBLA","NTV","DHH","NBV","CST","PDT","BLP","BLPH",
      "RVA","RVA-2015","RVC","RVR1977","JBS","SRV-BRG","TLA"
    ),
    "fr" to listOf("LSG","BDS","SG21","NEG1979"),
    "it" to listOf("CEI","NR2006","NR1994","LND","BDG"),
    "pt" to listOf("NVI-PT","NVT","ARC","NTLH","OL","VFL"),
    "de" to listOf("HOF","SCH2000","LUTH1545","SCH1951","NGU-DE"),
    "ru" to listOf("NRT","RUSV","CARS","CARST","CARSA","ERV-RU"),
    "zh-Hans" to listOf("CUVS","CCB","CNVS","CSBS","CUVMPS","ERV-ZH","RCU17SS"),
    "zh-Hant" to listOf("CUV","CCBT","CNVT","CSBT","CUVMPT","RCU17TS"),
    "ja" to listOf("JLB","JERV"),
    "ko" to listOf("KLB","KOERV"),
    "hi" to listOf("ERV-HI","SHB"),
    "ar" to listOf("NAV","ERV-AR")
  )

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(Res.string.settings)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
          }
        }
      )
    }
  ) { pad ->
    Column(
      Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // ─── APPEARANCE SECTION ───
      SectionHeader(stringResource(Res.string.appearance))

      // Theme
      Text(stringResource(Res.string.theme), style = MaterialTheme.typography.titleSmall)
      val themeOptions = listOf(
        "system" to stringResource(Res.string.theme_system),
        "light"  to stringResource(Res.string.theme_light),
        "dark"   to stringResource(Res.string.theme_dark)
      )
      val selectedThemeKey = when (prefs.theme.lowercase()) {
        "light" -> "light"
        "dark" -> "dark"
        else -> "system"
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        themeOptions.forEach { (key, label) ->
          FilterChip(
            selected = selectedThemeKey == key,
            onClick = { scope.launch { repo.setTheme(key) } },
            label = { Text(label) }
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      // ─── Color theme ───
      Text(stringResource(Res.string.color_theme), style = MaterialTheme.typography.titleSmall)
      Text(
        stringResource(Res.string.color_theme_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      val selectedPreset = ThemePreset.fromKey(prefs.themePreset)
      val previewDark = when (prefs.theme.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
      }
      val dynamicSupported = platformSupportsDynamicColor()
      val presets = buildList {
        add(ThemePreset.Parchment to stringResource(Res.string.theme_parchment))
        add(ThemePreset.Sage to stringResource(Res.string.theme_sage))
        add(ThemePreset.Indigo to stringResource(Res.string.theme_indigo))
        add(ThemePreset.Ink to stringResource(Res.string.theme_ink))
        if (dynamicSupported) add(ThemePreset.Dynamic to stringResource(Res.string.theme_dynamic))
        add(ThemePreset.Custom to stringResource(Res.string.theme_custom))
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        presets.forEach { (preset, label) ->
          val previewScheme = if (preset == ThemePreset.Dynamic) {
            platformDynamicColorScheme(previewDark) ?: colorSchemeFor(
              preset,
              previewDark,
              prefs.customThemeHue,
              prefs.customThemeSaturation,
              prefs.customThemeLightness,
              prefs.customThemeSecondary,
              prefs.customThemeTertiary
            )
          } else colorSchemeFor(
            preset,
            previewDark,
            prefs.customThemeHue,
            prefs.customThemeSaturation,
            prefs.customThemeLightness,
            prefs.customThemeSecondary,
            prefs.customThemeTertiary
          )
          ThemeSwatchChip(
            label = label,
            scheme = previewScheme,
            isSelected = selectedPreset == preset,
            onClick = { scope.launch { repo.setThemePreset(preset.key) } }
          )
        }
      }

      AnimatedVisibility(visible = selectedPreset == ThemePreset.Custom) {
        CustomThemePalettePicker(
          prefs = prefs,
          dark = previewDark,
          onColorSelected = { role, color ->
            scope.launch { repo.setCustomThemeAccent(role, color) }
          },
          onMatchAccents = { scope.launch { repo.resetCustomThemeAccents() } }
        )
      }

      Spacer(Modifier.height(4.dp))

      // Font
      Text(stringResource(Res.string.font_label), style = MaterialTheme.typography.titleSmall)
      var fontExpanded by remember { mutableStateOf(false) }
      val currentFontLabel = if (prefs.fontMode == "serif")
        stringResource(Res.string.font_serif)
      else
        stringResource(Res.string.font_sans)
      Box {
        OutlinedButton(onClick = { fontExpanded = true }, modifier = Modifier.fillMaxWidth()) {
          Text(currentFontLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(Res.string.font_sans)) },
            onClick = { fontExpanded = false; scope.launch { repo.setFontMode("sans") } }
          )
          DropdownMenuItem(
            text = { Text(stringResource(Res.string.font_serif)) },
            onClick = { fontExpanded = false; scope.launch { repo.setFontMode("serif") } }
          )
        }
      }

      Spacer(Modifier.height(4.dp))

      // Text Size
      Text(stringResource(Res.string.text_size), style = MaterialTheme.typography.titleSmall)
      var sliderScale by remember(prefs.textSizeScale) { mutableStateOf(prefs.textSizeScale) }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          "A",
          style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = MaterialTheme.typography.bodyMedium.fontSize * sliderScale
          )
        )
        Slider(
          value = sliderScale,
          onValueChange = { v ->
            val snapped = kotlin.math.round(v * 20f) / 20f
            sliderScale = snapped
            scope.launch { repo.setTextSizeScale(snapped) }
          },
          valueRange = 0.8f..1.6f,
          modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(
          "A",
          style = MaterialTheme.typography.titleLarge.copy(
            fontSize = MaterialTheme.typography.titleLarge.fontSize * sliderScale
          )
        )
      }
      Text(
        "${(sliderScale * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(Modifier.height(4.dp))

      // Scripture line spacing
      Text(stringResource(Res.string.ui_line_spacing), style = MaterialTheme.typography.titleSmall)
      var lineSpacing by remember(prefs.readingLineSpacing) {
        mutableStateOf(prefs.readingLineSpacing)
      }
      Slider(
        value = lineSpacing,
        onValueChange = { value ->
          val snapped = kotlin.math.round(value * 20f) / 20f
          lineSpacing = snapped
          scope.launch { repo.setReadingLineSpacing(snapped) }
        },
        valueRange = 1.3f..2.1f,
        modifier = Modifier.fillMaxWidth()
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          stringResource(Res.string.ui_verse_per_line),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f)
        )
        Switch(
          checked = prefs.versePerLine,
          onCheckedChange = { enabled -> scope.launch { repo.setVersePerLine(enabled) } }
        )
      }

      Spacer(Modifier.height(4.dp))

      // Jesus' words color
      Text(
        text = stringResource(Res.string.pref_jesus_words_color_title),
        style = MaterialTheme.typography.titleSmall
      )

      var jesusColorExpanded by remember { mutableStateOf(false) }

      val jesusColorOptions: List<Pair<String, StringResource>> = listOf(
        "default" to Res.string.color_default,
        "red"     to Res.string.color_red,
        "orange"  to Res.string.color_orange,
        "yellow"  to Res.string.color_yellow,
        "green"   to Res.string.color_green,
        "blue"    to Res.string.color_blue,
        "indigo"  to Res.string.color_indigo,
        "purple"  to Res.string.color_purple
      )

      val currentJesusColorKey = prefs.jesusWordsColor.lowercase()
      val currentJesusColorLabel = jesusColorOptions
        .firstOrNull { it.first == currentJesusColorKey }
        ?.second
        ?.let { stringResource(it) }
        ?: stringResource(Res.string.color_default)

      Box {
        OutlinedButton(onClick = { jesusColorExpanded = true }, modifier = Modifier.fillMaxWidth()) {
          Text(currentJesusColorLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = jesusColorExpanded, onDismissRequest = { jesusColorExpanded = false }) {
          jesusColorOptions.forEach { (key, resId) ->
            DropdownMenuItem(
              text = { Text(stringResource(resId)) },
              onClick = { jesusColorExpanded = false; scope.launch { repo.setJesusWordsColor(key) } }
            )
          }
        }
      }

      Spacer(Modifier.height(4.dp))

      // Divine Name rendering
      Text(
        text = stringResource(Res.string.pref_divine_name_title),
        style = MaterialTheme.typography.titleSmall
      )
      Text(
        text = stringResource(Res.string.pref_divine_name_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      val divineNameOptions = listOf(
        "traditional" to stringResource(Res.string.divine_name_traditional),
        "yhwh"        to stringResource(Res.string.divine_name_yhwh),
        "yhvh"        to stringResource(Res.string.divine_name_yhvh),
        "yahweh"      to stringResource(Res.string.divine_name_yahweh)
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        divineNameOptions.forEach { (key, label) ->
          FilterChip(
            selected = prefs.divineName == key,
            onClick = { scope.launch { repo.setDivineName(key) } },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
          )
        }
      }

      Spacer(Modifier.height(4.dp))

      // Divine Name highlight color
      Text(
        text = stringResource(Res.string.pref_divine_name_color_title),
        style = MaterialTheme.typography.titleSmall
      )

      var dnColorExpanded by remember { mutableStateOf(false) }

      val dnColorOptions: List<Pair<String, StringResource>> = listOf(
        "default" to Res.string.color_default,
        "red"     to Res.string.color_red,
        "orange"  to Res.string.color_orange,
        "yellow"  to Res.string.color_yellow,
        "green"   to Res.string.color_green,
        "blue"    to Res.string.color_blue,
        "indigo"  to Res.string.color_indigo,
        "purple"  to Res.string.color_purple
      )

      val currentDnColorKey = prefs.divineNameColor.lowercase()
      val currentDnColorLabel = dnColorOptions
        .firstOrNull { it.first == currentDnColorKey }
        ?.second
        ?.let { stringResource(it) }
        ?: stringResource(Res.string.color_default)

      Box {
        OutlinedButton(onClick = { dnColorExpanded = true }, modifier = Modifier.fillMaxWidth()) {
          Text(currentDnColorLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = dnColorExpanded, onDismissRequest = { dnColorExpanded = false }) {
          dnColorOptions.forEach { (key, resId) ->
            DropdownMenuItem(
              text = { Text(stringResource(resId)) },
              onClick = { dnColorExpanded = false; scope.launch { repo.setDivineNameColor(key) } }
            )
          }
        }
      }

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      // ─── CONTENT SECTION ───
      SectionHeader(stringResource(Res.string.content_section))

      // Language
      Text(stringResource(Res.string.language), style = MaterialTheme.typography.titleSmall)
      var langExpanded by remember { mutableStateOf(prefs.screenshotExpandLanguage) }
      val languageOptions = listOf(
        "system" to stringResource(Res.string.language_system),
        "en" to "English",
        "es" to "Espa\u00F1ol",
        "zh-Hans" to "\u4E2D\u6587(\u7B80\u4F53)",
        "zh-Hant" to "\u4E2D\u6587(\u7E41\u9AD4)",
        "ja" to "\u65E5\u672C\u8A9E",
        "fr" to "Fran\u00E7ais",
        "it" to "Italiano",
        "ru" to "\u0420\u0443\u0441\u0441\u043A\u0438\u0439",
        "pt" to "Portugu\u00EAs",
        "de" to "Deutsch",
        "ko" to "\uD55C\uAD6D\uC5B4",
        "hi" to "\u0939\u093F\u0928\u094D\u0926\u0940",
        "ar" to "\u0627\u0644\u0639\u0631\u0628\u064A\u0629"
      )
      val currentLangLabel = languageOptions.firstOrNull { it.first == prefs.appLanguage }?.second
        ?: stringResource(Res.string.language_system)

      Box {
        OutlinedButton(onClick = { langExpanded = true }, modifier = Modifier.fillMaxWidth()) {
          Text(currentLangLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
          languageOptions.forEach { (code, label) ->
            DropdownMenuItem(
              text = { Text(label) },
              onClick = {
                langExpanded = false
                val prevLang = prefs.appLanguage
                scope.launch {
                  repo.setAppLanguage(code)

                  val eff = LocaleUtils.effectiveAssetTag(code)
                  val langKey =
                    if (eff.startsWith("zh")) {
                      if (eff.contains("hant", ignoreCase = true)) "zh-Hant" else "zh-Hans"
                    } else eff.substringBefore('-')

                  val youList = versionsByLang[langKey] ?: versionsByLang["en"].orEmpty()
                  val bgList  = bgVersionsByLang[langKey] ?: bgVersionsByLang["en"].orEmpty()

                  val current = prefs.translation
                  if (prefs.readerMode != "internal") {
                    if (prefs.readerMode == "biblecom") {
                      if (current !in youList) {
                        repo.setVersion(Linker.defaultVersionForLanguage(eff))
                      }
                    } else {
                      if (current !in bgList) {
                        repo.setVersion(Linker.defaultGatewayVersionForLanguage(eff))
                      }
                    }
                  }

                  // AppCompat recreates Android activities when the locale
                  // changes. Do not request a second explicit recreation.
                  if (code != prevLang) {
                    platformSetAppLocale(code)
                  }
                }
              }
            )
          }
        }
      }

      Spacer(Modifier.height(4.dp))

      val languageKey = run {
        val eff = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
        if (eff.startsWith("zh")) {
          if (eff.contains("hant", ignoreCase = true)) "zh-Hant" else "zh-Hans"
        } else eff.substringBefore('-')
      }

      val isInternal = prefs.readerMode == "internal"
      val versionChoices = if (isInternal) emptyList()
        else if (prefs.readerMode == "biblecom")
          (versionsByLang[languageKey] ?: versionsByLang["en"].orEmpty())
        else
          (bgVersionsByLang[languageKey] ?: bgVersionsByLang["en"].orEmpty())

      Text(stringResource(Res.string.preferred_reader), style = MaterialTheme.typography.titleSmall)
      Text(stringResource(Res.string.preferred_reader_subtitle), style = MaterialTheme.typography.bodySmall)
      val readerModes = listOf("internal", "biblecom", "biblegateway")
      val readerLabels = listOf(
        stringResource(Res.string.reader_internal),
        stringResource(Res.string.reader_biblecom),
        stringResource(Res.string.reader_biblegateway)
      )
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        readerModes.forEachIndexed { idx, mode ->
          SegmentedButton(
            selected = prefs.readerMode == mode,
            onClick = {
              if (prefs.readerMode != mode) {
                scope.launch {
                  repo.setReaderMode(mode)
                  if (mode != "internal") {
                    val eff = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
                    val lk = if (eff.startsWith("zh")) {
                      if (eff.contains("hant", ignoreCase = true)) "zh-Hant" else "zh-Hans"
                    } else eff.substringBefore('-')
                    val list = if (mode == "biblecom") versionsByLang[lk] ?: versionsByLang["en"].orEmpty()
                               else bgVersionsByLang[lk] ?: bgVersionsByLang["en"].orEmpty()
                    if (prefs.translation !in list) {
                      repo.setVersion(
                        if (mode == "biblecom") Linker.defaultVersionForLanguage(eff)
                        else Linker.defaultGatewayVersionForLanguage(eff)
                      )
                    }
                  }
                }
              }
            },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3)
          ) { Text(readerLabels[idx], maxLines = 1, style = MaterialTheme.typography.labelSmall) }
        }
      }

      AnimatedVisibility(visible = isInternal) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = stringResource(Res.string.in_app_bible_version),
            style = MaterialTheme.typography.titleSmall
          )
          Text(
            text = stringResource(Res.string.in_app_bible_version_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          var internalVersionExpanded by remember { mutableStateOf(false) }
          val defaultInternalEdition = BibleEditions.defaultForLanguage(prefs.appLanguage)
          val internalChoices = BibleEditions.available(prefs.appLanguage).map { id ->
            id to when (id) {
              BibleEditions.BSB -> stringResource(Res.string.version_bsb)
              BibleEditions.KJV_1769 -> stringResource(Res.string.version_kjv)
              defaultInternalEdition -> stringResource(Res.string.version_local_modern)
              else -> stringResource(Res.string.version_local_traditional)
            }
          }
          val selectedInternalLabel = internalChoices
            .firstOrNull { it.first == BibleEditions.selectedForLanguage(prefs.appLanguage, prefs.internalBibleVersion) }
            ?.second ?: internalChoices.first().second
          Box {
            OutlinedButton(
              onClick = { internalVersionExpanded = true },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(selectedInternalLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(
              expanded = internalVersionExpanded,
              onDismissRequest = { internalVersionExpanded = false }
            ) {
              internalChoices.forEach { (id, label) ->
                DropdownMenuItem(
                  text = { Text(label) },
                  onClick = {
                    internalVersionExpanded = false
                    scope.launch { repo.setInternalBibleVersion(id) }
                  }
                )
              }
            }
          }
        }
      }

      AnimatedVisibility(visible = !isInternal) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = stringResource(Res.string.external_bible_version_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = stringResource(Res.string.external_bible_version),
              style = MaterialTheme.typography.titleSmall,
              modifier = Modifier.weight(1f)
            )
            Text(
              text = "${versionChoices.size}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          var verExpanded by remember { mutableStateOf(false) }
          val selectedExternalVersion = Linker.selectedVersionForReader(
            currentVersion = prefs.translation,
            readerMode = prefs.readerMode,
            appLanguage = prefs.appLanguage
          ) ?: prefs.translation
          Box {
            OutlinedButton(onClick = { verExpanded = true }, modifier = Modifier.fillMaxWidth()) {
              Text(selectedExternalVersion, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = verExpanded, onDismissRequest = { verExpanded = false }) {
              versionChoices.forEach { v ->
                DropdownMenuItem(
                  text = { Text(v) },
                  onClick = { verExpanded = false; scope.launch { repo.setVersion(v) } }
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(4.dp))

      // Collections
      Text(stringResource(Res.string.collections), style = MaterialTheme.typography.titleSmall)
      SettingsSwitch(stringResource(Res.string.pseudepigrapha), prefs.showPseudepigrapha) {
        scope.launch { repo.setPseudepigrapha(it) }
      }
      SettingsSwitch(stringResource(Res.string.deuterocanonical), prefs.showDeutero) {
        scope.launch { repo.setDeutero(it) }
      }
      SettingsSwitch(stringResource(Res.string.apocrypha), prefs.showApoc) {
        scope.launch { repo.setApoc(it) }
      }

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      // ─── SEARCH SECTION ───
      SectionHeader(stringResource(Res.string.search_section))
      SettingsSwitch(stringResource(Res.string.ai_search), prefs.aiSearch) {
        scope.launch { repo.setAiSearch(it) }
      }
      Text(
        stringResource(Res.string.ai_search_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      // Daily notifications remain between Search and Accessibility.
      SectionHeader(stringResource(Res.string.daily_notification_title))
      SettingsSwitch(stringResource(Res.string.daily_notification_toggle), prefs.dailyVerseNotifications) { enabled ->
        if (!notificationPermissionPending) {
          if (!enabled) {
            DailyVerseNotificationBridge.clearPermissionRequestFeedback()
            scope.launch { repo.setDailyVerseNotifications(false) }
          } else {
            notificationPermissionPending = true
            DailyVerseNotificationBridge.requestPermission { granted ->
              notificationPermissionPending = false
              if (granted) scope.launch { repo.setDailyVerseNotifications(true) }
            }
          }
        }
      }
      Text(stringResource(Res.string.daily_notification_desc), style = MaterialTheme.typography.bodySmall)
      TextButton(onClick = { showNotificationTime = true }) {
        val minutes = prefs.dailyVerseNotificationMinuteOfDay.coerceIn(0, 1439)
        val displayTime = (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')
        Text(stringResource(Res.string.daily_notification_time) + ": " + displayTime)
      }
      if (isApplePlatform) {
        Text(stringResource(Res.string.daily_notification_ios_window), style = MaterialTheme.typography.bodySmall)
      }
      if (notificationPermissionDenied || (prefs.dailyVerseNotifications && notificationAllowed == false)) {
        Text(stringResource(Res.string.daily_notification_permission_denied), style = MaterialTheme.typography.bodySmall)
      }
      if (notificationPermissionDenied || prefs.dailyVerseNotifications) {
        TextButton(onClick = { DailyVerseNotificationBridge.openSettings() }) {
          Text(stringResource(Res.string.daily_notification_system_settings))
        }
      }
      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      // ─── ACCESSIBILITY SECTION ───
      SectionHeader(stringResource(Res.string.accessibility_section))
      SettingsSwitch(stringResource(Res.string.haptic_feedback), prefs.hapticEnabled) {
        scope.launch { repo.setHapticEnabled(it) }
      }
      SettingsSwitch(stringResource(Res.string.expand_notes_default), prefs.expandNotesDefault) {
        scope.launch { repo.setExpandNotesDefault(it) }
      }

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      // ─── TEXT-TO-SPEECH SECTION ───
      SectionHeader(stringResource(Res.string.tts_section))
      SettingsSwitch(stringResource(Res.string.tts_auto_continue), prefs.autoContinueTts) {
        scope.launch { repo.setAutoContinueTts(it) }
      }
      SettingsSwitch(stringResource(Res.string.tts_cross_book), prefs.crossBookTts) {
        scope.launch { repo.setCrossBookTts(it) }
      }
      SettingsSwitch(stringResource(Res.string.tts_read_intros), prefs.ttsReadIntros) {
        scope.launch { repo.setTtsReadIntros(it) }
      }

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      // ─── DATA SECTION ───
      SectionHeader(stringResource(Res.string.data_section))
      Text(
        stringResource(Res.string.data_section_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(8.dp))

      var exportResult by remember { mutableStateOf<String?>(null) }
      var importResult by remember { mutableStateOf<String?>(null) }
      var showImportDialog by remember { mutableStateOf(false) }
      var importText by remember { mutableStateOf("") }

      val copiedMsg = stringResource(Res.string.copied_to_clipboard)
      val appNameText = stringResource(Res.string.app_name)
      val successMsg = stringResource(Res.string.import_success)
      val errorMsg = stringResource(Res.string.import_error)

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
          scope.launch {
            val backup = repo.exportBackup()
            platformCopyToClipboard(ctx, appNameText, backup)
            exportResult = copiedMsg
          }
        }) {
          Text(stringResource(Res.string.export_data))
        }
        OutlinedButton(onClick = { showImportDialog = true }) {
          Text(stringResource(Res.string.import_data))
        }
      }

      exportResult?.let { msg ->
        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        LaunchedEffect(msg) { delay(3000); exportResult = null }
      }
      importResult?.let { msg ->
        Text(msg, style = MaterialTheme.typography.bodySmall,
          color = if (msg == successMsg) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.error)
        LaunchedEffect(msg) { delay(3000); importResult = null }
      }

      if (showImportDialog) {
        AlertDialog(
          onDismissRequest = { showImportDialog = false },
          title = { Text(stringResource(Res.string.import_data)) },
          text = {
            OutlinedTextField(
              value = importText,
              onValueChange = { importText = it },
              modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
              placeholder = { Text(stringResource(Res.string.import_paste_hint)) }
            )
          },
          confirmButton = {
            TextButton(onClick = {
              scope.launch {
                val ok = repo.importBackup(importText)
                importResult = if (ok) successMsg else errorMsg
                showImportDialog = false
                importText = ""
              }
            }) { Text(stringResource(Res.string.import_confirm)) }
          },
          dismissButton = {
            TextButton(onClick = { showImportDialog = false; importText = "" }) {
              Text(stringResource(Res.string.cancel))
            }
          }
        )
      }

      HorizontalDivider(Modifier.padding(vertical = 8.dp))

      // ─── SUPPORT SECTION ───
      SectionHeader(stringResource(Res.string.support))

      if (!isApplePlatform) {
        Text(stringResource(Res.string.donation), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.donation_text))
        OutlinedButton(
          onClick = { platformOpenUrl(ctx, "https://paypal.me/domvgreco") }
        ) { Text(stringResource(Res.string.donate_button)) }
        Text(stringResource(Res.string.donate_message), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
      }

      OutlinedButton(
        onClick = {
          val url = if (isApplePlatform)
            "https://apps.apple.com/us/app/bible-companion-offline/id6763134690"
          else
            "https://play.google.com/store/apps/details?id=com.dividesbyzer0.biblecompanion"
          platformOpenUrl(ctx, url)
        }
      ) {
        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.rate_app))
      }

      val shareAppSubject = stringResource(Res.string.app_name)
      OutlinedButton(
        onClick = {
          platformShareText(
            ctx,
            shareAppSubject,
            "https://wordinlight.org/biblecompanion"
          )
        }
      ) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.share_app))
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(bottom = 4.dp)
  )
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

/**
 * A miniature native-page preview using the same color scheme as the app.
 * All visual marks are decoration, not sample Scripture or progress data.
 */
@Composable
private fun ThemeSwatchChip(
  label: String,
  scheme: androidx.compose.material3.ColorScheme,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
  val borderWidth = if (isSelected) 2.dp else 1.dp
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    tonalElevation = if (isSelected) 2.dp else 0.dp,
    border = BorderStroke(borderWidth, borderColor),
    modifier = Modifier.width(136.dp).semantics { selected = isSelected }
  ) {
    Column(
      Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(82.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(scheme.surface)
      ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(scheme.primary))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).height(5.dp).background(scheme.onSurface.copy(alpha = 0.65f), RoundedCornerShape(3.dp)))
          }
          Box(Modifier.fillMaxWidth().height(22.dp).background(scheme.primaryContainer, RoundedCornerShape(6.dp)))
          Box(Modifier.fillMaxWidth(0.9f).height(4.dp).background(scheme.onSurface.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
          Box(Modifier.fillMaxWidth(0.7f).height(4.dp).background(scheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
        }
      }
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

// -------------------- ABOUT Screen ------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
  val ctx = LocalPlatformContext.current
  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(Res.string.about_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
          }
        }
      )
    }
  ) { pad ->
    Column(
      Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.height(8.dp))

      // App icon — uses the launcher foreground artwork.
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(96.dp)
      ) {
        Image(
          painter = painterResource(Res.drawable.app_icon),
          contentDescription = null,
          modifier = Modifier.fillMaxSize()
        )
      }

      Text(
        stringResource(Res.string.app_name),
        style = MaterialTheme.typography.headlineSmall
      )

      val versionText = remember(ctx) {
        val v = platformAppVersion(ctx)
        val b = platformAppBuild(ctx)
        if (v.isBlank()) "" else if (b.isBlank()) v else "$v ($b)"
      }
      if (versionText.isNotBlank()) {
        Text(
          stringResource(Res.string.version_label, versionText),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(Modifier.height(8.dp))

      // Content
      SelectionContainer {
        Column(
          Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalAlignment = Alignment.Start
        ) {
          Text(stringResource(Res.string.about_what_title), style = MaterialTheme.typography.titleMedium)
          Text(stringResource(Res.string.about_what_text))
          Text(stringResource(Res.string.about_features_text))
          Text(
            stringResource(Res.string.about_mission_text),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
          )
          Text(
            stringResource(Res.string.about_free_text),
            style = MaterialTheme.typography.titleSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      // Rate button — Play Store on Android, App Store on iOS
      OutlinedButton(
        onClick = {
          val url = if (isApplePlatform)
            "https://apps.apple.com/us/app/bible-companion-offline/id6763134690"
          else
            "https://play.google.com/store/apps/details?id=com.dividesbyzer0.biblecompanion"
          platformOpenUrl(ctx, url)
        }
      ) {
        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.rate_app))
      }

      // Share app button
      val shareAppSubject = stringResource(Res.string.app_name)
      OutlinedButton(
        onClick = {
          platformShareText(
            ctx,
            shareAppSubject,
            "https://wordinlight.org/biblecompanion"
          )
        }
      ) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.share_app))
      }
    }
  }
}

// -------------------- Generic Notes Screen (replaces 10 identical screens) ------------------
private fun splitMarkdownSections(body: String, headingPrefix: String = "## "): List<Pair<String?, String>> {
  if (body.isBlank()) return listOf(null to body)
  val lines = body.replace("\r\n", "\n").split('\n')
  val sections = mutableListOf<Pair<String?, StringBuilder>>()
  var current: Pair<String?, StringBuilder> = null to StringBuilder()
  // Build the "too deep" prefix to exclude sub-headings (e.g. "### " when splitting on "## ")
  val tooDeep = "$headingPrefix#"
  for (line in lines) {
    if (line.startsWith(headingPrefix) && !line.startsWith(tooDeep)) {
      if (current.second.isNotEmpty() || current.first != null) sections.add(current.first to current.second)
      current = line.removePrefix(headingPrefix).trim() to StringBuilder()
    } else {
      if (current.second.isNotEmpty()) current.second.append('\n')
      current.second.append(line)
    }
  }
  if (current.second.isNotEmpty() || current.first != null) sections.add(current.first to current.second)
  return sections.map { it.first to it.second.toString() }
}

internal fun genericNotesPlainText(
  body: String,
  divineName: String,
  appLanguage: String,
  divineNameColorActive: Boolean
): String {
  fun transform(text: String): String = applyDivineName(
    text = text,
    mode = divineName,
    lang = appLanguage,
    colorActive = divineNameColorActive,
    collection = "old_testament"
  )

  fun isDisplayHeading(line: String): Boolean =
    line.startsWith("# ") ||
      line.startsWith("## ") ||
      line.startsWith("### ") ||
      line.startsWith("#### ")

  // H5/H6 and indented hash-prefixed lines render as body text. Protect their
  // literal hashes from markdownToPlainText's broader heading cleanup.
  val literalHash = '\uFDEF'
  fun protectLiteralHeading(text: String): String = text.lines().joinToString("\n") { line ->
    val hashCount = line.takeWhile { it == '#' }.length
    if (hashCount in 1..6 && line.getOrNull(hashCount)?.isWhitespace() == true) {
      literalHash.toString().repeat(hashCount) + line.substring(hashCount)
    } else {
      line
    }
  }

  val lines = stripMarkdownHtmlComments(body).replace("\r\n", "\n").split('\n')
  val displayBlocks = mutableListOf<String>()
  val tableSeparator = Regex("""^\|\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")
  var i = 0
  while (i < lines.size) {
    val raw = lines[i]
    when {
      isDisplayHeading(raw) || raw.trim() == "---" -> {
        displayBlocks += raw
        i++
      }
      raw.trimStart().startsWith(">") -> {
        val quoteLines = mutableListOf<String>()
        var j = i
        while (j < lines.size && lines[j].trimStart().startsWith(">")) {
          quoteLines += lines[j].trimStart().removePrefix(">").removePrefix(" ")
          j++
        }
        displayBlocks += transform(quoteLines.joinToString("\n"))
          .lines().joinToString("\n") { "> $it" }
        i = j
      }
      raw.trimStart().startsWith("- ") -> {
        val indent = raw.takeWhile { it.isWhitespace() }
        val content = raw.trimStart().removePrefix("- ").trim()
        displayBlocks += "$indent- ${transform(content)}"
        i++
      }
      raw.trimStart().matches(Regex("""\d+[.)]\s+.*""")) -> {
        val indent = raw.takeWhile { it.isWhitespace() }
        displayBlocks += indent + transform(raw.trimStart().trim())
        i++
      }
      raw.trimStart().startsWith("|") && raw.contains("|") -> {
        var j = i
        while (j < lines.size && lines[j].trim().startsWith("|") && lines[j].contains("|")) {
          val tableLine = lines[j]
          displayBlocks += if (tableSeparator.matches(tableLine.trim())) {
            tableLine
          } else {
            tableLine.split('|').joinToString("|") { cell -> transform(cell) }
          }
          j++
        }
        i = j
      }
      raw.isNotBlank() -> {
        val paragraph = StringBuilder()
        var j = i
        while (
          j < lines.size &&
          lines[j].isNotBlank() &&
          lines[j].trim() != "---" &&
          (j == i || !isDisplayHeading(lines[j]))
        ) {
          if (paragraph.isNotEmpty()) {
            val previous = lines[j - 1]
            if (previous.length >= 2 && previous.endsWith("  ")) paragraph.append('\n')
            else paragraph.append(' ')
          }
          paragraph.append(lines[j].trim())
          j++
        }
        displayBlocks += protectLiteralHeading(transform(paragraph.toString()))
        i = j
      }
      else -> {
        displayBlocks += ""
        i++
      }
    }
  }

  return markdownToPlainText(displayBlocks.joinToString("\n"))
    .replace(literalHash, '#')
}

@Composable
private fun FullNoteSelectionDialog(
  noteText: String,
  onDismiss: () -> Unit,
  onCopyAll: () -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  var selectedValue by remember(noteText) {
    mutableStateOf(
      TextFieldValue(
        text = noteText,
        selection = TextRange(0, noteText.length)
      )
    )
  }

  LaunchedEffect(noteText) {
    withFrameNanos { }
    focusRequester.requestFocus()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.select_all)) },
    text = {
      BasicTextField(
        value = selectedValue,
        onValueChange = { next ->
          if (next.text == noteText) selectedValue = next
        },
        readOnly = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .focusRequester(focusRequester)
      )
    },
    confirmButton = {
      TextButton(onClick = onCopyAll) {
        Text(stringResource(Res.string.copy_all))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(Res.string.cancel))
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericNotesScreen(
  titleRes: StringResource,
  assetFileName: String,
  prefs: PrefsState,
  repo: PrefsRepo,
  collapsible: Boolean = false,
  headingPrefix: String = "## ",
  // false renders the note as one plain scrolling column with regular markdown
  // headings: no collapsible section cards and no jump-to-section dropdown in
  // the app bar.
  toc: Boolean = true,
  onBack: () -> Unit
) {
  val ctx = LocalPlatformContext.current
  val notesLanguage = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
  val notesKey = notesScreenKey(notesLanguage, assetFileName)
  var body by remember(notesKey) { mutableStateOf("") }

  LaunchedEffect(notesKey) {
    body = readAssetText(ctx, "notes/$notesLanguage/$assetFileName")
      ?: readAssetText(ctx, "notes/en/$assetFileName")
      ?: "\u2014"
  }

  val titleText = stringResource(titleRes)
  val fullNoteText = remember(body, prefs.divineName, prefs.divineNameColor, notesLanguage) {
    genericNotesPlainText(
      body = body,
      divineName = prefs.divineName,
      appLanguage = notesLanguage,
      divineNameColorActive = prefs.divineNameColor != "default"
    )
  }
  val sections = remember(body, headingPrefix) { splitMarkdownSections(body, headingPrefix) }
  val sectionHeaders = remember(sections) { sections.mapNotNull { it.first } }
  val showToc = toc && !collapsible && sectionHeaders.size >= 8

  var noteState by remember(notesKey) {
    mutableStateOf(LiveNotesScreenStates.read(prefs.notesExpandedSectionsJson, notesLanguage, assetFileName))
  }
  val tocListState = rememberSaveable(notesKey, saver = LazyListState.Saver) {
    LazyListState(noteState.firstVisibleItem.coerceAtLeast(0), noteState.firstVisibleOffset.coerceAtLeast(0))
  }
  val collapsibleListState = rememberSaveable(notesKey, saver = LazyListState.Saver) {
    LazyListState(noteState.firstVisibleItem.coerceAtLeast(0), noteState.firstVisibleOffset.coerceAtLeast(0))
  }
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val copiedMessage = stringResource(Res.string.copied_to_clipboard)
  var tocDropdownExpanded by remember { mutableStateOf(false) }

  var selectionResetKey by remember(notesKey) { mutableStateOf(0) }
  var showDismissButton by remember(notesKey) { mutableStateOf(false) }
  var showFullSelection by remember(notesKey) { mutableStateOf(false) }
  val nativeTextToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
  val nativeClipboard = androidx.compose.ui.platform.LocalClipboardManager.current
  val notesFocusManager = androidx.compose.ui.platform.LocalFocusManager.current

  fun resetNoteSelectionState() {
    showDismissButton = false
    selectionResetKey++
    notesFocusManager.clearFocus()
  }

  val noteToolbar = remember(notesKey, nativeTextToolbar, notesFocusManager) {
    NotesSelectionToolbar(
      delegate = nativeTextToolbar,
      onVisibilityChanged = { showDismissButton = it },
      onSelectWholeNote = {
        resetNoteSelectionState()
        showFullSelection = true
      },
      platformFinishesActionCallback = !isApplePlatform
    )
  }
  fun clearNoteSelection() {
    resetNoteSelectionState()
    noteToolbar.hide()
  }
  val noteClipboard = remember(notesKey, nativeClipboard, nativeTextToolbar, notesFocusManager) {
    NotesSelectionClipboard(nativeClipboard) { clearNoteSelection() }
  }
  DisposableEffect(noteToolbar) {
    onDispose { noteToolbar.hide() }
  }
  val noteContentSelectionModifier = Modifier.pointerInput(notesKey) {
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      // Native handles live in a popup. A new page gesture hides stale chrome;
      // an actual selection reopens it through the toolbar callback.
      showDismissButton = false
    }
  }

  fun copyAllNote() {
    platformCopyToClipboard(ctx, titleText, fullNoteText)
    clearNoteSelection()
    scope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      snackbarHostState.showSnackbar(copiedMessage, duration = SnackbarDuration.Short)
    }
  }

  fun saveNoteState(state: NotesScreenState) {
    LiveNotesScreenStates.record(notesLanguage, assetFileName, state)
    val payload = Json.encodeToString(state)
    // Start the short preference transaction before navigation disposes this
    // scope; finish it even when the user immediately leaves the page.
    scope.launch(NonCancellable, start = CoroutineStart.UNDISPATCHED) {
      repo.setNoteScreenState(notesLanguage, assetFileName, payload)
    }
  }
  fun updateNoteState(state: NotesScreenState) {
    noteState = state
    saveNoteState(state)
  }
  val activeListState = if (collapsible) collapsibleListState else tocListState
  LaunchedEffect(notesKey, body, collapsible, showToc) {
    if (body.isNotBlank() && (collapsible || showToc)) {
      snapshotFlow { activeListState.firstVisibleItemIndex to activeListState.firstVisibleItemScrollOffset }
        .distinctUntilChanged().collectLatest { (index, offset) ->
          delay(250)
          updateNoteState(noteState.copy(firstVisibleItem = index, firstVisibleOffset = offset))
        }
    }
  }
  DisposableEffect(notesKey, collapsible, showToc) {
    onDispose {
      if (body.isNotBlank() && (collapsible || showToc)) {
        saveNoteState(noteState.copy(firstVisibleItem = activeListState.firstVisibleItemIndex,
          firstVisibleOffset = activeListState.firstVisibleItemScrollOffset))
      }
    }
  }

  val currentSectionTitle = if (showToc) {
    val firstVisible = tocListState.firstVisibleItemIndex
    if (firstVisible < sections.size) sections[firstVisible].first ?: titleText else titleText
  } else titleText

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          if (showToc) {
            Box {
              Row(
                Modifier.clickable { tocDropdownExpanded = !tocDropdownExpanded },
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  currentSectionTitle,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                  if (tocDropdownExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp)
                )
              }
              DropdownMenu(
                expanded = tocDropdownExpanded,
                onDismissRequest = { tocDropdownExpanded = false }
              ) {
                sectionHeaders.forEach { header ->
                  val sectionItemIdx = sections.indexOfFirst { it.first == header }
                  DropdownMenuItem(
                    text = { Text(header, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                      tocDropdownExpanded = false
                      scope.launch { tocListState.animateScrollToItem(sectionItemIdx) }
                    }
                  )
                }
              }
            }
          } else {
            Text(titleText)
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
          }
        },
        actions = {
          if (fullNoteText.isNotBlank()) {
            IconButton(onClick = { clearNoteSelection(); showFullSelection = true }) {
              Icon(imageVector = Icons.Filled.SelectAll, contentDescription = stringResource(Res.string.select_all))
            }
            IconButton(onClick = { copyAllNote() }) {
              Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = stringResource(Res.string.copy_all))
            }
            IconButton(onClick = { platformShareText(ctx, titleText, fullNoteText) }) {
              Icon(imageVector = Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
            }
          }
        }
      )
    }
  ) { pad ->
    androidx.compose.runtime.CompositionLocalProvider(
      androidx.compose.ui.platform.LocalTextToolbar provides noteToolbar,
      androidx.compose.ui.platform.LocalClipboardManager provides noteClipboard
    ) {
    Box(Modifier.fillMaxSize()) {
    if (collapsible && sectionHeaders.size >= 2) {
      // Collapsible sections mode: each headed section is expandable
      LazyColumn(
        state = collapsibleListState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(pad).then(noteContentSelectionModifier)
      ) {
        items(
          count = sections.size,
          key = { idx -> sections[idx].first ?: "csection_$idx" }
        ) { idx ->
          val (header, sectionBody) = sections[idx]
          if (header != null) {
            val isExpanded = header in noteState.expandedHeaders
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
                    val next = if (isExpanded) noteState.expandedHeaders - header else noteState.expandedHeaders + header
                    updateNoteState(noteState.copy(expandedHeaders = next))
                  },
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    mdInline(header),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                  )
                  Icon(
                    if (isExpanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                  )
                }
                AnimatedVisibility(visible = isExpanded) {
                  val subSections = remember(sectionBody) { splitMarkdownSections(sectionBody, "### ") }
                  val hasSubHeadings = subSections.any { it.first != null }
                  if (hasSubHeadings) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                      for ((subIdx, sub) in subSections.withIndex()) {
                        val (subHeader, subBody) = sub
                        if (subHeader != null) {
                          val subKey = notesHeaderPath(header, subHeader)
                          key(subKey) {
                            val subExpanded = subKey in noteState.expandedPaths
                            Card(
                              modifier = Modifier.fillMaxWidth(),
                              shape = RoundedCornerShape(8.dp),
                              colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                              )
                            ) {
                              Column(Modifier.padding(10.dp)) {
                                Row(
                                  Modifier.fillMaxWidth().clickable {
                                    val next = if (subExpanded) noteState.expandedPaths - subKey else noteState.expandedPaths + subKey
                                    updateNoteState(noteState.copy(expandedPaths = next))
                                  },
                                  verticalAlignment = Alignment.CenterVertically
                                ) {
                                  Text(
                                    mdInline(subHeader),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                  )
                                  Icon(
                                    if (subExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                  )
                                }
                                if (subExpanded) {
                                  Column(Modifier.padding(top = 6.dp)) {
                                    val subSubSections = remember(subBody) { splitMarkdownSections(subBody, "#### ") }
                                    val hasSubSubHeadings = subSubSections.any { it.first != null }
                                    if (hasSubSubHeadings) {
                                      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        for ((ssIdx, ss) in subSubSections.withIndex()) {
                                          val (ssHeader, ssBody) = ss
                                          if (ssHeader != null) {
                                            val ssKey = notesHeaderPath(header, subHeader, ssHeader)
                                            key(ssKey) {
                                              val ssExpanded = ssKey in noteState.expandedPaths
                                              Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = CardDefaults.cardColors(
                                                  containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                )
                                              ) {
                                                Column(Modifier.padding(8.dp)) {
                                                  Row(
                                                    Modifier.fillMaxWidth().clickable {
                                                      val next = if (ssExpanded) noteState.expandedPaths - ssKey else noteState.expandedPaths + ssKey
                                                      updateNoteState(noteState.copy(expandedPaths = next))
                                                    },
                                                    verticalAlignment = Alignment.CenterVertically
                                                  ) {
                                                    Text(
                                                      mdInline(ssHeader),
                                                      style = MaterialTheme.typography.labelLarge,
                                                      modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                      if (ssExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                                      contentDescription = null,
                                                      modifier = Modifier.size(18.dp)
                                                    )
                                                  }
                                                  if (ssExpanded) {
                                                    Column(Modifier.padding(top = 4.dp)) {
                                                      RenderNotesMarkdown(body = ssBody, prefs = prefs, selectionResetKey = selectionResetKey)
                                                    }
                                                  }
                                                }
                                              }
                                            }
                                          } else if (ssBody.isNotBlank()) {
                                            key("$header/$subHeader/inline_$ssIdx") {
                                              RenderNotesMarkdown(body = ssBody, prefs = prefs, selectionResetKey = selectionResetKey)
                                            }
                                          }
                                        }
                                      }
                                    } else {
                                      RenderNotesMarkdown(body = subBody, prefs = prefs, selectionResetKey = selectionResetKey)
                                    }
                                  }
                                }
                              }
                            }
                          }
                        } else if (subBody.isNotBlank()) {
                          key("$header/inline_$subIdx") {
                            RenderNotesMarkdown(body = subBody, prefs = prefs, selectionResetKey = selectionResetKey)
                          }
                        }
                      }
                    }
                  } else {
                    Column(Modifier.padding(top = 8.dp)) {
                      RenderNotesMarkdown(body = sectionBody, prefs = prefs, selectionResetKey = selectionResetKey)
                    }
                  }
                }
              }
            }
          } else {
            // Preamble section (no header): render directly
            if (sectionBody.isNotBlank()) {
              RenderNotesMarkdown(body = sectionBody, prefs = prefs, selectionResetKey = selectionResetKey)
            }
          }
        }
      }
    } else if (!showToc) {
      Column(
        Modifier.padding(pad).then(noteContentSelectionModifier).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        RenderNotesMarkdown(body = body, prefs = prefs, selectionResetKey = selectionResetKey)
      }
    } else {
      LazyColumn(
        state = tocListState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(pad).then(noteContentSelectionModifier)
      ) {
        items(
          count = sections.size,
          key = { idx -> sections[idx].first ?: "section_$idx" }
        ) { idx ->
          val (header, sectionBody) = sections[idx]
          Column {
            if (header != null) {
              Text(
                mdInline(header),
                style = MaterialTheme.typography.titleLarge
              )
              Spacer(Modifier.height(8.dp))
            }
            RenderNotesMarkdown(body = sectionBody, prefs = prefs, selectionResetKey = selectionResetKey)
          }
        }
      }
    }
    AnimatedVisibility(
      visible = showDismissButton,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
    ) {
      SmallFloatingActionButton(
        onClick = { clearNoteSelection() },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
      ) {
        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.cancel), modifier = Modifier.size(18.dp))
      }
    }
    } // Box
    } // Selection toolbar provider
  }

  if (showFullSelection) {
    FullNoteSelectionDialog(
      noteText = fullNoteText,
      onDismiss = { showFullSelection = false },
      onCopyAll = {
        copyAllNote()
        showFullSelection = false
      }
    )
  }
}
