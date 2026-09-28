"""Read-only contracts for the multilingual prophecy notes and their routes.

Run: python -m unittest discover -s tools/prophecy -p 'test_*.py'
These checks do not replace theological review or an on-device rendering test.
"""

from __future__ import annotations

import json
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from audit_note_references import citations, validate


ROOT = Path(__file__).resolve().parents[2]
NOTES = ROOT / "shared/assets/notes"
LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")
PAGES = ("revelation_timeline.md", "messianic_prophecy.md", "daniels_timeline.md", "second_coming_rapture.md")
VALUES = {tag: "values-" + tag for tag in LANGUAGES}
VALUES.update({"en": "values", "zh-Hans": "values-zh-rCN", "zh-Hant": "values-zh-rTW"})
BASELINE = "a8049020"
HEADINGS = re.compile(r"^(#{1,6}) (.+)$", re.M)
URLS = re.compile(r"https://[^\s<>\])]+")
FORBIDDEN_ATTRIBUTION = re.compile(
    r"messiah\s*2030|messiah2030|wsimg\.com|youtube\.com|youtu\.be|"
    r"documentar(?:y|ies|io|ios)|documentaire|documental|documentário|Dokumentation|"
    r"документальн|纪录片|紀錄片|다큐멘터리|ドキュメンタリー|وثائقي|वृत्तचित्र",
    re.I,
)

# These inherited quotations used different wording from the bundled editions.
# Allow only these exact corrections; compare their replacement to the edition
# itself rather than exempting blockquotes from the preservation contract.
NATIVE_QUOTE_CORRECTIONS = {
    "pt": {
        '> "Pois o próprio Senhor descerá do céu com grande brado, à voz do arcanjo e ao som da trombeta de Deus; e os mortos em Cristo ressuscitarão primeiro. Depois nós, os que estivermos vivos e permanecermos, seremos arrebatados juntamente com eles nas nuvens, para o encontro com o Senhor nos ares. E assim estaremos para sempre com o Senhor."':
            ("1_thessalonians", 4, (16, 17)),
        '> "Nem todos dormiremos, mas todos seremos transformados, num instante, num abrir e fechar de olhos, ao som da última trombeta. Pois a trombeta soará, os mortos ressuscitarão incorruptíveis, e nós seremos transformados."':
            ("1_corinthians", 15, (51, 52)),
    },
    "ja": {
        '> 「主ご自身が、号令と御使いのかしらの声と神のラッパの響きとともに、天から下って来られます。そしてまず、キリストにある死者がよみがえります。それから、生き残っている私たちが、彼らと一緒に雲に包まれて引き上げられ、空中で主と会うのです。こうして私たちは、いつまでも主とともにいることになります。」':
            ("1_thessalonians", 4, (16, 17)),
        '> 「私たちはみな眠るわけではありませんが、みな変えられます。最後のラッパが鳴るとき、たちまち、一瞬のうちにです。ラッパが鳴ると、死者は朽ちないものによみがえり、私たちは変えられるのです。」':
            ("1_corinthians", 15, (51, 52)),
    },
    "zh-Hans": {
        '> “因为主必亲自从天降临，有号令的声音和天使长的声音，又有神的号吹响；那在基督里死了的人必先复活。以后我们这活着还存留的人，必和他们一同被提到云里，在空中与主相遇。这样，我们就要永远与主同在。”':
            ("1_thessalonians", 4, (16, 17)),
        '> “我们不是都要睡觉，而是都要改变，就在一刹那，眨眼之间，号筒末次吹响的时候。因号筒要响，死人要复活成为不朽坏的，我们也要改变。”':
            ("1_corinthians", 15, (51, 52)),
    },
    "zh-Hant": {
        '> 「因為主必親自從天降臨，有號令的聲音和天使長的聲音，又有神的號吹響；那在基督裡死了的人必先復活。以後我們這活著還存留的人，必和他們一同被提到雲裡，在空中與主相遇。這樣，我們就要永遠與主同在。」':
            ("1_thessalonians", 4, (16, 17)),
        '> 「我們不是都要睡覺，而是都要改變，就在一剎那，眨眼之間，號筒末次吹響的時候。因號筒要響，死人要復活成為不朽壞的，我們也要改變。」':
            ("1_corinthians", 15, (51, 52)),
    },
}


