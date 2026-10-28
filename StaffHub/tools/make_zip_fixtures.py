"""Maakt zip-fixtures voor tools/test_unzip.mjs.

Waarom zelf maken: Windows Compress-Archive schrijft backslash-separators (een
eigen testgeval), maar voor de brede paden wil ik exact controle: gedeelde
topmap (strip-test), een '..' in het pad (moet geweigerd worden), macOS-rommel
(moet weggesmeerd worden) en een dubbel weg (telt één keer).

Een zip met absolute paden (/etc/passwd) of 'C:' wijst zip_utils af; dat pad
mag de writer niet eens aan (ValueError). De '..' variant lukt wél: zipfile
accepteert elke arcname, precies zoals kwaadwillende ritsprogramma's.
"""
import os, zipfile

B = os.path.join(os.path.dirname(__file__), "fixtures")
os.makedirs(B, exist_ok=True)

# 1) gewone.zip: alles onder één topmap 'handleidingen/' -> moet 1 niveau strippen
with zipfile.ZipFile(os.path.join(B, "gewone.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("handleidingen/readme.txt", "hallo staff\n")
    z.writestr("handleidingen/deploy/gids.md", "# deploy\nstap 1\n")
    z.writestr("handleidingen/plaatje.png", "\x89PNG\r\n\x1a\n" + "x" * 40)

# 2) boos.zip: een pad dat uit de target-map klimt -> ZipFout bij plannen
with zipfile.ZipFile(os.path.join(B, "boos.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("lees.txt", "onschuldig")
    z.writestr("../buiten.txt", "ik hoor hier NIET")

# 3) mac.zip: Finder-rommel -> overgeslagen, de echte bestanden blijven
with zipfile.ZipFile(os.path.join(B, "mac.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("doc.txt", "blijft")
    z.writestr("__MACOSX/._doc.txt", "rommel")
    z.writestr("map/.DS_Store", "rommel")
    z.writestr("doc.txt", "blijft")  # dubbel: moet wegfilteren naar één

print("fixtures klaar:", sorted(os.listdir(B)))
