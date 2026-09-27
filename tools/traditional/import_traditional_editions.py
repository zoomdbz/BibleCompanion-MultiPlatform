#!/usr/bin/env python3
"""Build deterministic traditional-language Scripture overlays.

The app's localized books remain the metadata layer. This importer writes a
second Scripture layer containing only source-backed verse text and Psalm
superscriptions. Summaries, takeaways, notes, cross-references, section-heading
text, and book introductions continue to come from the existing localized
books. Every alternate-edition chapter carries an explicit heading table. An
audited passage map moves existing localized heading text when the selected
edition places that passage at a different chapter or verse; empty lists are
written explicitly so runtime rendering never guesses or silently falls back.

USFM footnotes, cross-references, and Strong's metadata are excluded from the
display text. Translator-supplied words retain [ADD] markers. Native USFM
``wj`` markup takes precedence. Otherwise, full-verse speech comes from the
hash-pinned English KJV wj source; mixed verses require an edition-specific,
hash-bound reviewed span ledger before any narration can receive coloring.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from heading_maps import HeadingMapError, HeadingRelocation, load_heading_maps
from jesus_word_spans import JesusSpanError, ReviewedJesusSpans, verse_key
from reference_maps import reference_map_for_edition


SCHEMA_VERSION = 2
SOURCE_DATE = "2026-09-26"
HEADING_MAP_PATH = Path(__file__).with_name("edition_heading_maps.json")
REVIEWED_JESUS_EDITIONS = {
    ("de", "luther1912"),
    ("it", "diodati1885"),
    ("es", "rv1909"),
    ("pt", "almeida1911"),
    ("ru", "synodal1876"),
    ("ko", "korrv"),
    ("ja", "bungo"),
    ("ar", "van_dyck"),
    ("zh-Hans", "cuv"),
    ("zh-Hant", "cuv"),
}


@dataclass(frozen=True)
class EditionConfig:
    language: str
    edition_id: str
    display_name: str
    archive_name: str
    archive_sha256: str
    source_url: str
    source_title: str
    rights: str
    source_format: str = "usfm"
    module_key: str | None = None
    auxiliary_name: str | None = None
    auxiliary_sha256: str | None = None
    auxiliary_url: str | None = None
    auxiliary_title: str | None = None
    publisher: str | None = None
    rights_url: str | None = None


EDITIONS: tuple[EditionConfig, ...] = (
    EditionConfig(
        "de", "luther1912", "Lutherbibel 1912", "deu1912.zip",
        "650A8192134A8F0057286C469754EDCFAEE4FBB18800621AEE4563F3055BB39B",
        "https://ebible.org/Scriptures/deu1912_usfm.zip",
        "Lutherbibel 1912", "Public domain",
    ),
    EditionConfig(
        "es", "rv1909", "Reina-Valera 1909", "spaRV1909.zip",
        "B5BFAC87199A561FCBACB5E32BE5D8D280934B1C6830088D9EB8C68FFBFBE711",
        "https://ebible.org/Scriptures/spaRV1909_usfm.zip",
        "Santa Biblia - Reina-Valera 1909", "Public domain",
    ),
    EditionConfig(
        "fr", "lsg1910", "Louis Segond 1910", "fraLSG.zip",
        "3A0615E992FFD412B1AFCAED50D146BBA5EC8AE2378F04CA71459A4CD2D7CC33",
        "https://ebible.org/Scriptures/fraLSG_usfm.zip",
        "Bible Louis Segond 1910", "Public domain",
    ),
    EditionConfig(
        "it", "diodati1885", "Diodati 1885", "ita1885.zip",
        "459884735DF5D5AE7F3381980BC5EA846F0034CEA305FA77B5D72E09727E051C",
        "https://ebible.org/Scriptures/ita1885_usfm.zip",
        "Diodati Bibbia 1885", "Public domain",
    ),
    EditionConfig(
        "pt", "almeida1911", "Almeida 1911", "porAlmeida1911.zip",
        "C67FCF72F22A7916B695034F2DA48C10E38432463AA6C60F6CB48F992ACE5FDA",
        "https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/PorAlmeida1911.zip",
        "De 1911 Biblia Sagrada Traduzida em Portuguez por Joao Ferreira d'Almeida",
        "CrossWire module distributed under the GNU General Public License; underlying historical text is from the 1911 edition.",
        source_format="sword", module_key="PorAlmeida1911",
    ),
    EditionConfig(
        "ru", "synodal1876", "Russian Synodal Bible 1876", "russyn.zip",
        "ACD5D80C0D28CA72D17CB12A2BB9F561D957439B03BDDFAA433BEB51CF7B0363",
        "https://ebible.org/Scriptures/russyn_usfm.zip",
        "Russian Synodal Bible 1876", "Public domain",
    ),
    EditionConfig(
        "ja", "bungo", "Japanese Bungo Bible", "japBungo.zip",
        "1ACC5048206BA75ADE77B3CB146568809A28151A53CE314CFC721D080ACA75E6",
        "https://www.crosswire.org/ftpmirror/pub/sword/packages/rawzip/JapBungo.zip",
        "Meiji Old Testament (1953 base text) and Taisho Revised New Testament",
        "Public domain", source_format="sword", module_key="JapBungo",
    ),
    EditionConfig(
        "ko", "korrv", "Korean Revised Version 1961", "korean-krv-1961.json",
        "65C6D99F80A5A47B9C0D144F313AC533F482454F336D3A82FC1DCEDB020612E2",
        "https://raw.githubusercontent.com/bluesaurel/Korean-Bible-1961-KRV/2fdb91e77d323b99bacaa9b61fc18f318b703240/bible_1961_krv.json",
        "Korean Revised Version 1961 (KRV)",
        "Economic rights expired on 2011-12-31 according to the Korean Bible Society. Attribution and integrity rights remain; text is attributed to the Korean Bible Society and is not paraphrased.",
        source_format="krv-json",
        auxiliary_name="korean-krv-holybible.jsonl",
        auxiliary_sha256="FD40780326E24EFE9E16EFB775DB39DDF6F5D0980A97344DE618533D5A6663C1",
        auxiliary_url="https://raw.githubusercontent.com/crizin/bible-db/4bcb50b3ead59d20b4a6b847f5c79ab4cdba6a2c/data/krv/krv_holybible.jsonl",
        auxiliary_title="KRV 1961 HolyBible transcription and native merged-verse markers",
        publisher="Korean Bible Society",
        rights_url="https://www.bskorea.or.kr/bbs/content.php?co_id=subpage2_3_4_1",
    ),
    EditionConfig(
        "zh-Hans", "cuv", "Chinese Union Version (Simplified)", "cmn-cu89s.zip",
        "68DF122E9195E071DC286F19EF53E530FCAADB3A16A7DC34B8430B7062F70598",
        "https://ebible.org/Scriptures/cmn-cu89s_usfm.zip",
        "Chinese Union Version, New Punctuation, simplified script", "Public domain",
    ),
    EditionConfig(
        "zh-Hant", "cuv", "Chinese Union Version (Traditional)", "cmn-cu89t.zip",
        "01E919EC0F2EA9E22ADAA5097340FC4AD18A7976A32F850B2E6434D7884F3E81",
        "https://ebible.org/Scriptures/cmn-cu89t_usfm.zip",
        "Chinese Union Version, New Punctuation, traditional script", "Public domain",
    ),
    EditionConfig(
        "ar", "van_dyck", "Arabic Van Dyck Bible", "arb-vd.zip",
        "E4A2AB9491B2AC2FF799BB2A80EC9322203A7C36E78210B4506DF84308C54948",
        "https://ebible.org/Scriptures/arb-vd_usfm.zip",
        "Arabic Van Dyck Bible", "Public domain",
    ),
)


# USFM code, collection, app book id. Order also matches the KJV SWORD canon.
BOOKS: tuple[tuple[str, str, str], ...] = (
    ("GEN", "old_testament", "genesis"),
    ("EXO", "old_testament", "exodus"),
    ("LEV", "old_testament", "leviticus"),
    ("NUM", "old_testament", "numbers"),
    ("DEU", "old_testament", "deuteronomy"),
    ("JOS", "old_testament", "joshua"),
    ("JDG", "old_testament", "judges"),
    ("RUT", "old_testament", "ruth"),
    ("1SA", "old_testament", "1_samuel"),
    ("2SA", "old_testament", "2_samuel"),
    ("1KI", "old_testament", "1_kings"),
    ("2KI", "old_testament", "2_kings"),
    ("1CH", "old_testament", "1_chronicles"),
    ("2CH", "old_testament", "2_chronicles"),
    ("EZR", "old_testament", "ezra"),
    ("NEH", "old_testament", "nehemiah"),
    ("EST", "old_testament", "esther"),
    ("JOB", "old_testament", "job"),
    ("PSA", "old_testament", "psalms"),
    ("PRO", "old_testament", "proverbs"),
    ("ECC", "old_testament", "ecclesiastes"),
    ("SNG", "old_testament", "song_of_songs"),
    ("ISA", "old_testament", "isaiah"),
    ("JER", "old_testament", "jeremiah"),
    ("LAM", "old_testament", "lamentations"),
    ("EZK", "old_testament", "ezekiel"),
    ("DAN", "old_testament", "daniel"),
    ("HOS", "old_testament", "hosea"),
    ("JOL", "old_testament", "joel"),
    ("AMO", "old_testament", "amos"),
    ("OBA", "old_testament", "obadiah"),
    ("JON", "old_testament", "jonah"),
    ("MIC", "old_testament", "micah"),
    ("NAM", "old_testament", "nahum"),
    ("HAB", "old_testament", "habakkuk"),
    ("ZEP", "old_testament", "zephaniah"),
    ("HAG", "old_testament", "haggai"),
    ("ZEC", "old_testament", "zechariah"),
    ("MAL", "old_testament", "malachi"),
    ("MAT", "new_testament", "matthew"),
    ("MRK", "new_testament", "mark"),
    ("LUK", "new_testament", "luke"),
    ("JHN", "new_testament", "john"),
    ("ACT", "new_testament", "acts"),
    ("ROM", "new_testament", "romans"),
    ("1CO", "new_testament", "1_corinthians"),
    ("2CO", "new_testament", "2_corinthians"),
    ("GAL", "new_testament", "galatians"),
    ("EPH", "new_testament", "ephesians"),
    ("PHP", "new_testament", "philippians"),
    ("COL", "new_testament", "colossians"),
    ("1TH", "new_testament", "1_thessalonians"),
    ("2TH", "new_testament", "2_thessalonians"),
    ("1TI", "new_testament", "1_timothy"),
    ("2TI", "new_testament", "2_timothy"),
    ("TIT", "new_testament", "titus"),
    ("PHM", "new_testament", "philemon"),
    ("HEB", "new_testament", "hebrews"),
    ("JAS", "new_testament", "james"),
    ("1PE", "new_testament", "1_peter"),
    ("2PE", "new_testament", "2_peter"),
    ("1JN", "new_testament", "1_john"),
    ("2JN", "new_testament", "2_john"),
    ("3JN", "new_testament", "3_john"),
    ("JUD", "new_testament", "jude"),
    ("REV", "new_testament", "revelation"),
)


@dataclass
class SourceVerse:
    chapter: int
    verse: int
    verse_end: int
    parts: list[str] = field(default_factory=list)
    footnotes: int = 0
    source_jesus_spans: int = 0
    addition_spans: int = 0
    promoted_from_footnote: bool = False
    source_placeholder: bool = False

    @property
    def text(self) -> str:
        return normalize_space(" ".join(self.parts))


@dataclass
class SourceChapter:
    number: int
    verses: list[SourceVerse] = field(default_factory=list)
    superscription_parts: list[str] = field(default_factory=list)

    @property
    def superscription(self) -> str:
        return normalize_space(" ".join(self.superscription_parts))


@dataclass
class SourceBook:
    code: str
    source_name: str
    source_sha256: str
    chapters: list[SourceChapter]
    source_mappings: list[dict] = field(default_factory=list)


class ImportErrorDetail(ValueError):
    pass


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace("\u00a0", " ")).strip()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def source_code(text: str, path: Path) -> str:
    match = re.search(r"(?m)^\\id\s+(\S+)", text)
    if not match:
        raise ImportErrorDetail(f"Missing USFM id marker: {path}")
    return match.group(1)


def marker_at(text: str, offset: int) -> tuple[str, bool, int] | None:
    if offset >= len(text) or text[offset] != "\\":
        return None
    match = re.match(r"\\(\+?[A-Za-z0-9-]+)(\*)?", text[offset:])
    if not match:
        return None
    return match.group(1), bool(match.group(2)), offset + match.end()


def find_close(text: str, offset: int, marker: str) -> tuple[int, int]:
    candidates = ["\\" + marker + "*"]
    if marker.startswith("+"):
        candidates.append("\\" + marker[1:] + "*")
    found = [(text.find(token, offset), token) for token in candidates]
    found = [(index, token) for index, token in found if index >= 0]
    if not found:
        raise ImportErrorDetail(f"Unclosed inline marker {marker!r}: {text[offset:offset + 120]!r}")
    index, token = min(found, key=lambda item: item[0])
    return index, index + len(token)


def parse_inline(raw: str) -> tuple[str, int, int, int]:
    """Return visible text plus footnote, Jesus-span, and addition counts."""
    out: list[str] = []
    footnotes = 0
    jesus_spans = 0
    addition_spans = 0
    i = 0
    while i < len(raw):
        parsed = marker_at(raw, i)
        if parsed is None:
            out.append(raw[i])
            i += 1
            continue
        marker, closing, after = parsed
        base = marker.removeprefix("+")
        if closing:
            i = after
            continue

        if base in {"f", "fe", "ef", "x", "ex"}:
            _end, i = find_close(raw, after, marker)
            footnotes += 1 if base in {"f", "fe", "ef"} else 0
            continue

        if base == "w":
            end, i = find_close(raw, after, marker)
            content = raw[after:end].lstrip()
            visible = content.partition("|")[0]
            nested, n_foot, n_jesus, n_add = parse_inline(visible)
            out.append(nested)
            footnotes += n_foot
            jesus_spans += n_jesus
            addition_spans += n_add
            continue

        if base in {"add", "wj"}:
            end, i = find_close(raw, after, marker)
            content = raw[after:end].lstrip()
            nested, n_foot, n_jesus, n_add = parse_inline(content)
            if base == "add":
                out.extend(("[ADD]", nested, "[/ADD]"))
                addition_spans += 1
            else:
                out.extend(("[J]", nested, "[/J]"))
                jesus_spans += 1
            footnotes += n_foot
            jesus_spans += n_jesus
            addition_spans += n_add
            continue

        if base in {
            "pn", "qs", "it", "ior", "ord", "qt", "k", "em", "bd", "bdit",
            "sc", "sup", "nd", "sig", "tl", "no", "pro", "rb", "wa", "fv",
        }:
            end, i = find_close(raw, after, marker)
            nested, n_foot, n_jesus, n_add = parse_inline(raw[after:end].lstrip())
            out.append(nested)
            footnotes += n_foot
            jesus_spans += n_jesus
            addition_spans += n_add
            continue

        raise ImportErrorDetail(f"Unsupported inline marker {marker!r} in {raw!r}")

    return normalize_space("".join(out)), footnotes, jesus_spans, addition_spans


CONTINUATION_MARKER = re.compile(r"^(?:q\d*|qm\d*|p|m|mi|pi\d*|nb|pc|pr|cls|li\d*)$")


def parse_verse_label(label: str, path: Path, line_number: int) -> tuple[int, int]:
    match = re.fullmatch(r"(\d+)(?:-(\d+))?", label)
    if not match:
        raise ImportErrorDetail(f"Unsupported verse label {label!r}: {path}:{line_number}")
    start = int(match.group(1))
    end = int(match.group(2) or start)
    if start < 1 or end < start:
        raise ImportErrorDetail(f"Invalid verse label {label!r}: {path}:{line_number}")
    return start, end


def append_inline(target: SourceVerse | SourceChapter, raw: str, superscription: bool = False) -> None:
    visible, footnotes, jesus, additions = parse_inline(raw)
    if not visible:
        return
    if superscription:
        assert isinstance(target, SourceChapter)
        target.superscription_parts.append(visible)
    else:
        assert isinstance(target, SourceVerse)
        target.parts.append(visible)
        target.footnotes += footnotes
        target.source_jesus_spans += jesus
        target.addition_spans += additions


FOOTNOTE_BLOCK = re.compile(r"\\f\s+.*?\\f\*", re.DOTALL)
FOOTNOTE_VERSE = re.compile(r"\\\+?fv\s+(\d+)\s*\\\+?fv\*")
FOOTNOTE_VERSE_MALFORMED = re.compile(r"\\\+?fv\s+\\ft\s+(\d+)\s+\\ft\s*")


def promoted_footnote_verses(raw: str, chapter: int) -> tuple[str, list[SourceVerse]]:
    """Extract explicitly numbered omitted verses from source footnotes.

    The Chinese Union Version keeps eleven traditional New Testament verses in
    its own USFM apparatus. The user explicitly requires those numbered verses
    in the reader, so this promotes only footnotes carrying an ``fv`` number.
    Ordinary notes remain excluded.
    """
    prefix_addition = ""
    promoted: list[SourceVerse] = []
    for block_match in FOOTNOTE_BLOCK.finditer(raw):
        block = block_match.group(0)
        verse_match = FOOTNOTE_VERSE.search(block)
        if verse_match is None:
            # The traditional-script CUV encodes Acts 28:29 as
            # ``\\fv \\ft 29 \\ft`` instead of a closed ``fv`` field. The
            # surrounding note and its numbered verse are otherwise identical
            # to the simplified-script source.
            verse_match = FOOTNOTE_VERSE_MALFORMED.search(block)
        if verse_match is None:
            continue
        verse_number = int(verse_match.group(1))
        before = block[:verse_match.start()]
        after = block[verse_match.end():]

        # Remove apparatus field markers. For Acts 24:6 the source footnote
        # also carries the close of verse 6 before its explicit verse 7 marker.
        before = re.sub(r"\\(?:f|fr|ft)\s+(?:-|\d+[:.]\d+)?\s*", "", before)
        before = re.sub(r"^.*?有古卷加[：:]\s*", "", before, count=1)
        before_visible, _notes, _jesus, _adds = parse_inline(before)
        if before_visible:
            prefix_addition = normalize_space(f"{prefix_addition} {before_visible}")

        after = re.sub(r"\\ft\s*", "", after)
        after = after.removesuffix("\\f*")
        visible, _notes, jesus, additions = parse_inline(after)
        if not visible:
            raise ImportErrorDetail(
                f"Footnote verse {chapter}:{verse_number} has no visible text"
            )
        promoted.append(SourceVerse(
            chapter=chapter,
            verse=verse_number,
            verse_end=verse_number,
            parts=[visible],
            source_jesus_spans=jesus,
            addition_spans=additions,
            promoted_from_footnote=True,
        ))
    return prefix_addition, promoted


def parse_usfm_bytes(raw: bytes, path: Path) -> SourceBook:
    raw_text = raw.decode("utf-8-sig")
    code = source_code(raw_text, path)
    chapters: list[SourceChapter] = []
    current_chapter: SourceChapter | None = None
    current_verse: SourceVerse | None = None

    for line_number, original in enumerate(raw_text.splitlines(), start=1):
        line = original.rstrip()
        chapter_match = re.match(r"^\\c\s+(\d+)\s*$", line)
        if chapter_match:
            current_chapter = SourceChapter(int(chapter_match.group(1)))
            chapters.append(current_chapter)
            current_verse = None
            continue

        verse_match = re.match(r"^\\v\s+(\S+)\s+(.*)$", line)
        if verse_match:
            if current_chapter is None:
                raise ImportErrorDetail(f"Verse before chapter: {path}:{line_number}")
            start, end = parse_verse_label(verse_match.group(1), path, line_number)
            current_verse = SourceVerse(current_chapter.number, start, end)
            append_inline(current_verse, verse_match.group(2))
            prefix_addition, promoted = promoted_footnote_verses(
                verse_match.group(2), current_chapter.number
            )
            if prefix_addition:
                current_verse.parts.append(prefix_addition)
            if not current_verse.text:
                raise ImportErrorDetail(f"Empty verse: {path}:{line_number}")
            current_chapter.verses.append(current_verse)
            current_chapter.verses.extend(promoted)
            continue

        marker_match = re.match(r"^\\([A-Za-z0-9]+)\s*(.*)$", line)
        if marker_match:
            marker, body = marker_match.groups()
            if marker == "d" and current_chapter is not None and body:
                append_inline(current_chapter, body, superscription=True)
            elif current_verse is not None and body and CONTINUATION_MARKER.fullmatch(marker):
                append_inline(current_verse, body)
            continue

        if current_verse is not None and line.strip():
            append_inline(current_verse, line)

    if not chapters:
        raise ImportErrorDetail(f"No chapters parsed: {path}")
    validate_book_coverage(code, chapters)
    return SourceBook(code, path.name, hashlib.sha256(raw).hexdigest().upper(), chapters)


def validate_book_coverage(code: str, chapters: list[SourceChapter]) -> None:
    expected_chapter = 1
    for chapter in chapters:
        if chapter.number != expected_chapter:
            raise ImportErrorDetail(
                f"{code}: expected chapter {expected_chapter}, found {chapter.number}"
            )
        expected_verse = 1
        for verse in chapter.verses:
            if verse.verse != expected_verse:
                raise ImportErrorDetail(
                    f"{code} {chapter.number}: expected verse {expected_verse}, "
                    f"found {verse.verse}-{verse.verse_end}"
                )
            if not verse.text:
                raise ImportErrorDetail(f"{code} {chapter.number}:{verse.verse} is empty")
            expected_verse = verse.verse_end + 1
        if expected_verse == 1:
            raise ImportErrorDetail(f"{code} {chapter.number} contains no verses")
        expected_chapter += 1


def parse_usfm_archive(archive: Path) -> tuple[dict[str, SourceBook], str]:
    # Read the bytes from the verified archive itself. An extracted cache can
    # be stale or edited even when the archive still matches its pinned hash.
    result: dict[str, SourceBook] = {}
    with zipfile.ZipFile(archive) as source_zip:
        for entry in sorted(source_zip.infolist(), key=lambda item: item.filename):
            if entry.is_dir() or not entry.filename.lower().endswith(".usfm"):
                continue
            raw = source_zip.read(entry)
            path = Path(entry.filename)
            code = source_code(raw.decode("utf-8-sig"), path)
            if code == "FRT":
                continue
            if code in result:
                raise ImportErrorDetail(f"Duplicate USFM book code {code}")
            result[code] = parse_usfm_bytes(raw, path)
    expected = {code for code, _collection, _book_id in BOOKS}
    if set(result) != expected:
        raise ImportErrorDetail(
            f"USFM inventory mismatch; missing={sorted(expected - set(result))}, "
            f"unexpected={sorted(set(result) - expected)}"
        )
    file_set = hashlib.sha256()
    for code in sorted(result):
        file_set.update(code.encode("ascii"))
        file_set.update(b"\0")
        file_set.update(bytes.fromhex(result[code].source_sha256))
    return result, file_set.hexdigest().upper()


KRV_BOOK_NAMES: tuple[str, ...] = (
    "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
    "Joshua", "Judges", "Ruth", "1Samuel", "2Samuel", "1Kings",
    "2Kings", "1Chronicles", "2Chronicles", "Ezra", "Nehemiah",
    "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes",
    "SongofSolomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel",
    "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah",
    "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
    "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans",
    "1Corinthians", "2Corinthians", "Galatians", "Ephesians",
    "Philippians", "Colossians", "1Thessalonians", "2Thessalonians",
    "1Timothy", "2Timothy", "Titus", "Philemon", "Hebrews", "James",
    "1Peter", "2Peter", "1John", "2John", "3John", "Jude",
    "Revelation",
)

KRV_MERGED_MARKER = re.compile(r"\((\d+)절에 포함되어 있음\)")
KRV_OMITTED_MARKER = "(없음)"


def parse_krv_json(
    config: EditionConfig,
    source_path: Path,
    auxiliary_path: Path,
) -> dict[str, SourceBook]:
    """Parse the 1961 KRV while preserving its native verse markers.

    The primary reviewed transcription supplies the wording. The independently
    pinned HolyBible transcription supplies only exact ``included in verse``
    and ``missing`` markers. This avoids both pysword's alternate-versification
    shifts and the primary dataset's duplicated text in merged verse slots.
    """
    raw_books = json.loads(source_path.read_text(encoding="utf-8"))
    if not isinstance(raw_books, list):
        raise ImportErrorDetail(f"KRV source is not a book list: {source_path}")
    by_name: dict[str, dict] = {}
    for book in raw_books:
        if not isinstance(book, dict) or not isinstance(book.get("book"), str):
            raise ImportErrorDetail(f"Malformed KRV book entry: {source_path}")
        if book["book"] in by_name:
            raise ImportErrorDetail(f"Duplicate KRV book {book['book']!r}")
        by_name[book["book"]] = book
    if set(by_name) != set(KRV_BOOK_NAMES):
        raise ImportErrorDetail(
            f"KRV book inventory mismatch; missing={sorted(set(KRV_BOOK_NAMES) - set(by_name))}, "
            f"unexpected={sorted(set(by_name) - set(KRV_BOOK_NAMES))}"
        )

    auxiliary_rows: list[dict] = []
    for line_number, line in enumerate(auxiliary_path.read_text(encoding="utf-8").splitlines(), start=1):
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ImportErrorDetail(
                f"Malformed KRV auxiliary JSONL at {auxiliary_path}:{line_number}"
            ) from exc
        auxiliary_rows.append(row)
    auxiliary_markers: dict[tuple[str, int, int], str] = {}
    for row in auxiliary_rows:
        text = row.get("text")
        if text == KRV_OMITTED_MARKER or (
            isinstance(text, str) and KRV_MERGED_MARKER.fullmatch(text)
        ):
            key = (str(row.get("code")), int(row.get("chapter")), int(row.get("verse")))
            auxiliary_markers[key] = text
    if len(auxiliary_markers) != 34:
        raise ImportErrorDetail(
            f"Expected 34 native KRV auxiliary markers, found {len(auxiliary_markers)}"
        )

    source_hash = sha256(source_path)
    result: dict[str, SourceBook] = {}
    raw_total = 0
    for ((code, _collection, _book_id), source_name) in zip(
        BOOKS, KRV_BOOK_NAMES, strict=True
    ):
        raw_book = by_name[source_name]
        raw_chapters = raw_book.get("chapters")
        if not isinstance(raw_chapters, list):
            raise ImportErrorDetail(f"KRV {source_name} has no chapter list")
        chapters: list[SourceChapter] = []
        mappings: list[dict] = []
        for raw_chapter in raw_chapters:
            chapter_number = int(raw_chapter.get("chapter"))
            raw_verses = raw_chapter.get("verses")
            if not isinstance(raw_verses, list):
                raise ImportErrorDetail(
                    f"KRV {source_name} {chapter_number} has no verse list"
                )
            primary_by_verse = {
                int(row["verse"]): normalize_space(str(row["text"]))
                for row in raw_verses
            }
            verses: list[SourceVerse] = []
            for row in raw_verses:
                verse_number = int(row["verse"])
                raw_total += 1
                key = (code, chapter_number, verse_number)
                text = normalize_space(str(row["text"]))
                placeholder = False
                if key in auxiliary_markers:
                    text = auxiliary_markers[key]
                    placeholder = True
                elif key == ("PSA", 72, 19):
                    closing = primary_by_verse.get(20)
                    if not closing:
                        raise ImportErrorDetail("KRV Psalm 72:20 closing line is missing")
                    text = normalize_space(f"{text} {closing}")
                elif key == ("PSA", 72, 20):
                    text = "(19절에 포함되어 있음)"
                    placeholder = True
                if not text:
                    raise ImportErrorDetail(
                        f"KRV {code} {chapter_number}:{verse_number} is empty"
                    )
                verses.append(SourceVerse(
                    chapter=chapter_number,
                    verse=verse_number,
                    verse_end=verse_number,
                    parts=[text],
                    source_placeholder=placeholder,
                ))
                if placeholder:
                    mappings.append({
                        "chapter": chapter_number,
                        "sourceVerse": verse_number,
                        "targetRange": str(verse_number),
                        "kind": "editionPlaceholder",
                        "text": text,
                        "reason": (
                            "Preserves the 1961 KRV native omitted- or merged-verse marker "
                            "instead of duplicating or inventing Scripture text."
                        ),
                    })
            chapters.append(SourceChapter(chapter_number, verses))
        validate_book_coverage(code, chapters)
        result[code] = SourceBook(
            code=code,
            source_name=source_path.name,
            source_sha256=source_hash,
            chapters=chapters,
            source_mappings=mappings,
        )
    if raw_total != 31_102:
        raise ImportErrorDetail(f"Expected 31,102 KRV verse records, found {raw_total}")
    return result


SWORD_VERSE_MAPS: dict[tuple[str, str, int], dict[int, tuple[int, int]]] = {
    # JapBungo follows source-native merged verse units at these locations.
    ("JapBungo", "EXO", 7): {24: (24, 25)},
    ("JapBungo", "2SA", 19): {26: (25, 26)},
    ("JapBungo", "2CH", 2): {12: (12, 13)},

    # Portuguese Almeida 1911 numbers the closing verses of 2 Corinthians 13
    # as 12-13. The app uses the familiar 14-verse numbering.
    ("PorAlmeida1911", "2CO", 13): {
        12: (12, 13),
        13: (14, 14),
    },

}

SWORD_PLACEHOLDERS: dict[tuple[str, str, int], set[int]] = {
}

# Two JapBungo entries contain a literal transcription slash immediately before
# a CJK character. The same artifact is visible in multiple downstream digital
# copies, while the printed words are simply 牖 (window) and 圯 (collapsed).
# Match the complete artifact exactly so no genuine punctuation is normalized.
SWORD_TEXT_CORRECTIONS: dict[tuple[str, str, int, int], tuple[str, str]] = {
    ("JapBungo", "GEN", 26, 8): ("\\牖", "牖"),
    ("JapBungo", "AMO", 9, 11): ("\\圯", "圯"),
}


def mapped_sword_chapter(
    module_key: str,
    code: str,
    chapter_number: int,
    raw_verses: list[tuple[int, str]],
) -> tuple[SourceChapter, list[dict]]:
    verse_map = SWORD_VERSE_MAPS.get((module_key, code, chapter_number), {})
    placeholders = SWORD_PLACEHOLDERS.get((module_key, code, chapter_number), set())
    covered_blank_numbers = {
        number
        for source_number, (target_start, target_end) in verse_map.items()
        for number in range(target_start, target_end + 1)
        if number != source_number
    }
    chapter = SourceChapter(chapter_number)
    mappings: list[dict] = []
    for source_number, text in raw_verses:
        if text:
            correction = SWORD_TEXT_CORRECTIONS.get(
                (module_key, code, chapter_number, source_number)
            )
            if correction is not None:
                original, replacement = correction
                if text.count(original) != 1:
                    raise ImportErrorDetail(
                        f"Expected one {original!r} transcription artifact at "
                        f"{module_key} {code} {chapter_number}:{source_number}"
                    )
                text = text.replace(original, replacement)
                mappings.append({
                    "chapter": chapter_number,
                    "sourceVerse": source_number,
                    "targetRange": str(source_number),
                    "kind": "sourceTranscriptionCorrection",
                    "original": original,
                    "replacement": replacement,
                    "reason": "Removed a stray digital transcription slash without changing the source word.",
                })
            target_start, target_end = verse_map.get(
                source_number, (source_number, source_number)
            )
            chapter.verses.append(SourceVerse(
                chapter=chapter_number,
                verse=target_start,
                verse_end=target_end,
                parts=[text],
            ))
            if (target_start, target_end) != (source_number, source_number):
                mappings.append({
                    "chapter": chapter_number,
                    "sourceVerse": source_number,
                    "targetRange": (
                        f"{target_start}-{target_end}"
                        if target_start != target_end else str(target_start)
                    ),
                    "kind": "sourceNativeVerseMapping",
                })
            continue

        if source_number in placeholders:
            chapter.verses.append(SourceVerse(
                chapter=chapter_number,
                verse=source_number,
                verse_end=source_number,
                parts=["(없음)"],
                source_placeholder=True,
            ))
            mappings.append({
                "chapter": chapter_number,
                "sourceVerse": source_number,
                "targetRange": str(source_number),
                "kind": "editionPlaceholder",
                "text": "(없음)",
                "verification": "Bible.com KRV prints this numbered verse as (없음).",
            })
            continue

        if source_number in covered_blank_numbers:
            continue

        raise ImportErrorDetail(
            f"{module_key} {code} {chapter_number}:{source_number} is empty "
            "without an explicit source mapping or edition placeholder"
        )

    return chapter, mappings


def parse_sword(config: EditionConfig, archive: Path, pysword_path: Path | None) -> dict[str, SourceBook]:
    if pysword_path is not None:
        sys.path.insert(0, str(pysword_path))
    try:
        from pysword.modules import SwordModules  # type: ignore
    except ImportError as exc:
        raise ImportErrorDetail(
            "SWORD sources require pysword 0.2.8; pass --pysword-path to its install directory"
        ) from exc

    modules = SwordModules(str(archive))
    metadata = modules.parse_modules()
    if config.module_key not in metadata:
        raise ImportErrorDetail(f"SWORD module {config.module_key!r} is absent from {archive}")
    bible = modules.get_bible_from_module(config.module_key)
    structure = bible.get_structure().get_books()
    sword_books = list(structure.get("ot", [])) + list(structure.get("nt", []))
    if len(sword_books) != len(BOOKS):
        raise ImportErrorDetail(f"Expected 66 SWORD books, found {len(sword_books)}")

    result: dict[str, SourceBook] = {}
    for (code, _collection, _book_id), sword_book in zip(BOOKS, sword_books, strict=True):
        chapters: list[SourceChapter] = []
        source_mappings: list[dict] = []
        for chapter_number, verse_count in enumerate(sword_book.chapter_lengths, start=1):
            raw_verses: list[tuple[int, str]] = []
            for verse_number in range(1, verse_count + 1):
                text = normalize_space(
                    bible.get(
                        books=sword_book.name,
                        chapters=chapter_number,
                        verses=verse_number,
                        clean=True,
                    )
                )
                raw_verses.append((verse_number, text))
            chapter, chapter_mappings = mapped_sword_chapter(
                config.module_key or "", code, chapter_number, raw_verses
            )
            chapters.append(chapter)
            source_mappings.extend(chapter_mappings)
        validate_book_coverage(code, chapters)
        result[code] = SourceBook(
            code, archive.name, sha256(archive), chapters, source_mappings
        )
    return result


def clone_verse(verse: SourceVerse, chapter: int, number: int) -> SourceVerse:
    return SourceVerse(
        chapter=chapter,
        verse=number,
        verse_end=number + (verse.verse_end - verse.verse),
        parts=list(verse.parts),
        footnotes=verse.footnotes,
        source_jesus_spans=verse.source_jesus_spans,
        addition_spans=verse.addition_spans,
        promoted_from_footnote=verse.promoted_from_footnote,
        source_placeholder=verse.source_placeholder,
    )


NATIVE_REFERENCE_MARKER = re.compile(r"\[(?:(\d+):(\d+)|(\d+[a-z]))\]")
TRAILING_VERSE = re.compile(
    r"\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\s*\.?\s*$"
)


def base_verse_units(
    repo_root: Path,
    language: str,
    collection: str,
    book_id: str,
) -> list[tuple[int, int, int]]:
    """Return ordered verse units from the locale's current reader structure."""
    path = (
        repo_root / "shared" / "assets" / "books" / collection /
        language / f"{book_id}.json"
    )
    raw = json.loads(path.read_text(encoding="utf-8"))
    units: list[tuple[int, int, int]] = []
    for story in raw.get("stories", []):
        suffix = story.get("id", "").rsplit("-", 1)[-1]
        if not suffix.isdigit():
            continue
        chapter = int(suffix)
        for bullet in story.get("summaryBullets", []):
            match = TRAILING_VERSE.search(bullet)
            if match is None:
                raise ImportErrorDetail(f"Verse bullet has no trailing reference: {path}")
            marker_chapter = int(match.group(1))
            start = int(match.group(2))
            end = int(match.group(3) or start)
            if marker_chapter != chapter:
                raise ImportErrorDetail(
                    f"Chapter marker mismatch in {path}: story {chapter}, "
                    f"bullet {marker_chapter}:{start}-{end}"
                )
            units.append((chapter, start, end))
    if not units:
        raise ImportErrorDetail(f"No base verse units: {path}")
    return units


