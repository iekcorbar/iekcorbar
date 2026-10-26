package com.iekcorbar.zukan;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Everything Zukan knows: every item, and every recipe of vanilla and of our plugins.
 * Other plugins register a {@code Supplier<Map<String,Object>>} service (plain JDK types,
 * so nobody needs a shared API jar) - Zukan asks every registration it finds, whatever the
 * plugin is called or which version it runs; {@code excluded-providers} in the config is the
 * opt-out. The map holds "items" (List of ItemStack) and "recipes" (List of Map with
 * category, icon, station, inputs, outputs, info, grid, shapeless).
 */
final class Catalog {
    /** One entry in the item list. origin = providing plugin; addonOf = the plugin that provider is an add-on of. */
    record Entry(ItemStack stack, String key, String name, String ns, int order, String origin, String addonOf) { }

    private final ZukanPlugin plugin;
    final List<Entry> entries = new ArrayList<>();
    final Map<String, Entry> byKey = new HashMap<>();
    final Map<String, Recipe.Category> categories = new LinkedHashMap<>();
    final Map<String, List<Recipe>> byOutput = new HashMap<>(), byInput = new HashMap<>();
    int recipeCount;
    private final Set<Material> hidden = new HashSet<>();
    private int maxChoices;
    /** Plugins that list their own crafting recipes: their Bukkit versions (generic paper/note block slots) are skipped. */
    private final Set<String> skipNs = new HashSet<>();
    private final Map<String, Map<?, ?>> collected = new LinkedHashMap<>();
    /** provider plugin (lowercase) -> the plugin it declares itself an add-on of, read from its depend/softdepend. */
    private final Map<String, String> addonOf = new HashMap<>();

