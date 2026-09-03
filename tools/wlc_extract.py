import re, json, unicodedata
import xml.etree.ElementTree as ET

NS = {'o': 'http://www.bibletechnologies.net/2003/OSIS/namespace'}
CANTILLATION = re.compile('[֑-ֽ֯]')   # te'amim + meteg, keep nikkud

def verse_text(verse):
    out = []
    for child in verse:
        tag = child.tag.split('}')[-1]
        if tag == 'w':
            word = (child.text or '') + ''.join(
                (g.text or '') + (g.tail or '') for g in child)
            word = word.replace('/', '')
            if out and not out[-1].endswith('־'):
                out.append(' ')
            out.append(word)
        elif tag == 'seg':
            kind = child.get('type', '')
            if kind in ('x-maqqef', 'x-sof-pasuq'):
                out.append(child.text or '')
        # <note> and paragraph markers (x-pe / x-samekh) are dropped
    return ''.join(out).strip()

tree = ET.parse('Ps.xml')
chapters = {}
for verse in tree.iter('{%s}verse' % NS['o']):
    osis = verse.get('osisID')
    if not osis: continue
    _, ch, _ = osis.split('.')
    chapters.setdefault(int(ch), []).append(verse_text(verse))

def strip_marks(t):
    return ''.join(c for c in t if not unicodedata.combining(c))

rows = []
for ch in sorted(chapters):
    text = CANTILLATION.sub('', ' '.join(chapters[ch]))
    plain = strip_marks(text)
    letters = sum(1 for c in plain if 'א' <= c <= 'ת')
    rows.append((ch, len(text), len(plain), letters, text))

print("chapters:", len(rows))
print("\nshortest 14 by plain length (marks removed):")
for ch, full, plain, letters, _ in sorted(rows, key=lambda r: r[2])[:14]:
    print(f"  Ps {ch:3d}   plain {plain:4d}   letters {letters:4d}   with nikkud {full:4d}")

for limit in (100, 120, 150, 200, 250):
    n_plain = sum(1 for r in rows if r[2] < limit)
    n_let = sum(1 for r in rows if r[3] < limit)
    print(f"limit {limit}: {n_plain} chapters by plain length, {n_let} by letters only")

json.dump([{"c": ch, "text": t} for ch, _, _, _, t in rows],
          open('psalms_full.json', 'w', encoding='utf-8'), ensure_ascii=False)
