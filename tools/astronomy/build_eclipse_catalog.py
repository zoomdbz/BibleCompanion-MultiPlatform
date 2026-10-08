#!/usr/bin/env python3
"""Build the pinned 2015-2033 eclipse and Hebrew-calendar comparison catalog.

NASA's century catalogs publish the instant of greatest eclipse in Dynamical
Time (TD) and a modeled Delta T.  This script derives approximate Universal
Time with UT = TD - Delta T.  It deliberately does not call that result exact
UTC.  The seven Hebcal results are calendar-simultaneity comparisons at that
instant; they do not assert that an eclipse was visible from those locations.

The command writes deterministic JSON to stdout.  It never edits repository
files.  Network requests use at most four workers and bounded retries.
"""

from __future__ import annotations

import argparse
import copy
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
import html
import json
import re
import sys
import time
from pathlib import Path
from typing import Any, Iterable
import unicodedata
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo


START_YEAR = 2015
END_YEAR = 2033
MAX_FETCH_WORKERS = 4
FETCH_ATTEMPTS = 4
FETCH_TIMEOUT_SECONDS = 30
USER_AGENT = "BibleCompanion-EclipseCatalog/1.0 (+source-data build)"
DECADE_TIME_TOLERANCE_SECONDS = 1

SOLAR_CENTURY_URL = "https://eclipse.gsfc.nasa.gov/SEcat5/SE2001-2100.html"
LUNAR_CENTURY_URL = "https://eclipse.gsfc.nasa.gov/LEcat5/LE2001-2100.html"
SOLAR_DECADE_URLS = (
    "https://eclipse.gsfc.nasa.gov/SEdecade/SEdecade2011.html",
    "https://eclipse.gsfc.nasa.gov/SEdecade/SEdecade2021.html",
    "https://eclipse.gsfc.nasa.gov/SEdecade/SEdecade2031.html",
)
LUNAR_DECADE_URLS = (
    "https://eclipse.gsfc.nasa.gov/LEdecade/LEdecade2011.html",
    "https://eclipse.gsfc.nasa.gov/LEdecade/LEdecade2021.html",
    "https://eclipse.gsfc.nasa.gov/LEdecade/LEdecade2031.html",
)
HEBCAL_CONVERTER_URL = "https://www.hebcal.com/converter"
HEBCAL_CALENDAR_URL = "https://www.hebcal.com/hebcal"
HEBCAL_ZMANIM_URL = "https://www.hebcal.com/zmanim"

MONTHS = {
    "Jan": 1, "Feb": 2, "Mar": 3, "Apr": 4, "May": 5, "Jun": 6,
    "Jul": 7, "Aug": 8, "Sep": 9, "Oct": 10, "Nov": 11, "Dec": 12,
}
SOLAR_TYPES = {"P": "partial", "A": "annular", "T": "total", "H": "hybrid"}
LUNAR_TYPES = {"N": "penumbral", "P": "partial", "T": "total"}
DECADE_TYPES = {
    "Partial": "partial", "Annular": "annular", "Total": "total",
    "Hybrid": "hybrid", "Penumbral": "penumbral",
}


@dataclass(frozen=True)
class ReferenceLocation:
    id: str
    name: str
    geoname_id: int
    tzid: str
    calendar_schedule: str


LOCATIONS = (
    ReferenceLocation("jerusalem", "Jerusalem", 281184, "Asia/Jerusalem", "Israel"),
    ReferenceLocation("new_york", "New York", 5128581, "America/New_York", "Diaspora"),
    ReferenceLocation("santiago", "Santiago", 3871336, "America/Santiago", "Diaspora"),
    ReferenceLocation("tokyo", "Tokyo", 1850147, "Asia/Tokyo", "Diaspora"),
    ReferenceLocation("sydney", "Sydney", 2147714, "Australia/Sydney", "Diaspora"),
    ReferenceLocation("auckland", "Auckland", 2193733, "Pacific/Auckland", "Diaspora"),
    ReferenceLocation("honolulu", "Honolulu", 5856195, "Pacific/Honolulu", "Diaspora"),
)


