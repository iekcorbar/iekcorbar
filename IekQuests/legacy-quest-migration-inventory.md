# Historical Legacy Quest Inventory

## Scope and verification status

This is a historical inventory prepared from the bundled `quests.yml` before its removal from the packaged resources and from the active Java source in the local workspace. It is not an active quest configuration or a migration report. The bundled legacy quest definitions have been removed from the plugin package; the legacy quest engine remains available only for server-authored definitions explicitly included in a `quests` chapter of a Quest bundle. The workspace contains no production `quests.yml`, `wizard-quests.yml`, or `data.yml`; server-authored definitions and player progress therefore cannot be inventoried or migrated here. The workspace has no Git metadata, and GitHub authentication was unavailable during verification, so this checkout could not be compared with the current remote `main` branch. The attached RAR could not be extracted with the available tools and was not treated as a verified source.

The active legacy quest runtime has not been removed. The old bundled quest definitions have since been removed from the packaged resources, as requested, while server-authored definitions remain migratable through Quest bundles.

## Legacy feature inventory and modular destination

| Legacy feature | Current implementation | Required modular destination | Migration gap/status |
| --- | --- | --- | --- |
| Quest IDs, titles, subtitles, icons, order, groups | `Quest` model and questbook ordering/grouping | Dialogue IDs and display text; Journal pages | Display fields have no direct universal mapping; questbook grouping/order needs a replacement UI/index. |
| NPC IDs and multiple NPC bindings | `npc-id`, `npc-ids`; legacy NPC dialogue lookup | `npcs.yml` binding to dialogue IDs | Requires generated NPC bindings and equivalent eligibility across all bound NPCs. |
| Start conditions | `start` predicates | Dialogue prerequisites and player/server tags | Predicates include tags, quest-local tags, permissions, NPC IDs, and money comparisons; not all currently have equivalent modular predicates. |
| Quest states and acceptance/refusal/restart | Per-player `started`, `done`, `stage`, `startedAt`; choice actions | Tags, dialogue response effects, objective state, event transitions | Requires explicit state tags and one-time transitions. Existing persisted state is absent from this workspace. |
| Ordered stages and progression | Ordered `stages`, `next`, `goto`, random start/stage selection | Per-stage objectives plus dialogue branches and transition tags | The modular Objective list uses AND; it cannot directly represent an ordered stage graph without an added workflow model. |
| Stage types | `talk`, `craft`, `deliver`, `deliverall`, `walk`, `kill`, `menu` | Objectives, dialogues, response choices | `deliverall` with item/name matching and the `menu` interaction need explicit parity. |
| Stage conditions, labels, lines, voice | Stage `cond`, `label`, `lines`, `voice` | Dialogue/response prerequisites, text, voice | Requires translating current condition grammar and preserving page/choice behavior. |
| Choices, conditions, branches, close, daily limits | Ordered choice maps/lists with `cond`, `goto`, `action`, `do`, `close`, `limit-per-day` | Responses, tags, next-dialogue, cooldowns | Choice visibility, actions, and transition order need a complete mapping. |
| Stage, quest, and repeat rewards | Shared legacy `run` action executor | `Reward.yml`, dialogue rewards, objective completion rewards | The action grammar is broader than the current modular reward validation; unsafe/unsupported actions must be implemented or explicitly rejected before migration. |
| Repeat contracts | Per-quest repeat definitions with item/walk requirements, cooldown/daily, money/eco, voice and rewards | Objectives, response actions, cooldown and persistent repeat state | Modular system has no equivalent per-contract counters/cooldowns/repeat workflow confirmed in this checkout. |
| Quest-local tags and points | `Prog.tags` and `Prog.points`; conditions use `qtag` and money/permission checks | Registered player tags, server tags, explicit counters/conditions | Quest-local tags can be namespaced, but points and numeric conditions need a persistent generic counter facility. |
| Timed sequences | `sequences` with ordered delay/action steps | Events, rewards, or a shared scheduled action sequence service | Existing modular systems do not provide verified delay sequences. |
| Questbook GUI and chat mode | `/questbook`, inventory GUI, chat dialogues, action bar tracker, compass | `/journal`, dialogues and objective UI | Journal is a book and does not yet replace quest list/group browsing, GUI choices, chat mode, tracking, or quest cooldown displays. |
| Quest wizard and staff commands | `/questwizard`; `/questbook reload/check/track/reset/give` and related handlers | Dialogue/objective/event admin commands and editors | Current modular admin tools do not cover all legacy editing, reset, tracking, and inspection operations. |
| Commands and permissions | `questbook`, `questwizard`, granular permission nodes and aliases | Existing modular command roots | Removing legacy commands requires equivalent admin/player workflows, aliases, permissions, and help. |
| Event hooks | Legacy quest accept/complete calls into `EventsManager` | `on-quest-accept` / `on-quest-complete` or generic workflow transitions | Must remain active and system-neutral after quest IDs stop being loaded by the legacy engine. |
| Player progress persistence | Legacy `Prog` includes stage, started/done, startedAt, local tags, points, cooldowns | Existing `data.yml` objective/tag/journal state plus a new workflow state section | Production data is absent. The inspected `loadData()` iterates `players.<uuid>` but reads quest state from `<uuid>.quests`; `saveData()` clears `players` and writes `<uuid>.quests`. The actual deployed data layout must be inspected before any migration; this apparent path mismatch must not be guessed around. |

