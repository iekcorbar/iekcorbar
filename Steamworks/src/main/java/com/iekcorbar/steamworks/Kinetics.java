package com.iekcorbar.steamworks;

import io.papermc.paper.block.fluid.FluidData;
import org.bukkit.Axis;
import org.bukkit.Fluid;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The kinetic graph: which parts touch, how fast each one turns and whether the
 * network is overloaded. Speeds are stored relative to one root part per network
 * (ratio), so a speed change never needs a new search - only adding/removing
 * parts does.
 *
 * Connections (speed factor from a part to its neighbour):
 *  - shaft ends that touch:      1 (a gearbox flips some sides, see portFactor)
 *  - small cogs side by side:   -1
 *  - large cog / small cog, diagonal: -2 / -0.5
 *  - two large cogs at right angles, diagonal: +-1
 */
final class Kinetics {
    static final BlockFace[] FACES = {BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN, BlockFace.SOUTH, BlockFace.NORTH};
    private static final double DEG_PER_TICK_PER_RPM = 360.0 / 1200.0;
    private static final double PHI_WRAP = 360.0 * 4096;

    record Link(KNode other, double factor) {}

    private final KineticsHost plugin;
    private final Map<UUID, Map<Long, KNode>> worlds = new HashMap<>();
    private final Map<UUID, Map<Long, Set<KNode>>> chunks = new HashMap<>();
    final Set<Network> networks = new HashSet<>();
    private final Set<KNode> dirty = new LinkedHashSet<>();
    final Set<KNode> activeCranks = new HashSet<>();
    final Set<KNode> wheels = new HashSet<>();
    final Set<KNode> mills = new HashSet<>();
    final Set<KNode> hammers = new HashSet<>();
    final Set<KNode> switches = new HashSet<>();
    final Set<KNode> fans = new HashSet<>();
    final Set<KNode> kettles = new HashSet<>();
    final Set<KNode> mixers = new HashSet<>();
    final Set<KNode> stations = new HashSet<>();
    final Set<KNode> signals = new HashSet<>();
    final Set<KNode> pumpjacks = new HashSet<>();
    final Set<KNode> refineries = new HashSet<>();
    final Set<KNode> breakers = new HashSet<>();
    final Set<KNode> belts = new HashSet<>();
    final Set<KNode> ejectors = new HashSet<>();
    final Set<KNode> arms = new HashSet<>();
    final Set<KNode> plants = new HashSet<>();
    final Set<KNode> crushers = new HashSet<>();
    /** Machines registered by add-on plugins (KNode.type == CUSTOM, behaviour on KNode.ext). */
    final Set<KNode> customs = new HashSet<>();
    long tick;

