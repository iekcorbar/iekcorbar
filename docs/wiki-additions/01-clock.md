---
slug: additions-clock
title: The Steam Clock
category: Steamworks Additions
parent:
---
## The Steam Clock

A two-block brass tower: iron column, brass case with an ivory dial, two small golden bells under a dark roof and a finial on top. It tells the town what time it is - twice.

### What it does

* A **floating time plate** above the dial always reads the current in-game hour, e.g. `07:00`.
* On **every in-game hour** the bell strikes: a clear brass bell tone, pitched per hour, and at **noon and midnight it rings twice**.
* A small steam puff comes out of the top when it strikes, so you can see from across the square who is late.

### Good to know

* A full Minecraft day is 20 minutes, so an hour passes every 50 real-time minutes. The clock is tied to the world clock - sleep through the night and the clock jumps with it.
* The clock only ticks and strikes **while players are within about 24 blocks**. A clock in an abandoned quarter is perfectly silent and costs the server nothing.
* It stands free (the base block is solid, you bump into it) and needs no power at all.

### Crafting

Shapeless: **1 Clock + 2 Copper Ingots + 2 Gold Nuggets + 1 Redstone** → 1 Steam Clock.

### Placing and moving

Right-click with the clock item to place it (needs 2 blocks of air above its spot). Left-click twice to take it back; the time plate disappears with it.

Staff: `/sa give <player> steam_clock`.
