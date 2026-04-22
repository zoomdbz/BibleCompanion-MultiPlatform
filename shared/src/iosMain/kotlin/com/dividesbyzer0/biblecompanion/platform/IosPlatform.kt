package com.dividesbyzer0.biblecompanion.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathDomainMask
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLConnection
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURL.Companion.URLWithString
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.lastPathComponent
import platform.Foundation.pathExtension
import platform.Foundation.stringByDeletingPathExtension
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.Security.SecRandomCopyBytes
import platform.CoreFoundation.CFStringTransform
import platform.CoreFoundation.kCFStringTransformToLatin
import platform.CoreFoundation.kCFStringTransformStripCombiningMarks
import kotlinx.cinterop.refTo
import kotlinx.cinterop.convert
import platform.CommonCrypto.CC_SHA256
import platform.CommonCrypto.CC_SHA256_DIGEST_LENGTH

actual abstract class PlatformContext {
    val bundle: NSBundle = NSBundle.mainBundle
}

fun createPlatformContext(): PlatformContext = object : PlatformContext() {}

actual fun readAssetText(context: PlatformContext, path: String): String? {
    // Assets are bundled as resources in the iOS app
    val components = path.split("/")
    val fileName = components.last().substringBeforeLast(".")
    val ext = components.last().substringAfterLast(".", "")
    val subDir = if (components.size > 1) components.dropLast(1).joinToString("/") else null

    val resourcePath = if (subDir != null) {
        context.bundle.pathForResource(fileName, ext, subDir)
    } else {
        context.bundle.pathForResource(fileName, ext)
    }

    return resourcePath?.let {
        NSString.stringWithContentsOfFile(it, NSUTF8StringEncoding, null)
    }
}

actual fun assetExists(context: PlatformContext, path: String): Boolean {
    val components = path.split("/")
    val fileName = components.last().substringBeforeLast(".")
    val ext = components.last().substringAfterLast(".", "")
    val subDir = if (components.size > 1) components.dropLast(1).joinToString("/") else null
    val resourcePath = if (subDir != null) {
        context.bundle.pathForResource(fileName, ext, subDir)
    } else {
        context.bundle.pathForResource(fileName, ext)
    }
    return resourcePath != null
}

@OptIn(ExperimentalForeignApi::class)
private fun getDocumentsDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    )
    return (paths.firstOrNull() as? String) ?: ""
}

@OptIn(ExperimentalForeignApi::class)
private fun NSSearchPathForDirectoriesInDomains(
    directory: NSSearchPathDirectory,
    domainMask: NSSearchPathDomainMask,
    expandTilde: Boolean
): List<*> {
    return platform.Foundation.NSSearchPathForDirectoriesInDomains(directory, domainMask, expandTilde)
}

actual fun readCacheFile(context: PlatformContext, dir: String, name: String): String? {
    val path = "${getDocumentsDir()}/$dir/$name"
    return if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    } else null
}

actual fun writeCacheFile(context: PlatformContext, dir: String, name: String, content: String) {
    val dirPath = "${getDocumentsDir()}/$dir"
    NSFileManager.defaultManager.createDirectoryAtPath(dirPath, true, null, null)
    val path = "$dirPath/$name"
    (content as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
}

actual fun cacheFileExists(context: PlatformContext, dir: String, name: String): Boolean {
    val path = "${getDocumentsDir()}/$dir/$name"
    return NSFileManager.defaultManager.fileExistsAtPath(path)
}

actual fun ensureCacheDir(context: PlatformContext, dir: String) {
    val dirPath = "${getDocumentsDir()}/$dir"
    NSFileManager.defaultManager.createDirectoryAtPath(dirPath, true, null, null)
}

actual fun platformOpenUrl(context: PlatformContext, url: String) {
    URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl)
    }
}

actual fun platformOpenUrlInBrowser(context: PlatformContext, url: String) {
    platformOpenUrl(context, url)
}

