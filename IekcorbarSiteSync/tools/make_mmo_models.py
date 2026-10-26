r"""Genereert een eigen 3D-model + 16x16-textuur voor elke MMOItems-custom-item
nu ZONDER model-referentie (2026-10-26, gebruiker: "alle mmoitems krijgen eigen
models", "vergeet geen particles", "laat de vanilla items zoals ze horen").

Waarom gegenereerd in plaats met de hand: ~410 items, en de bestaande
moreweapon_2..41-modellen bevatten geen enkele naam-info, dus koppelen zou raden
zijn. Elke gegenereerde textuur is een diagonaal/oprecht pixel-ontwerp per
archetype (wapen/gereedschap/sieraad/armor/gem/...) met een pallet afgeleid van
de item-NAAM (silver, molten, frozen, bone, thor, santa, ...) + basismateriaal +
een deterministische kleurschakering per item-id, zodat elk item écht uniek is.

Model-opbouw volgt de pack-eigen stijl (zie ValhallaMMO's iron_dagger.json):
per opaque textuur-pixel één 2px-dik element op het verticale vlak (z 7..9),
noord-UV gemirrorde [x1,y0,x0,y1]-conventie, EN elke model krijgt een
"particle"-textuurverwijzing naar z'n eigen textuur (break/hit-deeltjes = juist
item). Geen vanilla overrides: de verwijzing is een item_model-component
(mmoitems:<stam>/<id>) in de MMOItems item-config zelf.

Inputs : E:\backups server\pluginset\MMOItems\item\*.yml   (server-sleutel)
Outputs: IekcorbarSiteSync/resourcepack/mmo/assets/mmoitems/models/item/<stam>/<id>.json
         IekcorbarSiteSync/resourcepack/mmo/assets/mmoitems/textures/item/<stam>/<id>.png
         pluginset-aangepast/MMOItems/item/*.yml  (model: regels + Noptuna-fix;
                    upload-map naar plugins/MMOItems/item op de server)
Gebruik: python IekcorbarSiteSync/tools/make_mmo_models.py   (GEEN -I: PIL staat
         in de user-site-packages; de -I-vlag van de andere scripts negeert die)
Daarna : python -I IekcorbarSiteSync/tools/make_pack.py && python -I scripts/merge-packs.py
"""
import hashlib, json, os, re, shutil

SRC_YML = r"E:\backups server\pluginset\MMOItems\item"
PACK = os.path.join("IekcorbarSiteSync", "resourcepack", "mmo", "assets", "mmoitems")
OUT_YML = os.path.join("pluginset-aangepast", "MMOItems", "item")

from PIL import Image, ImageDraw

# ---------------------------------------------------------------- palette ---

def h(key, i, mod=30):  # deterministische "hand"-variatie per item
    return int(hashlib.md5(key.encode()).hexdigest()[i * 2:i * 2 + 2], 16) % mod - mod // 2

def c(hexs, d=0):
    r0 = int(hexs[1:3], 16); g = int(hexs[3:5], 16); b = int(hexs[5:7], 16)
    return (max(0, min(255, r0 + d)), max(0, min(255, g + d)), max(0, min(255, b + d)))

