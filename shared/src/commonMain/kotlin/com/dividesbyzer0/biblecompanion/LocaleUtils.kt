package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleLanguage
import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleScript
import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleCountry
import com.dividesbyzer0.biblecompanion.platform.platformLanguageFromTag
import com.dividesbyzer0.biblecompanion.platform.platformScriptFromTag
import com.dividesbyzer0.biblecompanion.platform.platformCountryFromTag

object LocaleUtils {
    fun effectiveAssetTag(appLang: String): String {
        if (!appLang.equals("system", true)) {
            return when (appLang.lowercase()) {
                "zh-hant" -> "zh-Hant"
                "zh-hans" -> "zh-Hans"
                else -> appLang.lowercase()
            }
        }

        val lang = platformGetDefaultLocaleLanguage()
        val script = platformGetDefaultLocaleScript()
        val country = platformGetDefaultLocaleCountry()

        return when (lang) {
            "es","fr","it","ru","pt","de","ko","hi","ar","ja" -> lang
            "zh" -> if (script.contains("hant", ignoreCase = true) || country in setOf("TW","HK","MO")) "zh-Hant" else "zh-Hans"
            else -> "en"
        }
    }

    fun isEnglishTag(tag: String): Boolean =
        tag.lowercase().startsWith("en")
}
