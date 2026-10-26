package com.iekcorbar.wildlife;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** What the recipe viewer (Zukan plugin) asks for: the Wildlife items. The Field Journal recipe is a normal Bukkit recipe. */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final WildlifePlugin plugin;

    ZukanData(WildlifePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> get() {
        List<ItemStack> all = new ArrayList<>();
        all.add(plugin.items().journal());
        all.add(plugin.items().crabClaw());
        all.add(plugin.items().lizardTail());
        for (Species s : Species.values()) all.add(plugin.items().egg(s));
        all.addAll(plugin.zoo().allItems());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", new ArrayList<>());
        return out;
    }
}
