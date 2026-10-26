"""Regenerate docs/wiki-steamworks/08-recipes.md from src/lib/recipes-data.json (Workbench rows).

The table used to be hand-typed and drifted out of sync the moment recipes changed (26-10).
Run: python -I scripts/build-wiki-recipes-md.py   (after build-recipes-data.js)
"""
import io, json

d = json.load(io.open("src/lib/recipes-data.json", encoding="utf-8"))
sw = next(p for p in d["plugins"] if p["id"] == "steamworks")

SECTIONS = [
    ("Materials", ["Andesite Alloy", "Zinc Nugget", "Andesite Casing", "Brass Casing", "Whisk", "Propeller",
                   "Iron Plate", "Copper Plate", "Golden Plate", "Brass Plate", "Brass Ingot"]),
    ("Power and transmission", ["Shaft", "Cogwheel", "Large Cogwheel", "Gearbox", "Hand Crank", "Water Wheel",
                                "Windmill", "Steam Boiler", "Steam Engine", "Speed Gauge", "Stress Gauge",
                                "Clutch", "Sprocket", "Gearshift", "Whistle"]),
    ("Machines", ["Millstone", "Steam Hammer", "Bellows Fan", "Kettle", "Mixer", "Drill",
                  "Saw", "Crushing Wheel"]),
    ("Moving items", ["Belt", "Chute", "Funnel", "Depot", "Ejector"]),
    ("Karakuri Arm", ["Arm", "Arm Gripper", "Arm Placer", "Arm Drill", "Punch Card"]),
    ("Trains", ["Locomotive", "Passenger Wagon", "Cargo Wagon", "Station", "Signal"]),
    ("Oil", ["Pipe", "Tank", "Pump", "Valve", "Pumpjack", "Refinery"]),
    ("Tools", ["Wrench", "Dowsing Rod", "Canister"]),
]

def row(r):
    if r["type"] != "shaped":
        return None
    rows = []
    for line in r["grid"]:
        cells = list(line)
        while len(cells) > 1 and cells[-1] is None:   # trim empty right edge
            cells.pop()
        rows.append(" ".join((c or "-").lower() for c in cells))
    while rows and set(rows[0]) <= {"-", " "}:
        rows.pop(0)
    while rows and set(rows[-1]) <= {"-", " "}:
        rows.pop()
    return "| %s | %s | %d |" % (r["result"], " / ".join(rows), r["count"])

by_name = {}
for r in sw["recipes"]:
    if r["category"] == "Workbench" and r["type"] == "shaped":
        by_name.setdefault(r["result"], []).append(r)

out = io.open("docs/wiki-steamworks/08-recipes.md", "w", encoding="utf-8", newline="\n")
out.write("""---
slug: steamworks-recipes
title: Steamworks Recipes
category: Steamworks
parent: steamworks
---
## Crafting recipes
Rows are the crafting grid from top to bottom; "-" is an empty slot. Steamworks parts and materials (shaft, cogwheel, plates...) must be the real Steamworks item, not plain paper. All recipes also show up in your recipe book.
This page is generated from the plugin's own recipe table - run `node scripts/build-recipes-data.js && python -I scripts/build-wiki-recipes-md.py` after recipe changes.

""")
missing = set(by_name)
for title, names in SECTIONS:
    present = [n for n in names if n in by_name]
    out.write("### %s\n| Item | Grid | Makes |\n|---|---|---|\n" % title)
    for n in present:
        for r in by_name[n]:
            out.write(row(r) + "\n")
        missing.discard(n)
    out.write("\n")
if missing:
    out.write("### Other\n| Item | Grid | Makes |\n|---|---|---|\n")
    for n in sorted(missing):
        for r in by_name[n]:
            out.write(row(r) + "\n")
    out.write("\n")
out.write("""### Smelting
| Input | Furnace / Blast Furnace |
|---|---|
| Raw Zinc | Zinc Ingot |
| Raw Tin | Tin Ingot |

Machine recipes (millstone, steam hammer, bellows fan, mixer) are on [Processing Machines](/wiki?page=steamworks-processing).
""")
out.close()
print("sections:", len(SECTIONS), "unlisted:", sorted(missing) if missing else "none")
