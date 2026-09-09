package com.dividesbyzer0.biblecompanion

import kotlinx.serialization.Serializable

object BibleEditions {
  const val BSB = "bsb"
  const val KJV_1769 = "kjv1769"

  fun effective(appLanguage: String, selected: String): String {
    val language = LocaleUtils.effectiveAssetTag(appLanguage)
    if (language != "en") return BSB
    return if (selected.equals(KJV_1769, ignoreCase = true) ||
      selected.equals("kjv", ignoreCase = true)
    ) KJV_1769 else BSB
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
