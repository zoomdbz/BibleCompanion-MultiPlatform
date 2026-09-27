#!/usr/bin/env python3
"""Audit or narrowly sync one modern-edition chapter from a browser snapshot.

The input is one JSON object per line on stdin, or one POST /snapshot request
over loopback. Each object names a supported language, USFM book, chapter,
rendered Bible.com URL/title, and ordered heading/verse DOM records. Publisher
text stays in memory. Results contain only references, counts, and hashes.

Without --apply this command never writes an asset. With --apply it may replace
only the Scripture prefix of a target story's summaryBullets entries. Every
trailing native marker and every byte outside those prefixes remains exact.
Native bridged/plus-joined source ranges remain one app bullet. Changed tagged
verses use rendered source [J], [ADD], and [DN] spans as authoritative. A local
tag absent from the rendered source survives only under exact plain-text
identity or a position-preserving Unicode space substitution.
"""

from __future__ import annotations

import argparse
from copy import deepcopy
from dataclasses import dataclass
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import unicodedata
from typing import Any, Iterable, TextIO
from urllib.parse import unquote, urlsplit

from bs4 import BeautifulSoup, NavigableString, Tag

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_base_heading_tables import TRAILING_MARKER_RE, parse_trailing_marker  # noqa: E402
from compare_modern_editions import BOOKS, EDITIONS  # noqa: E402
from compare_modern_editions_browser import (  # noqa: E402
    BrowserAuditError,
    LATIN_EXPLICIT_DN,
    RUNTIME_DN_FORMS,
    _classes,
    _is_skipped,
    _range_from_usfm,
    _runtime_covers_untagged_dn,
    _space_separator_fold,
    extract_browser_records,
)

ROOT = Path(__file__).resolve().parents[2]
BOOK_INDEX = {code: (collection, book_id) for code, collection, book_id in BOOKS}
# The checked-in corpus predates a single root-id convention. These are the
# exact legacy aliases present in the canonical assets; accepting a derived
# normalization here would let a misspelled or unrelated payload identity pass
# merely because its chapter story used the same misspelling.
BOOK_ROOT_ID_ALIASES: dict[str, frozenset[str]] = {
    "1_samuel": frozenset({"1-samuel"}),
    "2_samuel": frozenset({"2-samuel"}),
    "1_kings": frozenset({"1-kings"}),
    "2_kings": frozenset({"2-kings"}),
    "1_chronicles": frozenset({"1chronicles"}),
    "2_chronicles": frozenset({"2chronicles"}),
    "song_of_songs": frozenset({"song-of-songs"}),
    "1_corinthians": frozenset({"1-corinthians"}),
}
APP_TAG = re.compile(r"\[(/?)(J|ADD|DN)\]")
MAX_SNAPSHOT = 8_000_000
# Rendered poetry can split one native verse into several DOM fragments.
# Psalm 119 currently reaches 559 records while remaining far below the
# snapshot byte cap, so 500 rejects a valid canonical chapter.
MAX_RECORDS = 2_000
DECODER = json.JSONDecoder()
REVIEWED_REPLACEMENT_PATH = Path(__file__).with_name("reviewed_native_chapter_replacements.json")

# Keep these deliberately narrow. CSS-module names on the rendered Bible.com
# page contain forms such as ChapterContent_wj__... and ChapterContent_nd__....
SEMANTIC_CLASS = {
    "J": frozenset({"wj", "red", "red-letter", "redletter", "jesus-words", "words-of-jesus"}),
    "ADD": frozenset({"add", "transchange", "translator-addition"}),
    "DN": frozenset({"nd"}),
}

# Full lexical contents accepted inside a rendered Bible.com ``__nd`` span.
# DOM markup proves the semantic role, but not that the publisher wrapper has
# a narrow boundary. Keep this list aligned with the locale forms that the app
# can normalize in ScriptureRefs.kt. Outer Unicode punctuation and separators
# are allowed by _is_proven_source_divine_name; ordinary surrounding words are
# never trimmed or inferred.
SOURCE_DN_LEXEMES: dict[str, frozenset[str]] = {
    "en": frozenset({"lord", "god", "yahweh", "yhwh", "yhvh", "yahuah", "yahveh", "jehovah", "jah"}),
    # ``Gott`` and ``Gottes`` are publisher-marked ``nd`` in SCH2000. They are
    # intentionally accepted only here, after the rendered wrapper proves their
    # semantic role; neither may become a global runtime synonym for the divine
    # name.
    "de": frozenset({"herr", "herrn", "gott", "gottes", "jahwe", "jehova"}),
    # NVI Exodus 3:14 marks both self-identification phrases with __nd. These
    # phrases are valid only with the publisher wrapper; never infer them from
    # ordinary unmarked Spanish text or replace their wording with YHWH.
    "es": frozenset({"señor", "jehová", "yahveh", "yahvé", "yo soy", "yo soy el que soy"}),
    # NBS Genesis 15:2,8 marks ``Dieu`` as ``nd``. As with German Gottes,
    # rendered publisher markup is mandatory; unmarked Dieu remains ordinary
    # source text and is never inferred to be the Tetragrammaton.
    "fr": frozenset({"éternel", "seigneur", "dieu", "yahvé", "yahveh"}),
    "it": frozenset({"signore", "geova"}),
    "pt": frozenset({"senhor", "javé", "jeová"}),
    "ru": frozenset({
        "яхве", "иегова", "господь", "господа", "господу", "господом", "господе",
        "господень", "господнее", "господнего", "господнему", "господним", "господнем",
        "господних", "господняя", "господнюю", "господней", "господня", "господню",
        "господне", "господни",
    }),
    "ar": frozenset({"الرب", "يهوه"}),
    "hi": frozenset({"यहोवा", "याहवे", "प्रभु"}),
    "ja": frozenset({"ヤハウェ", "ヱホバ", "エホバ", "主"}),
    "ko": frozenset({"여호와", "야훼", "주님", "주"}),
    "zh-Hans": frozenset({"耶和华", "雅威", "上主"}),
    "zh-Hant": frozenset({"耶和華", "雅威", "上主"}),
}


class ScriptureSyncError(ValueError):
    """The source snapshot or local asset cannot support a safe narrow sync."""


class DivineNameCoverageError(ScriptureSyncError):
    """A wording update would weaken runtime divine-name behavior."""


@dataclass(frozen=True)
class Boundary:
    kind: str
    opening: bool
    offset: int
    order: int
    marker: str


@dataclass(frozen=True)
class TaggedText:
    plain: str
    boundaries: tuple[Boundary, ...]


@dataclass(frozen=True)
class Token:
    text: str
    start: int
    end: int


@dataclass(frozen=True)
class RawReplacement:
    start: int
    end: int
    value: str
    original_suffix: str


