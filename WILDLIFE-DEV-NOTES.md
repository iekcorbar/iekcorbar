# Wildlife — geheugen/voortgang (dieren uitbreiden + animaties opknappen)

Laatst bijgewerkt: 2026-10-25 ~23:15. **BATCH 1 (0.4.0) + BATCH 2 (0.5.0) KLAAR** — jar's in
repo-root, mvn groen, 133 modellen, 0 bot-audit mismatches. NIET live-getest.

## Status
- **Wildlife-0.5.0.jar** in de root (0.4.0 ook nog). 133 bbmodels in `Wildlife/fmm-models/wildlife/`
  (gait-rework zit in ALLE modellen!) + Zoo-pack 185 modellen + `Iekcorbar-pack.zip` (sha1 218168a3).
- Batch 1 (0.4.0): gait()/hop_walk()/stilt_walk() + tanuki, kraan, koi (POND), makak.
- Batch 2 (0.5.0, gebruiker koos deze 10 expliciet): kangoeroe (hop!), struisvogel, krokodil,
  koala (day-naps "sleep"), MOL + MOLEHILLS (Molehills.java: display + chunk-PDC, max 1/12 bloks),
  neushoorn, pinguin (donkey-bray, wadel-roll), ORCA (dolphin-base, POND, eigen AI = spettert),
  piranha (cod, school 4-8), ara's (3 EXTRA looks; MOGEN op schouder landen — songbird niet).
- AI-audit batch 1: bot-audit 0 mismatches; Brain: schouder/bee-doelen gestript, olifantoren X-as,
  kraan geen waterdrijver. Rijden onmogelijk (interact-cancel).
