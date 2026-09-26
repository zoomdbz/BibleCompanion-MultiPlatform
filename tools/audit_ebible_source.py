#!/usr/bin/env python3
"""Compare an in-app canonical language with a pinned eBible USFM archive.

The archive stays in memory. The tool writes no Scripture and compares the
source wording after removing USFM notes, cross-references, and presentation
markup. Whitespace and Unicode normalization are the only text normalization.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import unicodedata
import zipfile

import requests

from audit_scripture_sources import APP_TAG, BOOKS, TRAILING_REFERENCE, j_mask


ID = re.compile(r"(?m)^\\id\s+(\S+)")
CHAPTER = re.compile(r"^\\c\s+(\d+)\b")
VERSE = re.compile(r"^\\v\s+(\d+)(?:-(\d+))?\s+(.*)$")
MARKER = re.compile(r"\\(\+?[A-Za-z0-9-]+)(\*)?")
SKIP_PAIRED = {"f", "fe", "x", "fig", "bdit"}
VISIBLE_PAIRED = {
    "add", "bd", "bdit", "bk", "dc", "em", "it", "k", "nd", "ord",
    "pn", "png", "pro", "qs", "qt", "rq", "sc", "sig", "sls", "sup", "tl",
    "wj", "w", "wa", "wg", "wh",
}
STRUCTURAL_LINE = re.compile(
    r"^\\(?:id|usfm|ide|h|toc\d|mt\d?|mte\d?|ms\d?|mr|is\d?|ip|ipi|im|"
    r"imt\d?|io\d?|iot|iex|ib|s\d?|sr|r|d|sp|qa|cl|cp|cd|p|m|po|pr|cls|"
    r"pmo|pm|pmc|pmr|pi\d?|mi|nb|pc|ph\d?|q\d?|qr|qc|qm\d?|qd|lh|li\d?|"
    r"lf|lim\d?|litl|tr|th\d?|thr\d?|tc\d?|tcr\d?|b)\b"
)
FLOW_LINE = re.compile(
    r"^\\(?:p|m|po|pr|cls|pmo|pm|pmc|pmr|pi\d?|mi|nb|pc|ph\d?|q\d?|qr|qc|qm\d?|qd|"
    r"lh|li\d?|lf|lim\d?|litl|tr|th\d?|thr\d?|tc\d?|tcr\d?|b)\b\s*(.*)$"
)


class AuditError(RuntimeError):
    pass


def normalize(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).split())


def remove_paired(raw: str, offset: int, marker: str) -> tuple[str, int]:
    closing = "\\" + marker + "*"
    end = raw.find(closing, offset)
    if end < 0:
        raise AuditError(f"unclosed USFM marker {marker!r}: {raw[max(0, offset - 30):offset + 120]!r}")
    return raw[offset:end], end + len(closing)


def clean_inline(raw: str) -> tuple[str, str]:
    plain: list[str] = []
    marked: list[str] = []
    i = 0
    while i < len(raw):
        match = MARKER.search(raw, i)
        if match is None:
            plain.append(raw[i:])
            marked.append(raw[i:])
            break
        plain.append(raw[i:match.start()])
        marked.append(raw[i:match.start()])
        name = match.group(1)
        base = name.removeprefix("+")
        closing = bool(match.group(2))
        if closing:
            raise AuditError(f"unexpected close marker {name!r}: {raw!r}")
        if base in SKIP_PAIRED:
            _content, i = remove_paired(raw, match.end(), name)
            continue
        if base in VISIBLE_PAIRED:
            content, i = remove_paired(raw, match.end(), name)
            content = content.lstrip()
            if base in {"w", "wa", "wg", "wh", "k"}:
                content = content.split("|", 1)[0]
            inner_plain, inner_marked = clean_inline(content)
            plain.append(inner_plain)
            if base == "wj":
                marked.extend(("[J]", inner_marked, "[/J]"))
            elif base == "add":
                marked.extend(("[ADD]", inner_marked, "[/ADD]"))
            else:
                marked.append(inner_marked)
            continue
        if base in {"va", "vp"}:
            _content, i = remove_paired(raw, match.end(), name)
            continue
        raise AuditError(f"unsupported inline USFM marker {name!r}: {raw!r}")
    return normalize("".join(plain).replace("\u00b6", " ")), normalize("".join(marked).replace("\u00b6", " "))


def parse_usfm(raw: bytes, label: str) -> tuple[str, dict[tuple[int, int, int], tuple[str, str]]]:
    text = raw.decode("utf-8-sig")
    id_match = ID.search(text)
    if id_match is None:
        raise AuditError(f"missing USFM id in {label}")
    code = id_match.group(1)
    chapter: int | None = None
    verses: dict[tuple[int, int, int], tuple[str, str]] = {}
    active: tuple[int, int, int] | None = None
    active_parts: list[str] = []

    def finish() -> None:
        nonlocal active, active_parts
        if active is None:
            return
        plain, marked = clean_inline(" ".join(active_parts))
        if not plain:
            raise AuditError(f"empty source verse in {label}: {active}")
        if active in verses:
            raise AuditError(f"duplicate source verse in {label}: {active}")
        verses[active] = (plain, marked)
        active = None
        active_parts = []

    for line_number, original in enumerate(text.splitlines(), start=1):
        line = original.strip()
        chapter_match = CHAPTER.match(line)
        if chapter_match:
            finish()
            chapter = int(chapter_match.group(1))
            continue
        verse_match = VERSE.match(line)
        if verse_match:
            finish()
            if chapter is None:
                raise AuditError(f"verse before chapter in {label}:{line_number}")
            start = int(verse_match.group(1))
            end = int(verse_match.group(2) or start)
            active = (chapter, start, end)
            active_parts = [verse_match.group(3)]
            continue
        if active is not None and line:
            flow_match = FLOW_LINE.match(line)
            if flow_match:
                if flow_match.group(1):
                    active_parts.append(flow_match.group(1))
            elif not STRUCTURAL_LINE.match(line):
                active_parts.append(line)
    finish()
    return code, verses


def parse_app(path: Path) -> dict[tuple[int, int, int], tuple[str, str]]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    result: dict[tuple[int, int, int], tuple[str, str]] = {}
    for story in payload.get("stories", []):
        for raw in story.get("summaryBullets", []):
            match = TRAILING_REFERENCE.search(raw)
            if match is None:
                raise AuditError(f"unparseable app verse in {path}: {raw[:120]!r}")
            chapter = int(match.group(1))
            start = int(match.group(2))
            end = int(match.group(3) or start)
            marked = normalize(raw[:match.start()])
            key = (chapter, start, end)
            if key in result:
                raise AuditError(f"duplicate app verse in {path}: {key}")
            result[key] = (normalize(APP_TAG.sub("", marked)), marked)
    return result


def download_archive(url: str, expected_sha256: str | None) -> tuple[bytes, str]:
    response = requests.get(url, timeout=120)
    response.raise_for_status()
    body = response.content
    digest = hashlib.sha256(body).hexdigest().upper()
    if expected_sha256 and digest != expected_sha256.upper():
        raise AuditError(f"archive SHA-256 mismatch: expected {expected_sha256.upper()}, got {digest}")
    return body, digest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--archive-sha256")
    parser.add_argument("--sample-limit", type=int, default=50)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    try:
        archive, archive_hash = download_archive(args.url, args.archive_sha256)
        source_files: dict[str, tuple[str, bytes]] = {}
        with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
            for name in zipped.namelist():
                if not name.lower().endswith(".usfm"):
                    continue
                raw = zipped.read(name)
                id_match = ID.search(raw.decode("utf-8-sig"))
                if id_match is not None:
                    source_files[id_match.group(1)] = (name, raw)

        missing_source: list[str] = []
        missing_app: list[str] = []
        wording: list[dict[str, object]] = []
        j_markup: list[str] = []
        totals = Counter()
        books: list[dict[str, object]] = []
        for code, collection, book in BOOKS:
            if code not in source_files:
                raise AuditError(f"archive lacks canonical source book {code}")
            app_path = repo / "shared" / "assets" / "books" / collection / args.language / f"{book}.json"
            if not app_path.is_file():
                raise AuditError(f"missing app asset {app_path}")
            source_name, source_raw = source_files[code]
            actual_code, source = parse_usfm(source_raw, source_name)
            if actual_code != code:
                raise AuditError(f"source code mismatch: expected {code}, got {actual_code}")
            app = parse_app(app_path)
            book_exact = 0
            for key in sorted(set(app) | set(source)):
                ref = f"{book} {key[0]}:{key[1]}" + (f"-{key[2]}" if key[2] != key[1] else "")
                if key not in source:
                    missing_source.append(ref)
                    continue
                if key not in app:
                    missing_app.append(ref)
                    continue
                if app[key][0] == source[key][0]:
                    totals["exactText"] += 1
                    book_exact += 1
                    if j_mask(app[key][1]) != j_mask(source[key][1]):
                        j_markup.append(ref)
                else:
                    wording.append(
                        {
                            "reference": ref,
                            "app": app[key][0],
                            "source": source[key][0],
                        }
                    )
            totals["appLines"] += len(app)
            totals["sourceLines"] += len(source)
            books.append({"book": book, "appLines": len(app), "sourceLines": len(source), "exactText": book_exact})
        result = {
            "mode": "read-only exact-text audit; NFC and whitespace normalized; notes and cross-references excluded",
            "language": args.language,
            "sourceUrl": args.url,
            "archiveSha256": archive_hash,
            "appLines": totals["appLines"],
            "sourceLines": totals["sourceLines"],
            "exactText": totals["exactText"],
            "wordingDifferenceCount": len(wording),
            "missingFromSourceCount": len(missing_source),
            "missingFromAppCount": len(missing_app),
            "jMarkupDifferenceCountOnExactText": len(j_markup),
            "missingFromSource": missing_source[:args.sample_limit],
            "missingFromApp": missing_app[:args.sample_limit],
            "wordingDifferences": wording[:args.sample_limit],
            "jMarkupDifferencesOnExactText": j_markup[:args.sample_limit],
            "books": books,
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if wording or missing_source or missing_app else 0
    except (AuditError, OSError, UnicodeError, ValueError, KeyError, requests.RequestException, zipfile.BadZipFile) as exc:
        print(f"eBible source audit failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
