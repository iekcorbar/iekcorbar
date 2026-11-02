# Steamworks: pijpen-systeem (ontwerp)

Status: **fase 1 en fase 2 (automatisch deel) gebouwd in Steamworks 0.3.40, nog niet live.** Nog open: de aangedreven as-pomp (fase 2b). Besluiten staan onder punt 4.

Dit document gaat over de pijpen van Steamworks (vloeistof, olie-achtig). Het staat los van:
- `docs/steamworks-kinetics-design.md`: de rotatie-kant (tandwielen, assen).
- TechCore (andere auteur, eigen tubes en fluids): deelt niets met Steamworks.
- `docs/wiki-steamworks/07-oil.md`: de spelersuitleg van het huidige pijpsysteem. Bijwerken zodra iets verandert.

## 1. Huidige situatie (uit de code, 02-11)

- **Pijp** = één verbindingsstuk in het midden (`sw_pipe_center`) + één arm per zijde waar een vloeistofdeel zit (`sw_pipe_arm`). Zie `Visuals.updatePipe`.
- **Vloeistofdelen** (`Oil.fluidPart`): pijp, tank, pompjack, raffinaderij, stoomketel, pomp, klep.
- **Geleiders** (`Oil.conducts`): pijp en pomp altijd; klep alleen als hij draait.
- **Eindpunten:** tank en machines. Vloeistof stroomt via een zoektocht (BFS, max 1024 blokken), niet via druk of snelheid.
- **Armen verschijnen alleen naar vloeistofdelen.** Vanilla kisten, hoppers en containers krijgen géén arm. Machines zoals de ketel (kettle) of mixer ook niet.
- **Recept pijp:** 2 koperplaten + 1 kopering → 4 pijpen. Koperplaten vergen een eigen keten (staalhamer), dus pijpen zijn lastig te verkrijgen.
- **Bekende klachten (vd week):** pijpen sluiten niet aan bij het plaatsen naast een machine; het model draait niet naar de juiste richting.

## 2. Gewenst (door de eigenaar)

- Pijpen sluiten visueel aan op vanilla kisten, vaten, hoppers en containers.
- Ook functioneel: items en/of vloeistof worden door de pijpen naar en van containers verplaatst.
- Spelers moeten grote, ingewikkelde fabrieken kunnen bouwen.
- Pijpen moeten makkelijk te craften zijn.
- Pijpen moeten één blok in beslag nemen (volle blokbreedte) en naadloos aansluiten op de buur.

## 3. Fasen

**Fase 1: visueel aansluiten (laag risico)**
- Armen ook naar vanilla containers (kist, vat, hopper, oven, enz.) en naar machines die aansluitbaar zijn.
- Nieuwe maat: pijpstuk van rand tot rand van het blok, half blok dik, flenzen en verbindingsstuk 11/16.
- Oorzaak van "sluit niet aan" en "draait verkeerd" nalopen, eventueel met een tijdelijke logregel bij het plaatsen.
- Recept vereenvoudigen zodat pijpen zonder de staalhamer te maken zijn (bijv. direct uit kopering op de werkbank).

**Fase 2: items en vloeistof verplaatsen (groot)**
- Afhankelijk van de keuzes hieronder: aparte itempijp of gedeelde leiding.
- Overdracht van en naar containers met een duidelijke richting (in / uit).
- Snelheid en aandrijving bepalen hoe realistisch het aanvoelt.

**Fase 3: grote fabrieken (groot)**
- Pompen, kleppen en sorteerders om netwerken te sturen.
- Pas bouwen als fase 2 in-game getest is.

## 4. Besluiten (eigenaar, 02-11)

1. **Vloeistof en items:** één leiding voor beide.
2. **Transport:** beide manieren. Aangedreven pompen (via de as) en automatische leidingen (zoals een hopper).
3. **Containers:** alle vanilla-containers, behalve shulkerbox en enderchest. Voorstel voor de exacte lijst (nog te bevestigen): kist, getrapte kist, vat, hopper, dispenser, dropper, oven/hoogoven/rookoven, brouwstandaard.
4. **Richting:** elke aansluiting kent invoer en uitvoer.

**Open punten voor de bouw:**
- De exacte containerlijst bevestigen (zie punt 3).
- Hoe een leiding met items én vloeistof om gaat: twee aparte inhouden in één pijp, of één gemengde inhoud? Dit bepaalt de regels voor doorstroming.

## 5. Risico's

- **Prestaties:** grote fabrieken met veel leidingen en BFS-zoektochten. Limiet van 1024 blokken en de cache per netwerk moeten getest worden.
- **Chunk-grenzen:** armen worden bij het laden van een chunk berekend. Buren in een andere chunk kunnen tijdelijk missen (zie `SteamworksPlugin` regel 362-365).
- **Schaal:** pijpen op een volle blokbreedte veranderen het zicht op machines. Eerst de maat vastzetten, dan pas de functie.
- **Duplicatie:** TechCore heeft eigen tubes en fluids. Die niet verwarren met Steamworks.

## 6. Testen

Eén deploy en één test per fase, net als bij de dieren:
- Plaats pijpen naast kist, vat, hopper, ketel en tank. Sluiten ze aan, en draaien de armen goed?
- Herstart de server en controleer dezelfde opstelling.
- Grote opstelling (meer dan 50 pijpen) en de TPS-meting.
