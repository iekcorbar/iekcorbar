package com.iekcorbar.wildlife;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Molehills: when a mole comes up for air, it pushes a fresh pile of dug earth out beside it
 * (a non-persistent ItemDisplay of the zoo:molehill model, like the cask covers). The spot is
 * saved in the chunk's data, so hills stay in the world; moles only dig when no hill exists
 * within 12 blocks, so a lawn keeps one or two, not a minefield.
 */
final class Molehills implements Listener {
    private final WildlifePlugin plugin;
    private final NamespacedKey dataKey, tagKey;
    private final Map<Location, ItemDisplay> hills = new HashMap<>();

    Molehills(WildlifePlugin plugin) {
        this.plugin = plugin;
        dataKey = new NamespacedKey(plugin, "molehills");
        tagKey = new NamespacedKey(plugin, "molehill");
    }

    /** A mole appeared near this spot: dig a hill within five blocks, unless the area is used up. */
    void digNear(Location at) {
        World w = at.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Location h : hills.keySet())
            if (h.getWorld().getUID() == w.getUID() && h.distanceSquared(at) < 144) return;
        for (int i = 0; i < 6; i++) {
            int x = at.getBlockX() + r.nextInt(11) - 5, z = at.getBlockZ() + r.nextInt(11) - 5;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block ground = w.getHighestBlockAt(x, z);
            if (!ground.getType().isSolid() || ground.getType() == Material.SNOW || ground.getType() == Material.ICE) continue;
            Block spot = ground.getRelative(BlockFace.UP);
            if (!spot.getType().isAir()) continue;
            show(spot.getLocation().add(0.5, 0, 0.5));
            save(spot.getChunk());
            return;
        }
    }

    /** Spawn the pile display at this air-block location. */
    private void show(Location at) {
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, en -> {
            en.setPersistent(false);
            en.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            ItemStack look = new ItemStack(Material.PAPER);
            look.setData(DataComponentTypes.ITEM_MODEL, Key.key("zoo", "molehill"));
            en.setItemStack(look);
            en.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            en.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.85f, 0.85f, 0.85f), new Quaternionf()));
        });
        Block up = at.getBlock();
        d.setBrightness(new Display.Brightness(Math.max(up.getLightFromBlocks(), 4), up.getLightFromSky()));
        hills.put(at, d);
    }

    // ------------------------------------------------------------ persistence

    private void save(Chunk c) {
        StringBuilder sb = new StringBuilder();
        for (Location l : hills.keySet()) {
            if (l.getWorld().getUID() != c.getWorld().getUID() || l.getBlockX() >> 4 != c.getX() || l.getBlockZ() >> 4 != c.getZ()) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(l.getBlockX()).append(' ').append(l.getBlockY()).append(' ').append(l.getBlockZ());
        }
        if (sb.isEmpty()) c.getPersistentDataContainer().remove(dataKey);
        else c.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, sb.toString());
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent e) {
        loadChunk(e.getChunk());
    }

    void loadChunk(Chunk c) {
        String data = c.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (data == null) return;
        for (String line : data.split("\n")) {
            try {
                String[] p = line.split(" ");
                Location at = new Location(c.getWorld(), Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]), Integer.parseInt(p[2]) + 0.5);
                if (at.getBlock().getType() != Material.AIR) continue;   // something was placed over it - skip, keep the record
                if (!hills.containsKey(at)) show(at);
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent e) {
        World w = e.getWorld();
        Chunk c = e.getChunk();
        hills.entrySet().removeIf(en -> {
            Location l = en.getKey();
            if (l.getWorld() != w || l.getBlockX() >> 4 != c.getX() || l.getBlockZ() >> 4 != c.getZ()) return false;
            en.getValue().remove();
            return true;
        });
    }

    void removeAll() {
        for (ItemDisplay d : hills.values()) d.remove();
        hills.clear();
        for (World w : plugin.getServer().getWorlds())
            for (Entity en : w.getEntities())
                if (en.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) en.remove();
    }
}
