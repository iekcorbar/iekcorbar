# Hihou - geheugen/voortgang (bijhouden TIJDENS het werk)

Laatste bijgewerkt: 2026-10-26 ~08:40. Taak van de gebruiker: nieuwe plugin voor
**treasure/event/vote chests + lucky chests**, mooie modellen + fysieke sleutels,
én (v0.2) **Game-Island-spelautomatisering** (nu moeten staff nog handmatig
winnaars aanwijzen/ Prijzen uitdelen).

## v0.1.0 - KISTEN-KANT (BOUW GRAAN, jar Hihou-0.1.0.jar in repo-root)
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
  basis, onbreekbaar voor suiker; GATE patroon: `_open` model via Decor.open(p,60).
- Pack: tools/make_pack.py (propkit Pack; elke model heeft particle-textuur ✓).
  15 modellen. Merge-packs SOURCES uitgebreid -> Iekcorbar-pack.zip bevat hihou nu.

## Test-flow (in-game, na deploy van Hihou-0.1.0.jar + nieuwe Iekcorbar-pack.zip)
1. `/ih give treasure` -> plaatsen -> `/ih edit` -> leg spullen erin -> sluit.
2. `/ih key treasure` -> klik kist zonder sleutel = rood actionbar; met = open-
   model + GUI; loot halen; opnieuw proberen = "The chest is empty."
3. `/ih give lucky` + `/ih key lucky` -> random prize uit pool + chat broadcast.
4. `/ih give vote` + `/ih key <naam> vote 3` -> stemmings-flow (later: console-hook).
5. `/ih map` -> toont regio van kist op coord 32-blok.
6. Restart: kisten + inhoud + slot-estado's moeten intact terugkomen (chunk PDC + chests.json).

## v0.2 - GAME-ISLAND-AUTOMATISERING (NOG TE BOUWEN - plan)
Spelen op eiland: battleship, connect4, joust, speed racing, cart shooting,
the maze, avoid the puddles, guess the block. Detectie-methode per speltype:
- FINISHP: eerste speler op finish-blok wint (speed racing, maze, joust-overleving)
- LASTSTAND: laatste speler binnen een regio wint (avoid the puddles, joust)
- ANSWER: `/ih game <naam> answer A|B|C|D` - wie op die plaat staat wint (guess the block)
- MANUAL: `/ih game <naam> win <speler>` (battleship, connect4, cart shooting)
Registratie: `/ih game create <naam> <type>` + kijk-knoppen start/finish/join +
regio-sizes. `/ih game start <naam> [duur]` met countdown; prijs per parcour
instelbaar: key(sleutel direct in pocket) OF teleport-neer bij Victory-kist.
Reeds bestaande primitives: Decor-at-block, rayTrace, region-containment (Zoo
near()), Interactie-entity voor platen (of gewoon echte drukplaten detecteren
via PressurePlateBlock - eenvoudiger!). Prioriteit: PressurePlate + PLAYER_MOVE
met regio-check op 5-tick-interval is goedkoop genoeg.

## Open opties (overleg met Barend)
- Wereld-spawn van treasure-kisten (config-kans per interval) - nu alleen staff-plaatsen.
- Vote-kist automatisch op spawn plaatsen + state per stem (nu: keys via reward-hook).
- Wiki-pagina voor Hihou (docs/wiki-hihou + seed + Import-knop), net als Gouw.
- Site: /steamworks-achtige pagina? Later, eerst game-engine werkend.

## Regels (zoals altijd)
- Nooit live-DB benaderen; mvn groen + in-game-check door Barend is het bewijs.
- Na elke afronding: commit + push via git-beheer; jar in root (buiten git).
