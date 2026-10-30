package com.iekcorbar.sakagura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brewing Competition: players submit their best drinks, scored by quality + difficulty.
 * Admin starts/stops a competition; players submit drinks while holding them.
 * Leaderboard and rewards on competition end.
 */
final class Competition {
    private final Sakagura plugin;
    private final File file;
    private YamlConfiguration data;

    // competition state
    private boolean active;
    private long startTime;
    private long endTime;
    private String theme; // optional theme (drink type, or "any")

    // submissions: player UUID -> list of submissions
    private final Map<UUID, List<Submission>> submissions = new ConcurrentHashMap<>();

    // leaderboard cache
    private List<LeaderEntry> leaderboard = new ArrayList<>();

    static final class Submission {
        final UUID player;
        final String playerName;
        final String drinkSlug;
        final String drinkName;
        final int quality;
        final int difficulty;
        final double score;
        final long timestamp;

        Submission(UUID player, String playerName, String drinkSlug, String drinkName,
                   int quality, int difficulty, long timestamp) {
            this.player = player;
            this.playerName = playerName;
            this.drinkSlug = drinkSlug;
            this.drinkName = drinkName;
            this.quality = quality;
            this.difficulty = difficulty;
            // score = quality * 10 + difficulty * 5 (max ~150)
            this.score = quality * 10.0 + difficulty * 5.0;
            this.timestamp = timestamp;
        }
    }

    static final class LeaderEntry {
        final UUID player;
        final String playerName;
        final String drinkName;
        final int quality;
        final int difficulty;
        final double score;

        LeaderEntry(Submission s) {
            this.player = s.player;
            this.playerName = s.playerName;
            this.drinkName = s.drinkName;
            this.quality = s.quality;
            this.difficulty = s.difficulty;
            this.score = s.score;
        }
    }

