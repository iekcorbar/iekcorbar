"""Steamworks Power FMM models. Reuses Steamworks' model helpers (same texture sheet, same
bbmodel/json writers) with its own uuid counter, so the base's files stay byte-stable.
Run from the repo root:  python -I SteamworksPower/tools/make_models.py SteamworksPower/fmm-models"""
import itertools
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "Steamworks", "tools"))
import make_models as mm          # noqa: E402  (core helpers: Model, swatches, png)

mm._c = itertools.count(90000)    # pow_* uuids never touch the base's counter range


def water_turbine():
    # drum AROUND THE SHAFT (model Y): eight copper segments, hub and four blades inside.
    # (This was once built around model X - but the SPIN display turns around model Y, so the
    # drum tumbled sideways on the shaft. Model Y is always the shaft axis for custom parts.)
    m = mm.Model("pow_water_turbine", 1.0)
    ring = [(5.2, 0), (3.7, 3.7), (0, 5.2), (-3.7, 3.7), (-5.2, 0), (-3.7, -3.7), (0, -5.2), (3.7, -3.7)]
    for i, (x, z) in enumerate(ring):                      # segments at (x,z), thickness along Y
        m.cube(f"seg{i}", (x - 1.4, -3.4, z - 1.4), (x + 1.4, 3.4, z + 1.4), "copper")
    m.cube("hub", (-1.2, -3.6, -1.2), (1.2, 3.6, 1.2), "steel")
    m.cube("bX+", (0.8, -2.2, -0.7), (4.4, 2.2, 0.7), "brass")
    m.cube("bX-", (-4.4, -2.2, -0.7), (-0.8, 2.2, 0.7), "brass")
    m.cube("bZ+", (-0.7, -2.2, 0.8), (0.7, 2.2, 4.4), "brass")
    m.cube("bZ-", (-0.7, -2.2, -4.4), (0.7, 2.2, -0.8), "brass")
    m.cube("cap+", (-1.8, 3.4, -1.8), (1.8, 4.2, 1.8), "darkstone")
    m.cube("cap-", (-1.8, -4.2, -1.8), (1.8, -3.4, 1.8), "darkstone")
    return m


def mechanical_press():
    # the plate it stamps on is the block's own top face: the arch stands over it (y +8..+14)
    m = mm.Model("pow_mechanical_press", 0.8)
    m.cube("base", (-7.2, -8.0, -7.2), (7.2, -6.0, 7.2), "darkstone")
    m.cube("colW", (-6.2, -6.0, -1.2), (-4.2, 12.4, 1.2), "steel")
    m.cube("colE", (4.2, -6.0, -1.2), (6.2, 12.4, 1.2), "steel")
    m.cube("head", (-6.6, 12.4, -2.4), (6.6, 13.6, 2.4), "brass")
    m.cube("ram", (-1.2, 10.2, -1.2), (1.2, 12.4, 1.2), "dark")
    m.cube("foot", (-3.0, 8.8, -3.0), (3.0, 10.2, 3.0), "brass")
    return m


def stock_ticker():
    m = mm.Model("pow_stock_ticker", 1.0)
    m.cube("body", (-4.4, -6.2, -3.2), (4.4, 1.0, 3.2), "copper")
    m.cube("hood", (-5.0, 1.0, -3.8), (5.0, 2.4, 3.8), "verdigris")
    for i, (a, b) in enumerate([(-2.6, -0.6), (0.6, 2.6)]):
        m.cube(f"rim{i}", (a - 0.4, -3.8, 3.2), (b + 0.4, 0.8, 3.6), "brass")
        m.cube(f"dial{i}", (a, -3.4, 3.4), (b, 0.4, 3.8), "dial")
    m.cube("bracket", (-1.0, -7.6, -1.0), (1.0, -6.0, 1.0), "steel")
    m.cube("boltW", (-4.0, -5.4, 3.0), (-3.2, -4.6, 3.5), "brass")
    m.cube("boltE", (3.2, -5.4, 3.0), (4.0, -4.6, 3.5), "brass")
    return m


def brass_flywheel():
    # a heavy wheel AROUND the shaft (model Y): brass rim, six steel spokes, brass hub
    m = mm.Model("pow_brass_flywheel", 1.0)
    mm.axle(m)
    mm.ring(m, "rim", 6.9, 1.8, -1.1, 1.1, "brass")
    for k in range(6):
        mm.radial(m, f"spoke{k}", k * 60, 1.6, 6.2, 0.5, -0.55, 0.55, "steel")
    mm.rod(m, "hub", 2.2, -1.3, 1.3, "brass")
    return m


def sieve_funnel():
    # a funnel with a mesh tray. Custom parts stand with model Y along the shaft (belt direction)
    # and model Z up: the mesh is the tray top at belt height, the belt line enters at both Y ends
    # through brass end-walls. Legs carry it, a copper spout hints where the sifted items go.
    m = mm.Model("pow_sieve_funnel", 1.0)
    for sx in (-1, 1):
        for sz in (-1, 1):
            m.cube(f"leg{sx}{sz}", (sx * 5.8 - 0.5, sz * 5.8 - 0.5, -8.0),
                   (sx * 5.8 + 0.5, sz * 5.8 + 0.5, 2.4), "steel")
    for sy in (-1, 1):
        m.cube(f"wall{sy}", (-6.4, sy * 6.4 - 0.6, 2.4), (6.4, sy * 6.4 + 0.6, 5.0), "brass")
    for sx in (-1, 1):
        m.cube(f"side{sx}", (sx * 6.4 - 0.6, -6.4, 2.4), (sx * 6.4 + 0.6, 6.4, 5.0), "darkwood")
    for x in (-3.2, 0.0, 3.2):
        m.cube(f"mesh_x{x}", (x - 0.5, -5.8, 4.9), (x + 0.5, 5.8, 5.3), "dial")
    for y in (-3.2, 0.0, 3.2):
        m.cube(f"mesh_y{y}", (-5.8, y - 0.5, 5.1), (5.8, y + 0.5, 5.5), "steel")
    m.cube("spout", (-1.4, -1.4, -4.6), (1.4, 1.4, -2.4), "copper")
    for k, (bx, by) in enumerate([(-6.4, -6.4), (6.4, -6.4), (-6.4, 6.4), (6.4, 6.4)]):
        m.cube(f"bolt{k}", (bx - 0.5, by - 0.5, 5.0), (bx + 0.5, by + 0.5, 5.8), "brass")
    return m


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "fmm-models")
    os.makedirs(out, exist_ok=True)
    for m in (water_turbine(), mechanical_press(), stock_ticker(), brass_flywheel(), sieve_funnel()):
        for e in m.elements:                       # vanilla model bounds (26.2 client rule)
            assert all(-16 <= c + 8 <= 32 for c in e["from"] + e["to"]), (m.name, e["name"])
        json.dump(m.bbmodel(), open(os.path.join(out, m.name + ".bbmodel"), "w", encoding="utf-8"), separators=(",", ":"))
        json.dump(m.item_json(), open(os.path.join(out, m.name + ".json"), "w", encoding="utf-8"), separators=(",", ":"))
        print(f"{m.name}: {len(m.elements)} cubes")