# Independently reviewed translation repairs against the unchanged English.
# Keep exact replacements so the remaining historical text still has a strict
# no-loss check; do not exempt entire paragraphs or sections.
STUDY_TRANSLATION_CORRECTIONS = {
    "it": {
        "Il verbo greco dietro «rapiti» è **harpazo**, che significa afferrare o portare via. Il termine teologico «rapimento» deriva in ultima analisi dalla traduzione latina di questo concetto.":
            "Il verbo greco dietro «rapiti» è **harpazo**, che significa afferrare o portare via. Il termine teologico inglese «rapture», corrispondente all'italiano «rapimento», deriva in ultima analisi dalla traduzione latina di questo concetto.",
    },
    "pt": {
        "O verbo grego traduzido como \"arrebatados\" é **harpazo**, que significa agarrar ou levar de repente. O termo teológico \"arrebatamento\" descreve esse conceito bíblico.":
            "O verbo grego traduzido como \"arrebatados\" é **harpazo**, que significa agarrar ou levar de repente. O termo teológico inglês \"rapture\", correspondente ao português \"arrebatamento\", deriva em última análise da tradução latina desse conceito.",
        "7. **Os crentes não estão destinados à ira de Deus.** Veja **1 Tessalonicenses 5:9**. A questão não resolvida é se isso significa retirada de todo o período da tribulação ou preservação da ira de Deus durante ele.":
            "7. **Os crentes não estão destinados à ira de Deus.** Veja **1 Tessalonicenses 5:9**. A questão não resolvida é se isso significa retirada de todo o período da tribulação ou proteção contra a ira de Deus durante esse período.",
    },
    "ar": {
        "يعطيها سفر الرؤيا اسم بابل العظيمة (**رؤيا 17:5**) ولا يعطيها اسما آخر. ويدعوها النص «المدينة العظيمة التي لها ملك على ملوك الأرض» (**رؤيا 17:18**)، والجالسة على «سبعة جبال» (**رؤيا 17:9**)، و«السكرى من دم القديسين» (**رؤيا 17:6**). ويعرضها النص كقوة فاسدة وغنية ومضطهدة واقعة تحت الدينونة الإلهية.":
            "يعطيها سفر الرؤيا اسم بابل العظيمة (**رؤيا 17:5**) ولا يعطيها اسما آخر. ويدعوها النص «المدينة العظيمة التي لها ملك على ملوك الأرض» (**رؤيا 17:18**)، والجالسة على «سبعة جبال» (**رؤيا 17:9**)، و«السكرى من دم القديسين» (**رؤيا 17:6**). ويعرضها النص كقوة فاسدة وغنية تمارس الاضطهاد وتقع تحت الدينونة الإلهية.",
        "بسبب هذه النصوص، تميز هذه الدراسة **يقين عودة المسيح وجمع شعبه وقيامته** من **نموذج التوقيت المختلف عليه الذي يوضع به ذلك الجمع ضمن تسلسل سفر الرؤيا**.":
            "بسبب هذه النصوص، تميز هذه الدراسة **يقين عودة المسيح وجمع شعبه وقيامتهم** من **نموذج التوقيت المختلف عليه الذي يوضع به ذلك الجمع ضمن تسلسل سفر الرؤيا**.",
    },
    "fr": {
        "Le verbe grec traduit par « enlevés » est **harpazo**, qui signifie saisir ou enlever. Le terme théologique français « enlèvement » traduit ce concept que le latin a rendu à l'origine du mot anglais correspondant.":
            "Le verbe grec traduit par « enlevés » est **harpazo**, qui signifie saisir ou enlever. Le terme théologique anglais « rapture », correspondant au français « enlèvement », vient en dernier ressort de la traduction latine de ce concept.",
    },
    "ru": {
        "За словами \"восхищены будем\" стоит греческий глагол **harpazo**, означающий схватить или унести. Богословский термин \"восхищение\" описывает это библейское событие.":
            "За словами \"восхищены будем\" стоит греческий глагол **harpazo**, означающий схватить или унести. Английский богословский термин \"rapture\" в конечном счете происходит через латинский перевод этого понятия.",
    },
    "ja": {
        "- 忠実また真実": "- 忠実で真実な方",
    },
    "de": {
        "Einige dieser Szenen greifen voraus und zeigen Ergebnisse, die spätere Kapitel ausführlicher beschreiben.":
            "Einige dieser Szenen scheinen vorauszugreifen und Ergebnisse zu zeigen, die spätere Kapitel ausführlicher beschreiben.",
        "Das griechische Verb hinter „entrückt werden“ lautet **harpazo** und bedeutet ergreifen oder hinwegreißen. Der deutsche theologische Begriff „Entrückung“ bezeichnet dieses biblische Geschehen.":
            "Das griechische Verb hinter „entrückt werden“ lautet **harpazo** und bedeutet ergreifen oder hinwegreißen. Der englische theologische Begriff „rapture“ (deutsch: „Entrückung“) geht letztlich auf die lateinische Übersetzung dieses Begriffs zurück.",
    },
}


