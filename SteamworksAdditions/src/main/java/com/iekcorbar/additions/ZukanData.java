package com.iekcorbar.additions;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: every Additions prop AND its
 * workbench recipe (shapeless; the ingredients are vanilla materials, so these
 * map straight onto Zukan's 3x3 grid). Zukan sees the Steamworks dependency in
 * plugin.yml and lists this under @steamworks as add-on content.
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
        List<Map<String, Object>> recipes = new ArrayList<>();
        for (DecorDef d : additions.decor.defs.values()) {
            if (d.recipe.isEmpty()) continue;
            List<List<ItemStack>> cells = new ArrayList<>();
            for (int i = 0; i < 9; i++) cells.add(List.of());
            int at = 0;
            for (Object o : d.recipe) {
                if (at >= 9) break;
                cells.set(at++, choices(o));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", "Steamworks Additions");
            m.put("icon", new ItemStack(Material.CRAFTING_TABLE));
            m.put("station", new ItemStack(Material.CRAFTING_TABLE));
            m.put("grid", true);
            m.put("inputs", cells);
            m.put("outputs", List.of(additions.decor.item(d, d.recipeAmount)));
            m.put("info", List.of("Shapeless"));
            recipes.add(m);
        }
        out.put("recipes", recipes);
        return out;
    }

    private List<ItemStack> choices(Object o) {
        if (o instanceof Material mat) return List.of(new ItemStack(mat));
        if (o instanceof Tag<?> tag) {
            List<ItemStack> l = new ArrayList<>();
            for (Object m : tag.getValues()) l.add(new ItemStack((Material) m));
            return l;
        }
        if (o instanceof RecipeChoice.MaterialChoice mc) {
            List<ItemStack> l = new ArrayList<>();
            for (Material m : mc.getChoices()) l.add(new ItemStack(m));
            return l;
        }
        if (o instanceof RecipeChoice.ExactChoice ec) return new ArrayList<>(ec.getChoices());
        return List.of();
    }
}
