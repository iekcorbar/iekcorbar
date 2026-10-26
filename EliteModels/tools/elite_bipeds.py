"""Humanoid EliteMobs models (own designs): drowned, stray, bogged, wither skeleton, piglins, vindicator, evoker,
illusioner, witch, iron golem, warden, enderman. Same body plan as make_elite (bones root > body > h_head, arm_l/arm_r,
leg_l/leg_r; front = -Z), steampunk details on top. Registered in MOBS (model name -> builder)."""
import make_elite as me
from make_elite import mk, skeleton, face, goggles, gear, pipe, boiler, humanoid_anims

MOBS = {}


def mob(name, size=1.0):
    def deco(fn):
        MOBS[name] = fn
        if size != 1.0:
            mk.SIZE[name[3:]] = size
        return fn
    return deco


def bony(m, bone, joint, ribs="bone_d"):
    """Skeleton body: ribs over a dark spine, joints in a different colour."""
    skeleton(m, bone, bone, bone, boots=bone + "_d")
    m.box("body", -3.2, 12, -1.2, 3.2, 24, 1.2, "soot")
    for y in (15, 17.2, 19.4, 21.6):
        m.box("body", -4.1, y, -2.1, 4.1, y + 1.2, 2.1, bone)
    m.box("body", -0.9, 12, -2.3, 0.9, 24, -1.5, bone + "_d")
    for b, x in (("arm_l", 6), ("arm_r", -6)):
        m.box(b, x - 2.4, 20.8, -2.4, x + 2.4, 23.2, 2.4, joint)
        m.box(b, x - 2.2, 15.6, -2.2, x + 2.2, 16.8, 2.2, joint)
    for b, x in (("leg_l", 2), ("leg_r", -2)):
        m.box(b, x - 2.3, 6.4, -2.3, x + 2.3, 7.6, 2.3, joint)


def hollow_eyes(m, glow):
    m.box("h_head", -2.8, 27.5, -4.25, -1.2, 29.2, -4.1, "soot")
    m.box("h_head", 1.2, 27.5, -4.25, 2.8, 29.2, -4.1, "soot")
    m.box("h_head", -2.2, 27.9, -4.4, -1.6, 28.6, -4.25, glow)
    m.box("h_head", 1.6, 27.9, -4.4, 2.2, 28.6, -4.25, glow)
    m.box("h_head", -2, 25.2, -4.15, 2, 26.2, -4.05, "soot")


# ------------------------------------------------------------------ undead

@mob("em_diver_drowned")
def diver_drowned(m):
    """Drowned in a brass diving helmet with an air hose to a tank on the back, lead boots."""
    skeleton(m, "drown_skin", "prismarine", "prismarine_d", boots="steel")
    face(m, eye="sea_lantern", mouth="soot", glow=False)
    m.box("h_head", -5.2, 23.4, -5.2, 5.2, 33.4, 5.2, "brass")                      # helmet sphere (box)
    m.box("h_head", -5.4, 27.6, -5.4, 5.4, 28.8, 5.4, "brass_d")                    # seam
    m.box("h_head", -3.0, 25.4, -5.5, 3.0, 31.2, -5.2, "brass_l")                   # window frame
    m.box("h_head", -2.4, 26.0, -5.7, 2.4, 30.6, -5.5, "glass_blue")
    for x, y in ((-4.4, 24.4), (4.4, 24.4), (-4.4, 32.4), (4.4, 32.4)):
        m.box("h_head", x - 0.4, y - 0.4, -5.5, x + 0.4, y + 0.4, -5.3, "copper")   # rivets
    m.box("h_head", 4.8, 27, -1, 6.2, 29, 1, "iron")                                # hose socket
    m.box("body", -3.6, 13, 2.2, 3.6, 24, 6.4, "copper")                            # air tank
    for y in (15, 20):
        m.box("body", -3.8, y, 2.1, 3.8, y + 1, 6.5, "brass")
    m.box("body", -4.6, 22, -2.2, 4.6, 24.5, 2.2, "brass_d")                        # collar
    m.box("arm_l", 3.6, 21, -2.5, 8.4, 24, 2.5, "steel")                            # pauldrons
    m.box("arm_r", -8.4, 21, -2.5, -3.6, 24, 2.5, "steel")
    for b, x in (("leg_l", 2), ("leg_r", -2)):
        m.box(b, x - 2.5, 0, -2.8, x + 2.5, 2.4, 2.4, "steel_d")                    # lead boots
    m.box("arm_r", -7.5, 9.5, -9.5, -4.5, 12.5, -2, "steel")                        # trident shaft
    m.box("arm_r", -6.6, 10.4, -16, -5.4, 11.6, -9.5, "brass")
    for x in (-7.2, -6.0, -4.8):
        m.box("arm_r", x - 0.3, 9.8, -19, x + 0.3, 12.2, -16, "iron")
    humanoid_anims(m, arms_forward=60, walk_amp=22, arm_amp=12, attack="swing", period=1.0)


