# Zukan steampunk GUI pack: two full-screen backgrounds for the 6-row container (list + recipe view), drawn in pure Python.
# usage: python -I tools/make_pack.py resourcepack      -> resourcepack/Zukan-pack.zip (+ preview PNGs next to it)
# The art is a bitmap font glyph used in the inventory title (see Gui.title):  shifts left 8px,  = list,  = recipe.
import json
import math
import os
import struct
import sys
import zipfile
import zlib

S = 2                      # texture pixels per GUI pixel
W, H = 176, 222            # 6-row chest screen
IW, IH = W * S, H * S


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


C = {
    "bg": hexc("2a211a"), "bg2": hexc("33281f"), "bg3": hexc("221a14"),
    "brass": hexc("b8893d"), "brass_l": hexc("e0b862"), "brass_d": hexc("7d5a26"), "brass_dd": hexc("4f3716"),
    "copper": hexc("a8602f"), "copper_l": hexc("d98a52"), "copper_d": hexc("6b3a1c"),
    "cell": hexc("17120e"), "cell_edge": hexc("0d0a07"), "cell_hi": hexc("5a4630"),
    "steam": hexc("cfd6d6"), "rivet": hexc("d9b872"), "iron": hexc("5b5a58"), "iron_l": hexc("8a8986"), "iron_d": hexc("383735"),
    "glow": hexc("f4c46a"),
}


