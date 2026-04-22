package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.httpGetForPreflight
import com.dividesbyzer0.biblecompanion.platform.platformGetDefaultLocaleLanguage
import com.dividesbyzer0.biblecompanion.platform.urlEncode

object Linker {

  // Map of display code → YouVersion numeric id. Codes are ASCII abbreviations used
  // in the app's settings dropdown; YouVersion's URL format is /bible/{id}/...{CODE}
  // where the trailing CODE is informational (YouVersion serves content based on id).
  // IDs are from the bible.com public catalog (www.bible.com/versions).
  private val youVersionIdByCode: Map<String, Int> = mapOf(
    // English
    "KJV" to 1, "KJVAAE" to 546, "KJVAE" to 547, "ESV" to 59, "NIV" to 111, "NIVUK" to 113,
    "NIRV" to 110, "NKJV" to 114, "NASB" to 100, "NASB1995" to 100, "NASB2020" to 2692,
    "CSB" to 1713, "CSBA" to 4124, "HCSB" to 72, "NLT" to 116, "NLTCE" to 4249,
    "NRSVUE" to 3523, "NRSV-CI" to 2015, "RSV" to 2020, "RSVCI" to 3548,
    "AFV" to 4253, "ASV" to 12, "AMP" to 1588, "AMPC" to 8,
    "CPDV" to 42, "CEB" to 37, "CJB" to 1275, "CEV" to 392, "CEVUK" to 294, "CEVDCI" to 303,
    "DARBY" to 478, "DRC1752" to 55, "EASY" to 2079, "BSB" to 3034, "EHV" to 4224,
    "FNVNT" to 3633, "FBV" to 1932, "GNT" to 68, "GNTD" to 69, "GNBDC" to 416, "GNBDK" to 431, "GNBUK" to 296,
    "GNV" to 2163, "GW" to 70, "ICB" to 1359, "JUB" to 1077, "LEB" to 90, "LSB" to 3345, "LSV" to 2660,
    "MEV" to 1171, "MSG" to 97, "NABRE" to 463, "NCV" to 105, "NET" to 107, "NMV" to 2135,
    "OJB" to 130, "PEV" to 2530, "TLV" to 314, "TPT" to 1849, "WEB" to 206, "WEBBE" to 1204,
    "WMB" to 1209, "WMBBE" to 1207, "WYC" to 2407, "YLT" to 821,
    // Spanish
    "RVR1960" to 149, "RVR1995" to 150, "RVR95" to 150, "RVR09" to 1718,
    "NVI" to 128, "NVIS" to 2664, "NBLA" to 103, "NBV" to 753, "NTV" to 127,
    "LBLA" to 89, "JBS" to 1076, "RVC" to 146, "RVA" to 147, "RVA2015" to 1782, "RVA-2015" to 1782,
    "BDO1573" to 1715, "DHH" to 52, "DHH94I" to 52, "DHH94PC" to 411, "DHH23ST" to 4278,
    "DHHDK" to 1845, "DHHS94" to 1846, "GLOSSSP" to 4212, "BHTI" to 222, "PDT" to 197,
    "BLP" to 28, "BLPH" to 28, "TLA" to 176, "TLAI" to 178,
    // French
    "LSG" to 93, "NEG1979" to 31, "BDS" to 21, "BFC" to 63, "PDV2017" to 133,
    "NFC" to 2367, "BCC1923" to 504, "JND" to 64, "BEX2004" to 3286, "S21" to 152, "SG21" to 152,
    "FMAR" to 62, "NBS" to 104, "NEG79" to 106, "NVS78P" to 2053, "OST" to 131,
    "THU" to 3547, "TFM" to 3447, "NEG" to 3877, "SACY" to 2599,
    // Italian
    "NR94" to 123, "NR06" to 122, "NR1994" to 123, "NR2006" to 122, "IRB20" to 3368,
    "DB1885" to 54, "ICL00P" to 1197, "ICL00D" to 1196, "RDV24" to 141,
    // Russian
    "RST" to 90, "DROT" to 3873, "CSLAV" to 45, "BTI" to 313, "CARS" to 385,
    "CARSA" to 840, "CARST" to 4027, "CASS70" to 480, "RSP" to 201, "CAROS" to 3830,
    "SYNO" to 400, "ROT" to 3764, "RU167" to 167, "NRT" to 143,
    // Portuguese
    "NVI-PT" to 129, "NBV-P" to 1966,
    "ARA" to 1608, "ARC" to 212, "A21" to 2645, "BLT" to 3254, "ONBV" to 4272,
    "NVT" to 1930, "VFL" to 200, "NAA" to 1840, "NTLH" to 211,
    "MZNVI" to 4094, "RC60DO" to 3658, "TB" to 277, "BPT09DC" to 228, "AVM" to 4542,
    // German
    "LUT" to 84, "ELB" to 57, "SCH2000" to 157, "GANTP" to 65, "BIBELHEUTE" to 877,
    "SCH1951" to 158, "ELB71" to 58, "ELBBK" to 2351, "HFA" to 73, "LUTHEUTE" to 3100,
    "DELUT" to 51, "NGU2011" to 108, "TKW" to 2200,
    // Chinese
    "CUVS" to 46, "CSBS" to 43, "RCUVSS" to 140, "CCB" to 36, "CUNPSS" to 48, "CNVS" to 41,
    "TCV2019T" to 3283, "CSBT" to 312, "RCUV" to 139, "CCCBST" to 2361, "CUNP" to 414,
    "CNV" to 40, "CCB_T" to 1392, "ZHDC1889" to 1889,
    // Japanese (removed AB=2040, id 2040 serves English NIV on bible.com;
    // NJB=4637 is a partial Bible, only contains Jonah)
    "JA1955" to 81, "JCB" to 83, "ERV" to 3802, "JA1819" to 1819,
    // Korean
    "KRV" to 88, "RNKSV" to 142, "KOERV" to 3803, "NLTNK" to 3502, "KLB" to 86,
    // Hindi
    "HHBD" to 819, "IRVHIN" to 1980, "HSB" to 3540, "HERV" to 2562,
    "HINCLBSI" to 1682, "HINOVBSI" to 1683, "HSS" to 1628,
    // Arabic
    "QNAV" to 3901, "SAB" to 153, "AVDDV" to 14, "FAOV" to 2301, "GOV" to 3513,
    "ASVD" to 192, "AR1665" to 1665
  )