def native_quote(tag: str, book_id: str, chapter: int, verses: tuple[int, ...]) -> str:
    path = ROOT / f"shared/assets/books/new_testament/{tag}/{book_id}.json"
    book = json.loads(path.read_text(encoding="utf-8-sig"))
    text = {}
    for story in book["stories"]:
        for bullet in story["summaryBullets"]:
            marker = re.search(r"\((\d+):(\d+)\)[.\s]*$", bullet)
            if marker and int(marker[1]) == chapter and int(marker[2]) in verses:
                verse = int(marker[2])
                if verse in text:
                    raise ValueError(f"Duplicate verse {tag}/{book_id} {chapter}:{verse}")
                text[verse] = re.sub(r"\[/?(?:J|DN|ADD)\]", "", bullet[:marker.start()]).strip()
    if tag in ("ja", "zh-Hant"):
        opening, closing, separator = "「", "」", ""
    elif tag == "zh-Hans":
        opening, closing, separator = "“", "”", ""
    else:
        opening, closing, separator = '"', '"', " "
    return "> " + opening + separator.join(text[verse] for verse in verses) + closing


def read(tag: str, page: str) -> str:
    return (NOTES / tag / page).read_text(encoding="utf-8-sig")


def normalized_lines(body: str) -> list[str]:
    """Ignore heading depth, not wording, numbers, quotes, or body order."""
    return [re.sub(r"^#{1,6} ", "", line).strip()
            for line in body.splitlines() if line.strip()]


def contains_in_order(original: list[str], updated: list[str]) -> bool:
    iterator = iter(updated)
    return all(any(candidate == line for candidate in iterator) for line in original)