def _positive(value: object) -> bool:
    return type(value) is int and value > 0


def _sha_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _sha_json(value: object) -> str:
    data = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return _sha_bytes(data)


def _load_reviewed_native_chapter_replacements() -> dict[tuple[str, str, int], dict[str, Any]]:
    payload = json.loads(REVIEWED_REPLACEMENT_PATH.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or payload.get("schemaVersion") != 1:
        raise ScriptureSyncError("reviewed native replacement ledger schema invalid")
    entries = payload.get("entries")
    if not isinstance(entries, list):
        raise ScriptureSyncError("reviewed native replacement ledger entries invalid")
    required = {
        "language", "bibleId", "reference", "collection", "bookId",
        "expectedLocalStorySha256", "expectedLocalRangeCount",
        "expectedLocalAppTagOpenCounts", "reason",
    }
    result: dict[tuple[str, str, int], dict[str, Any]] = {}
    for row in entries:
        if not isinstance(row, dict) or set(row) != required:
            raise ScriptureSyncError("reviewed native replacement row fields invalid")
        match = re.fullmatch(r"([1-3]?[A-Z]{2,3})\.(\d+)", str(row["reference"]))
        language = row["language"]
        tag_counts = row["expectedLocalAppTagOpenCounts"]
        if (
            match is None
            or language not in EDITIONS
            or type(row["bibleId"]) is not int
            or row["bibleId"] != EDITIONS[language].bible_id
            or row["collection"] not in {"old_testament", "new_testament"}
            or not isinstance(row["bookId"], str)
            or not re.fullmatch(r"[a-z0-9_]+", row["bookId"])
            or not re.fullmatch(r"[0-9a-f]{64}", str(row["expectedLocalStorySha256"]))
            or not _positive(row["expectedLocalRangeCount"])
            or not isinstance(tag_counts, dict)
            or set(tag_counts) != {"J", "ADD", "DN"}
            or not all(type(value) is int and value >= 0 for value in tag_counts.values())
            or not isinstance(row["reason"], str)
            or not row["reason"].strip()
        ):
            raise ScriptureSyncError("reviewed native replacement row value invalid")
        code, chapter = match.group(1), int(match.group(2))
        if BOOK_INDEX.get(code) != (row["collection"], row["bookId"]):
            raise ScriptureSyncError("reviewed native replacement book identity invalid")
        key = (language, code, chapter)
        if key in result:
            raise ScriptureSyncError("duplicate reviewed native replacement row")
        result[key] = row
    return result


REVIEWED_NATIVE_CHAPTER_REPLACEMENTS = _load_reviewed_native_chapter_replacements()


def _reviewed_native_chapter_replacement(
    language: str,
    code: str,
    chapter: int,
    collection: str,
    book_id: str,
    story: dict[str, Any],
    local_range_count: int,
) -> bool:
    row = REVIEWED_NATIVE_CHAPTER_REPLACEMENTS.get((language, code, chapter))
    if row is None:
        return False
    if (row["collection"], row["bookId"], row["expectedLocalRangeCount"]) != (
        collection, book_id, local_range_count
    ):
        raise ScriptureSyncError("reviewed native replacement local identity changed")
    # The ledger authorizes only the exact historical story. Once the chapter
    # has been replaced, or if any unrelated edit changes it, fall back to the
    # ordinary fail-closed path rather than reusing the exception.
    if _sha_json(story) != row["expectedLocalStorySha256"]:
        return False
    bullets = story.get("summaryBullets")
    if not isinstance(bullets, list) or not all(isinstance(value, str) for value in bullets):
        raise ScriptureSyncError("reviewed native replacement local bullets invalid")
    counts = {
        kind: sum(value.count(f"[{kind}]") for value in bullets)
        for kind in ("J", "ADD", "DN")
    }
    if counts != row["expectedLocalAppTagOpenCounts"]:
        raise ScriptureSyncError("reviewed native replacement local tag inventory changed")
    return True


def _identity(snapshot: dict[str, Any], root: Path) -> tuple[str, int, str, str, str, Path]:
    language, code, chapter = snapshot.get("language"), snapshot.get("book"), snapshot.get("chapter")
    if language not in EDITIONS or code not in BOOK_INDEX or not _positive(chapter):
        raise ScriptureSyncError("unknown language, book, or chapter")
    edition = EDITIONS[language]
    supplied_id = snapshot.get("bibleId")
    if supplied_id is not None and (type(supplied_id) is not int or supplied_id != edition.bible_id):
        raise ScriptureSyncError("Bible edition identity mismatch")
    url = snapshot.get("pageUrl")
    title = snapshot.get("pageTitle")
    if not isinstance(url, str):
        raise ScriptureSyncError("missing rendered browser page URL")
    if (not isinstance(title, str) or not title.strip()
            or re.search(r"client challenge|captcha|verify you are human", title, re.I)):
        raise ScriptureSyncError("browser page title is missing or challenged")
    parsed = urlsplit(url)
    valid_paths = {
        f"/bible/{edition.bible_id}/{code}.{chapter}.{abbreviation}".lower()
        for abbreviation in (edition.abbreviation, *edition.published_abbreviation_aliases)
    }
    parts = unquote(parsed.path).lower().rstrip("/").split("/")
    if len(parts) == 5 and re.fullmatch(r"[a-z]{2}(?:-[a-z]{2})?", parts[1]):
        parts.pop(1)
    normalized_path = "/".join(parts)
    if (parsed.scheme != "https" or parsed.hostname not in {"www.bible.com", "bible.com"}
            or parsed.username or parsed.password or parsed.port not in {None, 443}
            or normalized_path not in valid_paths):
        raise ScriptureSyncError("source URL does not identify the declared passage and edition")
    collection, book_id = BOOK_INDEX[code]
    path = root / "shared" / "assets" / "books" / collection / language / f"{book_id}.json"
    return language, chapter, code, collection, book_id, path


def _local_story(payload: Any, book_id: str, chapter: int) -> tuple[int, dict[str, Any], list[tuple[int, int]]]:
    # The repository predates one ID convention. File/code identity is
    # authoritative; real roots include 1chronicles, 1-samuel, 1_timothy, and
    # song-of-songs. Require a nonblank root and bind the target story exactly
    # to that root instead of guessing an underscore/hyphen normalization.
    if (not isinstance(payload, dict) or not isinstance(payload.get("id"), str)
            or not payload["id"].strip() or not isinstance(payload.get("stories"), list)):
        raise ScriptureSyncError("local book identity or structure mismatch")
    root_id = payload["id"]
    accepted_root_ids = {book_id, *BOOK_ROOT_ID_ALIASES.get(book_id, ())}
    if root_id not in accepted_root_ids:
        raise ScriptureSyncError("local book root does not identify the requested book")
    matches: list[tuple[int, dict[str, Any], list[tuple[int, int]]]] = []
    for story_index, story in enumerate(payload["stories"]):
        if not isinstance(story, dict) or not isinstance(story.get("summaryBullets"), list):
            raise ScriptureSyncError("invalid local story")
        if story.get("id") != f"{root_id}-{chapter}":
            continue
        units: list[tuple[int, int]] = []
        for bullet_index, bullet in enumerate(story["summaryBullets"]):
            marker = parse_trailing_marker(bullet, bullet_index)
            if marker.unit is None or marker.unit.chapter != chapter:
                raise ScriptureSyncError("target story has a nonnative or cross-chapter marker")
            units.append((marker.unit.start, marker.unit.end))
        if not units or units != sorted(units):
            raise ScriptureSyncError("target story has missing or unordered native ranges")
        if any(start < 1 or end < start for start, end in units):
            raise ScriptureSyncError("target story has an invalid native range")
        if any(units[index][0] <= units[index - 1][1] for index in range(1, len(units))):
            raise ScriptureSyncError("target story has overlapping native ranges")
        matches.append((story_index, story, units))
    if len(matches) != 1:
        raise ScriptureSyncError("target chapter must have exactly one matching story")
    return matches[0]


def _parse_tagged(value: str) -> TaggedText:
    if not isinstance(value, str):
        raise ScriptureSyncError("Scripture prefix is not text")
    pieces: list[str] = []
    boundaries: list[Boundary] = []
    stack: list[str] = []
    cursor = 0
    plain_offset = 0
    for order, match in enumerate(APP_TAG.finditer(value)):
        literal = value[cursor:match.start()]
        pieces.append(literal)
        plain_offset += len(literal)
        opening = not bool(match[1])
        kind = match[2]
        if opening:
            if kind in stack:
                raise ScriptureSyncError("nested duplicate app tag")
            stack.append(kind)
        elif not stack or stack[-1] != kind:
            raise ScriptureSyncError("unmatched or crossing app tag")
        else:
            stack.pop()
        boundaries.append(Boundary(kind, opening, plain_offset, order, match.group(0)))
        cursor = match.end()
    pieces.append(value[cursor:])
    if stack:
        raise ScriptureSyncError("unclosed app tag")
    return TaggedText("".join(pieces), tuple(boundaries))


def _is_cjk_character(character: str) -> bool:
    name = unicodedata.name(character, "")
    return name.startswith(("CJK ", "HIRAGANA ", "KATAKANA ", "IDEOGRAPHIC "))


def _tokens(value: str) -> tuple[Token, ...]:
    result: list[Token] = []
    start = 0
    while start < len(value):
        character = value[start]
        if _is_cjk_character(character):
            end = start + 1
        elif character.isspace():
            end = start + 1
            while end < len(value) and value[end].isspace():
                end += 1
        elif unicodedata.category(character)[0] in {"L", "M", "N"}:
            end = start + 1
            while (end < len(value) and not _is_cjk_character(value[end])
                   and unicodedata.category(value[end])[0] in {"L", "M", "N"}):
                end += 1
        else:
            end = start + 1
        result.append(Token(value[start:end], start, end))
        start = end
    return tuple(result)


def _boundary_context(tokens: tuple[Token, ...], offset: int) -> tuple[bool, tuple[str, ...], tuple[str, ...], bool]:
    positions = [0, *(token.end for token in tokens)]
    if offset not in positions:
        raise ScriptureSyncError("app tag splits a literal source token")
    index = positions.index(offset)
    left = tuple(token.text for token in tokens[max(0, index - 2):index])
    right = tuple(token.text for token in tokens[index:index + 2])
    if not left and not right:
        raise ScriptureSyncError("app tag has no literal anchor")
    return index == 0, left, right, index == len(tokens)


def _map_boundaries(local: TaggedText, source_plain: str) -> tuple[Boundary, ...]:
    local_tokens, source_tokens = _tokens(local.plain), _tokens(source_plain)
    source_offsets = [0, *(token.end for token in source_tokens)]
    mapped: list[Boundary] = []
    for boundary in local.boundaries:
        signature = _boundary_context(local_tokens, boundary.offset)
        candidates = [
            offset for offset in source_offsets
            if _boundary_context(source_tokens, offset) == signature
        ]
        if len(candidates) != 1:
            raise ScriptureSyncError("app tag boundary lacks a unique literal-token source anchor")
        mapped.append(Boundary(boundary.kind, boundary.opening, candidates[0], boundary.order, boundary.marker))
    offsets = [boundary.offset for boundary in mapped]
    if offsets != sorted(offsets):
        raise ScriptureSyncError("mapped app tag boundaries changed order")
    return tuple(mapped)


def _map_spacing_only_boundaries(local: TaggedText, source_plain: str) -> tuple[Boundary, ...] | None:
    """Map boundaries by offset only for a one-to-one Unicode Zs change."""
    if local.plain == source_plain:
        return None
    if _space_separator_fold(local.plain) != _space_separator_fold(source_plain):
        return None
    # _space_separator_fold replaces each Zs code point with exactly one ASCII
    # space. Equality therefore proves an equal-length, position-preserving map
    # and excludes every word, punctuation, case, and order change.
    if len(local.plain) != len(source_plain):
        raise ScriptureSyncError("spacing-only normalization changed text length")
    return tuple(
        Boundary(item.kind, item.opening, item.offset, item.order, item.marker)
        for item in local.boundaries
    )


def _semantic_spans(tagged: TaggedText, kind: str) -> tuple[tuple[int, int], ...]:
    opened: int | None = None
    spans: list[tuple[int, int]] = []
    for boundary in tagged.boundaries:
        if boundary.kind != kind:
            continue
        if boundary.opening:
            if opened is not None:
                raise DivineNameCoverageError("nested source divine-name boundary")
            opened = boundary.offset
        else:
            if opened is None or boundary.offset <= opened:
                raise DivineNameCoverageError("invalid source divine-name boundary")
            spans.append((opened, boundary.offset))
            opened = None
    if opened is not None:
        raise DivineNameCoverageError("unclosed source divine-name boundary")
    return tuple(spans)


def _source_divine_name_lexeme_span(value: str, language: str) -> tuple[int, int] | None:
    """Return the exact lexeme inside one publisher-marked P/Z wrapper."""
    start, end = 0, len(value)

    def surrounding(character: str) -> bool:
        return character.isspace() or unicodedata.category(character)[0] in {"P", "Z"}

    while start < end and surrounding(value[start]):
        start += 1
    while end > start and surrounding(value[end - 1]):
        end -= 1
    if start == end:
        return None
    core = value[start:end]
    # Arabic source editions retain variable vowel marks around the same two
    # lexical forms. Other supported scripts need their exact code points.
    if language == "ar":
        core = "".join(character for character in core if unicodedata.category(character) != "Mn")
    return (start, end) if core.casefold() in SOURCE_DN_LEXEMES.get(language, ()) else None


def _is_proven_source_divine_name(value: str, language: str) -> bool:
    """Accept one locale-specific divine-name lexeme plus outer P/Z text."""
    return _source_divine_name_lexeme_span(value, language) is not None


def _proven_source_dn_spans(tagged: TaggedText, language: str) -> tuple[tuple[int, int], ...]:
    proven: list[tuple[int, int]] = []
    for start, end in _semantic_spans(tagged, "DN"):
        relative = _source_divine_name_lexeme_span(tagged.plain[start:end], language)
        if relative is None:
            raise DivineNameCoverageError(
                "rendered divine-name span contains unproven lexical content"
            )
        lexical_start, lexical_end = relative
        proven.append((start + lexical_start, start + lexical_end))
    return tuple(proven)


def _literal_occurrences(value: str, literal: str) -> Iterable[tuple[int, int]]:
    position = 0
    while literal and (found := value.find(literal, position)) >= 0:
        yield found, found + len(literal)
        position = found + 1


def _runtime_divine_name_spans(language: str, text: str, collection: str) -> tuple[tuple[int, int], ...]:
    """Enumerate only literal spans recognized by the current runtime policy."""
    tokens = _tokens(text)
    boundaries = {0, *(token.end for token in tokens)}
    candidates: set[tuple[int, int]] = set()

    # Exact runtime literals, including contextual mixed-case forms handled by
    # _runtime_covers_untagged_dn. Keep this list beside the imported runtime
    # policy rather than creating a second recognition policy.
    literals = set(RUNTIME_DN_FORMS.get(language, ()))
    literals.update({
        "de": {"Herrn"},
        "es": {"Señor"},
        "fr": {"Seigneur"},
        "ko": {"주님"},
        "zh-Hans": {"上主"},
        "zh-Hant": {"上主"},
    }.get(language, set()))
    for literal in literals:
        for start, end in _literal_occurrences(text, literal):
            if start in boundaries and end in boundaries and _runtime_covers_untagged_dn(
                    language, text, start, end, collection):
                candidates.add((start, end))

    # Explicit Latin forms are case-insensitive in ScriptureRefs.kt. Token
    # boundaries prevent matching YHWH or Jehovah inside a larger word.
    for token in tokens:
        if token.text.casefold() in LATIN_EXPLICIT_DN and _runtime_covers_untagged_dn(
                language, text, token.start, token.end, collection):
            candidates.add((token.start, token.end))

    ordered = tuple(sorted(candidates))
    if any(ordered[index][0] < ordered[index - 1][1] for index in range(1, len(ordered))):
        raise DivineNameCoverageError("overlapping runtime divine-name spans")
    return ordered


def _preserve_runtime_divine_names(local_plain: str, source_plain: str, source_dn: TaggedText,
                                   language: str, collection: str) -> tuple[str, int]:
    """Prove each existing runtime sentinel has one safe source counterpart.

    Exact source spelling remains exact. A presentation/case change is safe
    only when rendered ``__nd`` markup identifies the counterpart; the caller
    then emits an explicit ``[DN]`` span instead of rewriting publisher text.
    """
    source_dn_spans = set(_proven_source_dn_spans(source_dn, language))
    local_spans = _runtime_divine_name_spans(language, local_plain, collection)
    if not local_spans:
        return source_plain, 0

    source_tokens = _tokens(source_plain)
    source_boundaries = {0, *(token.end for token in source_tokens)}
    candidate_lists: list[list[tuple[int, int]]] = []
    for local_start, local_end in local_spans:
        local_value = local_plain[local_start:local_end]
        candidates: set[tuple[int, int]] = set()
        # Exact sentinel text is safe only when the runtime still recognizes
        # it in the source context, or rendered __nd independently marks it.
        for start, end in _literal_occurrences(source_plain, local_value):
            if (start in source_boundaries and end in source_boundaries
                    and ((start, end) in source_dn_spans or _runtime_covers_untagged_dn(
                        language, source_plain, start, end, collection))):
                candidates.add((start, end))
        # A case/presentation change needs independent rendered DN evidence.
        for start, end in source_dn_spans:
            source_value = source_plain[start:end]
            if (source_value != local_value and source_value.casefold() == local_value.casefold()
                    and start in source_boundaries and end in source_boundaries
                    and len(source_value) == len(local_value)):
                candidates.add((start, end))
        if not candidates:
            raise DivineNameCoverageError("runtime divine-name span has no safe source alignment")
        candidate_lists.append(sorted(candidates))

    # A former runtime sentinel can map to more than one same-looking source
    # occurrence after a publisher wording change. Ordinarily that is
    # ambiguous. It is harmless when every possible target is independently
    # marked ``nd`` by the rendered publisher source: the reconstruction below
    # will wrap every one of those authoritative spans, so no local guess
    # controls the result and runtime coverage cannot be weakened.
    all_candidates_source_marked = bool(candidate_lists) and all(
        candidates and all(candidate in source_dn_spans for candidate in candidates)
        for candidates in candidate_lists
    )

    # Count ordered, non-overlapping full assignments. More than one is
    # ambiguous, even if every individual token looks plausible.
    solutions: list[tuple[tuple[int, int], ...]] = []

    def search(index: int, previous_end: int, selected: list[tuple[int, int]]) -> None:
        if len(solutions) > 1:
            return
        if index == len(candidate_lists):
            solutions.append(tuple(selected))
            return
        for candidate in candidate_lists[index]:
            if candidate[0] < previous_end:
                continue
            selected.append(candidate)
            search(index + 1, candidate[1], selected)
            selected.pop()

    search(0, 0, [])
    if not solutions:
        raise DivineNameCoverageError("runtime divine-name spans have no ordered source alignment")
    if len(solutions) != 1 and all_candidates_source_marked:
        return source_plain, len(local_spans)
    if len(solutions) != 1:
        raise DivineNameCoverageError("runtime divine-name source alignment is ambiguous")

    for (local_start, local_end), (source_start, source_end) in zip(local_spans, solutions[0]):
        local_value = local_plain[local_start:local_end]
        source_value = source_plain[source_start:source_end]
        if source_value == local_value:
            continue
        if ((source_start, source_end) not in source_dn_spans
                or source_value.casefold() != local_value.casefold()):
            raise DivineNameCoverageError("divine-name presentation change lacks rendered boundary")
    return source_plain, len(local_spans)


def _class_has_semantic(tag: Tag, kind: str) -> bool:
    classes = _classes(tag)
    if classes.intersection(SEMANTIC_CLASS[kind]):
        return True
    stem = {"J": "wj", "ADD": "add", "DN": "nd"}[kind]
    # Cover both element-style modules (x__nd) and CSS-module exports
    # (ChapterContent_wj__hash) without accepting an arbitrary substring.
    pattern = re.compile(rf"(?:^|_){stem}(?:__|$)")
    return any(pattern.search(name) for name in classes)


def _semantic_node_text(node: Tag, kind: str, *, normalize: bool = False) -> str:
    # Match compare_modern_editions_browser._node_text boundary behavior while
    # retaining only the requested semantic wrapper.
    from compare_biblecom import BLOCK_BOUNDARY, INLINE_BOUNDARY, normalize_boundary_whitespace

    pieces: list[str] = []

    def walk(item: Tag | NavigableString, active: bool = False) -> None:
        if isinstance(item, NavigableString):
            pieces.append(str(item))
            return
        if _is_skipped(item):
            return
        if item.name == "br":
            pieces.append(BLOCK_BOUNDARY)
            return
        pieces.append(INLINE_BOUNDARY)
        semantic = _class_has_semantic(item, kind) and not active
        if semantic:
            pieces.append(f"[{kind}]")
        for child in item.children:
            if isinstance(child, (Tag, NavigableString)):
                walk(child, active or semantic)
        if semantic:
            pieces.append(f"[/{kind}]")
        pieces.append(INLINE_BOUNDARY)

    walk(node)
    value = "".join(pieces)
    return normalize_boundary_whitespace(value) if normalize else value


def _align_rendered_semantic_whitespace(tagged: TaggedText, source_plain: str,
                                        kind: str) -> TaggedText:
    """Align source-proven wrappers when markup boundaries move only spaces.

    Plain and semantic extraction walk the same rendered DOM. A [J] boundary
    between whitespace-bearing HTML spans can prevent the normalizer from
    collapsing those spaces, even though removing the wrapper yields the
    correct publisher text. Anchor each wrapper to its exact ordered
    non-whitespace characters in the independently extracted plain text.
    No differing letter or punctuation can pass this check.
    """
    if tagged.plain == source_plain:
        return tagged
    source_chars = [(index, char) for index, char in enumerate(source_plain) if not char.isspace()]
    tagged_chars = [(index, char) for index, char in enumerate(tagged.plain) if not char.isspace()]
    if not source_chars or [char for _, char in tagged_chars] != [char for _, char in source_chars]:
        raise ScriptureSyncError("semantic source extraction changed Scripture text")
    boundaries: list[Boundary] = []
    for start, end in _semantic_spans(tagged, kind):
        ranks = [rank for rank, (index, _) in enumerate(tagged_chars) if start <= index < end]
        if not ranks:
            raise ScriptureSyncError("semantic source wrapper contains only whitespace")
        source_start = source_chars[ranks[0]][0]
        source_end = source_chars[ranks[-1]][0] + 1
        boundaries.append(Boundary(kind, True, source_start, len(boundaries), f"[{kind}]"))
        boundaries.append(Boundary(kind, False, source_end, len(boundaries), f"[/{kind}]"))
    return TaggedText(source_plain, tuple(boundaries))


def _source_semantics(records: list[dict[str, str]], book: str, chapter: int,
                      expected: dict[tuple[int, int], str]) -> dict[str, dict[tuple[int, int], TaggedText]]:
    from compare_biblecom import BLOCK_BOUNDARY, normalize_boundary_whitespace

    fragments: dict[str, dict[tuple[int, int], list[str]]] = {kind: {} for kind in SEMANTIC_CLASS}
    last_key: tuple[int, int] | None = None
    last_end = 0
    for record in records:
        if record.get("kind") != "verse":
            continue
        html_fragment = record.get("html")
        if not isinstance(html_fragment, str):
            raise ScriptureSyncError("invalid browser snapshot fragment")
        key = _range_from_usfm(record.get("usfm", ""), book, chapter)
        if key is None:
            continue
        if key != last_key and key[0] <= last_end:
            raise ScriptureSyncError("duplicate, overlapping, or unordered native ranges")
        node = BeautifulSoup(html_fragment, "html.parser").find(True)
        if node is None:
            raise ScriptureSyncError("empty browser snapshot fragment")
        if key != last_key:
            for kind in fragments:
                fragments[kind][key] = []
            last_end = key[1]
        for kind in fragments:
            fragments[kind][key].append(_semantic_node_text(node, kind))
        last_key = key
    output: dict[str, dict[tuple[int, int], TaggedText]] = {kind: {} for kind in fragments}
    for kind, range_fragments in fragments.items():
        for key, parts in range_fragments.items():
            markup = normalize_boundary_whitespace(BLOCK_BOUNDARY.join(parts))
            parsed = _parse_tagged(markup)
            source_plain = expected.get(key)
            if isinstance(source_plain, str) and parsed.plain != source_plain:
                parsed = _align_rendered_semantic_whitespace(parsed, source_plain, kind)
            if parsed.plain != source_plain:
                raise ScriptureSyncError(
                    f"semantic source extraction changed Scripture text at {book}.{chapter}.{key[0]}-{key[1]} ({kind})"
                )
            output[kind][key] = parsed
    return output


def _confirmed_tagged_replacement(local_prefix: str, source_plain: str,
                                  semantics: dict[str, TaggedText], *, language: str,
                                  collection: str) -> tuple[str, int, int]:
    if APP_TAG.search(source_plain):
        raise ScriptureSyncError("source text contains an app control tag")
    local = _parse_tagged(local_prefix)
    _, runtime_dn_count = _preserve_runtime_divine_names(
        local.plain, source_plain, semantics["DN"], language, collection
    )

    # Rendered semantic spans are authoritative and may introduce or move an
    # app wrapper while the publisher text changes. A local-only wrapper may
    # survive under an identity map or a length-preserving Unicode Zs
    # substitution. A single J wrapper covering the entire native range may
    # also survive a Unicode-space-run-only change: its boundaries are the
    # independently verified range boundaries, while lexical changes still
    # require rendered source evidence or a separately reviewed ledger.
    fallback_mapped: tuple[Boundary, ...] | None = None
    spans: list[tuple[int, int, str]] = []
    for kind in SEMANTIC_CLASS:
        source_tagged = semantics.get(kind)
        if source_tagged is None or source_tagged.plain != source_plain:
            raise ScriptureSyncError("semantic source extraction changed Scripture text")
        source_spans = (
            _proven_source_dn_spans(source_tagged, language)
            if kind == "DN"
            else _semantic_spans(source_tagged, kind)
        )
        if source_spans:
            spans.extend((start, end, kind) for start, end in source_spans)
            continue
        if not any(boundary.kind == kind for boundary in local.boundaries):
            continue
        local_kind_spans = _semantic_spans(local, kind)
        if (
            kind == "J"
            and source_plain
            and local_kind_spans == ((0, len(local.plain)),)
            and re.sub(r" +", " ", _space_separator_fold(local.plain)).strip()
            == re.sub(r" +", " ", _space_separator_fold(source_plain)).strip()
        ):
            spans.append((0, len(source_plain), kind))
            continue
        if local.plain == source_plain:
            fallback_mapped = local.boundaries
        elif fallback_mapped is None:
            fallback_mapped = _map_spacing_only_boundaries(local, source_plain)
        if fallback_mapped is None:
            raise ScriptureSyncError("local app tag lacks rendered or text-identity proof")
        remapped = TaggedText(
            source_plain,
            tuple(boundary for boundary in fallback_mapped if boundary.kind == kind),
        )
        spans.extend((start, end, kind) for start, end in _semantic_spans(remapped, kind))

    events: dict[int, list[tuple[int, int, str]]] = {}
    seen: set[tuple[int, int, str]] = set()
    kinds = tuple(SEMANTIC_CLASS)
    for start, end, kind in spans:
        span = (start, end, kind)
        if span in seen:
            raise ScriptureSyncError("duplicate rendered semantic span")
        seen.add(span)
        if not 0 <= start < end <= len(source_plain):
            raise ScriptureSyncError("invalid rendered semantic span")
        events.setdefault(start, []).append((1, end, kind))
        events.setdefault(end, []).append((0, start, kind))

    pieces: list[str] = []
    active: list[tuple[int, int, str]] = []
    cursor = 0
    for offset in sorted(events):
        pieces.append(source_plain[cursor:offset])
        closing = sorted(
            (item for item in events[offset] if item[0] == 0),
            key=lambda item: (-item[1], -kinds.index(item[2])),
        )
        for _, start, kind in closing:
            if not active or active[-1] != (start, offset, kind):
                raise ScriptureSyncError("crossing rendered semantic spans")
            active.pop()
            pieces.append(f"[/{kind}]")
        opening = sorted(
            (item for item in events[offset] if item[0] == 1),
            key=lambda item: (-item[1], kinds.index(item[2])),
        )
        for _, end, kind in opening:
            active.append((offset, end, kind))
            pieces.append(f"[{kind}]")
        cursor = offset
    if active:
        raise ScriptureSyncError("unclosed rendered semantic span")
    pieces.append(source_plain[cursor:])
    replacement = "".join(pieces)
    reparsed = _parse_tagged(replacement)
    if reparsed.plain != source_plain:
        raise ScriptureSyncError("tag reconstruction changed source text")
    return replacement, len(local.boundaries), runtime_dn_count


def _skip_ws(text: str, position: int) -> int:
    while position < len(text) and text[position] in " \t\r\n":
        position += 1
    return position


def _object_fields(text: str, start: int) -> dict[str, tuple[int, int]]:
    if start >= len(text) or text[start] != "{":
        raise ScriptureSyncError("expected JSON object")
    position = start + 1
    fields: dict[str, tuple[int, int]] = {}
    while True:
        position = _skip_ws(text, position)
        if position >= len(text):
            raise ScriptureSyncError("unterminated JSON object")
        if text[position] == "}":
            return fields
        key, position = DECODER.raw_decode(text, position)
        if not isinstance(key, str) or key in fields:
            raise ScriptureSyncError("invalid or duplicate JSON key")
        position = _skip_ws(text, position)
        if position >= len(text) or text[position] != ":":
            raise ScriptureSyncError("invalid JSON field separator")
        value_start = _skip_ws(text, position + 1)
        _, value_end = DECODER.raw_decode(text, value_start)
        fields[key] = (value_start, value_end)
        position = _skip_ws(text, value_end)
        if position >= len(text):
            raise ScriptureSyncError("unterminated JSON object")
        if text[position] == "}":
            return fields
        if text[position] != ",":
            raise ScriptureSyncError("invalid JSON object separator")
        position += 1


def _array_item_spans(text: str, start: int) -> list[tuple[int, int]]:
    if start >= len(text) or text[start] != "[":
        raise ScriptureSyncError("expected JSON array")
    result: list[tuple[int, int]] = []
    position = start + 1
    while True:
        position = _skip_ws(text, position)
        if position >= len(text):
            raise ScriptureSyncError("unterminated JSON array")
        if text[position] == "]":
            return result
        item_start = position
        _, position = DECODER.raw_decode(text, position)
        result.append((item_start, position))
        position = _skip_ws(text, position)
        if position >= len(text):
            raise ScriptureSyncError("unterminated JSON array")
        if text[position] == "]":
            return result
        if text[position] != ",":
            raise ScriptureSyncError("invalid JSON array separator")
        position += 1


def _story_start(text: str, story_index: int) -> int:
    root_start = _skip_ws(text, 1 if text.startswith("\ufeff") else 0)
    root_fields = _object_fields(text, root_start)
    if "stories" not in root_fields:
        raise ScriptureSyncError("asset lacks stories array")
    stories = _array_item_spans(text, root_fields["stories"][0])
    if story_index >= len(stories):
        raise ScriptureSyncError("story index is out of range")
    return stories[story_index][0]


def _decoded_offsets(token: str, expected: str) -> tuple[int, ...]:
    """Map decoded Python-string offsets to raw offsets in one JSON token."""
    if len(token) < 2 or token[0] != '"' or token[-1] != '"' or json.loads(token) != expected:
        raise ScriptureSyncError("summary bullet is not the expected JSON string")
    offsets = [1]
    decoded: list[str] = []
    position = 1
    while position < len(token) - 1:
        if token[position] != "\\":
            character = token[position]
            if ord(character) < 32:
                raise ScriptureSyncError("raw control character in JSON string")
            decoded.append(character)
            position += 1
        else:
            if position + 1 >= len(token) - 1:
                raise ScriptureSyncError("truncated JSON escape")
            escape = token[position + 1]
            if escape in '"\\/bfnrt':
                decoded.append(json.loads('"' + token[position:position + 2] + '"'))
                position += 2
            elif escape == "u" and position + 6 <= len(token) - 1:
                first = int(token[position + 2:position + 6], 16)
                if 0xD800 <= first <= 0xDBFF:
                    if position + 12 > len(token) - 1 or token[position + 6:position + 8] != "\\u":
                        raise ScriptureSyncError("unpaired high surrogate in JSON string")
                    second = int(token[position + 8:position + 12], 16)
                    if not 0xDC00 <= second <= 0xDFFF:
                        raise ScriptureSyncError("invalid surrogate pair in JSON string")
                    decoded.append(chr(0x10000 + ((first - 0xD800) << 10) + second - 0xDC00))
                    position += 12
                elif 0xDC00 <= first <= 0xDFFF:
                    raise ScriptureSyncError("unpaired low surrogate in JSON string")
                else:
                    decoded.append(chr(first))
                    position += 6
            else:
                raise ScriptureSyncError("invalid JSON string escape")
        offsets.append(position)
    if "".join(decoded) != expected:
        raise ScriptureSyncError("JSON string offset mapping mismatch")
    return tuple(offsets)


def _raw_replacements(raw: bytes, story_index: int, bullets: list[str], replacements: list[str]) -> list[RawReplacement]:
    text = raw.decode("utf-8")
    story_fields = _object_fields(text, _story_start(text, story_index))
    if "summaryBullets" not in story_fields:
        raise ScriptureSyncError("target story lacks summaryBullets")
    spans = _array_item_spans(text, story_fields["summaryBullets"][0])
    if len(spans) != len(bullets) or len(replacements) != len(bullets):
        raise ScriptureSyncError("raw and parsed bullet counts differ")
    result: list[RawReplacement] = []
    for index, ((start, end), bullet, replacement_prefix) in enumerate(zip(spans, bullets, replacements)):
        token = text[start:end]
        if json.loads(token) != bullet:
            raise ScriptureSyncError("raw bullet span does not match parsed asset")
        marker = TRAILING_MARKER_RE.search(bullet)
        if marker is None:
            raise ScriptureSyncError("target bullet lacks a trailing native marker")
        offsets = _decoded_offsets(token, bullet)
        suffix_start = offsets[marker.start()]
        suffix = token[suffix_start:]
        encoded = json.dumps(replacement_prefix, ensure_ascii=False, separators=(",", ":"))
        if APP_TAG.search(source_literal := _parse_tagged(replacement_prefix).plain):
            raise ScriptureSyncError("replacement source contains an app control tag")
        del source_literal  # Parsed above solely to validate reconstructed tags.
        replacement_token = encoded[:-1] + suffix
        if json.loads(replacement_token)[-len(bullet[marker.start():]):] != bullet[marker.start():]:
            raise ScriptureSyncError("replacement did not preserve trailing marker")
        if replacement_token != token:
            result.append(RawReplacement(start, end, replacement_token, suffix))
    return result


def _apply_raw_replacements(raw: bytes, replacements: list[RawReplacement]) -> tuple[bytes, str, str]:
    text = raw.decode("utf-8")
    previous = 0
    pieces: list[str] = []
    untouched: list[str] = []
    marker_suffixes: list[str] = []
    for replacement in sorted(replacements, key=lambda item: item.start):
        if replacement.start < previous or replacement.end > len(text):
            raise ScriptureSyncError("overlapping or out-of-bounds raw replacement")
        segment = text[previous:replacement.start]
        pieces.extend((segment, replacement.value))
        untouched.append(segment)
        marker_suffixes.append(replacement.original_suffix)
        previous = replacement.end
    tail = text[previous:]
    pieces.append(tail)
    untouched.append(tail)
    updated_text = "".join(pieces)
    # The digest is a compact proof over length-prefixed untouched segments;
    # concatenation ambiguity cannot make a different segment list collide.
    proof = _sha_json([_sha_bytes(item.encode("utf-8")) for item in untouched])
    marker_proof = _sha_json([_sha_bytes(item.encode("utf-8")) for item in marker_suffixes])
    return updated_text.encode("utf-8"), proof, marker_proof


def sync_snapshot(snapshot: dict[str, Any], *, root: Path = ROOT, apply: bool = False) -> dict[str, Any]:
    language, chapter, code, collection, book_id, path = _identity(snapshot, root)
    records = snapshot.get("records")
    if (not isinstance(records, list) or len(records) > MAX_RECORDS
            or not all(isinstance(record, dict) for record in records)):
        raise ScriptureSyncError("snapshot record limit or structure invalid")
    raw = path.read_bytes()
    payload = json.loads(raw.decode("utf-8-sig"))
    story_index, story, local_ranges = _local_story(payload, book_id, chapter)
    source = extract_browser_records(records, code, chapter)
    if list(source.ranges) != local_ranges:
        raise ScriptureSyncError("source and local native range inventories differ")
    semantics = _source_semantics(records, code, chapter, source.ranges)
    reviewed_native_replacement = _reviewed_native_chapter_replacement(
        language, code, chapter, collection, book_id, story, len(local_ranges)
    )

    bullets = story["summaryBullets"]
    replacement_prefixes: list[str] = []
    source_difference_ranges = 0
    presentation_only_ranges = 0
    changed_ranges = 0
    tagged_changed_ranges = 0
    preserved_boundaries = 0
    preserved_runtime_dn_spans = 0
    local_prefix_hashes: list[str] = []
    source_prefix_hashes: list[str] = []
    for bullet, key in zip(bullets, local_ranges):
        marker = TRAILING_MARKER_RE.search(bullet)
        if marker is None:
            raise ScriptureSyncError("target bullet lacks a trailing native marker")
        local_prefix = bullet[:marker.start()]
        source_plain = source.ranges[key]
        local_tagged = _parse_tagged(local_prefix)
        local_prefix_hashes.append(_sha_json(local_prefix))
        source_prefix_hashes.append(_sha_json(source_plain))
        if local_tagged.plain != source_plain:
            source_difference_ranges += 1
        source_by_kind = {kind: semantics[kind][key] for kind in semantics}
        replacement, boundary_count, runtime_dn_count = _confirmed_tagged_replacement(
            source_plain if reviewed_native_replacement else local_prefix,
            source_plain,
            source_by_kind,
            language=language,
            collection=collection,
        )
        replacement_prefixes.append(replacement)
        preserved_runtime_dn_spans += runtime_dn_count
        if boundary_count:
            preserved_boundaries += boundary_count
        if replacement == local_prefix:
            presentation_only_ranges += 1
            continue
        changed_ranges += 1
        if boundary_count:
            tagged_changed_ranges += 1

    raw_replacements = _raw_replacements(raw, story_index, bullets, replacement_prefixes)
    updated, untouched_proof, marker_proof = _apply_raw_replacements(raw, raw_replacements)
    if len(raw_replacements) != changed_ranges:
        raise ScriptureSyncError("parsed and raw changed range counts differ")

    expected = deepcopy(payload)
    expected_bullets = expected["stories"][story_index]["summaryBullets"]
    for index, (bullet, prefix) in enumerate(zip(bullets, replacement_prefixes)):
        marker = TRAILING_MARKER_RE.search(bullet)
        expected_bullets[index] = prefix + bullet[marker.start():]
    checked = json.loads(updated.decode("utf-8-sig"))
    if checked != expected:
        raise ScriptureSyncError("raw replacement changed a non-target JSON value")
    for before, after in zip(bullets, expected_bullets):
        old_marker = TRAILING_MARKER_RE.search(before)
        if old_marker is None:
            raise ScriptureSyncError("trailing marker changed")
        # The replacement prefix may legitimately end in publisher whitespace.
        # Re-running the marker regex on the updated bullet would absorb that
        # whitespace into its leading ``\s*`` and falsely report that the marker
        # changed.  The raw writer already preserves the original suffix byte for
        # byte, so verify that exact suffix directly instead.
        old_suffix = before[old_marker.start():]
        if not after.endswith(old_suffix):
            raise ScriptureSyncError("trailing marker changed")
        if parse_trailing_marker(before, 0) != parse_trailing_marker(after, 0):
            raise ScriptureSyncError("trailing marker changed")

    changed = updated != raw
    result = {
        "status": "changed" if changed else "match",
        "mode": "apply" if apply else "audit",
        "language": language,
        "bibleId": EDITIONS[language].bible_id,
        "book": code,
        "chapter": chapter,
        "collection": collection,
        "nativeRangeCount": len(local_ranges),
        "sourceDifferenceRangeCount": source_difference_ranges,
        "preservedPresentationOnlyRangeCount": presentation_only_ranges,
        "changedRangeCount": changed_ranges,
        "taggedChangedRangeCount": tagged_changed_ranges,
        "preservedTagBoundaryCount": preserved_boundaries,
        "preservedRuntimeDivineNameSpanCount": preserved_runtime_dn_spans,
        "beforeSha256": _sha_bytes(raw),
        "afterSha256": _sha_bytes(updated),
        "localPrefixSetSha256": _sha_json(local_prefix_hashes),
        "sourcePrefixSetSha256": _sha_json(source_prefix_hashes),
        "untouchedRawSegmentsSha256": untouched_proof,
        "preservedRawMarkersSha256": marker_proof,
    }
    if apply and changed:
        if path.read_bytes() != raw:
            raise ScriptureSyncError("local asset changed during audit")
        temporary: str | None = None
        try:
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".scripture-", suffix=".tmp", delete=False) as handle:
                temporary = handle.name
                handle.write(updated)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)
    return result