- Eieren/beelden/journaal/advancements/Zukan: allemaal automatisch via MA.SPECIES + EGG dict.
- Docs 00/02/03 + seed (43 pagina's) geregenereerd.

## DEPLOYEN (later, bij de eerstvolgende herstart)
- HELE map `Wildlife/fmm-models/wildlife/` → `plugins/FreeMinecraftModels/models/wildlife/`
  (alle 133, niet alleen de nieuwe — gait-rework zit overal).
- `Wildlife-0.5.0.jar` → plugins/ + herstart. Resourcepack: Iekcorbar-pack.zip / Zoo-pack.zip in RPM.
- Staff: wildlife-pagina's opnieuw importeren op /wiki.

## Test-aandacht in game
- Bestaande: deer/zebra-wandelgang, olifantoren, songbird-hop, kraan-stap, koi.
- Nieuw: kangoeroe-hop, pinguin-wadel + donkey-bray, koala-nap, mollen + molhopen in gazon,
  orca bovent water, piranha-school, ara op schouder, makak-troep, tanuki 's nachts.

## Nog open (later)
- Sets restant: poolvos, sneeuwuil; giraf, leeuw, hyena; fenek, meerkat, aasgier.
- Custom wildlife-sounds (nu vanilla placeholders) — sounds/make_sounds.py, ALLEEN toevoegen.
- Tanuki-knipoog-easter-egg: **GEBOUWD 29-10** (Wildlife 0.7.1, `Animals` case TANUKI, `tanuki_wink`).
- Mol: graaft MOLHEUVEL bij spawn; optioneel later: mol verdwijnt in heuvel bij panic.

## Opdracht (gebruiker)
Meer dieren. Keus: alle vier de sets, **in batches van 1**.
**Batch 1 = JAPAN-SET: tanuki, rode kraanvogel, koi, Japanse makak.**
Daarna (nog niet nu): koud-set (poolvos, pinguin, sneeuwuil), savanna (giraf, leeuw, hyena),
woestijn (fenek, meerkat, aasgier).

EXPRESSIE BEZIELD: "kijk goed naar bestaande EN nieuwe modellen/skeletten — veel dieren
bewegen NU nog gek". => ANIMATIES-EERST, en alle walk-cycli op de schop.

## Wat er mis is met de huidige animaties (diagnose uit make_animals.py)
- `quad_walk` = diagonal TROT (fl+br SAME fase, fr+bl inverted) voor deer/bear/boar/capy/hedgehog/
  tortoise/zebra. Echte viervoeters LOOPEN in laterale volgorde (achterpoot links -> voorpoot
  links -> achter rechts -> voor rechts); diagonal = draf/galop. Zie er "stevig/hollend" uit.
- OLIFANT moet PACEN (dezelfde kant samen) — nu diagonal. Ook period/amp: olifant 1.5 s/16° te snel.
- SCHILDPAD 1.6 s/16° te snel; everzwijn 0.55 s/30° enorm; das?? hedgehog 0.35/35 sew-machien.
- SONGBIRD: walk = vleugels FLAPPEN op 0.18 s (±70°) -> trilt op de grond als een naaidoos.
  Vogels op de grond = hoppen; vlieg-flap hoort niet in "walk" (FMM kiest walk/idle op snelheid).
- DUCK body-roll 7° per stap is veel te hevig (waggelen mag, maar ~4°).
- Body-bob zit 2x per cyclus (trot-bounce); bij laterale gang 1x per cyclus en kleiner.
- Birds: nek meebewegen bij stappen; kraanvogel-stap = poot langzaam optrekken, hold, zetten
  (geen sine — keyframes).

## Plan animaties (in make_animals.py, dan ALLE modellen opnieuw genereren)
- Nieuwe `gait(m, period, amp, head=..., tail=..., style="walk|trot|pace", bob=...)`:
  laterale fase-offsets: bl=0, fl=0.35, br=0.5, fr=0.85 (x2π); trot diagonaal; pace zelfde kant.
- Per soort: deer walk 0.95/18, zebra walk 0.9/16 (was trot 0.75/26), bear walk 1.3/14 + kop 4,
  boar walk 0.8/16, capybara walk 1.05/14 geen bob, hedgehog walk 0.38/24, tortoise walk 2.2/10
  (extra body-hold), elephant pace 1.9/12 + ear slow + kop sway 4, duck walk 0.7/22 roll 4,
  songbird: NIEUWE hop-walk (0.42 s: body 1 hop, beide pootjes ±26 X, wieken op +10° flutter).
  Kreeft/hagedis/slak/insecten: amplitudes iets zachter; lizard body-rot 10->6.
- Modellen regenereeren: `python -I tools/make_animals.py <out>` -> fmm-models/wildlife/
  (check out-pad in main()/CLI!); daarna make_zoo_pack.py (statues.csv) — zelfde kubussen.

## Plan batch 1 dieren (na de animatie-pas)
Per dier: Species-entry + SIZE + builder + rare name/kleur + food + drops + sounds + (biomes,
group, weight, habitat, time) + Journal-advancements (automatisch via Species.values()? checken) +
ZukanData + spawn-ei + config? + docs/wiki-wildlife + seed + version 0.4.0.
- TANUKI  pig-base, bos/gebergte (bamboo, old_growth, taiga, cherry), 's nachts actief,
  ROLLETJE-easter-egg? CLAUDE.md: "the tanuki wink waits for the Tanuki feature" -> in code zoeken.
- KRAANVOGEL chicken-base (als duck), wetlands, statige long-leg walk, vleugels breed bij idle.
- KOI NEW habitat POND (in het water spawning — check Spawner; anders op land = fout),
  salmon-base (zwemmend), tail-sway walk, geen pootjes.
- MAKAak pig-base, sneeuw/bos + bronnen; zit-animatie (zitten op kont, net als meerkat later).

## Bronnen/notes
- make_animals.py: Model/Bone/cube/sym/box, wave(), quad_walk, oriented() (= -X forward, crab
  uitgezonderd), scaled() per SIZE dict; babychibi via HEAD_K; SPECIES list (id, builder, kw,
  rare recolor, rare kw); EXTRA; ITEMS (klauw/staart/journal). 828 regels.
- Fmm.java: FMM kiest idle/walk/death zelf op beweging; plugin speelt curl/hide/sleep/eat via
  playAnimation; GEEN snelheidsregie -> periodes moeten in het model kloppen bij de loopsnelheid.
- Species.java: base EntityType, scale=1 blok=1 m, health, speed (blokken/tick), habitat, time,
  group min/max, weight, biomes (substrings), icon (journal item), notes/hint, sounds, pitch;
  models()/rareModel()/isFood/foodText.
- Sounds: maak GEEN nieuwe custom sounds in deze batch — vanilla geluiden hergebruiken
  (wolf/fox/duck/cod), net zoals tortoise/hedgehog/crab nu al doen.

## GEFIXT 26-10 - hert liep scheef (gebruiker: "het hert is niet goed georienteerd, dat loopt nog schuin - ik denk dat het schuin op het originele mob staat, of de AI denkt dat. zoiets")
- De user klopte op de streep. make_animals.py's oriented()-docstring beschrijft exact dit symptoom:
  "FMM treats model -X as forward; heads drawn at -Z walked sideways, facing right" - het hert op de
  server draaide nog een pre-oriented (of verder verouderde) wl_deer-export. Extra bevinding: de walk-
  animatie is na 24 okt ook gewijzigd (sync-trot 0.75s/26° -> 0.6.0 phase-shifted walk 0.95s/20°), dus
  het server-bestand was dubbel verouderd. Deer-gedrag in Animals.java is onschuldig (alleen snort/
  flee/eat, geen rotatie-hacks; de krab heeft z'n +90 juist omdat-ie als enige -X-face gebouwd is).
- Actie: complete set gegenereerd met `python -I Wildlife/tools/make_animals.py fmm-models\wildlife`
  -> 133 bbmodels in de repo onder fmm-models/wildlife/ (30 soorten x adult/baby/rare/rare_baby +
  songbird/macaw-varianten; vanaf nu de bewaarde upload-bron, eerder stonden die files nergens vast).
- Deploy naar server: vervang ALLE bestanden in plugins/FreeMinecraftModels/models/wildlife/ door deze
  set en herstart (FMM laadt modellen bij enable). Check daarna een wild hert: kop recht vooruit, geen
  diagonale stand meer tijdens rennen.
- Staat een dier na deze deploy nog steeds scheef: dan is de map niet echt vervangen (vergelijk bbmodel-
  tijdstempels op de server) of heet het modelbestand niet wl_<soort>.bbmodel.

## NIEUW 31-10 - Animal Rescue event (0.8.4)
- Extremely rare (0.05% per tick per speler) spawnen van gevangen dieren
- 5 val-types: snare (string), cage (iron bars), net (cobweb), pit (dirt), bear_trap (anvil)
- Rechtsklik om te bevrijden -> achievement + bonus
- Achievements: first_rescue, conservationist (5), master_conservationist (10)
- Rescue-count opgeslagen in player PDC (rescue_count key)

## NIEUW 31-10 - Global advancement announcements (0.8.5)
- `global()` methode in Adv.java markeert advancements als globaal aangekondigd
- Bij het verdienen van een global advancement wordt een chat-bericht naar alle online spelers gestuurd
- Format: "PlayerName has earned the advancement [Title]"
- Global advancements: Living Encyclopedia, Master Naturalist, A New Machine in the Sky,
  Master Hunter, Master Conservationist, Master Photographer
