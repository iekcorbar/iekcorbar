# Gouw — geheugen/voortgang (houd dit file bij TIJDENS het werk)

Laatst bijgewerkt: 2026-10-25 20:35 (sessie 2 — vorige sessie liep vast; alles hieronder is gedaan
behalve het in-game-gedeelte).

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
