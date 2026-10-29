# Cinematic Interaction — REA-onderzoek (29-10-2026)

**Doel:** de Fabric-mod nabouwen als server-plugin voor Paper 26.2 én 26.3.
**Target:** `archief/cinematic-interaction/Cinematic Interaction-Fabric-26.3-0.18.30.jar`
(1.329.871 bytes, sha256 `bb74ca7d2b70f26df192fff98ccb306e025b616410b01e7e3d98f8242aec1d70`;
REA-inspection `ai_94a65ff90f7300e4b817…`, formaat zip, sha256 gecheckt op identity).
**Bron-code is niet beschikbaar** — alle bevindingen hieronder zijn *observed* uit de
shipped artifact (klassenamen, constant pool, mixins.json, fabric.mod.json).

## Wat de mod doet (aardbewijs)
"Eerste-persoons cinematische interactie met werkbanken, ovens e.a." — inplaats van
het GUI-scherm te openen verplaatst de **client** de camera naar het blok, tekent de
slots in de wereld, en speelt animaties; de **server** blijft de waarheid houden:
container, crafting-logica en per-station watchers die de echte blokkijstanden
versyncelen. Het echte scherm wordt alleen onderdrukt.

## Architectuur (uit 322 klassen + 33 mixins)
- **Server (~60 klassen):**
  * `station/Sessions$Session` — één actieve cinematische sessie per speler.
  * `station/StationKind` — 19 soorten: TABLE, FURNACE, SMOKER, BLAST_FURNACE,
    ENCHANTING, ANVIL, GRINDSTONE, SMITHING, STONECUTTER, LOOM, CARTOGRAPHY,
    BREWING, CRAFTER, CAMPFIRE, LECTERN, BEACON, JUKEBOX, CHISELED_BOOKSHELF, ADDON.
  * per-familie logica: `table/` (TableActions, TableCrafting$Match, TableGrid,
    TableStore), `furnace/` (FurnaceActions$Outcome, FurnaceLayout$Bed/Glow/Zone,
    FurnaceWatch$Fingerprinted, FurnaceZones$Face), `brewing/BrewingWatch`,
    `campfire/CampfireActions$CookingWatch`, `enchant/EnchantActions`,
    `station/JukeboxActions`, `station/ShelfActions`.
  * `api/AddonStations$Definition` — extensiepunt: andere mods registeren hun
    eigen station via `id;blockId;menuClass;sides` (= ons CustomPart-idee).
- **Protocol (17 payloads, `net/`):** id's met protocolversie-achtervoegsel
  (`station_action_14`, `protocol_14` — 14 = de MC-netwerkprotocolversie van 26.3).
  * C2S: `StationActionPayload{pos;kind;action;index;button;shift;side}` met acties
    **BEGIN, CLICK, PAINT, CRAFT, LEAVE, WINDOW, PLACE, SPREAD, QUICK, CHOOSE**;
    `RecipeChoicePayload`, `SessionClosePayload`.
  * S2C: contents/state per station (`TableContentsPayload`, `FurnaceContentsPayload`,
    `FurnaceStatePayload`, `BrewingContentsPayload`, `CampfireProgressPayload`,
    `EnchantDisplayPayload`, `MenuDisplayPayload`) + effecten
    (`MenuFxPayload$Cue`, `EnchantFxPayload`) + `AtStationPayload` + `RecipeSyncPayload`.
- **Server-mixins (7):** AbstractFurnaceBlockEntity, BrewingStandBlockEntity,
  CraftingMenuAccess, EnchantmentMenu, PlayerChunkSender, ServerLevel, ServerPlayer —
  allemaal klein: haken voor session-lifecycle en read-access.
- **Client-mixins (26):** Camera, FirstPersonHands, MouseHandler, KeyboardInput, Gui,
  Screen, LevelRenderer, PlayerRenderer, ... — de eigenlijke cinema zit hier (217 klassen).

## Feasibility als Paper-plugin (onze conclusie, geen observed feit)
- **1-op-1 kan 100% server-side:** het hele session-model, de action-grammatica, de
  echte crafting/smelting/brewing-logica, de watchers (onze KNode-tick doet dit al),
  en het addon-idee (onze `CustomPartRegistry` is precies `AddonStations`).
- **Kán niet zonder client-mod:** camera verplaatsen, handen animeren, slots ín het
  blok tekenen. Vanille-clients begrijpen deze payloads niet.
- **Eerlijke server-equivalent** (de AfkCinema-leer uit spatial-gui): klik een
  werkbank → géén scherm, maar een zwevend FMM/ItemDisplay-paneel met slots vlak
  boven het blok + Interaction-entityklikken (CLICK/PLACE/CRAFT/QUICK), swing-
  animaties via server-packets, en result-fx via displays. "Cinema voor werkbanken",
  draait op élke vanilla client, beide MC-versies.
- 26.2/26.3: zelfde API-abstractie als onze andere plugins (paper-api, testbank).

## Open vragen voor de bouw-sessie
1. Welke stations als eerste? (TABLE + FURNACE zijn de kern; LEVER later.)
2. Paneel-look: ItemDisplay-grid of TextDisplay-plates? (Zie AfkCinema `/cine screen`.)
3. Anti-cheat: click-zones valideren tegen `pos;kind` uit de sessie (mod doet dit zelf ook).
4. Performance: watchers only while session active (mod: `Fingerprinted` = delta-detectie).

## Next move
Werkbank-prototype in een nieuwe plugin of als Steamworks-onderdeel, met dit
document als spiekbrief; hierna pas de tweede REA-passage (Table/FurnaceActions
constant pool → exacte validatieregels) als de eerste bouw staat.
