"""Read-only Divine Name wiring checks; Kotlin rendering tests run in CI."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion"
SOURCE = (COMMON / "ScriptureRefs.kt").read_text("utf-8-sig")
APPLY = SOURCE.split("internal fun applyDivineName(", 1)[1].split("private val aliasJson", 1)[0]


class DivineNameContracts(unittest.TestCase):
    def test_traditional_color_excludes_articles_without_changing_color_off_policy(self):
        traditional = APPLY.split('if (mode == "traditional")', 1)[1]
        traditional = traditional.split("val localizedName", 1)[0]
        self.assertIn("if (!colorActive)", traditional)
        self.assertIn('existingDnClose.replace(existingDnOpen.replace(text, ""), "")', traditional)
        self.assertIn("return excludeEnglishArticlesFromDivineNameColor(colored)", traditional)
        self.assertIn("mapOutsideDivineNameTags(text)", traditional)

    def test_article_removal_runs_after_source_normalization_before_name_output(self):
        stages = (
            "normalizeMarkedDivineNamesForReplacement(",
            "val resolved = replaceNameModeSegment(",
            "englishArticleBeforeResolvedName.replace(resolved)",
            ".replace(DIVINE_NAME_TOKEN, renderedName)",
        )
        offsets = [APPLY.index(stage) for stage in stages]
        self.assertEqual(sorted(offsets), offsets)
        self.assertIn('scriptureInlineTag.findAll(it.groupValues[1]).joinToString("")', APPLY)
        titles = SOURCE.split("private fun replaceEnglishOtTitles", 1)[1]
        titles = titles.split("private fun replaceNameModeSegment", 1)[0]
        for article in ("the", "The", "THE"):
            self.assertNotIn(f'.replace("{article} LORD"', titles)

    def test_case_insensitive_lord_replacement_requires_a_source_marked_name(self):
        marked = SOURCE.split("private fun normalizeMarkedDivineNameContent", 1)[1]
        marked = marked.split("private fun normalizeMarkedDivineNamesForReplacement", 1)[0]
        self.assertIn('"en" -> replaceEnglishOtTitles(englishMarkedLord.replace(source, "LORD"))', marked)
        unmarked = SOURCE.split("private fun replaceEnglishOtTitles", 1)[1]
        unmarked = unmarked.split("private fun normalizeMarkedDivineNameContent", 1)[0]
        self.assertNotIn("englishMarkedLord", unmarked)

    def test_article_color_splits_keep_semantic_tags_and_copy_text(self):
        helper = SOURCE.split("private fun excludeEnglishArticlesFromDivineNameColor", 1)[1]
        helper = helper.split("private fun replaceEnglishOtTitles", 1)[0]
        self.assertIn("existingDnSpan.replace(text)", helper)
        self.assertIn("scriptureInlineTag.findAll(value)", helper)
        self.assertIn("append(tag.value)", helper)
        self.assertIn("append(article.value)", helper)
        self.assertIn("match.value", helper)
        notes = (COMMON / "AppRoot.kt").read_text("utf-8-sig")
        export = notes.split("internal fun genericNotesPlainText(", 1)[1]
        export = export.split("private fun FullNoteSelectionDialog", 1)[0]
        self.assertIn("applyDivineName(", export)
        self.assertIn("markdownToPlainText(", export)


if __name__ == "__main__":
    unittest.main()
