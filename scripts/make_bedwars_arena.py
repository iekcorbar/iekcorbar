#!/usr/bin/env python3
"""make_bedwars_arena.py - de BedWars-arena als CODE: acht Sponge-v2 .schem delen.

Waarom: Hihou 0.3/0.4/0.5 (etappes 1-3) draait live, maar er staat op de server nog geen
enige arena - dus is niets ooit écht getest. Kerstdorp-leer: wat niet in git staat, bestaat
niet. Dit script bouwt de klassieke opzet: 1 lobby + 4 basiseilanden (rood/blauw/groen/geel)
+ 2 diamond-mids + 2 emerald-mids. Bruggen ontbreken bewust - dat doen de spelers zelf.

Usage (repo root):
    python -I scripts/make_bedwars_arena.py              -> deploy/bedwars/*.schem (8 delen)
    python -I scripts/make_bedwars_arena.py <outdir>     -> andere bestemming
    python -I scripts/make_bedwars_arena.py --overzicht  -> maten per deel, geen files

In het spel (WorldEdit op de server), y=0 van elk deel = onderkant van het drijvende platform:
    //schem load bedwars-00-lobby        //pos1 <plek>  //paste -a
    ...enz., eilanden +/- 34 blokken uit elkaar (diamond-mids halverwege, emerald ver daarbuiten).
Daarna de setup in de plugin (checklist wordt meegeprint).

Markeringen (afgesproken conventions, het script legt ze, de commands wijzen ze aan):
    note_block   = generatorpad (kijk ernaar bij /ih bw gen add)
    anvil        = shop-marker  (kijk ernaar bij /ih bw team <arena> shop <team>)
    <kleur>_bed  = het teambed   (kijk ernaar bij /ih bw team <arena> bed <team>)
    chest        = vrije teamkist (BlockEntity maakt WorldEdit zelf bij de paste)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_trailer_schems import Set, controleer  # noqa: E402  zelfde bouwer en check als dorpen/trailer

Lucht = "minecraft:air"
BASIS = "minecraft:polished_blackstone"
BORD = "minecraft:polished_blackstone_bricks"
DICK = "minecraft:blackstone"
GLAS = "minecraft:glass"
EINDSTAAF = "minecraft:end_rod"
ZEEGLOW = "minecraft:sea_lantern"
LANTERNE = "minecraft:lantern[hanging=true]"
BLOEM = "minecraft:red_mushroom_block"
KETEN = "minecraft:chain[axis=y]"
SPRUK = "minecraft:spruce_planks"
SPRUK_TRAP = "minecraft:spruce_stairs"
KIST = "minecraft:chest[facing=south;type=single;waterlogged=false]"
AMBEEL = "minecraft:anvil[facing=south]"
PADYZER = "minecraft:note_block"
GOLDBLOK = "minecraft:gold_block"
DIAMANT = "minecraft:diamond_block"
SMARAGD = "minecraft:emerald_block"


def platform(st, y, kleur, r=7):
    """Het drijvende eiland: blackstone-underkant taps, wol-vloer op y, betonnen rand ring."""
    for x in range(st.w):
        for z in range(st.l):
            d = max(abs(x - (st.w - 1) // 2), abs(z - (st.l - 1) // 2))  # Chebyshev tot midden
            if d <= r:
                st.put(x, y, z, kleur + "_wool")
                st.put(x, y - 1, z, kleur + "_concrete")
            if d <= r - 1:
                st.put(x, y - 2, z, DICK)
            if d == r:
                st.put(x, y + 1, z, BORD)  # lage rand: geen wall-state gedoe, gewoon blokken
            if d in (r - 1, r - 2):
                for dy in range(1, 3):
                    st.put(x, y + dy, z, Lucht)
    # onderkant aftoppen: kegel
    for x in range(st.w):
        for z in range(st.l):
            d = max(abs(x - (st.w - 1) // 2), abs(z - (st.l - 1) // 2))
            if d <= r - 3:
                st.put(x, y - 3, z, BASIS)
            if d <= r - 5:
                st.put(x, y - 4, z, DICK)


def island(kleur):
    """Eén teambasis: bed achter een glazen nis, twee ijzer-pads, een goud-pad, ambeer-shop, vrije kist."""
    st = Set(15, 10, 15)
    y = 5
    platform(st, y, kleur)
    c = (7, 7)

    def put(x, yy, z, s):
        st.put(x, yy, z, s)

    # bed-nis: verhoogd podium van glas met ketting-lantaarns
    for x in range(5, 10):
        for z in range(4, 7):
            put(x, y + 1, z, BORD)
    put(6, y + 2, 5, kleur + "_bed[facing=south;part=foot]")
    put(6, y + 2, 6, kleur + "_bed[facing=south;part=head]")
    for x in (5, 9):
        for z in (4, 6):
            put(x, y + 1, z, GLAS)
            put(x, y + 2, z, GLAS)
            put(x, y + 3, z, GLAS)
    put(7, y + 3, 4, EINDSTAAF)
    put(7, y + 4, 5, KETEN)
    put(7, y + 5, 5, LANTERNE)
    # ijzer-pads: note block op gouden voet, twee hoeken van het eiland
    for (x, z) in ((3, 3), (11, 3)):
        put(x, y + 1, z, GOLDBLOK)
        put(x, y + 2, z, PADYZER)
    # goud-pad: dichter naar het midden
    put(3, y + 1, 11, GOLDBLOK)
    put(3, y + 2, 11, PADYZER)
    # shop (ambeer) + teamkist naast het nis-podium
    put(10, y + 1, 8, AMBEEL)
    put(11, y + 1, 8, KIST)
    return st, "bedwars basis %s: bed (facing south), 2x ijzer-pad, 1x goud-pad, ambeer-shop, kist" % kleur


def mid(mat, naam):
    """Neutral mid: klein blackstone plateau met het dure pad in het midden + twee kisten."""
    st = Set(9, 8, 9)
    y = 4
    for x in range(st.w):
        for z in range(st.l):
            d = max(abs(x - 4), abs(z - 4))
            if d <= 4:
                st.put(x, y, z, BASIS)
            if d <= 3:
                st.put(x, y - 1, z, DICK)
            if d <= 2:
                st.put(x, y - 2, z, DICK)
    st.put(4, y + 1, 4, GOLDBLOK)
    st.put(4, y + 2, 4, PADYZER)
    st.put(4, y + 3, 4, mat)
    st.put(2, y + 1, 2, KIST)
    st.put(6, y + 1, 6, KIST)
    for x in (1, 7):
        st.put(x, y + 1, 4, EINDSTAAF)
    return st, "neutral mid (%s pad met note_block eronder)" % naam


def lobby():
    """ Wachtrij-eiland: zwartstenen vloer, sprukten prieel met zee-lantaarn, gouden middengloed."""
    st = Set(13, 12, 13)
    y = 3
    for x in range(st.w):
        for z in range(st.l):
            d = max(abs(x - 6), abs(z - 6))
            if d <= 6:
                st.put(x, y, z, BASIS)
            if d <= 5:
                st.put(x, y - 1, z, DICK)
            if d <= 4:
                st.put(x, y - 2, z, DICK)
    for x in (3, 9):
        for z in (3, 9):
            for dy in range(1, 5):
                st.put(x, y + dy, z, "minecraft:spruce_log[axis=y]")
    for x in range(2, 11):
        for z in range(2, 11):
            d = max(abs(x - 6), abs(z - 6))
            if d <= 4:
                st.put(x, y + 5, z, SPRUK)
                if (x + z) % 5 == 0:
                    st.put(x, y + 5, z, SPRUK_TRAP)
    st.put(6, y + 4, 6, ZEEGLOW)
    st.put(6, y, 6, GOLDBLOK)
    # pad naar het veld: rand van beton + bloemetjes
    for z in range(2, 11):
        if abs(z - 6) <= 4:
            st.put(0, y, z, BORD)  # randrandje richting het veld
    st.put(6, y + 1, 2, BLOEM)
    st.put(8, y + 1, 9, BLOEM)
    return st, "BedWars lobby: prieel, zee-lantaarn, gouden spawn-plaat (y=0 = platformvloer)"


DELEN = [
    ("bedwars-00-lobby", lobby),
    ("bedwars-01-rood", lambda: island("red")),
    ("bedwars-02-blauw", lambda: island("blue")),
    ("bedwars-03-groen", lambda: island("green")),
    ("bedwars-04-geel", lambda: island("yellow")),
    ("bedwars-05-diamond-oost", lambda: mid(DIAMANT, "diamond")),
    ("bedwars-06-diamond-west", lambda: mid(DIAMANT, "diamond")),
    ("bedwars-07-emerald-zuid", lambda: mid(SMARAGD, "emerald")),
]

CHECKLIST = """
Setting in de plugin (na het plakken, alle commands staff):
  /ih bw create eekdam
  sta in het prieel:      /ih bw set eekdam spawn
  sta ernaast (lucht):    /ih bw set eekdam spec
  sta tussen de eilanden: /ih bw set eekdam center   en  /ih bw set eekdam size 70
  /ih bw set eekdam min 2   /ih bw set eekdam maxteam 4
  per team (rood, blauw, groen, geel) - eerst aanmaken, dan aanwijzen:
     /ih bw team eekdam add red red
     sta op het wol-veld van het eiland:  /ih bw team eekdam spawn red
     kijk naar het bed:                   /ih bw team eekdam bed red
     kijk naar de ambeer:                 /ih bw team eekdam shop red
     kijk naar een note_block (goud-voet):/ih bw gen eekdam add iron red
     ...tweede ijzer-pad opnieuw, en:     /ih bw gen eekdam add gold red
  (idem blauw/groen/geel), dan de mids:
     kijk naar het note_block op het diamond-mid: /ih bw gen eekdam add diamond
     idem emerald-mid:                             /ih bw gen eekdam add emerald
  /ih bw start eekdam            (of wacht op 2 wachtenden - auto-start)
Tijdens de wedstrijd: /ih bw shop | /ih bw kit classic|builder|miner|archer
"""


def main():
    out = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "deploy/bedwars"
    overzicht = "--overzicht" in sys.argv
    os.makedirs(out, exist_ok=True)
    print("bedwars-delen -> %s" % out)
    for naam, bouw in DELEN:
        st, beschrijving = bouw()
        if overzicht:
            print("\n%s  %d x %d x %d" % (naam, st.w, st.h, st.l))
            print("   %d blokstoffen" % len(st.palette))
            continue
        pad = os.path.join(out, naam + ".schem")
        st.save(pad, naam, beschrijving)
        controleer(pad)
        print("  ok %-28s %2dx%2dx%2d  %3d blokstoffen  %6d B" % (
            naam, st.w, st.h, st.l, len(st.palette), os.path.getsize(pad)))
    if not overzicht:
        print(CHECKLIST)


if __name__ == "__main__":
    main()
