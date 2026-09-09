package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import com.dividesbyzer0.biblecompanion.platform.assetExists
import com.dividesbyzer0.biblecompanion.platform.platformOpenUrl
import com.dividesbyzer0.biblecompanion.platform.normalizeNFKC
import com.dividesbyzer0.biblecompanion.platform.normalizeNFKD
import com.dividesbyzer0.biblecompanion.platform.ColorHsl
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

// Unicode-aware word boundary: \b in Kotlin Regex is ASCII-only, so non-ASCII tokens
// (Arabic, Cyrillic, accented Latin) never match \bX\b. Use lookarounds against
// \p{L} (any Unicode letter) plus \p{Mn} (combining marks) instead.
private fun uwb(literal: String): Regex =
  Regex("(?<![\\p{L}\\p{Mn}])" + Regex.escape(literal) + "(?![\\p{L}\\p{Mn}])")

// Japanese and Korean do not put spaces around every word. A raw replacement
// of "主" or "주" corrupts ordinary words such as 主人 (master), 持ち主
// (owner), 주십시오 (please give), and 거주 (residence). Match the divine
// title in its grammatical forms and leave lexical compounds and verb stems
// alone.
private val japaneseLord = Regex(
  "主(?=(?:は|が|を|に|へ|の|と|よ|から|より|も|こそ|で|だ|です|なる|、|。|，|．|！|？|\\s|$))"
)
private val japaneseNonDivinePrefixes = listOf(
  "持ち", "救い", "ご", "領", "君", "家", "店", "船", "地", "当", "雇い",
  "造り", "創造", "所有"
)

private fun replaceJapaneseLord(text: String, transform: (String) -> String): String =
  japaneseLord.replace(text) { match ->
    val before = text.substring(0, match.range.first)
    if (japaneseNonDivinePrefixes.any { before.endsWith(it) }) match.value else transform(match.value)
  }

private val koreanLord = Regex(
  "(?<![가-힣])(?:주님|주(?=(?:께서|께|가|를|와|여|에게|앞|" +
    "의(?=\\s|[,.!?;:，。！？；：]|$)|는|\\s|[,.!?;:，。！？；：]|$)))"
)

private fun replaceKoreanLord(text: String, transform: (String) -> String): String =
  koreanLord.replace(text) { match ->
    if (match.value != "주" || text.getOrNull(match.range.last + 1) != '는') {
      transform(match.value)
    } else {
      // 주는 is ambiguous: it can be "the LORD [topic]" or the ordinary verb
      // "gives." The corpus contains many non-divine forms such as 보여 주는,
      // 풀어 주는 and 생명을 주는. Only accept the attested divine contexts.
      val before = text.substring(0, match.range.first).trimEnd()
      val previousWord = before.takeLastWhile { it in '\uAC00'..'\uD7A3' }
      val afterTopic = text.substring(match.range.last + 2).trimStart()
      val isDivine = previousWord == "나" ||
        previousWord == "그러나" ||
        (previousWord.isEmpty() &&
          (afterTopic.startsWith("자기") || afterTopic.startsWith("복도")))
      if (isDivine) transform(match.value) else match.value
    }
  }

private const val DIVINE_NAME_TOKEN = "\uFDD0"
private const val TRADITIONAL_NAME_TOKEN = "\uFDD1"
private const val DN_OPEN_TOKEN = "\uFDD2"
private const val DN_CLOSE_TOKEN = "\uFDD3"
private const val PRESERVED_NAME_OPEN_TOKEN = "\uFDD4"
private const val PRESERVED_NAME_CLOSE_TOKEN = "\uFDD5"

private val existingDnOpen = Regex("\\[DN\\s*]", RegexOption.IGNORE_CASE)
private val existingDnClose = Regex("\\[/\\s*DN\\s*]", RegexOption.IGNORE_CASE)
private val scriptureInlineTag = Regex(
  "\\[(?:/\\s*)?(?:J|DN|ADD)\\s*]",
  RegexOption.IGNORE_CASE
)
private val explicitLatinDivineName = Regex(
  "(?<![\\p{L}\\p{Mn}])(?:Yahweh|YHWH|YHVH|Yahuah|Yahveh|Jehovah|Jah)(?![\\p{L}\\p{Mn}])",
  RegexOption.IGNORE_CASE
)
private val traditionalConvertibleLatinDivineName = Regex(
  "(?<![\\p{L}\\p{Mn}])(?:Yahweh|YHWH|YHVH|Yahuah|Yah)(?![\\p{L}\\p{Mn}])",
  RegexOption.IGNORE_CASE
)
private val traditionalPreservedLatinDivineName = Regex(
  "(?<![\\p{L}\\p{Mn}])(?:Yahveh|Jehovah|Jah)(?![\\p{L}\\p{Mn}])",
  RegexOption.IGNORE_CASE
)
private val englishAngelOfLord = Regex(
  "(?<![\\p{L}\\p{Mn}])(angels?\\s+of\\s+)(the\\s+)?(Lord)(?![\\p{L}\\p{Mn}])",
  RegexOption.IGNORE_CASE
)
private val spanishAngelOfLord = Regex(
  "(?<![\\p{L}\\p{Mn}])(ángel(?:es)?\\s+)(del\\s+)(Señor)(?![\\p{L}\\p{Mn}])",
  RegexOption.IGNORE_CASE
)
private val frenchAngelOfLord = Regex(
  "(?<![\\p{L}\\p{Mn}])(anges?\\s+)(du\\s+)(Seigneur)(?![\\p{L}\\p{Mn}])",
  RegexOption.IGNORE_CASE
)
private val germanAngelOfLord = Regex(
  "(?<![\\p{L}\\p{Mn}])(Engel(?:n|s)?\\s+)des\\s+(Herrn)(?![\\p{L}\\p{Mn}])",
  RegexOption.IGNORE_CASE
)
private val hindiAngelOfLord = Regex(
  "(?<![\\p{L}\\p{Mn}])प्रभु(?=\\s+(?:का|के|की)\\s+(?:(?:एक)\\s+)?(?:स्वर्ग)?दूत)"
)
private val arabicLord = Regex(
  "(?<![\\p{L}\\p{Mn}])ا[\\p{Mn}]*ل[\\p{Mn}]*ر[\\p{Mn}]*ب[\\p{Mn}]*(?![\\p{L}\\p{Mn}])"
)
private val arabicYhwh = Regex(
  "(?<![\\p{L}\\p{Mn}])ي[\\p{Mn}]*ه[\\p{Mn}]*و[\\p{Mn}]*ه[\\p{Mn}]*(?![\\p{L}\\p{Mn}])"
)
private val russianLord = Regex(
  "(?<![\\p{L}\\p{Mn}])Господ(?:ь|а|у|ом|е|ень|нее|него|нему|ним|нем|них|няя|нюю|ней|ня|ню|не|ни)(?![\\p{L}\\p{Mn}])"
)

/** Removes display-only semantic markers while preserving their text content. */
internal fun stripScriptureInlineTags(text: String): String =
  scriptureInlineTag.replace(text, "")

/** Returns the first offset after a supported inline tag, or -1 when none starts at [off]. */
internal fun scriptureInlineTagEnd(
  text: String,
  off: Int,
  opening: Boolean,
  tag: String
): Int {
  if (off !in text.indices || text[off] != '[') return -1
  var cursor = off + 1
  if (!opening) {
    if (cursor >= text.length || text[cursor] != '/') return -1
    cursor++
    while (cursor < text.length && text[cursor].isWhitespace()) cursor++
  }
  if (cursor + tag.length > text.length) return -1
  if (!text.substring(cursor, cursor + tag.length).equals(tag, ignoreCase = true)) return -1
  cursor += tag.length
  while (cursor < text.length && text[cursor].isWhitespace()) cursor++
  if (cursor >= text.length || text[cursor] != ']') return -1
  return cursor + 1
}

/**
 * Applies [transform] only outside an existing [DN]...[/DN] span. This makes
 * Divine Name rendering idempotent and keeps a second rendering pass from
 * nesting color markers.
 */
private fun mapOutsideDivineNameTags(text: String, transform: (String) -> String): String {
  var cursor = 0
  val out = StringBuilder(text.length + 16)
  while (cursor < text.length) {
    val open = existingDnOpen.find(text, cursor)
    if (open == null) {
      out.append(transform(text.substring(cursor)))
      break
    }
    out.append(transform(text.substring(cursor, open.range.first)))
    val close = existingDnClose.find(text, open.range.last + 1)
    if (close == null) {
      // Preserve malformed pre-existing markup instead of making it worse.
      out.append(text.substring(open.range.first))
      break
    }
    out.append(text.substring(open.range.first, close.range.last + 1))
    cursor = close.range.last + 1
  }
  return out.toString()
}

private fun languageKey(lang: String): String {
  val tag = lang.replace('_', '-')
  return when {
    tag.startsWith("zh-Hant", ignoreCase = true) ||
      tag.startsWith("zh-TW", ignoreCase = true) ||
      tag.startsWith("zh-HK", ignoreCase = true) -> "zh-Hant"
    tag.startsWith("zh", ignoreCase = true) -> "zh-Hans"
    else -> tag.substringBefore('-').lowercase()
  }
}

private fun localizedDivineName(mode: String, lang: String): String = when (mode) {
  "yhwh" -> when (lang) {
    "ru" -> "ЙХВХ"
    "ar" -> "يهوه"
    else -> "YHWH"
  }
  "yhvh" -> "YHVH"
  else -> when (lang) {
    "es" -> "Yahvé"
    "pt" -> "Javé"
    "fr" -> "Yahvé"
    "de" -> "Jahwe"
    "it" -> "Yahweh"
    "ru" -> "Яхве"
    "ar" -> "يهوه"
    "hi" -> "याहवे"
    "ko" -> "야훼"
    "ja" -> "ヤハウェ"
    "zh-Hans", "zh-Hant" -> "雅威"
    else -> "Yahweh"
  }
}

private fun traditionalDivineName(lang: String): String = when (lang) {
  "en" -> "the LORD"
  "es" -> "SEÑOR"
  "pt" -> "SENHOR"
  "fr" -> "ÉTERNEL"
  "de" -> "HERR"
  "it" -> "SIGNORE"
  "ru" -> "Господь"
  "ar" -> "الرَّبّ"
  "hi" -> "यहोवा"
  "ko" -> "주"
  "ja" -> "主"
  "zh-Hans" -> "耶和华"
  "zh-Hant" -> "耶和華"
  else -> "the LORD"
}