# Luther 1912 combines the two clauses that the modern German base exposes as
# 1 Chronicles 12:4 and 12:5. Preserve the source sentence as one 12:4-5 range.
# This is the only source/base text-unit count difference after splitting the
# two source-supplied mid-verse alternate-reference markers.
GERMAN_SOURCE_UNIT_WIDTHS = {
    ("1CH", 12, 4, 0): 2,
}

# These editions have source-native combined/split verse units that should stay
# intact. Their chapter inventories already work in the reader; only Isaiah's
# cross-chapter heading needs the reviewed relocation declared below.
GERMAN_KEEP_SOURCE_VERSIFICATION = {"PSA", "ISA", "2CO", "3JN", "REV"}


def strip_native_reference_markers(source_book: SourceBook) -> SourceBook:
    chapters: list[SourceChapter] = []
    mappings = list(source_book.source_mappings)
    for chapter in source_book.chapters:
        verses: list[SourceVerse] = []
        for verse in chapter.verses:
            markers = list(NATIVE_REFERENCE_MARKER.finditer(verse.text))
            cleaned = normalize_space(NATIVE_REFERENCE_MARKER.sub("", verse.text))
            if not cleaned:
                raise ImportErrorDetail(
                    f"Empty text after reference cleanup in {source_book.code} "
                    f"{chapter.number}:{verse.verse}"
                )
            verses.append(SourceVerse(
                chapter=chapter.number,
                verse=verse.verse,
                verse_end=verse.verse_end,
                parts=[cleaned],
                footnotes=verse.footnotes,
                source_jesus_spans=cleaned.count("[J]"),
                addition_spans=cleaned.count("[ADD]"),
                promoted_from_footnote=verse.promoted_from_footnote,
                source_placeholder=verse.source_placeholder,
            ))
            for marker in markers:
                historical = (
                    f"{marker.group(1)}:{marker.group(2)}"
                    if marker.group(1) is not None
                    else str(marker.group(3))
                )
                mappings.append({
                    "archiveReference": (
                        f"{source_book.code} {chapter.number}:{verse.verse}"
                    ),
                    "historicalReference": (
                        f"{source_book.code} {historical}"
                    ),
                })
        chapters.append(SourceChapter(
            chapter.number,
            verses,
            list(chapter.superscription_parts),
        ))
    validate_book_coverage(source_book.code, chapters)
    return SourceBook(
        source_book.code,
        source_book.source_name,
        source_book.source_sha256,
        chapters,
        mappings,
    )


