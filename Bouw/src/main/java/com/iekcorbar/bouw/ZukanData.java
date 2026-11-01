package com.iekcorbar.bouw;

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
    private final BouwPlugin plugin;
    private final Bouw bouw;

    ZukanData(BouwPlugin plugin) {
        this.plugin = plugin;
        this.bouw = plugin.bouw;
    }

    private List<ItemStack> bouwItems() {
        List<ItemStack> l = new ArrayList<>(bouw.decor.allItems());
        l.addAll(plugin.shapes.allItems());
        return l;
    }

    @Override
    public Map<String, Object> get() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", bouwItems());
        List<Map<String, Object>> recipes = new ArrayList<>();
        for (DecorDef d : bouw.decor.defs.values()) {
            if (d.recipe.isEmpty()) continue;
            List<List<ItemStack>> cells = new ArrayList<>();
            for (int i = 0; i < 9; i++) cells.add(List.of());
            int at = 0;
            for (Object o : d.recipe) {
                if (at >= 9) break;
                cells.set(at++, choices(o));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", "Bouw");
            m.put("icon", new ItemStack(Material.CRAFTING_TABLE));
            m.put("station", new ItemStack(Material.CRAFTING_TABLE));
            m.put("grid", true);
            m.put("inputs", cells);
            m.put("outputs", List.of(bouw.decor.item(d, d.recipeAmount)));
            m.put("info", List.of("Shapeless"));
            recipes.add(m);
        }
        for (org.bukkit.inventory.Recipe r : plugin.shapes.recipes) {
            List<List<ItemStack>> cells = new ArrayList<>();
            for (int i = 0; i < 9; i++) cells.add(List.of());
            ItemStack result;
            if (r instanceof org.bukkit.inventory.ShapedRecipe sr) {
                result = sr.getResult();
                String[] shape = sr.getShape();
                for (int row = 0; row < shape.length; row++)
                    for (int col = 0; col < shape[row].length(); col++) {
                        org.bukkit.inventory.RecipeChoice c = sr.getChoiceMap().get(shape[row].charAt(col));
                        if (c != null) cells.set(row * 3 + col, choiceList(c));
                    }
            } else if (r instanceof org.bukkit.inventory.ShapelessRecipe sl) {
                result = sl.getResult();
                int at = 0;
                for (org.bukkit.inventory.RecipeChoice c : sl.getChoiceList()) if (at < 9) cells.set(at++, choiceList(c));
            } else continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", "Bouw blocks");
            m.put("icon", new ItemStack(Material.CRAFTING_TABLE));
            m.put("station", new ItemStack(Material.CRAFTING_TABLE));
            m.put("grid", true);
            m.put("inputs", cells);
            m.put("outputs", List.of(result));
            m.put("info", List.of(r instanceof org.bukkit.inventory.ShapedRecipe ? "Shaped" : "Shapeless"));
            recipes.add(m);
        }
        out.put("recipes", recipes);
        return out;
    }

    private List<ItemStack> choiceList(org.bukkit.inventory.RecipeChoice c) {
        if (c instanceof org.bukkit.inventory.RecipeChoice.ExactChoice ec) return new ArrayList<>(ec.getChoices());
        if (c instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice mc) return choices(mc);
        return List.of();
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
