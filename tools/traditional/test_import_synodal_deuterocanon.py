"""Focused tests for exact Russian Synodal DC overlay generation."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import import_synodal_deuterocanon as synodal


class FakeBook:
    def __init__(self, name: str, osis_name: str, chapter_lengths: list[int]):
        self.name = name
        self.osis_name = osis_name
        self.chapter_lengths = chapter_lengths


class FakeBible:
    def __init__(self, raw: dict[tuple[str, int, int], str]):
        self.raw = raw

    def get(self, *, books: str, chapters: int, verses: int, clean: bool) -> str:
        value = self.raw[(books, chapters, verses)]
        if not clean:
            return value
        return synodal.XML_TAG.sub("", synodal.TITLE.sub("", value))


def write_book(repo: Path, book_id: str, chapters: list[int], bare: bool = False) -> None:
    stories = []
    for chapter, last_verse in enumerate(chapters, start=1):
        bullets = []
        for verse in range(1, last_verse + 1):
            marker = f"({verse})" if bare else f"({chapter}:{verse})"
            bullets.append(f"Fixture {marker}.")
        stories.append({"id": f"{book_id}-{chapter}", "summaryBullets": bullets})
    path = repo / f"shared/assets/books/deuterocanonical/ru/{book_id}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"stories": stories}), encoding="utf-8")


class SynodalDcTests(unittest.TestCase):
    def fixture(self, root: Path, index: list[str]) -> Path:
        index_path = root / "shared/assets/books/deuterocanonical/ru/_index.json"
        index_path.parent.mkdir(parents=True, exist_ok=True)
        rows = [["", "note"], *[[book_id, book_id] for book_id in index]]
        index_path.write_text(json.dumps(rows), encoding="utf-8")
        archive = root / "RusSynodal.zip"
        archive.write_bytes(b"fixture")
        return archive

    def test_build_writes_exact_overlay_and_explicit_fallback(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.fixture(root, ["tobit", "4_maccabees"])
            write_book(root, "tobit", [2])
            source = FakeBook("Tobit", "Tob", [2])
            bible = FakeBible({
                ("Tobit", 1, 1): "First verse.",
                ("Tobit", 1, 2): "Second verse.",
            })
            digest = hashlib.sha256(b"fixture").hexdigest().upper()
            with (
                patch.object(synodal, "ARCHIVE_SHA256", digest),
                patch.object(synodal, "SUPPORTED_SOURCES", {
                    "tobit": synodal.BookSource("Tobit"),
                }),
                patch.object(synodal, "_open_sword", return_value=(
                    bible, {}, {"Tobit": source},
                )),
            ):
                rows = synodal.build_synodal_dc(archive, root, root / "out", None)

            self.assertEqual([row["bookId"] for row in rows], ["tobit", "4_maccabees"])
            self.assertEqual(rows[0]["coverage"], "full")
            self.assertEqual(rows[0]["coveredVerseNumbers"], 2)
            self.assertEqual(rows[1]["coverage"], "fallback")
            overlay = json.loads((root / "out/tobit.json").read_text("utf-8"))
            self.assertEqual(overlay["chapters"][0]["lastVerse"], 2)
            self.assertEqual(overlay["chapters"][0]["verses"][1]["text"], "Second verse.")
            self.assertEqual(overlay["sourceProof"]["license"], "Public Domain")

    def test_mismatched_verse_slots_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.fixture(root, ["tobit"])
            write_book(root, "tobit", [2])
            source = FakeBook("Tobit", "Tob", [1])
            bible = FakeBible({("Tobit", 1, 1): "Only verse."})
            digest = hashlib.sha256(b"fixture").hexdigest().upper()
            with (
                patch.object(synodal, "ARCHIVE_SHA256", digest),
                patch.object(synodal, "SUPPORTED_SOURCES", {
                    "tobit": synodal.BookSource("Tobit"),
                }),
                patch.object(synodal, "_open_sword", return_value=(
                    bible, {}, {"Tobit": source},
                )),
                self.assertRaisesRegex(synodal.SynodalImportError, "verse slots differ"),
            ):
                synodal.build_synodal_dc(archive, root, root / "out", None)

    def test_psalm_151_keeps_only_proven_canonical_superscription(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.fixture(root, ["psalm_151"])
            write_book(root, "psalm_151", [1])
            source = FakeBook("Psalms", "Ps", [0] * 150 + [1])
            raw = (
                '<title canonical="true" type="psalm">Native title</title>'
                "Verse text."
            )
            bible = FakeBible({("Psalms", 151, 1): raw})
            digest = hashlib.sha256(b"fixture").hexdigest().upper()
            with (
                patch.object(synodal, "ARCHIVE_SHA256", digest),
                patch.object(synodal, "SUPPORTED_SOURCES", {
                    "psalm_151": synodal.BookSource("Psalms", (151,), (1,), True),
                }),
                patch.object(synodal, "_open_sword", return_value=(
                    bible, {}, {"Psalms": source},
                )),
            ):
                synodal.build_synodal_dc(archive, root, root / "out", None)
            overlay = json.loads((root / "out/psalm_151.json").read_text("utf-8"))
            chapter = overlay["chapters"][0]
            self.assertEqual(chapter["superscription"], "Native title")
            self.assertEqual(chapter["verses"][0]["text"], "Verse text.")
            self.assertNotIn("[J]", chapter["verses"][0]["text"])
            self.assertNotIn("[DN]", chapter["verses"][0]["text"])

    def test_unmapped_heading_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = self.fixture(root, ["tobit"])
            write_book(root, "tobit", [1])
            source = FakeBook("Tobit", "Tob", [1])
            bible = FakeBible({
                ("Tobit", 1, 1): '<title type="x-s">Bad anchor</title>Text.',
            })
            digest = hashlib.sha256(b"fixture").hexdigest().upper()
            with (
                patch.object(synodal, "ARCHIVE_SHA256", digest),
                patch.object(synodal, "SUPPORTED_SOURCES", {
                    "tobit": synodal.BookSource("Tobit"),
                }),
                patch.object(synodal, "_open_sword", return_value=(
                    bible, {}, {"Tobit": source},
                )),
                self.assertRaisesRegex(synodal.SynodalImportError, "Unmapped source heading"),
            ):
                synodal.build_synodal_dc(archive, root, root / "out", None)

    def test_metadata_must_remain_public_domain_synodal_1_9_1(self):
        with self.assertRaisesRegex(synodal.SynodalImportError, "versification mismatch"):
            synodal._validate_metadata({synodal.MODULE_KEY: {
                "version": synodal.MODULE_VERSION,
                "versification": "KJV",
                "distributionlicense": synodal.MODULE_LICENSE,
            }})


if __name__ == "__main__":
    unittest.main()
