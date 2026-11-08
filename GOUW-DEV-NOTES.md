# Gouw — geheugen/voortgang (houd dit file bij TIJDENS het werk)

Laatst bijgewerkt: 2026-11-08 (sessie 4 — zie "Sessie 2026-11-08" onderaan).


## Wat is Gouw
In-house opvolger van de (betaalde) **Lands** plugin: chunk-land claims.
Maven-project in `Gouw/` (Paper `26.2.build.129-stable`, Java 25, jar → repo-root).
- Eerste start: `plugins/Gouw/claims.json` leeg + Lands-SQLite-database aanwezig → `LandsImporter`
  importeert alles (owner/trusted/chunks). Daarna is claims.json de enige bron.
- **GEEN MySQL!** Claims lokaal in JSON; importer leest lokaal SQLite-bestand
  (`plugins/Lands/Data/database_v2.db`, via `plugin.yml libraries: sqlite-jdbc` die Paper op de
  SERVER ophaalt). De site-MySQL is `127.0.0.1` op de Plesk-host en de CMI-DB is buitenwerk-slechts
  voor de site — er hoeft vanaf deze developer-machine NOOIT een database-verbinding getest te
  worden. Vaste sessie-regel (gebruiker: "DAT gaat niet... is beveiligd"): **nooit proberen vanaf
  deze machine op DB's te connecten; testen gebeurt offline (`mvn package`) + in-game op de server.**

## Wat er mis was bij overname (vorige sessie, waarom het vastliep)
- 19:54 laatste goede build (`Gouw-0.1.0.jar`); 20:11–20:13 Land/ClaimStore/Listener/GouwMenu
  bewerkt NA die build → source compileerde niet meer:
  - `GouwMenu` riep `plugin.expiry().check()` aan, die classe/method bestond nog niet;
  - `GouwCommand` "border" riep `here.may(uuid)` aan zonder Flag (handtekening veranderd);
  - `setOwningOfflinePlayer`/`getOwningOfflinePlayer` bestaan NIET in paper-api (correct:
    `setOwningPlayer(OfflinePlayer)` / `getOwningPlayer()`);
  - GouwMenu was nooit als listener geregistreerd en had geen `/gouw menu`-command: menu onbereikbaar;
  - kapotte teksten (untrust-balk, `relics_gone`-methodnaam, "third with the chunk inspector").

## Gedaan (2026-10-25 avond, sessie 2)
- [x] `Expiry.java` geschreven: `expire-inactive-days` (default 0 = uit); online owners en owners
      zonder bekende last-play (geïmporteerd uit Lands) worden NOOIT weggehaald; log per expiratie.
- [x] `GouwPlugin`: `menu` + `expiry` velden, GouwMenu-registratie, `menu()`/`expiry()` accessors,
      hourly timer (start 1 min na enable; `check()` is self-no-op als config 0, dus reload werkt).
- [x] `config.yml`: `expire-inactive-days: 0` + commentaar.
- [x] `GouwCommand`: `/gouw` zonder args → menu; `menu`-case; help- en tabcomplete-uitbreiding;
      border-fix (`isMember`).
- [x] `GouwMenu`: SkullMeta-API gefixt; expiry-klik geeft resultaat + "uit"-melding; untrust-tekst;
      `gouwGone`; `pendingTrust` → ConcurrentHashMap; javadoc kloppend.
- [x] Versie 0.1.0 → 0.2.0 (pom + plugin.yml). Reden: memberFlags heten in Land.java "since 0.2"
      en menu/expiry zijn nieuwe features; 0.1.0 is alleen lokaal gebouwd, nooit gedeployd.
      (Terugdraaien als de gebruiker dat wil: pom+plugin.yml terug en hernoem root-jar.)
- [x] `mvn -q package` groen (BUILD OK, 20:28). Nieuwe root-jar: `Gouw-0.2.0.jar` (oude
      `Gouw-0.1.0.jar` laat ik staan als artefact).
- [x] CLAUDE.md: "### Gouw plugin" sectie toegevoegd (bestond nog niet).

## Open / volgende keren
- [ ] **In-game testen (server, de gebruiker):** jar `Gouw-0.2.0.jar` → `plugins/`. Eerste start
      moet de Lands-gouwen importeren (console-log `Imported N Lands gouwen (M chunks)`). Let op:
      Paper download sqlite-jdbc bij startup (eerste run even wachten).
