package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.Serializable

object BibleEditions {
  const val BSB = "bsb"
  const val KJV_1769 = "kjv1769"
  const val LUTHER_1912 = "luther1912"
  const val RV_1909 = "rv1909"
  const val LSG_1910 = "lsg1910"
  const val DIODATI_1885 = "diodati1885"
  const val ALMEIDA_1911 = "almeida1911"
  const val SYNODAL_1876 = "synodal1876"
  const val BUNGO = "bungo"
  const val KOR_RV = "korrv"
  const val CUV = "cuv"
  const val VAN_DYCK = "van_dyck"

  // The first entry is always the existing modern corpus and remains the
  // default. A second entry is exposed only when a complete, source-backed
  // traditional overlay ships with the app.
  private val availableByLanguage = mapOf(
    "en" to listOf(BSB, KJV_1769),
    "de" to listOf("sch2000", LUTHER_1912),
    "es" to listOf("nvi", RV_1909),
    "fr" to listOf("nbs", LSG_1910),
    "it" to listOf("nr06", DIODATI_1885),
    "pt" to listOf("nvt", ALMEIDA_1911),
    "ru" to listOf("nrt_nrp", SYNODAL_1876),
    "ja" to listOf("jcb", BUNGO),
    "ko" to listOf("rnksv", KOR_RV),
    "zh-Hans" to listOf("ccb", CUV),
    "zh-Hant" to listOf("rcuv", CUV),
    "ar" to listOf("sab", VAN_DYCK),
    // No complete, redistributable traditional Hindi source has been
    // identified. Do not mislabel the modern IRV or scrape copyrighted HINOV.
    "hi" to listOf("irvhin")
  )

  fun available(appLanguage: String): List<String> =
    availableByLanguage[LocaleUtils.effectiveAssetTag(appLanguage)] ?: availableByLanguage.getValue("en")

  fun defaultForLanguage(appLanguage: String): String = available(appLanguage).first()

  fun selectedForLanguage(appLanguage: String, selected: String): String {
    return canonicalId(appLanguage, selected) ?: defaultForLanguage(appLanguage)
  }

  internal fun canonicalId(appLanguage: String, selected: String): String? {
    val choices = available(appLanguage)
    if (choices.contains(KJV_1769) && selected.equals("kjv", ignoreCase = true)) return KJV_1769
    return choices.firstOrNull { it.equals(selected, ignoreCase = true) }
  }

  fun effective(appLanguage: String, selected: String): String {
    return selectedForLanguage(appLanguage, selected)
  }

  fun isKjv(appLanguage: String, selected: String): Boolean =
    effective(appLanguage, selected) == KJV_1769

  fun isAlternate(appLanguage: String, selected: String): Boolean =
    effective(appLanguage, selected) != defaultForLanguage(appLanguage)

  // A shared link may select an edition for this reader without changing the
  // user's saved preference. Never apply a different language's edition ID.
  fun forNavigation(appLanguage: String, current: String, sourceLanguage: String?, sourceEdition: String?): String {
    val sameLanguage = sourceLanguage != null &&
      LocaleUtils.effectiveAssetTag(sourceLanguage) == LocaleUtils.effectiveAssetTag(appLanguage)
    if (sameLanguage && sourceEdition != null) {
      // Unknown editions explicitly fall back to the default, matching the
      // reader's unavailable-edition notice. Do not substitute another alternate.
      return selectedForLanguage(appLanguage, sourceEdition)
    }
    return effective(appLanguage, current)
  }

  fun canKeepLinkedVerse(appLanguage: String, sourceLanguage: String?, editionUnavailable: Boolean): Boolean =
    !editionUnavailable && LocaleUtils.effectiveAssetTag(sourceLanguage ?: "en") ==
      LocaleUtils.effectiveAssetTag(appLanguage)

