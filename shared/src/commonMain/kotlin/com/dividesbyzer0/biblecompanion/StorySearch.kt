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

  private data class VerseDoc(
    val collection: String,
    val bookId: String,
    val bookTitle: String,
    val storyId: String,
    val bulletIdx: Int,
    val chapter: Int,
    val verse: Int,
    val verseEnd: Int?,
    val text: String,
    val rawText: String
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
  private val verseDocs = mutableListOf<VerseDoc>()
  private val verseInvertedIndex = mutableMapOf<String, MutableList<Int>>()
  private var sortedVocabulary: List<String> = emptyList()
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

  private val bulletRefPattern = Regex("""\((\d+):(\d+)(?:\s*-\s*(\d+))?\)\s*\.?\s*$""")

  fun build(context: PlatformContext, appLang: String) {
    if (builtForLang == appLang) return
    docs.clear(); chapterIndex.clear(); bookLookup.clear(); numberedFamilies.clear()
    noteDocs.clear(); bookDocs.clear(); verseDocs.clear(); verseInvertedIndex.clear()
    sortedVocabulary = emptyList()

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
          val refsJoined = story.refs.joinToString(" • ")
          val body = buildString {
            appendLine(story.title); appendLine(refsJoined)
            story.summaryBullets.forEach { appendLine(it) }; appendLine(story.keyTakeaway)
            story.crossRefs.forEach { appendLine(it) }
            story.translationNotes.forEach { tn -> appendLine(tn.term); tn.original?.let { appendLine(it) }; appendLine(tn.note) }
          }
          docs += Doc(col, bookId, bookTitle, bookKey, familyKey, num, story.id, story.title, refsJoined, normalize(body), extractChapterSpans(refsJoined))

          for ((bIdx, bullet) in story.summaryBullets.withIndex()) {
            val m = bulletRefPattern.find(bullet) ?: continue
            val ch = m.groupValues[1].toIntOrNull() ?: continue
            val v = m.groupValues[2].toIntOrNull() ?: continue
            val vEnd = m.groupValues[3].toIntOrNull()
            val normText = normalize(bullet)
            val vDocIdx = verseDocs.size
            verseDocs += VerseDoc(col, bookId, bookTitle, story.id, bIdx, ch, v, vEnd, normText, bullet)
            for (word in normText.split(Regex("\\s+")).filter { it.length >= 2 }) {
              verseInvertedIndex.getOrPut(word) { mutableListOf() }.add(vDocIdx)
            }
          }
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

    sortedVocabulary = verseInvertedIndex.keys.sorted()
    builtForLang = appLang
  }

  private fun prefixCandidates(prefix: String, into: MutableSet<Int>) {
    if (prefix.isEmpty()) return
    val vocab = sortedVocabulary
    if (vocab.isEmpty()) return
    var lo = 0
    var hi = vocab.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (vocab[mid] < prefix) lo = mid + 1 else hi = mid
    }
    var i = lo
    while (i < vocab.size && vocab[i].startsWith(prefix)) {
      verseInvertedIndex[vocab[i]]?.let { into.addAll(it) }
      i++
    }
  }

  fun ensureBuilt(context: PlatformContext, appLang: String) {
    if (builtForLang != appLang) runCatching { build(context, appLang) }
  }

  fun search(queryRaw: String, limit: Int = 30): List<SearchHit> {
    if (docs.isEmpty()) return emptyList()
    val q = normalize(queryRaw).trim()
    if (q.length < 2) return emptyList()
    parseExplicitRef(q)?.let { ref -> val hits = searchByExplicitRef(ref); if (hits.isNotEmpty()) return hits.take(limit) }

    val storyHits = searchFlexible(q, 25)
    val verseHits = searchVerses(q, 25)
    val noteHits = searchNotes(q, 5)
    val bookHits = searchBooks(q, 5)

    val merged = mergeStoryAndVerseHits(storyHits, verseHits)

    return (merged + bookHits + noteHits)
      .sortedWith(
        compareByDescending<SearchHit> { it.score }
          .thenBy { when (it.type) { SearchHitType.STORY -> 0; SearchHitType.BOOK -> 1; SearchHitType.NOTE -> 2 } }
          .thenBy { it.title.length }
      )
      .take(limit)
  }

  // --- Synonym / word-family expansion (English only) ---

  private val synonymLookup: Map<String, Set<String>> = run {
    val groups = listOf(
      setOf("true", "truth", "truly"),
      setOf("righteous", "righteousness"),
      setOf("holy", "holiness"),
      setOf("mercy", "merciful", "mercies"),
      setOf("grace", "gracious"),
      setOf("glory", "glorious", "glorify", "glorified"),
      setOf("faith", "faithful", "faithfulness", "faithless"),
      setOf("just", "justice", "justify", "justified", "justification"),
      setOf("wise", "wisdom"),
      setOf("save", "salvation", "savior", "saved"),
      setOf("redeem", "redemption", "redeemer", "redeemed"),
      setOf("forgive", "forgiveness", "forgiven"),
      setOf("repent", "repentance"),
      setOf("prophesy", "prophecy", "prophet", "prophets", "prophetic"),
      setOf("bless", "blessed", "blessing", "blessings"),
      setOf("worship", "worshipped", "worshipper"),
      setOf("sin", "sinful", "sinner", "sinners"),
      setOf("believe", "belief", "believer"),
      setOf("pray", "prayer", "prayers", "prayed"),
      setOf("baptize", "baptism", "baptized"),
      setOf("destroy", "destruction", "destroyer"),
      setOf("obey", "obedient", "obedience"),
      setOf("know", "knowledge", "known"),
      setOf("die", "death", "dead", "died"),
      setOf("live", "life", "alive", "living"),
      setOf("king", "kingdom", "kings", "kingly"),
      setOf("priest", "priesthood", "priestly"),
      setOf("heal", "healing", "healed", "healer"),
      setOf("judge", "judgment", "judgments"),
      setOf("strong", "strength"),
      setOf("create", "creation", "creator", "created"),
      setOf("resurrect", "resurrection"),
      setOf("eternal", "everlasting", "eternity"),
      setOf("wrath", "wrathful"),
      setOf("sanctify", "sanctification", "sanctified"),
      setOf("covenant", "covenants"),
      setOf("spirit", "spiritual"),
      setOf("angel", "angels", "angelic"),
      setOf("demon", "demons", "demonic"),
      setOf("heaven", "heavenly", "heavens"),
      setOf("fear", "fearful", "afraid"),
      setOf("lamb", "lambs"),
      setOf("blood", "bloodshed"),
      setOf("cross", "crucify", "crucified", "crucifixion"),
      setOf("servant", "serve", "service"),
      setOf("temple", "temples"),
      setOf("altar", "altars"),
      setOf("sacrifice", "sacrificial", "sacrificed")
    )
    val map = mutableMapOf<String, Set<String>>()
    for (group in groups) {
      for (word in group) {
        map[word] = group - word
      }
    }
    map
  }

  private fun expandWithSynonyms(token: String, lang: String): List<String> {
    if (lang != "en") return listOf(token)
    val syns = synonymLookup[token.lowercase()] ?: return listOf(token)
    return listOf(token) + syns.toList()
  }

  // --- Stopwords ---

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

  // --- Stemmer ---

  private fun stemLite(w: String, lang: String): String {
    if (lang != "en" || w.length < 4) return w
    val s = w.lowercase()
    return when {
      s.endsWith("ness") && s.length > 6 -> s.dropLast(4)
      s.endsWith("ment") && s.length > 6 -> s.dropLast(4)
      s.endsWith("tion") && s.length > 6 -> s.dropLast(4)
      s.endsWith("sion") && s.length > 6 -> s.dropLast(4)
      s.endsWith("ous") && s.length > 5 -> s.dropLast(3)
      s.endsWith("ful") && s.length > 5 -> s.dropLast(3)
      s.endsWith("ual") && s.length > 5 -> s.dropLast(3)
      s.endsWith("ity") && s.length > 5 -> s.dropLast(3)
      s.endsWith("ive") && s.length > 5 -> s.dropLast(3)
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

  // --- Proximity scoring ---

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

  // --- Verse-level search ---

  private fun searchVerses(q: String, limit: Int): List<SearchHit> {
    if (verseDocs.isEmpty()) return emptyList()
    val lang = builtForLang ?: "en"
    val qTokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val sigTokens = significantTokens(qTokens, lang)
    if (sigTokens.isEmpty()) return emptyList()
    val stemmedSig = sigTokens.map { stemLite(it, lang) }

    val expandedTokens = sigTokens.flatMap { expandWithSynonyms(it, lang) }.distinct()
    val expandedStems = expandedTokens.map { stemLite(it, lang) }.distinct()

    val candidates = mutableSetOf<Int>()
    for (tok in expandedTokens) {
      verseInvertedIndex[tok]?.let { candidates.addAll(it) }
    }
    for (stem in expandedStems) {
      if (stem.length >= 3) prefixCandidates(stem, candidates)
    }

    if (candidates.isEmpty()) return emptyList()

    data class VerseHit(val vDocIdx: Int, val score: Int)
    val verseHits = mutableListOf<VerseHit>()

    for (idx in candidates) {
      val vd = verseDocs[idx]
      var score = 0
      var directHits = 0
      var totalHits = 0

      for (tok in sigTokens) {
        val directScore = scoreToken(vd.text, tok)
        if (directScore > 0) {
          score += directScore
          directHits++
          totalHits++
          continue
        }
        val stemmed = stemLite(tok, lang)
        val stemScore = if (indexWordPrefix(vd.text, stemmed) >= 0) 35 else 0
        if (stemScore > 0) {
          score += stemScore
          totalHits++
          continue
        }
        val syns = synonymLookup[tok.lowercase()]
        if (syns != null) {
          var bestSynScore = 0
          for (syn in syns) {
            val ss = scoreToken(vd.text, syn)
            if (ss > bestSynScore) bestSynScore = ss
            if (bestSynScore > 0) break
          }
          if (bestSynScore == 0) {
            for (syn in syns) {
              val synStem = stemLite(syn, lang)
              if (indexWordPrefix(vd.text, synStem) >= 0) { bestSynScore = 28; break }
            }
          }
          if (bestSynScore > 0) {
            score += (bestSynScore * 6) / 10
            totalHits++
          }
        }
      }

      if (totalHits == 0) continue

      if (totalHits == sigTokens.size) score += 300
      if (totalHits >= 2) score += totalHits * totalHits * 50

      if (q.length >= 5 && indexInfix(vd.text, q) >= 0) score += 3000

      score += when (vd.collection) {
        "old_testament", "new_testament" -> 100
        "deuterocanonical" -> 30
        else -> 0
      }

      if (score > 0) verseHits += VerseHit(idx, score)
    }

    val grouped = verseHits
      .groupBy { verseDocs[it.vDocIdx].let { vd -> Triple(vd.collection, vd.bookId, vd.storyId) } }
      .map { (key, hits) -> hits.maxByOrNull { it.score }!! }
      .sortedByDescending { it.score }
      .take(limit)

    return grouped.map { vh ->
      val vd = verseDocs[vh.vDocIdx]
      val ref = "${vd.bookTitle} ${vd.chapter}:${vd.verse}${if (vd.verseEnd != null) "-${vd.verseEnd}" else ""}"
      val snippet = cleanBulletForDisplay(vd.rawText)
      SearchHit(ref, snippet, vd.collection, vd.bookId, vd.storyId, vh.score, SearchHitType.STORY, vd.verse, vd.verseEnd)
    }
  }

  private fun scoreToken(text: String, tok: String): Int = when {
    indexWordBoundary(text, tok) >= 0 -> 100
    indexWordPrefix(text, tok) >= 0 -> 40
    indexInfix(text, tok) >= 0 -> 8
    tok.length >= 4 && fuzzyContains(text, tok) -> 20
    else -> 0
  }

  private fun cleanBulletForDisplay(raw: String): String {
    return raw
      .replace("[J]", "").replace("[/J]", "")
      .replace(Regex("""\s*\(\d+:\d+(?:\s*-\s*\d+)?\)\s*\.?\s*$"""), "")
      .trim()
  }

  private fun mergeStoryAndVerseHits(storyHits: List<SearchHit>, verseHits: List<SearchHit>): List<SearchHit> {
    val storyMap = storyHits.associateBy { Triple(it.collection, it.bookId, it.storyId) }.toMutableMap()

    for (vh in verseHits) {
      val key = Triple(vh.collection, vh.bookId, vh.storyId)
      val existing = storyMap[key]
      if (existing != null) {
        val boostedScore = maxOf(existing.score, vh.score) + minOf(existing.score, vh.score) / 4
        storyMap[key] = if (vh.score >= existing.score) {
          vh.copy(score = boostedScore)
        } else {
          existing.copy(score = boostedScore, verse = vh.verse, verseEnd = vh.verseEnd)
        }
      } else {
        storyMap[key] = vh
      }
    }

    return storyMap.values.toList()
  }

  // --- Note search ---

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

  // --- Book search ---

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

  // --- Explicit reference search ---

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
      out += SearchHit("$bookTitle: ${d.title}", "Chapter ${ref.chapter} → ${d.refsJoined}", col, bookId, storyId, 10_000)
    }
    return out.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title.length })
  }

  private fun resolveBooks(family: String, number: Int?): List<Triple<String, String, String>> {
    val fam = family.lowercase(); val entries = bookLookup.entries.filter { (k, _) -> k.first.startsWith(fam) }
    return if (number != null) { val exact = entries.filter { (k, _) -> k.second == number }.flatMap { it.value }; exact.ifEmpty { entries.filter { (k, _) -> k.second == null }.flatMap { it.value } } }
    else entries.filter { (k, _) -> k.second == null }.flatMap { it.value }
  }

  // --- Flexible story search ---

  private fun searchFlexible(q: String, limit: Int): List<SearchHit> {
    val numParse = parseNumberedBookFromQuery(q)
    val qTokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val lang = builtForLang ?: "en"
    val sigTokens = significantTokens(qTokens, lang)
    val stemmedSig = sigTokens.map { stemLite(it, lang) }
    val isPhraseQuery = numParse == null && sigTokens.size >= 2

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
      score += keywordScore(d, qTokens, sigTokens, lang, isPhraseQuery)
      if (q.length >= 5 && indexInfix(d.text, q) >= 0) {
        score += 3000
      }
      if (isPhraseQuery) {
        var stemBodyHits = 0
        for (stok in stemmedSig) {
          if (indexWordPrefix(d.text, stok) >= 0) stemBodyHits++
        }
        if (stemBodyHits >= 2) score += stemBodyHits * stemBodyHits * 80
        score += proximityBonus(d.text, sigTokens)
      }
      if (score <= 0) continue
      score += when (d.collection) {
        "old_testament", "new_testament" -> 150
        "deuterocanonical" -> 50
        else -> 0
      }
      hits += SearchHit("${d.bookTitle}: ${d.title}", makeSnippet(d, qTokens), d.collection, d.bookId, d.storyId, score)
    }
    return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title.length }).take(limit)
  }

  // --- Text utilities ---

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

  private fun fuzzyContains(haystack: String, needle: String): Boolean {
    val n = needle.lowercase()
    if (n.length < 4) return false
    val lowered = haystack.lowercase()
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

  private fun keywordScore(d: Doc, qTokens: List<String>, sigTokens: List<String>, lang: String, isPhraseQuery: Boolean = false): Int {
    var score = 0
    val t = d.text; val ref = d.refsJoined; val title = d.title
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
      var bodyHit = when {
        indexWordBoundary(t,tok)>=0 -> 90
        indexWordPrefix(t,tok)>=0 -> 35
        indexInfix(t,tok)>=0 -> 5
        tok.length >= 4 && fuzzyContains(t, tok) -> 18
        else -> 0
      }
      if (bodyHit == 0 && lang == "en") {
        val syns = synonymLookup[tok.lowercase()]
        if (syns != null) {
          for (syn in syns) {
            val ss = when {
              indexWordBoundary(t, syn) >= 0 -> 54
              indexWordPrefix(t, syn) >= 0 -> 21
              else -> 0
            }
            if (ss > bodyHit) bodyHit = ss
            if (bodyHit > 0) break
          }
        }
      }
      if (bodyHit > 0) { score += bodyHit * bodyMul; bodyMatches++ }
      val fam = tok.replace(" ",""); if (familyMatches(fam,d.familyKey)) score+=50
    }
    if (isPhraseQuery && bodyMatches >= 2) {
      score += bodyMatches * bodyMatches * 60
    }
    return score
  }

  // --- Snippets ---

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
  private fun ellipsize(s: String, maxLen: Int): String { val str = s.replace("\n"," ").trim(); if (str.length<=maxLen) return str; return str.take(maxLen-1)+"…" }
  private fun ellipsizeAround(s: String, hitStart: Int, hitLen: Int, maxLen: Int): String { val str = s.replace("\n"," ").trim(); if (str.length<=maxLen) return str; val mid = (hitStart+hitLen/2).coerceIn(0,str.length); val half = maxLen/2; val from = (mid-half).coerceAtLeast(0); val to = min(from+maxLen,str.length); val slice = str.substring(from,to); return (if (from>0) "…" else "")+slice+(if (to<str.length) "…" else "") }

  private fun extractChapterSpans(refs: String): List<IntRange> {
    if (refs.isBlank()) return emptyList()
    val s = refs.lowercase().replace('–','-').replace('—','-')
    val spans = mutableListOf<IntRange>(); val parts = s.split("•",";").map { it.trim() }
    val pattern = Regex("(\\d+)\\s*(?::\\d+)?\\s*(?:-\\s*(\\d+)\\s*(?::\\d+)?)?")
    for (p in parts) { for (m in pattern.findAll(p)) { val startChap = m.groupValues[1].toIntOrNull() ?: continue; val endChap = m.groupValues.getOrNull(2)?.toIntOrNull() ?: startChap; if (endChap>=startChap) spans += (startChap..endChap) } }
    return spans
  }
}
