"""Focused parser tests for the independent note-address data audit."""

import unittest

from audit_note_references import citations, validate


class NoteReferenceTests(unittest.TestCase):
    def test_english_range(self):
        ref, = citations("en", "Read Revelation 19:11-21:5.")
        self.assertEqual((ref.book, ref.chapter, ref.verse, ref.end_chapter, ref.end_verse),
                         ("revelation", 19, 11, 21, 5))
        self.assertEqual([], validate("en", ref))

    def test_german_native_range(self):
        ref, = citations("de", "Jesaja 8,23-9,1")
        self.assertEqual((ref.chapter, ref.verse, ref.end_chapter, ref.end_verse), (8, 23, 9, 1))
        self.assertEqual([], validate("de", ref))

    def test_fullwidth_chinese_range(self):
        ref, = citations("zh-Hant", "（馬太福音２４：２９－３１）")
        self.assertEqual((ref.chapter, ref.verse, ref.end_verse), (24, 29, 31))

    def test_short_native_book_names_with_explicit_verses(self):
        for language, text, expected in (
                ("fr", "Luc 21:34-36", ("luke", 21, 34, 36)),
                ("ja", "ルカ21:34-36", ("luke", 21, 34, 36)),
                ("ja", "使徒14:22", ("acts", 14, 22, 22)),
                ("de", "Am 1,2", ("amos", 1, 2, 2))):
            with self.subTest(language=language, text=text):
                ref, = citations(language, text)
                self.assertEqual(expected, (ref.book, ref.chapter, ref.verse, ref.end_verse))

    def test_complete_chapter_range(self):
        ref, = citations("en", "Daniel 7-9")
        self.assertEqual((ref.chapter, ref.verse, ref.end_chapter, ref.end_verse), (7, None, 9, None))

    def test_numbered_next_book_does_not_extend_a_range(self):
        refs = citations("en", "Matthew 24:29-31, 1 Thessalonians 4:13-18")
        self.assertEqual(2, len(refs))
        self.assertEqual((24, 31), (refs[0].end_chapter, refs[0].end_verse))

    def test_number_in_chapter_heading_is_not_a_verse(self):
        ref, = citations("en", "Revelation 14: 144,000 and the Lamb")
        self.assertEqual((14, None), (ref.chapter, ref.verse))

    def test_localized_thousands_in_heading_are_not_verse_numbers(self):
        for language, text in (("de", "Offenbarung 14: 144.000"),
                               ("fr", "Apocalypse 14 : 144 000")):
            with self.subTest(language=language):
                ref, = citations(language, text)
                self.assertEqual((14, None), (ref.chapter, ref.verse))

    def test_prose_does_not_become_an_abbreviated_book_reference(self):
        for language, text in (("de", "am 10"), ("pt", "os 42"),
                               ("es", "de 360"), ("ko", "전 458"),
                               ("ar", "بعد 1,000"), ("hi", "यह 69"),
                               ("zh-Hans", "给出1,290"), ("hi", "दानिय्येल 360-दिन"),
                               ("ko", "약 2,000"), ("ko", "나 1,290"),
                               ("ko", "막 1,960"), ("hi", "यह 1,260")):
            with self.subTest(language=language, text=text):
                self.assertEqual([], citations(language, text))

    def test_invalid_endpoint_and_reversed_range(self):
        ref, = citations("en", "Revelation 20:99-4")
        self.assertEqual(["range ends before it starts", "verse 20:99 absent"], validate("en", ref))


if __name__ == "__main__":
    unittest.main()
