"""Extra models for the regular elites (mobproperties) whose entity type has no boss model yet and that are enabled:
creeper, goat, hoglin, llama, bee, phantom, piglin brute. Same conventions as elite_beasts.py (head at -Z, bones
root > body > h_head, leg_fl/fr/bl/br, tail). Registered in MOBS."""
import elite_beasts as eb
import make_elite as me
from elite_beasts import quad_anims, quad_legs, root_body
from make_elite import mk, gear, goggles, boiler

MOBS = {}


def mob(name, size=1.0):
    def deco(fn):
        MOBS[name] = fn
        if size != 1.0:
            mk.SIZE[name[3:]] = size
        return fn
    return deco


@mob("em_steam_creeper")
def steam_creeper(m):
    """Creeper as a pressure bomb: green plated boiler body, a pressure gauge on the chest and a hissing fuse pipe."""
    root_body(m, 6)
    m.box("body", -4, 6, -2, 4, 18, 2, "moss")
    m.box("body", -4.2, 10, -2.2, 4.2, 11, 2.2, "brass")
    m.box("body", -4.2, 15, -2.2, 4.2, 16, 2.2, "brass")
    m.box("body", -2, 11.4, -2.4, 2, 14.4, -2.2, "steel")                              # gauge
    m.box("body", -1.4, 11.8, -2.6, 1.4, 14, -2.4, "white")
    m.box("body", -0.2, 12.2, -2.8, 0.2, 13.4, -2.6, "red_eye")
    m.bone("tail", (0, 18, 2), "body")                                                  # fuse pipe with a spark
    m.box("tail", -0.8, 18, 1, 0.8, 22, 2.6, "copper")
    m.box("tail", -1.2, 22, 0.6, 1.2, 23.2, 3, "brass")
    m.box("tail", -0.6, 23.2, 1.2, 0.6, 24.4, 2.4, "glow_orange")
    m.bone("h_head", (0, 18, 0), "body")
    m.box("h_head", -4, 18, -4, 4, 26, 4, "moss")
    m.box("h_head", -4.2, 21.6, -4.2, 4.2, 22.4, 4.2, "brass")                          # head band
    m.box("h_head", -3, 22.6, -4.3, -1, 24.6, -4.1, "black")                            # face
    m.box("h_head", 1, 22.6, -4.3, 3, 24.6, -4.1, "black")
    m.box("h_head", -1, 20.6, -4.3, 1, 22.4, -4.1, "black")
    m.box("h_head", -2, 18.6, -4.3, 2, 20.6, -4.1, "black")
    quad_legs(m, 2, -2, 2, 6, 3, 3, 4, "moss", "iron")
    # legs of a creeper are short, so quad_legs' small upper segment keeps the vanilla look
    m.anim("idle", 3.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (1, (0, 10, 0)), (2, (0, -10, 0)), (3, (0, 0, 0))]},
                         "tail": {"rotation": mk.wave(8, 0.6, 10, 0, axis=0)}})
    mk.quad_walk(m, 0.8, 22, 3, 10)
    m.anim("attack", 1.2, {"body": {"scale": [(0, (1, 1, 1)), (0.8, (1.35, 1.25, 1.35)), (1.2, (1.45, 1.3, 1.45))]},
                           "tail": {"rotation": mk.wave(8, 0.2, 25, 0, axis=0)}}, loop="once")
    m.anim("death", 0.8, {"root": {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, 90))]}}, loop="hold")


