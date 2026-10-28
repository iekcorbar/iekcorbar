"""Sakagura resource pack: garden produce/seed item models, sake cask models (one per wood +
copper), crop plant stage models + sprites (3 stages each), and the sounds (namespace sakagura).

The crop block itself is a structure void; the plugin shows a growing plant as a non-persistent
ItemDisplay whose ITEM_MODEL is sakagura:block/<crop>_stage<N> (see Garden.java). Rebuild the
models here and in Kitchen's make_pack.py when Fruit.java changes.

Usage (Sakagura folder): python -I tools/make_pack.py resourcepack
"""
import json, os, random, shutil, struct, sys, zipfile, zlib

ROOT = None
BASE = {
    "grain": (214, 182, 96), "grain_d": (170, 136, 60), "hop": (150, 196, 90), "grape": (96, 40, 120), "ume": (176, 196, 70),
    "yuzu": (246, 204, 50), "peach": (246, 160, 130), "cherry": (186, 20, 40), "strawberry": (220, 40, 50), "blueberry": (60, 80, 170),
    "juniper": (70, 90, 140), "ginger": (210, 170, 110), "seed": (110, 80, 50), "pit": (150, 110, 70), "leaf": (80, 150, 60),
    "stem": (90, 120, 50), "iron": (90, 92, 98), "copper": (186, 110, 62), "rope": (196, 170, 120), "paper": (240, 232, 210),
    "ink": (40, 30, 30), "red": (180, 40, 36), "glow": (240, 226, 120),
    "oak": (168, 132, 82), "spruce": (110, 80, 50), "birch": (210, 196, 140), "jungle": (164, 116, 80), "acacia": (176, 96, 56),
    "dark_oak": (70, 48, 26), "cherrywood": (226, 180, 172), "mangrove": (120, 56, 50), "bamboo": (200, 186, 96),
    # 2026-10-28: new-crop colours (must cover every col used in FRUITS below)
    "rice": (232, 226, 198), "rye": (196, 158, 96), "maize": (238, 200, 78), "tomato": (208, 52, 42),
    "raspberry": (214, 74, 104), "blackcurrant": (48, 34, 66), "elderberry": (64, 44, 82), "cranberry": (188, 32, 48),
    "gooseberry": (168, 198, 96), "mulberry": (96, 24, 48), "persimmon": (232, 118, 44), "loquat": (238, 172, 84),
    "pomegranate": (198, 42, 64), "fig": (118, 62, 102), "quince": (224, 190, 84), "olive": (122, 132, 62),
    "date": (142, 86, 42), "lychee": (218, 98, 92), "passion": (128, 62, 142), "vanilla": (228, 210, 172),
}
PALETTE = {}
for k, (r, g, b) in BASE.items():
    PALETTE[k] = (r, g, b)
    PALETTE[k + "_d"] = tuple(int(c * 0.72) for c in (r, g, b))
    PALETTE[k + "_l"] = tuple(min(255, int(c * 1.2 + 10)) for c in (r, g, b))
NAMES = list(PALETTE)
assert len(NAMES) <= 256

# must match Fruit.java (order!)
FRUITS = [("barley", "grain"), ("hops", "hop"), ("grape", "grape"), ("ume", "ume"), ("yuzu", "yuzu"), ("peach", "peach"),
          ("cherry", "cherry"), ("strawberry", "strawberry"), ("blueberry", "blueberry"), ("juniper", "juniper"), ("ginger", "ginger"),
          ("apple", "red"), ("pear", "hop"), ("apricot", "peach"), ("mikan", "yuzu"), ("kiwi", "pit"),
          ("rice", "rice"), ("rye", "rye"), ("maize", "maize"), ("tomato", "tomato"), ("raspberry", "raspberry"),
          ("blackcurrant", "blackcurrant"), ("elderberry", "elderberry"), ("cranberry", "cranberry"), ("gooseberry", "gooseberry"),
          ("mulberry", "mulberry"), ("persimmon", "persimmon"), ("loquat", "loquat"), ("pomegranate", "pomegranate"), ("fig", "fig"),
          ("quince", "quince"), ("olive", "olive"), ("date", "date"), ("lychee", "lychee"), ("passionfruit", "passion"),
          ("vanilla", "vanilla")]