    Competition(Sakagura plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "competitions.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            data = new YamlConfiguration();
            return;
        }
        data = YamlConfiguration.loadConfiguration(file);
        active = data.getBoolean("active", false);
        startTime = data.getLong("start", 0);
        endTime = data.getLong("end", 0);
        theme = data.getString("theme", "any");
        submissions.clear();
        ConfigurationSection subs = data.getConfigurationSection("submissions");
        if (subs != null) {
            for (String key : subs.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<Submission> list = new ArrayList<>();
                    for (Map<?, ?> m : subs.getMapList(key)) {
                        String slug = String.valueOf(m.get("slug"));
                        String name = String.valueOf(m.get("name"));
                        int q = ((Number) m.get("quality")).intValue();
                        int d = ((Number) m.get("difficulty")).intValue();
                        long ts = ((Number) m.get("timestamp")).longValue();
                        String pname = String.valueOf(m.get("player"));
                        list.add(new Submission(uuid, pname, slug, name, q, d, ts));
                    }
                    submissions.put(uuid, list);
                } catch (Exception ignored) { }
            }
        }
        rebuildLeaderboard();
    }

    private void save() {
        data = new YamlConfiguration();
        data.set("active", active);
        data.set("start", startTime);
        data.set("end", endTime);
        data.set("theme", theme);
        for (Map.Entry<UUID, List<Submission>> e : submissions.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Submission s : e.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("player", s.playerName);
                m.put("slug", s.drinkSlug);
                m.put("name", s.drinkName);
                m.put("quality", s.quality);
                m.put("difficulty", s.difficulty);
                m.put("timestamp", s.timestamp);
                list.add(m);
            }
            data.set("submissions." + e.getKey(), list);
        }
        try {
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save competitions.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ admin

    boolean start(Player admin, int days, String theme) {
        if (active) return false;
        active = true;
        startTime = System.currentTimeMillis();
        endTime = startTime + (long) days * 24 * 60 * 60 * 1000;
        this.theme = theme == null || theme.isBlank() ? "any" : theme;
        submissions.clear();
        rebuildLeaderboard();
        save();
        return true;
    }

    boolean stop() {
        if (!active) return false;
        active = false;
        endTime = System.currentTimeMillis();
        save();
        return true;
    }

    boolean isActive() { return active && System.currentTimeMillis() < endTime; }

    long timeRemaining() {
        if (!active) return 0;
        return Math.max(0, endTime - System.currentTimeMillis());
    }

    String theme() { return theme; }

    // ------------------------------------------------------------ submit

    String submit(Player p, ItemStack drink) {
        if (!isActive()) return "No competition is running right now.";
        // Check WorldGuard region first (if WorldGuard is available and a region is active)
        String activeRegion = plugin.compArea().activeAreaName();
        if (activeRegion != null && plugin.worldEdit().worldGuardAvailable()) {
            if (!plugin.worldEdit().isInRegion(p.getLocation(), activeRegion)) {
                return "You must be inside the competition area (" + activeRegion + ") to submit.";
            }
        } else if (!plugin.compArea().isInActiveArea(p.getLocation())) {
            CompetitionArea.Area a = plugin.compArea().activeArea();
            if (a != null) return "You must be inside the competition area (" + a.name + ") to submit.";
        }
        Brew b = plugin.goods().brewOf(drink);
        if (b == null) return "You must be holding a finished Sakagura drink.";
        Recipes.Match m = plugin.recipes().match(b);
        if (m == null || !m.finished() || m.quality() <= 0) return "That drink isn't finished yet.";
        if (theme != null && !theme.equals("any") && !m.recipe().slug.equals(theme)) {
            return "This competition is themed: " + theme + " only.";
        }
        // one submission per player per competition (best only)
        List<Submission> existing = submissions.get(p.getUniqueId());
        if (existing != null && !existing.isEmpty()) {
            Submission best = existing.stream().max(Comparator.comparingDouble(s -> s.score)).orElse(null);
            if (best != null && m.quality() * 10.0 + m.recipe().difficulty * 5.0 <= best.score) {
                return "Your best submission (" + best.drinkName + ", " + best.quality + " stars) is already better or equal.";
            }
            // replace with better
            submissions.get(p.getUniqueId()).clear();
        }
        String drinkName = m.recipe().names.isEmpty() ? m.recipe().slug : m.recipe().names.get(0);
        Submission sub = new Submission(p.getUniqueId(), p.getName(), m.recipe().slug, drinkName,
                m.quality(), m.recipe().difficulty, System.currentTimeMillis());
        submissions.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(sub);
        rebuildLeaderboard();
        save();
        return "Submitted " + drinkName + " (" + m.quality() + " stars, difficulty " + m.recipe().difficulty + "). Score: " + (int) sub.score;
    }

    // ------------------------------------------------------------ leaderboard

    private void rebuildLeaderboard() {
        List<LeaderEntry> list = new ArrayList<>();
        for (List<Submission> subs : submissions.values()) {
            for (Submission s : subs) {
                list.add(new LeaderEntry(s));
            }
        }
        // best per player only
        Map<UUID, LeaderEntry> best = new LinkedHashMap<>();
        for (LeaderEntry e : list) {
            LeaderEntry cur = best.get(e.player);
            if (cur == null || e.score > cur.score) best.put(e.player, e);
        }
        leaderboard = new ArrayList<>(best.values());
        leaderboard.sort(Comparator.comparingDouble((LeaderEntry e) -> e.score).reversed());
    }

    List<LeaderEntry> leaderboard() {
        rebuildLeaderboard();
        return leaderboard;
    }

    int rank(UUID player) {
        rebuildLeaderboard();
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).player.equals(player)) return i + 1;
        }
        return -1;
    }

    // ------------------------------------------------------------ rewards

    void giveRewards() {
        if (leaderboard.isEmpty()) return;
        // top 3 get rewards
        int[] money = {500, 250, 100};
        int[] eco = {20, 10, 5};
        String[] titles = {"&6&lMaster Brewer", "&e&lSkilled Brewer", "&7&lRising Brewer"};
        for (int i = 0; i < Math.min(3, leaderboard.size()); i++) {
            LeaderEntry e = leaderboard.get(i);
            Player p = Bukkit.getPlayer(e.player);
            if (p == null) continue;
            // money via console command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi money give " + p.getName() + " " + money[i]);
            // elite coins
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "em money add " + p.getName() + " " + eco[i]);
            // title
            p.sendTitle(titles[i], "&7Competition winner!", 10, 60, 20);
            p.sendMessage(Component.text("You placed #" + (i + 1) + " in the brewing competition!", NamedTextColor.GOLD));
            p.sendMessage(Component.text("Reward: $" + money[i] + " + " + eco[i] + " elite coins", NamedTextColor.GREEN));
        }
        // achievement for winner
        if (!leaderboard.isEmpty()) {
            Player winner = Bukkit.getPlayer(leaderboard.get(0).player);
            if (winner != null) {
                plugin.adv().grant(winner, "comp_winner");
            }
        }
    }

    // ------------------------------------------------------------ status

    Component statusLine() {
        if (!active) return Component.text("No competition running.", NamedTextColor.GRAY);
        long rem = timeRemaining();
        long days = rem / (24 * 60 * 60 * 1000);
        long hours = (rem / (60 * 60 * 1000)) % 24;
        long mins = (rem / (60 * 1000)) % 60;
        String time = days > 0 ? days + "d " + hours + "h" : hours + "h " + mins + "m";
        return Component.text("Competition active! Theme: " + theme + " | Ends in: " + time + " | Entries: " + leaderboard.size(), NamedTextColor.GOLD);
    }
}
