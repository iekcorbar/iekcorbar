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


# ============================================================ S4: kerker-poort
DEEP = "minecraft:cobbled_deepslate"
DEEPT = "minecraft:deepslate_tiles"
DEEPST = "minecraft:deepslate_brick_stairs[facing=south,half=top]"
DEEPSTN = "minecraft:deepslate_brick_stairs[facing=north,half=top]"
GANIE = "minecraft:gilded_blackstone"
SOULL = "minecraft:soul_lantern[hanging=true]"
SOULV = "minecraft:soul_lantern"
KETTING = "minecraft:chain[axis=y]"

def set4():
    st = Set(26, 16, 14)
    # voorplein + traptreden van 2 naar 1
    st.box(0, 0, 0, 25, 0, 13, DEEP)
    # twee torens
    for (tx0, tx1) in ((2, 5), (20, 23)):
        st.box(tx0, 1, 4, tx1, 13, 9, DEEPT)
        st.box(tx0 + 1, 1, 5, tx1 - 1, 13, 8, Lucht)          # hol
    # kantelen op de torens
    for (tx0, tx1) in ((2, 5), (20, 23)):
        for x in range(tx0, tx1 + 1):
            for z in (4, 9):
                if (x + z) % 2 == 0:
                    st.put(x, 14, z, DEEPT)
        for z in range(4, 10):
            if (2 + z) % 2 == 0:
                st.put(tx0, 14, z, DEEPT)
                st.put(tx1, 14, z, DEEPT)
    # muur ertussen met poort-opening
    st.box(6, 1, 6, 19, 7, 8, DEEPT)
    st.box(11, 1, 6, 14, 6, 8, Lucht)                          # opening 4 breed
    st.box(11, 7, 6, 14, 7, 8, DEEPT)                          # latei
    st.box(12, 8, 6, 13, 8, 8, GANIE)                          # sluitsteen
    # donker gangpad: gloed-kussentjes naast de poort
    st.put(9, 1, 7, "minecraft:magma_block")
    st.put(16, 1, 7, "minecraft:magma_block")
    # hangende zielen-lantarennetten in de poort (geen botsing met muur)
    st.put(12, 5, 7, SOULL)
    st.put(13, 5, 7, SOULL)
    # sjieke smalle pilasters van zwartsteen
    for x in (10, 15):
        st.box(x, 1, 5, x, 8, 5, ZWART)
    for (lx, lz) in ((3, 3), (22, 3), (10, 4), (15, 4)):
        st.put(lx, 1, lz, SOULV)
    return st


# ============================================================ S5: burchttoren
TUFF = "minecraft:tuff_bricks"
CHITUFF = "minecraft:chiseled_tuff_bricks"
BULB = "minecraft:copper_bulb"

def set5():
    st = Set(12, 26, 12)
    st.box(0, 0, 0, 11, 3, 11, DEEPT)                          # brede voet
    st.box(1, 4, 1, 10, 17, 10, TUFF)                          # schacht
    for y in range(4, 18):                                     # gepijlde hoeken
        for (cx, cz) in ((1, 1), (1, 10), (10, 1), (10, 10)):
            st.put(cx, y, cz, DEEPT)
    for y in range(6, 17, 3):                                  # lichte ramen
        for x in (3, 5, 7):
            st.put(x, y, 1, BULB)
            st.put(x, y, 10, BULB)
    st.box(0, 18, 0, 11, 20, 11, DEEPT)                        # bekrooning
    for x in range(12):                                        # kantelen
        for z in range(12):
            if (x in (0, 11) or z in (0, 11)) and (x + z) % 2 == 0:
                st.put(x, 21, z, DEEPT)
    for y in (22, 23, 24):                                     # mast + wimpel
        st.put(6, y, 6, KETTING)
    st.put(5, 24, 6, RED)
    st.put(6, 25, 6, RED)
    st.put(7, 24, 6, RED)
    return st


# ============================================================ S6: zwevend eiland
GRAS = "minecraft:grass_block[snowy=false]"
AARDE = "minecraft:dirt"
MOSSB = "minecraft:moss_block"
STEEN8 = "minecraft:stone"

