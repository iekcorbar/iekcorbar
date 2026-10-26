"""Writes src/main/resources/recipes.yml for Sakagura: the recipes from iekcorbar.com/brewing
(tools/site-recipes.json, fetched from /api/brewing/recipes) unchanged, plus our own Japanese /
steampunk drinks. Run from the Sakagura folder: python -I tools/make_recipes.py

Step format (same as the website): cook {time minutes, ingredients}, mix {time, ingredients} (a
cauldron without heat), distill {runs}, age {years, wood}. Ingredients: "<name>/<count>", where a
name is a vanilla item ("Sugar", "Milk Bucket"), a finished drink ("Gin"), kitchen:<id> or
sakagura:<id>.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
site = json.load(open(os.path.join(HERE, "site-recipes.json"), encoding="utf-8"))["recipes"]

OWN = [
    # slug, names (poor/normal/good), colour, difficulty, alcohol, lore, effects, steps
    ("sake", ["Rough Sake", "Sake", "Daiginjo Sake"], "f2efe0", 6, 15, ["<gray>Rice, water and patience"], ["REGENERATION/0-1/20-40"],
     [{"type": "cook", "time": 8, "ingredients": ["kitchen:steamed_rice/6", "Sugar/1"]}, {"type": "age", "years": 2, "wood": "spruce"}]),
    ("nigori", ["Watery Nigori", "Nigori Sake", "Creamy Nigori"], "ffffff", 3, 12, ["<gray>Cloudy and unfiltered"], ["SATURATION/1/2"],
     [{"type": "cook", "time": 6, "ingredients": ["kitchen:steamed_rice/5", "Sugar/2"]}]),
    ("shochu", ["Harsh Shochu", "Shochu", "Smooth Shochu"], "e8e4d8", 6, 25, ["<gray>Distilled twice"], ["RESISTANCE/0/30-60"],
     [{"type": "cook", "time": 7, "ingredients": ["sakagura:barley/6", "Potato/2"]}, {"type": "distill", "runs": 2}]),
    ("umeshu", ["Sour Umeshu", "Umeshu", "Aged Umeshu"], "c98a3a", 5, 14, ["<gray>Plum wine, sweet and tart"], ["REGENERATION/1/20-40"],
     [{"type": "mix", "time": 3, "ingredients": ["sakagura:ume/6", "Sugar/4", "Shochu/1"]}, {"type": "age", "years": 3, "wood": "cherry"}]),
    ("yuzu_chuhai", ["Flat Chuhai", "Yuzu Chuhai", "Sparkling Yuzu Chuhai"], "f7e04a", 3, 8, ["<gray>Shochu, yuzu and bubbles"], ["SPEED/0/60-120"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:yuzu/3", "Sugar/2", "Shochu/1"]}]),
    ("momo_sake", ["Bland Momo Sake", "Momo Sake", "Blushing Momo Sake"], "f7b2a2", 5, 10, ["<gray>Peach sake"], ["HEALTH_BOOST/0/60-120"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:peach/4", "Sake/1"]}, {"type": "age", "years": 1, "wood": "birch"}]),
    ("sakura_liqueur", ["Wilted Liqueur", "Sakura Liqueur", "Cherry Blossom Liqueur"], "ffb7c5", 6, 18, ["<light_purple>Smells of spring"], ["LUCK/0/120-300"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:cherry/6", "Sugar/3", "Pink Petals/4"]}, {"type": "distill", "runs": 1}, {"type": "age", "years": 2, "wood": "cherry"}]),
    ("steam_whisky", ["Sooty Whisky", "Steam Whisky", "Brass Reserve Whisky"], "b06a2c", 8, 32, ["<gold>Distilled in a copper still"], ["STRENGTH/0/60-120"],
     [{"type": "cook", "time": 10, "ingredients": ["sakagura:barley/10"]}, {"type": "distill", "runs": 3}, {"type": "age", "years": 8, "wood": "copper"}]),
    ("hop_ale", ["Flat Ale", "Hop Ale", "Golden Hop Ale"], "d9a441", 3, 6, ["<gray>Bitter and fresh"], ["HASTE/0/60-120"],
     [{"type": "cook", "time": 6, "ingredients": ["sakagura:barley/6", "sakagura:hops/3"]}, {"type": "age", "years": 2, "wood": "oak"}]),
    ("red_wine", ["Harsh Red Wine", "Red Wine", "Full-Bodied Red Wine"], "7b1e2b", 6, 12, ["<gray>From our own vines"], ["REGENERATION/0/20-40"],
     [{"type": "cook", "time": 6, "ingredients": ["sakagura:grape/8"]}, {"type": "age", "years": 4, "wood": "oak"}]),
    ("juniper_gin", ["Pine-Needle Gin", "Juniper Gin", "Botanical Gin"], "dff3f0", 6, 30, ["<gray>Juniper and a little ginger"], ["NIGHT_VISION/0/120-300"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:juniper/6", "sakagura:ginger/1", "Wheat/3"]}, {"type": "distill", "runs": 3}]),
    ("berry_wine", ["Sour Berry Wine", "Berry Wine", "Forest Berry Wine"], "6a2a7a", 4, 10, ["<gray>Blueberries and strawberries"], ["REGENERATION/0/20-30"],
     [{"type": "cook", "time": 5, "ingredients": ["sakagura:blueberry/4", "sakagura:strawberry/4", "Sugar/2"]}, {"type": "age", "years": 2, "wood": "birch"}]),
    ("apple_liquor", ["Weak Apple Liquor", "Apple Liquor", "Fine Apple Liquor"], "e8b04a", 5, 25, ["<gray>Needed for a good sangria"], [],
     [{"type": "cook", "time": 5, "ingredients": ["Apple/6", "Sugar/2"]}, {"type": "distill", "runs": 2}]),
    # non-alcoholic
    ("amazake", ["Thin Amazake", "Amazake", "Rich Amazake"], "f6efe0", 2, 0, ["<gray>Sweet rice drink, no alcohol"], ["SATURATION/0/3"],
     [{"type": "cook", "time": 4, "ingredients": ["kitchen:steamed_rice/3", "sakagura:ginger/1"]}]),
    ("matcha_latte", ["Watery Matcha", "Matcha Latte", "Ceremonial Matcha Latte"], "8fbf5a", 2, 0, ["<green>Green and gentle"], ["HASTE/0/120-240"],
     [{"type": "cook", "time": 2, "ingredients": ["kitchen:tea_leaves/3", "Milk Bucket/1", "Sugar/1"]}]),
    ("mugicha", ["Weak Mugicha", "Mugicha", "Roasted Mugicha"], "a8743a", 1, 0, ["<gray>Cold barley tea"], ["FIRE_RESISTANCE/0/60-120"],
     [{"type": "mix", "time": 2, "ingredients": ["sakagura:barley/3", "Snowball/2"]}]),
    ("ramune", ["Flat Ramune", "Ramune", "Fizzy Ramune"], "9fd8f0", 2, 0, ["<aqua>The marble rattles"], ["JUMP_BOOST/0-1/60-120"],
     [{"type": "mix", "time": 1, "ingredients": ["Sugar/3", "sakagura:yuzu/1", "Glass/1"]}]),
]


def ing(s):
    return f"{s['name']}/{s['count']}"


def site_steps(steps):
    out = []
    for s in steps:
        if s["type"] in ("cook", "mix"):
            out.append({"type": s["type"], "time": s.get("time", 1), "ingredients": [ing(i) for i in s.get("ingredients", [])]})
        elif s["type"] == "distill":
            out.append({"type": "distill", "runs": s.get("runs", 1)})
        elif s["type"] == "age":
            out.append({"type": "age", "years": s.get("years", 1), "wood": s.get("barrel", "any")})
    return out


def yq(v):
    """YAML scalar: quote strings that need it."""
    if isinstance(v, (int, float)):
        return str(v)
    return json.dumps(v, ensure_ascii=False)


lines = ["# Sakagura recipes. The first group comes from iekcorbar.com/brewing (keep them in sync with the",
         "# website); the second group is Sakagura's own. Regenerate with tools/make_recipes.py, or edit freely",
         "# and /brew reload. Names: poor / normal / good quality (one name = always that one).",
         "# Effects: TYPE/level/seconds, ranges like 0-1 scale with quality (harmful ones get milder).",
         "# Lore lines starting with + / ++ / +++ only show on poor / normal / good brews.", "recipes:"]


def emit(slug, names, color, diff, alcohol, lore, effects, steps):
    lines.append(f"  {slug}:")
    lines.append(f"    names: [{', '.join(yq(n) for n in names)}]")
    lines.append(f"    color: {yq(color)}")
    lines.append(f"    difficulty: {diff}")
    lines.append(f"    alcohol: {alcohol or 0}")
    lines.append(f"    lore: [{', '.join(yq(l) for l in lore)}]")
    lines.append(f"    effects: [{', '.join(yq(e) for e in effects)}]")
    lines.append("    steps:")
    for st in steps:
        parts = [f"type: {st['type']}"]
        for k in ("time", "runs", "years", "wood"):
            if k in st:
                parts.append(f"{k}: {yq(st[k])}")
        if "ingredients" in st:
            parts.append("ingredients: [" + ", ".join(yq(i) for i in st["ingredients"]) + "]")
        lines.append("      - {" + ", ".join(parts) + "}")


lines.append("  # ---- from iekcorbar.com/brewing")
for x in site:
    emit(x["slug"], x["names"], x["color"], x.get("difficulty", 5), x.get("alcohol"), x.get("lore", []), x.get("effects", []), site_steps(x["steps"]))
lines.append("  # ---- Sakagura's own")
for o in OWN:
    emit(*o)
out = os.path.join(HERE, "..", "src", "main", "resources", "recipes.yml")
open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("wrote", len(site), "site +", len(OWN), "own recipes to", os.path.normpath(out))


# ---- the wiki recipe page (docs/wiki-sakagura/03-recipes.md), from the same data

def pretty(token):
    name, _, count = token.partition("/")
    low = name.lower()
    if ":" in low:
        name = low.split(":", 1)[1].replace("_", " ").title()
    return f"{count} {name}" if count else name


def steps_text(steps):
    parts = []
    for st in steps:
        if st["type"] in ("cook", "mix"):
            verb = "Cook" if st["type"] == "cook" else "Mix (no heat)"
            parts.append(f"{verb} {st.get('time', 1)} min: " + ", ".join(pretty(i) for i in st.get("ingredients", [])))
        elif st["type"] == "distill":
            parts.append(f"Distill {st.get('runs', 1)}x")
        elif st["type"] == "age":
            wood = st.get("wood", "any")
            parts.append(f"Age {st.get('years', 1)} years" + ("" if wood == "any" else f" ({wood.replace('_', ' ')} cask)"))
    return " -> ".join(parts)


rows = []
for x in site:
    rows.append((x["names"][min(1, len(x["names"]) - 1)], steps_text(site_steps(x["steps"])), x.get("alcohol") or 0))
for o in OWN:
    rows.append((o[1][min(1, len(o[1]) - 1)], steps_text(o[7]), o[4]))
md = ["---", "slug: sakagura-recipes", "title: Sakagura Recipes", "category: Sakagura", "parent: sakagura", "---",
      "## Recipes", "", "Follow a recipe closely for the best quality (up to 5 stars). Cooking needs heat under the cauldron; mixing is done in a cauldron without heat. One minute means one real minute; one cask year is 20 real minutes.", "",
      "| Drink | How to make it | Alcohol |", "|---|---|---|"]
for name, how, alc in rows:
    md.append(f"| {name} | {how} | {str(alc) + '%' if alc and alc > 0 else '-'} |")
docs = os.path.join(HERE, "..", "..", "docs", "wiki-sakagura")
os.makedirs(docs, exist_ok=True)
open(os.path.join(docs, "03-recipes.md"), "w", encoding="utf-8").write(chr(10).join(md) + chr(10))
print("wrote the wiki recipe page with", len(rows), "drinks")


# ---- the website seed (src/lib/brewing-seeds/sakagura.json): our own recipes in the website's format, for the
#      staff "Import Sakagura recipes" button on /brewing (adds only recipes the website doesn't have yet)
FRUIT_NAMES = {"barley": "Barley", "hops": "Hops", "grape": "Grapes", "ume": "Ume Plums", "yuzu": "Yuzu", "peach": "Peaches",
               "cherry": "Cherries", "strawberry": "Strawberries", "blueberry": "Blueberries", "juniper": "Juniper Berries", "ginger": "Ginger"}


def site_ingredient(token):
    name, _, count = token.partition("/")
    low = name.lower()
    if low.startswith("sakagura:"):
        name = FRUIT_NAMES.get(low[9:], low[9:].replace("_", " ").title())
    elif low.startswith("kitchen:"):
        name = low[8:].replace("_", " ").title()
    return {"name": name, "count": int(count or 1)}


def site_step(st):
    if st["type"] in ("cook", "mix"):
        return {"type": st["type"], "time": st.get("time", 1), "ingredients": [site_ingredient(i) for i in st["ingredients"]]}
    if st["type"] == "distill":
        return {"type": "distill", "runs": st.get("runs", 1)}
    return {"type": "age", "years": st.get("years", 1), "barrel": st.get("wood", "any")}


seed = [{"slug": o[0], "names": o[1], "color": o[2], "difficulty": o[3], "alcohol": o[4] or None, "lore": o[5], "effects": o[6],
         "steps": [site_step(st) for st in o[7]]} for o in OWN]
seed_dir = os.path.join(HERE, "..", "..", "src", "lib", "brewing-seeds")
os.makedirs(seed_dir, exist_ok=True)
json.dump({"source": "Sakagura/tools/make_recipes.py", "recipes": seed}, open(os.path.join(seed_dir, "sakagura.json"), "w", encoding="utf-8"), indent=1)
print("wrote the website seed with", len(seed), "recipes")
