package com.iekcorbar.steamworks.trains;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail.Shape;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * A point on vanilla rails: the rail block, the edge it came in through
 * ("from"), the edge it heads to ("to") and how far along that piece it is.
 * Straight and sloped pieces are straight lines between the edge middles,
 * curves are quarter circles (radius 0.5) round the block corner. Moving on
 * crosses into the next rail block the way a minecart would, including up
 * and down slopes. Every wheel of a train is one walker; they all move the
 * same distance, so they stay on the same path and keep their spacing.
 */
public final class RailWalker {
    /** Rail shape at a block, or null (no rail / chunk not loaded). */
    public interface Rails {
        Shape shape(int x, int y, int z);
    }

    /**
     * What the FRONT wheel of a train chose at a switch: everything behind it follows the same
     * exit, even if redstone flips the rail while the train straddles it (0.3.44, switch system).
     */
    public interface Route {
        BlockFace committedExit(long key, BlockFace entry);
        void commitExit(long key, BlockFace entry, BlockFace exit);
    }

    public int x, y, z;
    public Shape shape;
    public BlockFace from, to;
    public double t;

    public RailWalker(int x, int y, int z, Shape shape, BlockFace from, BlockFace to, double t) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.shape = shape;
        this.from = from;
        this.to = to;
        this.t = t;
    }

    public RailWalker copy() {
        return new RailWalker(x, y, z, shape, from, to, t);
    }

    private void set(RailWalker o) {
        x = o.x; y = o.y; z = o.z; shape = o.shape; from = o.from; to = o.to; t = o.t;
    }

    // ------------------------------------------------------------ shapes

    /** The two edges a rail shape connects. */
    public static BlockFace[] ends(Shape s) {
        return switch (s) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case SOUTH_EAST -> new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST};
            case SOUTH_WEST -> new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST};
            case NORTH_WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.WEST};
            case NORTH_EAST -> new BlockFace[]{BlockFace.NORTH, BlockFace.EAST};
        };
    }

    static boolean connects(Shape s, BlockFace edge) {
        BlockFace[] e = ends(s);
        return e[0] == edge || e[1] == edge;
    }

    static BlockFace other(Shape s, BlockFace edge) {
        BlockFace[] e = ends(s);
        return e[0] == edge ? e[1] : e[0];
    }

    /** The shape that connects two edges, or null (same or opposite-diagonal pairs don't exist). */
    public static Shape shapePair(BlockFace a, BlockFace b) {
        if (a == b) return null;
        boolean n = a == BlockFace.NORTH || b == BlockFace.NORTH;
        boolean s = a == BlockFace.SOUTH || b == BlockFace.SOUTH;
        boolean e = a == BlockFace.EAST || b == BlockFace.EAST;
        boolean w = a == BlockFace.WEST || b == BlockFace.WEST;
        if (n && s) return Shape.NORTH_SOUTH;
        if (e && w) return Shape.EAST_WEST;
        if (n && e) return Shape.NORTH_EAST;
        if (n && w) return Shape.NORTH_WEST;
        if (s && e) return Shape.SOUTH_EAST;
        if (s && w) return Shape.SOUTH_WEST;
        return null;
    }

    /** The other curve a rail could take from this entry (what a switched point offers). Null if none. */
    public static Shape alternateShape(Shape s, BlockFace entry) {
        if (!curve(s)) return null;
        BlockFace current = other(s, entry);
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            if (f == entry || f == current || f == entry.getOppositeFace()) continue;
            Shape alt = shapePair(entry, f);
            if (alt != null && alt != s) return alt;
        }
        return null;
    }

    /** Packing for commit/lock maps (no relation to KNode.key). */
    public static long blockKey(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) (y + 2048) & 0xFFF);
    }

    /** Height of an edge above the block's bottom: 1 for the high end of a slope. */
    static int height(Shape s, BlockFace edge) {
        return switch (s) {
            case ASCENDING_EAST -> edge == BlockFace.EAST ? 1 : 0;
            case ASCENDING_WEST -> edge == BlockFace.WEST ? 1 : 0;
            case ASCENDING_NORTH -> edge == BlockFace.NORTH ? 1 : 0;
            case ASCENDING_SOUTH -> edge == BlockFace.SOUTH ? 1 : 0;
            default -> 0;
        };
    }

    public static boolean curve(Shape s) {
        return s == Shape.SOUTH_EAST || s == Shape.SOUTH_WEST || s == Shape.NORTH_WEST || s == Shape.NORTH_EAST;
    }

    static boolean sloped(Shape s) {
        return s.name().startsWith("ASCENDING");
    }

    /** One possible way out of a rail piece seen from an entry edge: exit edge, shape the piece must
     *  have (its live shape, or the flipped one of a switch) and the block entered. */
    public record Edge(BlockFace exit, Shape shape, int nx, int ny, int nz) {}

    /** All ways out of a rail piece from `entry`, validated against the real rails ahead (mirrors forward()). */
    public static List<Edge> edgesOut(Rails rails, int x, int y, int z, BlockFace entry) {
        List<Edge> out = new ArrayList<>();
        Shape s = rails.shape(x, y, z);
        if (s == null) return out;
        if (!connects(s, entry)) {
            // through the back of a curve: the vanilla way to run a straight line across a junction
            if (curve(s) && connects(s, entry.getOppositeFace())) {
                BlockFace thru = entry.getOppositeFace();
                Edge straight = forwardEdge(rails, x, y, z, entry, thru);
                if (straight != null) out.add(straight);
            }
            return out;
        }
        Edge live = stepEdge(rails, x, y, z, entry, s);
        if (live != null) out.add(live);
        Shape alt = alternateShape(s, entry);
        if (alt != null) {
            Edge flip = stepEdge(rails, x, y, z, entry, alt);
            if (flip != null && (live == null || flip.exit() != live.exit())) out.add(flip);
        }
        return out;
    }

    /** The edge leaving this block through `exit` and landing on a real connecting rail beyond it. */
    private static Edge forwardEdge(Rails rails, int x, int y, int z, BlockFace entry, BlockFace exit) {
        int nx = x + exit.getModX(), nz = z + exit.getModZ();
        BlockFace back = exit.getOppositeFace();
        Shape ns = rails.shape(nx, y, nz);
        if (validEntry(ns, back, 0)) return new Edge(exit, rails.shape(x, y, z), nx, y, nz);
        return null;
    }

    /** May a walker enter the block beyond `exit`? Normal connection, or through the back of a
     *  curve (the vanilla way to run a straight line across a junction). */
    private static boolean validEntry(Shape ns, BlockFace back, int needHigh) {
        if (ns == null) return false;
        if (connects(ns, back)) return height(ns, back) == needHigh;
        return needHigh == 0 && curve(ns) && connects(ns, back.getOppositeFace());
    }

    private static Edge stepEdge(Rails rails, int x, int y, int z, BlockFace entry, Shape use) {
        BlockFace exit = other(use, entry);
        int ey = y + height(use, exit);
        int nx = x + exit.getModX(), nz = z + exit.getModZ();
        BlockFace back = exit.getOppositeFace();
        Shape ns = rails.shape(nx, ey, nz);
        int ny = ey;
        if (!validEntry(ns, back, 0)) {
            ns = rails.shape(nx, ey - 1, nz);
            ny = ey - 1;
            if (!validEntry(ns, back, 1)) return null;
        }
        return new Edge(exit, use, nx, ny, nz);
    }

    public double length() {
        return curved() ? Math.PI / 4 : sloped(shape) ? Math.sqrt(2) : 1;
    }

    /** True when the stored from/to really describe a curve: entering a curve through its back
     *  makes the walker run straight across it (vanilla T-junction through-lines). */
    private boolean curved() {
        return curve(shape) && from != to.getOppositeFace();
    }

    // ------------------------------------------------------------ position

    private Vector edge(BlockFace e) {
        return new Vector(x + 0.5 + 0.5 * e.getModX(), y + height(shape, e), z + 0.5 + 0.5 * e.getModZ());
    }

    /** Point at distance tt along this piece (rail level = block bottom + height). */
    public Vector point(double tt) {
        double f = Math.max(0, Math.min(1, tt / length()));
        Vector a = edge(from), b = edge(to);
        if (!curved()) return a.multiply(1 - f).add(b.multiply(f));
        double cx = x + 0.5 + 0.5 * (from.getModX() + to.getModX());
        double cz = z + 0.5 + 0.5 * (from.getModZ() + to.getModZ());
        double a0 = Math.atan2(a.getZ() - cz, a.getX() - cx), a1 = Math.atan2(b.getZ() - cz, b.getX() - cx);
        double d = a1 - a0;
        if (d > Math.PI) d -= 2 * Math.PI;
        if (d < -Math.PI) d += 2 * Math.PI;
        double ang = a0 + d * f;
        return new Vector(cx + 0.5 * Math.cos(ang), y, cz + 0.5 * Math.sin(ang));
    }

    public Vector pos() {
        return point(t);
    }

    // ------------------------------------------------------------ moving

    /** Moves ds blocks along the track (negative = backwards). False (and unchanged) if the track ends. */
    public boolean move(double ds, Rails rails) {
        return move(ds, rails, null);
    }

    public boolean move(double ds, Rails rails, Route route) {
        if (ds >= 0) return forward(ds, rails, route);
        reverse();
        boolean ok = forward(-ds, rails, route);
        reverse();
        return ok;
    }

    public void reverse() {
        BlockFace f = from;
        from = to;
        to = f;
        t = length() - t;
    }

    private boolean forward(double ds, Rails rails, Route route) {
        RailWalker w = copy();
        w.t += ds;
        int guard = 0;
        while (w.t > w.length()) {
            if (++guard > 64) return false;
            double left = w.t - w.length();
            int exitY = w.y + height(w.shape, w.to);
            int nx = w.x + w.to.getModX(), nz = w.z + w.to.getModZ();
            BlockFace entry = w.to.getOppositeFace();
            BlockFace opp = entry.getOppositeFace();
            Shape s0 = rails.shape(nx, exitY, nz);
            if (s0 != null && curve(s0) && !connects(s0, entry) && connects(s0, opp)) {
                // straight over the back of a curve: the vanilla through-line across a junction
                w.x = nx; w.y = exitY; w.z = nz;
                w.shape = s0; w.from = entry; w.to = opp;
                w.t = left;
                continue;
            }
            Shape s = s0;
            int ny = exitY;
            if (s == null || !connects(s, entry) || height(s, entry) != 0) {
                s = rails.shape(nx, exitY - 1, nz); // down onto a slope whose high end faces us
                ny = exitY - 1;
                if (s == null || !connects(s, entry) || height(s, entry) != 1) return false;
            }
            w.x = nx;
            w.y = ny;
            w.z = nz;
            w.shape = s;
            w.from = entry;
            long bk = blockKey(nx, ny, nz);
            BlockFace committed = route == null ? null : route.committedExit(bk, entry);
            if (committed != null && (committed == other(s, entry) || (curve(s) && shapePair(entry, committed) != null))) {
                w.to = committed; // the front wheel already chose this switch: stay on its route
            } else {
                w.to = other(s, entry);
                if (route != null) route.commitExit(bk, entry, w.to);
            }
            w.t = left;
        }
        set(w);
        return true;
    }

    /** "x,y,z,SHAPE,FROM,TO,t" for trains.yml. */
    public String save() {
        return x + "," + y + "," + z + "," + shape.name() + "," + from.name() + "," + to.name() + "," + t;
    }

    public static RailWalker load(String s) {
        String[] p = s.split(",");
        return new RailWalker(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Shape.valueOf(p[3]),
                BlockFace.valueOf(p[4]), BlockFace.valueOf(p[5]), Double.parseDouble(p[6]));
    }
}
