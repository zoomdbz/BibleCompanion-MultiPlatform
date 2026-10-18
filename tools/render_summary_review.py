import csv
import html
import json
from pathlib import Path

# The checked-in review artifact is authoritative; temporary draft inputs are not.
DEST = Path(__file__).resolve().parent / 'reports/summary_review'
document = json.loads((DEST / 'approval_table.json').read_text())
rows = document['rows']
metadata = document['review']
for row in rows:
    for field in ('cross_references', 'direct_references'):
        for value in row[field]:
            address, separator, context = value.partition(': ')
            assert separator and context.strip(), (row['id'], value)

fields = ['ID', 'Passage', 'Decision', 'Implementation', 'Original summary', 'Concern', 'What Scripture says', 'Direct references', 'Approved summary', 'Essential cross-references', 'Approved note', 'All implemented reference changes', 'All implemented note changes', 'Editorial rationale', 'Confidence', 'Source file', 'Translation source notes']
with (DEST / 'approval_table.csv').open('w', newline='', encoding='utf-8') as stream:
    writer = csv.writer(stream)
    writer.writerow(fields)
    for row in rows:
        writer.writerow([row['id'], row['passage'], row['decision'], row.get('implementation_status', 'In progress'), row['current_summary'], row['concern'], row['scripture_says'], '\n'.join(row['direct_references']), row['proposed_summary'], '\n'.join(row['cross_references']), row['proposed_note'], '\n'.join(row.get('implemented_reference_changes', [])), '\n'.join(note['term'] + ': ' + note['note'] for note in row.get('implemented_note_changes', [])), row['translation_context_note'], row['confidence'], row['source_file'], '\n'.join(row['source_urls'])])

