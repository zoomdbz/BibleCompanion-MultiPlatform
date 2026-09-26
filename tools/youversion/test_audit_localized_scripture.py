from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest


MODULE_PATH = Path(__file__).with_name("audit_localized_scripture.py")
SPEC = importlib.util.spec_from_file_location("localized_scripture_auditor", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
auditor = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = auditor
SPEC.loader.exec_module(auditor)


def page(body: str, *, code: str = "NVI", label: str | None = None, search: str = "Mateo%2017") -> str:
    label = label or auditor.EDITIONS["es"].gateway_label
    return f"""
    <html><head><title>Test {code} - Bible Gateway</title>
    <link rel="canonical" href="https://www.biblegateway.com/passage/?search={search}&amp;version={code}"></head>
    <body><div class="passage-text"><div class="passage-content">
    <div class="version-{code} result-text-style-normal text-html">{body}</div>
    </div></div>
    <div class="publisher-info-bottom"><strong><a href="/versions/{code}/">{label}</a></strong>
    <p>Copyright test</p></div></body></html>
    """


class SourceParserTests(unittest.TestCase):
    def test_headings_repeated_spans_semantics_and_bridge(self) -> None:
        html = page(
            """
            <h3><span class="text Matt-17-1">Heading <sup class="crossreference">A</sup></span></h3>
            <span class="text Matt-17-1"><span class="chapternum">17</span>One</span>
            <span class="text Matt-17-2"><sup class="versenum">2</sup><span class="woj">Two</span></span>
            <span class="text Matt-17-2">continued</span>
            <span class="text Matt-17-3"><sup class="versenum">3-4</sup>Three and four</span>
            """
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), auditor.EDITIONS["es"], "matthew", 17, frozenset({2, 3})
        )
        assert parsed is not None
        structure, metadata = parsed
        self.assertEqual([(1, 1), (2, 2), (3, 4)], [(item.start, item.end) for item in structure.markers])
        self.assertTrue(structure.markers[1].has_jesus_words)
        self.assertEqual([1], [item.before_verse for item in structure.headings])
        spots = {item.verse: item for item in structure.spot_hashes}
        self.assertEqual(auditor.sha256_text("Two continued"), spots[2].source_sha256)
        self.assertTrue(spots[3].bridged)
        self.assertEqual(auditor.EDITIONS["es"].gateway_label, metadata.version_label)

    def test_traditional_empty_marker_is_recorded(self) -> None:
        html = page(
            """
            <span class="text John-5-1"><span class="chapternum">5</span>One</span>
            <span class="text John-5-2"><sup class="versenum">2</sup>Two</span>
            <span class="text John-5-3"><sup class="versenum">3</sup>Three</span>
            <span class="text John-5-4"><sup class="versenum">4</sup><sup class="footnote">a</sup></span>
            <span class="text John-5-5"><sup class="versenum">5</sup>Five</span>
            """
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), auditor.EDITIONS["es"], "john", 5, frozenset()
        )
        assert parsed is not None
        self.assertTrue(parsed[0].markers[3].empty)

    def test_unlabelled_bridge_fragments_use_exact_range_class(self) -> None:
        html = page(
            """
            <span class="text Ps-1-1-Ps-1-2"><span class="chapternum">1</span>First fragment</span>
            <span class="text Ps-1-1-Ps-1-2">continuation</span>
            <span class="text Ps-1-1-Ps-1-2"><sup class="versenum">1-2</sup>labelled fragment</span>
            <span class="text Ps-1-3"><sup class="versenum">3</sup>Third</span>
            """,
            code=auditor.EDITIONS["zh-Hans"].gateway_code,
            label=auditor.EDITIONS["zh-Hans"].gateway_label,
            search="Psalm%201",
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), auditor.EDITIONS["zh-Hans"], "psalms", 1, frozenset()
        )
        assert parsed is not None
        self.assertEqual([(1, 2), (3, 3)], [(item.start, item.end) for item in parsed[0].markers])

    def test_documented_ccb_isaiah_38_editorial_order_is_repaired(self) -> None:
        edition = auditor.EDITIONS["zh-Hans"]
        html = page(
            """
            <span class="text Isa-38-1"><span class="chapternum">38</span>One</span>
            <span class="text Isa-38-2"><sup class="versenum">2</sup>Two</span>
            <span class="text Isa-38-21"><sup class="versenum">21</sup>Twenty-one</span>
            <span class="text Isa-38-22"><sup class="versenum">22</sup>Twenty-two</span>
            <span class="text Isa-38-3-Isa-38-20"><sup class="versenum">3-20</sup>Three through twenty</span>
            """,
            code=edition.gateway_code,
            label=edition.gateway_label,
            search="Isaiah%2038",
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), edition, "isaiah", 38, frozenset()
        )
        assert parsed is not None
        self.assertEqual(
            [(1, 1), (2, 2), (3, 20), (21, 21), (22, 22)],
            [(item.start, item.end) for item in parsed[0].markers],
        )

    def test_documented_nvi_habakkuk_title_is_numbered_body(self) -> None:
        edition = auditor.EDITIONS["es"]
        html = page(
            """
            <h4 class="psalm-title"><span class="text Hab-3-1"><span class="chapternum">3</span>Prayer</span></h4>
            <span class="text Hab-3-2"><sup class="versenum">2</sup>Two</span>
            """,
            code=edition.gateway_code,
            label=edition.gateway_label,
            search="Habakkuk%203",
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), edition, "habakkuk", 3, frozenset()
        )
        assert parsed is not None
        self.assertEqual([(1, 1), (2, 2)], [(item.start, item.end) for item in parsed[0].markers])
        self.assertEqual((), parsed[0].headings)

    def test_documented_sch2000_source_gap_is_retained_as_unavailable(self) -> None:
        edition = auditor.EDITIONS["de"]
        html = page(
            '<span class="text Hos-14-2"><sup class="versenum">2</sup>Two</span>',
            code=edition.gateway_code,
            label=edition.gateway_label,
            search="Hosea%2014",
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), edition, "hosea", 14, frozenset()
        )
        assert parsed is not None
        self.assertEqual([(1, 1), (2, 2)], [(item.start, item.end) for item in parsed[0].markers])
        self.assertIsNone(parsed[0].markers[0].text_sha256)

    def test_nontraditional_empty_marker_is_rejected(self) -> None:
        html = page(
            """
            <span class="text Matt-17-1"><span class="chapternum">17</span>One</span>
            <span class="text Matt-17-2"><sup class="versenum">2</sup><sup class="footnote">a</sup></span>
            """
        )
        with self.assertRaises(auditor.SourceError):
            auditor.parse_chapter_page(
                html, auditor.sha256_text(html), auditor.EDITIONS["es"], "matthew", 17, frozenset()
            )

    def test_numbered_psalm_superscription_becomes_verse_one(self) -> None:
        edition = auditor.EDITIONS["ru"]
        html = page(
            """
            <h4 class="psalm-title"><span class="text Ps-9-2"><span class="chapternum">9</span>Title</span></h4>
            <span class="text Ps-9-2"><sup class="versenum">2</sup>Body</span>
            <span class="text Ps-9-3"><sup class="versenum">3</sup>More</span>
            """,
            code=edition.gateway_code,
            label=edition.gateway_label,
            search="Psalm%209",
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), edition, "psalms", 9, frozenset()
        )
        assert parsed is not None
        self.assertEqual([(1, 1), (2, 2), (3, 3)], [(item.start, item.end) for item in parsed[0].markers])
        self.assertEqual((), parsed[0].headings)

    def test_two_verse_psalm_superscription_becomes_verse_range(self) -> None:
        edition = auditor.EDITIONS["ru"]
        html = page(
            """
            <h4 class="psalm-title">
              <span class="text Ps-50-1"><span class="chapternum">50</span>Title one</span>
              <span class="text Ps-50-2"><sup class="versenum">2</sup>Title two</span>
            </h4>
            <span class="text Ps-50-3"><sup class="versenum">3</sup>Body</span>
            """,
            code=edition.gateway_code,
            label=edition.gateway_label,
            search="Psalm%2050",
        )
        parsed = auditor.parse_chapter_page(
            html, auditor.sha256_text(html), edition, "psalms", 50, frozenset()
        )
        assert parsed is not None
        self.assertEqual([(1, 1), (2, 2), (3, 3)], [(item.start, item.end) for item in parsed[0].markers])
        self.assertEqual((), parsed[0].headings)

    def test_wrong_version_label_is_rejected(self) -> None:
        html = page(
            '<span class="text Matt-17-1"><span class="chapternum">17</span>One</span>',
            label="Wrong Version (NVI)",
        )
        with self.assertRaises(auditor.SourceError):
            auditor.parse_chapter_page(
                html, auditor.sha256_text(html), auditor.EDITIONS["es"], "matthew", 17, frozenset()
            )


