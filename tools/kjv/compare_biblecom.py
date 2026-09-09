#!/usr/bin/env python3
"""Compare committed English KJV overlays with the official YouVersion API.

The validator is read-only with respect to app assets. It downloads one passage
per comparable chapter, caches successful JSON responses outside tracked
assets, and writes a machine-readable report. Authentication comes only from
the YVP_APP_KEY environment variable.
"""

from __future__ import annotations

import argparse
import hashlib
import html
from html.parser import HTMLParser
import json
import os
import re
import sys
import tempfile
import time
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener


API_BASE = "https://api.youversion.com/v1"
CANONICAL_BIBLE_ID = 1
CANONICAL_ABBREVIATION = "KJV"
DEUTEROCANON_BIBLE_ID = 546
DEUTEROCANON_ABBREVIATION = "KJVAAE"
SCHEMA_VERSION = 1
MAX_RESPONSE_BYTES = 8 * 1024 * 1024

INLINE_BOUNDARY = "\ue000"
BLOCK_BOUNDARY = "\ue001"
ASCII_WHITESPACE = " \t\n\f\v"
MARKER_RE = re.compile(r"(\[J\]|\[/J\]|\[ADD\]|\[/ADD\])")

TRADITIONAL_NT_VERSES: tuple[tuple[str, int, int], ...] = (
    ("matthew", 17, 21),
    ("matthew", 18, 11),
    ("matthew", 23, 14),
    ("mark", 7, 16),
    ("mark", 9, 44),
    ("mark", 9, 46),
    ("mark", 11, 26),
    ("mark", 15, 28),
    ("luke", 17, 36),
    ("luke", 23, 17),
    ("john", 5, 4),
    ("acts", 8, 37),
    ("acts", 15, 34),
    ("acts", 24, 7),
    ("acts", 28, 29),
    ("romans", 16, 24),
)

# These KJVAAE mappings use the same published book, chapter, and verse IDs as
# the committed source. Other Deuterocanonical books remain excluded until a
# separate versification review proves an exact mapping.
DIRECT_KJVAAE_BOOKS = frozenset(
    {
        "tobit",
        "judith",
        "wisdom",
        "sirach",
        "baruch",
        "song_of_three",
        "bel_and_the_dragon",
        "1_maccabees",
        "2_maccabees",
    }
)

NONCOMPARABLE_REASONS: dict[str, str] = {
    "susanna": (
        "YouVersion KJVAAE exposes Susanna under an anomalous chapter identifier "
        "(the public catalog displays chapter 13 while URLs/API metadata use 1_1); "
        "no reviewed exact mapping is committed."
    ),
    "prayer_of_manasseh": "YouVersion KJVAAE Bible ID 546 does not publish this book.",
    "1_esdras": "YouVersion KJVAAE Bible ID 546 does not publish this book.",
    "2_esdras": "YouVersion KJVAAE Bible ID 546 does not publish this book.",
    "esther_greek": (
        "The committed edition deliberately falls back for integrated Greek Esther; "
        "the pinned KJV source contains additions only and has no reviewed mapping."
    ),
    "psalm_151": "The committed KJV edition has no KJV/KJVA overlay for Psalm 151.",
    "3_maccabees": "The committed KJV edition has no KJV/KJVA overlay for 3 Maccabees.",
    "4_maccabees": "The committed KJV edition has no KJV/KJVA overlay for 4 Maccabees.",
}

BLOCK_TAGS = frozenset({"address", "article", "aside", "blockquote", "div", "li", "p", "section", "td", "th", "tr"})
VOID_TAGS = frozenset({"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"})
SKIP_CLASSES = frozenset({"yv-vlbl", "yv-clbl", "yv-h", "yv-n"})
RED_CLASS_NAMES = frozenset({"red", "red-letter", "redletter", "jesus-words", "words-of-jesus", "words_of_jesus"})


class ValidationError(RuntimeError):
    """Local data or comparison invariant failed."""


class ConfigurationError(RuntimeError):
    """Required credentials, licensing, or local configuration is unavailable."""


class UnsupportedMarkup(RuntimeError):
    """The API returned markup that cannot be compared without guessing."""


class NoRedirect(HTTPRedirectHandler):
    """Refuse redirects so the App Key cannot be forwarded to another origin."""

    def redirect_request(self, req: Request, fp: Any, code: int, msg: str, headers: Any, newurl: str) -> None:
        return None


class ApiError(RuntimeError):
    def __init__(self, path: str, status: int | None, message: str, retryable: bool = False) -> None:
        super().__init__(message)
        self.path = path
        self.status = status
        self.retryable = retryable


@dataclass(frozen=True)
class SemanticText:
    plain: str
    jesus_words: tuple[str, ...]
    translator_additions: tuple[str, ...]
    jesus_markup: str
    translator_addition_markup: str


@dataclass(frozen=True)
class ParsedApiVerse:
    verse: int
    semantic: SemanticText


@dataclass(frozen=True)
class ExpectedVerse:
    collection: str
    book_id: str
    local_chapter: int
    local_verse: int
    api_bible_id: int
    api_book_code: str
    api_chapter: int
    api_verse: int
    semantic: SemanticText

    @property
    def local_reference(self) -> str:
        return f"{self.book_id}.{self.local_chapter}.{self.local_verse}"

    @property
    def api_reference(self) -> str:
        return f"{self.api_book_code}.{self.api_chapter}.{self.api_verse}"


@dataclass
class HtmlFrame:
    tag: str
    skip_root: bool = False
    skipped: bool = False
    semantic: str | None = None
    block: bool = False


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest().upper()


def normalized_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n"


def atomic_write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(normalized_json(value), encoding="utf-8", newline="\n")
    temporary.replace(path)


