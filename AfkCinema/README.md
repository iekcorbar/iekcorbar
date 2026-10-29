# AfkCinema (26.2)

Server-plugin voor de "AFK-bioscoop": wie 3 minuten niets doet krijgt een
cinematische camera om z'n personage — en ziet dat personage ook écht in beeld.
Gebouwd naar het concept van de ji-afk-cinematic client-MOD, maar dan 100%
server-side: werkt op élke vanilla client.

## Commando's

| commando | wat het doet |
|---|---|
| `/cine afk` | direct de bioscoop: camera draait om jouw personage (via stand-in) |
| `/cine afk <speler>` | (admin) draai om een andere, gewoon zichtbare speler |
| `/cine stop` | terug naar je oude plek en gamemode |
| `/cine automatisch` | je AFK-bioscoop uit/aan zetten |
| `/cine menu` | ruimtelijk **stoompaneel**: zwevend messing bedieningspaneel vóór je neer (het "spatial GUI"-concept uit de client-MOD, maar dan server-side op elke vanilla client) |
| `/cine opname <padnaam>` | vrij rondvliegen als camera-man (pad wordt gemaakt als het nog niet bestaat) |
| `/cine pad add` | huidig standpunt + kijkrichting als waypoint toevoegen |
| `/cine pad verwijder <nr> [pad]` | waypoint-nummer weg |
| `/cine pad speel <pad>` | de camera de route laten volgen |
| `/cine pad info <pad>` / `lijst` / `wis <pad>` | pads beheren |
| `/cine herlaad` | config + pads.yml opnieuw lezen |

## Hoe "jezelf zien" werkt

Een spectator-speler is onzichtbaar — daarom zet de plugin bij AFK een
**Citizens-NPC met jouw skin** op precies jouw plek (via `SkinTrait
.setSkinPersistent(jou)`), en draait de camera dááromheen. Elke beweging of
klik breekt de film: gamemode en plek worden direct teruggezet. Citizens is
soft-dependency: zonder blijft de baan draaien, maar zie je geen poppetje.

## Camerapaden (waypoints)

`pads.yml` (naast de config) bevat alle routes: per punt wereld, x/y/z,
yaw/pitch. Tijdens afspelen vliegt de camera via een **Catmull-Rom spline**
door de punten met soepele easing en kortste-boog draaiing — dus geen hoekjes.
Config:

- `cine.pad-snelheid` — blokken per seconde
- `cine.pad-lus` — `heen-en-terug` (default), `lus` (rond, mits ≥3 punten), `eenmalig`
- `cine.pad-easing` — zacht in/out per segment
- `cine.afk-shot` — wat de AFK-camera doet: `orbit` of `pad:<naam>` (bestaand pad)

## Ruimtelijk stoompaneel (/cine menu)

De spatial-gui-MOD is puur client-rendering (78 klassen mixins op schermen/camera,
geen enkele netwerkcodelijn) — die is niet naar de server te halen. Wat WÉL kan:
hetzelfde belevingsgevoel server-side. `/cine menu` zet een zwevend paneel van
TextDisplay-plaatjes (donker messing op stoomglas, klinkbare Interaction-gebieden)
neer met: status, FILM STARTEN/STOPPEN, afk-automaat en per opgenomen route een
▶-knop. Iedereen mag aan iemands paneel-knoppen draaien (het paneel bedient altijd
de eigenaar). Voorzichtige notities: een dragende film zet de kijker in spectator
en spectators kunnen niet klikken — het paneel is dus de afstandsbediening vóór en
tussen de shots door.

## Tips voor de trailer

Maak met `/cine opname <naam>` + een paar `pad add`-punten een drone-shot langs
je sets, en draai hem met `/cine pad speel` terwijl Replay Mod de beelden
opneemt. De plugin bestuurt de camera van de kijker; Replay pakt dat als
gewoon spelend-beeld op — geen client-MOD nodig.

## Gebouwd voor

api-version 26.2 (paper-api 26.2.build.135). Compileren tegen Citizens doe je
met `AfkCinema/lib/Citizens.jar` — die jar staat niet in git (*.jar); kopiëer
hem van een server of `plugins-offline`.
