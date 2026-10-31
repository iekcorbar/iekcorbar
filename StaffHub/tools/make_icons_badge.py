"""StaffHub-iconen uit het ronde iekcorbar-merklogo (eigenaarsbesluit 1-11: hetzelfde
logo als de server en de website). Vervangt de uitvoer van make_icons.py (het anker);
dat script blijft staan, want make_server_icon.py importeert er de tegel uit.

Bron: public/images/logo-iekcorbar.png in de repo-root (gemaakt door
scripts/make-brand-icons.py, doorzichtige hoeken).

Gebruik:  python StaffHub/tools/make_icons_badge.py     (zonder -I: Pillow staat in user-site-packages)
Daarna:   npm run build; de desktop-app moet opnieuw gebouwd voor het nieuwe icoon.
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))          # StaffHub/
BRAND = os.path.join(os.path.dirname(ROOT), "public", "images", "logo-iekcorbar.png")
BG = (18, 26, 35)  # #121a23, de StaffHub-achtergrond uit globals.css


def badge(px, vulling=1.0, bg=None):
    src = Image.open(BRAND).convert("RGBA")
    b = int(px * vulling)
    logo = src.resize((b, b), Image.LANCZOS)
    cv = Image.new("RGBA", (px, px), (bg + (255,)) if bg else (0, 0, 0, 0))
    cv.alpha_composite(logo, ((px - b) // 2, (px - b) // 2))
    return cv


def opslaan(img, *delen):
    p = os.path.join(ROOT, *delen)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, optimize=True)
    print("geschreven", "/".join(delen), img.size)


def ico(img, *delen, maten):
    p = os.path.join(ROOT, *delen)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, sizes=maten)
    print("geschreven", "/".join(delen))


def main():
    opslaan(badge(512), "src", "app", "icon.png")
    opslaan(badge(180, 0.94, BG), "src", "app", "apple-icon.png")
    for px in (192, 384, 512):
        opslaan(badge(px, 0.94, BG), "public", f"icon-{px}.png")
    ico(badge(256), "public", "favicon.ico", maten=[(16, 16), (32, 32), (48, 48), (64, 64)])
    ico(badge(256), "desktop", "build", "icon.ico", maten=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])


if __name__ == "__main__":
    main()
