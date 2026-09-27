"""Regression tests for source integrity and traditional-edition markup."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import import_traditional_editions as importer


class SourceIntegrityTests(unittest.TestCase):
    def test_archive_bytes_override_any_stale_extracted_copy(self):
        raw = b"\\id GEN\n\\c 1\n\\v 1 Verified source.\n"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "source.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr("01GEN.usfm", raw)
            (root / "source").mkdir()
            (root / "source" / "01GEN.usfm").write_text(
                "\\id GEN\n\\c 1\n\\v 1 Stale extracted text.\n", encoding="utf-8"
            )
            with patch.object(importer, "BOOKS", (("GEN", "old_testament", "genesis"),)):
                books, digest = importer.parse_usfm_archive(archive)
        self.assertEqual(books["GEN"].chapters[0].verses[0].text, "Verified source.")
        expected_hash = hashlib.sha256(raw).hexdigest().upper()
        self.assertEqual(books["GEN"].source_sha256, expected_hash)
        self.assertEqual(
            digest, hashlib.sha256(b"GEN\0" + bytes.fromhex(expected_hash)).hexdigest().upper()
        )

    def test_duplicate_book_codes_fail_even_with_different_filenames(self):
        with tempfile.TemporaryDirectory() as temporary:
            archive = Path(temporary) / "duplicate.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                for name in ("first.usfm", "second.usfm"):
                    zipped.writestr(name, "\\id GEN\n\\c 1\n\\v 1 Text.\n")
            with self.assertRaisesRegex(importer.ImportErrorDetail, "Duplicate USFM book code GEN"):
                importer.parse_usfm_archive(archive)

    def test_word_level_source_spans_preserve_narration(self):
        book = importer.parse_usfm_bytes(
            b"\\id MAT\n\\c 1\n\\v 1 Narration \\wj spoken words\\wj* narration.\n",
            Path("MAT.usfm"),
        )
        self.assertEqual(
            book.chapters[0].verses[0].text,
            "Narration [J]spoken words[/J] narration.",
        )


class JesusWordTests(unittest.TestCase):
    def test_speech_around_narration_is_mixed_not_full_verse(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "shared/assets/books/editions/en/kjv1769/new_testament/example.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"chapters": [{"number": 1, "verses": [
                {"verse": 1, "text": "[J]Speech.[/J]"},
                {"verse": 2, "text": "[J]First.[/J] Narration. [J]Second.[/J]"},
                {"verse": 3, "text": "Narration. [J]Speech.[/J]"},
                {"verse": 4, "text": "[J]First.[/J] [J]Second.[/J]"},
            ]}]}), encoding="utf-8")
            full, mixed = importer.base_jesus_ranges(root, "new_testament", "example")
        self.assertEqual(full, {1: [(1, 1), (4, 4)]})
        self.assertEqual(mixed, {1: [(2, 2), (3, 3)]})

    def test_source_markup_is_not_extended_from_another_edition(self):
        chapter = importer.SourceChapter(1, verses=[
            importer.SourceVerse(1, 1, 1, parts=["Unmarked by this source."]),
            importer.SourceVerse(1, 2, 2, parts=["Narration [J]speech[/J]."], source_jesus_spans=1),
        ])
        row, inherited, omitted = importer.chapter_to_json(
            chapter, {1: [(1, 2)]}, {}, allow_inherited_jesus=False
        )
        self.assertEqual(row["verses"][0]["text"], "Unmarked by this source.")
        self.assertEqual(row["verses"][1]["text"], "Narration [J]speech[/J].")
        self.assertEqual((inherited, omitted), (0, 0))

    def test_omission_placeholder_does_not_receive_speech_tags(self):
        chapter = importer.SourceChapter(1, verses=[
            importer.SourceVerse(1, 1, 1, parts=["(omitted)"], source_placeholder=True),
        ])
        row, inherited, omitted = importer.chapter_to_json(chapter, {1: [(1, 1)]}, {})
        self.assertEqual(row["verses"][0]["text"], "(omitted)")
        self.assertEqual((inherited, omitted), (0, 0))

    def test_unsplit_range_must_be_wholly_speech_to_inherit_coloring(self):
        chapter = importer.SourceChapter(1, verses=[
            importer.SourceVerse(1, 1, 2, parts=["Speech and narration."]),
        ])
        row, inherited, omitted = importer.chapter_to_json(chapter, {1: [(1, 1)]}, {1: [(2, 2)]})
        self.assertEqual(row["verses"][0]["text"], "Speech and narration.")
        self.assertEqual(row["verses"][0]["verseEnd"], 2)
        self.assertEqual((inherited, omitted), (0, 1))


class HeadingPassageTests(unittest.TestCase):
    def _mapped_fixture(self, headings, rules, target_units):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        repo = Path(temporary.name)
        path = repo / "shared/assets/books/old_testament/de/psalms.json"
        path.parent.mkdir(parents=True)
        stories = []
        for chapter, chapter_headings in headings.items():
            stories.append({
                "id": f"psalms-{chapter}",
                "headings": chapter_headings,
                "summaryBullets": [f"Base ({chapter}:{verse})." for verse in range(1, 5)],
            })
        path.write_text(json.dumps({"stories": stories}), encoding="utf-8")
        config = next(row for row in importer.EDITIONS if row.language == "de")
        source = importer.SourceBook("PSA", "fixture", "fixture", [
            importer.SourceChapter(chapter, verses=[
                importer.SourceVerse(chapter, start, end, parts=["Text."])
                for start, end in units
            ]) for chapter, units in target_units.items()
        ])
        document = {"books": [{"bookId": "psalms", "mappings": rules}]}
        return repo, config, source, document

    def test_german_psalm_title_merge_uses_reviewed_map(self):
        repo = Path(__file__).resolve().parents[2]
        config = next(row for row in importer.EDITIONS if row.language == "de")
        packaged = json.loads((repo / "shared/assets/books/editions/de/luther1912/old_testament/psalms.json").read_text("utf-8"))
        source = importer.SourceBook("PSA", "fixture", "fixture", [
            importer.SourceChapter(chapter["number"], verses=[
                importer.SourceVerse(verse["chapter"], verse["verse"], verse.get("verseEnd", verse["verse"]), parts=["Text."])
                for verse in chapter["verses"]
            ]) for chapter in packaged["chapters"]
        ])
        overrides = importer.heading_overrides(repo, config, "PSA", "old_testament", "psalms", source)
        self.assertEqual(overrides[3][0]["beforeVerse"], 1)
        self.assertEqual(overrides[4][0]["beforeVerse"], 1)

    def test_shifted_intra_chapter_heading_and_unknown_anchor(self):
        repo, config, source, document = self._mapped_fixture(
            {1: [{"beforeVerse": 1, "text": "Unknown"}, {"beforeVerse": 3, "text": "Shifted"}]},
            [{"sourceChapter": 1, "sourceVerse": 2, "sourceVerseEnd": 4,
              "targetChapter": 1, "targetVerse": 1, "targetVerseEnd": 3}],
            {1: [(1, 1), (2, 2), (3, 3)]},
        )
        with patch.object(importer, "reference_map_for", return_value=document):
            overrides = importer.heading_overrides(repo, config, "PSA", "old_testament", "psalms", source)
        self.assertEqual(overrides[1], [
            {"beforeVerse": 1, "text": "Unknown"},
            {"beforeVerse": 2, "text": "Shifted"},
        ])

    def test_cross_chapter_heading_convergence_stacks_source_order(self):
        repo, config, source, document = self._mapped_fixture(
            {1: [{"beforeVerse": 3, "text": "Moved"}], 2: [{"beforeVerse": 1, "text": "Already here"}]},
            [{"sourceChapter": 1, "sourceVerse": 3, "targetChapter": 2, "targetVerse": 1}],
            {1: [(1, 4)], 2: [(1, 1), (2, 4)]},
        )
        with patch.object(importer, "reference_map_for", return_value=document):
            overrides = importer.heading_overrides(repo, config, "PSA", "old_testament", "psalms", source)
        self.assertEqual(overrides[1], [])
        self.assertEqual(overrides[2], [{"beforeVerse": 1, "text": "Moved\nAlready here"}])

    def test_mapped_target_must_exist_in_native_edition(self):
        repo, config, source, document = self._mapped_fixture(
            {1: [{"beforeVerse": 3, "text": "Moved"}]},
            [{"sourceChapter": 1, "sourceVerse": 3, "targetChapter": 2, "targetVerse": 1}],
            {1: [(1, 4)]},
        )
        with patch.object(importer, "reference_map_for", return_value=document):
            with self.assertRaisesRegex(importer.ImportErrorDetail, "absent target verse"):
                importer.heading_overrides(repo, config, "PSA", "old_testament", "psalms", source)

    def test_one_to_many_heading_uses_first_unsplit_target_unit(self):
        repo, config, source, document = self._mapped_fixture(
            {1: [{"beforeVerse": 2, "text": "Opening"}]},
            [{"sourceChapter": 1, "sourceVerse": 2,
              "targetChapter": 1, "targetVerse": 2, "targetVerseEnd": 3}],
            {1: [(1, 2), (3, 3), (4, 4)]},
        )
        with patch.object(importer, "reference_map_for", return_value=document):
            overrides = importer.heading_overrides(repo, config, "PSA", "old_testament", "psalms", source)
        self.assertEqual(overrides[1], [{"beforeVerse": 1, "text": "Opening"}])

    def test_conflicting_reviewed_rows_fail_instead_of_choosing_one(self):
        repo, config, source, document = self._mapped_fixture(
            {1: [{"beforeVerse": 2, "text": "Opening"}]},
            [
                {"sourceChapter": 1, "sourceVerse": 2, "targetChapter": 1, "targetVerse": 2},
                {"sourceChapter": 1, "sourceVerse": 2, "targetChapter": 1, "targetVerse": 3},
            ],
            {1: [(1, 1), (2, 2), (3, 3), (4, 4)]},
        )
        with patch.object(importer, "reference_map_for", return_value=document):
            with self.assertRaisesRegex(importer.ImportErrorDetail, "Conflicting heading passage maps"):
                importer.heading_overrides(repo, config, "PSA", "old_testament", "psalms", source)

    def test_portuguese_revised_headings_do_not_restore_removed_blessing_title(self):
        repo = Path(__file__).resolve().parents[2]
        base = json.loads((repo / "shared/assets/books/new_testament/pt/2_corinthians.json").read_text("utf-8"))
        story = next(row for row in base["stories"] if row["id"] == "2_corinthians-13")
        self.assertEqual([(row["beforeVerse"], row["text"]) for row in story["headings"]], [
            (1, "Conselhos finais de Paulo"),
            (11, "Saudações finais de Paulo"),
        ])
        overlay = json.loads((repo / "shared/assets/books/editions/pt/almeida1911/new_testament/2_corinthians.json").read_text("utf-8"))
        self.assertEqual(overlay["chapters"][12]["headings"], story["headings"])

    def test_bungo_frog_heading_moves_past_water_and_seven_days(self):
        repo = Path(__file__).resolve().parents[2]
        config = next(row for row in importer.EDITIONS if row.language == "ja")
        base = json.loads((repo / "shared/assets/books/old_testament/ja/exodus.json").read_text("utf-8"))
        chapters = []
        for story in base["stories"]:
            number = int(story["id"].rsplit("-", 1)[-1])
            # Only heading placement matters in this fixture. Give each chapter
            # enough verse slots, keeping Bungo's actual 7:24-25 unit intact.
            verses = [importer.SourceVerse(number, v, v, parts=["Text."]) for v in range(1, 52)]
            if number == 7:
                verses = verses[:23] + [importer.SourceVerse(7, 24, 25, parts=["Water; seven days."])]
            chapters.append(importer.SourceChapter(number, verses=verses))
        source = importer.SourceBook("EXO", "fixture", "fixture", chapters)
        overrides = importer.heading_overrides(repo, config, "EXO", "old_testament", "exodus", source)
        frog = "8\n第二の災い\u3000かえる"
        self.assertFalse(any(h["text"] == frog for h in overrides[7]))
        self.assertEqual(next(h["beforeVerse"] for h in overrides[8] if h["text"] == frog), 1)

        explicit = {"books": [{"bookId": "exodus", "mappings": [
            {"sourceChapter": 7, "sourceVerse": 25, "targetChapter": 8, "targetVerse": 1}
        ]}]}
        with patch.object(importer, "reference_map_for", return_value=explicit):
            also_mapped = importer.heading_overrides(repo, config, "EXO", "old_testament", "exodus", source)
        self.assertEqual(sum(h["text"] == frog for rows in also_mapped.values() for h in rows), 1)


if __name__ == "__main__":
    unittest.main()
