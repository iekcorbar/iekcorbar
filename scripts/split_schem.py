# split_schem.py - knipt een grote Sponge-v2 .schem uiteen in losse gebouwen.
# Analyseren:  python -I scripts/split_schem.py analyze <in.schem> [--margin=2] [--min=30] [--keep-ground]
# Knippen:     python -I scripts/split_schem.py split   <in.schem> <outdir> [--prefix=kerstdorp] [--margin=2] [--min=30] [--keep-ground]
#
# Werking: bezetheidsgrid (alles behalve lucht), samenhangende brokken via
# 26-zijdeling-BFS, overlappende boxen (met marge) worden samengevoegd,
# "grondslappen" (lagen >85% vol) vallen buiten de koppeling (tenzij
# --keep-ground), en BlockEntities/Entities reizen mee naar het stuk waarin
# hun coordinaten vallen. Formaatloggelei (varint-array, y/z/x-volgorde,
# Palette compound) volgt scripts/make_trailer_schems.py, maar leest dan wel
# echte doubles zodat entiteiten behouden blijven.
import gzip, os, re, struct, sys

T_END, T_BYTE, T_SHORT, T_INT, T_LONG, T_FLOAT, T_DOUBLE, T_BYTES, T_STR, T_LIST, T_COMPOUND, T_INTS, T_LONGS = range(13)


# ------------------------------------------------------------------ NBT readers
def _rstr(f):
    (n,) = struct.unpack(">H", f.read(2))
    return f.read(n).decode("utf-8", "surrogateescape")


def _rread(f, tt):
    if tt == T_BYTE: return struct.unpack(">b", f.read(1))[0]
    if tt == T_SHORT: return struct.unpack(">h", f.read(2))[0]
    if tt == T_INT: return struct.unpack(">i", f.read(4))[0]
    if tt == T_LONG: return struct.unpack(">q", f.read(8))[0]
    if tt == T_FLOAT: return struct.unpack(">f", f.read(4))[0]
    if tt == T_DOUBLE: return struct.unpack(">d", f.read(8))[0]
    if tt == T_BYTES:
        (n,) = struct.unpack(">i", f.read(4)); return f.read(n)
    if tt == T_STR: return _rstr(f)
    if tt == T_INTS:
        (n,) = struct.unpack(">i", f.read(4)); return list(struct.unpack(">" + "i" * n, f.read(4 * n)))
    if tt == T_LONGS:
        (n,) = struct.unpack(">i", f.read(4)); return list(struct.unpack(">" + "q" * n, f.read(8 * n)))
    if tt == T_LIST:
        st = f.read(1)[0]; (n,) = struct.unpack(">i", f.read(4))
        return [_rread(f, st) for _ in range(n)]
    if tt == T_COMPOUND:
        d = {}
        while True:
            t = f.read(1)[0]
            if t == T_END: break
            naam = _rstr(f)            # LET OP: twee regels - Python evalueert bij
            d[naam] = _rread(f, t)     # d[k]=v eerst de RHS, anders raakt de stream bij
        return d                        # nested compounds volledig uit zijn voegen
    raise ValueError("onbekend tagtype %s" % tt)


def laad(pad):
    with gzip.open(pad) as f:
        (t,) = struct.unpack(">B", f.read(1)); assert t == T_COMPOUND
        root = _rstr(f); d = _rread(f, T_COMPOUND)
    assert root == "Schematic" and d["Version"] == 2, "alleen Sponge v2 (.schem)"
    w, h, l = d["Width"], d["Height"], d["Length"]
    pal = sorted(d["Palette"].items(), key=lambda kv: kv[1])
    names = [k for k, _ in pal]
    air = names.index("minecraft:air") if "minecraft:air" in names else 0
    flat = []
    data = d["BlockData"]
    i = 0
    while len(flat) < w * h * l:
        shift = val = 0
        while True:
            b = data[i]; i += 1
            val |= (b & 0x7F) << shift
            if not b & 0x80: break
            shift += 7
        flat.append(val)
    return dict(w=w, h=h, l=l, names=names, air=air, flat=flat,
                bes=d.get("BlockEntities") or [], ents=d.get("Entities") or [],
                dv=d.get("DataVersion", 3337))


