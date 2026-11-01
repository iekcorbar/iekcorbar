package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.PreparedStatement;
import java.util.Locale;

/**
 * Hands the Sakagura garden season to the website. Sakagura keeps its season in its own
 * config.yml (seasons.current / start-time / days-per-season) and has no database, so — like
 * GouwClaims for the claims — this reads that file and writes one JSON value into the
 * existing settings table (key sakagura_season). The site's /calendar and /api/season read it.
 * Read-only towards Sakagura; the write is skipped when nothing changed.
 *
 * Config (season-sync:): enabled, every-seconds.
 */
public class SeasonSync {
    private final IekcorbarSiteSync plugin;
    private final MySQLSync mysql;
    private BukkitTask task;
    private String lastWritten = "";

    SeasonSync(IekcorbarSiteSync plugin, MySQLSync mysql) {
        this.plugin = plugin;
        this.mysql = mysql;
    }

    void start() {
        if (!plugin.getConfig().getBoolean("season-sync.enabled", true)) return;
        long every = Math.max(60, plugin.getConfig().getInt("season-sync.every-seconds", 300)) * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sync, 200L, every);
    }

    void stop() {
        if (task != null) task.cancel();
    }

    private void sync() {
        try {
            File file = new File(Bukkit.getPluginsFolder(), "Sakagura/config.yml");
            if (!file.isFile()) return;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            String season = y.getString("seasons.current", "SPRING").toUpperCase(Locale.ROOT);
            long start = y.getLong("seasons.start-time", 0L);
            int days = Math.max(1, y.getInt("seasons.days-per-season", 7));
            if (start <= 0) return;
            long next = start + days * 86_400_000L;
            String json = "{\"season\":\"" + season.replaceAll("[^A-Z]", "") + "\",\"start\":" + start
                    + ",\"days\":" + days + ",\"next\":" + next + "}";
            if (json.equals(lastWritten)) return;
            try (PreparedStatement ps = mysql.sharedConnection().prepareStatement(
                    "INSERT INTO settings (setting_key, setting_value) VALUES ('sakagura_season', ?) "
                            + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)")) {
                ps.setString(1, json);
                ps.executeUpdate();
            }
            lastWritten = json;
        } catch (Exception ex) {
            plugin.getLogger().warning("SeasonSync: " + ex.getMessage());
        }
    }
}
