# Valhalla-onderzoek (REA) — bevindingen 2026-10-29

Opdracht van de eigenaar: ValhallaMMO met REA onderzoeken en de
**core-features verbeteren in een EIGEN plugin**; Trinkets en Races worden
**addons**. Vraag "zit deze data al in onze player database?" is beantwoord:
ja (zie §2). Decompileerd bronmateriaal staat in `deploy/valhalla-decomp/`
(gitignore-werk, lokaal alleen). Dit document is de ingang voor de volgende
sessie.

## 1. De drie jars (aangeleverd via archive-2026-10-28T231233Z.tar.gz)

| plugin | versie | main-klasse | klassen | status |
|---|---|---|---|---|
| ValhallaMMO | 1.10.3 | `me.athlaeos.valhallammo.ValhallaMMO` | 1842 | API 1.19; softdepends: Vault, WorldEdit, WorldGuard, PlaceholderAPI, DecentHolograms, JustLootIt, CoreProtect, Nexo, CraftEngine, ItemsAdder |
| ValhallaTrinkets | 2.9 | `me.athlaeos.valhallatrinkets.ValhallaTrinkets` | 65 | softdepend MMO; /trinkets-menu, /trinketeditor |
| ValhallaRaces | 2.3 | `me.athlaeos.valhallaraces.ValhallaRaces` | 54 (51 leesbaar) | **depend** op MMO; rassen + klassen, eigen statsources |

Alle drie draaien op de live-server (Map: `plugins/ValhallaMMO`,
`plugins/ValhallaTrinkets`, `plugins/ValhallaRaces`).

**Licentie-waarschuwing:** dit is betaalde closed-source software met
copy-protection (obfuscated klassen `Dgduf`/`HXvGq`/`igzHW`/`JHesM`,
licentiecontroles + bStats). Voor onze eigen plugin geldt: features en
datamodellen mogen we NABOUWEN (eigen code, eigen naam), hun broncode
mogen we niet overnemen. Het decompilen is hier uitsluitend
onderzoeksdetail — de bevindingen hieronder zijn vertaald naar gedrag en
data-vormen, niet naar kopieerbare code.

## 2. Storage: DE DATA ZIT AL IN ONZE PLAYER DATABASE ✅

`config.yml` (live): `db_type: mysql`, host `172.18.0.1` (= Plesk-MySQL),
databank **`s30266_iekcorbar_player_data`**, gebruiker `u30266_…`.
De oude `plugins/ValhallaMMO/player_data.db` (sqlite, 172 KB) is een
overblijfsel van 20 sep en wordt NIET meer gebruikt.

Tabellen in diezelfde db (naast jobs_*, betonquest_*, mmocore_*,
mmoitems_*, lands_*):

* **14 × `profiles_<skill>`** — mining, digging, woodcutting, farming,
  fishing, archery, light_weapons, heavy_weapons, light_armor,
  heavy_armor, smithing, alchemy, enchanting, power.
  Kolommen (voorbeeld `profiles_mining`): `owner` (UUID), `exp` (double),
  `exp_total`, `level` (int), `maxallowedlevel` (default 32767),
  `newgameplus`, `forcepersist`, plus **per-perk kolommen**
  (miningdrops, veinminingunlocked/cooldown/validblocks/instantpickup,
  drillingspeedbonus/cooldown/duration/unlocked, blastingdrops/luck/
  itemimmunity/tntblastradius/tntdamagereduction/blastfortunelevel,
  miningspeedbonus, miningluck, blockexperiencemultiplier,
  emptyhandtool* …). Rijen: 3–18 per skill (29 spelers actief).
* **`PlayerClassProfile`** (9) + **`PlayerClassProgress`** (4) — Races:
  welk ras/klasse een speler heeft + voortgang.
* Trinkets: GEEN db-tabellen — trinket-items dragen hun id/properties in
  de **item-NBT** (`PersistentDataContainer`, keys
  `valhallatrinkets:trinket_unique_id`, `…:unique`, …) +
  `valhallatrinkets.yml` in de pluginmap.
* StaffHub leest de 14 skill-rijen al via één UNION-query (zie CLAUDE.md
  27-10: "ValhallaMMO-vormen + de 14 profiles_*-skillrijen").

**Gevolg voor de verplaatsing:** onze eigen plugin kan op dezelfde
tabellen blijven leunen (of ze netter maken: 14 tabellen → 1 ×
`mmo_profiles(uuid, skill, level, exp, data-json)`), en een import is één
`SELECT` op bestaande rijen.

## 3. Feature-scan MMO-kern (uit plugin.yml + 1518 gedecompilde bronnen)

Commando's: `/valhalla` (+16 skill-shortcuts, `/redeem` level/exp-tokens,
`/valtop` leaderboards, `/skills`, party-commandos). Permissies: o.a.
`valhalla.exp.<skill>.#` (exp-vermenigvuldigers via permissies!),
`valhalla.recipe.#`, noobprotectie, cooldowns-bypass.

Architectuur (package-kaart):
`playerstats/` = kernmotor: AccumulativeStatManager + StatCollector/Builder +
~dozens `StatSource`-klassen (PerkLevelSource, RaceAttributeSource,
TrinketAttributeSource, GlobalBuffSource, PermissionStatSource,
PotionEffectAttacker/DefenderSource, MainHandPenalty, …) — ALLES is een
stat-source die op de stack van een speler wordt gelegd.
`skills/` = skill-trees + perks (perks hebben rewards, resourcecost,
unlockconditions).
`item/` = eigen item-registry + attributes-op-items + armorsets +
quality/intended-level.
`crafting/`, `loot/`, `loot_tables/`, `recipes/`, `replacement_tables/` =
eigen recipen/loot-draaitabellen (jos).
`parties/`, `gui/`, `commands/`, `listeners/` (75!), `nms/` + `paper/` +
`version/` = NMS-laag per MC-versie, `hooks/` = external integrations
(HookManager.java is 1.5 MB aan integratie-klassen).

