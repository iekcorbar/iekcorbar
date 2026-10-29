# make_trailer_schems.py - bouwt de trailer-sets als Sponge-v2 .schem.
# Formaat geverifieerd 29-10 tegen 'Golden Tree (2023).schem' van de server
# (FAWE, WorldEdit 7.4.6): gzip big-endian NBT, root "Schematic", Version 2,
# sleutel "BlockData" = varint-array (y-outer, z, x-inner), Palette = compound
# van blokstate-string -> int, Offset = IntArray(3), Metadata met WEOffset-xyz.
#
#   python -X utf8 scripts/make_trailer_schems.py check <file.schem>
#   python -X utf8 scripts/make_trailer_schems.py gen   [uitmap]
#
# puure stdlib (gzip/struct) - geen nbtlib nodig.
import gzip
import os
import struct
import sys

T_END, T_BYTE, T_SHORT, T_INT, T_LONG, T_FLOAT, T_DOUBLE, T_BYTES, T_STR, T_LIST, T_COMPOUND, T_INTS, T_LONGS = range(13)

Lucht = "minecraft:air"


def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def w_comp(items):
    out = b""
    for naam, tt, payload in items:
        out += struct.pack(">B", tt) + _str(naam) + payload
    return out + b"\x00"


def varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out += bytes([b | 0x80])
        else:
            return out + bytes([b])


def int_array(vals):
    return struct.pack(">i", len(vals)) + b"".join(struct.pack(">i", v) for v in vals)


def list_empty(tag=0):
    return struct.pack(">Bi", tag, 0)


# ------------------------------------------------------------------ NBT-reader
def _rstr(f):
    (n,) = struct.unpack(">H", f.read(2))
    return f.read(n).decode("utf-8", "surrogateescape")


def _rread(f, tt):
    if tt == T_BYTE:
        return struct.unpack(">b", f.read(1))[0]
    if tt == T_SHORT:
        return struct.unpack(">h", f.read(2))[0]
    if tt == T_INT:
        return struct.unpack(">i", f.read(4))[0]
    if tt == T_LONG:
        return struct.unpack(">q", f.read(8))[0]
    if tt in (T_FLOAT, T_DOUBLE):
        f.read(4 if tt == T_FLOAT else 8)
        return None
    if tt == T_BYTES:
        (n,) = struct.unpack(">i", f.read(4))
        return f.read(n)
    if tt == T_STR:
        return _rstr(f)
    if tt == T_INTS:
        (n,) = struct.unpack(">i", f.read(4))
        return struct.unpack(">" + "i" * n, f.read(4 * n)) if n else ()
    if tt == T_LONGS:
        (n,) = struct.unpack(">i", f.read(4))
        return f.read(8 * n)
    if tt == T_LIST:
        st = f.read(1)[0]
        (n,) = struct.unpack(">i", f.read(4))
        return [_rread(f, st) for _ in range(n)]
    if tt == T_COMPOUND:
        d = {}
        while True:
            t = f.read(1)[0]
            if t == T_END:
                break
            naam = _rstr(f)          # LET OP: naam vóór waarde lezen - in één
            d[naam] = _rread(f, t)   # regel Python eerst de RHS (waarde)!
        return d
    raise ValueError("onbekend tagtype %s" % tt)


def lees_schem(pad):
    with gzip.open(pad) as f:
        (t,) = struct.unpack(">B", f.read(1))
        assert t == T_COMPOUND
        root = _rstr(f)
        return root, _rread(f, T_COMPOUND)


def controleer(pad, stil=False):
    root, d = lees_schem(pad)
    w, h, l = d["Width"], d["Height"], d["Length"]
    pm, data = d["PaletteMax"], d["BlockData"]
    assert root == "Schematic" and d["Version"] == 2 and pm == len(d["Palette"])
    assert "BlockData" in d
    n, i, nietlucht = 0, 0, 0
    while i < len(data):
        shift = val = 0
        while True:
            b = data[i]
            i += 1
            val |= (b & 0x7F) << shift
            if not b & 0x80:
                break
            shift += 7
        assert val < pm
        if val:
            nietlucht += 1
        n += 1
    assert n == w * h * l, "blockdata %d != %d" % (n, w * h * l)
    if not stil:
        print("OK %-38s %3dx%3dx%3d  palette %2d  niet-lucht %d" % (
            os.path.basename(pad), w, h, l, pm, nietlucht))
    return d


