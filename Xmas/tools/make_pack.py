#!/usr/bin/env python3
"""Xmas-pack: de kerst-bouw blok ken van Xmas Town, als props (barrier + ItemDisplay).

Usage (uit Xmas/):  python -I tools/make_pack.py resourcepack
  -> resourcepack/Xmas-pack/  +  resourcepack/Xmas-pack.zip   (scripts/merge-packs.py voegt 't samen)

Waarom zo: de vorige versie van dit script tekende 8 blokken in een eigen atlas en schreef een
item-model met `parent: xmas:block/<id>`, maar het pack werd nooit mee-gebouwd (staat niet in
scripts/merge-packs.py), er waren geen `assets/xmas/items/<id>.json` definities en de plugin
gaf alleen PAPER zonder item_model. In 26.2 is een item zonder items/-definitie een MAGENTA
blok. Dit script gebruikt daarom de gedeelde propkit-engine (propmodels.Pack): die schrijft model
EN items-definitie, de palette-atlas, en pack_format 64 - exact zoals Kitchen/Sakagura/Zoo het doen.
Elk model hieronder moet een DecorDef.id hebben in XmasDecor.java, en andersom.

Schaal: een blok-model vult 0..16 = één blok, dus DecorDef.scale blijft 1,0 - de props zijn
bouwstenen, geen beelden. Geen enkel element valt buiten -16..32 (de 26.2-grens).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "propkit"))
from propmodels import Pack  # noqa: E402

# Kleuren van onze eigen kerst-atlas. De namen staan los van de plugin-ids: een kleur mag
# meerdere blokken sieren (sparretak en lantaarn-groen zijn hetzelfde groen).
PALETTE = {
    "snow": (246, 249, 252), "snow_shade": (216, 226, 236), "snow_deep": (188, 202, 216),
    "ice": (168, 216, 240), "ice_light": (206, 238, 252), "ice_dark": (122, 178, 212),
    "pine_bark": (92, 66, 46), "pine_wood": (176, 132, 88), "pine_light": (206, 166, 118),
    "pine_ring": (142, 104, 66), "pine_green": (58, 106, 68), "pine_dark": (38, 76, 48),
    "present_red": (186, 44, 44), "present_red_d": (140, 28, 28), "present_green": (52, 132, 76),
    "present_green_d": (34, 96, 56), "ribbon_gold": (226, 186, 70), "ribbon_white": (244, 240, 232),
    "candy_red": (206, 62, 66), "candy_white": (248, 246, 242),
    "ginger": (166, 108, 56), "ginger_dark": (126, 80, 40), "icing": (246, 238, 224),
    "roof_red": (150, 60, 48), "roof_red_d": (112, 42, 34), "roof_grey": (96, 98, 104),
    "window_warm": (250, 214, 130), "window_frame": (122, 88, 56), "glass_pane": (198, 226, 240),
    "metal": (150, 154, 160), "brass": (206, 168, 72),
}

p = Pack("xmas", sys.argv[1] if len(sys.argv) > 1 else "resourcepack", PALETTE,
         "Iekcorbar Xmas Town - kerst bouwstenen")
box = p.box


# ------------------------------------------------------------------ bouwstenen (0..16 = 1 blok)

def cube(side, top=None):
    """Een volle bouwsteen: 16x16x16, met andere kleur bovenop als dat mooier is."""
    return [box(0, 0, 0, 16, 16, 16, side, top=top or side)]


def snow_block():
    # sneeuwblok met een iets donkerder rand, zodat een muur van deze blokken niet één wit vlak wordt
    e = [box(0, 0, 0, 16, 16, 16, "snow_shade", top="snow")]
    e += [box(1, 0, 1, 15, 15, 15, "snow")]                           # binnenwerk, rand blijft zichtbaar
    return e


def ice_block():
    e = [box(0, 0, 0, 16, 16, 16, "ice", top="ice_light")]
    e += [box(2, 0, 2, 6, 16, 6, "ice_light"), box(9, 0, 8, 13, 16, 12, "ice_dark")]
    return e


def pine_log():
    # stam met schors rondom en jaarringen op het kopse eind
    e = [box(1, 0, 1, 15, 16, 15, "pine_bark")]
    e += [box(2, 0, 2, 14, 16, 14, "pine_wood")]
    e += [box(1, 15, 1, 15, 16, 15, "pine_ring"), box(4, 15, 4, 12, 16, 12, "pine_wood"),
          box(6, 15, 6, 10, 16, 10, "pine_ring")]
    e += [box(1, 3, 0, 15, 4, 1, "pine_bark"), box(1, 9, 15, 15, 10, 16, "pine_bark")]  # bast-ribben
    return e


def pine_planks():
    e = [box(0, 0, 0, 16, 16, 16, "pine_wood", top="pine_light")]
    for y in (4, 8, 12):
        e.append(box(0, y, 0, 16, y + 0.7, 16, "pine_bark"))          # planknaden
    e += [box(7, 0, 0, 7.7, 4, 16, "pine_bark"), box(3, 4, 0, 3.7, 8, 16, "pine_bark"),
          box(11, 8, 0, 11.7, 12, 16, "pine_bark"), box(6, 12, 0, 6.7, 16, 16, "pine_bark")]
    return e


def present(col, dark):
    # pakje als bouwsteen: deksel-lijn plus een kruis lint. De strik blijft BINNEN de 16 px,
    # anders knipt hij in het blok erboven zodra iemand een stapel pakjes maakt.
    e = [box(0, 0, 0, 16, 16, 16, dark, top=dark)]
    e += [box(1, 1, 1, 15, 15, 15, col)]                              # binnenwerk, rand blijft zichtbaar
    e += [box(0, 12, 0, 16, 13, 16, dark)]                            # deksel-lijn
    e += [box(6.5, 0, 0, 9.5, 16, 0.8, "ribbon_gold"), box(6.5, 0, 15.2, 9.5, 16, 16, "ribbon_gold"),
          box(0, 0, 6.5, 0.8, 16, 9.5, "ribbon_gold"), box(15.2, 0, 6.5, 16, 16, 9.5, "ribbon_gold")]
    e += [box(5.5, 14.6, 6, 10.5, 16, 10, "ribbon_gold")]             # platte strik op het deksel
    return e


def candy_block():
    # schuine reep: afwisselende banen wit/rood, als zuil-blok (5 banen, allemaal binnen 16)
    e = [box(0, 0, 0, 16, 16, 16, "candy_white", top="candy_white")]
    for i in range(5):
        y = i * 3.2
        e.append(box(0, y, 0, 16, y + 1.8, 16, "candy_red"))
    return e


def gingerbread_block():
    e = [box(0, 0, 0, 16, 16, 16, "ginger", top="ginger_dark")]
    e += [box(0, 15, 0, 16, 16, 16, "icing")]                         # geschilde icing-rand
    for x in (2, 7, 12):
        e.append(box(x, 14.4, 1, x + 2.4, 15.2, 15, "icing"))         # icing-drip
    e += [box(3, 10, 0, 5, 12, 0.6, "candy_red"), box(11, 6, 0, 13, 8, 0.6, "ice_light"),
          box(6, 3, 0, 8, 5, 0.6, "ribbon_gold")]                     # snoepgoed erin
    return e


def roof_tile():
    # daksteen: een ribbel bovenop, zodat een dak van deze bloken leesbaar is
    e = [box(0, 0, 0, 16, 11, 16, "roof_red_d")]
    e += [box(0, 11, 0, 16, 13, 16, "roof_red")]
    for z in (0, 4, 8, 12):
        e.append(box(0, 13, z, 16, 15, z + 3, "roof_red"))
    e += [box(0, 15, 0, 16, 16, 16, "snow")]                          # sneeuwlaag op het dak
    return e


def lit_window():
    # raamsteen: kozijn rond een warm licht - de snerpendste sfeermaker van een kerstdorp
    e = [box(0, 0, 0, 16, 16, 16, "pine_wood", top="pine_light")]
    e += [box(2, 2, 0, 14, 14, 16, "window_warm")]
    e += [box(7.5, 2, 0, 8.5, 14, 16, "window_frame"), box(2, 7.5, 0, 14, 8.5, 16, "window_frame")]
    e += [box(1, 1, 0, 15, 2, 16, "window_frame"), box(1, 14, 0, 15, 15, 16, "window_frame"),
          box(1, 1, 0, 2, 15, 16, "window_frame"), box(14, 1, 0, 15, 15, 16, "window_frame")]
    e += [box(2, 15.0, 3, 14, 16.0, 6, "snow")]                       # sneeuwkraal op het kozijn (binnen 16)
    return e


def lamppost():
    # geen bouwsteen maar een lantaarnpaal voor de generator-schets: 2 blokken hoog, scale 1
    e = [box(6, 0, 6, 10, 1, 10, "metal"), box(7, 1, 7, 9, 16, 9, "metal"),
         box(5, 16, 5, 11, 17, 11, "metal"), box(5.5, 17, 5.5, 10.5, 22, 10.5, "brass"),
         box(6, 17.5, 6, 10, 21.5, 10, "window_warm"), box(5, 22, 5, 11, 23, 11, "metal"),
         box(4, 23, 4, 12, 24, 12, "snow")]
    return e


MODELS = {
    "snow_block": snow_block, "ice_block": ice_block, "pine_log": pine_log,
    "pine_planks": pine_planks, "present_red": lambda: present("present_red", "present_red_d"),
    "present_green": lambda: present("present_green", "present_green_d"),
    "candy_block": candy_block, "gingerbread_block": gingerbread_block,
    "roof_tile": roof_tile, "lit_window": lit_window, "village_lamp": lamppost,
}

for name, fn in MODELS.items():
    p.model(name, fn())

p.finish()
print("xmas-blokken:", ", ".join(sorted(MODELS)))
