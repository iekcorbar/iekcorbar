# Sakagura — geheugen/voortgang (bijhouden TIJDENS het werk)

Laatste stand 25 okt ~21:25: ALLES gedeploy ( zie SAKAGURA status hieronder).

# NIEUWE SESSIE-TAAK: Wildlife — extra dieren + ANIMATIES OPschonen (25 okt, 21:30)

Gebruiker wil meer dieren. KEUZE GEMAAKT: alle vier sets, maar in batches van 1 set.
**Batch 1 = JAPAN-SET: tanuki, rode kraanvogel, koi, Japanse makak** (past bij sakura-thema;
tanuki ontgrendelt de bestaande wink-easter-egg-belofte). Daarna: koud-set (poolvos, pinguïn,
sneeuwuil), savanna-grootwild (giraf, leeuw, hyena), woestijn-set (fenek, meerkat, aasgier).

**EXPRESSIE BEZIELD (gebruiker): "kijk goed naar bestaande EN nieuwe modellen/skeletten -
veel dieren bewegen NU nog gek, moet zo natuurlijk mogelijk."** => eerst de animatie-pijplijn
in make_animals.py + Fmm/Animals bestuderen, bestaande modellen verbeteren (gewrichten,
fasering loopbenen, wiepperiode, amplitude), DAN de vier nieuwe bouwen.

Wettendom per dier: Species-entry + wl_<id> en wl_<id>_rare + _baby-modellen (make_animals.py),
Base-mob keuze, Brain/gedrag-droppers, spawn-biomes, sounds (sounds/make_sounds.py),
ZukanData items? (spawn-eieren), Journal advancements, wiki docs, evt. easter-egg-koppeling.
Versie Wildlife 0.3.5 -> 0.4.0.


Laatst bijgewerkt: 2026-10-25 ~21:00. Taak van de gebruiker: **de hele Sakagura plugin repareren;
distileren werkt in het echt NIET** (eerdere CLI-claims "werkt" waren fout — aannames zijn hier
niets waard, alleen build + uitlegbaar ontwerp).

## Hoofdzaak: distilleren
- Oude ontwerp: `Distilling.java` registreert een Paper `PotionMix` (brew + glowstone in een
  **brewing stand**) + `BrewEvent` geeft elke fles een `distills++`. Op de live server (Paper 26.2)
  gebeurt dat nooit. Kan hier niet getest worden; de combinatie glowstone+potion is ook nog eens
  exactly vanilla's **splash-modifier** → fragile afval-land met Paper mixins.
- **Beslissing: mechanisme vervangen, niet reanimeren.** Nieuwe **Steam Still**: een echte
  BARREL met PDC `sakagura:still` + eigen model + eigen timer. Bepalend en in eigen code:
  - fles + **glowstone dust** in de still, **vuur/lava/campfire eronder** (zelfde `Cauldrons.heated`);
  - elke `distilling.seconds-per-run` (default 20 s): 1 glowstone op, alle bottel-stacks in de still
    (max `distilling.bottles-per-run` stacks/run, default 3) krijgen `distills++` + step 'D';
  - wolkdeeltjes enquanto bezig, `sakagura:distill.drip` per run; hoppers kunnen erin/uit (gewoon vat);
  - right-click = vanilla barrel-GUI + status-bericht; `distilling.debug` logt wat de still ziet.
- `Distilling.java` VERWIJDEREN (ook de PotionMix-inschrijving — anders blijft de brouwmix de
  brewing-stand vervuilen).
- Brew-model / recipes.yml / Recipes.score hoeven NIET anders: D-stap blijft `runs`.

## Ook aan te passen (Wordings die "brewing stand" beloofden)
- `Goods.next()` lore → "distill it in a Steam Still (Nx)"
- `ZukanData.describe()` 'D' → "Distill Nx in a Steam Still (heat + glowstone dust)"; items: still toevoegen
- `/brew` help-tekst; `docs/wiki-sakagura/00-intro.md` + `01-sakagura.md`; wiki-seed opnieuw (`python -I scripts/build-wiki-seed.py`)
- `Sakagura.java` wiring: veld `stills`, listener, tick, loadChunk, relight, removeAll (isStopping-guard), help
- `Goods.java`: stillKey-veld + `still(amount)` + `isStillItem(it)` + shapeless recipe in registerRecipes
  (barrel + 2 ijzer + blaze poeder + glowstone), 
- `Achievements.java`: "steam_still" item-achievement + scanner-haak
- `config.yml`: nieuwe sectie `distilling:` (seconds-per-run, bottles-per-run, debug); `debug-distilling` weg
- `Sakagura/tools/make_pack.py`: BASE "glow" kleur + `still()` model + `model("still", ...)`;
  daarna: `python -I tools/make_pack.py resourcepack` (in Sakagura/) en `python -I scripts/merge-packs.py`.
