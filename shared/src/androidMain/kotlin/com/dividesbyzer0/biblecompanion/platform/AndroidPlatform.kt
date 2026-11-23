package com.dividesbyzer0.biblecompanion.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.net.toUri
import com.dividesbyzer0.biblecompanion.Linker
import com.dividesbyzer0.biblecompanion.ttsSpeechPieces
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

actual typealias PlatformContext = Context

// Name of the fast-follow asset pack defined in embedding-assets/build.gradle.kts.
// Files under the "embedding/" prefix live in this pack rather than in the
// base APK's assets, so asset reads for those paths must consult
// AssetPackManager and read from the on-disk pack location.
private const val EMBEDDING_PACK_NAME = "embedding_assets"
private const val EMBEDDING_PATH_PREFIX = "embedding/"

/**
 * Resolve a relative asset path to a File on disk inside the fast-follow
 * pack, or null if the pack isn't downloaded yet (or the file is missing).
 * Returns null for paths that don't live in the asset pack so callers can
 * fall through to the regular context.assets API.
 */
private fun assetPackFile(context: Context, relativePath: String): File? {
    if (!relativePath.startsWith(EMBEDDING_PATH_PREFIX)) return null
    return runCatching {
        val mgr = AssetPackManagerFactory.getInstance(context)
        val location = mgr.getPackLocation(EMBEDDING_PACK_NAME) ?: return null
        val assetsPath = location.assetsPath() ?: return null
        val f = File(assetsPath, relativePath)
        if (f.exists()) f else null
    }.getOrNull()
}

actual fun readAssetText(context: PlatformContext, path: String): String? = runCatching {
    val packFile = assetPackFile(context, path)
    if (packFile != null) {
        packFile.readText()
    } else {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }
}.getOrNull()

actual fun readAssetBytes(context: PlatformContext, path: String): ByteArray? = runCatching {
    val packFile = assetPackFile(context, path)
    if (packFile != null) {
        packFile.readBytes()
    } else {
        context.assets.open(path).use { it.readBytes() }
    }
}.getOrNull()

actual fun assetExists(context: PlatformContext, path: String): Boolean = runCatching {
    if (assetPackFile(context, path) != null) return@runCatching true
    context.assets.open(path).use { }
    true
}.getOrDefault(false)

actual fun readCacheFile(context: PlatformContext, dir: String, name: String): String? = runCatching {
    File(File(context.filesDir, dir), name).readText()
}.getOrNull()

actual fun writeCacheFile(context: PlatformContext, dir: String, name: String, content: String) {
    val d = File(context.filesDir, dir).apply { mkdirs() }
    File(d, name).writeText(content)
}

actual fun cacheFileExists(context: PlatformContext, dir: String, name: String): Boolean =
    File(File(context.filesDir, dir), name).exists()

actual fun ensureCacheDir(context: PlatformContext, dir: String) {
    File(context.filesDir, dir).mkdirs()
}

