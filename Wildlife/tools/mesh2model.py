#!/usr/bin/env python3
"""CC0 download-mesh (OBJ+MTL+PNG, Blender-exported) -> voxel boxes + bones -> mesh_<sid>.json.

make_animals.py picks the JSON up in tools/downloads/ and builds wl_<sid> from it (rare/baby/
statues/audit keep working through the normal machinery). Colours snap to our existing 255-cell
wildlife.png palette, so the atlas and every downstream tool stay untouched.

usage:
  python3 tools/mesh2model.py <model.obj> <sid> --h 24 --face -z --up y --rig quad
then:
  python3 tools/make_animals.py fmm-models/wildlife   (builds the species from downloads/mesh_<sid>.json)
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_animals import PALETTE  # name -> (r,g,b); quantise target

HERE = os.path.dirname(os.path.abspath(__file__))


# ------------------------------------------------------------------ obj loading

def load_obj(path):
    P, UV = [], []          # vertex positions, per face-corner uv (faces index into UV directly)
    faces = []
    cur_tex = None
    mtllib = None
    for ln in open(path, encoding="utf-8", errors="ignore"):
        t = ln.split()
        if not t:
            continue
        if t[0] == "v":
            P.append([float(x) for x in t[1:4]])
        elif t[0] == "vt":
            UV.append([float(t[1]), float(t[2])])
        elif t[0] == "f":
            vs, ts = [], []
            for a in t[1:]:
                parts = a.split("/")
                vs.append(int(parts[0]) - 1 if int(parts[0]) >= 0 else int(parts[0]) + len(P) - 1)
                ts.append(int(parts[1]) - 1 if len(parts) > 1 and parts[1] else -1)
            for k in range(1, len(vs) - 1):      # fan triangulation
                faces.append((vs[0], vs[k], vs[k + 1], ts[0], ts[k], ts[k + 1]))
        elif t[0] == "mtllib":
            mtllib = t[1]
        elif t[0] == "usemtl":
            pass
    P = np.array(P)
    F = np.array(faces, dtype=np.int64)
    flat = P[F[:, 0]]
    for a, b, c, ta, tb, tc in faces:            # uv per corner -> per first corner only for single-colour boxes; fine:
        pass
    tex = read_mtl_texture(os.path.join(os.path.dirname(path), mtllib),
                           os.path.splitext(os.path.basename(path))[0]) if mtllib else None
    return P, F, UV, tex


def read_mtl_texture(mtl_path, obj_stem=None):
    if not os.path.exists(mtl_path):
        mtl_path = None
    pngs = []
    if mtl_path:
        for ln in open(mtl_path, encoding="utf-8", errors="ignore"):
            t = ln.split()
            if t and t[0] == "map_Kd":
                pngs.append(" ".join(t[1:]))
    d = os.path.dirname(mtl_path) if mtl_path else None
    for name in pngs:
        base = os.path.basename(name.strip().strip('"'))
        for cand in (os.path.join(d, base), os.path.join(d, "..", "Textures", base),
                     os.path.join(d, base.replace(" ", "_")), os.path.join(d, "..", base)):
            if os.path.exists(cand):
                return cand
        for root, _, files in os.walk(os.path.join(d, "..")):
            if base in files:
                return os.path.join(root, base)
    # Blender's OBJ exporter drops map_Kd for Quaternius packs: guess by model name instead.
    if obj_stem:
        import glob as _g
        up = os.path.join(d, "..") if d else "."
        cands = _g.glob(os.path.join(up, "**", "%s*.png" % obj_stem), recursive=True) + \
                _g.glob(os.path.join(up, "**", "%s*.png" % obj_stem.lower()), recursive=True) + \
                _g.glob(os.path.join(up, "**", "%s_Texture.png" % obj_stem.lower()), recursive=True)
        cands = sorted(set(cands), key=len)
        if cands:
            return cands[0]
    return None


# ------------------------------------------------------------------ geometry

def orient(P, face, up):
    P = P.copy()
    if up == "z":                                # Blender z-up -> y-up
        P = np.stack([P[:, 0], P[:, 2], -P[:, 1]], axis=1)
    if face == "auto":
        Lx = P[:, 0].max() - P[:, 0].min()
        Lz = P[:, 2].max() - P[:, 2].min()
        if Lx >= Lz:                             # body lies along x -> put length on z
            P = np.stack([-P[:, 2], P[:, 1], P[:, 0]], axis=1)
        zmin, zmax = P[:, 2].min(), P[:, 2].max()
        mid = (zmin + zmax) / 2
        if ARGS.rig == "fish":                   # a fish head is the THICK end (a tailfin is tall)
            head_a = (P[P[:, 2] < zmin + 0.18 * (zmax - zmin)]).shape[0]
            head_b = (P[P[:, 2] > zmax - 0.18 * (zmax - zmin)]).shape[0]
            flip = head_b > head_a
        else:                                    # the higher end carries the neck/head/ears
            top_a = P[P[:, 2] < mid][:, 1].max()
            top_b = P[P[:, 2] >= mid][:, 1].max()
            flip = top_b > top_a
        if flip:                                 # head is on +Z -> turn around
            P = np.stack([-P[:, 0], P[:, 1], -P[:, 2]], axis=1)
        face = "-z"
    if face == "+z":
        P = np.stack([-P[:, 0], P[:, 1], -P[:, 2]], axis=1)      # half turn
    elif face == "+x":
        P = np.stack([P[:, 2], P[:, 1], -P[:, 0]], axis=1)
    elif face == "-x":
        P = np.stack([-P[:, 2], P[:, 1], P[:, 0]], axis=1)
    return P                                      # face == "-z": identity (head at -Z, our drawn convention)


def voxelize(P, F, UV, tex, height_px, step):
    P = orient(P, ARGS.face, ARGS.up)
    span_y = P[:, 1].max() - P[:, 1].min()
    P = P * (height_px / span_y)
    P[:, 1] -= P[:, 1].min()
    P[:, 0] -= (P[:, 0].max() + P[:, 0].min()) / 2
    P[:, 2] -= (P[:, 2].max() + P[:, 2].min()) / 2
    lo, hi = P.min(axis=0), P.max(axis=0)
    dim = np.ceil((hi - lo) / step).astype(int) + 1
    img = Image.open(tex).convert("RGBA") if tex else None
    acc = {}                                      # voxel -> [n, r_sum, g_sum, b_sum]
    n_tri = len(F)
    for fi in range(n_tri):
        i0, i1, i2, t0, t1, t2 = F[fi]
        A, B, C = P[i0], P[i1], P[i2]
        e1, e2 = B - A, C - A
        area2 = np.linalg.norm(np.cross(e1, e2))
        if area2 < 1e-9:
            continue
        n = max(2, int(np.ceil(np.max([np.linalg.norm(e1), np.linalg.norm(e2), np.linalg.norm(C - B)]) / (step * 0.55))))
        us, vs = np.meshgrid(np.linspace(0, 1, n), np.linspace(0, 1, n))
        keep = (us + vs) <= 1.0
        us, vs = us[keep], vs[keep]
        pts = A[None, :] + us[:, None] * e1[None, :] + vs[:, None] * e2[None, :]
        idx = np.floor((pts - lo) / step).astype(int)
        if img is not None and t0 >= 0 and UV is not None and len(UV):
            w, h = img.size
            uv0, uv1, uv2 = np.array(UV[t0]), np.array(UV[t1]), np.array(UV[t2])
            uvs = uv0[None, :] + us[:, None] * (uv1 - uv0)[None, :] + vs[:, None] * (uv2 - uv0)[None, :]
            px = (np.clip(uvs[:, 0], 0, 0.999) * w).astype(int)
            py = ((1 - np.clip(uvs[:, 1], 0, 0.999)) * h).astype(int)
            cols = img.load()
            for k in range(len(pts)):
                rgb = cols[int(px[k]), int(py[k])]
                if rgb[3] < 127:
                    continue
                key = (idx[k, 0], idx[k, 1], idx[k, 2])
                a = acc.setdefault(key, [0, 0.0, 0.0, 0.0])
                a[0] += 1
                a[1] += rgb[0]
                a[2] += rgb[1]
                a[3] += rgb[2]
        else:
            col = np.array([200, 200, 200])
            for k in range(len(pts)):
                key = (idx[k, 0], idx[k, 1], idx[k, 2])
                a = acc.setdefault(key, [0, 0.0, 0.0, 0.0])
                a[0] += 1
                a[1] += col[0]
                a[2] += col[1]
                a[3] += col[2]
    return acc, dim, lo, step


def quantise(rgb):
    best, bd = None, 1e18
    for name, c in PALETTE.items():
        d = (rgb[0] - c[0]) ** 2 + (rgb[1] - c[1]) ** 2 + (rgb[2] - c[2]) ** 2
        if d < bd:
            best, bd = name, d
    return best


def greedy_boxes(acc, dim):
    g = np.zeros((int(dim[0]), int(dim[1]), int(dim[2]), 4), dtype=np.float64)
    for (x, y, z), a in acc.items():
        if 0 <= x < g.shape[0] and 0 <= y < g.shape[1] and 0 <= z < g.shape[2]:
            g[x, y, z] = a
    occ = g[..., 0] > 0
    boxes = []
    nz = g.shape[2]
    for z in range(nz):
        for y in range(g.shape[1]):
            x = 0
            while x < g.shape[0]:
                if not occ[x, y, z]:
                    x += 1
                    continue
                w = 1
                while x + w < g.shape[0] and occ[x + w, y, z]:
                    w += 1
                d = 1
                while y + d < g.shape[1] and occ[x:x + w, y + d, z].all():
                    d += 1
                h = 1
                while z + h < g.shape[2] and occ[x:x + w, y:y + d, z + h].all():
                    h += 1
                sub = g[x:x + w, y:y + d, z:z + h]
                tot = sub[..., 0].sum()
                col = [sub[..., 1].sum() / tot, sub[..., 2].sum() / tot, sub[..., 3].sum() / tot]
                boxes.append((x, y, z, x + w, y + d, z + h, quantise(col), tot))
                occ[x:x + w, y:y + d, z:z + h] = False
                x += w
    return boxes


# ------------------------------------------------------------------ rigging
# 11-11 rewrite. The first version produced three systematic failures on the CC0 downloads:
#   * boxes kept their raw grid coordinates (0..span), never re-centred on x/z like every hand-built
#     species - so the body bone's [0, 0.55H, 0] pivot sat at the model's CORNER and every rotation
#     (death roll, swim wave, idle bob) swung the whole animal around that corner;
#   * pivots were guessed from the model bbox (tail at the TIP, head at 0.75 of its top) instead of
#     from the bone's own attachment point - tails tore off when they wagged, heads somersaulted;
#   * one global threshold set for all species: giraffe and elephant legs were cut off mid-shin,
#     penguin heads and macaw heads were missed entirely.
# The rules below work on CENTRED box dicts (x/z around 0, head at -Z, y 0..H) and follow the
# conventions of the hand-built builders in make_animals.py: hip at the top of the leg, neck at the
# BACK of the head, tail at its BASE, wing at the shoulder (inner top corner). Each species may
# override the generic window (RULES) because a merged greedy-box can only be classified by where
# it sits, and a horse's thigh sits somewhere else than a crab's claw.

RULES = {                                   # generic quad defaults: slim lower boxes are legs,
    "leg_cy": 0.46, "leg_top": 0.62,        #   front/back split at z=0, left/right at x=0
    "leg_w": 0.34, "leg_d": 0.42,           #   (w/d are fractions of the body's own span)
    "head_z": 0.24, "head_y": 0.52,         # head zone: front 24% of the length, above 52% height
    "head_w": 0.60, "tail_z": 0.80,         # tail zone: last 20% of length, narrow, short, ON THE
    "tail_w": 0.24, "tail_d": 0.35,         #   centre line and springing from above the belly
    "leg_cy_bird": 0.34, "wing_x": 0.55,    # bird: feet under 34% (slim boxes only), wings beyond
    "leg_w_bird": 0.50, "leg_d_bird": 0.50, #   55% of half-width, head a NARROW front-top cluster,
    "head_z_bird": 0.26, "head_y_bird": 0.55,   # tail last 22% (narrow)
    "tail_z_bird": 0.78, "tail_w_bird": 0.30, "head_w_bird": 0.55,
    "tail_z_fish": 0.70, "head_z_fish": 0.28, "head_y_fish": 0.45, "tail_d_fish": 0.45,
}

SPECIES_RULES = {
    # 11-11 previews (pose3d) drove every one of these numbers.
    "elephant":  {"leg_d": 0.30, "head_z": 0.30, "head_y": 0.35},   # pillar legs stay short boxes;
                                                                    # head+tusks+trunk in front, low
    "giraffe":   {"leg_cy": 0.52, "leg_top": 0.74, "leg_d": 0.50,
                  "head_y": 0.50},                                   # legs are half the animal
    "boar":      {"tail_z": 0.95},                       # the mesh has no tail: rump stays body
    "macaque":   {"leg_cy": 0.50, "leg_top": 0.70, "head_y": 0.45}, # arms and legs alike, head low
    "crab":      {"leg_cy": 0.55, "leg_top": 0.85, "leg_w": 0.45, "leg_d": 0.60,
                  "head_z": 0.26, "head_y": 0.40, "tail_z": 0.98},  # legs under the shell, claws up
    "penguin":   {"leg_cy_bird": 0.24, "head_y_bird": 0.52},        # webbed feet only below the body,
                                                                    # tall head on a short neck
    "macaw":     {"leg_cy_bird": 0.25, "head_z_bird": 0.12, "head_y_bird": 0.30},  # tiny feet; head
                                                                                    # is the front-top cluster
}


def _dims(boxes):
    xs0 = min(b["from"][0] for b in boxes); xs1 = max(b["to"][0] for b in boxes)
    ys1 = max(b["to"][1] for b in boxes)
    zs0 = min(b["from"][2] for b in boxes); zs1 = max(b["to"][2] for b in boxes)
    return xs0, xs1, ys1, zs0, zs1


def rig_boxes(boxes, rig, H, rules):
    """Classify centred boxes (dicts with from/to lists) into bone groups."""
    if not boxes:
        return {}
    xs0, xs1, ys1, zs0, zs1 = _dims(boxes)
    Wx, Lz = xs1 - xs0, zs1 - zs0
    out = {}
    for b in boxes:
        x0, y0, z0 = b["from"]; x1, y1, z1 = b["to"]
        cx, cy, cz = (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2
        w, h, d = x1 - x0, y1 - y0, z1 - z0
        rel_z = (cz - zs0) / (Lz + 1e-9)
        rel_x = cx / (Wx / 2 + 1e-9)
        bone = "body"
        if rig == "quad":
            slim = w <= rules["leg_w"] * Wx and d <= rules["leg_d"] * Lz
            low = cy < rules["leg_cy"] * H or (y0 < rules["leg_cy"] * H and y1 < rules["leg_top"] * H)
            if slim and low:
                bone = ("leg_f" if cz < 0 else "leg_b") + ("l" if cx > 0 else "r")
            elif rel_z < rules["head_z"] and cy > rules["head_y"] * H and w <= rules["head_w"] * Wx:
                bone = "h_head"
            elif (rel_z > rules["tail_z"] and w < rules["tail_w"] * Wx and d < rules["tail_d"] * Lz
                  and abs(cx) < 0.30 * Wx and y0 > 0.35 * H):   # tail: centre-line box springing from
                bone = "tail"                                    # above the belly, off-centre is rump
        elif rig == "bird":
            slim = w <= rules["leg_w_bird"] * Wx and d <= rules["leg_d_bird"] * Lz
            if cy < rules["leg_cy_bird"] * H and slim:
                bone = "leg_l" if cx > 0 else "leg_r"
            elif abs(rel_x) > rules["wing_x"] and cy > rules["leg_cy_bird"] * H:
                bone = "wing_l" if cx > 0 else "wing_r"
            elif (rel_z < rules["head_z_bird"] and cy > rules["head_y_bird"] * H
                  and w <= rules["head_w_bird"] * Wx):      # a front column as wide as the body is chest
                bone = "h_head"
            elif rel_z > rules["tail_z_bird"] and w < rules["tail_w_bird"] * Wx:
                bone = "tail"
        elif rig == "fish":
            if rel_z > rules["tail_z_fish"] and d < rules["tail_d_fish"] * Lz:
                bone = "tail"                                # long merged slabs are the back, not the fluke
            elif rel_z < rules["head_z_fish"] and cy > rules["head_y_fish"] * H:
                bone = "h_head"
        out.setdefault(bone, []).append(b)
    return out


def bone_origins(rigged, rig, H):
    """Pivots from each bone's OWN attachment, not from the model bbox (see the 11-11 note above)."""
    org = {"body": [0.0, round(0.55 * H, 2), 0.0]}
    if "h_head" in rigged:
        bs = rigged["h_head"]
        hy0 = min(b["from"][1] for b in bs); hy1 = max(b["to"][1] for b in bs)
        hz0 = min(b["from"][2] for b in bs); hz1 = max(b["to"][2] for b in bs)
        org["h_head"] = [0.0, round((hy0 + hy1) / 2, 2), round(hz1 - 0.12 * (hz1 - hz0), 2)]
    for name in ("leg_fl", "leg_fr", "leg_bl", "leg_br", "leg_l", "leg_r"):
        if name in rigged:
            bs = rigged[name]
            top = max(b["to"][1] for b in bs)
            roots = [b for b in bs if b["to"][1] >= top - 1e-6]      # hip/shoulder box(es), not the hoof
            org[name] = [round(sum((b["from"][0] + b["to"][0]) / 2 for b in roots) / len(roots), 2),
                         round(top, 2),
                         round(sum((b["from"][2] + b["to"][2]) / 2 for b in roots) / len(roots), 2)]
    for name in ("wing_l", "wing_r"):
        if name in rigged:
            bs = rigged[name]
            # shoulder = the inner edge of the wing mass (closest to x=0) at the wing-root height
            inner = min(b["from"][0] for b in bs) if name == "wing_l" else max(b["to"][0] for b in bs)
            wy0 = min(b["from"][1] for b in bs); wy1 = max(b["to"][1] for b in bs)
            wz = sum((b["from"][2] + b["to"][2]) / 2 for b in bs) / len(bs)
            org[name] = [round(inner, 2), round(wy1 - 0.25 * (wy1 - wy0), 2), round(wz, 2)]
    if "tail" in rigged:
        bs = rigged["tail"]
        base = min(b["from"][2] for b in bs)                          # tail sits at the +Z end: base is its
        roots = [b for b in bs if b["from"][2] <= base + 1e-6]        # smallest z, where it meets the body
        org["tail"] = [0.0, round(sum((b["from"][1] + b["to"][1]) / 2 for b in roots) / len(roots), 2),
                       round(base - 0.5, 2)]
    return {k: v for k, v in org.items() if k in rigged or k == "body"}


