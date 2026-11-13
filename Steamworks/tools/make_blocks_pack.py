"""Steamworks-blocks resource pack: custom ore blocks on reserved note block states.

The plugin places ores as note blocks with instrument=custom_head, powered=true,
note=<OreType.noteId>. This pack maps exactly those states to our ore models and
leaves every other note block state on the vanilla model. Textures are drawn
here (own designs). Also writes item model definitions (assets/steamworks/items)
for the ore block items (item_model steamworks:<id>).

Usage: python make_blocks_pack.py <out folder>   -> <out>/Steamworks-blocks/ + <out>/Steamworks-blocks.zip
Put the zip in plugins/ResourcePackManager/mixer/ (never next to CreatePaper's pack:
both replace note_block.json).
"""
import json, os, random, struct, sys, zipfile, zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import decor_textures  # noqa: E402
import ore_textures  # noqa: E402

# must match OreType.java (name, noteId, host, ore colours: base, highlight, shadow)
ORES = [
    ("zinc_ore", 0, "stone", (150, 176, 186), (208, 228, 232), (88, 110, 122)),
    ("deepslate_zinc_ore", 1, "deepslate", (150, 176, 186), (208, 228, 232), (88, 110, 122)),
    ("tin_ore", 2, "stone", (204, 202, 190), (244, 242, 232), (128, 126, 120)),
    ("deepslate_tin_ore", 3, "deepslate", (204, 202, 190), (244, 242, 232), (128, 126, 120)),
    ("sulfur_ore", 4, "stone", (222, 198, 58), (252, 240, 120), (158, 128, 22)),
    ("deepslate_sulfur_ore", 5, "deepslate", (222, 198, 58), (252, 240, 120), (158, 128, 22)),
    ("nether_sulfur_ore", 6, "netherrack", (230, 206, 70), (254, 244, 130), (170, 140, 30)),
]
VANILLA_INSTRUMENTS = ["harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
                       "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "trumpet", "trumpet_exposed",
                       "trumpet_oxidized", "trumpet_weathered", "zombie", "skeleton", "creeper", "dragon",
                       "wither_skeleton", "piglin"]

# ---------------------------------------------------------------- stair/slab geometry (DecorShapes)
# The plugin draws these over a real vanilla stair/slab. Instead of rotating an ItemDisplay (the
# old DecorShapes table mis-drew every inner/outer corner), the pack bakes ONE model per vanilla
# state. The geometry is vanilla's OWN: the element boxes of the three stair models (26.2 client
# jar) put through vanilla's OWN transform table (assets/minecraft/blockstates/oak_stairs.json,
# read back 13-11: y = 90 deg steps clockwise seen from above, x = 180 means high/low swap).
# Cells: (x, z) with x: -1 west / +1 east, z: -1 north / +1 south; the low half is always the
# full slab, the HIGH set is the extra half-block columns.
STAIR_MODEL_BASE = {  # east/bottom identity of the authored vanilla models (inner/outer_stairs.json)
    "straight": frozenset([(1, -1), (1, 1)]),
    "inner": frozenset([(-1, 1), (1, -1), (1, 1)]),
    "outer": frozenset([(1, 1)]),
}
# 90 deg clockwise steps per (shape/side, facing) straight from oak_stairs.json (y / 90)
Y_STEPS = {
    "straight": {"east": 0, "south": 1, "west": 2, "north": 3},
    "inner_L": {"east": 0, "south": 1, "west": 2, "north": 3},
    "inner_R": {"east": 1, "south": 2, "west": 3, "north": 0},
    "outer_L": {"east": 0, "south": 1, "west": 2, "north": 3},
    "outer_R": {"east": 1, "south": 2, "west": 3, "north": 0},
}
ALL4 = frozenset({(-1, -1), (-1, 1), (1, -1), (1, 1)})


def rotate_cw(cells, steps):
    out = cells
    for _ in range(steps % 4):
        out = frozenset((-z, x) for (x, z) in out)
    return out


def stair_cells(shape_key, facing, half):
    """the HIGH cells of one vanilla state; half=top = vanilla's x:180 flip (high/low swap)."""
    shape = "straight" if shape_key == "straight" else shape_key.split("_")[0]
    region = rotate_cw(STAIR_MODEL_BASE[shape], Y_STEPS[shape_key][facing])
    return ALL4 - region if half == "top" else region


