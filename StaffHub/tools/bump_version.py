"""Verhoog het versienummer in StaffHub/package.json.

Gebruik:  python -I StaffHub/tools/bump_version.py 0.4.3

Alleen het versieveld wordt aangepast (regex op de rauwe tekst), de rest van
het bestand blijft byte-voor-byte gelijk. PowerShell's Set-Content schreef hier
op een given moment een BOM die elke JSON-lezer (en de release-zip) brak - dit
script schrijft gegarandeerd BOM-vrij UTF-8.
"""
import io, os, re, sys

if len(sys.argv) < 2:
    sys.exit("gebruik: bump_version.py <x.y.z>")
nieuw_v = sys.argv[1]
assert re.fullmatch(r"\d+\.\d+\.\d+", nieuw_v), "versie moet x.y.z zijn"

p = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "package.json")
s = io.open(p, encoding="utf-8-sig").read()
s, n = re.subn(r'("version"\s*:\s*")[^"]+(")', rf"\g<1>{nieuw_v}\g<2>", s)
assert n == 1, f"{n} versieregels gevonden"
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("versie ->", nieuw_v)
