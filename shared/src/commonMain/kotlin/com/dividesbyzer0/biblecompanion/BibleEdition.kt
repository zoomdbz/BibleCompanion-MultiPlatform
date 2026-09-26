package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.Serializable

object BibleEditions {
  const val BSB = "bsb"
  const val KJV_1769 = "kjv1769"

  // One available in-app edition per localized corpus for now. A future
  // edition also needs its assets and loader support before it belongs here.
  private val availableByLanguage = mapOf(
    "en" to listOf(BSB, KJV_1769),
    "de" to listOf("sch2000"),
    "es" to listOf("nvi"),
    "fr" to listOf("nbs"),
    "it" to listOf("nr06"),
    "pt" to listOf("nvt"),
    "ru" to listOf("nrt_nrp"),
    "ja" to listOf("jcb"),
    "ko" to listOf("rnksv"),
    "zh-Hans" to listOf("ccb"),
    "zh-Hant" to listOf("rcuv"),
    "ar" to listOf("sab"),
    "hi" to listOf("irvhin")
  )

  fun available(appLanguage: String): List<String> =
    availableByLanguage[LocaleUtils.effectiveAssetTag(appLanguage)] ?: availableByLanguage.getValue("en")

  fun defaultForLanguage(appLanguage: String): String = available(appLanguage).first()

  fun selectedForLanguage(appLanguage: String, selected: String): String {
    val choices = available(appLanguage)
    if (choices.contains(KJV_1769) && selected.equals("kjv", ignoreCase = true)) return KJV_1769
    return choices.firstOrNull { it.equals(selected, ignoreCase = true) } ?: choices.first()
  }

  fun effective(appLanguage: String, selected: String): String {
    return selectedForLanguage(appLanguage, selected)
  }

  fun isKjv(appLanguage: String, selected: String): Boolean =
    effective(appLanguage, selected) == KJV_1769

}

@Serializable
data class EditionVerse(
  val chapter: Int,
  val verse: Int,
  val text: String
)

@Serializable
data class EditionChapter(
  val number: Int,
  val superscription: String = "",
  val verses: List<EditionVerse> = emptyList()
)

@Serializable
data class EditionBookOverlay(
  val editionId: String,
  val collection: String,
  val bookId: String,
  val sourceBookCode: String,
  val chapters: List<EditionChapter> = emptyList()
)

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
