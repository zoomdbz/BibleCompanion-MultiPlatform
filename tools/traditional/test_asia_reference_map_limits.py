"""Regression guard for the deliberate Asia reference-map evidence boundary."""

from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from audit_reference_coverage import ASSETS_ROOT, audit, base_units, overlay_units
from reference_maps import reference_map_for_edition


ROOT = Path(__file__).resolve().parents[2]
LIMITS = json.loads(Path(__file__).with_name("asia_reference_map_limits.json").read_text(encoding="utf-8"))
ASIA_MAP = json.loads(Path(__file__).with_name("asia_reference_maps.json").read_text(encoding="utf-8"))
COLLECTIONS = ("old_testament", "new_testament")


def local_totals(language: str, edition: str) -> tuple[Counter[str], int, int]:
    totals: Counter[str] = Counter()
    equal_books = different_books = 0
    for collection in COLLECTIONS:
        for base_path in sorted((ASSETS_ROOT / collection / language).glob("*.json")):
            if base_path.name.startswith("_"):
                continue
            source_units, source_index = base_units(base_path)
            target_units, target_index = overlay_units(
                ASSETS_ROOT / "editions" / language / edition / collection / base_path.name,
                language, edition, collection, base_path.stem,
            )
            totals["baseUnits"] += len(source_units)
            totals["baseCoordinates"] += len(source_index)
            totals["targetUnits"] += len(target_units)
            totals["targetCoordinates"] += len(target_index)
            if set(source_index) == set(target_index) and set(source_units) == set(target_units):
                equal_books += 1
            else:
                different_books += 1
    return totals, equal_books, different_books


class AsiaReferenceMapLimitsTests(unittest.TestCase):
    def test_simplified_chinese_rows_are_explicit_cache_pinned_and_fail_closed(self):
        self.assertEqual(1, ASIA_MAP["schemaVersion"])
        self.assertEqual(1, len(ASIA_MAP["maps"]))
        mapping = ASIA_MAP["maps"][0]
        provenance = mapping["provenance"]
        self.assertEqual("zh-Hans", mapping["language"])
        self.assertEqual(66, len(mapping["books"]))
        self.assertEqual("209d32819e35c22dbdfd15d6f26a98a46499fe6105310f7edd644d357f014de9", provenance["ccbCacheAggregateSha256"])
        self.assertEqual("2026-09-26", provenance["ccbCacheAuditDate"])
        self.assertEqual("Chinese Contemporary Bible (Simplified) (CCB)", provenance["ccbPublisherVersionLabel"])
        self.assertEqual("https://www.biblegateway.com/versions/Chinese-Contemporary-Bible-CCB/", provenance["ccbPublisherDetailsUrl"])
        self.assertEqual("https://ebible.org/Scriptures/cmn-cu89s_usfm.zip", provenance["sourceUrl"])
        self.assertTrue(all(book["complete"] is False for book in mapping["books"]))
        exceptional = 0
        for book in mapping["books"]:
            for row in book["mappings"]:
                source = (row["sourceChapter"], row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]))
                target = (row["targetChapter"], row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]))
                if source != target:
                    exceptional += 1
                    self.assertEqual(("john", (7, 53, 53), (8, 1, 1)), (book["bookId"], source, target))
        self.assertEqual(1, exceptional)

    def test_local_inventory_evidence_is_pinned_in_the_limit_record(self):
        self.assertEqual(1, LIMITS["schemaVersion"])
        for pair in LIMITS["pairs"]:
            with self.subTest(language=pair["language"]):
                totals, equal_books, different_books = local_totals(pair["language"], pair["editionId"])
                self.assertEqual(dict(totals), {key: pair[key] for key in totals})
                self.assertEqual(equal_books, pair["identicalInventoryBooks"])
                self.assertEqual(different_books, pair["differentInventoryBooks"])
                self.assertTrue(pair["missingEvidence"])

    def test_japanese_inventory_expansion_matches_reviewed_native_unit_repairs(self):
        pair = next(item for item in LIMITS["pairs"] if item["language"] == "ja")
        provenance = pair["baseInventoryProvenance"]
        ledger_path = ROOT / provenance["reviewLedger"]
        ledger = json.loads(ledger_path.read_text(encoding="utf-8"))
        self.assertEqual("ja", ledger["language"])
        self.assertEqual(83, ledger["sourceEdition"]["bibleComVersionId"])
        self.assertEqual("Bible.com JCB edition 83", provenance["authority"])

        repairs = [
            (row["asset"], unit)
            for row in ledger["reviewedRepairs"]
            for unit in row["nativeUnits"]
        ]
        self.assertEqual(provenance["reviewedNativeUnitExpansions"], len(repairs))
        coordinate_increase = 0
        for asset, repair in repairs:
            old, new = repair["old"], repair["new"]
            self.assertEqual((old["chapter"], old["start"]), (new["chapter"], new["start"]))
            self.assertGreater(new["end"], old["end"])
            units, _ = base_units(ASSETS_ROOT / asset)
            self.assertIn(
                (new["chapter"], new["start"], new["end"]),
                {(unit.chapter, unit.start, unit.end) for unit in units},
            )
            coordinate_increase += new["end"] - old["end"]
        self.assertEqual(provenance["coordinateIncrease"], coordinate_increase)
        self.assertEqual(
            pair["baseCoordinates"],
            provenance["previousBaseCoordinates"] + coordinate_increase,
        )

    def test_equal_inventory_never_becomes_an_unreviewed_identity_map(self):
        for pair in LIMITS["pairs"]:
            with self.subTest(language=pair["language"]):
                # This ledger describes the older source-specific Asia maps.
                # Direct, chapter-bound concordance extensions have their own
                # source-evidence and structure tests.
                packaged = reference_map_for_edition(
                    ROOT, pair["language"], pair["editionId"], include_concordant=False
                )
                if pair["explicitRules"] == 0:
                    if pair["language"] == "ko":
                        # A separately reviewed Matthew 20 speech-boundary
                        # bridge is not an Asia inventory identity inference.
                        self.assertEqual(["matthew"], [book["bookId"] for book in packaged["books"]])
                        self.assertEqual(2, len(packaged["books"][0]["mappings"]))
                    else:
                        self.assertIsNone(packaged)
                    continue
                self.assertIsNotNone(packaged)
                self.assertEqual(pair["explicitRules"], sum(len(book["mappings"]) for book in packaged["books"]))
                john = next(book for book in packaged["books"] if book["bookId"] == "john")
                self.assertEqual(
                    (7, 53, 8, 1),
                    next((row["sourceChapter"], row["sourceVerse"], row["targetChapter"], row["targetVerse"])
                         for row in john["mappings"] if row["sourceChapter"] == 7 and row["sourceVerse"] == 53),
                )
                self.assertFalse(any(book.get("complete") for book in packaged["books"]))
                if pair["language"] == "zh-Hans":
                    self.assertEqual(66, len(packaged["books"]))
                else:
                    self.assertEqual(1, len(packaged["books"]))

    def test_simplified_chinese_coverage_only_improves_on_explicit_ledger(self):
        pair = next(item for item in LIMITS["pairs"] if item["language"] == "zh-Hans")
        coverage = audit()["languages"]["zh-Hans"]
        for direction, prefix in (("baseToAlternate", "baseToAlternate"), ("alternateToBase", "alternateToBase")):
            self.assertGreaterEqual(coverage[direction]["reviewedResolvable"], pair[f"{prefix}ReviewedUnits"])
            self.assertLessEqual(coverage[direction]["retainSourceEdition"], pair[f"{prefix}RetainedUnits"])


if __name__ == "__main__":
    unittest.main()
