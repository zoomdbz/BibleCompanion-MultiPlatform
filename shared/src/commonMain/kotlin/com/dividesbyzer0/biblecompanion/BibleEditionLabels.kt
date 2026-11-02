package com.dividesbyzer0.biblecompanion

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

/** Reader and Options use the same localized names and available editions. */
@Composable
internal fun bibleEditionLabel(appLanguage: String, editionId: String): String =
  when (BibleEditions.effective(appLanguage, editionId)) {
    BibleEditions.BSB -> stringResource(Res.string.version_bsb)
    BibleEditions.KJV_1769 -> stringResource(Res.string.version_kjv)
    BibleEditions.ONEN_2015 -> "ONEN 2015"
    BibleEditions.SUV_1952 -> "SUV 1952"
    BibleEditions.defaultForLanguage(appLanguage) -> stringResource(Res.string.version_local_modern)
    else -> stringResource(Res.string.version_local_traditional)
  }

@Composable
internal fun bibleEditionOptions(appLanguage: String): List<Pair<String, String>> =
  BibleEditions.available(appLanguage).map { it to bibleEditionLabel(appLanguage, it) }
