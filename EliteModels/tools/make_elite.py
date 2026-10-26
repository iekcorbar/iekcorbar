"""Steampunk EliteMobs models for FreeMinecraftModels (own designs, own palette texture) - pilot set.

Same pipeline as Wildlife/tools/make_animals.py (it supplies the Model class, bones, animation tracks and the
.bbmodel writer; we only add colours). Humanoids built like the vanilla ones (32 px = 2 blocks tall, head 8 px,
body 8x12x4, arms/legs 4 px wide) so they replace a vanilla zombie/skeleton/pillager/husk without a hitbox
mismatch, but recognisably Minecraft: same proportions, the steampunk is in the details (goggles, brass,
gears, pipes, boilers). Front = -Z, bones: root > body > h_head, arm_l, arm_r (+ extras), leg_l, leg_r.
FMM plays "idle", "walk" and "death" by itself; EliteMobs plays "attack" through the same animation names.

Usage: python -I tools/make_elite.py <out folder>   (-> fmm-models/elitemobs, one .bbmodel per mob)
Server: copy the .bbmodel files into plugins/FreeMinecraftModels/models/elitemobs/ and set
`customModel: em_steam_zombie` (file name without .bbmodel) in the boss .yml - FMM hides the boss equipment.
"""
import base64, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "Wildlife", "tools"))
import make_animals as mk  # noqa: E402

# Own palette: the 16x16 texture grid holds 256 colours, and the Wildlife palette alone is already near that, so the
# elite models do not share it. Every base colour gets a darker (_d) and a lighter (_l) shade.
COLOURS = {
    "white": (248, 248, 246), "black": (24, 22, 24), "soot": (40, 38, 40), "bone": (226, 220, 200),
    "brass": (212, 170, 74), "copper": (190, 104, 66), "iron": (150, 154, 160), "steel": (96, 100, 108),
    "leather": (96, 62, 38), "leather_dark": (64, 40, 26), "rust": (150, 80, 44), "glass_blue": (120, 200, 230),
    "glow_orange": (255, 170, 60), "wire": (230, 130, 50), "scarf": (170, 60, 52), "cloth": (214, 200, 164),
    "pants": (60, 56, 96), "vest": (88, 56, 40), "coat_blue": (46, 62, 92), "coat_red": (122, 36, 34),
    "zombie_skin": (112, 150, 96), "husk_skin": (184, 156, 104), "pillager_skin": (134, 138, 130),
    "drown_skin": (96, 156, 146), "frost": (170, 210, 230), "moss": (88, 120, 70), "bog": (100, 120, 60),
    "wither_bone": (62, 62, 68), "piglin_skin": (190, 130, 110), "snout": (166, 104, 88), "gold": (226, 186, 70),
    "robe_green": (70, 94, 72), "robe_gray": (110, 110, 122), "robe_purple": (92, 52, 122), "arcane": (150, 90, 220),
    "hat_dark": (50, 40, 70), "skin_pale": (210, 170, 140), "red_eye": (200, 40, 40), "lava": (255, 110, 30),
    "lava_dark": (150, 40, 20), "magma": (64, 30, 28), "slime_green": (110, 200, 90), "slime_core": (50, 130, 50),
    "nether_brick": (80, 40, 48), "wolf_fur": (156, 156, 158), "fur_white": (238, 236, 230), "fur_brown": (112, 76, 48),
    "fur_dark": (70, 48, 32), "rabbit_fur": (166, 124, 84), "horse_coat": (112, 72, 46), "pig_pink": (214, 150, 146),
    "end_black": (24, 16, 32), "end_eye": (230, 120, 255), "end_purple": (110, 40, 150), "deepslate": (60, 64, 70),
    "sculk": (20, 60, 70), "sculk_glow": (80, 230, 220), "prismarine": (90, 150, 140), "sea_lantern": (210, 240, 230),
    "wind": (200, 230, 240), "shulker": (150, 100, 170), "ghast_white": (240, 240, 240), "dragon_dark": (30, 26, 38),
    "wither_dark": (32, 32, 38), "spider_fur": (54, 44, 40), "spider_eye": (200, 30, 30), "silver": (140, 140, 152),
    "nether_gold": (200, 150, 60), "tusk": (238, 232, 214), "wood": (126, 92, 56),
    # NPC set (appended, so the indices of the colours above stay the same)
    "skin_tan": (190, 140, 98), "skin_brown": (140, 96, 64), "hair_brown": (92, 58, 34), "hair_black": (30, 26, 30),
    "hair_blond": (226, 190, 100), "hair_red": (168, 70, 36), "hair_grey": (190, 190, 194), "santa_red": (190, 38, 40),
    "cloth_green": (74, 112, 66), "cloth_blue": (60, 94, 150), "cloth_purple": (96, 60, 140), "apron": (226, 218, 196),
    "cloth_teal": (52, 124, 124), "cloth_pink": (226, 140, 170),
}
mk.BASE.clear()
mk.PALETTE.clear()
mk.NAMES.clear()
for n, c in COLOURS.items():
    mk.BASE[n] = c
    for suf, k in (("", None), ("_d", 0.72), ("_l", 1.18)):
        mk.PALETTE[n + suf] = c if k is None else tuple(max(0, min(255, int(v * k + (12 if k > 1 else 0)))) for v in c)
        mk.NAMES.append(n + suf)
