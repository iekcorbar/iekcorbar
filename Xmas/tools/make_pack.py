"""Xmas resource pack: kerst building blocks (own designs, palette texture).

Blocks are 16x16x16 cubes with kerst textures. Each block also has an item model
(item_model xmas:<id>) used by the plugin's inventory items.

Usage: python make_pack.py <out folder>  -> <out>/Xmas-pack/ + <out>/Xmas-pack.zip
"""
import json, os, struct, sys, zipfile, zlib

ROOT = None


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, bytes):
        open(path, "wb").write(data)
    else:
        open(path, "w", encoding="utf-8").write(json.dumps(data, indent=1))


# ------------------------------------------------------------------ palette (kerst)

BASE = {
    "snow": (240, 244, 250), "snow_shade": (210, 218, 230),
    "ice": (170, 210, 240), "ice_dark": (120, 170, 210),
    "pine": (40, 96, 52), "pine_dark": (24, 64, 34), "pine_light": (70, 140, 80),
    "wood": (150, 98, 58), "wood_dark": (100, 64, 36),
    "present_red": (186, 30, 36), "present_red_d": (130, 20, 26),
    "present_green": (30, 120, 60), "present_green_d": (20, 80, 40),
    "ribbon_gold": (224, 180, 60), "ribbon_gold_d": (170, 130, 40),
    "candy_red": (200, 40, 44), "candy_white": (250, 250, 250),
    "ginger": (176, 108, 54), "ginger_dark": (130, 78, 36), "icing": (250, 248, 240),
    "wreath_green": (40, 100, 50), "wreath_dark": (24, 64, 34), "berry_red": (186, 30, 36),
    "wax": (230, 222, 200), "flame": (255, 196, 70), "flame_core": (255, 244, 170),
    "glass": (196, 226, 230), "glass_dark": (150, 190, 200),
}
PALETTE = {}
for k, (r, g, b) in BASE.items():
    PALETTE[k] = (r, g, b)
    PALETTE[k + "_d"] = tuple(int(c * 0.7) for c in (r, g, b))
    PALETTE[k + "_l"] = tuple(min(255, int(c * 1.2 + 10)) for c in (r, g, b))
NAMES = list(PALETTE)


def cell(col):
    i = NAMES.index(col)
    cx, cy = (i % 16) * 8, (i // 16) * 8
    return [cx + 1, cy + 1, cx + 7, cy + 7]


def palette_png():
    rnd = __import__("random").Random(7)
    RES = 128
    px = [[(0, 0, 0, 255)] * RES for _ in range(RES)]
    for i, n in enumerate(NAMES):
        r, g, b = PALETTE[n]
        cx, cy = (i % 16) * 8, (i // 16) * 8
        for y in range(8):
            for x in range(8):
                d = rnd.randint(-4, 4)
                px[cy + y][cx + x] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255)
    return png(px)


TEX = "data:image/png;base64," + __import__("base64").b64encode(palette_png()).decode()


# ------------------------------------------------------------------ block models

def cube_block(name, col_top, col_side, col_bottom=None):
    """A 16x16x16 cube with given face colours."""
    if col_bottom is None:
        col_bottom = col_side
    el = {
        "from": [0, 0, 0], "to": [16, 16, 16],
        "faces": {
            "north": {"uv": cell(col_side), "texture": "#0"},
            "south": {"uv": cell(col_side), "texture": "#0"},
            "east": {"uv": cell(col_side), "texture": "#0"},
            "west": {"uv": cell(col_side), "texture": "#0"},
            "up": {"uv": cell(col_top), "texture": "#0"},
            "down": {"uv": cell(col_bottom), "texture": "#0"},
        }
    }
    return el


BLOCKS = {
    "xmas_snow": ("snow", "snow_shade", "snow_shade"),
    "xmas_ice": ("ice", "ice", "ice_dark"),
    "xmas_pine": ("pine", "pine", "wood"),
    "xmas_pine_planks": ("pine_light", "pine", "pine"),
    "xmas_present_red": ("present_red", "present_red", "present_red_d"),
    "xmas_present_green": ("present_green", "present_green", "present_green_d"),
    "xmas_candy_cane": ("candy_white", "candy_red", "candy_white"),
    "xmas_gingerbread": ("icing", "ginger", "ginger_dark"),
}


def make_block_model(bid, top, side, bottom):
    el = cube_block(bid, top, side, bottom)
    bb = {
        "credit": "Xmas", "texture_size": [128, 128],
        "textures": {"0": "xmas:xmas.png", "particle": "xmas:xmas.png"},
        "elements": [el],
    }
    return bb


def make_item_model(bid, top, side, bottom):
    el = {
        "from": [0, 0, 0], "to": [16, 16, 16],
        "faces": {
            "north": {"uv": cell(side), "texture": "#0"},
            "south": {"uv": cell(side), "texture": "#0"},
            "east": {"uv": cell(side), "texture": "#0"},
            "west": {"uv": cell(side), "texture": "#0"},
            "up": {"uv": cell(top), "texture": "#0"},
            "down": {"uv": cell(bottom), "texture": "#0"},
        }
    }
    P = lambda v: [round(c + 8, 3) for c in v]  # not needed for block items; keep 0..16
    js = {
        "parent": "block/cube_all",
        "textures": {"all": "xmas:block/" + bid},
    }
    return js


def main():
    global ROOT
    out = sys.argv[1]
    ROOT = os.path.join(out, "Xmas-pack")
    os.makedirs(ROOT, exist_ok=True)

    # palette texture
    write("assets/xmas/textures/block/xmas.png", palette_png())

    for bid, (top, side, bottom) in BLOCKS.items():
        bb = make_block_model(bid, top, side, bottom)
        write("assets/xmas/models/block/" + bid + ".json", bb)
        # item model
        im = {"parent": "xmas:block/" + bid}
        write("assets/xmas/models/item/" + bid + ".json", im)

    # pack.mcmeta
    write("pack.mcmeta", {"pack": {"pack_format": 46, "description": "Xmas blocks"}})

    # zip
    zip_path = os.path.join(out, "Xmas-pack.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(ROOT):
            for fn in files:
                full = os.path.join(dirpath, fn)
                arc = os.path.relpath(full, ROOT)
                z.write(full, arc)
    print("wrote Xmas-pack to", ROOT, "and", zip_path)


if __name__ == "__main__":
    main()