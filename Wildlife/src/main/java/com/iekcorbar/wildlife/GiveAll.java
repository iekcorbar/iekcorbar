package com.iekcorbar.wildlife;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** /giveall: one of every item of the plugin (the same list the recipe viewer shows). Too many for the free slots: shulker boxes. */
final class GiveAll {
    private GiveAll() {
    }

    /** Gives every item of the provider's list to the player; returns how many items there were. */
    static int give(Player t, Supplier<Map<String, Object>> provider, String title) {
        List<ItemStack> items = new ArrayList<>();
        if (provider.get().get("items") instanceof List<?> l) {
            for (Object o : l) if (o instanceof ItemStack s && !s.isEmpty()) items.add(s.asOne());
        }
        int free = 0;
        for (ItemStack s : t.getInventory().getStorageContents()) if (s == null || s.isEmpty()) free++;
        if (items.size() <= free) {
            for (ItemStack s : items) t.getInventory().addItem(s);
            return items.size();
        }
        int boxes = (items.size() + 26) / 27;
        for (int i = 0; i < items.size(); i += 27) {
            ItemStack box = new ItemStack(Material.SHULKER_BOX);
            BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
            ShulkerBox state = (ShulkerBox) meta.getBlockState();
            for (ItemStack s : items.subList(i, Math.min(items.size(), i + 27))) state.getInventory().addItem(s);
            meta.setBlockState(state);
            meta.displayName(Component.text(title + " " + (i / 27 + 1) + "/" + boxes, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            box.setItemMeta(meta);
            for (ItemStack left : t.getInventory().addItem(box).values()) t.getWorld().dropItemNaturally(t.getLocation(), left);
        }
        return items.size();
    }
}
