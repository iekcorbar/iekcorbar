package com.iekcorbar.sakagura;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Food spoilage for Sakagura: fresh fruit decays over real time.
 * Spoilage is tracked per ItemStack via a PDC timestamp.
 */
final class Spoilage {
    private final Sakagura plugin;
    private final NamespacedKey key;

    /** Minutes until spoilage. -1 = never spoils. */
    private static final Map<String, Integer> SPOIL_MINUTES = new HashMap<>();
    static {
        // Fresh fruit spoils relatively fast
        SPOIL_MINUTES.put("grape", 45);
        SPOIL_MINUTES.put("peach", 30);
        SPOIL_MINUTES.put("cherry", 20);
        SPOIL_MINUTES.put("strawberry", 20);
        SPOIL_MINUTES.put("blueberry", 30);
        SPOIL_MINUTES.put("yuzu", 60);
        SPOIL_MINUTES.put("ume", 60);
        SPOIL_MINUTES.put("apple", 90);
        SPOIL_MINUTES.put("pear", 60);
        SPOIL_MINUTES.put("apricot", 30);
        SPOIL_MINUTES.put("mikan", 45);
        SPOIL_MINUTES.put("kiwi", 30);
        SPOIL_MINUTES.put("mango", 30);
        SPOIL_MINUTES.put("papaya", 20);
        SPOIL_MINUTES.put("guava", 30);
        SPOIL_MINUTES.put("mangosteen", 20);
        SPOIL_MINUTES.put("dragonfruit", 30);
        SPOIL_MINUTES.put("rambutan", 20);
        SPOIL_MINUTES.put("longan", 20);
        SPOIL_MINUTES.put("sudachi", 60);
        // Grains and nuts last longer
        SPOIL_MINUTES.put("barley", 180);
        SPOIL_MINUTES.put("hops", 60);
        SPOIL_MINUTES.put("rice", 120);
        SPOIL_MINUTES.put("rye", 180);
        SPOIL_MINUTES.put("maize", 90);
        SPOIL_MINUTES.put("tomato", 30);
        SPOIL_MINUTES.put("raspberry", 20);
        SPOIL_MINUTES.put("blackcurrant", 30);
        SPOIL_MINUTES.put("elderberry", 30);
        SPOIL_MINUTES.put("cranberry", 60);
        SPOIL_MINUTES.put("gooseberry", 30);
        SPOIL_MINUTES.put("mulberry", 20);
        SPOIL_MINUTES.put("persimmon", 60);
        SPOIL_MINUTES.put("loquat", 30);
        SPOIL_MINUTES.put("pomegranate", 90);
        SPOIL_MINUTES.put("fig", 30);
        SPOIL_MINUTES.put("quince", 90);
        SPOIL_MINUTES.put("olive", 120);
        SPOIL_MINUTES.put("date", 90);
        SPOIL_MINUTES.put("lychee", 20);
        SPOIL_MINUTES.put("passionfruit", 20);
        SPOIL_MINUTES.put("vanilla", 180);
        SPOIL_MINUTES.put("ginger", 120);
    }

    static int spoilMinutes(String id) {
        return SPOIL_MINUTES.getOrDefault(id, -1);
    }

    Spoilage(Sakagura plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "spoil_at");
    }

    /** Stamps a food item with a spoilage timestamp if it has a spoil time. */
    void stamp(ItemStack item, String id) {
        if (item == null || item.isEmpty()) return;
        int mins = spoilMinutes(id);
        if (mins < 0) return;
        // rounded up to 5-minute steps so fruit from one harvest still stacks
        long step = 5L * 60 * 1000;
        long spoilAt = (System.currentTimeMillis() + (long) mins * 60 * 1000 + step - 1) / step * step;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, spoilAt);
        item.setItemMeta(meta);
    }

    /** Checks if an item has spoiled. */
    boolean spoiled(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        Long spoilAt = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.LONG);
        if (spoilAt == null) return false;
        return System.currentTimeMillis() > spoilAt;
    }

    /** Periodic check: replace spoiled food in all online players' inventories. */
    void tick() {
        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
            org.bukkit.inventory.ItemStack[] contents = p.getInventory().getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                org.bukkit.inventory.ItemStack it = contents[i];
                if (it == null || it.isEmpty() || !it.hasItemMeta()) continue;
                if (spoiled(it)) {
                    p.getInventory().setItem(i, null);
                    changed = true;
                    p.sendActionBar(net.kyori.adventure.text.Component.text("A fruit item spoiled!", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
            if (changed) p.updateInventory();
        }
    }
}