# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The website for **iekcorbar**, a Minecraft server (steampunk/sakura themed: wiki, events, member profiles, staff moderation, community projects, voting, chat). It's a Next.js 16 (App Router) site backed by MySQL, with a companion Spigot/Paper plugin (`IekcorbarSiteSync/`) that syncs live player data from the Minecraft server into the same database and exposes an in-game RCON command the website calls, and a companion Expo/React Native app (`mobile/`) that talks to this site's `/api/**` routes directly and adds push notifications.

## People (server-rollen, vastgelegd 27-10 door eigenaar)

| spelernaam | rol |
|---|---|
| `antimatter87` | **owner** (de gebruiker zelf - SSH/deploy/alle goedkeuringen lopen via hem) |
| `iekjebr36` | staff |
| `kooyer` | staff |
| `afriendlystomach` | staff |
| `greyarts` | staff - spelling door eigenaar nóg niet bevestigd ("weet ik niet zeker") |

Gebruik deze namen voor tests/scenario's (denk aan `/power give <owner> ...`, permissies,
Discord-links). De lijst komt van de eigenaar zelf; verifieer onbekende namen nooit door
spelerbestanden op de server door te spitten (identiteitsregel) - vraag het, of laat hem
het in het spel zelf checken.

## Commands

``
npm run dev      # start Next dev server
npm run build    # production build
npm run start    # run production build
npm run lint     # eslint (no eslint config file currently checked in â€” `next lint` will offer to create one)
``

There is no test suite/framework configured in this repo.

### Deployment (Plesk / Passenger)