- [ ] Testpunten: claim/autoclaim/unclaim-all, /gouw menu (vlaggen, trust via chat, untrust-klik),
      border-deeltjes, entry/actionbar, piston-over-grens, explosions, /gouw admin delete/import force/
      bypass, expire (days>0 + menu-knop).
- [ ] Eventueel later (alleen als gebruiker vraagt): wiki (`docs/wiki-gouw/` + seed via
      build-wiki-seed.py), site-koppeling, advancements-tab voor Gouw (`propkit/Adv.java`-template),
      Zukan provider? (Gouw heeft geen items).
- [ ] Lands zelf van de server af (plugins/Lands verwijderen) pas ná een goede import-draai.

## Site-koppeling + Lands-afbraak KLAAR (2026-10-26)
Van "eventueel later: site-koppeling" is nu alles gedaan zodat Lands weg kan:
- [x] Gouw zelf: commands (speler `claim/autoclaim/unclaim/trust/untrust/members/rename/info/list/
      near/border/leave`, staff `admin delete|import|reload|bypass`, alias `/g` `/claim`) +
      GouwMenu GUI (MAIN/FLAGS/ADMIN). Stond al in de plugin - niets nieuws nodig.
- [x] SiteSync 1.15.0: `GouwClaims.java` leest plugins/Gouw/claims.json (telt chunks per owner,
      once-per-mtime, offline owners included); MySQLSync pakt gouwClaims ABOVE de placeholder-
      bridge; config.yml: %lands% entries weg, comment "Lands retired". mvn groen.
- [x] website src/lib/db.js: getTotalLandsClaimed + getLandsClaimedByPlayer lezen nu enkel nog
      player_stats.lands_claimed (geen lands_players/lands_lands tables meer).
- [x] Wiki-pagina herschreven: db.json + iekcorbar-seed.sql "claiming-territory" -> titel/
      categorie "Gouw", content met menu + alle speler/admin commands. Categorie-lijsten zijn
      data-driven (geen vaste 'Lands' meer nodig).
- [x] site-zip herbouwd (IekcorbarSiteSync 1.15.0 + db.js erin; src/lib/db.json UITGESLOTEN van
      de zip, dus de LIVE-pagina staat nog als 'Lands' in MySQL).

## Crash gevonden: Gouw 0.2.0 draaide NOOIT (2026-10-26, 0.2.1)
Server-log (latest(5).log) liet `IllegalPluginAccessException: Unable to find handler list for
org.bukkit.event.player.PlayerEvent` bij enable. Oorzaak: `onBucket(PlayerBucketEvent)` -
PlayerBucketEvent is in Bukkit ABSTRACT (alleen de concrete Fill/Empty hebben een handlerList);
Bukkit kan er niet naar luisteren. Gouw crashte op registerEvents (regel 37), dus VÓÓR de
claim-bescherming: de Lands-import (reg 33) was wél gelukt, claims.json is gevuld, maar het
beschermen zelf stond uit. Fix: handler gesplitst in onBucketFill/onBucketEmpty, versie 0.2.1
(pom + letterlijke version in plugin.yml). mvn groen; Gouw-0.2.1.jar in de repo-root.
**Uploaden + herstarten; log moet dan tonen: "Gouw enabled - N gouwen".**

## Lands definitief van de server (stappen, gebruiker)
1. Nieuwe SiteSync jar 1.15.0 -> plugins/ (de server-de, niet de site-zip) + herstart; config.yml
   kan mee of handmatig de twee %lands%-regels schrappen.
2. Website: Plesk npm install + restart. DE LIVE WIKI-PAGINA nog even handmatig op Gouw zetten
   (moderatie > wiki pagina 'Claiming Territory' bewerken of opnieuw importeren) - want db.json
   zit niet in de zip en de DB is leidend.
3. Pas daarna plugins/Lands (+ plugins/Lands/Data) verwijderen. Gouw heeft de claims allang in
   claims.json; de lands_* tabellen in de plugin-DB staan/laten los (niemand leest ze meer).

## Bouwen / sleutelen
- Build: `mvn -q package` in `Gouw/` (Maven 3.9.14 + JDK 25 via Android Studio JBR — `javap` staat
  dáár: `C:\Program Files\Android\Android Studio\jbr\bin\javap.exe`; paper-api-jar in `~\.m2`).
