"""Friendly EliteMobs NPC models (the NPCs of the Adventurers Guild config that had no model yet). Vanilla-sized humanoids
(8x12x4 body, 8 px head) with a role outfit and prop each; idle/walk/attack/death like every elite model plus a small
"talk" animation. Registered in MOBS (names em_npc_<role>)."""
import make_elite as me
from make_elite import mk, skeleton, face, goggles, gear, humanoid_anims

MOBS = {}


def npc(name):
    def deco(fn):
        MOBS[name] = fn
        return fn
    return deco


def hair(m, col, style="short"):
    m.box("h_head", -4.3, 30.4, -4.3, 4.3, 32.4, 4.3, col)                             # top cap
    m.box("h_head", -4.3, 28.6, -4.3, 4.3, 30.4, -3.9, col)                            # fringe
    m.box("h_head", -4.3, 25.6, 3.7, 4.3, 32.4, 4.4, col)                              # back
    for x in (-4.4, 3.7):
        m.box("h_head", x, 26.4, -4.3, x + 0.7, 32.4, 4.3, col)                        # sides
    if style == "long":
        m.box("h_head", -4.3, 17, 3.7, 4.3, 26, 4.8, col)
        m.box("body", -4.3, 14, 2.2, 4.3, 24, 2.9, col)
    elif style == "bun":
        m.box("h_head", -1.6, 32.4, 1.0, 1.6, 35.4, 4.0, col)
    elif style == "pony":
        m.box("h_head", -1.0, 22, 4.0, 1.0, 29, 5.6, col)
    elif style == "wild":
        for x, z in ((-3, -2), (0, 1), (3, -1), (-2, 2), (2, 2)):
            m.box("h_head", x - 1, 32.4, z - 1, x + 1, 34.6, z + 1, col)


def beard(m, col, kind="short"):
    if kind == "stache":
        m.box("h_head", -2.6, 25.8, -4.5, 2.6, 26.8, -4.1, col)
        return
    m.box("h_head", -3.4, 24.4, -4.5, 3.4, 26.6, -4.1, col)
    if kind in ("long", "santa"):
        m.box("body", -3, 14, -2.7, 3, 24, -2.2, col)
        if kind == "santa":
            m.box("body", -3.6, 12.2, -2.9, 3.6, 16, -2.3, col)


def hat(m, kind, col, accent="brass"):
    if kind == "cap":
        m.box("h_head", -4.4, 30.4, -4.4, 4.4, 32.2, 4.4, col)
        m.box("h_head", -4.4, 29.6, -6.6, 4.4, 30.6, -3.6, col + "_d")
    elif kind == "tophat":
        m.box("h_head", -5.2, 31.8, -5.2, 5.2, 32.8, 5.2, col)
        m.box("h_head", -3.6, 32.8, -3.6, 3.6, 39, 3.6, col)
        m.box("h_head", -3.8, 33.2, -3.8, 3.8, 34.4, 3.8, accent)
    elif kind == "wide":
        m.box("h_head", -6.4, 31.4, -6.4, 6.4, 32.4, 6.4, col)
        m.box("h_head", -3.6, 32.4, -3.6, 3.6, 36, 3.6, col)
        m.box("h_head", -3.8, 32.6, -3.8, 3.8, 33.6, 3.8, accent)
    elif kind == "pointy":
        m.box("h_head", -6, 31.4, -6, 6, 32.4, 6, col)
        m.box("h_head", -3.8, 32.4, -3.8, 3.8, 35, 3.8, col)
        m.box("h_head", -2.4, 35, -2.4, 2.4, 38, 2.4, col)
        m.box("h_head", -1, 38, -1, 1, 41, 1, col + "_d")
        m.box("h_head", -3.9, 32.6, -3.9, 3.9, 33.4, 3.9, accent)
    elif kind == "hood":
        m.box("h_head", -4.6, 24, -4.5, 4.6, 32.8, 4.6, col)
        m.box("h_head", -3.4, 25, -4.8, 3.4, 31, -4.4, "soot")
        m.box("h_head", -4.6, 20, -2.4, 4.6, 24, 4.6, col + "_d")
    elif kind == "helm":
        m.box("h_head", -4.5, 30, -4.5, 4.5, 32.8, 4.5, "iron")
        m.box("h_head", -4.5, 24.2, -4.5, -3.5, 31, 4.5, "iron_d")
        m.box("h_head", 3.5, 24.2, -4.5, 4.5, 31, 4.5, "iron_d")
        m.box("h_head", -0.6, 29, -5.0, 0.6, 33.5, -4.4, "steel")
        m.box("h_head", -0.8, 32.8, -3, 0.8, 35, 3, col)
    elif kind == "band":
        m.box("h_head", -4.4, 29.8, -4.4, 4.4, 31, 4.4, col)
        m.box("h_head", 4.0, 26.5, 1, 4.8, 30, 3, col)
    elif kind == "santa":
        m.box("h_head", -4.6, 30.6, -4.6, 4.6, 33.2, 4.6, "santa_red")
        m.box("h_head", -4.6, 30, -4.6, 4.6, 31.2, 4.6, "white")
        m.box("h_head", -3.2, 33.2, -3.2, 3.2, 36, 3.2, "santa_red")
        m.box("h_head", -1, 36, 1, 1, 38, 3, "white")
    elif kind == "feather":
        hat(m, "cap", col)
        m.box("h_head", 4.2, 31, 0, 5, 38, 2, "red_eye")
    elif kind == "visor":
        m.box("h_head", -4.4, 30, -4.4, 4.4, 31.2, 4.4, "white")
        m.box("h_head", -4.4, 29.4, -7, 4.4, 30.4, -4.2, col)


