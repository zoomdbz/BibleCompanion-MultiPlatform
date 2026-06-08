package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import com.dividesbyzer0.biblecompanion.platform.assetExists
import com.dividesbyzer0.biblecompanion.platform.platformOpenUrl
import com.dividesbyzer0.biblecompanion.platform.platformOpenUrlInBrowser
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

internal fun applyDivineName(
  text: String,
  mode: String,
  lang: String,
  colorActive: Boolean,
  collection: String = "old_testament"
): String {
  val lk = if (lang.startsWith("zh")) lang else lang.substringBefore('-')
  val isOt = collection == "old_testament" ||
    collection == "deuterocanonical" ||
    collection == "apocrypha" ||
    collection == "pseudepigrapha"

  fun wrap(name: String): String =
    if (colorActive) "[DN]$name[/DN]" else name

  if (mode == "traditional") {
    if (!colorActive) return text
    return highlightTraditionalName(text, lk, isOt)
  }

  val base = when (mode) {
    "yhwh" -> "YHWH"
    "yhvh" -> "YHVH"
    else -> "Yahweh"
  }
  val r = wrap(base)
  // Universal pass for the Latin/English Tetragrammaton (appears in study notes
  // of every language, and in English verses). The name is a proper noun, so the
  // definite article that "the LORD" carries is DROPPED in the name modes
  // ("the angel of the LORD" -> "the angel of Yahweh"). The bare-name regex runs
  // FIRST so the wrapped name inserted by later rules is never re-matched (no
  // double [DN] wrap). BSB pair constructions: "Lord GOD" = Adonai + the
  // Tetragrammaton, so "Lord" stays and GOD becomes the name ("Lord Yahweh");
  // "GOD the Lord" = Tetragrammaton + Adonai; the rare Yah-YHWH doublings
  // (Isa 12:2; 26:4) collapse to one name. Acts 17:23 "UNKNOWN GOD" is untouched.
  val latin = text
    .replace(Regex("\\b(?:Yahweh|YHWH|YHVH|Yahuah|Yah)\\b"), r)
    .replace("the LORD GOD", r)
    .replace("GOD the LORD", r)
    .replace("LORD GOD", r)
    .replace("GOD the Lord", "$r the Lord")
    .replace("GOD, the Lord", "$r, the Lord")
    .replace("Lord GOD", "Lord $r")
    .replace("the LORD", r)
    .replace("The LORD", r)
    .replace("THE LORD", r)
    .replace(Regex("\\bLORD\\b"), r)
  return when (lk) {
    "en" -> latin
    "es" -> latin
      .replace("del SEÑOR", "de $r")
      .replace("al SEÑOR", "a $r")
      .replace("El SEÑOR", r)
      .replace("el SEÑOR", r)
      .replace(uwb("SEÑOR"), r)
      .replace(uwb("Jehová"), r)
      .replace(uwb("Yahveh"), r)
    "pt" -> {
      var t = latin
        .replace("do SENHOR", "de $r")
        .replace("ao SENHOR", "a $r")
        .replace("O SENHOR", r)
        .replace("o SENHOR", r)
        .replace(uwb("SENHOR"), r)
        .replace(uwb("Javé"), r)
      // "Senhor" (mixed case) is overloaded: the Tetragrammaton in the OT but
      // "the Lord (Jesus)" in the NT, so only convert it in OT context. Drop the
      // article and fix the Portuguese contractions (do/ao/no).
      if (isOt) t = t
        .replace("do Senhor", "de $r")
        .replace("ao Senhor", "a $r")
        .replace("no Senhor", "em $r")
        .replace("O Senhor", r)
        .replace("o Senhor", r)
        .replace(uwb("Senhor"), r)
      t
    }
    "fr" -> latin
      // French stores the divine name as mixed-case "l'Éternel"; the elided
      // article l' is part of the token, so dropping it leaves any preposition
      // intact ("de l'Éternel" -> "de Yahweh"). Cover straight (') and curly
      // (U+2019) apostrophes. The all-caps ÉTERNEL forms are legacy.
      .replace("l'Éternel", r)
      .replace("l’Éternel", r)
      .replace("L'Éternel", r)
      .replace("L’Éternel", r)
      .replace("l'ÉTERNEL", r)
      .replace("l’ÉTERNEL", r)
      .replace("L'ÉTERNEL", r)
      .replace("L’ÉTERNEL", r)
      .replace(uwb("ÉTERNEL"), r)
      .replace(uwb("Éternel"), r)
      .replace("du SEIGNEUR", "de $r")
      .replace("au SEIGNEUR", "à $r")
      .replace("Le SEIGNEUR", r)
      .replace("le SEIGNEUR", r)
      .replace(Regex("\\bSEIGNEUR\\b"), r)
    "de" -> latin
      // Drop the German article; the name is uninflected except the genitive
      // "des HERRN" -> "Yahwehs" (Saxon genitive), and the dem/zum/vom/am
      // contractions keep their preposition ("zum HERRN" -> "zu Yahweh").
      .replace("des HERRN", "${r}s")
      .replace("zum HERRN", "zu $r")
      .replace("vom HERRN", "von $r")
      .replace("am HERRN", "an $r")
      .replace("Der HERR", r)
      .replace("der HERR", r)
      .replace("dem HERRN", r)
      .replace("den HERRN", r)
      .replace(Regex("\\bHERRN?\\b"), r)
      .replace(Regex("\\bJahwe\\b"), r)
    "it" -> {
      var t = latin
        .replace("del SIGNORE", "di $r")
        .replace("al SIGNORE", "a $r")
        .replace("Il SIGNORE", r)
        .replace("il SIGNORE", r)
        .replace(Regex("\\bSIGNORE\\b"), r)
      // "Signore" (mixed case) is overloaded (NT "il Signore Gesù"), so only in
      // OT context. Drop the article and fix contractions (del/dal/nel/al).
      if (isOt) t = t
        .replace("del Signore", "di $r")
        .replace("dal Signore", "da $r")
        .replace("nel Signore", "in $r")
        .replace("al Signore", "a $r")
        .replace("Il Signore", r)
        .replace("il Signore", r)
        .replace(Regex("\\bSignore\\b"), r)
      t
    }
    "ru" -> {
      var t = latin
        .replace("ГОСПОДЬ", r).replace("ГОСПОДА", r)
        .replace("ГОСПОДУ", r).replace("ГОСПОДОМ", r)
        .replace(uwb("Яхве"), r)
      if (isOt) {
        // Unicode-boundary regex covering all Russian Господь declensions including
        // long-form possessive adjectives (Господней, Господнего, Господним, etc.)
        // without substring-eating into Господин (gentleman).
        val ruYhwh = Regex(
          "(?<![\\p{L}\\p{Mn}])Господ(?:ь|а|у|ом|е|нее|него|нему|ним|нем|них|няя|нюю|ней|ня|ню|не|ни)(?![\\p{L}\\p{Mn}])"
        )
        t = t.replace(ruYhwh, r)
      }
      t
    }
    "ar" -> {
      var t = latin
        .replace("الرَّبُّ", r).replace("الرَّبِّ", r).replace("الرَّبَّ", r)
        .replace("يَهوَهْ", r).replace("يهوه", r)
      if (isOt) t = t.replace(uwb("الرب"), r)
      t
    }
    "hi" -> latin.replace("यहोवा", r)
    "ko" -> {
      var t = latin.replace("여호와", r).replace("야훼", r)
      if (isOt) t = t.replace(Regex("주님?"), r)
      t
    }
    "ja" -> {
      var t = latin.replace("ヤハウェ", r).replace("ヱホバ", r)
      if (isOt) t = t.replace("主", r)
      t
    }
    "zh-Hans" -> {
      var t = latin.replace("耶和华", r).replace("雅威", r)
      if (isOt) t = t.replace("主", r)
      t
    }
    "zh-Hant" -> {
      var t = latin.replace("耶和華", r).replace("雅威", r)
      if (isOt) t = t.replace("主", r)
      t
    }
    else -> latin
  }
}

