"""Offline tests for the read-only public-browser parity comparator."""

from pathlib import Path
import io
from http.client import HTTPConnection
import json
import tempfile
import threading
import unittest
from unittest import mock

import compare_modern_editions_browser as audit
import reviewed_arabic_repairs as arabic_review


class BrowserMarkupTests(unittest.TestCase):
    def _write_spanish_matthew_17(self, root: Path, *, include_body: bool = False,
                                  variant: dict[str, str] | None = None) -> None:
        path = root / "new_testament" / "es" / "matthew.json"
        path.parent.mkdir(parents=True)
        bullets = ["Texto veinte. (17:20)."]
        if include_body:
            bullets.append("Texto veintiuno. (17:21).")
        bullets.append("Texto veintidós. (17:22).")
        expected = {
            "ref": "RVR1960 Matthew 17:21",
            "text": "Pero este género no sale sino con oración y ayuno.",
        }
        payload = {"stories": [{
            "id": "matthew-17",
            "summaryBullets": bullets,
            "manuscriptVariants": [expected if variant is None else variant],
        }]}
        path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

    @staticmethod
    def _spanish_matthew_17_snapshot(empty_verse: int = 21, bible_id: int = 128) -> dict:
        return {
            "language": "es", "book": "MAT", "chapter": 17,
            "pageUrl": f"https://www.bible.com/bible/{bible_id}/MAT.17.NVI",
            "pageTitle": "Mateo 17 | NVI",
            "records": [
                {"kind": "verse", "usfm": "MAT.17.20",
                 "html": '<span class="x__verse"><span class="x__label">20</span>Texto veinte.</span>'},
                {"kind": "verse", "usfm": f"MAT.17.{empty_verse}", "html": (
                    f'<span class="x__verse"><span class="x__label">{empty_verse}</span>'
                    '<span class="x__note">Publisher omission note</span></span>')},
                {"kind": "verse", "usfm": f"MAT.17.{empty_verse}",
                 "html": '<span class="x__verse"><span class="x__content"></span></span>'},
                {"kind": "verse", "usfm": "MAT.17.22",
                 "html": '<span class="x__verse"><span class="x__label">22</span>Texto veintidós.</span>'},
            ],
        }

    def test_ranges_labels_notes_headings_and_entities(self) -> None:
        html = """<main><div class="chapter-reader">
          <span class="x__heading">First &amp; Last</span>
          <span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>
            <span class="x__content">In <em>the</em> beginning &amp; earth<sup class="x__note">a</sup>.</span></span>
          <span class="x__heading">Second title</span>
          <span class="x__verse" data-usfm="GEN.1.2+GEN.1.3"><span class="x__label">2-3</span>
            <span class="x__content">A <span>joined</span> range.</span></span>
          <span class="x__verse" data-usfm="EXO.1.1">Other book</span>
        </div></main>"""
        parsed = audit.extract_browser_chapter(html, "GEN", 1)
        self.assertEqual({(1, 1), (2, 3)}, set(parsed.ranges))
        self.assertEqual("In the beginning & earth.", parsed.ranges[(1, 1)])
        self.assertEqual("A joined range.", parsed.ranges[(2, 3)])
        self.assertEqual(((1, "First & Last"), (2, "Second title")), parsed.headings)
        self.assertTrue(parsed.headings_available)

    def test_normalization_preserves_literal_spacing_and_punctuation(self) -> None:
        html = '<div class="chapter"><span class="x__verse" data-usfm="GEN.1.1"><span class="x__content">A  B, <i>cafe\u0301</i>!</span></span></div>'
        parsed = audit.extract_browser_chapter(html, "GEN", 1)
        self.assertEqual("A  B, cafe\u0301!".replace("e\u0301", "\u00e9"), parsed.ranges[(1, 1)])

    def test_duplicate_or_overlapping_range_fails_closed(self) -> None:
        html = '<div><span class="x__verse" data-usfm="GEN.1.1-2">one</span><span class="x__verse" data-usfm="GEN.1.2">two</span></div>'
        with self.assertRaises(audit.BrowserAuditError):
            audit.extract_browser_chapter(html, "GEN", 1)

    def test_out_of_order_ranges_fail_closed(self) -> None:
        records = [
            {"kind": "verse", "usfm": "ISA.38.21", "html": '<span class="x__verse">later</span>'},
            {"kind": "verse", "usfm": "ISA.38.7", "html": '<span class="x__verse">earlier</span>'},
        ]
        with self.assertRaisesRegex(audit.BrowserAuditError, "unordered native ranges"):
            audit.extract_browser_records(records, "ISA", 38)

    def test_nested_duplicate_wrapper_uses_inner_unit(self) -> None:
        html = '<div class="chapter"><div class="x__verse" data-usfm="GEN.1.1"><span class="x__verse" data-usfm="GEN.1.1"><b>Text</b></span></div></div>'
        parsed = audit.extract_browser_chapter(html, "GEN", 1)
        self.assertEqual({(1, 1): "Text"}, parsed.ranges)

    def test_a_b_suffix_is_not_inferred_as_separate_verses(self) -> None:
        html = '<span class="x__verse" data-usfm="GEN.1.1a">Text</span>'
        with self.assertRaisesRegex(audit.BrowserAuditError, "unparseable same-chapter native range"):
            audit.extract_browser_chapter(html, "GEN", 1)

    def test_unparseable_same_chapter_range_cannot_hide_beside_valid_verse(self) -> None:
        records = [
            {"kind": "verse", "usfm": "GEN.1.1", "html": '<span class="x__verse">First</span>'},
            {"kind": "verse", "usfm": "GEN.1.2b", "html": '<span class="x__verse">Second part</span>'},
        ]
        with self.assertRaisesRegex(audit.BrowserAuditError, "unparseable same-chapter native range"):
            audit.extract_browser_records(records, "GEN", 1)

    def test_challenge_page_fails_without_guessing(self) -> None:
        with self.assertRaises(audit.BrowserAuditError):
            audit.extract_browser_chapter('<title>Client Challenge</title><div>captcha</div>', "GEN", 1)

    def test_report_fingerprint_excludes_scripture(self) -> None:
        finding = audit._fingerprint("GEN.1.1", "text_mismatch", "local secret", "publisher secret")
        rendered = json.dumps(finding)
        self.assertNotIn("local secret", rendered)
        self.assertNotIn("publisher secret", rendered)
        self.assertEqual(64, len(finding["sourceSha256"]))

    def test_duplicate_fragments_concatenate_in_dom_order(self) -> None:
        records = [
            {"kind": "verse", "usfm": "MRK.9.43+MRK.9.44", "html": '<span class="x__verse"><span class="x__label">43-44</span><span class="x__content">First</span></span>'},
            {"kind": "verse", "usfm": "MRK.9.43+MRK.9.44", "html": '<span class="x__verse"><span class="x__content">second.</span><span class="x__note">note</span></span>'},
        ]
        parsed = audit.extract_browser_records(records, "MRK", 9)
        self.assertEqual({(43, 44): "First second."}, parsed.ranges)

    def test_exact_separate_superscription_matches_repeated_first_native_range(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "it" / "psalms.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [{
                "id": "psalms-7",
                "summaryBullets": ["Corpo del versetto. (7:1)."],
                "superscription": "Intestazione del salmo.",
            }]}, ensure_ascii=False), encoding="utf-8")
            original = path.read_bytes()
            source = audit.extract_browser_records([
                {"kind": "verse", "usfm": "PSA.7.1", "html": (
                    '<span class="x__verse"><span class="x__label">1</span>'
                    '<span class="x__content">Intestazione del salmo.</span></span>')},
                {"kind": "verse", "usfm": "PSA.7.1", "html": (
                    '<span class="x__verse"><span class="x__content">Corpo del versetto.</span></span>')},
            ], "PSA", 7)
            self.assertEqual({(1, 1): 2}, source.fragment_counts)

            result = audit.compare_source_chapter("it", "PSA", 7, root, source)

            self.assertEqual("match", result["status"])
            self.assertEqual([], result["findings"])
            self.assertEqual(original, path.read_bytes())

    def test_superscription_equivalence_stays_fail_closed_without_exact_split(self) -> None:
        cases = (
            (["Intestazione diversa.", "Corpo del versetto."], "changed superscription"),
            (["Intestazione del salmo. Corpo del versetto."], "single source fragment"),
        )
        for fragments, label in cases:
            with self.subTest(label=label), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                path = root / "old_testament" / "it" / "psalms.json"
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps({"stories": [{
                    "id": "psalms-7",
                    "summaryBullets": ["Corpo del versetto. (7:1)."],
                    "superscription": "Intestazione del salmo.",
                }]}, ensure_ascii=False), encoding="utf-8")
                records = []
                for index, fragment in enumerate(fragments):
                    label_html = '<span class="x__label">1</span>' if index == 0 else ""
                    records.append({
                        "kind": "verse",
                        "usfm": "PSA.7.1",
                        "html": (
                            f'<span class="x__verse">{label_html}'
                            f'<span class="x__content">{fragment}</span></span>'
                        ),
                    })
                source = audit.extract_browser_records(records, "PSA", 7)

                result = audit.compare_source_chapter("it", "PSA", 7, root, source)

                self.assertEqual("mismatch", result["status"])
                self.assertEqual("text_mismatch", result["findings"][0]["kind"])

    def test_multiple_content_spans_inside_one_verse_exclude_note(self) -> None:
        records = [{"kind": "verse", "usfm": "GEN.1.3", "html": (
            '<span class="x__verse"><span class="x__label">3</span>'
            '<span class="x__content">And God said, </span>'
            '<span class="x__note">publisher note</span>'
            '<span class="x__content">"Let there be light."</span></span>')}]
        parsed = audit.extract_browser_records(records, "GEN", 1)
        self.assertEqual('And God said, "Let there be light."', parsed.ranges[(3, 3)])

    def test_reviewed_arabic_dn_exception_requires_exact_story_source_and_offset(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "ar" / "genesis.json"
            path.parent.mkdir(parents=True)
            source_plain = "قال اللهُ."
            local_marked = "قال [DN]اللهُ[/DN]."
            story = {
                "id": "genesis-1",
                "summaryBullets": [local_marked + " (1:1)."],
                "headings": [{"beforeVerse": 1, "text": "عنوان"}],
            }
            payload = {"id": "genesis", "stories": [story]}
            path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            source = audit.BrowserChapter(
                ranges={(1, 1): source_plain}, headings=((1, "عنوان"),),
                headings_available=True, divine_markup={(1, 1): source_plain},
            )
            start = source_plain.index("اللهُ")
            chapter_key = ("ar", "GEN", 1)
            unit_key = ("ar", "GEN", 1, (1, 1))
            chapter_row = {
                "postStorySha256": arabic_review.digest_json(story),
                "sourceRangesSha256": arabic_review.digest_json([(1, 1)]),
                "sourceTextSha256": arabic_review.digest_json([((1, 1), source_plain)]),
                "sourceHeadingsSha256": arabic_review.digest_json([
                    {"beforeVerse": 1, "text": "عنوان"},
                ]),
            }
            unit_row = {
                "taggedPrefixSha256": arabic_review.digest_text(local_marked),
                "sourcePrefixSha256": arabic_review.digest_text(source_plain),
                "spans": ((start, start + len("اللهُ"), "اللهُ"),),
            }
            with mock.patch.dict(
                    audit.REVIEWED_ARABIC_CHAPTERS, {chapter_key: chapter_row}, clear=True), \
                    mock.patch.dict(
                        arabic_review.REVIEWED_ARABIC_DN_RANGES,
                        {unit_key: unit_row}, clear=True,
                    ):
                result = audit.compare_source_chapter("ar", "GEN", 1, root, source)
                self.assertEqual("match", result["status"])
                self.assertEqual(1, result["divineNamePresentationDifferences"])

                self.assertFalse(audit._reviewed_arabic_dn_presentation(
                    language="ar", code="GEN", chapter=1, unit=(1, 1),
                    local_marked="[DN]قال[/DN] اللهُ.", source_plain=source_plain,
                    source_marked=source_plain, chapter_context=chapter_row,
                ))

                story["reviewChanged"] = True
                path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
                changed_story = audit.compare_source_chapter("ar", "GEN", 1, root, source)
                self.assertEqual("mismatch", changed_story["status"])
                self.assertEqual("divine_name_boundary_mismatch", changed_story["findings"][0]["kind"])

                del story["reviewChanged"]
                path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
                changed_source = audit.BrowserChapter(
                    ranges={(1, 1): source_plain + " حقا."}, headings=((1, "عنوان"),),
                    headings_available=True,
                    divine_markup={(1, 1): source_plain + " حقا."},
                )
                source_result = audit.compare_source_chapter(
                    "ar", "GEN", 1, root, changed_source,
                )
                self.assertEqual("mismatch", source_result["status"])
                self.assertEqual(
                    "divine_name_boundary_mismatch",
                    source_result["findings"][0]["kind"],
                )

    def test_footnote_only_omitted_verse_stays_blocked(self) -> None:
        records = [
            {"kind": "verse", "usfm": "MAT.17.21", "html": (
                '<span class="x__verse"><span class="x__label">21</span>'
                '<span class="x__note">Publisher omission note</span></span>')},
            {"kind": "verse", "usfm": "MAT.17.21",
             "html": '<span class="x__verse"><span class="x__content"></span></span>'},
        ]
        with self.assertRaisesRegex(audit.BrowserAuditError, "empty native verse range"):
            audit.extract_browser_records(records, "MAT", 17)

    def test_read_only_nvi_omission_requires_locked_local_variant(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_spanish_matthew_17(root)
            result = audit.compare_stdin_snapshot(self._spanish_matthew_17_snapshot(), root)
            self.assertEqual("match", result["status"])
            self.assertEqual(1, result["sourceOmissions"])
            self.assertEqual(1, result["emptyRanges"])
            self.assertEqual(2, result["localRanges"])
            self.assertEqual(2, result["sourceRanges"])

        expected_allowlist = {
            ("MAT", 17, 21), ("MAT", 18, 11), ("MAT", 23, 14),
            ("MRK", 7, 16), ("MRK", 9, 44), ("MRK", 9, 46),
            ("MRK", 11, 26), ("MRK", 15, 28),
            ("LUK", 17, 36), ("LUK", 23, 17), ("JHN", 5, 4),
            ("ACT", 8, 37), ("ACT", 15, 34), ("ACT", 24, 7), ("ACT", 28, 29),
            ("ROM", 16, 24),
        }
        self.assertEqual(expected_allowlist, set(audit.NVI_SOURCE_OMISSIONS))

    def test_nvi_omission_blocks_local_body_or_variant_drift(self) -> None:
        cases = (
            (True, None, "local body coverage mismatch"),
            (False, {"ref": "RVR1960 Matthew 17:21", "text": "altered"},
             "manuscript variant mismatch"),
            (False, {"ref": "RVR1960 Mateo 17:21",
                     "text": "Pero este género no sale sino con oración y ayuno."},
             "manuscript variant mismatch"),
        )
        for include_body, variant, message in cases:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                self._write_spanish_matthew_17(root, include_body=include_body, variant=variant)
                with self.assertRaisesRegex(audit.BrowserAuditError, message):
                    audit.compare_stdin_snapshot(self._spanish_matthew_17_snapshot(), root)

    def test_empty_source_range_wrong_edition_or_reference_stays_blocked(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_spanish_matthew_17(root)
            wrong_edition = self._spanish_matthew_17_snapshot(bible_id=149)
            with self.assertRaisesRegex(audit.BrowserAuditError, "Bible ID"):
                audit.compare_stdin_snapshot(wrong_edition, root)
            wrong_reference = self._spanish_matthew_17_snapshot(empty_verse=20)
            wrong_reference["records"] = wrong_reference["records"][1:]
            with self.assertRaisesRegex(audit.BrowserAuditError, "unapproved NVI"):
                audit.compare_stdin_snapshot(wrong_reference, root)

        arabic = {
            "language": "ar", "book": "LUK", "chapter": 7,
            "pageUrl": "https://www.bible.com/bible/153/LUK.7.SAB",
            "pageTitle": "Luke 7 | SAB",
            "records": [
                {"kind": "verse", "usfm": "LUK.7.15",
                 "html": '<span class="x__verse"><span class="x__label">15</span></span>'},
                {"kind": "verse", "usfm": "LUK.7.15",
                 "html": '<span class="x__verse"><span class="x__content"></span></span>'},
            ],
        }
        with self.assertRaisesRegex(audit.BrowserAuditError, "locked SAB Luke 7 rendered-source hash changed"):
            audit.compare_stdin_snapshot(arabic, Path("unused"))

    def test_exact_locked_sab_luke7_defect_is_classified_without_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "new_testament/ar/luke.json"
            path.parent.mkdir(parents=True)
            verses = (1, 11, 15, 18, 36)
            local_headings = [
                {"beforeVerse": verse, "text": f"Local {verse}"}
                for verse in (1, 11, 18, 36)
            ]
            story = {
                "id": "luke-7",
                "summaryBullets": [f"Verse {verse}. (7:{verse})." for verse in verses],
                "headings": local_headings,
            }
            path.write_text(json.dumps({"id": "luke", "stories": [story]}), encoding="utf-8")
            records: list[dict[str, str]] = []
            for verse in verses:
                if verse != 15:
                    records.append({
                        "kind": "heading",
                        "html": f'<h3 class="x__heading">Source {verse}</h3>',
                    })
                    records.append({
                        "kind": "verse", "usfm": f"LUK.7.{verse}",
                        "html": (
                            f'<span class="x__verse" data-usfm="LUK.7.{verse}">'
                            f'<span class="x__label">{verse}</span>Verse {verse}.</span>'
                        ),
                    })
                else:
                    records.extend((
                        {"kind": "verse", "usfm": "LUK.7.15", "html": (
                            '<span class="x__verse" data-usfm="LUK.7.15">'
                            '<span class="x__label">15</span></span>')},
                        {"kind": "verse", "usfm": "LUK.7.15", "html": (
                            '<span class="x__verse" data-usfm="LUK.7.15">'
                            '<span class="x__content"></span></span>')},
                    ))
            filtered = [record for record in records if record.get("usfm") != "LUK.7.15"]
            source = audit.extract_browser_records(filtered, "LUK", 7)
            verse_15 = "Verse 15."
            locked = {
                "recordsSha256": audit._canonical_json_sha256(records),
                "emptyRecordsSha256": audit._canonical_json_sha256([
                    record for record in records if record.get("usfm") == "LUK.7.15"
                ]),
                "localStorySha256": audit._canonical_json_sha256(story),
                "localVersePrefixSha256": audit.sha256_text(verse_15).lower(),
                "sourceRangesSha256": audit._canonical_json_sha256(list(source.ranges)),
                "sourceTextSha256": audit._canonical_json_sha256(list(source.ranges.items())),
                "sourceHeadingsSha256": audit._canonical_json_sha256([
                    {"beforeVerse": anchor, "text": text}
                    for anchor, text in audit.normalize_source_headings(source.headings)
                ]),
            }
            snapshot = {
                "language": "ar", "book": "LUK", "chapter": 7,
                "pageUrl": "https://www.bible.com/bible/153/LUK.7.SAB",
                "pageTitle": "Luke 7 | SAB", "records": records,
            }
            fallback_source = {
                **audit.SAB_LUK7_FALLBACK_SOURCE,
                "verseTextSha256": audit.sha256_text(verse_15).lower(),
            }
            with (mock.patch.dict(audit.SAB_LUK7_DEFECT, locked, clear=True),
                  mock.patch.dict(audit.SAB_LUK7_FALLBACK_SOURCE, fallback_source, clear=True)):
                result = audit.compare_stdin_snapshot(snapshot, root)
                self.assertEqual("source_defect", result["status"])
                self.assertFalse(result["sourceParityClaimed"])
                self.assertTrue(result["localSourceDefectVersePreserved"])
                self.assertTrue(result["fallbackSourceVerified"])
                self.assertEqual(fallback_source, result["fallbackSource"])
                self.assertEqual(["LUK.7.15"], result["sourceDefectRanges"])
                self.assertEqual(1, result["emptyRanges"])

                audit.SAB_LUK7_FALLBACK_SOURCE["verseTextSha256"] = "0" * 64
                with self.assertRaisesRegex(audit.BrowserAuditError, "publisher verification changed"):
                    audit.compare_stdin_snapshot(snapshot, root)
                audit.SAB_LUK7_FALLBACK_SOURCE["verseTextSha256"] = fallback_source["verseTextSha256"]

                changed = json.loads(json.dumps(snapshot))
                changed["records"][0]["html"] += " "
                with self.assertRaisesRegex(audit.BrowserAuditError, "rendered-source hash changed"):
                    audit.compare_stdin_snapshot(changed, root)

                story["summaryBullets"][2] = "Altered. (7:15)."
                path.write_text(
                    json.dumps({"id": "luke", "stories": [story]}), encoding="utf-8"
                )
                with self.assertRaisesRegex(audit.BrowserAuditError, "local story hash changed"):
                    audit.compare_stdin_snapshot(snapshot, root)

    def test_checked_in_sab_luke7_verse_matches_publisher_fallback(self) -> None:
        path = audit.ROOT / "shared/assets/books/new_testament/ar/luke.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        story = next(story for story in payload["stories"] if story["id"] == "luke-7")
        verse = next(bullet for bullet in story["summaryBullets"] if bullet.endswith("(7:15)."))
        exact_publisher_text = "فَجَلَسَ الْمَيِّتُ وَأَخَذَ يَتَكَلَّمُ، فَأَعْطَاهُ عِيسَى لِأُمِّهِ."
        self.assertEqual(f"{exact_publisher_text} (7:15).", verse)
        self.assertEqual(audit.SAB_LUK7_DEFECT["localStorySha256"], audit._canonical_json_sha256(story))
        self.assertEqual(
            audit.SAB_LUK7_FALLBACK_SOURCE["verseTextSha256"],
            audit.sha256_text(exact_publisher_text).lower(),
        )

    def test_nested_divine_name_between_content_spans_survives(self) -> None:
        records = [{"kind": "verse", "usfm": "GEN.2.4", "html": (
            '<span class="x__verse"><span class="x__label">4</span>'
            '<span class="x__content">Dies ist die Geschichte, die </span>'
            '<span class="x__note">publisher note</span>'
            '<span class="x__content">Gott und </span>'
            '<span class="x__nd"><span class="x__content">Herr</span></span>'
            '<span class="x__content"> schuf.</span></span>')}]
        parsed = audit.extract_browser_records(records, "GEN", 2)
        self.assertEqual("Dies ist die Geschichte, die Gott und Herr schuf.", parsed.ranges[(4, 4)])
        self.assertEqual("Dies ist die Geschichte, die Gott und [DN]Herr[/DN] schuf.", parsed.divine_markup[(4, 4)])

    def test_german_and_spanish_divine_name_presentation_is_semantic_match(self) -> None:
        for language, local_name, source_name in (("de", "HERR", "Herr"), ("es", "SEÑOR", "Señor")):
            with self.subTest(language=language), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                path = root / "old_testament" / language / "genesis.json"
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps({"stories": [{"summaryBullets": [f"Gott und [DN]{local_name}[/DN] sprach. (1:1)"]}]}), encoding="utf-8")
                source = audit.extract_browser_records([{"kind": "verse", "usfm": "GEN.1.1", "html": (
                    '<span class="mod__verse__1"><span class="mod__label__1">1</span>'
                    '<span class="mod__content__1">Gott und </span>'
                    f'<span class="mod__nd__1"><span class="mod__content__1">{source_name}</span></span>'
                    '<span class="mod__content__1"> sprach.</span></span>')}], "GEN", 1)
                result = audit.compare_source_chapter(language, "GEN", 1, root, source)
                self.assertEqual("match", result["status"])
                self.assertEqual(1, result["divineNamePresentationDifferences"])
                self.assertEqual([], result["findings"])

    def test_runtime_generated_divine_names_in_untagged_base_scripture(self) -> None:
        cases = (
            ("de", "HERR", "Herr", 1, "presentation"),
            ("es", "SEÑOR", "Señor", 1, "presentation"),
            ("fr", "SEIGNEUR", "Seigneur", 1, "presentation"),
            ("zh-Hans", "耶和华", "耶和华", 0, "exact"),
            ("zh-Hans", "上主", "上主", 0, "gap"),
        )
        for language, local_name, source_name, count, verdict in cases:
            with self.subTest(language=language, token=local_name), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                path = root / "old_testament" / language / "genesis.json"
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps({"stories": [{"summaryBullets": [f"A {local_name} B (1:1)"]}]}, ensure_ascii=False), encoding="utf-8")
                source = audit.extract_browser_records([{"kind": "verse", "usfm": "GEN.1.1", "html": (
                    '<span class="x__verse"><span class="x__label">1</span><span class="x__content">A </span>'
                    f'<span class="x__nd"><span class="x__content">{source_name}</span></span>'
                    '<span class="x__content"> B</span></span>')}], "GEN", 1)
                result = audit.compare_source_chapter(language, "GEN", 1, root, source)
                self.assertEqual(count, result["divineNamePresentationDifferences"])
                if verdict == "gap":
                    self.assertEqual("divine_name_runtime_coverage_gap", result["findings"][0]["kind"])
                else:
                    self.assertEqual("match", result["status"])
                    self.assertEqual([], result["findings"])
        self.assertTrue(audit._runtime_covers_untagged_dn("ja", "主の使い", 0, 1, "old_testament"))
        self.assertFalse(audit._runtime_covers_untagged_dn("ja", "主人", 0, 1, "old_testament"))
        self.assertTrue(audit._runtime_covers_untagged_dn("ar", "الرَّبِّ", 0, len("الرَّبِّ"), "old_testament"))
        self.assertTrue(audit._runtime_covers_untagged_dn("ko", "주님의", 0, 2, "old_testament"))
        self.assertFalse(audit._runtime_covers_untagged_dn("en", "LORD", 0, 4, "new_testament"))

    def test_divine_name_runtime_gap_and_explicit_boundary_mismatch_are_distinct(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "de" / "genesis.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [{"summaryBullets": ["Gott und Herr sprach. (1:1)"]}]}), encoding="utf-8")
            source = audit.extract_browser_records([{"kind": "verse", "usfm": "GEN.1.1", "html": (
                '<span class="x__verse"><span class="x__label">1</span>'
                '<span class="x__content">Gott und </span><span class="x__nd"><span class="x__content">Herr</span></span>'
                '<span class="x__content"> sprach.</span></span>')}], "GEN", 1)
            result = audit.compare_source_chapter("de", "GEN", 1, root, source)
            self.assertEqual("mismatch", result["status"])
            self.assertEqual("divine_name_runtime_coverage_gap", result["findings"][0]["kind"])
            self.assertNotIn("Gott", json.dumps(result))
        self.assertEqual("divine_name_boundary_mismatch", audit._dn_difference("[DN]Herr[/DN]", "H[DN]err[/DN]"))
        self.assertEqual("text_mismatch", audit._dn_difference("X[DN]Herr[/DN]", "x[DN]Herr[/DN]"))
        self.assertEqual("divine_name_presentation_difference", audit._dn_difference("[DN]STRAßE[/DN]", "[DN]Strasse[/DN]"))

    def test_french_nonbreaking_spaces_are_distinct_typography_findings(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "fr" / "genesis.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [{"summaryBullets": ["Il dit : « parole » (1:1)"]}]}), encoding="utf-8")
            source = audit.extract_browser_records([{"kind": "verse", "usfm": "GEN.1.1", "html": (
                '<span class="x__verse"><span class="x__label">1</span>'
                '<span class="x__content">Il dit\u00a0: «\u202fparole\u00a0»</span></span>')}], "GEN", 1)
            result = audit.compare_source_chapter("fr", "GEN", 1, root, source)
            self.assertEqual("mismatch", result["status"])
            self.assertEqual(1, result["typographySpacingDifferences"])
            self.assertEqual("typography_spacing_difference", result["findings"][0]["kind"])

        self.assertEqual("a  b", audit._space_separator_fold("a\u00a0\u202fb"))
        self.assertNotEqual("a b", audit._space_separator_fold("a\u00a0\u202fb"))
        self.assertEqual("text_mismatch", audit._dn_difference("A : mot", "A\u00a0; mot"))

    def test_unicode_layout_space_is_trimmed_only_at_native_range_edges(self) -> None:
        records = [{
            "kind": "verse",
            "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse"><span class="x__label">1</span>'
                '<span class="x__content">\u202fText\u00a0: inside\u202f</span></span>'
            ),
        }]
        parsed = audit.extract_browser_records(records, "GEN", 1)
        self.assertEqual({(1, 1): "Text\u00a0: inside"}, parsed.ranges)

    def test_publisher_nd_outer_bracket_is_not_part_of_divine_name_boundary(self) -> None:
        records = [{
            "kind": "verse",
            "usfm": "LEV.27.10",
            "html": (
                '<span class="x__verse"><span class="x__label">10</span>'
                '<span class="x__content">A [dem </span>'
                '<span class="x__nd"><span class="x__content">Herrn]</span></span>'
                '<span class="x__content"> heilig.</span></span>'
            ),
        }]
        parsed = audit.extract_browser_records(records, "LEV", 27)
        self.assertEqual("A [dem Herrn] heilig.", parsed.ranges[(10, 10)])
        self.assertEqual("A [dem [DN]Herrn[/DN]] heilig.", parsed.divine_markup[(10, 10)])

    def test_heading_binds_to_next_labelled_range_not_unlabelled_fragment(self) -> None:
        records = [
            {"kind": "verse", "usfm": "GEN.1.1", "html": '<span class="x__verse"><span class="x__label">1</span>First</span>'},
            {"kind": "heading", "html": '<span class="x__heading">Next section</span>'},
            {"kind": "verse", "usfm": "GEN.1.1", "html": '<span class="x__verse">continued</span>'},
            {"kind": "verse", "usfm": "GEN.1.2", "html": '<span class="x__verse"><span class="x__label">2</span>Second</span>'},
        ]
        parsed = audit.extract_browser_records(records, "GEN", 1)
        self.assertEqual(((2, "Next section"),), parsed.headings)

    def test_trailing_heading_after_last_labelled_range_is_ignored(self) -> None:
        records = [
            {"kind": "verse", "usfm": "GEN.1.1", "html": (
                '<span class="x__verse"><span class="x__label">1</span>'
                '<span class="x__content">Scripture text.</span></span>')},
            {"kind": "heading", "html": '<span class="x__heading">Next section</span>'},
        ]
        parsed = audit.extract_browser_records(records, "GEN", 1)
        self.assertEqual({(1, 1): "Scripture text."}, parsed.ranges)
        self.assertEqual((), parsed.headings)
        self.assertTrue(parsed.headings_available)

    def test_snapshot_record_limit_allows_fragmented_long_chapter(self) -> None:
        record = {"kind": "verse", "usfm": "PSA.119.1",
                  "html": '<span class="x__verse">fragment</span>'}
        payload = {"language": "en", "book": "PSA", "chapter": 119,
                   "pageUrl": "https://www.bible.com/bible/3034/PSA.119.BSB",
                   "pageTitle": "Psalm 119 | BSB", "records": [record] * 559}
        source = audit.BrowserChapter({(1, 1): "text"}, (), False)
        expected = {"status": "match"}
        with mock.patch.object(audit, "validate_page_identity") as identity, \
                mock.patch.object(audit, "extract_browser_records", return_value=source) as extract, \
                mock.patch.object(audit, "compare_source_chapter", return_value=expected) as compare:
            self.assertIs(expected, audit.compare_stdin_snapshot(payload, Path("unused")))
        identity.assert_called_once()
        extract.assert_called_once_with(payload["records"], "PSA", 119)
        compare.assert_called_once_with("en", "PSA", 119, Path("unused"), source)

        payload["records"] = [record] * (audit.MAX_SNAPSHOT_RECORDS + 1)
        with self.assertRaisesRegex(audit.BrowserAuditError, "snapshot record limit"):
            audit.compare_stdin_snapshot(payload, Path("unused"))

    def test_stacked_source_heading_lines_merge_to_one_app_object(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "de" / "genesis.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [{"summaryBullets": ["Text (1:1)"],
                                        "headings": [{"beforeVerse": 1, "text": "Title\nSubtitle"}]}]}), encoding="utf-8")
            records = [
                {"kind": "heading", "html": '<span class="x__heading">Title</span>'},
                {"kind": "heading", "html": '<span class="x__heading">Subtitle</span>'},
                {"kind": "verse", "usfm": "GEN.1.1", "html": '<span class="x__verse"><span class="x__label">1</span>Text</span>'},
            ]
            source = audit.extract_browser_records(records, "GEN", 1)
            result = audit.compare_source_chapter("de", "GEN", 1, root, source)
            self.assertEqual("match", result["status"])
            self.assertEqual(2, result["sourceHeadingLines"])
            self.assertEqual(1, result["sourceHeadings"])

    def test_blank_and_punctuation_only_heading_fragments_are_ignored(self) -> None:
        records = [
            {"kind": "heading", "html": '<span class="x__heading"> \t </span>'},
            {"kind": "heading", "html": '<span class="x__heading">\u00a0;</span>'},
            {"kind": "heading", "html": '<span class="x__heading">\u202fUn  vrai titre\u00a0</span>'},
            {"kind": "verse", "usfm": "GEN.11.1", "html": '<span class="x__verse"><span class="x__label">1</span>Texte</span>'},
        ]
        parsed = audit.extract_browser_records(records, "GEN", 11)
        self.assertEqual(((1, "Un  vrai titre"),), parsed.headings)
        self.assertTrue(parsed.headings_available)
        artifacts_only = audit.extract_browser_records(records[:2] + records[3:], "GEN", 11)
        self.assertEqual((), artifacts_only.headings)
        self.assertFalse(artifacts_only.headings_available)

    def test_browser_snapshot_protocol_uses_hash_only_result(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "en" / "genesis.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [{"summaryBullets": ["Local text (1:1)"]}]}), encoding="utf-8")
            snapshot = {"language": "en", "book": "GEN", "chapter": 1,
                        "pageUrl": "https://www.bible.com/bible/3034/GEN.1.BSB", "pageTitle": "Genesis 1 | BSB",
                        "records": [
                {"kind": "verse", "usfm": "GEN.1.1", "html": '<span class="x__verse"><span class="x__content">Source text</span></span>'},
            ]}
            result = audit.compare_stdin_snapshot(snapshot, root)
            rendered = json.dumps(result)
            self.assertEqual("mismatch", result["status"])
            self.assertNotIn("Local text", rendered)
            self.assertNotIn("Source text", rendered)

    def test_snapshot_rejects_wrong_edition_passage_and_challenge(self) -> None:
        base = {"language": "en", "book": "GEN", "chapter": 1,
                "pageUrl": "https://www.bible.com/bible/3034/GEN.1.BSB", "pageTitle": "Genesis 1 | BSB",
                "records": [{"kind": "verse", "usfm": "GEN.1.1", "html": '<span class="x__verse">Text</span>'}]}
        bad = [
            {**base, "pageUrl": "https://www.bible.com/bible/1/GEN.1.KJV"},
            {**base, "pageUrl": "https://www.bible.com/bible/3034/EXO.1.BSB"},
            {**base, "pageTitle": "Client Challenge"},
            {**base, "pageUrl": "https://bible.com.evil.test/bible/3034/GEN.1.BSB"},
        ]
        for snapshot in bad:
            with self.subTest(page=snapshot["pageUrl"], title=snapshot["pageTitle"]):
                with self.assertRaises(audit.BrowserAuditError):
                    audit.compare_stdin_snapshot(snapshot, Path("unused"))
        audit.validate_page_identity("https://www.bible.com/de/bible/157/GEN.1.SCH2000", "1. Mose 1", 157, "GEN", 1)

    def test_jsonl_stream_two_snapshots_and_invalid_record(self) -> None:
        class FlushedSink(io.StringIO):
            flushes = 0

            def flush(self) -> None:
                self.flushes += 1
                super().flush()

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "en" / "genesis.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [{"summaryBullets": ["Local secret (1:1)"]}]}), encoding="utf-8")
            base = {"language": "en", "book": "GEN", "chapter": 1,
                    "pageUrl": "https://www.bible.com/bible/3034/GEN.1.BSB", "pageTitle": "Genesis 1 | BSB"}
            matching = {**base, "records": [{"kind": "verse", "usfm": "GEN.1.1",
                         "html": '<span class="mod__verse__1"><span class="mod__content__1">Local secret</span></span>'}]}
            mismatch = {**base, "records": [{"kind": "verse", "usfm": "GEN.1.1",
                         "html": '<span class="mod__verse__2"><span class="mod__content__2">Publisher secret</span></span>'}]}
            source = io.StringIO("\n".join((json.dumps(matching), json.dumps(mismatch), "{invalid raw source}")) + "\n")
            sink = FlushedSink()
            status = audit.stream_snapshots(source, sink, root)
            output = sink.getvalue()
            rows = [json.loads(line) for line in output.splitlines()]
            self.assertEqual(2, status)
            self.assertEqual(["match", "mismatch", "blocked"], [row["status"] for row in rows])
            self.assertEqual(3, sink.flushes)
            self.assertNotIn("Local secret", output)
            self.assertNotIn("Publisher secret", output)
            self.assertNotIn("invalid raw source", output)

    def test_loopback_snapshot_server_returns_sanitized_results(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "old_testament" / "en" / "genesis.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({"stories": [{"summaryBullets": ["Local secret (1:1)"]}]}), encoding="utf-8")
            server = audit.create_snapshot_server(0, root)
            self.assertEqual("127.0.0.1", server.server_address[0])
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            client = HTTPConnection("127.0.0.1", server.server_port, timeout=3)
            try:
                client.request("GET", "/health")
                health = client.getresponse()
                self.assertEqual(200, health.status)
                self.assertEqual({"status": "ok"}, json.loads(health.read()))

                snapshot = {"language": "en", "book": "GEN", "chapter": 1,
                            "pageUrl": "https://www.bible.com/bible/3034/GEN.1.BSB", "pageTitle": "Genesis 1 | BSB",
                            "records": [
                    {"kind": "verse", "usfm": "GEN.1.1",
                     "html": '<span class="x__verse"><span class="x__content">Publisher secret</span></span>'}]}
                client.request("POST", "/snapshot", json.dumps(snapshot), {"Content-Type": "application/json"})
                response = client.getresponse()
                self.assertEqual(200, response.status)
                self.assertEqual("no-store", response.getheader("Cache-Control"))
                body = response.read().decode("utf-8")
                self.assertEqual("mismatch", json.loads(body)["status"])
                self.assertNotIn("Publisher secret", body)
                self.assertNotIn("Local secret", body)

                client.request("POST", "/snapshot", "{bad raw source}", {"Content-Type": "application/json"})
                bad = json.loads(client.getresponse().read())
                self.assertEqual("blocked", bad["status"])
                self.assertNotIn("raw source", json.dumps(bad))
            finally:
                client.close()
                server.shutdown()
                server.server_close()
                worker.join(timeout=3)

    def test_local_heading_read_preserves_only_anchors_and_normalized_text(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "genesis.json"
            path.write_text(json.dumps({"stories": [{
                "summaryBullets": ["Text (1:1)"],
                "headings": [{"beforeVerse": 1, "text": "The [DN]LORD[/DN]"}],
            }]}), encoding="utf-8")
            self.assertEqual(((1, "The LORD"),), audit.local_headings(path, 1))


if __name__ == "__main__":
    unittest.main()
