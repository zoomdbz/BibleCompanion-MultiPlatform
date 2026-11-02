#!/usr/bin/env python3
"""Check Swahili UI resource parity and the Android/iOS 6.2.0 release fields."""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "shared/src/commonMain/composeResources"
ANDROID = ROOT / "app/src/androidMain/res"
IOS = ROOT / "iosApp"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?(?:s|d|f|@)")
IOS_ENTRY = re.compile(r'^\s*"([^"\n]+)"\s*=\s*"((?:\\.|[^"\\])*)"\s*;', re.M)


def xml_strings(path: Path) -> dict[str, str]:
    result = {}
    for item in ET.parse(path).getroot().findall("string"):
        name = item.attrib["name"]
        if name in result:
            raise ValueError(f"Duplicate resource key: {path}:{name}")
        result[name] = "".join(item.itertext())
    return result


def check_pairs(label: str, english: dict[str, str], swahili: dict[str, str]) -> list[str]:
    issues = []
    if set(english) != set(swahili):
        issues.append(f"{label}: resource keys differ: missing={sorted(set(english)-set(swahili))}, "
                      f"extra={sorted(set(swahili)-set(english))}")
    for name in sorted(set(english) & set(swahili)):
        if not swahili[name].strip():
            issues.append(f"{label}/{name}: empty text")
        if PLACEHOLDER.findall(english[name]) != PLACEHOLDER.findall(swahili[name]):
            issues.append(f"{label}/{name}: placeholder mismatch")
    return issues


def ios_strings(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8-sig")
    entries = IOS_ENTRY.findall(text)
    names = [name for name, _ in entries]
    if len(names) != len(set(names)):
        raise ValueError(f"Duplicate iOS resource key: {path}")
    return dict(entries)


def main() -> int:
    issues = []
    compose_en = xml_strings(COMPOSE / "values/strings.xml")
    compose_sw = xml_strings(COMPOSE / "values-sw/strings.xml")
    issues += check_pairs("Compose", compose_en, compose_sw)
    for locale_dir in sorted(COMPOSE.glob("values*")):
        strings = xml_strings(locale_dir / "strings.xml")
        for key in ("ui_previous_book", "ui_next_book"):
            if not strings.get(key, "").strip():
                issues.append(f"Compose/{locale_dir.name}: missing {key}")
    android_en = xml_strings(ANDROID / "values/strings.xml")
    android_sw = xml_strings(ANDROID / "values-sw/strings.xml")
    issues += check_pairs("Android", android_en, android_sw)
    for location, file_name in (("iosApp", "InfoPlist.strings"),
                                ("iosApp", "Localizable.strings"),
                                ("FeastWidget", "Localizable.strings")):
        english = ios_strings(IOS / location / "en.lproj" / file_name)
        swahili = ios_strings(IOS / location / "sw.lproj" / file_name)
        issues += check_pairs(f"iOS/{location}/{file_name}", english, swahili)

    locale_xml = ET.parse(ANDROID / "xml/locales_config.xml").getroot()
    locales = {item.attrib.get("{http://schemas.android.com/apk/res/android}name")
               for item in locale_xml}
    if "sw" not in locales:
        issues.append("Android locale config lacks sw")
    project = (IOS / "iosApp.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    if not re.search(r"\bknownRegions\s*=\s*\([^)]*\bsw\s*,", project, re.S):
        issues.append("iOS project knownRegions lacks sw")
    if len(re.findall(r"CURRENT_PROJECT_VERSION\s*=\s*62\s*;", project)) != 4:
        issues.append("iOS project/version 62 count differs")
    if len(re.findall(r"MARKETING_VERSION\s*=\s*6\.2\.0\s*;", project)) != 4:
        issues.append("iOS project/version 6.2.0 count differs")
    android_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    if not re.search(r"\bversionCode\s*=\s*62\b", android_build):
        issues.append("Android versionCode differs")
    if not re.search(r'\bversionName\s*=\s*"6\.2\.0"', android_build):
        issues.append("Android versionName differs")
    print(f"Checked {len(compose_sw)} Compose and {len(android_sw)} Android Swahili UI strings, "
          f"iOS resources, locale registration, and version 62 / 6.2.0; "
          f"failures: {len(issues)}")
    for issue in issues:
        print(issue)
    return int(bool(issues))


if __name__ == "__main__":
    raise SystemExit(main())
