"""Kura resource pack: every part and item model of the storage network.

All our own design (steampunk brass/copper + Japanese kura storehouse: white plaster,
black timber): low-poly element models coloured from one palette texture.
The plugin uses item_model kura:<id> for items and for the ItemDisplays on placed parts:
  - full-block parts (connector, terminals, trim, framed cable, emitter, cabinet,
    interface/crate covers) span 0..16 so they hide the barrier/barrel/redstone block
  - cable_<mask>: core + arms, bit = index in Networks.FACES (down, up, north, south, west, east)
  - cable_connector: flange on the north side (turned to the facing by the plugin)
  - fronts face north (z = 0)

Usage: python -I make_pack.py <out folder>  -> <out>/Kura-pack/ + <out>/Kura-pack.zip
Put the zip in plugins/ResourcePackManager/mixer/ (no files shared with our other packs).
"""
import json, os, random, struct, sys, zipfile, zlib

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
    "brass": (214, 170, 72), "brass_dark": (150, 112, 44), "brass_light": (240, 210, 120),
    "copper": (196, 108, 68), "copper_dark": (132, 66, 40), "copper_light": (228, 150, 104),
    "verdigris": (92, 168, 140), "iron": (176, 180, 184), "iron_dark": (96, 100, 106),
    "wood": (126, 84, 50), "wood_dark": (70, 44, 28), "wood_light": (170, 122, 78),
    "timber": (34, 30, 30), "plaster": (236, 232, 222), "plaster_shade": (210, 204, 192),
    "paper": (244, 238, 220), "paper_shade": (220, 212, 190), "screen": (40, 70, 52), "screen_glow": (120, 230, 150),
    "screen_line": (80, 170, 110), "glass": (170, 210, 220), "red": (180, 36, 36), "red_on": (255, 70, 60),
    "red_dark": (90, 20, 20), "gauge": (240, 236, 220), "black": (24, 22, 24), "gold": (236, 196, 70),
    "crystal": (120, 220, 230), "crystal_glow": (190, 250, 255), "lacquer": (150, 30, 36), "indigo": (46, 70, 140),
    "green": (70, 150, 70), "blue": (60, 100, 200), "yellow": (240, 200, 60), "hole": (40, 34, 30),
    "rope": (200, 170, 110), "slat": (150, 106, 64), "slat_gap": (52, 36, 24), "handle": (92, 56, 36),
}
NAMES = list(PALETTE)
assert len(NAMES) <= 256


