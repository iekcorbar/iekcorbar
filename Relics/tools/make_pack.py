"""Relics resource pack: models for the lore props (own designs, palette texture) + their sounds.

Collection 1 - "Shoutlander's Shadow" (Halloween 2026): remains of Shoutlander's creatures and
of the First Settlement they destroyed, relics of the old world and of the Guild. Only the known
history of the lore is used (see the Relics plugin javadoc).

Models are block-space element models (0..16, y = 0 on the ground, front = -Z / north; wall
variants hang on a wall at Z = 16; ceiling variants hang from y = 16). Tall relics reach y = 32.
Every model is also an item model (item_model relics:<name>).

Usage: python make_pack.py <out folder>  -> <out>/Relics-pack/ + <out>/Relics-pack.zip
"""
import json, os, random, shutil, struct, sys, zipfile, zlib

ROOT = None
BASE = {
    "bone": (222, 214, 190), "bone_old": (176, 166, 138), "char": (34, 30, 28), "soot": (58, 52, 48), "ash": (150, 146, 140),
    "ember": (240, 110, 30), "rust": (122, 62, 38), "iron": (110, 112, 116), "brass": (176, 140, 60), "copper": (170, 100, 56),
    "stone": (118, 116, 112), "moss": (70, 96, 52), "dirt": (78, 58, 40), "glow_red": (255, 40, 30), "glow_purple": (190, 90, 255),
    "glyph": (120, 70, 190), "glass": (170, 200, 196), "fluid": (70, 110, 60), "flesh": (52, 30, 40), "vein": (130, 24, 34),
    "slime": (60, 80, 50), "wax": (230, 222, 200), "flame": (255, 196, 70), "flame_core": (255, 244, 170), "cloth": (120, 34, 30),
    "cloth_old": (88, 40, 34), "wood_char": (52, 40, 32), "wood_old": (100, 78, 56), "obsidian": (28, 20, 40), "lens_dark": (60, 14, 12),
    "paper": (200, 186, 150), "chain": (80, 82, 86),
    # Xmas Town
    "pine": (40, 96, 52), "snow": (240, 244, 250), "xred": (186, 30, 36), "xgreen": (30, 120, 60), "gold": (224, 180, 60),
    "bulb_red": (255, 70, 60), "bulb_yellow": (255, 220, 90), "bulb_blue": (80, 160, 255), "bulb_off": (70, 66, 60),
    "wood_warm": (150, 98, 58), "brick": (150, 70, 52), "ginger": (176, 108, 54), "icing": (250, 248, 240), "carrot": (240, 130, 30),
    "coal": (30, 28, 28), "cocoa": (96, 58, 36), "cider": (190, 110, 50), "amber": (255, 186, 90), "bamboo": (150, 170, 80),
    "straw": (210, 186, 100), "cream": (240, 226, 196), "mug": (200, 60, 50),
    # New Year
    "bronze": (150, 104, 52), "lantern_red": (210, 40, 36), "paper_white": (246, 242, 230), "confetti_pink": (240, 110, 170),
    "confetti_cyan": (70, 200, 220), "fizz": (240, 220, 140), "soba": (150, 120, 90), "dough": (196, 140, 70), "dial": (240, 232, 210),
    # Easter, St. Patrick's Day, Curiosities
    "egg_pink": (246, 170, 200), "egg_blue": (140, 200, 240), "egg_yellow": (250, 230, 120), "egg_lilac": (200, 170, 240),
    "wicker": (186, 140, 80), "chocolate": (100, 60, 36), "blossom": (255, 190, 210), "porcelain_w": (244, 244, 248),
    "shamrock": (60, 170, 70), "rainbow_r": (230, 50, 50), "rainbow_o": (245, 150, 40), "rainbow_y": (245, 225, 60),
    "rainbow_g": (70, 190, 80), "rainbow_b": (60, 120, 220), "rainbow_v": (140, 70, 200), "hat_green": (40, 120, 50),
    "torii": (200, 50, 36), "lacquer_black": (28, 26, 28), "cat_white": (248, 246, 240), "bonsai_pot": (90, 110, 130),
    "xblue": (40, 96, 196), "xpink": (240, 120, 172), "spark": (180, 220, 255), "coil": (200, 120, 60), "washi": (240, 230, 200), "umbrella_red": (200, 40, 50), "umbrella_blue": (40, 80, 160),
    "indigo": (40, 56, 112), "tokkuri": (232, 232, 238), "clay_pot": (148, 96, 66),
    # Valentine's
    "rose_red": (204, 44, 74), "rose_deep": (142, 24, 52), "blush": (246, 170, 190), "lace_white": (250, 238, 242),
    "rose_gold": (228, 160, 120), "rose_leaf": (52, 110, 66), "cream_pink": (252, 220, 216),
    "well_stone": (130, 126, 128), "water_dark": (36, 74, 108),
    # Winter Onsen
    "hinoki": (216, 178, 122), "onsen_water": (126, 196, 206), "snow_blue": (214, 228, 240),
}
PALETTE = {}
for k, (r, g, b) in BASE.items():
    PALETTE[k] = (r, g, b)
    PALETTE[k + "_d"] = tuple(int(c * 0.7) for c in (r, g, b))
    PALETTE[k + "_l"] = tuple(min(255, int(c * 1.2 + 10)) for c in (r, g, b))
NAMES = list(PALETTE)
assert len(NAMES) <= 1024  # 32 x 32 cells of 8 px in a 256 px texture


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def palette_png():
    rnd = random.Random(31)
    px = [[(0, 0, 0, 255)] * 256 for _ in range(256)]
    for i, k in enumerate(NAMES):
        r, g, b = PALETTE[k]
        cx, cy = (i % 32) * 8, (i // 32) * 8
        rough = 12 if k.split("_")[0] in ("stone", "rust", "char", "soot", "ash", "dirt", "bone", "wood", "obsidian") else 5
        for y in range(8):
            for x in range(8):
                d = rnd.randint(-rough, rough)
                px[cy + y][cx + x] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255)
    return png(px)


def uv(col):
    i = NAMES.index(col)
    u, v = (i % 32) * 0.5, (i // 32) * 0.5  # 256 px texture: one 8 px cell = 0.5 uv units
    return [u + 0.08, v + 0.08, u + 0.42, v + 0.42]


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, bytes):
        open(path, "wb").write(data)
    else:
        open(path, "w", encoding="utf-8").write(json.dumps(data, indent=1))


def box(x1, y1, z1, x2, y2, z2, col, top=None):
    f = {d: {"uv": uv(col), "texture": "#p"} for d in ("north", "south", "east", "west", "up", "down")}
    if top:
        f["up"] = {"uv": uv(top), "texture": "#p"}
    return {"from": [min(x1, x2), min(y1, y2), min(z1, z2)], "to": [max(x1, x2), max(y1, y2), max(z1, z2)], "faces": f}


def sym(x1, y1, z1, x2, y2, z2, col, top=None):
    return [box(x1, y1, z1, x2, y2, z2, col, top), box(16 - x2, y1, z1, 16 - x1, y2, z2, col, top)]


def shift(es, dx=0, dy=0, dz=0):
    out = []
    for e in es:
        e = json.loads(json.dumps(e))
        e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2] + dz]
        e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2] + dz]
        out.append(e)
    return out


# worn on the head (the Creature Skull item is a dragon head, so it can be equipped): the model sits on top of the
# head, snout forward, instead of disappearing inside it. Units are 1/16 block inside the head layer (scaled 0.625).
HEAD_DISPLAY = {"creature_skull": {"head": {"rotation": [0, 0, 0], "translation": [0, 13, -2], "scale": [0.95, 0.95, 0.95]}}}


