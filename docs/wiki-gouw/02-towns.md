---
slug: gouw-towns
title: Gouw — Towns & Rights
category: Gouw Land Claims
parent:
---
Since 0.4 every gouw doubles as a small town. No alliances, no wars — just people building something together.

## Who's who

* **Mayor** — the player who claimed the first chunk. The mayor runs everything: invites and removes members, hands out rights, promotes officers, buys chunks with town money, and releases the whole gouw. Only the mayor can promote and demote.
* **Officer** — a promoted resident. Officers manage almost everything the mayor can (claim, unclaim chunks, invite, remove, rights, bank withdrawals, buying) but can't promote, demote or release the whole gouw.
* **Resident** — an invited member who **accepted the invite**, with a personal set of rights. Residents live in the town; whether they may claim new chunks is one of those rights.

Since version 0.5 **an invite is an invite, not a membership**: the mayor sends it, the target gets a chat message with **[Accept]** / **[Decline]** (offline players are notified when they next join) and only then becomes a resident. Being a resident of other towns alongside your own gouw is normal and allowed — neighbours regularly welcome each other, and you can be a member of several towns at once. Your menu and `/gouw info` always act on the town you are standing in.

## Rights, per resident

Six rights can be granted to each resident individually:

| Right | Lets the resident… |
|---|---|
| **Build** | break and place blocks, use buckets |
| **Containers** | open chests, barrels and similar storage |
| **Interact** | use doors, beds, buttons and levers |
| **Mobs** | attack animals and mobs in the gouw |
| **PvP** | attack other players inside |
| **Claim** | add new chunks to the town (`/gouw claim`) |

New residents start with the town's **defaults** (everything except Claim). The mayor or an officer opens `/gouw menu` → **Residents** → clicks a resident's head: six toggle buttons appear, plus promote/demote and remove. Changing the defaults (menu → **New-resident rights**) does not silently rewrite residents who already have their own personal settings.

## The town bank

Every resident may **deposit and withdraw** through `/gouw bank`, `/gouw deposit <amount>` and `/gouw withdraw <amount>`, or with the buttons on the menu's **Bank** page. The bank is where a town keeps shared money: building funds, border expansions, that bridge everyone wanted.

Heads-up: **Gouw never pays salaries.** Your income comes from the job you picked during onboarding (Jobs Reborn / the quest tutorial) — gouw only holds and spends what residents put in.

## Growing the town

`/gouw buy [n]` buys *n* extra chunk slots for the town, paid from the bank (250 per chunk at the time of writing, with a server cap per town). The new limit applies to the whole gouw, so `/gouw info` shows e.g. `34/40 chunks (30 rank + 10 bought)`. Claiming into a bigger town is then simply `/gouw claim` (officers) or with the CLAIM right (residents).

[Back to the Introduction](/wiki?page=gouw-intro) · [Land Claims](/wiki?page=gouw) · [Commands](/wiki?page=gouw-commands)
