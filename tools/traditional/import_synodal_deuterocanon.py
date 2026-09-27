"""Import exact Russian Synodal deuterocanonical overlays from CrossWire.

This module is intentionally separate from the 66-book traditional-edition
importer.  The official ``RusSynodal`` SWORD module uses Synodal versification,
and several additions are either integrated into canonical books or use verse
inventories that do not match the app.  Only mechanically exact mappings are
emitted.  Everything else remains an explicit fallback to the current edition.
"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
from html import unescape
import json
from pathlib import Path
import re
import sys
from typing import Any


ARCHIVE_SHA256 = "B802570E1783C326552B9E810786EFE3DF4EFCD615F28CCF3A86BAE27DBC5022"
ARCHIVE_URL = "https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/RusSynodal.zip"
MODULE_INFO_URL = "https://www.crosswire.org/sword/modules/ModInfo.jsp?modName=RusSynodal"
MODULE_KEY = "RusSynodal"
MODULE_VERSION = "1.9.1"
MODULE_LICENSE = "Public Domain"
MODULE_VERSIFICATION = "Synodal"
EDITION_ID = "synodal1876"
LANGUAGE = "ru"
SCHEMA_VERSION = 2


class SynodalImportError(RuntimeError):
    """Raised when the pinned source or an exact mapping cannot be proved."""


@dataclass(frozen=True)
class BookSource:
    module_book: str
    source_chapters: tuple[int, ...] | None = None
    display_chapters: tuple[int, ...] | None = None
    allow_canonical_psalm_title: bool = False


# Daniel 13 and 14 are whole, independently bounded works in the Synodal
# module.  Daniel 3 and Esther are deliberately absent because extracting them
# requires edition-specific internal splits rather than whole-chapter mapping.
SUPPORTED_SOURCES: dict[str, BookSource] = {
    "tobit": BookSource("Tobit"),
    "judith": BookSource("Judith"),
    "wisdom": BookSource("Wisdom"),
    "sirach": BookSource("Sirach"),
    "baruch": BookSource("Baruch"),
    "letter_of_jeremiah": BookSource("Epistle of Jeremiah"),
    "1_maccabees": BookSource("I Maccabees"),
    "2_maccabees": BookSource("II Maccabees"),
    "3_maccabees": BookSource("III Maccabees"),
    "1_esdras": BookSource("I Esdras"),
    "susanna": BookSource("Daniel", (13,), (1,)),
    "bel_and_the_dragon": BookSource("Daniel", (14,), (1,)),
    "psalm_151": BookSource("Psalms", (151,), (1,), True),
}


FALLBACK_REASONS = {
    "prayer_of_manasseh": (
        "RusSynodal has 12 source verses, while the app's Russian Prayer of "
        "Manasseh has 15 slots; no arbitrary verse splitting is permitted."
    ),
    "2_esdras": (
        "RusSynodal has 70 verses in 2 Esdras 7, while the app has 140; the "
        "source does not cover every app verse slot."
    ),
    "4_maccabees": "The official RusSynodal module does not contain 4 Maccabees.",
    "esther_greek": (
        "RusSynodal integrates Greek Esther material into Esther; the app's "
        "standalone chapter model needs an audited internal versification map."
    ),
    "song_of_three": (
        "RusSynodal integrates this material into Daniel 3; an audited internal "
        "versification map is required before extraction."
    ),
}


TRAILING_REFERENCE = re.compile(
    r"\(\s*(?:(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?|(\d+))\s*\)\s*\.?\s*$"
)
TITLE = re.compile(r"<title\b(?P<attrs>[^>]*)>(?P<text>.*?)</title>", re.I | re.S)
XML_TAG = re.compile(r"<[^>]+>")
SPACE = re.compile(r"\s+")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def _normalize_text(value: str) -> str:
    return SPACE.sub(" ", unescape(value)).strip()


def _write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _load_index(repo_root: Path) -> list[str]:
    path = repo_root / "shared/assets/books/deuterocanonical/ru/_index.json"
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise SynodalImportError(f"Russian deuterocanonical index is not a list: {path}")
    book_ids: list[str] = []
    for row in raw:
        if not isinstance(row, list) or len(row) < 2 or not isinstance(row[0], str):
            raise SynodalImportError(f"Malformed Russian deuterocanonical index row: {row!r}")
        if row[0]:
            book_ids.append(row[0])
    if len(book_ids) != len(set(book_ids)):
        raise SynodalImportError("Duplicate Russian deuterocanonical book ID")
    if set(SUPPORTED_SOURCES) - set(book_ids):
        raise SynodalImportError(
            "Supported Synodal books are missing from the Russian index: "
            f"{sorted(set(SUPPORTED_SOURCES) - set(book_ids))}"
        )
    return book_ids


def _base_units(repo_root: Path, book_id: str) -> dict[int, list[tuple[int, int]]]:
    path = repo_root / f"shared/assets/books/deuterocanonical/ru/{book_id}.json"
    raw = json.loads(path.read_text(encoding="utf-8"))
    stories = [
        story for story in raw.get("stories", [])
        if str(story.get("id", "")).rsplit("-", 1)[-1].isdigit()
    ]
    if not stories:
        raise SynodalImportError(f"No numeric chapter stories in {path}")
    allow_bare_verse = len(stories) == 1
    result: dict[int, list[tuple[int, int]]] = {}
    for story in stories:
        chapter = int(str(story["id"]).rsplit("-", 1)[-1])
        if chapter in result:
            raise SynodalImportError(f"Duplicate chapter {chapter} in {path}")
        units: list[tuple[int, int]] = []
        expected_start = 1
        for bullet in story.get("summaryBullets", []):
            if not isinstance(bullet, str):
                raise SynodalImportError(f"Non-text verse bullet in {path}, chapter {chapter}")
            match = TRAILING_REFERENCE.search(bullet)
            if match is None:
                raise SynodalImportError(f"Verse bullet has no trailing reference in {path}")
            if match.group(4) is not None:
                if not allow_bare_verse:
                    raise SynodalImportError(f"Bare verse reference in multi-chapter book {path}")
                start = end = int(match.group(4))
            else:
                marker_chapter = int(match.group(1))
                if marker_chapter != chapter:
                    raise SynodalImportError(
                        f"Chapter marker mismatch in {path}: {marker_chapter} != {chapter}"
                    )
                start = int(match.group(2))
                end = int(match.group(3) or start)
            if start != expected_start or end < start:
                raise SynodalImportError(
                    f"Non-contiguous app verse slots in {path}: expected {expected_start}, "
                    f"found {start}-{end}"
                )
            units.append((start, end))
            expected_start = end + 1
        if not units:
            raise SynodalImportError(f"No verse units in {path}, chapter {chapter}")
        result[chapter] = units
    return result


def _base_headings(repo_root: Path, book_id: str) -> dict[int, list[dict[str, Any]]]:
    """Load the localized editorial headings keyed to display chapters."""
    path = repo_root / f"shared/assets/books/deuterocanonical/ru/{book_id}.json"
    raw = json.loads(path.read_text(encoding="utf-8"))
    stories = [
        story for story in raw.get("stories", [])
        if str(story.get("id", "")).rsplit("-", 1)[-1].isdigit()
    ]
    if len(stories) == 1:
        return {1: [dict(row) for row in stories[0].get("headings", [])]}
    return {
        int(str(story["id"]).rsplit("-", 1)[-1]):
            [dict(row) for row in story.get("headings", [])]
        for story in stories
    }


def _validate_metadata(metadata: dict[str, Any]) -> dict[str, Any]:
    if MODULE_KEY not in metadata:
        raise SynodalImportError(f"SWORD module {MODULE_KEY!r} is absent")
    row = metadata[MODULE_KEY]
    required = {
        "version": MODULE_VERSION,
        "versification": MODULE_VERSIFICATION,
        "distributionlicense": MODULE_LICENSE,
    }
    for field, expected in required.items():
        actual = row.get(field)
        if actual != expected:
            raise SynodalImportError(
                f"RusSynodal {field} mismatch; expected {expected!r}, got {actual!r}"
            )
    return row


def _open_sword(archive: Path, pysword_path: Path | None) -> tuple[Any, dict[str, Any], dict[str, Any]]:
    if pysword_path is not None and str(pysword_path) not in sys.path:
        sys.path.insert(0, str(pysword_path))
    try:
        from pysword.modules import SwordModules  # type: ignore
    except ImportError as exc:
        raise SynodalImportError(
            "RusSynodal import requires pysword 0.2.8; pass its install directory"
        ) from exc

    modules = SwordModules(str(archive))
    metadata = modules.parse_modules()
    module_metadata = _validate_metadata(metadata)
    bible = modules.get_bible_from_module(MODULE_KEY)
    structure = bible.get_structure().get_books()
    books = {
        book.name: book
        for testament_books in structure.values()
        for book in testament_books
    }
    return bible, module_metadata, books


def _chapter_map(spec: BookSource, source_book: Any) -> list[tuple[int, int]]:
    if spec.source_chapters is None:
        source_chapters = tuple(range(1, len(source_book.chapter_lengths) + 1))
    else:
        source_chapters = spec.source_chapters
    display_chapters = spec.display_chapters or source_chapters
    if len(source_chapters) != len(display_chapters):
        raise SynodalImportError(f"Invalid chapter map for {spec.module_book}")
    if len(set(display_chapters)) != len(display_chapters):
        raise SynodalImportError(f"Duplicate display chapter in map for {spec.module_book}")
    return list(zip(source_chapters, display_chapters, strict=True))


def _source_title(raw_text: str, *, permitted: bool, source_reference: str) -> str | None:
    titles = list(TITLE.finditer(raw_text))
    if not titles:
        return None
    if len(titles) != 1 or not permitted:
        raise SynodalImportError(f"Unmapped source heading at {source_reference}")
    match = titles[0]
    attrs = match.group("attrs").lower()
    if 'canonical="true"' not in attrs or 'type="psalm"' not in attrs:
        raise SynodalImportError(f"Unapproved source heading type at {source_reference}")
    title = _normalize_text(XML_TAG.sub("", match.group("text")))
    if not title:
        raise SynodalImportError(f"Empty canonical psalm title at {source_reference}")
    return title


def _proof(archive: Path, source_book: Any, source_chapters: list[int]) -> dict[str, Any]:
    return {
        "sourceTitle": "1876 Russian Synodal Bible",
        "module": MODULE_KEY,
        "moduleVersion": MODULE_VERSION,
        "versification": MODULE_VERSIFICATION,
        "license": MODULE_LICENSE,
        "moduleInfoUrl": MODULE_INFO_URL,
        "archiveUrl": ARCHIVE_URL,
        "archiveFile": archive.name,
        "archiveSha256": ARCHIVE_SHA256,
        "sourceBook": source_book.name,
        "sourceOsis": source_book.osis_name,
        "sourceChapters": source_chapters,
    }


def _build_book(
    bible: Any,
    archive: Path,
    source_book: Any,
    spec: BookSource,
    repo_root: Path,
    output_dir: Path,
    book_id: str,
) -> dict[str, Any]:
    base = _base_units(repo_root, book_id)
    base_headings = _base_headings(repo_root, book_id)
    mapping = _chapter_map(spec, source_book)
    if set(base) != {display for _source, display in mapping}:
        raise SynodalImportError(
            f"{book_id}: source/display chapter coverage does not match app chapters"
        )

    chapters: list[dict[str, Any]] = []
    mapped_ranges: list[dict[str, str]] = []
    for source_chapter, display_chapter in mapping:
        if source_chapter < 1 or source_chapter > len(source_book.chapter_lengths):
            raise SynodalImportError(
                f"{book_id}: source chapter {source_chapter} is outside {source_book.name}"
            )
        verse_count = int(source_book.chapter_lengths[source_chapter - 1])
        expected_units = [(number, number) for number in range(1, verse_count + 1)]
        if base[display_chapter] != expected_units:
            raise SynodalImportError(
                f"{book_id} {display_chapter}: app/source verse slots differ; "
                "no verse splitting or renumbering is permitted"
            )

        verses: list[dict[str, Any]] = []
        superscription: str | None = None
        for verse_number in range(1, verse_count + 1):
            raw = str(bible.get(
                books=source_book.name,
                chapters=source_chapter,
                verses=verse_number,
                clean=False,
            ))
            title = _source_title(
                raw,
                permitted=(
                    spec.allow_canonical_psalm_title
                    and source_chapter == spec.source_chapters[0]
                    and display_chapter == 1
                    and verse_number == 1
                ),
                source_reference=f"{source_book.osis_name} {source_chapter}:{verse_number}",
            )
            if title is not None:
                if superscription is not None:
                    raise SynodalImportError(f"Multiple superscriptions in {book_id}")
                superscription = title
            text = _normalize_text(str(bible.get(
                books=source_book.name,
                chapters=source_chapter,
                verses=verse_number,
                clean=True,
            )))
            if not text:
                raise SynodalImportError(
                    f"Empty source verse at {source_book.osis_name} "
                    f"{source_chapter}:{verse_number}"
                )
            if XML_TAG.search(text) or "\\" in text:
                raise SynodalImportError(
                    f"Leaked source markup at {source_book.osis_name} "
                    f"{source_chapter}:{verse_number}"
                )
            if any(marker in text for marker in ("[J]", "[/J]", "[DN]", "[/DN]")):
                raise SynodalImportError(
                    f"Unexpected display marker in source at {source_book.osis_name} "
                    f"{source_chapter}:{verse_number}"
                )
            if title is not None and title in text:
                raise SynodalImportError(
                    f"Canonical title was not separated from verse text at "
                    f"{source_book.osis_name} {source_chapter}:{verse_number}"
                )
            verses.append({
                "chapter": display_chapter,
                "verse": verse_number,
                "text": text,
            })

        chapter = {
            "number": display_chapter,
            "lastVerse": verse_count,
            "verseUnitCount": verse_count,
            # Every alternate-edition chapter owns its heading lookup table.
            "headings": base_headings.get(display_chapter, []),
        }
        if superscription is not None:
            chapter["superscription"] = superscription
        chapter["verses"] = verses
        chapters.append(chapter)
        if source_chapter != display_chapter:
            mapped_ranges.append({
                "archiveReference": (
                    f"{source_book.osis_name} {source_chapter}:1-{verse_count}"
                ),
                "displayReference": f"{book_id} {display_chapter}:1-{verse_count}",
            })

    source_chapters = [source for source, _display in mapping]
    proof = _proof(archive, source_book, source_chapters)
    overlay: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "editionId": EDITION_ID,
        "language": LANGUAGE,
        "collection": "deuterocanonical",
        "bookId": book_id,
        "sourceBookCode": source_book.osis_name,
        "coverage": "full",
        "sourceProof": proof,
        "chapters": chapters,
    }
    if mapped_ranges:
        overlay["sourceMapping"] = mapped_ranges
    _write_json(output_dir / f"{book_id}.json", overlay)

    verse_units = sum(len(chapter["verses"]) for chapter in chapters)
    manifest: dict[str, Any] = {
        "collection": "deuterocanonical",
        "bookId": book_id,
        "sourceBookCode": source_book.osis_name,
        "sourceFile": archive.name,
        "sourceFileSha256": ARCHIVE_SHA256,
        "coverage": "full",
        "output": f"deuterocanonical/{book_id}.json",
        "chapters": len(chapters),
        "verseUnits": verse_units,
        "coveredVerseNumbers": verse_units,
        "jesusWordSpans": 0,
        "inheritedJesusVerseSpans": 0,
        "omittedMixedJesusVerseSpans": 0,
        "translatorAdditionSpans": 0,
        "superscriptions": sum("superscription" in chapter for chapter in chapters),
        "promotedFootnoteVerses": 0,
        "sourcePlaceholderVerses": 0,
        "sourceProof": proof,
    }
    if mapped_ranges:
        manifest["sourceMapping"] = mapped_ranges
    return manifest


def build_synodal_dc(
    archive: Path,
    repo_root: Path,
    output_dir: Path,
    pysword_path: Path | None,
) -> list[dict]:
    """Write proven RusSynodal DC overlays and return ordered manifest rows.

    ``output_dir`` is the destination ``deuterocanonical`` directory, normally
    inside the main importer's staging tree.  Supported sources fail closed on
    any archive, metadata, chapter, verse-slot, empty-text, or heading mismatch.
    Known non-exact mappings return explicit current-edition fallback rows.
    """
    archive = archive.resolve()
    repo_root = repo_root.resolve()
    output_dir = output_dir.resolve()
    if not archive.is_file():
        raise SynodalImportError(f"Missing RusSynodal archive: {archive}")
    actual_hash = _sha256(archive)
    if actual_hash != ARCHIVE_SHA256:
        raise SynodalImportError(
            f"RusSynodal archive SHA-256 mismatch; expected {ARCHIVE_SHA256}, "
            f"got {actual_hash}"
        )

    book_ids = _load_index(repo_root)
    bible, _metadata, source_books = _open_sword(archive, pysword_path)
    output_dir.mkdir(parents=True, exist_ok=True)
    rows: list[dict] = []
    for book_id in book_ids:
        spec = SUPPORTED_SOURCES.get(book_id)
        if spec is None:
            reason = FALLBACK_REASONS.get(book_id)
            if reason is None:
                raise SynodalImportError(
                    f"No RusSynodal import or reviewed fallback reason for {book_id}"
                )
            rows.append({
                "collection": "deuterocanonical",
                "bookId": book_id,
                "coverage": "fallback",
                "fallbackEditionId": "current",
                "reason": reason,
            })
            continue
        source_book = source_books.get(spec.module_book)
        if source_book is None:
            raise SynodalImportError(
                f"RusSynodal source book {spec.module_book!r} is absent"
            )
        rows.append(_build_book(
            bible,
            archive,
            source_book,
            spec,
            repo_root,
            output_dir,
            book_id,
        ))
    return rows
