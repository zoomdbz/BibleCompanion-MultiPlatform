package com.dividesbyzer0.biblecompanion

import com.dividesbyzer0.biblecompanion.platform.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.math.min
import kotlin.math.sqrt

// Small bounded LRU cache for recent semantic-query results. Users edit
// queries back and forth while searching; without a cache, every revisited
// query re-encodes through ONNX. Cleared on language change since results
// are language-specific.
// Uses kotlinx.coroutines.sync.Mutex so the implementation compiles on every
// KMP target (kotlin.synchronized is JVM-only). Accessors are suspend because
// the caller already lives inside a search coroutine. Key is a data class so
// there's no string-concat separator collision (e.g. lang="ena" + query="test"
// vs lang="en" + query="atest") and no embedded control characters.
object SemanticCache {
    private const val MAX_ENTRIES = 32

    private data class CacheKey(val lang: String, val query: String)

    private val store = LinkedHashMap<CacheKey, List<EmbeddingSearch.SemanticHit>>(
        MAX_ENTRIES, 0.75f
    )
    private val mutex = Mutex()

    private fun keyOf(lang: String, query: String): CacheKey =
        CacheKey(lang, query.trim().lowercase())

    suspend fun get(lang: String, query: String): List<EmbeddingSearch.SemanticHit>? {
        if (query.isBlank()) return null
        val k = keyOf(lang, query)
        return mutex.withLock {
            val v = store.remove(k) ?: return@withLock null
            store[k] = v // re-insert to move to MRU position
            v
        }
    }

    suspend fun put(lang: String, query: String, hits: List<EmbeddingSearch.SemanticHit>) {
        if (query.isBlank()) return
        val k = keyOf(lang, query)
        mutex.withLock {
            store.remove(k)
            store[k] = hits
            while (store.size > MAX_ENTRIES) {
                val oldest = store.keys.iterator().next()
                store.remove(oldest)
            }
        }
    }

    suspend fun clear() {
        mutex.withLock { store.clear() }
    }
}

object EmbeddingSearch {

    private const val DIM = 384
    private const val BOS_ID = 0
    private const val EOS_ID = 2
    private const val UNK_ID = 3
    private const val MAX_TOKEN_LEN = 16
    private const val MAX_SEQ_LEN = 128
    private const val METASPACE = '▁'

    data class SemanticHit(
        val storyId: String,
        val score: Float,
        val collection: String,
        val bookId: String
    )

    private class EntryMeta(
        val collection: String,
        val bookId: String,
        val storyId: String
    )

    // The corpus-generation script previously emitted the JSON's internal `id`
    // for `book_id`, but runtime navigation loads `books/<collection>/<lang>/<stem>.json`.
    // These 8 stems differ from the internal id, so existing baked embeddings carry
    // unloadable bookIds. Normalize on load so semantic-only hits route correctly.
    private val bookIdAliases: Map<String, String> = mapOf(
        "1-corinthians" to "1_corinthians",
        "1-kings"       to "1_kings",
        "1-samuel"      to "1_samuel",
        "1chronicles"   to "1_chronicles",
        "2-kings"       to "2_kings",
        "2-samuel"      to "2_samuel",
        "2chronicles"   to "2_chronicles",
        "song-of-songs" to "song_of_songs"
    )

    private fun normalizeBookId(raw: String): String = bookIdAliases[raw] ?: raw

    // Embedding index state
    private var embData: ByteArray? = null
    private var embDataOffset = 0
    private var embCount = 0
    private var embScale = 0f
    private var embOffset = 0f
    private var meta: List<EntryMeta>? = null

    // Tokenizer state
    private var vocabScores: FloatArray? = null
    private var vocabLookup: HashMap<String, Int>? = null
    private var vocabLoaded = false

    private var builtForLang: String? = null
    private val buildMutex = Mutex()

