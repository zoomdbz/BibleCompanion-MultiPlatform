"""Read-only Android manifest and native resource regression checks."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ANDROID = Path(__file__).resolve().parents[2] / "app/src/androidMain"
NS = "{http://schemas.android.com/apk/res/android}"


class ManifestContracts(unittest.TestCase):
    def test_custom_links_remain_browsable_without_web_domain_verification(self):
        manifest = ET.parse(ANDROID / "AndroidManifest.xml")
        activity = next(
            node for node in manifest.findall("application/activity")
            if node.get(NS + "name") == ".MainActivity"
        )
        self.assertEqual("true", activity.get(NS + "exported"))
        custom = [
            node for node in activity.findall("intent-filter")
            if any(data.get(NS + "scheme") == "biblecompanion" for data in node.findall("data"))
        ]
        self.assertEqual(1, len(custom))
        link_filter = custom[0]
        self.assertNotEqual("true", link_filter.get(NS + "autoVerify"))
        self.assertEqual("open", link_filter.find("data").get(NS + "host"))
        self.assertEqual(
            {"android.intent.action.VIEW"},
            {node.get(NS + "name") for node in link_filter.findall("action")},
        )
        self.assertEqual(
            {"android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"},
            {node.get(NS + "name") for node in link_filter.findall("category")},
        )

    def test_verified_app_links_only_use_web_schemes(self):
        manifest = ET.parse(ANDROID / "AndroidManifest.xml")
        for node in manifest.findall("application/activity/intent-filter"):
            if node.get(NS + "autoVerify") == "true":
                schemes = {data.get(NS + "scheme") for data in node.findall("data") if data.get(NS + "scheme")}
                self.assertTrue(schemes)
                self.assertLessEqual(schemes, {"http", "https"})

    def test_widget_loading_label_exists_in_every_native_locale(self):
        resources = list((ANDROID / "res").glob("values*/strings.xml"))
        self.assertEqual(13, len(resources))
        for path in resources:
            with self.subTest(locale=path.parent.name):
                root = ET.parse(path)
                labels = root.findall("string[@name='widget_loading']")
                self.assertEqual(1, len(labels))
                self.assertTrue(labels[0].text.strip())
        layout = ET.parse(ANDROID / "res/layout/widget_loading.xml")
        self.assertEqual("@string/widget_loading", layout.find("TextView").get(NS + "text"))


if __name__ == "__main__":
    unittest.main()
