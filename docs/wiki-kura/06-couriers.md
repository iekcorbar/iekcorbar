---
slug: kura-couriers
title: Copper Golem Couriers
category: Kura Storage
parent: kura
---
## Copper Golem Couriers

A copper golem can work as a courier for your storage network: it fetches items from supply points and carries them into the network.

### Set it up

1. Place an **Inventory Interface** that is connected to your network.
2. Stand next to a copper golem that is within 12 blocks of the interface.
3. Right-click the golem with an **Inventory Configurator**. The golem is bound to the nearest interface.
4. Right-click it again with the configurator to set it free.

### What it fetches

* Items from **copper chests** around the interface.
* With Steamworks installed: the chests and barrels that **machines deliver into** - under a steam hammer, under or beside a kettle or millstone, and at the end of a conveyor belt.

It never empties the network itself, and it only works while you are online and allowed to build at the supply point.

### Good to know

* The golem takes one stack at a time and walks it to the interface.
* If the network is full, it puts the rest down instead of standing around.
* A golem that is not bound keeps behaving like a normal copper golem.
* The radius can be changed in the Kura config (`couriers.radius`, 4-24 blocks).