def remap_native_reference_markers(
    source_book: SourceBook,
    target_units: list[tuple[int, int, int]],
) -> SourceBook:
    """Map eBible's normalized slots onto the German reader's references.

    The Luther archive uses the standard/Bible.com chapter and verse slots while
    retaining the historical printed reference as a visible ``[chapter:verse]``
    label. Those labels are metadata, not Scripture text. The in-app reader must
    share one chapter model across its modern and traditional choices, so this
    routine aligns the unchanged canonical sequence to the locale's existing
    reference units. It keeps source-combined verses as ranges and never invents
    a/b fragments.
    """
    source_segments: list[
        tuple[SourceVerse, str, tuple[int, int] | None, int]
    ] = []
    superscription_indexes: list[tuple[int, list[str]]] = []
    mappings: list[dict] = list(source_book.source_mappings)
    for source_chapter in source_book.chapters:
        chapter_first_segment = len(source_segments)
        for source_verse in source_chapter.verses:
            text = source_verse.text
            markers = list(NATIVE_REFERENCE_MARKER.finditer(text))
            segments: list[tuple[str, tuple[int, int] | None]] = []
            if not markers:
                segments.append((text, None))
            else:
                if markers[0].start() > 0:
                    prefix = normalize_space(text[:markers[0].start()])
                    if prefix:
                        segments.append((prefix, None))
                for index, marker in enumerate(markers):
                    if marker.group(1) is None:
                        raise ImportErrorDetail(
                            f"Lettered alternate reference {marker.group(0)} cannot "
                            f"split {source_book.code} {source_chapter.number}:"
                            f"{source_verse.verse} during German remapping"
                        )
                    end = markers[index + 1].start() if index + 1 < len(markers) else len(text)
                    visible = normalize_space(text[marker.end():end])
                    if not visible:
                        raise ImportErrorDetail(
                            f"Empty text after {marker.group(0)} in {source_book.code} "
                            f"{source_chapter.number}:{source_verse.verse}"
                        )
                    segments.append((
                        visible,
                        (int(marker.group(1)), int(marker.group(2))),
                    ))
            for segment_index, (visible, historical_reference) in enumerate(segments):
                source_segments.append((
                    source_verse,
                    visible,
                    historical_reference,
                    segment_index,
                ))
        if source_chapter.superscription_parts:
            if chapter_first_segment == len(source_segments):
                raise ImportErrorDetail(
                    f"Cannot place superscription for {source_book.code} "
                    f"chapter {source_chapter.number}"
                )
            superscription_indexes.append((
                chapter_first_segment,
                list(source_chapter.superscription_parts),
            ))

    remapped: dict[int, list[SourceVerse]] = {}
    segment_targets: list[tuple[int, int, int]] = []
    target_index = 0
    for source_verse, visible, historical_reference, segment_index in source_segments:
        width = GERMAN_SOURCE_UNIT_WIDTHS.get((
            source_book.code,
            source_verse.chapter,
            source_verse.verse,
            segment_index,
        ), 1)
        selected = target_units[target_index:target_index + width]
        if len(selected) != width:
            raise ImportErrorDetail(
                f"{source_book.code}: source text exceeds locale reference inventory"
            )
        target_chapter = selected[0][0]
        if any(chapter != target_chapter for chapter, _start, _end in selected):
            raise ImportErrorDetail(
                f"{source_book.code} {source_verse.chapter}:{source_verse.verse} "
                "would span a chapter boundary"
            )
        for left, right in zip(selected, selected[1:]):
            if right[1] != left[2] + 1:
                raise ImportErrorDetail(
                    f"Non-contiguous target range in {source_book.code}"
                )
        target_start = selected[0][1]
        target_end = selected[-1][2]
        segment_targets.append((target_chapter, target_start, target_end))
        remapped.setdefault(target_chapter, []).append(SourceVerse(
            chapter=target_chapter,
            verse=target_start,
            verse_end=target_end,
            parts=[visible],
            footnotes=source_verse.footnotes if segment_index == 0 else 0,
            source_jesus_spans=visible.count("[J]"),
            addition_spans=visible.count("[ADD]"),
            promoted_from_footnote=(
                source_verse.promoted_from_footnote and segment_index == 0
            ),
            source_placeholder=(
                source_verse.source_placeholder and segment_index == 0
            ),
        ))
        mapping = {
            "archiveReference": (
                f"{source_book.code} {source_verse.chapter}:{source_verse.verse}"
            ),
            "displayReference": (
                f"{source_book.code} {target_chapter}:{target_start}"
                + (f"-{target_end}" if target_end != target_start else "")
            ),
        }
        if historical_reference is not None:
            mapping["historicalReference"] = (
                f"{source_book.code} {historical_reference[0]}:"
                f"{historical_reference[1]}"
            )
        if (
            historical_reference is not None or
            source_verse.chapter != target_chapter or
            source_verse.verse != target_start or
            source_verse.verse_end != target_end or
            segment_index != 0
        ):
            mappings.append(mapping)
        target_index += width

    if target_index != len(target_units):
        raise ImportErrorDetail(
            f"{source_book.code}: mapped {target_index} locale reference units; "
            f"expected {len(target_units)}"
        )

    superscriptions: dict[int, list[str]] = {}
    for segment_index, parts in superscription_indexes:
        target_chapter = segment_targets[segment_index][0]
        superscriptions.setdefault(target_chapter, []).extend(parts)

    chapters = [
        SourceChapter(
            chapter,
            remapped[chapter],
            superscriptions.get(chapter, []),
        )
        for chapter in sorted(remapped)
    ]
    validate_book_coverage(source_book.code, chapters)
    return SourceBook(
        source_book.code,
        source_book.source_name,
        source_book.source_sha256,
        chapters,
        mappings,
    )


