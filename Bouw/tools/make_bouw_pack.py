"""Bouw resource pack (namespace bouw): building pieces converted from the MIT-licensed Macaw's Doors
and Macaw's Fences mods (c) Sketch Macaw / Peachy Macaw - see LICENSE-MACAWS.txt, which ships in the pack.
Only the MIT mods are used; the extracted jars live in archief/macaws-mit/{doors,fences} (gitignored).
Models are flattened (parents resolved), textures copied and renamed into the bouw namespace, fences get
the 16 connection masks the prop engine wants, doors get "<id>" (closed, 2 blocks tall) and "<id>_open".

Usage (from Bouw/): python -I tools/make_bouw_pack.py resourcepack  [--src ../archief/macaws-mit]
  -> resourcepack/Bouw-pack.zip, src/main/resources/families.json (read by the plugin)
"""
import json, os, sys, shutil, zipfile, copy

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "..", "archief", "macaws-mit")
NS = "bouw"
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove", "bamboo", "crimson", "warped"]
# (family id, kind, source mod, pattern with {w}, display name)
DOORS = [
    ("japanese_door", "japanese/{w}_japanese_door", "Japanese Door"),
    ("japanese2_door", "japanese/{w}_japanese2_door", "Japanese Door II"),
]
FENCES = [
    ("picket_fence", "{w}_picket_fence", "Picket Fence"),
    ("stockade_fence", "{w}_stockade_fence", "Stockade Fence"),
]

used_tex = {}      # source mod -> set of texture paths (without namespace/ext)
missing = []


class Mod:
    def __init__(self, folder, ns):
        self.ns, self.root = ns, os.path.join(SRC, folder, "assets", ns)

    def model(self, ref):
        """ref like mcwdoors:block/foo or block/foo -> parsed json or None"""
        path = ref.split(":", 1)[-1]
        f = os.path.join(self.root, "models", path + ".json")
        return json.load(open(f, encoding="utf-8")) if os.path.exists(f) else None

    def state(self, name):
        f = os.path.join(self.root, "blockstates", name + ".json")
        return json.load(open(f, encoding="utf-8")) if os.path.exists(f) else None


def flatten(mod, ref):
    """Resolve parents -> (elements, textures). Returns None if a parent is outside the mod (vanilla)."""
    m = mod.model(ref)
    if m is None:
        return None
    elems, tex = m.get("elements"), dict(m.get("textures", {}))
    if "parent" in m and elems is None or "parent" in m:
        p = flatten(mod, m["parent"]) if not m["parent"].startswith("minecraft:") and not m["parent"].startswith("block/block") else None
        if p is not None:
            pe, pt = p
            if elems is None:
                elems = pe
            merged = dict(pt)
            merged.update(tex)
            tex = merged
    return (copy.deepcopy(elems) if elems else None), tex


def resolve_tex(tex):
    """Follow #refs between texture keys."""
    out = {}
    for k, v in tex.items():
        seen = 0
        while v.startswith("#") and seen < 8:
            v = tex.get(v[1:], v)
            seen += 1
        out[k] = v
    return out


def retarget(mod, tex):
    """mcwdoors:block/x -> bouw:block/x ; remember files to copy."""
    out = {}
    for k, v in tex.items():
        if v.startswith("#"):
            continue
        if ":" in v and not v.startswith("minecraft:"):
            ns, p = v.split(":", 1)
            used_tex.setdefault(ns, set()).add(p)
            out[k] = f"{NS}:{p}"
        elif ":" not in v:
            used_tex.setdefault(mod.ns, set()).add(v)
            out[k] = f"{NS}:{v}"
        else:
            out[k] = v
    return out


