"""Builds the data for the custom music discs of the Relics plugin.

Run in the Relics folder:  python -I tools/make_discs.py <path-to-ffmpeg>
Reads: sounds/out/relics (built-in carols made by sounds/make_sounds.py) and Relics/discs/ (your own audio files).
Writes: src/main/resources/config.yml   (the generated "discs:" block between the BEGIN/END markers of the
                                        plugins/Relics config: id, title, seconds, sound event, recipe item, colour)
        discs-out/                    (your tracks as mono ogg + a sounds.json fragment, merged into the pack by make_pack.py)
"""
import json, os, re, shutil, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
SOUNDS = os.path.abspath(os.path.join(ROOT, "..", "sounds", "out", "relics", "sounds"))
AUDIO = (".ogg", ".mp3", ".wav", ".flac", ".m4a", ".opus")

# id, title, sound event, ogg under sounds/out/relics/sounds, extra crafting item
BUILTIN = [
    ("jingle_bells", "Jingle Bells", "disc.jingle_bells", "disc/jingle_bells_1.ogg", "SNOWBALL"),
    ("deck_the_halls", "Deck the Halls", "disc.deck_the_halls", "disc/deck_the_halls_1.ogg", "SWEET_BERRIES"),
    ("we_wish", "We Wish You a Merry Christmas", "disc.we_wish", "disc/we_wish_1.ogg", "COOKIE"),
    ("silent_night", "Silent Night", "disc.silent_night", "disc/silent_night_1.ogg", "CANDLE"),
]


def seconds(ffmpeg, path):
    out = subprocess.run([ffmpeg, "-i", path], capture_output=True, text=True).stderr
    m = re.search(r"Duration: (\d+):(\d+):(\d+(?:\.\d+)?)", out)
    if not m:
        sys.exit("cannot read the length of " + path)
    return int(int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3)) + 0.999)


MARK_BEGIN = "  # BEGIN:generated"
MARK_END = "  # END:generated"


def entry_lines(did, title, seconds, event, recipe, colour):
    return [
        f"  {did}:",
        f"    title: {title}",
        f"    seconds: {seconds}",
        f"    event: {event}",
        f"    recipe: {recipe if recipe else '\"\"'}",
        f"    colour: {colour}",
    ]


def write_discs_block(cfg, entries):
    """Replace the text between the BEGIN/END markers in the plugin config with the generated entries."""
    text = open(cfg, encoding="utf-8").read()
    begin, end = MARK_BEGIN, MARK_END
    if begin not in text or end not in text:
        sys.exit("could not find the '" + begin + "' / '" + end + "' markers in " + cfg + " - add them first")
    b, e = text.index(begin), text.index(end)
    block = "\n".join([begin] + entries + [end]) + "\n"
    open(cfg, "w", encoding="utf-8").write(text[:b] + block + text[e + len(end):])


def main():
    ffmpeg = sys.argv[1] if len(sys.argv) > 1 else shutil.which("ffmpeg")
    if not ffmpeg:
        sys.exit("pass the path to ffmpeg")
    rows = []
    for i, (did, title, event, rel, recipe) in enumerate(BUILTIN):
        p = os.path.join(SOUNDS, rel)
        if not os.path.exists(p):
            sys.exit("missing " + p + " - run sounds/make_sounds.py first")
        rows.append((did, title, seconds(ffmpeg, p), event, recipe, i % 6))
    out = os.path.join(ROOT, "discs-out")
    shutil.rmtree(out, ignore_errors=True)
    info = {}
    txt = os.path.join(ROOT, "discs", "discs.txt")
    if os.path.exists(txt):
        for line in open(txt, encoding="utf-8"):
            f = [x.strip() for x in line.split("|")]
            if f and f[0] and not f[0].startswith("#"):
                info[f[0].lower()] = f
    fragment = {}
    d = os.path.join(ROOT, "discs")
    for name in sorted(os.listdir(d)) if os.path.isdir(d) else []:
        stem, ext = os.path.splitext(name)
        if ext.lower() not in AUDIO:
            continue
        did = re.sub(r"[^a-z0-9_]", "_", stem.lower())
        if any(r[0] == did for r in rows):
            print("skipped (id already used):", name)
            continue
        f = info.get(stem.lower(), [])
        title = f[1] if len(f) > 1 and f[1] else stem.replace("_", " ").title()
        recipe = f[2].upper() if len(f) > 2 else ""
        target = os.path.join(out, "assets", "relics", "sounds", "disc", "u_" + did + ".ogg")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        subprocess.run([ffmpeg, "-loglevel", "error", "-y", "-i", os.path.join(d, name), "-vn", "-ac", "1", "-ar", "44100",
                        "-c:a", "libvorbis", "-q:a", "4", target], check=True)
        event = "disc." + did
        fragment[event] = {"sounds": [{"name": "relics:disc/u_" + did, "stream": True}], "subtitle": "subtitles.relics.disc"}
        rows.append((did, title, seconds(ffmpeg, target), event, recipe, len(rows) % 6))
        print("disc:", did, "-", title, f"({rows[-1][2]} s)")
    if fragment:
        json.dump(fragment, open(os.path.join(out, "sounds_fragment.json"), "w", encoding="utf-8"), indent=1)
    res = os.path.join(ROOT, "src", "main", "resources", "config.yml")
    entries = []
    for did, title, seconds_, event, recipe, colour in rows:
        entries.extend(entry_lines(did, title, seconds_, event, recipe, colour))
    write_discs_block(res, entries)
    print("wrote", len(rows), "discs to", res)


if __name__ == "__main__":
    main()
