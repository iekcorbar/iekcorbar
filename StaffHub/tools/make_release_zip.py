#!/usr/bin/env python3
"""Bouwt de leverings-zip voor StaffHub: deploy/staffhub-<version>.zip.

De zip bevat ALLEEN bron (geen node_modules/.next/data/desktop-dist): op de VPS
gebeurt de build zelf in docker. Elk bestand krijgt de map `staffhub/` als
voorvoegsel, zodat uitpakken in /opt direct /opt/staffhub oplevert.

    python -I StaffHub/tools/make_release_zip.py

Het versienummer komt uit StaffHub/package.json en staat dus in de bestandsnaam
('niet twee bestanden met dezelfde naam maar verschillende inhoud').
"""
import json
import os
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.dirname(HERE)                                  # StaffHub/
ROOT = os.path.dirname(SRC)                                  # repo-root
DEPLOY = os.path.join(ROOT, "deploy")

# Nooit meegeven: gegenereerd, lokaal of geheim
EXCLUDE_DIRS = {"node_modules", ".next", ".git", "data", "dist", ".turbo", "__pycache__"}
EXCLUDE_FILES = {".env", "staffhub.db", "staffhub.db-wal", "staffhub.db-shm"}


def collect():
    out = []
    for dirpath, dirnames, filenames in os.walk(SRC):
        dirnames[:] = sorted(d for d in dirnames if d not in EXCLUDE_DIRS)
        for name in sorted(filenames):
            if name in EXCLUDE_FILES or name.endswith((".pyc", ".log")):
                continue
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, SRC).replace(os.sep, "/")
            out.append((full, "staffhub/" + rel))
    return sorted(out, key=lambda p: p[1])


def main():
    version = json.load(open(os.path.join(SRC, "package.json"), encoding="utf-8"))["version"]
    os.makedirs(DEPLOY, exist_ok=True)
    target = os.path.join(DEPLOY, f"staffhub-{version}.zip")
    files = collect()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        for full, arc in files:
            z.write(full, arc)
    size = os.path.getsize(target)
    print(f"{target}: {len(files)} bestanden, {size/1024:.0f} KB")


if __name__ == "__main__":
    main()
