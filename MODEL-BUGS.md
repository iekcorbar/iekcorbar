# Model/Item/Mob Bug Tracker — iekcorbar

**Doel:** Tijdens de testavond worden hier alle modellen, items, mobs en props die niet
correct functioneren genoteerd. Ze worden **niet** vanavond gerepareerd — alleen in kaart
gebracht. Reparaties gebeuren op een later moment, model voor model.

**Regels voor deze avond:**
- Geen server-restart, geen deploy, geen pack-rebuild.
- Alleen documenteren wat kapot is (symptoom + waar het gezien is).
- Eén regel per probleem; meerdere symptomen bij hetzelfde model = meerdere regels.

**Status-legenda:** `[ ]` open · `[x]` gerepareerd · `[~]` deels / omzeild

---

## Hoe invullen (kopieer een regel en pas aan)

```
- [ ] #001 | <naam model/item/mob> | <type> | <plugin/mod> | <symptoom> | <waar gezien> | <prioriteit>
```

Type: `item` · `mob` · `block/prop` · `model` · `texture` · `animatie`
Prioriteit: `hoog` · `middel` · `laag`

---

## Items

| # | Naam | Type | Plugin/Mod | Symptoom | Waar gezien | Prio | Status |
|---|------|------|------------|----------|-------------|------|--------|
|   |      |      |            |          |             |      | [ ]    |

## Mobs

| # | Naam | Type | Plugin/Mod | Symptoom | Waar gezien | Prio | Status |
|---|------|------|------------|----------|-------------|------|--------|
| 1 | 13 CC0-dieren (hert/zebra/olifant/giraf/leeuw/everzwijn/makak/pinguin/ara/kreeft/orca/koi/piranha) | mob/animatie | Wildlife | bots niet gecentreerd (rotaties om modelhoek), staartpivot op staarttip, hoofdpivot op schedeltop, poten afgeknipt (giraf/olifant), ara/penguin-kop mis-ingedeeld, kreeft mank, krab liep opzij | in-game 11-11 | hoog | [x] her-rig 11-11 (mesh_rerig.py + build54 + Wildlife 0.9.27), wacht op uitlevering |
| 2 | Olifant (mesh) | model | Wildlife | blijft voxel-blob: greedy-merge heeft borst+poten aan één plaat gesmeed, kleurkwantisatie gaf vleugelflinkers | preview3d 11-11 | laag | [ ] alleen op te lossen door opnieuw voxelliseren — Quaternius-OBJ is niet in de repo (bron opnieuw downloaden) |

## Blocks / Props (placeable)

| # | Naam | Type | Plugin/Mod | Symptoom | Waar gezien | Prio | Status |
|---|------|------|------------|----------|-------------|------|--------|
|   |      |      |            |          |             |      | [ ]    |

## Models / Textures / Animaties

| # | Naam | Type | Plugin/Mod | Symptoom | Waar gezien | Prio | Status |
|---|------|------|------------|----------|-------------|------|--------|
|   |      |      |            |          |             |      | [ ]    |

## Overig (UI, geluid, recepten, advancements, ...)

| # | Naam | Type | Plugin/Mod | Symptoom | Waar gezien | Prio | Status |
|---|------|------|------------|----------|-------------|------|--------|
|   |      |      |            |          |             |      | [ ]    |

---

## Bevindingen audit (vooraf — 2026-11-08)

Automatisch gevonden door de repo/pack te inspecteren. Deze items zijn **nog niet gerepareerd**;
ze komen vanavond / later aan de beurt. Geen deploy/restart vanavond.

### Wildlife — spawn eggs ontbreken (9 soorten)

De `EGG`-tabel in `Wildlife/tools/make_zoo_pack.py` (regel 48-59) bevat alleen de oudere
soorten. De 9 soorten die in 0.9.15/0.9.16 zijn toegevoegd staan er niet in, dus
`eggs()` genereert geen `egg_<id>`-model voor ze → paarse/magenta spawn egg in Zukan en
in het spel.

| # | Naam | Type | Plugin | Symptoom | Waar gezien | Prio | Status |
|---|------|------|--------|----------|-------------|------|--------|
| 1 | Giraffe spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 2 | Lion spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 3 | Hyena spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 4 | Arctic Fox spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 5 | Snowy Owl spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 6 | Reindeer spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 7 | Fennec spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 8 | Meerkat spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |
| 9 | Vulture spawn egg | mob/spawn egg | Wildlife | model was ontbrekend; nu geregenereerd in build 48, wacht op deploy | Zukan / in-game | hoog | [~] |

**Oorzaak:** `EGG`-dict in `make_zoo_pack.py` mist deze 9 entries. Stat-items (`stat_*`)
voor deze soorten zijn wél aanwezig, alleen de spawn eggs niet.

**Status (2026-11-08, gedeploy):** de 9 regels zijn toegevoegd aan `EGG` in
`make_zoo_pack.py` en de Zoo-pack + merged pack (build 48) zijn opnieuw gegenereerd
(`audit-pack.py` groen, 0 problemen). Build 48 is **gedeployed naar de server** (upload +
oude zip verwijderd + `rspm reload`, geen server-restart). Client moet herstarten om de
nieuwe pack te downloaden. Status van de 9 entries: gedeplay, wacht op client-test → `[~]`.

### Zukan — "items die niet bij Kitchen horen"

In `Kitchen/src/.../ZukanData.java` worden recepten opgebouwd met `#tag`-expansies
(bijv. `#logs`, `#planks`). Die tags worden in `options()` uitgevouwen tot vanilla
Material-items, die via `Catalog.add()` als entries met origin=null in de globale catalogus
belanden. In de Zukan-GUI kunnen daardoor vanilla items (hout, planken, …) zichtbaar
worden in de buurt van Kitchen-recepten, of onder de verkeerde groepering vallen.

**Status:** code-oorzaak geïdentificeerd; in-game verificatie nodig om te bevestigen welke
items precies mis-groeperd zijn. Vanavond alleen noteren wat er gezien wordt.

### Zukan — ontbrekende textures (algemeen)

De merged pack (build 47) is schoon volgens `audit-pack.py` (0 problemen, 0 orphans in
eigen namespaces). De "paarse/magenta" items die in Zukan verschijnen zijn dus niet
door ontbrekende definities in de pack, maar door:
- de 9 ontbrekende spawn eggs hierboven, en
- mogelijk items van plugins die een `item_model`-verwijzing gebruiken waarvan de
  definitie niet in de pack staat (audit vangt dit voor eigen namespaces; derde-partij
  namespaces als `mmoitems` hebben eigen gedrag).

**Vanavond te controleren:** welke items in Zukan paars/magenta tonen → noteer in de
tabellen hierboven.

---

## Notities / patronen

_(Hier grotere observaties: "alle paarse blokken zijn X", "alle mobs van plugin Y hebben geen animatie", etc.)_

---

## Voortgang

- Totaal gevonden: 9 (audit) + handmatige bevindingen vanavond
- Geregenereerd (model bestaat, wacht op deploy): 9
- Gerepareerd/live: 0
- Open: 9+ (handmatige bevindingen vanavond nog toe te voegen)

_Laatst bijgewerkt: (vul in tijdens de avond)_
