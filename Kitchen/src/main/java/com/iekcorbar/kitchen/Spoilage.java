package com.iekcorbar.kitchen;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Food spoilage: cooked meals and fresh produce decay over real time.
 * Preserved items (smoked, dried, pickled, fermented) last much longer or
 * never spoil. Spoilage is tracked per ItemStack via a PDC timestamp.
 */
final class Spoilage {
    private final KitchenPlugin plugin;
    private final NamespacedKey key;

    /** Minutes until spoilage. -1 = never spoils. */
    private static final Map<KItem, Integer> SPOIL_MINUTES = new HashMap<>();
    static {
        // fresh produce spoils fast
        SPOIL_MINUTES.put(KItem.TOMATO, 30);
        SPOIL_MINUTES.put(KItem.CHILI, 30);
        SPOIL_MINUTES.put(KItem.NAPA_CABBAGE, 45);
        SPOIL_MINUTES.put(KItem.GREEN_ONION, 45);
        SPOIL_MINUTES.put(KItem.DAIKON, 60);
        SPOIL_MINUTES.put(KItem.CORN, 60);
        SPOIL_MINUTES.put(KItem.MIZUNA, 30);
        SPOIL_MINUTES.put(KItem.EGGPLANT, 45);
        SPOIL_MINUTES.put(KItem.BAMBOO_SHOOTS, 45);
        SPOIL_MINUTES.put(KItem.SHIITAKE, 30);
        SPOIL_MINUTES.put(KItem.ENOKI, 20);
        SPOIL_MINUTES.put(KItem.SALMON_SASHIMI, 15);
        SPOIL_MINUTES.put(KItem.TUNA_SASHIMI, 15);
        SPOIL_MINUTES.put(KItem.COD_SLICE, 15);
        SPOIL_MINUTES.put(KItem.FUGU_SASHIMI, 15);
        // cooked meals spoil moderately
        SPOIL_MINUTES.put(KItem.STEAMED_RICE, 60);
        SPOIL_MINUTES.put(KItem.EDAMAME, 45);
        SPOIL_MINUTES.put(KItem.TOFU, 90);
        SPOIL_MINUTES.put(KItem.MOCHI, 120);
        SPOIL_MINUTES.put(KItem.TAMAGOYAKI, 60);
        SPOIL_MINUTES.put(KItem.ONIGIRI, 45);
        SPOIL_MINUTES.put(KItem.SALMON_NIGIRI, 30);
        SPOIL_MINUTES.put(KItem.TUNA_NIGIRI, 30);
        SPOIL_MINUTES.put(KItem.MAKI_ROLL, 30);
        SPOIL_MINUTES.put(KItem.MISO_SOUP, 30);
        SPOIL_MINUTES.put(KItem.RAMEN, 45);
        SPOIL_MINUTES.put(KItem.GYUDON, 45);
        SPOIL_MINUTES.put(KItem.OYAKODON, 45);
        SPOIL_MINUTES.put(KItem.ODEN, 60);
        SPOIL_MINUTES.put(KItem.NABE, 60);
        SPOIL_MINUTES.put(KItem.TEMPURA, 30);
        SPOIL_MINUTES.put(KItem.TAKUAN, 180); // pickled lasts longer
        SPOIL_MINUTES.put(KItem.DANGO, 90);
        SPOIL_MINUTES.put(KItem.BENTO_BOX, 60);
        SPOIL_MINUTES.put(KItem.SUSHI_PLATTER, 30);
        SPOIL_MINUTES.put(KItem.TEA_SET, 30);
        SPOIL_MINUTES.put(KItem.RICE_BREAD, 90);
        SPOIL_MINUTES.put(KItem.MELONPAN, 60);
        SPOIL_MINUTES.put(KItem.CASTELLA, 120);
        SPOIL_MINUTES.put(KItem.NIKUMAN, 45);
        SPOIL_MINUTES.put(KItem.ANPAN, 60);
        SPOIL_MINUTES.put(KItem.SHIITAKE, 30);
        SPOIL_MINUTES.put(KItem.ENOKI, 20);
        SPOIL_MINUTES.put(KItem.UDON, 30);
        SPOIL_MINUTES.put(KItem.SOBA, 30);
        SPOIL_MINUTES.put(KItem.RAMEN_NOODLES, 30);
        // preserved items: -1 (never spoil) — smoked, dried, pickled, fermented
        // shiso_leaves, ginger, wasabi_root, mizuna, kabocha, bamboo_shoots, eggplant
        // are already in SPOIL_MINUTES above or default to -1
    }

    static int spoilMinutes(KItem t) {
        return SPOIL_MINUTES.getOrDefault(t, -1);
    }

    Spoilage(KitchenPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "spoil_at");
    }

    /** Stamps a food item with a spoilage timestamp if it has a spoil time. */
    void stamp(ItemStack item) {
        if (item == null || item.isEmpty()) return;
        KItem t = plugin.items().typeOf(item);
        if (t == null) return;
        int mins = spoilMinutes(t);
        if (mins < 0) return; // never spoils
        long spoilAt = System.currentTimeMillis() + (long) mins * 60 * 1000;
        org.bukkit.inventory.meta.ItemMeta m = item.getItemMeta();
        m.getPersistentDataContainer().set(key, PersistentDataType.LONG, spoilAt);
        item.setItemMeta(m); // ONE meta object in and back out: item.getItemMeta() hands out a fresh
                             // copy every call, so mutating one and setting another silently lost
                             // the stamp and nothing ever spoiled (fixed 10-11).
    }

    /** Checks if an item has spoiled. */
    boolean spoiled(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        Long spoilAt = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.LONG);
        if (spoilAt == null) return false; // not tracked (never spoils or not stamped)
        return System.currentTimeMillis() > spoilAt;
    }

    /** Converts a spoiled item to a "spoiled food" marker (just removes it for now). */
    ItemStack spoil(ItemStack item) {
        // For now, just return null (item disappears). In the future, could return a "Spoiled Food" item.
        return null;
    }

    /** Periodic check: replace spoiled food in all online players' inventories. */
    void tick() {
        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
            org.bukkit.inventory.ItemStack[] contents = p.getInventory().getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                org.bukkit.inventory.ItemStack it = contents[i];
                if (it == null || it.isEmpty()) continue;
                if (spoiled(it)) {
                    p.getInventory().setItem(i, null);
                    changed = true;
                    p.sendActionBar(net.kyori.adventure.text.Component.text("A food item spoiled!", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
            if (changed) p.updateInventory();
        }
    }
}