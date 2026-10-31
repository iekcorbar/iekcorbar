"""Haalt in StaffHub bewerkte .bbmodel-bestanden op en zet ze in de projectmap.

De hub (staff.iekcorbar.com) staat altijd aan en is de bron van de bewerking; deze
script haalt in wat er sinds de vorige keer is opgeslagen ("Opslaan in hub" in de
3D-viewer) en schrijft het over elk bestand in de repo met dezelfde bestandsnaam.
Draai hem wanneer de pc aan staat - hij onthoudt tot waar hij gelezen heeft, dus
niets gaat verloren als hij een tijd niet draait.

Gebruik:   python -I scripts/pull-hub-models.py [--dry] [--all]
Instellen: scripts/.hub-pull.json  {"url": "https://staff.iekcorbar.com", "user": "...", "token": "..."}
           (token = WebDAV-token uit StaffHub; of de env HUB_URL / HUB_USER / HUB_TOKEN)
           --all negeert de onthouden tijd en haalt alles op (alleen nodig na een mislukte run)
"""
import base64, json, os, sys, urllib.request, urllib.error

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(ROOT, "scripts", ".hub-pull.json")
STATE = os.path.join(ROOT, "scripts", ".hub-pull-state.json")
LOG = os.path.join(ROOT, "scripts", "hub-edits.log")
SKIP = {".git", "node_modules", ".next", "target", "deploy", "archief", "__pycache__", "output_browser"}


def config():
    c = {}
    if os.path.exists(CFG):
        c = json.load(open(CFG, encoding="utf-8"))
    url = os.environ.get("HUB_URL") or c.get("url") or "https://staff.iekcorbar.com"
    user = os.environ.get("HUB_USER") or c.get("user")
    token = os.environ.get("HUB_TOKEN") or c.get("token")
    if not user or not token:
        sys.exit("Geen inloggegevens: maak scripts/.hub-pull.json (url, user, token) of zet HUB_USER/HUB_TOKEN.")
    return url.rstrip("/"), "Basic " + base64.b64encode(f"{user}:{token}".encode()).decode()


def get(url, auth):
    req = urllib.request.Request(url, headers={"Authorization": auth})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"Hub antwoordde {e.code} op {url}")


def repo_bestanden(naam):
    gevonden = []
    for dp, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP]
        if naam in files:
            gevonden.append(os.path.join(dp, naam))
    return gevonden


def main():
    dry, alles = "--dry" in sys.argv, "--all" in sys.argv
    url, auth = config()
    since = 0
    if not alles and os.path.exists(STATE):
        since = json.load(open(STATE)).get("since", 0)
    lijst = json.loads(get(f"{url}/api/changes?since={since}", auth))
    if not lijst["files"]:
        print("Niets nieuws in de hub.")
        return
    nieuwste = since
    for f in lijst["files"]:
        doel = repo_bestanden(f["name"])
        inhoud = get(f"{url}/api/changes?id={f['id']}", auth)
        nieuwste = max(nieuwste, f["created"])
        if not doel:
            print(f"- {f['name']}: staat niet in de projectmap (overgeslagen)")
            continue
        for pad in doel:
            rel = os.path.relpath(pad, ROOT)
            if open(pad, "rb").read() == inhoud:
                print(f"= {rel}: al gelijk")
                continue
            print(f"{'(dry) ' if dry else ''}> {rel}")
            if not dry:
                open(pad, "wb").write(inhoud)
                open(LOG, "a", encoding="utf-8").write(f"{f['created']}\t{rel}\n")
    if not dry:
        json.dump({"since": nieuwste}, open(STATE, "w"))
        print("Klaar. Let op: wordt dit model door een make_*.py-generator gebouwd, dan overschrijft de volgende run je bewerking - verwerk hem dan ook in de generator.")


main()
