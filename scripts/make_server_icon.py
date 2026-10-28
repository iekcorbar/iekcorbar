"""Maakt het server-icoon voor het multiplayer-scherm van de minecraft-server.

Geen eigen ontwerp: dit script importeert het tegeltje zélf uit
StaffHub/tools/make_icons.py (het logo-banner van de favicon), dus het
server-icoon IS de favicon - 64 pixel of 1024, dezelfde tekening, samefallet
met de afgeronde hoeken zoals de web-merk ze heeft.

Formaten (Paper pakt de scherpste die bij de schermgrootte past; de
vanilla-regel blijft: server-icon.png moet EXACT 64x64 zijn):
    server-icon.png        64    (vanilla + elke Paper)
    server-icon64.png      64
    server-icon128.png    128
    server-icon256.png    256
    server-icon512.png    512
    server-icon1024.png  1024

Gebruik:  python scripts/make_server_icon.py
Zonder -I draaien: Pillow staat in de user-site-packages.

De plaatjes gaan naar deploy/server-icon/ - van dáár copieert de eigenaar ze
naar de server-root (naast server.properties); de server leest ze bij de
volgende (her)start. Zie deploy/TE-DEPLOYEN.txt.
"""
import importlib.util
import io
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UIT = os.path.join(ROOT, "deploy", "server-icon")

# het maker-script van het merk importeren zonder het naar scripts/ te slepen
_spec = importlib.util.spec_from_file_location("make_icons", os.path.join(ROOT, "StaffHub", "tools", "make_icons.py"))
make_icons = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(make_icons)


def tagrenzeloos(grootte):
    """Het merk-tegeltje, maar met de transparante randjes gevuld met de eigen
    donkere tegelkleur: de Minecraft-serverlijst plakt er geen achtergrond
    achter, dan werden die hoeken zwart in plaats van donkerblauw."""
    im = make_icons.tegel(grootte).convert("RGBA")
    onder = Image.new("RGBA", im.size, make_icons.ACHTERGROND + (255,))
    onder.alpha_composite(im)
    return onder.convert("RGB")


if __name__ == "__main__":
    os.makedirs(UIT, exist_ok=True)
    maten = {
        "server-icon.png": 64,
        "server-icon64.png": 64,
        "server-icon128.png": 128,
        "server-icon256.png": 256,
        "server-icon512.png": 512,
        "server-icon1024.png": 1024,
    }
    for naam, maat in maten.items():
        p = os.path.join(UIT, naam)
        tagrenzeloos(maat).save(p, optimize=True)
        print(f"  {naam:<22} {maat}x{maat}  {os.path.getsize(p)} bytes")
    print("klaar ->", UIT)