def fetch_text(url: str) -> str:
    """Fetch one UTF-8 resource with bounded exponential retries."""
    last_error: Exception | None = None
    for attempt in range(FETCH_ATTEMPTS):
        try:
            request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/json"})
            with urlopen(request, timeout=FETCH_TIMEOUT_SECONDS) as response:
                charset = response.headers.get_content_charset() or "utf-8"
                return response.read().decode(charset, errors="strict")
        except Exception as exc:  # urllib exposes several transport exception types
            last_error = exc
            if attempt + 1 < FETCH_ATTEMPTS:
                time.sleep(0.5 * (2 ** attempt))
    raise RuntimeError(f"Failed after {FETCH_ATTEMPTS} attempts: {url}: {last_error}")


def fetch_many(urls: Iterable[str]) -> dict[str, str]:
    """Fetch unique URLs using no more than four concurrent workers."""
    ordered = list(dict.fromkeys(urls))
    results: dict[str, str] = {}
    with ThreadPoolExecutor(max_workers=MAX_FETCH_WORKERS) as executor:
        future_urls = {executor.submit(fetch_text, url): url for url in ordered}
        for future in as_completed(future_urls):
            url = future_urls[future]
            results[url] = future.result()
    return results


def json_url(base: str, **params: object) -> str:
    return f"{base}?{urlencode(params)}"


def visible_text(document: str) -> str:
    without_tags = re.sub(r"<[^>]+>", " ", document)
    return html.unescape(without_tags).replace("\xa0", " ")


def nasa_datetime(year: int, month: int, day: int, clock: str) -> datetime:
    """Parse a NASA clock defensively, allowing a boundary value to carry."""
    hour, minute, second = (int(part) for part in clock.split(":"))
    midnight = datetime(year, month, day, tzinfo=timezone.utc)
    return midnight + timedelta(hours=hour, minutes=minute, seconds=second)


def clock_difference_seconds(left: str, right: str) -> int:
    """Return right-left on a 24-hour clock, normalized across midnight."""
    def seconds(clock: str) -> int:
        hour, minute, second = (int(part) for part in clock.split(":"))
        return hour * 3600 + minute * 60 + second

    difference = seconds(right) - seconds(left)
    return ((difference + 43200) % 86400) - 43200


def parse_century_catalog(document: str, body: str, source: str) -> list[dict[str, Any]]:
    """Parse NASA century catalog rows, including TD and published Delta T."""
    types = SOLAR_TYPES if body == "solar" else LUNAR_TYPES
    pattern = re.compile(
        r"(?m)^\s*\d+\s+(20\d{2})\s+([A-Z][a-z]{2})\s+(\d{2})\s+"
        r"(\d{2}:\d{2}:\d{2})\s+(-?\d+)\s+\d+\s+\d+\s+([A-Z])(?:[+\-me])?\s"
    )
    rows: list[dict[str, Any]] = []
    for match in pattern.finditer(visible_text(document)):
        year = int(match.group(1))
        if not START_YEAR <= year <= END_YEAR:
            continue
        month = MONTHS[match.group(2)]
        day = int(match.group(3))
        td_time = match.group(4)
        delta_t = int(match.group(5))
        code = match.group(6)
        if code not in types:
            raise ValueError(f"Unexpected NASA {body} eclipse type {code!r}")
        td = nasa_datetime(year, month, day, td_time)
        ut = td - timedelta(seconds=delta_t)
        rows.append({
            "date": ut.date().isoformat(),
            "nasaCatalogDateTd": td.date().isoformat(),
            "body": body,
            "type": types[code],
            "greatestTd": td.strftime("%Y-%m-%dT%H:%M:%S TD"),
            "deltaTSeconds": delta_t,
            "greatestUtApprox": ut.strftime("%Y-%m-%dT%H:%M:%S UT"),
            "provenance": {
                "centuryCatalog": source,
                "catalogTimeScale": "TD",
                "utDerivation": "UT = TD - published Delta T",
            },
            "_ut": ut,
        })
    if not rows:
        raise ValueError(f"No {body} rows parsed from {source}")
    return rows


def parse_decade_table(document: str, body: str, source: str) -> dict[tuple[str, str, str], dict[str, str]]:
    """Parse a NASA decade table independently for cross-checking."""
    pattern = re.compile(
        r"(20\d{2})\s+([A-Z][a-z]{2})\s+(\d{2})\s+"
        r"(\d{2}:\d{2}:\d{2})\s+(Partial|Annular|Total|Hybrid|Penumbral)\b"
    )
    rows: dict[tuple[str, str, str], dict[str, str]] = {}
    for match in pattern.finditer(visible_text(document)):
        year = int(match.group(1))
        if not START_YEAR <= year <= END_YEAR:
            continue
        event_date = f"{year:04d}-{MONTHS[match.group(2)]:02d}-{int(match.group(3)):02d}"
        kind = DECADE_TYPES[match.group(5)]
        key = (event_date, body, kind)
        if key in rows:
            raise ValueError(f"Duplicate decade-table event {key} in {source}")
        rows[key] = {"source": source, "greatestTd": match.group(4)}
    return rows