**Data-gedreven kern** (dit willen we overnemen van concept, niet als code):
* exp-kurves zijn **formule-strings in de config**:
  `exp_level_curve: '(%level% + 75 * 2^(%level%/7.6)) + 300'` per skill in
  `skills/<skill>_progression.yml`, plus `max_level`, exp-multipliers per
  activiteit en per-blok exp-tabellen (`mining_break: COBBLESTONE: 8, …`).
* perk-bomen in `skills/<skill>.yml`; talen in `languages/`;
  custom content in `skills/custom/`.

## 4. Wat Trinkets en Races doen (addon-blauwdruk)

* **Trinkets**: extra uitrustingsplekken (ring/bracer/amulet…); items
  krijgen trinket-properties via `/trinketeditor` (NBT); /trinkets GUI om
  ze te dragen; perk- en level-eisen; loot-tabel + recipen;
  keepinventory-permissie. Stats lopen als StatSources in de MMO-stack.
* **Races**: ras + klasse per speler (keuzemenu, /races), eigen
  attribute-sources + actieve abilities met cooldowns
  (CooldownManager), `classes.yml` + `races.yml` data; opslag in
  PlayerClassProfile/Progress.
  **Volledig leesbaar (29-10, 01:25): 51 van de 54 klassen gedecompileerd**
  — de protector `pdjAO/RLHXh/UhzX/SknVT/yszthvbepghksbpl` uitgesloten,
  wat de eerdere vineflower-hang veroorzaakte. Structuur is precies zoals
  de data suggereert: `Race`/`RaceManager`/`RacePickerMenu` en
  `Class`/`ClassManager`/`ClassPickerMenu` met `RaceStatSource`/
  `ClassStatSource` (+Offensive/Defensive-varianten) die als
  StatSources in de MMO-stack schuiven; `RaceSource`/`ClassSource` als
  externe API; `CooldownManager` voor abilities; `DataManager` =
  PlayerClassProfile/Progress-tabellen.
  **Races is volledig DATA-gedreven** — rassen
  met `stat_buffs` (MINING_EXP_GAIN: 0.2, EXPLOSION_RESISTANCE: 0.4,
  MOVEMENT_SPEED_BONUS: -0.15, HEALTH_BONUS: 4 …) in `races.yml`, klassen
  met `group`-nummer (één keuze per groep) + `stat_buffs` + optionele
  `race_filter` in `classes.yml`; config: plicht-keuze bij join/bewegen,
  GUI-settings, permissie-bestandaam). Code-decompilatie is hierfür niet
  eens nodig: de sjabloon IS de feature. Onze addon-bouw: zelfde
  data-vorm, eigen stat-source-API (§3), geen GUI-rommel overnemen.

## 5. Verbeterpunten voor EIGEN plugin (doel van de opdracht)

1. **Asynchroon & licht**: de 75 listener-klassen + per-block-break exp
   doen Valhalla zwaar; onze kern: één EventRouter, exp op async batch,
   Caffeine-cache met TTL, DB-write-behind (Valhalla: 10 profile-threads +
   optioneel Redis-sloten).
2. **Eén profieldata-model**: json-kolom of 1 tabel i.p.v. 14×26 kolommen
   (import uit de bestaande `profiles_*` is rechttoe-rechtaan).
3. **Geen licentie-dieren, geen bStats, geen resourcepack-verplichting.**
4. **Onze stijl**: koppeling naar het iekcorbar-merk (Steamworks-ketens,
   Zukan-verzamelingen, Kura-huisstijl); PlaceholderAPI-uitgangen voor de
   site + StaffHub (leest nu de tabellen al).
5. **Add-ons als losse modules**: trinkets + races/class als aparte jarren
   met eigen API-call (stat-source registratie) — precies zoals Valhalla het
   intern doet, maar dan expliciet en klein.
6. RACES-API: PlaceholderSync van SiteSync kan straks onze placeholders
   leveren (`%iekcorbarmmo_level_mining%` enz.).

## 6. Reproduceer-specificatie (voor volgende sessies)

```powershell
# jars uit de aangeleverde tar
tar -xzf "C:\Users\baaye\Downloads\archive-2026-10-28T231233Z.tar.gz" -C $w
# REA opende ze als archief (geen Java-decompiler in REA — JVM-class is
# ondersteund pad): sha256 MMO-jar =
# ce64a70de90b0d14de93f4330ac3edf362d742f9f63f7dcd39d94a54c74a8099
# Vineflower 1.11.1 (sha1 77767cdbc76d116c6d5e1565ec387e531085b9f4,
# geverifieerd op Maven Central) - de license-classes uitsluiten:
java -jar vineflower.jar "-e=me/athlaeos/valhallaraces/Dgduf.class,me/athlaeos/valhallaraces/HXvGq.class,me/athlaeos/valhallaraces/igzHW.class,me/athlaeos/valhallaraces/JHesM.class,me/athlaeos/valhallaraces/mXjYm.class,me/athlaeos/valhallaraces/qjgRP.class" <jar> <out>
# (Races liep vast op precies die protectie-klassen; mét bovenstaande
#  -e-uitsluitingen decompileert de jar keurig: 51 bronnen. Volledige
#  klassenlijst + API-shape staat in §4 hierboven.)
```
