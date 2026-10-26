package com.iekcorbar.additions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: every Additions prop. Their workbench
 * recipes are normal Bukkit recipes, which Zukan reads itself. Zukan sees the Steamworks
 * dependency in plugin.yml and lists these items under @steamworks as add-on content.
 */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final Additions additions;

    ZukanData(Additions additions) {
        this.additions = additions;
    }

    @Override
    public Map<String, Object> get() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", new ArrayList<>(additions.decor.allItems()));
        out.put("recipes", new ArrayList<>());
        return out;
    }
}
