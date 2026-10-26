"""Writes COPIES of the EliteMobs NPC files that have no model yet, with `customModel: <model>` instead of the
LibsDisguises `disguise:` line (originals are never touched).

Usage: python -I tools/assign_npcs.py <EliteMobs npcs folder> [out folder]     (default out: ../server/npcs)
Models come from make_elite_all.NPC_MODELS. Server: copy the files over plugins/EliteMobs/npcs/ and the models into
plugins/FreeMinecraftModels/models/elitemobs/, then /em reload.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # -I does not add the script folder

from make_elite_all import NPC_MODELS


def main():
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "server", "npcs")
    os.makedirs(out, exist_ok=True)
    done, skipped = [], []
    for key, model in sorted(NPC_MODELS.items()):
        p = os.path.join(src, key + ".yml")
        if not os.path.exists(p):
            skipped.append(key + " (file missing)")
            continue
        text = open(p, encoding="utf-8", errors="replace").read()
        if re.search(r"^customModel:", text, re.M):
            skipped.append(key + " (already has a model)")
            continue
        line = "customModel: " + model
        if re.search(r"^disguise:.*$", text, re.M):
            text = re.sub(r"^disguise:.*$", line, text, count=1, flags=re.M)
        else:
            text = text + ("" if text.endswith("\n") else "\n") + line + "\n"
        with open(os.path.join(out, key + ".yml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        done.append(key)
    print("wrote", len(done), "npc files; skipped:", skipped)


if __name__ == "__main__":
    main()
