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
            val normalizedInput = appLang.replace('_', '-')
            val language = platformLanguageFromTag(normalizedInput)
            val script = platformScriptFromTag(normalizedInput)
            val country = platformCountryFromTag(normalizedInput)
            return supportedAssetTag(language, script, country)
                ?: normalizedInput.lowercase()
        }

        val lang = platformGetDefaultLocaleLanguage()
        val script = platformGetDefaultLocaleScript()
        val country = platformGetDefaultLocaleCountry()

        return supportedAssetTag(lang, script, country) ?: "en"
    }

    private fun supportedAssetTag(language: String, script: String, country: String): String? =
        when (language.lowercase()) {
            "en" -> "en"
            "es", "fr", "it", "ru", "pt", "de", "ko", "hi", "ar", "ja" ->
                language.lowercase()
            "zh" -> when (script.lowercase()) {
                "hant" -> "zh-Hant"
                "hans" -> "zh-Hans"
                else -> if (country.uppercase() in setOf("TW", "HK", "MO")) "zh-Hant" else "zh-Hans"
            }
            else -> null
        }

    fun isEnglishTag(tag: String): Boolean =
        tag.lowercase().startsWith("en")
}
