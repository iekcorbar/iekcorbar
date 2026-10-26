# NPC skins (steampunk / sakura)

49 classic 64x64 player skins for the Citizens / BetonQuest NPCs (own pixel art, `python -I tools/make_skins.py`).
`npc_skins.csv` = Citizens id -> NPC name -> skin file. `preview_sheet.png` = front + back of every skin (same order as the csv).
Both texture layers are used: the overlay layer (hats, coats, aprons, beards, backpacks) gives the 3D depth.

## Putting a skin on an NPC
Citizens needs a signed texture, so a PNG on disk is not enough:
1. Upload the PNG to a public URL (own website, or https://mineskin.org - that sends the file to a third party, your call).
2. In game: `/npc select <id>` then `/npc skin --url <URL>` (see `apply_commands.txt`). Use the classic (not slim) variant.
3. The 24 "Guard" NPCs (ids 93, 99-121) can use guard_a..guard_d in any mix.
