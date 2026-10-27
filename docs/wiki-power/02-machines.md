---
slug: power-machines
title: Press, Ticker, Flywheel & Sieve
category: Steamworks Power
parent:
---
## Mechanical Press

A brass arch over a heavy ram. Items that lie **on top** of the press get stamped while it turns; the results fall out beside it. The press does not smelt - it **compacts**: nuggets to ingots, snow to ice, clay to bricks, and it gives the honey back its bottle.

* Needs rotation from below: a shaft enters through the bottom, like a millstone.
* Work speed depends on the network (full speed ≈ 3 s per item at 12 RPM).
* The recipe list lives in the plugin's `config.yml` under `press:` - server owners add their own compacting recipes; `/swpower reload` applies them.

Defaults:

| On the plate | In the box below |
|---|---|
| 9 Zinc Nuggets | 1 Zinc Ingot |
| 1 Honey Bottle | 1 Glass Bottle (+ 25% Honeycomb) |
| 1 Clay block | 4 Bricks |
| 1 Hay Block | 1 Leather (+ returns the hay, 50%) |
| 4 Snow Blocks | 2 Packed Ice |

Craft: **iron plates** over an andesite casing, piston and two iron ingots as the legs.

Staff: `/swpower give <player> pow_mechanical_press`.

## Stock Ticker

A copper box with two little dials that **counts the container it leans against**: chest, barrel, hopper, or a Steamworks tank. Above it floats the two biggest stacks ("Cobblestone x128 | Flint x40 · 23/27") - visible from across the yard, only while players are near.

* No rotation needed. Place it against any block with an inventory; if several touch, it picks the nearest.
* Right-click: a one-line summary of the container in your action bar.
* Craft: glass panes around a comparator, on a brass casing.

Staff: `/swpower give <player> pow_stock_ticker`.

## Brass Flywheel

A heavy brass wheel on a steel axle. While the network runs it stores the spin; when the power source falls away - the river drops, the boiler dies, someone pulls the clutch - the wheel **takes the line over** and keeps every machine turning at a slowly falling speed until its charge is spent.

* It charges from whatever turns the line: turbine, engine, windmill, all the same to it - anything at 3 RPM or more. Even a hand crank winds it, as long as someone keeps turning.
* Right-click it: the action bar shows the charge in percent and what it is doing (winding up / full / running down).
* Real power coming back simply hands the wheel the line over again; nothing is lost.
* The last stretch sputters - a nearly empty wheel coasts the line at a crawl instead of snapping it.

Craft: **four brass plates** bolted around a **brass casing**.

Staff: `/swpower give <player> pow_brass_flywheel`.

## Sieve Funnel

A brass mesh on four steel legs, sitting at the end of a belt or chute line. Right-click it **with any item** and the mesh tunes itself to exactly that item: matching items fall into the container underneath, everything else is tossed off the side and the line keeps running.

* The filter reads the item itself, not just its material: two different Steamworks plates with the same head are two different filters.
* Right-click empty: what does the mesh currently hold? Sneak right-click empty: open the mesh - everything passes.
* No filter and no container below? Items wait on the line - nothing is voided.
* It sifts items, not fluids, and never turns: it is a static piece of the line, like the funnel it is built from.

Craft: **two iron plates** and a **string** for the mesh frame, around your old faithful **funnel**.

Staff: `/swpower give <player> pow_sieve_funnel`.

## Taking them back

Left-click twice, like every Steamworks part: the machine drops as an item (creative: one sneak right-click with the wrench).
