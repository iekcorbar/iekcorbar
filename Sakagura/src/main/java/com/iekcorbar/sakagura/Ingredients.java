package com.iekcorbar.sakagura;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Ingredient identity. Keys: "mc:SUGAR" (vanilla item), "kitchen:rice" (Kitchen item),
 * "sakagura:barley" (our crops), "sw:flour" (Steamworks material), "relics:chocolate_heart"
 * (a Relics treat), "brew:gin" (a finished drink of that recipe). Other plugins' items are
 * recognised by their item data only.
 */
final class Ingredients {
    static final NamespacedKey KITCHEN = new NamespacedKey("kitchen", "item");
    static final NamespacedKey STEAMWORKS = new NamespacedKey("steamworks", "material");
    static final NamespacedKey RELICS_TREAT = new NamespacedKey("relics", "treat");
    static final NamespacedKey WILDLIFE = new NamespacedKey("wildlife", "drop");
    static NamespacedKey ours, brewKey;

    private Ingredients() {
    }

    /** Key for a recipe token ("Sugar", "Milk Bucket", "kitchen:rice", "Gin"). Unknown names become "name:..." for later. */
    static String key(String token) {
        String t = token.trim();
        String low = t.toLowerCase(Locale.ROOT);
        if (low.startsWith("kitchen:") || low.startsWith("sakagura:") || low.startsWith("sw:") || low.startsWith("relics:") || low.startsWith("wildlife:")) return low;
        if (low.equals("grass")) return "mc:SHORT_GRASS";
        Material m = Material.matchMaterial(t.replace(' ', '_'));
        if (m != null) return "mc:" + m.name();
        return "name:" + low;
    }

    /** Key of an item put into a cauldron, or null if it isn't an ingredient at all. */
    static String of(ItemStack it, Sakagura plugin) {
        if (it == null || it.isEmpty()) return null;
        if (it.hasItemMeta()) {
            PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
            String brew = pdc.get(brewKey, PersistentDataType.STRING);
            if (brew != null) {
                Recipe r = plugin.recipes().finished(Brew.parse(brew));
                return r == null ? null : "brew:" + r.slug;
            }
            String id = pdc.get(ours, PersistentDataType.STRING);
            if (id != null) return "sakagura:" + id;
            id = pdc.get(KITCHEN, PersistentDataType.STRING);
            if (id != null) return "kitchen:" + id;
            id = pdc.get(STEAMWORKS, PersistentDataType.STRING);
            if (id != null) return "sw:" + id;
            id = pdc.get(RELICS_TREAT, PersistentDataType.STRING);
            if (id != null) return "relics:" + id;   // a Relics treat as garnish: seasonal brews only
            id = pdc.get(WILDLIFE, PersistentDataType.STRING);
            if (id != null) return "wildlife:" + id;   // an animal material (koi scale ...)
            if (pdc.getKeys().stream().anyMatch(k -> !k.getNamespace().equals("minecraft"))) return null; // some other plugin's item
        }
        return "mc:" + it.getType().name();
    }

    /** Readable name for a key (for lore and messages). */
    static String label(String key, Sakagura plugin) {
        int c = key.indexOf(':');
        String id = key.substring(c + 1);
        if (key.startsWith("brew:")) {
            Recipe r = plugin.recipes().get(id);
            return r == null ? id : r.name(5);
        }
        if (key.startsWith("sakagura:")) {
            Fruit f = Fruit.byId(id);
            if (f != null) return f.displayName;
        }
        String s = id.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
