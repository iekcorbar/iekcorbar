# NPC dialogue system

IekQuests uses a YAML dialogue graph as the only conversation and response flow for configured Citizens NPCs. Interacting with a configured NPC opens a centered Paper Dialog. Dialogue text does not go to ordinary Minecraft chat, and the interface does not use or change player inventories. Dialog text is paged independently from response pages; each response page has up to six numbered clickable buttons. A normal Minecraft client selects responses by clicking the dialog buttons.

## Data files

Editable quest bundles live in `Quest/*.yml`. The runtime files for dialogues, responses, NPCs, tags, Journal entries, rewards, objectives, events, sequences, and the legacy quest engine are generated in `organization/`. See [Quest Package Files](quest-packages.md) for the bundle chapters, reload behavior, migration, and conflict rules. `data.yml` remains separate and stores quest progress, dialogue tags/cooldowns, Journal assignments, and Objective progress.

A minimal dialogue entry looks like:

```yaml
dialogue_greeting:
  title: "Vorkan"
  lines:
    - "Welcome to the harbor."
    - "I have a task for you."
  prerequisites: []
  server-prerequisites: []
  responses:
    - response_accept
    - response_decline
  commands: []
  voice: "steamworks:vorkan.line01"
  add-player-tags: []
  add-server-tags: []
  addjournal: []
  removejournal: []
  Reward: []
```

A response entry can start a quest using the native quest engine:

```yaml
response_accept:
  text: "Yes, I will help."
  prerequisites: []
  server-prerequisites: []
  next-dialogue: "dialogue_active, dialogue_fallback"
  commands:
    - "server: cmi money give %playername% 20"
  add-player-tags:
    - accepted_harbor_task
  add-server-tags: []
  addjournal: []
  removejournal: []
  executeobjective: ""
  quest-actions:
    - quest-id: harbor_task
      action: accept
  per-player-cooldown: "0h13m44s"
```

An NPC binding is an ordered list. The first dialogue whose tag and quest prerequisites are satisfied is selected:

```yaml
vorkan:
  display-name: "Vorkan"
  npc-id: 134
  dialogues:
    - dialogue_greeting
    - dialogue_fallback
```

All tag prerequisites in each list use AND logic. Prefix a tag with `!` to require that it is absent. Empty prerequisites are `[]`. Tag assignments are stored by player UUID; server tag assignments are global. Unknown tags and references are logged and do not satisfy prerequisites.

## Native quest actions

`quest-actions` is a list of mappings with `quest-id`, `action`, and only the optional fields needed by the action. `quest-id` outside the list provides quest context and may gate a response through `quest-state`, `quest-stage`, or `quest-prerequisites`. Each action mapping performs the operation on its own `quest-id` (or uses the surrounding quest context when omitted).

For example, accepting a quest needs no target stage or repeat ID:

```yaml
quest-actions:
  - quest-id: harbor_task
    action: accept
```

`target-stage` is used by `goto-stage`; `repeat-id` is used by `repeat-contract`. Supported actions are `accept`, `refuse`, `advance`, `turn-in`, `goto-stage`, `complete`, `restart`, `repeat-contract`, and `run-rewards`. `restart` resets and accepts a completed quest only when it has no repeat contracts. `advance` and `turn-in` use the current quest stage and its existing requirements, rewards, and transitions. `complete` is allowed only when the current stage is ready. `repeat-contract` validates that the quest is complete, the repeat exists, its cooldown has elapsed, and its required items or location are satisfied. Quest progress, objectives, and rewards continue to use the existing `data.yml` state and quest engine.

## Next-dialogue selection

A response can select a single next dialogue, try ordered fallbacks, or randomly order candidates and use the first eligible dialogue:

```yaml
next-dialogue: dialogue_active
```

```yaml
next-dialogue: "dialogue_kan_1, dialogue_kan_2, fall_back_kan"
```

```yaml
next-dialogue: "[dialogue_kan_1; dialogue_kan_2; dialogue_kan_3]"
```