assert len(mk.NAMES) <= 256, len(mk.NAMES)
mk.TEX = "data:image/png;base64," + base64.b64encode(mk.palette_png()).decode()


# ------------------------------------------------------------------ shared humanoid skeleton

def skeleton(m, skin, shirt, pants, boots="leather_dark", arms_skin=None, extra_arm_r=None):
    """Bones + the plain vanilla-proportioned body; returns nothing, details are added by each mob."""
    a = arms_skin or skin
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 12, 0), "root")
    m.box("body", -4, 12, -2, 4, 24, 2, shirt)
    m.box("body", -4.1, 12, -2.1, 4.1, 14, 2.1, pants)  # belt line
    m.bone("h_head", (0, 24, 0), "body")
    m.box("h_head", -4, 24, -4, 4, 32, 4, skin)
    for side, x in (("arm_l", 6), ("arm_r", -6)):
        m.bone(side, (x, 22, 0), "body")
        s = 1 if x > 0 else -1
        m.box(side, min(x - 2, x + 2) + (0), 10, -2, max(x - 2, x + 2), 22, 2, a)
        m.box(side, min(x - 2.05, x + 2.05), 10, -2.05, max(x - 2.05, x + 2.05), 13, 2.05, a + "_d")  # hand
    for side, x in (("leg_l", 2), ("leg_r", -2)):
        m.bone(side, (x, 12, 0), "body")
        m.box(side, x - 2, 3, -2, x + 2, 12, 2, pants)
        m.box(side, x - 2.1, 0, -2.4, x + 2.1, 3.2, 2.1, boots)


def face(m, eye="black", mouth="soot", brow=None, glow=False):
    """Two eyes and a mouth on the front (z = -4)."""
    c = "glow_orange" if glow else eye
    m.box("h_head", -3, 27.5, -4.15, -1, 29, -4.0, "white")
    m.box("h_head", 1, 27.5, -4.15, 3, 29, -4.0, "white")
    m.box("h_head", -2.4, 27.6, -4.3, -1.4, 28.8, -4.15, c)
    m.box("h_head", 1.4, 27.6, -4.3, 2.4, 28.8, -4.15, c)
    m.box("h_head", -2, 25.2, -4.1, 2, 26, -4.0, mouth)
    if brow:
        m.box("h_head", -3.2, 29.4, -4.2, 3.2, 30.1, -4.05, brow)