private fun replaceEnglishOtTitles(text: String): String {
  var t = englishAngelOfLord.replace(text) { m ->
    m.groupValues[1] + DIVINE_NAME_TOKEN
  }
  return t
    .replace("the LORD GOD", DIVINE_NAME_TOKEN)
    .replace("The LORD GOD", DIVINE_NAME_TOKEN)
    .replace("THE LORD GOD", DIVINE_NAME_TOKEN)
    .replace("GOD the LORD", DIVINE_NAME_TOKEN)
    .replace("LORD GOD", DIVINE_NAME_TOKEN)
    .replace("GOD the Lord", "$DIVINE_NAME_TOKEN the Lord")
    .replace("GOD, the Lord", "$DIVINE_NAME_TOKEN, the Lord")
    .replace("Lord GOD", "Lord $DIVINE_NAME_TOKEN")
    .replace("the LORD", DIVINE_NAME_TOKEN)
    .replace("The LORD", DIVINE_NAME_TOKEN)
    .replace("THE LORD", DIVINE_NAME_TOKEN)
    .replace(Regex("\\bLORD\\b"), DIVINE_NAME_TOKEN)
    .replace(Regex("\\bGOD\\b"), DIVINE_NAME_TOKEN)
}

private fun replaceNameModeSegment(text: String, lang: String, isOt: Boolean): String {
  var t = text
  when (lang) {
    "es" -> {
      t = t.replace(uwb("Jehová"), DIVINE_NAME_TOKEN)
        .replace(uwb("Yahveh"), DIVINE_NAME_TOKEN)
        .replace(uwb("Yahvé"), DIVINE_NAME_TOKEN)
      if (isOt) t = spanishAngelOfLord.replace(t) { m ->
        m.groupValues[1] + "de " + DIVINE_NAME_TOKEN
      }.replace("del SEÑOR", "de $DIVINE_NAME_TOKEN")
        .replace("al SEÑOR", "a $DIVINE_NAME_TOKEN")
        .replace("El SEÑOR", DIVINE_NAME_TOKEN)
        .replace("el SEÑOR", DIVINE_NAME_TOKEN)
        .replace(uwb("SEÑOR"), DIVINE_NAME_TOKEN)
    }
    "pt" -> {
      t = t.replace(uwb("Javé"), DIVINE_NAME_TOKEN)
        .replace(uwb("Jeová"), DIVINE_NAME_TOKEN)
      if (isOt) t = t
        .replace("do SENHOR", "de $DIVINE_NAME_TOKEN")
        .replace("ao SENHOR", "a $DIVINE_NAME_TOKEN")
        .replace("O SENHOR", DIVINE_NAME_TOKEN)
        .replace("o SENHOR", DIVINE_NAME_TOKEN)
        .replace(uwb("SENHOR"), DIVINE_NAME_TOKEN)
        .replace("do Senhor", "de $DIVINE_NAME_TOKEN")
        .replace("ao Senhor", "a $DIVINE_NAME_TOKEN")
        .replace("no Senhor", "em $DIVINE_NAME_TOKEN")
        .replace("O Senhor", DIVINE_NAME_TOKEN)
        .replace("o Senhor", DIVINE_NAME_TOKEN)
        .replace(uwb("Senhor"), DIVINE_NAME_TOKEN)
    }
    "fr" -> {
      t = t.replace(uwb("Yahvé"), DIVINE_NAME_TOKEN)
        .replace(uwb("Yahveh"), DIVINE_NAME_TOKEN)
      if (isOt) t = frenchAngelOfLord.replace(t) { m ->
        m.groupValues[1] + "de " + DIVINE_NAME_TOKEN
      }.replace("l'Éternel", DIVINE_NAME_TOKEN)
        .replace("l’Éternel", DIVINE_NAME_TOKEN)
        .replace("L'Éternel", DIVINE_NAME_TOKEN)
        .replace("L’Éternel", DIVINE_NAME_TOKEN)
        .replace("l'ÉTERNEL", DIVINE_NAME_TOKEN)
        .replace("l’ÉTERNEL", DIVINE_NAME_TOKEN)
        .replace("L'ÉTERNEL", DIVINE_NAME_TOKEN)
        .replace("L’ÉTERNEL", DIVINE_NAME_TOKEN)
        .replace(uwb("ÉTERNEL"), DIVINE_NAME_TOKEN)
        .replace(uwb("Éternel"), DIVINE_NAME_TOKEN)
        .replace("du SEIGNEUR", "de $DIVINE_NAME_TOKEN")
        .replace("au SEIGNEUR", "à $DIVINE_NAME_TOKEN")
        .replace("Le SEIGNEUR", DIVINE_NAME_TOKEN)
        .replace("le SEIGNEUR", DIVINE_NAME_TOKEN)
        .replace(Regex("\\bSEIGNEUR\\b"), DIVINE_NAME_TOKEN)
    }
    "de" -> {
      t = t.replace(uwb("Jahwe"), DIVINE_NAME_TOKEN)
        .replace(uwb("Jehova"), DIVINE_NAME_TOKEN)
      if (isOt) t = germanAngelOfLord.replace(t) { m ->
        m.groupValues[1] + DIVINE_NAME_TOKEN + "s"
      }.replace("des HERRN", "${DIVINE_NAME_TOKEN}s")
        .replace("zum HERRN", "zu $DIVINE_NAME_TOKEN")
        .replace("vom HERRN", "von $DIVINE_NAME_TOKEN")
        .replace("am HERRN", "an $DIVINE_NAME_TOKEN")
        .replace("Der HERR", DIVINE_NAME_TOKEN)
        .replace("der HERR", DIVINE_NAME_TOKEN)
        .replace("dem HERRN", DIVINE_NAME_TOKEN)
        .replace("den HERRN", DIVINE_NAME_TOKEN)
        .replace(Regex("\\bHERRN?\\b"), DIVINE_NAME_TOKEN)
    }
    "it" -> {
      t = t.replace(uwb("Geova"), DIVINE_NAME_TOKEN)
      if (isOt) t = t
        .replace("del SIGNORE", "di $DIVINE_NAME_TOKEN")
        .replace("al SIGNORE", "a $DIVINE_NAME_TOKEN")
        .replace("Il SIGNORE", DIVINE_NAME_TOKEN)
        .replace("il SIGNORE", DIVINE_NAME_TOKEN)
        .replace(Regex("\\bSIGNORE\\b"), DIVINE_NAME_TOKEN)
        .replace("del Signore", "di $DIVINE_NAME_TOKEN")
        .replace("dal Signore", "da $DIVINE_NAME_TOKEN")
        .replace("nel Signore", "in $DIVINE_NAME_TOKEN")
        .replace("al Signore", "a $DIVINE_NAME_TOKEN")
        .replace("Il Signore", DIVINE_NAME_TOKEN)
        .replace("il Signore", DIVINE_NAME_TOKEN)
        .replace(Regex("\\bSignore\\b"), DIVINE_NAME_TOKEN)
    }
    "ru" -> {
      t = t.replace(uwb("Яхве"), DIVINE_NAME_TOKEN)
        .replace(uwb("Иегова"), DIVINE_NAME_TOKEN)
      if (isOt) t = t
        .replace("ГОСПОДЬ", DIVINE_NAME_TOKEN)
        .replace("ГОСПОДА", DIVINE_NAME_TOKEN)
        .replace("ГОСПОДУ", DIVINE_NAME_TOKEN)
        .replace("ГОСПОДОМ", DIVINE_NAME_TOKEN)
        .replace(russianLord, DIVINE_NAME_TOKEN)
    }
    "ar" -> {
      t = t.replace(arabicYhwh, DIVINE_NAME_TOKEN)
      if (isOt) t = t.replace(arabicLord, DIVINE_NAME_TOKEN)
    }
    "hi" -> {
      t = t.replace(uwb("यहोवा"), DIVINE_NAME_TOKEN)
        .replace(uwb("याहवे"), DIVINE_NAME_TOKEN)
      if (isOt) t = t.replace(hindiAngelOfLord, DIVINE_NAME_TOKEN)
    }
    "ko" -> {
      t = t.replace("여호와", DIVINE_NAME_TOKEN).replace("야훼", DIVINE_NAME_TOKEN)
      if (isOt) t = replaceKoreanLord(t) { DIVINE_NAME_TOKEN }
    }
    "ja" -> {
      t = t.replace("ヤハウェ", DIVINE_NAME_TOKEN)
        .replace("ヱホバ", DIVINE_NAME_TOKEN)
        .replace("エホバ", DIVINE_NAME_TOKEN)
      if (isOt) t = replaceJapaneseLord(t) { DIVINE_NAME_TOKEN }
    }
    "zh-Hans" -> {
      t = t.replace("耶和华", DIVINE_NAME_TOKEN).replace("雅威", DIVINE_NAME_TOKEN)
      if (isOt) t = t.replace(Regex("上主(?=的(?:天使|使者)|之(?:天使|使者))"), DIVINE_NAME_TOKEN)
    }
    "zh-Hant" -> {
      t = t.replace("耶和華", DIVINE_NAME_TOKEN).replace("雅威", DIVINE_NAME_TOKEN)
      if (isOt) t = t.replace(Regex("上主(?=的(?:天使|使者)|之(?:天使|使者))"), DIVINE_NAME_TOKEN)
    }
  }
  if (isOt) t = replaceEnglishOtTitles(t)
  return explicitLatinDivineName.replace(t, DIVINE_NAME_TOKEN)
}

private fun traditionalWrap(value: String): String = DN_OPEN_TOKEN + value + DN_CLOSE_TOKEN

private fun highlightEnglishOtTitles(text: String): String {
  var t = text
    .replace(Regex("\\bLORD\\b")) { traditionalWrap(it.value) }
    .replace(Regex("\\bGOD\\b")) { traditionalWrap(it.value) }
  t = englishAngelOfLord.replace(t) { m ->
    m.groupValues[1] + m.groupValues[2] + traditionalWrap(m.groupValues[3])
  }
  return t
}

