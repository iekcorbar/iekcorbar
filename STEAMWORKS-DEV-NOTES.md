# Steamworks — geheugen/voortgang (rondgang langs "veel dingen mis")

Laatst bijgewerkt: 2026-10-26 ~03:00 (ronde 2: Create-REA + Create-stijl wrench 0.3.14).
Gebruiker: "aan deze plugin zijn nog veel dingen mis...
lopen we straks 1 voor 1 na". Eerste ronde (klaar hieronder): wrench + roteren + creative-fix.

## Ronde 1 KLAAR — Steamworks 0.3.13 (jar in root, mvn groen, niet live-getest)
Klacht: gearshift/gearbox/shaft kunnen NIET in andere stand; creative: 1 klik = meerdere delen;
"as van de windmolen niet naar beneden".
Bevestigde oorzaak (code): KNode.axis en KNode.facing waren `final`, alleen bij het plaatsen
bepaald (PartListener.place switch per type) — er bestond géén rotatie-code. Gebruiker gelijk.

Wat er nu is:
- [x] KNode: axis/facing non-final.
- [x] Kinetics: `canRotate(n)` + `turn(n, cw)` — as-delen (shaft, cogs, gearbox, clutch,
      gearshift, sprocket, crushing wheel, gauges) X→Y→Z; steam engine X↔Z; richtings-delen
      (hand crank, fan, drill, saw, **windmill**) N→E→S→W→UP→DOWN (windmolen-as naar beneden =
      dit!); belt/station/signal horizontaal (belt-as loodrecht op facing).
- [x] SteamworksPlugin.rotatePart(n, cw): turn → visuals.spawn → kinetics.relink (netwerk
      herbouwt erdoorheen) → storage.save → koper-klikgeluid + action-bar-beschrijving.
- [x] **Steam Wrench**: MaterialType.WRENCH ("Steam Wrench", id "wrench"); recipe 2 iron ingots +
      2 sticks (PartItems); right-click op deel = draaien, sneak = andere kant; de wrench-tak
      staat in PartListener VÓÓR de millstone/chain/boiler/TANK-takken (want TANK checkt
      materialOf!=null als canister-proxy). Kan niet-draaien → showInfo. Giveall/list/give/tab/
      Zukan pakken 'm automatisch op (loops over MaterialType).
- [x] Model: **gegenereerd** door `Steamworks/tools/make_models.py` (wrench() toegevoegd + in
      build()); het scripts schrijft per model .bbmodel + .json — beide gekopieerd naar
      Steamworks/fmm-models/steamworks/ (sw_wrench, 9 cubes). Determinisme gecheckt: bestaande
      .json byte-op-byte gelijk. Eerdere handmatige kopie (uit steam_wrench.bbmodel) is
      hiermee OVERGESCHREVEN/vervangen.
- [x] Creative "1 klik, meerdere": off-hand event wordt nu ALTIJD cancelled bij een eigen
      paper-item (isOurs) + place()-dedup: zelfde target binnen 40 ms = gedupliceerd event.
      Kan statisch niet gereproduceerd; als het nog gebeurt: exacte deling + klikpatronen vragen.
- [x] **"Soms een barrier" in creative — twee echte oorzaken gevonden + gefixt:**
      1. Middelklik (pick-block) op een deel gaf het RAWE BARRIER-item (elk deel is een
         barrier-marker; het pack verbergt barrier nergens — de ItemDisplay-models bedekken
         hem normaal). Zoiets plaatsen = onzichtbaar blok ZONDER node. Fix: `onPick` in
         PartListener — `PlayerPickBlockEvent` (Paper, cancellable; géén setItem, dus zelf
         inv.setItem op `getTargetSlot()`) → cancelled + correcte part-item in de slot.
      2. `place()` zette BARRIER vóór het handmatig vuurde BlockPlaceEvent; cancel/exception
         van een andere listener kon een ghost-barrier achterlaten. Fix: volgorde om —
         event eerst, `setType(BARRIER)` pas ná goedkeuring (geen restore meer nodig).
      Let op bij future edits: de `ProbeBreakEvent`-guard bovenin `onBreak` MOEILIJK blijven
      (andere plugin breekt nodes via probes); die is per ongeluk eens weggehaald en teruggezet.
      Herbouwd in dezelfde 0.3.13-build (mvn groen, jar in root ververst).