@mob("em_frost_stray")
def frost_stray(m):
    """Stray in a frayed hood and scarf, icicle crown and an ice-blue glowing core."""
    bony(m, "bone", "frost")
    hollow_eyes(m, "glass_blue")
    m.box("h_head", -4.6, 24, -4.6, 4.6, 33, 4.6, "frost_d")                        # hood
    m.box("h_head", -3.6, 25, -4.7, 3.6, 31.6, -4.5, "soot")                        # hood opening
    hollow_eyes(m, "glass_blue")
    for x, h in ((-3, 3), (-1, 5), (1.5, 4), (3.2, 2.5)):
        m.box("h_head", x - 0.5, 33, -1, x + 0.5, 33 + h, 0, "frost_l")             # icicles
    m.box("body", -4.3, 22.4, -2.3, 4.3, 24.4, 2.3, "frost")                        # scarf
    m.box("body", -1.2, 15, -2.5, 1.2, 21, -2.1, "glass_blue")                      # frozen core
    m.box("body", -4.3, 3, -2.4, 4.3, 12, 2.4, "frost_d")                           # ragged cloak skirt
    for x in (-3, 0, 3):
        m.box("body", x - 0.8, 1, -2.5, x + 0.8, 3, 2.5, "frost_d")
    m.box("arm_r", -7.5, 9.5, -8, -4.5, 12.5, -2, "wood")                           # bow
    m.box("arm_r", -7, 5.5, -9, -5, 16, -8, "frost")
    m.box("arm_r", -6.1, 5.5, -9.2, -5.9, 16, -2.5, "white")
    humanoid_anims(m, arms_forward=0, walk_amp=28, arm_amp=22, attack="shoot", period=0.75)


@mob("em_bog_bogged")
def bog_bogged(m):
    """Bogged with mushrooms sprouting from the head and back, vines and a lantern jar."""
    bony(m, "moss", "bog")
    hollow_eyes(m, "glow_orange")
    for x, z, c in ((-2.5, -1.5, "scarf"), (1.5, 0.5, "leather"), (-0.5, 2.0, "scarf")):
        m.box("h_head", x - 0.5, 32, z - 0.5, x + 0.5, 34.4, z + 0.5, "cloth")        # mushroom stems
        m.box("h_head", x - 2.0, 34.4, z - 2.0, x + 2.0, 35.6, z + 2.0, c)           # caps
        m.box("h_head", x - 0.8, 35.6, z - 0.8, x + 0.8, 35.9, z + 0.8, "cloth")
    for x in (-3.5, 0.5, 3.8):
        m.box("body", x - 0.5, 14, 2.1, x + 0.5, 24, 2.6, "moss_d")                  # hanging vines
    m.box("body", -3.5, 17, 2.2, 3.5, 22, 3.6, "moss_d")
    m.box("body", -3, 20, 3.6, 3, 24, 5.4, "scarf")                                  # back mushroom
    m.box("body", -4.1, 13, -2.1, 4.1, 14.4, 2.1, "leather")                         # belt
    m.box("body", 1.6, 9.5, -3.4, 3.6, 13.3, -1.6, "glass_blue")                      # jar
    m.box("body", 2.0, 10, -3.0, 3.2, 12.8, -2.0, "glow_orange")
    m.box("arm_r", -7.5, 9.5, -8, -4.5, 12.5, -2, "wood")
    m.box("arm_r", -7, 5.5, -9, -5, 16, -8, "moss")
    humanoid_anims(m, arms_forward=0, walk_amp=24, arm_amp=18, attack="shoot", period=0.9)