    suspend fun ensureBuilt(context: PlatformContext, lang: String) {
        if (builtForLang == lang && embData != null) return
        buildMutex.withLock {
            if (builtForLang == lang && embData != null) return@withLock
            // Clear prior state so a failed load can't leave stale embeddings from
            // a previous language live while builtForLang reports the new one.
            // Also clear the semantic query cache since cached results are tied
            // to the previous language's index.
            embData = null
            embCount = 0
            embScale = 0f
            embOffset = 0f
            meta = null
            builtForLang = null
            SemanticCache.clear()

            loadEmbeddings(context, lang)
            loadMetadata(context, lang)
            if (!vocabLoaded) loadVocab(context)

            val data = embData
            val entries = meta
            val ok = data != null && embCount > 0 && entries != null && entries.size == embCount
            if (ok) {
                builtForLang = lang
            } else {
                embData = null
                embCount = 0
                meta = null
            }
        }
    }

    fun isReady(lang: String): Boolean = builtForLang == lang && embData != null

    fun encodeAndSearch(
        query: String, lang: String, limit: Int = 15
    ): List<SemanticHit>? {
        if (!isReady(lang)) return null
        if (!platformOnnxIsReady()) return null

        val prefixed = "query: $query"
        val tokenIds = tokenize(prefixed)
        val inputIds = LongArray(tokenIds.size) { tokenIds[it].toLong() }
        val attMask = LongArray(tokenIds.size) { 1L }

        val hidden = platformOnnxInference(inputIds, attMask) ?: return null
        val seqLen = tokenIds.size
        if (hidden.size < seqLen * DIM) return null

        val pooled = meanPool(hidden, seqLen, attMask)
        l2Normalize(pooled)
        return search(pooled, limit)
    }

    fun search(queryEmbedding: FloatArray, limit: Int = 15): List<SemanticHit> {
        val data = embData ?: return emptyList()
        val entries = meta ?: return emptyList()
        if (queryEmbedding.size != DIM) return emptyList()

        var querySum = 0f
        for (v in queryEmbedding) querySum += v
        val offsetTerm = embOffset * querySum

        // Track best entry index and score per story without allocating a Pair
        // on every update. Two parallel maps keyed by storyId — slightly more
        // memory but no per-update allocation, which matters when the query
        // fires on a typing-hot path.
        val bestScore = HashMap<String, Float>()
        val bestIdx = HashMap<String, Int>()
        for (i in 0 until embCount) {
            val base = embDataOffset + i * DIM
            var intDot = 0f
            for (d in 0 until DIM) {
                intDot += queryEmbedding[d] * data[base + d].toInt()
            }
            val score = embScale * intDot + offsetTerm
            val entry = entries.getOrNull(i) ?: continue
            val existing = bestScore[entry.storyId]
            if (existing == null || score > existing) {
                bestScore[entry.storyId] = score
                bestIdx[entry.storyId] = i
            }
        }

        // Bounded top-N selection instead of full sort. With ~1500 unique
        // stories and limit=15, linear-insert keeps the work O(N*limit)
        // instead of O(N log N) and avoids allocating a sorted intermediate
        // list of every story score.
        if (bestScore.isEmpty()) return emptyList()
        val topScores = FloatArray(limit)
        val topStoryIds = arrayOfNulls<String>(limit)
        var topCount = 0
        for ((storyId, score) in bestScore) {
            if (topCount < limit) {
                // Insert into sorted position.
                var pos = topCount
                while (pos > 0 && topScores[pos - 1] < score) {
                    topScores[pos] = topScores[pos - 1]
                    topStoryIds[pos] = topStoryIds[pos - 1]
                    pos--
                }
                topScores[pos] = score
                topStoryIds[pos] = storyId
                topCount++
            } else if (score > topScores[limit - 1]) {
                // Displace the worst and slide into sorted position.
                var pos = limit - 1
                while (pos > 0 && topScores[pos - 1] < score) {
                    topScores[pos] = topScores[pos - 1]
                    topStoryIds[pos] = topStoryIds[pos - 1]
                    pos--
                }
                topScores[pos] = score
                topStoryIds[pos] = storyId
            }
        }

        val out = ArrayList<SemanticHit>(topCount)
        for (k in 0 until topCount) {
            val sid = topStoryIds[k] ?: continue
            val idx = bestIdx[sid] ?: continue
            val entry = entries[idx]
            out.add(SemanticHit(entry.storyId, topScores[k], entry.collection, entry.bookId))
        }
        return out
    }