class LocalAndComparisonTests(unittest.TestCase):
    def test_local_parser_validates_tags_and_does_not_mutate(self) -> None:
        story = {
            "summaryBullets": ["[J]Words[/J] and [ADD]addition[/ADD] (1:1)."],
            "headings": [{"beforeVerse": 1, "text": "Heading"}],
            "keep": {"value": 7},
        }
        original = json.loads(json.dumps(story))
        markers, headings = auditor.parse_local_story(story, "es", "matthew", 1)
        self.assertEqual(original, story)
        self.assertEqual("Words and addition", markers[0].plain)
        self.assertTrue(markers[0].has_jesus_words)
        self.assertTrue(markers[0].has_additions)
        self.assertEqual((1,), headings)

    def test_local_parser_accepts_omitted_optional_headings(self) -> None:
        story = {"summaryBullets": ["Verse text (1:1)"]}
        original = json.loads(json.dumps(story))
        markers, headings = auditor.parse_local_story(story, "it", "malachi", 1)
        self.assertEqual(original, story)
        self.assertEqual([(1, 1)], [(item.start, item.end) for item in markers])
        self.assertEqual((), headings)

    def test_local_chapters_uses_declared_book_id_alias(self) -> None:
        payload = {
            "id": "1-samuel",
            "stories": [
                {"id": "1-samuel-1", "summaryBullets": ["Verse (1:1)."]},
                {"id": "1-samuel-2", "summaryBullets": ["Verse (2:1)."]},
            ],
        }
        chapters = auditor.local_chapters(payload, "de", "1_samuel")
        self.assertEqual([1, 2], sorted(chapters))

    def test_unexpected_extra_and_traditional_extra_are_distinct(self) -> None:
        local = (
            auditor.LocalMarker(20, 20, "Twenty", False, False),
            auditor.LocalMarker(21, 21, "Traditional", True, False),
            auditor.LocalMarker(22, 22, "Twenty-two", False, False),
            auditor.LocalMarker(23, 23, "Unexpected", False, False),
        )
        source = auditor.ChapterStructure(
            "matthew", 17,
            (
                auditor.Marker(20, 20, False, False, False),
                auditor.Marker(21, 21, True, False, False),
                auditor.Marker(22, 22, False, False, False),
            ),
            (), (), "https://example.test", "A" * 64,
        )
        findings = auditor.compare_chapter(
            "es", "matthew", 17, local, (), source, frozenset(), False, False
        )
        by_type = {item["type"]: item for item in findings}
        self.assertEqual([21], by_type["traditional-local-verses-need-fallback-proof"]["verses"])
        self.assertEqual([23], by_type["unexpected-local-verses"]["verses"])

    def test_bridged_spot_check_does_not_claim_exact_match(self) -> None:
        local = (auditor.LocalMarker(3, 4, "Grouped", False, False),)
        source = auditor.ChapterStructure(
            "matthew", 1, (auditor.Marker(3, 4, False, False, False),), (),
            (auditor.SpotHash(3, None, None, True),), "https://example.test", "A" * 64,
        )
        findings = auditor.compare_chapter(
            "es", "matthew", 1, local, (), source, frozenset({3}), False, False
        )
        self.assertIn("spot-check-bridged-line", {item["type"] for item in findings})


