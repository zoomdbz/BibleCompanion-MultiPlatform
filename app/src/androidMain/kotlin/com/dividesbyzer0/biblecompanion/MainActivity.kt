package com.dividesbyzer0.biblecompanion

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.normalizeLocaleTagForCompare

class MainActivity : AppCompatActivity() {
    private var notificationPermissionResult: ((Boolean) -> Unit)? = null
    private var notificationPermissionRequested = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val granted = DailyVerseNotificationScheduler.allowed(this)
        // Keep an explicit opt-in if the permission dialog outlives rotation.
        if (notificationPermissionRequested && granted) lifecycleScope.launch {
            PrefsRepo(applicationContext).setDailyVerseNotifications(true)
        }
        notificationPermissionRequested = false
        DailyVerseNotificationBridge.permissionRequestFinished(granted)
        notificationPermissionResult?.invoke(granted)
        notificationPermissionResult = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("daily_verse_permission_requested", notificationPermissionRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        DailyVerseNotificationBridge.refreshAfterResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermissionRequested = savedInstanceState?.getBoolean("daily_verse_permission_requested") ?: false
        enableEdgeToEdge()

        DailyVerseNotificationBridge.install(object : DailyVerseNotificationHost {
            override fun requestPermission(result: DailyVerseNotificationPermissionResult) {
                if (DailyVerseNotificationScheduler.allowed(this@MainActivity)) result.complete(true)
                else if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionResult = { result.complete(it) }
                    if (notificationPermissionRequested) return
                    notificationPermissionRequested = true
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else result.complete(false)
            }
            override fun synchronize(prefs: PrefsState) {
                DailyVerseNotificationScheduler.synchronize(applicationContext, prefs)
            }
            override fun checkPermission(result: DailyVerseNotificationPermissionResult) {
                result.complete(DailyVerseNotificationScheduler.allowed(this@MainActivity))
            }
            override fun openSettings() {
                val settings = if (Build.VERSION.SDK_INT >= 26)
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
                startActivity(settings)
            }
        })

        val repo = PrefsRepo(this)
        val init = repo.initialSnapshot()
        val resolved = when (init.appLanguage) {
            "system" -> null
            "zh-Hans" -> "zh-CN"
            "zh-Hant" -> "zh-TW"
            else -> init.appLanguage
        }
        val current = AppCompatDelegate.getApplicationLocales()
        val currentTags = if (current.isEmpty) null else current.toLanguageTags()
        // Compare normalized tags so a stored "en-US" doesn't force a relaunch
        // when our pref holds "en", and vice versa.
        if (normalizeLocaleTagForCompare(resolved) != normalizeLocaleTagForCompare(currentTags)) {
            val target = if (resolved == null)
                LocaleListCompat.getEmptyLocaleList()
            else
                LocaleListCompat.forLanguageTags(resolved)
            AppCompatDelegate.setApplicationLocales(target)
            return
        }

        val shortcutAction = when (intent?.action) {
            "com.dividesbyzer0.biblecompanion.SEARCH" -> "search"
            "com.dividesbyzer0.biblecompanion.BOOKMARKS" -> "bookmarks"
            "com.dividesbyzer0.biblecompanion.CONTINUE" -> "continue"
            "com.dividesbyzer0.biblecompanion.FEAST_CALENDAR" -> "feast_calendar"
            else -> null
        }

        val deepLinkRoute = intent?.data?.let { uri ->
            if (uri.scheme == "biblecompanion" && uri.host == "open") {
                val route = uri.getQueryParameter("route")
                if (route != null) {
                    if (route.startsWith("book/") && route.contains("storyId=") && !route.contains("sourceLang="))
                        route + (if (route.contains('?')) "&" else "?") + "sourceLang=en"
                    else route
                }
                else {
                    val col = uri.getQueryParameter("col")
                    val book = uri.getQueryParameter("book")
                    val story = uri.getQueryParameter("story")
                    val sourceLang = uri.getQueryParameter("sourceLang") ?: "en"
                    val verse = uri.getQueryParameter("verse")?.toIntOrNull()?.takeIf { it > 0 }
                    val verseEnd = uri.getQueryParameter("verseEnd")?.toIntOrNull()
                        ?.takeIf { verse != null && it >= verse }
                    if (col != null && book != null) Dest.BookView.route(
                        col, book, story, verse = verse, verseEnd = verseEnd, sourceLang = sourceLang,
                        sourceEdition = uri.getQueryParameter("sourceEdition")
                    )
                    else null
                }
            } else null
        }

        setContent {
            CompositionLocalProvider(LocalPlatformContext provides this@MainActivity) {
                AppRoot(shortcutAction = shortcutAction, deepLinkRoute = deepLinkRoute)
            }
        }
    }
}
