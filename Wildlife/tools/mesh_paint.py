#!/usr/bin/env python3
"""Schilder de mesh-JSONs zonder external texture (Quaternius farm/fish: textures zitten alleen
in de .blend) met soort-eigen paletkleuren op boxposities. Doet Nothing to textured models.

usage: python3 tools/mesh_paint.py <sid>   (sid in PAINTERS)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def _bbox(boxes):
    xs0 = min(b["from"][0] for b in boxes); ys0 = min(b["from"][1] for b in boxes); zs0 = min(b["from"][2] for b in boxes)
    xs1 = max(b["to"][0] for b in boxes); ys1 = max(b["to"][1] for b in boxes); zs1 = max(b["to"][2] for b in boxes)
    return xs0, ys0, zs0, xs1, ys1, zs1


def _rel(b, bb):
    xs0, ys0, zs0, xs1, ys1, zs1 = bb
    cx, cy, cz = (b["from"][0] + b["to"][0]) / 2, (b["from"][1] + b["to"][1]) / 2, (b["from"][2] + b["to"][2]) / 2
    return cx, (cy - ys0) / (ys1 - ys0 + 1e-9), (cz - zs0) / (zs1 - zs0 + 1e-9)


def paint_zebra(boxes):
    bb = _bbox(boxes)
    for b in boxes:
        _, ry, rz = _rel(b, bb)
        if b["bone"] in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
            b["color"] = "zebra_black"
        elif b["bone"] == "tail":
            b["color"] = "zebra_black"
        elif b["bone"] == "h_head":
            b["color"] = "zebra_black" if ry > 0.82 else "zebra_white"
        elif ry > 0.86 and 0.30 < rz < 0.80:
            b["color"] = "zebra_black"                       # mane
        else:
            b["color"] = "zebra_white"


def paint_orca(boxes):
    bb = _bbox(boxes)
    for b in boxes:
        _, ry, rz = _rel(b, bb)
        if ry < 0.42 or (rz > 0.80 and ry < 0.62):
            b["color"] = "white"                              # belly + underside of the flukes
        elif b["bone"] == "tail":
            b["color"] = "koi_black"
        else:
            b["color"] = "koi_black"
            if rz < 0.12 and ry > 0.55:
                b["color"] = "white"                          # eye patch zone stays bright


def paint_koi(boxes):
    bb = _bbox(boxes)
    for b in boxes:
        cx, ry, rz = _rel(b, bb)
        h = int(abs(cx) * 13 + ry * 101 + rz * 57) % 7
        b["color"] = "monarch" if h == 0 else "chestnut" if h == 3 else "white"


PAINTERS = {"zebra": paint_zebra, "orca": paint_orca, "koi": paint_koi}


def main():
    sid = sys.argv[1]
    path = os.path.join(HERE, "downloads", "mesh_%s.json" % sid)
    d = json.load(open(path, encoding="utf-8"))
    PAINTERS[sid](d["boxes"])
    d["meta"]["painted"] = "procedural palette (mesh2model.py + mesh_paint.py, source textures blend-only)"
    json.dump(d, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    print("%s: %d boxes hertset" % (sid, len(d["boxes"])))


if __name__ == "__main__":
    main()