- [x] PartType.lore SHAFT/GEARBOX noemen de wrench; docs/wiki-steamworks/02-power.md heeft een
      "Steam Wrench" sectie + shaft/gearbox/windmill-regels; seed: steamworks 50 pagina's.
- [x] CLAUDE.md dag-entry. Site-zip van 01:08 bevat dit al (docs/seed当时 erin; modellen zitten
      NIET in de site-zip).

## Create 6.0.10 — REA-referentie (2026-10-26, jar: Downloads\create-1.21.1-6.0.10.jar)
REA: sha256 ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a, 12.536 entries,
extract-ev_b575387f4b16379c7b6775fb373b695571fd3039847e54621b1b4f3061f16290. JEB-engine draait
niet (geen pseudocode), wel unreadable but complete bytecode via javap op de extract-root
(Temp\rea-extracted-d81e4e7b-... + dumps in Temp\opencode\create_rea\). Klassen onobfuscateerd.

Bevestigde Create-werkelijkheid (bytecode-bewijs):
- Rotatie = PUUR BlockState, geen entity/PDC. IWrenchable.getRotatedBlockState(state, GEKLIKTE_FACE)
  draait met de klok mee ROND DIE FACE-AS (Direction.getClockWise(axis)):
  shafts: zijklik→verticaal, eindklik→X<->Z (RotatedPillarKineticBlock.AXIS);
  cogs: HorizontalAxisKineticBlock.HORIZONTAL_AXIS (Y-klik wisselt X<->Z);
  fan/pers: HorizontalKineticBlock.HORIZONTAL_FACING; windmolen: DirectionalKineticBlock.FACING
  (klik langs eigen as = geen verandering, behalve AXIS_ALONG_FIRST_COORDINATE-toggle).
  SNEAK is hier NIET 'terugdraaien' — sneak+wrench = demonteren (onSneakWrenched: getDrops
  direct in inventory via placeItemBackInInventory + destroyBlock + WRENCH_REMOVE-sound;
  niet-Create blokken alleen via AllTags.WRENCH_PICKUP).
- onWrenched: eerst canSurvive-check op de ROTATED state (kan niet -> InteractionResult.PASS,
  niets gebeurt), dan switchToBlockState + updateAfterWrenched(=Block.updateFromNeighbourShapes,
  connecties herbouwen zelf), sound alleen als state echt veranderde.
- KineticBlockEntity.switchToBlockState (server-only): als neue state kinetisch niet equivalent
  -> net uit (network.remove + detachKinetics + removeSource) en GeneratingBron zet
  reActivateSource=true; dan setBlock flags 3 -> buren attachen opnieuw. = ons relink-patroon.
- Create 6.0 heeft ook een RadialWrenchMenu: keybind (AllKeys.ROTATE_MENU) met wrench in hand +
  block richt kruisdraad -> radiaal menu van GEREGISTERDE blockstate-properties
  (registerRotationProperty, VALID_PROPERTIES map, BLOCK_BLACKLIST), alle waarden per eigenschap,
  keuze via RadialWrenchMenuSubmitPacket naar server. Precisie-rotatie naast de klikcyclus.
- Gearshift: GearshiftBlock extends shaft + BooleanProperty POWERED = REDSTONE stuurt
  richting (FORWARD/REVERSE via SplitShaftBlockEntity). GEEN wrench 3-states cycle in Create.
  -> checklist-punt "gearshift 3 standen via wrench" hiermee besproken: Create doet het anders.
- Flywheel (dependency jar, 458 classes, com.jozufozu.flywheel): client-side render-engine
  (GPU-instancing voor contraptions). Voor een server-plugin niet bruikbaar; FMM/ItemDisplay is
  bij ons het equivalent. Create kent onze ghost-barrier-klasse niet: echte blokken + echte models.

Ideeeen die we mogen overwegen (NOT goedgekeurd, langs gebruiker lopen):
1. Wrench-rotatie gezicht-afhankelijk maken (getClickedFace al beschikbaar in onInteract) —
   voelt Creepy-natuurlijker dan sneak=omkeren.
2. sneak+wrench = deel netjes verwijderen (drop naar inventory) — herontdekkingsveilig.
3. Rotatie-geluid alleen als state echt wijzigt (Create checkt that).

