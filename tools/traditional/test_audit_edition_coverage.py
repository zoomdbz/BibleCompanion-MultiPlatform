#!/usr/bin/env python3
"""Regression tests for the offline traditional-edition coverage audit."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from audit_edition_coverage import (
    POLICY_PATH,
    ROOT,
    CoverageAuditError,
    audit_all,
    audit_manifest,
    audit_overlay_inventory,
    audit_reference_map_coverage,
    audit_rejected_alternate_metadata,
    expected_coverage,
    load_policy,
)


class EditionCoverageAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.policy = load_policy(POLICY_PATH)
        cls.editions = {
            (row["language"], row.get("editionId")): row
            for row in cls.policy["languages"]
            if row["status"] == "edition"
        }

    def test_current_inventory_matches_policy_offline(self) -> None:
        report = audit_all()
        self.assertFalse(report["networkAccessRequired"])
        self.assertEqual(
            report["totals"],
            {
                "traditionalEditions": 12,
                "absentLanguages": 1,
                "canonicalSourceBackedBooks": 792,
                "deuterocanonicalSourceBackedBooks": 27,
                "deuterocanonicalFallbackBooks": 189,
                "overlayBooks": 819,
                "fallbackBooks": 189,
            },
        )
        hindi = next(row for row in report["languages"] if row["language"] == "hi")
        self.assertEqual(hindi["status"], "absent")

    def test_english_mapped_books_count_as_source_backed(self) -> None:
        statuses = expected_coverage(self.policy, "kjv_with_apocrypha")
        dc = {
            book_id: status
            for (collection, book_id), status in statuses.items()
            if collection == "deuterocanonical"
        }
        self.assertEqual(sum(status in ("full", "mapped") for status in dc.values()), 14)
        self.assertEqual(sum(status == "mapped" for status in dc.values()), 2)
        self.assertEqual(sum(status == "fallback" for status in dc.values()), 4)

    def test_required_dc_profiles_are_bound_to_the_named_editions(self) -> None:
        report = audit_all()
        rows = {
            (row["language"], row.get("editionId")): row
            for row in report["languages"]
            if row.get("status") != "absent"
        }
        self.assertEqual(
            (rows[("en", "kjv1769")]["deuterocanonicalSourceBacked"],
             rows[("en", "kjv1769")]["deuterocanonicalMapped"],
             rows[("en", "kjv1769")]["deuterocanonicalFallback"]),
            (14, 2, 4),
        )
        self.assertEqual(
            (rows[("ru", "synodal1876")]["deuterocanonicalSourceBacked"],
             rows[("ru", "synodal1876")]["deuterocanonicalFallback"]),
            (13, 5),
        )
        for key, row in rows.items():
            if key not in (("en", "kjv1769"), ("ru", "synodal1876")):
                self.assertEqual(
                    (row["deuterocanonicalSourceBacked"], row["deuterocanonicalFallback"]),
                    (0, 18),
                    key,
                )

    def test_mislabeled_manifest_coverage_fails(self) -> None:
        edition = self.editions[("en", "kjv1769")]
        manifest_path = ROOT / edition["manifest"]
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        row = next(item for item in manifest["books"] if item["bookId"] == "baruch")
        row["coverage"] = "fallback"
        with self.assertRaisesRegex(CoverageAuditError, "Coverage mislabeled"):
            audit_manifest(ROOT, self.policy, edition, manifest, manifest_path.parent)

    def test_source_pin_change_fails(self) -> None:
        edition = self.editions[("en", "kjv1769")]
        manifest_path = ROOT / edition["manifest"]
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["source"]["archiveSha256"] = "A" * 64
        with self.assertRaisesRegex(CoverageAuditError, "Pinned source field differs"):
            audit_manifest(ROOT, self.policy, edition, manifest, manifest_path.parent)

    def test_unexpected_overlay_file_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            edition_dir = Path(temporary)
            collection = edition_dir / "old_testament"
            collection.mkdir()
            (collection / "genesis.json").write_text("{}", encoding="utf-8")
            (collection / "unexpected.json").write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(CoverageAuditError, "Overlay inventory differs"):
                audit_overlay_inventory({"old_testament/genesis.json"}, edition_dir, "synthetic")

    def test_fallback_cannot_claim_source_metadata(self) -> None:
        edition = self.editions[("en", "kjv1769")]
        manifest_path = ROOT / edition["manifest"]
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        row = next(item for item in manifest["books"] if item["bookId"] == "psalm_151")
        row["sourceProof"] = {"module": "not-a-real-source"}
        with self.assertRaisesRegex(CoverageAuditError, "Fallback unexpectedly declares source metadata"):
            audit_manifest(ROOT, self.policy, edition, manifest, manifest_path.parent)

    def test_reference_map_cannot_target_fallback_book(self) -> None:
        statuses = expected_coverage(self.policy, "kjv_with_apocrypha")
        with tempfile.TemporaryDirectory() as temporary:
            edition_dir = Path(temporary)
            (edition_dir / "_reference_map.json").write_text(
                json.dumps({"books": [{"bookId": "psalm_151", "mappings": []}]}),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(CoverageAuditError, "Reference map targets a fallback book"):
                audit_reference_map_coverage(edition_dir, statuses, "synthetic")

    def test_reviewed_alternates_can_never_be_drop_in_sources(self) -> None:
        sources = self.policy["reviewedAlternateSources"]["sources"]
        self.assertTrue(sources)
        self.assertTrue(all(row["distinctEditionIdentity"] is True for row in sources))
        self.assertTrue(all(row["approvedAsDropIn"] is False for row in sources))
        with self.assertRaisesRegex(CoverageAuditError, "SpaPlatense"):
            audit_rejected_alternate_metadata(
                self.policy,
                [("synthetic", {"url": sources[0]["url"]})],
            )


if __name__ == "__main__":
    unittest.main()
