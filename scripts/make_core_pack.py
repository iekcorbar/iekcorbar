"""Core-pack: iekcorbar-eigen items die aan geen plugin-bouwer toebehoren.

Nu: het QUESTBOEK. Het BetonQuest-journaal draagt custom_model_data 101
(BetonQuest/config.yml journal.custom_model_data) en deze pack hangt aan alle
written_books met custom_model_data[0] >= 100 een rode lederen tome met
messing hoeken en slot aan. Andere boeken (gewone written books,
/ibook-gidsen) houden hun vanilla-model - de override kijgt alleen een CMD van
100 of hoger.

Let op (audit): alle uv-waarden blijven binnen 0..32 - de textuur is 32x32,
vierkwadranten: leer TL, pagina's TR, messing BL, rug BR.

Ritueel (skill pack-build): python -I scripts/make_core_pack.py  ->
scripts/core-pack/Core-pack.zip  -> BUILD verhogen in merge-packs.py ->
merge -> audit -> sha1 -> TE-DEPLOYEN-regel.
"""
import glob
import json
import os
import random
import struct
import subprocess
import tempfile
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "core-pack", "Core-pack.zip")
SR = random.Random(20261030)
ROOT = os.path.dirname(HERE)
FFMPEG = os.environ.get("FFMPEG") or (glob.glob(os.path.expandvars(
    r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_*\ffmpeg-*\bin\ffmpeg.exe")) or ["ffmpeg"])[0]
# Globale muziek: ons eigen overworld-nummer vervangt de vanilla-muziekevents.
# deep_dark blijft vanilla (de stilte/griezel daar is bedoeld). Onbekende
# eventnamen in een nieuwere versie doen niets; nieuwe biomen later toevoegen.
MUSIC_SRC = os.path.join(ROOT, "sounds", "music", "overworld.mp3")
END_SRC = os.path.join(ROOT, "sounds", "music", "end.mp3")
SPAWN_SRC = os.path.join(ROOT, "sounds", "music", "spawn.mp3")
SPAWN_XFADE = 5.0  # s: staart en kop worden in elkaar gelegd, zodat de repeat geen naad heeft
MUSIC_EVENTS = ["music.game", "music.creative", "music.under_water", "music.end", "music.dragon"] + [
    "music.nether." + b for b in ("nether_wastes", "crimson_forest", "soul_sand_valley",
                                   "warped_forest", "basalt_deltas")] + [
    "music.overworld." + b for b in (
        "meadow", "jungle_and_forest", "old_growth_taiga", "snowy_slopes",
        "frozen_peaks", "jagged_peaks", "stony_peaks", "flower_forest", "grove",
        "swamp", "cherry_grove", "lush_caves", "dripstone_caves")]
SZ = 32


def px(r, g, b, a=255):
    return (r & 255, g & 255, b & 255, a)


def build_texture():
    img = [[px(0, 0, 0, 0) for _ in range(SZ)] for _ in range(SZ)]

    def rect(x0, y0, x1, y1, col):
        for y in range(y0, y1):
            for x in range(x0, x1):
                img[y][x] = col

    def frame(x0, y0, x1, y1, col):
        for x in range(x0, x1):
            img[y0][x] = col
            img[y1 - 1][x] = col
        for y in range(y0, y1):
            img[y][x0] = col
            img[y][x1 - 1] = col

    # LEER TL (0..16 x 0..16): warm rood, donkere rand + stiksel
    for y in range(16):
        for x in range(16):
            edge = x < 1 or y < 1 or x > 14 or y > 14
            img[y][x] = px(96 if edge else 142 + SR.randint(-8, 8),
                           20 if edge else 25 + SR.randint(0, 5),
                           12 if edge else 14 + SR.randint(0, 6))
    frame(2, 2, 14, 14, px(70, 14, 14))
    for i in range(3, 13, 2):
        img[2][i] = img[13][i] = img[i][2] = img[i][13] = px(210, 180, 120)
    # PAGINA'S TR (16..32 x 0..16): crème met papierstrepen
    for y in range(16):
        for x in range(16, 32):
            img[y][x] = px(208, 196, 158) if (y % 3) == 0 else px(236, 226, 192)
    frame(16, 0, 32, 16, px(170, 158, 120))
    # MESSING BL (0..16 x 16..32): diagonale glans + klinknagels
    for y in range(16, 32):
        for x in range(16):
            t = (x + y - 16) / 30.0
            img[y][x] = px(int(120 + 90 * t), int(88 + 70 * t), int(26 + 30 * t))
    for cx, cy in ((2, 18), (13, 18), (2, 29), (13, 29)):
        img[cy][cx] = px(235, 205, 125)
    # RUG BR (16..32 x 16..32): donker leer + gouden titelband
    for y in range(16, 32):
        for x in range(16, 32):
            img[y][x] = px(88 + SR.randint(-5, 5), 16, 12 + SR.randint(-3, 3))
    rect(19, 22, 29, 25, px(196, 156, 62))
    img[23][22] = img[23][25] = px(235, 210, 130)
    return img


def png_bytes(img):
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in img)

    def ch(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)

    return (b"\x89PNG\r\n\x1a\n"
            + ch(b"IHDR", struct.pack(">IIBBBBB", SZ, SZ, 8, 6, 0, 0, 0))
            + ch(b"IDAT", zlib.compress(raw, 9))
            + ch(b"IEND", b""))


# ---------------------------------------------------------------- model
LEATHER = [0, 0, 16, 16]
PAGES = [16, 0, 32, 16]
BRASS = [0, 16, 16, 32]
SPINE = [16, 16, 32, 32]


def box_el(name, frm, to, uv):
    faces = {f: {"uv": uv, "texture": "#tex"} for f in
             ("north", "east", "south", "west", "up", "down")}
    return {"name": name, "from": frm, "to": to, "faces": faces}


def build_model():
    els = [
        box_el("cover-onder", [1, 2, 2], [15, 3, 14], LEATHER),
        box_el("cover-boven", [1, 5, 2], [15, 6, 14], LEATHER),
        box_el("pagina-rand", [2.2, 3, 3], [13.6, 5, 13], PAGES),
        box_el("rug", [0.85, 1.95, 2.15], [2.15, 6.05, 13.85], SPINE),
        box_el("hoek-NO", [13.5, 5.6, 1.7], [15.3, 6.4, 3.5], BRASS),
        box_el("hoek-ZO", [13.5, 5.6, 12.5], [15.3, 6.4, 14.3], BRASS),
        box_el("slot-plaat", [5.5, 6, 5.5], [10.5, 6.35, 10.5], BRASS),
        box_el("slot-knop", [7.4, 6.35, 7.4], [8.6, 7.05, 8.6], BRASS),
        box_el("slot-riem", [14.0, 3.4, 6.9], [15.25, 5.6, 9.1], BRASS),
        box_el("slot-gesp", [13.5, 4.15, 7.5], [14.15, 4.85, 8.5], BRASS),
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {"tex": "iekcorbar:item/quest_book", "particle": "iekcorbar:item/quest_book"},
        "display": {
            "gui": {"rotation": [30, -45, 0], "translation": [0, 2, 0], "scale": [0.62, 0.62, 0.62]},
            "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "head": {"rotation": [0, 45, 0], "translation": [0, 4, 0], "scale": [0.9, 0.9, 0.9]},
            "thirdperson_righthand": {"rotation": [75, 100, 0], "translation": [0, 3, 1], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.65, 0.65, 0.65]},
            "fixed": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
        },
        "elements": els,
    }


def item_def():
    return {"model": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "fallback": {"type": "minecraft:model", "model": "minecraft:item/written_book"},
        "entries": [{"threshold": 100,
                     "model": {"type": "minecraft:model", "model": "iekcorbar:item/quest_book"}}],
    }}