def set6():
    st = Set(16, 16, 16)
    # grasplateau (elig-vormig)
    for x in range(16):
        for z in range(16):
            dx, dz = x - 8, z - 8
            if dx * dx / 36.0 + dz * dz / 30.0 <= 1.0:
                st.put(x, 9, z, GRAS)
                st.put(x, 8, z, MOSSB if (x + z) % 3 == 0 else AARDE)
    # kegel eronder
    for y in range(3, 8):
        r = (8 - y) * 1.15
        for x in range(16):
            for z in range(16):
                dx, dz = x - 8, z - 8
                if dx * dx + dz * dz <= r * r:
                    st.put(x, y, z, DEEP if y > 5 else STEEN8)
    # hangende kettingen met lantarens aan de onderkant
    for (cx, cz) in ((5, 6), (11, 9), (8, 4)):
        for y in (5, 6, 7):
            st.put(cx, y, cz, KETTING)
        st.put(cx, 4, cz, SOULL)
    # boompje + lantaren op het eiland
    kers = "minecraft:cherry_log[axis=y]"
    for y in range(10, 13):
        st.put(8, y, 8, kers)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if abs(dx) + abs(dz) <= 1:
                st.put(8 + dx, 13, 8 + dz, "minecraft:cherry_leaves")
    st.put(8, 14, 8, "minecraft:cherry_leaves")
    st.put(5, 10, 9, LAMPGLOEI)
    return st


# ============================================================ S7: theehuis
SPR = "minecraft:spruce_planks"
SPRST = "minecraft:spruce_stairs[facing=south,half=top]"
SPRSTN = "minecraft:spruce_stairs[facing=north,half=top]"
SPRSTO = "minecraft:spruce_stairs[facing=east,half=top]"
SPRSTW = "minecraft:spruce_stairs[facing=west,half=top]"
CHST = "minecraft:cherry_stairs[facing=south,half=top]"

def set7():
    st = Set(14, 12, 14)
    st.box(1, 0, 1, 12, 0, 12, ZWART)                          # vloer
    for (px, pz) in ((2, 2), (11, 2), (2, 11), (11, 11)):      # stijlen
        for y in range(1, 5):
            st.put(px, y, pz, STAM)
    st.box(2, 1, 2, 11, 2, 11, Lucht)                          # interieur hol
    # lage wanden met open raam erboven
    st.box(2, 3, 2, 11, 3, 2, SPR)                             # achterwand half
    for x in range(2, 12):
        for y in (1, 2):
            st.put(x, y, 3, SPR) if x % 3 else None            # schermen voora
    for z in (4, 7, 10):                                       # zijpanelen
        for y in (1, 2):
            st.put(2, y, z, SPR)
            st.put(11, y, z, SPR)
    # dak: drie afgeplatte lagen, telkens een smaller
    st.box(0, 4, 0, 13, 4, 13, SPR)
    st.box(1, 5, 1, 12, 5, 12, SPR)
    st.box(2, 6, 2, 11, 6, 11, SPR)
    st.box(4, 7, 4, 9, 7, 9, SPR)
    st.box(5, 8, 5, 8, 8, 8, SPR)
    for x in range(14):                                        # dakrand-piek
        for z in (0, 13):
            st.put(x, 4, z, SPRST if z == 13 else SPRSTN)
    for z in range(1, 13):
        st.put(0, 4, z, SPRSTW)
        st.put(13, 4, z, SPRSTO)
    for y in (9, 10):                                          # topmast
        st.put(6, y, 6, KETTING)
    # hangende lampionnetten onder de dakrand
    for (lx, lz) in ((3, 1), (10, 1), (3, 12), (10, 12)):
        st.put(lx, 3, lz, "minecraft:lantern[hanging=true]")
    # assortiment theespullen binnen
    st.put(6, 1, 6, "minecraft:composter")
    st.put(7, 1, 6, GLOW)
    return st


# ============================================================ S8-S20: extra sets
MAGMA = "minecraft:magma_block"
OBS = "minecraft:obsidian"
GRATE = "minecraft:copper_grate"
KOPPER = "minecraft:copper_block"
CALC = "minecraft:calcite"
KWARTS = "minecraft:quartz_bricks"
EINDROD = "minecraft:end_rod"
LAVA = "minecraft:lava"
ROODWOL = "minecraft:red_wool"
WITWOL = "minecraft:white_wool"
GLASW = "minecraft:white_stained_glass"
IRON = "minecraft:iron_bars"
EIK = "minecraft:oak_planks"
SCAF = "minecraft:scaffolding"
SLABPB = "minecraft:polished_blackstone_slab"


def set8():
    # lantaarn-laantje (klein): voor close-ups en tussenscuts
    st = Set(14, 7, 8)
    st.box(0, 0, 0, 13, 0, 7, GRAS)
    st.box(0, 0, 2, 13, 0, 5, PAD)
    for x in (1, 5, 9, 13):
        lantaarnpaal(st, x, 1, 1, h=3)
    st.box(6, 1, 6, 7, 1, 6, SLABPB)           # bank
    st.box(6, 1, 7, 7, 1, 7, ZWART)
    kersenboom(st, 11, 1, 6, stam=3, r=2)
    return st


