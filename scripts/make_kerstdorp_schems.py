#!/usr/bin/env python3
"""make_kerstdorp_schems.py - bouwt Xmas Town als zes losse Sponge-v2 .schem delen.

Waarom dit script bestaat: het kerstdorp stond tot nu toe in drie .schem-bestanden in deploy/
(gitignored). CLAUDE.md beschrijft ze nog (boom 16x29x16, rode wolvilten-hut 12x12x12, lantaarn-
toren 5x18x11) maar de bestanden zelf zijn overal weg: uit deploy/, uit Downloads (de bron
`rebirthchristmasdemo-e3070.schem`) en van de server. Een dorp dat alleen in een gitignored mapje
bestaat is geen dorp, dus de opbouw ligt nu als CODE: herbouwbaar, aanpasbaar, nooi meer kwijt.

Usage (repo root):
    python -I scripts/make_kerstdorp_schems.py            -> deploy/kerstdorp/*.schem (6 delen)
    python -I scripts/make_kerstdorp_schems.py <outdir>   -> andere bestemming
    python -I scripts/make_kerstdorp_schems.py --overzicht -> maten + materiaal per deel, geen files

In het spel, precies zoals de trailer-delen (WorldEdit/FAWE op de server):
    //schem load kerstdorp-01-plein
    //paste -a
`-a` slaat lucht over, dus bomen en torens snijden geen gate uit het terrein eronder. Elk deel
staat op y=0 = maaiveld: //pos1 op de gewenste vloerplek en plakken.

Wat er BEWUST niet in kan: de kerst-props (adventkrans, kalender, cadeautoren, slee,
lichtslingers, countdown-bord, marktkraam) leven in chunk-PDC met ItemDisplay-entity's - die
bestaan niet in een schem. Elk deel draagt daarom in zijn Description-veld mee welke props erbij
horen, zodat `//paste`, `/relics list` en `/xmas list` samen het plaatje afmaken.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_trailer_schems import Set, controleer  # noqa: E402  (dezelfde bouwer en check als de trailer)

# ------------------------------------------------------------------ bouwstoffen
# Alleen blok-toestanden die scripts/make_trailer_schems.py al bewezen in het spel pakt. Een
# verzonnen eigenschap (say snow[layers=3]) maakt van een heel deel narigt; dit is een dorp, dus
# alles wat hier staat is gecontroleerd door controleer() na het schrijven.
Lucht = "minecraft:air"
SNEEUW = "minecraft:snow_block"
PAD = "minecraft:dirt_path"
KIEKEL = "minecraft:cobblestone"
KIEKEL_MOSS = "minecraft:mossy_cobblestone"
ANDESIET = "minecraft:polished_andesite"
DIEP = "minecraft:deepslate_tiles"
CALCITE = "minecraft:calcite"
KWARTS = "minecraft:quartz_bricks"
TON = "minecraft:chiseled_tuff_bricks"
ZWART = "minecraft:polished_blackstone"
ZWART_KAP = "minecraft:polished_blackstone_slab[type=top]"
SPRUK = "minecraft:spruce_planks"
SPRUK_STAM = "minecraft:spruce_log[axis=y]"
SPRUK_TRAP = "minecraft:spruce_stairs"
SPRUK_BLAD = "minecraft:spruce_leaves"
BERK_STAM = "minecraft:birch_log[axis=y]"
GOLD = "minecraft:gold_block"
ZEEGLOW = "minecraft:sea_lantern"
GLOEILAMP = "minecraft:shroomlight"
EINDSTAAF = "minecraft:end_rod"
LANTERN_HANG = "minecraft:lantern[hanging=true]"
KOMPOST = "minecraft:composter"          # markt-kraam: omgekeerde emmer als kist, bewezen in de trailer
WIT = "minecraft:white_wool"
ROOD = "minecraft:red_wool"
GROEN = "minecraft:green_wool"
BES_ROOD = "minecraft:red_concrete"
BES_GEEL = "minecraft:yellow_concrete"
GLAS = "minecraft:glass"
IJZERSTAAF = "minecraft:iron_bars"
KETEN = "minecraft:chain[axis=y]"
MOS = "minecraft:moss_block"


def vloer(st, s=SNEEUW, y=0):
    """De maaiveldlaag van een deel."""
    for x in range(st.w):
        for z in range(st.l):
            st.put(x, y, z, s)


def lantaarnpaal(st, x, y, z, h=4):
    """Zwart zuil met warm licht bovenin en een sneeuwkap - het silhouet van het dorp."""
    for i in range(h):
        st.put(x, y + i, z, ZWART)
    st.put(x, y + h, z, GLOEILAMP)
    st.put(x, y + h + 1, z, ZWART_KAP)
    st.put(x, y + h + 2, z, SNEEUW)


def spar(st, x, y, z, stam=2, laag=3):
    """Besneeuwde spar: stam + kegel van drie bladerlagen per schijf, sneeuw op de rand.

    Dikke lagen, geen platte schijven: een spar met gaten ertussen leest als een kandelaar.
    """
    for i in range(stam):
        st.put(x, y + i, z, SPRUK_STAM)
    for lvl in range(laag):
        r = laag - lvl
        yy = y + stam + lvl * 2 - 1
        for dy in range(3 if lvl == 0 else 2):
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + 1 and not (dx == 0 and dz == 0 and lvl > 0 and dy == 0):
                        st.put(x + dx, yy + dy, z + dz, SPRUK_BLAD)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r or abs(dz) == r:
                    if abs(dx) + abs(dz) <= r + 2:
                        st.put(x + dx, yy + (3 if lvl == 0 else 2), z + dz, SNEEUW)
    st.put(x, y + stam + laag * 2, z, SPRUK_BLAD)
    st.put(x, y + stam + laag * 2 + 1, z, SNEEUW)


def lichtslinger(st, x, y, z, richting, lengte):
    """Hangend lantaarn-spoor tussen palen: ketting + lantaarn. (Echte lichtslingers zijn een
    relic-prop; dit is het vaste lijnwerk eronder zodat het ook zonder props niet kaal is.)"""
    dx, dz = {"oost": (1, 0), "west": (-1, 0), "zuid": (0, 1), "noord": (0, -1)}[richting]
    for i in range(lengte):
        st.put(x + dx * i, y, z + dz * i, KETEN)
        st.put(x + dx * i, y - 1, z + dz * i, LANTERN_HANG)


def huis(st, x0, y0, z0, breed, diep, muur, gevel, hoog=4, nok=None, schoorsteen=True):
    """Los huisje: vloer, muren, zolderplafond, zadeldak van trappen met sneeuw, twee lichte ramen,
    een deuropening en optioneel een schoorsteen. Geeft de top-hoogte terug.

    Het dak gebruikt trappen met half=top, per rij van beide zijden naar de nok - dezelfde truc als
    de trailer-brouwerij, dus bewezen plakbaar. De nok Steekt één breedte boven de muur uit, zodat
    er een goot overhangt (dat is wat een dak een dak maakt).
    """
    x1, z1 = x0 + breed - 1, z0 + diep - 1
    if nok is None:
        nok = breed // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            st.put(x, y0, z, ANDESIET)
    for y in range(1, hoog + 1):
        for x in range(x0, x1 + 1):
            st.put(x, y0 + y, z0, muur)
            st.put(x, y0 + y, z1, muur)
        for z in range(z0, z1 + 1):
            st.put(x0, y0 + y, z, muur)
            st.put(x1, y0 + y, z, muur)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            st.put(x, y0 + hoog, z, SPRUK)
    # zadeldak
    for i in range(nok + 1):
        yy = y0 + hoog + 1 + i
        for z in range(z0 - 1, z1 + 2):
            if x0 + i <= x1 - i:
                st.put(x0 + i, yy, z, SPRUK_TRAP + "[facing=east,half=top]")
                st.put(x1 - i, yy, z, SPRUK_TRAP + "[facing=west,half=top]")
            if i and x0 + i < x1 - i:
                st.put(x0 + i, yy + 1, z, SNEEUW)
                st.put(x1 - i, yy + 1, z, SNEEUW)
    for z in range(z0 - 1, z1 + 2):
        st.put(x0 + nok, y0 + hoog + 1 + nok, z, SNEEUW)
    # ramen: glas in de gevel, warm licht erachter
    for zx in (z0 + 1, z1 - 1):
        st.put(x0, y0 + 2, zx, GLAS)
        st.put(x0 + 1, y0 + 2, zx, GLOEILAMP)
        st.put(x1, y0 + 2, zx, GLAS)
        st.put(x1 - 1, y0 + 2, zx, GLOEILAMP)
    # voorgevel: deur + een venster ernaast
    st.put(x0 + breed // 2, y0 + 1, z0, Lucht)
    st.put(x0 + breed // 2, y0 + 2, z0, Lucht)
    st.put(x0 + breed // 2, y0 + 1, z0 + 1, GLOEILAMP)
    if breed > 5:
        st.put(x0 + 1, y0 + 2, z0, GLAS)
        st.put(x0 + 1, y0 + 2, z0 + 1, GLOEILAMP)
    if gevel is not None:
        for x in range(x0, x1 + 1):
            st.put(x, y0 + hoog, z0, gevel)
    if schoorsteen:
        for y in range(hoog + 2, hoog + 6):
            st.put(x1 - 1, y0 + y, z1 - 1, KIEKEL)
        st.put(x1 - 1, y0 + hoog + 6, z1 - 1, DIEP)
        st.put(x1 - 1, y0 + hoog + 7, z1 - 1, EINDSTAAF)
    return y0 + hoog + nok + 2


# ============================================================ deel 1: het plein
def plein():
    """Het kerstplein met de countdown-toren. Hier hoort het bord dat de middag uitrolt."""
    st = Set(26, 26, 26)
    vloer(st)
    # kasseien ring + middenplein, met een sneeuwrand
    for x in range(st.w):
        for z in range(st.l):
            if 4 <= x <= 21 and 4 <= z <= 21:
                st.put(x, 0, z, KIEKEL if (x + z) % 3 else KIEKEL_MOSS)
            elif x in (0, 25) or z in (0, 25):
                st.put(x, 0, z, CALCITE)
    for x in range(8, 18):
        for z in range(9, 16):
            st.put(x, 0, z, ANDESIET)
    # middentrapje naar een verhoogd podiumpje (waar de kerstboom straks náást staat)
    st.box(11, 1, 11, 14, 1, 13, ANDESIET)
    st.box(11, 1, 13, 14, 1, 13, DIEP)
    # lantaarnpalen op de vier hoeken van het plein
    for (x, z) in ((5, 5), (20, 5), (5, 20), (20, 20)):
        lantaarnpaal(st, x, 1, z)
    # put in het midden-noord: kring van steen met water erin (een put zonder water is een kuil)
    st.box(2, 1, 12, 4, 2, 14, KIEKEL)
    st.put(3, 1, 13, "minecraft:water")
    # de countdown-toren tegen de noordmuur: 4 breed, 5 diep, 22 hoog
    tx, ty, tz = 11, 1, 20
    st.box(tx, ty, tz, tx + 3, ty + 1, tz + 4, DIEP)
    for y in range(2, 17):
        for x in range(tx, tx + 4):
            for z in range(tz, tz + 5):
                if x in (tx, tx + 3) or z in (tz, tz + 4):
                    st.put(x, y, z, TON if y % 4 else KWARTS)
    # trappetje BINNEN de toren: een klim van 4 stappen per richting, binnen de voetprint
    for i in range(12):
        hoek = i // 4
        stap = i % 4
        if hoek == 0:
            st.put(tx + 1, 3 + i, tz + 1 + stap, KIEKEL)
        elif hoek == 1:
            st.put(tx + 1 + stap, 3 + i, tz + 4, KIEKEL)
        elif hoek == 2:
            st.put(tx + 3, 3 + i, tz + 4 - stap, KIEKEL)
        else:
            st.put(tx + 3 - stap, 3 + i, tz + 1, KIEKEL)
    # klokgeleding: een wijzerplaat van glas met warm licht, HIGH op de toren - puur sfeer.
    # Het werkende countdown-bord is een relic-PROP die op de grond vóór de toren staat (zie pad hieronder).
    for x in range(tx + 1, tx + 3):
        for y in range(11, 14):
            st.put(x, y, tz, GLAS)
    st.put(tx + 1, 12, tz, GLOEILAMP)
    st.put(tx + 2, 11, tz, GLOEILAMP)
    for x in range(tx, tx + 4):
        st.put(x, 14, tz, KWARTS)
        st.put(x, 10, tz, KWARTS)
    for y in range(10, 15):
        st.put(tx, y, tz, TON)
        st.put(tx + 3, y, tz, TON)
    # vrij geplaveid pad vóór de toren: hier komt het countdown-bord te staan, dus geen muren/blokken
    for x in range(tx - 1, tx + 5):
        for z in range(tz - 5, tz):
            st.put(x, 0, z, ANDESIET)
            for y in range(1, 6):
                st.put(x, y, z, Lucht)
    # torenkap + sneeuw + ketting met lantaarn als kerstbel-vervanger
    for i in range(3):
        for x in range(tx + i, tx + 4 - i):
            st.put(x, 18 + i, tz + 1, SPRUK_TRAP + "[facing=south,half=top]")
            st.put(x, 19 + i, tz + 1, SNEEUW)
    st.box(tx + 1, 17, tz + 1, tx + 2, 17, tz + 3, DIEP)
    lichtslinger(st, tx + 1, 16, tz - 1, "noord", 2)
    # sieraden: twee takken spar en een stapel pakjes op het podiumpje
    spar(st, 8, 1, 8, 2, 3)
    spar(st, 18, 1, 18, 2, 3)
    st.box(12, 2, 12, 13, 3, 12, ROOD)
    st.put(12, 4, 12, GOLD)
    return st, ("Plein 26x26 met kasseien-ring, vier lantaarnpalen, put, sparren, podiumpje met "
                "pakken en een klokgeleding met wijzerplaat. HET VRIJE GEPLAVIEDE PAD VÓÓR DE TOREN "
                "IS VOOR HET BORD (schem kent geen props): /relics give countdown_board precies op "
                "dat pad, dan shift+rechtermuisklik om de tijdzone te kiezen (world Volg de eerst"
                "volgende wereld-middernacht). Verder hierbij: /relics give christmas_tree op het "
                "podiumpje, advent_wreath, string_lights langs de toren en 2-3 gift_box in het plein.")


# ============================================================ deel 2: de grote kerstboom
def kerstboom():
    """De dorpboom - 16 breed, 29 hoog, 16 diep, precies als het verloren deel uit CLAUDE.md."""
    st = Set(16, 29, 16)
    vloer(st)
    c = 8
    # berkentstronk (de boom mag een dikke voet hebben)
    for y in range(1, 5):
        st.put(c - 1, y, c - 1, BERK_STAM)
        st.put(c, y, c - 1, BERK_STAM)
        st.put(c - 1, y, c, BERK_STAM)
        st.put(c, y, c, BERK_STAM)
    # zeven lage schijven van elk drie bladerlagen: een volle kegel, geen etagere
    for r in range(7):
        radius = 6 - r
        yy = 5 + r * 3
        for dy in range(3):
            for dx in range(-radius, radius + 1):
                for dz in range(-radius, radius + 1):
                    if abs(dx) + abs(dz) <= radius + 2:
                        st.put(c + dx, yy + dy, c + dz, SPRUK_BLAD)
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if abs(dx) + abs(dz) >= radius + 1:
                    st.put(c + dx, yy + 3, c + dz, SNEEUW)
                if abs(dx) + abs(dz) == radius and r % 2 == 0 and (dx + dz) % 3 == 0:
                    st.put(c + dx, yy - 1, c + dz, BES_ROOD if r < 3 else BES_GEEL)
    for y in range(22, 25):
        st.put(c, y, c, SPRUK_BLAD)
    # gouden ster met gloei
    st.put(c, 25, c, GOLD)
    st.put(c, 26, c, ZEEGLOW)
    st.put(c, 27, c, EINDSTAAF)
    # pakjes onder de boom (de echt werkende zijn relic-props)
    for (x, z, s) in ((4, 6, ROOD), (11, 5, GROEN), (5, 11, WIT), (10, 10, ROOD)):
        st.box(x, 1, z, x + 1, 2, z + 1, s)
        st.put(x, 3, z, GOLD)
    return st, ("Kerstboom 16x29x16: berkenvoet, zeven besneeuwde sparlagen, rode en gouden "
                "bessen, gouden ster met sea-lantern-gloei en eindstaf, vier pakjes voet. Werkende "
                "prullen komen eromheen met /relics give gift_box, present (xmas-blokken: "
                "/xmas give present_red) en string_lights in de takken.")


# ============================================================ deel 3: de rode hut
def huis_rood():
    """De rode wolken-hut uit het oude demo-bestand: 12x12x12, warm licht, rook."""
    st = Set(12, 13, 13)
    vloer(st)
    top = huis(st, 1, 1, 1, 10, 10, ROOD, WIT)
    # een spar en een lantaarn naast de voordeur, plus een sneeuwman-voet (sneeuwman is een relic)
    spar(st, 10, 1, 3, 2, 2)
    lantaarnpaal(st, 1, 1, 4, 3)
    st.box(4, 1, 11, 6, 2, 12, SNEEUW)
    return st, ("Rode wolvilten-hut 12x12x12 met witte gevelrand, zadeldak met sneeuwgoot, twee "
                "verlichte ramen, deuropening, schoorsteen met eindstaf-rook en een spar. Zet er "
                "naast: /relics give snowman (voet is al gelegd), hearth BINNEN de hut op de vloer "
                "achter de deur, string_lights over de gevel, cookie_plate naast de voordeur.")


# ============================================================ deel 4: huisjes-rij
def huisjes():
    """Drie hutten in een rij, elk een andere kleur, met een haag en een pad naar het plein."""
    st = Set(34, 14, 14)
    vloer(st)
    for (i, (x0, muur)) in enumerate(((1, ROOD), (12, GROEN), (23, WIT))):
        top = huis(st, x0, 1, 3, 10, 9, muur, None, hoog=3, nok=5, schoorsteen=(i != 1))
        # schuurtje achter elk huis: stapel hout en een ton
        st.box(x0 + 1, 1, 12, x0 + 2, 2, 13, SPRUK_STAM)
        st.put(x0 + 3, 1, 12, KIEKEL_MOSS)
    # voor elk huis een kasseienstoeppad naar de straat + een heg ertussen
    for x in range(6, 29):
        st.put(x, 1, 1, PAD)
    for x in range(st.w):
        if x % 11 in (5, 6, 7):
            for z in range(1, 3):
                st.put(x, 1, z, PAD)
    for x in range(10, 24):
        st.put(x, 1, 2, MOS)
    for (x, z) in ((4, 1), (14, 1), (25, 1)):
        lantaarnpaal(st, x, 1, z, 3)
    return st, ("Rij van drie hutten (rood, groen, wit) met zadeldaken, heg, kasseienstoeppaden en "
                "drie lantaarnpalen. Per huis: /relics give hearth (binnen), string_lights op de "
                "gevel, kadomatsu naast de deur en een gift_box op de stoep. De middelste heeft "
                "bewust geen schoorsteen - daar staat de slee (relic: sleight/seat).")


# ============================================================ deel 5: marktstraat
def marktstraat():
    """Marktkramen met gestreepte luifels, kratten, een verkoperstoep en lichtslingers."""
    st = Set(30, 11, 16)
    vloer(st, PAD)
    for x in range(st.w):
        for z in range(st.l):
            if 3 <= z <= 12:
                st.put(x, 0, z, KIEKEL if (x // 2 + z) % 2 else ANDESIET)
    for k in range(4):
        x0 = 1 + k * 7
        # tafel + zuilen + gestreepte luifel
        st.box(x0, 1, 4, x0 + 5, 1, 6, SPRUK)
        for z in (4, 6):
            st.put(x0, 2, z, SPRUK_STAM)
            st.put(x0 + 5, 2, z, SPRUK_STAM)
            st.put(x0, 3, z, SPRUK_STAM)
            st.put(x0 + 5, 3, z, SPRUK_STAM)
        for i in range(6):
            st.box(x0 + i, 4, 3, x0 + i, 4, 7, ROOD if i % 2 else WIT)
        st.box(x0 + 1, 2, 5, x0 + 4, 2, 5, DIEP)
        # waar de koopwaar staat: een plank met kommen
        st.put(x0 + 2, 2, 5, KOMPOST)
        st.put(x0 + 4, 2, 5, "minecraft:water")
        # lichtslinger tussen de kramen
        lichtslinger(st, x0, 6, 4, "oost", 5)
    for x in range(2, 29, 6):
        lantaarnpaal(st, x, 1, 13, 3)
    return st, ("Markstraat 30x16 met vier kramen (gestreepte luifel, plank, ton, water-emmer) en "
                "twee rijen lichtslingers. De kramen zijn kaal: /relics give market_stall op elke "
                "plek, plus snow_globe, music_box en cookie_plate tussen de kraampjes; treats "
                "(hete chocalade, gluhwein, stollen) via de Gilding-shop / Kitchen-recepten.")


# ============================================================ deel 6: lantaarntoren + laantje
def lantaarntoren():
    """De slanke lantaarn/toren uit het oude demo-bestand (5x18x11), met een laantje eronder."""
    st = Set(7, 19, 13)
    vloer(st)
    for z in range(1, 12):
        st.put(3, 1, z, PAD)
    # toren: mangrove-achtige stammen (berk/spruit) met kwarts-ringen
    for y in range(2, 15):
        st.put(2, y, 5, BERK_STAM)
        st.put(4, y, 5, BERK_STAM)
        st.put(2, y, 7, BERK_STAM)
        st.put(4, y, 7, BERK_STAM)
        if y % 4 == 0:
            st.box(2, y, 5, 4, y, 7, KWARTS)
            st.put(3, y, 6, GLOEILAMP)
        else:
            st.put(3, y, 5, IJZERSTAAF)
            st.put(3, y, 7, IJZERSTAAF)
            st.put(2, y, 6, IJZERSTAAF)
            st.put(4, y, 6, IJZERSTAAF)
    # kapitein: platform + lantaarn-kroon
    st.box(1, 15, 4, 5, 15, 8, KWARTS)
    st.box(1, 16, 4, 5, 16, 8, ZWART)
    st.put(3, 17, 6, ZEEGLOW)
    st.put(3, 18, 6, EINDSTAAF)
    for (x, z) in ((1, 4), (5, 4), (1, 8), (5, 8)):
        st.put(x, 16, z, SPRUK_TRAP + "[facing=south,half=top]")
    return st, ("Lantaarntoren 7x19x13 (het oude 5x18x11 deel, iets breder zodat er een laantje "
                "doorheen loopt): berkenzuilen met kwartsvringen, ijzerstaven, een zee-glow-kroon "
                "en sneeuwtrap. Zet string_lights van de toren naar de huizen en snow_lantern "
                "langs het laantje (/relics give snow_lantern).")


DELEN = [
    ("kerstdorp-01-plein", plein),
    ("kerstdorp-02-kerstboom", kerstboom),
    ("kerstdorp-03-huis-rood", huis_rood),
    ("kerstdorp-04-huisjes-rij", huisjes),
    ("kerstdorp-05-marktstraat", marktstraat),
    ("kerstdorp-06-lantaarntoren", lantaarntoren),
]


def main():
    out = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "deploy/kerstdorp"
    overzicht = "--overzicht" in sys.argv
    os.makedirs(out, exist_ok=True)
    print("kerstdorp-delen -> %s" % out)
    for naam, bouw in DELEN:
        st, beschrijving = bouw()
        if overzicht:
            print("\n%s  %d x %d x %d" % (naam, st.w, st.h, st.l))
            print("   %d blokstoffen: %s" % (len(st.palette), ", ".join(
                s.replace("minecraft:", "") for s in st.palette[1:9]) + " ..."))
            continue
        pad = os.path.join(out, naam + ".schem")
        st.save(pad, naam, beschrijving)
        controleer(pad)
        print("  ok %-26s %2dx%2dx%2d  %3d blokstoffen  %6d B" % (
            naam, st.w, st.h, st.l, len(st.palette), os.path.getsize(pad)))
    if overzicht:
        print("\n(geen bestanden geschreven - weglaten van --overzicht bouwt ze in deploy/kerstdorp/)")


if __name__ == "__main__":
    main()
