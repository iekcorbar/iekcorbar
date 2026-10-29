# Trailer-productiescript "SAKURA AND STEAM" — volledig zelf te maken

Versie 2 (29-10). Bewust herschreven vanuit de PDF-script: **elk shot is
bouwmateriaal dat wij zelf in Blender kunnen renderen** — eigen
FMM/Blockbench-modellen, procedurele sets en de bestaande 3D-logo. Geen
gameplay-opnames nodig (optionele later-upgrade: Replay Mode-beelden op
dezelfde timings). Audio = bestaande genarrateerde mp3 (229,92 s,
`trailer versie 2- narrated intro 6 ... female only.mp3`).

Beeldformaat: 1280x720 @ 24 fps · look: donker navy + messing + sakura-roze,
anamorfische filmkleur, 2.39:1 letterbox vanaf ACT 2.

---

## KLEUREN & STIJL (vast, uit het merk)
- Nacht: `#100d15` · messing/goud: `#b57a3c` / `#d9a038`
- Sakura-roze: `#f2b6c8` / hardroze `#c46a86`
- Steam-wit `#cfd6de` · gloeiember `#ffb347`
- Letterbox-balken 2.39:1, vignette ~0.35, 35 mm-korrel, subtiele bloom.
- Tekst-in-beeld: Georgia-bold, messing-goud, fade 6 frames in / 12 uit.

---

## ACT 1 — THE LOST WORLD   0:00 – 0:45

| tc | duur | VO | SHOT | bron / camera |
|---|---|---|---|---|
| 0:00 | 3 | stilte | **00 ZWART + bell** | zwart, 1 bel-gloed |
| 0:03 | 7 | "Time does not rush here. It flows..." | **A1a sakura macro** | eigen `wl`-loze procedurele tak; **slow dolly-in 60→75 mm**, 40 vallende bloesems (echte particles), 2 lantaarns f9 |
| 0:10 | 6 | "...toward a new era." | **A1b lantaarn-reeks** | `brass_street_lamp` + `copper_paper_lantern` (5 exemplaren) langs nachtsloot; **tracking shot op rails**, mist |
| 0:16 | 8 | "Welcome to a world where the ancient traditions of the Shogunate..." | **A1c torii-poort** | procedurele torii (2 palen + 2 balken, lakrood `#8c2f2f`) + kersenboom + zwenkende camera (30°-arc) |
| 0:24 | 7 | "...melt into the steady power of steam and copper." | **A1d overgang** | **match-cut**: roze bloesem → roestige messing-kogellager; 1 frame roodflits op "copper" |
| 0:31 | 8 | "The old gods watch in silence as the machines awaken." | **A1e stenen gezichten** | 3 procedurele Jizo-stenen hoofden, ogen aan (emissie), camera crash-zoom naar 1 paar ogen; **1 frame wit** |
| 0:39 | 6 | "Mechanical airships drift through the clouds." | **A1f airship silhouet** | `em_zeppelin_dragon` ver weg door wolken (backlight, silhouet); slow push |

> Inactie-regel: max 9 s per shot, wissel elke snij-point van scale (macro ↔ wijd).

---

## ACT 2 — THE GEARS TURN   0:45 – 1:45

