# Trailer-productiescript v3 — "SAKURA AND STEAM" — ALLES IN MINECRAFT

Versie 3 (29-10, ochtend). Gebruikers-eis: **alles moet in Minecraft zijn.**
Dit script vervangt de Blender-diorama-benadering (v2). Elke shot is een
in-game opname met **Replay Mod** op de client, op locaties die al op de
server staan of met één kleine build-sectie die WE nog in de serverwereld
zetten (met bestaande blokken/FMM-modellen/decor uit onze eigen plugins).
Audio blijft: `trailer versie 2- narrated intro 6 ... female only.mp3`
(229,92 s — de vier aktes + outro uit de PDF).

Benodigdheden (éénmalig, owner-acties in vet):
- **Replay Mod installeren op de client** (java 25 staat klaar; Replay
  ondersteunt 26.x — instant-filming mode aan).
- Opname-client met ons resourcepack + Optifine-achtige shaders uit
  **Bliss/Tildes** voor de film-look (optioneel, doet enorm veel).
- Ik lever: camera-route-tabellen (positie/tijd per shot), per-shot
  commando-lijst (/time, /weather,tp, summons), de uiteindelijke montage
  (ffmpeg: beats, letterbox, titel, audio-sync).

---

## AUTORITATIEF SCRIPT (woordelijk uit `trailer script.pdf`, 29-10 uitgelezen)

**Voice-over regels per acte** (cn = snijpunt-richting voor de montage):

| acte | VO-regels (woorden als de mp3) | auditieve cues om op te snijden |
|---|---|---|
| A1 | "Time does not rush here. It flows... toward a new era. Welcome to a world where the ancient traditions of the Shogunate melt into the steady power of steam and copper." → "The old gods watch in silence as the machines awaken. Mechanical airships drift through the clouds. And beneath the falling pink petals of the Sakura, pioneers forge a future. But this land does not build itself." | start: lage strijkers + **één klokgelier**; midden: **klokwerk-tiktak** zet in |
| A2 | "This is no world for spectators. This is a world for creators. Forge your own empire. Claim your land, and protect your sanctuaries. Master the ancient art of the Steam Brewery... and let your flavors rule the Villager Market." | koper akkoorden + **rumtige drum-slagen** per zin |
| A3 | "But with great power... comes great threat. In the shadows of the mechanical cities lie elite dungeons. Dangerous depths... where only the strongest dare to tread." | **taiko-rollen** + koor "Ahhh-Ohhh" bij "dangerous depths" |
| A4 | "Who will you be when the pressure rises? Declare your race. Choose your RPG job within the Adventure Guild. Unlock powerful, custom trinkets. Because in this challenging world... you do not survive alone." → "Fight for your allies. Protect what you have built. The pressure gauges are rising... the sky turns to rose and gold. Claim your destiny. Ascend in rank, and become the legend this realm needs." | maximum intensiteit; **"rose and gold" = colorgrade-shift** |
| OUT | "Sakura and Steam. Your adventure awaits." | **abrupt stop**, dan enige galmende bel + stoom-guishe, daarna absolute stilte |

**Muziekstijl (Suno-notitie uit de PDF, voor eventuele alternatieve track):**
slow cinematic trailer, dark fantasy, deep bass drones, orchestral strings,
shakuhachi, taiko accents, subtle ticking gears, noble spoken-word narration.

**Eindkaart-correctie:** de PDF typt `://iekcorbar.com` (gerestaarteerd).
Het echte adres is **`play.iekcorbar.com`** (bevestigd uit de Replay-opname-
metadata: `serverName: play.iekcorbar.com`). Op de eindkaart dus:
`play.iekcorbar.com` + `discord.gg/G9P2khU7F4`.

---

## LOCATIES op de live-server (bestaand — niet bouwen, alleen filmen)

