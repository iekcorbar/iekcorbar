"""Steampunk / sakura NPC skins (64x64 classic player skins, own pixel art, pure Python, no dependencies).

Every skin uses both texture layers: the base layer is the body, the overlay layer (hat, coat, apron, beard, backpack ...)
sticks out of the model and gives the "3D" depth.

Usage (run in NpcSkins/):  python -I tools/make_skins.py           -> skins/*.png, preview_sheet.png, npc_skins.csv, apply_commands.txt
Citizens: /npc select <id>, then /npc skin --url <public url of the png>   (classic arms; mark them 'default' not 'slim')
"""
import csv
import os
import random
import struct
import sys
import zlib

W = H = 64
PARTS = {  # w, h, d, base uv, overlay uv (classic 64x64 layout)
    "head": (8, 8, 8, (0, 0), (32, 0)),
    "body": (8, 12, 4, (16, 16), (16, 32)),
    "rarm": (4, 12, 4, (40, 16), (40, 32)),
    "larm": (4, 12, 4, (32, 48), (48, 48)),
    "rleg": (4, 12, 4, (0, 16), (0, 32)),
    "lleg": (4, 12, 4, (16, 48), (0, 48)),
}
SIDES = ("front", "back", "left", "right")
ALL = SIDES + ("top", "bottom")


