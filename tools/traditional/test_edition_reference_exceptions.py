#!/usr/bin/env python3
"""Validate the non-Psalm modern-to-traditional reference exceptions.

The default checks use the checked-in base and overlay texts. Optional source
arguments verify the pinned mapping tables and eBible archives recorded in the
provenance note.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = Path(__file__).with_name("edition_reference_exceptions.json")
BOOKS = REPO_ROOT / "shared/assets/books"
BASE_PATHS = {
    ("de", "isaiah"): BOOKS / "old_testament/de/isaiah.json",
    ("it", "job"): BOOKS / "old_testament/it/job.json",
    ("ru", "romans"): BOOKS / "new_testament/ru/romans.json",
    ("zh-Hant", "john"): BOOKS / "new_testament/zh-Hant/john.json",
}
TARGET_PATHS = {
    ("de", "isaiah"): BOOKS / "editions/de/luther1912/old_testament/isaiah.json",
    ("it", "job"): BOOKS / "editions/it/diodati1885/old_testament/job.json",
    ("ru", "romans"): BOOKS / "editions/ru/synodal1876/new_testament/romans.json",
    ("zh-Hant", "john"): BOOKS / "editions/zh-Hant/cuv/new_testament/john.json",
}
EXPECTED_KEYS = {
    ("de", "sch2000", "luther1912", "isaiah"),
    ("it", "nr06", "diodati1885", "job"),
    ("ru", "nrt_nrp", "synodal1876", "romans"),
    ("zh-Hant", "rcuv", "cuv", "john"),
}
EXPECTED_ROWS = {
    ("de", "isaiah"): [
        (8, 23, 23, 9, 1, 1),
        (9, 1, 20, 9, 2, 21),
        (64, 1, 1, 64, 1, 2),
        (64, 2, 11, 64, 3, 12),
    ],
    ("it", "job"): [
        (38, 39, 41, 39, 1, 3),
        (39, 1, 30, 39, 4, 33),
        (40, 1, 5, 39, 34, 38),
        (40, 6, 24, 40, 1, 19),
        (40, 25, 32, 41, 1, 8),
        (41, 1, 1, 41, 9, 9),
        (41, 2, 26, 41, 10, 34),
    ],
    ("ru", "romans"): [
        *[(chapter, 1, last, chapter, 1, last) for chapter, last in {
            1: 32, 2: 29, 3: 31, 4: 25, 5: 21, 6: 23, 7: 25, 8: 39,
            9: 33, 10: 21, 11: 36, 12: 21, 13: 14, 14: 23, 15: 33,
            16: 24,
        }.items()],
        (16, 25, 27, 14, 24, 26),
    ],
    ("zh-Hant", "john"): [
        (7, 53, 53, 8, 1, 1),
        (8, 1, 1, 8, 1, 1),
    ],
}
ARCHIVES = {
    "de": (
        "650a8192134a8f0057286c469754edcfaee4fbb18800621aee4563f3055bb39b",
        "24-ISAdeu1912.usfm",
        "daca8923266d6b8c4a62576e4396fdde1ced00fb10fa8045d96f26ff6d88f6c1",
    ),
    "it": (
        "459884735df5d5ae7f3381980bc5ea846f0034cea305fa77b5d72e09727e051c",
        "19-JOBita1885.usfm",
        "3b0a9d08a8758c9bc69b720ee22eca8ae4a790468acf7cb5ce8d44ff5648886d",
    ),
    "ru": (
        "acd5d80c0d28ca72d17cb12a2bb9f561d957439b03bddfaa433beb51cf7b0363",
        "75-ROMrussyn.usfm",
        "fea367b62c049a0ef784f1e708d3f2e3c8463c09f34b52bc21c7d24a14855872",
    ),
    "zh-Hant": (
        "01e919ec0f2ea9e22adaa5097340fc4ad18a7976a32f850b2e6434d7884f3e81",
        "73-JHNcmn-cu89t.usfm",
        "d692d0509087472f18a46d4e3ce7d1000eb85aa2ca1978edf947cffecba8057b",
    ),
}
BASE_REF_RE = re.compile(r"\((\d+):(\d+)\)\.?$")


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def base_verses(path: Path) -> tuple[set[tuple[int, int]], dict[tuple[int, int], str]]:
    data = load_json(path)
    verses: dict[tuple[int, int], str] = {}
    for story in data["stories"]:
        for bullet in story["summaryBullets"]:
            match = BASE_REF_RE.search(bullet)
            if not match:
                raise AssertionError(f"missing terminal verse coordinate in {path}: {bullet!r}")
            coordinate = tuple(map(int, match.groups()))
            if coordinate in verses:
                raise AssertionError(f"duplicate base coordinate in {path}: {coordinate}")
            verses[coordinate] = bullet
    return set(verses), verses


def target_verses(path: Path) -> tuple[set[tuple[int, int]], dict[tuple[int, int], str]]:
    data = load_json(path)
    verses = {
        (verse["chapter"], verse["verse"]): verse["text"]
        for chapter in data["chapters"]
        for verse in chapter["verses"]
    }
    return set(verses), verses


def compact_row(row: dict) -> tuple[int, int, int, int, int, int]:
    return (
        row["sourceChapter"],
        row["sourceVerse"],
        row.get("sourceVerseEnd", row["sourceVerse"]),
        row["targetChapter"],
        row["targetVerse"],
        row.get("targetVerseEnd", row["targetVerse"]),
    )


def expand(rows: list[dict]) -> dict[tuple[int, int], set[tuple[int, int]]]:
    result: dict[tuple[int, int], set[tuple[int, int]]] = {}
    for raw in rows:
        sc, sv, se, tc, tv, te = compact_row(raw)
        source = list(range(sv, se + 1))
        target = list(range(tv, te + 1))
        if len(source) > 1 and len(target) > 1 and len(source) != len(target):
            raise AssertionError(f"ambiguous unequal multi-range: {raw}")
        for index, source_verse in enumerate(source):
            key = (sc, source_verse)
            if key in result:
                raise AssertionError(f"duplicate source mapping: {key}")
            if len(source) == len(target):
                mapped = {(tc, target[index])}
            elif len(target) == 1:
                mapped = {(tc, target[0])}
            elif len(source) == 1:
                mapped = {(tc, verse) for verse in target}
            else:
                raise AssertionError(f"unsupported range shape: {raw}")
            result[key] = mapped
    return result


def sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


class ExceptionMapTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.document = load_json(MAP_PATH)
        cls.maps = cls.document["maps"]

    def test_schema_and_exact_exception_rows(self) -> None:
        self.assertEqual(1, self.document["schemaVersion"])
        actual_keys = set()
        for edition_map in self.maps:
            self.assertEqual(1, edition_map["schemaVersion"])
            self.assertEqual(1, len(edition_map["books"]))
            book = edition_map["books"][0]
            key = (
                edition_map["language"], edition_map["baseEditionId"],
                edition_map["editionId"], book["bookId"],
            )
            actual_keys.add(key)
            self.assertEqual(
                EXPECTED_ROWS[(edition_map["language"], book["bookId"])],
                [compact_row(row) for row in book["mappings"]],
            )
        self.assertEqual(EXPECTED_KEYS, actual_keys)

    def test_explicit_coordinates_exist_and_complete_maps_cover_every_verse(self) -> None:
        for edition_map in self.maps:
            language = edition_map["language"]
            book = edition_map["books"][0]
            book_id = book["bookId"]
            source, _ = base_verses(BASE_PATHS[(language, book_id)])
            target, _ = target_verses(TARGET_PATHS[(language, book_id)])
            explicit = expand(book["mappings"])
            self.assertLessEqual(explicit.keys(), source)
            if book["complete"]:
                self.assertEqual(source, explicit.keys())
            reached = set().union(*explicit.values())
            self.assertLessEqual(reached, target)
            unresolved = {
                tuple(map(int, coordinate.rsplit(" ", 1)[1].split(":")))
                for coordinate in edition_map["provenance"].get(
                    "unresolvedTargetOnlyCoordinates", []
                )
            }
            self.assertLessEqual(unresolved, target)
            self.assertFalse(unresolved & reached)
            if book["complete"]:
                self.assertEqual(unresolved, target - reached,
                                 f"complete target coverage drift for {language}/{book_id}")

    def test_boundaries_follow_actual_text_not_only_counts(self) -> None:
        _, de_source = base_verses(BASE_PATHS[("de", "isaiah")])
        _, de_target = target_verses(TARGET_PATHS[("de", "isaiah")])
        self.assertIn("Sebulon", de_source[(8, 23)])
        self.assertIn("Sebulon", de_target[(9, 1)])
        self.assertIn("Naphtali", de_source[(8, 23)])
        self.assertIn("Naphthali", de_target[(9, 1)])
        self.assertIn("Himmel", de_source[(64, 1)])
        self.assertIn("Himmel", de_target[(64, 1)])
        self.assertIn("Name", de_source[(64, 1)])
        self.assertIn("Name", de_target[(64, 2)])

        _, it_source = base_verses(BASE_PATHS[("it", "job")])
        _, it_target = target_verses(TARGET_PATHS[("it", "job")])
        self.assertIn("leone", it_source[(38, 39)].lower())
        self.assertIn("leone", it_target[(39, 1)].lower())
        self.assertIn("corvo", it_source[(38, 41)].lower())
        self.assertIn("corvo", it_target[(39, 3)].lower())
        # NR06 calls the creature "coccodrillo" here; Diodati calls it
        # "leviatan". The shared hook-and-tongue wording proves the mapped
        # passage without forcing the two translations to use one noun.
        self.assertIn("coccodrillo", it_source[(40, 25)].lower())
        self.assertIn("leviatan", it_target[(41, 1)].lower())
        self.assertIn("amo", it_source[(40, 25)].lower())
        self.assertIn("amo", it_target[(41, 1)].lower())

        _, ru_source = base_verses(BASE_PATHS[("ru", "romans")])
        _, ru_target = target_verses(TARGET_PATHS[("ru", "romans")])
        self.assertIn("\u0442\u0430\u0439\u043d", ru_source[(16, 25)].lower())
        self.assertIn("\u0442\u0430\u0439\u043d", ru_target[(14, 24)].lower())

        _, zh_source = base_verses(BASE_PATHS[("zh-Hant", "john")])
        _, zh_target = target_verses(TARGET_PATHS[("zh-Hant", "john")])
        home = "\u5404\u4eba\u90fd\u56de\u5bb6\u53bb\u4e86"
        olives = "\u6a44\u6b16\u5c71"
        self.assertIn(home, zh_source[(7, 53)])
        self.assertIn(olives, zh_source[(8, 1)])
        self.assertIn(home, zh_target[(8, 1)])
        self.assertIn(olives, zh_target[(8, 1)])

    def test_optional_tvtms_reproduces_isaiah_and_job_rows(self) -> None:
        if ARGS.tvtms is None:
            self.skipTest("pass --tvtms to verify the pinned STEPBible table")
        raw_bytes = ARGS.tvtms.read_bytes()
        self.assertEqual(
            "63058e0f20201af4bdaa7d830da5be8f493455d947c5f147d84840b33db9ddf8",
            sha256_bytes(raw_bytes),
        )
        raw = raw_bytes.decode("utf-8-sig")
        expected_lines = [
            "OneToOne\tIsa.9:1\tIsa.8:23\tIsa.9:1\tIsa.8:23",
            "OneToOne\tIsa.9:2-21\tIsa.9:1-20\tIsa.9:2-21\tIsa.9:1-20",
            "OneToOne\tIsa.64:2-12\tIsa.64:1-11",
            "OneToOne\tJob.38:39-41\tJob.38:39-41\tJob.38:39-41\tJob.38:39-41\tJob.39:1-3",
            "OneToOne\tJob.39:1-30\tJob.39:1-30\tJob.39:1-30\tJob.39:1-30\tJob.39:4-33",
            "OneToOne\tJob.40:1-5\tJob.40:1-5\tJob.39:31-35\tJob.40:1-5\tJob.39:34-38",
            "OneToOne\tJob.40:6-24\tJob.40:6-24\tJob.40:1-19\tJob.40:6-24\tJob.40:1-19",
            "OneToOne\tJob.41:1-8\tJob.40:25-32\tJob.40:20-27\tJob.40:25-32\tJob.41:1-8",
            "OneToOne\tJob.41:9\tJob.41:1\tJob.40:28\tJob.41:1\tJob.41:9",
            "OneToOne\tJob.41:10-34\tJob.41:2-26\tJob.41:1-25\tJob.41:2-26\tJob.41:10-34",
        ]
        for line in expected_lines:
            self.assertIn(line, raw)

    def test_optional_crosswire_tables(self) -> None:
        if ARGS.crosswire_luther is None or ARGS.crosswire_synodal is None:
            self.skipTest("pass both CrossWire files to verify pinned SWORD mappings")
        luther_bytes = ARGS.crosswire_luther.read_bytes()
        synodal_bytes = ARGS.crosswire_synodal.read_bytes()
        self.assertEqual(
            "5a07e22efdf46bc1dfa4a1ff644cc6d11d5b7799d5cd9617196f38388251e1c3",
            sha256_bytes(luther_bytes),
        )
        self.assertEqual(
            "76385bbd47834482c651e509e898d704cf27b5d4037ecff26ec7baf45f1edba6",
            sha256_bytes(synodal_bytes),
        )
        luther = luther_bytes.decode("utf-8-sig")
        synodal = synodal_bytes.decode("utf-8-sig")
        self.assertIn("Isa.8.23-Isa.9.20=Isa.9.1-Isa.9.21", luther)
        self.assertIn("Isa.63.19=Isa.63.19-Isa.64.1", luther)
        self.assertIn("Isa.64.1-Isa.64.11=Isa.64.2-Isa.64.12", luther)
        self.assertIn("Rom.14.24-Rom.14.26=Rom.16.25-Rom.16.27!a", synodal)

    def test_optional_ebible_archives(self) -> None:
        provided = {
            "de": ARGS.de_source,
            "it": ARGS.it_source,
            "ru": ARGS.ru_source,
            "zh-Hant": ARGS.zh_source,
        }
        if not any(provided.values()):
            self.skipTest("pass one or more --*-source archives to verify eBible inputs")
        for language, path in provided.items():
            if path is None:
                continue
            archive_sha, member, member_sha = ARCHIVES[language]
            raw = path.read_bytes()
            self.assertEqual(archive_sha, sha256_bytes(raw))
            with zipfile.ZipFile(path) as archive:
                book = archive.read(member)
            self.assertEqual(member_sha, sha256_bytes(book))
            text = book.decode("utf-8-sig")
            if language == "de":
                self.assertRegex(text, r"(?m)^\\c 9\s*$[\s\S]*?^\\v 1 \[8:23\]")
            elif language == "it":
                self.assertRegex(text, r"(?m)^\\c 39\s*$[\s\S]*?^\\v 1 .*leone")
                self.assertRegex(text, r"(?m)^\\c 41\s*$[\s\S]*?^\\v 1 .*leviatan")
            elif language == "ru":
                self.assertRegex(text, r"(?m)^\\c 14\s*$[\s\S]*?^\\v 24 .*\u0443\u0442\u0432\u0435\u0440\u0434")
                chapter_16 = text.split("\\c 16", 1)[1]
                self.assertNotRegex(chapter_16, r"(?m)^\\v 25\b")
            else:
                chapter_7, chapter_8 = text.split("\\c 7", 1)[1].split("\\c 8", 1)
                self.assertNotRegex(chapter_7, r"(?m)^\\v 53\b")
                self.assertRegex(chapter_8, r"(?m)^\\v 1 .*\u5404\u4eba\u90fd\u56de\u5bb6\u53bb\u4e86.*\u6a44\u6b16\u5c71")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(add_help=True)
    parser.add_argument("--tvtms", type=Path)
    parser.add_argument("--crosswire-luther", type=Path)
    parser.add_argument("--crosswire-synodal", type=Path)
    parser.add_argument("--de-source", type=Path)
    parser.add_argument("--it-source", type=Path)
    parser.add_argument("--ru-source", type=Path)
    parser.add_argument("--zh-source", type=Path)
    args, remaining = parser.parse_known_args()
    sys.argv = [sys.argv[0], *remaining]
    return args


ARGS = parse_args()


if __name__ == "__main__":
    unittest.main()
