#!/usr/bin/env python3
"""Re-rig the CC0 species in tools/downloads/ with the fixed rules from mesh2model.py.

The 13 mesh_*.json files were generated before the 11-11 rig rewrite: boxes sat uncentred in the
+x/+z octant, tail pivots landed at the tail TIP, head pivots at the top of the skull and the leg
thresholds cut giraffe/elephant legs mid-shin (one global rule for every species). The original
OBJ meshes are not in the repo, so instead of re-voxelising we rebuild each JSON from its own
boxes: re-centre x/z, run the new classify+pivot rules, write bones/boxes back. Everything
downstream (mesh_build in make_animals.py, rare/baby variants, zoo statues, the audit) keeps
reading the same file format. Re-running the tool on an already-rigged file is idempotent.

Special cases:
  * crab - the hand-built crab is drawn facing -X and oriented() skips the quarter turn for it,
    but the mesh crab came out of mesh2model facing -Z like every other species. Rig it in the
    -Z space, then turn the finished bones+boxes -90 deg about y and mark meta.facing so a second
    run doesn't turn it twice.
  * orca - owner round 10-11 late: the dorsal fin vanished in the greedy voxel merge. Re-added
    here as a three-box ridge on the mid-back (the model gets its size back via SIZE["orca"] in
    make_animals.py, not by growing the boxes).

usage: python3 tools/mesh_rerig.py [--dry]
"""
import argparse
import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from mesh2model import RULES, SPECIES_RULES, bone_origins, recenter, rig_boxes  # noqa: E402


def rules_for(sid):
    """mesh2model.RULES met de per-soort uitzonderingen eroverheen (idem rig_and_origins)."""
    r = dict(RULES)
    r.update(SPECIES_RULES.get(sid, {}))
    return r


def _clean(b):
    a, c = b["from"], b["to"]
    b["from"] = [min(x, y) for x, y in zip(a, c)]
    b["to"] = [max(x, y) for x, y in zip(a, c)]
    return b


def turn_minus_x(d):
    """(x, z) -> (z, -x): head from -Z to -X, applied to boxes AND pivots."""
    t = lambda v: [v[2], v[1], -v[0]]
    for b in d["boxes"]:
        a, c = t(b["from"]), t(b["to"])
        b["from"], b["to"] = [min(x, y) for x, y in zip(a, c)], [max(x, y) for x, y in zip(a, c)]
    for name, org in d["bones"].items():
        d["bones"][name] = [round(v, 2) for v in t(org)]
    d["meta"]["facing"] = "-x"


def turn_back_to_minus_z(d):
    """Undo turn_minus_x ((x, z) -> (-z, x)) so re-running the tool re-rigs in the authored space."""
    t = lambda v: [-v[2], v[1], v[0]]
    for b in d["boxes"]:
        a, c = t(b["from"]), t(b["to"])
        b["from"], b["to"] = [min(x, y) for x, y in zip(a, c)], [max(x, y) for x, y in zip(a, c)]


def add_dorsal_fin(rigged, d):
    """One slanted ridge of three boxes on the mid-back, coloured like the whale's dark skin
    already used by the mesh. Idempotent: keyed on meta.dorsal_fin."""
    if d["meta"].get("dorsal_fin"):
        return
    col = "koi_black"
    bs = rigged.get("body", [])
    back = max(b["to"][1] for b in bs
               if abs((b["from"][2] + b["to"][2]) / 2) < 6)
    fin = [((-0.6, back - 0.5, -1.0), (0.6, back + 2.5, 2.0)),
           ((-0.6, back + 2.0, 1.5), (0.6, back + 4.5, 3.5)),
           ((-0.6, back + 4.0, 3.0), (0.6, back + 5.5, 4.5))]
    for frm, to in fin:
        bs.append({"bone": "body", "from": [round(v, 2) for v in frm],
                   "to": [round(v, 2) for v in to], "color": col})
    d["meta"]["dorsal_fin"] = True


