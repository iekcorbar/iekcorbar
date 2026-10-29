# Robuuste Java constant pool reader: strings + class-referenties per class.
import struct, zipfile

# Algemeen hulpmiddel: lees strings + class-referenties uit .class-bestanden (Java constant pool),
# voor REA-sessies op plain-Java Archives: python -I scripts\java-constantpool.py <jar> [klasse-regex ...]
import sys
JAR = sys.argv[1] if len(sys.argv) > 1 else "Cinematic Interaction-Fabric-26.3-0.18.30.jar"
z = zipfile.ZipFile(JAR)
TARGETS = [
    "dev/dyslexic/cinematic/Cinematic.class",
    "dev/dyslexic/cinematic/net/Network.class",
    "dev/dyslexic/cinematic/net/ProtocolPayload.class",
    "dev/dyslexic/cinematic/net/StationActionPayload.class",
    "dev/dyslexic/cinematic/net/StationActionPayload$Action.class",
    "dev/dyslexic/cinematic/station/StationKind.class",
    "dev/dyslexic/cinematic/api/AddonStations.class",
    "dev/dyslexic/cinematic/api/AddonStations$Definition.class",
]

def parse(buf):
    assert buf[:4] == b"\xca\xfe\xba\xbe", "geen class"
    p = 8
    count = struct.unpack(">H", buf[p:p+2])[0]; p += 2
    utf8 = {}
    class_refs = []   # (tag=7) utf8 index
    str_refs = []     # (tag=8) utf8 index
    i = 1
    while i < count:
        tag = buf[p]; p += 1
        if tag == 1:
            ln = struct.unpack(">H", buf[p:p+2])[0]; p += 2
            utf8[i] = buf[p:p+ln].decode("utf-8", "replace"); p += ln
        elif tag == 7:
            class_refs.append(struct.unpack(">H", buf[p:p+2])[0]); p += 2
        elif tag == 8:
            str_refs.append(struct.unpack(">H", buf[p:p+2])[0]); p += 2
        elif tag in (16, 19, 20):
            p += 2
        elif tag == 15:
            p += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            p += 4
        elif tag in (5, 6):
            p += 8; i += 1
        else:
            raise ValueError(f"tag {tag} bij entry {i}")
        i += 1
    classes = sorted({utf8.get(v, f"?{v}") for v in class_refs})
    strs = [utf8.get(v, f"?{v}") for v in str_refs]
    return utf8, classes, strs

for name in TARGETS:
    if name not in z.namelist():
        print(f"\n== {name.split('/')[-1]} == (NIET GEVONDEN)"); continue
    utf8, classes, strs = parse(z.read(name))
    simple = name.split("/")[-1]
    print(f"\n== {simple} ==")
    print("  strings:", strs[:20])
    interesting = [c for c in classes if any(s in c.lower() for s in ("payload", "regist", "cinematic", "network", "container", "screen", "menu", "station", "id"))]
    print("  class-refs:", sorted(set(interesting))[:16])
