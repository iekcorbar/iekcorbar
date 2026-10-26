package com.iekcorbar.sakagura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: garden produce and seeds, casks, every drink (as a
 * top-quality bottle) with its steps, and what each crop yields. The cask and seed workbench recipes
 * are normal Bukkit recipes, which Zukan reads itself.
 */
final class ZukanData implements Supplier<Map<String, Object>> {
    private final Sakagura plugin;

    ZukanData(Sakagura plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get() {
        Goods goods = plugin.goods();
        List<ItemStack> all = new ArrayList<>();
        for (Fruit f : Fruit.values()) {
            all.add(goods.item(f.produce, 1));
            if (f.seed != null) all.add(goods.item(f.seed, 1));
        }
        for (Woods w : Woods.values()) all.add(goods.cask(w, 1));
        all.add(goods.still(1));
        all.addAll(plugin.gardenDecor().allItems());

        BiFunction<String, Integer, ItemStack> kitchen = null;
        for (RegisteredServiceProvider<?> r : Bukkit.getServicesManager().getRegistrations(BiFunction.class)) {
            if (r.getPlugin().getName().equals("Kitchen")) kitchen = (BiFunction<String, Integer, ItemStack>) r.getProvider();
        }

        List<Map<String, Object>> recipes = new ArrayList<>();
        ItemStack cauldron = new ItemStack(Material.CAULDRON);
        for (Recipe r : plugin.recipes().all()) {
            ItemStack bottle = goods.bottle(perfect(r), 1);
            all.add(bottle);
            List<List<ItemStack>> inputs = new ArrayList<>();
            List<String> info = new ArrayList<>();
            int n = 0;
            for (Recipe.Step st : r.steps) {
                n++;
                for (Map.Entry<String, Integer> e : st.ingredients().entrySet()) {
                    ItemStack it = ingredient(e.getKey(), e.getValue(), kitchen);
                    if (it != null) inputs.add(List.of(it));
                }
                info.add(n + ". " + describe(st));
            }
            if (inputs.isEmpty()) continue;
            info.add("Difficulty " + r.difficulty + " of 10" + (r.alcohol > 0 ? ", " + r.alcohol + "% alcohol" : ""));
            add(recipes, "Brewing (Sakagura)", cauldron, cauldron, inputs, List.of(bottle), info);
        }

        ItemStack farmland = new ItemStack(Material.FARMLAND);
        for (Fruit f : Fruit.values()) {
            ItemStack plant = goods.item(f.plantItem(), 1);
            ItemStack yield = goods.item(f.produce, Math.max(1, f.maxDrop));
            List<String> info = new ArrayList<>();
            info.add("Grows in about " + f.minutes + " min");
            info.add("Gives " + f.minDrop + "-" + f.maxDrop + " when picked");
            info.add(f.regrows ? "Picking by hand lets it grow back" : "Dug up when picked, replant it");
            info.add("Found growing wild in: " + String.join(", ", f.wildBiomes));
            add(recipes, "Garden", farmland, farmland, List.of(List.of(plant)), List.of(yield), info);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", recipes);
        return out;
    }

    /** The finished, top-quality brew of a recipe (same way the offline recipe test builds one). */
    private Brew perfect(Recipe r) {
        Brew b = new Brew();
        b.ingredients.putAll(r.first().ingredients());
        for (Recipe.Step st : r.steps) {
            switch (st.type()) {
                case 'C' -> b.cook = st.time();
                case 'M' -> b.mix = st.time();
                case 'D' -> b.distills = st.runs();
                case 'A' -> {
                    b.years = st.years();
                    b.wood = st.wood().equals("any") ? "oak" : st.wood();
                }
                default -> { }
            }
            b.step(st.type());
        }
        return b;
    }

    private static String describe(Recipe.Step st) {
        return switch (st.type()) {
            case 'C' -> "Cook " + Math.round(st.time()) + " min in a cauldron over a fire";
            case 'M' -> "Mix " + Math.round(st.time()) + " min in a cauldron without a fire";
            case 'D' -> "Distill " + st.runs() + "x in a Steam Still (glowstone inside, heat below)";
            case 'A' -> "Age " + Math.round(st.years()) + " year" + (Math.round(st.years()) == 1 ? "" : "s") + " in a cask"
                    + (st.wood().equals("any") ? "" : " of " + st.wood().replace('_', ' '));
            default -> "?";
        };
    }

    private ItemStack ingredient(String key, int count, BiFunction<String, Integer, ItemStack> kitchen) {
        int c = key.indexOf(':');
        String ns = key.substring(0, c), id = key.substring(c + 1);
        switch (ns) {
            case "mc" -> {
                Material m = Material.matchMaterial(id);
                return m == null || !m.isItem() ? null : new ItemStack(m, count);
            }
            case "sakagura" -> {
                return plugin.goods().item(id, count);
            }
            case "kitchen" -> {
                ItemStack k = kitchen == null ? null : kitchen.apply(id, count);
                return k != null ? k : named(Material.PAPER, id, count);
            }
            case "brew" -> {
                Recipe r = plugin.recipes().get(id);
                return r == null ? null : plugin.goods().bottle(perfect(r), count);
            }
            default -> {
                // sw: (a Steamworks material) and anything else: a labelled stand-in
                return named(Material.PAPER, Ingredients.label(key, plugin), count);
            }
        }
    }

    private static ItemStack named(Material m, String name, int count) {
        ItemStack it = new ItemStack(m, count);
        ItemMeta meta = it.getItemMeta();
        String label = name.replace('_', ' ');
        meta.displayName(Component.text(Character.toUpperCase(label.charAt(0)) + label.substring(1), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    private void add(List<Map<String, Object>> out, String category, ItemStack icon, ItemStack station, List<List<ItemStack>> inputs, List<ItemStack> outputs, List<String> info) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("icon", icon);
        m.put("station", station);
        m.put("grid", false);
        m.put("inputs", inputs);
        m.put("outputs", outputs);
        m.put("info", info);
        out.add(m);
    }
}
