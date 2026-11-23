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
// Opens a Bible.com or BibleGateway link in the provider's app when installed,
// otherwise in the browser. Other hosts behave exactly like platformOpenUrl.
expect fun platformOpenExternalReader(context: PlatformContext, url: String)
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
// TtsSession (ReaderTts.kt) owns playback. A platform speaks one queue of
// segments at a time and reports progress through the event sink, on the main
// thread, tagged with the request id it was handed. The session drops events
// whose id is not the current request, so late callbacks from a stopped or
// replaced queue cannot finish, fail, or highlight the next one.
sealed class TtsPlatformEvent(val requestId: Long) {
    /** Segment [segmentIndex] of the queue started (or resumed) speaking. */
    class SegmentStarted(requestId: Long, val segmentIndex: Int) : TtsPlatformEvent(requestId)
    /** The final segment finished naturally. */
    class Finished(requestId: Long) : TtsPlatformEvent(requestId)
    /** The engine failed to bind or to speak; nothing more will play. */
    class Failed(requestId: Long) : TtsPlatformEvent(requestId)
}

expect fun platformTtsInit(context: PlatformContext)
expect fun platformTtsSetEventSink(sink: ((TtsPlatformEvent) -> Unit)?)
/** Replaces anything playing with [segments], spoken in order. */
expect fun platformTtsSpeakQueue(
    context: PlatformContext,
    requestId: Long,
    segments: List<String>,
    languageTag: String
)
/** Ends playback and forgets the queue, including work waiting on engine bind. */
expect fun platformTtsStop(context: PlatformContext)
/** Returns false when there was nothing playing to pause. */
expect fun platformTtsPause(context: PlatformContext): Boolean
/** Continues the paused queue from where it stopped; never restarts it. */
expect fun platformTtsResume(context: PlatformContext)

// ---- ONNX Runtime (embedding search) ----
expect fun platformOnnxInit(context: PlatformContext): Boolean
expect fun platformOnnxInference(inputIds: LongArray, attentionMask: LongArray): FloatArray?
expect fun platformOnnxIsReady(): Boolean