## Ronde 2 KLAAR — Steamworks 0.3.14: Create-stijl wrench (na de Create-REA, idem keuzes)
Gebruiker koos (meervoudige keuze): face-afhankelijke rotatie (zoals Create), sneak+wrench =
demontage in inventaris, gearshift NIET op 3 standen (Create doet het via redstone; dat is dus
bewaard), canSurvive-achtige weigering = stil.

Wat er nu is (mvn groen, jar in root):
- [x] Kinetics: old `turn(n, cw)` VERVANGEN door `clockwise(BlockFace, Axis)` (Bukkit-tabel voor
      vanilla Direction.getClockWise) + `turnFace(n, clicked)` = draai kloksgewijs rond de as van
      het AANGEKLIKTE vlak (exact het IWrenchable.getRotatedBlockState-recept uit de REA).
      Zijklik op shaft = overeind; klik op boven/kantvlak = horizontaal 90° verder; klik op eigen
      eind = geen verandering. BELT/STATION/SIGNAL/STEAM_ENGINE weigeren verticaal (returns false).
- [x] SteamworksPlugin.rotatePart(n, BlockFace clicked): null = kan helemaal niet draaien
      (showInfo), "" = kan hier niet draaien (grijze action bar, GEEN geluid/geen save - de
      canSurvive-geest), String = beschrijving + koperklik + relink + save (was al zo).
- [x] PartListener wrench-tak: sneak -> `breakNode(n, p, false, true)` = Create-demontage;
      nieuwe breakNode-overload toInventory: part in inventory (overflow -> vallen), creative
      verliest hem zoals Create, ProbeBreakEvent-beveiliging blijft gelden.
- [x] MaterialType.WRENCH-lore + docs/wiki-steamworks/02-power.md + seed (50 pp) bijgewerkt.
- [!] Live nog NIET getest: draai-gevoel per type, demontage in survival (drop-deel->inventaris),
      "Can't turn that here." op verboden vlakken, en of de YAW-cyclus elders (KineticsTest?)
      nog iets van turn() verwachtte (mvn groen = nee).
- [x] Keuzes BEVESTIGD via multiple-choice: grijze-tekst-weigering blijven, demontage trouw aan
      Create (creative = niets terug), site-zip herbouwen UITGESTELD tot vlak voor deploy.
      Nu: gebruiker kiest het volgende 1-voor-1 checklistpunt met een eigen voorbeeld.

## 1-voor-1 punt 1 "kinetics-ratio's" — GECONTROLEERD GROEN (2026-10-26)
REA-bewijs (Create RotationPropagator.getRotationSpeedModifier, rotprop_c.txt): onze linkregels
zijn 1-op-1 hetzelfde: shaft recht 1×as-modifier; klein+klein naast -1; groot→klein diagonaal
-2/-0.5; groot+groot loodrecht ±1 via XOR van de offsetcomponenten (onze flip-formule = exact
dezelfde); **groot+groot zelfde as meshed bij Create OOK niet** (isLargeToLargeGear: axisA==axisB
-> false) - dus geen gat; sprocket 1:1; belt 1:1; crushing-wheels -1.
Bronnen in cfg: crank 16 RPM/cap8, waterrad 4/kant max 8 cap32, windmolen cap64 (test: 8 RPM),
stoommotor 24 RPM/cap64, max-rpm 192. Load = impact×|RPM|, capacity = cap×|RPM| per bron
(zelfde formules als Create, zie design doc L18-19).
Offline KineticsTest (Steamworks/tools/test) 64 checks: ALLEMAAL OK incl. gearbox-tekens,
groot-groot kwadranten, loops, overstress-jam, tegenkracht, overspeed-snap (256 RPM), clutch,
gearshift, fan, belt, sprocket, crusher.
Run-recipe (werktuig-kennis voor later):
  javac -cp Steamworks\target\classes;<paper-api>;<adventure-api/key 5.2.0>;<examination-api/string>;<option>;<annotations>;<snakeyaml 2.2>;<guava>;<gson> -d tmp Steamworks\tools\test\KineticsTest.java
  java -cp tmp;<zelfde cp> com.iekcorbar.steamworks.KineticsTest Steamworks\src\main\resources\config.yml
Open: gebruikers WAARNEMING in het spel (na deploy) - statisch is er niets te vinden.

