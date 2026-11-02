# Quest Package Files

IekQuests keeps editable quest packages in the server data folder at `Quest/`. Each YAML file is a bundle; include only the chapters that belong to that package. The chapter names are case-sensitive:

```yaml
player-tags:
  tags:
    - tutorial_started
Objective:
  tutorial_reach_building:
    PlayerTag:
      - tutorial_reach_building_active
    ServerTag: []
    Objective:
      - type: reach
        world: iekcorbarSeason2
        x: 100
        y: 70
        z: -25
        radius: 5
    OncompleteRemoveTag:
      - tutorial_reach_building_active
    OncompleteAssignTag:
      - tutorial_building_reached
Journal:
  tutorial_reach_building:
    Title:
      - Reach the guild building
    description:
      - Find the guild building and speak to its guide.
dialogues:
  tutorial_start:
    title: Tutorial
    lines:
      - Welcome to Iek Corbar.
    prerequisites: []
    server-prerequisites: []
    responses:
      - tutorial_accept
    commands: []
    voice: ""
    add-player-tags: []
    add-server-tags: []
    addjournal: []
    removejournal: []
    Reward: []
responses:
  tutorial_accept:
    text: I will begin.
    prerequisites: []
    server-prerequisites: []
    commands: []
    add-player-tags:
      - tutorial_reach_building_active
    add-server-tags: []
    addjournal: []
    removejournal: []
    executeobjective: ""
    next-dialogue: ""
```

Supported chapters are `server-tags`, `Reward`, `responses`, `quests`, `player-tags`, `Objective`, `npcs`, `Journal`, `events`, `dialogues`, and `Sequences`. `Sequences` is the reusable delayed-action system: sequences run scheduled commands, tag and Journal changes, rewards, or dialogues when started by a dialogue, response, or event. It is shared infrastructure and does not represent a quest by itself.

## Generated runtime configuration

`organization/` contains generated aggregate files: `server-tags.yml`, `Reward.yml`, `responses.yml`, `quests.yml`, `player-tags.yml`, `Objective.yml`, `npcs.yml`, `Journal.yml`, `events.yml`, `dialogues.yml`, and `Sequences.yml`. These files are rebuilt from `Quest/*.yml`; edit the source bundles or use supported in-game editors instead of editing generated files by hand.

Run `/iekquest reload quest` after adding or changing a bundle. The command validates the package set before replacing the generated files. If package parsing or duplicate definitions fail, the previous `organization/` folder remains active and the command reports the source file and validation issue. A successful rebuild keeps a timestamped backup of the previous generated folder. It then reloads the quest, dialogue, objective, event, Journal, reward, and sequence systems without resetting player data.

Files are processed in case-insensitive filename order. Duplicate IDs across packages stop the rebuild. If multiple packages bind the same Citizens NPC ID, IekQuests merges their dialogue lists when their visibility prerequisites agree and reports the merge; conflicting visibility rules stop the rebuild.

The Quest Wizard writes each wizard-managed legacy quest into its own `_wizard-<id>.yml` bundle. Admin editors write changes back to the source bundle. New editor-created entries without an existing source owner are stored in `_server-managed.yml`.

## Upgrade behavior

On first startup after this change, if `Quest/` has no YAML bundles, existing flat modular configuration files are imported once into `Quest/_server-config-import.yml`. Flat `quests.yml` and `wizard-quests.yml` are archived under `Quest/_legacy-disabled/` and are not loaded as active legacy quest definitions. The remaining old flat YAML files are also archived there after the imported copy is saved. `data.yml` is not rewritten by this import, so unrelated dialogue tags, cooldowns, Journal assignments, and Objective progress remain intact.

If bundles already exist during the first upgrade, the old flat files are archived under `Quest/_legacy-disabled/` and the import is skipped to avoid silently combining conflicting definitions. Review that archive and copy any required definitions into the appropriate Quest bundle. The startup warning identifies this case. The legacy `quests.yml` is never imported as an active quest chapter; it is retained only in the archive.
