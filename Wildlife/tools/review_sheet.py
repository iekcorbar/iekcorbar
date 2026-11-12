# Model-voor-model reviewhulp voor de Wildlife-dieren.
#
#   python -I tools/review_sheet.py meten [soort ...]      -> meetrapport (orienteering, poten, vloer)
#   python -I tools/review_sheet.py sheet out.png [soort]  -> posesheet: rust, zij-loop, vooraan, achter
#   python -I tools/review_sheet.py soorten                -> alle dieren-modellen, één per regel
#   python -I tools/review_sheet.py vergelijk out.png soort  -> HEAD (build nu) vs werkboom, met mens
#
# Opties (omgevingsvariabelen):
#   STEVE=1      elk vak krijgt de Species-schaal uit Species.java (authored px -> in-game px) én er
#                staat een 1,8 m mens naast, zodat de rijgrote verhouding met het oog te zien is
#   CELL=240     groter raster; MODELS_DIR=<pad>  een andere modelbron bekijken (oud vs nieuw)
#
# Waarom: FreeMinecraftModels draait een model om de entity-yaw en gebruikt **-X als voorkant**
# (make_animals.oriented() draait de ontwerpas -Z daarheen). Dieren die "opzij" lopen hebben hun
# hoofd langs Z. Deze tool meet dat per bot, zonder het spel in te gaan; de sheet laat het oog
# oordelen. Habitat Comes uit Species.java: AIR/WATER/POND/CAVE-dieren hoeft niet op de grond te
# staan, LAND/SHORE wel.
#
#   CELL=200 python -I tools/review_sheet.py sheet /tmp/opencode/wl.png bear lion
import glob
import json
import math
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# MODELS_DIR: een andere (tijdelijke) modelbron bekijken, bijvoorbeeld het model vóór de herbouw.
MODELS = os.environ.get("MODELS_DIR") or os.path.join(ROOT, "fmm-models", "wildlife")
SPECIES_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "iekcorbar", "wildlife", "Species.java")

# geen losse dieren: varianten, beeldjes, eieren, bottle-items en losse ledematen
SKIP_SUFFIX = ("_baby", "_rare", "_stat", "_egg")
SKIP_HAS = ("bottled", "field_journal")
SKIP_EXTRA = {"wl_lizard_tail", "wl_crab_claw", "wl_field_journal"}


def norm(stem):
    """Neemt zowel 'lion' als 'wl_lion' aan en geeft de model-naam terug."""
    return stem if stem.startswith("wl_") else "wl_" + stem


def species_files():
    out = []
    for f in sorted(glob.glob(os.path.join(MODELS, "*.bbmodel"))):
        stem = os.path.basename(f)[:-len(".bbmodel")]
        if stem in SKIP_EXTRA or any(s in stem for s in SKIP_HAS) or any(stem.endswith(s) for s in SKIP_SUFFIX):
            continue
        out.append(stem)
    return out


def looks():
    """Alle look-namen die Species.java bij een soort registreert (songbird_cardinal, dragon_kura, ...)."""
    txt = open(SPECIES_JAVA, encoding="utf-8").read()
    return set(re.findall(r'"(wl_[a-z_0-9]+)"', txt))


def habitats():
    """wl_<id> -> (weergavenaam, habitat, schaal, basis-mob) uit Species.java"""
    txt = open(SPECIES_JAVA, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'^\s{4}([A-Z_0-9]+)\("([^"]+)",\s*EntityType\.([A-Z_]+),\s*([0-9.]+),.*?Habitat\.([A-Z]+)',
                         txt, re.M | re.S):
        ident, display, base, scale, hab = m.groups()
        key = "wl_" + ident.lower()
        out[key] = (display, hab, float(scale), base)
    return out


def load(stem):
    return json.load(open(os.path.join(MODELS, stem + ".bbmodel"), encoding="utf-8"))


def tree(model):
    els = {e["uuid"]: e for e in model["elements"]}
    nodes = {}

    def rec(n):
        nodes[n["uuid"]] = n
        for c in n.get("children", []):
            if isinstance(c, dict):
                rec(c)
        n["_els"] = [els[c] for c in n.get("children", []) if isinstance(c, str) and c in els]
        n["_kids"] = [nodes[c["uuid"]] for c in n.get("children", []) if isinstance(c, dict)]
        return n
    roots = [rec(n) for n in model["outliner"] if isinstance(n, dict)]
    return roots, nodes


