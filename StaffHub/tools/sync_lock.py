"""Packaging-hulp: lock bestand controleren en root-versie syncen met package.json.

Waarom: npm 11.19 (in de Docker-image) was strenger dan lokaal npm 11.6 - een
oude root-versie in de lock plus een ontbrekende floating-ui-entry kostten een
gehele release-build. Dit script controleert beide en zet de versie-velden op
dezelfde waarde als package.json.
"""
import io
import json
import os
import re

# altijd relatief aan dit script, nooit aan de werkmap: vanuit de repo-root
# raakte dit anders het lock-bestand VAN DE SITE (0.1.0) in plaats van die van
# StaffHub.
HIER = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lockpad = os.path.join(HIER, "package-lock.json")
pkgpad = os.path.join(HIER, "package.json")

lock = json.load(io.open(lockpad, encoding="utf-8"))
print("root version in lock:", lock["packages"][""].get("version"))
hits = [k for k in lock["packages"] if "floating-ui/dom" in k]
print("floating-ui entries:", [(h, lock["packages"][h]["version"]) for h in hits])

versie = json.load(io.open(pkgpad, encoding="utf-8"))["version"]
s = io.open(lockpad, encoding="utf-8").read()
# alleen de twee root-regels (top-level + packages[""]): beide staan direct na
# "name": "staffhub". Echte package-versies van 0.4.x laten we met rust.
pat = re.compile(r'("name": "staffhub",\s*"version": ")[^"]+(")')
s2, n = pat.subn(r"\g<1>" + versie + r"\g<2>", s)
assert n == 2, f"verwacht 2 root-versie-velden, vond {n}"
io.open(lockpad, "w", encoding="utf-8", newline="\n").write(s2)
print("versie-velden in lock gezet op", versie, "aantal:", n)