    Catalog(ZukanPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ keys and names

    /** Kind of item: material + item model (so custom items of other plugins stay apart). */
    static String keyOf(ItemStack it) {
        Key model = it.getData(DataComponentTypes.ITEM_MODEL);
        String base = it.getType().name();
        if (it.getType() == Material.POTION || it.getType() == Material.SPLASH_POTION || it.getType() == Material.LINGERING_POTION) {
            if (it.getItemMeta() instanceof PotionMeta pm && pm.getBasePotionType() != null) base += "#" + pm.getBasePotionType().name();
        }
        return model == null ? base : base + "|" + model.asString();
    }

    static String nsOf(ItemStack it) {
        Key model = it.getData(DataComponentTypes.ITEM_MODEL);
        return model == null ? "minecraft" : model.namespace();
    }

    @SuppressWarnings("deprecation")
    static String nameOf(ItemStack it) {
        var m = it.getItemMeta();
        if (m != null && m.hasDisplayName() && m.displayName() != null) return plain(m.displayName());
        if (m != null && m.hasItemName()) return plain(m.itemName());
        try {
            String s = it.getI18NDisplayName();
            if (s != null && !s.isEmpty()) return s.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            // no language data: fall back to the id
        }
        return it.getType().getKey().getKey().replace('_', ' ');
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ build

    void build() {
        entries.clear();
        byKey.clear();
        categories.clear();
        byOutput.clear();
        byInput.clear();
        recipeCount = 0;
        maxChoices = Math.max(2, plugin.getConfig().getInt("max-choices", 24));
        hidden.clear();
        ignored.clear();
        // an older server config has no such key: MMOItems is hidden by default
        for (String n : plugin.getConfig().contains("ignored-namespaces") ? plugin.getConfig().getStringList("ignored-namespaces") : List.of("mmoitems")) ignored.add(n.toLowerCase(Locale.ROOT));
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.contains("COMMAND_BLOCK") || n.startsWith("TEST_") || n.equals("BARRIER") || n.equals("LIGHT") || n.equals("STRUCTURE_BLOCK")
                    || n.equals("STRUCTURE_VOID") || n.equals("JIGSAW") || n.equals("DEBUG_STICK") || n.equals("KNOWLEDGE_BOOK")) hidden.add(m);
        }
        cat("crafting", "Crafting", Material.CRAFTING_TABLE, 0);
        cat("smelting", "Smelting", Material.FURNACE, 1);
        cat("blasting", "Blasting", Material.BLAST_FURNACE, 2);
        cat("smoking", "Smoking", Material.SMOKER, 3);
        cat("campfire", "Campfire Cooking", Material.CAMPFIRE, 4);
        cat("stonecutting", "Stonecutting", Material.STONECUTTER, 5);
        cat("smithing", "Smithing", Material.SMITHING_TABLE, 6);
        cat("brewing", "Brewing", Material.BREWING_STAND, 7);
        cat("fuel", "Fuel", Material.COAL, 8);
        cat("composting", "Composting", Material.COMPOSTER, 9);

        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir() || hidden.contains(m)) continue;
            addEntry(new ItemStack(m), m.ordinal());
        }
        collected.clear();
        skipNs.clear();
        collect();
        vanillaRecipes();
        plugin.getLogger().info("Recipes found in Bukkit from plugins: " + perNamespace);
        brewing();
        fuelAndCompost();
        providers();
        // custom items that only appear as recipe results or ingredients
        List<Recipe> all = new ArrayList<>();
        for (List<Recipe> l : byOutput.values()) all.addAll(l);
        for (Recipe r : all) for (ItemStack o : r.outputs) addEntry(o, 100_000 + entries.size());
        entries.sort((a, b) -> Integer.compare(a.order(), b.order()));
        plugin.getLogger().info("Catalog: " + entries.size() + " items, " + recipeCount + " recipes, " + categories.size() + " recipe types.");
    }

    private void cat(String id, String name, Material icon, int order) {
        categories.put(id, new Recipe.Category(id, name, new ItemStack(icon), order));
    }

    private void addEntry(ItemStack it, int order) {
        addEntry(it, order, null, null);
    }

    /** origin = the plugin that listed this item through its provider (null: vanilla or a recipe output). */
    private void addEntry(ItemStack it, int order, String origin, String addon) {
        ItemStack one = it.asOne();
        String key = keyOf(one);
        if (byKey.containsKey(key)) return;
        Entry e = new Entry(one, key, nameOf(one), nsOf(one), order, origin, addon);
        byKey.put(key, e);
        entries.add(e);
    }

    // ------------------------------------------------------------ vanilla

    private List<ItemStack> options(RecipeChoice c) {
        List<ItemStack> out = new ArrayList<>();
        if (c instanceof RecipeChoice.MaterialChoice mc) {
            for (Material m : mc.getChoices()) {
                if (m.isAir() || hidden.contains(m)) continue;
                out.add(new ItemStack(m));
                if (out.size() >= maxChoices) break;
            }
        } else if (c instanceof RecipeChoice.ExactChoice ec) {
            for (ItemStack s : ec.getChoices()) {
                out.add(s.asOne());
                if (out.size() >= maxChoices) break;
            }
        }
        return out;
    }

    private void vanillaRecipes() {
        perNamespace.clear();
        Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe r;
            try {
                r = it.next();
            } catch (RuntimeException e) {
                continue; // a plugin's broken recipe
            }
            try {
                convert(r);
            } catch (RuntimeException e) {
                plugin.getLogger().fine("Skipped a recipe: " + e.getMessage());
            }
        }
    }

    private final Map<String, Integer> perNamespace = new TreeMap<>();

    /** Recipe namespaces that never show up (config ignored-namespaces, default mmoitems). */
    private final Set<String> ignored = new HashSet<>();

    /** Items another plugin keeps private (MMOItems tags its items in the item's data under a key in its own namespace). */
    private boolean isIgnoredItem(ItemStack it) {
        if (ignored.isEmpty() || !it.hasItemMeta()) return false;
        for (org.bukkit.NamespacedKey k : it.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (ignored.contains(k.getNamespace().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private void convert(org.bukkit.inventory.Recipe r) {
        ItemStack result = r.getResult();
        if (result == null || result.isEmpty()) return;
        if (r instanceof org.bukkit.Keyed ik && ignored.contains(ik.getKey().getNamespace().toLowerCase(Locale.ROOT))) return;
        if (isIgnoredItem(result)) return;
        if (r instanceof org.bukkit.Keyed kk && !kk.getKey().getNamespace().equals("minecraft")) perNamespace.merge(kk.getKey().getNamespace(), 1, Integer::sum);
        if ((r instanceof ShapedRecipe || r instanceof ShapelessRecipe || r instanceof TransmuteRecipe) && r instanceof org.bukkit.Keyed k
                && skipNs.contains(k.getKey().getNamespace())) return;
        if (r instanceof ShapedRecipe s) {
            List<List<ItemStack>> cells = new ArrayList<>();
            for (int i = 0; i < 9; i++) cells.add(List.of());
            String[] shape = s.getShape();
            Map<Character, RecipeChoice> choices = s.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++) {
                for (int col = 0; col < shape[row].length() && col < 3; col++) {
                    RecipeChoice c = choices.get(shape[row].charAt(col));
                    if (c != null) cells.set(row * 3 + col, options(c));
                }
            }
            add(new Recipe(categories.get("crafting"), cells, List.of(result.clone()), List.of(), true, false, new ItemStack(Material.CRAFTING_TABLE)));
        } else if (r instanceof ShapelessRecipe s) {
            List<List<ItemStack>> cells = new ArrayList<>();
            for (RecipeChoice c : s.getChoiceList()) if (cells.size() < 9) cells.add(options(c));
            while (cells.size() < 9) cells.add(List.of());
            add(new Recipe(categories.get("crafting"), cells, List.of(result.clone()), List.of(), true, true, new ItemStack(Material.CRAFTING_TABLE)));
        } else if (r instanceof TransmuteRecipe t) {
            List<List<ItemStack>> cells = new ArrayList<>();
            cells.add(options(t.getInput()));
            cells.add(options(t.getMaterial()));
            while (cells.size() < 9) cells.add(List.of());
            add(new Recipe(categories.get("crafting"), cells, List.of(result.clone()), List.of(), true, true, new ItemStack(Material.CRAFTING_TABLE)));
        } else if (r instanceof CookingRecipe<?> c) {
            String id = c instanceof BlastingRecipe ? "blasting" : c instanceof SmokingRecipe ? "smoking" : c instanceof CampfireRecipe ? "campfire" : c instanceof FurnaceRecipe ? "smelting" : null;
            if (id == null) return;
            List<String> info = new ArrayList<>();
            info.add("Time: " + String.format(Locale.ROOT, "%.1f", c.getCookingTime() / 20.0) + " s");
            if (c.getExperience() > 0) info.add("Experience: " + c.getExperience());
            add(new Recipe(categories.get(id), List.of(options(c.getInputChoice())), List.of(result.clone()), info, false, false, categories.get(id).icon));
        } else if (r instanceof StonecuttingRecipe s) {
            add(new Recipe(categories.get("stonecutting"), List.of(options(s.getInputChoice())), List.of(result.clone()), List.of(), false, false, new ItemStack(Material.STONECUTTER)));
        } else if (r instanceof SmithingTransformRecipe s) {
            add(new Recipe(categories.get("smithing"), List.of(options(s.getTemplate()), options(s.getBase()), options(s.getAddition())),
                    List.of(result.clone()), List.of(), false, false, new ItemStack(Material.SMITHING_TABLE)));
        }
    }

    // ------------------------------------------------------------ brewing, fuel, composting

    private ItemStack potion(Material bottle, String type) {
        ItemStack it = new ItemStack(bottle);
        try {
            PotionType t = PotionType.valueOf(type);
            if (it.getItemMeta() instanceof PotionMeta pm) {
                pm.setBasePotionType(t);
                it.setItemMeta(pm);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return it;
    }

    private void brewing() {
        Recipe.Category cat = categories.get("brewing");
        ItemStack stand = new ItemStack(Material.BREWING_STAND);
        String[][] table = {
                {"WATER", "NETHER_WART", "AWKWARD"}, {"WATER", "FERMENTED_SPIDER_EYE", "WEAKNESS"}, {"WATER", "SUGAR", "MUNDANE"},
                {"WATER", "REDSTONE", "MUNDANE"}, {"WATER", "GLOWSTONE_DUST", "THICK"}, {"WATER", "SLIME_BLOCK", "OOZING"},
                {"WATER", "STONE", "INFESTED"}, {"WATER", "COBWEB", "WEAVING"}, {"WATER", "BREEZE_ROD", "WIND_CHARGED"},
                {"AWKWARD", "GOLDEN_CARROT", "NIGHT_VISION"}, {"AWKWARD", "RABBIT_FOOT", "LEAPING"}, {"AWKWARD", "MAGMA_CREAM", "FIRE_RESISTANCE"},
                {"AWKWARD", "SUGAR", "SWIFTNESS"}, {"AWKWARD", "PUFFERFISH", "WATER_BREATHING"}, {"AWKWARD", "GLISTERING_MELON_SLICE", "HEALING"},
                {"AWKWARD", "SPIDER_EYE", "POISON"}, {"AWKWARD", "GHAST_TEAR", "REGENERATION"}, {"AWKWARD", "BLAZE_POWDER", "STRENGTH"},
                {"AWKWARD", "TURTLE_HELMET", "TURTLE_MASTER"}, {"AWKWARD", "PHANTOM_MEMBRANE", "SLOW_FALLING"},
                {"NIGHT_VISION", "FERMENTED_SPIDER_EYE", "INVISIBILITY"}, {"LEAPING", "FERMENTED_SPIDER_EYE", "SLOWNESS"},
                {"SWIFTNESS", "FERMENTED_SPIDER_EYE", "SLOWNESS"}, {"HEALING", "FERMENTED_SPIDER_EYE", "HARMING"},
                {"POISON", "FERMENTED_SPIDER_EYE", "HARMING"},
        };
        for (String[] row : table) {
            Material ing = Material.matchMaterial(row[1]);
            ItemStack from = potion(Material.POTION, row[0]), to = potion(Material.POTION, row[2]);
            if (ing == null || from == null || to == null) continue;
            add(new Recipe(cat, List.of(List.of(from), List.of(new ItemStack(ing))), List.of(to), List.of("Takes 20 s"), false, false, stand));
        }
        // modifiers apply to any potion
        ItemStack swift = potion(Material.POTION, "SWIFTNESS");
        if (swift != null) {
            add(new Recipe(cat, List.of(List.of(swift), List.of(new ItemStack(Material.REDSTONE))), List.of(swift),
                    List.of("Redstone: longer lasting", "Glowstone dust: stronger", "Gunpowder: splash potion", "Dragon's breath: lingering potion"), false, false, stand));
        }
    }

    private void fuelAndCompost() {
        Recipe.Category fuel = categories.get("fuel"), comp = categories.get("composting");
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir() || hidden.contains(m)) continue;
            if (m.isFuel()) {
                int ticks = m.asItemType().getBurnDuration();
                if (ticks > 0) {
                    add(new Recipe(fuel, List.of(List.of(new ItemStack(m))), List.of(), List.of("Burns for " + String.format(Locale.ROOT, "%.1f", ticks / 20.0) + " s",
                            "Smelts " + String.format(Locale.ROOT, "%.1f", ticks / 200.0) + " items"), false, false, new ItemStack(Material.FURNACE)));
                }
            }
            if (m.isCompostable()) {
                add(new Recipe(comp, List.of(List.of(new ItemStack(m))), List.of(new ItemStack(Material.BONE_MEAL)),
                        List.of("Chance: " + Math.round(m.getCompostChance() * 100) + "% to raise the level"), false, false, new ItemStack(Material.COMPOSTER)));
            }
        }
    }

    // ------------------------------------------------------------ plugin providers

    /**
     * Asks every plugin that registered the Supplier service for its data once (before the vanilla pass,
     * which skips what they replace). There is no allow list: registration IS the opt-in, so a plugin of
     * any name or version is read as soon as it is installed - only excluded-providers keeps one out.
     * A provider whose plugin.yml depends (or softdepends) on another provider is treated as an add-on
     * of it: its content is found under @parent too and shown with an "Addon of parent" note.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void collect() {
        Set<String> excluded = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("excluded-providers")) excluded.add(s.toLowerCase(Locale.ROOT));
        java.util.Collection<RegisteredServiceProvider<Supplier>> regs = Bukkit.getServicesManager().getRegistrations(Supplier.class);
        Set<String> providers = new HashSet<>();
        for (RegisteredServiceProvider<Supplier> reg : regs) providers.add(reg.getPlugin().getName().toLowerCase(Locale.ROOT));
        for (RegisteredServiceProvider<Supplier> reg : regs) {
            String owner = reg.getPlugin().getName();
            String key = owner.toLowerCase(Locale.ROOT);
            if (excluded.contains(key)) continue;
            for (String dep : reg.getPlugin().getDescription().getDepend()) {
                String p = dep.toLowerCase(Locale.ROOT);
                if (!p.equals(key) && providers.contains(p)) { addonOf.put(key, dep); break; }
            }
            if (!addonOf.containsKey(key)) {   // add-ons usually softdepend: same rule for those
                for (String dep : reg.getPlugin().getDescription().getSoftDepend()) {
                    String p = dep.toLowerCase(Locale.ROOT);
                    if (!p.equals(key) && providers.contains(p)) { addonOf.put(key, dep); break; }
                }
            }
            Object data;
            try {
                data = reg.getProvider().get();
            } catch (Throwable t) {
                plugin.getLogger().warning(owner + " could not list its recipes: " + t);
                continue;
            }
            if (!(data instanceof Map<?, ?> map)) continue;
            collected.put(owner, map);
            if (Boolean.TRUE.equals(map.get("replaceCrafting"))) skipNs.add(owner.toLowerCase(Locale.ROOT));
        }
    }

    private void providers() {
        int order = 20;
        for (Map.Entry<String, Map<?, ?>> en : collected.entrySet()) {
            String owner = en.getKey();
            Map<?, ?> map = en.getValue();
            if (map.get("items") instanceof List<?> items) {
                String origin = owner.toLowerCase(java.util.Locale.ROOT);
                String parent = addonOf.get(origin);
                for (Object o : items) if (o instanceof ItemStack s && !s.isEmpty()) addEntry(s, 50_000 + entries.size(), origin, parent);
            }
            if (map.get("recipes") instanceof List<?> recipes) {
                int before = recipeCount;
                for (Object o : recipes) {
                    if (!(o instanceof Map<?, ?> rm)) continue;
                    try {
                        order = fromProvider(owner, rm, order);
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning(owner + ": bad recipe skipped (" + e + ")");
                    }
                }
                plugin.getLogger().info(owner + ": " + (recipeCount - before) + " recipes.");
            }
        }
    }

    private int fromProvider(String owner, Map<?, ?> rm, int order) {
        String name = String.valueOf(rm.get("category"));
        String id = owner.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
        Recipe.Category cat = categories.get(id);
        if (cat == null) {
            ItemStack icon = rm.get("icon") instanceof ItemStack s ? s.asOne() : rm.get("station") instanceof ItemStack s2 ? s2.asOne() : new ItemStack(Material.BOOK);
            cat = new Recipe.Category(id, name, icon, order++);
            categories.put(id, cat);
        }
        List<List<ItemStack>> inputs = new ArrayList<>();
        if (rm.get("inputs") instanceof List<?> in) {
            for (Object cell : in) {
                List<ItemStack> opts = new ArrayList<>();
                if (cell instanceof ItemStack s) opts.add(s.clone());
                else if (cell instanceof List<?> l) for (Object o : l) if (o instanceof ItemStack s && !s.isEmpty()) opts.add(s.clone());
                if (opts.size() > maxChoices) opts = new ArrayList<>(opts.subList(0, maxChoices));
                inputs.add(opts);
            }
        }
        List<ItemStack> outputs = new ArrayList<>();
        if (rm.get("outputs") instanceof List<?> out) for (Object o : out) if (o instanceof ItemStack s && !s.isEmpty()) outputs.add(s.clone());
        List<String> info = new ArrayList<>();
        if (rm.get("info") instanceof List<?> l) for (Object o : l) info.add(String.valueOf(o));
        boolean grid = Boolean.TRUE.equals(rm.get("grid"));
        if (grid) while (inputs.size() < 9) inputs.add(List.of());
        ItemStack station = rm.get("station") instanceof ItemStack s ? s.asOne() : null;
        add(new Recipe(cat, inputs, outputs, info, grid, Boolean.TRUE.equals(rm.get("shapeless")), station));
        return order;
    }

    // ------------------------------------------------------------ index

    private void add(Recipe r) {
        recipeCount++;
        Set<String> seen = new HashSet<>();
        for (ItemStack o : r.outputs) {
            String k = keyOf(o);
            if (seen.add("o" + k)) byOutput.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
            addEntry(o, 100_000 + entries.size());
        }
        for (List<ItemStack> cell : r.inputs) {
            for (ItemStack s : cell) {
                String k = keyOf(s);
                if (seen.add("i" + k)) byInput.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
                if (!byKey.containsKey(k)) addEntry(s, 100_000 + entries.size());
            }
        }
    }

    List<Recipe> recipesFor(String key) {
        return byOutput.getOrDefault(key, Collections.emptyList());
    }

    List<Recipe> usesOf(String key) {
        return byInput.getOrDefault(key, Collections.emptyList());
    }
}
