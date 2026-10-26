"""Wildlife animal models for FreeMinecraftModels (own designs, own palette texture).

Every animal is a Blockbench .bbmodel with bones and animations that FMM plays on an
invisible vanilla base mob: "idle" and "walk" are picked by FMM itself, "death" when the
mob dies; extra ones (curl, sleep, eat, hide) are played by the plugin.

Scale is real: 1 block = 16 px = 1 m (a deer stands ~1 m at the shoulder, a brown bear
~1.2 m, a mallard is 55 cm long). Insects and other tiny animals are about twice their
real size so they stay visible. The plugin scales the base mob's hitbox to match
(Species.scale). Coordinates: pixels, y = 0 on the ground, the head points to -Z.
Bones whose name starts with "h_" follow where the mob looks (FMM convention).
Shapes are built from several slices per body part (chest, belly, hips; upper and lower
legs; skull and muzzle) with shaded palette variants (<colour>_d darker, _l lighter).
Element rotations are not used (vanilla item models only allow a few angles), slopes are
stepped instead. Each species gets a rare colour variant (<id>_rare).

Usage: python make_animals.py <out folder>   (-> fmm-models/wildlife)
"""
import base64, json, math, os, random, struct, sys, uuid, zlib

RES = 128
U = lambda: str(uuid.uuid4())

BASE = {
    "deer": (150, 98, 60), "deer_white": (236, 230, 220), "fur_brown": (112, 76, 48), "fur_dark": (70, 48, 32),
    "fur_light": (176, 140, 100), "fur_cream": (226, 208, 176), "fur_white": (238, 236, 230), "fur_black": (36, 32, 30),
    "fur_red": (164, 92, 52), "fur_gold": (210, 168, 86), "boar": (84, 68, 56), "antler": (206, 190, 156),
    "hoof": (44, 38, 34), "nose": (30, 26, 24), "eye": (16, 14, 14), "eye_red": (180, 40, 40), "tusk": (238, 232, 214),
    "pink": (214, 150, 146), "claw": (60, 54, 50),
    "duck_gray": (164, 162, 156), "duck_brown": (118, 84, 58), "chestnut": (112, 58, 40), "duck_green": (30, 96, 56),
    "speculum": (50, 70, 170), "bill": (222, 186, 60), "web": (226, 132, 40), "white": (248, 248, 246), "black": (24, 22, 24),
    "monarch": (232, 116, 26), "morpho": (40, 108, 220), "glow_yellow": (246, 238, 120), "glow_blue": (130, 220, 250),
    "wing_clear": (214, 232, 236), "firefly_red": (196, 70, 40),
    "shell_brown": (142, 96, 56), "shell_tan": (204, 166, 108), "shell_gold": (228, 192, 74), "snail_body": (150, 142, 122),
    "spine": (96, 74, 54), "spine_tip": (214, 204, 190), "hedgehog_face": (196, 170, 136),
    "capy": (140, 100, 64), "yuzu": (246, 196, 40), "leaf": (76, 146, 58),
    "tortoise_shell": (150, 120, 70), "scute": (110, 84, 46), "moss": (90, 140, 66), "scales": (156, 138, 100),
    "lizard": (96, 150, 70), "lizard_spot": (60, 100, 46), "lizard_blue": (60, 120, 200), "lizard_blue_spot": (30, 70, 140),
    "crab": (200, 74, 44), "crab_blue": (60, 108, 180), "crab_belly": (232, 210, 180),
    "jay": (64, 110, 196), "cardinal": (196, 36, 36), "canary": (244, 214, 58), "sparrow": (136, 106, 76),
    "beak": (226, 168, 66), "gold": (226, 186, 70),
    "dragon_teal": (40, 160, 150), "dragon_red": (200, 50, 40), "dragon_eye": (40, 90, 110),
    "zebra_white": (240, 238, 230), "zebra_black": (28, 26, 30), "elephant": (122, 118, 114), "elephant_white": (222, 216, 208),
    "elephant_ear": (178, 128, 126),
    "tanuki": (126, 108, 92), "tanuki_dark": (66, 56, 50),
    "koi_orange": (236, 118, 40), "koi_black": (40, 34, 36), "koi_fin": (246, 196, 150),
    "macaque": (150, 118, 92),
}
PALETTE = {}
for n, (r, g, b) in BASE.items():
    PALETTE[n] = (r, g, b)
    PALETTE[n + "_d"] = tuple(max(0, int(c * 0.72)) for c in (r, g, b))
    PALETTE[n + "_l"] = tuple(min(255, int(c * 1.18 + 12)) for c in (r, g, b))
BABY_K = 0.5    # a baby is half the size of the adult ...
HEAD_K = 1.35   # ... with a head a third bigger
NAMES = list(PALETTE)
assert len(NAMES) <= 256, len(NAMES)


def png(px):
    h, w = len(px), len(px[0])
    rows, prev = [], bytes(w * 4)
    for row in px:
        cur = bytes(c for p in row for c in p)
        sub = bytes((cur[i] - (cur[i - 4] if i >= 4 else 0)) & 255 for i in range(len(cur)))
        up = bytes((cur[i] - prev[i]) & 255 for i in range(len(cur)))
        cost = lambda b: sum(min(v, 256 - v) for v in b)
        best = min(((cost(cur), 0, cur), (cost(sub), 1, sub), (cost(up), 2, up)), key=lambda t: t[0])
        rows.append(bytes([best[1]]) + best[2])
        prev = cur
    raw = b"".join(rows)
    ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def palette_png():
    """8x8 cells with fur-like noise (vertical streaks) so flat faces read as fur/feathers."""
    rnd = random.Random(11)
    px = [[(0, 0, 0, 255)] * RES for _ in range(RES)]
    for i, n in enumerate(NAMES):
        r, g, b = PALETTE[n]
        cx, cy = (i % 16) * 8, (i // 16) * 8
        streak = [rnd.randint(-6, 6) for _ in range(8)]
        for y in range(8):
            for x in range(8):
                d = rnd.randint(-5, 5) + streak[x]
                px[cy + y][cx + x] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255)
    return png(px)


TEX = "data:image/png;base64," + base64.b64encode(palette_png()).decode()


