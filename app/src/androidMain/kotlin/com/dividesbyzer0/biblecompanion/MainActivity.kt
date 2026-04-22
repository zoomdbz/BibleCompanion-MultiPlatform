package com.dividesbyzer0.biblecompanion

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.os.LocaleListCompat
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = PrefsRepo(this)
        val init = repo.initialSnapshot()
        val locales = if (init.appLanguage == "system")
            LocaleListCompat.getEmptyLocaleList()
        else
            LocaleListCompat.forLanguageTags(init.appLanguage)
        AppCompatDelegate.setApplicationLocales(locales)

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
