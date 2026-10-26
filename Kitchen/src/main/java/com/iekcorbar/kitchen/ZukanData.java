package com.iekcorbar.kitchen;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: every Kitchen item and the cutting board and
 * cooking pot recipes, as plain JDK types. The workbench recipes are normal Bukkit recipes with
 * exact choices, so Zukan reads those itself.
 */
final class ZukanData implements Supplier<Map<String, Object>> {
    private static final int MAX_TAG_ITEMS = 24;
    private final KitchenPlugin plugin;

    ZukanData(KitchenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Map<String, Object> get() {
        List<ItemStack> all = new ArrayList<>();
        for (KItem t : KItem.values()) all.add(plugin.items().create(t, 1));
        all.addAll(plugin.kitchenDecor().allItems());

        List<Map<String, Object>> recipes = new ArrayList<>();
        ItemStack board = plugin.items().create(KItem.CUTTING_BOARD, 1);
        ItemStack pot = plugin.items().create(KItem.COOKING_POT, 1);
        for (Recipes.Cut c : plugin.recipes().cuts) {
            List<ItemStack> in = options(c.input().spec());
            if (in.isEmpty()) continue;
            List<ItemStack> outs = new ArrayList<>();
            List<String> info = new ArrayList<>();
            info.add("Use a knife on the cutting board");
            for (Recipes.Output o : c.outputs()) {
                ItemStack s = output(o, in.get(0));
                if (s == null) continue;
                outs.add(s);
                if (o.chance() < 1) info.add(name(s) + ": " + Math.round(o.chance() * 100) + "% chance");
            }
            if (!outs.isEmpty()) add(recipes, "Cutting Board", board, List.of(in), outs, info);
        }
        for (Recipes.Cook c : plugin.recipes().cooks) {
            List<List<ItemStack>> cells = new ArrayList<>();
            boolean ok = true;
            for (Recipes.Ingredient ing : c.ingredients()) {
                List<ItemStack> o = options(ing.spec());
                if (o.isEmpty()) {
                    ok = false;
                    break;
                }
                cells.add(o);
            }
            if (!ok) continue;
            List<String> info = new ArrayList<>();
            info.add("Cooking pot over a heat source (kamado stove, campfire, lava)");
            info.add("Time: " + String.format(Locale.ROOT, "%.0f", c.ticks() / 20.0) + " s");
            if (c.container() != null) {
                cells.add(List.of(new ItemStack(c.container())));
                info.add("Needs a " + c.container().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " as container");
            }
            ItemStack result = output(c.result(), null);
            if (result != null) add(recipes, "Cooking Pot", pot, cells, List.of(result), info);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", recipes);
        return out;
    }

    private String name(ItemStack s) {
        return s.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private ItemStack output(Recipes.Output o, ItemStack input) {
        if (o.spec().equalsIgnoreCase("stripped")) {
            if (input == null) return null;
            Material s = Material.matchMaterial("STRIPPED_" + input.getType().name());
            return s == null ? null : new ItemStack(s, o.count());
        }
        return plugin.recipes().make(o.spec(), o.count());
    }

    /** The items a spec stands for: kitchen:id, #tag, a material, or several joined by |. Steamworks (sw:) parts are left out. */
    private List<ItemStack> options(String spec) {
        List<ItemStack> l = new ArrayList<>();
        for (String part : spec.split("\\|")) {
            String p = part.trim();
            String low = p.toLowerCase(Locale.ROOT);
            if (low.startsWith("kitchen:")) {
                KItem t = KItem.byId(low.substring(8));
                if (t != null) l.add(plugin.items().create(t, 1));
            } else if (low.startsWith("sw:")) {
                continue;
            } else if (low.startsWith("#")) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(low.substring(1)), Material.class);
                if (tag == null) continue;
                for (Material m : tag.getValues()) {
                    l.add(new ItemStack(m));
                    if (l.size() >= MAX_TAG_ITEMS) break;
                }
            } else {
                Material m = Material.matchMaterial(p);
                if (m != null && m.isItem()) l.add(new ItemStack(m));
            }
        }
        return l;
    }

    private void add(List<Map<String, Object>> out, String category, ItemStack station, List<List<ItemStack>> inputs, List<ItemStack> outputs, List<String> info) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("icon", station);
        m.put("station", station);
        m.put("grid", false);
        m.put("inputs", inputs);
        m.put("outputs", outputs);
        m.put("info", info);
        out.add(m);
    }
}