    fun merge(
        keywordHits: List<SearchHit>,
        semanticHits: List<SemanticHit>?,
        limit: Int = 30
    ): List<SearchHit> {
        if (semanticHits.isNullOrEmpty()) return keywordHits

        val topSemScore = semanticHits.first().score
        val minScore = maxOf(0.87f, topSemScore * 0.95f)
        val confident = semanticHits.filter { it.score >= minScore }
        val confidentIds = confident.map { it.storyId }.toSet()
        val kwStoryIds = keywordHits
            .filter { it.type == SearchHitType.STORY }
            .map { it.storyId }.toSet()
        val kwTopScore = keywordHits.maxOfOrNull { it.score } ?: 0

        val semScoreMap = HashMap<String, Float>()
        for (sem in confident) semScoreMap[sem.storyId] = sem.score

        fun syntheticScore(semScore: Float, rank: Int): Int {
            if (topSemScore <= 0f) return 300
            val relative = semScore / topSemScore
            val boost = if (topSemScore >= 0.87f) 1.2f
                        else if (topSemScore >= 0.85f) 1.0f
                        else 0.85f
            return ((kwTopScore.coerceAtLeast(3000)) * relative * boost).toInt() - rank * 50
        }

        val boosted = keywordHits.map { hit ->
            if (hit.type == SearchHitType.STORY && hit.storyId in confidentIds) {
                val kwBoosted = hit.score + (kwTopScore / 2).coerceAtLeast(1000)
                val semSynthetic = syntheticScore(semScoreMap[hit.storyId] ?: 0f, 0)
                hit.copy(score = maxOf(kwBoosted, semSynthetic), semantic = true)
            } else hit
        }

        val newHits = confident
            .filter { it.storyId !in kwStoryIds }
            .take(4)
            .mapIndexed { rank, sem ->
                val title = StorySearch.storyTitle(sem.storyId)
                    ?: sem.storyId.replace('-', ' ').replaceFirstChar { it.uppercaseChar() }
                val refs = StorySearch.storySnippet(sem.storyId) ?: ""
                val preview = StorySearch.storySummaryPreview(sem.storyId)
                val snippet = if (preview != null) "$refs - $preview" else refs
                val semScore = syntheticScore(sem.score, rank)
                SearchHit(title, snippet, sem.collection, sem.bookId,
                    sem.storyId, semScore, SearchHitType.STORY, semantic = true)
            }

        return (boosted + newHits)
            .sortedWith(
                compareByDescending<SearchHit> { it.score }
                    .thenBy {
                        when (it.type) {
                            SearchHitType.STORY -> 0
                            SearchHitType.BOOK -> 1
                            SearchHitType.NOTE -> 2
                        }
                    }
            )
            .take(limit)
    }

    // --- Tokenization (SentencePiece Unigram) ---

    fun tokenize(text: String): IntArray {
        val lookup = vocabLookup ?: return intArrayOf(BOS_ID, EOS_ID)
        val scores = vocabScores ?: return intArrayOf(BOS_ID, EOS_ID)

        val normalized = normalizeNFKC(text)
        val processed = buildString(normalized.length + 1) {
            append(METASPACE)
            for (ch in normalized) append(if (ch == ' ') METASPACE else ch)
        }

        val pieces = splitOnMetaspace(processed)
        val tokenIds = mutableListOf(BOS_ID)
        for (piece in pieces) {
            unigramViterbi(piece, lookup, scores, tokenIds)
        }
        tokenIds.add(EOS_ID)

        if (tokenIds.size > MAX_SEQ_LEN) {
            val truncated = tokenIds.subList(0, MAX_SEQ_LEN - 1).toMutableList()
            truncated.add(EOS_ID)
            return truncated.toIntArray()
        }
        return tokenIds.toIntArray()
    }

