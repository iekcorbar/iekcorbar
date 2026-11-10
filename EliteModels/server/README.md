# EliteModels / server — mirror van de EliteMobs content op de VPS

Deze map is de **bron van waarheid** voor de zelfgemaakte EliteMobs-content
(craftenmines-thema's, bossen, spawns, events). Live-stand:
`plugins/EliteMobs/` op de server. gespiegeld 10-11 (avond, na de content-update-reset).

| pad hier | equals op de server |
|---|---|
| `custombosses/events_craftenmine/` | `plugins/EliteMobs/custombosses/events_craftenmine/` |
| `customspawns/*.yml` | `plugins/EliteMobs/customspawns/` (9 eigen + 8 plugin-defaults) |
| `customevents/events_craftenmine/` | `plugins/EliteMobs/customevents/events_craftenmine/` |
| `npcs/`, `assigned.csv` | FMM/npc-assignlijst (zie tools/) |
| `mobproperties-enabled.txt` | de 42 elites die AAN staan in `plugins/EliteMobs/mobproperties/` |

## Twee harde lesjes (10-11)

1. **EliteMobs' automatische content-update poedert bij elke herstart
   `mobproperties/` plat met fabrieksdefaults** (86/90 elites uit → geen enkele natuurlijke
   elite meer). Hersteld uit `mobproperties.bak-pre-theme` (8 nov, de goede staat; alleen de
   `isEnabled`-vlaggen verschilden). Tegenmaatregel: `nightbreak.autoDownloadPluginUpdates:
   false` in `plugins/EliteMobs/config.yml` — UITLATEN NIET weer aanzetten. Verkeerde staat
   bewaard als `mobproperties.bak-20261110-reset` op de server. Bij een toekomstige reset:
   de `isEnabled: true`-lijst hierboven opnieuw afzetten op de 90 defs.

2. **Event-referenties zijn kale bestandsnamen, geen paden.** `spawnType:
   events_craftenmine/foo_spawn.yml` is Ongeldig — EliteMobs zoekt `customspawns/<naam>`
   (niet-recursief, geen map-prefix). Vandaar dat 4 seizoensevents nooit startten.
   Oplossing 10-11: alle eigen spawn-bestanden in de ROOT van `customspawns/` en de events
   naar kale namen. Controle: elk `spawnType` in `customevents/**/*.yml` moet als bestand in
   `customspawns/` bestaan.

## Synchroniseren

`customspawns/` en `customevents/` hierheen terughalen na serverwerk:

    scp -r iekcorbar-vps:/var/lib/pterodactyl/volumes/06b962a3-cf1d-4606-a254-a62027f0983d/plugins/EliteMobs/customspawns/*.yml EliteModels/server/customspawns/
    scp -r "iekcorbar-vps:/var/lib/pterodactyl/volumes/06b962a3-cf1d-4606-a254-a62027f0983d/plugins/EliteMobs/customevents/events_craftenmine/*.yml" EliteModels/server/customevents/events_craftenmine/
