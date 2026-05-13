@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.dividesbyzer0.biblecompanion.platform

import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.getBytes
import platform.Foundation.length
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSLocale
import platform.Foundation.NSMutableCharacterSet
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.languageCode
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.scriptCode
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy
import kotlin.concurrent.Volatile

actual abstract class PlatformContext {
    val bundle: NSBundle = NSBundle.mainBundle
}

fun createPlatformContext(): PlatformContext = object : PlatformContext() {}

actual fun readAssetText(context: PlatformContext, path: String): String? {
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

actual fun readAssetBytes(context: PlatformContext, path: String): ByteArray? {
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
        NSData.dataWithContentsOfFile(it)?.let { data ->
            val len = data.length.toInt()
            ByteArray(len).also { bytes ->
                // getBytes wants CPointer<out CPointed>?, not CValuesRef.
                // Pin the ByteArray so its addressOf(0) returns a stable raw
                // pointer for the NSData copy. The previous refTo(0) form
                // returns CValuesRef and doesn't match either overload.
                bytes.usePinned { pinned ->
                    data.getBytes(pinned.addressOf(0), data.length)
                }
            }
        }
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

private fun getDocumentsDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    )
    return (paths.firstOrNull() as? String) ?: ""
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
    @Suppress("CAST_NEVER_SUCCEEDS")
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
    NSURL.URLWithString(url)?.let { nsUrl ->
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any>(), null)
    }
}

actual fun platformOpenUrlInBrowser(context: PlatformContext, url: String) {
    platformOpenUrl(context, url)
}

actual fun platformIsAppInstalled(context: PlatformContext, packageId: String): Boolean = false

actual val isApplePlatform: Boolean = true

actual fun platformCopyToClipboard(context: PlatformContext, label: String, text: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun platformShareText(context: PlatformContext, subject: String, text: String) {
    val items = listOf(text)
    val activityVC = UIActivityViewController(activityItems = items, applicationActivities = null)
    val scenes = UIApplication.sharedApplication.connectedScenes
    val windowScene = scenes.firstOrNull() as? UIWindowScene
    val window = windowScene?.windows?.firstOrNull {
        (it as? UIWindow)?.isKeyWindow() == true
    } as? UIWindow
    val rootVC = window?.rootViewController ?: return
    rootVC.presentViewController(activityVC, animated = true, completion = null)
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
    // Override Foundation's locale resolution so Compose Resources picks up the
    // user's in-app language preference instead of only the system language.
    // Apple resolves AppleLanguages from NSUserDefaults before NSBundle reads
    // localized strings, so setting it here propagates to stringResource(...)
    // calls (Compose Resources reads through NSBundle on iOS).
    // Full effect — including system framework strings — applies on next launch.
    val defaults = NSUserDefaults.standardUserDefaults
    if (tag.equals("system", ignoreCase = true)) {
        defaults.removeObjectForKey("AppleLanguages")
    } else {
        val canonical = when (tag.lowercase()) {
            "zh-hans" -> "zh-Hans"
            "zh-hant" -> "zh-Hant"
            else -> tag
        }
        // AppleLanguages is canonically an NSArray<NSString>. Kotlin/Native
        // bridges Kotlin's List<String> to NSArray<NSString> implicitly when
        // crossing the ObjC boundary — NSUserDefaults.setObject takes id (Any?),
        // and the runtime conversion handles the rest. The previous attempt to
        // use NSArray.arrayWithObject(...) failed to compile because that class
        // method returns List<*> in the K/N bindings, not NSArray.
        defaults.setObject(listOf(canonical), forKey = "AppleLanguages")
    }
    defaults.synchronize()
}

actual fun platformRecreateApp(context: PlatformContext) {
    // No-op on iOS - UI recomposes automatically
}

actual fun normalizeNFKD(s: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsStr = s as NSString
    return nsStr.decomposedStringWithCompatibilityMapping
}

actual fun normalizeNFKC(s: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsStr = s as NSString
    return nsStr.precomposedStringWithCompatibilityMapping
}

private fun nsDataToUtf8String(data: NSData): String {
    val length = data.length.toInt()
    if (length == 0) return ""
    val src = data.bytes ?: return ""
    val buffer = ByteArray(length)
    buffer.usePinned { pinned ->
        memcpy(pinned.addressOf(0), src, length.convert())
    }
    return buffer.decodeToString()
}

actual fun httpGetForPreflight(url: String, timeoutMs: Int, maxBytes: Int): Pair<Int, String> {
    return try {
        val nsUrl = NSURL.URLWithString(url) ?: return 0 to ""
        val request = NSMutableURLRequest(
            uRL = nsUrl,
            cachePolicy = NSURLRequestUseProtocolCachePolicy,
            timeoutInterval = timeoutMs / 1000.0
        )
        request.setHTTPMethod("GET")
        request.setValue("Mozilla/5.0 (iOS) BibleCompanion/1.0", forHTTPHeaderField = "User-Agent")

        val semaphore = dispatch_semaphore_create(0)
        var statusCode = 200
        var body = ""
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, _ ->
            statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 200
            val text = data?.let { nsDataToUtf8String(it) } ?: ""
            body = if (text.length > maxBytes) text.substring(0, maxBytes) else text
            dispatch_semaphore_signal(semaphore)
        }
        task.resume()
        // DISPATCH_TIME_FOREVER == ~0ULL; NSURLRequest.timeoutInterval bounds the wait in practice.
        dispatch_semaphore_wait(semaphore, ULong.MAX_VALUE)
        statusCode to body.lowercase()
    } catch (_: Throwable) {
        200 to "" // permissive on error, matching Android behavior
    }
}

