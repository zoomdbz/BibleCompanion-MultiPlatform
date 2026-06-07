package com.dividesbyzer0.biblecompanion.platform

import android.app.Activity
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
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

actual fun platformOpenUrlInBrowser(context: PlatformContext, url: String) {
    val base = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val tried = runCatching {
        val chrome = base.cloneFilter().setPackage("com.android.chrome")
        context.startActivity(Intent(chrome).setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
    if (!tried) runCatching { context.startActivity(base) }
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
@Volatile private var ttsPendingText: String? = null
@Volatile private var ttsPendingLocale: Locale? = null
@Volatile var ttsOnDone: (() -> Unit)? = null
@Volatile private var ttsLastText: String? = null
@Volatile private var ttsLastLocale: Locale? = null
@Volatile private var ttsPaused: Boolean = false
// The utteranceId of the final queued chunk. onDone fires the UI callback only
// when this one completes, so multi-chunk chapters do not report "finished"
// after the first chunk. See doSpeak / ttsChunks.
@Volatile private var ttsFinalUtteranceId: String? = null
private val mainHandler = Handler(Looper.getMainLooper())
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
        "uk" -> "uk-UA"
        else -> languageTag
    }
    return Locale.forLanguageTag(mapped)
}

// Android's TextToSpeech.speak() silently returns ERROR (nothing plays) when
// the input exceeds TextToSpeech.getMaxSpeechInputLength(), typically 4000
// characters. Long chapters (Matthew 5 at ~5200 chars, Psalm 119, 1 Enoch,
// Jubilees, and hundreds of others) tripped this and produced dead air while
// shorter neighbors worked. Split oversized text into engine-sized chunks at
// sentence or clause boundaries and queue them in order.
private fun ttsChunks(text: String, maxLen: Int): List<String> {
    if (text.length <= maxLen) return listOf(text)
    val breakChars = charArrayOf('.', '!', '?', '。', '！', '？', '；', ';', '\n')
    val out = ArrayList<String>()
    var start = 0
    val n = text.length
    while (start < n) {
        var end = minOf(start + maxLen, n)
        if (end < n) {
            val window = text.substring(start, end)
            val half = maxLen / 2
            val sentenceAt = window.lastIndexOfAny(breakChars)
            val spaceAt = window.lastIndexOf(' ')
            val cut = when {
                sentenceAt >= half -> sentenceAt + 1
                spaceAt >= half -> spaceAt + 1
                else -> window.length // no good boundary; hard cut
            }
            end = start + cut
        }
        val piece = text.substring(start, end).trim()
        if (piece.isNotEmpty()) out.add(piece)
        start = end
    }
    return if (out.isEmpty()) listOf(text.take(maxLen)) else out
}

private fun doSpeak(engine: TextToSpeech, text: String, locale: Locale) {
    ttsLastText = text
    ttsLastLocale = locale
    ttsPaused = false
    val langResult = engine.setLanguage(locale)
    if (langResult < TextToSpeech.LANG_AVAILABLE) {
        engine.setLanguage(Locale.US)
    }
    val maxLen = (TextToSpeech.getMaxSpeechInputLength() - 50).coerceAtLeast(500)
    val chunks = ttsChunks(text, maxLen)
    ttsFinalUtteranceId = "tts-${chunks.size - 1}"
    var anyError = false
    chunks.forEachIndexed { i, chunk ->
        val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(chunk, mode, null, "tts-$i")
        if (result == TextToSpeech.ERROR) anyError = true
    }
    if (anyError) {
        mainHandler.post { ttsOnDone?.invoke() }
    }
}

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
                    val engine = ttsInstance ?: return@synchronized
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        // Fire the UI "done" callback only when the LAST queued
                        // chunk finishes; intermediate chunks keep playback alive.
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == ttsFinalUtteranceId) mainHandler.post { ttsOnDone?.invoke() }
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) { mainHandler.post { ttsOnDone?.invoke() } }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { mainHandler.post { ttsOnDone?.invoke() } }
                    })
                    val pText = ttsPendingText
                    val pLocale = ttsPendingLocale
                    if (pText != null && pLocale != null) {
                        ttsPendingText = null
                        ttsPendingLocale = null
                        mainHandler.post { doSpeak(engine, pText, pLocale) }
                    }
                } else {
                    // Bind failed; clear the half-init reference so a future
                    // ensureTtsEngine call can retry from a clean slate.
                    ttsInstance = null
                    mainHandler.post { ttsOnDone?.invoke() }
                }
            }
        }
    }
}

actual fun platformTtsInit(context: PlatformContext) {
    ensureTtsEngine(context)
}

actual fun platformTtsSpeak(context: PlatformContext, text: String, languageTag: String) {
    val locale = ttsLocale(languageTag)
    synchronized(ttsLock) {
        val tts = ttsInstance
        if (tts != null && ttsReady) {
            ttsPendingText = null
            ttsPendingLocale = null
            doSpeak(tts, text, locale)
        } else {
            ttsPendingText = text
            ttsPendingLocale = locale
            ensureTtsEngine(context)
        }
    }
}

actual fun platformTtsStop(context: PlatformContext) {
    // Calling stop() on an unbound TTS instance logs "stop failed: not bound
    // to TTS engine". Guard on ttsReady so stop is a true no-op when no
    // engine is bound yet.
    if (!ttsReady) return
    runCatching { ttsInstance?.stop() }
}

actual fun platformTtsIsSpeaking(context: PlatformContext): Boolean =
    ttsReady && (ttsInstance?.isSpeaking == true)

actual fun platformTtsSetOnDone(callback: (() -> Unit)?) {
    ttsOnDone = callback
}

// Android TextToSpeech has no native pause/resume. Emulate by stopping and
// re-speaking the full utterance on resume. Fine for chapter-at-a-time TTS
// where the unit of playback is one story.
actual fun platformTtsPause(context: PlatformContext) {
    if (ttsReady) runCatching { ttsInstance?.stop() }
    ttsPaused = true
}

actual fun platformTtsResume(context: PlatformContext) {
    val text = ttsLastText ?: return
    val locale = ttsLastLocale ?: return
    synchronized(ttsLock) {
        val tts = ttsInstance
        if (tts != null && ttsReady) {
            ttsPaused = false
            doSpeak(tts, text, locale)
        } else {
            ttsPendingText = text
            ttsPendingLocale = locale
            ttsPaused = false
            ensureTtsEngine(context)
        }
    }
}

actual fun platformTtsIsPaused(context: PlatformContext): Boolean = ttsPaused

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

