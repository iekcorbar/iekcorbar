package com.iekcorbar.kura;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Per-player favourites: a double-click in a terminal pins an item, and pinned kinds
 * always sort to the top of the list (a ★ marks them). Stored in the player's own data,
 * one comma-separated string, keyed by {@code MATERIAL|namespace} so it survives relogs
 * and treats all variants of one kind as one favourite (like the poly filter does).
 */
final class Favourites {
    private final NamespacedKey key;

    Favourites(NamespacedKey key) {
        this.key = key;
    }

    /** The stable id of a kind: material name + item model namespace (kitchen, steamworks... or minecraft). */
    static String id(ItemStack it) {
        return it.getType().name().toUpperCase(Locale.ROOT) + "|" + TerminalMenu.namespace(it);
    }

    Set<String> read(Player p) {
        String s = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String f : s.split(",")) if (!f.isBlank()) out.add(f);
        return out;
    }

    private void write(Player p, Set<String> set) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (set.isEmpty()) pdc.remove(key);
        else pdc.set(key, PersistentDataType.STRING, String.join(",", set));
    }

    /** Pin or unpin; returns true when the item is now a favourite. Bounded at 64 kinds. */
    boolean toggle(Player p, String id) {
        Set<String> set = read(p);
        if (set.contains(id)) {
            set.remove(id);
            write(p, set);
            return false;
        }
        if (set.size() >= 64) return false;
        set.add(id);
        write(p, set);
        return true;
    }
}