@mob("em_ram_goat")
def ram_goat(m):
    """Goat with a brass-capped battering horn pair and a piston in the neck."""
    root_body(m, 11)
    m.box("body", -4, 8, -7, 4, 18, 8, "fur_white")
    m.box("body", -4.2, 14, -7.2, 4.2, 18.4, -4, "fur_brown")
    m.box("body", -4.2, 12, -1, 4.2, 12.8, 1, "leather")
    m.bone("tail", (0, 17, 8), "body")
    m.box("tail", -0.8, 16, 8, 0.8, 19, 10, "fur_white")
    m.bone("h_head", (0, 17, -7), "body")
    m.box("h_head", -2.6, 15, -13, 2.6, 23, -6, "fur_white")
    m.box("h_head", -1.6, 15, -14.6, 1.6, 19, -12, "fur_brown")
    m.box("h_head", -0.6, 12.6, -11, 0.6, 15, -10, "fur_white")                         # beard
    for x in (-2.2, 2.2):
        m.box("h_head", x - 0.9, 23, -9, x + 0.9, 27, -7.4, "bone")                     # horns
        m.box("h_head", x - 1.2, 26.4, -10.6, x + 1.2, 28, -7, "brass")                 # brass caps
        m.box("h_head", x - 0.5, 19.6, -13.1, x + 0.5, 20.6, -12.9, "black")
    m.box("h_head", -2.8, 16.4, -9.4, 2.8, 17.2, -8.6, "copper")                        # collar pipe
    quad_legs(m, 2.2, -5, 5.5, 9, 5, 4, 2.2, "fur_white", "steel")
    quad_anims(m, 0.7, 28, -45)


@mob("em_forge_hoglin")
def forge_hoglin(m):
    """Hoglin as a forge beast: leather-brown hide, an iron shoulder plate with a smoking pipe, glowing tusks."""
    root_body(m, 18)
    m.box("body", -7, 12, -12, 7, 26, 12, "fur_brown")
    m.box("body", -7.2, 20, -8, 7.2, 26.4, 8, "leather")
    m.box("body", -7.4, 24, -9, 7.4, 28, 0, "iron")                                     # shoulder plate
    for z in (-7, -3):
        m.box("body", -7.6, 24.4, z - 0.4, 7.6, 27.6, z + 0.4, "brass")
    m.box("body", 2, 28, -6, 5, 34, -3, "steel")
    m.box("body", 1.6, 34, -6.4, 5.4, 35, -2.6, "soot")
    m.bone("tail", (0, 24, 12), "body")
    m.box("tail", -0.8, 20, 12, 0.8, 24, 14, "fur_brown")
    m.bone("h_head", (0, 22, -12), "body")
    m.box("h_head", -5, 16, -22, 5, 26, -11, "fur_brown")
    m.box("h_head", -3.5, 15.4, -24, 3.5, 20, -21, "snout")
    for x in (-3.4, 3.4):
        m.box("h_head", x - 0.9, 16, -24, x + 0.9, 24, -22.4, "tusk")
        m.box("h_head", x - 0.9, 23.2, -24, x + 0.9, 24, -22.4, "glow_orange")          # hot tips
        m.box("h_head", x - 0.8, 26, -19, x + 0.8, 30, -17, "fur_dark")
        m.box("h_head", x - 0.7, 23.4, -22.2, x + 0.7, 24.6, -22, "black")
    m.box("h_head", -1.4, 25.6, -18, 1.4, 27.4, -13, "fur_dark")
    quad_legs(m, 4.6, -8, 8, 13, 8, 5, 4.4, "fur_brown", "iron")
    quad_anims(m, 0.95, 24, -40)