| tc | duur | VO | SHOT | bron / camera |
|---|---|---|---|---|
| 0:45 | 8 | "This is no world for spectators." | **B1 factory wide** | `sw_brass_casing` + `sw_andesite_casing` wand (8x3), `sw_bellows_fan` (rotor **draaiend**, 2,4 omwentelingen/s), stoom; **crane-up 2 m** |
| 0:53 | 5 | "This is a world for creators." | **B2 hands-on close** | `sw_arm` + `sw_arm_tool_gripper` pakt gloeiend `sw_brass_ingot`; **rack focus** op ingot |
| 0:58 | 7 | "Forge your own empire." | **B3 forge** | `sw_crushing_wheel` draait, `sw_cogwheel`-tandwielset, ember-particles (60), **whip-pan** B2→B3 |
| 1:05 | 8 | "Claim your land, and protect your sanctuaries." | **B4 claim-grens** | messing `sw_pipe`-lijn met `sw_valve`/`clutch_casing_on` (groen licht aan), **dolly langs pijpleiding**, 2 seinen |
| 1:13 | 7 | "Master the ancient art of the Steam Brewery..." | **B5 brouwerij** | koperen brouwketels (3, procedureel, bubbel-schaalanime) + `sw_canister` + `sw_crude_canister` op `sw_belt` **LOPEND**; warm sidelight |
| 1:20 | 7 | "...and let your flavors rule the Villager Market." | **B6 markt** | marktkraam `sw_depot` + `sw_cargo_wagon` + `Kitchen`-kist, bonte lanternen; **360°-orbit** om toonbank |
| 1:27 | 8 | (muziek-zwelling) | **B7 gears montage** | 5 × 0,8 s **punch-ins**: tandwiel/ketel/pijp/ventilator/grijper — elk 1 frame flits op de taiko-slag |
| 1:35 | 10 | — | **B8 chain reveal** | camera **trekt achteruit** door 12 m fabriek, alles draait; tekst: **SAKURA AND STEAM** (goud, 1 s in) |

---

## ACT 3 — THE CALL TO ARMS   1:45 – 2:45

| tc | duur | VO | SHOT | bron / camera |
|---|---|---|---|---|
| 1:45 | 6 | "But with great power... comes great threat." | **C1 duisternis** | zwart + 2 rode ogen die **knipperen** (emissie-schaal), onscreente tekst |
| 1:51 | 9 | "In the shadows of the mechanical cities lie elite dungeons." | **C2 dungeon-ingang** | poort van `sw_andesite_casing`-puin + `copper_paper_lantern`-rij die **uitgaan** (dim-oord, 1 per seconde) |
| 2:00 | 7 | "Dangerous depths... where only the strongest dare to tread." | **C3 monster-siluets** | `wl_wraith` + `wl_bear` silhouet met rim-light, ogen aan; camera **slow creep-in** |
| 2:07 | 8 | "Who will you be when the pressure rises?" | **C4 class menu** | 3 iconen op sokkel (`sw_arm_turret`, `sw_drill`, `sw_ejector`) in rij, één na één **oplichtend** |
| 2:15 | 8 | "Declare your race." | **C5 races** | 4 dier masks (`wl_deer`, `wl_boar`, `wl_bear_rare`, `wl_crane`) in cirkel, **top-down orbit**, 1 mask per tel |
| 2:23 | 8 | "Choose your RPG job within the Adventure Guild." | **C6 guild-hal** | zelfde hal als B6 maar **vanboven gefilmd**, lantaarn-rij naar troon; tekst: **ADVENTURE GUILD** |
| 2:31 | 8 | "Unlock powerful, custom trinkets." | **C7 trinkets** | 5 `sw_andesite_alloy`-ringen + `brass_ingot`-hangers, **draaiende macro** (300°/s), elk met emissie-gloed |
| 2:39 | 6 | "Because in this challenging world... you do not survive alone." | **C8 party** | 5 wezens + 2 machines schouder aan schouder als silhouet tegen gouden lucht, camera **crane-back** |

---

## ACT 4 — THE GRAND CLIMAX   2:45 – 3:35