def cell(col):
    i = NAMES.index(col)
    cx, cy = (i % 16) * 8, (i // 16) * 8
    return [cx + 1, cy + 1, cx + 7, cy + 7]

# Size factor per species on top of the real-size drawing (1 block = 1 m): the real animals are shown a bit
# larger so they read well next to vanilla mobs (cow 1.4 m, horse 1.6 m, player 1.8 m). Keep Species.scale in sync.
SIZE = {"elephant": 0.9, "zebra": 1.25, "deer": 1.3, "bear": 1.15, "boar": 1.3, "capybara": 1.3, "duck": 1.4, "tortoise": 1.3, "hedgehog": 1.3,
        "lizard": 1.3, "crab": 1.2, "songbird": 1.3, "snail": 1.3, "butterfly": 1.2, "dragonfly": 1.2,
        "tanuki": 1.3, "crane": 1.25, "koi": 1.4, "macaque": 1.25,
        "kangaroo": 1.3, "ostrich": 1.2, "crocodile": 1.25, "koala": 1.3, "mole": 1.35,
        "rhino": 1.15, "penguin": 1.35, "orca": 1.0, "piranha": 1.35, "macaw": 1.35}

# ------------------------------------------------------------------ model + bones + animations

class Bone:
    def __init__(self, name, origin):
        self.name, self.origin, self.uuid = name, list(origin), U()
        self.cubes, self.children = [], []


class Model:
    def __init__(self, name, recolor=None):
        self.name = name
        self.recolor = recolor or {}
        self.roots, self.bones, self.elements = [], {}, []
        self.anims = []
        self.baby = name.endswith("_baby")

    def bone(self, name, origin, parent=None):
        b = Bone(name, origin)
        self.bones[name] = b
        (self.bones[parent].children if parent else self.roots).append(b)
        return b

    def col(self, c):
        """Recolour keeps the shade suffix: {'deer': 'fur_white'} also maps deer_d -> fur_white_d."""
        if c in self.recolor:
            return self.recolor[c]
        for suf in ("_d", "_l"):
            if c.endswith(suf) and c[:-2] in self.recolor:
                return self.recolor[c[:-2]] + suf
        return c

    def cube(self, bone, frm, to, col):
        col = self.col(col)
        frm, to = [min(a, b) for a, b in zip(frm, to)], [max(a, b) for a, b in zip(frm, to)]
        e = {"name": col, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
             "allow_mirror_modeling": True, "from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
             "autouv": 0, "color": 0, "origin": self.bones[bone].origin, "rotation": [0, 0, 0],
             "faces": {f: {"uv": cell(col), "texture": 0} for f in ("north", "east", "south", "west", "up", "down")},
             "type": "cube", "uuid": U()}
        self.elements.append(e)
        self.bones[bone].cubes.append(e["uuid"])

    def box(self, bone, x1, y1, z1, x2, y2, z2, col):
        self.cube(bone, (x1, y1, z1), (x2, y2, z2), col)

    def sym(self, bone, x1, y1, z1, x2, y2, z2, col):
        """Mirrored pair left/right of x = 0 (x1..x2 given for the +x side)."""
        self.cube(bone, (x1, y1, z1), (x2, y2, z2), col)
        self.cube(bone, (-x2, y1, z1), (-x1, y2, z2), col)

    def anim(self, name, length, tracks, loop="loop"):
        animators = {}
        for bname, chans in tracks.items():
            if bname not in self.bones:
                continue
            kfs = []
            for chan, points in chans.items():
                for t, (x, y, z) in points:
                    kfs.append({"channel": chan, "data_points": [{"x": str(round(x, 2)), "y": str(round(y, 2)), "z": str(round(z, 2))}],
                                "uuid": U(), "time": round(t, 3), "color": -1, "interpolation": "linear"})
            animators[self.bones[bname].uuid] = {"name": bname, "type": "bone", "keyframes": kfs}
        self.anims.append({"uuid": U(), "name": name, "loop": loop, "override": False, "length": length, "snapping": 20,
                           "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                           "animators": animators})

    def outline(self, b):
        return {"name": b.name, "origin": b.origin, "rotation": [0, 0, 0], "color": 0, "uuid": b.uuid, "export": True,
                "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
                "children": b.cubes + [self.outline(c) for c in b.children]}

    def key(self):
        """Species id of a model name (wl_<id>[_rare][_baby], songbird_*/macaw_* looks count as the species)."""
        n = self.name[3:]
        if n.endswith("_baby"):
            n = n[:-5]
        if n.endswith("_rare"):
            n = n[:-5]
        if n.startswith("songbird"):
            return "songbird"
        if n.startswith("macaw"):
            return "macaw"
        return n

    def size(self):
        """Per-species size factor (SIZE below), halved for babies."""
        return SIZE.get(self.key(), 1.0) * (BABY_K if self.baby else 1.0)

    def chibi(self):
        """Baby proportions: everything on the head bone grows by HEAD_K about the neck pivot."""
        if not self.baby or "h_head" not in self.bones:
            return
        by_id = {e["uuid"]: e for e in self.elements}
        o = self.bones["h_head"].origin

        def walk(b):
            for cid in b.cubes:
                e = by_id[cid]
                for key in ("from", "to"):
                    e[key] = [round(o[i] + (e[key][i] - o[i]) * HEAD_K, 3) for i in range(3)]
            for c in b.children:
                walk(c)
        walk(self.bones["h_head"])
        self.chibi = lambda: None

    def oriented(self):
        """FreeMinecraftModels treats model -X as 'forward' (checked in-game: heads drawn at -Z walked sideways,
        facing right). The animals are drawn with the head at -Z, so turn the whole model a quarter:
        (x, z) -> (z, -x) puts -Z on -X. The crab is already drawn facing -X."""
        if self.key() in ("crab",):
            return
        t = lambda v: [v[2], v[1], -v[0]]
        for e in self.elements:
            a, b = t(e["from"]), t(e["to"])
            e["from"] = [round(min(x, y), 3) for x, y in zip(a, b)]
            e["to"] = [round(max(x, y), 3) for x, y in zip(a, b)]
            e["origin"] = [round(v, 3) for v in t(e["origin"])]
        for bn in self.bones.values():
            bn.origin = t(bn.origin)
        for an in self.anims:
            for ani in an["animators"].values():
                for kf in ani["keyframes"]:
                    for dp in kf["data_points"]:
                        x, y, z = float(dp["x"]), float(dp["y"]), float(dp["z"])
                        # position moves like a point; a rotation about X becomes one about Z (sign flipped) and back
                        nx, ny, nz = (z, y, -x) if kf["channel"] == "position" else (z, y, -x)
                        dp["x"], dp["y"], dp["z"] = str(round(nx, 2)), str(round(ny, 2)), str(round(nz, 2))

    def scaled(self):
        """Scale every coordinate (cubes, bone origins, animation positions) by size(); rotations stay."""
        k = self.size()
        if k == 1.0:
            return
        for e in self.elements:
            for key in ("from", "to", "origin"):
                e[key] = [round(v * k, 3) for v in e[key]]
        for b in self.bones.values():
            b.origin = [v * k for v in b.origin]
        for an in self.anims:
            for ani in an["animators"].values():
                for kf in ani["keyframes"]:
                    if kf["channel"] == "position":
                        for dp in kf["data_points"]:
                            for ax in "xyz":
                                dp[ax] = str(round(float(dp[ax]) * k, 2))

    def bbmodel(self):
        self.chibi()
        self.oriented()
        self.scaled()
        self.oriented = self.scaled = lambda: None
        return {"meta": {"format_version": "4.9", "model_format": "free", "box_uv": False},
                "name": self.name, "model_identifier": "", "visible_box": [1, 1, 0], "variable_placeholders": "",
                "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
                "resolution": {"width": RES, "height": RES}, "elements": self.elements,
                "outliner": [self.outline(b) for b in self.roots],
                "textures": [{"path": "", "name": "wildlife.png", "folder": "", "namespace": "", "id": "0", "width": RES,
                              "height": RES, "uv_width": RES, "uv_height": RES, "particle": False, "render_mode": "default",
                              "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
                              "frame_interpolate": False, "visible": True, "internal": True, "saved": False, "uuid": U(),
                              "relative_path": "", "source": TEX}],
                "animations": self.anims}


def wave(n, length, amp, phase=0.0, axis=0, base=0.0):
    out = []
    for i in range(n + 1):
        r = [0.0, 0.0, 0.0]
        r[axis] = base + amp * math.sin(2 * math.pi * (i / n) + phase)
        out.append((length * i / n, tuple(r)))
    return out


def hold(length, v):
    return [(0, v), (length, v)]


def death(m, body="body", drop=2):
    m.anim("death", 0.6, {body: {"rotation": [(0, (0, 0, 0)), (0.5, (0, 0, 90)), (0.6, (0, 0, 90))],
                                "position": [(0, (0, 0, 0)), (0.5, (0, -drop, 0)), (0.6, (0, -drop, 0))]}}, loop="hold")


def quad_walk(m, period, amp, head=3, tail=10, extra=None):
    t = {"leg_fl": {"rotation": wave(8, period, amp)}, "leg_br": {"rotation": wave(8, period, amp)},
         "leg_fr": {"rotation": wave(8, period, -amp)}, "leg_bl": {"rotation": wave(8, period, -amp)},
         "h_head": {"rotation": wave(8, period, head, 0.5)}, "tail": {"rotation": wave(8, period, tail, 0, axis=1)},
         "body": {"position": [(0, (0, 0, 0)), (period / 4, (0, 0.25, 0)), (period / 2, (0, 0, 0)), (3 * period / 4, (0, 0.25, 0)), (period, (0, 0, 0))]}}
    if extra:
        t.update(extra)
    m.anim("walk", period, t)


def gait(m, period, amp, head=3, tail=10, style="walk", bob=0.15, extra=None):
    """The proper walk cycle: real four-legged animals do NOT trot when they stroll.
    style 'walk' = lateral sequence (back-left, front-left, back-right, front-right each their own beat),
    'pace' = same-side pairs (camels, giraffes; good for the elephant), 'trot' = diagonals (only for small
    scuttlers like the hedgehog). Legs pivot at the hip/shoulder, so one sine per leg with a phase offset;
    the body dips once per beat - half the bob of the old diagonal trot."""
    phases = {"walk": {"leg_bl": 0.0, "leg_fl": 0.25, "leg_br": 0.5, "leg_fr": 0.75},
              "pace": {"leg_bl": 0.0, "leg_fl": 0.0, "leg_br": 0.5, "leg_fr": 0.5},
              "trot": {"leg_bl": 0.0, "leg_fr": 0.0, "leg_br": 0.5, "leg_fl": 0.5}}[style]
    t = {ln: {"rotation": wave(8, period, amp, 2 * math.pi * p)} for ln, p in phases.items()}
    t["h_head"] = {"rotation": wave(8, period, head, math.pi * 0.5)}
    if tail:
        t["tail"] = {"rotation": wave(8, period, tail, 0, axis=1)}
    t["body"] = {"position": [(0, (0, 0, 0)), (period / 4, (0, bob, 0)), (period / 2, (0, 0, 0)),
                              (3 * period / 4, (0, bob, 0)), (period, (0, 0, 0))]}
    if extra:
        t.update(extra)
    m.anim("walk", period, t)


def hop_walk(m, period=0.45, hop=0.5, fold=80):
    """Ground birds hop: both feet kick together, the body rises and lands, the folded wings give
    one small flutter per hop (a real sparrow's walk, not a flight flap - the old 0.18 s flap-as-walk
    made every songbird vibrate). fold = the resting wing angle the flutter swings from."""
    legs = wave(4, period, 26)
    m.anim("walk", period, {"leg_l": {"rotation": legs}, "leg_r": {"rotation": legs},
                            "body": {"position": [(0, (0, 0, 0)), (period * 0.3, (0, hop, 0)),
                                                  (period * 0.55, (0, -0.08, 0)), (period, (0, 0, 0))]},
                            "wing_l": {"rotation": [(0, (0, 0, -fold)), (period * 0.3, (0, 0, -fold + 18)), (period * 0.6, (0, 0, -fold)), (period, (0, 0, -fold))]},
                            "wing_r": {"rotation": [(0, (0, 0, fold)), (period * 0.3, (0, 0, fold - 18)), (period * 0.6, (0, 0, fold)), (period, (0, 0, fold))]},
                            "h_head": {"rotation": [(0, (0, 0, 0)), (period * 0.3, (-8, 0, 0)), (period, (0, 0, 0))]}})


def stilt_walk(m, period=1.4, lift=18):
    """Long-legged wader (crane, heron): the foot lifts slowly, holds, and is placed - a keyframed
    step, not a sine. The neck folds forward with each step, the body dips as the foot lands."""
    def step(offset):
        pts = [(0, (0, 0, 0)), (0.30 * period, (lift, 0, 0)), (0.45 * period, (lift, 0, 0)), (0.62 * period, (0, 0, 0)), (period, (0, 0, 0))]
        return [(t + offset, v) for t, v in pts]
    m.anim("walk", period, {"leg_l": {"rotation": step(0)}, "leg_r": {"rotation": step(0.5 * period)},
                            "body": {"position": [(0, (0, 0, 0)), (0.62 * period, (0, 0.18, 0)), (0.8 * period, (0, 0, 0)), (period, (0, 0, 0))]},
                            "h_head": {"rotation": [(0, (0, 0, 0)), (0.3 * period, (6, 0, 0)), (0.62 * period, (0, 0, 0)), (period, (0, 0, 0))]}})


def quad_idle(m, tail=6, ear=None):
    t = {"h_head": {"rotation": [(0, (0, 0, 0)), (1.2, (0, 12, 0)), (2.0, (0, 12, 0)), (3.0, (0, -8, 0)), (4.0, (0, 0, 0))]},
         "tail": {"rotation": wave(8, 4.0, tail, 0, axis=1)},
         "body": {"position": [(0, (0, 0, 0)), (2.0, (0, 0.15, 0)), (4.0, (0, 0, 0))]}}
    m.anim("idle", 4.0, t)


def leg(m, name, x, z, top, upper, lower, wu, wl, col, foot=None, foot_h=0.6, parent="body", knee=0.0):
    """Two-part leg in one bone (pivot at the top): thicker upper part, thinner lower part, hoof/paw."""
    m.bone(name, (x, top, z), parent)
    m.box(name, x - wu / 2, top - upper, z - wu / 2, x + wu / 2, top, z + wu / 2, col)
    m.box(name, x - wl / 2, foot_h if foot else 0, z - wl / 2 + knee, x + wl / 2, top - upper + 0.3, z + wl / 2 + knee, col + "_d" if not col.endswith("_d") else col)
    if foot:
        m.box(name, x - wl / 2 - 0.15, 0, z - wl / 2 - 0.2 + knee, x + wl / 2 + 0.15, foot_h, z + wl / 2 + 0.15 + knee, foot)

# ------------------------------------------------------------------ mammals

def deer(m, antlers=True):
    c = "deer"
    m.bone("body", (0, 12.5, 0))
    # torso: chest, barrel, haunch (z -9 .. 10), back a shade darker, white belly
    m.box("body", -2.8, 9.0, -9.0, 2.8, 15.6, -3.0, c)
    m.box("body", -2.5, 9.8, -3.0, 2.5, 15.2, 4.0, c)
    m.box("body", -2.7, 9.4, 4.0, 2.7, 15.8, 9.8, c)
    m.box("body", -1.8, 15.5, -8.5, 1.8, 16.0, 9.0, c + "_d")
    m.box("body", -2.0, 8.9, -6.0, 2.0, 9.6, 7.0, "deer_white")
    m.box("body", -2.1, 9.0, -9.2, 2.1, 12.0, -8.4, "deer_white")  # chest
    m.bone("tail", (0, 15.2, 9.8), "body")
    m.box("tail", -0.8, 13.0, 9.8, 0.8, 15.6, 11.0, c)
    m.box("tail", -0.7, 12.8, 10.9, 0.7, 15.2, 11.3, "deer_white")
    # neck (stepped forward-up) + head
    m.bone("h_head", (0, 15.0, -8.0), "body")
    m.box("h_head", -1.5, 13.5, -10.2, 1.5, 17.5, -7.0, c)
    m.box("h_head", -1.35, 16.5, -11.6, 1.35, 20.0, -8.6, c)
    m.box("h_head", -1.0, 14.0, -10.4, 1.0, 17.0, -10.1, "deer_white")  # throat patch
    m.box("h_head", -1.8, 19.2, -14.6, 1.8, 22.4, -10.4, c)            # skull
    m.box("h_head", -1.2, 19.4, -17.6, 1.2, 21.4, -14.6, c + "_d")      # muzzle
    m.box("h_head", -0.9, 20.4, -17.9, 0.9, 21.4, -17.4, "nose")
    m.box("h_head", -1.0, 19.3, -17.0, 1.0, 19.6, -14.8, "deer_white")  # chin
    m.sym("h_head", 1.75, 21.2, -13.6, 1.85, 21.9, -12.8, "eye")
    m.sym("h_head", 1.4, 22.0, -11.6, 2.0, 23.2, -10.9, c)              # ears: base ...
    m.sym("h_head", 2.0, 22.6, -11.7, 3.6, 24.0, -11.0, c)              # ... and flaring out
    m.sym("h_head", 2.1, 22.8, -11.75, 3.4, 23.8, -11.6, "pink")
    if antlers:
        a = "antler"
        m.sym("h_head", 0.8, 22.4, -12.2, 1.4, 25.0, -11.6, a)
        m.sym("h_head", 1.2, 24.6, -13.6, 1.8, 25.2, -11.6, a)
        m.sym("h_head", 1.6, 25.0, -15.4, 2.2, 25.6, -13.4, a)
        m.sym("h_head", 1.5, 25.2, -12.9, 1.9, 27.0, -12.5, a)
        m.sym("h_head", 1.8, 25.5, -14.6, 2.2, 27.2, -14.2, a)
        m.sym("h_head", 2.0, 25.6, -15.8, 2.4, 26.8, -15.4, a)
    for name, x, z in (("leg_fl", 1.6, -7.0), ("leg_fr", -1.6, -7.0), ("leg_bl", 1.7, 7.6), ("leg_br", -1.7, 7.6)):
        leg(m, name, x, z, 10.2, 4.8, 5.4, 1.9, 1.15, c, foot="hoof", knee=0.3 if z > 0 else 0.0)
    gait(m, 0.95, 20, head=3, tail=12, style="walk", bob=0.18)
    quad_idle(m, 10)
    m.anim("eat", 2.4, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (55, 0, 0)), (1.9, (55, 0, 0)), (2.4, (0, 0, 0))],
                                   "position": [(0, (0, 0, 0)), (0.5, (0, -1.5, 0)), (1.9, (0, -1.5, 0)), (2.4, (0, 0, 0))]}}, loop="once")
    death(m, drop=4)


def bear(m):
    c = "fur_brown"
    m.bone("body", (0, 14, 0))
    m.box("body", -6.5, 8.0, -14.0, 6.5, 20.0, -4.0, c)          # chest
    m.box("body", -4.8, 19.8, -12.5, 4.8, 21.8, -5.0, c + "_d")  # shoulder hump
    m.box("body", -6.2, 8.4, -4.0, 6.2, 19.0, 6.0, c)            # barrel
    m.box("body", -6.0, 8.2, 6.0, 6.0, 18.4, 14.0, c)            # rump
    m.box("body", -5.0, 7.9, -11.0, 5.0, 8.6, 12.0, c + "_d")    # belly
    m.box("body", -4.0, 18.6, -3.0, 4.0, 19.4, 12.0, c + "_l")   # sun-bleached back
    m.bone("tail", (0, 16.5, 14), "body")
    m.box("tail", -1.0, 15.5, 14.0, 1.0, 17.3, 15.4, c)
    m.bone("h_head", (0, 15.0, -14.0), "body")
    m.box("h_head", -4.5, 10.5, -17.5, 4.5, 18.0, -13.5, c)      # neck ruff
    m.box("h_head", -4.6, 10.8, -23.5, 4.6, 18.4, -17.0, c)      # skull
    m.box("h_head", -2.6, 10.8, -27.5, 2.6, 14.8, -23.5, "fur_light")  # muzzle
    m.box("h_head", -1.6, 13.4, -28.0, 1.6, 15.0, -27.2, "nose")
    m.box("h_head", -2.2, 10.6, -26.8, 2.2, 11.4, -23.6, "fur_light_d")
    m.sym("h_head", 4.55, 15.4, -22.0, 4.7, 16.2, -21.0, "eye")
    m.sym("h_head", 2.8, 18.2, -19.5, 5.0, 20.4, -18.3, c + "_d")  # round ears
    m.sym("h_head", 3.2, 18.6, -19.6, 4.6, 19.9, -19.4, "fur_light_d")
    for name, x, z in (("leg_fl", 4.2, -10.0), ("leg_fr", -4.2, -10.0), ("leg_bl", 4.0, 10.5), ("leg_br", -4.0, 10.5)):
        m.bone(name, (x, 9.5, z), "body")
        m.box(name, x - 2.6, 4.5, z - 2.6, x + 2.6, 10.0, z + 2.6, c)
        m.box(name, x - 2.2, 1.0, z - 2.2, x + 2.2, 4.7, z + 2.2, c + "_d")
        m.box(name, x - 2.5, 0.0, z - 2.9, x + 2.5, 1.2, z + 2.3, "fur_dark")
        for k in (-1.6, -0.5, 0.6, 1.7):
            m.box(name, x + k - 0.3, 0.0, z - 3.5, x + k + 0.3, 0.6, z - 2.8, "claw")
    gait(m, 1.3, 15, head=3, tail=5, style="walk", bob=0.2)
    quad_idle(m, 4)
    m.anim("sleep", 2.0, {"body": {"position": hold(2.0, (0, -7.5, 0))},
                          "leg_fl": {"rotation": hold(2.0, (-85, 0, 0))}, "leg_fr": {"rotation": hold(2.0, (-85, 0, 0))},
                          "leg_bl": {"rotation": hold(2.0, (85, 0, 0))}, "leg_br": {"rotation": hold(2.0, (85, 0, 0))},
                          "h_head": {"rotation": hold(2.0, (15, 25, 0)), "position": hold(2.0, (0, -1, 0))}})
    m.anim("eat", 2.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.4, (40, 0, 0)), (1.6, (40, 0, 0)), (2.0, (0, 0, 0))]}}, loop="once")
    death(m, drop=6)


