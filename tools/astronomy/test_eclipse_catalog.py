from __future__ import annotations
from datetime import datetime, timedelta, timezone
import unittest
from audit_eclipse_catalog import (
    LANGUAGES,
    NOTES,
    REQUIRED_SOURCES,
    audit,
    audit_source_locations,
    audit_visible_table,
    bibliography_source_section,
    expected_events,
    source_document,
)
from build_eclipse_catalog import semantic_observances

class EclipseFeastTableTests(unittest.TestCase):
    def english_note(self) -> str:
        return (NOTES / "en/astronomical_signs.md").read_text(encoding="utf-8")

    def english_bibliography(self) -> str:
        return (NOTES / "en/bibliography.md").read_text(encoding="utf-8")

    def outcome(self, event_date: str, location: str) -> dict:
        event = next(r for r in source_document()["events"] if r["date"] == event_date)
        return next(r for r in event["referenceLocations"] if r["locationId"] == location)

    def test_internal_source_is_complete_but_public_table_is_curated(self) -> None:
        source = source_document()
        self.assertEqual(source["eventCount"], {"total": 85, "solar": 42, "lunar": 43})
        self.assertEqual(len(source["events"]), 85)
        self.assertEqual(len(expected_events()), 40)
        self.assertNotIn(("2026-08-28", "lunar", "partial"), expected_events())
        self.assertIn(("2026-08-12", "solar", "total"), expected_events())

    def test_all_languages_keep_ancient_historical_and_selected_modern_context(self) -> None:
        self.assertEqual(audit(), [], f"Expected all {len(LANGUAGES)} curated tables to match")

    def test_seven_sunset_aware_comparisons_per_source_event(self) -> None:
        for event in source_document()["events"]:
            self.assertEqual(len(event["referenceLocations"]), 7)
            self.assertTrue(event["provenance"]["decadeCrossCheckMatched"])
            self.assertLessEqual(abs(event["provenance"]["decadeTimeDifferenceSeconds"]), 1)
            for location in event["referenceLocations"]:
                with self.subTest(date=event["date"], location=location["locationId"]):
                    instant = datetime.fromisoformat(location["localDateTime"])
                    greatest = datetime.strptime(event["greatestUtApprox"], "%Y-%m-%dT%H:%M:%S UT").replace(tzinfo=timezone.utc)
                    self.assertEqual(instant.astimezone(timezone.utc), greatest)
                    sunset = datetime.fromisoformat(location["sunsetLocal"])
                    after_sunset = instant >= sunset
                    self.assertEqual(location["afterSunset"], after_sunset)
                    effective = instant.date() + timedelta(days=int(after_sunset))
                    self.assertEqual(location["effectiveCivilDate"], effective.isoformat())
                    self.assertEqual(location["sunsetMarginSeconds"], round((instant - sunset).total_seconds()))
                    self.assertEqual(instant.strftime("%z"), location["utcOffset"].replace(":", ""))

    def test_october_2024_trumpets_in_israel_its_eve_in_chile(self) -> None:
        israel = self.outcome("2024-10-02", "jerusalem")
        chile = self.outcome("2024-10-02", "santiago")
        self.assertEqual((israel["hebrewDate"]["month"], israel["hebrewDate"]["day"]), ("Tishrei", 1))
        self.assertEqual((chile["hebrewDate"]["month"], chile["hebrewDate"]["day"]), ("Elul", 29))
        self.assertTrue(israel["afterSunset"])
        self.assertFalse(chile["afterSunset"])

    def test_august_2026_not_dismissed_by_american_date(self) -> None:
        israel = self.outcome("2026-08-12", "jerusalem")
        america = self.outcome("2026-08-12", "new_york")
        self.assertEqual(israel["hebrewDate"]["day"], 30)
        self.assertEqual(america["hebrewDate"]["day"], 29)
        self.assertIn("rosh-chodesh-elul", [r["id"] for r in israel["semanticObservances"]])

    def test_pacific_extremes_capture_otherwise_missed_alignments(self) -> None:
        for date, location, observance in (
            ("2020-06-21", "auckland", "rosh-chodesh-tamuz"),
            ("2022-05-16", "honolulu", "pesach-sheni"),
            ("2030-06-01", "auckland", "rosh-chodesh-sivan"),
        ):
            self.assertIn(date, [r[0] for r in expected_events()])
            self.assertIn(observance, [r["id"] for r in self.outcome(date, location)["semanticObservances"]])

    def test_hanukkah_candle_count_is_not_current_day(self) -> None:
        records = [{"title": "Chanukah: 5 Candles", "category": "holiday", "subcat": "minor", "yomTov": False}]
        self.assertEqual(semantic_observances(records, "Kislev", 28)[0]["day"], 4)
        for location in ("jerusalem", "new_york", "santiago", "tokyo", "sydney", "auckland", "honolulu"):
            day = next(r["day"] for r in self.outcome("2019-12-26", location)["semanticObservances"] if r["id"] == "chanukah")
            self.assertEqual(day, 4)

    def test_missing_visible_event_fails(self) -> None:
        text = "\n".join(line for line in self.english_note().splitlines() if not line.startswith("| 2026-08-12 |"))
        self.assertIn("modern rows must contain all 40 selected feast alignments exactly once", audit_visible_table(text))

    def test_duplicate_row_fails(self) -> None:
        text = self.english_note()
        row = next(line for line in text.splitlines() if line.startswith("| 2026-08-12 |"))
        self.assertIn("modern rows must contain all 40 selected feast alignments exactly once", audit_visible_table(text + "\n" + row))

    def test_wrong_visible_type_fails(self) -> None:
        text = self.english_note().replace("| 2026-08-12 | Total solar eclipse", "| 2026-08-12 | Partial solar eclipse")
        self.assertIn("2026-08-12: visible type does not match NASA event", audit_visible_table(text))

    def test_consistently_wrong_localized_type_fails(self) -> None:
        text = (NOTES / "es/astronomical_signs.md").read_text(encoding="utf-8")
        text = text.replace("Eclipse solar anular", "Eclipse solar total")
        self.assertIn("2017-02-26: visible type does not match NASA event", audit_visible_table(text, "es"))

    def test_non_feast_event_cannot_reappear(self) -> None:
        text = self.english_note() + "\n| 2026-08-28 | Partial lunar eclipse | No selected alignment | 15 Elul 5786 |"
        self.assertIn("modern rows must contain all 40 selected feast alignments exactly once", audit_visible_table(text))

    def test_missing_bibliography_source_fails(self) -> None:
        source = REQUIRED_SOURCES[0]
        bibliography = self.english_bibliography()
        section, heading_count = bibliography_source_section(self.english_note(), bibliography)
        self.assertEqual(heading_count, 1)
        self.assertIsNotNone(section)
        bibliography = bibliography.replace(section, section.replace(source, "", 1), 1)
        failures = audit_source_locations(self.english_note(), bibliography)
        self.assertIn(f"bibliography astronomy subsection is missing source {source}", failures)

    def test_source_left_in_astronomical_note_fails(self) -> None:
        source = REQUIRED_SOURCES[0]
        astronomy = f"{self.english_note().rstrip()}\n\n{source}\n"
        failures = audit_source_locations(astronomy, self.english_bibliography())
        self.assertIn(f"source remains misplaced in astronomical_signs.md: {source}", failures)

    def test_ancient_eclipse_does_not_replace_daytime_darkness(self) -> None:
        text = self.english_note()
        self.assertIn("| March 13, 4 BC | Partial lunar eclipse |", text)
        self.assertIn("| January 10, 1 BC | Total lunar (blood moon) |", text)
        self.assertIn("| April 3, AD 33 | Partial lunar eclipse |", text)
        self.assertIn("A lunar eclipse cannot explain the daytime darkness in Matthew 27:45.", text)
        self.assertNotIn("| April 7, AD 30 |", text)

if __name__ == "__main__":
    unittest.main()