private fun highlightTraditionalSegment(text: String, lang: String, isOt: Boolean): String {
  val preservedNames = mutableListOf<String>()
  var t = traditionalPreservedLatinDivineName.replace(text) { match ->
    val index = preservedNames.size
    preservedNames += match.value
    "$PRESERVED_NAME_OPEN_TOKEN$index$PRESERVED_NAME_CLOSE_TOKEN"
  }
  t = traditionalConvertibleLatinDivineName.replace(t, TRADITIONAL_NAME_TOKEN)
  if (isOt) t = highlightEnglishOtTitles(t)
  when (lang) {
    "en" -> Unit
    "es" -> {
      t = t.replace(uwb("SEÑOR")) { traditionalWrap(it.value) }
        .replace(uwb("Jehová")) { traditionalWrap(it.value) }
        .replace(uwb("Yahveh")) { traditionalWrap(it.value) }
        .replace(uwb("Yahvé")) { traditionalWrap(it.value) }
      if (isOt) t = spanishAngelOfLord.replace(t) { m ->
        m.groupValues[1] + m.groupValues[2] + traditionalWrap(m.groupValues[3])
      }
    }
    "pt" -> {
      t = t.replace(Regex("\\bSENHOR\\b")) { traditionalWrap(it.value) }
        .replace(uwb("Javé")) { traditionalWrap(it.value) }
        .replace(uwb("Jeová")) { traditionalWrap(it.value) }
      if (isOt) t = t.replace(Regex("\\bSenhor\\b")) { traditionalWrap(it.value) }
    }
    "fr" -> {
      t = t.replace(uwb("ÉTERNEL")) { traditionalWrap(it.value) }
        .replace(uwb("Éternel")) { traditionalWrap(it.value) }
        .replace(Regex("\\bSEIGNEUR\\b")) { traditionalWrap(it.value) }
        .replace(uwb("Yahvé")) { traditionalWrap(it.value) }
        .replace(uwb("Yahveh")) { traditionalWrap(it.value) }
      if (isOt) t = frenchAngelOfLord.replace(t) { m ->
        m.groupValues[1] + m.groupValues[2] + traditionalWrap(m.groupValues[3])
      }
    }
    "de" -> {
      t = t.replace(Regex("\\bHERRN?\\b")) { traditionalWrap(it.value) }
        .replace(uwb("Jahwe")) { traditionalWrap(it.value) }
        .replace(uwb("Jehova")) { traditionalWrap(it.value) }
      if (isOt) t = germanAngelOfLord.replace(t) { m ->
        m.groupValues[1] + "des " + traditionalWrap(m.groupValues[2])
      }
    }
    "it" -> {
      t = t.replace(Regex("\\bSIGNORE\\b")) { traditionalWrap(it.value) }
        .replace(uwb("Geova")) { traditionalWrap(it.value) }
      if (isOt) t = t.replace(Regex("\\bSignore\\b")) { traditionalWrap(it.value) }
    }
    "ru" -> {
      t = t.replace(Regex("ГОСПОДЬ|ГОСПОДА|ГОСПОДУ|ГОСПОДОМ")) { traditionalWrap(it.value) }
        .replace(uwb("Яхве")) { traditionalWrap(it.value) }
        .replace(uwb("Иегова")) { traditionalWrap(it.value) }
      if (isOt) t = t.replace(russianLord) { traditionalWrap(it.value) }
    }
    "ar" -> {
      t = t.replace(arabicYhwh) { traditionalWrap(it.value) }
      if (isOt) t = t.replace(arabicLord) { traditionalWrap(it.value) }
    }
    "hi" -> {
      t = t.replace(uwb("यहोवा")) { traditionalWrap(it.value) }
        .replace(uwb("याहवे")) { traditionalWrap(it.value) }
      if (isOt) t = t.replace(hindiAngelOfLord) { traditionalWrap(it.value) }
    }
    "ko" -> {
      t = t.replace(Regex("여호와|야훼")) { traditionalWrap(it.value) }
      if (isOt) t = replaceKoreanLord(t) { traditionalWrap(it) }
    }
    "ja" -> {
      t = t.replace(Regex("ヤハウェ|ヱホバ|エホバ")) { traditionalWrap(it.value) }
      if (isOt) t = replaceJapaneseLord(t) { traditionalWrap(it) }
    }
    "zh-Hans" -> {
      t = t.replace(Regex("耶和华|雅威")) { traditionalWrap(it.value) }
      if (isOt) t = t.replace(Regex("上主(?=的(?:天使|使者)|之(?:天使|使者))")) {
        traditionalWrap(it.value)
      }
    }
    "zh-Hant" -> {
      t = t.replace(Regex("耶和華|雅威")) { traditionalWrap(it.value) }
      if (isOt) t = t.replace(Regex("上主(?=的(?:天使|使者)|之(?:天使|使者))")) {
        traditionalWrap(it.value)
      }
    }
  }
  for ((index, value) in preservedNames.withIndex()) {
    t = t.replace(
      "$PRESERVED_NAME_OPEN_TOKEN$index$PRESERVED_NAME_CLOSE_TOKEN",
      traditionalWrap(value)
    )
  }
  return t
}

internal fun applyDivineName(
  text: String,
  mode: String,
  lang: String,
  colorActive: Boolean,
  collection: String = "old_testament"
): String {
  val lk = languageKey(lang)
  val isOt = collection == "old_testament" ||
    collection == "deuterocanonical" ||
    collection == "apocrypha" ||
    collection == "pseudepigrapha"

  if (mode == "traditional") {
    if (!colorActive) return text
    val traditionalName = traditionalDivineName(lk)
    return mapOutsideDivineNameTags(text) { segment ->
      highlightTraditionalSegment(segment, lk, isOt)
        .replace(TRADITIONAL_NAME_TOKEN, "[DN]$traditionalName[/DN]")
        .replace(DN_OPEN_TOKEN, "[DN]")
        .replace(DN_CLOSE_TOKEN, "[/DN]")
    }
  }

  val localizedName = localizedDivineName(mode, lk)
  val renderedName = if (colorActive) "[DN]$localizedName[/DN]" else localizedName
  return mapOutsideDivineNameTags(text) { segment ->
    replaceNameModeSegment(segment, lk, isOt).replace(DIVINE_NAME_TOKEN, renderedName)
  }
}

private val aliasJson = Json { ignoreUnknownKeys = true }

private data class AliasRow(
  val canon: String,
  val aliases: List<String>,
  val group: String?
)

private object BookAliases {
  fun load(ctx: PlatformContext, appLang: String): List<AliasRow> {
    val lang = LocaleUtils.effectiveAssetTag(appLang)
    val candidates = listOf("refs/$lang/book_aliases.json", "refs/en/book_aliases.json")
    val path = candidates.firstOrNull { p -> assetExists(ctx, p) } ?: return emptyList()
    val txt = readAssetText(ctx, path) ?: return emptyList()

    val arr = runCatching { aliasJson.parseToJsonElement(txt).jsonArray }.getOrNull() ?: return emptyList()
    val out = ArrayList<AliasRow>(arr.size)
    for (elem in arr) {
      val o = elem.jsonObject
      val canon = (o["canon"] as? JsonPrimitive)?.content?.trim() ?: continue
      if (canon.isEmpty()) continue
      val aliasElem = o["aliases"]
      val aliases: List<String> = when (aliasElem) {
        is JsonArray -> aliasElem.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.ifEmpty { null } }
        is JsonPrimitive -> listOf(aliasElem.content.trim()).filter { it.isNotEmpty() }
        else -> emptyList()
      }
      val group = (o["group"] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null }
      out += AliasRow(canon, aliases, group)
    }
    return out
  }
}

object ScriptureRefs {

  private data class BookEntry(
    val canon: String,           // English canonical — used for URL payload
    val displayCanon: String,    // Localized canonical — used for display substitution
    val collection: String,
    val keys: MutableSet<String>,
    val strippedKeys: MutableSet<String> = mutableSetOf(),
    val foldAsciiOnlyKeys: MutableSet<String> = mutableSetOf(),
    // The real asset id, read from _index.json rather than rebuilt from the
    // title. Deriving it by lowercasing the title and swapping spaces for
    // underscores was wrong for nine books: "Gospel of Thomas" asked for
    // gospel_of_thomas.json when the file is gospel_thomas.json, and
    // "Esther (Greek)" asked for esther_(greek).json. Every reference to those
    // books opened nothing. Empty falls back to the old derivation, which is
    // what englishSeed needs since it has no index to read.
    val bookId: String = ""
  )

  private fun assetBookId(entry: BookEntry): String =
    entry.bookId.ifEmpty { entry.canon.trim().lowercase().replace(' ', '_') }

  // Backed by a Compose mutableStateOf so that when primeBooks finishes after
  // the home screen has already composed (e.g. VOTD card), readers recompose
  // and scripture refs become tappable. Previously stored as @Volatile var,
  // which Compose can't observe; you had to navigate away/back to see refs work.
  private val booksState = mutableStateOf<List<BookEntry>>(emptyList())
  @kotlin.concurrent.Volatile private var bookKeyInitials: Set<Char> = emptySet()
  private var books: List<BookEntry>
    get() = booksState.value
    set(value) {
      // Compact-script notes may have no boundary before a citation. Keep this
      // lookup cheap so those paragraphs do not scan every character as a book.
      bookKeyInitials = value.asSequence()
        .flatMap { it.keys.asSequence() }
        .mapNotNull { it.firstOrNull()?.lowercaseChar() }
        .toSet()
      booksState.value = value
    }
  @kotlin.concurrent.Volatile private var lastLang: String? = null

