from __future__ import annotations

import http.client
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock

from compare_modern_editions import BOOKS
from compare_modern_editions_browser import BrowserAuditError
from sync_modern_scripture_browser import _parse_tagged
from sync_modern_ranges_browser import (
    BOOK_ROOT_IDS,
    REVIEWED_FULL_RANGE_J_SOURCE_REPLACEMENTS,
    RangeSyncError,
    _local_story,
    _reviewed_full_range_j_replacement,
    _sha_json,
    create_snapshot_server,
    stream_snapshots,
    sync_snapshot,
)


class ModernRangeSyncTests(unittest.TestCase):
    maxDiff = None

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_book(self, bullets: list[str], *, language: str = "ja",
                   headings: list[dict[str, object]] | None = None, raw_transform=None) -> Path:
        payload = {
            "id": "genesis",
            "title": "Genesis",
            "stories": [{
                "id": "genesis-1",
                "title": "Genesis 1",
                "refs": ["Genesis 1"],
                "summaryBullets": bullets,
                "crossRefs": ["Never change this reference (1:4)."],
                "translationNotes": [{"term": "unchanged", "note": "unchanged"}],
                "keyTakeaway": "unchanged",
                "headings": headings or [],
            }],
            "sentinel": "unchanged",
        }
        raw = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        if raw_transform:
            raw = raw_transform(raw)
        path = self.root / "shared/assets/books/old_testament" / language / "genesis.json"
        path.parent.mkdir(parents=True)
        path.write_bytes(raw.encode("utf-8"))
        return path

    @staticmethod
    def verse(usfm: str, text: str, *, label: str = "4") -> dict[str, str]:
        return {
            "kind": "verse",
            "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}">'
                f'<span class="x__label">{label}</span>'
                f'<span class="x__content">{text}</span></span>'
            ),
        }

    @staticmethod
    def divine_verse(usfm: str, before: str, name: str, after: str) -> dict[str, str]:
        return {
            "kind": "verse",
            "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">4</span>'
                f'<span class="x__content">{before}</span>'
                f'<span class="x__nd"><span class="x__content">{name}</span></span>'
                f'<span class="x__content">{after}</span></span>'
            ),
        }

    def snapshot(self, records: list[dict[str, str]], *, language: str = "ja",
                 bible_id: int = 83, abbreviation: str = "JCB", **changes) -> dict[str, object]:
        result: dict[str, object] = {
            "language": language,
            "bibleId": bible_id,
            "book": "GEN",
            "chapter": 1,
            "pageUrl": f"https://www.bible.com/bible/{bible_id}/GEN.1.{abbreviation}",
            "pageTitle": "Genesis 1",
            "records": records,
        }
        result.update(changes)
        return result

    def test_jcb_genesis_bridge_updates_only_marker_and_never_splits(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4)."])
        before = path.read_text(encoding="utf-8")
        result = sync_snapshot(
            self.snapshot([self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]),
            root=self.root,
            apply=True,
        )
        after = path.read_text(encoding="utf-8")
        payload = json.loads(after)
        self.assertEqual(["同じ本文です。 (1:4-5)."], payload["stories"][0]["summaryBullets"])
        self.assertEqual(1, result["nativeUnitCount"])
        self.assertEqual(1, result["changedMarkerCount"])
        self.assertEqual("changed", result["status"])
        self.assertEqual(before.replace("(1:4).", "(1:4-5).", 1), after)
        self.assertIn("Never change this reference (1:4).", after)

    def test_audit_mode_reports_without_writing(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4)."])
        before = path.read_bytes()
        result = sync_snapshot(
            self.snapshot([self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]), root=self.root
        )
        self.assertEqual("audit", result["mode"])
        self.assertEqual("changed", result["status"])
        self.assertEqual(before, path.read_bytes())

    def test_fullwidth_marker_style_and_trailing_punctuation_are_preserved(self) -> None:
        path = self.write_book(["同じ本文です。 （1：4）。"])
        sync_snapshot(
            self.snapshot([self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("同じ本文です。 （1：4-5）。", bullet)

    def test_existing_range_separator_style_is_preserved_when_end_changes(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4–5)."])
        sync_snapshot(
            self.snapshot([self.verse("GEN.1.4+GEN.1.5+GEN.1.6", "同じ本文です。")]),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("同じ本文です。 (1:4–6).", bullet)

    def test_a_b_source_suffix_is_never_split_or_inferred(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4)."])
        before = path.read_bytes()
        with self.assertRaises(BrowserAuditError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.4a", "同じ本文です。")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_count_difference_blocks(self) -> None:
        path = self.write_book(["四。 (1:4).", "六。 (1:6)."])
        before = path.read_bytes()
        with self.assertRaises(RangeSyncError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.4", "四。")]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_start_difference_blocks(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4)."])
        before = path.read_bytes()
        with self.assertRaises(RangeSyncError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.5", "同じ本文です。")]), root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_rendered_word_punctuation_and_case_differences_sync_atomically(self) -> None:
        cases = ("違う本文です。", "同じ本文です！", "SAME text.")
        for index, source in enumerate(cases):
            with self.subTest(source=source):
                case_root = self.root / f"text-{index}"
                original = self.root
                self.root = case_root
                try:
                    path = self.write_book(["同じ本文です。 (1:4)."] if index < 2 else ["Same text. (1:4)."])
                    before = path.read_text(encoding="utf-8")
                    result = sync_snapshot(
                        self.snapshot([self.verse("GEN.1.4+GEN.1.5", source)]),
                        root=self.root,
                        apply=True,
                    )
                    after = path.read_text(encoding="utf-8")
                    self.assertEqual(1, result["sourceSyncedRangeCount"])
                    self.assertEqual([f"{source} (1:4-5)."],
                                     json.loads(after)["stories"][0]["summaryBullets"])
                    self.assertEqual(before.replace(
                        json.loads(before)["stories"][0]["summaryBullets"][0],
                        f"{source} (1:4-5).",
                        1,
                    ), after)
                finally:
                    self.root = original

    def test_rendered_j_tag_is_reconstructed_during_atomic_range_sync(self) -> None:
        path = self.write_book(["[J]古い言葉[/J]。 (1:4)."])
        usfm = "GEN.1.4+GEN.1.5"
        record = {
            "kind": "verse", "usfm": usfm,
            "html": (
                f'<span class="x__verse" data-usfm="{usfm}"><span class="x__label">4</span>'
                '<span class="x__wj">新しい言葉</span><span class="x__content">。</span></span>'
            ),
        }
        result = sync_snapshot(self.snapshot([record]), root=self.root, apply=True)
        self.assertEqual(1, result["sourceSyncedRangeCount"])
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual(["[J]新しい言葉[/J]。 (1:4-5)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_unconfirmed_local_j_tag_still_blocks_atomic_range_sync(self) -> None:
        path = self.write_book(["[J]古い言葉[/J]。 (1:4)."])
        before = path.read_bytes()
        with self.assertRaises(RangeSyncError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.4+GEN.1.5", "新しい言葉。")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_hash_pinned_whole_range_j_span_survives_missing_rendered_wj(self) -> None:
        path = self.write_book(["[J]古い言葉。[/J] (1:4)."])
        payload = json.loads(path.read_text(encoding="utf-8"))
        story_sha = _sha_json(payload["stories"][0])
        source_ranges_sha = _sha_json([(4, 5)])
        reviewed = {
            ("ja", "GEN", 1, (4, 5)): (story_sha, source_ranges_sha),
        }
        with mock.patch(
            "sync_modern_ranges_browser.REVIEWED_FULL_RANGE_J_SOURCE_REPLACEMENTS",
            reviewed,
        ):
            result = sync_snapshot(
                self.snapshot([self.verse("GEN.1.4+GEN.1.5", "新しい言葉。")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(1, result["sourceSyncedRangeCount"])
        self.assertEqual(2, result["preservedTagBoundaryCount"])
        self.assertEqual(
            ["[J]新しい言葉。[/J] (1:4-5)."],
            json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"],
        )

    def test_checked_in_whole_range_j_exceptions_are_closed(self) -> None:
        self.assertEqual(
            {
                ("ja", "LUK", 4, (18, 19)),
                ("ja", "LUK", 11, (50, 51)),
                ("ja", "LUK", 20, (37, 38)),
                ("ja", "LUK", 20, (42, 43)),
            },
            set(REVIEWED_FULL_RANGE_J_SOURCE_REPLACEMENTS),
        )

    def test_hash_pinned_whole_range_j_exception_rejects_changed_hash(self) -> None:
        path = self.write_book(["[J]古い言葉。[/J] (1:4)."])
        before = path.read_bytes()
        reviewed = {
            ("ja", "GEN", 1, (4, 5)): ("0" * 64, _sha_json([(4, 5)])),
        }
        with mock.patch(
            "sync_modern_ranges_browser.REVIEWED_FULL_RANGE_J_SOURCE_REPLACEMENTS",
            reviewed,
        ), self.assertRaises(RangeSyncError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.4+GEN.1.5", "新しい言葉。")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_hash_pinned_whole_range_j_exception_rejects_partial_rendered_wj(self) -> None:
        source_plain = "新しい言葉。"
        reviewed = {
            ("ja", "GEN", 1, (4, 5)): ("story", "ranges"),
        }
        source_kinds = {
            "J": _parse_tagged("[J]新しい[/J]言葉。"),
            "ADD": _parse_tagged(source_plain),
            "DN": _parse_tagged(source_plain),
        }
        with self.assertRaisesRegex(RangeSyncError, "gained rendered J evidence"):
            _reviewed_full_range_j_replacement(
                language="ja", code="GEN", chapter=1, source_unit=(4, 5),
                local_prefix="[J]古い言葉。[/J]", source_plain=source_plain,
                source_kinds=source_kinds, collection="old_testament",
                story_sha="story", source_ranges_sha="ranges", reviewed=reviewed,
            )

    def test_psalm_119_sized_rendered_snapshot_is_accepted(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4)."])
        records = [
            {"kind": "heading", "html": '<span class="x__heading">。</span>'}
            for _ in range(558)
        ] + [self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]
        result = sync_snapshot(self.snapshot(records), root=self.root, apply=True)
        self.assertEqual("changed", result["status"])
        self.assertEqual(["同じ本文です。 (1:4-5)."],
                         json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"])

    def test_unicode_zs_only_difference_is_accepted(self) -> None:
        path = self.write_book(["Mot : texte. (1:4)."], language="fr")
        sync_snapshot(
            self.snapshot(
                [self.verse("GEN.1.4+GEN.1.5", "Mot\u00a0: texte.")],
                language="fr", bible_id=104, abbreviation="NBS",
            ),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("Mot : texte. (1:4-5).", bullet)

    def test_comparator_dn_presentation_equivalence_is_accepted(self) -> None:
        path = self.write_book(["Der HERR sprach. (1:4)."], language="de")
        sync_snapshot(
            self.snapshot(
                [self.divine_verse("GEN.1.4+GEN.1.5", "Der ", "Herr", " sprach.")],
                language="de", bible_id=157, abbreviation="SCH2000",
            ),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("Der HERR sprach. (1:4-5).", bullet)

    def test_rendered_dn_wrapper_repairs_unrecognized_untagged_presentation(self) -> None:
        path = self.write_book(["Der Herr sprach. (1:4)."], language="de")
        result = sync_snapshot(
            self.snapshot(
                [self.divine_verse("GEN.1.4+GEN.1.5", "Der ", "Herr", " sprach.")],
                language="de", bible_id=157, abbreviation="SCH2000",
            ),
            root=self.root,
            apply=True,
        )
        self.assertEqual(1, result["sourceSyncedRangeCount"])
        self.assertEqual(
            ["Der [DN]Herr[/DN] sprach. (1:4-5)."],
            json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"],
        )

    def test_valid_app_tags_are_stripped_for_comparison_but_preserved(self) -> None:
        path = self.write_book(["[J]同じ[ADD]本文[/ADD]です。[/J] (1:4)."])
        sync_snapshot(
            self.snapshot([self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]),
            root=self.root,
            apply=True,
        )
        bullet = json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]
        self.assertEqual("[J]同じ[ADD]本文[/ADD]です。[/J] (1:4-5).", bullet)

    def test_malformed_or_crossing_tags_block(self) -> None:
        for index, prefix in enumerate(("[J]未終了", "[J][ADD]交差[/J][/ADD]")):
            with self.subTest(prefix=prefix):
                case_root = self.root / f"tags-{index}"
                original = self.root
                self.root = case_root
                try:
                    path = self.write_book([prefix + " (1:4)."])
                    before = path.read_bytes()
                    with self.assertRaises(RangeSyncError):
                        sync_snapshot(
                            self.snapshot([self.verse("GEN.1.4+GEN.1.5", "未終了")]),
                            root=self.root,
                            apply=True,
                        )
                    self.assertEqual(before, path.read_bytes())
                finally:
                    self.root = original

    def test_heading_inside_new_bridge_blocks_but_start_anchor_is_safe(self) -> None:
        blocked_path = self.write_book(
            ["同じ本文です。 (1:4)."], headings=[{"beforeVerse": 5, "text": "Inside"}]
        )
        before = blocked_path.read_bytes()
        with self.assertRaises(RangeSyncError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, blocked_path.read_bytes())

        safe_root = self.root / "safe-heading"
        original = self.root
        self.root = safe_root
        try:
            safe_path = self.write_book(
                ["同じ本文です。 (1:4)."], headings=[{"beforeVerse": 4, "text": "Start"}]
            )
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]),
                root=self.root,
                apply=True,
            )
            self.assertIn("(1:4-5).", safe_path.read_text(encoding="utf-8"))
        finally:
            self.root = original

    def test_wrong_page_identity_blocks(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4)."])
        before = path.read_bytes()
        for changes in (
            {"bibleId": 3034},
            {"pageUrl": "https://www.bible.com/bible/3034/GEN.1.BSB"},
            {"pageUrl": "https://evil.example/bible/83/GEN.1.JCB"},
            {"pageTitle": "Client Challenge"},
        ):
            with self.subTest(changes=changes), self.assertRaises(RangeSyncError):
                sync_snapshot(
                    self.snapshot([self.verse("GEN.1.4", "同じ本文です。")], **changes), root=self.root
                )
        self.assertEqual(before, path.read_bytes())

    def test_all_canonical_root_and_story_id_conventions_are_closed(self) -> None:
        exceptions = {
            "1_samuel": "1-samuel", "2_samuel": "2-samuel",
            "1_kings": "1-kings", "2_kings": "2-kings",
            "1_chronicles": "1chronicles", "2_chronicles": "2chronicles",
            "song_of_songs": "song-of-songs", "1_corinthians": "1-corinthians",
        }
        expected = {book_id: exceptions.get(book_id, book_id)
                    for _code, _collection, book_id in BOOKS}
        self.assertEqual(expected, BOOK_ROOT_IDS)
        for _code, _collection, book_id in BOOKS:
            root_id = expected[book_id]
            valid = {"id": root_id, "stories": [{
                "id": f"{root_id}-1", "summaryBullets": ["Text. (1:1)."],
            }]}
            with self.subTest(book=book_id, case="valid"):
                self.assertEqual([(1, 1)], _local_story(valid, book_id, 1)[2])
            copied = {"id": "not-the-requested-book", "stories": [{
                "id": "not-the-requested-book-1", "summaryBullets": ["Text. (1:1)."],
            }]}
            with self.subTest(book=book_id, case="copied-root"), self.assertRaises(RangeSyncError):
                _local_story(copied, book_id, 1)
            wrong_story = {"id": root_id, "stories": [{
                "id": f"{root_id}-2", "summaryBullets": ["Text. (1:1)."],
            }]}
            with self.subTest(book=book_id, case="wrong-story"), self.assertRaises(RangeSyncError):
                _local_story(wrong_story, book_id, 1)

    def test_copied_wrong_book_payload_blocks_before_marker_mutation(self) -> None:
        path = self.write_book(["Same. (1:4)."])
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["id"] = "exodus"
        payload["stories"][0]["id"] = "exodus-1"
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        before = path.read_bytes()
        with self.assertRaises(RangeSyncError):
            sync_snapshot(self.snapshot([self.verse("GEN.1.4+GEN.1.5", "Same.")]),
                          root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())

    def test_concurrent_change_blocks_atomic_replace(self) -> None:
        path = self.write_book(["同じ本文です。 (1:4)."])
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

        with mock.patch.object(Path, "read_bytes", raced), self.assertRaises(RangeSyncError):
            sync_snapshot(
                self.snapshot([self.verse("GEN.1.4+GEN.1.5", "同じ本文です。")]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, path.read_bytes())

    def test_jsonl_and_loopback_errors_are_sanitized(self) -> None:
        secret = "SECRET_PUBLISHER_TEXT"
        sink = io.StringIO()
        status = stream_snapshots(io.StringIO('{"records":"' + secret + '"\n'), sink, root=self.root)
        self.assertEqual(2, status)
        self.assertNotIn(secret, sink.getvalue())
        self.assertNotIn("errorCode", json.loads(sink.getvalue()))

        server = create_snapshot_server(0, root=self.root, apply=True)
        self.assertEqual("127.0.0.1", server.server_address[0])
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            payload = json.dumps({"records": secret}).encode("utf-8")
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5)
            connection.request("POST", "/snapshot", body=payload, headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            body = response.read().decode("utf-8")
            self.assertEqual(200, response.status)
            self.assertEqual("no-store", response.getheader("Cache-Control"))
            self.assertNotIn(secret, body)
            result = json.loads(body)
            self.assertEqual("blocked", result["status"])
            self.assertEqual("unknown_language_book_or_chapter", result["errorCode"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
