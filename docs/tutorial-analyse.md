# Tutorial-analyse + permissielagen (peildatum 29-10-2026)

Alles wat de tutorial en de permissies eromheen betreft: huidige staat,
gevonden bugs en het slotplan. Puur analyse — er is nog niets aangepast.
Bedoeld als uitgangspunt
voor het gesprek met greyarts en de implementatie-ronde hierna.

## 1. De tutorial zelf (BetonQuest-pakket)

**Bron:** `plugins/BetonQuest/QuestPackages/tutorial/package.yml` op de VPS
(75 KB, 1129 regels, versie 3.2.0-QUEST-2). Géén kopie in de repo — moet naar
`pluginset-aangepast/BetonQuest/QuestPackages/tutorial/`.

**Opzet (kwaliteitswerk van greyarts):** 4 Citizens-NPC's (122 Tutorial,
123 Dialoog tussendoor verbergen/toon, 127 Race Assignor, 128 Class Assignor),
13 journal-hoofdstukken, ItemJoin-hooks (klik draagbare-werkbank/profiel/jobs-menu
= stap af via console `bq action …`), location- en craft-objectief, login-router
(start/hervat/niets), complete restart-flow en debug-reset; alle tags netjes
opgeruimd aan het einde.

**Loop:** teleport naar tutorial-gebied → gebouw bereiken → dialoog-NPC
( gespreksysteem) → E-menu-tour (werkbank, jobs, warps, lands, profiel) →
4 stokjes craften → hoofdstukken IV–VII = louter chatverhaal → ras kiezen (1
van 4) → klasse kiezen in drie rondes → klaar: teleport naar spawn + starterset.