# ------------------------------------------------------------------ NBT writers
def _s(x):
    b = x.encode("utf-8"); return struct.pack(">H", len(b)) + b


def w_comp(items):
    return b"".join(struct.pack(">B", tt) + _s(n) + p for n, tt, p in items) + b"\x00"


def int_array(vals):
    return struct.pack(">i", len(vals)) + b"".join(struct.pack(">i", v) for v in vals)


def varint(n):
    out = b""
    while True:
        b = n & 0x7F; n >>= 7
        if n: out += bytes([b | 0x80])
        else: return out + bytes([b])


def comp_body(d):
    """compound-dict -> NBT-veldbody; generiek genoeg voor BE/Entity-resten."""
    out = b""
    for k, v in d.items():
        if k == "xyz" or (isinstance(v, list) and len(v) == 3 and all(type(i) is int for i in v) and k in ("Pos",)):
            pass  # Pos is vaak floats; apart behandeld hieronder
        if isinstance(v, bool):
            out += struct.pack(">B", T_BYTE) + _s(k) + struct.pack(">b", int(v))
        elif isinstance(v, int):
            out += struct.pack(">B", T_INT) + _s(k) + struct.pack(">i", v)
        elif isinstance(v, float):
            out += struct.pack(">B", T_DOUBLE) + _s(k) + struct.pack(">d", v)
        elif isinstance(v, str):
            out += struct.pack(">B", T_STR) + _s(k) + _s(v)
        elif isinstance(v, bytes):
            out += struct.pack(">B", T_BYTES) + _s(k) + struct.pack(">i", len(v)) + v
        elif isinstance(v, list) and v and all(type(i) is int for i in v) and len(v) in (3, 4):
            out += struct.pack(">B", T_INTS) + _s(k) + int_array(v)
        elif isinstance(v, list):
            if not v:
                out += struct.pack(">B", T_LIST) + _s(k) + struct.pack(">Bi", 0, 0)
            elif all(type(i) in (int, float) for i in v):
                tt = T_DOUBLE if any(type(i) is float for i in v) else T_INT
                fmt = ">d" if tt == T_DOUBLE else ">i"
                out += struct.pack(">B", T_LIST) + _s(k) + struct.pack(">Bi", tt, len(v))
                out += b"".join(struct.pack(fmt, tt == T_DOUBLE and float(i) or i) for i in v)
            elif all(isinstance(i, str) for i in v):
                out += struct.pack(">B", T_LIST) + _s(k) + struct.pack(">Bi", T_STR, len(v))
                out += b"".join(_s(i) for i in v)
            elif all(isinstance(i, dict) for i in v):
                out += struct.pack(">B", T_LIST) + _s(k) + struct.pack(">Bi", T_COMPOUND, len(v))
                out += b"".join(comp_body(i) for i in v)
            else:
                out += struct.pack(">B", T_LIST) + _s(k) + struct.pack(">Bi", 0, 0)
        elif isinstance(v, dict):
            out += struct.pack(">B", T_COMPOUND) + _s(k) + comp_body(v)
    return out + b"\x00"


# ------------------------------------------------------------------ kniplogica
def bezet_punten(s):
    w, h, l = s["w"], s["h"], s["l"]
    air = s["air"]
    occ = set()
    iy = 0
    for y in range(h):
        for z in range(l):
            row = s["flat"][y * l * w + z * w: y * l * w + z * w + w]
            for x, v in enumerate(row):
                if v != air:
                    occ.add((x, y, z))
    return occ


