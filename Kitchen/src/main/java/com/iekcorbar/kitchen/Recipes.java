package com.iekcorbar.kitchen;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Cutting board and cooking pot recipes from kitchen.yml. Item specs:
 * {@code kitchen:<id>}, {@code sw:<id>} (a Steamworks material, matched by its
 * item data - no dependency), {@code #<tag>} (a vanilla item tag, e.g.
 * {@code #logs}), a vanilla material name, or several joined by {@code |}.
 * Outputs may end in {@code x<count>} and/or {@code <chance>%}.
 */
final class Recipes {
    record Ingredient(String spec, Predicate<ItemStack> test) {}

    record Output(String spec, int count, double chance) {}

    record Cut(String id, Ingredient input, List<Output> outputs) {}

    record Cook(String id, List<Ingredient> ingredients, Material container, Output result, int ticks, String season, boolean coop) {}

    private final KitchenPlugin plugin;
    final List<Cut> cuts = new ArrayList<>();
    final List<Cook> cooks = new ArrayList<>();
    private final List<CampfireRecipe> grill = new ArrayList<>();

    Recipes(KitchenPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        File f = new File(plugin.getDataFolder(), "kitchen.yml");
        YamlConfiguration server = YamlConfiguration.loadConfiguration(f);
        YamlConfiguration builtin = YamlConfiguration.loadConfiguration(
                new InputStreamReader(plugin.getResource("kitchen.yml"), StandardCharsets.UTF_8));
        cuts.clear();
        cooks.clear();
        int defaultSeconds = plugin.getConfig().getInt("cooking-pot.default-seconds", 10);
        // a section missing from an older server copy falls back to the built-in one
        ConfigurationSection cut = server.isConfigurationSection("cutting") ? server.getConfigurationSection("cutting") : builtin.getConfigurationSection("cutting");
        ConfigurationSection cook = server.isConfigurationSection("cooking") ? server.getConfigurationSection("cooking") : builtin.getConfigurationSection("cooking");
        if (cut != null) {
            for (String id : cut.getKeys(false)) {
                ConfigurationSection s = cut.getConfigurationSection(id);
                try {
                    List<Output> outs = new ArrayList<>();
                    for (String o : s.getStringList("output")) outs.add(output(o));
                    cuts.add(new Cut(id, ingredient(s.getString("input")), outs));
                } catch (Exception e) {
                    plugin.getLogger().warning("kitchen.yml: skipping cutting recipe " + id + " (" + e.getMessage() + ")");
                }
            }
        }
        if (cook != null) {
            for (String id : cook.getKeys(false)) {
                ConfigurationSection s = cook.getConfigurationSection(id);
                try {
                    List<Ingredient> ins = new ArrayList<>();
                    for (String i : s.getStringList("ingredients")) ins.add(ingredient(i));
                    if (ins.isEmpty() || ins.size() > 6) throw new IllegalArgumentException("needs 1-6 ingredients");
                    String c = s.getString("container");
                    Material container = c == null || c.equalsIgnoreCase("none") ? null : Material.valueOf(c.toUpperCase(Locale.ROOT));
                    cooks.add(new Cook(id, ins, container, output(s.getString("result")), 20 * s.getInt("seconds", defaultSeconds), s.getString("season", "all"), s.getBoolean("coop", false)));
                } catch (Exception e) {
                    plugin.getLogger().warning("kitchen.yml: skipping cooking recipe " + id + " (" + e.getMessage() + ")");
                }
            }
        }
        grill.clear();
        for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
            if (it.next() instanceof CampfireRecipe r) grill.add(r);
        }
        plugin.getLogger().info("Loaded " + cuts.size() + " cutting and " + cooks.size() + " cooking recipe(s).");
    }

    // ------------------------------------------------------------ seasons

/** Current real-world season based on the server clock. */
public static String currentSeason() {
    int month = java.time.LocalDate.now().getMonthValue();
    if (month >= 3 && month <= 5) return "spring";
    if (month >= 6 && month <= 8) return "summer";
    if (month >= 9 && month <= 11) return "autumn";
    return "winter";
}

/** Whether a cook recipe is available in the current season ("all" = always). */
public boolean inSeason(Cook c) {
    String s = c.season();
    if (s == null || s.equalsIgnoreCase("all") || s.isBlank()) return true;
    return s.equalsIgnoreCase(currentSeason());
}

    // ------------------------------------------------------------ specs

    Ingredient ingredient(String spec) {
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("empty ingredient");
        List<Predicate<ItemStack>> any = new ArrayList<>();
        for (String part : spec.split("\\|")) any.add(single(part.trim()));
        return new Ingredient(spec, item -> {
            if (item == null || item.isEmpty()) return false;
            for (Predicate<ItemStack> p : any) if (p.test(item)) return true;
            return false;
        });
    }

    @SuppressWarnings("unchecked")
    private Predicate<ItemStack> single(String s) {
        String low = s.toLowerCase(Locale.ROOT);
        if (low.startsWith("kitchen:")) {
            KItem t = KItem.byId(low.substring(8));
            if (t == null) throw new IllegalArgumentException("unknown kitchen item " + s);
            return item -> plugin.items().typeOf(item) == t;
        }
        if (low.startsWith("sw:")) {
            String id = low.substring(3);
            return item -> id.equals(plugin.items().steamworksMaterial(item));
        }
        if (low.startsWith("#")) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(low.substring(1)), Material.class);
            if (tag == null) throw new IllegalArgumentException("unknown item tag " + s);
            return item -> tag.isTagged(item.getType()) && plugin.items().typeOf(item) == null && plugin.items().steamworksMaterial(item) == null;
        }
        Material m = Material.matchMaterial(s);
        if (m == null) throw new IllegalArgumentException("unknown item " + s);
        // a plain material never matches a Kitchen/Steamworks item that happens to use it as its base
        return item -> item.getType() == m && plugin.items().typeOf(item) == null && plugin.items().steamworksMaterial(item) == null;
    }

    Output output(String spec) {
        if (spec == null) throw new IllegalArgumentException("missing output");
        int count = 1;
        double chance = 1;
        String id = null;
        for (String part : spec.trim().split("\\s+")) {
            if (part.endsWith("%")) chance = Double.parseDouble(part.substring(0, part.length() - 1)) / 100.0;
            else if (part.matches("x\\d+")) count = Integer.parseInt(part.substring(1));
            else id = part;
        }
        if (id == null) throw new IllegalArgumentException("missing output item in '" + spec + "'");
        if (make(id, 1) == null) throw new IllegalArgumentException("unknown output " + id);
        return new Output(id, count, chance);
    }

    /** The item for an output spec (kitchen:<id> or a vanilla material); sw: outputs need Steamworks' item bridge. */
    ItemStack make(String id, int count) {
        String low = id.toLowerCase(Locale.ROOT);
        if (low.startsWith("kitchen:")) {
            KItem t = KItem.byId(low.substring(8));
            return t == null ? null : plugin.items().create(t, count);
        }
        if (low.equals("stripped")) return new ItemStack(Material.STRIPPED_OAK_LOG, count); // placeholder; resolved per input
        Material m = Material.matchMaterial(id);
        return m == null || !m.isItem() ? null : new ItemStack(m, count);
    }

    /** What an output gives this time (chance rolled), or null. */
    ItemStack roll(Output o, ItemStack input) {
        if (o.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= o.chance()) return null;
        if (o.spec().equalsIgnoreCase("stripped")) {
            Material s = Material.matchMaterial("STRIPPED_" + input.getType().name());
            return s == null ? null : new ItemStack(s, o.count());
        }
        return make(o.spec(), o.count());
    }

    // ------------------------------------------------------------ lookups

    Cut cut(ItemStack item) {
        for (Cut c : cuts) if (c.input().test().test(item)) return c;
        return null;
    }

    /**
     * The first cooking recipe whose ingredients exactly cover the filled slots
     * (one item per slot). Returns the slot used for each ingredient in {@code used}.
     */
    Cook cook(ItemStack[] slots, int[] used) {
        int filled = 0;
        for (ItemStack s : slots) if (s != null && !s.isEmpty()) filled++;
        if (filled == 0) return null;
        for (Cook c : cooks) {
            if (c.ingredients().size() != filled) continue;
            if (assign(c.ingredients(), slots, 0, new boolean[slots.length], used)) return c;
        }
        return null;
    }

    private static boolean assign(List<Ingredient> ins, ItemStack[] slots, int i, boolean[] taken, int[] used) {
        if (i == ins.size()) return true;
        for (int s = 0; s < slots.length; s++) {
            if (taken[s] || slots[s] == null || slots[s].isEmpty() || !ins.get(i).test().test(slots[s])) continue;
            taken[s] = true;
            used[i] = s;
            if (assign(ins, slots, i + 1, taken, used)) return true;
            taken[s] = false;
        }
        return false;
    }

    /** Vanilla campfire recipe for the Kamado grill. */
    CampfireRecipe grill(ItemStack item) {
        if (item == null || plugin.items().typeOf(item) != null) return null;
        for (CampfireRecipe r : grill) if (r.getInputChoice().test(item)) return r;
        return null;
    }
}