def apply_display_versification(
    config: EditionConfig,
    code: str,
    source_book: SourceBook,
    target_units: list[tuple[int, int, int]],
) -> tuple[SourceBook, dict | None]:
    """Map source-normalized chapters to the numbering used by that app locale."""
    if config.language == "de":
        if code in GERMAN_KEEP_SOURCE_VERSIFICATION:
            remapped = strip_native_reference_markers(source_book)
        else:
            remapped = remap_native_reference_markers(source_book, target_units)
        if len(remapped.source_mappings) != len(source_book.source_mappings):
            return remapped, {
                "kind": (
                    "alternateReferenceMarkers"
                    if code in GERMAN_KEEP_SOURCE_VERSIFICATION
                    else "verseMappings"
                ),
                "mappings": remapped.source_mappings,
                "reason": (
                    "The eBible archive uses normalized interchange references and "
                    "prints historical Luther references in brackets. The app strips "
                    "that metadata and maps the unchanged verse sequence to the "
                    "German reader's existing reference structure."
                ),
            }
        source_book = remapped
    if config.language in {"de", "fr"} and code == "JOL" and len(source_book.chapters) == 3:
        source_two = source_book.chapters[1]
        source_three = source_book.chapters[2]
        if source_two.verses[-1].verse_end != 32 or source_three.verses[-1].verse_end != 21:
            raise ImportErrorDetail(
                f"{config.language}/Joel has an unexpected three-chapter source layout"
            )
        chapter_two = SourceChapter(
            2,
            [clone_verse(verse, 2, verse.verse) for verse in source_two.verses if verse.verse_end <= 27],
            list(source_two.superscription_parts),
        )
        chapter_three = SourceChapter(
            3,
            [clone_verse(verse, 3, verse.verse - 27) for verse in source_two.verses if verse.verse >= 28],
        )
        chapter_four = SourceChapter(
            4,
            [clone_verse(verse, 4, verse.verse) for verse in source_three.verses],
            list(source_three.superscription_parts),
        )
        mapped = SourceBook(
            source_book.code,
            source_book.source_name,
            source_book.source_sha256,
            [source_book.chapters[0], chapter_two, chapter_three, chapter_four],
        )
        validate_book_coverage(code, mapped.chapters)
        return mapped, {
            "kind": "chapterSplit",
            "sourceRange": "Joel 2:28-32; 3:1-21",
            "targetRange": "Joel 3:1-5; 4:1-21",
            "reason": "The app locale uses the four-chapter Hebrew numbering while the source archive is normalized to three chapters.",
        }
    if config.language in {"de", "fr"} and code == "MAL" and len(source_book.chapters) == 4:
        source_three = source_book.chapters[2]
        source_four = source_book.chapters[3]
        if source_three.verses[-1].verse_end != 18 or source_four.verses[-1].verse_end != 6:
            raise ImportErrorDetail(
                f"{config.language}/Malachi has an unexpected four-chapter source layout"
            )
        chapter_three = SourceChapter(
            3,
            [clone_verse(verse, 3, verse.verse) for verse in source_three.verses] +
            [clone_verse(verse, 3, verse.verse + 18) for verse in source_four.verses],
            list(source_three.superscription_parts),
        )
        mapped = SourceBook(
            source_book.code,
            source_book.source_name,
            source_book.source_sha256,
            [source_book.chapters[0], source_book.chapters[1], chapter_three],
            list(source_book.source_mappings),
        )
        validate_book_coverage(code, mapped.chapters)
        return mapped, {
            "kind": "chapterMerge",
            "sourceRange": "Malachi 3:1-18; 4:1-6",
            "targetRange": "Malachi 3:1-24",
            "reason": "The app locale uses the three-chapter Hebrew numbering while the source archive is normalized to four chapters.",
        }
    if source_book.source_mappings:
        return source_book, {
            "kind": "verseMappings",
            "mappings": source_book.source_mappings,
            "reason": "The source module uses merged, shifted, or explicitly empty verse slots; the app preserves the source text while exposing the locale's reference numbers.",
        }
    return source_book, None


