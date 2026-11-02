# IekQuests Journal

Journal page definitions are generated at `plugins/IekQuests/organization/Journal.yml` from `Journal` chapters in `Quest/*.yml`. Each top-level key is a stable entry ID. `Titolo` is optional; `descrizione` contains one or more lines displayed below the title.

```yaml
lost_compass:
  Titolo:
    - Find the lost compass
  descrizione:
    - Search the old road west of the harbor.
    - Return the compass to the surveyor.
```

The player command `/journal` opens a written book titled `Journal` containing only that player's assigned entries. If no pages are assigned, the book explains that the Journal is empty. Assignments are stored by player UUID under `journal.<uuid>.entries` in `data.yml` and survive reloads and restarts.

## Assigning and removing entries

IekQuests can change a player's Journal through three systems:

- `events` chapter: entry fields `addjournal` and `removejournal` run when the event entry is eligible.
- `Objective` chapter: `addjournal` and `removejournal` run once when the objective completes.
- `dialogues` and `responses` chapters: `addjournal` and `removejournal` run when the dialogue opens or the response is selected.

Every configured ID is validated against the generated Journal definitions. Unknown IDs are reported and are not assigned.

Operators can manage online players directly:

```text
/iekquest journal add <player> <entry-id>
/iekquest journal remove <player> <entry-id>
```

Reload the Journal definitions through `/questbook reload` or `/iekdialogue reload`. Reloading definitions does not clear player assignments. Deleting or renaming a Journal entry does not erase the stored assignment; validation reports any stale IDs until the definition is restored or the assignment is removed.
