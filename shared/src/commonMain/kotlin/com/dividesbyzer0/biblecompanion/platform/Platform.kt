package com.dividesbyzer0.biblecompanion.platform

import androidx.compose.runtime.staticCompositionLocalOf

// Platform context wraps Android Context on Android, custom object on iOS
expect abstract class PlatformContext

val LocalPlatformContext = staticCompositionLocalOf<PlatformContext> {
    error("No PlatformContext provided")
}

// ---- Asset loading ----
expect fun readAssetText(context: PlatformContext, path: String): String?
expect fun readAssetBytes(context: PlatformContext, path: String): ByteArray?
expect fun assetExists(context: PlatformContext, path: String): Boolean

// ---- Cache files ----
expect fun readCacheFile(context: PlatformContext, dir: String, name: String): String?
expect fun writeCacheFile(context: PlatformContext, dir: String, name: String, content: String)
expect fun cacheFileExists(context: PlatformContext, dir: String, name: String): Boolean
expect fun ensureCacheDir(context: PlatformContext, dir: String)

// ---- URL / browser ----
expect fun platformOpenUrl(context: PlatformContext, url: String)
expect fun platformOpenUrlInBrowser(context: PlatformContext, url: String)
expect fun platformIsAppInstalled(context: PlatformContext, packageId: String): Boolean
expect val isApplePlatform: Boolean

// ---- Clipboard & share ----
expect fun platformCopyToClipboard(context: PlatformContext, label: String, text: String)
expect fun platformShareText(context: PlatformContext, subject: String, text: String)

// ---- Locale ----
expect fun platformGetDefaultLocaleLanguage(): String
expect fun platformGetDefaultLocaleScript(): String
expect fun platformGetDefaultLocaleCountry(): String
expect fun platformLanguageFromTag(tag: String): String
expect fun platformScriptFromTag(tag: String): String
expect fun platformCountryFromTag(tag: String): String
expect fun platformSetAppLocale(tag: String)
expect fun platformRecreateApp(context: PlatformContext)

// ---- Text normalization ----
expect fun normalizeNFKD(s: String): String
expect fun normalizeNFKC(s: String): String

// ---- Network ----
expect fun httpGetForPreflight(url: String, timeoutMs: Int, maxBytes: Int): Pair<Int, String>

// ---- Crypto ----
expect fun sha256Hex(bytes: ByteArray): String

// ---- URL encoding ----
expect fun urlEncode(s: String): String

// ---- Date ----
expect fun platformCurrentDate(): Triple<Int, Int, Int>

// ---- Timestamp ----
expect fun currentTimeMillis(): Long

// ---- App version (read at runtime from platform build info) ----
expect fun platformAppVersion(context: PlatformContext): String
expect fun platformAppBuild(context: PlatformContext): String

// ---- Text-to-Speech ----
expect fun platformTtsInit(context: PlatformContext)
expect fun platformTtsSpeak(context: PlatformContext, text: String, languageTag: String)
expect fun platformTtsStop(context: PlatformContext)
expect fun platformTtsIsSpeaking(context: PlatformContext): Boolean
expect fun platformTtsSetOnDone(callback: (() -> Unit)?)
expect fun platformTtsPause(context: PlatformContext)
expect fun platformTtsResume(context: PlatformContext)
expect fun platformTtsIsPaused(context: PlatformContext): Boolean

// ---- ONNX Runtime (embedding search) ----
expect fun platformOnnxInit(context: PlatformContext): Boolean
expect fun platformOnnxInference(inputIds: LongArray, attentionMask: LongArray): FloatArray?
expect fun platformOnnxIsReady(): Boolean

