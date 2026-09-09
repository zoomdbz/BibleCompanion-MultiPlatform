package com.dividesbyzer0.biblecompanion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dividesbyzer0.biblecompanion.platform.LocalPlatformContext
import org.jetbrains.compose.resources.stringResource

/**
 * A broad historical placement, not a claim about a book's composition date.
 * Entries may repeat when a book spans more than one part of the narrative.
 */
data class ChronologyEntry(
  val collection: String,
  val bookId: String,
  val chapterRange: String,
  val openingChapter: Int,
  val lane: ChronologyLane,
  val basis: ChronologyBasis? = null,
  val gospelStage: GospelStage? = null,
  val psalmAttribution: PsalmAttribution? = null
)

enum class ChronologyLane {
  HISTORICAL_FLOW,
  PARALLEL_ACCOUNTS,
  VOICES_FROM_PERIOD,
  DEUTEROCANON
}

enum class ChronologyBasis {
  TRADITIONAL_SETTING,
  APPROXIMATE_PLACEMENT,
  PLACEMENT_DEBATED,
  PARALLEL_ACCOUNT,
  SPANS_MULTIPLE_PERIODS
}

enum class GospelStage {
  BIRTH_EARLY_YEARS,
  PREPARATION_EARLY_MINISTRY,
  GALILEAN_MINISTRY,
  WITHDRAWAL_TRAINING,
  JOURNEY_JERUSALEM,
  FINAL_WEEK_CRUCIFIXION,
  RESURRECTION
}

/**
 * Disjoint groupings based on the names in the Hebrew Psalm superscriptions.
 * Psalm 88 names both Heman and the Sons of Korah, so it has its own group.
 */
enum class PsalmAttribution {
  DAVID,
  ASAPH,
  SONS_OF_KORAH,
  SOLOMON,
  MOSES,
  HEMAN_AND_SONS_OF_KORAH,
  ETHAN,
  UNNAMED
}

enum class ChronologyEpochId {
  CREATION_EARLY_HISTORY,
  PATRIARCHS,
  EXODUS_WILDERNESS,
  CONQUEST_JUDGES,
  UNITED_KINGDOM,
  DIVIDED_KINGDOM,
  JUDAH_FINAL_YEARS,
  BABYLONIAN_EXILE,
  RETURN_RESTORATION,
  PSALMS_COLLECTION,
  SECOND_TEMPLE,
  LIFE_OF_JESUS,
  EARLY_CHURCH
}

internal fun decodeChronologyExpandedEpochs(value: String): Set<ChronologyEpochId> =
  value.split(',')
    .map { it.trim() }
    .mapNotNull { stored -> ChronologyEpochId.entries.firstOrNull { it.name == stored } }
    .toSet()

internal fun encodeChronologyExpandedEpochs(epochs: Set<ChronologyEpochId>): String =
  ChronologyEpochId.entries
    .filter { it in epochs }
    .joinToString(",") { it.name }

internal fun parseChronologyChapterRange(value: String): List<Int> = buildList {
  value.split(',').forEach { rawPart ->
    val part = rawPart.trim()
    val bounds = part.split('-', limit = 2)
    when (bounds.size) {
      1 -> bounds[0].toIntOrNull()?.let { add(it) }
      2 -> {
        val start = bounds[0].toIntOrNull()
        val end = bounds[1].toIntOrNull()
        if (start != null && end != null && start <= end) addAll(start..end)
      }
    }
  }
}

data class ChronologyEpoch(
  val id: ChronologyEpochId,
  val entries: List<ChronologyEntry>
)

private fun ot(
  bookId: String,
  range: String,
  openingChapter: Int = 1,
  lane: ChronologyLane = ChronologyLane.HISTORICAL_FLOW,
  basis: ChronologyBasis? = null,
  psalmAttribution: PsalmAttribution? = null
) = ChronologyEntry(
  "old_testament",
  bookId,
  range,
  openingChapter,
  lane,
  basis,
  psalmAttribution = psalmAttribution
)

private fun nt(
  bookId: String,
  range: String,
  openingChapter: Int = 1,
  lane: ChronologyLane = ChronologyLane.HISTORICAL_FLOW,
  basis: ChronologyBasis? = null,
  gospelStage: GospelStage? = null
) = ChronologyEntry(
  "new_testament",
  bookId,
  range,
  openingChapter,
  lane,
  basis,
  gospelStage
)

