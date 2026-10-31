package com.iekcorbar.wildlife;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animal Rescue: extremely rare trapped animals spawn in the world.
 * Players can free them for achievements and a bonus.
 */
final class Rescue implements Listener {
    private final WildlifePlugin plugin;
    private final NamespacedKey trapKey;
    private final NamespacedKey rescueCountKey;
    private final Map<UUID, Integer> rescueCounts = new HashMap<>();

    // trap types: name -> visual block
    private static final String[] TRAP_NAMES = {"snare", "cage", "net", "pit", "bear_trap"};
    private static final Material[] TRAP_BLOCKS = {Material.STRING, Material.IRON_BARS, Material.COBWEB, Material.DIRT, Material.ANVIL};

    Rescue(WildlifePlugin plugin) {
        this.plugin = plugin;
        this.trapKey = new NamespacedKey(plugin, "trap");
        this.rescueCountKey = new NamespacedKey(plugin, "rescue_count");
    }

    /** Attempt to spawn a trapped animal near a player. Extremely rare. */
    void trySpawn(Player p, ThreadLocalRandom r) {
        // extremely rare: 0.05% chance per tick per player
        if (r.nextDouble() > 0.0005) return;
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) return;
        double a = r.nextDouble() * Math.PI * 2, d = 30 + r.nextDouble() * 40;
        int x = (int) (p.getLocation().getX() + Math.cos(a) * d);
        int z = (int) (p.getLocation().getZ() + Math.sin(a) * d);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        int y = w.getHighestBlockYAt(x, z);
        Location at = new Location(w, x + 0.5, y + 1, z + 0.5);
        Block ground = w.getBlockAt(x, y, z);
        if (!ground.getType().isSolid()) return;

        // pick a random species
        Species sp = Species.values()[r.nextInt(Species.values().length)];
        if (!sp.animal()) return;

        // pick trap type
        int trapIdx = r.nextInt(TRAP_NAMES.length);
        String trapName = TRAP_NAMES[trapIdx];
        Material trapBlock = TRAP_BLOCKS[trapIdx];

        spawnTrap(at, sp, trapName, trapBlock);
    }

    private void spawnTrap(Location at, Species sp, String trapName, Material trapBlock) {
        World w = at.getWorld();
        // place trap block on ground
        Block trapLoc = at.clone().subtract(0, 1, 0).getBlock();
        if (trapLoc.getType() == Material.AIR) trapLoc.setType(trapBlock, false);

        // interaction entity for clicking
        Interaction inter = w.spawn(at, Interaction.class, e -> {
            e.setPersistent(false);
            e.setInteractionWidth(1.0f);
            e.setInteractionHeight(1.0f);
            e.getPersistentDataContainer().set(trapKey, PersistentDataType.STRING, sp.name() + ":" + trapName);
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction inter)) return;
        String data = inter.getPersistentDataContainer().get(trapKey, PersistentDataType.STRING);
        if (data == null) return;
        e.setCancelled(true);

        String[] parts = data.split(":");
        Species sp;
        try { sp = Species.valueOf(parts[0]); }
        catch (Exception ex) { return; }

        // free the animal
        Location at = inter.getLocation();
        World w = at.getWorld();
        inter.remove();

        // remove trap block
        Block trapBlock = at.clone().subtract(0, 1, 0).getBlock();
        if (trapBlock.getType() != Material.AIR && trapBlock.getType() != Material.BEDROCK) {
            trapBlock.setType(Material.AIR, false);
        }

        // effects
        w.spawnParticle(Particle.HEART, at.clone().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0);
        w.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

        // grant achievement
        Player p = e.getPlayer();
        int count = rescueCounts.getOrDefault(p.getUniqueId(), 0) + 1;
        rescueCounts.put(p.getUniqueId(), count);

        // persist count
        p.getPersistentDataContainer().set(rescueCountKey, PersistentDataType.INTEGER, count);

        // grant achievements based on count
        if (count == 1) Achievements.grant(plugin, p, "first_rescue");
        if (count == 5) Achievements.grant(plugin, p, "conservationist");
        if (count == 10) Achievements.grant(plugin, p, "master_conservationist");

        p.sendActionBar(net.kyori.adventure.text.Component.text("You rescued a " + sp.displayName + "!", net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    /** Load rescue counts from player data. */
    void loadPlayer(Player p) {
        Integer c = p.getPersistentDataContainer().get(rescueCountKey, PersistentDataType.INTEGER);
        if (c != null) rescueCounts.put(p.getUniqueId(), c);
    }
}
