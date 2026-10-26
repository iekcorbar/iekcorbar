# Plugin-fixes: Lands-resten -> Gouw (2026-10-26)

Twee serverbestanden die nog verwezen naar de verwijderde Lands-plugin.
`pluginset-aangepast/` staat in .gitignore (met player-data erin), dus deze
kopieen bestaan voor git-backup hier.

**Deploy**: kopieer beide bestanden over hun origineel op de server en reload:

1. `ItemJoin/items.yml`     -> `plugins/ItemJoin/items.yml`  (`/itemjoin reload`, of next restart)
2. `DecentHolograms/...`    -> `plugins/DecentHolograms/holograms/Land_claim.yml` (`/decentholograms reload`, of next restart)

Wat er is veranderd:
- Het join-item `Pyro_plugins` (NETHERITE_HOE in de crafting-grid) heet nu
  "&eGouw Menu" en draait `op: gouw menu` in plaats van `op: lands` — dat
  commando bestond niet meer sinds Lands van de server is.
- Het spawn-hologram "Land Claim Shop" toont nu "Gouw Menu". Claims kopen kan
  niet meer (Gouw werkt met een vaste chunk-limiet), dus de tekst wijst nu
  naar het menu in plaats van naar een winkel.