def attach_decade_cross_checks(
    events: list[dict[str, Any]], decade_documents: dict[str, str]
) -> None:
    cross_checks: dict[tuple[str, str, str], dict[str, str]] = {}
    for source, document in decade_documents.items():
        body = "solar" if "/SEdecade/" in source else "lunar"
        parsed = parse_decade_table(document, body, source)
        overlap = set(cross_checks).intersection(parsed)
        if overlap:
            raise ValueError(f"Duplicate events across NASA decade tables: {sorted(overlap)}")
        cross_checks.update(parsed)

    century_keys: set[tuple[str, str, str]] = set()
    for event in events:
        key = (event["nasaCatalogDateTd"], event["body"], event["type"])
        century_keys.add(key)
        checked = cross_checks.get(key)
        if checked is None:
            raise ValueError(f"NASA decade tables are missing century-catalog event {key}")
        td_clock = event["greatestTd"].split("T", 1)[1].split(" ", 1)[0]
        difference_seconds = clock_difference_seconds(td_clock, checked["greatestTd"])
        if abs(difference_seconds) > DECADE_TIME_TOLERANCE_SECONDS:
            raise ValueError(
                f"NASA time mismatch for {key}: century={td_clock}, decade={checked['greatestTd']}"
            )
        event["provenance"]["decadeCrossCheck"] = checked["source"]
        event["provenance"]["decadeCrossCheckMatched"] = True
        event["provenance"]["decadeGreatestTd"] = checked["greatestTd"]
        event["provenance"]["decadeTimeDifferenceSeconds"] = difference_seconds

    extra = set(cross_checks).difference(century_keys)
    if extra:
        raise ValueError(f"NASA decade tables contain unmatched in-range events: {sorted(extra)}")


def calendar_urls() -> dict[tuple[int, str], str]:
    urls: dict[tuple[int, str], str] = {}
    for year in range(START_YEAR, END_YEAR + 1):
        for schedule, israel in (("Israel", "on"), ("Diaspora", "off")):
            urls[(year, schedule)] = json_url(
                HEBCAL_CALENDAR_URL,
                v=1,
                cfg="json",
                year=year,
                yt="G",
                maj="on",
                min="on",
                nx="on",
                i=israel,
            )
    return urls


def holiday_index(calendar_documents: dict[tuple[int, str], dict[str, Any]]) -> dict[tuple[str, str], list[dict[str, Any]]]:
    """Index only requested major/minor holiday and Rosh Chodesh records."""
    index: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for (_, schedule), document in calendar_documents.items():
        for item in document.get("items", []):
            category = item.get("category")
            subcat = item.get("subcat")
            if category not in {"holiday", "roshchodesh"}:
                continue
            if category == "holiday" and subcat not in {"major", "minor"}:
                continue
            record: dict[str, Any] = {
                "title": item["title"],
                "category": category,
                "subcat": subcat or "roshchodesh",
                "yomTov": bool(item.get("yomtov", False)),
            }
            if item.get("memo"):
                record["memo"] = item["memo"]
            index.setdefault((schedule, item["date"][:10]), []).append(record)
    for records in index.values():
        records.sort(key=lambda row: (row["category"], row["subcat"], row["title"]))
    return index


CHANUKAH_CANDLES = re.compile(r"^Chanukah: (\d+) Candles?$")


def observance_id(title: str) -> str:
    """Create a stable, display-independent-enough identifier from a Hebcal title."""
    without_year = re.sub(r"\s+\d{4}$", "", title)
    ascii_title = unicodedata.normalize("NFKD", without_year).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", ascii_title.lower()).strip("-")


