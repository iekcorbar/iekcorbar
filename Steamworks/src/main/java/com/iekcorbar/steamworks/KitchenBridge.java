package com.iekcorbar.steamworks;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.function.BiFunction;

/**
 * Kitchen items in machines.yml ({@code kitchen:<id>}) without a compile
 * dependency: Kitchen items carry their id in {@code kitchen:item}, and
 * Kitchen registers a {@code BiFunction<String, Integer, ItemStack>} service
 * that makes them. Kitchen loads first (our softdepend), so recipes can be
 * checked when machines.yml is read.
 */
final class KitchenBridge {
    private static final NamespacedKey KEY = new NamespacedKey("kitchen", "item");

    private KitchenBridge() {
    }

    /** The Kitchen id of an item, or null. */
    static String idOf(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    /** A Kitchen item, or null when Kitchen isn't installed or the id is unknown. */
    @SuppressWarnings("unchecked")
    static ItemStack create(String id, int amount) {
        for (RegisteredServiceProvider<?> r : Bukkit.getServicesManager().getRegistrations(BiFunction.class)) {
            if (!r.getPlugin().getName().equals("Kitchen")) continue;
            Object made = ((BiFunction<String, Integer, ItemStack>) r.getProvider()).apply(id, amount);
            if (made instanceof ItemStack it) return it;
        }
        return null;
    }
}