def rot(v, r, o):
    """Blockbench: X dan Y dan Z, om origin o (missende velden = 0)."""
    x, y, z = v[0] - o[0], v[1] - o[1], v[2] - o[2]
    rx, ry, rz = (math.radians(a or 0) for a in ((r or [0, 0, 0]) + [0, 0, 0])[:3])
    cx, sx = math.cos(rx), math.sin(rx)
    y, z = y * cx - z * sx, y * sx + z * cx
    cy, sy = math.cos(ry), math.sin(ry)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cz, sz = math.cos(rz), math.sin(rz)
    x, y = x * cz - y * sz, x * sz + y * cz
    return x + o[0], y + o[1], z + o[2]


def box_of(node):
    """Rest-bbox + volume-gewogen midden van een bot en zijn kinderen.

    Let op: dit gaat langs de acht HOEKPUNTEN van elke box, niet langs het midden. Met de midde-
    len leek elke poot van >2 px dik "1,3 boven de grond" (het midden van 0..2,6 is 1,3) en zag
    een bbox te klein uit voor brede boxen - precies de maat-fouten die deze review moet vinden."""
    pts = []

    def walk(n, chain):
        ch = chain + [(n.get("rotation"), n.get("origin") or [0, 0, 0])]
        for e in n["_els"]:
            a, b = e["from"], e["to"]
            vol = max(1e-6, abs(b[0] - a[0]) * abs(b[1] - a[1]) * abs(b[2] - a[2]))
            corners = [(x, y, z) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
            for c in corners:
                px, py, pz = c
                for r, o in ch:
                    px, py, pz = rot((px, py, pz), r, o)
                pts.append((px, py, pz, vol))
            mids = [sum(v[i] for v in corners) / 8.0 for i in range(3)]
            px, py, pz = mids
            for r, o in ch:
                px, py, pz = rot((px, py, pz), r, o)
            pts.append((px, py, pz, 0.0))
        for k in n["_kids"]:
            walk(k, ch)
    walk(node, [])
    if not pts:
        return None
    w = sum(p[3] for p in pts)
    return {"cen": tuple(sum(p[i] * p[3] for p in pts) / w for i in range(3)),
            "lo": tuple(min(p[i] for p in pts) for i in range(3)),
            "hi": tuple(max(p[i] for p in pts) for i in range(3)), "n": len(pts)}


def find(nodes, *keys):
    best = None
    for n in nodes.values():
        nm = n.get("name", "")
        if any(nm == k or nm.endswith("_" + k) for k in keys):
            b = box_of(n)
            if b and (best is None or b["n"] > best[1]["n"]):
                best = (n, b)
    return best[0] if best else None


def measure(stem):
    m = load(stem)
    roots, nodes = tree(m)
    bones = {}
    for n in nodes.values():
        bones.setdefault(n["name"], n)
    body = bones.get("body") or roots[0]
    bb = box_of(body)
    parts = [box_of(r) for r in roots]
    parts = [p for p in parts if p] + [bb]
    lo = tuple(min(p["lo"][i] for p in parts) for i in range(3))
    hi = tuple(max(p["hi"][i] for p in parts) for i in range(3))
    hab = habitats().get(stem)
    if hab is None:
        # een look van een soort (songbird_canary, dragon_kura): trek de habitat van de basissoort over
        for kind in ("wl_willowisp", "wl_songbird", "wl_macaw", "wl_dragon"):
            if stem.startswith(kind + "_") and kind in habitats():
                hab = habitats()[kind]
                break
        else:
            # een jong of zeldzame variant is GEEN eigen soort: val terug op de basisssoort
            base = re.sub(r"(_rare|_baby)$", "", re.sub(r"(_rare|_baby)$", "", stem))
            hab = habitats().get(base) or ("?", "?", 1.0, "?")
    res = {"stem": stem, "display": hab[0], "habitat": hab[1], "scale": hab[2], "base": hab[3],
           "eigen soort": hab[1] != "?", "look": stem in looks(),
           "lo": lo, "hi": hi,
           "len_x": round(hi[0] - lo[0], 1), "len_z": round(hi[2] - lo[2], 1),
           "height": round(hi[1] - lo[1], 1), "floor": round(lo[1], 1),
           "bones": sorted(bones)}
    head = find(nodes, "h_head", "head", "neck")
    hb = box_of(head) if head else None
    res["head"] = hb["cen"] if hb else None
    if hb and bb:
        dx = hb["cen"][0] - bb["cen"][0]
        dz = hb["cen"][2] - bb["cen"][2]
        res["face"] = (round(dx, 1), round(dz, 1))
        res["axiaal"] = "X" if abs(dx) >= abs(dz) else "Z"
    clips = [a["name"] for a in m.get("animations", [])]
    res["clips"] = clips
    res["clip"] = "fly" if "fly" in clips else ("walk" if "walk" in clips else (clips[0] if clips else "idle"))
    legs = [b for b in bones if b.startswith("leg")]
    arms = [b for b in bones if b.startswith("arm")]
    res["arms"] = len(arms)
    res["legs"] = len(legs)
    stand = legs or arms  # waar het dier (mee) op de grond staat
    if stand:
        cs = [box_of(bones[b])["cen"] for b in stand]
        res["leg_x"] = (round(min(c[0] for c in cs), 1), round(max(c[0] for c in cs), 1))
        res["leg_z"] = (round(min(c[2] for c in cs), 1), round(max(c[2] for c in cs), 1))
        res["leg_y"] = round(max(box_of(bones[b])["hi"][1] for b in stand), 1)  # hoogste = heup/schouder
        res["leg_lo"] = round(min(box_of(bones[b])["lo"][1] for b in stand), 1)  # laagste = voet
    return res


def verdict(r):
    """Aandachtspunten voor de review. Peil: FMM voorkant = -X, grond = y0, 16 px = 1 blok."""
    flags = []
    stands = r["habitat"] in ("LAND", "SHORE")
    humanoid = "arm_l" in r["bones"] or "arm_r" in r["bones"]
    quad = r.get("legs", 0) >= 4 and not humanoid
    ax = r.get("axiaal")
    if quad and ax == "Z":
        flags.append("HOOFD LANGS Z = loopt opzij")
    if quad and r.get("face") and abs(r["face"][1]) > 2.0:
        flags.append("hoofd %.1f px naar één kant" % r["face"][1])
    if humanoid and r.get("face") and abs(r["face"][0]) > 2.5:
        flags.append("hoofdkop %+.1f px voorovergebukt" % -r["face"][0])
    if r.get("legs", 0) or r.get("arms", 0):
        if not r["head"]:
            flags.append("geen hoofd-/nekbot")
    if stands:
        if r["floor"] < -0.6:
            flags.append("zakt door de vloer (%.1f)" % r["floor"])
        elif r["floor"] > 1.0:
            flags.append("zweeft %.1f boven de grond" % r["floor"])
        if "leg_lo" in r and r["leg_lo"] > 1.0:
            flags.append("poten eindigen %.1f te hoog" % r["leg_lo"])
    if r.get("legs", 0) >= 4 and quad:
        if abs(r["leg_z"][1] - r["leg_z"][0]) < 0.5:
            flags.append("poten niet links/rechts")
        if abs(r["leg_x"][1] - r["leg_x"][0]) < 0.5:
            flags.append("poten niet voor/achter")
    if "walk" not in r["clips"] and "fly" not in r["clips"]:
        flags.append("GEEN loop-/vliegclip")
    if r["habitat"] == "?" and not r["look"]:
        flags.append("niet in Species.java")
    return flags


def cmd_measure(args):
    want = [norm(a) for a in args] if args else species_files()
    bad = 0
    for stem in want:
        try:
            r = measure(stem)
        except Exception as e:  # noqa: BLE001
            print("%-26s METEN MISLUKT %s" % (stem, e))
            bad += 1
            continue
        f = verdict(r)
        led = f"{r.get('legs', 0)}poot" + (f"/{r['arms']}arm" if r.get("arms") else "")
        info = (f"{r['habitat']:<5} x{r['len_x']:.1f} z{r['len_z']:.1f} h{r['height']:.1f} "
                f"vloer{r['floor']:.1f} kop(" + ("%.1f/%.1f" % r["face"] if r.get("face") else "-")
                + f") {led} clip={r['clip']}"
                + ("  (eigen soort)" if r["eigen soort"] else ("  (look)" if r["look"] else "")))
        if f:
            bad += 1
            print("%-26s ! %s | %s" % (stem, "; ".join(f), info))
        else:
            print("%-26s ok %s" % (stem, info))
    print("\n%d soorten, %d met aandachtspunten" % (len(want), bad))


def cmd_sheet(args):
    out, rest = args[0], args[1:]
    want = [norm(a) for a in rest] if rest else species_files()
    from PIL import Image, ImageDraw
    cell = int(os.environ.get("CELL", "150"))
    steve = os.environ.get("STEVE", "") == "1"   # 1,8 m mens ernaast + de Species-schaal toepassen
    strips, labels = [], []
    for stem in want:
        r = measure(stem)
        clip = r["clip"]
        specs = ["iso::0", "side:%s:0.25" % clip, "front:%s:0.5" % clip, "back:%s:0.75" % clip]
        tmp = os.path.join("/tmp/opencode", "wl-strip-%s.png" % stem)
        env = dict(os.environ)
        if steve:
            env.update({"REF": "steve", "SCALE": repr(r["scale"])})
        p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "pose3d.py"), tmp,
                            os.path.join(MODELS, stem + ".bbmodel"), str(cell)] + specs,
                           capture_output=True, text=True, env=env, cwd=HERE)
        if p.returncode != 0 or not os.path.exists(tmp):
            print("render fout %s: %s" % (stem, (p.stderr.strip().splitlines() or ["?"])[-1]))
            continue
        im = Image.open(tmp).convert("RGB")
        strips.append(im)
        # in-game maten: authored px x Species-schaal, 16 px = 1 m
        m = lambda v: v * r["scale"] / 16.0
        lab = "%s%s  %.2f x %.2f x %.2f m" % (stem.replace("wl_", ""), "!" if verdict(r) else "",
                                              m(r["len_x"]), m(r["height"]), m(r["len_z"]))
        labels.append(lab if steve else "%s%s" % (stem.replace("wl_", ""), "!" if verdict(r) else ""))
    if not strips:
        print("niets gerenderd")
        return
    pad = 26
    head = Image.new("RGB", (max(s.width for s in strips) + pad, 18), (230, 230, 230))
    hd = ImageDraw.Draw(head)
    for i, t in enumerate(["rust", "lopend van opzij", "van voren", "van achter"]):
        hd.text((pad + i * cell + 6, 5), t, fill=(60, 60, 60))
    img = Image.new("RGB", (max(s.width for s in strips) + pad,
                            head.height + sum(s.height for s in strips)), (246, 246, 246))
    img.paste(head, (0, 0))
    y = head.height
    for lab, s in zip(labels, strips):
        box = Image.new("RGB", (s.height, pad), (246, 246, 246))
        bd = ImageDraw.Draw(box)
        bd.text((4, pad // 2 - 5), lab, fill=(20, 20, 20))
        img.paste(box.rotate(90, expand=True), (0, y))
        img.paste(s, (pad, y))
        y += s.height
    img.save(out)
    print("wrote %s (%d soorten, %dx%d, cell %d)" % (out, len(strips), img.width, img.height, cell))


def head_scale(stem):
    """De Species-schaal zo als hij in HEAD staat - voor de vergelijk-rule.
    Anders krijgt het OUDE model de NIEUWE schaal te zien en is 'oud' plots een dwerg."""
    rel = "Wildlife/src/main/java/com/iekcorbar/wildlife/Species.java"
    top = subprocess.run(["git", "-C", ROOT, "rev-parse", "--show-toplevel"],
                         capture_output=True, text=True).stdout.strip() or ROOT
    p = subprocess.run(["git", "-C", top, "show", "HEAD:" + rel], capture_output=True, text=True)
    if p.returncode != 0:
        return None
    ident = stem.replace("wl_", "").upper()
    m = re.search(r'^\s{4}' + ident + r'\("[^"]+",\s*EntityType\.[A-Z_]+,\s*([0-9.]+)', p.stdout, re.M)
    return float(m.group(1)) if m else None


def cmd_vergelijk(args):
    """Oud (git HEAD) tegenover nieuw (werkboom), per dier, met de maatstaf-mens erbij.

      python -I tools/review_sheet.py vergelijk out.png olifant [leeuw ...]

    Rijen = bronnen, kolommen = rust-profiel / lopend-profiel / rust-iso. Maten in het label zijn
    de IN-GAME meters: authored px x Species-schaal (FMM schaalt het model met generic_scale).
    """
    global MODELS
    out, wants = args[0], [norm(a) for a in args[1:]]
    if not wants:
        print("geen soort gegeven")
        return
    from PIL import Image, ImageDraw
    cell = int(os.environ.get("CELL", "220"))
    top = subprocess.run(["git", "-C", ROOT, "rev-parse", "--show-toplevel"],
                         capture_output=True, text=True).stdout.strip() or os.path.abspath(ROOT)
    cur = MODELS
    old = os.path.join("/tmp/opencode", "wl-vergelijk-oud")
    os.makedirs(old, exist_ok=True)
    rows = []                                    # (label, modeldir)
    for stem in wants:
        # git toont alleen paden relatief aan de werkboom-root - en er staat ook een (verouderde)
        # fmm-models/wildlife op de repo-root, dus hier fout oplossen = het verkeerde dier tonen
        rel = os.path.relpath(os.path.join(cur, stem + ".bbmodel"), top)
        p = subprocess.run(["git", "-C", top, "show", "HEAD:" + rel], capture_output=True, text=True)
        if p.returncode != 0:
            print("  (git HEAD heeft %s niet - alleen de huidige versie)" % stem)
            rows.append(("nieuw", cur, stem))
            continue
        open(os.path.join(old, stem + ".bbmodel"), "w", encoding="utf-8").write(p.stdout)
        rows.append(("HEAD (build nu)", old, stem))
        rows.append(("werkboom", cur, stem))
    views = [("side::0", "rust (profiel)"), ("side:walk:0.25", "lopend (profiel)"), ("iso::0", "rust (iso)")]
    tiles, labs, bad = [], [], 0
    for tag, src, stem in rows:
        MODELS = src                             # measure() leest uit de bron die nu aan de beurt is
        try:
            r = measure(stem)
            if src == old:                       # HEAD-rij: ook de schaal van HEAD, niet de nieuwe
                hs = head_scale(stem)
                if hs:
                    r["scale"] = hs
        except Exception as e:  # noqa: BLE001
            print("meten mislukt %s: %s" % (stem, e)); bad += 1
            MODELS = cur
            continue
        clip = r["clip"]
        row = []
        for spec, _t in views:
            v, c, tm = spec.split(":")
            tmp = os.path.join("/tmp/opencode", "wl-cmp-%s.png" % stem)
            env = dict(os.environ, REF="steve", SCALE=repr(r["scale"]))
            p = subprocess.run([sys.executable, "-I", os.path.join(HERE, "pose3d.py"), tmp,
                                os.path.join(src, stem + ".bbmodel"), str(cell), spec],
                               capture_output=True, text=True, env=env, cwd=HERE)
            row.append(Image.open(tmp).convert("RGB") if p.returncode == 0 and os.path.exists(tmp) else None)
        m = lambda v: v * r["scale"] / 16.0
        # geen gedachtestreep in de tekst: het standaard-bitmaplettertype van PIL kent U+2014 niet
        labs.append("%s / %s  %s  %.2f x %.2f x %.2f m%s" % (
            stem.replace("wl_", ""), tag, "!" if verdict(r) else "ok",
            m(r["len_x"]), m(r["height"]), m(r["len_z"]),
            "" if not verdict(r) else "  [" + "; ".join(verdict(r)) + "]"))
        tiles.append(row)
        MODELS = cur
    pad, lab_w, head = 10, 250, 24
    img = Image.new("RGB", (lab_w + len(views) * (cell + pad) + pad,
                            len(tiles) * (cell + head + 34) + 2 * pad), (246, 246, 246))
    d = ImageDraw.Draw(img)
    for i, (_s, t) in enumerate(views):
        d.text((lab_w + pad + i * (cell + pad), 8), t, fill=(60, 60, 60))
    for r, (lab, row) in enumerate(zip(labs, tiles)):
        y = head + 34 + r * (cell + head + 34)
        d.text((6, y - 18), lab, fill=(20, 20, 20))
        d.text((6, y + cell - 12), "(grijs = speler 1,8 m)", fill=(150, 150, 150))
        for c, t in enumerate(row):
            if t:
                img.paste(t, (lab_w + pad + c * (cell + pad), y))
    img.save(out)
    print("wrote %s (%d rijen x %d kolommen%s)" % (out, len(tiles), len(views),
                                                   ", %d mislukt" % bad if bad else ""))


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] in ("-h", "--help"):
        print(__doc__)
    elif a[0] == "meten":
        cmd_measure(a[1:])
    elif a[0] == "sheet":
        cmd_sheet(a[1:])
    elif a[0] == "vergelijk":
        cmd_vergelijk(a[1:])
    elif a[0] == "soorten":
        print("\n".join(s.replace("wl_", "") for s in species_files()))
