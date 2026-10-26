---
slug: additions-clock
title: The Steam Clock
category: Steamworks Additions
parent:
---
## The Steam Clock

A two-block brass tower: iron column, brass case with an ivory dial, two small golden bells under a dark roof and a finial on top. It tells the town what time it is - and it is right.

### What it does

* A **floating time plate** above the dial reads the time to the minute, e.g. `21:47` - gold by day, deep blue by night.
* By default it runs on the **real wall clock**: it matches your watch, and it strikes **once every real hour** (twice at noon and midnight). The server owner can switch it to the Minecraft world clock instead, where a full day takes 20 minutes.
* On the hour the bell strikes: a clear brass tone, pitched per hour, with a small **steam puff** from the top so you can see from across the square who is late.
* **Right-click the tower** (empty hand) and the action bar shows the exact time, minutes until the next strike, and what the sky is doing.

### Good to know

* The clock only ticks and strikes **while players are within about 24 blocks**. A clock in an abandoned quarter is perfectly silent and costs the server nothing.
* A clock that rings you awake at 3 AM? The owner can silence the night hours (23:00-05:00) or mute the bell completely - the plate keeps showing the time either way.
* Standing near a clock while it strikes earns a little advancement - and collecting all Additions props earns a bigger one.
* It stands free (the base block is solid, you bump into it) and needs no power at all.

### Crafting

Shapeless: **1 Clock + 2 Copper Ingots + 2 Gold Nuggets + 1 Redstone** → 1 Steam Clock.

### Placing and moving

Right-click with the clock item to place it (needs 2 blocks of air above its spot). Left-click twice to take it back; the time plate disappears with it.

Staff: `/sa give <player> steam_clock`.
