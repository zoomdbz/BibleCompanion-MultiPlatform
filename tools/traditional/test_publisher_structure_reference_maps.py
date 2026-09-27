#!/usr/bin/env python3
"""Check packaged map integrity; reproduce source evidence when available."""

from __future__ import annotations

import json
import os
from pathlib import Path
import unittest

from build_publisher_structure_maps import CACHE, OUT, PAIRS, ROOT, cache_digest, generate
from reference_maps import reference_map_for_edition


EXPECTED_CACHE_HASHES = {
    "de": "dc97c47d0ff3d50d1d5fd2453f57a9962170068ad75d2d5c916cbcb6abb1b8a1",
    "it": "402d5927149f8148ca07b0df85c5e7a8ec5006a538814d9e1ac48e73a8c3952b",
    "ru": "2203b340713936eb2957f62c9463f13f8f1e9b1efedb8ea5ec735295d157f634",
}
EXPECTED_COUNTS = {"de": (61, 861, 24595), "it": (65, 1112, 29071), "ru": (65, 998, 27524)}
SOURCE_ROOT = Path(os.environ.get("BIBLE_TRADITIONAL_SOURCE_ROOT", r"C:\Users\Dominic\AppData\Local\Temp\bible-traditional-sources"))
TABLE_ROOT = Path(os.environ.get("BIBLE_TRADITIONAL_TABLE_ROOT", r"C:\Users\Dominic\AppData\Local\Temp"))


class PublisherStructureReferenceMapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.document = json.loads(OUT.read_text(encoding="utf-8"))

    def test_schema_and_provenance(self) -> None:
        self.assertEqual(self.document["schemaVersion"], 1)
        self.assertEqual({m["language"] for m in self.document["maps"]}, set(PAIRS))
        for item in self.document["maps"]:
            language = item["language"]
            publisher, base, alternate, _ = PAIRS[language]
            proof = item["provenance"]
            self.assertEqual((item["baseEditionId"], item["editionId"]), (base, alternate))
            self.assertEqual(proof["auditDate"], "2026-09-26")
            self.assertEqual(proof["publisherCacheAuditDate"], "2026-09-25")
            self.assertTrue(proof["publisherCacheRunId"].startswith("20260925-"))
            self.assertEqual(proof["publisherEdition"], publisher)
            self.assertEqual(proof["publisherCacheAggregateSha256"], EXPECTED_CACHE_HASHES[language])
            self.assertEqual(len(proof["alternateSourceArchiveSha256"]), 64)
            self.assertIn("biblegateway.com", proof["publisherDetailsUrl"])
            self.assertEqual((len(item["books"]), proof["mappedChapters"], proof["mappedNativeUnits"]), EXPECTED_COUNTS[language])

    def test_only_whole_identity_rows_and_no_duplicate_books(self) -> None:
        for item in self.document["maps"]:
            names = [book["bookId"] for book in item["books"]]
            self.assertEqual(len(names), len(set(names)))
            for book in item["books"]:
                self.assertFalse(book["complete"])
                self.assertTrue(book["mappings"])
                prior = (0, 0)
                for row in book["mappings"]:
                    self.assertEqual(row["sourceChapter"], row["targetChapter"])
                    self.assertEqual(row["sourceVerse"], row["targetVerse"])
                    self.assertEqual(row.get("sourceVerseEnd"), row.get("targetVerseEnd"))
                    self.assertGreater((row["sourceChapter"], row["sourceVerse"]), prior)
                    prior = (row["sourceChapter"], row.get("sourceVerseEnd", row["sourceVerse"]))

    def test_packaged_maps_and_manifest_attribution(self) -> None:
        for language, (_publisher, _base, alternate, _table) in PAIRS.items():
            expected = reference_map_for_edition(ROOT, language, alternate)
            directory = ROOT / "shared/assets/books/editions" / language / alternate
            self.assertEqual(json.loads((directory / "_reference_map.json").read_text(encoding="utf-8")), expected)
            manifest = json.loads((directory / "_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest["referenceMap"], {
                "path": "_reference_map.json",
                "books": [book["bookId"] for book in expected["books"]],
                "provenance": expected["provenance"],
            })

    def test_optional_pinned_source_reproduction(self) -> None:
        required = [SOURCE_ROOT / name for name in ("deu1912.zip", "ita1885.zip", "russyn.zip")]
        required += [TABLE_ROOT / name for name in ("CrossWire-Luther-a2c51f3.properties", "CrossWire-Synodal-a2c51f3.properties")]
        required += [CACHE / publisher for publisher, *_ in PAIRS.values()]
        if not all(path.exists() for path in required):
            self.skipTest("Pinned publisher caches or traditional-source archives are not available")
        for language, (publisher, *_rest) in PAIRS.items():
            self.assertEqual(cache_digest(CACHE / publisher), EXPECTED_CACHE_HASHES[language])
        self.assertEqual(generate(SOURCE_ROOT, TABLE_ROOT), self.document)


if __name__ == "__main__":
    unittest.main()
