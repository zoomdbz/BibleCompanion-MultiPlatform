#!/usr/bin/env python3
"""Remove local headings only after corpus-level rendered-source proof.

The live heading synchronizer intentionally refuses to delete a non-empty
heading table when one page exposes no structural heading nodes. This tool is
the second, corpus-level gate. It requires the exact 1,189-chapter inventory,
same-snapshot parity and heading evidence, and a hash of the current local
chapter story for every usable checkpoint. It changes only the JSON value of
the proven candidate stories' ``headings`` fields. Dry-run is the default.
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import copy
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
from typing import Iterator

from compare_modern_editions import EDITIONS
from sync_modern_headings_browser import (
    BOOK_INDEX,
    ROOT,
    _check_existing,
    _local_story,
    _replace_heading_value,
)


PARITY_ROOT = ROOT / ".scripture-structure-cache" / "live-browser-parity-2026-09-26"
HEADING_ROOT = ROOT / ".scripture-structure-cache" / "live-browser-headings-2026-09-26"
LOCK_PATH = ROOT / ".scripture-structure-cache" / "apply-proven-absent-headings.lock"
ABSENCE_ERROR = "source_page_has_no_structural_heading_evidence_refusing_deletion"
REFERENCE_FILE = re.compile(r"^([A-Z0-9]{3})\.([1-9]\d*)\.json$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
EXPECTED_CHAPTERS = 1189
MIN_POSITIVE_HEADING_CHAPTERS = 250
EVIDENCE_SCHEMA_VERSION = 1
LOCKED_NONCANDIDATE_SOURCE_DEFECTS = {
    ("ar", "LUK", 7): "locked_sab_luk_7_15_empty_native_range",
}

# This is the same locked Protestant inventory used by live_browser_runner.mjs.
# German SCH2000 and French NBS use four Joel chapters and three Malachi
# chapters, leaving the edition total at 1,189.
CANONICAL_CHAPTER_COUNTS = (
    ("GEN", 50), ("EXO", 40), ("LEV", 27), ("NUM", 36), ("DEU", 34),
    ("JOS", 24), ("JDG", 21), ("RUT", 4), ("1SA", 31), ("2SA", 24),
    ("1KI", 22), ("2KI", 25), ("1CH", 29), ("2CH", 36), ("EZR", 10),
    ("NEH", 13), ("EST", 10), ("JOB", 42), ("PSA", 150), ("PRO", 31),
    ("ECC", 12), ("SNG", 8), ("ISA", 66), ("JER", 52), ("LAM", 5),
    ("EZK", 48), ("DAN", 12), ("HOS", 14), ("JOL", 3), ("AMO", 9),
    ("OBA", 1), ("JON", 4), ("MIC", 7), ("NAM", 3), ("HAB", 3),
    ("ZEP", 3), ("HAG", 2), ("ZEC", 14), ("MAL", 4), ("MAT", 28),
    ("MRK", 16), ("LUK", 24), ("JHN", 21), ("ACT", 28), ("ROM", 16),
    ("1CO", 16), ("2CO", 13), ("GAL", 6), ("EPH", 6), ("PHP", 4),
    ("COL", 4), ("1TH", 5), ("2TH", 3), ("1TI", 6), ("2TI", 4),
    ("TIT", 3), ("PHM", 1), ("HEB", 13), ("JAS", 5), ("1PE", 5),
    ("2PE", 3), ("1JN", 5), ("2JN", 1), ("3JN", 1), ("JUD", 1),
    ("REV", 22),
)


class ProvenHeadingAbsenceError(ValueError):
    """The checked-in audit evidence cannot authorize a heading deletion."""


@dataclass(frozen=True)
class Checkpoint:
    path: Path
    raw: bytes
    value: dict[str, object]


@dataclass(frozen=True)
class ChapterEvidence:
    language: str
    code: str
    chapter: int
    parity: Checkpoint
    heading: Checkpoint


@dataclass(frozen=True)
class AssetSnapshot:
    path: Path
    raw: bytes
    payload: dict[str, object]


@dataclass(frozen=True)
class Candidate:
    language: str
    code: str
    chapter: int
    path: Path
    story_index: int
    existing: list[dict[str, object]]
    native_range_count: int


@dataclass(frozen=True)
class FilePlan:
    path: Path
    original: bytes
    updated: bytes
    candidates: tuple[Candidate, ...]


def _canonical_json_sha256(value: object) -> str:
    try:
        rendered = json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError) as exc:
        raise ProvenHeadingAbsenceError("asset cannot be canonically hashed") from exc
    return hashlib.sha256(rendered.encode("utf-8")).hexdigest()


def _chapter_count(language: str, code: str, default: int) -> int:
    if language in {"de", "fr"} and code == "JOL":
        return 4
    if language in {"de", "fr"} and code == "MAL":
        return 3
    return default


def _expected_references(language: str) -> tuple[tuple[str, int], ...]:
    if language not in EDITIONS:
        raise ProvenHeadingAbsenceError("unsupported language")
    if {code for code, _count in CANONICAL_CHAPTER_COUNTS} != set(BOOK_INDEX):
        raise ProvenHeadingAbsenceError("canonical book inventory differs from the linker")
    references = tuple(
        (code, chapter)
        for code, default in CANONICAL_CHAPTER_COUNTS
        for chapter in range(1, _chapter_count(language, code, default) + 1)
    )
    if len(references) != EXPECTED_CHAPTERS or len(set(references)) != EXPECTED_CHAPTERS:
        raise ProvenHeadingAbsenceError("canonical chapter inventory is invalid")
    return references


def _reference_from_path(language: str, path: Path) -> tuple[str, int]:
    match = REFERENCE_FILE.fullmatch(path.name)
    if match is None:
        raise ProvenHeadingAbsenceError("noncanonical checkpoint filename")
    code, chapter_text = match.groups()
    identity = (code, int(chapter_text))
    if identity not in set(_expected_references(language)):
        raise ProvenHeadingAbsenceError("checkpoint identity is outside the canonical inventory")
    return identity


def _inventory_paths(root: Path, language: str) -> dict[tuple[str, int], Path]:
    expected = _expected_references(language)
    expected_names = {f"{code}.{chapter}.json" for code, chapter in expected}
    folder = root / language
    paths = sorted(folder.glob("*.json"), key=lambda item: item.name)
    actual_names = {item.name for item in paths}
    if len(paths) != len(actual_names) or actual_names != expected_names:
        raise ProvenHeadingAbsenceError("checkpoint inventory is not the exact canonical 1,189 references")
    result = {_reference_from_path(language, path): path for path in paths}
    if set(result) != set(expected):
        raise ProvenHeadingAbsenceError("checkpoint inventory identity mismatch")
    return result


def _load_checkpoint(path: Path, snapshots: dict[Path, bytes]) -> Checkpoint:
    raw = path.read_bytes()
    try:
        value = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProvenHeadingAbsenceError("checkpoint is not valid JSON") from exc
    if not isinstance(value, dict):
        raise ProvenHeadingAbsenceError("checkpoint is not an object")
    snapshots[path] = raw
    return Checkpoint(path=path, raw=raw, value=value)


def _validate_parity_identity(language: str, code: str, chapter: int,
                              value: dict[str, object]) -> None:
    reference = f"{code}.{chapter}"
    edition = EDITIONS[language]
    if value.get("status") == "blocked":
        if (value.get("language") not in {None, language}
                or value.get("bibleId") not in {None, edition.bible_id}
                or value.get("reference") not in {None, reference}):
            raise ProvenHeadingAbsenceError("blocked checkpoint identity mismatch")
        return
    if (value.get("language") != language
            or value.get("bibleId") != edition.bible_id
            or value.get("reference") != reference):
        raise ProvenHeadingAbsenceError("checkpoint identity mismatch")


def _validate_envelope(language: str, code: str, chapter: int,
                       value: dict[str, object]) -> tuple[str, str]:
    edition = EDITIONS[language]
    if (type(value.get("evidenceSchemaVersion")) is not int
            or value.get("evidenceSchemaVersion") != EVIDENCE_SCHEMA_VERSION
            or value.get("language") != language
            or type(value.get("bibleId")) is not int
            or value.get("bibleId") != edition.bible_id
            or value.get("reference") != f"{code}.{chapter}"):
        raise ProvenHeadingAbsenceError("checkpoint evidence schema or identity is stale")
    snapshot_hash = value.get("snapshotSha256")
    story_hash = value.get("localStorySha256")
    if (not isinstance(snapshot_hash, str) or SHA256.fullmatch(snapshot_hash) is None
            or not isinstance(story_hash, str) or SHA256.fullmatch(story_hash) is None):
        raise ProvenHeadingAbsenceError("checkpoint evidence hashes are missing or invalid")
    return snapshot_hash, story_hash


def _asset_story(language: str, code: str, chapter: int,
                 assets: dict[Path, AssetSnapshot]) -> tuple[AssetSnapshot, int, dict[str, object], list[tuple[int, int]]]:
    collection, book_id = BOOK_INDEX[code]
    path = ROOT / "shared" / "assets" / "books" / collection / language / f"{book_id}.json"
    asset = assets.get(path)
    if asset is None:
        raw = path.read_bytes()
        try:
            payload = json.loads(raw.decode("utf-8-sig"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ProvenHeadingAbsenceError("local asset is not valid JSON") from exc
        if not isinstance(payload, dict):
            raise ProvenHeadingAbsenceError("local asset is not an object")
        asset = AssetSnapshot(path=path, raw=raw, payload=payload)
        assets[path] = asset
    try:
        story_index, story, units = _local_story(asset.payload, book_id, chapter)
    except (KeyError, TypeError, ValueError) as exc:
        raise ProvenHeadingAbsenceError("current asset chapter structure is invalid") from exc
    return asset, story_index, story, units


def _validate_current_story(language: str, code: str, chapter: int,
                            expected_hash: str,
                            assets: dict[Path, AssetSnapshot]) -> None:
    _asset, _index, story, _units = _asset_story(language, code, chapter, assets)
    if _canonical_json_sha256(story) != expected_hash:
        raise ProvenHeadingAbsenceError("current asset story differs from checkpoint evidence")


def _validate_current_parity_schema(language: str, code: str, chapter: int,
                                    value: dict[str, object]) -> None:
    edition = EDITIONS[language]
    expected_url = (
        f"https://www.bible.com/bible/{edition.bible_id}/"
        f"{code}.{chapter}.{edition.abbreviation}"
    )
    if value.get("sourceUrl") != expected_url:
        raise ProvenHeadingAbsenceError("parity source URL is missing or stale")
    if type(value.get("headingComparisonAvailable")) is not bool:
        raise ProvenHeadingAbsenceError("parity heading availability is missing")
    for field in (
        "localRanges", "sourceRanges", "rangesCompared", "localHeadings",
        "sourceHeadings", "sourceHeadingLines", "sourceOmissions", "emptyRanges",
    ):
        if type(value.get(field)) is not int or value[field] < 0:
            raise ProvenHeadingAbsenceError(f"parity field {field} is missing or invalid")
    if value["sourceHeadingLines"] < value["sourceHeadings"]:
        raise ProvenHeadingAbsenceError("parity heading counts are inconsistent")
    if not isinstance(value.get("findings"), list):
        raise ProvenHeadingAbsenceError("parity findings are missing")


def _validate_locked_source_defect(
        language: str, code: str, chapter: int,
        parity: dict[str, object], heading: dict[str, object],
        ) -> None:
    defect_code = LOCKED_NONCANDIDATE_SOURCE_DEFECTS.get((language, code, chapter))
    if defect_code is None:
        raise ProvenHeadingAbsenceError("unreviewed source-defect checkpoint")
    required = {
        "status": "source_defect",
        "sourceParityClaimed": False,
        "sourceDefectCode": defect_code,
        "sourceDefectRanges": [f"{code}.{chapter}.15"],
        "localSourceDefectVersePreserved": True,
        "localRanges": 50,
        "sourceRanges": 49,
        "rangesCompared": 49,
        "headingComparisonAvailable": True,
        "localHeadings": 4,
        "sourceHeadings": 4,
        "sourceHeadingLines": 4,
        "sourceOmissions": 0,
        "emptyRanges": 1,
    }
    if any(parity.get(key) != value for key, value in required.items()):
        raise ProvenHeadingAbsenceError("locked source-defect evidence changed")
    expected_findings = {
        ("local_range_only", "LUK.7.15-15"),
        ("heading_text_mismatch", "LUK.7.1#1"),
        ("heading_text_mismatch", "LUK.7.11#2"),
        ("heading_text_mismatch", "LUK.7.18#3"),
        ("heading_text_mismatch", "LUK.7.36#4"),
    }
    findings = parity.get("findings")
    actual_findings = {
        (finding.get("kind"), finding.get("reference"))
        for finding in findings
        if isinstance(finding, dict)
    } if isinstance(findings, list) else set()
    if actual_findings != expected_findings or len(findings or ()) != len(expected_findings):
        raise ProvenHeadingAbsenceError("locked source-defect findings changed")
    if (heading.get("status") != "not_needed"
            or heading.get("reason") != defect_code):
        raise ProvenHeadingAbsenceError("locked source-defect heading exclusion changed")


def _validated_inventory(
    language: str,
    *,
    checkpoint_snapshots: dict[Path, bytes],
    asset_snapshots: dict[Path, AssetSnapshot],
) -> dict[tuple[str, int], ChapterEvidence]:
    parity_paths = _inventory_paths(PARITY_ROOT, language)
    heading_paths = _inventory_paths(HEADING_ROOT, language)
    inventory: dict[tuple[str, int], ChapterEvidence] = {}
    positive_heading_chapters = 0
    source_defect_chapters: set[tuple[str, int]] = set()
    for code, chapter in _expected_references(language):
        parity = _load_checkpoint(parity_paths[(code, chapter)], checkpoint_snapshots)
        heading = _load_checkpoint(heading_paths[(code, chapter)], checkpoint_snapshots)
        _validate_parity_identity(language, code, chapter, parity.value)
        status = parity.value.get("status")
        if status not in {"match", "mismatch", "source_defect"}:
            raise ProvenHeadingAbsenceError("invalid parity status")
        if heading.value.get("status") not in {"match", "changed", "blocked", "not_needed"}:
            raise ProvenHeadingAbsenceError("invalid heading status")
        _validate_current_parity_schema(language, code, chapter, parity.value)
        parity_snapshot, parity_story = _validate_envelope(
            language, code, chapter, parity.value
        )
        heading_snapshot, heading_story = _validate_envelope(
            language, code, chapter, heading.value
        )
        if parity_snapshot != heading_snapshot or parity_story != heading_story:
            raise ProvenHeadingAbsenceError("parity and heading evidence came from different snapshots")
        _validate_current_story(
            language, code, chapter, parity_story, asset_snapshots
        )
        if status == "source_defect":
            _validate_locked_source_defect(
                language, code, chapter, parity.value, heading.value,
            )
            source_defect_chapters.add((code, chapter))
        source_headings = parity.value["sourceHeadings"]
        if source_headings > 0:
            if parity.value["headingComparisonAvailable"] is not True:
                raise ProvenHeadingAbsenceError("positive heading evidence is unavailable")
            positive_heading_chapters += 1
        inventory[(code, chapter)] = ChapterEvidence(
            language=language,
            code=code,
            chapter=chapter,
            parity=parity,
            heading=heading,
        )
    if positive_heading_chapters < MIN_POSITIVE_HEADING_CHAPTERS:
        raise ProvenHeadingAbsenceError("corpus lacks positive DOM heading evidence")
    expected_defects = {
        (code, chapter)
        for defect_language, code, chapter in LOCKED_NONCANDIDATE_SOURCE_DEFECTS
        if defect_language == language
    }
    if source_defect_chapters != expected_defects:
        raise ProvenHeadingAbsenceError("locked source-defect inventory is incomplete")
    return inventory


def _candidate(evidence: ChapterEvidence,
               assets: dict[Path, AssetSnapshot]) -> Candidate | None:
    heading = evidence.heading.value
    parity = evidence.parity.value
    if heading.get("status") != "blocked" or heading.get("errorCode") != ABSENCE_ERROR:
        return None
    required = {
        "status": "match",
        "sourceHeadings": 0,
        "sourceHeadingLines": 0,
        "headingComparisonAvailable": False,
        "sourceOmissions": 0,
        "emptyRanges": 0,
    }
    if any(parity.get(key) != value for key, value in required.items()):
        raise ProvenHeadingAbsenceError("candidate lacks exact empty-heading parity proof")
    local_count = parity.get("localHeadings")
    local_ranges = parity.get("localRanges")
    if (type(local_count) is not int or local_count < 1
            or type(local_ranges) is not int or local_ranges < 1
            or parity.get("sourceRanges") != local_ranges
            or parity.get("rangesCompared") != local_ranges
            or parity.get("findings") != []):
        raise ProvenHeadingAbsenceError("candidate parity evidence is inconsistent")
    asset, story_index, story, units = _asset_story(
        evidence.language, evidence.code, evidence.chapter, assets
    )
    existing = story.get("headings")
    try:
        _check_existing(existing, {start for start, _end in units})
    except (TypeError, ValueError) as exc:
        raise ProvenHeadingAbsenceError("candidate heading table is invalid") from exc
    if len(existing) != local_count or len(units) != local_ranges:
        raise ProvenHeadingAbsenceError("current asset differs from parity evidence")
    return Candidate(
        language=evidence.language,
        code=evidence.code,
        chapter=evidence.chapter,
        path=asset.path,
        story_index=story_index,
        existing=copy.deepcopy(existing),
        native_range_count=len(units),
    )


def _verify_heading_only(plan: FilePlan) -> None:
    try:
        expected = json.loads(plan.original.decode("utf-8-sig"))
        actual = json.loads(plan.updated.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProvenHeadingAbsenceError("planned asset is not valid JSON") from exc
    for candidate in plan.candidates:
        stories = expected.get("stories") if isinstance(expected, dict) else None
        if not isinstance(stories, list) or candidate.story_index >= len(stories):
            raise ProvenHeadingAbsenceError("planned story index is invalid")
        story = stories[candidate.story_index]
        if not isinstance(story, dict) or story.get("headings") != candidate.existing:
            raise ProvenHeadingAbsenceError("planned heading table changed unexpectedly")
        story["headings"] = []
    if actual != expected:
        raise ProvenHeadingAbsenceError("planned output changes data outside proven headings")
    for tag in (b"[J]", b"[/J]", b"[DN]", b"[/DN]", b"[ADD]", b"[/ADD]"):
        if plan.original.count(tag) != plan.updated.count(tag):
            raise ProvenHeadingAbsenceError("planned output changes Scripture tag counts")


def _build_plans(candidates: list[Candidate],
                 assets: dict[Path, AssetSnapshot]) -> list[FilePlan]:
    grouped: dict[Path, list[Candidate]] = {}
    for candidate in candidates:
        grouped.setdefault(candidate.path, []).append(candidate)
    plans: list[FilePlan] = []
    for path, rows in sorted(grouped.items(), key=lambda item: str(item[0])):
        original = assets[path].raw
        current = original
        ordered = tuple(sorted(rows, key=lambda item: (item.story_index, item.chapter)))
        for candidate in ordered:
            try:
                payload = json.loads(current.decode("utf-8-sig"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                raise ProvenHeadingAbsenceError("planned asset is not valid JSON") from exc
            _collection, book_id = BOOK_INDEX[candidate.code]
            try:
                story_index, story, units = _local_story(payload, book_id, candidate.chapter)
            except (KeyError, TypeError, ValueError) as exc:
                raise ProvenHeadingAbsenceError("planned chapter structure is invalid") from exc
            existing = story.get("headings")
            if (story_index != candidate.story_index
                    or existing != candidate.existing
                    or len(units) != candidate.native_range_count):
                raise ProvenHeadingAbsenceError("first-pass candidate changed during planning")
            current = _replace_heading_value(current, story_index, [], existing)
        if current == original:
            raise ProvenHeadingAbsenceError("candidate deletion made no change")
        plan = FilePlan(path=path, original=original, updated=current, candidates=ordered)
        _verify_heading_only(plan)
        plans.append(plan)
    return plans


def _stage_plans(plans: list[FilePlan]) -> dict[Path, Path]:
    staged: dict[Path, Path] = {}
    try:
        for plan in plans:
            temporary: str | None = None
            try:
                with tempfile.NamedTemporaryFile(
                    dir=plan.path.parent,
                    prefix=".heading-absence-",
                    suffix=".stage",
                    delete=False,
                ) as handle:
                    temporary = handle.name
                    handle.write(plan.updated)
                    handle.flush()
                    os.fsync(handle.fileno())
                os.chmod(temporary, stat.S_IMODE(plan.path.stat().st_mode))
                staged[plan.path] = Path(temporary)
            except Exception:
                if temporary and os.path.exists(temporary):
                    os.unlink(temporary)
                raise
    except Exception:
        _cleanup_staged(staged)
        raise
    return staged


def _cleanup_staged(staged: dict[Path, Path]) -> None:
    for temporary in staged.values():
        try:
            temporary.unlink(missing_ok=True)
        except OSError:
            pass


def _preflight(
    checkpoint_snapshots: dict[Path, bytes],
    asset_snapshots: dict[Path, AssetSnapshot],
    plans: list[FilePlan],
    staged: dict[Path, Path] | None = None,
) -> None:
    for path, expected in checkpoint_snapshots.items():
        if path.read_bytes() != expected:
            raise ProvenHeadingAbsenceError("checkpoint changed after first-pass validation")
    for path, asset in asset_snapshots.items():
        if path.read_bytes() != asset.raw:
            raise ProvenHeadingAbsenceError("asset changed after first-pass validation")
    for plan in plans:
        if asset_snapshots[plan.path].raw != plan.original:
            raise ProvenHeadingAbsenceError("planned asset snapshot mismatch")
        if staged is not None:
            temporary = staged.get(plan.path)
            if temporary is None or temporary.read_bytes() != plan.updated:
                raise ProvenHeadingAbsenceError("staged output differs from validated plan")


def _reserve_backup(path: Path) -> Path:
    descriptor, name = tempfile.mkstemp(
        dir=path.parent,
        prefix=".heading-absence-",
        suffix=".backup",
    )
    os.close(descriptor)
    os.unlink(name)
    return Path(name)


def _commit_staged(plans: list[FilePlan], staged: dict[Path, Path]) -> None:
    backups: list[tuple[Path, Path]] = []
    try:
        for plan in plans:
            # The backup receives the exact filesystem object selected by the
            # atomic rename. Verifying it after the rename closes the usual
            # compare-then-replace window before any validated output lands.
            if plan.path.read_bytes() != plan.original:
                raise ProvenHeadingAbsenceError("asset changed immediately before replacement")
            backup = _reserve_backup(plan.path)
            os.replace(plan.path, backup)
            backups.append((plan.path, backup))
            if backup.read_bytes() != plan.original:
                raise ProvenHeadingAbsenceError("asset changed during atomic replacement")
            if plan.path.exists():
                raise ProvenHeadingAbsenceError("asset was recreated during atomic replacement")
            os.replace(staged[plan.path], plan.path)
            if plan.path.read_bytes() != plan.updated:
                raise ProvenHeadingAbsenceError("committed output differs from validated plan")
        if any(plan.path.read_bytes() != plan.updated for plan in plans):
            raise ProvenHeadingAbsenceError("committed output changed before transaction completion")
    except Exception as exc:
        rollback_errors: list[str] = []
        for path, backup in reversed(backups):
            if not backup.exists():
                continue
            try:
                os.replace(backup, path)
            except OSError as rollback_error:
                rollback_errors.append(f"{path}: {rollback_error}")
        if rollback_errors:
            raise ProvenHeadingAbsenceError(
                "heading deletion failed and rollback was incomplete: "
                + "; ".join(rollback_errors)
            ) from exc
        raise
    cleanup_errors: list[str] = []
    for _path, backup in backups:
        try:
            backup.unlink()
        except OSError as cleanup_error:
            cleanup_errors.append(f"{backup}: {cleanup_error}")
    if cleanup_errors:
        # Every target already contains its fully validated output. Do not
        # attempt a partial rollback after any backup has been removed.
        raise ProvenHeadingAbsenceError(
            "heading deletion committed, but backup cleanup failed: "
            + "; ".join(cleanup_errors)
        )


@contextmanager
def _exclusive_apply_lock() -> Iterator[None]:
    LOCK_PATH.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(
            LOCK_PATH,
            os.O_CREAT | os.O_EXCL | os.O_WRONLY,
            0o600,
        )
    except FileExistsError as exc:
        raise ProvenHeadingAbsenceError("another heading deletion apply holds the exclusive lock") from exc
    try:
        os.write(descriptor, f"pid={os.getpid()}\n".encode("ascii"))
        os.fsync(descriptor)
        yield
    finally:
        os.close(descriptor)
        try:
            LOCK_PATH.unlink()
        except FileNotFoundError:
            pass


def _report(languages: list[str], candidates: list[Candidate],
            plans: list[FilePlan], apply: bool) -> dict[str, dict[str, object]]:
    report: dict[str, dict[str, object]] = {}
    for language in languages:
        language_plans = [
            plan for plan in plans
            if any(candidate.language == language for candidate in plan.candidates)
        ]
        digest = hashlib.sha256()
        changed_chapters = 0
        for plan in language_plans:
            for candidate in plan.candidates:
                if candidate.language != language:
                    continue
                digest.update(f"{candidate.code}.{candidate.chapter}\n".encode("ascii"))
                changed_chapters += 1
        if changed_chapters != sum(item.language == language for item in candidates):
            raise ProvenHeadingAbsenceError("candidate report inventory mismatch")
        report[language] = {
            "mode": "apply" if apply else "audit",
            "changedChapters": changed_chapters,
            "changedFiles": len(language_plans),
            "referenceSetSha256": digest.hexdigest(),
        }
    return report


def _run_locked(languages: list[str], *, apply: bool) -> dict[str, dict[str, object]]:
    checkpoint_snapshots: dict[Path, bytes] = {}
    asset_snapshots: dict[Path, AssetSnapshot] = {}
    candidates: list[Candidate] = []
    for language in languages:
        inventory = _validated_inventory(
            language,
            checkpoint_snapshots=checkpoint_snapshots,
            asset_snapshots=asset_snapshots,
        )
        # This is the sole candidate-selection pass. Planning below consumes
        # only these validated immutable records and never rereads checkpoints.
        for identity in _expected_references(language):
            candidate = _candidate(inventory[identity], asset_snapshots)
            if candidate is not None:
                candidates.append(candidate)
    plans = _build_plans(candidates, asset_snapshots)
    if not apply:
        _preflight(checkpoint_snapshots, asset_snapshots, plans)
        return _report(languages, candidates, plans, apply)
    staged = _stage_plans(plans)
    try:
        # No asset is replaced until every checkpoint, current asset, and
        # staged byte string has passed one final all-files preflight.
        _preflight(checkpoint_snapshots, asset_snapshots, plans, staged)
        _commit_staged(plans, staged)
    finally:
        _cleanup_staged(staged)
    return _report(languages, candidates, plans, apply)


def run(languages: list[str], *, apply: bool) -> dict[str, dict[str, object]]:
    if not languages or len(set(languages)) != len(languages):
        raise ProvenHeadingAbsenceError("languages must be a non-empty unique list")
    for language in languages:
        if language not in EDITIONS:
            raise ProvenHeadingAbsenceError("unsupported language")
    if apply:
        with _exclusive_apply_lock():
            return _run_locked(languages, apply=True)
    return _run_locked(languages, apply=False)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("languages", nargs="+", help="language codes to audit")
    parser.add_argument("--apply", action="store_true", help="remove only headings proven absent")
    args = parser.parse_args()
    print(json.dumps(run(args.languages, apply=args.apply), ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
