"""Own 16x16 textures for the Steamworks decorative blocks (steampunk / Japanese).

Each painter returns a 16x16 grid of (r, g, b) tuples. Used by make_blocks_pack.py.
Keep DECOR in step with DecorType.java (id, key = reserved note-block state).
"""
import math
import random

COPPER = (182, 106, 52)
BRASS = (190, 152, 64)
GOLD = (232, 196, 84)
IRON = (74, 74, 80)
STEEL = (122, 124, 132)
VERDIGRIS = (88, 150, 128)
LACQUER = (152, 34, 30)
VERMILION = (196, 58, 38)
BLACK = (30, 28, 30)
PAPER = (240, 230, 204)
LIGHT_WOOD = (196, 170, 120)
DARK_WOOD = (92, 60, 38)
SAKURA = (214, 152, 142)
STRAW = (204, 186, 116)
STONE = (128, 128, 126)
DARK_STONE = (78, 80, 84)
PLASTER = (232, 228, 218)


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return tuple(clamp(v * f) for v in c)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Canvas:
    def __init__(self, seed, base, noise=8):
        self.r = random.Random(seed)
        self.px = [[self.jit(base, noise) for _ in range(16)] for _ in range(16)]

    def jit(self, c, n):
        d = self.r.randint(-n, n)
        return tuple(clamp(v + d) for v in c)

    def set(self, x, y, c, noise=0):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = self.jit(c, noise) if noise else c

    def rect(self, x0, y0, x1, y1, c, noise=0):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c, noise)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.set(x, y0, c)
            self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c)
            self.set(x1, y, c)

    def bevel(self, x0, y0, x1, y1, light=1.18, dark=0.72):
        """Raised edge: light top/left, dark bottom/right."""
        for x in range(x0, x1 + 1):
            self.px[y0][x] = shade(self.px[y0][x], light)
            self.px[y1][x] = shade(self.px[y1][x], dark)
        for y in range(y0, y1 + 1):
            self.px[y][x0] = shade(self.px[y][x0], light)
            self.px[y][x1] = shade(self.px[y][x1], dark)

    def rivet(self, x, y, metal):
        self.set(x, y, shade(metal, 1.45))
        self.set(x + 1, y + 1, shade(metal, 0.55))
        self.set(x + 1, y, shade(metal, 1.05))
        self.set(x, y + 1, shade(metal, 0.85))

    def grain(self, horizontal, base, rows=(3, 7, 11, 15), dark=0.78):
        for i in range(16):
            for j in range(16):
                x, y = (i, j) if horizontal else (j, i)
                wave = math.sin((x if horizontal else y) * 0.9 + (y if horizontal else x) * 2.1) * 0.06
                self.px[y][x] = self.jit(shade(base, 1 + wave), 6)
        for r in rows:  # plank seams
            for k in range(16):
                if horizontal:
                    self.set(k, r, shade(base, dark))
                else:
                    self.set(r, k, shade(base, dark))

    def circle(self, cx, cy, r0, r1, c):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r0 <= d <= r1:
                    self.set(x, y, c)


# ---------------------------------------------------------------- steampunk metal

def riveted_copper(seed):
    c = Canvas(seed, COPPER, 9)
    c.bevel(0, 0, 15, 15)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13), (7, 1), (7, 13), (1, 7), (13, 7)):
        c.rivet(x, y, COPPER)
    return c.px


def brass_tiles(seed):
    c = Canvas(seed, BRASS, 8)
    for ty in (0, 8):
        for tx in (0, 8):
            c.bevel(tx, ty, tx + 7, ty + 7, 1.2, 0.68)
            c.set(tx + 3, ty + 3, shade(BRASS, 1.3))
    return c.px


def cast_iron_grate(seed):
    c = Canvas(seed, IRON, 5)
    for gy in range(4):
        for gx in range(4):
            x0, y0 = 1 + gx * 4, 1 + gy * 4
            c.rect(x0, y0, x0 + 1, y0 + 1, (14, 12, 14))
            c.set(x0, y0 - 1, shade(IRON, 1.25))
    c.bevel(0, 0, 15, 15, 1.25, 0.6)
    return c.px