def robe(m, col, trim="gold"):
    m.box("body", -4.5, 2, -2.6, 4.5, 24.4, 2.6, col)
    m.box("body", -4.6, 2, -2.7, 4.6, 3.4, 2.7, trim)
    m.box("body", -0.5, 3.4, -2.8, 0.5, 24.4, -2.6, trim)
    for side in ("arm_l", "arm_r"):
        s = 1 if side == "arm_l" else -1
        m.box(side, s * 4 + (0 if s > 0 else -4.6), 11.4, -2.6, s * 4 + (4.6 if s > 0 else 0), 22.6, 2.6, col)
        m.box(side, s * 4 + (0 if s > 0 else -4.8), 10.4, -2.8, s * 4 + (4.8 if s > 0 else 0), 11.6, 2.8, trim)


def apron(m, col):
    m.box("body", -3.6, 5, -2.5, 3.6, 21, -2.0, col)
    m.box("body", -3.6, 21, -2.4, -2.6, 24.4, -2.0, col + "_d")
    m.box("body", 2.6, 21, -2.4, 3.6, 24.4, -2.0, col + "_d")


def belt(m, col="leather_dark"):
    m.box("body", -4.2, 12, -2.3, 4.2, 14, 2.3, col)
    m.box("body", -1, 12.2, -2.5, 1, 13.8, -2.3, "brass")


def talk(m):
    m.anim("talk", 2.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.5, (6, 8, 0)), (1.0, (-3, -6, 0)), (1.5, (5, 5, 0)), (2.0, (0, 0, 0))]},
                         "arm_l": {"rotation": [(0, (0, 0, 2)), (0.6, (-50, 0, 4)), (1.2, (-20, 0, 2)), (2.0, (0, 0, 2))]}})


def base(m, skin, shirt, pants, boots="leather_dark", brow="hair_brown", arms=None, hair_col=None, style="short", eye="black", mouth="skin_brown"):
    skeleton(m, skin, shirt, pants, boots=boots, arms_skin=arms or skin)
    face(m, eye=eye, mouth=mouth, brow=brow)
    if hair_col:
        hair(m, hair_col, style)


def finish(m, attack="swing", forward=0, amp=26):
    humanoid_anims(m, arms_forward=forward, walk_amp=amp, arm_amp=18, attack=attack, period=0.9)
    talk(m)


def hold(m, build):
    build(m)


def staff(m, head_col, glow=None, y=34):
    m.box("arm_r", -7.6, 0, -4.2, -6.6, y, -3.2, "wood")
    m.box("arm_r", -8.4, y, -5.0, -5.8, y + 2.6, -2.4, head_col)
    if glow:
        m.box("arm_r", -7.8, y + 0.3, -4.6, -6.4, y + 2.3, -2.8, glow)


