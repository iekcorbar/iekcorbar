"""Steamworks Additions resource pack (namespace additions): the Steam Clock, the Brass Railing
(connection masks, like the garden fence) and the Vent Grille (wall). Own low-poly designs;
front = north (z=0), wall mounts have their back at z=16.
Usage (from SteamworksAdditions/): python -I tools/make_additions_pack.py resourcepack
  -> resourcepack/Additions-pack.zip
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "propkit"))
from propmodels import Pack  # noqa: E402

PALETTE = {
    "brass": (206, 168, 72), "brass_dark": (150, 118, 46), "gold": (246, 206, 90), "iron": (146, 150, 158),
    "dark": (52, 50, 56), "black": (34, 34, 38), "copper": (182, 106, 52), "verdigris": (88, 150, 128),
    "ivory": (240, 232, 200),
}


def build(out):
    pk = Pack("additions", out, PALETTE, "Steamworks Additions (steam clock, brass details)")
    b = pk.box

    def side(d, x1, y1, z1, x2, y2, z2, c):
        """Box given for direction E (+x); turned to N(0)/E(1)/S(2)/W(3) around the block centre."""
        if d == 1:
            return b(x1, y1, z1, x2, y2, z2, c)
        if d == 3:
            return b(16 - x2, y1, z1, 16 - x1, y2, z2, c)
        if d == 2:
            return b(16 - z2, y1, x1, 16 - z1, y2, x2, c)
        return b(z1, y1, 16 - x2, z2, y2, 16 - x1, c)

    # ------------------------------------------------------------------ steam clock (2 blocks tall)
    pk.model("steam_clock", [
        b(5, 0, 5, 11, 2, 11, "dark"),                                   # floor plate
        b(3.5, 2, 3.5, 12.5, 4.5, 12.5, "iron"),                         # pedestal
        b(6.5, 4.5, 6.5, 9.5, 12, 9.5, "iron"),                          # column
        b(3, 12, 3, 13, 22, 13, "brass"),                                # case
        b(2.4, 12, 2.4, 13.6, 13, 13.6, "brass_dark"),                   # cornice under the case
        b(4.2, 13.7, 1.7, 11.8, 20.7, 2.1, "black"),                     # dial bezel (north face)
        b(4.8, 14.3, 1.3, 11.2, 20.1, 1.8, "ivory"),                     # dial
        b(7.6, 14.9, 1.0, 8.4, 19.4, 1.4, "dark"),                       # minute hand, straight up
        b(8.0, 16.8, 1.0, 10.5, 17.6, 1.4, "dark"),                      # hour hand, pointing two
        b(7.4, 16.4, 0.9, 8.6, 17.6, 1.1, "gold"),                       # hand pin
        b(2, 22, 2, 14, 24, 14, "dark"),                                 # roof
        b(3.6, 24, 3.6, 6.6, 27, 6.6, "gold"),                           # bell left
        b(9.4, 24, 3.6, 12.4, 27, 6.6, "gold"),                          # bell right
        b(7.3, 24, 7.3, 8.7, 29, 8.7, "brass"),                          # spike
        b(6.8, 29, 6.8, 9.2, 31.4, 9.2, "gold"),                         # finial
    ])

    # ------------------------------------------------------------------ brass railing (joins)
    post = [b(6.8, 0, 6.8, 9.2, 13, 9.2, "iron"), b(6.2, 13, 6.2, 9.8, 15, 9.8, "brass")]

    def rail(d):
        return [side(d, 9.0, 8.6, 7.1, 16, 10.2, 8.9, "brass"),
                side(d, 9.0, 4.0, 7.1, 16, 5.6, 8.9, "brass")]

    pk.fence("brass_railing", post, rail)

    # ------------------------------------------------------------------ vent grille (wall)
    pk.model("vent_grille", [
        b(1.5, 2, 13, 14.5, 14.5, 15, "copper"),                         # frame
        b(2.6, 3.1, 12.6, 13.4, 13.4, 13.1, "black"),                    # open inside
        b(3.0, 4.4, 11.6, 13.0, 6.0, 12.8, "verdigris"),                 # slat low
        b(3.0, 7.7, 11.6, 13.0, 9.3, 12.8, "verdigris"),                 # slat mid
        b(3.0, 11.0, 11.6, 13.0, 12.6, 12.8, "verdigris"),               # slat high
        b(2.0, 2.5, 12.7, 2.9, 3.4, 13.2, "dark"),                       # corner bolts
        b(13.1, 2.5, 12.7, 14.0, 3.4, 13.2, "dark"),
        b(2.0, 13.3, 12.7, 2.9, 14.2, 13.2, "dark"),
        b(13.1, 13.3, 12.7, 14.0, 14.2, 13.2, "dark"),
    ])

    pk.finish()


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "resourcepack")