private fun dc(
  bookId: String,
  range: String,
  openingChapter: Int = 1,
  basis: ChronologyBasis? = null
) = ChronologyEntry(
  "deuterocanonical",
  bookId,
  range,
  openingChapter,
  ChronologyLane.DEUTEROCANON,
  basis
)

object BibleChronologyData {
  val epochs: List<ChronologyEpoch> = listOf(
    ChronologyEpoch(
      ChronologyEpochId.CREATION_EARLY_HISTORY,
      listOf(ot("genesis", "1-11"))
    ),
    ChronologyEpoch(
      ChronologyEpochId.PATRIARCHS,
      listOf(
        ot("genesis", "12-50", 12),
        ot("job", "1-42", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.TRADITIONAL_SETTING)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.EXODUS_WILDERNESS,
      listOf(
        ot("exodus", "1-40"),
        ot("leviticus", "1-27"),
        ot("numbers", "1-36"),
        ot("deuteronomy", "1-34"),
        ot("psalms", "90", 90, ChronologyLane.VOICES_FROM_PERIOD, ChronologyBasis.TRADITIONAL_SETTING)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.CONQUEST_JUDGES,
      listOf(
        ot("joshua", "1-24"),
        ot("judges", "1-21"),
        ot("ruth", "1-4", lane = ChronologyLane.VOICES_FROM_PERIOD)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.UNITED_KINGDOM,
      listOf(
        ot("1_samuel", "1-31"),
        ot("2_samuel", "1-24"),
        ot("1_kings", "1-11"),
        ot("1_chronicles", "1-29", lane = ChronologyLane.PARALLEL_ACCOUNTS),
        ot("2_chronicles", "1-9", lane = ChronologyLane.PARALLEL_ACCOUNTS),
        ot(
          "psalms", "3, 7, 18, 34, 51-52, 54, 56-57, 59-60, 63, 142", 3,
          ChronologyLane.VOICES_FROM_PERIOD, ChronologyBasis.TRADITIONAL_SETTING
        ),
        ot("psalms", "72, 127", 72, ChronologyLane.VOICES_FROM_PERIOD, ChronologyBasis.TRADITIONAL_SETTING),
        ot("proverbs", "1-31", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("ecclesiastes", "1-12", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("song_of_songs", "1-8", lane = ChronologyLane.VOICES_FROM_PERIOD),
        dc("wisdom", "1-19", basis = ChronologyBasis.TRADITIONAL_SETTING),
        dc("psalm_151", "1", basis = ChronologyBasis.TRADITIONAL_SETTING)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.DIVIDED_KINGDOM,
      listOf(
        ot("1_kings", "12-22", 12),
        ot("2_kings", "1-20"),
        ot("2_chronicles", "10-32", 10, ChronologyLane.PARALLEL_ACCOUNTS),
        ot("jonah", "1-4", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("amos", "1-9", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("hosea", "1-14", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("isaiah", "1-39", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("micah", "1-7", lane = ChronologyLane.VOICES_FROM_PERIOD),
        dc("tobit", "1-14", basis = ChronologyBasis.APPROXIMATE_PLACEMENT)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.JUDAH_FINAL_YEARS,
      listOf(
        ot("2_kings", "21-25", 21),
        ot("2_chronicles", "33-36", 33, ChronologyLane.PARALLEL_ACCOUNTS),
        ot("nahum", "1-3", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("zephaniah", "1-3", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("jeremiah", "1-39", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("habakkuk", "1-3", lane = ChronologyLane.VOICES_FROM_PERIOD),
        dc("prayer_of_manasseh", "1")
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.BABYLONIAN_EXILE,
      listOf(
        ot("jeremiah", "40-52", 40),
        ot("ezekiel", "1-48"),
        ot("daniel", "1-12"),
        ot("isaiah", "40-55", 40, ChronologyLane.VOICES_FROM_PERIOD),
        ot("lamentations", "1-5", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("obadiah", "1", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.PLACEMENT_DEBATED),
        dc("baruch", "1-5"),
        dc("letter_of_jeremiah", "1"),
        dc("song_of_three", "1", basis = ChronologyBasis.PARALLEL_ACCOUNT),
        dc("susanna", "1", basis = ChronologyBasis.PARALLEL_ACCOUNT),
        dc("bel_and_the_dragon", "1", basis = ChronologyBasis.PARALLEL_ACCOUNT),
        dc("judith", "1-16", basis = ChronologyBasis.PLACEMENT_DEBATED)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.RETURN_RESTORATION,
      listOf(
        ot("ezra", "1-10"),
        ot("esther", "1-10"),
        ot("nehemiah", "1-13"),
        ot("haggai", "1-2", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("zechariah", "1-14", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("malachi", "1-4", lane = ChronologyLane.VOICES_FROM_PERIOD),
        ot("isaiah", "56-66", 56, ChronologyLane.VOICES_FROM_PERIOD),
        ot("joel", "1-3", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.PLACEMENT_DEBATED),
        ot("psalms", "126, 137", 126, ChronologyLane.VOICES_FROM_PERIOD, ChronologyBasis.APPROXIMATE_PLACEMENT),
        dc("1_esdras", "1-9", basis = ChronologyBasis.PARALLEL_ACCOUNT),
        dc("2_esdras", "1-16", basis = ChronologyBasis.APPROXIMATE_PLACEMENT),
        dc("esther_greek", "1-21", basis = ChronologyBasis.PARALLEL_ACCOUNT)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.PSALMS_COLLECTION,
      listOf(
        ot(
          "psalms",
          "3-9, 11-32, 34-41, 51-65, 68-70, 86, 101, 103, 108-110, 122, 124, 131, 133, 138-145",
          3,
          ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.DAVID
        ),
        ot(
          "psalms", "50, 73-83", 50, ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.ASAPH
        ),
        ot(
          "psalms", "42, 44-49, 84-85, 87", 42, ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.SONS_OF_KORAH
        ),
        ot(
          "psalms", "72, 127", 72, ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.SOLOMON
        ),
        ot(
          "psalms", "90", 90, ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.MOSES
        ),
        ot(
          "psalms", "88", 88, ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.HEMAN_AND_SONS_OF_KORAH
        ),
        ot(
          "psalms", "89", 89, ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.ETHAN
        ),
        ot(
          "psalms",
          "1-2, 10, 33, 43, 66-67, 71, 91-100, 102, 104-107, 111-121, 123, 125-126, 128-130, 132, 134-137, 146-150",
          1,
          ChronologyLane.VOICES_FROM_PERIOD,
          psalmAttribution = PsalmAttribution.UNNAMED
        )
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.SECOND_TEMPLE,
      listOf(
        dc("3_maccabees", "1-7"),
        dc("sirach", "1-51"),
        dc("2_maccabees", "1-15"),
        dc("1_maccabees", "1-16"),
        dc("4_maccabees", "1-18")
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.LIFE_OF_JESUS,
      listOf(
        nt("matthew", "1-2", gospelStage = GospelStage.BIRTH_EARLY_YEARS),
        nt("luke", "1-2", lane = ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.BIRTH_EARLY_YEARS),
        nt("matthew", "3-4", 3, gospelStage = GospelStage.PREPARATION_EARLY_MINISTRY),
        nt("mark", "1", lane = ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.PREPARATION_EARLY_MINISTRY),
        nt("luke", "3-4", 3, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.PREPARATION_EARLY_MINISTRY),
        nt("john", "1-4", lane = ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.PREPARATION_EARLY_MINISTRY),
        nt("matthew", "5-13", 5, gospelStage = GospelStage.GALILEAN_MINISTRY),
        nt("mark", "2-6", 2, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.GALILEAN_MINISTRY),
        nt("luke", "5-9", 5, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.GALILEAN_MINISTRY),
        nt("john", "5-6", 5, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.GALILEAN_MINISTRY),
        nt("matthew", "14-18", 14, gospelStage = GospelStage.WITHDRAWAL_TRAINING),
        nt("mark", "7-9", 7, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.WITHDRAWAL_TRAINING),
        nt("luke", "9", 9, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.WITHDRAWAL_TRAINING),
        nt("matthew", "19-20", 19, gospelStage = GospelStage.JOURNEY_JERUSALEM),
        nt("mark", "10", 10, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.JOURNEY_JERUSALEM),
        nt("luke", "10-19", 10, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.JOURNEY_JERUSALEM),
        nt("john", "7-11", 7, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.JOURNEY_JERUSALEM),
        nt("matthew", "21-27", 21, gospelStage = GospelStage.FINAL_WEEK_CRUCIFIXION),
        nt("mark", "11-15", 11, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.FINAL_WEEK_CRUCIFIXION),
        nt("luke", "19-23", 19, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.FINAL_WEEK_CRUCIFIXION),
        nt("john", "12-19", 12, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.FINAL_WEEK_CRUCIFIXION),
        nt("matthew", "28", 28, gospelStage = GospelStage.RESURRECTION),
        nt("mark", "16", 16, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.RESURRECTION),
        nt("luke", "24", 24, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.RESURRECTION),
        nt("john", "20-21", 20, ChronologyLane.PARALLEL_ACCOUNTS, gospelStage = GospelStage.RESURRECTION)
      )
    ),
    ChronologyEpoch(
      ChronologyEpochId.EARLY_CHURCH,
      listOf(
        nt("acts", "1-12"),
        nt("james", "1-5", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.APPROXIMATE_PLACEMENT),
        nt("acts", "13-15", 13),
        nt("galatians", "1-6", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.PLACEMENT_DEBATED),
        nt("acts", "16-18", 16),
        nt("1_thessalonians", "1-5", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("2_thessalonians", "1-3", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("acts", "19-20", 19),
        nt("1_corinthians", "1-16", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("2_corinthians", "1-13", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("romans", "1-16", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("acts", "21-28", 21),
        nt("colossians", "1-4", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("philemon", "1", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("ephesians", "1-6", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("philippians", "1-4", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("1_timothy", "1-6", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.APPROXIMATE_PLACEMENT),
        nt("titus", "1-3", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.APPROXIMATE_PLACEMENT),
        nt("1_peter", "1-5", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("hebrews", "1-13", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.APPROXIMATE_PLACEMENT),
        nt("2_timothy", "1-4", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("2_peter", "1-3", lane = ChronologyLane.VOICES_FROM_PERIOD, basis = ChronologyBasis.APPROXIMATE_PLACEMENT),
        nt("jude", "1", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("1_john", "1-5", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("2_john", "1", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("3_john", "1", lane = ChronologyLane.VOICES_FROM_PERIOD),
        nt("revelation", "1-22", lane = ChronologyLane.VOICES_FROM_PERIOD)
      )
    )
  )

  private val canonicalIds = setOf(
    "genesis", "exodus", "leviticus", "numbers", "deuteronomy", "joshua", "judges", "ruth",
    "1_samuel", "2_samuel", "1_kings", "2_kings", "1_chronicles", "2_chronicles", "ezra",
    "nehemiah", "esther", "job", "psalms", "proverbs", "ecclesiastes", "song_of_songs",
    "isaiah", "jeremiah", "lamentations", "ezekiel", "daniel", "hosea", "joel", "amos",
    "obadiah", "jonah", "micah", "nahum", "habakkuk", "zephaniah", "haggai", "zechariah",
    "malachi", "matthew", "mark", "luke", "john", "acts", "romans", "1_corinthians",
    "2_corinthians", "galatians", "ephesians", "philippians", "colossians", "1_thessalonians",
    "2_thessalonians", "1_timothy", "2_timothy", "titus", "philemon", "hebrews", "james",
    "1_peter", "2_peter", "1_john", "2_john", "3_john", "jude", "revelation"
  )

  private val deuterocanonIds = setOf(
    "tobit", "judith", "wisdom", "sirach", "baruch", "letter_of_jeremiah",
    "prayer_of_manasseh", "1_maccabees", "2_maccabees", "3_maccabees", "4_maccabees",
    "1_esdras", "2_esdras", "esther_greek", "song_of_three", "susanna",
    "bel_and_the_dragon", "psalm_151"
  )

  /** Exposed for deterministic unit/static checks without coupling the UI to test code. */
  fun validationErrors(): List<String> {
    val entries = epochs.flatMap { it.entries }
    val canonicalActual = entries.filter { it.collection != "deuterocanonical" }.map { it.bookId }.toSet()
    val dcActual = entries.filter { it.collection == "deuterocanonical" }.map { it.bookId }.toSet()
    return buildList {
      val expectedEpochIds = ChronologyEpochId.entries
      val actualEpochIds = epochs.map { it.id }
      if (actualEpochIds != expectedEpochIds) {
        add("Chronology epoch order mismatch: expected $expectedEpochIds; found $actualEpochIds")
      }
      val missingCanonical = canonicalIds - canonicalActual
      if (missingCanonical.isNotEmpty()) add("Missing canonical books: ${missingCanonical.sorted()}")
      val unexpectedCanonical = canonicalActual - canonicalIds
      if (unexpectedCanonical.isNotEmpty()) add("Unexpected canonical books: ${unexpectedCanonical.sorted()}")
      val missingDc = deuterocanonIds - dcActual
      if (missingDc.isNotEmpty()) add("Missing Deuterocanon books: ${missingDc.sorted()}")
      val unexpectedDc = dcActual - deuterocanonIds
      if (unexpectedDc.isNotEmpty()) add("Unexpected Deuterocanon books: ${unexpectedDc.sorted()}")
      entries.filter { it.chapterRange.isBlank() || it.openingChapter < 1 }.forEach {
        add("Invalid range for ${it.collection}/${it.bookId}")
      }
      val psalmGroups = epochs
        .firstOrNull { it.id == ChronologyEpochId.PSALMS_COLLECTION }
        ?.entries
        .orEmpty()
      if (psalmGroups.any { it.bookId != "psalms" || it.psalmAttribution == null }) {
        add("The Psalms collection contains an invalid attribution row")
      }
      val psalmAttributions = psalmGroups.mapNotNull { it.psalmAttribution }
      if (
        psalmAttributions.size != PsalmAttribution.entries.size ||
        psalmAttributions.toSet() != PsalmAttribution.entries.toSet()
      ) {
        add("The Psalms collection does not contain each attribution group exactly once")
      }
      val psalmNumbers = psalmGroups.flatMap { parseChronologyChapterRange(it.chapterRange) }
      val duplicatePsalms = psalmNumbers.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
      if (duplicatePsalms.isNotEmpty()) add("Duplicate Psalms in attribution groups: ${duplicatePsalms.sorted()}")
      val expectedPsalms = (1..150).toSet()
      val actualPsalms = psalmNumbers.toSet()
      val missingPsalms = expectedPsalms - actualPsalms
      if (missingPsalms.isNotEmpty()) add("Missing Psalms from attribution groups: ${missingPsalms.sorted()}")
      val unexpectedPsalms = actualPsalms - expectedPsalms
      if (unexpectedPsalms.isNotEmpty()) add("Unexpected Psalms in attribution groups: ${unexpectedPsalms.sorted()}")
    }
  }
}

/**
 * Resolves the requested chapter against the loaded edition/language. Some localized
 * single-chapter books use a different internal chapter id, so the first real chapter
 * is the safe fallback rather than a fabricated id.
 */
fun chronologyOpeningStoryId(book: Book, requestedChapter: Int): String? {
  val exact = "${book.id}-$requestedChapter"
  return book.stories.firstOrNull { it.id == exact }?.id
    ?: book.stories.firstOrNull { !it.id.endsWith("-prologue") }?.id
    ?: book.stories.firstOrNull()?.id
}

/** Known localized versification differences represented by the bundled assets. */
private fun ChronologyEntry.rangeForLanguage(appLanguage: String): String {
  val language = LocaleUtils.effectiveAssetTag(appLanguage)
  return when {
    bookId == "joel" && language in setOf("de", "fr") -> "1-4"
    bookId == "song_of_three" && language in setOf(
      "ar", "es", "fr", "it", "ja", "pt", "ru", "zh-Hans", "zh-Hant"
    ) -> "3"
    else -> chapterRange
  }
}

private fun ChronologyEntry.openingChapterForLanguage(appLanguage: String): Int {
  val language = LocaleUtils.effectiveAssetTag(appLanguage)
  return if (
    bookId == "song_of_three" && language in setOf(
      "ar", "es", "fr", "it", "ja", "pt", "ru", "zh-Hans", "zh-Hant"
    )
  ) 3 else openingChapter
}

@Composable
private fun ChronologyEpochId.title(): String = when (this) {
  ChronologyEpochId.CREATION_EARLY_HISTORY -> stringResource(Res.string.chronology_epoch_creation)
  ChronologyEpochId.PATRIARCHS -> stringResource(Res.string.chronology_epoch_patriarchs)
  ChronologyEpochId.EXODUS_WILDERNESS -> stringResource(Res.string.chronology_epoch_exodus)
  ChronologyEpochId.CONQUEST_JUDGES -> stringResource(Res.string.chronology_epoch_conquest)
  ChronologyEpochId.UNITED_KINGDOM -> stringResource(Res.string.chronology_epoch_united_kingdom)
  ChronologyEpochId.DIVIDED_KINGDOM -> stringResource(Res.string.chronology_epoch_divided_kingdom)
  ChronologyEpochId.JUDAH_FINAL_YEARS -> stringResource(Res.string.chronology_epoch_judah_final)
  ChronologyEpochId.BABYLONIAN_EXILE -> stringResource(Res.string.chronology_epoch_exile)
  ChronologyEpochId.RETURN_RESTORATION -> stringResource(Res.string.chronology_epoch_return)
  ChronologyEpochId.PSALMS_COLLECTION -> stringResource(Res.string.chronology_epoch_psalms)
  ChronologyEpochId.SECOND_TEMPLE -> stringResource(Res.string.chronology_epoch_second_temple)
  ChronologyEpochId.LIFE_OF_JESUS -> stringResource(Res.string.chronology_epoch_jesus)
  ChronologyEpochId.EARLY_CHURCH -> stringResource(Res.string.chronology_epoch_church)
}

@Composable
private fun PsalmAttribution.title(): String = when (this) {
  PsalmAttribution.DAVID -> stringResource(Res.string.chronology_psalms_david)
  PsalmAttribution.ASAPH -> stringResource(Res.string.chronology_psalms_asaph)
  PsalmAttribution.SONS_OF_KORAH -> stringResource(Res.string.chronology_psalms_korah)
  PsalmAttribution.SOLOMON -> stringResource(Res.string.chronology_psalms_solomon)
  PsalmAttribution.MOSES -> stringResource(Res.string.chronology_psalms_moses)
  PsalmAttribution.HEMAN_AND_SONS_OF_KORAH -> stringResource(Res.string.chronology_psalms_heman_korah)
  PsalmAttribution.ETHAN -> stringResource(Res.string.chronology_psalms_ethan)
  PsalmAttribution.UNNAMED -> stringResource(Res.string.chronology_psalms_unnamed)
}

@Composable
private fun ChronologyLane.title(): String = when (this) {
  ChronologyLane.HISTORICAL_FLOW -> stringResource(Res.string.chronology_historical_flow)
  ChronologyLane.PARALLEL_ACCOUNTS -> stringResource(Res.string.chronology_parallel_accounts)
  ChronologyLane.VOICES_FROM_PERIOD -> stringResource(Res.string.chronology_voices_period)
  ChronologyLane.DEUTEROCANON -> stringResource(Res.string.chronology_deuterocanon_lane)
}

@Composable
private fun ChronologyLane.containerColor(): Color = when (this) {
  ChronologyLane.HISTORICAL_FLOW -> MaterialTheme.colorScheme.primaryContainer
  ChronologyLane.PARALLEL_ACCOUNTS -> MaterialTheme.colorScheme.secondaryContainer
  ChronologyLane.VOICES_FROM_PERIOD -> MaterialTheme.colorScheme.tertiaryContainer
  ChronologyLane.DEUTEROCANON -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun ChronologyBasis.title(): String = when (this) {
  ChronologyBasis.TRADITIONAL_SETTING -> stringResource(Res.string.chronology_basis_traditional)
  ChronologyBasis.APPROXIMATE_PLACEMENT -> stringResource(Res.string.chronology_basis_approximate)
  ChronologyBasis.PLACEMENT_DEBATED -> stringResource(Res.string.chronology_placement_debated)
  ChronologyBasis.PARALLEL_ACCOUNT -> stringResource(Res.string.chronology_basis_parallel)
  ChronologyBasis.SPANS_MULTIPLE_PERIODS -> stringResource(Res.string.chronology_basis_multiple_periods)
}

@Composable
private fun GospelStage.title(): String = when (this) {
  GospelStage.BIRTH_EARLY_YEARS -> stringResource(Res.string.chronology_gospel_birth)
  GospelStage.PREPARATION_EARLY_MINISTRY -> stringResource(Res.string.chronology_gospel_preparation)
  GospelStage.GALILEAN_MINISTRY -> stringResource(Res.string.chronology_gospel_galilee)
  GospelStage.WITHDRAWAL_TRAINING -> stringResource(Res.string.chronology_gospel_training)
  GospelStage.JOURNEY_JERUSALEM -> stringResource(Res.string.chronology_gospel_journey)
  GospelStage.FINAL_WEEK_CRUCIFIXION -> stringResource(Res.string.chronology_gospel_final_week)
  GospelStage.RESURRECTION -> stringResource(Res.string.chronology_gospel_resurrection)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BibleChronologyScreen(
  appLanguage: String,
  includeDeuterocanon: Boolean,
  expandedEpochs: String,
  onIncludeDeuterocanonChange: (Boolean) -> Unit,
  onExpandedEpochsChange: (String) -> Unit,
  onBack: () -> Unit,
  onOpenChapterRange: (collection: String, bookId: String, openingChapter: Int) -> Unit
) {
  val context = LocalPlatformContext.current
  val titles = remember(appLanguage) {
    buildMap {
      listOf("old_testament", "new_testament", "deuterocanonical").forEach { collection ->
        ContentRepo.listBooksLocalized(context, collection, appLanguage)
          .filter { it.first.isNotBlank() }
          .forEach { (id, title) -> put("$collection/$id", title) }
      }
    }
  }
  var expandedEpochsValue by rememberSaveable {
    mutableStateOf(encodeChronologyExpandedEpochs(decodeChronologyExpandedEpochs(expandedEpochs)))
  }

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(Res.string.bible_chronology)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(Res.string.back)
            )
          }
        }
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
      item(key = "chronology-intro") {
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
          ),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Text(
              stringResource(Res.string.chronology_intro),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            Row(
              Modifier
                .fillMaxWidth()
                .clickable { onIncludeDeuterocanonChange(!includeDeuterocanon) },
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                stringResource(Res.string.chronology_include_deuterocanon),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
              )
              Spacer(Modifier.width(12.dp))
              Switch(
                checked = includeDeuterocanon,
                onCheckedChange = null
              )
            }
          }
        }
        Spacer(Modifier.height(16.dp))
      }

      itemsIndexed(
        BibleChronologyData.epochs,
        key = { _, epoch -> epoch.id.name }
      ) { index, epoch ->
        val isExpanded = epoch.id in decodeChronologyExpandedEpochs(expandedEpochsValue)
        ChronologyEpochRow(
          epoch = epoch,
          expanded = isExpanded,
          onToggleExpanded = {
            val updated = decodeChronologyExpandedEpochs(expandedEpochsValue).toMutableSet()
            if (!updated.add(epoch.id)) updated.remove(epoch.id)
            val encoded = encodeChronologyExpandedEpochs(updated)
            expandedEpochsValue = encoded
            onExpandedEpochsChange(encoded)
          },
          isFirst = index == 0,
          isLast = index == BibleChronologyData.epochs.lastIndex,
          includeDeuterocanon = includeDeuterocanon,
          appLanguage = appLanguage,
          titles = titles,
          onOpenChapterRange = onOpenChapterRange
        )
      }
    }
  }
}

@Composable
private fun ChronologyEpochRow(
  epoch: ChronologyEpoch,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  isFirst: Boolean,
  isLast: Boolean,
  includeDeuterocanon: Boolean,
  appLanguage: String,
  titles: Map<String, String>,
  onOpenChapterRange: (String, String, Int) -> Unit
) {
  val lineColor = MaterialTheme.colorScheme.outlineVariant
  val dotColor = MaterialTheme.colorScheme.primary

  Row(
    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    verticalAlignment = Alignment.Top
  ) {
    Canvas(
      Modifier
        .width(28.dp)
        .fillMaxHeight()
    ) {
      val x = size.width / 2f
      val dotY = 26.dp.toPx()
      val top = if (isFirst) dotY else 0f
      val bottom = if (isLast) dotY else size.height
      drawLine(lineColor, Offset(x, top), Offset(x, bottom), 2.dp.toPx(), StrokeCap.Round)
      drawCircle(dotColor, 5.dp.toPx(), Offset(x, dotY))
    }

    Card(
      modifier = Modifier.weight(1f).padding(bottom = 14.dp),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
      Row(
        Modifier
          .fillMaxWidth()
          .clickable(onClick = onToggleExpanded)
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          epoch.id.title(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f)
        )
        Icon(
          if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
          contentDescription = stringResource(
            if (expanded) Res.string.show_less else Res.string.show_more
          )
        )
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          val hasVisibleEntries = epoch.entries.any {
            it.lane != ChronologyLane.DEUTEROCANON || includeDeuterocanon
          }
          if (epoch.id == ChronologyEpochId.LIFE_OF_JESUS) {
            GospelStage.entries.forEach { stage ->
              val stageEntries = epoch.entries.filter { it.gospelStage == stage }
              if (stageEntries.isNotEmpty()) {
                ChronologyGospelStageGroup(
                  stage = stage,
                  entries = stageEntries,
                  appLanguage = appLanguage,
                  titles = titles,
                  onOpenChapterRange = onOpenChapterRange
                )
              }
            }
          } else if (epoch.id == ChronologyEpochId.PSALMS_COLLECTION) {
            Text(
              stringResource(Res.string.chronology_psalms_note),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            epoch.entries.forEach { entry ->
              val title = entry.psalmAttribution?.title()
                ?: titles["${entry.collection}/${entry.bookId}"]
                ?: stringResource(Res.string.books_missing, entry.bookId)
              ChronologyBookRow(
                entry = entry,
                title = title,
                appLanguage = appLanguage,
                showLane = false,
                onOpenChapterRange = onOpenChapterRange
              )
            }
          } else if (epoch.id == ChronologyEpochId.EARLY_CHURCH) {
            epoch.entries.forEach { entry ->
              if (entry.lane != ChronologyLane.DEUTEROCANON || includeDeuterocanon) {
                val title = titles["${entry.collection}/${entry.bookId}"]
                  ?: stringResource(Res.string.books_missing, entry.bookId)
                ChronologyBookRow(
                  entry = entry,
                  title = title,
                  appLanguage = appLanguage,
                  showLane = true,
                  onOpenChapterRange = onOpenChapterRange
                )
              }
            }
          } else {
            ChronologyLane.entries.forEach { lane ->
              if (lane != ChronologyLane.DEUTEROCANON || includeDeuterocanon) {
                val laneEntries = epoch.entries.filter { it.lane == lane }
                if (laneEntries.isNotEmpty()) {
                  ChronologyLaneGroup(
                    lane = lane,
                    entries = laneEntries,
                    appLanguage = appLanguage,
                    titles = titles,
                    onOpenChapterRange = onOpenChapterRange
                  )
                }
              }
            }
          }
          if (!hasVisibleEntries) {
            Text(
              stringResource(Res.string.chronology_include_deuterocanon),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChronologyLaneGroup(
  lane: ChronologyLane,
  entries: List<ChronologyEntry>,
  appLanguage: String,
  titles: Map<String, String>,
  onOpenChapterRange: (String, String, Int) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Surface(
      color = lane.containerColor(),
      shape = RoundedCornerShape(50),
      modifier = Modifier
    ) {
      Text(
        lane.title(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
      )
    }

    entries.forEach { entry ->
      val title = titles["${entry.collection}/${entry.bookId}"]
        ?: stringResource(Res.string.books_missing, entry.bookId)
      ChronologyBookRow(
        entry = entry,
        title = title,
        appLanguage = appLanguage,
        showLane = false,
        onOpenChapterRange = onOpenChapterRange
      )
    }
  }
}

@Composable
private fun ChronologyGospelStageGroup(
  stage: GospelStage,
  entries: List<ChronologyEntry>,
  appLanguage: String,
  titles: Map<String, String>,
  onOpenChapterRange: (String, String, Int) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stage.title(),
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary
    )
    entries.forEach { entry ->
      val title = titles["${entry.collection}/${entry.bookId}"]
        ?: stringResource(Res.string.books_missing, entry.bookId)
      ChronologyBookRow(
        entry = entry,
        title = title,
        appLanguage = appLanguage,
        showLane = true,
        onOpenChapterRange = onOpenChapterRange
      )
    }
  }
}

@Composable
private fun ChronologyBookRow(
  entry: ChronologyEntry,
  title: String,
  appLanguage: String,
  showLane: Boolean,
  onOpenChapterRange: (String, String, Int) -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable {
        onOpenChapterRange(
          entry.collection,
          entry.bookId,
          entry.openingChapterForLanguage(appLanguage)
        )
      },
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(
        Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
          stringResource(Res.string.chronology_chapters, entry.rangeForLanguage(appLanguage)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showLane || entry.basis != null) {
          val laneDetail = if (showLane) entry.lane.title() else null
          val basisDetail = entry.basis?.title()
          val details = listOfNotNull(laneDetail, basisDetail)
          Text(
            details.joinToString(" | "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Icon(
        Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