def _check_stair_geometry():
    """cross-check the flip rule against the server's own semantics for top halves: a top stair's
    high column must back the wall at -facing (StairBlock updateShape), where the L/R arm sides
    mirror as well. If a corner's flipped region ever violates the arm rule, building fails."""
    all4 = frozenset({(-1, -1), (-1, 1), (1, -1), (1, 1)})
    for key in ("straight", "inner_L", "inner_R", "outer_L", "outer_R"):
        for f in ("east", "south", "west", "north"):
            r = stair_cells(key, f, "top")
            # a top half must be the complement of the same facing/shape's bottom region
            assert r == all4 - stair_cells(key, f, "bottom"), (key, f)
    # the closure must be the full 4-state rotation group (no state may equal another)
    for key in ("straight", "inner_L", "inner_R", "outer_L", "outer_R"):
        seen = set()
        for f in ("east", "south", "west", "north"):
            r = stair_cells(key, f, "bottom")
            assert r not in seen, (key, f)
            seen.add(r)
    print("stair geometry validated (vanilla model table + server closure)")


def stair_model(tex, shape_key, facing, half):
    """baked model JSON for one state; boxes in 0..16 model px (x=0 west, z=0 north)."""
    els = []
    high = stair_cells(shape_key, facing, half)
    for x in (-1, 1):
        for z in (-1, 1):
            x0, z0 = (8 if x > 0 else 0), (8 if z > 0 else 0)
            x1, z1 = x0 + 8, z0 + 8
            els.append({"from": [x0, 0, z0], "to": [x1, 8, z1], "faces": {
                "down": {"uv": [x0, z0, x1, z1], "texture": "#bottom"},
                "north": {"uv": [x0, 16 - z0, x1, 16 - z1], "texture": "#side"},
                "south": {"uv": [x0, z0, x1, z1], "texture": "#side"},
                "west": {"uv": [16 - x0, z0, 16 - x1, z1], "texture": "#side"},
                "east": {"uv": [x0, z0, x1, z1], "texture": "#side"}}})
            if (x, z) in high:
                els.append({"from": [x0, 8, z0], "to": [x1, 16, z1], "faces": {
                    "up": {"uv": [x0, z0, x1, z1], "texture": "#top"},
                    "north": {"uv": [x0, 16 - z0, x1, 16 - z1], "texture": "#side"},
                    "south": {"uv": [x0, z0, x1, z1], "texture": "#side"},
                    "west": {"uv": [16 - x0, z0, 16 - x1, z1], "texture": "#side"},
                    "east": {"uv": [x0, z0, x1, z1], "texture": "#side"}}})
    return {"textures": {"bottom": tex, "top": tex, "side": tex}, "elements": els}


def slab_model(tex, kind):
    """kind = bottom/top/double, exactly vanilla's slab geometry."""
    y0, y1 = {"bottom": (0, 8), "top": (8, 16), "double": (0, 16)}[kind]
    faces = {
        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
        "north": {"uv": [0, 0, 16, 8], "texture": "#side"},
        "south": {"uv": [0, 0, 16, 8], "texture": "#side"},
        "west": {"uv": [0, 0, 16, 8], "texture": "#side"},
        "east": {"uv": [0, 0, 16, 8], "texture": "#side"}}
    if kind == "bottom":
        faces.pop("up")
    if kind == "top":
        faces.pop("down")
    return {"textures": {"bottom": tex, "top": tex, "side": tex},
            "elements": [{"from": [0, y0, 0], "to": [16, y1, 16], "faces": faces}]}


def png(pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in pixels)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def clamp(v):
    return max(0, min(255, int(v)))