@mob("em_ember_wither_skeleton", 1.18)
def ember_wither_skeleton(m):
    """Charred wither skeleton: nether-brick armour, lava core in the chest, a huge cinder cleaver."""
    bony(m, "wither_bone", "nether_brick")
    hollow_eyes(m, "lava")
    m.box("body", -4.4, 18, -2.4, 4.4, 24, 2.4, "nether_brick")                      # breastplate
    m.box("body", -2.0, 18.6, -2.6, 2.0, 22.6, -2.4, "lava")                         # lava core window
    m.box("body", -1.2, 19.4, -2.8, 1.2, 21.8, -2.6, "lava_l")
    m.box("body", -4.5, 12, -2.3, 4.5, 13.4, 2.3, "brass_d")                         # belt
    m.box("h_head", -4.3, 30.6, -4.3, 4.3, 32.6, 4.3, "nether_brick")                # helm band
    for x in (-2.5, 0, 2.5):
        m.box("h_head", x - 0.5, 32.6, -4.2, x + 0.5, 35.4, 1.0, "nether_brick_d")   # crest spikes
    m.box("arm_l", 3.5, 20.6, -3, 8.9, 24.4, 3, "nether_brick")                      # pauldrons
    m.box("arm_r", -8.9, 20.6, -3, -3.5, 24.4, 3, "nether_brick")
    m.box("arm_r", -7.2, 9.4, -4, -4.8, 12.6, 2, "leather_dark")                     # grip
    m.box("arm_r", -6.6, 8, -22, -5.4, 14, -4, "wither_dark")                        # cleaver blade
    m.box("arm_r", -6.7, 8, -22.2, -5.3, 8.8, -9, "lava")                            # glowing edge
    humanoid_anims(m, arms_forward=0, walk_amp=26, arm_amp=22, attack="swing", period=0.8)


def piglin_base(m, gold_armour, rank):
    skeleton(m, "piglin_skin", "leather", "nether_brick", boots="leather_dark", arms_skin="piglin_skin")
    face(m, eye="black", mouth="soot", brow="piglin_skin_d")
    m.box("h_head", -5, 24, -4.2, 5, 32, 4.2, "piglin_skin")                         # wider piglin head
    m.box("h_head", -2.2, 24.6, -6.2, 2.2, 27.6, -4.2, "snout")                      # snout
    m.box("h_head", -1.4, 25.4, -6.4, -0.6, 26.6, -6.2, "soot")
    m.box("h_head", 0.6, 25.4, -6.4, 1.4, 26.6, -6.2, "soot")
    m.box("h_head", -3.4, 24.4, -5.2, -2.6, 26.8, -4.3, "tusk")                      # tusks
    m.box("h_head", 2.6, 24.4, -5.2, 3.4, 26.8, -4.3, "tusk")
    m.box("h_head", 5, 26, -1.5, 8, 31, 1.5, "piglin_skin_d")                        # ears
    m.box("h_head", -8, 26, -1.5, -5, 31, 1.5, "piglin_skin_d")
    face(m, eye="black", mouth="snout", brow="piglin_skin_d")
    if gold_armour:
        m.box("body", -4.4, 17, -2.4, 4.4, 24, 2.4, "gold")                          # gold chest plate
        m.box("body", -4.4, 12.4, -2.3, 4.4, 14, 2.3, "gold_d")
        m.box("arm_l", 3.7, 20.6, -2.8, 8.3, 23.6, 2.8, "gold")
        m.box("arm_r", -8.3, 20.6, -2.8, -3.7, 23.6, 2.8, "gold")
        m.box("h_head", -5.2, 30.8, -4.4, 5.2, 32.4, 4.4, "gold")                    # helmet band
    if rank:
        goggles(m, lens="glow_orange", y=30.2, frame="gold")
        boiler(m, "body")


@mob("em_gilded_zombified_piglin")
def gilded_zombified_piglin(m):
    """Zombified piglin in rotting gold armour, gold chain and a brass-capped club."""
    piglin_base(m, True, False)
    m.box("h_head", -5.1, 29, -4.3, 5.1, 32.1, 4.3, "zombie_skin_d")                 # rotten patch on the scalp
    m.box("body", -3.4, 15, -2.6, -0.8, 18.4, -2.3, "zombie_skin")
    m.box("arm_r", -7.4, 9.6, -10, -4.6, 12.4, -2, "wood")
    m.box("arm_r", -8.0, 9.0, -14.5, -4.0, 13.0, -10, "gold")
    m.box("arm_r", -7.4, 9.6, -15.5, -4.6, 12.4, -14.5, "gold_d")
    humanoid_anims(m, arms_forward=75, walk_amp=28, arm_amp=12, attack="slam", period=0.9)


