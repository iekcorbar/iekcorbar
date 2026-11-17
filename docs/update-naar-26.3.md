# Update naar Paper 26.3 — voorbereiding & checklist

**Datum:** 6 nov 2026  
**Server:** 82.165.250.99 (Pterodactyl-container `06b962a3-...`, Paper 26.2 → 26.3)  
**Huidige pack-build op server:** iekcorbar-pack-build33 (lokaal BUILD=37)  
**Builds (custom plugins):** ✅ Herbouwd tegen Paper 26.3 API (paper-api 26.3.build.230-stable) op 6 nov 2026. Alle 15 jars staan in `deploy/` (zie TE-DEPLOYEN.txt). Nog niet naar de server geüpload (deploy-batching).  
**Bestaande nacht-backup routine:** `/usr/local/sbin/iekcorbar-backup` (04:00, 7 rotaties) blijft ongewijzigd.

> Let op: deze checklist is de **voorbereiding**. De eigenaar doet de daadwerkelijke server-update en de downloads die ik niet kan doen handmatig. Ik (agent) kan de custom iekcorbar-plugins herbouwen zodra je het sein geeft.

---

## Stap 0 — Voor je begint (aanbevolen volgorde)

1. **Server stoppen** via het Pterodactyl-panel (of console: `stop`). Wacht tot hij helemaal down is.
2. **Extra snapshot** (optioneel, naast de pre-update tar hierboven): panel-backup of een `cp -a` van de volume naar een tweede schijf.
3. **`server.properties` / `paper-global.yml` / `bukkit.yml` bewaren** — die blijven bij een update meestal staan, maar maak er een kopie van.
4. **Plugins-map backup** (zit al in de tar, maar een aparte `plugins/`-kopie maakt rollback sneller).
5. Eerst **Paper-jar** vervangen, dan plugins, dan starten en log controleren.

---

## Stap 1 — Paper server-jar (download)

**Paper 26.3 build #244 (latest op moment van schrijven):**

- Directe download: `https://fill-data.papermc.io/v1/objects/9601f9a26a2a52a0f2c807cf1abf691f96ee4fb287878313b601cebc2c51eabf/paper-26.3-244.jar`
- Alle builds: https://fill-ui.papermc.io/projects/paper (kies 26.3, hoogste buildnummer)

**Actie:** oude `server.jar` hernoemen naar `server.jar.bak-26.2`, nieuwe jar als `server.jar` plaatsen in de volume-root (`/var/lib/pterodactyl/volumes/06b962a3-.../`).  
De `libraries/`-map wordt door Paper zelf bijgewerkt bij de eerste start.

---

## Stap 2 — Custom iekcorbar-plugins (deze kan ik doen)

Deze plugins hebben hun bron in deze repo en zijn herbouwbaar tegen de Paper 26.3 API. **Ik doe dit zodra je het sein geeft** (mvn package per plugin, jar naar `deploy/`, daarna upload via SSH). Geen download-links nodig.

| Plugin (bronmap) | Huidige jar op server | Status |
|---|---|---|
| AfkCinema | AfkCinema-0.3.2.jar | herbouwen |
| Gouw | Gouw-0.2.1.jar | herbouwen |
| Hihou | Hihou-0.4.0.jar | herbouwen |
| IekcorbarSiteSync | IekcorbarSiteSync-1.17.2.jar | herbouwen |
| Kitchen | Kitchen-0.3.17.jar | herbouwen |
| Kura | Kura-0.1.8.jar | herbouwen |
| Relics | Relics-0.7.3.jar | herbouwen |
| Sakagura | Sakagura-0.7.13.jar | herbouwen |
| Satchel | Satchel-0.3.2.jar | herbouwen |
| Steamworks | Steamworks-0.3.40.jar | herbouwen |
| SteamworksAdditions | SteamworksAdditions-0.1.3.jar | herbouwen |
| SteamworksPower | (in SteamworksPower/) | herbouwen |
| Wildlife | Wildlife-0.9.10.jar | herbouwen |
| Zukan | Zukan-0.2.7.jar | herbouwen |
| IekQuests | (op server als IekBquest-3.0.5.jar — ander plugin, zie hieronder) | n.v.t. |

**TechCore** (`TechCore-0.9.35.jar`) is ook van ons (auteur Iekcorbar), maar de bron staat **niet** in deze repo → ik kan hem niet herbouwen. Zie sectie 3 (handmatig).

**Opmerking:** IekBquest-3.0.5.jar op de server is een *ander* plugin (auteur Wolf2323 e.a., "All Your Adventure Supplies") — geen bron in deze repo. Zie sectie 3.

