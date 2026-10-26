package com.iekcorbar.relics;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: every relic prop and every treat. Their workbench
 * recipes are normal Bukkit recipes, which Zukan reads itself; relics without a recipe are handed out
 * with /relics give.
 */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final RelicsPlugin plugin;
    private final Relics relics;

    ZukanData(RelicsPlugin plugin, Relics relics) {
        this.plugin = plugin;
        this.relics = relics;
    }

    @Override
    public Map<String, Object> get() {
        List<ItemStack> all = new ArrayList<>();
        for (RelicType t : RelicType.values()) all.add(relics.item(t, 1));
        for (Treats.Treat t : Treats.Treat.values()) all.add(plugin.treats().item(t, 1));
        if (plugin.discs() != null) all.addAll(plugin.discs().items());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", new ArrayList<>());
        return out;
    }
}
