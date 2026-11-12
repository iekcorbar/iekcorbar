"""Merges all our own resource packs into ONE reproducible zip: iekcorbar-packv2.zip.

Why: ResourcePackManager mixes every zip in plugins/ResourcePackManager/mixer/ into one pack and
re-sends it to players whenever the mix changes. Six small zips of ours meant six inputs that could
each trigger a re-mix, and every build wrote new timestamps, so the bytes (and hash) changed even
when nothing had. This script writes one zip with fixed timestamps and sorted entries: the same
content always gives the same bytes. Since 2026-10-26 this single file is named
iekcorbar-packv2.zip - ONE zip in the mixer for everything of OURS; it replaced the old
Iekcorbar-pack.zip because garden/brewery models on the server had drifted out of sync (stale
per-plugin copies in the mixer fighting the merged copy).

Inputs - our own packs (built by each plugin's tools/make_pack.py first):
  Steamworks/resourcepack/Steamworks-blocks.zip, Kitchen/resourcepack/Kitchen-pack.zip,
  Sakagura/resourcepack/Sakagura-pack.zip, Relics/resourcepack/Relics-pack.zip,
  Kura/resourcepack/Kura-pack.zip, Wildlife/resourcepack/Wildlife-sounds.zip (any that exist)
The packs use separate namespaces; the only shared paths are pack.mcmeta (identical) and, if two
packs ever ship the same file with different content, this script stops with an error.

Third-party plugin packs (EliteMobs, FMM, TechCore, ValhallaMMO, BackpackPlus, RPM) deliberately
stay OUT - see THIRD_SOURCES at the bottom for the failed 68 MB experiment of 2026-10-26.

Usage (repo root): python -I scripts/merge-packs.py   ->  iekcorbar-packv2.zip (~15 MB)
Put this file in plugins/ResourcePackManager/mixer/ (and remove any old per-plugin copies of OUR
packs from there - the third-party zips keep living beside this one).
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
    "Hihou/resourcepack/Hihou-pack.zip",
    "SteamworksAdditions/resourcepack/Additions-pack.zip",
    "IekcorbarSiteSync/resourcepack/Mmo-pack.zip",
    "scripts/core-pack/Core-pack.zip",
]
OUT_DIR = "deploy"
BUILD = 56  # bump this EVERY rebuild: the pack name, the in-game description stamp and the
           # old-file cleanup all follow it, so there is never a nameless pile of twins.
OUT = os.path.join(OUT_DIR, "iekcorbar-pack-build%d.zip" % BUILD)
FIXED = (2026, 1, 1, 0, 0, 0)

# Server-mixer copies of the plugin-owned packs (EliteMobs, FMM, TechCore, ValhallaMMO,
# BackpackPlus, RPM). TRIED AND REVERTED 2026-10-26: baking them into v2 made one 68 MB file
# and clients answered "1 out of 2 resource packs failed to download". They keep their own
# zips in the mixer (RPM manages + re-mixes those itself, they auto-update on plugin export);
# v2 stays our-own ~15 MB and loads fine. Re-fill this list (from E:\backups server\rspm
# mixer) only if you really want a single file and know the client can take it.
THIRD_DIR = r"E:\backups server\rspm mixer"
THIRD_SOURCES = [
    # "BackpackPlus_resource_pack.zip",
    # "EliteMobs_resource_pack.zip",
    # "FreeMinecraftModels_resource_pack.zip",
    # "FreeMinecraftModels_shared_resource_pack.zip",
    # "ResourcePackManager_resource_pack.zip",
    # "TechCore_resource_pack.zip",
    # "ValhallaMMO_default.zip",
]

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
                            nv = round(t["to"][ax] + 0.03, 3)
                            if ax == 1 and nv > 32.0:
                                continue  # MC 26.2 refuses elements above y=32; leave the face as-is
                            t["to"][ax] = nv
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
dropped = []
merged = []


def json_union(name, a, b):
    """Merge two colliding JSONs the way the game's own pack stack would want: plugins that all
    ship their own atlases/blocks.json, font/default.json or items/<vanilla>.json each add list
    entries (atlas sources, font providers, custom-model-data ranges) - union them instead of
    letting one zip silently kill the others' textures. Returns merged bytes, or None when the
    files are structurally different (then the first one simply wins, like RPM did before)."""
    try:
        ja, jb = json.loads(a), json.loads(b)
    except ValueError:
        return None
    if not isinstance(ja, dict) or not isinstance(jb, dict):
        return None
    key = lambda s: json.dumps(s, sort_keys=True)

    def list_union(field):
        if isinstance(ja.get(field), list) and isinstance(jb.get(field), list):
            have = {key(s) for s in ja[field]}
            add = [s for s in jb[field] if key(s) not in have]
            if add:
                ja[field] = ja[field] + add
            return True
        return False

    if "/atlases/" in name and list_union("sources"):
        return json.dumps(ja, indent=1).encode("utf-8")
    if "/font/" in name and list_union("providers"):
        return json.dumps(ja, indent=1).encode("utf-8")
    if name.endswith("sounds.json") and all(isinstance(v, dict) for v in ja.values()) and all(isinstance(v, dict) for v in jb.values()):
        for k, v in jb.items():
            ja.setdefault(k, v)
        return json.dumps(ja, indent=1).encode("utf-8")
    ma, mb = ja.get("model"), jb.get("model")
    if "/items/" in name and isinstance(ma, dict) and isinstance(mb, dict) \
            and ma.get("type") == "range_dispatch" == mb.get("type") \
            and isinstance(ma.get("entries"), list) and isinstance(mb.get("entries"), list):
        have = {key(s) for s in ma["entries"]}
        add = [s for s in mb["entries"] if key(s) not in have]
        # range_dispatch wants its thresholds in ascending order
        ma["entries"] = sorted(ma["entries"] + add, key=lambda s: s.get("threshold", 0))
        return json.dumps(ja, indent=1).encode("utf-8")
    return None


def merge(path, ours):
    """One source zip into files. Ours (SOURCES) are read first and NEVER lose: same path from a
    plugin-owned zip with different bytes is dropped and listed at the end. Plugin zips colliding
    with each other on atlas/font/sounds/items JSON get their list entries UNIONED (json_union);
    anything else stays first-wins, like RPM's own overlay order on the server did before. Two of
    our OWN packs disagreeing stays a hard error - that is ours bug, not the mixer's."""
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if name.endswith("/"):
                continue
            data = z.read(name)
            if name not in files:
                files[name] = data
                owner[name] = "ours:" + path if ours else path
                continue
            if files[name] == data or name == "pack.mcmeta":
                continue  # identical bytes, or the mcmeta we overwrite ourselves at the end
            if owner[name].startswith("ours:"):
                if ours:
                    sys.exit(f"CONFLICT: {name} differs between our own packs {owner[name][5:]} and {path}")
                dropped.append((name, path))  # plugin copy loses against ours
                continue
            if not ours:
                if name.endswith(".json"):
                    m = json_union(name, files[name], data)
                    if m is not None:
                        files[name] = m
                        dropped_note = f"union-merged {name} <- {os.path.basename(path)}"
                        merged.append(dropped_note)
                        continue
                dropped.append((name, path))  # plugin vs plugin: earlier one wins