def col(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def faces(part, layer):
    w, h, d, b, o = PARTS[part]
    u, v = o if layer else b
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


class Skin:
    def __init__(self, seed):
        self.px = [[(0, 0, 0, 0)] * W for _ in range(H)]
        self.rng = random.Random(seed)

    def put(self, x, y, c, noise=0):
        if noise:
            n = self.rng.randint(-noise, noise)
            c = (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255)
        self.px[y][x] = c

    def rect(self, part, face, x0, y0, x1, y1, c, layer=0, noise=0):
        fx, fy, fw, fh = faces(part, layer)[face]
        for y in range(max(y0, 0), min(y1, fh)):
            for x in range(max(x0, 0), min(x1, fw)):
                self.put(fx + x, fy + y, c, noise)

    def dot(self, part, face, x, y, c, layer=0):
        self.rect(part, face, x, y, x + 1, y + 1, c, layer)

    def sides(self, part, y0, y1, c, layer=0, noise=0, which=SIDES):
        for f in which:
            self.rect(part, f, 0, y0, faces(part, layer)[f][2], y1, c, layer, noise)

    def whole(self, part, c, layer=0, noise=0, which=ALL):
        for f in which:
            fw, fh = faces(part, layer)[f][2:]
            self.rect(part, f, 0, 0, fw, fh, c, layer, noise)


# ------------------------------------------------------------------ palette
BRASS, COPPER, IRON, STEEL = col("c19a3e"), col("b0643a"), col("767c85"), col("aab2bb")
LEATHER, LEATHER_D = col("6b4423"), col("3e2a18")
WHITE, BLACK, GLASS, GLOW = col("efe9dc"), col("1d1d22"), col("7fd0e8"), col("ffb347")
TONES = {"fair": col("ecc3a1"), "light": col("e0ad88"), "tan": col("c68c5e"), "brown": col("9a6540"), "dark": col("6e4429"),
         "pale": col("f1d5c0"), "ash": col("c9b3a3")}


def build(sp, seed):
    s = Skin(seed)
    sk = TONES[sp.get("skin", "fair")]
    hair = col(sp.get("hair", "3a2a1c")) if sp.get("hair") != "none" else None
    shirt = col(sp.get("shirt", "7a6a58"))
    pants = col(sp.get("pants", "40342a"))
    boots = col(sp.get("boots", "3e2a18"))
    sleeve = sp.get("sleeve", "long")
    scol = col(sp.get("sleeve_col")) if sp.get("sleeve_col") else shirt
    glove = col(sp["gloves"]) if sp.get("gloves") else None

    # ---- base layer
    s.whole("head", sk, noise=3)
    s.whole("body", shirt, noise=4)
    s.sides("body", 11, 12, shade(shirt, 0.8))
    for p in ("rarm", "larm"):
        s.whole(p, sk, noise=3)
        rows = {"long": 11, "short": 4, "bare": 0}[sleeve]
        if rows:
            s.sides(p, 0, rows, scol, noise=4)
        s.rect(p, "top", 0, 0, 4, 4, scol, noise=3)
        if glove:
            s.sides(p, 9, 12, glove, noise=3)
            s.rect(p, "bottom", 0, 0, 4, 4, glove)
    for p in ("rleg", "lleg"):
        s.whole(p, pants, noise=4)
        s.sides(p, 9, 12, boots, noise=3)
        s.rect(p, "bottom", 0, 0, 4, 4, shade(boots, 0.7))
        s.rect(p, "front", 0, 11, 4, 12, shade(boots, 0.75))

    # ---- face
    eye = col(sp.get("eye", "2b2118"))
    for x0 in (1, 5):
        s.dot("head", "front", x0, 4, WHITE)
    s.dot("head", "front", 2, 4, eye)
    s.dot("head", "front", 5, 4, eye)
    s.rect("head", "front", 3, 6, 5, 7, shade(sk, 0.7))
    s.rect("head", "front", 3, 5, 5, 6, shade(sk, 0.93))
    if hair:
        s.rect("head", "front", 1, 3, 3, 4, shade(hair, 0.9))
        s.rect("head", "front", 5, 3, 7, 4, shade(hair, 0.9))
    if sp.get("scar"):
        for y in range(2, 6):
            s.dot("head", "front", 5, y, col("a85a52"))
    if sp.get("blush"):
        s.dot("head", "front", 1, 5, col("e49a94"))
        s.dot("head", "front", 6, 5, col("e49a94"))

    # ---- hair
    style = sp.get("style", "short")
    if hair and style != "bald":
        s.rect("head", "top", 0, 0, 8, 8, hair, noise=5)
        s.rect("head", "front", 0, 0, 8, 2, hair, noise=5)
        s.rect("head", "right", 0, 0, 5, 4, hair, noise=5)
        s.rect("head", "left", 3, 0, 8, 4, hair, noise=5)
        s.rect("head", "back", 0, 0, 8, 6, hair, noise=5)
        s.rect("head", "top", 0, 0, 8, 8, hair, layer=1, noise=6)
        s.rect("head", "back", 0, 0, 8, 3, hair, layer=1, noise=6)
        s.rect("head", "right", 3, 0, 8, 2, hair, layer=1, noise=6)
        s.rect("head", "left", 0, 0, 5, 2, hair, layer=1, noise=6)
        if style == "long":
            s.rect("head", "back", 0, 0, 8, 8, hair, noise=5)
            s.rect("head", "right", 0, 0, 5, 8, hair, noise=5)
            s.rect("head", "left", 3, 0, 8, 8, hair, noise=5)
            s.rect("body", "back", 1, 0, 7, 6, hair, layer=1, noise=6)
            s.rect("head", "back", 0, 3, 8, 8, hair, layer=1, noise=6)
        elif style == "bun":
            s.rect("head", "top", 3, 3, 5, 5, shade(hair, 1.25), layer=1)
            s.rect("head", "back", 3, 0, 5, 3, shade(hair, 1.25), layer=1)
        elif style == "ponytail":
            s.rect("head", "back", 3, 3, 5, 8, hair, layer=1, noise=6)
            s.rect("body", "back", 3, 0, 5, 4, hair, layer=1, noise=6)
        elif style in ("wild", "spiky"):
            s.rect("head", "right", 0, 0, 8, 5, hair, layer=1, noise=8)
            s.rect("head", "left", 0, 0, 8, 5, hair, layer=1, noise=8)
            s.rect("head", "back", 0, 0, 8, 6, hair, layer=1, noise=8)
            s.rect("head", "front", 0, 0, 8, 2, hair, layer=1, noise=8)
        if style == "friar":
            s.rect("head", "top", 2, 2, 6, 6, sk, noise=2)
            s.rect("head", "top", 2, 2, 6, 6, sk, layer=1)
    elif sp.get("hair") != "none":
        pass

    # ---- beard
    beard = sp.get("beard")
    bc = col(sp.get("beard_col") or sp.get("hair") or "3a2a1c")
    if beard in ("short", "long", "dwarf"):
        n = {"short": 8, "long": 8, "dwarf": 8}[beard]
        s.rect("head", "front", 0, 5, 8, n, bc, layer=1, noise=6)
        s.rect("head", "front", 3, 5, 5, 6, shade(sk, 0.8), layer=1)
        s.rect("head", "right", 3, 4, 8, 8, bc, layer=1, noise=6)
        s.rect("head", "left", 0, 4, 5, 8, bc, layer=1, noise=6)
        if beard in ("long", "dwarf"):
            s.rect("body", "front", 1, 0, 7, 5 if beard == "long" else 7, bc, layer=1, noise=6)
            s.rect("body", "front", 2, 5 if beard == "long" else 7, 6, 7 if beard == "long" else 9, bc, layer=1, noise=6)
    elif beard == "stache":
        s.rect("head", "front", 1, 5, 7, 6, bc, layer=1, noise=5)
    elif beard == "mutton":
        s.rect("head", "front", 0, 3, 2, 7, bc, layer=1, noise=5)
        s.rect("head", "front", 6, 3, 8, 7, bc, layer=1, noise=5)
        s.rect("head", "front", 1, 5, 7, 6, bc, layer=1, noise=5)

    # ---- clothes (overlay layer)
    cloth = sp.get("outfit")
    oc = col(sp.get("outer", "5a4a3a"))
    trim = col(sp.get("trim", "c19a3e"))
    if cloth in ("coat", "robe", "labcoat", "kimono", "happi", "armor"):
        full = cloth in ("robe", "kimono")
        s.sides("body", 0, 12, oc, layer=1, noise=4)
        s.rect("body", "top", 0, 0, 8, 4, oc, layer=1)
        for p in ("rarm", "larm"):
            rows = 10 if cloth != "happi" else 5
            s.sides(p, 0, rows, oc, layer=1, noise=4)
            s.rect(p, "top", 0, 0, 4, 4, oc, layer=1)
            if cloth in ("coat", "labcoat", "robe", "kimono"):
                s.sides(p, rows - 1, rows, trim, layer=1)
        legrows = {"robe": 10, "kimono": 10, "coat": 6, "labcoat": 6, "happi": 3, "armor": 5}[cloth]
        for p in ("rleg", "lleg"):
            s.sides(p, 0, legrows, oc, layer=1, noise=4)
            if legrows >= 6:
                s.sides(p, legrows - 1, legrows, trim, layer=1)
        if cloth in ("coat", "labcoat", "robe"):
            s.rect("body", "front", 3, 0, 5, 12, shade(oc, 0.62), layer=1)           # opening
            for y in (2, 5, 8):
                s.dot("body", "front", 2, y, trim, layer=1)
            s.rect("body", "front", 0, 0, 2, 3, shade(oc, 1.15), layer=1)
            s.rect("body", "front", 6, 0, 8, 3, shade(oc, 1.15), layer=1)
        if cloth == "kimono":
            s.dot("body", "front", 3, 0, shade(oc, 0.6), layer=1)
            for i in range(6):
                s.dot("body", "front", 3 + (i // 3), i, shade(oc, 0.6), layer=1)
            s.sides("body", 7, 10, col(sp.get("obi", "9a2f3a")), layer=1, noise=3)
            s.rect("body", "back", 2, 7, 6, 11, shade(col(sp.get("obi", "9a2f3a")), 1.2), layer=1)
        if cloth == "happi":
            s.rect("body", "front", 3, 0, 5, 12, shade(oc, 0.7), layer=1)
            s.sides("body", 11, 12, WHITE, layer=1)
            s.rect("body", "back", 2, 3, 6, 7, WHITE, layer=1)
        if cloth == "labcoat":
            s.rect("body", "front", 5, 7, 7, 9, shade(oc, 0.8), layer=1)             # pocket
            s.dot("body", "front", 6, 6, col("b04a3a"), layer=1)                     # pen
        if cloth == "armor":
            s.rect("body", "front", 0, 0, 8, 10, STEEL, layer=1, noise=5)
            s.rect("body", "front", 3, 0, 5, 10, shade(STEEL, 0.78), layer=1)
            for y in (3, 6, 9):
                s.rect("body", "front", 0, y, 8, y + 1, shade(STEEL, 0.7), layer=1)
            for x, y in ((1, 1), (6, 1), (1, 8), (6, 8)):
                s.dot("body", "front", x, y, BRASS, layer=1)
            s.sides("rleg", 0, 5, STEEL, layer=1, noise=4)
            s.sides("lleg", 0, 5, STEEL, layer=1, noise=4)
            for p in ("rarm", "larm"):
                s.sides(p, 0, 3, STEEL, layer=1, noise=4)
                s.rect(p, "top", 0, 0, 4, 4, STEEL, layer=1)
                s.sides(p, 7, 10, shade(STEEL, 0.8), layer=1, noise=3)
    elif cloth == "vest":
        s.rect("body", "front", 0, 0, 3, 10, oc, layer=1, noise=4)
        s.rect("body", "front", 5, 0, 8, 10, oc, layer=1, noise=4)
        s.rect("body", "back", 0, 0, 8, 11, oc, layer=1, noise=4)
        s.rect("body", "left", 0, 0, 4, 10, oc, layer=1)
        s.rect("body", "right", 0, 0, 4, 10, oc, layer=1)
        for y in (2, 5, 8):
            s.dot("body", "front", 2, y, trim, layer=1)
    elif cloth == "jumpsuit":
        for p in ("rarm", "larm"):
            s.sides(p, 0, 10, oc, layer=1, noise=3)
        for p in ("rleg", "lleg"):
            s.sides(p, 0, 9, oc, layer=1, noise=3)
        s.sides("body", 0, 12, oc, layer=1, noise=3)
        s.rect("body", "front", 2, 2, 6, 6, shade(oc, 0.7), layer=1)                 # chest panel
        s.rect("body", "front", 3, 3, 5, 5, GLOW, layer=1)
        s.rect("body", "front", 3, 0, 5, 12, shade(oc, 0.8), layer=1)
        s.dot("body", "front", 3, 3, col("ffe9a0"), layer=1)

    if sp.get("apron"):
        ap = col(sp["apron"])
        s.rect("body", "front", 1, 3, 7, 12, ap, layer=1, noise=4)
        s.rect("body", "front", 1, 0, 2, 3, shade(ap, 0.8), layer=1)
        s.rect("body", "front", 6, 0, 7, 3, shade(ap, 0.8), layer=1)
        s.rect("body", "back", 1, 0, 2, 3, shade(ap, 0.8), layer=1)
        s.rect("body", "back", 6, 0, 7, 3, shade(ap, 0.8), layer=1)
        s.rect("body", "back", 3, 5, 5, 8, shade(ap, 0.7), layer=1)
        s.rect("body", "front", 2, 8, 6, 10, shade(ap, 0.82), layer=1)               # pocket
    if sp.get("belt"):
        bl = col(sp["belt"])
        s.sides("body", 9, 11, bl, layer=1, noise=3)
        s.rect("body", "front", 3, 9, 5, 11, BRASS, layer=1)
        if sp.get("pouches"):
            s.rect("body", "front", 0, 9, 2, 12, shade(bl, 1.2), layer=1)
            s.rect("body", "front", 6, 9, 8, 12, shade(bl, 1.2), layer=1)
    if sp.get("scarf"):
        sc = col(sp["scarf"])
        s.sides("body", 0, 2, sc, layer=1, noise=3)
        s.rect("body", "front", 5, 2, 7, 8, sc, layer=1, noise=3)
        s.rect("body", "front", 5, 7, 7, 8, shade(sc, 0.8), layer=1)
    if sp.get("shoulder"):
        pc = col(sp["shoulder"])
        for p in ("rarm", "larm"):
            s.sides(p, 0, 2, pc, layer=1, noise=3)
            s.rect(p, "top", 0, 0, 4, 4, pc, layer=1, noise=3)
            s.dot(p, "front", 1, 0, BRASS, layer=1)
            s.dot(p, "front", 2, 1, BRASS, layer=1)
    if sp.get("backpack"):
        bp = col(sp["backpack"])
        s.rect("body", "back", 0, 0, 8, 11, bp, layer=1, noise=4)
        s.rect("body", "back", 1, 7, 7, 10, shade(bp, 0.8), layer=1)
        s.rect("body", "back", 3, 7, 5, 9, BRASS, layer=1)
        s.rect("body", "right", 0, 0, 4, 10, shade(bp, 0.85), layer=1)
        s.rect("body", "left", 0, 0, 4, 10, shade(bp, 0.85), layer=1)
        s.rect("body", "front", 1, 0, 2, 8, shade(bp, 0.7), layer=1)
        s.rect("body", "front", 6, 0, 7, 8, shade(bp, 0.7), layer=1)
        s.rect("body", "top", 0, 0, 8, 4, shade(bp, 1.1), layer=1)
        if sp.get("pipes"):
            s.rect("body", "back", 1, 0, 2, 3, COPPER, layer=1)
            s.rect("body", "back", 6, 0, 7, 3, COPPER, layer=1)
    if sp.get("gear"):
        gx, gy = 5, 4
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            s.dot("body", "front", gx + dx, gy + dy, BRASS, layer=1)
        s.dot("body", "front", gx, gy, shade(BRASS, 0.55), layer=1)
    if sp.get("rune"):
        rc = col(sp["rune"])
        for x, y in ((1, 5), (3, 8), (6, 6), (4, 10)):
            s.dot("body", "front", x, y, rc, layer=1)
        s.dot("head", "front", 3, 1, rc, layer=1)
        s.dot("head", "front", 4, 1, rc, layer=1)
    if sp.get("armband"):
        s.sides("rarm", 4, 6, col(sp["armband"]), layer=1)
    if sp.get("cuffs"):
        cc = col(sp["cuffs"])
        for p in ("rarm", "larm"):
            s.sides(p, 8, 10, cc, layer=1, noise=3)

    # ---- hats / headgear
    hat = sp.get("hat")
    hc = col(sp.get("hat_col", "6b4423"))
    h2 = col(sp.get("hat2", "c19a3e"))
    L = 1
    if hat == "toque":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L)
        s.sides("head", 0, 3, hc, layer=L, noise=3)
        s.sides("head", 3, 4, shade(hc, 0.85), layer=L)
    elif hat == "straw":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=6)
        s.sides("head", 0, 3, hc, layer=L, noise=6)
        s.sides("head", 2, 3, h2, layer=L)
    elif hat in ("hardhat", "helmet", "pith", "cap"):
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=3)
        rows = 2 if hat == "cap" else 3
        s.sides("head", 0, rows, hc, layer=L, noise=3)
        if hat != "cap":
            s.sides("head", rows - 1, rows, h2, layer=L)
        if hat == "hardhat":
            s.rect("head", "top", 3, 0, 5, 8, shade(hc, 0.85), layer=L)
        if hat == "helmet":
            s.rect("head", "front", 3, 0, 5, 2, GLOW, layer=L)
            s.rect("head", "front", 3, 1, 5, 2, col("fff0b0"), layer=L)
        if hat == "cap":
            s.rect("head", "front", 0, 2, 8, 3, shade(hc, 0.7), layer=L)
    elif hat == "hood":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=4)
        s.rect("head", "back", 0, 0, 8, 8, hc, layer=L, noise=4)
        s.rect("head", "right", 0, 0, 8, 8, hc, layer=L, noise=4)
        s.rect("head", "left", 0, 0, 8, 8, hc, layer=L, noise=4)
        s.rect("head", "front", 0, 0, 8, 2, hc, layer=L, noise=4)
        s.rect("head", "front", 0, 2, 1, 8, hc, layer=L, noise=4)
        s.rect("head", "front", 7, 2, 8, 8, hc, layer=L, noise=4)
        s.rect("head", "front", 0, 0, 8, 1, shade(hc, 0.7), layer=L)
    elif hat == "tophat":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=2)
        s.sides("head", 0, 2, hc, layer=L, noise=2)
        s.sides("head", 1, 2, h2, layer=L)
        s.dot("head", "front", 3, 1, shade(h2, 1.3), layer=L)
    elif hat == "captain":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=2)
        s.sides("head", 0, 2, hc, layer=L, noise=2)
        s.rect("head", "front", 0, 1, 8, 2, h2, layer=L)
        s.rect("head", "front", 3, 0, 5, 1, h2, layer=L)
    elif hat == "kabuto":
        s.rect("head", "top", 0, 0, 8, 8, IRON, layer=L, noise=4)
        s.sides("head", 0, 3, IRON, layer=L, noise=4)
        s.rect("head", "back", 0, 3, 8, 8, shade(IRON, 0.8), layer=L, noise=4)
        s.rect("head", "right", 0, 3, 8, 6, shade(IRON, 0.8), layer=L)
        s.rect("head", "left", 0, 3, 8, 6, shade(IRON, 0.8), layer=L)
        s.rect("head", "front", 3, 0, 5, 3, BRASS, layer=L)
        s.rect("head", "front", 0, 2, 8, 3, shade(IRON, 0.65), layer=L)
    elif hat == "ninja":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=3)
        for f in ("back", "right", "left"):
            s.rect("head", f, 0, 0, 8, 8, hc, layer=L, noise=3)
        s.rect("head", "front", 0, 0, 8, 3, hc, layer=L, noise=3)
        s.rect("head", "front", 0, 5, 8, 8, hc, layer=L, noise=3)
        s.rect("head", "front", 0, 3, 1, 5, hc, layer=L)
        s.rect("head", "front", 7, 3, 8, 5, hc, layer=L)
        s.rect("head", "front", 0, 0, 8, 1, h2, layer=L)
    elif hat == "hachimaki":
        s.sides("head", 1, 3, hc, layer=L, noise=2)
        s.dot("head", "front", 3, 1, col("c0392b"), layer=L)
        s.dot("head", "front", 4, 1, col("c0392b"), layer=L)
        s.rect("head", "back", 5, 2, 8, 6, hc, layer=L)
    elif hat == "bandana":
        s.sides("head", 1, 3, hc, layer=L, noise=3)
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=3)
        s.rect("head", "back", 4, 3, 7, 7, hc, layer=L, noise=3)
    elif hat == "witch":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=2)
        s.sides("head", 0, 3, hc, layer=L, noise=2)
        s.rect("head", "front", 0, 2, 8, 3, h2, layer=L)
    elif hat == "crown":
        s.rect("head", "top", 0, 0, 8, 8, hc, layer=L, noise=2)
        s.sides("head", 0, 2, hc, layer=L, noise=2)
        s.sides("head", 0, 1, h2, layer=L)
        s.dot("head", "front", 2, 0, col("d04040"), layer=L)
        s.dot("head", "front", 5, 0, col("d04040"), layer=L)
    elif hat == "flower":
        s.rect("head", "top", 0, 0, 8, 1, col("e089a4"), layer=L)
        s.dot("head", "right", 6, 1, col("f7d6e0"), layer=L)
        s.dot("head", "right", 5, 2, col("e089a4"), layer=L)
        s.dot("head", "right", 7, 2, col("e089a4"), layer=L)
    elif hat == "ribbon":
        s.rect("head", "top", 0, 0, 8, 1, hc, layer=L)
        s.rect("head", "front", 0, 1, 8, 2, hc, layer=L)
        s.dot("head", "right", 7, 2, h2, layer=L)

    # ---- eyewear
    gg = sp.get("goggles")
    if gg:
        lens = col(sp.get("lens", "7fd0e8"))
        frame = BRASS if gg != "iron" else IRON
        y = 3 if gg in ("down", "iron") else 1
        if gg in ("down", "iron"):
            s.sides("head", y, y + 2, LEATHER_D, layer=L, which=("left", "right", "back"))
            s.rect("head", "front", 0, y, 8, y + 2, LEATHER_D, layer=L)
        for x in (1, 5):
            s.rect("head", "front", x, y, x + 2, y + 2, frame, layer=L)
            s.dot("head", "front", x, y, lens, layer=L)
            s.dot("head", "front", x + 1, y + 1, shade(lens, 0.8), layer=L)
        s.rect("head", "front", 3, y, 5, y + 1, frame, layer=L)
    if sp.get("monocle"):
        s.rect("head", "front", 5, 3, 7, 5, BRASS, layer=L)
        s.dot("head", "front", 5, 3, GLASS, layer=L)
        s.dot("head", "front", 6, 4, shade(GLASS, 0.8), layer=L)
    if sp.get("glasses"):
        for x in (1, 5):
            s.rect("head", "front", x, 3, x + 2, 5, shade(BRASS, 0.8), layer=L)
            s.dot("head", "front", x, 3, col("c8e6f0"), layer=L)
            s.dot("head", "front", x + 1, 4, col("c8e6f0"), layer=L)
        s.rect("head", "front", 3, 3, 5, 4, shade(BRASS, 0.8), layer=L)
    if sp.get("eyepatch"):
        s.rect("head", "front", 1, 3, 3, 5, BLACK, layer=L)
        s.sides("head", 2, 3, BLACK, layer=L, which=("left", "right", "back"))
        s.rect("head", "front", 0, 2, 4, 3, BLACK, layer=L)
    if sp.get("mask"):
        mc = col(sp["mask"])
        s.rect("head", "front", 0, 5, 8, 8, mc, layer=L, noise=2)
        s.sides("head", 5, 8, mc, layer=L, which=("left", "right", "back"))
    return s