@mob("em_brass_piglin")
def brass_piglin(m):
    """Piglin foreman: goggles, a steam pack and a golden crossbow."""
    piglin_base(m, True, True)
    m.box("arm_r", -7.2, 10.2, -9.0, -4.8, 12.6, -2.0, "wood")
    m.box("arm_r", -9.5, 11.0, -8.6, -2.5, 12.0, -7.4, "gold")
    m.box("arm_r", -7.0, 12.6, -8.5, -5.0, 13.8, -3.0, "copper")
    humanoid_anims(m, arms_forward=0, walk_amp=24, arm_amp=16, attack="shoot", period=0.85)


# ------------------------------------------------------------------ illagers & witch

def illager(m, coat, skin="pillager_skin"):
    skeleton(m, skin, coat, coat + "_d", boots="leather_dark", arms_skin=skin)
    face(m, eye="black", mouth="soot", brow="soot")
    m.box("h_head", -1, 24.4, -5.6, 1, 27.8, -4.0, skin + "_d")                      # long nose
    m.box("h_head", -3.4, 29.6, -4.2, 3.4, 30.4, -4.05, "soot")                      # unibrow
    m.box("body", -4.3, 3, -2.3, 4.3, 12, 2.3, coat)                                 # long coat
    m.box("body", -4.3, 3, -2.3, 4.3, 4.2, 2.3, coat + "_d")
    m.box("body", -4.1, 12.4, -2.1, 4.1, 13.6, 2.1, "leather_dark")
    m.box("body", -0.9, 12.2, -2.35, 0.9, 13.8, -2.15, "brass")


@mob("em_steam_vindicator")
def steam_vindicator(m):
    """Vindicator in a green coat, iron jaw guard and a boiler-powered hatchet."""
    illager(m, "robe_green")
    m.box("h_head", -4.2, 24, -4.3, 4.2, 26.4, 4.3, "steel")                         # jaw guard
    m.box("h_head", -3.4, 24.2, -4.5, 3.4, 25.2, -4.3, "soot")
    m.box("h_head", -4.3, 30.6, -4.3, 4.3, 31.8, 4.3, "steel_d")                     # brow plate
    m.box("body", -4.4, 20, -2.4, 4.4, 24, 2.4, "steel")                             # chest plate
    for x in (-2.8, 0, 2.8):
        m.box("body", x - 0.4, 20.4, -2.6, x + 0.4, 23.6, -2.4, "brass")
    boiler(m, "body", 2.2)
    m.box("arm_r", -7.2, 9.6, -3, -4.8, 12.4, 2, "leather_dark")
    m.box("arm_r", -6.8, 9.6, -14, -5.2, 12.4, -3, "wood")                           # haft
    m.box("arm_r", -8.8, 8.4, -17, -3.2, 15, -11, "steel")                           # axe head
    m.box("arm_r", -9.0, 8.6, -17.4, -8.2, 14.8, -11.4, "iron_l")                     # edge
    m.box("arm_r", -6.4, 12.4, -12, -5.6, 16, -10, "copper")                         # steam pipe
    humanoid_anims(m, arms_forward=0, walk_amp=30, arm_amp=22, attack="swing", period=0.75)


@mob("em_arcane_evoker")
def arcane_evoker(m):
    """Evoker in long purple robes, a brass-banded tall hat and an orbiting arcane engine."""
    illager(m, "robe_purple")
    m.box("body", -4.6, 0, -2.6, 4.6, 12, 2.6, "robe_purple")                        # floor-length robe
    m.box("body", -4.6, 0, -2.6, 4.6, 1.4, 2.6, "brass")                             # hem trim
    m.box("h_head", -4.6, 31.6, -4.6, 4.6, 33, 4.6, "robe_purple_d")                 # hat brim
    m.box("h_head", -3.4, 33, -3.4, 3.4, 36, 3.4, "robe_purple")
    m.box("h_head", -2.2, 36, -2.2, 2.2, 39, 2.2, "robe_purple")
    m.box("h_head", -3.5, 33.2, -3.6, 3.5, 34.2, 3.6, "brass")
    gear(m, "h_head", 0, 34.8, -3.6, 1.2)
    m.box("h_head", -0.8, 39, -0.8, 0.8, 40.2, 0.8, "arcane")
    for b, x in (("arm_l", 6), ("arm_r", -6)):
        m.box(b, x - 2.4, 8, -2.4, x + 2.4, 13, 2.4, "robe_purple_d")                # sleeves
        m.box(b, x - 2.5, 12.6, -2.5, x + 2.5, 13.4, 2.5, "brass")
    m.box("arm_r", -7.4, 6.5, -5.5, -4.6, 9.5, -2.7, "arcane_l")                      # glowing orb in hand
    m.box("arm_r", -6.6, 7.2, -4.7, -5.4, 8.8, -3.5, "white")
    humanoid_anims(m, arms_forward=40, walk_amp=18, arm_amp=10, attack="slam", period=1.0)