def semantic_observances(
    records: list[dict[str, Any]], hebrew_month: str, hebrew_day: int
) -> list[dict[str, Any]]:
    """Normalize instantaneous observances while retaining raw Hebcal records.

    Hebcal's ``Chanukah: N Candles`` item describes lighting at the end of its
    Gregorian date.  At an instant mapped to that annual-calendar date, the
    Chanukah day in progress is therefore N-1.  ``Chanukah: 1 Candle`` occurs
    before Chanukah begins and does not create an instantaneous observance.
    Other maj/min/nx records describe the Hebrew date in progress and pass
    through unchanged at the semantic layer.
    """
    semantic: list[dict[str, Any]] = []
    for record in records:
        title = record["title"]
        candle_match = CHANUKAH_CANDLES.fullmatch(title)
        if candle_match:
            candle_count = int(candle_match.group(1))
            chanukah_day = candle_count - 1
            if chanukah_day == 0:
                continue
            if hebrew_month == "Kislev" and hebrew_day - 24 != chanukah_day:
                raise ValueError(
                    f"Chanukah normalization mismatch: {title}, {hebrew_day} {hebrew_month}"
                )
            if hebrew_month == "Tevet" and not (1 <= hebrew_day <= 3 and 6 <= chanukah_day <= 8):
                raise ValueError(
                    f"Chanukah normalization mismatch: {title}, {hebrew_day} {hebrew_month}"
                )
            semantic.append({
                "id": "chanukah",
                "title": "Chanukah",
                "day": chanukah_day,
                "sourceTitle": title,
                "sourceTiming": "evening candle count; semantic day is candle count minus one",
            })
            continue
        if title == "Chanukah: 8th Day":
            semantic.append({
                "id": "chanukah",
                "title": "Chanukah",
                "day": 8,
                "sourceTitle": title,
                "sourceTiming": "all-day holiday label",
            })
            continue
        semantic.append({
            "id": observance_id(title),
            "title": title,
            "sourceTitle": title,
            "category": record["category"],
            "subcat": record["subcat"],
            "yomTov": record["yomTov"],
        })
    return semantic


def zmanim_url(location: ReferenceLocation, local_date: date) -> str:
    return json_url(
        HEBCAL_ZMANIM_URL,
        cfg="json",
        geonameid=location.geoname_id,
        date=local_date.isoformat(),
        sec=1,
    )


def converter_url(local_date: date, after_sunset: bool) -> str:
    params: dict[str, object] = {
        "cfg": "json",
        "date": local_date.isoformat(),
        "g2h": 1,
        "strict": 1,
    }
    if after_sunset:
        params["gs"] = "on"
    return json_url(HEBCAL_CONVERTER_URL, **params)


