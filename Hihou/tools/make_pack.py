"""Hihou resource pack (namespace hihou): the five chest types (closed + open) and their keys.
All our own low-poly designs on the shared palette texture; every model carries a "particle"
texture so break/hit particles show the right colour.
Usage (from Wildlife/../Hihou/): python -I tools/make_pack.py resourcepack  ->  resourcepack/Hihou-pack/ + Hihou-pack.zip
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "propkit"))
from propmodels import Pack  # noqa: E402

PALETTE = {
    "oak": (162, 130, 78), "oak_dark": (110, 84, 46), "darkoak": (76, 50, 30), "darkoak_dark": (48, 31, 19),
    "birch": (196, 176, 120), "cherry": (232, 162, 166), "cherry_dark": (168, 104, 110), "bamboo": (150, 168, 78),
    "gold": (226, 186, 70), "brass": (206, 168, 72), "iron": (190, 194, 200), "iron_dark": (96, 100, 108),
    "black": (30, 28, 34), "diamond": (110, 224, 214), "emerald": (64, 190, 110), "ruby": (214, 44, 60),
    "amethyst": (160, 94, 214), "netherite": (66, 58, 60), "paper": (240, 232, 210), "lantern": (255, 214, 120),
    "snow": (240, 244, 250), "soul": (90, 190, 190),
}


def chest(pk, body, band, lock, open_):
    b = pk.box
    es = [b(2, 0, 3, 14, 1, 13, "oak_dark"),                        # base plank
          b(2, 1, 3, 14, 8, 13, body),                                # body
          b(2, 4, 2.9, 14, 5, 3.1, band), b(2, 4, 12.9, 14, 5, 13.1, band),   # side bands front/back
          b(6, 5, 2.8, 10, 8, 3.2, lock)]                             # lock box on the front
    if not open_:
        es += [b(2, 8, 3, 14, 12, 13, body),                          # closed lid
               b(2, 8, 2.9, 14, 9, 3.1, band), b(2, 8, 12.9, 14, 9, 13.1, band),
               b(6, 6.5, 2.7, 9, 7.5, 3.3, "iron_dark"),              # keyhole plate
               b(7, 6.7, 2.6, 8, 7.3, 3.4, "gold")]
    else:
        es += [b(2, 8, 3, 14, 12, 13, body, rot=("x", -65, (2, 12, 8))),          # lid swung up/back
               b(3, 2, 4, 13, 5, 12, "black"),                                    # hollow interior
               b(4, 2.5, 5, 8, 4.5, 9, band), b(7, 3, 6, 12, 6, 11, "gold"),      # loot pile
               b(9, 2.5, 5, 12, 4.5, 8, band), b(6, 4.6, 7, 8, 6.0, 9, "diamond"),
               b(7, 7, 2.8, 9, 8, 3.2, "iron_dark"), b(7.6, 6.4, 2.7, 8.4, 7.6, 3.3, lock)]  # open lock box
    return es


def key(pk, body, teeth):
    b = pk.box
    return [
        b(2, 12, 7.6, 6, 13, 8.4, body), b(2, 14, 7.6, 6, 15, 8.4, body),          # bow (ring top/bottom)
        b(2, 12, 7.6, 3, 15, 8.4, body), b(5, 12, 7.6, 6, 15, 8.4, body),          # bow (ring sides)
        b(5, 13, 7.8, 14, 14, 8.2, body),                                          # shaft
        b(11, 11, 7.8, 12, 13, 8.2, teeth), b(13, 11, 7.8, 14, 12, 8.2, teeth),   # teeth
        b(3, 13, 7.4, 5, 14, 8.6, "gold"),                                         # rivet on the bow
    ]


def build(out):
    pk = Pack("hihou", out, PALETTE, "Hihou chests and keys")
    types = {
        "treasure": ("darkoak", "gold", "brass"),
        "event": ("cherry", "ruby", "gold"),
        "vote": ("birch", "brass", "iron_dark"),
        "lucky": ("amethyst", "gold", "diamond"),
        "victory": ("bamboo", "diamond", "gold"),
    }
    for t, (body, band, lock) in types.items():
        pk.model(t + "_chest", chest(pk, body, band, lock, False))
        pk.model(t + "_chest_open", chest(pk, body, band, lock, True), item=False)
    for t, teeth in (("treasure", "gold"), ("event", "iron_dark"), ("vote", "iron"), ("lucky", "diamond"), ("victory", "emerald")):
        pk.model(t + "_key", key(pk, types[t][1], teeth))
    pk.finish()


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "resourcepack")
