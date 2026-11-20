# Relics — afmetingen: stel-lijst & protocol

**Doel:** maten van relics in-game afstellen zonder tussendoor te bouwen/deployen.
Jij meldt wat je ziet en wat het moet worden → ik pas de code meteen aan (schaal/height/model)
→ **er gebeurt niets op de server** tot jij "klaar" zegt. Dan: één jar-build + één pack-build + één deploy.

## Protocol per melding
1. **Jij:** `"relic_id: nu ~X blokken (visueel|hitbox), moet Y"` — id's staan in de tabellen hieronder.
   Tip voor meten: F3 geeft je eigen y; tel blokken van de grond tot het hoogste deel van het model.
   Vuistregel: visuele hoogte ≈ model-eenheden/16 × scale; hitbox = `height` (floor-plaatsing).
2. **Ik:** pas `RelicType` (height/scale) en/of `tools/make_pack.py` (model zelf) aan, zet de rij
   in de stel-lijst op ✅ code-aanpassing, bouw NIET, deploy NIET.
3. **Aan het eind:** jars-bump (0.7.11) + BUILD 67, `merge-packs.py` + `audit-pack.py` groen,
   jar+pack in `deploy/`, één TE-punt, één uitrol, clients één keer herstarten.

Let op:
- De merge-packs **Y-FIT** pass krimpt modellen >2 bloken automatisch met een java-schaal compensatie —
  een gemelde maat gaat dus altijd om het **eindresultaat in het spel**, niet om de losse getallen.
- Y-spread van `Relics.java` (display-positionering) en mount-rotaties kunnen meespelen; ik verifieer
  per item waar de maat vandaan komt voordat ik een getal aanraak.

