import unittest

from repair_hindi_source_markup import (
    INITIAL_REVIEWED_PRESENTATION_SPEECH_MISMATCHES,
    REVIEWED_LEXICAL_REPAIRS,
    psalm119_headings,
    repair_acrostic,
    repair_reviewed_lexical_text,
    transplant_reviewed_presentation_jesus_tags,
    transplant_jesus_tags,
)


class SourceSpeechTransferTests(unittest.TestCase):
    def test_narrator_between_speech_spans_keeps_original_spaces(self):
        original = "[J]First.  Narrator.\tSecond.[/J]"
        source = "[J]First.[/J] Narrator. [J]Second.[/J]"
        result = transplant_jesus_tags(original, source)
        self.assertEqual(result, "[J]First.  [/J]Narrator.\t[J]Second.[/J]")
        self.assertEqual(result.replace("[J]", "").replace("[/J]", ""), "First.  Narrator.\tSecond.")

    def test_source_without_speech_removes_wrong_tags(self):
        self.assertEqual(transplant_jesus_tags("[J]Narration.[/J]", "Narration."), "Narration.")

    def test_mismatching_wording_cannot_transfer_speech(self):
        with self.assertRaisesRegex(ValueError, "identity"):
            transplant_jesus_tags("Different words.", "[J]Source words.[/J]")

    def test_other_display_tags_require_review(self):
        with self.assertRaisesRegex(ValueError, "Other display tags"):
            transplant_jesus_tags("[DN]Name[/DN]", "[J]Name[/J]")


class ReviewedPresentationSpeechTransferTests(unittest.TestCase):
    def test_initial_mismatch_count_is_locked_to_review_record(self):
        self.assertEqual(229, INITIAL_REVIEWED_PRESENTATION_SPEECH_MISMATCHES)

    @staticmethod
    def row(app, source, app_spans=(), source_spans=()):
        return {
            "appText": app,
            "sourceText": source,
            "approvedAppApparatus": list(app_spans),
            "approvedSourceApparatus": list(source_spans),
        }

    def test_app_only_apparatus_is_forced_outside_jesus_speech(self):
        app = "A (Ref) B"
        span = {"start": 2, "end": 7, "text": "(Ref)", "sourceUsfmMarker": "bdit"}
        result = transplant_reviewed_presentation_jesus_tags(
            "[J]A (Ref) B[/J]", "[J]A B[/J]", self.row(app, "A B", [span])
        )
        self.assertEqual(result, "[J]A [/J](Ref) [J]B[/J]")
        self.assertEqual(result.replace("[J]", "").replace("[/J]", ""), app)

    def test_source_only_apparatus_does_not_shift_speech_flags(self):
        source = "A (note) B"
        span = {"start": 2, "end": 8, "text": "(note)", "sourceUsfmMarker": "it"}
        result = transplant_reviewed_presentation_jesus_tags(
            "A  B", "[J]A[/J] (note) [J]B[/J]", self.row("A B", source, source_spans=[span])
        )
        self.assertEqual(result, "[J]A  B[/J]")

    def test_whitespace_gap_preserves_original_bytes(self):
        result = transplant_reviewed_presentation_jesus_tags(
            "[J]A  B[/J]", "[J]A[/J] B", self.row("A B", "A B")
        )
        self.assertEqual(result, "[J]A  [/J]B")
        self.assertEqual(result.replace("[J]", "").replace("[/J]", ""), "A  B")

    def test_apparatus_offset_drift_fails_closed(self):
        bad_span = {"start": 2, "end": 7, "text": "wrong", "sourceUsfmMarker": "bdit"}
        with self.assertRaisesRegex(ValueError, "apparatus text drift"):
            transplant_reviewed_presentation_jesus_tags(
                "A (Ref) B", "A B", self.row("A (Ref) B", "A B", [bad_span])
            )

    def test_other_app_display_tag_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Other display tags"):
            transplant_reviewed_presentation_jesus_tags(
                "[DN]A[/DN] B", "[J]A B[/J]", self.row("A B", "A B")
            )


class AcrosticHeadingTests(unittest.TestCase):
    def test_source_label_belongs_to_following_verse(self):
        raw = b"\\c 119\n\\s1 Stanza one\n\\d Aleph\n\\v 1 Words.\n\\v 8 Prior words.\n\\s1 Stanza two\n\\d Beth\n\\v 9 New words.\n"
        self.assertEqual(psalm119_headings(raw), {1: ("Stanza one", "Aleph"), 9: ("Stanza two", "Beth")})

    def test_label_moves_without_removing_any_other_word_and_is_idempotent(self):
        story = {"headings": [{"beforeVerse": 9, "text": "Stanza"}], "summaryBullets": ["Verse words. Beth (119:8)."]}
        source = {(119, 8, 8): ("Verse words.", "Verse words.")}
        headings = {9: ("Stanza", "Beth")}
        self.assertEqual(repair_acrostic(story, source, headings), (1, 1))
        self.assertEqual(story["summaryBullets"], ["Verse words. (119:8)."])
        self.assertEqual(story["headings"][0]["text"], "Stanza\nBeth")
        self.assertEqual(repair_acrostic(story, source, headings), (0, 0))

    def test_unrelated_text_change_blocks_acrostic_repair(self):
        story = {"headings": [{"beforeVerse": 9, "text": "Stanza"}], "summaryBullets": ["Different words. Beth (119:8)."]}
        with self.assertRaisesRegex(ValueError, "Other wording"):
            repair_acrostic(story, {(119, 8, 8): ("Source words.", "Source words.")}, {9: ("Stanza", "Beth")})


class ReviewedLexicalRepairTests(unittest.TestCase):
    key = (12, 18, 18)
    repair = REVIEWED_LEXICAL_REPAIRS[("REV", *key)]

    def test_exact_old_text_changes_to_exact_pinned_source_and_preserves_suffix(self):
        suffix = " (12:18)."
        repaired, changed = repair_reviewed_lexical_text(
            "REV", self.key, self.repair["old"], self.repair["official"]
        )
        self.assertTrue(changed)
        self.assertEqual(self.repair["official"] + suffix, repaired + suffix)

    def test_already_fixed_text_is_idempotent(self):
        repaired, changed = repair_reviewed_lexical_text(
            "REV", self.key, self.repair["official"], self.repair["official"]
        )
        self.assertFalse(changed)
        self.assertEqual(self.repair["official"], repaired)

    def test_pinned_source_drift_blocks_repair(self):
        with self.assertRaisesRegex(ValueError, "source drift"):
            repair_reviewed_lexical_text("REV", self.key, self.repair["old"], "Different source.")

    def test_current_word_or_tag_drift_blocks_repair(self):
        with self.assertRaisesRegex(ValueError, "current text drift"):
            repair_reviewed_lexical_text(
                "REV", self.key, "[J]" + self.repair["old"] + "[/J]", self.repair["official"]
            )

    def test_unreviewed_reference_never_changes(self):
        self.assertEqual(
            ("Untouched.", False),
            repair_reviewed_lexical_text("REV", (12, 17, 17), "Untouched.", "Other."),
        )


if __name__ == "__main__":
    unittest.main()
