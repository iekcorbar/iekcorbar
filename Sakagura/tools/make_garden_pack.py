"""Garden decor resource pack (namespace garden): planters, flower beds, fences, hedge, trellis, pergola, bench,
bird bath, fountain, lamp, gnome, scarecrow, wheelbarrow, stepping stones, sundial, mailbox, rain barrel, compost
bin, tool shed, sprinkler, pot shelf. Own low-poly designs; front = north (z=0).
Usage (from Sakagura/): python -I tools/make_garden_pack.py resourcepack  -> resourcepack/Garden-pack.zip
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "propkit"))
from propmodels import Pack  # noqa: E402

PALETTE = {
    "wood": (170, 124, 74), "wood_dark": (104, 70, 42), "wood_light": (212, 172, 118), "white": (246, 244, 238), "stone": (128, 126, 120),
    "stone_light": (170, 168, 160), "stone_dark": (90, 88, 84), "moss": (88, 124, 58), "soil": (92, 64, 44), "leaf": (86, 150, 60),
    "leaf_dark": (50, 104, 46), "red": (200, 52, 48), "yellow": (240, 204, 56), "blue": (70, 110, 210), "pink": (236, 130, 170),
    "purple": (150, 90, 190), "water": (64, 130, 210), "water_light": (150, 200, 240), "iron": (190, 194, 200), "iron_dark": (96, 100, 108),
    "black": (34, 34, 38), "lantern": (255, 214, 120), "straw": (222, 196, 102), "straw_dark": (176, 148, 66), "orange": (230, 130, 40),
    "skin": (232, 190, 150), "brass": (206, 168, 72), "green": (60, 118, 70), "clay": (150, 84, 56), "brown": (110, 72, 44),
}
FLOWER = {"": ["red", "yellow", "blue"], "_red": ["red", "orange", "pink"], "_yellow": ["yellow", "orange", "white"],
          "_blue": ["blue", "purple", "white"], "_pink": ["pink", "white", "purple"]}


def build(out):
    pk = Pack("garden", out, PALETTE, "Garden decor (Sakagura)")
    b = pk.box

    def flowers(y, cols, x1, x2, z1, z2, step=3):
        es, i = [], 0
        for x in range(x1, x2, step):
            for z in range(z1, z2, step):
                c = cols[i % len(cols)]
                i += 1
                es.append(b(x + 0.5, y, z + 0.5, x + 1.5, y + 1.5, z + 1.5, "leaf_dark"))
                es.append(b(x, y + 1.5, z, x + 2, y + 3, z + 2, c))
        return es

    def side(d, x1, y1, z1, x2, y2, z2, c):
        """Box given for direction E (+x, 8..16); turned to N(0)/E(1)/S(2)/W(3) around the block centre."""
        if d == 1:
            return b(x1, y1, z1, x2, y2, z2, c)
        if d == 3:
            return b(16 - x2, y1, z1, 16 - x1, y2, z2, c)
        if d == 2:
            return b(16 - z2, y1, x1, 16 - z1, y2, x2, c)
        return b(z1, y1, 16 - x2, z2, y2, 16 - x1, c)

    for suf, cols in FLOWER.items():
        pk.model("planter_box" + suf, [b(1, 0, 3, 15, 10, 13, "wood"), b(1, 0, 3, 15, 1.5, 13, "wood_dark"), b(2, 9, 4, 14, 10.5, 12, "soil")]
                 + flowers(10.5, cols, 2, 14, 4, 12, 3))
        pk.model("flower_bed" + suf, [b(0, 0, 0, 16, 3, 16, "soil", top="leaf_dark"), b(0, 0, 0, 16, 3, 1, "stone"), b(0, 0, 15, 16, 3, 16, "stone"),
                                      b(0, 0, 0, 1, 3, 16, "stone"), b(15, 0, 0, 16, 3, 16, "stone")] + flowers(3, cols, 2, 14, 2, 14, 3))

    # picket fence (2 tall)
    post = [b(6.5, 0, 6.5, 9.5, 20, 9.5, "white"), b(6, 20, 6, 10, 22, 10, "white")]

    def fence_rail(d):
        es = [side(d, 8, 5, 7.3, 16, 8, 8.7, "white"), side(d, 8, 13, 7.3, 16, 16, 8.7, "white")]
        for x in (9.5, 12.5, 15.0):
            es.append(side(d, x - 0.8, 0, 7.6, x + 0.8, 18, 8.4, "white"))
        return es
    pk.fence("garden_fence", post, fence_rail)

    # hedge (2 tall)
    hpost = [b(3, 0, 3, 13, 28, 13, "leaf"), b(4, 28, 4, 12, 31, 12, "leaf_dark"), b(3, 10, 3, 13, 12, 13, "leaf_dark")]

    def hedge_arm(d):
        return [side(d, 13, 0, 3, 16, 28, 13, "leaf"), side(d, 13, 28, 4, 16, 31, 12, "leaf_dark"), side(d, 13, 10, 3, 16, 12, 13, "leaf_dark")]
    pk.fence("hedge", hpost, hedge_arm)

    # trellis (2 tall, lattice with vines)
    tpost = [b(6.5, 0, 6.5, 9.5, 30, 9.5, "wood"), b(6, 30, 6, 10, 32, 10, "wood_light")]

    def trellis_arm(d):
        es = [side(d, 9.5, 4, 7.4, 16, 6, 8.6, "wood"), side(d, 9.5, 14, 7.4, 16, 16, 8.6, "wood"), side(d, 9.5, 24, 7.4, 16, 26, 8.6, "wood")]
        for x in (11, 14):
            es.append(side(d, x, 2, 7.4, x + 1.2, 28, 8.6, "wood_light"))
        for x, y in ((10, 6), (12.5, 12), (11, 19), (14, 23), (13, 8)):
            es.append(side(d, x, y, 7.0, x + 2, y + 2, 9.0, "leaf"))
        return es
    pk.fence("trellis", tpost, trellis_arm)

    # pergola (3 tall, walk-through)
    pk.model("pergola", [b(0, 0, 0, 3, 46, 3, "wood"), b(13, 0, 0, 16, 46, 3, "wood"), b(0, 0, 13, 3, 46, 16, "wood"), b(13, 0, 13, 16, 46, 16, "wood"),
                         b(-1, 44, 0, 17, 47, 3, "wood_light"), b(-1, 44, 13, 17, 47, 16, "wood_light")]
                 + [b(x, 46, -1, x + 2, 48, 17, "wood_dark") for x in (0, 4.5, 9, 14)]
                 + [b(0.2, 30, 0.2, 2.8, 44, 2.8, "leaf"), b(13.2, 24, 0.2, 15.8, 44, 2.8, "leaf"), b(0.2, 20, 13.2, 2.8, 44, 15.8, "leaf"),
                    b(13.2, 32, 13.2, 15.8, 44, 15.8, "leaf"), b(3, 46, 3, 13, 48, 13, "leaf_dark"), b(5, 40, 5, 6, 46, 6, "leaf"), b(10, 38, 9, 11, 46, 10, "leaf")])

    # bench
    pk.model("garden_bench", [b(1, 0, 4, 3, 7, 6, "iron_dark"), b(13, 0, 4, 15, 7, 6, "iron_dark"), b(1, 0, 10, 3, 7, 12, "iron_dark"), b(13, 0, 10, 15, 7, 12, "iron_dark"),
                              b(0, 7, 3, 16, 8.5, 13, "wood"), b(0, 8.5, 3, 16, 9, 5, "wood_dark"),
                              b(0, 10, 11, 16, 12, 12.5, "wood"), b(0, 14, 11, 16, 16, 12.5, "wood"), b(0, 18, 11, 16, 20, 12.5, "wood"),
                              b(1, 8.5, 11, 3, 20, 12.5, "iron_dark"), b(13, 8.5, 11, 15, 20, 12.5, "iron_dark")])

    # bird bath & fountain
    pk.model("bird_bath", [b(4, 0, 4, 12, 3, 12, "stone_dark"), b(6.5, 3, 6.5, 9.5, 11, 9.5, "stone"), b(2, 11, 2, 14, 13, 14, "stone_light"),
                           b(1.5, 13, 1.5, 14.5, 14.5, 2.5, "stone_light"), b(1.5, 13, 13.5, 14.5, 14.5, 14.5, "stone_light"),
                           b(1.5, 13, 2.5, 2.5, 14.5, 13.5, "stone_light"), b(13.5, 13, 2.5, 14.5, 14.5, 13.5, "stone_light"), b(2.5, 13, 2.5, 13.5, 13.8, 13.5, "water_light"),
                           b(7, 14, 7, 9, 16, 9, "red"), b(7, 16, 6.5, 9, 18, 9.5, "red"), b(8, 16.5, 9.5, 8.5, 17, 11, "orange")])
    pk.model("fountain", [b(0, 0, 0, 16, 5, 16, "stone_dark"), b(1, 5, 1, 15, 8, 15, "stone"), b(2, 8, 2, 14, 8.8, 14, "water"),
                          b(1, 8, 1, 15, 9.5, 2, "stone_light"), b(1, 8, 14, 15, 9.5, 15, "stone_light"), b(1, 8, 2, 2, 9.5, 14, "stone_light"), b(14, 8, 2, 15, 9.5, 14, "stone_light"),
                          b(6, 8, 6, 10, 20, 10, "stone"), b(3.5, 20, 3.5, 12.5, 22, 12.5, "stone_light"), b(4, 22, 4, 12, 22.7, 12, "water_light"),
                          b(4, 22, 4, 12, 23.5, 5, "stone_light"), b(4, 22, 11, 12, 23.5, 12, "stone_light"),
                          b(7, 22, 7, 9, 28, 9, "stone"), b(6, 28, 6, 10, 29, 10, "stone_light"), b(7.5, 29, 7.5, 8.5, 31, 8.5, "water_light")])

    # lamp (2 tall)
    pk.model("garden_lamp", [b(4.5, 0, 4.5, 11.5, 3, 11.5, "iron_dark"), b(7, 3, 7, 9, 24, 9, "iron_dark"), b(6, 24, 6, 10, 25, 10, "iron"),
                             b(5, 25, 5, 11, 30, 11, "lantern"), b(4.5, 24.5, 4.5, 11.5, 25.5, 11.5, "black"), b(4.5, 30, 4.5, 11.5, 31.5, 11.5, "black"),
                             b(7, 31.5, 7, 9, 32, 9, "black"), b(5, 25, 5, 6, 30, 6, "black"), b(10, 25, 5, 11, 30, 6, "black"), b(5, 25, 10, 6, 30, 11, "black"), b(10, 25, 10, 11, 30, 11, "black")])

    # gnome
    pk.model("garden_gnome", [b(5, 0, 5, 7.5, 3, 8, "brown"), b(8.5, 0, 5, 11, 3, 8, "brown"), b(5, 3, 4.5, 11, 9, 9, "blue"), b(4, 5, 5, 5, 8, 8, "skin"), b(11, 5, 5, 12, 8, 8, "skin"),
                              b(5.5, 9, 4.8, 10.5, 13, 8.7, "skin"), b(5.5, 8.5, 4.5, 10.5, 11, 5.5, "white"), b(5.2, 13, 4.5, 10.8, 14.5, 9, "red"),
                              b(6, 14.5, 5.3, 10, 16.5, 8.2, "red"), b(7, 16.5, 6, 9, 18.5, 7.5, "red"), b(7.5, 18.5, 6.4, 8.5, 19.5, 7.1, "red"),
                              b(7.4, 10.5, 4.2, 8.6, 11.7, 5, "orange")])

    # scarecrow (2 tall)
    pk.model("scarecrow", [b(7, 0, 7, 9, 24, 9, "wood_dark"), b(0, 18, 7.2, 16, 20, 8.8, "wood_dark"), b(5, 10, 5.5, 11, 22, 10.5, "blue"),
                           b(1, 17.5, 6.5, 3, 20.5, 9.5, "straw"), b(13, 17.5, 6.5, 15, 20.5, 9.5, "straw"), b(5.5, 6, 6, 10.5, 10, 10, "brown"),
                           b(5, 24, 5, 11, 30, 11, "orange"), b(6, 26, 4.8, 7, 27.2, 5.2, "black"), b(9, 26, 4.8, 10, 27.2, 5.2, "black"), b(7, 24.5, 4.8, 9, 25.3, 5.2, "black"),
                           b(3.5, 30, 3.5, 12.5, 31.5, 12.5, "straw_dark"), b(5.5, 31.5, 5.5, 10.5, 36, 10.5, "straw_dark"), b(5.5, 31.5, 5.5, 10.5, 32.5, 10.5, "red"),
                           b(5, 22, 10, 11, 23, 10.5, "straw"), b(9, 4, 5.5, 10.5, 6, 6.2, "straw")])

    # wheelbarrow
    pk.model("wheelbarrow", [b(3, 5, 2, 13, 6, 12, "iron_dark"), b(3, 6, 2, 4, 11, 12, "green"), b(12, 6, 2, 13, 11, 12, "green"), b(4, 6, 2, 12, 11, 3, "green"), b(4, 6, 11, 12, 11, 12, "green"),
                             b(4, 6, 3, 12, 9.5, 11, "soil"), b(5, 9.5, 4, 8, 11, 7, "leaf"), b(9, 9.5, 7, 11, 11.5, 9, "leaf"),
                             b(6.5, 3, 0.5, 9.5, 6, 3.5, "iron_dark"), b(7.2, 0, -0.5, 8.8, 3.5, 4.5, "black"),
                             b(2.5, 5, 11, 4, 6.5, 16, "wood"), b(12, 5, 11, 13.5, 6.5, 16, "wood"), b(2.5, 0, 12, 4, 5, 13.5, "wood"), b(12, 0, 12, 13.5, 5, 13.5, "wood")])

    # stepping stones
    pk.model("stepping_stones", [b(1, 0, 1, 7, 1.5, 7, "stone"), b(9, 0, 2, 15, 1.5, 7.5, "stone_light"), b(2, 0, 9, 8, 1.5, 15, "stone_light"), b(10, 0, 9.5, 14.5, 1.5, 14.5, "stone"),
                                 b(2, 1.5, 2, 4, 1.8, 4, "moss"), b(11, 1.5, 10.5, 13, 1.8, 12.5, "moss")])

    # sundial
    pk.model("sundial", [b(3, 0, 3, 13, 3, 13, "stone_dark"), b(5.5, 3, 5.5, 10.5, 11, 10.5, "stone"), b(2, 11, 2, 14, 13, 14, "stone_light"),
                         b(3, 13, 3, 13, 13.3, 13, "brass"), b(4.5, 13.3, 4.5, 11.5, 13.5, 11.5, "stone_light"),
                         b(7.6, 13.3, 3.5, 8.4, 17, 8, "brass", rot=("x", -22.5, (8, 13.3, 8))), b(7.8, 13.4, 3, 8.2, 13.6, 13, "black")])

    # mailbox
    pk.model("mailbox", [b(7, 0, 7, 9, 20, 9, "wood_dark"), b(4, 20, 5, 12, 22, 11, "wood"), b(3, 22, 4.5, 13, 28, 11.5, "red"), b(4, 28, 5.5, 12, 29.5, 10.5, "red"),
                         b(5, 23, 4.2, 11, 26, 4.6, "black"), b(12.5, 23, 6, 14, 29, 7, "yellow"), b(12.5, 28, 6, 15.5, 29.5, 7, "yellow")])

    # rain barrel
    pk.model("rain_barrel", [b(2, 0, 2, 14, 15, 14, "wood"), b(1.5, 1.5, 1.5, 14.5, 3, 14.5, "iron_dark"), b(1.5, 11, 1.5, 14.5, 12.5, 14.5, "iron_dark"),
                             b(2.5, 15, 2.5, 13.5, 16, 13.5, "wood_dark"), b(3, 15, 3, 13, 16.2, 13, "water"), b(7, 3, 0.8, 9, 5, 2, "iron"), b(7.4, 2, 0, 8.6, 3.2, 1, "iron")])

    # compost bin
    slats = (0.5, 4, 7.5, 11)
    pk.model("compost_bin", [b(1, 0, 1, 3, 13, 3, "wood_dark"), b(13, 0, 1, 15, 13, 3, "wood_dark"), b(1, 0, 13, 3, 13, 15, "wood_dark"), b(13, 0, 13, 15, 13, 15, "wood_dark")]
                 + [b(3, y, 1.5, 13, y + 2.5, 2.5, "wood") for y in slats] + [b(3, y, 13.5, 13, y + 2.5, 14.5, "wood") for y in slats]
                 + [b(1.5, y, 3, 2.5, y + 2.5, 13, "wood") for y in slats] + [b(13.5, y, 3, 14.5, y + 2.5, 13, "wood") for y in slats]
                 + [b(3, 1, 3, 13, 12, 13, "soil"), b(5, 12, 5, 8, 13, 8, "straw_dark"), b(9, 12, 8, 12, 13.4, 11, "leaf_dark"), b(7, 12, 10, 8.5, 13, 11.5, "orange")])

    # tool shed (2 tall)
    pk.model("tool_shed", [b(1, 0, 1, 15, 22, 15, "wood"), b(1, 0, 1, 15, 2, 15, "wood_dark"), b(0, 22, 0, 16, 24, 16, "wood_dark"),
                           b(-1, 24, -1, 17, 26, 17, "red"), b(1, 26, 1, 15, 28, 15, "red"), b(3, 28, 3, 13, 30, 13, "red"), b(5, 30, 5, 11, 31.5, 11, "wood_dark"),
                           b(4, 0.5, 0.6, 12, 17, 1.4, "wood_light"), b(4, 0.5, 0.3, 5, 17, 0.7, "wood_dark"), b(11, 0.5, 0.3, 12, 17, 0.7, "wood_dark"),
                           b(4, 16, 0.3, 12, 17, 0.7, "wood_dark"), b(10, 8, 0.2, 11.2, 9.2, 0.5, "brass"), b(1.2, 12, 0.3, 3.2, 15, 1.0, "blue"),
                           b(13.5, 4, 0.3, 14.5, 18, 1.0, "iron"), b(13, 18, 0.3, 15, 19, 1.0, "iron")])

    # sprinkler
    pk.model("sprinkler", [b(5, 0, 5, 11, 2, 11, "iron_dark"), b(7, 2, 7, 9, 9, 9, "iron"), b(5.5, 9, 5.5, 10.5, 11, 10.5, "brass"), b(2, 11, 7.2, 14, 12, 8.8, "brass"),
                           b(2, 11, 7.2, 3, 13, 8.8, "water_light"), b(13, 11, 7.2, 14, 13, 8.8, "water_light"), b(7, 11, 7, 9, 12.5, 9, "black")])

    # pot shelf (wall shelf with potted plants)
    pk.model("pot_shelf", [b(0, 6, 8, 16, 8, 16, "wood"), b(1, 0, 10, 3, 6, 16, "wood_dark"), b(13, 0, 10, 15, 6, 16, "wood_dark"),
                           b(1, 8, 10, 6, 12, 15, "clay"), b(1.5, 12, 10.5, 5.5, 15, 14.5, "leaf"), b(2.5, 15, 11.5, 4.5, 17, 13.5, "pink"),
                           b(8, 8, 10, 12, 11, 14, "clay"), b(8.5, 11, 10.5, 11.5, 14, 13.5, "leaf_dark"), b(9.5, 14, 11.5, 10.5, 16, 12.5, "yellow"),
                           b(13, 8, 11, 15, 10, 13, "clay"), b(13.2, 10, 11.2, 14.8, 11.5, 12.8, "leaf")])
    pk.finish()


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "resourcepack")
