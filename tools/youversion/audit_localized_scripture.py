#!/usr/bin/env python3
"""Audit localized Scripture structure against exact BibleGateway editions.

This tool is intentionally read-only. It never stages or changes Scripture.
It caches only sanitized marker/heading metadata and one-way hashes; raw HTML
and source verse text are never written to disk.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Iterable, Sequence
from urllib.parse import parse_qs, urlencode, urljoin, urlparse

try:
    import requests
    from bs4 import BeautifulSoup, NavigableString, Tag
except ImportError as exc:  # pragma: no cover - exercised only on an unprepared host
    raise SystemExit("Install requests and beautifulsoup4 before running this auditor") from exc


SCHEMA_VERSION = 3
BIBLEGATEWAY_BASE = "https://www.biblegateway.com"
MAX_RESPONSE_BYTES = 16 * 1024 * 1024
MAX_BOUNDARY_PROBES = 4
MAX_SPOT_CHECK_CHARS = 500
DEFAULT_CACHE = ".scripture-structure-cache"
LOCAL_REFERENCE_RE = re.compile(r"\s\((\d+):(\d+)(?:-(\d+))?\)\.?$")
MARKER_RE = re.compile(r"(\[J\]|\[/J\]|\[ADD\]|\[/ADD\])")
VERSE_LABEL_RE = re.compile(r"^(\d+)(?:\s*[-\u2010-\u2015]\s*(\d+))?$")
CHALLENGE_MARKERS = (
    "cf-chl-",
    "challenge-platform",
    "enable javascript and cookies to continue",
    "just a moment...",
)


@dataclass(frozen=True)
class Edition:
    language: str
    gateway_code: str
    gateway_label: str
    gateway_language: str
    bible_com_id: int | None
    bible_com_code: str | None
    bible_com_same_edition: bool


EDITIONS: dict[str, Edition] = {
    "de": Edition("de", "SCH2000", "Schlachter 2000 (SCH2000)", "Deutsch (DE)", 157, "SCH2000", True),
    "es": Edition("es", "NVI", "Nueva Versión Internacional (NVI)", "Español (ES)", 128, "NVI", True),
    # BibleGateway does not expose the app's NBS parity edition. SG21 is a
    # structural comparator only; Bible.com 104/NBS remains the link authority.
    "fr": Edition("fr", "SG21", "Segond 21 (SG21)", "Français (FR)", 104, "NBS", False),
    "it": Edition("it", "NR2006", "Nuova Riveduta 2006 (NR2006)", "Italiano (IT)", 122, "NR06", True),
    "ru": Edition("ru", "NRT", "New Russian Translation (NRT)", "Русский (RU)", 143, "NRT", True),
    "pt": Edition("pt", "NVT", "Nova Versão Transformadora (NVT)", "Português (PT)", 1930, "NVT", True),
    # Bible.com has no verified JLB equivalent in the app map. JCB remains the
    # app's numbering/link parity edition and is explicitly marked different.
    "ja": Edition("ja", "JLB", "Japanese Living Bible (JLB)", "日本語 (JA)", 83, "JCB", False),
    # RNKSV is not offered by BibleGateway. KLB is a structural comparator.
    "ko": Edition("ko", "KLB", "Korean Living Bible (KLB)", "한국어 (KO)", 142, "RNKSV", False),
    # BibleGateway and Bible.com use the same CCB label, but their currently
    # exposed texts are not the same revision/verse inventory (for example,
    # John 7:53). Use BibleGateway CCB only as a structural comparator.
    "zh-Hans": Edition("zh-Hans", "CCB", "Chinese Contemporary Bible (Simplified) (CCB)", "汉语 (ZH)", 36, "CCB", False),
    # The current corpus lineage points to Bible.com 139/RCUV, not CCB_T.
    # BibleGateway's RCU17TS is a related 2017 Shen-edition revision, so it is
    # useful for structure only and must never be reported as an exact source.
    "zh-Hant": Edition("zh-Hant", "RCU17TS", "Revised Chinese Union Version (Traditional Script) Shen Edition (RCU17TS)", "汉语 (ZH)", 139, "RCUV", False),
    # SAB and IRVHIN are not offered by BibleGateway. These are structural
    # comparators only; their Bible.com editions remain the link authorities.
    "ar": Edition("ar", "NAV", "Ketab El Hayat (NAV)", "العربية (AR)", 153, "SAB", False),
    "hi": Edition("hi", "ERV-HI", "Hindi Bible: Easy-to-Read Version (ERV-HI)", "हिन्दी (HI)", 1980, "IRVHIN", False),
}


FALLBACK_EDITIONS: dict[str, Edition] = {
    "es": Edition("es", "RVR1960", "Reina-Valera 1960 (RVR1960)", "Español (ES)", 149, "RVR1960", True),
    "fr": Edition("fr", "LSG", "Louis Segond (LSG)", "Français (FR)", 93, "LSG", True),
    "it": Edition("it", "LND", "La Nuova Diodati (LND)", "Italiano (IT)", None, None, False),
    "ru": Edition("ru", "RUSV", "Russian Synodal Version (RUSV)", "Русский (RU)", 400, "SYNO", True),
    "pt": Edition("pt", "ARC", "Almeida Revista e Corrigida 2009 (ARC)", "Português (PT)", 212, "ARC", True),
    "ja": Edition("ja", "JERV", "Japanese Bible: Easy-to-Read Version (JERV)", "日本語 (JA)", 3802, "ERV", False),
    "zh-Hans": Edition("zh-Hans", "CUVS", "Chinese Union Version (Simplified) (CUVS)", "汉语 (ZH)", 48, "CUVS", True),
    "zh-Hant": Edition("zh-Hant", "CUV", "Chinese Union Version (Traditional) (CUV)", "汉语 (ZH)", 414, "CUNP", True),
    "hi": Edition("hi", "SHB", "Saral Hindi Bible (SHB)", "हिन्दी (HI)", None, None, False),
}


BOOK_CODES: dict[str, str] = {
    "genesis": "GEN", "exodus": "EXO", "leviticus": "LEV", "numbers": "NUM",
    "deuteronomy": "DEU", "joshua": "JOS", "judges": "JDG", "ruth": "RUT",
    "1_samuel": "1SA", "2_samuel": "2SA", "1_kings": "1KI", "2_kings": "2KI",
    "1_chronicles": "1CH", "2_chronicles": "2CH", "ezra": "EZR", "nehemiah": "NEH",
    "esther": "EST", "job": "JOB", "psalms": "PSA", "proverbs": "PRO",
    "ecclesiastes": "ECC", "song_of_songs": "SNG", "isaiah": "ISA", "jeremiah": "JER",
    "lamentations": "LAM", "ezekiel": "EZK", "daniel": "DAN", "hosea": "HOS",
    "joel": "JOL", "amos": "AMO", "obadiah": "OBA", "jonah": "JON",
    "micah": "MIC", "nahum": "NAM", "habakkuk": "HAB", "zephaniah": "ZEP",
    "haggai": "HAG", "zechariah": "ZEC", "malachi": "MAL",
    "matthew": "MAT", "mark": "MRK", "luke": "LUK", "john": "JHN",
    "acts": "ACT", "romans": "ROM", "1_corinthians": "1CO", "2_corinthians": "2CO",
    "galatians": "GAL", "ephesians": "EPH", "philippians": "PHP", "colossians": "COL",
    "1_thessalonians": "1TH", "2_thessalonians": "2TH", "1_timothy": "1TI",
    "2_timothy": "2TI", "titus": "TIT", "philemon": "PHM", "hebrews": "HEB",
    "james": "JAS", "1_peter": "1PE", "2_peter": "2PE", "1_john": "1JN",
    "2_john": "2JN", "3_john": "3JN", "jude": "JUD", "revelation": "REV",
}
CODE_TO_BOOK = {code: book for book, code in BOOK_CODES.items()}
BOOK_COLLECTION = {
    book: ("old_testament" if index < 39 else "new_testament")
    for index, book in enumerate(BOOK_CODES)
}

BOOK_SEARCH_NAMES: dict[str, str] = {
    "genesis": "Genesis", "exodus": "Exodus", "leviticus": "Leviticus", "numbers": "Numbers",
    "deuteronomy": "Deuteronomy", "joshua": "Joshua", "judges": "Judges", "ruth": "Ruth",
    "1_samuel": "1 Samuel", "2_samuel": "2 Samuel", "1_kings": "1 Kings", "2_kings": "2 Kings",
    "1_chronicles": "1 Chronicles", "2_chronicles": "2 Chronicles", "ezra": "Ezra", "nehemiah": "Nehemiah",
    "esther": "Esther", "job": "Job", "psalms": "Psalm", "proverbs": "Proverbs",
    "ecclesiastes": "Ecclesiastes", "song_of_songs": "Song of Songs", "isaiah": "Isaiah", "jeremiah": "Jeremiah",
    "lamentations": "Lamentations", "ezekiel": "Ezekiel", "daniel": "Daniel", "hosea": "Hosea",
    "joel": "Joel", "amos": "Amos", "obadiah": "Obadiah", "jonah": "Jonah",
    "micah": "Micah", "nahum": "Nahum", "habakkuk": "Habakkuk", "zephaniah": "Zephaniah",
    "haggai": "Haggai", "zechariah": "Zechariah", "malachi": "Malachi",
    "matthew": "Matthew", "mark": "Mark", "luke": "Luke", "john": "John",
    "acts": "Acts", "romans": "Romans", "1_corinthians": "1 Corinthians", "2_corinthians": "2 Corinthians",
    "galatians": "Galatians", "ephesians": "Ephesians", "philippians": "Philippians", "colossians": "Colossians",
    "1_thessalonians": "1 Thessalonians", "2_thessalonians": "2 Thessalonians", "1_timothy": "1 Timothy",
    "2_timothy": "2 Timothy", "titus": "Titus", "philemon": "Philemon", "hebrews": "Hebrews",
    "james": "James", "1_peter": "1 Peter", "2_peter": "2 Peter", "1_john": "1 John",
    "2_john": "2 John", "3_john": "3 John", "jude": "Jude", "revelation": "Revelation",
}

BG_PREFIXES: dict[str, str] = {
    "genesis": "Gen", "exodus": "Exod", "leviticus": "Lev", "numbers": "Num",
    "deuteronomy": "Deut", "joshua": "Josh", "judges": "Judg", "ruth": "Ruth",
    "1_samuel": "1Sam", "2_samuel": "2Sam", "1_kings": "1Kgs", "2_kings": "2Kgs",
    "1_chronicles": "1Chr", "2_chronicles": "2Chr", "ezra": "Ezra", "nehemiah": "Neh",
    "esther": "Esth", "job": "Job", "psalms": "Ps", "proverbs": "Prov",
    "ecclesiastes": "Eccl", "song_of_songs": "Song", "isaiah": "Isa", "jeremiah": "Jer",
    "lamentations": "Lam", "ezekiel": "Ezek", "daniel": "Dan", "hosea": "Hos",
    "joel": "Joel", "amos": "Amos", "obadiah": "Obad", "jonah": "Jonah",
    "micah": "Mic", "nahum": "Nah", "habakkuk": "Hab", "zephaniah": "Zeph",
    "haggai": "Hag", "zechariah": "Zech", "malachi": "Mal",
    "matthew": "Matt", "mark": "Mark", "luke": "Luke", "john": "John",
    "acts": "Acts", "romans": "Rom", "1_corinthians": "1Cor", "2_corinthians": "2Cor",
    "galatians": "Gal", "ephesians": "Eph", "philippians": "Phil", "colossians": "Col",
    "1_thessalonians": "1Thess", "2_thessalonians": "2Thess", "1_timothy": "1Tim",
    "2_timothy": "2Tim", "titus": "Titus", "philemon": "Phlm", "hebrews": "Heb",
    "james": "Jas", "1_peter": "1Pet", "2_peter": "2Pet", "1_john": "1John",
    "2_john": "2John", "3_john": "3John", "jude": "Jude", "revelation": "Rev",
}

TRADITIONAL_NT_REFS: frozenset[tuple[str, int, int]] = frozenset(
    {
        ("matthew", 17, 21), ("matthew", 18, 11), ("matthew", 23, 14),
        ("mark", 7, 16), ("mark", 9, 44), ("mark", 9, 46),
        ("mark", 11, 26), ("mark", 15, 28), ("luke", 17, 36),
        ("luke", 23, 17), ("john", 5, 4), ("acts", 8, 37),
        ("acts", 15, 34), ("acts", 24, 7), ("acts", 28, 29),
        ("romans", 16, 24),
    }
)

# BibleGateway has a small number of publisher-page defects that must not be
# mistaken for gaps in the underlying edition.  Keep every exception narrow
# and auditable.  SCH2000 Hosea 14 omits its verse-1 span on BibleGateway even
# though YouVersion SCH2000 exposes the verse.  NR2006 Jeremiah 44 renders the
# visible label "2" inside a second span that is incorrectly classed as verse 1.
KNOWN_SOURCE_GAPS: dict[tuple[str, str, int], frozenset[int]] = {
    ("SCH2000", "hosea", 14): frozenset({1}),
}
PLAIN_NUMBER_LABEL_FIXES: dict[tuple[str, str, int, int], int] = {
    ("NR2006", "jeremiah", 44, 1): 2,
    ("NR2006", "jeremiah", 52, 1): 2,
}
# CCB Isaiah 38 prints verses 21-22 before verses 7-20 on BibleGateway.  The
# spans remain individually numbered and unambiguous, but their DOM order is
# editorial rather than numeric.  Reorder only this documented page before
# validating its verse inventory and comparing hashes.
NUMERIC_GROUP_ORDER_FIXES: frozenset[tuple[str, str, int]] = frozenset({
    ("CCB", "isaiah", 38),
})
# BibleGateway wraps NVI Habakkuk 3:1 in an h4.psalm-title even though it is a
# numbered Scripture verse.  Treat that exact span as body text, not a section
# heading; otherwise the source inventory and exact-text comparison lose it.
NUMBERED_HEADING_VERSE_FIXES: frozenset[tuple[str, str, int, int]] = frozenset({
    ("NVI", "habakkuk", 3, 1),
})


class AuditFailure(RuntimeError):
    """The audit cannot prove a requested invariant."""


class ConfigurationError(AuditFailure):
    """The command or configured source matrix is invalid."""


class SourceError(AuditFailure):
    """A remote page, version identity, or DOM invariant is invalid."""


@dataclass(frozen=True)
class Marker:
    start: int
    end: int
    empty: bool
    has_jesus_words: bool
    has_additions: bool
    text_sha256: str | None = None
    text_length: int | None = None

    @property
    def label(self) -> str:
        return str(self.start) if self.start == self.end else f"{self.start}-{self.end}"

    def covered(self) -> range:
        return range(self.start, self.end + 1)


@dataclass(frozen=True)
class HeadingMarker:
    before_verse: int
    kind: str
    text_sha256: str
    text_length: int


@dataclass(frozen=True)
class SpotHash:
    verse: int
    source_sha256: str | None
    source_length: int | None
    bridged: bool


@dataclass(frozen=True)
class ChapterStructure:
    book: str
    chapter: int
    markers: tuple[Marker, ...]
    headings: tuple[HeadingMarker, ...]
    spot_hashes: tuple[SpotHash, ...]
    canonical_url: str
    page_sha256: str


@dataclass(frozen=True)
class LocalMarker:
    start: int
    end: int
    plain: str
    has_jesus_words: bool
    has_additions: bool

    def covered(self) -> range:
        return range(self.start, self.end + 1)


@dataclass(frozen=True)
class PageMetadata:
    version_label: str
    copyright: str
    details_url: str
    canonical_url: str


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def run_id() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%SZ") + f"-{os.getpid()}"


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest().upper()


def sha256_text(value: str) -> str:
    return sha256_bytes(value.encode("utf-8"))


def normalized_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n"


def atomic_write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = normalized_json(value).encode("utf-8")
    descriptor, temporary_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        temporary.replace(path)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise


def read_json_object(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, ValueError) as exc:
        raise AuditFailure(f"cannot read JSON object {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise AuditFailure(f"JSON root is not an object: {path}")
    return payload


def normalized_text(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).split())


def plain_local_text(value: str, reference: str) -> tuple[str, bool, bool]:
    stack: list[str] = []
    parts: list[str] = []
    has_j = False
    has_add = False
    position = 0
    for match in MARKER_RE.finditer(value):
        parts.append(value[position:match.start()])
        marker = match.group(0)
        if marker in {"[J]", "[ADD]"}:
            kind = marker[1:-1]
            if kind in stack:
                raise AuditFailure(f"nested {marker} at {reference}")
            stack.append(kind)
            has_j |= kind == "J"
            has_add |= kind == "ADD"
        else:
            kind = marker[2:-1]
            if not stack or stack[-1] != kind:
                raise AuditFailure(f"unmatched or crossing {marker} at {reference}")
            stack.pop()
        position = match.end()
    parts.append(value[position:])
    if stack:
        raise AuditFailure(f"unclosed [{stack[-1]}] at {reference}")
    plain = normalized_text("".join(parts))
    if not plain:
        raise AuditFailure(f"empty local Scripture text at {reference}")
    return plain, has_j, has_add


class GatewayClient:
    def __init__(self, timeout: float, retries: int, delay: float) -> None:
        self.timeout = timeout
        self.retries = retries
        self.delay = delay
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Accept": "text/html,application/xhtml+xml",
                "Accept-Encoding": "gzip, deflate",
                "User-Agent": "BibleCompanion-Structural-Auditor/1.0 (+https://github.com/zoomdbz/BibleCompanion-MultiPlatform)",
            }
        )

    def get_html(self, path: str, params: Sequence[tuple[str, str]] = ()) -> tuple[str, str, str]:
        url = urljoin(BIBLEGATEWAY_BASE, path)
        expected_url = url + ("?" + urlencode(params) if params else "")
        last_error: Exception | None = None
        for attempt in range(self.retries + 1):
            if self.delay:
                time.sleep(self.delay)
            try:
                response = self.session.get(
                    url,
                    params=list(params),
                    timeout=self.timeout,
                    allow_redirects=False,
                    stream=True,
                )
                if response.is_redirect or response.is_permanent_redirect:
                    raise SourceError(
                        f"BibleGateway redirected {expected_url} to {response.headers.get('Location')!r}"
                    )
                body = response.raw.read(MAX_RESPONSE_BYTES + 1, decode_content=True)
                if len(body) > MAX_RESPONSE_BYTES:
                    raise SourceError(f"BibleGateway response exceeded 16 MiB: {expected_url}")
                if response.status_code == 429 or 500 <= response.status_code <= 599:
                    raise requests.HTTPError(f"retryable HTTP {response.status_code}", response=response)
                if response.status_code != 200:
                    raise SourceError(f"BibleGateway returned HTTP {response.status_code}: {expected_url}")
                content_type = response.headers.get("Content-Type", "").lower()
                if "text/html" not in content_type:
                    raise SourceError(f"BibleGateway returned non-HTML content {content_type!r}: {expected_url}")
                try:
                    html = body.decode("utf-8", errors="strict")
                except UnicodeError as exc:
                    raise SourceError(f"BibleGateway returned non-UTF-8 HTML: {expected_url}") from exc
                lowered = html.lower()
                if any(marker in lowered for marker in CHALLENGE_MARKERS):
                    raise SourceError(f"BibleGateway returned a browser challenge: {expected_url}")
                return html, sha256_bytes(body), expected_url
            except SourceError:
                raise
            except (requests.RequestException, OSError) as exc:
                last_error = exc
                if attempt >= self.retries:
                    break
                retry_after = getattr(getattr(exc, "response", None), "headers", {}).get("Retry-After", "")
                try:
                    pause = min(30.0, max(0.0, float(retry_after)))
                except (TypeError, ValueError):
                    pause = min(30.0, float(2**attempt))
                time.sleep(pause)
        raise SourceError(f"BibleGateway request failed after retries: {expected_url}: {last_error}")


def validate_cache_location(repo_root: Path, cache_dir: Path) -> None:
    expected = (repo_root / DEFAULT_CACHE).resolve()
    resolved = cache_dir.resolve()
    try:
        resolved.relative_to(expected)
    except ValueError:
        raise ConfigurationError(
            f"sanitized audit cache must stay under the gitignored {DEFAULT_CACHE} directory"
        ) from None


def sanitized_cache_path(cache_dir: Path, edition: Edition, book: str, chapter: int) -> Path:
    return cache_dir / "structure" / edition.gateway_code / book / f"{chapter}.json"


def marker_from_dict(value: dict[str, Any]) -> Marker:
    return Marker(
        int(value["start"]),
        int(value["end"]),
        bool(value["empty"]),
        bool(value["hasJesusWords"]),
        bool(value["hasAdditions"]),
        value.get("textSha256"),
        int(value["textLength"]) if value.get("textLength") is not None else None,
    )


def heading_from_dict(value: dict[str, Any]) -> HeadingMarker:
    return HeadingMarker(
        int(value["beforeVerse"]),
        str(value["kind"]),
        str(value["textSha256"]),
        int(value["textLength"]),
    )


def spot_from_dict(value: dict[str, Any]) -> SpotHash:
    return SpotHash(
        int(value["verse"]),
        value.get("sourceSha256"),
        value.get("sourceLength"),
        bool(value["bridged"]),
    )


def load_cached_structure(
    path: Path,
    edition: Edition,
    book: str,
    chapter: int,
    requested_spots: frozenset[int],
) -> tuple[ChapterStructure, PageMetadata] | None:
    if not path.is_file():
        return None
    payload = read_json_object(path)
    expected = {
        "schemaVersion": SCHEMA_VERSION,
        "edition": edition.gateway_code,
        "book": book,
        "chapter": chapter,
    }
    if any(payload.get(key) != value for key, value in expected.items()):
        raise ConfigurationError(f"incompatible sanitized cache record: {path}; use --refresh")
    available_spots = {int(item["verse"]) for item in payload.get("spotHashes", [])}
    if not requested_spots.issubset(available_spots):
        return None
    metadata = payload.get("metadata")
    if not isinstance(metadata, dict):
        raise ConfigurationError(f"sanitized cache record lacks metadata: {path}")
    markers = tuple(marker_from_dict(item) for item in payload.get("markers", []))
    allowed_source_gaps = KNOWN_SOURCE_GAPS.get((edition.gateway_code, book, chapter), frozenset())
    represented = {verse for marker in markers for verse in marker.covered()}
    if allowed_source_gaps - represented:
        markers = tuple(
            sorted(
                (*markers, *(Marker(verse, verse, False, False, False) for verse in allowed_source_gaps - represented)),
                key=lambda item: (item.start, item.end),
            )
        )
    structure = ChapterStructure(
        book,
        chapter,
        markers,
        tuple(heading_from_dict(item) for item in payload.get("headings", [])),
        tuple(spot_from_dict(item) for item in payload.get("spotHashes", [])),
        str(payload.get("canonicalUrl", "")),
        str(payload.get("pageSha256", "")),
    )
    page_metadata = PageMetadata(
        str(metadata.get("versionLabel", "")),
        str(metadata.get("copyright", "")),
        str(metadata.get("detailsUrl", "")),
        str(payload.get("canonicalUrl", "")),
    )
    return structure, page_metadata


def store_cached_structure(
    path: Path, edition: Edition, structure: ChapterStructure, metadata: PageMetadata
) -> None:
    payload = {
        "schemaVersion": SCHEMA_VERSION,
        "edition": edition.gateway_code,
        "book": structure.book,
        "chapter": structure.chapter,
        "canonicalUrl": structure.canonical_url,
        "pageSha256": structure.page_sha256,
        "metadata": {
            "versionLabel": metadata.version_label,
            "copyright": metadata.copyright,
            "detailsUrl": metadata.details_url,
        },
        "markers": [
            {
                "start": item.start,
                "end": item.end,
                "empty": item.empty,
                "hasJesusWords": item.has_jesus_words,
                "hasAdditions": item.has_additions,
                "textSha256": item.text_sha256,
                "textLength": item.text_length,
            }
            for item in structure.markers
        ],
        "headings": [
            {
                "beforeVerse": item.before_verse,
                "kind": item.kind,
                "textSha256": item.text_sha256,
                "textLength": item.text_length,
            }
            for item in structure.headings
        ],
        "spotHashes": [
            {
                "verse": item.verse,
                "sourceSha256": item.source_sha256,
                "sourceLength": item.source_length,
                "bridged": item.bridged,
            }
            for item in structure.spot_hashes
        ],
    }
    encoded = normalized_json(payload)
    # This assertion prevents an accidental future change from writing verse
    # text into the sanitized cache.
    forbidden_keys = {"text", "plain", "tagged", "html", "sourceText", "verseText"}
    if forbidden_keys.intersection(payload):
        raise AssertionError("source text field reached sanitized cache")
    atomic_write_json(path, payload)
    if normalized_json(read_json_object(path)) != encoded:
        raise AuditFailure(f"sanitized cache did not round-trip: {path}")


def parse_version_catalog(html: str) -> dict[str, tuple[str, str]]:
    soup = BeautifulSoup(html, "html.parser")
    select = soup.find("select", attrs={"name": "version"})
    if select is None:
        raise SourceError("BibleGateway version catalog has no version selector")
    language: str | None = None
    result: dict[str, tuple[str, str]] = {}
    for option in select.find_all("option"):
        text = normalized_text(option.get_text(" ", strip=True).replace("\xa0", " "))
        classes = set(option.get("class") or [])
        if "lang" in classes or (text.startswith("---") and text.endswith("---")):
            language = text.strip("-")
            continue
        if not text or "spacer" in classes or language is None:
            continue
        code = str(option.get("value", "")).strip()
        if not code:
            raise SourceError(f"BibleGateway catalog has an empty version code for {text!r}")
        previous = result.get(code)
        current = (language, text)
        if previous is not None and previous != current:
            raise SourceError(f"BibleGateway catalog has conflicting entries for {code}")
        result[code] = current
    if not result:
        raise SourceError("BibleGateway version catalog was empty")
    return result


def validate_catalog_entry(catalog: dict[str, tuple[str, str]], edition: Edition) -> None:
    actual = catalog.get(edition.gateway_code)
    expected = (edition.gateway_language, edition.gateway_label)
    if actual != expected:
        raise SourceError(
            f"{edition.language}: expected BibleGateway catalog {edition.gateway_code}={expected!r}, got {actual!r}"
        )


def ref_from_classes(tag: Tag, expected_prefix: str) -> tuple[int, int, int | None] | None:
    values: list[tuple[int, int, int | None]] = []
    escaped = re.escape(expected_prefix)
    pattern = re.compile(rf"^{escaped}-(\d+)-(\d+)(?:-{escaped}-(\d+)-(\d+))?$")
    for class_name in tag.get("class") or []:
        match = pattern.fullmatch(str(class_name))
        if match:
            chapter = int(match.group(1))
            start = int(match.group(2))
            end_chapter = int(match.group(3)) if match.group(3) else None
            end = int(match.group(4)) if match.group(4) else None
            if end_chapter is not None and (end_chapter != chapter or end is None or end < start):
                raise SourceError(f"cross-chapter or reversed BibleGateway range class {class_name!r}")
            values.append((chapter, start, end))
    if not values:
        return None
    if len(set(values)) != 1:
        raise SourceError(f"ambiguous BibleGateway reference classes: {tag.get('class')!r}")
    return values[0]


SKIP_CLASSES = frozenset(
    {
        "versenum", "chapternum", "footnote", "crossreference", "crossref-link",
        "full-chap-link", "footnote-link", "crossref-link",
    }
)
JESUS_CLASSES = frozenset({"woj", "red", "red-letter", "words-of-jesus", "jesus-words"})
ADDITION_CLASSES = frozenset({"add", "transchange", "translator-addition"})


def semantic_text(tag: Tag, reference: str) -> tuple[str, bool, bool]:
    parts: list[str] = []
    has_j = False
    has_add = False

    def walk(node: Tag | NavigableString) -> None:
        nonlocal has_j, has_add
        if isinstance(node, NavigableString):
            parts.append(str(node))
            return
        if not isinstance(node, Tag):
            return
        classes = {str(item).lower() for item in node.get("class") or []}
        if classes.intersection(SKIP_CLASSES):
            return
        style = str(node.get("style", "")).replace(" ", "").lower()
        j_scope = bool(classes.intersection(JESUS_CLASSES))
        add_scope = bool(classes.intersection(ADDITION_CLASSES))
        if "color:red" in style and not j_scope:
            raise SourceError(f"unclassified red-letter markup at {reference}")
        if j_scope and add_scope:
            raise SourceError(f"overlapping Jesus/addition semantic root at {reference}")
        has_j |= j_scope
        has_add |= add_scope
        if node.name == "br":
            parts.append(" ")
            return
        for child in node.children:
            walk(child)

    walk(tag)
    return normalized_text("".join(parts)), has_j, has_add


def heading_anchors(heading: Tag, expected_prefix: str, expected_chapter: int) -> tuple[int, ...]:
    anchors: set[int] = set()
    for span in heading.select("span.text"):
        reference = ref_from_classes(span, expected_prefix)
        if reference is not None and reference[0] == expected_chapter:
            anchors.add(reference[1])
    return tuple(sorted(anchors))


def heading_anchor(heading: Tag, expected_prefix: str, expected_chapter: int) -> int:
    anchors = heading_anchors(heading, expected_prefix, expected_chapter)
    if len(anchors) != 1:
        raise SourceError(
            f"heading has {len(anchors)} anchors instead of one for {expected_prefix}-{expected_chapter}"
        )
    return anchors[0]


def parse_page_metadata(soup: BeautifulSoup, edition: Edition) -> PageMetadata:
    passage_texts = soup.select(".passage-text")
    if len(passage_texts) != 1:
        raise SourceError(
            f"{edition.language}: expected one BibleGateway passage-text block, got {len(passage_texts)}"
        )
    version_roots = passage_texts[0].select(f".version-{edition.gateway_code}")
    if len(version_roots) != 1:
        raise SourceError(
            f"{edition.language}: expected one .version-{edition.gateway_code} root, got {len(version_roots)}"
        )
    publisher = soup.select_one(".publisher-info-bottom")
    if publisher is None:
        raise SourceError(f"{edition.language}: passage page has no publisher-info-bottom")
    strong = publisher.find("strong")
    if strong is None or normalized_text(strong.get_text(" ", strip=True)) != edition.gateway_label:
        actual = normalized_text(strong.get_text(" ", strip=True)) if strong else None
        raise SourceError(
            f"{edition.language}: expected version label {edition.gateway_label!r}, got {actual!r}"
        )
    copyright_tag = publisher.find("p")
    copyright_text = normalized_text(copyright_tag.get_text(" ", strip=True)) if copyright_tag else ""
    if not copyright_text:
        raise SourceError(f"{edition.language}: passage page has no copyright/public-domain attribution")
    details_link = strong.find("a")
    if details_link is None or not details_link.get("href"):
        raise SourceError(f"{edition.language}: passage page has no version details link")
    canonical = soup.find("link", rel="canonical")
    canonical_url = str(canonical.get("href", "")) if canonical else ""
    parsed = urlparse(canonical_url)
    versions = parse_qs(parsed.query).get("version", [])
    if versions != [edition.gateway_code]:
        raise SourceError(
            f"{edition.language}: canonical URL does not identify {edition.gateway_code}: {canonical_url!r}"
        )
    return PageMetadata(
        edition.gateway_label,
        copyright_text,
        urljoin(BIBLEGATEWAY_BASE, str(details_link["href"])),
        canonical_url,
    )


def parse_chapter_page(
    html: str,
    page_sha256: str,
    edition: Edition,
    book: str,
    chapter: int,
    spot_verses: frozenset[int],
    allow_missing: bool = False,
) -> tuple[ChapterStructure, PageMetadata] | None:
    soup = BeautifulSoup(html, "html.parser")
    passage_texts = soup.select(".passage-text")
    if not passage_texts:
        if allow_missing:
            title = normalized_text(soup.title.get_text(" ", strip=True)) if soup.title else ""
            if edition.gateway_code not in title:
                raise SourceError(
                    f"boundary probe returned neither a passage nor an identifiable {edition.gateway_code} result page"
                )
            return None
        raise SourceError(f"{edition.language}/{book}.{chapter}: BibleGateway returned no passage text")
    metadata = parse_page_metadata(soup, edition)
    passage = passage_texts[0]
    prefix = BG_PREFIXES[book]

    heading_tags: list[Tag] = []
    for tag in passage.find_all(re.compile(r"^h[1-6]$")):
        classes = set(tag.get("class") or [])
        contains_numbered_body = any(
            (edition.gateway_code, book, chapter, reference[1]) in NUMBERED_HEADING_VERSE_FIXES
            for span in tag.select("span.text")
            if (reference := ref_from_classes(span, prefix)) is not None and reference[0] == chapter
        )
        if "resources-header" not in classes and not contains_numbered_body:
            heading_tags.append(tag)

    body_spans: list[Tag] = []
    for span in passage.select("span.text"):
        heading_parent = span.find_parent(re.compile(r"^h[1-6]$"))
        if span.find_parent(class_="resources-section") is not None:
            continue
        reference = ref_from_classes(span, prefix)
        if reference is None or reference[0] != chapter:
            continue
        if heading_parent is not None and (
            edition.gateway_code, book, chapter, reference[1]
        ) not in NUMBERED_HEADING_VERSE_FIXES:
            continue
        body_spans.append(span)

    if not body_spans:
        if allow_missing:
            return None
        raise SourceError(f"{edition.language}/{book}.{chapter}: no exact chapter verse markers")

    groups: list[dict[str, Any]] = []
    active: dict[str, Any] | None = None
    seen_starts: set[int] = set()
    for span in body_spans:
        reference = ref_from_classes(span, prefix)
        assert reference is not None
        class_verse = reference[1]
        class_end = reference[2]
        plain_number_label: int | None = None
        labels = span.select("sup.versenum")
        if len(labels) > 1:
            raise SourceError(f"{edition.language}/{book}.{chapter}: multiple verse labels in one span")
        label_range: tuple[int, int] | None = None
        if labels:
            label_text = normalized_text(labels[0].get_text(" ", strip=True))
            label_match = VERSE_LABEL_RE.fullmatch(label_text)
            if label_match is None:
                raise SourceError(
                    f"{edition.language}/{book}.{chapter}: ambiguous verse label {label_text!r}"
                )
            label_range = (int(label_match.group(1)), int(label_match.group(2) or label_match.group(1)))
            if label_range[0] != class_verse or label_range[1] < label_range[0]:
                raise SourceError(
                    f"{edition.language}/{book}.{chapter}: label/class mismatch {label_text!r} vs {class_verse}"
                )
            if class_end is not None and class_end != label_range[1]:
                raise SourceError(
                    f"{edition.language}/{book}.{chapter}: range class ends at {class_end}, label ends at {label_range[1]}"
                )
        elif class_end is not None:
            # BibleGateway sometimes emits several fragments for one bridged
            # range and puts the visible range label on a later fragment. The
            # class itself still supplies an exact, same-chapter range.
            label_range = (class_verse, class_end)
        elif active is not None:
            repaired_verse = PLAIN_NUMBER_LABEL_FIXES.get(
                (edition.gateway_code, book, chapter, class_verse)
            )
            visible = normalized_text(span.get_text(" ", strip=True))
            if repaired_verse is not None and re.match(rf"^{repaired_verse}\s+", visible):
                class_verse = repaired_verse
                label_range = (repaired_verse, repaired_verse)
                plain_number_label = repaired_verse
        chapter_labels = span.select(".chapternum")
        if len(chapter_labels) > 1:
            raise SourceError(f"{edition.language}/{book}.{chapter}: multiple chapter labels in one span")
        begins_group = label_range is not None or bool(chapter_labels)
        if active is None:
            begins_group = True
        elif class_verse != active["start"]:
            begins_group = True

        if begins_group:
            start, end = label_range or (class_verse, class_verse)
            if active is not None and start == active["start"]:
                begins_group = False
            else:
                if start in seen_starts:
                    raise SourceError(
                        f"{edition.language}/{book}.{chapter}: noncontiguous duplicate verse marker {start}"
                    )
                if active is not None:
                    groups.append(active)
                active = {
                    "start": start,
                    "end": end,
                    "parts": [],
                    "hasJesusWords": False,
                    "hasAdditions": False,
                }
                seen_starts.add(start)
        assert active is not None
        if class_verse != active["start"]:
            raise SourceError(
                f"{edition.language}/{book}.{chapter}: continuation class {class_verse} does not match active group {active['start']}"
            )
        text, has_j, has_add = semantic_text(
            span, f"BibleGateway {edition.gateway_code} {book}.{chapter}.{active['start']}"
        )
        if plain_number_label is not None:
            text = re.sub(rf"^{plain_number_label}\s+", "", text, count=1)
        if text:
            active["parts"].append(text)
        active["hasJesusWords"] |= has_j
        active["hasAdditions"] |= has_add
    if active is not None:
        groups.append(active)

    if (edition.gateway_code, book, chapter) in NUMERIC_GROUP_ORDER_FIXES:
        groups.sort(key=lambda item: (item["start"], item["end"]))

    headings: list[HeadingMarker] = []
    numbered_psalm: tuple[Marker, ...] = ()
    first_body_start = groups[0]["start"]
    for heading in heading_tags:
        text, _has_j, _has_add = semantic_text(
            heading, f"BibleGateway heading {edition.gateway_code} {book}.{chapter}"
        )
        if not text:
            continue
        classes = set(heading.get("class") or [])
        if book == "psalms" and "psalm-title" in classes and first_body_start > 1:
            anchors = set(heading_anchors(heading, prefix, chapter))
            inferred_single = anchors == {first_body_start}
            explicit_range = anchors == set(range(1, first_body_start))
            if not (inferred_single or explicit_range) or numbered_psalm:
                raise SourceError(
                    f"{edition.language}/{book}.{chapter}: ambiguous numbered Psalm superscription"
                )
            numbered_psalm = (
                (Marker(
                    1, first_body_start - 1, False, False, False,
                    sha256_text(text), len(text),
                ),)
                if inferred_single
                else tuple(Marker(verse, verse, False, False, False) for verse in range(1, first_body_start))
            )
            continue
        anchor = heading_anchor(heading, prefix, chapter)
        headings.append(HeadingMarker(anchor, heading.name, sha256_text(text), len(text)))

    markers: list[Marker] = list(numbered_psalm)
    spot_hashes: list[SpotHash] = []
    for group in groups:
        text = normalized_text(" ".join(group["parts"]))
        marker = Marker(
            group["start"], group["end"], not bool(text),
            bool(group["hasJesusWords"]), bool(group["hasAdditions"]),
            None if not text else sha256_text(text), None if not text else len(text),
        )
        markers.append(marker)
        selected = sorted(set(marker.covered()).intersection(spot_verses))
        for verse in selected:
            bridged = marker.start != marker.end
            if len(text) > MAX_SPOT_CHECK_CHARS and not bridged:
                raise SourceError(
                    f"spot check {book}.{chapter}.{verse} exceeds {MAX_SPOT_CHECK_CHARS} characters"
                )
            spot_hashes.append(
                SpotHash(verse, None if bridged or not text else sha256_text(text), None if bridged else len(text), bridged)
            )

    allowed_source_gaps = KNOWN_SOURCE_GAPS.get((edition.gateway_code, book, chapter), frozenset())
    represented = {verse for marker in markers for verse in marker.covered()}
    markers.extend(
        Marker(verse, verse, False, False, False)
        for verse in sorted(allowed_source_gaps - represented)
    )
    markers.sort(key=lambda marker: (marker.start, marker.end))

    previous_end = 0
    covered: set[int] = set()
    for marker in markers:
        if marker.start <= previous_end or marker.end < marker.start:
            raise SourceError(
                f"{edition.language}/{book}.{chapter}: duplicate or out-of-order range {marker.label}"
            )
        previous_end = marker.end
        covered.update(marker.covered())
    empties = {verse for marker in markers if marker.empty for verse in marker.covered()}
    allowed_empty = {
        verse for candidate_book, candidate_chapter, verse in TRADITIONAL_NT_REFS
        if candidate_book == book and candidate_chapter == chapter
    }
    unexpected_empty = empties - allowed_empty
    if unexpected_empty:
        raise SourceError(
            f"{edition.language}/{book}.{chapter}: empty nontraditional verse markers {sorted(unexpected_empty)}"
        )
    missing_inventory = set(range(1, max(covered) + 1)) - covered if covered else set()
    if missing_inventory - allowed_source_gaps:
        raise SourceError(
            f"{edition.language}/{book}.{chapter}: noncontiguous verse inventory; "
            f"missing={sorted(missing_inventory)}"
        )
    requested_missing_spots = spot_verses - {item.verse for item in spot_hashes}
    if requested_missing_spots:
        raise SourceError(
            f"{edition.language}/{book}.{chapter}: requested spot markers absent {sorted(requested_missing_spots)}"
        )
    structure = ChapterStructure(
        book, chapter, tuple(markers), tuple(headings), tuple(spot_hashes),
        metadata.canonical_url, page_sha256,
    )
    return structure, metadata


def fetch_structure(
    client: GatewayClient,
    cache_dir: Path,
    edition: Edition,
    book: str,
    chapter: int,
    spot_verses: frozenset[int],
    refresh: bool,
    allow_missing: bool = False,
) -> tuple[ChapterStructure | None, PageMetadata | None, bool]:
    path = sanitized_cache_path(cache_dir, edition, book, chapter)
    if not refresh and not allow_missing:
        cached = load_cached_structure(path, edition, book, chapter, spot_verses)
        if cached is not None:
            return cached[0], cached[1], True
    search = f"{BOOK_SEARCH_NAMES[book]} {chapter}"
    html, page_hash, _request_url = client.get_html(
        "/passage/", (("search", search), ("version", edition.gateway_code))
    )
    parsed = parse_chapter_page(
        html, page_hash, edition, book, chapter, spot_verses, allow_missing=allow_missing
    )
    # Drop raw HTML before doing any file I/O.
    del html
    if parsed is None:
        return None, None, False
    structure, metadata = parsed
    if not allow_missing:
        store_cached_structure(path, edition, structure, metadata)
    return structure, metadata, False


def canonical_files(repo_root: Path, language: str) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for book, collection in BOOK_COLLECTION.items():
        path = repo_root / "shared" / "assets" / "books" / collection / language / f"{book}.json"
        if not path.is_file():
            raise AuditFailure(f"{language}: missing canonical asset {path.relative_to(repo_root)}")
        result[book] = path
    if len(result) != 66:
        raise AssertionError("canonical book map must contain 66 books")
    return result


def local_chapters(payload: dict[str, Any], language: str, book: str) -> dict[int, dict[str, Any]]:
    stories = payload.get("stories")
    if not isinstance(stories, list):
        raise AuditFailure(f"{language}/{book}: stories is not an array")
    asset_book_id = payload.get("id")
    if not isinstance(asset_book_id, str) or not asset_book_id:
        raise AuditFailure(f"{language}/{book}: missing book id")
    result: dict[int, dict[str, Any]] = {}
    prefix = f"{asset_book_id}-"
    for story in stories:
        if not isinstance(story, dict):
            raise AuditFailure(f"{language}/{book}: non-object story")
        story_id = story.get("id")
        if not isinstance(story_id, str) or not story_id.startswith(prefix) or not story_id[len(prefix):].isdigit():
            raise AuditFailure(f"{language}/{book}: invalid story ID {story_id!r}")
        chapter = int(story_id[len(prefix):])
        if chapter < 1 or chapter in result:
            raise AuditFailure(f"{language}/{book}: duplicate/invalid chapter {chapter}")
        if not isinstance(story.get("summaryBullets"), list):
            raise AuditFailure(f"{language}/{book}.{chapter}: missing summaryBullets array")
        if "headings" in story and not isinstance(story["headings"], list):
            raise AuditFailure(f"{language}/{book}.{chapter}: headings is not an array")
        result[chapter] = story
    if not result or set(result) != set(range(1, max(result) + 1)):
        raise AuditFailure(f"{language}/{book}: local chapter sequence is not contiguous from 1")
    return result


def parse_local_story(
    story: dict[str, Any], language: str, book: str, chapter: int
) -> tuple[tuple[LocalMarker, ...], tuple[int, ...]]:
    markers: list[LocalMarker] = []
    occupied: set[int] = set()
    for raw in story["summaryBullets"]:
        if not isinstance(raw, str):
            raise AuditFailure(f"{language}/{book}.{chapter}: non-string Scripture bullet")
        match = LOCAL_REFERENCE_RE.search(raw)
        if match is None:
            raise AuditFailure(f"{language}/{book}.{chapter}: bullet lacks one trailing reference")
        marked_chapter = int(match.group(1))
        start, end = int(match.group(2)), int(match.group(3) or match.group(2))
        if marked_chapter != chapter or start < 1 or end < start:
            raise AuditFailure(f"{language}/{book}.{chapter}: invalid local marker {match.group(0)!r}")
        plain, has_j, has_add = plain_local_text(
            raw[:match.start()].rstrip(), f"{language}/{book}.{chapter}.{start}-{end}"
        )
        for verse in range(start, end + 1):
            if verse in occupied:
                raise AuditFailure(f"{language}/{book}.{chapter}: duplicate local verse {verse}")
            occupied.add(verse)
        markers.append(LocalMarker(start, end, plain, has_j, has_add))
    headings: list[int] = []
    for item in story.get("headings", []):
        if not isinstance(item, dict) or not isinstance(item.get("beforeVerse"), int) or not isinstance(item.get("text"), str):
            raise AuditFailure(f"{language}/{book}.{chapter}: invalid heading object")
        if item["beforeVerse"] < 1 or item["beforeVerse"] not in occupied:
            raise AuditFailure(
                f"{language}/{book}.{chapter}: heading points outside a local verse at {item['beforeVerse']}"
            )
        plain_local_text(item["text"], f"{language}/{book}.{chapter} heading")
        headings.append(item["beforeVerse"])
    return tuple(markers), tuple(headings)


def covered_local(markers: Iterable[LocalMarker]) -> set[int]:
    return {verse for marker in markers for verse in marker.covered()}


def covered_source(markers: Iterable[Marker]) -> set[int]:
    return {verse for marker in markers if not marker.empty for verse in marker.covered()}


def compare_chapter(
    language: str,
    book: str,
    chapter: int,
    local_markers: tuple[LocalMarker, ...],
    local_headings: tuple[int, ...],
    source: ChapterStructure,
    spot_verses: frozenset[int],
    compare_jesus: bool,
    compare_additions: bool,
    exact_text: bool = False,
) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    local_covered = covered_local(local_markers)
    source_covered = covered_source(source.markers)
    missing = sorted(source_covered - local_covered)
    extras = sorted(local_covered - source_covered)
    allowed = {
        verse for candidate_book, candidate_chapter, verse in TRADITIONAL_NT_REFS
        if candidate_book == book and candidate_chapter == chapter
    }
    if missing:
        findings.append({"type": "missing-local-verses", "verses": missing})
    unexpected_extras = sorted(set(extras) - allowed)
    traditional_extras = sorted(set(extras).intersection(allowed))
    if unexpected_extras:
        findings.append({"type": "unexpected-local-verses", "verses": unexpected_extras})
    if traditional_extras:
        findings.append({"type": "traditional-local-verses-need-fallback-proof", "verses": traditional_extras})

    local_groups = [(item.start, item.end) for item in local_markers if set(item.covered()).issubset(source_covered)]
    source_groups = [(item.start, item.end) for item in source.markers if not item.empty]
    if local_groups != source_groups:
        findings.append(
            {
                "type": "verse-grouping-mismatch",
                "local": [f"{start}-{end}" if start != end else str(start) for start, end in local_groups],
                "source": [f"{start}-{end}" if start != end else str(start) for start, end in source_groups],
            }
        )

    source_heading_positions = tuple(item.before_verse for item in source.headings)
    if local_headings != source_heading_positions:
        findings.append(
            {
                "type": "heading-placement-mismatch",
                "local": list(local_headings),
                "source": list(source_heading_positions),
            }
        )

    local_by_verse = {verse: marker for marker in local_markers for verse in marker.covered()}
    source_by_verse = {
        verse: marker for marker in source.markers if not marker.empty for verse in marker.covered()
    }
    semantic_findings: list[dict[str, Any]] = []
    for verse in sorted(local_covered.intersection(source_covered)):
        local_marker, source_marker = local_by_verse[verse], source_by_verse[verse]
        differences: list[str] = []
        if compare_jesus and local_marker.has_jesus_words != source_marker.has_jesus_words:
            differences.append("J")
        if compare_additions and local_marker.has_additions != source_marker.has_additions:
            differences.append("ADD")
        if differences:
            semantic_findings.append({"verse": verse, "markers": differences})
    if semantic_findings:
        findings.append({"type": "semantic-marker-presence-mismatch", "verses": semantic_findings})

    if exact_text:
        local_by_range = {(item.start, item.end): item for item in local_markers}
        exact_mismatches: list[dict[str, Any]] = []
        exact_unavailable: list[str] = []
        for source_marker in source.markers:
            if source_marker.empty:
                continue
            label = source_marker.label
            local_marker = local_by_range.get((source_marker.start, source_marker.end))
            if local_marker is None:
                continue
            if source_marker.text_sha256 is None:
                exact_unavailable.append(label)
                continue
            local_hash = sha256_text(normalized_text(local_marker.plain))
            if local_hash != source_marker.text_sha256:
                exact_mismatches.append(
                    {
                        "range": label,
                        "localSha256": local_hash,
                        "sourceSha256": source_marker.text_sha256,
                        "localLength": len(normalized_text(local_marker.plain)),
                        "sourceLength": source_marker.text_length,
                    }
                )
        if exact_mismatches:
            findings.append({"type": "exact-text-mismatch", "ranges": exact_mismatches})
        if exact_unavailable:
            findings.append({"type": "exact-text-unavailable", "ranges": exact_unavailable})

    spot_by_verse = {item.verse: item for item in source.spot_hashes}
    for verse in sorted(spot_verses):
        source_spot = spot_by_verse.get(verse)
        local_marker = local_by_verse.get(verse)
        if source_spot is None or local_marker is None:
            findings.append({"type": "spot-check-unavailable", "verse": verse})
        elif source_spot.bridged or local_marker.start != local_marker.end:
            findings.append({"type": "spot-check-bridged-line", "verse": verse})
        else:
            local_hash = sha256_text(normalized_text(local_marker.plain))
            if local_hash != source_spot.source_sha256:
                findings.append(
                    {
                        "type": "spot-check-text-mismatch",
                        "verse": verse,
                        "localSha256": local_hash,
                        "sourceSha256": source_spot.source_sha256,
                        "sourceLength": source_spot.source_length,
                    }
                )
    for finding in findings:
        finding.update({"language": language, "book": book, "chapter": chapter})
    return findings


def edition_manifest(edition: Edition, metadata: PageMetadata | None) -> dict[str, Any]:
    result: dict[str, Any] = {
        "bibleGateway": {
            "code": edition.gateway_code,
            "label": edition.gateway_label,
            "languageGroup": edition.gateway_language,
        },
        "bibleComParity": {
            "id": edition.bible_com_id,
            "code": edition.bible_com_code,
            "sameEdition": edition.bible_com_same_edition,
            "scope": "chapter/verse numbering and external-link parity only",
        },
    }
    if metadata is not None:
        result["bibleGateway"].update(
            {
                "copyright": metadata.copyright,
                "detailsUrl": metadata.details_url,
            }
        )
    return result


def verify_traditional_fallbacks(
    client: GatewayClient,
    cache_dir: Path,
    catalog: dict[str, tuple[str, str]],
    edition: Edition,
    references: set[tuple[str, int, int]],
    refresh: bool,
) -> tuple[list[dict[str, Any]], dict[str, Any] | None, dict[str, int]]:
    stats = {"cacheHits": 0, "networkFetches": 0}
    if not references:
        return [], None, stats
    fallback = FALLBACK_EDITIONS.get(edition.language)
    if fallback is None:
        return (
            [
                {
                    "type": "no-native-traditional-biblegateway-fallback",
                    "reference": f"{book}.{chapter}.{verse}",
                }
                for book, chapter, verse in sorted(references)
            ],
            None,
            stats,
        )
    validate_catalog_entry(catalog, fallback)
    grouped: dict[tuple[str, int], set[int]] = {}
    for book, chapter, verse in references:
        grouped.setdefault((book, chapter), set()).add(verse)
    findings: list[dict[str, Any]] = []
    metadata: PageMetadata | None = None
    for (book, chapter), verses in sorted(grouped.items()):
        structure, current_metadata, hit = fetch_structure(
            client, cache_dir, fallback, book, chapter, frozenset(), refresh
        )
        stats["cacheHits" if hit else "networkFetches"] += 1
        if structure is None or current_metadata is None:
            raise SourceError(f"fallback {fallback.gateway_code} unexpectedly returned no {book}.{chapter}")
        metadata = current_metadata
        source_covered = covered_source(structure.markers)
        for verse in sorted(verses):
            if verse not in source_covered:
                findings.append(
                    {
                        "type": "traditional-fallback-missing-verse",
                        "reference": f"{book}.{chapter}.{verse}",
                        "fallback": fallback.gateway_code,
                    }
                )
    return findings, edition_manifest(fallback, metadata), stats


def audit_language(
    repo_root: Path,
    cache_dir: Path,
    client: GatewayClient,
    catalog: dict[str, tuple[str, str]],
    edition: Edition,
    spots: dict[tuple[str, int], frozenset[int]],
    refresh: bool,
    exact_text: bool,
) -> dict[str, Any]:
    validate_catalog_entry(catalog, edition)
    files = canonical_files(repo_root, edition.language)
    all_findings: list[dict[str, Any]] = []
    stats = {"cacheHits": 0, "networkFetches": 0, "books": 0, "chapters": 0, "verses": 0, "headings": 0}
    structures: dict[tuple[str, int], ChapterStructure] = {}
    locals_by_chapter: dict[tuple[str, int], tuple[tuple[LocalMarker, ...], tuple[int, ...]]] = {}
    metadata: PageMetadata | None = None

    for book, path in files.items():
        chapters = local_chapters(read_json_object(path), edition.language, book)
        source_present_chapters: set[int] = set()
        for chapter, story in chapters.items():
            spot_verses = spots.get((book, chapter), frozenset())
            structure, current_metadata, hit = fetch_structure(
                client, cache_dir, edition, book, chapter, spot_verses, refresh,
                allow_missing=False,
            )
            stats["cacheHits" if hit else "networkFetches"] += 1
            if structure is None or current_metadata is None:
                all_findings.append(
                    {"type": "source-missing-local-chapter", "language": edition.language, "book": book, "chapter": chapter}
                )
                continue
            metadata = current_metadata
            source_present_chapters.add(chapter)
            structures[(book, chapter)] = structure
            locals_by_chapter[(book, chapter)] = parse_local_story(story, edition.language, book, chapter)
            stats["chapters"] += 1
            stats["verses"] += len(covered_source(structure.markers))
            stats["headings"] += len(structure.headings)

        probe = max(chapters) + 1
        for _ in range(MAX_BOUNDARY_PROBES):
            structure, _probe_metadata, _hit = fetch_structure(
                client, cache_dir, edition, book, probe, frozenset(), True, allow_missing=True
            )
            stats["networkFetches"] += 1
            if structure is None:
                break
            all_findings.append(
                {"type": "source-extra-chapter", "language": edition.language, "book": book, "chapter": probe}
            )
            probe += 1
        else:
            raise SourceError(
                f"{edition.language}/{book}: more than {MAX_BOUNDARY_PROBES} unexpected source chapters"
            )
        expected_chapters = set(chapters)
        if source_present_chapters != expected_chapters:
            all_findings.append(
                {
                    "type": "chapter-inventory-mismatch",
                    "language": edition.language,
                    "book": book,
                    "missingFromSource": sorted(expected_chapters - source_present_chapters),
                }
            )
        stats["books"] += 1

    compare_jesus = any(
        marker.has_jesus_words for structure in structures.values() for marker in structure.markers
    )
    compare_additions = any(
        marker.has_additions for structure in structures.values() for marker in structure.markers
    )
    traditional_needing_proof: set[tuple[str, int, int]] = set()
    for (book, chapter), structure in structures.items():
        local_markers, local_headings = locals_by_chapter[(book, chapter)]
        chapter_findings = compare_chapter(
            edition.language, book, chapter, local_markers, local_headings, structure,
            spots.get((book, chapter), frozenset()), compare_jesus, compare_additions, exact_text,
        )
        all_findings.extend(chapter_findings)
        for finding in chapter_findings:
            if finding["type"] == "traditional-local-verses-need-fallback-proof":
                traditional_needing_proof.update(
                    (book, chapter, verse) for verse in finding["verses"]
                )

    fallback_findings, fallback_manifest, fallback_stats = verify_traditional_fallbacks(
        client, cache_dir, catalog, edition, traditional_needing_proof, refresh
    )
    all_findings.extend(
        {**item, "language": edition.language} for item in fallback_findings
    )
    stats["cacheHits"] += fallback_stats["cacheHits"]
    stats["networkFetches"] += fallback_stats["networkFetches"]
    return {
        "language": edition.language,
        "source": edition_manifest(edition, metadata),
        "traditionalFallback": fallback_manifest,
        "semanticCapabilities": {
            "jesusWordMarkupExposed": compare_jesus,
            "translatorAdditionMarkupExposed": compare_additions,
        },
        "exactTextCompared": exact_text,
        "counts": stats,
        "findings": all_findings,
    }


def parse_spot_checks(raw: str) -> dict[str, dict[tuple[str, int], frozenset[int]]]:
    result: dict[str, dict[tuple[str, int], set[int]]] = {}
    if not raw.strip():
        return {}
    for item in raw.split(","):
        match = re.fullmatch(r"([^:]+):([1-3]?[A-Z]{2,3})\.(\d+)\.(\d+)", item.strip())
        if match is None:
            raise ConfigurationError(
                f"invalid --spot-checks entry {item!r}; expected language:USFM.chapter.verse"
            )
        language, code, chapter, verse = match.group(1), match.group(2), int(match.group(3)), int(match.group(4))
        if language not in EDITIONS or code not in CODE_TO_BOOK or chapter < 1 or verse < 1:
            raise ConfigurationError(f"unsupported --spot-checks entry {item!r}")
        book = CODE_TO_BOOK[code]
        result.setdefault(language, {}).setdefault((book, chapter), set()).add(verse)
    total = sum(len(verses) for language in result.values() for verses in language.values())
    if total > 24:
        raise ConfigurationError("--spot-checks is limited to 24 short verses per run")
    return {
        language: {key: frozenset(verses) for key, verses in chapters.items()}
        for language, chapters in result.items()
    }


def selected_languages(raw: str) -> list[str]:
    languages = [item.strip() for item in raw.split(",") if item.strip()]
    if not languages:
        raise ConfigurationError("--languages selected no languages")
    unknown = sorted(set(languages) - set(EDITIONS))
    if unknown:
        raise ConfigurationError(f"unsupported languages: {', '.join(unknown)}")
    if len(languages) != len(set(languages)):
        raise ConfigurationError("--languages contains a duplicate")
    return languages


def self_test() -> None:
    sample = """
    <html><head><title>Matthew 17 NVI - Bible Gateway</title>
    <link rel="canonical" href="https://www.biblegateway.com/passage/?search=Mateo%2017&amp;version=NVI"></head>
    <body><div class="passage-text"><div class="passage-content"><div class="version-NVI result-text-style-normal text-html">
      <h3><span class="text Matt-17-1">A heading <sup class="crossreference">A</sup></span></h3>
      <p><span class="text Matt-17-1"><span class="chapternum">17</span> One</span>
      <span class="text Matt-17-2"><sup class="versenum">2</sup><span class="woj">Two</span></span>
      <span class="text Matt-17-2">continued</span>
      <span class="text Matt-17-3"><sup class="versenum">3-4</sup>Three and four</span></p>
    </div></div></div>
    <div class="publisher-info-bottom"><strong><a href="/versions/NVI/">Nueva Versión Internacional (NVI)</a></strong><p>Copyright test</p></div>
    </body></html>
    """
    parsed = parse_chapter_page(sample, sha256_text(sample), EDITIONS["es"], "matthew", 17, frozenset({2}))
    assert parsed is not None
    structure, metadata = parsed
    assert [(item.start, item.end) for item in structure.markers] == [(1, 1), (2, 2), (3, 4)]
    assert structure.markers[1].has_jesus_words
    assert [item.before_verse for item in structure.headings] == [1]
    assert structure.spot_hashes[0].source_sha256 == sha256_text("Two continued")
    assert metadata.version_label == EDITIONS["es"].gateway_label
    assert len(BOOK_CODES) == len(BOOK_SEARCH_NAMES) == len(BG_PREFIXES) == 66
    assert len(TRADITIONAL_NT_REFS) == 16


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--languages", default=",".join(EDITIONS), help="Comma-separated app languages")
    parser.add_argument(
        "--cache-dir", type=Path, default=repo_root / DEFAULT_CACHE,
        help="Gitignored directory for sanitized structure only; raw HTML is never cached",
    )
    parser.add_argument(
        "--spot-checks", default="de:EPH.6.10",
        help="Comma-separated short exact checks as language:USFM.chapter.verse; maximum 24",
    )
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--retries", type=int, default=3)
    parser.add_argument("--delay", type=float, default=0.15, help="Delay before each network request")
    parser.add_argument("--refresh", action="store_true", help="Ignore sanitized structure cache")
    parser.add_argument(
        "--exact-text", action="store_true",
        help="Hash-compare every same-range local Scripture line with the selected BibleGateway edition",
    )
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.timeout <= 0 or args.retries < 0 or args.delay < 0:
        parser.error("timeout must be positive; retries and delay must be nonnegative")
    return args


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    if args.self_test:
        try:
            self_test()
        except (AssertionError, AuditFailure, ValueError) as exc:
            print(f"Localized Scripture structural auditor self-test failed: {exc}", file=sys.stderr)
            return 2
        print("Localized Scripture structural auditor self-test passed; no network requests were made.")
        return 0

    current_run = run_id()
    cache_dir = args.cache_dir.resolve()
    output = cache_dir / "runs" / current_run / "audit-manifest.json"
    manifest: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "runId": current_run,
        "generatedAt": utc_now(),
        "status": "running",
        "mode": "read-only structural/reference audit",
        "source": BIBLEGATEWAY_BASE,
        "rawHtmlCached": False,
        "scriptureTextWritten": False,
        "languages": [],
        "errors": [],
    }
    try:
        validate_cache_location(repo_root, cache_dir)
        languages = selected_languages(args.languages)
        spots = parse_spot_checks(args.spot_checks)
        if set(spots) - set(languages):
            raise ConfigurationError("--spot-checks includes a language not selected by --languages")
        client = GatewayClient(args.timeout, args.retries, args.delay)
        catalog_html, _catalog_hash, _catalog_url = client.get_html("/versions/")
        catalog = parse_version_catalog(catalog_html)
        del catalog_html
        for language in languages:
            report = audit_language(
                repo_root, cache_dir, client, catalog, EDITIONS[language], spots.get(language, {}),
                args.refresh, args.exact_text,
            )
            manifest["languages"].append(report)
            atomic_write_json(output, manifest)
        finding_count = sum(len(item["findings"]) for item in manifest["languages"])
        manifest["status"] = "findings" if finding_count else "clean"
        manifest["totals"] = {
            "languages": len(languages),
            "findings": finding_count,
            "books": sum(item["counts"]["books"] for item in manifest["languages"]),
            "chapters": sum(item["counts"]["chapters"] for item in manifest["languages"]),
            "verses": sum(item["counts"]["verses"] for item in manifest["languages"]),
        }
        atomic_write_json(output, manifest)
    except (AuditFailure, OSError, UnicodeError, ValueError, requests.RequestException) as exc:
        manifest["status"] = "blocked"
        manifest["errors"].append({"type": type(exc).__name__, "message": str(exc)})
        atomic_write_json(output, manifest)
        print(f"Localized Scripture structural audit blocked: {exc}", file=sys.stderr)
        print(f"Audit manifest: {output}", file=sys.stderr)
        return 2

    print(
        f"Localized Scripture structural audit {manifest['status']}: "
        f"{manifest['totals']['languages']} languages, {manifest['totals']['findings']} findings."
    )
    print(f"Audit manifest: {output}")
    print("No Scripture or app assets were changed; raw source HTML was not cached.")
    return 1 if manifest["totals"]["findings"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