def shield(m, col, accent="brass"):
    m.box("arm_l", 7.2, 8, -5.6, 8.2, 20, 1.2, col)
    m.box("arm_l", 7.0, 12, -2.8, 8.4, 16, -1.4, accent)


def sword(m, blade="steel", hilt="brass"):
    m.box("arm_r", -7.4, 8.4, -4.4, -6.6, 11, -3.4, "leather_dark")
    m.box("arm_r", -8.8, 11, -4.8, -5.2, 12, -3.0, hilt)
    m.box("arm_r", -7.6, 12, -4.4, -6.4, 28, -3.4, blade)


@npc("em_npc_hermes")
def hermes(m):
    """Messenger: winged cap and boots, gold-and-sky tunic, a letter satchel."""
    base(m, "skin_pale", "cloth_blue", "white", boots="gold", hair_col="hair_blond")
    hat(m, "cap", "cloth_blue")
    for s in (-1, 1):
        m.box("h_head", s * 4.4 - (0.4 if s > 0 else 0.4), 30.6, 0, s * 4.4 + (3.6 if s > 0 else -3.6), 33.6, 1, "white")
    for leg, s in (("leg_l", 1), ("leg_r", -1)):
        m.box(leg, s * 2 + 2, 2, 0, s * 2 + 4.8 * s, 6, 1, "white")
    m.box("body", -3, 12, 2.2, 3, 19, 4.6, "leather")
    m.box("body", -3.1, 17.4, 2.1, 3.1, 19, 4.7, "brass")
    belt(m)
    finish(m)


@npc("em_npc_bartley")
def bartley(m):
    base(m, "skin_tan", "white", "pants", hair_col="hair_black")
    apron(m, "apron")
    beard(m, "hair_black", "stache")
    m.box("arm_r", -7.6, 8, -6.4, -4.4, 12, -3.2, "brass")                               # mug
    m.box("arm_r", -7.4, 12, -6.2, -4.6, 13.4, -3.4, "white")
    m.box("arm_r", -4.2, 8.6, -5.2, -3.4, 11.2, -4.0, "brass_d")
    finish(m, "slam")


@npc("em_npc_vincent")
def vincent(m):
    base(m, "skin_pale", "white", "soot", hair_col="hair_black")
    hat(m, "visor", "cloth_green")
    m.box("body", -4.2, 13, -2.4, 4.2, 24, 2.4, "soot")
    m.box("body", -1.5, 13, -2.6, 1.5, 24, -2.4, "white")
    m.box("body", -1.2, 22, -2.8, 1.2, 23.4, -2.6, "coat_red")
    m.box("arm_l", 7.2, 8, -4.4, 8.4, 13, -1.6, "white")                                  # cards
    m.box("arm_l", 7.4, 12.2, -4.6, 8.0, 13.4, -1.8, "coat_red")
    finish(m, "shoot")


@npc("em_npc_greg")
def greg(m):
    base(m, "skin_brown", "rust", "leather_dark", boots="soot", brow="soot")
    apron(m, "leather")
    beard(m, "hair_brown", "long")
    m.box("arm_r", -7.4, 9, -4.0, -6.6, 24, -3.0, "wood")                                  # hammer
    m.box("arm_r", -9.6, 24, -5.6, -4.4, 29, -1.4, "steel")
    m.box("arm_r", -9.8, 24, -5.8, -4.2, 25, -1.2, "iron")
    for s in (-1, 1):
        m.box("body", s * 4.6 - 0.6, 20, -2.6, s * 4.6 + 0.6, 24, 2.6, "leather_dark")
    finish(m, "slam")