**Volume:** ~3000 woorden (±45 chatmeldingen, 8 titels, 4 lange NPC-teksten,
12 ras/klasse-beschrijvingen, 13 journalpagina's). Grootste muur: hoofdstuk VII
"Expanded Gameplay Systems" = 18 meldingen achter elkaar (vissen, gewassen,
brouwen, Zeta, Oracles, TechCore) met delays 0,2–0,4 s.

**Ontdekte fouten:**
1. **Klasse-overschrijfbug (grootste):** `classes.yml` (ValhallaRaces) heeft 9
   klassen in EÉN klasse-slot; de tutorial roept 3× `races set class` → de
   uiteindelijke klasse is altijd de laatste ronde (gathering). Gevechts- en
   productieklasse worden stil overschreven; hun bonuses zijn nooit actief.
2. **Dubbele keuze:** ValhallaRaces config heeft `pick_race: true`,
   `pick_class: true`, `on_join: true` → bij login verschijnt al meteen de
   plugineigen ras-GUI, en daarna in de tutorial opnieuw via de Assignors.
3. **Spelfouten/conventies:** "Iekobar" in de dialoog-intro (moet Iek Corbar);
   letterlijke `<space>`; CJK-haakjes 「⚔」 in hoofdstuk VI (font-risico);
   tag-namen `Choosed_race/...` (inwendig); puntkomma-consistentie; "the"
   vergeten bij "makes Blacksmith useful"; journal-teksten dubbel (en-US+en).
4. **De "false choice"-grap** (geen ras? dan kies ik voor je) kost 3 kliks op
   het moment dat iemand wil spelen.

**Stroomlijnings-wensen (spelers willen spelen):** principe "doen boven lezen";
II–V + ras/klasse blijven; aanvoelbare-uitleg (VII, vis-tabellen, Zeta) uit de
chat → ouderwets boek "Adventurer's Guide" aan het einde (+ wiki-verwijzing);
klasse-rondes in één gesprek; login-hervatting = herinnering in plaats van
teleport.

## 2. De permissie-lagen (waarom spelers NU kunnen ontsnappen)

Op de server draaien **drie** lagen tegelijk:

1. **LuckPerms** (5.5.71) — de baas. Default-groep geeft o.a. `essentials.home,
   essentials.sethome, essentials.delhome, essentials.back, essentials.warp,
   landclaim.*, mine.*, jobs.use, valhalla.profile+skills, pf.*, brewery.*`.
2. **CMI** (9.8.11 + CMIEInjector) — **de echte home-provider**: er staat
   géén EssentialsX-jar op de server! De `essentials.*`-nodes in de
   default-groep zijn **dode erfenis**. De echte deuren heten
   `cmi.command.home`, `cmi.command.sethome`, `cmi.command.delhome`,
   `cmi.command.back`, `cmi.command.warp`, `cmi.command.spawn`,
   `cmi.command.tpa`, `cmi.command.tpahere` — en die staan in GEEN enkele
   LuckPerms-groep of -user ontkend → **iedere speler mag ze gewoon**.
3. **CommandBlockerPro** (1.4.1) — blokkeert zwart op wit `/help /plugins
   /version …` en heel `/jobs` (bypass `cb.admin`; regel 3 pakt alles met
   "jobs" in de tekst). Werkend, maar alleen voor dat lijstje.

### De CMI-rankladder — de echte ontsnappingsroute ⚠️

`CMI/Settings/Ranks.yml`: playtime-ladder **Adventure (default) → Kōsei (1 u) →
Kikai-shi (2 u) → Tetsu-bushi (3 u) → Karakuri (4 u) → Jōki Tennō (5 u)**, allemaal
`AutoRankup: true`, en elke promotie voert uit:

    lp user [playerName] parent set rank_N

`parent set` **vervangt de hele parent-lijst**. Een speler die gewoon idle
blijft (AFK telt mee als speeltijd) heeft na 1 uur automatisch `rank_1` — de
tutorial-groep vliegt dan uit zijn parents, en rank_1 erft zelf weer van
`default`: de lock is dan stilletjes verdwenen zónder dat de speler iets typte.
Alle lagere rang-entries (Branch1/2, Last) staan `Enabled: false` (schone
template-resten).

## 3. Slot-Plan (config-only, geen code)

1. **LP-groep `tutorial`** (default als parent):
   - negatieven: `cmi.command.home`, `.sethome`, `.delhome`, `.back`, `.warp`,
     `.tpa`, `.tpahere` (en voor de zekerheid de dode `essentials.*`-nodes ook),
     `landclaim.*`-claim-nodes en `mine.*`-menu-nodes. Welke systemen een tutorial-speler
    mag houden beslist de eigenaar (advies: jobs + valhalla.profile + chat blijven).
   - `iekcorbar.tutorial_done` krijgt deze groep nog níét; die node komt pas bij
      het voltooien (punt 2).
2. **BetonQuest-pakket**: actie `tutorial_start` krijgt
   `lp user %player% parent set tutorial`; actie `tutorial_complete` krijgt
   `lp user %player% parent set default` +
   `lp user %player% permission set iekcorbar.tutorial_done true`.
   (ItemJoin-stappen die `/warp` tonen eventueel inkorten zolang die knop
   geblokkeerd is.)
3. **CMI Ranks.yml**: bij Kōsei `PermissionRequirement:
   - iekcorbar.tutorial_done:De tutorial` — playtime alleen rankt dan níet meer
   door; na voltooiing draait de ladder exact zoals nu.
4. **Optioneel**: dode `essentials.*`-nodes uit de default-groep halen
   (opschonen, geen gedrag), en in `pluginset-aangepast/` een repo-kopie van
   zowel tutorial-package.yml als CMI Ranks.yml bijhouden.

## 4. Conceptbericht greyarts (30-10, nog NIET verzonden - eigenaar stuurt zelf z'n DM)

Drie blokken: (1) tutorial inkorten + klasse-overschrijfbug + dubbele raskeuze +
type-fouten; (2) slotplan: LP-groep `tutorial` met `cmi.command.*`-ontkenningen,
BQ-acties wisselen parent, Kōsei-rank krijgt `iekcorbar.tutorial_done`
PermissionRequirement (want de ladder runt `lp parent set rank_N` en wist elke
tutorial-groep na 1 uur idle); (3) nieuwe quests: agent schrijft
BetonQuest-YAML-pakketten + `/npc create`-lijst, greyarts schetst ideeën. Plus
vraag: repo-kopie van tutorial-package.yml + CMI Ranks.yml.

Versies: **Italiaans** (om te sturen) + **Nederlandse spiegel** - volledige
tekst in chat 30-10. Commando's/config-nodes blijven in beide in het Engels.

## 5. Nog te beslissen door de eigenaar

- Ja/nee op slot-plan hierboven (en welke permissies tutorial-spellers WEL houden).
- Klasse-keus: één eerlijke 9-klassekeuze (aanbevolen) of multi-class (VR-ontwerp).
- Adventurer's Guide-boek als vervanger van de praatmuur: ja/nee.
- Bericht naar greyarts (Discord, Engels): opsomming + vraag over
  klasse-overschrijf en dubbele raskeuze — concept in chat voordat het geplaatst wordt.
- Quests ronden erna: BetonQuest = pure YAML; de agent schrijft het pakket +
  de exacte `/npc create`-commando's, greyarts/eigenaar zet de NPC's eenmalig.