| code | locatie | wat staat er | bron |
|---|---|---|---|
| L1 | **sakura-tuin** (Sakagura Garden-set) | kersenbomen, bye-byes… | garden-decor: 21 items, lantaarn-palen |
| L2 | **stoom-fabriek** (Steamworks run-live) | draaiende machines: belts, fans, presses, robots-arms, treinen/cargo wagons | machines.ylive, FMM-modellen |
| L3 | **keuken + markt** (Kitchen/Kitchendecor) | toonbanken, kratten, brouwsels, drankjes | Kitchen 0.x live |
| L4 | **Zoo** (Wildlife) | hagen met beer/zwijn/hert/kraanvogel/zebra/olifant + beebies, kiosk, beelden | Wildlife 0.7.0 live, 137 modellen |
| L5 | **Elitemobs-dungeons**: `em_hallosseum` + `em_knight_castle` (LOSSE wouden!) | kant-en-klare donkere burcht-sets met mobs | staan als wereldmappen op de server |
| L6 | **haven + strand** (Bluemap-area) | water, kades, schemerschaduwen | Bluemap 13 GB = rijke wereld |
| L7 | **hoofdstad/spawn** | de stad zelf, straten, lantaarnpalen (brass/copper straat-lampen uit fmm steampunk-set) | fmm-models/steampunk_iekcorbar |

Bijvangst: de twee dungeon-werelden (L5) zijn de "elite dungeons" van de
VO — letterlijk gebouwd op de server. Luchtschepen: EliteMobs-spawns
`em_airship_ghast`/`em_zeppelin_dragon` bestaan als ENTITEIT met onze
modellen → summons in de lucht boven L6/L7 = echte vliegende schepen.

---

## BEOORDELEN VOORAF: wat er bijgebouwd moet worden (klein)

**Status 29-10 nachtelijk: TWINTIG sets KLAAR** — als `.schem` op de server
in `schematics/` (pterodactyl-eigendom, Sponge-v2, stuk voor stuk gevalideerd
tegen de echte WorldEdit-files). In-game laden:
`//schem load iekcorbar-trailer-S<nummer>` dan `//paste -a`.

| set | wat | voor |
|---|---|---|
| S1 sakura-laan | gracht + bruggen + 12 lantaarnpalen + 6 kersenbomen (46×12×20) | A1 |
| S2 stenen-hoofden | 3 Godenkoppen met gloed (20×14×10) | A1 |
| S3 torii | grote poort + trede + bomen + lantaarns (18×15×12) | A1/A4 |
| S4 kerker-poort | poort met torens, kantelen, zielen-licht (26×16×14) | A3 C2 |
| S5 burchttoren | 26 hoog, lichte ramen, wimpel (12×26×12) | A4 D4 |
| S6 zwevend-eiland | eiland + hangende kettingen + boompje (16×16×16) | A4 D1 |
| S7 theehuis | pilaren + laagdak + lampionnetten (14×12×14) | A1/A2 |
| S8 lantaarn-laantje | close-up-versie van S1 (14×7×8) | A1 B-roll |
| S9 vuurtoren | wit-rood, lichtkoepel aan (14×22×14) | A2/A4 |
| S10 meerpaal | luchtschip-meermast met gloeiringen (10×18×10) | A1f/A4 |
| S11 hokora | mini-heiligdom (8×7×8) | A1 detail |
| S12 boogbrug | brug over water met hek (16×8×10) | A1/A2 overgang |
| S13 markt | 3 gestreepte kraampjes + toonbanken (20×8×14) | A2 B6 |
| S14 smederij | hall met 2 magma-schoorstenen (14×11×12) | A2 B1/B3 |
| S15 kerkgang | zuilengang, magma-strip, hangende zielen (8×10×20) | A3 C3 |
| S16 baaskamer | obsidiaan-arena + lavapoel + kroon (16×12×16) | A3 |
| S17 herberg | gilde-herberg, ramen verlicht (14×13×12) | A3 C7 |
| S18 brouwerij | gistketels + leiding + schoorsteen (12×9×10) | A2 B5 |
| S19 uitkijktoren | houten toren voor de zoo (8×14×8) | A3/A4 |
| S20 tuinvijver | vijver + mini-torii + boom (12×5×12) | A1/D6 slot |

Generator: `scripts/make_trailer_schems.py` → `python -X utf8
scripts\make_trailer_schems.py gen` (20 bestanden naar
`deploy/trailer-schems/`). Maten zijn blok×blok×blok; `//paste -a` slaat
lucht over.

Alles andere: bestaand.

---

## SHOT-LIJST (tc = opnametijd, REPLAY-tijd = daadwerkelijk opnemen)

### ACT 1 — THE LOST WORLD   0:00–0:45   locaties: L1/L7, tijd: 18000 (zonsopgang), weather clear → regen later

