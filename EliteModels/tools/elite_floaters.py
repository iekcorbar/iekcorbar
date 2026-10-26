"""Flying, floating, cube and boss-size EliteMobs models (own designs): blaze, ghast, vex, guardians, breeze, shulker,
slime, magma cube, ender dragon, wither. Front = -Z. Registered in MOBS."""
import make_elite as me
from make_elite import mk, gear, pipe

MOBS = {}


def mob(name, size=1.0):
    def deco(fn):
        MOBS[name] = fn
        if size != 1.0:
            mk.SIZE[name[3:]] = size
        return fn
    return deco


def root_body(m, y):
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, y, 0), "root")


def hover(m, amp=1.0, period=3.0):
    m.anim("idle", period, {"body": {"position": [(0, (0, 0, 0)), (period / 2, (0, amp, 0)), (period, (0, 0, 0))]}})
    m.anim("walk", period, {"body": {"position": [(0, (0, 0, 0)), (period / 2, (0, amp, 0)), (period, (0, 0, 0))],
                                     "rotation": [(0, (6, 0, 0)), (period / 2, (10, 0, 0)), (period, (6, 0, 0))]}})


def fall(m):
    m.anim("death", 0.8, {"root": {"rotation": [(0, (0, 0, 0)), (0.7, (40, 0, 20))], "position": [(0, (0, 0, 0)), (0.7, (0, -14, 0))]}}, loop="hold")


def lunge(m, bone="h_head"):
    m.anim("attack", 0.6, {bone: {"rotation": [(0, (0, 0, 0)), (0.2, (-30, 0, 0)), (0.35, (22, 0, 0)), (0.6, (0, 0, 0))]},
                           "body": {"position": [(0, (0, 0, 0)), (0.2, (0, 0, 3)), (0.35, (0, 0, -6)), (0.6, (0, 0, 0))]}}, loop="once")


# ------------------------------------------------------------------ nether

@mob("em_furnace_blaze")
def furnace_blaze(m):
    """Blaze whose head is a riveted furnace; the rods are brass pistons with glowing tips, circling it."""
    root_body(m, 12)
    m.bone("h_head", (0, 20, 0), "body")
    m.box("h_head", -4.5, 16, -4.5, 4.5, 25, 4.5, "iron")
    m.box("h_head", -4.7, 20, -4.7, 4.7, 21.2, 4.7, "brass")
    m.box("h_head", -3.2, 17.4, -4.8, 3.2, 22.6, -4.6, "soot")                            # furnace door
    m.box("h_head", -2.6, 18.2, -5.0, 2.6, 21.8, -4.8, "lava")
    m.box("h_head", -3.4, 22.8, -4.8, -1.2, 24, -4.6, "glow_orange")                        # eyes
    m.box("h_head", 1.2, 22.8, -4.8, 3.4, 24, -4.6, "glow_orange")
    m.box("h_head", -1.4, 25, -1.4, 1.4, 29, 1.4, "steel")                                   # chimney
    m.box("h_head", -1.9, 29, -1.9, 1.9, 30, 1.9, "soot")
    for ring, (h, r, off) in enumerate(((13, 7, 0), (8, 8.5, 0.8), (3, 6.5, 1.6))):
        m.bone("rods%d" % ring, (0, h, 0), "body")
        for k in range(4):
            import math
            a = off + k * math.pi / 2
            x, z = math.cos(a) * r, math.sin(a) * r
            m.box("rods%d" % ring, x - 0.9, h - 2.8, z - 0.9, x + 0.9, h + 2.8, z + 0.9, "brass")
            m.box("rods%d" % ring, x - 0.5, h + 2.8, z - 0.5, x + 0.5, h + 4.4, z + 0.5, "lava")
    spin = lambda p, s: {"rotation": [(0, (0, 0, 0)), (p / 2, (0, 180 * s, 0)), (p, (0, 360 * s, 0))]}
    tracks = {"rods0": spin(3.0, 1), "rods1": spin(2.4, -1), "rods2": spin(2.0, 1),
              "body": {"position": [(0, (0, 0, 0)), (1.5, (0, 1, 0)), (3.0, (0, 0, 0))]}}
    m.anim("idle", 3.0, tracks)
    m.anim("walk", 3.0, tracks)
    m.anim("attack", 0.8, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.8, (0, 0, 0))]}, "rods0": spin(0.8, 2)}, loop="once")
    fall(m)


