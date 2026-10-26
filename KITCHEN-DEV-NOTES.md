# Kitchen + Garden crops — HERBOUW — KLAAR (2026-10-26 ~00:50)

Alles hieronder is GEBOUWWD en geaudit; mvn groen, jars in root, packs + merge + seeds + CLAUDE.md
+ wiki-docs bijgewerkt, site-zip herbouwd. NIET live-getest (server nog niet herstart).

Status per onderdeel:
- [x] Kitchen Crops.java herschreven (struct void + chunk-PDC-entries + ItemDisplay + tripwire-migratie)
- [x] Kitchen CropType/KItem +4 gewassen (corn/tomato/chili/sesame + cornmeal), Achievements +5 taken,
      config.yml + growth-minutes, kitchen.yml corn→cornmeal, make_pack palette/items/sprite/CROPS,
      tripwire-sectie + tripwire.json WEG, rmtree-guard in main()
- [x] Kitchen take-command + plugin.yml usage + help
- [x] KitchenPlugin: docstring, crops.removeAllDisplays() in onDisable
- [x] Sakagura Garden.java + Fruit.java herschreven (zelfde architectuur; 16 soorten incl.
      apple/pear/apricot/mikan/kiwi + pip/pit/kernel/zaden; Goods FOOD +5 eetbaar)
- [x] Sakagura.java: take (drink via recipes().match, cask via caskWood, fruit via idOf), tab, help,
      onDisable garden.saveAll()+removeAllDisplays(), docstring; plugin.yml usage
- [x] GardenDecor: fountain .scale(3f) + particles ×3
- [x] make_pack Sakagura: FRUITS/SEEDS/ITEMS/trunk + tripwire-extra WEG
- [x] poms: Kitchen 0.3.0, Sakagura 0.3.0; jars in repo-root (00:40/00:43)
- [x] packs herbouwd + merge-packs: Iekcorbar-pack.zip 2279 files sha1 f5ab0fb5;
      AUDIT (output_browser/audit_crops.py): geen tripwire-resten, alle stage/item-modellen aanwezig
- [x] docs wiki-kitchen 00/02 + wiki-sakagura 00/02 bijgewerkt; seeds: kitchen 38, sakagura 20 pagina's
- [x] CLAUDE.md: Kitchen- en Sakagura-sectie nieuwe architectuur + dagentry
- [ ] IN GAME: zie "Test-aandacht" hieronder + deploy-notitie server-kitchen.yml.

## DEPLOYEN (gebruiker doet dit zelf, bij herstart)
1. Kitchen-0.3.0.jar + Sakagura-0.3.0.jar → plugins/ (vervang 0.2.7/0.2.8).
2. Nieuwe Kitchen-pack.zip + Sakagura-pack.zip in de RPM-mixer (of Iekcorbar-pack.zip vervangen) —
   de oude tripwire.json-override valt weg; bestaande velden migreren automatisch.
3. Website: verse site-zip + npm install/build + staff importeert keukentuin/sakagura-pagina's opnieuw.
4. Server-Kitchen/kitchen.yml (indien aanwezig): maïsknip-regel toevoegen OF bestand verwijderen
   (section-fallback is HELE sectie, dus nieuwe entries verschijnen niet vanzelf).

Test-aandacht in game (26 okt): oogsten verandert nooit meer van gewas; oude tripwire-velden zijn na
herstart mollen-/stoomstille-achtige displays; afgeknipte tripwire-val is geen crop meer;
corn/tomato/chili/sesame + apple/pear/apricot/mikan/kiwi geplant, gegroeid, geoogst; /kitchen take en
/brew take werken; fontein 3x —-display zweeft/zakt niet (anders DISPLAY_Y in Crops/Garden bijstellen).

## Origineel plan (ongewijzigd archief)

Laatst bijgewerkt: 2026-10-26. Doel: **tripwire-disguise entirely weg** + veel meer gewassen +
give/take commando's + fountain 3x. Versions: Kitchen 0.2.7→**0.3.0**, Sakagura 0.2.8→**0.3.0**.

## Waarom (de echte bug, aan de gebruiker uitgelegd)
- Crop-identiteit zat in tripwire-BITS (disarmed + attached/powered/faces). Vanilla "repairde"
  die bits (regelmechaniek via scheduledTick/neighbor paden langs onze physics-cancel) →
  **bij oogsten/groeien veranderde de crop**. Plus: 64 states TOTAAL voor beide plugins samen
  (Kitchen 0-29, Garden 30-62) → geen ruimte voor nieuwe gewassen.
- User's instinkt klopte: "ander blok dan tripwire".

## Nieuwe architectuur (beide plugins)
- Crop-block = **STRUCTURE_VOID**: onzichtbaar, NOOIT door spelers te plaatsen, GEEN states die
  vanilla kunnen wijzigen. De marker bestaat alleen om: (a) de plek te blokkeren, (b) klikbaar-
  niet te zijn: rechtsklikken gebeurt op de GROND eronder (farmland of rich-soil barrier), daar
  lookup = entry op block.UP.
- Identiteit = chunk PDC "crops" INTEGER_ARRAY, nieuw formaat: pos bits 0-16 (oud) |
  type ordinal bits 17-24 | stage bits 25-27 | wild bit 28.
