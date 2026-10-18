"""Verify approved study-text coverage, native links, and scripture preservation.

Structural checks complement the separate semantic translation review. They
do not turn length, script, or address checks into a translation-quality claim.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
import json
from pathlib import Path
import re
import subprocess
import sys

from summary_review_localization import (
    IMPLEMENTATION, LANGUAGES, REPORT, ROOT, WORK, address, asset, baseline, chapter, read,
    template as changed_plan,
)
sys.path.insert(0, str(ROOT / 'tools/prophecy'))
from audit_note_references import alias_pattern, citations, normalize, validate


NATIVE_SCRIPT = {
    'ar': r'[\u0600-\u06ff]', 'hi': r'[\u0900-\u097f]',
    'ru': r'[\u0400-\u04ff]', 'ja': r'[\u3040-\u30ff\u3400-\u9fff]',
    'ko': r'[\u1100-\u11ff\uac00-\ud7af]',
    'zh-Hans': r'[\u3400-\u9fff]', 'zh-Hant': r'[\u3400-\u9fff]',
}


def native_script_present(language, value):
    pattern = NATIVE_SCRIPT.get(language)
    return pattern is None or re.search(pattern, normalize(value)) is not None


def documented_adjustment(adjustments, language, row, field, index, before, after):
    """Allow only an independently reviewed, exact native-edition correction."""
    return any(
        item['language'] == language and item['row_id'] == row and
        item['field'] == field and item['index'] == index and
        item['before'] == before and item['after'] == after and
        item.get('reason', '').strip() and item.get('reviewed_by', '').strip()
        for item in adjustments
    )


def audit(language):
    rows = read(REPORT)['rows']
    targets = defaultdict(set)
    for row in rows:
        targets[asset(row, language)].add(row['chapter'])
    approved_files = len(targets)
    for collection in ('old_testament', 'new_testament'):
        for book, _ in read(ROOT / f'shared/assets/books/{collection}/{language}/_index.json'):
            targets[ROOT / f'shared/assets/books/{collection}/{language}/{book}.json']
    errors = []
    adjustments_path = REPORT.with_name('translation_adjustments.json')
    adjustments = read(adjustments_path)['entries'] if adjustments_path.exists() else []
    if IMPLEMENTATION.exists():
        common_plan = read(IMPLEMENTATION)['english_plan']
    else:
        common_path = WORK / 'english_manifest.json'
        common_plan = read(common_path) if common_path.exists() else []
    counters = {'rows': len(rows), 'files': len(targets), 'files_with_approved_changes': approved_files,
                'planned_note_slots': sum('slot' in item for row in common_plan for item in row['notes_template']),
                'planned_reference_slots': sum('slot' in item for row in common_plan for item in row['refs_template']),
                'new_or_changed_notes': 0,
                'new_or_changed_references': 0, 'canonical_chapters_preserved': 0}
    for path, approved in targets.items():
        old, new = baseline(path), read(path)
        if {k:v for k,v in old.items() if k != 'stories'} != {k:v for k,v in new.items() if k != 'stories'}:
            errors.append(f'Book metadata changed: {path}')
        if [s['id'] for s in old['stories']] != [s['id'] for s in new['stories']]:
            errors.append(f'Story identities/order changed: {path}')
            continue
        for original, current in zip(old['stories'], new['stories']):
            number = int(original['id'].rsplit('-', 1)[1])
            allowed = {'keyTakeaway', 'crossRefs', 'translationNotes'} if number in approved else set()
            if {k:v for k,v in original.items() if k not in allowed} != {k:v for k,v in current.items() if k not in allowed}:
                errors.append(f'Protected scripture/fields changed: {language}/{original["id"]}')
            if number in approved:
                for reference in current.get('crossRefs', []):
                    prefix, _, context = reference.partition(': ')
                    if not context.strip():
                        errors.append(f'Missing reference context: {language}/{original["id"]}: {reference}')
                    parsed = citations(language, prefix)
                    if not parsed:
                        errors.append(f'Unrecognized reference: {language}/{original["id"]}: {prefix}')
                    for ref in parsed:
                        errors.extend(f'{language}/{original["id"]}: {prefix}: {error}'
                                      for error in validate(language, ref))
            counters['canonical_chapters_preserved'] += 1
    if language == 'en':
        for row in rows:
            current = chapter(read(asset(row, language)), row['chapter'])
            counters['new_or_changed_notes'] += sum(
                'slot' in item for item in changed_plan(
                    row['existing_translation_notes'], current['translationNotes'], note=True
                )
            )
            counters['new_or_changed_references'] += sum(
                'slot' in item for item in changed_plan(row['existing_cross_references'], current['crossRefs'])
            )
            if current['keyTakeaway'] != row['proposed_summary']:
                errors.append(f'{row["id"]}: approved English summary differs')
            if not any(row['proposed_note'] in n['note'] for n in current['translationNotes']):
                errors.append(f'{row["id"]}: approved English note missing')
            for reference in row['cross_references']:
                if reference not in current['crossRefs']:
                    errors.append(f'{row["id"]}: approved English reference missing: {reference}')
        return {'language': language, **counters, 'errors': errors, 'passed': not errors}
    if IMPLEMENTATION.exists():
        implementation = read(IMPLEMENTATION)
        template = implementation['english_plan']
        completed = set(implementation['completed_rows'].get(language, []))
    else:
        template = read(WORK / f'template_{language}.json')['rows']
        receipt = WORK / f'applied_{language}.json'
        completed = set(read(receipt)) if receipt.exists() else set()
    expected = {r['id'] for r in rows}
    if completed != expected:
        errors.append('Unapplied rows: ' + ', '.join(sorted(expected - completed)))
    for row in template:
        current = chapter(read(asset(row, language)), row['chapter'])
        original = chapter(baseline(asset(row, language)), row['chapter'])
        if not current['keyTakeaway'].strip():
            errors.append(f'{row["id"]}: empty summary')
        if current['keyTakeaway'] == row['summary_english']:
            errors.append(f'{row["id"]}: untranslated English summary')
        if not native_script_present(language, current['keyTakeaway']):
            errors.append(f'{row["id"]}: native script missing from summary')
        if len(current['translationNotes']) != len(row['notes_template']):
            errors.append(f'{row["id"]}: translated note count differs from English plan')
            continue
        for index, (item, note) in enumerate(zip(row['notes_template'], current['translationNotes'])):
            if 'retain' in item:
                before = original['translationNotes'][item['retain']]
                counters['new_or_changed_notes'] += note != before
                if note != before and not documented_adjustment(
                    adjustments, language, row['id'], 'translationNotes', index, before, note
                ):
                    errors.append(f'{row["id"]}: unrelated retained note changed')
                continue
            counters['new_or_changed_notes'] += 1
            if not note.get('term', '').strip() or not note.get('note', '').strip():
                errors.append(f'{row["id"]}/{item["slot"]}: empty translated note')
            if note.get('note') == item['english']['note']:
                errors.append(f'{row["id"]}/{item["slot"]}: untranslated English note')
            if not native_script_present(language, note['note']):
                errors.append(f'{row["id"]}/{item["slot"]}: native script missing from note')
            i = item['original_index']
            if i is not None and 'original' in original['translationNotes'][i]:
                if note.get('original') != original['translationNotes'][i]['original']:
                    errors.append(f'{row["id"]}/{item["slot"]}: original-language metadata changed')
        if len(current['crossRefs']) != len(row['refs_template']):
            errors.append(f'{row["id"]}: translated reference count differs from English plan')
            continue
        for index, (item, value) in enumerate(zip(row['refs_template'], current['crossRefs'])):
            if 'retain' in item:
                before = original['crossRefs'][item['retain']]
                counters['new_or_changed_references'] += value != before
                if value != before and not documented_adjustment(
                    adjustments, language, row['id'], 'crossRefs', index, before, value
                ):
                    errors.append(f'{row["id"]}: unrelated retained reference changed')
                continue
            counters['new_or_changed_references'] += 1
            prefix, separator, context = value.partition(': ')
            if not separator or not context.strip():
                errors.append(f'{row["id"]}/{item["slot"]}: missing reference context')
            if not native_script_present(language, prefix) or not native_script_present(language, context):
                errors.append(f'{row["id"]}/{item["slot"]}: native script missing from reference or context')
            parsed = citations(language, prefix)
            source = citations('en', address(item['english']))
            _, pattern = alias_pattern(language)
            normalized = normalize(prefix)
            matches = list(pattern.finditer(normalized))
            uncovered = normalized
            for match in reversed(matches):
                uncovered = uncovered[:match.start()] + uncovered[match.end():]
            if (not parsed or not matches or matches[0].start() != 0 or uncovered.strip(' ;,') or
                [r.book for r in parsed] != [r.book for r in source]):
                errors.append(f'{row["id"]}/{item["slot"]}: invalid or wrong-book native citation: {prefix}')
            for ref in parsed:
                errors.extend(f'{row["id"]}/{item["slot"]}: {prefix}: {error}' for error in validate(language, ref))
            if context == item['english'].partition(': ')[2]:
                errors.append(f'{row["id"]}/{item["slot"]}: untranslated English reference description')
    return {'language': language, **counters, 'errors': errors, 'passed': not errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--language', action='append', choices=('en', *LANGUAGES))
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    results = [audit(language) for language in args.language or ('en', *LANGUAGES)]
    payload = {'checks': 'Coverage, exact approved English, protected fields, localized note/reference structure, native address validity; semantic review recorded separately.',
               'results': results, 'passed': all(r['passed'] for r in results)}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n')
    for result in results:
        print(result['language'], 'PASS' if result['passed'] else 'FAIL',
              f"{result['rows']} rows; {len(result['errors'])} errors")
        for error in result['errors'][:20]:
            print(' ', error)
    return 0 if payload['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