  private fun youVersionIdFor(code: String): Int? =
    youVersionIdByCode[code.uppercase()]

  fun defaultVersionForLanguage(appLanguage: String): String {
    val key = langKey(appLanguage)
    return candidatesByLang[key]?.firstOrNull() ?: "ESV"
  }

  private fun normalizeRefInput(ref: String): String {
    if (ref.contains(':')) return ref
    val m = Regex("""(\s\d+)[,.](\d)""").find(ref) ?: return ref
    return ref.substring(0, m.range.first) +
            "${m.groupValues[1]}:${m.groupValues[2]}" +
            ref.substring(m.range.last + 1)
  }

  fun buildBibleGatewayUrl(queryRef: String, versionCode: String): String {
    val normalized = normalizeRefInput(queryRef)
    val parsed = parseRef(normalized)
    val rebuilt = if (parsed != null) "${parsed.first} ${parsed.second}" else normalized
    val q = urlEncode(rebuilt)
    val v = urlEncode(versionCode)
    return "https://www.biblegateway.com/passage/?search=$q&version=$v"
  }

  fun buildBibleComUrl(ref: String, versionCode: String): String? {
    val vCode = versionCode.trim().uppercase()
    val id = youVersionIdFor(vCode) ?: return null
    val parsed = parseRef(normalizeRefInput(ref)) ?: return null
    val (bookName, restRaw) = parsed
    var bookCode = toYouVersionBookCode(bookName) ?: return null
    val rest = normalizeTailForSingleChapterCode(bookCode, restRaw)
    var chapter = firstChapterOf(rest)
    if (bookCode == "PSA" && chapter == "151") { bookCode = "PS2"; chapter = "1" }
    if (bookCode == "PS2" && chapter == "151") { chapter = "1" }

    val dashClass = "[\\-\\u2010\\u2013\\u2014\\uFF0D\\u223C\\u301C]"
    val hasList = rest.contains(',')
    val crossChapter = Regex("^\\s*\\d+\\s*:\\s*\\d+\\s*$dashClass\\s*\\d+\\s*:\\s*\\d+").containsMatchIn(rest)
    val verseMatch = Regex("^\\s*\\d+\\s*:(\\d+)\\s*(?:$dashClass\\s*(\\d+))?").find(rest)
    val verseStart = verseMatch?.groupValues?.getOrNull(1)
    val verseEnd = verseMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotEmpty() }
    var versePart = when {
      hasList || crossChapter -> ""
      verseStart != null && verseEnd != null -> ".${verseStart}-${verseEnd}"
      verseStart != null -> ".${verseStart}"
      else -> ""
    }
    if (versePart.isNotEmpty()) {
      versePart = versePart.trimEnd('-', '\u2010', '\u2013', '\u2014', '\uFF0D', '\u301C', '\u223C')
    }
    val path = if (versePart.isEmpty()) "/bible/$id/$bookCode.$chapter.$vCode"
    else "/bible/$id/$bookCode.$chapter$versePart.$vCode"
    return "https://www.bible.com$path"
  }

  fun isBibleComChapterLikelyAvailable(url: String, timeoutMs: Int = 7000): Boolean {
    val (code, body) = httpGetForPreflight(url, timeoutMs, 65536)
    if (code >= 400) return false
    val emptyDesc = Regex("""<meta\s+name="description"\s+content=""\s*/?>""")
    if (emptyDesc.containsMatchIn(body)) return false
    val emptyOgTitle = Regex("""<meta\s+property="og:title"\s+content="\s+\([A-Z0-9\-]{2,}\)\s*-\s*\|""")
    if (emptyOgTitle.containsMatchIn(body)) return false
    return true
  }

  private val dcBooks = setOf("1ES","2ES","TOB","JDT","ESG","WIS","SIR","BAR","LJE","S3Y","SUS","BEL","1MA","2MA","3MA","4MA","MAN","PS2")

  private fun isDeuterocanonRef(ref: String): Boolean {
    val parsed = parseRef(ref) ?: return false
    val (bookName, rest) = parsed
    var code = toYouVersionBookCode(bookName) ?: return false
    val ch = firstChapterOf(rest)
    if (code == "PSA" && ch == "151") code = "PS2"
    return code in dcBooks
  }

  private fun langKey(tag: String): String {
    val resolved = if (tag.equals("system", ignoreCase = true)) LocaleUtils.effectiveAssetTag(tag) else tag
    val t = resolved.lowercase()
    return if (t.startsWith("zh")) {
      if (t.contains("hant") || t.contains("tw") || t.contains("hk")) "zh-hant" else "zh-hans"
    } else t.substringBefore('-')
  }

  private val candidatesByLang: Map<String, List<String>> = mapOf(
    "en" to listOf("ESV","NIV","NRSVUE","KJVAAE","DRC1752","CPDV","CEB","CEVDCI","KJV","NKJV","NASB","CSB","NLT","AFV","ASV","AMP","AMPC","CSBA","CJB","CEV","CEVUK","DARBY","EASY","BSB","EHV","FNVNT","FBV"),
    "es" to listOf("RVR09","RVR95","DHH94I","BDO1573","DHHDK","DHHS94","BHTI","RVR1960","JBS","NBV","RVA2015","RVC","LBLA","GLOSSSP","PDT","BLPH","NVIS","NBLA","NTV","NVI"),
    "fr" to listOf("BFC","PDV2017","NFC","BCC1923","BEX2004","LSG","NEG1979","BDS","S21","FMAR","NBS","NEG79","NVS78P","OST","THU","TFM","NEG","SACY"),
    "it" to listOf("ICL00D","DB1885","ICL00P","RDV24","NR06","NR94","IRB20"),
    "ru" to listOf("RU167","RST","DROT","CSLAV","BTI","CARS","CARSA","CARST","CASS70","RSP","CAROS","SYNO","ROT"),
    "pt" to listOf("BPT09DC","NVI-PT","ARA","ARC","A21","BLT","ONBV","NVT","VFL","NAA","NTLH","MZNVI","RC60DO","TB","NBV-P","AVM"),
    "de" to listOf("LUT","ELB","SCH2000","GANTP","BIBELHEUTE","SCH1951","ELB71","ELBBK","HFA","LUTHEUTE","DELUT","NGU2011","TKW"),
    "zh-hans" to listOf("ZHDC1889","RCUVSS","CUVS","CSBS","CCB","CUNPSS","CNVS"),
    "zh-hant" to listOf("ZHDC1889","RCUV","TCV2019T","CSBT","CCCBST","CUNP","CNV","CCB_T"),
    "ja" to listOf("JCB","JA1819","AB","JA1955","ERV"),
    "ko" to listOf("KRV","RNKSV","KOERV","NLTNK","KLB"),
    "hi" to listOf("IRVHIN","HHBD","HSB","HERV","HINCLBSI","HINOVBSI","HSS"),
    "ar" to listOf("SAB","AR1665","AVDDV","QNAV","FAOV","GOV","ASVD")
  )

  fun hasApocryphaSupport(versionCode: String, langTag: String): Boolean {
    val v = versionCode.uppercase()
    return when (langKey(langTag)) {
      "en" -> v in setOf("NRSVUE","KJVAAE","DRC1752","CPDV","CEB","CEVDCI")
      "es" -> v in setOf("DHH94I","BDO1573","DHHDK","DHHS94","BHTI")
      "fr" -> v in setOf("BFC","PDV2017","NFC","BCC1923","BEX2004")
      "it" -> v in setOf("ICL00D")
      "ru" -> v in setOf("RU167")
      "pt" -> v in setOf("BPT09DC")
      "zh-hans", "zh-hant" -> v in setOf("ZHDC1889")
      "ja" -> v in setOf("JA1819")
      "ar" -> v in setOf("AR1665")
      else -> false
    }
  }

  fun pickApocryphaFallback(langTag: String): String = when (langKey(langTag)) {
    "en" -> "NRSVUE"; "es" -> "DHH94I"; "fr" -> "BFC"; "it" -> "ICL00D"
    "ru" -> "RU167"; "pt" -> "BPT09DC"; "zh-hans", "zh-hant" -> "ZHDC1889"
    "ja" -> "JA1819"; "ar" -> "AR1665"; else -> "NRSVUE"
  }

  fun apocryphaCandidates(langTag: String): List<String> {
    val lang = langKey(langTag)
    val list = candidatesByLang[lang] ?: candidatesByLang["en"] ?: emptyList()
    val dcOnly = list.filter { hasApocryphaSupport(it, langTag) }
    return if (dcOnly.isNotEmpty()) dcOnly
    else (candidatesByLang["en"] ?: emptyList()).filter { hasApocryphaSupport(it, "en") }
  }

  fun bestLinkForRef(ref: String, currentVersion: String, appLanguage: String): Pair<String, String> {
    val lang = langKey(appLanguage)
    val isDc = isDeuterocanonRef(ref)
    if (!isDc) {
      val bc = buildBibleComUrl(ref, currentVersion)
      return if (bc != null) currentVersion to bc
      else currentVersion to buildBibleGatewayUrl(ref, currentVersion)
    }
    if (hasApocryphaSupport(currentVersion, appLanguage)) {
      buildBibleComUrl(ref, currentVersion)?.let { url ->
        if (isBibleComChapterLikelyAvailable(url)) return currentVersion to url
      }
    }
    val candidates = candidatesByLang[lang] ?: emptyList()
    for (v in candidates) {
      if (!hasApocryphaSupport(v, appLanguage)) continue
      val u = buildBibleComUrl(ref, v) ?: continue
      if (isBibleComChapterLikelyAvailable(u)) return v to u
    }
    findWorkingDcVersionOnBibleGateway(lang, ref)?.let { (v, url) -> return v to url }
    val v = "NRSVUE"
    val url = buildBibleComUrl(ref, v) ?: buildBibleGatewayUrl(ref, v)
    return v to url
  }

  private fun findWorkingDcVersionOnBibleGateway(langKey: String, ref: String): Pair<String, String>? {
    val bgOnlyCandidates = when (langKey) { "fr" -> listOf("CRAMPON"); "it" -> listOf("CEI"); else -> emptyList() }
    for (v in bgOnlyCandidates) { return v to buildBibleGatewayUrl(ref, v) }
    return null
  }

  fun toLink(collection: String, ref: String, translation: String, preferBibleCom: Boolean, appLanguage: String? = null): String {
    val lang = appLanguage ?: platformGetDefaultLocaleLanguage()
    val (v, url) = bestLinkForRef(ref, translation, lang)
    if (preferBibleCom && url.contains("biblegateway.com")) {
      buildBibleComUrl(ref, v)?.let { return it }
    }
    return url
  }

  fun hasExternalReaderSupport(canonBook: String): Boolean =
    toYouVersionBookCode(canonBook.trim()) != null

  fun buildYouVersionDeepLink(ref: String, versionCode: String): String? {
    val parsed = parseRef(normalizeRefInput(ref)) ?: return null
    val (bookName, restRaw) = parsed
    var book = toYouVersionBookCode(bookName) ?: return null
    val rest = normalizeTailForSingleChapterCode(book, restRaw)
    var chap = firstChapterOf(rest)
    val verse = firstVerseOf(rest) ?: "1"
    if (book == "PSA" && chap == "151") { book = "PS2"; chap = "1" }
    val vId = youVersionIdFor(versionCode) ?: return null
    return "youversion://bible?reference=$book.$chap.$verse&version_id=$vId"
  }

  private fun parseRef(ref: String): Pair<String, String>? {
    val rx = Regex("^\\s*((?:[1-4]|i{1,3}|iv)\\s+)?([A-Za-z][A-Za-z\\s,'()\\-]+?)\\s+(\\d+.*)$", RegexOption.IGNORE_CASE)
    val m = rx.find(ref) ?: return null
    val ord = m.groupValues[1].trim()
    val name = m.groupValues[2].trim().replace("\\s+".toRegex(), " ")
    val bookName = "$ord $name".trim()
    return bookName to m.groupValues[3].trim()
  }

  private val SINGLE_CHAPTER_CODES = setOf("OBA","PHM","2JN","3JN","JUD","LJE","MAN","PS2")

  private fun normalizeTailForSingleChapterCode(bookCode: String, restRaw: String): String {
    if (!SINGLE_CHAPTER_CODES.contains(bookCode)) return restRaw
    if (restRaw.contains(":")) return restRaw
    val tail = restRaw.trim()
    val valid = Regex("^\\d+(?:\\s*[-,;]\\s*\\d+)*\\s*$")
    if (!valid.matches(tail)) return restRaw
    return "1:${tail.replace("\\s+".toRegex(), "")}"
  }

  private fun firstChapterOf(rest: String): String {
    val sb = StringBuilder()
    for (ch in rest) { if (ch.isDigit()) sb.append(ch) else break }
    return if (sb.isNotEmpty()) sb.toString() else "1"
  }

  private fun firstVerseOf(rest: String): String? {
    val m = Regex("^\\s*(\\d+)\\s*:(\\d+)").find(rest) ?: return null
    return m.groupValues[2]
  }

  private fun toYouVersionBookCode(englishBookRaw: String): String? {
    val s = englishBookRaw.trim().lowercase().replace("\\s+".toRegex(), " ").replace("\u2019", "'")
    return when (s) {
      "genesis" -> "GEN"; "exodus" -> "EXO"; "leviticus" -> "LEV"; "numbers" -> "NUM"
      "deuteronomy" -> "DEU"; "joshua" -> "JOS"; "judges" -> "JDG"; "ruth" -> "RUT"
      "1 samuel" -> "1SA"; "2 samuel" -> "2SA"; "1 kings" -> "1KI"; "2 kings" -> "2KI"
      "1 chronicles" -> "1CH"; "2 chronicles" -> "2CH"; "ezra" -> "EZR"; "nehemiah" -> "NEH"
      "esther" -> "EST"; "job" -> "JOB"; "psalm", "psalms" -> "PSA"; "proverbs" -> "PRO"
      "ecclesiastes", "qoheleth" -> "ECC"
      "song of songs", "song of solomon", "canticles" -> "SNG"
      "isaiah" -> "ISA"; "jeremiah" -> "JER"; "lamentations" -> "LAM"; "ezekiel" -> "EZK"
      "daniel" -> "DAN"; "hosea" -> "HOS"; "joel" -> "JOL"; "amos" -> "AMO"
      "obadiah" -> "OBA"; "jonah" -> "JON"; "micah" -> "MIC"; "nahum" -> "NAM"
      "habakkuk" -> "HAB"; "zephaniah" -> "ZEP"; "haggai" -> "HAG"; "zechariah" -> "ZEC"
      "malachi" -> "MAL"; "matthew" -> "MAT"; "mark" -> "MRK"; "luke" -> "LUK"
      "john" -> "JHN"; "acts" -> "ACT"; "romans" -> "ROM"
      "1 corinthians" -> "1CO"; "2 corinthians" -> "2CO"; "galatians" -> "GAL"
      "ephesians" -> "EPH"; "philippians" -> "PHP"; "colossians" -> "COL"
      "1 thessalonians" -> "1TH"; "2 thessalonians" -> "2TH"
      "1 timothy" -> "1TI"; "2 timothy" -> "2TI"; "titus" -> "TIT"; "philemon" -> "PHM"
      "hebrews" -> "HEB"; "james" -> "JAS"; "1 peter" -> "1PE"; "2 peter" -> "2PE"
      "1 john" -> "1JN"; "2 john" -> "2JN"; "3 john" -> "3JN"
      "jude" -> "JUD"; "revelation" -> "REV"
      "1 esdras", "i esdras", "one esdras" -> "1ES"
      "2 esdras", "ii esdras", "two esdras", "2 esdras (4 ezra)", "iv ezra", "4 ezra" -> "2ES"
      "tobit" -> "TOB"; "judith" -> "JDT"
      "esther (greek)", "additions to esther", "esther greek", "ester greek" -> "ESG"
      "wisdom", "wisdom of solomon" -> "WIS"; "sirach", "ecclesiasticus" -> "SIR"
      "baruch" -> "BAR"
      "letter of jeremiah", "epistle of jeremiah", "baruch 6" -> "LJE"
      "song of three", "song of the three", "song of the three holy children", "song of the three jews", "song of three jews", "prayer of azariah" -> "S3Y"
      "susanna" -> "SUS"; "psalm 151", "psalms 151" -> "PS2"
      "bel and the dragon", "bel, and the dragon", "bel" -> "BEL"
      "1 maccabees", "i maccabees", "one maccabees" -> "1MA"
      "2 maccabees", "ii maccabees", "two maccabees" -> "2MA"
      "3 maccabees", "iii maccabees", "three maccabees" -> "3MA"
      "4 maccabees", "iv maccabees", "four maccabees" -> "4MA"
      "prayer of manasseh", "manasseh" -> "MAN"
      else -> null
    }
  }
}
