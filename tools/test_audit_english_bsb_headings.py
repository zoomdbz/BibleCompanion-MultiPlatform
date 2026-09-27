import tempfile
import unittest
from pathlib import Path

from audit_english_bsb_headings import source_headings


class EnglishBsbHeadingAuditTest(unittest.TestCase):
    def test_stacked_acrostic_and_inline_speaker(self):
        source = """\\c 1
\\ms BOOK I
\\s1 First heading
\\p \\v 1 First verse.
\\qa א
\\qa ALEPH
\\p \\v 2 Second verse.
\\s2 The Friends
\\q1 Continuation of second verse.
\\s2 The Bride
\\q1 Another continuation.
\\p \\v 3 Third verse.
"""
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "example.usfm"
            path.write_text(source, encoding="utf-8")
            anchored, inline = source_headings(path)
        self.assertEqual(anchored, [
            (1, 1, "BOOK I"), (1, 1, "First heading"), (1, 2, "א ALEPH"),
        ])
        self.assertEqual(inline, [(1, 2, "The Friends"), (1, 2, "The Bride")])

    def test_unpaired_acrostic_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "example.usfm"
            path.write_text("\\c 1\n\\qa א\n\\v 1 Text.\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "unpaired acrostic"):
                source_headings(path)


if __name__ == "__main__":
    unittest.main()
