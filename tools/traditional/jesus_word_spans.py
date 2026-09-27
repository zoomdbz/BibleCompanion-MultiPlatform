"""Fail-closed, edition-specific reviewed Jesus-word annotations.

The ledger records exact speech substrings in the target edition, never
offsets projected from English. A raw-verse hash binds each review to the
specific imported text. The importer must reject incomplete mixed-verse
coverage; proposals are never promoted to reviewed annotations implicitly.
"""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path


MARKER = re.compile(r"(\[/?(?:DN|ADD|J)\])")
SOURCE_MARKER = re.compile(r"(\[/?(?:DN|ADD)\])")
KEY_FIELDS = ("collection", "bookId", "chapter", "verse")


class JesusSpanError(ValueError):
    pass


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest().upper()


def verse_key(collection: str, book_id: str, chapter: int, verse: int) -> tuple[str, str, int, int]:
    return collection, book_id, chapter, verse


def _balanced(text: str) -> bool:
    stack: list[str] = []
    for marker in MARKER.findall(text):
        name = marker[1:-1]
        if name.startswith("/"):
            if not stack or stack.pop() != name[1:]:
                return False
        else:
            stack.append(name)
    return not stack


def _plain(text: str) -> str:
    return MARKER.sub("", text)


def _insert_spans(raw: str, ranges: list[tuple[int, int]]) -> str:
    """Wrap each visible run, splitting J around pre-existing semantic tags."""
    output: list[str] = []
    plain_offset = 0
    for part in SOURCE_MARKER.split(raw):
        if SOURCE_MARKER.fullmatch(part):
            output.append(part)
            continue
        end = plain_offset + len(part)
        boundaries = {plain_offset, end}
        for start, stop in ranges:
            if plain_offset < start < end:
                boundaries.add(start)
            if plain_offset < stop < end:
                boundaries.add(stop)
        points = sorted(boundaries)
        for left, right in zip(points, points[1:]):
            piece = part[left - plain_offset:right - plain_offset]
            if any(start <= left and right <= stop for start, stop in ranges):
                output.extend(("[J]", piece, "[/J]"))
            else:
                output.append(piece)
        plain_offset = end
    return "".join(output)


