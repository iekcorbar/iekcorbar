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
    shutil.copytree(src, os.path.join(root, "assets", namespace), dirs_exist_ok=True)


def main():
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
        # stairs + slabs: drawn by the plugin over a hidden vanilla stair/slab of the same shape
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