| tc | s | VO-regel | SHOT | in-game camera |
|---|---|---|---|---|
| 0:00 | 3 | — | **ZWART + klok** | (geen opname — zwart) |
| 0:03 | 8 | "Time does not rush here. It flows..." | **A1** sakura-tuin: **kruipende groothoek-dolly** tussen bomen door door bloesem | replay-cam, path langs 10 takken, fog aan |
| 0:11 | 6 | "...toward a new era." | **A2** lantaarn-reeks: **tracking shot** langs 8 lantaarnpalen, regen + reflecties | /weather rain; cam 1,5 m hoogte |
| 0:17 | 8 | "Welcome to a world where the ancient traditions of the Shogunate..." | **A3** S1/S3 **torii-breed** camera **rijgt verticaal** achter poort om | /time set 23000; zachte maan |
| 0:25 | 6 | "...melt into the steady power of steam and copper." | **A4** macro **draaiend messing tandwiel** (Steamworks `cogwheel`-item op ItemFrame/dispenser-anim) | slow-mo 20 %, rack-focus |
| 0:31 | 8 | "The old gods watch in silence as the machines awaken." | **A5** S2 stenen hoofden → daarachter **lichten van de stad/fabriek gaan aan** (one shot, lampen togglen) | cam static, 24 u-time-lapse 3 s |
| 0:39 | 6 | "Mechanical airships drift through the clouds." | **A6** twee `em_zeppelin_dragon`-spawns **boven L6 haven** in mist | cam omhoog, zeppelin **volgt eigen path** |

### ACT 2 — THE GEARS TURN   0:45–1:45   locatie: L2 (dag, zon 6000)

| tc | s | VO | SHOT |
|---|---|---|---|
| 0:45 | 8 | "This is no world for spectators." | **B1** L2 fabriek: **steadicam-wandeling** tussen draaiende machines door (belts, presses, fans) |
| 0:53 | 5 | "This is a world for creators." | **B2** speler-hand (first-person!) `sw_brass_ingot` in arm, robot-arm `sw_arm` **grijpt erom** |
| 0:58 | 6 | "Forge your own empire." | **B3** `crushing_wheel` + `sw_cogwheel` rij, **dolly langs** de draaiende as |
| 1:04 | 8 | "Claim your land, and protect your sanctuaries." | **B4** L7: **Lands-vlag/genest** — claim-block + palen; cam **cirkelt** erom |
| 1:12 | 8 | "Master the ancient art of the Steam Brewery..." | **B5** L3 keuken/brouwerij: **overhead-dolly** over ketels + `Kitchen`-fornuizen, stoomdeeltjes |
| 1:20 | 8 | "...and let your flavors rule the Villager Market." | **B6** L3 markt: NPC-handel, **360°-orbit** om een kraam met drankjes |
| 1:28 | 10 | — | **B7 montage**: 5 × 1,2 s punch-ins van draaiende onderdelen (tandwiel/pers/belt/grijper/whistle) |
| 1:38 | 7 | — | **B8** fabriek-breed: camera **trekt 40 m terug** in 7 s — hele stad draait |

### ACT 3 — THE CALL TO ARMS   1:45–2:45   locatie: **L5 em_knight_castle / hallosseum** (tijd 24000, regen)

| tc | s | VO | SHOT |
|---|---|---|---|
| 1:45 | 6 | "But with great power... comes great threat." | **C1** donker: **ene paar rode ogen** (EliteMob-spawn in het zwart) knipperen |
| 1:51 | 8 | "In the shadows of the mechanical cities lie elite dungeons." | **C2** **burcht-buitenwijd**: kasteel + machine-lantaarns, cam **crash-zoom** naar poort |
| 2:01 | 6 | "Dangerous depths..." | **C3** **kruip door gang** (cam 0,5 m), fakkels, mob-silhouet achter glas |
| 2:07 | 7 | "...where only the strongest dare to tread." | **C4** vechtsbeeld: speler + EliteMob **echt gevecht** opnamemoment (1 takes) |
| 2:14 | 8 | "Who will you be when the pressure rises?" | **C5** 3 mob-types op rij (tank/zoeker/genezer skins) — cam **dolly** langs |
| 2:22 | 7 | "Declare your race." | **C6** L4 Zoo: **rij dieren** (beer-hert-krane) — top-down orbit, 1 dier per tel (letterlijk "races") |
| 2:29 | 8 | "Choose your RPG job within the Adventure Guild." | **C7** MMO-guild-bord/quest (BetonQuest NPC `betonquest_player` is live!) — **over-the-shoulder shot** |
| 2:37 | 8 | "Unlock powerful, custom trinkets." | **C8** macro: **ValhallaTrinket-item** in hand, PDC-gloed, draaiend op display |