Comma-separated candidates are tried left to right. In a quoted semicolon group, candidates are shuffled once per transition and tried at most once each. For either form, the first existing dialogue whose tag and quest prerequisites, NPC restrictions, and other current eligibility checks pass is selected. Rejected candidates have no effects. If none is eligible, the conversation ends with the configured unavailable message. `/iekdialogue validate` checks syntax and every referenced dialogue ID.

## Voice sounds

`voice` is supported on dialogue and response entries. A dialogue's voice plays once when the dialogue is selected; a response's voice plays when that response is selected. Paging and redrawing do not replay it. The value is a sound event key such as `steamworks:vorkan.line01`. IekQuests calls the sound event; it does not download or activate a resource pack. The client must already have the resource pack that defines the sound.

## Reusable rewards

Define named reward action lists in `Reward.yml` and call them from a dialogue:

```yaml
# Reward.yml
welcome_reward:
  - "money 100"
  - "eco 5"
  - "give DIAMOND 2"
  # Or grant an IekItem by its items.yml ID: "item custom_item_id 1"
  - "sound steamworks:quest.complete"
  - "say &aWelcome!"
```

```yaml
# dialogues.yml
dialogue_greeting:
  Reward:
    - welcome_reward
```

Each reward uses the existing quest reward action executor and preserves action order. `%player%` is replaced with the player's name; plugin-backed actions retain the existing executor behavior. Rewards run once each time the dialogue is selected, not on page redraws. Reopening the dialogue selects it again; use tags, prerequisites, or cooldowns for one-time rewards. `Reward` may also be written as `rewards`, but do not define both on one dialogue. Unknown reward IDs and malformed built-in reward actions are reported by startup diagnostics and `/iekdialogue validate`. Standalone reward `tag+` and `tag-` use registered player tags; standalone `point` values are stored outside quest progress. Existing raw console-command reward syntax remains available; commands supplied this way are server-authored and cannot be statically validated by IekQuests.

`voice` and custom `sound` reward actions require the corresponding sound in a resource pack already loaded by the client.

Response cooldowns accept `-1` for no cooldown, single-unit values such as `1h`, `20m`, or `33s`, and compound values such as `0h13m44s`. They are stored by player UUID and response ID under `data.yml`'s `dialogue.cooldowns` path.

Dialogue-level commands, tags, and Journal changes run once when that dialogue is selected. Response-level commands, tags, Journal changes, quest actions, and cooldown run only when the response is selected. Paging and redraws do not repeat dialogue effects. Set `addjournal` or `removejournal` to comma-separated Journal entry IDs with `/iekdialogue dialogue|response set <id> <field> <entry-id,...>`; validation checks them against `Journal.yml`. Commands use `server:` (or `ser:`) for the console, `player:` for the interacting player, and `op:` for a temporary elevated player command. `op:` rejects commands that could leave persistent operator or permission changes; only trusted administrators should author commands.

For NPC item turn-ins, configure `executeobjective: <manual-objective-id>` on a response. The referenced objective must have `Manual: true` and only `deliver` tasks. It is rechecked and executed before any other response effects; if the player no longer has the required items or prerequisites, the response does not proceed. Pair it with an Objective.yml `has-item` watcher using `AvailabilityTag` to add/remove the tag that reveals the delivery response. See `docs/objectives.md` for the full two-objective example. Set this field in game with `/iekdialogue response set <response-id> executeobjective <objective-id>`; use `none` to clear it.

## Administration commands

Use `/iekdialogue help` for the command summary. The command requires `iekquests.dialogue` or `iekquests.admin`.

