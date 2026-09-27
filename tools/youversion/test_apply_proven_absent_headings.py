"""Focused fail-closed tests for corpus-proven heading deletion."""

from __future__ import annotations

from contextlib import ExitStack, contextmanager
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import apply_proven_absent_headings as subject  # noqa: E402


def _json_bytes(value: object, *, bom: bool = False, crlf: bool = False) -> bytes:
    text = json.dumps(value, ensure_ascii=False, indent=2)
    if crlf:
        text = text.replace("\n", "\r\n")
    raw = (text + ("\r\n" if crlf else "\n")).encode("utf-8")
    return (b"\xef\xbb\xbf" + raw) if bom else raw


class ProvenAbsentHeadingTests(unittest.TestCase):
    def _make_fixture(
        self,
        root: Path,
        *,
        candidates: set[int],
        positives: set[int],
    ) -> tuple[Path, tuple[tuple[str, int], ...]]:
        chapters = sorted(candidates | positives)
        references = tuple(("GEN", chapter) for chapter in chapters)
        stories = []
        for chapter in chapters:
            stories.append({
                "id": f"genesis-{chapter}",
                "title": f"Genesis {chapter}",
                "summaryBullets": [
                    f"[J]Spoken[/J] [DN]LORD[/DN] [ADD]supplied[/ADD] ({chapter}:1)"
                ],
                "headings": [{"beforeVerse": 1, "text": f"Heading {chapter}"}],
            })
        asset = root / "shared/assets/books/old_testament/en/genesis.json"
        asset.parent.mkdir(parents=True)
        asset.write_bytes(_json_bytes({"id": "genesis", "stories": stories}, bom=True, crlf=True))

        parity_folder = root / "parity/en"
        heading_folder = root / "headings/en"
        parity_folder.mkdir(parents=True)
        heading_folder.mkdir(parents=True)
        for chapter, story in zip(chapters, stories):
            reference = f"GEN.{chapter}"
            envelope = {
                "evidenceSchemaVersion": 1,
                "language": "en",
                "bibleId": 3034,
                "reference": reference,
                "snapshotSha256": hashlib.sha256(reference.encode("ascii")).hexdigest(),
                "localStorySha256": subject._canonical_json_sha256(story),
            }
            positive = chapter in positives
            parity = {
                "language": "en",
                "bibleId": 3034,
                "reference": reference,
                "sourceUrl": f"https://www.bible.com/bible/3034/{reference}.BSB",
                "status": "match",
                "localRanges": 1,
                "sourceRanges": 1,
                "rangesCompared": 1,
                "headingComparisonAvailable": positive,
                "localHeadings": 1,
                "sourceHeadings": 1 if positive else 0,
                "sourceHeadingLines": 1 if positive else 0,
                "sourceOmissions": 0,
                "emptyRanges": 0,
                "findings": [],
                **envelope,
            }
            heading = (
                {"status": "match", **envelope}
                if positive
                else {
                    "status": "blocked",
                    "errorCode": subject.ABSENCE_ERROR,
                    **envelope,
                }
            )
            (parity_folder / f"{reference}.json").write_bytes(_json_bytes(parity))
            (heading_folder / f"{reference}.json").write_bytes(_json_bytes(heading))
        return asset, references

    @contextmanager
    def _patched(self, root: Path, references: tuple[tuple[str, int], ...]):
        with ExitStack() as stack:
            stack.enter_context(mock.patch.multiple(
                subject,
                ROOT=root,
                PARITY_ROOT=root / "parity",
                HEADING_ROOT=root / "headings",
                LOCK_PATH=root / "cache/apply.lock",
                MIN_POSITIVE_HEADING_CHAPTERS=1,
            ))
            stack.enter_context(mock.patch.object(
                subject, "_expected_references", return_value=references
            ))
            yield

    def test_exact_inventory_rejects_genesis_999(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _asset, references = self._make_fixture(root, candidates={2}, positives={1})
            rogue = root / "parity/en/GEN.999.json"
            rogue.write_text("{}\n", encoding="utf-8")
            with self._patched(root, references):
                with self.assertRaisesRegex(
                    subject.ProvenHeadingAbsenceError,
                    "exact canonical 1,189 references",
                ):
                    subject.run(["en"], apply=False)

    def test_mixed_or_stale_evidence_fails_closed(self):
        for mutation in ("mixed_snapshot", "stale_schema"):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                _asset, references = self._make_fixture(root, candidates={2}, positives={1})
                path = root / "headings/en/GEN.2.json"
                checkpoint = json.loads(path.read_text(encoding="utf-8"))
                if mutation == "mixed_snapshot":
                    checkpoint["snapshotSha256"] = "f" * 64
                else:
                    checkpoint.pop("evidenceSchemaVersion")
                path.write_bytes(_json_bytes(checkpoint))
                with self._patched(root, references):
                    with self.assertRaises(subject.ProvenHeadingAbsenceError):
                        subject.run(["en"], apply=False)

    def test_locked_source_defect_is_evidenced_but_never_a_deletion_candidate(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            genesis_story = {
                "id": "genesis-1", "summaryBullets": ["Text. (1:1)."],
                "headings": [{"beforeVerse": 1, "text": "Source heading"}],
            }
            luke_story = {
                "id": "luke-7",
                "summaryBullets": [f"Text {verse}. (7:{verse})." for verse in range(1, 51)],
                "headings": [
                    {"beforeVerse": verse, "text": f"Local {verse}"}
                    for verse in (1, 11, 18, 36)
                ],
            }
            assets = (
                (root / "shared/assets/books/old_testament/ar/genesis.json",
                 {"id": "genesis", "stories": [genesis_story]}),
                (root / "shared/assets/books/new_testament/ar/luke.json",
                 {"id": "luke", "stories": [luke_story]}),
            )
            for path, payload in assets:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(_json_bytes(payload))
            parity_folder = root / "parity/ar"
            heading_folder = root / "headings/ar"
            parity_folder.mkdir(parents=True)
            heading_folder.mkdir(parents=True)

            def envelope(code: str, chapter: int, story: dict) -> dict:
                reference = f"{code}.{chapter}"
                return {
                    "evidenceSchemaVersion": 1, "language": "ar", "bibleId": 153,
                    "reference": reference,
                    "snapshotSha256": hashlib.sha256(reference.encode("ascii")).hexdigest(),
                    "localStorySha256": subject._canonical_json_sha256(story),
                }

            genesis_envelope = envelope("GEN", 1, genesis_story)
            genesis_parity = {
                "language": "ar", "bibleId": 153, "reference": "GEN.1",
                "sourceUrl": "https://www.bible.com/bible/153/GEN.1.SAB",
                "status": "match", "localRanges": 1, "sourceRanges": 1,
                "rangesCompared": 1, "headingComparisonAvailable": True,
                "localHeadings": 1, "sourceHeadings": 1, "sourceHeadingLines": 1,
                "sourceOmissions": 0, "emptyRanges": 0, "findings": [],
                **genesis_envelope,
            }
            luke_envelope = envelope("LUK", 7, luke_story)
            luke_findings = [
                {"kind": "local_range_only", "reference": "LUK.7.15-15"},
                *[
                    {"kind": "heading_text_mismatch", "reference": f"LUK.7.{verse}#{index}"}
                    for index, verse in enumerate((1, 11, 18, 36), start=1)
                ],
            ]
            luke_parity = {
                "language": "ar", "bibleId": 153, "reference": "LUK.7",
                "sourceUrl": "https://www.bible.com/bible/153/LUK.7.SAB",
                "status": "source_defect", "sourceParityClaimed": False,
                "sourceDefectCode": "locked_sab_luk_7_15_empty_native_range",
                "sourceDefectRanges": ["LUK.7.15"],
                "localSourceDefectVersePreserved": True,
                "localRanges": 50, "sourceRanges": 49, "rangesCompared": 49,
                "headingComparisonAvailable": True, "localHeadings": 4,
                "sourceHeadings": 4, "sourceHeadingLines": 4,
                "sourceOmissions": 0, "emptyRanges": 1, "findings": luke_findings,
                **luke_envelope,
            }
            checkpoints = (
                (parity_folder / "GEN.1.json", genesis_parity),
                (heading_folder / "GEN.1.json", {"status": "match", **genesis_envelope}),
                (parity_folder / "LUK.7.json", luke_parity),
                (heading_folder / "LUK.7.json", {
                    "status": "not_needed",
                    "reason": "locked_sab_luk_7_15_empty_native_range",
                    **luke_envelope,
                }),
            )
            for path, value in checkpoints:
                path.write_bytes(_json_bytes(value))
            references = (("GEN", 1), ("LUK", 7))
            with self._patched(root, references):
                report = subject.run(["ar"], apply=False)
            self.assertEqual(0, report["ar"]["changedChapters"])

            luke_parity["sourceParityClaimed"] = True
            (parity_folder / "LUK.7.json").write_bytes(_json_bytes(luke_parity))
            with self._patched(root, references):
                with self.assertRaisesRegex(
                    subject.ProvenHeadingAbsenceError, "source-defect evidence changed"
                ):
                    subject.run(["ar"], apply=False)

    def test_same_count_asset_mutation_invalidates_story_hash(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            asset, references = self._make_fixture(root, candidates={2}, positives={1})
            payload = json.loads(asset.read_bytes().decode("utf-8-sig"))
            payload["stories"][1]["summaryBullets"][0] = (
                "[J]Changed[/J] [DN]LORD[/DN] [ADD]supplied[/ADD] (2:1)"
            )
            asset.write_bytes(_json_bytes(payload, bom=True, crlf=True))
            with self._patched(root, references):
                with self.assertRaisesRegex(
                    subject.ProvenHeadingAbsenceError,
                    "story differs from checkpoint evidence",
                ):
                    subject.run(["en"], apply=False)

    def test_checkpoint_mutation_after_planning_blocks_every_asset_write(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            asset, references = self._make_fixture(root, candidates={2}, positives={1})
            original_asset = asset.read_bytes()
            original_stage = subject._stage_plans

            def stage_then_mutate(plans):
                staged = original_stage(plans)
                path = root / "headings/en/GEN.2.json"
                path.write_bytes(path.read_bytes() + b" ")
                return staged

            with self._patched(root, references), mock.patch.object(
                subject, "_stage_plans", side_effect=stage_then_mutate
            ):
                with self.assertRaisesRegex(
                    subject.ProvenHeadingAbsenceError,
                    "checkpoint changed after first-pass validation",
                ):
                    subject.run(["en"], apply=True)
            self.assertEqual(asset.read_bytes(), original_asset)
            self.assertFalse((root / "cache/apply.lock").exists())
            self.assertEqual(list(asset.parent.glob(".heading-absence-*.stage")), [])

    def test_grouped_plan_changes_only_heading_bytes_and_preserves_tags(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            asset, references = self._make_fixture(root, candidates={1, 2}, positives={3})
            captured: list[subject.FilePlan] = []
            original_builder = subject._build_plans

            def capture(candidates, assets):
                plans = original_builder(candidates, assets)
                captured.extend(plans)
                return plans

            with self._patched(root, references), mock.patch.object(
                subject, "_build_plans", side_effect=capture
            ):
                report = subject.run(["en"], apply=False)
            self.assertEqual(report["en"]["changedChapters"], 2)
            self.assertEqual(report["en"]["changedFiles"], 1)
            self.assertEqual(len(captured), 1)
            plan = captured[0]
            self.assertTrue(plan.updated.startswith(b"\xef\xbb\xbf"))
            self.assertIn(b"\r\n", plan.updated)
            before = json.loads(plan.original.decode("utf-8-sig"))
            after = json.loads(plan.updated.decode("utf-8-sig"))
            self.assertEqual(after["stories"][0]["headings"], [])
            self.assertEqual(after["stories"][1]["headings"], [])
            self.assertEqual(after["stories"][2]["headings"], before["stories"][2]["headings"])
            for index in range(3):
                self.assertEqual(
                    after["stories"][index]["summaryBullets"],
                    before["stories"][index]["summaryBullets"],
                )
            for tag in (b"[J]", b"[/J]", b"[DN]", b"[/DN]", b"[ADD]", b"[/ADD]"):
                self.assertEqual(plan.updated.count(tag), plan.original.count(tag))
            self.assertEqual(asset.read_bytes(), plan.original)

    def test_multi_file_commit_failure_rolls_back_every_replacement(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            first = root / "first.json"
            second = root / "second.json"
            first.write_bytes(b"first-original")
            second.write_bytes(b"second-original")
            first_stage = root / "first.stage"
            second_stage = root / "second.stage"
            first_stage.write_bytes(b"first-updated")
            second_stage.write_bytes(b"second-updated")
            plans = [
                subject.FilePlan(first, b"first-original", b"first-updated", ()),
                subject.FilePlan(second, b"second-original", b"second-updated", ()),
            ]
            staged = {first: first_stage, second: second_stage}
            real_replace = subject.os.replace

            def fail_second_stage(source, destination):
                if Path(source) == second_stage:
                    raise OSError("injected replacement failure")
                return real_replace(source, destination)

            with mock.patch.object(subject.os, "replace", side_effect=fail_second_stage):
                with self.assertRaisesRegex(OSError, "injected replacement failure"):
                    subject._commit_staged(plans, staged)
            self.assertEqual(first.read_bytes(), b"first-original")
            self.assertEqual(second.read_bytes(), b"second-original")
            self.assertEqual(list(root.glob(".heading-absence-*.backup")), [])


if __name__ == "__main__":
    unittest.main()
