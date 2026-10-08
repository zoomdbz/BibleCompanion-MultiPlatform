"""Read-only reader edition data/wiring checks; Kotlin behavior tests run in CI."""

import json
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion"
APP = (COMMON / "AppRoot.kt").read_text("utf-8-sig")
RESUME = (COMMON / "ReadingResume.kt").read_text("utf-8-sig")
SHEET = (COMMON / "ReaderChapterSheet.kt").read_text("utf-8-sig")
BOOKS = ROOT / "shared/assets/books"
REF = re.compile(r"\((\d+):(\d+)(?:-(\d+))?\)[.\s]*$")


def base_units(language, collection, book):
    document = json.loads((BOOKS / collection / language / f"{book}.json").read_text("utf-8-sig"))
    return [tuple(map(int, (m[1], m[2], m[3] or m[2])))
            for story in document["stories"] for bullet in story["summaryBullets"]
            if (m := REF.search(bullet))]


def alternate_units(language, edition, collection, book):
    document = json.loads((BOOKS / "editions" / language / edition / collection / f"{book}.json").read_text("utf-8-sig"))
    return [(v["chapter"], v["verse"], v.get("verseEnd") or v["verse"])
            for chapter in document["chapters"] for v in chapter["verses"]]


def full_unit(anchor, units):
    chapter, start, end = anchor
    units = [u for u in units if u[0] == chapter]
    if not all(any(u[1] <= number <= u[2] for u in units) for number in range(start, end + 1)):
        return None
    touching = [u for u in units if u[1] <= end and u[2] >= start]
    return chapter, min(u[1] for u in touching), max(u[2] for u in touching)


def reviewed_mapping(language, edition, book, anchor, reverse=False):
    document = json.loads((BOOKS / "editions" / language / edition / "_reference_map.json").read_text("utf-8-sig"))
    rules = next(b["mappings"] for b in document["books"] if b["bookId"] == book)
    source, target = ("target", "source") if reverse else ("source", "target")
    result = []
    for verse in range(anchor[1], anchor[2] + 1):
        matching = [r for r in rules if r[f"{source}Chapter"] == anchor[0]
                    and r[f"{source}Verse"] <= verse <= r.get(f"{source}VerseEnd", r[f"{source}Verse"])]
        if not matching:
            return None
        for rule in matching:
            source_start = rule[f"{source}Verse"]
            source_end = rule.get(f"{source}VerseEnd", source_start)
            target_start = rule[f"{target}Verse"]
            target_end = rule.get(f"{target}VerseEnd", target_start)
            chapter = rule[f"{target}Chapter"]
            if source_end - source_start == target_end - target_start:
                number = target_start + verse - source_start
                result.append((chapter, number, number))
            elif source_end == source_start or target_end == target_start:
                result.append((chapter, target_start, target_end))
            else:
                return None
    ordered = sorted(set(result))
    chapter, start, end = ordered[0]
    for next_chapter, next_start, next_end in ordered[1:]:
        if next_chapter != chapter or next_start > end + 1:
            return None
        end = max(end, next_end)
    return chapter, start, end


