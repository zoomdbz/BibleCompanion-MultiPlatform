package com.dividesbyzer0.biblecompanion

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.createPlatformContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private var exceptionHookInstalled = false

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun installCrashHook() {
    if (exceptionHookInstalled) return
    exceptionHookInstalled = true
    kotlin.native.setUnhandledExceptionHook { throwable ->
        println("KN_UNCAUGHT: ${throwable::class.simpleName} -- ${throwable.message}")
        throwable.printStackTrace()
    }
}

object DeepLinkBridge {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route

    fun pushRoute(route: String?) {
        _route.value = route
    }
}

object ShortcutBridge {
    private val _action = MutableStateFlow<String?>(null)
    val action: StateFlow<String?> = _action

    fun pushAction(action: String?) {
        _action.value = action
    }
}

fun MainViewController() : platform.UIKit.UIViewController {
    installCrashHook()
    return ComposeUIViewController {
        val platformContext = remember { createPlatformContext() }
        val liveRoute by DeepLinkBridge.route.collectAsState()
        val liveShortcutAction by ShortcutBridge.action.collectAsState()
        CompositionLocalProvider(LocalPlatformContext provides platformContext) {
            AppRoot(shortcutAction = liveShortcutAction, deepLinkRoute = liveRoute)
        }
    }
}
