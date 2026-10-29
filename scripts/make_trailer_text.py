# make_trailer_text.py - tekenen de teksten voor de trailer-uitkaart (Pillow).
#   python scripts/make_trailer_text.py <uit.png>
# Doorzichtig 1280x720 canvas met: iekcorbar.com (goud, schreef) + discord-link (roze).
import sys

from PIL import Image, ImageDraw, ImageFont

uit = sys.argv[1] if len(sys.argv) > 1 else "deploy/trailer/tekst-uitkaart.png"

img = Image.new("RGBA", (1280, 720), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def font(groot):
    for fp in (r"C:\Windows\Fonts\georgiab.ttf", r"C:\Windows\Fonts\timesbd.ttf", r"C:\Windows\Fonts\arialbd.ttf"):
        try:
            return ImageFont.truetype(fp, groot)
        except Exception:
            continue
    return ImageFont.load_default()


def centrer(tekst, f, y, kleur):
    w = d.textlength(tekst, font=f)
    d.text(((1280 - w) / 2, y), tekst, font=f, fill=kleur)


# zachte schaduw voor leesbaarheid over elke achtergrond
GOUD = (217, 160, 56, 255)
ROZE = (242, 182, 200, 255)
SCHADUW = (0, 0, 0, 190)

# PDF typte "://iekcorbar.com" - echt adres is play.iekcorbar.com (Replay-meta 29-10)
centrer("play.iekcorbar.com", font(84), 556, SCHADUW)
d.offset = (2, 2)
centrer("", font(1), 0, (0, 0, 0, 0))  # offset-reset placeholder
d.offset = (0, 0)
centrer("play.iekcorbar.com", font(84), 552, GOUD)
centrer("discord.gg/G9P2khU7F4", font(40), 652, ROZE)

img.save(uit)
print("TEKSTKAAR KLAAR:", uit)