def boar(m):
    c = "boar"
    m.bone("body", (0, 9.5, 0))
    m.box("body", -4.0, 6.0, -8.5, 4.0, 14.0, -2.5, c)        # deep chest, high shoulders
    m.box("body", -3.7, 6.4, -2.5, 3.7, 13.0, 4.0, c)
    m.box("body", -3.4, 6.6, 4.0, 3.4, 12.0, 9.0, c)          # narrow low rump
    m.box("body", -1.0, 13.8, -9.0, 1.0, 15.4, 1.0, "fur_black")   # bristle mane
    m.box("body", -0.8, 12.8, 1.0, 0.8, 13.6, 6.0, "fur_black")
    m.box("body", -3.0, 5.9, -6.0, 3.0, 6.6, 6.0, c + "_d")
    m.bone("tail", (0, 11.5, 9.0), "body")
    m.box("tail", -0.3, 8.5, 9.0, 0.3, 11.6, 9.6, c + "_d")
    m.box("tail", -0.6, 7.8, 9.0, 0.6, 8.8, 9.8, "fur_black")
    m.bone("h_head", (0, 10.5, -8.5), "body")
    m.box("h_head", -3.0, 6.8, -12.5, 3.0, 13.0, -8.0, c)     # wedge head: high at the back ...
    m.box("h_head", -2.4, 6.8, -15.0, 2.4, 11.2, -12.5, c)    # ... sloping down ...
    m.box("h_head", -1.7, 6.8, -17.6, 1.7, 9.6, -15.0, c + "_d")  # ... to the long snout
    m.box("h_head", -1.5, 7.0, -18.0, 1.5, 9.4, -17.6, "pink_d")  # snout disc
    m.box("h_head", -1.8, 6.6, -16.0, 1.8, 7.0, -11.0, "fur_black")
    for sx in (1, -1):
        m.box("h_head", sx * 1.8 - 0.3, 7.6, -16.4, sx * 1.8 + 0.3, 8.4, -15.6, "tusk")
        m.box("h_head", sx * 2.1 - 0.25, 8.4, -16.1, sx * 2.1 + 0.25, 10.0, -15.7, "tusk")
    m.sym("h_head", 2.95, 11.0, -11.6, 3.1, 11.7, -10.9, "eye")
    m.sym("h_head", 1.6, 13.0, -9.2, 2.8, 15.0, -8.6, c + "_d")   # pointed ears
    for name, x, z in (("leg_fl", 2.4, -6.5), ("leg_fr", -2.4, -6.5), ("leg_bl", 2.2, 7.0), ("leg_br", -2.2, 7.0)):
        leg(m, name, x, z, 6.8, 3.4, 3.4, 2.2, 1.5, c, foot="hoof")
    gait(m, 0.8, 16, head=4, tail=10, style="walk", bob=0.18)
    quad_idle(m, 12)
    death(m, drop=3)


def capybara(m, yuzu=False):
    c = "capy"
    m.bone("body", (0, 8, 0))
    m.box("body", -4.0, 4.5, -8.0, 4.0, 11.0, -3.0, c)
    m.box("body", -4.3, 4.3, -3.0, 4.3, 11.4, 4.0, c)
    m.box("body", -4.0, 4.6, 4.0, 4.0, 11.8, 9.2, c)       # rump higher than the shoulders
    m.box("body", -3.0, 11.0, -6.0, 3.0, 11.9, 8.0, c + "_d")
    m.box("body", -3.4, 4.2, -6.0, 3.4, 4.8, 7.0, c + "_l")
    m.bone("h_head", (0, 9.5, -8.0), "body")
    m.box("h_head", -2.9, 6.0, -12.0, 2.9, 11.2, -7.5, c)     # big blunt head
    m.box("h_head", -2.6, 6.0, -15.6, 2.6, 10.4, -12.0, c)
    m.box("h_head", -2.4, 6.0, -15.9, 2.4, 9.6, -15.6, c + "_d")
    m.sym("h_head", 0.6, 8.8, -15.95, 1.4, 9.3, -15.85, "nose")
    m.sym("h_head", 2.85, 10.0, -12.2, 3.0, 10.6, -11.6, "eye")   # eyes high on the head
    m.sym("h_head", 1.6, 11.2, -9.2, 2.6, 12.2, -8.4, c + "_d")   # small round ears
    if yuzu:
        m.box("h_head", -1.5, 11.2, -13.0, 1.5, 14.0, -10.0, "yuzu")
        m.box("h_head", -0.3, 14.0, -11.8, 0.9, 14.5, -11.0, "leaf")
    for name, x, z in (("leg_fl", 2.6, -6.0), ("leg_fr", -2.6, -6.0), ("leg_bl", 2.6, 7.0), ("leg_br", -2.6, 7.0)):
        leg(m, name, x, z, 5.2, 2.8, 2.4, 2.4, 2.0, c, foot=c + "_d", foot_h=0.5)
    gait(m, 1.05, 14, head=2, tail=0, style="walk", bob=0.12)
    quad_idle(m, 0)
    death(m, drop=3)


def hedgehog(m, spine="spine"):
    """European hedgehog, ~25 cm long (real size would be 4 px; drawn at 6 px)."""
    m.bone("body", (0, 2, 0))
    m.box("body", -1.8, 0.8, -1.8, 1.8, 3.4, 2.6, spine)
    m.box("body", -1.4, 3.4, -1.4, 1.4, 4.0, 2.2, spine + "_d")
    rnd = random.Random(3)
    for i in range(16):
        x, z = rnd.uniform(-1.6, 1.3), rnd.uniform(-1.4, 2.2)
        y = 3.2 + rnd.uniform(0, 0.6)
        m.box("body", x, y, z, x + 0.35, y + 0.55, z + 0.35, "spine_tip" if i % 3 == 0 else spine + "_l")
    m.box("body", -1.5, 0.6, -1.2, 1.5, 0.9, 2.0, "hedgehog_face_d")
    m.bone("h_head", (0, 1.8, -1.8), "body")
    m.box("h_head", -1.2, 0.8, -3.2, 1.2, 2.6, -1.6, "hedgehog_face")
    m.box("h_head", -0.6, 1.0, -4.2, 0.6, 1.8, -3.2, "hedgehog_face")
    m.box("h_head", -0.3, 1.2, -4.45, 0.3, 1.7, -4.15, "nose")
    m.sym("h_head", 1.15, 2.0, -2.9, 1.25, 2.35, -2.5, "eye")
    m.sym("h_head", 0.7, 2.6, -2.0, 1.2, 3.0, -1.7, "hedgehog_face_d")
    for name, x, z in (("leg_fl", 1.0, -1.2), ("leg_fr", -1.0, -1.2), ("leg_bl", 1.0, 1.8), ("leg_br", -1.0, 1.8)):
        m.bone(name, (x, 0.9, z), "body")
        m.box(name, x - 0.35, 0, z - 0.35, x + 0.35, 0.95, z + 0.35, "hedgehog_face_d")
    gait(m, 0.42, 24, head=3, tail=0, style="trot", bob=0.12)
    quad_idle(m, 0)
    m.anim("curl", 1.0, {"h_head": {"rotation": hold(1.0, (75, 0, 0)), "position": hold(1.0, (0, -0.2, 1.2))},
                         "body": {"rotation": hold(1.0, (-20, 0, 0))},
                         "leg_fl": {"position": hold(1.0, (0, 0.5, 0.6))}, "leg_fr": {"position": hold(1.0, (0, 0.5, 0.6))},
                         "leg_bl": {"position": hold(1.0, (0, 0.5, -0.6))}, "leg_br": {"position": hold(1.0, (0, 0.5, -0.6))}}, loop="hold")
    death(m, drop=0.5)

def zebra(m):
    """Plains zebra, ~1.3 m at the shoulder: stocky, upright bristly mane, black stripes on a white coat."""
    w, k = "zebra_white", "zebra_black"
    m.bone("body", (0, 13, 0))
    m.box("body", -3.4, 10.0, -9.0, 3.4, 17.0, -3.0, w)          # chest
    m.box("body", -3.2, 10.4, -3.0, 3.2, 17.0, 4.0, w)           # barrel
    m.box("body", -3.3, 10.4, 4.0, 3.3, 17.2, 10.0, w)           # rump
    m.box("body", -2.2, 17.0, -8.0, 2.2, 17.4, 9.0, k)           # dark line down the back
    for z in (-8.2, -6.4, -4.6, -2.8, -1.0, 0.8, 2.6, 4.4, 6.2, 8.0):   # stripes round the body
        m.box("body", -3.45, 10.6, z, 3.45, 17.1, z + 0.9, k)
    m.bone("tail", (0, 16.0, 10.0), "body")
    m.box("tail", -0.5, 11.0, 10.0, 0.5, 16.4, 11.0, w)
    m.box("tail", -0.7, 9.0, 9.9, 0.7, 12.0, 11.2, k)
    m.bone("h_head", (0, 16.0, -8.5), "body")
    m.box("h_head", -1.9, 14.5, -11.0, 1.9, 19.0, -7.0, w)       # neck, stepped up and forward
    m.box("h_head", -1.8, 17.5, -12.4, 1.8, 22.0, -9.0, w)
    for z in (-10.4, -8.4):
        m.box("h_head", -2.0, 14.8, z, 2.0, 18.8, z + 0.8, k)
    for z in (-12.0, -10.2):
        m.box("h_head", -1.9, 17.8, z, 1.9, 21.8, z + 0.8, k)
    m.box("h_head", -1.9, 21.0, -16.0, 1.9, 24.4, -11.4, w)      # skull
    for z in (-15.0, -13.2):
        m.box("h_head", -1.95, 21.0, z, 1.95, 24.45, z + 0.6, k)
    m.box("h_head", -1.6, 20.4, -19.8, 1.6, 23.2, -16.0, "fur_dark")   # dark muzzle
    m.box("h_head", -1.2, 21.0, -20.1, 1.2, 22.8, -19.7, "nose")
    m.sym("h_head", 1.85, 22.6, -14.2, 1.95, 23.4, -13.5, "eye")
    m.sym("h_head", 0.8, 24.4, -13.6, 1.8, 27.6, -12.6, w)       # ears
    m.sym("h_head", 0.8, 26.8, -13.6, 1.8, 27.6, -12.6, k)
    for y1, y2, z1, z2 in ((19.0, 21.0, -8.2, -7.2), (21.5, 23.5, -10.4, -9.4), (23.8, 25.6, -12.4, -11.4)):
        m.box("h_head", -0.5, y1, z1, 0.5, y2, z2, k)             # upright mane
    m.box("h_head", -0.4, 24.4, -14.0, 0.4, 26.4, -13.4, k)       # forelock
    for name, x, z in (("leg_fl", 2.0, -7.0), ("leg_fr", -2.0, -7.0), ("leg_bl", 2.1, 7.4), ("leg_br", -2.1, 7.4)):
        knee = 0.3 if z > 0 else 0.0
        leg(m, name, x, z, 10.4, 4.8, 5.4, 2.3, 1.5, w, foot="hoof", knee=knee)
        for y in (9.4, 8.0, 6.6):
            m.box(name, x - 1.2, y, z - 1.2, x + 1.2, y + 0.8, z + 1.2, k)
        for y in (5.0, 3.6, 2.2):
            m.box(name, x - 0.8, y, z - 0.8 + knee, x + 0.8, y + 0.8, z + 0.8 + knee, k)
    gait(m, 0.95, 18, head=3, tail=12, style="walk", bob=0.16)
    quad_idle(m, 10)
    m.anim("eat", 2.4, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (60, 0, 0)), (1.9, (60, 0, 0)), (2.4, (0, 0, 0))],
                                   "position": [(0, (0, 0, 0)), (0.5, (0, -2.0, 0)), (1.9, (0, -2.0, 0)), (2.4, (0, 0, 0))]}}, loop="once")
    death(m, drop=4)


def elephant(m):
    """African elephant, ~3 m at the shoulder (drawn at 0.9x): pillar legs, big flapping ears, a trunk that swings."""
    g = "elephant"
    m.bone("body", (0, 36, 0))
    m.box("body", -10.0, 26.0, -17.0, 10.0, 48.0, -8.0, g)       # chest and shoulders
    m.box("body", -11.0, 24.0, -8.0, 11.0, 49.0, 8.0, g)         # barrel
    m.box("body", -10.0, 26.0, 8.0, 10.0, 47.0, 18.0, g)         # rump
    m.box("body", -7.0, 48.0, -14.0, 7.0, 51.0, 12.0, g + "_d")  # spine ridge
    m.box("body", -8.0, 23.5, -12.0, 8.0, 24.4, 14.0, g + "_l")  # belly
    for z, half in ((-12.0, 10.1), (-6.0, 11.1), (0.0, 11.1), (6.0, 11.1), (12.0, 10.1)):   # skin folds
        m.box("body", -half, 28.0, z, half, 46.0, z + 0.8, g + "_d")
    m.bone("tail", (0, 44.0, 18.0), "body")
    m.box("tail", -0.6, 30.0, 18.0, 0.6, 44.0, 19.2, g + "_d")
    m.box("tail", -1.2, 26.0, 17.8, 1.2, 31.0, 19.4, "fur_black")
    m.bone("h_head", (0, 44.0, -16.0), "body")
    m.box("h_head", -8.0, 34.0, -26.0, 8.0, 52.0, -14.0, g)      # skull
    m.box("h_head", -6.5, 52.0, -24.0, 6.5, 55.0, -15.0, g)      # domed forehead
    for y1, y2, z1, z2, hw in ((30, 40, -30, -25, 4.2), (21, 32, -33.5, -28.5, 3.6), (12, 23, -36, -31.5, 3.1), (4.5, 14, -37.5, -33.5, 2.6)):
        m.box("h_head", -hw, y1, z1, hw, y2, z2, g)               # the trunk, in narrowing steps
    for y, hw, z1, z2 in ((36.0, 4.3, -30.1, -24.9), (27.0, 3.7, -33.6, -28.4), (18.0, 3.2, -36.1, -31.4), (9.0, 2.7, -37.6, -33.4)):
        m.box("h_head", -hw, y, z1, hw, y + 0.8, z2, g + "_d")    # trunk rings
    m.box("h_head", -1.9, 1.5, -38.2, 1.9, 6.0, -33.9, "pink_d")  # tip
    m.sym("h_head", 4.8, 29.0, -28.0, 6.2, 36.0, -25.6, "tusk")   # tusks, curving down and forward
    m.sym("h_head", 4.8, 26.5, -32.5, 6.2, 30.0, -28.0, "tusk")
    m.sym("h_head", 4.9, 25.5, -35.0, 6.1, 28.0, -32.5, "tusk")
    m.sym("h_head", 7.9, 46.0, -23.0, 8.3, 47.5, -22.0, "eye")
    for side, sx in (("ear_l", 1), ("ear_r", -1)):
        m.bone(side, (sx * 8.0, 50.0, -15.0), "h_head")
        a, b = (8.0, 9.2) if sx > 0 else (-9.2, -8.0)
        ia, ib = (9.2, 9.5) if sx > 0 else (-9.5, -9.2)
        m.box(side, a, 36.0, -22.0, b, 54.0, -9.0, g + "_d")      # the big flat ear
        m.box(side, ia, 40.0, -20.0, ib, 52.0, -11.0, "elephant_ear")
    for name, x, z in (("leg_fl", 6.5, -11.0), ("leg_fr", -6.5, -11.0), ("leg_bl", 6.5, 11.5), ("leg_br", -6.5, 11.5)):
        m.bone(name, (x, 26.0, z), "body")
        m.box(name, x - 4.2, 10.0, z - 4.2, x + 4.2, 26.0, z + 4.2, g)
        m.box(name, x - 3.8, 2.0, z - 3.8, x + 3.8, 10.2, z + 3.8, g + "_d")
        m.box(name, x - 4.2, 0.0, z - 4.4, x + 4.2, 2.4, z + 4.2, g + "_d")
        for dx in (-2.8, -0.9, 0.9, 2.8):
            m.box(name, x + dx - 0.5, 0.0, z - 4.9, x + dx + 0.5, 1.2, z - 4.3, "tusk")   # toenails
    # ears flap front-back about the X axis (the old Y-axis wave made them swing like flags)
    ears = {"ear_l": {"rotation": wave(8, 3.0, 6)}, "ear_r": {"rotation": wave(8, 3.0, 6)}}
    gait(m, 2.0, 13, head=4, tail=8, style="walk", bob=0.2, extra=ears)
    m.anim("idle", 4.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (1.0, (0, 10, 0)), (2.0, (0, 10, 0)), (3.0, (0, -8, 0)), (4.0, (0, 0, 0))]},
                         "ear_l": {"rotation": wave(8, 4.0, 14)}, "ear_r": {"rotation": wave(8, 4.0, 14)},
                         "tail": {"rotation": wave(8, 4.0, 8, 0, axis=1)},
                         "body": {"position": [(0, (0, 0, 0)), (2.0, (0, 0.2, 0)), (4.0, (0, 0, 0))]}})
    m.anim("eat", 2.6, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (28, 0, 0)), (2.0, (28, 0, 0)), (2.6, (0, 0, 0))],
                                   "position": [(0, (0, 0, 0)), (0.5, (0, -3.0, -2.0)), (2.0, (0, -3.0, -2.0)), (2.6, (0, 0, 0))]}}, loop="once")
    death(m, drop=10)