---

## Stap 3 — Plugins die je handmatig moet updaten (download-links)

Deze zijn third-party (of zonder bron). Per plugin: huidige versie op server + waar je de 26.3-compatibele (of nieuwste) build haalt. **Kies steeds de build die "26.3" / "1.21.6" (of hoger) ondersteunt** en zet de jar in `plugins/` (oude jar eerst verwijderen of hernoemen naar `.bak`).

### Core / API (eerst updaten, andere plugins hangen ervan af)

| Plugin | Huidige | Download |
|---|---|---|
| LuckPerms | 5.5.71 | https://luckperms.net/download (Bukkit) |
| VaultUnlocked | 2.20.3 | https://www.spigotmc.org/resources/vaultunlocked.118573/ |
| PlaceholderAPI | 2.12.3 | https://www.spigotmc.org/resources/placeholderapi.6245/ |
| ProtocolLib | (onbekend) | https://www.spigotmc.org/resources/protocollib.1997/ |
| packetevents | 2.14.0 | https://github.com/retrooper/packetevents/releases |
| Citizens | 2.0.44-SNAPSHOT | https://www.spigotmc.org/resources/citizens.13811/ |
| WorldEdit | 7.4.6-beta-02 | https://enginehub.org/worldedit (of Modrinth) |
| WorldGuard | 7.0.19 | https://enginehub.org/worldguard |
| Multiverse-Core | 5.8.1 | https://www.spigotmc.org/resources/multiverse-core.390/ |

### MMO / RPG / combat

| Plugin | Huidige | Download |
|---|---|---|
| MMOItems | 6.10.1-SNAPSHOT | https://www.spigotmc.org/resources/mmoitems.39267/ (of MythicCraft) |
| MMOCore | 1.13.1-SNAPSHOT | https://www.spigotmc.org/resources/mmocore.70028/ |
| MythicLib | 1.7.1-SNAPSHOT | https://www.spigotmc.org/resources/mythiclib.63820/ (of MythicCraft) |
| MMOBridge | 1.12.0 | (onderdeel van MMOItems/MMOCore ecosysteem — mee-updaten) |
| EliteMobs | 10.9.12 | https://www.spigotmc.org/resources/elitemobs.40090/ |
| ValhallaMMO | 1.10.3 | https://www.spigotmc.org/resources/valhallammo.66521/ |
| ValhallaRaces | 2.3 | (zelfde auteur als ValhallaMMO) |
| ValhallaTrinkets | 2.9 | (zelfde auteur als ValhallaMMO) |
| MythicLib (zie boven) | — | — |

### Skript-ecosysteem

| Plugin | Huidige | Download |
|---|---|---|
| Skript | 2.17.1 | https://github.com/SkriptLang/Skript/releases |
| SkBee | 3.26.0 | https://github.com/SkriptLang/SkBee/releases |
| skript-reflect | 2.6.3 | https://github.com/TPGamesNL/skript-reflect/releases |
| FoxPapiSkriptExpansion | v1.0 | SpigotMC (zoeken: "FoxPapiSkriptExpansion") |

### Display / holograms / nametags

| Plugin | Huidige | Download |
|---|---|---|
| DecentHolograms | 2.10.1 | https://www.spigotmc.org/resources/decentholograms.96927/ |
| HeadDatabase | 4.24.0 | https://www.spigotmc.org/resources/head-database.14280/ (betaald) |
| TAB | v6.2.0 | https://www.spigotmc.org/resources/tab.57806/ |
| FreeMinecraftModels | 2.14.0 | https://www.spigotmc.org/resources/freeminecraftmodels.111166/ (Magmaguy) |

### Overige third-party

