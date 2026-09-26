#!/usr/bin/env python3
"""Report Scripture-body differences between HEAD and the working tree.

This is a read-only audit. It distinguishes wording changes from reference,
order, and display-tag changes so large versification repairs do not masquerade
as rewritten Scripture.
"""

from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re
import subprocess
import sys
import unicodedata


COLLECTIONS = {
    "old_testament",
    "new_testament",
    "deuterocanonical",
    "pseudepigrapha",
    "apocrypha",
}
REFERENCE = re.compile(r"\s+\((\d+):(\d+)(?:-(\d+))?\)\.?\s*$")
TAG = re.compile(r"\[/?(?:J|DN|ADD)\]")


def normalize(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).split())


def decode(raw: bytes, label: str) -> dict:
    try:
        payload = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"cannot parse {label}: {exc}") from exc
    if not isinstance(payload, dict):
        raise RuntimeError(f"expected object in {label}")
    return payload


def head_bytes(repo: Path, relative: str) -> bytes:
    process = subprocess.run(
        ["git", "show", f"HEAD:{relative}"], cwd=repo,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if process.returncode:
        raise RuntimeError(f"git show failed for {relative}: {process.stderr.decode(errors='replace').strip()}")
    return process.stdout


def bullets(payload: dict, label: str) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for story_index, story in enumerate(payload.get("stories", [])):
        if not isinstance(story, dict):
            raise RuntimeError(f"invalid story in {label}")
        for bullet_index, raw in enumerate(story.get("summaryBullets", [])):
            if not isinstance(raw, str):
                raise RuntimeError(f"non-string bullet in {label}")
            match = REFERENCE.search(raw)
            if match is None:
                # Some short non-canonical works store a prose synopsis in
                # summaryBullets rather than numbered source text.
                continue
            marked = normalize(raw[:match.start()])
            result.append({
                "reference": f"{match.group(1)}:{match.group(2)}" + (f"-{match.group(3)}" if match.group(3) else ""),
                "plain": normalize(TAG.sub("", marked)),
                "marked": marked,
                "story": story_index,
                "bullet": bullet_index,
            })
    return result


def sample(counter: Counter[str], locations: dict[str, list[str]], limit: int = 20) -> list[dict[str, object]]:
    output: list[dict[str, object]] = []
    for text, count in counter.most_common(limit):
        output.append({"count": count, "references": locations.get(text, [])[:5], "text": text[:300]})
    return output


def main() -> int:
    repo = Path(__file__).resolve().parents[1]
    process = subprocess.run(
        ["git", "diff", "--name-only", "--", "shared/assets/books"],
        cwd=repo, text=True, encoding="utf-8", errors="strict",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if process.returncode:
        print(process.stderr, file=sys.stderr)
        return 2

    reports: list[dict[str, object]] = []
    totals = Counter()
    for relative in sorted(line.strip().replace("\\", "/") for line in process.stdout.splitlines() if line.strip()):
        parts = Path(relative).parts
        if len(parts) < 6 or parts[3] not in COLLECTIONS or not relative.endswith(".json"):
            continue
        path = repo / Path(relative)
        before = bullets(decode(head_bytes(repo, relative), f"HEAD:{relative}"), f"HEAD:{relative}")
        after = bullets(decode(path.read_bytes(), relative), relative)
        before_plain = Counter(str(item["plain"]) for item in before)
        after_plain = Counter(str(item["plain"]) for item in after)
        removed = before_plain - after_plain
        added = after_plain - before_plain
        before_marked = Counter(str(item["marked"]) for item in before)
        after_marked = Counter(str(item["marked"]) for item in after)
        markup_removed = before_marked - after_marked
        markup_added = after_marked - before_marked
        before_order = [str(item["plain"]) for item in before]
        after_order = [str(item["plain"]) for item in after]
        before_refs = [(str(item["plain"]), str(item["reference"])) for item in before]
        after_refs = [(str(item["plain"]), str(item["reference"])) for item in after]
        changed = bool(removed or added or markup_removed or markup_added or before_refs != after_refs)
        if not changed:
            continue
        before_locations: dict[str, list[str]] = {}
        after_locations: dict[str, list[str]] = {}
        for item in before:
            before_locations.setdefault(str(item["plain"]), []).append(str(item["reference"]))
        for item in after:
            after_locations.setdefault(str(item["plain"]), []).append(str(item["reference"]))
        report = {
            "file": relative,
            "language": parts[4],
            "collection": parts[3],
            "beforeBullets": len(before),
            "afterBullets": len(after),
            "wordingRemoved": sum(removed.values()),
            "wordingAdded": sum(added.values()),
            "samePlainSequence": before_order == after_order,
            "samePlainMultiset": not removed and not added,
            "referenceOrOrderChanged": before_refs != after_refs,
            "displayTagMultisetChanged": bool(markup_removed or markup_added),
            "removedSamples": sample(removed, before_locations),
            "addedSamples": sample(added, after_locations),
        }
        reports.append(report)
        totals["files"] += 1
        totals["beforeBullets"] += len(before)
        totals["afterBullets"] += len(after)
        totals["wordingRemoved"] += sum(removed.values())
        totals["wordingAdded"] += sum(added.values())
        totals["filesWithWordingChanges"] += bool(removed or added)
        totals["filesWithReferenceOrOrderChanges"] += before_refs != after_refs
        totals["filesWithDisplayTagChanges"] += bool(markup_removed or markup_added)

    grouped: dict[str, Counter[str]] = {}
    for report in reports:
        key = f"{report['language']}/{report['collection']}"
        counter = grouped.setdefault(key, Counter())
        counter["files"] += 1
        counter["wordingRemoved"] += int(report["wordingRemoved"])
        counter["wordingAdded"] += int(report["wordingAdded"])
        counter["filesWithWordingChanges"] += bool(report["wordingRemoved"] or report["wordingAdded"])
        counter["filesWithReferenceOrOrderChanges"] += bool(report["referenceOrOrderChanged"])
        counter["filesWithDisplayTagChanges"] += bool(report["displayTagMultisetChanged"])

    result = {
        "mode": "read-only HEAD versus working-tree Scripture-body audit",
        "totals": dict(totals),
        "groups": {key: dict(value) for key, value in sorted(grouped.items())},
        "filesWithWordingChanges": [report for report in reports if report["wordingRemoved"] or report["wordingAdded"]],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if totals["filesWithWordingChanges"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