SEEDS = ["hop_cuttings", "grape_seeds", "ume_pit", "yuzu_seeds", "peach_pit", "cherry_pit",
         "apple_pip", "pear_pip", "apricot_kernel", "mikan_seeds", "kiwi_seeds",
         "tomato_seeds", "persimmon_pip", "loquat_stone", "pomegranate_seeds", "fig_cuttings",
         "quince_pip", "olive_stone", "date_pit", "lychee_seed", "passionfruit_seeds", "vanilla_cuttings"]
WOODS = [("oak", "oak"), ("spruce", "spruce"), ("birch", "birch"), ("jungle", "jungle"), ("acacia", "acacia"), ("dark_oak", "dark_oak"),
         ("cherry", "cherrywood"), ("mangrove", "mangrove"), ("bamboo", "bamboo"), ("copper", "copper")]


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def palette_png():
    rnd = random.Random(41)
    px = [[(0, 0, 0, 255)] * 128 for _ in range(128)]
    for i, k in enumerate(NAMES):
        r, g, b = PALETTE[k]
        cx, cy = (i % 16) * 8, (i // 16) * 8
        grain = k.split("_")[0] in [w[1] for w in WOODS]
        for y in range(8):
            for x in range(8):
                d = rnd.randint(-6, 6) - (10 if grain and x % 3 == 0 else 0)
                px[cy + y][cx + x] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255)
    return png(px)


