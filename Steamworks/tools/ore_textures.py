"""Steamworks ore textures: 16x16 pixel art in the vanilla style, drawn by us, plus labPBR 1.3 maps for shaders.

Per ore it returns three 16x16 RGBA images:
  albedo  <name>.png    the colour texture (what you see without shaders)
  normal  <name>_n.png  labPBR normal: R,G = normal XY, B = ambient occlusion (255 = none), A = height (for parallax)
  spec    <name>_s.png  labPBR specular: R = perceptual smoothness, G = F0 (255 = metal using the albedo as F0),
                        B = porosity (0-64), A = emissive (0-254, 255 = none)
Complementary Shaders (Unbound / Reimagined) read this format when "labPBR" is selected in the shader's material settings.

Everything is generated from seeded noise that wraps around the edges, so blocks tile without visible seams.
"""
import math, random

N = 16


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


# ------------------------------------------------------------------ tiling noise

def lattice(rnd, cells):
    return [[rnd.random() for _ in range(cells)] for _ in range(cells)]


def smooth(t):
    return t * t * (3 - 2 * t)


def value_noise(grid, x, y):
    c = len(grid)
    gx, gy = x / N * c, y / N * c
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = smooth(gx - x0), smooth(gy - y0)
    a, b = grid[y0 % c][x0 % c], grid[y0 % c][(x0 + 1) % c]
    d, e = grid[(y0 + 1) % c][x0 % c], grid[(y0 + 1) % c][(x0 + 1) % c]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (d * (1 - fx) + e * fx) * fy


def fractal(rnd, x, y, grids):
    total, amp, norm = 0.0, 1.0, 0.0
    for g in grids:
        total += value_noise(g, x, y) * amp
        norm += amp
        amp *= 0.55
    return total / norm


# ------------------------------------------------------------------ host rock (height 0..1, base colour, crevice 0..1)

def host_stone(rnd):
    grids = [lattice(rnd, 2), lattice(rnd, 4), lattice(rnd, 8)]
    h = [[fractal(rnd, x, y, grids) for x in range(N)] for y in range(N)]
    # a few hairline cracks (darker, lower)
    crack = [[0.0] * N for _ in range(N)]
    for _ in range(2):
        x, y = rnd.randrange(N), rnd.randrange(N)
        dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (1, -1)])
        for _ in range(rnd.randint(4, 7)):
            crack[y % N][x % N] = 1.0
            x += dx + rnd.choice([0, 0, 1, -1]) * (1 if dx == 0 else 0)
            y += dy + rnd.choice([0, 0, 1, -1]) * (1 if dy == 0 else 0)
    return h, crack


def stone_colour(v, crack, rnd):
    ramp = [(92, 92, 96), (112, 112, 114), (128, 127, 126), (143, 142, 139), (158, 156, 152)]
    i = min(4, max(0, int(v * 5.4 - 0.2)))
    r, g, b = ramp[i]
    if crack:
        r, g, b = r * 0.72, g * 0.72, b * 0.74
    j = rnd.randint(-3, 3)
    return (clamp(r + j), clamp(g + j), clamp(b + j))


def deepslate_colour(v, y, crack, rnd):
    ramp = [(42, 42, 48), (54, 54, 60), (66, 66, 72), (80, 80, 86), (96, 96, 102)]
    band = 0.16 * math.sin((y + v * 2.2) * 1.9)       # horizontal strata, bent by the noise
    i = min(4, max(0, int((v + band) * 5.2 - 0.1)))
    r, g, b = ramp[i]
    if crack:
        r, g, b = r * 0.7, g * 0.7, b * 0.72
    j = rnd.randint(-2, 2)
    return (clamp(r + j), clamp(g + j), clamp(b + j))


def netherrack_colour(v, y, crack, rnd):
    ramp = [(78, 28, 30), (98, 36, 36), (116, 44, 42), (134, 54, 48), (150, 66, 56)]
    ridge = abs(v - 0.5) * 2                              # veiny ridges
    i = min(4, max(0, int((1 - ridge) * 5.2 - 0.3)))
    r, g, b = ramp[i]
    if crack:
        r, g, b = r * 0.65, g * 0.6, b * 0.6
    j = rnd.randint(-3, 3)
    return (clamp(r + j), clamp(g + j * 0.5), clamp(b + j * 0.5))


# ------------------------------------------------------------------ ore clusters

def blob(rnd, cx, cy, size, crystal):
    """Cells of one ore chunk: grows outwards from a seed cell, so it stays compact (crystals lean along a diagonal)."""
    cells = [(cx, cy)]
    steps = [(1, 0), (0, 1), (-1, 0), (0, -1)]
    if crystal:
        steps += [(1, -1), (-1, 1), (1, -1), (-1, 1)]
    while len(cells) < size:
        x, y = rnd.choice(cells)
        dx, dy = rnd.choice(steps)
        q = ((x + dx) % N, (y + dy) % N)
        if q not in cells:
            cells.append(q)
    return set(cells)