MAT_PAL = {  # basismateriaal -> gezinkleuren (main, dark, light, accent)
    "IRON": ("#b8b8b8", "#8a8a8a", "#d9d9d9", "#6f6f6f"),
    "GOLDEN": ("#f4d03f", "#b7950b", "#fbe49a", "#7d6608"),
    "DIAMOND": ("#66e0d6", "#3aa6a0", "#a7f0eb", "#217c78"),
    "NETHERITE": ("#4a4244", "#2e282a", "#695f61", "#7c3b3b"),
    "STONE": ("#8e8e8e", "#6b6b6b", "#adadad", "#4f4f4f"),
    "WOODEN": ("#9a6d3f", "#6e4a26", "#bd9160", "#4b3319"),
    "LEATHER": ("#a06a3f", "#6f4826", "#c08a54", "#4a2f18"),
    "COPPER": ("#c77b4a", "#8f5430", "#e5a373", "#5f381f"),
}
KEY_PAL = {  # naam-zin -> kleurvervanginksel (wordt op main/dark/light toegepast)
    "silver": ("#dfe3e6", "#aeb4b8", "#f4f7f8"), "steel": ("#9fb4bf", "#6f838d", "#cfd9df"),
    "molten": ("#ff5a1f", "#b23308", "#ffc400"), "frozen": ("#9adcf5", "#4f9fbf", "#e7f8ff"),
    "ice": ("#9adcf5", "#4f9fbf", "#e7f8ff"), "hell": ("#c21f1f", "#6d0f0f", "#ff7a3c"),
    "fire": ("#ff6b1a", "#a03208", "#ffd24a"), "poisonous": ("#6fbe4e", "#3d6e2a", "#c8f29b"),
    "toxic": ("#6fbe4e", "#3d6e2a", "#c8f29b"), "bone": ("#e8e2ce", "#b5ab8e", "#f7f3e6"),
    "skeleton": ("#d9d2bd", "#9c947c", "#efeadd"), "shadow": ("#241d33", "#140f1e", "#463569"),
    "dark": ("#241d33", "#140f1e", "#463569"), "arcane": ("#8a4fd0", "#5a2e8f", "#c9a2f0"),
    "spellcaster": ("#4f6ad0", "#2e3f8f", "#a2b6f0"), "dragon": ("#1f6f54", "#0e4634", "#54b98e"),
    "undead": ("#7c8a5a", "#4c5636", "#b3c08c"), "moon": ("#8fb7e8", "#5c7fae", "#e0edff"),
    "sea": ("#2e9bd6", "#1b5f8a", "#9bd6f2"), "ocean": ("#2e9bd6", "#1b5f8a", "#9bd6f2"),
    "santa": ("#c22020", "#7a1212", "#ffffff"), "gingerbread": ("#b9793f", "#7a4c22", "#f2e3c9"),
    "candy": ("#e85d9a", "#a83b6c", "#ffffff"), "thor": ("#a9b7c0", "#6f7d86", "#ffd75e"),
    "barbarian": ("#9c5a2e", "#5f351a", "#c98d5a"), "emerald": ("#2ebd6b", "#1b7d44", "#9ff0bd"),
    "amethyst": ("#9a4fd0", "#5f2e8f", "#d0a2f0"), "ruby": ("#d6293b", "#8a1421", "#f58a97"),
    "sapphire": ("#2f5fd6", "#1b368a", "#97b3f5"), "topaz": ("#e8a12e", "#a86c14", "#f5d98a"),
    "opal": ("#dfe8e2", "#a5b3aa", "#f6fff9"), "lapis": ("#2b4bb8", "#182c75", "#7f97e6"),
    "brass": ("#c9a227", "#8f6f14", "#ecd07a"), "crystal": ("#8fe3f0", "#4f9fb0", "#eafcff"),
    "omnielemental": ("#d63b6a", "#751f3c", "#6ae3c8"), "executioner": ("#6f7579", "#3f4447", "#c8ccd0"),
    "yanis": ("#1f1f28", "#0e0e14", "#5a4fd0"), "butcher": ("#a33b3b", "#642020", "#e0a2a2"),
    "falcon": ("#c98d2e", "#8a5b14", "#f0d49b"), "cutlass": ("#b9bfc6", "#7d838a", "#e8ecf0"),
    "dracula": ("#8a1421", "#4d0a12", "#e0d8c8"), "precision": ("#9fb4bf", "#6f838d", "#cfd9df"),
    "composite": ("#9a6d3f", "#6e4a26", "#bd9160"), "medieval": ("#7d6a4f", "#4f4230", "#b8a17d"),
    "marking": ("#4f8a3d", "#2e5623", "#a3d68e"), "flowing": ("#5fb9c9", "#33707c", "#c3f0f7"),
    "master": ("#cfc4a8", "#8f8569", "#f2ecd9"), "primordial": ("#2e8a6a", "#17523c", "#7de3b8"),
    "red": ("#d6293b", "#8a1421", "#f58a97"), "green": ("#2ebd6b", "#1b7d44", "#9ff0bd"),
    "blue": ("#2f5fd6", "#1b368a", "#97b3f5"), "yellow": ("#e8c92e", "#a8900f", "#f5ec9a"),
    "purple": ("#9a4fd0", "#5f2e8f", "#d0a2f0"), "orange": ("#e8842e", "#a8540f", "#f5c49a"),
}

