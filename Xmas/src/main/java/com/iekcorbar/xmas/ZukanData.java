package com.iekcorbar.xmas;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: every Xmas building block. The recipes are normal
 * shapeless Bukkit recipes registered by the shared Decor engine, which Zukan reads itself - so this
 * only has to hand over the items.
 */
final class ZukanData implements Supplier<Map<String, Object>> {

    private final XmasPlugin plugin;

    ZukanData(XmasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> get() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", new ArrayList<>(plugin.decor().allItems()));
        out.put("recipes", new ArrayList<>());
        return out;
    }
}
