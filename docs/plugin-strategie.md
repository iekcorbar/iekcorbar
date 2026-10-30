# Pluginstrategie: naar eigen beheer

> Besluit eigenaar 30-10 (~06:10): "mijn doel is om eigenlijk alle
> plugin/werking in eigen beheer te hebben, varianten ervan, zodat ik gewoon
> gelijk minecraft kan updaten en plugins updaten, zonder een paar weken te
> hoeven wachten op nieuwe releases van derde partijen."
> Context: de BetonQuest → IekQuests-slag is de bewezen pilot. Dit document
> is de routekaart voor de rest.

## Why

- Derde-party vertraging: MC-update → wachten op plugin-builds (dit jaar
  nog meegemaakt met 26.2: MMO-stack crashte op CraftItemStack, DecentHolograms
  wilde niet laden, BQ-pakketten moesten handmatig ge-repaird).
- Customisatie-muur: wat niet in hun config zit, bestaat niet (of vereist
  hacks via Skript/brute commando's).
- Prijs/koppeling: premium-bundels (MythicCrusade-set) delen één API-fundering;
  één verkeerde update raakt alles.

## Bewezen pattern (uit IekQuests)

1. Scan wat de derde-party-partij ECHT gebruikt op onze server (geen
   luister-postie: alleen wat in onze config/content zit).
2. Eigen plugin met precies die features + onze eigen data-files (yaml/GUI).
3. Beide een tijdje naast elkaar; content omzetten; derde-party jar weg.
4. Artefacten: jar via plugin-release-ritueel, content in repo, BQ-les:
   altijd een back-up-map (hier: QuestPackages-offline).

## Routekaart per plugin

| derde-party | wat gebruiken we écht | eigen variant | status/prioriteit |
|---|---|---|---|
| BetonQuest | quests, contracts | **IekQuests** | ✅ grotendeels geïmplementeerd: 18 quests live; rest = Rod_merchant (zie MMOItems), Backpack, guards, jobs-ranks, tutorial |
| MMOItems | 25 station-shops (handelaars/eetwarens), Pyrofish-vissen, item-tiers | **IekShops** (GUI-winkel uit yaml) + vanilla/vissen met FMM-modellen | ① hoog — shops zijn de spil van de handelaars; fish kan naar vanilla-mat + eigen model |
| MythicLib | alleen als API-ondergrond van MMOItems/MMOCore | vervalt zodra IekShops + custom items draaien | meelopen met ① |
| MMOCore | 0 player-data; alleen gebruikt via MMOBridge uitrusten | vervalt (niets verliest het data) | laag risico, samen met ① |
| MMOBridge | health/level/drops/equip-koppeling EliteMobs↔MMO | **in IekElites opnemen** (eigen elite-schaling: attributes via vanilla AttributeModifier + yaml) | middel — de damage-kwesties zitten HIER; eigen kopie = eigen schuld |
| EliteMobs | álle standaard-mob-spawns zijn elitemobs met custom config | **IekElites** (spawn-tabel + tiers + modifiers + loot via vanilla loot_tables/datapacks) | ② groot werk — maar dit IS de kern van de server |
| TechCore | staal-ambachten (MATERIAL:STEEL_INGOT + model) | **Steamworks-familie** (eigen industrie doet dit al deels) | ③ — eerst inventory van welke TechCore-recepten echt gebruikt worden |
| CMI + CMILib | money, /home, /warp, kits, afk, gamemode-rommel | eigen **IekCore-small**: CMI is enorm; wat écht gebruikt wordt is klein (money/tp/kits). Stap 1 = inventory per CMI-feature | ④ langstrekken; CMI is stabiel + updates snel — minste pijn |
| Citizens | NPC's | blijft (vrijwel al ons eigen werk hangt eraan: IekQuests-klik, MetaCompass) — of later NPC-laag in eigen engine | blijft nu; niet-urgent |
| ValhallaMMO/Races/Trinkets | ras/klasse-systeem (tutorial!, cleric-reset) | **IekRaces/IekClasses** — RPG-kern; eigen data = eigen balans | ⑤ groot, maar de tutorial-lockdownplannen hangen hier nu aan vast |
| PlaceholderAPI / skript / ProtocolLib / packetevents / Vault | infrastructuur | blijven (open source, geen premium-vertraging) | nooit eigen variant nodig |

## Onafhankelijk-update-regel (vanaf nu)

- Bij élke MC-/Paper-update: alle eigen plugins + third-party-builds op de
  testbank (E:\mctest26_3-patronen) vóór de productiestart.
- Third-party plugins waar we van af willen: géén nieuwe content erin meer
  maken (alle new-content gaat in eigen plugin's data-files).
- Eigen plugins: api-version altijd =actuele Paper; geen deprecated API's die
  een MC-update kunnen breken.

## Gevolg voor vanavond/morgen

- MythicLib/MMO-stak BLIJFT voorlopig (shops + elitemobs hangen ervan af).
  Damage-vervel → pas weg als IekShops + IekElites de overname doen.
- Korte-termijn verzachting (los te vragen): MMOBridge health/level-opties
  fintunen zodat EliteMobs-damage voorspelbaarder wordt; vastleggen wat
  Bridge precies verandert (REA-ItemJoin-queue heeft dezelfde methode).
- tutorials + jobs-ranks: migratie naar IekQuests blijft zoals gepland
  (wacht op greyarts over lockdown); nieuwe content vanaf nu NIET meer in
  BQ/MMO-series zetten.
