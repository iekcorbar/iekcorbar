package com.iekcorbar.kitchen;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Cooking XP: every completed recipe grants XP based on nutrition + saturation.
 * Levels unlock nothing mechanically (yet) but give bragging rights and
 * hidden achievements. Stored in the player's PDC so it survives restarts.
 */
final class CookingXP {
    private final KitchenPlugin plugin;
    private final NamespacedKey key;

    CookingXP(KitchenPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "cooking_xp");
    }

    /** XP needed to reach the next level from the current one. */
    static int xpForLevel(int level) {
        // 0, 50, 150, 300, 500, 800, 1200, 1700, 2300, 3000, ...
        if (level <= 0) return 50;
        return 50 + level * 50 + (level * level * 10);
    }

    /** Total XP needed to reach a given level (cumulative). */
    static int totalXpForLevel(int level) {
        int total = 0;
        for (int i = 0; i < level; i++) total += xpForLevel(i);
        return total;
    }

    /** Max level. */
    static final int MAX_LEVEL = 10;

    int xp(Player p) {
        return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    int level(Player p) {
        int x = xp(p);
        int lvl = 0;
        while (lvl < MAX_LEVEL && x >= totalXpForLevel(lvl + 1)) lvl++;
        return lvl;
    }

    /** Grants XP and returns the new level (or -1 if no level-up). */
    int grant(Player p, int amount) {
        if (amount <= 0) return -1;
        int before = level(p);
        int cur = xp(p) + amount;
        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, cur);
        int after = level(p);
        if (after > before) {
            p.sendTitle("Level " + after + "!", "Cooking skill increased", 10, 60, 20);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f + after * 0.05f);
            p.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            if (after == 5) plugin.adv().grant(p, "xp_5");
            if (after == 10) plugin.adv().grant(p, "xp_10");
            return after;
        }
        return -1;
    }

    String bar(Player p) {
        int lvl = level(p);
        if (lvl >= MAX_LEVEL) return "Level " + lvl + " (max)";
        int cur = xp(p) - totalXpForLevel(lvl);
        int need = xpForLevel(lvl);
        int pct = (int) (100.0 * cur / Math.max(1, need));
        StringBuilder sb = new StringBuilder();
        sb.append("Level ").append(lvl).append(" (").append(cur).append("/").append(need).append(" XP)");
        return sb.toString();
    }
}