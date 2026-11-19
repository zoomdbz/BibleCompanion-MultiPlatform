#!/usr/bin/env python3
"""Compare the offline English day order with Bible.com's current public calendar.

This reads only the reference in each page title. It does not download or save
Bible text. Bible.com may change scheduled passages, so a clean result applies
to the calendar exposed at the time of this run.
"""

from __future__ import annotations

import calendar
from concurrent.futures import ThreadPoolExecutor
from datetime import date
import html
import json
from pathlib import Path
import re
import time
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
TITLE = re.compile(rb"<title>Verse of the Day - (.*?) - Bible App</title>")
BASE = "https://www.bible.com/verse-of-the-day?day={}"


def published_reference(day: int) -> str:
    url = BASE.format(day)
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "Mozilla/5.0", "Range": "bytes=0-32767"})
            with urlopen(request, timeout=25) as response:
                page = response.read()
            match = TITLE.search(page)
            if not match:
                with urlopen(Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=25) as response:
                    match = TITLE.search(response.read())
            if not match:
                raise ValueError("Verse of the Day title missing")
            return html.unescape(match.group(1).decode("utf-8"))
        except Exception:
            if attempt == 2:
                raise
            time.sleep(0.5 * (attempt + 1))
    raise AssertionError("unreachable")


def main() -> int:
    bank = json.loads((ROOT / "shared/assets/daily_verses/en/daily.json").read_text(encoding="utf-8"))["verses"]
    if len(bank) != 366:
        raise ValueError("English daily bank must have 366 entries")
    year = date.today().year
    days = 366 if calendar.isleap(year) else 365
    with ThreadPoolExecutor(max_workers=8) as pool:
        outcomes = list(pool.map(lambda day: (day, published_reference(day)), range(1, days + 1)))
    mismatches = []
    for day, source in outcomes:
        local = bank[day - 1]["ref"].replace("Acts of the Apostles", "Acts")
        if local != source:
            mismatches.append((day, local, source))
    print(f"Compared {days} day references with Bible.com for {year}: {len(mismatches)} mismatches")
    for day, local, source in mismatches:
        print(f"day {day}: app={local} | Bible.com={source} | {BASE.format(day)}")
    return int(bool(mismatches))


if __name__ == "__main__":
    raise SystemExit(main())