### ACT 4 — THE GRAND CLIMAX   2:45–3:35   mix-locaties

| tc | s | VO | SHOT |
|---|---|---|---|
| 2:45 | 8 | "Fight for your allies." | **D1** L4/Zoo-bos: **spelers + tamme dieren** rennen samen naar camera (breeding-kudde live) |
| 2:53 | 8 | "Protect what you have built." | **D2** L5 burcht-doorway: verdediging — `sw_arm_turret`s richten **omhoog** |
| 3:01 | 10 | "The pressure gauges are rising... the sky turns to rose and gold." | **D3** **time-lapse** 6000→18000 (rose/gold!), stad-silhouet, 2 zeppelins kruisen |
| 3:11 | 10 | "Claim your destiny. Ascend in rank, and become the legend this realm needs." | **D4 ascent**: cam **verticale klim 60 m** langs toren/stad, alles hieronder |
| 3:21 | 6 | — | **D5** zeppelin-front **keert terug** camera in → **1 frame wit** |
| 3:27 | 8 | — | **D6** stil: torii (S3) bij zonsopgang, bloesem waait |

### OUTRO — THE LANDING   3:35–3:50

| tc | s | VO | SHOT |
|---|---|---|---|
| 3:35 | 4 | "Sakura and Steam." | **E1** zwart → **3D-logo** (onze bestaande sting) 2 s + tekst |
| 3:39 | 5 | "Your adventure awaits." | **E2** `iekcorbar.com` + discord-link boven **gameplay-screenshot** (vage vignette) |
| 3:44 | 6 | — | **E3** fade zwart, stoom-guishe |

---

## OPNAME-EN-MONTAGEWERKWIJZE (Replay Mod)

1. **Per locatie één Replay-opname** van 1–3 min met daarin ALLE shots van
   die locatie (achtereenvolgens, spoor-achtig): je spant de cam per shot
   op de aangegeven posities (Replay ingebouwde keyframes: posie + rotatie
   + easing). Ik lever per shot een rijtje
   `(start-pos, eind-pos, duur, easing)` dat je 1-op-1 typt/inleest.
2. **Instant-Filming toetsen**: F5 hide UI altijd; shaders: Bliss medium.
3. Replay-export: 1280×720 60 fps → **ik versnel/vertraag + knip in ffmpeg**
   op de VO-timings, letterbox 2.39:1, grain+vignette, flitsen, audio eronder.
4. **Vliegende zeppelins**: `em_zeppelin_dragon` + `em_airship_ghast` als
   entiteiten (onze FMM-modellen) via `/wildlife`- achtige spawn of direct
   `/summon` + CustomModelData; Replay filmt ze real.
5. Rode-ogen-c1: EliteMob spawn → `invisibility potion` + `glow in the
   dark eyes` = simpele embers/eye-armor stand-in.

## MIJN AUTOMATISCHE DELEN (geen client nodig)
- camera-routes als `.replay`-import CSV's + per-shot `/tp`- en
  `/time`/`/weather`-commandolisten (kopieer-klaar)
- Summon-commando's per shot (zeppelins/mobs/dieren)
- montage + grading + audio-sync + titel-overlays in ffmpeg
- de logo-sting (E1) staat al kant-en-klaar

## WAT JIJ DOET (korte lijst, 1 namiddag)
1. Replay Mod installeren + connect naar iekcorbar.com
2. Per locatie (6 locaties) één opname draaien volgens mijn scripts
3. Die `.replay`-bestanden (of de mp4-exports) in de projectmap zetten →
   ik knip alles.

## NOG TE BEANTWOORDEN (owner)
- Bestaat er al een **gebouwde sakura-lantern-street** op de server, of
  bouwen we S1-S3 eerst (1 avond, ik lever block-lijst + place-commando's)?
- Shaders (Bliss/Tildes) op de opname-client: ja/nee (sterk aan te raden).
