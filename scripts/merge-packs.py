"""Merges all our own resource packs into ONE reproducible zip: Iekcorbar-pack.zip.

Why: ResourcePackManager mixes every zip in plugins/ResourcePackManager/mixer/ into one pack and
re-sends it to players whenever the mix changes. Six small zips of ours meant six inputs that could
each trigger a re-mix, and every build wrote new timestamps, so the bytes (and hash) changed even
when nothing had. This script writes one zip with fixed timestamps and sorted entries: the same
content always gives the same bytes.

Inputs (built by each plugin's tools/make_pack.py first):
  Steamworks/resourcepack/Steamworks-blocks.zip, Kitchen/resourcepack/Kitchen-pack.zip,
  Sakagura/resourcepack/Sakagura-pack.zip, Relics/resourcepack/Relics-pack.zip,
  Kura/resourcepack/Kura-pack.zip, Wildlife/resourcepack/Wildlife-sounds.zip (any that exist)
The packs use separate namespaces; the only shared paths are pack.mcmeta (identical) and, if two
packs ever ship the same file with different content, this script stops with an error.

Usage (repo root): python -I scripts/merge-packs.py   ->  Iekcorbar-pack.zip
Then replace those zips in plugins/ResourcePackManager/mixer/ by this single file.
"""
import glob, hashlib, json, os, sys, zipfile

SOURCES = [
    "Steamworks/resourcepack/Steamworks-blocks.zip",
    "Kitchen/resourcepack/Kitchen-pack.zip",
    "Sakagura/resourcepack/Sakagura-pack.zip",
    "Relics/resourcepack/Relics-pack.zip",
    "Kura/resourcepack/Kura-pack.zip",
    "Wildlife/resourcepack/Wildlife-sounds.zip",
    "Zukan/resourcepack/Zukan-pack.zip",
    "Wildlife/resourcepack/Zoo-pack.zip",
    "Sakagura/resourcepack/Garden-pack.zip",
    "Kitchen/resourcepack/Kitchendecor-pack.zip",
    "Satchel/resourcepack/Satchel-pack.zip",
]
OUT = "Iekcorbar-pack.zip"
FIXED = (2026, 1, 1, 0, 0, 0)

FACES = {"north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1)}


def fix_zfight(model):
    """Two elements with a face on the same plane in the same direction flicker (z-fighting). Push the one with
    the smaller face (the detail) 0.03 px outwards on that axis; repeated until no pair is left. Returns the
    number of changes."""
    els = model.get("elements") or []
    changed = 0
    for _ in range(6):
        again = False
        for face, (ax, hi) in FACES.items():
            groups = {}
            for i, e in enumerate(els):
                if face in e.get("faces", {}) and "rotation" not in e:
                    groups.setdefault(round(e["to"][ax] if hi else e["from"][ax], 3), []).append(i)
            for idx in groups.values():
                for a in range(len(idx)):
                    for b in range(a + 1, len(idx)):
                        A, B = els[idx[a]], els[idx[b]]
                        o = [k for k in range(3) if k != ax]
                        if not all(min(A["to"][k], B["to"][k]) - max(A["from"][k], B["from"][k]) > 0.01 for k in o):
                            continue
                        area = lambda e: (e["to"][o[0]] - e["from"][o[0]]) * (e["to"][o[1]] - e["from"][o[1]])
                        t = B if area(B) <= area(A) else A
                        if hi:
                            t["to"][ax] = round(t["to"][ax] + 0.03, 3)
                        else:
                            t["from"][ax] = round(t["from"][ax] - 0.03, 3)
                        changed += 1
                        again = True
        if not again:
            break
    return changed


files = {}
owner = {}
found = []
for src in SOURCES:
    if not os.path.exists(src):
        print("skipping (not built):", src)
        continue
    found.append(src)
    with zipfile.ZipFile(src) as z:
        for name in z.namelist():
            if name.endswith("/"):
                continue
            data = z.read(name)
            if name in files and files[name] != data:
                if name == "pack.mcmeta":
                    continue  # first pack's wins; all ours say the same
                sys.exit(f"CONFLICT: {name} differs between {owner[name]} and {src}")
            files.setdefault(name, data)
            owner.setdefault(name, src)

fixed = 0
for name in list(files):
    if "/models/item/" in name and name.endswith(".json"):
        try:
            m = json.loads(files[name])
        except ValueError:
            continue
        n = fix_zfight(m)
        if n:
            fixed += n
            files[name] = json.dumps(m, indent=1).encode("utf-8")
print("z-fighting faces separated:", fixed)

mcmeta = {"pack": {"description": "Iekcorbar (Steamworks, Kitchen, Sakagura, Relics, Kura, Wildlife, Zukan)", "pack_format": 64,
                   "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}}
files["pack.mcmeta"] = json.dumps(mcmeta, indent=1).encode("utf-8")
icon = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack-icon.png")  # scripts/make-pack-icon.py
if os.path.exists(icon):
    files["pack.png"] = open(icon, "rb").read()

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for name in sorted(files):
        info = zipfile.ZipInfo(name, FIXED)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        z.writestr(info, files[name])

digest = hashlib.sha1(open(OUT, "rb").read()).hexdigest()
print(f"{OUT}: {len(files)} files from {len(found)} packs, {os.path.getsize(OUT) / 1e6:.2f} MB, sha1 {digest}")
