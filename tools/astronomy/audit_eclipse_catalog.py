#!/usr/bin/env python3
"""Check the curated feast table against internal source/calendar evidence.

The complete source snapshot stays in tools, not app content.
"""
from __future__ import annotations
from collections import Counter
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CATALOG = Path(__file__).with_name("eclipse_catalog_2015_2033.json")
NOTES = ROOT / "shared/assets/notes"
LANGUAGES = ("en", "de", "es", "fr", "it", "pt", "ru", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi")
DATE = re.compile(r"\d{4}-\d{2}-\d{2}")
NUMBER = re.compile(r"\d+")
FEAST_IDS = {
    "pesach-i", "pesach-ii", "pesach-ii-chm", "pesach-sheni",
    "sukkot-i", "sukkot-ii", "purim", "purim-katan", "shushan-purim",
    "erev-purim", "erev-rosh-hashana", "rosh-hashana", "chanukah",
}
ENGLISH_TYPES = {
    ("solar", "total"): "Total solar eclipse",
    ("solar", "annular"): "Annular solar eclipse",
    ("solar", "partial"): "Partial solar eclipse",
    ("lunar", "total"): "Total lunar (blood moon)",
    ("lunar", "penumbral"): "Penumbral lunar eclipse",
}
TYPE_KEYS = (("lunar", "total"), ("lunar", "penumbral"), ("solar", "total"), ("solar", "partial"), ("solar", "annular"))
LOCALIZED_TYPE_LABELS = {
    "en": tuple(ENGLISH_TYPES[key] for key in TYPE_KEYS),
    "de": ("Totale Mondfinsternis (Blutmond)", "Halbschatten-Mondfinsternis", "Totale Sonnenfinsternis", "Partielle Sonnenfinsternis", "Ringförmige Sonnenfinsternis"),
    "es": ("Eclipse lunar total (luna de sangre)", "Eclipse lunar penumbral", "Eclipse solar total", "Eclipse solar parcial", "Eclipse solar anular"),
    "fr": ("Éclipse lunaire totale (lune de sang)", "Éclipse lunaire pénombrale", "Éclipse solaire totale", "Éclipse solaire partielle", "Éclipse solaire annulaire"),
    "it": ("Eclissi lunare totale (luna di sangue)", "Eclissi lunare penombrale", "Eclissi solare totale", "Eclissi solare parziale", "Eclissi solare anulare"),
    "pt": ("Eclipse lunar total (lua de sangue)", "Eclipse lunar penumbral", "Eclipse solar total", "Eclipse solar parcial", "Eclipse solar anular"),
    "ru": ("Полное лунное затмение (кровавая луна)", "Полутеневое лунное затмение", "Полное солнечное затмение", "Частичное солнечное затмение", "Кольцеобразное солнечное затмение"),
    "ja": ("皆既月食（血の月）", "半影月食", "皆既日食", "部分日食", "金環日食"),
    "ko": ("개기월식(핏빛 달)", "반영월식", "개기일식", "부분일식", "금환일식"),
    "zh-Hans": ("月全食（血月）", "半影月食", "日全食", "日偏食", "日环食"),
    "zh-Hant": ("月全食（血月）", "半影月食", "日全食", "日偏食", "日環食"),
    "ar": ("خسوف قمري كلي (قمر دموي)", "خسوف قمري شبه ظلي", "كسوف شمسي كلي", "كسوف شمسي جزئي", "كسوف شمسي حلقي"),
    "hi": ("पूर्ण चंद्र ग्रहण (रक्त चंद्रमा)", "उपच्छाया चंद्र ग्रहण", "पूर्ण सूर्य ग्रहण", "आंशिक सूर्य ग्रहण", "वलयाकार सूर्य ग्रहण"),
}
REQUIRED_SOURCES = (
    "https://penelope.uchicago.edu/josephus/ant-17.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE-0099-0000.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE0001-0100.html",
    "https://www.nature.com/articles/306743a0",
    "https://articles.adsabs.harvard.edu/pdf/1990QJRAS..31...53S",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE1401-1500.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE1901-2000.html",
    "https://eclipse.gsfc.nasa.gov/SEcat5/SE2001-2100.html",
    "https://eclipse.gsfc.nasa.gov/LEcat5/LE2001-2100.html",
    "https://www.hebcal.com/home/1663/zmanim-halachic-times-api",
    "https://www.hebcal.com/home/219/hebrew-date-converter-rest-api",
)
URL = re.compile(r"https?://[^\s)]+")


def note_title(text: str) -> str | None:
    """Return the first H1 title without its Markdown marker."""
    return next((line[2:] for line in text.splitlines() if line.startswith("# ")), None)


def bibliography_source_section(astronomy_text: str, bibliography_text: str) -> tuple[str | None, int]:
    """Return the bibliography H2 named after the astronomical note's H1."""
    title = note_title(astronomy_text)
    if title is None:
        return None, 0
    heading = f"## {title}"
    lines = bibliography_text.splitlines(keepends=True)
    matches = [index for index, line in enumerate(lines) if line.rstrip("\r\n") == heading]
    if len(matches) != 1:
        return None, len(matches)
    start = matches[0] + 1
    end = next(
        (index for index in range(start, len(lines)) if lines[index].startswith("## ")),
        len(lines),
    )
    return "".join(lines[start:end]).strip("\r\n"), 1


def audit_source_locations(
    astronomy_text: str,
    bibliography_text: str,
    expected_links: Counter[str] | None = None,
) -> list[str]:
    """Require the source list in its locale-titled bibliography subsection only."""
    failures = []
    title = note_title(astronomy_text)
    if title is None:
        failures.append("astronomical note must have an H1 title")
    section, heading_count = bibliography_source_section(astronomy_text, bibliography_text)
    if heading_count != 1:
        failures.append("bibliography must contain exactly one H2 matching the astronomical note title")
    section_text = section or ""
    for source in REQUIRED_SOURCES:
        if source in astronomy_text:
            failures.append(f"source remains misplaced in astronomical_signs.md: {source}")
        if source not in section_text:
            failures.append(f"bibliography astronomy subsection is missing source {source}")
    links = Counter(URL.findall(section_text))
    canonical_links = Counter(REQUIRED_SOURCES)
    if links != canonical_links:
        failures.append("bibliography astronomy subsection must contain exactly the 11 required source links")
    if expected_links is not None and links != expected_links:
        failures.append("bibliography astronomy subsection link parity differs from English")
    return failures

def source_document() -> dict:
    document = json.loads(CATALOG.read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1:
        raise ValueError("Unsupported eclipse evidence schema")
    keys = [(r["date"], r["body"], r["type"]) for r in document["events"]]
    if len(keys) != len(set(keys)):
        raise ValueError("Duplicate canonical eclipse event")
    return document

def selected_events() -> list[dict]:
    return [
        event for event in source_document()["events"]
        if any(
            o["id"] in FEAST_IDS or o["id"].startswith("rosh-chodesh-")
            for location in event["referenceLocations"]
            for o in location["semanticObservances"]
        )
    ]

def expected_events() -> list[tuple[str, str, str]]:
    return [(r["date"], r["body"], r["type"]) for r in selected_events()]

def table_rows(text: str) -> list[list[str]]:
    rows = []
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if any(re.search(r"\d", cell) for cell in cells):
            rows.append(cells)
    return rows

def audit_visible_table(text: str, language: str = "en") -> list[str]:
    failures = []
    expected = {date: (body, kind) for date, body, kind in expected_events()}
    rows = table_rows(text)
    if len(rows) != 47:
        failures.append("curated table must have 3 ancient, 4 historical, and 40 modern rows")
    modern = [r for r in rows if r and DATE.fullmatch(r[0])]
    if Counter(r[0] for r in modern) != Counter(expected.keys()):
        failures.append("modern rows must contain all 40 selected feast alignments exactly once")
    if [r[0] for r in modern] != sorted(r[0] for r in modern):
        failures.append("modern rows must follow chronological order")
    if any(DATE.fullmatch(r[0]) for r in rows[:7]):
        failures.append("ancient and historical context must precede modern rows")
    labels = {}
    localized_types = dict(zip(TYPE_KEYS, LOCALIZED_TYPE_LABELS.get(language, ())))
    for row in modern:
        if len(row) != 4 or not all(row):
            failures.append(f"{row[0]}: expected four nonempty visible columns")
            continue
        event_type = expected.get(row[0])
        if event_type is None:
            continue
        previous = labels.setdefault(event_type, row[1])
        if previous != row[1]:
            failures.append(f"{row[0]}: inconsistent localized eclipse type")
        if event_type in localized_types and row[1] != localized_types[event_type]:
            failures.append(f"{row[0]}: visible type does not match NASA event")
    if len(set(labels.values())) != len(labels):
        failures.append("different eclipse types must use distinct visible labels")
    return failures

def audit() -> list[str]:
    failures = []
    english = (NOTES / "en/astronomical_signs.md").read_text(encoding="utf-8")
    english_rows = table_rows(english)
    english_bibliography_path = NOTES / "en/bibliography.md"
    english_bibliography = english_bibliography_path.read_text(encoding="utf-8")
    english_source_section, _ = bibliography_source_section(english, english_bibliography)
    english_source_links = Counter(URL.findall(english_source_section or ""))
    for language in LANGUAGES:
        path = NOTES / language / "astronomical_signs.md"
        if not path.is_file():
            failures.append(f"{language}: missing astronomical_signs.md")
            continue
        text = path.read_text(encoding="utf-8")
        if "<!--" in text or "ECLIPSE_CATALOG_" in text:
            failures.append(f"{language}: removed catalog/comment metadata remains in app content")
        if any(ord(character) < 32 and character not in "\n\r\t" for character in text):
            failures.append(f"{language}: terminal/control characters in app content")
        if any(artifact in text for artifact in ("MYMEMORY", "Invoke-RestMethod", '"responseData"', '"responseStatus"')):
            failures.append(f"{language}: failed external-service output in app content")
        if any(line.startswith("+") for line in text.splitlines()):
            failures.append(f"{language}: literal patch artifact")
        failures.extend(f"{language}: {error}" for error in audit_visible_table(text, language))
        bibliography_path = NOTES / language / "bibliography.md"
        if bibliography_path.is_file():
            bibliography = bibliography_path.read_text(encoding="utf-8")
        else:
            bibliography = ""
            failures.append(f"{language}: missing bibliography.md")
        failures.extend(
            f"{language}: {error}"
            for error in audit_source_locations(text, bibliography, english_source_links)
        )
        rows = table_rows(text)
        if len(rows) == len(english_rows):
            for index, (translated, original) in enumerate(zip(rows, english_rows), 1):
                # Native month names and ordinals may add digits (March -> 3
                # in Japanese). They must not remove the source's numeric data.
                if Counter(NUMBER.findall(" ".join(original))) - Counter(NUMBER.findall(" ".join(translated))):
                    failures.append(f"{language}: table row {index} has missing/changed numerals")
    return failures

def main() -> int:
    failures = audit()
    if failures:
        print("\n".join(failures))
        return 1
    print(f"Curated feast table verified in {len(LANGUAGES)} languages: 47 rows each; complete catalog absent")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
