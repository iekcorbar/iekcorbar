"""Steamworks part models for FreeMinecraftModels (own designs, own texture).

Every model is built around the block centre (0,0,0), block = -8..8 on each axis,
with its rotation axis along +Y. The plugin puts it on an ItemDisplay and turns it
to the real axis, then spins it around model Y. Writes <name>.bbmodel (FMM needs
it for the texture) + <name>.json (the item/display model, true scale) into <out>.
Element rotations stay within +-45 deg in 22.5 deg steps on one axis (vanilla rule).
Usage: python make_models.py <out folder>"""
import base64, json, math, os, random, struct, sys, uuid, zlib

RES = 32
# deterministic ids: uuid4 made every regeneration a byte-diff of all 100+ files; uuid5 from a
# counter keeps the git history clean (same source -> same files, always)
import itertools as _it
_c = _it.count()
U = lambda: str(uuid.uuid5(uuid.NAMESPACE_URL, "iekcorbar-steamworks-%d" % next(_c)))

# ---------------------------------------------------------------- texture (32x32, 8x8 swatches)
random.seed(26)
SW = {  # name: (x, y, rgb, noise, dark edge)
    "brass": (0, 0, (190, 152, 64), 12, True), "copper": (8, 0, (182, 106, 52), 12, True),
    "steel": (16, 0, (122, 124, 132), 9, True), "dark": (24, 0, (58, 50, 46), 8, True),
    "wood": (0, 8, (164, 118, 74), 12, True), "darkwood": (8, 8, (92, 60, 38), 10, True),
    "stone": (16, 8, (132, 130, 126), 14, False), "darkstone": (24, 8, (88, 86, 84), 12, True),
    "lacquer": (0, 16, (150, 34, 30), 10, True), "black": (8, 16, (30, 28, 30), 5, False),
    "dial": (16, 16, (236, 228, 204), 4, False), "red": (24, 16, (200, 36, 30), 6, False),
    "verdigris": (0, 24, (88, 150, 128), 12, True), "rope": (8, 24, (196, 170, 120), 10, False),
    "gold": (16, 24, (232, 196, 84), 10, True), "paper": (24, 24, (244, 232, 204), 5, False),
}
px = [[(0, 0, 0, 255)] * RES for _ in range(RES)]
for name, (ox, oy, (r, g, b), noise, edge) in SW.items():
    for y in range(8):
        for x in range(8):
            e = edge and (x in (0, 7) or y in (0, 7))
            n = random.randint(-noise, noise) - (20 if e else 0)
            if name in ("wood", "darkwood") and x % 3 == 0:  # grain
                n -= 14
            px[oy + y][ox + x] = tuple(max(0, min(255, c + n)) for c in (r, g, b)) + (255,)


def png(pixels):
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in pixels)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", RES, RES, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


TEX = "data:image/png;base64," + base64.b64encode(png(px)).decode()


def split(angle):
    base = round(angle / 90.0) * 90
    return base % 360, angle - base


class Model:
    def __init__(self, name, gui=1.0):
        self.name, self.gui, self.elements = name, gui, []

    def cube(self, name, frm, to, mat, rot=(0, 0, 0), origin=None):
        ox, oy = SW[mat][0], SW[mat][1]
        frm, to = [min(a, b) for a, b in zip(frm, to)], [max(a, b) for a, b in zip(frm, to)]
        if origin is None:
            origin = [(a + b) / 2 for a, b in zip(frm, to)]
        self.elements.append({
            "name": name, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
            "allow_mirror_modeling": True, "from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to],
            "autouv": 0, "color": 0, "origin": [round(v, 4) for v in origin], "rotation": list(rot),
            "faces": {f: {"uv": [ox, oy, ox + 8, oy + 8], "texture": 0}
                      for f in ("north", "east", "south", "west", "up", "down")},
            "type": "cube", "uuid": U()})

    def merge(self, other):
        self.elements += [dict(e, uuid=U()) for e in other.elements]
        return self

    def bbmodel(self):
        return {
            "meta": {"format_version": "4.9", "model_format": "free", "box_uv": False},
            "name": self.name, "model_identifier": "", "visible_box": [1, 1, 0],
            "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
            "unhandled_root_fields": {}, "resolution": {"width": RES, "height": RES},
            "elements": self.elements,
            "outliner": [{"name": "body", "origin": [0, 0, 0], "color": 0, "uuid": U(), "export": True,
                          "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True,
                          "autouv": 0, "children": [e["uuid"] for e in self.elements]}],
            "textures": [{"path": "", "name": "steamworks.png", "folder": "", "namespace": "", "id": "0",
                          "width": RES, "height": RES, "uv_width": RES, "uv_height": RES, "particle": False,
                          "render_mode": "default", "render_sides": "auto", "frame_time": 1,
                          "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False,
                          "visible": True, "internal": True, "saved": False, "uuid": U(), "relative_path": "",
                          "source": TEX}],
            "animations": [],
        }

    def item_json(self):
        """True scale (block centre = model 8,8,8) - the display entity shows it 1:1."""
        P = lambda v: [round(c + 8, 4) for c in v]
        out = []
        for e in self.elements:
            axis = next((a for a, r in zip("xyz", e["rotation"]) if r), None)
            el = {"from": P(e["from"]), "to": P(e["to"]),
                  "faces": {f: {"uv": [v * 16 / RES for v in d["uv"]], "texture": "#0"} for f, d in e["faces"].items()}}
            if axis:
                el["rotation"] = {"angle": e["rotation"]["xyz".index(axis)], "axis": axis, "origin": P(e["origin"])}
            out.append(el)
        g = self.gui
        return {"textures": {"0": "0", "particle": "0"}, "elements": out,
                "display": {
                    "gui": {"rotation": [30, 225, 0], "scale": [0.62 * g] * 3},
                    "ground": {"translation": [0, 3, 0], "scale": [0.3 * g] * 3},
                    "fixed": {"scale": [0.6 * g] * 3},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.35 * g] * 3},
                    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.35 * g] * 3},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.4 * g] * 3},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.4 * g] * 3},
                    "head": {"translation": [0, 4, 0], "scale": [0.7 * g] * 3}}}


# ---------------------------------------------------------------- shape helpers (axis = model Y)
# (u, v) = (x, z) across the axis, w = y along it.

def box(m, name, u1, v1, w1, u2, v2, w2, mat, res=0.0):
    m.cube(name, (u1, w1, v1), (u2, w2, v2), mat, (0, res, 0) if res else (0, 0, 0), (0, 0, 0) if res else None)


def radial(m, name, theta, r1, r2, hw, w1, w2, mat):
    """A bar from radius r1 to r2 at angle theta (around Y), half width hw."""
    base, res = split(theta)
    u1, v1, u2, v2 = {0: (-hw, r1, hw, r2), 90: (r1, -hw, r2, hw),
                      180: (-hw, -r2, hw, -r1), 270: (-r2, -hw, -r1, hw)}[base]
    box(m, name, u1, v1, w1, u2, v2, w2, mat, res)


def disk(m, name, r, w1, w2, mat):
    """16-sided disc (8 rotated strips)."""
    h = r * math.tan(math.radians(11.25))
    for k in range(8):
        base, res = split(k * 22.5)
        if base % 180 == 0:
            box(m, f"{name}{k}", -h, -r, w1, h, r, w2, mat, res)
        else:
            box(m, f"{name}{k}", -r, -h, w1, r, h, w2, mat, res)


def rod(m, name, r, w1, w2, mat):
    """Octagonal rod along the axis."""
    h = r * math.tan(math.radians(22.5))
    for k in range(4):
        base, res = split(k * 45)
        if base % 180 == 0:
            box(m, f"{name}{k}", -h, -r, w1, h, r, w2, mat, res)
        else:
            box(m, f"{name}{k}", -r, -h, w1, r, h, w2, mat, res)


def ring(m, name, R, bar, w1, w2, mat, sides=16):
    step = 360 / sides
    h = R * math.tan(math.radians(step / 2)) + 0.15
    for k in range(sides):
        theta = k * step
        base, res = split(theta)
        u1, v1, u2, v2 = {0: (-h, R - bar / 2, h, R + bar / 2), 90: (R - bar / 2, -h, R + bar / 2, h),
                          180: (-h, -R - bar / 2, h, -R + bar / 2), 270: (-R - bar / 2, -h, -R + bar / 2, h)}[base]
        box(m, f"{name}{k}", u1, v1, w1, u2, v2, w2, mat, res)


def axle(m, half=8.0):
    rod(m, "axle", 1.8, -half, half, "steel")
    box(m, "axle_key", 1.55, -0.5, -half, 2.05, 0.5, half, "dark")  # flat key: shows the rotation


# ---------------------------------------------------------------- parts

def shaft():
    m = Model("sw_shaft", 1.3)
    axle(m)
    for w in (-4.0, 4.0):
        rod(m, f"band{w}", 2.15, w - 0.6, w + 0.6, "brass")
    return m


def cogwheel():
    # Japanese wooden cog with brass teeth: pitch radius ~7.75 (meshes with neighbours)
    m = Model("sw_cogwheel", 1.1)
    axle(m)
    disk(m, "disc", 6.4, -1.8, 1.8, "wood")
    ring(m, "rim", 6.3, 1.0, -2.0, 2.0, "darkwood")
    for k in range(8):
        radial(m, f"tooth{k}", k * 45, 6.4, 8.9, 1.25, -1.8, 1.8, "brass")
    rod(m, "hub", 3.0, -2.4, 2.4, "brass")
    return m


