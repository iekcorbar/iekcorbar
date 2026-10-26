---
slug: gouw-commands
title: Gouw Commands
category: Gouw Land Claims
parent:
---
All gouw commands live under one command — `/gouw` (short: `/g`, aliases also work: `/claim`). Typing it with no arguments opens the **gouw menu**, a clickable inventory screen for everything below.

* `/gouw claim` — claim the chunk you're standing in. Costs nothing; limited by your chunk quota (default 16).
* `/gouw autoclaim <radius>` — claim every chunk within <radius> chunks of where you stand, in one go. radius 1 = 3×3, radius 2 = 5×5.
* `/gouw unclaim` — give back the chunk under your feet. `/gouw unclaim all` releases your whole gouw at once.
* `/gouw trust <name>` / `/gouw untrust <name>` — add or remove a member of your gouw.
* `/gouw members` — list everyone you've trusted (and who trusted you).
* `/gouw leave` — step out of someone else's gouw you were trusted in.
* `/gouw rename <name>` — give your gouw a personal name (shown in enter messages).
* `/gouw info` — details of the gouw you're standing in: owner, size, members.
* `/gouw list` — every chunk you own, with coordinates.
* `/gouw near` — gouws around you, so you know where you can still build.
* `/gouw border` — a particle outline shows the edges of the gouw under your feet for a moment.

Staff (permission `gouw.admin`):

* `/gouw admin delete <name>` — remove a player's claims (e.g. after a confirmed request).
* `/gouw admin import` — re-run the old Lands import; `/gouw admin reload` re-reads the config.
* `/gouw admin bypass` — toggle walking through and building in *any* gouw while you inspect.

The menu also has a staff page with the same tools as buttons, for admins who prefer clicking over typing.
