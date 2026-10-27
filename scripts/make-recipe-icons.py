"""Draws our own small pixel-art icons (16x16) for every item name on the /recipes page, so the recipe
grids show a picture instead of only text. They are NOT Mojang textures: simple shapes and colours that
tell the player what kind of item it is (ingot, ore, dye, flower, block...), with the item name shown
under the picture on the page. Run from the repo root: python -I scripts/make-recipe-icons.py
Output: public/recipes/icons/<id>.png and src/lib/recipe-icons.json (list of ids)."""
import json, os, re, struct, zlib, hashlib

OUT = "public/recipes/icons"
os.makedirs(OUT, exist_ok=True)

COLORS = {
    "iron": (205, 205, 210), "copper": (205, 120, 75), "gold": (250, 205, 60), "golden": (250, 205, 60),
    "brass": (208, 172, 70), "tin": (185, 202, 208), "zinc": (160, 176, 195), "diamond": (90, 225, 220),
    "emerald": (60, 200, 100), "lapis": (45, 75, 195), "redstone": (205, 30, 30), "coal": (45, 45, 50),
    "charcoal": (60, 55, 50), "quartz": (238, 232, 222), "sulfur": (228, 218, 60), "obsidian": (35, 22, 55),
    "red": (190, 50, 45), "blue": (50, 70, 190), "green": (80, 125, 45), "yellow": (240, 210, 50),
    "orange": (240, 130, 30), "pink": (240, 140, 180), "purple": (130, 50, 170), "magenta": (190, 70, 190),
    "cyan": (30, 140, 150), "lime": (130, 205, 40), "light blue": (90, 170, 230), "light gray": (160, 160, 155),
    "gray": (80, 85, 90), "brown": (115, 75, 45), "black": (30, 30, 34), "white": (240, 240, 240),
}
STONE = (125, 125, 128)
DEEP = (70, 72, 78)
WOOD = (170, 130, 80)

# Drinks on the Sakagura tab get a bottle in their own recipe colour (filled from
# recipes-data.json below; keys are lower-case result names).
HEX = {}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def rnd(name, x, y):
    return hashlib.md5(f"{name}{x},{y}".encode()).digest()[0] / 255.0


class Img:
    def __init__(self):
        self.p = [[(0, 0, 0, 0)] * 16 for _ in range(16)]

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[y][x] = c + (255,)

    def rect(self, x1, y1, x2, y2, c):
        for y in range(y1, y2 + 1):
            for x in range(x1, x2 + 1):
                self.px(x, y, c)

    def disc(self, cx, cy, r, c):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.px(x, y, c)

    def outline(self):
        o = [[None] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                if self.p[y][x][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < 16 and 0 <= ny < 16 and self.p[ny][nx][3] and not o[ny][nx]:
                            o[y][x] = (25, 22, 28, 255)
        for y in range(16):
            for x in range(16):
                if o[y][x]:
                    self.p[y][x] = o[y][x]

    def png(self, path):
        raw = b"".join(b"\x00" + bytes(v for q in row for v in q) for row in self.p)
        ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
        open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) + ch(b"IDAT", zlib.compress(raw)) + ch(b"IEND", b""))


FONT = {  # 3x5 letters for the fallback tile
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110", "E": "111100110100111",
    "F": "111100110100100", "G": "011100101101011", "H": "101101111101101", "I": "111010010010111", "J": "001001001101010",
    "K": "101101110101101", "L": "100100100100111", "M": "101111111101101", "N": "110101101101101", "O": "010101101101010",
    "P": "110101110100100", "Q": "010101101111011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101", "Y": "101101010010010",
    "Z": "111001010100111",
}


def color_of(n, default=None):
    low = n.lower()
    best = None
    for k, v in COLORS.items():
        if re.search(r"\b" + k + r"\b", low) and (best is None or len(k) > len(best[0])):
            best = (k, v)
    return best[1] if best else default


def tile(im, name, base, spread=0.18, border=True):
    for y in range(16):
        for x in range(16):
            f = 1 + (rnd(name, x, y) - 0.5) * spread * 2
            im.px(x, y, shade(base, f))
    if border:
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                im.px(x, y, shade(base, 0.7))
        for i in range(1, 15):
            im.px(i, 1, shade(base, 1.15))
            im.px(1, i, shade(base, 1.1))


