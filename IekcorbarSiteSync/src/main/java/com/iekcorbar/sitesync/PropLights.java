package com.iekcorbar.sitesync;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Real light for lamp props (props.yml "light: <0-15>"): an invisible vanilla
 * LIGHT block is put in the block the lamp occupies. FreeMinecraftModels has no
 * "prop removed" event, so every light we placed is remembered (lights.yml) and
 * checked: right after a lamp item drops (picked up) and every 30 seconds. A
 * light whose lamp prop is gone is removed again.
 */
class PropLights {

    private static final NamespacedKey FMM_PROP = NamespacedKey.fromString("freeminecraftmodels:prop");

    private final IekcorbarSiteSync plugin;
    private final File file;
    private final Set<String> lights = new LinkedHashSet<>(); // "world,x,y,z"
    private Predicate<String> isLampModel = id -> false;

    PropLights(IekcorbarSiteSync plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lights.yml");
    }

    void enable(Predicate<String> isLampModel) {
        this.isLampModel = isLampModel;
        lights.addAll(YamlConfiguration.loadConfiguration(file).getStringList("lights"));
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, 600L, 600L);
    }

    /** Puts a light in this block if it's free (air or still water). */
    void place(Block block, int level) {
        Material type = block.getType();
        boolean water = type == Material.WATER && block.getBlockData() instanceof Levelled l && l.getLevel() == 0;
        if (!type.isAir() && !water && type != Material.LIGHT) return;
        org.bukkit.block.data.type.Light data = (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
        data.setLevel(Math.max(1, Math.min(15, level)));
        data.setWaterlogged(water || (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()));
        block.setBlockData(data, false);
        if (lights.add(key(block))) save();
    }

    /** Checks the lights around a spot (a lamp was just picked up there). */
    void checkNear(Location loc) {
        boolean changed = false;
        for (String k : new ArrayList<>(lights)) {
            Block b = block(k);
            if (b == null || b.getWorld() != loc.getWorld()) continue;
            if (b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(loc) <= 9) changed |= check(k, b);
        }
        if (changed) save();
    }

    private void checkAll() {
        boolean changed = false;
        for (String k : new ArrayList<>(lights)) {
            Block b = block(k);
            if (b == null) { // world gone
                lights.remove(k);
                changed = true;
                continue;
            }
            changed |= check(k, b);
        }
        if (changed) save();
    }

    /** True if the record was dropped. Only judges chunks whose entities are loaded. */
    private boolean check(String k, Block b) {
        World world = b.getWorld();
        if (!world.isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) return false;
        if (b.getType() != Material.LIGHT) { // replaced by a block, water, ...
            lights.remove(k);
            return true;
        }
        if (!b.getChunk().isEntitiesLoaded()) return false;
        Location center = b.getLocation().add(0.5, 0.5, 0.5);
        boolean lampThere = !world.getNearbyEntities(center, 0.75, 0.75, 0.75, e -> e instanceof ArmorStand stand
                && isLampModel.test(stand.getPersistentDataContainer().get(FMM_PROP, PersistentDataType.STRING))).isEmpty();
        if (lampThere) return false;
        boolean water = b.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
        b.setType(water ? Material.WATER : Material.AIR, false);
        lights.remove(k);
        return true;
    }

    private static String key(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static Block block(String k) {
        String[] p = k.split(",");
        if (p.length != 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.options().setHeader(java.util.List.of("Invisible light blocks placed for lamp props - managed by the plugin, don't edit."));
        cfg.set("lights", new ArrayList<>(lights));
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Props] Could not save lights.yml: " + e.getMessage());
        }
    }
}
