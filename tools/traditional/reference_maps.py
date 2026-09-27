"""Package only reviewed edition crosswalks; never infer rules from counts."""

from __future__ import annotations

import json
from pathlib import Path


def reference_map_for_edition(
    repo_root: Path, language: str, edition_id: str, *, include_concordant: bool = True
) -> dict | None:
    directory = repo_root / "tools/traditional"
    candidates = []
    complete_files = {
        "es": "es_rv1909_reference_map.json",
        "pt": "pt_almeida1911_reference_map.json",
    }
    complete_path = directory / complete_files[language] if language in complete_files else None
    if complete_path is not None:
        if not complete_path.is_file():
            raise ValueError(f"Missing complete reference map: {complete_path}")
        # These maps supersede the older partial Spanish NT artifact. Loading
        # both would duplicate books and make the packaged map ambiguous.
        candidates.append(json.loads(complete_path.read_text(encoding="utf-8")))

    single_files = {
        "de": "german_psalm_reference_map.json",
        "fr": "french_reference_map.json",
    }
    if not candidates and language in single_files:
        path = directory / single_files[language]
        if path.is_file():
            candidates.append(json.loads(path.read_text(encoding="utf-8")))
    if complete_path is None:
        for name in (
            "edition_reference_exceptions.json", "nt_reference_maps.json",
            "asia_reference_maps.json", "publisher_structure_reference_maps.json",
            "semantic_boundary_reference_maps.json",
        ):
            if name == "concordant_identity_reference_maps.json" and not include_concordant:
                continue
            path = directory / name
            if path.is_file():
                document = json.loads(path.read_text(encoding="utf-8"))
                if document.get("schemaVersion") != 1:
                    raise ValueError(f"Unsupported reference-map schema: {path}")
                candidates.extend(row for row in document["maps"] if row["language"] == language)
    if include_concordant:
        path = directory / "concordant_identity_reference_maps.json"
        if path.is_file():
            document = json.loads(path.read_text(encoding="utf-8"))
            if document.get("schemaVersion") != 1:
                raise ValueError(f"Unsupported reference-map schema: {path}")
            candidates.extend(row for row in document["maps"] if row["language"] == language)
    if not candidates:
        return None
    first = candidates[0]
    books = []
    by_book = {}
    provenance = []
    for candidate in candidates:
        if (candidate.get("schemaVersion") != 1 or candidate["language"] != language or
                candidate["editionId"] != edition_id or candidate["baseEditionId"] != first["baseEditionId"]):
            raise ValueError(f"Reference-map edition/schema mismatch: {language}/{edition_id}")
        concordant = candidate["provenance"].get("avoidsPreviouslyMappedChapters") is True
        semantic_boundary = candidate["provenance"].get("replacesPreviouslyMappedIdentityUnits") is True
        for book in candidate["books"]:
            book_id = book["bookId"]
            prior = by_book.get(book_id)
            if prior is None:
                copy = {**book, "mappings": list(book["mappings"])}
                books.append(copy)
                by_book[book_id] = copy
                continue
            if semantic_boundary:
                protected_source = {
                    (row["sourceChapter"], verse)
                    for row in book["mappings"]
                    for verse in range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1)
                }
                protected_target = {
                    (row["targetChapter"], verse)
                    for row in book["mappings"]
                    for verse in range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1)
                }
                retained = []
                for row in prior["mappings"]:
                    source_end = row.get("sourceVerseEnd", row["sourceVerse"])
                    target_end = row.get("targetVerseEnd", row["targetVerse"])
                    touches = any(
                        (row["sourceChapter"], n) in protected_source
                        or (row["targetChapter"], n) in protected_target
                        for n in range(min(row["sourceVerse"], row["targetVerse"]),
                                       max(source_end, target_end) + 1)
                    )
                    if not touches:
                        retained.append(row)
                        continue
                    if (row["sourceChapter"] != row["targetChapter"]
                        or row["sourceVerse"] != row["targetVerse"]
                        or source_end != target_end):
                        raise ValueError(
                            f"Semantic boundary cannot replace non-identity rule: {language}/{edition_id}/{book_id}"
                        )
                    run_start = None
                    for n in range(row["sourceVerse"], source_end + 2):
                        keep = (n <= source_end and
                                (row["sourceChapter"], n) not in protected_source and
                                (row["targetChapter"], n) not in protected_target)
                        if keep and run_start is None:
                            run_start = n
                        elif not keep and run_start is not None:
                            end = n - 1
                            kept = {"sourceChapter": row["sourceChapter"], "sourceVerse": run_start,
                                    "targetChapter": row["targetChapter"], "targetVerse": run_start}
                            if end > run_start:
                                kept["sourceVerseEnd"] = end
                                kept["targetVerseEnd"] = end
                            retained.append(kept)
                            run_start = None
                prior["mappings"] = retained + list(book["mappings"])
                prior["complete"] = False
                continue
            if not concordant or prior.get("complete"):
                raise ValueError(f"Duplicate reference-map book: {language}/{edition_id}/{book_id}")
            used_source = {
                (row["sourceChapter"], verse)
                for row in prior["mappings"]
                for verse in range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1)
            }
            used_target = {
                (row["targetChapter"], verse)
                for row in prior["mappings"]
                for verse in range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1)
            }
            for row in book["mappings"]:
                origin = {(row["sourceChapter"], verse)
                          for verse in range(row["sourceVerse"], row.get("sourceVerseEnd", row["sourceVerse"]) + 1)}
                destination = {(row["targetChapter"], verse)
                               for verse in range(row["targetVerse"], row.get("targetVerseEnd", row["targetVerse"]) + 1)}
                if origin & used_source or destination & used_target:
                    raise ValueError(
                        f"Concordant map overlaps a reviewed coordinate: {language}/{edition_id}/{book_id}"
                    )
                used_source.update(origin)
                used_target.update(destination)
            prior["mappings"].extend(book["mappings"])
        provenance.append(candidate["provenance"])
    return {
        "schemaVersion": 1,
        "language": language,
        "baseEditionId": first["baseEditionId"],
        "editionId": edition_id,
        "books": books,
        "provenance": {"sources": provenance},
    }