- Repo-root-jar = current build (conventie; `make-zip.js` pakt alleen `IekcorbarSiteSync-*.jar` mee —
  Gouw gaat als los jar naar `plugins/`).
- Hygiëne: `jar xf` NOOIT in het projectdir uitpakken (staat nu Schoon: src/, target/, pom.xml);
  inhoud controleren via een tijdir of `-tf` (alleen listen).

## Ontwerpen om niet te vergeten
- Eén gouw per speler (Map<UUID,Land>), claims op chunk-niveau zoals Lands.
- Vlaggen per trusted-member: BUILD/ACCESS/MOB/PVP, default ALLES aan (oud Lands-gedrag).
- Explosies slopen nooit door claims; zuigers mogen geen blokken over een grens duwen.
- Bypass = per-speler toggle-set (geen permissie-check overal).
- Trust-flow: chat-naam → UUID (zoals Relics gift box); async-chat + main-thread scheduler.
- API-valken: SkullMeta is setOwningPlayer/getOwningPlayer (GEEN ...OfflinePlayer-varianten).

## Sessie 2026-11-08 (deze machine: Linux, ~/projecten/iekcorbar-final)
- NOTITIES LIEPEN ACHTER: wat hierboven staat stopt bij 0.2.1 (26 okt). Daarna, alleen uit de
  git-log: 0.3.0 = chunk-limiet per rank (`gouw.chunks.<n>`, c1312125) en buychunks/bonus per
  speler + "Land Surveyor"-quest (061d3031). Zie die commits voor details; aanvullen bij next werk.
- **Bug-fix 0.3.1: border-deeltjes te klein/licht** (gedrag: wel zichtbaar maar flets/goudstof-dopje).
  Oorzaak in Border.java: `DustOptions` met size 1.1f/1.2f en lichte goudtint (225,180,90 /
  255,210,110) — op licht terrein nauwelijks te zien. Fix: verzadigd amber (255,158,30 chunk-omlijning,
  255,178,45 volledige border) + size 2.6f/3.0f. DeeltjesAANTALlen/timing unchanged (spacing 2,
  cap 160 chunks, 5 pulses via command+menu).
- Bouwen op deze machine: `/home/baaye/tools/apache-maven-3.9.9/bin/mvn -q package` in `Gouw/`
  (JDK 25.0.5; de Windows-pad-notities hierboven zijn niet meer actueel). Waarschuwingen over
  jansi/Unsafe zijn Maven-eigen ruis op JDK 25, niet de build.
- Artefact: jar → gekopieerd naar `deploy/` (deploy/ is git-ignored, jars worden daar niet getrackt).

## 0.4.0 "Dorpen" — stadssysteem (gebruiker: "towny-achtig, wel factie geen allies/enemies")
Ontwerp zoals afgesproken in deze sessie:
- **Rangen**: eigenaar = mayor (nooit in `ranks`), leden = ASSISTANT (officer) of RESIDENT.
  Officer mag beheren (claimen/unclaimen/uitnodigen/kicken/rechten/koop), resident heeft alleen zijn
  per-lid rechten. Promote/demote ALLEEN mayor. Wel factie, GEEN alliantie/oorlog.
- **Rechten-systeem per resident (gebruiker: "wat uitgebreider")**: `Land.Flag` is nu
  **BUILD / CONTAINERS (kisten) / INTERACT (deuren, bedden, knoppen) / MOB / PVP / CLAIM**.
  Oude globale `ACCESS`-vlag splitst bij load naar CONTAINERS+INTERACT (legacy parsing).
  Elk resident heeft `perms: uuid -> EnumSet<Flag>`; **afwezig = volgt de defaults** (`memberFlags`,
  instelbaar per gouw via menu-pagina "New-resident rights"; default: alles behalve CLAIM aan).
  Eenmaal expliciet aangepast (`togglePerm` materialiseert bij eerste klik) volgt die resident de
  eigen set. `may()` = mayor/officer altijd true, resident via rights. CLAIM is de enige vlag die
  claimen INTO de town toestaat (`ClaimStore.claim` checkt `may(CLAIM)`); unclaim/buy blijven
  mayor+officer. Listener: onInteract kiest CONTAINERS vs INTERACT via `accessFlag()`.
