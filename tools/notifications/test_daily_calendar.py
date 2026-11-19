"""Offline daily-calendar asset tests; these do not build either native app."""

import calendar
from datetime import date, timedelta
import hashlib
import json
import os
from pathlib import Path
import re
import unittest
from unittest.mock import patch

import daily_calendar as daily


def text_digest(text):
    return hashlib.sha256(daily.normalized_text(text).encode("utf-8")).hexdigest()


class DailyCalendarTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reference_map = daily.read_json(daily.MAP_PATH)
        cls.english = daily.read_json(daily.ASSETS / "daily_verses/en/daily.json")["verses"]

    def test_every_bundled_language_has_a_complete_calendar(self):
        paths = sorted((daily.ASSETS / "daily_verses").glob("*/daily.json"))
        self.assertEqual(14, len(paths))
        for path in paths:
            with self.subTest(language=path.parent.name):
                entries = daily.read_json(path)["verses"]
                self.assertEqual(366, len(entries))
                self.assertTrue(all(entry["text"].strip() and entry["ref"].strip() for entry in entries))

    def test_japanese_and_russian_follow_every_scheduled_day_and_native_unit(self):
        for language in daily.LANGUAGES:
            entries = daily.read_json(daily.ASSETS / "daily_verses" / language / "daily.json")["verses"]
            self.assertEqual(daily.build_bank(language), {"verses": entries})
            for day, (scheduled, entry) in enumerate(zip(self.english, entries), 1):
                with self.subTest(language=language, day=day):
                    reference = daily.calendar_reference(language, scheduled["ref"], self.reference_map)
                    native = daily.native_entry(language, reference)
                    self.assertEqual(native["ref"], entry["ref"])
                    self.assertEqual(daily.normalized_text(native["text"]), daily.normalized_text(entry["text"]))
                    self.assertNotIn("\ufffd", entry["text"])

    def test_reference_calendar_and_reviewed_source_hashes_are_pinned(self):
        calendar_path = daily.ROOT / self.reference_map["calendarSource"]
        canonical = json.dumps(daily.read_json(calendar_path), ensure_ascii=False,
                               sort_keys=True, separators=(",", ":")).encode("utf-8")
        self.assertEqual(self.reference_map["calendarSha256"], hashlib.sha256(canonical).hexdigest())
        psalm_refs = {entry["ref"] for entry in self.english if entry["ref"].startswith("Psalms ")}
        self.assertEqual(49, len(psalm_refs))
        self.assertEqual(psalm_refs, {row["canonicalRef"] for row in self.reference_map["ru"]})
        self.assertEqual(49, len(self.reference_map["ru"]))
        self.assertEqual(12, len(self.reference_map["ja"]))
        suffixes = []
        for language in daily.LANGUAGES:
            bible_id = 83 if language == "ja" else 143
            for row in self.reference_map[language]:
                with self.subTest(language=language, reference=row["nativeRef"]):
                    native = daily.native_entry(language, row["nativeRef"])
                    self.assertEqual(row["localTextSha256"], text_digest(native["text"]))
                    self.assertTrue(row["sourceUrl"].startswith(f"https://www.bible.com/bible/{bible_id}/"))
                    suffix = row.get("versePageOmittedSuffix", "")
                    source_text = daily.normalized_text(native["text"])
                    if suffix:
                        self.assertTrue(source_text.endswith(suffix))
                        source_text = source_text[:-len(suffix)]
                        suffixes.append(suffix)
                    self.assertEqual(row["sourceTextSha256"], text_digest(source_text))
        self.assertCountEqual([" \u2014", ",", ";"], suffixes)

    def test_all_twelve_restored_japanese_passages_keep_combined_ranges(self):
        expected = {
            "Zephaniah 3:17": "Zephaniah 3:17-18",
            "Proverbs 23:24": "Proverbs 23:24-25",
            "Proverbs 3:5-6": "Proverbs 3:4-6",
            "Psalms 56:3": "Psalms 56:3-4",
            "Matthew 5:16": "Matthew 5:15-16",
            "John 1:12": "John 1:11-12",
            "Psalms 23:3": "Psalms 23:2-3",
            "Proverbs 11:25": "Proverbs 11:24-25",
            "James 5:8": "James 5:7-8",
            "Proverbs 1:7": "Proverbs 1:7-9",
            "Proverbs 3:7": "Proverbs 3:7-8",
            "John 1:1": "John 1:1-2",
        }
        self.assertEqual(expected, {row["canonicalRef"]: row["nativeRef"] for row in self.reference_map["ja"]})

    def test_russian_psalms_account_for_title_verses_and_native_chapters(self):
        rows = {row["canonicalRef"]: row["nativeRef"] for row in self.reference_map["ru"]}
        for canonical, native in {
            "Psalms 27:4": "Psalms 26:4",
            "Psalms 8:3-4": "Psalms 8:4-5",
            "Psalms 3:3": "Psalms 3:4",
            "Psalms 51:10": "Psalms 50:12",
            "Psalms 133:1": "Psalms 132:1",
            "Psalms 119:105": "Psalms 118:105",
        }.items():
            self.assertEqual(native, rows[canonical])
        with self.assertRaisesRegex(ValueError, "Unreviewed Russian Psalm"):
            daily.calendar_reference("ru", "Psalms 150:1", self.reference_map)

    def test_optional_numbering_source_reproduction(self):
        table = Path(os.environ.get("BIBLE_TRADITIONAL_TABLE_ROOT", r"C:\Users\Dominic\AppData\Local\Temp"))
        path = table / "CrossWire-Synodal-a2c51f3.properties"
        if not path.exists():
            self.skipTest("Pinned CrossWire source not present")
        source = path.read_bytes()
        self.assertEqual(self.reference_map["provenance"]["numberingSourceSha256"], hashlib.sha256(source).hexdigest())
        lines = source.decode("utf-8").splitlines()
        for row in self.reference_map["ru"]:
            rule = row["numberingRule"]
            self.assertIn(rule, lines)
            left, right = rule.split("=")
            native = [tuple(map(int, ref)) for ref in re.findall(r"Ps\.(\d+)\.(\d+)", left)]
            canonical = [tuple(map(int, ref)) for ref in re.findall(r"Ps\.(\d+)\.(\d+)", right)]
            _, chapter, verse, end = daily.parse_reference(row["canonicalRef"])
            self.assertEqual(chapter, canonical[0][0])
            self.assertTrue(canonical[0][1] <= verse <= canonical[-1][1])
            self.assertTrue(verse <= end <= canonical[-1][1])
            mapped_verse = native[0][1] + (verse - canonical[0][1] if len(native) == 2 else 0)
            mapped_end = mapped_verse + (end - verse)
            tail = f"{native[0][0]}:{mapped_verse}" + (f"-{mapped_end}" if end > verse else "")
            self.assertEqual(row["nativeRef"], f"Psalms {tail}")

    def test_repaired_2026_passages_cover_every_native_verse(self):
        # Compared with Bible.com's public day pages on 2026-11-19.
        passages = {
            1: "Isaiah 43:18-19", 26: "Psalms 8:3-4", 32: "Luke 1:46-47",
            39: "James 1:2-3", 41: "Romans 5:3-4", 67: "Proverbs 31:25-26",
            76: "Proverbs 3:5-6", 115: "Romans 3:23-24", 124: "Matthew 9:37-38",
            139: "Ephesians 2:8-9", 212: "Ephesians 3:20-21",
            247: "Galatians 5:22-23", 258: "Lamentations 3:22-23",
            304: "2 Timothy 3:16-17", 323: "1 Thessalonians 5:16-18",
            357: "Luke 2:9-10",
        }
        for language in sorted(path.parent.name for path in (daily.ASSETS / "daily_verses").glob("*/daily.json")):
            entries = daily.read_json(daily.ASSETS / "daily_verses" / language / "daily.json")["verses"]
            for day, reference in passages.items():
                with self.subTest(language=language, day=day):
                    native_ref = "Psalms 8:4-5" if language == "ru" and day == 26 else reference
                    expected = daily.native_entry(language, native_ref)
                    actual = entries[day - 1]
                    self.assertEqual(expected["ref"].rsplit(" ", 1)[1], actual["ref"].rsplit(" ", 1)[1])
                    self.assertEqual(expected["text"], actual["text"])
            if language == "sw":
                self.assertEqual("Wakolosai 1:13", entries[301]["ref"])
                self.assertEqual("Yohana 14:27", entries[318]["ref"])
            else:
                self.assertEqual("Colossians 1:13", entries[301]["ref"])
                self.assertEqual("John 14:27", entries[318]["ref"])

    def test_calendar_never_wraps_early_in_leap_or_regular_years(self):
        for year in (2024, 2025, 2026, 2028):
            count = 366 if calendar.isleap(year) else 365
            for language in daily.LANGUAGES:
                entries = daily.read_json(daily.ASSETS / "daily_verses" / language / "daily.json")["verses"]
                for offset in range(count):
                    current = date(year, 1, 1) + timedelta(days=offset)
                    index = (current.timetuple().tm_yday - 1) % len(entries)
                    self.assertEqual(offset, index)
                    self.assertEqual(entries[offset], entries[index])
                self.assertEqual(count - 1, (date(year, 12, 31).timetuple().tm_yday - 1) % len(entries))

    def test_native_lookup_uses_trailing_reference_and_preserves_whole_units(self):
        book = {"stories": [{"id": "proverbs-1", "summaryBullets": [
            "[J]Native text with cross-reference 7:15.[/J] (1:7-9)."
        ]}]}
        with patch.object(daily, "book_document", return_value=book):
            entry = daily.native_entry("ja", "Proverbs 1:8")
            self.assertEqual("Proverbs 1:7-9", entry["ref"])
            self.assertEqual("Native text with cross-reference 7:15.", entry["text"])
            with self.assertRaisesRegex(ValueError, "Missing native verses"):
                daily.native_entry("ja", "Proverbs 1:10")
        for invalid in ("John 1:0", "John 1:5-3", "John 1:1; Luke 2:1"):
            with self.assertRaises(ValueError):
                daily.parse_reference(invalid)


if __name__ == "__main__":
    unittest.main()