def music_ogg(src=MUSIC_SRC, fade=0.0, dur=0.0):
    """mp3 -> stereo ogg (zonder albumhoes), q:a 3 ~ 112 kbps."""
    with tempfile.TemporaryDirectory() as td:
        out = os.path.join(td, "overworld.ogg")
        subprocess.run([FFMPEG, "-v", "error", "-y", "-i", src, "-vn", "-map_metadata", "-1",
                        *(["-af", "afade=t=in:d=%s,afade=t=out:st=%s:d=%s" % (fade, dur - fade, fade)] if fade else []),
                        "-c:a", "libvorbis", "-q:a", "3", out], check=True)
        with open(out, "rb") as f:
            return f.read()


def loop_ogg(src, x=SPAWN_XFADE):
    """Naadloos loop-bestand: eerste x s en laatste x s kruisen in elkaar (equal-power).
    Lengte = origineel - x. Het einde loopt vanzelf door in het begin."""
    with tempfile.TemporaryDirectory() as td:
        probe = subprocess.run([FFMPEG.replace("ffmpeg.exe", "ffprobe.exe"), "-v", "error", "-show_entries",
                                "format=duration", "-of", "csv=p=0", src], capture_output=True, text=True, check=True)
        d = float(probe.stdout.strip())
        out = os.path.join(td, "loop.ogg")
        fc = ("[0:a]asplit=3[a][b][c];"
              "[a]atrim=%s:%s,asetpts=PTS-STARTPTS,afade=t=out:d=%s:curve=qsin[t];"
              "[b]atrim=0:%s,asetpts=PTS-STARTPTS,afade=t=in:d=%s:curve=qsin[h];"
              "[t][h]amix=inputs=2:normalize=0:duration=longest[x];"
              "[c]atrim=%s:%s,asetpts=PTS-STARTPTS[m];"
              "[x][m]concat=n=2:v=0:a=1[o]") % (d - x, d, x, x, x, x, d - x)
        subprocess.run([FFMPEG, "-v", "error", "-y", "-i", src, "-vn", "-map_metadata", "-1",
                        "-filter_complex", fc, "-map", "[o]", "-c:a", "libvorbis", "-q:a", "4", out], check=True)
        with open(out, "rb") as f:
            return f.read()


