"""Render a picture for every recipe item that has an item model in one of our plugins.

Writes public/recipes/<plugin>/<id>.png (transparent, 128 px) using that plugin's own
tools/preview3d.py. Skips ids that already have a picture. Then run
scripts/make-recipe-icons.py so src/lib/recipe-pictures.json lists the new files.

Run from the repo root: python -I scripts/render-recipe-pictures.py [--dry]
"""
import glob, json, os, re, subprocess, sys

DRY = "--dry" in sys.argv
ROOT = os.getcwd()
PLUGINS = ["Kitchen", "Sakagura", "Relics", "Kura", "Steamworks", "Satchel", "Wildlife", "Hihou", "SteamworksAdditions"]


def ident(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


data = json.load(open("src/lib/recipes-data.json", encoding="utf-8"))
ids = set()
for p in data["plugins"]:
    for r in p["recipes"]:
        names = [n for row in (r.get("grid") or []) for n in row if n]
        for k in ("ingredients", "outputs"):
            names += [i["name"] for i in r.get(k, [])]
        if r.get("result"):
            names.append(r["result"])
        for n in names:
            for part in n.split(" or "):
                ids.add(ident(part))

VANILLA_NAMES = {"apple"}  # vanilla items that Sakagura only overrides; the real item looks different
have = set()
for d in os.listdir("public/recipes"):
    path = os.path.join("public/recipes", d)
    if os.path.isdir(path) and d != "icons":
        have |= {f[:-4] for f in os.listdir(path) if f.endswith(".png")}

todo = []
for f in glob.glob("*/**/assets/*/models/item/*.json", recursive=True):
    plugin = f.split(os.sep)[0]
    if plugin not in PLUGINS or "archief" in f or "node_modules" in f:
        continue
    iid = os.path.basename(f)[:-5]
    if iid in ids and iid not in have and iid not in VANILLA_NAMES and not any(t[0] == iid for t in todo):
        todo.append((iid, plugin, f))

print(f"{len(todo)} item(s) to render")
for iid, plugin, f in sorted(todo):
    tool = os.path.join(plugin, "tools", "preview3d.py")
    if not os.path.exists(tool):
        print(f"skip {iid}: no {tool}")
        continue
    out_dir = os.path.join("public", "recipes", plugin.lower())
    if plugin == "SteamworksAdditions":
        out_dir = os.path.join("public", "recipes", "additions")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"{iid}.png")
    if DRY:
        print(f"would render {iid} from {f} -> {out}")
        continue
    env = dict(os.environ, TRANSPARENT="1")
    subprocess.run([sys.executable, "-I", os.path.abspath(tool), os.path.abspath(out), "128", os.path.abspath(f)], check=True, env=env)
    print(f"rendered {out}")
