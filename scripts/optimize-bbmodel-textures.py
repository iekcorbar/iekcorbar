"""Losslessly recompresses the PNG textures embedded in .bbmodel files (FreeMinecraftModels copies one
texture per model into the resource pack, so every byte saved here is multiplied by the model count).
Pixels stay identical (checked); only the PNG filter/compression changes.
Run WITHOUT -I (Pillow lives in the user site):  python scripts/optimize-bbmodel-textures.py <folder> [<folder> ...]"""
import base64, io, json, os, re, sys
from PIL import Image

PREFIX = "data:image/png;base64,"
saved = files = changed = 0
cache = {}


def optimize(b64):
    if b64 in cache:
        return cache[b64]
    raw = base64.b64decode(b64)
    im = Image.open(io.BytesIO(raw))
    im.load()
    out = io.BytesIO()
    im.save(out, "PNG", optimize=True)
    new = out.getvalue()
    chk = Image.open(io.BytesIO(new))
    chk.load()
    if chk.mode != im.mode or chk.size != im.size or chk.tobytes() != im.tobytes() or len(new) >= len(raw):
        cache[b64] = b64
    else:
        cache[b64] = base64.b64encode(new).decode()
    return cache[b64]


for root in sys.argv[1:]:
    for dp, _, fs in os.walk(root):
        for f in fs:
            if not f.endswith(".bbmodel"):
                continue
            p = os.path.join(dp, f)
            text = open(p, encoding="utf-8").read()
            files += 1
            n = re.sub(re.escape(PREFIX) + r"([A-Za-z0-9+/=]+)", lambda m: PREFIX + optimize(m.group(1)), text)
            if n != text:
                json.loads(n)
                open(p, "w", encoding="utf-8", newline="").write(n)
                saved += len(text) - len(n)
                changed += 1
print("models", files, "changed", changed, "saved (base64 bytes)", saved)