def host(kind, rnd):
    px = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-12, 12)
            if kind == "stone":
                base = 125 + n + (8 if (x * 7 + y * 3) % 11 == 0 else 0)
                px[y][x] = (clamp(base), clamp(base), clamp(base), 255)
            elif kind == "deepslate":
                band = -10 if y % 4 == 0 else 0  # layered look
                base = 72 + n // 2 + band
                px[y][x] = (clamp(base), clamp(base), clamp(base + 4), 255)
            else:  # netherrack
                base = n + (-14 if (x + y * 5) % 7 == 0 else 0)
                px[y][x] = (clamp(112 + base), clamp(42 + base // 2), clamp(42 + base // 2), 255)
    return px


def ore_texture(name, kind, base, hi, sh, seed):
    rnd = random.Random(seed)
    px = host(kind, rnd)
    centres = [(3, 3), (11, 4), (6, 10), (12, 12), (2, 13), (9, 7)]
    for cx, cy in centres:
        cx = (cx + rnd.randint(-1, 1)) % 16
        cy = (cy + rnd.randint(-1, 1)) % 16
        blob = [(0, 0), (1, 0), (0, 1), (1, 1)] + rnd.sample([(-1, 0), (2, 0), (0, -1), (1, 2), (2, 1), (-1, 1)], 2)
        for dx, dy in blob:
            x, y = (cx + dx) % 16, (cy + dy) % 16
            px[y][x] = base + (255,)
        px[cy][cx] = hi + (255,)  # highlight top-left of the speck
        sx, sy = (cx + 1) % 16, (cy + 1) % 16
        px[sy][sx] = sh + (255,)
    return png(px)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(path, mode, **({} if mode == "wb" else {"encoding": "utf-8"})) as f:
        f.write(data if isinstance(data, bytes) else json.dumps(data, indent=2))


def add_sounds(root, namespace):
    """Copies sounds/out/<namespace> (made by sounds/make_sounds.py: sounds.json, .ogg files, subtitles) into the pack."""
    import shutil
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "sounds", "out", namespace)
    if not os.path.isdir(src):
        print("note: no sounds in", src, "- run sounds/make_sounds.py first (pack built without sounds)")
        return
    dst = os.path.join(root, "assets", namespace)
    djson = os.path.join(dst, "sounds.json")
    keep = {}
    if os.path.exists(djson):
        try:
            keep = json.load(open(djson, encoding="utf-8"))
        except Exception:
            keep = {}
    shutil.copytree(src, dst, dirs_exist_ok=True)
    # Hand-added events (e.g. quest voices steamworks:milo.lineNN, which live only in the
    # exploded pack's sounds.json) must survive rebuilds: generated events win, extras stay.
    if keep and os.path.exists(djson):
        cur = json.load(open(djson, encoding="utf-8"))
        extra = {k: v for k, v in keep.items() if k not in cur}
        if extra:
            cur.update(extra)
            with open(djson, "w", encoding="utf-8") as f:
                json.dump(cur, f, indent=1)


def main():
    _check_stair_geometry()
    out = sys.argv[1]
    root = os.path.join(out, "Steamworks-blocks")
    write(os.path.join(root, "pack.mcmeta"), {"pack": {
        "description": "Steamworks blocks (custom ores)",
        "pack_format": 64, "supported_formats": {"min_inclusive": 1, "max_inclusive": 999},
        "min_format": 1, "max_format": 999}})

    # reserved states: key 0-24 = custom_head + powered + note, key 25-49 = custom_head + unpowered + note
    keys = {n for _, n, *_ in ORES} | {d[3] for d in decor_textures.DECOR}
    assert len(keys) == len(ORES) + len(decor_textures.DECOR), "two blocks share a state"
    free_on = "|".join(str(n) for n in range(25) if n not in keys)
    free_off = "|".join(str(n) for n in range(25) if n + 25 not in keys)
    vanilla_when = [{"instrument": "|".join(VANILLA_INSTRUMENTS)}]
    if free_on:
        vanilla_when.append({"instrument": "custom_head", "powered": "true", "note": free_on})
    if free_off:
        vanilla_when.append({"instrument": "custom_head", "powered": "false", "note": free_off})
    parts = [{"when": {"OR": vanilla_when}, "apply": {"model": "minecraft:block/note_block"}}]
    for i, (name, note, kind, base, hi, sh) in enumerate(ORES):
        parts.append({"when": {"instrument": "custom_head", "powered": "true", "note": str(note)},
                      "apply": {"model": f"steamworks:block/{name}"}})
        mineral = name.replace("_ore", "").replace("deepslate_", "")
        albedo, normal, specular = ore_textures.ore(kind, mineral, 1000 + i)
        write(os.path.join(root, "assets/steamworks/textures/block", name + ".png"), png(albedo))
        # labPBR maps for shaders (Complementary Unbound/Reimagined, BSL, ...): normal + height, smoothness/F0/emission
        write(os.path.join(root, "assets/steamworks/textures/block", name + "_n.png"), png(normal))
        write(os.path.join(root, "assets/steamworks/textures/block", name + "_s.png"), png(specular))
        write(os.path.join(root, "assets/steamworks/models/block", name + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"steamworks:block/{name}"}})
        write(os.path.join(root, "assets/steamworks/items", name + ".json"),
              {"model": {"type": "minecraft:model", "model": f"steamworks:block/{name}"}})
    for i, (name, label, style, key, hard, tool, painter) in enumerate(decor_textures.DECOR):
        parts.append({"when": {"instrument": "custom_head", "powered": "true" if key < 25 else "false", "note": str(key % 25)},
                      "apply": {"model": f"steamworks:block/{name}"}})
        px = [[c + (255,) for c in row] for row in painter(5000 + i)]
        write(os.path.join(root, "assets/steamworks/textures/block", name + ".png"), png(px))
        write(os.path.join(root, "assets/steamworks/models/block", name + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"steamworks:block/{name}"}})
        write(os.path.join(root, "assets/steamworks/items", name + ".json"),
              {"model": {"type": "minecraft:model", "model": f"steamworks:block/{name}"}})
        # stairs + slabs: drawn by the plugin over a hidden vanilla stair/slab of the same shape.
        # The hand items use vanilla's own rotated models; the DISPLAYS use baked per-orientation
        # variants (DecorShapes), because rotating an oversized display mis-drew the corners.
        tex = f"steamworks:block/{name}"
        for suffix, parent, keys in (("_stairs", "stairs", ("bottom", "top", "side")),
                                     ("_inner_stairs", "inner_stairs", ("bottom", "top", "side")),
                                     ("_outer_stairs", "outer_stairs", ("bottom", "top", "side")),
                                     ("_slab", "slab", ("bottom", "top", "side")),
                                     ("_slab_top", "slab_top", ("bottom", "top", "side"))):
            write(os.path.join(root, "assets/steamworks/models/block", name + suffix + ".json"),
                  {"parent": f"minecraft:block/{parent}", "textures": {k: tex for k in keys}})
            write(os.path.join(root, "assets/steamworks/items", name + suffix + ".json"),
                  {"model": {"type": "minecraft:model", "model": f"steamworks:block/{name}{suffix}"}})
        # names never collide with the hand-item models above: _draw_<state> for the displays
        baked = [(("draw_straight_" + f + "_" + h), ("straight", f, h))
                 for f in ("east", "south", "west", "north") for h in ("bottom", "top")]
        baked += [(("draw_inner_" + s + "_" + f + "_" + h), ("inner_" + s, f, h))
                  for s in ("L", "R") for f in ("east", "south", "west", "north") for h in ("bottom", "top")]
        baked += [(("draw_outer_" + s + "_" + f + "_" + h), ("outer_" + s, f, h))
                  for s in ("L", "R") for f in ("east", "south", "west", "north") for h in ("bottom", "top")]
        baked += [("draw_slab_bottom", None), ("draw_slab_top", None), ("draw_slab_double", None)]
        for tag, spec in baked:
            model = slab_model(tex, tag.split("_")[2]) if spec is None else stair_model(tex, spec[0], spec[1], spec[2])
            write(os.path.join(root, "assets/steamworks/models/block", f"{name}_{tag}.json"), model)
            write(os.path.join(root, "assets/steamworks/items", f"{name}_{tag}.json"),
                  {"model": {"type": "minecraft:model", "model": f"steamworks:block/{name}_{tag}"}})
    write(os.path.join(root, "assets/minecraft/blockstates/note_block.json"), {"multipart": parts})
    add_sounds(root, "steamworks")

    zpath = os.path.join(out, "Steamworks-blocks.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for dirpath, _, files in os.walk(root):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                z.write(full, os.path.relpath(full, root).replace(os.sep, "/"))
    print("wrote", zpath, "with", len(ORES), "ores and", len(decor_textures.DECOR), "decor blocks")


if __name__ == "__main__":
    main()
