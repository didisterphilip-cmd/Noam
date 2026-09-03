"""Turn the WLC Psalms into the asset the app ships with."""
import json, unicodedata

ONES = " אבגדהוזחט"
TENS = " יכלמנסעפצ"
HUNDREDS = " קרשת"

def hebrew_numeral(n):
    out = ""
    h, rest = divmod(n, 100)
    if h: out += HUNDREDS[h]
    if rest in (15, 16):                      # never spell the Name
        return out + ("טו" if rest == 15 else "טז")
    t, o = divmod(rest, 10)
    if t: out += TENS[t]
    if o: out += ONES[o]
    return out

def plain_len(text):
    return len(''.join(c for c in text if not unicodedata.combining(c)))

rows = json.load(open('psalms_full.json', encoding='utf-8'))
out = [{
    "c": r["c"],
    "ref": "תהלים " + hebrew_numeral(r["c"]),
    "len": plain_len(r["text"]),
    "text": r["text"],
} for r in rows]

json.dump(out, open('psalms_he.json', 'w', encoding='utf-8'),
          ensure_ascii=False, separators=(',', ':'))

print("chapters:", len(out))
for r in sorted(out, key=lambda r: r["len"])[:10]:
    print(f'  {r["ref"]:12s} len {r["len"]:3d}')
print("\nrefs sanity:", hebrew_numeral(15), hebrew_numeral(16), hebrew_numeral(117), hebrew_numeral(150))
