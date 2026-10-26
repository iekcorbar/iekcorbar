# Hihou - geheugen/voortgang (bijhouden TIJDENS het werk)

Laatste bijgewerkt: 2026-10-26 ~session 2. Taak van deze sessie: **BedWars-port etappe
0.3.0** uit de verkenning-hieronder - arena's + teams + join in de nieuwe bw/ module.

## v0.1.0 - KISTEN-KANT (klaar)
- Vijf types, allemaal decor-prop-kit (propkit/sync.py: Hihou -> ns "hihou"):
  treasure / event / vote / lucky / victory chest. ELKE geplaatste kist is UNIEK:
  inhoud wordt per kist opgeslagen (world:x:y:z -> Bukkit-object Base64 lijst) in
  plugins/Hihou/chests.json. Geen vanilla chest-blootstelling: alles in de list.
- Sleutels = fysieke items (PAPER + PDC "key_type" + eigen model), 1 verbruik per
  unlock. Main-hand-only. `/ih key <type> [player] [amt]` werkt ook vanuit de
  CONSOLE -> vote-reward-haak is gewoon: vote_reward_config.command =
  `ih key %player% vote` (geen code-wijzigingen in SiteSync).
- GUI's: speler-opent = alleen eruit halen (klik in upper-cancel + verplaats naar
  speler; inventory vol -> terug in kist); `/ih edit` (kijk naar kist) = staff
  bewerk-GUI; opslaan gebeurt op close. `/ih remove` wist kist+loot; `/ih map`
  maakt een Treasure Map (centrum-card op kist, coords afgerond op 32 - geen pin).
- Lucky = per unlock 1 gewogen-random uit config.lucky-pool ("MATERIAL [amt] [wt]").
- Event-kisten zijn slot buiten `/ih event open|close` (broadcast).
- Bouw: break-hits config (100) + geen recipes (alleen /ih give). Solid barrier-
  basis, onbreekbaar; open-models via Decor.open(p,60) - zelfde patroon als de zoo.
- Pack: tools/make_pack.py (propkit Pack; elke model heeft particle-textuur ✓).
  15 modellen. Merge-packs SOURCES uitgebreid -> Iekcorbar-pack.zip bevat hihou nu.

## v0.2.0 - GAME ISLAND AUTOMATISERING (klaar, deze sessie)
- Gebruikersvragen: 'hoe stel ik de regio in?', 'worldguard is geen optie',
  'hoe weet je hoe groot de regio moet zijn?', 'de spellen zijn gewoon vanilla
  blokken'. Kern-uitleg die landde: een drukplaat is voor de server zomaar een
  blok - je wijst Hihou ONEs aan wat de finish/midden IS; daarna automatisch.
- Games.java, 3 modi, registry in games.json:
  * race  : `/ih game set X finish` terwijl je naar de drukplaat kijkt (8 blk
            reikwijdte). Fysiek betreden (Action.PHYSICAL op dat blok) = winner,
            raam in seconden (default 600, "verlopen zonder winnaar" netjes).
  * last  : `/ih game set X center` (staan in midden) + `size <r>` (vierkant->
            cirkel). Participanten geLOCKT bij start (in-cirkel, alive, survival/
            adventure); timer om -> nog binnen = winner(s).
  * manual: geen detectie; `/ih game win X <speler>` door staff (battleship,
            connect4, cart shooting). Werkt ook als staff-override op race/last.
  Stoppen/herstart: running-sessies overleven geen restart (log-wijsmaker).
- Prijs per spel: `prize none | key <type> [amt] | chest` - chest geeft Victory
  Key + teleport naar digstbijzijne geplaatte victory_chest (geen kist? key blijft,
  grey broadcast). Prijswinnaar: END_ROD particles + challenge-complete sound.
- Commando-tree: create|set|start|stop|win|delete|list (+tab, +plugin.yml usage).
- mvn groen 0.1.0 EN 0.2.0; jar in root, oudere verwijderd.

## v0.3.0 - BEDWARS ETAPPE 1: ARENA + TEAMS + JOIN (klaar, deze sessie)
- Bron-materiaal: BedWars1058 v25.2 is GPL-v3 (via github.com/andrei1058/BedWars1058, tag 25.2
  = exact deze jar). Dus GEEN code kopiëren: specs uit de public source gehaald (Arena.java,
  GameStartingTask, LegacyTeamAssigner, CmdJoin) + signature-check via de JDK-javap op de jar
  (REA-JEB provider draait hier niet - javap + inventory volstond). Eigen implementatie in
  bw/ package met onze patronen.
