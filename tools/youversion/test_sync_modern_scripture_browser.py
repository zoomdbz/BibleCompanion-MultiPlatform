from __future__ import annotations

import http.client
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock

from sync_modern_scripture_browser import (
    DivineNameCoverageError,
    ScriptureSyncError,
    _is_proven_source_divine_name,
    _sha_json,
    create_snapshot_server,
    stream_snapshots,
    sync_snapshot,
)


class ScriptureBrowserSyncTests(unittest.TestCase):
    maxDiff = None

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_book(self, bullets: list[str], *, raw_transform=None, extra_story: bool = False,
                   language: str = "en", book_id: str = "genesis", root_id: str | None = None) -> Path:
        root_id = root_id or book_id
        stories = [{
            "id": f"{root_id}-1",
            "title": "Genesis 1",
            "refs": ["Genesis 1:1"],
            "summaryBullets": bullets,
            "crossRefs": ["untouched"],
            "translationNotes": [{"term": "untouched", "note": "untouched"}],
            "keyTakeaway": "untouched",
            "headings": [],
        }]
        if extra_story:
            stories.append({
                "id": f"{root_id}-2", "title": "Genesis 2", "refs": ["Genesis 2:1"],
                "summaryBullets": ["Second chapter untouched. (2:1)."], "crossRefs": [],
                "translationNotes": [], "keyTakeaway": "untouched", "headings": [],
            })
        payload = {"id": root_id, "title": "Genesis", "stories": stories, "sentinel": "untouched"}
        raw = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        if raw_transform is not None:
            raw = raw_transform(raw)
        path = self.root / "shared/assets/books/old_testament" / language / f"{book_id}.json"
        path.parent.mkdir(parents=True)
        path.write_bytes(raw.encode("utf-8"))
        return path

    @staticmethod
    def verse(usfm: str, content: str, *, semantic_class: str | None = None) -> dict[str, str]:
        if semantic_class:
            content = f'<span class="{semantic_class}"><span class="x__content">{content}</span></span>'
        else:
            content = f'<span class="x__content">{content}</span>'
        return {
            "kind": "verse",
            "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}">'
                f'<span class="x__label">1</span>{content}</span>'
            ),
        }

    def snapshot(self, records: list[dict[str, str]], *, language: str = "en", bible_id: int = 3034,
                 book: str = "GEN", abbreviation: str = "BSB", **changes) -> dict[str, object]:
        result: dict[str, object] = {
            "language": language, "bibleId": bible_id, "book": book, "chapter": 1,
            "pageUrl": f"https://www.bible.com/bible/{bible_id}/{book}.1.{abbreviation}",
            "pageTitle": "Genesis 1 BSB",
            "records": records,
        }
        result.update(changes)
        return result

    @staticmethod
    def divine_record(before: str, divine_name: str, after: str, *, usfm: str = "GEN.1.1") -> dict[str, str]:
        return {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">1</span>'
                f'<span class="x__content">{before}</span>'
                f'<span class="x__nd"><span class="x__content">{divine_name}</span></span>'
                f'<span class="x__content">{after}</span></span>'
            ),
        }

    def test_default_audit_reports_change_without_writing(self) -> None:
        path = self.write_book(["Old wording. (1:1)."])
        before = path.read_bytes()
        result = sync_snapshot(self.snapshot([self.verse("GEN.1.1", "New wording.")]), root=self.root)
        self.assertEqual("changed", result["status"])
        self.assertEqual("audit", result["mode"])
        self.assertEqual(1, result["changedRangeCount"])
        self.assertEqual(before, path.read_bytes())
        self.assertNotIn("New wording", json.dumps(result))

    def test_apply_changes_only_prefix_and_preserves_raw_escaped_marker(self) -> None:
        def escaped_marker(raw: str) -> str:
            return raw.replace("Old wording. (1:1).", r"Old wording. \u00281:1\u0029.")

        path = self.write_book(["Old wording. (1:1)."], raw_transform=escaped_marker, extra_story=True)
        before = path.read_text(encoding="utf-8")
        result = sync_snapshot(
            self.snapshot([self.verse("GEN.1.1", "New exact wording.")]), root=self.root, apply=True
        )
        after = path.read_text(encoding="utf-8")
        self.assertEqual("changed", result["status"])
        self.assertEqual("apply", result["mode"])
        self.assertIn(r"New exact wording. \u00281:1\u0029.", after)
        self.assertIn('"id": "genesis-2"', after)
        self.assertEqual(before[before.index(r"\u00281:1\u0029."):], after[after.index(r"\u00281:1\u0029."):])
        self.assertEqual("New exact wording. (1:1).", json.loads(after)["stories"][0]["summaryBullets"][0])

    def test_publisher_prefix_trailing_space_does_not_change_native_marker(self) -> None:
        path = self.write_book(["Old wording. (1:1)."])
        before = path.read_text(encoding="utf-8")
        with mock.patch(
            "sync_modern_scripture_browser._confirmed_tagged_replacement",
            return_value=("New wording.  ", 0, 0),
        ):
            result = sync_snapshot(
                self.snapshot([self.verse("GEN.1.1", "New wording.  ")]),
                root=self.root,
                apply=True,
            )
        after = path.read_text(encoding="utf-8")
        self.assertEqual("changed", result["status"])
        self.assertEqual("New wording.   (1:1).", json.loads(after)["stories"][0]["summaryBullets"][0])
        self.assertEqual(before[before.index(" (1:1)."):], after[after.index(" (1:1)."):])

    def test_raw_offset_mapper_handles_surrogate_pair_and_escaped_quote(self) -> None:
        def escaped_prefix(raw: str) -> str:
            return raw.replace(
                'Old 😃 \\"word\\". (1:1).',
                r'Old \ud83d\ude03 \"word\". \u00281:1\u0029.',
            )

        path = self.write_book(['Old 😃 "word". (1:1).'], raw_transform=escaped_prefix)
        before = path.read_text(encoding="utf-8")
        sync_snapshot(
            self.snapshot([self.verse("GEN.1.1", 'New 😃 "word".')]), root=self.root, apply=True
        )
        after = path.read_text(encoding="utf-8")
        self.assertIn(r'New 😃 \"word\". \u00281:1\u0029.', after)
        self.assertEqual(before[before.index(r"\u00281:1\u0029."):], after[after.index(r"\u00281:1\u0029."):])

    def test_match_apply_leaves_file_byte_identical(self) -> None:
        path = self.write_book(["Exact wording. (1:1)."])
        before = path.read_bytes()
        result = sync_snapshot(
            self.snapshot([self.verse("GEN.1.1", "Exact wording.")]), root=self.root, apply=True
        )
        self.assertEqual("match", result["status"])
        self.assertEqual(before, path.read_bytes())

    def test_native_plus_range_remains_one_bullet(self) -> None:
        path = self.write_book(["Old combined line. (1:1-2)."])
        result = sync_snapshot(
            self.snapshot([self.verse("GEN.1.1+GEN.1.2", "New combined line.")]),
            root=self.root,
            apply=True,
        )
        bullets = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"]
        self.assertEqual(1, result["nativeRangeCount"])
        self.assertEqual(["New combined line. (1:1-2)."], bullets)

    def test_range_inventory_mismatch_blocks_without_write(self) -> None:
        path = self.write_book(["One. (1:1).", "Two. (1:2)."])
        before = path.read_bytes()
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.1", "One.")]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_concurrent_asset_change_blocks_before_atomic_replace(self) -> None:
        path = self.write_book(["Old wording. (1:1)."])
        before = path.read_bytes()
        real_read_bytes = Path.read_bytes
        reads = 0

        def raced_read_bytes(target: Path) -> bytes:
            nonlocal reads
            value = real_read_bytes(target)
            if target == path:
                reads += 1
                if reads == 2:
                    return value + b" "
            return value

        with mock.patch.object(Path, "read_bytes", raced_read_bytes):
            with self.assertRaises(ScriptureSyncError):
                sync_snapshot(
                    self.snapshot([self.verse("GEN.1.1", "New wording.")]), root=self.root, apply=True
                )
        self.assertEqual(before, path.read_bytes())

    def test_wrong_bible_id_url_and_challenge_each_block(self) -> None:
        self.write_book(["One. (1:1)."])
        record = [self.verse("GEN.1.1", "One.")]
        for changes in (
            {"bibleId": 1},
            {"pageUrl": "https://www.bible.com/bible/1/GEN.1.KJV"},
            {"pageUrl": "https://evil.example/bible/3034/GEN.1.BSB"},
            {"pageTitle": "Client Challenge"},
            {"pageTitle": None},
            {"book": "EXO", "pageUrl": "https://www.bible.com/bible/3034/GEN.1.BSB"},
            {"chapter": 2},
        ):
            with self.subTest(changes=changes), self.assertRaises(ScriptureSyncError):
                sync_snapshot(self.snapshot(record, **changes), root=self.root)

        source_url_only = self.snapshot(record)
        source_url_only["sourceUrl"] = source_url_only.pop("pageUrl")
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(source_url_only, root=self.root)

    def test_changed_whole_verse_jesus_tag_accepts_source_wrapper(self) -> None:
        path = self.write_book(["[J]Jesus said old middle and done.[/J] (1:1)."])
        result = sync_snapshot(
            self.snapshot([self.verse(
                "GEN.1.1", "Jesus said new middle and done.", semantic_class="ChapterContent_wj__abc"
            )]),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]Jesus said new middle and done.[/J] (1:1).", bullet)
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual(1, result["taggedChangedRangeCount"])

    def test_whole_verse_jesus_tag_survives_only_space_run_change_without_source_wrapper(self) -> None:
        path = self.write_book(["[J]Jesus  said old words.[/J] (1:1)."])
        result = sync_snapshot(
            self.snapshot([self.verse("GEN.1.1", "Jesus\u00a0said old words.")]),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]Jesus\u00a0said old words.[/J] (1:1).", bullet)
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual(1, result["taggedChangedRangeCount"])

    def test_whole_verse_jesus_tag_drops_outer_unicode_space_without_source_wrapper(self) -> None:
        path = self.write_book(["[J]Jesus said these words.\u202f[/J] (1:1)."])
        result = sync_snapshot(
            self.snapshot([self.verse("GEN.1.1", "Jesus said these words.")]),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]Jesus said these words.[/J] (1:1).", bullet)
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual(1, result["taggedChangedRangeCount"])

    def test_whole_verse_jesus_tag_blocks_lexical_change_without_source_wrapper(self) -> None:
        path = self.write_book(["[J]Jesus said old wording.[/J] (1:1)."])
        before = path.read_bytes()
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.1", "Jesus said unrelated wording.")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_rendered_source_adds_jesus_and_addition_tags_to_untagged_local_text(self) -> None:
        path = self.write_book(["He said a supplied word. (1:1)."])
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__wj"><span class="x__content">He said a </span>'
                '<span class="x__add"><span class="x__content">supplied</span></span>'
                '<span class="x__content"> word.</span></span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]He said a [ADD]supplied[/ADD] word.[/J] (1:1).", bullet)
        self.assertEqual(0, result["sourceDifferenceRangeCount"])
        self.assertEqual(1, result["changedRangeCount"])

    def test_rendered_source_adds_dn_tag_without_changing_matching_publisher_text(self) -> None:
        path = self.write_book(["The LORD spoke. (1:1)."])
        result = sync_snapshot(
            self.snapshot([self.divine_record("The ", "LORD", " spoke.")]),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("The [DN]LORD[/DN] spoke. (1:1).", bullet)
        self.assertEqual(0, result["sourceDifferenceRangeCount"])
        self.assertEqual(1, result["changedRangeCount"])

    def test_publisher_nd_outer_bracket_stays_outside_divine_name_tag(self) -> None:
        path = self.write_book(["A [dem HERRN] heilig. (1:1)."], language="de")
        record = self.divine_record("A [dem ", "Herrn]", " heilig.")
        result = sync_snapshot(
            self.snapshot([record], language="de", bible_id=157, abbreviation="SCH2000"),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("A [dem [DN]Herrn[/DN]] heilig. (1:1).", bullet)
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])

    def test_locale_divine_name_lexemes_allow_only_outer_unicode_spacing_and_punctuation(self) -> None:
        cases = (
            ("en", "Lord", "spoke"),
            ("de", "Herrn", "sprach"),
            ("es", "Señor", "habló"),
            ("fr", "Éternel", "parla"),
            ("it", "Signore", "parlò"),
            ("pt", "Senhor", "falou"),
            ("ru", "Господень", "сказал"),
            ("ar", "الرَّبِّ", "تكلّم"),
            ("hi", "प्रभु", "ने कहा"),
            ("ja", "主", "は語った"),
            ("ko", "주님", "이 말했다"),
            ("zh-Hans", "上主", "说"),
            ("zh-Hant", "上主", "說"),
        )
        for language, name, ordinary_words in cases:
            with self.subTest(language=language):
                self.assertTrue(
                    _is_proven_source_divine_name(f"\u00a0«{name},»\u202f", language)
                )
                self.assertFalse(
                    _is_proven_source_divine_name(f"{name} {ordinary_words}", language)
                )

    def test_publisher_marked_gott_gottes_and_dieu_are_narrow_divine_name_lexemes(self) -> None:
        self.assertTrue(_is_proven_source_divine_name("Gott", "de"))
        self.assertTrue(_is_proven_source_divine_name("Gottes", "de"))
        self.assertTrue(_is_proven_source_divine_name("Dieu", "fr"))
        self.assertFalse(_is_proven_source_divine_name("Gott sprach", "de"))
        self.assertFalse(_is_proven_source_divine_name("Gottes, des Herrn", "de"))
        self.assertFalse(_is_proven_source_divine_name("Seigneur Dieu", "fr"))

    def test_nvi_exodus_self_identification_requires_exact_publisher_nd_span(self) -> None:
        self.assertTrue(_is_proven_source_divine_name("Yo soy el que soy", "es"))
        self.assertTrue(_is_proven_source_divine_name("Yo soy", "es"))
        self.assertFalse(_is_proven_source_divine_name("Yo soy el que soy Dios", "es"))
        self.assertFalse(_is_proven_source_divine_name("soy el que soy", "es"))

    def test_gott_gottes_and_dieu_are_never_inferred_without_rendered_nd(self) -> None:
        cases = (
            ("de", 157, "SCH2000", "Und Gott sprach zu ihnen."),
            ("de", 157, "SCH2000", "vor dem Angesicht Gottes, des Herrn!"),
            ("fr", 104, "NBS", "Seigneur Dieu, que me donneras-tu ?"),
        )
        original_root = self.root
        try:
            for index, (language, bible_id, abbreviation, text) in enumerate(cases):
                with self.subTest(language=language):
                    self.root = original_root / f"unmarked-publisher-dn-{index}"
                    path = self.write_book([f"{text} (1:1)."], language=language)
                    before = path.read_bytes()
                    result = sync_snapshot(
                        self.snapshot(
                            [self.verse("GEN.1.1", text)],
                            language=language,
                            bible_id=bible_id,
                            abbreviation=abbreviation,
                        ),
                        root=self.root,
                        apply=True,
                    )
                    self.assertEqual("match", result["status"])
                    self.assertEqual(before, path.read_bytes())
                    self.assertNotIn("[DN]", path.read_text(encoding="utf-8"))
        finally:
            self.root = original_root

    def test_broad_rendered_dn_span_blocks_in_every_supported_language(self) -> None:
        cases = (
            ("en", 3034, "BSB", "Lord spoke"),
            ("de", 157, "SCH2000", "Herr sprach"),
            ("es", 128, "NVI", "Señor habló"),
            ("fr", 104, "NBS", "Éternel parla"),
            ("it", 122, "NR06", "Signore parlò"),
            ("pt", 1930, "NVT", "Senhor falou"),
            ("ru", 143, "NRT", "Господь сказал"),
            ("ar", 153, "SAB", "الرَّبِّ تكلّم"),
            ("hi", 1980, "IRVHIN", "प्रभु ने कहा"),
            ("ja", 83, "JCB", "主は語った"),
            ("ko", 142, "RNKSV", "주님이 말했다"),
            ("zh-Hans", 36, "CCB", "上主说"),
            ("zh-Hant", 139, "RCUV", "上主說"),
        )
        original_root = self.root
        try:
            for index, (language, bible_id, abbreviation, broad_text) in enumerate(cases):
                with self.subTest(language=language):
                    self.root = original_root / f"broad-dn-{index}"
                    path = self.write_book(["Old wording. (1:1)."], language=language)
                    before = path.read_bytes()
                    with self.assertRaisesRegex(
                        DivineNameCoverageError, "unproven lexical content"
                    ):
                        sync_snapshot(
                            self.snapshot(
                                [self.divine_record("", broad_text, "")],
                                language=language,
                                bible_id=bible_id,
                                abbreviation=abbreviation,
                            ),
                            root=self.root,
                            apply=True,
                        )
                    self.assertEqual(before, path.read_bytes())
        finally:
            self.root = original_root

    def test_changed_partial_tag_without_source_semantic_wrapper_blocks(self) -> None:
        path = self.write_book(["Jesus [J]said old middle[/J] and done. (1:1)."])
        before = path.read_bytes()
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.1", "Jesus said new middle and done.")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_french_zs_spacing_only_preserves_j_and_add_without_source_wrappers(self) -> None:
        local_prefix = "[J]Jésus dit : « Allez ; [ADD]maintenant[/ADD] ! »[/J]"
        local_plain = "Jésus dit : « Allez ; maintenant ! »"
        source_plain = local_plain.replace(" ", "\u00a0").replace("\u00a0;", "\u202f;")
        expected_prefix = local_prefix.replace(" ", "\u00a0").replace("\u00a0;", "\u202f;")
        path = self.write_book([local_prefix + " (1:1)."], language="fr")
        result = sync_snapshot(
            self.snapshot(
                [self.verse("GEN.1.1", source_plain)],
                language="fr", bible_id=104, abbreviation="NBS",
            ),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual(expected_prefix + " (1:1).", bullet)
        self.assertEqual(1, result["changedRangeCount"])
        self.assertEqual(1, result["taggedChangedRangeCount"])
        self.assertEqual(4, result["preservedTagBoundaryCount"])

    def test_missing_wrappers_do_not_relax_punctuation_word_or_case_changes(self) -> None:
        cases = (
            "Jésus\u00a0dit\u00a0:\u00a0«\u00a0Allez\u00a0?\u00a0»",
            "Jésus\u00a0dit\u00a0:\u00a0«\u00a0Partez\u00a0!\u00a0»",
            "JÉSUS\u00a0dit\u00a0:\u00a0«\u00a0Allez\u00a0!\u00a0»",
        )
        for index, source_plain in enumerate(cases):
            with self.subTest(source_plain=source_plain):
                case_root = self.root / f"tagged-lexical-{index}"
                original_root = self.root
                self.root = case_root
                try:
                    path = self.write_book(
                        ["Jésus dit : « [J]Allez[/J] ! » (1:1)."], language="fr"
                    )
                    before = path.read_bytes()
                    with self.assertRaises(ScriptureSyncError):
                        sync_snapshot(
                            self.snapshot(
                                [self.verse("GEN.1.1", source_plain)],
                                language="fr", bible_id=104, abbreviation="NBS",
                            ),
                            root=self.root,
                            apply=True,
                        )
                    self.assertEqual(before, path.read_bytes())
                finally:
                    self.root = original_root

    def test_source_semantic_boundary_move_uses_rendered_wrapper(self) -> None:
        path = self.write_book(["Start [J]words stay[/J] End. (1:1)."])
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__content">Start </span>'
                '<span class="x__wj"><span class="x__content">words stay changed</span></span>'
                '<span class="x__content"> End.</span></span>'
            ),
        }
        sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("Start [J]words stay changed[/J] End. (1:1).", bullet)

    def test_rendered_semantic_wrapper_terminal_space_matches_plain_source(self) -> None:
        path = self.write_book(["[J]Old words.[/J] (1:1)."])
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__wj"><span class="x__content">New words. </span></span>'
                '<span class="x__content">  </span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]New words.[/J] (1:1).", bullet)
        self.assertEqual("changed", result["status"])

    def test_split_rendered_semantic_wrappers_collapse_only_boundary_spaces(self) -> None:
        path = self.write_book(["[J]Old words.[/J] (1:1)."])
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__wj"><span class="x__content">New words </span></span>'
                '<span class="x__note">publisher note</span>'
                '<span class="x__content">  </span>'
                '<span class="x__wj"><span class="x__content">and more. </span></span>'
                '<span class="x__content">  </span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]New words[/J] [J]and more.[/J] (1:1).", bullet)
        self.assertEqual("changed", result["status"])

    def test_rendered_wrapper_resolves_repeated_literal_ambiguity(self) -> None:
        path = self.write_book(["x a [J]b[/J] c x a b c old. (1:1)."])
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__content">x a </span><span class="x__wj">b</span>'
                '<span class="x__content"> c x a b c new.</span></span>'
            ),
        }
        sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("x a [J]b[/J] c x a b c new. (1:1).", bullet)

    def test_divine_name_tag_requires_rendered_nd_boundary(self) -> None:
        path = self.write_book(["The [DN]LORD[/DN] spoke old words. (1:1)."])
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__content">The </span><span class="x__nd"><span class="x__content">LORD</span></span>'
                '<span class="x__content"> spoke new words.</span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("The [DN]LORD[/DN] spoke new words. (1:1).", bullet)
        self.assertEqual("changed", result["status"])

    def test_german_divine_names_keep_publisher_casing_inside_dn_tags(self) -> None:
        cases = (
            ("Der HERR sprach alte Worte. (1:1).", "Der ", "Herr", " sprach neue Worte.", "HERR"),
            ("Er gehorchte dem HERRN in alter Weise. (1:1).", "Er gehorchte dem ", "Herrn", " in neuer Weise.", "HERRN"),
        )
        for index, (local, before, source_dn, after, sentinel) in enumerate(cases):
            with self.subTest(sentinel=sentinel):
                case_root = self.root / str(index)
                original_root = self.root
                self.root = case_root
                try:
                    path = self.write_book([local], language="de")
                    result = sync_snapshot(
                        self.snapshot(
                            [self.divine_record(before, source_dn, after)],
                            language="de", bible_id=157, abbreviation="SCH2000",
                        ),
                        root=self.root,
                        apply=True,
                    )
                    bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
                    self.assertEqual(
                        f"{before}[DN]{source_dn}[/DN]{after} (1:1).",
                        bullet,
                    )
                    self.assertNotIn(sentinel, bullet)
                    self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])
                finally:
                    self.root = original_root

    def test_dn_presentation_change_writes_exact_source_casing_and_wrapper(self) -> None:
        path = self.write_book([
            "Der HERR sprach. (1:1).",
            "Dann ging er mit alten Worten fort. (1:2).",
        ], language="de")
        before = path.read_text(encoding="utf-8")
        records = [
            self.divine_record("Der ", "Herr", " sprach."),
            self.verse("GEN.1.2", "Dann ging er mit neuen Worten fort."),
        ]
        result = sync_snapshot(
            self.snapshot(records, language="de", bible_id=157, abbreviation="SCH2000"),
            root=self.root,
            apply=True,
        )
        after = path.read_text(encoding="utf-8")
        bullets = json.loads(after)["stories"][0]["summaryBullets"]
        self.assertEqual("Der [DN]Herr[/DN] sprach. (1:1).", bullets[0])
        self.assertEqual("Dann ging er mit neuen Worten fort. (1:2).", bullets[1])
        self.assertIn('"Der HERR sprach. (1:1)."', before)
        self.assertIn('"Der [DN]Herr[/DN] sprach. (1:1)."', after)
        self.assertEqual("changed", result["status"])
        self.assertEqual(2, result["sourceDifferenceRangeCount"])
        self.assertEqual(0, result["preservedPresentationOnlyRangeCount"])
        self.assertEqual(2, result["changedRangeCount"])
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])

    def test_spanish_divine_name_keeps_publisher_casing_inside_dn_tag(self) -> None:
        path = self.write_book(["El SEÑOR habló ayer. (1:1)."], language="es")
        result = sync_snapshot(
            self.snapshot(
                [self.divine_record("El ", "Señor", " habló hoy.")],
                language="es", bible_id=128, abbreviation="NVI",
            ),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("El [DN]Señor[/DN] habló hoy. (1:1).", bullet)
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])

    def test_nvi_exodus_two_publisher_marked_self_identification_spans_match(self) -> None:
        source = "[DN]Yo soy el que soy[/DN] y [DN]Yo soy[/DN] me ha enviado."
        self.write_book([f"{source} (1:1)."], language="es")
        record = {
            "kind": "verse",
            "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1">'
                '<span class="x__label">1</span>'
                '<span class="x__nd"><span class="x__content">Yo soy el que soy</span></span>'
                '<span class="x__content"> y </span>'
                '<span class="x__nd"><span class="x__content">Yo soy</span></span>'
                '<span class="x__content"> me ha enviado.</span></span>'
            ),
        }
        result = sync_snapshot(
            self.snapshot([record], language="es", bible_id=128, abbreviation="NVI"),
            root=self.root,
            apply=True,
        )
        self.assertEqual("match", result["status"])

    def test_french_divine_name_keeps_publisher_casing_inside_dn_tag(self) -> None:
        path = self.write_book(["L’ÉTERNEL parla hier. (1:1)."], language="fr")
        result = sync_snapshot(
            self.snapshot(
                [self.divine_record("L’", "Éternel", " parla aujourd’hui.")],
                language="fr", bible_id=104, abbreviation="NBS",
            ),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("L’[DN]Éternel[/DN] parla aujourd’hui. (1:1).", bullet)
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])

    def test_runtime_divine_name_count_ambiguity_blocks_without_write(self) -> None:
        path = self.write_book(["Der HERR sprach und der HERR antwortete alt. (1:1)."], language="de")
        before_bytes = path.read_bytes()
        with self.assertRaises(DivineNameCoverageError):
            sync_snapshot(
                self.snapshot(
                    [self.divine_record("Der ", "Herr", " antwortete neu.")],
                    language="de", bible_id=157, abbreviation="SCH2000",
                ),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before_bytes, path.read_bytes())

    def test_all_publisher_marked_divine_name_candidates_resolve_runtime_ambiguity(self) -> None:
        path = self.write_book(["Der HERR spricht. (1:1)."], language="de")
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__content">Der </span><span class="x__nd">Herr</span>'
                '<span class="x__content"> und der </span><span class="x__nd">Herr</span>'
                '<span class="x__content"> sprechen.</span></span>'
            ),
        }
        result = sync_snapshot(
            self.snapshot([record], language="de", bible_id=157, abbreviation="SCH2000"),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual(
            "Der [DN]Herr[/DN] und der [DN]Herr[/DN] sprechen. (1:1).",
            bullet,
        )
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])

    def test_repeated_exact_unmarked_runtime_token_is_ambiguous_and_blocks(self) -> None:
        path = self.write_book(["Signore parla ancora. (1:1)."], language="it")
        before_bytes = path.read_bytes()
        with self.assertRaisesRegex(
            DivineNameCoverageError, "runtime divine-name source alignment is ambiguous"
        ):
            sync_snapshot(
                self.snapshot(
                    [self.verse("GEN.1.1", "Signore e Signore parlano ora.")],
                    language="it", bible_id=122, abbreviation="NR06",
                ),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before_bytes, path.read_bytes())

    def test_runtime_divine_name_non_equivalence_blocks_without_write(self) -> None:
        path = self.write_book(["Der HERR sprach alt. (1:1)."], language="de")
        before_bytes = path.read_bytes()
        with self.assertRaises(DivineNameCoverageError):
            sync_snapshot(
                self.snapshot(
                    [self.divine_record("Gott, der ", "Allmächtige", ", sprach neu.")],
                    language="de", bible_id=157, abbreviation="SCH2000",
                ),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before_bytes, path.read_bytes())

    def test_runtime_sentinel_without_rendered_nd_evidence_blocks(self) -> None:
        path = self.write_book(["Der HERR sprach alt. (1:1)."], language="de")
        before_bytes = path.read_bytes()
        with self.assertRaises(DivineNameCoverageError):
            sync_snapshot(
                self.snapshot(
                    [self.verse("GEN.1.1", "Der Herr sprach neu.")],
                    language="de", bible_id=157, abbreviation="SCH2000",
                ),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before_bytes, path.read_bytes())

    def test_exact_hash_reviewed_native_chapter_can_replace_shifted_runtime_sentinel(self) -> None:
        path = self.write_book(["Der HERR steht im alten Vers. (1:1)."], language="de")
        story = json.loads(path.read_text(encoding="utf-8"))["stories"][0]
        row = {
            "collection": "old_testament",
            "bookId": "genesis",
            "expectedLocalRangeCount": 1,
            "expectedLocalStorySha256": _sha_json(story),
            "expectedLocalAppTagOpenCounts": {"J": 0, "ADD": 0, "DN": 0},
        }
        with mock.patch(
            "sync_modern_scripture_browser.REVIEWED_NATIVE_CHAPTER_REPLACEMENTS",
            {("de", "GEN", 1): row},
        ):
            result = sync_snapshot(
                self.snapshot(
                    [self.verse("GEN.1.1", "Die geprüfte Quelle steht hier.")],
                    language="de", bible_id=157, abbreviation="SCH2000",
                ),
                root=self.root,
                apply=True,
            )
        self.assertEqual("changed", result["status"])
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("Die geprüfte Quelle steht hier. (1:1).", bullet)

    def test_changed_story_hash_cannot_reuse_reviewed_native_replacement(self) -> None:
        path = self.write_book(["Der HERR steht im alten Vers. (1:1)."], language="de")
        before = path.read_bytes()
        row = {
            "collection": "old_testament",
            "bookId": "genesis",
            "expectedLocalRangeCount": 1,
            "expectedLocalStorySha256": "0" * 64,
            "expectedLocalAppTagOpenCounts": {"J": 0, "ADD": 0, "DN": 0},
        }
        with mock.patch(
            "sync_modern_scripture_browser.REVIEWED_NATIVE_CHAPTER_REPLACEMENTS",
            {("de", "GEN", 1): row},
        ), self.assertRaises(DivineNameCoverageError):
            sync_snapshot(
                self.snapshot(
                    [self.verse("GEN.1.1", "Die ungeprüfte Quelle steht hier.")],
                    language="de", bible_id=157, abbreviation="SCH2000",
                ),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_functional_coverage_block_is_sanitized_in_jsonl_result(self) -> None:
        self.write_book(["Der HERR sprach alt. (1:1)."], language="de")
        snapshot = self.snapshot(
            [self.verse("GEN.1.1", "Der Herr sprach neu.")],
            language="de", bible_id=157, abbreviation="SCH2000",
        )
        sink = io.StringIO()
        status = stream_snapshots(io.StringIO(json.dumps(snapshot) + "\n"), sink, root=self.root, apply=True)
        result = json.loads(sink.getvalue())
        self.assertEqual(2, status)
        self.assertEqual("blocked", result["status"])
        self.assertEqual("DivineNameCoverageError", result["errorType"])
        self.assertEqual(
            "runtime_divine_name_span_has_no_safe_source_alignment",
            result["errorCode"],
        )
        self.assertNotIn("HERR", sink.getvalue())
        self.assertNotIn("Herr", sink.getvalue())

    def test_exact_source_divine_name_is_nested_inside_existing_jesus_tag(self) -> None:
        path = self.write_book(["[J]Der HERR sprach alte Worte.[/J] (1:1)."], language="de")
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__wj"><span class="x__content">Der </span>'
                '<span class="x__nd"><span class="x__content">Herr</span></span>'
                '<span class="x__content"> sprach neue Worte.</span></span></span>'
            ),
        }
        result = sync_snapshot(
            self.snapshot([record], language="de", bible_id=157, abbreviation="SCH2000"),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]Der [DN]Herr[/DN] sprach neue Worte.[/J] (1:1).", bullet)
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])

    def test_exact_italian_runtime_token_survives_unmarked_superscription_prefix(self) -> None:
        path = self.write_book(["Signore parla ancora. (1:1)."], language="it")
        records = [
            {
                "kind": "verse", "usfm": "GEN.1.1",
                "html": (
                    '<span class="x__verse" data-usfm="GEN.1.1">'
                    '<span class="x__label">1</span>'
                    '<span class="x__content">Titolo del salmo.</span></span>'
                ),
            },
            {
                "kind": "verse", "usfm": "GEN.1.1",
                "html": (
                    '<span class="x__verse" data-usfm="GEN.1.1">'
                    '<span class="x__content">Signore parla ora.</span></span>'
                ),
            },
        ]
        result = sync_snapshot(
            self.snapshot(records, language="it", bible_id=122, abbreviation="NR06"),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("Titolo del salmo. Signore parla ora. (1:1).", bullet)
        self.assertEqual(1, result["changedRangeCount"])
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])

    def test_real_legacy_root_and_story_id_conventions_are_accepted(self) -> None:
        cases = (
            ("1_samuel", "1-samuel", "1SA"),
            ("1_chronicles", "1chronicles", "1CH"),
            ("song_of_songs", "song-of-songs", "SNG"),
        )
        for index, (book_id, root_id, code) in enumerate(cases):
            with self.subTest(root_id=root_id):
                case_root = self.root / f"legacy-{index}"
                original_root = self.root
                self.root = case_root
                try:
                    path = self.write_book(
                        ["Exact wording. (1:1)."], book_id=book_id, root_id=root_id
                    )
                    result = sync_snapshot(
                        self.snapshot([self.verse(f"{code}.1.1", "Exact wording.")], book=code),
                        root=self.root,
                    )
                    self.assertEqual("match", result["status"])
                    self.assertTrue(path.exists())
                finally:
                    self.root = original_root

    def test_blank_root_and_duplicate_exact_target_story_fail_closed(self) -> None:
        for index, mutation in enumerate(("blank", "duplicate")):
            with self.subTest(mutation=mutation):
                case_root = self.root / f"invalid-root-{index}"
                original_root = self.root
                self.root = case_root
                try:
                    path = self.write_book(["Exact wording. (1:1)."])
                    payload = json.loads(path.read_text(encoding="utf-8"))
                    if mutation == "blank":
                        payload["id"] = " "
                    else:
                        payload["stories"].append(dict(payload["stories"][0]))
                    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
                    before = path.read_bytes()
                    with self.assertRaises(ScriptureSyncError):
                        sync_snapshot(
                            self.snapshot([self.verse("GEN.1.1", "New wording.")]),
                            root=self.root,
                            apply=True,
                        )
                    self.assertEqual(before, path.read_bytes())
                finally:
                    self.root = original_root

    def test_wrong_book_payload_root_fails_closed_even_with_matching_story_id(self) -> None:
        path = self.write_book(["Old wording. (1:1)."], root_id="exodus")
        before = path.read_bytes()
        with self.assertRaisesRegex(ScriptureSyncError, "requested book"):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.1", "New wording.")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_addition_tag_requires_rendered_add_boundary(self) -> None:
        path = self.write_book(["This [ADD]word[/ADD] was old. (1:1)."])
        record = {
            "kind": "verse", "usfm": "GEN.1.1",
            "html": (
                '<span class="x__verse" data-usfm="GEN.1.1"><span class="x__label">1</span>'
                '<span class="x__content">This </span><span class="translator-addition">word</span>'
                '<span class="x__content"> was new.</span></span>'
            ),
        }
        sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("This [ADD]word[/ADD] was new. (1:1).", bullet)

    def test_malformed_crossing_local_tags_block(self) -> None:
        path = self.write_book(["[J]bad [DN]tags[/J][/DN] old. (1:1)."])
        before = path.read_bytes()
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.1", "bad tags new.", semantic_class="x__wj")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_literal_app_control_tag_in_source_blocks(self) -> None:
        path = self.write_book(["Old. (1:1)."])
        before = path.read_bytes()
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.1", "Literal [J] value.")]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_jsonl_failure_is_sanitized_and_never_echoes_source(self) -> None:
        self.write_book(["One. (1:1)."])
        malicious = "SECRET_PUBLISHER_TEXT"
        source = io.StringIO(json.dumps({"language": "en", "records": malicious}) + "\n")
        sink = io.StringIO()
        self.assertEqual(2, stream_snapshots(source, sink, root=self.root))
        self.assertNotIn(malicious, sink.getvalue())
        self.assertEqual("blocked", json.loads(sink.getvalue())["status"])

    def test_malformed_publisher_json_never_becomes_error_code_or_output(self) -> None:
        malicious = "SECRET_PUBLISHER_TEXT_MALFORMED"
        sink = io.StringIO()
        status = stream_snapshots(
            io.StringIO('{"records":"' + malicious + '"\n'), sink, root=self.root
        )
        result = json.loads(sink.getvalue())
        self.assertEqual(2, status)
        self.assertEqual("blocked", result["status"])
        self.assertEqual("JSONDecodeError", result["errorType"])
        self.assertNotIn("errorCode", result)
        self.assertNotIn(malicious, sink.getvalue())

    def test_loopback_server_returns_sanitized_block_and_no_store(self) -> None:
        self.write_book(["One. (1:1)."])
        server = create_snapshot_server(0, root=self.root)
        self.assertEqual("127.0.0.1", server.server_address[0])
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            payload = json.dumps({"records": "SECRET_PUBLISHER_TEXT"}).encode("utf-8")
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5)
            connection.request("POST", "/snapshot", body=payload, headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            body = response.read().decode("utf-8")
            self.assertEqual("no-store", response.getheader("Cache-Control"))
            self.assertEqual(200, response.status)
            self.assertNotIn("SECRET_PUBLISHER_TEXT", body)
            result = json.loads(body)
            self.assertEqual("blocked", result["status"])
            self.assertEqual("unknown_language_book_or_chapter", result["errorCode"])

            malformed_secret = "MALFORMED_PUBLISHER_SECRET"
            malformed = ('{"records":"' + malformed_secret + '"').encode("utf-8")
            connection.request("POST", "/snapshot", body=malformed, headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            malformed_body = response.read().decode("utf-8")
            malformed_result = json.loads(malformed_body)
            self.assertEqual("JSONDecodeError", malformed_result["errorType"])
            self.assertNotIn("errorCode", malformed_result)
            self.assertNotIn(malformed_secret, malformed_body)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_heading_records_do_not_change_scripture_prefix(self) -> None:
        path = self.write_book(["Old. (1:1)."])
        records = [
            {"kind": "heading", "html": '<span class="x__heading">Publisher heading</span>'},
            self.verse("GEN.1.1", "New."),
        ]
        sync_snapshot(self.snapshot(records), root=self.root, apply=True)
        payload = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual([], payload["stories"][0]["headings"])
        self.assertEqual("New. (1:1).", payload["stories"][0]["summaryBullets"][0])


if __name__ == "__main__":
    unittest.main()