def large_cogwheel():
    # spoked wheel, pitch radius ~15, 16 teeth (twice the small one)
    m = Model("sw_large_cogwheel", 0.55)
    axle(m)
    ring(m, "rim", 13.0, 2.2, -1.8, 1.8, "darkwood")
    ring(m, "band", 13.0, 0.8, -2.1, 2.1, "brass")
    for k in range(16):
        radial(m, f"tooth{k}", k * 22.5, 13.8, 16.0, 1.35, -1.8, 1.8, "brass")
    for k in range(8):
        radial(m, f"spoke{k}", k * 45, 3.0, 12.2, 0.9, -1.2, 1.2, "wood")
    rod(m, "hub", 3.6, -2.6, 2.6, "brass")
    return m


def gearbox():
    # ports on the four sides (model X and Z), closed top/bottom (model Y)
    m = Model("sw_gearbox", 1.0)
    box(m, "case", -7.4, -7.4, -7.4, 7.4, 7.4, 7.4, "darkwood")
    for su in (-1, 1):
        for sv in (-1, 1):
            box(m, "post", su * 7.6 - 1, sv * 7.6 - 1, -7.6, su * 7.6 + 1, sv * 7.6 + 1, 7.6, "brass")
    for sw in (-1, 1):
        box(m, "plate", -6.4, -6.4, sw * 7.4 - 0.4, 6.4, 6.4, sw * 7.4 + 0.4, "brass")
        box(m, "plate_in", -4.5, -4.5, sw * 7.8 - 0.1, 4.5, 4.5, sw * 7.8 + 0.1, "lacquer")
    for k, (u, v) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):  # bushings on the port faces
        if u:
            m.cube(f"bush{k}", (u * 7.4, -2.6, -2.6), (u * 8.0, 2.6, 2.6), "steel")
            m.cube(f"bushc{k}", (u * 7.9, -1.2, -1.2), (u * 8.05, 1.2, 1.2), "black")
        else:
            m.cube(f"bush{k}", (-2.6, -2.6, v * 7.4), (2.6, 2.6, v * 8.0), "steel")
            m.cube(f"bushc{k}", (-1.2, -1.2, v * 7.9), (1.2, 1.2, v * 8.05), "black")
    return m


def hand_crank():
    # sticks out of the block behind it (model -Y); handle orbits around Y
    m = Model("sw_hand_crank", 1.2)
    rod(m, "axle", 1.8, -8.0, -2.5, "steel")
    disk(m, "hub", 3.2, -2.8, -0.8, "brass")
    radial(m, "arm", 0, 0.0, 7.0, 1.3, -1.8, -0.4, "darkwood")
    box(m, "pin", -0.7, 5.6, -0.4, 0.7, 7.0, 1.0, "steel")
    box(m, "grip", -1.3, 5.0, 1.0, 1.3, 7.6, 6.5, "lacquer")
    box(m, "cap", -1.5, 4.8, 6.5, 1.5, 7.8, 7.3, "brass")
    return m


def water_wheel():
    # mizuguruma: two dark rims, 8 paddles, diameter ~1.7 blocks
    m = Model("sw_water_wheel", 0.6)
    axle(m)
    rod(m, "hub", 3.0, -5.6, 5.6, "brass")
    for side in (-1, 1):
        w = side * 4.8
        ring(m, f"rim{side}", 11.0, 1.4, w - 0.6, w + 0.6, "darkwood")
        for k in range(8):
            radial(m, f"spoke{side}_{k}", k * 45 + 22.5, 2.6, 10.6, 0.6, w - 0.5, w + 0.5, "darkwood")
    for k in range(8):
        radial(m, f"paddle{k}", k * 45 + 22.5, 6.0, 13.6, 0.6, -5.6, 5.6, "wood")
    return m


GAUGE_DIAL_Z = 5.5


def gauge_body(name, metal):
    # shaft runs along model Y; dials face model +Z and -Z
    m = Model(name, 1.1)
    box(m, "body", -5.0, -GAUGE_DIAL_Z, -5.0, 5.0, GAUGE_DIAL_Z, 5.0, metal)
    box(m, "base", -5.6, -4.0, -5.6, 5.6, 4.0, -4.4, "dark")
    box(m, "top", -5.6, -4.0, 4.4, 5.6, 4.0, 5.6, "dark")
    for s in (-1, 1):
        z0 = s * GAUGE_DIAL_Z
        for k in range(8):  # 16-gon dial face around model Z
            base, res = split(k * 22.5)
            r, h = 4.3, 4.3 * math.tan(math.radians(11.25))
            f, t = ((-h, -r), (h, r)) if base % 180 == 0 else ((-r, -h), (r, h))
            m.cube(f"dial{s}_{k}", (f[0], f[1], min(z0, z0 + s * 0.3)), (t[0], t[1], max(z0, z0 + s * 0.3)),
                   "dial", (0, 0, res) if res else (0, 0, 0), (0, 0, 0))
        for k in range(16):  # bezel
            base, res = split(k * 22.5)
            R, bar = 4.7, 0.8
            hh = R * math.tan(math.radians(11.25)) + 0.15
            u1, v1, u2, v2 = {0: (-hh, R - bar / 2, hh, R + bar / 2), 90: (R - bar / 2, -hh, R + bar / 2, hh),
                              180: (-hh, -R - bar / 2, hh, -R + bar / 2), 270: (-R - bar / 2, -hh, -R + bar / 2, hh)}[base]
            m.cube(f"bezel{s}_{k}", (u1, v1, min(z0, z0 + s * 0.6)), (u2, v2, max(z0, z0 + s * 0.6)),
                   "brass", (0, 0, res) if res else (0, 0, 0), (0, 0, 0))
        m.cube(f"mark{s}", (3.2, -0.4, min(z0, z0 + s * 0.35)), (4.1, 0.4, max(z0, z0 + s * 0.35)), "red")
    return m


def gauge_needle():
    # pointer along +X at angle 0, turning around model Z (the dial normal)
    m = Model("sw_gauge_needle", 1.0)
    for s in (-1, 1):
        z0 = s * (GAUGE_DIAL_Z + 0.35)
        m.cube(f"needle{s}", (-0.9, -0.3, min(z0, z0 + s * 0.3)), (3.8, 0.3, max(z0, z0 + s * 0.3)), "red")
        m.cube(f"cap{s}", (-0.7, -0.7, min(z0, z0 + s * 0.55)), (0.7, 0.7, max(z0, z0 + s * 0.55)), "black")
    return m


def millstone_base():
    m = Model("sw_millstone_base")
    box(m, "base", -8, -8, -8, 8, 8, -1.5, "darkstone")
    box(m, "band", -8.2, -8.2, -6.4, 8.2, 8.2, -4.6, "darkwood")
    box(m, "trough", -7.2, -7.2, -1.5, 7.2, 7.2, -0.8, "stone")
    box(m, "spout", -1.4, 8.0, -4.0, 1.4, 10.0, -2.6, "darkwood")
    return m


def millstone_runner():
    m = Model("sw_millstone_runner")
    disk(m, "stone", 7.0, -0.8, 5.0, "stone")
    rod(m, "eye", 1.6, 5.0, 5.3, "black")
    box(m, "peg", -0.8, 4.4, 5.0, 0.8, 6.0, 8.0, "darkwood")  # shows the turning
    return m


# Steam hammer: frame/head use model X = shaft axis, Y = up (torii-like frame:
# steel posts, red lacquer beams); the cam model uses the normal axis = model Y.

def steam_hammer_frame():
    m = Model("sw_steam_hammer_frame")
    for s in (-1, 1):  # base ring around the opening the head drops through
        m.cube(f"ring_x{s}", (-7.6, -8, s * 4.8), (7.6, -6.6, s * 7.6), "dark")
        m.cube(f"ring_z{s}", (s * 4.8, -8, -4.8), (s * 7.6, -6.6, 4.8), "dark")
    for s in (-1, 1):
        m.cube(f"post{s}", (-2.2, -6.6, s * 5.6), (2.2, 6.2, s * 7.6), "steel")
        for w in (-3.6, 1.4):
            m.cube(f"post_band{s}_{w}", (-2.5, w, s * 5.3), (2.5, w + 0.8, s * 7.9), "brass")
        # hangers carrying the cam shaft
        m.cube(f"hanger{s}", (s * 5.6, -3.2, -3.2), (s * 6.8, 5.0, 3.2), "darkwood")
        m.cube(f"bush{s}", (s * 5.4, -2.6, -2.6), (s * 7.0, 2.6, 2.6), "brass")
    m.cube("beam_x", (-6.8, 5.0, -3.2), (6.8, 6.2, 3.2), "steel")
    m.cube("nuki", (-1.6, 4.0, -7.6), (1.6, 5.0, 7.6), "lacquer")
    m.cube("kasagi", (-2.6, 6.2, -8.0), (2.6, 7.6, 8.0), "lacquer")
    m.cube("kasagi_cap", (-2.8, 7.6, -8.0), (2.8, 8.0, 8.0), "black")
    m.cube("valve", (-1.4, 7.6, -1.4), (1.4, 8.0, 1.4), "copper")
    return m


def steam_hammer_head():
    m = Model("sw_steam_hammer_head")
    m.cube("face", (-4.2, -8.0, -4.7), (4.2, -7.4, 4.7), "dark")
    m.cube("body", (-4.0, -7.4, -4.6), (4.0, -4.6, 4.6), "steel")
    m.cube("band", (-4.3, -6.2, -4.9), (4.3, -5.6, 4.9), "brass")
    for s in (-1, 1):  # guide rods up through the frame (stay visible when the head is down)
        m.cube(f"rod{s}", (-0.7, -4.6, s * 3.6), (0.7, 8.0, s * 4.4), "steel")
    return m


def steam_hammer_cam():
    m = Model("sw_steam_hammer_cam")
    axle(m)
    disk(m, "cam_disc", 2.6, -1.2, 1.2, "brass")
    radial(m, "cam_lobe", 0, 1.0, 3.4, 1.4, -1.0, 1.0, "steel")
    for w in (-4.4, 4.4):
        rod(m, f"collar{w}", 2.2, w - 0.5, w + 0.5, "brass")
    return m


