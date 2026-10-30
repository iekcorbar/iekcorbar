package com.iekcorbar.sakagura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sommelier: an NPC-like station that evaluates drinks and gives feedback.
 * Players right-click the sommelier with a drink in hand to get a quality assessment.
 * High-quality drinks earn rewards; the sommelier tracks tasting history.
 */
final class Sommelier implements Listener {
    private final Sakagura plugin;
    private final File file;
    private final Map<UUID, TastingRecord> tastings = new HashMap<>();

    static final class TastingRecord {
        int totalTasted;
        int bestQuality;
        String bestDrink;
        long lastTasting;

        TastingRecord() {
            totalTasted = 0;
            bestQuality = 0;
            bestDrink = "none";
            lastTasting = 0;
        }
    }

    Sommelier(Sakagura plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sommelier.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                TastingRecord rec = new TastingRecord();
                rec.totalTasted = data.getInt(key + ".total", 0);
                rec.bestQuality = data.getInt(key + ".best", 0);
                rec.bestDrink = data.getString(key + ".drink", "none");
                rec.lastTasting = data.getLong(key + ".last", 0);
                tastings.put(uuid, rec);
            } catch (Exception ignored) { }
        }
    }

    void save() {
        org.bukkit.configuration.file.YamlConfiguration data = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, TastingRecord> e : tastings.entrySet()) {
            TastingRecord rec = e.getValue();
            String key = e.getKey().toString();
            data.set(key + ".total", rec.totalTasted);
            data.set(key + ".best", rec.bestQuality);
            data.set(key + ".drink", rec.bestDrink);
            data.set(key + ".last", rec.lastTasting);
        }
        try {
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save sommelier.yml: " + e.getMessage());
        }
    }

    /** Evaluate a drink and give feedback. Returns the feedback message. */
    String evaluate(Player p, org.bukkit.inventory.ItemStack drink) {
        Brew b = plugin.goods().brewOf(drink);
        if (b == null) return null; // not a drink

        Recipes.Match m = plugin.recipes().match(b);
        if (m == null || !m.finished()) return "unfinished";

        String drinkName = m.recipe().names.isEmpty() ? m.recipe().slug : m.recipe().names.get(0);
        int quality = m.quality();
        int difficulty = m.recipe().difficulty;

        // track tasting
        TastingRecord rec = tastings.computeIfAbsent(p.getUniqueId(), k -> new TastingRecord());
        rec.totalTasted++;
        rec.lastTasting = System.currentTimeMillis();
        if (quality > rec.bestQuality) {
            rec.bestQuality = quality;
            rec.bestDrink = drinkName;
        }

        // feedback based on quality
        String feedback;
        NamedTextColor color;
        if (quality >= 10) {
            feedback = "Magnificent! This is a masterpiece. The balance is impeccable - every note sings in harmony.";
            color = NamedTextColor.GOLD;
            // reward for perfect
            BukkitReward(p, 100, 5);
        } else if (quality >= 8) {
            feedback = "Excellent work! A refined drink with great character. You clearly know what you're doing.";
            color = NamedTextColor.GREEN;
            BukkitReward(p, 50, 2);
        } else if (quality >= 6) {
            feedback = "Good, but not great. The flavors are there, but the balance could be improved. Try adjusting the timing.";
            color = NamedTextColor.YELLOW;
            BukkitReward(p, 20, 1);
        } else if (quality >= 4) {
            feedback = "Decent effort. The drink is drinkable but lacks refinement. Focus on following the recipe more precisely.";
            color = NamedTextColor.WHITE;
        } else if (quality >= 2) {
            feedback = "This needs work. The flavors are muddled and the balance is off. Practice makes perfect!";
            color = NamedTextColor.GRAY;
        } else {
            feedback = "I'm sorry, but this is... not good. The recipe was not followed. Start over and pay attention to the steps.";
            color = NamedTextColor.RED;
        }

        // additional feedback based on specific issues
        StringBuilder extra = new StringBuilder();
        if (b.cook > 0 && m.score() < 0.8) {
            extra.append(" The cooking time seems off.");
        }
        if (b.distills < m.recipe().steps.stream().filter(s -> s.type() == 'D').mapToInt(s -> s.runs()).sum() && m.recipe().steps.stream().anyMatch(s -> s.type() == 'D')) {
            extra.append(" It could use more distillation.");
        }
        if (b.years < 0.5 && m.recipe().steps.stream().anyMatch(s -> s.type() == 'A')) {
            extra.append(" Aging would improve this significantly.");
        }

        // send messages
        p.sendMessage(Component.text("Sommelier: ", NamedTextColor.DARK_PURPLE).append(Component.text("\"" + feedback + extra + "\"", color)));
        p.sendMessage(Component.text("Drink: " + drinkName + " | Quality: " + quality + "/10 | Difficulty: " + difficulty, NamedTextColor.GRAY));

        if (quality >= 8) {
            p.sendMessage(Component.text("Reward: $" + (quality >= 10 ? 100 : 50) + " + " + (quality >= 10 ? 5 : 2) + " elite coins", NamedTextColor.GREEN));
        }

        // achievement for perfect tasting
        if (quality >= 10) {
            plugin.adv().grant(p, "sommelier_perfect");
        }

        save();
        return feedback;
    }

    private void BukkitReward(Player p, int money, int eco) {
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "cmi money give " + p.getName() + " " + money);
        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "em money add " + p.getName() + " " + eco);
    }

    /** Get tasting stats for a player. */
    TastingRecord getRecord(UUID uuid) {
        return tastings.getOrDefault(uuid, new TastingRecord());
    }

    /** Format tasting stats. */
    String stats(Player p) {
        TastingRecord rec = getRecord(p.getUniqueId());
        return "Tastings: " + rec.totalTasted + " | Best: " + rec.bestQuality + "/10 (" + rec.bestDrink + ")";
    }
}
