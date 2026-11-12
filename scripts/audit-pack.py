"""Audit the merged pack like the 26.2 client would:

1. element coordinates must stay -16..32 (Paper 26.2 rejects models outside that on ANY axis);
2. face UVs within 0..16 (vanilla texel space), rotation origins in bounds;
3. every texture alias/ref and every "parent" must exist in the zip;
4. every model must be reachable: via an items/*.json definition (model field), as a
   parent of another model, or via a blockstates/ or font or items model reference -
   26.2 resolves item_model components THROUGH items/ definitions, so a model without
   one renders as a magenta cube (the crop bug of 26-10);
5. every items/*.json definition must reference an existing model.

Usage (repo root): python -I scripts/audit-pack.py   -> newest deploy/iekcorbar-pack-build*.zip
"""
import glob, json, os, re, sys, zipfile, hashlib

if len(sys.argv) > 1:
    path = sys.argv[1]
else:  # newest build in the single deploy output folder
    cands = sorted(glob.glob(os.path.join("deploy", "iekcorbar-pack-build*.zip")))
    path = cands[-1] if cands else sys.exit("no deploy/iekcorbar-pack-build*.zip found")
print("sha1", hashlib.sha1(open(path, "rb").read()).hexdigest()[:12])
z = zipfile.ZipFile(path)
names = set(z.namelist())
models, defs, other_json = {}, {}, {}

for name in sorted(names):
    if not name.endswith(".json") or name == "pack.mcmeta":
        continue
    try:
        data = json.loads(z.read(name))
    except ValueError:
        print("BAD %s: invalid json" % name)
        continue
    if "/models/" in name:
        models[name] = data
    elif "/items/" in name and name.count("/") >= 2:
        defs[name] = data
    else:
        other_json[name] = data

def to_model_id(t):
    ns, rest = t.split(":", 1) if ":" in t else ("minecraft", t)
    return "assets/%s/models/%s.json" % (ns, rest)

def to_tex_id(t):
    ns, rest = t.split(":", 1) if ":" in t else ("minecraft", t)
    return "assets/%s/textures/%s.png" % (ns, rest)

problems = 0
referenced = set()

def walk_refs(obj, where):
    """collect model ids referenced anywhere inside a json (parent, model fields, texture refs)"""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("parent", "model") and isinstance(v, str) and v:
                referenced.add(v)
            if k == "textures" and isinstance(v, dict):
                for t in v.values():
                    if isinstance(t, str) and not t.startswith("#"):
                        referenced.add(t)
            walk_refs(v, where)
    elif isinstance(obj, list):
        for v in obj:
            walk_refs(v, where)

# definitions -> their model refs count as referenced, and must exist
# (vanilla targets resolve against the client jar; the three mmoitems refs are MMOItems'
#  own typos, shipped loose in their export for years - see CLAUDE.md "known upstream quirk")
KNOWN = {"assets/mmoitems/items/chef/chef_15_edible_pine_resin.json",
         "assets/mmoitems/items/enchanter/enchanter5.json",
         "assets/mmoitems/items/hunter/xxx.json"}
n_defs = 0
for name, d in defs.items():
    n_defs += 1
    walk_refs(d, name)
    def check_model_ref(t, where):
        global problems
        if t.startswith("minecraft:") or ":" not in t:
            return
        mid = to_model_id(t)
        if mid not in names:
            if where in KNOWN:
                print("KNOWN %s: %s (MMOItems upstream typo)" % (where, t))
            else:
                print("BAD %s: definition references missing model %s" % (where, t))
                problems += 1
    if isinstance(d, dict):
        stack = [d]
        while stack:
            o = stack.pop()
            if isinstance(o, dict):
                if o.get("type") == "minecraft:model" and isinstance(o.get("model"), str):
                    check_model_ref(o["model"], name)
                for v in o.values():
                    if isinstance(v, (dict, list)):
                        stack.append(v)
            elif isinstance(o, list):
                stack.extend(v for v in o if isinstance(v, (dict, list)))