def shaft_half():
    # one half of an axle (model 0..+8) with a coupling disc at the middle; two of them make a split shaft
    m = Model("sw_shaft_half", 1.3)
    rod(m, "axle", 1.8, 0.0, 8.0, "steel")
    box(m, "axle_key", 1.55, -0.5, 0.0, 2.05, 0.5, 8.0, "dark")
    disk(m, "plate", 4.0, 0.3, 1.5, "brass")
    for k in range(4):
        radial(m, f"dog{k}", k * 90, 1.8, 4.2, 0.6, 0.15, 1.7, "dark")  # shows the turning through the window
    return m


def switch_casing(name, metal, frame, lamp_on):
    # split-shaft housing: closed ends with bushings, open window round the middle, lamps on the model +-Z sides
    m = Model(name)
    for s in (-1, 1):
        box(m, f"end{s}", -6.8, -6.8, s * 3.2, 6.8, 6.8, s * 7.4, metal)
        box(m, f"bush{s}", -2.6, -2.6, s * 7.4, 2.6, 2.6, s * 8.0, "steel")
        box(m, f"band{s}", -7.0, -7.0, s * 3.0, 7.0, 7.0, s * 3.6, frame)
    for su in (-1, 1):
        for sv in (-1, 1):
            box(m, f"post{su}{sv}", su * 6.8 - 1.0, sv * 6.8 - 1.0, -3.2, su * 6.8 + 1.0, sv * 6.8 + 1.0, 3.2, frame)
    for s in (-1, 1):
        m.cube(f"lamp{s}", (-1.2, 4.6, s * 6.8), (1.2, 6.2, s * 7.3), "red" if lamp_on else "black")
        m.cube(f"lamp_rim{s}", (-1.6, 4.2, s * 6.7), (1.6, 6.6, s * 6.9), "brass")
    return m


def bellows_fan_casing():
    # blows toward model +Y: copper drum, brass rim, front grille, back plate with bushing
    m = Model("sw_bellows_fan_casing")
    for s in (-1, 1):
        box(m, f"wall_x{s}", s * 6.4, -7.6, -7.6, s * 7.6, 7.6, 6.6, "copper")
        box(m, f"wall_z{s}", -6.4, s * 6.4, -7.6, 6.4, s * 7.6, 6.6, "copper")
    for s in (-1, 1):
        box(m, f"rim_x{s}", s * 6.2, -8.0, 6.6, s * 8.0, 8.0, 8.0, "brass")
        box(m, f"rim_z{s}", -6.2, s * 6.2, 6.6, 6.2, s * 8.0, 8.0, "brass")
    for k, c in enumerate((-3.2, 0.0, 3.2)):
        box(m, f"grille_u{k}", c - 0.4, -6.2, 6.9, c + 0.4, 6.2, 7.5, "dark")
        box(m, f"grille_v{k}", -6.2, c - 0.4, 7.0, 6.2, c + 0.4, 7.6, "dark")
    box(m, "back", -6.4, -6.4, -7.6, 6.4, 6.4, -6.4, "darkwood")
    box(m, "bush", -2.6, -2.6, -8.0, 2.6, 2.6, -7.6, "steel")
    return m


def bellows_fan_rotor():
    # uchiwa-style blades: paper on dark ribs, around model Y
    m = Model("sw_bellows_fan_rotor")
    rod(m, "axle", 1.8, -8.0, 1.0, "steel")
    rod(m, "hub", 2.4, -0.6, 2.2, "brass")
    for k in range(4):
        radial(m, f"blade{k}", k * 90 + 22.5, 2.2, 6.0, 1.9, 0.2, 1.0, "paper")
        radial(m, f"rib{k}", k * 90 + 22.5, 2.2, 6.0, 0.35, 1.0, 1.3, "darkwood")
    return m


def kettle():
    # iron pot on three short legs, open top, brass rim and bail handle
    m = Model("sw_kettle")
    box(m, "floor", -6.4, -6.4, -6.4, 6.4, 6.4, -5.2, "dark")
    for s in (-1, 1):
        box(m, f"wall_x{s}", s * 5.4, -6.4, -5.2, s * 6.6, 6.4, 5.0, "dark")
        box(m, f"wall_z{s}", -5.4, s * 5.4, -5.2, 5.4, s * 6.6, 5.0, "dark")
        box(m, f"rim_x{s}", s * 5.2, -7.0, 5.0, s * 7.0, 7.0, 6.0, "brass")
        box(m, f"rim_z{s}", -5.2, s * 5.2, 5.0, 5.2, s * 7.0, 6.0, "brass")
        box(m, f"band{s}", s * 6.6, -4.0, -2.0, s * 7.0, 4.0, -1.0, "copper")
        box(m, f"ear{s}", s * 6.8, -0.8, 5.4, s * 7.6, 0.8, 7.0, "brass")
    box(m, "handle", -7.6, -0.5, 7.0, 7.6, 0.5, 7.8, "dark")
    for k, (u, v) in enumerate(((4.0, 4.0), (-4.0, 4.0), (0.0, -4.6))):
        box(m, f"leg{k}", u - 1.0, v - 1.0, -8.0, u + 1.0, v + 1.0, -6.4, "darkwood")
    return m


def kettle_water():
    # liquid surface at the bottom of the pot; the plugin lifts it with the water level
    m = Model("sw_kettle_water")
    box(m, "surface", -5.4, -5.4, -5.2, 5.4, 5.4, -4.6, "verdigris")
    return m


def mixer_housing():
    # sits on the kettle: brass-banded gear housing up top, open below for the whisk
    m = Model("sw_mixer_housing")
    box(m, "case", -6.6, -6.6, 1.0, 6.6, 6.6, 7.6, "darkwood")
    box(m, "lid", -7.0, -7.0, 7.0, 7.0, 7.0, 8.0, "brass")
    box(m, "band", -6.9, -6.9, 2.0, 6.9, 6.9, 2.8, "brass")
    box(m, "bush", -2.6, -2.6, 7.9, 2.6, 2.6, 8.0, "steel")
    for su in (-1, 1):
        for sv in (-1, 1):
            box(m, f"strut{su}{sv}", su * 5.8 - 0.8, sv * 5.8 - 0.8, -8.0, su * 5.8 + 0.8, sv * 5.8 + 0.8, 1.0, "steel")
    m.cube("emblem", (-1.6, 3.2, 6.65), (1.6, 6.0, 6.9), "lacquer")
    return m


def mixer_whisk():
    # turns around model Y; drops half a block into the kettle while mixing
    m = Model("sw_mixer_whisk")
    rod(m, "axle", 1.4, -5.0, 8.0, "steel")
    for k in range(4):
        radial(m, f"arm{k}", k * 45, 0.0, 4.2, 0.4, -7.6, -6.8, "brass")
        theta = k * 45
        base, res = split(theta)
        box(m, f"loop{k}", -0.4, -4.6, -7.6, 0.4, 4.6, -1.8, "steel", res) if base % 180 == 0 else \
            box(m, f"loop{k}", -4.6, -0.4, -7.6, 4.6, 0.4, -1.8, "steel", res)
    return m


def ingot(name, metal):
    m = Model(name, 1.3)
    m.cube("bar", (-5.0, -1.6, -2.6), (5.0, 1.0, 2.6), metal)
    m.cube("top", (-4.0, 1.0, -1.8), (4.0, 1.8, 1.8), metal)
    m.cube("stamp", (-1.2, 1.8, -0.8), (1.2, 2.0, 0.8), "dark")
    return m


def raw_lump(name, metal, rock="darkstone"):
    # rough nugget cluster: ore colour with bits of rock
    m = Model(name, 1.4)
    m.cube("core", (-3.0, -2.0, -3.0), (3.0, 2.2, 3.0), metal)
    m.cube("knob1", (1.6, -1.4, -1.0), (4.4, 1.4, 1.8), metal)
    m.cube("knob2", (-4.0, -1.8, -2.2), (-1.4, 0.8, 0.8), metal)
    m.cube("knob3", (-1.4, 1.6, -1.6), (1.8, 3.6, 1.4), metal)
    m.cube("rock1", (-2.0, -2.4, 1.8), (1.0, -0.6, 3.8), rock)
    m.cube("rock2", (1.0, 0.6, -3.6), (3.4, 2.4, -1.6), rock)
    return m


def crystal(name, colour):
    # sulfur: little cluster of yellow crystals
    m = Model(name, 1.4)
    m.cube("base", (-3.0, -2.0, -3.0), (3.0, -0.6, 3.0), "darkstone")
    m.cube("c1", (-1.2, -0.6, -1.2), (1.2, 4.0, 1.2), colour, (0, 45, 0), (0, 0, 0))
    m.cube("c2", (1.0, -0.6, 0.4), (3.0, 2.6, 2.4), colour)
    m.cube("c3", (-3.0, -0.6, -2.2), (-1.0, 2.0, -0.2), colour)
    m.cube("c4", (-0.6, -0.6, 1.4), (0.8, 1.6, 2.8), colour)
    return m


def plate(name, metal):
    m = Model(name, 1.25)
    m.cube("sheet", (-6, -0.5, -6), (6, 0.5, 6), metal)
    m.cube("raised", (-4.6, 0.5, -4.6), (4.6, 0.8, 4.6), metal)
    for su in (-1, 1):
        for sv in (-1, 1):
            m.cube(f"rivet{su}{sv}", (su * 5.2 - 0.5, 0.5, sv * 5.2 - 0.5), (su * 5.2 + 0.5, 1.0, sv * 5.2 + 0.5), "dark")
    return m


# Train cars: model +Z = front, rail level at y = -12 (the plugin lifts the model 0.75 block),
# 30 px long (cars stand 2 blocks apart), 14 px wide.

