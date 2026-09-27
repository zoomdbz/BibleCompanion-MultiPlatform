#!/usr/bin/env python3
"""Audit current canonical assets against sanitized BibleGateway cache records.

This command is strictly offline and read-only. The cache contains publisher
metadata, native verse ranges, and one-way hashes, never source verse text. A
clean hash comparison proves equality to that cached BibleGateway response; it
does not prove equality to a different platform revision with the same label.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))

import audit_localized_scripture as source_audit


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CACHE = REPO_ROOT / ".scripture-structure-cache" / "structure"
SUPPORTED_LANGUAGES = ("de", "es", "it", "pt", "ru", "zh-Hans")


class OfflineAuditError(RuntimeError):
    pass


def cache_digest(root: Path, files: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(files, key=lambda item: item.relative_to(root).as_posix()):
        relative = path.relative_to(root).as_posix().encode("utf-8")
        digest.update(relative)
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest().upper()


def finding_counts(findings: list[dict[str, Any]]) -> dict[str, int]:
    counts: Counter[str] = Counter()
    for finding in findings:
        kind = finding["type"]
        if kind in {"missing-local-verses", "unexpected-local-verses", "traditional-local-verses-need-fallback-proof"}:
            counts[kind] += len(finding["verses"])
        elif kind == "semantic-marker-presence-mismatch":
            counts[kind] += len(finding["verses"])
        elif kind == "exact-text-mismatch":
            counts[kind] += len(finding["ranges"])
        elif kind == "exact-text-unavailable":
            counts[kind] += len(finding["ranges"])
        else:
            counts[kind] += 1
    return dict(sorted(counts.items()))


def exact_counts(
    local: tuple[source_audit.LocalMarker, ...],
    source: source_audit.ChapterStructure,
) -> Counter[str]:
    counts: Counter[str] = Counter()
    local_by_range = {(item.start, item.end): item for item in local}
    counts["localNativeUnits"] = len(local)
    for marker in source.markers:
        if marker.empty:
            counts["sourceEmptyMarkers"] += 1
            continue
        counts["sourceNativeUnits"] += 1
        candidate = local_by_range.get((marker.start, marker.end))
        if candidate is None:
            counts["sourceRangesWithoutMatchingLocalRange"] += 1
            continue
        counts["rangeComparable"] += 1
        if marker.text_sha256 is None:
            counts["sourceTextHashUnavailable"] += 1
            continue
        local_hash = source_audit.sha256_text(source_audit.normalized_text(candidate.plain))
        if local_hash == marker.text_sha256:
            counts["exactTextHashMatches"] += 1
        else:
            counts["textHashMismatches"] += 1
    return counts


def audit_language(repo_root: Path, cache_root: Path, language: str) -> dict[str, Any]:
    if language not in SUPPORTED_LANGUAGES:
        raise OfflineAuditError(f"no complete sanitized cache is configured for {language}")
    edition = source_audit.EDITIONS[language]
    edition_root = cache_root / edition.gateway_code
    if not edition_root.is_dir():
        raise OfflineAuditError(f"missing sanitized cache directory: {edition_root}")

    files = source_audit.canonical_files(repo_root, language)
    expected_cache: set[Path] = set()
    findings: list[dict[str, Any]] = []
    exact: Counter[str] = Counter()
    books: list[dict[str, Any]] = []
    metadata_values: set[tuple[str, str, str]] = set()

    for book, asset_path in files.items():
        chapters = source_audit.local_chapters(
            source_audit.read_json_object(asset_path), language, book
        )
        book_findings: list[dict[str, Any]] = []
        book_exact: Counter[str] = Counter()
        for chapter, story in chapters.items():
            cache_path = edition_root / book / f"{chapter}.json"
            expected_cache.add(cache_path)
            loaded = source_audit.load_cached_structure(
                cache_path, edition, book, chapter, frozenset()
            )
            if loaded is None:
                raise OfflineAuditError(f"missing or empty sanitized cache record: {cache_path}")
            structure, metadata = loaded
            metadata_values.add((metadata.version_label, metadata.copyright, metadata.details_url))
            local_markers, local_headings = source_audit.parse_local_story(
                story, language, book, chapter
            )
            chapter_findings = source_audit.compare_chapter(
                language, book, chapter, local_markers, local_headings, structure,
                frozenset(), compare_jesus=False, compare_additions=False,
                exact_text=True,
            )
            book_findings.extend(chapter_findings)
            book_exact.update(exact_counts(local_markers, structure))
        findings.extend(book_findings)
        exact.update(book_exact)
        books.append({
            "book": book,
            "chapters": len(chapters),
            "findings": finding_counts(book_findings),
            **dict(book_exact),
        })

    actual_cache = set(edition_root.rglob("*.json"))
    if actual_cache != expected_cache:
        missing = sorted(path.relative_to(cache_root).as_posix() for path in expected_cache - actual_cache)
        extra = sorted(path.relative_to(cache_root).as_posix() for path in actual_cache - expected_cache)
        raise OfflineAuditError(f"cache inventory mismatch for {language}; missing={missing[:5]}, extra={extra[:5]}")
    if len(metadata_values) != 1:
        raise OfflineAuditError(f"inconsistent publisher metadata across {language} cache")
    version_label, copyright_text, details_url = next(iter(metadata_values))
    return {
        "language": language,
        "intendedBibleComEdition": {
            "id": edition.bible_com_id,
            "code": edition.bible_com_code,
        },
        "comparisonSource": {
            "platform": "BibleGateway",
            "code": edition.gateway_code,
            "label": version_label,
            "copyright": copyright_text,
            "detailsUrl": details_url,
            "sameEditionLabelAsBibleCom": edition.bible_com_same_edition,
            "cacheAggregateSha256": cache_digest(cache_root, sorted(actual_cache)),
            "cacheFiles": len(actual_cache),
        },
        "scope": (
            "Offline comparison to the cached BibleGateway response only. "
            "It is not authority for a different Bible.com revision."
        ),
        "books": len(books),
        "chapters": sum(item["chapters"] for item in books),
        **dict(exact),
        "findings": finding_counts(findings),
        "bookResults": books,
    }


def parse_languages(raw: str) -> list[str]:
    selected = [item.strip() for item in raw.split(",") if item.strip()]
    unknown = sorted(set(selected) - set(SUPPORTED_LANGUAGES))
    if unknown:
        raise OfflineAuditError(f"unsupported or incomplete cached languages: {unknown}")
    return selected


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-root", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--languages", default=",".join(SUPPORTED_LANGUAGES))
    parser.add_argument("--details", action="store_true", help="include per-book counts")
    args = parser.parse_args()
    try:
        reports = [
            audit_language(REPO_ROOT, args.cache_root.resolve(), language)
            for language in parse_languages(args.languages)
        ]
        if not args.details:
            for report in reports:
                report.pop("bookResults", None)
        result = {
            "mode": "read-only offline hash and native-structure audit",
            "languages": reports,
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if any(report["findings"] for report in reports) else 0
    except (
        OfflineAuditError, source_audit.AuditFailure, OSError, UnicodeError,
        ValueError, KeyError,
    ) as exc:
        print(f"cached modern-edition audit failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