- Versie 0.2.7 → **0.2.8** (pom; plugin.yml filtert vanzelf), jar naar repo-root (0.2.7-artefact laat staan).
- CLAUDE.md: Sakagura-paragraaf "Distilling: Paper PotionMix..." vervangen + dag-entry onderaan.

## Klein-bug van de audit meepakken
- `Cauldrons.tick`: blok geen WATER_CAULDRON meer → pot blijft oneindig in de map (spookdata na
  explosion/sloop buiten `onBreak`). Fix: dode pots verzamelen en `remove(pot)` (buiten de loop,
  want remove() mutteert de map!).

## Wat WEL is nagekeken (ok of bewust zo)
- Cauldrons cook/mix-tellers; draw(3 flessen); Casks aging-settle-flow; Garden tripwire index 30+;
  Ingredients PDC-keys mc:/kitchen:/sakagura:/sw:/brew:; Drunk-effecten; Achievement-scanner;
  pom resource-filtering voor plugin.yml. Sounds `sakagura:distill.drip` + bubble bestaan (sounds/out/sakagura).
- geluids/sound `sakagura:distill.drip` wordt hergebruikt door de still (geen nieuwe geluiden nodig).

## Status na afloop (25 okt ~21:25)
- ALLES GEËNLOAD (gebruiker): site-zip + Sakagura-0.2.8 + SiteSync-1.14.5 + Gouw-0.2.0 + packs.
  Sakagura config.yml is NU DEFAUT (oude server-config verwijderd → geseed uit de jar): eigen
  server-edits (docs-url/aging/drunk/getal) staan dus weer op default; distilling-sectie aanwezig.
- Testlocus nog: still-draait-in-spel, Gouw-import-console, boek-model, /sakagura + wiki-refresh.
  Bij probleem still: distilling.debug: true + /brew reload → console zegt wat de still ziet.