def silence_ogg():
    """1 s stilte: vanilla-muziekevents verwijzen hiernaartoe, zodat vanilla niets meer speelt.
    Onze eigen timer (Skript iekcorbar_music.sk) speelt iekcorbar:music.overworld."""
    with tempfile.TemporaryDirectory() as td:
        out = os.path.join(td, "silence.ogg")
        subprocess.run([FFMPEG, "-v", "error", "-y", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo",
                        "-t", "1", "-c:a", "libvorbis", "-q:a", "0", out], check=True)
        with open(out, "rb") as f:
            return f.read()


def music_sounds_json():
    entry = {"replace": True, "sounds": [{"name": "iekcorbar:music/silence", "stream": True}]}
    return {ev: entry for ev in MUSIC_EVENTS}


def own_sounds_json():
    return {"music.overworld": {"sounds": [{"name": "iekcorbar:music/overworld", "stream": True}],
                                "subtitle": ""},
            "music.end": {"sounds": [{"name": "iekcorbar:music/end", "stream": True}],
                          "subtitle": ""},
            "music.spawn": {"sounds": [{"name": "iekcorbar:music/spawn", "stream": True}],
                            "subtitle": ""}}


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    mcmeta = {"pack": {"description": "iekcorbar core (quest book, overworld music)",
                       "pack_format": 64,
                       "supported_formats": {"min_inclusive": 1, "max_inclusive": 999}}}
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps(mcmeta, indent=1))
        z.writestr("assets/iekcorbar/textures/item/quest_book.png", png_bytes(build_texture()))
        z.writestr("assets/iekcorbar/models/item/quest_book.json", json.dumps(build_model(), indent=1))
        z.writestr("assets/minecraft/items/written_book.json", json.dumps(item_def(), indent=1))
        if os.path.exists(MUSIC_SRC):
            z.writestr("assets/iekcorbar/sounds/music/overworld.ogg", music_ogg(fade=6.0, dur=223.2))
            z.writestr("assets/iekcorbar/sounds/music/silence.ogg", silence_ogg())
            if os.path.exists(SPAWN_SRC):
                z.writestr("assets/iekcorbar/sounds/music/spawn.ogg", loop_ogg(SPAWN_SRC))
            if os.path.exists(END_SRC):
                z.writestr("assets/iekcorbar/sounds/music/end.ogg", music_ogg(END_SRC))
            z.writestr("assets/iekcorbar/sounds.json", json.dumps(own_sounds_json(), indent=1))
            z.writestr("assets/minecraft/sounds.json", json.dumps(music_sounds_json(), indent=1))
    print("wrote", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