SAFE_EXCEPTIONS = (
    ScriptureSyncError, BrowserAuditError, OSError, UnicodeError, ValueError,
    KeyError, IndexError, TypeError,
)


def _blocked_result(exc: BaseException) -> dict[str, str]:
    """Expose only controlled internal causes, never source/parser messages."""
    result = {"status": "blocked", "errorType": type(exc).__name__}
    # These two exact classes are raised only with fixed messages authored in
    # this tool. Browser/parser/JSON/OS messages may contain source fragments,
    # paths, or request data and must never become an error code.
    if type(exc) in {ScriptureSyncError, DivineNameCoverageError}:
        code = re.sub(r"[^a-z0-9]+", "_", str(exc).lower()).strip("_")
        if code:
            result["errorCode"] = code[:120]
    return result


def stream_snapshots(source: TextIO, sink: TextIO, *, root: Path = ROOT, apply: bool = False) -> int:
    status = 0
    for line in source:
        if not line.strip():
            continue
        try:
            if len(line) > MAX_SNAPSHOT:
                raise ScriptureSyncError("snapshot exceeds size limit")
            snapshot = json.loads(line)
            if not isinstance(snapshot, dict):
                raise ScriptureSyncError("snapshot must be a JSON object")
            result = sync_snapshot(snapshot, root=root, apply=apply)
        except SAFE_EXCEPTIONS as exc:
            # A malformed snapshot may place publisher text in an exception.
            # Only its class and allowlisted internal cause cross the boundary.
            result = _blocked_result(exc)
            status = 2
        sink.write(json.dumps(result, ensure_ascii=False, sort_keys=True) + "\n")
        sink.flush()
    return status


