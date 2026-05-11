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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

actual typealias PlatformContext = Context

actual fun readAssetText(context: PlatformContext, path: String): String? = runCatching {
    context.assets.open(path).bufferedReader().use { it.readText() }
}.getOrNull()

actual fun assetExists(context: PlatformContext, path: String): Boolean = runCatching {
    context.assets.open(path).close()
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

actual fun platformSetAppLocale(tag: String) {
    val resolved = when (tag) {
        "zh-Hans" -> "zh-CN"
        "zh-Hant" -> "zh-TW"
        else -> tag
    }
    val target = if (resolved == "system")
        LocaleListCompat.getEmptyLocaleList()
    else
        LocaleListCompat.forLanguageTags(resolved)
    if (AppCompatDelegate.getApplicationLocales() != target) {
        AppCompatDelegate.setApplicationLocales(target)
    }
}

actual fun platformRecreateApp(context: PlatformContext) {
    (context as? Activity)?.recreate()
}

actual fun normalizeNFKD(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKD)
actual fun normalizeNFKC(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

actual fun httpGetForPreflight(url: String, timeoutMs: Int, maxBytes: Int): Pair<Int, String> {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BibleCompanion/1.0")
        conn.setRequestProperty("Accept-Encoding", "identity")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream ?: return code to ""
        val buf = ByteArray(maxBytes)
        var off = 0
        while (true) {
            val n = stream.read(buf, off, kotlin.math.min(4096, maxBytes - off))
            if (n <= 0 || off >= maxBytes) break
            off += n
        }
        code to String(buf, 0, off).lowercase(Locale.US)
    } catch (_: Throwable) {
        200 to "" // permissive on error
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
@Volatile private var ttsPendingText: String? = null
@Volatile private var ttsPendingLocale: Locale? = null
@Volatile var ttsOnDone: (() -> Unit)? = null
@Volatile private var ttsLastText: String? = null
@Volatile private var ttsLastLocale: Locale? = null
@Volatile private var ttsPaused: Boolean = false
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

private fun doSpeak(engine: TextToSpeech, text: String, locale: Locale) {
    ttsLastText = text
    ttsLastLocale = locale
    ttsPaused = false
    val langResult = engine.setLanguage(locale)
    if (langResult < TextToSpeech.LANG_AVAILABLE) {
        engine.setLanguage(Locale.US)
    }
    val speakResult = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "votd")
    if (speakResult == TextToSpeech.ERROR) {
        mainHandler.post { ttsOnDone?.invoke() }
    }
}

private fun ensureTtsEngine(context: PlatformContext) {
    synchronized(ttsLock) {
        if (ttsInstance != null && ttsReady) return
        ttsInstance?.shutdown()
        ttsReady = false
        ttsInstance = TextToSpeech(context.applicationContext) { status ->
            synchronized(ttsLock) {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    val engine = ttsInstance ?: return@synchronized
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { mainHandler.post { ttsOnDone?.invoke() } }
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
    ttsInstance?.stop()
}

actual fun platformTtsIsSpeaking(context: PlatformContext): Boolean =
    ttsInstance?.isSpeaking == true

actual fun platformTtsSetOnDone(callback: (() -> Unit)?) {
    ttsOnDone = callback
}

// Android TextToSpeech has no native pause/resume. Emulate by stopping and
// re-speaking the full utterance on resume. Fine for chapter-at-a-time TTS
// where the unit of playback is one story.
actual fun platformTtsPause(context: PlatformContext) {
    ttsInstance?.stop()
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

