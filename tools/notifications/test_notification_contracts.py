"""Read-only asset/wiring checks, not a substitute for native device testing."""

import functools
import json
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "shared/assets"
COMMON = ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion"
LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")
KEYS = ("title", "toggle", "desc", "time", "save", "ios_window", "permission_denied", "system_settings",
        "clock_12", "clock_24", "am", "pm", "time_with_period")
ANCHOR = re.compile(r"\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\s*\.?\s*$")
REFERENCE = re.compile(r"^(.+?)\s+(\d+):(\d+)(?:-(\d+))?$")


@functools.lru_cache(None)
def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


class NotificationContracts(unittest.TestCase):
    def test_every_language_has_localized_settings_and_notification_actions(self):
        for lang in LANGUAGES:
            folder = {"en": "values", "zh-Hans": "values-zh-rCN", "zh-Hant": "values-zh-rTW"}.get(lang, "values-" + lang)
            resources = ET.parse(ROOT / "shared/src/commonMain/composeResources" / folder / "strings.xml")
            with self.subTest(language=lang):
                for key in KEYS:
                    found = resources.findall(f"./string[@name='daily_notification_{key}']")
                    self.assertEqual(1, len(found), key)
                    self.assertTrue(found[0].text and found[0].text.strip(), key)
                labels = read_json(ASSETS / "notifications" / (lang + ".json"))
                self.assertEqual({"title", "copy", "share", "copied"}, set(labels))
                self.assertTrue(all(isinstance(value, str) and value.strip() for value in labels.values()))
                if lang != "en":
                    self.assertNotEqual(labels, read_json(ASSETS / "notifications/en.json"))

    def test_notification_section_sits_between_search_and_accessibility_with_dividers(self):
        app = (COMMON / "AppRoot.kt").read_text(encoding="utf-8")
        search = app.index("SectionHeader(stringResource(Res.string.search_section))")
        notification = app.index("SectionHeader(stringResource(Res.string.daily_notification_title))")
        accessibility = app.index("SectionHeader(stringResource(Res.string.accessibility_section))")
        self.assertLess(search, notification)
        self.assertLess(notification, accessibility)
        self.assertIn("HorizontalDivider", app[search:notification])
        self.assertIn("HorizontalDivider", app[notification:accessibility])
        self.assertIn("TimeInput(state = time)", app)
        self.assertIn("repo.setDailyVerseNotificationTime(time.hour * 60 + time.minute)", app)

    def test_defaults_are_opt_in_and_both_backends_persist_the_time(self):
        models = (COMMON / "Models.kt").read_text(encoding="utf-8")
        self.assertIn("val dailyVerseNotifications: Boolean = false", models)
        self.assertIn("val dailyVerseNotificationMinuteOfDay: Int = 9 * 60", models)
        self.assertIn("val dailyVerseNotification24Hour: Boolean = false", models)
        for platform, filename in (("androidMain", "AndroidPrefs.kt"), ("iosMain", "IosPrefs.kt")):
            text = (ROOT / "shared/src" / platform / "kotlin/com/dividesbyzer0/biblecompanion" / filename).read_text(encoding="utf-8")
            self.assertIn('"daily_verse_notifications"', text)
            self.assertIn('"daily_verse_notification_time"', text)
            self.assertIn('"daily_verse_notification_24_hour"', text)
            self.assertIn("actual suspend fun setDailyVerseNotificationTime", text)
            self.assertIn("actual suspend fun setDailyVerseNotification24Hour", text)
            self.assertIn("coerceIn(0, 1439)", text)

    def test_clock_format_choice_preserves_picker_time_and_commits_only_on_save(self):
        app = (COMMON / "AppRoot.kt").read_text(encoding="utf-8")
        picker = app[app.index("if (showNotificationTime) {"):app.index("// Bible.com (YouVersion) catalog")]
        self.assertIn("mutableStateOf(prefs.dailyVerseNotification24Hour)", picker)
        self.assertIn("is24Hour = use24Hour", picker)
        self.assertIn("time.is24hour = format24Hour", picker)
        self.assertIn("key(use24Hour) { TimeInput(state = time) }", picker)
        self.assertNotIn("is24Hour = true", picker)
        self.assertIn("repo.setDailyVerseNotification24Hour(use24Hour)", picker)
        self.assertLess(picker.index("confirmButton"), picker.index("repo.setDailyVerseNotification24Hour"))

    def test_all_daily_and_feast_references_have_real_native_destinations(self):
        books = {}
        for collection in ("old_testament", "new_testament", "deuterocanonical"):
            for book_id, title in read_json(ASSETS / "books" / collection / "en/_index.json"):
                books[title.lower()] = (collection, book_id)
        for lang in LANGUAGES:
            daily = read_json(ASSETS / "daily_verses" / lang / "daily.json")["verses"]
            feasts = read_json(ASSETS / "daily_verses" / lang / "feasts.json")["feastVerses"].values()
            for entry in [*daily, *feasts]:
                with self.subTest(language=lang, reference=entry["ref"]):
                    match = REFERENCE.fullmatch(entry["ref"])
                    self.assertIsNotNone(match)
                    name, chapter, start, end = match.groups()
                    name = "Acts" if name.lower() == "acts of the apostles" else name
                    self.assertIn(name.lower(), books)
                    collection, book_id = books[name.lower()]
                    book = read_json(ASSETS / "books" / collection / lang / (book_id + ".json"))
                    chapter, start = int(chapter), int(start)
                    end = int(end) if end else start
                    story = next((s for s in book["stories"] if s["id"].rsplit("-", 1)[-1] == str(chapter)), None)
                    if len(book["stories"]) == 1 and chapter == 1:
                        story = book["stories"][0]
                    self.assertIsNotNone(story)
                    present = set()
                    for bullet in story["summaryBullets"]:
                        marker = ANCHOR.search(bullet)
                        if marker and int(marker[1]) == chapter:
                            first = int(marker[2])
                            present.update(range(first, int(marker[3] or first) + 1))
                    self.assertTrue(set(range(start, end + 1)) <= present)

    def test_native_routes_keep_language_and_edition(self):
        text = (COMMON / "DailyVerseNotifications.kt").read_text(encoding="utf-8")
        self.assertIn("sourceLang = language, sourceEdition = edition", text)
        self.assertIn("loaded.effectiveEdition != edition", text)
        self.assertIn("notificationAnchorExists(target.summaryBullets, chapter, first, last)", text)

    def test_notification_labels_ship_in_both_native_apps(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('assets.srcDirs("../shared/assets")', gradle)
        project = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
        self.assertIn("path = ../shared/assets/notifications", project)
        self.assertEqual(2, project.count("/* notifications in Resources */"))
        self.assertEqual(1, project.count("E7B2C3D40000001D /* notifications */ ="))

    def test_native_permissions_and_lifecycle_hooks(self):
        manifest = ET.parse(ROOT / "app/src/androidMain/AndroidManifest.xml")
        ns = "{http://schemas.android.com/apk/res/android}"
        permissions = {node.get(ns + "name") for node in manifest.findall("uses-permission")}
        self.assertIn("android.permission.POST_NOTIFICATIONS", permissions)
        self.assertIn("android.permission.RECEIVE_BOOT_COMPLETED", permissions)
        self.assertNotIn("android.permission.SCHEDULE_EXACT_ALARM", permissions)
        self.assertNotIn("android.permission.USE_EXACT_ALARM", permissions)
        for name in (".DailyVerseNotificationReceiver", ".CopyDailyVerseActivity"):
            nodes = [node for node in manifest.find("application") if node.get(ns + "name") == name]
            self.assertEqual(1, len(nodes), name)
            self.assertEqual("false", nodes[0].get(ns + "exported"))
        swift = (ROOT / "iosApp/iosApp/iOSApp.swift").read_text(encoding="utf-8")
        for token in ("UNCalendarNotificationTrigger", "daily-copy", "daily-share", "sourceRect",
                      "token == generation", "min(60, 64 - otherCount)", "DailyVerseNotificationManager.shared.install()"):
            self.assertIn(token, swift)


if __name__ == "__main__":
    unittest.main()
