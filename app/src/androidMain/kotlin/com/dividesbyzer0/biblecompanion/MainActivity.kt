package com.dividesbyzer0.biblecompanion

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.os.LocaleListCompat
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.normalizeLocaleTagForCompare

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                if (route != null) route
                else {
                    val col = uri.getQueryParameter("col")
                    val book = uri.getQueryParameter("book")
                    val story = uri.getQueryParameter("story")
                    if (col != null && book != null) "book/$col/$book" + (if (story != null) "?storyId=$story" else "")
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
