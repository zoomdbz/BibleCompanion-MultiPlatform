"""Source-only checks for localized About copy and consistent body typography."""

from pathlib import Path
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "shared/src/commonMain/composeResources"
BASELINE = "0d6328bf355be426bdb8d5a4acf9af8bbc81f356"
EDITION_WORDS = {
    "values": ("modern", "traditional"),
    "values-ar": ("حديثة", "تقليدية"),
    "values-de": ("moderne", "traditionelle"),
    "values-es": ("modernas", "tradicionales"),
    "values-fr": ("modernes", "traditionnelles"),
    "values-hi": ("आधुनिक", "पारंपरिक"),
    "values-it": ("moderne", "tradizionali"),
    "values-ja": ("現代訳", "伝統訳"),
    "values-ko": ("현대", "전통"),
    "values-pt": ("modernas", "tradicionais"),
    "values-ru": ("современные", "традиционные"),
    "values-zh-rCN": ("现代译本", "传统译本"),
    "values-zh-rTW": ("現代譯本", "傳統譯本"),
}


def about_strings(document):
    return {
        item.attrib["name"]: "".join(item.itertext())
        for item in document.findall("string")
        if item.attrib.get("name", "").startswith("about_")
    }


def feature_sentences(text):
    # The first sentence describes reference providers, the second editions,
    # and the remaining text describes reading and study tools. Do not split
    # the dot in Bible.com as a sentence boundary.
    return re.split(r"(?<=\.)\s+|(?<=。)|(?<=।)\s*", text, maxsplit=2)


class AboutContracts(unittest.TestCase):
    def test_every_locale_describes_modern_and_traditional_editions(self):
        paths = sorted(RESOURCES.glob("values*/strings.xml"))
        self.assertEqual(set(EDITION_WORDS), {path.parent.name for path in paths})
        for path in paths:
            with self.subTest(locale=path.parent.name):
                features = about_strings(ET.parse(path).getroot())["about_features_text"]
                sentences = feature_sentences(features)
                self.assertEqual(3, len(sentences))
                for word in EDITION_WORDS[path.parent.name]:
                    self.assertIn(word, sentences[1])

    def test_only_the_edition_sentence_changes_in_about_resources(self):
        available = subprocess.run(
            ["git", "cat-file", "-e", BASELINE + "^{commit}"],
            cwd=ROOT, capture_output=True,
        ).returncode == 0
        if not available:
            self.skipTest("Preservation baseline absent in shallow checkout")
        for locale in EDITION_WORDS:
            with self.subTest(locale=locale):
                path = RESOURCES / locale / "strings.xml"
                relative = path.relative_to(ROOT).as_posix()
                original_xml = subprocess.run(
                    ["git", "show", BASELINE + ":" + relative],
                    cwd=ROOT, capture_output=True, check=True,
                ).stdout
                original = about_strings(ET.fromstring(original_xml))
                updated = about_strings(ET.parse(path).getroot())
                old_features = feature_sentences(original.pop("about_features_text"))
                new_features = feature_sentences(updated.pop("about_features_text"))
                self.assertEqual(original, updated)
                self.assertEqual(3, len(old_features))
                self.assertEqual(3, len(new_features))
                self.assertEqual(old_features[0], new_features[0])
                self.assertEqual(old_features[2], new_features[2])
                self.assertNotEqual(old_features[1], new_features[1])

    def test_all_three_about_paragraphs_use_the_same_body_style(self):
        source = (ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion/AppRoot.kt").read_text("utf-8-sig")
        about = source.split("fun AboutScreen(", 1)[1].split("private fun splitMarkdownSections(", 1)[0]
        for resource in ("about_what_text", "about_features_text", "about_mission_text"):
            with self.subTest(resource=resource):
                self.assertRegex(
                    about,
                    r"Text\(\s*stringResource\(Res\.string\." + resource
                    + r"\),\s*style = MaterialTheme\.typography\.bodyLarge\s*\)",
                )
        self.assertNotIn("FontStyle.Italic", about)
        self.assertIn("SelectionContainer {", about)


if __name__ == "__main__":
    unittest.main()