- Look = **ItemDisplay** per crop (non-persistent, PAPER + ITEM_MODEL
  "kitchen:block/<id>_stage<N>" resp. "sakagura:block/<id>_stage<N>" of _wild) — exact hetzelfde
  decor-draw patroon (Decor.java draw()). Spawn at block center (x+.5, y+.5, z+.5), NONE, scale 1.
  CONSTANTS BOVENAAN Crops/Garden (DISPLAY_Y, DISPLAY_SCALE) als in-game bijstelling nodig is.
- **Migratie** in loadChunk: entry + block==TRIPWIRE → decodeer OUD (Kitchen: i/5, i%5, s4=wild;
  Garden: (i-30)/3 etc., legacy bits: N1 E2 S4 W8 A16 P32) → struct_void + nieuwe entry + display.
  Block==STRUCTURE_VOID + entry → ok (spawn display). Anders → entry weg (vuil).
  → oude tripwire velden worden automatisch omgezet; kapot-getrapte velden = stale entries die
    gewoon opgeruimd worden.
- tripwire.json blockstates-bestand verdwijnt uit Kitchen-pack (en tripwire-extra.json uit het
  Sakagura-resourcepack). Oude speuler-valen: afgekniupe tripwire-draden = geen crops meer.

## Nieuwe gewassen
Kitchen CropType: +CORN (KItem CORN seed=produce, 2-4), +TOMATO (TOMATO, 1-3), +CHILI (CHILI, 1-3),
+SESAME (SESAME_SEEDS, 2-3). Nieuwe KItems: CORN (SEED, eetbaar 1/0.8), CORNMEAL (INGREDIENT),
TOMATO (SEED, 2/1.2), CHILI (SEED, 1/0.6), SESAME_SEEDS (SEED). cutting in kitchen.yml:
corn→cornmeal x2. config.yml growth-minutes: corn 22, tomato 18, chili 20, sesame 24.
make_pack.py: CROPS lijst +4, palette +corn/corn_dark/tomato/chili/sesame/sesame_dark,
items() +modellen, sprite() +takken. Achievements: +corn/tomato/chili/sesame taak + item-mappings
(six_crops blijft zoals ie is — historie).
Sakagura Fruit: +APPLE(apple/apple_pip 2-4), +PEAR(pear/pear_pip 2-3), +APRICOT(apricot/
apricot_kernel 1-3), +MIKAN(mikan/mikan_seeds 2-4), +KIWI(kiwi/kiwi_seeds 2-3) — allen regrow.
Goods FOOD map erbij; make_pack: BASE-kleuren + FRUITS + SEEDS + ITEMS; Achievements/orchard
automatisch (loopt Fruit.values()). Garden wild: nieuwe biomes ok.

## Commando's
- `/kitchen give <player> <item> [aantal]` bestond al → **`/kitchen take`** eraf-variant (scan
  inventory typeOf==KItem, verminder). Tab + usage + plugin.yml bijgewerkt.
- `/brew give` bestond (drink/cask/fruit) → **`/brew take <player> <item|drink|cask_wood>
  [aantal]`**: goeds-items via idOf, drank via brewOf recipe, cask via caskWood. Admin node
  sakagura.admin / kitchen.admin.

## Fountain
- GardenDecor "fountain": `.scale(3f)` (Decor.transform gebruikt def.scale al, maaft basis op
  grond) + lore-tekst + particle-offsets ×3 (y 1.8→5.2, splash radius 0.4→1.2, FALLINGWater telt 8→20).

## Planning checks (mvn + packs + audit)
1. Kitchen mvn → Kitchen-0.3.0.jar in root.  2. Sakagura mvn → Sakagura-0.3.0.jar in root.
3. make_pack.py beide → Kitchen-pack.zip/Sakagura-pack.zip → merge-packs → Iekcorbar-pack.zip
   (GEEN tripwire.json meer; wél block-modellen per stage).  4. python-audit: alle display
   model-keys bestaan in zip; legacy bits round-trip test (data→index).  5. docs/wiki-kitchen +
   wiki-sakagura bijwerken + build-wiki-seed.py beide.  6. CLAUDE.md sectie + dagentry.
7. DEPLOY-NOTITIE (gebruiker doet zelf): beide jars + packs + server-herstart; bestaande
   tripwire-velden migreren automatisch; server kitchen.yml = oud bestand → NEW cutting entry
   ontbreekt tot gebruiker het bestand ververst (fallback: section-basis; cutting section
   bestaat → nieuwe items NIET automatisch! kitchen.yml op server bijwerken OF delete).
   Sakagura recipes.yml (server) krijgt nieuwe fruit-drinks niet automatisch — niet deze ronde.

## Test-aandacht in game
- Bestaande velden: na herstart zien crops er identiek uit, groeien normaal, oogsten verandert
  NOOIT meer van soort. Afgeknipte tripwire-val = geen crop meer (goed!).
- Nieuw: 4 Kitchen-gewassen + 5 tuin Fruit (eieren? nee — zaden via /kitchen take of give).
- `/kitchen take <me> rice 5` / `/brew take <me> apple` werken.
- Fountain 3x: staat hij goed op de grond? particles op hoogte? (DISPLAY constants + transform
  up=(ts-1)*0.5 regelt dat).