  // A recognized edition may still lack this book's overlay. A fallback book
  // cannot inherit the linked edition's verse coordinates just because its
  // numbers happen to exist in the base text.
  internal fun linkedEditionMatchesLoaded(
    appLanguage: String,
    sourceEdition: String?,
    effectiveEdition: String
  ): Boolean = sourceEdition == null ||
    canonicalId(appLanguage, sourceEdition)?.let { it == effectiveEdition } == true

}

@Serializable
data class EditionVerse(
  val chapter: Int,
  val verse: Int,
  val verseEnd: Int? = null,
  val text: String
)

@Serializable
data class EditionChapter(
  val number: Int,
  val superscription: String = "",
  // Alternate-edition assets must provide this lookup explicitly for every
  // chapter, including an empty list. Null means the overlay is incomplete.
  val headings: List<Heading>? = null,
  // Schema 2 integrity metadata. Nullable keeps the existing English KJV
  // schema-1 overlay readable during migration.
  val lastVerse: Int? = null,
  val verseUnitCount: Int? = null,
  val verses: List<EditionVerse> = emptyList()
)

internal fun editionChapterReferences(baseRefs: List<String>, chapter: EditionChapter): List<String> {
  val last = chapter.verses.lastOrNull()?.let { it.verseEnd ?: it.verse } ?: return baseRefs
  val prefix = baseRefs.firstOrNull()?.let {
    Regex("^(.+?)\\s+\\d+(?::\\d+(?:-\\d+)?|-\\d+)?$").matchEntire(it.trim())?.groupValues?.get(1)
  } ?: return baseRefs
  return listOf("$prefix ${chapter.number}:1" + if (last > 1) "-$last" else "")
}

@Serializable
data class EditionBookOverlay(
  val schemaVersion: Int,
  val editionId: String,
  val language: String,
  val collection: String,
  val bookId: String,
  val sourceBookCode: String,
  val coverage: String,
  val chapters: List<EditionChapter> = emptyList()
)

internal fun EditionBookOverlay.isStructurallyValid(
  expectedEditionId: String,
  expectedLanguage: String,
  expectedCollection: String,
  expectedBookId: String,
  expectedChapterNumbers: Set<Int>? = null
): Boolean {
  if (schemaVersion !in setOf(1, 2)) return false
  if (editionId != expectedEditionId || language != expectedLanguage) return false
  if (collection != expectedCollection || bookId != expectedBookId) return false
  if (sourceBookCode.isBlank() || coverage !in setOf("full", "mapped")) return false
  if (chapters.isEmpty()) return false

  val chapterNumbers = chapters.map { it.number }
  if (chapterNumbers != (1..chapters.size).toList()) return false
  if (chapterNumbers.distinct().size != chapterNumbers.size) return false
  if (expectedChapterNumbers != null && chapterNumbers.toSet() != expectedChapterNumbers) return false

  return chapters.all { chapter ->
    if (chapter.verses.isEmpty()) return@all false
    var expectedVerse = 1
    val validVerses = chapter.verses.all { verse ->
      val verseEnd = verse.verseEnd ?: verse.verse
      val valid = verse.chapter == chapter.number &&
        verse.verse == expectedVerse &&
        verseEnd >= verse.verse &&
        verse.text.isNotBlank()
      expectedVerse = verseEnd + 1
      valid
    }
    if (!validVerses) return@all false
    val computedLastVerse = expectedVerse - 1
    if (schemaVersion >= 2 && (
        chapter.lastVerse != computedLastVerse ||
        chapter.verseUnitCount != chapter.verses.size
      )) return@all false
    val headings = chapter.headings ?: return@all false
    val anchors = headings.map { it.beforeVerse }
    if (anchors != anchors.sorted() || anchors.distinct().size != anchors.size) return@all false
    if (headings.any { heading ->
        heading.text.isBlank() || chapter.verses.none { it.verse == heading.beforeVerse }
      }) return@all false
    true
  }
}

enum class EditionCoverage {
  BASE,
  FULL,
  FALLBACK
}

data class LoadedBook(
  val book: Book,
  val requestedEdition: String,
  val effectiveEdition: String,
  val coverage: EditionCoverage
)
