"""Kitchen resource pack: crop plant models + all item/prop/meal models.

Everything here is our own design: low-poly element models coloured from one palette
texture, crop sprites drawn pixel by pixel below. The plugin uses:
  - item_model kitchen:<id> for every KItem, every prop and every meal piece (kitchen:meal_*)
  - kitchen:block/<crop>_stage<N> / <crop>_wild as the ITEM_MODEL of the ItemDisplay that
    shows a growing crop (the crop block itself is a structure void; see Crops.java)

Usage: python make_pack.py <out folder>  -> <out>/Kitchen-pack/ + <out>/Kitchen-pack.zip
Put the zip in plugins/ResourcePackManager/mixer/ next to Steamworks-blocks.zip (no shared files).
"""
import json, math, os, random, shutil, struct, sys, zipfile, zlib

# ------------------------------------------------------------------ png + files

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
    "rice": (246, 244, 236), "rice_shade": (222, 218, 205), "nori": (34, 52, 38), "salmon": (240, 128, 84),
    "salmon_fat": (252, 206, 186), "tuna": (196, 44, 58), "cod": (236, 232, 222), "egg": (250, 206, 64),
    "egg_white": (250, 248, 240), "yolk": (245, 150, 30), "pork": (232, 170, 160), "pork_fat": (250, 236, 226),
    "beef": (176, 58, 60), "chicken": (238, 190, 170), "broth": (214, 160, 92), "miso_soup": (196, 140, 82),
    "noodle": (246, 226, 160), "negi": (110, 186, 70), "negi_white": (226, 240, 210), "leaf": (88, 160, 60),
    "leaf_dark": (54, 112, 44), "cabbage": (226, 236, 180), "cabbage_light": (240, 246, 214), "daikon": (246, 246, 240),
    "tea": (70, 132, 54), "tea_drink": (150, 186, 70), "kabocha": (240, 160, 40), "kabocha_skin": (52, 92, 52),
    "tempura": (232, 186, 96), "tofu": (248, 246, 238), "miso": (150, 98, 52), "soy": (64, 32, 20),
    "dashi": (222, 186, 110), "takuan": (246, 216, 60), "mochi": (252, 250, 246), "dango_pink": (246, 172, 186),
    "dango_green": (150, 200, 110), "glass": (196, 226, 230), "cork": (178, 128, 80), "cap_red": (196, 40, 40),
    "wood": (176, 126, 76), "wood_dark": (110, 72, 42), "wood_light": (212, 172, 120), "bamboo": (196, 186, 104),
    "bamboo_dark": (140, 132, 64), "porcelain": (244, 244, 248), "indigo": (46, 70, 140), "clay": (128, 76, 52),
    "clay_dark": (66, 40, 30), "brick": (150, 70, 52), "stone": (120, 118, 112), "iron": (196, 200, 204),
    "iron_dark": (122, 126, 132), "flint": (64, 62, 64), "diamond": (110, 226, 220), "netherite": (72, 64, 68),
    "handle": (92, 56, 36), "fire": (255, 170, 40), "fire_core": (255, 236, 120), "straw": (222, 196, 102),
    "straw_dark": (176, 148, 66), "soil": (82, 56, 36), "soil_rich": (54, 38, 26), "compost": (92, 70, 40),
    "tatami": (196, 196, 120), "tatami_edge": (40, 60, 50), "zabuton": (156, 40, 54), "gold": (226, 186, 70),
    "black": (30, 28, 30), "lacquer": (150, 30, 36), "carrot": (240, 130, 30), "potato": (210, 170, 100),
    "beet": (150, 30, 60), "brown": (120, 80, 50), "white": (250, 250, 250), "seed_dark": (70, 50, 36),
    "corn": (246, 206, 70), "corn_dark": (196, 150, 40), "tomato": (212, 50, 38), "chili": (176, 24, 20),
    "sesame": (240, 230, 200), "sesame_dark": (180, 160, 120),
"shiso": (120, 60, 120), "shiso_green": (80, 160, 60), "shiso_dark": (60, 40, 90),
"ginger": (220, 180, 100), "ginger_dark": (160, 120, 60),
"wasabi": (60, 160, 60), "wasabi_dark": (40, 120, 40),
"mizuna": (120, 180, 80), "mizuna_dark": (70, 120, 50),
"kabocha": (240, 160, 40), "kabocha_skin": (52, 92, 52), "kabocha_dark": (180, 110, 30),
"bamboo": (196, 186, 104), "bamboo_dark": (140, 132, 64), "bamboo_shoot": (220, 200, 140),
"eggplant": (90, 40, 120), "eggplant_dark": (60, 25, 90), "eggplant_light": (130, 70, 160),
"smoked": (120, 80, 50), "smoked_dark": (80, 50, 30),
"dried": (180, 140, 80), "dried_dark": (140, 100, 50),
"rack": (140, 100, 60), "rack_dark": (90, 65, 40),
"smoker": (60, 60, 60), "smoker_dark": (40, 40, 40), "smoker_iron": (120, 120, 130),
"clay_oven": (180, 120, 80), "clay_oven_dark": (120, 80, 50), "clay_oven_light": (220, 170, 120),
"steam": (230, 235, 245), "steamer": (140, 100, 70), "steamer_dark": (90, 65, 45), "steamer_lid": (160, 120, 80),
"bread": (220, 180, 110), "bread_dark": (180, 140, 80),
"melonpan": (240, 200, 100), "melonpan_dark": (200, 160, 70),
"castella": (230, 180, 90), "castella_dark": (190, 140, 60),
"nikuman": (230, 200, 170), "nikuman_dark": (190, 160, 130),
"anpan": (220, 170, 100), "anpan_dark": (180, 130, 70), "anpan_bean": (140, 80, 40),
"shiitake": (120, 90, 60), "shiitake_dark": (80, 60, 40), "shiitake_light": (160, 120, 80),
"enoki": (240, 230, 210), "enoki_dark": (200, 190, 170),
"log": (120, 80, 50), "log_dark": (80, 50, 30), "log_light": (160, 110, 70),
"enoki_bundle": (200, 190, 170), "enoki_bundle_dark": (160, 150, 130),
"noodle_maker": (160, 120, 80), "noodle_maker_dark": (110, 80, 50),
"udon": (240, 230, 200), "udon_dark": (200, 190, 160),
"soba": (140, 100, 60), "soba_dark": (100, 70, 40),
"ramen_noodles": (240, 230, 160), "ramen_noodles_dark": (200, 190, 120),
}
NAMES = list(PALETTE)
assert len(NAMES) <= 256, len(NAMES)


