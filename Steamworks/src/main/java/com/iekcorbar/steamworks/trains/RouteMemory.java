package com.iekcorbar.steamworks.trains;

import org.bukkit.block.BlockFace;

import java.util.HashMap;
import java.util.Map;

/**
 * One train's switch memory (0.3.44): the exits its front wheel actually took, per rail block
 * and entry edge. The cars behind replay those choices, so a redstone flip mid-train can never
 * split it. Lives as long as the route it belongs to; cleared when a train replans or is cut.
 */
public final class RouteMemory implements RailWalker.Route {
    private static final int FACES = BlockFace.values().length;
    private final Map<Long, BlockFace[]> exits = new HashMap<>();

    @Override
    public BlockFace committedExit(long key, BlockFace entry) {
        BlockFace[] a = exits.get(key);
        return a == null ? null : a[entry.ordinal()];
    }

    @Override
    public void commitExit(long key, BlockFace entry, BlockFace exit) {
        exits.computeIfAbsent(key, k -> new BlockFace[FACES])[entry.ordinal()] = exit;
        if (exits.size() > 512) exits.clear(); // runaway guard; a normal route never reaches this
    }

    public void clear() {
        exits.clear();
    }
}