# ------------------------------------------------------------------ birds

def duck(m):
    """Mallard drake, 55-60 cm."""
    m.bone("body", (0, 4, 0))
    m.box("body", -2.2, 2.2, -3.6, 2.2, 6.0, -1.0, "chestnut")      # breast
    m.box("body", -2.3, 2.0, -1.0, 2.3, 5.8, 2.4, "duck_gray")      # flanks
    m.box("body", -2.0, 2.4, 2.4, 2.0, 5.6, 4.6, "duck_gray_d")
    m.box("body", -1.4, 3.6, 4.6, 1.4, 5.2, 5.8, "black")            # rump
    m.box("body", -0.6, 5.0, 5.2, 0.6, 5.8, 5.6, "black")            # curled tail feather
    m.box("body", -1.0, 3.8, 5.8, 1.0, 4.6, 6.6, "white")
    for sx in (1, -1):                                                # folded wings
        x1, x2 = (2.2, 2.45) if sx > 0 else (-2.45, -2.2)
        m.box("body", x1, 3.8, -1.0, x2, 5.6, 4.4, "duck_brown")
        m.box("body", x1 + 0.01 * sx, 3.9, 2.2, x2 + 0.01 * sx, 4.5, 3.4, "speculum")
    m.bone("h_head", (0, 6.0, -2.6), "body")
    m.box("h_head", -0.9, 5.6, -3.4, 0.9, 7.6, -2.0, "duck_green")
    m.box("h_head", -0.95, 6.2, -3.45, 0.95, 6.5, -1.95, "white")    # neck ring
    m.box("h_head", -1.2, 7.4, -4.4, 1.2, 9.4, -2.2, "duck_green")
    m.box("h_head", -0.75, 7.6, -6.4, 0.75, 8.3, -4.4, "bill")
    m.box("h_head", -0.3, 8.0, -6.5, 0.3, 8.3, -6.3, "black")
    m.sym("h_head", 1.15, 8.5, -3.9, 1.25, 8.9, -3.5, "eye")
    for name, x in (("leg_l", 1.0), ("leg_r", -1.0)):
        m.bone(name, (x, 2.2, 0.6), "body")
        m.box(name, x - 0.25, 0.3, 0.35, x + 0.25, 2.3, 0.85, "web")
        m.box(name, x - 0.7, 0.0, -0.6, x + 0.7, 0.3, 0.9, "web")
    p = 0.7
    m.anim("walk", p, {"leg_l": {"rotation": wave(6, p, 22)}, "leg_r": {"rotation": wave(6, p, -22)},
                       "body": {"rotation": wave(6, p, 4, 0, axis=2)}, "h_head": {"rotation": wave(6, p, 6, 0.5)}})
    m.anim("idle", 3.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.8, (0, 25, 0)), (1.4, (0, 25, 0)), (2.0, (15, -20, 0)), (3.0, (0, 0, 0))]},
                         "body": {"position": [(0, (0, 0, 0)), (1.5, (0, 0.15, 0)), (3.0, (0, 0, 0))]}})
    death(m, drop=1)


def songbird(m, col="jay", breast="white", cap=None):
    """Small songbird, ~15-20 cm (drawn ~5 px long)."""
    m.bone("body", (0, 2.2, 0))
    m.box("body", -1.0, 1.0, -1.4, 1.0, 2.9, 1.4, col)
    m.box("body", -0.95, 0.9, -1.5, 0.95, 2.2, 0.4, breast)
    m.box("body", -0.6, 1.9, 1.4, 0.6, 2.3, 3.4, col + "_d")      # tail
    for sx in (1, -1):
        x1, x2 = (0.95, 1.1) if sx > 0 else (-1.1, -0.95)
        m.box("body", x1, 1.6, -0.9, x2, 2.8, 1.8, col + "_d")
    m.bone("h_head", (0, 2.8, -1.0), "body")
    m.box("h_head", -0.8, 2.5, -2.4, 0.8, 4.0, -0.9, cap or col)
    m.box("h_head", -0.25, 2.9, -3.2, 0.25, 3.3, -2.4, "beak")
    m.sym("h_head", 0.75, 3.3, -2.0, 0.85, 3.6, -1.7, "eye")
    for name, x in (("leg_l", 0.4), ("leg_r", -0.4)):
        m.bone(name, (x, 1.0, 0.1), "body")
        m.box(name, x - 0.1, 0, -0.05, x + 0.1, 1.0, 0.25, "beak_d")
        m.box(name, x - 0.25, 0, -0.5, x + 0.25, 0.12, 0.3, "beak_d")
    m.bone("wing_l", (1.0, 2.6, 0.2), "body")
    m.box("wing_l", 1.0, 2.5, -0.9, 3.4, 2.65, 1.4, col + "_d")
    m.bone("wing_r", (-1.0, 2.6, 0.2), "body")
    m.box("wing_r", -3.4, 2.5, -0.9, -1.0, 2.65, 1.4, col + "_d")
    hop_walk(m)   # on the ground a songbird HOPS (the old 0.18 s flap-as-walk made it vibrate)
    m.anim("idle", 2.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.3, (0, 35, 0)), (0.9, (0, 35, 0)), (1.1, (0, -30, 0)),
                                                 (1.7, (0, -30, 0)), (2.0, (0, 0, 0))]},
                         "wing_l": {"rotation": hold(2.0, (0, 0, -80))}, "wing_r": {"rotation": hold(2.0, (0, 0, 80))}})
    death(m, drop=0.5)

# ------------------------------------------------------------------ reptiles

def tortoise(m, shell="tortoise_shell"):
    """Sulcata-like tortoise, shell ~75 cm."""
    m.bone("body", (0, 3.5, 0))
    m.box("body", -4.0, 1.4, -5.2, 4.0, 2.2, 5.2, "scales_l")        # plastron
    m.box("body", -4.6, 2.0, -6.0, 4.6, 4.2, 6.0, shell)
    m.box("body", -4.0, 4.2, -5.2, 4.0, 5.6, 5.2, shell)
    m.box("body", -3.2, 5.6, -4.2, 3.2, 6.6, 4.2, shell)
    m.box("body", -2.0, 6.6, -2.8, 2.0, 7.1, 2.8, shell + "_l")
    for x, z in ((-2.2, -2.6), (2.2, -2.6), (-2.2, 2.6), (2.2, 2.6), (0, 0)):   # scute centres
        m.box("body", x - 0.9, 5.61 if (x, z) != (0, 0) else 7.11, z - 0.9, x + 0.9, 5.7 if (x, z) != (0, 0) else 7.2, z + 0.9, "scute")
    m.box("body", -4.65, 2.0, -0.4, 4.65, 4.2, 0.4, "scute")
    m.box("body", -0.4, 2.0, -6.05, 0.4, 4.2, 6.05, "scute")
    m.bone("tail", (0, 2.2, 6), "body")
    m.box("tail", -0.4, 1.6, 6.0, 0.4, 2.3, 7.0, "scales")
    m.bone("h_head", (0, 3.0, -6.0), "body")
    m.box("h_head", -1.0, 2.2, -7.6, 1.0, 3.6, -5.6, "scales")
    m.box("h_head", -1.2, 2.6, -9.6, 1.2, 4.2, -7.4, "scales")
    m.box("h_head", -0.9, 2.6, -10.1, 0.9, 3.4, -9.5, "scales_d")    # beak
    m.sym("h_head", 1.15, 3.6, -9.0, 1.25, 4.0, -8.6, "eye")
    for name, x, z in (("leg_fl", 3.6, -4.2), ("leg_fr", -3.6, -4.2), ("leg_bl", 3.6, 4.4), ("leg_br", -3.6, 4.4)):
        m.bone(name, (x, 2.6, z), "body")
        m.box(name, x - 1.2, 0.0, z - 1.2, x + 1.2, 2.6, z + 1.2, "scales")
        m.box(name, x - 1.3, 0.0, z - 1.4, x + 1.3, 0.5, z + 1.2, "scales_d")
    gait(m, 2.2, 10, head=2, tail=0, style="walk", extra={"body": {"position": hold(2.2, (0, 0, 0))}})
    quad_idle(m, 0)
    m.anim("hide", 1.0, {"h_head": {"position": hold(1.0, (0, -0.3, 3.0))},
                         "leg_fl": {"position": hold(1.0, (-0.8, 1.2, 0.8))}, "leg_fr": {"position": hold(1.0, (0.8, 1.2, 0.8))},
                         "leg_bl": {"position": hold(1.0, (-0.8, 1.2, -0.8))}, "leg_br": {"position": hold(1.0, (0.8, 1.2, -0.8))},
                         "body": {"position": hold(1.0, (0, -1.2, 0))}}, loop="hold")
    death(m, drop=1)


def lizard(m, col="lizard", spot="lizard_spot"):
    """Wall lizard, ~20 cm with tail (drawn 2x)."""
    m.bone("body", (0, 0.9, 0))
    m.box("body", -0.8, 0.4, -2.0, 0.8, 1.3, 1.6, col)
    m.box("body", -0.65, 0.35, -1.8, 0.65, 0.45, 1.4, col + "_l")
    for z in (-1.4, -0.4, 0.6):
        m.box("body", -0.3, 1.3, z, 0.3, 1.35, z + 0.5, spot)
    m.bone("h_head", (0, 0.9, -2.0), "body")
    m.box("h_head", -0.65, 0.45, -3.2, 0.65, 1.25, -1.9, col)
    m.box("h_head", -0.45, 0.45, -3.9, 0.45, 1.0, -3.2, col)
    m.sym("h_head", 0.6, 0.95, -3.0, 0.7, 1.15, -2.7, "eye")
    m.bone("tail", (0, 0.85, 1.6), "body")
    m.box("tail", -0.5, 0.5, 1.6, 0.5, 1.15, 3.4, col)
    m.box("tail", -0.35, 0.55, 3.4, 0.35, 1.0, 5.2, col + "_d")
    m.box("tail", -0.2, 0.6, 5.2, 0.2, 0.9, 6.8, spot)
    for name, x, z in (("leg_fl", 0.8, -1.4), ("leg_fr", -0.8, -1.4), ("leg_bl", 0.8, 1.2), ("leg_br", -0.8, 1.2)):
        sx = 1 if x > 0 else -1
        m.bone(name, (x, 0.8, z), "body")
        m.box(name, min(x, x + sx * 1.0), 0.45, z - 0.2, max(x, x + sx * 1.0), 0.75, z + 0.2, col + "_d")
        m.box(name, min(x + sx * 0.8, x + sx * 1.3), 0.0, z - 0.4, max(x + sx * 0.8, x + sx * 1.3), 0.55, z + 0.1, col + "_d")
    p = 0.3
    m.anim("walk", p, {"leg_fl": {"rotation": wave(6, p, 35, 0, axis=1)}, "leg_br": {"rotation": wave(6, p, 35, 0, axis=1)},
                       "leg_fr": {"rotation": wave(6, p, -35, 0, axis=1)}, "leg_bl": {"rotation": wave(6, p, -35, 0, axis=1)},
                       "body": {"rotation": wave(6, p, 6, 0, axis=1)}, "tail": {"rotation": wave(6, p, -18, 0, axis=1)}})
    m.anim("idle", 2.5, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.6, (-15, 0, 0)), (1.6, (-15, 0, 0)), (2.5, (0, 0, 0))]},
                         "tail": {"rotation": wave(4, 2.5, 6, 0, axis=1)}})
    death(m, drop=0.3)

# ------------------------------------------------------------------ small invertebrates