| Plugin | Huidige | Download |
|---|---|---|
| CMI | 9.8.11.0 | https://www.spigotmc.org/resources/cmi.3742/ (betaald, Zrips) |
| CMILib | 1.6.1.0 | (mee met CMI) |
| CMIEInjector | 1.0.2.4 | (mee met CMI) |
| AxGraves | 1.32.1 | https://www.spigotmc.org/resources/axgraves.117238/ (Artillex-Studios) |
| BetterStructures | 2.7.11 | https://www.spigotmc.org/resources/betterstructures.117237/ (Artillex-Studios) |
| DiscordSRV | 1.30.5 | https://www.spigotmc.org/resources/discordsrv.18494/ |
| Jobs (Reborn) | 5.2.6.6 | https://www.spigotmc.org/resources/jobs-reborn.4216/ |
| ItemJoin | (onbekend) | https://www.spigotmc.org/resources/itemjoin.12661/ |
| CommandBlockerPro | 1.4.1 | SpigotMC (zoeken) |
| FreezeEffect | 1.2 | SpigotMC (zoeken) |
| NPCGuide | 1.4.4 | SpigotMC (zoeken) |
| PyroLib | 1.6.0 | (onderdeel van Pyro-suite) |
| PyroMining | 4.6.1 | https://www.spigotmc.org/resources/pyromining.80164/ |
| ResourcePackManager | 2.4.9 | https://www.spigotmc.org/resources/resourcepackmanager.111530/ (of Hangar) |
| VillagerMarket | 1.13.3 | https://www.spigotmc.org/resources/villagermarket.97191/ |
| VotingPlugin | 7.1.1 | https://www.spigotmc.org/resources/votingplugin.15358/ |
| nuVotifier | (onbekend) | https://www.spigotmc.org/resources/nuvotifier.13449/ |
| WanderingTrades | 1.9.6 | https://www.spigotmc.org/resources/wanderingtrades.70768/ |
| IekBquest | 3.0.5 | https://github.com/WorkDBmarco91/IekBquest (releases) |
| TechCore | 0.9.35 | **Bron niet in repo** — herbouwen vanuit je eigen broncode of wachten op update. |
| AxAPI | (onbekend) | https://www.spigotmc.org/resources/axapi.117236/ (Artillex-Studios) |
| AfkCinema | — | (custom, zie sectie 2) |

### Resource packs in de mixer (server-side, geen plugin)

Deze zitten in `plugins/ResourcePackManager/mixer/` en worden door ResourcePackManager geserveerd. Na een Paper-update meestal ongewijzigd, maar controleer compatibiliteit van ResourcePackManager zelf (hierboven).

---

## Stap 4 — Configuratie die je moet controleren na de update

- `server.properties`: `rcon.password` bevat een backslash (`Verspanen+Baaye38\!`). De RCON-client in `/usr/local/sbin/iekcorbar-rcon` leest dit **raw** en logt daardoor niet in (auth-fout). De echte wachtwoord is `Verspanen+Baaye38!` (zonder backslash). Overweeg de backslash uit `server.properties` te halen of de rcon-script te laten unescapen. *(Niet blokkerend voor de update, wel voor de nacht-backup-flush.)*
- `paper-global.yml` / `bukkit.yml`: Paper migreert deze meestal automatisch; diff controleren.
- `plugins/*/config.yml`: plugin-configs blijven staan; sommige plugins herschrijven bij eerste start.
- Java-versie: de container draait `java_25` (yolk). Paper 26.3 vereist Java 21+. OK.

## Stap 5 — Na de update (testen)

1. Server starten, log scannen op `ERROR`/`SEVERE`/`NoClassDefFoundError`.
2. `/plugins` in-game: alle plugins groen.
3. Resource pack download-balk bij join (ResourcePackManager live-check).
4. Eén testwereld: chunk laden, entity spawn, command `/iekcorbar`-achtige.
5. Backup-script handmatig draaien: `iekcorbar-backup` en Discord-bericht controleren.
6. Indien alles OK: oude `server.jar.bak-26.2` en `.bak`-jars na een week verwijderen (ruimte).

## Wat ik (agent) kan doen zodra je het sein geeft

- De 15 custom plugins herbouwen tegen Paper 26.3 API (mvn package), jars in `deploy/`, upload via SSH, oude jars hernoemen.
- De resource pack herbouwen (BUILD ophogen in `merge-packs.py`, `merge-packs.py` + `audit-pack.py`).
- De iekcorbar-site (Next.js) updaten indien de API-versie in `IekcorbarSiteSync` wijzigt.

## Wat jij (eigenaar) handmatig doet

- Paper-jar downloaden & plaatsen (link hierboven).
- De third-party plugin-jars downloaden & vervangen (sectie 3).
- TechCore herbouwen (bron buiten deze repo).
- Server stoppen/starten via het panel.
- Config-diffs controleren.

---

**Backup-locatie (pre-update):**  
`/var/backups/iekcorbar-pre-update-26.3/iekcorbar-pre-26.3-2026-11-06_1712.tar.gz` (17 GB).  
Deze map wordt **niet** door de nacht-backup-routine aangeraakt (die werkt alleen in `/var/backups/iekcorbar/` met patroon `iekcorbar-*.tar.gz`), dus hij blijft bewaard totdat je hem zelf verwijdert.
