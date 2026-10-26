---
slug: steamworks-automation
title: Steamworks Automation
category: Steamworks
parent: steamworks
---
## Mechanical Drill
Breaks the block in front of it. Power it **from behind** (sneak while placing to flip it).

* Stone takes about 2 seconds at crank speed, obsidian about a minute. Faster = quicker.
* Drops what an iron pickaxe would get; custom ores drop their raw ore.
* Never breaks chests and other containers, unbreakable blocks or Steamworks parts.

## Mechanical Saw
Like the drill, but only cuts wood, leaves and plants (crops, cactus, sugar cane, bamboo, melons, pumpkins...). Cutting a log **fells the whole tree** above it.

> **Protection:** drills, saws and arms remember who placed them and only break or build where that player may. While that player is offline they keep working on spots that were allowed in the last 10 minutes, otherwise they wait.

## Conveyor Belt
Lay it out in the direction you look; pieces in a row work as one belt. Power it with a shaft **at the side** of any piece.

* The belt runs the way its rollers turn - reverse the rotation (sneak-crank or a Gearshift) and the belt runs the other way.
* Carries items, mobs and players (sneak to stand still).
* At the end of the line items go into a **chest**, barrel, hopper or **kettle** in front; otherwise they fall off.
* Under a **Steam Hammer** (two blocks above the belt) items wait until they are pressed. A **Bellows Fan** blowing along the belt processes items while they ride.
* Load: 0.5 Tq per RPM per piece. 16 RPM = 1.3 blocks per second.

## Karakuri Arm
A programmable robot arm, powered **from below**, with swappable tool heads:

| Tool head | Can do |
|---|---|
| Gripper Head | PICK and DROP items |
| Placer Head | PICK, DROP, PLACE blocks, USE bone meal |
| Drill Head | BREAK blocks |

1. Right-click the arm with a tool head to fit it.
2. **Teach points:** sneak + right-click the arm with an empty hand, then right-click up to 8 blocks within 4 blocks. Sneak + right-click the arm again when done.
3. **Program it:** right-click with a Book and Quill containing a program - or use the default (gripper: point 1 → other points).
4. **Punch Cards** copy a program from one arm to another.

The arm works with chests, barrels, hoppers, millstones, kettles, conveyor belts, items on the ground and levers.

**Full language reference, examples and an online checker:** [Karakuri Arm Guide](/steamworks/arm).