## Doelen checklist (repair distilleren) — ALLES KLAAR behalve in-game test
- [x] Stills.java + Goods still-item/recipe + wiring + Distilling.java weg + config
- [x] Achievements (steam_still) + ZukanData + next()/help wordings
- [x] make_pack.py still-model → pack rebuild → merge-packs (Iekcorbar-pack.zip sha1 d288c1b7)
- [x] docs 00/01 + build-wiki-seed (15 pagina's, "Steam Still" erin)
- [x] mvn package groen, jar **0.2.8** in repo-root
- [x] Cauldrons spook-pot fix (dead pots worden opgeruimd in tick)
- [x] CLAUDE.md sectie + dag-entry aangepast
- [x] IN-HET-SPILL: Sakagura 0.2.8 + laters 0.3.0 gedeployed (gebruiker "alles gedaan", 26 okt).
- [ ] EEN TESTKAART nog door Barend in-game te draaien (26 okt):
      1. cauldron stoken → 3 flessen draw → still plaatsen (BARREL ≠ gewoon vat:
         recipe barrel+2×iron+blaze+glowstone; right-click = vat-GUI + statusregel)
      2. flessen + glowstone dust in still, VUUR eronder → elke 20 s: 1 dust op,
         fles-stacks distills++ (max 3/turn), druppels + deeltjes; `distilling.debug: true`
         logt wat de still ziet
      3. vat-rijping + drinken (kwaliteit/dronkenheid) → /ibook give <jij> brewers_manual:
         boek opent, "New lore"-advancement bij eerste lezing
      4. WANDERER-TEST (nieuw, SiteSync 1.15.2): plugins/WanderingTrades/trades/ =
         brewers_manual.yml uploaden + /wanderingtrades reload (of restart) → trader die
         het boek verkoopt: 8×emerald + 1 book → plain boek met lore-lijn
         "Sakagura brewer's manual" → eerste right-click = echte boek + open
      5. Gouw-import console-regels + /sakagura-sitepagina + wiki-seed (15 pagina's) checken

## Andere opdrachten van de gebruiker (25 okt, na de repair)
- [x] **Sitepagina voor brouwen als /steamworks** — KLAAR: /sakagura (server component, 11 secties,
      28 renders in public/recipes/sakagura/ via preview3d.py TRANSPARENT + playground-component
      SakaguraPlayground.js met de Recipes.score-js-poort), nav-item 'Sakagura Guide' (Wine) in
      providers.js beide lijsten. `npm run build` groen, /sakagura statisch (○).
- [x] **In-game "Brewer's Manual" boek via /ibook in IekcorbarSiteSync, met FMM-model en info** —
      KLAAR: books/brewers_manual.txt (model sakura_journal, loot-weight 3), BookManager seeding-regel,
      versie 1.14.5, build OK, jar in repo-root (21:04). Server: nieuwe SiteSync-jar + herstart; boek
      verschijnt dan automatisch in plugins/IekcorbarSiteSync/books/. Test: /ibook give <jouw> brewers_manual.
      - [x] Optie alsnog uitgevoerd (26 okt): verkopen via wanderer-trades —
      wanderingtrades/trades/brewers_manual.yml (marker-lore "Sakagura brewer's manual") +
      SiteSync 1.15.2: BookManager.swapTraderBook gemeneraliseerd naar
      TRADER_BOOK_MARKERS-map (engineers én brewers; uitbreidbaar per boek).
- [x] **PDF van alle easter-eggs van deze week — PRIVÉ.** — KLAAR:
      `EasterEggs-PRIVE-2026-10-25.pdf` in repo-root (3 pagina's, rood PRIVÉ-kopblok op élke pagina).
      Generator: `C:\Users\baaye\AppData\Local\Temp\opencode\make_easter_eggs_pdf.py` (buiten de repo;
      python + reportlab, DRAAI ZONDER -I want -I blokkeert site-packages).
      26 grapjes uit de CODE gehaald (Steamworks/Kitchen/Sakagura/Wildlife/Relics/Kura/Zukan/Satchel/
      SiteSync gagboek), incl. triggers, action-bar-teksten en hidden advancement-id's.
      make-zip.js pakt hem NIET mee (whitelist) — niet in de deploy. Tanuki-knipoog staat onder
      "nog niet gebouwd".
- [x] Printbare upload-checklist → **Upload-checklist-2026-10-25-avond.pdf** (repo-root, 1 A4-pagina;
      generator-script in %TEMP%\opencode\make_upload_checklist_pdf.py, draaien ZONDER -I).

- [ ] **DAARNA (gebruiker, 25 okt): sitepagina voor brouwen, als /steamworks.**
      Overleg even: (a) /brewing uitbouwen (bestaat al als mini-game + "In-Game Guide" tab) of
      (b) nieuwe /sakagura "leg uit"-pagina. Stijl van /steamworks: server component + Section/Card/
      Table helpers + afbeeldingen + `use client` interactieve component + link naar wiki.
      Beschikbare assets: public/recipes/kitchen/ + public/recipes/icons/ + renders via
      `Sakagura/tools/preview3d.py` (en eventueel new: public/recipes/sakagura/).
      Interactieve ideeën: brouw-tijd/quality calculator, distilleer-simulator, ranken via /brew.
      Nav + eventueel /sakagura/... subpagina's; docs-url in Sakagura-config kan ernaar wijzen.


## ANDERE OPDRACHT (25 okt ~23:20): /brewing van de website af - KLAAR (npm build groen)
Gebruiker: de pagina was dubbel (in-game Sakagura + /sakagura + wiki) - VERWIJDERD, inclusief
het "zoeken van hidden items" (Ingredient Hunt). Uploaden + site-zip REBUILD doet gebruiker zelf
(de oude zip van vanavond bevat nog /brewing!).

WEG: src/app/brewing/, alle 7 src/app/api/brewing-routes, ingredient-hunt.js, tipsy-effect.js,
brewing-seeds/, brewing-visuals.js (had geen importers meer), hunt+tipsy blokken in providers.js,
beide nav-items, footer -> /sakagura "Sakagura Guide", "Active brewing vats" home-kaart +
berekening in api/status, Steam-Brewery-kaartlink -> /sakagura, recipes-link -> /sakagura,
hele clearsTipsyEffect/"Your Remedies"-plumbing in shop-page + items-route-veld + consume-tak in
purchases-route, FIRST_BREW uit lib/achievements, Brewing-tab + states + useMemo + tierName +
FlaskConical uit members/[username], 'brewing' uit HIDEABLE_SECTIONS + BUG_CATEGORIES,
hele brewing/tipsy/festive CSS-blok (~80 lijnen) uit globals.css, /brewing->/sakagura permanent
redirect (next.config.mjs), mini-game-teksten in docs 00-intro/01-sakagura -> /sakagura-gids +
seed geregenereerd (15 pagina's).

BEWUST BLIJDEN: db.js brewing_*/hunt-methoden + consumeShopPurchase (dead code), DB-tabellen
(owner dart later zelf), first-brew DB-rij (verschijnt nu als "custom" in staff-UI, mag weg),
case 'brewing' notification-icon + push-label (oude rijen), AuditLogView-area-label, "Brewery"
wiki-categorieen, Sakagura comments "from iekcorbar.com/brewing" (historisch).
Verifieerd: npm run build groen; /brewing niet in routes; redirect staat als 308 in
routes-manifest.json.

## Bouwen
- Build: `mvn -q package` in `Sakagura/`. Pack: python scripts zoals hierboven.
- **Regel uit de vorige sessie: nooit proberen vanaf deze machine een DB te benaderen.**
