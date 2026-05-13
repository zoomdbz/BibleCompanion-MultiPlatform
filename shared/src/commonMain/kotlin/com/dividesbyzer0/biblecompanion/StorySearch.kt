package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.PlatformContext
import com.dividesbyzer0.biblecompanion.platform.normalizeNFKD
import com.dividesbyzer0.biblecompanion.platform.readAssetText
import kotlin.math.min
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object StorySearch {

  private data class Doc(
    val collection: String, val bookId: String, val bookTitle: String,
    val bookKey: String, val familyKey: String, val number: Int?,
    val storyId: String, val title: String, val refsJoined: String,
    val text: String, val crossRefText: String,
    val chapterSpans: List<IntRange>,
    val summaryPreview: String = ""
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
    val rawText: String,
    val words: List<String>
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
  private var resolvedLang: String = "en"
  private val buildMutex = Mutex()
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

  private fun chapterWord(): String = when (resolvedLang.lowercase()) {
    "es" -> "Capítulo"; "fr" -> "Chapitre"; "de" -> "Kapitel"; "it" -> "Capitolo"
    "pt" -> "Capítulo"; "ru" -> "Глава"; "ar" -> "الفصل"; "hi" -> "अध्याय"
    "ja" -> "章"; "ko" -> "장"; "zh-hans" -> "章"; "zh-hant" -> "章"
    else -> "Chapter"
  }

  private val noteFiles = listOf(
    "translation_notes.md" to "translation_notes",
    "historical_awareness.md" to "historical_awareness",
    "bible_canon.md" to "bible_canon",
    "jesus_divinity.md" to "jesus_divinity",
    "grace.md" to "grace",
    "christian_symbolism.md" to "christian_symbolism",
    "unseen_war.md" to "unseen_war",
    "false_doctrine.md" to "false_doctrine",
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

  private fun buildLocked(context: PlatformContext, appLang: String) {
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
          val refsJoined = story.refs.map { ScriptureRefs.localizeRef(it) }.joinToString(" • ")
          val body = buildString {
            appendLine(story.title); appendLine(refsJoined)
            story.summaryBullets.forEach { appendLine(it) }; appendLine(story.keyTakeaway)
            story.translationNotes.forEach { tn -> appendLine(tn.term); tn.original?.let { appendLine(it) }; appendLine(tn.note) }
          }
          val xref = buildString { story.crossRefs.forEach { appendLine(it) } }
          val preview = story.summaryBullets.take(2).joinToString(" ") {
            it.replace(Regex("\\[(?!/?J]).*?]\\s*"), "").trim()
          }.take(200)
          docs += Doc(col, bookId, bookTitle, bookKey, familyKey, num, story.id, story.title, refsJoined, normalize(body), normalize(xref), extractChapterSpans(refsJoined), preview)

          for ((bIdx, bullet) in story.summaryBullets.withIndex()) {
            val m = bulletRefPattern.find(bullet) ?: continue
            val ch = m.groupValues[1].toIntOrNull() ?: continue
            val v = m.groupValues[2].toIntOrNull() ?: continue
            val vEnd = m.groupValues[3].toIntOrNull()
            val normText = normalize(bullet)
            val allWords = normText.split(' ').filter { it.isNotEmpty() }
            val vDocIdx = verseDocs.size
            verseDocs += VerseDoc(col, bookId, bookTitle, story.id, bIdx, ch, v, vEnd, normText, bullet, allWords)
            for (word in allWords) {
              if (word.length >= 2) verseInvertedIndex.getOrPut(word) { mutableListOf() }.add(vDocIdx)
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
    resolvedLang = LocaleUtils.effectiveAssetTag(appLang)
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

  suspend fun ensureBuilt(context: PlatformContext, appLang: String) {
    if (builtForLang == appLang) return
    buildMutex.withLock {
      if (builtForLang == appLang) return@withLock
      runCatching { buildLocked(context, appLang) }
    }
  }

  fun isReady(appLang: String): Boolean = builtForLang == appLang && docs.isNotEmpty()

  internal fun storyTitle(storyId: String): String? =
    docs.firstOrNull { it.storyId == storyId }?.title

  internal fun storySnippet(storyId: String): String? =
    docs.firstOrNull { it.storyId == storyId }?.refsJoined

  internal fun storySummaryPreview(storyId: String): String? =
    docs.firstOrNull { it.storyId == storyId }?.summaryPreview?.ifBlank { null }

  // True iff the query parses as a Bible reference (e.g., "John 3:16", "1 Cor 13").
  // Callers use this to decide whether to skip the expensive semantic step:
  // an explicit-ref query already has a deterministic best hit and doesn't
  // benefit from semantic embedding lookup.
  fun isExplicitReference(queryRaw: String): Boolean {
    val qNorm = normalize(queryRaw).trim()
    if (qNorm.length < 2) return false
    val q = if (resolvedLang == "en") correctQuery(qNorm) else qNorm
    return parseExplicitRef(q) != null
  }

  suspend fun search(queryRaw: String, limit: Int = 30): List<SearchHit> = buildMutex.withLock {
    if (docs.isEmpty()) return@withLock emptyList()
    val qNorm = normalize(queryRaw).trim()
    if (qNorm.length < 2) return@withLock emptyList()
    val q = if (resolvedLang == "en") correctQuery(qNorm) else qNorm
    parseExplicitRef(q)?.let { ref -> val hits = searchByExplicitRef(ref); if (hits.isNotEmpty()) return@withLock hits.take(limit) }

    val storyHits = searchFlexible(q, 25)
    val verseHits = searchVerses(q, 25)
    val noteHits = searchNotes(q, 5)
    val bookHits = searchBooks(q, 5)

    val merged = mergeStoryAndVerseHits(storyHits, verseHits)

    (merged + bookHits + noteHits)
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
      // --- Core theological concepts ---
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
      setOf("pray", "prayer", "prayers", "prayed", "praying"),
      setOf("baptize", "baptism", "baptized"),
      setOf("destroy", "destruction", "destroyer"),
      setOf("obey", "obedient", "obedience"),
      setOf("know", "knowledge", "known"),
      setOf("die", "death", "dead", "died", "dying"),
      setOf("live", "life", "alive", "living"),
      setOf("king", "kingdom", "kings", "kingly"),
      setOf("priest", "priesthood", "priestly"),
      setOf("heal", "healing", "healed", "healer"),
      setOf("judge", "judgment", "judgments", "judging"),
      setOf("strong", "strength", "strengthen", "strengthened"),
      setOf("create", "creation", "creator", "created"),
      setOf("resurrect", "resurrection", "resurrected", "risen", "raised"),
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
      setOf("transfigure", "transfigured", "transfiguration"),
      setOf("ascend", "ascended", "ascending", "ascension"),
      setOf("part", "parted", "parting", "divide", "divided", "split"),
      setOf("servant", "serve", "service", "serving"),
      setOf("temple", "temples"),
      setOf("altar", "altars"),
      setOf("sacrifice", "sacrificial", "sacrificed", "sacrificing"),
      setOf("love", "loved", "loves", "loving", "beloved"),
      setOf("atone", "atonement"),
      setOf("propitiate", "propitiation"),
      setOf("reconcile", "reconciled", "reconciliation"),
      setOf("intercede", "intercession", "intercessor"),
      setOf("elect", "elected", "election", "chosen"),
      setOf("anoint", "anointed", "anointing"),
      setOf("tithe", "tithes", "tithing"),
      setOf("offering", "offerings", "offered"),
      setOf("consecrate", "consecrated", "consecration"),
      setOf("cleanse", "cleansed", "cleansing", "purify", "purified", "purification", "pure", "purity"),
      setOf("sovereign", "sovereignty"),
      setOf("almighty", "omnipotent", "omnipotence"),
      setOf("incarnate", "incarnation"),
      setOf("gospel", "gospels"),
      setOf("doctrine", "doctrines"),
      setOf("heresy", "heresies", "heretic", "heretical"),
      setOf("apostle", "apostles", "apostolic"),
      setOf("disciple", "disciples", "discipleship"),
      setOf("evangelize", "evangelism", "evangelist", "evangelists"),
      setOf("testify", "testimony", "testimonies", "witness", "witnesses"),
      setOf("revelation", "reveal", "revealed", "revealing"),
      // --- Biblical events and observances ---
      setOf("flood", "flooded", "flooding", "deluge"),
      setOf("plague", "plagues", "pestilence"),
      setOf("passover", "pesach"),
      setOf("exile", "exiled", "exiles", "captivity", "captive", "captives"),
      setOf("parable", "parables"),
      setOf("tribulation", "tribulations"),
      setOf("pentecost", "shavuot"),
      setOf("tabernacle", "tabernacles", "sukkot"),
      setOf("feast", "feasts", "festival", "festivals"),
      setOf("sabbath", "sabbaths"),
      setOf("circumcise", "circumcised", "circumcision"),
      // --- Actions ---
      setOf("preach", "preached", "preaching", "preacher"),
      setOf("teach", "taught", "teacher", "teachers", "teaching", "teachings"),
      setOf("confess", "confessed", "confession"),
      setOf("fast", "fasted", "fasting"),
      setOf("praise", "praised", "praising", "praises"),
      setOf("thank", "thanks", "thankful", "thanksgiving"),
      setOf("rebuke", "rebuked", "rebuking"),
      setOf("persecute", "persecuted", "persecution", "persecutor"),
      setOf("tempt", "tempted", "temptation", "temptations"),
      setOf("deliver", "delivered", "deliverance", "deliverer"),
      setOf("promise", "promised", "promises"),
      setOf("command", "commanded", "commandment", "commandments"),
      setOf("gather", "gathered", "gathering", "assembly", "congregation"),
      setOf("suffer", "suffered", "suffering", "sufferings"),
      setOf("endure", "endured", "endurance"),
      setOf("conquer", "conquered", "victory", "victories", "victorious", "triumph"),
      setOf("inherit", "inherited", "inheritance"),
      setOf("dwell", "dwelling", "dwelt", "abide", "abode"),
      setOf("flee", "fled", "fleeing", "escape", "escaped"),
      setOf("wander", "wandered", "wandering"),
      setOf("proclaim", "proclaimed", "proclamation"),
      setOf("vision", "visions"),
      setOf("dream", "dreams", "dreamed", "dreamer"),
      setOf("send", "sent"),
      // --- Character descriptions ---
      setOf("humble", "humbled", "humility"),
      setOf("proud", "pride", "prideful"),
      setOf("patient", "patience", "patiently"),
      setOf("gentle", "gentleness", "meek", "meekness"),
      setOf("kind", "kindness"),
      setOf("generous", "generosity"),
      setOf("jealous", "jealousy", "envy", "envious"),
      setOf("greedy", "greed", "covet", "covetous"),
      setOf("wicked", "wickedness"),
      setOf("devout", "devoted", "devotion"),
      setOf("corrupt", "corrupted", "corruption"),
      setOf("godly", "godliness", "ungodly"),
      // --- Objects and places ---
      setOf("idol", "idols", "idolatry", "idolatrous"),
      setOf("scroll", "scrolls"),
      setOf("law", "laws", "lawful", "lawless", "lawlessness"),
      setOf("statute", "statutes", "ordinance", "ordinances"),
      setOf("psalm", "psalms"),
      setOf("hymn", "hymns", "song", "songs", "sing", "singing", "sang"),
      setOf("crown", "crowns", "crowned"),
      setOf("throne", "thrones"),
      setOf("sword", "swords"),
      setOf("shield", "shields"),
      setOf("armor", "armour"),
      setOf("trumpet", "trumpets", "shofar"),
      setOf("seal", "seals", "sealed"),
      setOf("tribe", "tribes"),
      setOf("nation", "nations", "gentile", "gentiles"),
      setOf("wilderness", "desert"),
      setOf("mountain", "mountains", "mount"),
      setOf("river", "rivers", "stream", "streams"),
      setOf("stone", "stones", "rock", "rocks"),
      // --- Physical/metaphorical ---
      setOf("fire", "fires", "flame", "flames", "burning", "burned"),
      setOf("water", "waters"),
      setOf("light", "lights", "lamp", "lamps"),
      setOf("dark", "darkness", "darkened"),
      setOf("wind", "winds", "storm", "storms", "tempest"),
      setOf("rain", "rains", "rainbow"),
      setOf("cloud", "clouds"),
      setOf("dust", "ashes"),
      setOf("gold", "golden"),
      setOf("bread", "loaf", "loaves", "manna"),
      setOf("wine", "winepress"),
      setOf("tree", "trees"),
      setOf("branch", "branches"),
      // --- Emotional/spiritual states ---
      setOf("joy", "joyful", "joyous", "rejoice", "rejoiced", "rejoicing"),
      setOf("peace", "peaceful", "peacemaker"),
      setOf("hope", "hoped", "hopeful"),
      setOf("comfort", "comforted", "comforter", "comforting"),
      setOf("anxiety", "anxious", "worry", "worried"),
      setOf("sorrow", "sorrowful", "grief", "grieve", "grieved", "mourn", "mourned", "mourning"),
      setOf("anger", "angry", "angered", "indignation"),
      setOf("courage", "courageous", "bold", "boldness", "boldly"),
      setOf("doubt", "doubted", "doubting", "unbelief"),
      setOf("guilt", "guilty"),
      setOf("shame", "shameful", "ashamed"),
      setOf("forsake", "forsaken", "forsook"),
      setOf("weep", "wept", "weeping", "tears"),
      // --- Relationships ---
      setOf("father", "fathers"),
      setOf("mother", "mothers"),
      setOf("brother", "brothers", "brethren"),
      setOf("sister", "sisters"),
      setOf("bride", "bridegroom", "wedding", "marriage", "marry", "married"),
      setOf("shepherd", "shepherds"),
      setOf("husband", "husbands", "wife", "wives"),
      setOf("child", "children", "offspring"),
      setOf("widow", "widows", "orphan", "orphans", "fatherless"),
      setOf("neighbor", "neighbours"),
      setOf("friend", "friends", "friendship"),
      setOf("enemy", "enemies", "foe", "foes", "adversary"),
      setOf("stranger", "strangers", "foreigner", "foreigners", "sojourner"),
      // --- Warfare/conflict ---
      setOf("battle", "battles"),
      setOf("fight", "fighting", "fought"),
      setOf("war", "wars", "warfare"),
      setOf("army", "armies", "soldier", "soldiers", "warrior", "warriors"),
      setOf("capture", "captured"),
      setOf("bondage", "bind", "binding", "chain", "chains", "shackle"),
      setOf("free", "freed", "freedom", "liberty", "liberate", "liberated"),
      setOf("rebel", "rebelled", "rebellion", "rebellious", "revolt"),
      // --- Agricultural/pastoral ---
      setOf("harvest", "harvested", "harvesting"),
      setOf("sow", "sowed", "sower", "sowing", "seed", "seeds"),
      setOf("reap", "reaped", "reaper", "reaping"),
      setOf("vine", "vines", "vineyard", "vineyards"),
      setOf("wheat", "barley", "grain"),
      setOf("flock", "flocks", "herd", "herds"),
      setOf("sheep", "goat", "goats"),
      setOf("fruit", "fruits", "fruitful"),
      setOf("plow", "plowed", "plowing"),
      setOf("yoke", "yoked"),
      setOf("thresh", "threshing", "winnow", "winnowing"),
      setOf("weed", "weeds", "tare", "tares"),
      // --- Birth/nativity ---
      setOf("birth", "born", "nativity"),
      setOf("virgin", "virginity"),
      setOf("barren", "barrenness"),
      // --- Additional high-search-volume terms ---
      setOf("curse", "cursed", "cursing"),
      setOf("vow", "vows", "vowed"),
      setOf("swear", "swore", "sworn", "oath", "oaths"),
      setOf("deceive", "deceived", "deception", "deceit", "deceitful"),
      setOf("betray", "betrayed", "betrayal", "betrayer"),
      setOf("deny", "denied", "denial"),
      setOf("restore", "restored", "restoration"),
      setOf("renew", "renewed", "renewal"),
      setOf("transform", "transformed", "transformation"),
      setOf("convert", "converted", "conversion"),
      setOf("adultery", "adulterer", "adulterous", "fornication"),
      setOf("lust", "lusted", "lustful"),
      setOf("drunk", "drunken", "drunkenness"),
      setOf("miracle", "miracles", "miraculous", "wonder", "wonders")
    )
    val map = mutableMapOf<String, Set<String>>()
    for (group in groups) {
      for (word in group) {
        map[word] = group - word
      }
    }
    map
  }



  // --- Common misspelling correction (English) ---

  private val corrections = mapOf(
    "resurection" to "resurrection", "ressurection" to "resurrection",
    "resurrecton" to "resurrection", "ressurrection" to "resurrection",
    "resuraction" to "resurrection", "ressurrecton" to "resurrection",
    "crusifixion" to "crucifixion", "cruxifixion" to "crucifixion",
    "crucifiction" to "crucifixion", "crucifixtion" to "crucifixion",
    "crusifiction" to "crucifixion",
    "transfiguation" to "transfiguration", "transfiguraton" to "transfiguration",
    "transfirguration" to "transfiguration",
    "baptizm" to "baptism", "babtism" to "baptism", "babtize" to "baptize",
    "pharasees" to "pharisees", "pharisies" to "pharisees", "pharasies" to "pharisees",
    "saducees" to "sadducees", "saduces" to "sadducees", "saducies" to "sadducees",
    "deciples" to "disciples", "diciples" to "disciples", "disiples" to "disciples",
    "apostels" to "apostles", "appostles" to "apostles",
    "prophesy" to "prophecy", "profesy" to "prophecy", "profecy" to "prophecy",
    "revalation" to "revelation", "revelaton" to "revelation", "revelatoin" to "revelation",
    "deuteranomy" to "deuteronomy", "deuteronmy" to "deuteronomy", "dueteronomy" to "deuteronomy",
    "levitikus" to "leviticus",
    "genisis" to "genesis", "gensis" to "genesis", "geneses" to "genesis",
    "exodous" to "exodus", "exodis" to "exodus",
    "psams" to "psalms", "pslams" to "psalms", "salms" to "psalms",
    "isaih" to "isaiah", "issaiah" to "isaiah", "isiaah" to "isaiah",
    "jerimiah" to "jeremiah", "jeramiah" to "jeremiah", "jerimah" to "jeremiah",
    "ezekial" to "ezekiel", "ezekeil" to "ezekiel",
    "mathew" to "matthew", "mathhew" to "matthew", "mattew" to "matthew",
    "galations" to "galatians", "galatins" to "galatians",
    "phillipians" to "philippians", "philippans" to "philippians", "philipians" to "philippians",
    "collossians" to "colossians", "colosians" to "colossians",
    "thessalonions" to "thessalonians", "thesalonians" to "thessalonians",
    "armagedon" to "armageddon",
    "pentatuch" to "pentateuch", "pentatuech" to "pentateuch",
    "abrahm" to "abraham", "abrahma" to "abraham",
    "soloman" to "solomon", "solomen" to "solomon",
    "jeruslem" to "jerusalem", "jeruselam" to "jerusalem", "jeruselum" to "jerusalem",
    "bethleham" to "bethlehem", "bethelhem" to "bethlehem",
    "nazarath" to "nazareth",
    "saten" to "satan", "saton" to "satan", "saitan" to "satan",
    "ascention" to "ascension", "assension" to "ascension",
    "repentence" to "repentance",
    "forgivness" to "forgiveness", "forgivenes" to "forgiveness",
    "rightousness" to "righteousness", "rightouness" to "righteousness",
    "sanctifcation" to "sanctification",
    "justifcation" to "justification",
    "reconcilation" to "reconciliation", "reconcliation" to "reconciliation",
    "resurect" to "resurrect", "ressurect" to "resurrect",
    "propitaition" to "propitiation", "propitation" to "propitiation",
    "abomanation" to "abomination", "abomation" to "abomination",
    "sacrfice" to "sacrifice", "sacrifce" to "sacrifice",
    "tabernacal" to "tabernacle", "tabernackle" to "tabernacle",
    "convenant" to "covenant", "covenent" to "covenant",
    "testamant" to "testament", "testement" to "testament"
  )

  private fun correctToken(token: String): String =
    corrections[token] ?: token

  fun correctQuery(query: String): String =
    query.split(Regex("\\s+")).joinToString(" ") { correctToken(it.lowercase()) }

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

  private val esStopwords = setOf("el","la","los","las","un","una","unos","unas","de","del","al","en","con","por","para","se","su","sus","que","y","o","es","son","fue","era","como","mas","pero","ni","si","no","lo","le","les")
  private val frStopwords = setOf("le","la","les","un","une","des","de","du","au","aux","en","dans","par","pour","sur","avec","ce","ces","se","son","sa","ses","que","qui","et","ou","est","sont","a","pas","ne","mais","si","non","je","tu","il","nous","vous","ils")
  private val deStopwords = setOf("der","die","das","den","dem","des","ein","eine","einen","einem","einer","und","oder","aber","denn","von","zu","auf","in","an","mit","fur","um","nach","aus","bei","als","wie","ist","sind","war","hat","nicht","kein","keine")
  private val itStopwords = setOf("il","lo","la","i","gli","le","un","uno","una","di","del","della","dei","degli","delle","da","dal","in","nel","nella","con","per","su","sul","tra","fra","che","e","o","ma","non","si","se","come","anche","piu")
  private val ptStopwords = setOf("o","a","os","as","um","uma","uns","umas","de","do","da","dos","das","em","no","na","nos","nas","por","pelo","pela","com","para","se","que","e","ou","mas","nao","sim","como","foi","era","sao")
  private val ruStopwords = setOf("и","в","на","с","по","из","от","за","к","о","не","но","а","что","как","это","он","она","они","его","её","их","у","бы","же","ли","для","до","при","так","все","был","были","было")
  private val arStopwords = setOf("في","من","الى","على","عن","مع","هذا","هذه","ذلك","التي","الذي","هو","هي","هم","كان","كانت","لا","ما","ان","قد","بعد","قبل","بين","كل","ثم","او")
  private val hiStopwords = setOf("का","की","के","में","से","को","पर","ने","है","हैं","था","थे","और","या","लेकिन","यह","वह","इस","उस","एक","नहीं","कि","जो","भी","तक","साथ")
  private val jaStopwords = setOf("の","に","は","を","た","が","で","て","と","し","れ","さ","ある","いる","も","する","から","な","こと","として","い","や","れる","など","なっ","ない","この","ため","その")
  private val koStopwords = setOf("의","에","를","은","는","이","가","와","과","로","에서","으로","하","된","한","할","하는","것","그","이","있","없","들","수","등","또","및")
  private val zhStopwords = setOf("的","了","在","是","我","有","和","就","不","人","都","一","这","中","大","为","上","个","他","们","到","来","也","说","那","你","要","会","对","把")

  private fun significantTokens(tokens: List<String>, lang: String): List<String> {
    val stops = when (lang) {
      "en" -> enStopwords; "es" -> esStopwords; "fr" -> frStopwords
      "de" -> deStopwords; "it" -> itStopwords; "pt" -> ptStopwords
      "ru" -> ruStopwords; "ar" -> arStopwords; "hi" -> hiStopwords
      "ja" -> jaStopwords; "ko" -> koStopwords
      "zh-hans","zh-hant" -> zhStopwords
      else -> enStopwords
    }
    return tokens.filter { it.length >= 2 && it !in stops }
  }

  // --- Stemmer ---

  private fun stemLite(w: String, lang: String): String {
    val s = w.lowercase()
    if (s.length < 4) return s
    if (lang == "en") return when {
      s.endsWith("ness") && s.length > 6 -> s.dropLast(4)
      s.endsWith("ment") && s.length > 6 -> s.dropLast(4)
      s.endsWith("ation") && s.length > 7 -> s.dropLast(5)
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
    // Non-English: truncate suffix to catch inflectional endings across languages
    if (s.length >= 7) return s.dropLast(2)
    if (s.length >= 5) return s.dropLast(1)
    return s
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
    // Strong proximity dominance. Words clustered within ~40 chars are a powerful
    // "same phrase" signal that easily overpowers verses where the same words happen
    // to appear scattered across a long passage (Jubilees has multi-paragraph bullets
    // that otherwise win on raw token-overlap).
    val coverageBoost = if (positions.size >= qTokens.size) 1.5f else 1f
    val base = when {
      span < 40 -> 4000
      span < 80 -> 2200
      span < 160 -> 1000
      span < 320 -> 400
      span < 800 -> 100
      else -> 20
    }
    return (base * coverageBoost).toInt()
  }

  // Longest Common Subsequence of two word lists. Standard O(n*m) DP, O(m) space.
  // Used to reward verses where the query words appear in the same relative order
  // as the user typed them, even if separated by extra words.
  private fun lcsLength(a: List<String>, b: List<String>): Int {
    val n = a.size; val m = b.size
    if (n == 0 || m == 0) return 0
    val dp = IntArray(m + 1)
    for (i in 1..n) {
      var prev = 0
      for (j in 1..m) {
        val temp = dp[j]
        dp[j] = if (a[i - 1] == b[j - 1]) prev + 1 else maxOf(dp[j], dp[j - 1])
        prev = temp
      }
    }
    return dp[m]
  }

  // --- Verse-level search ---

  private fun searchVerses(q: String, limit: Int): List<SearchHit> {
    if (verseDocs.isEmpty()) return emptyList()
    val lang = resolvedLang
    val qWords = q.split(' ').filter { it.isNotEmpty() }
    val allTokens = qWords.filter { it.length >= 2 }
    val sigTokens = significantTokens(allTokens, lang)
    if (allTokens.isEmpty()) return emptyList()

    val isPhraseQuery = allTokens.size >= 2

    val candidates: Set<Int> = buildCandidateSet(allTokens, sigTokens, lang, isPhraseQuery)
    if (candidates.isEmpty()) return emptyList()

    val verseHits = mutableListOf<Pair<Int, Int>>()
    for (idx in candidates) {
      val vd = verseDocs[idx]
      val score = scoreVerse(vd.text, q, allTokens, sigTokens, lang, qWords, vd.words)
      if (score <= 0) continue
      val withCol = score + when (vd.collection) {
        "old_testament", "new_testament" -> 100
        "deuterocanonical" -> 30
        else -> 0
      }
      verseHits += idx to withCol
    }

    val grouped = verseHits
      .groupBy { verseDocs[it.first].let { vd -> Triple(vd.collection, vd.bookId, vd.storyId) } }
      .map { (_, hits) -> hits.maxByOrNull { it.second }!! }
      .sortedByDescending { it.second }
      .take(limit)

    return grouped.map { (idx, score) ->
      val vd = verseDocs[idx]
      val ref = "${vd.bookTitle} ${vd.chapter}:${vd.verse}${if (vd.verseEnd != null) "-${vd.verseEnd}" else ""}"
      val snippet = cleanBulletForDisplay(vd.rawText)
      SearchHit(ref, snippet, vd.collection, vd.bookId, vd.storyId, score, SearchHitType.STORY, vd.verse, vd.verseEnd)
    }
  }

  private fun buildCandidateSet(allTokens: List<String>, sigTokens: List<String>, lang: String, isPhraseQuery: Boolean): Set<Int> {
    if (sigTokens.isEmpty() && allTokens.isEmpty()) return emptySet()

    fun lookupToken(tok: String): Set<Int> {
      val out = mutableSetOf<Int>()
      verseInvertedIndex[tok]?.let { out.addAll(it) }
      if (lang == "en") {
        val syns = synonymLookup[tok]
        if (syns != null) {
          for (syn in syns) verseInvertedIndex[syn]?.let { out.addAll(it) }
        }
      }
      val stem = stemLite(tok, lang)
      if (stem != tok && stem.length >= 3) prefixCandidates(stem, out)
      return out
    }

    // Pick the intersection token set: prefer significant tokens, but fall back to
    // all tokens (including stopwords) when there are fewer than 2 significant ones.
    // This catches queries like "I am that I am" where stripping stopwords leaves
    // only one token and a union match would return thousands of irrelevant verses.
    val intersectTokens = when {
      sigTokens.size >= 2 -> sigTokens
      allTokens.size >= 2 -> allTokens.distinct()
      else -> emptyList()
    }

    if (isPhraseQuery && intersectTokens.size >= 2) {
      val perTokenSets = intersectTokens.map { lookupToken(it) }.filter { it.isNotEmpty() }
      if (perTokenSets.isEmpty()) return emptySet()
      val sortedBySize = perTokenSets.sortedBy { it.size }
      var intersected = sortedBySize[0].toMutableSet()
      for (i in 1 until sortedBySize.size) {
        intersected.retainAll(sortedBySize[i])
        if (intersected.isEmpty()) break
      }
      if (intersected.isNotEmpty()) return intersected
      val twoWayHits = mutableMapOf<Int, Int>()
      for (set in perTokenSets) for (id in set) twoWayHits[id] = (twoWayHits[id] ?: 0) + 1
      val needed = minOf(2, perTokenSets.size)
      val partial = twoWayHits.filter { it.value >= needed }.keys
      if (partial.isNotEmpty()) return partial
      return perTokenSets.flatten().toSet()
    }

    val out = mutableSetOf<Int>()
    val tokensToUse = if (sigTokens.isNotEmpty()) sigTokens else allTokens
    for (tok in tokensToUse) out.addAll(lookupToken(tok))
    return out
  }

  private fun scoreVerse(text: String, q: String, allTokens: List<String>, sigTokens: List<String>, lang: String, qWords: List<String>, textWords: List<String>): Int {
    var score = 0

    if (q.length >= 5 && indexInfix(text, q) >= 0) score += 5000

    if (allTokens.size >= 3) {
      for (i in 0..(allTokens.size - 3)) {
        val tri = "${allTokens[i]} ${allTokens[i+1]} ${allTokens[i+2]}"
        if (indexInfix(text, tri) >= 0) score += 600
      }
    }
    if (allTokens.size >= 2) {
      for (i in 0..(allTokens.size - 2)) {
        val bi = "${allTokens[i]} ${allTokens[i+1]}"
        if (indexInfix(text, bi) >= 0) score += 180
      }
    }

    // Bible.com approach: score EVERY token (including stopwords), not just significant ones.
    // A verse matching 4/5 query words always outranks one matching 2/5.
    var matchedCount = 0
    val matchedSet = HashSet<String>(allTokens.size * 2)
    for (tok in allTokens) {
      val pts = scoreTokenFull(text, tok, lang)
      if (pts > 0) { score += pts; matchedCount++; matchedSet += tok }
    }

    if (allTokens.isNotEmpty()) {
      val frac = matchedCount.toFloat() / allTokens.size
      score += (frac * frac * 2000).toInt()
    }

    if (sigTokens.isNotEmpty() && sigTokens.all { it in matchedSet }) score += 300
    if (matchedCount >= 2) score += matchedCount * matchedCount * 50

    // Fuzzy phrase: catches "I am that I am" → "I am who I am" (one-word substitution).
    // Slides query words across text words and rewards near-perfect alignments.
    score += fuzzyPhraseScore(qWords, textWords)

    // Ordered subsequence: rewards verses where query words appear in the same order
    // they were typed, even with extra words between. Discriminates between the right
    // verse ("Do not judge..." for "judge not lest you be judged") and verses that
    // happen to contain the same words scattered in a different order. Also tries
    // the reversed query so KJV "judge not" still scores against NIV "do not judge".
    if (qWords.size >= 2 && textWords.size >= 2) {
      val lcsFwd = lcsLength(qWords, textWords)
      val lcsRev = lcsLength(qWords.asReversed(), textWords)
      val lcs = maxOf(lcsFwd, lcsRev)
      if (lcs >= 2) {
        val ratio = lcs.toFloat() / qWords.size
        // Forward order preferred: full weight when forward matches the longer LCS,
        // 0.7x when only the reverse does (catches phrase-order paraphrases).
        val weight = if (lcsFwd >= lcsRev) 1800f else 1260f
        score += (ratio * ratio * weight).toInt()
      }
    }

    score += proximityBonus(text, allTokens)

    // Length penalty: long bullets (Jubilees, Enoch sometimes condense entire
    // chapters into one paragraph) trivially "match" most queries because the
    // words are all there somewhere. Penalize so a focused 12-word bullet beats
    // a 400-word bullet with the same words scattered.
    val wordCount = textWords.size
    if (wordCount > 30) {
      val excess = (wordCount - 30).coerceAtMost(300)
      score -= excess * 8
    }

    return score.coerceAtLeast(0)
  }

  // Look for the query as a near-consecutive sequence in the text, allowing up to
  // a couple of word substitutions. Catches paraphrases like "I am that I am" vs
  // "I am who I am" that token-set scoring alone misses.
  private fun fuzzyPhraseScore(queryWords: List<String>, textWords: List<String>): Int {
    val n = queryWords.size
    if (n < 3 || textWords.size < n) return 0
    var bestMatches = 0
    val end = textWords.size - n
    for (start in 0..end) {
      var matches = 0
      for (i in 0 until n) {
        if (textWords[start + i] == queryWords[i]) matches++
      }
      if (matches > bestMatches) {
        bestMatches = matches
        if (bestMatches == n) break
      }
    }
    val diff = n - bestMatches
    return when {
      diff == 0 -> 2500            // perfect alignment (already partly captured by indexInfix)
      diff == 1 -> 2000            // one-word swap, like "that" vs "who"
      diff == 2 && n >= 5 -> 800   // two swaps in a longer phrase
      else -> 0
    }
  }

  private fun scoreTokenFull(text: String, tok: String, lang: String): Int {
    if (indexWordBoundary(text, tok) >= 0) return 100 + countOccurrences(text, tok) * 20
    if (indexWordPrefix(text, tok) >= 0) return 50 + countPrefixOccurrences(text, tok) * 10
    if (lang == "en") {
      val syns = synonymLookup[tok]
      if (syns != null) for (syn in syns) {
        if (indexWordBoundary(text, syn) >= 0) return 80 + countOccurrences(text, syn) * 15
        if (indexWordPrefix(text, syn) >= 0) return 40 + countPrefixOccurrences(text, syn) * 8
      }
    }
    val stem = stemLite(tok, lang)
    if (stem != tok && stem.length >= 3 && indexWordPrefix(text, stem) >= 0) return 60 + countPrefixOccurrences(text, stem) * 12
    if (indexInfix(text, tok) >= 0) return 8
    if (tok.length >= 4 && fuzzyContains(text, tok)) return 25
    return 0
  }

  private fun countOccurrences(text: String, token: String): Int {
    var count = 0
    var idx = indexWordBoundary(text, token, 0)
    while (idx >= 0) {
      count++
      if (count >= 10) return count
      val nextFrom = idx + token.length
      if (nextFrom >= text.length) return count
      idx = indexWordBoundary(text, token, nextFrom)
    }
    return count
  }

  private fun countPrefixOccurrences(text: String, prefix: String): Int {
    var count = 0
    var idx = indexWordPrefix(text, prefix, 0)
    while (idx >= 0) {
      count++
      if (count >= 10) return count
      val nextFrom = idx + prefix.length
      if (nextFrom >= text.length) return count
      idx = indexWordPrefix(text, prefix, nextFrom)
    }
    return count
  }

  private fun cleanBulletForDisplay(raw: String): String {
    return raw
      .replace("[J]", "").replace("[/J]", "")
      .replace(Regex("""\s*\(\d+:\d+(?:\s*-\s*\d+)?\)\s*\.?\s*$"""), "")
      .trim()
  }

  private fun mergeStoryAndVerseHits(storyHits: List<SearchHit>, verseHits: List<SearchHit>): List<SearchHit> {
    val verseByKey = verseHits.associateBy { Triple(it.collection, it.bookId, it.storyId) }
    val merged = mutableListOf<SearchHit>()
    val consumedKeys = mutableSetOf<Triple<String, String, String>>()

    for (sh in storyHits) {
      val key = Triple(sh.collection, sh.bookId, sh.storyId)
      val vh = verseByKey[key]
      if (vh != null) {
        val combined = maxOf(sh.score, vh.score) + minOf(sh.score, vh.score) / 4
        merged += vh.copy(score = combined)
        consumedKeys += key
      } else {
        merged += sh
      }
    }

    for ((key, vh) in verseByKey) {
      if (key !in consumedKeys) merged += vh
    }

    return merged
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

  private data class ExplicitRef(val family: String, val number: Int?, val chapter: Int, val verse: Int? = null, val verseEnd: Int? = null)

  private fun parseChapterAndVerse(tok: String): Triple<Int?, Int?, Int?> {
    val rangeM = Regex("^(\\d+):(\\d+)\\s*-\\s*(\\d+)$").matchEntire(tok)
    if (rangeM != null) return Triple(rangeM.groupValues[1].toIntOrNull(), rangeM.groupValues[2].toIntOrNull(), rangeM.groupValues[3].toIntOrNull())
    val cvM = Regex("^(\\d+):(\\d+)$").matchEntire(tok)
    if (cvM != null) return Triple(cvM.groupValues[1].toIntOrNull(), cvM.groupValues[2].toIntOrNull(), null)
    val cM = Regex("^(\\d+)$").matchEntire(tok)
    if (cM != null) return Triple(cM.groupValues[1].toIntOrNull(), null, null)
    return Triple(null, null, null)
  }

  private fun parseExplicitRef(q: String): ExplicitRef? {
    val tokens = q.replace("_"," ").replace("-"," ").replace("."," ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    for (tok in tokens) {
      val m = Regex("^(\\d+|i{1,3}|first|second|third)?([a-z]+?)(\\d+)(?::(\\d+))?$").matchEntire(tok.lowercase())
      if (m != null) {
        val numRaw = m.groupValues[1].ifBlank { null }
        val famRaw = m.groupValues[2]; val chapter = m.groupValues[3].toIntOrNull() ?: continue
        val verse = m.groupValues[4].toIntOrNull()
        val num = numRaw?.let { parseLeadingNumber(it) }
        val (_, fam) = splitNumberedBook("${numRaw ?: ""} $famRaw")
        return ExplicitRef(fam, num, chapter, verse)
      }
    }
    run {
      val (bookPart, chapTok) = splitBookAndChapter(tokens)
      if (bookPart.isNotEmpty() && chapTok != null) {
        val (num, fam) = splitNumberedBook(bookPart.joinToString(" "))
        if (fam.isNotBlank()) {
          val (ch, v, vEnd) = parseChapterAndVerse(chapTok)
          if (ch != null) return ExplicitRef(fam, num, ch, v, vEnd)
        }
      }
    }
    for (i in tokens.indices) {
      val n = parseLeadingNumber(tokens[i])
      if (n != null) {
        val tail = tokens.drop(i + 1); val (bookPart, chapTok) = splitBookAndChapter(tail)
        if (bookPart.isEmpty() || chapTok == null) continue
        val (_, fam) = splitNumberedBook(bookPart.joinToString(" ")); if (fam.isBlank()) continue
        val (ch, v, vEnd) = parseChapterAndVerse(chapTok)
        if (ch != null) return ExplicitRef(fam, n, ch, v, vEnd)
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
      val title = if (ref.verse != null) {
        "$bookTitle ${ref.chapter}:${ref.verse}${if (ref.verseEnd != null) "-${ref.verseEnd}" else ""}"
      } else {
        "$bookTitle: ${d.title}"
      }
      val snippet = if (ref.verse != null) {
        verseDocs.firstOrNull { it.collection == col && it.bookId == bookId && it.chapter == ref.chapter && it.verse == ref.verse }
          ?.let { cleanBulletForDisplay(it.rawText) }
          ?: "${chapterWord()} ${ref.chapter} → ${d.refsJoined}"
      } else {
        "${chapterWord()} ${ref.chapter} → ${d.refsJoined}"
      }
      out += SearchHit(title, snippet, col, bookId, storyId, 10_000, SearchHitType.STORY, ref.verse, ref.verseEnd)
    }
    return out.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title.length })
  }

  private fun resolveBooks(family: String, number: Int?): List<Triple<String, String, String>> {
    val fam = family.lowercase(); val entries = bookLookup.entries.filter { (k, _) -> k.first.startsWith(fam) }
    return if (number != null) { val exact = entries.filter { (k, _) -> k.second == number }.flatMap { it.value }; exact.ifEmpty { entries.filter { (k, _) -> k.second == null }.flatMap { it.value } } }
    else entries.filter { (k, _) -> k.second == null }.flatMap { it.value }
  }

  // --- Canonical result boosting (surgical, per-event) ---

  private val canonicalHits: Map<String, List<String>> = run {
    val combined = mutableMapOf<String, List<String>>()
    fun merge(map: Map<String, List<String>>) {
      for ((key, ids) in map) {
        val existing = combined[key]
        combined[key] = if (existing != null) (existing + ids).distinct() else ids
      }
    }
    merge(buildTierAPins())
    merge(buildTierALocalizedPins())
    merge(buildTierBPins1())
    merge(buildTierBPins2())
    merge(buildTierBPins3())
    merge(buildTierBPins4())
    combined
  }

  private fun MutableMap<String, List<String>>.pin(stories: List<String>, vararg terms: String) {
    for (raw in terms) {
      val t = normalize(raw)
      if (t.isBlank()) continue
      val existing = get(t)
      put(t, if (existing != null) (existing + stories).distinct() else stories)
    }
  }

  private fun buildTierAPins(): Map<String, List<String>> = buildMap {
    // ── NT Gospel events ──
    pin(listOf("matthew-28","mark-16","luke-24","john-20"), "resurrection","risen","resurrect","resurrected")
    pin(listOf("matthew-27","mark-15","luke-23","john-19"), "crucifixion","crucified","crucify","cross","calvary","golgotha")
    pin(listOf("matthew-1","matthew-2","luke-2"), "nativity","birth","magi","wisemen")
    pin(listOf("matthew-3","mark-1","luke-3"), "baptism","baptize","baptized")
    pin(listOf("matthew-4","luke-4"), "temptation","tempted","fast","fasted","fasting")
    pin(listOf("matthew-5","matthew-6","matthew-7"), "sermon","beatitude","beatitudes")
    pin(listOf("matthew-17","mark-9"), "transfiguration","transfigured")
    pin(listOf("matthew-14","mark-6","luke-9","john-6"), "loaves","5000","feeding")
    pin(listOf("matthew-21","mark-11","luke-19","john-12"), "triumphal","hosanna","palm")
    pin(listOf("matthew-26","mark-14","luke-22","john-13"), "supper")
    pin(listOf("matthew-26","mark-14","luke-22"), "gethsemane")
    pin(listOf("matthew-26","mark-14","luke-22","john-18"), "betray","betrayed","betrayal")
    pin(listOf("matthew-13"), "parable","parables","sower")
    pin(listOf("matthew-25"), "virgins","talents")

    pin(listOf("matthew-14","john-6"), "water","walking")
    pin(listOf("matthew-8","mark-4","luke-8"), "storm","calmed","calming")
    pin(listOf("matthew-21","mark-11","john-2"), "cleansing","moneychangers")
    pin(listOf("luke-19"), "zacchaeus")
    pin(listOf("john-20"), "thomas","doubting")
    pin(listOf("acts-7"), "stephen","stoning","stoned")

    // ── NT Acts / Epistles ──
    pin(listOf("acts-1"), "ascension","ascend","ascended")
    pin(listOf("acts-2"), "pentecost","tongues")
    pin(listOf("acts-9"), "damascus","conversion")
    pin(listOf("acts-27"), "shipwreck","shipwrecked")
    pin(listOf("1-corinthians-15"), "resurrection")
    pin(listOf("ephesians-6"), "armor","armour")
    pin(listOf("revelation-12"), "dragon")
    pin(listOf("revelation-6"), "horsemen")

    // ── Gospels: specific teachings / miracles ──
    pin(listOf("luke-10"), "samaritan")
    pin(listOf("luke-15"), "prodigal")
    pin(listOf("john-2"), "cana","wedding")
    pin(listOf("john-3"), "nicodemus")
    pin(listOf("john-11"), "lazarus")
    pin(listOf("john-15"), "vine","abide")

    // ── OT: Genesis ──
    pin(listOf("genesis-1","genesis-2"), "creation","created","create")
    pin(listOf("genesis-3"), "fall","serpent","forbidden","eden","original")
    pin(listOf("genesis-4"), "cain","abel")
    pin(listOf("genesis-6","genesis-7","genesis-8"), "flood","noah","ark","deluge")
    pin(listOf("genesis-9"), "rainbow")
    pin(listOf("genesis-11"), "babel","tower")
    pin(listOf("genesis-12"), "abram","abraham")
    pin(listOf("genesis-17"), "circumcision","circumcised")
    pin(listOf("genesis-19"), "sodom","gomorrah")
    pin(listOf("genesis-22"), "binding","isaac")
    pin(listOf("genesis-28"), "ladder","stairway","jacob")
    pin(listOf("genesis-32"), "wrestle","wrestled","peniel")
    pin(listOf("genesis-37"), "joseph","coat")
    pin(listOf("genesis-40","genesis-41"), "pharaoh","dream","dreams")
    pin(listOf("genesis-41"), "famine")

    // ── OT: Exodus ──
    pin(listOf("exodus-3"), "burning","bush","moses")
    pin(listOf("exodus-7","exodus-8","exodus-9","exodus-10","exodus-11"), "plague","plagues")
    pin(listOf("exodus-12"), "passover","lamb")
    pin(listOf("exodus-14"), "part","parted","parting","split","divided","sea","red","exodus")
    pin(listOf("exodus-20","deuteronomy-5"), "commandment","commandments","decalogue")
    pin(listOf("exodus-32"), "golden","calf")
    pin(listOf("exodus-16"), "manna")
    pin(listOf("exodus-25","exodus-26"), "tabernacle")

    // ── OT: Wilderness / Conquest ──
    pin(listOf("exodus-17","numbers-20"), "rock","water")
    pin(listOf("numbers-13","numbers-14"), "spies","caleb")
    pin(listOf("numbers-21"), "bronze","serpent")
    pin(listOf("numbers-22","numbers-23","numbers-24"), "balaam","donkey")
    pin(listOf("joshua-3"), "jordan","crossing")
    pin(listOf("joshua-6"), "jericho","walls")
    pin(listOf("joshua-10"), "sun")

    // ── OT: Judges / Ruth ──
    pin(listOf("judges-4","judges-5"), "deborah","barak","sisera")
    pin(listOf("judges-6","judges-7"), "gideon","fleece")
    pin(listOf("judges-13","judges-14","judges-15","judges-16"), "samson")
    pin(listOf("judges-16"), "delilah")
    pin(listOf("ruth-1","ruth-2","ruth-3","ruth-4"), "ruth","boaz")

    // ── OT: Monarchy ──
    pin(listOf("1-samuel-1"), "hannah")
    pin(listOf("1-samuel-3"), "samuel")
    pin(listOf("1-samuel-16"), "david","anointed")
    pin(listOf("1-samuel-17"), "goliath","giant","sling")
    pin(listOf("1-samuel-18","1-samuel-20"), "jonathan")
    pin(listOf("2-samuel-11","2-samuel-12"), "bathsheba","adultery")
    pin(listOf("2-samuel-5"), "kingdom")
    pin(listOf("1-kings-3"), "solomon","wisdom")
    pin(listOf("1-kings-6","1-kings-8"), "temple","dedication")
    pin(listOf("1-kings-10"), "sheba")
    pin(listOf("1-kings-18"), "carmel","baal","elijah")
    pin(listOf("2-kings-2"), "chariot","elisha","mantle")
    pin(listOf("2-kings-5"), "naaman","leprosy","leper")

    // ── OT: Exile / Prophets ──
    pin(listOf("daniel-3"), "furnace","shadrach","meshach","abednego")
    pin(listOf("daniel-5"), "handwriting","belshazzar")
    pin(listOf("daniel-6"), "lion","lions","den")
    pin(listOf("daniel-7"), "beast","beasts")
    pin(listOf("jonah-1","jonah-2"), "jonah","whale","swallowed","fish")
    pin(listOf("esther-4","esther-5","esther-7"), "esther","haman","mordecai","purim")
    pin(listOf("nehemiah-2","nehemiah-6"), "nehemiah","rebuild")
    pin(listOf("ezra-1","ezra-3"), "cyrus","return")
    pin(listOf("2-kings-24","2-kings-25"), "babylon","exile","captivity","nebuchadnezzar","babylonian")
    pin(listOf("isaiah-53"), "suffering","servant","pierced")
    pin(listOf("isaiah-7","isaiah-9"), "immanuel","virgin")
    pin(listOf("jeremiah-31"), "covenant")
    pin(listOf("ezekiel-37"), "bones","valley","dry")

    // ── OT: Psalms / Wisdom ──
    pin(listOf("psalms-23"), "shepherd")
    pin(listOf("psalms-22"), "forsaken")
    pin(listOf("psalms-51"), "cleanse")
    pin(listOf("psalms-91"), "refuge","shelter")
    pin(listOf("psalms-139"), "knit","womb","fearfully")
    pin(listOf("proverbs-31"), "noble","virtuous")
    pin(listOf("job-1","job-2"), "job","suffering")
    pin(listOf("job-38"), "whirlwind")
    pin(listOf("ecclesiastes-1"), "vanity","meaningless")

    // ── OT: Feasts / Law ──
    pin(listOf("leviticus-16"), "atonement","scapegoat","yom")
    pin(listOf("leviticus-23"), "feast","feasts","festival","festivals","sukkot","shavuot")
    pin(listOf("deuteronomy-6"), "shema")
    pin(listOf("deuteronomy-28"), "blessing","blessings","curse","curses")

    // ── Localized canonical triggers ──
    // After NFKD+accent-strip normalization, accented Latin → base form.
    // Store all Latin triggers accent-free. Non-Latin scripts stored as-is.

    // Resurrection (matt-28, mark-16, luke-24, john-20)
    pin(listOf("matthew-28","mark-16","luke-24","john-20"),
      "resurreccion", // es
      "auferstehung","auferstanden", // de
      "risurrezione","risorto", // it
      "ressurreicao", // pt
      "воскресение","воскрешение", // ru
      "القيامة", // ar
      "पुनरुत्थान", // hi
      "復活","复活", // zh
      "부활", // ko
      "復活" // ja
    )
    // Crucifixion (matt-27, mark-15, luke-23, john-19)
    pin(listOf("matthew-27","mark-15","luke-23","john-19"),
      "crucifixion", // es/fr (accent-stripped = same as en)
      "kreuzigung","gekreuzigt", // de
      "crocifissione","crocifisso", // it
      "crucificacao","crucificado", // pt
      "распятие","распят", // ru
      "الصلب","صلب", // ar
      "क्रूसीकरण","सूली", // hi
      "釘十字架","钉十字架", // zh
      "십자가","십자가형", // ko
      "十字架" // ja
    )
    // Nativity / Birth (matt-1, matt-2, luke-2)
    pin(listOf("matthew-1","matthew-2","luke-2"),
      "nacimiento","natividad", // es
      "nativite","naissance", // fr
      "geburt","weihnacht", // de
      "nascita","nativita", // it
      "nascimento","natividade", // pt
      "рождество","рождение", // ru
      "الميلاد","ميلاد", // ar
      "जन्म", // hi
      "聖誕","圣诞","誕生","诞生", // zh
      "탄생","성탄", // ko
      "降誕","誕生" // ja
    )
    // Baptism (matt-3, mark-1, luke-3)
    pin(listOf("matthew-3","mark-1","luke-3"),
      "bautismo","bautizo", // es
      "bapteme", // fr
      "taufe","getauft", // de
      "battesimo", // it
      "batismo", // pt
      "крещение", // ru
      "المعمودية","معمودية", // ar
      "बपतिस्मा", // hi
      "洗禮","洗礼", // zh
      "세례", // ko
      "バプテスマ" // ja
    )
    // Temptation (matt-4, luke-4)
    pin(listOf("matthew-4","luke-4"),
      "tentacion", // es
      "tentation", // fr
      "versuchung", // de
      "tentazione", // it
      "tentacao", // pt
      "искушение", // ru
      "التجربة","إغراء", // ar
      "प्रलोभन","परीक्षा", // hi
      "試探","试探", // zh
      "시험","유혹", // ko
      "誘惑" // ja
    )
    // Sermon on the Mount (matt-5/6/7)
    pin(listOf("matthew-5","matthew-6","matthew-7"),
      "bienaventuranzas","sermon", // es
      "beatitudes", // fr (accent-stripped matches en)
      "bergpredigt","seligpreisungen", // de
      "beatitudini", // it
      "bem-aventurancas","sermao", // pt
      "заповеди блаженства","нагорная проповедь", // ru
      "التطويبات","الموعظة", // ar
      "धन्य वचन","पहाड़ी उपदेश", // hi
      "登山寶訓","登山宝训","八福", // zh
      "산상수훈","팔복", // ko
      "山上の垂訓","八福" // ja
    )
    // Transfiguration (matt-17, mark-9)
    pin(listOf("matthew-17","mark-9"),
      "transfiguracion", // es
      "transfiguration", // fr/en same
      "verklarung", // de
      "trasfigurazione", // it
      "transfiguracao", // pt
      "преображение", // ru
      "التجلي", // ar
      "रूपान्तरण", // hi
      "變容","变容","登山變像","登山变像", // zh
      "변모","변형", // ko
      "変容" // ja
    )
    // Feeding 5000 (matt-14, mark-6, luke-9, john-6)
    pin(listOf("matthew-14","mark-6","luke-9","john-6"),
      "multiplicacion","panes","cinco mil", // es
      "pains","cinq mille", // fr
      "brotvermehrung","funftausend", // de
      "moltiplicazione","pani","cinquemila", // it
      "multiplicacao","paes","cinco mil", // pt
      "насыщение","пять тысяч","хлеба", // ru
      "خمسة آلاف","إطعام", // ar
      "पाँच हज़ार","रोटियाँ", // hi
      "五餅二魚","五饼二鱼","五千人", // zh
      "오병이어","오천명", // ko
      "五千人","パン" // ja
    )
    // Triumphal entry (matt-21, mark-11, luke-19, john-12)
    pin(listOf("matthew-21","mark-11","luke-19","john-12"),
      "entrada triunfal","ramos", // es
      "entree triomphale","rameaux", // fr
      "palmsonntag","einzug", // de
      "ingresso trionfale","domenica delle palme", // it
      "entrada triunfal","ramos", // pt
      "вход господень","вербное", // ru
      "أحد الشعانين","الدخول", // ar
      "विजयी प्रवेश", // hi
      "棕枝主日","荣耀进城", // zh
      "종려주일","예루살렘 입성", // ko
      "エルサレム入城" // ja
    )
    // Last Supper (matt-26, mark-14, luke-22, john-13)
    pin(listOf("matthew-26","mark-14","luke-22","john-13"),
      "ultima cena", // es
      "cene","derniere cene", // fr
      "abendmahl","letzte abendmahl", // de
      "ultima cena", // it (same as es)
      "ultima ceia","santa ceia", // pt
      "тайная вечеря", // ru
      "العشاء الأخير", // ar
      "अंतिम भोज", // hi
      "最後的晚餐","最后的晚餐", // zh
      "최후의 만찬", // ko
      "最後の晩餐" // ja
    )
    // Gethsemane (matt-26, mark-14, luke-22)
    pin(listOf("matthew-26","mark-14","luke-22"),
      "getsemani", // es/it
      "gethsemane", // fr/en same
      "gethsemane", // de same
      "getsemani", // pt
      "гефсимания", // ru
      "جثسيماني", // ar
      "गतसमनी", // hi
      "客西馬尼","客西马尼", // zh
      "겟세마네", // ko
      "ゲッセマネ" // ja
    )
    // Betrayal (matt-26, mark-14, luke-22, john-18)
    pin(listOf("matthew-26","mark-14","luke-22","john-18"),
      "traicion","judas", // es
      "trahison", // fr
      "verrat", // de
      "tradimento","giuda", // it
      "traicao", // pt
      "предательство","иуда", // ru
      "خيانة","يهوذا", // ar
      "विश्वासघात","यहूदा", // hi
      "猶大","犹大","背叛", // zh
      "유다","배신", // ko
      "ユダ","裏切り" // ja
    )
    // Parables (matt-13)
    pin(listOf("matthew-13"),
      "parabola","parabolas","sembrador", // es
      "parabole","paraboles","semeur", // fr
      "gleichnis","gleichnisse","samann", // de
      "parabola","parabole","seminatore", // it
      "parabola","parabolas","semeador", // pt
      "притча","притчи","сеятель", // ru
      "مثل","أمثال","الزارع", // ar
      "दृष्टान्त","बोने वाला", // hi
      "比喻","撒種","撒种", // zh
      "비유","씨 뿌리는 자", // ko
      "たとえ","種まき" // ja
    )
    // Walking on water (matt-14, john-6)
    pin(listOf("matthew-14","john-6"),
      "caminar sobre agua","camino sobre", // es
      "marcher sur eau", // fr
      "auf dem wasser", // de
      "camminare sulle acque", // it
      "andar sobre agua", // pt
      "хождение по воде","ходил по воде", // ru
      "المشي على الماء", // ar
      "पानी पर चलना", // hi
      "水上行走", // zh
      "물 위를 걸으심", // ko
      "水の上を歩く" // ja
    )
    // Calming the storm (matt-8, mark-4, luke-8)
    pin(listOf("matthew-8","mark-4","luke-8"),
      "calmar tempestad","tormenta", // es
      "tempete apaisee","tempete", // fr
      "stillung","sturm", // de
      "tempesta sedata","tempesta", // it
      "tempestade acalmada","tempestade", // pt
      "усмирение бури","буря", // ru
      "تهدئة العاصفة","عاصفة", // ar
      "तूफ़ान को शान्त करना", // hi
      "平靜風浪","平静风浪", // zh
      "풍랑을 잠잠케 하심", // ko
      "嵐を静める" // ja
    )
    // Ascension (acts-1)
    pin(listOf("acts-1"),
      "ascension", // es/fr/en same
      "himmelfahrt", // de
      "ascensione", // it
      "ascensao", // pt
      "вознесение", // ru
      "الصعود", // ar
      "स्वर्गारोहण", // hi
      "升天", // zh
      "승천", // ko
      "昇天" // ja
    )
    // Pentecost (acts-2)
    pin(listOf("acts-2"),
      "pentecostes", // es/pt
      "pentecote", // fr
      "pfingsten", // de
      "pentecoste", // it
      "пятидесятница", // ru
      "العنصرة","يوم الخمسين", // ar
      "पिन्तेकुस्त", // hi
      "五旬節","五旬节", // zh
      "오순절", // ko
      "ペンテコステ","五旬祭" // ja
    )
    // Damascus / Paul's conversion (acts-9)
    pin(listOf("acts-9"),
      "damasco","conversion","pablo","saulo", // es
      "damas","saul","paul", // fr
      "damaskus","bekehrung","paulus","saulus", // de
      "damasco","conversione","paolo","saulo", // it
      "damasco","conversao","paulo","saulo", // pt
      "дамаск","обращение","павел","савл", // ru
      "دمشق","شاول","بولس", // ar
      "दमिश्क","पौलुस","शाऊल", // hi
      "大馬士革","大马士革","保羅","保罗","掃羅","扫罗", // zh
      "다마스커스","바울","사울", // ko
      "ダマスコ","パウロ","サウロ" // ja
    )
    // Shipwreck (acts-27)
    pin(listOf("acts-27"),
      "naufragio", // es/it/pt
      "naufrage", // fr
      "schiffbruch", // de
      "кораблекрушение", // ru
      "غرق السفينة", // ar
      "जहाज़ का टूटना", // hi
      "船難","船难", // zh
      "난파","파선", // ko
      "難破" // ja
    )
    // Armor of God (eph-6)
    pin(listOf("ephesians-6"),
      "armadura", // es/pt
      "armure", // fr
      "waffenrustung","rustung", // de
      "armatura", // it
      "доспехи","всеоружие", // ru
      "سلاح الله","درع", // ar
      "परमेश्वर के हथियार", // hi
      "全副軍裝","全副军装", // zh
      "전신갑주","하나님의 갑옷", // ko
      "神の武具" // ja
    )
    // Good Samaritan (luke-10)
    pin(listOf("luke-10"),
      "buen samaritano","samaritano", // es
      "bon samaritain","samaritain", // fr
      "barmherziger samariter","samariter", // de
      "buon samaritano", // it
      "bom samaritano", // pt
      "добрый самарянин","самарянин", // ru
      "السامري الصالح","السامري", // ar
      "अच्छा सामरी","सामरी", // hi
      "好撒馬利亞人","好撒玛利亚人", // zh
      "선한 사마리아인","사마리아인", // ko
      "善きサマリア人","サマリア人" // ja
    )
    // Prodigal Son (luke-15)
    pin(listOf("luke-15"),
      "hijo prodigo", // es
      "fils prodigue", // fr
      "verlorener sohn","verlorene sohn", // de
      "figlio prodigo","figliol prodigo", // it
      "filho prodigo", // pt
      "блудный сын", // ru
      "الابن الضال", // ar
      "उड़ाऊ पुत्र", // hi
      "浪子","浪子回頭", // zh
      "탕자", // ko
      "放蕩息子","放蕩" // ja
    )
    // Wedding at Cana (john-2)
    pin(listOf("john-2"),
      "bodas de cana","cana", // es
      "noces de cana", // fr
      "hochzeit zu kana","hochzeit", // de
      "nozze di cana", // it
      "bodas de cana", // pt
      "брак в кане","кана", // ru
      "عرس قانا","قانا", // ar
      "काना का विवाह","काना", // hi
      "迦拿婚禮","迦拿婚礼","迦拿", // zh
      "가나 혼인잔치","가나", // ko
      "カナの婚礼","カナ" // ja
    )
    // Lazarus raised (john-11)
    pin(listOf("john-11"),
      "lazaro", // es/it/pt
      "lazare", // fr
      "lazarus", // de/en same
      "лазарь", // ru
      "لعازر", // ar
      "लाज़र", // hi
      "拉撒路", // zh
      "나사로", // ko
      "ラザロ" // ja
    )
    // Doubting Thomas (john-20)
    pin(listOf("john-20"),
      "tomas incredulo","tomas", // es
      "thomas incredule", // fr
      "unglaubiger thomas", // de
      "tommaso incredulo","tommaso", // it
      "tome incredulo","tome", // pt
      "фома неверующий","фома", // ru
      "توما","الشكاك", // ar
      "थोमा","अविश्वासी थोमा", // hi
      "多馬","多马","懷疑的多馬", // zh
      "도마","의심 많은 도마", // ko
      "トマス","疑い深いトマス" // ja
    )
    // Creation (genesis-1/2) - covered above
    // Fall / Eden (genesis-3)
    pin(listOf("genesis-3"),
      "caida","pecado original","eden", // es
      "chute","peche originel", // fr
      "sundenfall","paradies", // de
      "caduta","peccato originale", // it
      "queda","pecado original", // pt
      "грехопадение","рай","эдем", // ru
      "السقوط","عدن","الخطيئة الأصلية", // ar
      "पतन","अदन", // hi
      "墮落","堕落","伊甸","伊甸園","伊甸园", // zh
      "타락","에덴","원죄", // ko
      "堕落","エデン","エデンの園" // ja
    )
    // Cain and Abel (genesis-4)
    pin(listOf("genesis-4"),
      "cain","abel", // universal (accent-free)
      "каин","авель", // ru
      "قايين","هابيل", // ar
      "कैन","हाबिल", // hi
      "該隱","该隐","亞伯","亚伯", // zh
      "가인","아벨", // ko
      "カイン","アベル" // ja
    )
    // Flood / Noah - covered above
    // Rainbow (genesis-9)
    pin(listOf("genesis-9"),
      "arco iris","arcoiris", // es
      "arc-en-ciel", // fr
      "regenbogen", // de
      "arcobaleno", // it
      "arco-iris", // pt
      "радуга", // ru
      "قوس قزح", // ar
      "इंद्रधनुष", // hi
      "彩虹", // zh
      "무지개", // ko
      "虹" // ja
    )
    // Tower of Babel (genesis-11)
    pin(listOf("genesis-11"),
      "torre","babel", // universal
      "turm","turmbau", // de
      "вавилон","столпотворение", // ru
      "بابل","برج", // ar
      "बाबेल","मीनार", // hi
      "巴別","巴别","塔", // zh
      "바벨","탑", // ko
      "バベル" // ja
    )
    // Abraham's call (genesis-12)
    pin(listOf("genesis-12"),
      "llamado de abraham","abram", // es
      "appel d'abraham", // fr
      "berufung abrahams", // de
      "chiamata di abramo","abramo", // it
      "chamado de abraao","abraao", // pt
      "авраам","аврам", // ru
      "إبراهيم","أبرام", // ar
      "अब्राहम","अब्राम", // hi
      "亞伯拉罕","亚伯拉罕","亞伯蘭","亚伯兰", // zh
      "아브라함","아브람", // ko
      "アブラハム","アブラム" // ja
    )
    // Sodom and Gomorrah (genesis-19)
    pin(listOf("genesis-19"),
      "sodoma","gomorra", // universal
      "содом","гоморра", // ru
      "سدوم","عمورة", // ar
      "सदोम","अमोरा", // hi
      "所多瑪","所多玛","蛾摩拉", // zh
      "소돔","고모라", // ko
      "ソドム","ゴモラ" // ja
    )
    // Binding of Isaac (genesis-22)
    pin(listOf("genesis-22"),
      "sacrificio de isaac","aqedah", // es
      "sacrifice d'isaac","ligature", // fr
      "opferung isaaks","bindung", // de
      "sacrificio di isacco","legatura", // it
      "sacrificio de isaque", // pt
      "жертвоприношение исаака","акеда", // ru
      "ذبح إسحاق","إسحاق", // ar
      "इसहाक का बलिदान","इसहाक", // hi
      "以撒","獻以撒","献以撒", // zh
      "이삭","아케다", // ko
      "イサク","アケダー" // ja
    )
    // Jacob's ladder (genesis-28)
    pin(listOf("genesis-28"),
      "escalera","escala de jacob", // es
      "echelle de jacob", // fr
      "jakobsleiter","himmelsleiter", // de
      "scala di giacobbe","giacobbe", // it
      "escada de jaco", // pt
      "лестница иакова","иаков", // ru
      "سلم يعقوب","يعقوب", // ar
      "याकूब की सीढ़ी","याकूब", // hi
      "雅各的天梯","雅各","天梯", // zh
      "야곱의 사다리","야곱", // ko
      "ヤコブの梯子","ヤコブ" // ja
    )
    // Joseph's coat (genesis-37)
    pin(listOf("genesis-37"),
      "jose","tunica","capa de colores", // es
      "joseph","tunique", // fr
      "joseph","bunter rock", // de
      "giuseppe","tunica","veste", // it
      "jose","tunica", // pt
      "иосиф","разноцветная одежда", // ru
      "يوسف","رداء", // ar
      "यूसुफ़","चोगा", // hi
      "約瑟","约瑟","彩衣", // zh
      "요셉","채색옷", // ko
      "ヨセフ" // ja
    )
    // Burning bush (exodus-3)
    pin(listOf("exodus-3"),
      "zarza ardiente","zarza","moises", // es
      "buisson ardent","moise", // fr
      "brennender dornbusch","dornbusch","mose", // de
      "roveto ardente","roveto","mose", // it
      "sarca ardente","sarca","moises", // pt
      "неопалимая купина","моисей", // ru
      "العليقة المشتعلة","موسى", // ar
      "जलती झाड़ी","मूसा", // hi
      "燃燒的荊棘","燃烧的荆棘","摩西", // zh
      "불타는 떨기나무","모세", // ko
      "燃える柴","モーセ" // ja
    )
    // Plagues (exodus-7/8/9/10/11)
    pin(listOf("exodus-7","exodus-8","exodus-9","exodus-10","exodus-11"),
      "plagas","diez plagas", // es
      "plaies","dix plaies", // fr
      "plagen","zehn plagen", // de
      "piaghe","dieci piaghe", // it
      "pragas","dez pragas", // pt
      "казни","десять казней", // ru
      "الضربات","ضربات", // ar
      "विपत्तियाँ","दस विपत्तियाँ", // hi
      "十災","十灾","災禍","灾祸", // zh
      "재앙","열 가지 재앙", // ko
      "十の災い","災い" // ja
    )
    // Passover (exodus-12) - covered above
    // Red Sea (exodus-14) - covered above
    // Ten Commandments (exodus-20) - covered above
    // Golden Calf (exodus-32)
    pin(listOf("exodus-32"),
      "becerro de oro","becerro","ternero", // es
      "veau d'or", // fr
      "goldenes kalb", // de
      "vitello d'oro", // it
      "bezerro de ouro", // pt
      "золотой телец","телец", // ru
      "العجل الذهبي","عجل", // ar
      "सोने का बछड़ा", // hi
      "金牛犢","金牛犊", // zh
      "금송아지", // ko
      "金の子牛" // ja
    )
    // Manna (exodus-16)
    pin(listOf("exodus-16"),
      "mana", // es/pt/it
      "manne", // fr
      "манна", // ru
      "المن","منّ", // ar
      "मन्ना", // hi
      "嗎哪","吗哪", // zh
      "만나", // ko
      "マナ" // ja
    )
    // Balaam's donkey (numbers-22/23/24)
    pin(listOf("numbers-22","numbers-23","numbers-24"),
      "balaam","asna", // es
      "balaam","anesse", // fr
      "bileam","eselin", // de
      "balaam","asina", // it
      "balaao","jumenta", // pt
      "валаам","ослица", // ru
      "بلعام","أتان", // ar
      "बिलाम","गधी", // hi
      "巴蘭","巴兰","驢","驴", // zh
      "발람","나귀", // ko
      "バラム","ろば" // ja
    )
    // Jericho walls (joshua-6)
    pin(listOf("joshua-6"),
      "jerico","murallas", // es
      "jericho","murailles", // fr
      "jericho","mauern", // de
      "gerico","mura", // it
      "jerico","muralhas", // pt
      "иерихон","стены", // ru
      "أريحا","أسوار", // ar
      "यरीहो","दीवारें", // hi
      "耶利哥","城牆","城墙", // zh
      "여리고","성벽", // ko
      "エリコ","城壁" // ja
    )
    // Samson (judges-13/14/15/16)
    pin(listOf("judges-13","judges-14","judges-15","judges-16"),
      "sanson", // es
      "samson", // fr/en same
      "simson", // de
      "sansone", // it
      "sansao", // pt
      "самсон","далила", // ru
      "شمشون","دليلة", // ar
      "शिमशोन","दलीला", // hi
      "參孫","参孙","大利拉", // zh
      "삼손","들릴라", // ko
      "サムソン","デリラ" // ja
    )
    // Gideon (judges-6/7)
    pin(listOf("judges-6","judges-7"),
      "gedeon", // es/pt
      "gedeon", // fr
      "gideon", // de/en same
      "gedeone", // it
      "гедеон", // ru
      "جدعون", // ar
      "गिदोन", // hi
      "基甸", // zh
      "기드온", // ko
      "ギデオン" // ja
    )
    // Ruth and Boaz (ruth-1/2/3/4)
    pin(listOf("ruth-1","ruth-2","ruth-3","ruth-4"),
      "rut","booz", // es
      "ruth","boaz", // fr/en same
      "rut","boas", // de/pt
      "rut","booz", // it
      "руфь","вооз", // ru
      "راعوث","بوعز", // ar
      "रूत","बोअज़", // hi
      "路得","波阿斯", // zh
      "룻","보아스", // ko
      "ルツ","ボアズ" // ja
    )
    // David and Goliath (1-samuel-17) - covered above
    // David anointed (1-samuel-16)
    pin(listOf("1-samuel-16"),
      "david ungido","ungido", // es
      "david oint","oint", // fr
      "david gesalbt","gesalbt", // de
      "david unto","unto", // it
      "david ungido", // pt
      "давид помазан","помазание", // ru
      "مسح داود","مسح", // ar
      "दाऊद का अभिषेक","अभिषेक", // hi
      "大衛受膏","大卫受膏","膏", // zh
      "다윗 기름부음","기름부음", // ko
      "ダビデの油注ぎ" // ja
    )
    // Bathsheba / Adultery (2-samuel-11/12)
    pin(listOf("2-samuel-11","2-samuel-12"),
      "betsabe","adulterio", // es
      "bethsabee","adultere", // fr
      "batseba","ehebruch", // de
      "betsabea","adulterio", // it
      "bate-seba","adulterio", // pt
      "вирсавия","прелюбодеяние", // ru
      "بثشبع","زنا", // ar
      "बतशेबा","व्यभिचार", // hi
      "拔示巴","奸淫", // zh
      "밧세바","간음", // ko
      "バテシバ","姦淫" // ja
    )
    // Solomon's wisdom (1-kings-3)
    pin(listOf("1-kings-3"),
      "sabiduria de salomon","salomon", // es
      "sagesse de salomon","salomon", // fr
      "salomos weisheit","salomo", // de
      "saggezza di salomone","salomone", // it
      "sabedoria de salomao","salomao", // pt
      "мудрость соломона","соломон", // ru
      "حكمة سليمان","سليمان", // ar
      "सुलैमान की बुद्धि","सुलैमान", // hi
      "所羅門","所罗门","智慧", // zh
      "솔로몬","지혜", // ko
      "ソロモン","知恵" // ja
    )
    // Elijah on Carmel (1-kings-18)
    pin(listOf("1-kings-18"),
      "elias","carmelo","baal", // es
      "elie","carmel", // fr
      "elia","karmel", // de
      "elia","carmelo", // it
      "elias","carmelo", // pt
      "илия","кармил","ваал", // ru
      "إيليا","الكرمل","بعل", // ar
      "एलिय्याह","कर्मेल","बाल", // hi
      "以利亞","以利亚","迦密","巴力", // zh
      "엘리야","갈멜","바알", // ko
      "エリヤ","カルメル","バアル" // ja
    )
    // Elisha / Chariot (2-kings-2)
    pin(listOf("2-kings-2"),
      "eliseo","carro de fuego", // es
      "elisee","char de feu", // fr
      "elisa","feuriger wagen", // de
      "eliseo","carro di fuoco", // it
      "eliseu","carro de fogo", // pt
      "елисей","огненная колесница", // ru
      "أليشع","مركبة نارية", // ar
      "एलीशा","अग्नि रथ", // hi
      "以利沙","火車","火马车", // zh
      "엘리사","불병거","불수레", // ko
      "エリシャ","火の戦車" // ja
    )
    // Naaman healed (2-kings-5)
    pin(listOf("2-kings-5"),
      "naaman","lepra", // es
      "naaman","lepre", // fr
      "naaman","aussatz", // de
      "naaman","lebbra", // it
      "naama","lepra", // pt
      "нееман","проказа", // ru
      "نعمان","برص", // ar
      "नामान","कोढ़", // hi
      "乃縵","乃缦","大痲瘋","大麻风", // zh
      "나아만","나병", // ko
      "ナアマン","ツァラアト" // ja
    )
    // Fiery furnace (daniel-3)
    pin(listOf("daniel-3"),
      "horno de fuego","horno","sadrac","mesac","abed-nego", // es
      "fournaise","fournaise ardente", // fr
      "feuerofen","schadrach","meschach","abed-nego", // de
      "fornace ardente","fornace", // it
      "fornalha de fogo","fornalha", // pt
      "огненная печь","седрах","мисах","авденаго", // ru
      "أتون النار","شدرخ","ميشخ","عبدنغو", // ar
      "आग की भट्टी","शद्रक","मेशक","अबेदनगो", // hi
      "火窯","火窑","沙得拉","米煞","亞伯尼歌","亚伯尼歌", // zh
      "풀무불","사드락","메삭","아벳느고", // ko
      "燃える炉","シャデラク","メシャク","アベデネゴ" // ja
    )
    // Handwriting on wall (daniel-5)
    pin(listOf("daniel-5"),
      "escritura en la pared","mene tekel", // es
      "ecriture sur le mur", // fr
      "schrift an der wand","menetekel", // de
      "scrittura sul muro", // it
      "escrita na parede", // pt
      "надпись на стене","мене текел", // ru
      "الكتابة على الحائط","منا تقيل", // ar
      "दीवार पर लिखावट", // hi
      "牆上的字","墙上的字", // zh
      "벽에 쓴 글씨", // ko
      "壁の文字" // ja
    )
    // Daniel in lions' den (daniel-6) - covered above
    // Jonah (jonah-1/2) - covered above
    // Esther / Haman (esther-4/5/7)
    pin(listOf("esther-4","esther-5","esther-7"),
      "ester","aman","mardoqueo", // es
      "esther","haman","mardochee", // fr
      "esther","haman","mordechai", // de
      "ester","aman","mardocheo", // it
      "ester","hama","mardoqueu", // pt
      "есфирь","аман","мардохей", // ru
      "أستير","هامان","مردخاي", // ar
      "एस्तेर","हामान","मोर्दकै", // hi
      "以斯帖","哈曼","末底改", // zh
      "에스더","하만","모르드개", // ko
      "エステル","ハマン","モルデカイ" // ja
    )
    // Babylonian exile (2-kings-24/25)
    pin(listOf("2-kings-24","2-kings-25"),
      "exilio","babilonia","nabucodonosor","cautiverio", // es
      "exil","babylone","nabuchodonosor","captivite", // fr
      "exil","babylon","nebukadnezar","gefangenschaft", // de
      "esilio","babilonia","nabucodonosor","cattivita", // it
      "exilio","babilonia","nabucodonosor","cativeiro", // pt
      "вавилон","плен","навуходоносор","изгнание", // ru
      "بابل","سبي","نبوخذنصر", // ar
      "बाबुल","बन्धुवाई","नबूकदनेस्सर", // hi
      "巴比倫","巴比伦","被擄","被掳","尼布甲尼撒", // zh
      "바벨론","포로","느부갓네살", // ko
      "バビロン","捕囚","ネブカドネザル" // ja
    )
    // Suffering servant (isaiah-53)
    pin(listOf("isaiah-53"),
      "siervo sufriente","traspasado", // es
      "serviteur souffrant","transperce", // fr
      "leidender knecht","durchbohrt", // de
      "servo sofferente","trafitto", // it
      "servo sofredor","traspassado", // pt
      "страдающий раб","пронзенный", // ru
      "العبد المتألم","مثقوب", // ar
      "दुःखी सेवक","बेधा गया", // hi
      "受苦的僕人","受苦的仆人","被刺透", // zh
      "고난받는 종","찔림", // ko
      "苦難のしもべ","刺し通された" // ja
    )
    // Dry bones (ezekiel-37)
    pin(listOf("ezekiel-37"),
      "huesos secos","valle de huesos", // es
      "ossements desseches","vallee d'ossements", // fr
      "verdorrte gebeine","tal der gebeine", // de
      "ossa secche","valle delle ossa", // it
      "ossos secos","vale dos ossos", // pt
      "сухие кости","долина костей", // ru
      "العظام اليابسة","وادي العظام", // ar
      "सूखी हड्डियाँ", // hi
      "枯骨","枯骨復活","枯骨复活", // zh
      "마른 뼈", // ko
      "枯れた骨","枯骨" // ja
    )
    // Psalm 23 / Shepherd
    pin(listOf("psalms-23"),
      "pastor", // es/pt
      "berger", // fr
      "hirte", // de
      "pastore", // it
      "пастырь","пастух", // ru
      "الراعي", // ar
      "चरवाहा", // hi
      "牧者","牧羊人", // zh
      "목자", // ko
      "羊飼い" // ja
    )
    // Job's suffering (job-1/2)
    pin(listOf("job-1","job-2"),
      "sufrimiento de job", // es
      "souffrance de job", // fr
      "hiob","leiden hiobs", // de
      "giobbe","sofferenza di giobbe", // it
      "sofrimento de jo", // pt
      "иов","страдания иова", // ru
      "أيوب","معاناة أيوب", // ar
      "अय्यूब","अय्यूब की पीड़ा", // hi
      "約伯","约伯", // zh
      "욥", // ko
      "ヨブ" // ja
    )
    // Atonement / Scapegoat (leviticus-16)
    pin(listOf("leviticus-16"),
      "expiacion","chivo expiatorio", // es
      "expiation","bouc emissaire", // fr
      "versohnung","sundenbock", // de
      "espiazione","capro espiatorio", // it
      "expiacao","bode expiatorio", // pt
      "искупление","козел отпущения", // ru
      "الكفارة","كبش الفداء", // ar
      "प्रायश्चित","बलि का बकरा", // hi
      "贖罪","赎罪","替罪羊", // zh
      "속죄","속죄의 날","속죄양", // ko
      "贖罪","贖罪の日","身代わりの山羊" // ja
    )
    // Feasts (leviticus-23)
    pin(listOf("leviticus-23"),
      "fiestas","festividades", // es
      "fetes","solennites", // fr
      "feste","festtage", // de
      "feste","festivita", // it
      "festas","festividades", // pt
      "праздники","праздник", // ru
      "الأعياد","عيد", // ar
      "पर्व","त्योहार", // hi
      "節期","节期", // zh
      "절기","명절", // ko
      "祭り","祝祭" // ja
    )
    // Revelation horsemen (revelation-6)
    pin(listOf("revelation-6"),
      "jinetes","caballo palido","cuatro jinetes", // es
      "cavaliers","cheval pale","quatre cavaliers", // fr
      "reiter","fahles pferd","vier reiter", // de
      "cavalieri","cavallo pallido","quattro cavalieri", // it
      "cavaleiros","cavalo palido","quatro cavaleiros", // pt
      "всадники","конь бледный","четыре всадника", // ru
      "الفرسان","حصان أبيض","أربعة فرسان", // ar
      "घुड़सवार","पीला घोड़ा","चार घुड़सवार", // hi
      "四騎士","灰馬","四馬", // zh
      "네 기사","네 말","창백한 말", // ko
      "四騎士","青ざめた馬" // ja
    )
    // Dragon (revelation-12)
    pin(listOf("revelation-12"),
      "dragon", // es/fr/en same
      "drache", // de
      "dragone","drago", // it
      "dragao", // pt
      "дракон", // ru
      "التنين","تنين", // ar
      "अजगर", // hi
      "龍","龙", // zh
      "용", // ko
      "竜","ドラゴン" // ja
    )
    // Stoning of Stephen (acts-7)
    pin(listOf("acts-7"),
      "esteban","lapidacion", // es
      "etienne","lapidation", // fr
      "stephanus","steinigung", // de
      "stefano","lapidazione", // it
      "estevao","apedrejamento", // pt
      "стефан","побиение камнями", // ru
      "استفانوس","الرجم", // ar
      "स्तिफनुस","पत्थरवाह", // hi
      "司提反","用石頭打","用石头打", // zh
      "스데반","돌로 침", // ko
      "ステファノ","石打ち" // ja
    )
    // Cleansing temple (matt-21, mark-11, john-2)
    pin(listOf("matthew-21","mark-11","john-2"),
      "purificacion del templo","cambistas", // es
      "purification du temple","marchands", // fr
      "tempelreinigung","geldwechsler", // de
      "purificazione del tempio","cambiavalute", // it
      "purificacao do templo","cambistas", // pt
      "очищение храма","менялы", // ru
      "تطهير الهيكل","الصيارفة", // ar
      "मन्दिर की शुद्धि","सर्राफ़", // hi
      "潔淨聖殿","洁净圣殿", // zh
      "성전 정화","환전상", // ko
      "宮清め","両替人" // ja
    )

  }

  private fun buildTierALocalizedPins(): Map<String, List<String>> = buildMap {
    // ── Remaining Tier A events: localized translations for English-only pins ──

    // Ten virgins / Talents (matt-25)
    pin(listOf("matthew-25"),
      "virgenes","talentos","diez virgenes", // es
      "vierges","talents","dix vierges", // fr
      "jungfrauen","talente","zehn jungfrauen", // de
      "vergini","talenti","dieci vergini", // it
      "virgens","talentos","dez virgens", // pt
      "девы","таланты","десять дев", // ru
      "العذارى","الوزنات","عشر عذارى", // ar
      "कुँवारियाँ","तोड़े","दस कुँवारियाँ", // hi
      "十童女","才幹","才干", // zh
      "열 처녀","달란트", // ko
      "十人の乙女","タラント" // ja
    )
    // Water from rock (exod-17, num-20)
    pin(listOf("exodus-17","numbers-20"),
      "agua de la roca","roca","agua", // es
      "eau du rocher","rocher", // fr
      "wasser aus dem felsen","fels", // de
      "acqua dalla roccia","roccia", // it
      "agua da rocha","rocha", // pt
      "вода из скалы","скала", // ru
      "ماء من الصخرة","صخرة", // ar
      "चट्टान से पानी","चट्टान", // hi
      "磐石出水","磐石", // zh
      "반석에서 물", // ko
      "岩から水" // ja
    )
    // Spies / Caleb (num-13/14)
    pin(listOf("numbers-13","numbers-14"),
      "espias","caleb", // es/pt
      "espions", // fr
      "kundschafter","kaleb", // de
      "esploratori", // it
      "шпионы","разведчики","халев", // ru
      "الجواسيس","كالب", // ar
      "भेदिये","कालेब", // hi
      "探子","迦勒", // zh
      "정탐꾼","갈렙", // ko
      "斥候","カレブ" // ja
    )
    // Bronze serpent (num-21)
    pin(listOf("numbers-21"),
      "serpiente de bronce", // es
      "serpent d'airain", // fr
      "eherne schlange", // de
      "serpente di bronzo", // it
      "serpente de bronze", // pt
      "медный змей", // ru
      "الحية النحاسية", // ar
      "पीतल का साँप", // hi
      "銅蛇","铜蛇", // zh
      "놋뱀", // ko
      "青銅の蛇" // ja
    )
    // Jordan crossing (josh-3)
    pin(listOf("joshua-3"),
      "cruce del jordan","jordan", // es
      "traversee du jourdain","jourdain", // fr
      "jordanüberquerung","jordan", // de (accent-stripped)
      "attraversamento del giordano","giordano", // it
      "travessia do jordao","jordao", // pt
      "переход через иордан","иордан", // ru
      "عبور الأردن","الأردن", // ar
      "यरदन पार","यरदन", // hi
      "過約旦河","过约旦河","約旦","约旦", // zh
      "요단강","요단강 건넘", // ko
      "ヨルダン川渡り","ヨルダン" // ja
    )
    // Sun standing still (josh-10)
    pin(listOf("joshua-10"),
      "sol detenido","sol se detuvo", // es
      "soleil s'arreta","soleil arrete", // fr
      "sonne stillstand", // de
      "sole fermato","sole si fermo", // it
      "sol parado","sol se parou", // pt
      "солнце остановилось", // ru
      "الشمس وقفت", // ar
      "सूरज रुक गया", // hi
      "日頭停住","日头停住", // zh
      "태양이 멈춤", // ko
      "太陽が止まった" // ja
    )
    // Deborah / Barak (judg-4/5)
    pin(listOf("judges-4","judges-5"),
      "debora","barac", // es/it/pt
      "debora","baraq", // fr
      "debora","barak", // de
      "девора","варак", // ru
      "دبورة","باراق", // ar
      "दबोरा","बाराक", // hi
      "底波拉","巴拉", // zh
      "드보라","바락", // ko
      "デボラ","バラク" // ja
    )
    // Hannah (1-sam-1)
    pin(listOf("1-samuel-1"),
      "ana", // es/it/pt
      "anne", // fr
      "hanna", // de
      "анна", // ru
      "حنة", // ar
      "हन्ना", // hi
      "哈拿", // zh
      "한나", // ko
      "ハンナ" // ja
    )
    // Samuel's call (1-sam-3)
    pin(listOf("1-samuel-3"),
      "llamado de samuel", // es
      "appel de samuel", // fr
      "berufung samuels", // de
      "chiamata di samuele","samuele", // it
      "chamado de samuel", // pt
      "самуил","призвание самуила", // ru
      "صموئيل","دعوة صموئيل", // ar
      "शमूएल","शमूएल की बुलाहट", // hi
      "撒母耳","撒母耳蒙召", // zh
      "사무엘","사무엘의 부르심", // ko
      "サムエル","サムエルの召し" // ja
    )
    // Jonathan (1-sam-18/20)
    pin(listOf("1-samuel-18","1-samuel-20"),
      "jonatan", // es/pt
      "jonathas","jonathan", // fr
      "jonatan","jonathan", // de
      "gionata", // it
      "ионафан", // ru
      "يوناثان", // ar
      "योनातान", // hi
      "約拿單","约拿单", // zh
      "요나단", // ko
      "ヨナタン" // ja
    )
    // Kingdom of David (2-sam-5)
    pin(listOf("2-samuel-5"),
      "reino de david","reino", // es
      "royaume de david","royaume", // fr
      "konigreich davids","konigreich", // de
      "regno di davide","regno", // it
      "reino de davi", // pt
      "царство давида","царство", // ru
      "مملكة داود","مملكة", // ar
      "दाऊद का राज्य","राज्य", // hi
      "大衛的國度","大卫的国度", // zh
      "다윗의 왕국", // ko
      "ダビデの王国" // ja
    )
    // Temple / Dedication (1-kings-6/8)
    pin(listOf("1-kings-6","1-kings-8"),
      "templo","dedicacion", // es
      "temple","dedicace", // fr
      "tempel","einweihung", // de
      "tempio","dedicazione", // it
      "templo","dedicacao", // pt
      "храм","освящение храма", // ru
      "الهيكل","تدشين", // ar
      "मन्दिर","प्रतिष्ठा", // hi
      "聖殿","圣殿","獻殿","献殿", // zh
      "성전","봉헌", // ko
      "神殿","奉献" // ja
    )
    // Queen of Sheba (1-kings-10)
    pin(listOf("1-kings-10"),
      "reina de saba", // es
      "reine de saba", // fr
      "konigin von saba", // de
      "regina di saba", // it
      "rainha de saba", // pt
      "царица савская", // ru
      "ملكة سبأ", // ar
      "शीबा की रानी", // hi
      "示巴女王", // zh
      "스바 여왕", // ko
      "シバの女王" // ja
    )
    // Beasts (dan-7)
    pin(listOf("daniel-7"),
      "bestia","bestias","cuatro bestias", // es
      "bete","betes","quatre betes", // fr
      "tier","tiere","vier tiere", // de
      "bestia","bestie","quattro bestie", // it
      "besta","bestas","quatro bestas", // pt
      "зверь","звери","четыре зверя", // ru
      "الوحوش","وحش","أربعة وحوش", // ar
      "पशु","चार पशु", // hi
      "獸","兽","四獸","四兽", // zh
      "짐승","네 짐승", // ko
      "獣","四つの獣" // ja
    )
    // Nehemiah / rebuild (neh-2/6)
    pin(listOf("nehemiah-2","nehemiah-6"),
      "nehemias","reconstruccion","reconstruir", // es
      "nehemie","reconstruction","rebatir", // fr
      "nehemia","wiederaufbau", // de
      "neemia","ricostruzione", // it
      "neemias","reconstrucao", // pt
      "неемия","восстановление", // ru
      "نحميا","إعادة البناء", // ar
      "नहेम्याह","पुनर्निर्माण", // hi
      "尼希米","重建", // zh
      "느헤미야","재건", // ko
      "ネヘミヤ","再建" // ja
    )
    // Cyrus / return (ezra-1/3)
    pin(listOf("ezra-1","ezra-3"),
      "ciro","regreso","retorno", // es
      "cyrus","retour", // fr
      "kyrus","ruckkehr", // de
      "ciro","ritorno", // it
      "ciro","retorno", // pt
      "кир","возвращение", // ru
      "كورش","العودة", // ar
      "कोरेश","वापसी", // hi
      "古列","居魯士","居鲁士","歸回","归回", // zh
      "고레스","귀환", // ko
      "キュロス","帰還" // ja
    )
    // Immanuel / virgin birth (isa-7/9)
    pin(listOf("isaiah-7","isaiah-9"),
      "emmanuel","virgen", // es
      "emmanuel","vierge", // fr
      "immanuel","jungfrau", // de
      "emmanuele","vergine", // it
      "emanuel","virgem", // pt
      "еммануил","дева", // ru
      "عمانوئيل","عذراء", // ar
      "इम्मानुएल","कुँवारी", // hi
      "以馬內利","以马内利","童女", // zh
      "임마누엘","처녀", // ko
      "インマヌエル","乙女" // ja
    )
    // New covenant (jer-31)
    pin(listOf("jeremiah-31"),
      "nuevo pacto","nueva alianza", // es
      "nouvelle alliance", // fr
      "neuer bund", // de
      "nuova alleanza","nuovo patto", // it
      "nova alianca","novo pacto", // pt
      "новый завет", // ru
      "العهد الجديد", // ar
      "नई वाचा", // hi
      "新約","新约", // zh
      "새 언약", // ko
      "新しい契約" // ja
    )
    // Circumcision (gen-17)
    pin(listOf("genesis-17"),
      "circuncision", // es
      "circoncision", // fr
      "beschneidung", // de
      "circoncisione", // it
      "circuncisao", // pt
      "обрезание", // ru
      "الختان","ختان", // ar
      "खतना", // hi
      "割禮","割礼", // zh
      "할례", // ko
      "割礼" // ja
    )
    // Wrestling at Peniel (gen-32)
    pin(listOf("genesis-32"),
      "lucha","peniel", // es
      "lutte","peniel", // fr
      "ringkampf","pniel", // de
      "lotta","peniel", // it
      "luta","peniel", // pt
      "борьба","пенуэл", // ru
      "المصارعة","فنيئيل", // ar
      "कुश्ती","पनीएल", // hi
      "摔跤","毗努伊勒", // zh
      "씨름","브니엘", // ko
      "格闘","ペヌエル" // ja
    )
    // Pharaoh's dreams (gen-40/41)
    pin(listOf("genesis-40","genesis-41"),
      "faraon","sueno","suenos", // es
      "pharaon","songe","songes", // fr
      "pharao","traum","traume", // de
      "faraone","sogno","sogni", // it
      "farao","sonho","sonhos", // pt
      "фараон","сон","сны", // ru
      "فرعون","حلم","أحلام", // ar
      "फ़िरौन","स्वप्न","सपना", // hi
      "法老","夢","梦", // zh
      "바로","꿈", // ko
      "ファラオ","夢" // ja
    )
    // Famine (gen-41)
    pin(listOf("genesis-41"),
      "hambruna","hambre", // es
      "famine", // fr/en same
      "hungersnot","hunger", // de
      "carestia","fame", // it
      "fome", // pt
      "голод", // ru
      "مجاعة","جوع", // ar
      "अकाल", // hi
      "饑荒","饥荒", // zh
      "기근", // ko
      "飢饉" // ja
    )
    // Tabernacle (exod-25/26)
    pin(listOf("exodus-25","exodus-26"),
      "tabernaculo", // es/pt
      "tabernacle", // fr/en same
      "stiftshutte","stiftszelt", // de
      "tabernacolo", // it
      "скиния", // ru
      "خيمة الاجتماع","المسكن", // ar
      "मिलापवाला तम्बू","तम्बू", // hi
      "會幕","会幕", // zh
      "성막", // ko
      "幕屋" // ja
    )
    // Vine / abide (john-15)
    pin(listOf("john-15"),
      "vid","permaneced", // es
      "vigne","demeurez", // fr
      "weinstock","bleibt", // de
      "vite","dimorate", // it
      "videira","permanecei", // pt
      "виноградная лоза","пребудьте", // ru
      "الكرمة","اثبتوا", // ar
      "दाखलता","बने रहो", // hi
      "葡萄樹","葡萄树", // zh
      "포도나무", // ko
      "ぶどうの木" // ja
    )
    // Nicodemus / born again (john-3)
    pin(listOf("john-3"),
      "nicodemo","nacer de nuevo", // es
      "nicodeme","naitre de nouveau", // fr
      "nikodemus","wiedergeboren", // de
      "nicodemo","rinascere", // it
      "nicodemos","nascer de novo", // pt
      "никодим","рождение свыше", // ru
      "نيقوديموس","الولادة الجديدة", // ar
      "नीकुदेमुस","नया जन्म", // hi
      "尼哥底母","重生", // zh
      "니고데모","거듭남", // ko
      "ニコデモ","新生" // ja
    )
    // Zacchaeus (luke-19)
    pin(listOf("luke-19"),
      "zaqueo", // es
      "zachee", // fr
      "zachaus", // de
      "zaccheo", // it
      "zaqueu", // pt
      "закхей", // ru
      "زكا", // ar
      "जक्कई", // hi
      "撒該","撒该", // zh
      "삭개오", // ko
      "ザアカイ" // ja
    )
    // Psalm 22 / forsaken
    pin(listOf("psalms-22"),
      "desamparado","abandonado", // es
      "abandonne", // fr
      "verlassen", // de
      "abbandonato", // it
      "desamparado", // pt
      "оставлен", // ru
      "متروك", // ar
      "त्यागा","छोड़ दिया", // hi
      "離棄","离弃", // zh
      "버림받은", // ko
      "見捨てられた" // ja
    )
    // Psalm 51 / cleanse
    pin(listOf("psalms-51"),
      "limpiame","purificame", // es
      "purifie-moi", // fr
      "reinige", // de
      "purificami", // it
      "purifica-me", // pt
      "очисти", // ru
      "طهرني", // ar
      "शुद्ध कर", // hi
      "潔淨","洁净", // zh
      "깨끗하게", // ko
      "清めて" // ja
    )
    // Psalm 91 / refuge
    pin(listOf("psalms-91"),
      "refugio","amparo", // es
      "abri", // fr
      "zuflucht", // de
      "rifugio", // it
      "refugio","abrigo", // pt
      "прибежище","убежище", // ru
      "ملجأ", // ar
      "शरण","आश्रय", // hi
      "避難所","避难所", // zh
      "피난처","은신처", // ko
      "避け所" // ja
    )
    // Psalm 139 / womb
    pin(listOf("psalms-139"),
      "vientre","tejido", // es
      "ventre","forme", // fr
      "mutterleib","gebildet", // de
      "grembo","intessuto", // it
      "ventre","tecido", // pt
      "утроба","соткал", // ru
      "رحم","جبلتني", // ar
      "गर्भ","रचा", // hi
      "母腹","母胎", // zh
      "태중","모태", // ko
      "母の胎" // ja
    )
    // Proverbs 31 / noble wife
    pin(listOf("proverbs-31"),
      "mujer virtuosa","mujer noble", // es
      "femme vertueuse","femme vaillante", // fr
      "tugendhafte frau","edle frau", // de
      "donna virtuosa","donna nobile", // it
      "mulher virtuosa", // pt
      "добродетельная жена", // ru
      "المرأة الفاضلة", // ar
      "भली पत्नी","गुणवती", // hi
      "才德的婦人","才德的妇人", // zh
      "현숙한 여인", // ko
      "しっかりした妻" // ja
    )
    // Whirlwind / God speaks to Job (job-38)
    pin(listOf("job-38"),
      "torbellino","tempestad", // es
      "tourbillon","tempete", // fr
      "sturmwind","wirbelwind", // de
      "turbine","tempesta", // it
      "redemoinho", // pt
      "буря","вихрь", // ru
      "العاصفة","الزوبعة", // ar
      "आँधी","बवंडर", // hi
      "旋風","旋风", // zh
      "회오리바람","폭풍", // ko
      "嵐","つむじ風" // ja
    )
    // Ecclesiastes / vanity (eccl-1)
    pin(listOf("ecclesiastes-1"),
      "vanidad de vanidades", // es
      "vanite des vanites", // fr
      "eitelkeit der eitelkeiten", // de
      "vanita delle vanita", // it
      "vaidade de vaidades", // pt
      "суета сует", // ru
      "باطل الأباطيل", // ar
      "व्यर्थ ही व्यर्थ", // hi
      "虛空的虛空","虚空的虚空", // zh
      "헛되고 헛되다", // ko
      "空の空" // ja
    )
    // Shema (deut-6)
    pin(listOf("deuteronomy-6"),
      "escucha israel","oye israel", // es
      "ecoute israel", // fr
      "hore israel", // de
      "ascolta israele", // it
      "ouve israel", // pt
      "слушай израиль", // ru
      "اسمع يا إسرائيل", // ar
      "सुन हे इस्राएल", // hi
      "以色列啊你要聽","以色列啊你要听", // zh
      "이스라엘아 들으라", // ko
      "聞けイスラエル" // ja
    )

    // ── Additional Tier A events (to reach 100) ──

    // Annunciation (luke-1)
    pin(listOf("luke-1"),
      "annunciation","angel","gabriel","mary","virgin",
      "anunciacion","angel gabriel","maria", // es
      "annonciation","ange gabriel","marie", // fr
      "verkundigung","engel gabriel","maria", // de
      "annunciazione","angelo gabriele", // it
      "anunciacao","anjo gabriel", // pt
      "благовещение","архангел гавриил","мария", // ru
      "البشارة","جبرائيل","مريم", // ar
      "सुसमाचार","जिब्राईल","मरियम", // hi
      "天使報喜","天使报喜","加百列","馬利亞","马利亚", // zh
      "수태고지","가브리엘","마리아", // ko
      "受胎告知","ガブリエル","マリア" // ja
    )
    // Flight to Egypt (matt-2)
    pin(listOf("matthew-2"),
      "flight","egypt","herod","massacre","innocents","slaughter",
      "huida a egipto","herodes","matanza de inocentes", // es
      "fuite en egypte","herode","massacre des innocents", // fr
      "flucht nach agypten","herodes","kindermord", // de
      "fuga in egitto","erode","strage degli innocenti", // it
      "fuga para o egito","herodes","massacre dos inocentes", // pt
      "бегство в египет","ирод","избиение младенцев", // ru
      "الهروب إلى مصر","هيرودس","مذبحة الأبرياء", // ar
      "मिस्र में भाग","हेरोदेस", // hi
      "逃往埃及","希律","屠殺嬰孩","屠杀婴孩", // zh
      "애굽으로 피난","헤롯","영아 학살", // ko
      "エジプトへの逃避","ヘロデ","幼児虐殺" // ja
    )
    // Calling of disciples (matt-4)
    pin(listOf("matthew-4","mark-1"),
      "calling","disciples","fishers","follow",
      "llamado de discipulos","pescadores","sigueme", // es
      "appel des disciples","pecheurs","suis-moi", // fr
      "berufung der junger","fischer","folge mir", // de
      "chiamata dei discepoli","pescatori","seguimi", // it
      "chamado dos discipulos","pescadores","segue-me", // pt
      "призвание учеников","рыбаки","следуй за мной", // ru
      "دعوة التلاميذ","صيادين","اتبعني", // ar
      "चेलों की बुलाहट","मछुवारे","मेरे पीछे आओ", // hi
      "呼召門徒","呼召门徒","漁夫","渔夫", // zh
      "제자 부르심","어부","나를 따르라", // ko
      "弟子の召命","漁師","わたしに従いなさい" // ja
    )
    // Peter's confession (matt-16)
    pin(listOf("matthew-16"),
      "peter","confession","messiah","christ","keys",
      "confesion de pedro","tu eres el cristo", // es
      "confession de pierre","tu es le christ", // fr
      "bekenntnis des petrus","du bist der christus", // de
      "confessione di pietro","tu sei il cristo", // it
      "confissao de pedro","tu es o cristo", // pt
      "исповедание петра","ты христос", // ru
      "اعتراف بطرس","أنت المسيح", // ar
      "पतरस का अंगीकार","तू मसीह है", // hi
      "彼得的認信","彼得的认信","你是基督", // zh
      "베드로의 고백","주는 그리스도", // ko
      "ペテロの告白","あなたはキリスト" // ja
    )
    // Healing of blind man (john-9)
    pin(listOf("john-9"),
      "blind","healed","sight","mud","siloam",
      "ciego","sanado","siloe", // es
      "aveugle","gueri","siloe", // fr
      "blinder","geheilt","siloah", // de
      "cieco","guarito","siloe", // it
      "cego","curado","siloe", // pt
      "слепой","исцелен","силоам", // ru
      "الأعمى","شفاء","سلوام", // ar
      "अंधा","चंगा","शीलोह", // hi
      "瞎子","醫治","西羅亞","医治","西罗亚", // zh
      "소경","고침","실로암", // ko
      "盲人","いやし","シロアム" // ja
    )
    // Road to Emmaus (luke-24)
    pin(listOf("luke-24"),
      "emmaus","road","travelers",
      "camino a emaus","emaus", // es
      "chemin d'emmaus","emmaus", // fr
      "emmaus","weg nach emmaus", // de
      "strada di emmaus","emmaus", // it
      "caminho de emaus","emaus", // pt
      "эммаус","путь в эммаус", // ru
      "عمواس","طريق عمواس", // ar
      "इम्माऊस","इम्माऊस की राह", // hi
      "以馬忤斯","以马忤斯", // zh
      "엠마오","엠마오로 가는 길", // ko
      "エマオ","エマオへの道" // ja
    )
    // Great Commission (matt-28)
    pin(listOf("matthew-28"),
      "commission","nations","preach","gospel","world",
      "gran comision","id y haced discipulos","todas las naciones", // es
      "grand ordre missionnaire","faites des disciples","toutes les nations", // fr
      "missionsbefehl","machet zu jungern","alle volker", // de
      "grande commissione","fate discepoli","tutte le nazioni", // it
      "grande comissao","fazei discipulos","todas as nacoes", // pt
      "великое поручение","идите научите все народы", // ru
      "الإرسالية العظمى","تلمذوا جميع الأمم", // ar
      "महान आदेश","सब जातियों को चेला बनाओ", // hi
      "大使命","使萬民作門徒","使万民作门徒", // zh
      "지상명령","모든 민족을 제자로 삼아", // ko
      "大宣教命令","すべての国の人を弟子にしなさい" // ja
    )
    // Philip and Ethiopian (acts-8)
    pin(listOf("acts-8"),
      "philip","ethiopian","eunuch","chariot",
      "felipe","etiope","eunuco", // es
      "philippe","ethiopien","eunuque", // fr
      "philippus","athiopier","eunuch", // de
      "filippo","etiope","eunuco", // it
      "filipe","etiope","eunuco", // pt
      "филипп","эфиоп","евнух", // ru
      "فيلبس","الحبشي","الخصي", // ar
      "फिलिप्पुस","कूशी","खोजा", // hi
      "腓利","埃提阿伯","太監","太监", // zh
      "빌립","에디오피아","내시", // ko
      "ピリポ","エチオピア人","宦官" // ja
    )
    // Peter's vision / Cornelius (acts-10)
    pin(listOf("acts-10"),
      "cornelius","vision","sheet","unclean","gentiles",
      "cornelio","vision","lienzo","inmundo","gentiles", // es
      "corneille","vision","nappe","impur","paiens", // fr
      "kornelius","vision","tuch","unrein","heiden", // de
      "cornelio","visione","lenzuolo","impuro","gentili", // it
      "cornelio","visao","lenco","imundo","gentios", // pt
      "корнилий","видение","полотно","нечистое","язычники", // ru
      "كرنيليوس","رؤيا","ملاءة","نجس","الأمم", // ar
      "कुरनेलियुस","दर्शन","चादर","अशुद्ध","अन्यजाति", // hi
      "哥尼流","異象","异象","布","不潔","不洁","外邦人", // zh
      "고넬료","환상","보자기","부정","이방인", // ko
      "コルネリウス","幻","布","汚れた","異邦人" // ja
    )
    // Paul and Silas in prison (acts-16)
    pin(listOf("acts-16"),
      "silas","prison","earthquake","jailer","philippian",
      "silas","carcel","terremoto","carcelero","filipos", // es
      "silas","prison","tremblement","geolier","philippes", // fr
      "silas","gefangnis","erdbeben","kerkermeister","philippi", // de
      "sila","prigione","terremoto","carceriere","filippi", // it
      "silas","prisao","terremoto","carcereiro","filipos", // pt
      "сила","тюрьма","землетрясение","темничный страж","филиппы", // ru
      "سيلا","سجن","زلزال","سجان","فيلبي", // ar
      "सीलास","कारागार","भूकम्प","दरोगा","फिलिप्पी", // hi
      "西拉","監獄","监狱","地震","獄卒","狱卒","腓立比", // zh
      "실라","감옥","지진","간수","빌립보", // ko
      "シラス","牢獄","地震","看守","ピリピ" // ja
    )
    // Fall of Jerusalem (2-kings-25, jer-39)
    pin(listOf("2-kings-25","jeremiah-39","lamentations-1"),
      "fall of jerusalem","destruction","siege",
      "caida de jerusalen","destruccion","asedio", // es
      "chute de jerusalem","destruction","siege", // fr
      "fall jerusalems","zerstorung","belagerung", // de
      "caduta di gerusalemme","distruzione","assedio", // it
      "queda de jerusalem","destruicao","cerco", // pt
      "падение иерусалима","разрушение","осада", // ru
      "سقوط أورشليم","دمار","حصار", // ar
      "यरूशलेम का पतन","विनाश","घेराबंदी", // hi
      "耶路撒冷淪陷","耶路撒冷沦陷","毀滅","毁灭","圍城","围城", // zh
      "예루살렘 함락","멸망","포위", // ko
      "エルサレム陥落","破壊","包囲" // ja
    )
    // Elijah fed by ravens (1-kings-17)
    pin(listOf("1-kings-17"),
      "ravens","widow","zarephath",
      "cuervos","viuda","sarepta", // es
      "corbeaux","veuve","sarepta", // fr
      "raben","witwe","zarpat", // de
      "corvi","vedova","sarepta", // it
      "corvos","viuva","sarepta", // pt
      "вороны","вдова","сарепта", // ru
      "الغربان","أرملة","صرفة", // ar
      "कौवे","विधवा","सारपत", // hi
      "烏鴉","乌鸦","寡婦","寡妇","撒勒法", // zh
      "까마귀","과부","사렙다", // ko
      "烏","やもめ","ザレパテ" // ja
    )
    // Witch of Endor (1-sam-28)
    pin(listOf("1-samuel-28"),
      "witch","endor","medium","necromancer",
      "pitonisa de endor","adivina", // es
      "sorciere d'endor","necromancienne", // fr
      "hexe von endor","totenbeschworer", // de
      "strega di endor","negromante", // it
      "feiticeira de endor","medium", // pt
      "аэндорская волшебница","ведьма", // ru
      "عرافة عين دور","ساحرة", // ar
      "एन्दोर की भूतनी", // hi
      "隱多珥的交鬼婦人","隐多珥的交鬼妇人", // zh
      "엔돌의 신접한 여인", // ko
      "エンドルの口寄せ" // ja
    )
    // Solomon's judgment (1-kings-3)
    pin(listOf("1-kings-3"),
      "judgment","two mothers","baby","divide",
      "juicio de salomon","dos madres", // es
      "jugement de salomon","deux meres", // fr
      "salomos urteil","zwei mutter", // de
      "giudizio di salomone","due madri", // it
      "julgamento de salomao","duas maes", // pt
      "суд соломона","две матери", // ru
      "حكم سليمان","أمّان", // ar
      "सुलैमान का न्याय","दो माताएँ", // hi
      "所羅門的審判","所罗门的审判","兩個母親","两个母亲", // zh
      "솔로몬의 재판","두 어머니", // ko
      "ソロモンの裁き","二人の母" // ja
    )
    // Ezekiel's vision / chariot (ezek-1)
    pin(listOf("ezekiel-1"),
      "ezekiel","vision","throne","wheels","cherubim",
      "ezequiel","vision","trono","ruedas","querubines", // es
      "ezechiel","vision","trone","roues","cherubins", // fr
      "hesekiel","vision","thron","rader","cherubim", // de
      "ezechiele","visione","trono","ruote","cherubini", // it
      "ezequiel","visao","trono","rodas","querubins", // pt
      "иезекииль","видение","престол","колеса","херувимы", // ru
      "حزقيال","رؤيا","عرش","عجلات","كروبيم", // ar
      "यहेजकेल","दर्शन","सिंहासन","पहिये","करूब", // hi
      "以西結","以西结","異象","异象","寶座","宝座","輪","轮","基路伯", // zh
      "에스겔","환상","보좌","바퀴","그룹", // ko
      "エゼキエル","幻","御座","車輪","ケルビム" // ja
    )
    // Isaiah's commission (isa-6)
    pin(listOf("isaiah-6"),
      "isaiah","seraphim","coal","lips","holy","send",
      "isaias","serafines","carbon","labios","santo","enviame", // es
      "esaie","seraphins","charbon","levres","saint","envoie-moi", // fr
      "jesaja","seraphim","kohle","lippen","heilig","sende mich", // de
      "isaia","serafini","carbone","labbra","santo","mandami", // it
      "isaias","serafins","carvao","labios","santo","envia-me", // pt
      "исаия","серафимы","уголь","уста","свят","пошли меня", // ru
      "إشعياء","سيرافيم","جمرة","شفتين","قدوس","أرسلني", // ar
      "यशायाह","साराप","अंगारा","होंठ","पवित्र","मुझे भेज", // hi
      "以賽亞","以赛亚","撒拉弗","炭","嘴唇","聖哉","圣哉","差遣我", // zh
      "이사야","스랍","숯","입술","거룩","나를 보내소서", // ko
      "イザヤ","セラフィム","炭","くちびる","聖なる","私を遣わして" // ja
    )
    // Olivet discourse / end times (matt-24)
    pin(listOf("matthew-24","matthew-25"),
      "olivet","discourse","endtimes","signs","coming","tribulation",
      "discurso del monte de los olivos","senales","venida","tribulacion", // es
      "discours sur le mont des oliviers","signes","venue","tribulation", // fr
      "endzeitrede","olberg","zeichen","wiederkunft","trubsal", // de
      "discorso escatologico","monte degli ulivi","segni","venuta","tribolazione", // it
      "discurso das oliveiras","sinais","vinda","tribulacao", // pt
      "елеонская беседа","знамения","пришествие","скорбь", // ru
      "حديث جبل الزيتون","علامات","مجيء","ضيقة", // ar
      "जैतून पर्वत का प्रवचन","चिन्ह","आगमन","क्लेश", // hi
      "橄欖山講論","橄榄山讲论","預兆","预兆","再來","再来","災難","灾难", // zh
      "감람산 강화","징조","재림","환난", // ko
      "オリーブ山の説教","しるし","再臨","患難" // ja
    )
    // New Jerusalem (rev-21)
    pin(listOf("revelation-21"),
      "new jerusalem","holy city","no tears","new heaven","new earth",
      "nueva jerusalen","ciudad santa","sin lagrimas","nuevo cielo","nueva tierra", // es
      "nouvelle jerusalem","cite sainte","plus de larmes","nouveau ciel","nouvelle terre", // fr
      "neues jerusalem","heilige stadt","keine tranen","neuer himmel","neue erde", // de
      "nuova gerusalemme","citta santa","niente lacrime","nuovo cielo","nuova terra", // it
      "nova jerusalem","cidade santa","sem lagrimas","novo ceu","nova terra", // pt
      "новый иерусалим","святой город","слез больше не будет","новое небо","новая земля", // ru
      "أورشليم الجديدة","المدينة المقدسة","لا دموع","سماء جديدة","أرض جديدة", // ar
      "नया यरूशलेम","पवित्र नगरी","कोई आँसू नहीं","नया आकाश","नई पृथ्वी", // hi
      "新耶路撒冷","聖城","圣城","不再有眼淚","不再有眼泪","新天新地", // zh
      "새 예루살렘","거룩한 성","눈물을 닦아 주시고","새 하늘과 새 땅", // ko
      "新しいエルサレム","聖なる都","もはや涙はない","新しい天と新しい地" // ja
    )
    // Seven seals / Seven trumpets (rev-6/8)
    pin(listOf("revelation-6","revelation-8","revelation-16"),
      "seals","trumpets","bowls","wrath","seven",
      "sellos","trompetas","copas","ira","siete", // es
      "sceaux","trompettes","coupes","colere","sept", // fr
      "siegel","posaunen","schalen","zorn","sieben", // de
      "sigilli","trombe","coppe","ira","sette", // it
      "selos","trombetas","tacas","ira","sete", // pt
      "печати","трубы","чаши","гнев","семь", // ru
      "أختام","أبواق","جامات","غضب","سبعة", // ar
      "मुहरें","तुरहियाँ","कटोरे","क्रोध","सात", // hi
      "七印","七號","七号","七碗","忿怒", // zh
      "일곱 인","일곱 나팔","일곱 대접","진노", // ko
      "七つの封印","七つのラッパ","七つの鉢","怒り" // ja
    )
    // Mark of beast / 666 (rev-13)
    pin(listOf("revelation-13"),
      "mark","beast","666","number","forehead","antichrist",
      "marca de la bestia","666","numero","frente","anticristo", // es
      "marque de la bete","666","nombre","front","antechrist", // fr
      "malzeichen des tieres","666","zahl","stirn","antichrist", // de
      "marchio della bestia","666","numero","fronte","anticristo", // it
      "marca da besta","666","numero","testa","anticristo", // pt
      "начертание зверя","666","число","лоб","антихрист", // ru
      "سمة الوحش","666","عدد","جبهة","المسيح الدجال", // ar
      "पशु की छाप","666","संख्या","माथा","मसीह विरोधी", // hi
      "獸的印記","兽的印记","666","數目","数目","額","额","敵基督","敌基督", // zh
      "짐승의 표","666","수","이마","적그리스도", // ko
      "獣の刻印","666","数字","額","反キリスト" // ja
    )
    // Woman at the well (john-4)
    pin(listOf("john-4"),
      "woman","well","samaritan","living water","worship",
      "mujer samaritana","pozo","agua viva", // es
      "femme samaritaine","puits","eau vive", // fr
      "samariterin","brunnen","lebendiges wasser", // de
      "samaritana","pozzo","acqua viva", // it
      "mulher samaritana","poco","agua viva", // pt
      "самарянка","колодец","вода живая", // ru
      "السامرية","البئر","ماء حي", // ar
      "सामरी स्त्री","कुआँ","जीवन का जल", // hi
      "撒瑪利亞婦人","撒玛利亚妇人","井","活水", // zh
      "사마리아 여인","우물","생수", // ko
      "サマリアの女","井戸","生ける水" // ja
    )

    // Creation (genesis-1, genesis-2)
    pin(listOf("genesis-1","genesis-2"),
      "creacion", // es
      "creation", // fr same as en
      "schopfung","erschaffung", // de
      "creazione", // it
      "criacao", // pt
      "сотворение","творение", // ru
      "الخلق","خلق", // ar
      "सृष्टि","रचना", // hi
      "創造","创造", // zh
      "창조", // ko
      "創造","天地創造" // ja
    )
    // Flood / Noah (genesis-6/7/8)
    pin(listOf("genesis-6","genesis-7","genesis-8"),
      "diluvio","noe","arca", // es
      "deluge","noe","arche", // fr
      "sintflut","arche","noah", // de
      "diluvio","noe","arca", // it
      "diluvio","noe","arca", // pt
      "потоп","ной","ковчег", // ru
      "الطوفان","نوح","الفلك", // ar
      "जलप्रलय","नूह","जहाज", // hi
      "大洪水","挪亞","方舟","挪亚", // zh
      "대홍수","노아","방주", // ko
      "大洪水","ノア","箱舟" // ja
    )
    // Passover / Lamb (exodus-12)
    pin(listOf("exodus-12"),
      "cordero","pascua", // es
      "agneau","paque", // fr
      "lamm","passa", // de
      "agnello","pasqua", // it
      "cordeiro","pascoa", // pt
      "агнец","пасха", // ru
      "خروف","الفصح", // ar
      "मेम्ना","फसह", // hi
      "羔羊","逾越節","逾越节", // zh
      "어린양","유월절", // ko
      "子羊","過越" // ja
    )
    // Red Sea crossing (exodus-14)
    pin(listOf("exodus-14"),
      "mar rojo","cruce", // es
      "mer rouge","traversee", // fr
      "rotes meer","durchzug", // de
      "mar rosso","attraversamento", // it
      "mar vermelho","travessia", // pt
      "красное море","переход", // ru
      "البحر الأحمر","عبور", // ar
      "लाल सागर","पार करना", // hi
      "紅海","红海","過海","过海", // zh
      "홍해","바다 건넘", // ko
      "紅海","海を渡る" // ja
    )
    // Ten Commandments (exodus-20, deut-5)
    pin(listOf("exodus-20","deuteronomy-5"),
      "mandamientos","diez mandamientos", // es
      "commandements","dix commandements", // fr
      "gebote","zehn gebote", // de
      "comandamenti","dieci comandamenti", // it
      "mandamentos","dez mandamentos", // pt
      "заповеди","десять заповедей", // ru
      "الوصايا","الوصايا العشر", // ar
      "आज्ञाएँ","दस आज्ञाएँ", // hi
      "誡命","十誡","诫命","十诫", // zh
      "계명","십계명", // ko
      "戒め","十戒" // ja
    )
    // Blessings and curses (deut-28)
    pin(listOf("deuteronomy-28"),
      "bendicion","maldicion", // es
      "benediction","malediction", // fr
      "segen","fluch", // de
      "benedizione","maledizione", // it
      "bencao","maldicao", // pt
      "благословение","проклятие", // ru
      "بركة","لعنة", // ar
      "आशीर्वाद","शाप", // hi
      "祝福","咒詛","咒诅", // zh
      "축복","저주", // ko
      "祝福","呪い" // ja
    )
    // David and Goliath (1-samuel-17)
    pin(listOf("1-samuel-17"),
      "goliat","gigante","honda", // es
      "goliath","geant","fronde", // fr
      "goliath","riese","schleuder", // de
      "golia","gigante","fionda", // it
      "golias","gigante","funda", // pt
      "голиаф","великан","праща", // ru
      "جليات","العملاق", // ar
      "गोलियत","दैत्य","गोफन", // hi
      "歌利亞","歌利亚","巨人", // zh
      "골리앗","거인","물매", // ko
      "ゴリアテ","巨人" // ja
    )
    // Daniel in the lions' den (daniel-6)
    pin(listOf("daniel-6"),
      "foso","leones", // es
      "fosse","lions", // fr
      "lowengrube","lowen", // de
      "fossa","leoni", // it
      "cova","leoes", // pt
      "львиный ров","львы", // ru
      "جب الأسود", // ar
      "शेरों की माँद", // hi
      "獅子坑","狮子坑", // zh
      "사자굴","사자 굴", // ko
      "ライオンの穴" // ja
    )
    // Jonah (jonah-1/2)
    pin(listOf("jonah-1","jonah-2"),
      "jonas","ballena","pez", // es
      "jonas","baleine","poisson", // fr
      "jona","wal","fisch", // de
      "giona","balena","pesce", // it
      "jonas","baleia","peixe", // pt
      "иона","кит","рыба", // ru
      "يونس","الحوت", // ar
      "योना","मछली", // hi
      "約拿","约拿","大魚","大鱼", // zh
      "요나","큰 물고기", // ko
      "ヨナ","大きな魚" // ja
    )
    // Resurrection body (1-corinthians-15)
    pin(listOf("1-corinthians-15"),
      "cuerpo resucitado","cuerpo glorificado", // es
      "corps ressuscite","corps glorifie", // fr
      "auferstehungsleib", // de
      "corpo risorto","corpo glorificato", // it
      "corpo ressuscitado", // pt
      "тело воскресения","воскресение мёртвых", // ru
      "جسد القيامة", // ar
      "पुनरुत्थान का शरीर", // hi
      "復活的身體","复活的身体", // zh
      "부활의 몸", // ko
      "復活のからだ" // ja
    )

  }

  private fun buildTierBPins1(): Map<String, List<String>> = buildMap {
    // ── Core Theology ──
    pin(listOf("romans-3","romans-5","ephesians-2","titus-2","2_corinthians-12","john-1"),
      "grace","gracia","grace","gnade","grazia","graca", // en/es/fr/de/it/pt
      "благодать", // ru
      "نعمة", // ar
      "अनुग्रह", // hi
      "恩典", // zh
      "은혜", // ko
      "恵み" // ja
    )
    pin(listOf("hebrews-11","romans-4","james-2","genesis-15","habakkuk-2","mark-11","matthew-17"),
      "faith","fe","foi","glaube","fede","fe", // en/es/fr/de/it/pt
      "вера", // ru
      "إيمان", // ar
      "विश्वास", // hi
      "信心","信仰", // zh
      "믿음", // ko
      "信仰" // ja
    )
    pin(listOf("romans-3","romans-5","ephesians-2","acts-4","acts-16","john-3","titus-3"),
      "salvation","saved","salvacion","salut","erlosung","heil","salvezza","salvacao", // en/es/fr/de/it/pt
      "спасение", // ru
      "خلاص", // ar
      "उद्धार", // hi
      "救恩","得救", // zh
      "구원", // ko
      "救い" // ja
    )
    pin(listOf("romans-3","galatians-2","galatians-3","romans-5","philippians-3"),
      "justification","justified","justify","justificacion","justification","rechtfertigung","giustificazione","justificacao",
      "оправдание", // ru
      "التبرير", // ar
      "धर्मीकरण", // hi
      "稱義","称义", // zh
      "칭의", // ko
      "義認" // ja
    )
    pin(listOf("1_thessalonians-4","1_thessalonians-5","hebrews-12","1_peter-1","romans-6"),
      "sanctification","sanctify","holy","santificacion","sanctification","heiligung","santificazione","santificacao",
      "освящение", // ru
      "التقديس", // ar
      "पवित्रीकरण", // hi
      "成聖","成圣", // zh
      "성화", // ko
      "聖化" // ja
    )
    pin(listOf("romans-3","hebrews-2","1_john-2","1_john-4","romans-5"),
      "propitiation","expiation","atonement","propiciacion","propitiation","suhne","propiziazione","propiciacao",
      "умилостивление", // ru
      "الكفارة", // ar
      "प्रायश्चित्त", // hi
      "挽回祭", // zh
      "화목제물", // ko
      "なだめの供え物" // ja
    )
    pin(listOf("colossians-1","romans-5","2_corinthians-5","ephesians-2"),
      "reconciliation","reconcile","reconciliacion","reconciliation","versohnung","riconciliazione","reconciliacao",
      "примирение", // ru
      "المصالحة", // ar
      "मेल-मिलाप", // hi
      "和好", // zh
      "화목", // ko
      "和解" // ja
    )
    pin(listOf("ruth-3","ruth-4","isaiah-44","isaiah-49","revelation-5","ephesians-1","galatians-3","galatians-4"),
      "redemption","redeem","redeemer","redencion","redemption","erlosung","redenzione","redencao",
      "искупление","искупитель", // ru
      "الفداء","الفادي", // ar
      "छुटकारा","छुड़ानेवाला", // hi
      "救贖","救赎","救贖主","救赎主", // zh
      "구속","구속자", // ko
      "贖い","贖い主" // ja
    )
    pin(listOf("matthew-18","luke-15","colossians-3","psalms-51","matthew-6","ephesians-4","1_john-1"),
      "forgiveness","forgive","forgiven","perdon","pardon","vergebung","perdono","perdao",
      "прощение","простить", // ru
      "مغفرة","غفران", // ar
      "क्षमा","माफ़ी", // hi
      "饒恕","饶恕","赦免", // zh
      "용서", // ko
      "赦し" // ja
    )
    pin(listOf("2_peter-3","luke-13","acts-2","acts-3","acts-17","revelation-2","revelation-3"),
      "repentance","repent","arrepentimiento","repentir","busse","reue","pentimento","arrependimento",
      "покаяние","покайтесь", // ru
      "توبة", // ar
      "पश्चाताप","मन फिराव", // hi
      "悔改", // zh
      "회개", // ko
      "悔い改め" // ja
    )
    pin(listOf("1-corinthians-13","john-3","john-15","romans-5","1_john-4","matthew-22","deuteronomy-6"),
      "love","amor","amour","liebe","amore","amor", // en/es/fr/de/it/pt
      "любовь", // ru
      "محبة","حب", // ar
      "प्रेम","प्यार", // hi
      "愛","爱", // zh
      "사랑", // ko
      "愛" // ja
    )
    pin(listOf("romans-5","romans-8","1-corinthians-15","1_peter-1","hebrews-6","titus-2"),
      "hope","esperanza","esperance","hoffnung","speranza","esperanca",
      "надежда", // ru
      "رجاء", // ar
      "आशा", // hi
      "盼望", // zh
      "소망", // ko
      "希望" // ja
    )
    pin(listOf("matthew-5","matthew-18","psalms-103","luke-6","micah-6","james-2"),
      "mercy","merciful","misericordia","misericorde","barmherzigkeit","misericordia","misericordia",
      "милость","милосердие", // ru
      "رحمة", // ar
      "दया","करुणा", // hi
      "憐憫","怜悯", // zh
      "긍휼","자비", // ko
      "憐れみ","慈悲" // ja
    )

    // ── Holy Spirit ──
    pin(listOf("acts-2","john-14","john-16","romans-8","galatians-5","1-corinthians-12","ephesians-5"),
      "holy spirit","spirit","espiritu santo","saint esprit","heiliger geist","spirito santo","espirito santo",
      "святой дух","дух", // ru
      "الروح القدس","روح", // ar
      "पवित्र आत्मा","आत्मा", // hi
      "聖靈","圣灵", // zh
      "성령", // ko
      "聖霊" // ja
    )
    pin(listOf("1-corinthians-12","1-corinthians-14","romans-12","ephesians-4"),
      "spiritual gifts","gifts","charismata","dones espirituales","dons spirituels","geistesgaben","doni spirituali","dons espirituais",
      "духовные дары","дары", // ru
      "المواهب الروحية","مواهب", // ar
      "आत्मिक वरदान","वरदान", // hi
      "屬靈恩賜","属灵恩赐","恩賜","恩赐", // zh
      "은사","성령의 은사", // ko
      "御霊の賜物","賜物" // ja
    )

    // ── Eschatology ──
    pin(listOf("1_thessalonians-4","1-corinthians-15","matthew-24","revelation-20"),
      "rapture","arrebatamiento","enlevement","entruckung","rapimento","arrebatamento",
      "восхищение", // ru
      "الاختطاف", // ar
      "उठा लिया जाना", // hi
      "被提", // zh
      "휴거", // ko
      "携挙" // ja
    )
    pin(listOf("matthew-24","revelation-7","daniel-12","mark-13","2_thessalonians-2"),
      "tribulation","great tribulation","tribulacion","tribulation","trubsal","tribolazione","tribulacao",
      "скорбь","великая скорбь", // ru
      "ضيقة","الضيقة العظيمة", // ar
      "क्लेश","महा क्लेश", // hi
      "大災難","大灾难", // zh
      "환난","대환난", // ko
      "患難","大患難" // ja
    )
    pin(listOf("revelation-20","isaiah-11","isaiah-65","revelation-20"),
      "millennium","thousand years","milenio","millenaire","tausendjahrige","millennio","milenio",
      "тысячелетие","тысячелетнее царство", // ru
      "الألفية","ألف سنة", // ar
      "सहस्राब्दी","हज़ार वर्ष", // hi
      "千禧年","千年國度","千年国度", // zh
      "천년왕국","천년", // ko
      "千年王国" // ja
    )
    pin(listOf("matthew-24","matthew-25","acts-1","1_thessalonians-4","revelation-19","2_peter-3"),
      "second coming","return","parousia","segunda venida","second avenement","wiederkunft","seconda venuta","segunda vinda",
      "второе пришествие", // ru
      "المجيء الثاني","العودة", // ar
      "दूसरा आगमन", // hi
      "再來","再来","再臨", // zh
      "재림", // ko
      "再臨" // ja
    )
    pin(listOf("revelation-16","revelation-19","joel-3","zechariah-14"),
      "armageddon","har megiddo","armagedon","armagueddon","harmagedon","armageddon","armagedom",
      "армагеддон", // ru
      "هرمجدون", // ar
      "हर-मगिदोन", // hi
      "哈米吉多頓","哈米吉多顿", // zh
      "아마겟돈", // ko
      "ハルマゲドン" // ja
    )
    pin(listOf("matthew-25","revelation-20","hebrews-9","2_corinthians-5","romans-14","ecclesiastes-12"),
      "judgment","judgment day","juicio","jugement","gericht","giudizio","julgamento","juizo",
      "суд","судный день", // ru
      "الدينونة","يوم الدين", // ar
      "न्याय","न्याय का दिन", // hi
      "審判","审判","審判日","审判日", // zh
      "심판","심판의 날", // ko
      "裁き","裁きの日" // ja
    )

    // ── Afterlife ──
    pin(listOf("revelation-21","revelation-22","john-14","2_corinthians-5","philippians-1","1_thessalonians-4"),
      "heaven","cielo","ciel","himmel","cielo","ceu","paradis","paraiso",
      "небо","рай","небеса", // ru
      "السماء","الجنة","الفردوس", // ar
      "स्वर्ग","परलोक", // hi
      "天堂","天國","天国","樂園","乐园", // zh
      "천국","하늘나라","낙원", // ko
      "天国","天","楽園" // ja
    )
    pin(listOf("matthew-25","revelation-20","luke-16","mark-9","matthew-10","2_peter-2"),
      "hell","gehenna","sheol","hades","infierno","enfer","holle","inferno","inferno",
      "ад","геенна","шеол","преисподняя", // ru
      "جهنم","الهاوية","الجحيم", // ar
      "नरक","गेहन्ना","अधोलोक", // hi
      "地獄","地狱","陰間","阴间", // zh
      "지옥","게헨나","스올","음부", // ko
      "地獄","ゲヘナ","よみ","ハデス" // ja
    )
    pin(listOf("revelation-20","revelation-21","matthew-25"),
      "lake of fire","lago de fuego","etang de feu","feuersee","lago di fuoco","lago de fogo",
      "озеро огненное", // ru
      "بحيرة النار", // ar
      "आग की झील", // hi
      "火湖", // zh
      "불못", // ko
      "火の池" // ja
    )

    // ── Ethics / Virtues ──
    pin(listOf("galatians-5","colossians-3","ephesians-4","2_peter-1","philippians-4"),
      "patience","kindness","goodness","gentleness","self-control",
      "paciencia","bondad","mansedumbre","dominio propio", // es
      "patience","bonte","douceur","maitrise de soi", // fr
      "geduld","gute","sanftmut","selbstbeherrschung", // de
      "pazienza","bonta","mitezza","autocontrollo", // it
      "paciencia","bondade","mansidao","dominio proprio", // pt
      "терпение","благость","кротость","воздержание", // ru
      "صبر","لطف","وداعة","تعفف", // ar
      "धीरज","भलाई","नम्रता","संयम", // hi
      "忍耐","恩慈","溫柔","温柔","節制","节制", // zh
      "인내","자비","온유","절제", // ko
      "忍耐","慈愛","柔和","自制" // ja
    )
    pin(listOf("philippians-2","matthew-23","1_peter-5","james-4","proverbs-22","proverbs-11"),
      "humility","humble","humildad","humilite","demut","umilta","humildade",
      "смирение","смиренный", // ru
      "تواضع","متواضع", // ar
      "नम्रता","दीनता", // hi
      "謙卑","谦卑", // zh
      "겸손", // ko
      "謙遜","へりくだり" // ja
    )
    pin(listOf("micah-6","isaiah-1","amos-5","psalms-82","proverbs-21","matthew-23"),
      "justice","righteousness","justicia","justice","gerechtigkeit","giustizia","justica",
      "справедливость","правда", // ru
      "عدالة","عدل", // ar
      "न्याय","धार्मिकता", // hi
      "公義","公义","公正", // zh
      "정의","공의", // ko
      "正義","義" // ja
    )

    // ── Spiritual Life ──
    pin(listOf("matthew-6","luke-11","1_thessalonians-5","philippians-4","psalms-5","james-5","ephesians-6"),
      "prayer","pray","oracion","priere","gebet","preghiera","oracao",
      "молитва","молиться", // ru
      "صلاة","صلوا", // ar
      "प्रार्थना","प्रार्थना करो", // hi
      "禱告","祷告","祈禱","祈祷", // zh
      "기도", // ko
      "祈り","祈る" // ja
    )
    pin(listOf("john-4","psalms-95","psalms-100","psalms-150","revelation-4","revelation-5","romans-12"),
      "worship","adoracion","adoration","anbetung","adorazione","adoracao",
      "поклонение","поклоняться", // ru
      "عبادة","سجود", // ar
      "आराधना","उपासना", // hi
      "敬拜", // zh
      "예배","경배", // ko
      "礼拝","崇拝" // ja
    )
    pin(listOf("matthew-28","mark-16","acts-1","romans-10","matthew-10","2_timothy-4"),
      "evangelism","gospel","preach","evangelismo","evangelisation","evangelisation","evangelizzazione","evangelismo",
      "евангелизм","проповедь","евангелие", // ru
      "الكرازة","التبشير","الإنجيل", // ar
      "सुसमाचार प्रचार","सुसमाचार", // hi
      "傳福音","传福音","福音", // zh
      "전도","복음", // ko
      "伝道","福音" // ja
    )
    pin(listOf("matthew-28","matthew-4","luke-14","john-8","2_timothy-2","acts-14"),
      "discipleship","disciple","discipulado","formation","jungerschaft","discepolato","discipulado",
      "ученичество","ученик", // ru
      "التلمذة","تلميذ", // ar
      "शिष्यता","शिष्य","चेला", // hi
      "門徒訓練","门徒训练","門徒","门徒", // zh
      "제자훈련","제자도","제자", // ko
      "弟子訓練","弟子" // ja
    )

    // ── Relationships ──
    pin(listOf("genesis-2","ephesians-5","matthew-19","1-corinthians-7","hebrews-13","proverbs-18"),
      "marriage","husband","wife","matrimonio","mariage","ehe","matrimonio","casamento","matrimonio",
      "брак","муж","жена", // ru
      "زواج","زوج","زوجة", // ar
      "विवाह","पति","पत्नी", // hi
      "婚姻","丈夫","妻子", // zh
      "결혼","남편","아내", // ko
      "結婚","夫","妻" // ja
    )
    pin(listOf("matthew-19","malachi-2","deuteronomy-24","1-corinthians-7","mark-10"),
      "divorce","divorcio","divorce","scheidung","divorzio","divorcio",
      "развод", // ru
      "طلاق", // ar
      "तलाक", // hi
      "離婚","离婚", // zh
      "이혼", // ko
      "離婚" // ja
    )
    pin(listOf("deuteronomy-6","ephesians-6","proverbs-22","psalms-127","psalms-128","colossians-3"),
      "family","children","parenting","familia","famille","familie","famiglia","familia",
      "семья","дети","воспитание", // ru
      "عائلة","أولاد","تربية", // ar
      "परिवार","बच्चे","पालन-पोषण", // hi
      "家庭","兒女","儿女","教養","教养", // zh
      "가정","자녀","양육", // ko
      "家族","子ども","育児" // ja
    )

    // ── Church / Sacrament ──
    pin(listOf("matthew-16","acts-2","1-corinthians-12","ephesians-4","ephesians-5","colossians-1"),
      "church","body of christ","iglesia","eglise","kirche","chiesa","igreja",
      "церковь","тело христово", // ru
      "كنيسة","جسد المسيح", // ar
      "कलीसिया","मसीह की देह", // hi
      "教會","教会","基督的身體","基督的身体", // zh
      "교회","그리스도의 몸", // ko
      "教会","キリストの体" // ja
    )
    pin(listOf("matthew-26","mark-14","luke-22","1-corinthians-11","acts-2"),
      "communion","eucharist","lords supper","bread","wine","cup",
      "comunion","eucaristia","pan","vino","copa", // es
      "communion","eucharistie","pain","vin","coupe", // fr
      "abendmahl","brot","wein","kelch", // de
      "comunione","eucaristia","pane","vino","calice", // it
      "comunhao","eucaristia","pao","vinho","calice", // pt
      "причастие","евхаристия","хлеб","вино","чаша", // ru
      "شركة","عشاء الرب","خبز","خمر","كأس", // ar
      "प्रभु भोज","रोटी","दाखरस","कटोरा", // hi
      "聖餐","圣餐","餅","饼","葡萄酒","杯", // zh
      "성찬","떡","포도주","잔", // ko
      "聖餐","パン","ぶどう酒","杯" // ja
    )

    // ── Supernatural / Miracles ──
    pin(listOf("john-2","matthew-8","matthew-9","mark-5","luke-7","acts-3","exodus-14"),
      "miracle","miracles","milagro","milagros","miracle","miracles","wunder","miracolo","miracoli","milagre","milagres",
      "чудо","чудеса", // ru
      "معجزة","معجزات", // ar
      "चमत्कार", // hi
      "神蹟","神迹","奇蹟","奇迹", // zh
      "기적","이적", // ko
      "奇跡","奇蹟" // ja
    )
    pin(listOf("matthew-8","matthew-9","mark-5","luke-4","luke-7","james-5","acts-3","acts-14"),
      "healing","heal","healed","sanidad","guerison","heilung","guarigione","cura",
      "исцеление","исцелить", // ru
      "شفاء", // ar
      "चंगाई","चंगा करना", // hi
      "醫治","医治","治癒","治愈", // zh
      "치유","고침", // ko
      "いやし","癒し" // ja
    )

    // ── Covenant / Promise ──
    pin(listOf("genesis-15","genesis-17","exodus-19","jeremiah-31","hebrews-8","hebrews-9","2-samuel-7"),
      "covenant","promise","alliance","pact","pacto","alianza","alliance","bund","alleanza","patto","alianca","pacto",
      "завет","обещание", // ru
      "عهد","وعد","ميثاق", // ar
      "वाचा","वायदा", // hi
      "約","约","盟約","盟约","應許","应许", // zh
      "언약","약속", // ko
      "契約","約束" // ja
    )

    // ── Kingdom ──
    pin(listOf("matthew-13","matthew-6","mark-1","luke-17","john-3","john-18","daniel-2","daniel-7"),
      "kingdom of god","kingdom of heaven","kingdom","reino de dios","royaume de dieu","reich gottes","regno di dio","reino de deus",
      "царство божие","царство небесное", // ru
      "ملكوت الله","ملكوت السماوات", // ar
      "परमेश्वर का राज्य","स्वर्ग का राज्य", // hi
      "神的國","神的国","天國","天国", // zh
      "하나님 나라","천국", // ko
      "神の国","天の御国" // ja
    )

    // ── Christology ──
    pin(listOf("john-1","john-4","matthew-16","acts-2","daniel-7","isaiah-9","revelation-19","philippians-2"),
      "messiah","christ","lord","king of kings","son of god","son of man","lamb of god",
      "mesias","cristo","senor","rey de reyes","hijo de dios","hijo del hombre","cordero de dios", // es
      "messie","seigneur","roi des rois","fils de dieu","fils de l'homme","agneau de dieu", // fr
      "messias","christus","herr","konig der konige","sohn gottes","menschensohn","lamm gottes", // de
      "messia","signore","re dei re","figlio di dio","figlio dell'uomo","agnello di dio", // it
      "messias","senhor","rei dos reis","filho de deus","filho do homem","cordeiro de deus", // pt
      "мессия","христос","господь","царь царей","сын божий","сын человеческий","агнец божий", // ru
      "المسيح","المسيا","رب","ملك الملوك","ابن الله","ابن الإنسان","حمل الله", // ar
      "मसीहा","मसीह","प्रभु","राजाओं का राजा","परमेश्वर का पुत्र","मनुष्य का पुत्र","परमेश्वर का मेम्ना", // hi
      "彌賽亞","弥赛亚","基督","主","萬王之王","万王之王","神的兒子","神的儿子","人子","神的羔羊", // zh
      "메시아","그리스도","주","만왕의 왕","하나님의 아들","인자","하나님의 어린양", // ko
      "メシア","キリスト","主","王の王","神の子","人の子","神の小羊" // ja
    )
    pin(listOf("john-8","john-10","john-11","john-14","john-6","john-15","exodus-3"),
      "i am","yo soy","je suis","ich bin","io sono","eu sou",
      "я есмь", // ru
      "أنا هو", // ar
      "मैं हूँ", // hi
      "我是", // zh
      "나는 ~이다", // ko
      "わたしはある" // ja
    )

    // ── Nature of God ──
    pin(listOf("psalms-139","jeremiah-23","1-kings-8","isaiah-40","psalms-90","revelation-1","revelation-4"),
      "omnipotent","omniscient","omnipresent","sovereign","eternal","almighty","todopoderoso","omnipotente","eternel","souverain","allmachtig","ewig",
      "всемогущий","всеведущий","вездесущий","вечный", // ru
      "كلي القدرة","كلي العلم","كلي الحضور","سيد","أزلي", // ar
      "सर्वशक्तिमान","सर्वज्ञ","सर्वव्यापी","संप्रभु","अनन्त", // hi
      "全能","全知","全在","至高","永恆","永恒", // zh
      "전능","전지","편재","주권","영원", // ko
      "全能","全知","遍在","主権","永遠" // ja
    )

    // ── Angels / Demons ──
    pin(listOf("genesis-3","isaiah-14","ezekiel-28","revelation-12","matthew-4","job-1","luke-10","1_peter-5"),
      "satan","devil","lucifer","enemy","adversary","satanas","diablo","satan","diable","satan","teufel","satana","diavolo","satanas","diabo",
      "сатана","дьявол","люцифер", // ru
      "الشيطان","إبليس", // ar
      "शैतान","इब्लीस", // hi
      "撒但","撒旦","魔鬼", // zh
      "사탄","마귀", // ko
      "サタン","悪魔" // ja
    )
    pin(listOf("hebrews-1","psalms-91","matthew-18","luke-15","genesis-28","revelation-12","daniel-10"),
      "angel","angels","guardian","messenger","angel","angeles","ange","anges","engel","angelo","angeli","anjo","anjos",
      "ангел","ангелы", // ru
      "ملاك","ملائكة", // ar
      "स्वर्गदूत","दूत", // hi
      "天使", // zh
      "천사", // ko
      "天使","み使い" // ja
    )
    pin(listOf("mark-5","matthew-8","luke-8","ephesians-6","luke-11","revelation-12"),
      "demon","demons","unclean spirit","evil spirit","demonio","demonios","demon","demons","damon","damonen","demonio","demoni","demonio","demonios",
      "демон","бес","нечистый дух","злой дух", // ru
      "شيطان","شياطين","روح نجس","روح شرير", // ar
      "दुष्टात्मा","अशुद्ध आत्मा", // hi
      "鬼","邪靈","邪灵","污鬼", // zh
      "귀신","악령","더러운 영", // ko
      "悪霊","悪鬼","汚れた霊" // ja
    )

    // ── Worship / Sacrifice ──
    pin(listOf("psalms-100","psalms-150","psalms-95","hebrews-13","ephesians-5","colossians-3"),
      "praise","thanksgiving","alabanza","accion de gracias","louange","action de grace","lobpreis","dank","lode","ringraziamento","louvor","acao de gracas",
      "хвала","благодарение", // ru
      "تسبيح","شكر", // ar
      "स्तुति","धन्यवाद", // hi
      "讚美","赞美","感恩", // zh
      "찬양","감사", // ko
      "賛美","感謝" // ja
    )
    pin(listOf("genesis-22","leviticus-1","hebrews-10","romans-12","hebrews-13","psalms-51"),
      "sacrifice","offering","altar","sacrificio","ofrenda","sacrifice","offrande","opfer","sacrificio","offerta","sacrificio","oferta",
      "жертва","жертвоприношение","алтарь", // ru
      "ذبيحة","قربان","مذبح", // ar
      "बलिदान","भेंट","वेदी", // hi
      "祭物","獻祭","献祭","祭壇","祭坛", // zh
      "제사","제물","제단", // ko
      "犠牲","ささげ物","祭壇" // ja
    )

    // ── Law / Torah ──
    pin(listOf("exodus-20","deuteronomy-5","matthew-5","romans-7","galatians-3","psalms-119","deuteronomy-6"),
      "law","torah","commandment","statute","ley","loi","gesetz","legge","lei",
      "закон","тора","заповедь", // ru
      "شريعة","توراة","ناموس","وصية", // ar
      "व्यवस्था","तोराह","आज्ञा", // hi
      "律法","妥拉","誡命","诫命", // zh
      "율법","토라","계명", // ko
      "律法","トーラー","戒め" // ja
    )

    // ── Suffering / Persecution ──
    pin(listOf("james-1","1_peter-4","romans-8","2_corinthians-4","2_timothy-3","matthew-5","philippians-1"),
      "suffering","persecution","trial","affliction","sufrimiento","persecucion","prueba","souffrance","persecution","epreuve","leiden","verfolgung","sofferenza","persecuzione","sofrimento","perseguicao",
      "страдание","гонение","испытание", // ru
      "ألم","اضطهاد","تجربة", // ar
      "दुख","सताव","परीक्षा", // hi
      "苦難","苦难","逼迫","試煉","试炼", // zh
      "고난","박해","시련", // ko
      "苦難","迫害","試練" // ja
    )

    // ── Wisdom ──
    pin(listOf("proverbs-1","proverbs-2","proverbs-8","proverbs-9","james-1","james-3","1-kings-3","ecclesiastes-1"),
      "wisdom","understanding","discernment","prudence","sabiduria","sagesse","weisheit","saggezza","sabedoria",
      "мудрость","разумение","проницательность", // ru
      "حكمة","فهم","تمييز", // ar
      "बुद्धि","समझ","विवेक", // hi
      "智慧","聰明","聪明","分辨", // zh
      "지혜","명철","분별", // ko
      "知恵","悟り","見分け" // ja
    )

    // ── Sin ──
    pin(listOf("romans-3","romans-6","genesis-3","1_john-1","james-1","psalms-51","isaiah-59"),
      "sin","sinful","sinner","pecado","peche","sunde","peccato","pecado",
      "грех","грешник","грешный", // ru
      "خطيئة","خطية","خاطئ", // ar
      "पाप","पापी", // hi
      "罪","罪人", // zh
      "죄","죄인", // ko
      "罪","罪人" // ja
    )
    pin(listOf("exodus-20","1-kings-18","romans-1","1-corinthians-10","1_john-5","deuteronomy-4"),
      "idolatry","idol","idols","false god","idolatria","idolatrie","gotzendienst","idolatria","idolatria",
      "идолопоклонство","идол","кумир", // ru
      "عبادة الأصنام","صنم","أصنام", // ar
      "मूर्तिपूजा","मूर्ति", // hi
      "偶像崇拜","偶像", // zh
      "우상숭배","우상", // ko
      "偶像崇拝","偶像" // ja
    )

    // ── Sabbath ──
    pin(listOf("genesis-2","exodus-20","exodus-31","isaiah-58","mark-2","matthew-12","hebrews-4"),
      "sabbath","rest","day of rest","sabado","sabbat","sabbat","sabato","sabado",
      "суббота","покой", // ru
      "السبت","راحة", // ar
      "सब्त","विश्राम", // hi
      "安息日","安息", // zh
      "안식일","안식", // ko
      "安息日","休息" // ja
    )

    // ── Tithing / Stewardship ──
    pin(listOf("malachi-3","genesis-14","matthew-23","luke-18","2_corinthians-9","1-corinthians-16"),
      "tithe","tithing","offering","generosity","giving","diezmo","dime","zehnten","decima","dizimo",
      "десятина","пожертвование","щедрость", // ru
      "عشور","عطاء","سخاء", // ar
      "दशमांश","दान","उदारता", // hi
      "十一奉獻","十一奉献","十分之一","奉獻","奉献", // zh
      "십일조","헌금","관대함", // ko
      "什一","什一献金","ささげ物" // ja
    )
  }

  private fun buildTierBPins2(): Map<String, List<String>> = buildMap {
    // ── Prophecy ──
    pin(listOf("1-corinthians-14","joel-2","acts-2","revelation-1","amos-3","deuteronomy-18","2_peter-1"),
      "prophecy","prophet","prophetic","profecia","profeta","prophetie","prophete","prophezeiung","prophet","profezia","profeta","profecia","profeta",
      "пророчество","пророк","пророческий", // ru
      "نبوة","نبي","نبوي", // ar
      "भविष्यवाणी","भविष्यद्वक्ता", // hi
      "預言","预言","先知", // zh
      "예언","선지자", // ko
      "預言","預言者" // ja
    )

    // ── Blood ──
    pin(listOf("hebrews-9","leviticus-17","exodus-12","1_peter-1","ephesians-1","revelation-12","1_john-1"),
      "blood","blood of christ","blood of lamb","sangre","sang","blut","sangue","sangue",
      "кровь","кровь христова","кровь агнца", // ru
      "دم","دم المسيح","دم الحمل", // ar
      "लहू","मसीह का लहू","मेम्ने का लहू", // hi
      "血","基督的血","羔羊的血", // zh
      "피","그리스도의 피","어린양의 피", // ko
      "血","キリストの血","小羊の血" // ja
    )

    // ── Resurrection of the dead ──
    pin(listOf("1-corinthians-15","1_thessalonians-4","john-5","john-11","revelation-20","daniel-12","ezekiel-37"),
      "resurrection of dead","resurrection of body","raise","raised","resurreccion de muertos","resurrection des morts","auferstehung der toten","risurrezione dei morti","ressurreicao dos mortos",
      "воскресение мертвых", // ru
      "قيامة الأموات", // ar
      "मुर्दों का पुनरुत्थान", // hi
      "死人復活","死人复活", // zh
      "죽은 자의 부활", // ko
      "死者の復活" // ja
    )

    // ── Baptism of Holy Spirit ──
    pin(listOf("acts-2","acts-10","acts-19","1-corinthians-12","matthew-3","mark-1","joel-2"),
      "baptism of spirit","filled with spirit","bautismo del espiritu","bapteme du saint-esprit","geistestaufe","battesimo dello spirito","batismo do espirito",
      "крещение духом","исполнение духом", // ru
      "معمودية الروح","امتلاء بالروح", // ar
      "आत्मा का बपतिस्मा","आत्मा से भरना", // hi
      "聖靈的洗","圣灵的洗","被聖靈充滿","被圣灵充满", // zh
      "성령세례","성령충만", // ko
      "聖霊のバプテスマ","聖霊に満たされる" // ja
    )

    // ── Obedience / Disobedience ──
    pin(listOf("deuteronomy-28","1-samuel-15","john-14","john-15","acts-5","hebrews-5","james-1"),
      "obedience","obey","disobedience","obediencia","obedecer","obeissance","obeir","gehorsam","gehorchen","obbedienza","obbedire","obediencia","obedecer",
      "послушание","непослушание", // ru
      "طاعة","عصيان", // ar
      "आज्ञाकारिता","अनाज्ञाकारिता", // hi
      "順服","顺服","順從","顺从","悖逆", // zh
      "순종","불순종", // ko
      "従順","不従順" // ja
    )

    // ── Word of God ──
    pin(listOf("john-1","hebrews-4","2_timothy-3","psalms-119","isaiah-55","matthew-4","revelation-19"),
      "word of god","scripture","bible","palabra de dios","escritura","parole de dieu","ecriture","wort gottes","schrift","parola di dio","scrittura","palavra de deus","escritura",
      "слово божие","писание","библия", // ru
      "كلمة الله","الكتاب المقدس", // ar
      "परमेश्वर का वचन","पवित्रशास्त्र","बाइबल", // hi
      "神的話","神的话","聖經","圣经", // zh
      "하나님의 말씀","성경", // ko
      "神の言葉","聖書" // ja
    )

    // ── Promised Land ──
    pin(listOf("joshua-1","deuteronomy-34","genesis-12","genesis-15","numbers-13","numbers-14","hebrews-11"),
      "promised land","canaan","tierra prometida","terre promise","verheissenes land","terra promessa","terra prometida",
      "земля обетованная","ханаан", // ru
      "أرض الميعاد","كنعان", // ar
      "प्रतिज्ञा की भूमि","कनान", // hi
      "應許之地","应许之地","迦南", // zh
      "약속의 땅","가나안", // ko
      "約束の地","カナン" // ja
    )

    // ── Fasting ──
    pin(listOf("matthew-4","matthew-6","isaiah-58","joel-2","acts-13","esther-4","daniel-9","luke-4"),
      "fasting","fast","abstinence","ayuno","jeune","fasten","digiuno","jejum",
      "пост","постится", // ru
      "صوم","صيام", // ar
      "उपवास","व्रत", // hi
      "禁食","齋戒","斋戒", // zh
      "금식", // ko
      "断食" // ja
    )

    // ── Baptism (general, not just Jesus') ──
    pin(listOf("matthew-28","acts-2","acts-8","acts-16","romans-6","galatians-3","1_peter-3"),
      "baptize","immersion","water baptism","bautizar","inmersion","baptiser","immersion","taufen","battezzare","immersione","batizar","imersao",
      "крестить","погружение","водное крещение", // ru
      "يعمد","الغطس","معمودية الماء", // ar
      "बपतिस्मा देना","जल बपतिस्मा", // hi
      "施洗","浸禮","浸礼","水的洗禮","水的洗礼", // zh
      "세례 주다","침례","물세례", // ko
      "洗礼を授ける","水のバプテスマ" // ja
    )

    // ── Fruit of the Spirit ──
    pin(listOf("galatians-5"),
      "fruit of the spirit","fruit","fruto del espiritu","fruit de l'esprit","frucht des geistes","frutto dello spirito","fruto do espirito",
      "плод духа", // ru
      "ثمر الروح", // ar
      "आत्मा का फल", // hi
      "聖靈的果子","圣灵的果子", // zh
      "성령의 열매", // ko
      "御霊の実" // ja
    )

    // ── Ten Commandments (concept, not just location) ──
    pin(listOf("exodus-20","deuteronomy-5","matthew-5","matthew-22","romans-13"),
      "thou shalt not","do not kill","do not steal","do not murder","no mataras","no robaras","tu ne tueras","tu ne voleras","du sollst nicht toten","du sollst nicht stehlen",
      "не убий","не укради", // ru
      "لا تقتل","لا تسرق", // ar
      "हत्या न करना","चोरी न करना", // hi
      "不可殺人","不可杀人","不可偷盜","不可偷盗", // zh
      "살인하지 말라","도둑질하지 말라", // ko
      "殺してはならない","盗んではならない" // ja
    )

    // ── Armor of God (concept) ──
    pin(listOf("ephesians-6"),
      "belt of truth","breastplate","shield of faith","helmet","sword of spirit",
      "cinturon de verdad","coraza","escudo de fe","yelmo","espada del espiritu", // es
      "ceinture de verite","cuirasse","bouclier de la foi","casque","epee de l'esprit", // fr
      "gurtel der wahrheit","brustpanzer","schild des glaubens","helm","schwert des geistes", // de
      "cintura della verita","corazza","scudo della fede","elmo","spada dello spirito", // it
      "cinto da verdade","couraca","escudo da fe","capacete","espada do espirito", // pt
      "пояс истины","броня","щит веры","шлем","меч духовный", // ru
      "حزام الحق","درع البر","ترس الإيمان","خوذة","سيف الروح", // ar
      "सत्य की कमरबंद","कवच","विश्वास की ढाल","टोप","आत्मा की तलवार", // hi
      "真理的帶子","真理的带子","護心鏡","护心镜","信德的藤牌","信德的藤牌","頭盔","头盔","聖靈的寶劍","圣灵的宝剑", // zh
      "진리의 허리띠","의의 호심경","믿음의 방패","구원의 투구","성령의 검", // ko
      "真理の帯","正義の胸当て","信仰の盾","救いのかぶと","御霊の剣" // ja
    )

    // ── Lord's Prayer ──
    pin(listOf("matthew-6","luke-11"),
      "lords prayer","our father","padre nuestro","notre pere","vater unser","padre nostro","pai nosso",
      "отче наш","молитва господня", // ru
      "أبانا","الصلاة الربانية", // ar
      "हे हमारे पिता","प्रभु की प्रार्थना", // hi
      "主禱文","主祷文","我們在天上的父","我们在天上的父", // zh
      "주기도문","하늘에 계신 우리 아버지", // ko
      "主の祈り","天にまします我らの父よ" // ja
    )

    // ── Golden Rule ──
    pin(listOf("matthew-7","luke-6"),
      "golden rule","do unto others","regla de oro","regle d'or","goldene regel","regola d'oro","regra de ouro",
      "золотое правило", // ru
      "القاعدة الذهبية", // ar
      "सुनहरा नियम", // hi
      "金律","己所不欲勿施於人", // zh
      "황금률", // ko
      "黄金律" // ja
    )

    // ── Born Again ──
    pin(listOf("john-3","1_peter-1","titus-3","2_corinthians-5"),
      "born again","new birth","new creation","nacer de nuevo","nueva creacion","ne de nouveau","nouvelle creation","wiedergeboren","neue schopfung","nato di nuovo","nuova creazione","nascido de novo","nova criacao",
      "рождение свыше","новое творение", // ru
      "الولادة الجديدة","خليقة جديدة", // ar
      "नया जन्म","नई सृष्टि", // hi
      "重生","新造的人", // zh
      "거듭남","새 피조물", // ko
      "新生","新しい創造" // ja
    )

    // ── Resurrection power / Victory over death ──
    pin(listOf("1-corinthians-15","revelation-1","romans-6","john-11","2_timothy-1","hebrews-2"),
      "victory over death","death defeated","conquered death","sting of death","victoria sobre la muerte","victoire sur la mort","sieg uber den tod","vittoria sulla morte","vitoria sobre a morte",
      "победа над смертью","жало смерти", // ru
      "النصر على الموت","شوكة الموت", // ar
      "मृत्यु पर विजय","मृत्यु का डंक", // hi
      "勝過死亡","胜过死亡","死的毒鉤","死的毒钩", // zh
      "죽음의 승리","사망의 쏘는 것", // ko
      "死に対する勝利","死のとげ" // ja
    )

    // ── Shepherd / Sheep ──
    pin(listOf("psalms-23","john-10","ezekiel-34","isaiah-40","matthew-18","luke-15","1_peter-5"),
      "shepherd","sheep","flock","lost sheep","pastor","oveja","rebano","oveja perdida","berger","brebis","troupeau","hirte","schafe","herde","pastore","pecora","gregge","pastor","ovelha","rebanho",
      "пастырь","овцы","стадо","заблудшая овца", // ru
      "الراعي","خروف","خراف","قطيع","الخروف الضال", // ar
      "चरवाहा","भेड़","भेड़ों","झुंड","खोई हुई भेड़", // hi
      "牧者","牧人","羊","羊群","迷羊","失羊", // zh
      "목자","양","양떼","잃은 양", // ko
      "羊飼い","羊","群れ","迷った羊" // ja
    )

    // ── Bread of Life ──
    pin(listOf("john-6","matthew-4","john-4","exodus-16"),
      "bread of life","living bread","living water","pan de vida","pan vivo","agua viva","pain de vie","pain vivant","eau vive","brot des lebens","lebendiges brot","pane della vita","pane vivo","pao da vida","pao vivo",
      "хлеб жизни","хлеб живой","вода живая", // ru
      "خبز الحياة","الخبز الحي","ماء حي", // ar
      "जीवन की रोटी","जीवित रोटी","जीवन का जल", // hi
      "生命的糧","生命的粮","活水", // zh
      "생명의 떡","산 떡","생수", // ko
      "いのちのパン","生けるパン","生ける水" // ja
    )

    // ── Light / Darkness ──
    pin(listOf("john-1","john-8","matthew-5","1_john-1","ephesians-5","isaiah-9","genesis-1"),
      "light of world","light","darkness","luz del mundo","luz","tinieblas","lumiere du monde","lumiere","tenebres","licht der welt","licht","finsternis","luce del mondo","luce","tenebre","luz do mundo","luz","trevas",
      "свет мира","свет","тьма", // ru
      "نور العالم","نور","ظلمة", // ar
      "जगत की ज्योति","ज्योति","अंधकार", // hi
      "世界的光","光","黑暗", // zh
      "세상의 빛","빛","어둠", // ko
      "世の光","光","闇" // ja
    )

    // ── Thorn in flesh ──
    pin(listOf("2_corinthians-12"),
      "thorn in flesh","weakness","strength in weakness","aguijon en la carne","echarde dans la chair","dorn im fleisch","spina nella carne","espinho na carne",
      "жало в плоть","сила в немощи", // ru
      "شوكة في الجسد","القوة في الضعف", // ar
      "शरीर में काँटा","निर्बलता में सामर्थ्य", // hi
      "肉體的刺","肉体的刺","軟弱中的力量", // zh
      "육체의 가시","약한 데서 강함", // ko
      "肉体のとげ","弱さの中の力" // ja
    )

    // ── Spiritual warfare ──
    pin(listOf("ephesians-6","2_corinthians-10","james-4","1_peter-5","revelation-12"),
      "spiritual warfare","struggle","guerra espiritual","lucha","combat spirituel","geistlicher kampf","lotta spirituale","guerra espiritual",
      "духовная война","борьба", // ru
      "الحرب الروحية","صراع", // ar
      "आत्मिक युद्ध","संघर्ष", // hi
      "屬靈爭戰","属灵争战", // zh
      "영적 전쟁","싸움", // ko
      "霊的戦い","戦い" // ja
    )

    // ── Election / Predestination ──
    pin(listOf("ephesians-1","romans-8","romans-9","1_peter-1","2_thessalonians-2","john-15"),
      "election","predestination","chosen","called","eleccion","predestinacion","election","predestination","erwahlung","vorherbestimmung","elezione","predestinazione","eleicao","predestinacao",
      "избрание","предопределение","избранный","призвание", // ru
      "اختيار","تعيين سابق","مختار","مدعو", // ar
      "चुनाव","पूर्वनियति","चुना हुआ","बुलाया हुआ", // hi
      "揀選","拣选","預定","预定", // zh
      "선택","예정","택함","부르심", // ko
      "選び","予定","選ばれた者","召し" // ja
    )

    // ── New Testament Church ──
    pin(listOf("acts-2","acts-4","acts-13","acts-15","1-corinthians-12","ephesians-4"),
      "early church","apostles","fellowship","primera iglesia","apostoles","comunion","premiere eglise","apotres","communion","urgemeinde","apostel","gemeinschaft","prima chiesa","apostoli","comunione","primeira igreja","apostolos","comunhao",
      "ранняя церковь","апостолы","общение", // ru
      "الكنيسة الأولى","الرسل","شركة", // ar
      "प्रारम्भिक कलीसिया","प्रेरित","सहभागिता", // hi
      "初期教會","初期教会","使徒","團契","团契", // zh
      "초대교회","사도","교제", // ko
      "初代教会","使徒","交わり" // ja
    )

    // ── End times signs ──
    pin(listOf("matthew-24","mark-13","luke-21","2_timothy-3","2_peter-3","revelation-6"),
      "end times","last days","signs of times","end of age",
      "ultimos tiempos","ultimos dias","senales de los tiempos","fin del mundo", // es
      "derniers temps","derniers jours","signes des temps","fin du monde", // fr
      "endzeit","letzte tage","zeichen der zeit","ende der welt", // de
      "ultimi tempi","ultimi giorni","segni dei tempi","fine del mondo", // it
      "ultimos tempos","ultimos dias","sinais dos tempos","fim do mundo", // pt
      "последние времена","последние дни","знамения времен","конец мира", // ru
      "آخر الأيام","الأيام الأخيرة","علامات الأزمنة","نهاية العالم", // ar
      "अन्तिम समय","अन्तिम दिन","समय के चिन्ह","युग का अन्त", // hi
      "末世","末日","時代的徵兆","时代的征兆","世界的末了", // zh
      "마지막 때","말세","시대의 징조","세상 끝", // ko
      "終わりの時","終わりの日","時のしるし","世の終わり" // ja
    )

    // ── Tithing continued ──
    pin(listOf("2_corinthians-8","2_corinthians-9","acts-20","luke-6","proverbs-3","proverbs-11"),
      "generosity","generous","give","giving","generosidad","dar","generosite","donner","grosszugigkeit","geben","generosita","dare","generosidade","dar",
      "щедрость","давать", // ru
      "سخاء","عطاء", // ar
      "उदारता","देना", // hi
      "慷慨","施捨","施舍","給予","给予", // zh
      "관대","주는 것","베풂", // ko
      "寛大","与える","施し" // ja
    )

    // ── Trinity ──
    pin(listOf("matthew-28","matthew-3","2_corinthians-13","john-14","john-15","john-16","genesis-1"),
      "trinity","triune","godhead","father son spirit","trinidad","trinite","dreieinigkeit","trinita","trindade",
      "троица","триединый","божество", // ru
      "الثالوث","ثالوث","لاهوت", // ar
      "त्रिएकत्व","त्रिएक","ईश्वरत्व", // hi
      "三位一體","三位一体","三一神", // zh
      "삼위일체","삼위", // ko
      "三位一体" // ja
    )

    // ── Holiness ──
    pin(listOf("leviticus-19","1_peter-1","hebrews-12","isaiah-6","revelation-4","1_thessalonians-4"),
      "holiness","holy","set apart","consecration","santidad","santo","saintete","heiligkeit","santita","santidade",
      "святость","святой","освящение", // ru
      "قداسة","قدوس","تكريس", // ar
      "पवित्रता","पवित्र","समर्पण", // hi
      "聖潔","圣洁","聖","圣","分別為聖","分别为圣", // zh
      "거룩","성결","구별", // ko
      "聖","聖さ","聖別" // ja
    )

    // ── Resurrection (general theological concept) ──
    pin(listOf("1-corinthians-15","john-5","john-11","revelation-20","daniel-12","romans-6"),
      "eternal life","everlasting life","vida eterna","vie eternelle","ewiges leben","vita eterna","vida eterna",
      "жизнь вечная","вечная жизнь", // ru
      "حياة أبدية","الحياة الأبدية", // ar
      "अनन्त जीवन", // hi
      "永生","永遠的生命", // zh
      "영생","영원한 생명", // ko
      "永遠の命","永遠のいのち" // ja
    )

    // ── Cross / Cruciform life ──
    pin(listOf("matthew-16","mark-8","luke-9","luke-14","galatians-2","galatians-6","philippians-3"),
      "take up cross","deny self","carry cross","tomar su cruz","negarse","prendre sa croix","renoncer","kreuz auf sich nehmen","sich verleugnen","prendere la croce","rinnegare","tomar sua cruz","negar-se",
      "взять крест свой","отвергнуть себя", // ru
      "احمل صليبك","أنكر ذاتك", // ar
      "अपना क्रूस उठा","अपने आप से इन्कार कर", // hi
      "背起十字架","捨己","舍己", // zh
      "자기 십자가를 지고","자기를 부인하고", // ko
      "自分の十字架を負って","自分を捨てて" // ja
    )

    // ── Great Commandment ──
    pin(listOf("matthew-22","mark-12","deuteronomy-6","leviticus-19","luke-10"),
      "great commandment","greatest commandment","love god","love neighbor","gran mandamiento","mayor mandamiento","ama a dios","ama a tu projimo","grand commandement","plus grand commandement","grosstes gebot","liebe gott","liebe deinen nachsten","grande comandamento","ama dio","ama il prossimo","grande mandamento","ama a deus","ama o proximo",
      "наибольшая заповедь","возлюби бога","возлюби ближнего", // ru
      "الوصية العظمى","أحب الله","أحب قريبك", // ar
      "सबसे बड़ी आज्ञा","परमेश्वर से प्रेम","पड़ोसी से प्रेम", // hi
      "最大的誡命","最大的诫命","愛神","爱神","愛人如己","爱人如己", // zh
      "가장 큰 계명","하나님 사랑","이웃 사랑", // ko
      "最も大切な戒め","神を愛し","隣人を愛し" // ja
    )

    // ── Beatitudes ──
    pin(listOf("matthew-5"),
      "blessed are","poor in spirit","meek","peacemakers","bienaventurados","pobres en espiritu","mansos","pacificadores","heureux","pauvres en esprit","doux","artisans de paix","selig sind","armen im geist","sanftmutigen","friedensstifter","beati","poveri in spirito","miti","operatori di pace","bem-aventurados","pobres de espirito","mansos","pacificadores",
      "блаженны","нищие духом","кроткие","миротворцы", // ru
      "طوبى","المساكين بالروح","الودعاء","صانعو السلام", // ar
      "धन्य हैं","आत्मा के दीन","नम्र","मेल कराने वाले", // hi
      "有福了","虛心的人","虚心的人","溫柔的人","温柔的人","使人和睦的人", // zh
      "복이 있나니","심령이 가난한 자","온유한 자","화평케 하는 자", // ko
      "幸いな人","心の貧しい人","柔和な人","平和をつくる人" // ja
    )

    // ── Seal / Mark (general) ──
    pin(listOf("revelation-7","ephesians-1","ephesians-4","2_corinthians-1","2_timothy-2"),
      "seal","sealed","mark","sello","sellado","marca","sceau","scelle","marque","siegel","versiegelt","zeichen","sigillo","sigillato","marchio","selo","selado","marca",
      "печать","запечатлен","знак", // ru
      "ختم","مختوم","علامة", // ar
      "मुहर","मुहर लगी","चिह्न", // hi
      "印記","印记","蓋印","盖印", // zh
      "인","인침","표", // ko
      "印","封印","しるし" // ja
    )

    // ── Joy ──
    pin(listOf("philippians-4","nehemiah-8","psalms-16","james-1","galatians-5","psalms-30","john-15"),
      "joy","joyful","rejoice","gozo","alegria","regocijo","joie","allegresse","freude","gioia","allegria","alegria","gozo",
      "радость","ликование", // ru
      "فرح","بهجة", // ar
      "आनन्द","खुशी", // hi
      "喜樂","喜乐","歡喜","欢喜", // zh
      "기쁨","즐거움", // ko
      "喜び","歓喜" // ja
    )
    // ── Peace ──
    pin(listOf("john-14","philippians-4","isaiah-26","romans-5","colossians-3","psalms-46","numbers-6"),
      "peace","shalom","paz","paix","frieden","pace","paz",
      "мир","покой", // ru
      "سلام", // ar
      "शान्ति","शालोम", // hi
      "平安","和平", // zh
      "평화","평안", // ko
      "平和","平安" // ja
    )
    // ── Fear / Do not fear ──
    pin(listOf("isaiah-41","joshua-1","psalms-23","psalms-91","2_timothy-1","deuteronomy-31","psalms-27"),
      "fear","afraid","do not fear","fear not","temor","no temas","peur","ne crains pas","furcht","furchte dich nicht","paura","non temere","medo","nao temas",
      "страх","не бойся", // ru
      "خوف","لا تخف", // ar
      "भय","डर","मत डर", // hi
      "恐懼","恐惧","不要怕", // zh
      "두려움","두려워 마라", // ko
      "恐れ","恐れるな" // ja
    )
    // ── Anxiety / Worry ──
    pin(listOf("philippians-4","matthew-6","1_peter-5","psalms-55","isaiah-41","psalms-94"),
      "anxiety","worry","anxious","worried","ansiedad","preocupacion","anxiete","inquietude","angst","sorge","ansia","preoccupazione","ansiedade","preocupacao",
      "тревога","беспокойство", // ru
      "قلق","هم", // ar
      "चिन्ता","व्याकुलता", // hi
      "焦慮","焦虑","憂慮","忧虑", // zh
      "걱정","염려", // ko
      "不安","心配" // ja
    )
    // ── Comfort / Encouragement ──
    pin(listOf("2_corinthians-1","psalms-23","isaiah-40","matthew-5","john-14","psalms-34","romans-8"),
      "comfort","encouragement","consolation","consuelo","aliento","consolation","encouragement","trost","ermutigung","consolazione","incoraggiamento","consolo","encorajamento",
      "утешение","ободрение", // ru
      "عزاء","تشجيع", // ar
      "सान्त्वना","प्रोत्साहन", // hi
      "安慰","鼓勵","鼓励", // zh
      "위로","격려", // ko
      "慰め","励まし" // ja
    )
    // ── Trust ──
    pin(listOf("proverbs-3","psalms-37","isaiah-26","jeremiah-17","psalms-56","psalms-62","psalms-125"),
      "trust","trust in god","confianza","confiar","confiance","vertrauen","fiducia","confianca","confiar",
      "доверие","упование", // ru
      "ثقة","توكل", // ar
      "भरोसा","विश्वास", // hi
      "信靠","倚靠", // zh
      "신뢰","의지", // ko
      "信頼","信用" // ja
    )
    // ── Doubt / Unbelief ──
    pin(listOf("james-1","mark-9","matthew-14","john-20","hebrews-3","jude-1"),
      "doubt","unbelief","doubting","duda","incredulidad","doute","incredulite","zweifel","unglaube","dubbio","incredulita","duvida","incredulidade",
      "сомнение","неверие", // ru
      "شك","عدم إيمان", // ar
      "सन्देह","अविश्वास", // hi
      "疑惑","懷疑","怀疑", // zh
      "의심","불신", // ko
      "疑い","不信仰" // ja
    )
    // ── Strength ──
    pin(listOf("philippians-4","isaiah-40","psalms-18","psalms-27","ephesians-6","2_corinthians-12","nehemiah-8"),
      "strength","strong","fortaleza","fuerza","force","puissance","starke","kraft","forza","forca","poder",
      "сила","крепость", // ru
      "قوة","عزيمة", // ar
      "शक्ति","बल", // hi
      "力量","剛強","刚强", // zh
      "힘","강함", // ko
      "力","強さ" // ja
    )
    // ── Blessing ──
    pin(listOf("numbers-6","genesis-12","deuteronomy-28","psalms-1","ephesians-1","james-1","genesis-1"),
      "blessing","bless","blessed","bendicion","bendecir","benediction","benir","segen","segnen","benedizione","benedire","bencao","abencoar",
      "благословение","благословить", // ru
      "بركة","بارك", // ar
      "आशीर्वाद","आशीष", // hi
      "祝福","賜福","赐福", // zh
      "축복","복", // ko
      "祝福","恵み" // ja
    )
    // ── Protection ──
    pin(listOf("psalms-91","psalms-121","psalms-18","isaiah-54","2_thessalonians-3","psalms-46","proverbs-18"),
      "protection","protect","refuge","shelter","proteccion","refugio","protection","refuge","schutz","zuflucht","protezione","rifugio","protecao","refugio",
      "защита","убежище","прибежище", // ru
      "حماية","ملجأ", // ar
      "सुरक्षा","शरण", // hi
      "保護","庇護","避難", // zh
      "보호","피난처", // ko
      "保護","避け所" // ja
    )
    // ── Pride ──
    pin(listOf("proverbs-16","james-4","1_john-2","daniel-4","proverbs-11","obadiah-1"),
      "pride","proud","arrogance","orgullo","soberbia","orgueil","arrogance","stolz","hochmut","orgoglio","superbia","orgulho","soberba",
      "гордость","гордыня", // ru
      "كبرياء","تكبر", // ar
      "घमण्ड","अहंकार", // hi
      "驕傲","骄傲","傲慢", // zh
      "교만","자만", // ko
      "高慢","傲慢" // ja
    )
    // ── Jealousy / Envy ──
    pin(listOf("james-3","galatians-5","proverbs-14","1-corinthians-13","genesis-4","genesis-37","proverbs-27"),
      "jealousy","envy","envious","celos","envidia","jalousie","envie","eifersucht","neid","gelosia","invidia","ciume","inveja",
      "зависть","ревность", // ru
      "حسد","غيرة", // ar
      "ईर्ष्या","जलन", // hi
      "嫉妒","妒忌", // zh
      "질투","시기", // ko
      "嫉妬","ねたみ" // ja
    )
    // ── Anger / Wrath ──
    pin(listOf("james-1","ephesians-4","proverbs-15","romans-12","nahum-1","proverbs-29","colossians-3"),
      "anger","wrath","angry","ira","colera","enojo","colere","courroux","zorn","wut","ira","collera","ira","raiva",
      "гнев","ярость", // ru
      "غضب","سخط", // ar
      "क्रोध","गुस्सा", // hi
      "憤怒","忿怒", // zh
      "분노","진노", // ko
      "怒り","憤り" // ja
    )
    // ── Lust / Sexual Immorality ──
    pin(listOf("matthew-5","1_john-2","james-1","2-samuel-11","galatians-5","1-corinthians-6","1_thessalonians-4"),
      "lust","sexual immorality","fornication","lujuria","inmoralidad sexual","fornication","luxure","immoralite","lust","unzucht","lussuria","immoralita","luxuria","imoralidade",
      "похоть","блуд", // ru
      "شهوة","زنا", // ar
      "वासना","व्यभिचार", // hi
      "情慾","情欲","淫亂","淫乱", // zh
      "정욕","음행", // ko
      "情欲","淫行" // ja
    )
    // ── Lying / Deceit ──
    pin(listOf("proverbs-12","colossians-3","exodus-20","revelation-21","john-8","proverbs-6","ephesians-4"),
      "lying","lie","lies","deceit","deception","mentira","engano","mensonge","tromperie","luge","betrug","menzogna","inganno","mentira","engano",
      "ложь","обман", // ru
      "كذب","خداع", // ar
      "झूठ","छल", // hi
      "謊言","欺騙","谎言","欺骗", // zh
      "거짓","속임", // ko
      "嘘","偽り" // ja
    )
    // ── Greed / Covetousness ──
    pin(listOf("luke-12","1_timothy-6","hebrews-13","exodus-20","ecclesiastes-5","colossians-3"),
      "greed","covet","covetousness","avaricia","codicia","avarice","cupidite","habgier","geiz","avarizia","cupidigia","avareza","cobica",
      "жадность","алчность","сребролюбие", // ru
      "طمع","جشع", // ar
      "लालच","लोभ", // hi
      "貪婪","貪心","贪婪","贪心", // zh
      "탐욕","탐심", // ko
      "貪欲","むさぼり" // ja
    )
    // ── Adultery ──
    pin(listOf("matthew-5","exodus-20","proverbs-6","john-8","hebrews-13","hosea-1"),
      "adultery","adulterer","adulterio","adultere","ehebruch","adulterio","adulterio",
      "прелюбодеяние", // ru
      "زنا","فاحشة", // ar
      "व्यभिचार", // hi
      "姦淫","奸淫", // zh
      "간음", // ko
      "姦淫" // ja
    )
    // ── Drunkenness / Wine ──
    pin(listOf("ephesians-5","proverbs-20","proverbs-23","isaiah-5","galatians-5","1-corinthians-6"),
      "drunkenness","drunk","wine","alcohol","embriaguez","borrachera","vino","ivresse","vin","trunkenheit","wein","ubriachezza","vino","embriaguez","vinho",
      "пьянство","вино", // ru
      "سكر","خمر", // ar
      "मतवालापन","शराब","दाखरस", // hi
      "醉酒","酒", // zh
      "술취함","포도주", // ko
      "酩酊","ぶどう酒" // ja
    )
    // ── Money / Wealth ──
    pin(listOf("1_timothy-6","matthew-6","proverbs-22","ecclesiastes-5","luke-16","luke-12","matthew-19"),
      "money","wealth","riches","prosperity","dinero","riqueza","argent","richesse","geld","reichtum","denaro","ricchezza","dinheiro","riqueza",
      "деньги","богатство", // ru
      "مال","ثروة","غنى", // ar
      "धन","सम्पत्ति", // hi
      "錢財","財富","钱财","财富", // zh
      "돈","재물","부", // ko
      "金","富" // ja
    )
    // ── Poverty / Poor ──
    pin(listOf("proverbs-19","matthew-25","james-2","luke-6","deuteronomy-15","isaiah-58","matthew-5"),
      "poverty","poor","needy","pobreza","pobre","necesitado","pauvrete","pauvre","armut","arm","poverta","povero","pobreza","pobre",
      "бедность","нищета", // ru
      "فقر","فقير","محتاج", // ar
      "गरीबी","गरीब", // hi
      "貧窮","貧困","贫穷","贫困", // zh
      "가난","빈곤", // ko
      "貧困","貧しい" // ja
    )
    // ── Slavery / Freedom / Bondage ──
    pin(listOf("galatians-5","john-8","romans-6","exodus-6","philemon-1","isaiah-61","luke-4"),
      "slavery","bondage","freedom","liberty","free","esclavitud","libertad","libre","esclavage","liberte","libre","sklaverei","freiheit","schiavitu","liberta","escravidao","liberdade",
      "рабство","свобода", // ru
      "عبودية","حرية", // ar
      "दासता","बन्धन","स्वतन्त्रता", // hi
      "奴隸","捆綁","自由","奴隶","捆绑", // zh
      "종","속박","자유", // ko
      "奴隷","束縛","自由" // ja
    )
    // ── Temptation ──
    pin(listOf("matthew-4","james-1","1-corinthians-10","genesis-3","hebrews-4","luke-4","hebrews-2"),
      "temptation","tempt","tempted","tentacion","tentar","tentation","tenter","versuchung","tentazione","tentacao","tentar",
      "искушение","соблазн", // ru
      "تجربة","إغراء", // ar
      "परीक्षा","प्रलोभन", // hi
      "試探","诱惑","试探", // zh
      "시험","유혹", // ko
      "誘惑","試み" // ja
    )
    // ── Confession ──
    pin(listOf("1_john-1","james-5","proverbs-28","romans-10","psalms-32","psalms-51"),
      "confession","confess","confesion","confesar","confession","confesser","bekenntnis","bekennen","confessione","confessare","confissao","confessar",
      "исповедание","исповедь", // ru
      "اعتراف", // ar
      "अंगीकार","स्वीकार", // hi
      "認罪","承認","认罪","承认", // zh
      "고백","자백", // ko
      "告白","懺悔" // ja
    )
    // ── Testimony / Witness ──
    pin(listOf("acts-1","revelation-12","john-15","isaiah-43","acts-4","1_peter-3","psalms-66"),
      "testimony","witness","testify","testimonio","testigo","temoignage","temoin","zeugnis","zeuge","testimonianza","testimone","testemunho","testemunha",
      "свидетельство","свидетель", // ru
      "شهادة","شاهد", // ar
      "गवाही","साक्षी", // hi
      "見證","見证","作證","作证", // zh
      "간증","증인", // ko
      "証し","証人" // ja
    )
    // ── New Covenant ──
    pin(listOf("jeremiah-31","hebrews-8","luke-22","2_corinthians-3","ezekiel-36","hebrews-9"),
      "new covenant","nuevo pacto","nueva alianza","nouvelle alliance","neuer bund","nuova alleanza","nova alianca",
      "новый завет", // ru
      "العهد الجديد", // ar
      "नई वाचा","नया नियम", // hi
      "新約","新约", // zh
      "새 언약", // ko
      "新しい契約" // ja
    )
    // ── Speaking in Tongues ──
    pin(listOf("acts-2","1-corinthians-12","1-corinthians-14","mark-16","acts-10","acts-19"),
      "tongues","speaking in tongues","glossolalia","lenguas","hablar en lenguas","langues","parler en langues","zungenreden","zungen","lingue","parlare in lingue","linguas","falar em linguas",
      "языки","говорение на языках", // ru
      "ألسنة","التكلم بألسنة", // ar
      "भाषाओं","अन्य भाषा", // hi
      "方言","說方言","说方言", // zh
      "방언", // ko
      "異言","異言を語る" // ja
    )
    // ── Authority / Power ──
    pin(listOf("luke-10","matthew-28","acts-1","ephesians-1","colossians-1","romans-13","matthew-10"),
      "authority","power","dominion","autoridad","poder","autorite","pouvoir","autoritat","macht","autorita","potere","autoridade","poder",
      "власть","сила","могущество", // ru
      "سلطة","قوة","سلطان", // ar
      "अधिकार","सामर्थ्य", // hi
      "權柄","權能","权柄","权能", // zh
      "권세","능력", // ko
      "権威","力" // ja
    )
    // ── High Priest / Mediator ──
    pin(listOf("hebrews-4","hebrews-7","hebrews-9","psalms-110","leviticus-16","1_timothy-2"),
      "high priest","mediator","priesthood","sumo sacerdote","mediador","sacerdoce","souverain sacrificateur","mediateur","hoherpriester","mittler","sommo sacerdote","mediatore","sumo sacerdote","mediador",
      "первосвященник","ходатай", // ru
      "رئيس الكهنة","وسيط", // ar
      "महायाजक","मध्यस्थ", // hi
      "大祭司","中保", // zh
      "대제사장","중보자", // ko
      "大祭司","仲介者" // ja
    )
    // ── Antichrist ──
    pin(listOf("1_john-2","1_john-4","2_thessalonians-2","daniel-7","revelation-13","daniel-8"),
      "antichrist","man of sin","son of perdition","anticristo","hombre de pecado","antechrist","homme du peche","antichrist","mensch der sunde","anticristo","uomo del peccato","anticristo","homem do pecado",
      "антихрист","человек греха", // ru
      "المسيح الدجال","ضد المسيح", // ar
      "मसीह विरोधी", // hi
      "敵基督","敌基督", // zh
      "적그리스도", // ko
      "反キリスト" // ja
    )
    // ── False Prophet / False Teacher ──
    pin(listOf("matthew-7","matthew-24","2_peter-2","1_john-4","revelation-19","deuteronomy-18","jeremiah-23"),
      "false prophet","false teacher","falso profeta","falso maestro","faux prophete","faux docteur","falscher prophet","falscher lehrer","falso profeta","falso maestro","falso profeta","falso mestre",
      "лжепророк","лжеучитель", // ru
      "نبي كاذب","معلم كاذب", // ar
      "झूठा भविष्यवक्ता","झूठा शिक्षक", // hi
      "假先知","假教師","假先知","假教师", // zh
      "거짓 선지자","거짓 교사", // ko
      "偽預言者","偽教師" // ja
    )
    // ── Book of Life ──
    pin(listOf("revelation-20","philippians-4","revelation-3","exodus-32","daniel-12","revelation-21"),
      "book of life","libro de la vida","livre de vie","buch des lebens","libro della vita","livro da vida",
      "книга жизни", // ru
      "سفر الحياة","كتاب الحياة", // ar
      "जीवन की पुस्तक", // hi
      "生命冊","生命册", // zh
      "생명책", // ko
      "命の書" // ja
    )
    // ── Tree of Life ──
    pin(listOf("genesis-2","revelation-22","revelation-2","proverbs-3","proverbs-11"),
      "tree of life","arbol de la vida","arbre de vie","baum des lebens","albero della vita","arvore da vida",
      "древо жизни","дерево жизни", // ru
      "شجرة الحياة", // ar
      "जीवन का वृक्ष", // hi
      "生命樹","生命树", // zh
      "생명나무", // ko
      "命の木" // ja
    )
  }

  private fun buildTierBPins3(): Map<String, List<String>> = buildMap {
    // ── New Heaven / New Earth ──
    pin(listOf("revelation-21","isaiah-65","2_peter-3","isaiah-66","romans-8"),
      "new heaven","new earth","new heavens","cielo nuevo","tierra nueva","nouveau ciel","nouvelle terre","neuer himmel","neue erde","nuovo cielo","nuova terra","novo ceu","nova terra",
      "новое небо","новая земля", // ru
      "سماء جديدة","أرض جديدة", // ar
      "नया आकाश","नई पृथ्वी", // hi
      "新天","新地", // zh
      "새 하늘","새 땅", // ko
      "新しい天","新しい地" // ja
    )
    // ── Vine and Branches ──
    pin(listOf("john-15","isaiah-5","psalms-80","jeremiah-2","ezekiel-15"),
      "vine","branches","abide","vid","ramas","permanecer","vigne","sarments","demeurer","weinstock","reben","bleiben","vite","tralci","dimorare","videira","ramos","permanecer",
      "виноградная лоза","ветви","пребывать", // ru
      "كرمة","أغصان","اثبتوا", // ar
      "दाखलता","डालियाँ","बने रहो", // hi
      "葡萄樹","枝子","葡萄树", // zh
      "포도나무","가지","거하라", // ko
      "ぶどうの木","枝","とどまる" // ja
    )
    // ── Cornerstone / Foundation ──
    pin(listOf("ephesians-2","1_peter-2","isaiah-28","psalms-118","matthew-21","1-corinthians-3"),
      "cornerstone","foundation","piedra angular","fundamento","cimiento","pierre angulaire","fondement","eckstein","grundstein","pietra angolare","fondamento","pedra angular","fundamento",
      "краеугольный камень","основание", // ru
      "حجر الزاوية","أساس", // ar
      "कोने का पत्थर","नींव","नेव", // hi
      "房角石","根基", // zh
      "모퉁이돌","기초","반석", // ko
      "礎石","土台" // ja
    )
    // ── Rock / Fortress ──
    pin(listOf("psalms-18","psalms-62","2-samuel-22","matthew-7","1-corinthians-10","matthew-16","psalms-31"),
      "rock","fortress","stronghold","roca","fortaleza","rocher","forteresse","fels","festung","roccia","fortezza","rocha","fortaleza",
      "скала","крепость","твердыня", // ru
      "صخرة","حصن","ملجأ", // ar
      "चट्टान","गढ़","किला", // hi
      "磐石","堡壘","堡垒", // zh
      "반석","요새","산성", // ko
      "岩","要塞","砦" // ja
    )
    // ── Name of God / YHWH ──
    pin(listOf("exodus-3","exodus-34","psalms-8","philippians-2","proverbs-18","isaiah-42"),
      "name of god","yhwh","yahweh","jehovah","nombre de dios","nom de dieu","name gottes","nome di dio","nome de deus",
      "имя бога","яхве","иегова", // ru
      "اسم الله","يهوه", // ar
      "परमेश्वर का नाम","यहोवा","याहवे", // hi
      "神的名","耶和華","耶和华", // zh
      "하나님의 이름","여호와","야훼", // ko
      "神の名","ヤハウェ","エホバ" // ja
    )
    // ── Glory of God / Shekinah ──
    pin(listOf("exodus-33","isaiah-6","ezekiel-1","john-1","revelation-21","exodus-40","2chronicles-7"),
      "glory","glory of god","shekinah","gloria","gloria de dios","gloire","gloire de dieu","herrlichkeit","herrlichkeit gottes","gloria","gloria di dio","gloria","gloria de deus",
      "слава","слава божья","шекина", // ru
      "مجد","مجد الله","شكينة", // ar
      "महिमा","परमेश्वर की महिमा", // hi
      "榮耀","神的榮耀","荣耀","神的荣耀", // zh
      "영광","하나님의 영광", // ko
      "栄光","神の栄光" // ja
    )
    // ── Presence of God ──
    pin(listOf("psalms-16","psalms-139","exodus-33","matthew-18","acts-17","psalms-27","psalms-42"),
      "presence of god","gods presence","presencia de dios","presence de dieu","gegenwart gottes","presenza di dio","presenca de deus",
      "присутствие божье","присутствие бога", // ru
      "حضور الله","محضر الله", // ar
      "परमेश्वर की उपस्थिति", // hi
      "神的同在", // zh
      "하나님의 임재", // ko
      "神の臨在" // ja
    )
    // ── Alpha and Omega ──
    pin(listOf("revelation-1","revelation-21","revelation-22","isaiah-44","isaiah-48"),
      "alpha omega","alpha and omega","beginning end","first last","alfa omega","alfa y omega","principio fin","alpha omega","alpha et omega","commencement fin","alpha omega","anfang ende","alfa omega","principio fine","alfa omega","alfa e omega","principio fim",
      "альфа омега","начало конец","первый последний", // ru
      "الألف والياء","البداية والنهاية", // ar
      "अल्फा ओमेगा","आदि अन्त", // hi
      "阿拉法","俄梅戛","始終","始终", // zh
      "알파 오메가","처음과 끝", // ko
      "アルファ","オメガ","初めであり終わり" // ja
    )
    // ── Lion of Judah ──
    pin(listOf("revelation-5","genesis-49","hosea-5","hebrews-7"),
      "lion of judah","root of david","leon de juda","raiz de david","lion de juda","racine de david","lowe von juda","wurzel davids","leone di giuda","radice di davide","leao de juda","raiz de davi",
      "лев из колена иуды","корень давида", // ru
      "أسد يهوذا","أصل داود", // ar
      "यहूदा का सिंह","दाऊद की जड़", // hi
      "猶大的獅子","大衛的根","犹大的狮子","大卫的根", // zh
      "유다의 사자","다윗의 뿌리", // ko
      "ユダの獅子","ダビデの根" // ja
    )
    // ── Bride / Bridegroom / Wedding ──
    pin(listOf("revelation-19","revelation-21","john-3","isaiah-62","ephesians-5","matthew-25","song-of-songs-1"),
      "bride","bridegroom","wedding","marriage supper","novia","novio","boda","cena de bodas","mariee","epoux","noces","braut","brautigam","hochzeit","sposa","sposo","nozze","noiva","noivo","casamento",
      "невеста","жених","свадьба","брачная вечеря", // ru
      "عروس","عريس","عرس","وليمة العرس", // ar
      "दुल्हन","दूल्हा","विवाह","भोज", // hi
      "新婦","新郎","婚宴","婚筵", // zh
      "신부","신랑","혼인잔치", // ko
      "花嫁","花婿","婚宴","婚礼" // ja
    )
    // ── Salt and Light ──
    pin(listOf("matthew-5","mark-9","colossians-4","luke-14"),
      "salt","light of world","salt of earth","sal","luz del mundo","sal de la tierra","sel","lumiere du monde","sel de la terre","salz","licht der welt","salz der erde","sale","luce del mondo","sale della terra","sal","luz do mundo","sal da terra",
      "соль","свет мира","соль земли", // ru
      "ملح","نور العالم","ملح الأرض", // ar
      "नमक","संसार की ज्योति","पृथ्वी का नमक", // hi
      "鹽","世界的光","地上的鹽","盐","地上的盐", // zh
      "소금","세상의 빛","세상의 소금", // ko
      "塩","世の光","地の塩" // ja
    )
    // ── Yoke / Burden / Rest in Christ ──
    pin(listOf("matthew-11","galatians-6","galatians-5","1_john-5","psalms-55"),
      "yoke","burden","easy yoke","light burden","yugo","carga","joug","fardeau","joch","last","giogo","peso","jugo","fardo","carga",
      "иго","бремя","ярмо", // ru
      "نير","حمل","عبء", // ar
      "जूआ","बोझ", // hi
      "軛","擔子","轭","担子", // zh
      "멍에","짐", // ko
      "くびき","荷" // ja
    )
    // ── Narrow Gate / Wide Gate ──
    pin(listOf("matthew-7","luke-13"),
      "narrow gate","wide gate","narrow path","broad way","puerta estrecha","puerta ancha","camino estrecho","camino ancho","porte etroite","chemin large","enge pforte","breiter weg","porta stretta","via larga","porta estreita","caminho largo",
      "узкие врата","широкий путь", // ru
      "الباب الضيق","الطريق الواسع", // ar
      "सकरा द्वार","चौड़ा मार्ग", // hi
      "窄門","寬路","窄门","宽路", // zh
      "좁은 문","넓은 길", // ko
      "狭い門","広い道" // ja
    )
    // ── Anointing / Anointed ──
    pin(listOf("1-samuel-16","1_john-2","isaiah-61","luke-4","acts-10","james-5","psalms-23"),
      "anointing","anointed","anoint","uncion","ungido","ungir","onction","oint","oindre","salbung","gesalbt","salben","unzione","unto","ungere","uncao","ungido","ungir",
      "помазание","помазанник","помазать", // ru
      "مسحة","مسيح","ممسوح", // ar
      "अभिषेक","अभिषिक्त", // hi
      "膏抹","受膏","恩膏", // zh
      "기름부음","기름 부음 받은", // ko
      "油注ぎ","油を注がれた" // ja
    )
    // ── Perseverance / Endurance ──
    pin(listOf("james-1","hebrews-12","romans-5","2_timothy-4","revelation-2","galatians-6","1-corinthians-9"),
      "perseverance","endurance","endure","persevere","perseverancia","perseverar","perseverance","perseverer","ausdauer","beharrlichkeit","perseveranza","perseverare","perseveranca","perseverar",
      "терпение","стойкость","выносливость", // ru
      "مثابرة","صبر","احتمال", // ar
      "धीरज","सहनशीलता", // hi
      "忍耐","堅忍","坚忍", // zh
      "인내","참음", // ko
      "忍耐","堅忍" // ja
    )
    // ── Gratitude / Thankfulness ──
    pin(listOf("1_thessalonians-5","colossians-3","psalms-100","psalms-107","ephesians-5","philippians-4"),
      "gratitude","thankfulness","thankful","grateful","give thanks","gratitud","agradecimiento","gratitude","reconnaissance","dankbarkeit","gratitudine","riconoscenza","gratidao","agradecimento",
      "благодарность", // ru
      "شكر","امتنان", // ar
      "कृतज्ञता","धन्यवाद", // hi
      "感恩","感謝","感谢", // zh
      "감사","감사함", // ko
      "感謝","感恩" // ja
    )
    // ── Parable ──
    pin(listOf("matthew-13","luke-15","luke-10","mark-4","matthew-25","matthew-20","luke-16"),
      "parable","parables","parabola","parabolas","parabole","paraboles","gleichnis","gleichnisse","parabola","parabole","parabola","parabolas",
      "притча","притчи", // ru
      "مثل","أمثال", // ar
      "दृष्टान्त","दृष्टान्तों", // hi
      "比喻", // zh
      "비유", // ko
      "たとえ","たとえ話" // ja
    )
    // ── Wrath of God ──
    pin(listOf("romans-1","romans-9","revelation-6","nahum-1","zephaniah-1","john-3","revelation-16"),
      "wrath of god","gods wrath","divine wrath","ira de dios","colere de dieu","zorn gottes","ira di dio","ira de deus",
      "гнев божий","гнев господень", // ru
      "غضب الله", // ar
      "परमेश्वर का क्रोध", // hi
      "神的忿怒","神的憤怒","上帝的愤怒", // zh
      "하나님의 진노", // ko
      "神の怒り","神の憤り" // ja
    )
    // ── Day of the Lord ──
    pin(listOf("joel-2","1_thessalonians-5","2_peter-3","amos-5","zephaniah-1","malachi-4","isaiah-13"),
      "day of the lord","day of lord","dia del senor","jour du seigneur","tag des herrn","giorno del signore","dia do senhor",
      "день господень","день господа", // ru
      "يوم الرب", // ar
      "प्रभु का दिन","यहोवा का दिन", // hi
      "主的日子","耶和華的日子","耶和华的日子", // zh
      "주의 날","여호와의 날", // ko
      "主の日" // ja
    )
    // ── Adoption (spiritual) ──
    pin(listOf("romans-8","galatians-4","ephesians-1","john-1","1_john-3"),
      "adoption","adopted","children of god","sons of god","adopcion","hijos de dios","adoption","enfants de dieu","adoption","kinder gottes","adozione","figli di dio","adocao","filhos de deus",
      "усыновление","дети божьи", // ru
      "تبنّي","أبناء الله", // ar
      "दत्तक","परमेश्वर की सन्तान", // hi
      "嗣子","神的兒女","神的儿女", // zh
      "양자","하나님의 자녀", // ko
      "養子","神の子ども" // ja
    )
    // ── Inheritance (spiritual) ──
    pin(listOf("1_peter-1","ephesians-1","colossians-3","romans-8","galatians-3","galatians-4"),
      "inheritance","heir","herencia","heredero","heritage","heritier","erbe","erbschaft","eredita","erede","heranca","herdeiro",
      "наследие","наследство","наследник", // ru
      "ميراث","وارث", // ar
      "मीरास","उत्तराधिकार","वारिस", // hi
      "產業","基業","繼承","产业","基业","继承", // zh
      "유산","상속","후사", // ko
      "相続","嗣業","世継ぎ" // ja
    )
    // ── Intercession ──
    pin(listOf("romans-8","hebrews-7","1_timothy-2","isaiah-53","james-5","luke-22"),
      "intercession","intercede","intercessor","intercesion","interceder","intercession","interceder","furbitte","intercessione","intercessore","intercessao","interceder",
      "ходатайство","заступничество", // ru
      "شفاعة","وساطة", // ar
      "मध्यस्थता","बिनती", // hi
      "代求","代禱","代祷", // zh
      "중보","중보기도", // ko
      "執り成し","とりなし" // ja
    )
    // ── Crown / Reward ──
    pin(listOf("1-corinthians-9","2_timothy-4","james-1","1_peter-5","revelation-2","revelation-3","matthew-5"),
      "crown","reward","corona","recompensa","premio","couronne","recompense","krone","lohn","belohnung","corona","ricompensa","coroa","recompensa",
      "венец","награда", // ru
      "إكليل","مكافأة","أجر", // ar
      "मुकुट","प्रतिफल","इनाम", // hi
      "冠冕","賞賜","冠冕","赏赐", // zh
      "면류관","상","상급", // ko
      "冠","報い","報酬" // ja
    )
    // ── Treasure in Heaven ──
    pin(listOf("matthew-6","matthew-13","luke-12","colossians-3","1_timothy-6"),
      "treasure","treasure in heaven","tesoro","tesoro en el cielo","tresor","tresor dans le ciel","schatz","schatz im himmel","tesoro","tesoro in cielo","tesouro","tesouro no ceu",
      "сокровище","сокровище на небесах", // ru
      "كنز","كنز في السماء", // ar
      "खज़ाना","स्वर्ग में खज़ाना", // hi
      "財寶","天上的財寶","财宝","天上的财宝", // zh
      "보물","하늘의 보물", // ko
      "宝","天の宝" // ja
    )
    // ── Apostle / Missionary ──
    pin(listOf("ephesians-4","1-corinthians-12","acts-1","hebrews-3","matthew-10","romans-1"),
      "apostle","apostles","missionary","apostol","apostoles","misionero","apotre","apotres","missionnaire","apostel","missionare","apostolo","apostoli","missionario","apostolo","apostolos","missionario",
      "апостол","апостолы","миссионер", // ru
      "رسول","رسل","مبشر", // ar
      "प्रेरित","मिशनरी", // hi
      "使徒","宣教士", // zh
      "사도","선교사", // ko
      "使徒","宣教師" // ja
    )
    // ── Elder / Deacon / Overseer ──
    pin(listOf("1_timothy-3","titus-1","acts-6","1_peter-5","james-5","acts-20"),
      "elder","deacon","overseer","bishop","anciano","diacono","obispo","ancien","diacre","eveque","altester","diakon","bischof","anziano","diacono","vescovo","anciao","diacono","bispo",
      "старейшина","пресвитер","диакон","епископ", // ru
      "شيخ","شماس","أسقف", // ar
      "प्राचीन","सेवक","अध्यक्ष", // hi
      "長老","執事","監督","长老","执事","监督", // zh
      "장로","집사","감독", // ko
      "長老","執事","監督" // ja
    )
    // ── Purpose / Meaning of Life ──
    pin(listOf("jeremiah-29","romans-8","ephesians-2","ecclesiastes-12","proverbs-19","isaiah-43","psalms-139"),
      "purpose","meaning of life","calling","plan of god","proposito","sentido de la vida","llamado","plan de dios","but","sens de la vie","appel","plan de dieu","zweck","sinn des lebens","berufung","plan gottes","scopo","senso della vita","chiamata","proposito","sentido da vida","chamado","plano de deus",
      "предназначение","смысл жизни","призвание", // ru
      "هدف","معنى الحياة","دعوة", // ar
      "उद्देश्य","जीवन का अर्थ","बुलाहट", // hi
      "目的","生命的意義","呼召","生命的意义", // zh
      "목적","삶의 의미","소명","부르심", // ko
      "目的","人生の意味","召し" // ja
    )
    // ── Soul / Spirit distinction ──
    pin(listOf("hebrews-4","1_thessalonians-5","genesis-2","matthew-10","1-corinthians-15","ecclesiastes-12"),
      "soul","spirit","soul and spirit","alma","espiritu","ame","esprit","seele","geist","anima","spirito","alma","espirito",
      "душа","дух", // ru
      "نفس","روح", // ar
      "आत्मा","प्राण", // hi
      "靈魂","靈","灵魂","灵", // zh
      "영혼","영", // ko
      "魂","霊" // ja
    )
    // ── Original Sin / Fall ──
    pin(listOf("genesis-3","romans-5","romans-3","1-corinthians-15","psalms-51"),
      "original sin","fall of man","fallen nature","pecado original","caida del hombre","peche originel","chute de l'homme","erbsunde","sundenfall","peccato originale","caduta","pecado original","queda do homem",
      "первородный грех","грехопадение", // ru
      "الخطيئة الأصلية","سقوط الإنسان", // ar
      "मूल पाप","मनुष्य का पतन", // hi
      "原罪","人的墮落","人的堕落", // zh
      "원죄","타락", // ko
      "原罪","堕落" // ja
    )
    // ── Spiritual Gifts (general) ──
    pin(listOf("1-corinthians-12","1-corinthians-14","romans-12","ephesians-4","1_peter-4"),
      "spiritual gift","spiritual gifts","gift of prophecy","gift of healing","word of knowledge","word of wisdom","discernment",
      "don espiritual","dones espirituales","don de profecia","don de sanidad","discernimiento",
      "don spirituel","dons spirituels","don de prophetie","don de guerison","discernement",
      "geistesgabe","geistesgaben","gabe der prophetie","gabe der heilung","unterscheidung",
      "dono spirituale","doni spirituali","dono di profezia","dono di guarigione","discernimento",
      "dom espiritual","dons espirituais","dom de profecia","dom de cura","discernimento",
      "духовный дар","духовные дары","дар пророчества","дар исцеления","различение", // ru
      "موهبة روحية","مواهب روحية","موهبة النبوة","موهبة الشفاء","تمييز", // ar
      "आत्मिक वरदान","भविष्यवाणी का वरदान","चंगाई का वरदान","विवेक", // hi
      "屬靈恩賜","属灵恩赐","先知的恩賜","醫治的恩賜","辨別","先知的恩赐","医治的恩赐","辨别", // zh
      "영적 은사","예언의 은사","치유의 은사","분별", // ko
      "霊的賜物","預言の賜物","いやしの賜物","見分け" // ja
    )
    // ── Laying on of Hands ──
    pin(listOf("acts-6","acts-8","1_timothy-4","2_timothy-1","hebrews-6","acts-13"),
      "laying on of hands","ordination","imposicion de manos","ordenacion","imposition des mains","ordination","handauflegung","ordinierung","imposizione delle mani","ordinazione","imposicao de maos","ordenacao",
      "возложение рук","рукоположение", // ru
      "وضع الأيدي","رسامة", // ar
      "हाथ रखना","अभिषेक", // hi
      "按手","按立", // zh
      "안수","안수례", // ko
      "按手","任職" // ja
    )
    // ── Great White Throne ──
    pin(listOf("revelation-20","daniel-7","matthew-25","romans-14","2_corinthians-5"),
      "great white throne","final judgment","white throne","gran trono blanco","juicio final","grand trone blanc","jugement dernier","grosser weisser thron","endgericht","grande trono bianco","giudizio finale","grande trono branco","juizo final",
      "великий белый престол","последний суд", // ru
      "العرش الأبيض العظيم","الدينونة الأخيرة", // ar
      "बड़ा श्वेत सिंहासन","अन्तिम न्याय", // hi
      "白色大寶座","最後審判","白色大宝座","最后审判", // zh
      "큰 백색 보좌","최후의 심판", // ko
      "大きな白い御座","最後の審判" // ja
    )
    // ── Abomination of Desolation ──
    pin(listOf("daniel-9","daniel-11","matthew-24","mark-13","daniel-12"),
      "abomination of desolation","abomination","abominacion desoladora","abominacion","abomination de la desolation","abomination","grauel der verwustung","grauel","abominio della desolazione","abominacao da desolacao",
      "мерзость запустения", // ru
      "رجسة الخراب", // ar
      "उजाड़ने वाली घृणित वस्तु", // hi
      "那行毀壞可憎的","那行毁坏可憎的", // zh
      "멸망의 가증한 것", // ko
      "荒らす憎むべきもの" // ja
    )
    // ── 70 Weeks / Daniel's Prophecy ──
    pin(listOf("daniel-9","daniel-7","daniel-2","daniel-8","daniel-12"),
      "seventy weeks","70 weeks","daniels prophecy","four kingdoms","setenta semanas","profecia de daniel","cuatro reinos","soixante-dix semaines","prophetie de daniel","quatre royaumes","siebzig wochen","daniels prophezeiung","vier reiche","settanta settimane","profezia di daniele","quattro regni","setenta semanas","profecia de daniel","quatro reinos",
      "семьдесят седьмин","пророчество даниила","четыре царства", // ru
      "سبعون أسبوعا","نبوة دانيال","أربع ممالك", // ar
      "सत्तर सप्ताह","दानिय्येल की भविष्यवाणी", // hi
      "七十個七","但以理的預言","四大帝國","七十个七","但以理的预言","四大帝国", // zh
      "칠십 이레","다니엘의 예언","네 왕국", // ko
      "七十週","ダニエルの預言","四つの王国" // ja
    )
    // ── Depression / Grief / Sorrow ──
    pin(listOf("psalms-42","psalms-34","psalms-88","lamentations-3","matthew-5","2_corinthians-1","isaiah-53"),
      "depression","grief","sorrow","mourning","depresion","duelo","tristeza","luto","depression","deuil","tristesse","depression","trauer","kummer","depressione","dolore","lutto","depressao","luto","tristeza",
      "депрессия","горе","скорбь","печаль", // ru
      "اكتئاب","حزن","حداد", // ar
      "अवसाद","शोक","दुःख", // hi
      "憂鬱","悲傷","哀痛","忧郁","悲伤","哀痛", // zh
      "우울","슬픔","애도", // ko
      "うつ","悲しみ","嘆き" // ja
    )
    // ── Loneliness ──
    pin(listOf("psalms-25","psalms-68","genesis-2","1-kings-19","psalms-27","psalms-139","isaiah-41"),
      "loneliness","lonely","alone","soledad","solitario","solo","solitude","seul","einsamkeit","einsam","solitudine","solo","solidao","sozinho",
      "одиночество","одинокий", // ru
      "وحدة","وحيد", // ar
      "अकेलापन","अकेला", // hi
      "孤獨","寂寞","孤独","寂寞", // zh
      "외로움","고독", // ko
      "孤独","寂しさ" // ja
    )
    // ── Good vs Evil ──
    pin(listOf("genesis-3","romans-12","isaiah-5","amos-5","micah-6","ephesians-6","psalms-1"),
      "good and evil","good vs evil","evil","bien y mal","el mal","bien et mal","le mal","gut und bose","das bose","bene e male","il male","bem e mal","o mal",
      "добро и зло","зло", // ru
      "خير وشر","الشر", // ar
      "भलाई और बुराई","बुराई", // hi
      "善與惡","善与恶","惡","恶", // zh
      "선과 악","악", // ko
      "善と悪","悪" // ja
    )
    // ── Friendship ──
    pin(listOf("proverbs-17","proverbs-18","john-15","1-samuel-18","ecclesiastes-4","proverbs-27","james-2"),
      "friendship","friend","amistad","amigo","amitie","ami","freundschaft","freund","amicizia","amico","amizade","amigo",
      "дружба","друг", // ru
      "صداقة","صديق", // ar
      "मित्रता","मित्र","दोस्ती", // hi
      "友誼","朋友","友谊", // zh
      "우정","친구", // ko
      "友情","友" // ja
    )
    // ── Patience ──
    pin(listOf("james-5","romans-12","galatians-5","colossians-3","ecclesiastes-7","2_peter-3","hebrews-6"),
      "patience","patient","paciencia","paciente","patience","geduld","geduldig","pazienza","paziente","paciencia","paciente",
      "терпение","терпеливый", // ru
      "صبر","صبور", // ar
      "धैर्य","धैर्यवान", // hi
      "忍耐","耐心", // zh
      "인내","참을성", // ko
      "忍耐","辛抱" // ja
    )
    // ── Kindness / Goodness ──
    pin(listOf("ephesians-4","galatians-5","colossians-3","proverbs-11","ruth-2","titus-3","luke-6"),
      "kindness","goodness","gentle","gentleness","bondad","amabilidad","benignidad","bonte","gentillesse","gute","freundlichkeit","sanftmut","bonta","gentilezza","bondade","benignidade",
      "доброта","благость","кротость", // ru
      "لطف","صلاح","وداعة", // ar
      "भलाई","कृपालुता","नम्रता", // hi
      "良善","恩慈","溫柔","温柔", // zh
      "선함","인자","온유", // ko
      "親切","善良","柔和" // ja
    )
    // ── Self-Control / Discipline ──
    pin(listOf("galatians-5","proverbs-25","2_timothy-1","titus-2","1-corinthians-9","proverbs-16"),
      "self-control","self control","discipline","dominio propio","disciplina","maitrise de soi","discipline","selbstbeherrschung","disziplin","autocontrollo","disciplina","dominio proprio","autocontrole","disciplina",
      "воздержание","самообладание","дисциплина", // ru
      "ضبط النفس","انضباط", // ar
      "संयम","आत्मसंयम","अनुशासन", // hi
      "節制","自制","紀律","节制","纪律", // zh
      "절제","자제","훈련", // ko
      "自制","節制","訓練" // ja
    )
    // ── Faithfulness / Loyalty ──
    pin(listOf("lamentations-3","psalms-36","proverbs-3","galatians-5","2_timothy-2","matthew-25","revelation-2"),
      "faithfulness","faithful","loyalty","loyal","fidelidad","fiel","lealtad","fidelite","fidele","loyaute","treue","treu","fedelta","fedele","lealta","fidelidade","fiel","lealdade",
      "верность","верный", // ru
      "أمانة","أمين","وفاء", // ar
      "विश्वासयोग्यता","विश्वासयोग्य", // hi
      "信實","忠心","信实","忠诚", // zh
      "신실","충성","성실", // ko
      "忠実","誠実" // ja
    )
    // ── Work / Labor ──
    pin(listOf("colossians-3","2_thessalonians-3","proverbs-10","ecclesiastes-9","genesis-2","proverbs-31"),
      "work","labor","diligence","trabajo","diligencia","travail","diligence","arbeit","fleiss","lavoro","diligenza","trabalho","diligencia",
      "труд","работа","усердие", // ru
      "عمل","اجتهاد", // ar
      "काम","परिश्रम","मेहनत", // hi
      "工作","勞動","勤勞","劳动","勤劳", // zh
      "일","노동","근면", // ko
      "労働","勤勉","働き" // ja
    )
    // ── Resurrection Body / Glorified Body ──
    pin(listOf("1-corinthians-15","philippians-3","romans-8","2_corinthians-5","1_john-3"),
      "resurrection body","glorified body","imperishable body","spiritual body","cuerpo glorificado","cuerpo de resurreccion","corps glorifie","corps de resurrection","verherrlichter leib","auferstehungsleib","corpo glorificato","corpo della risurrezione","corpo glorificado","corpo da ressurreicao",
      "тело воскресения","прославленное тело", // ru
      "جسد القيامة","الجسد الممجد", // ar
      "पुनरुत्थान का शरीर","महिमा का शरीर", // hi
      "復活的身體","榮耀的身體","复活的身体","荣耀的身体", // zh
      "부활의 몸","영광의 몸", // ko
      "復活の体","栄光の体" // ja
    )
    // ── Circumcision of Heart ──
    pin(listOf("deuteronomy-30","romans-2","colossians-2","philippians-3","jeremiah-4"),
      "circumcision of heart","circumcised heart","spiritual circumcision","circuncision del corazon","circoncision du coeur","beschneidung des herzens","circoncisione del cuore","circuncisao do coracao",
      "обрезание сердца", // ru
      "ختان القلب", // ar
      "मन का खतना","हृदय का खतना", // hi
      "心的割禮","心的割礼", // zh
      "마음의 할례", // ko
      "心の割礼" // ja
    )
    // ── Revelation / Book of Revelation ──
    pin(listOf("revelation-1","revelation-4","revelation-12","revelation-19","revelation-21","revelation-22"),
      "revelation","apocalypse","book of revelation","apocalipsis","libro del apocalipsis","apocalypse","livre de l'apocalypse","offenbarung","apokalypse","apocalisse","libro dell'apocalisse","apocalipse","livro do apocalipse",
      "откровение","апокалипсис","книга откровения", // ru
      "سفر الرؤيا","رؤيا يوحنا", // ar
      "प्रकाशितवाक्य","प्रकाशन की पुस्तक", // hi
      "啟示錄","启示录", // zh
      "요한계시록","계시록", // ko
      "黙示録","ヨハネの黙示録" // ja
    )

    // ── Gospel / Good News ──
    pin(listOf("romans-1","mark-1","1-corinthians-15","galatians-1","matthew-4","isaiah-61","luke-4"),
      "gospel","good news","evangelio","buenas nuevas","evangile","bonne nouvelle","evangelium","frohe botschaft","vangelo","buona notizia","evangelho","boas novas",
      "евангелие","благая весть", // ru
      "إنجيل","بشارة", // ar
      "सुसमाचार","खुशखबरी", // hi
      "福音","好消息", // zh
      "복음","기쁜 소식", // ko
      "福音","良い知らせ" // ja
    )
    // ── Eternal Security / Assurance ──
    pin(listOf("john-10","romans-8","ephesians-1","philippians-1","1_john-5","john-6","2_timothy-1"),
      "eternal security","assurance","once saved always saved","saved forever","seguridad eterna","seguranza eterna","securite eternelle","assurance du salut","ewige sicherheit","heilsgewissheit","sicurezza eterna","certeza da salvacao",
      "вечная безопасность","уверенность в спасении", // ru
      "الأمان الأبدي","ضمان الخلاص", // ar
      "अनन्त सुरक्षा","उद्धार का आश्वासन", // hi
      "永恆的保障","救恩的確據","永恒的保障","救恩的确据", // zh
      "영원한 보장","구원의 확신", // ko
      "永遠の保証","救いの確信" // ja
    )
    // ── Free Will / Choice ──
    pin(listOf("deuteronomy-30","joshua-24","john-7","romans-9","1-corinthians-10","galatians-5"),
      "free will","choice","choose","libre albedrio","eleccion","escoger","libre arbitre","choix","choisir","freier wille","wahl","wahlen","libero arbitrio","scelta","scegliere","livre arbitrio","escolha","escolher",
      "свободная воля","свобода выбора", // ru
      "إرادة حرة","اختيار", // ar
      "स्वतन्त्र इच्छा","चुनाव", // hi
      "自由意志","選擇","选择", // zh
      "자유 의지","선택", // ko
      "自由意志","選択" // ja
    )
    // ── Sovereignty of God ──
    pin(listOf("isaiah-46","romans-9","daniel-4","psalms-115","proverbs-19","job-42","ephesians-1"),
      "sovereignty","sovereign","sovereignty of god","soberania","soberania de dios","souverainete","souverainete de dieu","souveranitat","souveranitat gottes","sovranita","sovranita di dio","soberania","soberania de deus",
      "суверенитет","суверенитет бога","владычество", // ru
      "سيادة الله","سيادة", // ar
      "परमेश्वर की प्रभुता","सार्वभौमता", // hi
      "神的主權","主权","神的主权", // zh
      "하나님의 주권","주권", // ko
      "神の主権","主権" // ja
    )
    // ── Providence ──
    pin(listOf("romans-8","genesis-50","esther-4","jeremiah-29","psalms-37","proverbs-16","matthew-6"),
      "providence","divine providence","providencia","providencia divina","providence","providence divine","vorsehung","provvidenza","providencia",
      "провидение","промысел божий", // ru
      "عناية إلهية","تدبير", // ar
      "ईश्वरीय विधान","भविष्य दृष्टि", // hi
      "天意","神的護理","神的护理", // zh
      "섭리","하나님의 섭리", // ko
      "摂理","神の摂理" // ja
    )
    // ── Deity of Christ ──
    pin(listOf("john-1","colossians-1","colossians-2","hebrews-1","philippians-2","titus-2","john-10"),
      "deity of christ","divinity of jesus","jesus is god","divinidad de cristo","divinite du christ","gottheit christi","divinita di cristo","divindade de cristo",
      "божественность христа","иисус есть бог", // ru
      "ألوهية المسيح","يسوع هو الله", // ar
      "मसीह का ईश्वरत्व","यीशु परमेश्वर है", // hi
      "基督的神性","耶穌是神","耶稣是神", // zh
      "그리스도의 신성","예수는 하나님", // ko
      "キリストの神性","イエスは神" // ja
    )
    // ── Virgin Birth / Incarnation ──
    pin(listOf("matthew-1","luke-1","isaiah-7","john-1","galatians-4","philippians-2"),
      "virgin birth","incarnation","born of a virgin","nacimiento virginal","encarnacion","naissance virginale","incarnation","jungfrauengeburt","inkarnation","nascita verginale","incarnazione","nascimento virginal","encarnacao",
      "непорочное зачатие","воплощение", // ru
      "الولادة العذراوية","التجسد", // ar
      "कुँवारी से जन्म","अवतार", // hi
      "童貞女所生","道成肉身","童贞女所生", // zh
      "동정녀 탄생","성육신", // ko
      "処女降誕","受肉" // ja
    )
    // ── Word Made Flesh / Logos ──
    pin(listOf("john-1","revelation-19","hebrews-4","1_john-1","colossians-1"),
      "logos","word made flesh","word became flesh","verbo hecho carne","verbe fait chair","wort wurde fleisch","verbo fatto carne","verbo se fez carne",
      "слово стало плотью","логос", // ru
      "الكلمة صار جسدا","اللوغوس", // ar
      "वचन देहधारी हुआ","लोगोस", // hi
      "道成了肉身", // zh
      "말씀이 육신이 되어","로고스", // ko
      "言は肉となった","ロゴス" // ja
    )
    // ── Union with Christ ──
    pin(listOf("romans-6","galatians-2","ephesians-2","colossians-3","john-15","2_corinthians-5","1-corinthians-12"),
      "union with christ","in christ","abide in me","union con cristo","en cristo","union avec le christ","en christ","vereinigung mit christus","in christus","unione con cristo","in cristo","uniao com cristo","em cristo",
      "единение со христом","во христе", // ru
      "الاتحاد بالمسيح","في المسيح", // ar
      "मसीह के साथ एकता","मसीह में", // hi
      "與基督聯合","在基督裏","与基督联合","在基督里", // zh
      "그리스도와의 연합","그리스도 안에서", // ko
      "キリストとの一体","キリストにあって" // ja
    )
    // ── Glorification ──
    pin(listOf("romans-8","1-corinthians-15","philippians-3","2_corinthians-3","colossians-3","1_john-3"),
      "glorification","glorified","glorify","glorificacion","glorificado","glorification","glorifie","verherrlichung","verherrlicht","glorificazione","glorificato","glorificacao","glorificado",
      "прославление","прославленный", // ru
      "تمجيد","ممجد", // ar
      "महिमान्वित","महिमा पाना", // hi
      "得榮耀","得荣耀", // zh
      "영화","영화롭게 됨", // ko
      "栄化","栄光を受ける" // ja
    )
    // ── Ransom ──
    pin(listOf("mark-10","1_timothy-2","matthew-20","1_peter-1","isaiah-53","hosea-13"),
      "ransom","ransomed","rescate","rescatado","rancon","rachete","losegeld","riscatto","resgate","resgatado",
      "выкуп","искупление", // ru
      "فدية","فداء", // ar
      "छुड़ौती","फिरौती", // hi
      "贖價","赎价", // zh
      "대속","속전", // ko
      "身代金","贖い" // ja
    )
  }

  private fun buildTierBPins4(): Map<String, List<String>> = buildMap {
    // ── Passover / Pesach ──
    pin(listOf("exodus-12","exodus-13","1-corinthians-5","luke-22","john-1","deuteronomy-16"),
      "passover","pesach","pascua","paque","pessach","pasqua","pascoa",
      "пасха","песах", // ru
      "الفصح","عيد الفصح", // ar
      "फसह","पेसाक", // hi
      "逾越節","逾越节", // zh
      "유월절","페사흐", // ko
      "過越","ペサハ" // ja
    )
    // ── Day of Atonement / Yom Kippur ──
    pin(listOf("leviticus-16","leviticus-23","hebrews-9","hebrews-10","numbers-29"),
      "day of atonement","yom kippur","atonement","dia de la expiacion","yom kipur","jour de l'expiation","yom kippour","versohnungstag","jom kippur","giorno dell'espiazione","yom kippur","dia da expiacao",
      "день искупления","йом кипур", // ru
      "يوم الكفارة","يوم كيبور", // ar
      "प्रायश्चित का दिन","योम किप्पुर", // hi
      "贖罪日","赎罪日", // zh
      "속죄일","욤 키푸르", // ko
      "贖罪の日","ヨム・キプール" // ja
    )
    // ── Feast of Tabernacles / Sukkot ──
    pin(listOf("leviticus-23","deuteronomy-16","john-7","nehemiah-8","zechariah-14","numbers-29"),
      "feast of tabernacles","sukkot","booths","fiesta de los tabernaculos","sucot","fete des tabernacles","souccot","laubhuttenfest","sukkot","festa delle capanne","sukkot","festa dos tabernaculos","sucot",
      "праздник кущей","суккот", // ru
      "عيد المظال","سوكوت", // ar
      "झोपड़ियों का पर्व","सुक्कोत", // hi
      "住棚節","住棚节", // zh
      "초막절","수콧", // ko
      "仮庵の祭り","スコット" // ja
    )
    // ── Feast of Trumpets / Rosh Hashanah ──
    pin(listOf("leviticus-23","numbers-29","psalms-81","1-corinthians-15","1_thessalonians-4"),
      "feast of trumpets","rosh hashanah","trumpets","fiesta de las trompetas","rosh hashana","fete des trompettes","roch hachana","posaunenfest","rosch haschana","festa delle trombe","rosh hashana","festa das trombetas","rosh hashana",
      "праздник труб","рош ха-шана", // ru
      "عيد الأبواق","رأس السنة","روش هشانا", // ar
      "तुरही का पर्व","रोश हशाना", // hi
      "吹角節","吹角节", // zh
      "나팔절","로쉬 하샤나", // ko
      "ラッパの祭り","ローシュ・ハシャナー" // ja
    )
    // ── Jubilee / Year of Jubilee ──
    pin(listOf("leviticus-25","isaiah-61","luke-4","leviticus-27","numbers-36"),
      "jubilee","year of jubilee","jubileo","ano del jubileo","jubile","annee du jubile","jubeljahr","giubileo","anno del giubileo","jubileu","ano do jubileu",
      "юбилей","юбилейный год", // ru
      "يوبيل","سنة اليوبيل", // ar
      "जुबली","जुबली का वर्ष", // hi
      "禧年", // zh
      "희년","유빌리", // ko
      "ヨベルの年","ヨベル" // ja
    )
    // ── Feast of Unleavened Bread ──
    pin(listOf("exodus-12","exodus-13","leviticus-23","1-corinthians-5","deuteronomy-16","mark-14"),
      "unleavened bread","feast of unleavened bread","pan sin levadura","fiesta de los panes sin levadura","pain sans levain","fete des pains sans levain","ungesauertes brot","fest der ungesauerten brote","pane azzimo","festa degli azzimi","paes asmos","festa dos paes asmos",
      "опресноки","праздник опресноков", // ru
      "الفطير","عيد الفطير", // ar
      "अखमीरी रोटी","अखमीरी रोटी का पर्व", // hi
      "無酵餅","除酵節","无酵饼","除酵节", // zh
      "무교병","무교절", // ko
      "種なしパン","除酵祭" // ja
    )
    // ── Feast of Weeks / Shavuot / Firstfruits ──
    pin(listOf("leviticus-23","deuteronomy-16","acts-2","exodus-34","ruth-2","numbers-28"),
      "feast of weeks","shavuot","firstfruits","feast of harvest","fiesta de las semanas","shavuot","primicias","fete des semaines","chavouot","premices","wochenfest","schawuot","erstlingsfruchte","festa delle settimane","shavuot","primizie","festa das semanas","shavuot","primicias",
      "праздник седмиц","шавуот","начатки", // ru
      "عيد الأسابيع","شافوعوت","الباكورات", // ar
      "सप्ताहों का पर्व","शावुओत","पहले फल", // hi
      "七七節","五旬節","初熟節","七七节","五旬节","初熟节", // zh
      "칠칠절","샤부옷","초실절", // ko
      "七週の祭り","シャブオット","初穂の祭り" // ja
    )
    // ── Conscience ──
    pin(listOf("romans-2","1_timothy-1","hebrews-10","acts-24","1_peter-3","1-corinthians-8","2_corinthians-1"),
      "conscience","clear conscience","good conscience","conciencia","buena conciencia","conscience","bonne conscience","gewissen","gutes gewissen","coscienza","buona coscienza","consciencia","boa consciencia",
      "совесть","чистая совесть", // ru
      "ضمير","ضمير صالح", // ar
      "विवेक","शुद्ध विवेक", // hi
      "良心","無虧的良心","无亏的良心", // zh
      "양심","선한 양심", // ko
      "良心","清い良心" // ja
    )
    // ── Heart (biblical concept) ──
    pin(listOf("proverbs-4","jeremiah-17","psalms-51","ezekiel-36","matthew-15","psalms-139","proverbs-23"),
      "heart","pure heart","new heart","corazon","corazon puro","coeur","coeur pur","herz","reines herz","cuore","cuore puro","coracao","coracao puro",
      "сердце","чистое сердце","новое сердце", // ru
      "قلب","قلب نقي","قلب جديد", // ar
      "हृदय","शुद्ध हृदय","नया हृदय", // hi
      "心","清潔的心","新心","清洁的心", // zh
      "마음","깨끗한 마음","새 마음", // ko
      "心","清い心","新しい心" // ja
    )
    // ── Renewing of the Mind ──
    pin(listOf("romans-12","ephesians-4","colossians-3","philippians-4","2_corinthians-10","isaiah-26"),
      "renewing of mind","transform","transformed","renovacion de la mente","transformacion","renouvellement de l'esprit","transformation","erneuerung des sinnes","verwandlung","rinnovamento della mente","trasformazione","renovacao da mente","transformacao",
      "обновление ума","преображение", // ru
      "تجديد الذهن","تغيير", // ar
      "मन का नया होना","रूपान्तरण", // hi
      "心意更新","變化","心意更新","变化", // zh
      "마음의 새롭게 함","변화", // ko
      "心の一新","変革" // ja
    )
    // ── Flesh vs Spirit ──
    pin(listOf("galatians-5","romans-8","romans-7","john-3","1-corinthians-3","galatians-6"),
      "flesh","flesh vs spirit","carnal","fleshly","carne","carne vs espiritu","carnal","chair","chair vs esprit","charnel","fleisch","fleisch vs geist","fleischlich","carne","carne vs spirito","carnale","carne","carne vs espirito","carnal",
      "плоть","плоть против духа","плотский", // ru
      "جسد","جسد ضد الروح","جسدي", // ar
      "शरीर","शरीर बनाम आत्मा","शारीरिक", // hi
      "肉體","肉體與聖靈","肉体","肉体与圣灵", // zh
      "육","육 대 영","육적", // ko
      "肉","肉対霊","肉的" // ja
    )
    // ── Truth ──
    pin(listOf("john-14","john-8","john-17","ephesians-4","3_john-1","psalms-25","proverbs-12"),
      "truth","verdad","verite","wahrheit","verita","verdade",
      "истина","правда", // ru
      "حق","حقيقة", // ar
      "सत्य","सच्चाई", // hi
      "真理","真實","真实", // zh
      "진리","진실", // ko
      "真理","真実" // ja
    )
    // ── Purity / Clean and Unclean ──
    pin(listOf("matthew-5","psalms-24","psalms-51","titus-1","1_john-3","james-4","leviticus-11"),
      "purity","pure","clean","unclean","pureza","puro","limpio","impuro","purete","pur","propre","impur","reinheit","rein","unrein","purezza","puro","pulito","impuro","pureza","puro","limpo","impuro",
      "чистота","чистый","нечистый", // ru
      "طهارة","طاهر","نجس", // ar
      "शुद्धता","शुद्ध","अशुद्ध", // hi
      "潔淨","清潔","不潔","洁净","清洁","不洁", // zh
      "정결","깨끗한","부정한", // ko
      "清さ","清い","汚れた" // ja
    )
    // ── Apostasy / Falling Away ──
    pin(listOf("hebrews-6","2_thessalonians-2","1_timothy-4","2_peter-2","hebrews-10","jude-1"),
      "apostasy","falling away","backslide","backsliding","fall away","apostasia","caer","retroceder","apostasie","chute","rechute","abfall","apostasie","ruckfall","apostasia","cadere","ricadere","apostasia","recair",
      "отступничество","отпадение","вероотступничество", // ru
      "ارتداد","سقوط","ردة", // ar
      "धर्मत्याग","पीछे हटना","भटकना", // hi
      "背道","離棄","倒退","背道","离弃","倒退", // zh
      "배교","타락","변절", // ko
      "背教","堕落","離反" // ja
    )
    // ── Hardened Heart ──
    pin(listOf("exodus-7","hebrews-3","hebrews-4","mark-3","romans-9","ezekiel-36","proverbs-28"),
      "hardened heart","hard heart","stiff-necked","stubborn","corazon endurecido","duro de cerviz","coeur endurci","cou raide","verhartetes herz","halsstarrig","cuore indurito","collo duro","coracao endurecido","obstinado",
      "ожесточенное сердце","жестоковыйный", // ru
      "قلب قاس","صلب الرقبة", // ar
      "कठोर हृदय","हठीला", // hi
      "剛硬的心","硬著頸項","刚硬的心","硬着颈项", // zh
      "완고한 마음","목이 뻣뻣한", // ko
      "かたくなな心","強情" // ja
    )
    // ── Hospitality ──
    pin(listOf("hebrews-13","romans-12","1_peter-4","3_john-1","genesis-18","acts-16","matthew-25"),
      "hospitality","hospitable","welcome","hospitalidad","hospitalario","hospitalite","hospitalier","gastfreundschaft","gastfreundlich","ospitalita","ospitale","hospitalidade",
      "гостеприимство","странноприимство", // ru
      "ضيافة","كرم الضيافة", // ar
      "अतिथि सत्कार","पहुनाई", // hi
      "接待","好客","款待", // zh
      "대접","환대","접대", // ko
      "もてなし","歓待" // ja
    )
    // ── Orphans and Widows ──
    pin(listOf("james-1","deuteronomy-10","isaiah-1","psalms-68","psalms-146","exodus-22","zechariah-7"),
      "orphan","orphans","widow","widows","fatherless","huerfano","viuda","orphelin","veuve","waise","witwe","orfano","vedova","orfao","viuva",
      "сирота","вдова","сироты","вдовы", // ru
      "يتيم","أرملة","يتامى","أرامل", // ar
      "अनाथ","विधवा", // hi
      "孤兒","寡婦","孤儿","寡妇", // zh
      "고아","과부", // ko
      "孤児","やもめ" // ja
    )
    // ── Death / Mortality ──
    pin(listOf("1-corinthians-15","romans-6","hebrews-9","psalms-23","psalms-116","ecclesiastes-3","revelation-21"),
      "death","dying","mortality","what happens after death","muerte","morir","mort","mourir","tod","sterben","morte","morire","morte","morrer",
      "смерть","умирание","смертность", // ru
      "موت","وفاة", // ar
      "मृत्यु","मरना", // hi
      "死","死亡", // zh
      "죽음","사망", // ko
      "死","死ぬこと" // ja
    )
    // ── Witchcraft / Sorcery / Occult ──
    pin(listOf("deuteronomy-18","galatians-5","revelation-21","1-samuel-28","acts-19","isaiah-47","exodus-22"),
      "witchcraft","sorcery","occult","divination","brujeria","hechiceria","sorcellerie","occultisme","divination","hexerei","zauberei","okkultismus","stregoneria","occultismo","divinazione","bruxaria","feiticaria","ocultismo",
      "колдовство","чародейство","оккультизм","ворожба", // ru
      "سحر","شعوذة","غيبيات","عرافة", // ar
      "जादू-टोना","टोना","तान्त्रिक", // hi
      "巫術","邪術","占卜","巫术","邪术", // zh
      "마술","주술","점술","점치기", // ko
      "魔術","呪術","占い" // ja
    )
    // ── Meditation on Scripture ──
    pin(listOf("psalms-1","psalms-119","joshua-1","psalms-63","psalms-77","psalms-143"),
      "meditation","meditate","meditate on the word","meditacion","meditar","meditation","mediter","meditation","meditieren","meditazione","meditare","meditacao","meditar",
      "размышление","медитация","помышление", // ru
      "تأمل","تدبر", // ar
      "ध्यान","मनन", // hi
      "默想","默念","默想", // zh
      "묵상","명상", // ko
      "黙想","瞑想" // ja
    )
    // ── Way Truth and Life ──
    pin(listOf("john-14","john-11","john-10","john-8","john-6","acts-4"),
      "way truth life","i am the way","camino verdad vida","yo soy el camino","chemin verite vie","je suis le chemin","weg wahrheit leben","ich bin der weg","via verita vita","io sono la via","caminho verdade vida","eu sou o caminho",
      "путь истина жизнь","я есть путь", // ru
      "الطريق والحق والحياة","أنا هو الطريق", // ar
      "मार्ग सत्य जीवन","मार्ग मैं हूँ", // hi
      "道路真理生命","我就是道路", // zh
      "길 진리 생명","내가 곧 길이요", // ko
      "道 真理 命","わたしは道であり" // ja
    )
    // ── I am the Door / Gate ──
    pin(listOf("john-10","matthew-7","luke-13","revelation-3","john-14"),
      "i am the door","i am the gate","door of the sheep","yo soy la puerta","je suis la porte","ich bin die tur","io sono la porta","eu sou a porta",
      "я есть дверь","дверь овцам", // ru
      "أنا هو الباب","باب الخراف", // ar
      "मैं द्वार हूँ","भेड़ों का द्वार", // hi
      "我就是門","我就是门", // zh
      "내가 문이니","양의 문", // ko
      "わたしは門である","羊の門" // ja
    )
    // ── Suffering Servant / Isaiah 53 ──
    pin(listOf("isaiah-53","isaiah-52","1_peter-2","acts-8","matthew-8","philippians-2"),
      "suffering servant","man of sorrows","siervo sufriente","varon de dolores","serviteur souffrant","homme de douleur","leidender knecht","mann der schmerzen","servo sofferente","uomo dei dolori","servo sofredor","homem de dores",
      "страдающий раб","муж скорбей", // ru
      "العبد المتألم","رجل الأوجاع", // ar
      "दुःखी दास","व्यथित पुरुष", // hi
      "受苦的僕人","憂患之子","受苦的仆人","忧患之子", // zh
      "고난의 종","슬픔의 사람", // ko
      "苦難のしもべ","悲しみの人" // ja
    )
    // ── Messianic Prophecy ──
    pin(listOf("isaiah-7","isaiah-9","isaiah-53","micah-5","psalms-22","daniel-9","zechariah-9"),
      "messianic prophecy","prophecy of christ","prophecies about jesus","profecia mesianica","profecias sobre jesus","prophetie messianique","propheties sur jesus","messianische prophetie","profezia messianica","profecia messianica","profecias sobre jesus",
      "мессианское пророчество","пророчества о христе", // ru
      "نبوءة مسيانية","نبوءات عن المسيح", // ar
      "मसीहाई भविष्यवाणी","मसीह के बारे में भविष्यवाणी", // hi
      "彌賽亞預言","弥赛亚预言", // zh
      "메시아 예언","그리스도에 대한 예언", // ko
      "メシア預言","キリストに関する預言" // ja
    )
    // ── Seven Churches of Revelation ──
    pin(listOf("revelation-2","revelation-3","revelation-1"),
      "seven churches","letters to churches","siete iglesias","cartas a las iglesias","sept eglises","lettres aux eglises","sieben gemeinden","briefe an die gemeinden","sette chiese","lettere alle chiese","sete igrejas","cartas as igrejas",
      "семь церквей","послания церквам", // ru
      "الكنائس السبع","رسائل الكنائس", // ar
      "सात कलीसियाएँ","कलीसियाओं को पत्र", // hi
      "七個教會","寫給教會的信","七个教会","写给教会的信", // zh
      "일곱 교회","교회들에게 보내는 편지", // ko
      "七つの教会","教会への手紙" // ja
    )
    // ── Four Horsemen ──
    pin(listOf("revelation-6","zechariah-6","zechariah-1"),
      "four horsemen","horsemen of apocalypse","white horse","red horse","black horse","pale horse","cuatro jinetes","jinetes del apocalipsis","quatre cavaliers","cavaliers de l'apocalypse","vier reiter","reiter der apokalypse","quattro cavalieri","cavalieri dell'apocalisse","quatro cavaleiros","cavaleiros do apocalipse",
      "четыре всадника","всадники апокалипсиса", // ru
      "الفرسان الأربعة","فرسان سفر الرؤيا", // ar
      "चार घुड़सवार","प्रकाशितवाक्य के घुड़सवार", // hi
      "四騎士","四个骑士", // zh
      "네 기사","묵시록의 기사", // ko
      "四騎士","黙示録の騎士" // ja
    )
    // ── 144,000 ──
    pin(listOf("revelation-7","revelation-14"),
      "144000","144,000","hundred forty four thousand","one hundred forty-four thousand","ciento cuarenta y cuatro mil","cent quarante-quatre mille","hundertvierundvierzigtausend","centoquarantaquattromila","cento e quarenta e quatro mil",
      "сто сорок четыре тысячи","144 тысячи", // ru
      "مئة وأربعة وأربعون ألفا", // ar
      "एक लाख चौवालीस हज़ार", // hi
      "十四萬四千","十四万四千", // zh
      "십사만 사천","144000", // ko
      "十四万四千" // ja
    )
    // ── Two Witnesses ──
    pin(listOf("revelation-11","zechariah-4","malachi-4"),
      "two witnesses","dos testigos","deux temoins","zwei zeugen","due testimoni","duas testemunhas",
      "два свидетеля", // ru
      "الشاهدان", // ar
      "दो गवाह", // hi
      "兩個見證人","两个见证人", // zh
      "두 증인", // ko
      "二人の証人" // ja
    )
    // ── Babylon the Great ──
    pin(listOf("revelation-17","revelation-18","revelation-14","jeremiah-50","jeremiah-51","isaiah-21"),
      "babylon","mystery babylon","babylon the great","fall of babylon","babilonia","gran babilonia","caida de babilonia","babylone","grande babylone","chute de babylone","babylon","grosses babylon","fall babylons","babilonia","grande babilonia","caduta di babilonia","babilonia","grande babilonia","queda da babilonia",
      "вавилон","великий вавилон","падение вавилона", // ru
      "بابل","بابل العظيمة","سقوط بابل", // ar
      "बाबुल","महान बाबुल","बाबुल का पतन", // hi
      "巴比倫","大巴比倫","巴比伦","大巴比伦", // zh
      "바벨론","큰 바벨론","바벨론의 멸망", // ko
      "バビロン","大いなるバビロン","バビロンの滅亡" // ja
    )
    // ── Throne of God ──
    pin(listOf("revelation-4","revelation-5","isaiah-6","ezekiel-1","daniel-7","psalms-47","hebrews-4"),
      "throne of god","throne","heavenly throne","trono de dios","trono","trone de dieu","trone","thron gottes","thron","trono di dio","trono","trono de deus","trono",
      "престол божий","престол", // ru
      "عرش الله","عرش", // ar
      "परमेश्वर का सिंहासन","सिंहासन", // hi
      "神的寶座","寶座","神的宝座","宝座", // zh
      "하나님의 보좌","보좌", // ko
      "神の御座","御座" // ja
    )
    // ── Judgment Seat of Christ / Bema ──
    pin(listOf("2_corinthians-5","romans-14","1-corinthians-3","matthew-16","revelation-22"),
      "judgment seat","judgment seat of christ","bema","tribunal de cristo","tribunal","tribunal de christ","richterstuhl","richterstuhl christi","tribunale di cristo","tribunal de cristo",
      "судилище христово","суд христов", // ru
      "كرسي المسيح للدينونة","كرسي الحكم", // ar
      "मसीह का न्याय आसन", // hi
      "基督的審判臺","基督的审判台", // zh
      "그리스도의 심판대","심판대", // ko
      "キリストの裁きの座","裁きの座" // ja
    )
    // ── Many Rooms / Mansions ──
    pin(listOf("john-14","luke-16","hebrews-11","revelation-21"),
      "mansions","many rooms","fathers house","many mansions","moradas","muchas moradas","casa de mi padre","demeures","nombreuses demeures","maison de mon pere","wohnungen","viele wohnungen","haus meines vaters","dimore","molte dimore","casa del padre mio","moradas","muitas moradas","casa de meu pai",
      "обители","много обителей","дом отца моего", // ru
      "منازل","منازل كثيرة","بيت أبي", // ar
      "निवास","बहुत से निवास","मेरे पिता के घर", // hi
      "住處","許多住處","我父的家","住处","许多住处","我父的家", // zh
      "처소","많은 처소","아버지의 집", // ko
      "住まい","多くの住まい","父の家" // ja
    )
    // ── River of Life ──
    pin(listOf("revelation-22","ezekiel-47","genesis-2","john-7","psalms-46","zechariah-14"),
      "river of life","water of life","rio de vida","agua de vida","fleuve de vie","eau de vie","strom des lebens","wasser des lebens","fiume della vita","acqua della vita","rio da vida","agua da vida",
      "река жизни","вода жизни", // ru
      "نهر الحياة","ماء الحياة", // ar
      "जीवन की नदी","जीवन का जल", // hi
      "生命河","生命水", // zh
      "생명의 강","생명의 물", // ko
      "命の川","命の水" // ja
    )
    // ── Exile / Babylonian Captivity ──
    pin(listOf("2-kings-25","jeremiah-29","daniel-1","ezekiel-1","psalms-137","2chronicles-36"),
      "exile","captivity","babylonian exile","babylonian captivity","exilio","cautividad","cautiverio babilonico","exil","captivite","captivite babylonienne","exil","gefangenschaft","babylonisches exil","esilio","cattivita","cattivita babilonese","exilio","cativeiro","cativeiro babilonico",
      "изгнание","плен","вавилонское пленение", // ru
      "سبي","أسر","السبي البابلي", // ar
      "निर्वासन","बन्धुवाई","बाबुल की बन्धुवाई", // hi
      "被擄","被掳","巴比倫之囚","巴比伦之囚", // zh
      "포로","유배","바벨론 포로", // ko
      "捕囚","バビロン捕囚" // ja
    )
    // ── Restoration of Israel ──
    pin(listOf("ezekiel-37","isaiah-11","amos-9","jeremiah-30","jeremiah-31","ezra-1","romans-11"),
      "restoration","restore","restoration of israel","return from exile","restauracion","restauracion de israel","restauration","restauration d'israel","wiederherstellung","wiederherstellung israels","restaurazione","restaurazione di israele","restauracao","restauracao de israel",
      "восстановление","восстановление израиля", // ru
      "استعادة","استعادة إسرائيل", // ar
      "पुनर्स्थापना","इस्राएल की पुनर्स्थापना", // hi
      "復興","以色列的復興","复兴","以色列的复兴", // zh
      "회복","이스라엘의 회복", // ko
      "回復","イスラエルの回復" // ja
    )
    // ── Signs and Wonders ──
    pin(listOf("acts-2","acts-5","hebrews-2","john-4","exodus-7","deuteronomy-6","mark-16"),
      "signs and wonders","signs","wonders","senales y prodigios","senales","prodigios","signes et prodiges","signes","prodiges","zeichen und wunder","zeichen","wunder","segni e prodigi","segni","prodigi","sinais e prodigios","sinais","prodigios",
      "знамения и чудеса","знамения","чудеса", // ru
      "آيات وعجائب","آيات","عجائب", // ar
      "चिह्न और अद्भुत काम","चिह्न","अद्भुत काम", // hi
      "神蹟奇事","神迹奇事", // zh
      "표적과 기사","표적","기사", // ko
      "しるしと不思議","しるし","不思議" // ja
    )
    // ── Deliverance / Exorcism ──
    pin(listOf("mark-5","matthew-12","luke-10","acts-16","mark-1","matthew-8","luke-4"),
      "deliverance","exorcism","cast out demons","casting out demons","liberacion","exorcismo","echar fuera demonios","delivrance","exorcisme","chasser les demons","befreiung","exorzismus","damonen austreiben","liberazione","esorcismo","scacciare demoni","libertacao","exorcismo","expulsar demonios",
      "освобождение","изгнание бесов","экзорцизм", // ru
      "تحرير","طرد الأرواح","طرد الشياطين", // ar
      "छुटकारा","दुष्टात्माओं को निकालना", // hi
      "釋放","趕鬼","释放","赶鬼", // zh
      "해방","축사","귀신 쫓기", // ko
      "解放","悪霊追い出し","悪魔祓い" // ja
    )
    // ── Running the Race / Fight the Good Fight ──
    pin(listOf("hebrews-12","1-corinthians-9","2_timothy-4","philippians-3","galatians-5","1_timothy-6"),
      "running the race","run the race","fight the good fight","press on","correr la carrera","pelear la buena batalla","courir la course","combattre le bon combat","den lauf laufen","den guten kampf kampfen","correre la corsa","combattere il buon combattimento","correr a carreira","combater o bom combate",
      "бежать на ристалище","подвизаться добрым подвигом", // ru
      "الجري في السباق","جاهد الجهاد الحسن", // ar
      "दौड़ दौड़ना","अच्छी लड़ाई लड़ना", // hi
      "奔跑賽程","打美好的仗","奔跑赛程","打美好的仗", // zh
      "달려야 할 길","선한 싸움 싸우기", // ko
      "走るべき行程","良い戦いを戦う" // ja
    )
    // ── Cloud of Witnesses ──
    pin(listOf("hebrews-11","hebrews-12"),
      "cloud of witnesses","hall of faith","heroes of faith","nube de testigos","heroes de la fe","nuee de temoins","heros de la foi","wolke von zeugen","glaubenshelden","nuvola di testimoni","eroi della fede","nuvem de testemunhas","herois da fe",
      "облако свидетелей","герои веры", // ru
      "سحابة من الشهود","أبطال الإيمان", // ar
      "गवाहों का बादल","विश्वास के वीर", // hi
      "如同雲彩的見證人","信心的英雄","如同云彩的见证人","信心的英雄", // zh
      "구름같이 둘러싼 증인들","믿음의 영웅들", // ko
      "雲のような証人","信仰の勇者" // ja
    )
    // ── Agape / God's Love ──
    pin(listOf("1_john-4","john-3","romans-5","romans-8","1-corinthians-13","ephesians-3","jeremiah-31"),
      "agape","gods love","unconditional love","love of god","agape","amor de dios","amor incondicional","agape","amour de dieu","amour inconditionnel","agape","gottes liebe","bedingungslose liebe","agape","amore di dio","amore incondizionato","agape","amor de deus","amor incondicional",
      "агапе","божья любовь","безусловная любовь", // ru
      "أغابي","محبة الله","حب غير مشروط", // ar
      "अगापे","परमेश्वर का प्रेम","बिना शर्त प्रेम", // hi
      "聖愛","神的愛","無條件的愛","圣爱","神的爱","无条件的爱", // zh
      "아가페","하나님의 사랑","무조건적 사랑", // ko
      "アガペー","神の愛","無条件の愛" // ja
    )
    // ── Brotherly Love ──
    pin(listOf("romans-12","1_thessalonians-4","hebrews-13","1_peter-1","john-13","1_john-3","1_john-4"),
      "brotherly love","love one another","philadelphia","amor fraternal","amaos unos a otros","amour fraternel","aimez-vous les uns les autres","bruderliebe","liebet einander","amore fraterno","amatevi gli uni gli altri","amor fraternal","amai-vos uns aos outros",
      "братская любовь","любите друг друга", // ru
      "محبة أخوية","أحبوا بعضكم بعضا", // ar
      "भ्रातृ प्रेम","एक दूसरे से प्रेम रखो", // hi
      "弟兄相愛","彼此相愛","弟兄相爱","彼此相爱", // zh
      "형제 사랑","서로 사랑하라", // ko
      "兄弟愛","互いに愛し合う" // ja
    )
    // ── Faith and Works ──
    pin(listOf("james-2","galatians-2","ephesians-2","romans-4","titus-3","james-1"),
      "faith and works","works","deeds","faith without works","fe y obras","obras","fe sin obras","foi et oeuvres","oeuvres","foi sans oeuvres","glaube und werke","werke","glaube ohne werke","fede e opere","opere","fede senza opere","fe e obras","obras","fe sem obras",
      "вера и дела","дела","вера без дел", // ru
      "إيمان وأعمال","أعمال","إيمان بدون أعمال", // ar
      "विश्वास और कर्म","कर्म","बिना कर्मों का विश्वास", // hi
      "信心和行為","行為","沒有行為的信心","信心和行为","行为","没有行为的信心", // zh
      "믿음과 행함","행함","행함이 없는 믿음", // ko
      "信仰と行い","行い","行いのない信仰" // ja
    )
    // ── Abraham's Covenant ──
    pin(listOf("genesis-12","genesis-15","genesis-17","galatians-3","romans-4","hebrews-6","acts-7"),
      "abrahamic covenant","covenant with abraham","abrahams covenant","pacto abrahamico","pacto con abraham","alliance abrahamique","alliance avec abraham","abrahamitischer bund","bund mit abraham","patto abramitico","alleanza con abramo","alianca abraamica","alianca com abraao",
      "авраамов завет","завет с авраамом", // ru
      "العهد الإبراهيمي","عهد إبراهيم", // ar
      "अब्राहम की वाचा","इब्राहीम की वाचा", // hi
      "亞伯拉罕之約","與亞伯拉罕立的約","亚伯拉罕之约","与亚伯拉罕立的约", // zh
      "아브라함의 언약","아브라함 언약", // ko
      "アブラハム契約","アブラハムとの契約" // ja
    )
    // ── Davidic Covenant ──
    pin(listOf("2-samuel-7","1chronicles-17","psalms-89","psalms-132","acts-2","luke-1"),
      "davidic covenant","covenant with david","davids throne","pacto davidico","pacto con david","trono de david","alliance davidique","alliance avec david","trone de david","davidischer bund","bund mit david","thron davids","patto davidico","alleanza con davide","trono di davide","alianca davidica","alianca com davi","trono de davi",
      "давидов завет","завет с давидом","престол давида", // ru
      "العهد الداودي","عهد داود","عرش داود", // ar
      "दाऊद की वाचा","दाऊद का सिंहासन", // hi
      "大衛之約","大衛的寶座","大卫之约","大卫的宝座", // zh
      "다윗의 언약","다윗의 보좌", // ko
      "ダビデ契約","ダビデの王座" // ja
    )
    // ── Worldliness / Love of the World ──
    pin(listOf("1_john-2","james-4","romans-12","john-17","colossians-3","titus-2"),
      "worldliness","worldly","love of the world","mundaneria","mundano","amor del mundo","mondanite","mondain","amour du monde","weltlichkeit","weltlich","liebe zur welt","mondanita","mondano","amore del mondo","mundanidade","mundano","amor do mundo",
      "мирское","мирской","любовь к миру","обмирщение", // ru
      "دنيوية","حب العالم","محبة العالم", // ar
      "सांसारिकता","संसार का प्रेम", // hi
      "世俗","貪愛世界","贪爱世界", // zh
      "세속","세상 사랑", // ko
      "世俗","世を愛する" // ja
    )
    // ── Blasphemy / Unforgivable Sin ──
    pin(listOf("matthew-12","mark-3","luke-12","hebrews-6","hebrews-10","1_john-5"),
      "blasphemy","blasphemy against the spirit","unforgivable sin","unpardonable sin","blasfemia","blasfemia contra el espiritu","pecado imperdonable","blaspheme","blaspheme contre l'esprit","peche impardonnable","blasphemie","lasterung des geistes","unvergebbare sunde","bestemmia","bestemmia contro lo spirito","peccato imperdonabile","blasfemia","blasfemia contra o espirito","pecado imperdoavel",
      "хула","хула на духа святого","непростительный грех", // ru
      "تجديف","التجديف على الروح القدس","الخطيئة التي لا تغتفر", // ar
      "ईशनिन्दा","पवित्र आत्मा के विरुद्ध निन्दा","अक्षम्य पाप", // hi
      "褻瀆","褻瀆聖靈","不可赦免的罪","亵渎","亵渎圣灵","不可赦免的罪", // zh
      "모독","성령 모독","용서받지 못할 죄", // ko
      "冒涜","聖霊に対する冒涜","赦されない罪" // ja
    )
    // ── Thirst for God / Spiritual Hunger ──
    pin(listOf("psalms-42","psalms-63","matthew-5","isaiah-55","john-4","john-7","psalms-143"),
      "thirst for god","hunger for god","spiritual hunger","spiritual thirst","sed de dios","hambre de dios","soif de dieu","faim de dieu","durst nach gott","hunger nach gott","sete di dio","fame di dio","sede de deus","fome de deus",
      "жажда бога","голод по богу", // ru
      "عطش لله","جوع لله", // ar
      "परमेश्वर की प्यास","आत्मिक भूख", // hi
      "渴慕神","飢渴慕義","渴慕神","饥渴慕义", // zh
      "하나님을 갈망","영적 갈급", // ko
      "神への渇き","霊的な飢え渇き" // ja
    )
    // ── Rest / Entering God's Rest (Hebrews) ──
    pin(listOf("hebrews-4","hebrews-3","matthew-11","psalms-23","psalms-46","exodus-33","genesis-2"),
      "rest","rest in god","enter his rest","gods rest","descanso","descanso en dios","entrar en su reposo","repos","repos en dieu","entrer dans son repos","ruhe","ruhe in gott","in seine ruhe eingehen","riposo","riposo in dio","entrare nel suo riposo","descanso","descanso em deus","entrar no seu descanso",
      "покой","покой божий","войти в покой его", // ru
      "راحة","الراحة في الله","ادخلوا راحته", // ar
      "विश्राम","परमेश्वर का विश्राम","उसके विश्राम में प्रवेश", // hi
      "安息","神的安息","進入祂的安息","进入他的安息", // zh
      "안식","하나님의 안식","그의 안식에 들어감", // ko
      "安息","神の安息","その安息に入る" // ja
    )
    // ── Sojourner / Pilgrim ──
    pin(listOf("hebrews-11","1_peter-2","1_peter-1","genesis-23","psalms-39","leviticus-25","1chronicles-29"),
      "sojourner","pilgrim","stranger","alien","foreigner","peregrino","extranjero","forastero","pelerin","etranger","pilgrim","fremdling","pellegrino","straniero","forestiero","peregrino","estrangeiro","forasteiro",
      "странник","пришелец","пилигрим", // ru
      "غريب","متغرب","حاج", // ar
      "परदेशी","यात्री","मुसाफिर", // hi
      "寄居的","客旅","外人", // zh
      "나그네","순례자","거류민", // ko
      "寄留者","旅人","巡礼者" // ja
    )
    // ── Tabernacle / Temple ──
    pin(listOf("exodus-25","exodus-40","1-kings-6","2chronicles-7","john-2","1-corinthians-3","1-corinthians-6","revelation-21"),
      "tabernacle","temple","tent of meeting","dwelling","tabernaculo","templo","tienda de reunion","tabernacle","temple","tente de la rencontre","stiftshutte","tempel","zelt der begegnung","tabernacolo","tempio","tenda del convegno","tabernaculo","templo","tenda da congregacao",
      "скиния","храм","шатер собрания", // ru
      "خيمة الاجتماع","هيكل","مسكن", // ar
      "मिलापवाला तम्बू","मन्दिर","निवास", // hi
      "會幕","聖殿","帳幕","会幕","圣殿","帐幕", // zh
      "성막","성전","회막", // ko
      "幕屋","神殿","会見の天幕" // ja
    )
    // ── Royal Priesthood ──
    pin(listOf("1_peter-2","exodus-19","revelation-1","revelation-5","revelation-20","isaiah-61"),
      "royal priesthood","priesthood of believers","holy nation","kingdom of priests","real sacerdocio","sacerdocio de creyentes","nacion santa","sacerdoce royal","sacerdoce de tous les croyants","nation sainte","konigliches priestertum","priestertum aller glaubigen","heilige nation","sacerdozio regale","sacerdozio di tutti i credenti","nazione santa","sacerdocio real","sacerdocio de todos os crentes","nacao santa",
      "царственное священство","священство верующих","святой народ", // ru
      "كهنوت ملوكي","كهنوت المؤمنين","أمة مقدسة", // ar
      "राजकीय याजकवर्ग","विश्वासियों का याजकपद","पवित्र जाति", // hi
      "君尊的祭司","信徒皆祭司","聖潔的國民","圣洁的国民", // zh
      "왕 같은 제사장","만인 제사장","거룩한 나라", // ko
      "王である祭司","万人祭司","聖なる国民" // ja
    )
    // ── Watchfulness / Be Alert ──
    pin(listOf("matthew-24","matthew-25","mark-13","1_thessalonians-5","1_peter-5","luke-21","revelation-3"),
      "watchfulness","watch","be alert","stay awake","keep watch","vigilancia","velar","estar alerta","vigilance","veiller","etre vigilant","wachsamkeit","wachen","wachsam sein","vigilanza","vegliare","stare all'erta","vigilancia","vigiar","estar alerta",
      "бдительность","бодрствуйте","будьте бдительны", // ru
      "سهر","اسهروا","كونوا متيقظين", // ar
      "जागते रहो","सतर्क रहो","चौकस रहो", // hi
      "儆醒","警醒","要謹慎","要谨慎", // zh
      "깨어 있으라","근신하라","경성하라", // ko
      "目を覚ましていなさい","警戒せよ" // ja
    )
    // ── Waiting on God ──
    pin(listOf("isaiah-40","psalms-27","psalms-37","psalms-130","lamentations-3","habakkuk-2","micah-7"),
      "waiting on god","wait on the lord","wait for god","esperar en dios","esperar al senor","attendre dieu","attendre le seigneur","auf gott warten","auf den herrn warten","aspettare dio","attendere il signore","esperar em deus","esperar no senhor",
      "ждать бога","уповать на господа","ожидание", // ru
      "انتظار الرب","ترقب الله", // ar
      "प्रभु की बाट जोहना","परमेश्वर की प्रतीक्षा", // hi
      "等候神","等候耶和華","等候耶和华", // zh
      "하나님을 기다림","여호와를 기다리라", // ko
      "主を待ち望む","神を待つ" // ja
    )
    // ── Revival / Renewal ──
    pin(listOf("psalms-85","habakkuk-3","acts-3","2chronicles-7","hosea-6","isaiah-57","ezekiel-37"),
      "revival","renewal","spiritual awakening","avivamiento","renovacion","despertar espiritual","reveil","renouveau","reveil spirituel","erweckung","erneuerung","geistliche erweckung","risveglio","rinnovamento","risveglio spirituale","avivamento","renovacao","despertar espiritual",
      "пробуждение","обновление","духовное пробуждение", // ru
      "انتعاش","تجديد","يقظة روحية", // ar
      "पुनरुत्थान","नवीनीकरण","आत्मिक जागृति", // hi
      "復興","更新","屬靈覺醒","复兴","更新","属灵觉醒", // zh
      "부흥","갱신","영적 각성", // ko
      "リバイバル","刷新","霊的覚醒" // ja
    )
    // ── Refiner's Fire / Purification ──
    pin(listOf("malachi-3","1_peter-1","zechariah-13","isaiah-48","proverbs-17","james-1","1_peter-4"),
      "refiners fire","refining fire","purification","purify","testing","fuego purificador","purificacion","purificar","prueba","feu du raffineur","purification","purifier","epreuve","lauterungsfeuer","reinigung","prufung","fuoco del raffinatore","purificazione","purificare","prova","fogo refinador","purificacao","purificar","prova",
      "огонь очищения","очищение","испытание", // ru
      "نار التنقية","تطهير","اختبار", // ar
      "शुद्ध करने वाली आग","शुद्धिकरण","परीक्षा", // hi
      "煉淨的火","潔淨","試煉","炼净的火","洁净","试炼", // zh
      "연단의 불","정화","시련", // ko
      "精錬の火","清め","試練" // ja
    )
    // ── Wilderness / Desert Experience ──
    pin(listOf("exodus-16","deuteronomy-8","matthew-4","numbers-14","1-kings-19","hosea-2","psalms-63"),
      "wilderness","desert","wasteland","desierto","yermo","desert","deserto","wuste","deserto","deserto",
      "пустыня","пустошь", // ru
      "برية","صحراء","قفر", // ar
      "जंगल","मरुभूमि","निर्जन स्थान", // hi
      "曠野","荒漠","旷野","荒漠", // zh
      "광야","사막", // ko
      "荒野","荒れ野" // ja
    )
    // ── Lovingkindness / Hesed ──
    pin(listOf("psalms-136","psalms-103","lamentations-3","hosea-6","micah-6","psalms-23","ruth-1"),
      "lovingkindness","loving kindness","steadfast love","hesed","loyal love","misericordia","amor leal","bondad amorosa","bonte","amour fidele","amour indefectible","gnade","treue liebe","gute","amore leale","benignita","amor leal","bondade","misericordia",
      "милосердие","милость","хесед","неизменная любовь", // ru
      "رحمة","محبة ثابتة","حسد", // ar
      "करुणा","अचल प्रेम","हेसेद", // hi
      "慈愛","堅定的愛","慈爱","坚定的爱", // zh
      "인자","한결같은 사랑","헤세드", // ko
      "慈しみ","不変の愛","ヘセド" // ja
    )
    // ── Remnant ──
    pin(listOf("romans-11","isaiah-10","isaiah-11","micah-5","zephaniah-3","jeremiah-23","ezekiel-6"),
      "remnant","faithful remnant","remanente","resto fiel","reste","reste fidele","uberrest","treuer uberrest","residuo","resto fedele","remanescente","resto fiel",
      "остаток","верный остаток", // ru
      "البقية","البقية الأمينة", // ar
      "बचे हुए लोग","विश्वासयोग्य शेष", // hi
      "餘民","剩餘的人","余民","剩余的人", // zh
      "남은 자","충성된 남은 자", // ko
      "残りの者","忠実な残りの者" // ja
    )
    // ── Chosen People / Elect ──
    pin(listOf("deuteronomy-7","1_peter-2","isaiah-43","colossians-3","matthew-24","john-15","ephesians-1"),
      "chosen","chosen people","elect","chosen ones","elegido","pueblo elegido","escogido","choisi","peuple elu","elu","erwahlt","auserwahlt","auserwahltes volk","eletto","popolo eletto","scelto","escolhido","povo escolhido","eleito",
      "избранный","избранный народ","избранные", // ru
      "مختار","شعب مختار","المختارون", // ar
      "चुने हुए","चुने हुए लोग", // hi
      "揀選","選民","蒙揀選的","拣选","选民","蒙拣选的", // zh
      "택함 받은","선민","택함 받은 자", // ko
      "選ばれた","選びの民","選ばれた者" // ja
    )
    // ── Firstborn / Birthright ──
    pin(listOf("colossians-1","hebrews-1","hebrews-12","exodus-13","genesis-25","romans-8","revelation-1"),
      "firstborn","birthright","primogenito","primogenitura","premier-ne","droit d'ainesse","erstgeborener","erstgeburtsrecht","primogenito","primogenitura","primogenito","primogenitura",
      "первенец","первородство","право первородства", // ru
      "بكر","حق البكورية", // ar
      "पहिलौठा","ज्येष्ठाधिकार", // hi
      "長子","長子的名分","长子","长子的名分", // zh
      "장자","장자의 명분", // ko
      "長子","長子の権利" // ja
    )
    // ── Seed / Offspring / Promise ──
    pin(listOf("genesis-3","genesis-12","genesis-22","galatians-3","romans-4","genesis-15","isaiah-53"),
      "seed","offspring","seed of abraham","descendant","simiente","descendencia","simiente de abraham","semence","descendance","semence d'abraham","same","nachkomme","same abrahams","seme","discendenza","seme di abramo","semente","descendencia","semente de abraao",
      "семя","потомство","семя авраама", // ru
      "نسل","ذرية","نسل إبراهيم", // ar
      "वंश","सन्तान","इब्राहीम का वंश", // hi
      "後裔","後代","亞伯拉罕的後裔","后裔","后代","亚伯拉罕的后裔", // zh
      "씨","자손","아브라함의 자손", // ko
      "子孫","末裔","アブラハムの子孫" // ja
    )
    // ── Sacrifice System / Offerings ──
    pin(listOf("leviticus-1","leviticus-3","leviticus-4","leviticus-7","hebrews-10","hebrews-9","numbers-28"),
      "burnt offering","sin offering","peace offering","grain offering","guilt offering","holocausto","ofrenda por el pecado","ofrenda de paz","ofrenda de grano","holocauste","sacrifice pour le peche","sacrifice de paix","offrande de cereales","brandopfer","sundopfer","friedensopfer","speiseopfer","olocausto","sacrificio per il peccato","sacrificio di pace","offerta di cereali","holocausto","oferta pelo pecado","oferta de paz","oferta de cereais",
      "всесожжение","жертва за грех","мирная жертва","хлебное приношение", // ru
      "محرقة","ذبيحة خطيئة","ذبيحة سلامة","تقدمة حبوب", // ar
      "होमबलि","पापबलि","मेलबलि","अन्नबलि", // hi
      "燔祭","贖罪祭","平安祭","素祭","赎罪祭", // zh
      "번제","속죄제","화목제","소제", // ko
      "燔祭","罪祭","和解の供え物","穀物の供え物" // ja
    )
    // ── Modesty / Clothing ──
    pin(listOf("1_timothy-2","1_peter-3","proverbs-31","isaiah-3","1-corinthians-6","romans-12"),
      "modesty","modest","clothing","dress","modestia","vestimenta","modestie","vetement","bescheidenheit","kleidung","modestia","abbigliamento","modestia","vestuario",
      "скромность","одежда","одеяние", // ru
      "حشمة","احتشام","لباس", // ar
      "शालीनता","वस्त्र","पहनावा", // hi
      "端莊","衣著","端庄","衣着", // zh
      "정숙","단정","옷차림", // ko
      "慎み","服装","身なり" // ja
    )
    // ── Gambling / Casting Lots ──
    pin(listOf("proverbs-16","proverbs-13","1_timothy-6","hebrews-13","matthew-27","acts-1","jonah-1"),
      "gambling","casting lots","lot","gamble","apuestas","echar suertes","suerte","jeux de hasard","tirer au sort","sort","glucksspiel","loswurf","los","gioco d'azzardo","tirare a sorte","sorte","jogo","lancar sortes","sorte",
      "азартные игры","жребий","бросание жребия", // ru
      "قمار","قرعة","إلقاء القرعة", // ar
      "जुआ","चिट्ठी","चिट्ठी डालना", // hi
      "賭博","抽籤","拈鬮","赌博","抽签","拈阄", // zh
      "도박","제비","제비 뽑기", // ko
      "賭け","くじ","くじ引き" // ja
    )
    // ── Dietary Laws / Clean and Unclean Food ──
    pin(listOf("leviticus-11","deuteronomy-14","acts-10","mark-7","romans-14","1_timothy-4","colossians-2"),
      "dietary laws","clean food","unclean food","kosher","what to eat","leyes alimentarias","alimentos limpios","alimentos impuros","lois alimentaires","aliments purs","aliments impurs","speisegesetze","reine speisen","unreine speisen","leggi alimentari","cibi puri","cibi impuri","leis alimentares","alimentos puros","alimentos impuros",
      "диетарные законы","чистая пища","нечистая пища","кашрут", // ru
      "شريعة الطعام","طعام حلال","طعام نجس", // ar
      "आहार नियम","शुद्ध भोजन","अशुद्ध भोजन", // hi
      "飲食條例","潔淨的食物","不潔淨的食物","饮食条例","洁净的食物","不洁净的食物", // zh
      "음식법","정한 음식","부정한 음식", // ko
      "食物規定","清い食べ物","汚れた食べ物" // ja
    )
    // ── New Creation / All Things New ──
    pin(listOf("2_corinthians-5","galatians-6","revelation-21","isaiah-43","isaiah-65","ephesians-4","colossians-3"),
      "new creation","new creature","all things new","behold i make all things new","nueva creacion","nueva criatura","nouvelle creation","nouvelle creature","neue schopfung","neue kreatur","nuova creazione","nuova creatura","nova criacao","nova criatura",
      "новое творение","новая тварь","се творю все новое", // ru
      "خليقة جديدة","هاأنا أصنع كل شيء جديدا", // ar
      "नई सृष्टि","देखो मैं सब कुछ नया करता हूँ", // hi
      "新造的人","看哪我將一切都更新了","新造的人","看哪我将一切都更新了", // zh
      "새로운 피조물","보라 내가 만물을 새롭게 하노라", // ko
      "新しい被造物","見よわたしはすべてを新しくする" // ja
    )
    // ── Circumcision (covenant sign) ──
    pin(listOf("genesis-17","romans-4","acts-15","galatians-5","colossians-2","joshua-5"),
      "circumcision","circumcise","circuncision","circuncidar","circoncision","circoncire","beschneidung","beschneiden","circoncisione","circoncidere","circuncisao","circuncidar",
      "обрезание","обрезать", // ru
      "ختان", // ar
      "खतना", // hi
      "割禮","割礼", // zh
      "할례", // ko
      "割礼" // ja
    )
    // ── Typology / Types and Shadows ──
    pin(listOf("hebrews-8","hebrews-10","colossians-2","hebrews-9","1-corinthians-10","galatians-4"),
      "type","shadow","types and shadows","typology","prefigure","tipo","sombra","tipos y sombras","type","ombre","types et ombres","typus","schatten","typen und schatten","tipo","ombra","tipi e ombre","tipo","sombra","tipos e sombras",
      "прообраз","тень","прообразы и тени", // ru
      "رمز","ظل","رموز وظلال", // ar
      "प्रतिरूप","छाया","प्रतिरूप और छाया", // hi
      "預表","影兒","预表","影儿", // zh
      "예표","그림자","모형과 그림자", // ko
      "型","影","型と影" // ja
    )
    // ── Spiritual Blindness ──
    pin(listOf("2_corinthians-4","john-9","matthew-15","isaiah-6","romans-11","mark-4","revelation-3"),
      "spiritual blindness","blind","blindness","eyes opened","ceguera espiritual","ciego","aveuglement spirituel","aveugle","geistliche blindheit","blind","cecita spirituale","cieco","cegueira espiritual","cego",
      "духовная слепота","слепой","слепота", // ru
      "عمى روحي","أعمى", // ar
      "आत्मिक अन्धापन","अन्धा", // hi
      "屬靈的瞎眼","瞎子","属灵的瞎眼", // zh
      "영적 눈멂","눈먼", // ko
      "霊的な盲目","盲目" // ja
    )
    // ── Abraham's Faith ──
    pin(listOf("genesis-15","genesis-22","romans-4","hebrews-11","james-2","galatians-3"),
      "abrahams faith","faith of abraham","faith like abraham","fe de abraham","fe como abraham","foi d'abraham","foi comme abraham","glaube abrahams","glaube wie abraham","fede di abramo","fede come abramo","fe de abraao","fe como abraao",
      "вера авраама","вера как у авраама", // ru
      "إيمان إبراهيم","إيمان كإيمان إبراهيم", // ar
      "इब्राहीम का विश्वास","इब्राहीम जैसा विश्वास", // hi
      "亞伯拉罕的信心","像亞伯拉罕一樣的信心","亚伯拉罕的信心","像亚伯拉罕一样的信心", // zh
      "아브라함의 믿음","아브라함 같은 믿음", // ko
      "アブラハムの信仰","アブラハムのような信仰" // ja
    )
    // ── Covenant Faithfulness ──
    pin(listOf("deuteronomy-7","psalms-89","psalms-100","exodus-34","numbers-14","2-samuel-7"),
      "covenant faithfulness","covenant love","faithful god","fidelidad del pacto","amor del pacto","dios fiel","fidelite de l'alliance","amour de l'alliance","dieu fidele","bundestreue","bundesliebe","treuer gott","fedelta dell'alleanza","amore dell'alleanza","dio fedele","fidelidade da alianca","amor da alianca","deus fiel",
      "верность завету","любовь завета","верный бог", // ru
      "أمانة العهد","محبة العهد","إله أمين", // ar
      "वाचा की विश्वासयोग्यता","वाचा का प्रेम","विश्वासयोग्य परमेश्वर", // hi
      "約的信實","約的愛","信實的神","约的信实","约的爱","信实的神", // zh
      "언약의 신실","언약의 사랑","신실하신 하나님", // ko
      "契約の忠実","契約の愛","忠実な神" // ja
    )
    // ── Forgiveness of sins ──
    pin(listOf("psalms-103","colossians-1","ephesians-1","acts-2","mark-2","isaiah-1","1_john-1"),
      "forgiveness of sins","sins forgiven","forgive sins","perdon de pecados","pecados perdonados","pardon des peches","peches pardonnes","vergebung der sunden","sunden vergeben","perdono dei peccati","peccati perdonati","perdao dos pecados","pecados perdoados",
      "прощение грехов","грехи прощены", // ru
      "غفران الخطايا","خطايا مغفورة", // ar
      "पापों की क्षमा","पाप क्षमा", // hi
      "罪得赦免","赦罪", // zh
      "죄 사함","죄를 사하다", // ko
      "罪の赦し","罪が赦された" // ja
    )
    // ── Jesus Wept / Emotions of Jesus ──
    pin(listOf("john-11","luke-19","hebrews-5","matthew-23","mark-10","isaiah-53"),
      "jesus wept","jesus cried","emotions of jesus","jesus lloro","emociones de jesus","jesus pleura","emotions de jesus","jesus weinte","die gefuhle jesu","gesu pianse","emozioni di gesu","jesus chorou","emocoes de jesus",
      "иисус прослезился","иисус заплакал", // ru
      "بكى يسوع","مشاعر يسوع", // ar
      "यीशु रोये","यीशु के भाव", // hi
      "耶穌哭了","耶稣哭了", // zh
      "예수께서 우셨다","예수의 감정", // ko
      "イエスは涙を流された","イエスの感情" // ja
    )
    // ── Prodigal Son (theological concept) ──
    pin(listOf("luke-15","luke-19","matthew-18","hosea-14","jeremiah-3","isaiah-55"),
      "prodigal","prodigal son","lost son","return to god","coming home","prodigo","hijo prodigo","hijo perdido","volver a dios","prodigue","fils prodigue","fils perdu","retour a dieu","verlorener sohn","ruckkehr zu gott","prodigo","figlio prodigo","figlio perduto","ritorno a dio","prodigo","filho prodigo","filho perdido","voltar a deus",
      "блудный сын","возвращение к богу", // ru
      "الابن الضال","العودة إلى الله", // ar
      "उड़ाऊ पुत्र","परमेश्वर की ओर लौटना", // hi
      "浪子","回到神面前","浪子回头", // zh
      "탕자","하나님께 돌아옴", // ko
      "放蕩息子","神に立ち返る" // ja
    )
  }

  private fun resolveCanonicalBoosts(qTokens: List<String>): Set<String> {
    val ids = mutableSetOf<String>()
    for (tok in qTokens) {
      canonicalHits[tok]?.let { ids.addAll(it) }
    }
    return ids
  }

  // --- Flexible story search ---

  private fun searchFlexible(q: String, limit: Int): List<SearchHit> {
    val numParse = parseNumberedBookFromQuery(q)
    val lang = resolvedLang
    val qTokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    val sigTokens = significantTokens(qTokens, lang)
    val stemmedSig = sigTokens.map { stemLite(it, lang) }
    val isPhraseQuery = numParse == null && sigTokens.size >= 2
    val canonicalIds = resolveCanonicalBoosts(sigTokens)

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
      val isCanonical = d.storyId in canonicalIds
      if (score <= 0 && !isCanonical) continue
      score += when (d.collection) {
        "old_testament", "new_testament" -> 150
        "deuterocanonical" -> 50
        else -> 0
      }
      hits += SearchHit("${d.bookTitle}: ${d.title}", makeSnippet(d, qTokens), d.collection, d.bookId, d.storyId, score)
    }

    if (canonicalIds.isNotEmpty() && hits.isNotEmpty()) {
      val topScore = hits.maxOf { it.score }
      val floor = topScore + 500
      for (i in hits.indices) {
        if (hits[i].storyId in canonicalIds) {
          hits[i] = hits[i].copy(score = maxOf(hits[i].score, floor))
        }
      }
    }

    return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title.length }).take(limit)
  }

  // --- Text utilities ---

  private fun normalize(s: String): String = normalizeNFKD(s).replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^\\p{L}\\p{N}\\s:-]"), " ").replace(Regex("\\s+"), " ").trim()

  private fun normalizeBookKey(title: String): Triple<String, String, Int?> { val n = normalize(title); val (num, family) = splitNumberedBook(n); val key = if (num != null) "$num-$family" else family; return Triple(key, family, num) }
  private fun parseNumberedBookFromQuery(qNormalized: String): Pair<Int, String>? { val s = qNormalized.replace("_"," ").replace("-"," ").replace("."," ").trim(); val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }; for (i in tokens.indices) { val n = parseLeadingNumber(tokens[i]); if (n != null) { val tail = tokens.drop(i).joinToString(" "); val (num, fam) = splitNumberedBook(tail); if (num != null && fam.isNotBlank()) return num to fam } }; for (tok in tokens) { val (num, fam) = splitNumberedBook(tok); if (num != null && fam.isNotBlank()) return num to fam }; return null }
  private fun parseLeadingNumber(tok: String): Int? = when (tok.lowercase()) { "1","i","first" -> 1; "2","ii","second" -> 2; "3","iii","third" -> 3; else -> null }
  private fun splitNumberedBook(n: String): Pair<Int?, String> { val s = n.replace("_"," ").replace("-"," ").replace("."," ").trim(); val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList(); if (tokens.isEmpty()) return null to ""; if (tokens.size == 1) { val t = tokens[0].lowercase(); val m = Regex("^(\\d+|i{1,3}|first|second|third)([a-z]+)$").matchEntire(t); if (m != null) { tokens.clear(); tokens += m.groupValues[1]; tokens += m.groupValues[2] } }; val number = parseLeadingNumber(tokens.first().lowercase()); val familyStartIdx = if (number != null) 1 else 0; val family = tokens.drop(familyStartIdx).joinToString(" ").replace(" ","").trim(); return number to family }
  private fun familyMatches(qFamily: String, docFamily: String): Boolean { if (qFamily.isBlank() || docFamily.isBlank()) return false; val q = qFamily.lowercase(); val d = docFamily.lowercase(); if (q.length < 2 || d.length < 2) return false; return d.startsWith(q) || q.startsWith(d) }
  private fun extractFamilyFromQuery(tokens: List<String>): String? { if (tokens.isEmpty()) return null; val maybeNum = parseLeadingNumber(tokens.first()); val famTokens = if (maybeNum != null) tokens.drop(1) else tokens; if (famTokens.isEmpty()) return null; return famTokens.joinToString(" ").replace(" ","") }

  // String scanning, no regex compilation. Callers MUST pass lowercased text/token —
  // both verseDoc text and query tokens are already normalized to lowercase, so the
  // hot path pays no per-call lowercase cost. Cold-path callers (keywordScore,
  // makeSnippet) lowercase mixed-case titles/refs once per doc before calling these.
  private fun indexWordBoundary(h: String, t: String, from: Int = 0): Int {
    if (t.isEmpty() || t.length > h.length - from) return -1
    var i = from
    while (i <= h.length - t.length) {
      val idx = h.indexOf(t, i)
      if (idx < 0) return -1
      val leftOk = idx == 0 || !h[idx - 1].isLetterOrDigit()
      val rightEnd = idx + t.length
      val rightOk = rightEnd == h.length || !h[rightEnd].isLetterOrDigit()
      if (leftOk && rightOk) return idx
      i = idx + 1
    }
    return -1
  }
  private fun indexWordPrefix(h: String, t: String, from: Int = 0): Int {
    if (t.isEmpty() || t.length > h.length - from) return -1
    var i = from
    while (i <= h.length - t.length) {
      val idx = h.indexOf(t, i)
      if (idx < 0) return -1
      val leftOk = idx == 0 || !h[idx - 1].isLetterOrDigit()
      if (leftOk) return idx
      i = idx + 1
    }
    return -1
  }
  private fun indexInfix(h: String, t: String): Int = h.indexOf(t)

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

  // Callers pass lowercased haystack and needle. Walks word boundaries in haystack
  // and tests each word against needle with edit distance ≤ 1.
  private fun editDistLe2(a: String, b: String): Boolean {
    val la = a.length; val lb = b.length
    if (kotlin.math.abs(la - lb) > 2) return false
    val prev = IntArray(lb + 1) { it }
    val curr = IntArray(lb + 1)
    for (i in 1..la) {
      curr[0] = i
      for (j in 1..lb) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
      }
      if (curr.min() > 2) return false
      prev.indices.forEach { prev[it] = curr[it] }
    }
    return curr[lb] <= 2
  }

  private fun fuzzyContains(haystack: String, needle: String): Boolean {
    if (needle.length < 4) return false
    val maxDist = if (needle.length >= 8) 2 else 1
    var wordStart = -1
    var i = 0
    while (i <= haystack.length) {
      val ch = if (i < haystack.length) haystack[i] else ' '
      val isWord = ch.isLetterOrDigit()
      if (isWord && wordStart < 0) wordStart = i
      if (!isWord && wordStart >= 0) {
        val len = i - wordStart
        if (kotlin.math.abs(len - needle.length) <= maxDist) {
          val w = haystack.substring(wordStart, i)
          if (maxDist == 1) { if (editDistLe1(w, needle)) return true }
          else { if (editDistLe2(w, needle)) return true }
        }
        wordStart = -1
      }
      i++
    }
    return false
  }

  private fun keywordScore(d: Doc, qTokens: List<String>, sigTokens: List<String>, lang: String, isPhraseQuery: Boolean = false): Int {
    var score = 0
    val t = d.text
    val xr = d.crossRefText
    val ref = d.refsJoined.lowercase()
    val title = d.title.lowercase()
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
      val bodyHit = scoreTokenFull(t, tok, lang)
      if (bodyHit > 0) {
        score += bodyHit * bodyMul; bodyMatches++
      } else if (xr.isNotEmpty()) {
        val xrHit = scoreTokenFull(xr, tok, lang)
        if (xrHit > 0) score += (xrHit * 2) / 5
      }
      val fam = tok.replace(" ",""); if (familyMatches(fam,d.familyKey)) score+=50
    }
    if (qTokens.isNotEmpty()) {
      val frac = bodyMatches.toFloat() / qTokens.size
      score += (frac * frac * 1500).toInt()
    }
    if (isPhraseQuery && bodyMatches >= 2) {
      score += bodyMatches * bodyMatches * 60
    }
    return score
  }

  // --- Snippets ---

  private fun makeSnippet(d: Doc, qTokens: List<String>, maxLen: Int = 160): String {
    val ref = d.refsJoined; val firstTok = qTokens.firstOrNull() ?: return ellipsize(ref, maxLen)
    val refLow = ref.lowercase(); val titleLow = d.title.lowercase()
    fun tryH(orig: String, low: String, f: (String,String)->Int): String? {
      val idx = f(low, firstTok); if (idx<0) return null
      return highlight(ellipsizeAround(orig, idx, firstTok.length, maxLen), firstTok)
    }
    return tryH(ref, refLow, ::indexWordBoundary) ?: tryH(d.title, titleLow, ::indexWordBoundary) ?: tryH(d.text, d.text, ::indexWordBoundary)
      ?: tryH(ref, refLow, ::indexWordPrefix) ?: tryH(d.title, titleLow, ::indexWordPrefix) ?: tryH(d.text, d.text, ::indexWordPrefix)
      ?: tryH(ref, refLow, ::indexInfix) ?: tryH(d.title, titleLow, ::indexInfix) ?: tryH(d.text, d.text, ::indexInfix)
      ?: if (d.summaryPreview.isNotBlank()) ellipsize("${d.refsJoined} - ${d.summaryPreview}", maxLen) else ellipsize(ref.ifBlank { d.title }, maxLen)
  }

  private fun makeTextSnippet(text: String, qTokens: List<String>, maxLen: Int = 160): String {
    val firstTok = qTokens.firstOrNull() ?: return ellipsize(text, maxLen)
    val low = text.lowercase()
    fun tryH(f: (String, String) -> Int): String? { val idx = f(low, firstTok); if (idx < 0) return null; return highlight(ellipsizeAround(text, idx, firstTok.length, maxLen), firstTok) }
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
