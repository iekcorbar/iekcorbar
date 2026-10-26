# AGENTS.md — iekcorbar-final

Volledige projectkennis staat in [CLAUDE.md](CLAUDE.md); lees die bij elk werk in deze repo.
Dit bestand houdt de harde regels vast die áltijd gelden (ook als de sessie vers begint).

## Harde regels

1. **Git: gebruik de skill `git-beheer`.** Commit per afgewerkte werkblok, direct pushen,
   repos altijd privé, nooit secrets/binary-bouwrommel meenemen. De gebruiker kent git zelf
   niet: rapporteer in gewoon Nederlands ("opgeslagen", "online gezet").
2. **Eén uitvoermap: `deploy/`** (in .gitignore). Alle afgemaakte artefacten gaan dáárheen:
   het samengevoegde pack, verse plugin-jars (kopieer uit `<Plugin>/target/` na een groene
   `mvn package`), site-zips. Nooit meer kopiën naar de projectroot of het Bureaublad.
   Na een bevestigde deploy: het artefact uit `deploy/` verwijderen (oude versies weg).
3. **Elk pack-build krijgt een nieuwe NAAM met build-nummer.** `BUILD` in
   `scripts/merge-packs.py` is de bron: bestandsnaam `deploy/iekcorbar-pack-build<N>.zip`,
   de in-game pack-beschrijving en het wegvegen van oudere packs volgen allemaal dat getal.
   Verhoog `BUILD` bij élke herbouw — dezelfde inhoud met een ander bestandsnaam-bedrog bestaat niet.
4. **Na elke packbouw:** `python -I scripts/merge-packs.py && python -I scripts/audit-pack.py`
   (laatste kiest automatisch de nieuwste pack in `deploy/`, exit-code 0 = schoon).

## Deploy-batching (userregel 26-10 avond)

De gebruiker deployt NIET na elke klus, maar in batching wanneer het hem uitkomt. Daarom:
- Elk afgemaakt artefact (pack, jar, site-zip) gaat in `deploy/` én krijgt één regel in
  `deploy/TE-DEPLOYEN.txt`: wat, waar naartoe op de server, wat vervangen/verwijderen, hoe te testen.
- Nooit aandringen, nooit "even checken of het al staat" - de lijst ís de status; de gebruiker pakt hem.
- Regels pas afvinken/verwijderen nadat de gebruiker bevestigt dat het live staat.
- Bij twijfel of iets al uitgeleverd is: noteer dat op de regel ("alleen als nog niet live"),
  niet zelf oplossen en niet navragen - de server draait gewoon door op wat er staat.

## Wat er van 26 okt (avond) aan de hand was — onthoud dit voor 26.2-modellen

Paper/Minecraft **26.2** is op twee punten streng voor dit resourcepack, en allebei gaven
 dezelfde symptomen (paars/zwart-blokbord in het spel):

1. **Elementen mogen op GEEN enkele as buiten -16..32** — anders weigert de client het hele
   model. `merge-packs.py` doet daarom de Y-FIT-pass (te hoge modellen krimpen + java
   `scale:`-compensatie in de plugin-def) en daarna een boundary-clamp op alle assen.
2. **Het `item_model`-component wordt geresolved via een item-definitie in
   `assets/<ns>/items/<id>.json`** — ook voor `block/<id>_stage<N>`-modellen. Een model zonder
   definitie = magenta kubus. Dit was de oorzaak van de paarse crop-perken (98 definities
   ontbraken); de Kitchen- en Sakagura-`make_pack.py` schrijven ze nu automatisch mee.

De audit (`scripts/audit-pack.py`) checkt beide + textures/parents/orphans, dus een volgende
sessie ziet een dergelijke bug nooit meer live op de server — alleen lokaal, exit-code 1.
Uitzonderingen (minecraft:-verwijzingen, MMOItems-export met custom model data) staan daar
verklaard op de KNOWN-lijst.

## Server-pad (handig)

- Pack + derden-packs: `plugins/ResourcePackManager/mixer/` op de server; na een upload:
  `resourcepacks reload` in de console én de client volledig herstarten (downloadbalk afwachten).
- Een in het spel geplaatst model blijft z'n oude uiterlijk tot de chunk herload — slopen en
  terugzetten is de snelste test na een packwissel.