def goggles(m, lens="glass_blue", y=29.5, band="leather_dark", frame="brass"):
    """Goggles pushed up on the forehead: a strap round the head, two brass rings with glass."""
    m.box("h_head", -4.2, y - 0.3, -4.2, 4.2, y + 0.8, 4.2, band)
    for x in (-2.2, 2.2):
        m.box("h_head", x - 1.7, y - 0.8, -5.0, x + 1.7, y + 1.6, -4.1, frame)
        m.box("h_head", x - 1.1, y - 0.2, -5.15, x + 1.1, y + 1.0, -4.95, lens)


def gear(m, bone, cx, cy, z, r=2.0, col="brass", front=True):
    """A flat gear (plus-shaped teeth) standing on the given z plane."""
    d = -0.8 if front else 0.8
    m.box(bone, cx - r, cy - r * 0.6, z, cx + r, cy + r * 0.6, z + d, col)
    m.box(bone, cx - r * 0.6, cy - r, z, cx + r * 0.6, cy + r, z + d, col)
    m.box(bone, cx - r * 0.3, cy - r * 0.3, z + d * 1.2, cx + r * 0.3, cy + r * 0.3, z + d * 1.4, "steel")


def pipe(m, bone, x1, y1, z1, x2, y2, z2, col="copper"):
    m.box(bone, x1, y1, z1, x2, y2, z2, col)


def boiler(m, bone, z0=2.2):
    """Steam pack on the back: tank, brass bands, two pipes to the shoulders, chimney and a pressure gauge."""
    m.box(bone, -3.4, 13, z0, 3.4, 23, z0 + 4.2, "copper")
    for y in (14.2, 19.6, 22):
        m.box(bone, -3.6, y, z0 - 0.1, 3.6, y + 0.9, z0 + 4.3, "brass")
    m.box(bone, -1.2, 23, z0 + 1.2, 1.2, 27.5, z0 + 3.4, "steel")          # chimney
    m.box(bone, -1.6, 27.5, z0 + 0.9, 1.6, 28.4, z0 + 3.7, "soot")
    pipe(m, bone, -3.2, 19, z0 - 0.8, -2.4, 24, z0 + 0.2, "brass")
    pipe(m, bone, 2.4, 19, z0 - 0.8, 3.2, 24, z0 + 0.2, "brass")
    m.box(bone, -1.2, 16, z0 + 4.2, 1.2, 18.2, z0 + 4.9, "iron")             # gauge
    m.box(bone, -0.8, 16.4, z0 + 4.9, 0.8, 17.8, z0 + 5.0, "glow_orange")
    m.box(bone, -1.8, 12.4, z0 + 0.6, 1.8, 13, z0 + 3.6, "soot")


# ------------------------------------------------------------------ animations (all humanoids)