def palette_png():
    rnd = random.Random(11)
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
    """One element; `faces` overrides colours per face, e.g. {"north": "screen"}."""
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
    """Writes models/item/<name>.json and the item definition kura:<name>."""
    lo, hi = bounds(elems)
    size = max(hi[i] - lo[i] for i in range(3))
    m = {"textures": {"p": "kura:item/palette", "particle": "kura:item/palette"}, "elements": elems}
    if block_icon:
        s = round(0.625 * 16 / max(size, 1), 3)
        m["display"] = {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [s, s, s]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        }
    else:  # handheld items: flat-ish, held like a tool
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
    write(f"assets/kura/models/item/{name}.json", m)
    write(f"assets/kura/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"kura:item/{name}"}})

# ------------------------------------------------------------------ shared pieces

def frame(col="brass", t=1.5):
    """Twelve edge beams of a full block."""
    e = []
    for (x1, x2) in ((0, t), (16 - t, 16)):
        for (z1, z2) in ((0, t), (16 - t, 16)):
            e.append(box(x1, 0, z1, x2, 16, z2, col))
    for (y1, y2) in ((0, t), (16 - t, 16)):
        for (z1, z2) in ((0, t), (16 - t, 16)):
            e.append(box(t, y1, z1, 16 - t, y2, z2, col))
        for (x1, x2) in ((0, t), (16 - t, 16)):
            e.append(box(x1, y1, t, x2, y2, 16 - t, col))
    return e


def cube(col, inset=0.0, faces=None):
    return [box(inset, inset, inset, 16 - inset, 16 - inset, 16 - inset, col, faces)]


def rivets(face, col="brass_light"):
    """Four small rivets just outside one face."""
    e = []
    for a in (2.2, 13.8):
        for b in (2.2, 13.8):
            if face == "north":
                e.append(box(a - 0.5, b - 0.5, -0.2, a + 0.5, b + 0.5, 0, col))
            elif face == "south":
                e.append(box(a - 0.5, b - 0.5, 16, a + 0.5, b + 0.5, 16.2, col))
            elif face == "up":
                e.append(box(a - 0.5, 16, b - 0.5, a + 0.5, 16.2, b + 0.5, col))
    return e

# ------------------------------------------------------------------ parts

def connector():
    e = cube("wood_dark", 0.5) + frame("brass")
    # copper port plates with a gear rosette on every side
    for face in ("north", "south", "east", "west", "up", "down"):
        if face in ("north", "south"):
            z1, z2 = (-0.1, 0.4) if face == "north" else (15.6, 16.1)
            e.append(box(4, 4, z1, 12, 12, z2, "copper"))
            e.append(box(6.5, 6.5, z1 - 0.2 if face == "north" else z1, 9.5, 9.5, z2 + (0.2 if face == "south" else 0), "brass_light"))
            for (x, y) in ((7.5, 4.5), (7.5, 10.5), (4.5, 7.5), (10.5, 7.5)):
                e.append(box(x, y, z1 - 0.1 if face == "north" else z1, x + 1, y + 1, z2 + (0.1 if face == "south" else 0), "brass"))
        elif face in ("east", "west"):
            x1, x2 = (15.6, 16.1) if face == "east" else (-0.1, 0.4)
            e.append(box(x1, 4, 4, x2, 12, 12, "copper"))
            e.append(box(x1 - (0 if face == "east" else 0.2), 6.5, 6.5, x2 + (0.2 if face == "east" else 0), 9.5, 9.5, "brass_light"))
        else:
            y1, y2 = (15.6, 16.1) if face == "up" else (-0.1, 0.4)
            e.append(box(4, y1, 4, 12, y2, 12, "copper"))
            e.append(box(6.5, y1 - (0 if face == "up" else 0.2), 6.5, 9.5, y2 + (0.2 if face == "up" else 0), 9.5, "brass_light"))
    return e


def terminal(crafting=False):
    e = [box(0, 0, 0, 16, 16, 16, "wood", {"north": "brass_dark", "up": "brass", "down": "wood_dark"})]
    e += frame("brass_dark", 1.2)
    # screen bezel + glowing screen with text lines
    e.append(box(2, 5, -0.4, 14, 14, 0, "brass"))
    e.append(box(3, 6, -0.6, 13, 13, -0.3, "screen"))
    for i, w in enumerate((8, 6, 7, 4)):
        e.append(box(4, 11.6 - i * 1.5, -0.7, 4 + w, 12.2 - i * 1.5, -0.55, "screen_glow" if i == 0 else "screen_line"))
    # knobs and keys below the screen
    for x in (3.2, 6.2):
        e.append(box(x, 1.8, -0.8, x + 1.8, 3.6, 0, "brass_light"))
    for k in range(4):
        e.append(box(9 + k * 1.3, 2, -0.5, 10 + k * 1.3, 3.2, 0, "black"))
    if crafting:
        # a little crafting grid inlay on top
        e.append(box(3, 16, 3, 13, 16.3, 13, "wood_light"))
        for a in (5.9, 9.1):
            e.append(box(a, 16.3, 3, a + 0.8, 16.5, 13, "wood_dark"))
            e.append(box(3, 16.3, a, 13, 16.5, a + 0.8, "wood_dark"))
    else:
        e.append(box(6, 16, 6, 10, 16.4, 10, "copper"))
    return e


def trim():
    # kura storehouse panel: white plaster with black timber frame and cross beam
    e = cube("plaster", 0.3) + frame("timber", 2)
    for face in ("north", "south"):
        z1, z2 = (-0.1, 0.4) if face == "north" else (15.6, 16.1)
        e.append(box(7.2, 2, z1, 8.8, 14, z2, "timber"))
        e.append(box(2, 7.2, z1, 14, 8.8, z2, "timber"))
    for face in ("east", "west"):
        x1, x2 = (15.6, 16.1) if face == "east" else (-0.1, 0.4)
        e.append(box(x1, 2, 7.2, x2, 14, 8.8, "timber"))
        e.append(box(x1, 7.2, 2, x2, 8.8, 14, "timber"))
    return e


ARM = {  # face index -> element for an arm from the core to that face
    0: lambda: box(5.5, 0, 5.5, 10.5, 5.5, 10.5, "copper"),
    1: lambda: box(5.5, 10.5, 5.5, 10.5, 16, 10.5, "copper"),
    2: lambda: box(5.5, 5.5, 0, 10.5, 10.5, 5.5, "copper"),
    3: lambda: box(5.5, 5.5, 10.5, 10.5, 10.5, 16, "copper"),
    4: lambda: box(0, 5.5, 5.5, 5.5, 10.5, 10.5, "copper"),
    5: lambda: box(10.5, 5.5, 5.5, 16, 10.5, 10.5, "copper"),
}
RING = {
    0: lambda: box(5, 1, 5, 11, 2.2, 11, "brass"),
    1: lambda: box(5, 13.8, 5, 11, 15, 11, "brass"),
    2: lambda: box(5, 5, 1, 11, 11, 2.2, "brass"),
    3: lambda: box(5, 5, 13.8, 11, 11, 15, "brass"),
    4: lambda: box(1, 5, 5, 2.2, 11, 11, "brass"),
    5: lambda: box(13.8, 5, 5, 15, 11, 11, "brass"),
}


def cable(mask):
    e = [box(5.2, 5.2, 5.2, 10.8, 10.8, 10.8, "copper_dark")]
    for i in range(6):
        if mask & (1 << i):
            e.append(ARM[i]())
            e.append(RING[i]())
    if mask == 0:
        e.append(box(6.5, 10.8, 6.5, 9.5, 11.4, 9.5, "brass"))  # a cap so a lone piece reads as a cable end
    return e


def framed_cable():
    e = cube("wood_dark", 0.4) + frame("timber", 2)
    for face in ("north", "south", "east", "west", "up", "down"):
        # copper pipe ends showing through a round port on each side
        if face in ("north", "south"):
            z1, z2 = (-0.1, 0.4) if face == "north" else (15.6, 16.1)
            e.append(box(5, 5, z1, 11, 11, z2, "brass"))
            e.append(box(6, 6, z1 - 0.1 if face == "north" else z1, 10, 10, z2 + (0.1 if face == "south" else 0), "copper"))
        elif face in ("east", "west"):
            x1, x2 = (15.6, 16.1) if face == "east" else (-0.1, 0.4)
            e.append(box(x1, 5, 5, x2, 11, 11, "brass"))
            e.append(box(x1 - (0 if face == "east" else 0.1), 6, 6, x2 + (0.1 if face == "east" else 0), 10, 10, "copper"))
        else:
            y1, y2 = (15.6, 16.1) if face == "up" else (-0.1, 0.4)
            e.append(box(5, y1, 5, 11, y2, 11, "brass"))
            e.append(box(6, y1 - (0 if face == "up" else 0.1), 6, 10, y2 + (0.1 if face == "up" else 0), 10, "copper"))
    return e


def cable_connector():
    return [box(5.2, 5.2, 5.2, 10.8, 10.8, 10.8, "copper_dark"),
            box(5.5, 5.5, 2, 10.5, 10.5, 5.5, "copper"),
            box(2, 2, 0, 14, 14, 2, "brass", {"north": "brass_dark"}),
            box(4, 4, -0.2, 12, 12, 0.2, "copper_light"),
            box(3, 3, 2, 13, 13, 3, "brass_dark")] + [
        box(x, y, -0.4, x + 1, y + 1, 0, "brass_light") for (x, y) in ((2.5, 2.5), (12.5, 2.5), (2.5, 12.5), (12.5, 12.5))]


def inventory_hopper():
    # funnel body; output nozzle on the north side
    e = [box(1, 6, 1, 15, 16, 15, "iron_dark", {"up": "black"}),
         box(0, 14, 0, 16, 16, 16, "brass"),
         box(4, 2, 4, 12, 6, 12, "iron"),
         box(6, 6.5, 0, 10, 10.5, 1, "copper"),
         box(6.5, 3, 6.5, 9.5, 7, 9.5, "copper")]
    e.append(box(5.5, 7, -0.3, 10.5, 11, 0.5, "brass_light"))
    e += [box(1, 9, -0.2, 15, 10, 0, "copper_dark"), box(1, 9, 16, 15, 10, 16.2, "copper_dark")]
    return e


def level_emitter(on):
    e = [box(0, 0, 0, 16, 16, 16, "wood_dark", {"north": "brass_dark", "up": "brass"})] + frame("brass", 1.2)
    # gauge dial with a needle
    e.append(box(3, 3, -0.4, 13, 13, 0, "brass"))
    e.append(box(4, 4, -0.6, 12, 12, -0.3, "gauge"))
    e.append(box(7.6, 7.6, -0.75, 8.4, 11.2, -0.55, "red_dark", rot=("z", -22.5 if on else 22.5, [8, 8, -0.6])))
    e.append(box(7.2, 7.2, -0.8, 8.8, 8.8, -0.5, "black"))
    # lamp on top
    lamp = "red_on" if on else "red_dark"
    e.append(box(5.5, 16, 5.5, 10.5, 17.5, 10.5, "brass"))
    e.append(box(6.2, 17.5, 6.2, 9.8, 19.5, 9.8, lamp))
    return e


def inventory_interface():
    # covers a barrel: wood staves, brass bands, copper hatch on top and pipe ports on the sides
    e = [box(0, 0, 0, 16, 16, 16, "wood", {"up": "wood_dark", "down": "wood_dark"})]
    for y in (2, 12.5):
        e.append(box(-0.2, y, -0.2, 16.2, y + 1.5, 16.2, "brass"))
    for x in (3.5, 7.5, 11.5):
        e.append(box(x, 0.2, -0.1, x + 0.4, 15.8, 0, "wood_dark"))
        e.append(box(x, 0.2, 16, x + 0.4, 15.8, 16.1, "wood_dark"))
    e.append(box(3, 16, 3, 13, 16.6, 13, "copper", {"up": "copper_light"}))
    e.append(box(6.5, 16.6, 7.3, 9.5, 17.4, 8.7, "brass_light"))
    for (x1, x2) in ((-0.6, 0), (16, 16.6)):
        e.append(box(x1, 5.5, 5.5, x2, 10.5, 10.5, "copper"))
    return e


def open_crate():
    # covers a barrel: plank crate, dark open mouth on the front
    e = [box(0, 0, 0, 16, 16, 16, "slat", {"north": "slat_gap"})]
    for y in (0, 5.3, 10.7):
        for face_z in ((-0.3, 0.2), (15.8, 16.3)):
            if face_z[0] < 0 and y > 0:
                continue  # the front stays open except for the bottom board
            e.append(box(-0.2, y, face_z[0], 16.2, y + 5, face_z[1], "slat"))
    for z in (0, 15):
        for x in (0, 15):
            e.append(box(x - 0.2, 0, z - 0.2, x + 1.2, 16, z + 1.2, "wood_dark"))
    e.append(box(2, 2, -0.1, 14, 14, 0, "slat_gap"))
    return e


def filing_cabinet():
    e = [box(0, 0, 0, 16, 16, 16, "wood", {"north": "wood_dark"})] + frame("timber", 1.2)
    for i in range(3):
        y = 1.6 + i * 4.8
        e.append(box(1.6, y, -0.3, 14.4, y + 4.2, 0.2, "wood_light"))
        e.append(box(6, y + 1.6, -0.9, 10, y + 2.4, -0.3, "brass"))
        e.append(box(6.8, y + 2.8, -0.5, 9.2, y + 3.6, -0.3, "paper"))  # label holder
    return e

# ------------------------------------------------------------------ handheld items

def wireless(advanced):
    e = [box(4, 0, 6.5, 12, 13, 9.5, "brass_dark"),
         box(4.8, 4.5, 6.2, 11.2, 12, 6.5, "screen"),
         box(5.5, 9.5, 6.1, 10, 10.3, 6.2, "screen_glow"),
         box(5.5, 7.5, 6.1, 9, 8.2, 6.2, "screen_line"),
         box(5, 1, 6.2, 7, 3, 6.5, "brass_light"), box(9, 1, 6.2, 11, 3, 6.5, "red"),
         box(10, 13, 7.5, 11, 18, 8.5, "iron"), box(9.7, 18, 7.2, 11.3, 19, 8.8, "copper_light")]
    if advanced:
        e += [box(4.5, 13, 7, 7.5, 16, 9, "brass"), box(5, 16, 7.5, 7, 18.5, 8.5, "crystal"),
              box(5.4, 18.5, 7.7, 6.6, 19.3, 8.3, "crystal_glow")]
    return e


def punch_card(mark):
    e = [box(3, 0, 7.6, 13, 15, 8.4, "paper", {"north": "paper_shade"})]
    rnd = random.Random(len(mark))
    for row in range(5):
        for col in range(4):
            if rnd.random() < 0.55:
                x, y = 4 + col * 2.2, 3 + row * 2.2
                e.append(box(x, y, 7.5, x + 1, y + 1.2, 8.5, "hole"))
    e.append(box(3, 13, 7.5, 13, 15, 8.5, {"item": "copper", "tag": "green", "poly": "indigo"}[mark]))
    if mark == "tag":
        e.append(box(10.5, 1, 7.4, 12.5, 3, 8.6, "gold"))
    if mark == "poly":
        e.append(box(9.5, 0.5, 7.4, 12.5, 3.5, 8.6, "verdigris"))
    return e


def configurator():
    r = ("z", 45, [8, 8, 8])
    return [box(7, -1, 7, 9, 12, 9, "handle", rot=r), box(6.5, 11, 6.5, 9.5, 13, 9.5, "brass", rot=r),
            box(5, 13, 7, 11, 18, 9, "brass_dark", rot=r), box(6, 14, 6.8, 10, 17, 9.2, "glass", rot=r),
            box(7.5, 6, 6.8, 8.5, 9, 9.2, "copper", rot=r)]


def paint_kit():
    return [box(2, 0, 4, 9, 6, 11, "iron", {"up": "lacquer"}), box(2.5, 6, 4.5, 8.5, 6.4, 10.5, "red"),
            box(3.5, 6.4, 5.5, 5.5, 6.6, 7.5, "blue"), box(5.5, 6.4, 7.5, 7.5, 6.6, 9.5, "green"),
            box(10.5, 0, 7.4, 12, 12, 8.6, "wood_light", rot=("z", -22.5, [11, 0, 8])),
            box(10.2, 12, 7.1, 12.3, 13.5, 8.9, "brass", rot=("z", -22.5, [11, 0, 8])),
            box(10.4, 13.5, 7.3, 12.1, 16, 8.7, "black", rot=("z", -22.5, [11, 0, 8]))]

# ------------------------------------------------------------------ main

def add_sounds(root, namespace):
    import shutil
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "sounds", "out", namespace)
    if not os.path.isdir(src):
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
    # Hand-added events survive a rebuild: generated events win, extras stay.
    if keep and os.path.exists(djson):
        cur = json.load(open(djson, encoding="utf-8"))
        extra = {k: v for k, v in keep.items() if k not in cur}
        if extra:
            cur.update(extra)
            with open(djson, "w", encoding="utf-8") as fh:
                json.dump(cur, fh, indent=1)


