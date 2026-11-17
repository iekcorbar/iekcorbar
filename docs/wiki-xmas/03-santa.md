---
slug: xmas-santa
title: Santa
category: Xmas Town
parent: xmas-intro
---
## The man himself

Santa lives in Xmas Town. Staff place him on the plaza and he stays there: he walks around within a short stroll of his spot, never attacks anybody, and does not despawn - between restarts he is put back exactly where he stood.

### What he does

* **He rings his bell** when a player stands near him. You will hear him coming through the snow.
* **He greets you** instead of trading - there is nothing to buy.
* **He gives one present per player per day, on 24, 25 and 26 December.** The gift window is the same as the relic gift boxes. Come close, listen for the bell, and the box is yours - once per day, no farming.

### What you cannot do

* Push him out of the village: he finds his way back to his stroll.
* Lure a second one: there is exactly one Santa per server, always.

### For staff

Santa is placed and removed with `/xmas santa spawn` (optionally with a world and coordinates), `/xmas santa goto <player>`, `/xmas santa remove` and `/xmas santa status` - these also work from the console for scripts. His position and identity persist in `plugins/Xmas/config.yml`; set `santa.gifts-anytime: true` there to test gifts outside December 24-26, and turn it back off before the players arrive.
