package com.iekcorbar.sitesync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads plugins/Gouw/claims.json — the Gouw plugin (in-house successor of Lands) keeps its
 * claims purely local JSON, no MySQL — and counts the chunks every owner holds, so the
 * website's lands_claimed column follows Gouw instead of the retired Lands plugin. Unlike
 * the PlaceholderAPI bridge this also covers owners that are offline right now.
 * The file is parsed once per change (lastModified), never per player.
 */
public class GouwClaims {

    private static Map<String, Integer> counts = null;
    private static long stamp = -1;

    /** Chunks this player owns, 0 when Gouw has claims but none of them are this player's,
     *  null when claims.json is not there/unreadable (caller decides the fallback). */
    public static synchronized Integer count(UUID owner) {
        Map<String, Integer> c = load();
        return c == null ? null : c.getOrDefault(owner.toString().toLowerCase(), 0);
    }

    private static Map<String, Integer> load() {
        File file = new File(Bukkit.getPluginsFolder(), "Gouw/claims.json");
        if (!file.isFile()) return null;
        try {
            long m = file.lastModified();
            if (counts != null && m == stamp) return counts;
            Map<String, Integer> next = new HashMap<>();
            JsonObject root = JsonParser.parseString(
                    Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("lands")) {
                JsonObject o = e.getAsJsonObject();
                String owner = o.get("owner").getAsString().toLowerCase();
                int chunks = o.getAsJsonArray("chunks").size();
                next.merge(owner, chunks, Integer::sum);
            }
            counts = next;
            stamp = m;
            return counts;
        } catch (Exception ex) {
            return counts; // keep the last good read; Gouw will save a fresh file on the next change
        }
    }
}
