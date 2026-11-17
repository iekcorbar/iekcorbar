package com.iekcorbar.hihou.bw;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.6.0: per-season BedWars numbers in plugins/Hihou/bwstats.json - games, wins, kills,
 * deaths, beds broken and the coins paid out. Plain readable JSON (Gson for the reading,
 * hand-written for the writing - the same deal as claims.json), because a stat file you
 * can open in a text editor is a stat file the next plugin can read without me.
 *
 * The season is just a number in the file: /ih bw season reset bumps it and empties the
 * rows, so "this season's top" always says honestly which season it means.
 */
final class BwStats {
    static final int GAMES = 0, WINS = 1, KILLS = 2, DEATHS = 3, BEDS = 4, COINS = 5;
    private static final String[] KEYS = {"games", "wins", "kills", "deaths", "beds", "coins"};
    private static final int ROWS = 6;

    private final File file;
    private int season = 1;
    private final Map<UUID, int[]> rows = new LinkedHashMap<>();

    BwStats(File file) {
        this.file = file;
        load();
    }

    int season() {
        return season;
    }

    private void load() {
        if (!file.exists()) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            season = root.has("season") ? root.get("season").getAsInt() : 1;
            for (var e : root.getAsJsonArray("players")) {
                JsonObject o = e.getAsJsonObject();
                UUID u = UUID.fromString(o.get("uuid").getAsString());
                int[] row = new int[ROWS];
                for (int i = 0; i < ROWS; i++) row[i] = o.has(KEYS[i]) ? o.get(KEYS[i]).getAsInt() : 0;
                rows.put(u, row);
            }
        } catch (Exception ex) {
            // a broken stats file costs a season of numbers, never a match: start fresh but keep the old one
            try {
                Files.move(file.toPath(), file.toPath().resolveSibling("bwstats.json.broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder("{ \"season\": ").append(season).append(", \"players\": [\n");
        boolean first = true;
        for (Map.Entry<UUID, int[]> e : rows.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            sb.append("  {\"uuid\": \"").append(e.getKey()).append('"');
            for (int i = 0; i < ROWS; i++) sb.append(", \"").append(KEYS[i]).append("\": ").append(e.getValue()[i]);
            sb.append('}');
        }
        sb.append("\n]}\n");
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    int get(UUID u, int what) {
        int[] r = rows.get(u);
        return r == null ? 0 : r[what];
    }

    void game(UUID u) {
        bump(u, GAMES, 1);
    }

    void win(UUID u) {
        bump(u, WINS, 1);
    }

    void kill(UUID u) {
        bump(u, KILLS, 1);
    }

    void death(UUID u) {
        bump(u, DEATHS, 1);
    }

    void bed(UUID u) {
        bump(u, BEDS, 1);
    }

    void coins(UUID u, int n) {
        bump(u, COINS, n);
    }

    private void bump(UUID u, int what, int n) {
        rows.computeIfAbsent(u, k -> new int[ROWS])[what] += n;
    }

    /** Called at the end of every match - and nowhere else, so the disk stays quiet during play. */
    void flush() {
        save();
    }

    void resetSeason() {
        season++;
        rows.clear();
        save();
    }

    /** Top n by wins (ties break on kills). */
    List<Map.Entry<UUID, int[]>> top(int n) {
        List<Map.Entry<UUID, int[]>> out = new ArrayList<>(rows.entrySet());
        out.sort((a, b) -> b.getValue()[WINS] != a.getValue()[WINS]
                ? Integer.compare(b.getValue()[WINS], a.getValue()[WINS])
                : Integer.compare(b.getValue()[KILLS], a.getValue()[KILLS]));
        return out.size() > n ? out.subList(0, n) : out;
    }

    String line(UUID u) {
        int[] r = rows.getOrDefault(u, new int[ROWS]);
        return nameOf(u) + " - season " + season + ": " + r[WINS] + "w " + r[KILLS] + "k " + r[DEATHS] + "d "
                + r[BEDS] + " beds, " + r[GAMES] + " games, " + r[COINS] + " coins earned";
    }

    /** "0 wins" rows stay out of the top list. */
    boolean has(UUID u) {
        int[] r = rows.get(u);
        return r != null && (r[GAMES] > 0 || r[KILLS] > 0);
    }

    private static String nameOf(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        String n = op.getName();
        return n == null ? "someone" : n;
    }
}
