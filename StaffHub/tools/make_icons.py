"""Maakt het volledige StaffHub-logo-set in één keer, vanuit één tekening.

Het logo (eigenaarsbesluit 28-10): donkerblauwe afgeronde tegel, zacht
staalblauw anker - exact de kleuren van de nieuwe huisstijl in globals.css.
De tekening gebeurt met het Windows-anker-glyph (U+2693, Segoe UI Symbol) op
hoge resolutie en wordt van daar naar élke grootte geschaald: favicon, web-
app-iconen, apple-touch en de desktop-icoon.

Gebruik:  python StaffHub/tools/make_icons.py
Daarna:   npm build (Next pakt src/app/icon.png + apple-icon.png zelf op).

Let op: draai ZONDER python -I, want Pillow staat in de user-site-packages.
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # StaffHub/
S = 1024

# Huisstijl 2.0 (globals.css): tegel = panel, rand = bronze, anker = gold-blauw
ACHTERGROND = (18, 26, 35)      # #121a23
RAND = (51, 65, 79)             # #33414f
ANKER = (91, 157, 217)          # #5b9dd9

def tegel(grootte):
    """Anker in lucide-verhoudingen (vergelijk het <Anchor> in de header):
    ring bovenop, stam, boog met twee schouder-einden. Handgetekend: geen
    enkel font op deze machine tekent U+2693 echt, en zo matcht het logo het
    icoon in de interface."""
    im = Image.new("RGBA", (grootte, grootte), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = int(grootte * 0.21)
    inset = max(1, grootte // 128)
    d.rounded_rectangle([inset, inset, grootte - inset - 1, grootte - inset - 1],
                        radius=r, fill=ACHTERGROND, outline=RAND,
                        width=max(1, grootte // 170))
    G = grootte
    t = int(G * 0.083)                      # lijndikte: 2/24 van de lucide icoon-box
    s = G * 0.78 / 24.0                     # lucide 24-box -> 78% van de tegel
    cx = G / 2.0                            # horizontaal gecentreerd
    oy = (G - 24 * s) / 2.0 + 0.04 * G      # verticaal, iets lager voor optisch gewicht
    def X(v): return cx + (v - 12) * s
    def Y(v): return oy + v * s
    def box(x0, y0, x1, y1): return (X(x0), Y(y0), X(x1), Y(y1))
    # boog (schouder naar schouder, open naar boven): cirkel c(12,12) r10, onderkwart
    d.arc(box(2, 2, 22, 22), start=20, end=160, fill=ANKER, width=t)
    # korte horizontaleuiteinden links/rechts op hoogte y=12 (lucide: H5..2 / 19..22)
    d.line([X(2.5), Y(12), X(5.5), Y(12)], fill=ANKER, width=t)
    d.line([X(18.5), Y(12), X(21.5), Y(12)], fill=ANKER, width=t)
    # stam van ring naar boogbodem
    d.line([X(12), Y(8), X(12), Y(21.6)], fill=ANKER, width=t)
    # ring bovenop: cirkel c(12,5) r3
    d.ellipse(box(9, 2, 15, 8), outline=ANKER, width=t)
    return im


def ico(path, maten):
    im = tegel(1024)
    im.save(path, sizes=maten)
    print(" ", path, "maten", maten)


def png(rel, maat):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    tegel(1024).resize((maat, maat), Image.LANCZOS).save(p, optimize=True)
    print(" ", rel, f"{maat}x{maat}")


if __name__ == "__main__":
    png(os.path.join("src", "app", "icon.png"), 512)
    png(os.path.join("src", "app", "apple-icon.png"), 180)
    png(os.path.join("public", "icon-192.png"), 192)
    png(os.path.join("public", "icon-384.png"), 384)
    png(os.path.join("public", "icon-512.png"), 512)
    ico(os.path.join(ROOT, "public", "favicon.ico"), [(16, 16), (32, 32), (48, 48), (64, 64)])
    os.makedirs(os.path.join(ROOT, "desktop", "build"), exist_ok=True)
    ico(os.path.join(ROOT, "desktop", "build", "icon.ico"),
        [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("logo-set klaar")
