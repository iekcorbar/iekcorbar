#!/usr/bin/env python3
"""make_santa.py - bouwt de kerstman als FMM-model (.bbmodel) + een preview-PC.

Waarom zelfstandig: Wildlife tekent zijn 203 dieren met een eigen 255-cel palette en een flinke
Model-klasse; de kerstman is ONE model en hoort bij Xmas. Dit script schrijft exact hetzelfde
Blockbench-v4.9 "free"-formaat (inline base64-palette, bones met origins, cubes met uv-eilanden,
animaties per bot-uuid) maar dan in eigen beheer, met een kleine kerstpalette. Deterministisch
(uuid5 uit een teller), dus een herbouw levert geen byte-diff van het hele model op.

Richting: FreeMinecraftModels gebruikt **-X als voorkant** (Wildlife draait daarvoor elke
ontwerpas met oriented()), en de vloer is y=0. Dit model is DIRECT in die ruimte getekend:
x = voor-achter (neus naar -x), y = omhoog, z = links/rechts. Poot- en armzwaai is rotatie om de
Z-as, hoofdschudden om Y, een knik in de rug om X.

Usage (uit Xmas/):  python -I tools/make_santa.py
   -> fmm-models/xmas/xmas_santa.bbmodel            (naar plugins/FreeMinecraftModels/models/xmas/)
   -> tools/preview-santa.png                       (zij- en vooraanzicht, teken ik zelf)
"""
import base64
import itertools
import json
import math
import os
import struct
import sys
import uuid
import zlib

RES = 128
CELL = 8
OUT_DIR = "fmm-models/xmas"
NAME = "xmas_santa"

PALETTE = {
    "red": (196, 40, 44), "red_dark": (148, 26, 30), "white": (246, 244, 240),
    "white_shade": (216, 214, 208), "black": (34, 32, 34), "gold": (226, 186, 70),
    "skin": (228, 178, 142), "skin_dark": (198, 148, 116), "sack": (154, 118, 76),
    "sack_dark": (120, 88, 56), "green": (52, 128, 74), "eye": (26, 24, 26),
    "cheek": (224, 130, 130),
}
NAMES = list(PALETTE)

_c = itertools.count()


def U():
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "%s/%d" % (NAME, next(_c))))