- **Claimen**: leden claimen INTO de gouw van hun mayor (`homeOf`); residents zonder CLAIM-recht
  krijgen een foutmelding. invites worden geweigerd als de doel nog een eigen gouw heeft
  (1 gouw-per-speler-invariant blijft).
- **Stadskas**: `bank` per land in claims.json. **deposit EN withdraw voor élke resident**
  (gebruiker 08-11: "citizens kunnen ook deposit en withdrawl"; kopen/unclaimen blijft
  mayor+officer). Koop-/withdrawnoemen via BANK-pagina (10/50/250, chunks 1/5/10).
  Inwoners vullen de kas uit hun JOB-VERDIENSTEN — die uitbetaling loopt via
  JobsReborn/onboarding/quests, **nooit via Gouw**.
- **Meer claims KOPEN in de plugin zelf**: `/gouw buy [n]` + buttons; prijs `price-per-chunk` (250),
  per-gouw-plafond `town-buy-max` (100), betaald uit de KAS. Limiet = rank-limit mayor
  (offline → config default + bonus) + `purchased`. Persoonlijke bonus (contract NPC) blijft.
- **KORRECTIE — misverstand over jobs (gebruiker 08-11):** spelers kiezen hun job al tijdens
  onboarding/tutorials via quests (JobsRank 1–5, NPC-flow) en JobsReborn betaalt daarvoor.
  **Gouw wijst GEEN jobs toe en betaalt GEEN salarissen.** De eerdere `/gouw job`-assignering,
  `Salaries`, `JobsReader` (config-lezen), JOBS-menu-pagina, `payroll` en alle `salary*/jobs-config`
  config-sleutels zijn daarom **weer verwijderd** vóór deploy. Mocht er ooit een kas-spek van jobs
  komen (bijv. % afdracht per job-uitbetaling naar de stadskas), dan hoort dat bij de
  JobsReborn/quest-kant, niet in deze plugin.
- **Data claims.json**: nieuwe velden `ranks`/`perms`/`bank`/`purchased`; `trusted` wordt nog
  GESCHREVEN (afgeleid) en nog GELEZEN (migration) — SiteSync GouwClaims hoeft er niet op verandert.
  Oude files laden naadloos (legacy `flags: [...ACCESS...]` → split; leden zonder `perms` erven
  de defaults).
- **Menu**: 27-slot hoofd (LAND/claim/auto/unclaim/border/BUY/DEFAULTS-rechten/invite/RESIDENTS/BANK)
  + RESIDENTS 54-slot (heads 10-16 + 19-43; klik = Rechten-pagina, shift = remove, 45 invite, 49 back)
  + MEMBER 27-slot (hoofd 4, zes vlag-toggles op slots 11/13/15/17/19/21, promote/demote 24 (mayor),
  remove 25, back 22) + BANK + ADMIN. DEFAULTS-pagina (nieuwe-resident-standaarden) met mayManage-check.
- Economie via `EconomyRef` (Vault reflectie; oude inline withdraw in GouwCommand vervangen).
- Versie 0.4.0 (pom + plugin.yml; softdepend Vault, **Jobs niet meer** — Gouw raakt JobsReborn niet
  meer). `mvn -q package` groen; `deploy/Gouw-0.4.0.jar`.
- **Taal (gebruiker koos: ALLES ENGELS):** alle player-facing strings consequent Engels —
  burgemeester→**mayor**, adjunct→**officer**, inwoner→**resident**, kas→**bank**. Term "taak/job"
  bestond al niet lang en is met de job-correctie helemaal verdwenen.
- **Open: in-game testen (server, gebruiker)**, dan pas feel/price-tuning:
  1. jar wisselen + herstart; claims.json upgradeert automatisch (trusted → residents met defaults,
     oude ACCESS-vlag splitst netjes).
  2. Test: claim als mayor; invite → resident mag NIET claimen (geen CLAIM), officer Wél;
     per-resident rights pagina: toggles, promote/demote, shift-remove; kas: resident deposit én
     withdraw; buy chunks (prijs 250, plafond 100); piston/explosie-gedrag unveranderd checken.
  3. Delen defaults-per-gouw: "New-resident rights"-pagina werkt per gouw apart (niet globaal).
- Eventueel later: wiki-pagina's docs/wiki-gouw uitbreiden met dorpen + rechten; SiteSync-sitekaart
  met mayor per gouw; town level (verhoogd rank-limit per inwoners-aantal)?