@mob("em_clockwork_illusioner")
def clockwork_illusioner(m):
    """Illusioner as a stage magician: top hat with a clock face, half mask, cards and a cane."""
    illager(m, "robe_gray")
    m.box("h_head", -4.6, 31.8, -4.6, 4.6, 32.8, 4.6, "hat_dark")                    # brim
    m.box("h_head", -3.4, 32.8, -3.4, 3.4, 38.5, 3.4, "hat_dark")                    # top hat
    m.box("h_head", -3.5, 33.2, -3.5, 3.5, 34.6, 3.5, "coat_red")
    m.box("h_head", -1.6, 35.2, -3.7, 1.6, 37.8, -3.4, "white")                      # clock face
    m.box("h_head", -0.15, 35.6, -3.8, 0.15, 37.2, -3.7, "black")
    m.box("h_head", -0.9, 36.3, -3.8, 0.1, 36.6, -3.7, "black")
    m.box("h_head", -4.2, 26, -4.3, 0.4, 31, -4.1, "white")                          # half mask
    m.box("h_head", -3.2, 28, -4.4, -1.2, 29, -4.3, "black")
    m.box("body", -4.4, 18, -2.4, 4.4, 24, 2.4, "hat_dark")                           # tailcoat
    m.box("body", -4.4, 3, 2.1, 4.4, 12, 2.6, "hat_dark")                             # tails
    m.box("body", -1.4, 19, -2.5, 1.4, 23.6, -2.4, "white")                           # shirt
    m.box("body", -1.2, 22, -2.55, 1.2, 23.6, -2.5, "coat_red")                       # bow tie
    m.box("arm_r", -6.6, 9.6, -16, -5.4, 12.4, -3, "black")                           # cane
    m.box("arm_r", -7.6, 9.2, -17, -4.4, 13, -15, "brass")
    m.box("arm_l", 4.8, 8.6, -4.5, 7.2, 12, -4.0, "white")                            # fan of cards
    m.box("arm_l", 4.0, 8.6, -4.0, 6.4, 12, -3.5, "coat_red")
    humanoid_anims(m, arms_forward=0, walk_amp=24, arm_amp=16, attack="swing", period=0.85)


@mob("em_brew_witch")
def brew_witch(m):
    """Witch with a crooked hat, a bubbling distillery on her back and a flask in each hand."""
    skeleton(m, "moss", "robe_purple", "robe_purple_d", boots="leather_dark", arms_skin="moss")
    face(m, eye="black", mouth="soot")
    m.box("h_head", -1, 24.4, -5.6, 1, 27.6, -4.0, "moss_d")                          # nose
    m.box("h_head", -0.6, 25.6, -6.0, 0.4, 26.4, -5.6, "bog")                         # wart
    m.box("h_head", -5.4, 31.8, -5.4, 5.4, 33, 5.4, "hat_dark")
    m.box("h_head", -3.8, 33, -3.8, 3.8, 36.5, 3.8, "hat_dark")
    m.box("h_head", -2.6, 36.5, -2.2, 2.6, 40, 3.0, "hat_dark")
    m.box("h_head", -1.2, 40, 0, 1.2, 43, 3.6, "hat_dark")
    m.box("h_head", -3.9, 33.2, -3.9, 3.9, 34.4, 3.9, "brass")
    m.box("h_head", -1.2, 33.2, -4.0, 1.2, 34.4, -3.9, "gold")
    m.box("body", -4.5, 0, -2.5, 4.5, 12, 2.5, "robe_purple")                         # robe skirt
    m.box("body", -4.5, 0, -2.5, 4.5, 1.2, 2.5, "robe_purple_d")
    m.box("body", -4.2, 13, -2.2, 4.2, 14.4, 2.2, "leather")
    for x in (-2.8, 0, 2.8):
        m.box("body", x - 0.6, 11.6, -2.6, x + 0.6, 13.4, -2.2, "glass_blue")        # potion belt
    m.box("body", -3.6, 13, 2.2, 3.6, 22, 5.6, "copper")                              # still
    m.box("body", -1.4, 22, 2.8, 1.4, 27, 5.0, "glass_blue")
    m.box("body", -0.8, 22.4, 3.4, 0.8, 24.5, 4.4, "slime_green")
    pipe(m, "body", 1.4, 27, 3.4, 5.6, 28, 4.4, "brass")
    m.box("body", 4.6, 22, 3.0, 6.4, 28, 4.8, "glass_blue")
    for b, x in (("arm_r", -6), ("arm_l", 6)):
        m.box(b, x - 1.6, 9.2, -6.2, x + 1.6, 12.4, -3.0, "glass_blue")               # flask
        m.box(b, x - 1.1, 9.8, -5.7, x + 1.1, 11.2, -3.5, "slime_green")
        m.box(b, x - 0.6, 12.4, -5.2, x + 0.6, 13.6, -4.0, "wood")
    humanoid_anims(m, arms_forward=40, walk_amp=22, arm_amp=12, attack="shoot", period=0.95)


