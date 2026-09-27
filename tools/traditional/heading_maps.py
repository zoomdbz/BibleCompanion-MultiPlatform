"""Load and strictly validate edition-specific heading placement tables.

The table stores only reviewed exceptions. Ordinary headings continue through
the edition reference map or retain their localized base coordinate. A single
base heading can produce more than one target row, but the target fragments
must reproduce its text exactly and in order with its declared separator.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


SCHEMA_VERSION = 1


class HeadingMapError(ValueError):
    """The reviewed heading table is malformed or internally inconsistent."""


@dataclass(frozen=True)
class HeadingTarget:
    chapter: int
    before_verse: int
    text: str


@dataclass(frozen=True)
class HeadingRelocation:
    source_chapter: int
    source_before_verse: int
    source_text: str
    join_with: str
    targets: tuple[HeadingTarget, ...]


@dataclass(frozen=True)
class EditionHeadingMap:
    language: str
    edition_id: str
    book_code: str
    collection: str
    book_id: str
    source_title: str
    source_url: str
    source_artifact_sha256: str
    source_date: str
    relocations: tuple[HeadingRelocation, ...]


def _object(value: object, label: str) -> dict:
    if not isinstance(value, dict):
        raise HeadingMapError(f"{label} must be an object")
    return value


def _exact_keys(value: dict, required: set[str], label: str) -> None:
    missing = sorted(required - value.keys())
    extra = sorted(value.keys() - required)
    if missing or extra:
        details = []
        if missing:
            details.append(f"missing {', '.join(missing)}")
        if extra:
            details.append(f"unknown {', '.join(extra)}")
        raise HeadingMapError(f"{label}: {'; '.join(details)}")


def _text(value: object, label: str) -> str:
    if not isinstance(value, str) or not value or value != value.strip():
        raise HeadingMapError(f"{label} must be a nonblank, trimmed string")
    return value


def _positive_int(value: object, label: str) -> int:
    if type(value) is not int or value < 1:
        raise HeadingMapError(f"{label} must be a positive integer")
    return value


def _sha256(value: object, label: str) -> str:
    digest = _text(value, label).upper()
    if len(digest) != 64 or any(character not in "0123456789ABCDEF" for character in digest):
        raise HeadingMapError(f"{label} must be a SHA-256 digest")
    return digest


def load_heading_maps(path: Path) -> dict[tuple[str, str, str], EditionHeadingMap]:
    """Return reviewed tables keyed by language, edition id, and book code."""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise HeadingMapError(f"Cannot read heading map {path}: {exc}") from exc

    root = _object(document, "heading map root")
    _exact_keys(root, {"schemaVersion", "description", "editions"}, "heading map root")
    if root["schemaVersion"] != SCHEMA_VERSION:
        raise HeadingMapError(
            f"Unsupported heading map schema {root['schemaVersion']!r}; expected {SCHEMA_VERSION}"
        )
    _text(root["description"], "heading map description")
    if not isinstance(root["editions"], list):
        raise HeadingMapError("heading map editions must be an array")

    result: dict[tuple[str, str, str], EditionHeadingMap] = {}
    for edition_index, raw_edition in enumerate(root["editions"]):
        label = f"heading map edition {edition_index}"
        edition = _object(raw_edition, label)
        _exact_keys(
            edition,
            {
                "language", "editionId", "bookCode", "collection", "bookId",
                "pinnedSource", "relocations",
            },
            label,
        )
        language = _text(edition["language"], f"{label} language")
        edition_id = _text(edition["editionId"], f"{label} editionId")
        book_code = _text(edition["bookCode"], f"{label} bookCode")
        collection = _text(edition["collection"], f"{label} collection")
        book_id = _text(edition["bookId"], f"{label} bookId")
        key = (language, edition_id, book_code)
        if key in result:
            raise HeadingMapError(f"Duplicate edition heading key: {'/'.join(key)}")

        source = _object(edition["pinnedSource"], f"{label} pinnedSource")
        _exact_keys(
            source,
            {"title", "url", "artifactSha256", "sourceDate"},
            f"{label} pinnedSource",
        )
        source_title = _text(source["title"], f"{label} pinnedSource title")
        source_url = _text(source["url"], f"{label} pinnedSource url")
        source_sha256 = _sha256(
            source["artifactSha256"], f"{label} pinnedSource artifactSha256"
        )
        source_date = _text(source["sourceDate"], f"{label} pinnedSource sourceDate")

        raw_relocations = edition["relocations"]
        if not isinstance(raw_relocations, list) or not raw_relocations:
            raise HeadingMapError(f"{label} relocations must be a nonempty array")
        seen_sources: set[tuple[int, int]] = set()
        relocations: list[HeadingRelocation] = []
        for relocation_index, raw_relocation in enumerate(raw_relocations):
            relocation_label = f"{label} relocation {relocation_index}"
            relocation = _object(raw_relocation, relocation_label)
            _exact_keys(
                relocation,
                {"sourceChapter", "sourceBeforeVerse", "sourceText", "joinWith", "targets"},
                relocation_label,
            )
            source_chapter = _positive_int(
                relocation["sourceChapter"], f"{relocation_label} sourceChapter"
            )
            source_verse = _positive_int(
                relocation["sourceBeforeVerse"], f"{relocation_label} sourceBeforeVerse"
            )
            source_text = _text(relocation["sourceText"], f"{relocation_label} sourceText")
            join_with = relocation["joinWith"]
            if join_with not in {" ", "\n"}:
                raise HeadingMapError(
                    f"{relocation_label} joinWith must be one space or one newline"
                )
            source_key = (source_chapter, source_verse)
            if source_key in seen_sources:
                raise HeadingMapError(
                    f"Duplicate heading source key: {'/'.join(key)} {source_chapter}:{source_verse}"
                )
            seen_sources.add(source_key)

            raw_targets = relocation["targets"]
            if not isinstance(raw_targets, list) or not raw_targets:
                raise HeadingMapError(f"{relocation_label} targets must be a nonempty array")
            targets: list[HeadingTarget] = []
            seen_targets: set[tuple[int, int]] = set()
            for target_index, raw_target in enumerate(raw_targets):
                target_label = f"{relocation_label} target {target_index}"
                target = _object(raw_target, target_label)
                _exact_keys(target, {"chapter", "beforeVerse", "text"}, target_label)
                target_chapter = _positive_int(target["chapter"], f"{target_label} chapter")
                target_verse = _positive_int(
                    target["beforeVerse"], f"{target_label} beforeVerse"
                )
                target_text = _text(target["text"], f"{target_label} text")
                target_key = (target_chapter, target_verse)
                if target_key in seen_targets:
                    raise HeadingMapError(
                        f"Duplicate heading target key: {'/'.join(key)} "
                        f"{source_chapter}:{source_verse} -> {target_chapter}:{target_verse}"
                    )
                seen_targets.add(target_key)
                targets.append(HeadingTarget(target_chapter, target_verse, target_text))

            target_coordinates = [
                (target.chapter, target.before_verse) for target in targets
            ]
            if target_coordinates != sorted(target_coordinates):
                raise HeadingMapError(
                    f"Heading targets must be in ascending Scripture order: "
                    f"{'/'.join(key)} {source_chapter}:{source_verse}"
                )

            # Splits are lossless and order-preserving. Localized composite
            # headings use either a single space or a newline as their divider.
            if join_with.join(target.text for target in targets) != source_text:
                raise HeadingMapError(
                    f"Heading target fragments do not preserve source text: "
                    f"{'/'.join(key)} {source_chapter}:{source_verse}"
                )
            relocations.append(HeadingRelocation(
                source_chapter,
                source_verse,
                source_text,
                join_with,
                tuple(targets),
            ))

        result[key] = EditionHeadingMap(
            language,
            edition_id,
            book_code,
            collection,
            book_id,
            source_title,
            source_url,
            source_sha256,
            source_date,
            tuple(relocations),
        )
    return result
