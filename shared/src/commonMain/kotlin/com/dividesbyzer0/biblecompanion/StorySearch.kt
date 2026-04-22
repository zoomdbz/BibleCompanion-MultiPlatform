package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.normalizeNFKD
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import kotlin.math.min

object StorySearch {

  private data class Doc(
    val collection: String, val bookId: String, val bookTitle: String,
    val bookKey: String, val familyKey: String, val number: Int?,
    val storyId: String, val title: String, val refsJoined: String,
    val text: String, val chapterSpans: List<IntRange>
  )

  private data class NoteDoc(
    val route: String,
    val title: String,
    val text: String
  )

  private data class BookDoc(
    val collection: String,
    val bookId: String,
    val title: String,
    val normalizedTitle: String
  )

  private var builtForLang: String? = null
  private val docs = mutableListOf<Doc>()
  private val noteDocs = mutableListOf<NoteDoc>()
  private val bookDocs = mutableListOf<BookDoc>()
  private val chapterIndex = mutableMapOf<String, Map<Int, String>>()
  private val bookLookup = mutableMapOf<Pair<String, Int?>, MutableList<Triple<String, String, String>>>()
  private val numberedFamilies = mutableSetOf<String>()
  private val collections = listOf("old_testament","new_testament","deuterocanonical","apocrypha","pseudepigrapha")

  private val noteFiles = listOf(
    "translation_notes.md" to "translation_notes",
    "historical_awareness.md" to "historical_awareness",
    "bible_canon.md" to "bible_canon",
    "jesus_divinity.md" to "jesus_divinity",
    "grace.md" to "grace",
    "christian_symbolism.md" to "christian_symbolism",
    "unseen_war.md" to "unseen_war",
    "common_distortions.md" to "common_distortions",
    "christophanies.md" to "christophanies",
    "faqs.md" to "faqs",
    "genealogy_notes.md" to "genealogy",
    "feast_calendar_notes.md" to "feast_calendar",
    "messianic_prophecy.md" to "messianic_prophecy",
    "daniels_timeline.md" to "daniels_timeline",
    "astronomical_signs.md" to "astronomical_signs",
    "revelation_overview.md" to "revelation_overview"
  )

  fun build(context: PlatformContext, appLang: String) {
    if (builtForLang == appLang) return
    docs.clear(); chapterIndex.clear(); bookLookup.clear(); numberedFamilies.clear()
    noteDocs.clear(); bookDocs.clear()

    for (col in collections) {
      val pairs: List<Pair<String, String>> = when (col) {
        "apocrypha" -> { val (r, g) = ContentRepo.listApocryphaSectionsLocalized(context, appLang); r + g }
        else -> ContentRepo.listBooksLocalized(context, col, appLang)
      }
      for ((bookId, bookTitle) in pairs) {
        val book = ContentRepo.loadBookOrNull(context, col, bookId, appLang) ?: continue
        val (bookKey, familyKey, num) = normalizeBookKey(book.title)
        if (num != null) numberedFamilies += familyKey
        bookLookup.getOrPut(familyKey to num) { mutableListOf() }.add(Triple(col, bookId, book.title))
        val idx = ChapterLocator.build(book)
        chapterIndex["$col|$bookId"] = idx.byChapter
        for (story in book.stories) {
          val refsJoined = story.refs.joinToString(" \u2022 ")
          val body = buildString {
            appendLine(story.title); appendLine(refsJoined)
            story.summaryBullets.forEach { appendLine(it) }; appendLine(story.keyTakeaway)
            story.crossRefs.forEach { appendLine(it) }
            story.translationNotes.forEach { tn -> appendLine(tn.term); tn.original?.let { appendLine(it) }; appendLine(tn.note) }
          }
          docs += Doc(col, bookId, bookTitle, bookKey, familyKey, num, story.id, story.title, refsJoined, normalize(body), extractChapterSpans(refsJoined))
        }
        bookDocs += BookDoc(col, bookId, bookTitle, normalize(bookTitle))
      }
    }

    val tag = LocaleUtils.effectiveAssetTag(appLang)
    for ((fileName, route) in noteFiles) {
      val md = readAssetText(context, "notes/$tag/$fileName")
        ?: readAssetText(context, "notes/en/$fileName")
        ?: continue
      val title = md.lineSequence()
        .firstOrNull { it.trimStart().startsWith("# ") }
        ?.trimStart()?.removePrefix("# ")?.trim()
        ?.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        ?: fileName.removeSuffix(".md").replace('_', ' ')
      val plainText = markdownToPlainText(md)
      noteDocs += NoteDoc(route, title, normalize(plainText))
    }

    builtForLang = appLang
  }

