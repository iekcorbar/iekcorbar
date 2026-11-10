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

def assign_rig(boxes, step, rig):
    if not boxes:
        return {}
    xs0 = min(b[0] for b in boxes) * step
    xs1 = max(b[3] for b in boxes) * step
    ys1 = max(b[4] for b in boxes) * step
    zs0 = min(b[2] for b in boxes) * step
    zs1 = max(b[5] for b in boxes) * step
    Lz, Wx, H = zs1 - zs0, xs1 - xs0, ys1
    out = {}
    for b in boxes:
        cx, cy, cz = (b[0] + b[3]) / 2 * step, (b[1] + b[4]) / 2 * step, (b[2] + b[5]) / 2 * step
        rel_z = (cz - zs0) / Lz
        rel_y = cy / H
        rel_x = (cx - (xs0 + xs1) / 2) / (Wx / 2 + 1e-9)
        bone = "body"
        if rig == "quad":
            if b[1] * step < 0.42 * H and abs(b[3] - b[0]) <= 0.30 * Wx and b[4] * step <= 0.44 * H + 0.001:
                bone = ("leg_f" if rel_z < 0.5 else "leg_b") + ("l" if rel_x > 0.12 else "r" if rel_x < -0.12 else "l")
                if "l" in bone and abs(rel_x) <= 0.12:
                    bone = bone
            elif rel_z < 0.24 and rel_y > 0.52 and abs(b[3] - b[0]) * step < 0.55 * Wx:
                bone = "h_head"
            elif rel_z > 0.80 and rel_y > 0.45 and (b[3] - b[0]) * step < 0.35 * Wx:
                bone = "tail"
        elif rig == "bird":
            if b[4] * step < 0.40 * H:
                bone = "leg_l" if rel_x > 0 else "leg_r"
            elif abs(rel_x) > 0.45 and 0.30 < rel_y < 0.85:
                bone = "wing_l" if rel_x > 0 else "wing_r"
            elif rel_z < 0.22 and rel_y > 0.5:
                bone = "h_head"
            elif rel_z > 0.82:
                bone = "tail"
        elif rig == "fish":
            if rel_z > 0.74:
                bone = "tail"
            elif rel_z < 0.30 and rel_y > 0.45:
                bone = "h_head"
        out.setdefault(bone, []).append(b)
    return out


def bone_origins(rig_boxes, step, rig, H):
    zs0 = min(b[2] for bs in rig_boxes.values() for b in bs) * step
    zs1 = max(b[5] for bs in rig_boxes.values() for b in bs) * step
    Lz = zs1 - zs0
    org = {"body": [0.0, round(0.55 * H, 2), 0.0]}
    if "h_head" in rig_boxes:
        bs = rig_boxes["h_head"]
        org["h_head"] = [0.0, round(max(b[4] for b in bs) * step * 0.75, 2), round(zs0 + 0.10 * Lz, 2)]
    for name in ("leg_fl", "leg_fr", "leg_bl", "leg_br", "leg_f", "leg_l", "leg_r", "leg_b"):
        if name in rig_boxes:
            bs = rig_boxes[name]
            cx = sum((b[0] + b[3]) / 2 for b in bs) * step / len(bs)
            cz = sum((b[2] + b[5]) / 2 for b in bs) * step / len(bs)
            org[name] = [round(cx, 2), round(max(b[4] for b in bs) * step, 2), round(cz, 2)]
    for name in ("wing_l", "wing_r"):
        if name in rig_boxes:
            bs = rig_boxes[name]
            cx = sum((b[0] + b[3]) / 2 for b in bs) * step / len(bs)
            cy = sum((b[1] + b[4]) / 2 for b in bs) * step / len(bs)
            org[name] = [round(cx * 0.55, 2), round(cy, 2), 0.0]
    if "tail" in rig_boxes:
        bs = rig_boxes["tail"]
        org["tail"] = [0.0, round(sum((b[1] + b[4]) / 2 for b in bs) * step / len(bs), 2), round(zs1 - 0.06 * Lz, 2)]
    return org


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
    rigged = assign_rig(boxes, step, ARGS.rig) if ARGS.rig != "none" else {"body": boxes}
    H = ARGS.h
    orgs = bone_origins(rigged, step, ARGS.rig, H)
    out = {
        "meta": {"source": os.path.basename(ARGS.obj), "face": ARGS.face, "up": ARGS.up, "rig": ARGS.rig,
                 "height_px": H, "license": "CC0 (Quaternius / OpenGameArt author pages)", "generated_by": "mesh2model.py"},
        "bones": orgs,
        "boxes": [],
    }
    for bone, bs in rigged.items():
        for b in bs:
            out["boxes"].append({"bone": bone, "from": [round(b[0] * step, 2), round(b[1] * step, 2), round(b[2] * step, 2)],
                                 "to": [round(b[3] * step, 2), round(b[4] * step, 2), round(b[5] * step, 2)], "color": b[6]})
    path = ARGS.out or os.path.join(HERE, "downloads", "mesh_%s.json" % ARGS.sid)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    print("%s: %d boxes, bones %s -> %s" % (ARGS.sid, len(out["boxes"]), sorted(rigged.keys()), path))


if __name__ == "__main__":
    main()