def rig_and_origins(sid, boxes, rig, H):
    """One call: classify the centred boxes and derive the pivots. Returns (bones-with-boxes, origins)."""
    rules = dict(RULES)
    rules.update(SPECIES_RULES.get(sid, {}))
    rigged = rig_boxes(boxes, rig, H, rules)
    return rigged, bone_origins(rigged, rig, H)


def recenter(boxes):
    """Grid-index boxes are only half the job: shift x/z so the animal is centred on 0 like the
    hand-built species (feet stay on y=0). Without this every rotation swings around a corner.
    Guard: an already-centred file gets shifts of only ±0.01 px from rounding noise - re-applying
    those every run creeps the geometry across classification thresholds (mesh_rerig must be a
    fixed point). Only re-centre when the model really sits off-centre."""
    if not boxes:
        return boxes
    xs0 = min(b["from"][0] for b in boxes); xs1 = max(b["to"][0] for b in boxes)
    zs0 = min(b["from"][2] for b in boxes); zs1 = max(b["to"][2] for b in boxes)
    dx, dz = (xs0 + xs1) / 2, (zs0 + zs1) / 2
    if abs(dx) < 0.05 and abs(dz) < 0.05:
        return boxes
    for b in boxes:
        b["from"] = [round(b["from"][0] - dx, 2), round(b["from"][1], 2), round(b["from"][2] - dz, 2)]
        b["to"] = [round(b["to"][0] - dx, 2), round(b["to"][1], 2), round(b["to"][2] - dz, 2)]
    return boxes


