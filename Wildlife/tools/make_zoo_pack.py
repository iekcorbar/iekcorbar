"""Zoo resource pack (namespace zoo): kiosk, gates, fences, enclosure props, guest props, ticket.
All our own low-poly designs. Front of every model = north (z=0); the plugin turns them.
Usage (from Wildlife/): python -I tools/make_zoo_pack.py resourcepack   -> resourcepack/Zoo-pack/ + Zoo-pack.zip
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "propkit"))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from propmodels import Pack  # noqa: E402
import make_animals as MA  # noqa: E402  (the animal builders: statues are the same cubes, frozen in their rest pose)

PALETTE = {
    "iron": (190, 194, 200), "iron_dark": (96, 100, 108), "black": (34, 34, 38), "wood": (170, 124, 74), "wood_dark": (104, 70, 42),
    "wood_light": (212, 172, 118), "stone": (128, 126, 120), "stone_dark": (90, 88, 84), "moss": (88, 124, 58), "gold": (226, 186, 70),
    "red": (190, 48, 44), "white": (246, 244, 238), "green": (60, 118, 70), "green_dark": (36, 78, 46), "straw": (222, 196, 102),
    "straw_dark": (176, 148, 66), "bamboo": (190, 182, 100), "bamboo_dark": (132, 126, 62), "water": (64, 120, 200), "paper": (240, 232, 210),
    "leaf": (86, 150, 60), "lantern": (255, 214, 120), "brass": (206, 168, 72), "blue": (46, 80, 150), "yellow": (240, 200, 50),
    "clay": (150, 84, 56), "bark": (96, 70, 48), "bark_light": (190, 150, 100), "ticket": (240, 200, 110),
}


# animal colours for the statues: the animal palette, prefixed so it never clashes with the prop colours above
ALL = dict(PALETTE)
for _n, _c in MA.PALETTE.items():
    ALL["a_" + _n] = _c

# spawn egg colours per species: (body, spots) from the animal palette
EGG = {"deer": ("deer", "antler"), "bear": ("fur_brown", "fur_light"), "boar": ("boar", "tusk"), "duck": ("duck_green", "bill"),
       "butterfly": ("monarch", "black"), "firefly": ("glow_yellow", "black"), "snail": ("shell_brown", "shell_tan"),
       "hedgehog": ("spine", "spine_tip"), "capybara": ("capy", "yuzu"), "tortoise": ("tortoise_shell", "scute"),
       "lizard": ("lizard", "lizard_spot"), "crab": ("crab", "crab_belly"), "songbird": ("jay", "white"),
       "dragonfly": ("dragon_teal", "dragon_red"), "elephant": ("elephant", "tusk"), "zebra": ("zebra_white", "zebra_black"),
       "tanuki": ("tanuki", "fur_cream"), "crane": ("white", "eye_red"), "koi": ("koi_orange", "white"),
       "macaque": ("macaque", "pink"),
       "kangaroo": ("fur_brown", "fur_dark"), "ostrich": ("black", "white"), "crocodile": ("lizard", "lizard_spot"),
       "koala": ("elephant", "fur_cream"), "mole": ("elephant", "pink"), "rhino": ("elephant", "tusk"),
       "penguin": ("black", "web"), "orca": ("black", "white"), "piranha": ("scales", "firefly_red"),
       "macaw": ("cardinal", "jay")}


def statues(pk, csv_path):
    """One item model per animal look (stat_<id>[_rare|_baby], songbird colours): the animal's cubes in rest pose, head to
    the north like every prop. Vanilla item models only reach -16..32 px, so big animals are drawn smaller and the
    prop is scaled back up (last CSV column group): id,display scale,hitbox width,hitbox height."""
    rows = []

    def one(name, build, kw, recolor):
        m = MA.Model(name, recolor=recolor)
        build(m, **kw)
        m.chibi()
        m.scaled()
        els = m.elements
        pts = []
        for e in els:
            a, b = list(e["from"]), list(e["to"])
            if m.key() == "crab":      # drawn facing -X: turn a quarter so it faces north
                a, b = [-a[2], a[1], a[0]], [-b[2], b[1], b[0]]
            pts.append(([min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)], e["name"]))
        lo = [min(p[0][i] for p in pts) for i in range(3)]
        hi = [max(p[1][i] for p in pts) for i in range(3)]
        dx, dy, dz = hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]
        up = 2.0 if max(dx, dy, dz) < 10 else 1.0           # the tiny ones (snail, firefly) are shown at double size
        g = min(1.0, 46.0 / max(dx, dz, 1e-6), 30.0 / max(dy, 1e-6))
        cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2
        elems = []
        for a, b, col in pts:
            elems.append(pk.box(round((a[0] - cx) * g + 8, 3), round((a[1] - lo[1]) * g, 3), round((a[2] - cz) * g + 8, 3),
                                round((b[0] - cx) * g + 8, 3), round((b[1] - lo[1]) * g, 3), round((b[2] - cz) * g + 8, 3), "a_" + col))
        mid = "stat_" + name[3:]
        pk.model(mid, elems)
        w = max(0.45, min(3.0, max(dx, dz) * up / 16 * 0.8))
        h = max(0.45, min(3.5, dy * up / 16))
        rows.append(f"{mid},{up / g:.4f},{w:.2f},{h:.2f}")

    for sid, build, kw, rare, rare_kw in MA.SPECIES:
        one("wl_" + sid, build, kw, None)
        one("wl_" + sid + "_rare", build, {**kw, **rare_kw}, rare)
        one("wl_" + sid + "_baby", build, kw, None)
    for name, build, kw in MA.EXTRA:
        one("wl_" + name, build, kw, None)
    os.makedirs(os.path.dirname(csv_path), exist_ok=True)
    open(csv_path, "w", encoding="utf-8").write("# id,display scale,hitbox width,hitbox height (made by tools/make_zoo_pack.py)\n" + "\n".join(rows) + "\n")


def eggs(pk):
    """Spawn egg item models: an upright egg in the animal's colours with a few spots."""
    b = pk.box
    for sid, (body, spot) in EGG.items():
        c, sp = "a_" + body, "a_" + spot
        es = [b(6, 0, 6, 10, 1, 10, c + "_d"), b(5, 1, 5, 11, 3, 11, c), b(4, 3, 4, 12, 8, 12, c, top=c + "_l"), b(5, 8, 5, 11, 10, 11, c + "_l"),
              b(6, 10, 6, 10, 11.5, 10, c + "_l"),
              b(6, 4, 3.95, 8.2, 6, 4.05, sp), b(9, 6, 3.95, 10.5, 7.5, 4.05, sp), b(3.95, 5, 8, 4.05, 7, 10, sp),
              b(11.95, 4, 6, 12.05, 6, 8, sp), b(7, 6.5, 11.95, 9, 8.5, 12.05, sp), b(5, 3.5, 11.95, 6.5, 5, 12.05, sp)]
        pk.model("egg_" + sid, es)