def snail(m, shell="shell_brown", band="shell_tan"):
    """Garden snail (real 3 cm shell; drawn ~3 px)."""
    m.bone("body", (0, 0.5, 0))
    m.box("body", -0.6, 0.0, -2.2, 0.6, 0.7, 2.0, "snail_body")
    m.box("body", -0.7, 0.0, -1.6, 0.7, 0.2, 1.6, "snail_body_l")
    m.bone("h_head", (0, 0.6, -1.8), "body")
    m.box("h_head", -0.45, 0.2, -2.9, 0.45, 1.0, -1.8, "snail_body")
    m.sym("h_head", 0.15, 1.0, -2.75, 0.3, 1.9, -2.6, "snail_body")
    m.sym("h_head", 0.1, 1.9, -2.8, 0.35, 2.15, -2.55, "eye")
    m.bone("shell", (0, 1.0, 0.4), "body")
    # spiral coil, seen from the side: outer whorl, inner whorl, apex
    m.box("shell", -0.9, 0.6, -1.0, 0.9, 3.4, 1.8, shell)
    m.box("shell", -0.95, 1.2, -0.4, 0.95, 2.8, 1.2, band)
    m.box("shell", -1.0, 1.6, 0.0, 1.0, 2.4, 0.8, shell + "_d")
    m.box("shell", -1.05, 1.8, 0.2, 1.05, 2.2, 0.6, band + "_l")
    m.anim("walk", 1.6, {"body": {"position": [(0, (0, 0, 0)), (0.8, (0, 0, -0.3)), (1.6, (0, 0, 0))]},
                         "h_head": {"rotation": wave(4, 1.6, 8, 0, axis=1)}})
    m.anim("idle", 3.0, {"h_head": {"rotation": wave(4, 3.0, 10, 0, axis=0)}})
    m.anim("curl", 1.0, {"h_head": {"position": hold(1.0, (0, -0.6, 1.6))}}, loop="hold")
    death(m, drop=0)


def crab(m, col="crab"):
    """Shore crab, built sideways: its own left-right runs along Z (the long axis of the shell), its
    face looks to -X. Walking 'forward' (-Z) therefore looks like a crab walking sideways. Legs stick
    out beside the shell (along Z), the right claw is much bigger (like a fiddler crab).
    Real carapace ~6-8 cm; drawn ~5 px."""
    m.bone("body", (0, 1.4, 0))
    m.box("body", -1.6, 0.8, -2.6, 1.6, 2.1, 2.6, col)
    m.box("body", -1.3, 2.1, -2.2, 1.3, 2.4, 2.2, col + "_d")
    m.box("body", -1.5, 0.7, -2.3, 1.5, 0.8, 2.3, "crab_belly")
    for z in (-0.6, 0.6):                                   # eye stalks at the front edge
        m.box("body", -1.9, 2.0, z - 0.15, -1.6, 2.8, z + 0.15, col + "_d")
        m.box("body", -2.0, 2.8, z - 0.25, -1.7, 3.1, z + 0.25, "eye")
    # claws at the front corners; the right one (+Z) is the big waving claw
    for name, z, k in (("claw_l", -2.3, 0.8), ("claw_r", 2.3, 1.6)):
        sz = -1 if z < 0 else 1
        m.bone(name, (-1.4, 1.6, z), "body")
        m.box(name, -1.4 - 1.2 * k, 1.3, z - 0.3 * k, -1.4, 1.3 + 0.6 * k, z + 0.3 * k, col)          # arm
        m.box(name, -1.4 - 2.4 * k, 1.1, z - 0.6 * k, -1.4 - 1.2 * k, 1.1 + 1.3 * k, z + 0.6 * k, col + "_d")  # palm
        m.box(name, -1.4 - 3.0 * k, 1.1 + 0.7 * k, z - 0.5 * k, -1.4 - 2.4 * k, 1.1 + 1.3 * k, z + 0.5 * k, col + "_d")  # upper pincer
        m.box(name, -1.4 - 2.9 * k, 1.1, z - 0.35 * k, -1.4 - 2.4 * k, 1.1 + 0.4 * k, z + 0.35 * k, col + "_l")    # lower pincer
    legs = {}
    for i, x in enumerate((-0.9, 0.1, 1.1)):               # three walking legs on each side
        for side, z in (("a", 2.6), ("b", -2.6)):
            name = f"leg_{side}{i}"
            sz = 1 if z > 0 else -1
            m.bone(name, (x, 1.3, z), "body")
            m.box(name, x - 0.18, 1.0, min(z, z + sz * 1.4), x + 0.18, 1.4, max(z, z + sz * 1.4), col)          # out sideways
            m.box(name, x - 0.15, 0.0, min(z + sz * 1.2, z + sz * 1.6), x + 0.15, 1.1, max(z + sz * 1.2, z + sz * 1.6), col + "_d")  # down to the ground
            legs[name] = {"rotation": wave(4, 0.35, 22 if (i + (side == "a")) % 2 else -22, 0, axis=0)}
    m.anim("walk", 0.35, legs)
    m.anim("idle", 2.0, {"claw_l": {"rotation": wave(4, 2.0, 10, 0, axis=1)}, "claw_r": {"rotation": wave(4, 2.0, -8, 0.5, axis=1)}})
    # wave the big claw at a player: raise it, wave three times, lower it
    up = (0, 0, -55)
    m.anim("wave", 1.6, {"claw_r": {"rotation": [(0, (0, 0, 0)), (0.25, up), (0.45, (0, 25, -60)), (0.65, (0, -25, -60)),
                                                 (0.85, (0, 25, -60)), (1.05, (0, -25, -60)), (1.25, (0, 25, -60)), (1.6, (0, 0, 0))]},
                         "body": {"rotation": [(0, (0, 0, 0)), (0.25, (0, 0, 6)), (1.25, (0, 0, 6)), (1.6, (0, 0, 0))]}}, loop="once")
    death(m, drop=0.5)


def butterfly(m, wing="monarch"):
    """Monarch butterfly (real wingspan 10 cm; drawn ~6 px)."""
    m.bone("body", (0, 4, 0))
    m.box("body", -0.18, 3.8, -1.1, 0.18, 4.15, 1.0, "black")
    m.box("body", -0.25, 3.85, -1.45, 0.25, 4.25, -1.1, "black")
    m.sym("body", 0.1, 4.1, -2.4, 0.16, 4.18, -1.4, "black")        # antennae
    m.sym("body", 0.08, 4.1, -2.6, 0.22, 4.25, -2.4, "black")
    for side, sx in (("wing_l", 1), ("wing_r", -1)):
        m.bone(side, (sx * 0.2, 4.1, 0), "body")
        def w(x1, z1, x2, z2, c, y=4.1):
            a, b = (sx * x1, sx * x2) if sx > 0 else (sx * x2, sx * x1)
            m.box(side, a, y, z1, b, y + 0.06, z2, c)
        w(0.2, -1.2, 2.8, 0.3, wing)          # forewing
        w(0.2, 0.3, 2.2, 1.6, wing + "_d")    # hindwing
        w(2.6, -1.2, 3.0, 0.3, "black", 4.105)
        w(0.2, -1.25, 2.9, -1.05, "black", 4.105)
        w(2.0, 0.3, 2.3, 1.6, "black", 4.105)
        w(2.65, -0.9, 2.85, -0.7, "white", 4.17)
        w(2.65, -0.3, 2.85, -0.1, "white", 4.17)
        w(1.2, -0.6, 1.3, 0.25, "black", 4.165)    # veins
    p = 0.3
    flap = {"wing_l": {"rotation": wave(4, p, 65, 0, axis=2)}, "wing_r": {"rotation": wave(4, p, -65, 0, axis=2)},
            "body": {"position": [(0, (0, 0, 0)), (p / 2, (0, 0.4, 0)), (p, (0, 0, 0))]}}
    m.anim("idle", p, flap)
    m.anim("walk", p, flap)
    death(m, drop=0)


def firefly(m, glow="glow_yellow"):
    """Firefly (real 1.5 cm; drawn ~2 px). The plugin adds glow particles at night."""
    m.bone("body", (0, 4, 0))
    m.box("body", -0.3, 3.8, -0.8, 0.3, 4.25, 0.5, "black")
    m.box("body", -0.32, 3.75, 0.5, 0.32, 4.15, 1.1, glow)
    m.box("body", -0.3, 4.0, -1.1, 0.3, 4.3, -0.8, "firefly_red")
    m.bone("wing_l", (0.3, 4.25, -0.2), "body")
    m.box("wing_l", 0.3, 4.25, -0.8, 1.4, 4.3, 0.6, "wing_clear")
    m.bone("wing_r", (-0.3, 4.25, -0.2), "body")
    m.box("wing_r", -1.4, 4.25, -0.8, -0.3, 4.3, 0.6, "wing_clear")
    f = {"wing_l": {"rotation": wave(4, 0.12, 50, 0, axis=2)}, "wing_r": {"rotation": wave(4, 0.12, -50, 0, axis=2)}}
    m.anim("idle", 0.12, f)
    m.anim("walk", 0.12, f)
    death(m, drop=0)


def dragonfly(m, col="dragon_teal"):
    """Emperor-like dragonfly (real 8 cm long; drawn ~7 px)."""
    m.bone("body", (0, 4, 0))
    m.box("body", -0.35, 3.75, -1.6, 0.35, 4.35, 0.0, col)            # thorax
    m.box("body", -0.18, 3.85, 0.0, 0.18, 4.15, 5.0, col + "_d")      # abdomen
    for z in (1.0, 2.0, 3.0, 4.0):
        m.box("body", -0.2, 3.84, z, 0.2, 4.16, z + 0.25, "black")
    m.box("body", -0.55, 3.75, -2.2, 0.55, 4.45, -1.6, "dragon_eye")
    f = {}
    for side, sx in (("wing_l", 1), ("wing_r", -1)):
        for k, z1, z2, span in ((side, -1.5, -0.8, 3.6), (side + "2", -0.7, 0.0, 3.2)):
            m.bone(k, (sx * 0.3, 4.35, (z1 + z2) / 2), "body")
            a, b = (0.3, 0.3 + span) if sx > 0 else (-0.3 - span, -0.3)
            m.box(k, a, 4.35, z1, b, 4.38, z2, "wing_clear")
            ta, tb = (0.3 + span - 0.4, 0.3 + span) if sx > 0 else (-0.3 - span, -0.3 - span + 0.4)
            m.box(k, ta, 4.385, z1, tb, 4.4, z2, "black")
            amp = 35 if k == side else -35
            f[k] = {"rotation": wave(4, 0.1, amp * sx, 0, axis=2)}
    m.anim("idle", 0.1, f)
    m.anim("walk", 0.1, f)
    death(m, drop=0)

# ------------------------------------------------------------------ japan set (0.4.0)

def tanuki(m):
    """Japanese raccoon dog: low-slung grey-brown body, cream cheeks, black mask over the eyes,
    small round ears and the famous ringed tail. Drawn ~60 cm long."""
    c, dk, cr = "tanuki", "tanuki_dark", "fur_cream"
    m.bone("body", (0, 5.6, 0))
    m.box("body", -2.6, 3.2, -5.0, 2.6, 8.2, -1.6, c)            # chest
    m.box("body", -2.9, 2.8, -1.6, 2.9, 8.0, 2.2, c)             # belly
    m.box("body", -2.6, 3.4, 2.2, 2.6, 8.4, 5.0, c)              # rump
    m.box("body", -1.2, 8.1, -4.4, 1.2, 8.6, 4.4, dk)            # darker back stripe
    m.box("body", -1.8, 2.7, -3.0, 1.8, 3.3, 3.0, cr)            # cream belly
    m.bone("tail", (0, 7.6, 5.0), "body")                        # held slightly up
    m.box("tail", -0.5, 7.0, 5.0, 0.5, 8.0, 5.9, c)
    m.box("tail", -0.55, 7.7, 5.6, 0.55, 8.7, 6.5, dk)           # ring 1 (dark)
    m.box("tail", -0.5, 8.3, 6.2, 0.5, 9.2, 7.0, cr)             # ring 2 (light)
    m.bone("h_head", (0, 7.4, -5.0), "body")
    m.box("h_head", -2.0, 5.4, -7.4, 2.0, 8.8, -5.0, c)          # round skull
    m.box("h_head", -1.3, 5.2, -8.9, 1.3, 6.9, -7.4, cr)         # cream muzzle
    m.box("h_head", -0.5, 6.3, -9.1, 0.5, 6.8, -8.8, "nose")     # nose tip
    m.box("h_head", -2.05, 7.0, -7.3, 2.05, 8.0, -6.2, "fur_black")   # mask band
    m.sym("h_head", 1.9, 7.3, -7.0, 2.0, 7.8, -6.6, "eye")       # eyes in the mask
    m.sym("h_head", 2.0, 5.6, -7.0, 2.15, 6.8, -5.6, cr)         # cream cheeks below
    m.sym("h_head", 1.0, 8.8, -6.4, 2.0, 9.9, -5.6, c)           # round ears
    m.sym("h_head", 1.25, 9.0, -6.25, 1.75, 9.65, -5.8, cr)      # ... with light inner
    for name, x, z in (("leg_fl", 2.0, -3.4), ("leg_fr", -2.0, -3.4), ("leg_bl", 2.2, 3.4), ("leg_br", -2.2, 3.4)):
        leg(m, name, x, z, 4.2, 2.2, 2.0, 1.5, 1.1, c, foot=dk, foot_h=0.4)
    gait(m, 0.9, 16, head=3, tail=8, style="walk", bob=0.14)
    quad_idle(m, 6)
    m.anim("eat", 2.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.4, (35, 0, 0)), (1.5, (35, 0, 0)), (2.0, (0, 0, 0))]}}, loop="once")
    death(m, drop=2)


