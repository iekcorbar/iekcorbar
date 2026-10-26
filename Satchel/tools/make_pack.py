"""Satchel resource pack: the six backpacks and the upgrade plates.

All our own design (steampunk: canvas, leather straps, riveted metal plates, a little brass gauge on the flap):
low-poly element models coloured from one palette texture, the same technique as the Kura pack.
Item models: satchel:<item id>; fronts face north (z = 0).

Usage: python -I make_pack.py <out folder>  -> <out>/Satchel-pack/ + <out>/Satchel-pack.zip
Then (repo root) python -I scripts/merge-packs.py puts it into Iekcorbar-pack.zip.
"""
import json, os, random, struct, sys, zipfile, zlib


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


ROOT = None


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, bytes):
        open(path, "wb").write(data)
    else:
        open(path, "w", encoding="utf-8").write(json.dumps(data, indent=1))


def clamp(v):
    return max(0, min(255, int(v)))


# ------------------------------------------------------------------ palette

PALETTE = {
    "leather": (110, 70, 42), "leather_dark": (70, 44, 28), "leather_light": (150, 100, 62),
    "brass": (214, 170, 72), "brass_dark": (150, 112, 44), "brass_light": (240, 210, 120),
    "iron": (176, 180, 184), "iron_dark": (96, 100, 106), "iron_light": (214, 218, 222),
    "red": (180, 36, 36), "red_dark": (110, 24, 24), "green": (70, 150, 70), "wood": (126, 84, 50),
    "gauge": (240, 236, 220), "black": (24, 22, 24), "crystal": (120, 220, 230), "crystal_glow": (190, 250, 255),
    "gold": (236, 196, 70), "plate": (84, 90, 100), "plate_dark": (58, 62, 72), "stitch": (230, 220, 190),
}
# per tier: cloth (main), cloth_dark (shade), cloth_light (pockets), metal (rivets/trim), metal_light, glass
TIERS = {
    "canvas_backpack": [(176, 146, 98), (136, 108, 70), (200, 172, 124), (150, 112, 44), (214, 170, 72), (240, 210, 120)],
    "copper_backpack": [(150, 82, 52), (108, 56, 36), (190, 112, 76), (196, 108, 68), (228, 150, 104), (92, 168, 140)],
    "iron_backpack": [(96, 104, 114), (66, 72, 80), (130, 138, 148), (176, 180, 184), (214, 218, 222), (120, 220, 230)],
    "gold_backpack": [(150, 36, 40), (104, 24, 28), (186, 60, 62), (236, 196, 70), (250, 226, 130), (255, 244, 190)],
    "diamond_backpack": [(36, 74, 110), (24, 48, 76), (58, 108, 150), (120, 220, 230), (190, 250, 255), (230, 255, 255)],
    "netherite_backpack": [(56, 46, 56), (36, 30, 38), (84, 70, 86), (98, 86, 100), (150, 134, 154), (240, 120, 60)],
}
for tier, c in TIERS.items():
    for n, rgb in zip(("cloth", "cloth_dark", "cloth_light", "metal", "metal_light", "glass"), c):
        PALETTE[f"{tier}_{n}"] = rgb
NAMES = list(PALETTE)
assert len(NAMES) <= 256


