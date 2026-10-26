---
slug: kura-automation
title: Automation
category: Kura Storage
parent: kura
---
## Automation

Kura works with vanilla hoppers and with **Steamworks** machines, because its Inventory Interface and Open Crate are real barrels.

### Getting items in

* **Vanilla hopper or Steamworks conveyor belt into an Inventory Interface** (import mode): everything that arrives is stored in the network. A farm can feed your storage directly.
* **Steamworks Karakuri Arm**: let it DROP items into an Interface.
* **Inventory Hopper** pointing from a farm chest into any network chest: moves the farm's output into storage.

### Getting items out

* **Inventory Interface in export mode**: put an Item Filter with what you need in it (for example coal and iron). The barrel is kept topped up with one stack of each, so hoppers, belts and arms can take from it - for example to feed a Steamworks boiler or millstone.
* **Inventory Hopper from the network into an Open Crate**: the items fall out in front of the crate, onto a conveyor belt or into a water stream.

### Redstone

* A **Level Emitter** turns on when the network holds more (or less) than an amount of one item. Use it to switch a farm off when storage is full, or to start a machine when supplies run low.

### Tips

* Give your "dump" chests the **lowest priority** and your sorted chests a filter with **higher priority** (Inventory Configurator). Items then land in the sorted chests first.
* A **Polymorphic Filter** on a chest keeps it for the items already in it.
