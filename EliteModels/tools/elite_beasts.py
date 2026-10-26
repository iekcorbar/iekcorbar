"""Four-legged, eight-legged and small EliteMobs models (own designs): ravager, wolf, rabbit, polar bear, zoglin, horse,
pig, spiders, silverfish, endermite. Head at -Z, bones: root > body > h_head, leg_fl/fr/bl/br, tail. Registered in MOBS."""
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


def quad_legs(m, xs, zf, zb, top, upper, lower, w, col, foot="steel", claws=False):
    for name, x, z in (("leg_fl", xs, zf), ("leg_fr", -xs, zf), ("leg_bl", xs, zb), ("leg_br", -xs, zb)):
        m.bone(name, (x, top, z), "body")
        m.box(name, x - w / 2, top - upper, z - w / 2, x + w / 2, top, z + w / 2, col)
        m.box(name, x - w / 2 + 0.2, 0.8, z - w / 2 + 0.2, x + w / 2 - 0.2, top - upper + 0.2, z + w / 2 - 0.2, col + "_d")
        m.box(name, x - w / 2 - 0.3, 0, z - w / 2 - 0.5, x + w / 2 + 0.3, 1.0, z + w / 2 + 0.3, foot)
        m.box(name, x - w / 2 - 0.2, top - upper - 0.4, z - w / 2 - 0.2, x + w / 2 + 0.2, top - upper + 0.6, z + w / 2 + 0.2, "brass")


