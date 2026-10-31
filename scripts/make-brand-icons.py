"""Merk-iconen van iekcorbar uit het ronde serverlogo (scripts/brand/logo_concept_2.jpg).

Het logo is een ronde badge op een witte jpg-achtergrond: eerst wordt er een
vloeiend (anti-aliased) cirkelmasker op gezet zodat de hoeken doorzichtig zijn,
daarna worden alle formaten geschreven:

  site        public/icon.png (64), src/app/favicon.ico (16-256), public/apple-touch-icon.png (180, ondoorzichtig)
  PWA/app     public/app-icons/icon-192.png, icon-512.png (ondoorzichtig, donkere rand), icon-maskable-512.png (veilige zone 80%)
  master      public/images/logo-iekcorbar.png (512, doorzichtig)
  server      deploy/server-icon/server-icon.png (64, EXACT 64x64 voor vanilla) + 64/128/256/512/1024

Gebruik:  python scripts/make-brand-icons.py     (Pillow staat in user-site-packages: zonder -I)
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(HERE, "brand", "logo_concept_2.jpg")
BG = (15, 15, 18)  # #0f0f12 - achtergrondkleur uit manifest.webmanifest


def rond(img, straal=255.0):
    """Cirkelmasker met 4x supersampling; alles buiten de cirkel wordt doorzichtig."""
    w, h = img.size
    s = 4
    m = Image.new("L", (w * s, h * s), 0)
    d = ImageDraw.Draw(m)
    c = (w * s) / 2
    r = straal * (w / 512) * s
    d.ellipse((c - r, c - r, c + r, c + r), fill=255)
    m = m.resize((w, h), Image.LANCZOS)
    out = img.convert("RGBA")
    out.putalpha(m)
    return out


def groot(master, px):
    return master.resize((px, px), Image.LANCZOS)


def op_donker(master, px, vulling=1.0):
    """Badge op effen donkere achtergrond (voor iOS/PWA die geen transparantie willen)."""
    cv = Image.new("RGBA", (px, px), BG + (255,))
    b = int(px * vulling)
    cv.alpha_composite(groot(master, b), ((px - b) // 2, (px - b) // 2))
    return cv.convert("RGB")


def opslaan(img, rel):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    print("geschreven", rel, img.size)


def main():
    src = Image.open(SRC).convert("RGB")
    assert src.size == (512, 512), src.size
    # de badge heeft zelf 6 px witte rand rondom (straal 250 om midden 256): bijsnijden en opschalen
    src = src.crop((6, 6, 506, 506)).resize((512, 512), Image.LANCZOS)
    master = rond(src)

    opslaan(master, "public/images/logo-iekcorbar.png")
    opslaan(groot(master, 64), "public/icon.png")
    opslaan(op_donker(master, 180, 0.94), "public/apple-touch-icon.png")
    opslaan(op_donker(master, 192, 0.94), "public/app-icons/icon-192.png")
    opslaan(op_donker(master, 512, 0.94), "public/app-icons/icon-512.png")
    opslaan(op_donker(master, 512, 0.78).convert("RGBA"), "public/app-icons/icon-maskable-512.png")

    ico = os.path.join(ROOT, "src", "app", "favicon.ico")
    big = groot(master, 256)
    big.save(ico, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("geschreven src/app/favicon.ico")

    for px in (64, 128, 256, 512, 1024):
        opslaan(groot(master, px), f"deploy/server-icon/server-icon{px}.png")
    opslaan(groot(master, 64), "deploy/server-icon/server-icon.png")


if __name__ == "__main__":
    main()
