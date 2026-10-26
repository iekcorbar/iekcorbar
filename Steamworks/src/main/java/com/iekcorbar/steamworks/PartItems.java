package com.iekcorbar.steamworks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import com.iekcorbar.steamworks.trains.CarType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Part items (paper with a part id in its data and the part's model as look),
 * display items for the model entities, and the crafting recipes.
 *
 * Recipes that need a part or a material use "any paper" in the shape (recipe
 * matching can't look at item data); PrepareItemCraftEvent then checks slot by
 * slot that those hold exactly the right item (see validCraft). Steamworks
 * items can't be used as paper in vanilla recipes either.
 */
final class PartItems {
    private final SteamworksPlugin plugin;
    final NamespacedKey partKey, materialKey, carKey;
    private final Map<String, ItemStack> displayCache = new HashMap<>();
    private final Map<NamespacedKey, Spec> recipeSpecs = new HashMap<>();
    final List<NamespacedKey> recipeKeys = new ArrayList<>();
    /** Our crafting recipes with their real ingredients (for the recipe viewer), by recipe id. */
    record CraftRecipe(ItemStack result, String[] shape, Map<Character, Object> ingredients) {}
    final Map<String, CraftRecipe> craftRecipes = new java.util.LinkedHashMap<>();
    private Method applyDisplayModel;
    private boolean fmmMissingLogged;
    private final java.util.Set<String> unknownLogged = new java.util.HashSet<>();
    /** False until FreeMinecraftModels has finished loading its models (see waitForModels). */
    private boolean modelsReady;

    PartItems(SteamworksPlugin plugin) {
        this.plugin = plugin;
        this.partKey = new NamespacedKey(plugin, "part");
        this.materialKey = new NamespacedKey(plugin, "material");
        this.carKey = new NamespacedKey(plugin, "car");
    }

    ItemStack create(PartType t, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : t.lore) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Steamworks", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, t.id());
        item.setItemMeta(meta);
        applyModel(item, t.model());
        return item;
    }

    PartType typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(partKey, PersistentDataType.STRING);
        return id == null ? null : PartType.byId(id);
    }

    ItemStack createMaterial(MaterialType t, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : t.lore) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Steamworks", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        if (t == MaterialType.DOWSING_ROD || t.name().endsWith("CANISTER")) meta.setMaxStackSize(t == MaterialType.CANISTER ? 16 : 1);
        meta.getPersistentDataContainer().set(materialKey, PersistentDataType.STRING, t.id());
        item.setItemMeta(meta);
        applyModel(item, t.model());
        return item;
    }

    MaterialType materialOf(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(materialKey, PersistentDataType.STRING);
        return id == null ? null : MaterialType.byId(id);
    }

    ItemStack createCar(CarType t, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(t.displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : t.lore) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Right-click a rail to put it down.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Steamworks", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(carKey, PersistentDataType.STRING, t.id());
        item.setItemMeta(meta);
        applyModel(item, t.model());
        return item;
    }

    /** The item's name if a player renamed it (anvil / name tag), else null. */
    String customName(ItemStack item, String defaultName) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasCustomName()) return null;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().customName());
        return name.isBlank() || name.equals(defaultName) ? null : name.trim();
    }

    CarType carOf(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(carKey, PersistentDataType.STRING);
        return id == null ? null : CarType.byId(id);
    }

    /** Any Steamworks item (part, material or train car). */
    boolean isOurs(ItemStack item) {
        return typeOf(item) != null || materialOf(item) != null || carOf(item) != null;
    }

    /** The same model trick the parts use, for other items (the manual's book model). */
    boolean showAs(ItemStack item, String model) {
        return applyModel(item, model);
    }

    ItemStack displayItem(String model) {
        ItemStack cached = displayCache.get(model);
        if (cached != null) return cached.clone();
        ItemStack item = new ItemStack(Material.PAPER);
        if (applyModel(item, model)) displayCache.put(model, item); // a miss is retried next time
        return item.clone();
    }

    /** The same item with its display model applied again (results of recipes registered before FMM was ready). */
    ItemStack refreshModel(ItemStack item) {
        if (item == null) return null;
        PartType t = typeOf(item);
        MaterialType m = t == null ? materialOf(item) : null;
        CarType c = t == null && m == null ? carOf(item) : null;
        String model = t != null ? t.model() : m != null ? m.model() : c != null ? c.model() : null;
        if (model == null) return item;
        ItemStack copy = item.clone();
        applyModel(copy, model);
        return copy;
    }

    /** Every model this plugin shows that FMM doesn't know (yet). */
    List<String> missingModels() {
        List<String> names = new ArrayList<>();
        for (PartType t : PartType.values()) names.add(t.model());
        for (MaterialType t : MaterialType.values()) names.add(t.model());
        for (CarType t : CarType.values()) names.add(t.model());
        for (FluidType t : FluidType.values()) names.add(t.model());
        List<String> missing = new ArrayList<>();
        for (String n : names) {
            if (!applyModel(new ItemStack(Material.PAPER), n)) missing.add(n);
        }
        return missing;
    }

    /**
     * FreeMinecraftModels loads its models after the server has started, so asking for them in onEnable fails.
     * Checks every 5 s (up to a minute) until FMM knows our models, then runs {@code then}; only models still
     * missing after that are reported.
     */
    void waitForModels(Runnable then) {
        waitForModels(then, 0);
    }

    private void waitForModels(Runnable then, int attempt) {
        List<String> missing = missingModels();
        if (!missing.isEmpty() && attempt < 12 && !fmmMissingLogged) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> waitForModels(then, attempt + 1), 100L);
            return;
        }
        modelsReady = true;
        displayCache.clear();
        if (!missing.isEmpty() && !fmmMissingLogged) {
            plugin.getLogger().warning(missing.size() + " model(s) unknown to FreeMinecraftModels: " + String.join(", ", missing)
                    + " - copy fmm-models/steamworks/ into plugins/FreeMinecraftModels/models/ and reload FreeMinecraftModels.");
            unknownLogged.addAll(missing);
        } else if (missing.isEmpty()) {
            plugin.getLogger().info("All Steamworks models found in FreeMinecraftModels.");
        }
        then.run();
    }

    /** FreeMinecraftModels display model as the item's look (same call IekcorbarSiteSync's books use). */
    private boolean applyModel(ItemStack item, String model) {
        try {
            if (applyDisplayModel == null) {
                Plugin fmm = Bukkit.getPluginManager().getPlugin("FreeMinecraftModels");
                if (fmm == null) throw new ClassNotFoundException("FreeMinecraftModels is not installed");
                applyDisplayModel = Class.forName("com.magmaguy.freeminecraftmodels.api.ModelItemAPI", true,
                        fmm.getClass().getClassLoader()).getMethod("applyDisplayModel", ItemStack.class, String.class);
            }
            boolean ok = (boolean) applyDisplayModel.invoke(null, item, model);
            if (!ok && modelsReady && unknownLogged.add(model)) {
                plugin.getLogger().warning("Unknown model '" + model + "' - copy fmm-models/steamworks/ into "
                        + "plugins/FreeMinecraftModels/models/ and reload FreeMinecraftModels.");
            }
            return ok;
        } catch (Exception e) {
            if (!fmmMissingLogged) {
                fmmMissingLogged = true;
                plugin.getLogger().warning("FreeMinecraftModels not available (" + e.getMessage()
                        + ") - parts show as plain items.");
            }
            return false;
        }
    }

    // ------------------------------------------------------------ recipes

    void registerRecipes() {
        RecipeChoice planks = new RecipeChoice.MaterialChoice(Tag.PLANKS);
        RecipeChoice stones = new RecipeChoice.MaterialChoice(Material.STONE, Material.COBBLESTONE, Material.ANDESITE,
                Material.DIORITE, Material.GRANITE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE);
        RecipeChoice slabs = new RecipeChoice.MaterialChoice(Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB,
                Material.JUNGLE_SLAB, Material.ACACIA_SLAB, Material.DARK_OAK_SLAB, Material.MANGROVE_SLAB, Material.CHERRY_SLAB);
        RecipeChoice strippedLogs = new RecipeChoice.MaterialChoice(Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
                Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG,
                Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG);
        // {amount, shape, char, ingredient, ...}; a PartType/MaterialType ingredient = a paper slot checked in validCraft.
        // Shapes/ingredients follow Create 6.0.10's own recipe JSONs (data/create/recipe/crafting/*) one-on-one;
        // where Create applies an item to a block in-world (casing on a stripped log) we craft it on the table instead.
        Map<PartType, Object[]> r = new EnumMap<>(PartType.class);
        r.put(PartType.SHAFT, new Object[]{8, new String[]{"A", "A"}, 'A', MaterialType.ANDESITE_ALLOY});
        r.put(PartType.COGWHEEL, new Object[]{1, new String[]{"P", "S"}, 'P', planks, 'S', PartType.SHAFT});
        r.put(PartType.LARGE_COGWHEEL, new Object[]{1, new String[]{"P", "P", "S"}, 'P', planks, 'S', PartType.SHAFT});
        r.put(PartType.GEARBOX, new Object[]{1, new String[]{" C ", "CBC", " C "}, 'C', PartType.COGWHEEL, 'B', MaterialType.ANDESITE_CASING});
        r.put(PartType.HAND_CRANK, new Object[]{1, new String[]{"CCC", "  A"}, 'C', planks, 'A', MaterialType.ANDESITE_ALLOY});
        r.put(PartType.WATER_WHEEL, new Object[]{1, new String[]{"SSS", "SCS", "SSS"}, 'S', planks, 'C', PartType.SHAFT});
        r.put(PartType.SPEED_GAUGE, new Object[]{1, new String[]{"C", "A"}, 'C', Material.COMPASS, 'A', MaterialType.ANDESITE_CASING});
        r.put(PartType.STRESS_GAUGE, new Object[]{1, new String[]{"S"}, 'S', PartType.SPEED_GAUGE}); // Create: stressometer = converted speedometer
        r.put(PartType.MILLSTONE, new Object[]{1, new String[]{"C", "S", "I"}, 'C', PartType.COGWHEEL, 'S', MaterialType.ANDESITE_CASING, 'I', stones});
        r.put(PartType.STEAM_HAMMER, new Object[]{1, new String[]{"S", "C", "I"}, 'S', PartType.SHAFT, 'C', MaterialType.ANDESITE_CASING, 'I', Material.IRON_BLOCK});
        r.put(PartType.CLUTCH, new Object[]{1, new String[]{"C", "S", "R"}, 'C', MaterialType.ANDESITE_CASING, 'S', PartType.SHAFT, 'R', Material.REDSTONE});
        r.put(PartType.GEARSHIFT, new Object[]{1, new String[]{"C", "S", "R"}, 'C', MaterialType.ANDESITE_CASING, 'S', PartType.COGWHEEL, 'R', Material.REDSTONE});
        r.put(PartType.BELLOWS_FAN, new Object[]{1, new String[]{"S", "C", "P"}, 'S', PartType.SHAFT, 'C', MaterialType.ANDESITE_CASING, 'P', MaterialType.PROPELLER});
        r.put(PartType.KETTLE, new Object[]{1, new String[]{"A A", "AAA"}, 'A', MaterialType.ANDESITE_ALLOY}); // Create's basin
        r.put(PartType.MIXER, new Object[]{1, new String[]{"S", "C", "I"}, 'S', PartType.COGWHEEL, 'C', MaterialType.ANDESITE_CASING, 'I', MaterialType.WHISK});
        r.put(PartType.STATION, new Object[]{1, new String[]{"LBL", " T ", " T "}, 'L', Material.LANTERN, 'B', new RecipeChoice.MaterialChoice(Tag.ITEMS_SIGNS), 'T', Material.STICK});
        r.put(PartType.SIGNAL, new Object[]{1, new String[]{"L", "P", "B"}, 'L', Material.REDSTONE_LAMP, 'P', MaterialType.IRON_PLATE, 'B', Material.IRON_BARS});
        r.put(PartType.PUMPJACK, new Object[]{1, new String[]{"BPB", "ISI", "I I"}, 'B', MaterialType.BRASS_PLATE, 'P', Material.PISTON, 'I', MaterialType.IRON_PLATE, 'S', PartType.SHAFT});
        r.put(PartType.PIPE, new Object[]{4, new String[]{"SCS"}, 'S', MaterialType.COPPER_PLATE, 'C', Material.COPPER_INGOT}); // Create's fluid pipe
        r.put(PartType.TANK, new Object[]{1, new String[]{"B", "C", "B"}, 'B', MaterialType.COPPER_PLATE, 'C', Material.BARREL}); // Create's fluid tank
        r.put(PartType.REFINERY, new Object[]{1, new String[]{"PTP", "BKB", "BFB"}, 'P', PartType.PIPE, 'T', PartType.TANK, 'B', MaterialType.BRASS_PLATE, 'K', PartType.KETTLE, 'F', Material.BLAST_FURNACE});
        r.put(PartType.DRILL, new Object[]{1, new String[]{" A ", "AIA", " C "}, 'A', MaterialType.ANDESITE_ALLOY, 'I', Material.IRON_INGOT, 'C', MaterialType.ANDESITE_CASING});
        r.put(PartType.SAW, new Object[]{1, new String[]{" A ", "AIA", " C "}, 'A', MaterialType.IRON_PLATE, 'I', Material.IRON_INGOT, 'C', MaterialType.ANDESITE_CASING});
        r.put(PartType.BELT, new Object[]{4, new String[]{"KKK", "ISI"}, 'K', Material.DRIED_KELP, 'I', Material.IRON_INGOT, 'S', PartType.SHAFT}); // Create has no table recipe for the belt (glue + connector); ours stays
        r.put(PartType.ARM, new Object[]{1, new String[]{"BCB", " S ", "III"}, 'B', MaterialType.BRASS_PLATE, 'C', PartType.COGWHEEL, 'S', PartType.SHAFT, 'I', MaterialType.IRON_PLATE});
        r.put(PartType.SPROCKET, new Object[]{1, new String[]{"CN", "NN"}, 'C', MaterialType.ANDESITE_CASING, 'N', Material.IRON_NUGGET}); // Create's encased chain drive: casing + 3 nuggets
        r.put(PartType.WINDMILL, new Object[]{1, new String[]{"W", "S", "I"}, 'W', new RecipeChoice.MaterialChoice(Tag.WOOL), 'S', slabs, 'I', PartType.SHAFT}); // Create's windmill bearing + a sail
        r.put(PartType.STEAM_BOILER, new Object[]{1, new String[]{"CTC", "CFC", "CCC"}, 'C', MaterialType.COPPER_PLATE, 'T', PartType.TANK, 'F', Material.BLAST_FURNACE}); // ours (Create burns under the engine directly)
        r.put(PartType.STEAM_ENGINE, new Object[]{1, new String[]{"P", "A", "C"}, 'P', MaterialType.GOLDEN_PLATE, 'A', MaterialType.ANDESITE_ALLOY, 'C', Material.COPPER_BLOCK});
        r.put(PartType.CRUSHING_WHEEL, new Object[]{2, new String[]{"AAA", "ASP", "AAA"}, 'A', MaterialType.ANDESITE_ALLOY, 'S', stones, 'P', planks}); // Create builds these on a 5x5 assembly table
        // batch 2 (0.3.17): item logistics + fluid parts, recipes exactly as Create 6 has them
        r.put(PartType.CHUTE, new Object[]{4, new String[]{"A", "I", "A"}, 'A', MaterialType.IRON_PLATE, 'I', Material.IRON_INGOT});
        r.put(PartType.FUNNEL, new Object[]{2, new String[]{"A", "K"}, 'A', MaterialType.ANDESITE_ALLOY, 'K', Material.DRIED_KELP});
        r.put(PartType.DEPOT, new Object[]{1, new String[]{"A", "C"}, 'A', MaterialType.ANDESITE_ALLOY, 'C', MaterialType.ANDESITE_CASING});
        r.put(PartType.EJECTOR, new Object[]{1, new String[]{"A", "D", "I"}, 'A', MaterialType.GOLDEN_PLATE, 'D', PartType.DEPOT, 'I', PartType.COGWHEEL});
        r.put(PartType.PUMP, new Object[]{1, new String[]{"C", "P"}, 'C', PartType.COGWHEEL, 'P', PartType.PIPE});   // Create: shapeless cog + pipe, as a column here
        r.put(PartType.VALVE, new Object[]{1, new String[]{"I", "P"}, 'I', MaterialType.IRON_PLATE, 'P', PartType.PIPE});
        r.put(PartType.WHISTLE, new Object[]{1, new String[]{"P", "C"}, 'P', MaterialType.GOLDEN_PLATE, 'C', Material.COPPER_INGOT});

        for (Map.Entry<PartType, Object[]> e : r.entrySet()) addRecipe(e.getKey().id(), create(e.getKey(), (int) e.getValue()[0]), e.getValue());
        addRecipe("large_cogwheel_from_little", create(PartType.LARGE_COGWHEEL, 1), new Object[]{1, new String[]{"P", "C"}, 'P', planks, 'C', PartType.COGWHEEL});
        addRecipe("speed_gauge_from_stress", create(PartType.SPEED_GAUGE, 1), new Object[]{1, new String[]{"S"}, 'S', PartType.STRESS_GAUGE});

        // Create's base chain: nuggets and alloy first, then the casings, whisk and propeller built from them
        addRecipe("andesite_alloy", createMaterial(MaterialType.ANDESITE_ALLOY, 1), new Object[]{1, new String[]{"BA", "AB"}, 'B', Material.IRON_NUGGET, 'A', Material.ANDESITE});
        addRecipe("andesite_alloy_from_zinc", createMaterial(MaterialType.ANDESITE_ALLOY, 1), new Object[]{1, new String[]{"BA", "AB"}, 'B', MaterialType.ZINC_NUGGET, 'A', Material.ANDESITE});
        addRecipe("zinc_nugget", createMaterial(MaterialType.ZINC_NUGGET, 9), new Object[]{9, new String[]{"I"}, 'I', MaterialType.ZINC_INGOT});
        addRecipe("andesite_casing", createMaterial(MaterialType.ANDESITE_CASING, 1), new Object[]{1, new String[]{"A", "L"}, 'A', MaterialType.ANDESITE_ALLOY, 'L', strippedLogs});
        addRecipe("brass_casing", createMaterial(MaterialType.BRASS_CASING, 1), new Object[]{1, new String[]{"B", "L"}, 'B', MaterialType.BRASS_INGOT, 'L', strippedLogs});
        addRecipe("whisk", createMaterial(MaterialType.WHISK, 1), new Object[]{1, new String[]{" C ", "SCS", "SSS"}, 'C', MaterialType.ANDESITE_ALLOY, 'S', MaterialType.IRON_PLATE});
        addRecipe("propeller", createMaterial(MaterialType.PROPELLER, 1), new Object[]{1, new String[]{" S ", "SCS", " S "}, 'C', MaterialType.ANDESITE_ALLOY, 'S', MaterialType.IRON_PLATE});

        // train cars: brass plates (mixer + hammer) for the locomotive
        addRecipe("locomotive", createCar(CarType.LOCOMOTIVE, 1), new Object[]{1, new String[]{"BCB", "BFB", " M "},
                'B', MaterialType.BRASS_PLATE, 'C', PartType.COGWHEEL, 'F', Material.FURNACE, 'M', Material.MINECART});
        addRecipe("passenger_wagon", createCar(CarType.PASSENGER_WAGON, 1), new Object[]{1, new String[]{"PPP", "PIP", " M "},
                'P', planks, 'I', MaterialType.IRON_PLATE, 'M', Material.MINECART});
        addRecipe("cargo_wagon", createCar(CarType.CARGO_WAGON, 1), new Object[]{1, new String[]{"P P", "PCP", " M "},
                'P', planks, 'C', Material.CHEST, 'M', Material.MINECART});

        // tin canisters and the dowsing rod
        addRecipe("canister", createMaterial(MaterialType.CANISTER, 2), new Object[]{2, new String[]{" T ", "T T", "TTT"}, 'T', MaterialType.TIN_INGOT});
        addRecipe("arm_gripper", createMaterial(MaterialType.ARM_GRIPPER, 1), new Object[]{1, new String[]{"I I", "I I", " B "}, 'I', Material.IRON_INGOT, 'B', MaterialType.BRASS_PLATE});
        addRecipe("arm_placer", createMaterial(MaterialType.ARM_PLACER, 1), new Object[]{1, new String[]{"IPI", " B "}, 'I', Material.IRON_INGOT, 'P', Material.PISTON, 'B', MaterialType.BRASS_PLATE});
        addRecipe("arm_drill", createMaterial(MaterialType.ARM_DRILL, 1), new Object[]{1, new String[]{" B ", "III", " I "}, 'I', Material.IRON_INGOT, 'B', MaterialType.BRASS_PLATE});
        addRecipe("punch_card", createMaterial(MaterialType.PUNCH_CARD, 2), new Object[]{2, new String[]{"PNP"}, 'P', Material.PAPER, 'N', Material.IRON_NUGGET});
        addRecipe("dowsing_rod", createMaterial(MaterialType.DOWSING_ROD, 1), new Object[]{1, new String[]{"  C", " S ", "S  "}, 'C', Material.COPPER_INGOT, 'S', Material.STICK});
        addRecipe("wrench", createMaterial(MaterialType.WRENCH, 1), new Object[]{1, new String[]{" II", "SI ", "S  "}, 'I', Material.IRON_INGOT, 'S', Material.STICK});

        // raw ore -> ingot in furnaces and blast furnaces (exact match on our raw item)
        smelt("zinc_ingot", MaterialType.RAW_ZINC, MaterialType.ZINC_INGOT);
        smelt("tin_ingot", MaterialType.RAW_TIN, MaterialType.TIN_INGOT);
    }

    private void smelt(String id, MaterialType in, MaterialType out) {
        RecipeChoice choice = new RecipeChoice.ExactChoice(createMaterial(in, 1));
        NamespacedKey furnace = new NamespacedKey(plugin, "smelt_" + id), blast = new NamespacedKey(plugin, "blast_" + id);
        Bukkit.removeRecipe(furnace, false);
        Bukkit.removeRecipe(blast, false);
        Bukkit.addRecipe(new org.bukkit.inventory.FurnaceRecipe(furnace, createMaterial(out, 1), choice, 0.7f, 200));
        Bukkit.addRecipe(new org.bukkit.inventory.BlastingRecipe(blast, createMaterial(out, 1), choice, 0.7f, 100));
        recipeKeys.add(furnace);
        recipeKeys.add(blast);
    }

    /** d = {amount (unused here), shape, char, ingredient, ...}. */
    void addRecipe(String id, ItemStack result, Object[] d) {
        RecipeChoice paper = new RecipeChoice.MaterialChoice(Material.PAPER);
        NamespacedKey key = new NamespacedKey(plugin, id);
        Bukkit.removeRecipe(key, false);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        String[] shape = (String[]) d[1];
        recipe.shape(shape);
        Map<Character, Object> paperSlots = new HashMap<>();
        Map<Character, Object> all = new java.util.LinkedHashMap<>();
        for (int i = 2; i < d.length; i += 2) {
            char c = (char) d[i];
            Object ing = d[i + 1];
            all.put(c, ing);
            if (ing instanceof Material m) recipe.setIngredient(c, m);
            else if (ing instanceof RecipeChoice choice) recipe.setIngredient(c, choice);
            else if (ing instanceof DecorType) {
                recipe.setIngredient(c, new RecipeChoice.MaterialChoice(Material.NOTE_BLOCK)); // checked in validCraft
                paperSlots.put(c, ing);
            } else {
                recipe.setIngredient(c, paper);
                paperSlots.put(c, ing);
            }
        }
        Bukkit.addRecipe(recipe);
        recipeKeys.add(key);
        recipeSpecs.put(key, new Spec(shape, paperSlots));
        craftRecipes.put(id, new CraftRecipe(result.clone(), shape, all));
    }

    /** Which Steamworks item each paper slot of one of our recipes must hold. */
    private record Spec(String[] shape, Map<Character, Object> paperSlots) {}

    void unregisterRecipes() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k, false);
        recipeKeys.clear();
        craftRecipes.clear();
    }

    /** False if Steamworks items are misused in a recipe (or plain paper / the wrong part sits in a part slot). */
    boolean validCraft(Recipe recipe, ItemStack[] matrix) {
        NamespacedKey key = recipe instanceof org.bukkit.Keyed k ? k.getKey() : null;
        Spec spec = key == null ? null : recipeSpecs.get(key);
        if (spec == null) {
            for (ItemStack i : matrix) if (isOurs(i)) return false;
            return true;
        }
        // the recipe matched somewhere in the grid (maybe shifted or mirrored): every placement must check out
        int w = (int) Math.round(Math.sqrt(matrix.length));
        int sh = spec.shape().length, sw = 0;
        for (String row : spec.shape()) sw = Math.max(sw, row.length());
        for (int dy = 0; dy + sh <= w; dy++) {
            for (int dx = 0; dx + sw <= w; dx++) {
                for (boolean mirror : new boolean[]{false, true}) {
                    if (placementOk(spec, matrix, w, dx, dy, sw, mirror)) return true;
                }
            }
        }
        return false;
    }

    private boolean placementOk(Spec spec, ItemStack[] matrix, int w, int dx, int dy, int sw, boolean mirror) {
        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                ItemStack item = matrix[y * w + x];
                boolean empty = item == null || item.isEmpty();
                int sx = x - dx, sy = y - dy;
                char c = ' ';
                if (sy >= 0 && sy < spec.shape().length && sx >= 0 && sx < sw) {
                    String row = spec.shape()[sy];
                    int col = mirror ? sw - 1 - sx : sx;
                    c = col < row.length() ? row.charAt(col) : ' ';
                }
                if (c == ' ') {
                    if (!empty) return false;
                    continue;
                }
                if (empty) return false;
                Object need = spec.paperSlots().get(c);
                if (need != null) {
                    if (need instanceof DecorType dt) {
                        if (plugin.decor().itemType(item) != dt) return false;
                    } else if (need instanceof PartType pt ? typeOf(item) != pt : materialOf(item) != need) return false;
                } else if (isOurs(item)) {
                    return false;
                }
            }
        }
        return true;
    }
}