  fun primeBooks(ctx: PlatformContext, appLanguage: String) {
    val tag = LocaleUtils.effectiveAssetTag(appLanguage)
    if (lastLang == tag && books.isNotEmpty()) return

    val cols = listOf("old_testament","new_testament","deuterocanonical","apocrypha","pseudepigrapha")
    val canonToCollection = mutableMapOf<String,String>()
    // _index.json gives (id, title) pairs. The id was being discarded here and
    // then guessed back from the title further down, which is the bug that made
    // nine books unreachable. Keep it.
    val canonToBookId = mutableMapOf<String,String>()
    for (c in cols) {
      val enPairs = ContentRepo.listBooksLocalized(ctx, c, "en")
      enPairs.forEach { (id, enTitle) ->
        canonToCollection[enTitle] = c
        canonToBookId[enTitle] = id
      }
    }

    val rows = BookAliases.load(ctx, tag)
    if (rows.isEmpty()) {
      books = englishSeed()
      lastLang = tag
      return
    }

    fun mapGroupToCollection(g: String?): String? = when (g?.lowercase()) {
      "canon" -> null
      "deuterocanon", "deuterocanonical" -> "deuterocanonical"
      "apocrypha" -> "apocrypha"
      "pseudepigrapha" -> "pseudepigrapha"
      else -> null
    }

    val out = mutableListOf<BookEntry>()

    fun addKey(dst: MutableSet<String>, raw: String, foldDst: MutableSet<String>? = null) {
      val t = raw.trim()
      if (t.isEmpty()) return
      dst += t
      val normSpaces = t.replace("\\s+".toRegex(), " ")
      dst += normSpaces
      val noSp = t.noSpaces()
      dst += noSp
      val folded = t.foldAscii()
      dst += folded
      if (foldDst != null && folded != t && folded != normSpaces && folded != noSp) {
        foldDst += folded
      }
    }

    for (row in rows) {
      val collection = run {
        canonToCollection[row.canon]
          ?: mapGroupToCollection(row.group)
          ?: row.aliases.firstNotNullOfOrNull { a -> canonToCollection[a] }
      } ?: continue

      val englishCanon: String = run {
        val names = listOf(row.canon) + row.aliases
        names.firstNotNullOfOrNull { name ->
          canonToCollection.keys.firstOrNull { en -> en.equals(name, ignoreCase = true) }
        } ?: row.canon
      }

      val entry = BookEntry(englishCanon, row.canon, collection, mutableSetOf(), mutableSetOf(),
                            mutableSetOf(), canonToBookId[englishCanon] ?: "")
      row.aliases.forEach { alias ->
        addKey(entry.keys, alias, entry.foldAsciiOnlyKeys)
        val stripped = stripLeadingOrdinal(alias)
        if (!stripped.equals(alias, ignoreCase = true)) {
          addKey(entry.keys, stripped, entry.foldAsciiOnlyKeys)
          addKey(entry.strippedKeys, stripped)
        }
      }
      addKey(entry.keys, row.canon, entry.foldAsciiOnlyKeys)
      run {
        val stripped = stripLeadingOrdinal(row.canon)
        if (!stripped.equals(row.canon, ignoreCase = true)) {
          addKey(entry.keys, stripped, entry.foldAsciiOnlyKeys)
          addKey(entry.strippedKeys, stripped)
        }
      }
      addKey(entry.keys, englishCanon, entry.foldAsciiOnlyKeys)
      out += entry
    }

    // Post-pass: remove foldAscii-derived keys from one entry when the same key
    // appears as a non-foldAscii (primary) key of a different entry. This prevents
    // diacritic collisions like Portuguese "Jó" (foldAscii -> "Jo") from hijacking
    // the explicit "Jo" alias of "João".
    val primaryOwnerLower = mutableMapOf<String, BookEntry>()
    for (entry in out) {
      val nonFold = entry.keys - entry.foldAsciiOnlyKeys
      for (k in nonFold) {
        val lk = k.lowercase()
        if (lk !in primaryOwnerLower) primaryOwnerLower[lk] = entry
      }
    }
    for (entry in out) {
      val toRemove = mutableSetOf<String>()
      for (foldKey in entry.foldAsciiOnlyKeys) {
        val owner = primaryOwnerLower[foldKey.lowercase()]
        if (owner != null && owner != entry) toRemove += foldKey
      }
      entry.keys -= toRemove
      entry.foldAsciiOnlyKeys -= toRemove
    }

    books = out
    lastLang = tag
  }

  fun collectionOf(canonBook: String?): String? {
    val name = canonBook?.trim() ?: return null
    return books.firstOrNull { it.canon.equals(name, ignoreCase = true) }?.collection
  }

  fun canonBookOfRef(refText: String): String? {
    val s = normalizeNFKC(refText)
      .replace('\u3000', ' ')
      .replace('\u00A0', ' ')
      .replace('\u202F', ' ')
    for (i in s.indices) {
      val prev = s.getOrNull(i - 1)
      if (!isLeftBoundary(prev)) continue
      val hit = scanBookAt(s, i) ?: continue
      return hit.first.canon
    }
    return null
  }

  fun canonicalizeRef(refText: String): String {
    if (books.isEmpty()) return refText
    val normalized = normalizeNFKC(refText)
      .replace('\u3000', ' ')
      .replace('\u00A0', ' ')
      .replace('\u202F', ' ')
    for (i in normalized.indices) {
      val prev = normalized.getOrNull(i - 1)
      if (!isLeftBoundary(prev)) continue
      val hit = scanBookAt(normalized, i) ?: continue
      val (entry, consumed) = hit
      return normalized.substring(0, i) + entry.canon + normalized.substring(i + consumed)
    }
    return refText
  }

  fun localizeRef(englishRef: String): String {
    if (books.isEmpty()) return englishRef
    val s = normalizeNFKC(englishRef)
      .replace('　', ' ')
      .replace(' ', ' ')
      .replace(' ', ' ')
    for (i in s.indices) {
      val prev = s.getOrNull(i - 1)
      if (!isLeftBoundary(prev)) continue
      val hit = scanBookAt(s, i) ?: continue
      val (entry, consumed) = hit
      if (entry.canon.equals(entry.displayCanon, ignoreCase = true)) return englishRef
      val before = s.substring(0, i)
      val after = s.substring(i + consumed)
      return "$before${entry.displayCanon}$after"
    }
    return englishRef
  }

  @Composable
  fun ClickableRefsText(
    text: String,
    collection: String,
    prefs: PrefsState,
    modifier: Modifier = Modifier,
    defaultBook: String? = null,
    allowRelativeInParensOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    onNonLinkClick: (() -> Unit)? = null
  ) {
    Internal(
      rawText = text,
      prefs = prefs,
      allowRelative = allowRelativeInParensOnly,
      relativeOnlyInParens = allowRelativeInParensOnly,
      inlineMarkdown = true,
      modifier = modifier,
      defaultBook = defaultBook,
      textStyle = textStyle,
      collection = collection,
      onNonLinkClick = onNonLinkClick
    )
  }

  @Composable
  fun ClickableRefsTextSmart(
    text: String,
    prefs: PrefsState,
    modifier: Modifier = Modifier,
    inlineMarkdown: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
  ) {
    Internal(
      rawText = text,
      prefs = prefs,
      allowRelative = true,
      relativeOnlyInParens = false,
      inlineMarkdown = inlineMarkdown,
      modifier = modifier,
      textStyle = textStyle,
      collection = "old_testament"
    )
  }