def palette_png():
    rnd = random.Random(7)
    px = [[(0, 0, 0, 255)] * 128 for _ in range(128)]
    for i, n in enumerate(NAMES):
        r, g, b = PALETTE[n]
        cx, cy = (i % 16) * 8, (i // 16) * 8
        for y in range(8):
            for x in range(8):
                d = rnd.randint(-7, 7)
                px[cy + y][cx + x] = (clamp(r + d), clamp(g + d), clamp(b + d), 255)
    return png(px)


def uv(col):
    i = NAMES.index(col)
    u, v = i % 16, i // 16  # 128 px texture: one 8 px cell = 1 uv unit
    return [u + 0.15, v + 0.15, u + 0.85, v + 0.85]

# ------------------------------------------------------------------ model building

def box(x1, y1, z1, x2, y2, z2, col, rot=None, top=None):
    """One element; `top` colours the up face differently. rot = (axis, angle, origin)."""
    faces = {f: {"uv": uv(col), "texture": "#p"} for f in ("north", "south", "east", "west", "up", "down")}
    if top:
        faces["up"] = {"uv": uv(top), "texture": "#p"}
    e = {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}
    if rot:
        e["rotation"] = {"axis": rot[0], "angle": rot[1], "origin": rot[2]}
    return e


def shift(elems, dx=0, dy=0, dz=0):
    out = []
    for e in elems:
        e = json.loads(json.dumps(e))
        e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2] + dz]
        e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2] + dz]
        if "rotation" in e:
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [o[0] + dx, o[1] + dy, o[2] + dz]
        out.append(e)
    return out


def bounds(elems):
    lo = [min(e["from"][i] for e in elems) for i in range(3)]
    hi = [max(e["to"][i] for e in elems) for i in range(3)]
    return lo, hi


