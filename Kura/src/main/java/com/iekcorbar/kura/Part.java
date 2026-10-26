package com.iekcorbar.kura;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** One placed network part (see PartType). Saved as one text line in the chunk's data. */
final class Part {
    final World world;
    final int x, y, z;
    final PartType type;
    final BlockFace facing;
    /** Look picked with the Paint Kit (trims, framed cables), or null. */
    Material paint;
    /** Filter item (Item/Tag/Polymorphic Filter or any plain item), or null. */
    ItemStack filter;
    /** Level Emitter: 0 = on when more than amount, 1 = on when less. Interface: 0 = import, 1 = export. */
    int mode;
    /** Level Emitter threshold. */
    int amount;
    /** Level Emitter currently powered (base block is a redstone block). */
    boolean powered;
    /** Filing Cabinet contents. */
    final List<ItemStack> contents = new ArrayList<>();
    ItemDisplay main, extra;
    int hits;
    long lastHit;

    Part(World world, int x, int y, int z, PartType type, BlockFace facing) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.facing = facing;
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

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    static long key(Block b) {
        return key(b.getX(), b.getY(), b.getZ());
    }

    /** Base block this part should stand on right now. */
    Material base() {
        return type == PartType.LEVEL_EMITTER && powered ? Material.REDSTONE_BLOCK : type.base;
    }

    int stored() {
        int n = 0;
        for (ItemStack it : contents) n += it.getAmount();
        return n;
    }

    // ------------------------------------------------------------ text form

    String serialize() {
        return type.name() + '|' + x + '|' + y + '|' + z + '|' + facing.name() + '|' + (paint == null ? "" : paint.name()) + '|'
                + mode + '|' + amount + '|' + (powered ? 1 : 0) + '|' + encode(filter) + '|'
                + (contents.isEmpty() ? "" : Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
    }

    static Part parse(World w, String line) {
        String[] f = line.split("\\|", -1);
        Part p = new Part(w, Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), PartType.valueOf(f[0]), BlockFace.valueOf(f[4]));
        p.paint = f[5].isEmpty() ? null : Material.matchMaterial(f[5]);
        p.mode = Integer.parseInt(f[6]);
        p.amount = Integer.parseInt(f[7]);
        p.powered = f[8].equals("1");
        p.filter = decode(f[9]);
        if (f.length > 10 && !f[10].isEmpty()) p.contents.addAll(decodeList(f[10]));
        return p;
    }

    static String encode(ItemStack it) {
        return it == null || it.isEmpty() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    static ItemStack decode(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Exception e) {
            return null; // an item of a removed plugin/version
        }
    }

    static List<ItemStack> decodeList(String s) {
        List<ItemStack> out = new ArrayList<>();
        try {
            for (ItemStack it : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s))) if (it != null && !it.isEmpty()) out.add(it);
        } catch (Exception ignored) {
            // unreadable contents: dropped
        }
        return out;
    }
}