actual fun platformIsAppInstalled(context: PlatformContext, packageId: String): Boolean {
    // iOS doesn't support checking arbitrary app installation
    return false
}

actual fun platformCopyToClipboard(context: PlatformContext, label: String, text: String) {
    UIPasteboard.generalPasteboard.string = text
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun platformShareText(context: PlatformContext, subject: String, text: String) {
    val items = listOf(text)
    val activityVC = UIActivityViewController(activityItems = items, applicationActivities = null)
    val scenes = UIApplication.sharedApplication.connectedScenes
    val windowScene = scenes.firstOrNull() as? platform.UIKit.UIWindowScene
    val window = windowScene?.windows?.firstOrNull { (it as? platform.UIKit.UIWindow)?.isKeyWindow == true } as? platform.UIKit.UIWindow
    window?.rootViewController?.presentViewController(
        activityVC, animated = true, completion = null
    )
}

actual fun platformGetDefaultLocaleLanguage(): String {
    val id = NSLocale.currentLocale.languageCode ?: "en"
    return id.lowercase()
}

actual fun platformGetDefaultLocaleScript(): String {
    return NSLocale.currentLocale.scriptCode?.lowercase() ?: ""
}

actual fun platformGetDefaultLocaleCountry(): String {
    return (NSLocale.currentLocale.countryCode ?: "").uppercase()
}

actual fun platformLanguageFromTag(tag: String): String {
    val locale = NSLocale(localeIdentifier = tag.replace("-", "_"))
    return (locale.languageCode ?: "en").lowercase()
}

actual fun platformScriptFromTag(tag: String): String {
    val locale = NSLocale(localeIdentifier = tag.replace("-", "_"))
    return (locale.scriptCode ?: "").lowercase()
}

actual fun platformCountryFromTag(tag: String): String {
    val locale = NSLocale(localeIdentifier = tag.replace("-", "_"))
    return (locale.countryCode ?: "").uppercase()
}

actual fun platformSetAppLocale(tag: String) {
    // iOS locale is set via system settings; no programmatic override needed
    // The app reads the user preference and loads appropriate assets
}

actual fun platformRecreateApp(context: PlatformContext) {
    // No-op on iOS - UI recomposes automatically
}

@OptIn(ExperimentalForeignApi::class)
actual fun normalizeNFKD(s: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsStr = s as NSString
    return nsStr.decomposedStringWithCompatibilityMapping
}

@OptIn(ExperimentalForeignApi::class)
actual fun normalizeNFKC(s: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsStr = s as NSString
    return nsStr.precomposedStringWithCompatibilityMapping
}

actual fun httpGetForPreflight(url: String, timeoutMs: Int, maxBytes: Int): Pair<Int, String> {
    return try {
        val nsUrl = NSURL.URLWithString(url) ?: return 0 to ""
        val request = NSMutableURLRequest(nsUrl).apply {
            setHTTPMethod("GET")
            setTimeoutInterval(timeoutMs / 1000.0)
            setValue("Mozilla/5.0 (iOS) BibleCompanion/1.0", forHTTPHeaderField = "User-Agent")
        }
        val data = NSURLConnection.sendSynchronousRequest(request, returningResponse = null, error = null)
        val fullBody = data?.let {
            NSString.create(data = it, encoding = NSUTF8StringEncoding)?.lowercase() ?: ""
        } ?: ""
        // Respect maxBytes by truncating the result (mirrors Android capping)
        val body = if (fullBody.length > maxBytes) fullBody.substring(0, maxBytes) else fullBody
        // Status code not available without response pointer; use content-based detection
        // (isBibleComChapterLikelyAvailable checks body content, not status code)
        200 to body
    } catch (_: Throwable) {
        200 to "" // permissive on error, matching Android behavior
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun sha256Hex(bytes: ByteArray): String {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { pinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(pinned.addressOf(0), bytes.size.convert(), digestPinned.addressOf(0))
        }
    }
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { b ->
            val v = b.toInt()
            append(hex[(v ushr 4) and 0xF])
            append(hex[v and 0xF])
        }
    }
}

actual fun urlEncode(s: String): String {
    // Use a strict allowed set matching Java's URLEncoder.encode() behavior:
    // only unreserved chars (letters, digits, -, _, ., *) pass through unencoded.
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsStr = s as NSString
    val allowed = platform.Foundation.NSMutableCharacterSet()
    allowed.addCharactersInString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.*")
    return nsStr.stringByAddingPercentEncodingWithAllowedCharacters(allowed) ?: s
}

actual fun currentTimeMillis(): Long =
    (platform.Foundation.NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun platformCurrentDate(): Triple<Int, Int, Int> {
    val fmt = platform.Foundation.NSDateFormatter()
    fmt.dateFormat = "yyyy-MM-dd"
    val parts = fmt.stringFromDate(platform.Foundation.NSDate()).split("-")
    return Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

actual fun platformAppVersion(context: PlatformContext): String {
    val info = NSBundle.mainBundle.infoDictionary ?: return ""
    return info["CFBundleShortVersionString"] as? String ?: ""
}

actual fun platformAppBuild(context: PlatformContext): String {
    val info = NSBundle.mainBundle.infoDictionary ?: return ""
    return info["CFBundleVersion"] as? String ?: ""
}

@Volatile private var ttsOnDone: (() -> Unit)? = null

@OptIn(BetaInteropApi::class)
private class TtsDelegate : platform.darwin.NSObject(), platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol {
    override fun speechSynthesizer(
        synthesizer: platform.AVFAudio.AVSpeechSynthesizer,
        didFinishSpeechUtterance: platform.AVFAudio.AVSpeechUtterance
    ) {
        ttsOnDone?.invoke()
    }

    override fun speechSynthesizer(
        synthesizer: platform.AVFAudio.AVSpeechSynthesizer,
        didCancelSpeechUtterance: platform.AVFAudio.AVSpeechUtterance
    ) {
        ttsOnDone?.invoke()
    }
}

private val ttsDelegate = TtsDelegate()
private val synthesizer = platform.AVFAudio.AVSpeechSynthesizer().also {
    it.delegate = ttsDelegate
}

private fun ttsLanguageTag(assetTag: String): String = when (assetTag) {
    "zh-Hans" -> "zh-CN"
    "zh-Hant" -> "zh-TW"
    else -> assetTag
}

actual fun platformTtsInit(context: PlatformContext) {
    // Delegate is wired at property init; nothing else needed.
}

actual fun platformTtsSpeak(context: PlatformContext, text: String, languageTag: String) {
    synthesizer.stopSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundary.AVSpeechBoundaryImmediate)
    val utterance = platform.AVFAudio.AVSpeechUtterance(string = text)
    val mapped = ttsLanguageTag(languageTag)
    utterance.voice = platform.AVFAudio.AVSpeechSynthesisVoice.voiceWithLanguage(mapped)
        ?: platform.AVFAudio.AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
    utterance.rate = 0.5f
    synthesizer.speakUtterance(utterance)
}

actual fun platformTtsStop(context: PlatformContext) {
    synthesizer.stopSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundary.AVSpeechBoundaryImmediate)
}

actual fun platformTtsIsSpeaking(context: PlatformContext): Boolean = synthesizer.isSpeaking()

actual fun platformTtsSetOnDone(callback: (() -> Unit)?) {
    ttsOnDone = callback
}

actual fun platformTtsPause(context: PlatformContext) {
    synthesizer.pauseSpeakingAtBoundary(platform.AVFAudio.AVSpeechBoundary.AVSpeechBoundaryImmediate)
}

actual fun platformTtsResume(context: PlatformContext) {
    synthesizer.continueSpeaking()
}

actual fun platformTtsIsPaused(context: PlatformContext): Boolean = synthesizer.isPaused()

