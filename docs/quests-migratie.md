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
| milo, halloween_lanterns | ✅ al in IekQuests | klaar (na deploy + test) |
| vorkan/Armorshop/Rod/gemologist/Upgradestone | BQ | stap 2 |
| mmoitems_food_shop | BQ | stap 2 (zelfde proef, 8× herhalen) |
| Backpack, Techcore, mad_doctor, cleric, CapitaUsop, NPCGuideGuards, RankTime (leeg) | BQ | stap 3 |
| JobsRank1-5 | BQ | stap 4 |
| tutorial | BQ | stap 5, met greyarts |