def pipe_wall(seed):
    c = Canvas(seed, (40, 36, 34), 4)
    for p0 in (0, 5, 10):
        for x in range(p0, p0 + 5):
            t = abs((x - p0) - 2) / 2.5
            col = shade(COPPER, 1.25 - t * 0.55)
            for y in range(16):
                c.set(x, y, col, 4)
        for by in (3, 11):
            c.rect(p0, by, p0 + 4, by + 1, shade(BRASS, 1.1))
            c.set(p0, by, shade(BRASS, 1.4))
    return c.px


def gear_panel(seed):
    c = Canvas(seed, IRON, 5)
    c.circle(8, 8, 3.2, 5.0, BRASS)
    for k in range(8):
        a = k * math.pi / 4
        x, y = int(8 + math.cos(a) * 5.6 - 0.5), int(8 + math.sin(a) * 5.6 - 0.5)
        c.set(x, y, shade(BRASS, 1.1))
    c.circle(8, 8, 0, 1.5, shade(BRASS, 1.3))
    c.bevel(0, 0, 15, 15)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rivet(x, y, STEEL)
    return c.px


def boiler_plate(seed):
    c = Canvas(seed, COPPER, 9)
    for x in range(16):
        c.set(x, 7, shade(COPPER, 0.6))
        c.set(x, 8, shade(COPPER, 1.2))
    for x in range(1, 16, 3):
        c.rivet(x, 5, COPPER)
        c.rivet(x, 9, COPPER)
    c.circle(11.5, 12.5, 0, 2.6, PAPER)
    c.circle(11.5, 12.5, 2.6, 3.4, BRASS)
    c.set(11, 12, (200, 30, 30))
    c.set(12, 11, (200, 30, 30))
    return c.px


def brass_diamond(seed):
    c = Canvas(seed, BRASS, 8)
    for y in range(16):
        for x in range(16):
            if (x + y) % 6 == 0 or (x - y) % 6 == 0:
                c.set(x, y, shade(BRASS, 0.7))
            if (x + y) % 6 == 0 and (x - y) % 6 == 0:
                c.set(x, y, shade(BRASS, 1.4))
    return c.px


def verdigris_plate(seed):
    c = Canvas(seed, COPPER, 9)
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.7 + seed) + math.cos(y * 0.6 - seed * 0.3) + c.r.random() * 0.9
            if v > 1.1:
                c.set(x, y, VERDIGRIS, 10)
    c.bevel(0, 0, 15, 15)
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        c.rivet(x, y, COPPER)
    return c.px


# ---------------------------------------------------------------- mixed

def lacquer_brass_corners(seed):
    # red lacquered boards held by brass straps top and bottom, rivets on the straps
    c = Canvas(seed, LACQUER, 6)
    c.grain(False, LACQUER, rows=(5, 10), dark=0.8)
    for y0 in (1, 12):
        c.rect(0, y0, 15, y0 + 2, BRASS, 5)
        for x in range(16):
            c.set(x, y0, shade(BRASS, 1.25))
            c.set(x, y0 + 2, shade(BRASS, 0.7))
        for x in (2, 7, 12):
            c.set(x, y0 + 1, shade(BRASS, 1.5))
    return c.px


def copper_shoji(seed):
    c = Canvas(seed, PAPER, 4)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15) or x in (5, 10) or y in (5, 10):
                c.set(x, y, shade(COPPER, 1.0 if x not in (0, 15) and y not in (0, 15) else 0.85), 6)
    return c.px


def gear_inlay_wood(seed):
    c = Canvas(seed, DARK_WOOD, 6)
    c.grain(True, DARK_WOOD, rows=(0, 15))
    c.circle(8, 8, 2.2, 3.6, BRASS)
    for k in range(6):
        a = k * math.pi / 3
        c.set(int(8 + math.cos(a) * 4.2), int(8 + math.sin(a) * 4.2), shade(BRASS, 1.2))
    return c.px