def wheels(m, zs, mat="lacquer"):
    for k, zc in enumerate(zs):
        for s in (-1, 1):
            x0, x1 = (5.4, 6.6) if s > 0 else (-6.6, -5.4)
            m.cube(f"wheel{k}{s}", (x0, -12.0, zc - 2.6), (x1, -6.8, zc + 2.6), mat)
            m.cube(f"wheel{k}{s}r", (x0, -12.0, zc - 2.6), (x1, -6.8, zc + 2.6), mat, (45, 0, 0), (0, -9.4, zc))
            m.cube(f"hub{k}{s}", (x0 + s * 0.6, -10.2, zc - 0.8), (x1 + s * 0.6, -8.6, zc + 0.8), "brass")
    m.cube("rod_l", (6.7, -9.8, zs[0] - 0.4), (7.1, -9.0, zs[-1] + 0.4), "steel")
    m.cube("rod_r", (-7.1, -9.8, zs[0] - 0.4), (-6.7, -9.0, zs[-1] + 0.4), "steel")


def locomotive():
    m = Model("sw_locomotive", 0.5)
    m.cube("frame", (-6.4, -8.2, -15.0), (6.4, -6.6, 15.0), "dark")
    m.cube("buffer_f", (-6.8, -8.6, 14.6), (6.8, -7.0, 15.4), "red")
    m.cube("buffer_r", (-6.8, -8.6, -15.4), (6.8, -7.0, -14.6), "red")
    wheels(m, (9.5, 2.5, -4.5))
    # boiler: two crossed boxes along Z make an octagon
    m.cube("boiler", (-4.4, -6.6, -6.0), (4.4, 2.2, 14.0), "black")
    m.cube("boiler_r", (-4.4, -6.6, -6.0), (4.4, 2.2, 14.0), "black", (0, 0, 45), (0, -2.2, 4.0))
    for z in (-1.0, 5.0, 11.0):
        m.cube(f"band{z}", (-4.7, -6.9, z), (4.7, 2.5, z + 0.8), "brass")
    m.cube("smokebox", (-4.0, -6.2, 13.8), (4.0, 1.8, 15.0), "dark")
    m.cube("door", (-2.0, -4.6, 14.9), (2.0, -0.6, 15.3), "steel")
    m.cube("lamp", (-1.2, 2.2, 13.6), (1.2, 4.0, 15.2), "gold")
    m.cube("chimney", (-1.7, 2.0, 10.0), (1.7, 10.0, 13.4), "black")
    m.cube("chimney_cap", (-2.4, 10.0, 9.3), (2.4, 11.2, 14.1), "brass")
    m.cube("dome", (-2.0, 2.0, 3.0), (2.0, 4.8, 6.6), "brass")
    m.cube("whistle", (-0.5, 2.0, -3.6), (0.5, 5.6, -2.6), "brass")
    m.cube("cowcatcher", (-5.6, -12.0, 14.0), (5.6, -9.0, 15.2), "dark", (22.5, 0, 0), (0, -10.5, 14.6))
    # cab (rear), red lacquer with a dark roof
    m.cube("cab_floor", (-6.6, -6.6, -15.0), (6.6, -5.8, -6.0), "darkwood")
    for s in (-1, 1):
        x0, x1 = (5.8, 6.6) if s > 0 else (-6.6, -5.8)
        m.cube(f"cab_low{s}", (x0, -5.8, -15.0), (x1, 1.0, -6.0), "lacquer")
        m.cube(f"cab_post_f{s}", (x0, 1.0, -7.0), (x1, 7.0, -6.0), "lacquer")
        m.cube(f"cab_post_r{s}", (x0, 1.0, -15.0), (x1, 7.0, -14.0), "lacquer")
    m.cube("cab_front_low", (-5.8, -5.8, -6.8), (5.8, 1.5, -6.0), "lacquer")
    m.cube("cab_front_top", (-5.8, 5.0, -6.8), (5.8, 7.0, -6.0), "lacquer")
    m.cube("cab_back", (-5.8, -5.8, -15.0), (5.8, 2.0, -14.2), "lacquer")
    m.cube("roof", (-7.4, 7.0, -15.6), (7.4, 8.2, -5.4), "darkwood")
    m.cube("roof_trim", (-7.6, 6.6, -15.8), (7.6, 7.0, -5.2), "brass")
    m.cube("firebox", (-3.0, -5.8, -9.4), (3.0, 0.0, -7.0), "dark")
    m.cube("fire", (-1.6, -4.4, -7.1), (1.6, -2.2, -6.9), "red")
    return m


def passenger_wagon():
    m = Model("sw_passenger_wagon", 0.5)
    m.cube("frame", (-6.4, -8.2, -15.0), (6.4, -6.6, 15.0), "dark")
    wheels(m, (9.0, -9.0))
    m.cube("floor", (-6.6, -6.6, -15.0), (6.6, -5.8, 15.0), "darkwood")
    for s in (-1, 1):
        x0, x1 = (5.8, 6.6) if s > 0 else (-6.6, -5.8)
        m.cube(f"wall_low{s}", (x0, -5.8, -15.0), (x1, 0.6, 15.0), "wood")
        m.cube(f"trim{s}", (x0 - 0.1 * s, 0.6, -15.0), (x1 + 0.1 * s, 1.2, 15.0), "lacquer")
        for z in (-15.0, -5.4, 4.2, 13.8):
            m.cube(f"post{s}{z}", (x0, 1.2, z), (x1, 6.6, z + 1.2), "darkwood")
        m.cube(f"wall_top{s}", (x0, 5.2, -15.0), (x1, 6.6, 15.0), "wood")
    for zs in (-1, 1):
        z0, z1 = (14.2, 15.0) if zs > 0 else (-15.0, -14.2)
        m.cube(f"end{zs}", (-5.8, -5.8, z0), (5.8, 6.6, z1), "wood")
        m.cube(f"door{zs}", (-2.0, -5.8, z0 - 0.1 * -zs), (2.0, 3.4, z1 + 0.1 * zs), "darkwood")
        m.cube(f"lamp{zs}", (-0.8, 4.0, z0 - 0.3 * -zs), (0.8, 5.4, z1 + 0.3 * zs), "gold")
    for k, z in enumerate((-10.0, 0.0, 10.0)):  # benches
        m.cube(f"bench{k}", (-5.0, -5.8, z - 1.4), (5.0, -3.4, z + 1.4), "lacquer")
    m.cube("roof", (-7.2, 6.6, -15.6), (7.2, 7.8, 15.6), "darkwood")
    m.cube("roof_ridge", (-4.6, 7.8, -15.6), (4.6, 8.8, 15.6), "darkwood")
    m.cube("roof_trim", (-7.4, 6.4, -15.8), (7.4, 6.8, 15.8), "brass")
    return m


def cargo_wagon():
    m = Model("sw_cargo_wagon", 0.5)
    m.cube("frame", (-6.4, -8.2, -15.0), (6.4, -6.6, 15.0), "dark")
    wheels(m, (9.0, -9.0))
    m.cube("floor", (-6.6, -6.6, -15.0), (6.6, -5.8, 15.0), "darkwood")
    for s in (-1, 1):
        x0, x1 = (5.8, 6.6) if s > 0 else (-6.6, -5.8)
        m.cube(f"side{s}", (x0, -5.8, -15.0), (x1, -1.0, 15.0), "wood")
        for z in (-15.0, -7.6, -0.6, 6.4, 13.8):
            m.cube(f"stake{s}{z}", (x0 - 0.2 * s, -5.8, z), (x1 + 0.2 * s, 0.4, z + 1.2), "steel")
    for zs in (-1, 1):
        z0, z1 = (14.2, 15.0) if zs > 0 else (-15.0, -14.2)
        m.cube(f"end{zs}", (-5.8, -5.8, z0), (5.8, -1.0, z1), "wood")
    m.cube("crate1", (-5.0, -5.8, -13.0), (0.6, 0.2, -6.0), "wood")
    m.cube("crate1_band", (-5.1, -3.2, -13.1), (0.7, -2.6, -5.9), "darkwood")
    m.cube("crate2", (0.8, -5.8, -12.0), (5.2, -1.4, -7.0), "darkwood")
    m.cube("barrel", (-4.6, -5.8, -2.4), (-0.4, 0.6, 2.4), "darkwood")
    m.cube("barrel_band", (-4.8, -2.0, -2.6), (-0.2, -1.4, 2.6), "steel")
    m.cube("sack", (0.6, -5.8, -2.0), (5.0, -2.4, 3.0), "rope")
    m.cube("crate3", (-5.0, -5.8, 5.0), (5.0, -0.6, 13.0), "wood")
    m.cube("crate3_band", (-5.1, -3.6, 4.9), (5.1, -3.0, 13.1), "copper")
    m.cube("rope", (-5.2, -0.6, 8.6), (5.2, -0.2, 9.4), "rope")
    return m


def station():
    # eki sign: white name board on two dark posts under a small red roof; board faces model +-Z
    # (the plugin puts the station name on it as text, 1 px in front of each face)
    m = Model("sw_station")
    for s in (-1, 1):
        m.cube(f"post{s}", (s * 6.6 - 0.8, -8.0, -0.8), (s * 6.6 + 0.8, 10.0, 0.8), "darkwood")
        m.cube(f"foot{s}", (s * 6.6 - 1.4, -8.0, -1.4), (s * 6.6 + 1.4, -6.8, 1.4), "darkstone")
    m.cube("board", (-6.0, 0.0, -0.5), (6.0, 9.0, 0.5), "paper")
    m.cube("frame_top", (-6.2, 9.0, -0.7), (6.2, 9.6, 0.7), "black")
    m.cube("frame_bottom", (-6.2, -0.6, -0.7), (6.2, 0.0, 0.7), "black")
    m.cube("roof", (-8.0, 10.0, -2.0), (8.0, 11.0, 2.0), "lacquer")
    m.cube("roof_cap", (-8.0, 11.0, -1.0), (8.0, 11.6, 1.0), "dark")
    m.cube("lamp", (-0.9, 7.0, 0.5), (0.9, 8.6, 1.4), "gold")
    return m