def palette(key, name, mat, family):
    pal = list(MAT_PAL.get(str(mat).upper(), ("#b9a58a", "#7f6f57", "#e4d5bd", "#5a4d3b")))
    low = (name or "").lower()
    for kw, cols in KEY_PAL.items():
        if re.search(rf"\b{kw}\b", low) or kw.upper() in (key or "").split("_"):
            pal[0], pal[1], pal[2] = cols[0], cols[1], cols[2]
            if kw in ("thor", "santa", "candy", "opal"):
                pal[3] = "#ffffff"
            break
    jit = (h(key, 0, 24), h(key, 1, 24))
    out = []
    for cc in pal:
        r0, g0, b0 = c(cc)
        out.append(((min(255, max(0, r0 + jit[0] // 2)), min(255, max(0, g0 + jit[1] // 2)),
                     min(255, max(0, b0 - jit[0] // 2)))))
    return out

# -------------------------------------------------------------- drawing ---

def new_img():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)

def line(d, x0, y0, x1, y1, col, w=2):
    d.line([(x0, y0), (x1, y1)], fill=col + (255,), width=w)

def blade_weapons(d, pal, kind):
    """Diagonale wapens: lemniscate (3,13)->(12,4), gevecht/staaf eronder."""
    main, dark, light, acc = pal
    if kind in ("sword", "long_sword", "greatsword", "katana", "rapier", "dagger", "thrusting_sword"):
        bl = 12 if kind != "dagger" else 8
        line(d, 3, 13, 3 + bl - 4, 13 - (bl - 4), main, 2)
        line(d, 3, 13, 3 + bl - 5, 13 - (bl - 5), light, 1)
        d.point((3 + bl - 4, 13 - (bl - 4)), fill=light + (255,))
        if kind == "dagger":
            line(d, 5, 11, 7, 13, dark, 2)
        else:
            line(d, 5, 11, 8, 14, dark, 2)
            line(d, 4, 14, 7, 11, acc, 2)
            line(d, 6, 13, 9, 10, acc, 2)
        line(d, 8, 15, 11, 12, (122, 74, 41), 2)
        if kind in ("greatsword", "long_sword"):
            d.point((2, 14), fill=(161, 122, 41) + (255,))
    elif kind in ("axe", "greataxe"):
        s = 1 if kind == "greataxe" else 0
        line(d, 3, 14, 9, 8, (122, 74, 41), 2)
        d.polygon([(10, 7), (13, 3 - s), (10, 3 - s), (8, 6)], fill=main + (255,))
        d.polygon([(10, 7), (13, 3 - s), (13, 6 + s)], fill=dark + (255,))
        d.point((11, 5), fill=light + (255,))
    elif kind == "pickaxe":
        line(d, 7, 13, 7, 7, (122, 74, 41), 2)
        line(d, 2, 6, 7, 3, main, 2)
        line(d, 12, 6, 7, 3, main, 2)
        line(d, 2, 6, 7, 4, light, 1)
        d.point((7, 3), fill=dark + (255,))
    elif kind in ("hammer", "greathammer", "mace", "club", "gauntlet"):
        line(d, 4, 14, 9, 9, (122, 74, 41), 2)
        box = (9, 2, 14, 7) if "great" not in kind else (8, 1, 15, 8)
        d.rectangle(box, fill=main + (255,))
        d.rectangle((box[0], box[1], box[2], box[1] + 1), fill=light + (255,))
        d.point((box[0] + 1, box[3] - 1), fill=dark + (255,))
        if kind == "mace":
            d.point((12, 3), fill=acc + (255,))
    elif kind in ("bow",):
        d.arc([4, 3, 14, 13], 260, 160, fill=main + (255,), width=2)
        line(d, 5, 12, 12, 4, light, 1)
    elif kind in ("crossbow", "musket", "handgun"):
        line(d, 2, 8, 13, 8, dark, 2)
        line(d, 8, 12, 11, 8, main, 2)
        d.point((13, 8), fill=acc + (255,))
        if kind == "crossbow":
            d.arc([4, 4, 12, 12], 120, 240, fill=main + (255,), width=1)
    elif kind in ("staff", "greatstaff", "wand"):
        line(d, 3, 14, 11, 6, (122, 74, 41), 2)
        d.ellipse((10, 2, 14, 6), fill=main + (255,), outline=light + (255,))
    elif kind in ("halberd", "lance", "spear"):
        line(d, 2, 14, 11, 5, (122, 74, 41), 2)
        d.polygon([(11, 5), (14, 2), (13, 6), (10, 7)], fill=main + (255,))
    elif kind in ("tome",):
        d.rectangle((3, 3, 13, 13), fill=main + (255,), outline=dark + (255,))
        d.rectangle((5, 5, 11, 11), outline=light + (255,))
        d.point((8, 8), fill=acc + (255,))
    elif kind == "lute":
        d.ellipse((2, 8, 9, 15), fill=main + (255,), outline=dark + (255,))
        line(d, 7, 10, 13, 4, dark, 2)
        d.point((13, 4), fill=light + (255,))
    elif kind == "shield":
        d.polygon([(3, 2), (13, 2), (13, 9), (8, 14), (3, 9)], fill=main + (255,), outline=dark + (255,))
        line(d, 8, 3, 8, 12, light, 1)
    elif kind == "ring":
        d.ellipse((4, 4, 12, 12), outline=main + (255,), width=2)
        d.point((8, 3), fill=acc + (255,))
    elif kind == "ornament":
        line(d, 4, 4, 12, 4, main, 1)
        d.polygon([(7, 4), (9, 4), (8, 8)], fill=light + (255,))
        d.point((8, 9), fill=acc + (255,))
    else:  # fallback: zweep/koel
        line(d, 3, 13, 12, 4, main, 2)

def blob(d, pal, facets=True):
    """Octaëder-gem (socket-gems, edelstenen)."""
    main, dark, light, acc = pal
    d.polygon([(8, 1), (13, 6), (8, 14), (3, 6)], fill=main + (255,))
    d.polygon([(8, 1), (13, 6), (8, 7)], fill=light + (255,))
    d.polygon([(8, 7), (13, 6), (8, 14)], fill=dark + (255,))
    if facets:
        line(d, 5, 5, 11, 5, light, 1)
        d.point((8, 9), fill=acc + (255,))

def armor(d, pal, slot):
    main, dark, light, acc = pal
    if slot == "helmet":
        d.rectangle((3, 4, 12, 12), fill=main + (255,), outline=dark + (255,))
        d.rectangle((5, 7, 10, 11), fill=(20, 20, 26, 200))
    elif slot == "chestplate":
        d.polygon([(3, 2), (6, 2), (8, 4), (10, 2), (13, 2), (13, 9), (11, 13), (5, 13), (3, 9)], fill=main + (255,), outline=dark + (255,))
        line(d, 8, 5, 8, 12, light, 1)
    elif slot == "leggings":
        d.polygon([(3, 1), (13, 1), (13, 6), (10, 6), (10, 15), (6, 15), (6, 6), (3, 6)], fill=main + (255,), outline=dark + (255,))
    elif slot == "boots":
        for x0 in (4, 9):
            d.polygon([(x0, 4), (x0 + 3, 4), (x0 + 3, 11), (x0 + 5, 11), (x0 + 5, 13), (x0, 13)], fill=main + (255,), outline=dark + (255,))
    d.point((8, 8), fill=acc + (255,)) if slot == "chestplate" else None

def material(d, pal, kind):
    main, dark, light, acc = pal
    if kind == "ingot":
        d.polygon([(3, 11), (5, 6), (12, 6), (14, 11)], fill=main + (255,))
        d.line([(5, 6), (12, 6)], fill=light + (255,), width=1)
        d.line([(3, 11), (14, 11)], fill=dark + (255,), width=1)
    elif kind == "dust":
        for (x, y) in ((5, 10), (6, 8), (8, 9), (10, 8), (9, 6), (7, 7)):
            d.point((x, y), fill=main + (255,))
        d.ellipse((4, 5, 11, 12), outline=light + (255,))
    elif kind == "shard":
        d.polygon([(8, 2), (11, 8), (8, 13), (5, 8)], fill=light + (255,), outline=main + (255,))
    else:  # nugget/core/knoop
        d.ellipse((4, 4, 11, 11), fill=main + (255,), outline=dark + (255,))
        d.point((6, 6), fill=light + (255,))

def consumable(d, pal, kind):
    main, dark, light, acc = pal
    if kind == "vial":
        d.rectangle((6, 2, 9, 4), fill=light + (255,))
        d.polygon([(5, 5), (10, 5), (11, 13), (4, 13)], fill=(220, 235, 245, 120))
        d.polygon([(5, 8), (10, 8), (11, 13), (4, 13)], fill=main + (255,))
    elif kind == "scroll":
        d.rectangle((3, 5, 13, 11), fill=(235, 222, 190, 255), outline=dark + (255,))
        line(d, 5, 7, 11, 7, (120, 90, 60), 1); line(d, 5, 9, 9, 9, (120, 90, 60), 1)
    elif kind == "food":
        d.ellipse((4, 6, 12, 14), fill=main + (255,), outline=dark + (255,))
        d.point((7, 5), fill=(122, 74, 41) + (255,))
        d.point((6, 9), fill=light + (255,))
    elif kind == "book":
        d.rectangle((3, 3, 13, 14), fill=(240, 235, 220, 255), outline=dark + (255,))
        line(d, 8, 3, 8, 14, dark, 1)
    else:
        d.ellipse((4, 4, 12, 12), fill=main + (255,))

def fish(d, pal):
    main, dark, light, acc = pal
    d.polygon([(2, 8), (5, 5), (10, 6), (10, 10), (5, 11)], fill=main + (255,))
    d.polygon([(10, 6), (14, 3), (13, 8), (14, 12), (10, 10)], fill=dark + (255,))
    d.point((5, 8), fill=(255, 255, 255, 255)); d.point((5, 8), fill=(20, 20, 20, 255))
    line(d, 7, 6, 7, 10, light, 1)

# ------------------------------------------------------------ archetypes ---

def pick(kind, key, name, mat):
    k = kind; low = ((name or "") + " " + key).lower()
    def has(*ws): return any(w in low for w in ws)
    if k in ("sword", "long_sword"):
        return "greatsword" if has("claymore", "great", "two handed", "dual") else ("katana" if has("katana") else k)
    if k == "greatsword":
        return "greatsword"
    if k == "dagger":
        return "dagger"
    if k == "thrusting_sword":
        return "rapier"
    if k == "axe":
        return "pickaxe" if has("pickaxe", "miner", "pick") else ("greataxe" if has("greataxe", "executioner") else "axe")
    if k == "greataxe":
        return "greataxe"
    if k == "hammer":
        return "mace" if has("santammer") or has("club", "cracker", "skull") else "hammer"
    if k == "greathammer":
        return "greathammer"
    if k == "mace":
        return "mace"
    if k in ("staff", "greatstaff"):
        return k
    if k in ("wand", "tome"):
        return "tome" if k == "tome" else "wand"
    if k == "bow":
        return "bow"
    if k == "crossbow":
        return "handgun" if has("hand gun", "handgun") else "crossbow"
    if k == "musket":
        return "musket"
    if k == "halberd":
        return "halberd"
    if k == "lance":
        return "lance"
    if k == "spear":
        return "spear"
    if k == "whip":
        return "whip"
    if k == "gauntlet":
        return "gauntlet"
    if k == "shield":
        return "shield"
    if k == "ring":
        return "ring"
    if k in ("ornament", "talisman", "bracelet", "amulet"):
        return "ornament"
    if k == "lute":
        return "lute"
    return k

def draw_item(kind, akey, name, mat):
    img, d = new_img()
    pal = palette(akey, name, mat, kind)
    if kind in ("sword", "long_sword", "greatsword", "katana", "rapier", "dagger", "thrusting_sword",
                "axe", "greataxe", "pickaxe", "hammer", "greathammer", "mace", "club", "bow",
                "crossbow", "musket", "handgun", "staff", "greatstaff", "wand", "halberd", "lance",
                "spear", "gauntlet", "shield", "ring", "ornament", "tome", "lute", "whip"):
        blade_weapons(d, pal, kind)
        if kind == "whip":
            d.arc([3, 6, 13, 14], 0, 300, fill=pal[0] + (255,), width=2)
        if kind == "gauntlet":
            d.rectangle((4, 6, 12, 13), fill=pal[0] + (255,), outline=pal[1] + (255,))
            for x in range(5, 12, 2):
                d.point((x, 6), fill=pal[2] + (255,))
    elif kind in ("gem", "gemstone", "catalyst", "off_catalyst"):
        blob(d, pal, facets=kind != "catalyst")
    elif kind in ("helmet", "chestplate", "leggings", "boots"):
        armor(d, pal, kind)
    elif kind in ("ingot", "dust", "shard", "nugget"):
        material(d, pal, kind)
    elif kind in ("vial", "scroll", "food", "book"):
        consumable(d, pal, kind)
    elif kind == "fish":
        fish(d, pal)
    else:
        blob(d, pal, False)
    return img

# ------------------------------------------------------- model (extrusie) ---

DISPLAY_HELD = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -30, 0], "translation": [1.5, 3.5, 0], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 2.5], "scale": [0.6, 0.6, 0.6]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}

def build_model(tex_ref, flat=False, img=None):
    els = []
    for ty in range(16):
        for tx in range(16):
            if img is not None and img.getpixel((tx, ty))[3] == 0:
                continue  # geen onzichtbare kubussen voor lege pixels
            x0, y0 = tx, 15 - ty
            u0, v0, u1, v1 = tx, ty, tx + 1, ty + 1
            if flat:  # 1px dik voor platte sprites
                z0, z1 = 7.75, 8.25
            else:
                z0, z1 = 7, 9
            els.append({
                "from": [x0, y0, z0], "to": [x1 := x0 + 1, y1 := y0 + 1, z1],
                "faces": {
                    "north": {"uv": [u1, v0, u0, v1], "texture": "#0"},
                    "south": {"uv": [u0, v0, u1, v1], "texture": "#0"},
                    "east": {"uv": [u1, v0, u0, v1], "rotation": 90, "texture": "#0"},
                    "west": {"uv": [u1, v0, u0, v1], "rotation": 270, "texture": "#0"},
                    "up": {"uv": [u0, v1, u1, v0], "texture": "#0"},
                    "down": {"uv": [u0, v1, u1, v0], "texture": "#0"},
                },
            })
    m = {"credit": "iekcorbar procedural MMO model (make_mmo_models.py)",
         "textures": {"0": tex_ref, "particle": tex_ref},
         "elements": els, "gui_light": "front"}
    if not flat:
        m["display"] = DISPLAY_HELD
    return m

# ------------------------------------------------------------------ main ---

def main():
    os.makedirs(OUT_YML, exist_ok=True)
    written_models = written_tex = 0
    generated_refs = []

    for fn in sorted(os.listdir(SRC_YML)):
        if not fn.endswith(".yml"):
            continue
        stem = fn[:-4]
        stem_out = re.sub(r"^00_job_", "", stem)
        txt = open(os.path.join(SRC_YML, fn), encoding="utf-8", errors="replace").read()
        # typo-fix: item met model -> Nuptuna maar verkeerd gespeld
        txt = txt.replace("mmoitems:fish/Noptuna", "mmoitems:fish/Nuptuna")
        blocks = re.split(r"(?m)^([A-Z][A-Z0-9_]+):\s*$", txt)
        pieces = [blocks[0]]
        for i in range(1, len(blocks), 2):
            key, body = blocks[i], blocks[i + 1] if i + 1 < len(blocks) else ""
            if key in ("base", "lore", "type") or not re.match(r"[A-Z]", key):
                pieces.append(f"{key}:\n{body}")
                continue
            model_ref = None
            m = re.search(r"(?m)^(\s{4}model:\s*)mmoitems:(\S+)\s*$", body)
            if m:
                model_ref = ("keep", m.group(2))
            else:
                # orphan-packmodel met exact dezelfde id-stam? direct koppelen.
                # Eigen procedural-modellen (credit-fingerprint) VALLEN NIET onder
                # orphans: die hearings opnieuw gegenereerd bij elke run.
                cand = f"{stem_out}/{key.lower()}"
                orphan_json = os.path.join(PACK, "models", "item", *cand.split("/")) + ".json"
                orphan_ok = False
                if os.path.isfile(orphan_json):
                    try:
                        cr = json.load(open(orphan_json, encoding='utf-8')).get('credit', '')
                        orphan_ok = not cr.startswith('iekcorbar procedural')
                    except ValueError:
                        orphan_ok = True
                if orphan_ok:
                    model_ref = ("orphan", cand)
                else:
                    nm = re.search(r"(?m)^\s+name:\s*['\"]?(.+?)['\"]?\s*$", body)
                    mt = re.search(r"(?m)^\s+material:\s*(\S+)", body)
                    name = nm.group(1) if nm else key
                    mat = mt.group(1) if mt else ""
                    stem_kind = STEM2TYPE.get(stem_out, stem_out)
                    low = (name + " " + key).lower()
                    if stem_kind == "armor":  # armor: vorm op slot (key- of materiaalwoord)
                        ku = key.upper()
                        stem_kind = ("helmet" if any(w in ku for w in ("HELMET", "HELM", "CROWN", "CAP", "HEAD"))
                                     else "chestplate" if any(w in ku for w in ("CHEST", "TUNIC", "ROBE", "Chest".upper()))
                                     else "leggings" if any(w in ku for w in ("LEGG", "PANTS"))
                                     else "boots" if any(w in ku for w in ("BOOTS", "SHOES"))
                                     else str(mat).upper().rsplit("_", 1)[-1])
                    elif stem_kind == "nugget":  # material-/misc-familie: subtype op naam
                        for w, sub in (("ingot", "ingot"), ("dust", "dust"), ("shard", "shard"),
                                       ("powder", "dust"), ("gem", "gem")):
                            if w in low:
                                stem_kind = sub
                                break
                    elif stem_kind == "vial":  # consumables: vial/scroll/food/book op naam
                        for w, sub in (("scroll", "scroll"), ("book", "book"), ("steak", "food"),
                                       ("bread", "food"), ("apple", "food"), ("stew", "food"),
                                       ("pie", "food"), ("cookie", "food"), ("elixir", "vial"),
                                       ("potion", "vial"), ("tincture", "vial"), ("salve", "vial")):
                            if w in low:
                                stem_kind = sub
                                break
                    kind = pick(stem_kind, key, name, mat)
                    akey = f"{stem_out}/{key.lower()}"
                    img = draw_item(kind, akey, name, mat)
                    tpath = os.path.join(PACK, "textures", "item", *akey.split("/")) + ".png"
                    os.makedirs(os.path.dirname(tpath), exist_ok=True)
                    img.save(tpath)
                    mpath = os.path.join(PACK, "models", "item", *akey.split("/")) + ".json"
                    os.makedirs(os.path.dirname(mpath), exist_ok=True)
                    mp = json.dumps(build_model(f"item/{akey}", flat=kind in ("gem", "gemstone", "ingot", "dust", "shard", "nugget", "vial", "scroll", "food", "book", "fish", "helmet", "chestplate", "leggings", "boots", "ornament"), img=img), indent=1)
                    open(mpath, "w", encoding="utf-8").write(mp)
                    written_models += 1
                    written_tex += 1
                    model_ref = ("new", akey)
            if model_ref and model_ref[0] != "keep":
                # model:-regel direct onder de 'base:'-regel van dit item zetten
                # (base: staat op 2 spaties, zijn kinderen op 4 — zie de item-ymls)
                mb = re.search(r"(?m)^  base:\s*$", body)
                if mb:
                    insert_at = mb.end()
                    body = body[:insert_at] + f"\n    model: mmoitems:{model_ref[1]}" + body[insert_at:]
            pieces.append(f"{key}:\n{body}")
        open(os.path.join(OUT_YML, fn), "w", encoding="utf-8").write("".join(pieces))

    print(f"gegenereerd: {written_models} modellen + {written_tex} textuur-png's")
    print(f"yml's gepatched naar {OUT_YML}")

STEM2TYPE = {"gem_stone": "gem", "material": "nugget", "consumable": "vial", "fish": "fish",
             "catalyst": "catalyst", "miscellaneous": "nugget", "block": "nugget"}
main()
