"""Quest voices: echte gesproken tekst voor quest-NPC's (30-10).

Leest sounds/voices/<npc>.txt - regels van vorm   id|tekst  - laat elke regel
spreken door een gratis Edge-neurale stem (internet nodig op deze laptop),
zet ze om naar mono 44.1 kHz ogg (Minecraft-formaat) en REGISTREERT de events
in sounds/out/steamworks/sounds.json als  <npc>.<id>  (bv. milo.line01).

Volgorde-waarschuwing (audio-craft): make_sounds.py schrijft sounds.json
elk keer vers - dus eerst make_sounds.py, DAARNA pas dit script, dan de
packbouwer. Dit script is idempotent: opnieuw draaien vervangt dezelfde
events, rest blijft staan.

Gebruik (repo-root):  python -X utf8 sounds/make_voices.py [npc ...]
Zonder argument: alle <npc>.txt in sounds/voices/.
Vereist: pip install edge-tts (gebruikers-site, draai ZONDER -I) + ffmpeg.
"""
import asyncio
import glob
import io
import json
import os
import subprocess
import sys

import edge_tts

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
FFMPEG = r"C:\Users\baaye\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.2-full_build\bin\ffmpeg.exe"
TMP = os.path.join(os.environ["TEMP"], "opencode")

# Stem per quest-NPC. Kies uit: edge-tts --list-voices | findstr /i neural
# Milo: Ierse handelaar. Machine: vlakke Amerikaanse man. future: en-GB-SoniaNeural (Petra?)
VOICES = {
    "milo": "en-IE-ConnorNeural",
    "machine": "en-US-GuyNeural",
}
NS = "steamworks"          # events leven in dit namespace (pack al present)


def read_lines(npc_file):
    out = []
    for raw in io.open(npc_file, encoding="utf-8"):
        raw = raw.strip()
        if not raw or raw.startswith("#"):
            continue
        lid, _, text = raw.partition("|")
        if text.strip():
            out.append((lid.strip(), text.strip()))
    return out


async def say(text, voice, mp3):
    await edge_tts.Communicate(text, voice).save(mp3)


def run(npcs):
    events = {}
    for npc in npcs:
        f = os.path.join(HERE, "voices", npc + ".txt")
        if not os.path.isfile(f):
            print("no such npc file:", f)
            continue
        voice = VOICES.get(npc, "en-US-AndrewNeural")
        dst = os.path.join(OUT, NS, "sounds", npc)
        os.makedirs(dst, exist_ok=True)
        for lid, text in read_lines(f):
            mp3 = os.path.join(TMP, f"{npc}_{lid}.mp3")
            asyncio.run(say(text, voice, mp3))
            ogg = os.path.join(dst, lid + ".ogg")
            subprocess.run([FFMPEG, "-y", "-v", "error", "-i", mp3,
                            "-c:a", "libvorbis", "-ac", "1", "-ar", "44100", "-q:a", "4", ogg],
                           check=True)
            events[f"{npc}.{lid}"] = {"sounds": [f"{NS}:{npc}/{lid}"]}
            print(f"  {npc}.{lid}: {os.path.getsize(ogg)//1024} KB")
    if not events:
        return
    j = os.path.join(OUT, NS, "sounds.json")
    data = json.loads(io.open(j, encoding="utf-8").read()) if os.path.isfile(j) else {}
    data.update(events)
    with io.open(j, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
    print(f"registered {len(events)} voice events in {NS}/sounds.json")


if __name__ == "__main__":
    names = sys.argv[1:] or sorted(
        os.path.basename(p)[:-4] for p in glob.glob(os.path.join(HERE, "voices", "*.txt")))
    print("voices:", ", ".join(names))
    run(names)
