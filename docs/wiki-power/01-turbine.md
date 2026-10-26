---
slug: power-turbine
title: The Water Turbine
category: Steamworks Power
parent:
---
## The Water Turbine

A copper drum on an axle, big enough to hide a shaft and small enough for a town canal. Where a water wheel needs a broad river, a turbine is happy in a two-block millrace.

### What it does

* It sits on the shaft line: rotation enters and leaves through both ends, like any shaft part. The drum must feel **water on its sides**.
* A **flowing** side gives it full power (12 RPM, 64 torque per RPM = 768 Tq); only still water on both sides gives half. Two flowing sides add a quarter on top.
* The turbine produces; it never consumes. Hook it up and it drives the whole line it touches.

### Building with it

Dig a race: water flows in beside the drum, the tailrace flows out on the other side. The turbine keeps turning as long as the water next to it **moves** - so a waterfall that lands on the drum works; a stagnant pond doesn't.

A canal town can hide these under every bridge: nobody sees a wheel, everybody enjoys the torque.

### Crafting

**4 iron ingots** (corners), **2 brass casings** (left and right) and **1 propeller** (center) → 1 Water Turbine.

The propeller is the expensive bit: two copper plates around a steel hub, and Steamworks' own - the press and the turbine share one family of parts.

### Placing and moving

Right-click a shaft end to place it, sneak right-click to pull it back (like any network part). Sneak-right-click with a wrench to turn it into your hand. The turbine itself does not store items or fluids.

Staff: `/power give <player> pow_water_turbine`.
