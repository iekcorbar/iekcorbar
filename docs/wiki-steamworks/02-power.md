---
slug: steamworks-power
title: Steamworks Power
category: Steamworks
parent: steamworks
---
## Power sources

### Hand Crank
* Place it against the end of a shaft (or a machine's input). Hold **right-click** to turn it; **sneak** to turn the other way.
* 16 RPM, capacity 128 Tq. Turning makes you hungry.
* One crank drives 2 millstones, 1 steam hammer, 2 bellows fans or 1 pumpjack.

### Water Wheel
* Needs **flowing** water along its paddles. Still source water does nothing.
* Every side with water pushing the paddles one way adds 4 RPM, up to 8 RPM. Water on both sides flowing the same way pushes in opposite directions and the wheel stands still - use one side.
* Capacity 256 Tq at full speed. It never stops.

### Windmill
Huge sails (about 10 blocks across) that catch the wind. Place the windmill against a block: the sails face away from it and a shaft goes out the back. Sneak while placing to flip it - and with a **Steam Wrench** you can turn the whole thing later, including pointing the shaft up or down.
* Speed: 4 RPM below Y 64, 6 RPM up to Y 100, 8 RPM higher up - plus 2 in rain and 4 in a thunderstorm.
* Capacity 64 Tq per RPM (512 Tq at 8 RPM).
* The sails need **free space**: a circle of 5 blocks round the hub, one block in front of it. More than two solid blocks (or leaves) in that circle and the windmill stops - right-click it to check.

### Steam Boiler and Steam Engine
The strongest power source.
* **Steam Boiler** - needs a **water source block** right next to it and fuel: right-click with coal, charcoal, a coal block, blaze rod, dried kelp block, lava bucket or a fuel canister (sneak = whole stack), a hopper pointing into its side, or **fuel from tanks through pipes** (from your refinery).
* **Steam Engine** - put it **on top** of the boiler; a shaft goes through its side. While the boiler burns and has water it runs at **24 RPM with 1536 Tq**.
* The boiler only burns fuel while an engine sits on it. Coal lasts 80 seconds; 1000 mB of fuel lasts 5 minutes.

## Carrying rotation

### Shaft
Carries rotation along its length. Place it against a face to point it that way. Shafts connect end to end.
Changed your mind? Right-click with a **Steam Wrench** and it turns around the face you click: side-click a shaft and it stands up, click its top or bottom and it swings flat the other way, click straight into its end and nothing happens. Sneak-click with the wrench and you **dismantle** the part into your inventory.

### Steam Wrench
A wrench made of two iron ingots and two sticks. Right-click a placed part with it and it turns **clockwise around the face you clicked**, just like Create does: clicking a side lays things down or tips them up, clicking the top or bottom swings them flat the other way, and clicking straight into a shaft's end changes nothing. It works on shafts, cogwheels, gearboxes, clutches, gearshifts, sprockets, crushing wheels, gauges, the steam engine, hand cranks, bellows fans, drills, saws, windmills, belts, stations and signals - the network rebuilds itself around the part while you watch. Parts that can only lie flat (belts, stations, signals, the steam engine) quietly refuse to stand up. **Sneak right-click dismantles** the part: it leaves the network and lands straight in your inventory (in creative the wrench just removes it, like Create). Right-clicking a part that cannot turn at all (a millstone, kettle or mixer) just shows its usual info.

### Cogwheel and Large Cogwheel
| Combination | Result |
|---|---|
| Two small cogwheels side by side (same axis) | same speed, opposite direction |
| Large cogwheel and small cogwheel, diagonal | small one turns **twice as fast** |
| Two large cogwheels at a right angle, diagonal | rotation goes round the corner |

Place a cogwheel against the rim of another one and it takes over its axis so they mesh. Cogwheels also pass rotation along their axle like a shaft.

### Gearbox
Sends rotation around a corner. It connects on its four sides (not top and bottom when placed on the floor). Opposite sides turn the other way. A Steam Wrench cycles which way that corner points.

### Clutch
Works like a shaft until it gets a **redstone signal** - then it disconnects and the far side stops. Its lamp turns red.

### Gearshift
Works like a shaft; with a **redstone signal** the far side turns the other way (lamp red). Handy to reverse a conveyor belt.

### Chain Sprocket (chain drive)
Carries rotation like a shaft, and can be **chained** to another sprocket up to 12 blocks away - also diagonally.
* Right-click the first sprocket with **iron chains**, then the second one. You need one chain per block of distance.
* Both sprockets must turn on the same axis and stand side by side in one plane (not behind each other).
* Chained sprockets turn at the same speed, the same way.
* Sneak + right-click a sprocket with an empty hand to take the chain off; breaking a sprocket drops its chains.

## Gauges
* **Speed Gauge** - a shaft with a dial that shows how fast it turns.
* **Stress Gauge** - shows how much of the network's capacity is used. Needle at the end = about to jam.

Right-click any part with an empty hand for the exact numbers.
