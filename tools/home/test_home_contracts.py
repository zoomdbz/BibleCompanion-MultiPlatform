"""Read-only Home destination checks; native UI testing remains separate."""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion"
LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")


class HomeContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = (COMMON / "AppRoot.kt").read_text(encoding="utf-8")
        cls.home = cls.app.split("fun HomeScreen(", 1)[1].split(
            "private data class HomeStudyTile", 1
        )[0]

    def test_about_is_the_last_home_item_outside_collapsible_study(self):
        items = re.findall(r'\bitem\s*\(\s*"([^"]+)"\s*\)', self.home)
        self.assertEqual("about", items[-1])
        self.assertEqual(1, items.count("about"))
        study = self.home.index('item("study")')
        about = self.home.index('item("about")')
        self.assertLess(study, about)
        study_item = self.home[study:about]
        self.assertIn("AnimatedVisibility(visible = studyExpanded)", study_item)
        self.assertEqual(study_item.count("{"), study_item.count("}"))

    def test_about_uses_existing_localized_label_and_navigation(self):
        footer = self.home[self.home.index('item("about")'):self.home.index("// Onboarding overlay")]
        self.assertIn("StudyItem(", footer)
        self.assertIn("text = stringResource(Res.string.about_title)", footer)
        self.assertIn("modifier = Modifier.fillMaxWidth()", footer)
        self.assertIn("enabled = !navBusy", footer)
        self.assertIn("onClick = { safeNav { onAbout() } }", footer)
        self.assertIn("onAbout = { nav.navigate(Dest.About.route) { launchSingleTop = true } }", self.app)
        self.assertIn("composable(Dest.About.route)", self.app)
        for language in LANGUAGES:
            folder = {"en": "values", "zh-Hans": "values-zh-rCN", "zh-Hant": "values-zh-rTW"}.get(
                language, "values-" + language
            )
            resources = ET.parse(ROOT / "shared/src/commonMain/composeResources" / folder / "strings.xml")
            with self.subTest(language=language):
                labels = resources.findall("./string[@name='about_title']")
                self.assertEqual(1, len(labels))
                self.assertTrue(labels[0].text and labels[0].text.strip())
                self.assertNotIn("\ufffd", labels[0].text)

    def test_study_keeps_its_about_destination_after_faq(self):
        study = (COMMON / "StudyScreen.kt").read_text(encoding="utf-8")
        destinations = study.split("private val studyGroups =", 1)[1].split("@OptIn(", 1)[0]
        self.assertLess(destinations.index("Res.string.faqs"), destinations.index("Res.string.about_title"))
        self.assertRegex(destinations, r"StudyDestination\(Res\.string\.about_title,\s*Icons\.Filled\.Info,\s*Dest\.About\b")


if __name__ == "__main__":
    unittest.main()