actual fun platformOpenUrl(context: PlatformContext, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

actual fun platformOpenUrlInBrowser(context: PlatformContext, url: String) {
    val base = Intent(Intent.ACTION_VIEW, url.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val tried = runCatching {
        val chrome = base.cloneFilter().setPackage("com.android.chrome")
        context.startActivity(Intent(chrome).setData(url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
    if (!tried) runCatching { context.startActivity(base) }
}

// YouVersion is verified for bible.com through Digital Asset Links and opens
// its https passage URLs directly. Bible Gateway publishes no asset links and
// its app has no https filter; it opens only through biblegateway:// links.
private const val YOUVERSION_PACKAGE = "com.sirma.mobile.bible.android"
private const val BIBLE_GATEWAY_PACKAGE = "com.csnmedia.android.bg"
private val youVersionHosts = setOf("www.bible.com", "bible.com")

// startActivity is not subject to package-visibility filtering, so no
// <queries> entry is needed. A missing app or a package without a matching
// VIEW filter throws, and the caller falls through to the next option.
private fun startInPackage(context: Context, uri: Uri, pkg: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

actual fun platformOpenExternalReader(context: PlatformContext, url: String) {
    Linker.bibleGatewayAppLinkForWebUrl(url)?.let { appLink ->
        if (startInPackage(context, appLink.toUri(), BIBLE_GATEWAY_PACKAGE)) return
    }
    val uri = url.toUri()
    if (uri.host?.lowercase() in youVersionHosts && startInPackage(context, uri, YOUVERSION_PACKAGE)) return
    platformOpenUrl(context, url)
}

actual fun platformIsAppInstalled(context: PlatformContext, packageId: String): Boolean = runCatching {
    context.packageManager.getPackageInfo(packageId, 0)
    true
}.getOrDefault(false)

actual val isApplePlatform: Boolean = false

actual fun platformCopyToClipboard(context: PlatformContext, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

actual fun platformShareText(context: PlatformContext, subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(i, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun platformGetDefaultLocaleLanguage(): String = Locale.getDefault().language.lowercase()
actual fun platformGetDefaultLocaleScript(): String = Locale.getDefault().script.lowercase()
actual fun platformGetDefaultLocaleCountry(): String = Locale.getDefault().country.uppercase()
actual fun platformLanguageFromTag(tag: String): String = Locale.forLanguageTag(tag).language.lowercase()
actual fun platformScriptFromTag(tag: String): String = Locale.forLanguageTag(tag).script.lowercase()
actual fun platformCountryFromTag(tag: String): String = Locale.forLanguageTag(tag).country.uppercase()

// Normalize a locale tag for comparison. AppCompat may report back tags with
// region suffixes ("en-US", "fr-FR") even when we set just the language ("en",
// "fr"); without normalization the exact-equality guard would churn and force
// a redundant setApplicationLocales call (and a second activity recreate).
// Rules:
//   - null or "system" → "system" (sentinel for "no override")
//   - "zh-Hans" / "zh-Hant" → kept intact (script matters, not region)
//   - everything else → strip after the first "-", lowercase
//
// Public because MainActivity in the :app module compares locale tags too
// (its own pre-setContent guard at onCreate) and must use the same rules.
fun normalizeLocaleTagForCompare(tag: String?): String {
    if (tag == null) return "system"
    val trimmed = tag.trim()
    if (trimmed.isEmpty() || trimmed.equals("system", ignoreCase = true)) return "system"
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("zh-hans") || lower == "zh-cn" -> "zh-Hans"
        lower.startsWith("zh-hant") || lower == "zh-tw" || lower == "zh-hk" || lower == "zh-mo" -> "zh-Hant"
        else -> lower.substringBefore('-')
    }
}

actual fun platformSetAppLocale(tag: String) {
    val resolved = when (tag) {
        "zh-Hans" -> "zh-CN"
        "zh-Hant" -> "zh-TW"
        else -> tag
    }
    val current = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (current.isEmpty) "system" else current.toLanguageTags()
    // Compare normalized tags so a stored "en-US" doesn't get re-set to "en"
    // (and vice versa).
    if (normalizeLocaleTagForCompare(currentTag) == normalizeLocaleTagForCompare(resolved)) return
    val target = if (resolved == "system")
        LocaleListCompat.getEmptyLocaleList()
    else
        LocaleListCompat.forLanguageTags(resolved)
    AppCompatDelegate.setApplicationLocales(target)
}

actual fun platformRecreateApp(context: PlatformContext) {
    (context as? Activity)?.recreate()
}

actual fun normalizeNFKD(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKD)
actual fun normalizeNFKC(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

actual fun httpGetForPreflight(url: String, timeoutMs: Int, maxBytes: Int): Pair<Int, String> {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BibleCompanion/1.0")
            setRequestProperty("Accept-Encoding", "identity")
        }
        val code = conn.responseCode
        val stream = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?: return code to ""
        val body = stream.use { s ->
            val buf = ByteArray(maxBytes)
            var off = 0
            while (true) {
                val n = s.read(buf, off, kotlin.math.min(4096, maxBytes - off))
                if (n <= 0 || off >= maxBytes) break
                off += n
            }
            String(buf, 0, off).lowercase(Locale.US)
        }
        code to body
    } catch (_: Throwable) {
        200 to "" // permissive on error
    } finally {
        runCatching { conn?.disconnect() }
    }
}

actual fun sha256Hex(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val d = md.digest(bytes)
    val hex = "0123456789abcdef"
    return buildString(d.size * 2) {
        d.forEach { b ->
            val v = b.toInt()
            append(hex[(v ushr 4) and 0xF]); append(hex[v and 0xF])
        }
    }
}

actual fun urlEncode(s: String): String =
    URLEncoder.encode(s, StandardCharsets.UTF_8.name())

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformCurrentDate(): Triple<Int, Int, Int> {
    val cal = java.util.Calendar.getInstance()
    return Triple(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

actual fun platformAppVersion(context: PlatformContext): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
}.getOrDefault("")

actual fun platformAppBuild(context: PlatformContext): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        info.longVersionCode.toString()
    else
        @Suppress("DEPRECATION") info.versionCode.toString()
}.getOrDefault("")

@Volatile private var ttsInstance: TextToSpeech? = null
@Volatile private var ttsReady = false
@Volatile private var ttsBinding = false
// Do not resolve Android's main Looper while this platform facade loads. Pure
// locale, edition, and linker code calls other actuals in this file during JVM
// unit tests, where no Android main Looper exists. TTS alone needs this handler.
private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
private val ttsLock = Any()

private fun ttsLocale(languageTag: String): Locale {
    val mapped = when (languageTag) {
        "zh-Hans" -> "zh-CN"
        "zh-Hant" -> "zh-TW"
        "en" -> "en-US"
        "es" -> "es-ES"
        "fr" -> "fr-FR"
        "de" -> "de-DE"
        "it" -> "it-IT"
        "pt" -> "pt-BR"
        "ru" -> "ru-RU"
        "ko" -> "ko-KR"
        "ja" -> "ja-JP"
        "hi" -> "hi-IN"
        "ar" -> "ar-SA"
        "sw" -> "sw-TZ"
        "uk" -> "uk-UA"
        else -> languageTag
    }
    return Locale.forLanguageTag(mapped)
}

// ---- Playback queue ----
// Android TextToSpeech has no pause. Pause stops the engine and keeps a cursor
// (segment plus character offset); Resume re-queues from that cursor. Every
// entry point runs on the main thread (Compose callers), and engine callbacks
// are posted back to it before they read or write any of this state.
//
// Each segment (one verse) is split into sentence pieces by ttsSpeechPieces,
// which also keeps every piece under TextToSpeech.getMaxSpeechInputLength();
// past that limit speak() silently returns ERROR. onRangeStart (API 26+) moves
// the cursor to the word being spoken, so Resume continues at that word.
// Engines that never call it fall back to the start of the current sentence.
private class TtsQueue(val requestId: Long, val segments: List<String>, val locale: Locale)
private class TtsPiece(val segment: Int, val offset: Int, val text: String)

private var ttsEventSink: ((TtsPlatformEvent) -> Unit)? = null
private var ttsQueue: TtsQueue? = null
// Pieces queued for the current generation; utterance ids index into it.
private var ttsPieces: List<TtsPiece> = emptyList()
private var ttsNextPiece = 0
private var ttsCursorSegment = 0
private var ttsCursorOffset = 0
private var ttsPaused = false
// A queue is waiting for the engine to bind. Stop and Pause clear it, so a
// late bind cannot start speech nobody wants anymore.
private var ttsStartPending = false
// Bumped by every speak, pause, resume, and stop. Utterance ids carry it, and
// posted callbacks compare it on the main thread, so a callback already in
// flight when the generation moved on is dropped there.
@Volatile private var ttsGeneration = 0L

// Queue a few pieces ahead instead of a whole chapter: Psalm 119 is hundreds
// of sentences, and each speak() is a binder call on the main thread.
private const val TTS_PIECES_AHEAD = 6

private fun postTtsPieceEvent(utteranceId: String?, block: (TtsPiece, Int) -> Unit) {
    val parts = utteranceId?.split(':') ?: return
    val generation = parts.getOrNull(0)?.toLongOrNull() ?: return
    val index = parts.getOrNull(1)?.toIntOrNull() ?: return
    mainHandler.post {
        if (generation != ttsGeneration) return@post
        val piece = ttsPieces.getOrNull(index) ?: return@post
        block(piece, index)
    }
}

private val ttsProgressListener = object : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) = postTtsPieceEvent(utteranceId) { piece, _ ->
        ttsCursorSegment = piece.segment
        ttsCursorOffset = piece.offset
        val queue = ttsQueue ?: return@postTtsPieceEvent
        ttsEventSink?.invoke(TtsPlatformEvent.SegmentStarted(queue.requestId, piece.segment))
    }

    // API 26+. Never called on 24-25 or by engines without word timing.
    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) =
        postTtsPieceEvent(utteranceId) { piece, _ ->
            ttsCursorSegment = piece.segment
            ttsCursorOffset = piece.offset + start.coerceIn(0, piece.text.length)
        }

    override fun onDone(utteranceId: String?) = postTtsPieceEvent(utteranceId) { _, index ->
        val next = ttsPieces.getOrNull(index + 1)
        if (next == null) {
            finishTtsQueue()
        } else {
            // Covers a pause in the gap before the next onStart.
            ttsCursorSegment = next.segment
            ttsCursorOffset = next.offset
            ttsReadyEngine()?.let { enqueueTtsPieces(it, index + 1 + TTS_PIECES_AHEAD) }
        }
    }

    // Errors end the request without completion; the session never advances
    // to another chapter on a failure.
    override fun onError(utteranceId: String?, errorCode: Int) =
        postTtsPieceEvent(utteranceId) { _, _ -> failTtsQueue() }

    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) =
        postTtsPieceEvent(utteranceId) { _, _ -> failTtsQueue() }

    // Reports our own stop() or QUEUE_FLUSH: a cancel, never a finish.
    override fun onStop(utteranceId: String?, interrupted: Boolean) {}
}

private fun ttsReadyEngine(): TextToSpeech? = ttsInstance?.takeIf { ttsReady }

private fun ensureTtsEngine(context: PlatformContext) {
    synchronized(ttsLock) {
        // Already bound; nothing to do.
        if (ttsInstance != null && ttsReady) return
        // Binding in flight; wait for the existing init callback rather than
        // tearing it down. Tearing down an unbound instance produces
        // "shutdown failed: not bound to TTS engine" warnings and orphans the
        // pending bind, so subsequent calls keep recreating the engine.
        if (ttsBinding) return
        ttsBinding = true
        ttsReady = false
        ttsInstance = TextToSpeech(context.applicationContext) { status ->
            synchronized(ttsLock) {
                ttsBinding = false
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    ttsInstance?.setOnUtteranceProgressListener(ttsProgressListener)
                } else {
                    // Bind failed; clear the half-init reference so a future
                    // ensureTtsEngine call can retry from a clean slate.
                    ttsInstance = null
                }
            }
            // Start or fail the queue that waited for this bind, if it is
            // still wanted; Stop and Pause clear ttsStartPending.
            mainHandler.post {
                if (!ttsStartPending) return@post
                if (ttsReadyEngine() != null) startTtsQueue(context) else failTtsQueue()
            }
        }
    }
}

// Queues pieces from the cursor: a new request, a resume, or a request that
// waited for the engine to bind.
private fun startTtsQueue(context: PlatformContext) {
    val queue = ttsQueue ?: return
    val engine = ttsReadyEngine()
    if (engine == null) {
        ttsStartPending = true
        ensureTtsEngine(context)
        return
    }
    ttsStartPending = false
    if (engine.setLanguage(queue.locale) < TextToSpeech.LANG_AVAILABLE) {
        engine.setLanguage(Locale.US)
    }
    val maxLen = (TextToSpeech.getMaxSpeechInputLength() - 50).coerceAtLeast(500)
    val pieces = ArrayList<TtsPiece>()
    for (segment in ttsCursorSegment until queue.segments.size) {
        val from = if (segment == ttsCursorSegment) ttsCursorOffset else 0
        ttsSpeechPieces(queue.segments[segment], from, maxLen).forEach { (offset, text) ->
            pieces += TtsPiece(segment, offset, text)
        }
    }
    ttsPieces = pieces
    ttsNextPiece = 0
    if (pieces.isEmpty()) {
        finishTtsQueue()
        return
    }
    enqueueTtsPieces(engine, TTS_PIECES_AHEAD)
}

private fun enqueueTtsPieces(engine: TextToSpeech, upTo: Int) {
    val generation = ttsGeneration
    val limit = minOf(upTo, ttsPieces.size)
    while (ttsNextPiece < limit) {
        val index = ttsNextPiece++
        val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (engine.speak(ttsPieces[index].text, mode, null, "$generation:$index") == TextToSpeech.ERROR) {
            failTtsQueue()
            return
        }
    }
}

private fun clearTtsQueue() {
    ttsGeneration++
    ttsQueue = null
    ttsPieces = emptyList()
    ttsNextPiece = 0
    ttsCursorSegment = 0
    ttsCursorOffset = 0
    ttsPaused = false
    ttsStartPending = false
}

// Events are posted so the session never re-enters itself from inside a call.
private fun postTtsEvent(event: TtsPlatformEvent) {
    mainHandler.post { ttsEventSink?.invoke(event) }
}

private fun finishTtsQueue() {
    val queue = ttsQueue ?: return
    clearTtsQueue()
    postTtsEvent(TtsPlatformEvent.Finished(queue.requestId))
}

private fun failTtsQueue() {
    val queue = ttsQueue ?: return
    clearTtsQueue()
    if (ttsReady) runCatching { ttsInstance?.stop() }
    postTtsEvent(TtsPlatformEvent.Failed(queue.requestId))
}

actual fun platformTtsInit(context: PlatformContext) {
    ensureTtsEngine(context)
}

actual fun platformTtsSetEventSink(sink: ((TtsPlatformEvent) -> Unit)?) {
    ttsEventSink = sink
}

actual fun platformTtsSpeakQueue(
    context: PlatformContext,
    requestId: Long,
    segments: List<String>,
    languageTag: String
) {
    clearTtsQueue()
    ttsQueue = TtsQueue(requestId, segments, ttsLocale(languageTag))
    // The first piece uses QUEUE_FLUSH, which replaces anything still playing.
    // If the engine is not bound yet, nothing is playing to replace.
    startTtsQueue(context)
}

actual fun platformTtsStop(context: PlatformContext) {
    clearTtsQueue()
    // Calling stop() on an unbound TTS instance logs "stop failed: not bound
    // to TTS engine". Guard on ttsReady so stop is a true no-op when no
    // engine is bound yet; clearTtsQueue already cancelled any pending start.
    if (ttsReady) runCatching { ttsInstance?.stop() }
}

actual fun platformTtsPause(context: PlatformContext): Boolean {
    if (ttsQueue == null) return false
    if (ttsPaused) return true
    // Drop callbacks from the pieces stop() is about to cut off; the cursor
    // keeps the word (or sentence) to resume from.
    ttsGeneration++
    ttsPaused = true
    ttsStartPending = false
    if (ttsReady) runCatching { ttsInstance?.stop() }
    return true
}

actual fun platformTtsResume(context: PlatformContext) {
    if (ttsQueue == null || !ttsPaused) return
    ttsPaused = false
    ttsGeneration++
    startTtsQueue(context)
}

// ---- ONNX Runtime ----

@Volatile private var ortEnv: ai.onnxruntime.OrtEnvironment? = null
@Volatile private var ortSession: ai.onnxruntime.OrtSession? = null
private val ortInitLock = Any()

actual fun platformOnnxInit(context: PlatformContext): Boolean {
    if (ortSession != null) return true
    // Double-checked locking. Two callers (search-prewarm and search-input)
    // can race past the volatile read above; without the synchronized block
    // they both create sessions and one leaks. Logcat confirmed two
    // "session created successfully" lines in the same millisecond.
    synchronized(ortInitLock) {
        if (ortSession != null) return true
        return try {
            // The 113 MB ONNX model ships in the fast-follow embedding_assets
            // pack. If the pack has finished downloading, load the model
            // directly from its on-disk location (no copy needed). Otherwise
            // fall back to the legacy assets path (for backwards compat with
            // any install-time delivery in dev/debug builds). Return false
            // when neither resolves so the search path skips semantic mode
            // gracefully until the pack arrives.
            val packModel = assetPackFile(context, "embedding/model_quantized.onnx")
            val modelPath: String = when {
                packModel != null -> packModel.absolutePath
                else -> {
                    val modelFile = File(File(context.filesDir, "embedding"), "model_quantized.onnx")
                    if (!modelFile.exists()) {
                        modelFile.parentFile?.mkdirs()
                        println("ONNX: extracting model from assets...")
                        val opened = runCatching {
                            context.assets.open("embedding/model_quantized.onnx")
                        }.getOrNull()
                        if (opened == null) {
                            println("ONNX: model not available yet (pack still downloading?)")
                            return false
                        }
                        opened.use { input ->
                            modelFile.outputStream().use { output ->
                                input.copyTo(output, bufferSize = 65536)
                            }
                        }
                        println("ONNX: model extracted (${modelFile.length() / 1024 / 1024} MB)")
                    }
                    modelFile.absolutePath
                }
            }
            val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
            ortEnv = env
            val opts = ai.onnxruntime.OrtSession.SessionOptions()
            opts.setIntraOpNumThreads(2)
            ortSession = env.createSession(modelPath, opts)
            println("ONNX: session created successfully")
            true
        } catch (e: Throwable) {
            println("ONNX: init failed: ${e.message}")
            false
        }
    }
}

actual fun platformOnnxInference(inputIds: LongArray, attentionMask: LongArray): FloatArray? {
    val session = ortSession ?: return null
    val env = ortEnv ?: return null
    var idTensor: ai.onnxruntime.OnnxTensor? = null
    var maskTensor: ai.onnxruntime.OnnxTensor? = null
    var result: ai.onnxruntime.OrtSession.Result? = null
    return try {
        val shape = longArrayOf(1, inputIds.size.toLong())
        idTensor = ai.onnxruntime.OnnxTensor.createTensor(
            env, java.nio.LongBuffer.wrap(inputIds), shape
        )
        maskTensor = ai.onnxruntime.OnnxTensor.createTensor(
            env, java.nio.LongBuffer.wrap(attentionMask), shape
        )
        result = session.run(
            mapOf("input_ids" to idTensor, "attention_mask" to maskTensor)
        )
        val outputValue = result.get("last_hidden_state").get()
        @Suppress("UNCHECKED_CAST")
        val output3d = outputValue.value as Array<Array<FloatArray>>
        val seqLen = inputIds.size
        val dim = output3d[0][0].size
        val flat = FloatArray(seqLen * dim)
        for (i in 0 until seqLen) {
            output3d[0][i].copyInto(flat, i * dim)
        }
        flat
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { result?.close() }
        runCatching { idTensor?.close() }
        runCatching { maskTensor?.close() }
    }
}

actual fun platformOnnxIsReady(): Boolean = ortSession != null