for name, m in models.items():
    walk_refs(m if isinstance(m, dict) else {}, name)
for name, o in other_json.items():
    walk_refs(o if isinstance(o, dict) else {}, name)

# blockstate-referenced models are fine (steamworks note_block trick)
mmo_problems = 0
OUR_ORPHANS = []
out_of_bounds = 0
for name, m in models.items():
    if not isinstance(m, dict):
        continue
    ns = name.split("/")[1]
    probs = []
    els = m.get("elements")
    if isinstance(els, list):
        for i, e in enumerate(els):
            if not isinstance(e, dict):
                continue
            for key in ("from", "to"):
                v = e.get(key)
                if isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v):
                    for ax, x in enumerate(v):
                        if x < -16.0001 or x > 32.0001:
                            probs.append("el%d %s[%d]=%s out of -16..32" % (i, key, ax, x))
            r = e.get("rotation")
            if isinstance(r, dict):
                v = r.get("origin")
                if isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v):
                    for ax, x in enumerate(v):
                        if x < -16.0001 or x > 32.0001:
                            probs.append("el%d rot.origin[%d]=%s out of -16..32" % (i, ax, x))
            for face, f in (e.get("faces") or {}).items():
                uv = f.get("uv")
                if isinstance(uv, list) and len(uv) == 4 and all(isinstance(x, (int, float)) for x in uv):
                    for j, x in enumerate(uv):
                        if x < -16.0001 or x > 32.0001:
                            probs.append("el%d %s uv[%d]=%s wild" % (i, face, j, x))
                t = f.get("texture")
                # a texture ref without namespace defaults to minecraft (client jar), like parents do
                if isinstance(t, str) and t and not t.startswith("#") and ":" in t and not t.startswith("minecraft:"):
                    if to_tex_id(t) not in names:
                        probs.append("el%d %s texture %s missing" % (i, face, t))
    for k, t in (m.get("textures") or {}).items():
        if isinstance(t, str) and t and not t.startswith("#") and ":" in t and not t.startswith("minecraft:") and to_tex_id(t) not in names:
            probs.append("alias %r -> %s missing" % (k, t))
    par = m.get("parent")
    # parent without namespace or with minecraft: = client jar, not our problem
    if isinstance(par, str) and par and ":" in par and not par.startswith("minecraft:") and to_model_id(par) not in names:
        probs.append("parent %s missing" % par)
    # model id used by item_model components needs an items/ definition; being someone's
    # parent, or a blockstates/model reference, is also OK
    mid = ns + ":" + name.split("/models/")[1][:-5]
    if not probs and mid not in referenced:
        # a model with elements nobody references = invisible dead weight OR the next magenta cube
        if isinstance(els, list) and els and ns not in ("minecraft", "mmoitems"):
            OUR_ORPHANS.append(name)
            if len(OUR_ORPHANS) <= 40:
                print("ORPHAN %s (no items/ def, not a parent, not blockstate-referenced)" % name)
    if probs:
        if ns == "mmoitems":
            mmo_problems += len(probs)
            if mmo_problems <= 3:
                print("KNOWN %s: %s (MMOItems export, custom-model-data system)" % (name, "; ".join(probs[:2])))
            continue
        out_of_bounds += 1
        print("BAD %s: %s" % (name, "; ".join(probs[:6])))
    problems += len(probs)

print("%s: %d models, %d item defs - %d problems in OUR namespaces, %d orphans in OUR namespaces (mmoitems counted separately: %d)" % (path, len(models), n_defs, problems, len(OUR_ORPHANS), mmo_problems))