@mob("em_airship_ghast", 1.0)
def airship_ghast(m):
    """Ghast as a small blimp: a patched balloon with brass bands, hanging ropes and a gondola with the face."""
    root_body(m, 20)
    m.box("body", -16, 16, -16, 16, 48, 16, "ghast_white")
    for y in (22, 32, 42):
        m.box("body", -16.3, y, -16.3, 16.3, y + 1.6, 16.3, "brass")
    for x, z in ((-12, -12), (12, -12), (-12, 12), (12, 12)):
        m.box("body", x - 1.5, 47.6, z - 1.5, x + 1.5, 49, z + 1.5, "copper")
    m.box("body", -5, 20, -16.4, 5, 28, -16, "leather")                                    # patch
    m.bone("h_head", (0, 16, 0), "body")
    m.box("h_head", -6, 8, -6, 6, 16, 6, "wood")                                           # gondola
    m.box("h_head", -6.3, 14, -6.3, 6.3, 15, 6.3, "brass")
    m.box("h_head", -4.2, 10, -6.2, -1.2, 13.4, -6, "red_eye")                              # eyes
    m.box("h_head", 1.2, 10, -6.2, 4.2, 13.4, -6, "red_eye")
    m.box("h_head", -2, 8.6, -6.2, 2, 9.6, -6, "soot")
    for x, z in ((-12, -12), (12, -12), (-12, 12), (12, 12)):
        m.box("h_head", x * 0.35 - 0.3, 15, z * 0.35 - 0.3, x * 0.35 + 0.3, 17, z * 0.35 + 0.3, "leather_dark")
    for i, (x, z) in enumerate(((-10, -10), (-3, -10), (4, -10), (10, -10), (-8, 0), (0, 0), (8, 0), (-6, 10), (6, 10))):
        m.bone("rope%d" % i, (x, 16, z), "body")
        m.box("rope%d" % i, x - 0.7, 3, z - 0.7, x + 0.7, 16, z + 0.7, "leather")
        m.box("rope%d" % i, x - 1.4, 1, z - 1.4, x + 1.4, 3, z + 1.4, "brass")
    sway = {"rope%d" % i: {"rotation": mk.wave(8, 3.0, 8, i * 0.7, axis=2)} for i in range(9)}
    m.anim("idle", 4.0, dict(sway, body={"position": [(0, (0, 0, 0)), (2, (0, 1.5, 0)), (4, (0, 0, 0))]}))
    m.anim("walk", 4.0, dict(sway, body={"position": [(0, (0, 0, 0)), (2, (0, 1.5, 0)), (4, (0, 0, 0))]}))
    m.anim("attack", 0.8, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.3, (-16, 0, 0)), (0.8, (0, 0, 0))]},
                           "body": {"position": [(0, (0, 0, 0)), (0.3, (0, 0, 2)), (0.8, (0, 0, 0))]}}, loop="once")
    fall(m)