def create_snapshot_server(port: int, *, root: Path = ROOT, apply: bool = False) -> HTTPServer:
    """Accept sequential in-memory snapshots on loopback without storage."""

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format: str, *_args: object) -> None:
            return

        def _respond(self, code: int, payload: dict[str, Any]) -> None:
            body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self) -> None:
            self._respond(200, {"status": "ok"}) if self.path == "/health" else self._respond(404, {"status": "not_found"})

        def do_POST(self) -> None:
            if self.path != "/snapshot":
                self._respond(404, {"status": "not_found"})
                return
            try:
                length_text = self.headers.get("Content-Length", "")
                if not length_text.isdecimal() or not 1 <= int(length_text) <= MAX_SNAPSHOT:
                    raise ScriptureSyncError("invalid request length")
                if self.headers.get_content_type() != "application/json":
                    raise ScriptureSyncError("invalid content type")
                length = int(length_text)
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise ScriptureSyncError("incomplete request body")
                snapshot = json.loads(raw.decode("utf-8"))
                if not isinstance(snapshot, dict):
                    raise ScriptureSyncError("snapshot must be a JSON object")
                result = sync_snapshot(snapshot, root=root, apply=apply)
            except SAFE_EXCEPTIONS as exc:
                result = _blocked_result(exc)
            self._respond(200, result)

    return HTTPServer(("127.0.0.1", port), Handler)