def signal(name, red):
    # semaphore-style lamp post: two lamps facing model +Z (the way it was placed facing the player)
    m = Model(name)
    m.cube("base", (-2.4, -8.0, -2.4), (2.4, -6.6, 2.4), "darkstone")
    m.cube("post", (-0.9, -6.6, -0.9), (0.9, 6.0, 0.9), "steel")
    m.cube("head", (-2.4, 1.2, -1.4), (2.4, 10.6, 1.4), "black")
    m.cube("hood_top", (-2.6, 10.6, -1.6), (2.6, 11.2, 2.4), "dark")
    m.cube("lamp_top", (-1.5, 6.6, 1.4), (1.5, 9.6, 1.9), "red" if red else "darkstone")
    m.cube("lamp_bottom", (-1.5, 2.2, 1.4), (1.5, 5.2, 1.9), "darkstone" if red else "verdigris")
    m.cube("lamp_top_b", (-1.5, 6.6, -1.9), (1.5, 9.6, -1.4), "red" if red else "darkstone")
    m.cube("lamp_bottom_b", (-1.5, 2.2, -1.9), (1.5, 5.2, -1.4), "darkstone" if red else "verdigris")
    m.cube("band", (-1.2, -2.0, -1.2), (1.2, -1.2, 1.2), "brass")
    return m


# ---------------------------------------------------------------- oil (model X = shaft axis for the pumpjack)

def pumpjack_base():
    m = Model("sw_pumpjack_base")
    m.cube("skid", (-7.6, -8.0, -7.6), (7.6, -6.8, 7.6), "dark")
    for s in (-1, 1):  # A-frame (samson post) holding the beam pivot at y = +5
        m.cube(f"leg_f{s}", (s * 4.6 - 0.7, -6.8, 1.6), (s * 4.6 + 0.7, 4.4, 3.0), "steel", (22.5, 0, 0), (s * 4.6, -1.2, 2.3))
        m.cube(f"leg_r{s}", (s * 4.6 - 0.7, -6.8, -3.0), (s * 4.6 + 0.7, 4.4, -1.6), "steel", (-22.5, 0, 0), (s * 4.6, -1.2, -2.3))
        m.cube(f"bearing{s}", (s * 4.6 - 1.0, 3.8, -1.0), (s * 4.6 + 1.0, 6.0, 1.0), "brass")
    m.cube("gear_box", (-3.0, -6.8, -7.4), (3.0, -1.6, -3.6), "lacquer")
    m.cube("wellhead", (-1.4, -6.8, 5.4), (1.4, -4.0, 7.4), "black")
    m.cube("wellhead_cap", (-2.0, -4.0, 4.8), (2.0, -3.2, 8.0), "brass")
    m.cube("rod", (-0.25, -3.2, 6.2), (0.25, 2.0, 6.7), "steel")
    return m


def pumpjack_beam():
    # centred on the pivot (the plugin lifts it 5 px and rocks it round model X)
    m = Model("sw_pumpjack_beam")
    m.cube("beam", (-1.0, -0.9, -7.6), (1.0, 0.9, 6.0), "darkwood")
    m.cube("band", (-1.2, -1.1, -1.0), (1.2, 1.1, 1.0), "brass")
    m.cube("head", (-1.6, -4.2, 5.4), (1.6, 1.6, 7.8), "lacquer")   # horse head over the well
    m.cube("head_face", (-1.7, -4.4, 7.6), (1.7, 1.8, 8.0), "dark")
    m.cube("weight", (-2.6, -3.6, -7.8), (2.6, -0.4, -4.6), "black")  # counterweight over the crank
    m.cube("pitman", (-0.4, -9.0, -6.8), (0.4, -0.9, -6.0), "steel")
    return m


def pipe_center():
    m = Model("sw_pipe_center", 1.4)
    m.cube("core", (-3.0, -3.0, -3.0), (3.0, 3.0, 3.0), "copper")
    for s in (-1, 1):
        m.cube(f"flange{s}", (-3.3, -3.3, s * 2.2 - 0.4), (3.3, 3.3, s * 2.2 + 0.4), "brass")
    return m


def pipe_arm():
    # from the core to the block face along model +Y
    m = Model("sw_pipe_arm")
    m.cube("tube", (-2.2, 3.0, -2.2), (2.2, 8.0, 2.2), "copper")
    m.cube("ring", (-2.6, 6.6, -2.6), (2.6, 7.6, 2.6), "brass")
    return m


def tank():
    # iron frame with glass-like open sides (the liquid column shows through), copper bands
    m = Model("sw_tank")
    m.cube("floor", (-7.6, -8.0, -7.6), (7.6, -6.8, 7.6), "dark")
    m.cube("roof", (-7.6, 6.8, -7.6), (7.6, 8.0, 7.6), "dark")
    m.cube("lid", (-3.0, 8.0, -3.0), (3.0, 8.6, 3.0), "brass")
    for su in (-1, 1):
        for sv in (-1, 1):
            m.cube(f"post{su}{sv}", (su * 7.6 - (1.6 if su > 0 else 0), -6.8, sv * 7.6 - (1.6 if sv > 0 else 0)),
                   (su * 7.6 + (0 if su > 0 else 1.6), 6.8, sv * 7.6 + (0 if sv > 0 else 1.6)), "steel")
    for y in (-3.0, 2.6):
        for s in (-1, 1):
            m.cube(f"band_x{s}{y}", (s * 7.4 - 0.3, y, -7.4), (s * 7.4 + 0.3, y + 0.8, 7.4), "copper")
            m.cube(f"band_z{s}{y}", (-7.4, y, s * 7.4 - 0.3), (7.4, y + 0.8, s * 7.4 + 0.3), "copper")
    m.cube("gauge", (-1.2, 3.8, 7.4), (1.2, 6.0, 7.9), "dial")
    return m


def fluid_column(name, mat):
    m = Model(name)
    m.cube("liquid", (-6.0, -6.5, -6.0), (6.0, 6.5, 6.0), mat)
    return m


def refinery():
    # distillation column on a firebox, with condenser coil and outlet pipes
    m = Model("sw_refinery")
    m.cube("firebox", (-7.4, -8.0, -7.4), (7.4, -3.0, 7.4), "darkstone")
    m.cube("door", (-2.4, -7.0, 7.3), (2.4, -4.0, 7.8), "black")
    m.cube("glow", (-1.6, -6.4, 7.7), (1.6, -4.6, 7.9), "red")
    m.cube("column", (-4.2, -3.0, -4.2), (4.2, 8.0, 4.2), "copper")
    m.cube("column_r", (-4.2, -3.0, -4.2), (4.2, 8.0, 4.2), "copper", (0, 45, 0), (0, 0, 0))
    for y in (-0.6, 3.0, 6.4):
        m.cube(f"tray{y}", (-4.6, y, -4.6), (4.6, y + 0.8, 4.6), "brass")
    m.cube("cap", (-2.4, 8.0, -2.4), (2.4, 8.8, 2.4), "dark")
    m.cube("coil", (4.2, -2.0, -1.2), (7.6, 6.0, 1.2), "steel")
    for y in (-1.0, 1.6, 4.2):
        m.cube(f"coil_ring{y}", (4.0, y, -1.6), (7.8, y + 0.8, 1.6), "copper")
    m.cube("valve", (-7.8, 1.0, -1.0), (-4.2, 3.0, 1.0), "lacquer")
    return m


def canister(name, band):
    m = Model(name, 1.3)
    m.cube("body", (-3.4, -5.0, -1.8), (3.4, 4.0, 1.8), "steel")
    m.cube("band", (-3.6, -1.4, -2.0), (3.6, 0.6, 2.0), band)
    m.cube("handle", (-2.6, 4.0, -0.6), (1.0, 5.6, 0.6), "dark")
    m.cube("spout", (1.6, 4.0, -0.8), (3.0, 6.0, 0.8), "brass")
    return m


def dowsing_rod():
    m = Model("sw_dowsing_rod", 1.2)
    m.cube("grip", (-0.6, -7.0, -0.6), (0.6, 0.0, 0.6), "darkwood")
    m.cube("fork_l", (-0.4, -0.5, -0.4), (0.4, 6.5, 0.4), "copper", (0, 0, 22.5), (0, 0, 0))
    m.cube("fork_r", (-0.4, -0.5, -0.4), (0.4, 6.5, 0.4), "copper", (0, 0, -22.5), (0, 0, 0))
    m.cube("bob", (-0.8, -8.2, -0.8), (0.8, -7.0, 0.8), "brass")
    return m


def wrench():
    # steamer's wrench: darkwood grip with brass bands, steel neck, open-jaw brass head
    m = Model("sw_wrench", 1.3)
    m.cube("grip", (-7.6, -1.0, -1.0), (1.0, 1.0, 1.0), "darkwood")
    m.cube("knob", (-8.0, -1.3, -1.3), (-7.2, 1.3, 1.3), "brass")
    m.cube("band1", (-5.4, -1.2, -1.2), (-4.6, 1.2, 1.2), "brass")
    m.cube("band2", (-2.6, -1.2, -1.2), (-1.8, 1.2, 1.2), "brass")
    m.cube("neck", (0.8, -1.4, -1.4), (3.0, 1.4, 1.4), "steel")
    m.cube("head", (2.8, -3.4, -3.4), (6.0, 3.4, 3.4), "brass")
    m.cube("jaw_low", (2.8, -3.6, -3.6), (7.6, -1.6, 3.6), "brass")
    m.cube("jaw_high", (2.8, 1.6, -3.6), (7.6, 3.6, 3.6), "brass")
    m.cube("tip", (6.0, 1.8, -0.9), (7.8, 3.4, 0.9), "steel")
    return m


