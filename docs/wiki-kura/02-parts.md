---
slug: kura-parts
title: Parts
category: Kura Storage
parent: kura
---
## Parts

Most parts are placed like blocks. **Taking one off:** sneak + right-click it with an **empty hand** (one click, any game mode), or punch it three times (one in creative). The Interface and Open Crate are real barrels and break like any barrel. To place a part against a chest, **sneak** while placing - otherwise the chest just opens.

| Part | What it does |
|---|---|
| **Inventory Cable** | The network. Touches a chest, barrel or shulker box and that container is in; touches another cable and the two become one network. Any cable makes a network on its own - there is no "heart" block to plan around. |
| **Framed Inventory Cable** | The same cable that fills the whole block, so it can run through a wall. Can be painted to look like any solid block. |
| **Storage Terminal** | Shows and handles every item in the network. Place it against a cable or against any chest of the network. On a lone chest (no cable anywhere) it shows just that chest. |
| **Crafting Terminal** | A Storage Terminal with a 3x3 crafting grid. After each craft the grid **refills itself** from the network, so you can keep clicking. **Shift-click** the result to craft a whole stack. |
| **Inventory Hopper** | Moves items from the block behind it to the block in front, 8 at a time. Either side can be a whole network, a chest, or an Open Crate. Right-click to set a filter. |
| **Level Emitter** | Gives a redstone signal when the network holds more (or less) than a set amount of one item. Right-click to set the item and amount. |
| **Inventory Interface** | A barrel. **Import**: everything put in goes into the network. **Export**: it is kept stocked with one stack of each item in its filter. Sneak + right-click with an empty hand for settings. |
| **Open Crate** | A barrel that drops everything put into it on the ground in front. Sneak while placing to make it drop downwards. |
| **Filing Cabinet** | Holds up to 256 items of **one kind** in all variants - perfect for enchanted books, potions or maps. Keeps its contents when broken. Works as part of a network too. |

### Old parts (0.1.11 and earlier)

Three parts used to do what a single cable does now. They can no longer be crafted, but **everything already placed keeps working** - each one behaves as a cable, keeps its look and its name:

| Part | Was | Now |
|---|---|---|
| **Inventory Connector** | The heart of a network; without one nothing joined. | Behaves as a cable. |
| **Inventory Trim** | Bridged a gap between chests; stores nothing; could be painted. | Behaves as a cable (still paintable). |
| **Cable Connector** | The piece at the end of a cable, aimed at the chest it attached. | Behaves as a cable: it joins every side, no aiming needed. |

### Good to know

* Furnaces, hoppers and brewing stands are never used as storage, and neither are Sakagura sake casks (your drinks keep ageing).
* Right-click a cable to see how big its network is - its blocks light up for a few seconds.
* A very big network (over 4096 blocks) stops growing; the terminal tells you when that happens.
