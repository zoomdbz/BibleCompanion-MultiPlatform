from __future__ import annotations

import unittest

from audit_eclipse_catalog import LANGUAGES, audit, expected_events


class EclipseCatalogTests(unittest.TestCase):
    def test_canonical_range_is_complete_and_typed(self) -> None:
        events = expected_events()
        self.assertEqual(len(events), 45)
        self.assertEqual(sum(body == "solar" for _, body, _ in events), 22)
        self.assertEqual(sum(body == "lunar" for _, body, _ in events), 23)
        self.assertEqual(
            {kind for _, body, kind in events if body == "solar"},
            {"total", "annular", "partial", "hybrid"},
        )
        self.assertEqual(
            {kind for _, body, kind in events if body == "lunar"},
            {"total", "partial", "penumbral"},
        )
        self.assertTrue(all(2024 <= int(date[:4]) <= 2033 for date, _, _ in events))

    def test_every_localized_note_has_the_exact_catalog(self) -> None:
        self.assertEqual(audit(), [], f"Expected all {len(LANGUAGES)} localized catalogs to match")


if __name__ == "__main__":
    unittest.main()
