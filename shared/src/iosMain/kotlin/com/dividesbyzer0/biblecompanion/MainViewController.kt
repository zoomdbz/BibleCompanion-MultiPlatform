package com.dividesbyzer0.biblecompanion

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.createPlatformContext

fun MainViewController(
    shortcutAction: String? = null,
    deepLinkRoute: String? = null
) = ComposeUIViewController {
    val platformContext = remember { createPlatformContext() }
    CompositionLocalProvider(LocalPlatformContext provides platformContext) {
        AppRoot(shortcutAction = shortcutAction, deepLinkRoute = deepLinkRoute)
    }
}