def parse_iso(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def event_key(event: dict[str, Any]) -> tuple[str, str, str]:
    return event["date"], event["body"], event["type"]


def reusable_location_outcomes(
    path: Path | None, events: list[dict[str, Any]]
) -> dict[tuple[tuple[str, str, str], str], dict[str, Any]]:
    """Load verified outcomes whose event instant and location definition still match."""
    if path is None:
        return {}
    with path.open("r", encoding="utf-8") as handle:
        prior = json.load(handle)
    current_events = {event_key(event): event for event in events}
    locations = {location.id: location for location in LOCATIONS}
    reusable: dict[tuple[tuple[str, str, str], str], dict[str, Any]] = {}
    for prior_event in prior.get("events", []):
        key = event_key(prior_event)
        current = current_events.get(key)
        if current is None or prior_event.get("greatestUtApprox") != current["greatestUtApprox"]:
            continue
        for outcome in prior_event.get("referenceLocations", []):
            location = locations.get(outcome.get("locationId"))
            if location is None:
                continue
            if (
                outcome.get("tzid") != location.tzid
                or outcome.get("calendarSchedule") != location.calendar_schedule
            ):
                continue
            reusable[(key, location.id)] = copy.deepcopy(outcome)
    return reusable


def add_location_comparisons(
    events: list[dict[str, Any]],
    holiday_records: dict[tuple[str, str], list[dict[str, Any]]],
    reused_outcomes: dict[tuple[tuple[str, str, str], str], dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Add Hebcal results for all named locations at each greatest instant."""
    reused_outcomes = reused_outcomes or {}
    zmanim_requests: dict[tuple[str, str], str] = {}
    local_instants: dict[tuple[int, str], datetime] = {}
    for event_index, event in enumerate(events):
        ut: datetime = event["_ut"]
        for location in LOCATIONS:
            local = ut.astimezone(ZoneInfo(location.tzid))
            local_instants[(event_index, location.id)] = local
            if (event_key(event), location.id) in reused_outcomes:
                continue
            key = (location.id, local.date().isoformat())
            zmanim_requests[key] = zmanim_url(location, local.date())

    zmanim_text = fetch_many(zmanim_requests.values())
    zmanim = {key: json.loads(zmanim_text[url]) for key, url in zmanim_requests.items()}

    converter_requests: dict[tuple[str, bool], str] = {}
    sunset_decisions: dict[tuple[int, str], tuple[datetime, bool]] = {}
    for event_index, event in enumerate(events):
        for location in LOCATIONS:
            if (event_key(event), location.id) in reused_outcomes:
                continue
            local = local_instants[(event_index, location.id)]
            response = zmanim[(location.id, local.date().isoformat())]
            sunset = parse_iso(response["times"]["sunset"])
            after_sunset = local >= sunset
            sunset_decisions[(event_index, location.id)] = (sunset, after_sunset)
            key = (local.date().isoformat(), after_sunset)
            converter_requests[key] = converter_url(local.date(), after_sunset)

    converter_text = fetch_many(converter_requests.values())
    converters = {key: json.loads(converter_text[url]) for key, url in converter_requests.items()}

    closest: dict[str, Any] | None = None
    unique_observances: set[str] = set()
    unique_semantic_observances: set[str] = set()
    for event_index, event in enumerate(events):
        comparisons: list[dict[str, Any]] = []
        for location in LOCATIONS:
            reused = reused_outcomes.get((event_key(event), location.id))
            if reused is not None:
                comparison = copy.deepcopy(reused)
                records = comparison["observances"]
                semantic_records = comparison["semanticObservances"]
                margin_seconds = int(comparison["sunsetMarginSeconds"])
            else:
                local = local_instants[(event_index, location.id)]
                sunset, after_sunset = sunset_decisions[(event_index, location.id)]
                effective = local.date() + timedelta(days=1 if after_sunset else 0)
                converted = converters[(local.date().isoformat(), after_sunset)]
                margin_seconds = round((local - sunset).total_seconds())
                records = holiday_records.get((location.calendar_schedule, effective.isoformat()), [])
                semantic_records = semantic_observances(records, converted["hm"], converted["hd"])
                comparison = {
                    "locationId": location.id,
                    "location": location.name,
                    "tzid": location.tzid,
                    "calendarSchedule": location.calendar_schedule,
                    "scope": "calendar simultaneity at greatest eclipse; visibility not assessed",
                    "localDateTime": local.isoformat(timespec="seconds"),
                    "utcOffset": local.strftime("%z")[:3] + ":" + local.strftime("%z")[3:],
                    "sunsetLocal": sunset.isoformat(timespec="seconds"),
                    "sunsetMarginSeconds": margin_seconds,
                    "afterSunset": after_sunset,
                    "effectiveCivilDate": effective.isoformat(),
                    "hebrewDate": {
                        "year": converted["hy"],
                        "month": converted["hm"],
                        "day": converted["hd"],
                        "display": converted["hebrew"],
                    },
                    "observances": records,
                    "semanticObservances": semantic_records,
                }
            unique_observances.update(record["title"] for record in records)
            unique_semantic_observances.update(record["title"] for record in semantic_records)
            comparisons.append(comparison)
            candidate = {
                "absoluteMarginSeconds": abs(margin_seconds),
                "signedMarginSeconds": margin_seconds,
                "eventDate": event["date"],
                "body": event["body"],
                "type": event["type"],
                "locationId": location.id,
                "localDateTime": comparison["localDateTime"],
                "sunsetLocal": comparison["sunsetLocal"],
            }
            if closest is None or candidate["absoluteMarginSeconds"] < closest["absoluteMarginSeconds"]:
                closest = candidate
        event["referenceLocations"] = comparisons

    validate_semantic_regressions(events)
    return {
        "closestSunsetMargin": closest,
        "uniqueObservanceTitles": sorted(unique_observances),
        "uniqueSemanticObservanceTitles": sorted(unique_semantic_observances),
    }


def validate_semantic_regressions(events: list[dict[str, Any]]) -> None:
    """Lock the two source-normalization cases that exposed candle-title drift."""
    by_date = {event["date"]: event for event in events}

    def chanukah_day(event_date: str, location_id: str) -> int | None:
        event = by_date[event_date]
        location = next(row for row in event["referenceLocations"] if row["locationId"] == location_id)
        record = next(
            (row for row in location["semanticObservances"] if row["id"] == "chanukah"),
            None,
        )
        return None if record is None else int(record["day"])

    for location in LOCATIONS:
        if chanukah_day("2019-12-26", location.id) != 4:
            raise ValueError(f"2019-12-26 must be Chanukah day 4 at {location.id}")
    expected_2029 = {
        "jerusalem": 5,
        "new_york": 4,
        "santiago": 4,
        "tokyo": 5,
        "sydney": 5,
        "auckland": 5,
        "honolulu": 4,
    }
    for location_id, expected_day in expected_2029.items():
        if chanukah_day("2029-12-05", location_id) != expected_day:
            raise ValueError(f"2029-12-05 must be Chanukah day {expected_day} at {location_id}")

    event_2021 = by_date["2021-12-04"]
    jerusalem = next(row for row in event_2021["referenceLocations"] if row["locationId"] == "jerusalem")
    if not any(row["id"] == "chag-habanot" for row in jerusalem["semanticObservances"]):
        raise ValueError("Chag HaBanot must remain an all-day semantic observance")


def build_catalog(reuse_catalog: Path | None = None) -> dict[str, Any]:
    nasa_urls = [SOLAR_CENTURY_URL, LUNAR_CENTURY_URL, *SOLAR_DECADE_URLS, *LUNAR_DECADE_URLS]
    nasa_documents = fetch_many(nasa_urls)
    events = parse_century_catalog(nasa_documents[SOLAR_CENTURY_URL], "solar", SOLAR_CENTURY_URL)
    events.extend(parse_century_catalog(nasa_documents[LUNAR_CENTURY_URL], "lunar", LUNAR_CENTURY_URL))
    events.sort(key=lambda row: (row["_ut"], row["body"], row["type"]))
    attach_decade_cross_checks(
        events,
        {url: nasa_documents[url] for url in (*SOLAR_DECADE_URLS, *LUNAR_DECADE_URLS)},
    )

    calendar_request_urls = calendar_urls()
    calendar_text = fetch_many(calendar_request_urls.values())
    calendar_documents = {
        key: json.loads(calendar_text[url]) for key, url in calendar_request_urls.items()
    }
    holiday_records = holiday_index(calendar_documents)
    reused_outcomes = reusable_location_outcomes(reuse_catalog, events)
    diagnostics = add_location_comparisons(events, holiday_records, reused_outcomes)

    for event in events:
        del event["_ut"]

    solar_count = sum(event["body"] == "solar" for event in events)
    lunar_count = len(events) - solar_count
    sources = {
        "nasaCenturyCatalogs": [SOLAR_CENTURY_URL, LUNAR_CENTURY_URL],
        "nasaIndependentDecadeCrossChecks": [*SOLAR_DECADE_URLS, *LUNAR_DECADE_URLS],
        "nasaDecadeCrossCheckToleranceSeconds": DECADE_TIME_TOLERANCE_SECONDS,
        "hebcal": {
            "zmanimApi": HEBCAL_ZMANIM_URL,
            "converterApi": HEBCAL_CONVERTER_URL,
            "annualCalendarApi": HEBCAL_CALENDAR_URL,
            "annualCalendarOptions": "maj=on, min=on, nx=on; modern holidays excluded",
        },
    }
    return {
        "schemaVersion": 1,
        "range": {"startYear": START_YEAR, "endYear": END_YEAR},
        "eventCount": {"total": len(events), "solar": solar_count, "lunar": lunar_count},
        "dateConvention": (
            "event date is the calendar date in approximate NASA UT after subtracting published "
            "Delta T from TD; this is not a claim of exact UTC"
        ),
        "calendarComparisonScope": (
            "Each reference-location result is calendar simultaneity at greatest eclipse only. "
            "Eclipse visibility and observance transitions during the full eclipse are not assessed."
        ),
        "referenceLocations": [
            {
                "id": location.id,
                "name": location.name,
                "geonameId": location.geoname_id,
                "tzid": location.tzid,
                "calendarSchedule": location.calendar_schedule,
            }
            for location in LOCATIONS
        ],
        "sources": sources,
        "diagnostics": diagnostics,
        "events": events,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compact", action="store_true", help="emit compact JSON")
    parser.add_argument(
        "--reuse-catalog",
        type=Path,
        help="reuse matching verified location outcomes from an existing generated catalog",
    )
    args = parser.parse_args(argv)
    catalog = build_catalog(args.reuse_catalog)
    json.dump(
        catalog,
        sys.stdout,
        ensure_ascii=True,
        indent=None if args.compact else 2,
        sort_keys=False,
        separators=(",", ":") if args.compact else None,
    )
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