# ------------------------------------------------------------------ big humanoids

def big_anims(m, period=1.2, attack_arms=-150):
    m.anim("idle", 4.0, {"body": {"position": [(0, (0, 0, 0)), (2, (0, 0.5, 0)), (4, (0, 0, 0))]},
                         "h_head": {"rotation": [(0, (0, 0, 0)), (1.3, (0, 10, 0)), (2.6, (0, -10, 0)), (4, (0, 0, 0))]}})
    m.anim("walk", period, {"leg_l": {"rotation": mk.wave(8, period, 26)}, "leg_r": {"rotation": mk.wave(8, period, -26)},
                            "arm_l": {"rotation": mk.wave(8, period, -20)}, "arm_r": {"rotation": mk.wave(8, period, 20)},
                            "body": {"rotation": mk.wave(8, period, 3, 0, axis=1)}})
    m.anim("attack", 0.9, {"arm_l": {"rotation": [(0, (0, 0, 0)), (0.3, (attack_arms, 0, 0)), (0.55, (20, 0, 0)), (0.9, (0, 0, 0))]},
                           "arm_r": {"rotation": [(0, (0, 0, 0)), (0.3, (attack_arms, 0, 0)), (0.55, (20, 0, 0)), (0.9, (0, 0, 0))]},
                           "body": {"rotation": [(0, (0, 0, 0)), (0.3, (-8, 0, 0)), (0.55, (10, 0, 0)), (0.9, (0, 0, 0))]}}, loop="once")
    m.anim("death", 1.2, {"root": {"rotation": [(0, (0, 0, 0)), (0.8, (80, 0, 0)), (1.2, (90, 0, 0))],
                                   "position": [(0, (0, 0, 0)), (1.2, (0, 0, 8))]}}, loop="hold")


