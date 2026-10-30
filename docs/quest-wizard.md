# Quest Wizard - building quests in-game (IekQuests 0.3)

For staff with `iekquests.admin`. The wizard is a click-GUI: it builds a quest
step by step and saves it to `plugins/IekQuests/wizard-quests.yml`. Your
hand-written quests.yml is NEVER touched by the wizard.

## Open it

- `/questwizard` (alias `/qwiz`), or the **nether-star button** in `/questbook`.
- Chat is used for text: the wizard closes the menu, you type the answer in
  chat, it reopens. `cancel` aborts one field; `done` ends a multi-line list.

## The main window

| button | what |
|---|---|
| Quest id | internal name, lowercase (vorkan2, my_quest) |
| Title / Subtitle / Icon / Order | what players see in the book. Icon = material name (BOOK, IRON_SWORD, PUMPKIN...) |
| NPC binding | opens the NPC window |
| Start conditions | who may begin: `!tag:done_x,point:rep>=3,perm:some.node,day,night` |
| Journal lines | the quest-book description (type lines, end with `done`) |
| Quest rewards | fired once when the quest finishes (rewards syntax below) |
| Stage buttons + green + | the quest itself, stage 1..N run in order |
| Contract buttons + | repeatable deliver contracts with their own cooldown |
| Help | full syntax cheat sheet in-game |
| Ender eye | save + activate immediately |
| Minecart | Manage: edit or delete any WIZARD-made quest |

## Stages

Pick a type with the five colored buttons:

- **talk** - NPC says the conversation lines; one big button continues.
- **craft** - player must have `amount` of `material` in the inventory
  (crafting counts live), then hands it in.
- **deliver** - same, but hands the items over.
- **walk** - done when the player is within `radius` of `location`
  (green-dye button = "use the spot I am standing on right now"). A compass
  button points the way in the quest window.
- **kill** - kill `amount` of `mob` (entity name, or MONSTER for any mob).

Extra per stage: conditions, stage rewards, a **goto** override (branch away)
and **choices** - the dialogue options players click. A choice can say
something back, fire actions (shop opens, tag set, console command) and/or
jump to another stage key. Choice conditions hide options you don't qualify
for (e.g. `!tag:race_changed`).

Up/down arrows reorder stages; lava bucket deletes.

## Contracts (repeat)

Deliver again for coin without replaying the story: material, amount, money,
elite coins, cooldown (1320 s = what the merchants use), optional once-per-day,
voice and extra reward lines (`point merchant_rep +1` etc.).

## NPCs

- **Bind existing**: type the Citizens id (79 = Vorkan).
- **Create new**: type a name - the NPC spawns where YOU stand and is bound
  automatically. Give it a skin afterwards with `/npc skin`, move it with
  `/npc move` or the "Pull NPC to me" button.
- **Delete later**: `/questwizard delete <quest-id>` removes a wizard quest
  (the NPC stays; remove it in-game with `/npc remove`).

## Rewards / actions syntax (also on the Help page)

```
money N | eco N | title A|B | sound ns:key | say text
tag+ name | tag- name | point cat +N | give MAT N | effect TYPE secs amp
teleport x;y;z;world | compass x;y;z;world | shop console-cmd %player%
perm+ some.node      grants the permission (LuckPerms, console)
perm- some.node      takes it back
anything else        runs as a console command
```

## Conditions syntax

```
tag:name   !tag:name   point:cat>=N   item:MAT>=N   perm:node   day   night
```
Comma = "and". Used on quest start, on stages and on choices.

## Example: a Mad-Doctor style reward

Quest rewards: `perm+ cmi.command.rt` unlocks /rt when the quest completes;
start condition `!tag:md_done` hides it afterwards. The perm+/perm- actions
work in stage rewards and choice "do" lists too.