- Nieuwe module Hihou/src/main/java/com/iekcorbar/hihou/bw/: BwArena (config-velden + runtime
  status/players/goods/teams; Status=WAITING/STARTING/PLAYING - restarting is 0.4.0), BwTeam
  (naam + DyeColor + spawn + members), BwGameManager (registries, setup-commands, join/leave,
  countdown-tick, launch, balance, stop, restoreAll), BwListeners (quit/death/PvP-hooks).
- Data: plugins/Hihou/arenas.json onszelf format (GEEN BedWars-yml's): arenas.<naam>.spawn
  ("world:x:y:z:yaw:pitch" één string), center+size = ONZE regio-primitief uit Games (vierkant
  -> cirkel; nu opgeslagen, 0.4.0's generators/bedden/mapreset gaan erin leven), min (default 2),
  maxTeam (default 4), teams.<t>.color (DyeColor) + .spawn. maxPlayers = teams x maxTeam.
- Join-flow (trouw aan de original, minus Vault/partijen/VIP): /ih bw join <arena> [team] -
  checks (nog nergens, arena ready, niet PLAYING, niet vol) -> Goods hand-in (locatie, gamemode,
  inventory+armor, health/food/xp) -> leeg inventory, ADVENTURE, tp naar waiting-spawn, effecten
  eraf, vlieg uit. Join-bericht {on}/{max} naar de lobby. Spelers >= min -> auto-countdown 10s.
- Countdown = GameStartingTask-patroon: title+chat+pling op 10 en 5..1; staff kan forceren
  (/ih bw start <arena> [sec]) en onder min spelers valt de teller terug naar WAITING
  ("Countdown stopped - not enough players") - exact zoals removePlayer daar doet.
- Teams: bij launch worden alle NIET-gekozen spelers gebalanceerd (shuffled, kleinste team met
  ruimte - LegacyTeamAssigner zonder party-gedeelte); gekozen teams via /ih bw join X <team>
  blijven. Start: SURVIVAL + tp naar team-spawn, GO!-title in teamkleur + teamlijst in chat.
- Teruggeven (PlayerGoods-idee): leave/stop/quit/death -> inventaria, gamemode, locatie,
  health/food/xp terug; quit tijdens spel herstelt direct in het event (documentatie: offline-vast
  ReJoin-store = 0.4.0). Death in spel = uit de wedstrijd (bedden/eliminatie is pas 0.4.0).
- PvP-poort (onze variant van hun listener-brij): eigen team binnen een PLAYING-arena -> bounce
  met actionbar; geen PvP zodra één van beiden in een lobby staat; buiten-gangers kunnen arena-
  spelers niet raken en omgekeerd. Alleen op lidmaatschap gecheckt (geen world/coördinaat-eisen),
  want onze arena's staan in de normale survivalwereld.
- Commando-tree /ih bw create|set spawn|center|size|min|maxteam|team add|del|spawn|join|leave|
  start|stop|delete|list|info (+tab: arenanamen, teamnames, kleuren). DyeColor-namen (geen aqua/
  gold - Light blue/cyan; tab geeft 8 gangbare). plugin.yml usage uitgebreid; mvn groen 0.3.0.

## Test-flow (in-game, na deploy Hihou-0.3.0.jar + nieuwe Iekcorbar-pack.zip)
1. `/ih give treasure` -> plaatsen -> `/ih edit` -> spullen erin -> sluiten.
2. `/ih key treasure` -> klik kist zonder = rood actionbar; met = open-model +
   GUI; loot halen; opnieuw = "The chest is empty."
3. `/ih give lucky` + `/ih key lucky` -> random prize uit pool + broadcast.
4. `/ih give vote` + `/ih key <naam> vote 3` -> stemmings-flow; later de
   vote_reward-hook (zie boven) aanzetten op de site.
5. `/ih map` -> toont regio van kist op coord-32-blok.
6. Restart: kisten + inhoud + slot-estado's intact (chunk PDC + chests.json).
7. GAME: `/ih game create race race` -> kijk naar finishplaat ->
   `/ih game set race finish` -> `/ih game start race 120` -> eroverlopen =
   winnaar + prijs. `/ih game create puddles last` -> midden + `size 14` ->
   `start puddles 60` -> teller-out = laatste in cirkel wint.
   `/ih game create bs manual` + `win bs <naam>` voor battleship.
8. BEDWARS 0.3.0: `/ih bw create test` -> sta op de lobby-oplek -> `/ih bw set test spawn` ->
   `/ih bw team test add red red` + `/ih bw team test add blue blue` -> allebei erbij staan ->
   `/ih bw team test spawn red` (blauw op eigen plek staan). Twee accounts ->
   `/ih bw join test` -> 10s countdown (titel + pling) -> GO, elk team naar z'n spawn.
   `/ih bw leave` geeft alles terug (locatie/inventory). Probeer eigen team te slaan -> actionbar.
   `/ih bw start test 5` forceert als staff; `/ih bw stop test` hurkt iedereen terug.

## Open opties (overleg met Barend)
- Wereld-spawn van treasure-kisten (config-kans per interval) - nu alleen staff-plaatsen.
- Wiki-pagina voor Hihou (docs/wiki-hihou + seed + Import-knop), net als Gouw.
- Site: /steamworks-achtige pagina? Later, eerst de engine in-game bewijzen.
- Event-chests automatisch openzetten per news-event? Nu handmatig /ih event.

## Regels (zoals altijd)
- Nooit live-DB benaderen; mvn groen + in-game-check door Barend is het bewijs.
- Na elke afronding: commit + push via git-beheer; jar in root (buiten git).

## BEDWARS-PORT VERKENNING (2026-10-26 - etappe 0.3.0 KLAAR, zie v0.3.0 hierboven)
Gebruiker: "C:\Users\baaye\Downloads\bedwars-plugin-25.2.jar rea-eden en aan Hihou
toevoegen. Zo uitgebreid mogelijk." (dit is de oude BedWars-backlog-notitie uit
CLAUDE.md in andere vorm - nu via RE van een bestaande plugin).

Verkend (repo-sessie liep vol net voor de start - hier de brandstof):
- Jar: 2,4 MB, 1391 entries. plugin.yml: naam **BedWars1058** v25.2, author
  andrei1058, main com.andrei1058.bedwars.BedWars, api-version 1.13, softdepend:
  Vault, PlaceholderAPI, Citizens, Parties, SlimeWorldManager, VipFeatures, ...
  Code in com/ (527+) + io/ (41 - Likely lib package, check of dat MineSuperFacility
  /andrei1058-shared lib is). Geen assets/rein resources bundles.
- Dit is de gekende OPEN-SOURCE plugin (github.com/andrei1058/BedWars1058) -
  REA-decompilatie klopt dus met publiek bekende architectuur: arenas (yml's),
  teams, spawns, store, generators (iron/gold/diamond...), beds + protection,
  game-status tick-loop, listener-brij, spawners/respawn, kits, upgrades
  (trap/enderchest/multishot/sharpness/protection), helpers (Vault-economie,
  Partij-API).
- VOLGENDE SESSIE PLAN: (1) REA-skill + decompile via rea-tools van de jar
  (packages: com.andrei1058.bedwars.* -> map features); (2) beslissen wat
  "Hihou-ified" wordt: arena-data in eigen games.json-achtig format, teams
  via eigen state (GEEN Party-API), generators met onze sounds/deeltjes,
  bed-detectie via eigen prop-systeem (barrier+ItemDisplay kist als 'bed'?),
  shop = Chests-keyless GUI, upgrades = items met eigen modellen uit onze pack;
  (3) module-structuur: Hihou/src/.../bw/ - BwArena, BwGameManager, BwTeams,
  BwGenerators, BwStore, BwListeners; (4) etappes: 0.3.0 arena+teams+join,
  0.4.0 generators+bed+suddain death, 0.5.0 shop+upgrades+kits.
- 0.3.0 STATUS (sessie 2): (1)-(3) en arena+teams+join KLAAR. REA-JEB provider draait hier
  niet; signatures via JDK-javap op de jar + publieke GitHub-source tag 25.2 als
  gedragsspecificatie (GPL - dus eigen implementatie, geen kopieen). Restanten etappe 1:
  mapreset/restarting (0.4.0), ReJoin/offline-goederen-store (0.4.0), spectators (0.4.0),
  winnaar-detectie via bed-loot (0.4.0), sudden-death y-kill + game-timer (0.4.0),
  generators op onze center+size-regio + steamworks-sounds (0.4.0), shop/upgrades/kits (0.5.0).
- Let op: Hihou 0.3.0 is de huidige HEAD (game-engine + bw arenas/teams/join, jar in root).
  De kist/sleutel-kant hoeft niet anders - BedWars-shop is apart.
- Commando-toekomst: /ih bw create|join|start|... + arena's per wereld (of
  regionbox uit onze center+size-regio-primitieven - die zijn er al!).