def crane(m):
    """Red-crowned crane: tall white wader, black neck and tail plumes, bright red crown, dagger
    beak. Walks with slow high steps (stilt_walk). Drawn ~1.15 m tall."""
    w, k = "white", "black"
    m.bone("body", (0, 11.6, 0))
    m.box("body", -1.9, 10.2, -3.4, 1.9, 13.6, -0.4, w)          # chest
    m.box("body", -1.8, 10.0, -0.4, 1.8, 13.4, 2.8, w)           # belly
    m.box("body", -1.6, 13.2, -2.0, 1.6, 13.8, 3.0, k)           # folded wing backs (black)
    m.box("body", -0.9, 11.6, 2.8, 0.9, 13.2, 5.4, k)            # drooping tail plumes
    for sx in (1, -1):                                            # hand feathers sticking out
        x1, x2 = (1.85, 2.15) if sx > 0 else (-2.15, -1.85)
        m.box("body", x1, 11.0, -0.6, x2, 12.6, 3.2, k)
    m.bone("h_head", (0, 13.0, -3.0), "body")                    # long S-neck
    m.box("h_head", -0.45, 13.0, -4.3, 0.45, 16.6, -3.3, k)      # lower neck (black)
    m.box("h_head", -0.45, 15.8, -5.5, 0.45, 18.4, -4.2, k)
    m.box("h_head", -0.8, 17.6, -6.9, 0.8, 19.4, -5.4, w)        # skull (white)
    m.box("h_head", -0.35, 19.35, -6.6, 0.35, 19.75, -5.6, "eye_red")   # the red crown
    m.box("h_head", -0.25, 17.9, -10.6, 0.25, 18.5, -6.9, "fur_dark")   # dagger beak
    m.box("h_head", -0.5, 17.8, -8.2, 0.5, 18.6, -6.8, w)        # small white cheek tuft
    m.sym("h_head", 0.75, 18.4, -6.3, 0.82, 18.9, -6.0, "eye")
    for name, x in (("leg_l", 0.75), ("leg_r", -0.75)):          # bare black legs
        m.bone(name, (x, 10.4, -0.2), "body")
        m.box(name, x - 0.16, 1.0, -0.36, x + 0.16, 10.4, 0.16, "fur_dark")
        m.box(name, x - 0.16, 0.6, 0.16, x + 0.16, 1.2, 1.5, "fur_dark")   # back toe
        m.box(name, x - 0.16, 0.6, -1.2, x + 0.16, 1.0, 0.2, "fur_dark")   # front toes
    stilt_walk(m, 1.5, 16)
    m.anim("idle", 3.6, {"h_head": {"rotation": [(0, (0, 0, 0)), (1.0, (0, 25, 0)), (2.2, (0, 25, 0)), (3.0, (0, -18, 0)), (3.6, (0, 0, 0))]},
                         "body": {"position": [(0, (0, 0, 0)), (1.8, (0, 0.12, 0)), (3.6, (0, 0, 0))]}})
    m.anim("eat", 1.8, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (30, 0, 0)), (1.2, (30, 0, 0)), (1.8, (0, 0, 0))]}}, loop="once")
    death(m, drop=1.5)


def koi(m):
    """Koi: a fat ornamental carp - white body with orange and black patches, flowing tail fin.
    Built swimming (head at -Z like every animal here); the tail swings to propel it."""
    wh, or_, bl = "white", "koi_orange", "koi_black"
    m.bone("body", (0, 3.8, 0))
    m.box("body", -1.05, 2.8, -1.8, 1.05, 4.9, 1.0, wh)          # middle, orange saddle
    m.box("body", -1.08, 3.4, -0.6, 1.08, 4.92, 0.7, or_)        # patch on the back
    m.box("body", -0.95, 2.9, 1.0, 0.95, 4.7, 2.6, wh)           # tail stalk
    m.box("body", -1.08, 2.95, 1.4, 1.08, 4.65, 2.2, bl)         # black spot
    m.box("body", -0.06, 4.8, -1.0, 0.06, 5.8, 1.2, "koi_fin")   # dorsal fin
    m.sym("body", 1.0, 2.9, -0.4, 2.0, 3.5, 0.8, "koi_fin")      # pectoral fins
    m.bone("h_head", (0, 3.8, -1.8), "body")
    m.box("h_head", -0.95, 2.9, -3.3, 0.95, 4.7, -1.8, wh)       # head
    m.box("h_head", -0.98, 3.4, -3.0, 0.98, 4.72, -2.2, or_)     # orange face patch
    m.box("h_head", -0.4, 3.4, -3.6, 0.4, 4.4, -3.3, wh)         # mouth
    m.sym("h_head", 0.3, 4.0, -3.8, 0.36, 4.1, -3.4, wh)         # barbels
    m.sym("h_head", 0.9, 4.0, -2.9, 0.98, 4.5, -2.6, "eye")
    m.bone("tail", (0, 3.8, 2.6), "body")
    m.box("tail", -0.05, 3.6, 2.6, 0.05, 6.3, 4.6, "koi_fin")    # upper lobe
    m.box("tail", -0.05, 1.5, 2.6, 0.05, 4.0, 4.6, "koi_fin")    # lower lobe
    m.box("tail", -0.06, 3.3, 2.8, 0.06, 4.5, 4.9, or_)          # coloured middle
    p = 0.5
    m.anim("walk", p, {"tail": {"rotation": wave(6, p, 16, 0, axis=1)},
                       "body": {"rotation": wave(6, p, 5, math.pi, axis=1),
                                "position": [(0, (0, 0, 0)), (p / 2, (0, 0.12, 0)), (p, (0, 0, 0))]},
                       "h_head": {"rotation": wave(6, p, 2.5, 0, axis=1)}})
    m.anim("idle", 2.6, {"tail": {"rotation": wave(5, 2.6, 4, 0, axis=1)},
                         "h_head": {"rotation": [(0, (0, 0, 0)), (1.2, (0, 6, 0)), (2.6, (0, 0, 0))]}})
    death(m, drop=0)


def macaque(m):
    """Japanese macaque (snow monkey): stocky sandy-grey body, pink face with a brow ridge, small
    ears and a short up-curved tail. Drawn ~55 cm."""
    c, pk = "macaque", "pink"
    m.bone("body", (0, 6.4, 0))
    m.box("body", -2.7, 4.0, -4.6, 2.7, 9.6, -1.2, c)            # broad chest/shoulders
    m.box("body", -2.4, 3.6, -1.2, 2.4, 8.8, 2.4, c)
    m.box("body", -2.5, 4.2, 2.4, 2.5, 8.6, 4.6, c)              # rump
    m.box("body", -1.6, 3.4, -3.0, 1.6, 4.0, 3.2, "fur_cream")  # pale belly
    m.bone("tail", (0, 8.0, 4.6), "body")                        # short, curled up
    m.box("tail", -0.4, 7.8, 4.6, 0.4, 8.6, 5.5, c)
    m.box("tail", -0.35, 8.5, 5.2, 0.35, 9.5, 5.8, c)
    m.bone("h_head", (0, 8.0, -4.4), "body")
    m.box("h_head", -2.1, 6.0, -6.6, 2.1, 9.6, -4.3, c)          # skull
    m.box("h_head", -1.5, 6.2, -7.2, 1.5, 8.2, -6.6, pk)         # pink face
    m.box("h_head", -2.15, 8.4, -6.7, 2.15, 9.0, -6.2, "fur_dark")  # brow ridge
    m.box("h_head", -1.1, 6.3, -7.5, 1.1, 7.2, -7.1, pk)         # muzzle
    m.sym("h_head", 1.1, 7.4, -7.3, 1.35, 7.9, -7.15, "eye")
    m.sym("h_head", 2.1, 6.6, -5.4, 2.6, 8.0, -4.7, c)           # small side ears
    m.sym("h_head", 2.25, 6.9, -5.3, 2.5, 7.7, -4.85, pk)
    for name, x, z in (("leg_fl", 2.1, -3.0), ("leg_fr", -2.1, -3.0), ("leg_bl", 2.3, 3.0), ("leg_br", -2.3, 3.0)):
        leg(m, name, x, z, 4.8, 2.4, 2.4, 1.7, 1.3, c, foot=pk, foot_h=0.35)
    gait(m, 0.95, 15, head=4, tail=6, style="walk", bob=0.16)
    quad_idle(m, 4)
    m.anim("eat", 2.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.4, (30, 0, 0)), (1.4, (30, 0, 0)), (2.0, (0, 0, 0))]}}, loop="once")
    death(m, drop=2)


# ------------------------------------------------------------------ batch 2 (0.5.0)

def swim(m, period=0.5, amp=16, head=2.5, roll=5, fluke=False, die=2):
    """Shared fish/whale motion: the whole body swims as one S-wave through tail + body bones.
    fluke=True rocks about the X axis instead of the Y: a whale's tail is horizontal and beats
    up-down, a fish's is vertical and beats side to side."""
    ax = 0 if fluke else 1
    p = period
    m.anim("walk", p, {"tail": {"rotation": wave(6, p, amp, 0, axis=ax)},
                       "body": {"rotation": wave(6, p, roll, math.pi, axis=ax),
                                "position": [(0, (0, 0, 0)), (p / 2, (0, 0.12, 0)), (p, (0, 0, 0))]},
                       "h_head": {"rotation": wave(6, p, head, 0, axis=ax)}})
    m.anim("idle", p * 5, {"tail": {"rotation": wave(5, p * 5, max(3, amp // 4), 0, axis=ax)},
                           "h_head": {"rotation": [(0, (0, 0, 0)), (p * 2.5, (0, 6, 0)), (p * 5, (0, 0, 0))]}})
    death(m, drop=die)


def kangaroo(m):
    """Red kangaroo, ~1.3 m upright: rests on its tail, huge two legs, tiny arms, long ears.
    The walk is a two-footed hop (both legs in phase, body flies up, tail counter-weights)."""
    c, dk, cr = "fur_brown", "fur_dark", "fur_cream"
    m.bone("body", (0, 8.5, -1.0))                              # tilted-back posture
    m.box("body", -2.1, 5.5, -3.5, 2.1, 12.5, 1.5, c)
    m.box("body", -1.3, 5.2, -3.6, 1.3, 6.2, 1.6, c + "_d")     # belly shadow
    m.bone("tail", (0, 6.0, 1.2), "body")                       # thick, rests on the ground
    m.box("tail", -0.7, 4.2, 1.0, 0.7, 6.4, 5.2, c)
    m.box("tail", -0.5, 2.6, 4.6, 0.5, 4.6, 8.2, c + "_d")
    m.box("tail", -0.4, 2.2, 7.8, 0.4, 3.0, 9.0, dk)            # dark tip
    m.sym("body", 0.5, 8.5, -3.9, 0.9, 9.4, -2.6, c + "_d")     # tiny arms
    m.bone("h_head", (0, 12.0, -2.8), "body")
    m.box("h_head", -1.0, 11.2, -5.4, 1.0, 13.2, -2.6, c)       # skull
    m.box("h_head", -0.6, 11.1, -6.6, 0.6, 12.2, -5.4, c + "_d")  # muzzle
    m.box("h_head", -0.25, 11.6, -6.9, 0.25, 12.1, -6.5, dk)    # nose
    m.sym("h_head", 0.55, 12.2, -4.6, 0.65, 12.8, -4.1, "eye")
    m.sym("h_head", 0.4, 13.2, -3.8, 0.8, 16.4, -3.2, c)        # long ears
    m.sym("h_head", 0.5, 13.4, -3.9, 0.7, 15.8, -3.35, "pink_d")
    for name, x in (("leg_l", 1.7), ("leg_r", -1.7)):           # the big springs
        m.bone(name, (x, 6.2, 0.6), "body")
        m.box(name, x - 0.7, 1.8, -0.6, x + 0.7, 6.4, 1.9, c)   # thigh
        m.box(name, x - 0.45, 0.9, 0.2, x + 0.45, 2.6, 1.6, c + "_d")  # shin
        m.box(name, x - 0.8, 0.0, -1.8, x + 0.8, 0.9, 2.2, c + "_d")   # long foot
    p = 0.9
    m.anim("walk", p, {"leg_l": {"rotation": wave(6, p, 30)}, "leg_r": {"rotation": wave(6, p, 30)},
                       "body": {"position": [(0, (0, 0, 0)), (p * 0.35, (0, 1.4, 0)), (p * 0.62, (0, -0.2, 0)), (p, (0, 0, 0))],
                                "rotation": wave(6, p, 9, math.pi)},
                       "tail": {"rotation": wave(6, p, 10, math.pi)},
                       "h_head": {"rotation": wave(6, p, 5, 0.6)}})
    m.anim("idle", 4.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (1.0, (0, 15, 0)), (2.4, (0, -12, 0)), (3.6, (0, 0, 0))]},
                         "tail": {"rotation": wave(6, 4.0, 4, 0, axis=1)},
                         "body": {"position": [(0, (0, 0, 0)), (2.0, (0, 0.1, 0)), (4.0, (0, 0, 0))]}})
    m.anim("eat", 2.2, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (40, 0, 0)), (1.7, (40, 0, 0)), (2.2, (0, 0, 0))]}}, loop="once")
    death(m, drop=3)


def ostrich(m):
    """Common ostrich, ~2.2 m: the tallest bird alive. Bare pink-grey neck, black body plumage
    (white wing and tail tufts), two-toed legs built for sprinting."""
    bl, wh, sk = "black", "white", "fur_cream"
    m.bone("body", (0, 14.5, 0))
    m.box("body", -3.2, 12.0, -3.8, 3.2, 17.5, 3.2, bl)
    m.box("body", -2.6, 11.4, -3.2, 2.6, 12.6, 2.6, bl + "_d")
    m.sym("body", 3.2, 12.6, -1.6, 4.3, 15.0, 3.0, wh)          # loose white wing feathers
    m.box("body", -1.3, 14.0, 3.0, 1.3, 16.4, 5.2, wh)          # tail tuft
    m.bone("h_head", (0, 17.0, -3.0), "body")
    m.box("h_head", -0.6, 17.0, -4.1, 0.6, 21.4, -2.9, sk)      # long bare neck
    m.box("h_head", -0.9, 21.2, -5.6, 0.9, 22.9, -3.8, sk)      # small skull
    m.box("h_head", -0.65, 21.4, -7.6, 0.65, 22.3, -5.6, wh)    # flat blunt bill
    m.sym("h_head", 0.85, 21.9, -5.0, 0.95, 22.5, -4.5, "eye")
    for name, x in (("leg_l", 1.15), ("leg_r", -1.15)):
        m.bone(name, (x, 12.4, 0.2), "body")
        m.box(name, x - 0.28, 1.6, -0.1, x + 0.28, 12.5, 0.5, sk + "_d")   # bare shin
        m.box(name, x - 0.85, 0.0, -2.4, x + 0.85, 0.8, 0.9, sk)           # two-toed foot
    stilt_walk(m, 0.85, 28)
    m.anim("idle", 3.4, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.7, (0, 20, 0)), (1.7, (0, 20, 0)), (2.5, (0, -15, 0)), (3.4, (0, 0, 0))]},
                         "body": {"position": [(0, (0, 0, 0)), (1.7, (0, 0.12, 0)), (3.4, (0, 0, 0))]}})
    m.anim("eat", 1.6, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.3, (50, 0, 0)), (1.2, (50, 0, 0)), (1.6, (0, 0, 0))]}}, loop="once")
    death(m, drop=3)


