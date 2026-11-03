# IekQuests Events

Event rules are generated at `plugins/IekQuests/organization/events.yml` from the `events` chapter in `Quest/*.yml`. Each top-level event section is a mapping of event IDs to entries. Empty sections use `{}`.

Every matching entry runs in file order. An entry checks all `prerequisiteTag` expressions with AND logic. Prefix a player tag with `!` to require it to be absent. Eligible entries assign `setplayertag`, update Journal pages, execute `commands`, start configured `sequences`, then queue `dialogues` in that order. `on-leave` does not allow direct dialogues. Sequence IDs reference `organization/Sequences.yml`.

`addjournal` and `removejournal` contain IDs from `organization/Journal.yml`; unknown IDs are reported by validation and ignored at runtime.

## Event types and filters

| Section | Trigger | Required filter |
| --- | --- | --- |
| `on-join` | Every player connection | None |
| `on-first-join` | The server's first join for that player | None |
| `on-leave` | Player disconnect | None; dialogues are forbidden |
| `on-respawn` | Player respawn | None |
| `on-world-change` | Player changes worlds | `world`; optional `from-world` |
| `on-advancement` | Player completes an advancement | `advancement` NamespacedKey |
| `on-quest-accept` | IekQuests starts a quest | `quest-id` |
| `on-quest-complete` | IekQuests completes a quest | `quest-id` |
| `on-level-up` | Vanilla Minecraft XP level increases | `level` or `min-level` / `max-level` |
| `on-region-enter` | Player enters a WorldGuard region | `region`; requires WorldGuard event API |
| `on-region-leave` | Player leaves a WorldGuard region | `region`; requires WorldGuard event API |
| `on-objective-complete` | An Objective.yml objective changes from incomplete to completed | `objective-id` |

`on-objective-complete` runs only on the transition to completed, after the objective completion state, its own tags, and its Journal changes have been applied and saved. Its prerequisites therefore see updated tags. An admin reset permits a later completion to fire a new occurrence. Every matching eligible entry runs in declaration order.

`on-level-up` observes the vanilla Minecraft XP level. It does not observe MMOCore or another external RPG level. Quest events are emitted by the IekQuests quest engine.

## Example

```yaml
on-join:
  welcome_back:
    prerequisiteTag: []
    commands:
      - "server: say Welcome back! -s"
    setplayertag: []
    addjournal: []
    removejournal: []
    dialogues: []
    sequences: []

on-first-join: {}
on-leave: {}
on-respawn: {}
on-world-change: {}
on-advancement: {}
on-quest-accept: {}
on-quest-complete: {}
on-level-up: {}
on-region-enter: {}
on-region-leave: {}
on-objective-complete: {}
```

Command entries use `server:`, `ser:`, `player:`, or `op:` prefixes and may end in `-s`. The suffix is removed before dispatch. It marks the command as silent for IekQuests feedback; it cannot suppress output emitted independently by another plugin or a command such as `say` that intentionally broadcasts.

## Administration

Use `/iekdialogue events list [event-type]`, `show <event-type> <id>`, `create <event-type> <id>`, `set <event-type> <id> <field> <value>`, `delete <event-type> <id>`, `validate [event-type] [id]`, and `reload`. Lists entered through `set` are comma-separated. Event editor changes are written back to their owning Quest bundle; reload does not reset quest progress, tag assignments, or dialogue cooldowns.

An `on-objective-complete` entry must set `objective-id` to an existing Objective ID. It may queue `dialogues` or start reusable `sequences`. Objective completion state and its own tags and Journal effects are applied before the event runs.

Operators can manually start configured entries with `/iekquest event start <player> <event-id>`. It starts all entries with that ID across event sections, bypasses event-specific filters, and still checks each entry's `prerequisiteTag`. Journal pages can also be assigned or removed with `/iekquest journal add <player> <entry-id>` and `/iekquest journal remove <player> <entry-id>`.
