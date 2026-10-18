"""Apply reviewed study-text translations without changing canonical scripture.

The approved review and completed English edits define the translation slots.
Existing localized notes/references are retained only where English retained
the corresponding original. New prose is supplied by reviewed language packs.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import replace
from functools import lru_cache
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'tools/reports/summary_review/approval_table.json'
IMPLEMENTATION = REPORT.with_name('implementation_manifest.json')
WORK = Path('/tmp/summary_localization')
LANGUAGES = ('ar', 'de', 'es', 'fr', 'hi', 'it', 'ja', 'ko', 'pt', 'ru', 'zh-Hans', 'zh-Hant')
sys.path.insert(0, str(ROOT / 'tools/prophecy'))
from audit_note_references import alias_pattern, citations, normalize, validate


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def dump(path, value):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def chapter(document, number):
    matches = [s for s in document['stories'] if int(s['id'].rsplit('-', 1)[1]) == number]
    if len(matches) != 1:
        raise ValueError(f'Chapter identity is not unique: {document["id"]} {number}')
    return matches[0]


def asset(row, language):
    return ROOT / 'shared/assets/books' / row['collection'] / language / f'{row["book"]}.json'


@lru_cache(maxsize=200)
def baseline(path):
    commit = read(REPORT)['review']['source_commit']
    relative = Path(path).relative_to(ROOT).as_posix()
    return json.loads(subprocess.check_output(['git', 'show', f'{commit}:{relative}'], cwd=ROOT))


def sig(ref):
    return (ref.book, ref.chapter, ref.verse, ref.end_chapter, ref.end_verse)


def address(value):
    return value.partition(': ')[0]


def template(old, new, note=False):
    remaining = set(range(len(old)))
    result = []
    for index, item in enumerate(new):
        exact = next((i for i in sorted(remaining) if old[i] == item), None)
        if exact is not None:
            remaining.remove(exact)
            result.append({'retain': exact})
            continue
        if note:
            old_index = next((i for i in sorted(remaining)
                              if old[i].get('term') == item.get('term') and
                              old[i].get('original') == item.get('original')), None)
        else:
            old_index = next((i for i in sorted(remaining) if address(old[i]) == address(item)), None)
        if old_index is not None:
            remaining.remove(old_index)
        result.append({'slot': ('N' if note else 'R') + str(index), 'english': item,
                       'original_index': old_index})
    return result


def names(language):
    english = read(ROOT / 'shared/assets/refs/en/book_aliases.json')
    local = read(ROOT / f'shared/assets/refs/{language}/book_aliases.json')
    canonical = {}
    for collection in ('old_testament', 'new_testament', 'deuterocanonical'):
        canonical.update({title.casefold(): book for book, title in
                          read(ROOT / f'shared/assets/books/{collection}/en/_index.json')})
    result = {}
    for en, native in zip(english, local):
        # Match the complete canonical title. A substring parser would let
        # Gospel of the Hebrews overwrite the actual epistle's title.
        book = canonical.get(en['canon'].casefold())
        if book:
            result.setdefault(book, native['canon'])
    return result


def format_ref(ref, titles):
    suffix = str(ref.chapter)
    if ref.verse is not None:
        suffix += ':' + str(ref.verse)
    if ref.end_chapter != ref.chapter:
        suffix += '-' + str(ref.end_chapter)
        if ref.end_verse is not None:
            suffix += ':' + str(ref.end_verse)
    elif ref.end_verse is not None and ref.end_verse != ref.verse:
        suffix += '-' + str(ref.end_verse)
    return titles[ref.book] + ' ' + suffix


def coordinate_hints(language):
    """Harvest repeated existing native citation pairs as hints, never proof."""
    exact = defaultdict(Counter)
    points = defaultdict(Counter)
    for collection in ('old_testament', 'new_testament'):
        for book, title in read(ROOT / f'shared/assets/books/{collection}/en/_index.json'):
            en_path = ROOT / f'shared/assets/books/{collection}/en/{book}.json'
            native_path = ROOT / f'shared/assets/books/{collection}/{language}/{book}.json'
            original = baseline(en_path)
            native = baseline(native_path)
            by_chapter = {int(s['id'].rsplit('-', 1)[1]): s for s in native['stories']}
            for en_story in original['stories']:
                local_story = by_chapter.get(int(en_story['id'].rsplit('-', 1)[1]))
                if local_story is None:
                    continue
                source_refs, target_refs = en_story.get('crossRefs', []), local_story.get('crossRefs', [])
                if len(source_refs) != len(target_refs):
                    continue
                for source, target in zip(source_refs, target_refs):
                    a, b = citations('en', address(source)), citations(language, address(target))
                    if len(a) != len(b):
                        continue
                    for first, second in zip(a, b):
                        if first.book != second.book:
                            continue
                        exact[sig(first)][sig(second)] += 1
                        for ac, av, bc, bv in ((first.chapter, first.verse, second.chapter, second.verse),
                                              (first.end_chapter, first.end_verse, second.end_chapter, second.end_verse)):
                            if av is not None and bv is not None:
                                points[(first.book, ac, av)][(bc, bv)] += 1
    return exact, points


def hint(value, language, titles, exact, points):
    out, evidence = [], []
    for ref in citations('en', address(value)):
        candidates = exact.get(sig(ref), {})
        if candidates:
            chosen, count = Counter(candidates).most_common(1)[0]
            mapped = replace(ref, book=chosen[0], chapter=chosen[1], verse=chosen[2],
                             end_chapter=chosen[3], end_verse=chosen[4])
            reason = f'existing localized reference pair ({count} observations); verify meaning'
        else:
            start = points.get((ref.book, ref.chapter, ref.verse), {})
            end = points.get((ref.book, ref.end_chapter, ref.end_verse), {})
            if start and end:
                a = Counter(start).most_common(1)[0][0]
                b = Counter(end).most_common(1)[0][0]
                mapped = replace(ref, chapter=a[0], verse=a[1], end_chapter=b[0], end_verse=b[1])
                reason = 'existing native endpoint observations; verify full passage'
            else:
                mapped = ref
                reason = 'same coordinates only; native semantic verification required'
        out.append(format_ref(mapped, titles))
        evidence.append(reason)
    return '; '.join(out), '; '.join(evidence)


def prepare():
    rows = read(REPORT)['rows']
    english = []
    for row in rows:
        story = chapter(read(asset(row, 'en')), row['chapter'])
        if story['keyTakeaway'] != row['proposed_summary']:
            raise ValueError(f'English summary not final: {row["id"]}')
        if not any(row['proposed_note'] in n['note'] for n in story['translationNotes']):
            raise ValueError(f'English approved note missing: {row["id"]}')
        english.append({
            'id': row['id'], 'collection': row['collection'], 'book': row['book'], 'chapter': row['chapter'],
            'summary_english': story['keyTakeaway'],
            'notes_template': template(row['existing_translation_notes'], story['translationNotes'], note=True),
            'refs_template': template(row['existing_cross_references'], story['crossRefs']),
        })
    dump(WORK / 'english_manifest.json', english)
    for language in LANGUAGES:
        titles = names(language)
        exact, points = coordinate_hints(language)
        payload = []
        for source, row in zip(english, rows):
            item = json.loads(json.dumps(source))
            old = chapter(baseline(asset(row, language)), row['chapter'])
            if len(old.get('translationNotes', [])) != len(row['existing_translation_notes']):
                raise ValueError(f'Native note layout differs: {language}/{row["id"]}')
            item['summary_current'] = old['keyTakeaway']
            for entry in item['notes_template']:
                if 'slot' in entry:
                    i = entry['original_index']
                    entry['current'] = old['translationNotes'][i] if i is not None else None
            for entry in item['refs_template']:
                if 'slot' in entry:
                    i = entry['original_index']
                    entry['current'] = old['crossRefs'][i] if i is not None else None
                    entry['citation_hint'], entry['hint_basis'] = hint(entry['english'], language, titles, exact, points)
            payload.append(item)
        dump(WORK / f'template_{language}.json', {'language': language, 'rows': payload})
        print(f'Prepared {language}: {len(payload)} rows', flush=True)


def apply(language, filename):
    source = read(WORK / f'template_{language}.json')
    package = read(filename)
    if package['language'] != language:
        raise ValueError('Language package identity mismatch')
    targets = {r['id']: r for r in source['rows']}
    if len({r['id'] for r in package['rows']}) != len(package['rows']):
        raise ValueError('Duplicate translated row')
    # Packages may be applied in bounded batches; every row is audited at the end.
    documents = {}
    for translated in package['rows']:
        row = targets[translated['id']]
        path = asset(row, language)
        doc = documents.setdefault(path, read(path))
        story = chapter(doc, row['chapter'])
        old = chapter(baseline(path), row['chapter'])
        summary = translated['summary']
        if summary == '$keep':
            summary = old['keyTakeaway']
        if not summary.strip():
            raise ValueError(f'Empty translated summary: {language}/{row["id"]}')
        expected_notes = {n['slot'] for n in row['notes_template'] if 'slot' in n}
        expected_refs = {n['slot'] for n in row['refs_template'] if 'slot' in n}
        if set(translated['notes']) != expected_notes or set(translated['refs']) != expected_refs:
            raise ValueError(f'Translation slot mismatch: {language}/{row["id"]}')
        notes = []
        for item in row['notes_template']:
            if 'retain' in item:
                notes.append(old['translationNotes'][item['retain']])
            else:
                value = translated['notes'][item['slot']]
                if not value['term'].strip() or not value['note'].strip():
                    raise ValueError(f'Empty translated note: {language}/{row["id"]}')
                native_note = dict(item['english'])
                i = item['original_index']
                if i is not None and 'original' in old['translationNotes'][i]:
                    native_note['original'] = old['translationNotes'][i]['original']
                native_note.update(value)
                notes.append(native_note)
        refs = []
        for item in row['refs_template']:
            if 'retain' in item:
                refs.append(old['crossRefs'][item['retain']])
            else:
                value = translated['refs'][item['slot']]
                prefix, separator, context = value.partition(': ')
                if not separator or not context.strip():
                    raise ValueError(f'Bare reference: {language}/{row["id"]}')
                parsed = citations(language, prefix)
                source_refs = citations('en', address(item['english']))
                _, pattern = alias_pattern(language)
                normalized = normalize(prefix)
                matches = list(pattern.finditer(normalized))
                uncovered = normalized
                for match in reversed(matches):
                    uncovered = uncovered[:match.start()] + uncovered[match.end():]
                if (not parsed or not matches or matches[0].start() != 0 or
                    uncovered.strip(' ;,') or
                    [r.book for r in parsed] != [r.book for r in source_refs] or
                    any(validate(language, ref) for ref in parsed)):
                    raise ValueError(f'Invalid native reference: {language}/{row["id"]}: {value}')
                refs.append(value)
        story['keyTakeaway'] = summary
        story['translationNotes'] = notes
        story['crossRefs'] = refs
    for path, doc in documents.items():
        dump(path, doc)
    receipt = WORK / f'applied_{language}.json'
    applied = set(read(receipt)) if receipt.exists() else set()
    applied.update(r['id'] for r in package['rows'])
    dump(receipt, sorted(applied))
    print(f'Applied {len(package["rows"])} {language} rows; total completed {len(applied)}/{len(targets)}')


def archive():
    """Save the completed plan and coverage so later audits need no /tmp files."""
    rows = read(REPORT)['rows']
    expected = {row['id'] for row in rows}
    completed = {}
    for language in LANGUAGES:
        receipt = read(WORK / f'applied_{language}.json')
        if set(receipt) != expected:
            raise ValueError(f'Cannot archive incomplete language: {language}')
        completed[language] = receipt
    dump(IMPLEMENTATION, {
        'source_commit': read(REPORT)['review']['source_commit'],
        'english_plan': read(WORK / 'english_manifest.json'),
        'completed_rows': completed,
        'retained_corrections_report': 'translation_adjustments.json',
        'scope': 'Approved summaries and their contextual references/translation notes; canonical scripture preserved.',
    })
    print(f'Archived {len(rows)} rows across {len(completed)} translated languages')


def review_packets(languages=LANGUAGES):
    """Pair final English and native study prose for a different reviewer."""
    evidence = read(REPORT.with_name('edition_adaptations.json'))['records']
    for language in languages:
        source = read(WORK / f'template_{language}.json')['rows']
        expected = {row['id'] for row in source}
        if set(read(WORK / f'applied_{language}.json')) != expected:
            raise ValueError(f'Cannot review an incomplete language: {language}')
        entries = []
        for row in source:
            current = chapter(read(asset(row, language)), row['chapter'])
            entry = {key: row[key] for key in ('id', 'collection', 'book', 'chapter', 'summary_english')}
            entry['localized_summary'] = current['keyTakeaway']
            for field, plan in (('notes', 'notes_template'), ('references', 'refs_template')):
                values = current['translationNotes' if field == 'notes' else 'crossRefs']
                entry[field] = [
                    {'index': index, 'slot': item['slot'], 'english': item['english'], 'localized': value}
                    for index, (item, value) in enumerate(zip(row[plan], values)) if 'slot' in item
                ]
            entry['retained_note_indexes'] = [i for i, item in enumerate(row['notes_template']) if 'retain' in item]
            entry['edition_evidence'] = [
                item for item in evidence if item['language'] == language and item['id'] == row['id']
            ]
            entries.append(entry)
        dump(WORK / f'audit_packet_{language}.json', {'language': language, 'rows': entries})
        print(f'Prepared independent review packet: {language}, {len(entries)} rows')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('prepare')
    sub.add_parser('archive')
    review_command = sub.add_parser('review-packets')
    review_command.add_argument('--language', action='append', choices=LANGUAGES)
    command = sub.add_parser('apply')
    command.add_argument('language', choices=LANGUAGES)
    command.add_argument('package')
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare()
    elif args.command == 'archive':
        archive()
    elif args.command == 'review-packets':
        review_packets(args.language or LANGUAGES)
    else:
        apply(args.language, args.package)


if __name__ == '__main__':
    main()