def humanoid_anims(m, arms_forward=0.0, walk_amp=34, arm_amp=22, attack="slam", period=0.8):
    z = arms_forward
    m.anim("idle", 3.0, {
        "body": {"position": [(0, (0, 0, 0)), (1.5, (0, 0.25, 0)), (3.0, (0, 0, 0))]},
        "h_head": {"rotation": [(0, (0, 0, 0)), (1.0, (2, 8, 0)), (2.0, (0, -6, 0)), (3.0, (0, 0, 0))]},
        "arm_l": {"rotation": [(0, (z, 0, 2)), (1.5, (z + 2, 0, 4)), (3.0, (z, 0, 2))]},
        "arm_r": {"rotation": [(0, (z, 0, -2)), (1.5, (z + 2, 0, -4)), (3.0, (z, 0, -2))]}})
    m.anim("walk", period, {
        "leg_l": {"rotation": mk.wave(8, period, walk_amp)}, "leg_r": {"rotation": mk.wave(8, period, -walk_amp)},
        "arm_l": {"rotation": mk.wave(8, period, -arm_amp, 0, base=z)}, "arm_r": {"rotation": mk.wave(8, period, arm_amp, 0, base=z)},
        "body": {"position": [(0, (0, 0, 0)), (period / 4, (0, 0.5, 0)), (period / 2, (0, 0, 0)), (3 * period / 4, (0, 0.5, 0)), (period, (0, 0, 0))],
                 "rotation": mk.wave(8, period, 2.5, 0, axis=1)},
        "h_head": {"rotation": mk.wave(8, period, 2, 0.5, axis=1)}})
    if attack == "slam":      # both arms up, then down
        a = {"arm_l": {"rotation": [(0, (z, 0, 0)), (0.25, (150, 0, 0)), (0.45, (20, 0, 0)), (0.7, (z, 0, 0))]},
             "arm_r": {"rotation": [(0, (z, 0, 0)), (0.25, (150, 0, 0)), (0.45, (20, 0, 0)), (0.7, (z, 0, 0))]},
             "body": {"rotation": [(0, (0, 0, 0)), (0.25, (-12, 0, 0)), (0.45, (14, 0, 0)), (0.7, (0, 0, 0))]}}
    elif attack == "swing":   # right arm overhead swing across
        a = {"arm_r": {"rotation": [(0, (z, 0, 0)), (0.2, (160, 0, -10)), (0.4, (30, 0, 10)), (0.7, (z, 0, 0))]},
             "arm_l": {"rotation": [(0, (z, 0, 0)), (0.4, (z - 25, 0, 0)), (0.7, (z, 0, 0))]},
             "body": {"rotation": [(0, (0, 0, 0)), (0.2, (-8, 12, 0)), (0.4, (6, -20, 0)), (0.7, (0, 0, 0))]}}
    else:                     # aim and recoil
        a = {"arm_r": {"rotation": [(0, (z, 0, 0)), (0.2, (88, 0, 0)), (0.5, (88, 0, 0)), (0.6, (96, 0, 0)), (0.9, (z, 0, 0))]},
             "arm_l": {"rotation": [(0, (z, 0, 0)), (0.2, (80, 0, 14)), (0.6, (80, 0, 14)), (0.9, (z, 0, 0))]},
             "body": {"rotation": [(0, (0, 0, 0)), (0.5, (0, 0, 0)), (0.6, (-6, 0, 0)), (0.9, (0, 0, 0))]}}
    m.anim("attack", 0.9 if attack == "shoot" else 0.7, a, loop="once")
    m.anim("death", 0.9, {
        "root": {"rotation": [(0, (0, 0, 0)), (0.6, (90, 0, 0)), (0.9, (90, 0, 0))],
                 "position": [(0, (0, 0, 0)), (0.6, (0, 0, 2)), (0.9, (0, 0, 2))]},
        "arm_l": {"rotation": [(0, (0, 0, 0)), (0.4, (-30, 0, 20)), (0.9, (-30, 0, 20))]},
        "arm_r": {"rotation": [(0, (0, 0, 0)), (0.4, (-30, 0, -20)), (0.9, (-30, 0, -20))]}}, loop="hold")


# ------------------------------------------------------------------ the mobs

