"""Read-only chronology checks; native Kotlin tests still belong in CI."""

from collections import Counter, defaultdict
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET
import json


ROOT = Path(__file__).resolve().parents[2]
COMMON = ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion"
SOURCE = (COMMON / "BibleChronology.kt").read_text(encoding="utf-8-sig")
LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")


def balanced_call(text, start):
    depth = 0
    quoted = escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:index]
    raise ValueError("Unbalanced chronology call")


def entries_from_source():
    data = SOURCE.split("object BibleChronologyData {", 1)[1].split("private val canonicalIds", 1)[0]
    epochs = []
    for match in re.finditer(r"\bChronologyEpoch\(", data):
        body = balanced_call(data, match.end() - 1)
        epoch = re.search(r"ChronologyEpochId\.(\w+)", body)[1]
        entries = []
        for call in re.finditer(r"\b(ot|nt|dc)\(", body):
            args = balanced_call(body, call.end() - 1)
            head = re.match(r'\s*"([\w]+)"\s*,\s*"([\d, -]+)"(.*)', args, re.S)
            if not head:
                raise ValueError(f"Unsupported chronology literal in {epoch}")
            book, chapter_range, rest = head.groups()
            opening = re.match(r"\s*,\s*(\d+)\b", rest)
            lane = re.search(r"ChronologyLane\.(\w+)", rest)
            basis = re.search(r"ChronologyBasis\.(\w+)", rest)
            stage = re.search(r"GospelStage\.(\w+)", rest)
            attribution = re.search(r"PsalmAttribution\.(\w+)", rest)
            collection = {"ot": "old_testament", "nt": "new_testament", "dc": "deuterocanonical"}[call[1]]
            entries.append({
                "book": book, "range": chapter_range,
                "opening": int(opening[1]) if opening else 1,
                "collection": collection,
                "lane": lane[1] if lane else ("DEUTEROCANON" if call[1] == "dc" else "HISTORICAL_FLOW"),
                "basis": basis[1] if basis else None,
                "stage": stage[1] if stage else None,
                "attribution": attribution[1] if attribution else None,
            })
        epochs.append((epoch, entries))
    return epochs


EPOCHS = entries_from_source()


def chapters(chapter_range):
    result = []
    for segment in chapter_range.split(","):
        bounds = list(map(int, segment.strip().split("-")))
        result.extend(range(bounds[0], bounds[-1] + 1))
    return result


def native_chapters(book, values, language, attribution=None):
    if book == "joel" and language in ("de", "fr"):
        return [1, 2, 3, 4]
    if book == "song_of_three" and language in ("ar", "es", "fr", "it", "ja", "pt", "ru", "zh-Hans", "zh-Hant"):
        return [3]
    if book == "malachi" and language in ("de", "fr"):
        return sorted({3 if number == 4 else number for number in values})
    if book != "psalms" or language != "ru":
        return values
    result = []
    for number in values:
        if number == 10 and attribution == "UNNAMED":
            continue
        if number <= 9 or number >= 148:
            result.append(number)
        elif number == 10:
            result.append(9)
        elif 11 <= number <= 113 or 117 <= number <= 146:
            result.append(number - 1)
        elif number in (114, 115):
            result.append(113)
        elif number == 116:
            result.extend((114, 115))
        elif number == 147:
            result.extend((146, 147))
    return sorted(set(result))