@npc("em_npc_ace")
def ace(m):
    base(m, "skin_pale", "white", "soot", boots="soot", hair_col="hair_black")
    hat(m, "wide", "hat_dark", "coat_red")
    beard(m, "hair_black", "stache")
    m.box("body", -4.5, 4, -2.6, 4.5, 24.4, 2.6, "coat_red")
    m.box("body", -0.5, 4, -2.8, 0.5, 24.4, -2.6, "gold")
    for s in (-1, 1):
        m.box("arm_l" if s > 0 else "arm_r", s * 4 - (0 if s > 0 else 4.4), 11.2, -2.5, s * 4 + (4.4 if s > 0 else 0), 22.4, 2.5, "coat_red")
    m.box("arm_r", -8, 8, -5.4, -4.4, 13, -3.6, "white")                                    # card fan
    m.box("arm_r", -7.6, 12.4, -5.8, -4.8, 13.6, -3.8, "coat_red")
    finish(m, "shoot")


@npc("em_npc_trainer_adventurer")
def trainer_adventurer(m):
    base(m, "skin_tan", "cloth_green", "leather", boots="leather_dark", hair_col="hair_brown")
    m.box("body", -4.3, 12, -2.4, 4.3, 24, 2.4, "leather")
    belt(m)
    m.box("body", -3.4, 12, 2.2, 3.4, 23, 5.6, "vest")                                      # backpack
    m.box("body", -3.6, 20, 2.1, 3.6, 21, 5.8, "brass")
    m.box("body", -2.8, 23, 3, 2.8, 25, 5.4, "cloth")
    sword(m)
    finish(m)


@npc("em_npc_trainer_berserker")
def trainer_berserker(m):
    base(m, "skin_tan", "fur_brown", "fur_dark", boots="leather_dark", hair_col="hair_red", style="wild", brow="hair_red")
    beard(m, "hair_red", "long")
    m.box("h_head", -3.2, 27, -4.35, -1, 28, -4.1, "red_eye")                               # war paint
    m.box("h_head", 1, 27, -4.35, 3.2, 28, -4.1, "red_eye")
    for side, s in (("arm_l", 1), ("arm_r", -1)):
        m.box(side, s * 4 - (0 if s > 0 else 4.8), 20, -2.8, s * 4 + (4.8 if s > 0 else 0), 24, 2.8, "fur_white")
    belt(m, "leather")
    m.box("arm_r", -7.4, 8.4, -4.0, -6.6, 24, -3.0, "wood")                                 # axe
    m.box("arm_r", -9.6, 20, -4.6, -6.0, 28, -2.4, "steel")
    m.box("arm_r", -10.2, 21, -4.4, -9.4, 27, -2.6, "iron")
    finish(m, "slam")


@npc("em_npc_trainer_cleric")
def trainer_cleric(m):
    base(m, "skin_pale", "white", "white", boots="gold", hair_col="hair_blond", style="long")
    robe(m, "white", "gold")
    m.box("h_head", -4.4, 30.8, -4.4, 4.4, 31.8, 4.4, "gold")
    staff(m, "gold", "glow_orange")
    finish(m, "swing")


@npc("em_npc_trainer_paladin")
def trainer_paladin(m):
    base(m, "skin_tan", "steel", "iron", boots="iron", hair_col=None)
    m.box("body", -4.5, 12, -2.5, 4.5, 24.4, 2.5, "iron")
    m.box("body", -3.5, 14, -2.8, 3.5, 22.4, -2.5, "white")                                 # tabard
    m.box("body", -0.6, 14, -2.9, 0.6, 22.4, -2.8, "coat_blue")
    for side, s in (("arm_l", 1), ("arm_r", -1)):
        m.box(side, s * 4 - (0 if s > 0 else 5), 19.6, -2.9, s * 4 + (5 if s > 0 else 0), 23.4, 2.9, "iron")
    hat(m, "helm", "coat_blue")
    sword(m)
    shield(m, "iron", "gold")
    finish(m, "swing")


@npc("em_npc_trainer_ranger")
def trainer_ranger(m):
    base(m, "skin_tan", "cloth_green", "leather_dark", boots="leather_dark")
    hat(m, "hood", "cloth_green")
    m.box("body", -4.3, 12, -2.4, 4.3, 24, 2.4, "cloth_green")
    belt(m)
    m.box("body", -2, 14, 2.3, 2, 26, 4.2, "leather")                                      # quiver
    for x in (-1, 0, 1):
        m.box("body", x - 0.3, 26, 2.8, x + 0.3, 29, 3.4, "bone")
    m.box("arm_l", 7.6, 4, -4.2, 8.4, 28, -3.2, "wood")                                    # bow
    m.box("arm_l", 7.4, 26, -4.2, 8.6, 28, -2.6, "wood_d")
    m.box("arm_l", 7.4, 4, -3.4, 8.6, 6, -2.6, "wood_d")
    m.box("arm_l", 7.7, 6, -3.1, 8.3, 26, -3.0, "white")
    finish(m, "shoot", forward=0)