def set9():
    # vuurtoren: kalk wit met rode banden, lichtkoepel bovenin
    st = Set(14, 22, 14)
    st.box(1, 0, 1, 12, 0, 12, DEEP)
    st.box(2, 1, 2, 11, 6, 11, CALC)
    st.box(3, 7, 3, 10, 12, 10, CALC)
    st.box(4, 13, 4, 9, 16, 9, KWARTS)
    for y in (4, 10):                          # rode banden
        st.box(2, y, 2, 11, y, 11, RED)
        st.box(3, y, 3, 10, y, 10, Lucht)
        if y == 10:
            st.box(4, y, 4, 9, y, 9, RED)
    st.box(3, 17, 3, 10, 19, 10, GLASW)        # lampenkas
    st.box(5, 17, 5, 8, 19, 8, GLOW)           # het licht zelf
    st.box(2, 20, 2, 11, 20, 11, ZWART)        # kap
    st.box(2, 17, 2, 11, 17, 2, ZWART)         # galerij-randen
    st.box(2, 17, 11, 11, 17, 11, ZWART)
    return st


def set10():
    # luchtschip-meerpaal: mast met armen en gloeiringen
    st = Set(10, 18, 10)
    st.box(3, 0, 3, 6, 0, 6, ZWART)
    st.box(4, 1, 4, 5, 15, 5, ZWART)
    st.box(1, 13, 4, 8, 13, 5, IRON)           # meerarmen
    for y in (9, 11):
        for (rx, rz) in ((3, 5), (6, 4), (5, 3), (4, 6)):
            st.put(rx, y, rz, GRATE)
    st.put(4, 16, 4, EINDROD)
    st.put(5, 16, 5, EINDROD)
    st.put(4, 17, 5, GLOW)
    return st


def set11():
    # hokora: mini-heiligdom onder lantaarndak
    st = Set(8, 7, 8)
    st.box(0, 0, 0, 7, 0, 7, GRAS)
    st.box(2, 1, 2, 5, 1, 5, MOSSY)
    for (px, pz) in ((2, 2), (5, 2), (2, 5), (5, 5)):
        st.put(px, 2, pz, STAM)
        st.put(px, 3, pz, STAM)
    st.box(1, 4, 1, 6, 4, 6, SPR)
    st.box(2, 5, 2, 5, 5, 5, SPR)
    st.put(3, 5, 4, EINDROD)
    st.put(4, 1, 1, SOULV)                     # offersteen voorop
    st.put(1, 1, 3, LAMPGLOEI)
    st.put(6, 1, 4, LAMPGLOEI)
    return st


def set12():
    # stenen boogbrug over water
    st = Set(16, 8, 10)
    st.box(0, 0, 2, 15, 0, 7, WATER)
    st.box(0, 0, 0, 15, 0, 1, GRAS)
    st.box(0, 0, 8, 15, 0, 9, GRAS)
    st.box(3, 0, 1, 4, 4, 8, MOSSY)            # pijlers
    st.box(11, 0, 1, 12, 4, 8, MOSSY)
    st.box(0, 5, 0, 15, 5, 9, MOSSY)           # brugdek
    st.box(5, 1, 2, 10, 4, 7, Lucht)           # boog-opening
    for x in range(0, 16, 2):                  # hekwerk
        st.put(x, 6, 0, MOSSY)
        st.put(x, 6, 9, MOSSY)
    st.put(7, 1, 5, MAGMA)                     # meetlat onder de boog
    return st


def set13():
    # marktplein met drie gestreepte kraampjes
    st = Set(20, 8, 14)
    st.box(0, 0, 0, 19, 0, 13, PAD)
    for sx in (2, 8, 14):
        for (px, pz) in ((sx, 3), (sx + 3, 3), (sx, 6), (sx + 3, 6)):
            for y in (1, 2):
                st.put(px, y, pz, EIK)
        st.box(sx - 1, 3, 2, sx + 4, 3, 7, ROODWOL)
        for xx in range(sx - 1, sx + 5, 2):
            st.box(xx, 3, 4, xx, 3, 5, WITWOL)
        st.box(sx, 1, 3, sx + 3, 2, 3, SPR)    # toonbank
        st.put(sx + 1, 3, 6, GLOW)
        st.put(sx + 2, 1, 5, "minecraft:composter")
    lantaarnpaal(st, 0, 1, 12, h=3)
    lantaarnpaal(st, 19, 1, 12, h=3)
    return st