def crocodile(m):
    """Siamese crocodile, ~2 m: low-slung armour, long jaws with tooth rows, the tail is the
    engine. Walks a slow high-undulation crawl, floats in water with eyes above the surface."""
    g, sp, be = "lizard", "lizard_spot", "scales_l"
    m.bone("body", (0, 1.7, 0))
    m.box("body", -2.1, 0.7, -3.4, 2.1, 2.7, 0.2, g)
    m.box("body", -1.8, 0.6, 0.2, 1.8, 2.4, 3.6, g)
    for z in (-2.6, -1.0, 0.6, 2.2):                             # scute ridges
        m.box("body", -0.55, 2.6, z, 0.55, 3.0, z + 1.1, sp)
    m.box("body", -1.7, 0.55, -3.0, 1.7, 0.9, 3.2, be)          # pale belly
    m.bone("h_head", (0, 1.7, -3.4), "body")
    m.box("h_head", -1.15, 0.8, -7.6, 1.15, 2.4, -3.2, g)       # long snout
    m.box("h_head", -1.2, 0.75, -3.9, 1.2, 2.6, -3.2, g)        # skull, eyes on top
    m.box("h_head", -1.0, 0.6, -7.4, 1.0, 0.85, -4.0, "white")  # teeth line
    for zx in (-6.9, -5.9, -4.9):
        m.box("h_head", -1.05, 0.85, zx, -0.8, 1.1, zx + 0.3, "white")
        m.box("h_head", 0.8, 0.85, zx, 1.05, 1.1, zx + 0.3, "white")
    m.sym("h_head", 0.95, 2.3, -4.4, 1.3, 2.9, -3.9, "eye")
    m.box("h_head", -0.25, 2.35, -7.7, 0.25, 2.6, -7.4, sp)     # nostrils
    m.bone("tail", (0, 1.6, 3.4), "body")
    m.box("tail", -0.9, 0.8, 3.4, 0.9, 2.3, 6.2, g)
    m.box("tail", -0.55, 0.75, 6.0, 0.55, 1.9, 8.6, g + "_d")
    m.box("tail", -0.25, 0.8, 8.4, 0.25, 1.6, 10.0, sp)
    m.box("tail", -0.45, 2.2, 3.6, 0.45, 2.7, 5.8, sp)          # tail ridge
    for name, x, z in (("leg_fl", 1.9, -2.2), ("leg_fr", -1.9, -2.2), ("leg_bl", 1.9, 1.8), ("leg_br", -1.9, 1.8)):
        sx = 1 if x > 0 else -1
        m.bone(name, (x, 1.1, z), "body")
        m.box(name, min(x, x + sx * 1.5), 0.4, z - 0.45, max(x, x + sx * 1.5), 1.0, z + 0.45, g + "_d")  # sprawled arm
        m.box(name, min(x + sx * 1.1, x + sx * 2.1), 0.0, z - 0.7, max(x + sx * 1.1, x + sx * 2.1), 0.5, z + 0.7, g + "_d")  # splayed foot
    gait(m, 1.2, 14, head=2, tail=12, style="walk", bob=0.06,
         extra={"body": {"rotation": wave(8, 1.2, 4, 0, axis=1), "position": gait_bob(0.06, 1.2)}})
    m.anim("idle", 4.5, {"h_head": {"rotation": [(0, (0, 0, 0)), (1.5, (4, 0, 0)), (3.0, (0, 0, 0))]},
                         "body": {"position": [(0, (0, 0, 0)), (2.2, (0, 0.08, 0)), (4.5, (0, 0, 0))]}})
    death(m, drop=2)


def gait_bob(bob, period):
    return [(0, (0, 0, 0)), (period / 4, (0, bob, 0)), (period / 2, (0, 0, 0)), (3 * period / 4, (0, bob, 0)), (period, (0, 0, 0))]


def koala(m):
    """Koala: a round grey ball with a coat of chinchilla fur, giant fluffy ears, a black nose
    and no visible tail. Lazy: it sits, it sleeps, it eats leaves. Drawn ~65 cm."""
    c, dk, ft = "elephant", "fur_dark", "fur_cream"
    m.bone("body", (0, 4.0, 0))
    m.box("body", -2.3, 1.6, -3.0, 2.3, 6.6, 2.2, c)
    m.box("body", -1.6, 6.4, -2.4, 1.6, 7.2, 1.6, c + "_l")     # lighter back
    m.box("body", -1.8, 1.4, -2.6, 1.8, 2.2, 1.8, ft)           # cream belly
    m.bone("h_head", (0, 6.2, -2.6), "body")
    m.box("h_head", -1.5, 5.0, -4.4, 1.5, 7.6, -2.4, c)         # round head
    m.box("h_head", -0.4, 5.4, -4.9, 0.4, 6.4, -4.4, dk)        # big black nose
    m.sym("h_head", 0.8, 6.1, -4.1, 1.0, 6.6, -3.9, "eye")
    m.sym("h_head", 1.4, 7.2, -3.4, 2.5, 9.4, -2.6, c)          # giant fluffy ears
    m.sym("h_head", 1.6, 7.5, -3.3, 2.3, 9.0, -2.8, ft)
    for name, x, z in (("leg_fl", 1.7, -1.6), ("leg_fr", -1.7, -1.6), ("leg_bl", 1.7, 0.8), ("leg_br", -1.7, 0.8)):
        leg(m, name, x, z, 2.6, 1.4, 1.2, 1.3, 1.1, c, foot=ft, foot_h=0.3)
    gait(m, 1.4, 12, head=3, tail=0, style="walk", bob=0.1)
    quad_idle(m, 0)
    m.anim("eat", 2.4, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (28, 0, 0)), (1.9, (28, 0, 0)), (2.4, (0, 0, 0))]}}, loop="once")
    m.anim("sleep", 2.0, {"body": {"position": hold(2.0, (0, -0.6, 0))},
                          "h_head": {"rotation": hold(2.0, (25, 10, 0))}}, loop="hold")
    death(m, drop=1)


def mole(m):
    """European mole, ~15 cm: velvety grey cylinder, pink nose, and spade paws that are
    built for digging, not walking. Rare above ground - it comes up right next to its molehill."""
    c, dk = "elephant", "fur_black"
    m.bone("body", (0, 1.7, 0))
    m.box("body", -1.1, 0.6, -2.2, 1.1, 2.7, 1.8, c)
    m.box("body", -0.8, 0.55, -2.0, 0.8, 1.0, 1.6, c + "_l")    # pale underside
    m.bone("h_head", (0, 1.6, -2.2), "body")                    # pointed wedge of a face
    m.box("h_head", -0.8, 0.7, -3.8, 0.8, 2.4, -2.0, c)
    m.box("h_head", -0.35, 1.0, -4.3, 0.35, 1.8, -3.7, "pink")  # pink nose
    m.box("h_head", -0.15, 1.9, -4.1, 0.15, 2.1, -3.9, dk)      # tiny eye under the fur
    m.box("h_head", -0.6, 2.2, -2.6, 0.6, 2.7, -2.1, c + "_d")  # fur over the ears
    for name, x in (("leg_fl", 0.9, ), ("leg_fr", -0.9, )):     # the spades: out, not down
        z = -1.2
        m.bone(name, (x, 1.1, z), "body")
        m.box(name, min(x, x + (1.3 if x > 0 else -1.3)), 0.7, z - 0.35, max(x, x + (1.3 if x > 0 else -1.3)), 1.1, z + 0.35, c + "_d")
        m.box(name, min(x + (0.9 if x > 0 else -0.9), x + (1.9 if x > 0 else -1.9)), 0.0, z - 0.7,
              max(x + (0.9 if x > 0 else -0.9), x + (1.9 if x > 0 else -1.9)), 0.7, z + 0.7, c + "_d")
    for name, x in (("leg_bl", 0.85), ("leg_br", -0.85)):
        z = 1.2
        m.bone(name, (x, 1.0, z), "body")
        m.box(name, x - 0.4, 0.0, z - 0.4, x + 0.4, 1.1, z + 0.4, c + "_d")
    gait(m, 0.35, 20, head=2, tail=0, style="trot", bob=0.06)
    m.anim("idle", 3.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (6, 0, 0)), (1.1, (0, 8, 0)), (1.7, (6, 0, 0)), (3.0, (0, 0, 0))]}})
    death(m, drop=0)


def rhino(m):
    """White rhinoceros, ~1.8 m at the shoulder: an armoured grey tank with the horn out front.
    Heavy single-file steps like the elephant, but shorter legs and a big head swing."""
    g, dk, hn = "elephant", "fur_dark", "tusk"
    m.bone("body", (0, 15.0, 0))
    m.box("body", -5.5, 10.5, -7.5, 5.5, 19.5, 0.0, g)          # shoulder mass
    m.box("body", -5.2, 10.0, 0.0, 5.2, 18.5, 7.0, g)           # barrel
    m.box("body", -4.6, 9.8, -6.5, 4.6, 10.6, 6.0, g + "_l")    # belly
    m.box("body", -4.0, 19.3, -6.0, 4.0, 20.1, 5.0, dk)         # dark back line
    for z, hw in ((-5.5, 5.6), (0.5, 5.4), (5.5, 5.0)):         # skin folds
        m.box("body", -hw, 11.0, z, hw, 19.0, z + 0.7, g + "_d")
    m.bone("tail", (0, 18.0, 7.0), "body")
    m.box("tail", -0.35, 13.5, 7.0, 0.35, 18.2, 7.8, g + "_d")
    m.box("tail", -0.5, 12.0, 7.2, 0.5, 14.0, 7.8, dk)
    m.bone("h_head", (0, 15.5, -7.5), "body")
    m.box("h_head", -4.4, 9.5, -14.5, 4.4, 18.5, -6.5, g)       # big blocky head
    m.box("h_head", -2.2, 8.8, -16.8, 2.2, 14.5, -14.5, g)      # muzzle
    m.box("h_head", -1.1, 14.3, -17.6, 1.1, 21.5, -15.2, hn)    # the big horn
    m.box("h_head", -0.7, 14.0, -15.6, 0.7, 16.6, -14.6, hn + "_d")  # second, small
    m.sym("h_head", 3.0, 16.2, -11.2, 3.3, 17.2, -10.2, "eye")
    m.sym("h_head", 4.2, 17.6, -8.6, 5.3, 20.6, -7.4, g)        # tube ears
    for name, x, z in (("leg_fl", 3.6, -5.0), ("leg_fr", -3.6, -5.0), ("leg_bl", 3.6, 4.6), ("leg_br", -3.6, 4.6)):
        m.bone(name, (x, 11.0, z), "body")
        m.box(name, x - 2.3, 2.0, z - 2.3, x + 2.3, 11.2, z + 2.3, g)
        m.box(name, x - 2.5, 0.0, z - 2.5, x + 2.5, 2.4, z + 2.5, g + "_d")
        for dx in (-1.3, 0.0, 1.3):
            m.box(name, x + dx - 0.35, 0.0, z - 2.75, x + dx + 0.35, 1.0, z - 2.45, hn)   # toe nails
    ears = None
    gait(m, 1.65, 11, head=3, tail=8, style="walk", bob=0.2)
    m.anim("idle", 4.5, {"h_head": {"rotation": [(0, (0, 0, 0)), (1.2, (0, 8, 0)), (2.6, (0, -6, 0)), (4.5, (0, 0, 0))]},
                         "tail": {"rotation": wave(8, 4.5, 6, 0, axis=1)},
                         "body": {"position": [(0, (0, 0, 0)), (2.2, (0, 0.14, 0)), (4.5, (0, 0, 0))]}})
    death(m, drop=8)


def penguin(m):
    """Emperor penguin, ~1.1 m: tuxedo with an orange ear patch, stubby legs, flippers held out.
    Waddles with a real side-to-side roll - this is the one animal where the roll should show."""
    bl, wh, or_ = "black", "white", "web"
    m.bone("body", (0, 7.0, 0))
    m.box("body", -1.7, 1.4, -1.7, 1.7, 12.2, 1.5, bl)
    m.box("body", -1.4, 1.5, -1.85, 1.4, 10.6, -1.55, wh)       # white bib (front = -Z)
    m.box("body", -1.75, 1.2, -0.6, 1.75, 1.6, 1.2, bl + "_d")  # feet shadow
    m.bone("h_head", (0, 11.8, -0.2), "body")
    m.box("h_head", -1.1, 11.6, -1.5, 1.1, 13.6, 0.6, bl)
    m.box("h_head", -1.15, 12.0, -1.6, 1.15, 13.2, -1.4, wh)    # white face stripe
    m.sym("h_head", 1.05, 12.0, -0.4, 1.25, 13.0, 0.3, or_)     # orange ear patch
    m.box("h_head", -0.3, 12.3, -2.6, 0.3, 12.9, -1.5, or_)     # beak
    m.sym("h_head", 0.45, 12.7, -1.55, 0.7, 13.1, -1.3, "eye")
    for name, sx in (("arm_l", 1), ("arm_r", -1)):              # flippers held slightly out
        m.bone(name, (sx * 1.7, 10.4, -0.2), "body")
        x1, x2 = (1.6, 2.5) if sx > 0 else (-2.5, -1.6)
        m.box(name, x1, 5.4, -0.8, x2, 10.6, 0.4, bl)
    for name, x in (("leg_l", 0.8), ("leg_r", -0.8)):
        m.bone(name, (x, 1.6, 0), "body")
        m.box(name, x - 0.35, 0.0, -0.9, x + 0.35, 1.6, 0.5, or_)
        m.box(name, x - 0.35, 0.0, -1.3, x + 0.35, 0.5, 0.1, or_)
    p = 0.85
    m.anim("walk", p, {"leg_l": {"rotation": wave(6, p, 16)}, "leg_r": {"rotation": wave(6, p, -16)},
                       "body": {"rotation": wave(6, p, 5.5, 0, axis=2)},
                       "arm_l": {"rotation": wave(6, p, 6, 0.4, axis=2)}, "arm_r": {"rotation": wave(6, p, -6, 0.4, axis=2)},
                       "h_head": {"rotation": wave(6, p, 2.5, math.pi)}})
    m.anim("idle", 3.6, {"h_head": {"rotation": [(0, (0, 0, 0)), (1.0, (0, 18, 0)), (2.2, (0, 18, 0)), (3.2, (0, 0, 0))]},
                         "body": {"position": [(0, (0, 0, 0)), (1.8, (0, 0.1, 0)), (3.6, (0, 0, 0))]}})
    death(m, drop=1)