## 1-voor-1 punt 2 "cogs meshen" — KLAAR (2026-10-26, in 0.3.14)
REA-bewijs: Create-REGELS kloppen al 1-op-1 bij ons (zie punt 1), maar Create prevents
botsing PLAATSING door CogWheelBlock.isValidCogwheelPosition: loodrecht-buur-cogs mogen niet
elkaar raken (groot-buurling altijd fout; self groot + buur klein fout), BEHALZES als de
buur-as de gewrichts-as is (wijst naar elkaar = kan). Wij hadden die check niet -> dodende
"geplaatst maar meshed niet". Toegevoegd:
- Kinetics.cogsFit(KNode) - exacte vertaling (alleen COGWHEEL/LARGE_COGWHEEL buren tellen).
- PartListener.place(): cog die botst -> weigering ("The cogs would collide there.", grijs;
  niets in de wereld, geen consume, event NIET gevuurd).
- SteamworksPlugin.rotatePart(): draaien dat zou botsen -> as/facing teruggezet -> "" ->
  "Can't turn that here." - dit is de canSurvive-check uit de Create-onWrenched-flow.
- KineticsTest: 4 nieuwe checks (past in lucht / klein-bij-groot botst / wijst-naar-gewricht
  past / stapel op zelfde as past) + placeholder-check L157 gerepareerd (was `|| true`,
  nu echt: eerste small in vermenigvuldig-trap = -32 RPM OK). 70 checks ALLEMAAL OK,
  mvn groen, Steamworks-0.3.14.jar in root herbouwd.

## 1-voor-1 punt 4 "machines" — AUDIT KLAAR, niks kapot (2026-10-26)
Millstones/Hammers/Mixers(kettle)/Crushers/Fans/Breakers(drill+zaag) statisch nagelezen:
- Alle vier de roterende machines: werk per 5-tick = clamp(speed/divisor, min, max) x5, voortgang
  = recipe.time() (mill/mixer) of cfg.cycle (hammer); speed<=0 of lege input -> progress reset.
  Formules = design-doc L52-57 + Settings-defaults; test 8 (hammer/crank capaciteit) groen.
- Hopper-kant: molensteen voedt vanaf hopper-BOVEN (Facing DOWN), kettle uit zijkant-hoppers
  (Facing naar kettle), resultaten duwen opzij/omonder (millstones opzij; kettle omlaag+opzij);
  hammer: hopper-ONDER via InventoryPickupItemEvent-holdback klopt met design (only while
  running && item pressable).
- Crushers: werken per paar (partner() zoekt buur-wiel zelfde as; -1 link draait ze tegen elkaar);
  oilWork-field hergebruikt als crush-timer, OK omdat een node niet beide types kan zijn;
  geen min/max-clamp - bewust lineair zoals Create's crusher-timer.
- Kettle: 4 water/bucket max 3, heat = fire/lit campfire/lava/magma (doc-consistent), mixer erboven
  = whisk drop 0.5 + BUBBLE/SPLASH + whirlpool-sound; Kitchen-bridge ingredient-matching (kitchen:id).
- MachineRecipes whole-section fallback zoals steeds: server-machines.yml met OUDE secties
  verbergt nieuwe ingebakken recipes -> bij deploy checken/vernieuwen (net als kitchen.yml).

Niks aangepast in code. Open: wat voelde de gebruiker concreet mis? (test na deploy).

## 1-voor-1 punten 7/10/11 — treinen / decor / info (2026-10-26, in 0.3.14)
- 7 treinen (Trains.java + Train.java): signaal-secties (sectionTaken tot volgende signaal,
  max trainSectionLength*2), rood-signaal Ahead-dash, station-wacht, autopilot, fuel (=stook,
  eigen maxSpeed - NIET kinetics-afhankelijk, design-keuze), tickets, remmen/achteruit. Niks gemist.
- 10 decor (Decor/DecorShapes via propkit) + EasterEggs (89 l.): geen TODO's; kettle-ei hangt
  in showInfo (eggs.kettleLook) - OK.
- 11 info-regel: OUDE KIEST — info toonde nooit de stand van een deel. Gemaakt:
  SteamworksPlugin.orientText(n) = gedeelde formulering (points/carries/faces/east-west...),
  rotatePart geeft die terug (was aparte switch) EN info() plakt "  |  stand" op
  not-connected/jammed/standing-still/RPM-regels via orient()-helper. mvn groen, jar ververst.