def set14():
    # smederij/fabrieksgevel: bakstenen hall met twee gloeiende schoorstenen
    st = Set(14, 11, 12)
    st.box(0, 0, 0, 13, 0, 11, DEEP)
    st.box(1, 1, 1, 12, 5, 10, STEEN)
    st.box(2, 2, 2, 11, 5, 9, Lucht)
    st.box(5, 1, 1, 8, 4, 1, Lucht)            # poort
    for x in (3, 10):
        st.put(x, 3, 1, BULB)                  # lichtgeven ramen
    for chx in (3, 10):
        st.box(chx, 6, 5, chx + 1, 9, 6, ZWART)
        st.put(chx, 10, 5, MAGMA)
        st.put(chx + 1, 10, 6, MAGMA)
    st.box(0, 1, 4, 0, 3, 7, GRATE)            # leidinggevel
    st.put(13, 1, 3, KOPPER)
    return st


def set15():
    # kerkgang: zuilengang met magma-strip en hangende zielen
    st = Set(8, 10, 20)
    st.box(0, 0, 0, 7, 0, 19, DEEP)
    st.box(0, 8, 0, 7, 8, 19, ZWART)
    for zz in range(2, 20, 4):
        st.box(3, 0, zz, 4, 0, zz, MAGMA)
    for z in range(20):
        st.box(0, 1, z, 0, 7, z, DEEPT)
        st.box(7, 1, z, 7, 7, z, DEEPT)
    for z in (2, 6, 10, 14, 18):
        for x in (1, 6):
            st.put(x, 7, z, KETTING)
            st.put(x, 6, z, SOULL)
    st.put(4, 1, 19, SOULV)
    return st


def set16():
    # baas-kamer:_obsidian arena, lavapoel, hangende kroonluchter
    st = Set(16, 12, 16)
    st.box(0, 0, 0, 15, 0, 15, OBS)
    cx = cz = 7
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            d2 = dx * dx + dz * dz
            if d2 <= 6:
                st.put(cx + dx, 0, cz + dz, LAVA)
            elif 25 <= d2 <= 34:
                for y in range(1, 6):
                    st.put(cx + dx, y, cz + dz, ZWART)
            elif 50 <= d2 <= 58 and (dx + dz) % 2 == 0:
                st.put(cx + dx, 9, cz + dz, ZWART)
    st.put(cx, 5, cz, SOULL)
    st.put(cx, 6, cz, KETTING)
    for (ox, oz) in ((-3, 0), (3, 0), (0, -3), (0, 3)):
        st.put(cx + ox, 7, cz + oz, SOULV)
    return st


def set17():
    # gilde-herberg: twee verdiepingen, lichten aan in de ramen
    st = Set(14, 13, 12)
    st.box(0, 0, 0, 13, 0, 11, DEEP)
    st.box(1, 1, 1, 12, 5, 10, EIK)
    st.box(2, 1, 2, 11, 5, 9, Lucht)
    st.box(6, 1, 1, 7, 4, 1, Lucht)            # deur
    for x in (3, 5, 8, 10):
        st.put(x, 3, 1, GLOW)                  # ramen (zee-lantaarn = licht)
    st.box(0, 6, 0, 13, 10, 11, EIK)           # bovenverdieping overkragend
    st.box(1, 7, 1, 12, 10, 10, Lucht)
    for x in (2, 5, 8, 11):
        st.put(x, 8, 0, GLOW)
    st.box(0, 11, 0, 13, 11, 11, SPR)          # dak
    st.box(1, 12, 1, 12, 12, 10, SPR)
    st.put(3, 1, 5, "minecraft:composter")
    st.put(9, 1, 7, "minecraft:composter")
    return st


def set18():
    # brouwerij-gevel: koperen gistketels met gloeiende bodem-klep
    st = Set(12, 9, 10)
    st.box(0, 0, 0, 11, 0, 9, MOSSY)
    for (vx, vz) in ((2, 2), (2, 6), (8, 4)):
        st.box(vx, 1, vz, vx + 1, 3, vz + 1, KOPPER)
        st.box(vx, 4, vz, vx + 1, 4, vz + 1, GRATE)
        st.put(vx + 1, 2, vz, BULB)
    st.box(0, 5, 2, 11, 5, 2, GRATE)           # leiding boven
    st.box(11, 1, 1, 11, 8, 8, ZWART)          # schoorsteen-gable
    st.put(11, 8, 4, MAGMA)
    lantaarnpaal(st, 0, 1, 8, h=3)
    st.put(5, 1, 8, SOULV)
    return st