  fun search(queryRaw: String, limit: Int = 30): List<SearchHit> {
    if (docs.isEmpty()) return emptyList()
    val q = normalize(queryRaw).trim()
    if (q.length < 2) return emptyList()
    parseExplicitRef(q)?.let { ref -> val hits = searchByExplicitRef(ref); if (hits.isNotEmpty()) return hits.take(limit) }
    val storyHits = searchFlexible(q, 20)
    val noteHits = searchNotes(q, 5)
    val bookHits = searchBooks(q, 5)
    return (bookHits + storyHits + noteHits).sortedByDescending { it.score }.take(limit)
  }

  // Stopwords: generic particles safe to strip. Theologically-loaded words
  // (I, am, is, be, was, were, you, me, my, your, etc.) are intentionally kept
  // because they matter for exegesis (e.g., "Before Abraham was, I am").
  private val enStopwords = setOf(
    "a","an","the","of","and","or","but","so","nor","yet",
    "in","on","at","by","for","with","to","from","into","onto","unto","upon",
    "over","under","above","below","between","among","about","against","through",
    "during","until","till","before","after","since",
    "will","would","shall","should","can","could","may","might","must","do","does","did","done",
    "this","that","these","those","which","what","who","whom","whose","when","where","why","how",
    "not","no","than","then","else","though","although","while","if","because",
    "just","very","too","also","here","there","only","own","such","same","other","another"
  )

  private fun significantTokens(tokens: List<String>, lang: String): List<String> {
    return when (lang) {
      "en" -> tokens.filter { it.length >= 2 && it !in enStopwords }
      else -> tokens.filter { it.length >= 2 }
    }
  }

  // Lite English stemmer. Strips common suffixes so "follow/follows/following/followed"
  // all match. Only used on query tokens, not pre-indexed, so we compare stemmed
  // query token against word-prefix in the body.
  private fun stemLite(w: String, lang: String): String {
    if (lang != "en" || w.length < 4) return w
    val s = w.lowercase()
    return when {
      s.endsWith("sses") -> s.dropLast(2)
      s.endsWith("ies") -> s.dropLast(3) + "y"
      s.endsWith("ing") && s.length > 5 -> s.dropLast(3)
      s.endsWith("ed") && s.length > 4 -> s.dropLast(2)
      s.endsWith("ly") && s.length > 4 -> s.dropLast(2)
      s.endsWith("es") && s.length > 4 -> s.dropLast(2)
      s.endsWith("s") && !s.endsWith("ss") && !s.endsWith("us") && s.length > 3 -> s.dropLast(1)
      else -> s
    }
  }

  // Proximity bonus: reward documents where significant query tokens cluster
  // together in the body. Smaller span (tokens closer together) = higher bonus.
  private fun proximityBonus(body: String, qTokens: List<String>): Int {
    if (qTokens.size < 2) return 0
    val positions = qTokens.mapNotNull { tok ->
      val b = indexWordBoundary(body, tok)
      if (b >= 0) b else indexWordPrefix(body, tok).takeIf { it >= 0 }
    }.sorted()
    if (positions.size < 2) return 0
    val span = positions.last() - positions.first()
    return when {
      span < 50 -> 600
      span < 100 -> 400
      span < 200 -> 200
      span < 400 -> 100
      else -> 30
    }
  }

  private fun searchNotes(q: String, limit: Int): List<SearchHit> {
    val qTokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val hits = mutableListOf<SearchHit>()
    for (doc in noteDocs) {
      var score = 0
      val titleNorm = normalize(doc.title)
      for (tok in qTokens) {
        when { indexWordBoundary(titleNorm, tok) >= 0 -> score += 300; indexWordPrefix(titleNorm, tok) >= 0 -> score += 180; indexInfix(titleNorm, tok) >= 0 -> score += 20 }
        when { indexWordBoundary(doc.text, tok) >= 0 -> score += 80; indexWordPrefix(doc.text, tok) >= 0 -> score += 30; indexInfix(doc.text, tok) >= 0 -> score += 5 }
      }
      if (score <= 0) continue
      val snippet = makeTextSnippet(doc.text, qTokens)
      hits += SearchHit(doc.title, snippet, "", doc.route, "", score, SearchHitType.NOTE)
    }
    return hits.sortedByDescending { it.score }.take(limit)
  }