## Ronde 3 KLAAR — treinen x2 (gebruiker: "treinen zijn nog echt klein, moeten erin kunnen")
Bevinding: ER WAS AL een zit-systeem! CarType-banken (locomotief-cabine: W/S/remmen/fluit,
passagierswagon 4 zitplaatsen via onzichtbare ItemDisplay-passenger + Interaction-hitbox voor
klikken). Het probleem was puur afmeting: wagen ~1 blok.

Gemaakt (alles via ONE constante Train.SCALE = 2.0):
- Train: WHEELBASE 1.2->2.4, GAP 0.8->1.6 (koppel/move-logica gebruikt die constanten, schaaalt mee).
- Trains: MODEL_LIFT 0.75->1.5 (-12px * 2), model-schaal Vector3f(sc,sc,sc), viewRange 1.5->2.5,
  hitbox 1.1/1.5 -> x2, seatLoc vermenigvuldigt bank-offsets met SCALE, schoorsteenpositie (chimney) +
  fluit y + duwbox horizontaal x2; duwbox VERTICAAL bewust niet (kuikens op het spoor moeten
  voelbaar blijven; neus-dekking: p = 1.05*SCALE, box sluit aan op neus 0.94).
- Radii: near() koppelen-op-klik 1.6->2.4, blocked() overlap 1.6->3.0, "rijd geen trein in"
  2.0->(WHEELBASE+GAP)=4.0 formule.
- mvn groen; Steamworks-0.3.14.jar HERBOUWD (root) met trains x2 erin; KineticsTest 70/70.

Let op bij deploy: bestaande treinen in trains.yml hebben oude walposities (centers 2.0
uit elkaar) -> met x2-modellen staan ze KLEM (neuzen ~0.1 uit elkaar) - oogt OK, breekt niets,
maar oude treinen even slopen+opbouwen geeft de nieuwe 4.0-verdeling. Modellen/pack: GEEN
wijziging nodig (display-schaal doet het).

## 1-voor-1 punten 5/6/9/8 — fans/belts/arms/oil AUDIT GROEN (2026-10-26)
Alles statisch nagelopen (geen code-wijzigingen nodig):
- Fans: airstream stopt op solide blok/part; eerste lava/fire/water/soul-fire/campfire makes
  processing-stream (SMELT/SMOKE/WASH/HAUNT whole stack, fanProcessTicks=100); items worden pas
  bewerkt VAAR het catalyst-blok (at>catalystAt; washing ook OP het blok); push capped 2x;
  particles overgeslagen zonder speler binnen 32; cache correct gerescalled in load().
- Belts: rolle-richting = axis x up (gearshift/flipping bron keert om), items/mobs worden
  gedragen,
  player behalve sneak/flying/spectator/riding; einde-dozen: container of KETTLE, anders eraf;
  hamer +2 houdt items vast (centre met 0 vel).
- Arms: REPEAT/END counters consistent (key = index REPEAT-op), 64-step+wraps guard tegen
  freeze, PICK/DROP gripper+placer, PLACE/USE placer (breakers.mayBreak probe-checks), BREAK
  drill only + skippt containers/barriers/kinetic-parts, LEVER an; teaching LOWEST cancel, max 8
  punten, armRange; punch cards + books (compile-check met doc-link); info toont rule+gehouden item.
- Oil: veld per chunk uit seed (desert/badlands/ocean x2.5, alleen NORMAL); oilLeft = PDC of
  computed; pumpjack: rate x RPM mB/s, veld-oil alleen afgetrokken als echt gestopt (got>0),
  geen opbouw als tanks vol; refinery: hitte-check (zelfde bronnen als kettle), FUEL-ruimte
  voorgecheckt (stopt netjes), waste zonder ruimte -> pollute(chunk)+DUST+uitdof-geluid;
  pollution: PDC+load/unload-sync, decay/100t, wither grass/leaves >300, poison/nausea >600,
  cap 20000; canister = 1000 mB vullen/maken (creative behoudt); tank slopen met waste = spill;
  info: mB left, mB/s, refinery-warnings; dowsing rod: cooldown 1s, actionbar hier/nearest
  richting+chunk-afstand (alleen als hier geen oil), pollution-bericht.
Conclusie: niets aangepast. Lijst is statisch af; alles wacht op LIVE-test na deploy.