def base_jesus_ranges(
    repo_root: Path,
    collection: str,
    book_id: str,
) -> tuple[dict[int, list[tuple[int, int]]], dict[int, list[tuple[int, int]]]]:
    # The KJV overlay carries word-level [J] from its hash-pinned CrossWire
    # source wj markers. Localized base corpora often expand partial speech to
    # whole verses, and BSB metadata is not the source authority here.
    if collection != "new_testament":
        return {}, {}
    path = (
        repo_root / "shared" / "assets" / "books" / "editions" /
        "en" / "kjv1769" / collection / f"{book_id}.json"
    )
    raw = json.loads(path.read_text(encoding="utf-8"))
    full: dict[int, list[tuple[int, int]]] = {}
    mixed: dict[int, list[tuple[int, int]]] = {}
    for chapter in raw["chapters"]:
        chapter_number = chapter["number"]
        for verse in chapter["verses"]:
            scripture = verse["text"]
            if "[J]" not in scripture:
                continue
            outside_speech = re.sub(r"\[J\].*?\[/J\]", "", scripture, flags=re.DOTALL)
            outside_speech = re.sub(r"\[/?(?:ADD|DN)\]", "", outside_speech)
            target = full if not outside_speech.strip() else mixed
            target.setdefault(chapter_number, []).append(
                (verse["verse"], verse.get("verseEnd", verse["verse"]))
            )
    return full, mixed


