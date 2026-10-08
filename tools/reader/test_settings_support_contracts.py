"""Source-only checks for Options support controls; device testing runs in CI."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
APP = (ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion/AppRoot.kt").read_text("utf-8-sig")
SETTINGS = APP.split("fun SettingsScreen(", 1)[1].split("private fun SectionHeader(", 1)[0]


class SettingsSupportContracts(unittest.TestCase):
    def test_options_have_no_donation_controls(self):
        for entry in ("Res.string.donation", "Res.string.donation_text",
                      "Res.string.donate_button", "Res.string.donate_message",
                      "paypal.me"):
            with self.subTest(entry=entry):
                self.assertNotIn(entry, SETTINGS)

    def test_rate_and_share_controls_keep_existing_platform_routes(self):
        self.assertIn("Res.string.rate_app", SETTINGS)
        self.assertIn("Res.string.share_app", SETTINGS)
        self.assertIn("val url = if (isApplePlatform)", SETTINGS)
        self.assertIn("https://apps.apple.com/us/app/bible-companion-offline/id6763134690", SETTINGS)
        self.assertIn("https://play.google.com/store/apps/details?id=com.dividesbyzer0.biblecompanion", SETTINGS)
        self.assertIn("https://wordinlight.org/biblecompanion", SETTINGS)
        self.assertIn("platformShareText(", SETTINGS)


if __name__ == "__main__":
    unittest.main()
