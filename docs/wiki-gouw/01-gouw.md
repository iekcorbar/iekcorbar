---
slug: gouw
title: Gouw — Land Claims
category: Gouw Land Claims
parent:
---
Gouw claims **chunks** (16×16 blocks). Stand in a chunk and run `/gouw claim` and it's yours: the action bar tells you, and `/gouw border` traces the outline of the whole gouw with golden particles.

**What a gouw protects.** Outsiders can't break or place blocks, can't fill or empty buckets, and can't open anything — chests and barrels fall under the **Containers** right, doors, beds and buttons under **Interact**. They can't attack animals or mobs living there, and they can't fight players inside either. Even explosions — creeper or TNT — won't chew holes in claimed ground, and pistons are stopped at the border. Staff with the bypass toggle are always allowed through.

**Who may do what.** The owner (the **mayor**) can always do everything. Officers can too. Residents each carry their own set of rights — see [Towns & Rights](/wiki?page=gouw-towns) for how the mayor hands those out per person.

**Limits.** Every player may claim **16 chunks** by default (staff can change this in the config, and rank groups get more through the `gouw.chunks.<n>` permission). Beyond that, a town can simply **buy** more: `/gouw buy 1` raises the gouw's limit from the town bank (250 per chunk at the time of writing). `/gouw autoclaim 2` claims a 5×5 square around you in one go, which is the quickest way to lay out a base plot. `/gouw unclaim` gives the chunk you're standing in back (mayor and officers); `/gouw unclaim all` gives up everything you own.

**Finding your way.** `/gouw info` shows the gouw under your feet, `/gouw list` shows your town's numbers, and `/gouw near [r]` lists gouws around you. Entering or leaving a gouw shows an action bar and a pulse along the chunk border. Claims of inactive players are only released if staff enable `expire-inactive-days` — on iekcorbar that's currently off (0), so claims stay yours.

Old Lands claims were imported on Gouw's first start and are stored as plain JSON (`plugins/Gouw/claims.json`) — if a claim from the Lands days looks off, tell staff; they can inspect and fix it with `/gouw admin` tools.

[Back to the Introduction](/wiki?page=gouw-intro) · [Towns & Rights](/wiki?page=gouw-towns) · [Commands](/wiki?page=gouw-commands)