## Checked-in quest definitions

The checked-in file contains 70 quest definitions, 169 stages, and two timed sequences. Every quest below is accounted for in this inventory. Counts are the number of configured stages, choice entries, and repeat contracts. Stage type counts show the legacy flow that must be preserved.

| Quest ID | Stages and types | Choices | Repeat contracts | Destination mapping required |
| --- | --- | ---: | ---: | --- |
| `milo` | 2: talk 1, craft 1 | 0 | 1 | Dialogue, ordered objectives, Journal, repeat workflow/reward |
| `igor` | 4: talk 1, craft 1, deliver 2 | 0 | 1 | Dialogue, ordered objectives, Journal, repeat workflow/reward |
| `vorkan` | 1: talk 1 | 3 | 12 | Dialogue branches, Journal, 12 repeat workflows |
| `brakka` | 1: talk 1 | 3 | 12 | Dialogue branches, Journal, 12 repeat workflows |
| `petra` | 1: talk 1 | 3 | 12 | Dialogue branches, Journal, 12 repeat workflows |
| `aster` | 1: talk 1 | 3 | 11 | Dialogue branches, Journal, 11 repeat workflows |
| `teahouse` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `sakebrewery` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `bbqmaster` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `sushimaster` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `candymerchant` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `fruitmarket` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `coffeehouse` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `icecreamcart` | 1: talk 1 | 1 | 6 | Dialogue choice, Journal, six repeat workflows |
| `mad_doctor` | 1: talk 1 | 3 | 0 | Dialogue branches, delayed sequence `md_show` / `md_quick`, Journal |
| `usop` | 1: talk 1 | 2 | 0 | Dialogue branches, compass/travel action, Journal |
| `techcore` | 2: talk 1, deliver 1 | 0 | 0 | Ordered dialogue/objective workflow and Journal |
| `steam_gears` | 3: talk 1, deliver 2 | 0 | 0 | Ordered dialogue/objective workflow and Journal |
| `steam_boiler` | 3: talk 1, deliver 2 | 0 | 0 | Ordered dialogue/objective workflow and Journal |
| `black_gold` | 3: talk 1, deliver 2 | 0 | 0 | Ordered dialogue/objective workflow and Journal |
| `iron_road` | 3: talk 1, deliver 1, deliverall 1 | 0 | 0 | Ordered workflow, multi-item delivery consumption, Journal |
| `engineer_contracts` | 1: talk 1 | 1 | 5 | Dialogue choice and five repeat workflows |
| `cleric` | 1: talk 1 | 6 | 0 | Conditional dialogue choices and action parity |
| `unbind_scrolls` | 1: talk 1 | 2 | 0 | Dialogue choices, item/action parity |
| `rod_merchant` | 1: talk 1 | 3 | 12 | Dialogue branches and 12 repeat workflows |
| `guard_1`–`guard_24` | 24 quests, each 1 menu stage | 21 each | 0 | 24 NPC-bound menu workflows with 504 choices total |
| `backpack_one` | 15: talk 2, walk 4, deliverall 3, kill 3, menu 3 | 11 | 0 | Ordered stage graph, inventory consumption, kill/walk progress, branches |
| `backpack_two` | 22: talk 2, menu 8, walk 4, kill 3, deliverall 5 | 26 | 0 | Ordered stage graph, branches, inventory consumption, kill/walk progress |
| `backpack_three` | 10: talk 3, menu 6, walk 1 | 19 | 10 | Branch graph, ten repeat workflows, quest-local prerequisites |
| `jobs_rank1`–`jobs_rank5` | 5 quests, each 7 stages: talk 3, menu 4 | 30 each | 0 | 5 ordered NPC workflows with 150 choices and permission/job actions |
| `kit_knife` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `kit_prep` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `kit_hot` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `kit_feast` | 2: talk 1, deliverall 1 | 0 | 3 | Ordered item delivery and three repeat workflows |
| `sak_garden` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `sak_cellar` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `sak_still` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `sak_bottler` | 2: talk 1, deliver 1 | 0 | 3 | Ordered item delivery and three repeat workflows |
| `kura_first` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `kura_lines` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `kura_flow` | 2: talk 1, deliverall 1 | 0 | 0 | Ordered item delivery workflow |
| `kura_net` | 2: talk 1, deliverall 1 | 0 | 3 | Ordered item delivery and three repeat workflows |
| `land_surveyor` | 1: talk 1 | 3 | 0 | Dialogue branches and compass/location action |