FACE_TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def turn_y(elems, quarter):
    """Rotate elements about the block centre by quarter*90 degrees clockwise seen from above (vanilla blockstate y)."""
    elems = copy.deepcopy(elems)
    for _ in range(quarter % 4):
        for e in elems:
            (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
            nx1, nx2 = 16 - z2, 16 - z1
            nz1, nz2 = x1, x2
            e["from"], e["to"] = [nx1, y1, nz1], [nx2, y2, nz2]
            e["faces"] = {FACE_TURN[f]: v for f, v in e["faces"].items()}
            r = e.get("rotation")
            if r:
                ox, oy, oz = r["origin"]
                r["origin"] = [16 - oz, oy, ox]
                if r["axis"] == "x":
                    r["axis"] = "z"
                elif r["axis"] == "z":
                    r["axis"] = "x"
                    r["angle"] = -r["angle"]
    return elems


def lift(elems, dy):
    elems = copy.deepcopy(elems)
    for e in elems:
        e["from"][1] += dy
        e["to"][1] += dy
        r = e.get("rotation")
        if r:
            r["origin"][1] += dy
    return elems


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.3, 0.3, 0.3]},
    "head": {"scale": [0.4, 0.4, 0.4]},
}


def check_bounds(name, elems):
    for e in elems:
        for a in e["from"] + e["to"]:
            if a < -16 or a > 32:
                raise SystemExit(f"{name}: coordinate {a} outside -16..32")


