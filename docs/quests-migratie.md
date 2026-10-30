# Quests-migratie: van BetonQuest naar IekQuests

> Besluit eigenaar 30-10: **alle quests gaan naar IekQuests; BQ gaat eraf.**
> Featurelijst hieronder is gehaald uit de 19 live-pakketten (scan van de
> instructies die greyarts echt gebruikt). Bestaande quests blijven staan en
> draaien totdat hun IekQuests-versie getest is.

## Wat IekQuests 0.1 al heeft

gespreks-stages (talk/craft/deliver/walk), rewards (money|eco|title|sound|say|
commando), herhaalcontracten met cooldown, /questbook-menu + boekvenster,
Citizens-NPC-klik, compass-knop, eigen data.yml, reload/reset.

## Wat 0.2 nog nodig heeft (feature-pariteit uit de scan)

| # | Feature | Waarvoor (pakketten) |
|---|---|---|
| 1 | **tags** per speler (add/del/voorwaarde) | álle quests (1122×) |
| 2 | **points**: +N, drempel-condities, %point%-toning in GUI | vriendschap/reputatie (65×) |
| 3 | **condities-boom**: and/or van tag/point/item/permission/tijd | overal |
| 4 | **vertakkingen**: keuze-opties per stage (gaat naar stage X) | gesprekken van alle quests |
| 5 | **gebeurtenis-reeksen**: lijst stappen met eigen delay | folders (688×) — Mad Doctor-scènes, presentaties |
| 6 | **random**: toevallige contractkeus + één-per-dag-vlag | daily contracten (45×) |
| 7 | **mobkill-doel** (type + aantal) | gevechtsquests, Jobs-ranges |
| 8 | **effect/teleport/give** als reward-type | presentatie + tutorials |
| 9 | **journaal**-lijst per quest (statusregels in boek) | 315× |
| 10 | NPC-koppeling meerdere NPC's per quest + "open shop"-actie | handelaars (mi stations / mmo shops) |
| later | region-conditie (WorldGuard, 8×) & mmoitem-conditie (12×) | alleen als migratie ze vraagt |

> **Stand 0.2.1 (30-10 04:30):** features 1-9 + shop-knoppen zijn IN de engine
> en getest via de generator; `mmoitem` (Rod_merchant) en `region` zijn het
> enige dat nog ontbreekt.

## Migratie-volgorde (risico oplopend)

1. **Onze eigen twee** (milo, halloween) — staan al in de questconfig, alleen nog
   0.2-features om de dialogen mooier te maken (voice per regel doet 0.1 al).
2. **Een handelaar als proef** (vorkan_kross): punten + random contract + take +
   shop-open-commando. Loopt dit → de andere vier + 8 eetwarens zijn gekopieer-
   werk.
3. **Backpack / Techcore / mad_doctor / cleric / CapitaUsop / guards**: praat-
   quests met tags en reeksen.
4. **Jobs-ranges** (5 pakketten): condities op jobs-niveau via
   commando's/placeholder (geen jobs-API nodig).
5. **Tutorial als laatste** — samen met greyarts (zijn antwoord op het DM van
   30-10), want diens ItemJoin-hooks + ras/klasse-assignors verdienen de nieuwe
   opzet (inkorten + slot).
6. **BQ eraf**: pas alsalles 2 weken foutloos draait: BQ-jar + QuestPackages weg,
   `bq action`-hooks (ItemJoin) vervangen door `/iequests tap <quest>`.

## Per-pakket-status (trechter)

| pakket | staat op | naar IekQuests |
|---|---|---|
| milo, halloween_lanterns | ✅ al in IekQuests | milo live; Igor npc-id 137 gekoppeld 30-10 09:25 (halloween live na reload + test) |
| vorkan/Armorshop/gemologist/Upgradestone | ✅ gemigreerd 0.2.1 | 12+12+12+11 contracten, cooldown 1320s, rep-punten, mi-shopknoppen |
| mmoitems_food_shop | ✅ gemigreerd 0.2.1 | 8×6 contracten (82 Aiko … 89 Yuki), 400c + eco |
| mad_doctor, cleric, CapitaUsop, Techcore | ✅ gemigreerd 0.4.0 (golf 3) | md_show-reeks, betaalde-cleric-optie, kompas-richtingen |
| Rod_merchant | ✅ gemigreerd 0.5.0 (golf 3b) | 12 viscontracten op COD + exacte display-naam (`name: "=Tuna"`), 1320s, rep, 3 shopknoppen |
| NPCGuideGuards (24×) | ✅ gemigreerd 0.5.0 (golf 3b) | guard_1..24 = één `menu`-stage met 21 keuzes (GUI verbreed naar 54 slots), keuzes sluiten het menu (close: true) |
| Backpack | ✅ gemigreerd 0.5.0 (golf 3b) | backpack_one/two/three + Orion-contracten (`deliverall`); pad/mentale-/kracht-tags via `qtag:` over quests; regionen omgezet naar flat-walk met coördinaten uit WorldGuard regions.yml |
| JobsRank1-5 | ✅ gemigreerd 0.5.0 (golf 4) | jobs_rankN met `npc:`-keuzes (per NPC welke master), `joinjob`/`leavejob` → /jobs als speler; JR1 geeft lp rank_1 |
| RankTime (leeg) | 🗄️ archief | deed niets; BQ's `RankTime>start_rank_system` was een dode verwijzing |
| tutorial | ⛓️ wacht op greyarts | golf 5 |

### Migratie-opmerkingen golf 1 (30-10 04:30)

- BQ gaf 1 willekeurig contract per dag; IekQuests toont de hele contracten-
  rij met per-contract cooldown (1320s) — zelfde tempo, transparanter menu.
- Cijfers 1-op-1 uit de pakketten: Vorkan/Brakka 250/400/650/900c, Petra 250-650,
  Aster 300-600, eetwarens 400c. Elitecoins erbij: 2-5 naar schaal (was er niet
  in BQ — eigenaar wil elitecoins in beloningen waar passend).
- Rep-punten: vorkan/brakka/petra/aster/aiko..._friendship +1 per contract.
  BQ's vriendjes-drempels (1/31/61/100 met eigen praatregels) zijn NOT
  migrated — gesprekken worden in golf 3 mooier (choices + condities kunnen het).
- NPC-klik botsing: ZOLANG beide draaien reageren BQ én IekQuests op dezelfde
  NPC. Bij deploy de 5 package-mappen mee naar QuestPackages-offline\ ver-
  places (TE-DEPLOYEN punt 53 zegt hoe).
- scripts/gen-iekquests-merchants.py = bron van de merchant/hall-YAML-blokken.
