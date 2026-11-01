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