class Out:
    def __init__(self, out):
        self.root = os.path.join(out, "Bouw-pack")
        shutil.rmtree(self.root, ignore_errors=True)
        self.zip = os.path.join(out, "Bouw-pack.zip")
        self.count = 0

    def write(self, rel, data):
        p = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        if isinstance(data, bytes):
            open(p, "wb").write(data)
        else:
            open(p, "w", encoding="utf-8").write(json.dumps(data, indent=1))

    def model(self, name, elems, tex, gui_scale=None):
        check_bounds(name, elems)
        m = {"textures": tex, "elements": elems, "display": copy.deepcopy(DISPLAY)}
        if gui_scale:
            for k in ("gui", "fixed", "head"):
                m["display"][k]["scale"] = [gui_scale] * 3
        self.write(f"assets/{NS}/models/item/{name}.json", m)
        self.write(f"assets/{NS}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        self.count += 1


def build_door(out, mod, fam, pattern, woods):
    items = []
    for w in woods:
        base = pattern.format(w=w)
        d, b = os.path.dirname(base), os.path.basename(base)
        parts = {}
        for key in ("closed", "top_closed", "bottom_left", "top_left"):
            r = flatten(mod, f"mcwdoors:block/{d}/{b}_{key}")
            if r is None or r[0] is None:
                parts = None
                break
            parts[key] = (r[0], resolve_tex(r[1]))
        if not parts:
            missing.append(base)
            continue

        def join(low, up):
            tex = {}
            elems = []
            for tag, (e, t), dy in (("a", low, 0), ("b", up, 16)):
                for el in copy.deepcopy(e):
                    for f in el["faces"].values():
                        if f["texture"].startswith("#"):
                            f["texture"] = "#" + tag + f["texture"][1:]
                    elems.append(el)
                for k, v in retarget(mod, t).items():
                    tex[tag + k] = v
                # lift upper elements
            n = len(low[0])
            elems = elems[:n] + lift(elems[n:], 16)
            elems = turn_y(elems, 1)    # source panel lies in the YZ plane; the engine's facing 0/180 needs XY
            tex["particle"] = tex.get("aparticle", next(iter(tex.values())))
            return elems, tex

        e, t = join(parts["closed"], parts["top_closed"])
        name = f"{w}_{fam}"
        out.model(name, e, t, 0.28)
        e, t = join(parts["bottom_left"], parts["top_left"])
        out.model(name + "_open", e, t, 0.28)
        items.append({"id": name, "wood": w})
    return items


def build_fence(out, mod, fam, pattern, woods):
    items = []
    for w in woods:
        st = mod.state(pattern.format(w=w))
        if not st or "multipart" not in st:
            missing.append(pattern.format(w=w))
            continue
        post, side = None, None
        for part in st["multipart"]:
            ap = part["apply"]
            ap = ap[0] if isinstance(ap, list) else ap
            if "when" not in part:
                post = ap["model"]
            elif part["when"].get("north") == "true":
                side = ap["model"]
        if not post or not side:
            missing.append(pattern.format(w=w) + ' (no post/side)')
            continue
        rp, rs = flatten(mod, post), flatten(mod, side)
        if not rp or not rs or not rp[0] or not rs[0]:
            missing.append(pattern.format(w=w))
            continue
        tex = {}
        pe, ps = rp[0], resolve_tex(rp[1])
        se, ss = rs[0], resolve_tex(rs[1])
        for tag, es, ts in (("p", pe, ps), ("s", se, ss)):
            for el in es:
                for f in el["faces"].values():
                    if f["texture"].startswith("#"):
                        f["texture"] = "#" + tag + f["texture"][1:]
            for k, v in retarget(mod, ts).items():
                tex[tag + k] = v
        tex["particle"] = next(iter(tex.values()))
        name = f"{w}_{fam}"
        for mask in range(16):
            es = copy.deepcopy(pe)
            for d in range(4):
                if mask & (1 << d):
                    es += turn_y(se, d)
            out.model(f"{name}_{mask}", es, tex)
        items.append({"id": name, "wood": w})
    return items


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "resourcepack"
    out = Out(outdir)
    doors, fences = Mod("doors", "mcwdoors"), Mod("fences", "mcwfences")
    families = []
    for fam, pattern, label in DOORS:
        families.append({"family": fam, "kind": "door", "label": label, "items": build_door(out, doors, fam, pattern, WOODS)})
    for fam, pattern, label in FENCES:
        families.append({"family": fam, "kind": "fence", "label": label, "items": build_fence(out, fences, fam, pattern, WOODS)})
    folders = {"mcwdoors": doors, "mcwfences": fences}
    for ns, paths in used_tex.items():
        for p in sorted(paths):
            f = os.path.join(folders[ns].root, "textures", p.split(":", 1)[-1] + ".png") if ns in folders else None
            if f and os.path.exists(f):
                out.write(f"assets/{NS}/textures/{p}.png", open(f, "rb").read())
            else:
                missing.append("texture " + ns + ":" + p)
    out.write("pack.mcmeta", {"pack": {"description": "Bouw (Macaw's Doors/Fences, MIT)", "pack_format": 64,
                                       "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}})
    out.write("LICENSE-MACAWS.txt", (
        "Models and textures in assets/bouw/ derived from Macaw's Doors and Macaw's Fences,\n"
        "Copyright (c) Sketch Macaw & Peachy Macaw, distributed under the MIT License\n"
        "(license declared in the mods' neoforge.mods.toml). Converted to item models for the Bouw plugin.\n\n"
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated\n"
        "documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation\n"
        "the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, subject\n"
        "to the above copyright notice and this permission notice being included in all copies or substantial portions.\n"
        "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.\n").encode())
    if os.path.exists(out.zip):
        os.remove(out.zip)
    with zipfile.ZipFile(out.zip, "w", zipfile.ZIP_DEFLATED) as z:
        for base, _, files in os.walk(out.root):
            for f in sorted(files):
                full = os.path.join(base, f)
                z.write(full, os.path.relpath(full, out.root).replace("\\", "/"))
    res = os.path.join(HERE, "..", "src", "main", "resources")
    os.makedirs(res, exist_ok=True)
    json.dump(families, open(os.path.join(res, "families.json"), "w", encoding="utf-8"), indent=1)
    print("models", out.count, "items", sum(len(f["items"]) for f in families), "missing", len(missing))
    for m in missing[:20]:
        print("  missing", m)
    print("size", os.path.getsize(out.zip) // 1024, "KB")


if __name__ == "__main__":
    main()