def serve_snapshots(port: int, *, root: Path = ROOT, apply: bool = False) -> None:
    server = create_snapshot_server(port, root=root, apply=apply)
    try:
        print(f"Scripture snapshot server listening on 127.0.0.1:{server.server_port}", flush=True)
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="update only verified Scripture prefixes")
    parser.add_argument("--root", type=Path, default=ROOT, help="repository root")
    parser.add_argument("--snapshot-server", action="store_true", help="accept snapshots over loopback POST /snapshot")
    parser.add_argument("--snapshot-stdin", action="store_true", help="read one snapshot JSON object instead of JSON Lines")
    parser.add_argument("--port", type=int, default=9227, help="loopback server port; default 9227")
    args = parser.parse_args()
    if args.snapshot_server:
        if args.snapshot_stdin or not 1 <= args.port <= 65535:
            parser.error("--snapshot-server requires a valid --port and cannot use --snapshot-stdin")
        try:
            serve_snapshots(args.port, root=args.root, apply=args.apply)
        except OSError:
            print("Scripture snapshot server could not bind loopback port", file=sys.stderr)
            return 2
        return 0
    if args.snapshot_stdin:
        try:
            snapshot = json.load(sys.stdin)
            if not isinstance(snapshot, dict):
                raise ScriptureSyncError("snapshot must be a JSON object")
            result = sync_snapshot(snapshot, root=args.root, apply=args.apply)
        except SAFE_EXCEPTIONS as exc:
            result = _blocked_result(exc)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 2 if result["status"] == "blocked" else 0
    return stream_snapshots(sys.stdin, sys.stdout, root=args.root, apply=args.apply)


if __name__ == "__main__":
    raise SystemExit(main())
