---
slug: steamworks-oil
title: Steamworks Oil
category: Steamworks
parent: steamworks
---
## Finding oil
About 1 in 12 overworld chunks has an oil field underneath - more often in deserts, badlands and oceans. Right-click with an **Oil Dowsing Rod**: it tells you how much oil is under you, or in which direction the nearest field is. Fields run dry when you pump them.

## The oil chain
1. **Pumpjack** - place it on the field and power it with a shaft through its side. Its beam nods up and down. At crank speed it pumps 32 mB of crude oil per second (load 8 Tq per RPM).
2. **Pipes** - connect pumpjacks, tanks and refineries. They join up on their own; fluids flow without power. A pipe also joins up with a vanilla **chest, trapped chest, barrel, hopper, dispenser, dropper, furnace, blast furnace, smoker or brewing stand** that touches it. Shulker boxes and ender chests are left out.
   - **Items in pipes:** use a steam wrench on a container next to a pipe to set that side of the pipe: *off* → *takes items out* → *puts items in* → *off*. A network moves one item type at a time, 8 per second, and stops when the target is full. The axle-driven pump comes later.
3. **Tanks** - hold 16 buckets (16,000 mB) of one fluid. You see the level and colour: crude oil black, fuel amber, chemical waste green.
4. **Refinery** - needs **heat** under it (fire, a lit campfire, lava or magma). It turns 100 mB of crude oil per second into **60% fuel** and **40% chemical waste**. It stops when there is no room for fuel.

Right-click a tank, pumpjack or refinery with an empty hand to see what it is doing.

## Pumps and Valves
A **Mechanical Pump** and a **Fluid Valve** are pipe pieces with a personality of their own - both are powered by the rotation network, just like machines.

* **Pump** - a junction on the line that can also *drink and fill*: right-click it with a canister to take 1,000 mB out of the whole connected network, or to pour a canister in. Handy far from any tank: one tap and the oil is in your bucket.
* **Valve** - a shut-off tap. It only lets fluid pass **while it turns**; stop its network (or a clutch upstream) and the line is cut where the valve sits. Place one before your refinery to decide what happens to your oil while you sleep.

## Canisters
Craft them from tin. Right-click a tank with an **Empty Canister** to take 1,000 mB; right-click with a full canister to pour it in.
* A **Fuel Canister** in a locomotive gives 5 minutes of steam.
* A **Chemical Waste Canister** can be neutralized in a heated kettle with 4 bone meal and a bucket of water.

## Pollution
Give the refinery a separate tank for its waste. **If the waste has nowhere to go, it is dumped into the chunk** - and so is the waste in a tank you break.

| Pollution | What happens |
|---|---|
| any | green toxic haze |
| over 300 | grass turns to dirt, leaves and flowers die |
| over 600 | players in the chunk are poisoned |
| over 1500 | ...and get nauseous |

Pollution fades slowly on its own. The dowsing rod also tells you the pollution in your chunk.