## Runtime grammar found in source

### Conditions

The checked-in definitions use `tag`, `!tag`, `qtag`, `!qtag`, `npc`, `money` comparisons, and `perm`. The runtime parses these as comma-separated conditions. A migration must preserve AND semantics, comparison operators, NPC context, and quest-local tag ownership.

### Actions

The shared legacy action executor contains handlers for `money`, `pay`, `eco`, `ecopay`, `eloot`, `title`, `sound`, `say`, `tag+`, `tag-`, `perm+`, `perm-`, `point`, `effect`, `teleport`, `item`, `travel`, `xp`, `heal`, `feed`, `broadcast`, `cmdplayer`, `takeitem`, `give`, `compass`, `shop`, `joinjob`, `leavejob`, `chat`, and `sequence`. Unknown prefixes fall through to console command dispatch, which is an existing security-sensitive behavior that must be deliberately preserved or safely replaced. Checked-in quest data additionally contains `techcore`, `valhallaraces:races`, `npcguide`, `satchel`, `relics`, `gouw`, `em`, and `lp` command-style actions; their effects depend on installed plugins/commands and cannot be asserted without those integrations.

### Timed sequences

The checked-in configuration contains `md_show` (25 delayed steps) and `md_quick` (3 delayed steps). They use delay-prefixed action lines and `%player%` substitution. Sequence scheduling, cancellation, disconnect behavior, and duplicate prevention must be specified in any replacement.

## Removal gate

The legacy engine must remain active until a replacement can validate and represent every row and feature above, a dry run has no unmapped items, a verified backup and idempotent progress migration are available, and the real server-authored YAML and `data.yml` have been supplied for migration. The checked-in defaults alone are insufficient to safely migrate production-specific definitions or player progress. In particular, removing `quests.yml`, `quest-actions`, `/questbook`, or `/questwizard` at this stage would violate the required parity and data-safety checks.