private fun highlightTraditionalName(text: String, lang: String, isOt: Boolean): String {
  fun w(s: String) = "[DN]$s[/DN]"
  // Study notes write the Tetragrammaton as the Latin "YHWH"/"Yahweh"/etc. In
  // traditional mode the user wants this language's traditional rendering (the
  // LORD, SEÑOR, ...), so convert those Latin tokens to it FIRST (bare); the
  // coloring passes below then wrap it once like any other traditional token.
  // The app's verse text already stores the traditional token (no Latin YHWH),
  // so it is unaffected by this conversion.
  val tradWord = when (lang) {
    "en" -> "the LORD"
    "es" -> "SEÑOR"
    "pt" -> "SENHOR"
    "fr" -> "ÉTERNEL"
    "de" -> "HERR"
    "it" -> "SIGNORE"
    "ru" -> "Господь"
    "ar" -> "الرب"
    "hi" -> "यहोवा"
    "ko" -> "주"
    "ja" -> "主"
    "zh-Hans" -> "耶和华"
    "zh-Hant" -> "耶和華"
    else -> "the LORD"
  }
  var converted = text.replace(Regex("\\b(?:Yahweh|YHWH|YHVH|Yahuah|Yah)\\b"), tradWord)
  if (lang == "en") converted = converted.replace("the the LORD", "the LORD")
  // Color the universal English tokens; the converted tradWord is colored here
  // (en) or by the localized branch below (other languages).
  val latin = converted
    .replace(Regex("\\bLORD\\b")) { w(it.value) }
    .replace(Regex("\\bGOD\\b")) { w(it.value) }
  return when (lang) {
    "en" -> latin
    "es" -> latin
      .replace(uwb("SEÑOR")) { w(it.value) }
      .replace(uwb("Jehová")) { w(it.value) }
      .replace(uwb("Yahveh")) { w(it.value) }
    "pt" -> {
      var t = latin
        .replace(Regex("\\bSENHOR\\b")) { w(it.value) }
        .replace(uwb("Javé")) { w(it.value) }
      if (isOt) t = t.replace(Regex("\\bSenhor\\b")) { w(it.value) }
      t
    }
    "fr" -> latin
      .replace(uwb("ÉTERNEL")) { w(it.value) }
      .replace(uwb("Éternel")) { w(it.value) }
      .replace(Regex("\\bSEIGNEUR\\b")) { w(it.value) }
    "de" -> latin
      .replace(Regex("\\bHERRN?\\b")) { w(it.value) }
      .replace(Regex("\\bJahwe\\b")) { w(it.value) }
    "it" -> {
      var t = latin.replace(Regex("\\bSIGNORE\\b")) { w(it.value) }
      if (isOt) t = t.replace(Regex("\\bSignore\\b")) { w(it.value) }
      t
    }
    "ru" -> {
      var t = latin
        .replace(Regex("ГОСПОДЬ|ГОСПОДА|ГОСПОДУ|ГОСПОДОМ")) { w(it.value) }
        .replace(uwb("Яхве")) { w(it.value) }
      if (isOt) {
        val ruYhwh = Regex(
          "(?<![\\p{L}\\p{Mn}])Господ(?:ь|а|у|ом|е|нее|него|нему|ним|нем|них|няя|нюю|ней|ня|ню|не|ни)(?![\\p{L}\\p{Mn}])"
        )
        t = t.replace(ruYhwh) { w(it.value) }
      }
      t
    }
    "ar" -> {
      var t = latin
        .replace(Regex("الرَّبُّ|الرَّبِّ|الرَّبَّ")) { w(it.value) }
        .replace(Regex("يَهوَهْ|يهوه")) { w(it.value) }
      if (isOt) t = t.replace(uwb("الرب")) { w(it.value) }
      t
    }
    "hi" -> latin.replace(Regex("यहोवा")) { w(it.value) }
    "ko" -> {
      var t = latin.replace(Regex("여호와|야훼")) { w(it.value) }
      if (isOt) t = t.replace(Regex("주님?")) { w(it.value) }
      t
    }
    "ja" -> {
      var t = latin.replace(Regex("ヤハウェ|ヱホバ")) { w(it.value) }
      if (isOt) t = t.replace(Regex("主")) { w(it.value) }
      t
    }
    "zh-Hans" -> {
      var t = latin.replace(Regex("耶和华|雅威")) { w(it.value) }
      if (isOt) t = t.replace(Regex("主")) { w(it.value) }
      t
    }
    "zh-Hant" -> {
      var t = latin.replace(Regex("耶和華|雅威")) { w(it.value) }
      if (isOt) t = t.replace(Regex("主")) { w(it.value) }
      t
    }
    else -> latin
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
    val foldAsciiOnlyKeys: MutableSet<String> = mutableSetOf()
  )

  // Backed by a Compose mutableStateOf so that when primeBooks finishes after
  // the home screen has already composed (e.g. VOTD card), readers recompose
  // and scripture refs become tappable. Previously stored as @Volatile var,
  // which Compose can't observe; you had to navigate away/back to see refs work.
  private val booksState = mutableStateOf<List<BookEntry>>(emptyList())
  private var books: List<BookEntry>
    get() = booksState.value
    set(value) { booksState.value = value }
  @kotlin.concurrent.Volatile private var lastLang: String? = null

  fun primeBooks(ctx: PlatformContext, appLanguage: String) {
    val tag = LocaleUtils.effectiveAssetTag(appLanguage)
    if (lastLang == tag && books.isNotEmpty()) return

    val cols = listOf("old_testament","new_testament","deuterocanonical","apocrypha","pseudepigrapha")
    val canonToCollection = mutableMapOf<String,String>()
    for (c in cols) {
      val enPairs = ContentRepo.listBooksLocalized(ctx, c, "en")
      enPairs.forEach { (_, enTitle) -> canonToCollection[enTitle] = c }
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

      val entry = BookEntry(englishCanon, row.canon, collection, mutableSetOf(), mutableSetOf(), mutableSetOf())
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
    val scope = rememberCoroutineScope()

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

    fun openUrl(u: String, preferBrowser: Boolean = false) {
      if (preferBrowser) {
        platformOpenUrlInBrowser(ctx, u)
      } else {
        platformOpenUrl(ctx, u)
      }
    }

    val effectiveLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)

    val displayText = normalizeNFKC(rawText)
      .replace('\u3000', ' ')
      .replace('\u00A0', ' ')
      .replace('\u202F', ' ')
      .replace('\u2009', ' ')
      .replace('\u2002', ' ')
      .replace('\u2003', ' ')
      .let { applyDivineName(it, prefs.divineName, effectiveLang, prefs.divineNameColor != "default", collection) }

    // Scan text: additionally convert CJK ideographic comma (、) to ASCII comma
    // for ref-tail matching. This is 1:1 so positions stay aligned with displayText.
    val scanText = buildString(displayText.length) {
      for (ch in displayText) append(if (ch == '\u3001') ',' else ch)
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

          fun startsTag(s: String, off: Int, opening: Boolean, tag: String): Int {
            if (off >= s.length || s[off] != '[') return -1
            var j = off + 1
            if (!opening) {
              if (j >= s.length || s[j] != '/') return -1
              j++
              while (j < s.length && s[j].isWhitespace()) j++
            }
            if (j + tag.length > s.length) return -1
            if (!s.substring(j, j + tag.length).equals(tag, ignoreCase = true)) return -1
            j += tag.length
            while (j < s.length && s[j].isWhitespace()) j++
            if (j >= s.length || s[j] != ']') return -1
            return j + 1
          }

          val jOpenEnd  = startsTag(part, i, opening = true, "J")
          if (jOpenEnd > 0) { toggleJesus(); i = jOpenEnd; continue }
          val jCloseEnd = startsTag(part, i, opening = false, "J")
          if (jCloseEnd > 0) { if (jesusOn) toggleJesus(); i = jCloseEnd; continue }

          val dnOpenEnd  = startsTag(part, i, opening = true, "DN")
          if (dnOpenEnd > 0) { toggleDN(); i = dnOpenEnd; continue }
          val dnCloseEnd = startsTag(part, i, opening = false, "DN")
          if (dnCloseEnd > 0) { if (dnOn) toggleDN(); i = dnCloseEnd; continue }

          val prev = part.getOrNull(i - 1)
          if (isLeftBoundary(prev)) {
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

                if (looksLikeThousands || shortAmbiguous) {
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
                  readerMode = prefs.readerMode
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
                      readerMode = prefs.readerMode
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
      if (jesusOn) pop()
    }

    // Localized dialog strings
    val dcNotAvailableTitle = stringResource(Res.string.dc_not_available_title)
    val dcNotAvailableBody = stringResource(Res.string.dc_not_available_body)
    val dcSwap = stringResource(Res.string.dc_swap)
    val dcKeep = stringResource(Res.string.dc_keep)
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
                val tried = d.fallbackDeepLink?.let { dl ->
                  runCatching { platformOpenUrl(ctx, dl) }.isSuccess
                } ?: false
                if (!tried) openUrl(d.fallbackUrl, preferBrowser = true)
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
            val bookId = payload.canonBook.trim().lowercase().replace(' ', '_')
            val parts = payload.tail.trim().split(":")
            val chapter = parts.firstOrNull()
              ?.split("-")?.firstOrNull()?.trim()?.filter { it.isDigit() } ?: "1"
            val verseTail = parts.getOrNull(1)?.split(Regex("[,\\s]"))?.firstOrNull()
            val verseRange = verseTail?.split("-") ?: emptyList()
            val verse = verseRange.firstOrNull()?.trim()?.filter { it.isDigit() }?.toIntOrNull()
            val verseEnd = verseRange.getOrNull(1)?.trim()?.filter { it.isDigit() }?.toIntOrNull()
            val storyId = "$bookId-$chapter"
            internalNav(payload.collection, bookId, storyId, verse, verseEnd)
            navGate = false
            return@let
          }

          if (payload.collection in setOf("apocrypha", "pseudepigrapha") &&
              !Linker.hasExternalReaderSupport(payload.canonBook)) {
            noReaderDialog = true
            return@let
          }

          val primaryCandidate =
            if (payload.preferBibleCom)
              Linker.buildBibleComUrl(fullRef, payload.translation)
                ?: Linker.buildBibleGatewayUrl(fullRef, payload.translation)
            else
              Linker.buildBibleGatewayUrl(fullRef, payload.translation)
                ?: Linker.buildBibleComUrl(fullRef, payload.translation)

          val primaryUrl = primaryCandidate ?: run { navGate = false; return@let }

          if (!primaryUrl.contains("bible.com")) {
            openUrl(primaryUrl)
            navGate = false
            return@let
          }

          val isPsalm151 = payload.canonBook.trim().lowercase().startsWith("psalm") &&
                  payload.tail.trim().startsWith("151")
          val isDc = isPsalm151 ||
                  isApocryphaBook(payload.canonBook) ||
                  payload.collection in setOf("deuterocanonical", "apocrypha", "pseudepigrapha")

          navGate = true

          if (isDc) {
            if (payload.preferBibleCom && Linker.hasApocryphaSupport(payload.translation, payload.appLanguage)) {
              val tried = Linker.buildYouVersionDeepLink(fullRef, payload.translation)?.let { dl ->
                runCatching { platformOpenUrl(ctx, dl) }.isSuccess
              } ?: false
              if (tried) { navGate = false; return@let }
            }

            if (!Linker.hasApocryphaSupport(payload.translation, payload.appLanguage)) {
              // Use static fallback (no network) — calling bestLinkForRef here would
              // trigger synchronous HTTP calls on the main thread (NetworkOnMainThreadException)
              // which silently leaves navGate=true and blocks every subsequent click.
              val suggestVersion = Linker.pickApocryphaFallback(payload.appLanguage)
              val fallbackUrl = Linker.buildBibleComUrl(fullRef, suggestVersion)
                ?: Linker.buildBibleGatewayUrl(fullRef, suggestVersion)

              val fallbackDeep = Linker.buildYouVersionDeepLink(fullRef, suggestVersion)

              // Derive in-app nav target so the dialog can offer "Read in-app".
              val intArgs = derivedInternalNavArgs(payload)

              dialog = SwapDialog(
                currentVersion   = payload.translation,
                suggestVersion   = suggestVersion,
                primaryUrl       = primaryUrl,
                fallbackUrl      = fallbackUrl,
                primaryDeepLink  = null,
                fallbackDeepLink = fallbackDeep,
                internalCollection = intArgs.collection,
                internalBookId     = intArgs.bookId,
                internalStoryId    = intArgs.storyId,
                internalVerse      = intArgs.verse,
                internalVerseEnd   = intArgs.verseEnd
              )
              navGate = false
              return@let
            }

            val currentPrimary = primaryUrl
            scope.launch {
              try {
                val result = withContext(Dispatchers.Default) {
                  val ok = Linker.isBibleComChapterLikelyAvailable(currentPrimary)
                  if (ok) Triple(true, "", "")
                  else {
                    val (v, u) = Linker.bestLinkForRef(fullRef, payload.translation, payload.appLanguage)
                    Triple(false, v, u)
                  }
                }
                val ok = result.first
                val suggestVersion = result.second
                val fallbackUrl = result.third

                if (!ok) {
                  val primaryDeep = if (Linker.hasApocryphaSupport(payload.translation, payload.appLanguage))
                    Linker.buildYouVersionDeepLink(fullRef, payload.translation) else null
                  val fallbackDeep = if (Linker.hasApocryphaSupport(suggestVersion, payload.appLanguage))
                    Linker.buildYouVersionDeepLink(fullRef, suggestVersion) else null

                  val intArgs = derivedInternalNavArgs(payload)
                  dialog = SwapDialog(
                    currentVersion   = payload.translation,
                    suggestVersion   = suggestVersion,
                    primaryUrl       = currentPrimary,
                    fallbackUrl      = fallbackUrl,
                    primaryDeepLink  = primaryDeep,
                    fallbackDeepLink = fallbackDeep,
                    internalCollection = intArgs.collection,
                    internalBookId     = intArgs.bookId,
                    internalStoryId    = intArgs.storyId,
                    internalVerse      = intArgs.verse,
                    internalVerseEnd   = intArgs.verseEnd
                  )
                } else {
                  val deepTried = if (Linker.hasApocryphaSupport(payload.translation, payload.appLanguage)) {
                    Linker.buildYouVersionDeepLink(fullRef, payload.translation)?.let { dl ->
                      runCatching { platformOpenUrl(ctx, dl) }.isSuccess
                    } ?: false
                  } else false

                  if (!deepTried) {
                    openUrl(currentPrimary, preferBrowser = true)
                  }
                }
              } catch (t: Throwable) {
                // Network or parse failure — fall back to opening the primary URL rather
                // than leaving the user stuck on a frozen ref.
                runCatching { openUrl(currentPrimary, preferBrowser = true) }
              } finally {
                navGate = false
              }
            }
            return@let
          }

          Linker.buildYouVersionDeepLink(fullRef, payload.translation)?.let { yv ->
            val started = runCatching { platformOpenUrl(ctx, yv) }.isSuccess
            if (started) { navGate = false; return@let }
          }

          openUrl(primaryUrl)
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
      ch in listOf('(', '[', '{', '\u2022', ',', '\u00B7', '\u2014', '\u2013', '-', '/',
        '\uFF0C', '\uFF1B', '\uFF1A', '\u3002', '\u3001', '\uFF08', '\uFF3B', '\uFF5B',
        '"', '\'',
        '\u201C', '\u201D', '\u201E', '\u201F',
        '\u2018', '\u2019', '\u201A', '\u201B',
        '\u00AB', '\u00BB', '\u2039', '\u203A',
        '\u300A', '\u300B', '\u300C', '\u300D', '\u300E', '\u300F') -> true
      else -> false
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
      if (ordDigit != null && canonStartsWithDigit && !entry.canon.startsWith("$ordDigit ")) {
        val hasMatchingOrdinalAlias = entry.keys.any { it.startsWith("$ordDigit ") }
        if (!hasMatchingOrdinalAlias) continue
      }

      for (key in entry.keys) {
        if (rest.length >= key.length && rest.regionMatches(0, key, 0, key.length, ignoreCase = true)) {
          val consumed = ordLen + key.length
          val next = s.getOrNull(i0 + consumed)
          if (next != null && next.isLetter()) continue
          if (canonStartsWithDigit && ordDigit == null && entry.strippedKeys.contains(key)) continue

          val ordinalMatch = ordDigit != null && canonStartsWithDigit && entry.canon.startsWith("$ordDigit ")
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
      lower.startsWith("first ")  -> "1 " to 6
      lower.startsWith("second ") -> "2 " to 7
      lower.startsWith("third ")  -> "3 " to 6
      lower.startsWith("fourth ") -> "4 " to 7
      else -> "" to 0
    }
  }

  private fun scanRefTail(s: String, start: Int): Int {
    var i = start
    fun dash(c: Char?) = c == '\u2013' || c == '\u2014' || c == '-' || c == '\uFF0D' || c == '\u301C' || c == '\uFF5E'
    fun skip() { while (s.getOrNull(i) == ' ') i++ }
    fun digits(): Boolean { val st = i; while (s.getOrNull(i)?.isDigit() == true) i++; return i > st }
    fun verseSuffix() { val c = s.getOrNull(i); if (c == '\u7BC0' || c == '\u8282') i++ }

    skip(); if (!digits()) return start
    val chEnd = s.getOrNull(i)
    val chEndNext = s.getOrNull(i + 1)
    val hasEuroVerseSep = (chEnd == ',' || chEnd == '.') && chEndNext?.isDigit() == true
    val hasCjkChapterMark = chEnd == '\u7AE0'
    skip()

    if (s.getOrNull(i) != ':' && !hasEuroVerseSep && !hasCjkChapterMark) {
      if (dash(s.getOrNull(i))) {
        val saveDash = i
        i++; skip()
        if (!digits()) return saveDash
      }
      return i
    }

    i++
    val verseStart = i
    skip()
    if (!digits()) {
      i = verseStart - 1
      return i
    }
    verseSuffix()

    while (true) {
      skip()
      when (s.getOrNull(i)) {
        ',' -> {
          val commaPos = i
          i++; skip()
          val digitStart = i
          if (!digits()) return i
          if (scanBookAt(s, digitStart) != null) { i = commaPos; return commaPos }
          verseSuffix()
        }
        '\u2013', '-', '\u2014', '\uFF0D', '\u301C', '\uFF5E' -> {
          val saveDash = i
          i++; skip()
          if (!digits()) return saveDash
          verseSuffix()
          val save = i; skip()
          if (s.getOrNull(i) == ':') { i++; skip(); if (!digits()) { i = save } else verseSuffix() }
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
        '\u2013', '-', '\u2014', '\uFF0D', '\u301C', '\uFF5E' -> {
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

  private fun normalizeCjkTail(tail: String): String {
    if ('\u7AE0' !in tail) return tail
    val r = tail.replace(Regex("\u7AE0\\s*"), ":").replace("[\u7BC0\u8282]".toRegex(), "")
    return if (r.endsWith(":")) r.dropLast(1) else r
  }

  private fun String.noSpaces(): String = replace(" ", "")
  private fun String.foldAscii(): String =
    normalizeNFKD(this).replace(Regex("\\p{M}+"), "")

  private fun stripLeadingOrdinal(name: String): String {
    val s = name.trim()
    val m = Regex("^(?:[1-5]|i{1,3}|iv|first|second|third|fourth|fifth)\\s+", RegexOption.IGNORE_CASE).find(s)
    return if (m != null) s.substring(m.range.last + 1).trimStart() else s
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
    val readerMode: String
  ) {
    val preferBibleCom: Boolean get() = readerMode == "biblecom"
    val isInternal: Boolean get() = readerMode == "internal"

    fun encode(): String =
      listOf(
        collection,
        canonBook.replace("|","\u00A6"),
        tail.replace("|","\u00A6"),
        translation,
        appLanguage,
        readerMode
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
          readerMode = mode
        )
      }.getOrNull()
    }
  }

  private data class SwapDialog(
    val currentVersion: String,
    val suggestVersion: String,
    val primaryUrl: String,
    val fallbackUrl: String,
    val primaryDeepLink: String?,
    val fallbackDeepLink: String?,
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
    val bookId = payload.canonBook.trim().lowercase().replace(' ', '_')
    val parts = payload.tail.trim().split(":")
    val chapter = parts.firstOrNull()?.split("-")?.firstOrNull()?.trim()?.filter { it.isDigit() } ?: "1"
    val verseTail = parts.getOrNull(1)?.split(Regex("[,\\s]"))?.firstOrNull()
    val verseRange = verseTail?.split("-") ?: emptyList()
    val verse = verseRange.firstOrNull()?.trim()?.filter { it.isDigit() }?.toIntOrNull()
    val verseEnd = verseRange.getOrNull(1)?.trim()?.filter { it.isDigit() }?.toIntOrNull()
    return InternalNavArgs(payload.collection, bookId, "$bookId-$chapter", verse, verseEnd)
  }
}
