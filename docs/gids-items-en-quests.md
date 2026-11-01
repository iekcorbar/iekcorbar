# Gids: custom items en quests maken

Stand 1-11-2026. Gebaseerd op de repo (IekQuests 0.5.x, `docs/plugin-strategie.md`, `docs/quest-wizard.md`).

## Eerst de waarheid

- **Quests** maak je in **IekQuests**. Daar is alles voor: gesprekken, doelen, beloningen, NPC's, wizard.
- **Custom items maak je NIET in IekQuests.** IekQuests kan een item alleen *geven* (`give MATERIAAL N`
  = gewoon vanilla) of *vragen* (levering met materiaal + naamfilter). Het maakt geen items.
- **MMOItems is er volgens de repo nog wel.** `docs/plugin-strategie.md`: MMOItems wordt pas vervangen
  door IekShops (nog niet gebouwd), en MythicLib blijft tot die tijd. Controleer op de server met
  `/plugins` of `mi list` (staat hier niet in de repo, alleen in de notities).
- Items met een eigen plugin (Steamworks, Relics, Kitchen, Sakagura, Kura, Satchel, Wildlife) komen uit die plugin.

## Welke route voor welk item?

| Wat | Route |
|---|---|
| Wapen, armor, ring, vis, materiaal, consumable (RPG-item) | **MMOItems** (stap A) |
| Boek met verhaal | **IekcorbarSiteSync books** (`/ibook`), stap B |
| Beloning-item voor elite/dungeon | **EliteMobs** item-yml, in quest met `eloot` |
| Machine, onderdeel, meubel, relic, gewas | de eigen plugin (zie wiki per plugin) |
| Gewoon een vanilla item als beloning | `give DIAMOND 5` in de quest |

## A. Een MMOItems-item maken (stap voor stap)

1. Bepaal het **type** (sword, axe, ring, material, consumable...). Elk type heeft een bestand
   in `plugins/MMOItems/item/<type>.yml`. Lokale kopie: `pluginset-aangepast/MMOItems/item/`.
2. Optie 1 - in het spel: `/mi create <TYPE> <ID>` (ID in HOOFDLETTERS), daarna `/mi edit <TYPE> <ID>`
   opent de GUI om naam, damage, lore enz. te zetten.
3. Optie 2 - in de yml (sneller als je meerdere maakt). Voorbeeld in `material.yml`:
   ```yaml
   MIJN_ITEM:
     base:
       model: mmoitems:material/mijn_item     # alleen als je een eigen model hebt
       material: IRON_INGOT
       name: '&fMijn Item'
       lore:
         - '&7Beschrijving.'
       disable-crafting: true
   ```
4. Opslaan en `/mi reload`.
5. Testen: `/mi browse` of `/mi give <speler> <TYPE> <ID>`.
6. **Eigen model/texture** (optioneel): zie `IekcorbarSiteSync/tools/make_mmo_models.py` (maakt een
   pixel-art model) en de pack-regels (skill `pack-build`: BUILD ophogen, herbouwen, auditen).
   Zonder `model:` regel ziet het item er vanilla uit - prima voor een eerste test.
7. Het yml-bestand naar de server zetten (`plugins/MMOItems/item/`), `/mi reload`.

## B. Een boek maken

1. Nieuw bestand `plugins/IekcorbarSiteSync/books/mijnboek.txt`: header met `key: value` regels,
   een regel `===`, dan pagina's gescheiden door `---`. Voorbeeld: `engineers_manual.txt`.
2. `/ibook reload`, daarna `/ibook give <speler> mijnboek`.

## C. Een quest maken - de makkelijke manier (wizard)

Volledige uitleg: `docs/quest-wizard.md`. Kort:

1. Zorg dat je `iekquests.admin` hebt. Typ `/questwizard`.
2. **Quest id** (klein, bv. `smid_ijzer`), **Title**, **Subtitle**, **Icon** (materiaalnaam).
3. **NPC binding**: bestaande Citizens-id typen, of een nieuwe NPC laten spawnen op jouw plek.
4. **Stages** toevoegen met de groene +: `talk` (praten), `craft`/`deliver` (items inleveren),
   `walk` (ergens heen), `kill` (mobs doden).
