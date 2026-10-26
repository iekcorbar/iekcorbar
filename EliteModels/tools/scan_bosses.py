"""Survey of an EliteMobs custombosses folder: resolves `extends:` chains, then reports per entity type how many bosses
could get a generated model (no custom disguise, no model of their own).

Usage: python -I tools/scan_bosses.py <custombosses folder> [out.json]
Read-only: the boss files are only read.
"""
import collections
import json
import os
import re
import sys

KEYS = ("entityType", "customModel", "disguise", "name", "isEnabled", "scale", "isBaby", "level")


def load(root):
    files = {}
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.endswith(".yml"):
                p = os.path.join(d, f)
                t = open(p, encoding="utf-8", errors="replace").read()
                files[os.path.relpath(p, root).replace(os.sep, "/")] = t
    return files


def field(text, key):
    m = re.search(r"^" + key + r":\s*(.*)$", text, re.M)
    return m.group(1).strip().strip("'\"") if m else None


def resolve(files, rel, depth=0):
    text = files[rel]
    out = {k: field(text, k) for k in KEYS}
    ext = field(text, "extends")
    if ext and depth < 6:
        base_rel = os.path.normpath(os.path.join(os.path.dirname(rel), ext)).replace(os.sep, "/")
        if base_rel not in files:
            cand = [r for r in files if r.endswith("/" + ext) or r == ext]
            base_rel = cand[0] if cand else None
        if base_rel:
            base = resolve(files, base_rel, depth + 1)
            for k in KEYS:
                if out[k] is None:
                    out[k] = base[k]
    return out


def main():
    root = sys.argv[1]
    files = load(root)
    rows = []
    for rel in sorted(files):
        r = resolve(files, rel)
        r["path"] = rel
        r["abstract"] = r["entityType"] is None
        rows.append(r)
    if len(sys.argv) > 2:
        json.dump(rows, open(sys.argv[2], "w"), indent=0)
    usable = [r for r in rows if r["entityType"] and not r["customModel"] and not r["disguise"] and r["isEnabled"] != "false"]
    print(len(rows), "files,", len(usable), "usable (own entity type, no disguise, no model)")
    print("with custom model:", sum(1 for r in rows if r["customModel"]), " with disguise:", sum(1 for r in rows if r["disguise"]),
          " without type (templates):", sum(1 for r in rows if not r["entityType"]))
    c = collections.Counter(r["entityType"] for r in usable)
    for k, v in c.most_common():
        print(v, k)


if __name__ == "__main__":
    main()
