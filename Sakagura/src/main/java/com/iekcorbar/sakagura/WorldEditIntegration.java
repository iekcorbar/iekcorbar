package com.iekcorbar.sakagura;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * WorldEdit/WorldGuard integration for competition areas.
 * Uses WorldEdit selections to define areas and WorldGuard regions for protection.
 * Both plugins are optional soft dependencies - falls back to built-in area system if not present.
 */
final class WorldEditIntegration {
    private final Sakagura plugin;

    WorldEditIntegration(Sakagura plugin) {
        this.plugin = plugin;
    }

    /** Check if WorldEdit is available. */
    boolean worldEditAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null;
    }

    /** Check if WorldGuard is available. */
    boolean worldGuardAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
    }

    /** Get the player's WorldEdit selection as a region. */
    Region getSelection(Player p) {
        if (!worldEditAvailable()) return null;
        try {
            com.sk89q.worldedit.bukkit.WorldEditPlugin we = (com.sk89q.worldedit.bukkit.WorldEditPlugin)
                    plugin.getServer().getPluginManager().getPlugin("WorldEdit");
            com.sk89q.worldedit.session.SessionManager sm = we.getWorldEdit().getSessionManager();
            com.sk89q.worldedit.LocalSession session = sm.get(BukkitAdapter.adapt(p));
            if (session == null) return null;
            return session.getSelection(BukkitAdapter.adapt(p.getWorld()));
        } catch (Exception e) {
            plugin.getLogger().warning("WorldEdit selection error: " + e.getMessage());
            return null;
        }
    }

    /** Create a WorldGuard region from a WorldEdit selection. */
    boolean createRegion(Player p, String name, boolean protect) {
        if (!worldGuardAvailable() || !worldEditAvailable()) return false;
        Region selection = getSelection(p);
        if (selection == null) return false;

        try {
            World world = p.getWorld();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) return false;

            // Remove existing region with same name
            if (manager.hasRegion(name)) {
                manager.removeRegion(name);
            }

            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(name, min, max);

            // Set flags
            if (protect) {
                region.setFlag(Flags.BUILD, StateFlag.State.DENY);
                region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
                region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
                region.setFlag(Flags.PVP, StateFlag.State.DENY);
                region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
            }
            // Allow entry
            region.setFlag(Flags.ENTRY, StateFlag.State.ALLOW);

            manager.addRegion(region);
            manager.save();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("WorldGuard region creation error: " + e.getMessage());
            return false;
        }
    }

    /** Remove a WorldGuard region. */
    boolean removeRegion(World world, String name) {
        if (!worldGuardAvailable()) return false;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null || !manager.hasRegion(name)) return false;
            manager.removeRegion(name);
            manager.save();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("WorldGuard region removal error: " + e.getMessage());
            return false;
        }
    }

    /** Check if a location is inside a WorldGuard region. */
    boolean isInRegion(Location loc, String regionName) {
        if (!worldGuardAvailable()) return true; // no WG = no restriction
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(loc.getWorld()));
            if (manager == null) return true;
            ProtectedRegion region = manager.getRegion(regionName);
            if (region == null) return true;
            return region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        } catch (Exception e) {
            return true;
        }
    }

    /** Get region bounds as a string. */
    String regionBounds(World world, String name) {
        if (!worldGuardAvailable()) return "N/A";
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null || !manager.hasRegion(name)) return "not found";
            ProtectedRegion region = manager.getRegion(name);
            return region.getMinimumPoint() + " to " + region.getMaximumPoint();
        } catch (Exception e) {
            return "error";
        }
    }

    /** Get a formatted status message. */
    Component status() {
        StringBuilder sb = new StringBuilder();
        sb.append("WorldEdit: ").append(worldEditAvailable() ? "available" : "not found");
        sb.append(" | WorldGuard: ").append(worldGuardAvailable() ? "available" : "not found");
        return Component.text(sb.toString(), NamedTextColor.GRAY);
    }
}
