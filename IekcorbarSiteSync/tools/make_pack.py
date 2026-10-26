"""Zips IekcorbarSiteSync/resourcepack/mmo/ into Mmo-pack.zip for scripts/merge-packs.py.

Why it lives here: the MMOItems item models (assets/mmoitems/** — items/, models/,
textures/) existed for years only as a loose mixer zip (mmopackNew_rework.zip) in
plugins/ResourcePackManager/mixer/ on the server, with no copy in git. Extracted
from the newest backup (729 models / 729 item definitions / 766 textures) on
2026-10-26 so the repo is the source of truth; new or fixed models get added under
resourcepack/mmo/assets/mmoitems/ and ship through Iekcorbar-pack.zip like every
other plugin pack. The zip has no pack.mcmeta/pack.png on purpose — merge-packs.py
writes the single shared one.

Usage (repo root): python -I IekcorbarSiteSync/tools/make_pack.py
Then: python -I scripts/merge-packs.py   -> Iekcorbar-pack.zip
"""
import os, zipfile

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # .../IekcorbarSiteSync
SRC = os.path.join(BASE, "resourcepack", "mmo")
OUT = os.path.join(BASE, "resourcepack", "Mmo-pack.zip")
FIXED = (2026, 10, 26, 0, 0, 0)  # fixed timestamp: same content -> same bytes, like merge-packs.py

def main():
    files = []
    for root, _dirs, names in os.walk(SRC):
        for f in names:
            fp = os.path.join(root, f)
            files.append((os.path.relpath(fp, SRC).replace(os.sep, "/"), fp))
    files.sort()
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for arc, fp in files:
            info = zipfile.ZipInfo(arc, FIXED)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16  # readable on Linux (Plesk/unzip)
            z.writestr(info, open(fp, "rb").read())
    print(f"Wrote {OUT} ({len(files)} entries)")

if __name__ == "__main__":
    main()
