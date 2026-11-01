"""Bouw block lines: own pixel-art textures + vanilla-shaped models for a full line of building pieces
(block, stairs, slab, wall, fence, gate, trapdoor, door, button, plate). Everything is drawn here, no copied assets.

The plugin puts a real vanilla block (stone bricks / oak planks / ...) in the world and covers it with item
displays showing these models; the models therefore follow the vanilla shapes, slightly inflated where thin.
Used by make_bouw_pack.py (writes into the same pack + src/main/resources/lines.json).
"""
import json, random

# id, display name, kind (stone = pickaxe, wood = axe), recipe ingredients (3x A + 1x B -> 4 blocks), style
LINES = [
    ("cogwork_brass", "Cogwork Brass", "stone", "GOLD_INGOT", "IRON_NUGGET", "cogs"),
    ("pipework_copper", "Pipework Copper", "stone", "COPPER_INGOT", "IRON_INGOT", "pipes"),
    ("verdigris_scale", "Verdigris Scale", "stone", "OXIDIZED_COPPER", "PRISMARINE_SHARD", "scales"),
    ("boiler_brick", "Boiler Brick", "stone", "BRICK", "COPPER_INGOT", "boiler"),
    ("blackened_iron", "Blackened Iron", "stone", "IRON_INGOT", "COAL", "plates"),
    ("kawara_grey", "Kawara Grey", "stone", "DEEPSLATE_TILES", "CLAY_BALL", "kawara"),
    ("moss_garden_stone", "Moss Garden Stone", "stone", "COBBLESTONE", "MOSS_BLOCK", "mossy"),
    ("shikkui_plaster", "Shikkui Plaster", "stone", "CALCITE", "WHITE_DYE", "plaster"),
    ("sakura_lattice", "Sakura Lattice", "wood", "CHERRY_PLANKS", "STICK", "lattice"),
    ("shoji_paper", "Shoji Paper", "wood", "PAPER", "BIRCH_PLANKS", "shoji"),
    ("tatami_weave", "Tatami Weave", "wood", "WHEAT", "GREEN_DYE", "tatami"),
    ("vermilion_lacquer", "Vermilion Lacquer", "wood", "MANGROVE_PLANKS", "RED_DYE", "lacquer"),
    ("yakisugi_charred", "Yakisugi Charred Wood", "wood", "DARK_OAK_PLANKS", "CHARCOAL", "charred"),
    ("bamboo_slat", "Bamboo Slat", "wood", "BAMBOO", "STRING", "bamboo"),
]
LINES += [
    # plain building blocks
    ("slate_tile", "Slate Tile", "stone", "COBBLED_DEEPSLATE", "FLINT", "slate", (92, 100, 112)),
    ("slate_cobble", "Slate Cobble", "stone", "DEEPSLATE", "GRAVEL", "cobble", (88, 94, 104)),
    ("marble_white", "White Marble", "stone", "QUARTZ_BLOCK", "CALCITE", "marble", (236, 236, 232)),
    ("marble_black", "Black Marble", "stone", "BLACKSTONE", "QUARTZ", "marble", (40, 40, 46)),
    ("limestone_brick", "Limestone Brick", "stone", "SMOOTH_SANDSTONE", "BONE_MEAL", "bricks", (214, 204, 170)),
    ("flagstone", "Flagstone", "stone", "ANDESITE", "STONE", "flags", (128, 130, 134)),
    ("basalt_brick", "Basalt Brick", "stone", "BASALT", "COAL", "bricks", (70, 72, 78)),
    ("sandy_brick", "Sandy Brick", "stone", "SANDSTONE", "CLAY_BALL", "bricks", (200, 168, 112)),
    ("cement_plain", "Plain Cement", "stone", "LIGHT_GRAY_CONCRETE_POWDER", "CLAY_BALL", "cement", (160, 160, 158)),
    ("dark_brick", "Dark Brick", "stone", "NETHER_BRICKS", "COAL", "bricks", (74, 40, 44)),
    ("terracotta_shingle", "Terracotta Shingle", "stone", "TERRACOTTA", "BRICK", "shingle", (176, 96, 64)),
    ("walnut_planks", "Walnut Planks", "wood", "DARK_OAK_PLANKS", "COCOA_BEANS", "planks", (92, 62, 44)),
    ("cedar_planks", "Cedar Planks", "wood", "SPRUCE_PLANKS", "ORANGE_DYE", "planks", (170, 98, 62)),
    ("weathered_planks", "Weathered Planks", "wood", "OAK_PLANKS", "GRAY_DYE", "planks", (148, 142, 132)),
    ("whitewash_planks", "Whitewash Planks", "wood", "BIRCH_PLANKS", "WHITE_DYE", "planks", (226, 220, 206)),
    ("driftwood", "Driftwood", "wood", "STRIPPED_OAK_LOG", "KELP", "driftwood", (176, 164, 142)),
    ("thatch", "Thatch", "wood", "HAY_BLOCK", "VINE", "thatch", (206, 176, 82)),
    ("painted_blue", "Blue Painted Boards", "wood", "JUNGLE_PLANKS", "BLUE_DYE", "planks", (60, 96, 164)),
    ("painted_green", "Green Painted Boards", "wood", "ACACIA_PLANKS", "GREEN_DYE", "planks", (66, 128, 84)),
]
PIECES = ["block", "stairs", "slab", "wall", "fence", "gate", "trapdoor", "door", "button", "plate"]


