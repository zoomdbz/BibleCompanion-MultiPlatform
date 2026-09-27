from __future__ import annotations

import http.client
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock

from sync_modern_semantics_browser import (
    SemanticSyncError, create_snapshot_server, stream_snapshots, sync_snapshot,
)
from sync_modern_scripture_browser import ScriptureSyncError


class SemanticBrowserSyncTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.path = self.root / "shared/assets/books/old_testament/en/genesis.json"
        self.path.parent.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def book(self, bullets: list[str], *, escaped_marker: bool = False,
             language: str = "en", superscription: str | None = None) -> bytes:
        self.path = self.root / f"shared/assets/books/old_testament/{language}/genesis.json"
        self.path.parent.mkdir(parents=True, exist_ok=True)
        first_story = {
            "id": "genesis-1", "summaryBullets": bullets,
            "headings": [], "note": "untouched",
        }
        if superscription is not None:
            first_story["superscription"] = superscription
        payload = {
            "id": "genesis", "sentinel": "untouched",
            "stories": [
                first_story,
                {"id": "genesis-2", "summaryBullets": ["Other. (2:1)."], "headings": []},
            ],
        }
        raw = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
        if escaped_marker:
            raw = raw.replace("(1:1).", r"\u00281:1\u0029.")
        self.path.write_bytes(raw.encode("utf-8"))
        return self.path.read_bytes()

    @staticmethod
    def record(content: str, *, usfm: str = "GEN.1.1") -> dict[str, str]:
        return {
            "kind": "verse", "usfm": usfm,
            "html": (f'<span class="x__verse" data-usfm="{usfm}">'
                     f'<span class="x__label">1</span>{content}</span>'),
        }

    @staticmethod
    def content(text: str, kind: str | None = None) -> str:
        wrapped = f'<span class="x__content">{text}</span>'
        return f'<span class="x__{kind.lower()}">{wrapped}</span>' if kind else wrapped

    def snapshot(self, records: list[dict[str, str]], **changes: object) -> dict[str, object]:
        value: dict[str, object] = {
            "language": "en", "bibleId": 3034, "book": "GEN", "chapter": 1,
            "pageUrl": "https://www.bible.com/bible/3034/GEN.1.BSB",
            "pageTitle": "Genesis 1 BSB", "records": records,
        }
        value.update(changes)
        return value

    def bullet(self) -> str:
        return json.loads(self.path.read_text(encoding="utf-8"))["stories"][0]["summaryBullets"][0]

    def test_audit_adds_source_jesus_words_without_write(self) -> None:
        before = self.book(["He said new words. (1:1)."])
        snapshot = self.snapshot([self.record(self.content("He said true words.", "wj"))])
        result = sync_snapshot(snapshot, root=self.root)
        self.assertEqual("changed", result["status"])
        self.assertEqual("audit", result["mode"])
        self.assertEqual(before, self.path.read_bytes())
        self.assertNotIn("true words", json.dumps(result))
        sync_snapshot(snapshot, root=self.root, apply=True)
        self.assertEqual("[J]He said true words.[/J] (1:1).", self.bullet())

    def test_publisher_prefix_trailing_space_preserves_exact_native_marker(self) -> None:
        before = self.book(["Old wording. (1:1)."])
        with mock.patch(
            "sync_modern_semantics_browser._tagged_source",
            return_value=("New wording.  ", 0, 0),
        ):
            result = sync_snapshot(
                self.snapshot([self.record(self.content("New wording."))]),
                root=self.root,
                apply=True,
            )
        after = self.path.read_bytes()
        self.assertEqual("changed", result["status"])
        self.assertEqual("New wording.   (1:1).", self.bullet())
        marker = b" (1:1)."
        self.assertEqual(before[before.index(marker):], after[after.index(marker):])

    def test_source_moves_existing_jesus_boundary(self) -> None:
        self.book(["Start [J]old words[/J] end. (1:1)."])
        content = self.content("Start ") + self.content("new words end.", "wj")
        sync_snapshot(self.snapshot([self.record(content)]), root=self.root, apply=True)
        self.assertEqual("Start [J]new words end.[/J] (1:1).", self.bullet())

    def test_source_adds_translator_addition_inside_jesus_words(self) -> None:
        self.book(["He said old words. (1:1)."])
        content = (self.content("He said ") + self.content("new", "add")
                   + self.content(" words."))
        nested = f'<span class="x__wj">{content}</span>'
        sync_snapshot(self.snapshot([self.record(nested)]), root=self.root, apply=True)
        self.assertEqual("[J]He said [ADD]new[/ADD] words.[/J] (1:1).", self.bullet())

    def test_source_adds_divine_name_and_retains_exact_source_casing(self) -> None:
        self.book(["The Lord spoke old words. (1:1)."])
        content = self.content("The ") + self.content("LORD", "nd") + self.content(" spoke new words.")
        sync_snapshot(self.snapshot([self.record(content)]), root=self.root, apply=True)
        self.assertEqual("The [DN]LORD[/DN] spoke new words. (1:1).", self.bullet())

    def test_exact_split_superscription_aligns_only_body_semantics(self) -> None:
        self.book(
            ["The LORD speaks. (1:1)."],
            superscription="Psalm of the LORD.",
        )
        title = self.content("Psalm of the ") + self.content("LORD", "nd") + self.content(".")
        body = self.content("The ") + self.content("LORD", "nd") + self.content(" speaks.")

        result = sync_snapshot(
            self.snapshot([self.record(title), self.record(body)]),
            root=self.root,
            apply=True,
        )

        payload = json.loads(self.path.read_text(encoding="utf-8"))
        self.assertEqual("changed", result["status"])
        self.assertEqual("The [DN]LORD[/DN] speaks. (1:1).", self.bullet())
        self.assertNotIn("Psalm of", self.bullet())
        self.assertEqual("Psalm of the LORD.", payload["stories"][0]["superscription"])

    def test_empty_superscription_field_does_not_block_equal_body(self) -> None:
        before = self.book(["Body text. (1:1)."], superscription="")
        result = sync_snapshot(
            self.snapshot([self.record(self.content("Body text."))]),
            root=self.root,
            apply=True,
        )
        self.assertEqual("match", result["status"])
        self.assertEqual(before, self.path.read_bytes())

    def test_superscription_alignment_blocks_changed_or_single_fragment_source(self) -> None:
        cases = (
            (["Changed title.", "Body text."], "changed superscription"),
            (["Psalm title.", "Changed body."], "changed body"),
            (["Psalm title. Body text."], "single combined fragment"),
        )
        for fragments, label in cases:
            with self.subTest(label=label):
                before = self.book(["Body text. (1:1)."], superscription="Psalm title.")
                records = [self.record(self.content(fragment)) for fragment in fragments]
                with self.assertRaisesRegex(SemanticSyncError, "split-text proof"):
                    sync_snapshot(self.snapshot(records), root=self.root, apply=True)
                self.assertEqual(before, self.path.read_bytes())

    def test_source_remaps_addition_and_divine_name_boundaries(self) -> None:
        self.book(["The [DN]LORD[/DN] spoke [ADD]old[/ADD] words. (1:1)."])
        content = (self.content("The ") + self.content("LORD", "nd")
                   + self.content(" spoke ") + self.content("new words", "add")
                   + self.content("."))
        sync_snapshot(self.snapshot([self.record(content)]), root=self.root, apply=True)
        self.assertEqual("The [DN]LORD[/DN] spoke [ADD]new words[/ADD]. (1:1).", self.bullet())

    def test_broad_source_divine_name_wrapper_blocks_without_guessing(self) -> None:
        before = self.book(["The LORD spoke old words. (1:1)."])
        content = self.content("The ") + self.content("LORD spoke", "nd") + self.content(" new words.")
        with self.assertRaisesRegex(ScriptureSyncError, "unproven lexical content"):
            sync_snapshot(self.snapshot([self.record(content)]), root=self.root, apply=True)
        self.assertEqual(before, self.path.read_bytes())

    def test_runtime_divine_name_survives_absent_source_nd(self) -> None:
        self.book(["The LORD spoke old words. (1:1)."])
        sync_snapshot(
            self.snapshot([self.record(self.content("The LORD spoke new words."))]),
            root=self.root, apply=True,
        )
        self.assertEqual("The LORD spoke new words. (1:1).", self.bullet())

    def test_runtime_divine_name_change_without_nd_blocks(self) -> None:
        before = self.book(["The LORD spoke old words. (1:1)."])
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(self.snapshot([self.record(self.content("The Lord spoke new words."))]),
                          root=self.root, apply=True)
        self.assertEqual(before, self.path.read_bytes())

    def test_rendered_nd_allows_exact_source_presentation(self) -> None:
        self.book(["The LORD spoke old words. (1:1)."])
        content = self.content("The ") + self.content("Lord", "nd") + self.content(" spoke new words.")
        sync_snapshot(self.snapshot([self.record(content)]), root=self.root, apply=True)
        self.assertEqual("The [DN]Lord[/DN] spoke new words. (1:1).", self.bullet())

    def test_exact_publisher_nd_marks_german_gott_gottes_and_french_dieu(self) -> None:
        cases = (
            (
                "de", 157, "SCH2000",
                "Und ", "Gott", " sprach zu ihnen.",
            ),
            (
                "de", 157, "SCH2000",
                "Dreimal im Jahr sollen alle deine Männer erscheinen vor dem Angesicht ",
                "Gottes", ", des Herrn!",
            ),
            (
                "fr", 104, "NBS",
                "Abram répondit : Seigneur ", "Dieu", ", que me donneras-tu ?",
            ),
        )
        original_root = self.root
        try:
            for index, (language, bible_id, abbreviation, before, name, after) in enumerate(cases):
                with self.subTest(language=language):
                    self.root = original_root / f"publisher-dn-{index}"
                    self.book([f"{before}{name}{after} (1:1)."], language=language)
                    content = self.content(before) + self.content(name, "nd") + self.content(after)
                    result = sync_snapshot(
                        self.snapshot(
                            [self.record(content)],
                            language=language,
                            bibleId=bible_id,
                            pageUrl=(
                                f"https://www.bible.com/bible/{bible_id}/GEN.1.{abbreviation}"
                            ),
                        ),
                        root=self.root,
                        apply=True,
                    )
                    self.assertEqual("changed", result["status"])
                    self.assertEqual(f"{before}[DN]{name}[/DN]{after} (1:1).", self.bullet())
        finally:
            self.root = original_root

    def test_local_dn_without_source_nd_blocks_across_wording_change(self) -> None:
        before = self.book(["The [DN]LORD[/DN] spoke old words. (1:1)."])
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(self.snapshot([self.record(self.content("The LORD spoke new words."))]),
                          root=self.root, apply=True)
        self.assertEqual(before, self.path.read_bytes())

    def test_absent_source_wrapper_blocks_local_tag_across_wording_change(self) -> None:
        before = self.book(["Start [ADD]blue[/ADD] end old. (1:1)."])
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(
                self.snapshot([self.record(self.content("Start blue end new."))]),
                root=self.root, apply=True,
            )
        self.assertEqual(before, self.path.read_bytes())

    def test_full_range_jesus_tag_survives_space_run_change_without_wj(self) -> None:
        self.book(["[J]Jesus  spoke these words.[/J] (1:1)."])
        result = sync_snapshot(
            self.snapshot([self.record(self.content("Jesus\u202fspoke these words."))]),
            root=self.root,
            apply=True,
        )
        self.assertEqual("changed", result["status"])
        self.assertEqual(
            "[J]Jesus\u202fspoke these words.[/J] (1:1).",
            self.bullet(),
        )

    def test_full_range_jesus_tag_drops_outer_unicode_space_without_wj(self) -> None:
        self.book(["[J]Jesus spoke these words.\u202f[/J] (1:1)."])
        result = sync_snapshot(
            self.snapshot([self.record(self.content("Jesus spoke these words."))]),
            root=self.root,
            apply=True,
        )
        self.assertEqual("changed", result["status"])
        self.assertEqual("[J]Jesus spoke these words.[/J] (1:1).", self.bullet())

    def test_full_range_jesus_tag_blocks_lexical_change_without_wj(self) -> None:
        before = self.book(["[J]Jesus spoke the old wording.[/J] (1:1)."])
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(
                self.snapshot([self.record(self.content("Jesus spoke unrelated wording."))]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, self.path.read_bytes())

    def test_multiple_jesus_spans_still_block_without_source_wj(self) -> None:
        before = self.book(["[J]One[/J] middle [J]two[/J]. (1:1)."])
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(
                self.snapshot([self.record(self.content("New unrelated wording."))]),
                root=self.root,
                apply=True,
            )
        self.assertEqual(before, self.path.read_bytes())

    def test_absent_source_wrapper_blocks_missing_anchor(self) -> None:
        before = self.book(["Start [J]old[/J] end. (1:1)."])
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(self.snapshot([self.record(self.content("New unrelated text."))]),
                          root=self.root, apply=True)
        self.assertEqual(before, self.path.read_bytes())

    def test_absent_source_wrapper_preserves_local_tag_when_plain_text_is_identical(self) -> None:
        before = self.book(["x a [J]b[/J] c x a b c. (1:1)."])
        result = sync_snapshot(
            self.snapshot([self.record(self.content("x a b c x a b c."))]),
            root=self.root,
            apply=True,
        )
        self.assertEqual("match", result["status"])
        self.assertEqual(before, self.path.read_bytes())

    def test_absent_source_wrapper_preserves_only_position_safe_zs_change(self) -> None:
        self.book(["[J]Il dit : oui.[/J] (1:1)."])
        sync_snapshot(
            self.snapshot([self.record(self.content("Il\u00a0dit\u00a0:\u00a0oui."))]),
            root=self.root,
            apply=True,
        )
        self.assertEqual("[J]Il\u00a0dit\u00a0:\u00a0oui.[/J] (1:1).", self.bullet())

    def test_wrong_book_payload_root_is_rejected_without_write(self) -> None:
        before = self.book(["Old text. (1:1)."])
        payload = json.loads(self.path.read_text(encoding="utf-8"))
        payload["id"] = "exodus"
        payload["stories"][0]["id"] = "exodus-1"
        self.path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        wrong = self.path.read_bytes()
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(
                self.snapshot([self.record(self.content("New text."))]),
                root=self.root,
                apply=True,
            )
        self.assertNotEqual(before, wrong)
        self.assertEqual(wrong, self.path.read_bytes())

    def test_marker_and_raw_confinement(self) -> None:
        before = self.book(["Old text. (1:1)."], escaped_marker=True)
        result = sync_snapshot(self.snapshot([self.record(self.content("New text."))]),
                               root=self.root, apply=True)
        after = self.path.read_bytes()
        self.assertIn(rb"New text. \u00281:1\u0029.", after)
        self.assertEqual(before[before.index(rb"\u00281:1\u0029."):],
                         after[after.index(rb"\u00281:1\u0029."):])
        self.assertEqual("New text. (1:1).", self.bullet())
        self.assertEqual(64, len(result["untouchedRawSegmentsSha256"]))

    def test_identity_range_and_race_guards(self) -> None:
        before = self.book(["Old text. (1:1)."])
        records = [self.record(self.content("New text."))]
        for change in ({"bibleId": 1}, {"pageUrl": "https://evil.test/bible/3034/GEN.1.BSB"},
                       {"pageTitle": "Client Challenge"}):
            with self.subTest(change=change), self.assertRaises(ScriptureSyncError):
                sync_snapshot(self.snapshot(records, **change), root=self.root, apply=True)
        with self.assertRaises(ScriptureSyncError):
            sync_snapshot(self.snapshot([self.record(self.content("New text."), usfm="GEN.1.2")]),
                          root=self.root, apply=True)
        self.assertEqual(before, self.path.read_bytes())
        real_read = Path.read_bytes
        calls = 0

        def race(path: Path) -> bytes:
            nonlocal calls
            value = real_read(path)
            if path == self.path:
                calls += 1
                if calls == 2:
                    return value + b" "
            return value

        with mock.patch.object(Path, "read_bytes", race), self.assertRaises(SemanticSyncError):
            sync_snapshot(self.snapshot(records), root=self.root, apply=True)
        self.assertEqual(before, self.path.read_bytes())

    def test_preserves_bridged_range_without_split(self) -> None:
        self.book(["Old. (1:1-2)."])
        result = sync_snapshot(
            self.snapshot([self.record(self.content("New.", "wj"), usfm="GEN.1.1+GEN.1.2")]),
            root=self.root, apply=True,
        )
        self.assertEqual(1, result["nativeRangeCount"])
        self.assertEqual("[J]New.[/J] (1:1-2).", self.bullet())

    def test_stream_blocker_is_sanitized(self) -> None:
        self.book(["Start [J]SECRET[/J] end. (1:1)."])
        snapshot = self.snapshot([self.record(self.content("Unrelated publisher text."))])
        sink = io.StringIO()
        status = stream_snapshots(io.StringIO(json.dumps(snapshot) + "\n"), sink, root=self.root)
        self.assertEqual(2, status)
        self.assertEqual("blocked", json.loads(sink.getvalue())["status"])
        self.assertNotIn("SECRET", sink.getvalue())
        self.assertNotIn("publisher", sink.getvalue())

    def test_loopback_server_audits_by_default(self) -> None:
        before = self.book(["Old text. (1:1)."])
        server = create_snapshot_server(0, root=self.root)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port)
            try:
                body = json.dumps(self.snapshot([self.record(self.content("New text."))]))
                connection.request("POST", "/snapshot", body=body,
                                   headers={"Content-Type": "application/json"})
                response = connection.getresponse()
                result = json.loads(response.read())
            finally:
                connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=1)
        self.assertEqual(200, response.status)
        self.assertEqual("audit", result["mode"])
        self.assertEqual(before, self.path.read_bytes())


if __name__ == "__main__":
    unittest.main()