@npc("em_npc_trainer_spellcaster")
def trainer_spellcaster(m):
    base(m, "skin_pale", "cloth_purple", "cloth_purple", boots="hat_dark", hair_col="hair_grey")
    robe(m, "robe_purple", "gold")
    beard(m, "hair_grey", "long")
    hat(m, "pointy", "robe_purple", "gold")
    staff(m, "gold", "arcane", y=30)
    finish(m, "swing")


@npc("em_npc_lucky_pete")
def lucky_pete(m):
    base(m, "skin_tan", "white", "pants", boots="leather_dark", hair_col="hair_brown")
    m.box("body", -4.3, 8, -2.4, 4.3, 24, 2.4, "gold")
    m.box("body", -0.5, 8, -2.6, 0.5, 24, -2.4, "brass_d")
    hat(m, "cap", "gold")
    m.box("arm_r", -8.4, 9, -7.4, -3.6, 14, -6.6, "brass")                                 # giant coin
    m.box("arm_r", -7.4, 10, -7.6, -4.6, 13, -7.4, "gold_l")
    beard(m, "hair_brown", "stache")
    finish(m, "slam")


@npc("em_npc_charles")
def charles(m):
    base(m, "skin_tan", "coat_red", "soot", boots="soot", hair_col="hair_black")
    hat(m, "band", "white")
    beard(m, "hair_black", "stache")
    m.box("body", -4.3, 12, -2.4, 4.3, 24, 2.4, "coat_red")
    m.box("body", -4.4, 18, -2.5, 4.4, 19.4, 2.5, "white")
    m.box("body", -0.5, 17, -3.0, 0.5, 21, -2.5, "brass")                                  # whistle
    m.box("arm_r", -7.2, 8, -5.0, -6.6, 28, -4.2, "wood")                                  # practice sword
    m.box("arm_r", -8.2, 11, -5.2, -5.6, 12, -4.0, "wood_d")
    finish(m, "swing")


@npc("em_npc_eden")
def eden(m):
    base(m, "skin_pale", "cloth_purple", "cloth_purple", boots="hat_dark", hair_col="hair_grey", style="long")
    robe(m, "robe_purple", "arcane")
    for x, y in ((-2.5, 18), (2, 14), (-1, 9), (3, 20)):
        m.box("body", x - 0.5, y, -2.9, x + 0.5, y + 1, -2.7, "arcane_l")
    m.box("arm_l", 6.6, 8.6, -6.8, 9.6, 12.6, -2.6, "leather")                             # spell book
    m.box("arm_l", 6.8, 9, -6.6, 9.4, 12.2, -6.4, "arcane")
    m.box("h_head", -4.4, 30.6, -4.4, 4.4, 31.4, 4.4, "arcane")
    finish(m, "shoot")


@npc("em_npc_robin")
def robin(m):
    base(m, "skin_pale", "cloth_green", "leather", boots="leather_dark", hair_col="hair_red")
    hat(m, "feather", "cloth_green")
    m.box("body", -2, 14, 2.3, 2, 26, 4.2, "leather")
    for x in (-1, 0, 1):
        m.box("body", x - 0.3, 26, 2.8, x + 0.3, 29.5, 3.4, "white")
    m.box("arm_l", 7.6, 4, -4.2, 8.4, 28, -3.2, "wood")
    m.box("arm_l", 7.7, 6, -3.1, 8.3, 26, -3.0, "white")
    belt(m)
    finish(m, "shoot")