@mob("em_magma_cube_furnace")
def magma_furnace(m):
    """Magma cube: a hot cast-iron cube with glowing seams, a grate and tiny exhaust pipes."""
    root_body(m, 0)
    m.box("body", -8, 0, -8, 8, 16, 8, "magma")
    m.box("body", -8.2, 11, -8.2, 8.2, 12, 8.2, "iron")
    m.box("body", -8.2, 4, -8.2, 8.2, 5, 8.2, "iron")
    for x in (-5.5, 0, 5.5):
        m.box("body", x - 0.8, 0.2, -8.3, x + 0.8, 15.8, -8, "lava")
    m.box("body", -4.5, 6, -8.4, 4.5, 10.4, -8.1, "soot")                                   # grate
    for x in (-3, -1, 1, 3):
        m.box("body", x - 0.3, 6.2, -8.6, x + 0.3, 10.2, -8.4, "lava")
    m.box("body", -4.5, 12.6, -8.4, -2, 14.2, -8.2, "glow_orange")                           # eyes
    m.box("body", 2, 12.6, -8.4, 4.5, 14.2, -8.2, "glow_orange")
    for x in (-5, 5):
        m.box("body", x - 1, 16, -1, x + 1, 19, 1, "steel")
        m.box("body", x - 1.3, 19, -1.3, x + 1.3, 19.8, 1.3, "soot")
    m.anim("idle", 1.6, {"body": {"position": [(0, (0, 0, 0)), (0.8, (0, 0.6, 0)), (1.6, (0, 0, 0))]}})
    m.anim("walk", 1.2, {"body": {"position": [(0, (0, 0, 0)), (0.4, (0, 5, 0)), (0.8, (0, 0, 0)), (1.2, (0, 0, 0))]}})
    m.anim("attack", 0.6, {"body": {"position": [(0, (0, 0, 0)), (0.25, (0, 6, -3)), (0.6, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.6, {"root": {"position": [(0, (0, 0, 0)), (0.5, (0, -8, 0))]}}, loop="hold")


@mob("em_gear_slime")
def gear_slime(m):
    """Slime with a spinning brass gear in its middle (shown through a window) and a copper cap."""
    root_body(m, 0)
    m.box("body", -8, 0, -8, 8, 14, 8, "slime_green")
    m.box("body", -6, 2, -6, 6, 12, 6, "slime_green_l")
    m.box("body", -4, 4, -8.3, 4, 10, -8.0, "slime_core")                                    # window
    m.bone("core", (0, 7, -8.4), "body")
    m.box("core", -2.8, 4.2, -8.8, 2.8, 9.8, -8.4, "brass")
    m.box("core", -1, 5.6, -9.0, 1, 8.4, -8.8, "steel")
    m.box("body", -5.4, 10, -8.3, -2.6, 12.4, -8.0, "black")                                  # eyes
    m.box("body", 2.6, 10, -8.3, 5.4, 12.4, -8.0, "black")
    m.box("body", -2, 13.8, -2, 2, 15.4, 2, "copper")
    m.anim("idle", 2.0, {"body": {"position": [(0, (0, 0, 0)), (1, (0, 0.8, 0)), (2, (0, 0, 0))]},
                         "core": {"rotation": [(0, (0, 0, 0)), (2, (0, 0, 360))]}})
    m.anim("walk", 1.2, {"body": {"position": [(0, (0, 0, 0)), (0.4, (0, 4, 0)), (0.8, (0, 0, 0)), (1.2, (0, 0, 0))]},
                         "core": {"rotation": [(0, (0, 0, 0)), (1.2, (0, 0, 360))]}})
    m.anim("attack", 0.6, {"body": {"position": [(0, (0, 0, 0)), (0.25, (0, 5, -3)), (0.6, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.6, {"root": {"position": [(0, (0, 0, 0)), (0.5, (0, -6, 0))]}}, loop="hold")


# ------------------------------------------------------------------ small fliers and swimmers

@mob("em_imp_vex")
def imp_vex(m):
    """Vex as a clockwork imp: brass wings on hinges, a spring tail and a tiny sword."""
    root_body(m, 8)
    m.box("body", -2.4, 6, -1.6, 2.4, 14, 1.6, "silver")
    m.box("body", -2.6, 9, -1.8, 2.6, 10, 1.8, "brass")
    m.box("body", -1.4, 2, -1, 1.4, 6, 1, "silver_d")
    m.bone("h_head", (0, 14, 0), "body")
    m.box("h_head", -2.6, 14, -2.6, 2.6, 19.6, 2.6, "silver_l")
    m.box("h_head", -2.2, 16.4, -2.8, -0.6, 17.8, -2.6, "red_eye")
    m.box("h_head", 0.6, 16.4, -2.8, 2.2, 17.8, -2.6, "red_eye")
    for x in (-2.2, 2.2):
        m.box("h_head", x - 0.5, 19.6, -0.5, x + 0.5, 22.6, 0.5, "brass")                # antennae
    for side, x, s in (("wing_l", 2.2, 1), ("wing_r", -2.2, -1)):
        m.bone(side, (x, 12, 1.2), "body")
        m.box(side, x, 10.5, 1, x + s * 9, 13.5, 1.6, "brass_l")
        m.box(side, x + s * 1, 6, 1, x + s * 7, 10.5, 1.6, "brass")
        m.box(side, x + s * 8.4, 10.5, 0.9, x + s * 9.2, 13.5, 1.7, "copper")
    m.bone("arm_r", (-2.4, 12.4, 0), "body")
    m.box("arm_r", -4.2, 6.4, -1, -2.4, 12.4, 1, "silver_d")
    m.box("arm_r", -3.6, 5.6, -7.4, -3.0, 6.6, -1, "steel")                                 # sword
    m.box("arm_r", -4.2, 5.2, -2.4, -2.4, 7, -1.6, "brass")
    flap = {"wing_l": {"rotation": mk.wave(8, 0.4, 35, 0, axis=2)}, "wing_r": {"rotation": mk.wave(8, 0.4, -35, 0, axis=2)}}
    m.anim("idle", 0.4, dict(flap))
    m.anim("walk", 0.4, dict(flap))
    m.anim("attack", 0.5, {"arm_r": {"rotation": [(0, (0, 0, 0)), (0.15, (-110, 0, 0)), (0.3, (30, 0, 0)), (0.5, (0, 0, 0))]}}, loop="once")
    fall(m)


def guardian(m, size_col, spike, eye):
    dark = size_col if size_col.endswith("_d") else size_col + "_d"
    root_body(m, 8)
    m.box("body", -6, 2, -6, 6, 14, 9, size_col)
    m.box("body", -6.2, 6, -6.2, 6.2, 7, 9.2, "brass")
    m.box("body", -6.2, 10, -6.2, 6.2, 11, 9.2, "brass")
    for x, y, z in ((-6.6, 12, 0), (6.6, 12, 0), (0, 14.4, 2), (-6.6, 4, 4), (6.6, 4, 4), (0, 1.4, 3)):
        m.box("body", x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, spike)
        m.box("body", x - 0.5, y - 0.5, z - 0.5, x + (1.6 if x > 0 else -1.6 if x < 0 else 0.5), y + (1.6 if y > 8 else -1.6 if y < 8 else 0.5), z + 0.5, spike)
    m.bone("h_head", (0, 8, -6), "body")
    m.box("h_head", -3.6, 4.4, -7.4, 3.6, 11.6, -5.8, dark)
    m.box("h_head", -1.7, 6.2, -8.6, 1.7, 9.8, -7.4, "brass")                                # eye socket
    m.box("h_head", -1.1, 6.8, -8.9, 1.1, 9.2, -8.6, eye)
    m.bone("tail", (0, 8, 9), "body")
    m.box("tail", -1.5, 5, 9, 1.5, 11, 14, size_col)
    m.box("tail", -0.5, 3, 12, 0.5, 13, 18, dark)
    wig = {"tail": {"rotation": mk.wave(8, 1.2, 14, 0, axis=1)}}
    m.anim("idle", 1.2, dict(wig, body={"position": [(0, (0, 0, 0)), (0.6, (0, 0.8, 0)), (1.2, (0, 0, 0))]}))
    m.anim("walk", 0.8, {"tail": {"rotation": mk.wave(8, 0.8, 22, 0, axis=1)}})
    m.anim("attack", 0.6, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.2, (0, 0, 0)), (0.3, (0, 0, 0)), (0.6, (0, 0, 0))]},
                           "body": {"position": [(0, (0, 0, 0)), (0.25, (0, 0, 3)), (0.6, (0, 0, 0))]}}, loop="once")
    fall(m)


@mob("em_submarine_guardian")
def submarine_guardian(m):
    guardian(m, "prismarine", "sea_lantern", "red_eye")


@mob("em_elder_submarine", 2.2)
def elder_submarine(m):
    guardian(m, "prismarine_d", "brass", "glow_orange")
    m.box("body", -4, 14, -4, 4, 16, 6, "gold")
    for x in (-3, 0, 3):
        m.box("body", x - 0.6, 16, -0.6, x + 0.6, 19, 0.6, "gold")


@mob("em_whirl_breeze")
def whirl_breeze(m):
    """Breeze as a steam vortex: a vented brass head and three flat rings of wind turning at different speeds."""
    root_body(m, 4)
    m.bone("h_head", (0, 20, 0), "body")
    m.box("h_head", -4, 18, -4, 4, 26, 4, "steel")
    m.box("h_head", -4.2, 21, -4.2, 4.2, 22, 4.2, "brass")
    for x in (-2.4, 0.2, 2.8):
        m.box("h_head", x - 0.4, 18.6, -4.3, x + 0.4, 20.6, -4.1, "soot")                     # vents
    m.box("h_head", -3, 23, -4.3, -1, 24.6, -4.1, "wind")
    m.box("h_head", 1, 23, -4.3, 3, 24.6, -4.1, "wind")
    for i, (y, r) in enumerate(((14, 8), (9, 6), (4, 4))):
        m.bone("ring%d" % i, (0, y, 0), "body")
        m.box("ring%d" % i, -r, y, -r, r, y + 1.2, r, "wind")
        m.box("ring%d" % i, -r + 1.4, y - 0.2, -r + 1.4, r - 1.4, y + 1.4, r - 1.4, "wind_l")
        m.box("ring%d" % i, r - 1, y + 0.2, -1, r + 0.6, y + 1.0, 1, "brass")
    spin = lambda p, s: {"rotation": [(0, (0, 0, 0)), (p / 2, (0, 180 * s, 0)), (p, (0, 360 * s, 0))]}
    tracks = {"ring0": spin(2.4, 1), "ring1": spin(1.6, -1), "ring2": spin(1.1, 1),
              "body": {"position": [(0, (0, 0, 0)), (1.5, (0, 1, 0)), (3.0, (0, 0, 0))]}}
    m.anim("idle", 3.0, tracks)
    m.anim("walk", 3.0, tracks)
    m.anim("attack", 0.8, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.8, (0, 0, 0))]}, "ring0": spin(0.8, 3)}, loop="once")
    fall(m)