@mob("em_boiler_golem")
def boiler_golem(m):
    """Iron golem as a walking boiler: riveted plates, a furnace door in the belly, two chimneys, vines of pipe."""
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 20, 0), "root")
    m.box("body", -9, 20, -5.5, 9, 36, 5.5, "iron")                                  # torso
    m.box("body", -7, 14, -4.5, 7, 20, 4.5, "iron_d")                                # waist
    m.box("body", -9.2, 28, -5.7, 9.2, 29.2, 5.7, "brass")
    m.box("body", -9.2, 22, -5.7, 9.2, 23.2, 5.7, "brass")
    m.box("body", -4, 22.6, -5.9, 4, 28.4, -5.5, "soot")                              # furnace door
    m.box("body", -3, 23.2, -6.2, 3, 27.4, -5.9, "iron_d")
    m.box("body", -2.2, 24.0, -6.3, 2.2, 26.4, -6.2, "glow_orange")
    for x in (-7.5, 7.5):
        m.box("body", x - 1.6, 36, -2, x + 1.6, 44, 1.6, "steel")                     # chimneys
        m.box("body", x - 2.0, 44, -2.4, x + 2.0, 45.2, 2.0, "soot")
        m.box("body", x - 2.1, 38, -2.5, x + 2.1, 39, 2.1, "brass")
    for x, y in ((-7.5, 33), (7.5, 33), (-7.5, 25), (7.5, 25)):
        m.box("body", x - 0.5, y - 0.5, -5.9, x + 0.5, y + 0.5, -5.7, "brass_l")      # rivets
    pipe(m, "body", -9.4, 18, 0, -8.4, 36, 2.4, "copper")
    pipe(m, "body", 8.4, 18, 0, 9.4, 36, 2.4, "copper")
    m.box("body", -3, 14, 5.4, 3, 34, 8.4, "copper")                                   # back tank
    m.box("body", -3.4, 30, 5.2, 3.4, 31.2, 8.6, "brass")
    m.bone("h_head", (0, 36, 0), "body")
    m.box("h_head", -4, 36, -4.5, 4, 46, 4.5, "iron")
    m.box("h_head", -1.2, 36.5, -7, 1.2, 41.5, -4.5, "iron_d")                        # nose
    m.box("h_head", -3.2, 41.2, -4.7, -0.8, 42.6, -4.5, "glow_orange")                # eyes
    m.box("h_head", 0.8, 41.2, -4.7, 3.2, 42.6, -4.5, "glow_orange")
    m.box("h_head", -4.2, 44.6, -4.7, 4.2, 46.2, 4.7, "brass")
    for side, x in (("arm_l", 12), ("arm_r", -12)):
        m.bone(side, (x, 35, 0), "body")
        s = 1 if x > 0 else -1
        m.box(side, x - 3.2, 6, -3.2, x + 3.2, 35, 3.2, "iron")
        m.box(side, x - 3.5, 24, -3.5, x + 3.5, 25.2, 3.5, "brass")
        m.box(side, x - 4, 3, -4, x + 4, 12, 4, "iron_d")
        m.box(side, x + s * 3.2, 14, -1.2, x + s * 4.6, 32, 1.2, "copper")
    for side, x in (("leg_l", 4.8), ("leg_r", -4.8)):
        m.bone(side, (x, 14, 0), "body")
        m.box(side, x - 3.2, 0, -3.8, x + 3.2, 14, 3.8, "iron_d")
        m.box(side, x - 3.5, 7, -4, x + 3.5, 8, 4, "brass")
    big_anims(m, 1.4)


@mob("em_sculk_warden")
def sculk_warden(m):
    """Warden as a deepslate automaton with sculk-lit seams and a pulsing chest lens."""
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 18, 0), "root")
    m.box("body", -8, 18, -5, 8, 34, 5, "deepslate")
    m.box("body", -9.5, 28, -6, 9.5, 38, 6, "deepslate_d")                            # shoulder mass
    for y in (21, 24.5, 28):
        m.box("body", -6.5, y, -5.2, 6.5, y + 1.4, -4.9, "sculk")                      # ribs
    m.box("body", -2.4, 22, -5.6, 2.4, 27, -5.2, "sculk_glow")                         # chest lens
    m.box("body", -1.2, 23, -5.8, 1.2, 26, -5.6, "white")
    for x in (-6.5, 6.5):
        m.box("body", x - 1.4, 34, -2, x + 1.4, 39, 2, "sculk")                        # tendrils
        m.box("body", x - 0.8, 39, -1.4, x + 0.8, 41, 1.4, "sculk_glow")
    m.box("body", -4, 18, 5, 4, 32, 7, "sculk")                                        # spine growth
    m.bone("h_head", (0, 38, 0), "body")
    m.box("h_head", -5.5, 38, -4.5, 5.5, 46, 4.5, "deepslate")
    m.box("h_head", -4.5, 44.6, -4.7, 4.5, 46.4, 4.7, "deepslate_d")
    m.box("h_head", -4.2, 39.4, -5.0, 4.2, 40.6, -4.5, "soot")                         # mouth slit
    m.box("h_head", -2.2, 41.2, -4.8, -0.6, 42.2, -4.5, "sculk_glow")                   # eyes hidden: just slits
    m.box("h_head", 0.6, 41.2, -4.8, 2.2, 42.2, -4.5, "sculk_glow")
    for x in (-8.2, 8.2):
        m.box("h_head", x - 2.5, 42, -1.5, x + 2.5, 47, 1.5, "deepslate_d")            # ear plates
        m.box("h_head", x - 1.2, 43.4, -1.6, x + 1.2, 45.6, -1.4, "sculk_glow")
    for side, x in (("arm_l", 12), ("arm_r", -12)):
        m.bone(side, (x, 36, 0), "body")
        m.box(side, x - 3, 10, -3, x + 3, 36, 3, "deepslate")
        m.box(side, x - 3.6, 2, -3.6, x + 3.6, 10, 3.6, "deepslate_d")
        m.box(side, x - 3.2, 20, -3.2, x + 3.2, 21.2, 3.2, "sculk_glow")
    for side, x in (("leg_l", 4.5), ("leg_r", -4.5)):
        m.bone(side, (x, 18, 0), "body")
        m.box(side, x - 3.2, 0, -3.6, x + 3.2, 18, 3.6, "deepslate_d")
    big_anims(m, 1.5, -160)


