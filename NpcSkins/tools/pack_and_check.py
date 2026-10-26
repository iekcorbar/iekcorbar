"""Two helpers for putting the NPC skins on the website (no site rebuild needed, app.js serves public/uploads/**).

  python -I tools/pack_and_check.py pack    -> writes npc-skins.zip (forward-slash paths, one top folder 'npc-skins/')
                                              Extract it inside <app folder>/public/uploads/ in Plesk (File Manager > Extract).
  python -I tools/pack_and_check.py check   -> requests every skin URL and prints which ones are missing
Run from the NpcSkins folder. The base URL can be overridden:  BASE=https://example.com/npc-skins python -I tools/pack_and_check.py check"""
import os, sys, urllib.request, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
SKINS = os.path.join(HERE, "..", "skins")
BASE = os.environ.get("BASE", "https://iekcorbar.com/npc-skins")


def names():
    return sorted(f for f in os.listdir(SKINS) if f.endswith(".png"))


def pack():
    out = os.path.join(HERE, "..", "npc-skins.zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in names():
            z.write(os.path.join(SKINS, f), "npc-skins/" + f)
    print("wrote", os.path.abspath(out), len(names()), "skins")
    print("Extract it INSIDE public/uploads/ so the result is public/npc-skins/<name>.png")


def check():
    bad = 0
    for f in names():
        url = BASE + "/" + f
        try:
            r = urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=15)
            ok = r.status == 200 and "image" in (r.headers.get("Content-Type") or "")
            state = "ok" if ok else "wrong type/status %s %s" % (r.status, r.headers.get("Content-Type"))
        except Exception as e:
            ok, state = False, str(e)
        if not ok:
            bad += 1
            print("MISSING", url, "-", state)
    print("%d of %d skins reachable" % (len(names()) - bad, len(names())))


if __name__ == "__main__":
    {"pack": pack, "check": check}.get(sys.argv[1] if len(sys.argv) > 1 else "", lambda: print(__doc__))()
