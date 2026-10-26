"""Kitchen decor resource pack (namespace kitchendecor): counters, sink, stove top, tables, chairs, stools, menu
board, order bell, noren curtain, paper lantern, wine rack, shelves, ice box, kotatsu, serving cart, sake barrel and sign.
Own low-poly designs; front = north (z=0). Wall props (menu board, sign) hug the back (z=16).
Usage (from Kitchen/): python -I tools/make_kitchendecor_pack.py resourcepack -> resourcepack/Kitchendecor-pack.zip
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "propkit"))
from propmodels import Pack  # noqa: E402

PALETTE = {
    "wood": (170, 124, 74), "wood_dark": (104, 70, 42), "wood_light": (212, 172, 118), "white": (246, 244, 238), "stone": (128, 126, 120),
    "stone_light": (186, 184, 176), "stone_dark": (80, 78, 76), "iron": (190, 194, 200), "iron_dark": (96, 100, 108), "black": (34, 34, 38),
    "red": (190, 48, 44), "red_dark": (130, 30, 30), "blue": (46, 80, 150), "blue_dark": (30, 52, 100), "gold": (226, 186, 70), "brass": (206, 168, 72),
    "copper": (196, 112, 70), "paper": (240, 232, 210), "lantern": (255, 214, 120), "glass": (180, 220, 230), "wine": (110, 30, 50), "green": (60, 118, 70),
    "straw": (222, 196, 102), "straw_dark": (176, 148, 66), "chalk": (44, 60, 52), "orange": (230, 130, 40), "flame": (255, 150, 40), "cloth": (210, 60, 70),
}


def build(out):
    pk = Pack("kitchendecor", out, PALETTE, "Kitchen decor (Kitchen)")
    b = pk.box

    def side(d, x1, y1, z1, x2, y2, z2, c, top=None):
        """Box given for direction E (+x); turned to N(0)/E(1)/S(2)/W(3) around the block centre."""
        if d == 1:
            return b(x1, y1, z1, x2, y2, z2, c, top=top)
        if d == 3:
            return b(16 - x2, y1, z1, 16 - x1, y2, z2, c, top=top)
        if d == 2:
            return b(16 - z2, y1, x1, 16 - z1, y2, x2, c, top=top)
        return b(z1, y1, 16 - x2, z2, y2, 16 - x1, c, top=top)

    # ------------------------------------------------------------ counters (join sideways)
    def counter(name, top_extra=None):
        body = [b(1, 0, 1, 15, 14, 15, "wood_dark"), b(0, 14, 0, 16, 16, 16, "stone_light"), b(2, 1, 0.6, 14, 13, 1, "wood")]

        def arm(d):
            return [side(d, 15, 0, 1, 16, 14, 15, "wood_dark")]
        for mask in range(16):
            es = list(body) + (top_extra(mask) if top_extra else [])
            for d in range(4):
                if mask & (1 << d):
                    es += arm(d)
            pk.model(f"{name}_{mask}", es, item=(mask == 0))

    counter("kitchen_counter")
    # sink: basin + tap, same joining body
    def sink_extra(mask):
        return [b(3, 15.5, 4, 13, 16.2, 12, "iron_dark"), b(4, 16, 5, 12, 16.4, 11, "water_light" if False else "glass"),
                b(7.2, 16, 12, 8.8, 22, 13.6, "iron"), b(7.2, 21, 8, 8.8, 22.6, 13.6, "iron"), b(7.4, 19, 7.4, 8.6, 21, 8.6, "iron")]
    counter("counter_sink", sink_extra)

    def stove_extra(mask):
        return [b(1, 16, 1, 15, 17, 15, "iron_dark"), b(3, 17, 3, 7, 17.8, 7, "black"), b(9, 17, 3, 13, 17.8, 7, "black"), b(3, 17, 9, 7, 17.8, 13, "black"),
                b(9, 17, 9, 13, 17.8, 13, "black"), b(5, 3, 0.2, 7, 7, 0.8, "brass"), b(9, 3, 0.2, 11, 7, 0.8, "brass"), b(4, 8, 0.4, 12, 9.5, 0.8, "iron")]
    counter("stove_top", stove_extra)

    # ------------------------------------------------------------ dining table (joins)
    def leg_post():
        return [b(1, 0, 1, 4, 14, 4, "wood_dark"), b(12, 0, 1, 15, 14, 4, "wood_dark"), b(1, 0, 12, 4, 14, 15, "wood_dark"), b(12, 0, 12, 15, 14, 15, "wood_dark")]
    for mask in range(16):
        es = leg_post() + [b(0.5, 14, 0.5, 15.5, 16, 15.5, "wood"), b(0, 15.5, 0, 16, 16, 16, "wood_light")]
        if mask & 1:
            es.append(b(0, 14, 0, 16, 16, 0.5, "wood"))
        for d in range(4):
            if mask & (1 << d):
                es.append(side(d, 15.5, 14, 0.5, 16, 16, 15.5, "wood"))
                es.append(side(d, 15.5, 15.5, 0, 16, 16, 16, "wood_light"))
        if not mask & 1 and False:
            pass
        pk.model(f"dining_table_{mask}", es, item=(mask == 0))

    # ------------------------------------------------------------ seats
    pk.model("chair", [b(3, 0, 3, 5, 9, 5, "wood_dark"), b(11, 0, 3, 13, 9, 5, "wood_dark"), b(3, 0, 11, 5, 9, 13, "wood_dark"), b(11, 0, 11, 13, 9, 13, "wood_dark"),
                       b(2.5, 9, 2.5, 13.5, 10.5, 13.5, "red"), b(2.5, 8, 2.5, 13.5, 9, 13.5, "wood"),
                       b(3, 10.5, 12, 5, 26, 14, "wood_dark"), b(11, 10.5, 12, 13, 26, 14, "wood_dark"), b(3, 18, 12.2, 13, 22, 13.8, "wood"), b(3, 23, 12.2, 13, 26.5, 13.8, "wood")])
    pk.model("stool", [b(4, 0, 4, 6, 9, 6, "wood_dark"), b(10, 0, 4, 12, 9, 6, "wood_dark"), b(4, 0, 10, 6, 9, 12, "wood_dark"), b(10, 0, 10, 12, 9, 12, "wood_dark"),
                       b(3, 9, 3, 13, 11, 13, "wood"), b(3.5, 11, 3.5, 12.5, 11.6, 12.5, "wood_light"), b(4, 4, 4, 12, 5, 6, "wood_dark")])
    pk.model("bar_stool", [b(7, 0, 7, 9, 19, 9, "iron_dark"), b(4, 0, 4, 12, 1.5, 12, "iron"), b(5, 8, 5, 11, 9, 11, "iron"), b(3, 19, 3, 13, 21, 13, "red"), b(3, 18, 3, 13, 19, 13, "iron")])

    # ------------------------------------------------------------ wall props (back at z = 16)
    pk.model("menu_board", [b(0, 2, 14, 16, 16, 16, "wood_dark"), b(1, 3, 13.4, 15, 15, 14, "chalk"), b(2, 12, 13.2, 9, 12.8, 13.5, "white"), b(2, 10, 13.2, 12, 10.6, 13.5, "white"),
                            b(2, 8, 13.2, 10, 8.6, 13.5, "white"), b(2, 6, 13.2, 11, 6.6, 13.5, "white"), b(2, 4, 13.2, 8, 4.6, 13.5, "white"), b(12, 12.5, 13.2, 14, 13.1, 13.5, "gold")])
    pk.model("restaurant_sign", [b(1, 15, 9, 1.8, 16, 9.8, "iron_dark"), b(14.2, 15, 9, 15, 16, 9.8, "iron_dark"), b(0, 15.5, 8.6, 16, 16, 9.4, "iron_dark"),
                                 b(1.2, 4, 8.8, 1.8, 15.5, 9.4, "iron"), b(14.2, 4, 8.8, 14.8, 15.5, 9.4, "iron"),
                                 b(0, 3, 8.4, 16, 12, 10.2, "wood_dark"), b(0.8, 3.8, 8.0, 15.2, 11.2, 8.4, "red"), b(2, 5.5, 7.8, 5, 9.5, 8.0, "gold"), b(6.5, 5.5, 7.8, 9.5, 9.5, 8.0, "gold"),
                                 b(11, 5.5, 7.8, 14, 9.5, 8.0, "gold")])

    # ------------------------------------------------------------ small props
    pk.model("order_bell", [b(4, 0, 4, 12, 2, 12, "wood_dark"), b(5, 2, 5, 11, 4, 11, "brass"), b(6, 4, 6, 10, 6, 10, "brass"), b(7, 6, 7, 9, 7, 9, "gold"), b(7.5, 7, 7.5, 8.5, 8.2, 8.5, "iron")])
    pk.model("noren_curtain", [b(1, 14, 7, 15, 16, 9, "wood_dark"), b(0, 15, 7.4, 1, 16, 8.6, "iron"), b(15, 15, 7.4, 16, 16, 8.6, "iron"),
                               b(1.5, 3, 7.4, 7.5, 14, 8.6, "blue"), b(8.5, 3, 7.4, 14.5, 14, 8.6, "blue"), b(3.2, 6, 7.2, 5.8, 11, 7.4, "white"), b(10.2, 6, 7.2, 12.8, 11, 7.4, "white"),
                               b(1.5, 3, 7.4, 7.5, 4.2, 8.6, "blue_dark"), b(8.5, 3, 7.4, 14.5, 4.2, 8.6, "blue_dark")])
    pk.model("paper_lantern", [b(6.5, 0, 6.5, 9.5, 1.5, 9.5, "black"), b(4.5, 1.5, 4.5, 11.5, 3, 11.5, "paper"), b(3, 3, 3, 13, 11, 13, "lantern"), b(4.5, 11, 4.5, 11.5, 12.5, 11.5, "paper"),
                               b(6.5, 12.5, 6.5, 9.5, 14, 9.5, "black"), b(3, 5, 3, 13, 5.5, 13, "red"), b(3, 8.5, 3, 13, 9, 13, "red"), b(7, 14, 7, 9, 18, 9, "iron_dark")])

    # ------------------------------------------------------------ storage
    pk.model("wine_rack", [b(0, 0, 2, 16, 2, 14, "wood_dark"), b(0, 30, 2, 16, 32, 14, "wood_dark"), b(0, 0, 2, 2, 32, 14, "wood"), b(14, 0, 2, 16, 32, 14, "wood"),
                           b(2, 10, 2, 14, 11, 14, "wood"), b(2, 20, 2, 14, 21, 14, "wood")]
                 + [b(3 + 3.5 * i, y, 3, 5 + 3.5 * i, y + 3.5, 13, c) for y, cols in ((2.5, ("wine", "glass", "wine")), (11.5, ("glass", "wine", "green")), (21.5, ("wine", "green", "wine")))
                    for i, c in enumerate(cols)])
    pk.model("shelf_unit", [b(0, 0, 2, 2, 32, 14, "wood_dark"), b(14, 0, 2, 2 + 12 + 0, 32, 14, "wood_dark") if False else b(14, 0, 2, 16, 32, 14, "wood_dark"),
                            b(2, 0, 13, 14, 32, 14, "wood")]
                 + [b(2, y, 2, 14, y + 1.5, 14, "wood_light") for y in (0, 10, 20, 30.5)]
                 + [b(3, 1.5, 4, 7, 7, 8, "straw"), b(8.5, 1.5, 5, 13, 5, 10, "white"), b(3, 11.5, 5, 6, 17, 8, "glass"), b(7, 11.5, 4, 10, 15, 7, "orange"), b(11, 11.5, 6, 13.5, 14, 9, "green"),
                    b(3, 21.5, 5, 7, 24, 9, "copper"), b(8.5, 21.5, 4, 12, 28, 8, "glass"), b(4, 22.5, 6, 6, 26, 8, "red")])
    fridge_body = [b(1, 0, 1, 15, 4, 15, "iron_dark"), b(1, 4, 1, 15, 30, 15, "iron"), b(1, 30, 1, 15, 32, 15, "iron_dark"), b(0.5, 11, 0.5, 15.5, 12, 15.5, "copper"),
                   b(0.5, 24, 0.5, 15.5, 25, 15.5, "copper")]
    pk.model("fridge", fridge_body + [b(11.5, 14, 0.2, 13, 22, 1, "brass"), b(11.5, 5.5, 0.2, 13, 9, 1, "brass"), b(3, 5.5, 0.2, 8, 6.5, 0.6, "iron_dark"), b(3, 26, 0.2, 9, 28, 0.6, "gold"),
                                      b(2, 14, 0.2, 10, 22, 0.5, "iron_dark")])
    pk.model("fridge_open", [b(1, 0, 1, 15, 4, 15, "iron_dark"), b(1, 4, 5, 15, 30, 15, "iron"), b(1, 30, 1, 15, 32, 15, "iron_dark"), b(1, 4, 1, 3, 30, 5, "iron"), b(13, 4, 1, 15, 30, 5, "iron"),
                             b(3, 10, 5, 13, 11, 14, "glass"), b(3, 20, 5, 13, 21, 14, "glass"), b(4, 4, 7, 7, 10, 11, "white"), b(8, 11, 6, 12, 16, 10, "wine"),
                             b(14.5, 4, -9, 16, 30, 1, "iron"), b(14.8, 12, -9.4, 15.6, 20, -8.4, "brass")])

    # ------------------------------------------------------------ larger props
    pk.model("kotatsu", [b(1, 0, 1, 15, 3, 15, "wood_dark"), b(0, 3, 0, 16, 7, 16, "cloth"), b(0, 3, 0, 16, 4, 16, "red_dark"), b(-1, 7, -1, 17, 9, 17, "wood_light"),
                         b(0, 3, -1.4, 16, 4.2, 0, "red_dark") if False else b(2, 9, 2, 14, 9.4, 14, "wood"), b(11, 9.4, 9, 14, 12, 12, "orange"), b(5, 9.4, 4, 9, 10.5, 8, "paper")])
    pk.model("serving_cart", [b(1, 0, 3, 3, 3, 5, "black"), b(13, 0, 3, 15, 3, 5, "black"), b(1, 0, 11, 3, 3, 13, "black"), b(13, 0, 11, 15, 3, 13, "black"),
                              b(1, 3, 3, 15, 5, 13, "wood_dark"), b(1, 3, 3, 2, 17, 4, "iron_dark"), b(14, 3, 3, 15, 17, 4, "iron_dark"), b(1, 9, 3, 15, 10, 13, "wood"),
                              b(1, 14, 3, 15, 15, 13, "wood_light"), b(1, 3, 12, 2, 20, 13, "iron_dark"), b(14, 3, 12, 15, 20, 13, "iron_dark"), b(0, 19, 11, 16, 21, 13, "iron_dark"),
                              b(4, 15, 5, 8, 17, 9, "white"), b(8.5, 15, 6, 10, 18, 8, "white"), b(9, 15, 8, 12, 16, 11, "blue"), b(3, 10, 4, 6, 12, 7, "wine")])
    pk.model("sake_barrel", [b(2, 0, 2, 14, 15, 14, "wood"), b(1.5, 2, 1.5, 14.5, 4, 14.5, "straw"), b(1.5, 11, 1.5, 14.5, 13, 14.5, "straw"), b(1.5, 6.5, 1.5, 14.5, 8.5, 14.5, "straw_dark"),
                             b(2.5, 15, 2.5, 13.5, 16, 13.5, "wood_dark"), b(3, 15, 3, 13, 15.4, 13, "wood_light"), b(6, 2, 0.8, 10, 11, 1.4, "white"), b(6.8, 4, 0.7, 9.2, 9, 0.9, "red"),
                             b(7, 14, 1.5, 9, 15, 3, "wood_dark")])
    pk.finish()


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "resourcepack")
