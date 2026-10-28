"""Disk-forensics: welke ogg is welke song?

De keten op papier (config id -> event -> geluidsbestand) is consistent.
Dus als de namen in het spel niet kloppen, staat er de verkeerde CONTENT in
een bestandsnaam. Dit script meet de duur van elke ogg in de pack en vergelijkt
met de bronnen en de config - duur is de vingerafdruk (31/40/41/86/108/130/132/133/208).
"""
import io
import json
import os
import re
import subprocess
import zipfile

FFPROBE = r"C:\Users\baaye\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.2-full_build\bin\ffprobe.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # repo-root (stapt uit Relics/tools)
PAK = os.path.join(ROOT, "deploy", "iekcorbar-pack-build13.zip")
TMP = os.path.join(os.environ["TEMP"], "opencode", "discklanten")
os.makedirs(TMP, exist_ok=True)

def duur(p):
    out = subprocess.run([FFPROBE, "-v", "error", "-show_entries", "format=duration",
                          "-of", "csv=p=0", p], capture_output=True, text=True)
    try:
        return round(float(out.stdout.strip()), 1)
    except ValueError:
        return None

# 1) config: id -> seconden
cfg = io.open(os.path.join(ROOT, "Relics", "src", "main", "resources", "config.yml"), encoding="utf-8").read()
blok = cfg[cfg.find("discs:"):cfg.find("# END:generated")]
verwacht = {m.group(1): int(m.group(2)) for m in
            re.finditer(r"  ([a-z_]+):\n\s+title: .+\n\s+seconds: (\d+)", blok)}
print("config seconden:", verwacht)

# 2) sounds.json uit de pack: event -> bestand
z = zipfile.ZipFile(PAK)
sj = json.loads(z.read("assets/relics/sounds.json").decode("utf-8"))
print("\nevent -> bestand (uit de pack):")
for k in sorted(sj):
    if k.startswith("disc.") or k.startswith("music_box."):
        s = sj[k]["sounds"][0]
        naam = s["name"] if isinstance(s, dict) else s
        print(f"  {k:38s} -> {naam}")

# 3) pak de ogg's uit en meet
print("\nmeten:")
for n in z.namelist():
    if "/sounds/disc/" in n or "/sounds/musicbox/" in n:
        best = os.path.join(TMP, os.path.basename(n))
        with open(best, "wb") as f:
            f.write(z.read(n))
        d = duur(best)
        did = os.path.basename(n).replace("u_", "").replace(".ogg", "").replace("_1", "")
        w = verwacht.get(did)
        vlag = "" if w is None else ("  OK" if abs((d or 0) - w) <= 2 else f"  >>> OMWISSELD? config {did} = {w}s, bestand {d}s")
        print(f"  {os.path.basename(n):36s} {str(d):>7s}s {vlag}")

# 4) bronnen meten (de 5 externe platen)
print("\nbronnen in Relics/discs:")
for f in sorted(os.listdir(os.path.join(ROOT, "Relics", "discs"))):
    if f.lower().endswith((".mp3", ".wav", ".m4a", ".ogg")):
        print(f"  {f:36s} {duur(os.path.join(ROOT, 'Relics', 'discs', f)):>7s}s")