@mob("em_brass_enderman")
def brass_enderman(m):
    """Tall thin enderman in a brass collar, with pipes down the arms and glowing purple lenses."""
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, 26, 0), "root")
    m.box("body", -4, 26, -2, 4, 38, 2, "end_black")
    m.box("body", -4.2, 36, -2.2, 4.2, 38, 2.2, "brass")                               # collar
    m.box("body", -4.1, 27, -2.1, 4.1, 28.2, 2.1, "brass_d")
    m.box("body", -1.5, 30, -2.4, 1.5, 34, -2.0, "iron")                                # gauge
    m.box("body", -0.8, 31, -2.6, 0.8, 33.2, -2.4, "end_eye")
    m.box("body", -3, 28, 2, 3, 36, 3.6, "copper")                                      # small coil on the back
    m.bone("h_head", (0, 38, 0), "body")
    m.box("h_head", -4, 38, -4, 4, 46, 4, "end_black")
    m.box("h_head", -4.1, 41.6, -4.2, 4.1, 42.4, 4.2, "end_black_d")
    m.box("h_head", -3.2, 41.6, -4.3, -0.8, 43.2, -4.1, "end_eye")                      # eyes
    m.box("h_head", 0.8, 41.6, -4.3, 3.2, 43.2, -4.1, "end_eye")
    m.box("h_head", -4.2, 44.6, -4.2, 4.2, 46.4, 4.2, "brass")                          # head band
    gear(m, "h_head", 0, 45.4, -4.2, 1.2)
    for side, x in (("arm_l", 6), ("arm_r", -6)):
        m.bone(side, (x, 36, 0), "body")
        s = 1 if x > 0 else -1
        m.box(side, x - 1, 6, -1, x + 1, 36, 1, "end_black")
        m.box(side, x - 1.3, 20, -1.3, x + 1.3, 21, 1.3, "brass")
        m.box(side, x + s * 1.0, 8, -0.5, x + s * 1.5, 34, 0.5, "copper")
        m.box(side, x - 1.2, 3, -1.2, x + 1.2, 6.4, 1.2, "steel")
    for side, x in (("leg_l", 2), ("leg_r", -2)):
        m.bone(side, (x, 26, 0), "body")
        m.box(side, x - 1, 0, -1, x + 1, 26, 1, "end_black")
        m.box(side, x - 1.3, 12, -1.3, x + 1.3, 13, 1.3, "brass")
    m.anim("idle", 3.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (1, (0, 10, 0)), (2, (0, -10, 0)), (3, (0, 0, 0))]},
                         "arm_l": {"rotation": mk.wave(8, 3.0, 4)}, "arm_r": {"rotation": mk.wave(8, 3.0, -4)}})
    m.anim("walk", 0.9, {"leg_l": {"rotation": mk.wave(8, 0.9, 30)}, "leg_r": {"rotation": mk.wave(8, 0.9, -30)},
                         "arm_l": {"rotation": mk.wave(8, 0.9, -30)}, "arm_r": {"rotation": mk.wave(8, 0.9, 30)}})
    m.anim("attack", 0.7, {"arm_l": {"rotation": [(0, (0, 0, 0)), (0.2, (-120, 0, -10)), (0.45, (30, 0, 0)), (0.7, (0, 0, 0))]},
                           "arm_r": {"rotation": [(0, (0, 0, 0)), (0.2, (-120, 0, 10)), (0.45, (30, 0, 0)), (0.7, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.9, {"root": {"rotation": [(0, (0, 0, 0)), (0.7, (90, 0, 0))], "position": [(0, (0, 0, 0)), (0.7, (0, 0, 4))]}}, loop="hold")