  private fun searchBooks(q: String, limit: Int): List<SearchHit> {
    val qTokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val hits = mutableListOf<SearchHit>()
    for (doc in bookDocs) {
      var score = 0
      for (tok in qTokens) {
        when { indexWordBoundary(doc.normalizedTitle, tok) >= 0 -> score += 400; indexWordPrefix(doc.normalizedTitle, tok) >= 0 -> score += 250; indexInfix(doc.normalizedTitle, tok) >= 0 -> score += 30; tok.length >= 4 && fuzzyContains(doc.normalizedTitle, tok) -> score += 120 }
      }
      if (score <= 0) continue
      hits += SearchHit(doc.title, doc.collection.replace('_', ' '), doc.collection, doc.bookId, "", score, SearchHitType.BOOK)
    }
    return hits.sortedByDescending { it.score }.take(limit)
  }

  private data class ExplicitRef(val family: String, val number: Int?, val chapter: Int)

  private fun parseExplicitRef(q: String): ExplicitRef? {
    val tokens = q.replace("_"," ").replace("-"," ").replace("."," ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    for (tok in tokens) {
      val m = Regex("^(\\d+|i{1,3}|first|second|third)?([a-z]+?)(\\d+)(?::\\d+)?$").matchEntire(tok.lowercase())
      if (m != null) {
        val numRaw = m.groupValues[1].ifBlank { null }
        val famRaw = m.groupValues[2]; val chapter = m.groupValues[3].toIntOrNull() ?: continue
        val num = numRaw?.let { parseLeadingNumber(it) }
        val (_, fam) = splitNumberedBook("${numRaw ?: ""} $famRaw")
        return ExplicitRef(fam, num, chapter)
      }
    }
    run {
      val (bookPart, chapTok) = splitBookAndChapter(tokens)
      if (bookPart.isNotEmpty() && chapTok != null) {
        val (num, fam) = splitNumberedBook(bookPart.joinToString(" "))
        if (fam.isNotBlank()) {
          val chapter = when { chapTok.matches(Regex("^\\d+:\\d+$")) -> chapTok.substringBefore(":").toIntOrNull(); chapTok.matches(Regex("^\\d+$")) -> chapTok.toIntOrNull(); else -> null }
          if (chapter != null) return ExplicitRef(fam, num, chapter)
        }
      }
    }
    for (i in tokens.indices) {
      val n = parseLeadingNumber(tokens[i])
      if (n != null) {
        val tail = tokens.drop(i + 1); val (bookPart, chapTok) = splitBookAndChapter(tail)
        if (bookPart.isEmpty() || chapTok == null) continue
        val (_, fam) = splitNumberedBook(bookPart.joinToString(" ")); if (fam.isBlank()) continue
        val chapter = when { chapTok.matches(Regex("^\\d+:\\d+$")) -> chapTok.substringBefore(":").toIntOrNull(); chapTok.matches(Regex("^\\d+$")) -> chapTok.toIntOrNull(); else -> null }
        if (chapter != null) return ExplicitRef(fam, n, chapter)
      }
    }
    return null
  }

  private fun splitBookAndChapter(tokens: List<String>): Pair<List<String>, String?> {
    val bookPart = mutableListOf<String>(); var chapterTok: String? = null
    for (t in tokens) { val tl = t.lowercase(); if (tl.matches(Regex("^\\d+$")) || tl.matches(Regex("^\\d+:\\d+$")) || tl.firstOrNull()?.isDigit() == true) { chapterTok = tl; break }; bookPart += tl }
    return bookPart to chapterTok
  }