# ------------------------------------------------------------------ main

def main():
    global ARGS
    ap = argparse.ArgumentParser()
    ap.add_argument("obj")
    ap.add_argument("sid")
    ap.add_argument("--h", type=float, default=24.0, help="total height in px (1 block = 16 px)")
    ap.add_argument("--face", default="auto", choices=["auto", "-z", "+z", "+x", "-x"])
    ap.add_argument("--up", default="y", choices=["y", "z"])
    ap.add_argument("--rig", default="quad", choices=["quad", "bird", "fish", "none"])
    ap.add_argument("--vox", type=float, default=1.15, help="voxel size in px")
    ap.add_argument("--out", default=None)
    ARGS = ap.parse_args()

    P, F, UV, tex = load_obj(ARGS.obj)
    acc, dim, lo, step = voxelize(P, F, UV, tex, ARGS.h, ARGS.vox)
    boxes = greedy_boxes(acc, dim)
    # grid index -> pixel coords: the index is relative to the bbox floor (lo), so add lo back,
    # then centre x/z (the 11-11 bug: without this the whole animal sat in the +x/+z octant).
    dicts = [{"bone": None,
              "from": [round(b[0] * step + lo[0], 2), round(b[1] * step + lo[1], 2), round(b[2] * step + lo[2], 2)],
              "to": [round(b[3] * step + lo[0], 2), round(b[4] * step + lo[1], 2), round(b[5] * step + lo[2], 2)],
              "color": b[6]} for b in boxes]
    recenter(dicts)
    if ARGS.rig != "none":
        rigged, orgs = rig_and_origins(ARGS.sid, dicts, ARGS.rig, ARGS.h)
    else:
        rigged, orgs = {"body": dicts}, {"body": [0.0, round(0.55 * ARGS.h, 2), 0.0]}
    out = {
        "meta": {"source": os.path.basename(ARGS.obj), "face": ARGS.face, "up": ARGS.up, "rig": ARGS.rig,
                 "height_px": ARGS.h, "license": "CC0 (Quaternius / OpenGameArt author pages)", "generated_by": "mesh2model.py"},
        "bones": orgs,
        "boxes": [],
    }
    for bone, bs in rigged.items():
        for b in bs:
            b["bone"] = bone
            out["boxes"].append(b)
    path = ARGS.out or os.path.join(HERE, "downloads", "mesh_%s.json" % ARGS.sid)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    print("%s: %d boxes, bones %s -> %s" % (ARGS.sid, len(out["boxes"]), sorted(rigged.keys()), path))


if __name__ == "__main__":
    main()