# ------------------------------------------------------------------------------ textures
def canvas(c):
    return [[c] * 16 for _ in range(16)]


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def noise(px, rnd, amt=6):
    for y in range(16):
        for x in range(16):
            d = rnd.randint(-amt, amt)
            px[y][x] = shade(px[y][x], d)


def rect(px, x1, y1, x2, y2, c):
    for y in range(max(0, y1), min(16, y2)):
        for x in range(max(0, x1), min(16, x2)):
            px[y][x] = c


def disc(px, cx, cy, r, c):
    for y in range(16):
        for x in range(16):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                px[y][x] = c


def texture(style, rnd, color=None):
    c = color or (128, 128, 128)
    if style == "cogs":
        px = canvas((196, 158, 64))
        for ox, oy in ((0, 0), (8, 0), (0, 8), (8, 8)):
            disc(px, ox + 4, oy + 4, 3, (150, 116, 40))
            disc(px, ox + 4, oy + 4, 1, (60, 46, 20))
            for dx, dy in ((0, -4), (0, 4), (-4, 0), (4, 0)):
                rect(px, ox + 4 + dx - (0 if dx == 0 else 0), oy + 4 + dy, ox + 5 + dx, oy + 5 + dy, (150, 116, 40))
        noise(px, rnd, 7)
    elif style == "pipes":
        px = canvas((170, 98, 54))
        for y0 in (2, 10):
            rect(px, 0, y0, 16, y0 + 4, (205, 128, 72))
            rect(px, 0, y0, 16, y0 + 1, (232, 164, 104))
            rect(px, 0, y0 + 3, 16, y0 + 4, (120, 66, 36))
            for x0 in (3, 11):
                rect(px, x0, y0 - 1, x0 + 2, y0 + 5, (96, 54, 30))
        noise(px, rnd, 5)
    elif style == "scales":
        px = canvas((70, 140, 124))
        for row in range(0, 16, 4):
            for col in range(-2, 16, 4):
                off = 2 if (row // 4) % 2 else 0
                disc(px, col + off + 2, row + 2, 2, (96, 172, 150))
                rect(px, col + off, row + 3, col + off + 4, row + 4, (44, 100, 88))
        noise(px, rnd, 6)
    elif style == "boiler":
        px = canvas((140, 58, 46))
        for y in range(0, 16, 4):
            rect(px, 0, y + 3, 16, y + 4, (76, 40, 34))
            off = 4 if (y // 4) % 2 else 0
            for x in range(off, 16, 8):
                rect(px, x, y, x + 1, y + 4, (76, 40, 34))
        for x in (1, 9):
            disc(px, x, 1, 0, (200, 170, 120))
        noise(px, rnd, 7)
    elif style == "plates":
        px = canvas((62, 64, 70))
        for ox, oy in ((0, 0), (8, 0), (0, 8), (8, 8)):
            rect(px, ox, oy, ox + 8, oy + 1, (96, 98, 106))
            rect(px, ox, oy, ox + 1, oy + 8, (96, 98, 106))
            rect(px, ox + 7, oy, ox + 8, oy + 8, (34, 34, 40))
            rect(px, ox, oy + 7, ox + 8, oy + 8, (34, 34, 40))
            for dx, dy in ((2, 2), (5, 2), (2, 5), (5, 5)):
                rect(px, ox + dx, oy + dy, ox + dx + 1, oy + dy + 1, (126, 128, 138))
        noise(px, rnd, 4)
    elif style == "kawara":
        px = canvas((70, 74, 82))
        for row in range(0, 16, 4):
            for x in range(16):
                curve = [3, 2, 1, 1, 1, 1, 2, 3][x % 8]
                rect(px, x, row + curve, x + 1, row + 4, (92, 98, 108) if (x // 8) % 2 == 0 else (84, 90, 100))
                px[row + 3][x] = (36, 38, 44)
        noise(px, rnd, 5)
    elif style == "mossy":
        px = canvas((124, 126, 122))
        for _ in range(7):
            x, y = rnd.randint(0, 13), rnd.randint(0, 13)
            rect(px, x, y, x + rnd.randint(3, 5), y + rnd.randint(2, 4), shade((124, 126, 122), rnd.randint(-22, 14)))
        for _ in range(26):
            x, y = rnd.randint(0, 15), rnd.randint(0, 15)
            px[y][x] = (74 + rnd.randint(0, 24), 130 + rnd.randint(0, 30), 56)
        noise(px, rnd, 5)
    elif style == "plaster":
        px = canvas((232, 228, 216))
        for _ in range(3):
            x, y = rnd.randint(2, 12), rnd.randint(2, 12)
            for i in range(rnd.randint(3, 6)):
                px[min(15, y + i)][min(15, x + i // 2)] = (190, 184, 170)
        noise(px, rnd, 4)
    elif style == "lattice":
        px = canvas((214, 156, 156))
        for i in range(0, 16, 4):
            rect(px, i, 0, i + 1, 16, (130, 84, 80))
            rect(px, 0, i, 16, i + 1, (130, 84, 80))
        rect(px, 0, 0, 16, 1, (110, 70, 66))
        rect(px, 0, 15, 16, 16, (110, 70, 66))
        noise(px, rnd, 5)
    elif style == "shoji":
        px = canvas((240, 232, 206))
        for i in (0, 5, 10, 15):
            rect(px, i, 0, i + 1, 16, (110, 80, 52))
            rect(px, 0, i, 16, i + 1, (110, 80, 52))
        noise(px, rnd, 3)
    elif style == "tatami":
        px = canvas((148, 156, 88))
        for y in range(0, 16, 2):
            rect(px, 0, y, 16, y + 1, (164, 172, 100))
        for x in range(0, 16, 4):
            rect(px, x, 0, x + 1, 16, (124, 132, 72))
        rect(px, 0, 0, 16, 1, (34, 46, 30))
        rect(px, 0, 15, 16, 16, (34, 46, 30))
        noise(px, rnd, 5)
    elif style == "lacquer":
        px = canvas((178, 38, 30))
        for y in range(0, 16, 4):
            rect(px, 0, y + 3, 16, y + 4, (104, 20, 18))
            rect(px, 2, y + 1, 9, y + 2, (210, 80, 62))
        noise(px, rnd, 4)
    elif style == "charred":
        px = canvas((34, 30, 28))
        for x in range(0, 16, 3):
            rect(px, x, 0, x + 1, 16, (62, 54, 48))
        for _ in range(8):
            x, y = rnd.randint(0, 14), rnd.randint(0, 14)
            rect(px, x, y, x + 1, y + rnd.randint(2, 5), (14, 12, 12))
        noise(px, rnd, 5)
    elif style == "slate":
        px = canvas(c)
        for y in range(0, 16, 3):
            rect(px, 0, y, 16, y + 1, shade(c, 18))
            rect(px, 0, y + 2, 16, y + 3, shade(c, -22))
        for _ in range(5):
            x, y = rnd.randint(0, 13), rnd.randint(0, 14)
            rect(px, x, y, x + 3, y + 1, shade(c, -14))
        noise(px, rnd, 5)
    elif style == "cobble":
        px = canvas(shade(c, -26))
        for _ in range(9):
            x, y = rnd.randint(0, 12), rnd.randint(0, 12)
            w, h = rnd.randint(4, 6), rnd.randint(3, 5)
            col = shade(c, rnd.randint(-8, 14))
            rect(px, x, y, x + w, y + h, col)
            rect(px, x, y, x + w, y + 1, shade(col, 14))
            rect(px, x, y + h - 1, x + w, y + h, shade(col, -16))
        noise(px, rnd, 4)
    elif style == "marble":
        px = canvas(c)
        light = sum(c) > 400
        vein = shade(c, -50 if light else 60)
        x = rnd.randint(0, 15)
        for y in range(16):
            x = max(0, min(15, x + rnd.randint(-1, 1)))
            px[y][x] = vein
            if rnd.random() < 0.4 and x < 15:
                px[y][x + 1] = shade(vein, 14 if light else -10)
        x = rnd.randint(0, 15)
        for y in range(16):
            x = max(0, min(15, x + rnd.randint(-1, 1)))
            if y % 3:
                px[y][x] = shade(c, -24 if light else 30)
        noise(px, rnd, 3)
    elif style == "bricks":
        px = canvas(c)
        mortar = shade(c, 40 if sum(c) < 400 else -46)
        for y in range(0, 16, 4):
            rect(px, 0, y + 3, 16, y + 4, mortar)
            off = 4 if (y // 4) % 2 else 0
            for x in range(off, 16, 8):
                rect(px, x, y, x + 1, y + 3, mortar)
            for x in range(0, 16, 8):
                bx = (x + off) % 16
                tint = rnd.randint(-10, 10)
                rect(px, bx + 1, y, min(16, bx + 8), y + 3, shade(c, tint))
        noise(px, rnd, 4)
    elif style == "flags":
        px = canvas(shade(c, -34))
        for (x, y, w, h) in ((0, 0, 7, 6), (7, 0, 9, 5), (0, 6, 5, 6), (5, 5, 6, 7), (11, 5, 5, 6), (0, 12, 8, 4), (8, 12, 8, 4), (11, 11, 5, 1)):
            col = shade(c, rnd.randint(-8, 12))
            rect(px, x, y, x + w - 1, y + h - 1, col)
        noise(px, rnd, 5)
    elif style == "cement":
        px = canvas(c)
        for _ in range(30):
            px[rnd.randint(0, 15)][rnd.randint(0, 15)] = shade(c, rnd.choice((-26, 22)))
        noise(px, rnd, 4)
    elif style == "shingle":
        px = canvas(c)
        for row in range(0, 16, 4):
            for x in range(16):
                edge = [3, 2, 1, 1, 1, 1, 2, 3][(x + (4 if (row // 4) % 2 else 0)) % 8]
                rect(px, x, row + edge, x + 1, row + 4, shade(c, 10 if (x // 4) % 2 else -6))
                px[row + 3][x] = shade(c, -52)
        noise(px, rnd, 5)
    elif style == "planks":
        px = canvas(c)
        for y in range(0, 16, 4):
            rect(px, 0, y + 3, 16, y + 4, shade(c, -40))
            rect(px, 0, y, 16, y + 1, shade(c, 14))
            sx = rnd.randint(2, 13)
            rect(px, sx, y, sx + 1, y + 3, shade(c, -34))
            for _ in range(3):
                gx = rnd.randint(0, 12)
                rect(px, gx, y + rnd.randint(1, 2), gx + rnd.randint(2, 4), y + 3 - 1 + 0, shade(c, rnd.choice((-12, 10))))
        noise(px, rnd, 4)
    elif style == "driftwood":
        px = canvas(c)
        for y in range(16):
            tone = (y * 7 + rnd.randint(0, 2)) % 5
            rect(px, 0, y, 16, y + 1, shade(c, (-18, -6, 0, 8, 16)[tone]))
        for _ in range(6):
            x, y = rnd.randint(0, 11), rnd.randint(0, 15)
            rect(px, x, y, x + rnd.randint(3, 5), y + 1, shade(c, -44))
        noise(px, rnd, 5)
    elif style == "thatch":
        px = canvas(c)
        for x in range(0, 16, 2):
            for y in range(16):
                if rnd.random() < 0.7:
                    px[y][x] = shade(c, rnd.randint(-30, 22))
        for y in (5, 11):
            rect(px, 0, y, 16, y + 1, shade(c, -58))
        noise(px, rnd, 5)
    else:  # bamboo
        px = canvas((148, 170, 78))
        for x in (0, 5, 10):
            rect(px, x, 0, x + 1, 16, (96, 118, 48))
            rect(px, x + 3, 0, x + 4, 16, (176, 198, 104))
        for y in (4, 12):
            rect(px, 0, y, 16, y + 1, (108, 130, 54))
        noise(px, rnd, 5)
    return px


# ------------------------------------------------------------------------------ models
def box(x1, y1, z1, x2, y2, z2, e=0.0):
    f = {d: {"texture": "#t"} for d in ("north", "south", "east", "west", "up", "down")}
    return {"from": [x1 - e, y1 - e, z1 - e], "to": [x2 + e, y2 + e, z2 + e], "faces": f}


def flip(elems):
    out = []
    for el in elems:
        n = json.loads(json.dumps(el))
        n["from"][1], n["to"][1] = 16 - el["to"][1], 16 - el["from"][1]
        n["faces"]["up"], n["faces"]["down"] = el["faces"]["down"], el["faces"]["up"]
        out.append(n)
    return out


E = 0.15   # thin parts are drawn this much bigger so the vanilla block underneath never peeks through


def model_set():
    m = {}
    m["block"] = [box(0, 0, 0, 16, 16, 16)]
    m["slab_bottom"] = [box(0, 0, 0, 16, 8, 16)]
    m["slab_top"] = [box(0, 8, 0, 16, 16, 16)]
    st = {"straight": [box(0, 0, 0, 16, 8, 16), box(8, 8, 0, 16, 16, 16)],
          "outer": [box(0, 0, 0, 16, 8, 16), box(8, 8, 8, 16, 16, 16)],
          "inner": [box(0, 0, 0, 16, 8, 16), box(8, 8, 0, 16, 16, 16), box(0, 8, 8, 8, 16, 16)]}
    for k, v in st.items():
        m["stairs_%s_bottom" % k] = v
        m["stairs_%s_top" % k] = flip(v)
    m["wall_post"] = [box(4, 0, 4, 12, 16, 12, E)]
    m["wall_arm_low"] = [box(5, 0, 0, 11, 14, 8, E)]
    m["wall_arm_tall"] = [box(5, 0, 0, 11, 16, 8, E)]
    m["wall_inv"] = [box(4, 0, 4, 12, 16, 12, E), box(5, 0, 0, 11, 14, 5, E), box(5, 0, 11, 11, 14, 16, E)]
    m["fence_post"] = [box(6, 0, 6, 10, 16, 10, E)]
    m["fence_arm"] = [box(7, 6, 0, 9, 9, 6, E), box(7, 12, 0, 9, 15, 6, E)]
    m["fence_inv"] = m["fence_post"] + [box(7, 6, 0, 9, 9, 6, E), box(7, 12, 0, 9, 15, 6, E), box(7, 6, 10, 9, 9, 16, E), box(7, 12, 10, 9, 15, 16, E)]
    posts = [box(0, 5, 7, 2, 16, 9, E), box(14, 5, 7, 16, 16, 9, E)]
    m["gate"] = posts + [box(2, 6, 7, 14, 9, 9, E), box(2, 12, 7, 14, 15, 9, E), box(6, 9, 7, 10, 12, 9, E)]
    m["gate_open"] = posts + [box(0, 6, 9, 2, 9, 13, E), box(0, 12, 9, 2, 15, 13, E), box(14, 6, 9, 16, 9, 13, E),
                              box(14, 12, 9, 16, 15, 13, E), box(0, 5, 13, 2, 16, 15, E), box(14, 5, 13, 16, 16, 15, E)]
    m["trapdoor_bottom"] = [box(0, 0, 0, 16, 3, 16, E)]
    m["trapdoor_top"] = [box(0, 13, 0, 16, 16, 16, E)]
    m["trapdoor_open"] = [box(0, 0, 13, 16, 16, 16, E)]
    m["door"] = [box(0, 0, 0, 3, 16, 16, E)]
    m["door_open_left"] = [box(0, 0, 0, 16, 16, 3, E)]
    m["door_open_right"] = [box(0, 0, 13, 16, 16, 16, E)]
    m["button"] = [box(5, 0, 6, 11, 2, 10, E)]
    m["button_pressed"] = [box(5, 0, 6, 11, 1, 10, E)]
    m["plate"] = [box(1, 0, 1, 15, 1, 15, E)]
    m["plate_pressed"] = [box(1, 0, 1, 15, 0.5, 15, E)]
    return m


# inventory model per piece: which model shows in the hand / GUI
INV = {"block": "block", "stairs": "stairs_straight_bottom", "slab": "slab_bottom", "wall": "wall_inv", "fence": "fence_inv",
       "gate": "gate", "trapdoor": "trapdoor_bottom", "door": "door", "button": "button", "plate": "plate"}

DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "head": {"scale": [0.6, 0.6, 0.6]},
}


def generate(out, resdir, png):
    """out = pack writer with .write(rel, data); png = png encoder taking [[rgba]]. Returns the lines list."""
    models = model_set()
    lines = []
    for (lid, name, kind, a, b, style, *col) in LINES:
        rnd = random.Random(lid)
        px = texture(style, rnd, col[0] if col else None)
        out.write("assets/bouw/textures/block/%s.png" % lid, png([[(r, g, bl, 255) for (r, g, bl) in row] for row in px]))
        for mname, elems in models.items():
            mj = {"textures": {"t": "bouw:block/%s" % lid, "particle": "bouw:block/%s" % lid}, "elements": elems, "display": DISPLAY}
            out.write("assets/bouw/models/item/%s_c_%s.json" % (lid, mname), mj)
            out.write("assets/bouw/items/%s_c_%s.json" % (lid, mname), {"model": {"type": "minecraft:model", "model": "bouw:item/%s_c_%s" % (lid, mname)}})
        for piece, mname in INV.items():
            out.write("assets/bouw/items/%s_%s.json" % (lid, piece), {"model": {"type": "minecraft:model", "model": "bouw:item/%s_c_%s" % (lid, mname)}})
        lines.append({"id": lid, "name": name, "kind": kind, "a": a, "b": b})
    json.dump({"pieces": PIECES, "lines": lines}, open(resdir + "/lines.json", "w", encoding="utf-8"), indent=1)
    return lines