// Pure-Kotlin SHA-256 (FIPS 180-4). Avoids CommonCrypto cinterop dependency.
actual fun sha256Hex(bytes: ByteArray): String {
    val k = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
        0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
        0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
        0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
        0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )
    val h = intArrayOf(
        0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(),
        0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt()
    )

    val len = bytes.size
    val bitLen = len.toLong() * 8
    val padLen = (56 - (len + 1) % 64 + 64) % 64
    val padded = ByteArray(len + 1 + padLen + 8)
    bytes.copyInto(padded, 0)
    padded[len] = 0x80.toByte()
    for (i in 0..7) {
        padded[padded.size - 1 - i] = (bitLen ushr (i * 8)).toByte()
    }

    val w = IntArray(64)
    var blockStart = 0
    while (blockStart < padded.size) {
        for (j in 0..15) {
            w[j] = ((padded[blockStart + j * 4].toInt() and 0xFF) shl 24) or
                ((padded[blockStart + j * 4 + 1].toInt() and 0xFF) shl 16) or
                ((padded[blockStart + j * 4 + 2].toInt() and 0xFF) shl 8) or
                (padded[blockStart + j * 4 + 3].toInt() and 0xFF)
        }
        for (j in 16..63) {
            val s0 = rotr32(w[j - 15], 7) xor rotr32(w[j - 15], 18) xor (w[j - 15] ushr 3)
            val s1 = rotr32(w[j - 2], 17) xor rotr32(w[j - 2], 19) xor (w[j - 2] ushr 10)
            w[j] = w[j - 16] + s0 + w[j - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (j in 0..63) {
            val s1 = rotr32(e, 6) xor rotr32(e, 11) xor rotr32(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + k[j] + w[j]
            val s0 = rotr32(a, 2) xor rotr32(a, 13) xor rotr32(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        blockStart += 64
    }

    val hex = "0123456789abcdef"
    return buildString(64) {
        for (word in h) {
            for (shift in 24 downTo 0 step 8) {
                val byte = (word ushr shift) and 0xFF
                append(hex[(byte ushr 4) and 0xF])
                append(hex[byte and 0xF])
            }
        }
    }
}

private fun rotr32(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))

actual fun urlEncode(s: String): String {
    // Strict allowed set matching Java's URLEncoder.encode(): only unreserved chars pass through.
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsStr = s as NSString
    val allowed = NSMutableCharacterSet()
    allowed.addCharactersInString("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.*")
    return nsStr.stringByAddingPercentEncodingWithAllowedCharacters(allowed) ?: s
}

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun platformCurrentDate(): Triple<Int, Int, Int> {
    val fmt = NSDateFormatter()
    fmt.dateFormat = "yyyy-MM-dd"
    val parts = fmt.stringFromDate(NSDate()).split("-")
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

private class TtsDelegate : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance
    ) {
        ttsOnDone?.invoke()
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didCancelSpeechUtterance: AVSpeechUtterance
    ) {
        ttsOnDone?.invoke()
    }
}

// Lazy: AVSpeechSynthesizer must be created on the main thread, and creating it
// at top-level eager-init time runs during framework load, before the iOS app
// delegate is fully ready. Defer to first TTS call, which always happens on the
// UI thread from a Compose action.
private val ttsDelegate by lazy { TtsDelegate() }
private val synthesizer: AVSpeechSynthesizer by lazy {
    AVSpeechSynthesizer().also { it.delegate = ttsDelegate }
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
    synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    val utterance = AVSpeechUtterance(string = text)
    val mapped = ttsLanguageTag(languageTag)
    utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(mapped)
        ?: AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
    utterance.rate = 0.5f
    synthesizer.speakUtterance(utterance)
}

actual fun platformTtsStop(context: PlatformContext) {
    synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
}

actual fun platformTtsIsSpeaking(context: PlatformContext): Boolean = synthesizer.isSpeaking()

actual fun platformTtsSetOnDone(callback: (() -> Unit)?) {
    ttsOnDone = callback
}

actual fun platformTtsPause(context: PlatformContext) {
    synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
}

actual fun platformTtsResume(context: PlatformContext) {
    synthesizer.continueSpeaking()
}

actual fun platformTtsIsPaused(context: PlatformContext): Boolean = synthesizer.isPaused()

// ---- ONNX Runtime (implemented in Swift, injected via setIosQueryEncoder) ----

interface IosQueryEncoder {
    fun isReady(): Boolean
    fun encode(inputIds: LongArray, attentionMask: LongArray): FloatArray?
}

private var queryEncoder: IosQueryEncoder? = null

fun setIosQueryEncoder(encoder: IosQueryEncoder) {
    queryEncoder = encoder
}

actual fun platformOnnxInit(context: PlatformContext): Boolean =
    queryEncoder?.isReady() ?: false

actual fun platformOnnxInference(inputIds: LongArray, attentionMask: LongArray): FloatArray? =
    queryEncoder?.encode(inputIds, attentionMask)

actual fun platformOnnxIsReady(): Boolean =
    queryEncoder?.isReady() ?: false
