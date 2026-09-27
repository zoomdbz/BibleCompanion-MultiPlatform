#!/usr/bin/env python3
"""Offline, fail-closed coverage audit for packaged edition reference maps.

This audits reference equivalence, not translation accuracy. No verse text is
printed or written. Exact unresolved native coordinate ranges remain in the
report. Only explicit map rows can establish cross-edition coverage.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
import json
from pathlib import Path
import re
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
ASSETS_ROOT = REPO_ROOT / "shared" / "assets" / "books"
EDITIONS = {
    "en": ("bsb", "kjv1769"),
    "de": ("sch2000", "luther1912"),
    "es": ("nvi", "rv1909"),
    "fr": ("nbs", "lsg1910"),
    "it": ("nr06", "diodati1885"),
    "pt": ("nvt", "almeida1911"),
    "ru": ("nrt_nrp", "synodal1876"),
    "ja": ("jcb", "bungo"),
    "ko": ("rnksv", "korrv"),
    "zh-Hans": ("ccb", "cuv"),
    "zh-Hant": ("rcuv", "cuv"),
    "ar": ("sab", "van_dyck"),
}
COLLECTIONS = ("old_testament", "new_testament")
MARKER = re.compile(r"\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\s*\.?\s*$")


class AuditError(ValueError):
    pass


@dataclass(frozen=True, order=True)
class Anchor:
    chapter: int
    start: int
    end: int

    def numbers(self) -> range:
        return range(self.start, self.end + 1)


@dataclass(frozen=True)
class Rule:
    source: Anchor
    target: Anchor


def _object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise AuditError(f"{label}: expected object")
    return value


def _list(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise AuditError(f"{label}: expected list")
    return value


def _integer(value: Any, label: str) -> int:
    if type(value) is not int or value < 1:
        raise AuditError(f"{label}: expected positive integer")
    return value


def _read_json(path: Path) -> dict[str, Any]:
    try:
        return _object(json.loads(path.read_text(encoding="utf-8-sig")), str(path))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise AuditError(f"{path}: cannot read valid JSON: {exc}") from exc


def _anchor(chapter: Any, start: Any, end: Any, label: str) -> Anchor:
    c = _integer(chapter, f"{label}.chapter")
    s = _integer(start, f"{label}.verse")
    e = s if end is None else _integer(end, f"{label}.verseEnd")
    if e < s or e - s > 1000:
        raise AuditError(f"{label}: invalid verse range")
    return Anchor(c, s, e)


def _index(units: list[Anchor], label: str) -> dict[tuple[int, int], Anchor]:
    result: dict[tuple[int, int], Anchor] = {}
    for unit in units:
        for verse in unit.numbers():
            key = (unit.chapter, verse)
            if key in result:
                raise AuditError(f"{label}: overlapping native units at {unit.chapter}:{verse}")
            result[key] = unit
    if not result:
        raise AuditError(f"{label}: no native verse units")
    return result


def base_units(path: Path) -> tuple[list[Anchor], dict[tuple[int, int], Anchor]]:
    book = _read_json(path)
    units: list[Anchor] = []
    for story in _list(book.get("stories"), f"{path}.stories"):
        bullets = _list(_object(story, f"{path}.story").get("summaryBullets"), f"{path}.summaryBullets")
        for bullet in bullets:
            if not isinstance(bullet, str):
                raise AuditError(f"{path}: non-string summary bullet")
            match = MARKER.search(bullet)
            if match is None:
                raise AuditError(f"{path}: Scripture bullet lacks a trailing native reference")
            units.append(_anchor(int(match[1]), int(match[2]), int(match[3]) if match[3] else None, str(path)))
    return units, _index(units, str(path))


def overlay_units(path: Path, language: str, edition: str, collection: str, book_id: str) -> tuple[list[Anchor], dict[tuple[int, int], Anchor]]:
    overlay = _read_json(path)
    expected = {"language": language, "editionId": edition, "collection": collection, "bookId": book_id}
    for key, value in expected.items():
        if overlay.get(key) != value:
            raise AuditError(f"{path}: {key} does not match {value}")
    units: list[Anchor] = []
    chapters = _list(overlay.get("chapters"), f"{path}.chapters")
    for expected_chapter, chapter in enumerate(chapters, start=1):
        row = _object(chapter, f"{path}.chapter")
        number = _integer(row.get("number"), f"{path}.chapter.number")
        if number != expected_chapter:
            raise AuditError(f"{path}: chapters are not consecutively numbered")
        expected_verse = 1
        verses = _list(row.get("verses"), f"{path}.chapter.verses")
        if not verses:
            raise AuditError(f"{path}: chapter has no native units")
        for verse in verses:
            item = _object(verse, f"{path}.verse")
            unit = _anchor(item.get("chapter"), item.get("verse"), item.get("verseEnd"), str(path))
            if unit.chapter != number:
                raise AuditError(f"{path}: verse chapter disagrees with containing chapter")
            if unit.start != expected_verse:
                raise AuditError(f"{path}: overlay verses are not consecutive")
            units.append(unit)
            expected_verse = unit.end + 1
    return units, _index(units, str(path))


def reference_map(path: Path, language: str, base: str, alternate: str) -> dict[str, list[Rule]]:
    if not path.exists():
        return {}
    doc = _read_json(path)
    expected = {"schemaVersion": 1, "language": language, "baseEditionId": base, "editionId": alternate}
    for key, value in expected.items():
        if doc.get(key) != value:
            raise AuditError(f"{path}: {key} does not match {value}")
    result: dict[str, list[Rule]] = {}
    for book in _list(doc.get("books"), f"{path}.books"):
        row = _object(book, f"{path}.book")
        book_id = row.get("bookId")
        if not isinstance(book_id, str) or not book_id or book_id in result:
            raise AuditError(f"{path}: missing or duplicate bookId")
        if "complete" in row and type(row["complete"]) is not bool:
            raise AuditError(f"{path}/{book_id}: complete must be boolean")
        rules: list[Rule] = []
        for i, item in enumerate(_list(row.get("mappings"), f"{path}/{book_id}.mappings")):
            rule = _object(item, f"{path}/{book_id}.mapping[{i}]")
            label = f"{path}/{book_id}.mapping[{i}]"
            source = _anchor(rule.get("sourceChapter"), rule.get("sourceVerse"), rule.get("sourceVerseEnd"), f"{label}.source")
            target = _anchor(rule.get("targetChapter"), rule.get("targetVerse"), rule.get("targetVerseEnd"), f"{label}.target")
            rules.append(Rule(source, target))
        result[book_id] = rules
    return result


def validate_rules(rules: list[Rule], source: dict[tuple[int, int], Anchor], target: dict[tuple[int, int], Anchor], label: str) -> None:
    for i, rule in enumerate(rules):
        for side, anchor, index in (("source", rule.source, source), ("target", rule.target, target)):
            missing = next((n for n in anchor.numbers() if (anchor.chapter, n) not in index), None)
            if missing is not None:
                raise AuditError(f"{label}: rule {i} {side} coordinate {anchor.chapter}:{missing} is absent")


def _rules_by_verse(rules: list[Rule], reverse: bool) -> dict[tuple[int, int], list[Rule]]:
    indexed: dict[tuple[int, int], list[Rule]] = defaultdict(list)
    for rule in rules:
        origin = rule.target if reverse else rule.source
        for number in origin.numbers():
            indexed[(origin.chapter, number)].append(rule)
    return indexed


def map_unit(unit: Anchor, rules_by_verse: dict[tuple[int, int], list[Rule]], target_index: dict[tuple[int, int], Anchor], reverse: bool = False) -> tuple[Anchor | None, str]:
    targets: set[Anchor] = set()
    for number in unit.numbers():
        matches = rules_by_verse.get((unit.chapter, number), ())
        if not matches:
            return None, "unreviewed"
        for rule in matches:
            origin, destination = (rule.target, rule.source) if reverse else (rule.source, rule.target)
            origin_length = origin.end - origin.start + 1
            destination_length = destination.end - destination.start + 1
            if origin_length == destination_length:
                mapped = destination.start + number - origin.start
                targets.add(Anchor(destination.chapter, mapped, mapped))
            elif origin_length == 1 or destination_length == 1:
                targets.add(destination)
            else:
                return None, "unequal_multi_span"
    ordered = sorted(targets)
    if not ordered:
        return None, "unreviewed"
    first = ordered[0]
    end = first.end
    for target in ordered[1:]:
        if target.chapter != first.chapter:
            return None, "cross_chapter"
        if target.start > end + 1:
            return None, "disjoint"
        end = max(end, target.end)
    for number in range(first.start, end + 1):
        if (first.chapter, number) not in target_index:
            return None, "missing_target"
    touching = {target_index[(first.chapter, number)] for number in range(first.start, end + 1)}
    return Anchor(first.chapter, min(t.start for t in touching), max(t.end for t in touching)), "resolved"


def _count_direction(units: list[Anchor], rules: list[Rule], target_index: dict[tuple[int, int], Anchor], reverse: bool) -> dict[str, Any]:
    indexed = _rules_by_verse(rules, reverse)
    reasons: Counter[str] = Counter()
    coordinate_reasons: Counter[str] = Counter()
    unresolved: list[dict[str, Any]] = []
    for unit in units:
        _, reason = map_unit(unit, indexed, target_index, reverse)
        reasons[reason] += 1
        coordinate_reasons[reason] += unit.end - unit.start + 1
        if reason != "resolved":
            if (unresolved and unresolved[-1]["chapter"] == unit.chapter
                and unresolved[-1]["end"] + 1 == unit.start
                and unresolved[-1]["reason"] == reason):
                unresolved[-1]["end"] = unit.end
            else:
                unresolved.append({"chapter": unit.chapter, "start": unit.start,
                                   "end": unit.end, "reason": reason})
    return {
        "sourceUnits": len(units),
        "reviewedResolvable": reasons.pop("resolved", 0),
        "retainSourceEdition": sum(reasons.values()),
        "retentionReasons": dict(sorted(reasons.items())),
        "sourceCoordinates": sum(coordinate_reasons.values()),
        "reviewedResolvableCoordinates": coordinate_reasons.pop("resolved", 0),
        "retainSourceEditionCoordinates": sum(coordinate_reasons.values()),
        "unresolvedRanges": unresolved,
    }


def audit(assets_root: Path = ASSETS_ROOT, editions: dict[str, tuple[str, str]] = EDITIONS) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    totals: dict[str, dict[str, Counter[str]]] = {
        language: {"baseToAlternate": Counter(), "alternateToBase": Counter()}
        for language in editions
    }
    for language, (base, alternate) in editions.items():
        edition_dir = assets_root / "editions" / language / alternate
        manifest = _read_json(edition_dir / "_manifest.json")
        if manifest.get("language") != language or manifest.get("editionId") != alternate:
            raise AuditError(f"{edition_dir}: manifest language or edition mismatch")
        manifest_books = {}
        for item in _list(manifest.get("books"), f"{edition_dir}/_manifest.json.books"):
            entry = _object(item, f"{edition_dir}/_manifest.json.book")
            if entry.get("collection") not in COLLECTIONS:
                continue
            book_id = entry.get("bookId")
            collection = entry["collection"]
            if not isinstance(book_id, str) or not book_id or (collection, book_id) in manifest_books:
                raise AuditError(f"{edition_dir}: duplicate or invalid manifest book")
            if entry.get("coverage") != "full" or entry.get("output") != f"{collection}/{book_id}.json":
                raise AuditError(f"{edition_dir}: {collection}/{book_id} lacks full packaged overlay")
            manifest_books[(collection, book_id)] = entry
        base_books = {
            (collection, path.stem)
            for collection in COLLECTIONS
            for path in (assets_root / collection / language).glob("*.json")
            if not path.stem.startswith("_")
        }
        if base_books != set(manifest_books):
            missing = sorted(base_books - set(manifest_books))
            extra = sorted(set(manifest_books) - base_books)
            raise AuditError(f"{edition_dir}: manifest/base book mismatch; missing={missing}, extra={extra}")
        maps_path = edition_dir / "_reference_map.json"
        maps = reference_map(maps_path, language, base, alternate)
        unknown = sorted(set(maps) - {book_id for _, book_id in base_books})
        if unknown:
            raise AuditError(f"{maps_path}: map names unknown books {unknown}")
        for collection, book_id in sorted(base_books):
            source_units, source_index = base_units(assets_root / collection / language / f"{book_id}.json")
            target_units, target_index = overlay_units(edition_dir / collection / f"{book_id}.json", language, alternate, collection, book_id)
            rules = maps.get(book_id, [])
            validate_rules(rules, source_index, target_index, f"{maps_path}/{book_id}")
            forward = _count_direction(source_units, rules, target_index, reverse=False)
            reverse = _count_direction(target_units, rules, source_index, reverse=True)
            rows.append({
                "language": language, "book": f"{collection}/{book_id}",
                "baseEdition": base, "alternateEdition": alternate,
                "map": str(maps_path.relative_to(assets_root)) if book_id in maps else None,
                "explicitRules": len(rules),
                "baseToAlternate": forward, "alternateToBase": reverse,
            })
            for name, direction in (("baseToAlternate", forward), ("alternateToBase", reverse)):
                for key in ("sourceUnits", "reviewedResolvable", "retainSourceEdition",
                            "sourceCoordinates", "reviewedResolvableCoordinates",
                            "retainSourceEditionCoordinates"):
                    totals[language][name][key] += direction[key]
    return {
        "policy": "Only explicit, validated rows count as reviewed. Unmapped or unrepresentable native units retain their source edition. Counts cover both directions.",
        "languages": {
            lang: {name: dict(counts) for name, counts in totals[lang].items()}
            for lang in editions
        },
        "books": rows,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets-root", type=Path, default=ASSETS_ROOT, help="Packaged books assets directory")
    parser.add_argument("--report", type=Path, help="Optional JSON output path within this repository")
    args = parser.parse_args()
    try:
        result = audit(args.assets_root)
        output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.report:
            destination = args.report.resolve()
            if not destination.is_relative_to(REPO_ROOT):
                raise AuditError("report path must be inside the repository")
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(output, encoding="utf-8")
            print(f"Wrote coordinate coverage report: {destination}")
        else:
            print(output, end="")
    except AuditError as exc:
        parser.exit(1, f"reference coverage audit failed: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
