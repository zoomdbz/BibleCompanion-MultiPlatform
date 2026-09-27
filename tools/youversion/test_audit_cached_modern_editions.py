from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

import audit_cached_modern_editions as audit
import audit_localized_scripture as source


class CachedModernAuditTests(unittest.TestCase):
    def test_cache_digest_covers_paths_and_bytes_deterministically(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "a/1.json"
            second = root / "b/1.json"
            first.parent.mkdir()
            second.parent.mkdir()
            first.write_bytes(b"one")
            second.write_bytes(b"two")
            before = audit.cache_digest(root, [second, first])
            self.assertEqual(before, audit.cache_digest(root, [first, second]))
            second.write_bytes(b"changed")
            self.assertNotEqual(before, audit.cache_digest(root, [first, second]))

    def test_findings_count_ranges_not_only_affected_chapters(self):
        findings = [
            {"type": "exact-text-mismatch", "ranges": [{"range": "1"}, {"range": "2"}]},
            {"type": "missing-local-verses", "verses": [4, 5]},
            {"type": "verse-grouping-mismatch"},
        ]
        self.assertEqual(audit.finding_counts(findings), {
            "exact-text-mismatch": 2,
            "missing-local-verses": 2,
            "verse-grouping-mismatch": 1,
        })

    def test_exact_counts_require_the_same_native_range(self):
        local = (source.LocalMarker(1, 2, "Joined text", False, False),)
        joined_hash = source.sha256_text("Joined text")
        same = source.ChapterStructure(
            "example", 1,
            (source.Marker(1, 2, False, False, False, joined_hash, 11),),
            (), (), "https://example.test", "PAGE",
        )
        self.assertEqual(audit.exact_counts(local, same)["exactTextHashMatches"], 1)
        split = source.ChapterStructure(
            "example", 1,
            (source.Marker(1, 1, False, False, False, joined_hash, 11),),
            (), (), "https://example.test", "PAGE",
        )
        self.assertEqual(audit.exact_counts(local, split)["sourceRangesWithoutMatchingLocalRange"], 1)

    def test_unknown_language_fails_closed(self):
        with self.assertRaises(audit.OfflineAuditError):
            audit.parse_languages("de,fr")


if __name__ == "__main__":
    unittest.main()
