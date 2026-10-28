import io
p = r"StaffHub\package.json"
s = io.open(p, encoding="utf-8-sig").read()
import re
nieuw, n = re.subn(r'("version"\s*:\s*")[^"]+(")', r"\g<1>0.4.2\g<2>", s)
assert n == 1, f"{n} versieregels gevonden"
io.open(p, "w", encoding="utf-8", newline="\n").write(nieuw)
print("versie -> 0.4.2 (1 regel aangepast, rest van het bestand ongemoeid)")