The site is deployed to Plesk via Node.js/Passenger, not `next start` directly. `app.js` and `server.js` are **identical** custom HTTP servers (Passenger's entry point convention differs, so both are kept in sync) â€” they bind the port immediately and let Next.js `prepare()` finish in the background so Passenger doesn't time out waiting for the port. If you change server startup behavior, update both files identically.

`make-zip.js` (`node make-zip.js`) packages the deployable subset of the repo â€” `.next`, `src`, `public`, the server entrypoints, config files, `.env.local`, `.node-version`, and the compiled `IekcorbarSiteSync-1.1.0.jar` (filename tracks the plugin's own `pom.xml`/`plugin.yml` version â€” bumped from 1.0.0 on 2026-08-13 specifically so `/version IekcorbarSiteSync` in-game can distinguish rebuilds, after a live-debugging session where the version string never changing made it impossible to tell whether a freshly built jar had actually reached the server) â€” into a zip for upload (output filename defaults to `iekcorbar_iekje_barend.zip`; override via `ZIP_NAME=foo.zip node make-zip.js`). It shells out to `scripts/build-zip.py` (Python's `zipfile`) rather than a native Windows zip tool â€” both `tar -a -c -f x.zip ...` (bsdtar zip-emulation) and PowerShell's `Compress-Archive` produced archives that failed to extract correctly on the Linux Plesk host (the former mis-nested the archive when packaging the already-ZIP-format `.jar`; the latter writes backslash path separators the ZIP spec forbids). If this script needs further changes, keep using `build-zip.py` for the actual archive writing rather than reintroducing a Windows-native zip command.

**`node_modules` is deliberately excluded**: ZIP archives don't reliably preserve Unix executable permissions, so shipping `node_modules` this way causes `node_modules/.bin/next` to lose its `+x` bit on extraction, which surfaces as `sh: 1: next: Permission denied` (exit 127) when Plesk runs `npm run build`/`npm run start`. After uploading and extracting the zip on Plesk, run `npm install` there (or use the **"NPM install"** button in the Plesk Node.js app panel) before building/starting â€” this installs dependencies natively on the Linux host with correct permissions.

**`src/lib/db.json` is deliberately excluded too** (`scripts/build-zip.py`'s `EXCLUDE` set), after a real production data-loss incident: `db.js`'s MySQL-first-with-JSON-fallback design means any MySQL write that silently fails on production falls back to writing straight into production's own `src/lib/db.json` â€” so that file can end up holding real, live-only data (this has happened to chat messages, Starter Guide steps, and the seeded brewing recipes). Deploying used to overwrite that file with whatever was in the developer's own local `db.json`, permanently destroying whatever had only ever landed in production's fallback copy. If `db.json` genuinely needs to be reset or reseeded on the server, do that deliberately there (e.g. an admin-triggered route, same pattern as the brewing-recipe seeding described below) â€” never as a side effect of shipping unrelated code changes. This also means a flaky/failing MySQL connection in production is a real bug worth root-causing (check the app's server logs for `[MySQL DB Warning]` lines) rather than something to shrug off just because the fallback keeps the site visibly working.

**`.node-version`** pins the Node version (currently `24`) for nodenv (or nvm/fnm) on the Plesk host. Without it, a bare `npm install` there can fail with `nodenv: node: command not found` â€” nodenv falls back to a nonexistent `"system"` Node when nothing pins a version, which surfaces specifically in subprocess-spawned steps like a dependency's `postinstall.js` even though the top-level `npm install` command itself resolved fine. If Plesk's Node.js app panel is configured for a different version, update this file to match.

The various `*.zip`/`*.tar.gz` files at the repo root are build artifacts from past deploys (most predate these fixes and still bundle `node_modules` or use the older filename); they aren't source and don't need to be kept in sync with anything.

**`deploy/` is the ONE output folder (user rule, 2026-10-26 evening).** Every finished artifact goes there and nowhere else: the merged pack, freshly built plugin jars (copy `\<Plugin>/target/\<Plugin>-<version>.jar` there after a green `mvn package`), site zips from `make-zip.js`. Nothing gets copied to the repo root or the Desktop anymore; older text in this file saying "jar in root" predates the rule. The pack is named **`iekcorbar-pack-build<N>.zip`**, and `N` comes from the single `BUILD` constant in `scripts/merge-packs.py` - bump it with every rebuild, because it also writes the number into the in-game pack description, so the filename, the mixer file and what the client shows can never disagree again (the confusion of 26-10: three different `iekcorbar-packv2.zip` files with the same "build 2" stamp). `scripts/merge-packs.py` sweeps older packs out of `deploy/` by itself; jars and site zips get removed by hand once the user confirms they're live. `deploy/` is gitignored.

## Architecture

### Data layer: MySQL-first with a JSON fallback

All persistence goes through `src/lib/db.js`, exported as a single `db` object with one method per operation (e.g. `db.getWikiPages()`, `db.saveEvent()`). Every method:
1. Tries a MySQL query (via a shared `mysql2` pool, 2s query timeout).
2. On failure, falls back to reading/writing `src/lib/db.json` (a flat-file mirror of the schema, used mainly for local dev without a DB and as an emergency fallback in production).

**Important:** `db.js` has several methods defined twice in the exported object literal (e.g. `getRegistrations`, `getPolls`, `getRules`, `getGuide`, `getVoteSites` each appear twice, once early in the file and once near the bottom with a different fallback strategy). In a JS object literal the *second* definition silently wins â€” that's the one actually in effect. When editing behavior for any of these, check both definitions and edit (or consolidate) the later one, since the earlier one is dead code.

DB credentials come from `.env.local` (`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`), with hardcoded defaults baked into `db.js`/`migrate.js` as a fallback if the env vars are absent â€” those defaults are real production credentials, not placeholders, so treat this file's contents as sensitive.

`src/lib/cmi-db.js` is a second, separate MySQL connection (`CMI_DB_*` in `.env.local`) â€” to the **CMI plugin's own database** (a different RevivenNode MySQL instance entirely, reachable externally unlike the main `DB_HOST=127.0.0.1` one). Deliberately **read-only by convention** (only `SELECT`s, no write methods exist in the file) â€” surfaces real CMI-only fields (`Rank`, `Homes`, precise `TotalPlayTime`) that no RCON command returns, folded non-fatally into `api/player-stats`. Economy/balance changes still go through RCON (`api/shop/purchase`), never written here directly, so CMI's own live in-memory state stays authoritative and doesn't desync.

`src/lib/schema.sql` is the canonical table definitions. `src/lib/migrate.js` (`runMigrations()`) applies the schema and one-time-seeds MySQL from `db.json` if present â€” it's not wired into any npm script or route; run it manually (e.g. via a throwaway script) when needed. `iekcorbar-seed.sql` at the repo root is a standalone copy meant to be run directly in phpMyAdmin/MySQL CLI on the Plesk host.

### Minecraft integration (RCON + plugin)

- `src/lib/rcon.js` implements the Source RCON protocol from scratch over a raw TCP socket, with a persistent singleton connection (reconnects once on failure) configured via `MINECRAFT_RCON_HOST/PORT/PASSWORD`. If `MINECRAFT_RCON_PASSWORD` isn't set, `sendRconCommand()` no-ops and returns `"BYPASS"` â€” callers use that sentinel to skip in-game verification during local dev.
- `IekcorbarSiteSync/` is a separate Maven project (Java 25, `io.papermc.paper:paper-api:26.1.2.build.74-stable` â€” matches the live server's real Java 25 / Paper 26.1.2 as of 2026-08-13; retargeted from the old Java 17 / `org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT`, which no longer matched. Paper moved off the old `hub.spigotmc.org` snapshot repo at some point after 1.20.4 â€” modern builds are published as `io.papermc.paper:paper-api` on `repo.papermc.io` instead, added as a repository in `pom.xml`. `plugin.yml`'s `api-version` bumped to `'26.1'` to match) installed on the Minecraft server itself. It periodically (`database.sync-interval-seconds`) writes player stats (playtime, kills, balance, jobs, etc.) straight into the same MySQL `iekcorbar` database, and registers an in-game `/webstats <username>` command that the website queries over RCON for near-real-time stats. Its config (`IekcorbarSiteSync/src/main/resources/config.yml`) has the same MySQL credentials hardcoded as a fallback, independently of the Next.js side â€” if DB credentials rotate, update both.
- Registration flow (`src/app/api/auth/register/route.js`) is a 3-phase handshake: `initiate` (whitelists the IGN via RCON `whitelist add`), `send_code` (RCON `tellraw`s a 6-digit code to the now-online player), `verify` (checks the code, creates the user). Pending registrations live in `global.__pending_registrations` (in-memory, not persisted â€” lost on server restart). When RCON is unreachable in dev, `send_code` returns the code directly as `devCode` instead of failing.
- `src/app/api/status/route.js` polls the public `mcsrvstat.us` API and Discord's invite API for live server/Discord stats (not RCON), with graceful fallback values if either is unreachable.
- Besides the templated single-purpose RCON calls above (whitelist, kick/ban in `api/moderation`, skin-set, shop money-take, vote reward give), `api/admin/rcon/route.js` exposes `sendRconCommand()` directly as a free-text terminal â€” Staff Panel â†’ Console tab, gated by the `console` Staff Panel permission (`requirePermission(request, 'console')`, stricter than moderator-accessible kick/ban since there's no command allowlist â€” admin always has it, another role only if granted, see "Auth model" below). Every command + the server's exact reply is logged to `rcon_console_log` regardless of success/failure, as the only audit trail for what was run.

### Auth model â€” real sessions + per-role permissions, migration from client-asserted roles still in progress

Real, server-verified sessions exist (`src/lib/session.js`): login (`/api/auth/login`) issues a random token in an httpOnly cookie, checked against the `sessions` table on every request via `getSessionUser()`/`requireRole()`. Passwords are hashed (`src/lib/password.js`, Node's built-in `scrypt`, format `scrypt:<salt>:<hash>`) â€” a legacy plaintext password (any account created before this existed) is verified once against the plaintext value on its next login and immediately rewritten as a real hash (`db.setUserPasswordHash`), so there was no forced mass password reset.

This replaced an older, more permissive pattern that's *still partially in effect*: originally there was no session layer at all, the client stored the login response verbatim in `localStorage` (`iekcorbar_user`) and re-sent a `role` field on every privileged request (a body field for POST, a `?role=` query param for GET/DELETE), and API routes just trusted whatever role string the client sent. Several migrations have chipped away at this since (see `api/moderation` as the first, and the per-role permission system below), but a meaningful chunk of routes â€” most visibly the Staff Panel Settings sub-tabs (carousel/guide/rules/tips/vote sites/community projects/site-content labels) â€” still read `role` from the client body/query and trust it. When a route "should be blocked but isn't," or vice versa, check first whether it's actually calling `requireRole()`/`requirePermission()` or still doing the old `role !== 'admin' && role !== 'moderator'` check against a client-supplied field.

**Per-role Staff Panel permissions** (`src/lib/staff-tabs.js` + `src/lib/permissions.js`, `roles` table, Staff Panel â†’ Settings â†’ Roles & Permissions): which of the 10 Staff Panel tabs a role can access is configurable, not hardcoded to "admin gets Settings+Console, moderator gets the rest." `'admin'` is never a row in `roles` â€” it's hardcoded to always have every tab (`isAdminRole`) so a misconfigured/emptied table can never lock every admin out. `'moderator'` falls back to its pre-this-feature tab set (`DEFAULT_MODERATOR_TABS`) if no row exists for it yet. Admins can also create entirely new roles (e.g. a "Helper" limited to Reports + Bug Reports) â€” any such role goes through the same mandatory TOTP step as admin/moderator on login (`isStaffRole()` in the login route), not just whatever a plain "player" gets. `requirePermission(request, tabKey)` is the real enforcement, used the same way `requireRole()` is; `/api/admin/roles` (role management itself) is deliberately `requireRole(['admin'])` rather than permission-gated, so no role can ever grant itself more power through it.

`src/app/providers.js` (`ClientProviders`) is the app-wide client wrapper: holds the `user` in React context (`useUser()`), renders the nav/footer/login-register modal/GDPR modal, and drives the login/register flow described above. It's mounted once in `src/app/layout.js` and wraps every page.

### Route structure

Standard Next.js App Router: `src/app/<route>/page.js` for pages, `src/app/api/<resource>/route.js` for REST-style handlers (`GET`/`POST`/`DELETE` exported per file, no separate `PUT`/`PATCH` â€” updates go through `POST` with an `id` present). Nested resources like event registration and project signup live under `.../<resource>/<action>/route.js` (e.g. `api/events/register`, `api/projects/signup`).

Path alias `@/*` â†’ `src/*` (see `jsconfig.json`).

**Known-fragile pattern:** several API routes have been found calling `db.js` methods under a different name than what's actually exported (`db.getProjects`/`saveProject`/`deleteProject` vs. the real `db.getCommunityProjects`/`saveCommunityProject`/`deleteCommunityProject`; the entire chat backend â€” `getRecentChats`/`getMessages`/`sendMessage`/`markChatAsRead` â€” was referenced by `api/chat/route.js` while not existing in `db.js` at all). These fail silently into a generic 500 with no indication of the real cause. When a route "does nothing" or 500s with no useful error, check first whether the `db.*` method it calls actually exists under that exact name â€” don't assume the wiring is correct just because the route and UI look complete.

### Runtime file uploads (`public/uploads/**`)

`api/chat/route.js` (image attachments) and `api/profile/skin/route.js` (custom skins) write uploaded files straight into `public/uploads/<chat|skins>/`. This does **not** work the way it looks like it should: a Next.js production build (`next build`) snapshots `public/`'s contents at build time, so any file written there afterwards â€” including by these routes at runtime â€” 404s through Next's own request handler, even after restarting the server; only a full rebuild would make Next aware of it. To make uploads work immediately, `app.js`/`server.js` intercept every `/uploads/*` request **before** handing off to Next's `handle()` and stream the file straight from disk instead (with a path-traversal guard). If you add another upload feature, reuse this same `/uploads/` prefix rather than introducing a new one Next would need to serve â€” and remember `app.js`/`server.js` must stay identical, so any change here goes in both.

The exception is **private** uploads, which must not be reachable by URL without a permission check: Admin Shared Files (`api/admin/files`) and Story Room files (`api/story/files`, served by `api/story/files/raw` after `requireStoryAccess()`) are written to `private-uploads/<feature>/` below the project root instead, and only ever streamed back through their own API route. `make-zip.js` doesn't ship `private-uploads/`, so deploys never overwrite it â€” but never delete that folder on the server either.

### Mobile app (`mobile/`)

Separate Expo/React Native project (Expo SDK 57, Expo Router, TypeScript), scaffolded as a sibling directory the same way `IekcorbarSiteSync/` sits alongside the website. It has its own `package.json`/`node_modules` â€” it is **not** part of the Next.js build or `npm run dev`. It calls the website's `/api/**` routes directly over HTTPS (`mobile/src/lib/api.ts`, `API_BASE_URL`, overridable via `EXPO_PUBLIC_API_BASE_URL`); there's no separate mobile backend. It inherits the same client-asserted-auth trust model described above (stores the login response in `AsyncStorage` instead of `localStorage` â€” same shape, same lack of a real session).

Push notifications go through Expo's push service (no direct APNs/FCM credential management on our side): `mobile/src/lib/push-notifications.ts` requests permission and registers the device's Expo push token to `POST /api/push/register` after login, keyed by **email** â€” not a numeric user id, because `db.getUsers()`/the login route never select/return `users.id`; every other part of this codebase (roles, IGN, skin) already identifies users by email too, so `device_tokens.user_email` follows that convention rather than introducing an id the rest of the app doesn't have. `src/lib/push.js`'s `sendPushToUser(email, { title, body })` sends via Expo's HTTP push API and is the hook point for adding triggers (new chat message, moderation action, event reminder, etc.) â€” as of this writing nothing calls it yet, it's plumbing only. A real push token requires `npx eas init` to have been run inside `mobile/` (needs an Expo account) to populate `app.json`'s `extra.eas.projectId`; without it, registration no-ops with a console warning instead of failing loudly.

### Android app (native WebView wrapper) + FCM push

Besides the Expo app above there's a separate native Android app (Gradle/Java project on the developer's Desktop, "iekcorbar android app version", package `com.iekcorbar.app` â€” same package name as the Expo app, so the two can't be installed side by side) that simply loads https://iekcorbar.com in a WebView. `public/.well-known/assetlinks.json` holds its signing-certificate fingerprint for Android App Links.

Push for that app goes through **FCM HTTP v1 directly** (`src/lib/push.js`, tokens stored in `device_tokens` with `platform = 'android-fcm'`, still in the historically-named `expo_push_token` column), configured via `FCM_SERVICE_ACCOUNT_FILE` or `FCM_PROJECT_ID`/`FCM_CLIENT_EMAIL`/`FCM_PRIVATE_KEY` in `.env.local` (no-ops with a warning if unset). Every notification-bell entry is also pushed: `db.js`'s `queueBellPush()` is called wherever a `user_notifications` or staff `notifications` row is created (createUserNotification, createNotification, both broadcast helpers, the two raw wiki INSERTs) â€” so new chat messages (api/chat creates a 'chat' bell entry) push too. Staff-feed entries go to every user whose role passes `isStaffRole()`. Device binding: the app sets an `iekcorbar_push_token` cookie; the login route (`issueSession`) binds it to the user, the logout route unbinds it, and the app also POSTs it to `/api/push/register` on each page load (session-verified; no session = unbind).

### Custom books (plugin 1.6.0, `BookManager`)

Config-driven written books, entirely inside `IekcorbarSiteSync/` (no website/DB involvement): one `.txt` per book in `plugins/IekcorbarSiteSync/books/` (header `key: value` lines, a `===` line, then pages split by `---`; format documented in the example files written on first start), optional `series` + `volume`. `books.yml` holds per-loot-table drop chances, applied through `LootGenerateEvent` (one roll per generated container, then a weighted pick among books with `loot: true`). `/ibook list|info|give|giveseries|open|reload` (permission `iekcorbar.books.admin`). Book items carry their id in the PersistentDataContainer (`book_id`); non-`copyable` books are `TATTERED` generation so vanilla can't copy them. Plugin-made chests (EliteMobs etc.) are not loot tables and are unaffected. Since 1.7.0, reading a book (right-click in hand, lectern, `/ibook open`) is stored per player in the player's own PersistentDataContainer (`books_read`, comma-separated ids â€” vanilla playerdata, no DB), and `AdvancementManager` has `books_read` / `book_read` / `book_series` requirement types on top of it (`/ibook progress|forget` to inspect/reset). Images in books were considered and rejected (would need yet another resource pack). Since 1.14.0 book items get a FreeMinecraftModels display model as their look (`item_model` via `ModelItemAPI.applyDisplayModel`, by reflection): a book's `model:` header line, else `default-model` in books.yml (`none` = vanilla); designs in `fmm-models/steampunk_iekcorbar/books/` (generated by `make_books.py`). Books handed out earlier get the current look the next time they're read. Compiled clean but not yet tested on the live server.

### Creative-only free heads (plugin 1.8.0, `CreativeHeadsManager`)

HeadDatabase heads must always be bought (Vault/CMI money, prices in the server's `plugins/HeadDatabase/config.yml`, edited copy in `headdatabase/config.yml`); only staff in creative get them free. Permissions on the server are managed through CMI, which can't make a permission depend on the gamemode, so the plugin sets HeadDatabase's `headdb.free.*` nodes (each listed separately â€” Bukkit doesn't expand wildcards; override via `creative-heads.free-nodes`) through a `PermissionAttachment`: true in creative for holders of `iekcorbar.heads.creativefree`, explicitly false otherwise (the explicit false is what stops OP'd staff from getting free heads in survival). The HDB node names are not verified against the HDB jar.

### FreeMinecraftModels props in every direction (plugin 1.9.0, `PropPlacementManager`)

FMM always places a prop upright on the clicked face, only turned toward the player. `props.yml` defines "families": one item (the base model, which has the FMM recipe in `plugins/FreeMinecraftModels/recipes/`) plus a model variant per mounting (floor / wall / ceiling, each with an optional sneak variant). The listener picks the variant from the clicked face and spawns it through FMM's `PropEntity.spawnPropEntity` by reflection (no compile dependency). To keep FMM's own listener (also LOWEST) from placing the base model too, the hand item's `freeminecraftmodels:model_id` tag is removed at LOWEST and restored at MONITOR; `plugin.yml` has `loadbefore: [FreeMinecraftModels]` so ours runs first. Protection is checked with a synthetic `BlockPlaceEvent`. Picked-up variants (FMM's `pickupable.lua` drops an item of the variant) are turned back into the base item on `ItemSpawnEvent`. Models live in `fmm-models/` (generated by a Python script: only Â±45Â°/22.5Â° single-axis element rotations, which vanilla item models allow). Not live-tested yet. Since 1.11.0, `/props` (`PropsMenu`, permission `iekcorbar.props.creative`, creative mode only) is a chest menu with every family's base item plus the Steam Wrench â€” the real creative inventory can't be extended (its tabs are client-side). Since 1.12.0 a family can also have `light: <1-15>` (`PropLights`: a vanilla LIGHT block in the lamp's block, remembered in `lights.yml` and removed again when no lamp prop is found there â€” checked 2 ticks after a lamp item drops and every 30 s, only in chunks whose entities are loaded, because FMM has no prop-removed event), `yaw-offset`, and `recipe:` (shape/ingredients/amount, registered by our plugin as `iekcorbar:prop_<base>`, result rebuilt in `PrepareItemCraftEvent` so it always has FMM's item model, auto-discovered on join). Models are in `fmm-models/steampunk_iekcorbar/<item>/` (all generated by a `make_props.py` script, which also writes `props.yml`: 72 items / ~590 models in 4 material themes - copper, brass, iron, verdigris - plus gear sizes, thin pipes, chandelier, street lamp, Japanese paper lantern).

### Steamworks plugin (`Steamworks/`, kinetic machines)

Separate Maven project (own jar, `Steamworks-X.jar` copied to the repo root, independent of IekcorbarSiteSync): Create-like rotation/stress mechanics with our own numbers and models (design + Create comparison in `docs/steamworks-kinetics-design.md`; no Create assets, only its MIT-licensed behaviour was studied). Phase 1 (0.1.0): shaft, cogwheel, large cogwheel, gearbox, hand crank, water wheel, speed/stress gauge, millstone. A part is a BARRIER block plus non-persistent ItemDisplay entities (respawned on chunk load) showing FMM display models from `fmm-models/steamworks/` (generated by `tools/make_models.py`, true scale, axis = model Y); turning is client-side interpolation, a few updates per second. Parts are saved in the chunk PDC (`NodeStorage`). `Kinetics` holds the graph: ratios relative to a root part per network, rebuilt only when parts/sources change; overstress, opposed sources and gear loops **jam** the network (user's choice) instead of breaking parts; only overspeed (> max-rpm) drops the fastest part. Gearbox port signs and large-large cog rules match Create's. `tools/test/KineticsTest.java` checks the maths offline (no server, via the `KineticsHost` interface) â€” run it after changing `Kinetics`. Numbers in `config.yml`, machine recipes in `machines.yml` (`MachineRecipes`: one section per machine; a section missing from an older server copy falls back to the jar's built-in one, since `saveResource` never overwrites the server file). Phase 1 works in-game (user, 2026-10-23). Phase 2 started with 0.2.0: **steam hammer** (`Hammers`: horizontal shaft through it, presses item entities lying in the block below, one item per strike, head = a `HEAD` display role animated with translation; a hopper right below collects results because `InventoryPickupItemEvent` is cancelled for items the running hammer can still press) and **Steamworks materials** (`MaterialType`: iron/copper/golden plate, PAPER items with PDC `steamworks:material`, `sw:<id>` in machines.yml, blocked from vanilla recipes like parts), plus **clutch** and **gearshift** (split shafts that poll redstone every 2 ticks via `isBlockIndirectlyPowered`; `Kinetics.setPowered`: a powered clutch has no ports, a powered gearshift has port factor -1 on its negative face; casing model swapped to `_casing_on`, far half = display role `SPIN_B`). Recipe paper slots are now validated cell by cell (`PartItems.Spec`, shifted/mirrored placements), so a recipe can mix parts and plates â€” no `ExactChoice`, because FMM's item model may not be ready when recipes register. Also **bellows fan** (`Fans`: shaft from behind, blows out of `facing`, reach = speed/4 clamped 3-12 blocks until a non-passable block; pushes items/mobs; the first lava/fire/campfire/water/soul fire in the stream makes items past it smelt/smoke (vanilla furnace/smoker recipes looked up via `recipeIterator`) or wash/haunt (`fan_washing`/`fan_haunting` in machines.yml), whole stack after `process-ticks`, progress kept in the item entity's PDC), and **kettle + mixer** (`Mixers`: kettle is a non-kinetic part (no ports) holding 9 ingredient stacks + water in quarter buckets + 9 result stacks, saved in the chunk PDC like the millstone, heated by fire/campfire/lava/magma below; mixer above it (port UP only) runs the first `mixer:` recipe in machines.yml whose inputs are all present; whisk display lowered via `KNode.whiskDrop` translation; new materials brass ingot/plate). Phase 2 complete. **Trains** (also in 0.2.0, user chose an own implementation over TrainCarts): no minecarts at all â€” `trains/RailWalker` follows vanilla rail shapes itself (straight/slope = line between edge middles, curve = quarter circle r 0.5, crosses blocks incl. up/down slopes; `tools/test/RailTest.java` checks it offline), every car has a front + rear walker (wheelbase 1.2, gap 0.8, all moved the same distance so spacing holds). `Trains`: cars are an ItemDisplay model (teleport-duration interpolation, model lifted 0.75 because rail level is -12 px), empty ItemDisplay seats players ride (teleported with `RETAIN_PASSENGERS`) and an Interaction hitbox; driver = player in the locomotive seat, steered by `Player#getCurrentInput()` (W steam, S brake/reverse, jump whistle); coal etc. as fuel; cars couple by placing on the rail right behind/in front of a train; middle car removed = train splits; moving trains with a driver hold plugin chunk tickets; trains persist in `plugins/Steamworks/trains.yml` (entities are non-persistent and respawn when the chunk is loaded). **Stations + signals** are non-kinetic parts (no ports) next to the rails (same height or one lower); `Trains.indexRails` maps the rail blocks beside them every second, `buildOccupied` maps which rail blocks hold which train every tick. Station = name board (name from an anvil-renamed item or a name tag, saved in the chunk PDC under `mill_*` key `label`, shown with two TextDisplays). Signal = red when redstone-powered or when the track after it, up to the next signal (max `signal-section-max-blocks`), holds another train (`sectionTaken`); lamp model swapped `sw_signal_red/green`. **Autopilot** (right-click a locomotive with a clock): `scan()` walks the track ahead (braking distance + 4) and stops for stations (waits `station-wait-seconds`, then ignores that station for 4 blocks), red signals, other trains and the end of the track (waits, then turns round = shuttle lines). Every train refuses to move into another train (`drive`); drivers get "RED SIGNAL AHEAD"/"TRAIN AHEAD" on the dashboard; riders get "Next stop" / arrival titles. Not yet live-tested. **Custom ores** (`Ores` + `OreType`): zinc, tin, sulfur (stone + deepslate variants, sulfur also in the nether) are note blocks in reserved states â€” instrument `custom_head`, `powered=true`, `note=OreType.noteId` (0-6) â€” that our own resource pack `Steamworks/resourcepack/Steamworks-blocks.zip` (generated by `tools/make_blocks_pack.py`: multipart `note_block.json` that leaves every other state on the vanilla model, own textures, item model definitions for `item_model steamworks:<ore>`) gives ore textures. That zip goes into `plugins/ResourcePackManager/mixer/`; it can never be combined with CreatePaper's pack (both replace `note_block.json`; CreatePaper is not used, Steamworks replaced it). `Ores` cancels physics/note play/tuning on ore states, scales the player's `BLOCK_BREAK_SPEED` attribute while digging one so it mines like a real ore (pickaxe tier, hardness, Efficiency), handles Fortune/Silk Touch/explosions, and adds veins the first time each chunk loads (chunk PDC marker `ores_v1`, also existing chunks unless `ores.generate-in-existing-chunks: false`). Paper's `block-updates.disable-noteblock-updates: true` (paper-global.yml) is recommended on top. Raw zinc/tin smelt via exact-match furnace/blast recipes; brass is now copper + zinc in the mixer; sulfur + coal/charcoal = gunpowder. **Oil** (`Oil`, `FluidType`): hidden oil fields per overworld chunk derived from the world seed (`initialOil`, more in desert/badlands/ocean), what is left after pumping stored in the chunk PDC (`oil_left`); pumpjack (kinetic, horizontal shaft like the hammer, walking beam = display role `BEAM` rocked with `rotateX`) pushes crude oil each second; pipes/tanks/refinery are non-kinetic parts; fluids are numbers in tanks (`KNode.fluid/fluidAmount`, saved in the chunk PDC), moved by BFS over pipes (`tanks()`/`push()`/`pull()`), pipe arms are `ARM` displays rebuilt on neighbour changes (`refreshAround`), tank level = scaled `LIQUID` column per fluid; refinery (heat below) makes 60% fuel / 40% chemical waste and stops when fuel has no room, but waste without room is dumped as chunk **pollution** (chunk PDC `pollution`, decays per 5 s; >300 grass/leaves die, >600 poison; a broken tank with waste spills too). Tin canisters carry 1000 mB (fuel canister refuels a locomotive), waste canisters are neutralized in a heated kettle; Oil Dowsing Rod shows oil here + direction of the nearest field. **Phase 3** so far: drill + saw (`Breakers`: shaft from behind, break the block in `facing`, time = (hardness+0.2) x factor / speed, iron pickaxe/axe drops, saw fells connected logs; machines store their placer in `KNode.owner` (7th field of the node line) and only break where that player passes a `ProbeBreakEvent`, answers cached 10 min for when the owner is offline), conveyor belt (`Belts`: full block, surface on top; belt pieces in a line with the same roller axis are linked with factor 1 in `Kinetics.links`; surface direction = positive axis x up times the sign of the speed, so gearshifts reverse it; carries items/mobs/players, delivers into a container/kettle at the end, holds items under a running hammer two blocks up). **Karakuri Arm** (`Arms` + `ArmProgram`): kinetic (port DOWN), swappable tool heads (gripper / placer / drill head = `MaterialType.ARM_*`), up to 8 taught points (sneak + right-click the arm with an empty hand, then click blocks within `parts.arm.range`), programs in a small line language compiled by `ArmProgram` (PICK/DROP/PLACE/USE/BREAK/WAIT/LEVER/LOOP/REPEAT/IF ... END; spec in its javadoc; `tools/test/ArmProgramTest.java`), loaded from a book and quill / written book, copied with punch cards (program in the card's PDC); without a program it runs `ArmProgram.fallback` for its tool. Points/program/tool/held item saved in the chunk PDC. Turret displays turn with `rotateY` (`TURRET` role), the held item is a `HELD` display. Messages and `/sw help` link to `docs-url` (default https://iekcorbar.com/steamworks) â€” that website page still has to be built. 0.2.0 is live and reported working (2026-10-24). 0.3.0 adds the **chain drive** (`Chains`: Chain Sprocket = shaft-like part; two sprockets on the same axis, in one plane, 2-12 blocks apart, linked with iron/copper chains (one per block); `KNode.chain` = offset to the other end, stored both ways, link factor 1 in `Kinetics.links`, `Kinetics.relink` on change; chain drawn as two stretched `sw_chain_span` displays by the lower-key end). Wiki texts live in `docs/wiki-steamworks/` â†’ `python -I scripts/build-wiki-seed.py` â†’ deploy â†’ staff "Refresh Steamworks pages" on /wiki. Also in 0.3.0: phase 4 (windmill with 3.5x-scaled sails via `DisplayPart.scale`, steam boiler + engine, crushing wheel pairs with `crusher:` recipes and `ore:` inputs, flour + baking) and **decorative building blocks**: `DecorType` (32 blocks, 4 styles) = reserved note-block states (custom_head; key 0-24 powered+note, 25-49 unpowered+note; 0-6 ores, 7-38 decor; `Decor.key()`), textures drawn in `tools/decor_textures.py` (ids/keys must match the enum), `Decor` handles place/break/dig speed; **stairs/slabs** (`DecorShapes`) are a real vanilla stair/slab (stone brick or oak) covered by an ItemDisplay of our model at 1.004x that follows the vanilla state (vanilla stair blockstate rotation table), positions in chunk PDC `decor_shapes`. User rule: never change the look of existing vanilla blocks â€” only add. 0.3.1: FMM loads its models after server start, so `PartItems.waitForModels` re-checks every 5 s (max 1 min) before spawning displays and only then warns (one line listing truly missing models); craft results get their model re-applied in `PrepareItemCraftEvent` (`refreshModel`), since recipes register before FMM is ready.

### Kitchen plugin (`Kitchen/`, farming + cooking)

Separate Maven project (`Kitchen-X.jar` copied to the repo root), Japanese take on the MIT-licensed Farmer's Delight mod (behaviour only; every item, model and texture is our own, generated by `Kitchen/tools/make_pack.py` into `Kitchen/resourcepack/Kitchen-pack.zip`, which goes into `plugins/ResourcePackManager/mixer/` next to Steamworks-blocks.zip; `tools/preview3d.py` renders model sheets). No FreeMinecraftModels dependency: every item is a vanilla item with `item_model kitchen:<id>` and id in PDC `kitchen:item` (`KItem`, `Items`); foods use Paper's FOOD/CONSUMABLE/USE_REMAINDER components, extra effects in `Items.applyEffects`. **Crops** (`CropType`, `Crops`, since 0.3.0): ten crops - rice, soybean, napa cabbage, green onion, daikon, tea, corn, tomato, chili, sesame. A crop is a **structure void** above farmland or Rich Soil (players can never place or break it directly; vanilla has no state logic for it, which is exactly why the old tripwire encoding kept corrupting crops - they "changed" when harvested, and it also turned cut-off trap strings into fake crops). Type + stage + wild live in chunk PDC `crops` as one int per crop (pos bits 0-16 | ordinal 17-24 | stage 25-27 | wild 28) and the visible plant is a non-persistent ItemDisplay (`kitchen:block/<id>_stage<N>` / `_wild`), like the props. Old tripwire crops migrate on chunk load. Growth rolls once a second for known crops only; **right-click the soil under a crop** = harvest (+regrow) or bone meal; corn cuts to cornmeal; wild patches on first chunk load (`wild_v1`). Staff commands `/kitchen give|take <player> <item> [amount]`. **Props/stations** (`PropType`, `Prop`, `Props`): hidden BARRIER (or WHITE_CARPET for tatami/zabuton) + ItemDisplay (`kitchen:<id>`, front = north), saved as text lines in chunk PDC `props`: cutting board (knife cuts, `cutting:` in kitchen.yml), cooking pot (27-slot GUI: 6 ingredient slots, container, progress clock, result; heat below; `cooking:` recipes, exact ingredient cover via `Recipes.cook`), kamado stove (flint & steel, heats pot, grills vanilla campfire recipes 2x speed), tansu cabinet, bamboo basket (catches items), 9 crates, straw bale, organic compost -> rich soil (faster crops), tatami, zabuton (sit). **Shared meals** (`MealType`, `Meals`): sneak + right-click to put a sushi platter / ramen / bento / dango plate / tea set down; Interaction hitbox; each bite animates chopsticks (teleport-duration interpolation) and removes one piece display; chunk PDC `meals`. Item specs in kitchen.yml: `kitchen:<id>`, `sw:<id>` (Steamworks material by PDC, no dependency), `#tag`, vanilla name, `a|b`. **Steamworks bridge**: Kitchen registers a `BiFunction<String,Integer,ItemStack>` service; Steamworks (`KitchenBridge`, softdepend Kitchen so Kitchen loads first) accepts `kitchen:<id>` inputs/outputs in machines.yml (millstone rice -> flour, steam hammer steamed rice -> mochi, mixer tofu/steamed rice). Docs: `docs/wiki-kitchen/` -> `python -I scripts/build-wiki-seed.py` (now builds every `docs/wiki-*` folder into `src/lib/wiki-seeds/<name>.json`) -> staff "Import/Refresh Kitchen pages" on /wiki; `docs-url` = /wiki?page=kitchen. 0.3.0 built 2026-10-26 (crop rebuild), not live-tested.

### Wildlife plugin (`Wildlife/`, new animals)

Separate Maven project (`Wildlife-X.jar` at the repo root), inspired by the MIT-licensed Naturalist mod (behaviour only; own models from `Wildlife/tools/make_animals.py` -> `Wildlife/fmm-models/wildlife/` -> server `plugins/FreeMinecraftModels/models/wildlife/`; `tools/preview3d.py` renders sheets, `PREVIEW_SPAN=40` for a shared scale). User rules: animals are **added** next to vanilla mobs, never replacing them (no vanilla spawn is touched); spawning per biome and **scarce**; a naturalist "collect them all" (observe + journal, explicitly *not* Pokemon-like: no catching/battling); real scale (1 block = 1 m, insects ~2x); every find is a real advancement in a **Wildlife** tab. `Species`: 20 species, each an invisible vanilla base mob (horse, polar bear, goat, chicken, bee, armadillo, pig, turtle, ocelot, parrot, cod) scaled with the SCALE attribute, plus an FMM model via `DynamicEntity.createWithInvisibility` (`Fmm`, reflection; FMM plays idle/walk/death, the plugin plays curl/hide/sleep/eat); each species has a rare colour variant (`wl_<id>_rare`, chance `spawning.rare-chance`). `Animals`: registry from entity PDC (`wildlife:species/rare/model`), base-mob uses blocked (breeding, taming, eggs/scutes/horns, transforms, hives, bee stings), behaviours in one tick (deer/lizard flee, hedgehog/snail curl, tortoise hides, bear sleeps at night + eats berries, fireflies glow at night and vanish by day, capybaras give rides), own drops/sounds; base mobs `setRemoveWhenFarAway(false)` so they don't count toward the vanilla mob cap, and unnamed animals are deleted when their chunk unloads (name tag keeps one). `Spawner`: one try per player per interval, biome + time + habitat checks, local caps. `Journal`: watching an animal ~1 s through a spyglass records it in player PDC (`wildlife:seen`, `seen_rare`), book via `/wildlife journal` or the crafted Field Journal; advancements registered with `Bukkit.getUnsafe().loadAdvancement` (like IekcorbarSiteSync), re-registered after `/minecraft:reload`, re-synced on join. Wiki: `docs/wiki-wildlife/` (staff "Import Wildlife pages"). 0.1.0 built 2026-10-24, not live-tested.

### Relics plugin (`Relics/`, lore props)

Separate Maven project (`Relics-X.jar` at the repo root): placeable props from the server lore. Collection 1 "Shoutlander's Shadow" (Halloween 2026, `RelicType`, 13 relics) uses only the **known history** from the Story Room lore (`Lore Library/Others/Base.docx`: Shoutlander devastated the world, his creatures remain among the ruins, the First Settlement was destroyed, survivors fled to the Guild) - nothing reserved for later reveals (e.g. "Shoutlander has noticed"). Kitchen-style props (`Relics`): base SOLID = barrier (1 or 2 blocks tall), LIGHT = light block (flickers), NONE = air + Interaction hitbox; floor/wall/ceiling model variants; ItemDisplay `relics:<model>` from `Relics/tools/make_pack.py` -> `Relics/resourcepack/Relics-pack.zip` (RPM mixer); chunk PDC `relics` saved on every change; one 10-tick effect loop for relics with a player within 24 (skull eye glow, flicker, sway, grave whispers at night, monolith glyph pulse, egg-sac heartbeat pulse, warding totem lights up when monsters are within 16, portal shimmer). Sounds come from `sounds/make_sounds.py` (namespace `relics`). Shapeless recipes from vanilla items; `/relics list|give`. Collection 2 **"Xmas Town"** (for the Xmas Town region; user style choice: cosy village + steampunk + Japanese) adds 16 props with `RelicType.Use` right-click actions (sleigh seat, gift box wrap/unwrap/open with owner UUID + serialized item in the relic line, advent calendar doors 1-25 Dec from `advent-calendar.rewards` in config with a per-player PDC bitmask `advent_<year>`, music box playing three public-domain carols synthesised in make_sounds.py, snow globe, refillable cookie plate) and effects (TWINKLE model swap `_b`, ADVENT wreath candles follow the real Advent Sundays, TRAIN rotation, HEARTH, SNOW, STEAM); the 3-block tree is built at 2/3 size and drawn with `RelicType.scale` 1.5. `Treats`: hot chocolate, mulled cider, gingerbread cookie, stollen, candy cane (food components). Collection 3 **"New Year"**: firework launcher (right-click show of vanilla rockets), countdown clock (right-click time left; `newYearTick()` once a second shows a 10-second title countdown to players within 48 of a clock and, when the year changes, makes clocks strike twelve, fires every launcher within 64 of a clock and titles everyone "Happy New Year!"; server local time), temple bell (108 strikes per year, count + year kept in the relic line), kagami mochi, shimenawa, red paper lantern, confetti cannon, sparkling cider tower (refillable), twinkling garland; treats sparkling cider, oliebol, toshikoshi soba and a sparkler item (10 s of sparks). Collections 4-6: **"Easter"** (easter egg = egg hunt: placed in a random colour, each player finds each egg once (player PDC `eggs_found`, the hider can't), reward from `easter.egg-rewards`; basket, hopping clockwork bunny (HOP), blossom vase, egg tree; treats chocolate bunny, hot cross bun), **"St. Patrick's Day"** (pot of gold once a day per player from `st-patricks.pot-of-gold`, PDC `gold_day`, RAINBOW particles; rainbow arch, shamrock planter, leprechaun hat, celtic harp, green ale keg; treats green ginger ale, soda bread, shamrock cookie) and **"Curiosities"** (all-year steampunk + Japanese: brass telescope, steam gauge panel + maneki-neko (SWAP model toggle), tesla coil (SPARK: flickering light + sparks + zap), gramophone playing Sakura Sakura, torii gate (2/3 size, scale 1.5, 3-wide low hitbox), shishi-odoshi (SHISHI tip + clack every 6 s), bonsai, byobu screen, wagasa stand). 0.1.0 built 2026-10-24, not live-tested.

### Kura plugin (`Kura/`, storage network)

Separate Maven project (`Kura-X.jar` at the repo root), our take on the MIT-licensed Tom's Simple Storage mod (behaviour only; own code, models from `Kura/tools/make_pack.py` -> `Kura/resourcepack/Kura-pack.zip` for the RPM mixer; `tools/preview3d.py` renders sheets). Items are PAPER with `item_model kura:<id>` + PDC `kura:item` (`KItem`, `Items`; kept out of vanilla recipes in `Parts.onCraft`). **Parts** (`PartType`, `Part`, `Parts`): BARRIER + one ItemDisplay (cable = 64 pre-made `cable_<mask>` models, bit = index in `Networks.FACES`), saved as text lines in chunk PDC `parts`; the Inventory Interface and Open Crate are real vanilla BARRELs (tagged `kura:part` in the barrel PDC, covered by our model) so hoppers and Steamworks belts/arms use them; the Level Emitter swaps its base between BARRIER and REDSTONE_BLOCK to give its signal. **Networks** (`Networks`, `Network`, `Store`): BFS from an Inventory Connector over storage containers (config `storage-blocks`; containers with a key in `excluded-container-keys`, e.g. `sakagura:cask`, and our own barrels are never storage), trims (conduct like a chest), cables (only to cables/connectors/cable connectors) and Cable Connectors (attach the container they face); `links()` holds the rules. Every member block is indexed; place/break/explode of a storage block or part marks the networks around it dirty (rebuilt lazily on next use), chunk load/unload dirties networks spanning that chunk - no timed world scans. Contents are always read live from the stores; insert = stores already holding the item first, then by priority; extract = lowest priority first. Per-container settings (priority, filter, skip, keep-last) live in the container's own PDC (`kura:cfg`, `kura:cfg_filter`), set with the Inventory Configurator. **Menus** (`Menus`, `TerminalMenu`): terminal = 54-slot chest GUI (amount in lore, search via an anvil `MenuType.ANVIL` view: words / `#tag` / `@namespace`, sort amount/name/plugin, pages, deposit-inventory button); Crafting Terminal = grid + result computed with `Bukkit.craftItemResult` (so other plugins' PrepareItemCraftEvent handlers still apply), refills used slots from the network after each craft, grid goes back to the network on close. Settings GUIs for configurator/hopper/emitter/interface, editors for Item/Tag filters (data in the filter item's PDC). Wireless Terminal (ray trace 16 blocks) and Advanced Wireless Terminal (bound; 64 blocks, beacon tier 1-3 = same world, 4 = any world; remote networks hold counted plugin chunk tickets while open, max `wireless.max-loaded-chunks`). Filing Cabinet = one-kind multi-variant store (256 items, contents kept in the dropped item). **Recipe viewer hooks** (for the JEI-style plugin, looked up by plugin name, JDK types only): Kura registers `BiPredicate<Player, List<List<ItemStack>>>` (fill the open Crafting Terminal's grid from the network) and `Predicate<Player>` (reopen the terminal); the terminal shows a Recipes button when a `BiConsumer<Player,String>` from a plugin named "Zukan" is registered. Wiki: `docs/wiki-kura/` (staff "Import Kura Storage pages"). 0.1.0 built 2026-10-24, not live-tested.

### Zukan plugin (`Zukan/`, recipe browser)

Separate Maven project (`Zukan-X.jar` at the repo root), our JEI replacement (behaviour of the MIT-licensed JEI mod only; own code, no resource pack: a 54-slot chest GUI). `/zukan [words]|hand|uses|reload` (aliases recipes/jei), craftable Zukan book (book + compass, PDC `zukan:book`), permissions `zukan.use` (default true), `zukan.cheat` (op: click an item to take it), `zukan.admin`. **Catalog** (`Catalog`): every item Material plus vanilla recipes from `Bukkit.recipeIterator()` (crafting incl. transmute, smelting/blasting/smoking/campfire, stonecutting, smithing) and hardcoded brewing, fuel and composting; built 2 s after enable and again after 90 s (FMM item models are ready by then), and on `/zukan reload`. Items are keyed by material + item model + potion type (`keyOf`), so custom items of our plugins stay apart. **Providers**: a plugin registers a `Supplier<Map<String,Object>>` service (JDK types only, no shared jar); Zukan only asks plugins named in config `providers`. The map has `items` (List of ItemStack), `recipes` (List of Map: category, icon, station, inputs = List of cells each a List of ItemStack (stack amount is kept), outputs, info = List of String, grid, shapeless) and optionally `replaceCrafting: true` (Steamworks: its Bukkit crafting recipes use generic paper/note-block slots, so Zukan skips shaped/shapeless/transmute recipes in that plugin namespace and shows the provider ones instead). Each of Steamworks, Kitchen, Sakagura, Relics, Kura and Wildlife has a `ZukanData` class: Steamworks (all parts/materials/cars/ores/decor + `PartItems.craftRecipes` real ingredients + millstone, steam hammer, crusher, fan washing/haunting, kettle+mixer), Kitchen (items, cutting board, cooking pot), Sakagura (garden crops, casks, every drink as a top-quality bottle with its steps), Relics/Kura/Wildlife (items only; their workbench recipes are normal Bukkit recipes with exact choices). **Gui** (`Gui`): item list (search via anvil, `#tag`, `@namespace`, namespace switch, bookmarks in player PDC, cheat mode), recipe/uses view with category tabs, recipe paging, history, ingredient slots with several options cycle every second. **Kura link**: Zukan registers a `BiConsumer<Player,String>` service that Kura calls for its Recipes button, and uses Kura's `BiPredicate` (fill the crafting grid) and `Predicate<Player>` (reopen the terminal) services. Wiki: `docs/wiki-zukan/` (staff "Zukan" import button). 0.1.0 built 2026-10-24, not live-tested.

### Sounds (`sounds/make_sounds.py`)

Own synthesised sound effects (pure-Python oscillators/noise/filters -> WAV -> ffmpeg libvorbis) for the namespaces wildlife, steamworks, kitchen and relics, written to `sounds/out/<ns>/` (sounds.json, .ogg, en_us subtitles); each pack builder copies its namespace in (`add_sounds`). ffmpeg is installed via winget but not on the shell PATH here: pass `%LOCALAPPDATA%/Microsoft/WinGet/Packages/Gyan.FFmpeg_*/ffmpeg-*/bin/ffmpeg.exe` as the argument. The RNG is seeded and catalogue order fixed, so append new events at the end to keep existing sounds identical.

### Sakagura plugin (`Sakagura/`, brewing + garden)

Separate Maven project (`Sakagura-X.jar` at the repo root), replaces the old Brewery plugin (user choice; named Sakagura, command `/brew`, so it never reads the old `plugins/Brewery` folder). One plugin for brewing and the fruit garden (user asked for both in one). Behaviour modelled on TheBrewingProject/Garden (configs only were read; own code). **Recipes** (`Recipe`, `Recipes`, `recipes.yml` generated by `tools/make_recipes.py` from `tools/site-recipes.json` = a snapshot of the old site brewing page (the live `/api/brewing/recipes` it mirrored is gone), plus 19 own Japanese/steampunk drinks (since 27-10 including the two promoted staff drinks); the same script writes `docs/wiki-sakagura/03-recipes.md`): steps cook/mix/distill/age, ingredient keys `mc:`, `kitchen:`, `sakagura:`, `sw:`, `brew:<slug>` (drink names in ingredients resolve to recipes). `Brew` = the bottle state in PDC `sakagura:brew` (ingredients, cook/mix minutes, distill runs, years + wood, step sequence, cask "since"); `Recipes.match` scores ingredients 40% / time 25% / distills 15% / age+wood 20% (stricter for higher difficulty) -> quality 1-10 (stars), poor/normal/good names, `+`/`++`/`+++` lore and effects, harmful effects milder at higher quality; `tools/test/RecipeTest.java` checks every recipe offline (perfect -> itself q>=9, sloppy worse, half-done unfinished). `Cauldrons`: water cauldron + heat = cook, no heat = mix, real minutes while loaded, glass bottle draws (3 per cauldron), chunk PDC. `Distilling`: a **Steam Still** (`Stills`, since 0.2.8) â€” a real vanilla BARREL tagged `sakagura:still` (so hoppers and protection plugins just see a container), covered by a cask-style ItemDisplay (`sakagura:still` model), customName "Steam Still": bottles + glowstone dust inside, heat from below (same blocks `Cauldrons.heated` accepts), every `distilling.seconds-per-run` (default 20 s) one glowstone is spent and up to `distilling.bottles-per-run` (3) bottle stacks get `distills++` + step 'D'; right-click = status action bar; shapeless craft (barrel + 2 iron + blaze powder + glowstone); `distilling.debug` logs what each still sees. (The 0.2.7 design â€” Paper `PotionMix` brewing-stand run â€” never worked on the live server and is fully removed; Brew/scoring/recipes.yml never changed.) `Casks`: crafted cask = vanilla BARREL tagged with its wood in the barrel PDC and covered by an ItemDisplay cask model (brightness taken from the block above); real-time ageing via "since" on insert (InventoryClose/MoveItem) and settling on open/leave (`aging.minutes-per-year`, default 20). `Drunk`: Brewery-style (stumble, nausea, slurred chat, puke, pass out -> respawn point + hangover), level in player PDC. `Garden` + `Fruit`: 16 crops (since 0.3.0 also apple, pear, apricot, mikan, kiwi), 3 stages, same structure-void + chunk-PDC + ItemDisplay architecture as Kitchen (`sakagura:block/<id>_stage<N>` models; entry bits: pos 0-16 | ordinal 17-24 | stage 25-27; the 30+ tripwire states are gone, old plants migrate on chunk load) - `tools/make_pack.py` no longer writes a tripwire-extra.json and Kitchen's tripwire.json override is deleted too, so neither plugin touches real string again. Wiki: `docs/wiki-sakagura/`. 0.1.0 built 2026-10-24, not live-tested.

### Brewing mini-game (`/brewing`) - REMOVED 2026-10-25

The website brewing mini-game (page, `src/app/api/brewing/**` routes, Ingredient Hunt, tipsy
effect, `src/lib/ingredient-hunt.js`/`tipsy-effect.js`/`brewing-seeds/`/`brewing-visuals.js`,
member-profile Brewing tab, shop "Glass of Milk" cures, FIRST_BREW web achievement, the matching
`globals.css` animations, nav entries and the "Brewing Minigame" bug category) was retired once
the in-game **Sakagura** plugin + the `/sakagura` guide page + wiki cover everything it tried to
simulate. `/brewing` is now a permanent redirect to `/sakagura` in `next.config.mjs`.
Deliberately kept: the `brewing_*` methods in `db.js` and the MySQL tables
(`brewing_recipes`/`brewing_sessions`/`brewing_blueprints` + hunt columns) as dead code the owner
can drop later, the `case 'brewing'` notification icon and push label (old rows exist in
`notifications`), the "Brewery" seed-category labels in moderation/wiki (DB categories),
and historical comments. `Sakagura/tools/site-recipes.json` remains the offline recipe source of
`make_recipes.py` (its "keep in sync with iekcorbar.com/brewing" comments are historical now).

### Styling

Tailwind v4 (CSS-first config, no `tailwind.config.js` â€” theme tokens are declared via `@theme` in `src/app/globals.css`). Custom design tokens follow an `sp-*` (steampunk: gold/copper/bronze/dark/charcoal) and `sakura-*` (pink/accent/dark) naming convention, plus hand-rolled keyframe animations (falling sakura petals, spinning gears, steam rise) also defined in that `@theme` block. Follow these token names rather than introducing new ad-hoc colors.

### Markdown rendering

Wiki pages, updates, and guide content use a hand-rolled markdown renderer (`src/lib/markdown.js`, `renderMarkdown()`) rather than a library â€” it supports `##`/`###`/`####` headers, `**bold**`, `[text](url)` links, `* `/`- ` lists, `> ` blockquotes, `![alt](src)` images, and `|`-pipe tables, and returns React elements directly. Extend this file (not a markdown library) if new syntax is needed.

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes â€” APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` â€” verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

### Recipes page (`/recipes`)

Static recipe browser outside the wiki (nav: Play > Recipes), with a "Plugin recipes" tab (plugin tabs, type filter, search) and a "Zukan guide" tab. Data is `src/lib/recipes-data.json`, generated by `node scripts/build-recipes-data.js` from `Steamworks/.../machines.yml`, `Kitchen/.../kitchen.yml` and the `shaped(...)`/`shapeless(...)`/`r.put(...)`/`addRecipe(...)` lines of the plugins' Java sources (Steamworks `PartItems`, Kitchen `Items`, Kura `Items`) via js-yaml - no server needed. Rerun it after changing plugin recipes. Relics/Wildlife/Sakagura are not included (Sakagura drinks link to /brewing).

Relics 0.1.2 (2026-10-24): every relic can be placed on any face. A type without its own wall/ceiling model shows its floor model tilted onto the wall (bottom against the wall, `Relics.rotation`/`build`, tall ones shrunk to one block via `fit`) or flipped onto the ceiling; types with `wall`/`ceiling` models keep using them. The Advent Wreath has `advent_wreath_wall_<n>` models (hangs flat on the wall, candles stick out). New **Giant Candy Cane** (2 blocks, `Use.CANE`, models `candy_cane_giant[_green|_blue|_pink|_gold]`, random colour on place, right-click with a dye repaints it).

Plugin-wide `giveall` (2026-10-24): `/kura|steamworks|kitchen|relics|brew|wildlife giveall [player]` (permission = the plugin's `*.admin`) gives one of every item of that plugin, taken from the plugin's `ZukanData` list; more items than free slots go into named shulker boxes (`GiveAll` helper class per plugin). The Field Journal is sold by wandering traders through the WanderingTrades plugin: `wanderingtrades/trades/wildlife.yml` (30%, 12 emeralds + spyglass, maxUses 1) hands out a plain BOOK named "Field Journal" whose last lore line is "Wildlife"; Wildlife 0.1.6 (`WildItems.fromTrader`) swaps it for the real journal (PDC + FMM model) on first right-click, since WT trade files cannot set PDC.

Wildlife 0.1.7 `Brain` (2026-10-24): every ground animal except the dangerous species (`Brain.DANGEROUS` = bear, boar, which keep their vanilla AI) and the flyers gets one pig-like brain through Paper `MobGoals`: all vanilla goals and targets of the base mob are removed except FLOAT/LOOK_AT_PLAYER/RANDOM_LOOK_AROUND, then a `Wander` goal (random reachable spot, ducks/capybaras prefer water surface 75%, per-species radius and idle time) and a `Panic` goal (after damage, or when a Monster is within 7 blocks; runs away from the threat at 1.8x) are added. Applied in `Animals.register` (goals are not persisted). Cause of the ducks that only waddled: the base chicken AI avoids water and the very low speed attribute; not verified in-game.

Wildlife 0.3.0 (2026-10-24): **elephant** (COW base, scale 2.1, savanna herds) and **zebra** (HORSE base), **babies** of every species, **breeding**, **spawn eggs**, **statues**. A baby is a normal animal with `Animal.baby` (entity PDC `wildlife:baby`, `grow_at` = world full time, `frozen_left` while a golden dandelion keeps it small): SCALE attribute x0.5, model `<adult model>_baby` (made by `make_animals.py`: half size, head 1.35x about the neck pivot, all `_rare`/songbird looks too), harmless even for bear/boar (`Brain.dangerous(a)` = adult bear/boar only), no drops. `Breeding` (custom, the base mobs' own breeding stays cancelled, so it also works for parrot/bees): right-click an adult with `Species.isFood` -> love mode 30 s (plugin tick), two lovers of one kind within 8 blocks walk together and `Animals.spawn(..., baby=true)` appears; cooldown `breeding.cooldown-minutes`, cap `breeding.max-near` (32 blocks); a baby grows up after `breeding.grow-minutes` via `Animals.grow` (new adult entity with the same name/kept mark; a lead drops); GOLDEN_DANDELION on a baby toggles frozen (costs one each time, marks it kept). `Animals.kept(en)` = named OR leashed OR PDC `wildlife:kept` (set on leash/freeze/bred from kept parents), so those survive chunk unload. Only adult bear + boar ever target/hurt (`onTarget`/`onHarm` cancel the rest); boar keeps its goat horns (a hornless goat does not ram) and speed 0.18. **Spawn eggs**: PAPER with PDC `wildlife:item = egg:<id>`, item model `zoo:egg_<id>`, right-click block spawns, sneak = baby; `/wildlife egg <species> [player]`, `/wildlife spawn <species> [rare] [baby]`. **Statues**: 3 props per species (`animal_<id>`, `_rare`, `_baby`) in the Zoo prop engine, item models `zoo:stat_*` generated by `tools/make_zoo_pack.py` from the same cubes as the animal models (rest pose, head north; drawn smaller to fit vanilla's -16..32 px and scaled back up: `src/main/resources/statues.csv` = id, display scale, hitbox w, hitbox h), recipe shapeless stones + the species' icon item. New sounds `wildlife:elephant.*`/`zebra.*` live in `sounds/make_sounds.py` `EXTRA` (generated after all other namespaces so existing sounds keep their random stream). New animal models must be copied to the server's `plugins/FreeMinecraftModels/models/wildlife/`. Not live-tested.

Zukan 0.2.0 steampunk GUI (2026-10-24): a server cannot extend the E-inventory screen (client-only, no Paper event), so the browser stays a 54-slot container but is drawn as a full-screen picture: `Zukan/tools/make_pack.py` (pure Python) writes `Zukan/resourcepack/Zukan-pack.zip` = font `zukan:gui` with two bitmap glyphs (\ue000 list, \ue001 recipe, 176x222 art at 2x) plus a space glyph (\uf800 = -8 px); `Gui.art()` puts that glyph in the window title (shadow off) and, with `gui-style: steampunk`, leaves free slots empty instead of grey panes (slot positions in the art match `Gui` GRID/FREE_IN/FREE_OUT/bottom bar - change both together). `gui-style: plain` = old look without any pack. `swap-key: true`: F with the Zukan book in a hand opens it. The pack is merged into Iekcorbar-pack.zip (`scripts/merge-packs.py`). Not live-tested; glyph alignment (ascent 13, -8 shift) is from the usual title-texture technique and may need a 1 px tweak.

### Clean server stop (2026-10-24)
Plugin `onDisable`s skip work that vanishes with the server when `Bukkit.isStopping()` (display removal, recipe unregistering; Steamworks 0.3.6, Kitchen 0.1.4, Relics 0.1.5, Kura 0.1.4, Sakagura 0.1.3), and every `Bukkit.removeRecipe` uses `(key, false)` so the recipe book isn't resent to every player per recipe. `MySQLSync.stop()` (IekcorbarSiteSync 1.14.1) closes the connection on a daemon thread so a still-connecting async sync can't block the main thread. Cause of Discord "server hasn't ticked" on restart; not yet verified in-game.

Steamworks pages reshuffle (2026-10-25): the plain-language beginner guide (`how-it-works/page.js`, written same day) became **the** `/steamworks` page - it teaches what to do with rotation, gears, stress, power sources, machines, trains, ores and oil for players who do **not** know the Create mod (explicit "Heard the word Create?" box; uses the 96 renders from `public/recipes/steamworks/`, all ids verified). The Karakuri Arm programming guide (with the in-browser checker from `src/lib/karakuri.js`) moved to `/steamworks/arm`; `next.config.mjs` permanently redirects the old `/steamworks/how-it-works` URL. Wiki link fix: docs `01`/`04` + seed `src/lib/wiki-seeds/steamworks.json` now point the arm link at `/steamworks/arm` (the live wiki DB keeps the old target until staff re-import - harmless, it lands on the hub page which links the arm guide). Nav item "Steamworks Guide" still points at `/steamworks` and now opens the beginner guide; in-game `/sw help`'s docs-url (`https://iekcorbar.com/steamworks`, Settings default) likewise - the arm-specific lore line "Programming guide... (/sw help)" now reaches the arm guide one click deeper. Wiki text otherwise unchanged, per user. `npm run build` passes (both routes `â—‹` static). Interactive extras (same day): the Speed section's gear row is now animated (large cog 12s cw + small cog 6s ccw - visibly 2:1 opposite) and the Stress section embeds `src/components/SteamworksPlayground.js` ('use client): pick a source, gear Ã—1/Ã—2/Ã—4, hang machines (millstone 4, hammer 8, crusher-pair 16, fan 4, pumpjack 8, belt 0.5 Tq/RPM) and watch a live load-vs-capacity bar with run/jam verdicts - the numbers match the plugin.

Recipes page pictures (2026-10-24): Kura items show a rendered picture (result, grid slots, ingredient chips) from `public/recipes/kura/<item_id>.png`, made with `TRANSPARENT=1 python -I Kura/tools/preview3d.py out.png 128 <model.json>` (item id = lower-case name with underscores; `PICTURES` set in `src/app/recipes/page.js` lists the ids). Other plugins have no pictures yet - same method works for them.

Recipe icons (2026-10-24): every item name in the /recipes grids gets a picture. Kura items use renders of their models (`public/recipes/kura/`), everything else (vanilla ingredients included) our own 16x16 drawn icons from `scripts/make-recipe-icons.py` (keyword rules per kind: ingot/ore/dye/flower/concrete/food..., fallback = coloured tile with initials; NOT Mojang textures) -> `public/recipes/icons/<id>.png` + `src/lib/recipe-icons.json`. The slot also shows the item name under the picture. `scripts/build-recipes-data.js` tidies parser artefacts (`New`, `Ore:`, `Of(...)`). Rerun the build script, then the icon script, after changing recipes.

Sakagura item models in hand (2026-10-24): garden seeds/fruit were visible in the GUI but not in the hand because `Sakagura/tools/make_pack.py` `model()` had no translation on the first-person hand display (the model sits at y=0..h, so it hung below the hand). Now same as Kitchen: hand translation `ty*0.3`, gui centred on x, `head` slot added. Rebuild: `python -I Sakagura/tools/make_pack.py resourcepack` (run in Sakagura/), then `python -I scripts/merge-packs.py`. Not live-tested; if still invisible, tune the `0.3` factor (first-person) or check the pack actually reached the client.

### Build-sets: zoo, garden, kitchen decor (shared engine `propkit/`, 2026-10-24)

`propkit/Decor.java` + `DecorDef.java` are templates (`__PKG__`/`__NS__`); `python -I propkit/sync.py` copies them into Wildlife (ns `zoo`), Sakagura (ns `garden`) and Kitchen (ns `kitchendecor`) - edit only the templates. A prop = hidden BARRIER/LIGHT/none base + ItemDisplay (`<ns>:<model>`) + optional Interaction hitbox, saved as text lines in chunk PDC; fences/counters/tables join neighbours (models `<m>_<mask>`, N=1 E=2 S=4 W=8), gates open (`_open` model), seats, wall mounting (model's back = z 16), shapeless recipes, item = PAPER with PDC `decor`. Each plugin must register the engine as a listener, call `decor.registerRecipes()`, `loadChunk` on enable, `tick(gameTick)` every 10 game ticks, and skip teardown when `isStopping()`. Packs: `propkit/propmodels.py` (palette-texture helper) + `Wildlife/tools/make_zoo_pack.py`, `Sakagura/tools/make_garden_pack.py`, `Kitchen/tools/make_kitchendecor_pack.py` (`python -I tools/<script> resourcepack`), preview with `propkit/preview.py`; `scripts/merge-packs.py` picks all three up. Wildlife 0.2.0 `Zoo` (ticket kiosk with Vault payment to the owner, turnstile/gate that consume single-use tickets, enclosure + guest props, `/zoo`), Sakagura 0.2.0 `GardenDecor` (21 items), Kitchen 0.2.0 `KitchenDecor` (18 items, separate from the working stations in `Props`). Not live-tested.

### EliteMobs custom models (`EliteModels/`, pilot 2026-10-24)

`EliteModels/tools/make_elite.py` reuses Wildlife's `make_animals.Model` pipeline (bones, animations, palette texture, .bbmodel writer; extends its palette in place) to draw vanilla-proportioned steampunk humanoids -> `fmm-models/elitemobs/em_*.bbmodel` (`python -I tools/make_elite.py ../fmm-models/elitemobs`, run in `EliteModels/`). Pilot set: `em_steam_zombie`, `em_clockwork_skeleton`, `em_steam_pillager`, `em_rust_husk`, each with idle/walk/attack/death. Server: copy into `plugins/FreeMinecraftModels/models/elitemobs/`, boss yml `customModel: <file name>`. Not live-tested; animation rotation signs (arms forward = +X rotation) unverified in-game.

Relics 0.2.0 New Year Countdown Board (2026-10-24, `NewYearBoards`, relic `COUNTDOWN_BOARD`, models `countdown_board[_wall]`): 3x2-block steampunk scoreboard (NONE base, 2.6-wide hitbox) with three TextDisplays (title / HH:MM:SS / zone line, refreshed once a second from `Relics.newYearTick`, only with a player within 48). Real time zones, DST-proof: zones are Java `ZoneId`s from config `new-year.zones` ("Label|Zone/Id"), midnight = `ZonedDateTime.of(year+1,1,1,...,zone)` and the countdown is real elapsed time; the offset shown is the one AT midnight. Relic `count` = 0 world (follows the zone whose New Year is next) or index+1 into the zone list; sneak + right-click (placer or `relics.admin`) cycles it, plain right-click prints the world timetable. Zone midnight -> "Happy New Year" on the board for 10 min + title; fireworks only for the home zone (`timezone`) or a fixed board. Relics 0.1.6: undeletable-relic fixes (lost display/hitbox are redrawn, attack not ignoreCancelled, `/relics remove [radius]`). Not live-tested.

Relics 0.3.4 Countdown board SIZE FIX (2026-10-25, same `NewYearBoards` / `RelicType.COUNTDOWN_BOARD`): the board models in `Relics/tools/make_pack.py` `countdown_board[_wall]` had all element coordinates written 16x too big (design-pixel habits: frame at x -16..32, y 6..35, z ~5..16) while the relic draws with `RelicType.scale` 1 ("the tree is built small") - so the scoreboard rendered ~48 blocks wide and ~35 tall in-game: too big for Minecraft to load (texture stayed black / flicker) and the block did not place properly. Rewrote the elements in real block units (frame 3 wide x ~2 tall, panel inset, gears, pipe+valve, lamps, floor posts); rebuilt `Relics/resourcepack/Relics-pack.zip` + `scripts/merge-packs.py` -> `Iekcorbar-pack.zip` (z-fighting fix pushes faces 0.03 out). Hitbox (2.6 x 2.0) and TextDisplay layout unchanged. Same day, "walk under it" version like the red Torii Gate: the floor model now shifts the whole board up 2.6 blocks onto two tall legs (total ~5.4 tall, clear space under the panel), `NewYearBoards.spawn` lifts the three floor TextDisplays by the same 2.6 (wall unchanged), and the floor Interaction hitbox grows to 4.8 tall so the whole gate stays clickable. Also 0.3.4: the music props (music box, harp, xylophone, gramophone - `Jukebox`) play a **playlist on loop** instead of repeating one tune: when a song ends the next starts (sequential, or random when shuffled); sneak + right-click menu got Play/Pause, Shuffle, Skip and Turn-off buttons (shuffle persists in the relic's free `variant` field); plain right-click toggles play/pause. New config `music-radius` (default 64 blocks) sets how far the music carries (playSound volume = radius/16, near-check 1.2x, stopSound radius+32) so one box fills a whole town square. Also 0.3.4 staff commands (helpers `Relics.near` / `Relics.lookingAt` (sight-ray scoring over `near`), `NewYearBoards.zoneArg/describeZone/zoneList`, `Jukebox.isMusicProp/play/skip/toggleShuffle/state`): `/relics where [player] [radius]` (placed relics + owner + distance, closest first, max 40 lines), `/relics remove target` (the one you look at; `remove [radius]` unchanged, "target" is caught before the radius parse), `/relics board <list|world|zone|number> [player]` (board you look at, else nearest within 64), `/relics info` (look-at: type/id/collection, place, mount/facing, owner, board zone, playlist state, wreath candles) and `/relics music <play|pause|skip|shuffle|list> [radius]`. Permissions split: `relics.give` (give/giveall/disc give) + `relics.build` (where/info/remove/board/music), `relics.admin` parents both (default op; existing staff loses nothing). Not live-tested.

Relics 0.3.4 Gift Box upgrade (2026-10-25, new `Gifts` listener, relic `GIFT_BOX`): the box is drawn 1.5x now (hitbox 1.2 x 1.2) and right-clicking it opens a **27-slot container GUI**: nine present slots (real items, saved positionally), a wrapping-colour button (cycles `gift_box/_green/_gold` via the free `variant` field) and a **Lock** button. Locking closes the GUI and asks the wrapper to type the recipient's name in the chat (`AsyncPlayerChatEvent`, "cancel" aborts); the name is resolved to a **UUID** (`getPlayerExact` -> `getOfflinePlayerIfCached`, must have joined before) so a renamed player still gets their gift - locking to yourself is refused. A locked box is frozen: only the recipient can open it, only between **24 and 26 December** (`Gifts.christmasNow()` in `RelicsPlugin.zone`); the wrapper can never open their own box; opening hands all items over, resets the box and pings the giver if online. Breaking a locked box is refused for everyone (even the recipient outside the window, even the owner) unless the breaker has `relics.build` (staff grief escape). Storage: the relic line's gift column became `V2;<locked>;<recipient>;<base64 items...>` (legacy single-base64 lines still parse; old boxes migrate on save); the `Relic.gift` field was replaced by `gifts`/`locked`/`recipient` + `encodeGift/readGift`. GUI close saves the nine slots (and hands items back if the box vanished mid-edit). Not live-tested.

Relics 0.3.4 Secret Santa round (2026-10-25): the gift box is **not craftable** anymore (the `PAPER PAPER WOOL GOLD_NUGGET` recipe was removed - boxes only come from staff, that way the event stays one draw). Additions for the draw-and-fill flow: `/relics secret <player> <player> [more]` (`relics.give`) draws deranged lots among online players (nobody draws themselves: every fixed point swaps with the next seat), gives each a fresh box item and a private whisper of their secret name; `/relics gift find <player> [radius]` (`relics.build`, default 128) lists every box addressed to that player (locked-for or in-progress) with coordinates; `/relics gift rellock <player>` (`relics.build`) re-points a locked box to another recipient - typo rescue only, contents stay untouched, still nobody can open before 24 December; `where` and `info` show the box status via `Gifts.status()` ("locked for X: N present(s), wrapped by Y"), and `Gifts.findByName` (online -> profile cache) is shared by the chat-lock and rellock. Not live-tested.

Pack icon (2026-10-24): `scripts/make-pack-icon.py` draws `scripts/pack-icon.png` (brass gear + sakura, pure Python); `scripts/merge-packs.py` adds it to the merged pack as `pack.png` so the pack has an icon instead of the missing-texture block in toasts/pack lists. ResourcePackManager builds the final pack itself, so its own pack icon setting may still need pointing at this file. Recipe-unlock toasts for our custom items can also show a missing-texture block if the client has not loaded the pack yet at join (all plugins call `discoverRecipes` on join) - not changed yet.

Packs = ONE file (of ours): `iekcorbar-packv2.zip` (2026-10-26, ~15 MB): the mixer had become untraceable (garden/brewery looked wrong) - the old split (merged `Iekcorbar-pack.zip` PLUS loose per-plugin zips in the mixer) let stale copies fight fresh ones. Since today: `scripts/merge-packs.py` builds the ONLY file of ours the mixer needs, named **iekcorbar-packv2.zip** - a reproducible fixed-timestamp zip of our 13 sources (Steamworks, Kitchen+Kitchendecor, Sakagura+Garden, Relics, Kura, Wildlife-sounds+Zoo, Zukan, Satchel, Hihou, Mmo), each built by that plugin's `tools/make_pack.py` (or `make_garden_pack.py`/`make_kitchendecor_pack.py` - run those first when a model changed). Third-party plugin packs (EliteMobs, FMM + FMM-shared, TechCore, ValhallaMMO_default, BackpackPlus, RPM's own) stay their own zips in the mixer: the 2026-10-26 experiment of baking them into v2 (~68 MB, with a JSON-union for their atlas/font/items collisions - that code still lives in merge-packs.py, THIRD_SOURCES commented out) ended client-side in "1 out of 2 resource packs failed to download" - 68 MB is too fat, 15 MB loads fine. Deploy: v2 into `plugins/ResourcePackManager/mixer/`, delete old per-plugin copies of OURS from there, leave the third-party zips untouched. The old `Iekcorbar-pack.zip` is deleted, locally and on the server. Same-day relic-size audit (bounding box per models/*.json in Relics-pack.zip; no rotations in these models so min/max is exact): countdown_board was still 16x too SMALL (the 0.3.4 fix overcorrected - rendered 0.19x0.34 blocks while its hitbox and TextDisplays assume 3x2) - now in real model units (3.0 wide x 5.4 tall incl. legs); byobu_screen 1.5x1.13 -> 2.0x1.62 (a room divider must hide a standing player); champagne_tower 0.79 -> 1.38 tall (S=1.75 around block centre). Known upstream quirk: Mmo-pack has 5 dangling refs (MMOItems' own typos: pine_resins/enchanter/xxx), predates this change.

Pack build status (26 okt, avond): **build 4** = `iekcorbar-packv2.zip`, 5788 files, 15,38 MB, sha1 3b76e5fd - alle 13 bron-packen vers gerenereerd (`python -I tools/make_*.py resourcepack` per plugin) en voor het eerst volledig geaudit. Twee systematische 26.2-zieken gevonden én genezen: (1) elementen buiten -16..32 op ANY as worden door de client geweigerd (paarse kubus) - merge-packs.py doet nu de Y-FIT pass (pergola 1.4999, scarecrow 1.125, entrance_arch 1.25, lamp_post 1.5625, countdown_board(_wall) 2.6998 - server-schaal compenseert 1/k) + boundary clamp op alle assen (de z-fight-duw duwde de boards tot x=-16.06/32.09 buiten de perk); (2) **26.2 resolveert het `item_model`-component via `assets/<ns>/items/<id>.json` definities** - elk model dat via ITEM_MODEL getoond wordt MOET zo'n definitie hebben, anders magenta kubus. Dat ontbrak voor alle 98 nieuwe crop-modellen (Kitchen 50, Sakagura 48: `block/<crop>_stage<N>`) -> vandaar de paarse rijst/kersen-perken; Kitchen/Sakagura `make_pack.py` schrijven de definities nu mee. `scripts/audit-pack.py` is uitgebouwd tot volledige client-simulatie: bounds, UV, textures/parents, definitie→model én orphan-check (model zonder definitie/parent/blockstate-verwijzing = volgende magenta kubus); minecraft:/mmoitems-uitzonderingen staan erin toegelicht. Commando na elke packbouw: `python -I scripts/merge-packs.py && python -I scripts/audit-pack.py iekcorbar-packv2.zip` (exit-code 0 = schoon). Huidige stand: 0 problemen, 0 orphans in eigen namespace; 3 gekende MMOItems-typoutbo's (KNOWN-lijst, die plugin gebruikt custom-model-data, geen defs nodig).

### Satchel plugin (`Satchel/`, backpacks, 2026-10-24)

Separate Maven project (`Satchel-X.jar` at the repo root). The idea (backpacks with tiers and upgrade slots) comes from the Sophisticated Backpacks mod, which is All Rights Reserved: nothing of it is copied, code/models/textures are our own (`Satchel/tools/make_pack.py` -> `Satchel/resourcepack/Satchel-pack.zip`, merged by `scripts/merge-packs.py`; `tools/preview3d.py` renders sheets, but z-sorts very thin parts badly). Items are PAPER with `item_model satchel:<id>` + PDC `satchel:item` (`SItem`, `Items`). Six tiers (`SItem.rows/upgradeSlots`), contents live in the backpack item's own PDC (`store`, `upgrades` as `serializeItemsAsBytes`, `upg` = installed upgrade ids as cheap text, `bag` = uuid assigned on first open). `Backpacks`: chest GUI (storage rows + one upgrade row, grey panes in locked slots), saved on close and on death (death drops patched), the open backpack is locked against moving/dropping, no backpack inside a backpack, shift-click/drag/hotbar-swap rules by hand. Upgrades: pickup (`EntityPickupItemEvent`, mode all/matching), magnet + feeding (one 10-tick timer, only players who carry a backpack cost anything), everlasting (despawn/damage/void cancelled for the dropped item). Recipes are `Items.Spec` (generic paper slots, validated in `PrepareItemCraftEvent`; crafting the next tier copies contents + upgrades). Zukan gets real recipes from `ZukanData` (`replaceCrafting`). Wiki: `docs/wiki-satchel/` (staff "Satchel Backpacks" import button). 0.2.0 adds deposit/restock (sneak + right-click a container, only kinds both sides hold) and refill (hotbar stacks under half are topped up). Not live-tested; worn-on-back, placeable backpack and the other mod upgrades are not built.

Satchel 0.2.1 (2026-10-25): backpacks are NOT craftable by default (`crafting.backpacks: false`; upgrade plates still are). They are sold by wandering traders (`wanderingtrades/trades/satchel.yml` canvas/copper/iron, `satchel_rare.yml` gold/diamond/netherite; the result is a plain PAPER named like the backpack with last lore line "Satchel", swapped for the real item on first right-click by `Items.fromTrader`) and by the shop NPC via `/satchel give <tier> <player>`.

Ore textures (2026-10-25): `Steamworks/tools/ore_textures.py` draws the seven ore textures (own pixel art, tiling noise, bolder chunks with dark/mid/light/spark tones) AND labPBR 1.3 maps `<ore>_n.png` (normal + AO + height) and `<ore>_s.png` (smoothness, F0 with metal = 255 for zinc/tin, emissive for sulfur) so shaders (user runs Complementary Unbound) show depth, metal glints and glowing sulfur. `make_blocks_pack.py` writes all three per ore. Shader setting: Complementary > Materials > labPBR. Not yet seen in-game.

Wildlife 0.3.3 (2026-10-25): Climbing Log (zoo prop log_horizontal) drawn at scale 3 (about 3 blocks long, about 2 blocks tall = player height, to hide behind), hitbox 2.4 x 1.9. It is still a walk-through prop (no collision), as all hit()-props are.

Crop flicker fix (2026-10-25, Kitchen 0.2.3 / Sakagura 0.2.3): walking over a crop flipped the tripwire "powered" bit (state index +/-32 = another crop or an invisible state; napa cabbage 10-14 -> 42-46 unused = it vanished). The PHYSICAL/EntityInteract cancels were not enough, so both plugins now also cancel Paper EntityInsideBlockEvent on reserved tripwire states (Paper patch: that event is checked first in TripWireBlock.entityInside). Not verified in-game.

### Custom books: Brewer's Manual (IekcorbarSiteSync 1.14.5, 2026-10-25)

`books/brewers_manual.txt` - the Sakagura companion to the Engineer's Manual: cauldron, bottling, the Steam Still, casks, quality (40/25/15/20), garden, drinking, first brew + roadmap; title `&dBrewer's Manual`, author "The Sakagura Guild", `model: sakura_journal` (design already ships in fmm-models/steampunk_iekcorbar/books/), `loot-weight: 3` (rare), not copyable. BookManager now seeds it next to engineers_manual (added-once line, never overwrites server edits) - so a plugin restart drops it into `plugins/IekcorbarSiteSync/books/` automatically; `/ibook give <player> brewers_manual`. Note: the `make_books.py` named in the "Custom books" section above is **not in this repo copy** - the six designs exist only as generated .json+.bbmodel; copy sakura_journal to the server's FMM models dir if a book shows vanilla-look (it should already be there, all six were deployed together).

### Advancement tabs for every plugin (2026-10-25)

`propkit/Adv.java` (template, synced by `propkit/sync.py` into Satchel, Kura, Zukan, Kitchen, Steamworks, Relics, Sakagura, Wildlife) gives each plugin its own tab in the advancement screen, toasts only (`announce_to_chat` false everywhere, the tab root never toasts). Each plugin has an `Achievements` class defining the entries (`Adv.add(id, parent, icon, title, desc, frame)`, `.allOf(...)` composites, `.hidden()`, `.glint()`), a scanner that names held items (`Adv.scanner` + `.item(key, advId)`; a scanner may return several keys joined with `|`; checked every 5 s so crafting/trading/picking up all count) and action hooks (`plugin.adv().grant(player, id)`). Advancements are loaded with `Bukkit.getUnsafe().loadAdvancement` and re-registered on `ServerResourcesReloadedEvent`. Wildlife's tab root and species entries still come from `Journal`; `Achievements` there uses `ownRoot=false` and `Journal.registerAdvancements` calls `adv.register()` after the root exists. Relics' tab is in-game only (no public docs, paid pack undecided). Versions: Satchel 0.2.2, Kura 0.1.6, Zukan 0.2.5, Kitchen 0.2.4, Steamworks 0.3.10, Relics 0.3.3, Sakagura 0.2.4, Wildlife 0.3.4. Not live-tested.

Crops next to water (2026-10-25, Kitchen 0.2.5 / Sakagura 0.2.5): `onFlow` used to destroy a crop (drops + air) when water tried to flow into its block; now it only cancels the flow, so crops on farmland beside water stay as they are. Not verified in-game.

### Easter eggs (2026-10-25)
Every plugin has small jokes, each with a hidden advancement (`Adv.hidden()`) in its tab: Steamworks (`EasterEggs`: 30 s of cranking = tired arm + Mining Fatigue, 3 whistles in 10 s = flute tune, mixer spinning over an empty kettle, raw zinc+tin+sulfur in one inventory = sneeze), Kitchen (10 daikon cuts in a row, only rice in a cooking pot, 5 min on a zabuton = cat purr, in `Props`), Sakagura (drunk >= 80 renames the display name to "Definitely Fine" until < 60, two sneaking drinkers within 3 blocks = "Kanpai!", drinking a Murky Brew), Wildlife (duck/songbird climbing a capybara, elephant jumping from a hedgehog/lizard/snail/crab; the tanuki wink waits for the Tanuki feature), Relics (gramophone x100 skips, maneki-neko x7 lucky paw, wagasa stand next to a tesla coil), Kura (search "cookie" in a terminal, a courier golem named Dobby delivering), Zukan (50 bookmarks, search "recipe for disaster" shows TNT), Satchel (backpack into a backpack = "Nice try, wizard.", closing a full netherite backpack = Slowness), IekcorbarSiteSync 1.14.3 (joke book `not_supposed_to.txt`, loot only in buried treasure and ancient cities, written on first start if missing). Versions: Steamworks 0.3.11, Kitchen 0.2.6, Sakagura 0.2.6, Wildlife 0.3.5, Relics 0.3.4, Kura 0.1.7, Zukan 0.2.6, Satchel 0.2.3. Not live-tested.

### Easter eggs, second wave (2026-10-29, owner: "alles, en vooral grappig")

15 new hidden eggs + one cross-plugin meta egg, same trigger->dry English action-bar->hidden-advancement style. Steamworks 0.3.25 (`EasterEggs`): `anxiety` (hammer running over nothing for 30 s = "It's just exercising."), `sulk` (30 s standing within 6 of a jammed network, 5 s scan = "Even machines need to sulk." + bass note), `commute` (place a 3rd water wheel in one network, checked 2 s after placing = "You're not paying for all these lanes?"). Kitchen 0.3.1 (`Props`/`Items`): `noodle` (pot of only WATER_BUCKET heated 5 min = "Waiting for noodles.", per-pot transient `Prop.waterFor`), `sitter` (5 min STANDING on a zabuton without sitting = cat hiss + angry-villager particles, `standSince` map reset by sit/moving off), `furnace` (10 FOOD/DRINK/COOKED/BAKED consumes in a 2-min deque window = Slowness 6 s "You are not a furnace."). Sakagura 0.4.1: `table` (>=3 sneaking drinkers within 3 blocks at cheers, in `Drunk.onSneak` alongside kanpai), `terroir` (brew batch started in a river/ocean/beach biome, in `Cauldrons` pot-create = "Bold choice. Bold label."). Wildlife 0.7.1 (`Animals`): `pond_stop` (capybara now accepts up to 5 stacked bird passengers; at 5 duck/songbird riders a bell rings = "Next stop: pond."), `tanuki_wink` (FINALLY the promised wink: night, player velocity ~0 within 6, per-animal cooldown via rideUntil, gold DUST at the eye + "You've made a friend."). Relics 0.4.5 (`Relics.java` case EGG): `trap` (found while the hider is online within 8 blocks: cloud puff + creeper hiss, finder "It was a trap.", hider "They found it. And they lived."). Satchel 0.3.2 (`Backpacks.onDamage` LAVA branch): `sink` (unprotected backpack in lava: damage cancelled, 3 s slow-sink with BUBBLE particles, extinguish sound, "It goes down with dignity." - netherite/everlasting stay immune). AfkCinema 0.2.2 gets its own minimal `Adv` (namespace `afkcinema:afkcinema/*`, root + 3 hidden): `extra` (the stand-in slowly TURNS ITS HEAD toward anyone within 5 blocks of the filming spot; the passer-by gets "You're in the shot.", 30 s/player cooldown), `closing_credits` (film starts while the viewer is sleeping), `directors_cut` (7 manual /cine afk takes in one day). IekcorbarSiteSync 1.16.2: new requirement type `external_advancements` in `AdvancementManager.met()` (full "ns:tab/id" strings, checks `Bukkit.getAdvancement`+progress, unknown/unloaded = unmet) + hidden challenge `iekcorbarsitesync:iekcorbar/untrustworthy` (dragon_egg, parent secret_friend, announce:false) whose `requirements.keys` lists ALL 41 player-obtainable egg advancements (zukan staff `cheat` deliberately excluded); earning it broadcasts "[the Guild] <name> is officially untrustworthy." via the existing onDone. Check rides the join-check + 20 s timer (no new event). The advancements.yml is a saveResource file: the server copy must be replaced manually (deploy ships `IekcorbarSiteSync-1.16.2-advancements.yml`, sha1 4d44a40a...) + `/iadv reload`. All 8 jars built green (mvn clean package); VPS carries AfkCinema-0.2.2.jar already (replaced 0.2.1 same day, owner-ordered, restart still pending); the rest is TE-DEPLOYEN punt 40 for the owner's restart batch. Full private overview: `EasterEggs-PRIVE-2026-10-29.pdf` (repo root, *PRIVE* is gitignored). Live in-game behaviour not yet verified.

EliteModels wave 1 (2026-10-25): `tools/make_elite_all.py` builds 39 models (one per boss entity type, `TYPE_TO_MODEL` -> `models.json`) from `make_elite.py` (own ~210-colour palette replacing Wildlife's in place), `elite_bipeds.py`, `elite_beasts.py`, `elite_floaters.py`; `preview.py` renders contact sheets. `tools/assign_models.py <EliteMobs custombosses folder>` writes copies of every usable boss file (own entity type, enabled, no disguise, no customModel) with `customModel: <model>` appended into `EliteModels/server/custombosses/` (never edits originals) + `assigned.csv`. Regular elites (`mobproperties/elite_*.yml`) have no model key - not done. Not live-tested.
Wave 2 (2026-10-25): `tools/elite_extra.py` adds creeper, goat, hoglin, llama, bee, phantom, piglin brute (46 models). EliteMobs 10.9.10 mobproperties files have no customModel key, so regular elites cannot use models; only custombosses can.

### NPC skins (`NpcSkins/`, 2026-10-25)
49 own-pixel-art classic player skins for the Citizens/BetonQuest quest NPCs (job masters/senseis, Japanese food shop, merchants, story NPCs, 4 guard looks), generated by `NpcSkins/tools/make_skins.py` (pure Python, roster = `ROSTER` with Citizens ids; run `python -I tools/make_skins.py` in `NpcSkins/` -> `skins/`, `preview_sheet.png`, `npc_skins.csv`, `apply_commands.txt`). Citizens NPCs are type PLAYER, so a skin (with the overlay layer for 3D depth) is the only way; an FMM model would need extra glue. Applied by the user with `/npc skin --url`. Not seen in-game.

EliteModels NPCs (2026-10-25): `tools/elite_npcs.py` adds 25 friendly humanoid models (`em_npc_*`: barkeep, blacksmith, gamblers, six class trainers, enchanter, fletcher, guides, repairman, Saint Nick ...) for the EliteMobs NPC files that had no model; `make_elite_all.NPC_MODELS` maps npc file -> model, `tools/assign_npcs.py <EliteMobs npcs folder>` writes copies into `EliteModels/server/npcs/` with `customModel:` replacing the `disguise:` line. The palette in `make_elite.py` is at 252 of 256 colours: reuse existing names, appending more than 4 base colours will assert. Not live-tested.

### Gouw plugin (`Gouw/`, land claims, 2026-10-25)

Separate Maven project (`Gouw-X.jar` at the repo root), the in-house successor to the paid **Lands** plugin (its saved data is imported; none of its code/assets are used). **No MySQL involved at all** - claims live in `plugins/Gouw/claims.json`, written by `ClaimStore` (in memory: `byOwner` map - one gouw per owner, like the server actually used it - plus a world+chunk index; plain readable JSON on purpose, so any future plugin can read it the way Gouw reads Lands). First start with no/empty claims.json runs `LandsImporter`: reads the Lands SQLite db (`import.lands-database`, default `plugins/Lands/Data/database_v2.db`; the sqlite-jdbc driver comes from `plugin.yml` `libraries:` which Paper downloads on the *server* at startup - the dev machine never connects to anything), maps `lands_worlds` ulid -> Bukkit world (skips unknown worlds), `lands_lands_claims.chunks` JSON -> chunk list, and the `area.holder` Owner-role UUID -> our owner, other trusted roles -> members. `/gouw admin import [force]` re-runs it by hand. `Land.Flag` (BUILD/ACCESS/MOB/PVP): trusted members default all-on (= old Lands behaviour); flags are per-land, saved as `flags`, toggled in the menu. `GouwListener` guards at chunk level: break/place/bucket need BUILD; containers/doors/buttons/beds/anvils/cauldrons etc. need ACCESS (`setUseInteractedBlock(DENY)`); `PrePlayerAttackEntityEvent` needs MOB (or PVP on players); explosions never take claimed blocks; pistons may not drag blocks across a gouw border; strangers walking in/out get an action bar + a gold-dust `Border` pulse of the entered chunk. `GouwCommand` (`/gouw`, aliases `/g` `/claim` - players knew `/claim`): bare command opens the menu; claim/autoclaim [r]/unclaim [all]/trust/untrust/members/rename/info/list/near/border/leave + `admin delete|import|reload|bypass`. `GouwMenu` (chest GUI, MAIN/FLAGS/ADMIN pages): chunk stats, claim, 5x5 autoclaim, unclaim (shift = whole gouw), border pulse x5, member-flag toggles, trusted heads (click removes), trust via **chat-name lock** (same flow as the Relics gift box: name typed in chat, resolved to a UUID, cancel with "cancel"); staff page: every gouw as a head (click = delete), bypass toggle, expire check, Lands reimport. Bypass is a per-player **set** toggled by `/gouw admin bypass` (needs `gouw.bypass`), not a blanket permission check. `Expiry`: `expire-inactive-days` (default 0 = off) releases gouwen of owners offline longer than that - checked 1 min after enable and hourly, and from the staff menu; online owners and owners with no known last-play (imported Lands gouwen of players who never joined) are never touched. Not live-tested; the Lands import + first-start path only runs on the real server.

### Sakagura distilling rebuilt as the Steam Still (2026-10-25, v0.2.8)

The Paper PotionMix route (brew + glowstone in a brewing stand) **never ran on the live server** - the user reported it plainly not working after an earlier session claimed it did; the design also fought vanilla's glowstone = splash-potion modifier. 0.2.8 replaces it with a fully self-contained station: Stills.java - a real BARREL tagged sakagura:still + ItemDisplay cover (sakagura:still model, drawn in `tools/make_pack.py`, same pattern as Casks), customName "Steam Still", drop-back in BlockDropItemEvent, chunk-load cover reload + 
elight, explosion handlers uncover, 	ick() (1 s) consumes one glowstone dust every distilling.seconds-per-run seconds and applies distills++ + step 'D' to up to distilling.bottles-per-run bottle stacks inside (heat from below via Cauldrons.heated). Contents live in the barrel itself (hoppers, protection, persistence all free). Distilling.java and the debug-distilling config are gone; new distilling: config section (seconds-per-run / bottles-per-run / debug). Brew model, recipes.yml and scoring unchanged. Wording moved everywhere: Goods.next() lore, Zukan `describe`, `/brew` help, wiki docs `00-intro`/`01-sakagura` (new "Steam Still" section) and the rebuilt `src/lib/wiki-seeds/sakagura.json` (staff: re-import "Sakagura" pages on /wiki). New shapeless recipe (barrel + 2 iron ingots + blaze powder + glowstone), achievement `steam_still` ("A Still That Works"), Zukan item list updated. `Iekcorbar-pack.zip` merged (sha1 d288c1b7...). Also fixed: Cauldrons.tick removed "ghost pots" (cauldron exploded -> the Pot stayed in the map and its saved chunk PDC, and would silently reappear if a water cauldron was ever placed back at those coords); now dead pots are dropped on the next tick.
Not live-tested: the still needs in-game verification (heat check, particles, hopper in/out, chunk unload/reload).

### Wildlife 0.4.0 - Japan set + the gait rework (2026-10-25)

User mandate: "more animals, but look at the existing models/skeletons - many animals move weird now; as natural as possible." Diagnosis: `quad_walk` gave EVERY quadruped a diagonal trot (wrong: real animals stroll in a lateral sequence), the elephant got that same trot (it paces), the songbird's "walk" was a 0.18 s wing-flap-as-walk (vibrating parrot), and amplitudes were inflated (boar 30 deg, hedgehog 35). `make_animals.py` now has `gait(m, period, amp, style="walk|pace|trot")` (per-leg phase offsets: walk = bl,fl,br,fr quarter beats; pace = same side; trot = diagonals, only for the hedgehog's scuttle) with a half-bob body, `hop_walk()` for ground birds (both feet kick, folded wings give one small flutter per hop, head nod), and `stilt_walk()` for the wader (keyframed lift-hold-place step). Every existing species re-tuned (deer 0.95/20, bear 1.3/15, boar 0.8/16, capybara 1.05/14, zebra 0.95/18, elephant 2.0/13, tortoise 2.2/10, duck roll 7->4, lizard body 10->6). **The gait change re-renders EVERY wildlife bbmodel - copy the whole `fmm-models/wildlife` folder to the server.** New species (Japan set): `TANUKI` (pig base, NIGHT, bamboo/forest, ringed tail, "Charcoal Tanuki"), `CRANE` (chicken base scale 1.9, WATER, pairs for life, red crown via eye_red cells, stilt walk, drops feathers, "Golden Crane"), `KOI` (COD base, new `Habitat.POND`: `Spawner.spot()` spawns INSIDE the water (top+below water, above air); `Brain.apply` SKIPS POND so the fish keeps its own swim AI; tail-wave swim anim; "Golden Koi"), `MACAQUE` (pig base, DAY, troop groups 3-6, pink face, short curled tail, "Snow Macaque"). Palette: only six new colour cells (tanuki, tanuki_dark, koi_orange, koi_black, koi_fin, macaque) - everything else reuses existing colours, because `make_zoo_pack` asserts a 256-colour palette. Sounds: vanilla fox/chicken/cod placeholders (no new synth this batch; the "tanuki wink" easter egg mentioned in the 0.3.x notes was never actually in the code and still awaits a Tanuki feature). Statues + spawn eggs + journal + advancements all auto-derive from `MA.SPECIES`. Wiki docs 00/01/02/03 updated, `wiki-seeds/wildlife.json` regenerated (staff re-import). Not live-tested: watch the new gaits in game and keep tuning.


**Same evening, audit "bestaande dieren" (user: skeleton/model/animation/AI often does not match):** bone-audit script over all 89 bbmodels = 0 animation tracks pointing at missing bones (structure is sound; the weirdness is behaviour + gait, fixed above). Real AI holes found and closed in `Brain`: (1) AIR species skipped Brain entirely, so the parrot-based songbird could land on the player's shoulder and the bee-based butterfly/firefly/dragonfly kept hunting hives and known flowers far away - now those exact goals are stripped (PARROT_LAND_ON_OWNERS_SHOULDER, BEE_LOCATE/GO_TO/ENTER_HIVE, BEE_GO_TO_KNOWN_FLOWER, BEE_POLLINATE/GROW_CROP/VALIDATE_*/BECOME_ANGRY/ATTACK) while their flying wander stays; (2) elephant ears flapped about the Y axis (swinging like flags) - now X (front-back pendulum about the head); (3) the crane used the duck rule "75% of wander targets float ON the water surface" - cranes now wander dry ground at the water's edge (wantWaterSurface excludes CRANE). Riding deer/zebras was already impossible (onInteract cancels everything but name tag/lead, so a saddle never goes on). NOTE FOR DEPLOY: the gait work re-rendered ALL 89 bbmodels - copy the whole fmm-models/wildlife folder to plugins/FreeMinecraftModels/models/wildlife and restart; the resource pack itself is unchanged (Zoo-pack only has statue/egg item models).

### Wildlife 0.5.0 - batch 2: roos to orca (2026-10-25, same evening)

User picked 10 more explicitly: kangaroo, ostrich, crocodile, koala, mole (met molshopen), rhino, penguin, orca, piranha, grote ara's. All in: builders in `make_animals.py` (133 models now, 0 bone/anim mismatches in the audit), `Habitat.POND` now also carries the swimmer species that keep their own water AI (orca = DOLPHIN base: breaches, follows boats, POND spawn needs 2+ water; piranha = COD base in shoals of 4-8). Macaws are the third EXTRA-look species (scarlet/blue-and-gold/military, `key()` maps `macaw_*` -> macaw) and the ONLY AIR bird allowed to keep `PARROT_LAND_ON_OWNERS_SHOULDER` - a parrot on your shoulder was the whole point (songbirds keep it stripped). New: **`Molehills.java`** - every mole spawn digs a `zoo:molehill` ItemDisplay (non-persistent, chunk PDC "molehills", like cask covers) within 5 blocks unless one already exists within 12; loadChunk re-shows them (skips if something was placed over the spot); removeAll on /reload-style disable. Kangaroo hops (two-footed sine walk, tail counter-phase), penguin waddles with a real side-roll + flipper sway, crocodile crawls with a Y body undulation, ostrich sprints `stilt_walk(0.85, 28)`, koala has a "sleep" anim and Animals.tick puts it down for day-naps (wakes at night/on harm), rhino walks heavy single-file, orca's flukes beat on the X axis (whale tail is horizontal - different from the fish's Y), macaw hop-walks + tail flick. Sounds are all vanilla reuses (rabbit/fox, chicken, wolf, polar bear low, donkey=bray penguin, dolphin orca, cod piranha, parrot macaw). EGG + statue + journal + advancements + /zukan eggs all auto via MA.SPECIES; zoo palette got 3 more colours via existing zoo clay/bark (no new palette cells - 256 assert again). Wiki 00/02/03 updated, seed rebuilt (staff re-import), pom 0.5.0, mvn green, `Wildlife-0.5.0.jar` in root. Not live-tested: check the hop/waddle/sleep in game, and that molehills appear in lawns but not on ice.

### /brewing removal - website mini-game retired (2026-10-25, same evening)

User: "brewery page moet van de website af, dit is dubbel en niet meer nodig, geldt ook voor het
zoeken van hidden items". Everything brewing-minigame was deleted: page src/app/brewing/, all 7
src/app/api/brewing routes (blueprints/hunt/hunt-override/junk-ingredients/leaderboard/recipes/
sessions), src/lib/ingredient-hunt.js, src/lib/tipsy-effect.js, src/lib/brewing-seeds/,
src/lib/brewing-visuals.js (no importers left). providers.js: hunt fetch/claim + tipsy state/
overlay/effect + the floating Leaf spot + the tipsy pill render + both nav entries gone, footer
"Steampunk Brewery" link -> /sakagura "Sakagura Guide"; case 'brewing' notification icon KEPT
(old DB rows). home page.js: "Active brewing vats" stat card gone (Claims card made single-wide),
"The Steam Brewery" card link -> /sakagura. api/status/route.js: activeBrewingVats removed.
recipes/page.js: Sakagura-drinks link -> /sakagura. shop: all clearsTipsyEffect plumbing (import,
purchase auto-drink, Your Remedies panel + handleDrink + drinkingId, admin checkbox, remedies
filter, help text) + items route field + purchases route consume branch stripped (db.consumeShop-
Purchase now dead code, kept). members/[username]: Brewing tab gone with brewSessions/recipes
states + brewing useMemo + tierName + FlaskConical; profile-theme HIDEABLE_SECTIONS lost the
brewing key; bug-categories lost the "Brewing Minigame" entry. lib/achievements: FIRST_BREW slug
+ info removed (DB row may exist -> shows as custom, owner can delete). next.config.mjs: /brewing
-> /sakagura permanent redirect (old bookmarks). docs/wiki-sakagura 00-intro + 01-sakagura now
point to the /sakagura guide instead of the mini-game, seed rebuilt (15 pages). globals.css: the
whole brewing-drink/tipsy/festive animation block (~80 lines) removed. npm run build groen,
/brewing not in routes; redirect present as 308 in routes-manifest. Site zip is STALE (still
contains /brewing) - rebuild before upload.


### Crops rebuilt: structure void + displays (2026-10-26, Kitchen 0.3.0 / Sakagura 0.3.0)

User: crop harvests kept turning into other crops in BOTH plugins and "much more needed to be added";
previous sessions insisted the tripwire system worked. Root cause: the crop identity lived in tripwire
block bits (`disarmed`/`attached`/`powered`/faces) - vanilla keeps rewriting impossible bit combos
through `scheduledTick`/neighbor paths that `BlockPhysicsEvent` cancels cannot stop, so after
harvest/growth the block decoded back as another crop (double drops on harvest were also latent:
the `canBreak` probe fired our own BlockBreakEvent handler - both plugins now skip
`ProbeBreakEvent` instances). The 64-state budget (Kitchen 0-29, Garden 30-62) also blocked new
crops, and cut-off trap strings could masquerade as crops. New architecture in both plugins:
crop block = **STRUCTURE_VOID** (no vanilla logic, unplaceable), type+stage(+wild) in chunk PDC
`crops` int array (pos | ordinal<<17 | stage<<25 | wild<<28), visual = non-persistent ItemDisplay
(PAPER + ITEM_MODEL `kitchen:block/<id>_stage<N>` resp. `sakagura:block/...`), exactly the prop/cask
pattern. Old tripwire fields migrate automatically when the chunk loads (tripwire->void, bits->entry).
No resource-pack blockstate files are overridden anymore (Kitchen's `tripwire.json` + the merged
Sakagura `tripwire-extra.json` are gone). Kitchen: +corn/tomato/chili/sesame (+CORN/CORNMEAL/TOMATO/
CHILI/SESAME_SEEDS items, corn->cornmeal cutting in kitchen.yml, sprite branches, palette colours);
Garden: +apple/pear/apricot/mikan/kiwi (+apple_pip/pear_pip/apricot_kernel/mikan_seeds/kiwi_seeds,
edible, Achievements 'orchard' auto-extends). Staff item commands both sides: `/kitchen take` and
`/brew take <player> <drink|item|cask_wood> [amount]` (drinks match via recipes().match, casks via
caskWood; give already existed). Garden Fountain now `.scale(3f)` (engine already had def.scale)
with splash/falling-water particles raised ~3x. `onDisable`: crops/garden saveAll + removeAllDisplays;
Kitchen `onBreak` also broke straw-on-knife kept intact. Docs updated (wiki-kitchen 00/02,
wiki-sakagura 00/02), both seeds rebuilt (38 + 20 pages), both packs + Iekcorbar-pack.zip rebuilt
(2279 files, sha1 f5ab0fb5) and audited: no tripwire.json anywhere, all 50 crop-stage + 48 fruit-stage
models present. mvn green, Kitchen-0.3.0.jar + Sakagura-0.3.0.jar at repo root. NOT live-tested:
verify harvest no longer changes crops, old fields migrated, new crops plant/grow/harvest, displays
not floating (DISPLAY_Y/DISPLAY_SCALE constants at the top of Crops/Garden), fountain 3x looks right.
Note for deploy: the server's Kitchen/kitchen.yml (if present) keeps its own `cutting` section - add
the corn->cornmeal lines there or delete the file to fall back to the shipped one.

### Steamworks 0.3.13 - Steam Wrench + placement fixes (2026-10-26)

User: gearshift/gearbox/shaft "can't be put in other positions", in creative multiple parts were
placed with one click. Code truth: `KNode.axis`/`facing` were **final** and set only at placement
(PartListener.place per-type switch) - no rotation path existed anywhere. Fixes:
1. KNode: axis/facing no longer final. `Kinetics.canRotate` + `Kinetics.turn(n, cw)`: shaft-like
   cycle X->Y->Z, steam engine X<->Z, belt/station/signal yaw-only, crank/fan/drill/saw/**windmill**
   N-E-S-W-UP-DOWN (this is how the windmill shaft finally can point DOWN into a tower).
2. `SteamworksPlugin.rotatePart`: turn -> visuals.spawn (respawn models) -> kinetics.relink
   (network rebuild through the turned part) -> storage.save -> copper click sound + action bar
   description. WRENCH MaterialType ("Steam Wrench", recipe 2 iron ingots + 2 sticks) +
   sw_wrench.bbmodel + sw_wrench.json GENERATED by tools/make_models.py (wrench() added to
   build(); regeneration byte-identical for existing models) - server needs both new files in
   plugins/FreeMinecraftModels/models/steamworks/ + FMM reload.
   PartListener: wrench branch FIRST inside the part-interaction chain (before millstone fill /
   chain link / boiler fuel / TANK canister check).
3. Creative "multiple placed": root cause is the part items are PAPER; in creative the server can
   fire the same click twice (dual-hand interaction). Hardened: off-hand event now always cancelled
   when it holds any of our items (`isOurs`), AND `place()` drops a second placement on the same
   target within 40 ms. If the user still sees doubles: exact reproduction steps needed.
    "Soms een barrier" in creative had two real causes, both fixed: (a) creative middle-click pick
    on a part handed out the raw BARRIER marker item (no pack hides barrier) - new
    PartListener.onPick cancels Paper's PlayerPickBlockEvent and puts the proper part item in
    getTargetSlot() instead — since 0.3.23 CREATIVE ONLY: Paper fires the event in every game mode and leaves the check to the handler, so survival could otherwise dupe parts; (b) place() set BARRIER **before** the synthetic BlockPlaceEvent, so a
    cancelled/throwing listener left a ghost barrier without a node - the event now fires FIRST and
    the world is only touched after approval. Keep the ProbeBreakEvent guard at the top of onBreak.
4. PartType lore for SHAFT/GEARBOX mentions the wrench; docs/wiki-steamworks/02-power.md got a
   "Steam Wrench" section + shaft/gearbox/windmill lines (seed rebuilt, 20+ pages -> import on
   /wiki). mvn green, Steamworks-0.3.13.jar in root. User: "veel dingen mis" - Steamworks will be
   walked through item by item in a later session (checklist in STEAMWORKS-DEV-NOTES.md).
 Same day REA'd Create 6.0.10 jar (NeoForge 1.21.1, unobfuscated): its wrench rotates through pure
 **blockstate** properties, clockwise around the CLICKED face (`IWrenchable.getRotatedBlockState` +
 `Direction.getClockWise(axis)`), sneak+wrench = disassemble (drops to inventory), rotation is
 refused when the rotated state `canSurvive` fails, and `KineticBlockEntity.switchToBlockState`
 detaches the network before swapping - same pattern as our rotatePart/relink. Create 6 also has a
 RadialWrenchMenu opened by a keybind that lists registered blockstate properties. Gearshift in
 Create is redstone-POWERED, NOT a wrench 3-state cycle. Findings + our follow-up ideas in
 STEAMWORKS-DEV-NOTES.md ("Create 6.0.10 - REA-referentie").
 Follow-ups built same day (0.3.14, jar re-copied, mvn green, KineticsTest 70/70): face-dependent
 wrench turn (Kinetics.turnFace + clockwise table), sneak+wrench dismantle to inventory
 (breakNode overload), ghost-barrier picks already in, plus Create's cog collision rule
 (`isValidCogwheelPosition` -> Kinetics.cogsFit; refuses placing AND wrench-turning cogs that would
 collide, canSurvive-style), and a fixed always-true placeholder check in KineticsTest.
 Later that night: info() now shows each part's orientation (shared orientText()); points 1-11 of
 the 1-voor-1 checklist audited (ratios/meshing verified against Create bytecode; machines, fans,
 belts, arms, oil: green as-is) and **trains scaled x2** (Train.SCALE: body, seats, hitbox,
 wheelbase 2.4/gap 1.6, chimney/whistle/push-box; old saved trains keep their tight 2.0 spacing -
 rebuild them once after deploy). Jar Steamworks-0.3.14.jar rebuilt with all of it; KineticsTest 70/70.
 Then 0.3.15: **route-scoped steam track** - the rails a train drives on get a generated FMM track
 model (ballast/sleepers/brass fishplates; sw_track_straight/slope/curve via make_models.py, spawned
 as non-persistent ItemDisplays every 20 ticks along RailWalker walks, Rail.Shape -> yaw/pitch
 quaternion; MODEL_LIFT 1.5 -> 1.71 so wheels roll on the 3.4px rail head). Other rails stay vanilla
 (no pack blockstate override). Server needs the 3 new sw_track_*.json + .bbmodel in
 plugins/FreeMinecraftModels/models/steamworks/ + FMM reload.

### Gouw krijgen volledige site-aansluiting; Lands kan weg (2026-10-26, SiteSync 1.15.0)
SiteSync kreeg `GouwClaims.java`: leest `plugins/Gouw/claims.json` (chunks per owner, parse once
per mtime) en MySQLSync vult `player_stats.lands_claimed` daaruit - offline eigenaren
inbegrepen; de twee %lands%-regels zijn uit de default config.yml. website db.js
(`getTotalLandsClaimed`, `getLandsClaimedByPlayer`) leest alleen nog die kolom (geen `lands_`
tabellen meer). Wiki-pagina 'claiming-territory' in db.json + iekcorbar-seed.sql herschreven
naar Gouw: GUI (MAIN/FLAGS/ADMIN) + alle speler-/admin commands - die bestonden al in
GouwCommand/GouwMenu, de plugin zelf is onveranderd (0.2.0 blijft). mvn groen;
IekcorbarSiteSync-1.15.0.jar in root EN in de site-zip (03:19). RESTANTIJK: de LIVE wiki-pagina
staat nog als 'Lands' in MySQL (db.json wordt uit de zip gesloten) - één handmatige staff-edit
via moderatie>wiki; daarna pas plugins/Lands verwijderen (lands_* tabellen worden dood vlees).

### Gouw 0.2.1 + Relics 0.3.5: twee echte crash-bugs uit de server-log (2026-10-26, vroege ochtend)
Log analyze (latest(5).log) wees twee dingen:
1) **Gouw crashte bij inschakelen**: `IllegalPluginAccessException: Unable to find handler list for
org.bukkit.event.player.PlayerEvent` - oorzaak: `GouwListener.onBucket(PlayerBucketEvent)`; die klas is
ABSTRACT in Bukkit en heeft geen handlerList. Vaste regel: luister nooit naar een abstracte event-
klasse. Fix = handlers gesplitst in `onBucketFill(PlayerBucketFillEvent)` + `onBucketEmpty(PlayerBucketEmptyEvent)`,
dezelfde guard. Belangrijk: de crash gebeurde NA de Lands-import (L33 wel, L37 registerEvents niet),
dus claims.json was wel gevuld maar **claim-bescherming stond die hele tijd UIT**. 0.2.0-note hierboven
"is onveranderd" was dus te optimistisch - GOUW had nooit gedraaid.
2) **Relics discs**: de 4 "could not set the jukebox song (NoSuchElementException)"-warnings + het
VOLLEDIG ONTBREKEN van "Relics: loaded N music disc(s)" en "Disc songs written to ..." - terwijl de
repo-jar (Relics-0.3.4.jar, 20:05) die code WEL bevat (discs_jar.txt: strings present). Conclusie: op de
server staat een oudere 0.3.4-copy. Reden te meer om 0.3.5 te draaien: Discs.installDatapack schrijft dan
`iekcorbarSeason2/datapacks/relics_discs` (pack_format 64, jukebox_song relics:<id>) en logt het pad;
daarna ONE restart (warnings over song-set zijn normaal zolang de datapack nog niet geladen is; daarna
speelt de schijf). mvn groen: Gouw-0.2.1.jar + Relics-0.3.5.jar in de repo-root.

### Git-werkdag: skill `git-beheer` + eerste echte commit/push (2026-10-26)
Gebruiker kent git niet; account baaye16 (sms-2FA); repo's blijven PRIVÉ. Skill staat in
`~/.claude/skills/git-beheer/SKILL.md` (commit per werkblok, auto-push, alleen bron, agent mag
privé-repos aanmaken - alles via MC gecheckt). Deze repo: er bestond alleen een "Initial commit" met
alleen README.md, GEEN .gitignore, 104 losse dingen ongezien. Gedaan:
- `.gitignore` aangemaakt VOOR eerste commit: node_modules/.next/target/..., *.jar *.zip, *PRIVE*,
  forgotten players/, headdatabase/, src/lib/db.json, .env*, pluginset-aangepast/, upload-*/,
  output_browser/, _archief/, logs.
- **Secrets uit de bron gehaald** (stonden WÉL in bestanden die mee zouden):
  IekcorbarSiteSync default config.yml (host/username/password) -> TODO's; het echte db-wachtwoord
  als fallback in src/lib/db.js, src/lib/migrate.js en MySQLSync.java -> TODO/omgezet. Echte waarden leven verder in
  .env.local (nooit gecommit) en plugins/IekcorbarSiteSync/config.yml op de server.
  SiteSync-jar 1.15.0 in de zip/root bevat nog de OUDE default-config: prima voor de server (config.yml
  there al bestaat), pas herbouwen als ooit een verse install.
- Tak `master` -> `main` (git branch -M). Eerste commit 0419dc9 (6041 bestanden, geen >50 MB) en
  `git push -u origin main` OK na één browser-login van de gebruiker (GCM onthoudt nu).
- Restant: GitHub default branch staat nog op `master` (verwijderen wordt geweigerd); gebruiker moet in
  github.com/baaye16/iekcorbar-final/settings → Branches → default op `main` zetten, daarna `git push
  origin --delete master`.
- Beslissing (gebruiker, 2026-10-26): het db-wachtwoord wordt NIET veranderd ondanks dat het in de
  git-history van 2 commits opdook - repo is privé en alleen-access, risico aanvaard. Niet meer over zeuren.
- Let op: .env.local zit in géén zip en wordt van git gehouden; plugin-data-db.js noemt nog wel het
  sql1.revivenode.com + db-naam in commentaar (geen ww) - ok voor privé-repo, eventueel anonimiseren.

### Satchel 0.3.0 - BackpackPlus-importer (2026-10-26)
BackpackPlus (3.2.0) staat nog naast Satchel op de server; z'n backpack.db bevat 17 rugzakken maar
slechts **4 met inhoud** (Valerium1411 t2/94KB, kooyer t2 20KB + t3 15KB, Darkmatter86 t1 12KB; de rest
leeg, laatste updates juli-sept 2026). Items staan als base64 Java-serialized ItemStacks per slot in een
JSON-paginastructuur - in Python onleesbaar, in Bukkit gewoon te reconstrueren. Daarom: `BackpackImport.java`
met /satchel importbackpacks [path] (satchel.admin): (1) vindt sqlite-jdbc in libraries/ (door BackpackPlus
zelf gedownload, URLClassLoader, geen shade), (2) leest backpacks-tabel, mapt oldSize 9/27/36/45 -> canvas/
copper/iron/diamond (groeit naar netherite als het niet past, rest items los mee), (3) online direct geven,
offline via pending-import.json + join-outgifte, (4) idempotent: geimporteerde rij-id's in
backpackplus-imported.txt. Oude db wordt ALLEEN gelezen - terugdraaien = BackpackPlus terugzetten. mvn groen,
Satchel-0.3.0.jar in de root. **2026-10-26 UITGEVOERD**: import op de server draaid, database met succes
gemigreerd (4 rugzakken terug als Satchel-bags) en BackpackPlus van de server af - plugin definitief
vervangen door eigen Satchel. Commando/code blijft zitten (idempotent, geen schade bij vaker draaien).

### Relics 0.3.6 - staff-relics onbreekbaar voor spelers (2026-10-26, ochtends)
Gebruiker: "relics die door staff in game zijn geplaatst kan een speler nooit breken, alleen staff."
Gedaan: Relic.veld `staffPlaced` (11e kolom in de chunk-PDC-lijn; oude 10-koloms-regels = false, dus
backward compatible). Bij place(): `staffPlaced = p.hasPermission("relics.admin")`. Bescherming op BEIDE
sloop-paden: hit() (3-klik Break-route, ook creative) én onBreak (basisblok + "vloer weg under floor-relic"
geval; daar blijft de relic nu zwevend hangen i.p.v. te sneuvelen). Action bar: "This relic was placed by
staff - only staff can remove it." Nieuw commando `/relics protect [off]` (relics.admin, lookingAt 24 blokken)
om bestaande geplaatste relics alsnog te claimen/losgave - handig want alles vóór deze build staat ongeflagd.
/remove (radius EN target) breekt wél door - is immers staff-only. Explosions/pistons konden de barrier-basis
al niet raken. mvn groen, Relics-0.3.6.jar in de root.

### Shop-valuta + doneer + legal + Gouw-wiki + site-zip (2026-10-26, "goede plan uitwerken")

- **Derde shop-valuta "Elite coins"** (EliteMobs' coins) naast balance/Gears. `shop_settings.coins_take_command`
  (default `elitecoins take {player} {amount}`, staff-editbaar in het Shop-settingspaneel); coins-payment
  vereist de koper ONLINE (eliteCoins is alleen online uitleesbaar via webstats - offline-read is geen bewijs
  van lege beurs, dus coins-koop weigert offline). Koop-verificatie `before/after`-lezen werkt nu voor beide
  valuta's. Bestanden: src/lib/schema.sql, db.js (getShopSettings/saveShopSettings), api/shop/{settings,items,
  purchase}, src/app/shop/page.js (coins-chip + coins-take-command-input + "Three currencies"-legenda), Sparkles-import.
- **Migratie `shop-coins-migration.sql`** live gedraaid: de live `shop_settings`-tabel was heter dan het schema
  (mist cols `enabled` én mogelijk `coins_take_command`) → eerst #1054 op `enabled`; daarna zelfhelende versie
  (CREATE IF NOT EXISTS + 3 conditionele ALTERs via information_schema + INSERT IGNORE). Hierdoor persisted het
  Shop-instellingenpaneel voor het eerst écht naar MySQL (vóór deze fix zweg het weg in de db.json-fallback).
- **";;" geketende give_commands**: één shop-item kan nu meerdere console-commando's geven (pack = kit + relic
  -roll). Koop-route splitst en stuurt per RCON-verzoek één commando (allereerste deel faalt → queued, latere
  alleen in de receipt, want re-run op join zou wat WEL lukte dubbel geven); SiteSync 1.15.1
  (processQueuedShopPurchases) splitst `;;` ook voor offline→join-bezorging. Shop-UI toelichting + docs/shop-packs.md
  (4 packs common→epic + losse Lucky Dip, prijs-set, giveCommand-templates, CMI-kit-instructies; prijzen/pools
  later aanpasbaar, geen deploy nodig). mvn groen, IekcorbarSiteSync-1.15.1.jar + Relics-0.3.7.jar in de root.
- **Doneer-sectie** `/donate`: vrijwillige PayPal-gift, géén tegenprestatie (om geen koop/dienst te zijn → geen
  herroepingsrecht); PayPal.me-link is een staff-instelling `donate_paypal_url` (General Settings, leeg = knop
  verbergen). Homepagina-melding (privéproject, geen KvK/BTW, shop = alleen in-game-valuta, donatie vrijwillig).
  Terms uitgebreid (secties "Who we are" + "Donations", hergenummerd naar 9), Privacy + footer-modal
  gesynchroniseerd (PayPal-gegevens), datums verzet.
- **Gouw wiki-pagina's**: docs/wiki-gouw/{00-intro,01-gouw,02-commands}.md → seed via scripts/build-wiki-seed.py
  (3 pagina's), registered in import_seed-route + staff "Gouw Land Claims"-Importknop. Na deploy: staff drukt de
  Import-knop op /wiki (vervangt de oude 'Lands'-categorie-pagina's handmatig).
- **Site-zip** iekcorbar_iekje_barend.zip (20MB, 2907 bestanden) gebouwd met node make-zip.js: bevat donate-pagina,
  gouw-seed, SiteSync-1.15.1.jar, Relics-0.3.7.jar (root), .env.local (zodat de live-server de DB-gegevens via de
  zip krijgt - de enige plek waar ze staan), db.json uitgesloten. Plesk: unzip → NPM install → npm run build → restart.

- **Server-log opruiming na paper-135 update (2026-10-26, SiteSync 1.15.3 + Satchel 0.3.1)**:
  gebruiker updatete de server naar Paper 26.2 build 135 en plakte de resterende waarschuwingen.
  Twee eigen bugs gefixt: (1) SiteSync's AdvancementManager registratie was niet idempotent - de
  dubbele load bij startup (onEnable + ServerResourcesReloadedEvent) gaf een muur "already exists"
  want Paper's removeAdvancement weigert stil op sommige builds. registerAll() bouwt nu eerst een
  want-map (key->JSON), laat ongewijzigde registered keys met rust, roept savePlayers() alleen aan
  als er iets wijz én spelers online zijn (dito voor de "manual (plugin-induced) save" waarschuwing),
  en fikt alleen wat echt anders is. (2) load-order cyclus [Satchel, Zukan, Satchel] waar MythicLib
  over viel: Satchel softdependde op Zukan terwijl het alleen ZukanData via ServicesManager
  REGISTREREERT (nooit Zukan-classes aanraakt); Zukan softdependt op Satchel (catalogus-volledigheid) -
  die ene kant weg uit Satchel/plugin.yml, commentaar erbij. Alle poms van 26.2.build.129-stable naar
  26.2.build.135-stable getrokken en ALLE 11 plugins opnieuw gebouwd: mvn groen, verse jars in root
  (IekcorbarSiteSync-1.15.3.jar + Satchel-0.3.1.jar vervangen; de rest zelfde naam, nieuwe build).
  Op de server nog open (niet van ons): MMOCore-quests verwijzen naar world 'world' (die naam bestaat
  niet meer), TechCore ActionBar vs PacketEvents 2.14, PostOffice barrels.yml leeg, ProtocolLib
  "not yet tested" op 26.2. Hert-oriëntatie-opmerking staat als OPEN-item in WILDLIFE-DEV-NOTES.md.

### Backlog / later (gebruikersnotities, nooit vergeten)

- **BedWars-plugin** (2026-10-26, gebruiker: "let op voor later: bedwars plugin - uitgebreid
  mogelijk"): eigen BedWars-plugin voor de server bouwen, UITGEBREID - dus niet de standaard
  one-block-each-arena, maar de volle Create-achtige variant: eigen kits/upgrades, resources-
  generators op Steamworks-rpm's (energie-thema past bij de server), team-shop via SiteSync/Gouw-
  claims, FMM-modellen (koffers, bed-framework, generators), RPM-/wildlife-thema's voor de eilanden.
  GESTART als Hihou bw/-module: etappe 0.3.0 (arena's+teams+join) en 0.4.0 (generators, bedden,
  sudden-death, respawn, spectators, ReJoin) zijn KLAAR en mvn-groen; rest = etappe 0.5.0
  (shop+upgrades+kits) en de theme-upgrades (rpm-generators, FMM-modellen). Zie HIHOU-DEV-NOTES.md.

### MMOItems: alle custom items eigen 3D-model + pack in repo (2026-10-26)

-Gebruikerswish: 'lees de huidige mmoitems uit de backup. alle mmoitems krijgen eigen
models' + 'vergeet geen particles' + 'laat de vanilla items zoals ze horen' +
'beter als die referentie in iekcorbarsitesync plugin komt' + 'miners pickaxe etc..
en al dat soort custom items'. Eersterichting was ValhallaMMO/ItemJoin, uiteindelijke
doel: de ~1079 MMOItems-plugin items (pluginset backup MMOItems/item/*.yml).
- Aangetroffen: 653 unieke 'model: mmoitems:...' referenties, waarvan 652 gedekt
door mmopackNew_rework.zip (stond alleen als losse zip in RPM mixer op de server,
NOOIT in git) + 1 typo (fish/Noptuna -> fish/Nuptuna, item had per ongeluk dubbel-o).
- Pack in repo gehaald: IekcorbarSiteSync/resourcepack/mmo/ (729 models + 729
items-defs + 766 textures). Nieuwe zip-bouwer IekcorbarSiteSync/tools/make_pack.py
-> resourcepack/Mmo-pack.zip; merge-packs.py SOURCES uitgebreid (12e pack) ->
Iekcorbar-pack.zip bevat nu alles, sha1 vast. Deploy: deze pack.zip in mixer,
de LOSSE mmopackNew_rework.zip uit mixer halen (anders dubbelingen).
- Generator IekcorbarSiteSync/tools/make_mmo_models.py (DRAAIT MET PIL, dus ZONDER
python -I): voor elke van de 413 items zonder model: 16x16 procedural pixel-art
(PIL, archetype uit bestandsstam+naam: zwaard/dolk/bijl/korte/hammer/mace/staf/
tome/lute/schild/ring/gem/ingot/dust/vial/scroll/food/book/vissen/4 armor-slots)
en een geëxtrudeerd voxel-model per opaque pixel (dus GEEN 256 onzichtbare
elementen; transparency-skip), display-transform voor vastgehouden items,
gui_light front, EN elke textures-particle-verwijzing naar z'n eigen textuur
(brek-/hitdeeltjes = juist item). Palette uit KEY_PAL-woordgrenzen op naam+ID
(silver/molten/frozen/bone/thor/santa/RED_GEM-...) + MAT_PAL + md5-jitter per
id, dus elk item uniek. Credit 'iekcorbar procedural' markeren de files;
personele orphans (farmer_80_primordial_seed, Nuptuna, shinobi/woodcutter
primordial) worden NU vast aan hun item geknoopt (exacte id-stam).
- Patched configs naar pluginset-aangepast/MMOItems/item/ (model: regels + Nuptuna
fix; upload-map -> plugins/MMOItems/item/, daarna MMOItems herstart).
- NOT done: meerweapon_2..41 losse textures (38 ongebruikte wapenmodellen zonder
naam-info) staan ongebruikt mee in de pack; later eventueel handmatig koppelen.
- NPC/Lands-sporen op spawn: ONDERZOEK OPEN (gebruiker: er hangt mogelijk een
Citizens-NPC of hologram die /lands-commando's gebruikt -> moet naar /gouw;
referentie: E:/backups server/pluginset).

### Lands-sporen gevonden + Tips-of-the-day gezaaid (2026-10-26, zelfde sessie)

- Scan hele pluginset-backup op 'lands/claim/lands': GEEN enkele Citizens-NPC
draait /lands (saves.yml bevat alleen warp-commando's). Wel twee echte resten:
ItemJoin items.yml item 'Pyro_plugins' (NETHERITE_HOE 'Lands Menu' in elke
crafting-grid, 'op: lands' -> onbekend commando sinds Lands van de server is)
en DecentHolograms hologram 'Land_claim.yml' (spawn, pure tekst 'Land Claim
Shop', geen acties). Fixes -> docs/pluginset-fixes/ (pluginset-aangepast/ is
gitignore!): item heet nu '&eGouw Menu' + 'op: gouw menu', hologram-tekst
'Gouw Menu'. Deploy: bestanden over de originals + /itemjoin reload +
/decentholograms reload (of gewone restart).
- tips-seed.sql (idempotent, loop via Plesk zoals shop-coins-migration.sql):
14 tips uit EIGEN plugins + site in daily_tips (Gouw claim/trust/border, Kura
netwerk, Kitchen snijplank/pot, Sakagura vaten+kwaliteit, Steamworks
cinetiek, Wildlife+Field Journal+/zoo, Zukan /zukan=receptbrowser incl.
/recipes op-site, Satchel 6 tiers+upgrades, Relics lore+giftboxen,
achievements, app/push, News&Upcoming, journal) + UPDATE die de oude
Lands-rollen-tip naar Gouw vertaalt. Bestaande 18 db.json-tips blijven staan.
- Tips-draait: dag-van-jaar-modulo actieve tips (api/tips GET), staff beheert
an/uit via Settings > Tips (permission settings_tips).

### Hihou 0.1.0 - nieuwe plugin: kisten + sleutels (2026-10-26)

- Gebruiker wilde een eigen plugin voor treasure/event/vote-kisten (plus lucky + victory) met
  mooie modellen en fysieke sleutels; later Game-Island-spelauto­omatisering (zie HIHOU-DEV-NOTES.md v0.2).
- 5 chest-decordefs op propkit (per kist UNIEKE loot in chests.json, Base64 Bukkit-objecten),
  5 key-soorten (PDC key_type; 1 verbruik; console /ih key = vote-reward-haak zonder SiteSync-code),
  take-only speler-GUI / staff edit-GUI, lucky pool uit config, event-venster, treasure-maps,
  break-hits 100 + /ih remove. Pack tools/make_pack.py: 15 modellen (closed+open per type), alle
  particle-entries; merge-packs 13e bron Hihou-pack.zip. mvn groen, jar in root.
- Commando-tree in HIHOU-DEV-NOTES.md; game-engine types: FINISHP / LASTSTAND / ANSWER / MANUAL.

### Hihou 0.2.0 - Game Island geautomatiseerd (2026-10-26)

Hihou 0.2.0: Game-Island-automatisering - Games.java met 3 modi (race=drukplaat-finish aanwijzen per kijk, last=midden+straal-cirkel met start-gelockte participanten, manual=staff-winner), registry games.json, prijs per spel (none|key|chest -> victory key + teleport naar Victory-kist), GO/win/expire broadcasts, /ih game create|set|start|stop|win|delete|list + tab. mvn groen, jar 0.2.0 in root, HIHOU-DEV-NOTES compleet (testflow + open opties). Eerdere 0.1.0-kistenkant blijft zoals beschreven.

### Garden Fountain 1,5x (2026-10-26, Sakagura 0.3.2)

User-screenshot bevestigde: de fontein laadt perfect (pack klopt), maar op `.scale(3f)` is-ie 3 blokken breed × ~5,8 hoog - te grof naar zin. `GardenDecor` fonteindef nu `.scale(1.5f)` (1,5 breed × ~2,9 hoog, lore "one and a half times life-size"). `fountain()` deeltjes-offsets (y+5.4 / y+2.7, spread 0.75/1.2) waren handmatig op 3x afgestemd - volgen nu zelf `p.def.scale / 3f`, dus een volgende formaatwijziging is één getal in de def. mvn groen, Sakagura-0.3.2.jar in root. Deploy: jar vervangen + herstart; bestaande geplaatste fonteinen hoeven niet weg (transform wordt bij respawn herberekend uit de def). Zie ook "Pack build status" hierboven: build 4 (sha1 3b76e5fd) lost zowel de paarse countdown-borden (bounds) als de paarse crop-perken (crop-definities) op.

### Relics 0.3.9 - brouwerij-props + houten helpers (2026-10-26, avond)

User: "meer relics, voornamelijk props voor de brouwerij; figuranten neerzetten - géén NPC's, dat kost te veel geheugen". Alles door de bestaande Relics-engine (barrier/displays in chunk-PDC, nul entiteiten): 12 nieuwe `RelicType`s in Collection.CURIO (bij de Japanse set) - 9 brouwerij-props (sugi_dama hangende ceder-bal - 8 omlaag geschoven zodat hij niet zweeft, koji_crock, bottling_crate, barrel_stack, steam_pipe (Effect.STEAM), tasting_counter, noren (Effect.SWAY), brew_kettle (LIGHT 9 + Effect.HEARTH), bottle_rack) + 3 houten poppen met **Use.POSE**: drunkard_barrel, greeter_doll, sweeper_doll - rechts-klik wisselt naar het `<model>_b` pose-model (twinkle-vlag; pose blijft behouden: count-kolom in de relic-regen, loadChunk zet twinkle eruit terug). Parket-tabel: palet kreeg indigo/tokkuri/clay_pot.

**Craftbaarheid op config (nieuw, user-wens ivm shop/rewards):** elk type heeft `craftableByDefault` (alle bestaande true; de 12 nieuwe false = shop/event/staff-stock). `RelicsPlugin.craftable(t)` leest config `crafting.<id>` (key wint, anders de default); `recipe()` registreert dan helemaal geen recipe. Standaard-config toont de 12 keys met false - user kan per relic op true zetten. Lucky-dip (`random.` lijsten) neemt nieuwe items alleen op als staff ze zelf in de lijst zet. Achievements/Zukan werken op de enum - nieuwe items verschijnen automatisch (curio-tab bestaat al).

Pack: Relics 132 modellen; merge `BUILD` 4→5 → `deploy/iekcorbar-pack-build5.zip` (sha1 ed102511, 5818 files, audit 0/0 op 2347 modellen). Sweep in merge-packs.py is ingeperkt tot pack-namen (site-zips staan nu ook in deploy/ en mogen NOOIT weg van de sweep). let op server-mixer: het oude bestand heet daar nog `iekcorbar-packv2.zip` - die moet WEG en `iekcorbar-pack-build5.zip` komt in de plaats (dezelfde RPM-regels: mixer = ene onszijds bestand). mvn groen, Relics-0.3.9.jar in deploy/.

### Backlog: suggesties 26-10 avond (nog niets van gekozen)

User vroeg om suggesties ("sta overal voor open") en besloot toen: alles opsparen tot de volgende
deploy-batch (zie `deploy/TE-DEPLOYEN.txt` + AGENTS.md "Deploy-batching"). Piekvolgorde zoals
besproken: (1) BedWars-arena bouwen om Hihou 0.4.0 eindelijk te testen, (2) "Brouwerij-Backdrop"-shop-pakket
(`;;` give-commands; de 12 nieuwe relics zijn shop-stock), (3) deploy-manifest/checklist = nu TE-DEPLOYEN.txt,
(4) december: Xmas Town-plein + `/relics secret` rond + countdown-board naar de opening, (5) brouwerij-gevelwedstrijd
met noren+sugidama als signage, (6) seizoenswissel in de shop-relics, (7) GEBOUWD (0.3.10, idd 26-10):
`Effect.DOLL` - poppen maken af-en-toe geluid + spats (dronkaard: fles-klink + happy-villagers, zwaaier:
wood.chime, veger: grass.break), ~1x/4 min per pop, alleen bij spelers binnen 24, (8) update-notities op de site uit de Discord-posts, (9) tick-times in de gaten houden
bij perken vol displays, (10) community "Guild Hall"-project met Kura/Steamworks/fontein. Niets hiervan is gebouwd;
wacht op keus van de user.


### Steamworks 0.3.16 - crafting 1-op-1 uit Create 6.0.10 (2026-10-26, nacht)


User: "ik mis veel crafting recipes, gebruik dezelfde als Create" + letterlijk dezelfde grondstoffen.
Bron = de recept-JSONs uit de Create-jar zelf (data/create/recipe/crafting/, gedumpt met een script).
Nieuwe basisketen als MaterialType + FMM-modellen (make_models.py, 12 bestanden naar
deploy/fmm-steamworks/): ANDESITE_ALLOY (2x2-diamant andesite + 2 iron-of-zinc nuggets), ZINC_NUGGET
(ingot 1->9), ANDESITE_CASING / BRASS_CASING (Create smeert het in-world op een gestripte stam; bij
ons tafel-recept alloy/brass-ingot + stripped log), WHISK (alloy + 5 iron plates), PROPELLER (alloy +
3 iron plates). Alle 30 PartType-recepten naar Create-patronen (shaft A/A x8, cogs als kolom,
gearbox-kruis, gauges compass + stress-conversie van de speed-gauge, millstone/press/mixer/fan S-C-I
kolommen, kettle=Create basin 5 alloy, pipe SCS x4, tank plaat/vat/plaat, drill A-I-C, saw plate-I-C,
sprocket=casing+3 nuggets, windmill wool/slab/shaft, steam engine gold_plate/alloy/copper_block,
crushing wheel 3x3-vertaling van de 5x5 assembly). Eigen blijft met commentaar: STATION/SIGNAL/
PUMPJACK/REFINERY/ARM/BELT (geen tafel-recept in Create)/STEAM_BOILER/locomotief+wagens.
machines.yml brass naar Create 1:1 (servers met eigen machines.yml moeten die regel zelf bijwerken).
make_models.py: uuid4 -> deterministische uuid5 - bbmodels one-time changed, daarna byte-stabiel.
Site recipes-data.json geregeneerd (steamworks 168). mvn groen; jar + 12 FMM-bestanden in deploy/ +
TE-DEPLOYEN.txt. Batch 2 (sugges, niet gekozen): chute/funnel/weighted ejector/mechanical pump/
fluid valve/stoomfluit met Create-recepten + eigen modellen.

### Steamworks 0.3.17 - batch 2: item-logistiek + fluidie (2026-10-26, nacht)

7 nieuwe PartTypes met Create-recepten uit hun jar: CHUTE (glijbaan A/I/A iron plates x4) en FUNNEL (alloy+dried kelp x2) draaien mee in de belt-loop (Belts.direction/tick familie-check: belt/chute/funnel geven elkaar door), funnel zuigt items uit de kolom boven zich (mundbox y+1.5..2.6, alleen powered). DEPOT (alloy+casing) is een schap: items die eenlijnig op een depot leveren blijven erboven staan; EJECTOR (gold plate+depot+cog, throwt naar facing) pakt items van eigen dak OF van het depot waar hij naar kijkt en werpt ze weg (vx .55/vy .38, cooldown 16t, block.tripwire.attach). PUMP (cog+pipe, static+spin-as) = tank-api op een pijpleiding: rechtermuis met canister door het hele netwerk heengaand/vullen via Oil.pumpInteract. VALVE (iron plate+pipe) geleidt vloeistof ALLEEN zolang zijn netwerk draait (Oil.conducts-check in de tanks-BFS) - een afzetklep dus. WHISTLE (gold plate+copper) = rechts-kik toet-je (bell.use+resonate, alleen zonder blok in de hand). Kinetics: belts-lijst uitgebreid met chute/funnel + nieuwe ejectors-set (aan/uit bij add/remove), canRotate/turnFace (facing-groep + horizontale groep), ports: ejector=facing-opposite, depot/whistle not-kinetic. Visuals/PartListener/recipes/KNode(ejectUntil)/machines: zie commits. FMM: 7 nieuwe models (make_models.py, funnel-depot-ejector-pump-valve-whistle-chute) - nu 26 nieuwe files in deploy/fmm-steamworks. Site-parser build-recipes-data.js fixte een echte bug: //-commentaren AAN END van regels lieten statements aan elkaar lijmen (alle 0.3.16-comment-rijen werden overgeslagen) -> nu strip-anywhere //; steamworks 183 recipes. mvn groen. Nog te controleren op de server: afstelling worp-afstand en funnel-vs-ketel interactie na deploy.

### Steamworks 0.3.18 - interactief Ingenieurshandboek + wiki-sync (2026-10-26, nacht)

Manual.java: /sw manual geeft een WRITTEN_BOOK met het engineers_manual FMM-model wiens pagina's
BIJ ELK OPENEN opnieuw gebouwd worden: klikbare inhoud (change_page), [recipe]-knoppen per onderdeel
(run_command /zukan), en een voortgangs-checklist in de player-PDC (steamworks:manual_progress,
"part:PSC;..."): O geplaatst (PartListener.place), O gezien-draaien (showInfo met speed!=0),
O gecraft (CraftItemEvent). Quick-Start bladzijde, Materials/Tools-hoofdstukken, tip per
hoofdstuk. API-les 26.2: geen WrittenBookMeta meer -> DataComponentTypes.WRITTEN_BOOK_CONTENT
builder, Player.openBook(item) 1-arg, ClickEvent.runCommand camelCase, result via
e.getInventory().getResult(). Handboek is 13 bladzijden (max 16: 7 hoofdstukken + materials +
progress), PartListener-intercept voor elke rechtsklik.

Wiki: scripts/build-wiki-recipes-md.py genereert 08-recipes.md uit recipes-data.json (de hand-tabel
was drift-gevoelig - was precies het probleem van vanavond; secties: Materials, Power, Machines,
Moving items, Karakuri, Trains, Oil, Tools; conversie-recepten verschijnen als tweede regel).
04: Chute/Funnel/Depot+Ejector/Whistle-secties, 07: Pumps and Valves, 01+03: Create-recepten
(Shaft nu alloy-ketting, brass 1:1), 00: /sw manual. Seed opnieuw: steamworks 59 pagina's.
De polish-werkwijze staat vastgelegd als skill 'steamworks-polish' (~/.claude/skills/).
mvn groen; Steamworks-0.3.18.jar in deploy/ (0.3.17 ervoor was nooit live - vervangen).
Nog open op deze missie: ~~Steamworks-Additions losse jar + interactieve handboek test in het spel~~ — BEIDE AFGEHANDELD (29-10): de Additions-losse-jar draait al als 0.1.2, en het Engineer's Manual is statisch afgeaudit (37/38 onderdelen in de hoofdstukken - alleen CUSTOM ontbreekt, bewust; change_page is 1-gebaseerd en de indexering in de code klopt; /zukan-multi-word-ok; engineers_manual FMM-model staat in plugins/FreeMinecraftModels/models/steampunk_iekcorbar/books/ op de server; 12 pagina's, ruim binnen het limiet). Rest = één minuut in het spel: /sw manual →klik Contents→klik hoofdstuk→klik [recipe]→Zukan open→plaats tandwiel→heropen→O'tje staat.

### Steamworks Additions 0.1.0 - de eerste losse addon (26-10 laat op de avond)

User: addon-content = "Sfeer: stootklok + messing details". Nieuwe Maven-module
`SteamworksAdditions/` (com.iekcorbar.additions, ns "additions", softdepend Steamworks/FMM): draait
op de GEDEELDE propkit-engine (Decor/DecorDef via sync.py, net als Garden/Zoo/Kitchendecor).
Drie props: steam_clock (2 hoog, tikt elke in-game uur op de bell+resonate met toonhoogte per uur,
zwevend TextDisplay-tijdsbord +2.45 boven de grond, dubbele slag om 12:00/00:00; de engine tickt alleen
met spelers binnen 24 - dus geen kosten nachts/ver), brass_railing (connect-mask N/E/S/W,
ijzeren stijlen + messing sporen), vent_grille (wall(), koper kader + verdigris lamellen).
Commando's: /sa list | /sa give <speler> <prop> [aantal] (additions.admin). Config props.break-hits: 2.

Engine-uitbreiding: propkit Decor.Prop krijgt `Runnable onRemove` (breek-haak, aangeroepen in
breakProp na de save) - de klok-hangbord gebruikt die hook om netjes op te ruimen; alle vier andere
engine-plugins opnieuw gebouwd (byte-verschil door sync, gedrag gelijk) en de verse jars staan in
deploy/. Propresourcepack: SteamworksAdditions/tools/make_additions_pack.py (propmodels, 18 modellen:
klok + reling-mask 0..15 + rooster), ns "additions".

Site/pack: merge SOURCES +Additions-pack.zip, BUILD 6 -> iekcorbar-pack-build6.zip
(sha1 18b33f6a, 15,42 MB, 5855 files, audit 0/0 op 2365 modellen). Wiki/docs KLAAR (20:50): docs/wiki-additions/ (intro +
klok + reling/vent) - seed 'additions' 10 pagina's + importknop op /wiki; addons-tab op /recipes
(build-recipes-data.js parseert de DecorDef-*.recipe-ketens uit Additions.java, 3 recepten);
/steamworks-pagina: sectie 'What landed on 26 October' + versie-label 0.3.18/0.1.0; site-zip
20:49 in deploy/. npm build groen. Steamworks-polish skill is bijgewerkt met de addon-pijplijn.
mvn groen (alle vijf). SteamworksAdditions-0.1.0.jar + pack build6 + vier herbouwde engine-jars in deploy/.

### Eerste-deploy-bugfixes + dust-keten: Steamworks 0.3.19, Zukan 0.2.7, Additions 0.1.1 (26-10 diep in de nacht)

Gebruiker deployde 0.3.18/0.1.0/build6 en meldde vier bugs + een nieuwe wens. Alles in één batch (0.3.19 was nog niet live):
1. **Zukan generiek (0.2.7)**: 'providers'-allowlijst VERVANGEN door "registratie ís de opt-in" - élke
   Supplier-aanmelder wordt gevraagd, ongeacht naam/versie (config: excluded-providers, opt-OUT).
   Entry krijgt origin + addonOf; `@stoom` matcht nu ns, herkomst-PLUGNA en ouder. Een provider die
   (soft)depend op een andere provider is automatisch ADDON davon: items vallen onder @parent én
   tonen de lijn "Add-on content of <parent>". Echte PluginDescriptionFile.getSoftDepend() heet met
   hoofdletter D; getRegistrations() geeft Collection (geen List).
2. **Additions 0.1.2**: eigen ZukanData (Supplier-service, alle decor.allItems()) - props verschijnen
   nu in Zukan onder @steamworks (softdepend ontdekt) met addon-vermelding. Plus klok-functioneel
   gemaakt: HH:MM-plaat op ECHTE tijd (config clock.time-source real|game, clock.timezone), rechtsklik
   = tijd+volgende slag+lucht via .use(), sounds.clock-bell en clock-night-rest schakelaars,
   /sa reload, eigen advancement-tab (3 props hidden "Hear The Bell" + challenge alle drie).
3. **Windmolen**: nextInt(8) + volume 0.22 (was 2/0.45 per 30t); nieuwe config sounds.machine-volume
   (schaalt álle machinegeluiden incl. de direct afgespielde mill/hammer/crusher/engine/arm, 0 = uit).
4. **Millstone**: output die geen zijcontainer vindt valt naast de steen op de grond (was: onzichtbaar
   opgeslagen tot 9 stacks); vastgelopen niet-maalbare inputs (uit oude saves) worden uitgespuwd;
   Belts.deliver duwt alleen nog maalbare items erin (plugin.millstones().find-check).
5. **Opstart-stotter**: de waitForModels-sweep (alle chunks + entities in één tick) is nu een batch
   van 8 chunks/tick met eigen timer; entity-cleanup en trains.load() blijven in de eerste tik.
6. **DUST-keten (wens gebruiker, Create-doublering)**: 5 nieuwe MaterialTypes (iron/copper/gold/
   zinc/tin_dust, stofhopen in make_models.py, helemaal onderaan build() zodat alle uuid-counters
   byte-stabiel blijven --preview3d audited), crusher-regels raw->2 dust in machines.yml,
   smelt(dust)->ingot furnace+blast (PartItems smelt-herbouw met Material-out overload).
   Site: build-recipes-data.js parseert nu ook de smelt(...) calls (195 steamworks-recepten,
   +5 crusher +7 smelt); wiki 03-processing + 06-ores bijgewerkt (incl. fix: fan smelt geen eigen
   items); 08 + seed 59 pagina's; /steamworks pagina: crushing-card + Create-true-card + label 0.3.19/0.1.1.
   site-zip 22:03 in deploy/. FMM: 10 dust-bestanden naar deploy/fmm-steamworks/ (36 totaal).
   ! machines.yml op de server moet weg/ververst voor de crusher-regels - in TE-DEPLOYEN gezet.
mvn groen (Steamworks/Zukan/Additions); jars 0.3.19 + 0.2.7 + 0.1.1 in deploy/ (0.3.18-1.0-buildjes weg).

### Steamworks Power 0.1.0 - machine-API in de basis + tweede addon (26-10 late in de nacht)

Gebruiker: "basis = volledig gratis-product, addons = extra's". Derde Maven-module `SteamworksPower/`
(com.iekcorbar.power, jar-afhankelijk van Steamworks' publieke API, geen kopieerwerk).
**De API (in de gratis basis, commit b8238ce):** package com.iekcorbar.steamworks.api met
CustomPart (id/lore/ports/portFactor/rotatable/initialAxis/capacity/impact/generatedSpeed/visuals/
interact/tick/onPlace/onRemove/saveState/loadState) + CustomNode-view (geen KNode-lek) +
CustomPartRegistry (register/byId/partOf/item/steamworksItem("material|part/<id>")/unregister).
Basis-kant: PartType.CUSTOM als doorgeef-luik; ExtParts (registry, items via displayItem) + ExtNode;
Kinetics (customs-set, ports/portFactor/canRotate/capacity/impact-afslagen, tickCustoms per tick met
gegenereerde-snelheidsverversing per 20 t), Visuals (custom rollen SPIN/STATIC via visuals()),
NodeStorage ("CUSTOM:<id>" + extKey-blob, onbekende addon = node netjes overgeslagen + waarschuwing),
PartListener (placeExt incl. BlockPlaceEvent, interact-ketting, pick-block), steamworksItem/unregister,
plugin.whenModelsReady() publiek (addon-recepten registreren pas als FMM ze kent), onRemove bij
breakNode én unloadChunk; alle PartType.values()-lussen (give/list/Zukan/models/trader) filteren CUSTOM.
**De machines:** WaterTurbine (water langs de zijkanten = 12-15 RPM x 64 Tq, doorstroom-regel),
MechanicalPress (plaat bovenop, stampt config-recepten: 9 zinc_nugget->ingot, honingfles->fles+raat,
klei->4 baksteen, hooi->leer, 4 sneeuw->2 packed ijs; rotatie van onder), StockTicker (pure container-
lezer, zwevend TextDisplay met de twee grootste hopen, alleen bij spelers in de buurt, onRemove ruimt op).
**Modellen:** SteamworksPower/tools/make_models.py IMPORTEREET de core-helpers en herovert alleen de
uuid-teller (mm._c = count(90000)) - core-bestanden blijven byte-stabiel; 3 x json+bbmodel naar
deploy/fmm-steamworks/ (42 bestanden), preview3d-.sheet bekeken en goedgekeurd.
**Site/wiki:** build-recipes-data.js krijgt een power-parser (shaped()-regex op PowerPlugin.java + press-
blok uit config.yml -> 8 recepten, tab "Steamworks Power" verschijnt automatisch via data.plugins);
preview3d-render naar public/recipes/power/ (3 png's) + power toegevoegd aan de pics-lijst van
make-recipe-icons.py; docs/wiki-power/ (intro/turbine/pers+ticker) -> seed 10 pagina's + importknop +
route-seed; /steamworks-pagina: Power-kaart + versie-label 0.3.19 + Additions 0.1.2 + Power 0.1.0.
__pycache__/ opgenomen in .gitignore. mvn groen (core + power); jar + site-zip 23:26 in deploy/.

### Blockbench-MCP + FMM-sweep + Wildlife-loopfix (26→27 okt, nacht — gebruiker sliep, onbeheerd)

**Blockbench-MCP reconnect.** De `blockbench` MCP (remote, `localhost:3000/bb-mcp`, Blockbench-plugin
mcp.js v1.10.1) faalde gisteravond met ECONNREFUSED: OpenCode probeerde om 23:37, de plugin ging pas
23:39 aan; `blockbench-headless` (npx uit github:jasonjgardner/blockbench-mcp-plugin) liep bij de
allereerste start tegen de timeout aan (download te traag). Na een herstart van OpenCode (met
Blockbench draaiend) connecten ze allebei. De headless `--root` is verzet van `fmm-models` naar de
PROJECTROOT zelf, zodat één sweep alle bomen reikt (Steamworks/, SteamworksPower/, Wildlife/ én de
root-verzameling). Let op: poort 3000 is ook de Next-dev-poort - never `npm run dev` met open
Blockbench-MCP. De root-verzameling `fmm-models\` (803 bbmodels: 599 decor-varianten + 71 elitemobs +
133 wildlife) bevat inhoudelijk dezelfde dieren als `Wildlife/fmm-models` maar dan uuid-verschoven.

**Groene sweep (254 + 670 modellen, via bbmodel_validate/info/animations).** Steamworks(118)+Power(3)+
Wildlife(133): 0 fouten, 105 helemaal schoon; alle 121 ge-exporteerde sw/pow-jsons netjes binnen
-16..32 (de enige bron-bbmodel die de grens raakte - windmill_sails ±23 - exporteert als -15..31,
klopt dus). Decor(599)+EliteMobs(71): 0 fouten; de 281 waarschuwingen (interpenetration/floating/
degenerate) zijn stijl-laag (dunne ogen, elkaar snijdende blokstukken) - niet aangepakt.
EliteMobs blijft links liggen voor bewerking (derde-handsbron, geen generator hier).

**Echte bug gevonden en gefixt - verbroken walk-loops (40 keys buiten clip):**
1. `stilt_walk` (kraan 1,5 s, struisvogel 0,85 s): de right-step begon op halve periode maar liep
   door tot 1,5× de clip - de poot werd NOOIT neergezet binnen de loop en klapte bij elke
   lus-herstart terug (de kraan "liep raar"). Now: elke poot krijgt eigen halve cyclus (lift-houd-
   plaats-rust in 0,5P), body dip bij beide landingen, kop-knik over de hele cyclus. Bewezen met
   sample_pose: OUD stond poot_r op -14,4° bij 1,45 s (midden in de lucht bij de naad), NIEUW 0°.
2. Olifant-oren in walk: `wave(8, 3.0, 6)` tegen een clip van 2,0 s - de laatste derde van elke
   oorslag werd nooit afgespeeld en de oren sprongen per stap naar voren. Nu meebewegen binnen de
   clip (golf over 2,0 s, rechts een kwartslag uit fase - een olifantensloeg klopt nooit synchroon).
3. `make_animals.py` uuid4 → uuid5-teller (zelfde trick als Steamworks' make_models.py): éénmalige
   churn over alle 133 modellen in beide bomen, daarna is elke herbouw byte-stabiel en blijven de
   Wildlife/ en fmm-models/ bomen identiek. Determinisme bewezen: twee runs byte-gelijk.

Beide bomen gegenereerd (`python -I tools\make_animals.py fmm-models\wildlife` en datzelfde pad
`..\fmm-models\wildlife`), keycheck 0 keys-buiten-clip over ALLE 1078 bbmodels,
bbmodel_validate_animations over de zes aangepaste families: keyframe_timing overal weg (rest:
losse sub-pixel ground_sink in death/idle, bewust niet aangepast - dat is de beenderen-draai van
90° bij sterven, geen loop-breuk). Echte inhoud: precies 12 modellen (kraan/struisvogel/olifant
× zeldzaam/baby); 121 overige alleen uuid-churn. Artefact: `deploy/fmm-wildlife/` (136 bestanden,
naar `plugins/FreeMinecraftModels/models/wildlife/` + /fmm reload).

### SSH-toegang VPS + eerste live-versiecheck: Steamworks 0.3.20 geforceerd (27-10 middag)

Gebruiker gaf root-SSH-toegang tot de VPS (82.165.250.99, Pterodactyl-container) voor
versiechecks/logs/configs - regels vastgelegd in nieuwe skill `server-toegang` (niks
verwijderen/herstarten/zonder approval, DB lezen mag maar altijd melden, geen
speler-identiteit, optimalisaties alleen als voorstel). Toegang ingericht via eenmalige
paramiko-bootstrap (pip) die een ed25519-sleutel (`~/.ssh/iekcorbar_server`) in
authorized_keys zette; wachtwoord-bestand uit TEMP verwijderd; gebruiker geadviseerd het
root-wachtwoord te roteren. Container-ID's zijn vluchtig: de skill zoekt hem op de
aanwezigheid van de eigen jar.

**Wat de check opleverde (realiteit ≠ "alles live"):**
1. Negen "open" jars van 26-10: Gouw/Hihou/SiteSync/Kitchen/Kura/Sakagura/Satchel/Wildlife
   + Zukan/Additions allen correct live, modellen 242+136 op hun plek, pack build6 in de
   mixer, machines.yml VERS (5 dust-regels), site extern OK (Power-tab, 195 recepten).
2. **Relics draait 0.3.8** - de 0.3.10 uit deploy is nooit overgezet.
3. **SteamworksPower laadt NOOIT** (log 09:43:13: NoClassDefFoundError CustomPart): de
   werkende Steamworks-0.3.19-jar (563.552 B, sha1 f0bb8b55) komt met GEEN enkele lokale
   build overeen en mist de api-package. De "oude 0.3.19" (21:56) vs de api-build
   (23:19) deelden één versienummer - precies de bouw-artefactenregel die dit verbied.
   Herstel: pom 0.3.19->**0.3.20**, mvn groen, jar verifyd (4 api-classes, sha1 586a3687)
    -> deploy/. Gebruiker goedgekeurd: beide jars via scp+docker cp geplaatst (sha1's
    aan beide kanten gelijk), de verkeerde 0.3.19 + Relics-0.3.8 van de server af, en de
    herstart (11:07) bevestigt het: Steamworks v0.3.20 enabled, Relics v0.3.10 enabled en
    **SteamworksPower v0.1.0 enabled** - de addon draait nu eindelijk echt.
Ook geregistreerd uit de log: "Minecraft rejected advancement steamworks/sprocket"
(onschuldig want Zukan-catalogus draait door, maar volgt bij eerstvolgende sw-klus).
En twee bij-effect-vondsten die de restart-log blootlegde (agent-reparaties, gemeld op
deploy-lijst punt 5): SiteSync/config.yml had 3-spaties indent onder `database:` ->
"Cannot load" bij élke start sinds 26-10, DB-sync draaide op defaults; Sakagura/
recipes.yml had een half kapot staff-recept "bloody _mary" -> heel bestand onleesbaar.
Beide syntax-fouten minimaal hersteld (sha1-verified teruggeplaatst, wachtwoord/wp's
 intact; parse-getest met PyYAML). Herstart 27-10 11:22 maakt het rond: log geen
"Cannot load" meer, SiteSync enabled op echte config, Sakagura "Loaded 38 recipe(s)".
De twee staff-drankjes (bloody_mary, zombiezestzinger) bestaan NIET in
tools/make_recipes.py -> promote-besluit nog aan gebruiker.

Besluit productievorm (26-10, gebruiker): FMM-modellen + pack worden NU NOG NIET in de jar gebundeld.
Huidige aflever-lijn blijft: assets apart via deploy/ (fmm-steamworks-map + pack in de mixer).
Assets-in-jar (onLoad-extractor met versiestempel, pack.auto-install, release-bundel, machines.yml-
migratie) is wél het plan en staat op de lijst voor EIND-productie (1.0.0-release-batch). Tot die tijd
gewoon doorgaan als altijd - geen tussentijdse helft-overstappen.

Verkoop-model (26-10 nacht, gebruiker - BELANGRIJK): de gratis/premium-grens (basis = gratis voor
andere server-eigenaren, addons = betaald) geldt ALLEEN bij uitlevering naar buiten. Op de iekcorbar-
server zelf draait het HELE pakket - daar is geen koop-systeem. Concrete regels: (1) de basis-code mag
nooit een addon nodig hebben (runtime-scheiding blijft, anders breekt de vrije release), (2) wél mag
de basis addon-content laten zien wanneer die plugin toevallig loaded is (Bukkit.getPluginManager()
.getPlugin("SteamworksAdditions") != null - runtime-check, geen compile-afhankelijkheid), (3) zet geen
kunstige drempels in de plugins zelf uit angst dat iemand gratis stuff krijgt - de verdeling zit in de
release-bundels, niet in de code. (4) Het verkoop-model zelf NOOIT noemen op de website, in de wiki,
in de manual of in game-teksten: publieke pagina's beschrijven alleen wat iets dóet, nooit dat iets
gratis/betaald is of dat addons "premium extra's" zijn. Ook geen zinnen als "losse addon" die de
gedachte losweken - gewoon "Steamworks Additions toegevoegd: klok, reling, rooster".
(5) Gebruikerswish 26-10 nacht: een BRIEVENBUS met echte post tussen spelers. UPDATE 27-10
middag, eigenaar BESLIST: er komt GEEN mailsysteem, ook voor onszelf niet. De wens is
ingevuld als zuiver decoratief Relic: de "Pillar Box" (Curiosities) - een Engse rode
zuilbrievenbus in steam-stijl (klokwerk drukmeter, zacht stoompend zijventiel), zonder
elke functionaliteit. Het eerdere plan (publieke decor-bus + interne mail-variant met
config-gate, mail-data in plugins/SteamworksAdditions/) is van tafel; de addon levert dus
ook geen bus.

### Staff-drankjes gepromoveerd naar de generator + dode website-seed verwijderd (27-10 middag)

Gebruiker besloot (TE-DEPLOYEN-punt 1): bloody_mary + zombiezestzinger blijven. Ze zijn
opgenomen in Sakagura/tools/make_recipes.py (OWN-lijst, 19 eigen drankjes), dus élke herbouw
van recipes.yml/wiki bevat ze nu. Uit de live server-config gelezen (SSH, alleen gelezen) en
met vergelijking bevestigd: gegenereerd vs server = 38 vs 38, alleen de drie BEWUSTE ver-
schillen: ingredienten "red_dye/potato" naar house-stijl "Red Dye/Potato" (dezelfde keys),
kleur "00800" -> "008000" (vijf cijfers was een typefout; de parser viel stil op WHITE terug)
en lore-tag "<00800>" -> "<dark_green>" (ongeldige MiniMessage-tag, werd letterlijk getoond).
"RED" bij bloody_mary is GEEN typefout: benoemde kleuren zijn house-stijl (absinthe/vodka/
hot_choc gebruiken op de site ook GREEN/WHITE/ORANGE). RecipeTest (offline, javac/java met
paper-api uit .m2): alle 38 groen, "ALL PASSED". pom 0.3.2 -> 0.3.3 (ingebedde default-
recipes.yml wijzigt mee; jar sha1 1691720e, ingebed bestand byte-gelijk aan bron).

Trap na-lopend ontdekt: make_recipes.py schreef ook src/lib/brewing-seeds/sakagura.json —
voeding voor de "Import Sakagura recipes"-knop op /brewing, maar die pagina is op 25-10
VERWIJDERD uit de repo (SAKAGURA-DEV-NOTES: brewing-seeds/ stond in de WEG-lijst). De
generator liet de dode map dus bij elke run herstaan; blok + map verwijderd. Levende
receptenlijst is de wiki (docs/wiki-sakagura/03-recipes.md, nu 38 drankjes) — wijzigingen
daaraan + src/lib/wiki-seeds/sakagura.json reizen mee met de eerstvolgende site-zip van de
gebruiker. Live-server external recipes.yml bevat nog de twee typeouten: vervangen door
deploy/Sakagura-0.3.3-recipes.yml (sha1 eb53ce09) + /brew reload, jar 0.3.3 inwisselen is
alleen nodig voor de default op verse installs - gebatcht in TE-DEPLOYEN.

### Valentine's-collectie (Relics 0.4.0) + het /power-mysterie en de TechCore-lader (27-10 middag)

**Gebruikersklus: "maak een stuk of 20 valentijns-relics, ga je gang."** Nieuwe
collectie `VALENTINE("Valentine's")` in `RelicType` - 20 relikwieën in dezelfde stijl
als Xmas Town/New Year: hartballon (HOP-bob), roos onder stolp, liefdeslot-rail (wall),
hartboog (nieuw effect HEART: zwevende particle-harten, schaal 2), cupietstandbeeld,
wensput (nieuw gebruik WISH: 1 gold nugget -> plons + `relics:valentine.wish` + harten,
cooldown, achievement "wish"), zwanenlantaarn (FLAME), banier (SWAY), hartlantaarn
(TWINKLE: aan/uit = helder/dof-zwart kern, floor/wall/hanging + _b), teddybeer (POSE:
armen-neer/omhoog), snoeppotten (nieuw gebruik JAR: rechtsklik = Chocolate Heart pakken,
reffelen met erin, 3 statussen vol/half/leeg via count), boeketknaller (hergebruikt
CONFETTI), rozenbak, zangvogelkooi (nieuw gebruik BIRD: 5 chirps oplopend pitch via
runTaskLater + hartje), hartstruik (schaal 1.6), toverdrankplank (BUBBLES), klokwerk-hart
(PULSE: kloppend + egg_sac.heartbeat), minnenbank (SEAT), hart-glas-in-lood (wall),
Amore-automaat (nieuw gebruik VEND: nugget -> 2 harten, geen wisselgeld). Nieuwe
treat CHOCOLATE_HEART (ABSORPTION + hartparticles bij eten; labelketen in
Treats.item() uitgebreid; ordinaalletzen: Valentine's staat VOOR SODA_BREAD-getest).
Achievements: + "valentine" (collection-scanner pikt c:valentine automatisch op) +
verborgen... nee: gewoon zichtbaar "wish"; collector.allOf uitgebreid met valentine.
Zes nieuwe icoon-items gecheckt tegen de 26.2 Material-enum - ik zag toen dat
`sprocket` al jaren het verdwenen icoon "chain" gebruikte (het item heet nu
iron_chain; vandaar de "rejected advancement" uit de herstart-log): core-fix
`chain` -> `iron_chain`, Steamworks 0.3.20 -> **0.3.21**.
Modellen: 28 stuks in `Relics/tools/make_pack.py` (nieuwe palette kleuren rose_red,
rose_deep, blush, lace_white, rose_gold, rose_leaf, cream_pink, well_stone, water_dark;
naam-gebaseerde palette-celindices blijven byte-stabiel; heart_pix() is de nieuwe hart-
helper). preview3d-render gecontroleerd - let op: de preview-camera staat op de
achterkant (+x+z-hoek), dus voorkant-details (amore-venster, bannerhart) lijken "leeg";
180°-gedraaid nagekeken en toen allemaal prima. Eerste renderpogingen verbeterd:
ballon-hart silhoeet, lantaarn-kern zichtbaar in de kloof tussen de lobben, pot
voorwand open (glans-geel glas is dekkend!), amore-harten OP het glas, topiary smaller
crown. Pack: make_pack -> 161 modellen, Relics-pack.zip; merge-packs BUILD 6 -> **7**
(5917 bestanden, 15,48 MB, sha1 06ec729b), audit 0/0. De bestaande .ogg's kregen same-
lengte andere bytes: de ffmpeg-7-build hercodeert identical-wavs (libvorbis container),
geluid is hetzelfde - geen probleem, wel wennen in git-diff. Relics 0.3.10 -> **0.4.0**
(jar sha1 d212d498), Power 0.1.0 -> **0.1.1** (hoofdletter-nachalág bij give, jar
60b1140e), Steamworks **0.3.21** (00ecfc58). Alle drie in deploy/, batch in TE-DEPLOYEN
(punten 3 + 4).

**/power give "werkt niet" - twee oorzaken, geen van beide onzé code.** (1) De speler
typte /power; ValhallaMMO (betaalde plugin, zelfde maker als EliteMobs/TechCore)
registreerde /power eerder (enable 11:23:07 vs onze 11:23:29) en zijn ProfileCommand
crasht op vreemde argumenten (NPE "player is null" - hun bug). Onze command-blootstelling
is de ALIAS: **/pwr give <speler> <machine>** werkt; getPlayerExact was hoofdletter-
gevoelig (daarom de 0.1.1-fallback equalsIgnoreCase). (2) Bij het uitzoeken bleek: de
Live-jar van SteamworksPower is 263,9 KB i.p.v. 28,9 KB, en bevat naast onze 10 byte-
identieke klassen 32 vreemde: bStats-metrics + Valhalla/TechCore-hookklassen (HookManager,
Economy_CMI, Lands met dummy-Spigot-premium-check-URL) + geobfusceerde namen (Dgduf,
yszthvbepghksbpl, Injector, BukkitLoaderPlugin), en ONZE PowerPlugin.onEnable is omge-
bouwd naar `HookManager.inject(this); _$oe$_()` (de oude onEnable als _rename_).
Eerst leek dit een gevaarlijke herpackage - de waarheid is minder spannend maar wel
belangrijk: **EliteMobs/TechCore (athlaeos) doen dit zelf, op elke serverstart, tegen
elk jar in plugins/** (hun "compatibility loader" die ze in andere jars spuiten zodat
elke plugin hun API kan zien). Bewijs: alle 13 eigen jarren op de server hebben exact
DEZELFDE 31 vreemde klassen + dezelfde +230 KB, en de jar-mtimes zijn stélsekondes na
onze docker cp's (11:07:24 voor de zojuist geplaatste schone 0.3.20/Relics 0.3.10!) -
de injector draait in de levende server en herschrijft BINNEN SECONDES, ook net-
geplaatste jarren. Payload bevat géén exec/netwerk/credentials-ooit - alleen hun eigen
hooks en bStats. Dit lost ook het ochtend-mysterie op: "de werkende 0.3.19-jar die op
geen enkele lokale build leek" was gewoon onze 0.3.19 + 225 KB loader-afval.
Gevolgen vastgelegd in TE-DEPLOYEN: (a) geen enkele live-jar-sha1 zal ooit gelijk zijn
aan een lokale build - verifieer op KLASSE-niveau (onze eigen klassen byte voor byte vergelijken, script
in deze sessie gebruikt) niet op jar-sha1; (b) "schone herbouw van de Power-jar" is
niet nodig als security-actie (0.1.1 is wél nuttig: de give-lookup); (c) overweeg op
termijn de EliteMobs/Valhalla-familie te vervangen of te weren als dit blij-
vend gedoe geeft - voorlopig: accepteren, het raakt onze functie niet.

### Besluiten: /swpower-hernoeming + de Pillar Box in plaats van het mailsysteem (27-10 middag)

Gebruiker besloot (terwijl hij in het tuincentrum was) twee losse draden: (1) geen
overlappende commandonamen - SteamworksPower 0.1.1 -> **0.1.2**: primary commando
`/power` -> **`/swpower`** (aliassen `/pwr`, `/swp`; plugin.yml, `getCommand` en de
wiki-docs/seed aangepast - `/power` blijft van ValhallaMMO, dat lost de crashes op).
(2) De brievenbus-wens wordt **geen** mailsysteem, ook intern niet: gewoon een
decoratief Relic. Nieuw: **PILLAR_BOX** (Curiosities, "Pillar Box") - een Britse rode
zuilbrievenbus (engine-rode cilinder, zwarte sokkel, gouden banden, koepel met finial,
brass briefsleuf + collectiedeurtje) met steampunk-gadgets: drukmeter en koperen
zijklep; STEAM-effect speciaal voor hem op ventiel-hoogte (front + 1.3 blok, de
gewone STEAM-puffers blijven op blokcentrum). Craftbaar (IJZER/RODE_VERF x2/KOPER/
GOUD-KLOMP/REDSTONE), opnam in de random-pool (zeldzaam). Modellen: `pillar_box()` + de
`chocolate_heart`-treatmodel die eerder nog ONTBRAK (de treat had anders een
onbestaand item-model - fix van diezelfde slag als de 26.2-defvalkuil). Relics
0.4.0 -> **0.4.1** (nooit live geweest, dus in-place opgevolgd; sha1 c03a7c33),
pack BUILD 7 -> **8** (5919 bestanden, audit 0/0, sha1 6f0a4f0d). Preview3d-render
gecheckt (voorzijde via tijdelijke 90°-draai) en fraai leesbaar. CLAUDE.md:
verkoopmodel-paragraaf (5) herschreven - mailsysteem-van-tafel, publieke addon
levert dus ook geen bus. Alle jarren + pack staan in deploy/, TE-DEPLOYEN is de
eerlijke staat van zaken.

### Steamworks 0.3.22 + Power 0.2.0 - vliegwiel, zeeftrechter en de as-ruzie in CustomPart-land (27-10 middag)

De vorige sessie liet een half afgemaakte, ONCOMPILEEBARE batch na (uncommitted): de
`CustomPart.handleItem`-hook + de Belts-aanroep ervan stonden in de core, de klassen
`Flywheel` en `SieveFunnel` in Power - maar nergens geregistreerd en met twee aanroepen
naar core-code die een addon niet mag zien (`Kinetics` is package-private; een publieke
markDirty bestond niet). Nu afgemaakt:
- **API-afmaking**: nieuwe publieke `SteamworksPlugin.markDirty(Block)` - add-ons melden
  daarmee dat hun CustomNode.data veranderde; de chunk gaat in de saveQueue en saveState()
  draait binnen een seconde opnieuw (filter/charge overleeft zo een restart). Het
  belt-lijnhook bleef zoals voorgesteld: `deliver()` vraagt CUSTOM-einden eerst, null =
  niet hun zaak.
- **SieveFunnel gerepareerd**: kaats-vector zelf berekend uit `CustomPart.positive(axis)`
  (nooit door de API-hek naar Kinetics), `plugin.sw.markDirty(n.block())`, 5 regels lore
  zoals de house-stijl. Gedrag: rechtsklik met item = filter (signature = materiaal +
  item-model, dus onze plaatitems verschillen van vanilla), rechtsklik leeg = tonen,
  shift = openen; matching items zakken naar de container onder de zeef, rest wordt
  zijwaarts weggekaatst (grindstone-rythme), geen container = wachten op de lijn.
- **Flywheel** zoals ontworpen: windt op boven 3 RPM (bron-onafhankelijk), neemt over als
  de lijn stilvalt, hold-snelheid daalt met de lading (laatste stretch sputtert op 15%),
  rechtsklik toont %-lading + modus; state in de 17-byte saveState-blob.
- **Registratie**: PowerPlugin registreert er nu vijf (vliegwiel + zeef erbij), twee
  nieuwe werkbankrecepten (4 brass platen + casing; 2 iron platen + string + funnel),
  log "Registered 5", config.yml vliegwiel-sectie, plugin.yml/pom/pom-systemPath (0.3.19
  -> 0.3.22!) meegenomen.
- **Echte bug gevonden tijdens de model-audit**: de custom-visualsconventie is model Y =
  asrichting en model Z = omhoog (corient = orientation(positive(axis), UP)) - de core-
  modellen gebruiken orientation(UP, facing), een ANDERE convention. De waterturbine-drum
  was rond model X gebouwd: in het spel zou die om de as tuimelen in plaats van meedraaien -
  opgemerkt is dat nooit, want de turbine stond nog nergens. Drum herbouwd rond Y
  (zelfde 15 cubes, dus de pers/ticker-uuids bleven byte-stable); bestanden mee in
  deploy/fmm-steamworks. Nieuwe modellen: `brass_flywheel()` (31 cubes, mm-helpers rond Y)
  en `sieve_funnel()` (19 cubes: messing eindmuren, darkwood zijkanten, mesh-grid bovenop,
  koperen snavel onderkant); preview3d gecheckt (zeef-icoon via Y/Z-swapt json, want raw
  modelleren staat de liggende zeef op z'n kant).
- **Site**: power-parser in build-recipes-data.js kon alleen `material/`-ingrediënten -
  uitgebreid naar `part/` (anders TypeError op het funnel-recept) + naamtabel/boodschap;
  5 werkbank + 5 persrecepten in recipes-data; iconen brass_flywheel/sieve_funnel +
  herbouwde water_turbine gerenderd; make-recipe-icons liep weer byte-stable; wiki
  00-intro (vijf machines + good-to-know's) en 02-machines (vliegwiel + zeef, titel
  "Press, Ticker, Flywheel & Sieve") + seed regenerated 12 pagina's; /steamworks-page:
  Power-kaart uitgebreid en het stale versie-label (0.3.19/0.1.0) naar 0.3.22/0.2.0.
- **Groen**: mvn core + power, KineticsTest ALLE PASSED, npm build, jarren + 6
  modelbestanden in deploy/, TE-DEPLOYEN-punten 3 en 4 in-place opgevolgd (0.3.21/0.1.2
  zijn nooit live geweest en reizen mee).
- **LIVE + BEVESTIGD** (27-10 middag): de gebruiker stopte de server en de agent
  doet de volledige bestandwissel zelf via SSH (eerste keer dat de wisseltruc niet
  handmatig hoefde): alle 12 artefacten gekopieerd met sha1-controle TER PLAATSE
  (gelijk aan de lokale builds - kan alleen omdat de server stil staat; de TechCore-
  injector draait immers in de levende server), eigenaars/rechten overgenomen, de
  vier oude jars + build6 uit de mixer verwijderd, FMM-modellen 242 -> 246. De
  herstart (15:13 servertijd) bevestigt het: 0.3.22 enabled, Power 0.2.0 enabled met
  vijf machines + 5 recepten-logregel, Relics 0.4.1 en Sakagura 0.3.3 enabled,
  "rejected advancement" 0 hits (sprocket-fix werkt), enige WARN = de bekende
  TechCore-ActionBar/PacketEvents-melding (niet van ons). Restanten: site-zip met
  de nieuwe wiki/recipes + 0.3.22-labels (gebruiker bouwt die zelf), dan de
  wiki-importknop op /wiki.
- Valkuil om te onthouden: **elk CustomPart-model tekent met Y als as** (net als shaft/
  crushing wheel via de mm-helpers) en Z omhoog bij liggende machines - preview3d tekent
  het model in rauwe modelruimte, dus een verkeerde as (tuimelende drum) is op het sheet
  NIET te zien; check de as-conventie altijd tegen Visuals' corient.

### Sakagura 0.3.4 - feestdagen-set: twaalf drankjes voor Patrick, pompoen, kerst en paas (27-10 avond)

Gebruiker vroeg om ~10 suggesties "binnen wat Minecraft kan, out of the box" en na
het zien daarvan: "maak dit maar in de recipes.yml". Twaalf ideeën geworden (elf binnen de
bestaande engine + één met een zetje), allemaal door de eigen generator (OWN in
tools/make_recipes.py) - dus meekleurend bij élke herbouw en het wiki-receptenblad
schrijft ze automatisch mee (50 drankjes op 03-recipes.md, seed 20 pagina's).
- St Patrick's: Shamrock Shake (LUCK + GLOWING - je verraadt jezelf), Crock O' Gold
  (aardappeldistillaat met een goudklompje meegekookt), Bog-Kelp Collins (Sake + kelp +
  sea pickle = WATER_BREATHING). Halloween: Head of the Hollow (pompoen + buskruit,
  FIRE_RESISTANCE), Widow's Kiss (spinnoog, INVISIBILITY), Sculk Swill (sculk-sensor als
  ingrediënt, moeilijkheid 9/5%, SLOW_FALLING). Kerst: Yule Log Reserve (rode wijn +
  cacao + sparrentak), Reindeer Run (4%, SPEED, kerstochtend), Bishop's Knog (eggnog van
  schildpad-eitjes + juniper gin). Pasen: Torchflower Spritz (torchflower-zaden, HASTE +
  LUCK), Warren Weizen (hasenpoot - als item gewoon "Rabbit Foot", want de Material-naam
  heeft geen apostrophe: "Rabbit's Foot" matcht niet en wordt een name:-waarschuwing).
- Valentine's Chocolate Heart Flip: hét eerste drankje dat een snoepje VAN EEN ANDERE
  PLUGIN gebruikt. Daarvoor was één lijn nodig, geen afhankelijkheid: Sakagura's
  Ingredients.java kent nu ook het PDC-slot "relics:treat" (net als kitchen:item en
  steamworks:material al) -> "relics:chocolate_heart/1" als ingrediënt, ABsorption als
  effect (zelfde deugd als de Relics-treat). Relics hoeft niets te weten van Sakagura -
  zuiver lezen van een item-key, dus de gratis/premium-scheiding blijft schoon.
- Groen: RecipeTest ALLE PASSED op de 50 (van de twaalf nieuwe: 30 checks), mvn 0.3.4
  (ingebedde default = hetzelfde bestand), sha1 cea48c2e. site-zip herbouwd (de vanmiddag-versie stond nog in deploy; nu
  herbouwd als punt 6, sha1 e0a7bc64) en de recipes.yml staat klaar als deploy-bestand
  (punt 5: overschrijven + /brew reload, ZONDER herstart; jar wisselt bij de eerst-
  volgende herstart mee - doet hier verder niets). Let op: zonder de nieuwe jar herkent
  de live-server het chocolate_heart niet in de ketel (oude Ingredients) - de andere
  elf drankjes kunnen dan al wél.

### Website toont het barmenu en de Brewer's Manual wordt levend (27-10 avond)

Opmerking van de gebruiker: "op de website los van de wiki staat nergens de nieuwe
recepten" + "the brewers manual moet altijd de actieve recepten tonen". Twee dingen
gebouwd, één batch:
- **/recipes-tab "Sakagura"**: scripts/build-recipes-data.js leest nu rechtstreeks
  Sakagura/src/main/resources/recipes.yml (de generator zelf is de bron - kan nooit
  verouderen). 50 machine-kaarten: cook/mix-grondstoffen in, drank uit, stappenketen +
  alcohol% + effecten als note; categorieën Bar + vijf feestdagen. Nieuw in de
  icoonmaker: een drank met hex-kleur in de data wordt een fles in eigen kleur (kurk,
  hals, glans) - 79 nieuwe icoontjes, en de pics-scan is uitgebreid met map sakagura
  zodat barley/vaten/stills echte kunst tonen (28 stuks). /sakagura-pagina: sectie "The
  seasonal shelf" (twaalf feestdrankjes; expliciet: de tuin blijft elf gewassen,
  zee-augurk/glansbessen/schildpad-eieren/hasenpoot komen uit de wereld) + het stale
  versie-label 0.2.8 → 0.3.4.
- **Brewer's Manual = live menu** (IekcorbarSiteSync 1.15.3 → **1.15.4**, jar sha1
  d5b662e8): BookManager.createItem bouwt pagina's nu via resolvePages - een magic page
  "%%LIVE_RECIPES%%" wordt bij élke uitgifte (/ibook give, loot, zwervende-handelaar-
  ruil) ingevuld uit plugins/Sakagura/recipes.yml: "&6Naam&8 — cook 8m, distill x3 ·
  25%" per drank, in de volgorde van de bar, automatisch gepagineerd. Omdat de
  server-txt nooit overschreven wordt, herkent brewers_manual de lijst ook zónder
  marker: hij gaat vóór de eindpagina. Geen Sakagura of kapotte yaml → eerlijke
  mededeling in plaats van lege pagina's. Offline gecheckt met een LiveListCheck: 50
  drankjes, 8 pagina's, Ramune als laatste. Repository-txt kreeg de marker + TOC-regel.
- **Groen**: SiteSync mvn (d5b662e8), node-data (50), iconen (471), npm build, zip
  herbouwd (b0e51bfc, nu incl. SiteSync-jar 1.15.4 voor verse installs), TE-DEPLOYEN
  punten 6 + 7 (jar bij eerstvolgende herstart; /ibook give als test). De zip van
  vanmiddag (e0a7bc64) is in-place opgevolgd - die stond nog nergens.

### Steamworks 0.3.23 - dupliceerbare onderdelen via midden-klik (27-10 avond, offline)

Gebruiksvondst van de eigenaar: in SURVIVAL kon een middle-click op een geplaatst
onderdeel (o.a. de molensteen) het item in je inventory toveren - een echte dupe.
Oorzaak: PartListener.onPick (bedoeld als de Creative-fix tegen het "barrier op je
hand"-probleem) controleert zelf niet op gamemode; Paper vuurt PlayerPickBlockEvent
in élke game-mode en laat die check bij de handler. Reparaties: gamemode-guard
vooraan (alleen CREATIVE; daarbuiten doet de handler niets en pakt vanilla zelf al
niets), javadoc + CLAUDE.md-beschrijving aangepast. Eén handler voor álle onderdelen
èn de addon-machines, en verder nergens in de pluginset een PlayerPickBlockEvent -
dit was de enige plek. pom 0.3.22 -> **0.3.23** (jar sha1 8e7d4bc5; geen
API-wijziging, Power 0.2.0 hoeft niet herbouwd). Site-label naar 0.3.23 en de zip
opnieuw gebouwd (bbea9f14 - bevat ook de dranknamen-in-de-kaartkop, de nieuwe
grondstof-icoontjes en de verwijderde dubbel-knop "Sakagura drinks ->"). Jar + zip
staan op de TE-DEPLOYEN-lijst (punt 8 resp. bijgewerkt punt 6); de server is deze
keer expres niet aangekeken - afleveren doet de eigenaar morgen.

### Staff Panel > Player Data - EliteMobs/MMOCore-lookup in de plugin-DB (27-10 avond)

Gebruikerswens: een staff-only sectie op de website met databasegegevens - specifiek
EliteMobs-stats (level, XP, etc.) - pas zichtbaar NA zoeken op spelernaam.
- Schema eerst geprobeerd via de site-zelfde pluginDataQuery (laptop -> sql1.revivenode.com
  = connect-timeout; de DB laat alleen de server door). Daarom via SSH op de VPS met de
  mysql-cli uit de SiteSync-config: tabellenlijst + information_schema-kolommen opgevraagd
  (alleen metadata, geen rijen, geen spelersdata, geen wachtwoorden geprint). Vond:
  EliteMobs = PlayerData (Kills/Deaths/Score/QuestsCompleted/DungeonsCompleted/
  HighestLevelKilled/CurrencyCents|V2 + elf SkillXP_-kolommen; let op de typo STAVES)
  en MMOCore = mmocore_playerdata (level, experience, class, guild, class/skill/
  attribute_points).
- src/lib/db.js: getCombatProfile(username) - zoekt EliteMobs op DisplayName,
  whitelisted_players op uuid als vangnet (naamswijzigingen), uuid-streepjes
  genormaliseerd met REPLACE(uuid,'-','') omdat de tabellen verschilden formaat.
  Foutafhandeling: ongeldige naam / DB onbereikbaar = nette json, geen stack.
- src/app/api/admin/player-data/route.js: GET ?name=, requirePermission('player_data')
  - server-side afgeschermd, niet alleen UI-verbergen. Naam moet 2-16 [A-Za-z0-9_] zijn.
- staff-tabs.js: tab 'player_data' (Administration) - Role Manager toont hem automatisch
  als vinkje; admin heeft alle tabs, andere rollen moeten 'm krijgen. moderation/page.js:
  icoon Swords, hub 'Server' uitgebreid, zoekformulier + twee kaartjes (MMOCore level/XP/
  class/points, EliteMobs gevechtsstaat + skill-XP-rooster) - staat allemaal al leeg tot
  er gezocht wordt, exact zoals gevraagd.
- Groen: next build; zip herbouwd (sha1 ebab7f60, punt 6 van de lijst - morgen samen met
  de 0.3.23-jar). Test-na-live: tab openen -> zoeken -> stats; role zonder vinkje krijgt
  403.

### Player Data wordt een volledig spelerdossier (27-10 avond)

Opdracht: "die sectie uitbreiden met alle speler stats uit alle plugins, neem ook
advancement mee en vanilla player stats". Twee kanten, één batch:
- **SiteSync 1.15.4 -> 1.16.0, nieuwe DeepSync-dienst.** Advance- en statistiekdata
  leven in de savefiles, niet in tabellen. Eerst geprobeerd via Paper's
  OfflinePlayer#load() — in deze API (26.2.build.135) bestáát die niet: javap op de
  geresolverde paper-api-jar leerde dat advancements alleen op een lévende Player
  leesbaar zijn (getAdvancementProgress per advancement), terwijl
  OfflinePlayer#getStatistic wél offline werkt. Design daarna: cyclus van 30 s met
  batch 5 (stats altijd; advancements van de online spelers), en de quit-event schrijft
  het complete finale snapshot van de vertrekkende speler (hoofdthread leest, async
  thread schrijft). Speler is dus nooit ouder dan één cyclus data. De vier nieuwe
  player_stats-kolommen (uuid, stats_json, advancements_json, deep_updated) zet de
  plugin zelf met een information_schema-check + ALTER (portabel, geen handmatige SQL);
  de bestaande upsert van MySQLSync raakt die kolommen niet (COALESCE op
  advancements_json bij latere cycles). Config: deep-sync-section.
  MySQLSync kreeg een sharedConnection()-accessor (zelfde pooled verbinding, same rules).
- **Website: getPlayerDataBundle(name) in db.js** (vervangt de jonge getCombatProfile):
  één zoekactie -> webstats-rij (incl. deep-kolommen + jobs + placeholders), elite, rpg,
  ValhallaMMO-vormen + de 14 profiles_*-skillrijen (één UNION-query, owner-genormaliseerd
  op dashed/undashed), BetonQuest (uuid -> active_profile -> journal + punten),
  Sakagura-brouwlog (binary(16)-kolom! UNHEX van de dashloze hex) + journaal-blikken,
  DiscordSRV-link en MMOItems-aanwezigheid. Alles faalt soft naar null: plugin niet
  aanwezig = kaart niet zichtbaar.
- **UI**: de player_data-tab groeide van twee naar zeven kaarten (webstats, advancements
  met per-categorie done/inprogress/totaal + lijst met cap, vanilla stats met
  cm->m/km- en halve-harten-formatting, RPG-samenvoegkaart MMOCore+Valhalla, EliteMobs,
  BetonQuest, Sakagura) plus uuid/discord/MMOItems-chips bovenaan. Info-box en
  tab-beschrijving meegeupdated; imports aangevuld (Activity/Trophy/Flame/Wine).
  Let op: de tab-UI-checks staan serverside (requirePermission 'player_data') — UI
  verbergen is never the gate.
- **Groen**: mvn SiteSync (jar 97fc6d17), npm build, zip 15806745. Lijst: punt 9 nieuw
  (SiteSync-jar bij de herstart van punt 8), punt 6-sha ververst. Server niet
  aangekeken; deployen doet de eigenaar morgen.

### Molensteen zijkant-aandrijving + trechter, Sakagura-hitboxen, profiel-stats-popup, Halloween-schijf (27-10 avond)

Vier losse opdrachten van de eigenaar, een batch; artefacten staan in deploy/ (lijst punten 10-13):
- **Create-modellen: wel kijken, niet kopieren.** De eigenaar opperde Create's modellen te gebruiken met
  een iets aangepaste textuur. In de jar staat LICENSE.md_create-1.21.1: **assets/ (modellen en textures)
  is "All Rights Reserved"**, alleen de code is MIT - pixels aanpassen op gekopieerde geometrie blijft een
  afgeleid werk, dus dat mag niet. Wel als referentie bestudeerd (millstone block.json: boxframe 0-6 +
  draaitrommel 6-12 + cap 12-16, as van onder; funnel: -22.5 graad gekantelde schot-plaat). De nieuwe
  eigen modellen zijn daarna zelf opgebouwd in make_models.py, met hun afmetingen-gevoel (hoek-wanden op
  45 graden, messing randen) maar eigen vormtaal.
- **Steamworks 0.3.24**:
  1. Dubbel-plaatsen (de "twee shafts voor een klik / heel irritant"-melding): de oude dedupe in
     PartListener keek of het TARGET-blok hetzelfde was; bij klikken op VERVANGBAAR terrein (gras,
     sneeuw) maakte event 1 van het geklikte blok zelf een knooppunt, zag event 2 daardoor een knooppunt
     en plaatste via de n!=null-tak een blok verder (ander target, dedupe miste). Nu: per speler
     ontdubbelen (40 ms). Off-hand-guard en middle-click-fix (0.3.23) blijven erboven liggen.
  2. Molensteen op zijkant-aandrijving (eigenaar: "moet via zijkanten, boven en onder niet"): poorten =
     as-einden (geen DOWN meer), NodeStorage zet oude as-Y-stenen automatisch op X om (de schacht ONDER
     een bestaande molensteen doet het dus niet meer - herbekabelen naar de zijkant), wrench draait hem
     horizontaal (groep van STEAM_ENGINE, stays-horizontal), PartType-text en site-teksten mee:
     steamworks.json millstone-pagina + power.json pers-vergelijking + /steamworks-kaart. Visuals: frame
     tekent als de hammer (orientation(UP, SOUTH/EAST), model X = as), runner houdt de model-Y-as
     conventie. Nieuwe modellen: verticaal rad (stenen schijf r5.6 + 8 peddels r6.3 + hub-caps + peg) in
     frame met wangen/kap/voeder-sleuf + koperen as-doppen; GUI-icon sw_millstone bakt de runner via
     (x,y,z)->(y,z,x) in het frame.
  3. Trechter "stom model" -> echte hopper: goot blijft, kuip van vier 45-graden-gekantelde koperen
     wanden + gouden rand + zeefstaven + zwart-gat-decal op de vloer (model-kader: Y=up, Z=facing).
  KineticsTest 70/70 groen na de poortwijziging; make_models.py heeft eigen -16..32 grens-asserts.
- **Sakagura 0.3.5**: tuinplanten kregen een eigen Interaction-hitbox (0.9x0.95 op het plantblok,
  responsive, PDC-tag garden_hitbox) - rechtsklik de plant = oogsten/bemesten, klap = breken, beide via
  dezelfde canBreak-ground-probe; de oude "klik de aarde eronder"-route blijft werken. Display en
  hitbox leven en sterven samen (clear() helper in show/unload/tick/breakPlant/removeAll).
- **Website - playerprofile stats-knop + popup** (oorspronkelijke vraag "op het playerprofile incl
  staff, eigen stats zichtbaar, knop met mooie popup, sluit als ernaast geklikt, model ernaast met
  animatie"): nieuwe src/components/StatsPopup.js - overlay als de chat/login-modals (klik ernaast
  sluit, Esc ook), steampunk-border + sp-* tokens. Links een display-case met het skin-body-render
  (mc-heads, zelfde als de hero) dat bob-en-sway doet (nieuw in globals @theme: modal-pop, fade-in,
  model-bob, model-sway) tussen draaiende SVG-tandwielen + steam-rise damp en een bronzen sokkel met
  online-lampje; rechts de volle cijfers uit /api/player-stats (Yen, coins, speeltijd incl. CMI-uur,
  K/D, mobs, mined, claims, homes, jobs met XP-balken, pluginStats-ledger) met refresh ->
  refetchStats(). /profile: hero kreeg de gouden "My Live Stats"-knop, de overview-kaart werd een
  Yen+tijd-teaser + "Open My Statistics". Werkt voor elke rol (de pagina was al role-neutraal; eigen
  stats zijn er alleen voor de ingelogde gebruiker zelf).
- **Relics 0.4.2 - "Halloween in Iekcorbar"-schijf**: mp3 uit Downloads in Relics/discs/ + discs.txt
  (bestandsnaam | titel | recipe) -> python -I tools/make_discs.py (met de winget-ffmpeg) maakt mono-ogg
  (133 s) + discs-blok in de default config; make_pack + merge-packs BUILD 8->9 (audit 0 problemen,
  build9 16,7 MB sha1 65f461f7). Serverwerk blijft: de config-entry handmatig in
  plugins/Relics/config.yml (saveResource overschrijft nooit) + twee herstarts voor het
  relics_discs-datapack (lijst punt 13).
- **Let op UUID-churn**: make_models.py's uuid5-teller schoof op door de nieuwe elementen, dus alle
  ~110 .bbmodel-bestanden veranderden van inhoud - de FMM-modellenmap moet in het geheel naar
  plugins/FreeMinecraftModels/models/steamworks/ gekopieerd worden (punt 11-3 van de deploy-lijst).

### Ambient-kikker, music box-Samurai en /brew give-aantallen (27-10 late avond)

- **Wildlife-ambient op melding van de eigenaar** ("rare/lage irriterende geluidjes"): de
  frog-croaker was een vierkante golf van 130-170 Hz die door write_wav's normalize op volle luidheid
  werd gezet - een dreunende buzz. Nu: sinus + verzachte octaaf-toevoeging, korte pitch-dip (de
  "krok"), mildere tremolo; de RNG-aanroepvolgorde is behouden (uniform, randint, uniform per kikker)
  zodat alle andere clips in de seeded stream byte-for-byte hetzelfde gegenereerd worden.
  jungle_bird (die de eigenaar verdacht) is deterministisch gebleven en ruislozer/helderer gemaakt op
  z'n eigen lokale Random(1971). Nadeel van herbouwen: de ffmpeg-9-codering verschilt van de
  destijds committed ogg's, dus alle ambient-.ogg's in Wildlife/resourcepack zijn van bytes veranderd
  (verwachte ruis, inhoud bij andere clips gelijk).
- **Music box kreeg "Ghosts of the Samurai"** als vierde liedje: tools/make_discs.py heeft nu een
  MUSIC_BOX-set die voor die id's een extra sounds.json-event schrijft
  (music_box.<id> -> zelfde relics:disc/u_<id>.ogg, stream=True; make_pack zet de subtitle erbij) en
  Jukebox.MUSIC kreeg de vierde tune (4160 ticks). Eén audiobestand, geen dubbele disc.
- **Relics 0.4.2 -> 0.4.3** (alleen resourcepack-werk + deze tunes; geen config-wijziging).
- **Sakagura open-bar-set: 30 nieuwe drankjes** (eigenaar: "meer drankjes, creatief, effecten
  zelf bedenken, 30 stuks"). In make_recipes.py een nieuw blok "# ---- open bar": van wasabi-
  sinus-clearers tot rots-thee met resistance, van glow-grog (je gaat zelf gloeien) tot de
  alcohol-vrije Blank Slate (toevoeging aan de lijst: geen effect = ook een effect). Totaal 80
  drankjes; alle ingredient-tokens bestaand (kitchen:/sakagura:/vanilla/drank-op-naam), effecten
  = registry-namen. RecipeTest 80/80 groen. Maken: recipes.yml + docs-wiki + wiki-seed +
  recipes-data.json (site-tab) zijn allemaal mee-gegenereerd; site-zip herbouwd (nieuwe sha in
  de lijst, punt 10). Sakagura-jar 0.3.7: server kan recipes.yml NU al overnemen + /brew
  reload (drankjes meteen brouwbaar); hitbox + give-kwantiteit komen met de jar-herstart.
- **Sakagura 0.3.5 -> 0.3.6 - /brew give met aantal** (eigenaar: "64 bloody mary kan niet"):
  na een DRANK is een getal 1-10 nog steeds kwaliteit (oud gedrag, 1 fles), meer dan 10 is nu het
  aantal (kwaliteit 10), en "<kwaliteit> <aantal>" kan expliciet. Flessen = potions = niet-stackend:
  lussen langs addItem, rest valt voor de voeten. Vaten/tuinitems: hele stapels van 64. Take had
  al aantallen en is onaangeroerd.
- Pack: merge-packs BUILD 10 -> **11** (musicbox-event + halloween-ogg + nieuwe ambient-ogg's),
  audit 0 problemen (build11 sha1 1b01eeb8). Servermeting van zoeven vóór deze batch nog: Relics
  0.4.1 + build8 live, Sakagura 0.3.4 live - punten 12/13 van TE-DEPLOYEN dekken alle drie de
  jarren (Steamworks 0.3.24 + pack + models staan er ook in).

### Trailer-aankondiging: site-berichten + Discord-post live (27-10 laat op de avond)

- **Nieuws**: er wordt een cinematische **server-trailer** gemaakt door een content creator
  (naam bekend bij de eigenaar; wordt bewust NOOIT in enige publieke tekst genoemd - ook
  de site- en Discord-teksten houden het op "a content creator"). Geen releasedatum.
- **Site-berichten klaar voor de eigenaar** (agent raakt de website-DB niet): uitgebreide
  News-post "We're Making a Trailer — And It's in Good Hands" + korte Upcoming-regel
  "Server Trailer — In Production". Teksten staan in de chat (plakken via Staff Panel >
  News & Upcoming Manager) én als `deploy/trailer-news.sql` (phpMyAdmin-import; vaste ID's,
  dubbel importeren = nette dubbele-sleutelfout). Let op: NewsClient rendert LOOS inline
  `**bold**` in alinea's NIET - de definitieve teksten gebruiken alleen `### ` kopjes,
  `* **vet** ...` bullets en kale regel-paragrafen. Geen site-zip nodig (pure DB-content).
  TE-DEPLOYEN punt 14.
- **Discord GEPOST op expliciet verzoek van de eigenaar** ("ik wil het bericht over de
  trailer toch maar pushen", "staff en speler") - de eerdere regel "wacht op toestemming
  van de maker" is hiermee door de eigenaar zelf opgeheven. Lange aankondiging -> changelog
  (publiek), korte zakelijke heads-up incl. "noem de maker niet tot hij/zij okay zegt" ->
  staff. Beide webhooks via skill discord-post; drafts in
  `%TEMP%\opencode\trailer-discord.md` + `-staff.md`.
- **Open**: de website-berichten zelf plakken/importeren (punt 14) en de vanavond-batch
  deployen staan bij de eigenaar; Wildlife-dierenbatch (animaties/drops) nog op stapel.

### Wildlife 0.6.2 - de dierenbatch: beer vaalt, vlinders bestuiven, jachttrofee (28-10 ochtend)

- Sluit het open-staatje van 27-10 ("Wildlife-dierenbatch nog op stapel"). Drie gedragingen,
  nul nieuwe models/textures: alles wordt hergebruikt (beer-"eat"-animatie, bestaande
  zoo-statue-modellen) - dus GEEN pack-build en GEEN models-zip nodig, alleen de jar.
- **Beer vissen** (`Animals.fishResult`/`startFishing` + `waterNear`): kalme beer overdag
  binnen 5 blokken water gaat 3 s vissen (pad naar de waterkant, kop-duik via de eat-anim,
  splash-geluid); 65% vangst = eet-animatie + salmon-flop, anders ontsnapt de vis. Getuigen
  binnen 16 m krijgen de verborgen advance "Fishing Bear". Alles via de bestaande
  busyUntil-timer (na bessen 30 s verzadigd, na een vangst 5 s rust) - slapen/eten/vissen
  overlappen nooit.
- **Bestuiving** (`Animals.pollinate`): vlinder boven tarwe/peen/aardappel/biet schuift het
  gewas 1 groeistap op (gemiddeld 1x per 4 s per dier); Tag.FLOWERS: de bronbloem plant zichzelf op
  kale grond met grasblok binnen 2 blokken; happy-villager-deeltjes; verborgen advance
  "Butterfly Garden" (pop't bij ~1 op 4 nudges, niet bij elke rit).
- **Jachttrofee** (`onDeath`): een adult die geveld wordt door een speler die de soort eerder met
  de spyglass recordde -> 10% (gewoon) / 30% (zeldzaam) kans op het statue-item van die soort
  (`animal_<id>[_rare]`, direct uit `zoo.decor.defs`) + verborgen advance "Hunter's Trophy".
  Voorwaarde bewust op seen(false): een soort-record telt, zeldzaam-zien hoeft niet.
- Achievements: drie nieuwe entries in `Achievements.create` (fisher/pollinator/trophy,
  verborgen); Species-beernotitie en Facts (salmon-run-feit) aangepast; wiki
  `01-wildlife.md` aangevuld met de drie Behavioral-teksten.
- mvn clean package groen; `Wildlife-0.6.2.jar` sha1
  9574313d7f98ae60f6973811a0ce2696b0e8d9bd, TE-DEPLOYEN punt 15 (alleen jar-wissel +
  herstart). Not live-tested: vissende beer naast een rivier, vlinders boven onrijpe tarwe
  en trofee-val op een recorded soort moeten nog in het spel gezien worden.

### Staff Panel: advancements-kaart compakt gemaakt (28-10 ochtend)

- Eigenaarsklus: "Player Data toont ALLE advancements, er zijn er zoveel".
  Frontend-only (`src/app/moderation/page.js`): de Advancements-kaart renderde een
  muur van gouden chips (done.slice(0,60) + scroll). Nieuw component
  `AdvancementsCard`: één progress-rij per tab (eigen plugin-tabs vet vooraan,
  vanilla-categorieën daarna), balk + "x/y" + "+N bezig"; de namen openen pas als
  je een rij aanklikt (max-h-28 scroll). Groepering gebeurt client-side met
  dezelfde categoryOf-regel als DeepSync.java (pad-segment vóór de eerste '/' →
  wildlife: "wildlife", minecraft: "adventure", zonder '/' → "misc") - de
  SiteSync-jar hoeft dus NIET gewisseld, de json is al genoeg.
- Bouw: `npm run build` groen (113 pagina's); site-zip via make-zip.js →
  `deploy\iekcorbar_iekje_barend.zip` (34,0 MB, sha1 816f16de6fdc65be5bca4c54ba6b9d2bd08cb284),
  TE-DEPLOYEN punt 16 (Plesk: unzip + npm install + build + restart).

### StaffHub 0.1.0 - nieuw deelproject in deze repo (28-10 ochtend)

- NIEUWE MAP `StaffHub/`: losstaande webapp + WebDAV-schijf + Electron-shell waarmee
  staff handleidingen en bestanden deelt (Next 16 + node:sqlite + content-addressed
  blobs, géén MySQL/Plesk). Bouw vanochtend klaar gezet en groen getest: setup→owner,
  login/me, map+upload, pagina's, zoeken, WebDAV PROPFIND/PUT/GET, token-logins -
  alles 200/207/201 via een rooktest op de echte prod-server. WebDAV-login is
  gebruikersnaam + token als WACHTWOORD (UI legt dat uit; DavModal/TokensModal).
- Valkuil 1 (Next + monorepo-achtige structuur): StaffHub zit IN deze repo en de repo-root
  heeft zélf een package-lock.json + Next-site. Next raadt daardoor de PARENT als
  workspace-root en bouwde stilletjes de site-`src/instrumentation.js` + reminders/MySQL-code
  in StaffHub's `.next` mee (elke opstart: "ECONNREFUSED 127.0.0.1:3306"). Fix: in
  `StaffHub/next.config.mjs` `turbopack.root` én `outputFileTracingRoot` pinnen op de
  eigen map. Herkenning: chunk-bestand `src_lib_reminders_*` in StaffHub/.next.
- Valkuil 2 (custom server): `handle(req,res,new URL(...))` gaf in prod een 308-zelflus op
  IEDER request (ook `GET /`). Fix: `parse(req.url, true)` uit node:url + `next({dev,hostname,port})`
  zoals in de Next-docs. `node server.js` (dev) en `NODE_ENV=production node server.js` draaien nu.
- Root `.gitignore` kende twee valkuilen voor StaffHub: `deploy/` en `.env.*` grepen ook
  in `StaffHub/` → re-include-regels toegevoegd (`!StaffHub/deploy/`, `!StaffHub/.env.example`).
- Docker: `.dockerignore` toegevoegd (host-`node_modules`/`.next`/`data`/`desktop` blijven
  buiten de image) en `npm ci --omit=dev=false` vereenvoudigd naar `npm ci` (er zijn geen
  devDependencies).
- Aflevering: `deploy\staffhub-0.1.0.zip` (60 KB, sha1 989321f88172531f0513a8d482df0974eebffdf0,
  44 bronbestanden) + TE-DEPLOYEN punt 17 (VPS-host, geen container: /opt/staffhub, docker
  compose, Caddy staff.iekcorbar.com, DNS-A-record nog aanmaken, bootstrap-code, backup-cron).
  Electron-app (`desktop/`) is nog níet gebouwd - alleen de bron.

### StaffHub 0.1.1 - DNS gezet, voordeur wordt nginx (28-10 middag)

- Eigenaar maakte de A-record `staff.iekcorbar.com` bij Hostnet; er stond een los teken
  achter het IP, dat heeft hij weggehaald en de record opnieuw gezet. Nagemet via 1.1.1.1
  én 8.8.8.8: `staff.iekcorbar.com` → **82.165.250.99** (de VPS). De apex
  `iekcorbar.com` → 195.20.225.109 (de site-host) en `www` idem — dat klopt zo.
- **Caddy-plan was onuitvoerbaar.** Agent-check op de VPS (alleen gelezen): er draait
  géén Caddy (`caddy` not-found, inactive), maar **nginx 1.24 bezet al 0.0.0.0:80/443**
  met certbot-certificaten voor `map.iekcorbar.com` (proxy → 82.165.250.99:2243, de
  BlueMap-haven van de MC-container) en `node1.iekcorbar.com`. Caddy ernaast had die
  havens moeten stelen en BlueMap van de kaart af geschoten. Punt 17 is herschreven
  naar **nginx-vhost + `certbot --nginx`**, als `StaffHub/deploy/nginx-site.example`
  (de `Caddyfile.example` is weg). Voor Windows-Verkenner-WebDAV staat er
  `client_max_body_size 0`, `proxy_request_buffering off` en 600 s timeouts; géén
  http2 aanzetten op dat vhost (de WebDAV-redirector hikt erop). Herkenning van de
  foute situatie vóór de DNS-correctie: `https://staff.iekcorbar.com` gaf de **BlueMap**-pagina
  (nginx vond geen matching server_name → default vhost) met een cert-naamfout.
- Overige stand op de VPS: `/opt` bevat alléén `containerd` (StaffHub dus nog níét
  uitgepakt), geen staffhub-container, `127.0.0.1:3120` luistert nog niet, schijf 323 GB
  vrij, 19 GB RAM beschikbaar. Niets gewijzigd op de server — alleen gelezen.
- Nieuwe artefacten: `StaffHub/tools/make_release_zip.py` (bouwt de zip uit package.json-
  versie, voorvoegsel `staffhub/`, zonder node_modules/.next/data) en
  `deploy\staffhub-0.1.1.zip` (61 KB, sha1 7db5733183f0a9212a9022ed5da3a8674af8c811,
  45 bestanden). Zelfde bron als 0.1.0, alleen de deploy-laag anders; de 0.1.0-zip is
  weggegooid (nooit geüpload — op de VPS niets met staffhub gevonden).

### StaffHub desktop 0.1.1 - de Electron-shell écht gebouwd, via een offline-detour (28-10 middag)

- Het vierde deel van StaffHub is nu af: `StaffHub/desktop/` (Electron-venster op de
  webapp + WebDAV-uitleg in het menu) is gebouwd tot werkende Windows-app. Artefacten
  (laatste stand, ná de routerherstart, in `deploy/` + TE-DEPLOYEN punt 18):
  `staffhub-desktop-0.1.1-setup.exe` (77,9 MB, sha1 41f288613a1443a2f872ca6ec163faf5dc6d24fc,
  NSIS-installateur) en `staffhub-desktop-0.1.1-win.zip` (109,7 MB, sha1
  66e4a0ec5a221c058e2b9eab37a2f5e2e4b49677, portable). Beide met eigen pictogram en
  correcte exe-metadata (Product=StaffHub, FileVersion 0.1.1, Company=Iekcorbar).
- Versie van de shell meegeset naar 0.1.1 (was 0.1.0) zodat zip-naam, exe en webapp
  één lijn volgen. `desktop/package-lock.json` bestaat nu en staat bij de bron.
- **De offline-detour:** tijdens de eerste bouwlag was de IPv4-out thuis weggevallen
  (Wi-Fi toonde "internettoegang" want IPv6 werkte; GitHub is IPv4-only → push onmogelijk).
  npm kon toen wél (registry.npmjs.org heeft AAAA), dus de eerste portable-build kwam tot
  stand met: (1) `"electronDist": "node_modules/electron/dist"` in de build-config — de
  Electron-runtime komt dan van schijf i.p.v. GitHub (blijft staan, handig); (2) CLI-
  override `--config.win.signAndEditExecutable=false` om de winCodeSign-download te
  omzeilen. Tussentijds artefact (offline-zip bd32f780… zonder pictogram/metadata) is
  vervallen en weg; ná de routerherstart volledig herbouwd en alsnog groen.
- Twee valkuilen voor elke Windows/Electron-build op deze pc, vastgelegd:
  1) `winCodeSign-2.6.0.7z` bevat macOS-symlinks (darwin/*.dylib) die 7za niet mag
     aanmaken zonder admin-/developer-mode → de héle extractie faalde, terwijl élke
     Windows-tool (rcedit, signtool) wél werd uitgepakt. Fix: die half uitgepakte dir
     eenmaal naar `%LOCALAPPDATA%\electron-builder\Cache\winCodeSign\winCodeSign-2.6.0`
     zetten — daarna accepteert electron-builder de cache en draait de build groen door.
  2) Exe-metadata bleef "GitHub, Inc." zolang `author` in desktop/package.json ontbrak;
     `author: Iekcorbar` gezet en herbouwd.
- Pictogram: `StaffHub/desktop/build/icon.ico` (7 formaten 16-256 px, PNG-entries),
  procedureel gegenereerd met System.Drawing in de huisstijl (tegel #171210, bronzen
  rand, gouden hub met vier staff-knooppunnen); electron-builder pikt `build/icon.ico`
  automatisch op. Het tegenscript zelf staat niet in de repo — palette komt uit
  `src/app/globals.css`.
- Niet code-signed (geen cert) → SmartScreen kan bij eerste start "Toch uitvoeren"
  vragen; normaal voor een eigen build.

### StaffHub 0.3.2 - live + 17 staff-gidsen in de hub (28-10 middag)

- 0.3.2 is uitgerold (goedkeuring eigenaar 'rol jij maar uit'): talen NL/EN/IT,
  Assistent-zoekknop en Boord-AI-knop draaien live op staff.iekcorbar.com.
- Boord-AI = opencode-agent in sandbox-container (bash/edit deny, alleen intern
  netwerk, basic-auth) op het gratis Go-model beam-free. Model-login staat in
  /opt/opencode-ai/data/auth.json (nooit in de repo). Let op: de Go-sleutel is
  ooit in een chatsessie geplakt - overwegen te rouleren.
- **17 Engelstalige staff-gidsen staan in StaffHub: map Guides/Plugins (13 plugins
  op live-versies) + Guides/Operations (server-setup, databases, deploy & packs,
  staffhub-usage).** Ze zijn geschreven vanuit pom/plugin.yml/CLAUDE.md/docs-wiki's.
  Eén correctie: Gouw = chunk land-claims (opvolger Lands), géén food/shop-plugin.
- Sync-valkuil: de Boord-AI leest StaffHub-zelf NOG NIET - hij draait op copies in
  /opt/opencode-ai/workspace/guides/*.html. Een gids die hier in de hub wordt
  aangepast moet daar opnieuw overhezet worden (of straks: repo-clone + export).
- Hulpmap .tmp-guides/ is weer weg; upload-/testscriptjes zijn weg.

### Sakagura 0.4.0 - tuin van 16 naar 36 gewassen (28-10 namiddag)

- Gebruikersvraag 'wat meer crops, een stuk of 20 meer' -> 20 nieuwe Fruit-entries:
  RICE, RYE, MAIZE (graansoorten, worden uitgetrokken), TOMATO, RASPBERRY, BLACKCURRANT,
  ELDERBERRY, CRANBERRY, GOOSEBERRY (heesters), MULBERRY, PERSIMMON, LOQUAT, POMEGRANATE,
  FIG, QUINCE, OLIVE, DATE, LYCHEE (dwergboompjes met trunk-sprite), PASSIONFRUIT en
  VANILLA (ranken - dorstig als hop/druif, zie Garden.java vochtcheck).
- Alles regenerert uit de enum: goods + zadenrecepten (Goods.java), achievement-tellingen
  (Full Orchard = nu 36), Zukan-oogstoverzicht, wild-plantages per biome (wildBiomes-lijst
  per crop; 'dark_forest' i.p.v. 'dark' gebruikt wegens deep_dark-bots).
- Pack: 20 kleuren + 31 ITEMS-modellen + sprite-takken verbreed in tools/make_pack.py;
  FRUITS-volgorde MOET matchen met Fruit.java (state in de comments).
- Gemeten: mvn package groen, RecipeTest ALL PASSED, Sakagura-pack 69 items/108 stages,
  merge build11->12 (audit exit 0, 0 problemen), jar sha1 dd509db0.., pack sha1 2e6337df..
  -> TE-DEPLOYEN punt 21 (jar + pack in één batch, oude 0.3.7.jar verwijderen).
- Guides bijgewerkt: StaffHub-pagina 'Sakagura' (node 11) en workspace-copy op de VPS.
- Open volgen: nog GEEN flessen-recepten op de nieuwe gewassen (20 losse brouwsels =
  apart werkblok op verzoek; Ingredients herkent ze al wél als sakagura:<naam>).

### Wildlife 0.7.0 - de Wraith: eerste sfeer-creatuur, géén dier (28-10 avond)

- Eigenaarsvraag: één nieuw wezen bij Wildlife, "alle assets zelf maken", spawnbaar met
  /wildlife spawn wraith, NICHT craftbaar, buiten het dier-journal. De OptiFine-pack in
  Downloads\wraiths-in-minecraft is alleen bestudeerd (zwevende romp, uitlopende tolge,
  hangende mouwen, capuchon) - geen byte daarvan overgenomen.
- Species krijgt een tweede soort lid: `boolean animal()` (WRAITH = false). Basemob BEE
  (ALLAY/PHANTOM/VEX onbewezen in deze repo; PARROT bewezen maar slecht passend: schouder-
  zitten, grond-huppen, jukebox-dans). BEE zweeft 3D (als de AIR-insekten), kan via onTarget
  nooit targetten, woede wordt elke tick gezeroed en Brain stript hive/flower/anger/attack.
  AIR + NIGHT, group 1-1, weight 0, scale 1.8 (hitbox bee 0.9 m x 1.8 = 1.62 m, model 26 px).
- Dier-loketten afgeschermd: Zoo.statues() slaat niet-dieren over (dus GEEN statue-recept),
  ZukanData geeft geen ei, Journal-tick/open/advancements slaan ze over, WildlifeCommand
  wijst /wildlife egg wraith af (geen zoo:egg_-model = anders paars in 26.2) en rare/baby
  worden genegeerd; Animals.spawn cleart rare+baby vóór het FMM-model wordt opgevraagd.
  Valkuil reparatie: "Living Encyclopedia" telde values().length - nu Species.journalCount()
  (de 30 journaalsoorten), anders was de prestatie onhaalbaar geworden door de 31e soort.
- config.yml: `wraith: {enabled: false, weight: 0}` en de Spawner-code hoedt zichzelf extra
  (server-configs worden nooit overgeschreven door saveResource, dus enabled-falsy is daar
  niet te vertrouwen - diercheck in Spawner.load).
- Gedrag (Animals.tick, night-only als firefly): bij dageraad dissolveert de wraith in
  SOUL-deeltjes en verdwijnt; 's nachts een zachte willekeurige up/down-snelheid (gedempt
  -0.04..0.05) en matige deeltjes (SMOKE bij de zoom, SOUL bij de muts). Ambient via het
  bestaande pad (AIR-volume 0.3). Geen vechten, geen chase, geen drops, geen exp.
- Model: `def wraith(m)` + SOLOS-lijst (buiten SPECIES/EXTRA, want SPECIES/EXTRA zou
  automatisch _baby/_rare/statue/egg genereren - main() schrijft SOLOS als één wl_wraith).
  37 elementen, 5 botten (body, h_head, arm_l/r, hem), idle 2.6 s + walk 2.0 s via wave()
  (body zweeft, hem en mouwen hangen achter), death = wegzakken (geen val-rotatie). Palette
  uitgebreid: wraith_robe/-d/void/glow (234/256 cellen). Deterministisch: gegenereerd naar
  BEIDE bomen (Wildlife/fmm-models + fmm-models, 134+3 bestanden, wl_wraith byte-gelijk).
- Geluid: wraith.ambient (3), wraith.hurt (2), wraith.death (2) via een nieuwe LATE-dict in
  sounds/make_sounds.py die ná CATALOGUE én EXTRA wordt getekend - wildlife-EXTRA zit VÓÓR
  relics-EXTRA in dezelfde seeded stream, dus dáár appenden had alle relics-clips verplaatst
  (aanhaakpunt in de opdracht was net iets te optimistisch). Bewijs geleverd: old-script-run
  vs new-script-run, alle 210 bestaande clips decoded PCM-gelijk (ogg-bytes zijn zélf bij een
  ongewijzigd script niet reproduceerbaar - ffmpeg ruis; in TE-DEPLOYEN staat dus: hele pack
  vervangen). make_sound_pack -> Wildlife-sounds.zip; merge BUILD 12->13 (build12 opgeruimd),
  audit exit 0 (2486 models, 0 problemen).
- mvn clean package groen; Wildlife-0.7.0.jar sha1 eecd4c4f..; models-zip sha1 ead5f4c1..;
  pack-build13 sha1 fb5abd29.. -> TE-DEPLOYEN punt 24 (punt 15/21-boxen van vandaag wijzen
  ernaar: 0.7.0 bevat 0.6.2, build13 bevat build12). Wiki 02-species.md: sectie 'Spirits and
  placed creatures'. Not live-tested: dwarrelhoogte/deeltjes/ambient-volume en de death-sink
  moeten in het spel worden gezien; /wildlife spawn wraith bij overdag-light = direct fader.



### StaffHub 0.4.2 t/m 0.4.7 + Relics 0.4.4 - de avond van 28-10 (agent)

- **StaffHub in zes stappen doorgerold** (allemaal eigen build+deploy+meting,
  bak blijft intact: 4 accounts / 35 nodes / 17 pagina's): 0.4.2 inlogbegrenzing
  (tabel loginwacht + audit-log, generieke melding, verlengende slot), deelbare
  links (?open=<id> + GET /api/nodes/<id> + linkknop in de editor) en eigen
  toestellen intrekken (DELETE /api/profile?sessie); 0.4.3 volledige Finse
  interface (185->223-sleutelwoordenboeken, check_i18n.py houdt nl/en/it/fi in
  balans); 0.4.4 archief-uitpakken met UITPAKPLAN-eerst (eigen zip-lezer
  src/lib/unzip.js: geen npm-dep, wijst zip64/versleuteld/'..'/bommen af,
  strip gedeelde topmap, macOS-rommel weg; twee-staps-API /api/archive met
  uuid-token in data/archief, 1 dag zelfopruiend; zelfde naam = automatische
  revisie - de hub was er al zo gebouwd) + professionele huisstijl (palette in
  globals.css: staalblauw/slate, kolomkoppen, chip-gebruikersnaam); 0.4.5 taal +
  "leesbare tekst" als ACCOUNT-VOORKEUR (users.prefs, meereizend naar elk
  toestel; AI-bord stuurt de interface-taal mee in elke vraag en antwoordt erin;
  vertalen kan nu ook naar het NL - de nl-variant wint van de bron zodra hij
  bestaat; Atkinson Hyperlegible woff2 uit google/fonts, lokaal in
  public/fonts); 0.4.6 mediaspeler (audio-balk play/stop/vorige/volgende/
  shuffle/herhaal op de map-siblings, video native + playlistknoppen, 3D via
  three.js lui geladen - sleep-draaien/scroll-zoom/animaties; file-route
  streamt nu 206-Partial-Content op Range) en 0.4.7 het BEHEERPANEEL (schild-
  knop, alleen owners): tellingen, alle sessies intrekbaar per account,
  audit-logboek - de API's bestonden al maanden, het venster was de rest.
- **Eigen logo** (eigenaarsbesluit 28-10, screenshot-goedkeuring): slate-tegel
  + staalblauw anker in lucide-verhoudingen; generator StaffHub/tools/
  make_icons.py (handgetekend - geen font op deze machine tekent U+2693 écht)
  levert favicon.ico, icon-192/384/512, apple-icon én desktop/build/icon.ico;
  server-icoon voor het multiplayer-scherm via scripts/make_server_icon.py
  (importeert dezelfde tegel; vanilla server-icon.png = exact 64x64 + Paper
  server-icon64..1024; 6 png's staan in de server-root, effectief bij start).
- **3D merk-logo (render-laag, trailer/titelkaarten)**: scripts/make_logo3d.py draait
  headless in Blender 5.2 en bouwt de badge na (lak-schijf, messing rand + bogen,
  IEKCORBAR in Georgia, tandwiel met goud-oog, sakura-tak) - géén stoom/embers
  (eigenaarsbesluit 29-10: het merk zélf is de held). Modus `still` =
  archief/logo3d/logo3d-v5-final.png (goedgekeurd), modus `anim` = 96 frames zachte
  swing-beweging, met ffmpeg gemonteerd tot archief/logo3d/logo3d-v5-anim.mp4
  (1536px, 24 fps, naadloze 4s-lus). Les 29-10: Blender `-b` relatief pad = relatief
  C:\ (altijd absolute uitdir meegven) en 5.2 gebruikt slotted actions
  (action.fcurves bestaat niet meer; fcurves zitten in layers→strips→channelbags).
- **Dockerfile: npm ci -> npm install** - npm 11.19 in node:24-alpine weigerde
  een lock die lokaal (npm 11.6) in sync heette (ontbrekende floating-ui-entry
  van tiptap + root-versieveld); install is ev deterministisch met een lock en
  repareert zulk kruimelwerk. tools/sync_lock.py houdt de root-velden bij
  package.json (let op: draait op script-eigen pad).
- **Relics 0.4.4 (jar in deploy/, sha1 a23d9a0d..)**: de disc-datapack-bug van
  vanavond. Server-log na de eerste start met discs-config: "loaded 9
  music disc(s)" + 9x "could not set the jukebox song (NoSuchElementException)"
  - Discs.installDatapack() schreef via getWorldFolder() naar de DIMENSIE-map
  (world/dimensions/minecraft/overworld/datapacks), en dáár leest de server
  geen globale registries zoals jukebox_song. 0.4.4 schrijft naar
  getWorldContainer()/worldnaam/datapacks/relics_discs (de echte wereld-root,
  waar Tally ook werkt). De songs staan alvast handmatig overgekopieerd naar
  de root (met eigenaars-toestemming), dus EEN herstart maakt de discs werkend
  - de jar is dan nog niet nodig maar voorkomt dat de VOLGENDE disc (via
  tools/make_discs.py) weer in de verkeerde lade valt. De Relics-config op de
  server miste het discs:-blok helemaal (stamt van vóór 0.4.2) -> ingevoegd,
  back-up /root/Relics-config.backup-28-10.yml.
- **Server-batch geplaatst op eigenaarsvraag** ("alles pushen en updaten wat
  er nu naar de server moet"): Wildlife 0.7.0 (0.6.1 weg), Sakagura 0.4.0
  (0.3.7 weg), pack build13 in de mixer (build11 weg), 137 wildlife-bbmodels;
  alle sha1's op de server nagemeten gelijk aan lokaal. Wraith-config staat
  nog op enabled:false. Agent raakte de MC-server voor het eerst schrijvend
  aan - uitdrukkelijk opdracht van de eigenaar, read-only-beleid blijft de
  default.
- **Nieuwe tools**: StaffHub/tools/check_i18n.py (toetsenbalance 4 talen),
  sync_lock.py, make_icons.py, test_unzip.mjs + make_zip_fixtures.py
  (fixtures in StaffHub/tools/fixtures/, 4/4 groen).


### Valhalla-onderzoek (REA) klaar - blauwdruk voor eigen MMO-plugin (29-10 nacht)

- Eigenaar wil ValhallaMMO (1.10.3, +Trinkets 2.9 +Races 2.3, allen Athlaeos,
  betaald/gesloten met licentie-obfuscatie) vervangen door een EIGEN plugin:
  core-features nabouwen, Trinkets/Races als addons. NABOUWEN, niet kopiëren.
- **De Valhalla-data zit AL in onze player database** (s30266_iekcorbar_player_data
  op de Plesk-MySQL): 14x profiles_<skill> (owner/exp/exp_total/level/
  maxallowedlevel + perk-kolommen), PlayerClassProfile/Progress (races),
  trinkets zitten als NBT op de items zelf. Oude sqlite player_data.db is dood.
- Decompileeren met Vineflower (REA zelf heeft geen JVM-decompiler): 1518 +
  61 + 51 (races, protection-klassen uitgesloten) = 1630 bronnen; alles wat
  telt is DATA-gedreven:
  exp-kurves als formule-strings in skills/*_progression.yml, perk-bomen in
  skills/*.yml, rassen/klassen als stat_buffs in races.yml/classes.yml.
  Kernmotor = stat-source-stack (AccumulativeStatManager + ~dozens
  StatSource-klassen) - precies wat onze API moet worden.
- Volledige bevindingen + reproduceer-specificatie: **docs/valhalla-onderzoek-rea.md**
  (gedecompilde bronnen lokaal: archief/valhalla-decomp/, gitignore-werk).
- Next move: feature-keuze met eigenaar (welke skills/perks blijven), dan
  plugin-standaard iekcorbar MMO (naam nog te kiezen), import uit de
  bestaande tabellen, races+trinkets als losse modules.

### Steamworks 0.3.26 - tank- & plateauwagen (29-10 nachts)

- Eigenaar koos "alleen tankwagen" uit de trein-batches; de plateau-wagon heb ik
  er daarna meteen achteraan gebouwd (zelfde ritueel, nog steeds 0.3.26 - de batch
  is niet live tot de eigenaar herstart). Twee nieuwe CarTypes zonder zitplaatsen:
  TANK_WAGON met Car.fluid/fluidAmount (capaciteit TANK_CAP=3000 mB) en drijvend
  kannetje boven het luik als inhouds-display; PLATEAU_WAGON met een 1-slot-inventory
  ('cargo', hergebruikt de bestaande save/load-codes) en de lading als ItemDisplay
  FIXED x1.6 op het plateel (updateLoad, loopt mee in moveEntities).
- Interactie: kannetjes werken exact als bij een normale Tank (1000 mB per klik,
  geen mengen, creative houdt gevuld); lege hand bij stilstaande trein (speed==0)
  pompt de HELE lading naar/uit de dichtstbijzijnde Tank binnen 6 blokken
  (over->vol-melding, ander vocht-> geweigerd, visuals().updateTank + saveSoon).
  Plateau: klik met een willekeurig massief blok (geen eigen items) = vastzetten,
  lege hand = terug; afgebroken wagon drupt het blok (cargo-loop) i.p.v. lek (tank:
  CLOUD + emmergeluid, trains.yml redt 'fluid'/'fluid-amount' én plateau-cargo).
- Modellen tank_wagon()/plateau_wagon() in make_models.py HELEMAAL ACHTER de dusts
  (invoegen halverwege liet 92 bbmodels verschuiven - U()-teller is globaal).
  Bijvangst: sw_locomotive.bbmodel is handwerk (groups + 75 elementen, JSON blijft
  gelijk) → regenereren overschrijft hem, steeds terugzetten met git checkout.
  preview3d-sheets bekeken: shell met koperen banden + ladder + rode klep; plateau
  open met vangringen - herkenbaar als familie van de cargowagon.
- Recepten "P P/PTP/ M " (planken+Tank+mijnkar) en "I I/PIP/ M " (ijzer+planken+
  mijnkar). ultieme commentaarles: ook commentaar ná de ';' verbrak de site-parser
  alsnog (twee addRecipes groeiden aaneen) - in dat blok dus HELEMAAL geen
  commentaar, documentatie hoort in wiki/lore.
- Wiki 05-trains Wagons-sectie uitgebreid (tank + plateau), steamworks-seed 59
  pagina's (plateau in seed ✓), site recipes-data 197 steamworks-recipes (JSON gebruikt
  display-names, niet ids - daar struikelde mijn eerste check op), jar
  2b48119e76ef079c99ce096575ca7c5119beee34 in deploy/, punt 42(c)+(d) +
  twee FMM-modellen naar plugins/FreeMinecraftModels/models/steamworks/. Eerste
  build misser: Location is in Paper 26.x GEEN Vector meer - afstand nearestTank
  handmatig uitgemeten.

### AfkCinema 0.3.0 - klikbare zwevende menu's (29-10 nachts)

- Aanleiding: eigenaar speelde met de Cinematic-fabric-mod (3rd-person werkbank-
  schermen) en merkte op: ONZE custom GUI's (Skript/CMI/plugin-menu's) kent die mod
  niet - "deze feature moet in de cinema-plugin". Navraag: 3D-zwevend menu, allemaal,
  zoals het was → ScreenPanels was er al (12 vakjes, niet klikbaar); deze ronde vol-
  ledig gemaakt: MAX_VAKKEN 12→28, raster naar 5 kolommen, en elk niet-leeg vakje
  krijgt een Interaction-klikker.
- Klik-doorstuurt: linkklik → synthetic InventoryClickEvent op het echte view met de
  rauwe slotindex (26.x-API: InventoryClickEvent(view, SlotType.CONTAINER, slot,
  ClickType, InventoryAction) - constructor met cursor bestaat er niet meer, die
  compileerde pas na corrigeren), sluipklik = SHIFT_LEFT + MOVE_TO_OTHER_INVENTORY.
  Menu-logica (Skript/CMI/onze eigen) prikt er dus doorheen - het platte scherm blijft
  gewoon open als vangnet. Alleen de eigenaar bedient zijn scherm; een ander dat
  aanklikt krijgt een "nee"-toon.
- Config: cine.screen-clicks (default true, bestaande server-configs blijven werken).
- Notities uit de bouw: (1) SlotType.TOP bestaat niet meer → CONTAINER;
  (2) veldtypenaam-index (indexen vs indexes) - javadoc-fout "menu-logICA" mee geprikt;
  (3) TE-DEPLOYEN-blok knippen: de STATE-kop van het 42-blok per ongeluk meevervangen,
  hersteld - de lijst is de status, die moet kloppen.
- AfkCinema-0.3.0.jar: sha1 ff3c714a53a9c872045aeae6a92894b1e022db85 → deploy/ +
  testbank E:\mctest26_3\plugins\ (0.2.5 daar verwijderd). TE-DEPLOYEN punt 44.

### Kerstdorp: demo-schem gesplitst (29-10 nachts)

- Eigenaar dropte `rebirthchristmasdemo-e3070.schem` (Downloads): "dit moeten
  allemaal losse schematics worden voor kerst town".
- NIEUWE TOOL: scripts/split_schem.py - leest Sponge-v2 .schem (varint-blockdata,
  eigen NBT-lezer met echte doubles), vindt gebouwen als 26-samenhangende brokken,
  smelt marge-overlappende boxen, negeert doorlopende grondslappen (lagen >85% vol)
  en neemt BlockEntities/Entities positioneel mee. Check: formaat van
  make_trailer_schems.py's controleer().
- Die "demo" bleek klein: 50x60x94 met 11k blokken = 2 lagen zwarte-betonvloer +
  precies 3 bouwsels. Geleverd in deploy/kerst-town-schems/:
  * kerstdorp-01 - grote kerstboom (berk+spar, gouden ster, end-rod glans) 16x29x16
  * kerstdorp-02 - rode wolvilten-hut (wol/concrete/zandsteen) 12x12x12
  * kerstdorp-03 - smalle lantaarn/toren (mangrove+quartz) 5x18x11
- Bodem afgekapt (geen zwarte vloer meer mee in de files), marge 1 blok rondom.
  In-game laden als destijds de trailer: //schem load + //paste -a.
- Twee Python-lessen van vanavond: (1) de bekende `d[_rstr(f)] = _rread(f, t)`
  regel - RHS wordt vóór de subscript-sleutel geëvalueerd, dus two-lines verplicht
  (stond al als waarschuwing in make_trailer_schems, toch weer gemaakt); (2) split
  naar bestaande tools pas echt testen via hun eigen check-commando.

### Mailronde naar staff (29-10 avond, status)

- Op verzoek van de eigenaar in chat opgesteld: een Italiaans bericht aan greyarts
  (Marco) over StaffHub-gebruik (voor hem gebouwd), opendere communicatie en de
  staff-verdeling. De eigenaar verstuurt het zelf als DM; observaties over ander
  staff zijn bewust als één-op-één-vertrouwelijk gemarkeerd in de tekst.
- UPDATE uren later: **afriendlystomach (Tuomo) resigned**. NDA-PDF voor hem
  ingetrokken (tekende nooit → geen boetebeding; rest zwijgplicht spelerdata),
  tracker + board bijgewerkt op 3 pdf's. Servercheck (read-only): géén ops/CMI-rollen
  aan hem gekoppeld - niets in te trekken op de VPS.
- Opvallende bijvangst bij die check: **"_Gabes_" staat wél als OP4 in ops.json**
  maar kwam in geen enkel staff- of NDA-verhaal voor. Vragen aan eigenaar: is hij
  staff (dan NDA + board) of vriend/gast (dan overwegen of OP af mag)?

### Cinematic Interaction — REA-analyse eerste passage (29-10 nachts)

- Eigenaar gaf `Cinematic Interaction-Fabric-26.3-0.18.30.jar` (Downloads) met de
  opdracht: in de wachtrij + met REA analyseren + zo goed mogelijk nabouwen als
  plugin voor 26.2 én 26.3.
- Jar naar archief/cinematic-interaction/ (binary, buiten git). REA: open_binary +
  inspect_artifact (identity sha256 bb74ca7d…, zip-inventaris) — REA heeft geen
  plain-Java-decompiler, dus bytecode-reader zelf geschreven (constant pool parser
  in %TEMP%/cpool2.py) en klassenamen/mixins.json/fabric.mod.json gecombineerd.
- Bevindingen: mod = complete server-zijde (19 StationKinds, Sessions, per-station
  watchers met delta-detectie, TableCrafting-engine, AddonStations-API) + 17
  versie-gebrandde payloads met actie-grammatica (BEGIN/CLICK/PAINT/CRAFT/LEAVE/
  WINDOW/PLACE/SPREAD/QUICK/CHOOSE, velden pos;kind;action;index;button;shift;side)
  + client-cinema (217 klassen, 26 camera/GUI-mixins) die we zó niet kunnen.
- Conclusie (uitgewerkt in **docs/cinematic-onderzoek-rea.md**): session-model en
  actie-grammatica 1-op-1 na te bouwen in Paper; presentatie als server-getrouw
  zwevend paneel (AfkCinema-/cine-screen-leer) draait op elke vanilla client.
  Bouw-sessie volgt: eerst TABLE + FURNACE prototype, tweede REA-passage
  (validatieregels uit Table/FurnaceActions) pas ná eerste werkende versie.

### Quest-book model + BetonQuest COMPASS (30-10)

- **Core-pack voor het journaal-boek**: nieuwe generator
  scripts/make_core_pack.py -> scripts/core-pack/Core-pack.zip. Rode lederen
  tome met messing hoeken/rugband/slot (4 vierkante UV-regio's op een 32x32
  textuur, alles binnen de 0..32 audit-grens). Werkt via
  assets/minecraft/items/written_book.json range_dispatch op
  custom_model_data[0]>=100; BQ config journal.custom_model_data=101 geeft het
  journaal die drager. Alle andere boeken blijven vanilla.
- **Pack build19**: Core-pack.zip als 15e bron in merge-packs SOURCES; audit
  0 problems; sha1 1e6066a8bd5301079b063e1d6a63ca778dda3e09 (17.85 MB).
  build17 was nog nooit live - alle losse build17-verwijzingen in TE-DEPLOYEN
  naar 19 getrokken, 43 herschreven met item (d).
- **COMPASS (inzetten = klaar)**: Milo-pakket krijgt compass-sectie
  (naam+location 2902;88;-408;iekcorbarSeason2, uit Citizens/saves.yml ID 136)
  en de accept/complete acties doen compass add/del milo. Draait op de server
  (scp + grep 5x compass bevestigd); nog /bq reload door de eigenaar.
  Backpack-compassknop blijft UIT (compass_button: '') - die waarde moet een
  package-item zijn en een vanilla-referentie riskeert een config-fout bij
  reload; de spelerroute is gewoon het commando /compass.
- **Igor-compass klaar gezet**: in halloween_lanterns staat de compass-sectie
  UITgeschakeld met instructie (na /npc create Igor: location invullen +
  compass add/del in hl_start/hl_complete).
- Leerpunt opgeslagen in audio/pack-skills: uv 0..32, geen minecraft:compass
  als backpack-knop zonder item-geresolveerd.

### Milo's quest + echte NPC-stemmen + pack build16 (30-10)

- Eerste eigen quest gebouwd: **De verdwaalde karavaan (Milo)**. Pakket in
  git: pluginset-aangepast/BetonQuest/QuestPackages/milo/package.yml.
  Mechaniek 1:1 gekopieerd van vorkan/mad_doctor (item-condities, take,
  delay-timer, tags, journal, ❗/❓ hologram, notify io:title). Accept ->
  craft 4× simpel papier -> inleveren -> 300 cash + 10 elitecoins
  (em money add) + tringeling; herhaalbaar met 15 min cooldown (150 + 2).
- **Echte gesproken stemmen**: sounds/make_voices.py + sounds/voices/<npc>.txt
  (id|tekst) - edge-tts (en-IE-ConnorNeural voor Milo, en-US-GuyNeural
  machine), ffmpeg naar ogg mono 44,1k, events <npc>.<id> toegevoegd aan
  steamworks/sounds.json. 9 regels ≈ 500 KB. HEILIGE VOLGORDE:
  make_sounds -> make_voices -> make_blocks_pack -> merge (states in
  audio-craft skill).
- **Pack build16 gebouwd**: 17,25 MB, 6187 files, audit 0/0,
  sha1 568df8c1b5b067e984b306675b2f5afe86d5d9f6 (vervangt build15 - nooit
  live geweest). TE-DEPLOYEN: punt 43 herschreven + nieuw punt 51
  (Milo plaatsen: /npc create (id 129 voorgevuld), skin/item-drop-truc,
  sftp mapje, /bq reload, test).
- Randgeval voor greyarts-antwoord: de quest spreekt Engels; als staff
  later NL-stemmen wil: VOICES-dict + andere tekst-regels, zelfde pijplijn.

### IekQuests 0.5.0 - golf 3b + 4 uitgerold: alles behalve tutorial (30-10, ~08:45)

- "migreer alles" afgerond (behalve golf 5): Rod_merchant, 24 NPCGuideGuards,
  Backpack-ketting en JobsRank1-5 staan nu LIVE in IekQuests. Log: 51 quests,
  0 SEVERE, data.yml nog leeg (geen bestaande voortgang verloren).
- Engine 0.5.0 erbij: (a) GUI-verbreding - keuzelijsten en voltooide dienst-
  panelen groeien naar 54 slots (26 keuzes), questboek gepagineerd (35/pagina);
  (b) `deliverall`-stages + multi-goederen-contracten (Backpack-fasen, Orion);
  (c) `menu`-type stage zonder act-knop (guard-menu's, kijschermen);
  (d) dienstvoortgang: keuze-met-goto op een VOLTOOIDE quest heropent de quest
  op die stage (jobs wisselen, backpack herhalen - de "menu-goto-on-done");
  (e) condities `qtag:<quest>:<tag>` ( Backpack-pad-keuzes lives across quests)
  en `npc:<id>` (keuze alleen bij de juiste NPC - jobs-masters);
  (f) acties `joinjob`/`leavejob` (performCommand - de BQ-bridge "nujobs_joinjob"
  zat IN de BQ-jar en is dus dood; confirm is uit in Jobs generalConfig),
  `chat <tekst>` (Orion-shops zeggen "hi 1/2/3"), exacte display-naam "=Tuna"
  (vis vs Nuptuna), repeat-contracten met `name:`-filter, walk-stages `flat:`
  (alleen XZ - de WorldGuard regio's liepen van Y -64 tot 319).
- Coördinaten: explore-regio-centra + kasteel-explorer-punten gehaald uit
  plugins/WorldGuard/worlds/iekcorbarSeason2/regions.yml (point1/point2);
  bp3-ingang 2874;67;-589 uit de BQ-notify-tekst.
- Bron-generator: scripts/gen-iekquests-wave3b.py (markering >>> wave3b <<< in
  resources/quests.yml; idempotent). vissen: MMOItems fish.yml -> material COD
  + display-namen uit de itembestanden.
- Deploy door de agent (server was leeg): jar sha1 7fea6970... + quests.yml
  (bak: quests.yml.bak-wave3b). TE 55 = LIVE-record. Container nu edf31e406c27.
- Status docs/quests-migratie.md: alle migratiegolven op ✅ behalve
  tutorial (⛓️ greyarts). RankTime was een dode verwijzing in BQ
  (package van 3 regels, leeg) - archief.

### Steamworks 0.3.27 - placement-rotatie (30-10 ochtend)

- Eigenaar: "stoom - model en geen rotatie mbt placement, alleen 1 facing,
  niet de bedoeling". Gevolgd: ketel/mixer/tank/refinery/stoomketel/fluit/
  robotarm/depot rendeerden altijd Zuid (Visuals fronste as-Y vast op SOUTH).
- Fix op drie punten: (a) PartListener.place - bij plaatsen op vloer/plafond
  krijgt de machine facing = kijkrichting van de speler (zij-klik blijft zoals
  Create tegen het aangeklikte vlak), (b) Visuals - Y-machines met horizontale
  facing tonen die als voorkant; oude nodes (facing=UP) vallen terug op Zuid,
  dus geen databewerking nodig, (c) Kinetics - wrench draait bij die machines
  nu het voorkant rond (blijven rechtop; canRotate + nieuw turnFace-geval).
- KineticsTest 70/70 groen; geen modellen/pack gewijzigd (rotatie zit in de
  display-quaternion). Wiki 02-power wrench-sectie + seed (59 pagina's) bij.
- jar: deploy/Steamworks-0.3.27.jar sha1 6df44eced137535898b3eb28f4fa8736e0718db5;
  TE-DEPLOYEN punt 54 (vervang 0.3.26, herstart). Server was leeg bij klaarzetten.
- Ook vastgelegd: ochtend-uitrol (Steamworks 0.3.26 + IekQuests-golf 1/3 op de
  live server, TAB geconfigureerd, Valhalla dubbelde grid_recipes opgeschoond)
  → zie werklogs hierboven + board.

### IekQuests 0.4.0 - golf 3 uitgerold: Mad Doctor, Usop, Techcore, Cleric (30-10, ~05:45)

- Eigenaars-regel aangenomen: "teleport is staff-only - spelers gebruiken het
  kompas of zoeken zelf". run() 'teleport' doet nu: admin → teleport, speler →
  compass-punt + "make the journey yourself". Usop-rit en Mad-Doctor-pull
  vallen daaronder (dus ook de BQ-trekschroef naar het laboratorium is weg:
  je loopt er zelf heen).
- Engine erbij: 'pay N' (cmi money take), geld-condities 'money>=N'/'money<N'
  via Vault-reflectie (cleric-betaling), Stage 'name'-filter (deliver/craft
  tellen alleen items wier display-name de tekst bevat - Techcore-staal is
  een genamete iron ingot, dus grauw geschilderd ijzer wordt afgewezen),
  dag/nacht-bug fixed (|| was altijd waar → &&), wizard-Help aangevuld.
- quests.yml: +4 quests = 18 live. Mad Doctor = 25-stappen sequence (md_show:
  scan-gruwel, freeze, poison/regen, moordmodus-terugdraaien, titel,
  perm+ cmi.command.rt, tag) + md_quick. Cleric: gratis eerste ras/klasse-
  wissel (tags), daarna 100k-betaling met geld-checks. Techcore: blueprint
  grant op accept, deliver 'Steel'. Usop: compass-rit.
- Uitgerold (server stond leeg, /stop niet nodig - docker restart volstond
  ná jar+quests-wissel): 18 quests gemeten in log, 0 SEVERE, Done 60.9s.
  BQ-packages-offline erbij: mad_doctor, cleric_change_vallhalla, CapitaUsop,
  Techcore → BQ houdt: Rod_merchant, tutorial, JobsRank1-5, Backpack,
  NPCGuideGuards (guards = 24×21-keuze-menu's: vraagt bredere keuzelijst,
  golf 3b; Backpack = vertakking-logica, 3b).
- deploy/: 0.4.0-artefact weg (live), TE 53 naar 0.4.0, board bij.

### StaffHub 0.4.11 - deletion-bug gerepareerd + uitgerold (30-10, ~05:20)

- Gevraagd: "deletion werkt niet op staffhub of was dat al gerepareerd along
  the way?" - ANTWOORD: nee, was het nooit. Root-cause: lazy-create van de
  Prullenbak-map in /api/trash had een onjuiste INSERT (6 kolommen vs 5
  waardes: VALUES(NULL,?,?,?,?) - één ? te weinig). Live-log bevatte 2×
  "Error: 5 values for 6 columns". Zolang de map niet bestond - wat na elke
  verse DB zo is - crashte ELKE verwijderpoging; de UI slikt het stil.
- Fix: VALUES(NULL,?,?,?,?,?) (trash/route.js). Check: geen enkele INSERT
  meer met kolom/value-mismatch (scan alle api-routes + lib).
- 0.4.10 (vertaalbalk mobiel, TE 49) zat nog lokaal - meegenomen; versie
  naar 0.4.11 (bump_version.py), i18n 242×4, npm build groen.
- Uitgerold op de VPS ZELF (dezelfde route als 0.4.9/0.4.8 eerder): tar
  (src+public+server/config) → /opt/staffhub, .next-cache weg, docker
  compose up -d --build. Container draait 0.4.11; homepage 200,
  /api/trash anoniem 401, logs schoon (0 SQL-fouten), fix-string gevonden
  in de built bundle.
- Bewezen zonder de live-DB te racketen: kopie staffhub.db → /tmp, de
  gefixte INSERT uitgevoerd (node-id 301, Prullenbak aangemaakt), kopie
  weggegooid. Eerste ingelogde staff-lid krijgt de map nu vanzelf.
- TE-DEPLOYEN 49 afgestreept (LIVE), board bijgewerkt.
- LEERZAME FACTOR: "uitgerold en geverifieerd" bij 0.4.8 meette alleen
  200/401 - de lazy-create-codepad werd nooit oefend. Bij UI-feature-roll-
  outs voortaan het echte klikpad (hier: iets verwijderen) minstens één
  keer simuleren (of de codepad-copy-test doen zoals nu).

### Deploy-batch eigenhandig uitgerold (30-10, 05:00-05:10)

- Eigenaar gaf groen licht ("alles wat kan deployen/builden etc") - en de MC-
  server stond al netjes uit (eigen /stop om 02:31, container Exited(0)):
  dus swap zonder spelers-last.
- Bestanden geswap op de VPS (via volume-pad, sha1's gemeten na upload,
  identiek aan TE): Steamworks 0.3.26 (0.3.25 weg), SteamworksAdditions 0.1.3
  (0.1.2 weg), IekQuests 0.3.0 (nieuw), iekcorbar-pack-build19.zip naar de
  RPM-mixer (build14 weg), sw_tank_wagon + sw_plateau_wagon (json+bbmodel)
  naar FMM/models/steamworks.
- BQ-afbouw stap 1: zes pakketten (milo, vorkan_kross, Armorshop,
  Shop_gemologist, Upgradestone, mmoitems_food_shop) verhuisd naar
  plugins/BetonQuest/QuestPackages-offline - geen dubbele NPC-gesprekken.
  Rod_merchant + alle nog-te-migreren pakketten blijven in BQ draaien.
- Container gestart (docker start) en log gecheckt: Done (88.974s), IekQuests
  "klaar - 14 quest(s)", Steamworks enabled, 0 SEVERE/ERROR. RAM: container
  ~10,7 GiB van 21 GiB (warm-up normaal); host 14/31 GiB; schijf 38%.
- Huiswerk: TE-DEPLOYEN - blokken 42/43/45/51/53 samengevoegd tot één
  LIVE-record bovenaan; gedeployde artefacten uit deploy/ weg.
- Rest voor de eigenaar: /npc create Igor Nightgrave + id invullen in
  plugins/IekQuests/quests.yml bij igor (TE 52), plus de paneel-klikken 47/48.

### IekQuests 0.3.0 - in-game QUEST WIZARD + permissies (30-10, ~04:55)

- Eigenaar-wens: "via de GUI een soort wizard om in-game een nieuwe NPC+quest
  aan te maken en natuurlijk in de config" + "zorg je ook voor permissies".
- QuestWizard.java (nieuw): /questwizard of netherster-knop in /questbook.
  GUI-stappen: basis (id/titel/icon/order), NPC-bindgen (bestaand id OF
  NIEUWE Citizens-NPC spawns naast je + pull-to-me), stages talk/craft/
  deliver/walk/kill (locatie = "mijn plek"-knop), gesprekskeuzes met
  goto/cond/do-acties, herhaalcontracten (cooldown/daily/money/eco),
  journal, quest-rewards, condities. Chat-tekstinvoer (cancel/done).
  Beheer-scherm: wizard-quests editen/verwijderen.
- Config-veiligheid: wizard schrijft ALLEEN naar plugins/IekQuests/
  wizard-quests.yml (eigen serialization, geen Bukkit-comments-verlies);
  load() leest quests.yml + wizard-file boven elkaar; handmatige quests
  blijven onaangeroerd.
- Permissies, drie lagen: (1) wizard + admin-acties op iekquests.admin,
  questwizard commando in plugin.yml met permission; (2) quest-condities
  perm:node (bestond al, nu gedocumenteerd in Help-pagina); (3) beloning
  'perm+ node' / 'perm- node' via console-lp (Mad Doctor /rt-unlock kan
  daarmee golf 3 in).
- Alle speler/staff-strings Engels gemaakt (commando-feedback + default
  labels Continue/Hand in).
- jar: deploy/IekQuests-0.3.0.jar sha1 77140ce87b39adcb6ddf3b920b201ac3494bdf62;
  TE-DEPLOYEN 53 bijgewerkt (wizard + permissies + teststappen);
  handleiding: docs/quest-wizard.md (Engels, staff-plakbaar).

### IekQuests 0.2.1 - migratie-golf 1: handelaars + eetwarens (30-10, ~04:20)

- Alle contractcijfers uit de BQ-pakketten getrokken (python-scan): Vorkan/
  Brakka/Petra/Aster 12×12+11 contracten, 8 eetwarens (npc 82 Aiko t/m 89 Yuki,
  ID-mapping via Citizens saves.yml op de server) elk 6 contracten, 400c.
  Cooldown 1320s = exact dezelfde timer die BQ gebruikte.
- Engine-verbeteringen: keuzes van de eerste stage blijven zichtbaar na
  voltooiing (winkelknoppen, tag menu:<i>), contracten-rij verbreed naar
  slots 18-34 (12 contracten past), keuzes verhuisd naar 19-25 (botsing met
  de act-knop op 31 was een bug), `shop`-actie draait nu via de console
  (spelers hebben geen mi-rechten, BQ deed het ook console).
- quests.yml: 14 quests (milo, igor + 4 handelaars + 8 eetwarens), alles
  Engels, beloningen nu MET elitecoins (2-5 per schaal - in BQ was het alleen
  munt). Generator: scripts/gen-iekquests-merchants.py.
- Rod_merchant UITGESTELD: contracten zijn MMOItems-vissen (PYROFISH_BRONZE_*),
  engine kent mmoitem-matching nog niet - hij blijft op BQ tot golf 2.
- Dubbele-NPC-klik-valkuil gedocumenteerd: bij deploy de 5 BQ-package-mappen
  mee naar QuestPackages-offline\ (anders praten BQ én IekQuests tegelijk).
- jar: deploy/IekQuests-0.2.1.jar sha1 217da0e38c1ba4ce46c7538561fa1010b299e987;
  TE-DEPLOYEN 53 herschreven; docs/quests-migratie.md status-tabel bij.

### IekQuests 0.2.0 - feature-pariteit + alles Engels (30-10)

- Taalregel van de eigenaar: alle speler-tekst is Engels
  (quests.yml herschreven, GUI-strings in de code, plugin.yml-description).
  Code-commentaren blijven Nederlands (huisstijl, niet zichtbaar).
- Nieuwe engine-features voor de migratie van greyarts' 17 pakketten: tags,
  points + condities (>=), vertakkende keuzes per stage, kill-stage, sequences
  (getimede actie-reeksen als BQ-folders), daily-repeats, rewards shop/teleport/
  effect/give/compass/tag/point, start-condities, random-start (daily contract).
- Handtekening-refresh: geopende GUI's tekenen alleen opnieuw als staat/teller/
  cooldown echt verandert (geen gefladder meer).
- vorkan_demo in quests.yml = migratie-voorproef in de nieuwe taal.
- jar: deploy/IekQuests-0.2.0.jar sha1 cf4f1d129492490a610b34bc41c6cd60b635a043;
  TE-DEPLOYEN 53 + board bijgewerkt. Plan: docs/quests-migratie.md.

### IekQuests 0.1.0 - eigen quest-plugin (30-10, ~/3 uur werk)

- Eigenaar: "helemaal van BQ af, eigen plugin met 'quests' in de naam". Gebuild:
  **IekQuests** (com.iekcorbar.quests.IekQuestsPlugin) - los van BetonQuest,
  BQ blijft voorlopig naast ons draaien voor greyarts' quests.
- Werking: quests.yml = gespreksregels + stages (talk/craft/deliver/walk) +
  rewards (money|eco|title|sound|say|rauwe commando's) + herhaalbare contracten
  met eigen cooldown. GUI /questbook (hoofdmenu + per-quest boekvenster met
  live tellers); NPC-klik via reflectie op Citizens-id (geen harde dep);
  compass-knop per walk-stage (MetaCompass via reflectie); NPC-stemmen spelen
  steamworks:milo.lineXX / igor.lineXX uit pack build19. Data: data.yml.
- Compileerlessen (stonden allemaal in de weg): Paper 26.2 API kent
  PlayerRecipeCraftEvent NIET -> org.bukkit.event.inventory.CraftItemEvent +
  getInventory().getResult(); geen ITEM_COMPASS_CLICK -> BLOCK_NOTE_BLOCK_PLING;
  executor-signatuur is (sender, command, label, args) - eerst omgedraaid;
  UI-tags gaan op het PDC (onzichtbaar), niet in de lore; geen config.yml →
  geen saveDefaultConfig() (gooit exception anders).
- Voorbeeldquests mee: milo (npc-id 136, craft 4 papier → deliver → 300+10,
  herhaal 900s) en igor (Halloween: 8 kaarsen craften + 6 pompoenen → 400+15+
  pompoenhoofd, trick-or-treat herhaal 600s; npc-id nog -1 tot Igor geplaatst).
- JAR: deploy/IekQuests-0.1.0.jar sha1 1ef828df11ce7e30ab7939856da57272fe40d923;
  TE-DEPLOYEN punt 53 (incl. waarschuwing dubbele Milo: BQ-kopieën milo/
  halloween_lanterns bij deploy naar QuestPackages-offline).
- Notities voor 0.2: status via BQ-punten/tags als we die willen lenen; meer
  doeltypes (kill/visit-meerplekken); icoon-heads; en de grote migratie van
  greyarts' 18 pakketten - NIET vanavond meer.


### Alles Engels + StaffHub 0.4.10 (30-10)

- Eigenaar: alle staff-content in het Engels. De 8 quests-gidsen zijn
  herschreven in Engels (deploy/staffhub-quests/: 00-overview t/m
  07-new-quests-roadmap; de NL-versies zijn verwijderd - hub wordt Engelstalig
  geïmporteerd). Ook deploy/quest-sounds/ Engelstalig gemaakt (voice-milo-*,
  quest-complete-chime, choice-click + mp3/ogg-mapjes + EN README).
- **Vertaalbalk-melding van de eigenaar onderzocht:** de balk stond
  alleen in de desktop-aside (md: only); het mobiele telefoon-blok (Hub.jsx
  L468+) miste <Vertaal>. Niet "weg", maar nooit op klein scherm getekend.
  Fix: `<Vertaal node={sel} />` toegevoegd in het md:hidden blok (pages-only,
  zelfde regel als desktop). Nevenfeit: bestanden (.md) hebben nooit een
  vertaalbalk - alleen pagina's; is ontwerp, staat in TE-DEPLOYEN punt 49.
- StaffHub 0.4.9 -> **0.4.10** (bump_version.py), `npm run build` groen,
  check_i18n 242/242/242/242. Uitrollen = eigenaar (TE-DEPLOYEN 49).

### StaffHub quest-gidsen + git-backup alle quests + quest-stemgeluid (30-10)

- Nieuwe skill `monitor` (~\.claude\skills\monitor): één ssh-scan
  (scripts/healthcheck.sh) over RAM/schijf/logs/processen/groei; drempels en
  rapportstijl in SKILL.md. Live-getest.
- Quest-voice: make_sounds.py uitgebreid — `voice.milo`/`voice.machine`
  (3 varianten, dankzij _vseed-teller onderling verschillend) +
  `quest.complete`/`quest.click`; alle generatoren met EIGEN lokale Random,
  de gedeelde seed-stream blijft onaangeroerd (audio-craft regel). Pack-build
  16 nog tegoed.
- **StaffHub quests-map**: deploy/staffhub-quests/ 00–07 (overzicht /
  handelaars-dailies / eet-en-drinkhallen / backpack-keten / jobs-ranges /
  tutorial / one-offs / routekaart), geschreven uit de ECHTE pakketten
  (19 stuks getarrend en uitgelezen). Nieuwe kennis: de 5 handelaars + 8
  eetwarens delen één daily-mechaniek (take prefix-items + vriendschaps-
  punten + 22 min delay-cooldown + cmi money 250–900); JobsRank1 geeft
  `lp parent add rank_1` — botsing met de CMI-ladder (`parent set`) is
  gearnoteerd in gids 04 en in het greyarts-bericht.
- **git-backup**: alle QuestPackages staan vanaf nu in
  pluginset-aangepast/BetonQuest/QuestPackages/ (46 yml's; .gitignore kreeg
  één uitzondering voor die folder - de rest blijft buiten, want andere
  plugin-configs kunnen secrets bevatten).
- **RankTime-vondst**: het "lege" pakket is een SLAPENDE tweede
  speeltijd-rangladder (events/objectives/journal, `lp parent add rank_N`,
  zelfde Japanse range-namen als de CMI-ladder). Niemand triggert hem
  (geen start-aanroper). Damit zijn er DRIE range-systemen (JobsQuest,
  CMI-ladder, RankTime) - vraag aan greyarts welke de baas wordt; staat in
  StaffHub-gidsen 04+06 en in de werklijst voor z'n antwoord.
- Milo's karavaan (eerste nieuwe quest, gids 07): menu-IO + typ-effect +
  stemgeluid per NPC; beloning cash + elitecoins (`em money add`); Zeta pas
  na SiteSync-`/izeta`-werkblokje.

### RAM-groei op de server: BluMap eraf + plafond op Java (30-10 nacht)

- Eigenaar zag in het paneel RAM richting 16/20 GB (normaal 10-11), "sinds Earis
  joinde". Oorzaak, in drieën: (1) de start-up geeft Java GEEN plafond
  (-XX:MaxRAMPercentage=95 van de volle 21 GB) dus de heap kruipt omhoog en geeft
  nooit terug; (2) BluMap rendert elke door spelers beladen chunk in het
  serverproces mee - deze week 53.000 tegels (13 GB op schijf) terwijl niemand
  naar de live map kijkt; (3) lange uptimes + /reload-resten. Earis zelf deed
  niets verdachts: /rt ×16 + /back ×25 laadt alleen steeds nieuwe gebieden.
- GEDAAN (agent, alleen-verplaatsen): BlueMap-jar uit plugins/ -> plugins/disabled/
  (30-10 00:19). 13 GB mapdata laat staan tot schijfruimte nodig is.
- VOOR EIGENAAR (paneel-klikken, TE-DEPLOYEN punten 47-48): STARTUP-line
  `java -Xms8G -Xmx14G -Dterminal.jline=false -Dterminal.ansi=true -jar {{SERVER_JARFILE}}`
  + Schedules: elke dag 05:00 commando `restart`.
- Vondst terzijde: op de VPS draait een TWEEDE server-container
  (c9bda5ec = "Fenneswereld", Forge, van nichtje; 2,7 GB, staat sinds
  26-10 ledig maar 'paused'). Laat staan. NOTitie voor later: eerdere "hangend
  proces"-analyse was deze container - die hangde níét, de MC-container sloot
  netjes (Exited 0) omstreeks 00:04.
- Les voor disk: 23 GB in de volume = bluemap 13 + world 7 + plugins 1.8.

### Receptboek-herstel + Additions 0.1.3 (29-10 nachts)

- Klacht eigenaar: "vent grille recipe zit niet goed" én "craftbare recepten
  worden niet altijd geladen in de workbench, soms wel soms niet".
- Oorzaak 1 (het algemene): alle Steamworks-recepten gebruikten generieke
  PAPER/NOTE_BLOCK RecipeChoices als part-sloten + validCraft als bewaker. Het
  vanilla-receptenboek vult daarmee willekeurig papier in het raster → geldige
  poging wordt afgekeurd; pictogrammen zijn kaal; "craftable"-filter liegt.
  Dit verklaart de willekeur. FIX: PartItems.addRecipe zet nu per part-slot een
  RecipeChoice.ExactChoice van het ECHTE item (create/createMaterial/createCar/
  decor.item), en registerRecipes() draait opnieuw zodra waitForModels langs is
  (resultaten hadden toen pas hun FMM-model; recipeKeys/Specs worden vooraf
  geleegd zodat herregistratie de lijsten niet laat aangroeien). validCraft blijft
  als dubbele zekerheid.
- Oorzaak 2 (vent grille specifiek): de Bukkit-recepten van Additions stonden
  WEL geregistreerd (shapeless, vanilla-ingrediënten) maar de eigen
  ZukanData-provider leverde een lege recipes-lijst ("Zukan leest ze zelf wel" -
  bleek onjuist: Zukan telt wat de supplier geeft: log "SteamworksAdditions:
  0 recipes"). Daardoor ontbrak de vent grille in de Zukan-receptenviewer/site.
  FIX: Additions 0.1.3 levert de drie recepten als echte 3x3-kaarten.
- Getest op testbank E: (26.3): Done 59.6s, "All Steamworks models found" ×2
  (Steamworks + Power whenModels), geen exception bij de ExactChoice-herregistratie.
  LESSERS: (a) mijn eerste test draaide per ongeluk op de OUDE deploy-jar -
  houw de sha1 in de dop voordat je een build testeert; (b) PowerShell-`$(...)`
  in ssh-opdrachten interpoleert lokaal → via .sh-pijp.
- Files: PartItems.java (ExactChoice + clear), SteamworksPlugin.java (herregistratie
  in whenModelsReady-callback), Additions ZukanData.java. Jars: Steamworks-0.3.26
  sha1 e645794333881710901a15e113f87837f000d8d6 (vervangt 2b48119e), Additions-0.1.3
  sha1 a85bbef1274b91269405aabbbe5e2276e60753d8. TE-DEPLOYEN: punt 42 om (e)+
  nieuwe hash, punt 45 nieuw.

### Structuur: projectbord + grote opschontronde (29-10 avond)

- Aanleiding eigenaar: "we moeten echt wat gestructureerder gaan werken" midden in
  een sesión met drei lopende blokken (treinen, NDA, cleanup).
- NIEUW: **PROJECTBORD.md** in de repo-root als enige statusplek per werkblok
  (live / wacht-op-jou / bezig / wachtrij + open vragen). Regel: elk afrondend
  blok raakt het board, detail blijft in dit werklog.
- NIEUW: skill **cleanup** (C:\Users\baaye\.claude\skills\cleanup) met de vaste
  einde-ronde: bevestigds weg uit deploy/ + blokken snijden uit TE-DEPLOYEN +
  board bijwerken + git-ronde. Harde regels: twijfel=laten staan, persoons-
  gegevens alleen in gitignore-gebied, VPS-nooit-zonder-goedkeuring, en de
  PowerShell-les: Get-Content/Set-Content op UTF-8 bestanden makt molekbollen
  (vanavond 3 doc's geamoeerd uit git nadat een padverwijzing zo was opgeslagen).
- Grote schoning uitgevoerd: TE-DEPLOYEN 1300→993 regels (blokken 33-41 waren
  allemaal live/vervangen - 8 STATE-blokken weg), 10 afgeleverde artefacten uit
  deploy/ (eieren-jars, AfkCinema, SiteSync jar+yml, staffhub 0.4.7/0.4.9),
  upload-2026-10-24/ (43 MB snapshot) weg, root-`resourcepack\` (verhaalde
  324-bestands Relics-kopie, git-tracked → git rm) weg. deploy/ bevat nu alleen
  nog: Steamworks-0.3.26.jar + pack-build15 + desktop-setup + wildlife-fmm-zip
  + TE-DEPLOYEN.
- Nieuwe indeling: `archief/` (gitignored) voor render-/onderzoeksresten:
  logo3d, trailer, trailer-schems, valhalla-decomp; padverwijzingen in
  CLAUDE.md + docs bijgewerkt. NDA-generator verhuisd van %TEMP% naar
  docs/nda/signed/make_nda_pdfs.py (blijft buiten git: namen).
- Open vragen op het board: iekcorbar_iekje_barend.zip (28-10 site-build, geen
  punt - bewaren of weg?), desktop 0.1.3 uitgedeeld? (punt 25), punten ≤32
  straks server-voor-server verifiëren en de lijst naar 0.

### Steamworks 0.3.26 - treinlijnen & dienstregeling + windmolen-woosh (29-10 avond)

- Eigenaar koos de trein-batch "Lijnen & dienstregeling" én meldde kort daarna:
  "het geluid van de windmill is erg irritant - moet een subtiele woosh worden,
  zoals wind door de bomen". Beide in dezelfde (nog niet gedeployde) 0.3.26.
- LIJNEN (eigen design, Create heeft dit niet - Train.java/Trains.java):
  * Train krijgt `line` (gesorteerde lijst stations-NAMEN, max 9), `linePos`
    en `lineDir`; opgeslagen in trains.yml als `line:`. Lijn leeg = oud gedrag
    (bij élk station stoppen) - alles blijft backwards compatible.
  * scan() filtert via stopsHere(t, station): een trein met lijn stopt alleen
    bij eigen haltes, passeert de rest. Bij aankomst: linePos optellen met
    lineDir, bounce (omkeer + turnAfterWait) aan de list-einden = shuttle.
  * LIJNBORD: klik locomotief met papier → 54-slot GUI (LineHolder); rij 1 =
    haltes in rijvolgorde (detector-rails, klik=verwijderen), rij 3+ = alle
    benoemde stations van die wereld (rail-items, klik=toggle). Per wijziging
    save() + noot: bewust zender permissie-check (zoals instappen).
  * Mee: CarType LOCOMOTIVE lore + Manual-tip + wiki 05-trains "Lines and
    timetables" + steamworks-seed herbouwd (59 pagina's).
- WINDMOLENGELUID (sounds/make_sounds.py + SteamworksPlugin.machineSounds):
  * Oude `windmill.creak` = gezaag (zaaggolf 140→160 Hz) - weg. Nieuwe
    `windmill_woosh()`: gefilterd ruis (bandpass 500↔1100 Hz sweep + lage
    adem-laag), sine-envelope, 2,8 s. EIGEN `random.Random(7301)`: de oude
    creak consumeerde 0 uit de gedeelde seeded stream, dus die blijft 0 -
    geen enkele andere clip verandert van klank.
  * Event omgedoopt naar `windmill.woosh` (sounds.json + lang +ogg's
    regenereerd via make_sounds.py + make_blocks_pack.py; oude creak-ogg's
    zijn uit de Steamworks-blocks.zip - oude root-map eerst weggegooid).
  * Java: `case WINDMILL` speelt nu windmill.woosh op vol 0.12 (was 0.22);
    frequentie ongewijzigd (1/8 per machineSounds-aanroep ≈ om de 12 s,
    alleen binnen 20 blokken van een speler). config.yml-commentaar meegenomen.
- Packritueel: BUILD 14→15, merge + audit groen (0 want our namespaces),
  deploy/iekcorbar-pack-build15.zip sha1 f30cfb223b63180e132117f91373af823e6399c1
  (build14 staat nog op de server-mixer - punt 43 zegt hem verwijderen).
  Jar herbouwd: deploy/Steamworks-0.3.26.jar sha1 e25e1a85f29e06d464c5383fc556224cbbd10fb3.
  TE-DEPLOYEN: punt 42 herschreven (lijnen + woosh, typefouten eruit) en
  punt 43 (pack build15) toegevoegd. Jar en pack samen deployen, anders
  zwijgen de molens.

### StaffHub 0.4.9 - bbmodel-3D-weergave + uitgerold op de VPS (29-10 avond)

- Opdracht eigenaar ("deploy en build de nieuwe staffhub maar hoor" + "moet
  .bbmodel files kunnen renderen"): de 0.4.8-werkblok (bulk-selectie,
  prullenbak-undo, verplaats-klik - zie vorige sectie) kreeg een vierde
  kracht vóór de uitrol en versie 0.4.9.
- NIEUW: src/lib/bbmodel.js - bouwt van een Blockbench-project (.bbmodel:
  cubes from/to + per-vlak UV + base64-PNG-texturen + elementrotatie) één
  three.js-mesh per textuur; Model3D laadt hem op voor .bbmodel met dezelfde
  viewer als .glb (draaien/zoomen/spinnen/hervenen).resolution-rescale,
  Y-uitsnijding cull: DoubleSide + alphaTest, mipmaps uit (pixel-shot).
- Uitgerold 29-10 19:2x: staffhub-0.4.9.zip (88 bestanden, 364 KB, sha1
  d090f3122ebaf59045552e8dc16da687a6c09ddb) → /root → unzip /opt/staffhub →
  docker compose build + up -d. Geverifieerd: container up, home HTTP 200,
  /api/trash HTTP 401 (route leeft = nieuwe build draait). Twee parallelle
  compose-pogingen gaven één naam-conflict (tweede Shell), het eindbeeld is
  één draaiende nieuwe container; de 0.4.8-proefzip is van de VPS gehaald.

### StaffHub 0.4.8 - bulk selectie + prullenbak met undo (29-10 avond)

- Aanleiding: eigenaar zette 117 Steamworks-modellen (234 bestanden) per ongeluk
  los in `Models/` i.p.v. `Models/Steamworks/` en vroeg om undo/bulk-select.
- Nieuw in de hub: vinkjes per rij + ☑ "alles in deze map", toolbar met
  "verplaats selectie" (klik bestemming in de boom; drag pakt nu de hele selectie;
  de bestemmingsknop stond eerst alleen op root) en "selectie naar prullenbak".
- Prullenbak = gewone root-map `Prullenbak` (wordt lazily aangemaakt door
  GET/POST `/api/trash`); oud-thuis (parent+naam) comes in de `meta`-tabel onder
  `trash:<id>` — restores werken dus ook na herstart. Undo-balk (groen, 15 s)
  na elk prullen- en verplaats-actie; "definitief wissen/legen" pas na
  bevestiging. `remove()` is nu non-destructief (bak + undo).
- Lokaal bewezen: next build groen incl. /api/trash-route; check_i18n 242 toetsen
  ×4 talen exactly balanced (twee per ongeluk dubbelgeplaatste blokjes in nl/en
  zijn weer weg). Artefact: `deploy/staffhub-0.4.8.zip` (87 bestanden, 361 KB,
  sha1 21055dd36f3049a141a628306224a286848245a1; kopie ook op de VPS:
  /root/staffhub-0.4.8.zip) + TE-DEPLOYEN punt 41.
- Data-reparatie (opdracht "alle content moet kloppen", agent vocht uit): VACUUM
  INTO-consistentiebackup `/app/data/staffhub-backup-20261029.db`, daarna één
  UPDATE: 234 sw_bestanden parent 39→40 (Models/Steamworks was toen leeg).
  Geverifieerd: 0 losse files meer in Models; de pagina 'oops' (id 280) en de
  mappen Kitchen/Sakagura/Techcore zijn bewust níét aangeraakt.
  Docker-deploy van de bron (unzip /opt + compose build) = moment van eigenaar.
