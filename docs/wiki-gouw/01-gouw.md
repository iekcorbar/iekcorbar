---
slug: gouw
title: Gouw — Land Claims
category: Gouw Land Claims
parent:
---
Gouw claims **chunks** (16×16 blocks). Stand in a chunk and run `/gouw claim` and it's yours: the action bar tells you, and a particle outline can show you its border with `/gouw border`.

**What a gouw protects.** Inside your gouw, outsiders can't break or place blocks, can't empty or fill buckets, and can't open chests, doors, buttons or any other interactive block. They can't attack your animals or mobs living there, and they can't fight other players in it either. Even explosions — creeper or TNT — won't chew holes in claimed ground. Pistons pushing into a gouw are stopped as well. Staff with the bypass toggle are always allowed through.

**Trust.** `/gouw trust <name>` adds someone; `/gouw untrust <name>` removes them again. Trusted members get build, open, mob-attack and pvp rights by default — the gouw menu (just `/gouw` with no arguments) has four toggle buttons (Build, Open, Mobs, Players) that loosen or tighten those rights for *all* members at once. `/gouw members` lists who's in.

**Limits.** Every player may claim **16 chunks** (server default; staff can change it in the config). `/gouw autoclaim 2` claims a 2-chunk radius around you in one go, which is the quickest way to lay out a base plot. `/gouw unclaim` gives the chunk you're standing in back; `/gouw unclaim all` gives up everything you own.

**Finding your way.** `/gouw info` shows the gouw under your feet, `/gouw list` shows all of yours, and `/gouw near` shows gouws close to you. Inactive claims can be released automatically if the server enables `expire-inactive-days` — on iekcorbar that's currently off (0), so claims stay yours.

Old Lands claims were imported on Gouw's first start and are stored as plain JSON (`plugins/Gouw/claims.json`) — if a claim from the Lands days looks off, tell staff; they can inspect and fix it with `/gouw admin` tools.

[Back to the Introduction](/wiki?page=gouw-intro) · [Commands reference](/wiki?page=gouw-commands)
