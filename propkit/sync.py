"""Copies the shared prop engine (Decor.java, DecorDef.java) into each plugin that uses it, with the
package name and model namespace filled in. Run after editing the engine, from the repo root:
python -I propkit/sync.py"""
import os

TARGETS = {
    "Wildlife": ("com.iekcorbar.wildlife", "zoo"),
    "Sakagura": ("com.iekcorbar.sakagura", "garden"),
    "Kitchen": ("com.iekcorbar.kitchen", "kitchendecor"),
    "Hihou": ("com.iekcorbar.hihou", "hihou"),
    "SteamworksAdditions": ("com.iekcorbar.additions", "additions"),
}
here = os.path.dirname(os.path.abspath(__file__))
# Adv.java (advancement helper) goes into every plugin that shows advancements
ADV = {
    "Steamworks": "com.iekcorbar.steamworks", "Kitchen": "com.iekcorbar.kitchen", "Wildlife": "com.iekcorbar.wildlife",
    "Relics": "com.iekcorbar.relics", "Sakagura": "com.iekcorbar.sakagura", "Kura": "com.iekcorbar.kura",
    "Zukan": "com.iekcorbar.zukan", "Satchel": "com.iekcorbar.satchel",
}
for plugin, pkg in ADV.items():
    out = os.path.join(here, "..", plugin, "src", "main", "java", *pkg.split("."))
    if os.path.isdir(out):
        s = open(os.path.join(here, "Adv.java"), encoding="utf-8").read().replace("__PKG__", pkg)
        open(os.path.join(out, "Adv.java"), "w", encoding="utf-8", newline=chr(10)).write(s)
        print("synced Adv into", plugin)
# TraderSwap.java (items sold by wandering traders) goes into the plugins whose items are in WanderingTrades files
TRADER = {"Steamworks": "com.iekcorbar.steamworks", "Kitchen": "com.iekcorbar.kitchen", "Sakagura": "com.iekcorbar.sakagura", "Kura": "com.iekcorbar.kura"}
for plugin, pkg in TRADER.items():
    out = os.path.join(here, "..", plugin, "src", "main", "java", *pkg.split("."))
    if os.path.isdir(out):
        s = open(os.path.join(here, "TraderSwap.java"), encoding="utf-8").read().replace("__PKG__", pkg)
        open(os.path.join(out, "TraderSwap.java"), "w", encoding="utf-8", newline=chr(10)).write(s)
        print("synced TraderSwap into", plugin)
for plugin, (pkg, ns) in TARGETS.items():
    out = os.path.join(here, "..", plugin, "src", "main", "java", *pkg.split("."))
    if not os.path.isdir(out):
        print("skip", plugin)
        continue
    for f in ("Decor.java", "DecorDef.java"):
        s = open(os.path.join(here, f), encoding="utf-8").read().replace("__PKG__", pkg).replace("__NS__", ns)
        open(os.path.join(out, f), "w", encoding="utf-8", newline="\n").write(s)
    print("synced", plugin)
