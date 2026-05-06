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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.dividesbyzer0.biblecompanion.platform.platformCurrentDate
import com.dividesbyzer0.biblecompanion.platform.platformTtsInit
import com.dividesbyzer0.biblecompanion.platform.platformTtsSpeak
import com.dividesbyzer0.biblecompanion.platform.platformTtsStop
import com.dividesbyzer0.biblecompanion.platform.platformTtsIsSpeaking
import com.dividesbyzer0.biblecompanion.platform.platformTtsSetOnDone
import com.dividesbyzer0.biblecompanion.platform.platformTtsPause
import com.dividesbyzer0.biblecompanion.platform.platformTtsResume
import com.dividesbyzer0.biblecompanion.platform.platformTtsIsPaused
import com.dividesbyzer0.biblecompanion.platform.currentTimeMillis
import com.dividesbyzer0.biblecompanion.platform.platformSetAppLocale
import com.dividesbyzer0.biblecompanion.platform.platformDynamicColorScheme
import com.dividesbyzer0.biblecompanion.platform.platformRecreateApp
import com.dividesbyzer0.biblecompanion.platform.platformSupportsDynamicColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// --------------- App Root ----------------

@Composable
fun AppRoot(shortcutAction: String? = null, deepLinkRoute: String? = null) {
  val ctx = LocalPlatformContext.current
  val repo = remember { PrefsRepo(ctx) }
  val initialPrefs = remember { repo.initialSnapshot() }
  val prefs by repo.flow.collectAsState(initialPrefs)

  LaunchedEffect(prefs.appLanguage) {
    // Bulletproof: any asset-load throw here would surface up the Compose
    // runtime and crash the app on iOS, where we can't catch in Swift.
    // Off-main: book-alias loading reads many JSON files; previously caused
    // first-frame jank on launch.
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
  val resolvedScheme = dynamicScheme ?: colorSchemeFor(preset, dark, prefs.customThemeHue)

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
    val navBack: () -> Unit = {
      if (!nav.popBackStack()) nav.navigate(Dest.Home.route) {
        popUpTo(Dest.Home.route) { inclusive = true }
      }
    }

    LaunchedEffect(shortcutAction) {
      when (shortcutAction) {
        "search" -> {}
        "bookmarks" -> nav.navigate(Dest.SavedItems.route) { launchSingleTop = true }
        "feast_calendar" -> nav.navigate(Dest.FeastCalendar.route) { launchSingleTop = true }
        "continue" -> {
          val col = prefs.lastReadCollection
          val bookId = prefs.lastReadBookId
          if (col != null && bookId != null) {
            nav.navigate(Dest.BookView.route(col, bookId, prefs.lastReadStoryId)) { launchSingleTop = true }
          }
        }
      }
    }

    LaunchedEffect(deepLinkRoute) {
      if (deepLinkRoute != null) {
        nav.navigate(deepLinkRoute) { launchSingleTop = true }
      }
    }

    val internalNavigate: (String, String, String?, Int?, Int?) -> Unit = { col, bookId, storyId, verse, verseEnd ->
      nav.navigate(Dest.BookView.route(col, bookId, storyId, verse, verseEnd)) { launchSingleTop = true }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalInternalNavigate provides internalNavigate) {
    Box(
      Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      NavHost(navController = nav, startDestination = Dest.Home.route) {
        composable(Dest.Home.route) {
          HomeScreen(
            prefs = prefs,
            repo = repo,
            onOpen = { col -> nav.navigate(Dest.Books.route(col)) { launchSingleTop = true } },
            onOpenBook = { col, bookId, storyId, verse, verseEnd ->
              nav.navigate(Dest.BookView.route(col, bookId, storyId, verse, verseEnd)) { launchSingleTop = true }
            },
            onNavigateRoute = { route -> nav.navigate(route) { launchSingleTop = true } },
            onSettings = { nav.navigate(Dest.Settings.route) { launchSingleTop = true } },
            onGenealogy = { nav.navigate(Dest.Genealogy.route) { launchSingleTop = true } },
            onJesusDivinity = { nav.navigate(Dest.JesusDivinity.route) { launchSingleTop = true } },
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
            onFeastCalendar = { nav.navigate(Dest.FeastCalendar.route) { launchSingleTop = true } },
            onProphecy = { nav.navigate(Dest.Prophecy.route) { launchSingleTop = true } },
            onAbout = { nav.navigate(Dest.About.route) { launchSingleTop = true } },
            onSavedItems = { nav.navigate(Dest.SavedItems.route) { launchSingleTop = true } }
          )
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
              nav.navigate(Dest.BookView.route(col, bookId, storyId)) { launchSingleTop = true }
            }
          )
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
        composable(Dest.Genealogy.route) {
          GenealogyScreen(prefs = prefs, onBack = { navBack() })
        }
        composable(Dest.FeastCalendar.route) {
          FeastCalendarScreen(prefs = prefs, repo = repo, onBack = { navBack() })
        }
        composable(Dest.Prophecy.route) {
          ProphecyMenuScreen(
            onBack = { navBack() },
            onMessianic = { nav.navigate(Dest.MessianicProphecy.route) { launchSingleTop = true } },
            onDaniel = { nav.navigate(Dest.DanielsTimeline.route) { launchSingleTop = true } },
            onAstronomical = { nav.navigate(Dest.AstronomicalSigns.route) { launchSingleTop = true } },
            onRevelation = { nav.navigate(Dest.RevelationOverview.route) { launchSingleTop = true } }
          )
        }
        composable(Dest.MessianicProphecy.route) {
          GenericNotesScreen(Res.string.prophecy_messianic, "messianic_prophecy.md", prefs, repo) { navBack() }
        }
        composable(Dest.DanielsTimeline.route) {
          GenericNotesScreen(Res.string.prophecy_daniel, "daniels_timeline.md", prefs, repo) { navBack() }
        }
        composable(Dest.AstronomicalSigns.route) {
          GenericNotesScreen(Res.string.prophecy_astronomical, "astronomical_signs.md", prefs, repo) { navBack() }
        }
        composable(Dest.RevelationOverview.route) {
          GenericNotesScreen(Res.string.prophecy_revelation, "revelation_overview.md", prefs, repo) { navBack() }
        }
        composable("books/{col}") { back ->
          val col = back.arguments?.getString("col") ?: "old_testament"
          BooksScreen(
            col = col,
            appLanguage = prefs.appLanguage,
            onBack = { navBack() },
            onOpenBook = { bookId ->
              nav.navigate(Dest.BookView.route(col, bookId)) { launchSingleTop = true }
            }
          )
        }
        composable(
          route = "book/{col}/{bookId}?storyId={storyId}&verse={verse}&verseEnd={verseEnd}&autoStartTts={autoStartTts}",
          arguments = listOf(
            navArgument("storyId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("verse") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("verseEnd") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("autoStartTts") { type = NavType.StringType; nullable = true; defaultValue = null }
          )
        ) { back ->
          val col = back.arguments?.getString("col") ?: return@composable
          val bookId = back.arguments?.getString("bookId") ?: return@composable
          val storyIdArg = back.arguments?.getString("storyId")
          val verseArg = back.arguments?.getString("verse")?.toIntOrNull()
          val verseEndArg = back.arguments?.getString("verseEnd")?.toIntOrNull()
          val autoStartTtsArg = back.arguments?.getString("autoStartTts") == "true"
          BookScreen(
            col = col,
            bookId = bookId,
            prefs = prefs,
            repo = repo,
            initialStoryId = storyIdArg,
            initialVerse = verseArg,
            initialVerseEnd = verseEndArg,
            autoStartTts = autoStartTtsArg,
            onNavigateToBook = { nextCol, nextBookId, startTts ->
              nav.navigate(Dest.BookView.route(nextCol, nextBookId, autoStartTts = startTts)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  prefs: PrefsState,
  repo: PrefsRepo,
  onOpen: (String) -> Unit,
  onOpenBook: (String, String, String?, Int?, Int?) -> Unit,
  onNavigateRoute: (String) -> Unit,
  onSettings: () -> Unit,
  onGenealogy: () -> Unit,
  onJesusDivinity: () -> Unit,
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
  onFeastCalendar: () -> Unit,
  onProphecy: () -> Unit,
  onAbout: () -> Unit,
  onSavedItems: () -> Unit = {}
) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()

  // Onboarding
  var showOnboarding by remember { mutableStateOf(!prefs.onboardingComplete) }

  var navBusy by remember { mutableStateOf(false) }
  fun safeNav(action: () -> Unit) {
    if (navBusy) return
    navBusy = true
    action()
    scope.launch { delay(350); navBusy = false }
  }

  var query by remember { mutableStateOf("") }
  var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
  var showSheet by remember { mutableStateOf(false) }
  var studyExpanded by remember(prefs.studyPinned) { mutableStateOf(prefs.studyPinned) }
  var searchJob by remember { mutableStateOf<Job?>(null) }
  var searchInFlight by remember { mutableStateOf(false) }
  var searchGen by remember { mutableStateOf(0) }
  var indexReady by remember(prefs.appLanguage) { mutableStateOf(StorySearch.isReady(prefs.appLanguage)) }

  LaunchedEffect(prefs.appLanguage) {
    if (!StorySearch.isReady(prefs.appLanguage)) {
      withContext(Dispatchers.Default) {
        runCatching { StorySearch.ensureBuilt(ctx, prefs.appLanguage) }
      }
      indexReady = StorySearch.isReady(prefs.appLanguage)
    } else {
      indexReady = true
    }
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
      Column(
        Modifier
          .padding(pad)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
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
                try {
                  delay(120)
                  if (!StorySearch.isReady(prefs.appLanguage)) {
                    withContext(Dispatchers.Default) {
                      runCatching { StorySearch.ensureBuilt(ctx, prefs.appLanguage) }
                    }
                    indexReady = StorySearch.isReady(prefs.appLanguage)
                  }
                  val hits = withContext(Dispatchers.Default) {
                    StorySearch.search(q)
                  }
                  if (gen == searchGen) results = hits
                } finally {
                  if (gen == searchGen) searchInFlight = false
                }
              }
            } else {
              searchInFlight = false
              searchGen++
              results = emptyList()
            }
          },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text(stringResource(Res.string.search_placeholder)) },
          singleLine = true,
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

        // Search results with highlighted keywords
        if (showSheet && results.isEmpty()) {
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
        if (showSheet && results.isNotEmpty()) {
          Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
              results.forEach { hit ->
                ListItem(
                  headlineContent = { Text(hit.title, fontWeight = FontWeight.Medium) },
                  supportingContent = {
                    when (hit.type) {
                      SearchHitType.BOOK -> Text(hit.snippet)
                      else -> Text(highlightSearchSnippet(hit.snippet, query))
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

        // Verse of the Day — keyed on language so switching UI language refreshes
        val votd = remember(prefs.appLanguage) {
          VerseOfTheDay.todayVerse(ctx, prefs.appLanguage)
        }
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
        if (!votdDismissed) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = {
                    platformShareText(ctx, votd.ref, "\u201C${votd.text}\u201D\n\u2014 ${votd.ref}")
                  }
                ) {
                  Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(Res.string.share),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
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
                      platformTtsSpeak(ctx, "${votd.text} ${votd.ref}", lang)
                      ttsPlaying = true
                    }
                  },
                ) {
                  Icon(
                    if (ttsPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (ttsPlaying) stringResource(Res.string.cd_tts_stop) else stringResource(Res.string.cd_tts_play),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
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
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
            Spacer(Modifier.height(4.dp))
            Text(
              "\u201C${votd.text}\u201D",
              style = MaterialTheme.typography.bodyMedium,
              fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
              color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            ScriptureRefs.ClickableRefsTextSmart(
              text = "\u2014 ${votd.ref}",
              prefs = prefs,
              modifier = Modifier.padding(top = 4.dp),
              textStyle = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
              )
            )
          }
        }
        }

        // Continue Reading card
        val lastCol = prefs.lastReadCollection
        val lastBook = prefs.lastReadBookId
        if (lastBook != null && lastCol != null) {
          ElevatedCard(
            onClick = {
              if (!navBusy) safeNav {
                onOpenBook(lastCol, lastBook, prefs.lastReadStoryId, null, null)
              }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer
            )
          ) {
            Row(
              Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
              )
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(
                  stringResource(Res.string.continue_reading),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                  prefs.lastReadBookTitle ?: lastBook,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }

        // Bookmarks & Saved Verses
        val bookmarks by repo.bookmarksFlow.collectAsState(initial = emptyList())
        val savedVerses by repo.savedVersesFlow.collectAsState(initial = emptyList())
        if (bookmarks.isNotEmpty() || savedVerses.isNotEmpty()) {
          ElevatedCard(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !navBusy) {
              safeNav { onSavedItems() }
            },
            colors = CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
          ) {
            Row(
              Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
              )
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(
                  stringResource(Res.string.saved_items),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                  "${bookmarks.size} bookmarks \u2022 ${savedVerses.size} verses",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
              }
            }
          }
        }

        // Old/New Testament buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          HomeWideButton(text = stringResource(Res.string.old_testament), modifier = Modifier.weight(1f), enabled = !navBusy) {
            safeNav { onOpen("old_testament") }
          }
          HomeWideButton(text = stringResource(Res.string.new_testament), modifier = Modifier.weight(1f), enabled = !navBusy) {
            safeNav { onOpen("new_testament") }
          }
        }

        // Extra collections (conditional)
        val hasExtras = prefs.showPseudepigrapha || prefs.showDeutero || prefs.showApoc
        if (hasExtras) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (prefs.showPseudepigrapha) {
              HomePill(text = stringResource(Res.string.pseudepigrapha), modifier = Modifier.weight(1f), enabled = !navBusy) {
                safeNav { onOpen("pseudepigrapha") }
              }
            } else Spacer(Modifier.weight(1f))
            if (prefs.showDeutero) {
              HomePill(text = stringResource(Res.string.deuterocanonical), modifier = Modifier.weight(1f), enabled = !navBusy) {
                safeNav { onOpen("deuterocanonical") }
              }
            } else Spacer(Modifier.weight(1f))
            if (prefs.showApoc) {
              HomePill(text = stringResource(Res.string.apocrypha), modifier = Modifier.weight(1f), enabled = !navBusy) {
                safeNav { onOpen("apocrypha") }
              }
            } else Spacer(Modifier.weight(1f))
          }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Study & Reference section - expandable
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
              IconButton(
                onClick = {
                  val next = !prefs.studyPinned
                  if (next) studyExpanded = true
                  scope.launch { repo.setStudyPinned(next) }
                }
              ) {
                Icon(
                  imageVector = Icons.Filled.PushPin,
                  contentDescription = stringResource(
                    if (prefs.studyPinned) Res.string.study_unpin else Res.string.study_pin
                  ),
                  tint = if (prefs.studyPinned) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
              Column(Modifier.padding(bottom = 8.dp)) {
                StudyItem(stringResource(Res.string.genealogy), !navBusy) { safeNav { onGenealogy() } }
                StudyItem(stringResource(Res.string.jesus_divinity), !navBusy) { safeNav { onJesusDivinity() } }
                StudyItem(stringResource(Res.string.grace), !navBusy) { safeNav { onGrace() } }
                StudyItem(stringResource(Res.string.prophecy), !navBusy) { safeNav { onProphecy() } }
                StudyItem(stringResource(Res.string.feast_calendar), !navBusy) { safeNav { onFeastCalendar() } }
                StudyItem(stringResource(Res.string.christian_symbolism), !navBusy) { safeNav { onChristianSymbolism() } }
                StudyItem(stringResource(Res.string.unseen_war), !navBusy) { safeNav { onUnseenWar() } }
                StudyItem(stringResource(Res.string.false_doctrine), !navBusy) { safeNav { onFalseDoctrine() } }
                StudyItem(stringResource(Res.string.common_distortions), !navBusy) { safeNav { onCommonDistortions() } }
                StudyItem(stringResource(Res.string.christophanies), !navBusy) { safeNav { onChristophanies() } }
                StudyItem(stringResource(Res.string.translation_notes), !navBusy) { safeNav { onTranslationNotes() } }
                StudyItem(stringResource(Res.string.historical_awareness), !navBusy) { safeNav { onHistoricalAwareness() } }
                StudyItem(stringResource(Res.string.bible_canon), !navBusy) { safeNav { onBibleCanon() } }
                StudyItem(stringResource(Res.string.faqs), !navBusy) { safeNav { onFaqs() } }
              }
            }
          }
        }

        Spacer(Modifier.height(4.dp))

        // About
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          TextButton(onClick = { safeNav { onAbout() } }, enabled = !navBusy) {
            Text(stringResource(Res.string.about_title), style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun StudyItem(text: String, enabled: Boolean, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      Icons.Filled.Star,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    )
    Spacer(Modifier.width(12.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
  }
}

/** Annotated string that bolds [[highlighted]] spans from search snippets. */
@Composable
private fun highlightSearchSnippet(snippet: String, query: String): AnnotatedString {
  return buildAnnotatedString {
    val cleaned = snippet.replace("[[", "").replace("]]", "")
    val lcCleaned = cleaned.lowercase()
    val lcQuery = query.lowercase().trim()

    if (lcQuery.length < 2) {
      append(cleaned)
      return@buildAnnotatedString
    }

    var cursor = 0
    val tokens = lcQuery.split(Regex("\\s+")).filter { it.length >= 2 }

    // Find all match ranges
    data class Range(val start: Int, val end: Int)
    val ranges = mutableListOf<Range>()
    for (tok in tokens) {
      var idx = lcCleaned.indexOf(tok)
      while (idx >= 0) {
        ranges.add(Range(idx, idx + tok.length))
        idx = lcCleaned.indexOf(tok, idx + 1)
      }
    }
    ranges.sortBy { it.start }

    // Merge overlapping ranges
    val merged = mutableListOf<Range>()
    for (r in ranges) {
      if (merged.isEmpty() || r.start > merged.last().end) merged.add(r)
      else merged[merged.lastIndex] = Range(merged.last().start, maxOf(merged.last().end, r.end))
    }

    for (m in merged) {
      if (cursor < m.start) append(cleaned.substring(cursor, m.start))
      withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
        append(cleaned.substring(m.start, m.end))
      }
      cursor = m.end
    }
    if (cursor < cleaned.length) append(cleaned.substring(cursor))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
  col: String,
  appLanguage: String,
  onBack: () -> Unit,
  onOpenBook: (String) -> Unit
) {
  val ctx = LocalPlatformContext.current

  val (regularList, gnosticList) = if (col == "apocrypha") {
    remember(col, appLanguage) { ContentRepo.listApocryphaSectionsLocalized(ctx, appLanguage) }
  } else {
    remember(col, appLanguage) { ContentRepo.listBooksLocalized(ctx, col, appLanguage) } to emptyList()
  }

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
      LazyColumn(Modifier.padding(pad)) {
        items(regularList) { pair: Pair<String, String> ->
          val (id, title) = pair
          if (id.isBlank()) {
            ListItem(
              headlineContent = {
                Text(
                  title,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            )
          } else {
            ListItem(
              headlineContent = { Text(title) },
              modifier = Modifier.clickable { onOpenBook(id) }
            )
          }
          HorizontalDivider()
        }

        if (gnosticList.isNotEmpty()) {
          item {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(thickness = 1.dp)
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 16.dp)) {
              Text(
                stringResource(Res.string.gnostic_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            HorizontalDivider(thickness = 1.dp)
          }
          items(gnosticList) { pair: Pair<String, String> ->
            val (id, title) = pair
            if (id.isBlank()) {
              ListItem(headlineContent = {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              })
            } else {
              ListItem(headlineContent = { Text(title) }, modifier = Modifier.clickable { onOpenBook(id) })
            }
            HorizontalDivider()
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BookScreen(
  col: String,
  bookId: String,
  prefs: PrefsState,
  repo: PrefsRepo,
  initialStoryId: String?,
  initialVerse: Int? = null,
  initialVerseEnd: Int? = null,
  autoStartTts: Boolean = false,
  onNavigateToBook: ((col: String, bookId: String, autoStartTts: Boolean) -> Unit)? = null,
  onBack: () -> Unit
) {
  val ctx = LocalPlatformContext.current
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  val doHaptic = { if (prefs.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

  val book = remember(col, bookId, prefs.appLanguage) {
    ContentRepo.loadBookOrNull(ctx, col, bookId, prefs.appLanguage)
  }

  val index = remember(book) { book?.let { ChapterLocator.build(it) } }
  val storyIndex = remember(book) { book?.stories?.mapIndexed { i, s -> s.id to i }?.toMap().orEmpty() }

  val listState = rememberLazyListState()

  // Per-story section visibility overrides (ephemeral; resets on navigation)
  val sectionOverrides = remember { mutableStateMapOf<String, Boolean>() }

  val nextBook = remember(col, bookId, prefs.appLanguage) {
    val books = ContentRepo.listBooksLocalized(ctx, col, prefs.appLanguage)
    val idx = books.indexOfFirst { it.first == bookId }
    if (idx >= 0 && idx + 1 < books.size) books[idx + 1] else null
  }

  // Verse selection state: Set of (storyId, bulletIndex)
  var selectedBullets by remember { mutableStateOf(setOf<Pair<String, Int>>()) }

  val bookKey = "$col/$bookId"
  var expandedStoryIds by remember(book, prefs.collapsedStoriesJson) {
    val allIds = book?.stories?.map { it.id }?.toSet() ?: emptySet()
    val collapsed = runCatching {
      val root = Json.parseToJsonElement(prefs.collapsedStoriesJson).jsonObject
      root[bookKey]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
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

  LaunchedEffect(autoStartTts) {
    if (autoStartTts && book != null && book.stories.isNotEmpty()) {
      delay(400)
      chapterTtsStoryId = book.stories.first().id
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

    // Scroll to the chapter being read
    listState.animateScrollToItem(storyIdx)

    val text = ttsBuildChapterText(story, prefs.appLanguage)

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
          onNavigateToBook(col, nextBook.first, true)
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

  // Save reading progress
  LaunchedEffect(book) {
    if (book != null) {
      val titlesMap = ContentRepo.listBooksLocalized(ctx, col, prefs.appLanguage).toMap()
      val title = titlesMap[bookId] ?: book.title
      repo.setLastRead(col, bookId, title, initialStoryId)
    }
  }

  LaunchedEffect(initialStoryId, initialVerse, storyIndex, book) {
    if (!initialStoryId.isNullOrBlank()) {
      if (initialStoryId !in expandedStoryIds) {
        expandedStoryIds = expandedStoryIds + initialStoryId
      }
      val storyIdx = storyIndex[initialStoryId]
      if (initialVerse != null && book != null) {
        val story = book.stories.find { it.id == initialStoryId }
        if (story != null) {
          val end = initialVerseEnd?.coerceAtLeast(initialVerse) ?: initialVerse
          val bullets = findBulletsForVerseRange(story.summaryBullets, initialVerse, end)
          if (bullets.isNotEmpty()) {
            goldFadeStoryId = initialStoryId
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

  // Track scroll position to update continue-reading target
  LaunchedEffect(book, listState) {
    if (book == null) return@LaunchedEffect
    val titlesMap = ContentRepo.listBooksLocalized(ctx, col, prefs.appLanguage).toMap()
    val title = titlesMap[bookId] ?: book.title
    snapshotFlow { listState.firstVisibleItemIndex }
      .distinctUntilChanged()
      .collectLatest { idx ->
        delay(500)
        val storyId = book.stories.getOrNull(idx)?.id
        repo.setLastRead(col, bookId, title, storyId)
      }
  }

  // Bookmarks & saved verses
  val bookmarks by repo.bookmarksFlow.collectAsState(initial = emptyList())
  val savedVerses by repo.savedVersesFlow.collectAsState(initial = emptyList())
  val labels by repo.labelsFlow.collectAsState(initial = emptyList())
  val bookmarkedStoryIds = remember(bookmarks, col, bookId) {
    bookmarks.filter { it.collection == col && it.bookId == bookId }.map { it.storyId }.toSet()
  }
  val savedVerseMap = remember(savedVerses, col, bookId) {
    savedVerses.filter { it.collection == col && it.bookId == bookId }
      .groupBy { it.storyId }
      .mapValues { (_, list) -> list.associate { it.bulletIndex to it.highlightColor } }
  }

  var showChapters by remember { mutableStateOf(false) }
  var selectedChapter by remember { mutableStateOf<Int?>(null) }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          val titlesMap = remember(col, prefs.appLanguage) {
            ContentRepo.listBooksLocalized(ctx, col, prefs.appLanguage).toMap()
          }
          val titleText = titlesMap[bookId] ?: book?.title
            ?: stringResource(Res.string.books_heading_generic)

          val canToggle = book != null && index != null

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
                .clickable(enabled = canToggle) {
                  showChapters = !showChapters
                  if (!showChapters) selectedChapter = null
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .alpha(if (didPulse) 1f else pulse.value),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                titleText,
                style = MaterialTheme.typography.titleMedium
              )
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
        navigationIcon = {
          IconButton(onClick = {
            if (chapterTtsPlaying) { platformTtsStop(ctx); chapterTtsPlaying = false; chapterTtsStoryId = null; chapterTtsPaused = false }
            onBack()
          }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
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
        val needsDcWarning = col == "deuterocanonical" &&
                !Linker.hasApocryphaSupport(prefs.translation, effLang)
        val dcCandidates = remember(effLang) {
          Linker.apocryphaCandidates(effLang)
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
            AnimatedVisibility(visible = showChapters && index != null) {
              val byChapter = (index ?: return@AnimatedVisibility).byChapter
              val maxChapter = byChapter.keys.maxOrNull() ?: 0
              val vScroll = rememberScrollState()
              val selCh = selectedChapter

              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 0.dp, max = 300.dp)
                  .padding(horizontal = 12.dp, vertical = 8.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                  if (selCh != null) {
                    IconButton(onClick = { selectedChapter = null }) {
                      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                  }
                  Text(
                    if (selCh != null) stringResource(Res.string.verses_label) else stringResource(Res.string.chapters_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                  )
                }

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(vScroll)
                ) {
                  FlowRow(
                    maxItemsInEachRow = 6,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    if (selCh != null) {
                      val exact = byChapter[selCh]
                      val fallbackKey = if (exact == null) byChapter.keys.filter { it <= selCh }.maxOrNull() else null
                      val chStoryId = exact ?: (fallbackKey?.let { byChapter[it] })
                      val chStory = chStoryId?.let { sid -> book?.stories?.find { it.id == sid } }
                      val verseNums = chStory?.summaryBullets?.mapNotNull { bullet ->
                        verseRefPattern.find(bullet)?.let { m ->
                          val ch = m.groupValues[1].toIntOrNull()
                          val v = m.groupValues[2].toIntOrNull()
                          if (ch == selCh && v != null) v else null
                        }
                      }?.distinct()?.sorted() ?: emptyList()
                      for (v in verseNums) {
                        ElevatedButton(
                          onClick = {
                            chStoryId?.let { sid ->
                              if (sid !in expandedStoryIds) expandedStoryIds = expandedStoryIds + sid
                              val bullets = findBulletsForVerseRange(chStory?.summaryBullets ?: emptyList(), v, v)
                              val firstBullet = bullets.minOrNull() ?: 0
                              val approxOffset = firstBullet * 200 + 150
                              if (bullets.isNotEmpty()) {
                                goldFadeStoryId = sid
                                goldFadeBulletIdxs = bullets
                              }
                              showChapters = false
                              selectedChapter = null
                              storyIndex[sid]?.let { idx -> scope.launch { listState.scrollToItem(idx, approxOffset) } }
                            }
                          },
                          modifier = Modifier.size(48.dp),
                          contentPadding = PaddingValues(0.dp),
                          shape = RoundedCornerShape(8.dp)
                        ) { Text("$v") }
                      }
                    } else if (maxChapter > 0) {
                      for (c in 1..maxChapter) {
                        ElevatedButton(
                          onClick = { selectedChapter = c },
                          modifier = Modifier.size(48.dp),
                          contentPadding = PaddingValues(0.dp),
                          shape = RoundedCornerShape(8.dp)
                        ) { Text("$c") }
                      }
                    }
                  }
                }
              }
            }

            var viewportTopY by remember { mutableFloatStateOf(0f) }
            var viewportHeightPx by remember { mutableStateOf(0) }
            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp,
                bottom = if (selectedBullets.isNotEmpty()) 80.dp else 16.dp
              ),
              verticalArrangement = Arrangement.spacedBy(24.dp),
              modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coords ->
                  viewportTopY = coords.positionInRoot().y
                  viewportHeightPx = coords.size.height
                }
            ) {
              items(items = book.stories, key = { it.id }) { story ->
                val storySelected = remember(selectedBullets, story.id) {
                  selectedBullets.filter { it.first == story.id }.map { it.second }.toSet()
                }
                val isTtsActive = chapterTtsPlaying && chapterTtsStoryId == story.id
                val isTtsPausedHere = isTtsActive && chapterTtsPaused
                StoryCard(
                  col = col,
                  listState = listState,
                  viewportTopY = viewportTopY,
                  viewportHeightPx = viewportHeightPx,
                  story = story,
                  prefs = prefs,
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
                      if (collapsed.isEmpty()) root.remove(bookKey)
                      else root[bookKey] = JsonArray(collapsed.map { JsonPrimitive(it) })
                      repo.setCollapsedStories(JsonObject(root).toString())
                    }
                  },
                  onToggleBookmark = {
                    doHaptic()
                    scope.launch {
                      if (story.id in bookmarkedStoryIds) {
                        val prior = bookmarks.firstOrNull {
                          it.collection == col && it.bookId == bookId && it.storyId == story.id
                        }
                        repo.removeBookmark(col, bookId, story.id)
                        if (prior != null) {
                          scope.launch {
                            showUndo(bookmarkRemovedMsg, undoActionLabel) {
                              repo.addBookmark(prior)
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
                          snippet = story.summaryBullets.firstOrNull()?.take(80) ?: "",
                          timestamp = currentTimeMillis()
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
                      Linker.bestLinkForRef(it, prefs.translation, prefs.appLanguage).second
                    }
                    val shareText = if (url != null) "${content.text}\n\n$url" else content.text
                    platformCopyToClipboard(ctx, content.primaryRef ?: "Verse", shareText)
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
                    onClick = { onNavigateToBook(col, nextBook.first, false) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                  ) {
                    Text(stringResource(Res.string.continue_to_book, nextBook.second))
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
                    platformCopyToClipboard(ctx, content.primaryRef ?: "Verses", content.text)
                    selectedBullets = emptySet()
                  }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(Res.string.share))
                  }
                  // Share
                  IconButton(onClick = {
                    doHaptic()
                    val content = buildSelectedContent(book, selectedBullets)
                    val url = content.primaryRef?.let {
                      Linker.bestLinkForRef(it, prefs.translation, prefs.appLanguage).second
                    }
                    val shareText = if (url != null) "${content.text}\n\n$url" else content.text
                    platformShareText(ctx, content.primaryRef ?: "Verses", shareText)
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
                          savedVerses.firstOrNull {
                            it.collection == col && it.bookId == bookId &&
                              it.storyId == sid && it.bulletIndex == idx
                          }
                        }
                        for ((sid, idx) in selectedBullets) {
                          repo.removeSavedVerse(col, bookId, sid, idx)
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
                          val ref = story.refs.firstOrNull() ?: ""
                          repo.addSavedVerse(SavedVerse(
                            collection = col,
                            bookId = bookId,
                            storyId = sid,
                            bulletIndex = idx,
                            text = bulletText,
                            ref = ref,
                            highlightColor = null,
                            timestamp = currentTimeMillis()
                          ))
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
                              if (savedVerseMap[sid]?.containsKey(idx) == true) {
                                repo.updateVerseHighlight(col, bookId, sid, idx, hlKey)
                              } else {
                                val story = book.stories.find { it.id == sid } ?: continue
                                val bulletText = story.summaryBullets.getOrNull(idx) ?: continue
                                val ref = story.refs.firstOrNull() ?: ""
                                repo.addSavedVerse(SavedVerse(
                                  collection = col, bookId = bookId, storyId = sid,
                                  bulletIndex = idx, text = bulletText, ref = ref,
                                  highlightColor = hlKey,
                                  timestamp = currentTimeMillis()
                                ))
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
                            val c = savedVerseMap[sid]?.get(idx)
                            if (c != null) Triple(sid, idx, c) else null
                          }
                          for ((sid, idx) in selectedBullets) {
                            repo.updateVerseHighlight(col, bookId, sid, idx, null)
                          }
                          selectedBullets = emptySet()
                          showColors = false
                          if (prior.isNotEmpty()) {
                            scope.launch {
                              showUndo(highlightClearedMsg, undoActionLabel) {
                                for ((sid, idx, c) in prior) {
                                  repo.updateVerseHighlight(col, bookId, sid, idx, c)
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
                                val ref = story.refs.firstOrNull() ?: ""
                                repo.addSavedVerse(SavedVerse(
                                  collection = col, bookId = bookId, storyId = sid,
                                  bulletIndex = idx, text = bulletText, ref = ref,
                                  timestamp = currentTimeMillis()
                                ))
                                repo.addLabelToVerse(col, bookId, sid, idx, lbl.id)
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

// -------------------------------------- Story cards ------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryCard(
  col: String,
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
  onCopyBullet: ((Int) -> Unit)? = null
) {
  val ctx = LocalPlatformContext.current

  val defaultBook: String? = remember(story.refs) {
    story.refs.firstOrNull()?.let { ScriptureRefs.canonBookOfRef(it) }
  }

  val sharePlain = remember(story, col, prefs.translation, prefs.appLanguage) {
    val md = buildStoryMarkdown(story)
    val plain = markdownToPlainText(md)
    val url = story.refs.firstOrNull()?.let { ref ->
      Linker.bestLinkForRef(ref, prefs.translation, prefs.appLanguage).second
    }
    val bookIdPart = story.id.substringBeforeLast('-')
    val deepLink = "biblecompanion://open?col=$col&book=$bookIdPart&story=${story.id}"
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
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
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
              prefs = prefs
            )
          }
        }

        // Collapsible content
        if (hasCollapsibleContent) {
          AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              // Summary bullets — tappable for verse selection
              if (story.summaryBullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  val firstGoldIdx = goldFadeBulletIdxs.minOrNull()
                  val vpHeightState = rememberUpdatedState(viewportHeightPx)
                  val vpTopState = rememberUpdatedState(viewportTopY)
                  story.summaryBullets.forEachIndexed { idx, bullet ->
                    val isSelected = idx in selectedBullets
                    val highlightColor = savedVerseColors[idx]
                    val isGoldTarget = idx in goldFadeBulletIdxs
                    val isScrollAnchor = isGoldTarget && idx == firstGoldIdx
                    val goldAlpha = remember { Animatable(0f) }
                    var bulletRootY by remember { mutableStateOf<Float?>(null) }
                    LaunchedEffect(isGoldTarget, isScrollAnchor) {
                      if (isGoldTarget) {
                        if (isScrollAnchor && listState != null) {
                          // Wait for expand animation + lazy composition so the bullet's
                          // on-screen position is final, then teleport (no animation) to it.
                          delay(160)
                          val vp = vpHeightState.value
                          val top = vpTopState.value
                          val by0 = bulletRootY
                          if (vp > 0 && by0 != null) {
                            val bulletInVp = by0 - top
                            val targetInVp = vp * 0.22f
                            val delta = bulletInVp - targetInVp
                            if (kotlin.math.abs(delta) > 2f) {
                              runCatching { listState.scrollBy(delta) }
                            }
                          }
                        }
                        // Brief beat so the user's eye lands on the row before the fade plays.
                        delay(80)
                        goldAlpha.snapTo(1f)
                        goldAlpha.animateTo(
                          0f,
                          animationSpec = tween(durationMillis = 2200, easing = LinearEasing)
                        )
                      } else {
                        goldAlpha.snapTo(0f)
                      }
                    }
                    val bgColor = when {
                      isSelected -> MaterialTheme.colorScheme.primaryContainer
                      goldAlpha.value > 0f -> Color(0xFFFFB300).copy(alpha = goldAlpha.value * 0.38f)
                      else -> highlightBgColor(highlightColor)
                    }
                    Row(
                      verticalAlignment = Alignment.Top,
                      modifier = Modifier
                        .onGloballyPositioned { coords ->
                          bulletRootY = coords.positionInRoot().y
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(bgColor)
                        .then(
                          if (onCopyBullet != null) Modifier.pointerInput(idx) {
                            awaitEachGesture {
                              awaitFirstDown(requireUnconsumed = false)
                              try {
                                withTimeout(500L) { waitForUpOrCancellation() }
                              } catch (_: PointerEventTimeoutCancellationException) {
                                onCopyBullet(idx)
                              }
                            }
                          } else Modifier
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                      Text(
                        "\u2022 ",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = if (onToggleBullet != null)
                          Modifier.clickable { onToggleBullet(idx) }
                        else Modifier
                      )
                      ScriptureRefs.ClickableRefsText(
                        text = bullet,
                        collection = col,
                        prefs = prefs,
                        defaultBook = defaultBook,
                        allowRelativeInParensOnly = true,
                        modifier = Modifier.weight(1f),
                        onNonLinkClick = if (onToggleBullet != null) {{ onToggleBullet(idx) }} else null
                      )
                    }
                  }
                }
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
                      allowRelativeInParensOnly = true
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
                          allowRelativeInParensOnly = true
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
                          textStyle = MaterialTheme.typography.titleSmall
                        )
                      }
                      SelectionContainer {
                        Text(
                          mv.text,
                          style = MaterialTheme.typography.bodyMedium
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
                          textStyle = MaterialTheme.typography.titleSmall
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
                          allowRelativeInParensOnly = true
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


private val verseRefPattern = Regex("""(\d+):(\d+)(?:\s*-\s*(\d+))?""")

private fun findBulletsForVerseRange(bullets: List<String>, startVerse: Int, endVerse: Int): Set<Int> {
  val out = linkedSetOf<Int>()
  for ((idx, bullet) in bullets.withIndex()) {
    for (match in verseRefPattern.findAll(bullet)) {
      val s = match.groupValues[2].toIntOrNull() ?: continue
      val e = match.groupValues[3].toIntOrNull() ?: s
      if (s <= endVerse && e >= startVerse) {
        out += idx
        break
      }
    }
  }
  return out
}

private data class SelectedContent(val text: String, val primaryRef: String?)

// Build the text + ref that represent ONLY the selected bullets.
//  - The header on each story's block is the specific verse-range reference
//    (e.g. "Matthew 28:18-19"), not the full chapter reference.
//  - primaryRef is the first story's specific reference, used to construct
//    a share URL that jumps straight to the selected verses.
//  - Falls back to the chapter reference / story title if the selected
//    bullets contain no parseable verse numbers.
private fun buildSelectedContent(book: Book, selected: Set<Pair<String, Int>>): SelectedContent {
  val grouped = selected.groupBy({ it.first }, { it.second }).mapValues { it.value.sorted() }
  val sb = StringBuilder()
  var primaryRef: String? = null
  val chapterRefTail = Regex(":\\s*\\d+(?:\\s*-\\s*\\d+)?\\s*$")

  for ((storyId, indices) in grouped) {
    val story = book.stories.find { it.id == storyId } ?: continue

    val verses = mutableListOf<Int>()
    for (idx in indices) {
      val bullet = story.summaryBullets.getOrNull(idx) ?: continue
      for (m in verseRefPattern.findAll(bullet)) {
        val s = m.groupValues[2].toIntOrNull() ?: continue
        val e = m.groupValues[3].toIntOrNull() ?: s
        verses += s
        verses += e
      }
    }
    val minV = verses.minOrNull()
    val maxV = verses.maxOrNull()

    val chapterRef = story.refs.firstOrNull()
    val specificRef = if (chapterRef != null && minV != null && maxV != null) {
      val bookAndChapter = chapterRef.replace(chapterRefTail, "")
      if (minV == maxV) "$bookAndChapter:$minV" else "$bookAndChapter:$minV-$maxV"
    } else chapterRef

    if (primaryRef == null) primaryRef = specificRef

    if (sb.isNotEmpty()) sb.appendLine()
    when {
      specificRef != null -> sb.appendLine(specificRef)
      else -> sb.appendLine(story.title)
    }
    for (idx in indices) {
      story.summaryBullets.getOrNull(idx)?.let {
        sb.appendLine(it.replace("[J]", "").replace("[/J]", ""))
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
  onOpenBook: (col: String, bookId: String, storyId: String?) -> Unit
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
            LazyColumn(
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(
                items = bookmarks.sortedByDescending { it.timestamp },
                key = { "${it.collection}/${it.bookId}/${it.storyId}" }
              ) { bm ->
                val bmColor = if (bm.collection == "old_testament" || bm.collection == "pseudepigrapha")
                  MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                Card(
                  modifier = Modifier.fillMaxWidth().clickable {
                    onOpenBook(bm.collection, bm.bookId, bm.storyId)
                  }
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
                    IconButton(onClick = {
                      scope.launch { repo.removeBookmark(bm.collection, bm.bookId, bm.storyId) }
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
                key = { "${it.collection}/${it.bookId}/${it.storyId}/${it.bulletIndex}" }
              ) { sv ->
                ReorderableItem(reorderState, key = "${sv.collection}/${sv.bookId}/${sv.storyId}/${sv.bulletIndex}") { isDragging ->
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
                      onOpenBook(sv.collection, sv.bookId, sv.storyId)
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
                          defaultBook = sv.bookId,
                          allowRelativeInParensOnly = true,
                          textStyle = MaterialTheme.typography.bodyMedium,
                          onNonLinkClick = { onOpenBook(sv.collection, sv.bookId, sv.storyId) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                          sv.ref,
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
                        scope.launch { repo.removeSavedVerse(sv.collection, sv.bookId, sv.storyId, sv.bulletIndex) }
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
                              .clickable { onOpenBook(sv.collection, sv.bookId, sv.storyId) }
                              .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Column(Modifier.weight(1f)) {
                              Text(sv.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                              Text(sv.ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                              scope.launch { repo.removeLabelFromVerse(sv.collection, sv.bookId, sv.storyId, sv.bulletIndex, lbl.id) }
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
private fun buildStoryMarkdown(story: Story): String = buildString {
  appendLine("# ${story.title}")
  if (story.refs.isNotEmpty()) {
    appendLine()
    story.refs.forEach { appendLine(it) }
  }
  if (story.summaryBullets.isNotEmpty()) {
    appendLine()
    story.summaryBullets.forEach { appendLine("- ${it.replace("[J]", "").replace("[/J]", "")}") }
  }
  if (story.keyTakeaway.isNotBlank()) {
    appendLine()
    appendLine("**Key takeaway:** ${story.keyTakeaway}")
  }
  if (story.crossRefs.isNotEmpty()) {
    appendLine()
    appendLine("**Cross references**")
    story.crossRefs.forEach { appendLine("- $it") }
  }
}.trimEnd()

// ---------------- TTS text cleaning ----------------

private val trailingVerseRefPattern = Regex(
  """\s*\(\s*\d+\s*:\s*\d+(?:\s*[-–]\s*\d+)?(?:\s*,\s*\d+\s*:\s*\d+(?:\s*[-–]\s*\d+)?)*\s*\)\s*\.?\s*$"""
)

private fun ttsCleanBullet(bullet: String): String {
  val stripped = bullet.replace("[J]", "").replace("[/J]", "").replace(trailingVerseRefPattern, "")
  val core = stripped.trimEnd(',', ';', ' ', '.', '—', '–', '-', '\t', ' ')
  return when {
    core.isEmpty() -> ""
    core.endsWith('?') || core.endsWith('!') -> core
    else -> "$core."
  }
}

private fun ttsOrdinalWord(n: Int, lang: String): String = when (lang) {
  "en" -> when (n) { 1 -> "First"; 2 -> "Second"; 3 -> "Third"; else -> n.toString() }
  "es" -> when (n) { 1 -> "Primera"; 2 -> "Segunda"; 3 -> "Tercera"; else -> n.toString() }
  "fr" -> when (n) { 1 -> "Première"; 2 -> "Deuxième"; 3 -> "Troisième"; else -> n.toString() }
  "de" -> when (n) { 1 -> "Erster"; 2 -> "Zweiter"; 3 -> "Dritter"; else -> n.toString() }
  "it" -> when (n) { 1 -> "Prima"; 2 -> "Seconda"; 3 -> "Terza"; else -> n.toString() }
  "pt" -> when (n) { 1 -> "Primeira"; 2 -> "Segunda"; 3 -> "Terceira"; else -> n.toString() }
  "ru" -> when (n) { 1 -> "Первое"; 2 -> "Второе"; 3 -> "Третье"; else -> n.toString() }
  else -> n.toString()
}

private fun ttsChapterWord(lang: String): String? = when (lang) {
  "en" -> "Chapter"
  "es" -> "Capítulo"
  "fr" -> "Chapitre"
  "de" -> "Kapitel"
  "it" -> "Capitolo"
  "pt" -> "Capítulo"
  "ru" -> "Глава"
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

private fun ttsBuildChapterText(story: Story, appLanguageTag: String): String = buildString {
  append(ttsTransformTitle(story.title, appLanguageTag))
  append(". ")
  story.summaryBullets.forEach { bullet ->
    val cleaned = ttsCleanBullet(bullet)
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
      "NR2006","NR1994","IRB20","DB1885","ICL00P","ICL00D","RDV24"
    ),
    "ru" to listOf(
      "RST","SYNO","NRT",
      "DROT","CSLAV","BTI","CARS","CARSA","CARST","CASS70","RSP","CAROS","ROT","RU167"
    ),
    "pt" to listOf(
      "NVI-PT","ARA","ARC","NVT","NAA","NTLH","A21","BLT","ONBV","VFL","NBV-P","MZNVI","RC60DO","TB","BPT09DC","AVM"
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
      "SAB","QNAV","AVDDV","FAOV","GOV","AR1665"
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
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        themeOptions.forEach { (key, label) ->
          FilterChip(
            selected = selectedThemeKey == key,
            onClick = { scope.launch { repo.setTheme(key) } },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
          val swatch = if (preset == ThemePreset.Custom) customThemeSwatch(prefs.customThemeHue, previewDark)
                       else swatchFor(preset, previewDark)
          ThemeSwatchChip(
            label = label,
            swatch = swatch,
            selected = selectedPreset == preset,
            onClick = { scope.launch { repo.setThemePreset(preset.key) } }
          )
        }
      }

      AnimatedVisibility(visible = selectedPreset == ThemePreset.Custom) {
        var hueSlider by remember(prefs.customThemeHue) { mutableStateOf(prefs.customThemeHue) }
        Column(Modifier.padding(top = 4.dp)) {
          Box(
            Modifier
              .fillMaxWidth()
              .height(24.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                  (0..360 step 30).map { h ->
                    Color.hsl(h.toFloat(), 0.6f, 0.5f)
                  }
                )
              )
          )
          Slider(
            value = hueSlider,
            onValueChange = { v ->
              hueSlider = v
              scope.launch { repo.setCustomThemeHue(v) }
            },
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth()
          )
        }
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
                        repo.setVersion(bgList.first())
                      }
                    }
                  }

                  platformSetAppLocale(code)
                  platformRecreateApp(ctx)
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
      val readerModes = listOf("biblecom", "biblegateway", "internal")
      val readerLabels = listOf(
        stringResource(Res.string.reader_biblecom),
        stringResource(Res.string.reader_biblegateway),
        stringResource(Res.string.reader_internal)
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
                      repo.setVersion(if (mode == "biblecom") Linker.defaultVersionForLanguage(eff) else list.first())
                    }
                  }
                }
              }
            },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3)
          ) { Text(readerLabels[idx], maxLines = 1, style = MaterialTheme.typography.labelSmall) }
        }
      }

      AnimatedVisibility(visible = !isInternal) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Spacer(Modifier.height(4.dp))
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = stringResource(Res.string.bible_version),
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
          Box {
            OutlinedButton(onClick = { verExpanded = true }, modifier = Modifier.fillMaxWidth()) {
              Text(prefs.translation, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * A theme preset chip: shows three stacked color swatches (primary + tertiary + surface)
 * above the preset name. The whole chip is clickable and outlined when selected.
 */
@Composable
private fun ThemeSwatchChip(
  label: String,
  swatch: ThemeSwatch,
  selected: Boolean,
  onClick: () -> Unit
) {
  val borderColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
  val borderWidth = if (selected) 2.dp else 1.dp
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    tonalElevation = if (selected) 2.dp else 0.dp,
    border = BorderStroke(borderWidth, borderColor),
    modifier = Modifier.width(108.dp)
  ) {
    Column(
      Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(36.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(swatch.surface)
      ) {
        Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(Modifier.size(14.dp).clip(CircleShape).background(swatch.primary))
          Box(Modifier.size(14.dp).clip(CircleShape).background(swatch.secondary))
        }
      }
      Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) MaterialTheme.colorScheme.primary
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenericNotesScreen(
  titleRes: StringResource,
  assetFileName: String,
  prefs: PrefsState,
  repo: PrefsRepo,
  collapsible: Boolean = false,
  headingPrefix: String = "## ",
  onBack: () -> Unit
) {
  val ctx = LocalPlatformContext.current
  var body by remember { mutableStateOf("") }

  LaunchedEffect(prefs.appLanguage) {
    val lang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
    body = readAssetText(ctx, "notes/$lang/$assetFileName")
      ?: readAssetText(ctx, "notes/en/$assetFileName")
      ?: "\u2014"
  }

  val titleText = stringResource(titleRes)
  val sections = remember(body, headingPrefix) { splitMarkdownSections(body, headingPrefix) }
  val sectionHeaders = remember(sections) { sections.mapNotNull { it.first } }
  val showToc = !collapsible && sectionHeaders.size >= 8

  val tocListState = rememberLazyListState()
  val collapsibleListState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var tocDropdownExpanded by remember { mutableStateOf(false) }

  // Track which collapsible sections are expanded, persisted per-file so the
  // user's last state is restored on return. The JSON shape is
  //   { "<assetFileName>": ["<sectionHeader>", ...], ... }
  // Keying by header text (not index) keeps the state correct when the
  // underlying notes file is edited.
  var expandedSections by remember(prefs.notesExpandedSectionsJson, sections) {
    val headers = sections.mapNotNull { it.first }.toSet()
    val persisted = runCatching {
      val root = Json.parseToJsonElement(prefs.notesExpandedSectionsJson).jsonObject
      root[assetFileName]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
    }.getOrDefault(emptySet())
    val resolved = sections
      .mapIndexedNotNull { idx, (header, _) ->
        if (header != null && header in persisted && header in headers) idx else null
      }
      .toSet()
    mutableStateOf(resolved)
  }

  var innerExpandedHeaders by remember { mutableStateOf<Set<String>>(emptySet()) }

  // Persist expanded-section state by header name (stable across edits).
  fun persistExpandedSections(newExpanded: Set<Int>) {
    val headerNames = newExpanded
      .mapNotNull { sections.getOrNull(it)?.first }
      .toList()
    val root = runCatching {
      Json.parseToJsonElement(prefs.notesExpandedSectionsJson).jsonObject.toMutableMap()
    }.getOrDefault(mutableMapOf())
    if (headerNames.isEmpty()) root.remove(assetFileName)
    else root[assetFileName] = JsonArray(headerNames.map { JsonPrimitive(it) })
    scope.launch { repo.setNotesExpandedSections(JsonObject(root).toString()) }
  }

  val currentSectionTitle = if (showToc) {
    val firstVisible = tocListState.firstVisibleItemIndex
    if (firstVisible < sections.size) sections[firstVisible].first ?: titleText else titleText
  } else titleText

  Scaffold(
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
          if (body.isNotBlank()) {
            IconButton(onClick = { platformCopyToClipboard(ctx, titleText, markdownToPlainText(body)) }) {
              Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = stringResource(Res.string.cd_copy))
            }
            IconButton(onClick = { platformShareText(ctx, titleText, markdownToPlainText(body)) }) {
              Icon(imageVector = Icons.Filled.Share, contentDescription = stringResource(Res.string.share))
            }
          }
        }
      )
    }
  ) { pad ->
    if (collapsible && sectionHeaders.size >= 2) {
      // Collapsible sections mode: each headed section is expandable
      LazyColumn(
        state = collapsibleListState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(pad)
      ) {
        items(
          count = sections.size,
          key = { idx -> sections[idx].first ?: "csection_$idx" }
        ) { idx ->
          val (header, sectionBody) = sections[idx]
          if (header != null) {
            val isExpanded = idx in expandedSections
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
                    val next = if (isExpanded) expandedSections - idx else expandedSections + idx
                    expandedSections = next
                    persistExpandedSections(next)
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
                      for (sub in subSections) {
                        val (subHeader, subBody) = sub
                        if (subHeader != null) {
                          val subKey = "$header/$subHeader"
                          val subExpanded = subKey in innerExpandedHeaders
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
                                  innerExpandedHeaders = if (subExpanded) innerExpandedHeaders - subKey else innerExpandedHeaders + subKey
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
                              AnimatedVisibility(visible = subExpanded) {
                                Column(Modifier.padding(top = 6.dp)) {
                                  RenderNotesMarkdown(body = subBody, prefs = prefs)
                                }
                              }
                            }
                          }
                        } else if (subBody.isNotBlank()) {
                          RenderNotesMarkdown(body = subBody, prefs = prefs)
                        }
                      }
                    }
                  } else {
                    Column(Modifier.padding(top = 8.dp)) {
                      RenderNotesMarkdown(body = sectionBody, prefs = prefs)
                    }
                  }
                }
              }
            }
          } else {
            // Preamble section (no header): render directly
            if (sectionBody.isNotBlank()) {
              RenderNotesMarkdown(body = sectionBody, prefs = prefs)
            }
          }
        }
      }
    } else if (!showToc) {
      Column(
        Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        RenderNotesMarkdown(body = body, prefs = prefs)
      }
    } else {
      LazyColumn(
        state = tocListState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(pad)
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
            RenderNotesMarkdown(body = sectionBody, prefs = prefs)
          }
        }
      }
    }
  }
}

