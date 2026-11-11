# Isometric/side/front preview of .bbmodel files WITH the bone hierarchy and animations applied
# (preview3d.py only shows the rest cubes; this samples a clip at a time, like FMM will play it).
# usage: python pose3d.py out.png model.bbmodel cell size spec [spec ...]
#   spec = view:clip:time      e.g. side:fly:0.3   |   iso:idle:0   |   iso::0 = rest pose
# Euler convention: Blockbench applies bone rotations as X then Y then Z (M = Rz Ry Rx, about the
# origin); animation keyframes override the base bone rotation (FMM semantics), positions add.
import json, math, os, struct, sys, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_animals import PALETTE, NAMES  # noqa: E402

out, model, cell = sys.argv[1], sys.argv[2], int(sys.argv[3])
specs = [s.split(":") + ["0"] * (3 - s.count(":")) for s in sys.argv[4:]] or [["iso", "", 0]]
# yaw 0 = camera op de -Z-vlakte: horizontaal is model-X, dus DIT is het profiel. FMM-voorkant = -X,
# dus -90 kijkt het dier in de ogen (front) en +90 ziet de achterkant. Voor 11-12 stond "side" op 90:
# die tegels waren achterkant-gezichten, en dat vertekende elke review (rond-blob leek smal).
YAWS = {"iso": 45, "side": 0, "front": -90, "back": 90, "top": 0}
PITCH = {"top": 84, "iso": 24, "side": 8, "front": 8, "back": 8}

M = json.load(open(model, encoding="utf-8"))
CUBES = {e["uuid"]: e for e in M["elements"]}
ANIMS = {a["name"]: a for a in M.get("animations", [])}


def _decode_png(raw):
    import base64 as b64
    d = b64.b64decode(raw.split(",", 1)[1])
    pos, idat, w = 8, b"", 0
    while pos < len(d):
        n = struct.unpack(">I", d[pos:pos + 4])[0]
        t = d[pos + 4:pos + 8]
        if t == b"IHDR":
            w, h = struct.unpack(">II", d[pos + 8:pos + 16])
        if t == b"IDAT":
            idat += d[pos + 8:pos + 8 + n]
        pos += 12 + n
    data, rows, prev, p = zlib.decompress(idat), [], bytes(w * 4), 0
    for _ in range(h):
        f, cur = data[p], bytearray(data[p + 1:p + 1 + w * 4])
        p += 1 + w * 4
        for i in range(len(cur)):
            if f == 1 and i >= 4:
                cur[i] = (cur[i] + cur[i - 4]) & 255
            elif f == 2:
                cur[i] = (cur[i] + prev[i]) & 255
        prev = bytes(cur)
        rows.append([tuple(cur[i * 4:i * 4 + 3]) for i in range(w)])
    return rows


SKIN = (_decode_png(M["textures"][0]["source"]),) if M["resolution"]["width"] != 128 else None


def mul(a, b):
    return [sum(a[i * 3 + k] * b[k * 3 + j] for k in range(3)) for i in range(3) for j in (0, 1, 2)]


