#!/usr/bin/env python3
"""Validate the reviewed NBS-to-LSG 1910 reference map.

The optional ``--audit-alignments`` report is a review aid.  It aligns the
checked-in French texts monotonically and prints every non-identity verse
boundary; it never writes the reviewed map.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import unicodedata
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = Path(__file__).with_name("french_reference_map.json")
BASE_ROOT = REPO_ROOT / "shared/assets/books"
OVERLAY_ROOT = BASE_ROOT / "editions/fr/lsg1910"
ANCHOR_RE = re.compile(r"\((\d+):(\d+)(?:-(\d+))?\)\.?\s*$")
INLINE_TAG_RE = re.compile(r"\[/?[A-Za-z]+\]")
TVTMS_SHA256 = "63058e0f20201af4bdaa7d830da5be8f493455d947c5f147d84840b33db9ddf8"
CROSSWIRE_SHA256 = "47dfe8f7d6a43fbe0d8f16af972db1e420907e540ab32d09a16313719a489b6f"
LSG_ARCHIVE_SHA256 = "3a0615e992ffd412b1afcaed50d146bba5ec8ae2378f04ca71459a4cd2d7cc33"
TVTMS_PATH: Path | None = None
CROSSWIRE_PATH: Path | None = None
LSG_ARCHIVE_PATH: Path | None = None

EXPECTED_BOOKS = {
    "genesis", "exodus", "numbers", "deuteronomy", "1_samuel", "2_samuel",
    "1_kings", "2_kings", "1_chronicles", "2_chronicles", "nehemiah", "job",
    "psalms", "ecclesiastes", "jeremiah", "daniel", "hosea", "zechariah", "mark",
}
EXPECTED_TARGET_ONLY = {
    "psalms": {(13, 1)},
    "john": {(5, 4)},
}
USFM_IDS = {
    "genesis": "GEN", "exodus": "EXO", "numbers": "NUM", "deuteronomy": "DEU",
    "1_samuel": "1SA", "2_samuel": "2SA", "1_kings": "1KI", "2_kings": "2KI",
    "1_chronicles": "1CH", "2_chronicles": "2CH", "nehemiah": "NEH", "job": "JOB",
    "psalms": "PSA", "ecclesiastes": "ECC", "jeremiah": "JER", "daniel": "DAN",
    "hosea": "HOS", "zechariah": "ZEC", "mark": "MRK",
}


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def base_verses(collection: str, book_id: str) -> list[tuple[tuple[int, int], str]]:
    book = load_json(BASE_ROOT / collection / "fr" / f"{book_id}.json")
    result: list[tuple[tuple[int, int], str]] = []
    for story in book["stories"]:
        for bullet in story.get("summaryBullets", []):
            match = ANCHOR_RE.search(bullet)
            if not match:
                continue
            chapter, first, last = (
                int(match.group(1)),
                int(match.group(2)),
                int(match.group(3) or match.group(2)),
            )
            text = bullet[: match.start()].strip()
            for verse in range(first, last + 1):
                result.append(((chapter, verse), text))
    return result


def target_verses(collection: str, book_id: str) -> list[tuple[tuple[int, int], str]]:
    overlay = load_json(OVERLAY_ROOT / collection / f"{book_id}.json")
    result: list[tuple[tuple[int, int], str]] = []
    for chapter in overlay["chapters"]:
        for unit in chapter["verses"]:
            for verse in range(unit["verse"], unit.get("verseEnd", unit["verse"]) + 1):
                result.append(((unit["chapter"], verse), unit["text"].strip()))
    return result


def normalized_words(text: str) -> str:
    text = INLINE_TAG_RE.sub(" ", text)
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def text_similarity(left: str, right: str) -> float:
    left = normalized_words(left)
    right = normalized_words(right)
    left_words = set(left.split())
    right_words = set(right.split())
    overlap = len(left_words & right_words) / max(1, len(left_words | right_words))
    length_ratio = min(len(left_words), len(right_words)) / max(1, len(left_words), len(right_words))
    return 0.8 * overlap + 0.2 * length_ratio


def collection_for(book_id: str) -> str:
    for collection in ("old_testament", "new_testament"):
        if (OVERLAY_ROOT / collection / f"{book_id}.json").is_file():
            return collection
    raise AssertionError(f"Missing French overlay book: {book_id}")


def row_coords(row: dict, side: str) -> list[tuple[int, int]]:
    chapter = row[f"{side}Chapter"]
    first = row[f"{side}Verse"]
    last = row.get(f"{side}VerseEnd", first)
    return [(chapter, verse) for verse in range(first, last + 1)]


def apply_audit_mapping(
    source_coords: set[tuple[int, int]], mappings: list[dict]
) -> dict[tuple[int, int], set[tuple[int, int]]]:
    """Overlay explicit exceptions on identity solely to audit inventory holes."""

    result = {coord: {coord} for coord in source_coords}
    for row in mappings:
        source = row_coords(row, "source")
        target = row_coords(row, "target")
        if len(source) == len(target):
            for left, right in zip(source, target):
                result[left] = {right}
        elif len(target) == 1:
            for left in source:
                result[left] = set(target)
        elif len(source) == 1:
            result[source[0]] = set(target)
        else:
            raise AssertionError(f"Ambiguous unequal spans: {row}")
    return result


def parse_usfm_coords(raw: bytes) -> set[tuple[int, int]]:
    chapter = None
    result: set[tuple[int, int]] = set()
    for line in raw.decode("utf-8-sig").splitlines():
        chapter_match = re.match(r"\\c\s+(\d+)", line)
        if chapter_match:
            chapter = int(chapter_match.group(1))
        verse_match = re.match(r"\\v\s+(\d+)(?:-(\d+))?", line)
        if verse_match and chapter is not None:
            first = int(verse_match.group(1))
            last = int(verse_match.group(2) or first)
            result.update((chapter, verse) for verse in range(first, last + 1))
    return result


def align_actual_texts(
    source: list[tuple[tuple[int, int], str]],
    target: list[tuple[tuple[int, int], str]],
) -> list[tuple[list[tuple[int, int]], list[tuple[int, int]], float]]:
    """Return a monotonic 1:1/1:2/2:1 alignment for source review."""

    source_text = [text for _, text in source]
    target_text = [text for _, text in target]
    # The two corpora preserve content order.  Their cumulative verse-count
    # delta stays small, so a banded dynamic program avoids an O(n^2) audit.
    band = 40
    scores: dict[tuple[int, int], float] = {(0, 0): 0.0}
    previous: dict[tuple[int, int], tuple[int, int, int, int, float]] = {}
    for total in range(len(source) + len(target) + 1):
        for i in range(max(0, total - len(target)), min(len(source), total) + 1):
            j = total - i
            if j < 0 or j > len(target) or abs(i - j) > band:
                continue
            current = scores.get((i, j))
            if current is None:
                continue
            for take_source, take_target in ((1, 1), (1, 2), (2, 1)):
                ni, nj = i + take_source, j + take_target
                if ni > len(source) or nj > len(target) or abs(ni - nj) > band:
                    continue
                left = " ".join(source_text[i:ni])
                right = " ".join(target_text[j:nj])
                similarity = text_similarity(left, right)
                candidate = current + similarity - (0.08 if take_source != take_target else 0.0)
                if candidate > scores.get((ni, nj), -1e9):
                    scores[(ni, nj)] = candidate
                    previous[(ni, nj)] = (i, j, take_source, take_target, similarity)
    cursor = (len(source), len(target))
    if cursor not in previous:
        raise AssertionError("French text alignment escaped the review band")
    aligned = []
    while cursor != (0, 0):
        i, j, take_source, take_target, similarity = previous[cursor]
        aligned.append((
            [coord for coord, _ in source[i : i + take_source]],
            [coord for coord, _ in target[j : j + take_target]],
            similarity,
        ))
        cursor = (i, j)
    return list(reversed(aligned))


def audit_alignments() -> int:
    for collection in ("old_testament", "new_testament"):
        overlay_dir = OVERLAY_ROOT / collection
        for overlay_path in sorted(overlay_dir.glob("*.json")):
            book_id = overlay_path.stem
            aligned = align_actual_texts(
                base_verses(collection, book_id), target_verses(collection, book_id)
            )
            changed = [row for row in aligned if row[0] != row[1]]
            if changed:
                print(f"{collection}/{book_id}: {len(changed)} changed units")
                for source, target, similarity in changed:
                    print(f"  {source} -> {target} ({similarity:.3f})")
    return 0


class FrenchReferenceMapTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.document = load_json(MAP_PATH)
        cls.books = {book["bookId"]: book for book in cls.document["books"]}

    def test_metadata(self) -> None:
        self.assertEqual(self.document["schemaVersion"], 1)
        self.assertEqual(self.document["language"], "fr")
        self.assertEqual(self.document["baseEditionId"], "nbs")
        self.assertEqual(self.document["editionId"], "lsg1910")
        self.assertEqual(set(self.books), EXPECTED_BOOKS)
        self.assertTrue(all(book["complete"] is False for book in self.books.values()))

    def test_every_explicit_coordinate_exists_and_no_source_is_duplicated(self) -> None:
        for book_id, book in self.books.items():
            collection = collection_for(book_id)
            source = dict(base_verses(collection, book_id))
            target = dict(target_verses(collection, book_id))
            seen: set[tuple[int, int]] = set()
            for row in book["mappings"]:
                source_coords = row_coords(row, "source")
                target_coords = row_coords(row, "target")
                self.assertTrue(set(source_coords).issubset(source), (book_id, row))
                self.assertTrue(set(target_coords).issubset(target), (book_id, row))
                self.assertFalse(seen.intersection(source_coords), (book_id, row))
                seen.update(source_coords)
                if not (book_id == "numbers" and source_coords == [(26, 1)]):
                    self.assertNotEqual(source_coords, target_coords, (book_id, row))
                self.assertTrue(
                    len(source_coords) == len(target_coords)
                    or len(source_coords) == 1
                    or len(target_coords) == 1,
                    (book_id, row),
                )

    def test_explicit_rows_join_the_same_source_text(self) -> None:
        for book_id, book in self.books.items():
            collection = collection_for(book_id)
            source = dict(base_verses(collection, book_id))
            target = dict(target_verses(collection, book_id))
            for row in book["mappings"]:
                source_coords = row_coords(row, "source")
                target_coords = row_coords(row, "target")
                # Numbers 25:19 and 26:1 jointly form LSG 26:1. Test the
                # cross-chapter merge once below; either fragment alone is not
                # a complete semantic unit.
                if book_id == "numbers" and source_coords in ([(25, 19)], [(26, 1)]):
                    continue
                similarity = text_similarity(
                    " ".join(source[coord] for coord in source_coords),
                    " ".join(target[coord] for coord in target_coords),
                )
                self.assertGreaterEqual(similarity, 0.25, (book_id, row, similarity))

        source = dict(base_verses("old_testament", "numbers"))
        target = dict(target_verses("old_testament", "numbers"))
        self.assertGreaterEqual(
            text_similarity(source[(25, 19)] + " " + source[(26, 1)], target[(26, 1)]),
            0.45,
        )

    def test_exceptions_plus_identity_explain_all_66_inventories(self) -> None:
        # This audit use of identity is not runtime behavior. It proves that the
        # explicit exceptions and documented target-only holes account for all
        # coordinate-set differences found in the two checked-in corpora.
        audited = 0
        for collection in ("old_testament", "new_testament"):
            for overlay_path in sorted((OVERLAY_ROOT / collection).glob("*.json")):
                audited += 1
                book_id = overlay_path.stem
                source_coords = set(dict(base_verses(collection, book_id)))
                target_coords = set(dict(target_verses(collection, book_id)))
                mappings = self.books.get(book_id, {}).get("mappings", [])
                resolved = apply_audit_mapping(source_coords, mappings)
                reached = set().union(*resolved.values())
                self.assertEqual(reached - target_coords, set(), book_id)
                self.assertEqual(target_coords - reached, EXPECTED_TARGET_ONLY.get(book_id, set()), book_id)
        self.assertEqual(audited, 66)

    def test_locked_boundaries(self) -> None:
        def rows(book_id: str) -> set[tuple[tuple[tuple[int, int], ...], tuple[tuple[int, int], ...]]]:
            return {
                (tuple(row_coords(row, "source")), tuple(row_coords(row, "target")))
                for row in self.books[book_id]["mappings"]
            }

        self.assertIn((((32, 1),), ((31, 55),)), rows("genesis"))
        self.assertIn((((25, 19),), ((26, 1),)), rows("numbers"))
        self.assertIn((((26, 1),), ((26, 1),)), rows("numbers"))
        self.assertIn((((7, 68),), ((7, 68), (7, 69))), rows("nehemiah"))
        self.assertIn((((34, 36), (34, 37)), ((34, 36),)), rows("job"))
        self.assertIn((((41, 1),), ((40, 28),)), rows("job"))
        self.assertIn((((13, 5), (13, 6)), ((13, 6),)), rows("psalms"))
        self.assertIn((((9, 50),), ((9, 50), (9, 51))), rows("mark"))

    def test_tvtms_pin_and_french_job_rows(self) -> None:
        if TVTMS_PATH is None:
            self.skipTest("pass --tvtms to reproduce the TVTMS pin")
        raw = TVTMS_PATH.read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(), TVTMS_SHA256)
        text = raw.decode("utf-8-sig")
        for fragment in (
            "FrenchNEG\tJob.39:1\tJob.38:39\t",
            "FrenchNEG\tJob.39:38\tJob.40:5\t",
            "FrenchNEG\tJob.40:28\tJob.41:9\t",
            "FrenchNEG\tJob.41:25\tJob.41:34\t",
        ):
            self.assertIn(fragment, text)

    def test_crosswire_pin_and_boundary_rows(self) -> None:
        if CROSSWIRE_PATH is None:
            self.skipTest("pass --crosswire to reproduce the CrossWire pin")
        raw = CROSSWIRE_PATH.read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(), CROSSWIRE_SHA256)
        text = raw.decode("utf-8-sig")
        for fragment in (
            "# Segond=English KJV",
            "1Sam.20.43=1Sam.20.42!b",
            "Job.39.1-Job.41.25=Job.38.39-Job.41.34",
            "Eccl.12.1-Eccl.12.2=Eccl.11.9-Eccl.11.10",
            "Eccl.12.3-Eccl.12.16=Eccl.12.1-Eccl.12.14",
            "Mark.9.50=Mark.9.50!a",
            "Mark.9.51=Mark.9.50!b",
        ):
            self.assertIn(fragment, text)

    def test_ebible_archive_pin_and_mapped_book_inventories(self) -> None:
        if LSG_ARCHIVE_PATH is None:
            self.skipTest("pass --lsg-archive to reproduce the eBible pin")
        raw = LSG_ARCHIVE_PATH.read_bytes()
        self.assertEqual(hashlib.sha256(raw).hexdigest(), LSG_ARCHIVE_SHA256)
        with zipfile.ZipFile(LSG_ARCHIVE_PATH) as archive:
            names = archive.namelist()
            for book_id, usfm_id in USFM_IDS.items():
                matches = [name for name in names if Path(name).name.endswith(f"{usfm_id}fraLSG.usfm")]
                self.assertEqual(len(matches), 1, book_id)
                source_coords = parse_usfm_coords(archive.read(matches[0]))
                collection = collection_for(book_id)
                overlay_coords = set(dict(target_verses(collection, book_id)))
                self.assertEqual(source_coords, overlay_coords, book_id)


def main() -> int:
    global TVTMS_PATH, CROSSWIRE_PATH, LSG_ARCHIVE_PATH
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit-alignments", action="store_true")
    parser.add_argument("--tvtms", type=Path)
    parser.add_argument("--crosswire", type=Path)
    parser.add_argument("--lsg-archive", type=Path)
    args, unittest_args = parser.parse_known_args()
    if args.audit_alignments:
        return audit_alignments()
    TVTMS_PATH = args.tvtms
    CROSSWIRE_PATH = args.crosswire
    LSG_ARCHIVE_PATH = args.lsg_archive
    program = unittest.main(argv=[sys.argv[0], *unittest_args], exit=False)
    return 0 if program.result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
