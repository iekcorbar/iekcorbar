package com.iekcorbar.sakagura;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Shared crops (11-11). Nine crops grow in Kitchen's fields AND in our garden: chili, daikon,
 * ginger, persimmon, rice, shiso, tomato, wasabi, yuzu. The owner asked for ONE item per crop
 * ("nu 2x daikons") - Kitchen owns the canonical item (it has the full food/slices/crate set),
 * and our garden drops that same item through Kitchen's public sharedItem() bridge. Kitchen's
 * recipes already read these keys (see Ingredients), so the crop is instantly usable everywhere.
 * When Kitchen is not installed the garden falls back to our own copies, so Sakagura stays
 * standalone-safe.
 */
final class KitchenShared {
    /** sakagura sid -> the kitchen produce id it now shares, for the nine twin crops. */
    static final Map<String, String> TWIN = Map.of(
            "daikon", "daikon", "chili", "chili", "rice", "rice", "tomato", "tomato", "ginger", "ginger",
            "wasabi", "wasabi_root", "shiso", "shiso_leaves", "yuzu", "yuzu", "persimmon", "persimmon");

    private static final Map<String, String> BACK = TWIN.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    /** Kitchen's PDC key, read the same way Ingredients reads it. */
    static final NamespacedKey KEY = new NamespacedKey("kitchen", "item");

    private static Method sharedItem;
    private static boolean looked;

    private KitchenShared() {
    }

    /** A fresh canonical Kitchen stack for our sid, or null (no twin / Kitchen absent -> caller falls back). */
    static ItemStack get(String sakaguraSid, int amount) {
        String kId = TWIN.get(sakaguraSid);
        if (kId == null) return null;
        Method m = method();
        if (m == null) return null;
        try {
            Plugin kitchen = Bukkit.getPluginManager().getPlugin("Kitchen");
            if (kitchen == null || !kitchen.isEnabled()) return null;
            ItemStack it = (ItemStack) m.invoke(kitchen, kId, Math.max(1, amount));
            if (it != null) return it;
        } catch (Exception ignored) {
        }
        return null;                                        // Kitchen gave nothing: fall back, never drop air
    }

    /** The raw Kitchen id on a stack ("daikon"), or null. */
    static String idOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    /** The sakagura Fruit a Kitchen-id belongs to ("wasabi_root" -> WASABI), or null. */
    static Fruit fruitOfKitchenId(String kitchenId) {
        String sid = kitchenId == null ? null : BACK.get(kitchenId);
        return sid == null ? null : Fruit.byId(sid);
    }

    /** Our own sid for a Kitchen twin stack ("wasabi_root" -> "wasabi"), or null. Lets every
     *  idOf()-based check (achievements, take-commands, planting) keep seeing shared crops. */
    static String sidOf(ItemStack it) {
        String k = idOf(it);
        return k == null ? null : BACK.get(k);
    }

    private static synchronized Method method() {
        if (looked) return sharedItem;
        looked = true;
        try {
            Plugin kitchen = Bukkit.getPluginManager().getPlugin("Kitchen");
            if (kitchen == null) return null;
            Class<?> kl = Class.forName("com.iekcorbar.kitchen.KitchenPlugin", true, kitchen.getClass().getClassLoader());
            sharedItem = kl.getMethod("sharedItem", String.class, int.class);
        } catch (Throwable ignored) {                                // Kitchen missing/old: fallback mode
        }
        return sharedItem;
    }
}