def steam_zombie(m):
    """Zombie with a boiler on its back, goggles on the forehead and a brass piston for a right forearm."""
    skeleton(m, "zombie_skin", "vest", "pants", boots="leather_dark")
    face(m, eye="black", mouth="soot", brow="zombie_skin_d")
    goggles(m)
    m.box("h_head", -4.1, 31.2, -4.1, 4.1, 32.2, 4.1, "zombie_skin_d")                       # scabby crown
    m.box("body", -4.1, 14, -2.1, -3.0, 24, -1.9, "leather")                                   # waistcoat seams
    m.box("body", 3.0, 14, -2.1, 4.1, 24, -1.9, "leather")
    for y in (16, 18.5, 21):
        m.box("body", -0.4, y, -2.2, 0.4, y + 0.8, -2.0, "brass")                              # buttons
    m.box("body", -4.2, 22.4, -2.2, 4.2, 23.4, 2.2, "scarf")                                   # torn scarf
    m.box("body", 1.2, 18, -2.3, 3.4, 22.4, -2.0, "zombie_skin_d")                             # exposed patch
    boiler(m, "body")
    # right arm: piston forearm with pipe and gear
    m.box("arm_r", -8.1, 15.5, -2.3, -3.9, 22, 2.3, "vest")
    m.box("arm_r", -7.6, 8.5, -1.7, -4.4, 15.5, 1.7, "brass")
    m.box("arm_r", -7.0, 6.5, -1.1, -5.0, 8.5, 1.1, "steel")
    m.box("arm_r", -7.9, 10, -0.6, -7.6, 18, 0.6, "copper")
    m.box("arm_r", -7.4, 5.0, -1.4, -4.6, 6.5, 1.4, "iron")
    gear(m, "arm_r", -8.3, 14, 0.0, 1.6)
    m.box("arm_l", 3.9, 19.5, -2.3, 8.1, 22, 2.3, "vest")                                      # torn sleeve
    for side, x in (("leg_l", 2), ("leg_r", -2)):
        m.box(side, x - 2.2, 7.5, -2.2, x + 2.2, 8.5, 2.2, "brass")                            # knee strap
    m.box("leg_l", 0, 3.2, -2.5, 4.2, 4.2, 2.1, "brass")
    humanoid_anims(m, arms_forward=70, walk_amp=30, arm_amp=12, attack="slam", period=0.9)


def clockwork_skeleton(m):
    """Skeleton held together by brass: gear joints, a visible mechanism in the ribcage and a wind-up key."""
    skeleton(m, "bone", "bone", "bone", boots="bone_d")
    # slimmer limbs like the vanilla skeleton: replace by thin new boxes over the base ones
    m.box("body", -3.2, 12, -1.2, 3.2, 24, 1.2, "steel")                                        # inner spine block (hides the solid chest)
    for y in (15, 17.2, 19.4, 21.6):
        m.box("body", -4.1, y, -2.1, 4.1, y + 1.2, 2.1, "bone")                                 # ribs
    m.box("body", -0.9, 12, -2.3, 0.9, 24, -1.5, "bone_d")                                      # sternum
    gear(m, "body", -1.5, 18, -2.2, 2.4, "brass")
    gear(m, "body", 2.2, 15.5, -2.2, 1.6, "copper")
    m.box("body", -1.2, 21, 2.0, 1.2, 23, 3.0, "brass")                                         # key base
    m.box("body", -0.5, 21.6, 3.0, 0.5, 22.4, 7.0, "brass")                                     # key shaft
    m.box("body", -3.5, 20.4, 6.4, 3.5, 23.6, 7.4, "brass")                                     # key wings
    face(m, eye="black", mouth="soot", glow=False)
    m.box("h_head", -2.8, 27.5, -4.25, -1.2, 29.2, -4.1, "soot")                                # hollow eye sockets
    m.box("h_head", 1.2, 27.5, -4.25, 2.8, 29.2, -4.1, "soot")
    m.box("h_head", -2.0, 27.9, -4.4, -1.6, 28.5, -4.25, "glow_orange")
    m.box("h_head", 1.6, 27.9, -4.4, 2.0, 28.5, -4.25, "glow_orange")
    m.box("h_head", -4.1, 30.5, -3.0, 4.1, 31.4, 3.0, "brass")                                  # brass crown plate
    gear(m, "h_head", 0, 31.0, -3.0, 1.2)
    for bone, x in (("arm_l", 6), ("arm_r", -6)):
        m.box(bone, x - 2.4, 20.8, -2.4, x + 2.4, 23.2, 2.4, "brass")                           # shoulder joint
        m.box(bone, x - 2.2, 15.6, -2.2, x + 2.2, 16.8, 2.2, "brass")                           # elbow
        m.box(bone, x - 1.0, 17, -1.0, x + 1.0, 21, 1.0, "wire")
    for bone, x in (("leg_l", 2), ("leg_r", -2)):
        m.box(bone, x - 2.3, 6.4, -2.3, x + 2.3, 7.6, 2.3, "brass")                             # knee
        m.box(bone, x - 1.0, 8, -2.35, x + 1.0, 11, -2.2, "wire")
    m.box("arm_r", -8.2, 5, -1.5, -3.8, 9.5, 1.5, "iron")                                       # clamp hand
    humanoid_anims(m, arms_forward=0, walk_amp=28, arm_amp=26, attack="swing", period=0.7)