  @Composable
  private fun Internal(
    rawText: String,
    prefs: PrefsState,
    allowRelative: Boolean,
    relativeOnlyInParens: Boolean,
    inlineMarkdown: Boolean,
    modifier: Modifier,
    textStyle: TextStyle,
    collection: String,
    defaultBook: String? = null,
    onNonLinkClick: (() -> Unit)? = null
  ) {
    val defaultEntry: BookEntry? = books.firstOrNull {
      it.canon.equals(defaultBook, ignoreCase = true)
    }
    val ctx = LocalPlatformContext.current
    val linkStyle = SpanStyle(
      textDecoration = TextDecoration.Underline,
      color = MaterialTheme.colorScheme.primary
    )
    val jesusColor = jesusColorFromPrefs(prefs)
    val dnColor = divineNameColorFromPrefs(prefs)

    var dialog by remember { mutableStateOf<SwapDialog?>(null) }
    var noReaderDialog by remember { mutableStateOf(false) }
    var navGate by remember { mutableStateOf(false) }
    val internalNav = LocalInternalNavigate.current

    fun openUrl(u: String) = platformOpenUrl(ctx, u)

    val effectiveLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
    val compactReferenceLanguage = inlineMarkdown && when (effectiveLang) {
      "ja", "ko", "zh-Hans", "zh-Hant" -> true
      else -> false
    }

    val displayText = rawText
      .replace('\u3000', ' ')
      .replace('\u00A0', ' ')
      .replace('\u202F', ' ')
      .replace('\u2009', ' ')
      .replace('\u2002', ' ')
      .replace('\u2003', ' ')
      .let { applyDivineName(it, prefs.divineName, effectiveLang, prefs.divineNameColor != "default", collection) }

    // Normalize only the separate scanner copy. Every replacement stays one code
    // point wide so annotation offsets remain aligned while displayed Scripture
    // retains its original compatibility characters.
    val scanText = buildString(displayText.length) {
      for (ch in displayText) {
        append(
          when {
            ch == '\u3001' -> ','
            ch in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
            else -> ch
          }
        )
      }
    }

    val rawDisplayParts = displayText.split(Regex("[;\uFF1B]"))
    val rawScanParts = scanText.split(Regex("[;\uFF1B]"))
    val displayParts = mutableListOf<String>()
    val scanParts = mutableListOf<String>()
    run {
      var pi = 0
      while (pi < rawScanParts.size) {
        val curS = rawScanParts[pi].trim()
        val nxtS = rawScanParts.getOrNull(pi + 1)?.trim()
        if (curS.matches(Regex("^\\d+$")) && nxtS != null && nxtS.matches(Regex("^\\d+.*$"))) {
          scanParts += "${curS}:${nxtS}"
          displayParts += "${rawDisplayParts[pi].trim()}:${rawDisplayParts.getOrNull(pi + 1)?.trim() ?: ""}"
          pi += 2
        } else {
          scanParts += curS
          displayParts += rawDisplayParts[pi].trim()
          pi += 1
        }
      }
    }

    var carry: BookEntry? = defaultEntry

    val asText = buildAnnotatedString {
      val lastIdx = scanParts.lastIndex
      var jesusOn = false
      fun toggleJesus() {
        if (jesusOn) pop() else pushStyle(SpanStyle(color = jesusColor))
        jesusOn = !jesusOn
      }
      var dnOn = false
      fun toggleDN() {
        if (dnOn) pop() else pushStyle(SpanStyle(color = dnColor))
        dnOn = !dnOn
      }
      var addedWordOn = false
      fun toggleAddedWord() {
        if (addedWordOn) pop() else pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        addedWordOn = !addedWordOn
      }
      var pIdx = 0
      while (pIdx <= lastIdx) {
        val part = scanParts[pIdx].trim()
        val dp = displayParts[pIdx].trim()
        var i = 0
        var boldOn = false
        var italicOn = false

        fun toggleBold() { if (boldOn) pop() else pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); boldOn = !boldOn }
        fun toggleItalic() { if (italicOn) pop() else pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); italicOn = !italicOn }

        while (i < part.length) {
          val urlHit = scanUrlAt(part, i)
          if (urlHit != null) {
            val (end, url, display) = urlHit
            pushStringAnnotation("URL", url)
            withStyle(linkStyle) { append(display) }
            pop()
            i = end
            continue
          }

          val jOpenEnd  = scriptureInlineTagEnd(part, i, opening = true, "J")
          if (jOpenEnd > 0) { toggleJesus(); i = jOpenEnd; continue }
          val jCloseEnd = scriptureInlineTagEnd(part, i, opening = false, "J")
          if (jCloseEnd > 0) { if (jesusOn) toggleJesus(); i = jCloseEnd; continue }

          val dnOpenEnd  = scriptureInlineTagEnd(part, i, opening = true, "DN")
          if (dnOpenEnd > 0) { toggleDN(); i = dnOpenEnd; continue }
          val dnCloseEnd = scriptureInlineTagEnd(part, i, opening = false, "DN")
          if (dnCloseEnd > 0) { if (dnOn) toggleDN(); i = dnCloseEnd; continue }

          val addOpenEnd = scriptureInlineTagEnd(part, i, opening = true, "ADD")
          if (addOpenEnd > 0) { toggleAddedWord(); i = addOpenEnd; continue }
          val addCloseEnd = scriptureInlineTagEnd(part, i, opening = false, "ADD")
          if (addCloseEnd > 0) {
            if (addedWordOn) toggleAddedWord()
            i = addCloseEnd
            continue
          }

          val prev = part.getOrNull(i - 1)
          val normalBoundary = isLeftBoundary(prev)
          val relaxedLocalizedBoundary = compactReferenceLanguage && !normalBoundary &&
            isCompactRefStart(part, i)
          if (normalBoundary || relaxedLocalizedBoundary) {
            val hit = scanBookAt(part, i)
            if (hit != null) {
              val (entry, bookLen) = hit
              val afterBook = part.getOrNull(i + bookLen)
              val skipAfter = when {
                afterBook == '\u300B' -> 1
                afterBook == '.' && part.getOrNull(i + bookLen + 1).let { it == null || it.isWhitespace() || it.isDigit() } -> 1
                else -> 0
              }
              val consumed = bookLen + skipAfter
              val tailEnd = scanRefTail(part, i + consumed)
              if (tailEnd > i + consumed) {
                val rawTail = part.substring(i + consumed, tailEnd).trim()
                val tail = normalizeCjkTail(rawTail)
                val tailHasColon = tail.contains(':')
                val nextFew = part.substring(tailEnd, minOf(part.length, tailEnd + 6))
                val looksLikeThousands = !tailHasColon && Regex("^\\s*,\\s*\\d{3}").containsMatchIn(nextFew)
                val bookText = part.substring(i, i + bookLen).trim()
                val hasNonAsciiLetter = bookText.any { it.code > 0x7F && it.isLetter() }
                val shortAmbiguous = !tailHasColon && !hasNonAsciiLetter && bookText.length <= 3
                val relaxedTooShort = relaxedLocalizedBoundary && bookText.length < 2
                val relaxedWithoutExplicitTail = relaxedLocalizedBoundary &&
                  !tailHasColon &&
                  '\u7AE0' !in rawTail &&
                  '\uC7A5' !in rawTail

                if (looksLikeThousands || shortAmbiguous || relaxedTooShort || relaxedWithoutExplicitTail) {
                  append(dp.substring(i, tailEnd))
                  i = tailEnd
                  continue
                }

                val matchedBookRaw = dp.substring(i, i + consumed).trim()
                val matchedBook = if (entry.canon.equals(entry.displayCanon, ignoreCase = true))
                  matchedBookRaw else entry.displayCanon
                val displayTail = dp.substring(i + consumed, tailEnd).trim()
                val display = "$matchedBook $displayTail"

                val appLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
                val payload = RefPayload(
                  collection = entry.collection,
                  canonBook = entry.canon,
                  tail = tail,
                  translation = prefs.translation,
                  appLanguage = appLang,
                  readerMode = prefs.readerMode,
                  bookId = assetBookId(entry)
                )
                pushStringAnnotation("BIBLE_REF", payload.encode())
                withStyle(linkStyle) { append(display) }
                pop()

                carry = entry
                i = tailEnd
                continue
              }
            }
            if (allowRelative && (carry != null || defaultEntry != null)) {
              val insideParens = isInsideParens(part, i)
              if (!relativeOnlyInParens || insideParens) {
                val target = if (insideParens && defaultEntry != null) defaultEntry else (carry ?: defaultEntry)
                if (target != null) {
                  val relEnd = scanRelativeTail(part, i)
                  if (relEnd > i) {
                    val display = dp.substring(i, relEnd)
                    val tail = normalizeCjkTail(part.substring(i, relEnd).trim())
                    val appLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)
                    val payload = RefPayload(
                      collection = target.collection,
                      canonBook = target.canon,
                      tail = tail,
                      translation = prefs.translation,
                      appLanguage = appLang,
                      readerMode = prefs.readerMode,
                      bookId = assetBookId(target)
                    )
                    pushStringAnnotation("BIBLE_REF", payload.encode())
                    withStyle(linkStyle) { append(display) }
                    pop()
                    i = relEnd
                    continue
                  }
                }
              }
            }
          }

          val ch = part[i]
          if (inlineMarkdown && ch == '*') {
            if (i + 1 < part.length && part[i + 1] == '*') { toggleBold(); i += 2; continue }
            else { toggleItalic(); i += 1; continue }
          }
          append(dp[i])
          i++
        }

