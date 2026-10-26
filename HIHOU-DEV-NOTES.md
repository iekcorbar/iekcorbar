# Hihou - geheugen/voortgang (bijhouden TIJDENS het werk)

Laatste bijgewerkt: 2026-10-26 ~09:10. Taak van de gebruiker: nieuwe plugin voor
**treasure/event/vote chests + lucky chests**, mooie modellen + fysieke sleutels,
én **Game-Island-spelautomatisering** (winnaar-detectie + prijzen).

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

## Test-flow (in-game, na deploy Hihou-0.2.0.jar + nieuwe Iekcorbar-pack.zip)
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

## Open opties (overleg met Barend)
- Wereld-spawn van treasure-kisten (config-kans per interval) - nu alleen staff-plaatsen.
- Wiki-pagina voor Hihou (docs/wiki-hihou + seed + Import-knop), net als Gouw.
- Site: /steamworks-achtige pagina? Later, eerst de engine in-game bewijzen.
- Event-chests automatisch openzetten per news-event? Nu handmatig /ih event.

## Regels (zoals altijd)
- Nooit live-DB benaderen; mvn groen + in-game-check door Barend is het bewijs.
- Na elke afronding: commit + push via git-beheer; jar in root (buiten git).
