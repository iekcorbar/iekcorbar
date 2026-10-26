# Pure-Python isometric preview of the Satchel models (painter's algorithm), colours from make_pack's palette.
# usage: python preview3d.py out.png cell model1.json [model2.json ...]
import json, math, os, struct, sys, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_pack import PALETTE, NAMES  # noqa: E402

out, cell = sys.argv[1], int(sys.argv[2])
files = sys.argv[3:]
cols = min(8, len(files)); rows = (len(files) + cols - 1) // cols
W, H = cell * cols, cell * rows
img = [[(214, 226, 236)] * W for _ in range(H)]
LIGHT = (0.4, 0.8, 0.45)


def rot(p, axis, a, o):
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if axis == "x": y, z = y * c - z * s, y * s + z * c
    elif axis == "y": x, z = x * c + z * s, -x * s + z * c
    else: x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def view(p):  # yaw 225 (looking from +x+z corner... ) then pitch 30
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    a = math.radians(45)
    x, z = x * math.cos(a) - z * math.sin(a), x * math.sin(a) + z * math.cos(a)
    b = math.radians(30)
    y, z = y * math.cos(b) - z * math.sin(b), y * math.sin(b) + z * math.cos(b)
    return x, y, z


def fill(poly, color, ox, oy, scale):
    pts = [(ox + cell / 2 + x * scale, oy + cell / 2 - y * scale) for x, y in poly]
    ys = [p[1] for p in pts]
    for py in range(max(oy, int(min(ys))), min(oy + cell, int(max(ys)) + 1)):
        xs = []
        for i in range(len(pts)):
            (x1, y1), (x2, y2) = pts[i], pts[i - 1]
            if (y1 > py + .5) != (y2 > py + .5):
                xs.append(x1 + (py + .5 - y1) * (x2 - x1) / (y2 - y1))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            for px in range(max(ox, int(xs[i])), min(ox + cell, int(xs[i + 1]) + 1)):
                img[py][px] = color


for idx, f in enumerate(files):
    m = json.load(open(f, encoding="utf-8"))
    ox, oy = (idx % cols) * cell, (idx // cols) * cell
    quads = []
    for e in m["elements"]:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        def colour(face):
            u, v = e["faces"][face]["uv"][:2]
            return PALETTE[NAMES[int(v) * 16 + int(u)]]
        faces = [((x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1)), ((x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)),
                 ((x1, y1, z1), (x1, y2, z1), (x1, y2, z2), (x1, y1, z2)), ((x2, y1, z1), (x2, y2, z1), (x2, y2, z2), (x2, y1, z2)),
                 ((x1, y1, z1), (x2, y1, z1), (x2, y1, z2), (x1, y1, z2)), ((x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2))]
        r = e.get("rotation")
        for q, fname in zip(faces, ("north", "south", "west", "east", "down", "up")):
            base = colour(fname)
            if r:
                q = [rot(p, r["axis"], math.radians(r["angle"]), r["origin"]) for p in q]
            # normal for shading
            a, b, c = q[0], q[1], q[2]
            u = [b[i] - a[i] for i in range(3)]; v = [c[i] - a[i] for i in range(3)]
            n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            ln = math.sqrt(sum(k * k for k in n)) or 1
            sh = abs(sum(n[i] / ln * LIGHT[i] for i in range(3)))
            col = tuple(int(min(255, ch * (0.45 + 0.6 * sh))) for ch in base)
            vq = [view(p) for p in q]
            quads.append((sum(p[2] for p in vq) / 4, [(p[0], p[1]) for p in vq], col))
    lo = min(min(e["from"]) for e in m["elements"]); hi = max(max(e["to"]) for e in m["elements"])
    scale = cell / (max(hi - lo, 4) * 1.9)
    for _, poly, col in sorted(quads, key=lambda t: t[0]):
        fill(poly, col, ox, oy, scale)

BG = (214, 226, 236)
if os.environ.get("TRANSPARENT"):   # PNG with alpha for the website
    raw = b"".join(b"\x00" + bytes(v for p in row for v in ((p + (255,)) if p != BG else (0, 0, 0, 0))) for row in img)
    ctype = 6
else:
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in img)
    ctype = 2
ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
open(out, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, ctype, 0, 0, 0)) + ch(b"IDAT", zlib.compress(raw)) + ch(b"IEND", b""))