def model(name, es):
    lo = [min(e["from"][i] for e in es) for i in range(3)]
    hi = [max(e["to"][i] for e in es) for i in range(3)]
    size = max(hi[i] - lo[i] for i in range(3))
    s = round(min(1.2, 0.625 * 16 / max(size, 1)), 3)
    ty = round((8 - (lo[1] + hi[1]) / 2) * s * 0.9, 2)
    # flat models (cookies, buns) sit at y 0..1, far below the pivot the hand display turns around, so in the hand
    # they floated away from it: lift them so their middle is on the pivot (first person only; others unchanged)
    hand_dy = round((8 - (lo[1] + hi[1]) / 2) * s * 0.5, 2) if hi[1] - lo[1] <= 3 else 0
    write(f"assets/relics/models/item/{name}.json", {
        "textures": {"p": "relics:item/palette", "particle": "relics:item/palette"}, "elements": es,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, ty, 0], "scale": [s] * 3},
                    "ground": {"translation": [0, 2, 0], "scale": [s * 0.5] * 3},
                    "fixed": {"translation": [0, ty * 0.6, 0], "scale": [s * 0.8] * 3},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [s * 0.45] * 3},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, hand_dy, 0], "scale": [s * 0.5] * 3},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, hand_dy, 0], "scale": [s * 0.5] * 3},
                    **HEAD_DISPLAY.get(name, {})}})
    write(f"assets/relics/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"relics:item/{name}"}})

# ------------------------------------------------------------------ the relics


def skull():
    """Horned skull of one of Shoutlander's creatures, lying on the ground (~1 m long)."""
    e = [box(3, 0, 6, 13, 7.5, 15, "bone"), box(4, 7.5, 7, 12, 8.5, 14, "bone_l"),     # cranium
         box(5, 0, 1, 11, 4.5, 6, "bone"), box(6, 4.5, 2, 10, 5.5, 6, "bone_old"),       # snout
         box(4.5, 0, 0.5, 11.5, 1.2, 6.5, "bone_old"),                                    # jaw line
         box(6.6, 3.0, 0.95, 7.6, 4.2, 1.05, "char"), box(8.4, 3.0, 0.95, 9.4, 4.2, 1.05, "char"),  # nostrils
         box(4.2, 4.0, 5.95, 6.8, 6.4, 6.05, "char"), box(9.2, 4.0, 5.95, 11.8, 6.4, 6.05, "char")]  # eye sockets
    for x in (5.0, 6.4, 7.8, 9.2, 10.6):                                                  # fangs
        e.append(box(x, 0, 0.9, x + 0.7, 1.9, 1.5, "bone_l"))
    e += sym(1.5, 5.0, 9.0, 3.0, 7.0, 11.0, "bone_old")                                  # horns: out ...
    e += sym(0.3, 6.5, 10.5, 1.8, 10.0, 12.0, "bone_old")                                 # ... up ...
    e += sym(0.0, 9.5, 12.0, 1.4, 11.0, 15.5, "bone_old_d")                               # ... and back
    e += [box(5, 0, 14, 11, 3, 16, "bone_old_d"), box(7.4, 7.6, 9, 8.6, 8.7, 13, "bone_old_d")]  # neck stump, crack
    return e


def skull_wall():
    return shift([box(2, 2, 15, 14, 14, 16, "wood_char")], 0, 0, 0) + shift(skull(), 0, 3, -0.5)


def ribcage():
    """Ribs of a huge creature arching over the ground - you can walk through them."""
    e = [box(7, 11, 0, 9, 13, 16, "bone_old")]                                             # spine
    for i, z in enumerate((1, 4, 7, 10, 13)):
        h = 11 - abs(i - 2) * 0.8
        e += sym(4.5, h - 2, z, 7.0, h, z + 1.2, "bone")                                  # out from the spine
        e += sym(2.2, h - 6, z, 4.5, h - 1.5, z + 1.2, "bone")                            # down ...
        e += sym(1.0, 0, z, 2.8, h - 5.5, z + 1.2, "bone_old")                            # ... into the ground
    e += [box(6, 0, 15, 10, 2, 16, "bone_old_d"), box(1, 0, 2, 4, 1, 5, "dirt"), box(12, 0, 9, 15, 1, 12, "dirt")]
    return e


def banner_cloth(x0, y_top, z, col="cloth"):
    e = [box(x0, y_top - 9, z, x0 + 8, y_top, z + 0.4, col)]
    for i, (dx, l) in enumerate(((0, 3), (1.5, 1.5), (3, 4), (4.5, 2), (6.5, 3.5))):        # torn strips
        e.append(box(x0 + dx, y_top - 9 - l, z, x0 + dx + 1.4, y_top - 9, z + 0.4, col if i % 2 else col + "_d"))
    e += [box(x0 + 2, y_top - 4, z - 0.01, x0 + 3.5, y_top - 2.5, z + 0.41, "char"),         # burn holes / claw slashes
          box(x0 + 5, y_top - 7, z - 0.01, x0 + 5.5, y_top - 1, z + 0.41, "char"),
          box(x0 + 6, y_top - 7.5, z - 0.01, x0 + 6.5, y_top - 1.5, z + 0.41, "char"),
          box(x0 + 1, y_top - 1.5, z - 0.02, x0 + 7, y_top - 0.8, z + 0.42, "brass_d")]     # tarnished emblem band
    return e


def banner():
    """Burned, claw-torn banner of the First Settlement on a broken pole."""
    return [box(7.4, 0, 9, 8.6, 15, 10.2, "wood_char"), box(7.0, 0, 8.6, 9.0, 1, 10.6, "wood_char_d"),
            box(3.5, 14, 9.2, 12.5, 15, 10.0, "wood_char")] + banner_cloth(4, 14, 9.4)


def banner_wall():
    return [box(3, 13, 14.5, 13, 14, 16, "iron_d"), box(4, 13.5, 13.5, 12, 14.5, 14.5, "wood_char")] + banner_cloth(4, 13.5, 13.8)


def lantern_body(y=0):
    e = [box(5, y, 5, 11, y + 1, 11, "iron_d"), box(5, y + 8, 5, 11, y + 9, 11, "soot"), box(6.5, y + 9, 6.5, 9.5, y + 10, 9.5, "iron_d"),
         box(7.3, y + 10, 7.3, 8.7, y + 11.5, 8.7, "iron")]
    for x, z in ((5, 5), (10.2, 5), (5, 10.2), (10.2, 10.2)):
        e.append(box(x, y + 1, z, x + 0.8, y + 8, z + 0.8, "iron_d"))
    e += [box(5.2, y + 1, 5.3, 10.8, y + 4, 5.5, "glass"), box(5.3, y + 1, 5.2, 5.5, y + 8, 10.8, "glass"),
          box(10.5, y + 5, 5.2, 10.7, y + 8, 10.8, "glass")]            # cracked: some panes gone
    e += [box(7.3, y + 1, 7.3, 8.7, y + 1.6, 8.7, "char"), box(7.5, y + 1.6, 7.5, 8.5, y + 3.6, 8.5, "flame"),
          box(7.75, y + 1.8, 7.75, 8.25, y + 3.0, 8.25, "flame_core")]
    return e


def lantern():
    return lantern_body(0)


def lantern_wall():
    return [box(7, 12, 14.5, 9, 14, 16, "iron_d"), box(7.5, 13, 8, 8.5, 14, 14.5, "iron_d"), box(7.6, 11.5, 7.6, 8.4, 13, 8.4, "chain")] + shift(lantern_body(0), 0, 0.5, 0)


def lantern_hanging():
    return [box(7.6, 11, 7.6, 8.4, 16, 8.4, "chain")] + lantern_body(0.5)


def candles():
    """Melted candles for the fallen settlers."""
    e = [box(2, 0, 3, 14, 0.4, 13, "wax_d")]
    for x, z, h in ((4, 5, 6), (8, 4, 9), (11, 7, 4.5), (6, 9, 7.5), (9.5, 10.5, 3)):
        e += [box(x, 0.4, z, x + 1.6, h, z + 1.6, "wax"), box(x - 0.2, 0.4, z + 0.3, x + 0.3, h - 1.5, z + 0.8, "wax_l"),
              box(x + 0.7, h, z + 0.7, x + 0.9, h + 0.6, z + 0.9, "char"),
              box(x + 0.5, h + 0.6, z + 0.5, x + 1.1, h + 1.8, z + 1.1, "flame"), box(x + 0.65, h + 0.7, z + 0.65, x + 0.95, h + 1.3, z + 0.95, "flame_core")]
    return e


def grave():
    """Weathered grave of a First Settlement settler, a rusted helmet resting on it."""
    e = [box(3, 0, 9, 13, 11, 11, "stone"), box(4, 11, 9, 12, 12.5, 11, "stone"), box(5.5, 12.5, 9, 10.5, 13.5, 11, "stone_d"),
         box(3, 0, 8.6, 13, 1.5, 11.4, "stone_d"), box(3.2, 7, 8.9, 5, 10, 9.1, "moss"), box(10, 1.5, 8.9, 12.8, 4, 9.1, "moss"),
         box(7.8, 3, 8.95, 8.2, 10, 9.05, "char"), box(6, 6.8, 8.95, 10, 7.2, 9.05, "char"),                 # carved cross
         box(9, 9, 8.94, 9.4, 12, 9.06, "stone_d"),                                                           # crack
         box(3, 0, 1, 13, 1.2, 8.6, "dirt"), box(4, 1.2, 2, 12, 1.8, 7.8, "dirt_d")]                          # mound
    e += [box(5.5, 1.8, 3.5, 10.5, 5, 7.5, "rust"), box(5.2, 1.8, 3.2, 10.8, 2.4, 7.8, "rust_d"), box(7.6, 2.4, 3.1, 8.4, 4.6, 3.4, "char")]  # helmet
    return e


def monolith():
    """Cracked pillar of the old world, faint glyphs pulsing (two blocks tall)."""
    e = [box(4, 0, 4, 12, 26, 12, "stone"), box(5, 26, 5, 11, 29, 11, "stone"), box(4, 26, 8, 8, 28, 12, "stone_d"),
         box(3, 0, 3, 13, 2, 13, "stone_d"), box(11.9, 8, 4, 12.1, 18, 5.5, "stone_d"), box(4, 14, 3.9, 6, 20, 4.1, "stone_d")]
    for y in (5, 9, 13, 17, 21):                                                                              # glyph rows
        e += [box(6, y, 3.95, 7, y + 2, 4.05, "glyph"), box(8, y + 0.5, 3.95, 10, y + 1.2, 4.05, "glyph"), box(9.5, y + 1, 3.95, 10.2, y + 2.5, 4.05, "glyph")]
    e += [box(0.5, 0, 1, 3, 1.5, 3.5, "stone_d"), box(13, 0, 11, 15.5, 1, 14, "stone"), box(12, 0, 1.5, 14, 2.5, 3, "moss")]  # rubble
    return e


def jar():
    """Guild archive specimen jar: a creature's claw floating in murky fluid."""
    e = [box(3.5, 0, 3.5, 12.5, 1.5, 12.5, "brass"), box(3.5, 13.5, 3.5, 12.5, 15, 12.5, "brass"), box(6.5, 15, 6.5, 9.5, 16, 9.5, "brass_d"),
         box(4, 1.5, 4, 12, 13.5, 4.3, "glass"), box(4, 1.5, 11.7, 12, 13.5, 12, "glass"), box(4, 1.5, 4.3, 4.3, 13.5, 11.7, "glass"),
         box(11.7, 1.5, 4.3, 12, 13.5, 11.7, "glass"), box(4.4, 1.5, 4.4, 11.6, 11.5, 11.6, "fluid"),
         box(6.5, 3, 7, 8.5, 9, 9, "flesh"), box(8.5, 7, 7.4, 10.5, 8, 8.6, "bone_old"), box(9.5, 4.5, 7.6, 10.4, 7, 8.4, "bone_old"),
         box(5.5, 6, 7.4, 6.5, 8.5, 8.6, "bone_old"), box(4.1, 6, 3.9, 8, 9, 3.95, "paper")]
    for x, z in ((3.6, 3.6), (11.6, 3.6), (3.6, 11.6), (11.6, 11.6)):
        e.append(box(x, 1.4, z, x + 0.8, 13.6, z + 0.8, "brass_d"))
    return e


def egg_sac(hang=False):
    e = []
    rnd = random.Random(5)
    for x, z, s in ((4, 4, 5), (9, 5, 4), (6, 9, 5.5), (10.5, 10, 3.5)):
        y0 = 16 - s * 1.3 if hang else 0.5
        e += [box(x, y0, z, x + s, y0 + s * 1.3, z + s, "flesh"), box(x + 0.5, y0 + s * 0.4, z - 0.01, x + 0.9, y0 + s * 1.1, z + s + 0.01, "vein"),
              box(x - 0.01, y0 + s * 0.6, z + 1, x + s + 0.01, y0 + s * 0.6 + 0.4, z + 1.4, "vein")]
    e.append(box(2, 15.5, 2, 14, 16, 14, "slime") if hang else box(2, 0, 2, 14, 0.6, 14, "slime"))
    return e


def cage(hanging=True):
    """Rusted gibbet cage with the bones of someone who didn't make it."""
    y0 = 1.5 if hanging else 0
    e = []
    if hanging:
        e.append(box(7.6, 13.5, 7.6, 8.4, 16, 8.4, "chain"))
    e += [box(4, y0, 4, 12, y0 + 1, 12, "rust"), box(4, y0 + 11, 4, 12, y0 + 12, 12, "rust"), box(7, y0 + 12, 7, 9, y0 + 13, 9, "rust_d")]
    for i in range(5):
        p = 4 + i * 1.8
        e += [box(p, y0 + 1, 4, p + 0.5, y0 + 11, 4.5, "rust_d"), box(p, y0 + 1, 11.5, p + 0.5, y0 + 11, 12, "rust_d"),
              box(4, y0 + 1, p, 4.5, y0 + 11, p + 0.5, "rust_d"), box(11.5, y0 + 1, p, 12, y0 + 11, p + 0.5, "rust_d")]
    e += [box(6, y0 + 1, 6, 9, y0 + 3.5, 9, "bone"), box(6.6, y0 + 1.6, 5.95, 7.4, y0 + 2.4, 6.05, "char"), box(7.8, y0 + 1.6, 5.95, 8.6, y0 + 2.4, 6.05, "char"),
          box(8.5, y0 + 1, 7, 11, y0 + 1.6, 9.5, "bone_old"), box(5, y0 + 1, 9, 10, y0 + 1.5, 9.6, "bone_old"), box(5.5, y0 + 1.5, 9.5, 6, y0 + 4, 10, "bone_old")]
    return e


def ash_pile():
    e = [box(1, 0, 2, 15, 1.2, 14, "ash"), box(3, 1.2, 4, 12, 2.2, 12, "ash_d"), box(5, 2.2, 6, 10, 2.8, 10, "soot")]
    e += [box(2, 1, 6, 9, 1.8, 7, "bone_old"), box(10, 1.5, 9, 12, 3.8, 11, "bone_old"), box(10.4, 2.4, 8.95, 11.6, 3.2, 9.05, "char"),
          box(6, 2.6, 7, 6.6, 2.9, 7.6, "ember"), box(8.5, 2.6, 9, 9, 2.9, 9.5, "ember"), box(4, 2.1, 10, 4.5, 2.3, 10.5, "ember")]
    return e


def totem(lit=False):
    """Guild warding totem: a brass ward whose red lens wakes when creatures are near (about 1.8 m)."""
    lens = "glow_red" if lit else "lens_dark"
    e = [box(4, 0, 4, 12, 2, 12, "brass_d"), box(6.5, 2, 6.5, 9.5, 22, 9.5, "brass"), box(5.5, 6, 5.5, 10.5, 7, 10.5, "copper"),
         box(5.5, 14, 5.5, 10.5, 15, 10.5, "copper"), box(4, 22, 4, 12, 28, 12, "brass_d"), box(5, 28, 5, 11, 29, 11, "copper"),
         box(7.4, 29, 7.4, 8.6, 31, 8.6, "brass"), box(5.5, 23, 3.6, 10.5, 27, 4.0, "brass"), box(6.3, 23.7, 3.4, 9.7, 26.3, 3.6, lens),
         box(7.2, 24.6, 3.3, 8.8, 25.4, 3.4, "glow_red_l" if lit else "char")]
    for y in (9, 17):
        e += [box(9.5, y, 7.5, 11.5, y + 2, 8.5, "copper_d"), box(4.5, y + 1, 7.5, 6.5, y + 3, 8.5, "copper_d")]   # little gears
    return e


def portal_shard():
    """A shard of the arch the first survivors used to escape to the Guild."""
    e = [box(6, 0, 6, 10, 3, 10, "obsidian"), box(5.5, 3, 6.5, 9.5, 8, 9.5, "obsidian"), box(6.5, 8, 7, 9.5, 12, 9.5, "obsidian"),
         box(7, 12, 7.5, 9, 15, 9, "obsidian_l"), box(7.5, 15, 8, 8.5, 17, 8.8, "obsidian_l"),
         box(5.45, 4, 7, 5.55, 9, 7.6, "glow_purple"), box(9.45, 2, 8, 9.55, 7, 8.5, "glow_purple"), box(7, 9, 6.95, 7.5, 13, 7.05, "glow_purple"),
         box(3, 0, 3, 5, 1, 5, "obsidian_d"), box(11, 0, 10, 13, 1.5, 12, "obsidian_d"),
         box(2.5, 8, 9, 3.5, 9.5, 10, "obsidian_l"), box(12, 11, 5, 13, 12, 6, "obsidian_l")]                            # floating bits
    return e


# ------------------------------------------------------------------ Xmas Town (collection 2: cosy village, steampunk and Japanese touches)


def tree(on):
    """Christmas tree, built at 2/3 size (the plugin shows it at 1.5x, about 2.8 blocks tall)."""
    e = [box(7, 0, 7, 9, 4, 9, "wood_warm_d"), box(5, 0, 5, 11, 1, 11, "xred")]
    tiers = [(4, 10, 10.5), (9, 15, 8.5), (14, 20, 6.5), (19, 25, 4.5), (24, 28, 2.5)]
    for y1, y2, r in tiers:
        e += [box(8 - r, y1, 8 - r, 8 + r, y2, 8 + r, "pine"), box(8 - r + 1, y2 - 1, 8 - r + 1, 8 + r - 1, y2 + 0.5, 8 + r - 1, "pine_d")]
        e += [box(8 - r - 0.01, y1, 7.5, 8 - r + 1.5, y1 + 0.6, 8.5, "snow")]
    rnd = random.Random(12)
    bulbs = ["bulb_red", "bulb_yellow", "bulb_blue"]
    for i in range(26):
        y1, y2, r = tiers[i % 4]
        y = y1 + rnd.uniform(1, y2 - y1 - 1)
        side = rnd.randint(0, 3)
        t = rnd.uniform(-r + 1, r - 1)
        x, z = [(8 - r - 0.3, 8 + t), (8 + r - 0.2, 8 + t), (8 + t, 8 - r - 0.3), (8 + t, 8 + r - 0.2)][side]
        if i % 3 == 0:
            col = ["xred", "gold", "brass"][(i // 3) % 3]
            e.append(box(x - 0.6, y - 0.6, z - 0.6, x + 0.6, y + 0.6, z + 0.6, col))
        else:
            col = bulbs[i % 3] if (on or i % 2 == 0) else "bulb_off"
            e.append(box(x - 0.35, y - 0.35, z - 0.35, x + 0.35, y + 0.35, z + 0.35, col))
    e += [box(7, 28, 7, 9, 31, 9, "gold_l" if on else "gold"), box(6, 29, 7.6, 10, 30, 8.4, "gold"), box(7.6, 29, 6, 8.4, 30, 10, "gold")]
    return e


def string_lights(on, wall=True):
    # 13-11 (owner): much too small - the wire spanned 14 px with pea-sized bulbs. Full block
    # width, thicker wire, bulbs 1.1 px, deeper sag; the ceiling strand droops through the block.
    e = []
    if wall:
        z, y0 = 15.1, 14.6   # strand against the wall, high in the block
    else:
        z, y0 = 8.0, 15.2    # strand across the ceiling
    for i in range(8):
        x = i * 2.0
        d = [0, 0.9, 1.3, 0.9][i % 4]
        y = y0 - d
        e.append(box(x, y, z, x + 2.0, y + 0.45, z + 0.45, "coal"))
        col = ["bulb_red", "bulb_yellow", "bulb_blue", "xgreen"][i % 4]
        if not on and i % 2:
            col = "bulb_off"
        e.append(box(x + 0.45, y - 1.25, z - 0.35, x + 1.55, y - 0.1, z + 0.8, col))
    return e


def advent_wreath(lit):
    e = [box(2, 0, 2, 14, 1.5, 4, "pine"), box(2, 0, 12, 14, 1.5, 14, "pine"), box(2, 0, 4, 4, 1.5, 12, "pine"), box(12, 0, 4, 14, 1.5, 12, "pine"),
         box(2.5, 1.5, 2.5, 13.5, 2, 3.5, "pine_d"), box(4, 1, 1.6, 5, 2, 2, "xred"), box(11, 1, 14, 12, 2, 14.4, "xred"), box(1.6, 1, 8, 2, 2, 9, "gold")]
    for i, (x, z) in enumerate(((3, 3), (12, 3), (12, 12), (3, 12))):
        e += [box(x - 0.8, 1.5, z - 0.8, x + 0.8, 6, z + 0.8, "xred"), box(x - 0.1, 6, z - 0.1, x + 0.1, 6.5, z + 0.1, "coal")]
        if i < lit:
            e += [box(x - 0.35, 6.5, z - 0.35, x + 0.35, 7.8, z + 0.35, "flame"), box(x - 0.15, 6.6, z - 0.15, x + 0.15, 7.2, z + 0.15, "flame_core")]
    return e


def advent_wreath_wall(lit):
    """Wreath hanging on the wall (back at z=16), candles sticking out into the room."""
    import math as _m
    e = []
    for i in range(12):
        a = i * _m.pi / 6
        x, y = 8 + _m.cos(a) * 5.5, 8 + _m.sin(a) * 5.5
        e.append(box(x - 1.6, y - 1.6, 13.6, x + 1.6, y + 1.6, 16, "pine" if i % 2 else "pine_d"))
    e += [box(6, 0.4, 12.8, 10, 3, 14, "xred"), box(4.8, 0, 12.8, 6, 2, 13.8, "xred_d"), box(10, 0, 12.8, 11.2, 2, 13.8, "xred_d"),
          box(7.4, 1, 12.6, 8.6, 2.2, 13, "gold")]
    for i, ang in enumerate((45, 135, 225, 315)):
        a = _m.radians(ang)
        x, y = 8 + _m.cos(a) * 5.5, 8 + _m.sin(a) * 5.5
        e += [box(x - 0.9, y - 0.9, 8.5, x + 0.9, y + 0.9, 13.6, "xred"), box(x - 0.1, y - 0.1, 8, x + 0.1, y + 0.1, 8.5, "coal")]
        if i < lit:
            e += [box(x - 0.35, y - 0.35, 6.7, x + 0.35, y + 0.35, 8, "flame"), box(x - 0.15, y - 0.15, 7.2, x + 0.15, y + 0.15, 7.9, "flame_core")]
    return e


def candy_cane_giant(col):
    """Two blocks of peppermint: striped shaft with a hook that bends over to the east."""
    e = []
    for i, y in enumerate(range(0, 24, 3)):
        e.append(box(6.5, y, 6.5, 9.5, y + 3, 9.5, col if i % 2 == 0 else "icing"))
    for i, x in enumerate((6.5, 9.5, 12.5)):
        e.append(box(x, 24, 6.5, x + 3, 27, 9.5, "icing" if i % 2 == 0 else col))
    e.append(box(6.5, 24, 6.5, 9.5, 27, 9.5, col))
    e.append(box(12.5, 20, 6.5, 15.5, 24, 9.5, col))
    e.append(box(12.5, 17, 6.5, 15.5, 20, 9.5, "icing"))
    return e


def steam_lantern_body(y=0):
    e = [box(5, y, 5, 11, y + 1.2, 11, "brass_d"), box(5.5, y + 1.2, 5.5, 10.5, y + 8, 10.5, "amber"), box(5, y + 8, 5, 11, y + 9.2, 11, "brass_d"),
         box(6.5, y + 9.2, 6.5, 9.5, y + 10.5, 9.5, "brass"), box(7.3, y + 10.5, 7.3, 8.7, y + 12, 8.7, "copper")]
    for x, z in ((5, 5), (10.2, 5), (5, 10.2), (10.2, 10.2)):
        e.append(box(x, y + 1.2, z, x + 0.8, y + 8, z + 0.8, "brass"))
    e += [box(10.9, y + 3, 7, 11.6, y + 6, 9, "copper_d"), box(7.2, y + 2, 7.2, 8.8, y + 4.5, 8.8, "flame_l")]
    return e


def snow_lantern():
    """Yukimi-doro stone lantern with a snow cap and a warm window."""
    return [box(6, 0, 6, 10, 5, 10, "stone"), box(4, 5, 4, 12, 6, 12, "stone_d"), box(5, 6, 5, 11, 10, 11, "stone"),
            box(6, 7, 4.9, 10, 9.5, 5.1, "amber"), box(6, 7, 10.9, 10, 9.5, 11.1, "amber"), box(2, 10, 2, 14, 11.5, 14, "stone_d"),
            box(3, 11.5, 3, 13, 12.5, 13, "snow"), box(6, 12.5, 6, 10, 14, 10, "snow"), box(7.3, 14, 7.3, 8.7, 15, 8.7, "stone")]


def kadomatsu():
    e = [box(4, 0, 4, 12, 5, 12, "straw"), box(4, 2, 3.9, 12, 3, 12.1, "xred"), box(5, 5, 5, 11, 6, 11, "straw_d")]
    for x, h in ((6, 15), (8.5, 13), (7, 11)):
        e += [box(x, 5, x - 1, x + 2, h, x + 1, "bamboo"), box(x, h, x - 1, x + 1, h + 1, x + 1, "bamboo_d")]
    e += [box(3, 5, 6, 6, 8, 10, "pine"), box(10, 5, 5, 13, 9, 9, "pine"), box(5, 6, 10, 9, 8, 13, "pine_d"), box(7, 8, 2.5, 9, 9, 4, "xred")]
    return e


def hearth():
    """Brick fireplace with a mantle, stockings and a garland (about 1.7 m)."""
    e = [box(0, 0, 9, 16, 22, 16, "brick"), box(0, 0, 8, 16, 2, 16, "stone_d"), box(4, 2, 8.9, 12, 10, 9.1, "char"),
         box(-0.5, 12, 7, 16.5, 14, 16, "wood_warm"), box(2, 22, 10, 14, 26, 16, "brick_d"),
         box(5, 2, 9.5, 11, 3, 13, "wood_char"), box(6, 3, 10, 10, 7, 12, "flame"), box(7, 3.5, 10.5, 9, 5.5, 11.5, "flame_core"),
         box(0, 14, 7.5, 16, 15.5, 9, "pine"), box(3, 14.5, 7.3, 4, 15.5, 7.6, "xred"), box(11, 14.5, 7.3, 12, 15.5, 7.6, "gold")]
    for x, col in ((2.5, "xred"), (7, "xgreen"), (11.5, "xred")):
        e += [box(x, 7, 7.6, x + 2, 12, 8.4, col), box(x - 1, 7, 7.6, x + 2, 8.5, 8.4, col), box(x, 11, 7.5, x + 2, 12, 8.5, "icing")]
    return e


def snowman():
    """Snowman with a top hat and brass goggles (about 1.8 m)."""
    return [box(3, 0, 3, 13, 10, 13, "snow"), box(4.5, 10, 4.5, 11.5, 18, 11.5, "snow"), box(5.5, 18, 5.5, 10.5, 24, 10.5, "snow"),
            box(5, 17, 5, 11, 18.5, 11, "xred"), box(9.5, 13, 4, 11, 18, 5, "xred"),
            box(7.5, 20, 4, 8.5, 21, 5.5, "carrot"), box(7.6, 20.1, 2.8, 8.4, 20.9, 4, "carrot"),
            box(6.3, 22, 5.4, 7.2, 22.9, 5.5, "coal"), box(8.8, 22, 5.4, 9.7, 22.9, 5.5, "coal"),
            box(7.5, 14, 4.4, 8.5, 15, 4.5, "coal"), box(7.5, 11.5, 4.4, 8.5, 12.5, 4.5, "coal"), box(7.5, 6, 2.9, 8.5, 7, 3, "coal"),
            box(4.5, 24, 4.5, 11.5, 25, 11.5, "coal"), box(6, 25, 6, 10, 30, 10, "coal"), box(6, 25.5, 5.9, 10, 26.5, 10.1, "xred"),
            box(5.8, 24.3, 5.2, 7.2, 25.4, 5.6, "brass"), box(8.8, 24.3, 5.2, 10.2, 25.4, 5.6, "brass"),
            box(-1, 14, 7.5, 4.5, 15, 8.5, "wood_char"), box(11.5, 15, 7.5, 17, 16, 8.5, "wood_char")]


def sleigh():
    """Red sleigh with brass runners (two blocks long; you can sit in it)."""
    return [box(1.5, 0, -6, 2.5, 1, 22, "brass"), box(13.5, 0, -6, 14.5, 1, 22, "brass"), box(1.5, 1, -7, 2.5, 4, -6, "brass"),
            box(13.5, 1, -7, 14.5, 4, -6, "brass"), box(1.5, 4, -7, 2.5, 5, -4, "brass"), box(13.5, 4, -7, 14.5, 5, -4, "brass"),
            box(2, 1, -4, 3, 3, -3, "brass_d"), box(13, 1, -4, 14, 3, -3, "brass_d"), box(2, 1, 18, 3, 3, 19, "brass_d"), box(13, 1, 18, 14, 3, 19, "brass_d"),
            box(1, 3, -3, 15, 4, 20, "xred_d"), box(1, 4, -3, 2, 10, 20, "xred"), box(14, 4, -3, 15, 10, 20, "xred"),
            box(1, 4, 19, 15, 13, 20, "xred"), box(1, 4, -4, 15, 8, -3, "xred"), box(0.8, 9.5, -3, 2.2, 10.5, 20, "gold"),
            box(13.8, 9.5, -3, 15.2, 10.5, 20, "gold"), box(0.8, 12.5, 18.8, 15.2, 13.5, 20.2, "gold"),
            box(2, 4, 8, 14, 6, 18.5, "cream"), box(2, 6, 17, 14, 11, 18.5, "cream_d"), box(10, 6, 9, 13, 9, 12, "xgreen")]


def toy_train():
    """Clockwork toy train on a round track (the plugin turns it)."""
    import math as _m
    e = []
    for i in range(12):
        a = i * _m.pi / 6
        x, z = 8 + _m.cos(a) * 5.5, 8 + _m.sin(a) * 5.5
        e.append(box(x - 1.3, 0, z - 1.3, x + 1.3, 0.4, z + 1.3, "wood_char"))
    e += [box(12.5, 0.4, 6.5, 14.5, 2.5, 9.5, "xred"), box(12.7, 2.5, 7, 14.3, 3.6, 8.2, "coal"), box(12.6, 0.4, 9.5, 14.4, 1.8, 10.5, "brass"),
          box(13, 0.1, 6.3, 14, 0.9, 9.7, "coal"), box(12.6, 0.4, 3.6, 14.4, 1.8, 6.2, "xgreen"), box(12.8, 1.8, 4, 14.2, 2.4, 5.8, "gold")]
    return e


def gift(col):
    return [box(4, 0, 4, 12, 7, 12, col), box(3.8, 6.5, 3.8, 12.2, 7.5, 12.2, col + "_d"), box(7.3, 0, 3.7, 8.7, 7.6, 12.3, "gold"),
            box(3.7, 0, 7.3, 12.3, 7.6, 8.7, "gold"), box(5.5, 7.6, 7.3, 7.5, 9, 8.7, "gold_l"), box(8.5, 7.6, 7.3, 10.5, 9, 8.7, "gold_l")]


def advent_calendar():
    e = [box(1, 1, 15, 15, 15, 16, "wood_warm"), box(2, 13, 14.7, 14, 14.6, 15, "xred"), box(6, 15, 15, 10, 16.5, 16, "wood_warm_d")]
    cols = ["xred", "xgreen", "gold", "cream"]
    for r in range(4):
        for c in range(6):
            x, y = 2 + c * 2, 2.5 + r * 2.5
            e.append(box(x + 0.2, y, 14.75, x + 1.8, y + 2, 15, cols[(r + c) % 4]))
    return e


def music_box():
    return [box(3, 0, 5, 13, 4, 11, "wood_warm"), box(3.5, 4, 5.5, 12.5, 4.3, 10.5, "wood_warm_d"), box(3, 4, 10.4, 13, 10, 11, "wood_warm"),
            box(4, 5, 10.2, 12, 9, 10.4, "gold"), box(5, 4.3, 6, 11, 6, 7.8, "brass"), box(5.5, 4.3, 8, 10.5, 4.8, 9.5, "iron"),
            box(13, 1.5, 7.5, 14.5, 2.5, 8.5, "brass_d"), box(14.5, 1, 7, 15, 3, 9, "brass"),
            box(7.5, 6, 6.5, 8.5, 9, 7.5, "icing"), box(6.5, 8, 6.8, 9.5, 8.6, 7.2, "icing")]


def snow_globe():
    e = [box(4, 0, 4, 12, 2.5, 12, "wood_warm"), box(3.6, 2, 3.6, 12.4, 2.6, 12.4, "brass"),
         box(4.5, 2.6, 4.5, 11.5, 11, 4.8, "glass"), box(4.5, 2.6, 11.2, 11.5, 11, 11.5, "glass"), box(4.5, 2.6, 4.8, 4.8, 11, 11.2, "glass"),
         box(11.2, 2.6, 4.8, 11.5, 11, 11.2, "glass"), box(5, 11, 5, 11, 12, 11, "glass"), box(6, 12, 6, 10, 12.6, 10, "glass"),
         box(5, 2.6, 5, 11, 3.2, 11, "snow"), box(6, 3.2, 6.5, 9, 6, 9, "xred"), box(5.6, 6, 6.2, 9.4, 7.2, 9.4, "snow"),
         box(9.5, 3.2, 7, 10.5, 8, 8, "pine")]
    for x, y, z in ((6, 9, 7), (9, 10, 9), (7.5, 8, 10), (10, 7.5, 6)):
        e.append(box(x, y, z, x + 0.4, y + 0.4, z + 0.4, "snow"))
    return e


def market_stall():
    """Christmas market stall with a striped canopy (two blocks tall)."""
    e = [box(1, 0, 9, 15, 9, 15, "wood_warm"), box(0.5, 9, 8.5, 15.5, 10, 15.5, "wood_warm_d"),
         box(1, 0, 1, 2, 26, 2, "wood_warm_d"), box(14, 0, 1, 15, 26, 2, "wood_warm_d"), box(1, 0, 14, 2, 26, 15, "wood_warm_d"), box(14, 0, 14, 15, 26, 15, "wood_warm_d")]
    for i in range(8):
        e.append(box(i * 2, 26, -1, i * 2 + 2, 28, 16, "xred" if i % 2 == 0 else "icing"))
    e += [box(0, 25, -1.2, 16, 26, 0, "xred"), box(3, 10, 10, 6, 12, 13, "ginger"), box(7, 10, 10, 10, 13, 13, "xgreen"),
          box(11, 10, 10, 13, 11.5, 13, "gold"), box(2, 20, 1.5, 14, 21, 2, "pine"), box(4, 19.5, 1.4, 5, 20.5, 1.6, "bulb_red"), box(9, 19.5, 1.4, 10, 20.5, 1.6, "bulb_yellow")]
    return e


def gingerbread_man(x, z, y=0):
    return [box(x - 1, y, z - 1.5, x + 1, y + 0.5, z + 1, "ginger"), box(x - 0.8, y, z - 2.6, x + 0.8, y + 0.5, z - 1.4, "ginger"),
            box(x - 1.9, y, z - 1.2, x + 1.9, y + 0.5, z - 0.5, "ginger"), box(x - 1.4, y, z + 0.8, x - 0.4, y + 0.5, z + 2, "ginger"),
            box(x + 0.4, y, z + 0.8, x + 1.4, y + 0.5, z + 2, "ginger"), box(x - 0.2, y + 0.5, z - 1, x + 0.2, y + 0.55, z + 0.5, "icing")]


def cookie_plate(left):
    e = [box(3, 0, 3, 13, 0.6, 13, "icing"), box(2.5, 0.4, 2.5, 13.5, 1, 13.5, "icing_d")]
    spots = [(5.5, 6.5), (10.5, 6.5), (8, 10), (5.5, 11), (10.5, 11), (8, 5)]
    for x, z in spots[:left]:
        e += gingerbread_man(x, z, 1)
    return e


def mug(col, liquid, extra=None):
    e = [box(5, 0, 5, 11, 7, 11, col), box(5.6, 6.2, 5.6, 10.4, 6.8, 10.4, liquid), box(11, 2, 7.3, 13, 5.5, 8.7, col + "_d")]
    return e + (extra or [])


def water_bottle():
    """A stout glass bottle of clear water: thick glass foot, water filled to the shoulder
    with one lighter band at the surface, a brass ring and a wooden cork under the lip."""
    e = [box(5.4, 0, 5.4, 10.6, 0.6, 10.6, "glass"),                      # glass bottom
         box(5.7, 0.6, 5.7, 10.3, 6.0, 10.3, "water_dark"),               # the water itself
         box(5.7, 6.0, 5.7, 10.3, 6.35, 10.3, "water_dark_l"),            # surface band, a shade lighter
         box(5.4, 6.35, 5.4, 10.6, 7.3, 10.6, "glass"),                   # empty shoulder
         box(7.0, 7.3, 7.0, 9.0, 9.2, 9.0, "glass"),                      # neck
         box(6.8, 9.0, 6.8, 9.2, 9.5, 9.2, "brass"),                      # lip ring
         box(7.3, 9.5, 7.3, 8.7, 10.4, 8.7, "wood_warm"),                 # cork
         box(5.6, 1.4, 5.5, 6.15, 5.0, 5.75, "icing")]                    # one shine stripe on the glass
    return e


def blood_token_coin(ring, face, mark):
    """A coin standing on its edge: ring, field a hair proud front and back, stamped drop."""
    return [box(4.8, 0.8, 7.2, 11.2, 7.2, 8.8, ring),
            box(5.5, 1.5, 7.05, 10.5, 6.5, 8.95, face),
            box(7.2, 2.6, 7.0, 8.8, 4.2, 9.0, mark),
            box(7.6, 4.2, 7.3, 8.4, 5.1, 8.7, mark)]


def blood_token():
    """The plain coin of the crimson hour: brass ring, deep blood-red field, bone-white drop,
    standing in a small iron stand."""
    return [box(6.2, 0, 7.0, 9.8, 0.8, 9.0, "iron"),
            box(6.5, 0.3, 7.15, 9.5, 0.8, 8.85, "coal")] + blood_token_coin("brass", "rose_deep", "bone")


def blood_token_blessed():
    """Blessed by the blood moon: gold ring and stand, the field glowing, gold veins."""
    return [box(6.2, 0, 7.0, 9.8, 0.8, 9.0, "gold"),
            box(6.5, 0.3, 7.15, 9.5, 0.8, 8.85, "ember")] + blood_token_coin("gold", "glow_red", "gold_l")


def blood_token_wall(blessed=False):
    """The coin in a wall plaque - frame flush with the stone (z = 16), face to the room."""
    fr, ring, face, mark = ("brass", "gold", "glow_red", "gold_l") if blessed else ("wood_warm_d", "brass", "rose_deep", "bone")
    return [box(4.5, 0.5, 14.8, 11.5, 7.8, 16, fr),
            box(5.2, 1.2, 14.6, 10.8, 7.1, 15.2, ring),
            box(5.9, 1.9, 14.55, 10.1, 6.4, 15.1, face),
            box(7.4, 3.2, 14.5, 8.6, 4.4, 15.05, mark)]


XMAS = {
    "christmas_tree": lambda: tree(True), "christmas_tree_b": lambda: tree(False),
    "string_lights": lambda: string_lights(True), "string_lights_b": lambda: string_lights(False),
    "string_lights_ceiling": lambda: string_lights(True, False), "string_lights_ceiling_b": lambda: string_lights(False, False),
    "steam_lantern": lambda: steam_lantern_body(0),
    "steam_lantern_wall": lambda: [box(7, 12, 14.5, 9, 14, 16, "brass_d"), box(7.5, 13, 8, 8.5, 14, 14.5, "brass_d")] + steam_lantern_body(0.5),
    "steam_lantern_hanging": lambda: [box(7.6, 12.5, 7.6, 8.4, 16, 8.4, "chain")] + steam_lantern_body(0.3),
    "snow_lantern": snow_lantern, "kadomatsu": kadomatsu, "hearth": hearth, "snowman": snowman, "sleigh": sleigh, "toy_train": toy_train,
    "gift_box": lambda: gift("xred"), "gift_box_green": lambda: gift("xgreen"), "gift_box_gold": lambda: gift("icing"),
    "advent_calendar": advent_calendar, "music_box": music_box, "snow_globe": snow_globe, "market_stall": market_stall,
    "cookie_plate": lambda: cookie_plate(6), "cookie_plate_3": lambda: cookie_plate(3), "cookie_plate_0": lambda: cookie_plate(0),
    "hot_chocolate": lambda: mug("mug", "cocoa", [box(6.5, 6.8, 6.5, 8.5, 7.6, 8.5, "icing")]),
    "mulled_cider": lambda: mug("glass", "cider", [box(9.5, 6, 5, 10.5, 9, 7, "carrot")]),
    "gingerbread_cookie": lambda: gingerbread_man(8, 8, 0),
    "christmas_stollen": lambda: [box(3, 0, 5, 13, 4, 11, "ginger_l"), box(3.5, 4, 5.5, 12.5, 4.6, 10.5, "icing"), box(3, 1.5, 4.9, 13, 2.5, 11.1, "cream")],
    "candy_cane": lambda: [box(7.4, 0, 7.4, 8.6, 10, 8.6, "icing")] + [box(7.35, y, 7.35, 8.65, y + 1, 8.65, "xred") for y in (1, 4, 7)]
                          + [box(7.4, 10, 7.4, 10.5, 11.2, 8.6, "icing"), box(9.3, 8.5, 7.4, 10.5, 10, 8.6, "xred")],
}
for _n in range(5):
    XMAS["advent_wreath_" + str(_n)] = (lambda n=_n: advent_wreath(n))
    XMAS["advent_wreath_wall_" + str(_n)] = (lambda n=_n: advent_wreath_wall(n))
for _suffix, _col in (("", "xred"), ("_green", "xgreen"), ("_blue", "xblue"), ("_pink", "xpink"), ("_gold", "gold")):
    XMAS["candy_cane_giant" + _suffix] = (lambda c=_col: candy_cane_giant(c))

# ------------------------------------------------------------------ New Year (collection 3)


def launcher():
    e = [box(2, 0, 2, 14, 2, 14, "brass_d"), box(3, 2, 3, 13, 3, 13, "wood_warm_d")]
    for x, z, h in ((4, 4, 9), (9, 4, 11), (4, 9, 10), (9, 9, 8)):
        e += [box(x, 3, z, x + 3, h, z + 3, "copper"), box(x + 0.5, h, z + 0.5, x + 2.5, h + 0.3, z + 2.5, "char"),
              box(x - 0.1, h - 1, z - 0.1, x + 3.1, h - 0.5, z + 3.1, "brass")]
    e += [box(13.5, 2, 7, 15, 4, 9, "brass"), box(14.4, 4, 7.5, 14.8, 7, 8.5, "lantern_red")]   # fuse lever
    return e


def countdown_clock():
    """Steampunk standing clock (two blocks tall)."""
    e = [box(3, 0, 4, 13, 2, 12, "wood_warm_d"), box(4, 2, 5, 12, 20, 11, "wood_warm"), box(3, 20, 4, 13, 30, 12, "wood_warm"),
         box(4, 30, 5, 12, 31.5, 11, "brass"), box(7, 31.5, 7, 9, 32, 9, "brass_d"),
         box(4.5, 21, 3.9, 11.5, 29, 4.0, "brass"), box(5.2, 21.7, 3.85, 10.8, 28.3, 3.9, "dial"),
         box(7.8, 25, 3.8, 8.2, 28, 3.85, "coal"), box(8, 24.8, 3.8, 10.2, 25.2, 3.85, "coal"),          # hands: nearly midnight
         box(5, 4, 4.9, 11, 18, 5.0, "glass"), box(7.7, 8, 7.5, 8.3, 18, 8.5, "brass"), box(6.5, 6, 7.4, 9.5, 9, 8.6, "brass_l"),  # pendulum
         box(11.5, 22, 4.5, 13, 24, 6, "copper_d"), box(3, 25, 4.5, 4.5, 27, 6, "copper_d")]                      # gears
    for i in range(12):
        import math as _m
        a = i * _m.pi / 6
        x, y = 8 + _m.sin(a) * 2.4, 25 + _m.cos(a) * 2.4
        e.append(box(x - 0.2, y - 0.2, 3.8, x + 0.2, y + 0.2, 3.85, "coal"))
    return e


def countdown_board(wall):
    """Big steampunk scoreboard, 3 blocks wide and ~2 tall: brass frame round a black panel, gears in
    the corners, pipes with valves on top, two lamps. Wall version hangs flush on the back (z = 16); the floor
    version stands tall on two legs so players can walk underneath it, like the torii gate. The plugin lays three
    text lines on the panel (see NewYearBoards; the floor lines ride 2.6 blocks higher with the panel).

    Coordinates are in VANILLA MODEL UNITS (16 = 1 block), like every other relic. History: the first
    version wrote block-sized numbers into unit fields (rendered ~48 blocks wide), the 0.3.4 'fix'
    then multiplied by 1 in the wrong direction and shrank everything (rendered 0.2 blocks). This is
    the same frame as before, honestly in units: 48 wide x ~30 panel, legs to 2.6 blocks, total ~5.4."""
    zb = 16.0 if wall else 12.0
    e = [box(-16, 0, zb - 4.8, 32, 3.84, zb, "brass"),           # bottom rail
         box(-16, 28.16, zb - 4.8, 32, 30.08, zb, "brass"),      # top rail
         box(-16, 3.84, zb - 4.8, -9.6, 30.08, zb, "brass"),     # left frame column
         box(28.8, 3.84, zb - 4.8, 32, 30.08, zb, "brass"),      # right frame column
         box(-12, 3.84, zb - 5.76, 28, 28.16, zb - 1.28, "lacquer_black"),   # black panel
         box(-15.2, 12.8, zb - 3.2, -9.6, 23.2, zb, "copper"),   # gears in the four corners
         box(-15.2, 2.4, zb - 3.2, -9.6, 9.6, zb, "copper"),
         box(28.8, 12.8, zb - 3.2, 32, 23.2, zb, "copper"),
         box(28.8, 2.4, zb - 3.2, 32, 9.6, zb, "copper"),
         box(-11.2, 30.08, zb - 2.4, 0, 36, zb, "copper_d"),     # pipe along the top with a valve
         box(4.8, 30.08, zb - 2.4, 9.6, 36, zb, "copper_d"),
         box(6.4, 36, zb - 2.4, 11.2, 41.6, zb, "brass"),
         box(7.2, 40, zb - 2.4, 10.4, 44.8, zb, "dial"),
         box(-16, 3.2, zb - 2.4, -11.2, 11.2, zb, "flame_core"),  # lamps either side of the panel
         box(27.2, 3.2, zb - 2.4, 32, 11.2, zb, "flame_core")]
    if wall:
        return e
    e = shift(e, 0, 41.6)   # stand it tall on two legs (2.6 blocks) so players can walk underneath
    return e + [box(-13.6, 0, 12, -10.4, 41.92, 15.2, "wood_warm_d"),  # the two legs
                box(26.4, 0, 12, 29.6, 41.92, 15.2, "wood_warm_d")]


def temple_bell():
    """Joya no kane: a bronze temple bell in a wooden frame, with its striking log (two blocks tall)."""
    return [box(0, 0, 6, 2, 30, 10, "wood_warm_d"), box(14, 0, 6, 16, 30, 10, "wood_warm_d"), box(-1, 28, 5, 17, 31, 11, "wood_warm"),
            box(-1.5, 31, 4.5, 17.5, 32, 11.5, "xred_d"), box(7.5, 26, 7.5, 8.5, 28, 8.5, "chain"),
            box(4, 12, 4, 12, 26, 12, "bronze"), box(3.5, 12, 3.5, 12.5, 14, 12.5, "bronze_d"), box(5, 26, 5, 11, 27, 11, "bronze_d"),
            box(4.5, 17, 3.9, 11.5, 18, 4.0, "bronze_l"), box(7, 14, 3.9, 9, 16, 4.0, "bronze_l"),
            box(7.4, 16, -6, 8.6, 17.2, 3, "wood_old"), box(7.6, 17.2, -5, 8.4, 27.5, -4.2, "iron_d")]   # striking log on ropes


def kagami_mochi():
    return [box(3, 0, 3, 13, 2, 13, "wood_warm"), box(2.5, 2, 2.5, 13.5, 2.5, 13.5, "paper_white"), box(4, 2.5, 4, 12, 6, 12, "mochi" if "mochi" in PALETTE else "icing"),
            box(5, 6, 5, 11, 9, 11, "icing"), box(6.5, 9, 6.5, 9.5, 11.5, 9.5, "carrot"), box(7.6, 11.5, 7.6, 8.4, 12, 8.4, "xgreen"),
            box(2.4, 2, 7.4, 2.6, 6, 8.6, "lantern_red")]


def shimenawa(wall=True):
    z = 15 if wall else 7
    e = [box(1, 12, z, 15, 14, z + 1.5, "straw"), box(1, 12.6, z - 0.01, 15, 13.2, z + 1.51, "straw_d")]
    for x in (3, 7, 11):
        e += [box(x, 9, z + 0.5, x + 1.2, 12, z + 0.6, "paper_white"), box(x + 0.6, 7, z + 0.5, x + 1.8, 9.5, z + 0.6, "paper_white")]
    for x in (5, 9, 13):
        e.append(box(x, 9.5, z + 0.5, x + 0.6, 12, z + 1.0, "straw_d"))
    return e


def red_lantern(y=0, rope=False):
    e = [box(4.5, y + 1, 4.5, 11.5, y + 11, 11.5, "lantern_red"), box(5, y + 0, 5, 11, y + 1, 11, "coal"), box(5, y + 11, 5, 11, y + 12, 11, "coal"),
         box(4.4, y + 3, 4.4, 11.6, y + 3.4, 11.6, "lantern_red_d"), box(4.4, y + 6, 4.4, 11.6, y + 6.4, 11.6, "lantern_red_d"),
         box(4.4, y + 9, 4.4, 11.6, y + 9.4, 11.6, "lantern_red_d"), box(7.6, y - 2, 7.6, 8.4, y, 8.4, "gold"), box(7, y - 3, 7, 9, y - 2, 9, "gold")]
    if rope:
        e.append(box(7.7, y + 12, 7.7, 8.3, 16, 8.3, "coal"))
    return e


def confetti_cannon():
    return [box(4, 0, 4, 12, 3, 12, "brass_d"), box(5.5, 3, 5.5, 10.5, 11, 10.5, "lantern_red"), box(5, 10, 5, 11, 11.5, 11, "gold"),
            box(6, 3.5, 5.4, 10, 4.5, 5.5, "gold"), box(6.5, 11.5, 6.5, 7.3, 12.3, 7.3, "confetti_pink"), box(8.5, 11.5, 8, 9.3, 12.3, 8.8, "confetti_cyan"),
            box(7.4, 11.5, 9, 8.2, 12.3, 9.8, "gold_l")]


def champagne_tower(full):
    """Cider pyramid. Built at S=1.75 so the whole tower reads as a real centrepiece (~1.4 blocks
    tall); the raw glass rows below are in the original 12.6-unit design. The plugin counts taken
    glasses and swaps to _half (6) / _empty (0)."""
    S = 1.75
    def sc(x1, y1, z1, x2, y2, z2, t):
        return box(8 + (x1 - 8) * S, y1 * S, 8 + (z1 - 8) * S, 8 + (x2 - 8) * S, y2 * S, 8 + (z2 - 8) * S, t)
    e = [sc(2, 0, 2, 14, 0.6, 14, "brass")]
    rows = [(3, 0.6, [(4, 4), (8, 4), (12, 4), (4, 8), (8, 8), (12, 8), (4, 12), (8, 12), (12, 12)]), (2, 4.6, [(6, 6), (10, 6), (6, 10), (10, 10)]), (1, 8.6, [(8, 8)])]
    k = 0
    for _, y, spots in rows:
        for x, z in spots:
            e += [sc(x - 0.3, y, z - 0.3, x + 0.3, y + 2, z + 0.3, "glass"), sc(x - 1.2, y + 2, z - 1.2, x + 1.2, y + 4, z + 1.2, "glass")]
            if k < full:
                e.append(sc(x - 1.0, y + 2.2, z - 1.0, x + 1.0, y + 3.6, z + 1.0, "fizz"))
            k += 1
    return e


def garland(on):
    e = []
    for i in range(8):
        x = 1 + i * 2
        sag = [0, 0.8, 1.2, 0.8][i % 4]
        e.append(box(x, 12 - sag, 15.4, x + 2, 12.4 - sag, 15.7, "gold"))
        if i % 2 == 0:
            col = "gold_l" if on else "gold"
            e += [box(x + 0.5, 10.4 - sag, 15.3, x + 1.5, 11.4 - sag, 15.6, col), box(x + 0.2, 10.7 - sag, 15.3, x + 1.8, 11.1 - sag, 15.6, col)]
        else:
            e.append(box(x + 0.8, 8 - sag, 15.3, x + 1.2, 12 - sag, 15.5, ["confetti_pink", "confetti_cyan", "lantern_red"][i % 3]))
    return e


NEWYEAR = {
    "countdown_board": lambda: countdown_board(False), "countdown_board_wall": lambda: countdown_board(True),
    "firework_launcher": launcher, "countdown_clock": countdown_clock, "temple_bell": temple_bell, "kagami_mochi": kagami_mochi,
    "shimenawa": lambda: shimenawa(True), "shimenawa_ceiling": lambda: shimenawa(False),
    "red_paper_lantern": lambda: red_lantern(3), "red_paper_lantern_hanging": lambda: red_lantern(2, True),
    "confetti_cannon": confetti_cannon, "champagne_tower": lambda: champagne_tower(14), "champagne_tower_half": lambda: champagne_tower(6),
    "champagne_tower_empty": lambda: champagne_tower(0), "new_year_garland": lambda: garland(True), "new_year_garland_b": lambda: garland(False),
    "sparkler": lambda: [box(7.7, 0, 7.7, 8.3, 9, 8.3, "iron_d"), box(7.5, 9, 7.5, 8.5, 14, 8.5, "ash"), box(7.2, 13, 7.2, 8.8, 14.5, 8.8, "flame_core")],
    "sparkling_cider": lambda: [box(6.5, 0, 6.5, 9.5, 8, 9.5, "xgreen_d"), box(7.2, 8, 7.2, 8.8, 11, 8.8, "xgreen_d"), box(7, 11, 7, 9, 12.5, 9, "gold"),
                                box(6.4, 3, 6.4, 9.6, 6, 9.6, "paper_white")],
    "oliebol": lambda: [box(5, 0, 5, 11, 5, 11, "dough"), box(6, 5, 6, 10, 6, 10, "dough_d"), box(5.5, 5.2, 5.5, 10.5, 5.8, 10.5, "icing"),
                        box(4.9, 2, 7, 5.0, 3, 8, "coal"), box(9, 4.9, 4.9, 10, 5.0, 5.0, "coal")],
    "toshikoshi_soba": lambda: [box(4, 0, 4, 12, 1, 12, "lantern_red_d"), box(3, 1, 3, 13, 5, 4, "lantern_red_d"), box(3, 1, 12, 13, 5, 13, "lantern_red_d"),
                                box(3, 1, 4, 4, 5, 12, "lantern_red_d"), box(12, 1, 4, 13, 5, 12, "lantern_red_d"), box(4, 1, 4, 12, 4.2, 12, "cocoa")]
                               + [box(4.5, 4.2, 5 + i * 1.2, 11.5, 4.4, 5.6 + i * 1.2, "soba") for i in range(6)]
                               + [box(9, 4.4, 5, 11, 4.8, 7, "xgreen"), box(5, 4.4, 8, 7.5, 5, 10, "tempura" if "tempura" in PALETTE else "ginger")],
}

# ------------------------------------------------------------------ Easter


def egg(col, col2):
    return [box(6, 0, 6, 10, 1, 10, col), box(5.5, 1, 5.5, 10.5, 5, 10.5, col), box(6, 5, 6, 10, 7, 10, col), box(6.8, 7, 6.8, 9.2, 7.8, 9.2, col),
            box(5.45, 2.5, 5.45, 10.55, 3.3, 10.55, col2), box(5.95, 5.2, 5.95, 10.05, 5.6, 10.05, col2)]


def egg_basket():
    e = [box(3, 0, 3, 13, 1, 13, "wicker"), box(2.5, 1, 2.5, 13.5, 5, 3.5, "wicker"), box(2.5, 1, 12.5, 13.5, 5, 13.5, "wicker"),
         box(2.5, 1, 3.5, 3.5, 5, 12.5, "wicker"), box(12.5, 1, 3.5, 13.5, 5, 12.5, "wicker"), box(7.4, 5, 2.6, 8.6, 11, 3.4, "wicker_d"),
         box(7.4, 5, 12.6, 8.6, 11, 13.4, "wicker_d"), box(7.4, 10.4, 3.4, 8.6, 11.2, 12.6, "wicker_d"), box(3.5, 3, 3.5, 12.5, 4, 12.5, "straw")]
    for (x, z), c in zip(((4.5, 4.5), (8, 5), (5, 8.5), (9, 9)), ("egg_pink", "egg_blue", "egg_yellow", "egg_lilac")):
        e += [box(x, 4, z, x + 2.6, 7, z + 2.6, c), box(x + 0.3, 7, z + 0.3, x + 2.3, 7.8, z + 2.3, c)]
    return e


def clockwork_bunny():
    """A brass clockwork rabbit with a wind-up key."""
    return [box(4.5, 0, 5, 11.5, 6, 12, "brass"), box(5.5, 6, 4, 10.5, 10, 8, "brass"), box(6, 10, 5.5, 7.2, 15, 6.5, "brass_l"),
            box(8.8, 10, 5.5, 10, 15, 6.5, "brass_l"), box(6.2, 11, 5.4, 7, 14, 5.5, "egg_pink"), box(9, 11, 5.4, 9.8, 14, 5.5, "egg_pink"),
            box(6.2, 8, 3.9, 7, 8.8, 4.0, "coal"), box(9, 8, 3.9, 9.8, 8.8, 4.0, "coal"), box(7.5, 6.8, 3.7, 8.5, 7.4, 4.0, "egg_pink"),
            box(7, 1, 12, 9, 3, 13.5, "icing"), box(7.6, 3, 11.5, 8.4, 6, 12.2, "copper"), box(6.5, 5, 12, 9.5, 6, 13, "copper_d"),
            box(4, 0, 5.5, 5, 1.5, 7.5, "brass_d"), box(11, 0, 5.5, 12, 1.5, 7.5, "brass_d"), box(5.2, 2, 7, 5.3, 4, 10, "copper_d")]


def blossom_vase():
    e = [box(6, 0, 6, 10, 6, 10, "porcelain_w"), box(6.5, 6, 6.5, 9.5, 8, 9.5, "porcelain_w"), box(5.9, 2, 5.9, 10.1, 3, 10.1, "bonsai_pot"),
         box(7.6, 8, 7.6, 8.4, 15, 8.4, "wood_char"), box(8.4, 12, 7.6, 11, 12.8, 8.4, "wood_char"), box(5, 14, 7.6, 7.6, 14.8, 8.4, "wood_char")]
    for x, y, z in ((10.5, 12.5, 8), (11, 13.5, 7.5), (8.5, 15, 8), (4.8, 14.5, 7.8), (5.5, 15.3, 8.4), (7, 13, 9), (9.6, 14, 7)):
        e.append(box(x - 0.7, y - 0.7, z - 0.7, x + 0.7, y + 0.7, z + 0.7, "blossom"))
    return e


def egg_tree():
    e = [box(7, 0, 7, 9, 2, 9, "wood_warm_d"), box(7.5, 2, 7.5, 8.5, 14, 8.5, "wood_char"), box(2, 8, 7.6, 14, 8.6, 8.4, "wood_char"),
         box(7.6, 11, 3, 8.4, 11.6, 13, "wood_char"), box(4, 12, 7.6, 12, 12.5, 8.4, "wood_char")]
    for (x, y, z), c in zip(((2.5, 6, 8), (13, 6.2, 8), (8, 9.2, 3.5), (8, 9.3, 12.5), (4.5, 10.3, 8), (11.5, 10.3, 8)),
                            ("egg_pink", "egg_blue", "egg_yellow", "egg_lilac", "egg_blue", "egg_pink")):
        e += [box(x - 0.1, y + 1.4, z - 0.1, x + 0.1, y + 2.4, z + 0.1, "coal"), box(x - 0.8, y, z - 0.8, x + 0.8, y + 1.6, z + 0.8, c)]
    return e

# ------------------------------------------------------------------ St. Patrick's Day


def pot_of_gold():
    e = [box(4, 0, 4, 12, 1, 12, "coal"), box(3, 1, 3, 13, 7, 13, "coal"), box(2.5, 6, 2.5, 13.5, 7.5, 13.5, "coal"),
         box(3.5, 7, 3.5, 12.5, 8.5, 12.5, "gold"), box(5, 8.5, 5, 11, 9.5, 11, "gold_l"), box(7, 9.5, 7, 9, 10, 9, "gold")]
    for x, y, z in ((2, 0, 11), (13, 0, 4), (12, 0, 12)):
        e.append(box(x, y, z, x + 1.6, y + 0.4, z + 1.6, "gold"))
    return e


def rainbow_arch():
    """A little rainbow arching over a cloud (two blocks wide when drawn)."""
    e = [box(-6, 0, 6, 0, 3, 10, "snow"), box(16, 0, 6, 22, 3, 10, "snow")]
    cols = ["rainbow_r", "rainbow_o", "rainbow_y", "rainbow_g", "rainbow_b", "rainbow_v"]
    for i, c in enumerate(cols):
        w = 1.2
        lo, hi = -5 + i * w, 21 - i * w
        top = 18 - i * w
        e += [box(lo, 2, 7.4, lo + w, top - 3, 8.6, c), box(hi - w, 2, 7.4, hi, top - 3, 8.6, c),
              box(lo + w * 0.6, top - 3, 7.4, hi - w * 0.6, top - 3 + w, 8.6, c)]
    return e


def shamrock_planter():
    e = [box(2, 0, 4, 14, 5, 12, "wood_warm"), box(1.5, 4.5, 3.5, 14.5, 5.5, 12.5, "wood_warm_d"), box(2.5, 5, 4.5, 13.5, 5.5, 11.5, "dirt")]
    for x, z in ((4, 6), (8, 8), (11, 6), (6, 10), (11, 10)):
        e += [box(x, 5.5, z, x + 0.3, 8, z + 0.3, "shamrock_d")]
        for dx, dz in ((-0.9, 0), (0.6, 0), (-0.15, -0.9)):
            e.append(box(x + dx, 8, z + dz, x + dx + 0.9, 8.3, z + dz + 0.9, "shamrock"))
    return e


def leprechaun_hat():
    return [box(2, 0, 2, 14, 1, 14, "hat_green"), box(4, 1, 4, 12, 9, 12, "hat_green"), box(3.9, 1.5, 3.9, 12.1, 3, 12.1, "coal"),
            box(6.5, 1.4, 3.8, 9.5, 3.1, 3.9, "gold"), box(7.2, 1.9, 3.7, 8.8, 2.6, 3.8, "coal"), box(9, 3, 3.8, 10.5, 5, 3.9, "shamrock")]


def celtic_harp():
    e = [box(4, 0, 6, 12, 2, 10, "wood_warm_d"), box(4, 2, 7, 6, 22, 9, "wood_warm"), box(6, 20, 7, 14, 23, 9, "wood_warm"),
         box(12, 2, 7.2, 13.5, 21, 8.8, "wood_warm"), box(5, 22, 7.5, 7, 24, 8.5, "gold")]
    for i in range(7):
        x = 6.4 + i * 0.85
        e.append(box(x, 3 + i * 1.6, 7.9, x + 0.12, 20, 8.1, "gold_l"))
    e += [box(4.1, 8, 6.9, 5.9, 10, 7.0, "shamrock"), box(4.1, 14, 6.9, 5.9, 15, 7.0, "gold")]
    return e


def bone_xylophone():
    """Xylophone of bones: a dark wooden frame, eleven bone bars getting shorter, two femur mallets, a little skull on top."""
    e = [box(1, 0, 3, 3, 8, 5, "wood_char"), box(13, 0, 3, 15, 8, 5, "wood_char"), box(1, 0, 11, 3, 8, 13, "wood_char"), box(13, 0, 11, 15, 8, 13, "wood_char"),
         box(1.5, 7, 3, 3, 8.5, 13, "wood_char_d"), box(13, 7, 3, 14.5, 8.5, 13, "wood_char_d")]
    for i in range(11):
        x = 3.2 + i * 0.95
        ln = 10 - i * 0.55
        e.append(box(x, 8.5, 8 - ln / 2, x + 0.8, 9.6, 8 + ln / 2, "bone" if i % 2 else "bone_old"))
        e.append(box(x + 0.3, 9.6, 7.6, x + 0.5, 9.8, 7.8, "char"))
    e += [box(3, 9.7, 12.2, 3.8, 10, 15, "bone"), box(2.6, 9.6, 14.6, 4.2, 10.4, 16, "bone_old"),
          box(12, 9.7, 12.2, 12.8, 10, 15, "bone"), box(11.6, 9.6, 14.6, 13.2, 10.4, 16, "bone_old"),
          box(6.5, 8.5, 1.5, 9.5, 11, 4.5, "bone"), box(7, 8.8, 4.45, 7.8, 9.6, 4.55, "char"), box(8.2, 8.8, 4.45, 9, 9.6, 4.55, "char")]
    return e


def green_keg():
    return [box(3, 0, 3, 13, 12, 13, "wood_warm"), box(2.8, 1, 2.8, 13.2, 2.2, 13.2, "iron_d"), box(2.8, 9.8, 2.8, 13.2, 11, 13.2, "iron_d"),
            box(4, 12, 4, 12, 12.3, 12, "wood_warm_d"), box(7.4, 4, 2.2, 8.6, 5.2, 3, "brass"), box(6, 6, 2.9, 10, 9, 3, "shamrock")]

# ------------------------------------------------------------------ Curiosities (steampunk + Japanese, all year)


def telescope():
    # 13-11 (owner): te klein. Full-height brass instrument on a wide tripod: the tube rises
    # through most of the block (stacked steps: only 22.5/45 deg single-axis turns are allowed,
    # so a shallow diagonal is drawn as a staircase - invisible at 16 px scale).
    return [box(5, 0, 5, 11, 0.7, 11, "brass_d"),                      # base plate
            box(6.9, 0.7, 6.9, 9.1, 9.0, 9.1, "brass_d"),              # central column
            box(3.4, 0.7, 7.6, 4.6, 1.3, 8.4, "brass"),                # tripod foot west
            box(11.4, 0.7, 7.6, 12.6, 1.3, 8.4, "brass"),              # foot east
            box(7.6, 0.7, 3.4, 8.4, 1.3, 4.6, "brass"),                # foot north
            box(7.6, 0.7, 11.4, 8.4, 1.3, 12.6, "brass"),              # foot south
            box(6.3, 8.2, 6.3, 9.7, 9.8, 9.7, "brass"),                # cradle
            box(4.5, 9.4, 7.4, 6.1, 10.8, 8.6, "brass"),               # tube 1 (objective end)
            box(5.9, 10.3, 7.4, 7.9, 11.9, 8.6, "brass_l"),            # tube 2
            box(7.7, 11.4, 7.4, 9.7, 13.0, 8.6, "brass"),              # tube 3
            box(9.5, 12.5, 7.4, 11.6, 14.1, 8.6, "brass_l"),           # tube 4 (eyepiece end)
            box(11.2, 13.3, 7.2, 12.6, 14.7, 8.8, "copper"),           # eyepiece
            box(3.9, 8.8, 7.1, 4.7, 11.4, 8.9, "copper_d"),            # objective ring
            box(6.7, 9.0, 6.1, 7.1, 10.2, 6.5, "coal"),                # finder scope post
            box(6.3, 10.0, 5.6, 7.9, 10.8, 6.6, "coal")]               # finder scope


def gauge_panel(b=False):
    e = [box(1, 2, 15, 15, 14, 16, "wood_warm_d"), box(1.5, 2.5, 14.6, 14.5, 13.5, 15, "brass_d")]
    for cx, cy in ((4.5, 9.5), (11.5, 9.5), (8, 5)):
        e += [box(cx - 2.6, cy - 2.6, 14.3, cx + 2.6, cy + 2.6, 14.6, "brass"), box(cx - 2.1, cy - 2.1, 14.2, cx + 2.1, cy + 2.1, 14.3, "dial"),
              box(cx - 0.15, cy, 14.1, cx + 0.15, cy + (1.8 if not b else 1.4), 14.2, "coal"),
              box(cx, cy - 0.15, 14.1, cx + (1.5 if b else 0.6), cy + 0.15, 14.2, "xred")]
    e += [box(13, 3, 14, 14, 4.5, 14.6, "copper"), box(2, 3, 14, 3, 4.5, 14.6, "copper")]
    return e


def tesla_coil():
    e = [box(4, 0, 4, 12, 3, 12, "iron_d"), box(6, 3, 6, 10, 14, 10, "coil"), box(5.5, 14, 5.5, 10.5, 15, 10.5, "iron")]
    for y in range(4, 14, 2):
        e.append(box(5.8, y, 5.8, 10.2, y + 0.6, 10.2, "copper_d"))
    e += [box(4.5, 15, 4.5, 11.5, 18, 11.5, "iron"), box(5, 18, 5, 11, 19, 11, "iron_d"), box(7.5, 19, 7.5, 8.5, 21, 8.5, "spark")]
    return e


def gramophone():
    return [box(3, 0, 4, 13, 4, 12, "wood_warm"), box(4, 4, 5, 12, 4.4, 11, "coal"), box(7, 4.4, 7, 9, 4.8, 9, "dial"),
            box(11, 4, 10, 12, 7, 11, "brass"), box(9, 6.5, 9, 12, 7.3, 10, "brass"), box(8, 7, 8, 10, 9, 10, "brass"),
            box(6, 9, 6, 12, 11, 12, "brass"), box(4, 11, 4, 14, 14, 14, "brass_l"), box(5, 11.2, 5, 13, 14.1, 13, "brass_d"),
            box(13, 1.5, 7.5, 14.5, 2.5, 8.5, "brass")]


def torii():
    """Torii gate, built at 2/3 size (drawn 1.5x: three blocks wide and high)."""
    return [box(-5, 0, 6.5, -2.5, 26, 9.5, "torii"), box(18.5, 0, 6.5, 21, 26, 9.5, "torii"), box(-5.5, 0, 6, -2, 2, 10, "lacquer_black"),
            box(18, 0, 6, 21.5, 2, 10, "lacquer_black"), box(-6, 20, 7, 22, 22, 9, "torii"), box(-8, 27, 6, 24, 29, 10, "torii"),
            box(-8.5, 29, 5.5, 24.5, 30.5, 10.5, "lacquer_black"), box(7, 22, 7.2, 9, 27, 8.8, "torii"), box(6, 23, 6.8, 10, 26, 7.2, "lacquer_black")]


def maneki_neko(waving):
    paw_y = 12 if waving else 10.5
    return [box(4.5, 0, 6, 11.5, 8, 11, "cat_white"), box(5, 8, 5.5, 11, 13, 10.5, "cat_white"), box(5, 13, 6.5, 6.5, 14.5, 8, "cat_white"),
            box(9.5, 13, 6.5, 11, 14.5, 8, "cat_white"), box(5.3, 13, 6.4, 6.2, 14, 6.5, "egg_pink"), box(9.8, 13, 6.4, 10.7, 14, 6.5, "egg_pink"),
            box(6.2, 10.5, 5.4, 7.2, 11.2, 5.5, "coal"), box(8.8, 10.5, 5.4, 9.8, 11.2, 5.5, "coal"), box(5.5, 7.5, 5.4, 10.5, 8.2, 5.6, "xred"),
            box(7.4, 6.6, 5.2, 8.6, 7.8, 5.5, "gold"), box(4, paw_y - 4, 6.5, 5.5, paw_y, 8, "cat_white"), box(4, paw_y - 0.5, 6.3, 5.5, paw_y, 6.5, "egg_pink"),
            box(10.5, 3, 5.5, 12, 6, 7, "cat_white"), box(6, 0, 5.5, 10, 4, 6, "gold_l")]


def shishi_odoshi(tipped):
    e = [box(1, 0, 3, 15, 2, 13, "stone"), box(2, 2, 4, 14, 2.4, 12, "glass"), box(3, 2, 7, 4, 9, 8, "bamboo"), box(10, 2, 7, 11, 9, 8, "bamboo"),
         box(12, 2, 9.5, 13.5, 14, 11, "bamboo"), box(12.6, 12, 6, 13.2, 13, 9.5, "bamboo_d"), box(4, 7.5, 7.2, 10, 8.2, 7.8, "bamboo_d"),
         box(2, 2, 2.5, 4, 3, 4.5, "stone_d")]
    if tipped:   # the spout tips down and empties
        e += [box(1.5, 3, 7, 7, 5, 8, "bamboo"), box(7, 5, 7, 12, 7, 8, "bamboo")]
    else:
        e += [box(3, 8, 7, 8, 10, 8, "bamboo"), box(8, 6, 7, 12.5, 8, 8, "bamboo")]
    return e


def bonsai():
    return [box(3, 0, 5, 13, 3, 11, "bonsai_pot"), box(3.5, 2.8, 5.5, 12.5, 3.2, 10.5, "dirt"), box(7.5, 3, 7.5, 8.5, 7, 8.5, "wood_char"),
            box(8.5, 6, 7.6, 11, 7, 8.4, "wood_char"), box(5, 7, 7.6, 7.6, 8, 8.4, "wood_char"), box(9, 7, 6, 13, 9, 10, "pine"),
            box(3.5, 8, 6.5, 7.5, 10, 9.5, "pine"), box(6, 9.5, 6, 10, 12, 10, "pine"), box(10.5, 3, 9, 11.5, 3.6, 10, "stone")]


def byobu():
    """Folding paper screen - a ROOM DIVIDER, so it has to hide a standing player: four panels of
    8 units wide, 26 tall (= 2.0 x 1.63 blocks). Was 6x18 (1.5 x 1.13) which read as a low table."""
    e = []
    for i, x in enumerate((-4, 4, 12, 20)):
        z = 7 if i % 2 == 0 else 9
        e += [box(x, 0, z, x + 8, 26, z + 0.6, "lacquer_black"), box(x + 0.8, 0.8, z - 0.05, x + 7.2, 25, z + 0.65, "washi")]
    e += [box(-2, 13, 6.9, 4, 19, 7, "blossom"), box(9, 6, 6.9, 15, 13, 7, "shamrock_d"), box(16, 16, 8.9, 22, 22, 9, "gold")]
    return e


def wagasa_stand():
    e = [box(4, 0, 4, 12, 1, 12, "wood_warm_d"), box(4, 1, 4, 5, 9, 5, "wood_warm"), box(11, 1, 4, 12, 9, 5, "wood_warm"),
         box(4, 1, 11, 5, 9, 12, "wood_warm"), box(11, 1, 11, 12, 9, 12, "wood_warm"), box(4, 8, 4, 12, 9, 12, "wood_warm_d")]
    for x, z, c in ((6, 6, "umbrella_red"), (9, 7, "umbrella_blue"), (7, 10, "washi")):
        e += [box(x - 0.3, 1, z - 0.3, x + 0.3, 15, z + 0.3, "wood_warm"), box(x - 1.2, 9, z - 1.2, x + 1.2, 15, z + 1.2, c), box(x - 0.6, 15, z - 0.6, x + 0.6, 16, z + 0.6, "coal")]
    return e


MORE = {
    "easter_egg": lambda: egg("egg_pink", "egg_blue"), "easter_egg_blue": lambda: egg("egg_blue", "egg_yellow"), "easter_egg_yellow": lambda: egg("egg_yellow", "egg_lilac"),
    "egg_basket": egg_basket, "clockwork_bunny": clockwork_bunny, "blossom_vase": blossom_vase, "egg_tree": egg_tree,
    "chocolate_bunny": lambda: [box(5, 0, 6, 11, 7, 11, "chocolate"), box(6, 7, 6.5, 10, 10, 10, "chocolate"), box(6.5, 10, 7.5, 7.5, 14, 8.5, "chocolate"),
                                box(8.5, 10, 7.5, 9.5, 14, 8.5, "chocolate"), box(4.9, 2, 7, 5, 4, 10, "gold")],
    "hot_cross_bun": lambda: [box(4, 0, 4, 12, 4, 12, "dough"), box(5, 4, 5, 11, 5, 11, "dough_l"), box(7.5, 5, 4.5, 8.5, 5.3, 11.5, "icing"), box(4.5, 5, 7.5, 11.5, 5.3, 8.5, "icing")],
    "pot_of_gold": pot_of_gold, "rainbow_arch": rainbow_arch, "shamrock_planter": shamrock_planter, "leprechaun_hat": leprechaun_hat,
    "celtic_harp": celtic_harp, "bone_xylophone": bone_xylophone, "green_ale_keg": green_keg,
    "green_ginger_ale": lambda: [box(5.5, 0, 5.5, 10.5, 9, 10.5, "glass"), box(6, 0.5, 6, 10, 7.5, 10, "shamrock"), box(6, 7.5, 6, 10, 9, 10, "icing"), box(10.5, 3, 7.3, 12, 7, 8.7, "glass")],
    "soda_bread": lambda: [box(3, 0, 3, 13, 5, 13, "dough"), box(4, 5, 4, 12, 6.5, 12, "dough"), box(7.6, 5, 3.5, 8.4, 6.6, 12.5, "dough_d"), box(3.5, 5, 7.6, 12.5, 6.6, 8.4, "dough_d")],
    "shamrock_cookie": lambda: [box(4, 0, 4, 12, 1, 12, "dough"), box(5, 1, 7, 7.5, 1.3, 9.5, "shamrock"), box(8.5, 1, 7, 11, 1.3, 9.5, "shamrock"), box(6.8, 1, 4.6, 9.2, 1.3, 7, "shamrock")],
    "brass_telescope": telescope, "steam_gauge_panel": lambda: gauge_panel(False), "steam_gauge_panel_b": lambda: gauge_panel(True),
    "tesla_coil": tesla_coil, "gramophone": gramophone, "torii_gate": torii, "maneki_neko": lambda: maneki_neko(False), "maneki_neko_b": lambda: maneki_neko(True),
    "shishi_odoshi": lambda: shishi_odoshi(False), "shishi_odoshi_b": lambda: shishi_odoshi(True), "bonsai": bonsai, "byobu_screen": byobu, "wagasa_stand": wagasa_stand,
}

# ------------------------------------------------------------------ brewery & wooden helpers (0.3.9)

def sugi_dama():
    es = [box(7.2, 21, 7.2, 8.8, 24, 8.8, "chain"),                       # cord
          box(1.5, 11, 1.5, 14.5, 19, 14.5, "pine"),
          box(3.5, 19, 3.5, 12.5, 20.8, 12.5, "pine"),
          box(3.5, 9.2, 3.5, 12.5, 11, 12.5, "pine"),
          box(0.6, 13.5, 3.5, 1.5, 16.5, 12.5, "pine"),
          box(14.5, 13.5, 3.5, 15.4, 16.5, 12.5, "pine"),
          box(3.5, 13.5, 0.6, 12.5, 16.5, 1.5, "pine"),
          box(3.5, 13.5, 14.5, 12.5, 16.5, 15.4, "pine"),
          box(1.5, 13.6, 7.7, 14.5, 16.4, 8.3, "straw"),                   # straw seams
          box(7.7, 13.6, 1.5, 8.3, 16.4, 14.5, "straw"),
          box(7.3, 8.6, 7.3, 8.7, 9.4, 8.7, "straw")]
    return es


def koji_crock():
    return [box(3.5, 0, 3.5, 12.5, 8, 12.5, "clay_pot"),
            box(3, 8, 3, 13, 9.5, 13, "clay_pot"),
            box(2.6, 9.5, 2.6, 13.4, 10.6, 13.4, "wood_old"),
            box(3.6, 10.6, 3.6, 12.4, 11.4, 12.4, "washi"),
            box(3.2, 11.2, 3.2, 12.8, 11.9, 12.8, "straw")]


def bottling_crate():
    es = [box(1, 0, 1, 15, 1, 15, "wood_char"),
          box(1, 1, 1, 15, 7, 2, "wood_old"), box(1, 1, 14, 15, 7, 15, "wood_old"),
          box(1, 1, 2, 2, 7, 14, "wood_old"), box(14, 1, 2, 15, 7, 14, "wood_old"),
          box(1, 4, 1.2, 15, 5, 1.9, "wood_warm"), box(1, 4, 14.1, 15, 5, 14.8, "wood_warm")]
    for x, z in ((3.5, 3.5), (3.5, 9.5), (9.5, 3.5), (9.5, 9.5), (6.5, 6.5)):
        es += [box(x, 7, z, x + 2, 9.6, z + 2, "glass"),
               box(x + 0.6, 9.6, z + 0.6, x + 1.4, 11.4, z + 1.4, "glass"),
               box(x + 0.5, 11.4, z + 0.5, x + 1.5, 11.9, z + 1.5, "gold")]
    es.append(box(2.5, 6.4, 6.5, 3.8, 7.8, 7.8, "straw"))
    return es


def barrel_stack():
    es = []
    for ox in (0, 8):
        es += [box(ox + 1, 1, 3.5, ox + 7, 7, 9.5, "wood_warm"),
               box(ox + 0.8, 1.8, 3.3, ox + 7.2, 2.6, 9.7, "brass"),
               box(ox + 0.8, 5.4, 3.3, ox + 7.2, 6.2, 9.7, "brass"),
               box(ox + 0.6, 2.4, 3.6, ox + 1, 5.6, 9.4, "wood_old"),
               box(ox + 6.9, 2.4, 3.6, ox + 7.3, 5.6, 9.4, "wood_old")]
    es += [box(4.5, 7, 4.5, 11.5, 15, 11.5, "wood_warm"),
           box(4.2, 7.8, 4.2, 11.8, 8.6, 11.8, "brass"),
           box(4.2, 13.4, 4.2, 11.8, 14.2, 11.8, "brass"),
           box(5.2, 15, 5.2, 10.8, 15.7, 10.8, "wood_old")]
    return es


def steam_pipe():
    return [box(5, 0, 5, 11, 1.4, 11, "iron"),
            box(6, 1.4, 6, 10, 14, 10, "copper"),
            box(6, 14, 6, 8, 18, 8, "copper"),
            box(5.6, 17.6, 5.6, 8.4, 18.4, 8.4, "iron"),
            box(7.6, 7.8, 5.4, 8.4, 8.6, 6.2, "iron"),
            box(5.2, 7.5, 5, 10.8, 8.5, 5.6, "brass"),
            box(7.4, 5.6, 5, 8.6, 10.6, 5.6, "brass"),
            box(7.3, 7.5, 4.6, 8.7, 8.7, 5.2, "iron"),
            box(10, 10.4, 6.8, 11.2, 11.6, 9.2, "dial"),
            box(10.8, 10.8, 7.4, 11.4, 11.2, 7.8, "glow_red")]


def tasting_counter():
    es = [box(0, 0, 5, 16, 8, 11, "wood_warm"),
          box(0, 8, 4.4, 16, 9.4, 11.6, "wood_old"),
          box(1, 2, 4.7, 15, 3, 5.2, "wood_char"),
          box(1, 5, 4.7, 15, 6, 5.2, "wood_char")]
    for x in (2.5, 6, 9.5):
        es += [box(x, 9.4, 6.4, x + 1.7, 10.9, 7.9, "tokkuri"),
               box(x + 0.35, 10.6, 6.75, x + 1.35, 11.05, 7.55, "fluid")]
    es += [box(12.6, 9.4, 6, 14.6, 12.6, 8, "tokkuri"),
           box(13.1, 12.6, 6.5, 14.1, 13.8, 7.5, "tokkuri"),
           box(13.2, 13.8, 6.6, 14, 14.3, 7.4, "gold")]
    return es


def noren():
    return [box(0, 0, 7.6, 1, 16, 8.6, "wood_char"), box(15, 0, 7.6, 16, 16, 8.6, "wood_char"),
            box(0, 14.8, 6.8, 16, 16, 9.4, "wood_warm"),
            box(0.8, 2.5, 7.7, 8, 15, 8.5, "indigo"), box(8, 2.5, 7.7, 15.2, 15, 8.5, "indigo"),
            box(3.2, 8, 7.4, 5.2, 10.5, 8.8, "washi"), box(10.8, 8, 7.4, 12.8, 10.5, 8.8, "washi")]


def brew_kettle():
    return [box(3, 0, 3, 13, 3.5, 13, "brick"),
            box(4, 3.5, 4, 12, 4.2, 12, "char"),
            box(5, 3.9, 5, 11, 4.6, 11, "ember"),
            box(3.5, 4.6, 3.5, 12.5, 11, 12.5, "copper"),
            box(3.3, 6.5, 3.3, 12.7, 7.5, 12.7, "brass"),
            box(5, 11, 5, 11, 12, 11, "iron"),
            box(7.2, 12, 7.2, 8.8, 12.9, 8.8, "gold"),
            box(12.5, 7, 6.8, 14.8, 8.4, 9.2, "copper")]


def bottle_rack():
    es = [box(1, 0, 7.2, 15, 12.6, 7.8, "wood_char"),
          box(0.5, 0.9, 6.8, 15.5, 1.7, 9.6, "wood_old"),
          box(0.5, 7.4, 6.8, 15.5, 8.2, 9.6, "wood_old")]
    for i, x in enumerate((2, 5.2, 8.4, 11.6)):
        for shelf in (1.7, 8.2):
            es += [box(x, shelf, 7.7, x + 2, shelf + 4, 9.7, "tokkuri"),
                   box(x + 0.6, shelf + 4, 8.2, x + 1.4, shelf + 4.9, 9, "tokkuri")]
            if i % 2:
                es.append(box(x + 0.1, shelf + 1.2, 7.6, x + 1.9, shelf + 2.2, 9.8, "indigo"))
            else:
                es.append(box(x + 0.6, shelf + 4.9, 8.1, x + 1.4, shelf + 5.3, 9.1, "gold"))
    return es


def _doll_head(es):
    es += [box(6, 5.5, 6.2, 10, 11, 9.8, "wood_warm"),
           box(6.6, 11.6, 6.6, 9.4, 14.4, 9.4, "wood_warm"),
           box(7.1, 12.5, 6.2, 7.5, 13, 6.7, "coal"),
           box(8.5, 12.5, 6.2, 8.9, 13, 6.7, "coal"),
           box(6.4, 14, 6.3, 9.6, 15, 9.7, "xred")]
    return es


def _doll_legs(es):
    return es + [box(5.5, 0, 5.5, 10.5, 1, 10.5, "wood_old"),
                 box(6.6, 1, 6.7, 7.8, 5.5, 7.9, "wood_warm"),
                 box(8.2, 1, 6.7, 9.4, 5.5, 7.9, "wood_warm"),
                 box(7, 4.8, 6, 9, 5.6, 10, "wood_old")]


def greeter_doll(pose):
    es = _doll_legs([])
    es = _doll_head(es)
    es.append(box(4.8, 6.5, 7.4, 5.8, 10.6, 8.4, "wood_warm"))          # left arm down
    if pose == "a":
        es += [box(10.2, 9.6, 7.4, 13.6, 10.6, 8.4, "wood_warm"),       # right arm out, forearm up
               box(12.8, 10.4, 7.4, 13.8, 13, 8.4, "wood_warm"),
               box(12.6, 13, 7.2, 14, 13.8, 8.6, "cream")]
    else:
        es += [box(10.2, 9.6, 7.4, 11.2, 14, 8.4, "wood_warm"),         # right arm straight up (wave)
               box(10.2, 14, 7.2, 11.6, 14.9, 8.6, "cream"),
               box(4.6, 6.7, 7.2, 5.6, 8, 8.2, "wood_warm")]
    return es


def sweeper_doll(pose):
    es = _doll_legs([])
    es = _doll_head(es)
    if pose == "a":
        broom = [box(1.4, 0, 3.6, 4, 1.7, 6.2, "straw"),               # brush west
                 box(2.4, 1.6, 4.5, 3.2, 8.6, 5.3, "wood_old"),
                 box(3.1, 8.2, 4.8, 3.9, 11.6, 5.6, "wood_old")]
        arms = [box(4.8, 6.6, 6.8, 5.8, 10.7, 7.8, "wood_warm"),
                box(5.5, 8.4, 5.2, 7.2, 9.4, 7, "wood_warm")]          # hands on the handle
    else:
        broom = shift([box(1.4, 0, 3.6, 4, 1.7, 6.2, "straw"),
                       box(2.4, 1.6, 4.5, 3.2, 8.6, 5.3, "wood_old"),
                       box(3.1, 8.2, 4.8, 3.9, 11.6, 5.6, "wood_old")], dx=11.8)
        arms = [box(10.2, 6.6, 6.8, 11.2, 10.7, 7.8, "wood_warm"),
                box(8.8, 8.4, 5.2, 10.5, 9.4, 7, "wood_warm")]
    return es + broom + arms


def drunkard_barrel(pose):
    es = [box(3.5, 0, 3.5, 12.5, 7, 12.5, "wood_warm"),
          box(3.2, 0.8, 3.2, 12.8, 1.8, 12.8, "brass"),
          box(3.2, 5.2, 3.2, 12.8, 6.2, 12.8, "brass"),
          box(4.3, 7, 4.3, 11.7, 7.7, 11.7, "wood_old"),
          box(6, 7.7, 5.4, 9.6, 11.2, 9, "wood_warm"),                 # head
          box(6.5, 9.3, 5.1, 6.9, 9.8, 5.5, "coal"),
          box(8.5, 9.3, 5.1, 8.9, 9.8, 5.5, "coal"),
          box(6.2, 8.3, 5.1, 6.6, 8.9, 5.5, "xred"),
          box(8.8, 8.3, 5.1, 9.2, 8.9, 5.5, "xred"),
          box(2.6, 6.3, 7, 6.2, 7.3, 8.4, "wood_warm")]                # arm over the west rim
    if pose == "a":
        es += [box(10.8, 7.7, 7.3, 12.2, 10.3, 8.7, "glass"),          # standing bottle on the rim
               box(10.9, 7.9, 7.4, 12.1, 9.5, 8.6, "fluid"),
               box(11.3, 10.3, 7.7, 11.7, 11, 8.1, "glass")]
    else:
        es += [box(10, 6.8, 7.4, 14, 7.8, 8.4, "wood_warm"),           # arm raised, cup aloft
               box(13.2, 7.6, 7, 14, 9.2, 8.8, "wood_warm"),
               box(12.9, 9.2, 6.8, 14.3, 10.4, 8.2, "tokkuri"),
               box(13.1, 9.9, 7, 14.1, 10.5, 8, "fluid")]
    return es


BREWERY = {
    "sugi_dama": lambda: shift(sugi_dama(), dy=-8), "koji_crock": koji_crock, "bottling_crate": bottling_crate,
    "barrel_stack": barrel_stack, "steam_pipe": steam_pipe, "tasting_counter": tasting_counter,
    "noren": noren, "brew_kettle": brew_kettle, "bottle_rack": bottle_rack,
    "greeter_doll": lambda: greeter_doll("a"), "greeter_doll_b": lambda: greeter_doll("b"),
    "sweeper_doll": lambda: sweeper_doll("a"), "sweeper_doll_b": lambda: sweeper_doll("b"),
    "drunkard_barrel": lambda: drunkard_barrel("a"), "drunkard_barrel_b": lambda: drunkard_barrel("b"),
}

# ------------------------------------------------------------------ Valentine's (collection 4)


def heart_pix(x, y, z, s, col, d=2.0):
    """A little flat heart facing north at (x, y, z), s = half-width in units."""
    return [box(x - s, y, z, x - s / 2.6, y + s * 1.1, z + d, col), box(x + s / 2.6, y, z, x + s, y + s * 1.1, z + d, col),
            box(x - s * 0.72, y - s * 0.55, z, x + s * 0.72, y + s * 0.55, z + d, col),
            box(x - s * 0.3, y - s * 0.95, z, x + s * 0.3, y - s * 0.4, z + d, col)]


def heart_balloon():
    return [box(4.2, 9.4, 5.8, 7.4, 12.4, 10.2, "rose_red"), box(8.6, 9.4, 5.8, 11.8, 12.4, 10.2, "rose_red"),
            box(4.9, 7.6, 6.1, 11.1, 9.8, 9.9, "rose_red"),
            box(6.2, 6.4, 6.5, 9.8, 7.8, 9.5, "rose_deep"), box(7.2, 5.4, 6.9, 8.8, 6.6, 9.1, "rose_deep"),
            box(7.55, 4.6, 7.15, 8.45, 5.5, 8.85, "rose_deep"),
            box(4.6, 11.4, 5.5, 6, 12.4, 5.9, "blush"),
            box(7.5, 4, 7.1, 8.5, 4.8, 8.9, "gold"),
            box(7.8, 0, 7.8, 8.2, 4.2, 8.2, "lace_white")]


def rose_bell():
    e = [box(3, 0, 3, 13, 1.4, 13, "brass"), box(3.5, 1.4, 3.5, 12.5, 1.9, 12.5, "rose_gold"),
         box(7.5, 1.9, 7.5, 8.5, 6.6, 8.5, "rose_leaf"), box(8.5, 3.6, 7.4, 10.4, 4.4, 8.6, "rose_leaf"),
         box(6.6, 6.4, 6.6, 9.4, 9, 9.4, "rose_deep"), box(6.9, 9, 6.9, 9.1, 10, 9.1, "rose_red"),
         box(4.1, 1.9, 4, 11.9, 10.2, 4.5, "glass"), box(4.1, 1.9, 11.5, 11.9, 10.2, 12, "glass"),
         box(4, 1.9, 4.5, 4.5, 10.2, 11.5, "glass"), box(11.5, 1.9, 4.5, 12, 10.2, 11.5, "glass"),
         box(5.6, 10.2, 5.6, 10.4, 10.9, 10.4, "glass"), box(7, 10.9, 7, 9, 11.6, 9, "glass"),
         box(7.4, 11.6, 7.4, 8.6, 12.8, 8.6, "rose_gold")]
    return e


def love_lock_rail():
    e = [box(1, 1, 14.6, 15, 15, 15.6, "iron_d"),
         box(2.6, 1, 14.1, 3.4, 15, 14.7, "iron"), box(7.6, 1, 14.1, 8.4, 15, 14.7, "iron"),
         box(12.6, 1, 14.1, 13.4, 15, 14.7, "iron"), box(1, 7.2, 14.1, 15, 8, 14.7, "iron")]

    def lock(x, y, col):
        return [box(x, y, 13.4, x + 2.6, y + 2.4, 14.4, col), box(x + 0.6, y + 2.4, 13.7, x + 2, y + 3.5, 14.1, "iron")]
    e += lock(3.4, 3.4, "brass") + lock(9.2, 4.4, "rose_gold") + lock(5.6, 9.4, "copper") + lock(10.4, 9.9, "gold")
    e += heart_pix(4.7, 6.6, 13.15, 0.9, "rose_red")                                    # one heart-shaped lock
    return e


def heart_arch():
    e = [box(1, 0, 1, 3, 15.5, 3, "wood_warm"), box(1, 0, 13, 3, 15.5, 15, "wood_warm"),
         box(13, 0, 1, 15, 15.5, 3, "wood_warm"), box(13, 0, 13, 15, 15.5, 15, "wood_warm"),
         box(1, 15.5, 1, 15, 16.6, 3, "wood_warm_d"), box(1, 15.5, 13, 15, 16.6, 15, "wood_warm_d"),
         box(1, 15.5, 3, 3, 16.6, 13, "wood_warm_d"), box(13, 15.5, 3, 15, 16.6, 13, "wood_warm_d"),
         box(7.6, 16.6, 7.4, 8.4, 17.8, 8.2, "rose_leaf")]                              # vine holding the heart
    e += heart_pix(8, 20.2, 6.8, 2.6, "rose_red", d=2.6)
    for x, y in ((3.4, 14), (12.6, 13), (2.2, 8), (13.8, 6)):                            # roses climbing the posts
        e += [box(x, y, 1.6, x + 1.4, y + 1.3, 2.6, "rose_red"), box(x + 0.2, y + 1.3, 1.8, x + 1.1, y + 1.8, 2.4, "blush")]
    return e


def cupid_statue():
    e = [box(5, 0, 5, 11, 2.4, 11, "stone"), box(5.5, 2.4, 5.5, 10.5, 3.2, 10.5, "stone_l"),
         box(6.8, 3.2, 6.8, 9.2, 8, 9, "bone"), box(7, 8, 7, 9, 10.2, 8.8, "bone"),
         box(4.6, 4.8, 8.6, 6.4, 8.4, 9.6, "bone_l"), box(9.6, 4.8, 8.6, 11.4, 8.4, 9.6, "bone_l"),
         box(5, 5.6, 5.8, 6.8, 6.4, 7, "bone"),                                          # left arm at the bow
         box(4.3, 3.8, 5.9, 4.8, 7.6, 6.4, "wood_warm"), box(4.4, 5.6, 6, 4.7, 5.9, 6.3, "lace_white"),
         box(8.8, 5.4, 6.8, 9.6, 6.2, 8, "bone"),                                        # right arm drawing
         box(9.5, 3.8, 9.2, 10.8, 7.4, 10.2, "wood_old"), box(9.7, 7.4, 9.4, 10.6, 8, 10, "iron")]  # quiver
    return e


def wishing_well():
    e = [box(2, 0, 2, 14, 5, 4.2, "well_stone"), box(2, 0, 11.8, 14, 5, 14, "well_stone"),
         box(2, 0, 4.2, 4.2, 5, 11.8, "well_stone"), box(11.8, 0, 4.2, 14, 5, 11.8, "well_stone"),
         box(1.6, 5, 1.6, 14.4, 5.9, 14.4, "stone"), box(4.2, 0.8, 4.2, 11.8, 2.2, 11.8, "water_dark"),
         box(5.6, 2.3, 6.4, 7, 2.6, 7.8, "gold_l"), box(8, 2.3, 8.4, 9.4, 2.6, 9.8, "gold"),
         box(2.6, 5.9, 2.6, 3.8, 13.4, 3.8, "wood_old"), box(12.2, 5.9, 2.6, 13.4, 12.2, 3.8, "wood_old"),
         box(2.6, 5.9, 12.2, 3.8, 13.4, 13.4, "wood_old"), box(12.2, 5.9, 12.2, 13.4, 13.4, 13.4, "wood_old"),
         box(1, 13.4, 1, 15, 14.6, 8.4, "wood_warm_d"), box(1, 14.6, 8.4, 15, 15.4, 15, "wood_warm"),
         box(7.6, 13, 7.6, 8.4, 13.4, 8.4, "lace_white"), box(6.8, 11, 6.8, 9.2, 12.8, 9.2, "wood_warm"),
         box(7.1, 11.3, 7.1, 8.9, 12.4, 8.9, "coal")]
    return e


def swan_lantern():
    """Two swans facing each other across a brass lamp post (mirror x around 8)."""
    def swan2(mirror):
        def X(x):
            return 16 - x if mirror else x
        return [box(min(X(3.4), X(7.6)), 0.6, 5.6, max(X(3.4), X(7.6)), 2.8, 9.4, "bone_l"),
                box(min(X(2.6), X(4.6)), 2.2, 6.8, max(X(2.6), X(4.6)), 4.8, 8.2, "lace_white"),
                box(min(X(6.2), X(7)), 2.8, 7.1, max(X(6.2), X(7)), 6.6, 7.9, "bone_l"),
                box(min(X(5.4), X(7.2)), 6.4, 6.9, max(X(5.4), X(7.2)), 7.8, 8.3, "bone_l"),
                box(min(X(4.4), X(5.6)), 6.9, 7.3, max(X(4.4), X(5.6)), 7.5, 7.9, "carrot"),
                box(min(X(6.4), X(6.8)), 7.2, 7.12, max(X(6.4), X(6.8)), 7.6, 7.5, "coal")]
    e = [box(1.5, 0, 1.5, 14.5, 0.7, 14.5, "water_dark")] + swan2(False) + swan2(True)
    e += [box(7.5, 0.7, 12, 8.5, 8.6, 13, "iron"), box(6.6, 8.6, 11.1, 9.4, 9.2, 13.9, "brass"),
          box(6.8, 5.6, 11.3, 9.2, 8.8, 13.7, "glass"), box(7.4, 6.2, 11.9, 8.6, 7.8, 13.1, "flame_core"),
          box(6.8, 4.8, 11.3, 9.2, 5.6, 13.7, "brass"), box(7.4, 9.2, 11.9, 8.6, 10.2, 13.1, "brass_d")]
    return e


def valentine_banner():
    e = [box(0.6, 14, 14.4, 15.4, 15, 15.4, "iron"), box(1.4, 13.4, 14.2, 2.2, 15.4, 15, "iron_d"),
         box(13.8, 13.4, 14.2, 14.6, 15.4, 15, "iron_d"), box(2.6, 3.6, 14.1, 13.4, 14, 14.9, "rose_deep"),
         box(2.6, 3.6, 14.3, 13.4, 4.4, 14.7, "gold")]                                  # fringe
    e += heart_pix(8, 9.6, 13.85, 2.1, "blush", d=0.6)
    e += [box(3.4, 2, 14.3, 4.4, 3.6, 14.8, "gold"), box(11.6, 2, 14.3, 12.6, 3.6, 14.8, "gold")]
    return e


def heart_lantern(on=True, mount="floor"):
    shell = "rose_red" if on else "rose_deep"
    body = [box(5.2, 6.2, 5.8, 7.5, 8.8, 10.2, shell), box(8.5, 6.2, 5.8, 10.8, 8.8, 10.2, shell),
            box(6.0, 4.6, 6, 10.0, 6.6, 10, shell), box(7.0, 3.4, 6.4, 9.0, 4.9, 9.6, shell)]
    glow = box(7.55, 6.4, 6.3, 8.45, 8.6, 9.7, "flame_core")        # flame seen in the valley between the lobes
    shine = [box(5.6, 8.5, 6, 7.1, 8.9, 10, "blush")] if on else [box(5.6, 8.5, 6, 7.1, 8.9, 10, "soot")]
    frame = [box(6, 9, 6, 10, 10, 10, "brass"), box(6.4, 2.8, 6.4, 9.6, 3.6, 9.6, "brass"),
             box(5.7, 10, 6.3, 6.3, 10.4, 9.7, "brass_d"), box(9.7, 10, 6.3, 10.3, 10.4, 9.7, "brass_d")]
    if mount == "wall":
        plate = [box(7.4, 10.4, 13.4, 8.6, 15, 14.2, "iron")]
        return shift(body + [glow] + shine + frame + plate, dz=6.4)
    if mount == "hang":
        rope = [box(7.6, 10.4, 7.6, 8.4, 16, 8.4, "chain")]
        return body + [glow] + shine + frame + rope
    stand = [box(7, 0, 7, 9, 2.8, 9, "brass_d"), box(6.4, 0, 6.4, 9.6, 0.7, 9.6, "brass")]
    return body + [glow] + shine + frame + stand


def teddy_bear(pose):
    e = [box(4.6, 0, 6.4, 7, 2.4, 9.4, "cocoa"), box(9, 0, 6.4, 11.4, 2.4, 9.4, "cocoa"),
         box(6.4, 0, 5.8, 9.6, 0.8, 7, "cocoa_l"),
         box(5.2, 2, 5.6, 10.8, 8, 9.4, "cocoa"), box(6.8, 2.8, 5.3, 9.2, 6.4, 5.7, "cream_pink"),
         box(5.6, 8, 5.4, 10.4, 11.8, 9, "cocoa"), box(6.6, 8.8, 4.9, 9.4, 10.4, 5.5, "cream_pink"),
         box(7.5, 9.9, 4.7, 8.5, 10.6, 5, "coal"), box(6.3, 11.2, 5.8, 7.5, 12.4, 7, "cocoa"),
         box(8.5, 11.2, 5.8, 9.7, 12.4, 7, "cocoa"), box(6.7, 11.6, 5.9, 7.3, 12.2, 6.4, "blush"),
         box(8.9, 11.6, 5.9, 9.5, 12.2, 6.4, "blush"), box(6.4, 10.2, 5.15, 6.9, 10.7, 5.35, "coal"),
         box(9.3, 10.2, 5.15, 9.8, 10.7, 5.35, "coal"), box(7, 7.4, 5.3, 9, 8.2, 9.2, "rose_red")]  # ribbon
    if pose == "a":
        e += [box(3.8, 3.4, 6.2, 5.2, 6.6, 8.4, "cocoa"), box(10.8, 3.4, 6.2, 12.2, 6.6, 8.4, "cocoa")]
    else:
        e += [box(3.6, 5.4, 6.2, 5, 9.6, 8.4, "cocoa"), box(11, 5.4, 6.2, 12.4, 9.6, 8.4, "cocoa"),
              box(3.6, 9.4, 6, 5.2, 10.4, 8.6, "cocoa_l"), box(10.8, 9.4, 6, 12.4, 10.4, 8.6, "cocoa_l")]
    return e


def heart_rows(n):
    """n rows of candy hearts in the jar (bottom first), stacked against the open front."""
    e = []
    cols = ("blush", "rose_red", "cream_pink")
    for row in range(n):
        y = 1.0 + row * 1.8
        for i, (x, z) in enumerate(((4.8, 4.6), (7.4, 4.4), (9.6, 4.9), (5.6, 7.2), (8.6, 7.6), (6, 9.6), (9.4, 9.8))):
            e += heart_pix(x + 1, y + 1.2, z, 0.95, cols[(row + i) % 3], d=1.5)
    return e


def candy_heart_jar(full):
    e = [box(4, 0, 4, 12, 0.7, 12, "lace_white"),
         box(4, 0.7, 11.4, 12, 10, 12, "glass"), box(4, 0.7, 4.6, 4.6, 10, 11.4, "glass"),
         box(11.4, 0.7, 4.6, 12, 10, 11.4, "glass"), box(4, 0.7, 4, 12, 1.6, 4.6, "glass"),
         box(4.4, 10, 4.4, 11.6, 11.4, 11.6, "brass"), box(6.6, 11.4, 6.6, 9.4, 12.1, 9.4, "rose_gold"),
         box(3.9, 9.7, 3.9, 12.1, 10.2, 12.1, "rose_gold")]
    return e + (heart_rows(4) if full == 2 else heart_rows(2) if full == 1 else [])


def bouquet_popper():
    e = [box(5.4, 0, 5.4, 10.6, 1.6, 10.6, "rose_gold"), box(6.2, 1.6, 6.2, 9.8, 3.6, 9.8, "rose_gold_l"),
         box(7, 3.6, 7, 9, 5.2, 9, "lace_white"), box(6.2, 0.4, 6.2, 9.8, 1.2, 9.8, "rose_deep"),
         box(7.9, 5.4, 7.9, 8.1, 8.2, 8.1, "rose_leaf")]
    for x, y, z, c in ((5.6, 7.6, 5.6, "blush"), (10.2, 8.2, 6, "rose_red"), (6, 9.4, 10.2, "blossom"),
                       (10.6, 7, 10.4, "blush"), (8, 9.8, 8, "rose_red"), (5, 8.8, 8, "cream_pink"),
                       (11.4, 9.2, 8, "blossom"), (8, 8.4, 5, "cream_pink")):
        e += [box(x, y, z, x + 1.6, y + 1.6, z + 1.6, c)]
    return e


def rose_planter():
    e = [box(1, 0, 3, 15, 3.4, 12, "wood_warm"), box(1, 3, 3, 15, 3.9, 3.5, "wood_warm_d"),
         box(1, 3, 11.5, 15, 3.9, 12, "wood_warm_d"), box(1.6, 1.2, 3.5, 14.4, 3, 11.5, "dirt")]
    for x, z, h in ((3.6, 5.6, 5.8), (7.4, 8.8, 6.6), (11.4, 5.8, 5.4), (5.6, 9.6, 4.8), (9.6, 4.4, 5.2)):
        e += [box(x, 3.2, z, x + 2.2, h, z + 2.2, "rose_leaf")]
        e += heart_pix(x + 1.1, h + 1.1, z + 0.4, 1, "rose_red", d=1.5)
    return e


def love_songbird():
    e = [box(4, 0, 4, 12, 0.9, 12, "brass"), box(4.6, 0.9, 4.6, 5.2, 9, 5.2, "brass_l"),
         box(10.8, 0.9, 4.6, 11.4, 9, 5.2, "brass_l"), box(4.6, 0.9, 10.8, 5.2, 9, 11.4, "brass_l"),
         box(10.8, 0.9, 10.8, 11.4, 9, 11.4, "brass_l"), box(4, 9, 4, 12, 9.8, 12, "brass"),
         box(5.6, 9.8, 5.6, 10.4, 10.8, 10.4, "brass_l"), box(7.4, 10.8, 7.4, 8.6, 11.6, 8.6, "iron"),
         box(5.6, 2.6, 7.6, 10.4, 3.1, 8.4, "wood_warm"),                                # perch
         box(6.4, 3.2, 6.4, 9, 5.8, 9.6, "blush"), box(8.6, 3.8, 6, 9.4, 5.4, 7.6, "rose_deep"),
         box(6.8, 4.6, 6.8, 8.4, 5.6, 8.2, "rose_deep"), box(8.8, 5.4, 6.4, 10, 6.6, 8.4, "blush"),
         box(9.8, 5.6, 7, 10.4, 6.2, 7.8, "gold"), box(9.2, 5.9, 6.9, 9.5, 6.2, 7.1, "coal"),
         box(4.8, 6, 4.9, 6.4, 7.4, 5.3, "rose_red")]                                   # little heart on the bars
    return e


def sweetheart_topiary():
    e = [box(4.6, 0, 4.6, 11.4, 3, 11.4, "clay_pot"), box(4.2, 3, 4.2, 11.8, 3.8, 11.8, "clay_pot_l"),
         box(7.2, 3.8, 7.2, 8.8, 8.6, 8.8, "wood_old"),
         box(3.8, 9.4, 6.2, 7.2, 13.6, 9.8, "moss"), box(8.8, 9.4, 6.2, 12.2, 13.6, 9.8, "moss"),
         box(4.8, 7.2, 6.4, 11.2, 10.4, 9.6, "moss"), box(6.2, 5.6, 6.5, 9.8, 8, 9.5, "moss"),
         box(7.1, 4.2, 6.7, 8.9, 5.9, 9.3, "moss_d")]
    for x, y, z in ((4.8, 12.6, 6), (10.8, 12.6, 9.2), (7.9, 5.4, 6.6), (6, 11.2, 8.8), (10, 11, 6.4)):
        e += [box(x, y, z, x + 1.1, y + 1.1, z + 1.1, "blossom")]
    return e


def love_potion_shelf():
    e = [box(2, 1, 14, 14, 1.9, 15.6, "wood_old"), box(2, 8.4, 14, 14, 9.2, 15.6, "wood_old"),
         box(2.4, 0, 14.6, 3.2, 1, 15.4, "iron"), box(12.8, 0, 14.6, 13.6, 1, 15.4, "iron"),
         box(2.4, 9.2, 14.6, 3.2, 10.2, 15.4, "iron"), box(12.8, 9.2, 14.6, 13.6, 10.2, 15.4, "iron")]

    def bottle(x, y, col, h=4):
        return [box(x, y, 14.2, x + 2, y + h, 15.6, "glass"), box(x + 0.3, y + 0.4, 14.4, x + 1.7, y + h - 1.4, 15.4, col),
                box(x + 0.6, y + h, 14.5, x + 1.4, y + h + 0.9, 15.3, "wood_warm")]
    e += bottle(3.4, 1.9, "rose_deep") + bottle(7, 1.9, "indigo", 3.4) + bottle(10.6, 1.9, "blush")
    e += bottle(4.6, 9.2, "cream_pink", 3.2) + bottle(8.6, 9.2, "rose_red", 3.6)
    return e


def clockwork_heart():
    e = [box(2.4, 0, 4.6, 13.6, 1, 11.4, "brass_d"), box(3, 1, 5.2, 13, 1.6, 10.8, "brass"),
         box(3.4, 1.6, 5.6, 4.4, 8.8, 10.4, "brass"), box(11.6, 1.6, 5.6, 12.6, 8.8, 10.4, "brass"),
         box(3.4, 8.4, 5.6, 12.6, 9.2, 10.4, "brass")]
    e += heart_pix(8, 6.4, 6.6, 2.4, "rose_deep", d=2.8)                                # the brass heart, wound between the uprights
    e += heart_pix(8, 6.2, 6.2, 1.5, "rose_red", d=3.6)                                 # glass heart core, catches the light
    e += [box(7.4, 8.9, 7.4, 8.6, 11.4, 8.6, "iron"), box(6.2, 11.4, 7.5, 9.8, 12.2, 8.5, "copper"),
          box(6.6, 12.2, 7.7, 9.4, 12.9, 8.3, "copper_d"),
          box(2, 3, 7.2, 3.2, 4.2, 8.4, "copper_d"), box(12.8, 3, 7.2, 14, 4.2, 8.4, "copper_d")]  # side gears
    return e


def lovers_bench():
    e = [box(1, 3.4, 3.8, 15, 4.4, 9.6, "wood_warm"), box(1.4, 4.4, 8.6, 14.6, 10.6, 9.6, "wood_old"),
         box(1.4, 4.9, 8.3, 14.6, 6.1, 8.7, "wood_warm"), box(1.4, 7.4, 8.3, 14.6, 8.6, 8.7, "wood_warm"),
         box(1.8, 0, 4.2, 3, 3.4, 5.4, "iron"), box(1.8, 0, 8, 3, 3.4, 9.2, "iron"),
         box(13, 0, 4.2, 14.2, 3.4, 5.4, "iron"), box(13, 0, 8, 14.2, 3.4, 9.2, "iron"),
         box(1, 4.4, 3.8, 1.8, 8, 9.6, "iron"), box(14.2, 4.4, 3.8, 15, 8, 9.6, "iron")]
    e += heart_pix(8, 9.8, 8.1, 1.1, "rose_red", d=0.8)                                 # carved in the top rail
    return e


def heart_window():
    e = [box(1, 0.6, 14.6, 15, 1.6, 15.6, "wood_old"), box(1, 14, 14.6, 15, 15, 15.6, "wood_old"),
         box(1, 1.6, 14.7, 2.1, 14, 15.5, "wood_old"), box(13.9, 1.6, 14.7, 14.9, 14, 15.5, "wood_old"),
         box(2.1, 2.1, 14.9, 13.9, 13.5, 15.3, "glass"), box(7.7, 2.1, 14.8, 8.3, 13.5, 15.4, "iron"),
         box(2.1, 7.5, 14.8, 13.9, 8.1, 15.4, "iron")]
    e += heart_pix(5.1, 5.4, 14.75, 1.5, "rose_deep", d=0.5)
    e += heart_pix(10.9, 10.5, 14.75, 1.5, "rose_deep", d=0.5)
    e += [box(4.2, 11.2, 14.75, 5.6, 12.6, 15.25, "blush"), box(10.4, 3.2, 14.75, 11.8, 4.6, 15.25, "blush")]
    return e


def amore_machine():
    e = [box(2.6, 0, 3.6, 13.4, 12.6, 10.4, "rose_deep"), box(2.2, 12.6, 3.2, 13.8, 13.6, 10.8, "rose_gold"),
         box(3.4, 3.2, 3.2, 10.6, 10.6, 3.7, "glass")]
    for row, y in enumerate((4.2, 6.6, 9)):                                             # three rows of candy hearts, on the glass
        for i, x in enumerate((4.6, 6.9, 9.2)):
            e += heart_pix(x, y, 2.75, 0.8, ("blush", "rose_red", "cream_pink")[(row + i) % 3], d=0.4)
    e += [box(11, 4.4, 3.2, 12.8, 9.4, 3.9, "brass"), box(11.5, 7.4, 3.1, 12.3, 8.2, 3.3, "coal"),
          box(11.4, 5, 3.1, 12.4, 6, 3.3, "gold"), box(3.4, 0.7, 3.3, 9, 2.6, 3.9, "iron_d"),
          box(4, 1.2, 3.5, 8.4, 2.1, 3.8, "rose_gold"), box(6.8, 10.8, 3.15, 8, 12, 3.5, "rose_red"),
          box(8, 10.8, 3.15, 9.2, 12, 3.5, "rose_red"), box(7.4, 10, 3.15, 8.6, 11.1, 3.5, "rose_red"),
          box(2.8, 0, 4, 3.4, 0.4, 10, "iron"), box(12.6, 0, 4, 13.2, 0.4, 10, "iron")]
    return e


def chocolate_heart():
    """Treat: a bite-sized chocolate heart lying flat, dusted sugar on top."""
    e = heart_pix(8, 3.6, 6.4, 2.2, "chocolate", d=1.4)
    e += [box(6.2, 4.9, 6.7, 7.4, 5.1, 7.9, "cream"), box(8.6, 4.9, 7.2, 9.8, 5.1, 8.4, "cream")]
    return e


def pillar_box():
    """A British pillar box in engine red, re-riveted by the Guild: black plinth, gold banding,
    dome with finial, brass slit + collection door, and a steam pressure gauge with side vent."""
    e = [box(2.8, 0, 2.8, 13.2, 1.2, 13.2, "coal"), box(3.4, 1.2, 3.4, 12.6, 1.8, 12.6, "coal_l"),
         box(4, 1.8, 4, 12, 21.5, 12, "xred"),
         box(4, 1.8, 4, 5, 21.5, 5, "xred_d"), box(11, 1.8, 4, 12, 21.5, 5, "xred_d"),       # round shading
         box(3.9, 2.2, 3.9, 12.1, 2.6, 12.1, "gold"), box(3.9, 20.9, 3.9, 12.1, 21.3, 12.1, "gold"),
         box(3.4, 21.5, 3.4, 12.6, 22.4, 12.6, "xred_d"),
         box(4.6, 22.4, 4.6, 11.4, 24.6, 11.4, "xred"), box(6, 24.6, 6, 10, 25.8, 10, "xred"),
         box(7, 25.8, 7, 9, 26.6, 9, "xred_d"),
         box(6.8, 26.6, 6.8, 9.2, 27.4, 9.2, "brass"), box(7.1, 27.4, 7.1, 8.9, 28.8, 8.9, "brass"),
         box(7.5, 28.8, 7.5, 8.5, 29.6, 8.5, "gold")]
    e += [box(5.2, 16.6, 3.8, 10.8, 19.4, 4.2, "brass"), box(6, 17.8, 3.7, 10, 18.5, 3.95, "char"),
          box(5.9, 17.4, 3.65, 10.1, 17.9, 4.25, "brass_d")]                                # letter slit
    e += [box(6.6, 14, 3.85, 9.4, 15.4, 4.15, "char"), box(7, 14.3, 3.75, 7.6, 15.1, 4.25, "gold"),
          box(8.4, 14.3, 3.75, 9, 15.1, 4.25, "gold")]                                      # cypher plate
    e += [box(5.6, 6, 3.8, 10.4, 12.6, 4.2, "xred_d"), box(5.5, 7, 3.75, 5.9, 8.2, 4.3, "iron"),
          box(5.5, 10.4, 3.75, 5.9, 11.6, 4.3, "iron"), box(9.7, 8.8, 3.7, 10.3, 9.9, 4.3, "gold")]  # door + hinges + lock
    e += [box(10.9, 12.9, 3.7, 12.1, 14.1, 4.3, "brass"), box(11.1, 13.1, 3.65, 11.9, 13.9, 4.35, "dial"),
          box(11.4, 13.3, 3.6, 11.6, 13.7, 4.4, "coal")]                                    # pressure gauge
    e += [box(2.6, 19.6, 7, 4.2, 20.8, 9, "copper"), box(2.2, 20.8, 6.7, 3.4, 21.4, 9.3, "brass"),
          box(12, 2.4, 4.5, 12.6, 3, 5.1, "brass"), box(12, 2.4, 10.9, 12.6, 3, 11.5, "brass")]  # rivets
    return e


def dragon_nest():
    """A dragon's nest: a wide ring of woven straw and twigs on a brass-banded base, a bed of embers
    in the hollow, and one big egg of riveted brass lying in it. Dragons go looking for these."""
    e = [box(1.5, 0, 1.5, 14.5, 1.2, 14.5, "wood_warm"), box(2, 1.2, 2, 14, 2.4, 14, "straw"),
         box(1.2, 1.4, 4, 2.6, 4.2, 12, "straw"), box(13.4, 1.4, 4, 14.8, 4.2, 12, "straw"),
         box(4, 1.4, 1.2, 12, 4.2, 2.6, "straw"), box(4, 1.4, 13.4, 12, 4.2, 14.8, "straw"),
         box(1.6, 1.4, 1.6, 4.4, 4.0, 4.4, "straw_d"), box(11.6, 1.4, 1.6, 14.4, 4.0, 4.4, "straw_d"),
         box(1.6, 1.4, 11.6, 4.4, 4.0, 14.4, "straw_d"), box(11.6, 1.4, 11.6, 14.4, 4.0, 14.4, "straw_d")]
    e += [box(0.8, 3.0, 5, 1.6, 6.4, 6, "wood_warm"), box(14.4, 3.0, 9, 15.2, 6.2, 10, "wood_warm"),    # loose twigs
          box(6, 3.0, 0.8, 7, 6.0, 1.6, "wood_warm"), box(9, 3.0, 14.4, 10, 6.4, 15.2, "wood_warm")]
    e += [box(1.2, 3.6, 1.2, 14.8, 4.4, 2.0, "brass"), box(1.2, 3.6, 14, 14.8, 4.4, 14.8, "brass"),   # brass rim bands
          box(1.2, 3.6, 2, 2, 4.4, 14, "brass"), box(14, 3.6, 2, 14.8, 4.4, 14, "brass")]
    e += [box(4.4, 2.4, 4.4, 11.6, 2.9, 11.6, "ember"), box(5.4, 2.9, 5.4, 10.6, 3.2, 10.6, "flame")]  # the warm bed
    e += [box(6, 3.2, 6, 10, 5.2, 10, "copper"), box(6.6, 5.2, 6.6, 9.4, 6.6, 9.4, "copper"),          # the egg
          box(5.6, 3.2, 6.6, 6, 4.8, 9.4, "brass"), box(10, 3.2, 6.6, 10.4, 4.8, 9.4, "brass"),
          box(6.8, 6.6, 6.8, 9.2, 7.2, 9.2, "brass"), box(6.2, 4.0, 5.8, 9.8, 4.5, 6.0, "brass_d"),
          box(6.2, 4.0, 10.0, 9.8, 4.5, 10.2, "brass_d")]
    return e


# Relics.java special-cases PILLAR_BOX in the STEAM effect: the cloud puffs escape at the vent's
# height (front of the block, one block up) instead of the block centre.


VALENTINE = {
    "heart_balloon": heart_balloon, "rose_bell": rose_bell, "love_lock_rail_wall": love_lock_rail,
    "chocolate_heart": chocolate_heart,
    "heart_arch": heart_arch, "cupid_statue": cupid_statue, "wishing_well": wishing_well,
    "swan_lantern": swan_lantern, "valentine_banner_wall": valentine_banner,
    "heart_lantern": lambda: heart_lantern(True, "floor"), "heart_lantern_b": lambda: heart_lantern(False, "floor"),
    "heart_lantern_wall": lambda: heart_lantern(True, "wall"), "heart_lantern_wall_b": lambda: heart_lantern(False, "wall"),
    "heart_lantern_hanging": lambda: heart_lantern(True, "hang"), "heart_lantern_hanging_b": lambda: heart_lantern(False, "hang"),
    "teddy_bear": lambda: teddy_bear("a"), "teddy_bear_b": lambda: teddy_bear("b"),
    "candy_heart_jar": lambda: candy_heart_jar(2), "candy_heart_jar_half": lambda: candy_heart_jar(1),
    "candy_heart_jar_empty": lambda: candy_heart_jar(0), "bouquet_popper": bouquet_popper,
    "rose_planter": rose_planter, "love_songbird": love_songbird, "sweetheart_topiary": sweetheart_topiary,
    "love_potion_shelf": love_potion_shelf, "clockwork_heart": clockwork_heart, "lovers_bench": lovers_bench,
    "heart_window": heart_window, "amore_machine": amore_machine,
}


# ------------------------------------------------------------------ Winter Onsen (0.7.0)

def onsen_tub():
    e = [box(1, 0, 3, 15, 7, 4, "hinoki"), box(1, 0, 12, 15, 7, 13, "hinoki"), box(1, 0, 4, 2, 7, 12, "hinoki"), box(14, 0, 4, 15, 7, 12, "hinoki"),
         box(2, 0, 4, 14, 1, 12, "hinoki_d"), box(2, 1, 4, 14, 6, 12, "onsen_water"),
         box(0.6, 7, 2.6, 15.4, 7.8, 4.4, "hinoki_l"), box(0.6, 7, 11.6, 15.4, 7.8, 13.4, "hinoki_l"),
         box(0.6, 7, 4.4, 2.4, 7.8, 11.6, "hinoki_l"), box(13.6, 7, 4.4, 15.4, 7.8, 11.6, "hinoki_l"),
         box(1, 2.5, 2.5, 15, 3.2, 3, "brass"), box(1, 2.5, 13, 15, 3.2, 13.5, "brass"),    # bands
         box(0, 0, 13.5, 4, 3, 16, "stone"), box(12, 0, 0, 16, 2.5, 3, "stone_d"), box(0.5, 3, 14, 3.5, 3.7, 15.5, "snow"),
         box(12.5, 2.5, 0.5, 15.5, 3.2, 2.5, "snow")]
    return e


def onsen_lantern():
    e = [box(5, 0, 5, 11, 1.5, 11, "stone"), box(7, 1.5, 7, 9, 6, 9, "stone_d"), box(5, 6, 5, 11, 7, 11, "stone"),
         box(5.5, 7, 5.5, 10.5, 10.5, 10.5, "stone"),
         box(6.2, 7.5, 5.4, 9.8, 10, 5.5, "flame"), box(6.2, 7.5, 10.5, 9.8, 10, 10.6, "flame"),
         box(5.4, 7.5, 6.2, 5.5, 10, 9.8, "flame"), box(10.5, 7.5, 6.2, 10.6, 10, 9.8, "flame"),
         box(2, 10.5, 2, 14, 12.5, 14, "stone_l"), box(3, 12.5, 3, 13, 13.5, 13, "stone"),
         box(2.5, 13.5, 2.5, 13.5, 14.8, 13.5, "snow"), box(7, 14.8, 7, 9, 16, 9, "stone_d")]
    return e


def kotatsu():
    e = [box(0.5, 1, 0.5, 15.5, 5.5, 15.5, "umbrella_red"), box(0.2, 1, 0.2, 15.8, 2.2, 15.8, "umbrella_red_d"),
         box(0, 5.5, 0, 16, 7, 16, "hinoki_l"), box(0, 5.5, 0, 16, 5.9, 16, "hinoki"),
         box(2, 0, 2, 14, 1, 14, "ember"),
         box(6.8, 7, 6.8, 9.4, 8.6, 9.4, "carrot"), box(7.8, 8.6, 7.8, 8.4, 9.2, 8.4, "pine"),
         box(10.5, 7, 3, 13.5, 7.4, 6, "paper_white")]
    return e


def snow_bunny(up):
    ear = 12.5 if up else 10.5
    e = [box(5, 0, 5.5, 11, 4.2, 11.5, "snow"), box(5.6, 4.2, 5.2, 10.4, 8, 9.6, "snow"), box(4.5, 0, 5, 11.5, 0.7, 12, "snow_blue"),
         box(6.6, 5.6, 4.8, 7.6, 6.6, 5.3, "xred"), box(8.4, 5.6, 4.8, 9.4, 6.6, 5.3, "xred"), box(7.4, 4.8, 4.9, 8.6, 5.4, 5.3, "blush"),
         box(6, 8, 7, 7.4, ear, 8.2, "snow"), box(8.6, 8, 7, 10, ear, 8.2, "snow"),
         box(6.2, 8.4, 6.8, 6.6, ear - 0.8, 7, "blush"), box(9.4, 8.4, 6.8, 9.8, ear - 0.8, 7, "blush"),
         box(5.8, 1, 10.8, 10.2, 3.2, 12.4, "snow_blue"), box(9.5, 0.7, 4.5, 11, 1.6, 6, "pine"), box(10, 1.6, 4.8, 10.6, 2.6, 5.4, "xred")]
    return e


def wash_set():
    e = [box(2, 2, 3, 8, 3, 9, "hinoki"), box(2.5, 0, 3.5, 3.5, 2, 4.5, "hinoki_d"), box(6.5, 0, 3.5, 7.5, 2, 4.5, "hinoki_d"),
         box(2.5, 0, 7.5, 3.5, 2, 8.5, "hinoki_d"), box(6.5, 0, 7.5, 7.5, 2, 8.5, "hinoki_d"),
         box(2.6, 3, 4.5, 7.4, 3.5, 7.5, "paper_white"),
         box(9, 0, 8, 14, 0.8, 13, "hinoki_d"), box(9, 0.8, 8, 14, 4, 8.6, "hinoki"), box(9, 0.8, 12.4, 14, 4, 13, "hinoki"),
         box(9, 0.8, 8.6, 9.6, 4, 12.4, "hinoki"), box(13.4, 0.8, 8.6, 14, 4, 12.4, "hinoki"),
         box(9.6, 0.8, 8.6, 13.4, 3.2, 12.4, "onsen_water"), box(9.5, 3.9, 8, 13.5, 4.5, 8.6, "brass")]
    return e


def towel_rack():
    e = [box(1, 0, 6, 2, 14, 7, "hinoki_d"), box(14, 0, 6, 15, 14, 7, "hinoki_d"),
         box(1, 11, 6, 15, 12, 7, "hinoki"), box(1, 6.5, 6, 15, 7.5, 7, "hinoki"),
         box(3, 6, 5.4, 7, 11.5, 5.9, "paper_white"), box(3, 6, 7.1, 7, 11.5, 7.6, "paper_white"), box(3, 11.5, 5.4, 7, 12.4, 7.6, "paper_white"),
         box(9, 3, 5.4, 13, 7.1, 5.9, "indigo"), box(9, 3, 7.1, 13, 7.1, 7.6, "indigo"), box(9, 7.1, 5.4, 13, 7.9, 7.6, "indigo"),
         box(0.5, 0, 5.5, 2.5, 0.8, 7.5, "stone_d"), box(13.5, 0, 5.5, 15.5, 0.8, 7.5, "stone_d")]
    return e


def sake_tray():
    e = [box(3, 0, 3, 13, 1, 13, "lacquer_black"), box(3, 1, 3, 13, 1.8, 3.6, "lacquer_black"), box(3, 1, 12.4, 13, 1.8, 13, "lacquer_black"),
         box(3, 1, 3.6, 3.6, 1.8, 12.4, "lacquer_black"), box(12.4, 1, 3.6, 13, 1.8, 12.4, "lacquer_black"),
         box(5, 1, 6, 7.6, 5.5, 8.6, "tokkuri"), box(5.8, 5.5, 6.8, 6.8, 7.4, 7.8, "tokkuri"), box(5.4, 7.4, 6.4, 7.2, 7.9, 8.2, "tokkuri_d"),
         box(9, 1, 5, 11, 2.8, 7, "porcelain_w"), box(9.4, 2.4, 5.4, 10.6, 2.9, 6.6, "amber"),
         box(9.5, 1, 9, 11.5, 2.8, 11, "porcelain_w"), box(9.9, 2.4, 9.4, 11.1, 2.9, 10.6, "amber"),
         box(4.5, 1, 9.5, 6.5, 1.6, 11.5, "onsen_water_l")]
    return e


def snow_pine():
    e = [box(7, 0, 7, 9, 8, 9, "wood_warm_d"), box(5, 0, 5, 11, 1, 11, "snow"),
         box(2, 6, 2, 14, 10, 14, "pine"), box(3.5, 10, 3.5, 12.5, 14, 12.5, "pine"), box(5, 14, 5, 11, 18, 11, "pine"), box(6.5, 18, 6.5, 9.5, 22, 9.5, "pine"),
         box(2.4, 10, 2.4, 13.6, 10.8, 13.6, "snow"), box(3.9, 14, 3.9, 12.1, 14.8, 12.1, "snow"), box(5.4, 18, 5.4, 10.6, 18.8, 10.6, "snow"),
         box(6.9, 22, 6.9, 9.1, 23.2, 9.1, "snow"), box(2.2, 8.5, 2.2, 5, 9.6, 5, "snow"), box(11, 8.5, 11, 13.8, 9.6, 13.8, "snow"),
         box(7.4, 23.2, 7.4, 8.6, 24.4, 8.6, "gold")]
    return e


ONSEN = {
    "onsen_tub": onsen_tub, "onsen_lantern": onsen_lantern, "kotatsu": kotatsu, "snow_bunny": lambda: snow_bunny(False),
    "snow_bunny_b": lambda: snow_bunny(True), "wash_set": wash_set, "towel_rack": towel_rack, "sake_tray": sake_tray, "snow_pine": snow_pine,
}


MODELS = {
    "creature_skull": skull, "creature_skull_wall": skull_wall, "creature_ribcage": ribcage,
    "torn_settlement_banner": banner, "torn_settlement_banner_wall": banner_wall,
    "charred_outpost_lantern": lantern, "charred_outpost_lantern_wall": lantern_wall, "charred_outpost_lantern_hanging": lantern_hanging,
    "vigil_candles": candles, "settlers_grave": grave, "ruin_monolith": monolith, "specimen_jar": jar,
    "creature_egg_sac": lambda: egg_sac(False), "creature_egg_sac_ceiling": lambda: egg_sac(True),
    "gibbet_cage": lambda: cage(False), "gibbet_cage_hanging": lambda: cage(True), "ash_pile": ash_pile,
    "warding_totem": lambda: totem(False), "warding_totem_lit": lambda: totem(True), "shattered_portal_shard": portal_shard,
}
MODELS.update(XMAS)
MODELS.update(NEWYEAR)
MODELS.update(MORE)
MODELS.update(BREWERY)
MODELS.update(VALENTINE)
MODELS.update(ONSEN)
MODELS["dragon_nest"] = dragon_nest      # Curiosities: the nest wild dragons look for (Wildlife 0.9.8)
MODELS["pillar_box"] = pillar_box  # Curiosities (year-round): the steam-pillar post box
MODELS["water_bottle"] = water_bottle      # Treat: drinkable, see Treats.java
MODELS["blood_token"] = blood_token        # RelicType: floor + wall, plain and Blessed - 0.6.0
MODELS["blood_token_wall"] = lambda: blood_token_wall(False)
MODELS["blood_token_blessed"] = blood_token_blessed
MODELS["blood_token_blessed_wall"] = lambda: blood_token_wall(True)



def add_sounds(root, namespace):
    """Copies sounds/out/<namespace> (made by sounds/make_sounds.py) into the pack."""
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "sounds", "out", namespace)
    if not os.path.isdir(src):
        print("note: no sounds in", src, "- run sounds/make_sounds.py first (pack built without sounds)")
        return
    dst = os.path.join(root, "assets", namespace)
    djson = os.path.join(dst, "sounds.json")
    keep = {}
    if os.path.exists(djson):
        try:
            keep = json.load(open(djson, encoding="utf-8"))
        except Exception:
            keep = {}
    shutil.copytree(src, dst, dirs_exist_ok=True)
    # Hand-added events (e.g. quest voices steamworks:milo.lineNN, which live only in the
    # exploded pack's sounds.json) must survive rebuilds: generated events win, extras stay.
    if keep and os.path.exists(djson):
        cur = json.load(open(djson, encoding="utf-8"))
        extra = {k: v for k, v in keep.items() if k not in cur}
        if extra:
            cur.update(extra)
            with open(djson, "w", encoding="utf-8") as f:
                json.dump(cur, f, indent=1)


# ------------------------------------------------------------------ music discs (data from tools/make_discs.py)
DISC_LABELS = [(200, 40, 40), (40, 150, 70), (220, 170, 50), (60, 110, 200), (170, 70, 170), (230, 120, 50)]


def disc_texture(label):
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if d > 7.4:
                continue
            if d < 1.2:
                px[y][x] = (0, 0, 0, 0)          # centre hole
            elif d < 3.6:
                px[y][x] = label + (255,)         # paper label
            else:
                g = 38 + (10 if int(d * 2) % 2 == 0 else 0)
                if dx + dy < -5 and d > 4:        # soft highlight
                    g += 30
                px[y][x] = (g, g, g + 6, 255)
            if 5.9 < d <= 7.4 and dx + dy > 6:
                px[y][x] = (22, 22, 26, 255)
    return png(px)


def add_discs():
    cfg = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "config.yml")
    if not os.path.exists(cfg):
        print("note: no config.yml - run tools/make_discs.py first (pack built without discs)")
        return
    lines = open(cfg, encoding="utf-8").read().split("\n")
    begin, end = "# BEGIN:generated", "# END:generated"
    bi = next(i for i, ln in enumerate(lines) if ln.strip() == begin)
    ei = next(i for i, ln in enumerate(lines) if ln.strip() == end)
    entries = {}
    cur = None
    for ln in lines[bi + 1:ei]:
        s = ln.strip()
        if not s or s.startswith("#"):
            continue
        if not ln.startswith("    ") and s.endswith(":") and not s.startswith("#"):
            cur = s[:-1]
            entries[cur] = {}
        elif cur is not None and ":" in s:
            k, _, v = s.partition(":")
            entries[cur][k.strip()] = v.strip().strip('"')
    for k, label in enumerate(DISC_LABELS):
        write(f"assets/relics/textures/item/disc_c{k}.png", disc_texture(label))
        write(f"assets/relics/models/item/disc_c{k}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"relics:item/disc_c{k}"}})
    for did, d in sorted(entries.items()):
        write(f"assets/relics/items/disc_{did}.json", {"model": {"type": "minecraft:model", "model": f"relics:item/disc_c{int(d.get('colour', 0)) % 6}"}})
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "discs-out")
    frag = os.path.join(out, "sounds_fragment.json")
    if os.path.exists(frag):
        shutil.copytree(os.path.join(out, "assets"), os.path.join(ROOT, "assets"), dirs_exist_ok=True)
        sj = os.path.join(ROOT, "assets", "relics", "sounds.json")
        if os.path.exists(sj):
            data = json.load(open(sj, encoding="utf-8"))
        else:
            data = {}
        data.update(json.load(open(frag, encoding="utf-8")))
        json.dump(data, open(sj, "w", encoding="utf-8"), indent=1)
        lp = os.path.join(ROOT, "assets", "relics", "lang", "en_us.json")
        if os.path.exists(lp):
            lang = json.load(open(lp, encoding="utf-8"))
            lang["subtitles.relics.disc"] = "Music disc plays"
            for ev in json.load(open(frag, encoding="utf-8")):  # music-box aliases (make_discs MUSIC_BOX)
                if ev.startswith("music_box."):
                    lang.setdefault("subtitles.relics." + ev, "Music box plays")
            json.dump(lang, open(lp, "w", encoding="utf-8"), indent=1)


def main():
    global ROOT
    out = sys.argv[1]
    ROOT = os.path.join(out, "Relics-pack")
    shutil.rmtree(ROOT, ignore_errors=True)
    write("pack.mcmeta", {"pack": {"description": "Relics (lore props)", "pack_format": 64,
                                   "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}})
    write("assets/relics/textures/item/palette.png", palette_png())
    for name, fn in MODELS.items():
        model(name, fn())
    add_sounds(ROOT, "relics")
    add_discs()
    zpath = os.path.join(out, "Relics-pack.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(ROOT):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                z.write(full, os.path.relpath(full, ROOT).replace(os.sep, "/"))
    print("wrote", zpath, "with", len(MODELS), "models")


if __name__ == "__main__":
    main()
