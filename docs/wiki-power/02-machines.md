---
slug: power-machines
title: Mechanical Press & Stock Ticker
category: Steamworks Power
parent:
---
## Mechanical Press

A brass arch over a heavy ram. Items that lie **on top** of the press get stamped while it turns; the results fall out beside it. The press does not smelt - it **compacts**: nuggets to ingots, snow to ice, clay to bricks, and it gives the honey back its bottle.

* Needs rotation from below: a shaft enters through the bottom, like a millstone.
* Work speed depends on the network (full speed ≈ 3 s per item at 12 RPM).
* The recipe list lives in the plugin's `config.yml` under `press:` - server owners add their own compacting recipes; `/power reload` applies them.

Defaults:

| On the plate | In the box below |
|---|---|
| 9 Zinc Nuggets | 1 Zinc Ingot |
| 1 Honey Bottle | 1 Glass Bottle (+ 25% Honeycomb) |
| 1 Clay block | 4 Bricks |
| 1 Hay Block | 1 Leather (+ returns the hay, 50%) |
| 4 Snow Blocks | 2 Packed Ice |

Craft: **iron plates** over an andesite casing, piston and two iron ingots as the legs.

Staff: `/power give <player> pow_mechanical_press`.

## Stock Ticker

A copper box with two little dials that **counts the container it leans against**: chest, barrel, hopper, or a Steamworks tank. Above it floats the two biggest stacks ("Cobblestone x128 | Flint x40 · 23/27") - visible from across the yard, only while players are near.

* No rotation needed. Place it against any block with an inventory; if several touch, it picks the nearest.
* Right-click: a one-line summary of the container in your action bar.
* Craft: glass panes around a comparator, on a brass casing.

Staff: `/power give <player> pow_stock_ticker`.

## Taking them back

Left-click twice, like every Steamworks part: the machine drops as an item (creative: one sneak right-click with the wrench).