# ------------------------------------------------------------------ Set-bouwer
class Set:
    def __init__(self, breed, hoog, diep):
        self.w, self.h, self.l = breed, hoog, diep
        self.g = [[[0] * diep for _ in range(hoog)] for _ in range(breed)]
        self.palette = [Lucht]
        self.idx = {Lucht: 0}

    def put(self, x, y, z, s):
        if not (0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.l):
            return
        if s == Lucht:
            self.g[x][y][z] = 0
            return
        if s not in self.idx:
            self.idx[s] = len(self.palette)
            self.palette.append(s)
        self.g[x][y][z] = self.idx[s]

    def box(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, s)

    def save(self, pad, naam, omschrijving=""):
        assert len(self.palette) <= 256, "palette te groot"
        data = b""
        for y in range(self.h):
            for z in range(self.l):
                for x in range(self.w):
                    data += varint(self.g[x][y][z])
        pal = [(b, T_INT, struct.pack(">i", i)) for i, b in enumerate(self.palette)]
        meta = [("name", T_STR, _str(naam)),
                ("Description", T_STR, _str(omschrijving)),
                ("WEOffsetX", T_INT, struct.pack(">i", 0)),
                ("WEOffsetY", T_INT, struct.pack(">i", 0)),
                ("WEOffsetZ", T_INT, struct.pack(">i", 0))]
        body = w_comp([
            ("Version", T_INT, struct.pack(">i", 2)),
            ("DataVersion", T_INT, struct.pack(">i", 3337)),
            ("Width", T_SHORT, struct.pack(">h", self.w)),
            ("Height", T_SHORT, struct.pack(">h", self.h)),
            ("Length", T_SHORT, struct.pack(">h", self.l)),
            ("Offset", T_INTS, int_array([0, 0, 0])),
            ("BlockData", T_BYTES, struct.pack(">i", len(data)) + data),
            ("PaletteMax", T_INT, struct.pack(">i", len(self.palette))),
            ("Palette", T_COMPOUND, w_comp(pal)),
            ("BlockEntities", T_LIST, list_empty(T_COMPOUND)),
            ("Entities", T_LIST, list_empty(T_COMPOUND)),
            ("Metadata", T_COMPOUND, w_comp(meta)),
        ])
        with open(pad, "wb") as f:
            f.write(gzip.compress(struct.pack(">B", T_COMPOUND) + _str("Schematic") + body))


# ------------------------------------------------------------------ stukjes decor
GRAS = "minecraft:grass_block[snowy=false]"
PAD = "minecraft:dirt_path"
WATER = "minecraft:water"
ZWART = "minecraft:polished_blackstone"
LAMPGLOEI = "minecraft:shroomlight"
KAP = "minecraft:polished_blackstone_slab[type=top]"
STAM = "minecraft:cherry_log[axis=y]"
BLAD = "minecraft:cherry_leaves"
BLOEisel = "minecraft:pink_petals"
STEEN = "minecraft:stone_bricks"
MOSSY = "minecraft:mossy_cobblestone"
AND = "minecraft:polished_andesite"
ZWARTEOOG = "minecraft:black_concrete"
RED = "minecraft:red_concrete"
ZWAR = "minecraft:black_concrete"
GLOW = "minecraft:sea_lantern"


def kersenboom(st, x, y, z, stam=4, r=3):
    for i in range(stam):
        st.put(x, y + i, z, STAM)
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(0, 3):
                if dx * dx + dz * dz + dy * dy <= r * r + 1 and not (dx == 0 and dz == 0 and dy == 0):
                    st.put(x + dx, y + stam - 2 + dy, z + dz, BLAD)


def lantaarnpaal(st, x, y, z, h=3):
    for i in range(h):
        st.put(x, y + i, z, ZWART)
    st.put(x, y + h, z, LAMPGLOEI)
    st.put(x, y + h + 1, z, KAP)