def brokken(s, margin, minb, keep_ground):
    w, h, l = s["w"], s["h"], s["l"]
    occ = bezet_punten(s)
    grond = set()
    if not keep_ground:
        for y in range(h):
            n = sum(1 for x in range(w) for z in range(l) if (x, y, z) in occ)
            if n > 0.85 * w * l:
                grond |= {(x, y, z) for x in range(w) for z in range(l) if (x, y, z) in occ}
    werk = occ - grond
    buren = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]
    seen, comps = set(), []
    for start in werk:
        if start in seen: continue
        stapel, lid = [start], []
        seen.add(start)
        while stapel:
            p = stapel.pop(); lid.append(p)
            x, y, z = p
            for dx, dy, dz in buren:
                nx, ny, nz = x + dx, y + dy, z + dz
                if 0 <= nx < w and 0 <= ny < h and 0 <= nz < l and (nx, ny, nz) in werk and (nx, ny, nz) not in seen:
                    seen.add((nx, ny, nz)); stapel.append((nx, ny, nz))
        if len(lid) >= minb:
            comps.append(lid)
    boxes = []
    top_grond = max((y for (_x, y, _z) in grond), default=-1)  # diepe kant: geen bodem-meepsiten
    for lid in comps:
        xs = [p[0] for p in lid]; ys = [p[1] for p in lid]; zs = [p[2] for p in lid]
        boxes.append([max(0, min(xs) - margin), max(top_grond + 1, min(ys) - margin), max(0, min(zs) - margin),
                      min(w - 1, max(xs) + margin), min(h - 1, max(ys) + margin), min(l - 1, max(zs) + margin), set(lid)])
    gewijzigd = True
    while gewijzigd:                                   # overlappende boxen samensmelten
        gewijzigd = False
        i = 0
        while i < len(boxes):
            j = i + 1
            while j < len(boxes):
                a, b = boxes[i], boxes[j]
                if a[0] <= b[3] and b[0] <= a[3] and a[1] <= b[4] and b[1] <= a[4] and a[2] <= b[5] and b[2] <= a[5]:
                    boxes[i] = [min(a[0], b[0]), min(a[1], b[1]), min(a[2], b[2]),
                                max(a[3], b[3]), max(a[4], b[4]), max(a[5], b[5]), a[6] | b[6]]
                    boxes.pop(j); gewijzigd = True
                else:
                    j += 1
            i += 1
    return boxes, grond


def bes_coord(bes):
    if "xyz" in bes: return bes["xyz"][0], bes["xyz"][1], bes["xyz"][2]
    return int(bes.get("x", 0)), int(bes.get("y", 0)), int(bes.get("z", 0))


def ent_pos(ent):
    p = ent.get("Pos")
    if not p or len(p) < 3: return None
    return int(p[0]), int(p[1]), int(p[2])


