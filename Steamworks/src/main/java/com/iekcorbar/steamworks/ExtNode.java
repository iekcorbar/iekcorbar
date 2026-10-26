package com.iekcorbar.steamworks;

import com.iekcorbar.steamworks.api.CustomNode;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.UUID;

/** The view an add-on gets of its placed machine; all state lives on the KNode. */
final class ExtNode implements CustomNode {
    final KNode node;

    ExtNode(KNode node) {
        this.node = node;
    }

    @Override public World world() { return node.world; }
    @Override public int x() { return node.x; }
    @Override public int y() { return node.y; }
    @Override public int z() { return node.z; }
    @Override public Block block() { return node.block(); }
    @Override public Location center() { return node.center(); }
    @Override public Axis axis() { return node.axis; }
    @Override public BlockFace facing() { return node.facing; }
    @Override public double speed() { return node.speed(); }
    @Override public UUID owner() { return node.owner; }
    @Override public Object data() { return node.extData; }
    @Override public void data(Object state) { node.extData = state; }
}
