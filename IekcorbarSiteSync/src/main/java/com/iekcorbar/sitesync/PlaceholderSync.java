package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic PlaceholderAPI bridge — resolves whatever placeholders staff
 * configured under `placeholder-sync.placeholders` in config.yml for a given
 * online player, so the website can surface data from any PlaceholderAPI
 * -compatible plugin (Lands, ValhallaMMO, ValhallaRaces, ...) without a code
 * change/plugin rebuild every time a new stat is wanted. Uses reflection
 * rather than a compile-time PlaceholderAPI dependency, matching this
 * project's existing pattern for other optional integrations (see the Jobs
 * Reborn block in MySQLSync.java / WebStatsCommand.java).
 */
public class PlaceholderSync {

    public static class Result {
        // The "lands_claims" key, parsed as a whole number if present and
        // numeric — null otherwise (caller decides the fallback). Kept
        // separate from `json` because it also feeds the website's existing
        // dedicated `player_stats.lands_claimed` column (the one the
        // homepage "Claims Protected" total sums), for backward compatibility.
        public final Integer landsClaims;
        // Every configured placeholder's resolved value, as a flat JSON
        // object of key -> string value. "{}" if placeholder-sync is
        // disabled, PlaceholderAPI isn't installed, the player is offline,
        // or nothing configured resolved to anything.
        public final String json;

        Result(Integer landsClaims, String json) {
            this.landsClaims = landsClaims;
            this.json = json;
        }
    }

    private static final Result EMPTY = new Result(null, "{}");

    public static Result resolve(IekcorbarSiteSync plugin, OfflinePlayer offlinePlayer) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("placeholder-sync");
        if (section == null || !section.getBoolean("enabled", true)) return EMPTY;

        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null) return EMPTY;

        // Every plugin backing these placeholders (Lands, ValhallaMMO, ...)
        // needs a live Player object, not just an OfflinePlayer — same
        // online-only limitation the EliteMobs coin sync already has
        // elsewhere in this project.
        if (!offlinePlayer.isOnline() || offlinePlayer.getPlayer() == null) return EMPTY;
        Player player = offlinePlayer.getPlayer();

        ConfigurationSection placeholders = section.getConfigurationSection("placeholders");
        if (placeholders == null) return EMPTY;

        Method setPlaceholders;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholders = papiClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Exception e) {
            return EMPTY;
        }

        Map<String, String> resolved = new LinkedHashMap<>();
        for (String key : placeholders.getKeys(false)) {
            String raw = placeholders.getString(key);
            if (raw == null || raw.isEmpty()) continue;
            try {
                Object value = setPlaceholders.invoke(null, player, raw);
                // PlaceholderAPI returns the placeholder text back unchanged
                // (or a plugin-specific "not found" string) when it can't
                // resolve it — that's still a String, not null, so this
                // can't reliably filter out a bad config entry; verifying
                // placeholders with /papi parse before adding them (see the
                // config.yml comment) is the real safeguard.
                if (value != null) resolved.put(key, value.toString());
            } catch (Exception ignored) {
                // Skip just this one placeholder, keep resolving the rest.
            }
        }

        if (resolved.isEmpty()) return EMPTY;

        Integer landsClaims = null;
        if (resolved.containsKey("lands_claims")) {
            try {
                landsClaims = Integer.parseInt(resolved.get("lands_claims").replaceAll("[^0-9-]", ""));
            } catch (Exception ignored) {
                // Not a plain number this time (placeholder unresolved/misconfigured) —
                // caller falls back to its own default rather than writing garbage.
            }
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(escapeJson(entry.getKey())).append("\":\"").append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        json.append("}");

        return new Result(landsClaims, json.toString());
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