| tc | duur | VO | SHOT | bron / camera |
|---|---|---|---|---|
| 2:45 | 9 | "Fight for your allies." | **D1 airship battle** | 2 × `em_zeppelin_dragon` + `em_airship_ghast` **in elkaars nabijheid vliegend** met rookspoor; **handheld-shake** (0,3° ruis) |
| 2:54 | 9 | "Protect what you have built." | **D2 defense** | `sw_arm_turret` × 3 die **omhoog richten**, `sw_pipe`-wallen, ember-regen; crash-zoom |
| 3:03 | 10 | "The pressure gauges are rising... the sky turns to rose and gold." | **D3 sky shift** | **time-lapse**: hemelkleur navy → rose → gold over 10 s (wereld-RGB keyframes), stad-silhouet `sw_cargo_wagon`-toren |
| 3:13 | 10 | "Claim your destiny. Ascend in rank, and become the legend this realm needs." | **D4 ascent** | camera **stijgt 40 m** langs alles wat er toe doet: sakura→fabriek→dungeon→vloot→koning; tekst **ASCEND** |
| 3:23 | 7 | (slot-akkoord) | **D5 freeze** | alles blijft 2 s stilstaan → **1 frame wit** → cut |
| 3:30 | 5 | — | **D6 black hold** | zwart, enkel bel-gloed |

---

## OUTRO — THE LANDING   3:35 – 3:50

| tc | duur | VO | SHOT | bron / camera |
|---|---|---|---|---|
| 3:35 | 4 | "Sakura and Steam." | **E1 logo sting** | eigen 3D-logo **uit zwart komend** (rotatie 8 s-clip → 4 s), stoom-werveling, messing-rand gloeit |
| 3:39 | 4 | "Your adventure awaits." | **E2 IP** | logo-center 520 px + `iekcorbar.com` (goud) + `discord.gg/G9P2khU7F4` (roze) |
| 3:43 | 4 | stilte / stoom-hiss fade | **E3 fade to black** | audio **abrupt stop**, laatste 2 s stil, slow fade |
| 3:47 | 3 | — | **E4 end card** | 2 logo's: app-tegel (anker) + merk-badge, 1 regel: "SEIZOEN 2 · 2026" |

---

## SHOT-PRODUCTIE LIJST (wat er gebouwd moet worden)

Alles in **één Blender-scene per set** + `make_trailer.py`-modes:
`still | close | anim` + frame-registry (LOPENDE machine-delen, particles,
lichtdim). Nieuw te bouwen sets (totaal 12):

1. `sakura_macro` — tak + 40 bloesems + vallende petals (deeltjessysteem)
2. `lantaarnreeks` — 5 × `brass_street_lamp`/`copper_paper_lantern`
3. `torii` — procedureel, lakrood
4. `stenen_gezichten` — 3 Jizo-koppen + oog-emissie
5. `factory_wide` — bestaand (al gebouwd) + draaiende rotor + stoom
6. `forge` — `crushing_wheel` + 20 `cogwheel` + ember-sproeiers (60)
7. `brouwerij` — 3 koperen brouwketels + lopende belt (`sw_belt_rollers` + blikken)
8. `markt` — depot/kraam/wagon + 12 kratzen + 8 lantaarns
9. `dungeon_poort` — puin + 8 dimmende lantaarns
10. `races_cirkel` — 4 diermaskers op sokkels + top-down-orbit
11. `vloot` — 2 zeppelins + ghast + rookspoor (curve-volgend)
12. `ascent_toren` — stapeling van alle eerdere elementen in één vertical dolly

**Al beschikt** (bouw niet dubbel): `iekcorbar-logo3d-anim.mp4`,
`tekst-uitkaart.png`, 4 bestaande stills (upgraden naar anim).

## TIMING-CONTROLES
- Som shots = exact 3:50 (229,92 s audio, geen drift; elke shot `duur` = 0,5 s-multipel)
- Cut-points vallen op taiko-slagen (uit audio detecteren: ffprobe loudness-pieken)
- Letterbox + korrel als **ffmpeg post-pass** (niet in Blender renderen) → goedkoper

## DEFINITIE VAN "KLAAR"
1. Alle 12 sets renderen ≥ 24 fps clip (of still + beweging in ffmpeg)
2. Montage: act 1-4 + outro = 1 mp4, audio erop
3. Post: letterbox, korrel, flitsen, whip-pan/transities
4. Kleurgrading: goud-roze look
