package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deep player sync for Staff Panel > Player Data: vanilla statistics and
 * advancement progress live in the server's own save files, not in any table —
 * so this walks the player_stats rows oldest-deep-sync first and writes the
 * result into four new player_stats columns (uuid, stats_json,
 * advancements_json, deep_updated). Stats read fine offline
 * (OfflinePlayer#getStatistic); this API only exposes advancements on a live
 * Player, so those come from the periodic cycle while players are online and
 * from the quit event itself — where Bukkit still hands us the real Player.
 *
 * The walk is deliberately drip-fed (batch every N seconds, never the main
 * thread) — a few hundred players cost one slow cycle, not a lag spike. Each
 * quit writes that player's complete final snapshot directly, so data is at
 * most one cycle old for anyone who actually plays.
 *
 * Config (deep-sync:): enabled, every-seconds, batch-size, refresh-minutes.
 */
public class DeepSync implements Listener {
    private final IekcorbarSiteSync plugin;
    private final MySQLSync mysql;
    private BukkitTask task;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean columnsOk = false;

    /** Vanilla stats worth showing staff; unknown/renamed enums are skipped silently. */
    private static final String[] STATISTIC_CANDIDATES = {
            "DEATHS", "PLAYER_KILLS", "MOB_KILLS", "ANIMALS_BRED", "FISH_CAUGHT", "TRADES",
            "SLEEP_IN_BED", "JUMP", "WALK_ONE_CM", "SPRINT_ONE_CM", "CROUCH_ONE_CM",
            "SWIM_ONE_CM", "CLIMB_ONE_CM", "DIVE_ONE_CM", "MINECART_ONE_CM", "BOAT_ONE_CM",
            "HORSE_ONE_CM", "PIG_ONE_CM", "STRIDER_ONE_CM", "FLY_ONE_CM", "AVIATE_ONE_CM",
            "SADDLE_ONE_CM", "DAMAGE_DEALT", "DAMAGE_TAKEN", "HEALED",
            "DROP", "PICKUP", "BREAK_ITEM", "MINED_BLOCK", "USE_ITEM", "CRAFT_ITEM",
            "ITEM_ENCHANTED", "ENCHANT_ITEM", "CHEST_OPENED", "TRAPPED_CHEST_INSPECTED",
            "DISPELSER_INSPECTED", "FLOWER_POTTED", "BANNER_CLEANED", "SHULKER_TEXTURED",
            "PLAY_RECORD", "TOTAL_KILLS", "MULTIKILL", "BEST_KILL_STREAK", "RAID_OBTAINED"
    };

    DeepSync(IekcorbarSiteSync plugin, MySQLSync mysql) {
        this.plugin = plugin;
        this.mysql = mysql;
    }

    void start() {
        if (!plugin.getConfig().getBoolean("deep-sync.enabled", true)) return;
        ensureColumns();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int every = Math.max(5, plugin.getConfig().getInt("deep-sync.every-seconds", 30));
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cycle, 200L, every * 20L);
    }

    void stop() {
        if (task != null) task.cancel();
    }

    /** Add the deep columns once (checked through information_schema — portable to MySQL and MariaDB). */
    private void ensureColumns() {
        String[][] wanted = {
                {"uuid", "VARCHAR(40)"},
                {"stats_json", "LONGTEXT"},
                {"advancements_json", "LONGTEXT"},
                {"deep_updated", "DATETIME NULL"}
        };
        try {
            Connection conn = mysql.sharedConnection();
            for (String[] col : wanted) {
                boolean has = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'player_stats' AND COLUMN_NAME = ?")) {
                    ps.setString(1, col[0]);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) has = rs.getInt(1) > 0;
                    }
                }
                if (!has) {
                    try (PreparedStatement st = conn.prepareStatement("ALTER TABLE player_stats ADD COLUMN " + col[0] + " " + col[1])) {
                        st.executeUpdate();
                        plugin.getLogger().info("[DeepSync] player_stats." + col[0] + " added.");
                    }
                }
            }
            columnsOk = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[DeepSync] could not prepare player_stats columns: " + e.getMessage());
        }
    }

    /** The oldest-deep-synced rows first (never-synced before anything else), small batch per run. */
    private void cycle() {
        if (!running.compareAndSet(false, true)) return;
        try {
            if (!columnsOk) { ensureColumns(); if (!columnsOk) return; } // player_stats may not exist yet on a fresh start
            int batch = Math.max(1, plugin.getConfig().getInt("deep-sync.batch-size", 5));
            int refresh = Math.max(1, plugin.getConfig().getInt("deep-sync.refresh-minutes", 15));
            List<String[]> due = new ArrayList<>();
            try (PreparedStatement ps = mysql.sharedConnection().prepareStatement(
                    "SELECT username, uuid FROM player_stats WHERE deep_updated IS NULL OR deep_updated < NOW() - INTERVAL " + refresh + " MINUTE"
                            + " ORDER BY deep_updated IS NULL DESC, deep_updated ASC LIMIT " + batch)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) due.add(new String[]{rs.getString(1), rs.getString(2)});
                }
            }
            for (String[] row : due) {
                try {
                    syncOne(row[0], row[1]);
                } catch (Exception e) {
                    if (plugin.getConfig().getBoolean("debug", false))
                        plugin.getLogger().warning("[DeepSync] " + row[0] + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false))
                plugin.getLogger().warning("[DeepSync] cycle failed: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private void syncOne(String username, String uuidCol) {
        UUID uuid = resolve(username, uuidCol);
        if (uuid == null) return;
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        Player online = op.isOnline() ? op.getPlayer() : null;
        write(op, uuid, username, online != null ? advancementsJson(online) : null);
    }

    /** Vanilla stats work for offline players too; advancements only exist on a live Player. */
    private static Map<String, Long> statsOf(org.bukkit.OfflinePlayer op) {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (String name : STATISTIC_CANDIDATES) {
            try {
                Statistic s = Statistic.valueOf(name);
                int v = op.getStatistic(s);
                if (v > 0) stats.put(s.name().toLowerCase(Locale.ROOT), (long) v);
            } catch (IllegalArgumentException ignored) { /* renamed/unknown stat */ }
        }
        return stats;
    }

    private void write(org.bukkit.OfflinePlayer op, UUID uuid, String username, String advJson) {
        try (PreparedStatement ps = mysql.sharedConnection().prepareStatement(
                "UPDATE player_stats SET uuid = ?, stats_json = ?, advancements_json = COALESCE(?, advancements_json), deep_updated = NOW() WHERE username = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, statsJson(statsOf(op)));
            ps.setString(3, advJson);
            ps.setString(4, username);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    /**
     * {done:[keys...], by:{category:[done,inprogress,total]}} — this API only exposes
     * per-advancement progress on a live Player, so we walk the whole registry once.
     */
    private String advancementsJson(Player p) {
        List<String> doneKeys = new ArrayList<>();
        Map<String, long[]> by = new LinkedHashMap<>();
        Iterator<Advancement> all = Bukkit.advancementIterator();
        while (all.hasNext()) {
            Advancement a = all.next();
            String cat = categoryOf(a.getKey().toString());
            long[] c = by.computeIfAbsent(cat, k -> new long[3]);
            c[2]++;
            var prog = p.getAdvancementProgress(a);
            if (prog.isDone()) {
                c[0]++;
                doneKeys.add(a.getKey().toString());
            } else if (!prog.getAwardedCriteria().isEmpty()) {
                c[1]++;
            }
        }
        java.util.Collections.sort(doneKeys);
        StringBuilder sb = new StringBuilder("{\"done\":[");
        int cap = Math.min(doneKeys.size(), 400);
        for (int i = 0; i < cap; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(doneKeys.get(i)).append('"');
        }
        sb.append("],\"by\":{");
        boolean first = true;
        for (Map.Entry<String, long[]> e : by.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":[").append(e.getValue()[0]).append(',').append(e.getValue()[1]).append(',').append(e.getValue()[2]).append(']');
        }
        sb.append("},\"total\":").append(doneKeys.size()).append(",\"list_full\":").append(doneKeys.size() <= 400).append('}');
        return sb.toString();
    }

    private static String categoryOf(String key) {
        int c = key.indexOf(':');
        String path = c >= 0 ? key.substring(c + 1) : key;
        int s = path.indexOf('/');
        return s > 0 ? path.substring(0, s) : "misc";
    }

    /** Prefer the uuid column if SiteSync already filled it; else the name lookup (works for everyone who ever played). */
    private UUID resolve(String username, String uuidCol) {
        if (uuidCol != null && !uuidCol.isBlank()) {
            String hex = uuidCol.replace("-", "");
            if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
                UUID u = UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
                if (Bukkit.getOfflinePlayer(u).hasPlayedBefore()) return u;
            }
        }
        try {
            org.bukkit.OfflinePlayer byName = Bukkit.getOfflinePlayer(username);
            if (byName.hasPlayedBefore() && byName.getUniqueId() != null) return byName.getUniqueId();
        } catch (Exception ignored) { }
        return null;
    }

    /** {"k":v,...} — our values are plain longs, escaping is trivial. */
    private static String statsJson(Map<String, Long> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean f = true;
        for (Map.Entry<String, Long> e : m.entrySet()) {
            if (!f) sb.append(',');
            f = false;
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        return sb.append('}').toString();
    }

    /**
     * The quit event still hands us a live Player — the last chance to read
     * advancements before they go offline-only. Build the fresh copy on the
     * main thread (pure in-memory reads), then push it to the database async.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!columnsOk) return;
        Player p = e.getPlayer();
        String advJson;
        Map<String, Long> stats;
        try {
            advJson = advancementsJson(p);
            stats = statsOf(p);                              // read while the player object is still alive
        } catch (Exception ex) {
            return;
        }
        UUID uuid = p.getUniqueId();
        String username = p.getName();
        String statsJson = statsJson(stats);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {   // never touch JDBC from the main thread
            try (PreparedStatement ps = mysql.sharedConnection().prepareStatement(
                    "UPDATE player_stats SET uuid = ?, stats_json = ?, advancements_json = ?, deep_updated = NOW() WHERE username = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, statsJson);
                ps.setString(3, advJson);
                ps.setString(4, username);
                ps.executeUpdate();
            } catch (Exception ignored) {
            }
        });
    }
}