    Kinetics(KineticsHost plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ lookup

    KNode get(World w, int x, int y, int z) {
        Map<Long, KNode> m = worlds.get(w.getUID());
        return m == null ? null : m.get(KNode.key(x, y, z));
    }

    KNode at(Block b) {
        return b == null ? null : get(b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    KNode rel(KNode n, int dx, int dy, int dz) {
        return get(n.world, n.x + dx, n.y + dy, n.z + dz);
    }

    Collection<KNode> inChunk(World w, int cx, int cz) {
        Map<Long, Set<KNode>> m = chunks.get(w.getUID());
        Set<KNode> s = m == null ? null : m.get(KNode.chunkKey(cx, cz));
        return s == null ? Collections.emptyList() : new ArrayList<>(s);
    }

    Collection<KNode> all() {
        List<KNode> out = new ArrayList<>();
        for (Map<Long, KNode> m : worlds.values()) out.addAll(m.values());
        return out;
    }

    void add(KNode n) {
        worlds.computeIfAbsent(n.world.getUID(), k -> new HashMap<>()).put(n.key, n);
        chunks.computeIfAbsent(n.world.getUID(), k -> new HashMap<>())
                .computeIfAbsent(n.chunkKey(), k -> new HashSet<>()).add(n);
        if (n.type == PartType.WATER_WHEEL) {
            wheels.add(n);
            n.generated = waterSpeed(n);
        }
        if (n.type == PartType.MILLSTONE) mills.add(n);
        if (n.type == PartType.STEAM_HAMMER) hammers.add(n);
        if (n.type.isSwitch()) switches.add(n);
        if (n.type == PartType.BELLOWS_FAN) fans.add(n);
        if (n.type == PartType.KETTLE) kettles.add(n);
        if (n.type == PartType.MIXER) mixers.add(n);
        if (n.type == PartType.STATION) stations.add(n);
        if (n.type == PartType.SIGNAL) signals.add(n);
        if (n.type == PartType.PUMPJACK) pumpjacks.add(n);
        if (n.type == PartType.REFINERY) refineries.add(n);
        if (n.type == PartType.DRILL || n.type == PartType.SAW) breakers.add(n);
        if (n.type == PartType.BELT || n.type == PartType.CHUTE || n.type == PartType.FUNNEL) belts.add(n);
        if (n.type == PartType.EJECTOR) ejectors.add(n);
        if (n.type == PartType.ARM) arms.add(n);
        if (n.type == PartType.WINDMILL || n.type == PartType.STEAM_BOILER || n.type == PartType.STEAM_ENGINE) plants.add(n);
        if (n.type == PartType.CRUSHING_WHEEL) crushers.add(n);
        if (n.type == PartType.CUSTOM && n.ext != null) {
            customs.add(n);
            if (n.ext.capacity() > 0) n.generated = n.ext.generatedSpeed(ExtParts.view(n));
        }
        dirty.add(n);
    }

    /** Takes a part out of the graph; its neighbours get recalculated. */
    void remove(KNode n) {
        List<Link> links = new ArrayList<>();
        links(n, links);
        n.alive = false;
        Map<Long, KNode> m = worlds.get(n.world.getUID());
        if (m != null) m.remove(n.key);
        Map<Long, Set<KNode>> c = chunks.get(n.world.getUID());
        if (c != null) {
            Set<KNode> s = c.get(n.chunkKey());
            if (s != null) {
                s.remove(n);
                if (s.isEmpty()) c.remove(n.chunkKey());
            }
        }
        activeCranks.remove(n);
        wheels.remove(n);
        mills.remove(n);
        hammers.remove(n);
        switches.remove(n);
        fans.remove(n);
        kettles.remove(n);
        mixers.remove(n);
        stations.remove(n);
        signals.remove(n);
        pumpjacks.remove(n);
        refineries.remove(n);
        breakers.remove(n);
        belts.remove(n);
        ejectors.remove(n);
        arms.remove(n);
        plants.remove(n);
        crushers.remove(n);
        customs.remove(n);
        dirty.remove(n);
        for (Link l : links) dirty.add(l.other());
        dropNetwork(n.network, n);
        n.network = null;
    }

    void markDirty(KNode n) {
        if (n.alive) dirty.add(n);
    }

    /** Let every add-on machine do its work, and refresh the RPM of add-on power sources. */
    void tickCustoms(long tick) {
        if (customs.isEmpty()) return;
        for (KNode n : customs) {
            if (n.ext == null || !n.alive) continue;
            n.ext.tick(ExtParts.view(n), tick);
            if (n.ext.capacity() > 0 && tick % 20 == 0) {
                double g = n.ext.generatedSpeed(ExtParts.view(n));
                if (g != n.generated) {
                    n.generated = g;
                    markDirty(n);
                }
            }
        }
    }

    private void dropNetwork(Network old, KNode except) {
        if (old == null || !networks.remove(old)) return;
        for (KNode o : old.nodes) if (o != except && o.alive) dirty.add(o);
    }

    // ------------------------------------------------------------ wrench

    /** Parts a steam wrench can turn (everything that has a chosen axis or facing). */
    static boolean canRotate(KNode n) {
        return switch (n.type) {
            case SHAFT, COGWHEEL, LARGE_COGWHEEL, GEARBOX, CLUTCH, GEARSHIFT, SPROCKET, CRUSHING_WHEEL,
                    SPEED_GAUGE, STRESS_GAUGE, STEAM_ENGINE, HAND_CRANK, BELLOWS_FAN, DRILL, SAW, WINDMILL,
                    BELT, CHUTE, FUNNEL, EJECTOR, PUMP, VALVE, STATION, SIGNAL, MILLSTONE -> true;
            case CUSTOM -> n.ext != null && n.ext.rotatable();
            default -> false;   // pure-Y machines (kettle, mixer, arm, boiler, depot, whistle) and fluid parts
        };
    }

    /** Bukkit's stand-in for vanilla Direction.getClockWise(Axis): clockwise around that axis,
     *  seen from the positive side (N->E->S->W around Y). Faces on the axis itself stay put. */
    static BlockFace clockwise(BlockFace f, Axis a) {
        return switch (a) {
            case X -> switch (f) {
                case UP -> BlockFace.NORTH; case NORTH -> BlockFace.DOWN;
                case DOWN -> BlockFace.SOUTH; case SOUTH -> BlockFace.UP; default -> f; };
            case Y -> switch (f) {
                case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST; case WEST -> BlockFace.NORTH; default -> f; };
            case Z -> switch (f) {
                case UP -> BlockFace.EAST; case EAST -> BlockFace.DOWN;
                case DOWN -> BlockFace.WEST; case WEST -> BlockFace.UP; default -> f; };
        };
    }

    /**
     * Create-style wrench turn: rotate the part clockwise around the axis of the face the player
     * clicked (side click tips a shaft up, end click does nothing, top click swaps X/Z).
     * Fields only, returns true when something changed - the caller rebuilds visuals/network/save.
     */
    static boolean turnFace(KNode n, BlockFace clicked) {
        Axis a = axisOf(clicked);
        switch (n.type) {
            case HAND_CRANK, BELLOWS_FAN, DRILL, SAW, WINDMILL, EJECTOR -> {
                BlockFace f = clockwise(n.facing, a);
                if (f == n.facing) return false;
                n.facing = f;
                n.axis = axisOf(f);
                return true;
            }
            case BELT, CHUTE, FUNNEL, STATION, SIGNAL -> {
                BlockFace f = clockwise(n.facing, a);
                if (f == n.facing || axisOf(f) == Axis.Y) return false;   // must stay horizontal
                n.facing = f;
                if (n.type == PartType.BELT || n.type == PartType.CHUTE || n.type == PartType.FUNNEL)
                    n.axis = axisOf(f) == Axis.X ? Axis.Z : Axis.X;
                return true;
            }
            case STEAM_ENGINE, MILLSTONE -> {                                      // stays horizontal too
                BlockFace f = clockwise(positive(n.axis), a);
                if (f == positive(n.axis) || axisOf(f) == Axis.Y) return false;
                n.axis = axisOf(f);
                return true;
            }
            default -> {                                                  // shaft-like: X/Y/Z line
                BlockFace f = clockwise(positive(n.axis), a);
                if (axisOf(f) == n.axis) return false;
                n.axis = axisOf(f);
                return true;
            }
        }
    }

    /**
     * Create's CogWheelBlock.isValidCogwheelPosition (verified against its bytecode): a large cog
     * reaches into the blocks around its rim, so cog wheels may not collide side faces. Only
     * perpendicular neighbours count; a neighbour whose own axis points into the joint is fine.
     */
    boolean cogsFit(KNode n) {
        boolean large = n.type == PartType.LARGE_COGWHEEL;
        for (BlockFace f : FACES) {
            if (axisOf(f) == n.axis) continue;
            KNode m = rel(n, f.getModX(), f.getModY(), f.getModZ());
            if (m == null || (m.type != PartType.COGWHEEL && m.type != PartType.LARGE_COGWHEEL)) continue;
            if (m.axis == axisOf(f)) continue;                    // turns towards the joint: no overlap
            if (m.type == PartType.LARGE_COGWHEEL || large) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ connections

    static Axis axisOf(BlockFace f) {
        return f.getModX() != 0 ? Axis.X : f.getModY() != 0 ? Axis.Y : Axis.Z;
    }

    static int sign(BlockFace f) {
        return f.getModX() + f.getModY() + f.getModZ();
    }

    static BlockFace positive(Axis a) {
        return a == Axis.X ? BlockFace.EAST : a == Axis.Y ? BlockFace.UP : BlockFace.SOUTH;
    }

    /** Faces where a shaft can plug in. */
    static List<BlockFace> ports(KNode n) {
        if (n.type == PartType.CUSTOM && n.ext != null) return new ArrayList<>(n.ext.ports(n.axis));
        List<BlockFace> out = new ArrayList<>(4);
        switch (n.type) {
            case GEARBOX -> {
                for (BlockFace f : FACES) if (axisOf(f) != n.axis) out.add(f);
            }
            case HAND_CRANK, BELLOWS_FAN, DRILL, SAW, WINDMILL, EJECTOR -> out.add(n.facing.getOppositeFace());
            case MIXER -> out.add(BlockFace.UP);
            case ARM -> out.add(BlockFace.DOWN);
            case KETTLE, STATION, SIGNAL, PIPE, TANK, REFINERY, STEAM_BOILER, DEPOT, WHISTLE -> { } // not kinetic
            case CLUTCH -> {
                if (!n.powered) {
                    out.add(positive(n.axis));
                    out.add(positive(n.axis).getOppositeFace());
                }
            }
            default -> {
                out.add(positive(n.axis));
                out.add(positive(n.axis).getOppositeFace());
            }
        }
        return out;
    }

    static boolean hasPort(KNode n, BlockFace f) {
        return ports(n).contains(f);
    }

    /**
     * Speed of the shaft plugged into face f = part speed x this factor. A gearbox
     * is four bevel gears in a ring: neighbouring sides alternate, so one port axis
     * keeps the sign of the face and the other flips it.
     */
    static double portFactor(KNode n, BlockFace f) {
        if (n.type == PartType.CUSTOM && n.ext != null) return n.ext.portFactor(f);
        if (n.type == PartType.GEARSHIFT) return n.powered && sign(f) < 0 ? -1 : 1;
        if (n.type != PartType.GEARBOX) return 1;
        Axis first = n.axis == Axis.X ? Axis.Y : Axis.X;
        return sign(f) * (axisOf(f) == first ? 1 : -1);
    }

    private static int along(Axis a, int dx, int dy, int dz) {
        return a == Axis.X ? dx : a == Axis.Y ? dy : dz;
    }

    void links(KNode n, List<Link> out) {
        for (BlockFace f : ports(n)) {
            KNode m = rel(n, f.getModX(), f.getModY(), f.getModZ());
            if (m != null && hasPort(m, f.getOppositeFace()))
                out.add(new Link(m, portFactor(n, f) * portFactor(m, f.getOppositeFace())));
        }
        if (n.type == PartType.SPROCKET && n.chain != null) {
            // chained sprockets turn at the same speed, the same way
            KNode m = rel(n, n.chain[0], n.chain[1], n.chain[2]);
            if (m != null && m.type == PartType.SPROCKET && m.chain != null
                    && m.chain[0] == -n.chain[0] && m.chain[1] == -n.chain[1] && m.chain[2] == -n.chain[2]) out.add(new Link(m, 1));
        }
        if (n.type == PartType.CRUSHING_WHEEL) {
            // a pair of crushing wheels side by side turn against each other, like cogs
            for (BlockFace f : FACES) {
                if (axisOf(f) == n.axis || axisOf(f) == Axis.Y) continue;
                KNode m = rel(n, f.getModX(), f.getModY(), f.getModZ());
                if (m != null && m.type == PartType.CRUSHING_WHEEL && m.axis == n.axis) out.add(new Link(m, -1));
            }
        }
        if (n.type == PartType.BELT) {
            // belt pieces in a line (same roller axis) run together
            for (BlockFace f : FACES) {
                if (axisOf(f) == n.axis || axisOf(f) == Axis.Y) continue;
                KNode m = rel(n, f.getModX(), f.getModY(), f.getModZ());
                if (m != null && m.type == PartType.BELT && m.axis == n.axis) out.add(new Link(m, 1));
            }
        }
        if (n.type == PartType.COGWHEEL) {
            for (BlockFace f : FACES) {
                if (axisOf(f) == n.axis) continue;
                KNode m = rel(n, f.getModX(), f.getModY(), f.getModZ());
                if (m != null && m.type == PartType.COGWHEEL && m.axis == n.axis) out.add(new Link(m, -1));
            }
            for (int[] d : diagonals(n.axis)) {
                KNode m = rel(n, d[0], d[1], d[2]);
                if (m != null && m.type == PartType.LARGE_COGWHEEL && m.axis == n.axis) out.add(new Link(m, -0.5));
            }
        } else if (n.type == PartType.LARGE_COGWHEEL) {
            for (int[] d : diagonals(n.axis)) {
                KNode m = rel(n, d[0], d[1], d[2]);
                if (m != null && m.type == PartType.COGWHEEL && m.axis == n.axis) out.add(new Link(m, -2));
            }
            for (Axis b : Axis.values()) {
                if (b == n.axis) continue;
                for (int sa = -1; sa <= 1; sa += 2) {
                    for (int sb = -1; sb <= 1; sb += 2) {
                        int dx = (n.axis == Axis.X ? sa : 0) + (b == Axis.X ? sb : 0);
                        int dy = (n.axis == Axis.Y ? sa : 0) + (b == Axis.Y ? sb : 0);
                        int dz = (n.axis == Axis.Z ? sa : 0) + (b == Axis.Z ? sb : 0);
                        KNode m = rel(n, dx, dy, dz);
                        if (m != null && m.type == PartType.LARGE_COGWHEEL && m.axis == b) {
                            boolean flip = along(n.axis, dx, dy, dz) > 0 ^ along(b, dx, dy, dz) > 0;
                            out.add(new Link(m, flip ? -1 : 1));
                        }
                    }
                }
            }
        }
    }

    /** The four diagonal neighbours in the plane across an axis. */
    private static int[][] diagonals(Axis a) {
        int[][] out = new int[4][];
        int i = 0;
        for (int s = -1; s <= 1; s += 2) {
            for (int t = -1; t <= 1; t += 2) {
                out[i++] = switch (a) {
                    case X -> new int[]{0, s, t};
                    case Y -> new int[]{s, 0, t};
                    case Z -> new int[]{s, t, 0};
                };
            }
        }
        return out;
    }

    // ------------------------------------------------------------ rebuilding

    /** Rebuilds every network touched since the last tick. */
    void processDirty() {
        for (int round = 0; round < 8 && !dirty.isEmpty(); round++) {
            ArrayDeque<KNode> seeds = new ArrayDeque<>(dirty);
            dirty.clear();
            Set<KNode> done = new HashSet<>();
            List<KNode> popped = new ArrayList<>();
            while (!seeds.isEmpty()) {
                KNode s = seeds.poll();
                if (!s.alive || done.contains(s)) continue;
                build(s, done, seeds, popped);
            }
            for (KNode p : popped) if (p.alive) plugin.snapBreakerPin(p);
        }
    }

    private void build(KNode seed, Set<KNode> done, ArrayDeque<KNode> seeds, List<KNode> popped) {
        Settings cfg = plugin.settings();
        List<KNode> comp = new ArrayList<>();
        ArrayDeque<KNode> q = new ArrayDeque<>();
        List<Link> links = new ArrayList<>();
        done.add(seed);
        q.add(seed);
        while (!q.isEmpty()) {
            KNode n = q.poll();
            comp.add(n);
            links.clear();
            links(n, links);
            for (Link l : links) if (l.other().alive && done.add(l.other())) q.add(l.other());
        }

        // old networks of these parts are gone; anything else they held gets rebuilt too
        Map<KNode, Double> prevRatio = new HashMap<>();
        Map<KNode, Network> prevNet = new HashMap<>();
        for (KNode n : comp) {
            prevRatio.put(n, n.ratio);
            prevNet.put(n, n.network);
            if (n.network != null && networks.remove(n.network)) {
                for (KNode o : n.network.nodes) if (o.alive && !done.contains(o)) seeds.add(o);
            }
            n.ratio = Double.NaN;
        }

        KNode root = Collections.min(comp, Comparator.comparingLong(n -> n.key));
        Network net = new Network(comp, root);
        boolean locked = false;
        root.ratio = 1;
        q.add(root);
        while (!q.isEmpty()) {
            KNode n = q.poll();
            links.clear();
            links(n, links);
            for (Link l : links) {
                KNode m = l.other();
                if (!m.alive) continue;
                double r = n.ratio * l.factor();
                if (Double.isNaN(m.ratio)) {
                    m.ratio = r;
                    q.add(m);
                } else if (Math.abs(m.ratio - r) > 1e-6 * Math.max(1, Math.abs(r))) {
                    locked = true;
                }
            }
        }
        for (KNode n : comp) if (Double.isNaN(n.ratio)) n.ratio = 1; // safety, cannot happen
        Network rootPrev = prevNet.get(root);
        Double rp = prevRatio.get(root);
        if (rootPrev != null && rp != null && !rp.isNaN()) net.phi = (rootPrev.phi * rp) % PHI_WRAP;

        // power
        KNode best = null;
        for (KNode n : comp) {
            if (n.generated == 0) continue;
            if (best == null || Math.abs(n.generated / n.ratio) > Math.abs(best.generated / best.ratio)) best = n;
        }
        double ref = best == null ? 0 : best.generated / best.ratio;
        net.ref = ref;
        boolean opposed = false;
        for (KNode n : comp) {
            if (n.generated == 0) continue;
            net.capacity += (n.type == PartType.CUSTOM && n.ext != null ? n.ext.capacity() : cfg.capacity(n.type)) * Math.abs(n.generated);
            if (Math.signum(n.generated) != Math.signum(ref * n.ratio)) opposed = true;
        }
        KNode fastest = null;
        for (KNode n : comp) {
            double s = Math.abs(ref * n.ratio);
            net.load += (n.type == PartType.CUSTOM && n.ext != null ? n.ext.impact() : cfg.impact(n.type)) * s;
            if (s > cfg.maxRpm + 1e-6 && (fastest == null || s > Math.abs(ref * fastest.ratio))) fastest = n;
        }

        if (comp.size() > cfg.maxNetwork) jam(net, "Network too large (" + comp.size() + "/" + cfg.maxNetwork + " parts)");
        else if (locked) jam(net, "Gears are locking each other");
        else if (opposed) jam(net, "Two power sources turn against each other");
        else if (fastest != null) {
            jam(net, "Too fast - a breaker pin snapped");
            popped.add(fastest);
        } else if (net.load > net.capacity + 1e-6) jam(net, "Overstressed");

        double max = net.maxSpeed();
        net.period = max <= 0 ? 20 : (int) Math.max(2, Math.min(20, Math.round(72.0 / (max * DEG_PER_TICK_PER_RPM))));
        net.nextUpdate = tick;
        networks.add(net);
        for (KNode n : comp) {
            Double before = prevRatio.get(n);
            if (prevNet.get(n) == null || before == null || before.isNaN() || Math.abs(before - n.ratio) > 1e-9)
                n.snap = true;
            n.network = net;
        }
        plugin.rebuilt(net);
    }

    private static void jam(Network net, String reason) {
        net.jammed = true;
        net.reason = reason;
    }

    // ------------------------------------------------------------ ticking

    void tick() {
        tick++;
        if (!activeCranks.isEmpty()) {
            for (KNode c : new ArrayList<>(activeCranks)) {
                if (--c.crankTicks <= 0) {
                    c.crankTicks = 0;
                    c.generated = 0;
                    activeCranks.remove(c);
                    markDirty(c);
                }
            }
        }
        if (tick % plugin.settings().wheelInterval == 0) {
            for (KNode w : wheels) {
                double s = waterSpeed(w);
                if (s != w.generated) {
                    w.generated = s;
                    markDirty(w);
                }
            }
        }
        processDirty();

        for (Network net : networks) {
            double s = net.effectiveRef();
            if (s == 0 || tick < net.nextUpdate) continue;
            net.phi = (net.phi + s * DEG_PER_TICK_PER_RPM * net.period) % PHI_WRAP;
            plugin.spin(net, net.period);
            net.nextUpdate = tick + net.period;
        }
        if (tick % 40 == 0) jamEffects();
    }

    /**
     * Redstone on a clutch/gearshift changed. A clutch then has no ports (both
     * sides become separate networks); a gearshift flips its negative side.
     * Its old network is rebuilt, so neighbours on both sides follow.
     */
    void setPowered(KNode n, boolean powered) {
        if (n.powered == powered) return;
        n.powered = powered;
        markDirty(n);
        if (n.network != null) dropNetwork(n.network, null);
    }

    /** A power source changed its own speed (windmill wind, steam engine): network recalculates when it differs. */
    void setGenerated(KNode n, double rpm) {
        if (n.generated == rpm) return;
        n.generated = rpm;
        markDirty(n);
    }

    /** A chain was added or removed: both ends (and everything they were connected to) get recalculated. */
    void relink(KNode n) {
        markDirty(n);
        if (n.network != null) dropNetwork(n.network, null);
    }

    /** Hand crank turned by a player. */
    void crank(KNode n, int dir) {
        Settings cfg = plugin.settings();
        double gen = cfg.crankRpm * dir;
        n.crankDir = dir;
        n.crankTicks = cfg.crankTicks;
        activeCranks.add(n);
        if (n.generated != gen) {
            n.generated = gen;
            markDirty(n);
        }
    }

    /**
     * Water wheel speed from the water flowing past it: every side (around the
     * axis) where the flow pushes the paddles one way adds rpm-per-side.
     */
    double waterSpeed(KNode n) {
        Settings cfg = plugin.settings();
        Vector a = vec(positive(n.axis));
        int sum = 0;
        for (BlockFace d : FACES) {
            if (axisOf(d) == n.axis) continue;
            int bx = n.x + d.getModX(), by = n.y + d.getModY(), bz = n.z + d.getModZ();
            if (!n.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            FluidData fd = n.world.getFluidData(bx, by, bz);
            Fluid type = fd.getFluidType();
            if (type != Fluid.WATER && type != Fluid.FLOWING_WATER) continue;
            Vector flow = fd.computeFlowDirection(new Location(n.world, bx, by, bz));
            if (flow.lengthSquared() < 1e-4) continue;
            double dot = flow.dot(a.clone().crossProduct(vec(d)));
            if (Math.abs(dot) > 0.1) sum += dot > 0 ? 1 : -1;
        }
        double s = sum * cfg.wheelRpmPerSide;
        return Math.max(-cfg.wheelMaxRpm, Math.min(cfg.wheelMaxRpm, s));
    }

    static Vector vec(BlockFace f) {
        return new Vector(f.getModX(), f.getModY(), f.getModZ());
    }

    private void jamEffects() {
        for (Network net : networks) {
            if (!net.jammed || net.nodes.isEmpty()) continue;
            KNode n = net.nodes.get((int) (Math.random() * net.nodes.size()));
            Location l = n.center();
            if (l.getWorld().getNearbyPlayers(l, 24).isEmpty()) continue;
            l.getWorld().spawnParticle(Particle.CLOUD, l.clone().add(0, 0.6, 0), 6, 0.25, 0.15, 0.25, 0.01);
            l.getWorld().playSound(l, Sound.BLOCK_PISTON_CONTRACT, 0.35f, 0.5f);
        }
    }
}
