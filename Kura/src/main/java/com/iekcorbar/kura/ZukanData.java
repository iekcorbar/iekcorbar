package com.iekcorbar.kura;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: every Kura item. The workbench recipes are normal
 * Bukkit recipes with exact choices, which Zukan reads itself.
 */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final KuraPlugin plugin;

    ZukanData(KuraPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> get() {
        List<ItemStack> all = new ArrayList<>();
        for (KItem t : KItem.values()) if (!t.legacy()) all.add(plugin.items().create(t, 1));  // 0.1.11: no recipes for the old parts
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", new ArrayList<>());
        return out;
    }
}
