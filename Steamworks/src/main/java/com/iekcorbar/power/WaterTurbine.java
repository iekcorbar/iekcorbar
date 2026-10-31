package com.iekcorbar.power;

import com.iekcorbar.steamworks.SteamworksPlugin;

import com.iekcorbar.steamworks.api.CustomNode;
import com.iekcorbar.steamworks.api.CustomPart;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;

import java.util.EnumSet;

/**
 * Water Turbine: one block that runs on river current. Water must touch both shaft ends;
 * flowing water on either side earns a quarter more than a stagnant stretch.
 */
public final class WaterTurbine implements CustomPart {
    private final SteamworksPlugin plugin;

    public WaterTurbine(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String id() { return "pow_water_turbine"; }
    @Override public String displayName() { return "Water Turbine"; }
    @Override public String[] lore() {
        return new String[]{"A brass drum turned by flowing water.", "Place it IN the current: water must touch both ends."};
    }
    @Override public String ownerPlugin() { return "SteamworksPower"; }
    @Override public Material particleBlock() { return Material.COPPER_BLOCK; }

    @Override public double capacity() { return plugin.getConfig().getDouble("turbine.capacity", 64); }

    @Override
    public double generatedSpeed(CustomNode n) {
        // the shaft takes both axis ends: the current must run past the drum's sides
        int water = 0, flowing = 0;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            if (CustomPart.axisOf(f) == n.axis()) continue;
            Levelled w = waterAt(n, f);
            if (w == null) continue;
            water++;
            if (w.getLevel() < w.getMaximumLevel()) flowing++;
        }
        if (flowing == 0) return water >= 2 ? plugin.getConfig().getDouble("turbine.rpm", 12) * 0.5 : 0;
        return plugin.getConfig().getDouble("turbine.rpm", 12) * (flowing >= 2 ? 1.25 : 1.0);
    }

    private static Levelled waterAt(CustomNode n, BlockFace f) {
        Block b = n.block().getRelative(f);
        return b.getType() == Material.WATER && b.getBlockData() instanceof Levelled w ? w : null;
    }

    @Override
    public void tick(CustomNode n, long tick) {
        if (n.speed() == 0 || tick % 40 != 0) return;
        if (n.world().getNearbyPlayers(n.center(), 20).isEmpty()) return;
        n.world().spawnParticle(Particle.SPLASH, n.center().add(0, -0.45, 0), 2, 0.15, 0.05, 0.15, 0.01f);
    }
}
