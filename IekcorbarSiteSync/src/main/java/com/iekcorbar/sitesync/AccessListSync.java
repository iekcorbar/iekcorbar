package com.iekcorbar.sitesync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;

// Mirrors the server's real banned-players.json and whitelist.json (vanilla
// format — both bukkit ban commands and CMI's own /cmi ban write through to
// these, they're not CMI-only files) into MySQL, read-only from the
// website's side, same one-way-mirror pattern as everything else this
// plugin syncs. These live at the server root (next to server.properties),
// not inside any plugin's own data folder — Bukkit.getWorldContainer()
// resolves to that root directory by default.
//
// Full-replace on every sync (truncate + reinsert) rather than incremental
// add/remove tracking, since these files are themselves just point-in-time
// snapshots — this keeps the mirror honest about unbans/whitelist removals
// too, not just new entries.
public class AccessListSync {

    private final IekcorbarSiteSync plugin;
    private final Gson gson = new Gson();

    public AccessListSync(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
    }

    public void createTablesIfNotExist(Connection conn) {
        try {
            String sqlBanned = "CREATE TABLE IF NOT EXISTS banned_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "username VARCHAR(100) NOT NULL," +
                    "created VARCHAR(50)," +
                    "source VARCHAR(100)," +
                    "expires VARCHAR(50)," +
                    "reason TEXT," +
                    "synced_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";
            try (PreparedStatement stmt = conn.prepareStatement(sqlBanned)) {
                stmt.executeUpdate();
            }

            String sqlWhitelist = "CREATE TABLE IF NOT EXISTS whitelisted_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "username VARCHAR(100) NOT NULL," +
                    "synced_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;";
            try (PreparedStatement stmt = conn.prepareStatement(sqlWhitelist)) {
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("AccessListSync table creation notice: " + e.getMessage());
        }
    }

    public void sync(Connection conn) {
        syncBannedPlayers(conn);
        syncWhitelist(conn);
    }

    private void syncBannedPlayers(Connection conn) {
        File file = new File(Bukkit.getWorldContainer(), "banned-players.json");
        if (!file.exists()) return;
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonArray entries = JsonParser.parseString(content).getAsJsonArray();

            try (PreparedStatement clear = conn.prepareStatement("TRUNCATE TABLE banned_players")) {
                clear.executeUpdate();
            }
            String sql = "INSERT INTO banned_players (uuid, username, created, source, expires, reason) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (JsonElement el : entries) {
                    JsonObject obj = el.getAsJsonObject();
                    stmt.setString(1, getStr(obj, "uuid"));
                    stmt.setString(2, getStr(obj, "name"));
                    stmt.setString(3, getStr(obj, "created"));
                    stmt.setString(4, getStr(obj, "source"));
                    stmt.setString(5, getStr(obj, "expires"));
                    stmt.setString(6, getStr(obj, "reason"));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Failed to sync banned-players.json: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync banned players to MySQL: " + e.getMessage());
        }
    }

    private void syncWhitelist(Connection conn) {
        File file = new File(Bukkit.getWorldContainer(), "whitelist.json");
        if (!file.exists()) return;
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonArray entries = JsonParser.parseString(content).getAsJsonArray();

            try (PreparedStatement clear = conn.prepareStatement("TRUNCATE TABLE whitelisted_players")) {
                clear.executeUpdate();
            }
            String sql = "INSERT INTO whitelisted_players (uuid, username) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (JsonElement el : entries) {
                    JsonObject obj = el.getAsJsonObject();
                    stmt.setString(1, getStr(obj, "uuid"));
                    stmt.setString(2, getStr(obj, "name"));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Failed to sync whitelist.json: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync whitelist to MySQL: " + e.getMessage());
        }
    }

    private String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
}