class ProphecyAssetTests(unittest.TestCase):
    def test_all_four_pages_in_all_thirteen_languages(self):
        for tag in LANGUAGES:
            for page in PAGES:
                with self.subTest(language=tag, page=page):
                    body = read(tag, page)
                    self.assertTrue(body.startswith("# "))
                    self.assertNotIn("\ufffd", body)
                    # Lowercase "todo" is an ordinary Spanish/Portuguese word.
                    self.assertFalse(re.search(r"\b(?:TODO|TRANSLATE_ME|PLACEHOLDER)\b", body),
                                     "Untranslated placeholder marker")
                    self.assertGreater(len(body), 2500)

    def test_matching_section_structure(self):
        for page in PAGES:
            expected = [len(level) for level, _ in HEADINGS.findall(read("en", page))]
            for tag in LANGUAGES:
                with self.subTest(language=tag, page=page):
                    actual = [len(level) for level, _ in HEADINGS.findall(read(tag, page))]
                    self.assertEqual(expected, actual)

    def test_dropdown_headers_are_unique_nonempty_and_renderable(self):
        for tag in LANGUAGES:
            for page in PAGES:
                with self.subTest(language=tag, page=page):
                    body = read(tag, page)
                    headings = HEADINGS.findall(body)
                    self.assertTrue(all(len(level) <= 4 for level, _ in headings))
                    outer = [title.strip() for level, title in headings if level == "##"]
                    self.assertGreaterEqual(len(outer), 2)
                    self.assertEqual(len(outer), len(set(outer)))
                    for section in re.split(r"(?m)^## ", body)[1:]:
                        self.assertTrue(section.partition("\n")[2].strip())

    def test_reader_facing_studies_do_not_contain_documentary_attribution(self):
        for tag in LANGUAGES:
            for page in PAGES:
                with self.subTest(language=tag, page=page):
                    body = read(tag, page)
                    self.assertNotRegex(body, FORBIDDEN_ATTRIBUTION)
                    # These studies link directly to supporting Scriptures.
                    self.assertFalse(URLS.findall(body))

    def test_english_enrichment_has_substantive_scripture_study_units(self):
        # These are regression tripwires, not a judgment of theological accuracy.
        # Detailed contextual and theological review remains a separate check.
        required = {
            "messianic_prophecy.md": ("Isaiah 53", "Hebrews 4", "Leviticus 23", "Hosea 6"),
            "daniels_timeline.md": ("Daniel 9:24", "Ezra 7", "Nehemiah 2", "1,290", "1,335"),
            "second_coming_rapture.md": ("1 Thessalonians 4", "Isaiah 26", "Ezekiel 37", "Matthew 24"),
        }
        for page, anchors in required.items():
            with self.subTest(page=page):
                body = read("en", page)
                self.assertGreater(len(body.split()), 2400,
                                   "Detailed study content unexpectedly shortened")
                for anchor in anchors:
                    self.assertIn(anchor, body)
                self.assertGreaterEqual(len(re.findall(r"(?m)^### ", body)), 5)

    def test_explicit_scripture_addresses_exist_in_each_native_edition(self):
        for tag in LANGUAGES:
            for page in PAGES:
                with self.subTest(language=tag, page=page):
                    references = citations(tag, read(tag, page))
                    self.assertGreater(len(references), 20)
                    problems = [(reference.display, validate(tag, reference))
                                for reference in references if validate(tag, reference)]
                    self.assertEqual([], problems)

    def test_native_assets_are_not_english_fallback_copies(self):
        for tag in LANGUAGES[1:]:
            for page in PAGES:
                with self.subTest(language=tag, page=page):
                    body = read(tag, page)
                    english = read("en", page)
                    self.assertNotEqual(body, english)
                    self.assertNotEqual(body.splitlines()[0], english.splitlines()[0])
                    # All new teaching paragraphs must translate, not just titles.
                    long_english_lines = {line for line in english.splitlines()
                                          if len(line) > 130 and not line.startswith("http")}
                    self.assertFalse(long_english_lines.intersection(body.splitlines()))

    def test_original_futurist_text_survives_in_order(self):
        exists = subprocess.run(["git", "cat-file", "-e", BASELINE + "^{commit}"],
                                cwd=ROOT, capture_output=True).returncode == 0
        if not exists:
            self.skipTest("Preservation audit requires historical baseline a8049020, absent in shallow checkout")
        for tag in LANGUAGES:
            with self.subTest(language=tag):
                relative = f"shared/assets/notes/{tag}/revelation_timeline.md"
                result = subprocess.run(["git", "show", BASELINE + ":" + relative],
                                        cwd=ROOT, capture_output=True, check=True)
                original = result.stdout.decode("utf-8-sig")
                updated = read(tag, "revelation_timeline.md")
                for old_quote, address in NATIVE_QUOTE_CORRECTIONS.get(tag, {}).items():
                    self.assertEqual(1, original.count(old_quote))
                    replacement = native_quote(tag, *address)
                    self.assertIn(replacement, updated)
                    original = original.replace(old_quote, replacement, 1)
                for old_line, replacement in STUDY_TRANSLATION_CORRECTIONS.get(tag, {}).items():
                    self.assertEqual(1, original.count(old_line))
                    self.assertIn(replacement, updated)
                    original = original.replace(old_line, replacement, 1)
                self.assertTrue(contains_in_order(normalized_lines(original),
                                                 normalized_lines(updated)),
                                "An original line changed/disappeared or moved out of order")

    def test_localized_menu_strings(self):
        resources = ROOT / "shared/src/commonMain/composeResources"
        for tag, folder in VALUES.items():
            with self.subTest(language=tag):
                tree = ET.parse(resources / folder / "strings.xml")
                for name in ("prophecy_second_coming_rapture", "prophecy_second_coming_rapture_desc"):
                    found = tree.findall(f"./string[@name='{name}']")
                    self.assertEqual(1, len(found))
                    self.assertTrue(found[0].text and found[0].text.strip())

    def test_menu_route_asset_and_search_registry_agree(self):
        source = ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion"
        nav = (source / "Navigation.kt").read_text(encoding="utf-8")
        app = (source / "AppRoot.kt").read_text(encoding="utf-8")
        search = (source / "StorySearch.kt").read_text(encoding="utf-8")
        self.assertIn('Dest("second_coming_rapture")', nav)
        self.assertIn('composable(Dest.SecondComingRapture.route)', app)
        self.assertIn('"second_coming_rapture.md"', app)
        self.assertIn('"second_coming_rapture.md" to "second_coming_rapture"', search)


if __name__ == "__main__":
    unittest.main()