def schrijf(pad, s, b, naam, omschrijving):
    w, h, l = s["w"], s["h"], s["l"]
    dw, dh, dl = b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1
    data = b""
    for y in range(b[1], b[4] + 1):
        for z in range(b[2], b[5] + 1):
            for x in range(b[0], b[3] + 1):
                data += varint(s["flat"][y * l * w + z * w + x])
    pal = [(n, T_INT, struct.pack(">i", i)) for i, n in enumerate(s["names"])]
    bes_out = []
    for bes in s["bes"]:
        x, y, z = bes_coord(bes)
        if b[0] <= x <= b[3] and b[1] <= y <= b[4] and b[2] <= z <= b[5]:
            nb = dict(bes)
            if "xyz" in nb: nb["xyz"] = [x - b[0], y - b[1], z - b[2]]
            else: nb["x"], nb["y"], nb["z"] = x - b[0], y - b[1], z - b[2]
            bes_out.append(nb)
    ents_out = []
    for ent in s["ents"]:
        c = ent_pos(ent)
        if c and b[0] <= c[0] <= b[3] and b[1] <= c[1] <= b[4] and b[2] <= c[2] <= b[5]:
            ne = dict(ent)
            p = ne["Pos"]
            ne["Pos"] = [p[0] - b[0], p[1] - b[1], p[2] - b[2]]
            ents_out.append(ne)
    bes_bytes = struct.pack(">Bi", T_COMPOUND, len(bes_out)) + b"".join(comp_body(x) for x in bes_out)
    ent_bytes = struct.pack(">Bi", T_COMPOUND, len(ents_out)) + b"".join(comp_body(x) for x in ents_out)
    meta = [("name", T_STR, _s(naam)), ("Description", T_STR, _s(omschrijving)),
            ("WEOffsetX", T_INT, struct.pack(">i", 0)), ("WEOffsetY", T_INT, struct.pack(">i", 0)),
            ("WEOffsetZ", T_INT, struct.pack(">i", 0))]
    body = w_comp([
        ("Version", T_INT, struct.pack(">i", 2)),
        ("DataVersion", T_INT, struct.pack(">i", s["dv"])),
        ("Width", T_SHORT, struct.pack(">h", dw)),
        ("Height", T_SHORT, struct.pack(">h", dh)),
        ("Length", T_SHORT, struct.pack(">h", dl)),
        ("Offset", T_INTS, int_array([0, 0, 0])),
        ("BlockData", T_BYTES, struct.pack(">i", len(data)) + data),
        ("PaletteMax", T_INT, struct.pack(">i", len(s["names"]))),
        ("Palette", T_COMPOUND, w_comp(pal)),
        ("BlockEntities", T_LIST, bes_bytes),
        ("Entities", T_LIST, ent_bytes),
        ("Metadata", T_COMPOUND, w_comp(meta)),
    ])
    with open(pad, "wb") as f:
        f.write(gzip.compress(struct.pack(">B", T_COMPOUND) + _s("Schematic") + body))
    return dw, dh, dl


def main():
    args = sys.argv[1:]
    wat, src = args[0], args[1]
    opt = " ".join(args)
    margin = int(re.search(r"--margin=(\d+)", opt).group(1)) if "--margin=" in opt else 2
    minb = int(re.search(r"--min=(\d+)", opt).group(1)) if "--min=" in opt else 30
    s = laad(src)
    boxes, grond = brokken(s, margin, minb, "--keep-ground" in opt)
    if wat == "analyze":
        print("bron: %dx%dx%d  palette %d  bezet %d  BE %d  ents %d" %
              (s["w"], s["h"], s["l"], len(s["names"]), sum(len(b[6]) for b in boxes) + len(grond), len(s["bes"]), len(s["ents"])))
        if grond: print("grondslaap overgeslagen: %d cellen in lagen >85%% vol" % len(grond))
        print("brokken (marge %d, min %d): %d" % (margin, minb, len(boxes)))
        for i, b in enumerate(sorted(boxes, key=lambda x: -len(x[6])), 1):
            print("  %02d  %3dx%3dx%3d  %6d blokken  hoek (%d,%d,%d)" %
                  (i, b[3]-b[0]+1, b[4]-b[1]+1, b[5]-b[2]+1, len(b[6]), b[0], b[1], b[2]))
        return
    out = args[2]
    prefix = re.search(r"--prefix=([\w-]+)", opt)
    prefix = prefix.group(1) if prefix else "deel"
    os.makedirs(out, exist_ok=True)
    for i, b in enumerate(sorted(boxes, key=lambda x: -len(x[6])), 1):
        naam = "%s-%02d" % (prefix, i)
        afm = (b[3]-b[0]+1, b[4]-b[1]+1, b[5]-b[2]+1)
        schrijf(os.path.join(out, naam + ".schem"), s, b, naam,
                "%s: %dx%dx%d, %d blokken" % (naam, afm[0], afm[1], afm[2], len(b[6])))
        print("wrote %s.schem  %dx%dx%d" % (naam, *afm))
    print("KLAAR:", len(boxes), "losse files ->", out)


if __name__ == "__main__":
    main()
