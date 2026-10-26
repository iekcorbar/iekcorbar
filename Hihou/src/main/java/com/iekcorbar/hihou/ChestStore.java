package com.iekcorbar.hihou;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Per-chest loot, stored in plugins/Hihou/chests.json keyed by "world:x:y:z" (like Satchel's bag
 * data - every placed chest is unique, which is the whole point). ItemStack lists are YAML-hostile
 * (not ConfigurationSerializable), so each chest is written as one Bukkit-object Base64 blob -
 * the standard Bukkit trick that survives enchantments, head textures and PDC across restarts.
 */
final class ChestStore {
    private final HihouPlugin plugin;
    private final YamlConfiguration y = new YamlConfiguration();
    private boolean dirty;

    ChestStore(HihouPlugin plugin) {
        this.plugin = plugin;
        File f = new File(plugin.getDataFolder(), "chests.json");
        if (f.exists()) {
            try {
                y.load(f);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read chests.json: " + e.getMessage());
            }
        }
    }

    static String id(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    List<ItemStack> loot(String id) {
        List<ItemStack> out = new ArrayList<>();
        String blob = y.getString("chests." + id);
        if (blob == null || blob.isEmpty()) return out;
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(blob)))) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                Object o = in.readObject();
                out.add(o instanceof ItemStack it && !it.getType().isAir() ? it : null);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not decode loot for " + id + ": " + e.getMessage());
        }
        return out;
    }

    void setLoot(String id, List<ItemStack> loot) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bo)) {
            out.writeInt(loot.size());
            for (ItemStack it : loot) out.writeObject(it == null ? new ItemStack(org.bukkit.Material.AIR) : it);
            y.set("chests." + id, Base64.getEncoder().encodeToString(bo.toByteArray()));
            dirty = true;
        } catch (Exception e) {
            plugin.getLogger().severe("Could not encode loot for " + id + ": " + e.getMessage());
        }
    }

    void clear(String id) {
        if (y.get("chests." + id) != null) {
            y.set("chests." + id, null);
            dirty = true;
        }
    }

    void flush() {
        if (!dirty) return;
        File f = new File(plugin.getDataFolder(), "chests.json");
        try {
            f.getParentFile().mkdirs();
            y.save(f);
            dirty = false;
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save chests.json: " + e.getMessage());
        }
    }
}