for src in SOURCES:
    if not os.path.exists(src):
        print("skipping (not built):", src)
        continue
    found.append(src)
    merge(src, True)

for src in THIRD_SOURCES:
    p = os.path.join(THIRD_DIR, src)
    if not os.path.exists(p):
        print("skipping (not in mixer backup):", src)
        continue
    if zipfile.ZipFile(p).namelist() == []:
        print("skipping (empty zip):", src)
        continue
    found.append(p)
    merge(p, False)

for note in merged:
    print(" ", note)
for name, loser in dropped[:20]:
    print(f"  dropped from {os.path.basename(loser)}: {name}")
if len(dropped) > 20:
    print(f"  ...and {len(dropped) - 20} more identical-path plugin copies (ours or the first zip won)")

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

# MC 26.2 rejects model elements outside y=-16..32 ("'to' specifier exceeds the allowed
# boundaries"), which turned every taller-than-2-blocks prop into a purple cube. Shrink the
# Y of oversized models so they fit (textures here are solid palette cells, so vertical UV
# squash is invisible); the matching DecorDef/RelicType scale compensates 1/k server-side,
# so the world size stays exactly the same. Models that share one Java scale get one forced
# k so a single scale value fixes both.
FIT_LIMIT = 32.0
FIT_GROUPS = {
    "assets/relics/models/item/countdown_board.json": 32.0 / 86.4,
    "assets/relics/models/item/countdown_board_wall.json": 32.0 / 86.4,
}