def brass_on_lacquer(seed):
    c = Canvas(seed, BLACK, 3)
    c.frame(1, 1, 14, 14, BRASS)
    c.circle(8, 8, 2.6, 3.6, BRASS)            # family crest (mon)
    c.circle(8, 8, 0, 1.2, shade(BRASS, 1.3))
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        c.set(int(8 + math.cos(a) * 2.2), int(8 + math.sin(a) * 2.2), BRASS)
    return c.px


def boiler_tile(seed):
    c = Canvas(seed, COPPER, 8)
    for ty in range(0, 16, 4):
        for tx in range(0, 16, 4):
            c.bevel(tx, ty, tx + 3, ty + 3, 1.15, 0.7)
            c.set(tx + 1, ty + 1, shade(BRASS, 1.3))
    return c.px


def sakura_copper_seams(seed):
    c = Canvas(seed, SAKURA, 6)
    c.grain(True, SAKURA, rows=())
    for r in (4, 9, 14):
        for x in range(16):
            c.set(x, r, COPPER, 6)
    return c.px


def lantern_wall(seed):
    c = Canvas(seed, (255, 222, 150), 5)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 10
            c.px[y][x] = mix(c.px[y][x], (226, 160, 80), d)
    for r in (3, 7, 11):
        for x in range(16):
            c.set(x, r, shade(DARK_WOOD, 1.1))
    c.rect(0, 0, 0, 15, DARK_WOOD)
    c.rect(15, 0, 15, 15, DARK_WOOD)
    return c.px


def gold_black_lacquer(seed):
    c = Canvas(seed, BLACK, 3)
    r = c.r
    for _ in range(4):  # maki-e sakura blossoms
        x, y = r.randint(2, 13), r.randint(2, 10)
        for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
            c.set(x + dx, y + dy, GOLD)
        c.set(x, y, (250, 230, 150))
    for x in range(16):  # a gold wave along the bottom
        y = 13 + int(round(math.sin(x * 0.8) * 1.2))
        c.set(x, y, shade(GOLD, 0.9))
    return c.px


# ---------------------------------------------------------------- Japanese wood & paper

def shoji(seed):
    c = Canvas(seed, PAPER, 3)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15) or x % 5 == 0 or y % 4 == 0:
                c.set(x, y, LIGHT_WOOD, 6)
    return c.px


def red_lacquer_wood(seed):
    c = Canvas(seed, VERMILION, 6)
    c.grain(False, VERMILION, rows=(4, 9, 15), dark=0.75)
    return c.px


def tatami(seed):
    c = Canvas(seed, STRAW, 6)
    for y in range(16):
        for x in range(16):
            if y % 2 == 0:
                c.set(x, y, shade(STRAW, 0.9), 4)
    for y in (0, 1, 14, 15):  # heri (cloth border)
        for x in range(16):
            c.set(x, y, (40, 70, 50), 4)
    return c.px


