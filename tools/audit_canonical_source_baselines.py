#!/usr/bin/env python3
"""Compare current canonical Scripture with the last source-import snapshots.

This is a read-only provenance audit.  A matching snapshot proves that the
current display text has not changed since that import; it does not, by itself,
prove that the importer reproduced the external publisher correctly.
"""

from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re
import subprocess
import sys
import unicodedata


BASELINES = {
    "en": ("33244871", "BSB"),
    "de": ("7d47636b", "SCH2000"),
    "es": ("7d47636b", "NVI"),
    "fr": ("7d47636b", "NBS"),
    "it": ("7d47636b", "NR06"),
    "ru": ("7d47636b", "NRT"),
    "pt": ("7d47636b", "NVT"),
    "ja": ("7d47636b", "JCB refresh snapshot containing earlier JA1955/ERV remnants"),
    "ko": ("7d47636b", "RNKSV"),
    "zh-Hans": ("7d47636b", "CCB"),
    "zh-Hant": ("7d47636b", "RCUV"),
    "ar": ("2fbb46d6", "SAB-derived with injected YHWH forms"),
    "hi": ("2fbb46d6", "IRVHin-derived with apparatus removed"),
}

COLLECTIONS = ("old_testament", "new_testament")
REFERENCE = re.compile(r"\s*\((\d+):(\d+)(?:-(\d+))?\)\.?\s*$")
TAG = re.compile(r"\[/?(?:J|DN|ADD)\]")


def normalize(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).split())


def decode(raw: bytes, label: str) -> dict:
    try:
        value = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"cannot parse {label}: {exc}") from exc
    if not isinstance(value, dict):
        raise RuntimeError(f"expected object in {label}")
    return value


def git_blob(repo: Path, revision: str, relative: str) -> bytes:
    process = subprocess.run(
        ["git", "show", f"{revision}:{relative}"],
        cwd=repo,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if process.returncode:
        raise RuntimeError(
            f"git show failed for {revision}:{relative}: "
            f"{process.stderr.decode(errors='replace').strip()}"
        )
    return process.stdout


def verses(payload: dict, label: str) -> list[tuple[str, str, str]]:
    result: list[tuple[str, str, str]] = []
    for story in payload.get("stories", []):
        if not isinstance(story, dict):
            raise RuntimeError(f"invalid story in {label}")
        story_id = str(story.get("id", ""))
        for raw in story.get("summaryBullets", []):
            if not isinstance(raw, str):
                raise RuntimeError(f"non-string Scripture bullet in {label}")
            match = REFERENCE.search(raw)
            if match is None:
                raise RuntimeError(f"Scripture bullet lacks a trailing reference in {label}: {raw[:80]!r}")
            plain = normalize(TAG.sub("", raw[: match.start()]))
            reference = f"{match.group(1)}:{match.group(2)}"
            if match.group(3):
                reference += f"-{match.group(3)}"
            result.append((story_id, reference, plain))
    return result


def compare(source: list[tuple[str, str, str]], current: list[tuple[str, str, str]]) -> Counter[str]:
    result: Counter[str] = Counter()
    result["sourceLines"] = len(source)
    result["currentLines"] = len(current)

    source_exact = Counter((story, ref, text) for story, ref, text in source)
    current_exact = Counter((story, ref, text) for story, ref, text in current)
    exact = source_exact & current_exact
    result["exactSameReference"] = sum(exact.values())

    source_left = source_exact - exact
    current_left = current_exact - exact
    source_plain = Counter(text for _story, _ref, text in source_left.elements())
    current_plain = Counter(text for _story, _ref, text in current_left.elements())
    moved = source_plain & current_plain
    result["sameTextDifferentReference"] = sum(moved.values())
    result["sourceTextNoExactCurrentMatch"] = sum((source_plain - moved).values())
    result["currentTextNoExactSourceMatch"] = sum((current_plain - moved).values())

    source_by_story: dict[str, list[str]] = {}
    current_by_story: dict[str, list[str]] = {}
    for story, _ref, text in source:
        source_by_story.setdefault(story, []).append(text)
    for story, _ref, text in current:
        current_by_story.setdefault(story, []).append(text)
    common_stories = set(source_by_story).intersection(current_by_story)
    result["sourceStories"] = len(source_by_story)
    result["currentStories"] = len(current_by_story)
    result["exactStoryTextSequence"] = sum(
        normalize(" ".join(source_by_story[story])) == normalize(" ".join(current_by_story[story]))
        for story in common_stories
    )
    result["storyTextSequenceDiffers"] = sum(
        normalize(" ".join(source_by_story[story])) != normalize(" ".join(current_by_story[story]))
        for story in common_stories
    )
    result["sourceOnlyStories"] = len(set(source_by_story) - set(current_by_story))
    result["currentOnlyStories"] = len(set(current_by_story) - set(source_by_story))
    return result


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    root = repo / "shared" / "assets" / "books"
    output: list[dict[str, object]] = []
    for language, (revision, edition) in BASELINES.items():
        aggregate: Counter[str] = Counter()
        for collection in COLLECTIONS:
            directory = root / collection / language
            paths = sorted(path for path in directory.glob("*.json") if not path.name.startswith("_"))
            for path in paths:
                relative = path.relative_to(repo).as_posix()
                source = verses(decode(git_blob(repo, revision, relative), f"{revision}:{relative}"), relative)
                current = verses(decode(path.read_bytes(), relative), relative)
                aggregate.update(compare(source, current))
        output.append(
            {
                "language": language,
                "claimedSourceAtBaseline": edition,
                "baselineCommit": revision,
                **dict(aggregate),
            }
        )
    print(
        json.dumps(
            {
                "mode": "read-only current text versus source-import Git snapshots",
                "warning": "Snapshot parity is provenance evidence, not independent publisher parity proof.",
                "languages": output,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, UnicodeError, ValueError, RuntimeError) as exc:
        print(f"Canonical source-baseline audit failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
