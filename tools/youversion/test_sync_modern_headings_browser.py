"""Focused offline tests for snapshot-driven base heading synchronization."""

from pathlib import Path
import copy
from http.client import HTTPConnection
import io
import json
import tempfile
import threading
import unittest

import sync_modern_headings_browser as sync
from compare_modern_editions import BOOKS


def verse(number: str, text: str = "Publisher wording") -> dict[str, str]:
    return {
        "kind": "verse", "usfm": f"GEN.1.{number}",
        "html": f'<span class="x__verse"><span class="x__label">{number}</span><span class="x__content">{text}</span></span>',
    }


def heading(label: str) -> dict[str, str]:
    return {"kind": "heading", "html": f'<span class="x__heading">{label}</span>'}


def snapshot(records: list[dict[str, str]]) -> dict:
    return {
        "language": "en", "bibleId": 3034, "book": "GEN", "chapter": 1,
        "sourceUrl": "https://www.bible.com/bible/3034/GEN.1.BSB",
        "records": records,
    }


class ModernHeadingSyncTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / "shared" / "assets" / "books" / "old_testament" / "en" / "genesis.json"
        self.path.parent.mkdir(parents=True)
        self.book = {
            "id": "genesis", "title": "Book title", "stories": [{
                "id": "genesis-1", "title": "Chapter title", "refs": ["Genesis 1:1-3"],
                "summaryBullets": ["[DN]LORD[/DN] spoke. (1:1).", "Second verse. (1:2-3)."],
                "crossRefs": ["Other reference"], "translationNotes": [{"term": "A", "note": "B"}],
                "keyTakeaway": "A takeaway", "headings": [{"beforeVerse": 1, "text": "Old section"}],
            }],
        }
        self.path.write_text(json.dumps(self.book, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def source(self) -> dict:
        return snapshot([heading("First section"), verse("1"), heading("Second section"),
                         heading("Second subtitle"), verse("2-3")])

    def test_dry_run_reports_hashes_without_writing_or_source_text(self) -> None:
        raw = self.path.read_bytes()
        report = sync.sync_snapshot(self.source(), root=self.root)
        self.assertEqual("changed", report["status"])
        self.assertEqual("audit", report["mode"])
        self.assertEqual(3, report["sourceHeadingCount"])
        self.assertEqual(2, report["targetHeadingCount"])
        self.assertEqual(2, report["nativeRangeCount"])
        self.assertEqual(raw, self.path.read_bytes())
        self.assertNotIn("Publisher wording", json.dumps(report))
        self.assertNotIn("First section", json.dumps(report))

    def test_apply_changes_only_heading_value_and_merges_stacked_labels(self) -> None:
        original = self.path.read_text(encoding="utf-8")
        report = sync.sync_snapshot(self.source(), root=self.root, apply=True)
        updated = self.path.read_text(encoding="utf-8")
        actual = json.loads(updated)
        expected = copy.deepcopy(self.book)
        expected["stories"][0]["headings"] = [
            {"beforeVerse": 1, "text": "First section"},
            {"beforeVerse": 2, "text": "Second section\nSecond subtitle"},
        ]
        self.assertEqual(expected, actual)
        self.assertEqual("apply", report["mode"])
        self.assertNotEqual(report["beforeSha256"], report["afterSha256"])
        self.assertEqual(original.split('"headings": ')[0], updated.split('"headings": ')[0])
        self.assertEqual(original.rsplit('"headings": ', 1)[1].split("\n    }", 1)[1],
                         updated.rsplit('"headings": ', 1)[1].split("\n    }", 1)[1])

    def test_layout_only_heading_fragments_do_not_block_safe_sync(self) -> None:
        records = [heading(" \t "), heading("\u00a0;"), heading("\u202fFirst section\u00a0"),
                   verse("1"), heading("Second section"), verse("2-3")]
        report = sync.sync_snapshot(snapshot(records), root=self.root)
        self.assertEqual("changed", report["status"])
        self.assertEqual(2, report["sourceHeadingCount"])
        applied = sync.sync_snapshot(snapshot(records), root=self.root, apply=True)
        self.assertEqual("changed", applied["status"])
        headings = json.loads(self.path.read_text(encoding="utf-8"))["stories"][0]["headings"]
        self.assertEqual([{"beforeVerse": 1, "text": "First section"},
                          {"beforeVerse": 2, "text": "Second section"}], headings)

    def test_rejects_native_range_difference_without_writing(self) -> None:
        raw = self.path.read_bytes()
        bad = snapshot([heading("First"), verse("1"), verse("2"), verse("3")])
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(bad, root=self.root, apply=True)
        self.assertEqual(raw, self.path.read_bytes())

    def test_verified_source_and_local_without_headings_match(self) -> None:
        self.book["stories"][0]["headings"] = []
        self.path.write_text(json.dumps(self.book, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raw = self.path.read_bytes()
        no_headings = snapshot([verse("1"), verse("2-3")])
        result = sync.sync_snapshot(no_headings, root=self.root, apply=True)
        self.assertEqual("match", result["status"])
        self.assertEqual(0, result["localHeadingCount"])
        self.assertEqual(0, result["sourceHeadingCount"])
        self.assertEqual(raw, self.path.read_bytes())

    def test_absent_heading_field_and_no_source_headings_match_without_writing(self) -> None:
        del self.book["stories"][0]["headings"]
        self.path.write_text(json.dumps(self.book, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raw = self.path.read_bytes()
        result = sync.sync_snapshot(snapshot([verse("1"), verse("2-3")]), root=self.root, apply=True)
        self.assertEqual("match", result["status"])
        self.assertEqual(0, result["localHeadingCount"])
        self.assertEqual(raw, self.path.read_bytes())

    def test_absent_heading_field_inserts_only_one_story_field(self) -> None:
        del self.book["stories"][0]["headings"]
        original = json.dumps(self.book, ensure_ascii=False, indent=2) + "\n"
        self.path.write_text(original, encoding="utf-8")
        report = sync.sync_snapshot(self.source(), root=self.root)
        self.assertEqual("changed", report["status"])
        self.assertEqual(original, self.path.read_text(encoding="utf-8"))
        report = sync.sync_snapshot(self.source(), root=self.root, apply=True)
        updated = self.path.read_text(encoding="utf-8")
        self.assertEqual("changed", report["status"])
        self.assertEqual(1, updated.count('"headings"'))
        self.assertEqual(original.replace('"keyTakeaway": "A takeaway"',
                                           '"keyTakeaway": "A takeaway",\n      "headings": [\n        {\n          "beforeVerse": 1,\n          "text": "First section"\n        },\n        {\n          "beforeVerse": 2,\n          "text": "Second section\\nSecond subtitle"\n        }\n      ]'),
                         updated)

    def test_absent_heading_insertion_preserves_crlf_and_compact_style(self) -> None:
        del self.book["stories"][0]["headings"]
        original = (json.dumps(self.book, ensure_ascii=False, indent=2) + "\n").replace("\n", "\r\n")
        self.path.write_bytes(original.encode("utf-8"))
        sync.sync_snapshot(self.source(), root=self.root, apply=True)
        updated = self.path.read_bytes()
        self.assertEqual(updated.count(b"\n"), updated.count(b"\r\n"))
        self.assertEqual("A takeaway", json.loads(updated)["stories"][0]["keyTakeaway"])

        compact = json.dumps(self.book, ensure_ascii=False, separators=(",", ":"))
        self.path.write_text(compact, encoding="utf-8")
        sync.sync_snapshot(self.source(), root=self.root, apply=True)
        updated_compact = self.path.read_text(encoding="utf-8")
        self.assertNotIn("\n", updated_compact)
        self.assertIn(',"headings":[', updated_compact)

    def test_source_without_headings_cannot_delete_existing_headings(self) -> None:
        raw = self.path.read_bytes()
        no_headings = snapshot([verse("1"), verse("2-3")])
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(no_headings, root=self.root, apply=True)
        self.assertEqual(raw, self.path.read_bytes())

    def test_underscore_asset_names_use_their_own_root_json_ids(self) -> None:
        for code, book_id, asset_id in (("1SA", "1_samuel", "1-samuel"),
                                        ("SNG", "song_of_songs", "song-of-songs"),
                                        ("1CH", "1_chronicles", "1chronicles")):
            with self.subTest(book=book_id):
                path = self.path.with_name(f"{book_id}.json")
                book = copy.deepcopy(self.book)
                book["id"] = asset_id
                book["stories"][0]["id"] = f"{asset_id}-1"
                path.write_text(json.dumps(book, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                live = snapshot([
                    heading("Verified heading"),
                    {"kind": "verse", "usfm": f"{code}.1.1", "html": '<span class="x__verse"><span class="x__label">1</span>Source</span>'},
                    {"kind": "verse", "usfm": f"{code}.1.2-{code}.1.3", "html": '<span class="x__verse"><span class="x__label">2-3</span>Source</span>'},
                ])
                live["book"] = code
                live["sourceUrl"] = f"https://www.bible.com/bible/3034/{code}.1.BSB"
                result = sync.sync_snapshot(live, root=self.root, apply=True)
                self.assertEqual("changed", result["status"])
                actual = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(asset_id, actual["id"])
                self.assertEqual(f"{asset_id}-1", actual["stories"][0]["id"])
                self.assertEqual("Verified heading", actual["stories"][0]["headings"][0]["text"])
                self.assertEqual(book["stories"][0]["summaryBullets"], actual["stories"][0]["summaryBullets"])

    def test_missing_root_id_and_duplicate_target_stories_fail_closed(self) -> None:
        self.book["id"] = ""
        self.path.write_text(json.dumps(self.book), encoding="utf-8")
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(self.source(), root=self.root, apply=True)
        self.book["id"] = "genesis"
        self.book["stories"].append(copy.deepcopy(self.book["stories"][0]))
        self.path.write_text(json.dumps(self.book), encoding="utf-8")
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(self.source(), root=self.root, apply=True)

    def test_all_canonical_root_and_story_id_conventions_are_closed(self) -> None:
        exceptions = {
            "1_samuel": "1-samuel", "2_samuel": "2-samuel",
            "1_kings": "1-kings", "2_kings": "2-kings",
            "1_chronicles": "1chronicles", "2_chronicles": "2chronicles",
            "song_of_songs": "song-of-songs", "1_corinthians": "1-corinthians",
        }
        expected = {book_id: exceptions.get(book_id, book_id)
                    for _code, _collection, book_id in BOOKS}
        self.assertEqual(expected, sync.BOOK_ROOT_IDS)
        for _code, _collection, book_id in BOOKS:
            root_id = expected[book_id]
            valid = {"id": root_id, "stories": [{
                "id": f"{root_id}-1", "summaryBullets": ["Text. (1:1)."],
            }]}
            with self.subTest(book=book_id, case="valid"):
                self.assertEqual([(1, 1)], sync._local_story(valid, book_id, 1)[2])
            copied = {"id": "not-the-requested-book", "stories": [{
                "id": "not-the-requested-book-1", "summaryBullets": ["Text. (1:1)."],
            }]}
            with self.subTest(book=book_id, case="copied-root"), self.assertRaises(sync.HeadingSyncError):
                sync._local_story(copied, book_id, 1)
            wrong_story = {"id": root_id, "stories": [{
                "id": f"{root_id}-2", "summaryBullets": ["Text. (1:1)."],
            }]}
            with self.subTest(book=book_id, case="wrong-story"), self.assertRaises(sync.HeadingSyncError):
                sync._local_story(wrong_story, book_id, 1)

    def test_copied_wrong_book_payload_blocks_before_heading_mutation(self) -> None:
        payload = copy.deepcopy(self.book)
        payload["id"] = "exodus"
        payload["stories"][0]["id"] = "exodus-1"
        self.path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        before = self.path.read_bytes()
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(self.source(), root=self.root, apply=True)
        self.assertEqual(before, self.path.read_bytes())

    def test_rejects_wrong_edition_and_url(self) -> None:
        wrong = self.source()
        wrong["bibleId"] = 1
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(wrong, root=self.root)

    def test_accepts_bible_com_locale_prefix_and_catalog_abbreviation_alias(self) -> None:
        localized = self.source()
        localized["sourceUrl"] = "https://www.bible.com/en/bible/3034/GEN.1.BSB"
        self.assertEqual("en", sync._identity(localized)[0])
        russian = self.source()
        russian.update({"language": "ru", "bibleId": 143,
                        "sourceUrl": "https://www.bible.com/ru/bible/143/GEN.1.НРП"})
        self.assertEqual("ru", sync._identity(russian)[0])
        wrong = self.source()
        wrong["sourceUrl"] = "https://www.bible.com/bible/3034/GEN.2.BSB"
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(wrong, root=self.root)

    def test_rejects_tagged_heading_to_preserve_display_semantics(self) -> None:
        self.book["stories"][0]["headings"][0]["text"] = "[DN]LORD[/DN]"
        self.path.write_text(json.dumps(self.book), encoding="utf-8")
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(self.source(), root=self.root, apply=True)

    def test_rejects_source_anchor_inside_native_range(self) -> None:
        bad = snapshot([heading("First"), verse("1"), verse("2-3"), heading("Mid-range"), verse("3")])
        with self.assertRaises((sync.HeadingSyncError, sync.BrowserAuditError)):
            sync.sync_snapshot(bad, root=self.root)

    def test_jsonl_errors_never_echo_source_text(self) -> None:
        source = io.StringIO(json.dumps(self.source()) + "\n" + "{private source text}\n")
        sink = io.StringIO()
        code = sync.stream_snapshots(source, sink, root=self.root)
        self.assertEqual(2, code)
        self.assertEqual(["changed", "blocked"], [json.loads(row)["status"] for row in sink.getvalue().splitlines()])
        self.assertNotIn("private source text", sink.getvalue())
        self.assertNotIn("Publisher wording", sink.getvalue())

    def test_live_cua_fields_derive_and_verify_edition(self) -> None:
        live = self.source()
        live["pageUrl"] = live.pop("sourceUrl")
        live["pageTitle"] = "Genesis 1 | BSB Bible | YouVersion"
        live.pop("bibleId")
        result = sync.sync_snapshot(live, root=self.root)
        self.assertEqual(3034, result["bibleId"])
        wrong = copy.deepcopy(live)
        wrong["pageUrl"] = "https://www.bible.com/bible/1/GEN.1.BSB"
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(wrong, root=self.root)
        wrong = copy.deepcopy(live)
        wrong["pageTitle"] = "Client Challenge"
        with self.assertRaises(sync.HeadingSyncError):
            sync.sync_snapshot(wrong, root=self.root)

    def test_loopback_server_accepts_live_cua_snapshot_without_persisting_source(self) -> None:
        server = sync.create_snapshot_server(0, root=self.root)
        self.assertEqual("127.0.0.1", server.server_address[0])
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        client = HTTPConnection("127.0.0.1", server.server_port, timeout=3)
        try:
            client.request("GET", "/health")
            health = client.getresponse()
            self.assertEqual(200, health.status)
            self.assertEqual("no-store", health.getheader("Cache-Control"))
            self.assertEqual({"status": "ok"}, json.loads(health.read()))

            live = self.source()
            live["pageUrl"] = live.pop("sourceUrl")
            live["pageTitle"] = "Genesis 1 | BSB Bible | YouVersion"
            live.pop("bibleId")
            original = self.path.read_bytes()
            client.request("POST", "/snapshot", json.dumps(live), {"Content-Type": "application/json"})
            response = client.getresponse()
            body = response.read().decode("utf-8")
            self.assertEqual(200, response.status)
            self.assertEqual("no-store", response.getheader("Cache-Control"))
            self.assertEqual("changed", json.loads(body)["status"])
            self.assertNotIn("Publisher wording", body)
            self.assertNotIn("First section", body)
            self.assertEqual(original, self.path.read_bytes())

            client.request("POST", "/snapshot", "{private source text}", {"Content-Type": "application/json"})
            blocked = client.getresponse().read().decode("utf-8")
            self.assertEqual("blocked", json.loads(blocked)["status"])
            self.assertNotIn("private source text", blocked)

            client.request("POST", "/snapshot", "x", {"Content-Type": "text/plain"})
            self.assertEqual("blocked", json.loads(client.getresponse().read())["status"])
        finally:
            client.close()
            server.shutdown()
            server.server_close()
            worker.join(timeout=3)

    def test_loopback_apply_changes_only_heading_value(self) -> None:
        server = sync.create_snapshot_server(0, root=self.root, apply=True)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        client = HTTPConnection("127.0.0.1", server.server_port, timeout=3)
        try:
            live = self.source()
            live["pageUrl"] = live.pop("sourceUrl")
            live["pageTitle"] = "Genesis 1 | BSB Bible | YouVersion"
            live.pop("bibleId")
            client.request("POST", "/snapshot", json.dumps(live), {"Content-Type": "application/json"})
            result = json.loads(client.getresponse().read())
            self.assertEqual("apply", result["mode"])
            current = json.loads(self.path.read_text(encoding="utf-8"))
            self.assertEqual(self.book["stories"][0]["summaryBullets"], current["stories"][0]["summaryBullets"])
            self.assertEqual("First section", current["stories"][0]["headings"][0]["text"])
        finally:
            client.close()
            server.shutdown()
            server.server_close()
            worker.join(timeout=3)


if __name__ == "__main__":
    unittest.main()