class ChronologyContracts(unittest.TestCase):
    def test_all_epochs_and_books_remain_present(self):
        self.assertEqual(13, len(EPOCHS))
        actual = defaultdict(set)
        for _, entries in EPOCHS:
            for entry in entries:
                actual[entry["collection"]].add(entry["book"])
        for collection, count in (("old_testament", 39), ("new_testament", 27), ("deuterocanonical", 18)):
            index = json.loads((ROOT / f"shared/assets/books/{collection}/en/_index.json").read_text("utf-8-sig"))
            self.assertEqual(count, len(actual[collection]))
            self.assertEqual({book for book, _ in index if book}, actual[collection])

    def test_every_canonical_chapter_has_a_reading(self):
        covered = defaultdict(set)
        for _, entries in EPOCHS:
            for entry in entries:
                if entry["collection"] != "deuterocanonical":
                    covered[(entry["collection"], entry["book"])].update(chapters(entry["range"]))
        for (collection, book), present in covered.items():
            path = ROOT / f"shared/assets/books/{collection}/en/{book}.json"
            stories = json.loads(path.read_text("utf-8-sig"))["stories"]
            expected = {int(story["id"].rsplit("-", 1)[1]) for story in stories}
            self.assertEqual(expected, present, book)

    def test_split_narratives_keep_every_chapter_once(self):
        counts = Counter()
        for _, entries in EPOCHS:
            for entry in entries:
                for chapter in chapters(entry["range"]):
                    counts[(entry["book"], chapter)] += 1
        for book, count in {"genesis": 50, "1_chronicles": 29, "2_chronicles": 36,
                            "1_kings": 22, "2_kings": 25, "ezra": 10,
                            "isaiah": 66, "jeremiah": 52}.items():
            self.assertEqual({chapter: 1 for chapter in range(1, count + 1)},
                             {chapter: total for (name, chapter), total in counts.items() if name == book})

    def test_job_conquest_solomon_and_restoration_read_in_sequence(self):
        all_entries = [entry for _, entries in EPOCHS for entry in entries]
        self.assertEqual([("genesis", "1-11"), ("job", "1-42"), ("genesis", "12-50")],
                         [(entry["book"], entry["range"]) for entry in all_entries[:3]])
        self.assertEqual("TRADITIONAL_SETTING", all_entries[1]["basis"])
        epochs = dict(EPOCHS)
        self.assertEqual(["joshua", "judges", "ruth"], [e["book"] for e in epochs["CONQUEST_JUDGES"]])
        kingdom = epochs["UNITED_KINGDOM"]
        beginning = next(i for i, e in enumerate(kingdom) if (e["book"], e["range"]) == ("1_kings", "1-10"))
        ending = next(i for i, e in enumerate(kingdom) if (e["book"], e["range"]) == ("1_kings", "11"))
        for book in ("proverbs", "song_of_songs", "ecclesiastes"):
            self.assertTrue(beginning < next(i for i, e in enumerate(kingdom) if e["book"] == book) < ending)
        restoration = [entry for entry in epochs["RETURN_RESTORATION"] if entry["collection"] != "deuterocanonical"]
        self.assertEqual(["ezra", "haggai", "zechariah", "esther", "ezra", "nehemiah", "malachi"],
                         [entry["book"] for entry in restoration[:7]])
        self.assertEqual(["1-6", "7-10"], [e["range"] for e in restoration if e["book"] == "ezra"])

    def test_renderer_does_not_regroup_lanes_or_gospels(self):
        renderer = SOURCE.split("private fun ChronologyEpochRow(", 1)[1]
        self.assertIn("val readingEntries = epoch.readingEntries(includeDeuterocanon)", renderer)
        self.assertIn("readingEntries.forEachIndexed", renderer)
        self.assertNotIn("ChronologyLane.entries.forEach", renderer)
        self.assertNotIn("GospelStage.entries.forEach", renderer)
        self.assertIn("readingStep = readingStepStart + entryIndex", renderer)
        self.assertIn("entry.gospelStage != readingEntries.getOrNull(entryIndex - 1)?.gospelStage", renderer)
        self.assertIn("it.readingEntries(includeDeuterocanon).size", SOURCE)
        self.assertIn("readingStep.toString()", renderer)

    def test_every_localized_range_and_opening_has_an_actual_chapter(self):
        documents = {}
        for language in LANGUAGES:
            for _, entries in EPOCHS:
                for entry in entries:
                    key = (language, entry["collection"], entry["book"])
                    if key not in documents:
                        path = ROOT / f"shared/assets/books/{entry['collection']}/{language}/{entry['book']}.json"
                        documents[key] = json.loads(path.read_text("utf-8-sig"))
                    document = documents[key]
                    present = {int(s["id"].rsplit("-", 1)[1]) for s in document["stories"]
                               if s["id"].rsplit("-", 1)[1].isdigit()}
                    expected = native_chapters(entry["book"], chapters(entry["range"]), language, entry["attribution"])
                    opening = native_chapters(entry["book"], [entry["opening"]], language)[0]
                    with self.subTest(language=language, book=entry["book"], range=entry["range"]):
                        self.assertTrue(set(expected) <= present, f"Missing chapters: {set(expected) - present}")
                        self.assertIn(opening, present)
                        self.assertIn(opening, expected)

    def test_guidance_exists_in_all_languages_and_keeps_its_scripture_anchors(self):
        keys = ("chronology_intro", "chronology_job_note", "chronology_judges_note", "chronology_solomon_note")
        paths = sorted((ROOT / "shared/src/commonMain/composeResources").glob("values*/strings.xml"))
        self.assertEqual(13, len(paths))
        english_document = ET.parse(ROOT / "shared/src/commonMain/composeResources/values/strings.xml")
        english = {key: english_document.find(f"./string[@name='{key}']").text for key in keys}
        for path in paths:
            document = ET.parse(path)
            values = {}
            for key in keys:
                nodes = document.findall(f"./string[@name='{key}']")
                self.assertEqual(1, len(nodes), (path.parent.name, key))
                values[key] = nodes[0].text
                self.assertTrue(values[key] and values[key].strip())
                self.assertNotIn("\ufffd", values[key])
            self.assertIn("11", values["chronology_job_note"])
            self.assertIn("12", values["chronology_job_note"])
            self.assertIn("1:1", values["chronology_judges_note"])
            for reference in ("25:1", "30:1", "31:1"):
                self.assertIn(reference, values["chronology_solomon_note"])
            if path.parent.name != "values":
                self.assertNotEqual(english, values)

    def test_chapter_opening_still_keeps_language_edition_and_saved_epoch_state(self):
        renderer = SOURCE.split("private fun ChronologyBookRow(", 1)[1]
        self.assertIn("entry.openingChapterForLanguage(appLanguage)", renderer)
        self.assertIn("onExpandedEpochsChange(encoded)", SOURCE)
        self.assertIn("rememberSaveable", SOURCE)
        app = (COMMON / "AppRoot.kt").read_text("utf-8-sig")
        route = app.split("composable(Dest.BibleChronology.route)", 1)[1].split("composable(Dest.TorahFeastsAndGentiles.route)", 1)[0]
        self.assertIn("appLang = prefs.appLanguage", route)
        self.assertIn("internalBibleVersion = prefs.internalBibleVersion", route)
        self.assertIn("chronologyOpeningStoryId(it, openingChapter)", route)


if __name__ == "__main__":
    unittest.main()