5. **Quest rewards**: bv.
   ```
   money 300
   eco 10
   mi give %player% MATERIAL MIJN_ITEM 1
   say &aBedankt!
   ```
   Alles wat geen vast sleutelwoord is, draait als **console-commando** (`%player%` wordt vervangen).
   Zo geef je een MMOItems-item: `mi give <type> <id> <speler> <aantal>` - kijk even de exacte syntax na
   met `/mi give` in de console, die verschilt per versie.
6. Ender-oog: opslaan en activeren. Test als gewone speler (niet-admin), want sommige acties
   (teleport) gedragen zich anders voor staff.

De wizard schrijft naar `plugins/IekQuests/wizard-quests.yml` en raakt `quests.yml` nooit aan.

## D. Een quest met de hand in quests.yml

Handig voor grote quests. Het kleinste werkende voorbeeld:

```yaml
quests:
  smid_ijzer:
    title: "&6Ijzer voor de smid"
    subtitle: "Smid Jan"
    icon: IRON_INGOT
    npc-id: 150                  # Citizens-id
    order: 100
    journal:
      - "Breng de smid 8 ijzerstaven."
    stages:
      praat:
        type: talk
        label: "&aIk help je"
        lines:
          - "&fJan: &7Ik heb ijzer nodig!"
      lever:
        type: deliver
        material: IRON_INGOT
        amount: 8
        label: "&aLever 8 ijzer in"
        lines:
          - "&fJan: &7Mooi werk."
    rewards:
      - "money 250"
      - "eco 5"
      - "mi give MATERIAL MIJN_ITEM %player% 1"
      - "tag+ smid_ijzer_done"
```

Handige extra's:
- **Een custom item inleveren** (zoals je MMOItems-item): `type: deliver`, `material:` = het basis-
  materiaal (bv. `IRON_INGOT`) en `name:` = een stuk van de weergavenaam (`name: "Mijn Item"`).
  Zonder `name:` telt ook een gewone ijzerstaaf mee.
- **Herhaalbaar**: blok `repeat:` met `material`, `amount`, `cooldown` (seconden), `money`, `eco`.
- **Alleen eenmalig**: `start: "!tag:smid_ijzer_done"` en geef de tag in `rewards`.
- **Elite-item geven**: `eloot mijn_item` (bestand uit EliteMobs `items/`).
- Opnieuw laden: `/questbook reload`. Check de console op fouten.

## E. Testlijst voor elke nieuwe quest

1. `/questbook reload`: geen fout in de console.
2. Als speler zonder staff: NPC aanklikken, alle stages doorlopen.
3. Beloning ontvangen? Item echt in het inventory, geld erbij?
4. Herhaling/cooldown werkt, en `start`-voorwaarde verbergt de quest na afloop.

## F. IekItems (nieuw, IekQuests 0.7.0): eigen items in IekQuests zelf

Geen MMOItems meer nodig voor gewone quest-items. Alles staat in `plugins/IekQuests/items.yml`.

**Stap voor stap, in het spel**
1. Pak een item, geef het een naam/lore/enchants (anvil, of een bestaand item).
2. Houd het in je hand: `/iekitem create smid_staf` (id = a-z, 0-9, _). Het staat nu in items.yml.
3. Extra's (attributes, flags, rechtsklik-acties, eigen model) zet je in items.yml, daarna `/iekitem reload`.
4. Testen: `/iekitem get smid_staf`, of `/iekitem give <speler> smid_staf 3`.
5. In een quest:
   - beloning: `item smid_staf 1`
   - inleveren: stage/contract met `material: item:smid_staf` (telt alleen dit item, geen look-alikes)
   - ook in de wizard: typ `item:smid_staf` als material.

**Velden in items.yml** (staan met uitleg bovenaan het bestand): material, name, lore, enchants, glow,
unbreakable, item-model (uit de resourcepack), attributes, flags, use (acties bij rechtsklik, zelfde syntax
als quest-rewards, bv. `effect REGENERATION 10 0`), consume, use-cooldown, use-permission.

**Commando's** (permissie `iekquests.items`, of `iekquests.admin`): `/iekitem` (alias `/iitem`, `/ii`):
list, get, give, create, delete, info, reload. `/questbook reload` laadt items.yml ook mee.

Let op: `tag+` en `point` in een `use:`-lijst worden niet bewaard (alleen quests hebben voortgang).
De bestaande MMOItems blijven werken; IekItems is er naast.

## G. De item-editor (GUI, IekQuests 0.7.1)

`/iekitem` zonder argumenten opent de **IekItems-browser** (`/iekitem edit <id>` springt direct naar een item).

