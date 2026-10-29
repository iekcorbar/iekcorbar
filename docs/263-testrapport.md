# 26.3 Upgrade-test — eindrapport (29-10, 14:34)

Testopstelling: `E:\mctest26_3\` — Paper **26.3 build 230** (sha256 ok),
kopie van de backup van vannacht (world + plugins, DB's geblokkeerd),
draaiend op localhost:25599. Dit rapport is de "first boot report" die in
de staff-post is aangekondigd.

## Eindoordeel

**Paper 26.3 zelf: probleemloos.** Server doet `Done (60.4s)` met de echte
productiewereld (1229 regio's, eenmalig geconverteerd — want: conversie is
one-way, vandaar deze kopie-test).

| status | plugins |
|---|---|
| ✅ draaien (52) | o.a. ValhallaMMO/Races/Trinkets, MMOCore, MythicLib, MMOBridge, CMI, LuckPerms, Citizens, PlaceholderAPI, Jobs, BetonQuest, Skript, WorldEdit/Guard, ViaVersion+Backwards, Steamworks, Wildlife, Sakagura, Kitchen, Relics, Gouw + **ons eigen AfkCinema 0.1.0** |
| ❌ crashes (2) | **MMOItems 6.10.1** (NoSuchMethodError CraftItemStack.asCraftMirror in MythicLib 26.1/26.2-wrapper) → heeft nieuwe MythicLib-build nodig die 26.3 noemt · **DecentHolograms 2.10.1** weigert zelf ("Unsupported server version") → simpele update |
|  geisoleerd (hang) | **TechCore 0.9.33** blokkeerde de startup (hang in enable) + EliteMobs 10.9.10 (dervan) — beide wachten op een 26.3-build; op Spigot staan 10.9.11/TechCore "tested 26.3" → bij upgrade deze jars meenemen |
| ️ fatal (opgelost) | **Tally-datapack** in de world-folder: oude entity-predicates = *harde* registry-fout op 26.3 → server start níét meer. Verwijderd in de test; in de productiewereld moeten Tally.zip + eventuele soortgelijke datapacks vóór de upgrade vervallen/VERVANGEN worden door een Tally-build voor 26.3 |

## Bevindingen voor de echte upgrade-dag

1. Eerst de 3 jar-updates regelen (MMOItems+MythicLib+MMOCore-set,
   DecentHolograms, TechCore+EliteMobs), dán pas de server-jar wisselen.
2. **Elke** wereld-map checken op oude datapacks (Tally.zip zat in
   iekcorbarSeason2 én in de 3 zijwerelden) — die blokkeren de startup.
3. World-conversie is one-way → verse backup direct vóór de upgrade
   (automatisch 's nachts, handmatig na de laatste testronde).
4. ViaVersion+ViaBackwards bleven netjes draaien → 26.2-clients kunnen
   gewoon blijven inloggen ná de upgrade.
5. Resourcepack hoeft niet gewijzigd (onze supported_formats-constructie
   pakt formats 1–999).
6. Blauwdruk voor de nodb-laag staat in scripts/patch-test-nodb*.ps1 —
   volgende testronde (Live-pariteit) opnieuw te gebruiken.

## Wat er nú al kan in de testomgeving

* Inloggen met eigen client (26.3-profiel) op `127.0.0.1:25599`
* **AfkCinema**: 3 min niets doen → bioscoopdraaibaan om je personage;
  of direct: `/cine film [speler]` · `/cine stop` · `/cine automatisch`
* Niets van dit alles raakt de productiedatabase (0 pogingen gelogd).