@mob("em_cargo_llama")
def cargo_llama(m):
    """Llama as a freight carrier: woven blanket, copper pack-cases with a gauge and a brass bell on the collar."""
    root_body(m, 22)
    m.box("body", -4.5, 14, -9, 4.5, 28, 9, "fur_white")
    m.box("body", -4.8, 24, -8, 4.8, 28.4, 7, "coat_red")                                # blanket
    m.box("body", -4.8, 23, -8, 4.8, 24, 7, "brass")
    for x in (-6.6, 6.6):
        m.box("body", x - 1.8, 19, -5, x + 1.8, 27, 5, "copper")                          # side cases
        m.box("body", x - 2, 22, -5.2, x + 2, 23, 5.2, "brass")
    gear(m, "body", 8.6, 23.4, 0, 1.8, "steel", front=False)
    m.bone("tail", (0, 26, 9), "body")
    m.box("tail", -0.8, 24, 9, 0.8, 28, 11, "fur_white")
    m.bone("h_head", (0, 28, -8), "body")
    m.box("h_head", -2.2, 26, -11, 2.2, 40, -6, "fur_white")                              # long neck
    m.box("h_head", -2.6, 38, -14, 2.6, 44, -6, "fur_white")                              # head
    m.box("h_head", -1.8, 38, -15.4, 1.8, 41.4, -13.6, "fur_brown")
    for x in (-1.8, 1.8):
        m.box("h_head", x - 0.7, 44, -9.6, x + 0.7, 47.6, -8.2, "fur_white")              # ears
        m.box("h_head", x - 0.5, 41.4, -13.7, x + 0.5, 42.6, -13.5, "black")
    m.box("h_head", -2.8, 33, -9, 2.8, 34, -5, "leather")                                 # collar
    m.box("h_head", -0.8, 31.4, -9.4, 0.8, 33, -8, "brass")                               # bell
    quad_legs(m, 3, -6, 6, 15, 9, 6, 3, "fur_white", "steel")
    quad_anims(m, 0.9, 26, -25)