## Ronde 4 KLAAR — Steamworks 0.3.15: stoom-spoor op treinroutes (gebruiker: "rails model met shakels")
Keuzes via MC: nieuwe rail-MODELS niet als pack-override (dat zou ALLE rails wijzigen) maar
route-gebonden via displays; gebruiker akkoord met die aanpak ("alleen treinroutes").

- make_models.py: 3 nieuwe gegenereerde modellen (deterministisch, rest byte-identitaal gebleven):
  sw_track_straight (12 cubes), sw_track_slope (12, 16√2 px lang -> display kantelt ±45°),
  sw_track_curve (18, boog om noordoost-hoek; benen noord+oost, boogsegmenten geroteerd rond eigen
  middelpunt). Bed 0..2,0 px (bedekt vanilla-rail <2px), dwarsliggers 2,0..2,6, rails 2,6..3,4,
  brass fishplates/shakels 2,3..3,7 op deuiteinden.
  LET OP: mijn replace-chain had rail-lijnen op nul-hoogte gezet; gefixt (top 2.6->3.4, 3x).
- Trains.java: MODEL_LIFT 1,5 -> 1,71 (wiel-draagvlak = railkop 3,4px). Nieuw sectie
  "track visuals": refreshTrack() jede 20t (samen met indexRails) loopt per trein de rail-lijn
  beide kanten op (RailWalker.move in stappen van 0,4, cap 256 per wandeling), verzamelt keys
  "worldUid:x,y,z", wens-set vs track-map: extra's despawnen, ontbrekende/invalid spawns
  spawnTrack() (niet-persistent ItemDisplay + visuals-tag, NONE-transform, Rail.Shape -> model +
  quaternion trackRot(): yaw 0/90/180/-90, hellingen via rotateX(±45) oost/west = yaw+tilt,
  display op blokmidden bij helling, anders op blokonderkant).
- Trein weg/uit unloaded -> spoor verdwijnt/tijdelijk; spoor volgt de route = "alleen treinroutes".
- mvn groen, Steamworks-0.3.15.jar in root. DEPLOY: jar + NIEUWE FMM-BESTANDEN: sw_track_straight/
  slope/curve (.json + .bbmodel) naar plugins/FreeMinecraftModels/models/steamworks/ + FMM-reload
  (anders tonen de display-ents kaal paper i.p.v. spoor).

## Lijst voor de 1-voor-1 nabespreking (gebruiker geeft voorbeelden, punt voor punt)
- [ ] ROGEREN live: elk type een keer draaien + netwerk-herbouw (vooral windmolen-as-omlaag,
      gearbox-hoek, belt-richting mét items erop, sprocket-kettingen na draaien)
- [ ] creative dubbelplaatsen nog gezien na deze build?
- [ ] barrier-fix live: middelklik op deel = part-item (geen barrier); geen ghost-barriers meer;
      bestaangende ghost-barriers (barrier zonder node) kun je gewoon wegslaan of //set air
- [ ] sw_wrench.bbmodel + sw_wrench.json → server: plugins/FreeMinecraftModels/models/steamworks/
      + FMM-reload/herstart (anders "Unknown model sw_wrench" + kaal paper-item)
- [x] gearshift: BESETLEN GEBRUIKER (2026-10-26, na Create-REA): nee - trouw aan Create.
       GearshiftBlock in Create 6 = shaft + POWERED boolean (redstone stuurt richting); geen
       wrench-3-standen. Onze as-draaien + redstone-omkering is dus exactly the original.
- [x] rest die "mis" voelt: AFGELOPEN (2026-10-26 nachtrondes): ratio's+cogs = Create-bytecode
       1-op-1 (+ botsingsregel nagebouwd), machines/fans/belts/arms/oil audited groen, treinen x2,
       info-regel kreeg oriëntatie. Over = ALLEEN nog live-test na deploy.
- [x] /steamworks info-regel: oriëntatie toegevoegd (orientText). Wiki: 02-power.md (wrench-
       tekst) + steamworks-seed 50 pp herbouwd; trein-pagina maakt geen grootte-claims = OK.

## Build/deploy-fouten-protocol
mvn -q -o package (in Steamworks/) → jar naar repo-root (huidige: Steamworks-0.3.15.jar).
Nooit DB/ping naar production. Live-check alleen in game.