def woven_bamboo(seed):
    bamboo = (196, 176, 96)
    c = Canvas(seed, bamboo, 5)
    for y in range(16):
        for x in range(16):
            cell = ((x // 4) + (y // 4)) % 2
            edge = (y % 4 == 0) if cell else (x % 4 == 0)
            if edge:
                c.set(x, y, shade(bamboo, 0.66))
            elif cell:
                c.set(x, y, shade(bamboo, 0.92), 4)
    return c.px


def yakisugi(seed):
    c = Canvas(seed, (34, 30, 30), 5)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 7 + int(math.sin(y) * 3)) % 11 == 0:
                c.set(x, y, (12, 10, 10))
            elif (x + y * 5) % 13 == 0:
                c.set(x, y, (70, 62, 58))
    for r in (5, 11):
        for y in range(16):
            c.set(r, y, (16, 14, 14))
    return c.px


def sakura_wood(seed):
    c = Canvas(seed, SAKURA, 6)
    c.grain(True, SAKURA, rows=(3, 7, 11, 15), dark=0.82)
    return c.px


def straw_wall(seed):
    c = Canvas(seed, STRAW, 5)
    for x in range(16):
        f = 0.85 if x % 2 else 1.05
        for y in range(16):
            c.set(x, y, shade(STRAW, f), 6)
    for y in (6, 7):
        for x in range(16):
            c.set(x, y, (120, 92, 50), 4)
    return c.px


def wooden_lattice(seed):
    c = Canvas(seed, (36, 28, 22), 3)
    for y in range(16):
        for x in range(16):
            if (x + y) % 5 == 0 or (x - y) % 5 == 0:
                c.set(x, y, LIGHT_WOOD, 8)
    c.frame(0, 0, 15, 15, shade(LIGHT_WOOD, 0.8))
    return c.px


# ---------------------------------------------------------------- Japanese stone & roof

def kawara_tiles(seed):
    tile = (76, 84, 96)
    c = Canvas(seed, tile, 4)
    for y in range(16):
        for x in range(16):
            row = y // 4
            ox = (x + (2 if row % 2 else 0)) % 4
            t = abs(ox - 1.5) / 2
            c.set(x, y, shade(tile, 1.22 - t * 0.5), 3)
            if y % 4 == 3:
                c.set(x, y, shade(tile, 0.55))
    return c.px


def granite_pavers(seed):
    c = Canvas(seed, (138, 132, 128), 12)
    for y in range(16):
        for x in range(16):
            row = y // 5
            off = 4 if row % 2 else 0
            if y % 5 == 4 or (x + off) % 8 == 7:
                c.set(x, y, (84, 80, 78))
            elif c.r.random() < 0.15:
                c.set(x, y, (180, 170, 166))
    return c.px


def mossy_garden_stone(seed):
    c = Canvas(seed, STONE, 12)
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.55 + seed) * math.cos(y * 0.5) + c.r.random() * 0.6
            if v > 0.55:
                c.set(x, y, (88, 128, 60), 12)
    for y in (5, 11):
        for x in range(16):
            c.set(x, y, shade(STONE, 0.7))
    return c.px


def painted_wall_band(seed):
    c = Canvas(seed, PLASTER, 4)
    c.rect(0, 11, 15, 15, DARK_WOOD, 6)
    for x in range(16):
        c.set(x, 11, shade(DARK_WOOD, 1.3))
        c.set(x, 10, shade(PLASTER, 0.85))
    return c.px


def lantern_stone(seed):
    c = Canvas(seed, STONE, 10)
    c.rect(4, 4, 11, 11, (255, 210, 120), 8)
    c.rect(7, 4, 8, 11, (70, 60, 50))
    c.rect(4, 7, 11, 8, (70, 60, 50))
    c.frame(3, 3, 12, 12, shade(STONE, 0.6))
    c.bevel(0, 0, 15, 15)
    return c.px


def gravel_tile(seed):
    c = Canvas(seed, (150, 146, 140), 14)
    for x in range(16):
        for k in (2, 7, 12):
            y = k + int(round(math.sin(x * 0.7) * 1.3))
            c.set(x, y % 16, (190, 186, 180))
            c.set(x, (y + 1) % 16, (110, 106, 100))
    return c.px


def dark_slate(seed):
    c = Canvas(seed, DARK_STONE, 6)
    for y in range(0, 16, 4):
        for x in range(16):
            c.set(x, y, shade(DARK_STONE, 0.6))
            c.set(x, y + 1, shade(DARK_STONE, 1.2))
        c.set(c.r.randint(0, 15), y + 2, shade(DARK_STONE, 0.7))
    return c.px


def white_plaster(seed):
    c = Canvas(seed, PLASTER, 5)
    for _ in range(6):
        y = c.r.randint(1, 14)
        x0 = c.r.randint(0, 9)
        for x in range(x0, x0 + 6):
            c.set(x, y, shade(PLASTER, 0.94))
    return c.px


