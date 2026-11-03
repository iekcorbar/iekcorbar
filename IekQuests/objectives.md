# IekQuests Objectives

Objective definitions are generated at `plugins/IekQuests/organization/Objective.yml` from `Objective` chapters in `Quest/*.yml`.

Each objective uses `PlayerTag`, `ServerTag`, `Objective`, `OncompleteRemoveTag`, `OncompleteAssignTag`, `addjournal`, and `removejournal`. Optional delivery controls are `AvailabilityTag` and `Manual`. Every listed prerequisite and every task is required (AND). A `!tag` in `PlayerTag` requires that player tag to be absent. `ServerTag` entries must be assigned globally. Completion removes and assigns player tags once, applies the configured Journal entry changes once, and stores the completed state under the player's UUID in `data.yml`.

`addjournal` and `removejournal` list IDs defined in `organization/Journal.yml`. Validate references with `/iekobjective validate`.

Supported task types are `kill`, `deliver`, `craft`, `has-item`, `reach`, `break-block`, `place-block`, `fish`, `smelt`, `enchant`, `use-item`, `open-container`, `interact-block`, and `inventory-click`. See the comments in `Objective.yml` for fields and examples. `deliver` checks all deliver requirements when all other tasks are ready, then consumes the required items once at final completion. Materials and IekItems are supported; MMOItems and external RPG item/level integrations are not assumed.

## NPC item delivery

Use two objectives when an NPC should only consume items after the player selects a dialogue response:

1. An inventory watcher objective contains only `has-item` tasks and sets `AvailabilityTag`. All its prerequisites and item quantities must pass for the tag to be assigned. If any prerequisite or quantity fails, the tag is removed. The watcher updates after inventory clicks and item pickups, every 20 ticks to catch other changes, and again before a response checks its prerequisites. Define the availability tag in the `player-tags` chapter and reserve it for this watcher. Watchers do not complete and cannot apply completion tags or Journal changes.
2. A delivery objective sets `Manual: true` and contains only `deliver` tasks. It will not complete from normal item events or inventory checks. A response calls it with `executeobjective`; IekQuests rechecks prerequisites and item quantities, consumes the exact configured totals once, and then applies its completion tags and Journal changes. If the recheck fails, the response has no effects and the dialogue is refreshed.

Example pair (the corresponding tags must exist in the `player-tags` chapter):

```yaml
brakka_delivery_available:
  PlayerTag:
    - brakka_quest_active
    - "!brakka_delivery_complete"
  ServerTag: []
  Objective:
    - type: has-item
      item: IRON_INGOT
      amount: 6
  AvailabilityTag: brakka_delivery_ready

brakka_delivery_submit:
  PlayerTag:
    - brakka_delivery_ready
  ServerTag: []
  Manual: true
  Objective:
    - type: deliver
      item: IRON_INGOT
      amount: 6
  OncompleteRemoveTag:
    - brakka_delivery_ready
  OncompleteAssignTag:
    - brakka_delivery_complete
```

Then set the response field in the `responses` chapter:

```yaml
response_deliver_iron:
  text: "Here are the iron ingots."
  prerequisites:
    - brakka_delivery_ready
  executeobjective: brakka_delivery_submit
  next-dialogue: brakka_thanks
```

Use `/iekobjective set <id> AvailabilityTag <player-tag>` or `none` to configure the watcher; use `/iekobjective set <id> Manual true|false` to control manual delivery. The dialogue graph editor also exposes the response's `executeobjective` field, and `/iekdialogue validate` checks that the target objective exists and has `Manual: true`.

## Inventory click slots

`inventory-click` uses a numeric slot local to the configured inventory context. For the player's 2×2 crafting inventory, result slot is `0`; input slots are `1–4`. Slot `4` is an input slot. Set `inventory: player-crafting` for the E-screen crafting area, or `inventory: player` for the player's storage inventory. IekQuests matches the clicked inventory and local slot rather than comparing against a raw view slot.

## Commands

Use `/iekobjective help` for command syntax. Admins can list, show, validate, reload, create, and edit objectives and tasks; inspect player progress; reset one player's progress for one objective; and delete definitions only after progress has been reset. The command requires `iekquests.objective` or `iekquests.admin`.

Task examples:

```text
/iekobjective create collect_slimes
/iekobjective set collect_slimes PlayerTag has_started
/iekobjective task add collect_slimes kill target=SLIME,amount=4
/iekobjective task add collect_slimes deliver item=WOODEN_SWORD,amount=3
/iekobjective validate collect_slimes
```

Progress is paused while prerequisites do not pass and resumes without losing accumulated counters. Changing a task definition changes its signature; only that task's stale counter is reset when it is next evaluated. Quest progress, dialogue tags, and cooldowns are stored separately and are not reset by objective reloads.

`craft` counts the result item shown on a successful `CraftItemEvent`. Shift-click crafting behavior depends on the server's crafting transaction; verify it on the target Paper build before relying on bulk-craft counts. Optional EliteMobs-specific kills, MMOCore levels, Jobs levels, Valhalla skills, MMOItems IDs, and WorldGuard objectives require additional verified integrations.


## Objective completion events

The `events` chapter can define `on-objective-complete` entries with a required `objective-id` filter. The event fires once when that objective changes from incomplete to completed, after completion state, `OncompleteRemoveTag`, `OncompleteAssignTag`, and objective Journal effects are applied and saved. Event prerequisites therefore read the updated tag state. Every matching eligible entry runs in YAML order. Resetting an objective removes its completion state, so a later completion is a new event occurrence. Use event `commands`, `setplayertag`, Journal effects, `sequences`, or dialogues for optional follow-up behavior; objective core completion remains owned by the `Objective` chapter.