@mob("em_brass_bee")
def brass_bee(m):
    """Bee as a brass drone: a striped brass/soot body, glass wings and a copper stinger."""
    root_body(m, 6)
    m.box("body", -2.6, 3, -4, 2.6, 9, 4, "gold")
    for z in (-1.6, 1.6):
        m.box("body", -2.8, 3, z - 0.7, 2.8, 9.2, z + 0.7, "soot")
    m.box("body", -1.4, 9, -0.6, 1.4, 10, 0.6, "brass")
    m.bone("tail", (0, 5, 4), "body")
    m.box("tail", -0.5, 4.6, 4, 0.5, 5.6, 7, "copper")
    m.bone("h_head", (0, 6, -4), "body")
    m.box("h_head", -2.2, 3.6, -7.6, 2.2, 8.4, -3.6, "gold")
    m.box("h_head", -2, 5.4, -7.8, -0.4, 7.2, -7.6, "glow_orange")                        # lens eyes
    m.box("h_head", 0.4, 5.4, -7.8, 2, 7.2, -7.6, "glow_orange")
    for x in (-1, 1):
        m.box("h_head", x - 0.2, 8.4, -7, x + 0.2, 11, -6.6, "brass")                     # antennae
    for side, x, s in (("wing_l", 2.2, 1), ("wing_r", -2.2, -1)):
        m.bone(side, (x, 9, 0), "body")
        m.box(side, x, 9, -3, x + s * 7, 9.4, 2, "glass_blue")
    m.box("body", -0.6, 1.4, -1, 0.6, 3, 1, "brass")
    m.box("body", -0.6, 0.4, 2, 0.6, 3, 3, "brass")
    flap = {"wing_l": {"rotation": mk.wave(8, 0.2, 30, 0, axis=2)}, "wing_r": {"rotation": mk.wave(8, 0.2, -30, 0, axis=2)}}
    m.anim("idle", 1.0, dict(flap, body={"position": [(0, (0, 0, 0)), (0.5, (0, 0.6, 0)), (1.0, (0, 0, 0))]}))
    m.anim("walk", 1.0, dict(flap, body={"position": [(0, (0, 0, 0)), (0.5, (0, 0.6, 0)), (1.0, (0, 0, 0))]}))
    m.anim("attack", 0.5, {"body": {"position": [(0, (0, 0, 0)), (0.2, (0, 0, -3)), (0.5, (0, 0, 0))],
                                    "rotation": [(0, (0, 0, 0)), (0.2, (25, 0, 0)), (0.5, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.6, {"root": {"rotation": [(0, (0, 0, 0)), (0.5, (0, 0, 180))], "position": [(0, (0, 0, 0)), (0.5, (0, -5, 0))]}}, loop="hold")


@mob("em_gas_phantom", 1.0)
def gas_phantom(m):
    """Phantom as a gas-lamp bat-kite: canvas wings on brass ribs, a glowing blue gas envelope body and a long pipe tail."""
    root_body(m, 4)
    m.box("body", -3, 2, -8, 3, 7, 8, "arcane")
    m.box("body", -3.2, 3.6, -5, 3.2, 4.4, 5, "brass")
    m.box("body", -2, 7, -4, 2, 8.6, 4, "glass_blue")
    m.bone("tail", (0, 4.5, 8), "body")
    m.box("tail", -1, 3.6, 8, 1, 5.4, 18, "end_purple")
    m.box("tail", -1.4, 3.2, 17, 1.4, 5.8, 20, "glow_orange")
    m.bone("h_head", (0, 5, -8), "body")
    m.box("h_head", -3, 2, -13, 3, 7.5, -8, "arcane")
    m.box("h_head", -2.4, 5, -13.2, -0.8, 6.4, -13, "sea_lantern")
    m.box("h_head", 0.8, 5, -13.2, 2.4, 6.4, -13, "sea_lantern")
    for side, x, s in (("wing_l", 3, 1), ("wing_r", -3, -1)):
        m.bone(side, (x, 5, -2), "body")
        m.box(side, x, 4.6, -4, x + s * 10, 5.4, 4, "end_purple")
        m.box(side, x + s * 10, 4.2, -3, x + s * 20, 5, 3, "arcane")
        m.box(side, x + s * 0.5, 4.8, -4.4, x + s * 20, 5.6, -3.8, "brass")                 # leading rib
    flap = {"wing_l": {"rotation": mk.wave(8, 1.4, 18, 0, axis=2)}, "wing_r": {"rotation": mk.wave(8, 1.4, -18, 0, axis=2)}}
    m.anim("idle", 1.4, dict(flap, body={"position": [(0, (0, 0, 0)), (0.7, (0, 1, 0)), (1.4, (0, 0, 0))]}))
    m.anim("walk", 1.0, {"wing_l": {"rotation": mk.wave(8, 1.0, 22, 0, axis=2)}, "wing_r": {"rotation": mk.wave(8, 1.0, -22, 0, axis=2)}})
    m.anim("attack", 0.6, {"body": {"rotation": [(0, (0, 0, 0)), (0.25, (35, 0, 0)), (0.6, (0, 0, 0))],
                                    "position": [(0, (0, 0, 0)), (0.25, (0, -3, -4)), (0.6, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.8, {"root": {"rotation": [(0, (0, 0, 0)), (0.7, (80, 0, 20))], "position": [(0, (0, 0, 0)), (0.7, (0, -4, 0))]}}, loop="hold")


@mob("em_iron_brute")
def iron_brute(m):
    """Piglin brute in riveted iron plate with a spiked brass club."""
    from elite_bipeds import piglin_base
    from make_elite import humanoid_anims
    piglin_base(m, True, False)
    m.box("body", -4.5, 12, -2.5, 4.5, 24.4, 2.5, "iron")
    for y in (14, 18, 22):
        m.box("body", -4.7, y, -2.7, 4.7, y + 0.7, 2.7, "brass")
    m.box("h_head", -5.2, 30.4, -4.4, 5.2, 32.6, 4.4, "iron")
    m.box("arm_r", -7.2, 9.8, -11, -4.8, 12, -3, "wood")                                 # club
    m.box("arm_r", -8.4, 8.6, -16, -3.6, 13.2, -9, "brass")
    for x, y, z in ((-9, 10.8, -12), (-3, 10.8, -12), (-6, 13.6, -12), (-6, 8, -12), (-6, 10.8, -16.6)):
        m.box("arm_r", x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5, "steel")
    humanoid_anims(m, arms_forward=0, walk_amp=30, arm_amp=18, attack="swing", period=0.9)
