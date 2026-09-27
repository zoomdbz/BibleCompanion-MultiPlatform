from __future__ import annotations

import http.client
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock

import sync_modern_range_merges_browser as range_merge
import reviewed_arabic_repairs as arabic_review
from compare_modern_editions_browser import BrowserAuditError
from sync_modern_range_merges_browser import (
    RangeMergeError, _reviewed_terminal_dn_presentation_prefix,
    _verified_terminal_duplicate_prefix, create_snapshot_server,
    stream_snapshots, sync_snapshot,
)
from sync_modern_scripture_browser import _parse_tagged, _sha_bytes


class ModernRangeMergeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_book(self, bullets: list[str], *, language: str = "ja",
                   headings: list[dict[str, object]] | None = None) -> Path:
        payload = {
            "id": "genesis", "title": "Genesis", "stories": [{
                "id": "genesis-1", "summaryBullets": bullets,
                "headings": headings or [],
                "crossRefs": ["Keep this (1:3)."],
            }], "sentinel": "unchanged",
        }
        path = self.root / "shared/assets/books/old_testament" / language / "genesis.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return path

    @staticmethod
    def verse(usfm: str, text: str, *, label: str = "3") -> dict[str, str]:
        return {
            "kind": "verse", "usfm": usfm,
            "html": f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">{label}</span><span class="x__content">{text}</span></span>',
        }

    @staticmethod
    def snapshot(records: list[dict[str, str]], *, language: str = "ja",
                 bible_id: int = 83, abbreviation: str = "JCB", **changes) -> dict[str, object]:
        value: dict[str, object] = {
            "language": language, "bibleId": bible_id, "book": "GEN", "chapter": 1,
            "pageUrl": f"https://www.bible.com/bible/{bible_id}/GEN.1.{abbreviation}",
            "pageTitle": "Genesis 1", "records": records,
        }
        value.update(changes)
        return value

    def test_merge_preserves_other_bytes_and_native_range(self) -> None:
        path = self.write_book(["前半。 (1:3).", "後半。 (1:4).", "次。 (1:5)."])
        before = path.read_text(encoding="utf-8")
        snapshot = self.snapshot([
            self.verse("GEN.1.3+GEN.1.4", "前半。 後半。"),
            self.verse("GEN.1.5", "次。", label="5"),
        ])
        audit = sync_snapshot(snapshot, root=self.root)
        self.assertEqual("changed", audit["status"])
        self.assertEqual("audit", audit["mode"])
        self.assertEqual(before, path.read_text(encoding="utf-8"))
        result = sync_snapshot(snapshot, root=self.root, apply=True)
        after = path.read_text(encoding="utf-8")
        self.assertEqual(1, result["mergedGroupCount"])
        self.assertEqual(1, result["removedBulletCount"])
        self.assertEqual(["前半。 後半。 (1:3-4).", "次。 (1:5)."],
                         json.loads(after)["stories"][0]["summaryBullets"])
        self.assertIn('"crossRefs": [\n        "Keep this (1:3)."', after)
        self.assertIn('"次。 (1:5)."', after)
        self.assertEqual("match", sync_snapshot(snapshot, root=self.root, apply=True)["status"])

    def test_unicode_zs_and_balanced_tags_are_preserved(self) -> None:
        path = self.write_book(["[J]Mot[/J] : (1:3).", "[ADD]texte[/ADD]. (1:4)."], language="fr")
        sync_snapshot(self.snapshot(
            [self.verse("GEN.1.3-4", "Mot\u00a0: texte.")],
            language="fr", bible_id=104, abbreviation="NBS",
        ), root=self.root, apply=True)
        bullets = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"]
        self.assertEqual(["[J]Mot[/J]\u00a0: [ADD]texte[/ADD]. (1:3-4)."], bullets)

    def test_matching_visible_text_reconstructs_rendered_semantic_spans(self) -> None:
        path = self.write_book(["[J]Jesus[/J] truly (1:3).", "spoke. (1:4)."], language="en")
        usfm = "GEN.1.3-4"
        record = {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">3</span>'
                '<span class="x__wj">Jesus <span class="x__add">truly</span> spoke.</span></span>'
            ),
        }
        result = sync_snapshot(
            self.snapshot([record], language="en", bible_id=3034, abbreviation="BSB"),
            root=self.root, apply=True,
        )
        self.assertEqual(1, result["semanticVerifiedGroupCount"])
        self.assertEqual(1, result["sourceSyncedGroupCount"])
        self.assertEqual(
            ["[J]Jesus [ADD]truly[/ADD] spoke.[/J] (1:3-4)."],
            json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"],
        )

    def test_japanese_source_without_interverse_space_keeps_no_separator(self) -> None:
        path = self.write_book(["前半。 (1:3).", "後半。 (1:4)."])
        result = sync_snapshot(self.snapshot([
            self.verse("GEN.1.3-4", "前半。後半。"),
        ]), root=self.root, apply=True)
        self.assertEqual("changed", result["status"])
        self.assertEqual(["前半。後半。 (1:3-4)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_jcb_psalm_merge_and_source_replacement(self) -> None:
        payload = {
            "id": "psalms", "stories": [{"id": "psalms-102", "summaryBullets": [
                "古い言葉。 (102:3).", "続く。 (102:4).",
            ], "headings": []}],
        }
        path = self.root / "shared/assets/books/old_testament/ja/psalms.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        snapshot = self.snapshot([
            self.verse("PSA.102.3-4", "新しい言葉。続く。"),
        ], book="PSA", chapter=102, pageUrl="https://www.bible.com/bible/83/PSA.102.JCB",
           pageTitle="Psalms 102")
        result = sync_snapshot(snapshot, root=self.root, apply=True)
        self.assertEqual(1, result["sourceSyncedGroupCount"])
        self.assertEqual(["新しい言葉。続く。 (102:3-4)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_merge_also_syncs_a_separate_unmerged_unit_from_rendered_source(self) -> None:
        path = self.write_book([
            "前半。 (1:3).", "後半。 (1:4).", "古い言葉。 (1:5).",
        ])
        before = path.read_text(encoding="utf-8")
        result = sync_snapshot(self.snapshot([
            self.verse("GEN.1.3+GEN.1.4", "前半。後半。"),
            self.verse("GEN.1.5", "新しい言葉。", label="5"),
        ]), root=self.root, apply=True)
        after = path.read_text(encoding="utf-8")
        self.assertEqual(1, result["sourceSyncedGroupCount"])
        self.assertEqual(2, result["semanticVerifiedGroupCount"])
        self.assertEqual([
            "前半。後半。 (1:3-4).", "新しい言葉。 (1:5).",
        ], json.loads(after)["stories"][0]["summaryBullets"])
        self.assertIn('"crossRefs": [\n        "Keep this (1:3)."', after)
        self.assertNotEqual(before, after)

    def test_unmerged_local_j_tag_requires_rendered_confirmation(self) -> None:
        path = self.write_book([
            "前半。 (1:3).", "後半。 (1:4).", "[J]古い言葉[/J]。 (1:5).",
        ])
        before = path.read_bytes()
        with self.assertRaises(RangeMergeError):
            sync_snapshot(self.snapshot([
                self.verse("GEN.1.3+GEN.1.4", "前半。後半。"),
                self.verse("GEN.1.5", "新しい言葉。", label="5"),
            ]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_unmerged_rendered_j_tag_is_reconstructed(self) -> None:
        path = self.write_book([
            "前半。 (1:3).", "後半。 (1:4).", "[J]古い言葉[/J]。 (1:5).",
        ])
        usfm = "GEN.1.5"
        tagged = {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">5</span>'
                '<span class="x__wj">新しい言葉</span><span class="x__content">。</span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([
            self.verse("GEN.1.3+GEN.1.4", "前半。後半。"), tagged,
        ]), root=self.root, apply=True)
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual([
            "前半。後半。 (1:3-4).", "[J]新しい言葉[/J]。 (1:5).",
        ], json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_arabic_merge_and_source_replacement(self) -> None:
        path = self.write_book(["النص القديم. (1:3).", "تابع. (1:4)."], language="ar")
        result = sync_snapshot(self.snapshot([
            self.verse("GEN.1.3+GEN.1.4", "النص الجديد تابع."),
        ], language="ar", bible_id=153, abbreviation="SAB"), root=self.root, apply=True)
        self.assertEqual(1, result["sourceSyncedGroupCount"])
        self.assertEqual(["النص الجديد تابع. (1:3-4)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_complete_terminal_unit_collapses_duplicate_range_without_joining(self) -> None:
        path = self.write_book([
            "stale partial text (1:3).",
            "[J]Complete rendered bridge.[/J] (1:4).",
        ], language="en")
        usfm = "GEN.1.3-4"
        record = {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">3</span>'
                '<span class="x__wj">Complete rendered bridge.</span></span>'
            ),
        }
        result = sync_snapshot(
            self.snapshot([record], language="en", bible_id=3034, abbreviation="BSB"),
            root=self.root, apply=True,
        )
        self.assertEqual(1, result["terminalCollapsedGroupCount"])
        self.assertEqual(0, result["sourceSyncedGroupCount"])
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual(
            ["[J]Complete rendered bridge.[/J] (1:3-4)."],
            json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"],
        )

    def test_terminal_duplicate_proof_rejects_wording_or_tag_reconstruction(self) -> None:
        exact = "[J]Complete rendered bridge.[/J]"
        kinds = {
            "J": _parse_tagged("[J]Complete rendered bridge.[/J]"),
            "ADD": _parse_tagged("Complete rendered bridge."),
            "DN": _parse_tagged("Complete rendered bridge."),
        }
        self.assertEqual(
            (exact, 2, 0),
            _verified_terminal_duplicate_prefix(
                ["stale (1:3).", exact + " (1:4)."],
                "Complete rendered bridge.", "Complete rendered bridge.",
                kinds, "en", "old_testament", "GEN.1.3-4",
            ),
        )
        changed = "Complete altered bridge."
        changed_kinds = {kind: _parse_tagged(changed) for kind in ("J", "ADD", "DN")}
        self.assertIsNone(_verified_terminal_duplicate_prefix(
            ["stale (1:3).", exact + " (1:4)."], changed, changed,
            changed_kinds, "en", "old_testament", "GEN.1.3-4",
        ))
        unmarked_kinds = {
            kind: _parse_tagged("Complete rendered bridge.")
            for kind in ("J", "ADD", "DN")
        }
        # The existing identity rule may preserve a local-only J span, so this
        # remains a valid exact terminal proof. It never changes its boundary.
        self.assertEqual(
            (exact, 2, 0),
            _verified_terminal_duplicate_prefix(
                ["stale (1:3).", exact + " (1:4)."],
                "Complete rendered bridge.", "Complete rendered bridge.",
                unmarked_kinds, "en", "old_testament", "GEN.1.3-4",
            ),
        )

    def test_reviewed_duplicate_chapter_never_falls_through_to_source_join(self) -> None:
        path = self.write_book([
            "stale partial text (1:3).",
            "Complete old bridge. (1:4).",
        ])
        payload = json.loads(path.read_text(encoding="utf-8"))
        story_sha = range_merge._sha_json(payload["stories"][0])
        source_ranges_sha = range_merge._sha_json([(3, 4)])
        reviewed = {("ja", "GEN", 1): (story_sha, source_ranges_sha)}
        before = path.read_bytes()
        with mock.patch.dict(
                range_merge.REVIEWED_TERMINAL_DUPLICATE_CHAPTERS, reviewed, clear=True):
            with self.assertRaisesRegex(
                    RangeMergeError, "reviewed terminal duplicate group differs"):
                sync_snapshot(self.snapshot([
                    self.verse("GEN.1.3-4", "Complete new bridge."),
                ]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_reviewed_arabic_terminal_divine_presentation_is_exact_and_local(self) -> None:
        terminal = "قال يَهوَهْ."
        source = "قال اللهُ."

        def row(source_text: str) -> dict[str, str]:
            return {
                "expectedStorySha256": "story",
                "expectedSourceRangesSha256": "ranges",
                "expectedLocalPrefixSha256": _sha_bytes(terminal.encode("utf-8")),
                "expectedSourcePrefixSha256": _sha_bytes(source_text.encode("utf-8")),
                "localToken": "يَهوَهْ",
                "sourceToken": "اللهُ",
            }

        plain_semantics = {kind: _parse_tagged(source) for kind in ("J", "ADD", "DN")}
        key = ("ar", "NUM", 1, (17, 19))
        proof = _reviewed_terminal_dn_presentation_prefix(
            language="ar", code="NUM", chapter=1, collection="old_testament",
            source_unit=(17, 19), terminal=terminal, source_plain=source,
            semantics=plain_semantics, story_sha="story", source_ranges_sha="ranges",
            reviewed={key: row(source)},
        )
        self.assertEqual((terminal, 0, 1), proof)

        altered = source + " حقا."
        altered_semantics = {kind: _parse_tagged(altered) for kind in ("J", "ADD", "DN")}
        self.assertIsNone(_reviewed_terminal_dn_presentation_prefix(
            language="ar", code="NUM", chapter=1, collection="old_testament",
            source_unit=(17, 19), terminal=terminal, source_plain=altered,
            semantics=altered_semantics, story_sha="story", source_ranges_sha="ranges",
            reviewed={key: row(altered)},
        ))
        marked_semantics = dict(plain_semantics)
        marked_semantics["DN"] = _parse_tagged("قال [DN]اللهُ[/DN].")
        self.assertIsNone(_reviewed_terminal_dn_presentation_prefix(
            language="ar", code="NUM", chapter=1, collection="old_testament",
            source_unit=(17, 19), terminal=terminal, source_plain=source,
            semantics=marked_semantics, story_sha="story", source_ranges_sha="ranges",
            reviewed={key: row(source)},
        ))

    def test_reviewed_arabic_atomic_repair_updates_text_headings_and_replays(self) -> None:
        source_plain = "قال اللهُ."
        tagged = "قال [DN]اللهُ[/DN]."
        local_terminal = "قال يَهوَهْ."
        path = self.write_book(
            ["قديم. (1:1).", local_terminal + " (1:2)."],
            language="ar", headings=[{"beforeVerse": 1, "text": "عنوان قديم"}],
        )
        payload = json.loads(path.read_text(encoding="utf-8"))
        pre_story = payload["stories"][0]
        target_headings = [{"beforeVerse": 1, "text": "عنوان المصدر"}]
        post_story = json.loads(json.dumps(pre_story, ensure_ascii=False))
        post_story["summaryBullets"] = [tagged + " (1:1-2)."]
        post_story["headings"] = target_headings
        chapter_key = ("ar", "GEN", 1)
        range_key = ("ar", "GEN", 1, (1, 2))
        offset = source_plain.index("اللهُ")
        chapter_row = {
            "preStorySha256": arabic_review.digest_json(pre_story),
            "postStorySha256": arabic_review.digest_json(post_story),
            "sourceRangesSha256": arabic_review.digest_json([(1, 2)]),
            "sourceTextSha256": arabic_review.digest_json([((1, 2), source_plain)]),
            "localHeadingsSha256": arabic_review.digest_json(pre_story["headings"]),
            "sourceHeadingsSha256": arabic_review.digest_json(target_headings),
            "postDivineNameSpanCount": 1,
        }
        range_row = {
            "localPrefixSha256": arabic_review.digest_text(local_terminal),
            "sourcePrefixSha256": arabic_review.digest_text(source_plain),
            "taggedPrefixSha256": arabic_review.digest_text(tagged),
            "spans": ((offset, offset + len("اللهُ"), "اللهُ"),),
        }
        records = [
            {"kind": "heading", "html": '<h3 class="heading">عنوان المصدر</h3>'},
            self.verse("GEN.1.1-2", source_plain, label="1"),
        ]
        snapshot = self.snapshot(
            records, language="ar", bible_id=153, abbreviation="SAB",
        )
        with mock.patch.dict(
                range_merge.REVIEWED_ARABIC_CHAPTERS, {chapter_key: chapter_row}, clear=True), \
                mock.patch.dict(
                    arabic_review.REVIEWED_ARABIC_DN_RANGES,
                    {range_key: range_row}, clear=True,
                ):
            result = sync_snapshot(snapshot, root=self.root, apply=True)
            self.assertEqual("changed", result["status"])
            self.assertEqual("pre", result["reviewedArabicAtomicState"])
            self.assertEqual(1, result["reviewedArabicDivineNameGroupCount"])
            self.assertEqual(1, result["terminalCollapsedGroupCount"])
            self.assertEqual(post_story, json.loads(path.read_text(encoding="utf-8"))["stories"][0])

            replay = sync_snapshot(snapshot, root=self.root, apply=True)
            self.assertEqual("match", replay["status"])
            self.assertEqual("post", replay["reviewedArabicAtomicState"])
            self.assertEqual(post_story, json.loads(path.read_text(encoding="utf-8"))["stories"][0])

    def test_reviewed_arabic_dn_offset_change_fails_closed(self) -> None:
        source = "قال اللهُ."
        terminal = "قال يَهوَهْ."
        semantics = {kind: _parse_tagged(source) for kind in ("J", "ADD", "DN")}
        key = ("ar", "GEN", 1, (1, 1))
        start = source.index("اللهُ")
        row = {
            "localPrefixSha256": arabic_review.digest_text(terminal),
            "sourcePrefixSha256": arabic_review.digest_text(source),
            "taggedPrefixSha256": arabic_review.digest_text("قال [DN]اللهُ[/DN]."),
            "spans": ((start + 1, start + 1 + len("اللهُ"), "اللهُ"),),
        }
        with mock.patch.dict(
                arabic_review.REVIEWED_ARABIC_DN_RANGES, {key: row}, clear=True):
            with self.assertRaisesRegex(RangeMergeError, "offset or lexeme changed"):
                range_merge._reviewed_arabic_dn_prefix(
                    language="ar", code="GEN", chapter=1, source_unit=(1, 1),
                    local_prefix=terminal, source_plain=source, semantics=semantics,
                )

    def test_large_arabic_genealogy_merges_with_bounded_source_sync(self) -> None:
        bullets = [f"اسم قديم {number}. (1:{number})." for number in range(1, 23)]
        path = self.write_book(bullets, language="ar")
        source_text = " ".join(f"اسم جديد {number}." for number in range(1, 23))
        result = sync_snapshot(self.snapshot([
            self.verse("GEN.1.1-22", source_text, label="1"),
        ], language="ar", bible_id=153, abbreviation="SAB"), root=self.root, apply=True)
        self.assertEqual(1, result["mergedGroupCount"])
        self.assertEqual(21, result["removedBulletCount"])
        self.assertEqual(1, result["sourceSyncedGroupCount"])
        self.assertEqual([source_text + " (1:1-22)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_large_group_uses_authoritative_rendered_semantic_span(self) -> None:
        bullets = ["x a [J]b[/J] c x a b c old. (1:1)."]
        bullets.extend(f"name {number}. (1:{number})." for number in range(2, 23))
        path = self.write_book(bullets, language="en")
        before = path.read_bytes()
        tail = " ".join(f"name {number}." for number in range(2, 23))
        usfm = "GEN.1.1-22"
        record = {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">1</span>'
                '<span class="x__content">x a </span><span class="x__wj">b</span>'
                f'<span class="x__content"> c x a b c new. {tail}</span></span>'
            ),
        }
        result = sync_snapshot(
            self.snapshot([record], language="en", bible_id=3034, abbreviation="BSB"),
            root=self.root, apply=True,
        )
        self.assertEqual(1, result["semanticVerifiedGroupCount"])
        self.assertEqual(1, result["sourceSyncedGroupCount"])
        self.assertNotEqual(before, path.read_bytes())
        self.assertEqual(
            [f"x a [J]b[/J] c x a b c new. {tail} (1:1-22)."],
            json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"],
        )

    def test_unconfirmed_j_tag_blocks_source_replacement(self) -> None:
        path = self.write_book(["[J]Jesus[/J] said (1:3).", "old word. (1:4)."], language="en")
        before = path.read_bytes()
        with self.assertRaises(RangeMergeError):
            sync_snapshot(self.snapshot([
                self.verse("GEN.1.3-4", "Jesus said new word."),
            ], language="en", bible_id=3034, abbreviation="BSB"), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_rendered_j_tag_is_preserved_during_source_replacement(self) -> None:
        path = self.write_book(["[J]Jesus[/J] said (1:3).", "old word. (1:4)."], language="en")
        usfm = "GEN.1.3-4"
        record = {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">3</span>'
                '<span class="x__wj">Jesus</span><span class="x__content"> said new word.</span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([record], language="en", bible_id=3034, abbreviation="BSB"),
                               root=self.root, apply=True)
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual(["[J]Jesus[/J] said new word. (1:3-4)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_runtime_divine_name_survives_source_replacement(self) -> None:
        path = self.write_book(["The LORD said. (1:3).", "Amen. (1:4)."], language="en")
        usfm = "GEN.1.3-4"
        record = {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">3</span>'
                '<span class="x__content">The </span><span class="x__nd">Lord</span>'
                '<span class="x__content"> spoke. Amen.</span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([record], language="en", bible_id=3034, abbreviation="BSB"),
                               root=self.root, apply=True)
        self.assertEqual(1, result["sourceSyncedGroupCount"])
        self.assertEqual(1, result["preservedRuntimeDivineNameSpanCount"])
        self.assertEqual(["The [DN]Lord[/DN] spoke. Amen. (1:3-4)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_chinese_source_without_interverse_space_keeps_no_separator(self) -> None:
        path = self.write_book(["起初。 (1:3).", "创造。 (1:4)."], language="zh-Hans")
        sync_snapshot(self.snapshot([
            self.verse("GEN.1.3+GEN.1.4", "起初。创造。"),
        ], language="zh-Hans", bible_id=36, abbreviation="CCB"), root=self.root, apply=True)
        self.assertEqual(["起初。创造。 (1:3-4)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_ambiguous_join_blocks_without_writing(self) -> None:
        path = self.write_book(["前半。 (1:3).", "後半。 (1:4)."])
        before = path.read_bytes()
        with mock.patch("sync_modern_range_merges_browser._semantic_text_equal", return_value=True):
            with self.assertRaisesRegex(RangeMergeError, "ambiguous native join"):
                sync_snapshot(self.snapshot([self.verse("GEN.1.3-4", "前半。 後半。")]),
                              root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_fullwidth_marker_style_survives_multiple_merges(self) -> None:
        path = self.write_book([
            "一。 （1：3）。", "二。 (1:4).", "三。 (1:5).", "四。 (1:6).",
        ])
        result = sync_snapshot(self.snapshot([
            self.verse("GEN.1.3-4", "一。 二。"),
            self.verse("GEN.1.5+GEN.1.6", "三。 四。", label="5"),
        ]), root=self.root, apply=True)
        self.assertEqual(2, result["mergedGroupCount"])
        self.assertEqual(["一。 二。 （1：3-4）。", "三。 四。 (1:5-6)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_noncontiguous_coverage_blocks(self) -> None:
        for index, (bullets, source) in enumerate((
            (["前半。 (1:3).", "後半。 (1:5)."], "前半。 後半。"),
            (["前半。 (1:3).", "後半。 (1:4).", "余り。 (1:5)."], "前半。 後半。"),
        )):
            with self.subTest(index=index):
                original = self.root
                self.root = original / str(index)
                try:
                    path = self.write_book(bullets)
                    before = path.read_bytes()
                    with self.assertRaises(RangeMergeError):
                        sync_snapshot(self.snapshot([self.verse("GEN.1.3-4", source)]), root=self.root, apply=True)
                    self.assertEqual(before, path.read_bytes())
                finally:
                    self.root = original

    def test_identical_source_and_local_omission_gap_allows_adjacent_merge(self) -> None:
        path = self.write_book([
            "前半。 (1:3).", "後半。 (1:4).", "後節。 (1:6).",
        ])
        snapshot = self.snapshot([
            self.verse("GEN.1.3+GEN.1.4", "前半。 後半。"),
            self.verse("GEN.1.6", "後節。", label="6"),
        ])
        result = sync_snapshot(snapshot, root=self.root, apply=True)
        self.assertEqual("changed", result["status"])
        self.assertEqual(1, result["removedBulletCount"])
        self.assertEqual([
            "前半。 後半。 (1:3-4).", "後節。 (1:6).",
        ], json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])
        self.assertEqual("match", sync_snapshot(snapshot, root=self.root, apply=True)["status"])

    def test_heading_inside_merge_blocks(self) -> None:
        path = self.write_book(["前半。 (1:3).", "後半。 (1:4)."],
                               headings=[{"beforeVerse": 4, "text": "Middle"}])
        before = path.read_bytes()
        with self.assertRaises(RangeMergeError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.3-4", "前半。 後半。")]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_source_heading_between_unmerged_units_is_noop(self) -> None:
        path = self.write_book(["前半。 (1:3).", "後半。 (1:4)."])
        before = path.read_bytes()
        records = [
            self.verse("GEN.1.3", "前半。"),
            {"kind": "heading", "html": '<h3 class="heading">Middle</h3>'},
            self.verse("GEN.1.4", "後半。", label="4"),
        ]
        # A native range cannot be assembled across a source heading. This
        # snapshot has two source units and therefore correctly remains a no-op.
        self.assertEqual("match", sync_snapshot(self.snapshot(records), root=self.root)["status"])
        self.assertEqual(before, path.read_bytes())

    def test_a_b_and_wrong_page_block(self) -> None:
        path = self.write_book(["前半。 (1:3).", "後半。 (1:4)."])
        before = path.read_bytes()
        with self.assertRaises(BrowserAuditError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.3a", "前半。 後半。")]), root=self.root, apply=True)
        with self.assertRaises(RangeMergeError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.3-4", "前半。 後半。")],
                                        pageUrl="https://evil.example/bible/83/GEN.1.JCB"),
                          root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_copied_wrong_book_payload_blocks_before_merge(self) -> None:
        path = self.write_book(["First. (1:3).", "Second. (1:4)."])
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["id"] = "exodus"
        payload["stories"][0]["id"] = "exodus-1"
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        before = path.read_bytes()
        with self.assertRaises(RangeMergeError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.3-4", "First. Second.")]),
                          root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_malformed_tags_block(self) -> None:
        path = self.write_book(["[J]前半 (1:3).", "後半。 (1:4)."])
        before = path.read_bytes()
        with self.assertRaises(RangeMergeError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.3-4", "前半 後半。")]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_concurrent_change_blocks(self) -> None:
        path = self.write_book(["前半。 (1:3).", "後半。 (1:4)."])
        before = path.read_bytes()
        real_read = Path.read_bytes
        calls = 0

        def raced(target: Path) -> bytes:
            nonlocal calls
            value = real_read(target)
            if target == path:
                calls += 1
                if calls == 2:
                    return value + b" "
            return value

        with mock.patch.object(Path, "read_bytes", raced), self.assertRaises(RangeMergeError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.3-4", "前半。 後半。")]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_jsonl_and_loopback_output_sanitized(self) -> None:
        secret = "SECRET_PUBLISHER_TEXT"
        sink = io.StringIO()
        self.assertEqual(2, stream_snapshots(io.StringIO('{"records":"' + secret + '"}\n'), sink, root=self.root))
        self.assertNotIn(secret, sink.getvalue())
        server = create_snapshot_server(0, root=self.root, apply=True)
        self.assertEqual("127.0.0.1", server.server_address[0])
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5)
            connection.request("POST", "/snapshot", body=json.dumps({"records": secret}).encode(),
                               headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            body = response.read().decode()
            self.assertEqual(200, response.status)
            self.assertEqual("no-store", response.getheader("Cache-Control"))
            self.assertNotIn(secret, body)
            self.assertEqual("blocked", json.loads(body)["status"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