def e(value): return html.escape(str(value), quote=True)
body = []
for row in rows:
    crossrefs = '<br>'.join(e(x) for x in row['cross_references']) or 'No additional contextual reference needed.'
    direct = '<br>'.join(e(x) for x in row['direct_references'])
    current_notes = ''.join('<p><strong>' + e(note.get('term', 'Note')) + ':</strong> ' + e(note['note']) + '</p>' for note in row['existing_translation_notes'])
    current_notes = current_notes.encode('ascii', 'xmlcharrefreplace').decode('ascii')
    changed_notes = ''.join('<p><strong>' + e(note['term']) + ':</strong> ' + e(note['note']) + '</p>' for note in row.get('implemented_note_changes', []))
    changed_notes = changed_notes.encode('ascii', 'xmlcharrefreplace').decode('ascii')
    changed_references = '<br>'.join(e(value) for value in row.get('implemented_reference_changes', []))
    implemented_notes = '<details><summary>All implemented note changes</summary>' + changed_notes + '</details>' if changed_notes else ''
    implemented_references = '<details><summary>All implemented reference changes</summary><p>' + changed_references + '</p></details>' if changed_references else ''
    sources = ' | '.join('<a href="' + e(url) + '">Translation source notes' + (f' {i + 1}' if len(row['source_urls']) > 1 else '') + '</a>' for i, url in enumerate(row['source_urls']))
    body.append(f'''<tr id="{e(row['id'])}"><th scope="row"><a href="#{e(row['id'])}">{e(row['id'])}</a><br>{e(row['passage'])}<span class="pending">{e(row.get("implementation_status", row["decision"]))}</span></th>
<td><p>{e(row['concern'])}</p><details><summary>Original app summary</summary><p>{e(row['current_summary'])}</p></details><details><summary>Original translation notes</summary>{current_notes}</details></td>
<td><p>{e(row['proposed_summary'])}</p><details><summary>Direct textual basis</summary><p>{e(row['scripture_says'])}</p><p>{direct}</p></details></td>
<td>{crossrefs}{implemented_references}</td><td><p>{e(row['proposed_note'])}</p>{implemented_notes}<p>{sources}</p><details><summary>Editorial rationale</summary><p>{e(row['translation_context_note'])}</p></details></td></tr>''')
document = '''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Bible Companion: summary approval table</title>
<style>
:root{color-scheme:light}*{box-sizing:border-box}body{font:15px/1.5 system-ui,sans-serif;margin:0;background:#f6f7f9;color:#152337}main{padding:28px;max-width:2000px;margin:auto}h1{font-size:28px;margin:0 0 12px}p{margin:0 0 12px}header{max-width:1100px}.notice{padding:14px 18px;background:#e5eefb;border-left:4px solid #285ea8;margin:20px 0}.controls{position:sticky;top:0;background:#f6f7f9;padding:12px 0;z-index:1;display:flex;gap:16px;align-items:center;flex-wrap:wrap}input{font:inherit;padding:10px;border:1px solid #9cabc0;border-radius:4px;min-width:320px}.scroll{overflow-x:auto}table{border-collapse:collapse;width:100%;min-width:1300px;background:white}th,td{border:1px solid #c9d2df;text-align:left;vertical-align:top;padding:14px}thead th{background:#203d65;color:white}tbody th{width:10%;background:#edf2f8}td:nth-child(2){width:21%}td:nth-child(3){width:26%}td:nth-child(4){width:15%}td:nth-child(5){width:28%}td p:last-child{margin-bottom:0}a{color:#174e96}details{margin-top:12px}summary{cursor:pointer;color:#335e95;font-weight:600}details p{margin-top:10px}.pending{display:block;font-size:12px;color:#5c6472;margin-top:12px}tr:target{outline:3px solid #285ea8;outline-offset:-3px}footer{margin:24px 0;color:#526174}@media print{.controls{display:none}main{padding:0}table{min-width:0;font-size:9px}th,td{padding:6px}details{display:block}header{max-width:none}a{color:inherit;text-decoration:none}}
</style></head><body><main><header><h1>Bible Companion: summary approval table</h1>
<p>Reviewed all 1,189 English chapter summaries across the Old and New Testaments. This table contains the chapters flagged for wording or essential context; inclusion does not mean every original summary was factually wrong.</p>
<div class="notice"><strong>All 129 rows approved.</strong> IMPLEMENTATION_STATUS The Verse of the Day quote fix remains included.</div>
<p>Keep the summary close to what the passage says. Put inferred motives, disputed identities, textual variants, and interpretive conclusions in labeled notes. The references below are the passages that help explain each issue.</p>
<p><a href="approval_table.csv">Download CSV</a> | <a href="approval_table.json">Structured review data</a>AUDIT_LINK</p></header>
<div class="controls"><label>Find a passage or issue <input id="search" type="search" placeholder="Judges 11, vow, resurrection..."></label><span id="count"></span></div>
<div class="scroll"><table><thead><tr><th>Row / passage</th><th>Wording concern</th><th>Approved summary</th><th>Essential cross-references</th><th>Translation / context note</th></tr></thead><tbody>''' + '\n'.join(body) + '''</tbody></table></div>
<footer>All rows were approved for implementation across 13 languages. Reference descriptions supply passage-specific context; translation notes identify interpretive uncertainty and edition differences. This review does not claim that unflagged passages admit only one interpretation.</footer>
<script>const rows=[...document.querySelectorAll('tbody tr')],input=document.querySelector('#search'),count=document.querySelector('#count');function filter(){const q=input.value.toLowerCase().trim();let n=0;for(const row of rows){row.hidden=!row.textContent.toLowerCase().includes(q);if(!row.hidden)n++;}count.textContent=n+' of '+rows.length+' proposals';}input.addEventListener('input',filter);filter();</script>
</main></body></html>'''
document = document.replace('IMPLEMENTATION_STATUS', e(metadata['implementation_status']))
document = document.replace('AUDIT_LINK', ' | <a href="translation_audit.json">Translation audit</a>' if metadata.get('audit_report') else '')
assert document.isascii()
(DEST / 'approval_table.html').write_text(document, encoding='ascii')
print(json.dumps(metadata, indent=2))
