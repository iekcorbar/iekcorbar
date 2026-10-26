"""Audit a pack zip: every /models/*.json element coordinate must be within -16..32 (MC 26.2 hard limit)."""
import json, sys, zipfile, hashlib

path = sys.argv[1]
print("sha1", hashlib.sha1(open(path, "rb").read()).hexdigest()[:12])
bad = {}
total = 0
with zipfile.ZipFile(path) as z:
    for name in z.namelist():
        if "/models/" not in name or not name.endswith(".json"):
            continue
        total += 1
        try:
            m = json.loads(z.read(name))
        except ValueError:
            continue
        els = m.get("elements")
        if not isinstance(els, list):
            continue
        for e in els:
            for key in ("from", "to"):
                v = e.get(key)
                if isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v):
                    for ax, x in enumerate(v):
                        if x < -16.0001 or x > 32.0001:
                            bad.setdefault(name, []).append("%s[%d]=%s" % (key, ax, x))
print("models scanned:", total)
print("out-of-range models:", len(bad))
for name, offs in sorted(bad.items()):
    print(" ", name, "->", ", ".join(offs))