  private fun searchByExplicitRef(ref: ExplicitRef): List<SearchHit> {
    val candidates = resolveBooks(ref.family, ref.number).ifEmpty { resolveBooks(ref.family, null) }
    if (candidates.isEmpty()) return emptyList()
    val out = mutableListOf<SearchHit>()
    for ((col, bookId, bookTitle) in candidates) {
      val key = "$col|$bookId"; val byChapter = chapterIndex[key]
      var storyId: String? = byChapter?.get(ref.chapter)
      if (storyId == null && byChapter != null) { val prev = byChapter.keys.filter { it <= ref.chapter }.maxOrNull(); storyId = prev?.let { byChapter[it] } }
      if (storyId == null) { storyId = docs.asSequence().filter { it.collection == col && it.bookId == bookId }.firstOrNull { spans -> spans.chapterSpans.any { ref.chapter in it } }?.storyId }
      if (storyId == null) continue
      val d = docs.firstOrNull { it.collection == col && it.bookId == bookId && it.storyId == storyId } ?: continue
      out += SearchHit("$bookTitle: ${d.title}", "Chapter ${ref.chapter} \u2192 ${d.refsJoined}", col, bookId, storyId, 10_000)
    }
    return out.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title.length })
  }

  private fun resolveBooks(family: String, number: Int?): List<Triple<String, String, String>> {
    val fam = family.lowercase(); val entries = bookLookup.entries.filter { (k, _) -> k.first.startsWith(fam) }
    return if (number != null) { val exact = entries.filter { (k, _) -> k.second == number }.flatMap { it.value }; exact.ifEmpty { entries.filter { (k, _) -> k.second == null }.flatMap { it.value } } }
    else entries.filter { (k, _) -> k.second == null }.flatMap { it.value }
  }

  private fun searchFlexible(q: String, limit: Int): List<SearchHit> {
    val numParse = parseNumberedBookFromQuery(q)
    val qTokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val lang = builtForLang ?: "en"
    // Stopword-filtered and stemmed tokens for phrase / fuzzy matching.
    val sigTokens = significantTokens(qTokens, lang)
    val stemmedSig = sigTokens.map { stemLite(it, lang) }
    // A "phrase query" is a user looking for a verse by quoting/paraphrasing it.
    // Heuristic: 3+ content words after stopwords, and no numbered-book parse
    // (numbered-book queries like "1 kings 2" should stay keyword-style).
    val isPhraseQuery = numParse == null && sigTokens.size >= 3

    val hits = mutableListOf<SearchHit>()
    for (d in docs) {
      if (numParse != null) {
        val (qNum, qFamily) = numParse
        if (d.familyKey in numberedFamilies && d.number != qNum) continue
        if (!familyMatches(qFamily, d.familyKey)) continue
      }
      var score = 0
      if (numParse != null) {
        val (qNum, qFamily) = numParse
        val key = "$qNum-$qFamily"
        score += if (d.bookKey == key) 800 else if (familyMatches(qFamily, d.familyKey)) 350 else 0
      } else {
        val fam = extractFamilyFromQuery(qTokens)
        if (fam != null && familyMatches(fam, d.familyKey)) score += 200
      }
      score += keywordScore(d, qTokens, isPhraseQuery)
      if (isPhraseQuery) {
        // Reward body matches against stemmed tokens (catches follow/follows/following)
        var stemBodyHits = 0
        for (stok in stemmedSig) {
          if (indexWordPrefix(d.text, stok) >= 0) stemBodyHits++
        }
        if (stemBodyHits >= 2) score += stemBodyHits * stemBodyHits * 80
        // Proximity bonus: reward when content tokens cluster together in body
        score += proximityBonus(d.text, sigTokens)
      }
      if (score <= 0) continue
      hits += SearchHit("${d.bookTitle}: ${d.title}", makeSnippet(d, qTokens), d.collection, d.bookId, d.storyId, score)
    }
    return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title.length }).take(limit)
  }

  private fun normalize(s: String): String = normalizeNFKD(s).lowercase().replace(Regex("[^\\p{L}\\p{N}\\s:-]"), " ").replace(Regex("\\s+"), " ").trim()

  private fun normalizeBookKey(title: String): Triple<String, String, Int?> { val n = normalize(title); val (num, family) = splitNumberedBook(n); val key = if (num != null) "$num-$family" else family; return Triple(key, family, num) }
  private fun parseNumberedBookFromQuery(qNormalized: String): Pair<Int, String>? { val s = qNormalized.replace("_"," ").replace("-"," ").replace("."," ").trim(); val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }; for (i in tokens.indices) { val n = parseLeadingNumber(tokens[i]); if (n != null) { val tail = tokens.drop(i).joinToString(" "); val (num, fam) = splitNumberedBook(tail); if (num != null && fam.isNotBlank()) return num to fam } }; for (tok in tokens) { val (num, fam) = splitNumberedBook(tok); if (num != null && fam.isNotBlank()) return num to fam }; return null }
  private fun parseLeadingNumber(tok: String): Int? = when (tok.lowercase()) { "1","i","first" -> 1; "2","ii","second" -> 2; "3","iii","third" -> 3; else -> null }
  private fun splitNumberedBook(n: String): Pair<Int?, String> { val s = n.replace("_"," ").replace("-"," ").replace("."," ").trim(); val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList(); if (tokens.isEmpty()) return null to ""; if (tokens.size == 1) { val t = tokens[0].lowercase(); val m = Regex("^(\\d+|i{1,3}|first|second|third)([a-z]+)$").matchEntire(t); if (m != null) { tokens.clear(); tokens += m.groupValues[1]; tokens += m.groupValues[2] } }; val number = parseLeadingNumber(tokens.first().lowercase()); val familyStartIdx = if (number != null) 1 else 0; val family = tokens.drop(familyStartIdx).joinToString(" ").replace(" ","").trim(); return number to family }
  private fun familyMatches(qFamily: String, docFamily: String): Boolean { if (qFamily.isBlank() || docFamily.isBlank()) return false; val q = qFamily.lowercase(); val d = docFamily.lowercase(); if (q.length < 2 || d.length < 2) return false; return d.startsWith(q) || q.startsWith(d) }
  private fun extractFamilyFromQuery(tokens: List<String>): String? { if (tokens.isEmpty()) return null; val maybeNum = parseLeadingNumber(tokens.first()); val famTokens = if (maybeNum != null) tokens.drop(1) else tokens; if (famTokens.isEmpty()) return null; return famTokens.joinToString(" ").replace(" ","") }

  private fun indexWordBoundary(h: String, t: String): Int { val m = Regex("\\b${Regex.escape(t.lowercase())}\\b").find(h.lowercase()); return m?.range?.first ?: -1 }
  private fun indexWordPrefix(h: String, t: String): Int { val m = Regex("\\b${Regex.escape(t.lowercase())}[\\p{L}\\p{N}]*").find(h.lowercase()); return m?.range?.first ?: -1 }
  private fun indexInfix(h: String, t: String): Int = h.lowercase().indexOf(t.lowercase())

  // True iff Levenshtein(a, b) <= 1. O(max(|a|,|b|)). Used for typo-tolerant search.
  private fun editDistLe1(a: String, b: String): Boolean {
    if (a == b) return true
    val la = a.length; val lb = b.length
    if (kotlin.math.abs(la - lb) > 1) return false
    if (la == lb) {
      var diffs = 0
      for (i in 0 until la) {
        if (a[i] != b[i]) { diffs++; if (diffs > 1) return false }
      }
      return true
    }
    val shorter = if (la < lb) a else b
    val longer = if (la < lb) b else a
    var i = 0; var j = 0; var used = 0
    while (i < shorter.length && j < longer.length) {
      if (shorter[i] == longer[j]) { i++; j++ }
      else { j++; used++; if (used > 1) return false }
    }
    return true
  }

  // Returns true if any word in [haystack] is within edit distance 1 of [needle].
  // Only used as a last-resort scoring step; infix/prefix matches are preferred.
  private fun fuzzyContains(haystack: String, needle: String): Boolean {
    val n = needle.lowercase()
    if (n.length < 4) return false
    val lowered = haystack.lowercase()
    // Fast reject: skip if the haystack has no characters at all or no candidate-length words.
    var wordStart = -1
    var i = 0
    while (i <= lowered.length) {
      val ch = if (i < lowered.length) lowered[i] else ' '
      val isWord = ch.isLetterOrDigit()
      if (isWord && wordStart < 0) wordStart = i
      if (!isWord && wordStart >= 0) {
        val w = lowered.substring(wordStart, i)
        wordStart = -1
        if (kotlin.math.abs(w.length - n.length) <= 1 && editDistLe1(w, n)) return true
      }
      i++
    }
    return false
  }

  private fun keywordScore(d: Doc, qTokens: List<String>, isPhraseQuery: Boolean = false): Int {
    var score = 0
    val t = d.text; val ref = d.refsJoined; val title = d.title
    // For phrase queries, body matches are the signal — boost them so content
    // matches in verse text outrank incidental title matches.
    val bodyMul = if (isPhraseQuery) 3 else 1
    var bodyMatches = 0
    for (tok in qTokens) {
      if (tok.isBlank()) continue
      when {
        indexWordBoundary(title,tok)>=0 -> score+=200
        indexWordPrefix(title,tok)>=0 -> score+=120
        indexInfix(title,tok)>=0 -> score+=15
        tok.length >= 4 && fuzzyContains(title, tok) -> score+=50
      }
      when {
        indexWordBoundary(ref,tok)>=0 -> score+=150
        indexWordPrefix(ref,tok)>=0 -> score+=90
        indexInfix(ref,tok)>=0 -> score+=10
      }
      val bodyHit = when {
        indexWordBoundary(t,tok)>=0 -> 90
        indexWordPrefix(t,tok)>=0 -> 35
        indexInfix(t,tok)>=0 -> 5
        tok.length >= 4 && fuzzyContains(t, tok) -> 18
        else -> 0
      }
      if (bodyHit > 0) { score += bodyHit * bodyMul; bodyMatches++ }
      val fam = tok.replace(" ",""); if (familyMatches(fam,d.familyKey)) score+=50
    }
    if (isPhraseQuery && bodyMatches >= 2) {
      // Multi-token overlap bonus — N unique tokens matching in body scores N^2 * 60
      score += bodyMatches * bodyMatches * 60
    }
    return score
  }

  private fun makeSnippet(d: Doc, qTokens: List<String>, maxLen: Int = 160): String {
    val ref = d.refsJoined; val firstTok = qTokens.firstOrNull() ?: return ellipsize(ref, maxLen)
    fun tryH(s: String, f: (String,String)->Int): String? { val idx = f(s,firstTok); if (idx<0) return null; return highlight(ellipsizeAround(s,idx,firstTok.length,maxLen),firstTok) }
    return tryH(ref, ::indexWordBoundary) ?: tryH(d.title, ::indexWordBoundary) ?: tryH(d.text, ::indexWordBoundary) ?: tryH(ref, ::indexWordPrefix) ?: tryH(d.title, ::indexWordPrefix) ?: tryH(d.text, ::indexWordPrefix) ?: tryH(ref, ::indexInfix) ?: tryH(d.title, ::indexInfix) ?: tryH(d.text, ::indexInfix) ?: ellipsize(ref.ifBlank { d.title }, maxLen)
  }

  private fun makeTextSnippet(text: String, qTokens: List<String>, maxLen: Int = 160): String {
    val firstTok = qTokens.firstOrNull() ?: return ellipsize(text, maxLen)
    fun tryH(f: (String, String) -> Int): String? { val idx = f(text, firstTok); if (idx < 0) return null; return highlight(ellipsizeAround(text, idx, firstTok.length, maxLen), firstTok) }
    return tryH(::indexWordBoundary) ?: tryH(::indexWordPrefix) ?: tryH(::indexInfix) ?: ellipsize(text, maxLen)
  }

  private fun highlight(s: String, token: String): String { if (token.isBlank()) return s; val i = s.lowercase().indexOf(token.lowercase()); if (i<0) return s; val end = (i+token.length).coerceAtMost(s.length); return s.substring(0,i)+"[["+s.substring(i,end)+"]]"+s.substring(end) }
  private fun ellipsize(s: String, maxLen: Int): String { val str = s.replace("\n"," ").trim(); if (str.length<=maxLen) return str; return str.take(maxLen-1)+"\u2026" }
  private fun ellipsizeAround(s: String, hitStart: Int, hitLen: Int, maxLen: Int): String { val str = s.replace("\n"," ").trim(); if (str.length<=maxLen) return str; val mid = (hitStart+hitLen/2).coerceIn(0,str.length); val half = maxLen/2; val from = (mid-half).coerceAtLeast(0); val to = min(from+maxLen,str.length); val slice = str.substring(from,to); return (if (from>0) "\u2026" else "")+slice+(if (to<str.length) "\u2026" else "") }

  private fun extractChapterSpans(refs: String): List<IntRange> {
    if (refs.isBlank()) return emptyList()
    val s = refs.lowercase().replace('\u2013','-').replace('\u2014','-')
    val spans = mutableListOf<IntRange>(); val parts = s.split("\u2022",";").map { it.trim() }
    val pattern = Regex("(\\d+)\\s*(?::\\d+)?\\s*(?:-\\s*(\\d+)\\s*(?::\\d+)?)?")
    for (p in parts) { for (m in pattern.findAll(p)) { val startChap = m.groupValues[1].toIntOrNull() ?: continue; val endChap = m.groupValues.getOrNull(2)?.toIntOrNull() ?: startChap; if (endChap>=startChap) spans += (startChap..endChap) } }
    return spans
  }
}