        if (italicOn) pop()
        if (boldOn) pop()
        if (pIdx < lastIdx) append("; ")
        pIdx++
      }
      if (addedWordOn) pop()
      if (dnOn) pop()
      if (jesusOn) pop()
    }

    // Localized dialog strings
    val dcNotAvailableTitle = stringResource(Res.string.dc_not_available_title)
    val dcNotAvailableBody = stringResource(Res.string.dc_not_available_body)
    val dcSwap = stringResource(Res.string.dc_swap)
    val dcReadInApp = stringResource(Res.string.dc_read_in_app)
    val apocNoReaderTitle = stringResource(Res.string.apoc_no_reader_title)
    val apocNoReaderBody = stringResource(Res.string.apoc_no_reader_body)
    val actionOk = stringResource(Res.string.action_ok)

    if (noReaderDialog) {
      AlertDialog(
        onDismissRequest = { noReaderDialog = false; navGate = false },
        title = { Text(apocNoReaderTitle) },
        text = { Text(apocNoReaderBody) },
        confirmButton = {
          TextButton(onClick = { noReaderDialog = false; navGate = false }) {
            Text(actionOk)
          }
        }
      )
    }

    dialog?.let { d ->
      AlertDialog(
        onDismissRequest = { navGate = false; dialog = null },
        title = { Text(dcNotAvailableTitle.replace("%1\$s", d.currentVersion)) },
        text = {
          Column {
            Text(dcNotAvailableBody.replace("%1\$s", d.currentVersion).replace("%2\$s", d.suggestVersion))
            Spacer(modifier = Modifier.height(12.dp))
            // Swap button (open suggested version on bible.com)
            TextButton(
              onClick = {
                openUrl(d.fallbackUrl)
                navGate = false; dialog = null
              },
              modifier = Modifier.fillMaxWidth()
            ) { Text("$dcSwap → ${d.suggestVersion}") }
            // Read in-app: route to the internal reader for this DC ref so users
            // in non-native-DC langs aren't forced into English.
            if (d.internalCollection != null && d.internalBookId != null && d.internalStoryId != null) {
              TextButton(
                onClick = {
                  internalNav(
                    d.internalCollection,
                    d.internalBookId,
                    d.internalStoryId,
                    d.internalVerse,
                    d.internalVerseEnd
                  )
                  navGate = false; dialog = null
                },
                modifier = Modifier.fillMaxWidth()
              ) { Text(dcReadInApp) }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = {
            // Cancel — close the dialog without navigating anywhere.
            navGate = false; dialog = null
          }) { Text(stringResource(Res.string.cancel)) }
        },
        dismissButton = null
      )
    }

    @Suppress("DEPRECATION")
    ClickableText(
      text = asText,
      modifier = modifier,
      style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
      onClick = { off ->
        asText.getStringAnnotations("URL", off, off).firstOrNull()?.let { ann ->
          openUrl(ann.item); return@ClickableText
        }

        asText.getStringAnnotations("BIBLE_REF", off, off).firstOrNull()?.let { ann ->
          if (dialog != null || navGate) return@let
          navGate = true

          val payload = RefPayload.decode(ann.item) ?: run { navGate = false; return@let }
          val fullRef = "${payload.canonBook} ${payload.tail}"

          if (fullRef.startsWith("http://") || fullRef.startsWith("https://")) {
            openUrl(fullRef); navGate = false; return@let
          }

          if (payload.isInternal) {
            val bookId = payload.assetId
            val target = parseInternalRefTail(payload.assetId, payload.tail)
            val storyId = "$bookId-${target.chapter}"
            internalNav(payload.collection, bookId, storyId, target.verse, target.verseEnd)
            navGate = false
            return@let
          }

          if (payload.collection in setOf("apocrypha", "pseudepigrapha") &&
              !Linker.hasExternalReaderSupport(payload.canonBook)) {
            noReaderDialog = true
            return@let
          }

          val isPsalm151 = payload.canonBook.trim().lowercase().startsWith("psalm") &&
                  payload.tail.trim().startsWith("151")
          val isDc = isPsalm151 ||
                  isApocryphaBook(payload.canonBook) ||
                  payload.collection in setOf("deuterocanonical", "apocrypha", "pseudepigrapha")

          val resolved = Linker.linkForReader(
            fullRef,
            payload.translation,
            payload.readerMode,
            payload.appLanguage
          ) ?: run {
            navGate = false
            return@let
          }
          val (resolvedVersion, resolvedUrl) = resolved

          if (isDc && !resolvedVersion.equals(payload.translation, ignoreCase = true)) {
            val intArgs = derivedInternalNavArgs(payload)
            dialog = SwapDialog(
              currentVersion = payload.translation,
              suggestVersion = resolvedVersion,
              fallbackUrl = resolvedUrl,
              internalCollection = intArgs.collection,
              internalBookId = intArgs.bookId,
              internalStoryId = intArgs.storyId,
              internalVerse = intArgs.verse,
              internalVerseEnd = intArgs.verseEnd
            )
            navGate = false
            return@let
          }

          // The verified HTTPS URL is a universal link: YouVersion opens when
          // installed, and the browser remains a reliable fallback otherwise.
          openUrl(resolvedUrl)
          navGate = false
          return@let
        }

        onNonLinkClick?.invoke()
      }
    )
  }

  // ----- scanners -----

  private fun isLeftBoundary(ch: Char?): Boolean =
    when {
      ch == null -> true
      ch.isWhitespace() -> true
      ch in listOf('(', '[', '{', '\u2022', ',', '\u00B7', '\u2010', '\u2011', '\u2014', '\u2013', '-', '/',
        '\uFF0C', '\uFF1B', '\uFF1A', '\u3002', '\u3001', '\uFF08', '\uFF3B', '\uFF5B',
        '"', '\'', '*',
        '\u201C', '\u201D', '\u201E', '\u201F',
        '\u2018', '\u2019', '\u201A', '\u201B',
        '\u00AB', '\u00BB', '\u2039', '\u203A',
        '\u300A', '\u300B', '\u300C', '\u300D', '\u300E', '\u300F') -> true
      else -> false
    }

  private fun isCompactRefChar(ch: Char?): Boolean {
    val code = ch?.code ?: return false
    return code in 0x3040..0x30FF ||
      code in 0x3400..0x9FFF ||
      code in 0xAC00..0xD7AF
  }

  private fun isCompactOrdinalStart(ch: Char): Boolean = when (ch) {
    '1', '2', '3', '4', '5',
    'I', 'i',
    '\uFF11', '\uFF12', '\uFF13', '\uFF14', '\uFF15',
    '\u7B2C', '\u4E00', '\u4E8C', '\u4E09', '\u56DB', '\u4E94',
    '\uC81C' -> true
    else -> false
  }

  private fun isCompactRefStart(s: String, start: Int): Boolean {
    val first = s.getOrNull(start) ?: return false
    if (isCompactRefChar(first) && first.lowercaseChar() in bookKeyInitials) return true
    if (!isCompactOrdinalStart(first)) return false

    val (_, ordinalLength) = takeOrdinal(s, start)
    if (ordinalLength == 0) return false
    val bookStart = s.getOrNull(start + ordinalLength) ?: return false
    return isCompactRefChar(bookStart) && bookStart.lowercaseChar() in bookKeyInitials
  }

  private fun isInsideParens(s: String, pos: Int): Boolean {
    var depth = 0
    var i = 0
    while (i < pos) {
      when (s[i]) {
        '(', '\uFF08', '[', '\uFF3B', '{', '\uFF5B' -> depth++
        ')', '\uFF09', ']', '\uFF3D', '}', '\uFF5D' -> if (depth > 0) depth--
      }
      i++
    }
    return depth > 0
  }

  private fun scanUrlAt(s: String, i: Int): Triple<Int,String,String>? {
    if (!s.startsWith("http://", i) && !s.startsWith("https://", i) && !s.startsWith("www.", i)) return null
    var j = i
    while (j < s.length) {
      val ch = s[j]
      if (ch.isWhitespace() || ch in listOf(')', ']', '}', '<')) break
      j++
    }
    var urlText = s.substring(i, j)
    while (urlText.isNotEmpty() && urlText.last() in listOf('.', ',', ';', ':', '!', '?', '\u2019', '"')) {
      urlText = urlText.dropLast(1); j--
    }
    val url = if (urlText.startsWith("www.", true)) "https://$urlText" else urlText
    return Triple(j, url, urlText)
  }

  private fun scanBookAt(s: String, i0: Int): Pair<BookEntry, Int>? {
    var i = i0
    val (ord, ordLen) = takeOrdinal(s, i)
    val ordDigit: Char? = ord.trim().firstOrNull()
    i += ordLen
    val rest = s.substring(i)

    var bestEntry: BookEntry? = null
    var bestLen = -1
    var bestOrdinalMatch = false

    for (entry in books) {
      val canonStartsWithDigit = entry.canon.firstOrNull()?.isDigit() == true
      val ordinalMatchesEntry = ordDigit != null && canonStartsWithDigit &&
        (entry.canon.startsWith("$ordDigit ") || entry.keys.any { key ->
          takeOrdinal(key, 0).first.trim().firstOrNull() == ordDigit
        })
      if (ordDigit != null && canonStartsWithDigit && !ordinalMatchesEntry) continue

      for (key in entry.keys) {
        if (rest.length >= key.length && rest.regionMatches(0, key, 0, key.length, ignoreCase = true)) {
          val consumed = ordLen + key.length
          val next = s.getOrNull(i0 + consumed)
          // Chinese and Korean commonly write chapter references without a
          // space as BookName + 第/제 + number + 章/장. Treat that chapter prefix
          // as reference syntax, not as a continuation of the book name.
          if (next != null && next.isLetter() && next !in setOf('\u7B2C', '\uC81C')) continue
          if (canonStartsWithDigit && ordDigit == null && entry.strippedKeys.contains(key)) continue

          val ordinalMatch = ordinalMatchesEntry
          if (key.length > bestLen || (key.length == bestLen && ordinalMatch && !bestOrdinalMatch)) {
            bestLen = key.length
            bestEntry = entry
            bestOrdinalMatch = ordinalMatch
          }
        }
      }
    }
    return bestEntry?.let { it to (ordLen + bestLen) }
  }

  private fun takeOrdinal(s: String, i: Int): Pair<String, Int> {
    if (i >= s.length) return "" to 0
    val rest = s.substring(i)

    fun isWs(ch: Char?) = ch != null && (ch.isWhitespace() || ch == '\u00A0' || ch == '\u202F' || ch == '\u2009')
    fun consumeSpace(off: Int): Int {
      var k = off
      while (k < rest.length && isWs(rest[k])) k++
      return k
    }

    fun skipPeriod(off: Int): Int = if (rest.getOrNull(off) == '.') off + 1 else off

    when (rest.firstOrNull()) {
      '1','\uFF11' -> return "1 " to consumeSpace(skipPeriod(1))
      '2','\uFF12' -> return "2 " to consumeSpace(skipPeriod(1))
      '3','\uFF13' -> return "3 " to consumeSpace(skipPeriod(1))
      '4','\uFF14' -> return "4 " to consumeSpace(skipPeriod(1))
      '5','\uFF15' -> return "5 " to consumeSpace(skipPeriod(1))
    }

    if (rest.startsWith("\uC81C")) {
      when (rest.getOrNull(1)) {
        '1', '\uFF11' -> return "1 " to consumeSpace(skipPeriod(2))
        '2', '\uFF12' -> return "2 " to consumeSpace(skipPeriod(2))
        '3', '\uFF13' -> return "3 " to consumeSpace(skipPeriod(2))
        '4', '\uFF14' -> return "4 " to consumeSpace(skipPeriod(2))
        '5', '\uFF15' -> return "5 " to consumeSpace(skipPeriod(2))
      }
    }

    if (rest.startsWith("\u7B2C\u4E00") || rest.startsWith("\u7B2C\uFF11")) return "1 " to consumeSpace(2)
    if (rest.startsWith("\u7B2C\u4E8C") || rest.startsWith("\u7B2C\uFF12")) return "2 " to consumeSpace(2)
    if (rest.startsWith("\u7B2C\u4E09") || rest.startsWith("\u7B2C\uFF13")) return "3 " to consumeSpace(2)
    if (rest.startsWith("\u7B2C\u56DB") || rest.startsWith("\u7B2C\uFF14")) return "4 " to consumeSpace(2)
    if (rest.startsWith("\u7B2C\u4E94") || rest.startsWith("\u7B2C\uFF15")) return "5 " to consumeSpace(2)
    when (rest.firstOrNull()) {
      '\u4E00' -> return "1 " to consumeSpace(1)
      '\u4E8C' -> return "2 " to consumeSpace(1)
      '\u4E09' -> return "3 " to consumeSpace(1)
      '\u56DB' -> return "4 " to consumeSpace(1)
      '\u4E94' -> return "5 " to consumeSpace(1)
    }

    val lower = rest.lowercase()
    return when {
      lower.startsWith("iv ")     -> "4 " to 3
      lower.startsWith("iii ")    -> "3 " to 4
      lower.startsWith("ii ")     -> "2 " to 3
      lower.startsWith("i ")      -> "1 " to 2
      lower.startsWith("first ")  -> "1 " to 6
      lower.startsWith("second ") -> "2 " to 7
      lower.startsWith("third ")  -> "3 " to 6
      lower.startsWith("fourth ") -> "4 " to 7
      lower.startsWith("fifth ")  -> "5 " to 6
      else -> "" to 0
    }
  }

  private fun scanRefTail(s: String, start: Int): Int {
    var i = start
    fun dash(c: Char?) = c == '\u2010' || c == '\u2011' || c == '\u2013' || c == '\u2014' ||
      c == '-' || c == '\uFF0D' || c == '\u301C' || c == '\uFF5E'
    fun skip() { while (s.getOrNull(i) == ' ') i++ }
    fun digits(): Boolean { val st = i; while (s.getOrNull(i)?.isDigit() == true) i++; return i > st }
    fun localizedVerseSuffix() {
      val c = s.getOrNull(i)
      if (c == '\u7BC0' || c == '\u8282' || c == '\uC808') i++
    }
    fun chapterSuffix(c: Char?) = c == '\u7AE0' || c == '\uC7A5'
    fun chapterPrefix(c: Char?) = c == '\u7B2C' || c == '\uC81C'

    skip()
    if (chapterPrefix(s.getOrNull(i))) { i++; skip() }
    if (!digits()) return start
    val chEnd = s.getOrNull(i)
    val chEndNext = s.getOrNull(i + 1)
    val hasEuroVerseSep = (chEnd == ',' || chEnd == '.') && chEndNext?.isDigit() == true
    val hasLocalizedChapterMark = chapterSuffix(chEnd)
    skip()

    if (s.getOrNull(i) != ':' && !hasEuroVerseSep && !hasLocalizedChapterMark) {
      if (dash(s.getOrNull(i))) {
        val saveDash = i
        i++; skip()
        if (!digits()) return saveDash
        if (chapterSuffix(s.getOrNull(i))) i++
      }
      return i
    }

    val localizedChapterEnd = hasLocalizedChapterMark
    i++
    skip()
    if (chapterPrefix(s.getOrNull(i))) { i++; skip() }
    val verseStart = i
    if (!digits()) {
      // A chapter-only reference such as 以賽亞書第53章 includes the suffix
      // in the clickable range. Colon/comma forms still stop before an empty
      // verse component.
      return if (localizedChapterEnd) i else verseStart - 1
    }
    localizedVerseSuffix()

    while (true) {
      skip()
      when (s.getOrNull(i)) {
        ',' -> {
          val commaPos = i
          i++; skip()
          val digitStart = i
          if (!digits()) return i
          if (scanBookAt(s, digitStart) != null) { i = commaPos; return commaPos }
          localizedVerseSuffix()
        }
        '\u2010', '\u2011', '\u2013', '-', '\u2014', '\uFF0D', '\u301C', '\uFF5E' -> {
          val saveDash = i
          i++; skip()
          if (!digits()) return saveDash
          localizedVerseSuffix()
          val save = i; skip()
          if (s.getOrNull(i) == ':') { i++; skip(); if (!digits()) { i = save } else localizedVerseSuffix() }
        }
        else -> return i
      }
    }
  }

  private fun scanRelativeTail(s: String, start: Int): Int {
    var i = start
    fun skip() { while (s.getOrNull(i) == ' ') i++ }
    fun digits(): Boolean { val st = i; while (s.getOrNull(i)?.isDigit() == true) i++; return i > st }

    skip(); if (!digits()) return start
    skip(); if (s.getOrNull(i) != ':') return start
    i++; skip(); if (!digits()) return start

    while (true) {
      skip()
      when (s.getOrNull(i)) {
        ',' -> {
          val commaPos = i
          i++; skip()
          val digitStart = i
          if (!digits()) return i
          if (scanBookAt(s, digitStart) != null) { i = commaPos; return commaPos }
        }
        '\u2010', '\u2011', '\u2013', '-', '\u2014', '\uFF0D', '\u301C', '\uFF5E' -> {
          val saveDash = i
          i++; skip()
          if (!digits()) return saveDash
          val save = i; skip()
          if (s.getOrNull(i) == ':') {
            i++; skip()
            if (!digits()) { i = save; return i }
          }
        }
        else -> return i
      }
    }
  }

  // ----- english seed fallback -----

  private fun englishSeed(): List<BookEntry> {
    fun mkKeys(vararg ks: String): MutableSet<String> {
      val out = mutableSetOf<String>()
      for (k in ks) {
        out += k; out += k.noSpaces(); out += k.foldAscii()
        val stripped = stripLeadingOrdinal(k)
        if (!stripped.equals(k, ignoreCase = true)) {
          out += stripped; out += stripped.noSpaces(); out += stripped.foldAscii()
        }
      }
      return out
    }
    fun e(canon: String, col: String, vararg keys: String) =
      BookEntry(canon, canon, col, mkKeys(*arrayOf(*keys, canon)))

    val ot = mutableListOf(
      e("Genesis","old_testament","Gen","Gn"),
      e("Exodus","old_testament","Exod","Ex"),
      e("Leviticus","old_testament","Lev","Lv"),
      e("Numbers","old_testament","Num","Nm"),
      e("Deuteronomy","old_testament","Deut","Dt"),
      e("Joshua","old_testament","Josh"),
      e("Judges","old_testament","Judg"),
      e("Ruth","old_testament"),
      e("1 Samuel","old_testament","1 Sam","I Samuel","First Samuel","Samuel","Sam"),
      e("2 Samuel","old_testament","2 Sam","II Samuel","Second Samuel","Samuel","Sam"),
      e("1 Kings","old_testament","1 Kgs","I Kings","First Kings","Kings","Kgs"),
      e("2 Kings","old_testament","2 Kgs","II Kings","Second Kings","Kings","Kgs"),
      e("1 Chronicles","old_testament","1 Chron","I Chronicles","First Chronicles","Chronicles","Chron","Chr"),
      e("2 Chronicles","old_testament","2 Chron","II Chronicles","Second Chronicles","Chronicles","Chron","Chr"),
      e("Ezra","old_testament"),
      e("Nehemiah","old_testament","Neh"),
      e("Esther","old_testament","Est"),
      e("Job","old_testament"),
      e("Psalm","old_testament","Psalms","Ps"),
      e("Proverbs","old_testament","Prov","Pr"),
      e("Ecclesiastes","old_testament","Eccl","Qoheleth"),
      e("Song of Songs","old_testament","Song of Solomon","Song","Canticles"),
      e("Isaiah","old_testament","Isa"),
      e("Jeremiah","old_testament","Jer"),
      e("Lamentations","old_testament","Lam"),
      e("Ezekiel","old_testament","Ezek","Eze"),
      e("Daniel","old_testament","Dan"),
      e("Hosea","old_testament","Hos"),
      e("Joel","old_testament"),
      e("Amos","old_testament"),
      e("Obadiah","old_testament","Obad"),
      e("Jonah","old_testament","Jon"),
      e("Micah","old_testament","Mic"),
      e("Nahum","old_testament","Nah"),
      e("Habakkuk","old_testament","Hab"),
      e("Zephaniah","old_testament","Zeph"),
      e("Haggai","old_testament","Hag"),
      e("Zechariah","old_testament","Zech"),
      e("Malachi","old_testament","Mal")
    )

    val nt = mutableListOf(
      e("Matthew","new_testament","Matt","Mt"),
      e("Mark","new_testament","Mrk"),
      e("Luke","new_testament","Lk"),
      e("John","new_testament","Jn"),
      e("Acts","new_testament","Act"),
      e("Romans","new_testament","Rom"),
      e("1 Corinthians","new_testament","1 Cor","I Corinthians","First Corinthians","Corinthians","Cor","1Co"),
      e("2 Corinthians","new_testament","2 Cor","II Corinthians","Second Corinthians","Corinthians","Cor","2Co"),
      e("Galatians","new_testament","Gal"),
      e("Ephesians","new_testament","Eph"),
      e("Philippians","new_testament","Phil"),
      e("Colossians","new_testament","Col"),
      e("1 Thessalonians","new_testament","1 Thess","I Thessalonians","First Thessalonians","Thessalonians","Thess","1Th"),
      e("2 Thessalonians","new_testament","2 Thess","II Thessalonians","Second Thessalonians","Thessalonians","Thess","2Th"),
      e("1 Timothy","new_testament","1 Tim","I Timothy","First Timothy","Timothy","Tim","1Ti"),
      e("2 Timothy","new_testament","2 Tim","II Timothy","Second Timothy","Timothy","Tim","2Ti"),
      e("Titus","new_testament"),
      e("Philemon","new_testament"),
      e("Hebrews","new_testament"),
      e("James","new_testament","Jas"),
      e("1 Peter","new_testament","1 Pet","I Peter","First Peter","Peter","Pet","1Pe"),
      e("2 Peter","new_testament","2 Pet","II Peter","Second Peter","Peter","Pet","2Pe"),
      e("1 John","new_testament","1 Jn","I John","First John","John","1Jo"),
      e("2 John","new_testament","2 Jn","II John","Second John","John","2Jo"),
      e("3 John","new_testament","3 Jn","III John","Third John","John","3Jo"),
      e("Jude","new_testament"),
      e("Revelation","new_testament","Rev")
    )

    val dc = mutableListOf(
      e("1 Esdras","deuterocanonical","1 Esd","I Esdras","First Esdras","One Esdras","Esdras"),
      e("2 Esdras","deuterocanonical","2 Esd","II Esdras","Second Esdras","Two Esdras","4 Ezra","IV Ezra","2 Esdras (4 Ezra)","Esdras"),
      e("Tobit","deuterocanonical","Tob","Tobias"),
      e("Judith","deuterocanonical","Jdt"),
      e("Esther (Greek)","deuterocanonical","Additions to Esther","Esther Greek","Esth Gk","Ester Greek"),
      e("Wisdom","deuterocanonical","Wis","Wisdom of Solomon"),
      e("Sirach","deuterocanonical","Ecclesiasticus","Sir","Ecclus"),
      e("Baruch","deuterocanonical","Bar"),
      e("Letter of Jeremiah","deuterocanonical","Epistle of Jeremiah","Baruch 6","Ep Jer","LJe"),
      e("Song of Three","deuterocanonical","Song of the Three","Song of the Three Holy Children","Song of the Three Jews","Song of Three Jews","Prayer of Azariah","Song of 3","S3Y"),
      e("Susanna","deuterocanonical","Sus"),
      e("Bel and the Dragon","deuterocanonical","Bel, and the Dragon","Bel"),
      e("Psalm 151", "deuterocanonical", "Ps 151", "Psalm CLI", "Psa 151", "Salmo 151"),
      e("1 Maccabees","deuterocanonical","1 Mac","I Maccabees","First Maccabees","One Maccabees"),
      e("2 Maccabees","deuterocanonical","2 Mac","II Maccabees","Second Maccabees","Two Maccabees"),
      e("3 Maccabees","deuterocanonical","3 Mac","III Maccabees","Third Maccabees","Three Maccabees"),
      e("4 Maccabees","deuterocanonical","4 Mac","IV Maccabees","Fourth Maccabees","Four Maccabees"),
      e("Prayer of Manasseh","deuterocanonical","Manasseh","Pr Man")
    )
    return (ot + nt + dc).toMutableList()
  }

  // ----- utils -----

  internal fun normalizeCjkTail(tail: String): String {
    var localized = tail.trim()
    if (localized.startsWith('\u7B2C') || localized.startsWith('\uC81C')) {
      localized = localized.drop(1).trimStart()
    }
    if ('\u7AE0' !in localized && '\uC7A5' !in localized) return localized
    val r = localized
      .replace(Regex("[\u7AE0\uC7A5]\\s*[\u7B2C\uC81C]?\\s*"), ":")
      .replace("[\u7BC0\u8282\uC808]".toRegex(), "")
    return if (r.endsWith(":")) r.dropLast(1) else r
  }

  // German, French and the other continental conventions separate chapter from
  // verse with a comma or a period rather than a colon: "1. K\u00F6nige 11,41".
  // scanRefTail already accepts that form (see hasEuroVerseSep), but everything
  // downstream splits the tail on ':' alone, so "11,41" collapsed into chapter
  // 1141 and the link opened the book at the top. 2,552 references in German
  // alone. Only the FIRST separator is a chapter break; commas after that are a
  // verse list ("11,41,43") and must survive.
  private fun normalizeEuroTail(tail: String): String {
    if (':' in tail) return tail
    val m = Regex("^(\\s*\\d+)\\s*[,.]\\s*(?=\\d)").find(tail) ?: return tail
    return m.groupValues[1] + ":" + tail.substring(m.range.last + 1)
  }

  private fun normalizeRefDashes(value: String): String =
    value
      .replace('\u2010', '-')
      .replace('\u2011', '-')
      .replace('\u2013', '-')
      .replace('\u2014', '-')
      .replace('\uFF0D', '-')
      .replace('\u301C', '-')
      .replace('\uFF5E', '-')

  internal data class InternalRefTarget(
    val chapter: String,
    val verse: Int?,
    val verseEnd: Int?
  )

  private val internalSingleChapterBookIds = setOf(
    "obadiah", "philemon", "2_john", "3_john", "jude",
    "letter_of_jeremiah", "prayer_of_manasseh", "psalm_151"
  )

  internal fun parseInternalRefTail(bookId: String, raw: String): InternalRefTarget {
    val rawTail = normalizeEuroTail(normalizeRefDashes(raw.trim()))
    val tail = if (
      bookId in internalSingleChapterBookIds &&
      ':' !in rawTail &&
      Regex("^\\d+(?:\\s*[-,;]\\s*\\d+)*\\s*$").matches(rawTail)
    ) {
      "1:${rawTail.replace(Regex("\\s+"), "")}"
    } else rawTail
    val chapter = tail.dropWhile { !it.isDigit() }
      .takeWhile { it.isDigit() }
      .ifEmpty { "1" }
    val versePart = tail.substringAfter(':', "").trim()
    val verse = versePart.takeWhile { it.isDigit() }.toIntOrNull()
    val rangeTail = versePart.substringAfter('-', "")
    val verseEnd = rangeTail
      .takeIf { it.isNotBlank() && ':' !in it && ',' !in it && '.' !in it }
      ?.trim()
      ?.takeWhile { it.isDigit() }
      ?.toIntOrNull()
    return InternalRefTarget(chapter, verse, verseEnd)
  }

  private fun String.noSpaces(): String = replace(" ", "")
  private fun String.foldAscii(): String =
    normalizeNFKD(this).replace(Regex("\\p{M}+"), "")

  private fun stripLeadingOrdinal(name: String): String {
    val s = name.trim()
    val (_, ordinalLength) = takeOrdinal(s, 0)
    return if (ordinalLength > 0) s.substring(ordinalLength).trimStart() else s
  }

  private fun isApocryphaBook(canonBook: String): Boolean {
    val b = canonBook.lowercase()
    return b.startsWith("1 esdras") || b.startsWith("2 esdras") ||
            b.startsWith("tobit") || b.startsWith("judith") ||
            b.startsWith("esther (greek)") || b.startsWith("additions to esther") || b.startsWith("esther greek") ||
            b.startsWith("wisdom") || b.startsWith("sirach") ||
            b.startsWith("baruch") || b.startsWith("letter of jeremiah") ||
            b.startsWith("song of three") || b.startsWith("prayer of azariah") ||
            b.startsWith("susanna") || b.startsWith("bel and the dragon") ||
            b.startsWith("1 maccabees") || b.startsWith("2 maccabees") ||
            b.startsWith("3 maccabees") || b.startsWith("4 maccabees") ||
            b.startsWith("psalm 151") || b.startsWith("ps 151") ||
            b.startsWith("odes") || b.startsWith("prayer of manasseh")
  }

  @Composable
  private fun colorFromKey(key: String): Color = when (key.lowercase()) {
    "red"     -> Color(0xFFD32F2F)
    "orange"  -> Color(0xFFF57C00)
    "yellow"  -> Color(0xFFFBC02D)
    "green"   -> Color(0xFF388E3C)
    "blue"    -> Color(0xFF1976D2)
    "indigo"  -> Color(0xFF303F9F)
    "purple"  -> Color(0xFF8E24AA)
    else      -> MaterialTheme.colorScheme.secondary
  }

  @Composable
  fun jesusColor(prefs: PrefsState): Color = jesusColorFromPrefs(prefs)

  @Composable
  fun divineNameColor(prefs: PrefsState): Color = divineNameColorFromPrefs(prefs)

  @Composable
  private fun jesusColorFromPrefs(prefs: PrefsState): Color {
    val base = colorFromKey(prefs.jesusWordsColor)
    val surface = MaterialTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.25f
    val target = if (isDark) 7.0 else 4.5
    return ensureContrast(base, surface, minRatio = target, preferLight = isDark)
  }

  @Composable
  private fun divineNameColorFromPrefs(prefs: PrefsState): Color {
    val base = colorFromKey(prefs.divineNameColor)
    val surface = MaterialTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.25f
    val target = if (isDark) 7.0 else 4.5
    return ensureContrast(base, surface, minRatio = target, preferLight = isDark)
  }

  @Composable
  private fun ensureContrast(
    base: Color,
    background: Color,
    minRatio: Double,
    preferLight: Boolean
  ): Color {
    fun contrast(a: Color, b: Color): Double {
      val la = a.luminance().toDouble(); val lb = b.luminance().toDouble()
      val lighter = maxOf(la, lb); val darker = minOf(la, lb)
      return (lighter + 0.05) / (darker + 0.05)
    }
    if (contrast(base, background) >= minRatio) return base

    val hsl = FloatArray(3)
    ColorHsl.colorToHSL(base.toArgb(), hsl)
    val startL = hsl[2]
    val startS = hsl[1]

    fun colorAt(l: Float, s: Float = startS): Color {
      val l2 = l.coerceIn(0f, 1f)
      val s2 = s.coerceIn(0f, 1f)
      val argb = ColorHsl.hslToColor(floatArrayOf(hsl[0], s2, l2))
      return Color(argb)
    }

    val steps = (1..24).map { it * 0.03f }
    val dirs = if (preferLight) listOf(+1f, -1f) else listOf(-1f, +1f)

    var best = base
    var bestRatio = contrast(base, background)

    fun tryUpdate(c: Color): Color? {
      val r = contrast(c, background)
      if (r > bestRatio) { best = c; bestRatio = r }
      return if (r >= minRatio) c else null
    }

    for (dir in dirs) {
      for (d in steps) {
        val l = startL + dir * d
        val s = if (preferLight) (startS + 0.05f).coerceAtMost(0.9f) else startS
        tryUpdate(colorAt(l, s))?.let { return it }
      }
    }

    return if (bestRatio >= (minRatio * 0.85)) best else MaterialTheme.colorScheme.onSurface
  }

  private data class RefPayload(
    val collection: String,
    val canonBook: String,
    val tail: String,
    val translation: String,
    val appLanguage: String,
    val readerMode: String,
    // The asset id to open. Carried rather than re-derived from canonBook,
    // because the two differ for nine books. Empty falls back to the old
    // derivation so an older encoded payload still decodes.
    val bookId: String = ""
  ) {
    val preferBibleCom: Boolean get() = readerMode == "biblecom"
    val isInternal: Boolean get() = readerMode == "internal"
    val assetId: String
      get() = bookId.ifEmpty { canonBook.trim().lowercase().replace(' ', '_') }

    fun encode(): String =
      listOf(
        collection,
        canonBook.replace("|","\u00A6"),
        tail.replace("|","\u00A6"),
        translation,
        appLanguage,
        readerMode,
        bookId.replace("|","\u00A6")
      ).joinToString("|")

    companion object {
      fun decode(s: String): RefPayload? = runCatching {
        val p = s.split("|")
        val modeRaw = p.getOrNull(5) ?: "biblecom"
        val mode = when (modeRaw) {
          "1" -> "biblecom"
          "0" -> "biblegateway"
          else -> modeRaw
        }
        RefPayload(
          collection = p[0],
          canonBook = p[1].replace("\u00A6","|"),
          tail = p[2].replace("\u00A6","|"),
          translation = p[3],
          appLanguage = p[4],
          readerMode = mode,
          bookId = (p.getOrNull(6) ?: "").replace("\u00A6","|")
        )
      }.getOrNull()
    }
  }

  private data class SwapDialog(
    val currentVersion: String,
    val suggestVersion: String,
    val fallbackUrl: String,
    // Internal nav payload so "Read in-app" can route to the in-app reader
    // for the same DC reference. Null when internal nav can't be derived.
    val internalCollection: String? = null,
    val internalBookId: String? = null,
    val internalStoryId: String? = null,
    val internalVerse: Int? = null,
    val internalVerseEnd: Int? = null
  )

  private data class InternalNavArgs(
    val collection: String,
    val bookId: String,
    val storyId: String,
    val verse: Int?,
    val verseEnd: Int?
  )

  // Mirrors the inline parsing inside BibleRefAnnotated's onClick — see the
  // payload.isInternal branch — so the SwapDialog can offer "Read in-app".
  private fun derivedInternalNavArgs(payload: RefPayload): InternalNavArgs {
    val bookId = payload.assetId
    val target = parseInternalRefTail(bookId, payload.tail)
    return InternalNavArgs(
      payload.collection,
      bookId,
      "$bookId-${target.chapter}",
      target.verse,
      target.verseEnd
    )
  }
}