    private fun splitOnMetaspace(text: String): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        for (i in 1 until text.length) {
            if (text[i] == METASPACE) {
                if (start < i) pieces.add(text.substring(start, i))
                start = i
            }
        }
        if (start < text.length) pieces.add(text.substring(start))
        return pieces
    }

    private fun unigramViterbi(
        text: String, lookup: HashMap<String, Int>,
        scores: FloatArray, out: MutableList<Int>
    ) {
        val n = text.length
        if (n == 0) return

        val bestScore = FloatArray(n + 1) { if (it == 0) 0f else Float.NEGATIVE_INFINITY }
        val bestId = IntArray(n + 1) { -1 }
        val bestLen = IntArray(n + 1)

        for (i in 0 until n) {
            if (bestScore[i] == Float.NEGATIVE_INFINITY) continue
            var found = false
            for (len in 1..min(MAX_TOKEN_LEN, n - i)) {
                val sub = text.substring(i, i + len)
                val id = lookup[sub] ?: continue
                found = true
                val s = bestScore[i] + scores[id]
                if (s > bestScore[i + len]) {
                    bestScore[i + len] = s
                    bestId[i + len] = id
                    bestLen[i + len] = len
                }
            }
            if (!found) {
                val s = bestScore[i] - 100f
                if (s > bestScore[i + 1]) {
                    bestScore[i + 1] = s
                    bestId[i + 1] = UNK_ID
                    bestLen[i + 1] = 1
                }
            }
        }

        val ids = mutableListOf<Int>()
        var pos = n
        while (pos > 0 && bestLen[pos] > 0) {
            ids.add(bestId[pos])
            pos -= bestLen[pos]
        }
        ids.reverse()
        out.addAll(ids)
    }

    // --- Loading ---

    private fun loadEmbeddings(context: PlatformContext, lang: String) {
        val bytes = readAssetBytes(context, "embedding/embeddings_$lang.bin") ?: return
        if (bytes.size < 24) return
        if (bytes[0] != 'B'.code.toByte() || bytes[1] != 'C'.code.toByte()) return
        embCount = readI32(bytes, 8)
        val dim = readI32(bytes, 12)
        if (dim != DIM) return
        embScale = readF32(bytes, 16)
        embOffset = readF32(bytes, 20)
        embData = bytes
        embDataOffset = 24
        val expected = 24 + embCount * dim
        if (bytes.size < expected) { embData = null; embCount = 0 }
    }

    private fun loadMetadata(context: PlatformContext, lang: String) {
        val text = readAssetText(context, "embedding/metadata_$lang.json") ?: return
        val arr = Json.parseToJsonElement(text).jsonArray
        meta = arr.map { el ->
            val obj = el.jsonObject
            val rawBook = obj["b"]?.jsonPrimitive?.content ?: ""
            EntryMeta(
                collection = obj["c"]?.jsonPrimitive?.content ?: "",
                bookId = normalizeBookId(rawBook),
                storyId = obj["s"]?.jsonPrimitive?.content ?: ""
            )
        }
    }

    private fun loadVocab(context: PlatformContext) {
        val bytes = readAssetBytes(context, "embedding/vocab.bin") ?: return
        if (bytes.size < 12) return
        if (bytes[0] != 'S'.code.toByte() || bytes[1] != 'P'.code.toByte()) return
        val count = readI32(bytes, 8)
        if (count !in 1..500_000) return
        val specialIds = setOf(0, 1, 2, 3, count - 1)
        val lookup = HashMap<String, Int>(count * 2)
        val scores = FloatArray(count)
        var pos = 12
        for (i in 0 until count) {
            if (pos + 6 > bytes.size) break
            scores[i] = readF32(bytes, pos); pos += 4
            val len = readU16(bytes, pos); pos += 2
            if (len > 64 || pos + len > bytes.size) break
            val token = bytes.decodeToString(pos, pos + len)
            pos += len
            if (i !in specialIds) lookup[token] = i
        }
        vocabScores = scores
        vocabLookup = lookup
        vocabLoaded = true
    }

    // --- Pooling and normalization ---

    private fun meanPool(hidden: FloatArray, seqLen: Int, mask: LongArray): FloatArray {
        val pooled = FloatArray(DIM)
        var maskSum = 0f
        for (i in 0 until seqLen) {
            val m = mask[i].toFloat()
            maskSum += m
            val off = i * DIM
            for (d in 0 until DIM) pooled[d] += hidden[off + d] * m
        }
        if (maskSum > 0f) for (d in 0 until DIM) pooled[d] /= maskSum
        return pooled
    }

    private fun l2Normalize(v: FloatArray) {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm).coerceAtLeast(1e-9f)
        for (i in v.indices) v[i] /= norm
    }

    // --- Binary reading helpers (little-endian) ---

    private fun readI32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun readU16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun readF32(b: ByteArray, off: Int): Float = Float.fromBits(readI32(b, off))
}
