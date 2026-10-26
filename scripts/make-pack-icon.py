"""Draws the resource pack icon (128x128 pack.png): a brass gear with a sakura blossom in the middle on dark charcoal,
the steampunk + sakura look of the server. Pure Python (own drawing, no assets). Output: scripts/pack-icon.png,
which scripts/merge-packs.py puts into Iekcorbar-pack.zip as pack.png.

Usage (repo root): python -I scripts/make-pack-icon.py
"""
import math, os, struct, zlib

N, SS = 128, 3   # size, supersampling


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b"")


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sample(x, y):
    """Colour at (x, y) in 0..1 coordinates."""
    dx, dy = x - 0.5, y - 0.5
    r = math.hypot(dx, dy)
    a = math.atan2(dy, dx)
    bg = mix((30, 28, 34), (52, 44, 50), min(1.0, r * 1.6))
    # gear: body radius with 12 teeth
    teeth = 12
    tooth = 0.5 + 0.5 * math.cos(a * teeth)
    outer = 0.34 + 0.07 * (1 if tooth > 0.45 else 0)
    if r < outer and r > 0.155:
        shade = 0.55 + 0.45 * (0.5 - (dx * 0.6 + dy * 0.8))          # light from the top left
        base = (214, 170, 72) if r > 0.2 else (178, 132, 52)
        rim = r > outer - 0.025
        col = mix((120, 84, 30), base, max(0.0, min(1.0, shade)))
        if rim:
            col = mix(col, (250, 214, 120), 0.35)
        if 0.2 < r < 0.215:
            col = mix(col, (96, 66, 24), 0.7)                        # groove between rim and hub
        return col
    if r <= 0.155:
        # sakura blossom: five rounded petals with a notch, centre dots
        pa = (a + math.pi / 2) % (2 * math.pi / 5) - math.pi / 5
        petal = 0.145 - 0.035 * (1 - math.cos(pa * 5)) / 2 - 0.02 * math.exp(-(pa * 14) ** 2)
        if r < petal + 0.02:
            t = r / 0.15
            col = mix((255, 214, 226), (236, 124, 160), t)
            if r < 0.045:
                col = (252, 208, 90)
            return col
        return (60, 46, 52)
    return bg


def main():
    px = []
    for j in range(N):
        row = []
        for i in range(N):
            acc = [0, 0, 0]
            for sj in range(SS):
                for si in range(SS):
                    c = sample((i + (si + 0.5) / SS) / N, (j + (sj + 0.5) / SS) / N)
                    for k in range(3):
                        acc[k] += c[k]
            row.append(tuple(int(v / (SS * SS)) for v in acc) + (255,))
        px.append(row)
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pack-icon.png")
    open(out, "wb").write(png(px))
    print("wrote", out)


if __name__ == "__main__":
    main()