def main():
    global ROOT
    out = sys.argv[1]
    ROOT = os.path.join(out, "Kura-pack")
    write("pack.mcmeta", {"pack": {"description": "Kura (storage network)", "pack_format": 64,
                                   "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}})
    write("assets/kura/textures/item/palette.png", palette_png())
    models = {
        "inventory_connector": connector(),
        "storage_terminal": terminal(False),
        "crafting_terminal": terminal(True),
        "inventory_trim": trim(),
        "inventory_cable": cable(4 | 8),
        "framed_cable": framed_cable(),
        "cable_connector": cable_connector(),
        "inventory_hopper": inventory_hopper(),
        "level_emitter": level_emitter(False),
        "level_emitter_on": level_emitter(True),
        "inventory_interface": inventory_interface(),
        "open_crate": open_crate(),
        "filing_cabinet": filing_cabinet(),
    }
    for mask in range(64):
        models[f"cable_{mask}"] = cable(mask)
    for name, e in models.items():
        model(name, e, True)
    hand = {
        "wireless_terminal": wireless(False), "adv_wireless_terminal": wireless(True),
        "item_filter": punch_card("item"), "tag_filter": punch_card("tag"), "poly_filter": punch_card("poly"),
        "configurator": configurator(), "paint_kit": paint_kit(),
    }
    for name, e in hand.items():
        model(name, e, False)
    add_sounds(ROOT, "kura")
    zpath = os.path.join(out, "Kura-pack.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(ROOT):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                z.write(full, os.path.relpath(full, ROOT).replace(os.sep, "/"))
    print("wrote", zpath, "with", len(models) + len(hand), "models")


if __name__ == "__main__":
    main()