def overlaps(ranges: Iterable[tuple[int, int]], start: int, end: int) -> bool:
    return any(start <= other_end and other_start <= end for other_start, other_end in ranges)


def covers_all(ranges: Iterable[tuple[int, int]], start: int, end: int) -> bool:
    rows = tuple(ranges)
    return all(any(other_start <= verse <= other_end for other_start, other_end in rows)
               for verse in range(start, end + 1))


def chapter_to_json(
    chapter: SourceChapter,
    inherited_jesus: dict[int, list[tuple[int, int]]],
    mixed_jesus: dict[int, list[tuple[int, int]]],
    allow_inherited_jesus: bool = True,
) -> tuple[dict, int, int]:
    verses = []
    inherited_spans = 0
    omitted_mixed_spans = 0
    for source_verse in chapter.verses:
        text = source_verse.text
        if allow_inherited_jesus and not source_verse.source_placeholder and "[J]" not in text:
            if covers_all(
                inherited_jesus.get(chapter.number, []),
                source_verse.verse,
                source_verse.verse_end,
            ):
                text = f"[J]{text}[/J]"
                inherited_spans += 1
            elif overlaps(
                mixed_jesus.get(chapter.number, []),
                source_verse.verse,
                source_verse.verse_end,
            ):
                # Coloring the whole translated verse would falsely color its
                # narration. Leave it uncolored unless the source supplied wj.
                omitted_mixed_spans += 1
        item = {
            "chapter": chapter.number,
            "verse": source_verse.verse,
            "text": text,
        }
        if source_verse.verse_end != source_verse.verse:
            item["verseEnd"] = source_verse.verse_end
        if source_verse.footnotes:
            item["sourceFootnoteCount"] = source_verse.footnotes
        if source_verse.promoted_from_footnote:
            item["sourceFootnotePromotion"] = True
        if source_verse.source_placeholder:
            item["sourcePlaceholder"] = True
        verses.append(item)
    item = {"number": chapter.number}
    item["lastVerse"] = chapter.verses[-1].verse_end
    item["verseUnitCount"] = len(chapter.verses)
    if chapter.superscription:
        item["superscription"] = chapter.superscription
    item["verses"] = verses
    return item, inherited_spans, omitted_mixed_spans


def _reviewed_heading_target(
    rules: list[dict],
    chapter: int,
    verse: int,
    label: str,
) -> tuple[int, int] | None:
    """Map one heading anchor, never guessing from adjacent verse numbers."""
    targets: set[tuple[int, int]] = set()
    for row in rules:
        source_chapter = row["sourceChapter"]
        source_start = row["sourceVerse"]
        source_end = row.get("sourceVerseEnd", source_start)
        if source_chapter != chapter or not source_start <= verse <= source_end:
            continue
        target_chapter = row["targetChapter"]
        target_start = row["targetVerse"]
        target_end = row.get("targetVerseEnd", target_start)
        source_length = source_end - source_start + 1
        target_length = target_end - target_start + 1
        if source_length == target_length:
            targets.add((target_chapter, target_start + verse - source_start))
        elif source_length == 1 or target_length == 1:
            # One source verse split across targets begins at the first native
            # target unit. A merged target stays whole.
            targets.add((target_chapter, target_start))
        else:
            raise ImportErrorDetail(f"Ambiguous heading passage map: {label} {chapter}:{verse}")
    if len(targets) > 1:
        raise ImportErrorDetail(f"Conflicting heading passage maps: {label} {chapter}:{verse}")
    return next(iter(targets)) if targets else None


def _validated_heading_rules(
    repo_root: Path,
    config: EditionConfig,
    collection: str,
    book_id: str,
    source_book: SourceBook,
) -> list[dict]:
    document = reference_map_for(config, repo_root)
    matches = [book for book in document["books"] if book.get("bookId") == book_id] if document else []
    if len(matches) > 1:
        raise ImportErrorDetail(f"Duplicate heading passage maps: {config.language}/{book_id}")
    if not matches:
        return []
    rules = matches[0].get("mappings")
    if not isinstance(rules, list):
        raise ImportErrorDetail(f"Malformed heading passage map: {config.language}/{book_id}")
    base_coordinates = {
        (chapter, verse)
        for chapter, start, end in base_verse_units(repo_root, config.language, collection, book_id)
        for verse in range(start, end + 1)
    }
    target_coordinates = {
        (chapter.number, verse)
        for chapter in source_book.chapters
        for unit in chapter.verses
        for verse in range(unit.verse, unit.verse_end + 1)
    }
    label = f"{config.language}/{book_id}"
    for index, row in enumerate(rules):
        if not isinstance(row, dict):
            raise ImportErrorDetail(f"Malformed heading passage map: {label} rule {index}")
        spans = []
        for side in ("source", "target"):
            chapter = row.get(f"{side}Chapter")
            start = row.get(f"{side}Verse")
            end = row.get(f"{side}VerseEnd", start)
            if any(type(value) is not int or value < 1 for value in (chapter, start, end)) or end < start or end - start > 1000:
                raise ImportErrorDetail(f"Malformed heading passage map: {label} rule {index} {side}")
            coordinates = base_coordinates if side == "source" else target_coordinates
            if any((chapter, verse) not in coordinates for verse in range(start, end + 1)):
                raise ImportErrorDetail(f"Heading passage map has absent {side} verse: {label} rule {index}")
            spans.append(end - start + 1)
        if spans[0] != spans[1] and min(spans) > 1:
            raise ImportErrorDetail(f"Ambiguous heading passage map: {label} rule {index}")
    return rules


def _validated_heading_relocations(
    config: EditionConfig,
    code: str,
    collection: str,
    book_id: str,
    original_by_chapter: dict[int, list[dict]],
    source_book: SourceBook,
) -> dict[tuple[int, int], HeadingRelocation]:
    """Load exact-text heading exceptions and validate them against both corpora."""
    try:
        table = load_heading_maps(HEADING_MAP_PATH)
    except HeadingMapError as exc:
        raise ImportErrorDetail(str(exc)) from exc

    configured_editions = {
        (edition.language, edition.edition_id): edition
        for edition in EDITIONS
    }
    configured_books = {
        book_code: (book_collection, configured_book_id)
        for book_code, book_collection, configured_book_id in BOOKS
    }
    for table_key, entry in table.items():
        table_config = configured_editions.get((entry.language, entry.edition_id))
        if table_config is None:
            raise ImportErrorDetail(
                f"Heading map references an unknown edition: {entry.language}/{entry.edition_id}"
            )
        expected_book = configured_books.get(entry.book_code)
        if expected_book != (entry.collection, entry.book_id):
            raise ImportErrorDetail(
                f"Heading map references an unknown or mismatched book: "
                f"{'/'.join(table_key)} -> {entry.collection}/{entry.book_id}"
            )
        expected_provenance = (
            table_config.source_title,
            table_config.source_url,
            table_config.archive_sha256.upper(),
            SOURCE_DATE,
        )
        actual_provenance = (
            entry.source_title,
            entry.source_url,
            entry.source_artifact_sha256,
            entry.source_date,
        )
        if actual_provenance != expected_provenance:
            raise ImportErrorDetail(
                f"Heading map pinned-source provenance mismatch: "
                f"{entry.language}/{entry.edition_id}/{entry.book_id}"
            )

    key = (config.language, config.edition_id, code)
    reviewed = table.get(key)
    if reviewed is None:
        return {}

    label = f"{config.language}/{config.edition_id}/{book_id}"
    if reviewed.collection != collection or reviewed.book_id != book_id:
        raise ImportErrorDetail(
            f"Heading map book identity mismatch: {label} is recorded as "
            f"{reviewed.collection}/{reviewed.book_id}"
        )
    rows_by_anchor: dict[tuple[int, int], list[dict]] = {}
    for chapter, headings in original_by_chapter.items():
        for heading in headings:
            before_verse = heading.get("beforeVerse")
            if type(before_verse) is int:
                rows_by_anchor.setdefault((chapter, before_verse), []).append(heading)

    native_unit_starts = {
        (chapter.number, verse.verse)
        for chapter in source_book.chapters
        for verse in chapter.verses
    }
    relocations: dict[tuple[int, int], HeadingRelocation] = {}
    for relocation in reviewed.relocations:
        anchor = (relocation.source_chapter, relocation.source_before_verse)
        matches = rows_by_anchor.get(anchor, [])
        if len(matches) != 1:
            raise ImportErrorDetail(
                f"Heading map source anchor must match exactly one base row: "
                f"{label} {anchor[0]}:{anchor[1]} (found {len(matches)})"
            )
        actual_text = matches[0].get("text")
        if actual_text != relocation.source_text:
            raise ImportErrorDetail(
                f"Heading map source text mismatch: {label} {anchor[0]}:{anchor[1]}"
            )
        for target in relocation.targets:
            target_anchor = (target.chapter, target.before_verse)
            if target_anchor not in native_unit_starts:
                raise ImportErrorDetail(
                    f"Heading map target is not a native verse-unit start: "
                    f"{label} {target.chapter}:{target.before_verse}"
                )
        relocations[anchor] = relocation
    return relocations