@mob("em_vault_shulker")
def vault_shulker(m):
    """Shulker as a riveted vault: iron base, a lid that lifts and a single glowing lens inside."""
    root_body(m, 0)
    m.box("body", -8, 0, -8, 8, 8, 8, "iron")
    m.box("body", -8.2, 3, -8.2, 8.2, 4, 8.2, "brass")
    m.box("body", -6, 8, -6, 6, 9, 6, "iron_d")
    m.box("body", -3, 8.8, -3, 3, 9.4, 3, "shulker")                                          # the creature inside
    m.box("body", -1.2, 9.2, -3.2, 1.2, 10.6, -2.6, "shulker_l")
    m.bone("h_head", (0, 8, 0), "body")                                                       # lid
    m.box("h_head", -8, 9, -8, 8, 16, 8, "shulker")
    m.box("h_head", -8.2, 11, -8.2, 8.2, 12, 8.2, "brass")
    m.box("h_head", -1.6, 12.4, -8.4, 1.6, 15, -8.0, "iron")                                   # dial
    m.box("h_head", -0.8, 13, -8.6, 0.8, 14.4, -8.4, "glow_orange")
    for x, z in ((-7, -7), (7, -7), (-7, 7), (7, 7)):
        m.box("h_head", x - 0.6, 15.8, z - 0.6, x + 0.6, 16.6, z + 0.6, "brass_l")
    m.anim("idle", 3.0, {"h_head": {"position": [(0, (0, 0, 0)), (1.5, (0, 0.6, 0)), (3, (0, 0, 0))]}})
    m.anim("walk", 3.0, {"h_head": {"position": [(0, (0, 0, 0)), (1.5, (0, 0.6, 0)), (3, (0, 0, 0))]}})
    m.anim("attack", 0.8, {"h_head": {"position": [(0, (0, 0, 0)), (0.2, (0, 7, 0)), (0.6, (0, 7, 0)), (0.8, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.6, {"h_head": {"position": [(0, (0, 0, 0)), (0.5, (0, 9, 0))]}}, loop="hold")


# ------------------------------------------------------------------ big bosses

@mob("em_zeppelin_dragon", 1.0)
def zeppelin_dragon(m):
    """Ender dragon with copper plating, a boiler in the chest, brass wing struts and a glowing furnace throat."""
    root_body(m, 20)
    m.box("body", -8, 12, -16, 8, 28, 16, "end_black")
    m.box("body", -8.4, 22, -12, 8.4, 28.6, 14, "copper")
    for z in (-8, 0, 8):
        m.box("body", -8.6, 21, z - 0.5, 8.6, 29, z + 0.5, "brass")
    m.box("body", -4, 28.6, -4, 4, 36, 6, "steel")
    m.box("body", -1.4, 36, 0, 1.4, 42, 3, "soot")
    for i in range(7):                                                                         # spine spikes
        m.box("body", -0.8, 28.4, -14 + i * 4.4, 0.8, 31.5, -12.4 + i * 4.4, "end_purple")
    m.bone("neck", (0, 22, -16), "body")
    for i in range(3):
        m.box("neck", -4 + i * 0.3, 20 + i * 1.4, -26 - i * 7, 4 - i * 0.3, 28 + i * 1.4, -17 - i * 7, "end_black")
        m.box("neck", -0.7, 28 + i * 1.4, -24 - i * 7, 0.7, 30.6 + i * 1.4, -19 - i * 7, "end_purple")
    m.bone("h_head", (0, 28, -40), "neck")
    m.box("h_head", -5, 24, -50, 5, 33, -38, "end_black")
    m.box("h_head", -4, 22, -52, 4, 25, -40, "end_black_d")                                    # lower jaw
    m.box("h_head", -3.2, 25, -52.6, 3.2, 27, -50, "tusk")
    m.box("h_head", -3.6, 30.4, -50.4, -1.4, 32, -49.8, "end_eye")
    m.box("h_head", 1.4, 30.4, -50.4, 3.6, 32, -49.8, "end_eye")
    for x in (-3.4, 3.4):
        m.box("h_head", x - 0.7, 33, -44, x + 0.7, 38, -42, "brass")
    m.bone("tail", (0, 20, 16), "body")
    for i in range(4):
        m.box("tail", -3 + i * 0.6, 17 + i, 16 + i * 8, 3 - i * 0.6, 24 + i, 24 + i * 8, "end_black")
    m.box("tail", -1.5, 24, 44, 1.5, 36, 48, "copper")                                         # rudder
    for side, s in (("wing_l", 1), ("wing_r", -1)):
        m.bone(side, (s * 8, 26, -6), "body")
        m.box(side, s * 8, 25, -8, s * 40, 28, -4, "end_black")
        m.box(side, s * 8, 26, -7, s * 40, 27, -5, "brass")
        m.box(side, s * 8, 25.4, 0, s * 38, 26.2, 18, "end_purple_d")
        m.box(side, s * 20, 25.2, -6, s * 20.8, 26.4, 12, "brass")
        m.box(side, s * 32, 25.2, -6, s * 32.8, 26.4, 8, "brass")
    for name, x, z in (("leg_l", 6, -6), ("leg_r", -6, -6)):
        m.bone(name, (x, 14, z), "body")
        m.box(name, x - 2, 4, z - 2, x + 2, 14, z + 2, "end_black")
        m.box(name, x - 2.4, 0, z - 3.4, x + 2.4, 4, z + 2.4, "steel")
    flap = {"wing_l": {"rotation": mk.wave(8, 2.0, 22, 0, axis=2)}, "wing_r": {"rotation": mk.wave(8, 2.0, -22, 0, axis=2)},
            "tail": {"rotation": mk.wave(8, 2.0, 8, 0.5, axis=1)}, "neck": {"rotation": mk.wave(8, 2.0, 4, 0, axis=1)},
            "body": {"position": [(0, (0, 0, 0)), (1, (0, 1.5, 0)), (2, (0, 0, 0))]}}
    m.anim("idle", 2.0, flap)
    m.anim("walk", 2.0, flap)
    m.anim("attack", 1.0, {"neck": {"rotation": [(0, (0, 0, 0)), (0.3, (-20, 0, 0)), (0.6, (14, 0, 0)), (1.0, (0, 0, 0))]},
                           "h_head": {"rotation": [(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.6, (10, 0, 0)), (1.0, (0, 0, 0))]}}, loop="once")
    m.anim("death", 1.5, {"root": {"rotation": [(0, (0, 0, 0)), (1.3, (60, 0, 25))], "position": [(0, (0, 0, 0)), (1.3, (0, -20, 0))]}}, loop="hold")


@mob("em_forge_wither", 1.0)
def forge_wither(m):
    """Wither as a three-headed forge: dark iron skull heads, a spine of pistons and a glowing furnace chest."""
    root_body(m, 12)
    m.box("body", -1.4, 6, -1.4, 1.4, 34, 1.4, "wither_dark")                                   # spine
    for i, y in enumerate((16, 21, 26)):
        m.box("body", -8, y, -1, 8, y + 1.6, 1, "steel")                                       # ribs
        m.box("body", -7, y + 1.6, -1.4, 7, y + 2.2, 1.4, "wither_dark")
    m.box("body", -3, 17, -2.2, 3, 22, 2.4, "magma")
    m.box("body", -2, 18, -2.5, 2, 21, -2.2, "lava")
    m.box("body", -1.2, 6, -1.2, 1.2, 14, 6, "wither_dark")                                     # tail
    m.box("body", -1.2, 6, 5.6, 1.2, 8, 9, "brass")
    for name, x, y in (("h_head", 0, 34), ("head_l", 11, 31), ("head_r", -11, 31)):
        m.bone(name, (x, y, 0), "body")
        s = 1.0 if name == "h_head" else 0.75
        m.box(name, x - 4 * s, y, -4 * s, x + 4 * s, y + 8 * s, 4 * s, "wither_dark")
        m.box(name, x - 4.2 * s, y + 3.4 * s, -4.2 * s, x + 4.2 * s, y + 4.6 * s, 4.2 * s, "brass")
        m.box(name, x - 3 * s, y + 4.6 * s, -4.3 * s, x - 0.8 * s, y + 6.2 * s, -4.1 * s, "lava")
        m.box(name, x + 0.8 * s, y + 4.6 * s, -4.3 * s, x + 3 * s, y + 6.2 * s, -4.1 * s, "lava")
        m.box(name, x - 2 * s, y + 0.6 * s, -4.3 * s, x + 2 * s, y + 1.6 * s, -4.1 * s, "soot")
        m.box(name, x - 1 * s, y + 8 * s, -1 * s, x + 1 * s, y + 11 * s, 1 * s, "steel")
        if name != "h_head":
            m.box("body", x * 0.5 - 0.6, y - 6, -0.6, x * 0.5 + 0.6, y, 0.6, "wither_dark")
    tracks = {"head_l": {"rotation": mk.wave(8, 3.0, 9, 0, axis=1)}, "head_r": {"rotation": mk.wave(8, 3.0, -9, 0, axis=1)},
              "h_head": {"rotation": mk.wave(8, 3.0, 6, 1, axis=1)},
              "body": {"position": [(0, (0, 0, 0)), (1.5, (0, 1.2, 0)), (3.0, (0, 0, 0))]}}
    m.anim("idle", 3.0, tracks)
    m.anim("walk", 3.0, tracks)
    m.anim("attack", 0.8, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.8, (0, 0, 0))]},
                           "head_l": {"rotation": [(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.8, (0, 0, 0))]},
                           "head_r": {"rotation": [(0, (0, 0, 0)), (0.3, (-25, 0, 0)), (0.8, (0, 0, 0))]}}, loop="once")
    fall(m)