def draw_ore(px, height, ao, spec, rnd, tones, mineral):
    """Paints the ore chunks into the albedo and fills the PBR data. tones = dark, mid, light, spark."""
    dark, mid, light, spark = tones
    crystal = mineral["crystal"]
    taken = set()
    centres = []
    tries = 0
    while len(centres) < mineral["chunks"] and tries < 200:
        tries += 1
        c = (rnd.randrange(N), rnd.randrange(N))
        if all(min(abs(c[0] - o[0]), N - abs(c[0] - o[0])) + min(abs(c[1] - o[1]), N - abs(c[1] - o[1])) >= 5 for o in centres):
            centres.append(c)
    chunks = []
    for c in centres:
        cells = blob(rnd, c[0], c[1], rnd.randint(*mineral["size"]), crystal)
        chunks.append(cells)
        taken |= cells
    for cells in chunks:
        # shadow ring on the rock to the lower right
        for (x, y) in cells:
            for ox, oy in ((1, 0), (0, 1), (1, 1)):
                q = ((x + ox) % N, (y + oy) % N)
                if q not in taken and q not in cells:
                    r, g, b = px[q[1]][q[0]][:3]
                    px[q[1]][q[0]] = (clamp(r * 0.62), clamp(g * 0.62), clamp(b * 0.64))
                    ao[q[1]][q[0]] = min(ao[q[1]][q[0]], 0.62)
        ys = [c[1] for c in cells]
        for (x, y) in cells:
            up = ((x) % N, (y - 1) % N) not in cells
            left = ((x - 1) % N, y % N) not in cells
            down = (x % N, (y + 1) % N) not in cells
            right = ((x + 1) % N, y % N) not in cells
            if down or right:
                col, hv = dark, 0.62
            elif up or left:
                col, hv = light, 0.95
            else:
                col, hv = (mid if rnd.random() < 0.72 else light), 0.84
            px[y][x] = col
            height[y][x] = hv + rnd.uniform(-0.03, 0.03)
            ao[y][x] = 1.0
            if mineral["metal"]:
                spec[y][x] = (150 if col != dark else 110, 255, 0, 255)       # rough metal, albedo = reflectance
            else:
                spec[y][x] = (200 if col != dark else 150, mineral["f0"], 0, 255)
        # one bright glint per chunk (and a second on big ones)
        pick = sorted(cells, key=lambda p: (p[1], p[0]))
        gx, gy = pick[0]
        glints = [(gx, gy)] + ([pick[len(pick) // 2]] if len(cells) > 6 else [])
        for gx, gy in glints:
            px[gy][gx] = spark
            height[gy][gx] = 1.0
            sm = 240 if mineral["metal"] else 235
            spec[gy][gx] = (sm, 255 if mineral["metal"] else mineral["f0"] + 30, 0, mineral["glow"] if mineral["glow"] else 255)
        if mineral["glow"]:
            for (x, y) in cells:
                if px[y][x] == light:
                    spec[y][x] = spec[y][x][:3] + (max(0, mineral["glow"] - 25),)


# ------------------------------------------------------------------ the three maps

def make(kind, tones, mineral, seed):
    rnd = random.Random(seed)
    h, crack = host_stone(rnd)
    px = [[None] * N for _ in range(N)]
    height = [[0.0] * N for _ in range(N)]
    ao = [[1.0] * N for _ in range(N)]
    spec = [[None] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            c = crack[y][x] > 0
            if kind == "stone":
                px[y][x] = stone_colour(h[y][x], c, rnd)
                smooth_v, f0 = 38, 10
            elif kind == "deepslate":
                px[y][x] = deepslate_colour(h[y][x], y, c, rnd)
                smooth_v, f0 = 60, 14
            else:
                px[y][x] = netherrack_colour(h[y][x], y, c, rnd)
                smooth_v, f0 = 22, 8
            height[y][x] = 0.28 + 0.42 * h[y][x] - (0.22 if c else 0)
            ao[y][x] = 0.7 if c else 1.0
            spec[y][x] = (smooth_v, f0, 48, 255)
    draw_ore(px, height, ao, spec, rnd, tones, mineral)

    albedo = [[px[y][x] + (255,) for x in range(N)] for y in range(N)]
    normal = [[None] * N for _ in range(N)]
    strength = 3.2
    for y in range(N):
        for x in range(N):
            dx = height[y][(x + 1) % N] - height[y][(x - 1) % N]
            dy = height[(y + 1) % N][x] - height[(y - 1) % N][x]
            nx, ny, nz = -dx * strength, dy * strength, 1.0
            ln = math.sqrt(nx * nx + ny * ny + nz * nz)
            nx, ny = nx / ln, ny / ln
            normal[y][x] = (clamp(128 + nx * 127), clamp(128 + ny * 127), clamp(255 * ao[y][x]), clamp(40 + height[y][x] * 210, 0, 254))
    specular = [[tuple(spec[y][x]) for x in range(N)] for y in range(N)]
    return albedo, normal, specular


# Colour tones (dark, mid, light, spark) and how each mineral behaves under shaders
ZINC = ((66, 88, 112), (122, 156, 182), (184, 214, 232), (238, 250, 255))
TIN = ((112, 108, 98), (192, 188, 172), (234, 230, 214), (255, 255, 250))
SULFUR = ((150, 118, 18), (226, 196, 46), (252, 236, 98), (255, 252, 196))
NETHER_SULFUR = ((166, 124, 16), (238, 204, 52), (255, 238, 104), (255, 252, 190))

MINERALS = {
    "zinc": dict(tones=ZINC, chunks=4, size=(8, 12), crystal=False, metal=True, f0=0, glow=0),
    "tin": dict(tones=TIN, chunks=4, size=(8, 12), crystal=False, metal=True, f0=0, glow=0),
    "sulfur": dict(tones=SULFUR, chunks=4, size=(7, 11), crystal=True, metal=False, f0=40, glow=48),
    "nether_sulfur": dict(tones=NETHER_SULFUR, chunks=5, size=(6, 9), crystal=True, metal=False, f0=40, glow=96),
}


def ore(kind, mineral_name, seed):
    m = MINERALS[mineral_name]
    return make(kind, m["tones"], m, seed)