def steam_pillager(m):
    """Pillager as an airship raider: pilot cap with goggles, long coat, brass pauldron and a steam crossbow."""
    skeleton(m, "pillager_skin", "coat_blue", "coat_blue", boots="leather_dark", arms_skin="coat_blue")
    face(m, eye="black", mouth="soot", brow="soot")
    m.box("h_head", -1, 24.4, -5.6, 1, 27.8, -4.0, "pillager_skin_d")                           # long nose
    m.box("h_head", -4.3, 30.2, -4.3, 4.3, 33.4, 4.3, "leather")                                # aviator cap
    m.box("h_head", -4.3, 25, 3.9, -3.6, 31, 4.5, "leather_dark")
    m.box("h_head", 3.6, 25, 3.9, 4.3, 31, 4.5, "leather_dark")
    m.box("h_head", -4.45, 26.4, -3, -3.9, 30.4, 3.5, "leather")                                # ear flaps
    m.box("h_head", 3.9, 26.4, -3, 4.45, 30.4, 3.5, "leather")
    goggles(m, y=30.4, band="leather_dark")
    m.box("body", -4.3, 3, -2.3, 4.3, 12, 2.3, "coat_blue")                                     # coat skirt over the legs
    m.box("body", -4.3, 3, -2.3, 4.3, 4.2, 2.3, "coat_blue_d")
    m.box("body", -0.5, 3.2, -2.4, 0.5, 23.5, -2.2, "brass")                                    # buttoned front
    for y in (6, 10, 14, 18):
        m.box("body", -1.2, y, -2.5, 1.2, y + 1, -2.3, "brass")
    m.box("body", -4.1, 22.4, -2.2, 4.1, 24.6, 2.2, "scarf")
    m.box("body", -4.1, 12.4, -2.1, 4.1, 13.6, 2.1, "leather_dark")                             # belt
    m.box("body", -0.9, 12.2, -2.35, 0.9, 13.8, -2.15, "brass")
    m.box("body", -3.6, 14, 2.1, 3.6, 22, 3.4, "leather")                                       # small pack
    m.box("body", -2, 22, 2.3, 2, 23.5, 3.2, "brass")
    m.box("arm_l", 3.7, 19.6, -2.8, 8.4, 23.4, 2.8, "brass")                                    # pauldron
    m.box("arm_l", 3.9, 22.6, -2.2, 8.2, 24, 2.2, "brass_d")
    for z in (-2, 0, 2):
        m.box("arm_l", 8.2, 21, z - 0.3, 8.6, 22, z + 0.3, "iron")
    m.box("arm_r", -8.2, 10, -2.4, -3.8, 12, 2.4, "leather")                                    # cuffs
    m.box("arm_r", -8.2, 12, -2.4, -3.8, 22, 2.4, "coat_blue")
    # crossbow held in the right hand, pointing forward (-z)
    m.box("arm_r", -7.2, 10.2, -9.0, -4.8, 12.6, -2.0, "wood_dark" if "wood_dark" in mk.PALETTE else "leather_dark")
    m.box("arm_r", -9.5, 11.0, -8.6, -2.5, 12.0, -7.4, "steel")                                 # bow limbs
    m.box("arm_r", -7.0, 12.6, -8.5, -5.0, 13.8, -3.0, "copper")                                # steam cylinder
    m.box("arm_r", -6.4, 13.8, -5.2, -5.6, 15.6, -4.4, "brass")
    humanoid_anims(m, arms_forward=0, walk_amp=24, arm_amp=16, attack="shoot", period=0.85)


