---
slug: gouw-commands
title: Gouw Commands
category: Gouw Land Claims
parent:
---
All gouw commands live under one command — `/gouw` (short: `/g`, alias `/claim`). Typing it with no arguments opens the **gouw menu**, a clickable inventory screen with pages for Residents, Rights and the Bank.

**Land**

* `/gouw claim` — claim the chunk you're standing in (mayor/officers; residents only with the CLAIM right).
* `/gouw autoclaim <radius>` — claim every free chunk within <radius> of where you stand, in one go. radius 1 = 3×3, radius 2 = 5×5.
* `/gouw buy [n]` — buy n extra chunk slots for the town, paid from the town bank.
* `/gouw unclaim` — give back the chunk under your feet (mayor/officers). `/gouw unclaim all` releases your whole gouw (mayor only).
* `/gouw border` — a golden particle outline of the gouw you're standing in.

**Town**

* `/gouw invite <name>` — send an invite (aliases: `/gouw trust`). It stays open until the target accepts - owning your own gouw or living in other towns is no problem.
* `/gouw accept [town]` / `/gouw decline [town]` — take or drop one town's invite. The chat notice carries clickable buttons that name the town; a bare `/gouw accept` lists your open invites so you can pick one.
* `/gouw kick <name>` — remove a resident; residents may also leave themselves with `/gouw leave`.
* `/gouw promote <name>` / `/gouw demote <name>` — resident ↔ officer (mayor only).
* `/gouw members` — list the residents of your gouw with their roles and rights.
* `/gouw rename <name>` — name your town (shown in enter messages).

**Bank**

* `/gouw bank` — balance, chunk numbers and the price of the next chunk.
* `/gouw deposit <amount>` / `/gouw withdraw <amount>` — every resident deposits and withdraws; withdrawals need the money to be in the bank, of course.

**Looking around**

* `/gouw info` — the gouw under your feet: who owns it, size vs limit, residents, bank.
* `/gouw list` — your own town's numbers plus server-wide totals.
* `/gouw near [r]` — the gouws within r chunks, with their distance.

> Jobs are not part of Gouw: your job comes from the onboarding tutorial (Jobs Reborn) and is paid there. The town bank only holds what residents deposit.

[Back to the Introduction](/wiki?page=gouw-intro) · [Land Claims](/wiki?page=gouw) · [Towns & Rights](/wiki?page=gouw-towns)
