package com.iekcorbar.steamworks;

import com.iekcorbar.steamworks.trains.CarType;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the recipe viewer (Zukan plugin) asks for: every Steamworks item and every recipe, as plain JDK
 * types (Map/List/ItemStack), so neither plugin needs the other on its classpath. Called when Zukan builds
 * its catalog, never on a timer.
 */
final class ZukanData implements Supplier<Map<String, Object>> {
    private static final int MAX_TAG_ITEMS = 24;
    private final SteamworksPlugin plugin;

    ZukanData(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> get() {
        PartItems items = plugin.items();
        List<ItemStack> all = new ArrayList<>();
        for (PartType t : PartType.values()) all.add(items.refreshModel(items.create(t, 1)));
        for (MaterialType t : MaterialType.values()) all.add(items.refreshModel(items.createMaterial(t, 1)));
        for (CarType t : CarType.values()) all.add(items.refreshModel(items.createCar(t, 1)));
        if (plugin.ores() != null) for (OreType t : OreType.values()) all.add(plugin.ores().item(t, 1));
        if (plugin.decor() != null) for (DecorType t : DecorType.values()) all.add(plugin.decor().item(t, 1));
        if (plugin.shapes() != null) {
            for (DecorType t : DecorType.values()) {
                all.add(plugin.shapes().item(t, true, 1));
                all.add(plugin.shapes().item(t, false, 1));
            }
        }

        List<Map<String, Object>> recipes = new ArrayList<>();
        crafting(recipes, items);
        machine(recipes, "Millstone", plugin.millstones().recipes(), item(PartType.MILLSTONE), true);
        machine(recipes, "Steam Hammer", plugin.hammers().recipes, item(PartType.STEAM_HAMMER), false);
        machine(recipes, "Crushing Wheels", plugin.crushers().recipes, item(PartType.CRUSHING_WHEEL), true);
        machine(recipes, "Bellows Fan: Washing", plugin.fans().washing, item(PartType.BELLOWS_FAN), false);
        machine(recipes, "Bellows Fan: Haunting", plugin.fans().haunting, item(PartType.BELLOWS_FAN), false);
        mixer(recipes);

        // recipes are registered before FMM's models are ready, so their stacks may lack the item model the catalogue items have;
        // Zukan matches on material + model, so without this the recipe is not found for its own result
        for (Map<String, Object> m : recipes) {
            m.put("outputs", fresh((List<ItemStack>) m.get("outputs")));
            List<List<ItemStack>> ins = new ArrayList<>();
            for (List<ItemStack> cell : (List<List<ItemStack>>) m.get("inputs")) ins.add(fresh(cell));
            m.put("inputs", ins);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", all);
        out.put("recipes", recipes);
        out.put("replaceCrafting", true); // our workbench recipes use generic paper slots in Bukkit; these are the real ones
        return out;
    }

    private List<ItemStack> fresh(List<ItemStack> stacks) {
        List<ItemStack> l = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) l.add(plugin.items().refreshModel(s));
        return l;
    }

    private ItemStack item(PartType t) {
        return plugin.items().refreshModel(plugin.items().create(t, 1));
    }

    // ------------------------------------------------------------ crafting (workbench)

    private void crafting(List<Map<String, Object>> out, PartItems items) {
        for (PartItems.CraftRecipe r : items.craftRecipes.values()) {
            List<List<ItemStack>> cells = new ArrayList<>();
            for (int i = 0; i < 9; i++) cells.add(List.of());
            for (int row = 0; row < r.shape().length && row < 3; row++) {
                String line = r.shape()[row];
                for (int col = 0; col < line.length() && col < 3; col++) {
                    Object ing = r.ingredients().get(line.charAt(col));
                    if (ing != null) cells.set(row * 3 + col, choices(ing));
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", "Steamworks Crafting");
            m.put("icon", new ItemStack(Material.CRAFTING_TABLE));
            m.put("station", new ItemStack(Material.CRAFTING_TABLE));
            m.put("grid", true);
            m.put("inputs", cells);
            m.put("outputs", List.of(r.result().clone()));
            m.put("info", List.of());
            out.add(m);
        }
    }

    /** One crafting-slot ingredient (as kept in PartItems.addRecipe) as the items that fit. */
    private List<ItemStack> choices(Object ing) {
        PartItems items = plugin.items();
        List<ItemStack> l = new ArrayList<>();
        if (ing instanceof Material mat) l.add(new ItemStack(mat));
        else if (ing instanceof PartType t) l.add(items.refreshModel(items.create(t, 1)));
        else if (ing instanceof MaterialType t) l.add(items.refreshModel(items.createMaterial(t, 1)));
        else if (ing instanceof DecorType t) l.add(plugin.decor().item(t, 1));
        else if (ing instanceof RecipeChoice.MaterialChoice mc) {
            for (Material m : mc.getChoices()) {
                l.add(new ItemStack(m));
                if (l.size() >= MAX_TAG_ITEMS) break;
            }
        }
        return l;
    }

    // ------------------------------------------------------------ machines

    private void machine(List<Map<String, Object>> out, String category, MachineRecipes book, ItemStack station, boolean timed) {
        for (MachineRecipes.Recipe r : book.all()) {
            List<ItemStack> in = input(r);
            if (in.isEmpty()) continue;
            List<ItemStack> outs = new ArrayList<>();
            List<String> info = new ArrayList<>();
            if (timed) info.add("Work: " + r.time() + " (" + String.format(Locale.ROOT, "%.1f", r.time() / 20.0) + " s at full speed)");
            for (MachineRecipes.Output o : r.outputs()) {
                ItemStack s;
                try {
                    s = o.create(plugin.items());
                } catch (RuntimeException e) {
                    continue;
                }
                outs.add(s);
                if (o.chance() < 1) info.add(o.name() + ": " + Math.round(o.chance() * 100) + "% chance");
            }
            if (outs.isEmpty()) continue;
            add(out, category, station, List.of(in), outs, info);
        }
    }

    private List<ItemStack> input(MachineRecipes.Recipe r) {
        List<ItemStack> l = new ArrayList<>();
        if (r.sw() != null) l.add(plugin.items().createMaterial(r.sw(), 1));
        else if (r.ore() != null) l.add(plugin.ores().item(r.ore(), 1));
        else if (r.kitchen() != null) {
            ItemStack k = KitchenBridge.create(r.kitchen(), 1);
            if (k != null) l.add(k);
        } else if (r.tag() != null) {
            for (Material m : r.tag().getValues()) {
                l.add(new ItemStack(m));
                if (l.size() >= MAX_TAG_ITEMS) break;
            }
        } else if (r.material() != null) l.add(new ItemStack(r.material()));
        return l;
    }

    private void mixer(List<Map<String, Object>> out) {
        ItemStack kettle = plugin.items().refreshModel(plugin.items().create(PartType.KETTLE, 1));
        for (Mixers.MixRecipe r : plugin.mixers().recipes()) {
            List<List<ItemStack>> cells = new ArrayList<>();
            for (Mixers.Ingredient ing : r.inputs()) {
                List<ItemStack> l = new ArrayList<>();
                if (ing.sw() != null) l.add(plugin.items().createMaterial(ing.sw(), ing.count()));
                else if (ing.kitchen() != null) {
                    ItemStack k = KitchenBridge.create(ing.kitchen(), ing.count());
                    if (k != null) l.add(k);
                } else if (ing.tag() != null) {
                    for (Material m : ing.tag().getValues()) {
                        l.add(new ItemStack(m, ing.count()));
                        if (l.size() >= MAX_TAG_ITEMS) break;
                    }
                } else if (ing.material() != null) l.add(new ItemStack(ing.material(), ing.count()));
                if (l.isEmpty()) {
                    cells = null;
                    break;
                }
                cells.add(l);
            }
            if (cells == null) continue;
            List<ItemStack> outs = new ArrayList<>();
            List<String> info = new ArrayList<>();
            info.add("Mixer on top of the kettle");
            if (r.water() > 0) info.add("Water: " + r.water() + " quarter bucket" + (r.water() == 1 ? "" : "s"));
            if (r.heat()) info.add("Needs fire, lava or magma under the kettle");
            info.add("Work: " + r.time());
            for (MachineRecipes.Output o : r.outputs()) {
                try {
                    outs.add(o.create(plugin.items()));
                } catch (RuntimeException ignored) {
                    // Kitchen item without the Kitchen plugin
                }
            }
            if (outs.isEmpty()) continue;
            add(out, "Kettle and Mixer", kettle, cells, outs, info);
        }
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