# ============================================================ S1: lantaarn-laan
def set1():
    st = Set(46, 12, 20)
    # terrein: gras + twee paden + water channel in het midden (z 9-10-11)
    for x in range(st.w):
        for z in range(st.l):
            st.put(x, 0, z, GRAS)
            if 6 <= z <= 7 or 13 <= z <= 14:
                st.put(x, 0, z, PAD)
            if 9 <= z <= 11:
                st.put(x, 0, z, WATER)
    # bruggen
    for bx in (14, 30):
        for dx in range(3):
            for z in range(8, 13):
                st.put(bx + dx, 1, z, "minecraft:spruce_planks")
        for z in (7, 14):
            st.put(bx + 1, 1, z, "minecraft:spruce_planks")
    # lantaarnpalen: twee rijen langs de grachten
    for x in range(2, 45, 7):
        lantaarnpaal(st, x, 1, 5)
        lantaarnpaal(st, x + 3, 1, 16)
    # kersenbomen
    for (x, z) in ((4, 3), (12, 17), (22, 2), (27, 17), (37, 4), (43, 15)):
        kersenboom(st, x, 1, z)
    # bloesem-tapijt
    for (x, z) in ((5, 4), (11, 16), (23, 3), (28, 16), (38, 5), (42, 14)):
        st.put(x, 1, z, BLOEisel)
    return st


# ============================================================ S2: stenen hoofden
def set2():
    st = Set(20, 14, 10)
    # plateau + achterwand
    st.box(0, 0, 0, 19, 0, 9, MOSSY)
    st.box(0, 1, 0, 19, 1, 1, MOSSY)
    st.box(0, 1, 8, 19, 8, 9, STEEN)          # lage muur achter de hoofden
    # drie hoofden op sokkels (oplopend in hoogte)
    for (cx, h, s) in ((4, 2, 1), (10, 3, 0), (16, 2, 2)):
        st.box(cx - 1, 2, 4, cx + 1, 2 + h - 1, 5, MOSSY)          # sokkel diep 2
        kop0, kop1 = cx - 1, cx + 1
        y0 = 2 + h
        st.box(kop0, y0, 4, kop1, y0 + 1, 5, AND)                   # kop 3x2x2
        # ogen aan zuidkant (z=4): twee zwarte punten
        st.put(cx - 1, y0, 4, ZWARTEOOG)
        st.put(cx + 1, y0, 4, ZWARTEOOG)
        # gloed op het voorhoofd
        st.put(cx, y0 + 1, 4, GLOW)
    # paar lantaarnpalen bij de hoeken
    for x in (1, 18):
        lantaarnpaal(st, x, 2, 6, h=4)
    return st


# ============================================================ S3: torii
def set3():
    st = Set(18, 15, 12)
    # stenen plate + 3 treden naar het plateau (oplopend in hoogte)
    st.box(2, 0, 2, 15, 0, 10, STEEN)
    st.box(0, 0, 5, 1, 0, 5, MOSSY)
    st.box(0, 1, 5, 1, 1, 5, MOSSY)
    st.box(0, 1, 6, 1, 1, 7, MOSSY)
    # twee zuilen (2x2), rood met zwarte voeten
    for cx in (5, 12):
        st.box(cx, 1, 6, cx + 1, 1, 7, ZWAR)
        st.box(cx, 2, 6, cx + 1, 9, 7, RED)
    # nuki (dwarsbalk) + isakura (tussengebintje) + kasagi (kroonlijst, dubbel zwart)
    st.box(3, 7, 5, 14, 7, 8, RED)
    st.box(6, 8, 6, 11, 8, 7, ZWART)
    st.box(2, 9, 4, 15, 9, 9, ZWART)
    st.box(2, 10, 5, 15, 10, 8, ZWART)
    st.box(2, 10, 4, 3, 10, 4, ZWART)      # opstanders van de kroon
    st.box(14, 10, 4, 15, 10, 4, ZWART)
    # twee kersenbomen achter + lantaarns ervoor
    kersenboom(st, 3, 1, 11)
    kersenboom(st, 14, 1, 11)
    lantaarnpaal(st, 4, 1, 4)
    lantaarnpaal(st, 13, 1, 4)
    return st


# ------------------------------------------------------------------------ main
if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "gen"
    if what == "check":
        controleer(sys.argv[2])
    else:
        uit = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "deploy", "trailer-schems")
        os.makedirs(uit, exist_ok=True)
        sets = [("iekcorbar-trailer-S1-sakura-laan", "S1 sakura-lantaarn-laan (46x12x20)", set1()),
                ("iekcorbar-trailer-S2-stenen-hoofden", "S2 drie stenen hoofden (20x14x10)", set2()),
                ("iekcorbar-trailer-S3-torii", "S3 torii-poort (18x15x12)", set3())]
        for naam, bes, st in sets:
            pad = os.path.join(uit, naam + ".schem")
            st.save(pad, naam, bes)
            controleer(pad)