# ---------------------------------------------------------------- drill + saw (point/cut towards model +Y)

def breaker_casing(name, accent):
    m = Model(name)
    box(m, "body", -6.6, -6.6, -8.0, 6.6, 6.6, 1.0, "steel")
    box(m, "band", -6.9, -6.9, -2.0, 6.9, 6.9, -0.8, accent)
    box(m, "back", -5.4, -5.4, -8.0, 5.4, 5.4, -7.4, "dark")
    box(m, "bush", -2.6, -2.6, -8.2, 2.6, 2.6, -7.9, "brass")
    box(m, "collar", -4.0, -4.0, 1.0, 4.0, 4.0, 2.4, "dark")
    return m


def drill_head():
    m = Model("sw_drill_head")
    rod(m, "shank", 1.6, 1.0, 4.0, "steel")
    for k, (r, w1, w2) in enumerate(((3.6, 2.4, 4.4), (2.6, 4.4, 6.0), (1.6, 6.0, 7.4), (0.7, 7.4, 8.2))):
        rod(m, f"cone{k}", r, w1, w2, "dark" if k % 2 else "steel")
    for k in range(3):  # spiral flutes
        radial(m, f"flute{k}", k * 135, 1.0, 3.8, 0.4, 2.4 + k * 1.6, 3.4 + k * 1.6, "brass")
    return m


def saw_head():
    m = Model("sw_saw_head")
    rod(m, "arbor", 1.4, 1.0, 5.6, "steel")
    disk(m, "blade", 7.4, 4.6, 5.2, "steel")
    for k in range(16):
        radial(m, f"tooth{k}", k * 22.5, 7.0, 8.0, 0.5, 4.5, 5.3, "dark")
    rod(m, "nut", 1.8, 5.2, 6.0, "brass")
    return m


def belt_frame():
    # runs along model Z; rubber surface at the top of the block (items ride on y = +8)
    m = Model("sw_belt_frame")
    m.cube("surface", (-6.8, 6.6, -8.0), (6.8, 8.0, 8.0), "black")
    for k, z in enumerate((-6.0, -2.0, 2.0, 6.0)):  # cleats so the movement direction reads
        m.cube(f"cleat{k}", (-6.2, 7.9, z - 0.3), (6.2, 8.1, z + 0.3), "dark")
    for s in (-1, 1):
        m.cube(f"rail{s}", (s * 7.0 - 1.0, 4.0, -8.0), (s * 7.0 + 1.0, 8.4, 8.0), "darkwood")
        m.cube(f"trim{s}", (s * 7.0 - 1.1, 8.0, -8.0), (s * 7.0 + 1.1, 8.6, 8.0), "brass")
        m.cube(f"leg_a{s}", (s * 6.6 - 0.8, -8.0, -6.6), (s * 6.6 + 0.8, 4.0, -5.0), "steel")
        m.cube(f"leg_b{s}", (s * 6.6 - 0.8, -8.0, 5.0), (s * 6.6 + 0.8, 4.0, 6.6), "steel")
    m.cube("brace", (-6.0, -2.0, -0.6), (6.0, -1.0, 0.6), "steel")
    return m


def belt_rollers():
    # rollers turning round model Y (= the roller axis across the belt), just under the surface
    m = Model("sw_belt_rollers")
    rod(m, "axle", 1.0, -7.8, 7.8, "steel")
    for w in (-5.0, 0.0, 5.0):
        rod(m, f"drum{w}", 2.2, w - 1.6, w + 1.6, "brass")
        box(m, f"mark{w}", 2.0, -0.4, w - 1.6, 2.4, 0.4, w + 1.6, "dark")
    return m


# ---------------------------------------------------------------- karakuri arm (boom points to model +Z at yaw 0)

def arm_base():
    m = Model("sw_arm_base")
    disk(m, "plinth", 7.4, -8.0, -5.8, "darkwood")
    disk(m, "ring", 6.0, -5.8, -4.6, "brass")
    rod(m, "socket", 3.4, -4.6, -2.6, "dark")
    m.cube("emblem", (-1.4, -7.4, 7.0), (1.4, -6.2, 7.5), "lacquer")
    return m


def arm_turret():
    # rotating column, shoulder joint, boom out to the tip at z = +12, y = +4
    m = Model("sw_arm_turret")
    rod(m, "column", 2.6, -2.6, 3.0, "copper")
    m.cube("shoulder", (-2.4, 2.0, -2.4), (2.4, 5.6, 2.4), "brass")
    m.cube("boom", (-1.0, 3.4, 0.0), (1.0, 5.2, 11.6), "lacquer")
    m.cube("boom_rib", (-0.4, 5.2, 1.0), (0.4, 6.0, 10.6), "dark")
    m.cube("counterweight", (-2.0, 2.6, -5.6), (2.0, 5.8, -2.4), "black")
    m.cube("wrist", (-1.4, 2.4, 10.6), (1.4, 4.4, 13.0), "brass")
    for s in (-1, 1):
        m.cube(f"piston{s}", (s * 1.6 - 0.4, -1.0, 1.0), (s * 1.6 + 0.4, 3.6, 2.0), "steel", (-22.5, 0, 0), (s * 1.6, 1.3, 1.5))
    return m


def arm_tool(name, kind):
    # hangs under the wrist at z = +11.8
    m = Model(name)
    z = 11.8
    m.cube("mount", (-1.6, 1.6, z - 1.6), (1.6, 2.4, z + 1.6), "dark")
    if kind == "gripper":
        for s in (-1, 1):
            m.cube(f"finger{s}", (-0.6, -2.4, z + s * 1.0 - 0.4), (0.6, 1.6, z + s * 1.0 + 0.4), "steel")
            m.cube(f"tip{s}", (-0.6, -2.8, z + s * 0.6 - 0.4), (0.6, -2.0, z + s * 0.6 + 0.4), "brass")
    elif kind == "placer":
        m.cube("ram", (-0.6, -1.0, z - 0.6), (0.6, 1.6, z + 0.6), "steel")
        m.cube("pad", (-2.0, -1.8, z - 2.0), (2.0, -1.0, z + 2.0), "copper")
    else:  # drill
        m.cube("chuck", (-1.0, -0.4, z - 1.0), (1.0, 1.6, z + 1.0), "steel")
        m.cube("bit1", (-0.6, -2.4, z - 0.6), (0.6, -0.4, z + 0.6), "dark")
        m.cube("bit2", (-0.3, -3.6, z - 0.3), (0.3, -2.4, z + 0.3), "steel")
    return m


def tool_item(name, kind):
    # the loose tool head as an item: centred
    src = arm_tool(name + "_tmp", kind)
    m = Model(name, 1.6)
    for e in src.elements:
        e2 = dict(e, uuid=U(), **{"from": [e["from"][0], e["from"][1], e["from"][2] - 11.8], "to": [e["to"][0], e["to"][1], e["to"][2] - 11.8],
                                  "origin": [e["origin"][0], e["origin"][1], e["origin"][2] - 11.8]})
        m.elements.append(e2)
    return m


def punch_card():
    m = Model("sw_punch_card", 1.4)
    m.cube("card", (-5.0, -0.3, -3.4), (5.0, 0.3, 3.4), "paper")
    for k, (x, z) in enumerate(((-3.6, -2.0), (-2.0, 0.6), (-0.4, -1.2), (1.2, 1.6), (2.8, -0.4), (-3.6, 1.8))):
        m.cube(f"hole{k}", (x, 0.25, z), (x + 0.9, 0.4, z + 0.6), "dark")
    m.cube("corner", (3.8, 0.25, -3.4), (5.0, 0.4, -2.4), "lacquer")
    return m


def track_straight():
    # sw straight track: full-width ballast bed, wooden sleepers, steel rails, brass fishplates
    m = Model("sw_track_straight", 0.6)
    m.cube("bed", (-8, 0.0, -8.0), (8, 2.0, 8.0), "darkstone")
    for z in (-6.4, -3.2, 0.0, 3.2, 6.4):
        m.cube(f"sleeper{z}", (-6.2, 2.0, z - 0.7), (6.2, 2.6, z + 0.7), "darkwood")
    for x in (-2.55, 1.65):
        m.cube(f"rail{x}", (x, 2.6, -8.0), (x + 0.9, 3.4, 8.0), "steel")
    n = 0
    for x in (-2.1, 2.1):
        for z0, z1 in ((-8.0, -7.15), (7.15, 8.0)):
            n += 1
            m.cube(f"shakel{n}", (x - 0.65, 2.3, z0), (x + 0.65, 3.7, z1), "brass")
    return m


def track_slope():
    # sw sloped track: same layout stretched to 16*sqrt(2) px; the display is tilted -45 deg,
    # so the ends land exactly on the block edges (low end at rail level, high end one up)
    m = Model("sw_track_slope", 0.6)
    h = 11.31
    m.cube("bed", (-8, 0.0, -h), (8, 2.0, h), "darkstone")
    for z in (-9.2, -4.6, 0.0, 4.6, 9.2):
        m.cube(f"sleeper{z}", (-6.2, 2.0, z - 0.7), (6.2, 2.6, z + 0.7), "darkwood")
    for x in (-2.55, 1.65):
        m.cube(f"rail{x}", (x, 2.6, -h), (x + 0.9, 3.4, h), "steel")
    n = 0
    for x in (-2.1, 2.1):
        for z0, z1 in ((-h, -h + 0.85), (h - 0.85, h)):
            n += 1
            m.cube(f"shakel{n}", (x - 0.65, 2.3, z0), (x + 0.65, 3.7, z1), "brass")
    return m