def build(out):
    pk = Pack("zoo", out, ALL, "Zoo props (Wildlife)")
    b = pk.box

    # ------------------------------------------------------------ molehill (dug by the mole, see Molehills.java)
    pk.model("molehill", [
        b(1, 0, 2, 15, 3, 14, "clay"), b(3, 3, 4, 13, 6, 12, "clay"), b(5, 6, 6, 11, 8, 10, "bark_light"),
        b(2, 0.4, 3, 4, 2.2, 5, "bark"), b(12, 0.3, 10, 14, 2, 12, "bark"),                   # thrown-up lobes
        b(6.4, 7.9, 7.4, 9.6, 8.3, 10.6, "black"),                                            # the hole
    ])

    # ------------------------------------------------------------ ticket kiosk (2 tall)
    k = [
        b(1, 0, 1, 15, 13, 15, "wood_dark", top="wood_dark"),             # cabinet
        b(0, 13, 0, 16, 15, 16, "wood_light"),                              # counter top
        b(1, 15, 2, 3, 29, 4, "wood"), b(13, 15, 2, 15, 29, 4, "wood"),   # front posts
        b(1, 15, 12, 3, 29, 14, "wood"), b(13, 15, 12, 15, 29, 14, "wood"),
        b(3, 15, 12, 13, 27, 14, "wood_dark"),                              # back wall
        b(1, 15, 4, 3, 27, 12, "wood_dark"), b(13, 15, 4, 15, 27, 12, "wood_dark"),
        b(0, 28, 0, 16, 31, 16, "red"), b(0, 31, 0, 16, 32, 16, "white"), b(0, 28, 0, 16, 29, 2, "white"),  # striped roof
        b(3, 22, 1.5, 13, 27, 2.5, "gold"),                                 # name sign
        b(4, 16, 3, 7, 18, 6, "ticket"), b(9, 16, 3, 12, 17, 6, "paper"),   # tickets on the counter
        b(11, 15, 8, 13, 17, 10, "brass"),                                  # bell
    ]
    pk.model("ticket_kiosk", k)

    # ------------------------------------------------------------ turnstiles
    def turnstile(top, open_):
        es = [b(2, 0, 2, 14, 2, 14, "iron_dark"), b(6, 2, 6, 10, 14, 10, "iron"), b(5, 14, 5, 11, 16, 11, top)]
        if open_:
            es += [b(1, 10, 7.4, 7, 12, 8.6, "iron", rot=("y", 45, (8, 11, 8))), b(9, 10, 7.4, 15, 12, 8.6, "iron", rot=("y", 45, (8, 11, 8))),
                   b(7.4, 10, 1, 8.6, 12, 7, "iron", rot=("y", 45, (8, 11, 8))), b(7.4, 10, 9, 8.6, 12, 15, "iron", rot=("y", 45, (8, 11, 8)))]
        else:
            es += [b(1, 10, 7.4, 7, 12, 8.6, "iron"), b(9, 10, 7.4, 15, 12, 8.6, "iron"),
                   b(7.4, 10, 1, 8.6, 12, 7, "iron"), b(7.4, 10, 9, 8.6, 12, 15, "iron")]
        return es
    pk.model("turnstile", turnstile("red", False)); pk.model("turnstile_open", turnstile("red", True), item=False)
    pk.model("exit_turnstile", turnstile("green", False)); pk.model("exit_turnstile_open", turnstile("green", True), item=False)

    # ------------------------------------------------------------ gates (2 tall; leaf across x, swings to the north side when open)
    def gate(col, stripe, open_):
        es = [b(0, 0, 6, 3, 32, 10, col), b(13, 0, 6, 16, 32, 10, col), b(0, 32, 5.5, 3, 33, 10.5, stripe), b(13, 32, 5.5, 16, 33, 10.5, stripe)]
        if not open_:
            es += [b(3, 3, 7.4, 13, 5, 8.6, "iron"), b(3, 25, 7.4, 13, 27, 8.6, "iron"), b(3, 5, 7.4, 4.2, 25, 8.6, "iron"), b(11.8, 5, 7.4, 13, 25, 8.6, "iron")]
            for x in (5.5, 7.5, 9.5):
                es.append(b(x, 5, 7.6, x + 1, 25, 8.4, "iron_dark"))
            es.append(b(3, 14, 7.4, 13, 15.2, 8.6, stripe))
            es.append(b(7, 27, 7, 9, 31, 9, "lantern"))
        else:
            es += [b(3, 3, 3, 4.2, 5, 6, "iron"), b(3, 25, 3, 4.2, 27, 6, "iron"), b(3, 5, 0.5, 4.2, 25, 6, "iron")]
            es += [b(3, 3, 0.5, 4.2, 5, 3, "iron"), b(3, 25, 0.5, 4.2, 27, 3, "iron")]
            for z in (1.5, 3, 4.5):
                es.append(b(3.2, 5, z, 3.9, 25, z + 0.7, "iron_dark"))
            es.append(b(7, 27, 7, 9, 31, 9, "lantern"))
        return es
    pk.model("zoo_gate", gate("iron_dark", "gold", False)); pk.model("zoo_gate_open", gate("iron_dark", "gold", True), item=False)
    pk.model("staff_gate", gate("black", "yellow", False)); pk.model("staff_gate_open", gate("black", "yellow", True), item=False)

    # ------------------------------------------------------------ entrance arch (3 blocks wide, x -16..32)
    arch = [
        b(-14, 0, 5, -6, 4, 11, "stone_dark"), b(22, 0, 5, 30, 4, 11, "stone_dark"),
        b(-12, 4, 6, -8, 28, 10, "wood_dark"), b(24, 4, 6, 28, 28, 10, "wood_dark"),
        b(-14, 28, 5, 30, 32, 11, "wood"), b(-16, 32, 4, 32, 34, 12, "red"),
        b(-8, 21, 5.4, 24, 28, 6.4, "gold"),
        b(-12, 34, 6, -8, 40, 10, "gold"), b(24, 34, 6, 28, 40, 10, "gold"),
        b(4, 34, 6.5, 12, 38, 9.5, "brass"),
    ]
    pk.model("entrance_arch", arch)

    # ------------------------------------------------------------ fences (2 tall, connecting)
    def rails(d, col, bar, thick):
        # direction d: N=0 (z 0..), E=1 (x ..16), S=2, W=3; rails from the post edge to the block edge
        def rect(x1, y1, z1, x2, y2, z2, c):
            return {0: b(z1, y1, 0, z2, y2, 6, c) if False else None}
        out = []
        for y in (6, 22):
            parts = {0: (7.2, y, 0, 8.8, y + 2, 6), 1: (10, y, 7.2, 16, y + 2, 8.8), 2: (7.2, y, 10, 8.8, y + 2, 16), 3: (0, y, 7.2, 6, y + 2, 8.8)}
            out.append(b(*parts[d], col))
        for t in (2, 4):
            frac = {0: (7.4, 4, 6 - t * 1.4, 8.6, 28, 7.4 - t * 1.4 + 1.2), 1: (10 + t * 1.4 - 1.2, 4, 7.4, 10 + t * 1.4, 28, 8.6),
                    2: (7.4, 4, 10 + t * 1.4 - 1.2, 8.6, 28, 10 + t * 1.4), 3: (6 - t * 1.4, 4, 7.4, 7.4 - t * 1.4 + 1.2, 28, 8.6)}
            out.append(b(*frac[d], bar))
        return out
    pk.fence("zoo_fence", [b(6.5, 0, 6.5, 9.5, 30, 9.5, "iron_dark"), b(6, 30, 6, 10, 32, 10, "iron")],
             lambda d: rails(d, "iron", "iron_dark", 1))
    pk.fence("bamboo_fence", [b(7, 0, 7, 9, 30, 9, "bamboo_dark"), b(6.6, 30, 6.6, 9.4, 32, 9.4, "bamboo")],
             lambda d: [e for y in (6, 14, 22) for e in [b(*{0: (7.3, y, 0, 8.7, y + 1.8, 7), 1: (9, y, 7.3, 16, y + 1.8, 8.7), 2: (7.3, y, 9, 8.7, y + 1.8, 16), 3: (0, y, 7.3, 7, y + 1.8, 8.7)}[d], "bamboo")]])

    # ------------------------------------------------------------ rocks, log, troughs
    pk.model("rock_small", [b(3, 0, 3, 13, 6, 12, "stone"), b(5, 6, 4, 11, 9, 10, "stone_dark"), b(4, 5, 3.5, 8, 7, 7, "moss", rot=("y", 22.5, (8, 6, 8)))])
    pk.model("rock_small_b", [b(2, 0, 4, 12, 5, 13, "stone_dark"), b(6, 5, 6, 13, 8, 12, "stone"), b(3, 4, 5, 7, 6, 9, "moss")])
    pk.model("rock_large", [b(1, 0, 1, 15, 10, 15, "stone"), b(3, 10, 3, 14, 20, 13, "stone_dark"), b(5, 20, 5, 12, 27, 11, "stone"),
                            b(2, 8, 2, 8, 11, 9, "moss"), b(6, 19, 4, 11, 21, 9, "moss")])
    pk.model("log_horizontal", [b(1, 0, 4, 15, 8, 12, "bark"), b(0.5, 0, 3.5, 1.5, 8.5, 12.5, "bark_light"), b(14.5, 0, 3.5, 15.5, 8.5, 12.5, "bark_light"),
                                b(5, 8, 6, 8, 11, 9, "bark"), b(10, 8, 5, 12, 10, 7, "moss")])
    pk.model("water_trough", [b(1, 0, 3, 15, 8, 13, "wood_dark"), b(2, 6, 4, 14, 7.2, 12, "water"), b(0, 7, 2, 16, 8.5, 3, "wood"), b(0, 7, 13, 16, 8.5, 14, "wood"),
                              b(1, 0, 3, 3, 2, 13, "wood")])
    pk.model("feeding_trough", [b(1, 0, 3, 15, 8, 13, "wood_dark"), b(2, 5, 4, 14, 7.5, 12, "straw"), b(0, 7, 2, 16, 8.5, 3, "wood"), b(0, 7, 13, 16, 8.5, 14, "wood"),
                                b(5, 7, 6, 8, 8.5, 9, "red"), b(9, 7, 7, 12, 8.4, 10, "leaf")])
    pk.model("hay_feeder", [b(1, 0, 3, 3, 14, 5, "wood"), b(13, 0, 3, 15, 14, 5, "wood"), b(1, 0, 11, 3, 14, 13, "wood"), b(13, 0, 11, 15, 14, 13, "wood"),
                            b(3, 4, 4, 13, 13, 12, "straw"), b(3, 13, 4, 13, 14, 12, "straw_dark"), b(1, 14, 3, 15, 15.5, 13, "wood_dark")])
    pk.model("bird_perch", [b(7, 0, 7, 9, 28, 9, "bark"), b(4, 0, 4, 12, 2, 12, "bark_light"), b(2, 20, 7, 14, 22, 9, "bark_light", rot=("z", 22.5, (8, 21, 8))),
                            b(1, 22, 6, 5, 25, 10, "leaf"), b(11, 26, 6, 15, 29, 10, "leaf"), b(6, 28, 6, 10, 31, 10, "leaf")])
    pk.model("animal_hut", [b(2, 0, 3, 14, 9, 14, "wood"), b(6, 0, 2.5, 10, 7, 3.2, "black"), b(1, 9, 1.5, 15, 11, 9, "red", rot=("x", -22.5, (8, 10, 8))),
                            b(1, 9, 7.5, 15, 11, 15, "red", rot=("x", 22.5, (8, 10, 8))), b(7, 12, 7, 9, 14, 9, "red")])

    # ------------------------------------------------------------ guest props
    pk.model("info_board", [b(2, 0, 7, 4, 12, 9, "wood_dark"), b(12, 0, 7, 14, 12, 9, "wood_dark"), b(1, 7, 6, 15, 15, 8, "wood"),
                            b(2, 8, 5.5, 14, 14, 6.5, "paper"), b(2, 13, 5.4, 14, 14.5, 6.4, "green"), b(4, 9, 5.4, 12, 10, 6.4, "iron_dark"), b(4, 11, 5.4, 10, 12, 6.4, "iron_dark")])
    pk.model("map_board", [b(1, 0, 7, 3, 20, 9, "wood_dark"), b(13, 0, 7, 15, 20, 9, "wood_dark"), b(0, 6, 6, 16, 24, 8, "wood"),
                           b(1, 7, 5.5, 15, 23, 6.5, "paper"), b(3, 15, 5.4, 8, 19, 6.4, "green"), b(9, 9, 5.4, 13, 13, 6.4, "water"), b(5, 9, 5.4, 8, 12, 6.4, "clay"),
                           b(11, 17, 5.4, 12, 18, 6.4, "red")])
    pk.model("direction_sign", [b(7, 0, 7, 9, 14, 9, "wood_dark"), b(1, 9, 6.4, 12, 13, 7.6, "wood_light"), b(12, 9, 6.4, 14, 13, 7.6, "wood_light", rot=("y", 45, (13, 11, 7))),
                                b(4, 3, 6.4, 15, 7, 7.6, "wood"), b(2, 3, 6.4, 4, 7, 7.6, "wood", rot=("y", 45, (3, 5, 7))), b(3, 10, 6.3, 10, 11, 6.5, "paper"), b(6, 4, 6.3, 12, 5, 6.5, "paper")])
    pk.model("zoo_bench", [b(1, 0, 4, 3, 7, 6, "iron_dark"), b(13, 0, 4, 15, 7, 6, "iron_dark"), b(1, 0, 10, 3, 7, 12, "iron_dark"), b(13, 0, 10, 15, 7, 12, "iron_dark"),
                           b(0, 7, 3, 16, 9, 13, "wood"), b(0, 9, 11.5, 16, 17, 13, "wood_light"), b(1, 9, 11, 3, 15, 13, "iron_dark"), b(13, 9, 11, 15, 15, 13, "iron_dark")])
    pk.model("trash_bin", [b(4, 0, 4, 12, 11, 12, "green"), b(3.5, 11, 3.5, 12.5, 13, 12.5, "green_dark"), b(5, 13, 5, 11, 14, 11, "iron_dark"),
                           b(5.5, 5, 3.7, 10.5, 9, 4, "white"), b(4, 0, 4, 12, 1.5, 12, "iron_dark")])
    pk.model("lamp_post", [b(4.5, 0, 4.5, 11.5, 4, 11.5, "iron_dark"), b(7, 4, 7, 9, 38, 9, "iron_dark"), b(6, 38, 6, 10, 39, 10, "iron"),
                           b(5, 39, 5, 11, 46, 11, "lantern"), b(4.5, 38.5, 4.5, 11.5, 39.5, 11.5, "black"), b(4.5, 46, 4.5, 11.5, 47.5, 11.5, "black"),
                           b(7, 47.5, 7, 9, 50, 9, "black"), b(5, 39, 5, 6, 46, 6, "black"), b(10, 39, 5, 11, 46, 6, "black"), b(5, 39, 10, 6, 46, 11, "black"), b(10, 39, 10, 11, 46, 11, "black")])
    pk.model("souvenir_stall", [
        b(1, 0, 3, 15, 12, 15, "wood_dark"), b(0, 12, 2, 16, 14, 16, "wood_light"),
        b(1, 14, 3, 3, 28, 5, "wood"), b(13, 14, 3, 15, 28, 5, "wood"), b(1, 14, 13, 3, 28, 15, "wood"), b(13, 14, 13, 15, 28, 15, "wood"),
        b(3, 14, 13, 13, 26, 15, "wood_dark"),
        b(0, 28, 0, 16, 30, 16, "red"), b(0, 30, 0, 16, 31, 16, "white"), b(0, 24, 0, 16, 28, 2, "white"), b(0, 24, 0, 4, 28, 2, "red"), b(8, 24, 0, 12, 28, 2, "red"),
        b(4, 14, 7, 7, 18, 10, "blue"), b(9, 14, 6, 12, 17, 9, "yellow"), b(6, 18, 14.6, 10, 24, 15, "paper"),
    ])
    # the ticket you hold (not a prop): a small ticket on a tilted plane
    pk.model("ticket", [b(3, 6, 6, 13, 7, 10, "ticket", rot=("y", 22.5, (8, 6, 8))), b(3, 7, 6, 5, 7.3, 10, "red", rot=("y", 22.5, (8, 6, 8))),
                        b(9, 7, 7.2, 12, 7.3, 8.8, "brass", rot=("y", 22.5, (8, 6, 8)))])
    statues(pk, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "statues.csv"))
    eggs(pk)
    pk.finish()


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "resourcepack")