def rust_husk(m):
    """Husk as a desert mechanic: head wrap with a brass monocle, tool belt, rusty steam wrench."""
    skeleton(m, "husk_skin", "cloth", "rust", boots="leather_dark", arms_skin="husk_skin")
    face(m, eye="black", mouth="soot")
    m.box("h_head", -4.3, 28.6, -4.3, 4.3, 32.4, 4.3, "cloth")                                  # wrap
    m.box("h_head", -4.3, 31.6, -4.3, 4.3, 32.6, 4.3, "cloth_d")
    m.box("h_head", -4.3, 24.4, -4.3, 4.3, 26.2, 4.3, "cloth")                                  # face cloth
    m.box("h_head", -4.1, 26.2, -4.4, 4.1, 27.0, -4.0, "cloth_d")
    m.box("h_head", 0.4, 26.8, -5.1, 3.6, 30.0, -4.1, "brass")                                  # monocle
    m.box("h_head", 0.9, 27.3, -5.25, 3.1, 29.5, -5.1, "glass_blue")
    m.box("h_head", 3.5, 24.5, -4.2, 3.9, 28, -3.9, "brass")                                    # chain
    m.box("body", -4.1, 13, -2.1, 4.1, 14.8, 2.1, "leather")                                    # tool belt
    for x, h in ((-3, 3), (-1, 4), (1, 3.4), (3, 2.6)):
        m.box("body", x - 0.5, 14.8 - h, -2.5, x + 0.5, 14.8, -2.1, "steel")
    m.box("body", -4.1, 19, -2.2, 4.1, 21, 2.2, "leather")                                      # sash
    m.box("body", -2.4, 12.4, 2.1, 2.4, 20, 4.3, "rust")                                        # backpack
    m.box("body", -1.2, 20, 2.6, 1.2, 24, 3.6, "iron")
    m.box("arm_r", -8.1, 10, -2.2, -3.9, 14, 2.2, "leather")                                    # glove
    m.box("arm_r", -6.9, 10.4, -9.6, -5.1, 13.2, -2.0, "steel")                                 # wrench shaft
    m.box("arm_r", -7.9, 10.0, -12.4, -4.1, 13.6, -9.6, "iron")                                 # wrench head
    m.box("arm_r", -6.5, 10.8, -12.5, -5.5, 12.8, -10.2, "husk_skin")                           # jaw gap
    m.box("arm_r", -7.1, 13.2, -7.5, -4.9, 14.4, -5.5, "brass")
    m.box("arm_l", 3.8, 14, -2.3, 8.2, 22, 2.3, "cloth")
    m.box("arm_l", 3.7, 16.5, -2.4, 8.3, 17.5, 2.4, "leather")
    humanoid_anims(m, arms_forward=0, walk_amp=30, arm_amp=22, attack="swing", period=0.85)


MOBS = {"em_steam_zombie": steam_zombie, "em_clockwork_skeleton": clockwork_skeleton,
        "em_steam_pillager": steam_pillager, "em_rust_husk": rust_husk}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "../fmm-models/elitemobs"
    os.makedirs(out, exist_ok=True)
    for name, build in MOBS.items():
        m = mk.Model(name)
        build(m)
        with open(os.path.join(out, name + ".bbmodel"), "w", encoding="utf-8") as f:
            json.dump(m.bbmodel(), f)
    print("wrote", len(MOBS), "models to", out)


if __name__ == "__main__":
    main()
