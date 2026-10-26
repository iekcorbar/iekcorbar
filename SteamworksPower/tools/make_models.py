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
    m = mm.Model("pow_water_turbine", 1.0)
    # drum around the X axis: eight brass segments, hub and four blades inside
    ring = [(5.2, 0), (3.7, 3.7), (0, 5.2), (-3.7, 3.7), (-5.2, 0), (-3.7, -3.7), (0, -5.2), (3.7, -3.7)]
    for i, (y, z) in enumerate(ring):
        m.cube(f"seg{i}", (-3.4, y - 1.4, z - 1.4), (3.4, y + 1.4, z + 1.4), "copper")
    m.cube("hub", (-3.6, -1.2, -1.2), (3.6, 1.2, 1.2), "steel")
    m.cube("bN", (-2.2, 0.8, -0.7), (2.2, 4.4, 0.7), "brass")
    m.cube("bS", (-2.2, -4.4, -0.7), (2.2, -0.8, 0.7), "brass")
    m.cube("bE", (-2.2, -0.7, 0.8), (2.2, 0.7, 4.4), "brass")
    m.cube("bW", (-2.2, -0.7, -4.4), (2.2, 0.7, -0.8), "brass")
    m.cube("cap+", (3.4, -1.8, -1.8), (4.2, 1.8, 1.8), "darkstone")
    m.cube("cap-", (-4.2, -1.8, -1.8), (-3.4, 1.8, 1.8), "darkstone")
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


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "fmm-models")
    os.makedirs(out, exist_ok=True)
    for m in (water_turbine(), mechanical_press(), stock_ticker()):
        for e in m.elements:                       # vanilla model bounds (26.2 client rule)
            assert all(-16 <= c + 8 <= 32 for c in e["from"] + e["to"]), (m.name, e["name"])
        json.dump(m.bbmodel(), open(os.path.join(out, m.name + ".bbmodel"), "w", encoding="utf-8"), separators=(",", ":"))
        json.dump(m.item_json(), open(os.path.join(out, m.name + ".json"), "w", encoding="utf-8"), separators=(",", ":"))
        print(f"{m.name}: {len(m.elements)} cubes")