def uv(col):
    i = NAMES.index(col)
    return [i % 16 + 0.15, i // 16 + 0.15, i % 16 + 0.85, i // 16 + 0.85]


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if isinstance(data, bytes):
        open(path, "wb").write(data)
    else:
        open(path, "w", encoding="utf-8").write(json.dumps(data, indent=1))


def box(x1, y1, z1, x2, y2, z2, col):
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": {f: {"uv": uv(col), "texture": "#p"} for f in ("north", "south", "east", "west", "up", "down")}}


def model(name, es, block_scale=False):
    lo = [min(e["from"][i] for e in es) for i in range(3)]
    hi = [max(e["to"][i] for e in es) for i in range(3)]
    size = max(hi[i] - lo[i] for i in range(3))
    s = round(min(1.2, 0.625 * 16 / max(size, 1)), 3)
    ty = round((8 - (lo[1] + hi[1]) / 2) * s * 0.9, 2)
    cx = (lo[0] + hi[0]) / 2
    write(f"assets/sakagura/models/item/{name}.json", {
        "textures": {"p": "sakagura:item/palette", "particle": "sakagura:item/palette"}, "elements": es,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [round((8 - cx) * s * 0.3, 2), ty, 0], "scale": [s] * 3},
                    "ground": {"translation": [0, 2, 0], "scale": [s * 0.5] * 3},
                    "fixed": {"translation": [0, ty * 0.6, 0], "scale": [s * 0.8] * 3},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [s * 0.45] * 3},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, ty * 0.3, 0], "scale": [s * 0.5] * 3},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, ty * 0.3, 0], "scale": [s * 0.5] * 3},
                    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [s] * 3}}})
    write(f"assets/sakagura/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"sakagura:item/{name}"}})

# ------------------------------------------------------------------ items


def berries(col, n=7, size=1.6, seed=1):
    rnd = random.Random(seed)
    e = [box(7.6, 0, 7.6, 8.4, 3, 8.4, "stem")]
    for i in range(n):
        x, y, z = 5 + rnd.random() * 5, 1 + rnd.random() * 3, 5 + rnd.random() * 5
        e.append(box(x, y, z, x + size, y + size, z + size, col if i % 3 else col + "_l"))
    return e


def round_fruit(col, r=3.0, leaf=True):
    e = [box(8 - r, 0, 8 - r * 0.7, 8 + r, r * 1.8, 8 + r * 0.7, col), box(8 - r * 0.7, 0, 8 - r, 8 + r * 0.7, r * 1.8, 8 + r, col),
         box(8 - r * 0.6, r * 1.8, 8 - r * 0.6, 8 + r * 0.6, r * 2.0, 8 + r * 0.6, col + "_d"), box(7.7, r * 2.0, 7.7, 8.3, r * 2.0 + 1, 8.3, "stem")]
    if leaf:
        e.append(box(8.3, r * 2.0 + 0.5, 7.5, 10.5, r * 2.0 + 0.9, 8.5, "leaf"))
    return e


ITEMS = {
    "barley": [box(7.5, 0, 7.5, 8.5, 9, 8.5, "grain_d")] + [box(7.0 + (i % 2) * 1.0, 9 + i * 1.0, 7.2, 8.0 + (i % 2) * 1.0, 10 + i * 1.0, 8.8, "grain") for i in range(5)],
    "hops": [box(6, 0, 6, 10, 6, 10, "hop"), box(6.5, 6, 6.5, 9.5, 8, 9.5, "hop_l"), box(7, 8, 7, 9, 9, 9, "hop_d"), box(5.6, 1, 7, 6, 5, 9, "hop_d")],
    "grape": berries("grape", 12, 1.7, 3) + [box(7, 6, 7, 9, 6.6, 9, "leaf")],
    "ume": round_fruit("ume", 2.5),
    "yuzu": round_fruit("yuzu", 3.0),
    "peach": round_fruit("peach", 3.2) + [box(7.8, 0.5, 4.7, 8.2, 6, 4.9, "peach_d")],
    "cherry": [box(5, 0, 7, 7.5, 2.5, 9.5, "cherry"), box(8.5, 0, 6.5, 11, 2.5, 9, "cherry"), box(6, 2.5, 7.9, 6.4, 7, 8.3, "stem"), box(9.5, 2.5, 7.4, 9.9, 7, 7.8, "stem"), box(6, 7, 7.6, 10, 7.4, 8.4, "stem")],
    "strawberry": [box(5.5, 0, 5.5, 10.5, 4, 10.5, "strawberry"), box(6.5, 4, 6.5, 9.5, 5.5, 9.5, "strawberry"), box(5.5, 5, 5.5, 10.5, 5.6, 10.5, "leaf"), box(7, 1, 5.4, 7.4, 1.4, 5.5, "grain")],
    "blueberry": berries("blueberry", 9, 1.8, 5),
    "juniper": berries("juniper", 8, 1.4, 6) + [box(5, 2, 7.6, 11, 2.4, 8.4, "leaf_d")],
    "ginger": [box(4, 0, 6, 9, 3, 9, "ginger"), box(8, 0, 7, 12, 2.5, 10, "ginger"), box(5, 3, 7, 7, 4.5, 8.5, "ginger_d"), box(10, 2.5, 8, 11, 4, 9, "ginger_d")],
    "hop_cuttings": [box(7.6, 0, 7.6, 8.4, 9, 8.4, "stem"), box(6, 6, 7.6, 10, 6.4, 8.4, "hop"), box(6.5, 2, 7.6, 9.5, 2.4, 8.4, "hop")],
    "grape_seeds": berries("seed", 6, 1.0, 7),
    "ume_pit": [box(6.5, 0, 6.5, 9.5, 2.5, 9.5, "pit"), box(7, 2.5, 7, 9, 3, 9, "pit_d")],
    "yuzu_seeds": berries("grain_d", 6, 1.0, 8),
    "peach_pit": [box(6, 0, 6.5, 10, 3, 9.5, "pit_d"), box(6.5, 3, 7, 9.5, 3.5, 9, "pit")],
    "cherry_pit": [box(7, 0, 7, 9, 2, 9, "pit")],
    "apple": round_fruit("red", 3.1),
    "pear": round_fruit("hop", 2.7) + [box(7.0, 4.8, 7.0, 9.0, 6.8, 9.0, "hop"), box(7.6, 6.8, 7.6, 8.4, 7.4, 8.4, "stem")],
    "apricot": round_fruit("peach", 2.3),
    "mikan": round_fruit("yuzu", 2.7),
    "kiwi": round_fruit("pit", 2.5, leaf=False),
    "apple_pip": berries("seed", 5, 1.0, 11),
    "pear_pip": berries("seed", 5, 1.0, 12),
    "apricot_kernel": [box(6.5, 0, 6.5, 9.5, 2.5, 9.5, "pit"), box(7, 2.5, 7, 9, 3, 9, "pit_d")],
    "mikan_seeds": berries("grain_d", 6, 1.0, 13),
    "kiwi_seeds": berries("ink", 5, 0.8, 14),
    # --- 20 new crops (Fruit.java 0.4.0): produce + seeds
    "rice": [box(7.5, 0, 7.5, 8.5, 8, 8.5, "rice_d")] + [box(7.0 + (i % 2), 8 + i, 7.2, 8.0 + (i % 2), 9 + i, 8.8, "rice") for i in range(4)],
    "rye": [box(7.5, 0, 7.5, 8.5, 9, 8.5, "rye_d")] + [box(7.0 + (i % 2), 9 + i, 7.2, 8.0 + (i % 2), 10 + i, 8.8, "rye") for i in range(5)],
    "maize": [box(7.2, 0, 7.2, 8.8, 9, 8.8, "maize"), box(6.2, 1, 8.0, 7.2, 8, 9.0, "leaf"), box(8.8, 2, 7.0, 9.8, 9, 8.0, "leaf_d")],
    "tomato": round_fruit("tomato", 3.2),
    "raspberry": berries("raspberry", 8, 1.7, 21),
    "blackcurrant": berries("blackcurrant", 9, 1.4, 22),
    "elderberry": berries("elderberry", 11, 1.2, 23) + [box(6, 5.4, 6, 10, 5.8, 10, "leaf")],
    "cranberry": berries("cranberry", 6, 1.6, 24),
    "gooseberry": berries("gooseberry", 5, 2.0, 25),
    "mulberry": berries("mulberry", 7, 1.5, 26),
    "persimmon": round_fruit("persimmon", 3.0),
    "loquat": round_fruit("loquat", 2.2),
    "pomegranate": round_fruit("pomegranate", 3.3) + [box(7.4, 6.6, 7.4, 8.6, 7.4, 8.6, "pomegranate_d")],
    "fig": round_fruit("fig", 2.4, leaf=False),
    "quince": round_fruit("quince", 3.0),
    "olive": round_fruit("olive", 1.9),
    "date": [box(6.2, 0, 7.0, 9.8, 2.8, 9.0, "date"), box(7.0, 2.8, 7.4, 9.0, 3.4, 8.6, "date_d"), box(8.0, 3.4, 7.8, 8.3, 4.6, 8.1, "stem")],
    "lychee": round_fruit("lychee", 2.6),
    "passionfruit": round_fruit("passion", 3.0),
    "vanilla": [box(6.9, 0, 7.4, 7.8, 8, 8.3, "vanilla_d"), box(8.1, 0.6, 7.6, 9.0, 7.2, 8.5, "vanilla"), box(7.4, 8.0, 7.4, 8.2, 9.0, 8.2, "stem")],
    "tomato_seeds": berries("seed", 6, 1.0, 27),
    "persimmon_pip": [box(6.5, 0, 6.5, 9.5, 2.5, 9.5, "pit"), box(7, 2.5, 7, 9, 3, 9, "pit_d")],
    "loquat_stone": berries("pit", 3, 1.6, 28),
    "pomegranate_seeds": berries("pomegranate", 7, 1.0, 29),
    "fig_cuttings": [box(7.6, 0, 7.6, 8.4, 9, 8.4, "stem"), box(6, 6, 7.6, 10, 6.4, 8.4, "fig"), box(6.5, 2, 7.6, 9.5, 2.4, 8.4, "fig")],
    "quince_pip": berries("seed", 5, 1.1, 30),
    "olive_stone": [box(6.8, 0, 7.0, 9.2, 2.6, 9.0, "pit")],
    "date_pit": [box(6.6, 0, 7.0, 9.4, 2.4, 9.0, "date_d"), box(7.2, 2.4, 7.3, 8.8, 2.8, 8.7, "date")],
    "lychee_seed": [box(6.5, 0, 6.5, 9.5, 3.0, 9.5, "pit")],
    "passionfruit_seeds": berries("ink", 6, 0.9, 31),
    "vanilla_cuttings": [box(7.6, 0, 7.6, 8.4, 9, 8.4, "stem"), box(6, 6, 7.6, 10, 6.4, 8.4, "vanilla_d"), box(6.5, 2, 7.6, 9.5, 2.4, 8.4, "vanilla")],
}


def cask(wood):
    """Sake cask over a vanilla barrel: a full block (so no barrel shows through) with rope hoops,
    a lid and a paper label."""
    w = wood
    e = [box(0, 0, 0, 16, 16, 16, w), box(1, 16, 1, 15, 16.3, 15, w + "_d"), box(6.5, 16.3, 6.5, 9.5, 16.9, 9.5, w + "_d")]
    hoop = "rope" if wood != "copper" else "iron"
    for y in (1.5, 13):
        e.append(box(-0.25, y, -0.25, 16.25, y + 1.4, 16.25, hoop))
    for x in (3.9, 7.9, 11.9):                                   # staves
        e.append(box(x, 0, -0.1, x + 0.3, 16, 16.1, w + "_d"))
    e += [box(5, 4.5, -0.35, 11, 11.5, -0.25, "paper"), box(7.5, 5.5, -0.4, 8.5, 10.5, -0.35, "ink"), box(5, 11, -0.4, 11, 11.5, -0.35, "red")]
    if wood == "copper":
        e += [box(7.4, 3, -0.8, 8.6, 4, -0.25, "iron"), box(-0.3, 7, 6, 0, 9, 10, "copper_d"), box(16, 7, 6, 16.3, 9, 10, "copper_d")]
    return e


def still():
    """Steam Still over a barrel: full copper boiler (hides the vanilla barrel), iron bands,
    chimney, fire window, gauge, valve wheel and paper label - front is north, like the cask."""
    e = [box(0, 0, 0, 16, 16, 16, "copper"),
         box(1, 16, 1, 15, 16.3, 15, "copper_d"), box(6.5, 16.3, 6.5, 9.5, 16.9, 9.5, "copper_d"),
         box(-0.25, 1.5, -0.25, 16.25, 2.9, 16.25, "iron"), box(-0.25, 13, -0.25, 16.25, 14.4, 16.25, "iron"),
         box(6.6, 14.6, 6.6, 9.4, 16.6, 9.4, "iron_d"), box(6.2, 16.6, 6.2, 9.8, 17.15, 9.8, "iron")]
    # fire window (front, low left): iron frame with glowing inside
    e += [box(2.9, 7.0, -0.35, 7.1, 7.3, -0.2, "iron"), box(2.9, 3.1, -0.35, 7.1, 3.4, -0.2, "iron"),
          box(2.9, 3.1, -0.35, 3.2, 7.3, -0.2, "iron"), box(6.8, 3.1, -0.35, 7.1, 7.3, -0.2, "iron"),
          box(3.2, 3.4, -0.3, 6.8, 7.0, -0.22, "glow")]
    # pressure gauge (front right)
    e += [box(8.8, 4.2, -0.32, 11.6, 7.0, -0.2, "iron"), box(9.1, 4.5, -0.35, 11.3, 6.7, -0.3, "paper"),
          box(10.1, 5.3, -0.4, 10.5, 6.6, -0.36, "red")]
    # name plate + valve wheel + side nozzles
    e += [box(4, 9.5, -0.35, 12, 10.5, -0.25, "paper"), box(7.6, 9.8, -0.4, 8.4, 11.5, -0.3, "ink"),
          box(7.5, 12.2, -0.3, 8.5, 13.2, -0.18, "iron"), box(6.2, 12.5, -0.28, 9.8, 12.9, -0.2, "copper_l"),
          box(7.8, 11.2, -0.28, 8.2, 14.2, -0.2, "copper_l"),
          box(-0.7, 8.0, 7.2, 0, 9.2, 8.8, "copper_l"), box(16, 8.0, 7.2, 16.7, 9.2, 8.8, "copper_l")]
    return e

# ------------------------------------------------------------------ crop sprites (16x16, 3 stages)


def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[15 - y][x] = c + (255,)


def sprite(name, col, stage):
    rnd = random.Random(sum(map(ord, name)) * 7 + stage)
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    L, D = PALETTE["leaf"], PALETTE["leaf_d"]
    fruit, fruit_l = PALETTE[col], PALETTE[col + "_l"]
    h = [5, 9, 13][stage]
    if name in ("barley", "rice", "rye", "maize"):
        for i in range(7):
            x, hh = 1 + i * 2, h - rnd.randint(0, 2)
            for y in range(hh):
                put(px, x, y, PALETTE["stem"] if stage < 2 else PALETTE["grain_d"])
            if stage >= 1:
                for k in range(3):
                    put(px, x + (k % 2), hh - k, fruit if stage == 2 else L)
    elif name in ("hops", "grape", "passionfruit", "vanilla"):
        for y in range(h + 2):                                  # a trellis pole the vine climbs
            put(px, 8, y, PALETTE["rope"] if name == "hops" else PALETTE["spruce"])
        for y in range(1, h):
            for d in (-2, -1, 1, 2):
                if rnd.random() < 0.55:
                    put(px, 8 + d + rnd.randint(-1, 1), y, L if rnd.random() < 0.6 else D)
        if stage == 2:
            for _ in range(6):
                x, y = rnd.randint(4, 12), rnd.randint(3, h - 1)
                for dx, dy in ((0, 0), (1, 0), (0, -1), (1, -1), (0, -2)):
                    put(px, x + dx, y + dy, fruit if (dx + dy) % 2 else fruit_l)
    elif name == "ginger":
        for i in range(5):
            x = 3 + i * 2
            for y in range(h):
                put(px, x + (y // 4) * (1 if i % 2 else -1), y, L if y % 3 else D)
        if stage == 2:
            for x in range(5, 11):
                put(px, x, 0, fruit)
    else:                                                       # bushes and dwarf fruit trees
        trunk = name in ("ume", "yuzu", "peach", "cherry", "apple", "pear", "apricot", "mikan",
                         "persimmon", "loquat", "pomegranate", "fig", "quince", "olive", "date", "lychee")
        if trunk:
            for y in range(max(2, h // 2)):
                put(px, 8, y, PALETTE["spruce"])
        lo = h // 2 if trunk else 0
        for y in range(lo, h):
            wd = min(6, 2 + (y - lo) // 2) if not trunk else min(6, 3 + min(y - lo, h - y))
            for x in range(8 - wd, 8 + wd):
                if rnd.random() < 0.75:
                    put(px, x, y, L if rnd.random() < 0.6 else D)
        if stage == 2:
            for _ in range(9 if name in ("blueberry", "juniper", "strawberry") else 6):
                put(px, rnd.randint(3, 12), rnd.randint(max(1, lo), h - 1), fruit if rnd.random() < 0.6 else fruit_l)
        if name == "cherry" and stage == 1:
            for _ in range(5):
                put(px, rnd.randint(4, 11), rnd.randint(lo, h - 1), (255, 190, 210))   # blossom
    return png(px)


def add_sounds(root, namespace):
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "sounds", "out", namespace)
    if not os.path.isdir(src):
        print("note: no sounds in", src, "- run sounds/make_sounds.py first")
        return
    shutil.copytree(src, os.path.join(root, "assets", namespace), dirs_exist_ok=True)


def main():
    global ROOT
    out = sys.argv[1]
    ROOT = os.path.join(out, "Sakagura-pack")
    shutil.rmtree(ROOT, ignore_errors=True)
    write("pack.mcmeta", {"pack": {"description": "Sakagura (brewing + garden)", "pack_format": 64,
                                   "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}})
    write("assets/sakagura/textures/item/palette.png", palette_png())
    for name, es in ITEMS.items():
        model(name, es)
    for wid, col in WOODS:
        model("cask_" + wid, cask(col))
    model("still", still())
    for i, (name, col) in enumerate(FRUITS):
        for st in range(3):
            tex = f"{name}_stage{st}"
            write(f"assets/sakagura/textures/block/{tex}.png", sprite(name, col, st))
            write(f"assets/sakagura/models/block/{tex}.json", {"parent": "minecraft:block/crop", "textures": {"crop": f"sakagura:block/{tex}"}})
            # items/ definition for every block model - without it the 26.2 client shows magenta
            # cubes for the Garden.java displays (same fix as Kitchen's crop stages).
            write(f"assets/sakagura/items/block/{tex}.json", {"model": {"type": "minecraft:model", "model": f"sakagura:block/{tex}"}})
    add_sounds(ROOT, "sakagura")
    zpath = os.path.join(out, "Sakagura-pack.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(ROOT):
            for fn in sorted(files):
                full = os.path.join(dirpath, fn)
                z.write(full, os.path.relpath(full, ROOT).replace(os.sep, "/"))
    print("wrote", zpath, "with", len(ITEMS) + len(WOODS) + 1, "item models and", len(FRUITS) * 3, "crop stage models")


if __name__ == "__main__":
    main()