def heading_overrides(
    repo_root: Path,
    config: EditionConfig,
    code: str,
    collection: str,
    book_id: str,
    source_book: SourceBook,
) -> dict[int, list[dict]]:
    """Return the complete per-chapter heading table for one edition book."""
    base_path = (
        repo_root / "shared" / "assets" / "books" / collection /
        config.language / f"{book_id}.json"
    )
    base = json.loads(base_path.read_text(encoding="utf-8"))
    original_by_chapter: dict[int, list[dict]] = {
        int(story["id"].rsplit("-", 1)[-1]): [dict(row) for row in story.get("headings", [])]
        for story in base.get("stories", [])
        if story.get("id", "").rsplit("-", 1)[-1].isdigit()
    }
    rules = _validated_heading_rules(repo_root, config, collection, book_id, source_book)
    reviewed_relocations = _validated_heading_relocations(
        config,
        code,
        collection,
        book_id,
        original_by_chapter,
        source_book,
    )

    # Preserve base heading order, including across chapter boundaries. Native
    # merged target units can receive multiple distinct titles; stack them.
    verses_by_chapter = {chapter.number: chapter.verses for chapter in source_book.chapters}
    placed: dict[tuple[int, int], list[str]] = {}
    for source_chapter, headings in sorted(original_by_chapter.items()):
        for heading in headings:
            source_verse = heading.get("beforeVerse")
            title = heading.get("text")
            if type(source_verse) is not int or source_verse < 1 or not isinstance(title, str) or not title.strip():
                raise ImportErrorDetail(f"Malformed base heading: {config.language}/{book_id} {source_chapter}")
            anchor = (source_chapter, source_verse)
            relocation = reviewed_relocations.get(anchor)
            if relocation is not None:
                # Publisher section boundaries are independent of verse
                # equivalence. An exact reviewed heading row is authoritative
                # even when the broader passage map chooses another anchor.
                explicit_targets = (
                    (target.chapter, target.before_verse, target.text)
                    for target in relocation.targets
                )
            else:
                mapped = _reviewed_heading_target(
                    rules, source_chapter, source_verse, f"{config.language}/{book_id}"
                )
                target_chapter, target_verse = mapped or anchor
                unit = next(
                    (verse for verse in verses_by_chapter.get(target_chapter, ())
                     if verse.verse <= target_verse <= verse.verse_end),
                    None,
                )
                if unit is None:
                    raise ImportErrorDetail(
                        f"Heading falls outside selected edition: "
                        f"{config.language}/{book_id} {target_chapter}:{target_verse}"
                    )
                explicit_targets = ((target_chapter, unit.verse, title),)

            for target_chapter, target_verse, target_text in explicit_targets:
                placed.setdefault((target_chapter, target_verse), []).append(target_text)

    headings_by_chapter: dict[int, list[dict]] = {}
    for (chapter, verse), titles in sorted(placed.items()):
        headings_by_chapter.setdefault(chapter, []).append({
            "beforeVerse": verse,
            "text": "\n".join(titles),
        })

    return {
        chapter.number: headings_by_chapter.get(chapter.number, [])
        for chapter in source_book.chapters
    }


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def count_tag(chapters: Iterable[dict], tag: str) -> int:
    return sum(
        verse["text"].count(tag)
        for chapter in chapters
        for verse in chapter["verses"]
    )


def verify_against_base(repo_root: Path, language: str, collection: str, book_id: str, chapters: list[SourceChapter]) -> None:
    base_path = repo_root / "shared" / "assets" / "books" / collection / language / f"{book_id}.json"
    raw = json.loads(base_path.read_text(encoding="utf-8"))
    story_count = sum(1 for story in raw.get("stories", []) if story.get("id", "").rsplit("-", 1)[-1].isdigit())
    if story_count != len(chapters):
        raise ImportErrorDetail(
            f"{language}/{book_id}: source has {len(chapters)} chapters but base has {story_count}"
        )


def deuterocanonical_fallbacks(repo_root: Path, language: str) -> list[dict]:
    index_path = repo_root / "shared" / "assets" / "books" / "deuterocanonical" / language / "_index.json"
    rows = json.loads(index_path.read_text(encoding="utf-8"))
    return [
        {
            "collection": "deuterocanonical",
            "bookId": row[0],
            "coverage": "fallback",
            "fallbackEditionId": "current",
            "reason": "The traditional source contains the 66-book Protestant canon only.",
        }
        for row in rows
        if row[0]
    ]


def reference_map_for(config: EditionConfig, repo_root: Path) -> dict | None:
    """Package reviewed passage equivalences; never derive them from counts."""
    try:
        return reference_map_for_edition(repo_root, config.language, config.edition_id)
    except ValueError as exc:
        raise ImportErrorDetail(str(exc)) from exc


