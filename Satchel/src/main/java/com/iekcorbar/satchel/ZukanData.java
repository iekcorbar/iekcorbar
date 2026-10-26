package com.iekcorbar.satchel;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** What the recipe viewer (Zukan plugin) asks for: every item plus the real crafting recipes (the workbench ones use generic paper slots). */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final SatchelPlugin plugin;

    ZukanData(SatchelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> get() {
        List<ItemStack> all = new ArrayList<>();
        for (SItem t : SItem.values()) all.add(plugin.items().create(t, 1));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", plugin.items().viewerRecipes());
        out.put("replaceCrafting", true);
        return out;
    }
}