def euler(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = [1, 0, 0, 0, cx, -sx, 0, sx, cx]
    Ry = [cy, 0, sy, 0, 1, 0, -sy, 0, cy]
    Rz = [cz, -sz, 0, sz, cz, 0, 0, 0, 1]
    return mul(Rz, mul(Ry, Rx))


def apply(mat, t, p):
    return tuple(mat[i * 3] * p[0] + mat[i * 3 + 1] * p[1] + mat[i * 3 + 2] * p[2] + t[i] for i in range(3))


def sample(clip, time):
    """bone uuid -> (rot_override or None, position or None) interpolated from keyframes."""
    a = ANIMS.get(clip)
    out = {}
    if not a:
        return out
    for uuid, an in a["animators"].items():
        rot, pos = None, None
        for ch, chan in (("rotation", "rot"), ("position", "pos")):
            kfs = sorted([k for k in an["keyframes"] if k["channel"] == ch], key=lambda k: k["time"])
            if not kfs:
                continue
            k0 = max([k for k in kfs if k["time"] <= time], key=lambda k: k["time"], default=kfs[0])
            k1 = min([k for k in kfs if k["time"] >= time], key=lambda k: k["time"], default=kfs[-1])
            f = 0.0 if k1["time"] == k0["time"] else (time - k0["time"]) / (k1["time"] - k0["time"])
            v = []
            for ax in "xyz":
                a0, a1 = float(k0["data_points"][0][ax]), float(k1["data_points"][0][ax])
                v.append(math.radians(a0 + (a1 - a0) * f) if chan == "rot" else a0 + (a1 - a0) * f)
            if chan == "rot":
                rot = v
            else:
                pos = v
        out[uuid] = (rot, pos)
    return out


BONE_POSE = {}


def build_bones(view_quads):
    nodes = {}

    def walk(n, parent_uuid):
        nodes[n["uuid"]] = (n, parent_uuid)
        for c in n.get("children", []):
            if isinstance(c, dict):
                walk(c, n["uuid"])
            else:
                nodes.setdefault(c, (None, n["uuid"]))   # plain cube reference

    for root in M["outliner"]:
        if isinstance(root, dict):
            walk(root, None)

    def transform(uuid):
        if uuid in BONE_POSE:
            return BONE_POSE[uuid]
        node, parent = nodes.get(uuid, (None, None))
        o = (node or {}).get("origin", [0, 0, 0])
        rot = list(map(math.radians, (node or {}).get("rotation", [0, 0, 0])))
        ov, pv = BONE_POSE_ROT.get(uuid, (None, None))
        if ov is not None:
            rot = ov                       # animation overrides the base rotation
        m = euler(*rot)
        # local(p) = m(p - o) + o + pos  ->  local translation t = o - m·o + pos
        mo = apply(m, [0, 0, 0], o)
        t = [o[i] - mo[i] + (pv[i] if pv else 0) for i in range(3)]
        if parent:
            pm, poff = transform(parent)
            mat = mul(pm, m)
            off = [sum(pm[i * 3 + k] * t[k] for k in range(3)) + poff[i] for i in range(3)]
        else:
            mat, off = m, t
        BONE_POSE[uuid] = (mat, off)
        return BONE_POSE[uuid]

    for e in M["elements"]:
        entry = nodes.get(e["uuid"])
        owner = entry[1] if entry else None      # walk() stored cube uuid -> (None, bone uuid)
        mat, off = transform(owner) if owner else ([1, 0, 0, 0, 1, 0, 0, 0, 1], [0, 0, 0])
        a, b = e["from"], e["to"]

        def colour(face):
            u, v = e["faces"][face]["uv"][:2]
            if SKIN:   # painted skin (dragon): average the island's texels
                u2, v2 = e["faces"][face]["uv"][2:]
                t = [SKIN[0][y][x] for y in range(int(v), int(v2)) for x in range(int(u), int(u2))]
                return tuple(sum(c[i] for c in t) // len(t) for i in range(3))
            return PALETTE[NAMES[(int(v) // 8) * 16 + int(u) // 8]]

        corners = [(x, y, z) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
        pts = [apply(mat, off, p) for p in corners]
        idx = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]
        for q, fname in zip(idx, ("down", "up", "north", "south", "west", "east")):
            poly = [pts[i] for i in q]
            u = [poly[1][i] - poly[0][i] for i in range(3)]
            v = [poly[2][i] - poly[0][i] for i in range(3)]
            n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            ln = math.sqrt(sum(k * k for k in n)) or 1
            LIGHT = (0.4, 0.8, 0.45)
            sh = abs(sum(n[i] / ln * LIGHT[i] for i in range(3)))
            base = colour(fname)
            c = tuple(int(min(255, ch * (0.45 + 0.6 * sh))) for ch in base)
            view_quads.append((poly, c))


def render(spec):
    view, clip, time = spec[0], spec[1], float(spec[2] or 0)
    BONE_POSE_ROT.clear(); BONE_POSE.clear()
    BONE_POSE_ROT.update(sample(clip, time))
    quads = []
    build_bones(quads)
    yaw, pitch = math.radians(YAWS.get(view, 45)), math.radians(PITCH.get(view, 24))
    W = cell; H = cell
    lo = min(min(p) for q, _ in quads for p in q)
    hi = max(max(p) for q, _ in quads for p in q)
    span = max(hi - lo, 8)

    def proj(p):
        x, y, z = p
        x1, z1 = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        y1, z2 = y * math.cos(pitch) - z1 * math.sin(pitch), y * math.sin(pitch) + z1 * math.cos(pitch)
        return x1, y1, z2

    scr = []
    xs = [p[0] for poly, _ in quads for p in poly]
    ys = [p[1] for poly, _ in quads for p in poly]
    cxx, cyy = (min(xs) + max(xs)) / 2.0, (min(ys) + max(ys)) / 2.0
    spread = max(max(xs) - min(xs), max(ys) - min(ys), 8)
    sc = cell / (spread * 1.15)
    for poly, c in quads:
        vp = [proj(p) for p in poly]
        sx = [cell / 2 + (x - cxx) * sc for x, _y, _z in vp]
        sy = [cell / 2 - (y - cyy) * sc for _x, y, _z in vp]
        dz = sum(z for _x, _y, z in vp) / 4
        scr.append((dz, list(zip(sx, sy)), c))

    pixels = {}
    for _dz, poly, c in sorted(scr, key=lambda t: -t[0]):
        pts = poly
        ys = [p[1] for p in pts]
        for py in range(max(0, int(min(ys))), min(H, int(max(ys)) + 1)):
            xs = []
            for i in range(len(pts)):
                (x1, y1), (x2, y2) = pts[i], pts[i - 1]
                if (y1 > py + .5) != (y2 > py + .5):
                    xs.append(x1 + (py + .5 - y1) * (x2 - x1) / (y2 - y1))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                for px in range(max(0, int(xs[i])), min(W, int(xs[i + 1]) + 1)):
                    pixels[(py, px)] = c
    return [[pixels.get((r, c), (214, 226, 236)) for c in range(W)] for r in range(H)]


BONE_POSE_ROT = {}
tiles = [render(s) for s in specs]
cols = min(4, len(tiles)); rows = (len(tiles) + cols - 1) // cols
W, H = cols * cell, rows * cell
img = [[(214, 226, 236)] * W for _ in range(H)]
for i, t in enumerate(tiles):
    ox, oy = (i % cols) * cell, (i // cols) * cell
    for r in range(cell):
        for c in range(cell):
            img[oy + r][ox + c] = t[r][c]
raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in img)
ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
open(out, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0)) + ch(b"IDAT", zlib.compress(raw)) + ch(b"IEND", b""))
print("wrote", out, "with", len(tiles), "tiles")
