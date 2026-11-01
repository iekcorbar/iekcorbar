package com.iekcorbar.kitchen;

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
 * Cooking Competition: players submit their best Kitchen meals, scored by
 * nutrition + saturation. Admin starts/stops; players submit while holding the
 * meal. Leaderboard and rewards on competition end.
 */
final class Competition {
    private final KitchenPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    private boolean active;
    private long startTime;
    private long endTime;
    private String region; // WorldGuard region submissions must be made in (null = anywhere)
    private String theme; // meal category ("any", "ramen", "sushi", ...)

    private final Map<UUID, List<Submission>> submissions = new ConcurrentHashMap<>();
    private List<LeaderEntry> leaderboard = new ArrayList<>();

    static final class Submission {
        final UUID player;
        final String playerName;
        final String itemName;
        final int nutrition;
        final float saturation;
        final double score;
        final long timestamp;

        Submission(UUID player, String playerName, String itemName, int nutrition, float saturation, long timestamp) {
            this.player = player;
            this.playerName = playerName;
            this.itemName = itemName;
            this.nutrition = nutrition;
            this.saturation = saturation;
            // score = nutrition * 10 + saturation * 5 (max ~100)
            this.score = nutrition * 10.0 + saturation * 5.0;
            this.timestamp = timestamp;
        }
    }

    static final class LeaderEntry {
        final UUID player;
        final String playerName;
        final String itemName;
        final int nutrition;
        final float saturation;
        final double score;

        LeaderEntry(Submission s) {
            this.player = s.player;
            this.playerName = s.playerName;
            this.itemName = s.itemName;
            this.nutrition = s.nutrition;
            this.saturation = s.saturation;
            this.score = s.score;
        }
    }

    Competition(KitchenPlugin plugin) {
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
        region = data.getString("region", null);
        submissions.clear();
        ConfigurationSection subs = data.getConfigurationSection("submissions");
        if (subs != null) {
            for (String key : subs.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<Submission> list = new ArrayList<>();
                    for (Map<?, ?> m : subs.getMapList(key)) {
                        String name = String.valueOf(m.get("name"));
                        int n = ((Number) m.get("nutrition")).intValue();
                        float sat = ((Number) m.get("saturation")).floatValue();
                        long ts = ((Number) m.get("timestamp")).longValue();
                        String pname = String.valueOf(m.get("player"));
                        list.add(new Submission(uuid, pname, name, n, sat, ts));
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
        data.set("region", region);
        for (Map.Entry<UUID, List<Submission>> e : submissions.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Submission s : e.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("player", s.playerName);
                m.put("name", s.itemName);
                m.put("nutrition", s.nutrition);
                m.put("saturation", s.saturation);
                m.put("timestamp", s.timestamp);
                list.add(m);
            }
            data.set("submissions." + e.getKey(), list);
        }
        try { data.save(file); } catch (Exception e) {
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

    String region() { return region; }

    void setRegion(String name) { region = name; save(); }

    // ------------------------------------------------------------ submit

    String submit(Player p, ItemStack food) {
        if (!isActive()) return "No competition is running right now.";
        if (region != null && plugin.worldEdit() != null && plugin.worldEdit().worldGuardAvailable()
                && !plugin.worldEdit().isInRegion(p.getLocation(), region)) {
            return "You must be inside the competition area (" + region + ") to submit.";
        }
        KItem t = plugin.items().typeOf(food);
        if (t == null || (t.kind != KItem.Kind.FOOD && t.kind != KItem.Kind.DRINK && t.kind != KItem.Kind.MEAL)) {
            return "You must be holding a Kitchen meal or food item.";
        }
        if (theme != null && !theme.equals("any")) {
            String cat = t.name().toLowerCase().replace("_", " ");
            if (!cat.contains(theme.toLowerCase())) {
                return "This competition is themed: " + theme + " only.";
            }
        }
        // one submission per player per competition (best only)
        List<Submission> existing = submissions.get(p.getUniqueId());
        if (existing != null && !existing.isEmpty()) {
            Submission best = existing.stream().max(Comparator.comparingDouble(s -> s.score)).orElse(null);
            if (best != null && t.nutrition * 10.0 + t.saturation * 5.0 <= best.score) {
                return "Your best submission (" + best.itemName + ", " + best.nutrition + " nutrition) is already better or equal.";
            }
            submissions.get(p.getUniqueId()).clear();
        }
        Submission sub = new Submission(p.getUniqueId(), p.getName(), t.displayName, t.nutrition, t.saturation, System.currentTimeMillis());
        submissions.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(sub);
        rebuildLeaderboard();
        save();
        return "Submitted " + t.displayName + " (nutrition " + t.nutrition + ", saturation " + t.saturation + "). Score: " + (int) sub.score;
    }

    // ------------------------------------------------------------ leaderboard

    private void rebuildLeaderboard() {
        List<LeaderEntry> list = new ArrayList<>();
        for (List<Submission> subs : submissions.values()) {
            for (Submission s : subs) list.add(new LeaderEntry(s));
        }
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
        int[] money = {500, 250, 100};
        int[] eco = {20, 10, 5};
        String[] titles = {"Master Chef", "Skilled Cook", "Rising Cook"};
        for (int i = 0; i < Math.min(3, leaderboard.size()); i++) {
            LeaderEntry e = leaderboard.get(i);
            Player p = Bukkit.getPlayer(e.player);
            if (p == null) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi money give " + p.getName() + " " + money[i]);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "em money add " + p.getName() + " " + eco[i]);
            p.sendTitle(titles[i], "Cooking competition winner!", 10, 60, 20);
            p.sendMessage(Component.text("You placed #" + (i + 1) + " in the cooking competition!", NamedTextColor.GOLD));
            p.sendMessage(Component.text("Reward: $" + money[i] + " + " + eco[i] + " elite coins", NamedTextColor.GREEN));
        }
        if (!leaderboard.isEmpty()) {
            Player winner = Bukkit.getPlayer(leaderboard.get(0).player);
            if (winner != null) plugin.adv().grant(winner, "comp_winner");
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