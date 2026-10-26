package com.iekcorbar.steamworks.api;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.UUID;

/**
 * Read-only view of the placed part a {@link CustomPart} callback is about. Steamworks owns the
 * node; add-ons only see where it is, how fast it turns and a free state slot ({@link #data()}).
 */
public interface CustomNode {
    World world();

    int x();

    int y();

    int z();

    Block block();

    /** Middle of the block, y + 0.5. */
    Location center();

    /** Rotation axis of the part (X, Y or Z - the wrench turns it around the clicked face). */
    Axis axis();

    /** The face the part was placed against (fans/cranks look where this points). */
    BlockFace facing();

    /** Current speed in RPM around the positive axis (0 when the network stands still or jams). */
    double speed();

    /** Who placed the part (null when it came from a save made before ownership). */
    UUID owner();

    /** Free slot for the add-on's own per-node state; survives as long as the chunk stays loaded. */
    Object data();

    void data(Object state);
}
