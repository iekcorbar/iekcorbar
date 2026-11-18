---
slug: hihou-staff
title: Staff - building and running arenas
category: Hihou BedWars
parent: hihou-intro
---
## Staff: building and running arenas

A BedWars arena is a named collection of points: a lobby and spectator spot, a bounding region for the map-reset, and per team a spawn, bed, shop marker and team chest, plus generator pads. All of it is wired in-game with `/ih bw` commands (staff only unless stated).

**1. Build the map.** The eight `bedwars-*` schematic parts (lobby, four team islands, two diamond mids, one emerald mid) live in `plugins/WorldEdit/schematics/`. Paste them with WorldEdit (`//schem load bedwars-01-rood` + `//paste -a`) in the order printed by `python -I scripts/make_bedwars_arena.py` - the islands share one layout, so keep the spacing from the checklist.

**2. Register the arena.**

* `/ih bw create <name>` then `/ih bw set <arena> spawn|spec|center|size|end <value>` — lobby spot, spectator spot and the reset region (center + radius). `end <seconds>` moves the sudden-death moment when every standing bed crumbles (default 1500 s = 25 min).
* `/ih bw team <arena> add <team> <color>` — red/blue/green/yellow by default, any name works; then stand on the spot and set each point: `spawn <team>`, `bed <team>` (look at the bed), `shop <team>` (look at the anvil), `chest <team>` (look at the team chest). `del <team>` removes.
* `/ih bw gen <arena> add iron|gold <team>|diamond|emerald` — stand on the pad; iron and gold need their owning team, mids do not.

**3. Snapshot before the first game.** `/ih bw snapshot <arena> [lo] [hi]` captures every non-air block in the region. After each match the reset restores **exactly** that state - placed, broken and blasted blocks all return. Take the snapshot while the arena is pristine; without it the reset falls back to the lite path (own placements only).

**4. Running games.**

* `/ih bw list` / `/ih bw info <arena>` — what exists and how it is wired.
* `/ih bw start <arena> [seconds]` / `/ih bw stop <arena>` — force the countdown or end a match; matches also auto-start when the lobby fills the minimum.
* `/ih bw mode <arena> <mode>` — force the next game's twist.
* `/ih bw season` — new season, stats cleared.
* `/ih bw delete <arena>` — forget it (the blocks stay; the reset restores what the snapshot knows).

Config keys (survive restarts): `bw-rewards.kill/bed/win` (coins), `bw-random-modes` (roll a twist per game, default on).

Back to [BedWars](/wiki?page=hihou-intro) · [Modes & rewards](/wiki?page=hihou-modes)
