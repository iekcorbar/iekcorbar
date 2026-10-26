---
slug: steamworks-processing
title: Steamworks Processing
category: Steamworks
parent: steamworks
---
## Millstone
Grinds items. It takes rotation **from below** only.

* **Crank build:** put any block down, a shaft on top of it, the millstone on the shaft, break the block under the shaft and click a hand crank against the shaft's underside.
* **Put items in:** right-click with the item, or a hopper on top pointing down.
* **Take results:** right-click with an empty hand (sneak to take your input back), or a chest, barrel or hopper next to it.
* Speed: about 7.5 s per cobblestone with a crank, 15-20 s with a water wheel. Load 4 Tq per RPM.

| Input | Output |
|---|---|
| Cobblestone | Gravel |
| Gravel | Sand (+ Flint 20%) |
| Sandstone / Red Sandstone | 2 Sand / 2 Red Sand |
| Terracotta | Red Sand |
| Bone | 4-5 Bone Meal |
| Any Wool | 3-4 String |
| Sugar Cane | 2 Sugar |
| Glowstone | 4 Glowstone Dust |
| Blaze Rod | 3 Blaze Powder |
| Wheat | 2 Flour (+ Seeds 50%) |
| Flowers | twice the dye of a crafting table |

## Steam Hammer
Presses items lying in the block **under** it, one item per strike. Power it with a shaft through its side; rotation passes through, so you can chain hammers.

* With a hand crank: one strike every 2.5 s. One crank drives exactly one hammer.
* A **hopper right under** the hammer collects the results - while the hammer runs it leaves items it can still press.
* On a **conveyor belt** two blocks below the hammer, items wait under it until they are pressed.

| Input | Output |
|---|---|
| Iron / Copper / Gold Ingot | Iron / Copper / Golden Plate |
| Brass Ingot | Brass Plate |
| Sugar Cane | Paper |
| Snow Block | Ice |
| Clay | 4 Clay Balls |
| Stone, Deepslate, Nether, Polished Blackstone Bricks, Deepslate Tiles | their cracked version |

## Bellows Fan
Blows air out of its front (power it from behind; sneak while placing to flip it). The air reaches 4 blocks at crank speed, up to 12 blocks when faster, and stops at solid blocks. It pushes items, mobs and players.

Put something **in** the air stream and items **past** it are processed after 5 seconds, a whole stack at once:

| In the stream | Process |
|---|---|
| Lava | smelting (normal furnace recipes) |
| Fire or a lit campfire | smoking (smoker recipes) |
| Water | washing |
| Soul fire / soul campfire | haunting |

**Washing:** gravel → flint 25% / iron nugget 12%, sand → clay ball 25%, red sand → gold nuggets, soul sand → quartz, coloured wool → white wool, magma block → obsidian, **concrete powder → concrete** (all colours).
**Haunting:** torch → soul torch, lantern → soul lantern, campfire → soul campfire, potato → poisonous potato, sand → soul sand, dirt → soul soil, crimson fungus → warped fungus, rotten flesh → leather 30%.

## Crushing Wheels
Place **two** crushing wheels side by side on the same axis and power one of them - they turn against each other. Items **thrown on top** of the pair are crushed one at a time; the results fall out **underneath**, between the wheels (put a hopper there). Load 8 Tq per RPM per wheel.

| Input | Output |
|---|---|
| Iron / Gold ore (also deepslate) | 2-3 raw iron / raw gold |
| Copper ore | 7-10 raw copper |
| Coal, Redstone, Lapis, Quartz ore | more than mining by hand |
| Diamond / Emerald ore | 1-2 |
| Nether Gold Ore | 10 gold nuggets |
| Zinc / Tin ore (Silk Touch blocks) | 2-3 raw zinc / raw tin |
| Sulfur ore | 5-7 sulfur |
| **Any raw ore** (iron, copper, gold, zinc, tin) | **2 dust** — smelt each dust into an ingot, so crushing **doubles** your ore |
| Wheat | 2-3 Flour |
| Cobblestone, gravel, bone, wool, blaze rod | like the millstone, but faster |

> Mine ores with **Silk Touch** and crush them for more than Fortune gives.
> The full doubling chain: ore block → crush → 2-3 raw → crush → 4-6 dust → smelt → 4-6 ingots.

## Kettle and Mixer
The **Kettle** holds ingredients and water; the **Mixer** on top of it stirs them. Power the mixer **from above**.

* **Heat:** fire, a lit campfire, lava or a magma block under the kettle.
* **Water:** right-click with a water bucket (holds 3 buckets). An empty bucket takes a bucket back out.
* **Ingredients:** right-click with them, a hopper next to the kettle pointing into it, a conveyor belt ending at it, or a Karakuri Arm.
* **Results:** right-click with an empty hand, or a chest under or next to the kettle. Sneak + empty hand takes the ingredients back.
* The whisk drops into the pot while it mixes.

| Ingredients | Needs | Result |
|---|---|---|
| 1 Copper Ingot + 1 Zinc Ingot | heat | 1 Brass Ingot |
| Dirt | ¼ bucket water | Mud |
| Sand + Dirt | ¼ bucket water | 2 Clay Balls |
| 3 Wheat | water, heat | Bread |
| 2 Flour | ¼ bucket water, heat | 2 Bread |
| Flour + Cocoa Beans | heat | 8 Cookies |
| 3 Flour + 2 Sugar + Egg | ½ bucket water, heat | Cake |
| Flour + Pumpkin + Sugar + Egg | heat | 2 Pumpkin Pie |
| Brown + Red Mushroom + Bowl | heat | Mushroom Stew |
| 6 Beetroot + Bowl | heat | Beetroot Soup |
| Pumpkin + Sugar + Egg | heat | Pumpkin Pie |
| Slime Ball + Blaze Powder | - | Magma Cream |
| Spider Eye + Sugar + Brown Mushroom | - | Fermented Spider Eye |
| 2 Cobblestone + 2 Quartz | - | 2 Diorite |
| Diorite + Cobblestone | - | 2 Andesite |
| Diorite + Quartz | - | Granite |
| 2 Sulfur + Coal or Charcoal | - | 2 Gunpowder |
| Chemical Waste Canister + 4 Bone Meal | 1 bucket water, heat | Empty Canister + Sulfur |