def track_curve():
    # sw curve (base shape: NORTH edge to EAST edge, quarter circle around the north-east corner)
    m = Model("sw_track_curve", 0.6)
    m.cube("bed", (-8, 0.0, -8.0), (8, 2.0, 8.0), "darkstone")
    m.cube("sleeper_n", (-3.4, 2.0, -7.3), (3.4, 2.6, -5.9), "darkwood")
    m.cube("sleeper_d", (2.343 - 3.4, 2.0, -2.343 - 0.7), (2.343 + 3.4, 2.6, -2.343 + 0.7),
           "darkwood", (0, -45, 0), (2.343, 1.5, -2.343))
    m.cube("sleeper_e", (5.9, 2.0, -3.4), (7.3, 2.6, 3.4), "darkwood")
    import math
    for x in (-2.55, 1.65):  # north leg rails
        m.cube(f"railn{x}", (x, 2.6, -8.0), (x + 0.9, 3.4, -2.4), "steel")
    for z in (-2.55, 1.65):  # east leg rails
        m.cube(f"raile{z}", (2.4, 2.6, z), (8.0, 3.4, z + 0.9), "steel")
    for R in (5.9, 10.1):    # arc segments at theta 157.5 / 135 / 112.5
        for th in (157.5, 135.0, 112.5):
            px = 8 + R * math.cos(math.radians(th))
            pz = -8 + R * math.sin(math.radians(th))
            m.cube(f"raila{R}{th}", (px - 0.45, 2.6, pz - 1.75), (px + 0.45, 3.4, pz + 1.75),
                   "steel", (0, -th, 0), (px, 3.0, pz))
    for x in (-2.1, 2.1):  # fishplate at the north edge
        m.cube(f"shakeln{x}", (x - 0.65, 2.3, -8.0), (x + 0.65, 3.7, -7.15), "brass")
    for z in (-2.1, 2.1):  # fishplate at the east edge
        m.cube(f"shakelE{z}", (7.15, 2.3, z - 0.65), (8.0, 3.7, z + 0.65), "brass")
    return m


def sprocket():
    m = Model("sw_sprocket", 1.2)
    axle(m)
    disk(m, "plate", 4.6, -0.8, 0.8, "steel")
    for k in range(8):
        radial(m, f"tooth{k}", k * 45, 4.4, 6.0, 0.7, -0.6, 0.6, "dark")
    rod(m, "hub", 2.2, -1.6, 1.6, "brass")
    return m


def chain_span():
    # one block of chain along model +Z (0..16), stretched by the plugin to the real length
    m = Model("sw_chain_span")
    for k in range(4):
        z = k * 4.0
        if k % 2 == 0:
            m.cube(f"link{k}", (-0.5, -0.9, z), (0.5, 0.9, z + 4.2), "steel")
        else:
            m.cube(f"link{k}", (-0.9, -0.5, z), (0.9, 0.5, z + 4.2), "dark")
    return m


# ---------------------------------------------------------------- phase 4: windmill, boiler, engine, crusher

def _arm_box(m, name, k, a1, a2, b1, b2, w1, w2, mat):
    """Box in sail k's own frame: a = along the sail (out from the hub), b = sideways."""
    if k == 0:
        box(m, name, b1, a1, w1, b2, a2, w2, mat)
    elif k == 1:
        box(m, name, a1, -b2, w1, a2, -b1, w2, mat)
    elif k == 2:
        box(m, name, -b2, -a2, w1, -b1, -a1, w2, mat)
    else:
        box(m, name, -a2, b1, w1, -a1, b2, w2, mat)


def windmill_sails(name="sw_windmill_sails", s=1.0, gui=1.0):
    # 4 lattice sails with canvas, turning round model Y; drawn 1:1 = radius 23 px,
    # the plugin shows them 3.5x bigger (~10 blocks across), about 0.75 block in front of the hub.
    # s < 1 makes a small copy for the item look.
    m = Model(name, gui)
    q = lambda v: v * s
    W = 3.4  # sail plane (x 3.5 = 12 px in front of the block centre)
    rod(m, "hub", q(3.0), q(W - 2.6), q(W + 1.0), "brass")
    rod(m, "cap", q(1.6), q(W + 1.0), q(W + 1.8), "lacquer")
    for k in range(4):
        _arm_box(m, f"spar{k}", k, q(2.0), q(23.0), q(-0.6), q(0.6), q(W - 0.5), q(W + 0.5), "darkwood")
        for b in (1.2, 6.4):  # lattice: two long rails + cross bars, all on one side of the spar
            _arm_box(m, f"rail{k}_{b}", k, q(5.0), q(22.4), q(b - 0.35), q(b + 0.35), q(W - 0.35), q(W + 0.35), "darkwood")
        for j, a in enumerate(range(5, 23, 3)):
            _arm_box(m, f"bar{k}_{j}", k, q(a), q(a + 0.6), q(0.6), q(6.8), q(W - 0.3), q(W + 0.3), "wood")
        _arm_box(m, f"canvas{k}", k, q(5.6), q(21.8), q(1.5), q(6.1), q(W - 0.12), q(W + 0.12), "paper")
        _arm_box(m, f"tip{k}", k, q(21.8), q(22.8), q(0.6), q(6.8), q(W - 0.4), q(W + 0.4), "lacquer")
    return m


def windmill_bearing():
    # nacelle the sails turn on; the sails face model +Y (the way it was placed), shaft out the back
    m = Model("sw_windmill_bearing")
    box(m, "body", -6.0, -6.0, -8.0, 6.0, 6.0, 4.0, "darkwood")
    box(m, "band", -6.3, -6.3, -2.0, 6.3, 6.3, -0.8, "brass")
    box(m, "roof", -6.6, -6.6, 2.6, 6.6, 6.6, 4.0, "lacquer")
    box(m, "bush", -2.6, -2.6, -8.2, 2.6, 2.6, -7.8, "steel")
    rod(m, "nose", 2.4, 4.0, 8.0, "steel")
    return m


def steam_boiler(name, lit):
    m = Model(name)
    # octagonal copper drum (two crossed boxes) standing up
    m.cube("drum", (-6.6, -6.0, -6.6), (6.6, 6.4, 6.6), "copper")
    m.cube("drum_r", (-6.6, -6.0, -6.6), (6.6, 6.4, 6.6), "copper", (0, 45, 0), (0, 0, 0))
    m.cube("base", (-7.6, -8.0, -7.6), (7.6, -6.0, 7.6), "darkstone")
    m.cube("lid", (-5.6, 6.4, -5.6), (5.6, 8.0, 5.6), "dark")
    for y in (-3.0, 3.0):
        m.cube(f"band{y}", (-6.9, y, -6.9), (6.9, y + 0.8, 6.9), "brass")
    m.cube("door", (-2.4, -5.6, 7.0), (2.4, -1.6, 7.6), "black")
    m.cube("fire", (-1.8, -5.0, 7.5), (1.8, -2.2, 7.8), "red" if lit else "darkstone")
    m.cube("gauge", (3.0, 1.0, 6.6), (5.4, 3.4, 7.4), "dial")
    m.cube("valve", (-5.4, 4.0, 6.0), (-3.6, 5.8, 7.6), "lacquer")
    return m


def steam_engine():
    # model X = shaft axis (the flywheel is a separate spinning display)
    m = Model("sw_steam_engine")
    m.cube("bed", (-7.6, -8.0, -7.6), (7.6, -6.4, 7.6), "dark")
    m.cube("cylinder", (-3.0, -6.4, -7.0), (3.0, -0.4, 0.0), "brass")
    m.cube("cylinder_r", (-3.0, -6.4, -7.0), (3.0, -0.4, 0.0), "brass", (0, 0, 45), (0, -3.4, -3.5))
    m.cube("head", (-3.4, -6.8, -7.6), (3.4, 0.0, -6.6), "copper")
    m.cube("rod", (-0.5, -3.9, 0.0), (0.5, -2.9, 5.0), "steel")
    m.cube("crosshead", (-1.6, -4.6, 4.4), (1.6, -2.2, 6.0), "dark")
    for s in (-1, 1):
        m.cube(f"pedestal{s}", (s * 6.2 - 1.0, -6.4, -1.6), (s * 6.2 + 1.0, 1.6, 1.6), "steel")
    m.cube("pipe", (2.2, -0.4, -5.0), (3.6, 6.0, -3.6), "copper")
    m.cube("pipe_top", (1.6, 6.0, -5.6), (4.2, 7.2, -3.0), "brass")
    return m


def steam_engine_flywheel():
    m = Model("sw_steam_engine_flywheel")
    axle(m)
    ring(m, "rim", 6.6, 1.4, -1.0, 1.0, "dark")
    for k in range(8):
        radial(m, f"spoke{k}", k * 45, 1.6, 6.0, 0.5, -0.5, 0.5, "steel")
    rod(m, "hub", 2.0, -1.4, 1.4, "brass")
    return m


def crushing_wheel():
    m = Model("sw_crushing_wheel")
    axle(m)
    disk(m, "wheel", 7.4, -6.0, 6.0, "stone")
    for k in range(16):
        radial(m, f"ridge{k}", k * 22.5, 6.8, 8.0, 0.6, -6.0, 6.0, "darkstone")
    rod(m, "hub", 3.0, -6.6, 6.6, "dark")
    return m


def flour_sack():
    m = Model("sw_flour", 1.5)
    m.cube("sack", (-3.6, -4.0, -2.6), (3.6, 2.6, 2.6), "paper")
    m.cube("neck", (-1.6, 2.6, -1.2), (1.6, 3.6, 1.2), "paper")
    m.cube("tie", (-1.8, 3.6, -1.4), (1.8, 4.2, 1.4), "rope")
    m.cube("stamp", (-1.4, -1.6, 2.55), (1.4, 0.8, 2.75), "lacquer")
    return m


