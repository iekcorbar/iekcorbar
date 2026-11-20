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
| | | | | | ⚪ |

### Shoutlander's Shadow (13)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `creature_skull` | Creature Skull | 1 | 1.5 | X | · | nee |
| `creature_ribcage` | Creature Ribcage | 3 | 3 | · | · | nee |
| `torn_settlement_banner` | Torn Settlement Banner | 3 | 3 | X | · | nee |
| `charred_outpost_lantern` | Charred Outpost Lantern | 1 | 1.0 | X | X | ja |
| `vigil_candles` | Vigil Candles | 1 | 1.0 | · | · | ja |
| `settlers_grave` | Settler's Grave | 3 | 3 | · | · | ja |
| `ruin_monolith` | Ruin Monolith | 2 | 1.0 | · | · | ja |
| `specimen_jar` | Specimen Jar | 1 | 1.0 | · | · | ja |
| `creature_egg_sac` | Creature Egg Sac | 1 | 1.0 | · | X | ja |
| `gibbet_cage` | Gibbet Cage | 1 | 1.0 | · | X | ja |
| `ash_pile` | Ash Pile | 1 | 1.0 | · | · | ja |
| `warding_totem` | Warding Totem | 2 | 1.0 | · | · | ja |
| `shattered_portal_shard` | Shattered Portal Shard | 1 | 1.0 | · | · | ja |

### Xmas Town (24)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `christmas_tree` | Christmas Tree | 3 | 1.5 | · | · | ja |
| `string_lights` | String Lights | 1 | 1 | X | X | ja |
| `advent_wreath` | Advent Wreath | 1 | 1 | X | · | ja |
| `steam_lantern` | Brass Steam Lantern | 1 | 1 | X | X | ja |
| `snow_lantern` | Snow Lantern | 1 | 1 | · | · | ja |
| `giant_candy_cane` | Giant Candy Cane | 2 | 1 | · | · | ja |
| `kadomatsu` | Kadomatsu | 1 | 1 | · | · | ja |
| `hearth` | Hearth | 3 | 1.5 | · | · | ja |
| `snowman` | Snowman | 2 | 1 | · | · | ja |
| `sleigh` | Sleigh | 1 | 1 | · | · | ja |
| `toy_train` | Clockwork Toy Train | 1 | 1 | · | · | ja |
| `gift_box` | Gift Box | 1 | 1.5 | · | · | ja |
| `advent_calendar` | Advent Calendar | 1 | 1 | X | · | ja |
| `music_box` | Music Box | 1 | 1 | · | · | ja |
| `snow_globe` | Snow Globe | 1 | 1 | · | · | ja |
| `market_stall` | Christmas Market Stall | 2 | 1.7 | · | · | ja |
| `cookie_plate` | Cookie Plate | 1 | 1 | · | · | ja |
| `gingerbread_house` | Gingerbread House | 1 | 1.15 | · | · | ja |
| `cuckoo_clock` | Cuckoo Clock | 1 | 1 | X | · | ja |
| `nutcracker` | Nutcracker Guard | 2 | 1.2 | · | · | ja |
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
| `rose_under_bell` | Rose Under a Bell | 1 | 1 | · | · | ja |
| `love_lock_rail` | Love Lock Rail | 1 | 1 | X | · | ja |
| `heart_arch` | Heart Arch | 2 | 2 | · | · | ja |
| `cupid_statue` | Cupid Statue | 1 | 1.3 | · | · | ja |
| `wishing_well` | Wishing Well | 1 | 1 | · | · | ja |
| `swan_lantern` | Twin Swan Lantern | 1 | 1 | · | · | ja |
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
| `lovers_bench` | Lovers' Bench | 1 | 2 | · | · | ja |
| `heart_window` | Heart Window | 1 | 1 | X | · | ja |
| `amore_machine` | Amore Automatron | 1 | 1 | · | · | ja |

### Easter (5)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `easter_egg` | Easter Egg | 1 | 1 | · | · | ja |
| `egg_basket` | Egg Basket | 1 | 1 | · | · | ja |
| `clockwork_bunny` | Clockwork Bunny | 1 | 1 | · | · | ja |
| `blossom_vase` | Blossom Vase | 1 | 1 | · | · | ja |
| `egg_tree` | Egg Tree | 1 | 1 | · | · | ja |

### St. Patrick's Day (6)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `pot_of_gold` | Pot of Gold | 1 | 1 | · | · | ja |
| `rainbow_arch` | Rainbow Arch | 1 | 1 | · | · | ja |
| `shamrock_planter` | Shamrock Planter | 1 | 1 | · | · | ja |
| `leprechaun_hat` | Leprechaun Hat | 1 | 1 | · | · | ja |
| `celtic_harp` | Celtic Harp | 2 | 1.7 | · | · | ja |
| `green_ale_keg` | Green Ale Keg | 1 | 1 | · | · | ja |

### Curiosities (26)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `brass_telescope` | Brass Telescope | 1 | 1 | · | · | ja |
| `steam_gauge_panel` | Steam Gauge Panel | 1 | 1 | X | · | ja |
| `tesla_coil` | Tesla Coil | 1 | 1 | · | · | ja |
| `gramophone` | Gramophone | 1 | 1.5 | · | · | ja |
| `torii_gate` | Torii Gate | 1 | 1.5 | · | · | ja |
| `maneki_neko` | Maneki-neko | 1 | 1 | · | · | ja |
| `shishi_odoshi` | Shishi-odoshi | 1 | 1 | · | · | ja |
| `bonsai` | Bonsai | 1 | 1 | · | · | ja |
| `byobu_screen` | Byobu Screen | 1 | 1 | · | · | ja |
| `wagasa_stand` | Wagasa Stand | 1 | 1 | · | · | ja |
| `notice_board` | Notice Board | 1 | 1 | · | · | ja |
| `wish_tree` | Wish Tree | 2 | 1 | · | · | ja |
| `pillar_box` | Pillar Box | 2 | 1 | · | · | ja |
| `dragon_nest` | Dragon Nest | 1 | 1.5 | · | · | ja |
| `sugi_dama` | Sugidama | 1 | 1 | · | · | nee |
| `koji_crock` | Koji Crock | 1 | 1 | · | · | nee |
| `bottling_crate` | Bottling Crate | 1 | 1 | · | · | nee |
| `barrel_stack` | Barrel Stack | 1 | 1 | · | · | nee |
| `steam_pipe` | Loose Steam Pipe | 1 | 1 | · | · | nee |
| `tasting_counter` | Tasting Counter | 1 | 1 | · | · | nee |
| `noren` | Noren | 1 | 1 | · | · | nee |
| `brew_kettle` | Brew Kettle | 1 | 1 | · | · | nee |
| `bottle_rack` | Bottle Rack | 1 | 1 | · | · | nee |
| `drunkard_barrel` | Drunkard in a Barrel | 1 | 1 | · | · | nee |
| `greeter_doll` | Greeter Doll | 1 | 1 | · | · | nee |
| `sweeper_doll` | Sweeper Doll | 1 | 1 | · | · | nee |

### Winter Onsen (8)
| id | naam | hb-hoogte | scale | wall | plafond | craft |
|---|---|---|---|---|---|---|
| `onsen_tub` | Hinoki Bath Tub | 1 | 1 | · | · | ja |
| `onsen_lantern` | Snow-Viewing Lantern | 1 | 1 | · | · | ja |
| `kotatsu` | Kotatsu | 1 | 1 | · | · | ja |
| `snow_bunny` | Snow Bunny | 1 | 1 | · | · | ja |
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