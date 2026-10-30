package com.iekcorbar.sakagura;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Competition Area: a designated 3D region where brewing competitions take place.
 * Admin sets two corner positions; players must be inside to submit drinks.
 * Areas persist in competitions.yml and can be reused across competitions.
 */
final class CompetitionArea {
    private final Sakagura plugin;
    private final File file;
    private YamlConfiguration data;

    // areas: name -> [corner1, corner2]
    private final Map<String, Area> areas = new ConcurrentHashMap<>();
    private String activeArea; // name of the area currently in use

    static final class Area {
        final String name;
        final String world;
        final double x1, y1, z1;
        final double x2, y2, z2;

        Area(String name, String world, double x1, double y1, double z1, double x2, double y2, double z2) {
            this.name = name;
            this.world = world;
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
            this.z2 = Math.max(z1, z2);
        }

        boolean contains(Location loc) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
            return loc.getX() >= x1 && loc.getX() <= x2
                    && loc.getY() >= y1 && loc.getY() <= y2
                    && loc.getZ() >= z1 && loc.getZ() <= z2;
        }

        int volume() {
            return (int) ((x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1));
        }

        String bounds() {
            return world + " (" + (int)x1 + "," + (int)y1 + "," + (int)z1 + ") to (" + (int)x2 + "," + (int)y2 + "," + (int)z2 + ")";
        }
    }

    CompetitionArea(Sakagura plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "competitions.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            data = new YamlConfiguration();
            return;
        }
        data = YamlConfiguration.loadConfiguration(file);
        activeArea = data.getString("active-area", null);
        ConfigurationSection sec = data.getConfigurationSection("areas");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                ConfigurationSection a = sec.getConfigurationSection(name);
                if (a == null) continue;
                String world = a.getString("world");
                double x1 = a.getDouble("x1"), y1 = a.getDouble("y1"), z1 = a.getDouble("z1");
                double x2 = a.getDouble("x2"), y2 = a.getDouble("y2"), z2 = a.getDouble("z2");
                areas.put(name, new Area(name, world, x1, y1, z1, x2, y2, z2));
            }
        }
    }

    private void save() {
        if (data == null) data = new YamlConfiguration();
        // preserve competition data by reloading first
        data = YamlConfiguration.loadConfiguration(file);
        data.set("active-area", activeArea);
        // Clear old areas first
        data.set("areas", null);
        for (Map.Entry<String, Area> e : areas.entrySet()) {
            Area a = e.getValue();
            String path = "areas." + e.getKey();
            data.set(path + ".world", a.world);
            data.set(path + ".x1", a.x1);
            data.set(path + ".y1", a.y1);
            data.set(path + ".z1", a.z1);
            data.set(path + ".x2", a.x2);
            data.set(path + ".y2", a.y2);
            data.set(path + ".z2", a.z2);
        }
        try {
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save competition areas: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ area management

    /** Set a corner for area creation. Returns the current corner count (1 or 2). */
    int setCorner(String name, Location loc, int cornerNum) {
        if (cornerNum == 1) {
            // store first corner temporarily
            data = YamlConfiguration.loadConfiguration(file);
            data.set("corner1." + name + ".world", loc.getWorld().getName());
            data.set("corner1." + name + ".x", loc.getX());
            data.set("corner1." + name + ".y", loc.getY());
            data.set("corner1." + name + ".z", loc.getZ());
            try { data.save(file); } catch (Exception ignored) { }
            return 1;
        } else {
            // read first corner
            data = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection c1 = data.getConfigurationSection("corner1." + name);
            if (c1 == null) return 0; // no first corner set
            String world = c1.getString("world");
            if (!world.equals(loc.getWorld().getName())) return -1; // different world
            double x1 = c1.getDouble("x"), y1 = c1.getDouble("y"), z1 = c1.getDouble("z");
            Area area = new Area(name, world, x1, y1, z1, loc.getX(), loc.getY(), loc.getZ());
            areas.put(name, area);
            // clean up temp corner
            data.set("corner1." + name, null);
            save();
            return 2;
        }
    }

    boolean hasFirstCorner(String name) {
        if (!file.exists()) return false;
        data = YamlConfiguration.loadConfiguration(file);
        return data.getConfigurationSection("corner1." + name) != null;
    }

    boolean createArea(String name, Location c1, Location c2) {
        if (!c1.getWorld().getName().equals(c2.getWorld().getName())) return false;
        Area area = new Area(name, c1.getWorld().getName(),
                c1.getX(), c1.getY(), c1.getZ(),
                c2.getX(), c2.getY(), c2.getZ());
        areas.put(name, area);
        save();
        return true;
    }

    boolean removeArea(String name) {
        if (!areas.containsKey(name)) return false;
        areas.remove(name);
        if (name.equals(activeArea)) activeArea = null;
        save();
        return true;
    }

    void setActiveArea(String name) {
        if (name == null || areas.containsKey(name)) {
            activeArea = name;
            save();
        }
    }

    String activeAreaName() { return activeArea; }

    Area activeArea() {
        if (activeArea == null) return null;
        return areas.get(activeArea);
    }

    boolean isInActiveArea(Location loc) {
        Area a = activeArea();
        if (a == null) return true; // no area set = no restriction
        return a.contains(loc);
    }

    List<String> areaNames() { return new ArrayList<>(areas.keySet()); }

    Area getArea(String name) { return areas.get(name); }

    // ------------------------------------------------------------ listing

    List<String> listAreas() {
        List<String> lines = new ArrayList<>();
        for (Area a : areas.values()) {
            String active = a.name.equals(activeArea) ? " [ACTIVE]" : "";
            lines.add(a.name + active + " - " + a.bounds() + " (volume: " + a.volume() + ")");
        }
        return lines;
    }
}