# ------------------------------------------------------------------ PNG + preview
def write_png(path, rows):
    h, w = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in rows)

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def view(s, back=False):
    """16x32 front or back picture, overlay on top of the base layer."""
    img = [[(0, 0, 0, 0)] * 16 for _ in range(32)]

    def blit(part, face, ox, oy):
        for layer in (0, 1):
            fx, fy, fw, fh = faces(part, layer)[face]
            for y in range(fh):
                for x in range(fw):
                    p = s.px[fy + y][fx + x]
                    if p[3]:
                        img[oy + y][ox + x] = p
    f = "back" if back else "front"
    blit("head", f, 4, 0)
    blit("body", f, 4, 8)
    if not back:
        blit("rarm", f, 0, 8); blit("larm", f, 12, 8); blit("rleg", f, 4, 20); blit("lleg", f, 8, 20)
    else:
        blit("larm", f, 0, 8); blit("rarm", f, 12, 8); blit("lleg", f, 4, 20); blit("rleg", f, 8, 20)
    return img


def sheet(skins, path, cols=8, scale=5):
    cw, ch = (16 * 2 + 4) * scale, 34 * scale
    rows_n = (len(skins) + cols - 1) // cols
    bg = (214, 222, 230, 255)
    out = [[bg] * (cw * cols) for _ in range(ch * rows_n)]
    for i, s in enumerate(skins):
        cx, cy = (i % cols) * cw + scale, (i // cols) * ch + scale
        for k, back in enumerate((False, True)):
            v = view(s, back)
            for y in range(32):
                for x in range(16):
                    p = v[y][x]
                    if p[3]:
                        for dy in range(scale):
                            for dx in range(scale):
                                out[cy + y * scale + dy][cx + (x + k * 18) * scale + dx] = p
    write_png(path, out)


# ------------------------------------------------------------------ roster
def N(cid, name, role, slug, **kw):
    return dict(id=cid, name=name, role=role, slug=slug, **kw)


ROSTER = [
    # ---- Jobs ranks (BetonQuest JobsRank1-5)
    N(49, "Shichō Riku", "Registrar (job office)", "shicho_riku", skin="fair", hair="2a2a30", style="short", shirt="e9e2d2", pants="2f3a4e", outfit="coat", outer="2f3a4e", trim="c19a3e", glasses=1, hat="cap", hat_col="2f3a4e", belt="3e2a18", gear=1),
    N(50, "Master Farmer", "Job master", "master_farmer", skin="tan", hair="6b4423", style="short", shirt="b9d08a", sleeve="short", pants="3f5f8a", boots="5a3a22", hat="straw", hat_col="d9b95b", hat2="a0522d", beard="stache", gloves="8a6a3a"),
    N(51, "Master Fisherman", "Job master", "master_fisherman", skin="brown", hair="c9c9c9", style="short", shirt="e0b13a", outfit="coat", outer="e0b13a", trim="6b4423", pants="2f4a5a", boots="1d1d22", hat="cap", hat_col="e0b13a", beard="long", beard_col="d8d8d8"),
    N(52, "Master Miner", "Job master", "master_miner", skin="tan", hair="2a2018", style="short", shirt="7a5a3a", pants="4a4036", boots="2a2018", hat="helmet", hat_col="a9a090", hat2="6b4423", cuffs="3e2a18", belt="3e2a18", pouches=1, scar=1),
    N(53, "Sensei Mechanical Craftsman", "Jobs 2 sensei", "sensei_mechanical_craftsman", skin="light", hair="3a2a1c", style="short", shirt="8a8f96", pants="3a3f46", apron="6b4423", goggles="down", lens="ffb347", gloves="3e2a18", belt="3e2a18", pouches=1, gear=1),
    N(54, "Master Digger", "Job master", "master_digger", skin="brown", hair="3a2a1c", style="short", shirt="8a7a5a", sleeve="short", pants="5a4a30", boots="3e2a18", hat="bandana", hat_col="b0643a", gloves="6b4423", belt="3e2a18"),
    N(55, "Master Chef", "Job master", "master_chef", skin="fair", hair="6b3a1c", style="short", shirt="efe9dc", outfit="coat", outer="efe9dc", trim="c0392b", pants="2f3a4e", boots="1d1d22", hat="toque", hat_col="f4f0e6", beard="stache"),
    N(56, "Master Woodcutter", "Job master", "master_woodcutter", skin="light", hair="7a3a1c", style="short", shirt="b03a2e", pants="4a3a2a", boots="3e2a18", outfit="vest", outer="2f2a22", beard="long", beard_col="7a3a1c", belt="3e2a18", gloves="6b4423"),
    N(57, "Sensei Iron Samurai", "Jobs 3 sensei", "sensei_iron_samurai", skin="fair", hair="1d1d22", style="bun", shirt="2f2a38", pants="2f2a38", outfit="armor", hat="kabuto", beard="stache", beard_col="1d1d22", belt="9a2f3a", scarf="9a2f3a"),
    N(58, "Master Explorer", "Job master", "master_explorer", skin="tan", hair="6b4423", style="short", shirt="c9b78a", pants="6b5a3a", boots="4a3a22", hat="pith", hat_col="d8c89a", hat2="6b4423", scarf="b03a2e", backpack="7a5a3a", belt="3e2a18", glasses=0),
    N(59, "Master Builder", "Job master", "master_builder", skin="light", hair="3a2a1c", style="short", shirt="4a6a9a", pants="6b6a60", boots="2a2a2e", outfit="vest", outer="e0b13a", hat="hardhat", hat_col="e0b13a", hat2="9a7a1a", belt="3e2a18", pouches=1),
    N(60, "Master Hunter", "Job master", "master_hunter", skin="tan", hair="3a2a1c", style="short", shirt="5a7a4a", pants="4a3a2a", boots="3e2a18", outfit="coat", outer="3f5a38", trim="6b4423", hat="hood", hat_col="3f5a38", belt="3e2a18", backpack="6b4423"),
    N(61, "Sensei Machine Lord", "Jobs 4 sensei", "sensei_machine_lord", skin="ash", hair="none", style="bald", shirt="3a3f46", pants="2a2e34", outfit="coat", outer="3a3f46", trim="b0643a", monocle=1, gear=1, shoulder="767c85", beard="stache", beard_col="55555a", gloves="1d1d22"),
    N(62, "Master Brewer", "Job master", "master_brewer", skin="fair", hair="b0643a", style="short", shirt="e0d6bc", pants="3a2f4a", apron="7a4a8a", glasses=1, beard="short", beard_col="b0643a", boots="3e2a18", belt="3e2a18"),
    N(63, "Master Crafter", "Job master", "master_crafter", skin="light", hair="3a2a1c", style="ponytail", shirt="9a7a50", pants="4a3a2a", apron="8a5a2a", belt="3e2a18", pouches=1, hat="bandana", hat_col="3f5a38"),
    N(64, "Master Enchanter", "Job master", "master_enchanter", skin="pale", hair="d8d0e8", style="long", shirt="4a2f6a", pants="2f2040", outfit="robe", outer="5a3a80", trim="c19a3e", hat="hood", hat_col="5a3a80", rune="d9b6ff", scarf="9a7ad0"),
    N(44, "Jiraiya", "Shinobi master (Jobs 5)", "jiraiya", skin="fair", hair="e8e8f0", style="wild", shirt="1d1d22", pants="1d1d22", outfit="coat", outer="2a2a32", trim="b03a2e", hat="ninja", hat_col="1d1d22", hat2="b03a2e", belt="b03a2e", scarf="b03a2e", cuffs="767c85", boots="1d1d22"),
    N(65, "Sensei Emperor of Steam", "Jobs 5 sensei", "sensei_emperor_of_steam", skin="light", hair="d8d8d8", style="short", shirt="7a1f2a", pants="2a2230", outfit="coat", outer="8a1f2e", trim="e8c25a", hat="crown", hat_col="2a2230", hat2="e8c25a", beard="mutton", beard_col="d8d8d8", shoulder="e8c25a", gear=1, goggles=None, boots="1d1d22"),
    N(66, "Master Weaponsmith", "Job master", "master_weaponsmith", skin="brown", hair="none", style="bald", shirt="6a5a4a", sleeve="short", pants="3a3228", apron="3e2a18", beard="long", beard_col="2a2018", gloves="1d1d22", belt="3e2a18", pouches=1, boots="2a2018"),
    N(68, "Master Armorsmith", "Job master", "master_armorsmith", skin="tan", hair="1d1d22", style="short", shirt="767c85", pants="3a3f46", outfit="armor", shoulder="767c85", goggles="iron", lens="ffb347", beard="short", gloves="1d1d22", boots="3a3f46"),
    # ---- teahouse / food (Sakura side)
    N(82, "Aiko", "Tea House", "aiko", skin="pale", hair="2a1e22", style="bun", shirt="f2b6c8", pants="e8a0b8", outfit="kimono", outer="f2b6c8", trim="ffffff", obi="9a2f5a", blush=1, hat="flower", boots="6b4423"),
    N(83, "Goro", "Sake Brewery", "goro", skin="tan", hair="2a2018", style="short", shirt="e8e0d0", pants="2f3a4e", outfit="happi", outer="2f4a7a", hat="hachimaki", hat_col="efe9dc", belt="efe9dc", boots="3e2a18"),
    N(84, "Taro", "BBQ Master", "taro", skin="brown", hair="1d1d22", style="spiky", shirt="efe9dc", sleeve="short", pants="2a2a2e", apron="b03a2e", hat="bandana", hat_col="b03a2e", gloves="6b4423", beard="stache", beard_col="1d1d22"),
    N(85, "Daichi", "Sushi Master", "daichi", skin="light", hair="1d1d22", style="short", shirt="f4f0e6", outfit="coat", outer="f4f0e6", trim="2f4a7a", pants="2f3a4e", hat="hachimaki", hat_col="f4f0e6", apron="2f4a7a", boots="6b4423"),
    N(86, "Momo", "Candy Merchant", "momo", skin="fair", hair="e07aa0", style="ponytail", shirt="f7d6e0", pants="e8a0b8", apron="ffffff", hat="ribbon", hat_col="e07aa0", hat2="ffffff", blush=1, sleeve="short", boots="c0507a", cuffs="e07aa0"),
    N(87, "Sora", "Fruit Market", "sora", skin="tan", hair="6b3a1c", style="short", shirt="f0a030", sleeve="short", pants="6b8a3a", apron="d97a2a", hat="straw", hat_col="e0c46a", hat2="c0392b", boots="5a3a22"),
    N(88, "Ren", "Coffee House", "ren", skin="light", hair="2a1e18", style="short", shirt="efe9dc", pants="3e2a18", outfit="vest", outer="6b4423", glasses=1, belt="2a1e18", boots="2a1e18", scarf="b0643a"),
    N(89, "Yuki", "Ice Cream Cart", "yuki", skin="pale", hair="e8f2fa", style="long", shirt="bfe3f0", pants="8fb8d0", apron="ffffff", hat="ribbon", hat_col="7fd0e8", hat2="ffffff", blush=1, boots="e8f2fa", sleeve="short"),
    # ---- merchants
    N(95, "Brakka Stonewall", "Armor Merchant", "brakka_stonewall", skin="tan", hair="b0643a", style="short", shirt="767c85", pants="4a4036", outfit="armor", hat="helmet", hat_col="767c85", hat2="c19a3e", beard="dwarf", beard_col="b0643a", gloves="3e2a18", boots="3a3f46", shoulder="767c85"),
    N(78, "Gemologist Petra", "Gem Shop", "gemologist_petra", skin="light", hair="1d1d22", style="long", shirt="2f4a7a", pants="2a2a3a", outfit="coat", outer="2f6f8a", trim="e8c25a", goggles="up", lens="7fd0e8", gear=0, boots="2a2a3a", rune="7fd0e8", cuffs="e8c25a"),
    N(81, "FishingRodney", "Rod Merchant", "fishingrodney", skin="light", hair="a0501c", style="short", shirt="3a6a8a", pants="5a4a30", boots="1d3a2a", hat="cap", hat_col="3a6a8a", hat2="e0b13a", outfit="vest", outer="6b8a3a", beard="short", beard_col="a0501c", gloves="6b4423"),
    N(96, "Aster Veyl", "Special Upgrade Merchant", "aster_veyl", skin="ash", hair="c8d8f0", style="long", shirt="2a4a5a", pants="1f2f3a", outfit="robe", outer="2f5a6a", trim="7fd0e8", hat="hood", hat_col="2f5a6a", rune="7fd0e8", gear=0, boots="1f2f3a"),
    N(90, "Elias Packwright", "Backpack maker", "elias_packwright", skin="light", hair="6b4423", style="short", shirt="9a7a50", pants="4a3a2a", backpack="7a5a3a", pipes=1, belt="3e2a18", pouches=1, hat="cap", hat_col="6b4423", boots="3e2a18", beard="short"),
    N(91, "Mara Threadneedle", "Backpack tailor", "mara_threadneedle", skin="fair", hair="b0643a", style="bun", shirt="8a4a6a", pants="3a2f4a", apron="c9b78a", scarf="e0b13a", glasses=1, belt="3e2a18", pouches=1, boots="3e2a18"),
    N(92, "Brann Gearlock", "Backpack engineer", "brann_gearlock", skin="brown", hair="2a2018", style="short", shirt="5a4a3a", pants="3a3228", apron="6b4423", goggles="down", lens="ffb347", gear=1, gloves="3e2a18", beard="short", boots="2a2018"),
    N(97, "Orion the Adventurer", "Backpack adventurer", "orion_the_adventurer", skin="tan", hair="6b4423", style="short", shirt="6b8a5a", pants="5a4a30", outfit="vest", outer="7a5a3a", hat="pith", hat_col="a0916a", hat2="6b4423", backpack="8a5a2a", scarf="b03a2e", belt="3e2a18", pouches=1, boots="3e2a18"),
    # ---- story
    N(79, "Vorkan Kross", "Blacksmith (contracts)", "vorkan_kross", skin="brown", hair="1d1d22", style="short", shirt="4a3a32", sleeve="short", pants="2a2a2e", apron="3e2a18", beard="short", beard_col="1d1d22", eyepatch=1, scar=1, gloves="1d1d22", shoulder="3a3f46", boots="1d1d22", belt="1d1d22"),
    N(76, "Mad doctor", "Mad Doctor", "mad_doctor", skin="pale", hair="f2f2f2", style="wild", shirt="efe9dc", pants="3a3f46", outfit="labcoat", outer="f4f0e6", trim="c19a3e", goggles="down", lens="9aff7a", gloves="3e3a4a", boots="1d1d22", gear=1),
    N(94, "Cleric", "Cleric", "cleric", skin="fair", hair="e8c25a", style="short", shirt="efe9dc", pants="efe9dc", outfit="robe", outer="f4f0e6", trim="e8c25a", hat="hood", hat_col="f4f0e6", scarf="e8c25a", boots="e8c25a", rune="e8c25a"),
    N(126, "Capitan Usop", "Sea captain (to the capital)", "capitan_usop", skin="tan", hair="3a2a1c", style="short", shirt="efe9dc", pants="2f3a4e", outfit="coat", outer="2f4a7a", trim="e8c25a", hat="captain", hat_col="2f4a7a", hat2="e8c25a", beard="mutton", beard_col="3a2a1c", belt="3e2a18", shoulder="e8c25a", boots="1d1d22"),
    N(125, "Alpha Test Techcore", "Techcore engineer", "techcore_engineer", skin="light", hair="2a2a30", style="short", shirt="8a8f96", pants="5a6068", outfit="jumpsuit", outer="767c85", goggles="down", lens="7fd0e8", gloves="3e2a18", belt="3e2a18", pouches=1, boots="3a3f46", gear=0),
    N(122, "Tutorial", "Tutorial guide", "tutorial_guide", skin="light", hair="6b4423", style="short", shirt="e8c25a", pants="3a2f4a", outfit="coat", outer="9a2f3a", trim="e8c25a", hat="tophat", hat_col="2a2230", hat2="e8c25a", gear=1, boots="1d1d22"),
    N(123, "Dialogue tutorial", "Dialogue guide", "dialogue_guide", skin="fair", hair="2a2a30", style="short", shirt="4a6a9a", pants="2a2a3a", outfit="vest", outer="2f4a7a", glasses=1, belt="3e2a18", scarf="e8c25a", boots="2a2a3a"),
    N(127, "Race Assignor", "Race Assignor", "race_assignor", skin="ash", hair="9ad0c8", style="long", shirt="2f6f6a", pants="1f3a38", outfit="robe", outer="2f8a82", trim="e8c25a", hat="hood", hat_col="2f8a82", rune="9ad0c8", boots="1f3a38"),
    N(128, "Class Assignor", "Class Assignor", "class_assignor", skin="fair", hair="3a2a1c", style="short", shirt="6a2f3a", pants="2a2230", outfit="armor", hat="helmet", hat_col="767c85", hat2="b03a2e", shoulder="b03a2e", scarf="b03a2e", boots="2a2230", gloves="1d1d22"),
    # ---- guards (24 Citizens NPCs, use any of the 4 looks)
    N(93, "Guard", "Guard variant A (iron helmet, brown beard)", "guard_a", skin="light", hair="3a2a1c", style="short", shirt="7a6a58", pants="3a3228", outfit="armor", hat="helmet", hat_col="767c85", hat2="c19a3e", beard="short", scarf="9a2f3a", gloves="3e2a18", boots="3a3f46", shoulder="767c85"),
    N(99, "Guard", "Guard variant B (kabuto-style, moustache)", "guard_b", skin="fair", hair="1d1d22", style="short", shirt="2f2a38", pants="2f2a38", outfit="armor", hat="kabuto", beard="stache", beard_col="1d1d22", scarf="b03a2e", boots="2f2a38", shoulder="9a2f3a"),
    N(100, "Guard", "Guard variant C (brass cap, tan)", "guard_c", skin="tan", hair="6b4423", style="short", shirt="5a6a7a", pants="3a3f46", outfit="armor", hat="cap", hat_col="c19a3e", boots="3e2a18", gloves="1d1d22", scarf="2f4a7a", belt="3e2a18"),
    N(101, "Guard", "Guard variant D (goggles, red hair)", "guard_d", skin="pale", hair="b0643a", style="short", shirt="8a8f96", pants="3a3f46", outfit="armor", hat="helmet", hat_col="aab2bb", hat2="b0643a", goggles="down", lens="ffb347", beard="short", beard_col="b0643a", boots="3a3f46", shoulder="aab2bb"),
]


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.join(here, "..")
    out = os.path.join(root, "skins")
    os.makedirs(out, exist_ok=True)
    skins, rows = [], []
    for i, sp in enumerate(ROSTER):
        sp = dict(sp)
        if sp.get("goggles") is None:
            sp.pop("goggles", None)
        s = build(sp, 1000 + i)
        skins.append(s)
        write_png(os.path.join(out, sp["slug"] + ".png"), s.px)
        rows.append((sp["id"], sp["name"], sp["role"], sp["slug"] + ".png"))
    sheet(skins, os.path.join(root, "preview_sheet.png"))
    with open(os.path.join(root, "npc_skins.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["citizens_id", "npc_name", "role", "file"])
        w.writerows(rows)
    with open(os.path.join(root, "apply_commands.txt"), "w", encoding="utf-8") as f:
        f.write("# Citizens: run in game as an OP. Replace <URL> with the public address of the PNG (see README).\n"
                "# The 24 'Guard' NPCs can use guard_a..guard_d in any mix (ids 93, 99-121).\n")
        for cid, name, role, fn in rows:
            f.write(f"/npc select {cid}   (* {name})\n/npc skin --url <URL>/{fn}\n")
    print("wrote", len(rows), "skins")


if __name__ == "__main__":
    main()