- `/iekdialogue list [dialogue|response|npc|tag|sequence]` lists loaded entries.
- `/iekdialogue validate [id]` validates the whole graph or the selected entry and its reachable dialogue/response references.
- `/iekdialogue show <dialogue|response|npc|tag|sequence> <id>` displays the loaded entry.
- `/iekdialogue delete <dialogue|response|npc|tag> <id>` refuses deletion while the entry is referenced or assigned.
- `/iekdialogue npc list|bind <key> <citizens-id> <display-name> <dialogue-id,...>|unbind <key-or-id>|visibility <key-or-id> <tag,...|none>` manages graph bindings for existing Citizens NPCs.
- `/iekdialogue tag define player|server <tag-id>` registers a tag. Use `/iekdialogue tag player list <player>` to list all player tags assigned to an online or previously seen player. Use `/iekdialogue tag player add|remove <player> <tag-id>` and `/iekdialogue tag server add|remove <tag-id>` to manage assignments.
- `/iekdialogue sequences list|show|validate [id]` inspects reusable delayed sequences.
- `/iekdialogue cooldown clear <player> [response-id]` clears stored response cooldowns.
- `/iekdialogue test <dialogue-id>` opens an explicitly marked admin preview. Selecting a response in preview has no effects.
- `/iekdialogue reload` reloads quest data, graph files, tag registries, NPC bindings, and configuration. `/questbook reload` performs the same reload.
- `/iekdialogue dialogue create <id>` and `/iekdialogue response create <id>` create graph entries. `set <id> <field> <value>` edits existing fields. Lists are comma-separated; `\n` splits dialogue text into lines; use `none` for empty lists or to end a response.
- Response field `executeobjective` accepts one ID of an objective configured with `Manual: true`, or `none` to clear it.
- Supported fields are shown in the in-game graph editor opened from `/questwizard`, or in `/iekdialogue help`.

`/questwizard` retains quest objectives, progress, requirements, rewards, and repeat-contract authoring. Its Dialogue graph editor creates and edits dialogues, responses, tags, and NPC bindings. The old stage `lines` and `choices` controls are no longer offered for new authoring. Existing legacy fields remain readable for migration and compatibility. Quest Wizard NPC association metadata remains available for the quest engine; configure the separate Citizens-to-dialogue binding in the graph editor.

Edits are saved in the plugin data directory and never synchronized to GitHub.

## Migration and compatibility

Configuration import and archive behavior is documented in [Quest Package Files](quest-packages.md). The quest engine remains available through the generated `organization/quests.yml`; old flat quest definitions are not loaded as an implicit fallback. Existing player progress remains in `data.yml`.

The migration is one-time and does not overwrite graph files if any of the three files already exists. If only some of the three graph files exist, automatic migration is skipped and `dialogue-migration-report.txt` lists affected NPC-bound quest IDs and the manual migration steps; the report is not overwritten once created. Review generated files before editing them. NPCs shared by multiple quests receive ordered dialogue entries based on the existing quest order. Verify those shared-NPC branches against intended server behavior after migration.

Paper's native Dialog API requires a compatible Paper server and Minecraft client. The JAR compiles against the project's Paper 26.2 API. This build was not tested on a live server or client. No resource pack is required.


## Response tag removal and sequences

Responses can remove player tags with `remove-player-tags`. The response must pass all dialogue/response checks first; when it has `executeobjective`, the Manual objective must also complete successfully before any response effect runs. On success, removals run before `add-player-tags`, followed by server tag additions, Journal changes, commands, voice, cooldown and quest actions. Tags listed in both add and remove fields are invalid. Tag changes immediately refresh per-player NPC visibility.

Dialogue and response entries may set `sequences: [sequence_id]`. The sequence starts once when the dialogue/response is selected. See `docs/sequences.md` for scheduling, prerequisites, supported step actions, and cancellation behavior.

## NPC visibility

A binding may define `visibility-prerequisites` in `npcs.yml`. Conditions use registered player tags, AND logic, and the same `!tag` absence syntax as dialogue prerequisites. For example:

```yaml
npc_tutorial_dialogue:
  display-name: Dialogue tutorial
  npc-id: 123
  visibility-prerequisites:
    - tutorial_step_1_completed
    - "!tutorial_dialogue_completed"
  dialogues:
    - tutorial_dialogue_intro
```

Visibility is evaluated per player using Paper's viewer-specific entity visibility and the optional Citizens NPC entity API. The plugin never globally hides or despawns the NPC. Visibility refreshes on player join, NPC spawn/reload, and IekQuests player-tag changes, including objective, event, dialogue, sequence, reward, and administrative changes. Interaction checks visibility again so stale entity visibility cannot open the dialogue. Without Citizens, NPC binding/visibility cannot be applied and validation reports that integration as unavailable. Missing or empty `visibility-prerequisites` leaves the binding visible to everyone.