class Canvas:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * IW for _ in range(IH)]

    def put(self, x, y, c):
        if 0 <= x < IW and 0 <= y < IH:
            self.px[y][x] = c

    def rect(self, x, y, w, h, c):
        x0, y0 = int(round(x * S)), int(round(y * S))
        x1, y1 = int(round((x + w) * S)), int(round((y + h) * S))
        for yy in range(max(0, y0), min(IH, y1)):
            row = self.px[yy]
            for xx in range(max(0, x0), min(IW, x1)):
                row[xx] = c

    def disc(self, cx, cy, r, c):
        cx, cy, r = cx * S, cy * S, r * S
        for yy in range(int(cy - r) - 1, int(cy + r) + 2):
            for xx in range(int(cx - r) - 1, int(cx + r) + 2):
                if (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r:
                    self.put(xx, yy, c)

    def ring(self, cx, cy, r1, r2, c):
        cx, cy, r1, r2 = cx * S, cy * S, r1 * S, r2 * S
        for yy in range(int(cy - r2) - 1, int(cy + r2) + 2):
            for xx in range(int(cx - r2) - 1, int(cx + r2) + 2):
                d = (xx - cx) ** 2 + (yy - cy) ** 2
                if r1 * r1 <= d <= r2 * r2:
                    self.put(xx, yy, c)

    def gear(self, cx, cy, r, teeth, rot, body, hub, hole=True):
        """A cog: body disc with square teeth, hub ring and a hole."""
        for i in range(teeth):
            a = rot + i * 2 * math.pi / teeth
            for t in range(-2, 3):
                aa = a + t * 0.07
                for rr in (r * 0.85, r * 0.95, r * 1.12):
                    self.disc(cx + math.cos(aa) * rr, cy + math.sin(aa) * rr, 0.9, body)
        self.disc(cx, cy, r, body)
        self.ring(cx, cy, r * 0.55, r * 0.72, hub)
        if hole:
            self.disc(cx, cy, r * 0.22, C["bg3"])
        for i in range(4):
            a = rot + i * math.pi / 2
            self.disc(cx + math.cos(a) * r * 0.4, cy + math.sin(a) * r * 0.4, 0.7, hub)

    def bevel(self, x, y, w, h, base, light, dark, t=1):
        self.rect(x, y, w, h, base)
        self.rect(x, y, w, t, light)
        self.rect(x, y, t, h, light)
        self.rect(x, y + h - t, w, t, dark)
        self.rect(x + w - t, y, t, h, dark)

    def rivet(self, x, y):
        self.disc(x, y, 1.2, C["brass_d"])
        self.disc(x - 0.2, y - 0.2, 0.8, C["rivet"])

    def cell(self, col, row_y, col_x=8):
        """One 18x18 slot cell with a recessed 16x16 interior at slot position (x, y)."""
        x, y = col, row_y
        self.rect(x - 1, y - 1, 18, 18, C["cell_edge"])
        self.rect(x, y, 16, 16, C["cell"])
        self.rect(x, y + 15, 16, 1, C["cell_hi"])
        self.rect(x + 15, y, 1, 16, C["cell_hi"])

    def png(self):
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for r, g, b, a in row:
                raw += bytes((r, g, b, a))
        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
        return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", IW, IH, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def slot_xy(slot):
    return 8 + (slot % 9) * 18, 18 + (slot // 9) * 18


def inv_cells(c):
    for r in range(3):
        for col in range(9):
            c.cell(8 + col * 18, 140 + r * 18)
    for col in range(9):
        c.cell(8 + col * 18, 198)


def frame(c):
    """Dark plank background, brass frame with rivets, corner gears."""
    c.rect(0, 0, W, H, C["bg"])
    for y in range(0, H, 6):
        c.rect(0, y, W, 1, C["bg2"] if (y // 6) % 2 == 0 else C["bg3"])
    c.bevel(0, 0, W, H, C["brass_d"], C["brass_l"], C["brass_dd"], 2)
    c.bevel(3, 3, W - 6, H - 6, C["bg"], C["brass_dd"], C["brass_l"], 1)
    for x, y in ((6, 6), (W - 7, 6), (6, H - 7), (W - 7, H - 7)):
        c.rivet(x, y)


def title_banner(c):
    c.bevel(8, 4, W - 16, 12, C["brass_d"], C["brass_l"], C["brass_dd"], 1)
    c.rect(10, 6, W - 20, 8, C["bg3"])
    # engraved "ZUKAN" built from rectangles (5x7 letters)
    glyph = {
        "Z": ["XXXXX", "....X", "...X.", "..X..", ".X...", "X....", "XXXXX"],
        "U": ["X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."],
        "K": ["X...X", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "X...X"],
        "A": [".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"],
        "N": ["X...X", "XX..X", "X.X.X", "X..XX", "X...X", "X...X", "X...X"],
    }
    x0 = (W - (5 * 6 - 1)) / 2
    # letters are 5x7 dots; the banner is 8px tall, so the middle dot row is skipped (5x6)
    rows = [0, 1, 2, 3, 5, 6]
    for i, ch in enumerate("ZUKAN"):
        for out_y, ry in enumerate(rows):
            for rx, v in enumerate(glyph[ch][ry]):
                if v == "X":
                    c.rect(x0 + i * 6 + rx, 7 + out_y, 1, 1, C["glow"])
    c.gear(18, 10, 5, 8, 0.2, C["brass"], C["brass_d"])
    c.gear(W - 18, 10, 5, 8, 0.5, C["copper"], C["copper_d"])


def pipes(c, y):
    c.rect(8, y, W - 16, 2, C["iron"])
    c.rect(8, y, W - 16, 1, C["iron_l"])
    c.rect(8, y + 1, W - 16, 1, C["iron_d"])
    for x in range(14, W - 8, 24):
        c.rect(x, y - 1, 3, 4, C["brass"])
        c.rect(x, y - 1, 3, 1, C["brass_l"])


def player_panel(c):
    c.bevel(5, 135, W - 10, 84, C["bg3"], C["brass_dd"], C["brass_l"], 1)
    c.rect(8, 136, 60, 1, C["brass_d"])
    inv_cells(c)
    c.gear(W - 12, 130, 4, 8, 0.1, C["iron_l"], C["iron"])


def list_art():
    c = Canvas()
    frame(c)
    title_banner(c)
    c.bevel(5, 16, W - 10, 112, C["bg3"], C["brass_dd"], C["brass_l"], 1)
    for slot in range(45):
        x, y = slot_xy(slot)
        c.cell(x, y)
    # button bar
    c.bevel(5, 126, W - 10, 1, C["brass_d"], C["brass_l"], C["brass_dd"], 0)
    for slot in range(45, 54):
        x, y = slot_xy(slot)
        c.rect(x - 2, y - 2, 20, 20, C["brass_d"])
        c.rect(x - 1, y - 1, 18, 18, C["brass_dd"])
        c.rect(x, y, 16, 16, C["cell"])
        c.rect(x, y, 16, 1, C["brass_dd"])
        c.rect(x + 15, y, 1, 16, C["cell_hi"])
    pipes(c, 131)
    player_panel(c)
    return c


def recipe_art():
    c = Canvas()
    frame(c)
    # tab row
    c.bevel(5, 4, W - 10, 22, C["bg3"], C["brass_dd"], C["brass_l"], 1)
    for slot in range(9):
        x, y = slot_xy(slot)
        c.rect(x - 2, y - 2, 20, 20, C["brass_d"])
        c.rect(x - 1, y - 1, 18, 18, C["brass_dd"])
        c.rect(x, y, 16, 16, C["cell"])
        c.rect(x, y + 15, 16, 1, C["cell_hi"])
    # workbench area
    c.bevel(5, 28, W - 10, 98, C["bg3"], C["brass_dd"], C["brass_l"], 1)
    for slot in (10, 11, 12, 13, 19, 20, 21, 22, 28, 29, 30, 31):
        x, y = slot_xy(slot)
        c.cell(x, y)
    # arrow slot (23): an engraved piston arrow with a gear
    x, y = slot_xy(23)
    c.cell(x, y)
    c.ring(x + 8, y + 8, 10, 11, C["brass_d"])
    for slot in (24, 25, 26, 33, 34, 35):
        x, y = slot_xy(slot)
        c.cell(x, y)
        if slot == 25:
            c.ring(x + 8, y + 8, 10.5, 11.5, C["brass_l"])
    # station (37) and info (39)
    for slot in (37, 39):
        x, y = slot_xy(slot)
        c.rect(x - 2, y - 2, 20, 20, C["copper_d"])
        c.rect(x - 1, y - 1, 18, 18, C["copper"])
        c.rect(x, y, 16, 16, C["cell"])
    # decorative: boiler lines + gears between rows 4
    c.gear(112, 99, 8, 12, 0.0, C["copper"], C["copper_d"])
    c.gear(130, 103, 5.5, 8, 0.2, C["brass"], C["brass_d"])
    c.gear(144, 98, 4, 8, 0.4, C["iron_l"], C["iron"])
    c.gear(158, 102, 6, 10, 0.1, C["brass"], C["brass_d"])
    # bottom button bar
    for slot in range(45, 54):
        x, y = slot_xy(slot)
        c.rect(x - 2, y - 2, 20, 20, C["brass_d"])
        c.rect(x - 1, y - 1, 18, 18, C["brass_dd"])
        c.rect(x, y, 16, 16, C["cell"])
        c.rect(x, y, 16, 1, C["brass_dd"])
        c.rect(x + 15, y, 1, 16, C["cell_hi"])
    pipes(c, 131)
    player_panel(c)
    return c


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    lst, rec = list_art(), recipe_art()
    for name, art in (("list", lst), ("recipe", rec)):
        with open(os.path.join(out, "preview_" + name + ".png"), "wb") as f:
            f.write(art.png())
    font = {"providers": [
        {"type": "bitmap", "file": "zukan:font/list.png", "ascent": 13, "height": H, "chars": [""]},
        {"type": "bitmap", "file": "zukan:font/recipe.png", "ascent": 13, "height": H, "chars": [""]},
        {"type": "space", "advances": {"": -8, "": -177}},
    ]}
    meta = {"pack": {"description": "Zukan steampunk GUI", "pack_format": 64, "supported_formats": {"min_inclusive": 1, "max_inclusive": 999},
                     "min_format": 1, "max_format": 999}}
    zpath = os.path.join(out, "Zukan-pack.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps(meta))
        z.writestr("assets/zukan/font/gui.json", json.dumps(font))
        z.writestr("assets/zukan/textures/font/list.png", lst.png())
        z.writestr("assets/zukan/textures/font/recipe.png", rec.png())
    print("wrote", zpath)


if __name__ == "__main__":
    main()