def palette_png():
    rnd = random.Random(23)
    px = [[(0, 0, 0, 255)] * 128 for _ in range(128)]
    for i, n in enumerate(NAMES):
        r, g, b = PALETTE[n]
        cx, cy = (i % 16) * 8, (i // 16) * 8
        for y in range(8):
            for x in range(8):
                d = rnd.randint(-6, 6)
                px[cy + y][cx + x] = (clamp(r + d), clamp(g + d), clamp(b + d), 255)
    return png(px)


def uv(col):
    i = NAMES.index(col)
    u, v = i % 16, i // 16
    return [u + 0.15, v + 0.15, u + 0.85, v + 0.85]


# ------------------------------------------------------------------ model building

def box(x1, y1, z1, x2, y2, z2, col, faces=None, rot=None):
    f = {k: {"uv": uv(col), "texture": "#p"} for k in ("north", "south", "east", "west", "up", "down")}
    for k, c in (faces or {}).items():
        f[k] = {"uv": uv(c), "texture": "#p"}
    e = {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": f}
    if rot:
        e["rotation"] = {"axis": rot[0], "angle": rot[1], "origin": rot[2]}
    return e


def bounds(elems):
    lo = [min(e["from"][i] for e in elems) for i in range(3)]
    hi = [max(e["to"][i] for e in elems) for i in range(3)]
    return lo, hi


def model(name, elems, block_icon=True):
    lo, hi = bounds(elems)
    size = max(hi[i] - lo[i] for i in range(3))
    m = {"textures": {"p": "satchel:item/palette", "particle": "satchel:item/palette"}, "elements": elems}
    if block_icon:
        s = round(0.625 * 16 / max(size, 1), 3)
        m["display"] = {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [s, s, s]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.35] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -1, 0], "scale": [0.6] * 3},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.4] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.45] * 3},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.45] * 3},
            "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [1, 1, 1]},
        }
    else:
        s = round(min(1.2, 0.8 * 16 / max(size, 1)), 3)
        m["display"] = {
            "gui": {"rotation": [20, 200, 0], "translation": [0, 0, 0], "scale": [s, s, s]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [s * 0.5] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [s * 0.8] * 3},
            "thirdperson_righthand": {"rotation": [60, 0, 0], "translation": [0, 3, 1], "scale": [s * 0.55] * 3},
            "firstperson_righthand": {"rotation": [0, -20, 10], "translation": [1, 2, 0], "scale": [s * 0.6] * 3},
            "firstperson_lefthand": {"rotation": [0, 20, -10], "translation": [1, 2, 0], "scale": [s * 0.6] * 3},
            "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [s] * 3},
        }
    write(f"assets/satchel/models/item/{name}.json", m)
    write(f"assets/satchel/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"satchel:item/{name}"}})


# ------------------------------------------------------------------ the backpacks

def backpack(tier):
    c, cd, cl, me, ml, gl = (f"{tier}_{n}" for n in ("cloth", "cloth_dark", "cloth_light", "metal", "metal_light", "glass"))
    e = [
        box(3, 0.5, 4, 13, 11, 10, c),                       # main body
        box(2.6, 0, 3.6, 13.4, 1.6, 10.4, "leather_dark"),   # leather base
        box(3.2, 9.8, 3.7, 12.8, 12.4, 10.2, cd),            # top flap
        box(3.2, 6.6, 3.6, 12.8, 12.4, 4.0, cd),             # front flap hanging down
        box(4.5, 1.8, 3.1, 11.5, 6.2, 4.0, cl),              # front pocket
        box(4.5, 6.0, 3.0, 11.5, 6.4, 4.0, "leather"),       # pocket lip
        box(1.4, 1.8, 5, 3, 7, 9, cl),                       # side pouches
        box(13, 1.8, 5, 14.6, 7, 9, cl),
        box(1.2, 6.6, 5, 3, 7.2, 9, "leather"),
        box(13, 6.6, 5, 14.8, 7.2, 9, "leather"),
        box(7.2, 5.4, 3.4, 8.8, 11.6, 3.7, "leather"),       # front strap
        box(6.6, 6.4, 3.1, 9.4, 8.6, 3.5, ml),               # buckle
        box(7.3, 7.0, 3.0, 8.7, 8.0, 3.2, me),
        box(5.8, 12.4, 5.4, 10.2, 13.2, 6.6, "leather"),     # carry handle
        box(4.6, 1.0, 10, 6.2, 11, 10.7, "leather_dark"),    # shoulder straps on the back
        box(9.8, 1.0, 10, 11.4, 11, 10.7, "leather_dark"),
        box(4.6, 0.6, 10.3, 11.4, 1.6, 10.9, "leather"),
    ]
    # rivets on the corners of the flap and brass gauge on the pocket
    for x in (3.6, 12.2):
        e.append(box(x, 11.2, 3.2, x + 0.6, 11.8, 3.5, me))
    e.append(box(5.4, 3.0, 2.8, 8.0, 5.6, 3.1, me))           # gauge rim
    e.append(box(5.8, 3.4, 2.6, 7.6, 5.2, 2.9, "gauge"))
    e.append(box(6.5, 3.6, 2.5, 6.9, 4.9, 2.7, "red"))        # needle
    if tier in ("iron_backpack", "gold_backpack", "diamond_backpack", "netherite_backpack"):
        e.append(box(3.0, 0.5, 3.8, 3.6, 11, 4.2, me))       # side plates
        e.append(box(12.4, 0.5, 3.8, 13.0, 11, 4.2, me))
    if tier in ("diamond_backpack", "netherite_backpack"):
        e.append(box(9.0, 3.2, 2.7, 11.0, 5.4, 3.1, gl))     # clasp stone
    if tier == "canvas_backpack":
        e.append(box(3.4, 12.4, 4.5, 12.6, 14.6, 8.5, "leather_light", rot=("y", 0, [8, 13, 6.5])))   # bedroll
        e.append(box(3.4, 13.0, 4.3, 12.6, 13.4, 4.6, "leather"))
        e.append(box(3.4, 13.0, 8.4, 12.6, 13.4, 8.7, "leather"))
    return e


# ------------------------------------------------------------------ the upgrade plates

def plate(extra):
    e = [
        box(1.5, 1.5, 6.5, 14.5, 14.5, 8.5, "plate"),
        box(1.0, 1.0, 6.2, 15.0, 1.8, 8.8, "brass_dark"), box(1.0, 14.2, 6.2, 15.0, 15.0, 8.8, "brass_dark"),
        box(1.0, 1.0, 6.2, 1.8, 15.0, 8.8, "brass_dark"), box(14.2, 1.0, 6.2, 15.0, 15.0, 8.8, "brass_dark"),
    ]
    for x, y in ((2.6, 2.6), (12.6, 2.6), (2.6, 12.6), (12.6, 12.6)):
        e.append(box(x, y, 5.5, x + 0.8, y + 0.8, 6.3, "brass_light"))
    return e + extra


def sym_base():
    return plate([box(5, 5, 5.5, 11, 11, 6.3, "plate_dark"), box(7.2, 7.2, 5.3, 8.8, 8.8, 6.1, "brass")])


def sym_pickup():
    return plate([
        box(7, 6.5, 5.5, 9, 12.5, 6.4, "brass_light"),
        box(5, 5.0, 5.5, 11, 7.0, 6.4, "brass_light", faces={}),
        box(6, 4.2, 5.5, 10, 5.2, 6.4, "brass_light"),
        box(3.8, 2.8, 5.5, 12.2, 3.8, 6.4, "leather"), box(3.8, 2.8, 5.5, 4.8, 5.4, 6.4, "leather"), box(11.2, 2.8, 5.5, 12.2, 5.4, 6.4, "leather"),
    ])


def sym_magnet():
    return plate([
        box(4, 4, 5.5, 6.6, 12.5, 6.5, "red"), box(9.4, 4, 5.5, 12, 12.5, 6.5, "red"),
        box(4, 3.4, 5.5, 12, 6, 6.5, "red"),
        box(4, 10.5, 5.5, 6.6, 12.5, 6.5, "gauge"), box(9.4, 10.5, 5.5, 12, 12.5, 6.5, "gauge"),
        box(6.6, 4.4, 5.5, 9.4, 5.6, 6.5, "plate_dark"),
    ])


def sym_feeding():
    return plate([
        box(4.8, 3.6, 5.5, 11.2, 9.6, 6.5, "red"), box(5.6, 9.6, 5.5, 10.4, 10.2, 6.5, "red"),
        box(4.2, 5.0, 5.5, 4.8, 8.4, 6.5, "red_dark"), box(11.2, 5.0, 5.5, 11.8, 8.4, 6.5, "red_dark"),
        box(7.6, 10.0, 5.5, 8.4, 12.2, 6.5, "wood"), box(8.4, 11.0, 5.5, 11, 12.4, 6.5, "green"),
    ])


def sym_everlasting():
    return plate([
        box(3.8, 3.8, 5.5, 12.2, 5.0, 6.5, "gold"), box(3.8, 11.0, 5.5, 12.2, 12.2, 6.5, "gold"),
        box(3.8, 3.8, 5.5, 5.0, 12.2, 6.5, "gold"), box(11.0, 3.8, 5.5, 12.2, 12.2, 6.5, "gold"),
        box(6.4, 6.4, 5.4, 9.6, 9.6, 6.6, "crystal"), box(7.2, 7.2, 5.2, 8.8, 8.8, 6.7, "crystal_glow"),
    ])


def sym_deposit():
    return plate([
        box(3.5, 3.5, 5.5, 12.5, 4.5, 6.5, "leather"), box(3.5, 3.5, 5.5, 4.5, 8.5, 6.5, "leather"), box(11.5, 3.5, 5.5, 12.5, 8.5, 6.5, "leather"),
        box(7, 6.5, 5.5, 9, 12.5, 6.5, "brass_light"), box(5.5, 6.5, 5.5, 10.5, 8, 6.5, "brass_light"), box(6.5, 5.2, 5.5, 9.5, 6.5, 6.5, "brass_light"),
    ])


def sym_restock():
    return plate([
        box(3.5, 3.5, 5.5, 12.5, 4.5, 6.5, "leather"), box(3.5, 3.5, 5.5, 4.5, 8.5, 6.5, "leather"), box(11.5, 3.5, 5.5, 12.5, 8.5, 6.5, "leather"),
        box(7, 5.5, 5.5, 9, 11.5, 6.5, "green"), box(5.5, 10, 5.5, 10.5, 11.5, 6.5, "green"), box(6.5, 11.5, 5.5, 9.5, 12.8, 6.5, "green"),
    ])


def sym_refill():
    return plate([
        box(4, 4, 5.5, 12, 12, 6.5, "iron_dark"), box(5, 5, 5.2, 11, 11, 5.8, "gauge"),
        box(7.4, 6, 5.0, 8.6, 9.4, 5.6, "red"), box(7.4, 8.4, 5.0, 10, 9.4, 5.6, "red"),
    ])


def main():
    global ROOT
    out = sys.argv[1]
    ROOT = os.path.join(out, "Satchel-pack")
    write("pack.mcmeta", {"pack": {"description": "Satchel (backpacks)", "pack_format": 64,
                                   "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}})
    write("assets/satchel/textures/item/palette.png", palette_png())
    n = 0
    for tier in TIERS:
        model(tier, backpack(tier), True)
        n += 1
    for name, e in {"upgrade_base": sym_base(), "pickup_upgrade": sym_pickup(), "magnet_upgrade": sym_magnet(),
                    "feeding_upgrade": sym_feeding(), "everlasting_upgrade": sym_everlasting(),
                    "deposit_upgrade": sym_deposit(), "restock_upgrade": sym_restock(), "refill_upgrade": sym_refill()}.items():
        model(name, e, False)
        n += 1
    zpath = os.path.join(out, "Satchel-pack.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(ROOT):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                z.write(full, os.path.relpath(full, ROOT).replace(os.sep, "/"))
    print("wrote", zpath, "with", n, "models")


if __name__ == "__main__":
    main()
