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

internal data class BridgeEvent(val value: String?, val id: Long)

object DeepLinkBridge {
    private val _route = MutableStateFlow(BridgeEvent(null, 0L))
    internal val route: StateFlow<BridgeEvent> = _route

    fun pushRoute(route: String?) {
        _route.value = BridgeEvent(route, _route.value.id + 1L)
    }
}

object ShortcutBridge {
    private val _action = MutableStateFlow(BridgeEvent(null, 0L))
    internal val action: StateFlow<BridgeEvent> = _action

    fun pushAction(action: String?) {
        _action.value = BridgeEvent(action, _action.value.id + 1L)
    }
}

fun MainViewController() : platform.UIKit.UIViewController {
    installCrashHook()
    return ComposeUIViewController {
        val platformContext = remember { createPlatformContext() }
        val liveRoute by DeepLinkBridge.route.collectAsState()
        val liveShortcutAction by ShortcutBridge.action.collectAsState()
        CompositionLocalProvider(LocalPlatformContext provides platformContext) {
            AppRoot(
                shortcutAction = liveShortcutAction.value,
                deepLinkRoute = liveRoute.value,
                shortcutEventId = liveShortcutAction.id,
                deepLinkEventId = liveRoute.id
            )
        }
    }
}