def orca(m):
    """Orca, ~6.5 m: the black-and-white torpedo with the saddle patch and a 1.8 m dorsal fin.
    Drawn true size (SIZE 1.0) - FMM displays this one at scale. Tail flukes are HORIZONTAL: the
    swim wave beats up-down (X), which is exactly what separates a whale from a fish."""
    bl, wh = "black", "white"
    m.bone("body", (0, 22, 0))
    m.box("body", -5.5, 12, -38, 5.5, 32, -10, bl)              # front torso
    m.box("body", -5.0, 11, -10, 5.0, 31, 16, bl)               # mid
    m.box("body", -3.6, 13, 16, 3.6, 29, 38, bl)                # rear torso to the peduncle
    m.box("body", -5.1, 10.4, -32, 5.1, 12.4, 12, wh)           # white belly
    m.box("body", -5.7, 24.5, -8, 5.7, 26.5, 6, bl + "_d")      # saddle patch (light grey)
    m.box("body", -0.6, 32, -6, 0.6, 44, 6, bl)                 # the tall dorsal fin
    m.sym("body", 5.4, 12, -24, 12.5, 16, -10, bl + "_d")       # pectoral flippers
    m.bone("h_head", (0, 21, -38), "body")
    m.box("h_head", -4.6, 13, -52, 4.6, 29, -38, bl)            # blunt head
    m.box("h_head", -0.5, 12.8, -53.4, 0.5, 20.5, -52, bl)      # jaw tip
    m.sym("h_head", 3.2, 19, -45, 4.9, 23, -43.5, wh)           # eye patch
    m.sym("h_head", 2.0, 17.4, -44.2, 2.6, 18.6, -43.6, "eye")
    m.box("h_head", -4.2, 12.2, -51, 4.2, 13.4, -38, wh)        # lower jaw line
    m.bone("tail", (0, 20, 38), "body")
    m.box("tail", -2.2, 16, 38, 2.2, 25, 48, bl)                # peduncle
    m.sym("tail", 2.0, 17.5, 46, 16, 20.5, 57, bl)              # horizontal flukes
    swim(m, period=1.1, amp=12, head=3, roll=5, fluke=True, die=6)


def piranha(m):
    """Red-bellied piranha, ~20 cm (drawn about double): a deep silver disc with an orange-red
    belly, an underslung jaw and a mouth full of teeth. It hunts in a shoal of dozens."""
    sc, be, wh = "scales", "firefly_red", "white"
    m.bone("body", (0, 4.6, 0))
    m.box("body", -0.55, 2.2, -2.4, 0.55, 7.2, 0.6, sc)         # deep disc body
    m.box("body", -0.6, 1.9, -2.0, 0.6, 3.2, 1.6, sc + "_d")    # behind the disc
    m.box("body", -0.62, 1.9, -1.6, 0.62, 3.4, 0.6, be)         # the red belly
    m.box("body", -0.05, 7.1, -1.4, 0.05, 8.4, 0.2, sc + "_d")  # dorsal fin
    m.box("body", -0.05, 1.4, 0.4, 0.05, 2.4, 1.8, be)          # anal fin
    m.bone("h_head", (0, 4.4, -2.4), "body")
    m.box("h_head", -0.5, 3.0, -3.9, 0.5, 5.4, -2.2, sc)
    m.box("h_head", -0.35, 2.2, -4.1, 0.35, 3.4, -3.0, sc + "_d")  # underslung jaw
    m.sym("h_head", 0.42, 4.2, -3.5, 0.52, 4.8, -3.2, "eye")
    for zx in (-3.9, -3.5, -3.1):
        m.box("h_head", -0.04, 3.2, zx, 0.04, 3.6, zx + 0.25, wh)  # teeth
    m.bone("tail", (0, 4.6, 1.6), "body")
    m.box("tail", -0.05, 2.6, 1.6, 0.05, 7.0, 3.4, sc + "_d")   # forked tail
    swim(m, period=0.25, amp=20, head=2, roll=6, die=0)


def macaw(m, col="cardinal", col2="jay", belly="canary"):
    """Scarlet macaw, ~85 cm beak-to-tail-tip: big curved black-and-white bill, white face mask,
    and a tail longer than its body. col = body, col2 = wings, belly = under-band."""
    m.bone("body", (0, 3.4, 0))
    m.box("body", -1.15, 2.2, -1.8, 1.15, 4.8, 1.0, col)
    m.box("body", -1.0, 2.1, -1.9, 1.0, 3.2, -0.2, belly)       # pale under-band
    m.bone("h_head", (0, 4.6, -0.9), "body")
    m.box("h_head", -0.85, 4.0, -2.2, 0.85, 5.6, -0.8, col)
    m.box("h_head", -0.7, 4.0, -2.5, 0.7, 5.0, -2.2, "white")   # face mask
    m.box("h_head", -0.35, 4.1, -3.9, 0.35, 5.3, -2.4, "fur_black")  # big upper mandible
    m.box("h_head", -0.25, 3.6, -3.1, 0.25, 4.2, -2.4, "fur_black")  # hooked tip
    m.box("h_head", -0.2, 4.9, -2.8, 0.2, 5.2, -2.4, "white")   # pale crown line
    m.sym("h_head", 0.45, 4.7, -2.4, 0.55, 5.0, -2.25, "eye")
    for name, sx in (("wing_l", 1), ("wing_r", -1)):
        m.bone(name, (sx * 1.1, 4.2, -0.3), "body")
        x1, x2 = (1.05, 2.1) if sx > 0 else (-2.1, -1.05)
        m.box(name, x1, 2.0, -1.2, x2, 4.3, 2.4, col2)
        m.box(name, x1, 2.0, 2.2, x2, 3.2, 4.0, col)            # wing shoulder same as body
    m.bone("tail", (0, 3.2, 0.9), "body")                       # the impossibly long tail
    m.box("tail", -0.35, 2.5, 0.9, 0.35, 3.6, 6.0, col)
    m.box("tail", -0.3, 2.4, 5.8, 0.3, 3.3, 10.4, col2)
    m.box("tail", -0.2, 2.5, 10.2, 0.2, 3.1, 11.6, "black")
    for name, x in (("leg_l", 0.5), ("leg_r", -0.5)):
        m.bone(name, (x, 1.4, -0.3), "body")
        m.box(name, x - 0.16, 0.0, -0.35, x + 0.16, 1.4, 0.25, "beak_d")
        m.box(name, x - 0.45, 0.0, -0.8, x + 0.45, 0.25, 0.2, "beak_d")
    hop_walk(m)
    m.anim("idle", 2.4, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.4, (0, 30, 0)), (1.1, (0, 30, 0)), (1.5, (0, -25, 0)), (2.0, (0, 0, 0))]},
                         "wing_l": {"rotation": hold(2.4, (0, 0, -10))}, "wing_r": {"rotation": hold(2.4, (0, 0, 10))},
                         "tail": {"rotation": [(0, (0, 0, 0)), (1.2, (5, 0, 0)), (2.4, (0, 0, 0))]}})
    death(m, drop=0.5)


# ------------------------------------------------------------------ species list

SPECIES = [  # (id, builder, kwargs, rare recolour, rare kwargs)
    ("deer", deer, {}, {"deer": "fur_white", "deer_white": "fur_white", "eye": "eye_red"}, {}),
    ("bear", bear, {}, {"fur_brown": "fur_red"}, {}),
    ("boar", boar, {}, {"boar": "fur_gold", "fur_black": "fur_brown"}, {}),
    ("duck", duck, {}, {"chestnut": "white", "duck_gray": "white", "duck_brown": "white", "duck_green": "white", "black": "white"}, {}),
    ("butterfly", butterfly, {}, {"monarch": "morpho"}, {}),
    ("firefly", firefly, {}, {"glow_yellow": "glow_blue"}, {}),
    ("snail", snail, {}, {"shell_brown": "shell_gold", "shell_tan": "glow_yellow"}, {}),
    ("hedgehog", hedgehog, {}, {"spine": "fur_white", "hedgehog_face": "fur_white", "eye": "eye_red"}, {}),
    ("capybara", capybara, {}, {}, {"yuzu": True}),
    ("tortoise", tortoise, {}, {"scute": "moss"}, {}),
    ("lizard", lizard, {}, {"lizard": "lizard_blue", "lizard_spot": "lizard_blue_spot"}, {}),
    ("crab", crab, {}, {"crab": "crab_blue"}, {}),
    ("songbird", songbird, {}, {"jay": "gold", "white": "gold_l"}, {}),
    ("dragonfly", dragonfly, {}, {"dragon_teal": "dragon_red"}, {}),
    ("elephant", elephant, {}, {"elephant": "elephant_white", "elephant_ear": "pink"}, {}),
    ("zebra", zebra, {}, {"zebra_white": "fur_cream", "zebra_black": "fur_brown"}, {}),
    ("tanuki", tanuki, {}, {"tanuki": "fur_black", "tanuki_dark": "black", "fur_cream": "fur_dark"}, {}),
    ("crane", crane, {}, {"white": "gold"}, {}),
    ("koi", koi, {}, {"koi_orange": "gold", "koi_fin": "gold_l"}, {}),
    ("macaque", macaque, {}, {"macaque": "fur_white"}, {}),
    ("kangaroo", kangaroo, {}, {"fur_brown": "fur_cream", "fur_dark": "fur_light"}, {}),
    ("ostrich", ostrich, {}, {"black": "fur_brown", "white": "fur_cream"}, {}),
    ("crocodile", crocodile, {}, {"lizard": "fur_dark", "lizard_spot": "black", "scales_l": "fur_light"}, {}),
    ("koala", koala, {}, {"elephant": "fur_white", "fur_cream": "white", "fur_dark": "pink"}, {}),
    ("mole", mole, {}, {"elephant": "shell_gold"}, {}),
    ("rhino", rhino, {}, {"elephant": "fur_black"}, {}),
    ("penguin", penguin, {}, {"white": "gold_l"}, {}),
    ("orca", orca, {}, {"black": "fur_white", "white": "elephant"}, {}),
    ("piranha", piranha, {}, {"scales": "gold", "firefly_red": "koi_orange"}, {}),
    ("macaw", macaw, {}, {"cardinal": "canary", "jay": "white", "canary": "gold_l"}, {}),
]
EXTRA = [  # more common looks of a species (chosen at random by the plugin)
    ("songbird_cardinal", songbird, {"col": "cardinal", "breast": "cardinal_l", "cap": "cardinal"}),
    ("songbird_canary", songbird, {"col": "canary", "breast": "canary_l"}),
    ("songbird_sparrow", songbird, {"col": "sparrow", "breast": "fur_cream", "cap": "fur_brown"}),
    ("macaw_blue", macaw, {"col": "jay", "col2": "canary", "belly": "white"}),        # blue-and-gold
    ("macaw_green", macaw, {"col": "leaf", "col2": "canary", "belly": "fur_cream"}),  # military macaw
]

# ------------------------------------------------------------------ item display models (true scale, like Steamworks)

def item_model(name, cubes):
    elements = []
    for frm, to, col in cubes:
        elements.append({"name": col, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
                         "allow_mirror_modeling": True, "from": list(frm), "to": list(to), "autouv": 0, "color": 0,
                         "origin": [0, 0, 0], "rotation": [0, 0, 0],
                         "faces": {f: {"uv": cell(col), "texture": 0} for f in ("north", "east", "south", "west", "up", "down")},
                         "type": "cube", "uuid": U()})
    bb = Model(name).bbmodel()
    bb["elements"] = elements
    bb["outliner"] = [{"name": "body", "origin": [0, 0, 0], "color": 0, "uuid": U(), "export": True, "mirror_uv": False,
                       "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": [e["uuid"] for e in elements]}]
    P = lambda v: [round(c + 8, 3) for c in v]
    js = {"textures": {"0": "0", "particle": "0"},
          "elements": [{"from": P(e["from"]), "to": P(e["to"]),
                        "faces": {f: {"uv": [v * 16 / RES for v in d["uv"]], "texture": "#0"} for f, d in e["faces"].items()}}
                       for e in elements],
          "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.8] * 3}, "ground": {"translation": [0, 3, 0], "scale": [0.4] * 3},
                      "fixed": {"scale": [0.7] * 3}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45] * 3},
                      "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.5] * 3}}}
    return bb, js


ITEMS = {
    "wl_crab_claw": [((-4, -2, -2), (2, 2, 2), "crab"), ((2, -3, -3), (6, 3, 3), "crab_d"), ((-6, -1, -1), (-4, 1, 1), "crab")],
    "wl_lizard_tail": [((-1, -1, -6), (1, 1, 0), "lizard"), ((-0.6, -0.6, 0), (0.6, 0.6, 6), "lizard_spot")],
    "wl_field_journal": [((-5, -1, -6), (5, 1, 6), "leaf_d"), ((-4.5, 1, -5.5), (4.5, 1.3, 5.5), "fur_cream"),
                         ((-5.01, -1, -1), (5.01, 1, 1), "fur_brown")],
}


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    n = 0

    def save(m):
        nonlocal n
        with open(os.path.join(out, m.name + ".bbmodel"), "w", encoding="utf-8") as f:
            json.dump(m.bbmodel(), f)
        n += 1

    for sid, build, kw, rare, rare_kw in SPECIES:
        m = Model("wl_" + sid)
        build(m, **kw)
        save(m)
        r = Model("wl_" + sid + "_rare", recolor=rare)
        build(r, **{**kw, **rare_kw})
        save(r)
        for suffix, rc in (("_baby", None), ("_rare_baby", rare)):   # babies: half size, bigger head
            b = Model("wl_" + sid + suffix, recolor=rc)
            build(b, **({**kw, **rare_kw} if rc else kw))
            save(b)
    for name, build, kw in EXTRA:
        m = Model("wl_" + name)
        build(m, **kw)
        save(m)
        b = Model("wl_" + name + "_baby")
        build(b, **kw)
        save(b)
    for name, cubes in ITEMS.items():
        bb, js = item_model(name, cubes)
        with open(os.path.join(out, name + ".bbmodel"), "w", encoding="utf-8") as f:
            json.dump(bb, f)
        with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(js, f)
        n += 1
    print("wrote", n, "models to", out)


if __name__ == "__main__":
    main()