# id, display name, style, key (reserved state), hardness, tool, painter
DECOR = [
    ("riveted_copper_plate", "Riveted Copper Plate", "metal", 7, 3.5, "pickaxe", riveted_copper),
    ("brass_tiles", "Brass Tiles", "metal", 8, 3.5, "pickaxe", brass_tiles),
    ("cast_iron_grate", "Cast Iron Grate", "metal", 9, 4.0, "pickaxe", cast_iron_grate),
    ("pipe_wall", "Pipe Wall", "metal", 10, 3.0, "pickaxe", pipe_wall),
    ("gear_panel", "Gear Panel", "metal", 11, 3.5, "pickaxe", gear_panel),
    ("boiler_plate", "Boiler Plate", "metal", 12, 3.5, "pickaxe", boiler_plate),
    ("brass_diamond_plate", "Brass Diamond Plate", "metal", 13, 3.5, "pickaxe", brass_diamond),
    ("verdigris_plate", "Verdigris Plate", "metal", 14, 3.5, "pickaxe", verdigris_plate),
    ("lacquer_brass_panel", "Lacquered Brass-Bound Panel", "mixed", 15, 2.0, "axe", lacquer_brass_corners),
    ("copper_shoji", "Copper Shoji", "mixed", 16, 1.5, "axe", copper_shoji),
    ("gear_inlay_wood", "Gear Inlay Wood", "mixed", 17, 2.0, "axe", gear_inlay_wood),
    ("brass_crest_lacquer", "Brass Crest Lacquer", "mixed", 18, 2.0, "axe", brass_on_lacquer),
    ("boiler_tiles", "Boiler Tiles", "mixed", 19, 3.0, "pickaxe", boiler_tile),
    ("sakura_copper_planks", "Copper-Seamed Sakura Planks", "mixed", 20, 2.0, "axe", sakura_copper_seams),
    ("paper_lantern_wall", "Paper Lantern Wall", "mixed", 21, 1.0, "axe", lantern_wall),
    ("maki_e_lacquer", "Maki-e Lacquer", "mixed", 22, 2.0, "axe", gold_black_lacquer),
    ("shoji_screen", "Shoji Screen", "wood", 23, 1.0, "axe", shoji),
    ("vermilion_wood", "Vermilion Lacquered Wood", "wood", 24, 2.0, "axe", red_lacquer_wood),
    ("tatami", "Tatami", "wood", 25, 0.8, "axe", tatami),
    ("woven_bamboo", "Woven Bamboo", "wood", 26, 1.0, "axe", woven_bamboo),
    ("yakisugi", "Yakisugi (Charred Wood)", "wood", 27, 2.0, "axe", yakisugi),
    ("sakura_planks", "Sakura Planks", "wood", 28, 2.0, "axe", sakura_wood),
    ("rice_straw_wall", "Rice Straw Wall", "wood", 29, 0.8, "axe", straw_wall),
    ("wooden_lattice", "Wooden Lattice", "wood", 30, 1.5, "axe", wooden_lattice),
    ("kawara_roof_tiles", "Kawara Roof Tiles", "stone", 31, 2.0, "pickaxe", kawara_tiles),
    ("granite_pavers", "Granite Pavers", "stone", 32, 2.0, "pickaxe", granite_pavers),
    ("mossy_garden_stone", "Mossy Garden Stone", "stone", 33, 2.0, "pickaxe", mossy_garden_stone),
    ("banded_plaster_wall", "Wood-Banded Plaster Wall", "stone", 34, 1.5, "pickaxe", painted_wall_band),
    ("lantern_stone", "Lantern Stone", "stone", 35, 2.0, "pickaxe", lantern_stone),
    ("raked_gravel_tile", "Raked Gravel Tile", "stone", 36, 1.5, "pickaxe", gravel_tile),
    ("dark_slate", "Dark Slate", "stone", 37, 2.5, "pickaxe", dark_slate),
    ("white_plaster", "White Plaster", "stone", 38, 1.5, "pickaxe", white_plaster),
]
