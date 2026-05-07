package com.dividesbyzer0.biblecompanion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.key

@Composable
fun RenderNotesMarkdown(
  body: String,
  prefs: PrefsState,
  ambientBook: String? = null,
  selectionResetKey: Int = 0
) {
  if (body.isBlank()) {
    Text("\u2014", style = MaterialTheme.typography.bodyMedium)
    return
  }

  val ambientCollection: String? = ScriptureRefs.collectionOf(ambientBook)

  val lines = body.replace("\r\n", "\n").split('\n')
  val isCjkLocale = when (LocaleUtils.effectiveAssetTag(prefs.appLanguage).lowercase()) {
    "ja", "ko", "zh-hans", "zh-hant" -> true
    else -> false
  }

  key(selectionResetKey) { SelectionContainer {
    Column {
      var i = 0
      while (i < lines.size) {
        val raw = lines[i]

        if (raw.trim() == "---") {
          Spacer(Modifier.height(8.dp))
          HorizontalDivider()
          Spacer(Modifier.height(8.dp))
          i++
          continue
        }

        when {
          raw.startsWith("### ") -> {
            Text(
              mdInline(raw.removePrefix("### ").trim()),
              style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(6.dp)); i++; continue
          }
          raw.startsWith("## ") -> {
            Text(
              mdInline(raw.removePrefix("## ").trim()),
              style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp)); i++; continue
          }
          raw.startsWith("# ") -> {
            Text(
              mdInline(raw.removePrefix("# ").trim()),
              style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp)); i++; continue
          }
        }

        if (raw.trimStart().startsWith("- ")) {
          val t = raw.trimStart().removePrefix("- ").trim()
          val normalized = if (isCjkLocale) normalizeForRefParsing(t) else t
          val scanFriendly = stripMdAroundLikelyRefs(normalized)
          if (ambientBook != null && ambientCollection != null) {
            ScriptureRefs.ClickableRefsText(
              text = scanFriendly,
              collection = ambientCollection,
              prefs = prefs,
              defaultBook = ambientBook,
              allowRelativeInParensOnly = true,
              textStyle = MaterialTheme.typography.bodyMedium
            )
          } else {
            ScriptureRefs.ClickableRefsTextSmart(
              text = scanFriendly,
              prefs = prefs,
              inlineMarkdown = true
            )
          }
          i++; continue
        }

        if (raw.trimStart().matches(Regex("""\d+[.)]\s+.*"""))) {
          val t = raw.trimStart().replace(Regex("""^\d+[.)]\s+"""), "").trim()
          val normalized = if (isCjkLocale) normalizeForRefParsing(t) else t
          val scanFriendly = stripMdAroundLikelyRefs(normalized)
          if (ambientBook != null && ambientCollection != null) {
            ScriptureRefs.ClickableRefsText(
              text = scanFriendly,
              collection = ambientCollection,
              prefs = prefs,
              defaultBook = ambientBook,
              allowRelativeInParensOnly = true,
              textStyle = MaterialTheme.typography.bodyMedium
            )
          } else {
            ScriptureRefs.ClickableRefsTextSmart(
              text = scanFriendly,
              prefs = prefs,
              inlineMarkdown = true
            )
          }
          i++; continue
        }

        if (raw.trimStart().startsWith("|") && raw.contains("|")) {
          val tableLines = mutableListOf<String>()
          var j = i
          val sepRegex = Regex("""^\|\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")
          while (j < lines.size && lines[j].trim().startsWith("|") && lines[j].contains("|")) {
            if (!sepRegex.matches(lines[j].trim())) tableLines.add(lines[j])
            j++
          }
          if (tableLines.isNotEmpty()) {
            val rows = tableLines.map { line ->
              line.trim().trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }
            }
            MarkdownTable(
              rows = rows,
              prefs = prefs,
              ambientBook = ambientBook,
              ambientCollection = ambientCollection,
              isCjkLocale = isCjkLocale
            )
            Spacer(Modifier.height(6.dp))
            i = j
            continue
          }
        }

        if (raw.isNotBlank()) {
          val sb = StringBuilder()
          var j = i
          while (j < lines.size && lines[j].isNotBlank() && lines[j].trim() != "---") {
            if (sb.isNotEmpty()) {
              // Respect markdown trailing-space line breaks: two or more
              // trailing spaces before \n mean a hard <br>.
              val prevRaw = lines[j - 1]
              if (prevRaw.length >= 2 &&
                prevRaw[prevRaw.length - 1] == ' ' &&
                prevRaw[prevRaw.length - 2] == ' '
              ) {
                sb.append('\n')
              } else {
                sb.append(' ')
              }
            }
            sb.append(lines[j].trim())
            j++
          }
          val para = sb.toString()
          val normalized = if (isCjkLocale) normalizeForRefParsing(para) else para
          val scanFriendly = stripMdAroundLikelyRefs(normalized)
          if (ambientBook != null && ambientCollection != null) {
            ScriptureRefs.ClickableRefsText(
              text = scanFriendly,
              collection = ambientCollection,
              prefs = prefs,
              defaultBook = ambientBook,
              allowRelativeInParensOnly = true,
              textStyle = MaterialTheme.typography.bodyMedium
            )
          } else {
            ScriptureRefs.ClickableRefsTextSmart(
              text = scanFriendly,
              prefs = prefs,
              inlineMarkdown = true
            )
          }
          Spacer(Modifier.height(6.dp))
          i = j; continue
        }

        Spacer(Modifier.height(6.dp))
        i++
      }
    }
  } }
}

internal fun mdInline(s: String): AnnotatedString = buildAnnotatedString {
  var i = 0
  while (i < s.length) {
    if (i + 1 < s.length && s[i] == '*' && s[i + 1] == '*') {
      val end = s.indexOf("**", i + 2)
      if (end > i + 2) {
        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
        append(s.substring(i + 2, end)); pop()
        i = end + 2; continue
      }
    }
    if (s[i] == '*') {
      val end = s.indexOf('*', i + 1)
      if (end > i + 1) {
        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        append(s.substring(i + 1, end)); pop()
        i = end + 1; continue
      }
    }
    append(s[i]); i++
  }
}

private fun normalizeForRefParsing(input: String): String {
  if (input.isEmpty()) return input
  val sb = StringBuilder(input.length)
  for (ch in input) {
    when (ch) {
      '\uFF10' -> sb.append('0'); '\uFF11' -> sb.append('1'); '\uFF12' -> sb.append('2')
      '\uFF13' -> sb.append('3'); '\uFF14' -> sb.append('4'); '\uFF15' -> sb.append('5')
      '\uFF16' -> sb.append('6'); '\uFF17' -> sb.append('7'); '\uFF18' -> sb.append('8')
      '\uFF19' -> sb.append('9')
      '\uFF1A' -> sb.append(':'); '\uFF0E' -> sb.append('.'); '\uFF0C' -> sb.append(',')
      '\uFF0F' -> sb.append('/')
      '\u2013', '\u2014', '\u2015', '\u301C', '\uFF5E', '\u2212' -> sb.append('-')
      '\u00A0', '\u2007', '\u202F', '\u3000' -> sb.append(' ')
      else -> sb.append(ch)
    }
  }
  return sb.toString().replace(Regex("""\s*:\s*"""), ":")
}

private val heading = Regex("""^\s{0,3}#{1,6}\s+""", RegexOption.MULTILINE)
private val boldItalics = Regex("""(?s)(\*\*|\*|__|_)(.+?)\1""")
private val inlineCode = Regex("""`([^`]+)`""")
private val mdLink = Regex("""\[(.*?)]\((.*?)\)""")
private val autolink = Regex("""<([^ >]+)>""")
private val blockquote = Regex("""(?m)^\s{0,3}>\s?""")
private val unordered  = Regex("""(?m)^\s{0,3}[-*+]\s+""")
private val ordered    = Regex("""(?m)^\s{0,3}\d+\.\s+""")
private val jesusStart = Regex("""\[J]""", RegexOption.IGNORE_CASE)
private val jesusEnd = Regex("""\[/J]""", RegexOption.IGNORE_CASE)
private val dnStart = Regex("""\[DN]""", RegexOption.IGNORE_CASE)
private val dnEnd = Regex("""\[/DN]""", RegexOption.IGNORE_CASE)

fun markdownToPlainText(src: String): String {
  var t = src
  t = t.replace(jesusStart, "").replace(jesusEnd, "")
  t = t.replace(dnStart, "").replace(dnEnd, "")
  t = t.replace(heading, "")
  t = t.replace(blockquote, "\u203A ")
  t = t.replace(unordered, "\u2022 ")
  t = t.replace(ordered, "\u2022 ")
  t = t.replace(mdLink) { m ->
    val label = m.groupValues[1].trim()
    val url = m.groupValues[2].trim()
    if (label.equals(url, true)) url else "$label ($url)"
  }
  t = t.replace(autolink) { it.groupValues[1] }
  t = t.replace(inlineCode) { it.groupValues[1] }
  t = t.replace(boldItalics) { it.groupValues[2] }
  t = t.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("""\n{3,}"""), "\n\n").trim()
  return t
}

private fun stripMdAroundLikelyRefs(text: String): String {
  val maybeRef = Regex(
    pattern = """(\*\*|\*|__|_)\s*([1-3]?\s*\p{L}[\p{L}\s]+?\s+\d{1,3}:\d{1,3}(?:[–\-]\d{1,3})?(?:,\s*\d{1,3})?)\s*\1"""
  )
  return text.replace(maybeRef) { mr -> mr.groupValues[2] }
}

@Composable
private fun MarkdownTable(
  rows: List<List<String>>,
  prefs: PrefsState,
  ambientBook: String?,
  ambientCollection: String?,
  isCjkLocale: Boolean
) {
  if (rows.isEmpty()) return
  Column(Modifier.fillMaxWidth()) {
    rows.forEachIndexed { idx, row ->
      Row(Modifier.fillMaxWidth()) {
        val perCellWeight = if (row.isEmpty()) 1f else 1f / row.size
        row.forEach { cell ->
          val normalized = if (isCjkLocale) normalizeForRefParsing(cell) else cell
          val scanFriendly = stripMdAroundLikelyRefs(normalized)
          Column(Modifier.weight(perCellWeight).padding(4.dp)) {
            if (ambientBook != null && ambientCollection != null) {
              ScriptureRefs.ClickableRefsText(
                text = scanFriendly,
                collection = ambientCollection,
                prefs = prefs,
                defaultBook = ambientBook,
                allowRelativeInParensOnly = true,
                textStyle = MaterialTheme.typography.bodyMedium
              )
            } else {
              ScriptureRefs.ClickableRefsTextSmart(
                text = scanFriendly,
                prefs = prefs,
                inlineMarkdown = true
              )
            }
          }
        }
      }
      if (idx != rows.lastIndex) {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
      }
    }
  }
}