# ------------------------------------------------------------------ png + palette
def png(pix):
    raw = b""
    for row in pix:
        raw += b"\x00" + bytes(v for px in row for v in px)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(pix[0]), len(pix), 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def palette_png():
    px = [[(0, 0, 0, 0)] * RES for _ in range(RES)]
    for i, n in enumerate(NAMES):
        r, g, b = PALETTE[n][:3]
        cx, cy = (i % 16) * CELL, (i // 16) * CELL
        for y in range(CELL):
            for x in range(CELL):
                px[cy + y][cx + x] = (r, g, b, 255)
    return png(px)


TEX = "data:image/png;base64," + base64.b64encode(palette_png()).decode()


def uv(col):
    i = NAMES.index(col)
    u, v = (i % 16) * CELL, (i // 16) * CELL
    return [u + 1, v + 1, u + 7, v + 7]


# ------------------------------------------------------------------ model
class Model:
    def __init__(self, name):
        self.name = name
        self.bones, self.elements, self.anims, self.roots = {}, [], [], []

    def bone(self, name, origin, parent=None):
        b = {"name": name, "origin": [float(v) for v in origin], "uuid": U(),
             "rotation": [0, 0, 0], "children": []}
        self.bones[name] = b
        if parent:
            self.bones[parent]["children"].append(b)
        else:
            self.roots.append(b)
        return b

    def cube(self, bone, x1, y1, z1, x2, y2, z2, col):
        e = {"name": "%s_%d" % (bone, len(self.elements)), "uuid": U(), "autouv": 0,
             "from": [x1, y1, z1], "to": [x2, y2, z2], "origin": [0, 0, 0], "rotation": [0, 0, 0],
             "faces": {f: {"uv": uv(col), "texture": "#0"}
                       for f in ("north", "south", "east", "west", "up", "down")}}
        self.elements.append(e)
        self.bones[bone]["children"].append(e["uuid"])
        return e

    def anim(self, name, length, tracks, loop="loop"):
        """tracks: bot-naam -> {kanaal: [(tijd, (x,y,z)), ...]} - één keyframe per kanaal per punt,
        precies zoals make_animals.dat schrijft (Blockbench-eisen voor FMM-clips)."""
        animators = {}
        for bn, chans in tracks.items():
            kfs = []
            for chan, points in chans.items():
                for t, (x, y, z) in points:
                    kfs.append({"channel": chan,
                                "data_points": [{"x": str(round(x, 2)), "y": str(round(y, 2)),
                                                 "z": str(round(z, 2))}],
                                "uuid": U(), "time": round(t, 3), "color": -1,
                                "interpolation": "linear"})
            animators[self.bones[bn]["uuid"]] = {"name": bn, "type": "bone", "keyframes": kfs}
        self.anims.append({"uuid": U(), "name": name, "loop": loop, "override": False,
                           "length": length, "snapping": 20, "selected": False,
                           "anim_time_update": "", "blend_weight": "", "start_delay": "",
                           "loop_delay": "", "animators": animators})

    def outline(self, b):
        kids = []
        for c in b["children"]:
            kids.append(c if isinstance(c, str) else self.outline(c))
        return {"name": b["name"], "origin": b["origin"], "rotation": b["rotation"], "color": 0,
                "uuid": b["uuid"], "export": True, "children": kids}

    def bbmodel(self):
        return {"meta": {"format_version": "4.9", "model_format": "free", "box_uv": False},
                "name": self.name, "model_identifier": "", "visible_box": [1, 1, 0],
                "variable_placeholders": "", "variable_placeholder_buttons": [],
                "timeline_setups": [], "unhandled_root_fields": {},
                "resolution": {"width": RES, "height": RES},
                "elements": self.elements, "outliner": [self.outline(b) for b in self.roots],
                "textures": [{"path": "", "name": "xmas_santa.png", "folder": "texture",
                              "namespace": "", "id": "0", "width": RES, "height": RES,
                              "uv_mode": "autoshow", "covered": False,
                              "data": {"icon": "far", "mode": "bitmap", "palleted": False,
                                       "resolution": 16}, "source": TEX}],
                "animations": self.anims}


def golf(n, lengte, amp, fase=0.0, as_=2, basis=0.0):
    """Sinus-sleutelbeelden: n+1 punten over lengte seconden, uitslag om `as_` (0=x,1=y,2=z)."""
    out = []
    for i in range(n + 1):
        r = [0.0, 0.0, 0.0]
        r[as_] = basis + amp * math.sin(2 * math.pi * (i / n) + fase)
        out.append((lengte * i / n, tuple(r)))
    return out


# ------------------------------------------------------------------ de kerstman
# 37 px hoog = 2,3 m: een forse, ronde man. y=0 is de grond, neus naar -x.
def bouw():
    m = Model(NAME)
    red, rd, wh, ws, bl, gd = "red", "red_dark", "white", "white_shade", "black", "gold"
    sk, skd, sa, sad, gr, eye = "skin", "skin_dark", "sack", "sack_dark", "green", "eye"

    m.bone("body", (0, 15, 0))
    # romp: dikke buik, voorover bol
    m.cube("body", -7, 9, -6, 5, 22, 6, red)
    m.cube("body", -7.5, 9, -5, -6.5, 13, 5, red)             # buikrand die voorbij de neus uitsteekt
    m.cube("body", -8, 13, -4, -7, 18, 4, red)               # de allergrootste buikomtrek
    m.cube("body", -7, 9, -6, 5, 11, 6, wh)                  # bies van de jaszoom
    m.cube("body", -7, 17, -6.3, 5, 19.5, 6.3, bl)           # riem
    m.cube("body", -7.6, 17.4, -1.8, -6.6, 19.1, 1.8, gd)    # riemgesp
    m.cube("body", -6.5, 21.5, -5, 4, 23, 5, wh)             # kraag

    # hoofd met baard en pet
    m.bone("head", (0, 23, 0), "body")
    m.cube("head", -4.5, 23, -4, 3.5, 29, 4, sk)
    m.cube("head", -6, 21.5, -3.5, -4, 26.5, 3.5, wh)        # baard, voor de kin
    m.cube("head", -5.5, 19.5, -2.5, -4, 22.5, 2.5, wh)      # baardpunt
    m.cube("head", -6.2, 25.5, -1.2, -5.2, 26.8, 1.2, skd)   # neus
    m.cube("head", -5.4, 26.6, -2.8, -4.8, 27.4, -2.0, eye)  # ogen
    m.cube("head", -5.4, 26.6, 2.0, -4.8, 27.4, 2.8, eye)
    m.cube("head", -5.6, 24.4, -3.4, -5.0, 25.4, -2.6, "cheek")
    m.cube("head", -5.6, 24.4, 2.6, -5.0, 25.4, 3.4, "cheek")
    m.cube("head", -5, 27.5, -4.3, -4.2, 29.2, 4.3, bl)      # bril/brilmootjes? nee: wenkbrauw-rand
    # pet: witte band, rode kegel die naar achteren hangt, witte bom
    m.cube("head", -5.2, 29, -4.4, 4, 30.6, 4.4, wh)
    m.cube("head", -4.6, 30.6, -3.8, 3.4, 34, 3.8, red)
    m.cube("head", 2, 33, -2.6, 6.5, 35.4, 2.6, red)
    m.cube("head", 6, 35, -1.6, 9, 38, 1.6, wh)              # pompom
    m.cube("head", 5.6, 34.6, -1.2, 7, 36, 1.2, rd)           # slof van de pet

    # armen: schouder bij y=21; ze hangen BUITEN de romp (anders verdwijnen ze erin vanaf de voorzijde)
    # en zijn een tint donkerder dan de jas, zodat de armen van het lichaam te onderscheiden zijn
    for nm, sz in (("arm_l", 1), ("arm_r", -1)):
        z1, z2 = (6.4, 9.6) if sz > 0 else (-9.6, -6.4)
        m.bone(nm, (0, 21, (z1 + z2) / 2), "body")
        m.cube(nm, -3.0, 12, z1, 2.0, 21.6, z2, rd)
        m.cube(nm, -3.4, 12, z1 - 0.2, 2.4, 13.8, z2 + 0.2, wh)      # manchet
        m.cube(nm, -4.0, 9.8, z1 + 0.4, -1.2, 12.8, z2 - 0.4, wh)    # wants

    # benen + laarzen
    for nm, sz in (("leg_l", 1), ("leg_r", -1)):
        z1, z2 = (0.4, 3.6) if sz > 0 else (-3.6, -0.4)
        m.bone(nm, (0, 9, (z1 + z2) / 2), "body")
        m.cube(nm, -3.5, 3, z1, 1.5, 9.4, z2, rd)
        m.cube(nm, -5.4, 0, z1 - 0.3, 2.2, 3.4, z2 + 0.3, bl)        # laars
        m.cube(nm, -5.4, 2.6, z1 - 0.3, 2.2, 3.6, z2 + 0.3, wh)      # omslag

    # zak over de schouder: zit aan de romp, maar draait mee bij het zwaaien
    m.bone("sack", (2, 20, 6), "body")
    m.cube("sack", 1, 6, 3, 9, 17, 11, sa)
    m.cube("sack", 2, 16, 4, 8, 20, 10, sa)
    m.cube("sack", 3, 19.5, 5, 7, 21.5, 9, sad)                # touw
    m.cube("sack", 4.5, 20.5, 6, 6, 23, 7.5, gr)               # een pak die eruit steekt
    m.cube("sack", 5.4, 22.6, 6.4, 6.4, 23.4, 7.4, gd)         # strik van dat pak

    # ---- animaties (FMM speelt idle/walk/death zelf)
    q = 3.2
    m.anim("idle", q, {
        "body": {"position": [(0, (0, 0, 0)), (q / 2, (0, 0.16, 0)), (q, (0, 0, 0))]},
        "head": {"rotation": golf(6, q, 4.0, 0.6, as_=1)},
        "arm_l": {"rotation": golf(6, q, 3.0, 0.0, as_=2)},
        "arm_r": {"rotation": golf(6, q, 3.0, math.pi, as_=2)},
        "sack": {"rotation": golf(4, q, 2.0, 0.4, as_=2)},
    })
    w = 1.3
    m.anim("walk", w, {
        "body": {"position": [(0, (0, 0, 0)), (w / 4, (0, 0.3, 0)), (w / 2, (0, 0, 0)),
                              (3 * w / 4, (0, 0.3, 0)), (w, (0, 0, 0))],
                 "rotation": [(0, (6, 0, 0)), (w, (6, 0, 0))]},
        "leg_l": {"rotation": golf(8, w, 26.0, 0.0, as_=2)},
        "leg_r": {"rotation": golf(8, w, 26.0, math.pi, as_=2)},
        "arm_l": {"rotation": golf(8, w, 20.0, math.pi, as_=2)},
        "arm_r": {"rotation": golf(8, w, 20.0, 0.0, as_=2)},
        "head": {"rotation": golf(8, w, 2.5, 0.5, as_=1)},
        "sack": {"rotation": golf(8, w, 3.5, 0.8, as_=2)},
    })
    m.anim("greet", 1.6, {
        "arm_r": {"rotation": [(0, (0, 0, 0)), (0.3, (0, 0, -78)), (0.6, (0, 0, -62)),
                               (0.9, (0, 0, -78)), (1.2, (0, 0, -62)), (1.6, (0, 0, 0))]},
        "head": {"rotation": [(0, (0, 0, 0)), (0.8, (0, 10, 0)), (1.6, (0, 0, 0))]},
    })
    m.anim("death", 1.0, {
        "body": {"rotation": [(0, (0, 0, 0)), (0.6, (14, 0, 0)), (1.0, (14, 0, 0))],
                 "position": [(0, (0, 0, 0)), (0.6, (0, -8, 0)), (1.0, (0, -8, 0))]},
    }, loop="hold")
    return m


# ------------------------------------------------------------------ preview (eigen renderer)
def project(p, hoek):
    """Zijaanzicht (kamera langs +z) en vooraanzicht (kamera langs -x); geen perspectief."""
    x, y, z = p
    if hoek == "zij":
        return x, y, z
    return z, y, -x


def render(m, hoek, size=170):
    """Eenvoudige schilder-rendering: alle vlakken, van ver naar dichtbij, met hun celkleur."""
    vlakken = []
    for e in m.elements:
        a, b = e["from"], e["to"]
        hok = [(x, y, z) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
        volgorde = {"zij": [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4),
                            (1, 3, 7, 5)],
                    "voor": [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4),
                              (1, 3, 7, 5)]}[hoek]
        ijk = e["faces"]["north"]["uv"]
        cx, cy = int(ijk[0]) // CELL, int(ijk[1]) // CELL
        kleur = PALETTE[NAMES[cy * 16 + cx]][:3]
        for q in volgorde:
            ptn = [project(hok[i], hoek) for i in q]
            diep = sum(p[2] for p in ptn) / 4.0
            vlakken.append((diep, ptn, kleur))
    xs = [p[0] for _d, v, _k in vlakken for p in v]
    ys = [p[1] for _d, v, _k in vlakken for p in v]
    minX, maxX, minY, maxY = min(xs), max(xs), min(ys), max(ys)
    skaal = (size - 10) / max(maxX - minX, maxY - minY, 1)
    can = [[(30, 26, 24)] * size for _ in range(size)]

    def vul(ptn, kleur):
        pts = [((p[0] - minX) * skaal + 5, size - 5 - (p[1] - minY) * skaal) for p in ptn]
        ys2 = [p[1] for p in pts]
        for py in range(max(0, int(min(ys2))), min(size, int(max(ys2)) + 1)):
            kruis = []
            for i in range(len(pts)):
                (x1, y1), (x2, y2) = pts[i], pts[i - 1]
                if (y1 > py + .5) != (y2 > py + .5):
                    kruis.append(x1 + (py + .5 - y1) * (x2 - x1) / (y2 - y1))
            kruis.sort()
            for i in range(0, len(kruis) - 1, 2):
                for px in range(max(0, int(kruis[i])), min(size, int(kruis[i + 1]) + 1)):
                    can[py][px] = kleur
    for diep, v, k in sorted(vlakken, key=lambda t: t[0]):
        vul(v, k)
    return can


def hoofdstuk():
    m = bouw()
    os.makedirs(OUT_DIR, exist_ok=True)
    pad = os.path.join(OUT_DIR, NAME + ".bbmodel")
    with open(pad, "w", encoding="utf-8") as f:
        json.dump(m.bbmodel(), f)
    # controle: botten, elementen en de 26.2-grens
    ys = [v for e in m.elements for v in (e["from"][1], e["to"][1])]
    xs = [v for e in m.elements for v in (e["from"][0], e["to"][0], e["from"][2], e["to"][2])]
    print("wrote %s: %d botten, %d elementen, %d clips, hoog %.1f px (%.2f m)" % (
        pad, len(m.bones), len(m.elements), len(m.anims), max(ys), max(ys) / 16))
    print("   x/z-bereik %.1f .. %.1f  (binnen de -24..24 van de 26.2-exportoffset)" % (min(xs), max(xs)))
    # preview: twee aanzichten naast elkaar (elke pixel 4 bytes, want IHDR belooft RGBA)
    A = render(m, "zij")
    B = render(m, "voor")
    S = len(A)
    led = (246, 246, 246, 255)
    can = [[led] * (S * 2 + 12) for _ in range(S)]
    for y in range(S):
        for x in range(S):
            can[y][x] = A[y][x] + (255,)
            can[y][S + 12 + x] = B[y][x] + (255,)
    with open("tools/preview-santa.png", "wb") as f:
        f.write(png(can))
    print("   preview: tools/preview-santa.png (links zij, rechts voor)")


if __name__ == "__main__":
    hoofdstuk()
