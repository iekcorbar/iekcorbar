# Reusable sequences

Reusable delayed sequences are defined in the `Sequences` chapter of server-local Quest bundles and generated at `organization/Sequences.yml`.

A sequence can be started by a dialogue, response, or event entry with a `sequences` list of IDs. Sequence IDs are independent from quest IDs and the `quests` chapter.

```yaml
tutorial_system_messages:
  prerequisiteTag:
    - tutorial_expanded_systems_step
  steps:
    - after: 4s
      commands:
        - "server: tell %playername% Read the Adventurer's Guide for details about fishing."
    - after: 8s
      commands:
        - "server: tell %playername% The guide also explains agriculture and brewing."
```

`after` is a non-negative offset from sequence start. Use `ms`, `s`, or `m`; for example `250ms`, `4s`, or `2m`. Steps must be listed in increasing offset order. Equal offsets are allowed and keep YAML declaration order.

Every `prerequisiteTag` condition uses AND logic. Positive tags must be assigned; prefix a tag with `!` to require absence. IekQuests checks prerequisites when starting the sequence and again before each step. If a check fails or the player disconnects, remaining steps are canceled.

A step may contain `commands`, `add-player-tags`, `remove-player-tags`, `addjournal`, `removejournal`, `rewards`, and `dialogues`. Commands use the shared `server:`, `ser:`, `player:`, or `op:` prefixes, support `%playername%`, and retain the shared trailing `-s` feedback behavior. Reward values reference IDs in `Reward.yml`. Tag and Journal references are validated. Dialogue IDs are queued if another dialogue is open.

Steps run on the server thread. Pending sequence timers are transient and are not persisted. Disconnect, plugin reload, and plugin disable cancel pending steps; completed steps are not replayed after reconnect or reload. A later distinct trigger can start the sequence again after its previous run ends. `/iekdialogue sequences list|show|validate [id]` inspects the loaded definitions. `/iekdialogue validate` and `/iekdialogue events validate` also report sequence references and definition errors.