def set19():
    # houten uitkijktoren (zoo/savanne-sfeer)
    st = Set(8, 14, 8)
    st.box(0, 0, 0, 7, 0, 7, GRAS)
    for (px, pz) in ((1, 1), (6, 1), (1, 6), (6, 6)):
        for y in range(1, 7):
            st.put(px, y, pz, "minecraft:oak_log[axis=y]")
    st.box(0, 7, 0, 7, 7, 7, SPR)              # platform
    for x in range(8):
        for z in (0, 7):
            if x % 2 == 0:
                st.put(x, 8, z, "minecraft:oak_log[axis=y]")
    for z in range(1, 7):
        for x in (0, 7):
            if z % 2 == 0:
                st.put(x, 8, z, "minecraft:oak_log[axis=y]")
    for y in range(9, 12):                     # palen voor dak
        st.put(3, y, 3, "minecraft:oak_log[axis=y]")
        st.put(4, y, 4, "minecraft:oak_log[axis=y]")
    st.box(2, 12, 2, 5, 12, 5, SPR)
    st.put(3, 9, 4, LAMPGLOEI)
    st.box(0, 1, 0, 1, 2, 1, SCAF)             # klimhulp
    return st


def set20():
    # tuinvijver met mini-torii: stil Act-1 detail
    st = Set(12, 5, 12)
    st.box(0, 0, 0, 11, 0, 11, GRAS)
    cx = cz = 6
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            d2 = dx * dx + dz * dz
            if d2 <= 7:
                st.put(cx + dx, 0, cz + dz, WATER)
            elif d2 <= 11:
                st.put(cx + dx, 0, cz + dz, MOSSY)
    # mini-torii linksachter
    for px in (1, 4):
        for y in (1, 2):
            st.put(px, y, 1, RED)
    st.box(0, 3, 1, 5, 3, 1, RED)
    st.box(0, 4, 1, 5, 4, 1, ZWAR)
    kersenboom(st, 10, 1, 2, stam=3, r=2)
    st.put(9, 1, 9, LAMPGLOEI)
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
        sets = [
            ("iekcorbar-trailer-S1-sakura-laan", "S1 sakura-lantaarn-laan", set1()),
            ("iekcorbar-trailer-S2-stenen-hoofden", "S2 drie stenen hoofden", set2()),
            ("iekcorbar-trailer-S3-torii", "S3 torii-poort", set3()),
            ("iekcorbar-trailer-S4-kerker-poort", "S4 kerker-poort met torens", set4()),
            ("iekcorbar-trailer-S5-burchttoren", "S5 burchttoren met kantelen", set5()),
            ("iekcorbar-trailer-S6-zwevend-eiland", "S6 zwevend eiland", set6()),
            ("iekcorbar-trailer-S7-theehuis", "S7 theehuis", set7()),
            ("iekcorbar-trailer-S8-lantaarn-laantje", "S8 klein lantaarn-laantje", set8()),
            ("iekcorbar-trailer-S9-vuurtoren", "S9 vuurtoren", set9()),
            ("iekcorbar-trailer-S10-meerpaal", "S10 luchtschip-meerpaal", set10()),
            ("iekcorbar-trailer-S11-hokora", "S11 hokora-heiligdom", set11()),
            ("iekcorbar-trailer-S12-boogbrug", "S12 stenen boogbrug", set12()),
            ("iekcorbar-trailer-S13-markt", "S13 marktplein met kraampjes", set13()),
            ("iekcorbar-trailer-S14-smederij", "S14 smederij/gevel met schoorstenen", set14()),
            ("iekcorbar-trailer-S15-kerkgang", "S15 kerkgang met zuilen", set15()),
            ("iekcorbar-trailer-S16-baaskamer", "S16 baas-kamer met lavapoel", set16()),
            ("iekcorbar-trailer-S17-herberg", "S17 gilde-herberg", set17()),
            ("iekcorbar-trailer-S18-brouwerij", "S18 brouwerij-gevel", set18()),
            ("iekcorbar-trailer-S19-uitkijktoren", "S19 houten uitkijktoren", set19()),
            ("iekcorbar-trailer-S20-tuinvijver", "S20 tuinvijver met mini-torii", set20()),
        ]
        for naam, bes, st in sets:
            pad = os.path.join(uit, naam + ".schem")
            st.save(pad, naam, bes)
            controleer(pad)