def bricks(im, name, base):
    tile(im, name, base, 0.08, True)
    for y in (4, 8, 12):
        im.rect(1, y, 14, y, shade(base, 0.6))
    for y, xs in ((1, (5, 11)), (5, (2, 8, 14)), (9, (5, 11)), (13, (2, 8, 14))):
        for x in xs:
            for k in range(3):
                im.px(x, y + k, shade(base, 0.6))


def ingot(im, c):
    for i in range(5):
        im.rect(3 + i // 2, 11 - i, 12 - i // 2 - 1 + 0, 11 - i, shade(c, 1.15 - i * 0.07))
    im.rect(2, 10, 12, 12, shade(c, 0.85))
    im.rect(3, 9, 12, 9, c)
    im.rect(4, 7, 13, 8, shade(c, 1.1))
    im.rect(5, 5, 13, 6, shade(c, 1.25))


def nugget(im, c):
    for (x, y) in ((6, 8), (9, 6), (8, 10)):
        im.disc(x, y, 1.6, c)
        im.px(x, y - 1, shade(c, 1.3))


def plate(im, c):
    im.rect(2, 5, 13, 11, shade(c, 0.9))
    im.rect(2, 5, 13, 6, shade(c, 1.2))
    for x, y in ((3, 6), (12, 6), (3, 10), (12, 10)):
        im.px(x, y, shade(c, 0.6))


def raw(im, name, c):
    im.disc(8, 8, 5, shade(STONE, 0.9))
    for _ in range(0):
        pass
    for y in range(16):
        for x in range(16):
            if im.p[y][x][3] and rnd(name, x, y) > 0.62:
                im.px(x, y, c)


def dust(im, c):
    for y in range(7, 13):
        w = (y - 6) * 2
        for x in range(8 - w // 2, 8 + w // 2 + 1):
            f = 0.85 + 0.3 * rnd("d", x, y)
            im.px(x, y, shade(c, f))


def gem(im, c):
    pts = [(8, 2), (5, 5), (11, 5), (4, 8), (12, 8), (8, 13)]
    for y in range(2, 14):
        for x in range(3, 14):
            if abs(x - 8) <= (y - 2) * 0.9 + 1 and abs(x - 8) <= (13 - y) * 0.9 + 3 and y <= 13:
                im.px(x, y, shade(c, 1.2 if x < 8 and y < 8 else 0.9 if x > 8 else 1.0))
    im.px(6, 5, shade(c, 1.5))


def ball(im, c):
    im.disc(8, 8, 5, c)
    im.disc(6, 6, 2, shade(c, 1.3))
    im.px(10, 11, shade(c, 0.7))


def lump(im, name, c):
    im.disc(8, 9, 5, c)
    for y in range(16):
        for x in range(16):
            if im.p[y][x][3]:
                im.px(x, y, shade(c, 0.8 + 0.4 * rnd(name, x, y)))


def dye(im, c):
    im.disc(8, 9, 4, c)
    im.rect(6, 3, 9, 5, shade(c, 0.7))
    im.px(7, 8, shade(c, 1.4))


def flower(im, name, c):
    im.rect(8, 8, 8, 14, (60, 140, 50))
    im.px(7, 11, (60, 140, 50)); im.px(9, 12, (60, 140, 50))
    for dx, dy in ((0, -2), (2, 0), (-2, 0), (0, 2), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        im.px(8 + dx, 5 + dy, c)
    im.px(8, 5, (250, 220, 70))


def ore(im, name, base, c):
    tile(im, name, base, 0.1)
    for (x, y) in ((4, 4), (5, 4), (4, 5), (10, 5), (11, 5), (11, 6), (6, 10), (7, 10), (7, 11), (11, 11), (12, 10)):
        im.px(x, y, c)
        im.px(x + 1 if x < 14 else x, y, shade(c, 1.2))


def knife(im, c):
    for i in range(8):
        im.px(4 + i, 11 - i, c)
        im.px(5 + i, 11 - i, shade(c, 1.2))
    im.px(3, 12, (110, 75, 40)); im.px(4, 12, (110, 75, 40)); im.px(3, 11, (110, 75, 40))


def bottle(im, c):
    im.rect(7, 2, 8, 4, (150, 95, 50))                       # cork
    im.rect(7, 4, 8, 6, shade(c, 1.25))                      # neck
    im.rect(5, 6, 10, 14, c)                                 # body
    im.rect(5, 6, 10, 6, shade(c, 0.75)); im.rect(5, 14, 10, 14, shade(c, 0.7))
    im.px(6, 8, (255, 255, 255)); im.px(6, 9, shade(c, 1.4))  # glass shine


def bowl(im, content):
    im.rect(3, 8, 12, 9, (150, 120, 85))
    im.rect(4, 10, 11, 12, (130, 100, 70))
    im.rect(4, 7, 11, 7, content)
    im.px(6, 6, shade(content, 1.2)); im.px(9, 6, shade(content, 0.9))


def plate_food(im, name, c):
    im.disc(8, 9, 6, (235, 235, 230))
    im.disc(8, 9, 5, (215, 215, 210))
    im.disc(8, 8, 3, c)
    im.px(7, 7, shade(c, 1.3))


def gear(im, name, c):
    im.disc(8, 8, 5, c)
    for k in range(8):
        import math
        a = k * math.pi / 4
        im.rect(int(8 + 6 * math.cos(a)) - 1, int(8 + 6 * math.sin(a)) - 1, int(8 + 6 * math.cos(a)), int(8 + 6 * math.sin(a)), shade(c, 0.9))
    im.disc(8, 8, 2, shade(c, 0.4))


def letters(im, name, c):
    tile(im, name, shade(c, 0.9), 0.05)
    words = [w for w in re.split(r"[^A-Za-z]+", name) if w and w.lower() not in ("any", "or", "of")]
    ini = "".join(w[0].upper() for w in words[:2]) or "?"
    x0 = 8 - (len(ini) * 4 - 1) // 2
    for i, ch in enumerate(ini):
        bits = FONT.get(ch, "0" * 15)
        for k, b in enumerate(bits):
            if b == "1":
                im.px(x0 + i * 4 + k % 3, 5 + k // 3, (255, 255, 255))


FOODS = ("beef", "pork", "chicken", "cod", "salmon", "tuna", "fish", "rice", "mochi", "tofu", "sashimi", "nigiri", "maki", "sushi",
         "onigiri", "dango", "tempura", "tamagoyaki", "takuan", "gyudon", "oyakodon", "oden", "nabe", "ramen", "noodle", "bread", "cake",
         "cookie", "pie", "cabbage", "onion", "daikon", "kabocha", "edamame", "potato", "beetroot", "dashi", "miso", "soy", "pumpkin",
         "pufferfish", "fugu", "egg", "nori", "slices", "cuts", "leaf", "tea", "straw", "mushroom", "flesh", "bento", "kelp", "wheat",
         "soybeans", "seeds", "sugar", "flour", "leaves", "chopped", "bean")
FLOWERS = ("allium", "azure bluet", "blue orchid", "cornflower", "dandelion", "lilac", "lily of the valley", "orange tulip", "oxeye daisy",
           "peony", "pink petals", "pink tulip", "poppy", "red tulip", "rose bush", "sunflower", "white tulip", "wither rose")


def draw(name):
    im = Img()
    low = name.lower()
    col = color_of(name)
    if low.startswith("any "):
        low2 = low[4:]
    else:
        low2 = low
    if low in HEX:
        bottle(im, HEX[low])
    elif "concrete powder" in low:
        tile(im, name, shade(col, 1.1), 0.22, False)
    elif "concrete" in low:
        tile(im, name, col, 0.04)
    elif low.endswith(" dye") or low == "ink sac":
        dye(im, col or (25, 25, 35))
    elif low.endswith("ingot"):
        ingot(im, col or (200, 200, 200))
    elif low.endswith("nugget"):
        nugget(im, col or (200, 200, 200))
    elif low.endswith("plate") and col:
        plate(im, col)
    elif low == "plate":
        plate(im, (190, 190, 195))
    elif low.startswith("raw ") and col:
        raw(im, name, col)
    elif low.endswith(" ore") or low == "ore":
        c = col or (200, 200, 200)
        if "diamond" in low: c = COLORS["diamond"]
        if "nether gold" in low: c = COLORS["gold"]
        if "quartz" in low: c = COLORS["quartz"]
        ore(im, name, DEEP if "deepslate" in low else (110, 50, 50) if "nether" in low else STONE, c)
    elif "bricks" in low or "tiles" in low:
        base = (60, 60, 66) if "deepslate" in low else (95, 45, 50) if "nether" in low else (40, 38, 45) if "blackstone" in low else (130, 130, 134)
        bricks(im, name, base)
        if "cracked" in low:
            for (x, y) in ((6, 3), (7, 4), (7, 5), (8, 6), (4, 10), (5, 11), (6, 12)):
                im.px(x, y, shade(base, 0.45))
    elif low in ("brick",):
        im.rect(3, 6, 12, 10, (170, 70, 55)); im.rect(3, 6, 12, 7, (200, 100, 80))
    elif low in ("cobblestone", "stone", "andesite", "diorite", "granite", "dirt", "gravel", "sand", "red sand", "sandstone", "red sandstone", "mud",
                 "soul sand", "soul soil", "terracotta", "obsidian", "magma block", "snow block", "ice", "clay", "netherrack", "glowstone", "iron block", "glass"):
        base = {"cobblestone": (115, 115, 115), "stone": STONE, "andesite": (135, 135, 130), "diorite": (190, 190, 190), "granite": (150, 100, 85),
                "dirt": (125, 85, 55), "gravel": (135, 130, 130), "sand": (220, 205, 150), "red sand": (190, 105, 50), "sandstone": (215, 200, 150),
                "red sandstone": (180, 100, 50), "mud": (65, 55, 55), "soul sand": (85, 65, 50), "soul soil": (75, 60, 50), "terracotta": (155, 90, 65),
                "obsidian": (30, 20, 50), "magma block": (170, 60, 20), "snow block": (245, 248, 250), "ice": (150, 190, 240), "clay": (160, 166, 180),
                "netherrack": (110, 50, 50), "glowstone": (240, 200, 110), "iron block": (215, 215, 220), "glass": (200, 230, 235)}[low]
        tile(im, name, base, 0.12 if low != "glass" else 0.03)
        if low == "glass":
            for i in range(2, 14):
                im.px(i, 2, (255, 255, 255)); im.px(2, i, (255, 255, 255))
    elif "planks" in low2 or "slabs" in low2 or "trapdoor" in low2:
        tile(im, name, WOOD, 0.05)
        for y in (4, 8, 12):
            im.rect(1, y, 14, y, shade(WOOD, 0.7))
    elif "log" in low2 or low2 == "stripped":
        tile(im, name, (110, 80, 50), 0.12)
        im.rect(4, 4, 11, 11, (190, 150, 95)); im.rect(6, 6, 9, 9, (160, 120, 75))
    elif "wool" in low2:
        tile(im, name, col or (235, 235, 235), 0.1, False)
    elif low in FLOWERS or low in ("dead bush",):
        flower(im, name, color_of(name, (200, 90, 90)) if low != "dead bush" else (130, 95, 55))
    elif low.endswith("knife"):
        knife(im, col or (190, 190, 195) if "flint" not in low else (70, 70, 75))
        if "diamond" in low: knife(im, COLORS["diamond"])
    elif low in ("diamond", "emerald", "quartz", "lapis lazuli", "amethyst shard"):
        gem(im, COLORS.get(low, COLORS["lapis"]) if low != "lapis lazuli" else COLORS["lapis"])
    elif low in ("redstone", "glowstone dust", "gunpowder", "bone meal", "blaze powder", "sugar", "flour", "sulfur"):
        dust(im, {"redstone": COLORS["redstone"], "glowstone dust": (240, 200, 90), "gunpowder": (95, 95, 100), "bone meal": (240, 240, 235),
                  "blaze powder": (240, 160, 30), "sugar": (250, 250, 250), "flour": (240, 235, 215), "sulfur": COLORS["sulfur"]}[low])
    elif low in ("coal", "charcoal", "flint"):
        lump(im, name, {"coal": (45, 45, 50), "charcoal": (65, 55, 50), "flint": (70, 70, 78)}[low])
    elif low in ("ender pearl", "ender eye", "slime ball", "clay ball", "magma cream", "egg", "spider eye", "fermented spider eye"):
        ball(im, {"ender pearl": (30, 110, 100), "ender eye": (40, 130, 90), "slime ball": (110, 200, 90), "clay ball": (160, 166, 180),
                  "magma cream": (200, 110, 40), "egg": (230, 215, 190), "spider eye": (130, 40, 50), "fermented spider eye": (110, 70, 50)}[low])
    elif low in ("stick", "bamboo", "blaze rod", "bone", "sugar cane"):
        c = {"stick": (130, 95, 55), "bamboo": (110, 160, 60), "blaze rod": (240, 170, 40), "bone": (235, 232, 215), "sugar cane": (140, 200, 100)}[low]
        for i in range(10):
            im.rect(3 + i, 12 - i, 4 + i, 12 - i, c)
    elif low in ("string",):
        for i in range(10):
            im.px(3 + i, 12 - i, (240, 240, 240))
    elif low in ("paper", "book", "leather", "punch card"):
        c = {"paper": (240, 238, 225), "book": (130, 70, 45), "leather": (150, 85, 45), "punch card": (235, 220, 185)}[low]
        im.rect(3, 3, 12, 13, c); im.rect(3, 3, 12, 4, shade(c, 1.15)); im.rect(3, 12, 12, 13, shade(c, 0.8))
        if low == "punch card":
            for (x, y) in ((5, 6), (8, 6), (6, 9), (10, 9)):
                im.px(x, y, (60, 40, 30))
    elif low in ("bowl",) or "soup" in low or "stew" in low or low in ("ramen", "nabe", "oden", "gyudon", "oyakodon", "dashi", "miso"):
        bowl(im, col or ((200, 130, 60) if "ramen" in low or "gyudon" in low else (170, 120, 70)))
    elif "torch" in low or "lantern" in low or "campfire" in low:
        flame = (90, 220, 230) if "soul" in low else (250, 190, 60)
        im.rect(7, 8, 8, 14, (130, 95, 55)); im.rect(7, 5, 8, 7, flame); im.px(7, 4, shade(flame, 1.2))
        if "lantern" in low:
            im.rect(5, 5, 10, 11, (70, 70, 75)); im.rect(6, 6, 9, 10, flame)
    elif any(w in low for w in FOODS):
        plate_food(im, name, color_of(name, (190 + int(rnd(name, 1, 1) * 40), 120 + int(rnd(name, 2, 2) * 60), 70 + int(rnd(name, 3, 3) * 40))))
    elif any(w in low for w in ("cogwheel", "gear", "sprocket", "shaft", "crank", "wheel", "clutch", "gearshift", "gearbox", "belt", "drill", "saw", "pump")):
        gear(im, name, (205, 150, 75) if "large" not in low else (195, 165, 80))
    else:
        h = hashlib.md5(name.encode()).digest()
        letters(im, name, (80 + h[0] % 100, 80 + h[1] % 100, 80 + h[2] % 100))
        return im
    im.outline()
    return im


def ident(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


data = json.load(open("src/lib/recipes-data.json", encoding="utf-8"))
for p in data["plugins"]:
    for r in p["recipes"]:
        if r.get("hex") and re.fullmatch(r"#[0-9a-fA-F]{6}", r["hex"]):
            HEX[r["result"].lower()] = tuple(int(r["hex"][i:i + 2], 16) for i in (1, 3, 5))
names = set()
for p in data["plugins"]:
    for r in p["recipes"]:
        for row in r.get("grid") or []:
            names.update(n for n in row if n)
        for k in ("ingredients", "outputs"):
            names.update(i["name"] for i in r.get(k, []))
        if r.get("result"):
            names.add(r["result"])
ids = []
for n in sorted(names):
    for part in (n.split(" or ") if " or " in n else [n]):
        draw(part.strip()).png(f"{OUT}/{ident(part.strip())}.png")
        ids.append(ident(part.strip()))
    if " or " in n:
        draw(n.split(" or ")[0].strip()).png(f"{OUT}/{ident(n)}.png")
        ids.append(ident(n))
json.dump(sorted(set(ids)), open("src/lib/recipe-icons.json", "w"))
print(len(set(ids)), "icons")

# which plugin folders hold a rendered model picture (public/recipes/<plugin>/<id>.png)
pics = {}
for d in ("kura", "kitchen", "sakagura", "steamworks", "power"):
    path = f"public/recipes/{d}"
    pics[d] = sorted(f[:-4] for f in os.listdir(path) if f.endswith(".png")) if os.path.isdir(path) else []
json.dump(pics, open("src/lib/recipe-pictures.json", "w"))
