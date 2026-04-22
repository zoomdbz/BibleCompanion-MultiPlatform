package com.dividesbyzer0.biblecompanion

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleLanguage
import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleScript
import com.dividesbyzer0.biblecompanion.platform.platformLanguageFromTag
import com.dividesbyzer0.biblecompanion.platform.platformScriptFromTag
import org.jetbrains.compose.resources.Font

val MerriweatherFamily: FontFamily
    @androidx.compose.runtime.Composable get() = FontFamily(
        Font(Res.font.serif_reading_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.serif_reading_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.serif_reading_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.serif_reading_bolditalic, FontWeight.Bold, FontStyle.Italic)
    )

val NotoSerifArabic: FontFamily
    @androidx.compose.runtime.Composable get() = FontFamily(
        Font(Res.font.noto_serif_arabic_regular, FontWeight.Normal),
        Font(Res.font.noto_serif_arabic_bold, FontWeight.Bold),
        Font(Res.font.noto_serif_arabic_regular, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.noto_serif_arabic_bold, FontWeight.Bold, FontStyle.Italic)
    )

val NotoSerifDevanagari: FontFamily
    @androidx.compose.runtime.Composable get() = FontFamily(
        Font(Res.font.noto_serif_devanagari_regular, FontWeight.Normal),
        Font(Res.font.noto_serif_devanagari_bold, FontWeight.Bold),
        Font(Res.font.noto_serif_devanagari_regular, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.noto_serif_devanagari_bold, FontWeight.Bold, FontStyle.Italic)
    )

val NotoSerifJP: FontFamily
    @androidx.compose.runtime.Composable get() = FontFamily(
        Font(Res.font.noto_serif_jp_regular, FontWeight.Normal),
        Font(Res.font.noto_serif_jp_bold, FontWeight.Bold),
        Font(Res.font.noto_serif_jp_regular, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.noto_serif_jp_bold, FontWeight.Bold, FontStyle.Italic)
    )

val NotoSerifKR: FontFamily
    @androidx.compose.runtime.Composable get() = FontFamily(
        Font(Res.font.noto_serif_kr_regular, FontWeight.Normal),
        Font(Res.font.noto_serif_kr_bold, FontWeight.Bold),
        Font(Res.font.noto_serif_kr_regular, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.noto_serif_kr_bold, FontWeight.Bold, FontStyle.Italic)
    )

val NotoSerifSC: FontFamily
    @androidx.compose.runtime.Composable get() = FontFamily(
        Font(Res.font.noto_serif_sc_regular, FontWeight.Normal),
        Font(Res.font.noto_serif_sc_bold, FontWeight.Bold),
        Font(Res.font.noto_serif_sc_regular, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.noto_serif_sc_bold, FontWeight.Bold, FontStyle.Italic)
    )

val NotoSerifTC: FontFamily
    @androidx.compose.runtime.Composable get() = FontFamily(
        Font(Res.font.noto_serif_tc_regular, FontWeight.Normal),
        Font(Res.font.noto_serif_tc_bold, FontWeight.Bold),
        Font(Res.font.noto_serif_tc_regular, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.noto_serif_tc_bold, FontWeight.Bold, FontStyle.Italic)
    )

@androidx.compose.runtime.Composable
fun serifFontForLanguage(langTag: String): FontFamily {
    val lang = if (langTag.equals("system", true)) platformGetDefaultLocaleLanguage()
               else platformLanguageFromTag(langTag)
    val script = if (langTag.equals("system", true)) platformGetDefaultLocaleScript()
                 else platformScriptFromTag(langTag)
    return when (lang) {
        "ar" -> NotoSerifArabic
        "hi" -> NotoSerifDevanagari
        "ja" -> NotoSerifJP
        "ko" -> NotoSerifKR
        "zh" -> when {
            script.contains("hant", ignoreCase = true) -> NotoSerifTC
            else -> NotoSerifSC
        }
        else -> MerriweatherFamily
    }
}

private fun TextStyle.scaled(factor: Float): TextStyle =
    copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

@androidx.compose.runtime.Composable
fun buildSerifTypography(langTag: String, scale: Float = 1.0f): Typography {
    val family = serifFontForLanguage(langTag)
    return Typography().run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = family).scaled(scale),
            displayMedium = displayMedium.copy(fontFamily = family).scaled(scale),
            displaySmall = displaySmall.copy(fontFamily = family).scaled(scale),
            headlineLarge = headlineLarge.copy(fontFamily = family).scaled(scale),
            headlineMedium = headlineMedium.copy(fontFamily = family).scaled(scale),
            headlineSmall = headlineSmall.copy(fontFamily = family).scaled(scale),
            titleLarge = titleLarge.copy(fontFamily = family).scaled(scale),
            titleMedium = titleMedium.copy(fontFamily = family).scaled(scale),
            titleSmall = titleSmall.copy(fontFamily = family).scaled(scale),
            bodyLarge = bodyLarge.copy(fontFamily = family).scaled(scale),
            bodyMedium = bodyMedium.copy(fontFamily = family).scaled(scale),
            bodySmall = bodySmall.copy(fontFamily = family).scaled(scale),
            labelLarge = labelLarge.copy(fontFamily = family).scaled(scale),
            labelMedium = labelMedium.copy(fontFamily = family).scaled(scale),
            labelSmall = labelSmall.copy(fontFamily = family).scaled(scale),
        )
    }
}

@androidx.compose.runtime.Composable
fun buildScaledTypography(scale: Float): Typography {
    if (scale == 1.0f) return Typography()
    return Typography().run {
        Typography(
            displayLarge = displayLarge.scaled(scale),
            displayMedium = displayMedium.scaled(scale),
            displaySmall = displaySmall.scaled(scale),
            headlineLarge = headlineLarge.scaled(scale),
            headlineMedium = headlineMedium.scaled(scale),
            headlineSmall = headlineSmall.scaled(scale),
            titleLarge = titleLarge.scaled(scale),
            titleMedium = titleMedium.scaled(scale),
            titleSmall = titleSmall.scaled(scale),
            bodyLarge = bodyLarge.scaled(scale),
            bodyMedium = bodyMedium.scaled(scale),
            bodySmall = bodySmall.scaled(scale),
            labelLarge = labelLarge.scaled(scale),
            labelMedium = labelMedium.scaled(scale),
            labelSmall = labelSmall.scaled(scale),
        )
    }
}
