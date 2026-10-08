"""Read-only checks for the localized Jesus' Divinity dropdown hierarchy."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
NOTES = ROOT / "shared/assets/notes"
CRY_TITLES = {
    "en": "Jesus' Cry of Forsakenness",
    "ar": "صرخة يسوع في الهجر",
    "de": "Der Schrei der Verlassenheit Jesu",
    "es": "El Grito de Desamparo de Jesús",
    "fr": "Le Cri d’Abandon de Jésus",
    "hi": "यीशु की त्याग की पुकार",
    "it": "Il Grido di Abbandono di Gesù",
    "ja": "イエスの見捨てられた叫び",
    "ko": "예수님의 버림받으심의 외침",
    "pt": "O Grito de Abandono de Jesus",
    "ru": "Крик оставленности Иисуса",
    "zh-Hans": "耶稣被离弃的呼喊",
    "zh-Hant": "耶穌被離棄的呼喊",
}


class JesusDivinitySections(unittest.TestCase):
    def test_forsakenness_is_the_third_peer_dropdown_in_every_language(self):
        paths = sorted(NOTES.glob("*/jesus_divinity.md"))
        self.assertEqual(set(CRY_TITLES), {path.parent.name for path in paths})
        for path in paths:
            with self.subTest(language=path.parent.name):
                text = path.read_text("utf-8-sig")
                title = CRY_TITLES[path.parent.name]
                headings = re.findall(r"^## (.+)$", text, re.M)
                self.assertEqual(4, len(headings))
                self.assertEqual(title, headings[2])
                self.assertEqual(1, text.splitlines().count("## " + title))
                self.assertNotIn("# " + title, text.splitlines())
                # The preceding Is God dropdown must end before the Cry section.
                preceding = text.split("## " + headings[1] + "\n", 1)[1]
                preceding = preceding.split("## " + title + "\n", 1)[0]
                self.assertNotIn(title, preceding)

    def test_forsakenness_keeps_its_children_before_prophecy_fulfillment(self):
        for language, title in CRY_TITLES.items():
            with self.subTest(language=language):
                text = (NOTES / language / "jesus_divinity.md").read_text("utf-8-sig")
                headings = re.findall(r"^## (.+)$", text, re.M)
                body = text.split("## " + title + "\n", 1)[1]
                body = body.split("## " + headings[3] + "\n", 1)[0]
                self.assertEqual(4, len(re.findall(r"^### (.+)$", body, re.M)))
                self.assertIn("27:46", body)
                self.assertIn("15:34", body)

    def test_shared_screen_uses_h2_for_peer_dropdowns(self):
        app = (ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion/AppRoot.kt").read_text("utf-8-sig")
        self.assertIn('headingPrefix: String = "## "', app)
        self.assertIn('"jesus_divinity.md", prefs, repo, collapsible = true', app)
        self.assertIn('splitMarkdownSections(sectionBody, "### ")', app)


if __name__ == "__main__":
    unittest.main()
