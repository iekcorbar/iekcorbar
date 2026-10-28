"""Vergelijk de toetsen van de vier woordenboeken in i18n.jsx.
Ontbrekende toetsen in en/it/fi tonen (t() valt terug op nl, dus het is geen
crash - maar wel onvertaalde tekst die de Fin/Enzel/Italiaan ziet)."""
import io, re

s = io.open(r"StaffHub/src/lib/i18n.jsx", encoding="utf-8").read()
blokken = {}
for naam in ["nl", "en", "it", "fi"]:
    m = re.search(r"const " + naam + r" = \{.*?\n\};", s, re.S)
    blokken[naam] = re.findall(r"^  '([^']+)':", m.group(0), re.M) if m else []
    blokken[naam] = set(blokken[naam])

print("nl:", len(blokken["nl"]), "toetsen")
for a in ["en", "it", "fi"]:
    missing = sorted(blokken["nl"] - blokken[a])
    extra = sorted(blokken[a] - blokken["nl"])
    print(f"{a}: {len(blokken[a])} | ontbreekt t.o.v. nl: {missing or 'geen'} | vreemde: {extra or 'geen'}")
