---
slug: steamworks-trains
title: Steamworks Trains
category: Steamworks
parent: steamworks
---
## Trains
Steamworks trains run on **normal rails** - straight, curves and slopes - but they are not minecarts.

### Putting a train together
1. Right-click a rail with a **Steam Locomotive** (needs at least two rails in a row). It points the way you look.
2. Right-click the rail right behind (or in front of) the train with a **Passenger Wagon** or **Cargo Wagon** to couple it.
3. Break a car with two hits. Breaking a car in the middle splits the train in two.

### Steam track
The moment a train rides a line the rails turn into **steampunk track**: ballast, sleepers and
brass fishplates ("shakels") appear along every rail the train drives on. Only train routes change -
plain rails everywhere else stay as they are, and if the train leaves a line the track follows it.
The cars sit slightly higher than before: wheels now roll on top of the rail heads, like on a real train.

### Driving
* Right-click the locomotive to sit in the cab.
* **W** = steam on, **S** = brake, then reverse slowly. **Jump** = steam whistle. **Sneak** = get off.
* The action bar shows speed and fuel, plus **RED SIGNAL AHEAD** or **TRAIN AHEAD** warnings.
* Mobs and players on the track are pushed aside. A train never drives into another train.

### Fuel
Right-click the locomotive with fuel (sneak = the whole stack). You only burn fuel while giving steam (W or S); rolling is free.

| Fuel | Steam |
|---|---|
| Coal / Charcoal | 2 minutes |
| Blaze Rod | 3 minutes |
| Coal Block | 20 minutes |
| Fuel Canister | 5 minutes (gives the empty canister back) |

### Wagons
* **Passenger Wagon** - 4 seats. Right-click to sit.
* **Cargo Wagon** - a chest of 27 slots. Right-click to open. Contents stay when the server restarts.

## Stations and signals
* **Station** - place it next to the track (same height or one higher, on a platform). Rename the station item in an anvil (or click the station with a renamed name tag) to put a name on its board. Riders see "Next stop" and the station name.
* **Signal** - place it next to the track. **Red** while the track after it, up to the next signal, holds a train - or while it gets redstone. Green when clear.

## Autopilot
Right-click a locomotive with a **Clock** to switch the autopilot on (click again to switch it off). The train then:
* drives itself (needs fuel, nobody has to sit in the cab),
* stops at every **station** for 6 seconds,
* waits in front of **red signals** and other trains,
* waits at the **end of the track** and drives back.

A line with a station at each end is a working shuttle service. Pressing W or S in the cab takes over and switches the autopilot off.

## Lines and timetables
Click a locomotive with a piece of **paper** to open its **line board**:
* The top row shows the stops of this line, in driving order; click one to remove it.
* The rows below list every named station on this world's rails - click to add (or remove) a stop, up to nine.
* With a line set, the autopilot **only stops at its own stations** and runs the line as a shuttle:
  dwell at each stop, next, and bounce (with a short wait) at the ends of the list.
* An empty line keeps the plain autopilot: stop at every station along the way.
* Lines are saved with the train and survive a restart. Name your stations first (anvil or name tag) - the board speaks names.
