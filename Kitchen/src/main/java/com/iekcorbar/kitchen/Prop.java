package com.iekcorbar.kitchen;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** One placed station/prop (see PropType). */
final class Prop implements InventoryHolder {
    /** Pot GUI: ingredient slots, container slot, progress slot, result slot. */
    static final int[] POT_INGREDIENTS = {1, 2, 3, 10, 11, 12};
    static final int POT_CONTAINER = 14, POT_PROGRESS = 15, POT_RESULT = 16;

    final World world;
    final int x, y, z;
    final PropType type;
    final BlockFace facing;
    /** Board / stove grill items. */
    final ItemStack[] items;
    final int[] itemTicks;
    /** Pot / storage / basket contents (pot uses the slots above). */
    Inventory inv;
    int progress;
    String cooking;
    boolean lit;
    long waterFor;   // joke: since when is this pot bubbling pure water only (not saved)
    ItemDisplay main;
    final List<Entity> extras = new ArrayList<>();
    int hits;
    long lastHit;

    Prop(World world, int x, int y, int z, PropType type, BlockFace facing) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.facing = facing;
        int n = type.behaviour == PropType.Behaviour.BOARD || type.behaviour == PropType.Behaviour.STOVE ? type.slots : 0;
        this.items = new ItemStack[n];
        this.itemTicks = new int[n];
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    Block block() {
        return world.getBlockAt(x, y, z);
    }

    Location center() {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    long key() {
        return key(x, y, z);
    }

    /** Position key inside one world. */
    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    static long key(Block b) {
        return key(b.getX(), b.getY(), b.getZ());
    }

    /** Items kept when the prop is saved (pot: its 8 working slots). */
    ItemStack[] saved() {
        if (inv == null) return items;
        if (type.behaviour == PropType.Behaviour.POT) {
            ItemStack[] out = new ItemStack[8];
            for (int i = 0; i < 6; i++) out[i] = inv.getItem(POT_INGREDIENTS[i]);
            out[6] = inv.getItem(POT_CONTAINER);
            out[7] = inv.getItem(POT_RESULT);
            return out;
        }
        return inv.getContents();
    }

    // ------------------------------------------------------------ text form (chunk data)

    String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name()).append('|').append(x).append('|').append(y).append('|').append(z).append('|')
                .append(facing.name()).append('|').append(lit ? 1 : 0).append('|').append(progress).append('|');
        ItemStack[] list = saved();
        for (int i = 0; i < list.length; i++) {
            if (i > 0) sb.append(',');
            ItemStack it = list[i];
            sb.append(it == null || it.isEmpty() ? "-" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        sb.append('|');
        for (int i = 0; i < itemTicks.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(itemTicks[i]);
        }
        return sb.toString();
    }

    /** Fields of a saved line: type, x, y, z, facing, lit, progress, items, item ticks. */
    static String[] fields(String line) {
        return line.split("\\|", -1);
    }

    static ItemStack[] decodeItems(String s) {
        if (s.isEmpty()) return new ItemStack[0];
        String[] parts = s.split(",", -1);
        ItemStack[] out = new ItemStack[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("-")) continue;
            try {
                out[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(parts[i]));
            } catch (Exception ignored) {
                // an item from a removed plugin/version: dropped
            }
        }
        return out;
    }
}
