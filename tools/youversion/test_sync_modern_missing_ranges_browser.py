from __future__ import annotations

import http.client
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock

from compare_modern_editions_browser import BrowserAuditError
from sync_modern_missing_ranges_browser import (
    MissingRangeSyncError, create_snapshot_server, stream_snapshots, sync_snapshot,
)


class MissingRangeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def book(self, bullets: list[str], *, language: str = "fr", headings=None,
             transform=None) -> Path:
        payload = {"id": "genesis", "sentinel": "KEEP", "stories": [
            {"id": "genesis-1", "summaryBullets": bullets, "headings": headings or [],
             "crossRefs": ["KEEP (1:2)."], "notes": "KEEP"},
            {"id": "genesis-2", "summaryBullets": ["KEEP (2:1)."]},
        ]}
        raw = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        if transform:
            raw = transform(raw)
        path = self.root / "shared/assets/books/old_testament" / language / "genesis.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw.encode("utf-8"))
        return path

    @staticmethod
    def verse(number: str, contents: str, *, usfm: str | None = None) -> dict[str, str]:
        ref = usfm or f"GEN.1.{number}"
        return {"kind": "verse", "usfm": ref, "html":
                f'<span class="x__verse" data-usfm="{ref}"><span class="x__label">{number}</span>'
                f'<span class="x__content">{contents}</span></span>'}

    @staticmethod
    def snapshot(records, *, language="fr", bible_id=104, abbreviation="NBS", **changes):
        result = {"language": language, "bibleId": bible_id, "book": "GEN", "chapter": 1,
                  "pageUrl": f"https://www.bible.com/bible/{bible_id}/GEN.1.{abbreviation}",
                  "pageTitle": "Genesis 1", "records": records}
        result.update(changes)
        return result

    def bullets(self, path: Path) -> list[str]:
        return json.loads(path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"]

    def test_french_omitted_bracketed_verse_and_audit_default(self) -> None:
        path = self.book(["Avant. (1:1).", "Après. (1:3)."])
        before = path.read_bytes()
        snapshot = self.snapshot([self.verse("1", "Avant."), self.verse("2", "[Texte omis]"),
                                  self.verse("3", "Après.")])
        result = sync_snapshot(snapshot, root=self.root)
        self.assertEqual("audit", result["mode"])
        self.assertEqual(1, result["insertedNativeUnitCount"])
        self.assertEqual(before, path.read_bytes())
        sync_snapshot(snapshot, root=self.root, apply=True)
        self.assertEqual(["Avant. (1:1).", "[Texte omis] (1:2).", "Après. (1:3)."], self.bullets(path))
        self.assertIn(b'"crossRefs": [\n        "KEEP (1:2)."', path.read_bytes())

    def test_insert_before_middle_end_and_whole_bridge(self) -> None:
        path = self.book(["Deux. (1:2).", "Quatre. (1:4)."])
        records = [self.verse("1", "Un."), self.verse("2", "Deux."),
                   self.verse("3", "Trois."), self.verse("4", "Quatre."),
                   self.verse("5", "Cinq et six.", usfm="GEN.1.5-GEN.1.6")]
        sync_snapshot(self.snapshot(records), root=self.root, apply=True)
        self.assertEqual(["Un. (1:1).", "Deux. (1:2).", "Trois. (1:3).",
                          "Quatre. (1:4).", "Cinq et six. (1:5-6)."], self.bullets(path))

    def test_source_j_add_dn_wrappers_are_encoded(self) -> None:
        path = self.book(["Avant. (1:1)."])
        verse = self.verse("2", '<span class="x__wj">Jésus <span class="x__add">vraiment</span></span> '
                               '<span class="x__nd">Seigneur</span>.')
        sync_snapshot(self.snapshot([self.verse("1", "Avant."), verse]), root=self.root, apply=True)
        self.assertEqual("[J]Jésus [ADD]vraiment[/ADD][/J] [DN]Seigneur[/DN]. (1:2).", self.bullets(path)[1])

    def test_local_non_subset_and_crossing_block(self) -> None:
        for bullets in (["Un. (1:1-2)."], ["Un. (1:1).", "Trois. (1:3)."]):
            with self.subTest(bullets=bullets):
                path = self.book(bullets)
                before = path.read_bytes()
                with self.assertRaises(MissingRangeSyncError):
                    sync_snapshot(self.snapshot([self.verse("1", "Un."), self.verse("2", "Deux.")]),
                                  root=self.root, apply=True)
                self.assertEqual(before, path.read_bytes())

    def test_suffix_marker_and_heading_ambiguity_block(self) -> None:
        cases = [
            (["Un. (1:1)."], [self.verse("1", "Un."), self.verse("2", "Deux.", usfm="GEN.1.2a")], None),
            (["Un. (1:1a)."], [self.verse("1", "Un."), self.verse("2", "Deux.")], None),
            (["Un. (1:1).", "Trois. （1：3）。"], [self.verse("1", "Un."), self.verse("2", "Deux."), self.verse("3", "Trois.")], None),
            (["Un. (1:1).", "Trois. (1:3)."], [self.verse("1", "Un."), self.verse("2", "Deux."), self.verse("3", "Trois.")], [{"beforeVerse": 2, "text": "Heading"}]),
        ]
        for index, (bullets, records, headings) in enumerate(cases):
            with self.subTest(index=index):
                path = self.book(bullets, headings=headings)
                before = path.read_bytes()
                with self.assertRaises((MissingRangeSyncError, BrowserAuditError, ValueError)):
                    sync_snapshot(self.snapshot(records), root=self.root, apply=True)
                self.assertEqual(before, path.read_bytes())

    def test_source_heading_at_missing_verse_blocks(self) -> None:
        path = self.book(["Un. (1:1)."])
        records = [self.verse("1", "Un."), {"kind": "heading", "html": '<h2 class="heading">New heading</h2>'},
                   self.verse("2", "Deux.")]
        with self.assertRaises(MissingRangeSyncError):
            sync_snapshot(self.snapshot(records), root=self.root, apply=True)
        self.assertEqual(["Un. (1:1)."], self.bullets(path))

    def test_raw_confinement_and_race_check(self) -> None:
        path = self.book(["Un. (1:1)."], transform=lambda raw: raw.replace('"KEEP"', '"\\u004bEEP"'))
        before = path.read_bytes()
        snapshot = self.snapshot([self.verse("1", "Un."), self.verse("2", "Deux.")])
        sync_snapshot(snapshot, root=self.root, apply=True)
        after = path.read_bytes()
        self.assertIn(b'"\\u004bEEP"', after)
        self.assertEqual(before, after.replace(b',\n        "Deux. (1:2)."', b''))

        original_read = Path.read_bytes
        calls = 0

        def race(target: Path) -> bytes:
            nonlocal calls
            value = original_read(target)
            if target == path:
                calls += 1
                if calls == 2:
                    return value + b" "
            return value

        with mock.patch.object(Path, "read_bytes", race), self.assertRaises(MissingRangeSyncError):
            sync_snapshot(self.snapshot([self.verse("1", "Un."), self.verse("2", "Deux."),
                                         self.verse("3", "Trois.")]), root=self.root, apply=True)

    def test_identity_and_sanitized_stream_and_loopback(self) -> None:
        self.book(["Un. (1:1)."])
        snapshot = self.snapshot([self.verse("1", "Un."), self.verse("2", "Deux.")])
        for changed in ({"bibleId": 83}, {"pageUrl": "http://www.bible.com/bible/104/GEN.1.NBS"},
                        {"pageTitle": "Captcha"}):
            with self.assertRaises(ValueError):
                sync_snapshot({**snapshot, **changed}, root=self.root, apply=True)
        secret = "SECRET_PUBLISHER_TEXT"
        sink = io.StringIO()
        self.assertEqual(2, stream_snapshots(io.StringIO('{"records":"' + secret + '"\n'), sink, root=self.root))
        self.assertNotIn(secret, sink.getvalue())
        server = create_snapshot_server(0, root=self.root)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5)
            connection.request("POST", "/snapshot", body=json.dumps(snapshot).encode(),
                               headers={"Content-Type": "application/json"})
            response = connection.getresponse()
            result = json.loads(response.read())
            self.assertEqual(200, response.status)
            self.assertEqual("no-store", response.getheader("Cache-Control"))
            self.assertEqual("audit", result["mode"])
            self.assertNotIn("Deux.", json.dumps(result))
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_copied_wrong_book_payload_blocks_before_insertion(self) -> None:
        path = self.book(["Un. (1:1)."])
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["id"] = "exodus"
        payload["stories"][0]["id"] = "exodus-1"
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        before = path.read_bytes()
        with self.assertRaises(ValueError):
            sync_snapshot(self.snapshot([self.verse("1", "Un."), self.verse("2", "Deux.")]),
                          root=self.root, apply=True)
        self.assertEqual(before, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
