"""Generate the Gazette's folded-newspaper FMM display model.

The book's own generator (make_books.py) is not in this repo copy, so this small
script builds the one extra design the same way: a .bbmodel (source, with the
embedded 32x32 Blockbench palette borrowed from lore_book) and the .json that
ModelItemAPI.applyDisplayModel reads. Both go into the server's
plugins/FreeMinecraftModels/models/steampunk_iekcorbar/books/ folder.

Run from the repo root:  python -I scripts/make_gazette_model.py
No resource pack rebuild: FMM carries the texture inside the .bbmodel.
"""
import json
import os
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BOOKS = os.path.join(ROOT, "fmm-models", "steampunk_iekcorbar", "books")
NAME = "gazette_newspaper"

# The six book designs share one 32x32 palette. Reuse it verbatim (including
# the Blockbench texture keys FMM needs) instead of inventing a second one.
with open(os.path.join(BOOKS, "lore_book.bbmodel"), encoding="utf-8") as fh:
    donor = json.load(fh)
texture = dict(donor["textures"][0])
texture["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, NAME + "/texture"))

# Palette blocks (4x4 px each): warm newsprint, grey fold, black ink.
PAPER = [0, 8, 4, 12]
FOLD = [24, 24, 28, 28]
INK = [24, 16, 28, 20]

# (name, from, to, uv) in item-model space (0..16, centred on 8). The paper is
# two thin slabs with a crease between them; the ink sits 0.02 above the paper
# so nothing is coplanar (no z-fighting) and every axis stays inside -16..32.
elements = [
    ("page_left",  [2.4, 6.6, 1.2], [7.9, 7.3, 14.8], PAPER),
    ("page_right", [8.1, 6.6, 1.2], [13.6, 7.3, 14.8], PAPER),
    ("fold",       [7.9, 6.6, 1.2], [8.1, 7.32, 14.8], FOLD),
    ("masthead_left",  [2.8, 7.32, 12.6], [7.6, 7.36, 14.2], INK),
    ("masthead_right", [8.4, 7.32, 12.6], [13.2, 7.36, 14.2], INK),
]
for i, z in enumerate([11.2, 9.9, 8.6, 7.3, 6.0, 4.7, 3.4], start=1):
    elements.append((f"line_left_{i}",  [2.8, 7.32, z], [7.6, 7.36, z + 0.3], INK))
    elements.append((f"line_right_{i}", [8.4, 7.32, z], [13.2, 7.36, z + 0.3], INK))

bb_elements = []
json_elements = []
ids = []
for name, start, end, uv in elements:
    eid = str(uuid.uuid5(uuid.NAMESPACE_URL, NAME + "/" + name))
    ids.append(eid)
    faces = {face: {"uv": uv, "texture": 0}
             for face in ("north", "east", "south", "west", "up", "down")}
    bb_elements.append({
        "name": name, "box_uv": False, "rescale": False, "locked": False,
        "render_order": "default", "allow_mirror_modeling": True,
        "from": start, "to": end, "autouv": 0, "color": 0,
        "origin": [(a + b) / 2 for a, b in zip(start, end)],
        "rotation": [0, 0, 0], "faces": faces, "type": "cube", "uuid": eid,
    })
    json_faces = {face: {"uv": uv, "texture": "#0"}
                  for face in ("north", "east", "south", "west", "up", "down")}
    json_elements.append({"from": start, "to": end, "faces": json_faces})

bbmodel = {
    "meta": {"format_version": "4.9", "model_format": "free", "box_uv": False},
    "name": NAME, "model_identifier": "", "visible_box": [1, 1, 0],
    "variable_placeholders": "", "variable_placeholder_buttons": [],
    "timeline_setups": [], "unhandled_root_fields": {},
    "resolution": {"width": 32, "height": 32},
    "elements": bb_elements,
    "outliner": [{
        "name": "newspaper", "origin": [0, 0, 0], "color": 0,
        "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, NAME + "/outliner")),
        "export": True, "mirror_uv": False, "isOpen": True, "locked": False,
        "visibility": True, "autouv": 0, "children": ids,
    }],
    "textures": [texture],
    "animations": [],
}

# The item transforms are the ones the other books use (tuned for a flat book).
with open(os.path.join(BOOKS, "lore_book.json"), encoding="utf-8") as fh:
    display = json.load(fh)["display"]
vanilla = {"textures": {"0": "0", "particle": "0"},
           "elements": json_elements, "display": display}

with open(os.path.join(BOOKS, NAME + ".bbmodel"), "w", encoding="utf-8") as fh:
    json.dump(bbmodel, fh, separators=(",", ":"))
    fh.write("\n")
with open(os.path.join(BOOKS, NAME + ".json"), "w", encoding="utf-8") as fh:
    json.dump(vanilla, fh, separators=(",", ":"))
    fh.write("\n")

print(f"Wrote {NAME}.bbmodel + {NAME}.json ({len(elements)} elements) in {BOOKS}")
