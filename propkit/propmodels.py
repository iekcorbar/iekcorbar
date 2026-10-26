"""Helpers for the prop resource packs (zoo, garden, kitchen decor): low-poly element models coloured from one
palette texture (own design, no copied assets). Used by Wildlife/tools/make_zoo_pack.py and friends.

    pack = Pack("zoo", out_folder, PALETTE)
    pack.model("bench", [box(...), ...])
    pack.fence("zoo_fence", post_elements, rail_fn)   # 16 connection masks
    pack.finish()          # pack.mcmeta, palette.png, zip
"""
import json, os, random, shutil, struct, zipfile, zlib


def png(px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def clamp(v):
    return max(0, min(255, int(v)))


class Pack:
    def __init__(self, ns, out, palette, description="Props"):
        self.ns = ns
        self.palette = dict(palette)
        self.names = list(self.palette)
        assert len(self.names) <= 256
        self.root = os.path.join(out, ns.capitalize() + "-pack")
        self.zip = os.path.join(out, ns.capitalize() + "-pack.zip")
        self.description = description
        shutil.rmtree(self.root, ignore_errors=True)
        self.models = []

    # ---------------------------------------------------------------- files
    def write(self, rel, data):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if isinstance(data, bytes):
            open(path, "wb").write(data)
        else:
            open(path, "w", encoding="utf-8").write(json.dumps(data, indent=1))

    def uv(self, col):
        i = self.names.index(col)
        u, v = i % 16, i // 16
        return [u + 0.15, v + 0.15, u + 0.85, v + 0.85]

    def box(self, x1, y1, z1, x2, y2, z2, col, rot=None, top=None, side=None):
        """One element. `top` colours the up face, `side` the four sides differently. rot = (axis, angle, origin)."""
        faces = {f: {"uv": self.uv(col), "texture": "#p"} for f in ("north", "south", "east", "west", "up", "down")}
        if top:
            faces["up"] = {"uv": self.uv(top), "texture": "#p"}
        if side:
            for f in ("north", "south", "east", "west"):
                faces[f] = {"uv": self.uv(side), "texture": "#p"}
        e = {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}
        if rot:
            e["rotation"] = {"axis": rot[0], "angle": rot[1], "origin": list(rot[2])}
        return e

    # ---------------------------------------------------------------- models
    def model(self, name, elems, item=True):
        lo = [min(e["from"][i] for e in elems) for i in range(3)]
        hi = [max(e["to"][i] for e in elems) for i in range(3)]
        size = max(hi[i] - lo[i] for i in range(3))
        s = round(min(1.4, 0.625 * 16 / max(size, 1)), 3)
        ty = round((8 - (lo[1] + hi[1]) / 2) * s * 0.9, 2)
        tx = round((8 - (lo[0] + hi[0]) / 2) * s * 0.3, 2)
        m = {"textures": {"p": f"{self.ns}:item/palette", "particle": f"{self.ns}:item/palette"}, "elements": elems}
        if item:
            m["display"] = {
                "gui": {"rotation": [30, 225, 0], "translation": [tx, ty, 0], "scale": [s, s, s]},
                "ground": {"translation": [0, 2, 0], "scale": [s * 0.5] * 3},
                "fixed": {"translation": [0, ty * 0.6, 0], "scale": [s * 0.8] * 3},
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [s * 0.45] * 3},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, ty * 0.3, 0], "scale": [s * 0.5] * 3},
                "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, ty * 0.3, 0], "scale": [s * 0.5] * 3},
                "head": {"scale": [s] * 3},
            }
        self.write(f"assets/{self.ns}/models/item/{name}.json", m)
        self.write(f"assets/{self.ns}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{self.ns}:item/{name}"}})
        self.models.append(name)

    def fence(self, name, post, rail_fn):
        """16 models name_<mask>: post always, rail_fn(direction) -> elements for N(0)/E(1)/S(2)/W(3)."""
        for mask in range(16):
            es = list(post)
            for d in range(4):
                if mask & (1 << d):
                    es += rail_fn(d)
            self.model(f"{name}_{mask}", es, item=(mask == 0))

    # ---------------------------------------------------------------- finish
    def palette_png(self):
        rnd = random.Random(11)
        px = [[(0, 0, 0, 255)] * 128 for _ in range(128)]
        for i, n in enumerate(self.names):
            r, g, b = self.palette[n][:3]
            a = self.palette[n][3] if len(self.palette[n]) > 3 else 255
            cx, cy = (i % 16) * 8, (i // 16) * 8
            for y in range(8):
                for x in range(8):
                    d = rnd.randint(-7, 7)
                    px[cy + y][cx + x] = (clamp(r + d), clamp(g + d), clamp(b + d), a)
        return png(px)

    def finish(self):
        self.write("pack.mcmeta", {"pack": {"description": self.description, "pack_format": 64,
                                            "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}, "min_format": 1, "max_format": 999}})
        self.write(f"assets/{self.ns}/textures/item/palette.png", self.palette_png())
        if os.path.exists(self.zip):
            os.remove(self.zip)
        with zipfile.ZipFile(self.zip, "w", zipfile.ZIP_DEFLATED) as z:
            for base, _, files in os.walk(self.root):
                for f in sorted(files):
                    full = os.path.join(base, f)
                    z.write(full, os.path.relpath(full, self.root).replace("\\", "/"))
        print("wrote", self.zip, len(self.models), "models")