@npc("em_npc_baron")
def baron(m):
    base(m, "skin_pale", "white", "soot", boots="soot", hair_col="hair_grey")
    robe(m, "coat_red", "gold")
    m.box("body", -4.8, 22, -2.8, 4.8, 24.8, 2.8, "fur_dark")                              # fur collar
    hat(m, "tophat", "soot", "gold")
    beard(m, "hair_grey", "stache")
    m.box("h_head", 1.6, 27.4, -5.6, 3.4, 29, -4.4, "gold")                                  # monocle
    m.box("arm_r", -7.4, 0, -4.4, -6.6, 22, -3.4, "gold")                                    # cane
    m.box("arm_r", -8.2, 22, -5, -5.8, 24.6, -2.8, "gold_l")
    finish(m, "swing")


def guide(m, coat, hair_col, skin):
    base(m, skin, "white", "leather_dark", boots="leather_dark", hair_col=hair_col)
    m.box("body", -4.5, 4, -2.6, 4.5, 24.4, 2.6, coat)
    m.box("body", -0.5, 4, -2.8, 0.5, 24.4, -2.6, "brass")
    for side, s in (("arm_l", 1), ("arm_r", -1)):
        m.box(side, s * 4 - (0 if s > 0 else 4.4), 11.4, -2.5, s * 4 + (4.4 if s > 0 else 0), 22.4, 2.5, coat)
    m.box("body", -1.4, 17, -3.2, 1.4, 20, -2.7, "brass")                                    # compass
    m.box("body", -0.9, 17.5, -3.4, 0.9, 19.5, -3.2, "glass_blue")
    m.box("arm_r", -8, 8, -6.6, -4.6, 11.4, -3.2, "cloth")                                   # scroll
    belt(m)


@npc("em_npc_dux")
def dux(m):
    guide(m, "coat_blue", "hair_brown", "skin_tan")
    hat(m, "cap", "coat_blue")
    finish(m)


@npc("em_npc_casus")
def casus(m):
    guide(m, "coat_red", "hair_black", "skin_brown")
    hat(m, "band", "brass")
    finish(m)


@npc("em_npc_gillian")
def gillian(m):
    base(m, "skin_pale", "white", "coat_blue", boots="leather_dark", hair_col="hair_brown", style="bun")
    m.box("body", -4.4, 4, -2.5, 4.4, 24.2, 2.5, "coat_blue")
    for y in (20, 17, 14):
        m.box("body", -0.4, y, -2.8, 0.4, y + 1, -2.5, "gold")
    m.box("body", -4.5, 22.6, -2.6, 4.5, 24.4, 2.6, "gold")
    m.box("arm_l", 6.8, 8.6, -6.8, 9.2, 13.4, -3.2, "wood")                                   # ledger
    m.box("arm_l", 7, 9.4, -6.9, 9, 12.6, -6.8, "cloth")
    finish(m)


@npc("em_npc_qelthuzad")
def qelthuzad(m):
    base(m, "skin_pale", "robe_purple", "hat_dark", boots="soot", hair_col="hair_grey", style="long")
    robe(m, "hat_dark", "sculk_glow")
    hat(m, "hood", "hat_dark")
    m.box("h_head", -2.6, 27.4, -4.9, -0.8, 28.8, -4.6, "sculk_glow")                       # glowing eyes
    m.box("h_head", 0.8, 27.4, -4.9, 2.6, 28.8, -4.6, "sculk_glow")
    staff(m, "bone", "sculk_glow", y=32)
    finish(m, "swing")


@npc("em_npc_reggie")
def reggie(m):
    base(m, "skin_tan", "white", "cloth_blue", boots="leather_dark", hair_col="hair_black")
    m.box("body", -4.3, 4, -2.4, 4.3, 24, 2.4, "cloth_blue")                                 # overalls
    m.box("body", -3, 14, -2.6, 3, 19, -2.4, "cloth_blue_d")
    for s in (-1, 1):
        m.box("body", s * 3 - 0.8, 19, -2.6, s * 3 + 0.8, 24.4, -2.4, "cloth_blue")
        m.box("body", s * 3 - 0.5, 22, -2.8, s * 3 + 0.5, 23, -2.6, "brass")
    goggles(m)
    m.box("arm_r", -7.4, 8.4, -5.6, -6.2, 16, -4.4, "steel")                                 # wrench
    m.box("arm_r", -8.2, 15, -6.2, -5.4, 19, -3.8, "steel")
    m.box("arm_r", -7.4, 17, -6.4, -6.2, 19, -5.8, "soot")
    gear(m, "body", 3.2, 8, -2.6, 1.6, "brass")
    finish(m, "slam")