## Stel-lijst (fill this in via meldingen)
| # | relic | observatie (nu) | gewenst (moet) | aanpassing | status |
|---|---|---|---|---|---|
| 1 | `gamenight_crate` | schaal 1,0 (~0,7 blk hoog, ~0,75 breed) | **visueel 2x** | `RelicType.scale` 1f → **2f** (java-only, zelfde truc als Krampus; hitbox blijft 1) | ✅ code-aanpassing, wacht op één gezamenlijke build |
| 2 | `gamenight_key` | schaal 1,0 | **visueel 2x** | `scale` 1f → **2f** — geplaatste sleutel ~1,4 blk hoog; item-in-hand blijft normaal formaat | ✅ code-aanpassing, wacht op build |
| 3 | `creature_skull` | schaal 1,5 | **1,5x zo groot** | `scale` 1,5f → **2,25f** — geplaatste/muurschedel groeit mee; gedragen schedel op het hoofd blijft vanilla-formaat (dragon-head-display, los mechanisme); hitbox blijft 1 | ✅ code-aanpassing, wacht op build |
| 4 | `creature_ribcage` | schaal 3,0 | **in verhouding met de (grotere) schedel** | `scale` 3f → **4,5f** (dezelfde ×1,5; verhouding ribcage/skull blijft 2,0; eronderdoorlopen blijft, hitbox `NONE`), vorm/model niet aangepast | ✅ code-aanpassing, wacht op build |
| 5 | `charred_outpost_lantern` | schaal 1,0 (constructie zonder scale-veld) | **1,5x zo groot** | naar volledige constructor: `scale` **1,5f** (floor+wall+hanging meegeschaaald, lichtlevel 11 blijft, light-basis 1 blok) | ✅ code-aanpassing, wacht op build |
| 6 | `ruin_monolith` | schaal 1,0 | **4x zo groot** | naar volledige constructor: `scale` **4f** — zuil ~4x, barrière-hitbox blijft 2 blokken (Krampus-precedent); glyph-glow volgt display, ENCHANT-deeltjes staan vast bij y+0,6 (bodem) - nacomment van eigenaar nodig of meepakken | ✅ code-aanpassing, wacht op build |
| 7 | `torn_settlement_banner` | schaal 3,0 (15-eenheden model, ~2,8 blk) | **3x, optie 2** | banner_cloth gegeneraliseerd (h/w-schaalfactoren, old calls unchanged), floor-banner herbouwd: paal 0..29 eenheden, lat bovenaan, dook 13x15 + scheuren; wall-bracket meebouwd. `scale` 3f → **4,5f** (visueel ~8,2 blk hoog, gemeten uit modelspan), height 3 → **5** (vloerbereikbaarheid: plaatsing vraagt 5 luchtblokken). Bounds gecheckt: alles -16..32 ✓. **LET OP: pakt pack-bouw mee (make_pack) - eerste stel-melding die íets aan het model doet** | ✅ code + model-aanpassing, wacht op build |
| 8 | `gibbet_cage` | schaal 1,0 (9-args) | **2x visueel** | naar volledige constructor: `scale` **2f** (vloer + hangend meeschakend; craftable blijft false) | ✅ code-aanpassing, wacht op build |
| 9 | `warding_totem` | schaal 1,0 (9-args) | **2x** | `scale` **2f** (ook de lit-variant - zelfde RelicType, draw() herberekent schaal); barriere-hitbox blijft 2 | ✅ code-aanpassing, wacht op build |
| 10 | `advent_wreath` | - | **helemaal uit het spel** | RelicType-constante + Effect.ADVENT + recipe + place/loadChunk/candles-veld + info-regel + adventCandles() weg; **deur 21** → `relics:plum_pudding` + `relics:hot_chocolate 2`; **modellen blijven bewust in het pack** (oude kransen in inventarissen blijven renderen, geen paarse kubus); geplaatste kransen verdwijnen bij de eerstvolgende restart (displays niet persistent). **Bij de uitrol: ook deur 21 in de LIVE config.yml aanpassen - jar overschrijft nooit de serverconfig!** | ✅ code+config-aanpassing, wacht op build |
| 11 | `steam_lantern` | schaal 1,0 (wél gewoon gegeven; model zit in de pack) | **2x** | `scale` 1f → **2f** (floor+wall+hanging; lichtblok level 14 blijft) | ✅ code-aanpassing, wacht op build |
| 12 | `snow_lantern` | schaal 1,0 | **2x** | `scale` 1f → **2f** (lichtblok 1 blk level 12 blijft) | ✅ code-aanpassing, wacht op build |
| 13 | `giant_candy_cane` | schaal 1,0 + lore "two blocks" | **2x** | `scale` 1f → **2f** + lore "nearly four blocks" (hitbox 2, dye-verven blijft) | ✅ code-aanpassing, wacht op build |
| 14 | `hearth` | schaal 1,5 | **2x** | `scale` 1,5f → **3f** | ✅ code-aanpassing, wacht op build |
| 15 | `snowman` | schaal 1,0 | **2x** | `scale` 1f → **2f** | ✅ code-aanpassing, wacht op build |
| 16 | `sleigh` | schaal 1,0 | **3x** | `scale` 1f → **3f** + zitplek volgt de schaal (`sit()` 0,35·(scale−1) hoger) | ✅ code-aanpassing, wacht op build |
| 17 | `toy_train` | schaal 1,0 | **4x** | `scale` 1f → **4f** (draai-effect om zelfde anker) | ✅ code-aanpassing, wacht op build |
| 18 | `gift_box` | schaal 1,5 | **2x** | `scale` 1,5f → **3f** (alle drie cadeau-wikkels meegeschakend); rechtsklik/vullen/locken werkt op dezelfde plek (geen apart hitboxje) | ✅ code-aanpassing, wacht op build |
| 19 | `advent_calendar` | schaal 1,0 | **3x** | `scale` 1f → **3f** (deur-logica/kalender zelf blijft op dezelfde plek; klikken gebeurt op het centrale blok van de relic) | ✅ code-aanpassing, wacht op build |
| 20 | `music_box` | schaal 1,0 | **2x** | `scale` 1f → **2f** (playlist/afspeelgedrag ongewijzigd, geluid komt van hetzelfde anker) | ✅ code-aanpassing, wacht op build |
| 21 | `snow_globe` | schaal 1,0 + saai model | **2x + leuker + nieuw model** | `scale` 1f → **2f**; model herbouwd: geelkoperen voet, glazen bol met chalet (verlicht raam), twee pijnbomen, sneeuwman, sneeuwvlokken in de bol (35 elementen, bounds -16..32 ✓, palette kleuren ✓); shake-effect: 6 stijgende ringen SNOWFLAKE-deeltjes + extra chime, met validatie-guard tegen gebroken relics. **Raakt zowel jar als pack** | ✅ code+model-aanpassing, wacht op build |
| 22 | `cuckoo_clock` | schaal 1,0 | **2x** | `scale` 1f → **2f** (vloer+muur); geluid/particles van de koekoek volgen de schaal (0,45×scale i.p.v. vast 0,9) | ✅ code-aanpassing, wacht op build |
| 23 | `nutcracker` | schaal 1,2 | **zelfde als Krampus** | `scale` 1,2f → **2,9f** (Krampus-schaal exact overgenomen, hitbox 2 blijft); lore aangepast. Interpretation: even groot als de Krampus, niet 'nogmaals 2x' - als je laatste bedoelde, eindigt hij op 2,4 | ✅ code-aanpassing, wacht op build |
| 24 | `chandelier` | schaal 1,0 | **3x** | `scale` 1f → **3f** (vloer/muur/plafond-variant meegeschakend, lichtblok level 13 en twinkle ongewijzigd) | ✅ code-aanpassing, wacht op build |
| 25 | `santa_mailbox` | schaal 1,0 | **2x + leuke hint** | `scale` 1f → **2f**; bij posten: 1 van 5 deterministische Noordpool-notities in de chat (met o.a. hint over het gift-window via `giftBoxes().window()`), zelfde patroon als Krampus-oordelen | ✅ code-aanpassing, wacht op build |
| 26 | `countdown_clock` | schaal 1,0 | **2x** | `scale` 1f → **2f** (aftellen/sound via actionbar op dezelfde plek) | ✅ code-aanpassing, wacht op build |
| 27 | `new_year_garland` | schaal 1,0 | **2x** | `scale` 1f → **2f** (vloer+muur, twinkle ongewijzigd) | ✅ code-aanpassing, wacht op build |
| 28 | `heart_balloon` | schaal 1,0 | **3x** | `scale` 1f → **3f**; HOP-deining volgt de schaal (0,15×scale) zodat het grotere hart ook echt zichtbaar dobbert | ✅ code-aanpassing, wacht op build |
| 29 | `rose_under_bell` | model met massieve 'glass'-panes (palette is ondoorzichtig) - roos onzichtbaar | **beter model** | rose_bell herbouwd: open klok met 4 dunne hoekpijlers + koepelrand, echte gelaagde roos (buitenste/innerste bloembladen, hart, stengel met doornen, 2 bladeren, gevallen bloemblad), geelkoperen voet met naamplaatje. 22 elementen, bounds ✓. **Ook de sneeuwbol (#21) op dezelfde manier open gemaakt** (39 elementen, bounds ✓), anders bleef het dorpje achter ondoorzichtig glas verscholen | ✅ model-aanpassing, wacht op build |
| 30 | `heart_arch` | schaal 2,0 | **2x** | `scale` 2f → **4f**; hart-particles volgen de schaal (spread/hoogte evenredig) | ✅ code-aanpassing, wacht op build |
| 31 | `cupid_statue` | schaal 1,3 | **4x** | `scale` 1,3f → **5,2f** (beeld op ware grootte, hitbox 1 blijft) | ✅ code-aanpassing, wacht op build |
| 32 | `wishing_well` | schaal 1,0 | **2x** | `scale` 1f → **2f**; wens-hartjes stijgen mee (0,6×scale) | ✅ code-aanpassing, wacht op build |
| 33 | `swan_lantern` | schaal 1,0 | **2x** | `scale` 1f → **2f** (lichtblok level 12 blijft 1 blok, vlamdeeltjes op dezelfde plek) | ✅ code-aanpassing, wacht op build |
| 34 | `lovers_bench` | schaal 2,0 + 1 zitplek | **2x + 2 spelers naast elkaar** | `scale` 2f → **4f**; `sit()` telt zitplekken per relic (bank = 2, rest = 1), plaatsingen langs de lokale X-as (±0,22×scale, meegedraaid met `rotationRaw`), zithoogte volgt de schaal (0,21×scale). Afstappen/break ruimt alle plekken al op | ✅ code-aanpassing, wacht op build |
| 35 | `heart_window` | schaal 1,0 + dichte ondoorzichtige 'glass'-pane | **2x + erdoorheen kijken** | `scale` 1f → **2f**; model: volledig glass-pane verwijderd, open frame met mullions + zwevende harten en dunne glazen rand-accenten (20 elementen, bounds ✓). Lichtblok level 6 is onzichtbaar en belemmert het zicht niet | ✅ code+model-aanpassing, wacht op build |
| 36 | `egg_tree` | schaal 1,0 | **3x** | `scale` 1f → **3f** | ✅ code-aanpassing, wacht op build |
| 37 | `pot_of_gold` | schaal 1,0 | **2x** | `scale` 1f → **2f**; regenboog-stof volgt schaal (0,7×scale) | ✅ code-aanpassing, wacht op build |
| 38 | `rainbow_arch` | schaal 1,0 | **6x** | `scale` 1f → **6f** (regenboog-stof volgt de schaal sinds #37; loop-through, hitbox blijft leeg) | ✅ code-aanpassing, wacht op build |
| 39 | `creature_skull` + `leprechaun_hat` | schedel zit ín het hoofd; hoed staat verkeerd op hoofd | **draagpositie corrigeren** | `HEAD_DISPLAY` in make_pack: schedel `translation [0,13,-2] @0,95` → **[0,18,-4] @1,1**; hoed had géén head-transform → **[0,16,0] @1,1**. Let op:.pack-zijde, kan ik niet zelf visueel checken - na de build even met een speler op het hoofd bekijken en evt. fijn bijstellen | ✅ model-aanpassing, wacht op build |
| 40 | `green_ale_keg` | schaal 1,0 | **2x** | `scale` 1f → **2f** | ✅ code-aanpassing, wacht op build |
| 41 | `brass_telescope` | schaal 1,0 | **3x** | `scale` 1f → **3f** (zoom-effect via actionbar/potion ongewijzigd) | ✅ code-aanpassing, wacht op build |
| 42 | `torii_gate` | schaal 1,5 | **1,5x** | `scale` 1,5f → **2,25f** (loop-through poort, hitbox blijft leeg) | ✅ code-aanpassing, wacht op build |
| 43 | `shishi_odoshi` | schaal 1,0 | **2x** | `scale` 1f → **2f** (klak-animatie via variant-model, ongewijzigd) | ✅ code-aanpassing, wacht op build |
| 44 | `byobu_screen` | schaal 1,0 | **2x** | `scale` 1f → **2f** | ✅ code-aanpassing, wacht op build |
| 45 | `notice_board` | schaal 1,0 | **2x** | `scale` 1f → **2f**; Hangings: `box()`-coördinaten, tekstdisplay-positie/formaat en Interaction-hitbox (1,1×1,65) volgen de schaal | ✅ code-aanpassing, wacht op build |
| 46 | `wish_tree` | schaal 1,0 | **3x** | `scale` 1f → **3f**; Hangings: takken/bladeren/wensstroken + hitbox (1,1×1,9) volgen de schaal | ✅ code-aanpassing, wacht op build |
| 47 | `dragon_nest` | schaal 1,5 | **2x** | `scale` 1,5f → **3f** (nest en Wildlife-drakenpaar groter op het nest) | ✅ code-aanpassing, wacht op build |
| 48 | `barrel_stack` | schaal 1,0 | **2x** | `scale` 1f → **2f** (shop-only, niet-craftbaar) | ✅ code-aanpassing, wacht op build |
| 49 | `tasting_counter` | schaal 1,0 | **2x** | `scale` 1f → **2f** (shop-only) | ✅ code-aanpassing, wacht op build |
| 50 | `noren` | op muur geplaatst = model 90° gekanteld ('op zn kop'); formaat is goed | **plaatsing beperken tot vloer** | `onInteract`: Norén weigert wall/ceiling-mount met hint ("place it on the ground") - het doek-model is alleen floor getekend. Bestaande verkeerd geplaatste Norén: slopen + op de grond terugzetten | ✅ code-aanpassing, wacht op build |
| 51 | `bottle_rack` | formaat goed, muur-orientatie verkeerd (geen wall-model → 90° gekanteld) | **eigen muur-model** | `bottle_rack_wall()` in make_pack (27 elementen, spiegeling in Z: rug z≈16, flessen de kamer in, bounds ✓) + `wall` veld in RelicType + MODELS-registratie. Bestaand verkeerd geplaatst exemplaar: slopen + tegen muur terugzetten | ✅ code+model-aanpassing, wacht op build |
| 52 | `drunkard_barrel` | schaal 1,0 | **2x** | `scale` 1f → **2f** (pose-varianten a/b meegeschakend) | ✅ code-aanpassing, wacht op build |
| 53 | `greeter_doll` | schaal 1,0 | **3x** | `scale` 1f → **3f** (beide poses) | ✅ code-aanpassing, wacht op build |
| 54 | `sweeper_doll` | schaal 1,0 | **3x** | `scale` 1f → **3f** (beide poses) | ✅ code-aanpassing, wacht op build |
| 55 | `onsen_tub` | schaal 1,0 | **2x** | `scale` 1f → **2f** (stoom-effect ongewijzigd) | ✅ code-aanpassing, wacht op build |
| 56 | `onsen_lantern` | schaal 1,0 | **2x** | `scale` 1f → **2f** (lichtblok level 12 blijft) | ✅ code-aanpassing, wacht op build |
| 57 | `snow_bunny` | schaal 1,0 | **+20%** | `scale` 1f → **1,2f** (oor-poses meegeschakend) | ✅ code-aanpassing, wacht op build |
| | | | | | ⚪ |

### Shoutlander's Shadow (13)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `creature_skull` | Creature Skull | 1 | 1.5 | X | · | nee |
| `creature_ribcage` | Creature Ribcage | 3 | 3 | · | · | nee |
| `torn_settlement_banner` | Torn Settlement Banner | ~~3~~ → **5** | ~~3~~ → **4.5** (#7) | X | · | nee |
| `charred_outpost_lantern` | Charred Outpost Lantern | 1 | ~~1.0~~ → **1.5** (#5) | X | X | nee |
| `vigil_candles` | Vigil Candles | 1 | 1.0 | · | · | nee |
| `settlers_grave` | Settler's Grave | 3 | 3 | · | · | nee |
| `ruin_monolith` | Ruin Monolith | 2 | ~~1.0~~ → **4.0** (#6) | · | · | nee |
| `specimen_jar` | Specimen Jar | 1 | 1.0 | · | · | nee |
| `creature_egg_sac` | Creature Egg Sac | 1 | 1.0 | · | X | nee |
| `gibbet_cage` | Gibbet Cage | 1 | ~~1.0~~ → **2.0** (#8) | · | X | nee |
| `ash_pile` | Ash Pile | 1 | 1.0 | · | · | ja |
| `warding_totem` | Warding Totem | 2 | 1.0 | · | · | ja |
| `shattered_portal_shard` | Shattered Portal Shard | 1 | 1.0 | · | · | ja |

### Xmas Town (23 — advent_wreath verwijderd, #10)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `christmas_tree` | Christmas Tree | 3 | 1.5 | · | · | ja |
| `string_lights` | String Lights | 1 | 1 | X | X | ja |
| `advent_wreath` | Advent Wreath | 1 | 1 | X | · | ja |
| `steam_lantern` | Brass Steam Lantern | 1 | 1 | X | X | ja |
| `snow_lantern` | Snow Lantern | 1 | 1 | · | · | ja |
| `giant_candy_cane` | Giant Candy Cane | 2 | 1 | · | · | ja |
| `kadomatsu` | Kadomatsu | 1 | 1 | · | · | ja |
| `hearth` | Hearth | 3 | ~~1.5~~ → **3** (#14) | · | · | ja |
| `snowman` | Snowman | 2 | 1 | · | · | ja |
| `sleigh` | Sleigh | 1 | 1 | · | · | ja |
| `toy_train` | Clockwork Toy Train | 1 | 1 | · | · | ja |
| `gift_box` | Gift Box | 1 | ~~1.5~~ → **3** (#18) | · | · | ja |
| `advent_calendar` | Advent Calendar | 1 | 1 | X | · | ja |
| `music_box` | Music Box | 1 | 1 | · | · | ja |
| `snow_globe` | Snow Globe | 1 | 1 | · | · | ja |
| `market_stall` | Christmas Market Stall | 2 | 1.7 | · | · | ja |
| `cookie_plate` | Cookie Plate | 1 | 1 | · | · | ja |
| `gingerbread_house` | Gingerbread House | 1 | 1.15 | · | · | ja |
| `cuckoo_clock` | Cuckoo Clock | 1 | 1 | X | · | ja |
| `nutcracker` | Nutcracker Guard | 2 | ~~1.2~~ → **2.9** (#23) | · | · | ja |
| `chandelier` | Candle Chandelier | 1 | 1 | X | X | ja |
| `plum_pudding` | Plum Pudding | 1 | 1.25 | · | · | ja |
| `santa_mailbox` | Santa Mailbox | 1 | 1 | · | · | ja |
| `krampus` | Krampus | 2 | 2.9 | · | · | ja |

### New Year (10)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `firework_launcher` | Firework Launcher | 1 | 1 | · | · | ja |
| `countdown_clock` | Countdown Clock | 2 | 1 | · | · | ja |
| `temple_bell` | Temple Bell | 2 | 1 | · | · | ja |
| `kagami_mochi` | Kagami Mochi | 1 | 1 | · | · | ja |
| `shimenawa` | Shimenawa | 1 | 1 | X | X | ja |
| `red_paper_lantern` | Red Paper Lantern | 1 | 1 | · | X | ja |
| `confetti_cannon` | Confetti Cannon | 1 | 1 | · | · | ja |
| `champagne_tower` | Sparkling Cider Tower | 1 | 1 | · | · | ja |
| `new_year_garland` | New Year Garland | 1 | 1 | X | · | ja |
| `countdown_board` | New Year Countdown Board | 2 | 2.7 | X | · | ja |

### Valentine's (20)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `heart_balloon` | Heart Balloon | 1 | 1 | · | · | ja |
| `rose_under_bell` | Rose Under a Bell | 1 | 1 (#29: nieuw model) | · | · | ja |
| `love_lock_rail` | Love Lock Rail | 1 | 1 | X | · | ja |
| `heart_arch` | Heart Arch | 2 | ~~2~~ → **4** (#30) | · | · | ja |
| `cupid_statue` | Cupid Statue | 1 | ~~1.3~~ → **5.2** (#31) | · | · | ja |
| `wishing_well` | Wishing Well | 1 | ~~1~~ → **2** (#32) | · | · | ja |
| `swan_lantern` | Twin Swan Lantern | 1 | ~~1~~ → **2** (#33) | · | · | ja |
| `valentine_banner` | Valentine Banner | 1 | 1 | X | · | ja |
| `heart_lantern` | Heart Lantern | 1 | 1 | X | X | ja |
| `teddy_bear` | Teddy Bear | 1 | 1 | · | · | ja |
| `candy_heart_jar` | Candy Heart Jar | 1 | 1 | · | · | ja |
| `bouquet_popper` | Bouquet Popper | 1 | 1 | · | · | ja |
| `rose_planter` | Rose Planter | 1 | 1 | · | · | ja |
| `love_songbird` | Love Songbird | 1 | 1 | · | · | ja |
| `sweetheart_topiary` | Sweetheart Topiary | 2 | 1.6 | · | · | ja |
| `love_potion_shelf` | Love Potion Shelf | 1 | 1 | X | · | ja |
| `clockwork_heart` | Clockwork Heart | 1 | 1 | · | · | ja |
| `lovers_bench` | Lovers' Bench | 1 | ~~2~~ → **4** (#34) | · | · | ja |
| `heart_window` | Heart Window | 1 | ~~1~~ → **2** (#35) | X | · | ja |
| `amore_machine` | Amore Automatron | 1 | 1 | · | · | ja |

### Easter (5)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `easter_egg` | Easter Egg | 1 | 1 | · | · | ja |
| `egg_basket` | Egg Basket | 1 | 1 | · | · | ja |
| `clockwork_bunny` | Clockwork Bunny | 1 | 1 | · | · | ja |
| `blossom_vase` | Blossom Vase | 1 | 1 | · | · | ja |
| `egg_tree` | Egg Tree | 1 | ~~1~~ → **3** (#36) | · | · | ja |

### St. Patrick's Day (6)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `pot_of_gold` | Pot of Gold | 1 | ~~1~~ → **2** (#37) | · | · | ja |
| `rainbow_arch` | Rainbow Arch | 1 | ~~1~~ → **6** (#38) | · | · | ja |
| `shamrock_planter` | Shamrock Planter | 1 | 1 | · | · | ja |
| `leprechaun_hat` | Leprechaun Hat | 1 | 1 | · | · | ja |
| `celtic_harp` | Celtic Harp | 2 | 1.7 | · | · | ja |
| `green_ale_keg` | Green Ale Keg | 1 | ~~1~~ → **2** (#40) | · | · | ja |

### Curiosities (26)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `brass_telescope` | Brass Telescope | 1 | ~~1~~ → **3** (#41) | · | · | ja |
| `steam_gauge_panel` | Steam Gauge Panel | 1 | 1 | X | · | ja |
| `tesla_coil` | Tesla Coil | 1 | 1 | · | · | ja |
| `gramophone` | Gramophone | 1 | 1.5 | · | · | ja |
| `torii_gate` | Torii Gate | 1 | ~~1.5~~ → **2.25** (#42) | · | · | ja |
| `maneki_neko` | Maneki-neko | 1 | 1 | · | · | ja |
| `shishi_odoshi` | Shishi-odoshi | 1 | ~~1~~ → **2** (#43) | · | · | ja |
| `bonsai` | Bonsai | 1 | 1 | · | · | ja |
| `byobu_screen` | Byobu Screen | 1 | ~~1~~ → **2** (#44) | · | · | ja |
| `wagasa_stand` | Wagasa Stand | 1 | 1 | · | · | ja |
| `notice_board` | Notice Board | 1 | ~~1~~ → **2** (#45) | · | · | ja |
| `wish_tree` | Wish Tree | 2 | ~~1~~ → **3** (#46) | · | · | ja |
| `pillar_box` | Pillar Box | 2 | 1 | · | · | ja |
| `dragon_nest` | Dragon Nest | 1 | ~~1.5~~ → **3** (#47) | · | · | ja |
| `sugi_dama` | Sugidama | 1 | 1 | · | · | nee |
| `koji_crock` | Koji Crock | 1 | 1 | · | · | nee |
| `bottling_crate` | Bottling Crate | 1 | 1 | · | · | nee |
| `barrel_stack` | Barrel Stack | 1 | ~~1~~ → **2** (#48) | · | · | nee |
| `steam_pipe` | Loose Steam Pipe | 1 | 1 | · | · | nee |
| `tasting_counter` | Tasting Counter | 1 | ~~1~~ → **2** (#49) | · | · | nee |
| `noren` | Noren | 1 | 1 | · | · | nee |
| `brew_kettle` | Brew Kettle | 1 | 1 | · | · | nee |
| `bottle_rack` | Bottle Rack | 1 | 1 (#51: wall-model) | · | · | nee |
| `drunkard_barrel` | Drunkard in a Barrel | 1 | ~~1~~ → **2** (#52) | · | · | nee |
| `greeter_doll` | Greeter Doll | 1 | ~~1~~ → **3** (#53) | · | · | nee |
| `sweeper_doll` | Sweeper Doll | 1 | ~~1~~ → **3** (#54) | · | · | nee |

### Winter Onsen (8)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `onsen_tub` | Hinoki Bath Tub | 1 | ~~1~~ → **2** (#55) | · | · | ja |
| `onsen_lantern` | Snow-Viewing Lantern | 1 | ~~1~~ → **2** (#56) | · | · | ja |
| `kotatsu` | Kotatsu | 1 | 1 | · | · | ja |
| `snow_bunny` | Snow Bunny | 1 | ~~1~~ → **1.2** (#57) | · | · | ja |
| `wash_set` | Wash Stool and Bucket | 1 | 1 | · | · | ja |
| `towel_rack` | Towel Rack | 1 | 1 | · | · | ja |
| `sake_tray` | Floating Sake Tray | 1 | 1 | · | · | ja |
| `snow_pine` | Snow-Laden Pine | 2 | 1.5 | · | · | ja |

### Halloween (2)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `bone_xylophone` | Bone Xylophone | 1 | 2.25 | · | · | ja |
| `blood_token` | Blood Token | 1 | 1 | X | · | nee |

### Game Night (7)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `gamenight_crate` | Winner's Crate | 1 | 1 | X | · | nee |
| `gamenight_key` | Game Night Key | 1 | 1 | · | · | nee |
| `gamenight_trophy` | Champion's Trophy | 1 | 1 | · | · | nee |
| `gamenight_sword` | Champion's Blade | 1 | 1 | X | · | nee |
| `gamenight_sash` | Winner's Sash | 1 | 1 | · | · | nee |
| `gamenight_dice_tower` | Dice Tower | 1 | 1 | · | · | nee |
| `gamenight_certificate` | Winner's Certificate | 1 | 1 | X | · | nee |