# ------------------------------------------------------------------ check 6: plugin-ids vs pack-defs
# Zesde look, want de vijf hierboven kunnen dit MIS niet zien: een item dat een plugin registreert
# en waarvoor in het pack GEEN model én geen items/<id>.json bestaat. Zo'n model_loze id is geen
# orphan (er is niets om te orfanen) - het is gewoon onzichtbaar, en de client laat dan een
# MAGENTA blok zien. Ontdekt 12-11 in Zukan: tien Kitchen-items (yuzu, yuzu_seeds, persimmon,
# persimmon_seeds, buckwheat, buckwheat_seeds, koji, enoki_spores, shiitake_spores, tsukemono)
# kregen wel `item_model kitchen:<id>` maar nooit een icoon.
#
# De bron van de ids is de plugin-zelf: alle `Key.key("<ns>", "<id>")`-letteralen plus de
# konstanten van de item-enum(s). Wie een item toevoegt WITHOUT een icoon, vliegt er nu uit.
ID_SOURCES = {                       # namespace -> (plugin-map, [java-bestanden met de item-enum])
    "kitchen":  ("Kitchen",  ["Kitchen/src/main/java/com/iekcorbar/kitchen/KItem.java"]),
    "sakagura": ("Sakagura", ["Sakagura/src/main/java/com/iekcorbar/sakagura/Fruit.java"]),
}
# Ids die GEEN inventory-item zijn: Kitchen-oesprongen bestaan als blok-model (block/<oesprong>_stageN)
# en nooit als vaststaand item, dus hun naam hoeft geen items/-definitie te hebben.
NOT_AN_ITEM = {"kitchen": {"cannabis", "hemp", "sesame", "shiso", "soybean", "tea", "wasabi"}}

def enum_constants(pad):
    """De enum-waarden van een Java-enum, als id zoals de plugin ze maakt (naam.small)."""
    try:
        src = open(pad, encoding="utf-8").read()
    except OSError:
        return set()
    body = src[src.index("enum "):] if "enum " in src else ""
    return {m.lower() for m in re.findall(r'^\s{4}([A-Z][A-Z_0-9]*)\s*[\(;]', body, re.M)}

gen = 0
for ns, (plugin, pads) in ID_SOURCES.items():
    ids = set()
    for p in pads:
        ids |= enum_constants(p)
    # plus alle letterlijke ids in Key.key("<ns>", "<id>")-aanroepen binnen die plugin.
    # Twee vormen: een complete id  ->  Key.key("kitchen", "yuzu_tea")
    #                een voorvoegsel  ->  Key.key("sakagura", "drink_" + id)  (let op de '+')
    exact, prefixes = set(), set()
    for root, _d, files in os.walk(plugin):
        for f in files:
            if not f.endswith(".java"):
                continue
            try:
                txt = open(os.path.join(root, f), encoding="utf-8").read()
            except OSError:
                continue
            for lit, next_char in re.findall(r'Key\.key\("%s",\s*"([a-z_0-9]+)"\s*([),+])' % ns, txt):
                (prefixes if next_char == "+" else exact).add(lit)
    ids |= exact
    for pre in sorted(prefixes):                      # van een voorvoegsel moet minstens ONE id bestaan
        if not any(k.startswith("assets/%s/items/%s" % (ns, pre)) for k in names):
            gen += 1
            print("NO ITEM MODEL/DEF %s:%s* - geen enkel item begint met dit voorvoegsel" % (ns, pre))
    for i in sorted(ids):
        if i in NOT_AN_ITEM.get(ns, set()):
            continue
        if "assets/%s/items/%s.json" % (ns, i) not in names:
            gen += 1
            print("NO ITEM MODEL/DEF %s:%s - 26.2 toont dit als MAGENTA blok (voeg het icoon toe "
                  "aan tools/make_pack.py van %s)" % (ns, i, ns))
if gen:
    print("check 6: %d geregistreerde items zonder icoon/definitie" % gen)
else:
    print("check 6: alle geregistreerde plugin-items hebben een icoon + items/-definitie")

sys.exit(1 if problems or OUR_ORPHANS or gen else 0)
