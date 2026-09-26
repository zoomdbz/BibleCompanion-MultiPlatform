#!/usr/bin/env python3
"""Inventory localized cross-reference drift without changing app assets.

This is a structural audit, not a translation-quality verdict. It records only
high-confidence repeated-description runs and German numeric target differences.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import defaultdict
from pathlib import Path

from remap_de_sch2000_psalm_superscriptions import CHAPTERS as GERMAN_TITLE_PSALMS


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "shared/assets"
COLLECTIONS = ("old_testament", "new_testament", "deuterocanonical")
LANGUAGES = ("de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def separate_ref(value: str) -> tuple[str, str]:
    citation, separator, description = value.partition(": ")
    return citation, description if separator else ""


def alias_table(language: str) -> list[tuple[str, str]]:
    english = read_json(ASSETS / "refs/en/book_aliases.json")
    localized = read_json(ASSETS / f"refs/{language}/book_aliases.json")
    if len(english) != len(localized):
        raise ValueError(f"Alias row count differs in {language}")
    owners: dict[str, str] = {}
    for source, target in zip(english, localized):
        # First row wins on an equal-length collision, as in ScriptureRefs.
        for alias in (target["canon"], *target["aliases"], source["canon"], *source["aliases"]):
            owners.setdefault(alias.lower(), source["canon"])
    return sorted(owners.items(), key=lambda item: len(item[0]), reverse=True)


def parse_citation(citation: str, aliases: list[tuple[str, str]]) -> tuple[tuple[str, str], ...]:
    pieces = []
    previous_book = None
    for part in citation.split(";"):
        part = part.strip()
        found = next(
            ((canon, part[len(alias):].strip()) for alias, canon in aliases
             if part.lower().startswith(alias + " ")),
            None,
        )
        if found is not None:
            book, specification = found
            previous_book = book
        elif previous_book is not None:
            book, specification = previous_book, part
        else:
            raise ValueError(f"Unparsed citation: {citation!r}")
        if not re.fullmatch(r"[0-9:,\-– ]+", specification):
            raise ValueError(f"Unparsed citation: {citation!r}")
        pieces.append((book, specification.replace(" ", "")))
    return tuple(pieces)


def german_expected_target(parsed: tuple[tuple[str, str], ...]) -> tuple[tuple[str, str], ...]:
    shifted = []
    for book, specification in parsed:
        if book == "Psalms":
            match = re.fullmatch(r"(\d+):(\d+)(?:-(\d+))?", specification)
            if match and int(match.group(1)) in GERMAN_TITLE_PSALMS:
                chapter, first, last = match.groups()
                specification = f"{chapter}:{int(first) + 1}"
                if last is not None:
                    specification += f"-{int(last) + 1}"
        if book == "Malachi":
            match = re.fullmatch(r"4:(\d+)(?:-(\d+))?", specification)
            if match:
                first, last = match.groups()
                specification = f"3:{int(first) + 18}"
                if last is not None:
                    specification += f"-{int(last) + 18}"
        shifted.append((book, specification))
    return tuple(shifted)


def format_target(parsed: tuple[tuple[str, str], ...]) -> str:
    return "; ".join(f"{book} {specification}" for book, specification in parsed)


def repeated_descriptions() -> list[dict[str, object]]:
    findings: dict[tuple[str, str, str, int], dict[str, object]] = {}
    for language in LANGUAGES:
        for collection in COLLECTIONS:
            folder = ASSETS / "books" / collection / language
            for path in sorted(folder.glob("*.json")):
                if path.name.startswith("_"):
                    continue
                local = read_json(path)["stories"]
                by_id = {story["id"]: story for story in local}
                english = {
                    story["id"]: story
                    for story in read_json(ASSETS / "books" / collection / "en" / path.name)["stories"]
                }
                for first in local:
                    chapter = re.fullmatch(r"(.+)-(\d+)", first["id"])
                    if chapter is None or (language == "ru" and path.stem == "psalms"):
                        continue
                    second = by_id.get(f"{chapter.group(1)}-{int(chapter.group(2)) + 1}")
                    en_first, en_second = english.get(first["id"]), english.get(second["id"]) if second else None
                    if second is None or en_first is None or en_second is None:
                        continue
                    first_refs, second_refs = first.get("crossRefs", []), second.get("crossRefs", [])
                    en_first_refs, en_second_refs = en_first.get("crossRefs", []), en_second.get("crossRefs", [])
                    run = 0
                    for a, b, ea, eb in zip(first_refs, second_refs, en_first_refs, en_second_refs):
                        a_target, a_desc = separate_ref(a)
                        b_target, b_desc = separate_ref(b)
                        _, ea_desc = separate_ref(ea)
                        _, eb_desc = separate_ref(eb)
                        if a_desc and a_desc == b_desc and a_target != b_target and ea_desc != eb_desc:
                            run += 1
                        else:
                            break
                    if run < 4:
                        continue
                    for local_story, source_story in ((first, en_first), (second, en_second)):
                        for index in range(run):
                            local_target, local_desc = separate_ref(local_story["crossRefs"][index])
                            english_target, english_desc = separate_ref(source_story["crossRefs"][index])
                            key = (language, collection, local_story["id"], index)
                            findings[key] = {
                                "language": language,
                                "collection": collection,
                                "file": path.relative_to(ROOT).as_posix(),
                                "story_id": local_story["id"],
                                "crossref_index_zero_based": index,
                                "local_target": local_target,
                                "english_target": english_target,
                                "english_source_description": english_desc,
                                "current_localized_description": local_desc,
                            }
    return [findings[key] for key in sorted(findings)]


def german_target_differences() -> list[dict[str, object]]:
    english_aliases = alias_table("en")
    german_aliases = alias_table("de")
    findings = []
    for collection in COLLECTIONS:
        for path in sorted((ASSETS / "books" / collection / "de").glob("*.json")):
            if path.name.startswith("_"):
                continue
            local = {story["id"]: story for story in read_json(path)["stories"]}
            english = {story["id"]: story for story in read_json(
                ASSETS / "books" / collection / "en" / path.name
            )["stories"]}
            for story_id, local_story in local.items():
                if story_id not in english:
                    continue
                source_refs = english[story_id].get("crossRefs", [])
                if story_id == "malachi-3":
                    source_refs += english["malachi-4"].get("crossRefs", [])
                local_refs = local_story.get("crossRefs", [])
                if len(source_refs) != len(local_refs):
                    raise ValueError(f"German crossRef count differs: {collection}/{path.name}:{story_id}")
                for index, (source, translated) in enumerate(zip(source_refs, local_refs)):
                    source_target, source_desc = separate_ref(source)
                    local_target, local_desc = separate_ref(translated)
                    expected = german_expected_target(parse_citation(source_target, english_aliases))
                    actual = parse_citation(local_target, german_aliases)
                    if expected != actual:
                        findings.append({
                            "language": "de",
                            "collection": collection,
                            "file": path.relative_to(ROOT).as_posix(),
                            "story_id": story_id,
                            "crossref_index_zero_based": index,
                            "english_target": source_target,
                            "expected_native_shift_target": format_target(expected),
                            "local_target": local_target,
                            "local_target_normalized": format_target(actual),
                            "english_source_description": source_desc,
                            "current_localized_description": local_desc,
                        })
    return findings


def recovery_candidates(repeated: list[dict[str, object]]) -> list[dict[str, object]]:
    """Find an existing translation for the *same* English citation and prose.

    Exclude flagged donor rows and phrases used for other English source text.
    This deliberately favors precision over recall; untranslated or ambiguous
    entries remain for source-based human review.
    """
    flagged = {
        (row["language"], row["collection"], row["story_id"], row["crossref_index_zero_based"])
        for row in repeated
    }
    by_source: dict[tuple[str, str, str], list[dict[str, object]]] = defaultdict(list)
    by_local_text: dict[tuple[str, str], list[dict[str, object]]] = defaultdict(list)
    for language in LANGUAGES:
        for collection in COLLECTIONS:
            for path in sorted((ASSETS / "books" / collection / language).glob("*.json")):
                if path.name.startswith("_") or (language == "ru" and path.stem == "psalms"):
                    continue
                english = {
                    story["id"]: story
                    for story in read_json(ASSETS / "books" / collection / "en" / path.name)["stories"]
                }
                for story in read_json(path)["stories"]:
                    source = english.get(story["id"])
                    if source is None:
                        continue
                    for index, (en_ref, local_ref) in enumerate(zip(
                        source.get("crossRefs", []), story.get("crossRefs", [])
                    )):
                        english_target, english_description = separate_ref(en_ref)
                        local_target, local_description = separate_ref(local_ref)
                        row = {
                            "language": language,
                            "collection": collection,
                            "file": path.relative_to(ROOT).as_posix(),
                            "story_id": story["id"],
                            "crossref_index_zero_based": index,
                            "english_target": english_target,
                            "english_source_description": english_description,
                            "local_target": local_target,
                            "current_localized_description": local_description,
                        }
                        by_source[(language, english_target, english_description)].append(row)
                        by_local_text[(language, local_description)].append(row)

    candidates = []
    for flagged_row in repeated:
        language = str(flagged_row["language"])
        source_key = (
            language,
            str(flagged_row["english_target"]),
            str(flagged_row["english_source_description"]),
        )
        donors = [
            row for row in by_source[source_key]
            if (row["language"], row["collection"], row["story_id"], row["crossref_index_zero_based"])
            not in flagged
        ]
        replacement_options = {row["current_localized_description"] for row in donors}
        if len(replacement_options) != 1:
            continue
        replacement = next(iter(replacement_options))
        if replacement == flagged_row["current_localized_description"]:
            continue
        uses = by_local_text[(language, replacement)]
        source_keys = {
            (row["language"], row["english_target"], row["english_source_description"])
            for row in uses
        }
        if source_keys != {source_key} or len(uses) > 3:
            continue
        candidates.append({
            **flagged_row,
            "existing_localized_description": replacement,
            "donor_story_ids": "; ".join(
                f"{row['story_id']}[{row['crossref_index_zero_based']}]" for row in donors
            ),
        })
    return candidates


def write_inventory(folder: Path, stem: str, rows: list[dict[str, object]]) -> None:
    (folder / f"{stem}.json").write_text(
        json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    with (folder / f"{stem}.csv").open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]) if rows else [])
        if rows:
            writer.writeheader()
            writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "tools/reports/crossref_audit")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    repeated = repeated_descriptions()
    german = german_target_differences()
    recoverable = recovery_candidates(repeated)
    write_inventory(args.output_dir, "repeated_descriptions", repeated)
    write_inventory(args.output_dir, "german_target_differences", german)
    write_inventory(args.output_dir, "recovery_candidates", recoverable)
    counts = defaultdict(int)
    for row in repeated:
        counts[row["language"]] += 1
    print(f"repeated_description_rows={len(repeated)} by_language={dict(sorted(counts.items()))}")
    print(f"german_target_differences={len(german)} output_dir={args.output_dir}")
    print(f"conservative_existing_translation_candidates={len(recoverable)}")


if __name__ == "__main__":
    main()
