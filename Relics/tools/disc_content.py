"""disc_content.py - contenude forensics: welke clip zit er echt in welke ogg?

Duur alleen bewijst niets (make_discs meet de seconden uit het bestand zelf).
Dus: decodeer elke bron en elke pakkett-ogg naar 8 kHz mono, maak van beide
een RMS-omhulsel van 100 ms-bakken en vind per ogg de best-matching bron via
genormaliseerde kruiscorrelatie (met shift-tolerantie). Ghosts telt dubbel:
de wav in discs/ is een langere master (225 s), de disc-ogg is 207 s van de
korte mp3-master - daarom matcht de ogg op de EERSTE 207 s van de wav.
"""
import os
import subprocess
import zipfile

import math
import array

FFMPEG = r"C:\Users\baaye\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.2-full_build\bin\ffmpeg.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # uit Relics/tools naar repo-root
import glob as _glob
_paks = sorted(_glob.glob(os.path.join(ROOT, "deploy", "iekcorbar-pack-build*.zip")),
               key=lambda p: int("".join(c for c in os.path.basename(p) if c.isdigit())))
PAK = _paks[-1]  # nieuwste build
TMP = os.path.join(os.environ["TEMP"], "opencode", "discwave")
os.makedirs(TMP, exist_ok=True)

def pcm8k(pad):
    out = os.path.join(TMP, os.path.basename(pad).replace(" ", "_") + ".raw")
    subprocess.run([FFMPEG, "-y", "-v", "error", "-i", pad, "-ac", "1", "-ar", "8000",
                    "-f", "s16le", out], check=True)
    a = array.array("h")
    with open(out, "rb") as f:
        a.frombytes(f.read())
    return a

def omhulsel(a, bak=800):
    v = []
    for i in range(0, len(a) - bak, bak):
        s = 0
        for j in range(i, i + bak, 8):          # elke 8e sample is genoeg voor RMS-trend
            x = a[j]
            s += x * x
        v.append(math.log1p(math.sqrt(s / (bak / 8))))
    gem = sum(v) / len(v)
    sd = math.sqrt(sum((x - gem) ** 2 for x in v) / len(v)) or 1
    return [(x - gem) / sd for x in v]

def beste_match(ogg, bron, max_shift=40):
    A, B = omhulsel(ogg), omhulsel(bron)
    n = min(len(A), len(B), 2200)
    A, B = A[:n], B[:n]
    best, bs = -9, 0
    for shift in range(-max_shift, max_shift):
        t = sum(A[i] * B[i + shift] for i in range(n - max_shift - abs(shift))
                if 0 <= i + shift < n) / (n - max_shift) if n > max_shift else 0
        if t > best:
            best, bs = t, shift
    return best

# 1) pak de 5 u_-ogg's uit de pack
z = zipfile.ZipFile(PAK)
ogg = {}
for n in z.namelist():
    b = os.path.basename(n)
    if b.startswith("u_") and b.endswith(".ogg"):
        p = os.path.join(TMP, b)
        open(p, "wb").write(z.read(n))
        ogg[b[2:-4]] = pcm8k(p)

# 2) decodeer de bronnen (.oud meenemen als OLD-controle)
bron = {}
d_dir = os.path.join(ROOT, "Relics", "discs")
for f in sorted(os.listdir(d_dir)):
    laage = f.lower()
    if laage.endswith((".mp3", ".wav", ".m4a", ".ogg")):
        stem = os.path.splitext(f)[0].lower().replace(" ", "_").replace("(1)", "").strip("_")
        bron[stem] = pcm8k(os.path.join(d_dir, f))
    elif laage.endswith(".oud"):
        stem = os.path.splitext(os.path.splitext(f)[0])[0].lower().replace(" ", "_") + "_OUD"
        bron[stem] = pcm8k(os.path.join(d_dir, f))

print("ogg (pack)                     <-> top-2-match                  score")
for oid, oa in sorted(ogg.items()):
    r = sorted(((beste_match(oa, ba), bid) for bid, ba in bron.items()), reverse=True)
    top, alt = r[0], (r[1] if len(r) > 1 else (0.0, "-"))
    vlag = "OK" if top[1] == oid else ">>> VERKEERD: bevat [{}]".format(top[1])
    print("  u_{:30s} 1e:{:28s} {:.2f} | 2e:{:28s} {:.2f}   {}".format(
        oid, top[1], top[0], alt[1], alt[0], vlag))
