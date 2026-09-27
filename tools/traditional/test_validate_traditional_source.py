"""Word-level regression tests for optional pinned-source verification."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import import_traditional_editions as importer
import import_synodal_deuterocanon as synodal
import validate_traditional_editions as validator


class SourceEqualityTests(unittest.TestCase):
    def test_changed_word_fails_even_when_all_counts_match(self):
        expected = [{"number": 1, "verses": [{"chapter": 1, "verse": 1, "text": "Verified word."}]}]
        changed = [{"number": 1, "verses": [{"chapter": 1, "verse": 1, "text": "Modified word."}]}]
        with self.assertRaisesRegex(validator.ValidationError, "Source text/markup differs: xx/example 1:1"):
            validator.compare_native_chapters(expected, changed, "xx/example")

    def test_tampered_pinned_source_fails_before_parser_runs(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "source.zip"
            path.write_bytes(b"approved bytes")
            pinned = hashlib.sha256(path.read_bytes()).hexdigest().upper()
            validator.verify_pin(path, pinned)
            path.write_bytes(b"tampered bytes")
            with self.assertRaisesRegex(validator.ValidationError, "Pinned source SHA-256 mismatch"):
                validator.verify_pin(path, pinned)

    def test_deterministic_inherited_jesus_markup_is_accepted(self):
        chapter = importer.SourceChapter(1, verses=[
            importer.SourceVerse(1, 1, 1, parts=["Spoken words."])
        ])
        expected, inherited, omitted = importer.chapter_to_json(
            chapter, {1: [(1, 1)]}, {}, allow_inherited_jesus=True,
        )
        self.assertEqual((inherited, omitted), (1, 0))
        self.assertEqual(expected["verses"][0]["text"], "[J]Spoken words.[/J]")
        validator.compare_native_chapters([expected], [json.loads(json.dumps(expected))], "xx/example")

    def test_native_merged_range_is_compared_without_splitting(self):
        chapter = importer.SourceChapter(1, verses=[
            importer.SourceVerse(1, 1, 2, parts=["One joined unit."])
        ])
        expected = importer.chapter_to_json(chapter, {}, {}, allow_inherited_jesus=False)[0]
        self.assertEqual(expected["verses"][0]["verseEnd"], 2)
        validator.compare_native_chapters([expected], [json.loads(json.dumps(expected))], "xx/example")
        modified = json.loads(json.dumps(expected))
        modified["verses"][0]["verseEnd"] = 1
        with self.assertRaisesRegex(validator.ValidationError, "Source native coordinate differs"):
            validator.compare_native_chapters([expected], [modified], "xx/example")

    def test_canonical_verification_does_not_trust_manifest_for_text(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source_root = root / "sources"
            source_root.mkdir()
            archive = source_root / "source.zip"
            archive.write_bytes(b"pinned source")
            pinned = hashlib.sha256(archive.read_bytes()).hexdigest().upper()
            # Source verification derives the edition's complete heading table
            # from the base reader asset. Keep this fixture self-contained so
            # the only mismatch under test is the altered Scripture word.
            base_story = {"stories": [{
                "id": "example-1",
                "summaryBullets": ["Pinned word. (1:1)."],
                "headings": [{"beforeVerse": 1, "text": "Beginning"}],
            }]}
            for language in ("xx", "en"):
                base_path = root / "shared/assets/books/old_testament" / language / "example.json"
                base_path.parent.mkdir(parents=True)
                base_path.write_text(json.dumps(base_story), encoding="utf-8")
            edition_dir = root / "shared/assets/books/editions/xx/old"
            path = edition_dir / "old_testament/example.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"chapters": [
                {"number": 1,
                 "headings": [{"beforeVerse": 1, "text": "Beginning"}],
                 "verses": [{"chapter": 1, "verse": 1, "text": "Altered word."}]}
            ]}), encoding="utf-8")
            native = importer.SourceBook("GEN", "source.zip", pinned, [
                importer.SourceChapter(1, verses=[
                    importer.SourceVerse(1, 1, 1, parts=["Pinned word."])
                ])
            ])
            config = SimpleNamespace(
                archive_name="source.zip", archive_sha256=pinned,
                auxiliary_sha256=None, source_format="usfm", language="xx",
                edition_id="old",
            )
            manifest = {
                "source": {"extractedScriptureFileSetSha256": "FILESET"},
                "books": [{"collection": "old_testament", "bookId": "example",
                           "sourceFile": "source.zip", "sourceFileSha256": pinned}],
            }
            with patch.object(validator, "BOOKS", (("GEN", "old_testament", "example"),)), \
                 patch.object(importer, "parse_usfm_archive", return_value=({"GEN": native}, "FILESET")), \
                 patch.object(importer, "apply_display_versification", return_value=(native, None)), \
                 patch.object(importer, "base_verse_units", return_value=[(1, 1, 1)]), \
                 patch.object(importer, "base_jesus_ranges", return_value=({}, {})):
                with self.assertRaisesRegex(validator.ValidationError, "Source text/markup differs"):
                    validator.verify_canonical_source(root, config, source_root, None, edition_dir, manifest)


class SynodalDcSourceTests(unittest.TestCase):
    def test_dc_source_chapter_and_verse_text_derive_from_original_chapter(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "shared/assets/books/deuterocanonical/ru/susanna.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [
                {"id": "susanna-1", "summaryBullets": ["First (1).", "Second (2)."]}
            ]}), encoding="utf-8")
            source_book = SimpleNamespace(name="Daniel", osis_name="Dan", chapter_lengths=[0] * 12 + [2])
            spec = synodal.BookSource("Daniel", (13,), (1,))

            class FakeBible:
                def get(self, *, books, chapters, verses, clean):
                    self_query = (books, chapters, verses)
                    if self_query not in {("Daniel", 13, 1), ("Daniel", 13, 2)}:
                        raise AssertionError(self_query)
                    return "First source." if verses == 1 else "Second source."

            expected, source_chapters = validator.expected_synodal_dc_chapters(
                FakeBible(), source_book, spec, root, "susanna",
            )
            self.assertEqual(source_chapters, [13])
            self.assertEqual([row["text"] for row in expected[0]["verses"]], ["First source.", "Second source."])
            tampered = json.loads(json.dumps(expected))
            tampered[0]["verses"][1]["text"] = "Second altered."
            with self.assertRaisesRegex(validator.ValidationError, "Source text/markup differs: ru/susanna 1:2"):
                validator.compare_native_chapters(expected, tampered, "ru/susanna")


if __name__ == "__main__":
    unittest.main()