@npc("em_npc_saint_nick")
def saint_nick(m):
    base(m, "skin_pale", "santa_red", "santa_red", boots="soot", hair_col=None)
    beard(m, "white", "santa")
    hat(m, "santa", "santa_red")
    m.box("body", -4.5, 12, -2.6, 4.5, 24.4, 2.6, "santa_red")
    m.box("body", -0.8, 12, -2.8, 0.8, 24.4, -2.6, "white")
    m.box("body", -4.6, 12, -2.7, 4.6, 13.6, 2.7, "white")
    belt(m, "soot")
    m.box("body", -3.5, 10, 2.4, 3.5, 22, 7, "leather")                                      # gift sack
    m.box("body", -3.7, 21, 2.2, 3.7, 23, 7.2, "gold")
    finish(m)


@npc("em_npc_kelly")
def kelly(m):
    base(m, "skin_tan", "rust", "leather_dark", boots="soot", hair_col="hair_black", style="pony")
    apron(m, "leather")
    m.box("h_head", -4.6, 29.8, -5.4, 4.6, 33, -3.6, "iron_d")                               # welding mask, pushed up
    m.box("h_head", -3.4, 30.4, -5.6, 3.4, 31.8, -5.4, "glass_blue_d")
    for side, s in (("arm_l", 1), ("arm_r", -1)):
        m.box(side, s * 4 - (0 if s > 0 else 4.6), 9.4, -2.6, s * 4 + (4.6 if s > 0 else 0), 13, 2.6, "leather_dark")
    m.box("arm_r", -9, 6, -5, -4.6, 12, -1, "iron")                                          # scrap bag
    gear(m, "arm_r", -6.8, 12.6, -5.2, 1.6, "brass", front=True)
    m.box("body", -3, 12, 2.2, 3, 22, 5, "leather_dark")
    finish(m, "slam")


@npc("em_npc_scotty")
def scotty(m):
    base(m, "skin_pale", "cloth_teal", "leather_dark", boots="leather_dark", hair_col="hair_brown")
    robe(m, "cloth_teal", "cloth")
    m.box("h_head", 1.0, 27.4, -4.9, 3.4, 29.6, -4.5, "brass")                               # glasses
    m.box("h_head", -3.4, 27.4, -4.9, -1.0, 29.6, -4.5, "brass")
    m.box("h_head", -3.0, 27.8, -5.0, -1.4, 29.2, -4.9, "glass_blue_l")
    m.box("h_head", 1.4, 27.8, -5.0, 3.0, 29.2, -4.9, "glass_blue_l")
    for x in (-2.5, 0, 2.5):
        m.box("body", x - 0.6, 13, -3.2, x + 0.6, 21, -2.6, "cloth")                           # scrolls in the belt
        m.box("body", x - 0.7, 17, -3.3, x + 0.7, 18, -2.5, "coat_red")
    belt(m)
    finish(m)


@npc("em_npc_spinner_sam")
def spinner_sam(m):
    base(m, "skin_brown", "white", "pants", boots="soot", hair_col="hair_black")
    m.box("body", -4.3, 12, -2.4, 4.3, 24, 2.4, "gold")
    for y in (14, 17, 20):
        m.box("body", -4.4, y, -2.5, 4.4, y + 1, 2.5, "coat_red")
    hat(m, "tophat", "coat_red", "gold")
    m.box("body", -4, 12, 2.4, 4, 26, 7, "coat_red")                                         # slot machine on his back
    m.box("body", -3, 18, 2.2, 3, 23, 2.4, "gold_l")
    for x in (-2, 0, 2):
        m.box("body", x - 0.8, 18.6, 2.0, x + 0.8, 22.4, 2.2, "white")
    m.box("body", 4, 20, 3, 5.2, 22, 4.4, "steel")                                           # lever
    m.box("body", 4, 22, 3, 6, 25, 4.6, "red_eye")
    finish(m, "slam")
