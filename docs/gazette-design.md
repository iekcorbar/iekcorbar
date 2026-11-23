# The Iekcorbar Gazette — ontwerp (besloten 22-11-2026)

Wekelijkse krant die als custom written book in-game verschijnt, met een lectern in spawn,
push-notificaties en een DiscordSRV-broadcast. Eén site-zip + één SiteSync-jar; pack ongewijzigd.
Fase 2 (23-11): het boek is kleurloos (geen `&`-codes) en heeft een FMM-model van een opgevouwen
krant (`gazette_newspaper`) i.p.v. het vanilla boek — geen kiosk en geen pack-rebuild.

## Randvoorwaarde: twee databases

De site en de plugin delen géén database:

- Site (Plesk-lokaal): `news_posts`, `events`, `users`, `settings`, …
- Plugin (RevivenNode, `s30266_iekcorbar_player_data`): `player_stats`, `site_command_queue`, `site_queue_status`, …

De brug is `site_command_queue`: de site schrijft er commando's in (`pluginDataQuery`), SiteSync
consumeert ze als console. De plugin leest nooit site-tabellen; de site leest plugin-tabellen alleen
via `pluginDataQuery`. De "laatste editie"-marker staat in de **site**-DB (`db.saveSettings`),
de plugin houdt `gazette.yml` bij.

## Besluiten

| Vraag | Keuze |
| --- | --- |
| Frequentie | Wekelijks, vaste dag: zondag 18:00 servertijd |
| Trigger | Automatisch (plugin-scheduler → HTTPS naar site) + handmatige override via Staff Panel |
| Distributie | Iedereen eenmalig per editie (online direct, offline bij join) + lectern in spawn om te herlezen |
| Inhoud | `/news`-posts + aankomende events + community-cijfers + roterende "Did you know" |
| Discord | `/discord broadcast` via de bestaande command queue (DiscordSRV 1.30.5 draait live) |
| Kopieerbaar | Ja — `copyable: true`, spelers mogen 'm meenemen en op eigen lectern zetten |
| Fase 2 | FMM-model `gazette_newspaper` voor het boek + alle kleurcodes eruit (23-11, geen kiosk/pack) |

## Flow

```
Staff Panel → Publish (of plugin-scheduler op zondag 18:00)
   ↓  POST /api/gazette/publish  (sessie OF Bearer GAZETTE_SECRET)
   ↓  site leest eigen DB + bouwt pagina's (src/lib/gazette.js)
   ↓  idempotent op ISO-week via settings.gazette_last_issue ('2026-W48')
   ↓  2 rijen in site_command_queue:
        1. ibook gazette <issue> <base64 pagina's>   (command is TEXT, ≤ ~45 KB payload)
        2. discord broadcast 📰 Gazette #N • kop • kop • iekcorbar.com/news
   ↓  broadcastUserNotification('news', ...) → push (FCM / Expo / web)
   ↓  logAudit
   ↓
SiteSync consumeert:
   ↓  books/weekly_gazette.txt overschrijven (stabiele id, volume = editienummer)
   ↓  /ibook reload → geven aan iedereen online → lectern vullen → gazette.yml bijwerken
   ↓  bij join: PDC gazette_issue < current → boek geven
```

De site en de plugin delen géén database. De "laatste editie"-marker staat in de site-DB; de plugin
heeft z'n eigen `gazette.yml`. Voor automatische publicatie roept de plugin de site aan over HTTPS
met een gedeeld geheim (config `gazette.secret` ↔ `.env.local` `GAZETTE_SECRET`).

## Bestandslijst

### Site
- `src/lib/gazette.js` — inhoud samenstellen, pagina's splitsen, base64, queue-commando's bouwen.
- `src/app/api/gazette/publish/route.js` — dual auth, idempotentie, enqueue, push, audit.
- Staff Panel → News — knop "Publish edition" (+ "Republish" als de week al bestaat).

### Plugin (SiteSync 1.19.0)
- `Gazette.java` — scheduler (HTTPS-call), `ibook gazette`-afhandeling, lectern, join-delivery, `/gazette status|forget`.
- `BookManager.java` — kleine uitbreiding: `publishEdition(...)` die `weekly_gazette.txt` schrijft + reload + aan iedereen geeft.
- `IekcorbarSiteSync.java` — registratie.
- `config.yml` — sectie `gazette:`.

## Inhoud van een editie

1. **Cover** — "THE IEKCORBAR GAZETTE", editienummer + datum, "From the Guild Press".
2. **This Week** — `news_posts` sinds maandag 00:00, nieuwste eerst. Titel goud, inhoud door een kleine
   markdown-stripper naar 2–3 regels.
3. **Coming Up** — `db.getEvents()` met datum ≥ nu, max 4.
4. **By the Numbers** — `getTotalLandsClaimed()`, `getTotalActiveJobs()`, aantal leden, stemmen deze
   week, meest gespeelde speler via `pluginDataQuery` op `player_stats`.
5. **Did You Know** — roterende easter-egg-regel (weeknummer als index).
6. **Colofon** — `iekcorbar.com/news` · `discord.gg/G9P2khU7F4`.

Alles in `&`-legacy codes (BookManager snapt die al), gesplitst op `---`, max. ~80 pagina's van
256 zichtbare tekens.

## Falen netjes afhandelen

- Queue dood (stale heartbeat) → `sendRconCommand` faalt direct → duidelijke error in Staff Panel.
- Te grote payload → oudste posts afknippen + waarschuwing in de log.
- DiscordSRV offline → die ene queue-rij faalt, de krant is al geleverd.
- MySQL-fallback op de site → publish weigert met een melding, geen lege editie.
- Automatische call faalt → log + één retry na 10 min.

## Deploy

Fase 1: site-zip + SiteSync-jar → twee regels in `deploy/TE-DEPLOYEN.txt`. Alleen de jar vraagt een
server-restart. Pack ongewijzigd. Fase 2 (23-11): site-2311.zip + SiteSync-1.19.1.jar + twee
FMM-bestanden in `plugins/FreeMinecraftModels/models/steampunk_iekcorbar/books/`; de live
`config.yml` moet `gazette.model` naar `gazette_newspaper` meekrijgen (`saveDefaultConfig`
 overschrijft nooit). Geen pack-rebuild, geen client-herstart.