class CacheAndConfigurationTests(unittest.TestCase):
    def test_sanitized_cache_contains_no_source_text(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "record.json"
            structure = auditor.ChapterStructure(
                "matthew", 1, (auditor.Marker(1, 1, False, True, False, "B" * 64, 12),),
                (auditor.HeadingMarker(1, "h3", "A" * 64, 7),),
                (auditor.SpotHash(1, "B" * 64, 12, False),),
                "https://example.test", "C" * 64,
            )
            metadata = auditor.PageMetadata("Label", "Copyright", "https://example.test/v", "https://example.test")
            auditor.store_cached_structure(path, auditor.EDITIONS["es"], structure, metadata)
            raw = path.read_text(encoding="utf-8")
            self.assertNotIn("Secret verse words", raw)
            self.assertNotIn('"html"', raw)
            loaded = auditor.load_cached_structure(
                path, auditor.EDITIONS["es"], "matthew", 1, frozenset({1})
            )
            self.assertIsNotNone(loaded)
            assert loaded is not None
            self.assertEqual("B" * 64, loaded[0].markers[0].text_sha256)

    def test_catalog_requires_exact_language_and_label(self) -> None:
        html = """
        <select name="version">
          <option class="lang" value="NVI">---Español (ES)---</option>
          <option value="NVI">Nueva Versión Internacional (NVI)</option>
        </select>
        """
        catalog = auditor.parse_version_catalog(html)
        auditor.validate_catalog_entry(catalog, auditor.EDITIONS["es"])
        with self.assertRaises(auditor.SourceError):
            auditor.validate_catalog_entry(catalog, auditor.EDITIONS["de"])

    def test_spot_check_limit_and_format(self) -> None:
        parsed = auditor.parse_spot_checks("de:EPH.6.10,es:MAT.17.20")
        self.assertEqual(frozenset({10}), parsed["de"][("ephesians", 6)])
        with self.assertRaises(auditor.ConfigurationError):
            auditor.parse_spot_checks("de:Ephesians.6.10")


if __name__ == "__main__":
    unittest.main()