class ReaderEditionContracts(unittest.TestCase):
    def test_external_description_follows_the_selected_provider(self):
        controls = APP.split("AnimatedVisibility(visible = !isInternal)", 1)[1]
        controls = controls.split("// Collections", 1)[0]
        self.assertRegex(
            controls,
            r'text = stringResource\(\s*if \(prefs.readerMode == "biblegateway"\)\s*'
            r'Res\.string\.external_biblegateway_version_desc\s*else\s*'
            r'Res\.string\.external_bible_version_desc\s*\)',
        )

    def test_every_language_has_distinct_external_provider_descriptions(self):
        paths = sorted((ROOT / "shared/src/commonMain/composeResources").glob("values*/strings.xml"))
        self.assertEqual(13, len(paths))
        for path in paths:
            with self.subTest(locale=path.parent.name):
                resources = ET.parse(path)
                gateway = resources.findall("./string[@name='external_biblegateway_version_desc']")
                biblecom = resources.findall("./string[@name='external_bible_version_desc']")
                self.assertEqual(1, len(gateway))
                self.assertEqual(1, len(biblecom))
                text = gateway[0].text
                self.assertTrue(text and text.strip())
                self.assertIn("Bible Gateway", text)
                self.assertIn("Bible Companion", text)
                self.assertNotIn("Bible.com", text)
                self.assertNotIn("YouVersion", text)
                self.assertNotIn("\ufffd", text)
                self.assertIn("Bible.com", biblecom[0].text)
                self.assertIn("YouVersion", biblecom[0].text)
                self.assertNotEqual(text, biblecom[0].text)

    def test_gateway_description_names_the_website_and_preserves_chinese_scripts(self):
        resources = ROOT / "shared/src/commonMain/composeResources"
        def description(locale):
            return ET.parse(resources / locale / "strings.xml").find(
                "./string[@name='external_biblegateway_version_desc']"
            ).text
        self.assertEqual(
            "Choose the Bible version for external references. "
            "Links open on the Bible Gateway website in your browser. "
            "This does not change the text in Bible Companion.",
            description("values"),
        )
        simplified = description("values-zh-rCN")
        traditional = description("values-zh-rTW")
        self.assertIn("浏览器", simplified)
        self.assertNotIn("瀏覽器", simplified)
        self.assertIn("瀏覽器", traditional)
        self.assertNotIn("浏览器", traditional)

    def test_every_language_has_the_new_button_label(self):
        paths = sorted((ROOT / "shared/src/commonMain/composeResources").glob("values*/strings.xml"))
        self.assertEqual(13, len(paths))
        labels = {}
        for path in paths:
            nodes = ET.parse(path).findall("./string[@name='ui_choose_bible_version']")
            self.assertEqual(1, len(nodes), path.parent.name)
            label = nodes[0].text
            self.assertTrue(label and label.strip())
            self.assertNotIn("\ufffd", label)
            labels[path.parent.name] = label
        self.assertEqual("Choose Bible version", labels["values"])
        self.assertEqual("选择圣经版本", labels["values-zh-rCN"])
        self.assertEqual("選擇聖經版本", labels["values-zh-rTW"])

    def test_picker_keeps_book_on_left_and_version_on_right_with_wrapping_text(self):
        controls = SHEET.split("Row(Modifier.fillMaxWidth(), verticalAlignment", 1)[1].split("SingleChoiceSegmentedButtonRow", 1)[0]
        self.assertLess(controls.index("Alignment.CenterStart"), controls.index("Alignment.CenterEnd"))
        self.assertEqual(2, controls.count("Modifier.weight(1f)"))
        self.assertIn("Res.string.ui_choose_book", controls)
        self.assertIn("Res.string.ui_choose_bible_version", controls)
        self.assertNotIn("maxLines", controls)
        self.assertIn("selected = id == selectedEditionId", controls)
        self.assertIn("onChooseEdition(id)", controls)
        self.assertIn("chapter = number\n", SHEET)
        self.assertIn("verseTab = true", SHEET)

    def test_picker_options_and_reader_header_share_localized_option_names(self):
        labels = (COMMON / "BibleEditionLabels.kt").read_text("utf-8-sig")
        self.assertIn("BibleEditions.available(appLanguage)", labels)
        for key in ("version_bsb", "version_kjv", "version_local_modern", "version_local_traditional"):
            self.assertIn(f"Res.string.{key}", labels)
        self.assertIn("bibleEditionOptions(appLanguage)", SHEET)
        self.assertIn("bibleEditionOptions(prefs.appLanguage)", APP)
        self.assertIn("bibleEditionLabel(effectiveLanguage, activeEditionId)", APP)

    def test_resume_route_and_card_follow_current_setting_not_old_link_edition(self):
        loader = RESUME.split("internal fun loadReadingResume(", 1)[1].split("internal fun readingResumeCanRestoreSavedEdition", 1)[0]
        self.assertIn("BibleEditions.effective(language, prefs.internalBibleVersion)", loader)
        self.assertNotIn("BibleEditions.forNavigation", RESUME)
        self.assertIn("loadReadingResume(context, prefs)", RESUME)
        route = APP.split("val resume = loadReadingResume(ctx, prefs)", 1)[1].split("val navBack", 1)[0]
        self.assertIn("resume?.storyId", route)
        self.assertIn("verse = resume?.verse", route)
        self.assertIn("sourceLang = LocaleUtils.effectiveAssetTag(prefs.appLanguage)", route)
        self.assertIn("sourceEdition = BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)", route)
        self.assertNotIn("prefs.lastReadSourceEdition", route)
        for path in (COMMON / "StudyScreen.kt", COMMON / "AppRoot.kt"):
            self.assertIn("rememberReadingResume(prefs)", path.read_text("utf-8-sig"))

    def test_saved_reader_restore_checks_actual_edition_in_addition_to_identity(self):
        resume_route = APP.split("val continueReading:", 1)[1].split("val navBack", 1)[0]
        self.assertIn("readingResumeCanRestoreSavedEdition(prefs)", resume_route)
        self.assertIn('restored?.arguments?.getString("sourceEdition")', resume_route)
        self.assertIn("&& BibleEditions.forNavigation(", resume_route)
        self.assertIn("sourceEdition == BibleEditions.effective(prefs.appLanguage, prefs.internalBibleVersion)", RESUME)

    def test_in_reader_switch_persists_selection_replaces_pinned_route_and_stops_old_text(self):
        handler = APP.split("onChangeBibleVersion = {", 1)[1].split("onChooseBook =", 1)[0]
        self.assertIn("repo.setInternalBibleVersion(editionId)", handler)
        self.assertIn("expireSavedReadingNowState()", handler)
        self.assertIn("sourceEdition = editionId", handler)
        self.assertIn("requestId = freshInternalReaderRequestId()", handler)
        self.assertIn("launchSingleTop = true", handler)
        selection = APP.split("onChooseEdition = choose@{", 1)[1].split("onIntro =", 1)[0]
        self.assertIn("readerPositionForEdition(", selection)
        self.assertIn("sourceEdition = activeEditionId", selection)
        self.assertIn("targetEdition = targetBook.effectiveEdition", selection)
        for statement in ("chapterTtsPlaying = false", "sectionTtsKey = null", "platformTtsStop(ctx)",
                          "selectedBullets = emptySet()", "cancelViewportRestoreForNavigation()"):
            self.assertIn(statement, selection)

    def test_links_and_bookmarks_still_pin_their_source_edition(self):
        link_reader = APP.split("val readerPrefs = prefs.copy(internalBibleVersion", 1)[1].split("val linkedEditionUnavailable", 1)[0]
        self.assertIn("BibleEditions.forNavigation(", link_reader)
        self.assertIn("sourceLangArg, sourceEditionArg", link_reader)
        editions = (COMMON / "BibleEdition.kt").read_text("utf-8-sig")
        self.assertIn("return selectedForLanguage(appLanguage, sourceEdition)", editions)

    def test_reviewed_cross_chapter_examples_match_bundled_source_and_target_units(self):
        cases = (
            ("en", "kjv1769", "new_testament", "matthew", (5, 16, 16), (5, 16, 16)),
            ("de", "luther1912", "old_testament", "isaiah", (9, 1, 1), (9, 2, 2)),
            ("it", "diodati1885", "old_testament", "job", (40, 1, 1), (39, 34, 34)),
            ("ru", "synodal1876", "new_testament", "romans", (16, 25, 25), (14, 24, 24)),
            ("zh-Hant", "cuv", "new_testament", "john", (7, 53, 53), (8, 1, 1)),
        )
        for language, edition, collection, book, source, expected in cases:
            with self.subTest(language=language, book=book):
                units = base_units(language, collection, book)
                self.assertEqual(source, full_unit(source, units))
                mapped = reviewed_mapping(language, edition, book, source)
                self.assertEqual(expected, mapped)
                native = full_unit(mapped, alternate_units(language, edition, collection, book))
                self.assertIsNotNone(native)
                self.assertLessEqual(native[1], expected[1])
                self.assertGreaterEqual(native[2], expected[2])

    def test_cross_chapter_merged_passage_cannot_be_invented_as_one_range(self):
        anchor = full_unit((12, 17, 17), base_units("en", "new_testament", "revelation"))
        self.assertEqual((12, 17, 18), anchor)
        self.assertIsNone(reviewed_mapping("en", "kjv1769", "revelation", anchor))
        helper = RESUME.split("internal fun readerPositionForEdition", 1)[1]
        self.assertIn("} ?: return fallback", helper)
        self.assertIn("fullNativeVerseAnchor(mapped", helper)


if __name__ == "__main__":
    unittest.main()
