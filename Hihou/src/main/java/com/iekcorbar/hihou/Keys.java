package com.iekcorbar.hihou;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Physical keys, one per chest type. Paper underneath with its own model; the PDC tag holds the type
 * string (treasure/event/vote/lucky/victory) that Chests matches against "<type>_chest". Keys are a
 * single use each: unlocking consumes one. Vote keys are handed out by the EXISTING vote-reward
 * pipeline (set vote_reward_config.command to "ih key %player% vote" - no code in either plugin
 * changes), which is why giving keys works from the console too.
 */
final class Keys {
    static final String[] TYPES = {"treasure", "event", "vote", "lucky", "victory"};

    private final NamespacedKey typeKey;

    Keys(HihouPlugin plugin) {
        typeKey = new NamespacedKey(plugin, "key_type");
    }

    /** The chest type this stack opens, or null when it is not a key. */
    String typeOf(ItemStack it) {
        if (it == null || it.getType() != Material.PAPER || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    ItemStack item(String type, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(cap(type) + " Key", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Opens a " + type + " chest - one use.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Hihou", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        it.setItemMeta(m);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("hihou", type + "_key"));
        it.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        return it;
    }

    static boolean validType(String s) {
        for (String t : TYPES) if (t.equalsIgnoreCase(s)) return true;
        return false;
    }

    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