- **Browser**: alle items; klik = bewerken, shift-klik = kopie krijgen. Knoppen: zoeken, **+ New item**
  (id typen in chat), **From my hand** (item in je hand wordt een IekItem).
- **Editor** (per item): Material, Name, Lore, Enchants, Glow, Unbreakable, Consume, Attributes, Flags,
  rechtsklik-acties, cooldown, permissie, kopie halen, verwijderen (shift-klik).
  Tekst typ je in de chat; lijsten sluit je af met `done`, `clear` maakt leeg, `cancel` stopt.
- **Model toewijzen** (de verfbusknop "Model"): toont alle item-modellen uit de resourcepacks in
  `plugins/ResourcePackManager/mixer/` (en `plugins/IekQuests/packs/`; aanpasbaar met `pack-dirs:` in
  config.yml), elk met een voorbeeld van je eigen item erin. Klik = toewijzen. Je kunt ook zoeken of een
  model-id typen (bv. `mmoitems:sword/long_sword`), en "Remove model" zet het vanilla-uiterlijk terug.
  Eerste keer scant hij de packs (paar seconden); "Rescan packs" na een nieuw pack.
  Je ziet het model alleen als je client het pack geladen heeft.

### In de quest-wizard
Overal paarse **amethist-scherf**-knoppen: die openen dezelfde browser als kiezer.
- Hoofdscherm: **+ Item reward (quest)**; stage: **Custom item** (als material voor craft/deliver) en
  **+ Item reward (stage)**; keuze: **+ Item (do-action)**; contract: **Custom item (material)** en
  **+ Item reward**.
- In de kiezer kun je ook **+ New item** doen en het meteen volledig bewerken (incl. model) en daarna
  **Select this item** klikken. Bij beloningen vraagt hij daarna het aantal.
- Wat de wizard opslaat: `item <id> <aantal>` als beloning en `material: ITEM:<ID>` bij inleveren.

## H. Gesprekmodus, klikbare chat en travel (IekQuests 0.8.0)

**Twee manieren om een quest te spelen**
- **GUI-modus**: het venster met knoppen (zoals tot nu toe).
- **Gesprekmodus**: de NPC praat in de chat en elke optie is een **aanklikbare knop** in de chat
  (`[ ✔ Start quest ]`, `[ ▸ keuze ]`, `[ Hand in ]`). Niets typen; klikken kan ook als je de chat open hebt.
- Wisselen: `/questbook mode` (of `/questbook mode gui|chat`), de knop **Conversation mode** (boek, bovenin het
  quest-venster) of **[GUI mode]** onderaan het chatpaneel. De keuze wordt per speler onthouden.
  Standaard voor nieuwe spelers: `default-mode` in config.yml.
- Een quest kan een modus afdwingen: `mode: gui` of `mode: chat` (default `player` = speler kiest). In de
  wizard: knop **Conversation mode** (hoofdscherm) wisselt player / gui / chat.
- Oude knoppen in de chat werken niet meer als de stage veranderd is ("That option is no longer available").

**Teleporteren voor spelers: de `travel` actie**
- `travel <bestemming> [cost=N] [warmup=S]`, als beloning of in de do-lijst van een keuze (met `close: true`
  voor een reismeester-NPC). Bestemming = naam uit `travel.destinations` in config.yml, of `x;y;z;wereld`.
- De speler moet `warmup` seconden stilstaan (standaard 3), anders breekt het af. Kosten worden pas bij
  vertrek afgeschreven (`cmi money take`) en staan in transactions.log. Optionele cooldown per speler.
- `teleport` blijft zoals het was: alleen voor staff (spelers krijgen een kompas). `travel` is de bewuste
  uitzondering. Uitzetten kan met `travel.enabled: false`.

**Nieuwe quest-opties**
- Condities: `quest:<id>` (die quest is klaar), `world:<naam>`, `level>=N`, `gamemode:<modus>`.
  Met `!` ervoor omdraaien, komma = en.
- Acties: `xp N`, `heal`, `feed`, `broadcast tekst`, `cmdplayer <commando>` (speler voert het zelf uit),
  `takeitem <iekitem|MATERIAAL> N`, `travel ...`.

**Nieuwe item-opties** (ook in de editor): `soulbound` (blijft bij dood), `undroppable`, `color` (hex, leren
armor/drankjes), `texture` (base64, hoofden), `custom-model-data`, `max-stack`, `use-sound`, `use-message`.