def fit_scale_y(o, k):
    if isinstance(o, dict):
        for key, v in o.items():
            if key in ("from", "to", "origin") and isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v):
                o[key] = [v[0], round(v[1] * k, 3), v[2]]
            else:
                fit_scale_y(v, k)
    elif isinstance(o, list):
        for v in o:
            fit_scale_y(v, k)


fitted = []
for name in list(files):
    if "/models/" not in name or not name.endswith(".json"):
        continue
    try:
        m = json.loads(files[name])
    except ValueError:
        continue
    els = m.get("elements")
    if not isinstance(els, list) or not els:
        continue
    ys = [v for e in els for w in (e.get("from"), e.get("to")) for v in ([w[1]] if isinstance(w, list) and len(w) == 3 else [])]
    if not ys:
        continue
    hi, lo = max(ys), min(ys)
    k = FIT_GROUPS.get(name)
    if k is None:
        k = 1.0
        if hi > FIT_LIMIT + 0.001:
            k = min(k, FIT_LIMIT / hi)
        if lo < -16.0 - 0.001:
            k = min(k, 16.0 / abs(lo))
    if k >= 0.999:
        continue
    fit_scale_y({"elements": els}, k)
    files[name] = json.dumps(m, indent=1).encode("utf-8")
    fitted.append((name, round(k, 4)))
for name, k in fitted:
    print("FIT %s -> y*%s (java scale x%.4f)" % (name, k, 1.0 / k))

# The FIT pass only watches Y (that is what needs the java scale compensation), but 26.2
# clamps EVERY axis to -16..32: the wide countdown boards ended up 0.06 units out on X
# after scaling (-16.06). Clamp any remaining out-of-range coordinate to the boundary -
# a sub-0.1-unit shave nobody sees, and it keeps java scales untouched.
clamped = 0
for name in list(files):
    if "/models/" not in name or not name.endswith(".json"):
        continue
    try:
        m = json.loads(files[name])
    except ValueError:
        continue
    els = m.get("elements")
    if not isinstance(els, list) or not els:
        continue
    touched = False
    for e in els:
        for key in ("from", "to"):
            v = e.get(key)
            if isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v):
                nv = [max(-16.0, min(32.0, float(x))) for x in v]
                if nv != [float(x) for x in v]:
                    e[key] = nv
                    touched = True
    if touched:
        files[name] = json.dumps(m, indent=1).encode("utf-8")
        clamped += 1
print("boundary clamp (-16..32) applied to", clamped, "models")

mcmeta = {"pack": {"description": "Iekcorbar v2 (Steamworks + Additions, Kitchen+decor, Sakagura+garden, Relics, Kura, Wildlife+Zoo, Zukan, Satchel, Hihou, MMO items) - build %d" % BUILD, "pack_format": 64,
                   "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}}
files["pack.mcmeta"] = json.dumps(mcmeta, indent=1).encode("utf-8")
icon = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack-icon.png")  # scripts/make-pack-icon.py
if os.path.exists(icon):
    files["pack.png"] = open(icon, "rb").read()

os.makedirs(OUT_DIR, exist_ok=True)
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for name in sorted(files):
        info = zipfile.ZipInfo(name, FIXED)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        z.writestr(info, files[name])

# housekeeping (user rule 26-10): one output folder, one name per build - sweep away every
# older pack, under either naming scheme, so no stale twin can ever be uploaded again.
# Site zips (iekcorbar_iekje_barend.zip and friends) are NOT touched by this sweep.
for stale in (glob.glob("iekcorbar-packv*.zip") + glob.glob(os.path.join(OUT_DIR, "iekcorbar-packv*.zip"))
              + glob.glob(os.path.join(OUT_DIR, "iekcorbar-pack-build*.zip"))):
    if os.path.abspath(stale) != os.path.abspath(OUT):
        os.remove(stale)
        print("removed stale:", stale)

digest = hashlib.sha1(open(OUT, "rb").read()).hexdigest()
print(f"{OUT}: {len(files)} files from {len(found)} packs, {os.path.getsize(OUT) / 1e6:.2f} MB, sha1 {digest}")
