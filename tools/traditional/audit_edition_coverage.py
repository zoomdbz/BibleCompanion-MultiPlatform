#!/usr/bin/env python3
"""Audit traditional-edition identity and book coverage without network access."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
POLICY_PATH = ROOT / "tools/traditional/edition_coverage_policy.json"
COLLECTIONS = ("old_testament", "new_testament", "deuterocanonical")
STATUSES = ("full", "mapped", "fallback")
SOURCE_BACKED_STATUSES = frozenset(("full", "mapped"))
REQUIRED_LANGUAGES = frozenset(
    ("ar", "de", "en", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh-Hans", "zh-Hant")
)
REQUIRED_EDITION_PROFILES = {
    ("en", "kjv1769"): "kjv_with_apocrypha",
    ("ru", "synodal1876"): "synodal_66_plus_reviewed_dc",
    **{
        key: "protestant_66_with_dc_fallback"
        for key in (
            ("ar", "van_dyck"),
            ("de", "luther1912"),
            ("es", "rv1909"),
            ("fr", "lsg1910"),
            ("it", "diodati1885"),
            ("ja", "bungo"),
            ("ko", "korrv"),
            ("pt", "almeida1911"),
            ("zh-Hans", "cuv"),
            ("zh-Hant", "cuv"),
        )
    },
}
SHA256_RE = re.compile(r"^[0-9A-F]{64}$")


class CoverageAuditError(AssertionError):
    pass


def require(value: bool, message: str) -> None:
    if not value:
        raise CoverageAuditError(message)


def read_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"Missing JSON file: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise CoverageAuditError(f"Cannot read JSON file {path}: {error}") from error
    require(isinstance(value, dict), f"Expected a JSON object: {path}")
    return value


def _book_spec(value: object, all_books: list[str], label: str) -> set[str]:
    if value == "$all":
        return set(all_books)
    require(isinstance(value, list), f"Coverage must be a list or $all: {label}")
    require(
        all(isinstance(book, str) and book for book in value),
        f"Coverage contains an invalid book id: {label}",
    )
    require(len(value) == len(set(value)), f"Coverage contains a duplicate book: {label}")
    result = set(value)
    require(result <= set(all_books), f"Coverage contains an unknown book: {label}")
    return result


def expected_coverage(
    policy: dict[str, Any], profile_id: str
) -> dict[tuple[str, str], str]:
    profiles = policy.get("coverageProfiles", {})
    require(profile_id in profiles, f"Unknown coverage profile: {profile_id}")
    profile = profiles[profile_id]
    configured = profile.get("collections")
    require(isinstance(configured, dict), f"Missing collection coverage: {profile_id}")
    require(set(configured) == set(COLLECTIONS), f"Collection set differs: {profile_id}")

    result: dict[tuple[str, str], str] = {}
    for collection in COLLECTIONS:
        books = policy["collections"][collection]
        rules = configured[collection]
        require(isinstance(rules, dict), f"Invalid collection rules: {profile_id}/{collection}")
        require(set(rules) <= set(STATUSES), f"Unknown coverage status: {profile_id}/{collection}")
        seen: set[str] = set()
        for status in STATUSES:
            selected = _book_spec(
                rules.get(status, []), books, f"{profile_id}/{collection}/{status}"
            )
            require(
                not (seen & selected),
                f"Book assigned more than once: {profile_id}/{collection}",
            )
            seen.update(selected)
            for book_id in selected:
                result[(collection, book_id)] = status
        require(
            seen == set(books),
            f"Coverage does not exactly cover {collection}: {profile_id}",
        )
    return result


def _profile_summary(statuses: dict[tuple[str, str], str]) -> dict[str, int]:
    canonical_source_backed = sum(
        status in SOURCE_BACKED_STATUSES
        for (collection, _), status in statuses.items()
        if collection in ("old_testament", "new_testament")
    )
    dc_statuses = [
        status
        for (collection, _), status in statuses.items()
        if collection == "deuterocanonical"
    ]
    return {
        "canonicalSourceBacked": canonical_source_backed,
        "deuterocanonicalSourceBacked": sum(
            status in SOURCE_BACKED_STATUSES for status in dc_statuses
        ),
        "deuterocanonicalMapped": dc_statuses.count("mapped"),
        "deuterocanonicalFallback": dc_statuses.count("fallback"),
    }


def _validate_source_pin(pin: object, label: str) -> None:
    require(isinstance(pin, dict), f"Invalid source pin: {label}")
    require(
        pin.get("location") in (
            "manifest.source",
            "manifest.source.auxiliary",
            "sourceBacked.deuterocanonical.sourceProof",
        ),
        f"Unknown source-pin location: {label}",
    )
    fields = pin.get("expectedFields")
    require(isinstance(fields, dict) and fields, f"Missing source-pin fields: {label}")
    require(
        any(key in fields for key in ("title", "sourceTitle", "module")),
        f"Source pin lacks an identity: {label}",
    )
    require(
        any("url" in key.lower() for key in fields),
        f"Source pin lacks a URL: {label}",
    )
    hashes = {key: value for key, value in fields.items() if key.endswith("Sha256")}
    require(bool(hashes), f"Source pin lacks a SHA-256 value: {label}")
    for field, value in hashes.items():
        require(
            isinstance(value, str) and SHA256_RE.fullmatch(value) is not None,
            f"Invalid pinned SHA-256: {label}/{field}",
        )


def validate_policy(policy: dict[str, Any]) -> None:
    require(policy.get("schemaVersion") == 1, "Unsupported coverage-policy schema")
    collections = policy.get("collections")
    require(isinstance(collections, dict), "Policy collections are missing")
    require(set(collections) == set(COLLECTIONS), "Policy collection set differs")
    for collection in COLLECTIONS:
        books = collections[collection]
        require(
            isinstance(books, list) and all(isinstance(book, str) and book for book in books),
            f"Invalid policy book inventory: {collection}",
        )
        require(len(books) == len(set(books)), f"Duplicate policy book id: {collection}")
    require(len(collections["old_testament"]) == 39, "Old Testament policy must contain 39 books")
    require(len(collections["new_testament"]) == 27, "New Testament policy must contain 27 books")
    require(len(collections["deuterocanonical"]) == 18, "Deuterocanon policy must contain 18 books")

    profiles = policy.get("coverageProfiles")
    require(isinstance(profiles, dict) and profiles, "Coverage profiles are missing")
    for profile_id, profile in profiles.items():
        require(
            isinstance(profile.get("reason"), str) and profile["reason"].strip(),
            f"Coverage profile lacks a reason: {profile_id}",
        )
        statuses = expected_coverage(policy, profile_id)
        require(
            all(
                statuses[(collection, book_id)] == "full"
                for collection in ("old_testament", "new_testament")
                for book_id in collections[collection]
            ),
            f"Canonical OT/NT must be fully source-backed: {profile_id}",
        )
        require(
            profile.get("summary") == _profile_summary(statuses),
            f"Coverage-profile summary differs: {profile_id}",
        )

    languages = policy.get("languages")
    require(isinstance(languages, list), "Policy languages are missing")
    language_ids = [row.get("language") for row in languages if isinstance(row, dict)]
    require(len(language_ids) == len(set(language_ids)), "Duplicate policy language")
    require(set(language_ids) == REQUIRED_LANGUAGES, "Traditional language policy set differs")
    edition_keys: set[str] = set()
    for row in languages:
        require(isinstance(row, dict), "Invalid language-policy row")
        language = row["language"]
        if row.get("status") == "absent":
            require(language == "hi", f"Only Hindi may lack a traditional edition: {language}")
            require(row.get("editionDirectoryMustBeAbsent") is True, "Hindi absence must fail closed")
            require(
                isinstance(row.get("reason"), str) and row["reason"].strip(),
                "Hindi absence lacks a reason",
            )
            continue
        require(row.get("status") == "edition", f"Unknown language status: {language}")
        edition_id = row.get("editionId")
        require(isinstance(edition_id, str) and edition_id, f"Missing edition id: {language}")
        key = f"{language}/{edition_id}"
        require(key not in edition_keys, f"Duplicate edition policy: {key}")
        edition_keys.add(key)
        require(
            row.get("manifest")
            == f"shared/assets/books/editions/{language}/{edition_id}/_manifest.json",
            f"Noncanonical manifest path: {key}",
        )
        require(
            isinstance(row.get("displayName"), str) and row["displayName"].strip(),
            f"Missing display name: {key}",
        )
        require(
            row.get("coverageProfile") == REQUIRED_EDITION_PROFILES.get((language, edition_id)),
            f"Required coverage profile differs: {key}",
        )
        pins = row.get("sourcePins")
        require(isinstance(pins, list) and pins, f"Missing source pins: {key}")
        for index, pin in enumerate(pins):
            _validate_source_pin(pin, f"{key}/{index}")
    require(len(edition_keys) == 12, "Policy must contain 12 traditional editions")
    require(
        edition_keys == {f"{language}/{edition}" for language, edition in REQUIRED_EDITION_PROFILES},
        "Required edition set differs",
    )

    alternates = policy.get("reviewedAlternateSources")
    require(isinstance(alternates, dict), "Reviewed alternate-source policy is missing")
    rule = str(alternates.get("rule", "")).lower()
    require(
        "distinct edition identity" in rule and "not approved as drop-in" in rule,
        "Alternate-source rule must prohibit drop-in relabeling",
    )
    sources = alternates.get("sources")
    require(isinstance(sources, list) and sources, "Reviewed alternate-source list is missing")
    source_keys: set[str] = set()
    for source in sources:
        require(isinstance(source, dict), "Invalid reviewed alternate-source row")
        source_key = source.get("sourceKey")
        require(isinstance(source_key, str) and source_key, "Alternate source lacks a key")
        require(source_key not in source_keys, f"Duplicate alternate source: {source_key}")
        source_keys.add(source_key)
        require(source.get("distinctEditionIdentity") is True, f"Alternate is not distinct: {source_key}")
        require(source.get("approvedAsDropIn") is False, f"Alternate approved as drop-in: {source_key}")
        require(
            isinstance(source.get("disposition"), str) and source["disposition"].strip(),
            f"Alternate source lacks a disposition: {source_key}",
        )
        target = source.get("targetEdition")
        require(target is None or target in edition_keys, f"Unknown alternate target: {source_key}")
        if "archiveSha256" in source:
            require(
                isinstance(source["archiveSha256"], str)
                and SHA256_RE.fullmatch(source["archiveSha256"]) is not None,
                f"Invalid alternate-source SHA-256: {source_key}",
            )
    require(
        source_keys == {"SpaPlatense", "FreCrampon", "FreVulgGlaire", "ChiSB", "PorCap", "HINOVBSI"},
        "Reviewed alternate-source set differs",
    )
    no_companion = set(alternates.get("noRedistributableDcCompanionFound", []))
    require(
        no_companion
        == {"ar/van_dyck", "de/luther1912", "it/diodati1885", "ja/bungo", "ko/korrv"},
        "No-companion source-review set differs",
    )


def load_policy(path: Path = POLICY_PATH) -> dict[str, Any]:
    policy = read_json(path)
    validate_policy(policy)
    return policy


def audit_rejected_alternate_metadata(
    policy: dict[str, Any], artifacts: list[tuple[str, object]]
) -> None:
    """Reject intrinsic identifiers for alternates, not only policy nicknames."""
    for alternate in policy["reviewedAlternateSources"]["sources"]:
        markers = {
            str(alternate[field]).strip().lower()
            for field in ("sourceKey", "identity", "url", "archiveSha256")
            if alternate.get(field)
        }
        for label, artifact in artifacts:
            text = (
                artifact.lower()
                if isinstance(artifact, str)
                else json.dumps(artifact, ensure_ascii=False, sort_keys=True).lower()
            )
            for marker in markers:
                require(
                    marker not in text,
                    f"Rejected alternate source metadata appears in an approved artifact: "
                    f"{alternate['sourceKey']} in {label}",
                )


def _nested_value(value: object, path: str, label: str) -> object:
    current = value
    for part in path.split("."):
        require(isinstance(current, dict) and part in current, f"Missing {label}.{part}")
        current = current[part]
    return current


def _match_fields(actual: object, expected: dict[str, object], label: str) -> None:
    require(isinstance(actual, dict), f"Invalid pinned source object: {label}")
    for field, value in expected.items():
        require(
            actual.get(field) == value,
            f"Pinned source field differs: {label}/{field}",
        )


def audit_source_pins(
    manifest: dict[str, Any],
    edition: dict[str, Any],
    rows: dict[tuple[str, str], dict[str, Any]],
    statuses: dict[tuple[str, str], str],
) -> None:
    key = f"{edition['language']}/{edition['editionId']}"
    for pin in edition["sourcePins"]:
        location = pin["location"]
        expected = pin["expectedFields"]
        if location.startswith("manifest."):
            actual = _nested_value(manifest, location.removeprefix("manifest."), key)
            _match_fields(actual, expected, f"{key}/{pin['role']}")
            continue
        require(
            location == "sourceBacked.deuterocanonical.sourceProof",
            f"Unsupported source pin: {key}/{location}",
        )
        selected = [
            rows[("deuterocanonical", book_id)]
            for book_id in edition["_policyCollections"]["deuterocanonical"]
            if statuses[("deuterocanonical", book_id)] in SOURCE_BACKED_STATUSES
        ]
        require(bool(selected), f"Source-proof pin selects no books: {key}")
        for row in selected:
            label = f"{key}/deuterocanonical/{row['bookId']}"
            proof = row.get("sourceProof")
            _match_fields(proof, expected, label)
            require(
                row.get("sourceFileSha256") == proof.get("archiveSha256"),
                f"Source-proof hash differs from manifest row: {label}",
            )


def audit_overlay_inventory(expected_outputs: set[str], edition_dir: Path, label: str) -> None:
    actual_outputs = {
        path.relative_to(edition_dir).as_posix()
        for path in edition_dir.rglob("*.json")
        if not path.name.startswith("_")
    }
    require(actual_outputs == expected_outputs, f"Overlay inventory differs: {label}")


def audit_reference_map_coverage(
    edition_dir: Path,
    statuses: dict[tuple[str, str], str],
    label: str,
) -> None:
    """Reject reference rules for books that are not present in the edition."""
    path = edition_dir / "_reference_map.json"
    if not path.is_file():
        return
    reference_map = read_json(path)
    books = reference_map.get("books")
    require(isinstance(books, list), f"Reference-map books are missing: {label}")
    seen: set[str] = set()
    for row in books:
        require(isinstance(row, dict), f"Invalid reference-map book row: {label}")
        book_id = row.get("bookId")
        require(isinstance(book_id, str) and book_id, f"Reference-map book id is missing: {label}")
        require(book_id not in seen, f"Duplicate reference-map book: {label}/{book_id}")
        seen.add(book_id)
        matching = [
            status
            for (collection, candidate), status in statuses.items()
            if candidate == book_id
        ]
        require(bool(matching), f"Reference map targets an unknown book: {label}/{book_id}")
        require(
            any(status in SOURCE_BACKED_STATUSES for status in matching),
            f"Reference map targets a fallback book: {label}/{book_id}",
        )


def audit_manifest(
    root: Path,
    policy: dict[str, Any],
    edition: dict[str, Any],
    manifest: dict[str, Any],
    edition_dir: Path,
) -> dict[str, int | str]:
    language = edition["language"]
    edition_id = edition["editionId"]
    label = f"{language}/{edition_id}"
    require(manifest.get("language") == language, f"Manifest language differs: {label}")
    require(manifest.get("editionId") == edition_id, f"Manifest edition id differs: {label}")
    require(manifest.get("displayName") == edition["displayName"], f"Manifest display name differs: {label}")

    statuses = expected_coverage(policy, edition["coverageProfile"])
    rows_value = manifest.get("books")
    require(isinstance(rows_value, list), f"Manifest books are missing: {label}")
    rows: dict[tuple[str, str], dict[str, Any]] = {}
    for row in rows_value:
        require(isinstance(row, dict), f"Invalid manifest book row: {label}")
        row_key = (row.get("collection"), row.get("bookId"))
        require(row_key not in rows, f"Duplicate manifest book row: {label}/{row_key}")
        rows[row_key] = row
    require(set(rows) == set(statuses), f"Manifest book inventory differs: {label}")

    expected_outputs: set[str] = set()
    for row_key, expected_status in statuses.items():
        collection, book_id = row_key
        row = rows[row_key]
        actual_status = row.get("coverage")
        require(
            actual_status == expected_status,
            f"Coverage mislabeled: {label}/{collection}/{book_id}; expected {expected_status}, got {actual_status}",
        )
        if expected_status in SOURCE_BACKED_STATUSES:
            expected_output = f"{collection}/{book_id}.json"
            require(row.get("output") == expected_output, f"Overlay output differs: {label}/{book_id}")
            require(
                isinstance(row.get("sourceBookCode"), str) and row["sourceBookCode"],
                f"Source-backed book lacks a source code: {label}/{book_id}",
            )
            require(
                isinstance(row.get("sourceFileSha256"), str)
                and SHA256_RE.fullmatch(row["sourceFileSha256"]) is not None,
                f"Source-backed book lacks a source hash: {label}/{book_id}",
            )
            if expected_status == "mapped":
                require(bool(row.get("sourceMapping")), f"Mapped book lacks a source map: {label}/{book_id}")
            expected_outputs.add(expected_output)
        else:
            require(row.get("fallbackEditionId") == "current", f"Fallback target differs: {label}/{book_id}")
            require(
                isinstance(row.get("reason"), str) and row["reason"].strip(),
                f"Fallback lacks a reason: {label}/{book_id}",
            )
            require(not row.get("output"), f"Fallback unexpectedly declares an overlay: {label}/{book_id}")
            # A fallback may retain source-file metadata to explain why a
            # present source work could not be mapped safely (English Greek
            # Esther does this). It must never claim an approved extraction
            # proof or coordinate mapping.
            for forbidden in ("sourceMapping", "sourceProof"):
                require(
                    not row.get(forbidden),
                    f"Fallback unexpectedly declares source metadata: {label}/{book_id}/{forbidden}",
                )
            base_path = root / "shared/assets/books" / collection / language / f"{book_id}.json"
            require(base_path.is_file(), f"Fallback base book is missing: {base_path}")

    audit_overlay_inventory(expected_outputs, edition_dir, label)
    audit_reference_map_coverage(edition_dir, statuses, label)
    for output in expected_outputs:
        overlay = read_json(edition_dir / output)
        collection, filename = output.split("/", 1)
        book_id = filename.removesuffix(".json")
        expected_status = statuses[(collection, book_id)]
        require(overlay.get("language") == language, f"Overlay language differs: {label}/{output}")
        require(overlay.get("editionId") == edition_id, f"Overlay edition differs: {label}/{output}")
        require(overlay.get("collection") == collection, f"Overlay collection differs: {label}/{output}")
        require(overlay.get("bookId") == book_id, f"Overlay book id differs: {label}/{output}")
        require(overlay.get("coverage") == expected_status, f"Overlay coverage differs: {label}/{output}")
        manifest_row = rows[(collection, book_id)]
        require(
            overlay.get("sourceBookCode") == manifest_row.get("sourceBookCode"),
            f"Overlay source book differs from manifest: {label}/{output}",
        )
        require(
            overlay.get("sourceProof") == manifest_row.get("sourceProof"),
            f"Overlay source proof differs from manifest: {label}/{output}",
        )
        overlay_provenance = {key: value for key, value in overlay.items() if key != "chapters"}
        audit_rejected_alternate_metadata(
            policy, [(f"{label}/{output}", overlay_provenance)]
        )

    totals = manifest.get("totals", {})
    source_backed = sum(status in SOURCE_BACKED_STATUSES for status in statuses.values())
    fallback = sum(status == "fallback" for status in statuses.values())
    require(totals.get("overlayBooks") == source_backed, f"Overlay total differs: {label}")
    require(totals.get("fallbackBooks") == fallback, f"Fallback total differs: {label}")

    edition_for_pins = dict(edition)
    edition_for_pins["_policyCollections"] = policy["collections"]
    audit_source_pins(manifest, edition_for_pins, rows, statuses)
    return {
        "language": language,
        "editionId": edition_id,
        "canonicalSourceBacked": sum(
            status in SOURCE_BACKED_STATUSES
            for (collection, _), status in statuses.items()
            if collection in ("old_testament", "new_testament")
        ),
        "deuterocanonicalSourceBacked": sum(
            status in SOURCE_BACKED_STATUSES
            for (collection, _), status in statuses.items()
            if collection == "deuterocanonical"
        ),
        "deuterocanonicalMapped": sum(
            status == "mapped"
            for (collection, _), status in statuses.items()
            if collection == "deuterocanonical"
        ),
        "deuterocanonicalFallback": sum(
            status == "fallback"
            for (collection, _), status in statuses.items()
            if collection == "deuterocanonical"
        ),
        "overlayBooks": source_backed,
        "fallbackBooks": fallback,
    }


def audit_all(
    root: Path = ROOT, policy_path: Path | None = None
) -> dict[str, Any]:
    root = root.resolve()
    policy_path = policy_path or root / "tools/traditional/edition_coverage_policy.json"
    policy = load_policy(policy_path)
    editions_root = root / "shared/assets/books/editions"
    edition_rows = [row for row in policy["languages"] if row["status"] == "edition"]
    absent_rows = [row for row in policy["languages"] if row["status"] == "absent"]

    expected_manifests = {row["manifest"] for row in edition_rows}
    actual_manifests = {
        path.relative_to(root).as_posix()
        for path in editions_root.glob("*/*/_manifest.json")
    }
    require(actual_manifests == expected_manifests, "Traditional manifest inventory differs from policy")
    expected_language_dirs = {row["language"] for row in edition_rows}
    actual_language_dirs = {path.name for path in editions_root.iterdir() if path.is_dir()}
    require(actual_language_dirs == expected_language_dirs, "Traditional language-directory inventory differs")

    report_rows: list[dict[str, int | str]] = []
    manifest_artifacts: list[tuple[str, object]] = []
    for edition in edition_rows:
        manifest_path = root / edition["manifest"]
        manifest = read_json(manifest_path)
        edition_dir = manifest_path.parent
        report_rows.append(audit_manifest(root, policy, edition, manifest, edition_dir))
        manifest_artifacts.append((f"{edition['language']}/{edition['editionId']}", manifest))

    for absent in absent_rows:
        language = absent["language"]
        require(
            not (editions_root / language).exists(),
            f"Absent language unexpectedly has an edition directory: {language}",
        )
        report_rows.append({"language": language, "status": "absent", "reason": absent["reason"]})

    audit_rejected_alternate_metadata(policy, manifest_artifacts)

    edition_reports = [row for row in report_rows if row.get("status") != "absent"]
    return {
        "schemaVersion": 1,
        "policy": policy_path.relative_to(root).as_posix(),
        "networkAccessRequired": False,
        "scope": "edition identity and inventory; exact Scripture wording uses the pinned-source validator",
        "languages": report_rows,
        "totals": {
            "traditionalEditions": len(edition_reports),
            "absentLanguages": len(absent_rows),
            "canonicalSourceBackedBooks": sum(int(row["canonicalSourceBacked"]) for row in edition_reports),
            "deuterocanonicalSourceBackedBooks": sum(
                int(row["deuterocanonicalSourceBacked"]) for row in edition_reports
            ),
            "deuterocanonicalFallbackBooks": sum(
                int(row["deuterocanonicalFallback"]) for row in edition_reports
            ),
            "overlayBooks": sum(int(row["overlayBooks"]) for row in edition_reports),
            "fallbackBooks": sum(int(row["fallbackBooks"]) for row in edition_reports),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="Repository root")
    parser.add_argument("--policy", type=Path, help="Coverage policy JSON")
    args = parser.parse_args()
    report = audit_all(args.root, args.policy)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
