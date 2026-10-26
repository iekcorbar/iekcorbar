"""Writes COPIES of the usable EliteMobs boss files with `customModel: <model>` appended (the originals are never touched).

Usage: python -I tools/assign_models.py <custombosses folder> [out folder]     (default out: ../server/custombosses)
Usable = own entity type (own or inherited), enabled, no disguise, no customModel yet. The model comes from
make_elite_all.TYPE_TO_MODEL. Also writes assigned.csv (path, entityType, model) next to the out folder.
Server: copy the out folder's files over plugins/EliteMobs/custombosses/ (same relative paths) and the models into
plugins/FreeMinecraftModels/models/elitemobs/, then /em reload.
"""
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # -I does not add the script folder

import scan_bosses as sb
from make_elite_all import TYPE_TO_MODEL


def main():
    root = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "server", "custombosses")
    files = sb.load(root)
    rows = []
    skipped = {"template": 0, "model": 0, "disguise": 0, "disabled": 0, "no_model_for_type": 0}
    for rel in sorted(files):
        r = sb.resolve(files, rel)
        if not r["entityType"]:
            skipped["template"] += 1
        elif r["customModel"]:
            skipped["model"] += 1
        elif r["disguise"]:
            skipped["disguise"] += 1
        elif r["isEnabled"] == "false":
            skipped["disabled"] += 1
        elif r["entityType"].upper() not in TYPE_TO_MODEL:
            skipped["no_model_for_type"] += 1
        else:
            model = TYPE_TO_MODEL[r["entityType"].upper()]
            text = files[rel]
            if not text.endswith("\n"):
                text += "\n"
            dest = os.path.join(out, rel)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "w", encoding="utf-8", newline="\n") as f:
                f.write(text + "customModel: " + model + "\n")
            rows.append((rel, r["entityType"], model))
    with open(os.path.join(out, "..", "assigned.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["path", "entityType", "model"])
        w.writerows(rows)
    print("assigned", len(rows), "bosses; skipped", skipped)


if __name__ == "__main__":
    main()