def add_tusks(rigged, d):
    """Olifantslagtanden: ivoire stubs links en rechts van de slurf, voor de onderkaak.

    De mesh is al 46,2 px lang en het x/z-venster is -24..24, dus tanden vóór de kop kunnen niet -
    ze moeten binnen de bestaande envelope blijven. Daarom: twee dunne boxen op |x| ~2-3, hangend
    vanaf de onderkant van de face naar beneden-buiten. Ze worden GEPIJND (meta.pins): zonder pin
    ziet de classificator ze bij een volgende run als slanke lage front-boxen = POTEN.
    """
    hs = [b for b in rigged.get("h_head", []) if b["color"] != "tusk"]
    if not hs:
        return
    fz = min(b["from"][2] for b in hs)          # het uiterste front van de kop
    jy = min(b["from"][1] for b in hs)          # onderkant van het gezicht
    tanden = []
    for sx in (1, -1):
        x0, x1 = (2.1, 3.5) if sx > 0 else (-3.5, -2.1)
        tanden.append({"bone": "h_head",
                       "from": [round(x0, 2), round(jy - 1.0, 2), round(fz + 1.4, 2)],
                       "to": [round(x1, 2), round(jy + 6.0, 2), round(fz + 4.2, 2)], "color": "tusk"})
    # meetpunten ONLY uit de originele kop-boxen, en oude tanden eerst weg: anders stapelt elke
    # run zich een laag lager (de vorige tand is dan het "gezicht" geworden)
    rigged["h_head"] = hs + tanden
    d["meta"]["pins"] = [p for p in d["meta"].get("pins", []) if p["color"] != "tusk"] + tanden


def rerig(path, dry=False):
    d = json.load(open(path, encoding="utf-8"))
    sid = os.path.basename(path)[len("mesh_"):-len(".json")]
    rig = d["meta"].get("rig", "quad")
    H = d["meta"]["height_px"]
    if sid == "crab" and d["meta"].get("facing") == "-x":
        turn_back_to_minus_z(d)        # authored space: rig always classifies with the head at -Z
    boxes = copy.deepcopy(d["boxes"])
    for b in boxes:
        del b["bone"]
        _clean(b)
    recenter(boxes)
    # gepinde boxen (tanden, rugvin) doorlopen de classificator NIET: die zien ze als slanke
    # laag hangende front-boxen en melden ze als poot. Eerst hun eigen bot, dan de rest riggen.
    pinned = {(tuple(p["from"]), tuple(p["to"]), p["color"]): p["bone"] for p in d["meta"].get("pins", [])}
    key = lambda b: (tuple(b["from"]), tuple(b["to"]), b["color"])
    rest, kept = [], []
    for b in boxes:
        (kept if key(b) in pinned else rest).append(b)
    rigged = rig_boxes(rest, rig, H, rules_for(sid))
    orgs = bone_origins(rigged, rig, H)     # vóór de pins: toegevoegd ivoor mag geen pivot verschuiven,
    for b in kept:                          #   anders is run 1 anders dan run 2 (en dat hoort niet)
        rigged.setdefault(pinned[key(b)], []).append(b)
    if sid == "orca":
        add_dorsal_fin(rigged, d)      # before assembly: fin rides along in the body sort-run
    if sid == "elephant":
        add_tusks(rigged, d)           # idem: ivoor hoort bij de kop en zwiept met het hoofd mee
    # body first: mesh_build parents every bone on "body", so it must exist before the children
    order = ["body"] + sorted(k for k in rigged if k != "body")
    # deterministic box order (bone, then position): re-runs stay byte-identical and the uuid5
    # numbering in make_animals doesn't shuffle the whole model when only the grouping changed
    d["boxes"] = [{"bone": bone, "from": b["from"], "to": b["to"], "color": b["color"]}
                  for bone in order
                  for b in sorted(rigged[bone], key=lambda b: (b["from"], b["to"], b["color"]))]
    d["bones"] = {k: orgs[k] for k in order if k in orgs}
    if sid == "crab":
        turn_minus_x(d)                # stored space: head at -X, exactly like the hand-built crab
    counts = ", ".join("%s:%d" % (k, len(rigged[k])) for k in order)
    print("%-9s %s" % (sid, counts))
    if not dry:
        json.dump(d, open(path, "w", encoding="utf-8"), ensure_ascii=False)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    dl = os.path.join(HERE, "downloads")
    for name in sorted(os.listdir(dl)):
        if name.startswith("mesh_") and name.endswith(".json"):
            rerig(os.path.join(dl, name), a.dry)


if __name__ == "__main__":
    main()
