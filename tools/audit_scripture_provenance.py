#!/usr/bin/env python3
"""Trace the last Git change for every bundled Scripture bullet.

The result is evidence about provenance and later mutations.  It does not
claim that a commit message proves external textual parity, and it never
changes repository files.
"""

from __future__ import annotations

from collections import Counter
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys


COLLECTIONS = (
    "old_testament",
    "new_testament",
    "deuterocanonical",
    "pseudepigrapha",
    "apocrypha",
)
HEADER = re.compile(r"^([0-9a-f]{40})\s+\d+\s+(\d+)(?:\s+\d+)?$")


def scripture_line_numbers(path: Path) -> set[int]:
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    result: set[int] = set()
    in_bullets = False
    for number, line in enumerate(lines, start=1):
        stripped = line.strip()
        if stripped == '"summaryBullets": [':
            in_bullets = True
            continue
        if in_bullets and stripped in {"],", "]"}:
            in_bullets = False
            continue
        if in_bullets and stripped.startswith('"'):
            result.add(number)
    return result


def blame_lines(repo: Path, relative: str) -> dict[int, str]:
    process = subprocess.run(
        ["git", "blame", "--line-porcelain", "--", relative],
        cwd=repo,
        text=True,
        encoding="utf-8",
        errors="strict",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if process.returncode:
        raise RuntimeError(f"git blame failed for {relative}: {process.stderr.strip()}")
    result: dict[int, str] = {}
    current_line: int | None = None
    current_sha: str | None = None
    for raw in process.stdout.splitlines():
        header = HEADER.match(raw)
        if header:
            current_sha = header.group(1)
            current_line = int(header.group(2))
        elif raw.startswith("\t") and current_line is not None and current_sha is not None:
            result[current_line] = current_sha
            current_line = None
            current_sha = None
    return result


def commit_subject(repo: Path, sha: str) -> str:
    if set(sha) == {"0"}:
        return "uncommitted working tree"
    process = subprocess.run(
        ["git", "show", "-s", "--format=%s", sha],
        cwd=repo,
        text=True,
        encoding="utf-8",
        errors="strict",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if process.returncode:
        return "unknown commit"
    return process.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compact", action="store_true")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    books_root = repo / "shared" / "assets" / "books"
    grouped: dict[tuple[str, str], Counter[str]] = {}
    files = 0
    bullets = 0
    for collection in COLLECTIONS:
        root = books_root / collection
        if not root.is_dir():
            continue
        for path in sorted(root.glob("*/*.json")):
            line_numbers = scripture_line_numbers(path)
            if not line_numbers:
                continue
            relative = path.relative_to(repo).as_posix()
            blame = blame_lines(repo, relative)
            missing = line_numbers - set(blame)
            if missing:
                raise RuntimeError(f"git blame omitted Scripture lines in {relative}: {sorted(missing)[:5]}")
            language = path.parent.name
            counter = grouped.setdefault((language, collection), Counter())
            for line_number in line_numbers:
                counter[blame[line_number]] += 1
            files += 1
            bullets += len(line_numbers)

    subjects = {
        sha: commit_subject(repo, sha)
        for counter in grouped.values()
        for sha in counter
    }
    output = {
        "mode": "read-only Git line provenance; not external edition proof",
        "files": files,
        "scriptureBullets": bullets,
        "groups": [],
    }
    for (language, collection), counter in sorted(grouped.items()):
        output["groups"].append(
            {
                "language": language,
                "collection": collection,
                "bullets": sum(counter.values()),
                "lastChangedBy": [
                    {
                        "commit": "working-tree" if set(sha) == {"0"} else sha[:10],
                        "bullets": count,
                        "subject": subjects[sha],
                    }
                    for sha, count in counter.most_common()
                ],
            }
        )
    if args.compact:
        compact = {
            "files": files,
            "scriptureBullets": bullets,
            "groups": [
                {
                    "language": item["language"],
                    "collection": item["collection"],
                    "bullets": item["bullets"],
                    "commits": {
                        entry["commit"]: entry["bullets"]
                        for entry in item["lastChangedBy"]
                    },
                }
                for item in output["groups"]
            ],
        }
        print(json.dumps(compact, ensure_ascii=False, indent=2))
    else:
        print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, UnicodeError, ValueError, RuntimeError) as exc:
        print(f"Scripture provenance audit failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