def normalize_line_endings(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


def normalize_boundary_whitespace(text: str) -> str:
    """Normalize whitespace only when an HTML/markup boundary touches it."""

    value = unicodedata.normalize("NFC", normalize_line_endings(text))
    boundary_pattern = re.compile(
        rf"[{re.escape(ASCII_WHITESPACE)}]*"
        rf"[{INLINE_BOUNDARY}{BLOCK_BOUNDARY}]"
        rf"(?:[{re.escape(ASCII_WHITESPACE)}{INLINE_BOUNDARY}{BLOCK_BOUNDARY}])*"
        rf"[{re.escape(ASCII_WHITESPACE)}]*"
    )

    def replace_boundary(match: re.Match[str]) -> str:
        chunk = match.group(0)
        if BLOCK_BOUNDARY in chunk:
            return " "
        if any(character in ASCII_WHITESPACE for character in chunk):
            return " "
        return ""

    previous = None
    while previous != value:
        previous = value
        value = boundary_pattern.sub(replace_boundary, value)
    return unicodedata.normalize("NFC", value.strip(ASCII_WHITESPACE))


def parse_semantic_text(text: str, reference: str) -> SemanticText:
    """Validate supported wrappers and produce comparison text and span content."""

    value = unicodedata.normalize("NFC", html.unescape(normalize_line_endings(text)))
    plain_parts: list[str] = []
    stack: list[tuple[str, list[str]]] = []
    jesus_words: list[str] = []
    additions: list[str] = []
    tagged_parts: list[str] = []

    position = 0
    for match in MARKER_RE.finditer(value):
        literal = value[position : match.start()]
        plain_parts.append(literal)
        tagged_parts.append(literal)
        for _kind, buffer in stack:
            buffer.append(literal)

        marker = match.group(0)
        plain_parts.append(INLINE_BOUNDARY)
        tagged_parts.extend((INLINE_BOUNDARY, marker, INLINE_BOUNDARY))
        for _kind, buffer in stack:
            buffer.append(INLINE_BOUNDARY)

        if marker == "[J]":
            if any(kind == "J" for kind, _buffer in stack):
                raise ValidationError(f"Nested [J] marker at {reference}")
            stack.append(("J", []))
        elif marker == "[ADD]":
            if any(kind == "ADD" for kind, _buffer in stack):
                raise ValidationError(f"Nested [ADD] marker at {reference}")
            stack.append(("ADD", []))
        else:
            expected_kind = "J" if marker == "[/J]" else "ADD"
            if not stack or stack[-1][0] != expected_kind:
                raise ValidationError(f"Unmatched or crossing {marker} marker at {reference}")
            kind, buffer = stack.pop()
            normalized_span = normalize_boundary_whitespace("".join(buffer))
            if kind == "J":
                jesus_words.append(normalized_span)
            else:
                additions.append(normalized_span)
        position = match.end()

    tail = value[position:]
    plain_parts.append(tail)
    tagged_parts.append(tail)
    for _kind, buffer in stack:
        buffer.append(tail)
    if stack:
        raise ValidationError(f"Unclosed [{stack[-1][0]}] marker at {reference}")

    canonical_tagged = normalize_boundary_whitespace("".join(tagged_parts))

    def markup_for(kept_open: str, kept_close: str) -> str:
        def replace_marker(match: re.Match[str]) -> str:
            marker = match.group(0)
            return marker if marker in {kept_open, kept_close} else INLINE_BOUNDARY

        return normalize_boundary_whitespace(MARKER_RE.sub(replace_marker, canonical_tagged))

    return SemanticText(
        plain=normalize_boundary_whitespace("".join(plain_parts)),
        jesus_words=tuple(jesus_words),
        translator_additions=tuple(additions),
        jesus_markup=markup_for("[J]", "[/J]"),
        translator_addition_markup=markup_for("[ADD]", "[/ADD]"),
    )


def _attribute_map(attrs: list[tuple[str, str | None]]) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw_name, raw_value in attrs:
        name = raw_name.lower()
        if name in result:
            raise UnsupportedMarkup(f"duplicate HTML attribute {name!r}")
        result[name] = raw_value or ""
    return result


def _suspected_unsupported_red_markup(tag: str, attrs: dict[str, str], classes: set[str]) -> bool:
    if "wj" in classes:
        return False
    if tag in {"red", "wj"} or classes.intersection(RED_CLASS_NAMES):
        return True
    if tag == "font" and attrs.get("color", "").strip().lower() in {"red", "#f00", "#ff0000"}:
        return True
    style = attrs.get("style", "").replace(" ", "").lower()
    if re.search(r"(?:^|;)color:(?:red|#f00|#ff0000|rgb\(255,0,0\))(?:;|$)", style):
        return True
    for name, raw_value in attrs.items():
        value = raw_value.strip().lower().replace("_", "-")
        if name.startswith("data-") and value in {"wj", "red-letter", "words-of-jesus", "jesus-words"}:
            return True
    return False


class YvDomChapterParser(HTMLParser):
    """Strict parser for YouVersion's documented yv-v milestone HTML."""

    def __init__(self, passage_id: str) -> None:
        super().__init__(convert_charrefs=True)
        self.passage_id = passage_id
        self.frames: list[HtmlFrame] = []
        self.skip_depth = 0
        self.current_verse: int | None = None
        self.current_parts: list[str] = []
        self.active_semantics: list[str] = []
        self.verses: dict[int, ParsedApiVerse] = {}
        self.outside_text: list[str] = []

    def _append(self, text: str) -> None:
        if self.skip_depth:
            return
        if self.current_verse is None:
            if text.strip(ASCII_WHITESPACE):
                self.outside_text.append(text)
            return
        self.current_parts.append(text)

    def _finish_verse(self) -> None:
        if self.current_verse is None:
            return
        if self.active_semantics:
            raise UnsupportedMarkup(
                f"semantic span crosses a verse boundary in {self.passage_id} verse {self.current_verse}"
            )
        if self.current_verse in self.verses:
            raise UnsupportedMarkup(f"duplicate yv-v milestone {self.current_verse} in {self.passage_id}")
        tagged = "".join(self.current_parts)
        semantic = parse_semantic_text(tagged, f"API {self.passage_id}.{self.current_verse}")
        if not semantic.plain:
            raise UnsupportedMarkup(f"empty API verse {self.passage_id}.{self.current_verse}")
        self.verses[self.current_verse] = ParsedApiVerse(self.current_verse, semantic)
        self.current_verse = None
        self.current_parts = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        attributes = _attribute_map(attrs)
        classes = {item for item in attributes.get("class", "").lower().split() if item}
        parent_skipped = self.skip_depth > 0
        own_skip = bool(classes.intersection(SKIP_CLASSES))
        skipped = parent_skipped or own_skip

        if not skipped and _suspected_unsupported_red_markup(tag, attributes, classes):
            raise UnsupportedMarkup(
                f"unsupported possible red-letter markup <{tag}> in {self.passage_id}; only class=wj is accepted"
            )

        if "yv-v" in classes:
            if skipped:
                if tag not in VOID_TAGS:
                    self.frames.append(HtmlFrame(tag=tag, skipped=True))
                return
            raw_verse = attributes.get("v", "")
            if not raw_verse.isdecimal() or int(raw_verse) < 1:
                raise UnsupportedMarkup(f"invalid yv-v value {raw_verse!r} in {self.passage_id}")
            raw_end = attributes.get("ev", "")
            if raw_end and raw_end != raw_verse:
                raise UnsupportedMarkup(
                    f"bridged/ranged verse milestone v={raw_verse!r} ev={raw_end!r} in {self.passage_id}"
                )
            self._finish_verse()
            self.current_verse = int(raw_verse)
            self.current_parts = []

        semantic: str | None = None
        if not skipped and "wj" in classes:
            semantic = "J"
        elif not skipped and "add" in classes:
            semantic = "ADD"

        block = tag in BLOCK_TAGS or tag == "br"
        if block:
            self._append(BLOCK_BOUNDARY)
        if semantic is not None:
            if semantic in self.active_semantics:
                raise UnsupportedMarkup(f"nested {semantic} semantic span in {self.passage_id}")
            self._append("[J]" if semantic == "J" else "[ADD]")
            self.active_semantics.append(semantic)

        if own_skip and not parent_skipped:
            self.skip_depth += 1
        if tag not in VOID_TAGS:
            self.frames.append(
                HtmlFrame(
                    tag=tag,
                    skip_root=own_skip and not parent_skipped,
                    skipped=skipped,
                    semantic=semantic,
                    block=block,
                )
            )

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.handle_starttag(tag, attrs)
        if tag.lower() not in VOID_TAGS:
            self.handle_endtag(tag)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag in VOID_TAGS:
            return
        if not self.frames:
            raise UnsupportedMarkup(f"unexpected </{tag}> in {self.passage_id}")
        frame = self.frames.pop()
        if frame.tag != tag:
            raise UnsupportedMarkup(
                f"non-well-formed HTML in {self.passage_id}: expected </{frame.tag}>, got </{tag}>"
            )
        if not frame.skipped and frame.semantic is not None:
            if not self.active_semantics or self.active_semantics[-1] != frame.semantic:
                raise UnsupportedMarkup(f"crossing semantic HTML spans in {self.passage_id}")
            self._append("[/J]" if frame.semantic == "J" else "[/ADD]")
            self.active_semantics.pop()
        if not frame.skipped and frame.block:
            self._append(BLOCK_BOUNDARY)
        if frame.skip_root:
            self.skip_depth -= 1

    def handle_data(self, data: str) -> None:
        self._append(data)

    def handle_comment(self, data: str) -> None:
        return

    def handle_decl(self, decl: str) -> None:
        raise UnsupportedMarkup(f"unexpected HTML declaration in {self.passage_id}")

    def unknown_decl(self, data: str) -> None:
        raise UnsupportedMarkup(f"unknown HTML declaration in {self.passage_id}")

    def finish(self) -> dict[int, ParsedApiVerse]:
        self.close()
        if self.frames:
            raise UnsupportedMarkup(f"unclosed HTML element <{self.frames[-1].tag}> in {self.passage_id}")
        if self.skip_depth or self.active_semantics:
            raise UnsupportedMarkup(f"unclosed semantic/hidden HTML span in {self.passage_id}")
        self._finish_verse()
        outside = normalize_boundary_whitespace("".join(self.outside_text))
        if outside:
            raise UnsupportedMarkup(
                f"unclassified text outside yv-v milestones in {self.passage_id}: {outside[:120]!r}"
            )
        if not self.verses:
            raise UnsupportedMarkup(
                f"no yv-v milestones in {self.passage_id}; refusing to infer verse boundaries"
            )
        return self.verses


def parse_api_chapter(content: str, passage_id: str) -> dict[int, ParsedApiVerse]:
    parser = YvDomChapterParser(passage_id)
    parser.feed(content)
    return parser.finish()


class YouVersionClient:
    def __init__(self, app_key: str, timeout: float, retries: int) -> None:
        if not app_key or app_key != app_key.strip():
            raise ConfigurationError("YVP_APP_KEY is missing or has leading/trailing whitespace")
        if "\r" in app_key or "\n" in app_key:
            raise ConfigurationError("YVP_APP_KEY contains a newline")
        self._app_key = app_key
        self.timeout = timeout
        self.retries = retries
        self.opener = build_opener(NoRedirect())

    def _safe_error_message(self, body: bytes, fallback: str) -> str:
        message = fallback
        try:
            decoded = body.decode("utf-8", errors="replace")
            parsed = json.loads(decoded)
            if isinstance(parsed, dict):
                candidate = parsed.get("message") or parsed.get("error") or parsed.get("detail")
                if isinstance(candidate, (str, int, float)):
                    message = str(candidate)
        except (UnicodeError, ValueError):
            pass
        return message.replace(self._app_key, "<redacted>")[:500]

    def get_json(self, path: str, params: Iterable[tuple[str, str]] = ()) -> dict[str, Any]:
        query = urlencode(list(params))
        url = f"{API_BASE}{path}" + (f"?{query}" if query else "")
        last_error: ApiError | None = None
        for attempt in range(self.retries + 1):
            request = Request(
                url,
                headers={
                    "Accept": "application/json",
                    "User-Agent": "BibleCompanion-KJV-Audit/1",
                    "X-YVP-App-Key": self._app_key,
                },
                method="GET",
            )
            try:
                with self.opener.open(request, timeout=self.timeout) as response:
                    body = response.read(MAX_RESPONSE_BYTES + 1)
                    if len(body) > MAX_RESPONSE_BYTES:
                        raise ApiError(path, response.status, "API response exceeded the 8 MiB safety limit")
                    if response.status != 200:
                        raise ApiError(path, response.status, f"unexpected HTTP {response.status}")
            except HTTPError as exc:
                body = exc.read(MAX_RESPONSE_BYTES + 1)
                message = self._safe_error_message(body, f"HTTP {exc.code}")
                retryable = exc.code == 429 or 500 <= exc.code <= 599
                last_error = ApiError(path, exc.code, message, retryable=retryable)
                if not retryable or attempt >= self.retries:
                    raise last_error
                retry_after = exc.headers.get("Retry-After", "")
                try:
                    delay = min(60.0, max(0.0, float(retry_after)))
                except ValueError:
                    delay = min(60.0, float(2**attempt))
                time.sleep(delay)
                continue
            except (URLError, TimeoutError, OSError) as exc:
                reason = getattr(exc, "reason", exc)
                last_error = ApiError(path, None, f"network error: {reason}", retryable=True)
                if attempt >= self.retries:
                    raise last_error
                time.sleep(min(60.0, float(2**attempt)))
                continue

            try:
                payload = json.loads(body.decode("utf-8"))
            except (UnicodeError, ValueError) as exc:
                raise ApiError(path, 200, f"response was not valid UTF-8 JSON: {exc}") from exc
            if not isinstance(payload, dict):
                raise ApiError(path, 200, "response root was not a JSON object")
            return payload
        assert last_error is not None
        raise last_error


def licensed_bibles(client: YouVersionClient) -> dict[int, str]:
    result: dict[int, str] = {}
    page_token: str | None = None
    seen_tokens: set[str] = set()
    for _page in range(100):
        params = [("language_ranges[]", "en"), ("page_size", "99")]
        if page_token:
            params.append(("page_token", page_token))
        payload = client.get_json("/bibles", params)
        data = payload.get("data")
        if not isinstance(data, list):
            raise ApiError("/bibles", 200, "licensed Bible response has no data array")
        for item in data:
            if not isinstance(item, dict):
                raise ApiError("/bibles", 200, "licensed Bible response contains a non-object item")
            try:
                bible_id = int(item["id"])
            except (KeyError, TypeError, ValueError) as exc:
                raise ApiError("/bibles", 200, "licensed Bible response has an invalid id") from exc
            abbreviation = item.get("abbreviation") or item.get("localized_abbreviation")
            if not isinstance(abbreviation, str) or not abbreviation:
                raise ApiError("/bibles", 200, f"licensed Bible {bible_id} has no abbreviation")
            result[bible_id] = abbreviation.upper()
        next_token = payload.get("next_page_token")
        if not next_token:
            return result
        if not isinstance(next_token, str) or next_token in seen_tokens:
            raise ApiError("/bibles", 200, "invalid or repeated next_page_token")
        seen_tokens.add(next_token)
        page_token = next_token
    raise ApiError("/bibles", 200, "licensed Bible pagination exceeded 100 pages")


def read_json_object(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError) as exc:
        raise ValidationError(f"cannot read JSON object {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise ValidationError(f"JSON root is not an object: {path}")
    return payload


def load_comparison_plan(
    overlays: Path,
) -> tuple[list[ExpectedVerse], list[dict[str, Any]], dict[str, str], int]:
    manifest_path = overlays / "_manifest.json"
    manifest = read_json_object(manifest_path)
    if manifest.get("schemaVersion") != 1 or manifest.get("editionId") != "kjv1769":
        raise ValidationError("unexpected KJV overlay manifest schema or edition")
    raw_books = manifest.get("books")
    if not isinstance(raw_books, list):
        raise ValidationError("KJV overlay manifest has no books array")

    plans: list[ExpectedVerse] = []
    excluded: list[dict[str, Any]] = []
    source_codes: dict[str, str] = {}
    seen_books: set[tuple[str, str]] = set()
    superscriptions_excluded = 0

    for item in raw_books:
        if not isinstance(item, dict):
            raise ValidationError("KJV overlay manifest contains a non-object book")
        collection = item.get("collection")
        book_id = item.get("bookId")
        if not isinstance(collection, str) or not isinstance(book_id, str):
            raise ValidationError("KJV overlay manifest contains an invalid book identity")
        identity = collection, book_id
        if identity in seen_books:
            raise ValidationError(f"duplicate manifest book {collection}/{book_id}")
        seen_books.add(identity)

        if collection in {"old_testament", "new_testament"}:
            bible_id = CANONICAL_BIBLE_ID
            api_code = item.get("sourceBookCode")
            mapping_kind = "direct"
        elif collection == "deuterocanonical" and book_id in DIRECT_KJVAAE_BOOKS:
            bible_id = DEUTEROCANON_BIBLE_ID
            api_code = item.get("sourceBookCode")
            mapping_kind = "direct"
        elif collection == "deuterocanonical" and book_id == "letter_of_jeremiah":
            bible_id = DEUTEROCANON_BIBLE_ID
            api_code = "BAR"
            mapping_kind = "source_fields"
        elif collection == "deuterocanonical" and book_id in NONCOMPARABLE_REASONS:
            excluded.append(
                {
                    "collection": collection,
                    "bookId": book_id,
                    "coverage": item.get("coverage"),
                    "reason": NONCOMPARABLE_REASONS[book_id],
                }
            )
            continue
        else:
            raise ValidationError(f"no reviewed Bible.com comparison disposition for {collection}/{book_id}")

        if not isinstance(api_code, str) or not re.fullmatch(r"[1-4A-Z][A-Z0-9]{2}", api_code):
            raise ValidationError(f"invalid API book code for {collection}/{book_id}: {api_code!r}")
        output = item.get("output")
        if not isinstance(output, str):
            raise ValidationError(f"comparable book has no output path: {collection}/{book_id}")
        overlay_path = overlays / Path(output)
        overlay = read_json_object(overlay_path)
        if overlay.get("collection") != collection or overlay.get("bookId") != book_id:
            raise ValidationError(f"overlay identity mismatch: {overlay_path}")
        chapters = overlay.get("chapters")
        if not isinstance(chapters, list) or not chapters:
            raise ValidationError(f"overlay has no chapters: {overlay_path}")

        source_codes[book_id] = api_code
        seen_local: set[tuple[int, int]] = set()
        for chapter in chapters:
            if not isinstance(chapter, dict) or not isinstance(chapter.get("number"), int):
                raise ValidationError(f"invalid chapter in {overlay_path}")
            local_chapter = chapter["number"]
            superscription = chapter.get("superscription")
            if superscription is not None:
                if not isinstance(superscription, str) or not superscription:
                    raise ValidationError(f"invalid superscription in {overlay_path} chapter {local_chapter}")
                parse_semantic_text(superscription, f"local {book_id}.{local_chapter} superscription")
                superscriptions_excluded += 1
            raw_verses = chapter.get("verses")
            if not isinstance(raw_verses, list) or not raw_verses:
                raise ValidationError(f"chapter {local_chapter} has no verses in {overlay_path}")
            for verse in raw_verses:
                if not isinstance(verse, dict):
                    raise ValidationError(f"invalid verse in {overlay_path}")
                local_verse = verse.get("verse")
                verse_chapter = verse.get("chapter")
                raw_text = verse.get("text")
                if not isinstance(local_verse, int) or local_verse < 1 or verse_chapter != local_chapter:
                    raise ValidationError(f"invalid local reference in {overlay_path}")
                if not isinstance(raw_text, str) or not raw_text:
                    raise ValidationError(f"empty local verse {book_id}.{local_chapter}.{local_verse}")
                local_key = local_chapter, local_verse
                if local_key in seen_local:
                    raise ValidationError(f"duplicate local verse {book_id}.{local_chapter}.{local_verse}")
                seen_local.add(local_key)

                if mapping_kind == "source_fields":
                    api_chapter = verse.get("sourceChapter")
                    api_verse = verse.get("sourceVerse")
                    if api_chapter != 6 or not isinstance(api_verse, int) or not 2 <= api_verse <= 73:
                        raise ValidationError(
                            f"unreviewed Letter of Jeremiah source mapping at {book_id}.{local_chapter}.{local_verse}"
                        )
                else:
                    api_chapter = local_chapter
                    api_verse = local_verse

                plans.append(
                    ExpectedVerse(
                        collection=collection,
                        book_id=book_id,
                        local_chapter=local_chapter,
                        local_verse=local_verse,
                        api_bible_id=bible_id,
                        api_book_code=api_code,
                        api_chapter=api_chapter,
                        api_verse=api_verse,
                        semantic=parse_semantic_text(raw_text, f"local {book_id}.{local_chapter}.{local_verse}"),
                    )
                )

    canonical_count = sum(1 for collection, _book_id in seen_books if collection in {"old_testament", "new_testament"})
    if canonical_count != 66:
        raise ValidationError(f"expected 66 canonical manifest books, found {canonical_count}")
    if len(excluded) != len(NONCOMPARABLE_REASONS):
        raise ValidationError(f"expected {len(NONCOMPARABLE_REASONS)} explicit exclusions, found {len(excluded)}")
    if len(seen_books) != 84:
        raise ValidationError(f"expected 84 manifest books including fallbacks, found {len(seen_books)}")
    manifest_totals = manifest.get("totals")
    expected_superscriptions = manifest_totals.get("superscriptions") if isinstance(manifest_totals, dict) else None
    if superscriptions_excluded != expected_superscriptions:
        raise ValidationError(
            "manifest superscription total mismatch: "
            f"found {superscriptions_excluded}, expected {expected_superscriptions!r}"
        )
    return plans, excluded, source_codes, superscriptions_excluded


def group_by_api_chapter(plans: list[ExpectedVerse]) -> list[tuple[tuple[int, str, int], list[ExpectedVerse]]]:
    grouped: dict[tuple[int, str, int], list[ExpectedVerse]] = {}
    for plan in plans:
        grouped.setdefault((plan.api_bible_id, plan.api_book_code, plan.api_chapter), []).append(plan)
    return list(grouped.items())


def cache_path_for(cache_dir: Path, bible_id: int, book_code: str, chapter: int) -> Path:
    return cache_dir / f"bible-{bible_id}" / book_code / f"{chapter}.json"


def fetch_api_chapter(
    client: YouVersionClient,
    cache_dir: Path,
    bible_id: int,
    book_code: str,
    chapter: int,
    refresh: bool,
) -> tuple[dict[str, Any], bool]:
    passage_id = f"{book_code}.{chapter}"
    request_metadata = {
        "bibleId": bible_id,
        "passageId": passage_id,
        "format": "html",
        "includeHeadings": False,
        "includeNotes": False,
    }
    path = cache_path_for(cache_dir, bible_id, book_code, chapter)
    if path.is_file() and not refresh:
        envelope = read_json_object(path)
        if envelope.get("schemaVersion") != SCHEMA_VERSION or envelope.get("request") != request_metadata:
            raise ConfigurationError(f"stale or incompatible cache envelope: {path}; use --refresh")
        response = envelope.get("response")
        if not isinstance(response, dict):
            raise ConfigurationError(f"cache envelope has no response object: {path}; use --refresh")
        return response, True

    encoded_passage = quote(passage_id, safe=".")
    response = client.get_json(
        f"/bibles/{bible_id}/passages/{encoded_passage}",
        (("format", "html"), ("include_headings", "false"), ("include_notes", "false")),
    )
    atomic_write_json(
        path,
        {
            "schemaVersion": SCHEMA_VERSION,
            "request": request_metadata,
            "response": response,
        },
    )
    return response, False


def empty_report(
    overlays: Path,
    cache_dir: Path,
    manifest: dict[str, Any],
    excluded: list[dict[str, Any]],
    superscriptions_excluded: int,
) -> dict[str, Any]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": utc_now(),
        "status": "blocked",
        "source": {
            "overlayDirectory": str(overlays),
            "editionId": manifest.get("editionId"),
            "archiveSha256": manifest.get("source", {}).get("archiveSha256") if isinstance(manifest.get("source"), dict) else None,
            "youVersionApiBase": API_BASE,
            "canonicalBible": {"id": CANONICAL_BIBLE_ID, "abbreviation": CANONICAL_ABBREVIATION},
            "deuterocanonBible": {"id": DEUTEROCANON_BIBLE_ID, "abbreviation": DEUTEROCANON_ABBREVIATION},
        },
        "normalization": [
            "decode HTML character references",
            "normalize line endings to LF",
            "normalize Unicode to NFC",
            "remove only [J]/[/J] and [ADD]/[/ADD] comparison wrappers",
            "normalize whitespace touching HTML or semantic-wrapper element boundaries",
            "preserve punctuation, case, spelling, and brackets",
        ],
        "cache": {"directory": str(cache_dir), "chaptersRead": 0, "chaptersFetched": 0},
        "licenseChecks": [],
        "coverage": {
            "scope": "verse text in books with reviewed one-to-one YouVersion mappings",
            "booksCompared": [],
            "booksExcluded": excluded,
            "contentExcluded": [
                {
                    "kind": "superscriptions",
                    "count": superscriptions_excluded,
                    "reason": (
                        "The API comparison disables headings. YouVersion heading/superscription markup does not "
                        "have a reviewed one-to-one mapping to the edition's separate superscription field."
                    ),
                }
            ],
            "chaptersPlanned": 0,
            "chaptersCompared": 0,
            "versesPlanned": 0,
            "versesCompared": 0,
            "allowedApiVerseExclusions": [
                {
                    "apiReference": "BAR.6.1",
                    "reason": "KJVAAE publishes this as the Letter of Jeremiah heading sentence; the app starts at source BAR.6.2.",
                }
            ],
        },
        "differences": [],
        "apiFailures": [],
        "traditionalNtVerseAssertions": [],
        "errors": [],
    }


def comparison_difference(expected: ExpectedVerse, actual: ParsedApiVerse | None) -> dict[str, Any] | None:
    if actual is None:
        return {
            "localReference": expected.local_reference,
            "apiReference": expected.api_reference,
            "issues": ["api_verse_missing"],
            "localText": expected.semantic.plain,
            "apiText": None,
            "localJesusWordSpans": list(expected.semantic.jesus_words),
            "apiJesusWordSpans": None,
            "localJesusWordMarkup": expected.semantic.jesus_markup,
            "apiJesusWordMarkup": None,
            "localTranslatorAdditionSpans": list(expected.semantic.translator_additions),
            "apiTranslatorAdditionSpans": None,
            "localTranslatorAdditionMarkup": expected.semantic.translator_addition_markup,
            "apiTranslatorAdditionMarkup": None,
        }

    issues: list[str] = []
    if expected.semantic.plain != actual.semantic.plain:
        issues.append("text_mismatch")
    if expected.semantic.jesus_markup != actual.semantic.jesus_markup:
        issues.append("jesus_words_markup_mismatch")
    if expected.semantic.translator_addition_markup != actual.semantic.translator_addition_markup:
        issues.append("translator_addition_markup_mismatch")
    if not issues:
        return None
    return {
        "localReference": expected.local_reference,
        "apiReference": expected.api_reference,
        "issues": issues,
        "localText": expected.semantic.plain,
        "apiText": actual.semantic.plain,
        "localJesusWordSpans": list(expected.semantic.jesus_words),
        "apiJesusWordSpans": list(actual.semantic.jesus_words),
        "localJesusWordMarkup": expected.semantic.jesus_markup,
        "apiJesusWordMarkup": actual.semantic.jesus_markup,
        "localTranslatorAdditionSpans": list(expected.semantic.translator_additions),
        "apiTranslatorAdditionSpans": list(actual.semantic.translator_additions),
        "localTranslatorAdditionMarkup": expected.semantic.translator_addition_markup,
        "apiTranslatorAdditionMarkup": actual.semantic.translator_addition_markup,
    }


def traditional_assertions(
    plans: list[ExpectedVerse],
    api_verses: dict[tuple[int, str, int, int], ParsedApiVerse],
) -> list[dict[str, Any]]:
    by_local = {(item.book_id, item.local_chapter, item.local_verse): item for item in plans}
    assertions: list[dict[str, Any]] = []
    for book_id, chapter, verse in TRADITIONAL_NT_VERSES:
        expected = by_local.get((book_id, chapter, verse))
        actual = None
        if expected is not None:
            actual = api_verses.get(
                (expected.api_bible_id, expected.api_book_code, expected.api_chapter, expected.api_verse)
            )
        assertions.append(
            {
                "localReference": f"{book_id}.{chapter}.{verse}",
                "apiReference": expected.api_reference if expected else None,
                "localPresent": expected is not None,
                "apiPresent": actual is not None if expected is not None else None,
                "textMatches": (
                    expected.semantic.plain == actual.semantic.plain
                    if expected is not None and actual is not None
                    else None
                ),
                "passed": (
                    expected is not None
                    and actual is not None
                    and expected.semantic.plain == actual.semantic.plain
                ),
            }
        )
    return assertions


def compare(args: argparse.Namespace) -> tuple[dict[str, Any], int]:
    overlays = args.overlays.resolve()
    cache_dir = args.cache_dir.resolve()
    manifest = read_json_object(overlays / "_manifest.json")
    plans, excluded, _source_codes, superscriptions_excluded = load_comparison_plan(overlays)
    report = empty_report(overlays, cache_dir, manifest, excluded, superscriptions_excluded)
    groups = group_by_api_chapter(plans)
    report["coverage"]["chaptersPlanned"] = len(groups)
    report["coverage"]["versesPlanned"] = len(plans)
    report["coverage"]["booksCompared"] = [
        {"collection": collection, "bookId": book_id}
        for collection, book_id in dict.fromkeys((item.collection, item.book_id) for item in plans)
    ]

    api_verses: dict[tuple[int, str, int, int], ParsedApiVerse] = {}
    try:
        app_key = os.environ.get("YVP_APP_KEY", "")
        client = YouVersionClient(app_key, args.timeout, args.retries)
        licensed = licensed_bibles(client)
        for bible_id, expected_abbreviation in (
            (CANONICAL_BIBLE_ID, CANONICAL_ABBREVIATION),
            (DEUTEROCANON_BIBLE_ID, DEUTEROCANON_ABBREVIATION),
        ):
            actual_abbreviation = licensed.get(bible_id)
            passed = actual_abbreviation == expected_abbreviation
            report["licenseChecks"].append(
                {
                    "bibleId": bible_id,
                    "expectedAbbreviation": expected_abbreviation,
                    "licensedAbbreviation": actual_abbreviation,
                    "passed": passed,
                }
            )
            if not passed:
                if actual_abbreviation is None:
                    raise ConfigurationError(
                        f"YVP_APP_KEY is not licensed for Bible ID {bible_id} ({expected_abbreviation})"
                    )
                raise ConfigurationError(
                    f"Bible ID {bible_id} abbreviation changed: expected {expected_abbreviation}, got {actual_abbreviation}"
                )

        for (bible_id, book_code, chapter), expected_verses in groups:
            passage_id = f"{book_code}.{chapter}"
            payload, from_cache = fetch_api_chapter(
                client,
                cache_dir,
                bible_id,
                book_code,
                chapter,
                args.refresh,
            )
            report["cache"]["chaptersRead" if from_cache else "chaptersFetched"] += 1
            returned_id = payload.get("id")
            content = payload.get("content")
            if not isinstance(returned_id, str) or returned_id.upper() != passage_id:
                raise UnsupportedMarkup(
                    f"API canonical passage id mismatch: requested {passage_id}, returned {returned_id!r}"
                )
            if not isinstance(content, str):
                raise UnsupportedMarkup(f"API passage {passage_id} has no HTML content string")
            parsed = parse_api_chapter(content, passage_id)
            report["coverage"]["chaptersCompared"] += 1
            expected_api_verses = {item.api_verse for item in expected_verses}
            allowed_extra = {1} if bible_id == DEUTEROCANON_BIBLE_ID and book_code == "BAR" and chapter == 6 else set()

            for verse_number, actual in parsed.items():
                api_verses[(bible_id, book_code, chapter, verse_number)] = actual
                if verse_number not in expected_api_verses and verse_number not in allowed_extra:
                    report["differences"].append(
                        {
                            "localReference": None,
                            "apiReference": f"{passage_id}.{verse_number}",
                            "issues": ["api_verse_unexpected"],
                            "localText": None,
                            "apiText": actual.semantic.plain,
                            "localJesusWordSpans": None,
                            "apiJesusWordSpans": list(actual.semantic.jesus_words),
                            "localJesusWordMarkup": None,
                            "apiJesusWordMarkup": actual.semantic.jesus_markup,
                            "localTranslatorAdditionSpans": None,
                            "apiTranslatorAdditionSpans": list(actual.semantic.translator_additions),
                            "localTranslatorAdditionMarkup": None,
                            "apiTranslatorAdditionMarkup": actual.semantic.translator_addition_markup,
                        }
                    )

            for expected in expected_verses:
                actual = parsed.get(expected.api_verse)
                if actual is not None:
                    report["coverage"]["versesCompared"] += 1
                difference = comparison_difference(expected, actual)
                if difference is not None:
                    report["differences"].append(difference)

        report["traditionalNtVerseAssertions"] = traditional_assertions(plans, api_verses)
        assertions_pass = all(item["passed"] for item in report["traditionalNtVerseAssertions"])
        counts_pass = (
            report["coverage"]["chaptersCompared"] == report["coverage"]["chaptersPlanned"]
            and report["coverage"]["versesCompared"] == report["coverage"]["versesPlanned"]
        )
        passed = not report["differences"] and assertions_pass and counts_pass
        report["status"] = "passed" if passed else "failed"
        return report, 0 if passed else 1
    except ApiError as exc:
        report["apiFailures"].append(
            {"path": exc.path, "httpStatus": exc.status, "message": str(exc), "retryable": exc.retryable}
        )
        report["errors"].append({"type": "api", "message": str(exc)})
    except ConfigurationError as exc:
        report["errors"].append({"type": "configuration_or_license", "message": str(exc)})
    except UnsupportedMarkup as exc:
        report["errors"].append({"type": "unsupported_api_markup", "message": str(exc)})
    report["traditionalNtVerseAssertions"] = traditional_assertions(plans, api_verses)
    report["status"] = "blocked"
    return report, 2


def self_test(overlays: Path) -> None:
    sample = (
        '<div class="p"><span class="yv-v" v="1"></span>'
        '<span class="yv-vlbl">1</span>In <span class="add">the</span> beginning &amp; earth.</div>'
        '<div class="p"><span class="yv-v" v="2" ev="2"></span>'
        '<span class="yv-vlbl">2</span><span class="wj">Jesus said,</span> "Go."</div>'
    )
    parsed = parse_api_chapter(sample, "TST.1")
    assert parsed[1].semantic.plain == "In the beginning & earth."
    assert parsed[1].semantic.translator_additions == ("the",)
    assert parsed[2].semantic.plain == 'Jesus said, "Go."'
    assert parsed[2].semantic.jesus_words == ("Jesus said,",)

    nested = parse_semantic_text("[J]A [ADD]true[/ADD] word.[/J]", "self-test")
    assert nested.plain == "A true word."
    assert nested.jesus_words == ("A true word.",)
    assert nested.translator_additions == ("true",)
    assert nested.jesus_markup == "[J]A true word.[/J]"
    assert nested.translator_addition_markup == "A [ADD]true[/ADD] word."
    first_duplicate = parse_semantic_text("[J]same[/J] same", "self-test")
    second_duplicate = parse_semantic_text("same [J]same[/J]", "self-test")
    assert first_duplicate.jesus_words == second_duplicate.jesus_words
    assert first_duplicate.jesus_markup != second_duplicate.jesus_markup
    assert parse_semantic_text("A &amp; B", "self-test").plain == "A & B"
    assert parse_semantic_text("A &amp;amp; B", "self-test").plain == "A &amp; B"

    try:
        YouVersionClient("", 1.0, 0)
    except ConfigurationError:
        pass
    else:
        raise AssertionError("missing YVP_APP_KEY was accepted")

    class NoNetworkClient:
        def get_json(self, path: str, params: Iterable[tuple[str, str]] = ()) -> dict[str, Any]:
            raise AssertionError(f"cache self-test attempted a network request: {path} {tuple(params)}")

    with tempfile.TemporaryDirectory(prefix="kjv-biblecom-self-test-") as temporary:
        cache_dir = Path(temporary)
        cached_response = {"id": "TST.1", "content": sample, "reference": "Test 1"}
        atomic_write_json(
            cache_path_for(cache_dir, 1, "TST", 1),
            {
                "schemaVersion": SCHEMA_VERSION,
                "request": {
                    "bibleId": 1,
                    "passageId": "TST.1",
                    "format": "html",
                    "includeHeadings": False,
                    "includeNotes": False,
                },
                "response": cached_response,
            },
        )
        loaded_response, from_cache = fetch_api_chapter(
            NoNetworkClient(), cache_dir, 1, "TST", 1, refresh=False  # type: ignore[arg-type]
        )
        assert from_cache and loaded_response == cached_response

    try:
        parse_api_chapter(
            '<span class="yv-v" v="1"></span><span class="red-letter">Text</span>',
            "TST.1",
        )
    except UnsupportedMarkup:
        pass
    else:
        raise AssertionError("unsupported red-letter markup was accepted")

    try:
        parse_api_chapter('<span class="yv-v" v="1" ev="2"></span>Text', "TST.1")
    except UnsupportedMarkup:
        pass
    else:
        raise AssertionError("bridged verse milestone was accepted")

    plans, excluded, _codes, superscriptions_excluded = load_comparison_plan(overlays)
    identities = {(item.collection, item.book_id) for item in plans}
    assert len({identity for identity in identities if identity[0] in {"old_testament", "new_testament"}}) == 66
    assert len(excluded) == 8
    assert superscriptions_excluded == 116
    assert len(group_by_api_chapter(plans)) > 1_000
    assert len(plans) > 30_000
    assert len(TRADITIONAL_NT_VERSES) == 16
    assert all(any((item.book_id, item.local_chapter, item.local_verse) == ref for item in plans) for ref in TRADITIONAL_NT_VERSES)


def parse_args() -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--overlays",
        type=Path,
        default=repo_root / "shared/assets/books/editions/en/kjv1769",
        help="Committed KJV overlay directory",
    )
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=repo_root / ".kjv-biblecom-cache",
        help="Ignored directory for licensed chapter responses and the default report",
    )
    parser.add_argument("--report", type=Path, help="JSON report path; defaults to <cache-dir>/report.json")
    parser.add_argument("--timeout", type=float, default=30.0, help="Per-request timeout in seconds")
    parser.add_argument("--retries", type=int, default=4, help="Retries for rate limits, 5xx, and network failures")
    parser.add_argument("--refresh", action="store_true", help="Refetch and replace successful cached chapters")
    parser.add_argument("--self-test", action="store_true", help="Run parser/mapping tests without API access")
    args = parser.parse_args()
    if args.timeout <= 0 or args.retries < 0:
        parser.error("--timeout must be positive and --retries must be nonnegative")
    return args


def main() -> int:
    args = parse_args()
    if args.self_test:
        try:
            self_test(args.overlays.resolve())
        except (AssertionError, OSError, ValidationError, UnsupportedMarkup, ValueError) as exc:
            print(f"Bible.com comparison self-test failed: {exc}", file=sys.stderr)
            return 1
        print("Bible.com comparison self-test passed; no network requests were made.")
        return 0

    report_path = (args.report or (args.cache_dir / "report.json")).resolve()
    try:
        report, exit_code = compare(args)
    except (OSError, ValidationError, ValueError) as exc:
        failure = {
            "schemaVersion": SCHEMA_VERSION,
            "generatedAt": utc_now(),
            "status": "blocked",
            "errors": [{"type": "local_validation", "message": str(exc)}],
        }
        atomic_write_json(report_path, failure)
        print(f"Bible.com comparison blocked: {exc}", file=sys.stderr)
        print(f"Report: {report_path}", file=sys.stderr)
        return 2

    atomic_write_json(report_path, report)
    print(
        f"Bible.com comparison {report['status']}: "
        f"{report['coverage']['versesCompared']}/{report['coverage']['versesPlanned']} verses compared; "
        f"{len(report['differences'])} differences."
    )
    print(f"Report: {report_path}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