@dataclass
class ReviewedJesusSpans:
    language: str
    edition_id: str
    path: Path
    rows: dict[tuple[str, str, int, int], dict]
    used: set[tuple[str, str, int, int]]
    verified_relocations: set[tuple[str, str, int, int]]

    @classmethod
    def load(cls, root: Path, language: str, edition_id: str) -> "ReviewedJesusSpans":
        path = root / "tools" / "traditional" / "jesus_word_spans" / f"{language}_{edition_id}.json"
        if not path.is_file():
            raise JesusSpanError(f"Missing reviewed Jesus-word ledger: {path}")
        payload = json.loads(path.read_text(encoding="utf-8"))
        if payload.get("schemaVersion") != 1 or payload.get("language") != language or payload.get("editionId") != edition_id:
            raise JesusSpanError(f"Wrong Jesus-word ledger identity: {path}")
        rows: dict[tuple[str, str, int, int], dict] = {}
        for row in payload.get("rows", []):
            try:
                key = tuple(row[field] for field in KEY_FIELDS)
            except KeyError as exc:
                raise JesusSpanError(f"Incomplete Jesus-word row in {path}") from exc
            if key in rows:
                raise JesusSpanError(f"Duplicate Jesus-word review: {key}")
            if not isinstance(row.get("sourceTextSha256"), str) or not re.fullmatch(r"[0-9A-F]{64}", row["sourceTextSha256"]):
                raise JesusSpanError(f"Missing source text hash: {key}")
            no_target_speech = row.get("noTargetSpeech") is True
            relocated = row.get("speechRelocatedTo") is not None
            spans = row.get("spans")
            if no_target_speech and relocated:
                raise JesusSpanError(f"Omission cannot also relocate speech: {key}")
            if no_target_speech:
                if spans not in (None, []):
                    raise JesusSpanError(f"Speech spans conflict with reviewed omission: {key}")
                if not isinstance(row.get("reason"), str) or not row["reason"].strip():
                    raise JesusSpanError(f"Reviewed omission needs explicit reason: {key}")
            elif relocated:
                destination = row["speechRelocatedTo"]
                if spans not in (None, []):
                    raise JesusSpanError(f"Relocation source contains target speech spans: {key}")
                if not isinstance(destination, dict) or destination.get("fulfillment") not in {"supplemental", "inherited"}:
                    raise JesusSpanError(f"Invalid relocation fulfillment: {key}")
                if not isinstance(destination.get("chapter"), int) or not isinstance(destination.get("verse"), int):
                    raise JesusSpanError(f"Invalid relocation target coordinate: {key}")
                if not isinstance(destination.get("exactText"), str) or not destination["exactText"]:
                    raise JesusSpanError(f"Relocation needs exact target speech: {key}")
                if not isinstance(destination.get("targetTextSha256"), str) or not re.fullmatch(r"[0-9A-F]{64}", destination["targetTextSha256"]):
                    raise JesusSpanError(f"Relocation needs target raw text hash: {key}")
            elif not isinstance(spans, list) or not spans:
                raise JesusSpanError(f"Missing reviewed speech spans: {key}")
            if row.get("supplementalFor") is not None:
                linked = row["supplementalFor"]
                if not isinstance(linked, list) or not linked or no_target_speech or relocated:
                    raise JesusSpanError(f"Invalid supplemental speech row: {key}")
                for authority in linked:
                    if not isinstance(authority, dict) or any(field not in authority for field in KEY_FIELDS):
                        raise JesusSpanError(f"Invalid supplemental authority: {key}")
            if row.get("overrideInherited") is not None and row.get("overrideInherited") is not True:
                raise JesusSpanError(f"Invalid full-verse inheritance override: {key}")
            if row.get("overrideInherited") and (row.get("supplementalFor") is not None or relocated):
                raise JesusSpanError(f"Full-verse override conflicts with relocation: {key}")
            review = row.get("review")
            if not isinstance(review, dict) or review.get("status") != "reviewed" or not review.get("evidence"):
                raise JesusSpanError(f"Unreviewed Jesus-word row: {key}")
            rows[key] = row
        return cls(language, edition_id, path, rows, set(), set())

    def apply(
        self, collection: str, book_id: str, chapter: int, verse: int,
        raw: str, verse_end: int | None = None,
    ) -> str:
        key = verse_key(collection, book_id, chapter, verse)
        row = self.rows.get(key)
        if row is None:
            return raw
        actual_end = verse if verse_end is None else verse_end
        if row.get("verseEnd", verse) != actual_end:
            raise JesusSpanError(f"Jesus-word native-unit range drift: {key}..{actual_end}")
        if key in self.used:
            raise JesusSpanError(f"Jesus-word row used twice: {key}")
        if "[J]" in raw or "[/J]" in raw:
            raise JesusSpanError(f"Source Jesus markup overlaps reviewed row: {key}")
        if not _balanced(raw):
            raise JesusSpanError(f"Unbalanced source tags: {key}")
        if sha256_text(raw) != row["sourceTextSha256"]:
            raise JesusSpanError(f"Jesus-word source text drift: {key}")
        if row.get("noTargetSpeech") is True or row.get("speechRelocatedTo") is not None:
            self.used.add(key)
            return raw
        plain = _plain(raw)
        ranges: list[tuple[int, int]] = []
        for span in row["spans"]:
            exact = span.get("exactText") if isinstance(span, dict) else None
            if not isinstance(exact, str) or not exact or plain.count(exact) == 0:
                raise JesusSpanError(f"Speech substring absent or ambiguous: {key}: {exact!r}")
            count = plain.count(exact)
            occurrence = span.get("occurrence")
            if count == 1 and occurrence is not None:
                raise JesusSpanError(f"Unnecessary occurrence for unique speech: {key}")
            if count > 1 and (not isinstance(occurrence, int) or isinstance(occurrence, bool) or not 1 <= occurrence <= count):
                raise JesusSpanError(f"Repeated speech needs valid 1-based occurrence: {key}: {exact!r}")
            index = 1 if occurrence is None else occurrence
            start = -1
            for _ in range(index):
                start = plain.find(exact, start + 1)
            ranges.append((start, start + len(exact)))
        ranges.sort()
        if any(a_end > b_start for (_, a_end), (b_start, _) in zip(ranges, ranges[1:])):
            raise JesusSpanError(f"Overlapping reviewed speech spans: {key}")
        result = _insert_spans(raw, ranges)
        if not _balanced(result) or _plain(result) != plain:
            raise JesusSpanError(f"Invalid output markup or changed visible text: {key}")
        for marker in ("[DN]", "[/DN]", "[ADD]", "[/ADD]"):
            if result.count(marker) != raw.count(marker):
                raise JesusSpanError(f"Changed source marker inventory: {key}: {marker}")
        if result.replace("[J]", "").replace("[/J]", "") != raw:
            raise JesusSpanError(f"Changed raw Scripture text: {key}")
        self.used.add(key)
        return result

    def validate_coverage(
        self, expected: set[tuple[str, str, int, int]],
        expected_full: set[tuple[str, str, int, int]] | None = None,
    ) -> None:
        expected_full = set() if expected_full is None else expected_full
        full_overrides = {key for key, row in self.rows.items() if row.get("overrideInherited") is True}
        invalid_full = full_overrides - expected_full
        if invalid_full:
            raise JesusSpanError(f"Full-verse override lacks KJV full authority: {sorted(invalid_full)[:5]}")
        supplemental = {key for key, row in self.rows.items() if row.get("supplementalFor") is not None}
        linked: dict[tuple[str, str, int, int], tuple[str, str, int, int]] = {}
        for target_key in supplemental:
            for authority in self.rows[target_key]["supplementalFor"]:
                source_key = tuple(authority[field] for field in KEY_FIELDS)
                if source_key in linked or source_key not in expected:
                    raise JesusSpanError(f"Duplicate or non-candidate supplemental authority: {source_key}")
                source_row = self.rows.get(source_key, {})
                destination = source_row.get("speechRelocatedTo")
                if destination is None or destination.get("fulfillment") != "supplemental" or target_key != (
                    source_key[0], source_key[1], destination["chapter"], destination["verse"]
                ):
                    raise JesusSpanError(f"Orphan supplemental speech row: {target_key}")
                linked[source_key] = target_key
        for source_key in expected:
            source_row = self.rows.get(source_key, {})
            destination = source_row.get("speechRelocatedTo")
            if destination is not None and destination["fulfillment"] == "supplemental" and source_key not in linked:
                raise JesusSpanError(f"Relocation has no supplemental target: {source_key}")
        required = expected | supplemental | full_overrides
        missing = required - self.used
        extra = set(self.rows) - required
        if missing or extra:
            sample = sorted(missing)[:5]
            raise JesusSpanError(
                f"Incomplete Jesus-word ledger {self.language}/{self.edition_id}: "
                f"{len(self.used)}/{len(expected)} reviewed mixed verses; "
                f"missing={len(missing)} sample={sample}; extra={len(extra)}"
            )
        unverified = {key for key in expected if self.rows.get(key, {}).get("speechRelocatedTo") is not None} - self.verified_relocations
        if unverified:
            raise JesusSpanError(f"Unverified Jesus-word relocation: {sorted(unverified)[:5]}")

    def verify_relocations(self, collection: str, book_id: str, chapters: list[dict]) -> None:
        """Prove relocated authority has its exact words colored at the target unit."""
        targets = {
            (chapter["number"], verse["verse"]): verse
            for chapter in chapters for verse in chapter["verses"]
        }
        for key, row in self.rows.items():
            if key[:2] != (collection, book_id):
                continue
            destination = row.get("speechRelocatedTo")
            if destination is None:
                continue
            target = targets.get((destination["chapter"], destination["verse"]))
            if target is None:
                raise JesusSpanError(f"Relocated Jesus speech target absent: {key}")
            tagged = target["text"]
            raw = tagged.replace("[J]", "").replace("[/J]", "")
            if sha256_text(raw) != destination["targetTextSha256"]:
                raise JesusSpanError(f"Relocated Jesus speech target drift: {key}")
            if destination["fulfillment"] == "supplemental" and (
                collection, book_id, destination["chapter"], destination["verse"]
            ) not in self.used:
                raise JesusSpanError(f"Relocated supplemental speech was not applied: {key}")
            exact = destination["exactText"]
            plain = _plain(tagged)
            if plain.count(exact) != 1:
                raise JesusSpanError(f"Relocated Jesus speech not unique in target: {key}")
            start = plain.index(exact)
            end = start + len(exact)
            position = 0
            in_jesus = False
            colored = [False] * len(plain)
            for part in MARKER.split(tagged):
                if part == "[J]":
                    in_jesus = True
                elif part == "[/J]":
                    in_jesus = False
                elif not MARKER.fullmatch(part):
                    for offset in range(position, position + len(part)):
                        colored[offset] = in_jesus
                    position += len(part)
            if not all(colored[start:end]):
                raise JesusSpanError(f"Relocated Jesus speech is not wholly marked: {key}")
            self.verified_relocations.add(key)
