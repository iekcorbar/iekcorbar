package com.iekcorbar.steamworks;

import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;

import java.util.function.Predicate;

/**
 * Service for other plugins (the Kura copper-golem couriers): "is this block a container that one of our machines
 * delivers into?" - the container under a steam hammer, under or beside a kettle or millstone, and the container
 * a conveyor belt runs into. JDK types only (Predicate&lt;Block&gt;), looked up by plugin name, no shared jar.
 */
final class OutputSpots implements Predicate<Block> {
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final SteamworksPlugin plugin;

    OutputSpots(SteamworksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean test(Block b) {
        if (b == null || !(b.getState(false) instanceof Container)) return false;
        Kinetics k = plugin.kinetics();
        KNode above = k.at(b.getRelative(BlockFace.UP));
        if (above != null && above.type == PartType.STEAM_HAMMER) return true;
        for (BlockFace f : new BlockFace[]{BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            KNode n = k.at(b.getRelative(f.getOppositeFace()));   // the machine sits on the other side of the container
            if (n != null && (n.type == PartType.KETTLE || n.type == PartType.MILLSTONE)) return true;
        }
        for (BlockFace f : SIDES) {
            KNode n = k.at(b.getRelative(f));
            if (n == null || n.type != PartType.BELT) continue;
            Axis along = f == BlockFace.NORTH || f == BlockFace.SOUTH ? Axis.Z : Axis.X;
            if (n.axis == along) return true;
        }
        return false;
    }
}
