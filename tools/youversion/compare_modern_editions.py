#!/usr/bin/env python3
"""Read-only, licensed YouVersion parity audit for the 13 canonical base editions.

No API response, Scripture text, or App Key is written. The optional report
contains only references, hashes, lengths, counts, and sanitized error types.
This tool does not grant or accept a publisher license.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import sys
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools" / "kjv"))

from audit_scripture_sources import BOOKS  # noqa: E402
from compare_biblecom import (  # noqa: E402
    API_BASE,
    ApiError,
    ConfigurationError,
    ParsedApiVerse,
    UnsupportedMarkup,
    ValidationError,
    YouVersionClient,
    YvDomChapterParser,
    parse_semantic_text,
    sha256_text,
    utc_now,
)


@dataclass(frozen=True)
class Edition:
    language: str
    bible_id: int
    abbreviation: str
    api_language: str
    published_abbreviation_aliases: tuple[str, ...] = ()


EDITIONS = {
    item.language: item
    for item in (
        Edition("en", 3034, "BSB", "en"),
        Edition("de", 157, "SCH2000", "de"),
        Edition("es", 128, "NVI", "es"),
        Edition("fr", 104, "NBS", "fr"),
        Edition("it", 122, "NR06", "it"),
        Edition("pt", 1930, "NVT", "pt"),
        # Bible.com publishes this catalog entry as Cyrillic NRP; NRT is
        # its English label and the existing app/provider code.
        Edition("ru", 143, "NRT", "ru", ("\u041d\u0420\u041f",)),
        Edition("ja", 83, "JCB", "ja"),
        Edition("ko", 142, "RNKSV", "ko"),
        Edition("zh-Hans", 36, "CCB", "zh"),
        Edition("zh-Hant", 139, "RCUV", "zh"),
        Edition("ar", 153, "SAB", "ar"),
        Edition("hi", 1980, "IRVHIN", "hi"),
    )
}
REFERENCE = re.compile(r"\s*\(\s*(\d+)\s*:\s*(\d+)(?:\s*[-\u2013]\s*(\d+))?\s*\)\.?\s*$")
DIVINE_TAG = re.compile(r"\[/?DN\]")
CHALLENGE = re.compile(r"captcha|cloudflare|challenge-platform|verify you are human", re.I)


@dataclass(frozen=True)
class VerseRange:
    start: int
    end: int
    plain: str
    jesus_markup: str


class SourceChallenge(ValidationError):
    """The content endpoint returned a client challenge rather than Scripture."""


class RangedChapterParser(YvDomChapterParser):
    """Reuse the strict API HTML parser while retaining native bridged verses."""

    def __init__(self, passage_id: str) -> None:
        super().__init__(passage_id)
        self.current_end: int | None = None
        self.verses: dict[tuple[int, int], ParsedApiVerse] = {}
        self.has_jesus_markup = False
        self.last_end = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        classes = dict(attrs).get("class") or ""
        if "wj" in classes.split():
            self.has_jesus_markup = True
        if "yv-v" not in classes.split():
            super().handle_starttag(tag, attrs)
            return
        values = dict(attrs)
        start, end = values.get("v", ""), values.get("ev") or values.get("v", "")
        if not start.isdecimal() or not end.isdecimal() or int(start) < 1 or int(end) < int(start):
            raise UnsupportedMarkup(f"invalid native verse range in {self.passage_id}")
        # The shared parser validates all other HTML semantics; only its
        # single-verse guard is bypassed. _finish_verse runs before assignment.
        adjusted = [(key, start if key == "ev" else value) for key, value in attrs]
        super().handle_starttag(tag, adjusted)
        self.current_end = int(end)

    def _finish_verse(self) -> None:
        if self.current_verse is None:
            return
        if self.active_semantics:
            raise UnsupportedMarkup(f"semantic span crosses a verse boundary in {self.passage_id}")
        end = self.current_end or self.current_verse
        key = (self.current_verse, end)
        if key[0] <= self.last_end:
            raise UnsupportedMarkup(f"overlapping or out-of-order native range in {self.passage_id}")
        if key in self.verses:
            raise UnsupportedMarkup(f"duplicate native verse range in {self.passage_id}")
        semantic = parse_semantic_text("".join(self.current_parts), f"API {self.passage_id}.{key[0]}-{key[1]}")
        if not semantic.plain:
            raise UnsupportedMarkup(f"empty native verse range in {self.passage_id}")
        self.verses[key] = ParsedApiVerse(key[0], semantic)
        self.last_end = end
        self.current_verse = None
        self.current_end = None
        self.current_parts = []

    def finish(self) -> dict[tuple[int, int], ParsedApiVerse]:
        result = super().finish()
        previous = 0
        for start, end in sorted(result):
            if start <= previous:
                raise UnsupportedMarkup(f"overlapping or unordered native ranges in {self.passage_id}")
            previous = end
        return result


def load_local_book(path: Path) -> dict[int, dict[tuple[int, int], VerseRange]]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(payload, dict) or not isinstance(payload.get("stories"), list):
        raise ValidationError(f"invalid local book structure: {path}")
    chapters: dict[int, dict[tuple[int, int], VerseRange]] = {}
    for story in payload["stories"]:
        if not isinstance(story, dict) or not isinstance(story.get("summaryBullets"), list):
            raise ValidationError(f"invalid local story structure: {path}")
        for bullet in story["summaryBullets"]:
            if not isinstance(bullet, str) or (match := REFERENCE.search(bullet)) is None:
                raise ValidationError(f"invalid local Scripture reference: {path}")
            chapter, start = int(match[1]), int(match[2])
            end = int(match[3] or start)
            if chapter < 1 or start < 1 or end < start:
                raise ValidationError(f"invalid local verse range: {path}")
            semantic = parse_semantic_text(DIVINE_TAG.sub("", bullet[: match.start()]), f"local {path.name} {chapter}:{start}-{end}")
            key = (start, end)
            chapter_ranges = chapters.setdefault(chapter, {})
            if key in chapter_ranges:
                raise ValidationError(f"duplicate local verse range: {path} {chapter}:{start}-{end}")
            chapter_ranges[key] = VerseRange(start, end, semantic.plain, semantic.jesus_markup)
    if not chapters:
        raise ValidationError(f"local book has no Scripture ranges: {path}")
    for chapter, ranges in chapters.items():
        previous = 0
        for start, end in sorted(ranges):
            if start <= previous:
                raise ValidationError(f"overlapping local ranges: {path} chapter {chapter}")
            previous = end
    return chapters


def _data(client: YouVersionClient, path: str) -> list[dict[str, Any]]:
    # Book and chapter collections are not paginated in the documented API.
    # The /bibles collection has its own explicit pagination below.
    payload = client.get_json(path)
    data = payload.get("data")
    if not isinstance(data, list) or not all(isinstance(item, dict) for item in data):
        raise ValidationError(f"API metadata collection invalid: {path}")
    if payload.get("next_page_token"):
        raise ValidationError(f"Unexpected API metadata pagination: {path}")
    return data


def _licensed(client: YouVersionClient, edition: Edition) -> bool:
    token: str | None = None
    seen: set[str] = set()
    for _ in range(100):
        params = [("language_ranges[]", edition.api_language), ("page_size", "99")]
        if token:
            params.append(("page_token", token))
        payload = client.get_json("/bibles", params)
        data = payload.get("data")
        if not isinstance(data, list):
            raise ValidationError("API licensed Bible collection invalid")
        for item in data:
            if not isinstance(item, dict):
                raise ValidationError("API licensed Bible entry invalid")
            if item.get("id") == edition.bible_id:
                expected = {value.upper() for value in (edition.abbreviation, *edition.published_abbreviation_aliases)}
                actual = {value.upper() for value in (item.get("abbreviation"), item.get("localized_abbreviation"))
                          if isinstance(value, str)}
                if not actual.intersection(expected):
                    raise ValidationError(f"licensed version identity mismatch for Bible ID {edition.bible_id}")
                return True
        next_token = payload.get("next_page_token")
        if not next_token:
            return False
        if not isinstance(next_token, str) or next_token in seen:
            raise ValidationError("API licensed Bible pagination invalid")
        seen.add(next_token)
        token = next_token
    raise ValidationError("API licensed Bible pagination exceeded limit")


def _source_chapters(client: YouVersionClient, edition: Edition, book_code: str) -> set[int]:
    path = f"/bibles/{edition.bible_id}/books/{book_code}/chapters"
    chapters: set[int] = set()
    for item in _data(client, path):
        value = item.get("id")
        if isinstance(value, int) and value > 0:
            chapters.add(value)
        elif isinstance(value, str) and value.isdecimal() and int(value) > 0:
            chapters.add(int(value))
        elif isinstance(item.get("passage_id"), str) and (match := re.fullmatch(rf"{re.escape(book_code)}\.(\d+)", item["passage_id"])):
            chapters.add(int(match[1]))
        # Introduction identifiers are source apparatus, not numeric chapters.
    if not chapters:
        raise ValidationError(f"API has no numeric chapters: {path}")
    return chapters


def _fingerprint(reference: str, kind: str, local: str | None, source: str | None) -> dict[str, Any]:
    return {
        "reference": reference,
        "kind": kind,
        "localSha256": sha256_text(local) if local is not None else None,
        "sourceSha256": sha256_text(source) if source is not None else None,
        "localLength": len(local) if local is not None else None,
        "sourceLength": len(source) if source is not None else None,
    }


def audit_edition(client: YouVersionClient, edition: Edition, books_root: Path) -> dict[str, Any]:
    result: dict[str, Any] = {
        "language": edition.language,
        "bibleId": edition.bible_id,
        "abbreviation": edition.abbreviation,
        "status": "not_audited",
        "booksCompared": 0,
        "localChapters": 0,
        "sourceChapters": 0,
        "chaptersCompared": 0,
        "localRanges": 0,
        "sourceRanges": 0,
        "rangesCompared": 0,
        "jesusMarkupCompared": 0,
        "jesusMarkupUnavailable": 0,
        "findings": [],
    }
    if not _licensed(client, edition):
        result["status"] = "blocked"
        result["blocker"] = "target Bible ID absent from this App Key's licensed /bibles collection"
        return result

    expected = {code: (collection, book) for code, collection, book in BOOKS}
    actual_books = {item.get("id") for item in _data(client, f"/bibles/{edition.bible_id}/books")}
    if not all(isinstance(code, str) for code in actual_books):
        raise ValidationError("API book inventory contains invalid IDs")
    for code in sorted(set(expected) - actual_books):
        result["findings"].append({"kind": "source_book_missing", "reference": code})
    for code, (collection, book) in expected.items():
        if code not in actual_books:
            continue
        local = load_local_book(books_root / collection / edition.language / f"{book}.json")
        source_chapters = _source_chapters(client, edition, code)
        result["localChapters"] += len(local)
        result["sourceChapters"] += len(source_chapters)
        result["localRanges"] += sum(len(ranges) for ranges in local.values())
        for chapter in sorted(set(local) - source_chapters):
            result["findings"].append({"kind": "source_chapter_missing", "reference": f"{code}.{chapter}"})
        for chapter in sorted(source_chapters - set(local)):
            result["findings"].append({"kind": "local_chapter_missing", "reference": f"{code}.{chapter}"})
        for chapter in sorted(set(local) & source_chapters):
            passage = f"{code}.{chapter}"
            payload = client.get_json(
                f"/bibles/{edition.bible_id}/passages/{passage}",
                (("format", "html"), ("include_headings", "false"), ("include_notes", "false")),
            )
            content = payload.get("content")
            if not isinstance(content, str):
                raise ValidationError(f"source passage missing content: {passage}")
            if payload.get("id") != passage:
                raise ValidationError(f"source passage identity mismatch: {passage}")
            if CHALLENGE.search(content):
                raise SourceChallenge(f"source passage returned client challenge: {passage}")
            source = RangedChapterParser(passage)
            source.feed(content)
            ranges = source.finish()
            result["sourceRanges"] += len(ranges)
            source_has_j = source.has_jesus_markup
            local_ranges = local[chapter]
            for start, end in sorted(set(local_ranges) - set(ranges)):
                result["findings"].append({"kind": "local_range_only", "reference": f"{passage}.{start}-{end}"})
            for start, end in sorted(set(ranges) - set(local_ranges)):
                result["findings"].append({"kind": "source_range_only", "reference": f"{passage}.{start}-{end}"})
            for key in sorted(set(local_ranges) & set(ranges)):
                local_verse, api_verse = local_ranges[key], ranges[key].semantic
                ref = f"{passage}.{key[0]}" + (f"-{key[1]}" if key[0] != key[1] else "")
                result["rangesCompared"] += 1
                if local_verse.plain != api_verse.plain:
                    result["findings"].append(_fingerprint(ref, "text_mismatch", local_verse.plain, api_verse.plain))
                if source_has_j:
                    result["jesusMarkupCompared"] += 1
                    if local_verse.jesus_markup != api_verse.jesus_markup:
                        result["findings"].append(_fingerprint(ref, "jesus_markup_mismatch", local_verse.jesus_markup, api_verse.jesus_markup))
                else:
                    result["jesusMarkupUnavailable"] += 1
            result["chaptersCompared"] += 1
        result["booksCompared"] += 1
    result["status"] = "mismatch" if result["findings"] else "match"
    return result


def audit(languages: list[str], books_root: Path, app_key: str | None, timeout: float, retries: int) -> dict[str, Any]:
    output: dict[str, Any] = {
        "schemaVersion": 1,
        "generatedAt": utc_now(),
        "mode": "read-only licensed YouVersion canonical text parity; no source text retained",
        "apiBase": API_BASE,
        "normalization": "HTML entities, line endings, Unicode NFC, and markup-boundary whitespace only; punctuation and wording preserved",
        "languages": [],
    }
    if not app_key:
        output["languages"] = [
            {"language": language, "bibleId": EDITIONS[language].bible_id, "status": "not_audited", "blocker": "YVP_APP_KEY absent; no API requests made"}
            for language in languages
        ]
        return output
    client = YouVersionClient(app_key, timeout, retries)
    for language in languages:
        edition = EDITIONS[language]
        try:
            result = audit_edition(client, edition, books_root)
        except ApiError as exc:
            reason = "licensed content unavailable (HTTP 403)" if exc.status == 403 else f"API HTTP {exc.status}" if exc.status else "API network failure"
            result = {"language": language, "bibleId": edition.bible_id, "status": "blocked", "blocker": reason, "apiPath": exc.path}
        except SourceChallenge:
            result = {"language": language, "bibleId": edition.bible_id, "status": "blocked", "blocker": "client challenge returned; no bypass attempted"}
        except UnsupportedMarkup:
            result = {"language": language, "bibleId": edition.bible_id, "status": "blocked", "blocker": "unsupported publisher markup or native range; comparison not completed"}
        except (ValidationError, ConfigurationError, OSError, UnicodeError, ValueError) as exc:
            # Deliberately omit exception text: malformed source HTML or local
            # data could put Scripture text in it.
            result = {"language": language, "bibleId": edition.bible_id, "status": "blocked", "blocker": type(exc).__name__}
        output["languages"].append(result)
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--languages", default=",".join(EDITIONS), help="Comma-separated language codes; default all 13")
    parser.add_argument("--report", type=Path, help="Optional sanitized JSON report; stdout is always available")
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--retries", type=int, default=4)
    args = parser.parse_args()
    languages = [part.strip() for part in args.languages.split(",")]
    if not languages or len(set(languages)) != len(languages) or any(language not in EDITIONS for language in languages):
        parser.error("--languages must contain distinct supported language codes")
    if args.timeout <= 0 or args.retries < 0:
        parser.error("--timeout must be positive and --retries nonnegative")
    result = audit(languages, ROOT / "shared" / "assets" / "books", os.environ.get("YVP_APP_KEY"), args.timeout, args.retries)
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered, encoding="utf-8", newline="\n")
    print(rendered, end="")
    return 2 if any(item["status"] in {"blocked", "not_audited"} for item in result["languages"]) else (1 if any(item["status"] == "mismatch" for item in result["languages"]) else 0)


if __name__ == "__main__":
    raise SystemExit(main())
