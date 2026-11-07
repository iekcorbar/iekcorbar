package com.iekcorbar.hunt;

import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/** Tracks per-player achievement counts per category and grants reward props at milestones. */
public class HuntManager implements Listener {
    private final HuntPlugin plugin;
    private final Adv adv;
    private final NamespacedKey countKey; // hunt:<category>_count

    // Milestones: category -> list of (threshold, reward description)
    public static final Map<String, int[]> MILESTONES = new HashMap<>();
    static {
        MILESTONES.put("wildlife", new int[]{25, 45, 54});
        MILESTONES.put("kitchen", new int[]{25, 50});
        MILESTONES.put("relics", new int[]{25, 50});
        MILESTONES.put("zoo", new int[]{10});
        MILESTONES.put("sakagura", new int[]{25});
        MILESTONES.put("steamworks", new int[]{25});
    }

    public HuntManager(HuntPlugin plugin) {
        this.plugin = plugin;
        this.countKey = new NamespacedKey(plugin, "hunt_count");
        this.adv = new Adv(plugin, "hunt", "Hunt", "Server-wide achievement hunt.", "nether_star", "minecraft:block/gold_block", true);
        for (String cat : MILESTONES.keySet()) {
            for (int m : MILESTONES.get(cat)) {
                String frame = m >= 50 ? "challenge" : m >= 25 ? "goal" : "task";
                adv.add(cat + "_" + m, "root", "nether_star", cat.toUpperCase() + " " + m, "Reach " + m + " " + cat + " achievements.", frame);
            }
        }
        adv.start();
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        Player p = e.getPlayer();
        Advancement adv = e.getAdvancement();
        String ns = adv.getKey().getNamespace();
        String cat = categoryOf(ns);
        if (cat == null) return;

        NamespacedKey key = new NamespacedKey(plugin, "hunt_" + cat);
        int count = p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0) + 1;
        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, count);

        // check milestones
        int[] ms = MILESTONES.get(cat);
        if (ms == null) return;
        for (int m : ms) {
            NamespacedKey claimed = new NamespacedKey(plugin, "hunt_" + cat + "_" + m);
            if (count == m && !p.getPersistentDataContainer().has(claimed, PersistentDataType.BYTE)) {
                p.getPersistentDataContainer().set(claimed, PersistentDataType.BYTE, (byte) 1);
                grantReward(p, cat, m);
            }
        }
    }

    private String categoryOf(String ns) {
        return switch (ns) {
            case "wildlife" -> "wildlife";
            case "kitchen" -> "kitchen";
            case "relics" -> "relics";
            case "zoo" -> "zoo";
            case "sakagura" -> "sakagura";
            case "steamworks", "steamworksadditions" -> "steamworks";
            default -> null;
        };
    }

    private void grantReward(Player p, String cat, int milestone) {
        p.sendMessage(org.bukkit.ChatColor.GOLD + "🏆 Hunt milestone reached! " + cat + " " + milestone + " achievements!");
        // toast via advancement
        adv.grant(p, cat + "_" + milestone);
        // store pending reward
        org.bukkit.NamespacedKey pendingKey = new org.bukkit.NamespacedKey(plugin, "hunt_pending_" + cat + "_" + milestone);
        p.getPersistentDataContainer().set(pendingKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        p.sendMessage(org.bukkit.ChatColor.GRAY + "Reward pending! Use /hunt claim to collect.");
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    public void claimRewards(Player p) {
        int claimed = 0;
        for (String cat : MILESTONES.keySet()) {
            for (int m : MILESTONES.get(cat)) {
                org.bukkit.NamespacedKey claimedKey = new org.bukkit.NamespacedKey(plugin, "hunt_" + cat + "_" + m);
                org.bukkit.NamespacedKey pendingKey = new org.bukkit.NamespacedKey(plugin, "hunt_pending_" + cat + "_" + m);
                if (p.getPersistentDataContainer().has(pendingKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                    // give reward item based on tier
                    org.bukkit.Material rewardMat = m >= 50 ? org.bukkit.Material.GOLD_INGOT
                            : m >= 25 ? org.bukkit.Material.IRON_INGOT
                            : org.bukkit.Material.COPPER_INGOT;
                    org.bukkit.inventory.ItemStack reward = new org.bukkit.inventory.ItemStack(rewardMat);
                    org.bukkit.inventory.meta.ItemMeta meta = reward.getItemMeta();
                    meta.displayName(net.kyori.adventure.text.Component.text(cat.toUpperCase() + " Trophy (" + m + ")", net.kyori.adventure.text.format.NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                    meta.lore(java.util.List.of(
                            net.kyori.adventure.text.Component.text("Hunt milestone: " + cat + " " + m + " achievements", net.kyori.adventure.text.format.NamedTextColor.GRAY),
                            net.kyori.adventure.text.Component.text("Place it as a trophy!", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)));
                    reward.setItemMeta(meta);
                    p.getInventory().addItem(reward);
                    p.getPersistentDataContainer().remove(pendingKey);
                    claimed++;
                }
            }
        }
        if (claimed == 0) {
            p.sendMessage(org.bukkit.ChatColor.GRAY + "No pending rewards. Keep earning achievements!");
        } else {
            p.sendMessage(org.bukkit.ChatColor.GREEN + "Claimed " + claimed + " reward(s)!");
        }
    }

    public void save() {
        // PDC is auto-saved by Bukkit
    }
}