def model(name, elems, icon=True):
    """Writes models/item/<name>.json and the item definition kitchen:<name>."""
    lo, hi = bounds(elems)
    size = max(hi[i] - lo[i] for i in range(3))
    s = round(min(1.4, 0.625 * 16 / max(size, 1)), 3)
    cy = (lo[1] + hi[1]) / 2
    cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2
    ty = round((8 - cy) * s * 0.9, 2)
    m = {"textures": {"p": "kitchen:item/palette", "particle": "kitchen:item/palette"}, "elements": elems}
    if icon:
        m["display"] = {
            "gui": {"rotation": [30, 225, 0], "translation": [round((8 - cx) * s * 0.3, 2), ty, 0], "scale": [s, s, s]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [s * 0.5] * 3},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, ty * 0.6, 0], "scale": [s * 0.8] * 3},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [s * 0.45] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, ty * 0.3, 0], "scale": [s * 0.5] * 3},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, ty * 0.3, 0], "scale": [s * 0.5] * 3},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [s] * 3},
        }
    write(f"assets/kitchen/models/item/{name}.json", m)
    write(f"assets/kitchen/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"kitchen:item/{name}"}})

# ------------------------------------------------------------------ shapes (8,8 = block centre; y = bottom)

def pile(col, col2=None, n=7, seed=1, y=0):
    rnd = random.Random(seed)
    out = []
    for i in range(n):
        x, z = 5 + rnd.random() * 5, 5 + rnd.random() * 5
        h = 1 + (i % 2)
        out.append(box(x, y, z, x + 1.4, y + h, z + 1.1, col2 if col2 and i % 3 == 0 else col))
    out.append(box(6, y, 6, 10, y + 1, 10, col))
    return out


def disc(r, y1, y2, col, top=None, cx=8, cz=8):
    """Rough cylinder from two crossed boxes."""
    return [box(cx - r, y1, cz - r * 0.6, cx + r, y2, cz + r * 0.6, col, top=top),
            box(cx - r * 0.6, y1, cz - r, cx + r * 0.6, y2, cz + r, col, top=top)]


def bowl(col_out="porcelain", col_in=None, y=0, r=4.0, h=3.5, rim="indigo"):
    e = [box(8 - r + 1, y, 8 - r + 1, 8 + r - 1, y + 1, 8 + r - 1, col_out)]
    e += [box(8 - r, y + 1, 8 - r, 8 + r, y + h, 8 - r + 0.8, col_out, top=rim),
          box(8 - r, y + 1, 8 + r - 0.8, 8 + r, y + h, 8 + r, col_out, top=rim),
          box(8 - r, y + 1, 8 - r + 0.8, 8 - r + 0.8, y + h, 8 + r - 0.8, col_out, top=rim),
          box(8 + r - 0.8, y + 1, 8 - r + 0.8, 8 + r, y + h, 8 + r - 0.8, col_out, top=rim)]
    if col_in:
        e.append(box(8 - r + 0.8, y + 1, 8 - r + 0.8, 8 + r - 0.8, y + h - 0.6, 8 + r - 0.8, col_in))
    return e


def bottle(liquid, cap="cork"):
    return [box(6.5, 0, 6.5, 9.5, 5, 9.5, liquid), box(6.2, 0, 6.2, 9.8, 6.2, 9.8, "glass"),
            box(7.2, 6.2, 7.2, 8.8, 8.5, 8.8, "glass"), box(7, 8.5, 7, 9, 9.5, 9, cap)]


def nigiri(fish, fat=None, y=0):
    e = [box(6.5, y, 7, 9.5, y + 1.6, 9, "rice", top="rice_shade"), box(6.1, y + 1.6, 6.8, 9.9, y + 2.3, 9.2, fish)]
    if fat:
        e += [box(6.9, y + 2.3, 6.8, 7.3, y + 2.31, 9.2, fat), box(8.3, y + 2.3, 6.8, 8.7, y + 2.31, 9.2, fat)]
    return e


def maki(y=0):
    return [box(6.8, y, 6.8, 9.2, y + 2, 9.2, "nori"), box(7.1, y + 2, 7.1, 8.9, y + 2.05, 8.9, "rice"),
            box(7.7, y + 2.05, 7.7, 8.3, y + 2.1, 8.3, "salmon")]


def onigiri():
    return [box(5.5, 0, 7, 10.5, 2, 9, "rice"), box(6.2, 2, 7, 9.8, 3.5, 9, "rice"), box(7, 3.5, 7, 9, 4.6, 9, "rice"),
            box(6.6, 0, 6.8, 9.4, 1.8, 9.2, "nori")]


def dango(y=0, rot=None):
    e = [box(7.6, y, 7.6, 8.4, y + 9, 8.4, "bamboo")]
    for i, c in enumerate(("dango_green", "mochi", "dango_pink")):
        e.append(box(6.8, y + 1.2 + i * 2.4, 6.8, 9.2, y + 3.4 + i * 2.4, 9.2, c))
    return e


def knife(blade, edge="iron"):
    r = ("y", 45, [8, 0, 8])
    return [box(7.5, 0, 2, 8.5, 1, 7, "handle", rot=r), box(7.6, 0.1, 7, 8.4, 0.9, 7.6, "iron_dark", rot=r),
            box(7.7, 0, 7.6, 8.3, 2.6, 14, blade, rot=r), box(7.75, 0, 7.6, 8.25, 0.4, 14, edge, rot=r)]


def plate(y=0):
    return [box(4, y, 4, 12, y + 0.6, 12, "porcelain"), box(3.4, y + 0.4, 3.4, 12.6, y + 1, 12.6, "porcelain", top="porcelain"),
            box(3.4, y + 1, 3.4, 12.6, y + 1.05, 4, "indigo"), box(3.4, y + 1, 12, 12.6, y + 1.05, 12.6, "indigo")]


def crate(top, top2=None):
    e = [box(0, 0, 0, 16, 14, 16, "wood"), box(-0.01, 2, -0.01, 16.01, 4, 16.01, "wood_dark"),
         box(-0.01, 10, -0.01, 16.01, 12, 16.01, "wood_dark"), box(1, 14, 1, 15, 16, 15, top)]
    if top2:
        rnd = random.Random(top)
        for i in range(5):
            x, z = 2 + rnd.random() * 10, 2 + rnd.random() * 10
            e.append(box(x, 15.5, z, x + 2.5, 16.4, z + 2.5, top2))
    return e

# ------------------------------------------------------------------ items

def items():
    M = {}
    M["rice"] = pile("rice", "rice_shade", 9, 1)
    M["soybeans"] = pile("potato", "straw", 8, 2)
    M["napa_cabbage_seeds"] = pile("seed_dark", "brown", 7, 3)
    M["daikon_seeds"] = pile("brown", "seed_dark", 7, 4)
    M["tea_seeds"] = pile("seed_dark", "wood_dark", 6, 5)
    M["napa_cabbage"] = [box(5, 0, 5, 11, 9, 11, "cabbage", top="cabbage_light"), box(4.5, 0, 6, 5, 7, 10, "leaf"),
                         box(11, 0, 6, 11.5, 7, 10, "leaf"), box(6, 0, 4.5, 10, 7, 5, "leaf"), box(6, 0, 11, 10, 7, 11.5, "leaf_dark")]
    M["green_onion"] = [box(7.5, 0, 6, 8.5, 1, 10, "negi_white", rot=("y", 45, [8, 0, 8])),
                        box(6, 0, 7.4, 8, 1.2, 8.6, "negi_white"), box(8, 0, 7.5, 13, 1, 8.5, "negi"),
                        box(3, 0, 7.6, 6, 0.8, 8.4, "seed_dark")]
    M["daikon"] = [box(3, 0, 7, 11, 2.5, 9.5, "daikon"), box(11, 0, 7.3, 13, 2.2, 9.2, "daikon"),
                   box(13, 0, 7, 15, 2, 9.5, "leaf"), box(14, 0, 6, 16, 1.5, 10.5, "leaf_dark")]
    M["tea_leaves"] = [box(5, 0, 6, 9, 0.6, 9, "tea"), box(8, 0.4, 7, 11, 1, 10, "leaf"), box(6, 0.8, 8, 9, 1.4, 11, "tea")]
    M["corn"] = [box(7, 0, 7, 9, 1, 9, "wood_dark")] + [box(6.5, 1 + i * 1.2, 6.2, 9.5, 2 + i * 1.2, 9.8, "corn" if i % 2 else "corn_dark") for i in range(4)] + [box(6.8, 5.6, 6.5, 9.2, 6.2, 9.5, "corn"), box(5, 1, 8, 6.5, 5.5, 8.6, "leaf")]
    M["cornmeal"] = pile("corn", "corn_dark", 9, 8)
    M["tomato"] = [box(5.6, 0, 5.6, 10.4, 4.4, 10.4, "tomato"), box(6.4, 0, 4.9, 9.6, 3.6, 5.6, "tomato"), box(4.9, 0.8, 6.4, 5.6, 3.6, 9.6, "tomato"), box(7.6, 4.4, 7.6, 8.4, 5.2, 8.4, "leaf_dark"), box(5.8, 4.9, 7.1, 10.2, 5.1, 8.9, "leaf")]
    M["chili"] = [box(7.6, 0.6, 7.4, 9, 5.2, 8.8, "chili"), box(7.4, 4.6, 7.2, 9.2, 5.4, 9, "chili"), box(8, 5.2, 7.8, 8.6, 6.4, 8.4, "leaf"), box(5, 0.4, 5.6, 6.2, 4.2, 6.8, "chili"), box(5.4, 4, 5.2, 6, 5, 6, "leaf")]
    M["sesame_seeds"] = pile("sesame", "sesame_dark", 11, 9)
    M["shiso_seeds"] = pile("shiso", "shiso_dark", 11, 10)
    M["shiso_leaves"] = [box(4, 0, 4, 12, 1.2, 12, "shiso"), box(5, 1.2, 5, 11, 1.6, 11, "shiso_green")]
    M["ginger"] = [box(5, 0, 6, 11, 2, 10, "ginger"), box(6, 2, 7, 10, 3, 9, "ginger_dark"), box(7, 0, 5, 9, 1, 6, "ginger")]
    M["wasabi_root"] = [box(6, 0, 6, 10, 3, 10, "wasabi"), box(7, 3, 7, 9, 4, 9, "wasabi_dark")]
    M["mizuna_seeds"] = pile("mizuna", "mizuna_dark", 11, 11)
    M["mizuna"] = [box(4, 0, 4, 12, 1.2, 12, "mizuna"), box(5, 1.2, 5, 11, 1.6, 11, "mizuna_dark")]
    M["kabocha"] = [box(5, 0, 5, 11, 6, 11, "kabocha"), box(4.5, 0, 6, 5, 5, 10, "kabocha_skin"), box(11, 0, 6, 11.5, 5, 10, "kabocha_skin")]
    M["bamboo_shoots"] = [box(6, 0, 6, 10, 4, 10, "bamboo_shoot"), box(7, 4, 7, 9, 5, 9, "bamboo_dark")]
    M["eggplant"] = [box(5, 0, 5, 11, 5, 11, "eggplant"), box(6, 0, 4.5, 10, 4, 5, "eggplant_light"), box(5.5, 0, 10.5, 10.5, 4, 11, "eggplant_dark")]
    M["cabbage_leaf"] = [box(4, 0, 5, 12, 0.6, 11, "cabbage_light"), box(5, 0.6, 6, 11, 0.8, 10, "leaf")]
    M["daikon_slices"] = disc(2.2, 0, 0.8, "daikon", cx=6, cz=8) + disc(2.2, 0.8, 1.6, "daikon", cx=8, cz=8) + disc(2.2, 0, 0.8, "daikon", cx=10, cz=8)
    M["chopped_green_onion"] = pile("negi", "negi_white", 10, 6)
    M["salmon_sashimi"] = [box(5, 0, 6.5, 11, 1.5, 9.5, "salmon"), box(6, 1.5, 6.5, 6.5, 1.51, 9.5, "salmon_fat"),
                           box(8, 1.5, 6.5, 8.5, 1.51, 9.5, "salmon_fat"), box(10, 1.5, 6.5, 10.5, 1.51, 9.5, "salmon_fat")]
    M["tuna_sashimi"] = [box(5, 0, 6.5, 11, 1.6, 9.5, "tuna")]
    M["cod_slice"] = [box(5, 0, 6.5, 11, 1.5, 9.5, "cod")]
    M["fugu_sashimi"] = [box(3, 0, 6, 13, 0.4, 10, "porcelain")] + [box(4 + i * 1.6, 0.4, 6.5, 5.4 + i * 1.6, 0.7, 9.5, "cod") for i in range(5)]
    M["beef_slices"] = [box(4, 0, 6, 12, 0.6, 10, "beef"), box(5, 0.6, 6.5, 11, 1.1, 9.5, "beef")]
    M["pork_slices"] = [box(4, 0, 6, 12, 0.7, 10, "pork"), box(4, 0, 9.4, 12, 0.75, 10, "pork_fat")]
    M["chicken_cuts"] = pile("chicken", "pork_fat", 6, 7)
    M["kabocha_slices"] = [box(4, 0, 7, 12, 1.5, 9, "kabocha"), box(4, 0, 9, 12, 1.5, 9.6, "kabocha_skin"),
                           box(5, 1.5, 7, 11, 3, 9, "kabocha"), box(5, 1.5, 9, 11, 3, 9.6, "kabocha_skin")]
    M["nori"] = [box(4, 0, 4, 12, 0.4, 12, "nori")]
    M["tree_bark"] = [box(4, 0, 6, 12, 1, 10, "wood_dark"), box(4, 1, 6, 12, 2, 6.8, "wood_dark"), box(4, 1, 9.2, 12, 2, 10, "wood_dark")]
    M["straw"] = [box(3, 0, 6.5 + i * 0.6, 13, 0.6, 7.1 + i * 0.6, "straw" if i % 2 else "straw_dark") for i in range(5)] + [box(7.5, 0, 6.2, 8.5, 0.9, 9.8, "straw_dark")]
    M["steamed_rice"] = [box(5, 0, 5, 11, 2, 11, "rice"), box(6, 2, 6, 10, 3.2, 10, "rice", top="rice_shade")]
    M["edamame"] = [box(4 + i * 1.5, 0, 6 + (i % 2), 5.2 + i * 1.5, 1.2, 10 + (i % 2), "dango_green") for i in range(6)]
    M["tofu"] = [box(5, 0, 5, 11, 5, 11, "tofu")]
    M["miso"] = [box(5, 0, 5, 11, 4, 11, "wood", top="miso")]
    M["soy_sauce"] = bottle("soy", "cap_red")
    M["dashi"] = bottle("dashi")
    M["raw_noodles"] = [box(4, 0, 6.5 + i * 0.6, 12, 0.6 + (i % 2) * 0.3, 6.9 + i * 0.6, "noodle") for i in range(6)] + [box(7.4, 0, 6.2, 8.6, 1.2, 10, "nori")]
    M["mochi"] = [box(5.5, 0, 5.5, 10.5, 2.5, 10.5, "mochi"), box(6.3, 2.5, 6.3, 9.7, 3.4, 9.7, "mochi")]
    M["tamagoyaki"] = [box(4, 0, 6, 12, 3.5, 10, "egg"), box(4, 1.1, 5.99, 12, 1.3, 10.01, "yolk"), box(4, 2.3, 5.99, 12, 2.5, 10.01, "yolk")]
    M["takuan"] = disc(2, 0, 0.8, "takuan", cx=6.5) + disc(2, 0.8, 1.6, "takuan", cx=9.5)
    M["tempura"] = [box(4, 0, 6, 9, 2.5, 10, "tempura"), box(8, 0, 6.5, 12, 2, 9.5, "tempura"), box(5, 2.5, 7, 8, 3.2, 9, "gold")]
    M["onigiri"] = onigiri()
    M["salmon_nigiri"] = nigiri("salmon", "salmon_fat")
    M["tuna_nigiri"] = nigiri("tuna")
    M["maki_roll"] = maki()
    M["dango"] = dango()
    M["miso_soup"] = bowl("lacquer", "miso_soup", rim="black") + [box(6, 3, 6, 7, 3.6, 7, "tofu"), box(9, 3, 8, 10, 3.6, 9, "tofu"), box(7, 2.95, 9, 9, 3.05, 10, "negi")]
    M["ramen"] = ramen_bowl() + ramen_pork() + ramen_egg() + ramen_negi() + ramen_noodles()
    M["gyudon"] = bowl("porcelain", "rice") + [box(5, 3, 5, 11, 3.6, 11, "beef"), box(7, 3.6, 7, 9, 3.8, 9, "negi")]
    M["oyakodon"] = bowl("porcelain", "rice") + [box(5, 3, 5, 11, 3.5, 11, "egg"), box(6, 3.5, 6, 8, 4, 8, "chicken"), box(8.5, 3.5, 8, 10, 4, 10, "chicken"), box(7, 3.5, 9, 8, 3.7, 10, "negi")]
    M["oden"] = bowl("clay", "dashi", rim="clay_dark") + [box(5.5, 2.5, 5.5, 8, 4, 8, "daikon"), box(8.5, 2.5, 8, 10.5, 4.2, 10, "egg_white"), box(6, 2.5, 8.8, 8, 3.8, 10.5, "tofu")]
    M["nabe"] = bowl("clay_dark", "dashi", rim="black") + [box(5, 2.8, 5, 8, 3.6, 8, "cabbage"), box(8.5, 2.8, 5.5, 10.5, 3.8, 7.5, "tofu"), box(5.5, 2.8, 8.5, 7.5, 3.6, 10.5, "brown"), box(8.5, 2.8, 8.5, 10.5, 3.2, 10.5, "negi")]
    M["green_tea"] = bottle("tea_drink")
    M["plate"] = plate()
    M["flint_knife"] = knife("flint", "stone")
    M["iron_knife"] = knife("iron", "white")
    M["diamond_knife"] = knife("diamond", "white")
    M["netherite_knife"] = knife("netherite", "iron_dark")
    return M

# ------------------------------------------------------------------ meal pieces (display only; sit on the plate/bowl)

def ramen_bowl():
    return bowl("black", None, r=5, h=4.5, rim="lacquer") + [box(3.8, 1, 3.8, 12.2, 3.6, 12.2, "broth")]


def ramen_pork():
    return [box(5.2, 3.6, 5.8, 8.6, 4.3, 9, "pork"), box(5.2, 3.6, 8.6, 8.6, 4.35, 9, "pork_fat")]


def ramen_egg():
    return [box(8.6, 3.6, 5.6, 10.6, 4.6, 7.6, "egg_white"), box(9.1, 4.6, 6.1, 10.1, 4.65, 7.1, "yolk")]


def ramen_negi():
    return [box(8.2, 3.6, 8.6, 10.6, 3.9, 10.4, "negi"), box(5.6, 3.6, 9.6, 7, 3.8, 10.8, "nori")]


def ramen_noodles():
    return [box(4.5, 3.6, 4.5 + i * 1.2, 11.5, 3.75, 5.1 + i * 1.2, "noodle") for i in range(6)]


def meal_pieces():
    P = {}
    P["meal_plate"] = plate()
    P["meal_salmon_nigiri"] = shift(nigiri("salmon", "salmon_fat"), dy=1, dz=0)
    P["meal_tuna_nigiri"] = shift(nigiri("tuna"), dy=1)
    P["meal_maki"] = shift(maki(), dy=1)
    P["meal_ramen_bowl"] = ramen_bowl()
    P["meal_ramen_pork"] = shift(ramen_pork(), dx=8 - 6.9, dz=8 - 7.4)
    P["meal_ramen_egg"] = shift(ramen_egg(), dx=8 - 9.6, dz=8 - 6.6)
    P["meal_ramen_negi"] = shift(ramen_negi(), dx=8 - 8.4, dz=8 - 9.5)
    P["meal_ramen_noodles"] = ramen_noodles()
    box_e = [box(2, 0, 3, 14, 1, 13, "lacquer"), box(2, 1, 3, 14, 3, 3.8, "lacquer", top="black"), box(2, 1, 12.2, 14, 3, 13, "lacquer", top="black"),
             box(2, 1, 3.8, 2.8, 3, 12.2, "lacquer", top="black"), box(13.2, 1, 3.8, 14, 3, 12.2, "lacquer", top="black"),
             box(7.6, 1, 3.8, 8.0, 2.6, 12.2, "black"), box(8, 1, 7.8, 13.2, 2.6, 8.2, "black")]
    P["meal_bento_box"] = box_e
    P["meal_bento_rice"] = [box(5.6, 1, 4.8, 10.4, 2.4, 11.2, "rice"), box(7.6, 2.4, 7.6, 8.4, 2.9, 8.4, "cap_red")]
    P["meal_bento_tamagoyaki"] = shift([box(6.4, 1, 6.6, 9.6, 2.8, 9.4, "egg"), box(6.4, 1.8, 6.59, 9.6, 1.95, 9.41, "yolk")], 0, 0, 0)
    P["meal_bento_tempura"] = [box(6.2, 1, 6.6, 9.8, 2.6, 9.4, "tempura"), box(6.6, 2.6, 7, 9.4, 3, 9, "gold")]
    P["meal_bento_takuan"] = disc(1.3, 2.4, 2.9, "takuan")
    P["meal_dango"] = [box(7.6, 1, 3.5, 8.4, 1.6, 12.5, "bamboo")] + [box(6.8, 0.9, 4.5 + i * 2.4, 9.2, 3.1, 6.7 + i * 2.4, c)
                                                                      for i, c in enumerate(("dango_green", "mochi", "dango_pink"))]
    P["meal_tea_tray"] = [box(1, 0, 2, 15, 0.8, 14, "lacquer"), box(1, 0.8, 2, 15, 1.4, 2.6, "black"), box(1, 0.8, 13.4, 15, 1.4, 14, "black"),
                          box(6, 0.8, 6, 10, 4.8, 10, "clay"), box(6.5, 4.8, 6.5, 9.5, 5.4, 9.5, "clay_dark"), box(7.6, 5.4, 7.6, 8.4, 6, 8.4, "clay_dark"),
                          box(10, 3, 7.6, 12, 3.8, 8.4, "clay")]
    P["meal_tea_cup"] = [box(6.8, 0.8, 6.8, 9.2, 3.6, 9.2, "porcelain"), box(7.2, 3.2, 7.2, 8.8, 3.4, 8.8, "tea_drink")]
    P["meal_chopsticks"] = [box(7.2, 1.2, 2.5, 7.8, 1.8, 13.5, "lacquer"), box(8.2, 1.2, 2.5, 8.8, 1.8, 13.5, "lacquer"),
                            box(6.8, 0, 3, 9.2, 1.2, 4.2, "porcelain")]
    return P

# ------------------------------------------------------------------ props (full block, front = north)

def props():
    M = {}
    M["cutting_board"] = [box(1, 0, 3, 15, 2, 13, "wood_light"), box(1, 0, 3, 15, 0.6, 13, "wood"), box(13, 0.5, 7, 15.6, 1.5, 9, "wood")]
    pot = [box(3, 0, 3, 13, 1, 13, "clay_dark"), box(2, 1, 2, 14, 8, 14, "clay"), box(1.6, 5, 1.6, 14.4, 6, 14.4, "clay_dark"),
           box(3, 8, 3, 13, 9.5, 13, "clay_dark"), box(4, 9.5, 4, 12, 10.5, 12, "clay"), box(7, 10.5, 7, 9, 11.5, 9, "clay_dark"),
           box(0.5, 6, 7, 1.6, 7, 9, "clay_dark"), box(14.4, 6, 7, 15.5, 7, 9, "clay_dark")]
    M["cooking_pot"] = pot
    M["steam_pot"] = [box(3, 0, 3, 13, 1, 13, "iron_dark"), box(2, 1, 2, 14, 8, 14, "iron"), box(1.6, 5, 1.6, 14.4, 6, 14.4, "iron_dark"),
                      box(3, 8, 3, 13, 9.5, 13, "iron_dark"), box(4, 9.5, 4, 12, 10.5, 12, "iron"), box(7, 10.5, 7, 9, 11.5, 9, "iron_dark"),
                      box(0.5, 6, 7, 1.6, 7, 9, "iron_dark"), box(14.4, 6, 7, 15.5, 7, 9, "iron_dark"),
                      box(6, 11.5, 6, 10, 13, 10, "steam")]
    stove = [box(0, 0, 0, 16, 14, 16, "brick"), box(0, 14, 0, 16, 16, 16, "stone"), box(4, 2, -0.01, 12, 8, 0.5, "black"),
             box(2, 13.99, 2, 14, 16.01, 14, "iron_dark"), box(0, 0, 0, 16, 1, 16, "stone")]
    M["kamado_stove"] = stove
    M["kamado_stove_lit"] = stove + [box(4.5, 2, -0.02, 11.5, 6, 0.6, "fire"), box(6, 2, -0.03, 10, 4.5, 0.7, "fire_core")]
    tansu = [box(0, 0, 0, 16, 16, 16, "wood"), box(0, 0, -0.01, 16, 1, 16.01, "wood_dark")]
    for y in (1.5, 6.5, 11.5):
        tansu += [box(1, y, -0.02, 15, y + 4, 0.3, "wood_light"), box(6.5, y + 1.5, -0.4, 9.5, y + 2.5, 0, "black")]
    tansu += [box(-0.02, 0, -0.02, 1, 16, 1, "black"), box(15, 0, -0.02, 16.02, 16, 1, "black")]
    M["tansu_cabinet"] = tansu
    basket = [box(1, 0, 1, 15, 1, 15, "bamboo_dark")]
    for i, (a, b) in enumerate(((1, 2), (14, 15))):
        basket += [box(a, 1, 1, b, 10, 15, "bamboo"), box(1, 1, a, 15, 10, b, "bamboo")]
    basket += [box(0.8, 4, 0.8, 15.2, 5, 15.2, "bamboo_dark"), box(0.8, 9, 0.8, 15.2, 10, 15.2, "bamboo_dark")]
    M["bamboo_basket"] = basket
    M["rice_sack"] = [box(1, 0, 2, 15, 13, 14, "straw"), box(3, 13, 4, 13, 15, 12, "straw_dark"), box(1, 6, 1.99, 15, 8, 14.01, "indigo")]
    M["soybean_crate"] = crate("potato", "straw")
    M["napa_cabbage_crate"] = crate("cabbage", "leaf")
    M["daikon_crate"] = crate("daikon", "leaf")
    M["green_onion_crate"] = crate("negi", "negi_white")
    M["tea_crate"] = crate("tea", "leaf")
    M["carrot_crate"] = crate("carrot", "leaf")
    M["potato_crate"] = crate("potato", "brown")
    M["beetroot_crate"] = crate("beet", "leaf_dark")
    M["straw_bale"] = [box(0, 0, 0, 16, 16, 16, "straw", top="straw_dark"), box(-0.01, 0, 3, 16.01, 16, 4, "brown"), box(-0.01, 0, 12, 16.01, 16, 13, "brown")]
    M["rich_soil"] = [box(0, 0, 0, 16, 16, 16, "soil_rich", top="soil")]
    M["organic_compost"] = [box(0, 0, 0, 16, 16, 16, "compost", top="straw_dark"), box(3, 16, 4, 7, 16.3, 6, "straw"), box(9, 16, 10, 12, 16.3, 11, "leaf_dark")]
    M["tatami_mat"] = [box(0, 0, 0, 16, 1, 16, "tatami"), box(0, 0, -0.01, 1.2, 1.01, 16.01, "tatami_edge"), box(14.8, 0, -0.01, 16, 1.01, 16.01, "tatami_edge")]
    M["zabuton"] = [box(1, 0, 1, 15, 2.5, 15, "zabuton"), box(7.4, 2.5, 7.4, 8.6, 2.8, 8.6, "gold")]
    M["miso_vat"] = [box(2, 0, 2, 14, 2, 14, "wood_dark"), box(2, 2, 2, 14, 12, 4, "wood"), box(2, 2, 12, 14, 12, 14, "wood"),
                     box(2, 2, 4, 4, 12, 12, "wood"), box(12, 2, 4, 14, 12, 12, "wood"),
                     box(1, 12, 1, 15, 14, 3, "wood_dark"), box(1, 12, 13, 15, 14, 15, "wood_dark"),
                     box(13, 12, 3, 15, 14, 13, "wood_dark"), box(1, 12, 3, 3, 14, 13, "wood_dark")]
    M["tsukemono_pot"] = [box(3, 0, 3, 13, 2, 13, "clay_dark"), box(3, 2, 3, 13, 10, 5, "clay"),
                          box(3, 2, 11, 13, 10, 13, "clay"), box(3, 2, 5, 5, 10, 11, "clay"),
                          box(11, 2, 5, 13, 10, 11, "clay"), box(2, 10, 2, 14, 12, 14, "clay_dark"),
                          box(4, 12, 4, 12, 13, 12, "clay"), box(7, 13, 7, 9, 15, 9, "wood_dark")]
    M["smoker"] = [box(0, 0, 0, 16, 2, 16, "smoker_iron"), box(1, 2, 1, 15, 14, 15, "smoker"),
                   box(0, 14, 0, 16, 16, 16, "smoker_dark"), box(2, 2, 0.5, 14, 8, 1, "smoker_iron"),
                   box(2, 2, 15, 14, 8, 15.5, "smoker_iron")]
    M["drying_rack"] = [box(1, 0, 1, 15, 1, 15, "rack"), box(1, 1, 1, 15, 2, 2, "rack_dark"),
                        box(1, 1, 14, 15, 2, 15, "rack_dark"), box(1, 2, 1, 2, 8, 15, "rack"),
                        box(14, 2, 1, 15, 8, 15, "rack"), box(1, 8, 1, 15, 9, 15, "rack_dark")]
    M["smoked_salmon"] = [box(4, 0, 5, 12, 1.5, 11, "smoked"), box(5, 1.5, 6, 11, 1.8, 10, "smoked_dark")]
    M["smoked_beef"] = [box(4, 0, 5, 12, 2, 11, "smoked"), box(5, 2, 6, 11, 2.4, 10, "smoked_dark")]
    M["dried_chili"] = [box(6, 0, 6, 10, 1.5, 10, "chili"), box(7, 1.5, 7, 9, 2, 9, "chili")]
    M["dried_herbs"] = [box(5, 0, 5, 11, 0.8, 11, "leaf"), box(6, 0.8, 6, 10, 1.2, 10, "leaf_dark")]
    M["sundried_tomato"] = [box(5, 0, 5, 11, 1.5, 11, "tomato"), box(6, 1.5, 6, 10, 1.8, 10, "tomato")]
    M["clay_oven"] = [box(0, 0, 0, 16, 12, 16, "clay_oven"), box(0, 12, 0, 16, 14, 16, "clay_oven_dark"),
                      box(2, 2, -0.01, 14, 10, 0.5, "black"), box(0, 0, 0, 16, 1, 16, "clay_oven_dark")]
    M["steamer"] = [box(2, 0, 2, 14, 10, 14, "steamer"), box(1, 10, 1, 15, 12, 15, "steamer_dark"),
                    box(4, 12, 4, 12, 13, 12, "steamer_lid"), box(7, 13, 7, 9, 14.5, 9, "steamer_lid")]
    M["rice_bread"] = [box(4, 0, 4, 12, 6, 12, "bread"), box(5, 6, 5, 11, 7, 11, "bread_dark")]
    M["melonpan"] = [box(4, 0, 4, 12, 5, 12, "melonpan"), box(5, 5, 5, 11, 6, 11, "melonpan_dark")]
    M["castella"] = [box(3, 0, 3, 13, 8, 13, "castella"), box(4, 8, 4, 12, 9, 12, "castella_dark")]
    M["nikuman"] = [box(5, 0, 5, 11, 6, 11, "nikuman"), box(6, 6, 6, 10, 7, 10, "nikuman_dark")]
    M["anpan"] = [box(5, 0, 5, 11, 5, 11, "anpan"), box(7, 5, 7, 9, 6, 9, "anpan_bean")]
    M["shiitake"] = [box(5, 0, 5, 11, 2, 11, "shiitake"), box(6, 2, 6, 10, 3, 10, "shiitake_light"), box(7, 3, 7, 9, 3.5, 9, "shiitake_dark")]
    M["enoki"] = [box(6, 0, 6, 10, 8, 10, "enoki"), box(7, 8, 7, 9, 9, 9, "enoki_dark")]
    M["shiitake_log"] = [box(2, 0, 2, 14, 4, 14, "log"), box(2, 4, 2, 14, 6, 14, "log_dark"),
                         box(4, 6, 4, 12, 7, 12, "shiitake"), box(5, 7, 5, 11, 7.5, 11, "shiitake_light")]
    M["enoki_bundle"] = [box(4, 0, 4, 12, 6, 12, "enoki_bundle"), box(5, 6, 5, 11, 7, 11, "enoki"),
                         box(6, 7, 6, 10, 8, 10, "enoki_dark")]
    M["noodle_maker"] = [box(0, 0, 0, 16, 8, 16, "noodle_maker"), box(1, 8, 1, 15, 10, 15, "noodle_maker_dark"),
                         box(4, 10, 4, 12, 12, 12, "wood_dark")]
    M["udon"] = [box(4, 0, 5, 12, 1, 11, "udon"), box(5, 1, 6, 11, 1.4, 10, "udon_dark")]
    M["soba"] = [box(4, 0, 5, 12, 1, 11, "soba"), box(5, 1, 6, 11, 1.3, 10, "soba_dark")]
    M["ramen_noodles"] = [box(4, 0, 5, 12, 1, 11, "ramen_noodles"), box(5, 1, 6, 11, 1.3, 10, "ramen_noodles_dark")]
    return M

# ------------------------------------------------------------------ meal items (icons = the whole meal)

def meal_icons(P):
    M = {}
    pieces = {"sushi_platter": ("meal_plate", [("meal_salmon_nigiri", -0.18, -0.1), ("meal_salmon_nigiri", -0.18, 0.1), ("meal_tuna_nigiri", 0, -0.1),
                                              ("meal_tuna_nigiri", 0, 0.1), ("meal_maki", 0.18, -0.1), ("meal_maki", 0.18, 0.1)]),
              "bento_box": ("meal_bento_box", [("meal_bento_rice", -0.12, 0), ("meal_bento_tamagoyaki", 0.1, -0.08),
                                               ("meal_bento_tempura", 0.1, 0.08), ("meal_bento_takuan", -0.12, 0.12)]),
              "dango_plate": ("meal_plate", [("meal_dango", 0, -0.12), ("meal_dango", 0, 0), ("meal_dango", 0, 0.12)]),
              "tea_set": ("meal_tea_tray", [("meal_tea_cup", -0.18, -0.15), ("meal_tea_cup", 0.18, -0.15), ("meal_tea_cup", -0.18, 0.15), ("meal_tea_cup", 0.18, 0.15)])}
    for name, (base, parts) in pieces.items():
        e = list(P[base])
        for m, dx, dz in parts:
            e += shift(P[m], dx * 16, 0, dz * 16)
        M[name] = e
    return M

# ------------------------------------------------------------------ crop sprites

def blank():
    return [[(0, 0, 0, 0)] * 16 for _ in range(16)]


def put(px, x, y, c):
    """y = height from the bottom (0 = bottom row)."""
    if 0 <= x < 16 and 0 <= y < 16:
        px[15 - y][x] = c + (255,) if len(c) == 3 else c


def stem(px, x, h, col, lean=0):
    for y in range(h):
        put(px, x + (lean * y) // max(h, 1), y, col)


def leafy(px, rnd, h, col, col2, n=5, w=3):
    """Stems with small diagonal leaves along them (soybean, daikon tops)."""
    for i in range(n):
        x = 2 + (i * 12) // max(n - 1, 1) + rnd.randint(-1, 1)
        top = max(2, h - rnd.randint(0, max(1, h // 3)))
        lean = rnd.choice((-1, 0, 1))
        for y in range(top):
            sx = x + (lean * y) // max(top, 1)
            put(px, sx, y, col2 if i % 2 else col)
            if y >= 1 and (y + i) % 3 == 0:
                d = 1 if (y // 3 + i) % 2 else -1
                for k in range(1, w):
                    put(px, sx + d * k, y + k // 2, col if k % 2 else col2)
                    put(px, sx + d * k, y + k // 2 + 1, col2)
        put(px, x + (lean * top) // max(top, 1), top, col2)


def rosette(px, rnd, size, col, col2):
    """Broad leaves fanning out from the bottom centre (napa cabbage)."""
    for a in (-60, -30, 0, 30, 60):
        r = math.radians(a)
        for t in range(size):
            x = 8 + int(round(math.sin(r) * t))
            y = int(round(math.cos(r) * t * 0.9))
            for w in (-1, 0, 1):
                put(px, x + w, y, col if (t + w) % 3 else col2)


def sprite(crop, stage, wild=False):
    rnd = random.Random(sum(map(ord, crop)) * 31 + stage * 7 + (99 if wild else 0))
    px = blank()
    G, D = PALETTE["leaf"], PALETTE["leaf_dark"]
    h = [4, 7, 10, 13][min(stage, 3)]
    if wild:
        h = 9
    if crop == "rice":
        for i in range(7):
            x = 1 + i * 2
            hh = h - rnd.randint(0, 2)
            stem(px, x, hh, G if i % 2 else D, rnd.choice((-1, 0, 1)))
            if stage == 3 or wild:
                for k in range(4):
                    put(px, x + (1 if k > 1 else 0), hh - k, PALETTE["straw"] if k % 2 else PALETTE["gold"])
    elif crop == "soybean":
        leafy(px, rnd, h, G, D, 6, 3)
        if stage >= 2:
            for _ in range(5 if stage == 3 else 2):
                x, y = rnd.randint(2, 13), rnd.randint(2, max(3, h - 2))
                c = PALETTE["straw"] if stage == 3 else PALETTE["dango_green"]
                put(px, x, y, c); put(px, x, y + 1, c)
    elif crop == "napa_cabbage":
        rosette(px, rnd, [3, 5, 7, 8][min(stage, 3)] if not wild else 6, G, D)
        if stage >= 2 or wild:
            rw, rh = (2, 4) if stage == 2 or wild else (3, 8)
            for y in range(0, rh):
                half = rw if y < rh - 2 else rw - 1
                for x in range(8 - half, 8 + half + 1):
                    edge = x in (8 - half, 8 + half)
                    put(px, x, y, D if edge else (PALETTE["cabbage_light"] if (x + y) % 3 else PALETTE["cabbage"]))
    elif crop == "green_onion":
        for i in range(6):
            x = 2 + i * 2 + rnd.randint(0, 1)
            hh = h - rnd.randint(0, 2)
            stem(px, x, hh, PALETTE["negi"])
            for y in range(min(3, hh)):
                put(px, x, y, PALETTE["negi_white"])
    elif crop == "daikon":
        leafy(px, rnd, h, G, D, 7, 3)
        if stage == 3 or wild:
            for y in range(0, 3):
                for x in range(6, 10):
                    put(px, x, y, PALETTE["daikon"])
    elif crop == "tea":
        for y in range(1, h):
            wd = min(6, 2 + y // 2)
            for x in range(8 - wd, 8 + wd):
                if rnd.random() < 0.7:
                    put(px, x, y, PALETTE["tea"] if rnd.random() < 0.6 else D)
        stem(px, 8, 2, PALETTE["wood_dark"])
        if stage == 3 or wild:
            for _ in range(7):
                put(px, rnd.randint(3, 12), rnd.randint(h - 4, h - 1), PALETTE["dango_green"])
            put(px, rnd.randint(4, 11), rnd.randint(3, h - 2), PALETTE["white"])
    elif crop == "corn":
        for i in range(5):
            x = 2 + i * 3
            hh = h + rnd.randint(-1, 1)
            stem(px, x, hh, G, 0)
            if stage >= 1:
                stem(px, x - 1 if rnd.random() < 0.5 else x + 1, max(2, hh - 3), D, rnd.choice((-1, 1)))
        if stage >= 2 or wild:
            for i in (1, 3):
                x = 2 + i * 3
                for y in range(max(0, h - 7), max(1, h - 2)):
                    put(px, x, y, PALETTE["corn"] if (x + y) % 2 else PALETTE["corn_dark"])
                    put(px, x + 1, y, PALETTE["corn_dark"] if (x + y) % 2 else PALETTE["corn"])
    elif crop == "tomato":
        leafy(px, rnd, h, G, D, 5, 4)
        if stage >= 2 or wild:
            n = 5 if stage == 3 else 2
            for _ in range(n):
                x, y = rnd.randint(3, 11), rnd.randint(0, max(1, h - 3))
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    put(px, x + dx, y + dy, PALETTE["tomato"])
            put(px, rnd.randint(3, 12), 2, PALETTE["chili"])
    elif crop == "chili":
        leafy(px, rnd, h, G, D, 5, 3)
        if stage >= 2 or wild:
            for _ in range(6 if stage == 3 else 3):
                x, y = rnd.randint(3, 12), rnd.randint(1, max(2, h - 1))
                for k in range(3):
                    put(px, x + k // 2, y - k, PALETTE["chili"] if k != 2 else D)
    elif crop == "sesame":
        for i in range(4):
            x = 4 + i * 3
            hh = h - rnd.randint(0, 1)
            stem(px, x, hh, G if i % 2 else D, 0)
            if stage >= 2 or wild:
                for y in range(max(1, hh - 5), hh):
                    put(px, x - 1, y, PALETTE["sesame"])
                    put(px, x + 1, y, PALETTE["sesame_dark"])
    elif crop == "shiso":
        leafy(px, rnd, h, PALETTE["shiso_green"], PALETTE["shiso_dark"], 6, 3)
        if stage >= 2 or wild:
            for _ in range(4 if stage == 3 else 2):
                x, y = rnd.randint(3, 12), rnd.randint(1, max(2, h - 2))
                for dx, dy in ((0, 0), (1, 0)):
                    put(px, x + dx, y + dy, PALETTE["shiso"])
    elif crop == "ginger":
        for i in range(3):
            x = 4 + i * 4
            for y in range(0, min(4, h)):
                put(px, x, y, PALETTE["ginger"] if (x + y) % 2 else PALETTE["ginger_dark"])
                put(px, x + 1, y, PALETTE["ginger_dark"] if (x + y) % 2 else PALETTE["ginger"])
        if stage >= 2 or wild:
            for _ in range(3):
                x, y = rnd.randint(2, 13), rnd.randint(0, 2)
                put(px, x, y, PALETTE["leaf"]); put(px, x, y + 1, PALETTE["leaf_dark"])
    elif crop == "wasabi":
        rosette(px, rnd, [4, 6, 8, 9][min(stage, 3)] if not wild else 7, PALETTE["wasabi"], PALETTE["wasabi_dark"])
        if stage >= 2 or wild:
            for _ in range(3):
                x, y = rnd.randint(3, 12), rnd.randint(0, max(1, h - 3))
                put(px, x, y, PALETTE["wasabi_dark"])
                put(px, x + 1, y, PALETTE["wasabi"])
    elif crop == "mizuna":
        leafy(px, rnd, h, PALETTE["mizuna"], PALETTE["mizuna_dark"], 7, 3)
        if stage >= 2 or wild:
            for _ in range(5 if stage == 3 else 2):
                x, y = rnd.randint(2, 13), rnd.randint(1, max(2, h - 2))
                put(px, x, y, PALETTE["mizuna"]); put(px, x + 1, y, PALETTE["mizuna_dark"])
    elif crop == "kabocha":
        for i in range(2):
            x = 4 + i * 6
            for y in range(0, min(5, h)):
                for dx in range(4):
                    put(px, x + dx, y, PALETTE["kabocha"] if (x + y + dx) % 2 else PALETTE["kabocha_dark"])
        if stage >= 2 or wild:
            for _ in range(2):
                x, y = rnd.randint(2, 12), rnd.randint(0, max(1, h - 4))
                put(px, x, y, PALETTE["kabocha_skin"]); put(px, x + 1, y + 1, PALETTE["kabocha_skin"])
    elif crop == "bamboo_shoots":
        for i in range(4):
            x = 3 + i * 3
            hh = h - rnd.randint(0, 1)
            for y in range(0, min(4, hh)):
                put(px, x, y, PALETTE["bamboo_shoot"])
                put(px, x + 1, y, PALETTE["bamboo_dark"])
            stem(px, x, hh, PALETTE["bamboo"], 0)
        if stage >= 2 or wild:
            for _ in range(4):
                x, y = rnd.randint(2, 13), rnd.randint(1, max(2, h - 2))
                put(px, x, y, PALETTE["leaf"]); put(px, x, y + 1, PALETTE["leaf_dark"])
    elif crop == "eggplant":
        leafy(px, rnd, h, PALETTE["leaf"], PALETTE["leaf_dark"], 5, 3)
        if stage >= 2 or wild:
            for _ in range(4 if stage == 3 else 2):
                x, y = rnd.randint(3, 12), rnd.randint(1, max(2, h - 2))
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    put(px, x + dx, y + dy, PALETTE["eggplant"] if (dx + dy) % 2 == 0 else PALETTE["eggplant_light"])
    return png(px)


CROPS = ["rice", "soybean", "napa_cabbage", "green_onion", "daikon", "tea", "corn", "tomato", "chili", "sesame", "shiso", "ginger", "wasabi", "mizuna", "kabocha", "bamboo_shoots", "eggplant"]  # CropType order

# ------------------------------------------------------------------ sounds

def add_sounds(root, namespace):
    """Copies sounds/out/<namespace> (made by sounds/make_sounds.py: sounds.json, .ogg files, subtitles) into the pack."""
    import shutil
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "sounds", "out", namespace)
    if not os.path.isdir(src):
        print("note: no sounds in", src, "- run sounds/make_sounds.py first (pack built without sounds)")
        return
    shutil.copytree(src, os.path.join(root, "assets", namespace), dirs_exist_ok=True)

# ------------------------------------------------------------------ main

def main():
    global ROOT
    out = sys.argv[1]
    ROOT = os.path.join(out, "Kitchen-pack")
    shutil.rmtree(ROOT, ignore_errors=True)   # fresh folder: no stale tripwire.json or removed models left in the zip
    write("pack.mcmeta", {"pack": {"description": "Kitchen (crops, food, props)", "pack_format": 64,
                                   "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}})
    write("assets/kitchen/textures/item/palette.png", palette_png())
    I = items()
    P = meal_pieces()
    R = props()
    for name, e in {**I, **P, **R, **meal_icons(P)}.items():
        model(name, e)
    for c, crop in enumerate(CROPS):
        for s in range(5):
            tex = f"{crop}_{'wild' if s == 4 else 'stage' + str(s)}"
            write(f"assets/kitchen/textures/block/{tex}.png", sprite(crop, min(s, 3), s == 4))
            write(f"assets/kitchen/models/block/{tex}.json", {"parent": "minecraft:block/crop", "textures": {"crop": f"kitchen:block/{tex}"}})
            # 26.2 resolves the ITEM_MODEL component through an items/ definition - without this
            # file every crop display renders as a magenta cube on the client.
            write(f"assets/kitchen/items/block/{tex}.json", {"model": {"type": "minecraft:model", "model": f"kitchen:block/{tex}"}})
    add_sounds(ROOT, "kitchen")
    # Java side checks: every KItem, prop and meal piece needs a model
    zpath = os.path.join(out, "Kitchen-pack.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(ROOT):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                z.write(full, os.path.relpath(full, ROOT).replace(os.sep, "/"))
    print("wrote", zpath, "with", len(I) + len(P) + len(R) + 4, "models")


if __name__ == "__main__":
    main()