import json
import tempfile
import unittest
from pathlib import Path

from jesus_word_spans import JesusSpanError, ReviewedJesusSpans, sha256_text


class JesusWordSpanTests(unittest.TestCase):
    def relocation_ledger(self, root: Path, duplicate: bool = False, orphan: bool = False) -> ReviewedJesusSpans:
        path = root / "tools" / "traditional" / "jesus_word_spans" / "de_luther1912.json"
        path.parent.mkdir(parents=True)
        source = ("new_testament", "matthew", 9, 30)
        target = ("new_testament", "matthew", 9, 31)
        raw_source = "Jesus said:"
        raw_target = "Go! Then they left."
        review = {"status": "reviewed", "evidence": "Pinned KJV speech and target-language review"}
        authority = dict(zip(("collection", "bookId", "chapter", "verse"), source))
        rows = [{**authority, "sourceTextSha256": sha256_text(raw_source), "spans": [],
                 "speechRelocatedTo": {"chapter": 9, "verse": 31,
                                       "targetTextSha256": sha256_text(raw_target),
                                       "exactText": "Go!", "fulfillment": "supplemental"},
                 "review": review},
                {**dict(zip(("collection", "bookId", "chapter", "verse"), target)),
                 "sourceTextSha256": sha256_text(raw_target), "spans": [{"exactText": "Go!"}],
                 "supplementalFor": [authority], "review": review}]
        if orphan:
            rows[0]["speechRelocatedTo"]["fulfillment"] = "inherited"
        if duplicate:
            rows[1]["supplementalFor"].append(authority)
        path.write_text(json.dumps({"schemaVersion": 1, "language": "de", "editionId": "luther1912", "rows": rows}), encoding="utf-8")
        return ReviewedJesusSpans.load(root, "de", "luther1912")

    def make_ledger(self, root: Path, text: str, spans: list[str | dict]) -> ReviewedJesusSpans:
        path = root / "tools" / "traditional" / "jesus_word_spans" / "de_luther1912.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps({
            "schemaVersion": 1,
            "language": "de",
            "editionId": "luther1912",
            "rows": [{
                "collection": "new_testament",
                "bookId": "matthew",
                "chapter": 4,
                "verse": 4,
                "sourceTextSha256": sha256_text(text),
                "spans": [span if isinstance(span, dict) else {"exactText": span} for span in spans],
                "review": {"status": "reviewed", "evidence": "reviewed German speech boundary"},
            }],
        }), encoding="utf-8")
        return ReviewedJesusSpans.load(root, "de", "luther1912")

    def test_wraps_exact_speech_without_touching_narration(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = "Jesus sagte: Gehe! Die Leute hörten zu. Komm zurück!"
            ledger = self.make_ledger(Path(directory), raw, ["Gehe!", "Komm zurück!"])
            tagged = ledger.apply("new_testament", "matthew", 4, 4, raw)
            self.assertEqual(
                tagged,
                "Jesus sagte: [J]Gehe![/J] Die Leute hörten zu. [J]Komm zurück![/J]",
            )
            ledger.validate_coverage({("new_testament", "matthew", 4, 4)})

    def test_splits_speech_around_existing_markers(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = "Er sprach: Ich bin [DN]YHWH[/DN] und [ADD]der[/ADD] Herr."
            ledger = self.make_ledger(Path(directory), raw, ["Ich bin YHWH und der Herr."])
            tagged = ledger.apply("new_testament", "matthew", 4, 4, raw)
            self.assertEqual(
                tagged,
                "Er sprach: [J]Ich bin [/J][DN][J]YHWH[/J][/DN][J] und [/J][ADD][J]der[/J][/ADD][J] Herr.[/J]",
            )
            self.assertEqual(tagged.replace("[J]", "").replace("[/J]", ""), raw)

    def test_rejects_text_drift_and_incomplete_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = "Narration: Speech."
            ledger = self.make_ledger(Path(directory), raw, ["Speech."])
            with self.assertRaisesRegex(JesusSpanError, "drift"):
                ledger.apply("new_testament", "matthew", 4, 4, "Narration: speech.")
            with self.assertRaisesRegex(JesusSpanError, "Incomplete"):
                ledger.validate_coverage({("new_testament", "matthew", 4, 4)})

    def test_rejects_ambiguous_or_overlapping_spans(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = "Go. Go."
            ledger = self.make_ledger(Path(directory), raw, ["Go."])
            with self.assertRaisesRegex(JesusSpanError, "Repeated speech"):
                ledger.apply("new_testament", "matthew", 4, 4, raw)

    def test_repeated_exact_speech_uses_explicit_occurrences(self):
        with tempfile.TemporaryDirectory() as directory:
            raw = "Jesus said: Go! Peter repeated: Go!"
            ledger = self.make_ledger(Path(directory), raw, [
                {"exactText": "Go!", "occurrence": 1},
                {"exactText": "Go!", "occurrence": 2},
            ])
            self.assertEqual(
                ledger.apply("new_testament", "matthew", 4, 4, raw),
                "Jesus said: [J]Go![/J] Peter repeated: [J]Go![/J]",
            )

    def test_reviewed_no_target_speech_preserves_raw_verse(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = "Jesus recognized their trick and said:"
            ledger = self.make_ledger(root, raw, ["said:"])
            path = ledger.path
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["rows"][0]["spans"] = []
            payload["rows"][0]["noTargetSpeech"] = True
            payload["rows"][0]["reason"] = "This target edition omits the KJV speech phrase."
            path.write_text(json.dumps(payload), encoding="utf-8")
            ledger = ReviewedJesusSpans.load(root, "de", "luther1912")
            self.assertEqual(ledger.apply("new_testament", "matthew", 4, 4, raw), raw)
            ledger.validate_coverage({("new_testament", "matthew", 4, 4)})

    def test_relocated_authority_colors_supplemental_target_only(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger = self.relocation_ledger(Path(directory))
            source = ledger.apply("new_testament", "matthew", 9, 30, "Jesus said:")
            target = ledger.apply("new_testament", "matthew", 9, 31, "Go! Then they left.")
            self.assertEqual(source, "Jesus said:")
            self.assertEqual(target, "[J]Go![/J] Then they left.")
            ledger.verify_relocations("new_testament", "matthew", [
                {"number": 9, "verses": [{"verse": 30, "text": source}, {"verse": 31, "text": target}]}
            ])
            ledger.validate_coverage({("new_testament", "matthew", 9, 30)})

    def test_relocation_rejects_uncolored_or_drifted_target(self):
        with tempfile.TemporaryDirectory() as directory:
            ledger = self.relocation_ledger(Path(directory))
            ledger.apply("new_testament", "matthew", 9, 30, "Jesus said:")
            ledger.apply("new_testament", "matthew", 9, 31, "Go! Then they left.")
            with self.assertRaisesRegex(JesusSpanError, "not wholly marked"):
                ledger.verify_relocations("new_testament", "matthew", [
                    {"number": 9, "verses": [{"verse": 31, "text": "Go! Then they left."}]}
                ])
            with self.assertRaisesRegex(JesusSpanError, "target drift"):
                ledger.verify_relocations("new_testament", "matthew", [
                    {"number": 9, "verses": [{"verse": 31, "text": "[J]Go![/J] Then they depart."}]}
                ])

    def test_relocation_rejects_orphan_and_duplicate_authority(self):
        for invalid in ("orphan", "duplicate"):
            with self.subTest(invalid=invalid), tempfile.TemporaryDirectory() as directory:
                ledger = self.relocation_ledger(Path(directory), orphan=invalid == "orphan", duplicate=invalid == "duplicate")
                ledger.apply("new_testament", "matthew", 9, 30, "Jesus said:")
                ledger.apply("new_testament", "matthew", 9, 31, "Go! Then they left.")
                with self.assertRaisesRegex(JesusSpanError, "Orphan|Duplicate"):
                    ledger.validate_coverage({("new_testament", "matthew", 9, 30)})

    def test_full_inheritance_override_removes_narrator_tail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = "Jesus said: Go! The crowd left."
            ledger = self.make_ledger(root, raw, ["Go!"])
            payload = json.loads(ledger.path.read_text(encoding="utf-8"))
            payload["rows"][0]["overrideInherited"] = True
            ledger.path.write_text(json.dumps(payload), encoding="utf-8")
            ledger = ReviewedJesusSpans.load(root, "de", "luther1912")
            tagged = ledger.apply("new_testament", "matthew", 4, 4, raw)
            self.assertEqual(tagged, "Jesus said: [J]Go![/J] The crowd left.")
            key = ("new_testament", "matthew", 4, 4)
            with self.assertRaisesRegex(JesusSpanError, "lacks KJV full authority"):
                ledger.validate_coverage(set(), set())
            ledger.validate_coverage(set(), {key})


if __name__ == "__main__":
    unittest.main()
