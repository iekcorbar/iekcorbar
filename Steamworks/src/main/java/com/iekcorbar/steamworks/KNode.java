package com.iekcorbar.steamworks;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** One placed part (a barrier block in the world + its display models). */
final class KNode {
    final World world;
    final int x, y, z;
    final long key;
    final PartType type;
    Axis axis;
    /** Crank: the way it sticks out. Gauge on a vertical shaft: the way its dials face. Steam wrench turns these two. */
    BlockFace facing;
    boolean alive = true;
    /** Who placed it (machines that break blocks only do so where this player may build). */
    java.util.UUID owner;
    /** Clutch/gearshift: has a redstone signal (read from the world, not saved). */
    boolean powered;

    // network state
    Network network;
    double ratio = Double.NaN;      // speed relative to the network's root part
    double generated;               // sources: own speed (RPM, around the positive axis)
    int crankTicks;
    int crankDir = 1;

    // looks
    final List<DisplayPart> displays = new ArrayList<>();
    boolean snap = true;

    // survival picking up
    int hits;
    long lastHit;

    // millstone
    ItemStack millInput;
    final List<ItemStack> millOutput = new ArrayList<>();
    double millProgress;

    // sieve
    ItemStack sieveInput;
    final List<ItemStack> sieveOutput = new ArrayList<>();
    double sieveProgress;

    // deployer
    ItemStack deployItem;

    // steam hammer
    double hammerProgress;

    // weighted ejector: tick until the next throw
    long ejectUntil;

    // kettle (results go in millOutput) + mixer
    final List<ItemStack> kettleItems = new ArrayList<>();
    int kettleWater;
    double mixProgress;
    float whiskDrop;

    // station / signal
    String label;
    boolean signalRed;
    final List<org.bukkit.entity.TextDisplay> labels = new ArrayList<>();

    // oil: tank contents, pumpjack/refinery work
    FluidType fluid;
    int fluidAmount;
    double oilWork, beamPhase;

    // karakuri arm
    final List<int[]> points = new ArrayList<>();
    String program;
    MaterialType tool;
    ItemStack held;

    // chain sprocket: offset to the sprocket it is chained to (null = none)
    int[] chain;

    // power plants: windmill wind / boiler fire
    int burnTicks;
    boolean blocked;

    // drill / saw
    long breakTarget = Long.MIN_VALUE;
    double breakProgress;

    // add-on machines (type == CUSTOM): the registered part, its per-node state and pending save bytes
    com.iekcorbar.steamworks.api.CustomPart ext;
    Object extData;
    byte[] extBlob;

    KNode(World world, int x, int y, int z, PartType type, Axis axis, BlockFace facing) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.key = key(x, y, z);
        this.type = type;
        this.axis = axis;
        this.facing = facing;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    long chunkKey() {
        return chunkKey(x >> 4, z >> 4);
    }

    Block block() {
        return world.getBlockAt(x, y, z);
    }

    Location center() {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    /** Actual speed in RPM around the positive direction of this part's axis (0 when jammed). */
    double speed() {
        return network == null ? 0 : network.speedOf(this);
    }

    static final class DisplayPart {
        /** SPIN_B: the far (negative) half of a gearshift, CASING: swapped model when powered. */
        enum Role { SPIN, SPIN_B, STATIC, NEEDLE, HEAD, CASING, LIQUID, BEAM, ARM, TURRET, HELD, CHAIN }

        final ItemDisplay entity;
        final Role role;
        final Quaternionf orient;
        final float spinSign;
        /** Size of the model (big windmill sails are drawn small and scaled up). */
        final float scale;

        DisplayPart(ItemDisplay entity, Role role, Quaternionf orient, float spinSign) {
            this(entity, role, orient, spinSign, 1f);
        }

        DisplayPart(ItemDisplay entity, Role role, Quaternionf orient, float spinSign, float scale) {
            this.entity = entity;
            this.role = role;
            this.orient = orient;
            this.spinSign = spinSign;
            this.scale = scale;
        }
    }
}
