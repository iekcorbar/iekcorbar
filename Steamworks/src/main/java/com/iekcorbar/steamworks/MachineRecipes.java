package com.iekcorbar.steamworks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One machine's recipe list from machines.yml. Input: a material, an item tag
 * ("tag:wool"), a Steamworks material ("sw:iron_plate") or a Kitchen item
 * ("kitchen:rice", needs the Kitchen plugin). Output lines:
 * "MATERIAL|sw:id|kitchen:id [amount] [chance%]".
 */
final class MachineRecipes {
    record Output(Material material, MaterialType sw, String kitchen, int amount, double chance) {
        ItemStack create(PartItems items) {
            if (kitchen != null) {
                ItemStack k = KitchenBridge.create(kitchen, amount);
                if (k != null) return k;
                kitchenMissing();
            }
            return sw != null ? items.createMaterial(sw, amount) : new ItemStack(material, amount);
        }

        private static void kitchenMissing() {
            throw new IllegalStateException("Kitchen plugin was removed - run /sw reload");
        }

        String name() {
            if (kitchen != null) return kitchen.replace('_', ' ');
            return sw != null ? sw.displayName : material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    record Recipe(Material material, Tag<Material> tag, MaterialType sw, OreType ore, String kitchen, int time, List<Output> outputs) {
        boolean matches(ItemStack i, MaterialType held) {
            if (ore != null || kitchen != null) return false; // ore blocks and Kitchen items are matched in find()
            if (sw != null) return sw == held;
            if (held != null) return false;
            return tag != null ? tag.isTagged(i.getType()) : i.getType() == material;
        }
    }

    private final SteamworksPlugin plugin;
    private final String section;
    private final List<Recipe> recipes = new ArrayList<>();
    private final Map<Material, Recipe> cache = new HashMap<>();

    MachineRecipes(SteamworksPlugin plugin, String section) {
        this.plugin = plugin;
        this.section = section;
    }

    /** Reads this machine's section; a machines.yml from an older version without it gets the built-in defaults. */
    void load(File file) {
        recipes.clear();
        cache.clear();
        for (Map<?, ?> m : section(plugin, file, section)) {
            try {
                String in = String.valueOf(m.get("input")).trim();
                Material mat = null;
                Tag<Material> tag = null;
                MaterialType sw = null;
                OreType ore = null;
                String kitchen = null;
                String low = in.toLowerCase(Locale.ROOT);
                if (low.startsWith("tag:")) {
                    tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(low.substring(4).trim()), Material.class);
                    if (tag == null) throw new IllegalArgumentException("unknown item tag " + in);
                } else if (low.startsWith("ore:")) {
                    ore = OreType.byId(low.substring(4).trim());
                    if (ore == null) throw new IllegalArgumentException("unknown Steamworks ore " + in);
                } else if (low.startsWith("sw:")) {
                    sw = MaterialType.byId(low.substring(3).trim());
                    if (sw == null) throw new IllegalArgumentException("unknown Steamworks material " + in);
                } else if (low.startsWith("kitchen:")) {
                    kitchen = low.substring(8).trim();
                    if (KitchenBridge.create(kitchen, 1) == null) throw new IllegalArgumentException("Kitchen plugin missing or unknown item " + in);
                } else {
                    mat = Material.matchMaterial(in);
                    if (mat == null) throw new IllegalArgumentException("unknown material " + in);
                }
                Object t = m.get("time");
                int time = t instanceof Number num ? num.intValue() : 100;
                List<Output> outs = new ArrayList<>();
                Object o = m.get("output");
                if (o instanceof List<?> l) for (Object e : l) outs.add(parseOutput(String.valueOf(e)));
                else if (o != null) outs.add(parseOutput(String.valueOf(o)));
                if (outs.isEmpty()) throw new IllegalArgumentException("no output");
                recipes.add(new Recipe(mat, tag, sw, ore, kitchen, Math.max(1, time), outs));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("machines.yml: skipping " + section + " recipe " + m + " (" + e.getMessage() + ")");
            }
        }
        plugin.getLogger().info("Loaded " + recipes.size() + " " + section.replace('_', ' ') + " recipe(s).");
    }

    /** A machines.yml section; one missing from an older server copy comes from the jar's built-in file. */
    static List<Map<?, ?>> section(SteamworksPlugin plugin, File file, String section) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (y.contains(section)) return y.getMapList(section);
        plugin.getLogger().info("machines.yml has no '" + section + "' section - using the built-in recipes "
                + "(delete machines.yml to get a fresh copy with every section).");
        return defaults(plugin).getMapList(section);
    }

    private static YamlConfiguration defaults(SteamworksPlugin plugin) {
        try (Reader r = new InputStreamReader(plugin.getResource("machines.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    static Output parseOutput(String s) {
        String[] p = s.trim().split("\\s+");
        Material mat = null;
        MaterialType sw = null;
        String kitchen = null;
        if (p[0].toLowerCase(Locale.ROOT).startsWith("kitchen:")) {
            kitchen = p[0].substring(8).toLowerCase(Locale.ROOT);
            if (KitchenBridge.create(kitchen, 1) == null) throw new IllegalArgumentException("Kitchen plugin missing or unknown item " + p[0]);
        } else if (p[0].toLowerCase(Locale.ROOT).startsWith("sw:")) {
            sw = MaterialType.byId(p[0].substring(3));
            if (sw == null) throw new IllegalArgumentException("unknown Steamworks material " + p[0]);
        } else {
            mat = Material.matchMaterial(p[0]);
            if (mat == null) throw new IllegalArgumentException("unknown material " + p[0]);
        }
        int amount = 1;
        double chance = 1;
        for (int i = 1; i < p.length; i++) {
            if (p[i].endsWith("%")) chance = Double.parseDouble(p[i].substring(0, p[i].length() - 1)) / 100.0;
            else amount = Integer.parseInt(p[i]);
        }
        return new Output(mat, sw, kitchen, amount, chance);
    }

    List<Recipe> all() {
        return recipes;
    }

    /** The recipe for this item, or null. Parts never match; Steamworks materials only match "sw:" inputs. */
    Recipe find(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        PartItems items = plugin.items();
        if (items.typeOf(item) != null) return null;
        String kitchen = KitchenBridge.idOf(item);
        if (kitchen != null) {
            for (Recipe r : recipes) if (kitchen.equals(r.kitchen())) return r;
            return null;
        }
        OreType ore = plugin.ores() == null ? null : plugin.ores().itemType(item);
        if (ore != null) {
            for (Recipe r : recipes) if (r.ore() == ore) return r;
            return null;
        }
        MaterialType held = items.materialOf(item);
        if (held != null) {
            for (Recipe r : recipes) if (r.matches(item, held)) return r;
            return null;
        }
        return cache.computeIfAbsent(item.getType(), type -> {
            for (Recipe r : recipes) if (r.matches(item, null)) return r;
            return null;
        });
    }
}