def build_edition(
    config: EditionConfig,
    source_root: Path,
    repo_root: Path,
    pysword_path: Path | None,
) -> dict:
    archive = source_root / config.archive_name
    if not archive.is_file():
        raise ImportErrorDetail(f"Missing source file: {archive}")
    actual_hash = sha256(archive)
    if actual_hash != config.archive_sha256:
        raise ImportErrorDetail(
            f"{config.archive_name} SHA-256 mismatch; expected {config.archive_sha256}, got {actual_hash}"
        )

    auxiliary: Path | None = None
    auxiliary_fields = (
        config.auxiliary_name,
        config.auxiliary_sha256,
        config.auxiliary_url,
        config.auxiliary_title,
    )
    if any(value is not None for value in auxiliary_fields):
        if not all(value is not None for value in auxiliary_fields):
            raise ImportErrorDetail(f"Incomplete auxiliary source configuration for {config.language}")
        auxiliary = source_root / str(config.auxiliary_name)
        if not auxiliary.is_file():
            raise ImportErrorDetail(f"Missing auxiliary source file: {auxiliary}")
        auxiliary_hash = sha256(auxiliary)
        if auxiliary_hash != config.auxiliary_sha256:
            raise ImportErrorDetail(
                f"{config.auxiliary_name} SHA-256 mismatch; "
                f"expected {config.auxiliary_sha256}, got {auxiliary_hash}"
            )

    if config.source_format == "sword":
        parsed = parse_sword(config, archive, pysword_path)
        extracted_file_set_hash = None
    elif config.source_format == "krv-json":
        if auxiliary is None:
            raise ImportErrorDetail("KRV import requires its pinned auxiliary source")
        parsed = parse_krv_json(config, archive, auxiliary)
        extracted_file_set_hash = None
    else:
        parsed, extracted_file_set_hash = parse_usfm_archive(archive)

    # An edition that supplies its own word-level speech markers controls both
    # where coloring starts and where it stops. Do not fill its unmarked verses
    # from another translation's editorial boundaries.
    source_has_jesus_markup = any(
        verse.source_jesus_spans
        for book in parsed.values()
        for chapter in book.chapters
        for verse in chapter.verses
    )
    reviewed_jesus = (
        ReviewedJesusSpans.load(repo_root, config.language, config.edition_id)
        if (config.language, config.edition_id) in REVIEWED_JESUS_EDITIONS
        else None
    )
    mixed_candidates: set[tuple[str, str, int, int]] = set()
    full_candidates: set[tuple[str, str, int, int]] = set()

    output_dir = (
        repo_root / "shared" / "assets" / "books" / "editions" /
        config.language / config.edition_id
    )
    staging = output_dir.parent / f".{config.edition_id}.staging"
    editions_root = (repo_root.resolve() / "shared" / "assets" / "books" / "editions")
    for target in (output_dir, staging):
        if not target.resolve().is_relative_to(editions_root):
            raise ImportErrorDetail(f"Generated output escapes the editions directory: {target}")
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir(parents=True)

    manifest_books: list[dict] = []
    inherited_total = 0
    omitted_mixed_total = 0
    reviewed_mixed_total = 0
    reviewed_omission_total = 0
    for code, collection, book_id in BOOKS:
        target_units = base_verse_units(
            repo_root, config.language, collection, book_id
        )
        source_book, source_mapping = apply_display_versification(
            config, code, parsed[code], target_units
        )
        verify_against_base(repo_root, config.language, collection, book_id, source_book.chapters)
        inherited, mixed_jesus = base_jesus_ranges(
            repo_root, collection, book_id
        )
        if reviewed_jesus is not None:
            full_candidates.update(
                verse_key(collection, book_id, chapter_number, number)
                for chapter_number, ranges in inherited.items()
                for start, end in ranges for number in range(start, end + 1)
            )
        if reviewed_jesus is not None:
            for source_chapter in source_book.chapters:
                spoken_numbers = {
                    number
                    for start, end in (
                        inherited.get(source_chapter.number, []) +
                        mixed_jesus.get(source_chapter.number, [])
                    )
                    for number in range(start, end + 1)
                }
                target_counts = {
                    number: sum(
                        target_verse.verse <= number <= target_verse.verse_end
                        for target_verse in source_chapter.verses
                    )
                    for number in spoken_numbers
                }
                unmapped = [number for number, count in target_counts.items() if count != 1]
                if unmapped:
                    raise ImportErrorDetail(
                        f"KJV speech coordinates do not map to one native target unit: "
                        f"{config.language}/{book_id} {source_chapter.number}:{sorted(unmapped)[:5]}"
                    )
        overrides = heading_overrides(
            repo_root, config, code, collection, book_id, source_book
        )
        chapter_rows = []
        inherited_book = 0
        omitted_mixed_book = 0
        reviewed_mixed_book = 0
        reviewed_omission_book = 0
        reviewed_relocation_book = 0
        supplemental_book = 0
        reviewed_full_book = 0
        reviewed_full_omission_book = 0
        for chapter in source_book.chapters:
            row, inherited_count, omitted_mixed_count = chapter_to_json(
                chapter, inherited, mixed_jesus,
                allow_inherited_jesus=not source_has_jesus_markup,
            )
            chapter_reviewed = 0
            if reviewed_jesus is not None:
                for verse_row in row["verses"]:
                    if verse_row.get("sourcePlaceholder"):
                        continue
                    key = verse_key(collection, book_id, chapter.number, verse_row["verse"])
                    ledger_row = reviewed_jesus.rows.get(key)
                    is_mixed = overlaps(
                        mixed_jesus.get(chapter.number, []),
                        verse_row["verse"], verse_row.get("verseEnd", verse_row["verse"]),
                    )
                    is_supplemental = ledger_row is not None and ledger_row.get("supplementalFor") is not None
                    is_full_override = ledger_row is not None and ledger_row.get("overrideInherited") is True
                    if not is_mixed and not is_supplemental and not is_full_override:
                        continue
                    if "[J]" in verse_row["text"] and not is_full_override:
                        raise ImportErrorDetail(f"Inherited J overlaps reviewed target: {key}")
                    if is_mixed:
                        mixed_candidates.add(key)
                    raw = verse_row["text"].replace("[J]", "").replace("[/J]", "") if is_full_override else verse_row["text"]
                    verse_row["text"] = reviewed_jesus.apply(
                        collection, book_id, chapter.number, verse_row["verse"],
                        raw, verse_row.get("verseEnd", verse_row["verse"]),
                    )
                    if is_full_override:
                        reviewed_full_book += 1
                        inherited_count -= 1
                        if ledger_row.get("noTargetSpeech") is True:
                            reviewed_full_omission_book += 1
                    elif is_supplemental:
                        supplemental_book += 1
                    elif "[J]" in verse_row["text"]:
                        chapter_reviewed += 1
                        reviewed_mixed_book += 1
                    elif ledger_row is not None and ledger_row.get("noTargetSpeech") is True:
                        chapter_reviewed += 1
                        reviewed_omission_book += 1
                    elif ledger_row is not None and ledger_row.get("speechRelocatedTo") is not None:
                        chapter_reviewed += 1
                        reviewed_relocation_book += 1
                omitted_mixed_count -= chapter_reviewed
            # This is the runtime source of truth for alternate-edition
            # heading placement. Keep even empty arrays explicit.
            row["headings"] = overrides[chapter.number]
            chapter_rows.append(row)
            inherited_book += inherited_count
            omitted_mixed_book += omitted_mixed_count
        inherited_total += inherited_book
        omitted_mixed_total += omitted_mixed_book
        reviewed_mixed_total += reviewed_mixed_book
        reviewed_omission_total += reviewed_omission_book
        overlay = {
            "schemaVersion": SCHEMA_VERSION,
            "editionId": config.edition_id,
            "language": config.language,
            "collection": collection,
            "bookId": book_id,
            "sourceBookCode": code,
            "coverage": "full",
            "chapters": chapter_rows,
        }
        if source_mapping is not None:
            overlay["sourceMapping"] = source_mapping
        relative_output = Path(collection) / f"{book_id}.json"
        write_json(staging / relative_output, overlay)
        verse_units = sum(len(chapter["verses"]) for chapter in chapter_rows)
        covered_verses = sum(
            verse.get("verseEnd", verse["verse"]) - verse["verse"] + 1
            for chapter in chapter_rows
            for verse in chapter["verses"]
        )
        manifest_book = {
            "collection": collection,
            "bookId": book_id,
            "sourceBookCode": code,
            "sourceFile": source_book.source_name,
            "sourceFileSha256": source_book.source_sha256,
            "coverage": "full",
            "output": relative_output.as_posix(),
            "chapters": len(chapter_rows),
            "verseUnits": verse_units,
            "coveredVerseNumbers": covered_verses,
            "jesusWordSpans": count_tag(chapter_rows, "[J]"),
            "inheritedJesusVerseSpans": inherited_book,
            "omittedMixedJesusVerseSpans": omitted_mixed_book,
            "reviewedMixedJesusVerseSpans": reviewed_mixed_book,
            "reviewedOmittedJesusVerseUnits": reviewed_omission_book,
            "reviewedRelocatedJesusVerseUnits": reviewed_relocation_book,
            "supplementalJesusVerseUnits": supplemental_book,
            "reviewedFullJesusVerseUnits": reviewed_full_book,
            "reviewedFullOmittedJesusVerseUnits": reviewed_full_omission_book,
            "translatorAdditionSpans": count_tag(chapter_rows, "[ADD]"),
            "superscriptions": sum(bool(chapter.get("superscription")) for chapter in chapter_rows),
            "promotedFootnoteVerses": sum(
                bool(verse.get("sourceFootnotePromotion"))
                for chapter in chapter_rows
                for verse in chapter["verses"]
            ),
            "sourcePlaceholderVerses": sum(
                bool(verse.get("sourcePlaceholder"))
                for chapter in chapter_rows
                for verse in chapter["verses"]
            ),
        }
        if source_mapping is not None:
            manifest_book["sourceMapping"] = source_mapping
        manifest_books.append(manifest_book)
        if reviewed_jesus is not None:
            reviewed_jesus.verify_relocations(collection, book_id, chapter_rows)

    if reviewed_jesus is not None:
        reviewed_jesus.validate_coverage(mixed_candidates, full_candidates)

    if config.language == "ru" and config.edition_id == "synodal1876":
        from import_synodal_deuterocanon import SynodalImportError, build_synodal_dc
        try:
            manifest_books.extend(build_synodal_dc(
                source_root / "RusSynodal.zip", repo_root,
                staging / "deuterocanonical", pysword_path,
            ))
        except SynodalImportError as exc:
            raise ImportErrorDetail(str(exc)) from exc
    else:
        manifest_books.extend(deuterocanonical_fallbacks(repo_root, config.language))
    totals = {
        "overlayBooks": sum(row["coverage"] == "full" for row in manifest_books),
        "fallbackBooks": sum(row["coverage"] == "fallback" for row in manifest_books),
        "chapters": sum(row.get("chapters", 0) for row in manifest_books),
        "verseUnits": sum(row.get("verseUnits", 0) for row in manifest_books),
        "coveredVerseNumbers": sum(row.get("coveredVerseNumbers", 0) for row in manifest_books),
        "jesusWordSpans": sum(row.get("jesusWordSpans", 0) for row in manifest_books),
        "inheritedJesusVerseSpans": inherited_total,
        "omittedMixedJesusVerseSpans": omitted_mixed_total,
        "reviewedMixedJesusVerseSpans": reviewed_mixed_total,
        "reviewedOmittedJesusVerseUnits": reviewed_omission_total,
        "reviewedRelocatedJesusVerseUnits": sum(row.get("reviewedRelocatedJesusVerseUnits", 0) for row in manifest_books),
        "supplementalJesusVerseUnits": sum(row.get("supplementalJesusVerseUnits", 0) for row in manifest_books),
        "reviewedFullJesusVerseUnits": sum(row.get("reviewedFullJesusVerseUnits", 0) for row in manifest_books),
        "reviewedFullOmittedJesusVerseUnits": sum(row.get("reviewedFullOmittedJesusVerseUnits", 0) for row in manifest_books),
        "translatorAdditionSpans": sum(row.get("translatorAdditionSpans", 0) for row in manifest_books),
        "superscriptions": sum(row.get("superscriptions", 0) for row in manifest_books),
        "promotedFootnoteVerses": sum(row.get("promotedFootnoteVerses", 0) for row in manifest_books),
        "sourcePlaceholderVerses": sum(row.get("sourcePlaceholderVerses", 0) for row in manifest_books),
    }
    source: dict[str, object] = {
        "title": config.source_title,
        "url": config.source_url,
        "sourceDate": SOURCE_DATE,
        "format": config.source_format,
        "rights": config.rights,
    }
    if config.source_format == "krv-json":
        source["sourceFileSha256"] = config.archive_sha256
    else:
        source["archiveSha256"] = config.archive_sha256
    if auxiliary is not None:
        source["auxiliary"] = {
            "title": config.auxiliary_title,
            "url": config.auxiliary_url,
            "sourceFileSha256": config.auxiliary_sha256,
            "purpose": (
                "Supplies only the edition's native omitted- and merged-verse markers; "
                "the primary source supplies Scripture wording."
            ),
        }
    if extracted_file_set_hash is not None:
        source["extractedScriptureFileSetSha256"] = extracted_file_set_hash
    if config.publisher is not None:
        source["publisher"] = config.publisher
    if config.rights_url is not None:
        source["rightsUrl"] = config.rights_url
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "editionId": config.edition_id,
        "displayName": config.display_name,
        "language": config.language,
        "source": source,
        "markup": {
            "jesusWords": (
                "Native source wj spans take precedence. Full-verse inheritance follows the pinned KJV1769 wj source. "
                "All KJV-mixed native units have edition-specific reviewed, raw-text-hash-pinned speech spans or explicit reviewed omissions; narration is not colored."
                if reviewed_jesus is not None else
                "Native source wj spans take precedence. Full-verse inheritance follows the pinned KJV1769 wj source. "
                "Mixed units without edition-specific reviewed spans remain uncolored."
            ),
            "translatorAdditions": "USFM add spans are preserved as [ADD] markers.",
            "footnotesAndCrossReferences": "Source apparatus is excluded from display text.",
        },
        "headings": {
            "textSource": "Localized editorial headings already shipped with the app; not publisher headings for this alternate edition.",
            "placement": "Explicit per-chapter beforeVerse lookup in the alternate edition's displayed coordinates.",
            "emptyChapters": "Stored as explicit empty arrays; runtime fallback is forbidden.",
        },
        "totals": totals,
        "books": manifest_books,
    }
    reference_map = reference_map_for(config, repo_root)
    if reference_map is not None:
        write_json(staging / "_reference_map.json", reference_map)
        manifest["referenceMap"] = {
            "path": "_reference_map.json",
            "books": [book["bookId"] for book in reference_map["books"]],
            "provenance": reference_map["provenance"],
        }
    write_json(staging / "_manifest.json", manifest)
    if output_dir.exists():
        shutil.rmtree(output_dir)
    staging.rename(output_dir)
    return manifest


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--repo-root", type=Path, default=repo_root)
    parser.add_argument("--pysword-path", type=Path)
    parser.add_argument(
        "--languages",
        nargs="*",
        choices=[config.language for config in EDITIONS],
        help="Import only these language tags; the default imports every configured edition.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    selected = set(args.languages or [config.language for config in EDITIONS])
    reports = []
    try:
        for config in EDITIONS:
            if config.language not in selected:
                continue
            manifest = build_edition(
                config,
                args.source_root.resolve(),
                args.repo_root.resolve(),
                args.pysword_path.resolve() if args.pysword_path else None,
            )
            reports.append({
                "language": config.language,
                "editionId": config.edition_id,
                **manifest["totals"],
            })
    except (OSError, UnicodeError, ImportErrorDetail, JesusSpanError, zipfile.BadZipFile) as exc:
        print(f"Traditional-edition import failed: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(reports, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