def whisk():
    # mixer rod: darkwood grip, brass ferrule, four copper wire loops bent into an egg
    m = Model("sw_whisk", 1.25)
    m.cube("grip", (-0.8, -7.0, -0.8), (0.8, -3.2, 0.8), "darkwood")
    m.cube("ferrule", (-1.1, -7.6, -1.1), (1.1, -6.6, 1.1), "brass")
    m.cube("wire_e", (3.4, -3.2, -0.4), (4.2, 4.4, 0.4), "copper", (0, 0, -22.5), (0, 0, 0))
    m.cube("wire_w", (-4.2, -3.2, -0.4), (-3.4, 4.4, 0.4), "copper", (0, 0, 22.5), (0, 0, 0))
    m.cube("wire_n", (-0.4, -3.2, 3.4), (0.4, 4.4, 4.2), "copper", (22.5, 0, 0), (0, 0, 0))
    m.cube("wire_s", (-0.4, -3.2, -4.2), (0.4, 4.4, -3.4), "copper", (-22.5, 0, 0), (0, 0, 0))
    m.cube("bind", (-1.0, -3.6, -1.0), (1.0, -2.2, 1.0), "steel")
    return m


def propeller():
    m = Model("sw_propeller", 1.3)
    m.cube("hub", (-1.6, -1.6, -2.6), (1.6, 1.6, 2.6), "steel")
    m.cube("blade_a", (-6.0, -0.5, 1.0), (6.0, 0.5, 2.4), "copper", (22.5, 0, 0), (0, 0, 0))
    m.cube("blade_b", (-6.0, -0.5, -2.4), (6.0, 0.5, -1.0), "copper", (-22.5, 0, 0), (0, 0, 0))
    m.cube("nut", (-0.9, -2.2, -0.9), (0.9, -1.5, 0.9), "brass")
    return m


def casing(name, band):
    # Create's gear housing: a log cube banded in metal with a shaft stub on top
    m = Model(name, 1.15)
    m.cube("log", (-5.5, -5.5, -5.5), (5.5, 5.5, 5.5), "wood")
    m.cube("band_a", (-6.0, -1.6, -6.0), (6.0, -0.2, 6.0), band)
    m.cube("band_b", (-6.0, 0.2, -6.0), (6.0, 1.6, 6.0), band)
    m.cube("cap", (-4.2, 5.5, -4.2), (4.2, 6.6, 4.2), "darkwood")
    m.cube("stub", (-1.2, 6.6, -1.2), (1.2, 8.0, 1.2), "steel")
    return m


def alloy_lump():
    # speckled andesite-zinc blob
    m = raw_lump("sw_andesite_alloy", "stone")
    m.cube("sparkle1", (-2.6, 1.2, -2.2), (-1.2, 2.4, -0.8), "steel")
    m.cube("sparkle2", (1.2, -0.8, 1.4), (2.6, 0.4, 2.8), "steel")
    return m


def zinc_nugget():
    m = Model("sw_zinc_nugget", 1.5)
    m.cube("n1", (-2.6, -1.4, -1.0), (0.0, 0.8, 1.6), "dial")
    m.cube("n2", (0.2, -1.0, -1.8), (2.8, 1.2, 0.4), "dial")
    m.cube("n3", (-1.2, 0.6, -0.6), (1.4, 2.6, 1.4), "dial")
    return m


def build():
    parts = [shaft(), cogwheel(), large_cogwheel(), gearbox(), hand_crank(), water_wheel(),
             gauge_body("sw_speed_gauge_body", "brass"), gauge_body("sw_stress_gauge_body", "copper"),
             gauge_needle(), millstone_base(), millstone_runner(),
             steam_hammer_frame(), steam_hammer_head(), steam_hammer_cam(),
             plate("sw_iron_plate", "steel"), plate("sw_copper_plate", "copper"), plate("sw_golden_plate", "gold"),
             shaft_half(), bellows_fan_casing(), bellows_fan_rotor(),
             kettle(), kettle_water(), mixer_housing(), mixer_whisk(),
             ingot("sw_brass_ingot", "brass"), plate("sw_brass_plate", "brass"),
             locomotive(), passenger_wagon(), cargo_wagon(),
             station(), signal("sw_signal", False), signal("sw_signal_red", True), signal("sw_signal_green", False),
             raw_lump("sw_raw_zinc", "steel"), ingot("sw_zinc_ingot", "steel"),
             raw_lump("sw_raw_tin", "dial"), ingot("sw_tin_ingot", "dial"), crystal("sw_sulfur", "gold"),
             pumpjack_base(), pumpjack_beam(), pipe_center(), pipe_arm(), tank(), refinery(),
             fluid_column("sw_fluid_crude_oil", "black"), fluid_column("sw_fluid_fuel", "gold"),
             fluid_column("sw_fluid_chemical_waste", "verdigris"),
             canister("sw_canister", "steel"), canister("sw_crude_canister", "black"),
             canister("sw_fuel_canister", "gold"), canister("sw_waste_canister", "verdigris"), dowsing_rod(), wrench(),
             breaker_casing("sw_drill_casing", "brass"), breaker_casing("sw_saw_casing", "lacquer"), drill_head(), saw_head(), belt_frame(), belt_rollers(),
             arm_base(), arm_turret(), arm_tool("sw_arm_tool_gripper", "gripper"), arm_tool("sw_arm_tool_placer", "placer"),
             arm_tool("sw_arm_tool_drill", "drill"), tool_item("sw_arm_gripper", "gripper"), tool_item("sw_arm_placer", "placer"),
             tool_item("sw_arm_drill", "drill"), punch_card(), sprocket(), chain_span(),
             windmill_sails(), windmill_bearing(), steam_boiler("sw_steam_boiler", False), steam_boiler("sw_steam_boiler_lit", True),
             steam_engine(), steam_engine_flywheel(), crushing_wheel(), flour_sack(),
             alloy_lump(), casing("sw_andesite_casing", "stone"), casing("sw_brass_casing", "brass"),
             whisk(), propeller(), zinc_nugget(),
             track_straight(), track_slope(), track_curve()]
    for part, metal, frame in (("clutch", "steel", "brass"), ("gearshift", "darkwood", "copper")):
        parts.append(switch_casing(f"sw_{part}_casing", metal, frame, False))
        parts.append(switch_casing(f"sw_{part}_casing_on", metal, frame, True))
    by = {p.name: p for p in parts}
    # item looks that combine display pieces
    for kind in ("speed", "stress"):
        g = Model(f"sw_{kind}_gauge", 1.1)
        g.merge(by[f"sw_{kind}_gauge_body"]).merge(by["sw_gauge_needle"]).merge(shaft())
        parts.append(g)
    ms = Model("sw_millstone", 1.0)
    parts.append(ms.merge(by["sw_millstone_base"]).merge(by["sw_millstone_runner"]))
    sh = Model("sw_steam_hammer", 1.0)
    sh.merge(by["sw_steam_hammer_frame"]).merge(by["sw_steam_hammer_head"])
    sh.cube("axle", (-8, -1.8, -1.8), (8, 1.8, 1.8), "steel")  # the cam shaft, along model X
    parts.append(sh)
    pj = Model("sw_pumpjack", 1.0)
    pj.merge(by["sw_pumpjack_base"])
    for e in by["sw_pumpjack_beam"].elements:  # beam lifted to its pivot for the item look
        e2 = dict(e, uuid=U(), **{"from": [e["from"][0], e["from"][1] + 5, e["from"][2]], "to": [e["to"][0], e["to"][1] + 5, e["to"][2]],
                                  "origin": [e["origin"][0], e["origin"][1] + 5, e["origin"][2]]})
        pj.elements.append(e2)
    parts.append(pj)
    pp = Model("sw_pipe", 1.3)
    pp.merge(by["sw_pipe_center"])
    pp.cube("arm_a", (-2.2, -8.0, -2.2), (2.2, 8.0, 2.2), "copper")
    parts.append(pp)
    wm = Model("sw_windmill", 1.0)
    wm.merge(by["sw_windmill_bearing"]).merge(windmill_sails("tmp", 0.33))
    parts.append(wm)
    am = Model("sw_arm", 1.0)
    parts.append(am.merge(by["sw_arm_base"]).merge(by["sw_arm_turret"]).merge(by["sw_arm_tool_gripper"]))
    bt = Model("sw_belt", 1.0)
    bt.merge(by["sw_belt_frame"])
    bt.cube("roller_item", (-6.0, -2.2, -2.2), (6.0, 2.2, 2.2), "brass")
    parts.append(bt)
    for part in ("drill", "saw"):
        g = Model(f"sw_{part}", 1.0)
        parts.append(g.merge(by[f"sw_{part}_casing"]).merge(by[f"sw_{part}_head"]))
    mx = Model("sw_mixer", 1.0)
    parts.append(mx.merge(by["sw_mixer_housing"]).merge(by["sw_mixer_whisk"]))
    fan = Model("sw_bellows_fan", 1.0)
    parts.append(fan.merge(by["sw_bellows_fan_casing"]).merge(by["sw_bellows_fan_rotor"]))
    for part in ("clutch", "gearshift"):
        g = Model(f"sw_{part}", 1.0)
        g.merge(by[f"sw_{part}_casing"])
        axle(g)
        parts.append(g)
    return parts


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for m in build():
        for e in m.elements:  # vanilla model bounds
            assert all(-16 <= c + 8 <= 32 for c in e["from"] + e["to"]), (m.name, e["name"])
        json.dump(m.bbmodel(), open(os.path.join(out, m.name + ".bbmodel"), "w", encoding="utf-8"), separators=(",", ":"))
        json.dump(m.item_json(), open(os.path.join(out, m.name + ".json"), "w", encoding="utf-8"), separators=(",", ":"))
        print(f"{m.name}: {len(m.elements)} cubes")