def quad_anims(m, period=0.9, amp=26, attack_head=-35, jump=False):
    m.anim("idle", 3.0, {"h_head": {"rotation": [(0, (0, 0, 0)), (1, (0, 12, 0)), (2, (0, -12, 0)), (3, (0, 0, 0))]},
                         "body": {"position": [(0, (0, 0, 0)), (1.5, (0, 0.3, 0)), (3, (0, 0, 0))]},
                         "tail": {"rotation": mk.wave(8, 3.0, 8, 0, axis=1)}})
    mk.quad_walk(m, period, amp, 3, 10)
    m.anim("attack", 0.6, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.2, (attack_head, 0, 0)), (0.35, (25, 0, 0)), (0.6, (0, 0, 0))]},
                           "body": {"rotation": [(0, (0, 0, 0)), (0.2, (-6, 0, 0)), (0.35, (8, 0, 0)), (0.6, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.9, {"root": {"rotation": [(0, (0, 0, 0)), (0.7, (0, 0, 90))], "position": [(0, (0, 0, 0)), (0.7, (-3, 0, 0))]}}, loop="hold")


def root_body(m, y):
    m.bone("root", (0, 0, 0))
    m.bone("body", (0, y, 0), "root")


@mob("em_ram_ravager")
def ram_ravager(m):
    """Ravager as a siege ram: iron-plated back with a boiler and chimney, a piston horn and glowing furnace eyes."""
    root_body(m, 20)
    m.box("body", -8, 14, -10, 8, 30, 12, "fur_dark")
    m.box("body", -8.5, 26, -10, 8.5, 30.6, 12, "iron")                               # back plate
    for z in (-6, 0, 6):
        m.box("body", -8.7, 25, z - 0.4, 8.7, 31, z + 0.4, "brass")
    m.box("body", -5, 30.6, -4, 5, 40, 8, "copper")                                    # boiler
    m.box("body", -5.2, 34, -4.2, 5.2, 35, 8.2, "brass")
    m.box("body", -1.6, 40, 0, 1.6, 47, 3.2, "steel")
    m.box("body", -2.2, 47, -0.6, 2.2, 48.4, 3.8, "soot")
    m.bone("tail", (0, 24, 12), "body")
    m.box("tail", -1, 22, 12, 1, 25, 18, "fur_dark")
    m.bone("h_head", (0, 28, -10), "body")
    m.box("h_head", -5, 22, -20, 5, 34, -9, "iron")
    m.box("h_head", -4, 18, -24, 4, 25, -14, "fur_dark")                                # jaw
    for x in (-3, -1, 1, 3):
        m.box("h_head", x - 0.4, 24.4, -23.6, x + 0.4, 27, -23, "tusk")
    m.box("h_head", -1.6, 34, -17, 1.6, 44, -13, "steel")                              # piston horn
    m.box("h_head", -2.0, 40, -17.4, 2.0, 41, -12.6, "brass")
    m.box("h_head", -1.2, 44, -16.6, 1.2, 47, -13.6, "iron_l")
    m.box("h_head", -4.2, 29, -20.4, -1.8, 31.4, -20, "glow_orange")
    m.box("h_head", 1.8, 29, -20.4, 4.2, 31.4, -20, "glow_orange")
    quad_legs(m, 5.5, -8, 9, 15, 10, 5, 5, "fur_dark", "iron")
    quad_anims(m, 1.0, 22, -45)


@mob("em_cog_wolf")
def cog_wolf(m):
    """Wolf with copper plating, goggles, a wind-up key on the back and a steel tail."""
    root_body(m, 9)
    m.box("body", -3.5, 5, -6, 3.5, 12, 6, "wolf_fur")
    m.box("body", -3.7, 10.4, -5, 3.7, 12.4, 3, "copper")                              # armour plates
    m.box("body", -3.7, 9.8, -5.2, 3.7, 10.6, 3.2, "brass")
    m.box("body", -0.4, 12.4, -1, 0.4, 16, 0, "brass")                                  # key
    m.box("body", -2.4, 15.4, -1.4, 2.4, 16.8, 0.4, "brass")
    m.bone("tail", (0, 10, 6), "body")
    m.box("tail", -0.8, 7, 6, 0.8, 10, 14, "steel")
    m.box("tail", -1.4, 7.4, 12.6, 1.4, 9.6, 15, "copper")
    m.bone("h_head", (0, 10, -6), "body")
    m.box("h_head", -3, 6, -10, 3, 12.6, -5, "wolf_fur")
    m.box("h_head", -1.6, 6, -13.4, 1.6, 9.2, -10, "wolf_fur_l")
    m.box("h_head", -0.8, 8.4, -13.8, 0.8, 9.4, -13.4, "black")
    for x in (-2.2, 2.2):
        m.box("h_head", x - 0.8, 12.6, -8, x + 0.8, 15, -6.4, "wolf_fur_d")
    m.box("h_head", -3.2, 10, -9.6, 3.2, 11, -4.8, "leather_dark")                      # goggle strap
    for x in (-1.9, 1.9):
        m.box("h_head", x - 1.3, 9.6, -10.2, x + 1.3, 12, -9.4, "brass")
        m.box("h_head", x - 0.9, 10, -10.4, x + 0.9, 11.6, -10.2, "glass_blue")
    quad_legs(m, 2.2, -5, 5, 6.5, 4, 2.5, 2, "wolf_fur", "steel")
    quad_anims(m, 0.7, 30, -30)


@mob("em_wind_rabbit")
def wind_rabbit(m):
    """Clockwork rabbit: brass ears, a winding key and a small gear in the cheek."""
    root_body(m, 4)
    m.box("body", -2.4, 2, -3.5, 2.4, 7, 3.5, "rabbit_fur")
    m.box("body", -2.6, 5.4, -3, 2.6, 6, 3, "brass")
    m.box("body", -0.3, 7, 0, 0.3, 10, 0.6, "brass")
    m.box("body", -1.8, 9.6, -0.2, 1.8, 10.6, 0.8, "brass")
    m.bone("tail", (0, 4, 3.5), "body")
    m.box("tail", -1.2, 3, 3.5, 1.2, 5.4, 5.6, "fur_white")
    m.bone("h_head", (0, 5, -3.5), "body")
    m.box("h_head", -2, 3, -7, 2, 7.4, -3, "rabbit_fur")
    m.box("h_head", -0.6, 3.4, -7.4, 0.6, 4.6, -7, "pig_pink")
    for x in (-1.2, 1.2):
        m.box("h_head", x - 0.5, 7.4, -5.6, x + 0.5, 15.5, -4.6, "brass")              # tall ears
        m.box("h_head", x - 0.3, 8, -5.8, x + 0.3, 14.5, -5.6, "copper")
    m.box("h_head", -1.8, 5.4, -7.2, -0.8, 6.4, -7.0, "glow_orange")
    m.box("h_head", 0.8, 5.4, -7.2, 1.8, 6.4, -7.0, "glow_orange")
    quad_legs(m, 1.6, -2, 2.6, 3.2, 2, 1, 1.4, "rabbit_fur", "steel")
    quad_anims(m, 0.5, 40, -25)


@mob("em_iron_bear", 1.0)
def iron_bear(m):
    """Polar bear in riveted iron plates with a furnace on its back and ice-blue fur showing at the seams."""
    root_body(m, 16)
    m.box("body", -7, 12, -14, 7, 26, 14, "fur_white")
    m.box("body", -7.4, 22, -11, 7.4, 26.6, 11, "iron")
    for z in (-8, -3, 2, 7):
        m.box("body", -7.6, 21, z - 0.4, 7.6, 27, z + 0.4, "brass")
    m.box("body", -3.6, 26.6, -2, 3.6, 33, 8, "steel")
    m.box("body", -3.8, 28, -2.2, 3.8, 29, 8.2, "brass")
    m.box("body", -1.4, 33, 1.5, 1.4, 38, 4.5, "soot")
    m.box("body", -2.4, 12.4, -9, 2.4, 20, -14.2, "glow_orange")                         # furnace door in the chest
    m.bone("tail", (0, 22, 14), "body")
    m.box("tail", -1.5, 20, 14, 1.5, 23, 16, "fur_white_d")
    m.bone("h_head", (0, 22, -14), "body")
    m.box("h_head", -4.5, 17, -22, 4.5, 26, -14, "fur_white")
    m.box("h_head", -2.6, 17.4, -27, 2.6, 22.4, -22, "fur_white_d")
    m.box("h_head", -1.4, 20.6, -27.6, 1.4, 22.2, -27, "black")
    m.box("h_head", -4.6, 24.8, -20, 4.6, 26.4, -14.4, "iron")
    for x in (-3.4, 3.4):
        m.box("h_head", x - 1.2, 26, -18, x + 1.2, 28.4, -16, "fur_white_d")
        m.box("h_head", x - 0.6, 23.4, -22.4, x + 0.6, 24.4, -22.2, "red_eye")
    quad_legs(m, 4.6, -10, 10, 13, 8, 5, 5.2, "fur_white", "iron")
    quad_anims(m, 1.1, 20, -30)


@mob("em_cinder_zoglin")
def cinder_zoglin(m):
    """Zoglin: rotting hide over bone, exposed ribs, big tusks and magma cracks."""
    root_body(m, 17)
    m.box("body", -7, 11, -12, 7, 25, 12, "zombie_skin_d")
    m.box("body", -7.2, 18, -9, 7.2, 25.4, 11, "piglin_skin")
    for z in (-6, -2, 2, 6):
        m.box("body", -7.4, 14, z - 0.5, 7.4, 22, z + 0.5, "bone")                         # ribs
    m.box("body", -2, 24.8, -10, 2, 26.4, 10, "magma")
    for z in (-6, 0, 6):
        m.box("body", -0.8, 25.6, z - 1, 0.8, 26.5, z + 1, "lava")
    m.bone("tail", (0, 22, 12), "body")
    m.box("tail", -0.8, 18, 12, 0.8, 22, 14, "zombie_skin")
    m.bone("h_head", (0, 21, -12), "body")
    m.box("h_head", -5, 15, -22, 5, 25, -11, "zombie_skin")
    m.box("h_head", -3.5, 14.4, -24, 3.5, 19, -21, "snout")
    for x in (-3.4, 3.4):
        m.box("h_head", x - 0.9, 15, -24, x + 0.9, 23, -22.4, "tusk")
        m.box("h_head", x - 0.8, 25, -19, x + 0.8, 29, -17, "zombie_skin_d")
        m.box("h_head", x - 0.7, 22.4, -22.2, x + 0.7, 23.6, -22, "red_eye")
    m.box("h_head", -1.4, 24.6, -18, 1.4, 26.4, -13, "lava_dark")
    quad_legs(m, 4.6, -8, 8, 12, 8, 4, 4.4, "zombie_skin", "nether_brick")
    quad_anims(m, 0.95, 24, -40)


@mob("em_clockwork_horse")
def clockwork_horse(m):
    """Horse of brass and leather: visible cogs in the flank, copper mane pipes and glowing lens eyes."""
    root_body(m, 22)
    m.box("body", -5, 16, -11, 5, 28, 11, "horse_coat")
    m.box("body", -5.2, 20, -3, 5.2, 21, 4, "leather")
    m.box("body", -4.8, 15.4, -2.5, 4.8, 16.8, 3.5, "leather_dark")
    gear(m, "body", 5.3, 22, -2, 3.2, "brass", front=False)
    gear(m, "body", -5.3, 22, 2, 2.4, "copper", front=True)
    m.bone("tail", (0, 26, 11), "body")
    for i in range(4):
        m.box("tail", -0.5 + i * 0.3, 14, 11 + i * 0.6, 0.5 + i * 0.3, 26 - i, 12 + i * 0.6, "copper")
    m.bone("h_head", (0, 28, -9), "body")
    m.box("h_head", -2.5, 24, -13, 2.5, 38, -8, "horse_coat")
    m.box("h_head", -2.5, 34, -21, 2.5, 40, -12, "horse_coat")
    m.box("h_head", -2.7, 34.2, -21.4, 2.7, 36.4, -18, "brass")                          # muzzle plate
    m.box("h_head", -0.6, 28, -13.6, 0.6, 38, -12.6, "copper")
    for x in (-1.6, 1.6):
        m.box("h_head", x - 0.7, 40, -11, x + 0.7, 43, -9.6, "horse_coat_d")
        m.box("h_head", x * 1.75 - 0.5, 37.6, -15.2, x * 1.75 + 0.5, 39.2, -14.2, "glow_orange")
    quad_legs(m, 3, -8, 8, 17, 10, 7, 3, "horse_coat", "steel")
    quad_anims(m, 0.8, 30, -25)


@mob("em_pipe_pig")
def pipe_pig(m):
    """Pig with a copper boiler on the back, a curly pipe tail and brass-capped trotters."""
    root_body(m, 8)
    m.box("body", -4.5, 4, -7, 4.5, 12, 7, "pig_pink")
    m.box("body", -3.6, 12, -3, 3.6, 17, 3, "copper")
    m.box("body", -3.8, 14, -3.2, 3.8, 15, 3.2, "brass")
    m.box("body", -0.8, 17, -0.8, 0.8, 20, 0.8, "steel")
    m.bone("tail", (0, 10, 7), "body")
    m.box("tail", -0.5, 10, 7, 0.5, 12, 8.4, "brass")
    m.box("tail", -0.5, 11.4, 8, 0.5, 12.4, 10, "brass")
    m.bone("h_head", (0, 9, -7), "body")
    m.box("h_head", -4, 5, -15, 4, 12.5, -7, "pig_pink")
    m.box("h_head", -2, 5.4, -16, 2, 8.6, -15, "snout")
    m.box("h_head", -1.4, 6, -16.3, -0.6, 7.4, -16, "soot")
    m.box("h_head", 0.6, 6, -16.3, 1.4, 7.4, -16, "soot")
    m.box("h_head", -4.2, 9.6, -13.4, 4.2, 10.4, -9, "leather_dark")
    for x in (-2.4, 2.4):
        m.box("h_head", x - 1.2, 9.4, -14.6, x + 1.2, 11.6, -13.4, "brass")
        m.box("h_head", x - 0.8, 9.8, -14.8, x + 0.8, 11.2, -14.6, "glass_blue")
        m.box("h_head", x - 1.0, 12.5, -11, x + 1.0, 14.4, -9.4, "pig_pink_d")
    quad_legs(m, 2.8, -4.6, 4.6, 5, 4, 1, 2.8, "pig_pink", "brass")
    quad_anims(m, 0.7, 28, -25)


# ------------------------------------------------------------------ eight legs

def spider(m, body_col, leg_col, eye_col, big, abdomen_extra):
    """Eight-legged body: legs are bones that swing in pairs. big = scale of the whole model."""
    root_body(m, 8)
    m.box("body", -3.5, 5, -2.5, 3.5, 11, 4.5, body_col)                                # thorax
    m.bone("h_head", (0, 8, -3), "body")
    m.box("h_head", -3, 5.4, -10, 3, 11, -3, body_col)
    for x in (-2.2, -0.9, 0.9, 2.2):
        m.box("h_head", x - 0.35, 8.6, -10.3, x + 0.35, 9.8, -10, eye_col)
    m.box("h_head", -1.6, 5.4, -11.2, -0.6, 7.6, -9.6, "bone")                            # fangs
    m.box("h_head", 0.6, 5.4, -11.2, 1.6, 7.6, -9.6, "bone")
    m.box("body", -5, 4.5, 4.5, 5, 12.5, 14.5, body_col + "_d")                           # abdomen
    abdomen_extra(m)
    for i in range(4):
        z = -1.2 + i * 1.6
        for side, s in (("l", 1), ("r", -1)):
            n = "leg_%s%d" % (side, i)
            m.bone(n, (s * 3.5, 8, z), "body")
            m.box(n, s * 3.5, 7.4, z - 0.5, s * 11.5, 8.8, z + 0.5, leg_col)             # upper
            m.box(n, s * 10.7, 0, z - 0.5, s * 12.1, 8.4, z + 0.5, leg_col + "_d")        # lower
            m.box(n, s * 10.6, 7.8, z - 0.8, s * 11.6, 9.2, z + 0.8, "brass")             # joint
    legs = {"leg_%s%d" % (sd, i): i for i in range(4) for sd in "lr"}
    walk = {}
    for n, i in legs.items():
        sign = 1 if n[4] == "l" else -1
        walk[n] = {"rotation": mk.wave(8, 0.7, 22 * sign, i * 1.57, axis=1)}
    m.anim("walk", 0.7, walk)
    m.anim("idle", 3.0, {"body": {"position": [(0, (0, 0, 0)), (1.5, (0, 0.4, 0)), (3, (0, 0, 0))]},
                         "h_head": {"rotation": [(0, (0, 0, 0)), (1, (0, 8, 0)), (2, (0, -8, 0)), (3, (0, 0, 0))]}})
    m.anim("attack", 0.5, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.15, (-30, 0, 0)), (0.3, (25, 0, 0)), (0.5, (0, 0, 0))]},
                           "body": {"rotation": [(0, (0, 0, 0)), (0.15, (-14, 0, 0)), (0.3, (10, 0, 0)), (0.5, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.8, {"root": {"rotation": [(0, (0, 0, 0)), (0.6, (0, 0, 180))], "position": [(0, (0, 0, 0)), (0.6, (0, 6, 0))]}}, loop="hold")


@mob("em_brass_spider")
def brass_spider(m):
    def extra(m):
        gear(m, "body", 0, 10, 14.5, 3.0, "brass", front=False)
        m.box("body", -3, 12.5, 6, 3, 14.5, 12, "copper")
        m.box("body", -0.7, 14.5, 8, 0.7, 17, 10, "steel")
    spider(m, "spider_fur", "iron", "spider_eye", 1.0, extra)


@mob("em_lantern_cave_spider")
def lantern_cave_spider(m):
    def extra(m):
        m.box("body", -3, 12.5, 6, 3, 17, 12, "glass_blue")
        m.box("body", -2.2, 13.2, 6.8, 2.2, 16.2, 11.2, "slime_green")
        m.box("body", -3.2, 12.4, 5.8, 3.2, 13.2, 12.2, "brass")
    spider(m, "bog", "moss", "glow_orange", 1.0, extra)


@mob("em_screw_silverfish")
def screw_silverfish(m):
    root_body(m, 2)
    for i, (w, h, z) in enumerate(((2.5, 2.2, -4.5), (3.2, 2.8, -2), (3.6, 3.2, 1), (2.8, 2.4, 3.6), (1.8, 1.6, 5.4))):
        m.box("body", -w, 0.4, z - 1.2, w, 0.4 + h, z + 1.2, "silver" if i % 2 == 0 else "steel")
        m.box("body", -w - 0.1, 0.4 + h * 0.4, z - 0.3, w + 0.1, 0.4 + h * 0.6, z + 0.3, "brass")
    m.bone("h_head", (0, 2, -4.5), "body")
    m.box("h_head", -1.4, 0.6, -7, 1.4, 3, -4.4, "silver_l")
    m.box("h_head", -1.8, 2.4, -6.6, -0.8, 3.4, -6.2, "black")
    m.box("h_head", 0.8, 2.4, -6.6, 1.8, 3.4, -6.2, "black")
    m.box("h_head", -0.2, 2.6, -8.4, 0.2, 3.2, -6.6, "brass")                               # drill snout
    m.anim("idle", 2.0, {"body": {"rotation": mk.wave(8, 2.0, 4, 0, axis=1)}})
    m.anim("walk", 0.4, {"body": {"rotation": mk.wave(8, 0.4, 10, 0, axis=1)}, "h_head": {"rotation": mk.wave(8, 0.4, 8, 1, axis=1)}})
    m.anim("attack", 0.4, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.15, (-25, 0, 0)), (0.4, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.5, {"root": {"rotation": [(0, (0, 0, 0)), (0.4, (0, 0, 180))], "position": [(0, (0, 0, 0)), (0.4, (0, 3, 0))]}}, loop="hold")


@mob("em_cog_endermite")
def cog_endermite(m):
    root_body(m, 2)
    for i, (w, h, z) in enumerate(((2.2, 2, -3), (3, 2.6, -0.4), (2.4, 2, 2.4))):
        m.box("body", -w, 0.4, z - 1.4, w, 0.4 + h, z + 1.4, "end_purple")
    gear(m, "body", 0, 3, 3.8, 1.6, "brass", front=False)
    m.bone("h_head", (0, 2, -3), "body")
    m.box("h_head", -1.6, 0.4, -5.6, 1.6, 2.8, -3, "end_black")
    m.box("h_head", -1.2, 1.8, -5.8, -0.4, 2.6, -5.6, "end_eye")
    m.box("h_head", 0.4, 1.8, -5.8, 1.2, 2.6, -5.6, "end_eye")
    m.anim("idle", 2.0, {"body": {"rotation": mk.wave(8, 2.0, 5, 0, axis=1)}})
    m.anim("walk", 0.4, {"body": {"rotation": mk.wave(8, 0.4, 12, 0, axis=1)}})
    m.anim("attack", 0.4, {"h_head": {"rotation": [(0, (0, 0, 0)), (0.15, (-25, 0, 0)), (0.4, (0, 0, 0))]}}, loop="once")
    m.anim("death", 0.5, {"root": {"rotation": [(0, (0, 0, 0)), (0.4, (0, 0, 180))], "position": [(0, (0, 0, 0)), (0.4, (0, 3, 0))]}}, loop="hold")
